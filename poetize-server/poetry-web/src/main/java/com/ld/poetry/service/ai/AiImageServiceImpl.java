package com.ld.poetry.service.ai;

import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.service.ai.image.DashScopeImageClient;
import com.ld.poetry.service.ai.image.GeminiImageClient;
import com.ld.poetry.service.ai.image.GeneratedImage;
import com.ld.poetry.service.ai.image.ImageConfigDto;
import com.ld.poetry.service.ai.image.OpenAiCompatibleImageClient;
import com.ld.poetry.utils.ArticleSummaryTextUtil;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 生图服务实现。
 *
 * <p>核心流程：
 * <ol>
 *   <li>读取 sys_ai_config.image_config（已解密）</li>
 *   <li>剥离文章 HTML/Markdown 得到纯文本，截断后作为输入</li>
 *   <li>按 imageMode 构造生图 prompt：
 *     <ul>
 *       <li>{@code plain}：直接拼接标题+内容，不调用 LLM</li>
 *       <li>{@code global}：用全局 llm_config 提炼 prompt（refine_prompt 作为系统提示词）</li>
 *       <li>{@code dedicated}：用 image_config.dedicated_llm 提炼 prompt</li>
 *     </ul>
 *   </li>
 *   <li>按 provider 调度 {@link OpenAiCompatibleImageClient} / {@link DashScopeImageClient} / {@link GeminiImageClient}，
 *       各客户端会在 prompt 前拼接 style_prompt 风格前缀</li>
 *   <li>对 URL 形态的结果下载为字节，统一通过 {@link FileStorageService} 落库</li>
 *   <li>返回可访问 URL</li>
 * </ol>
 */
@Slf4j
@Service
public class AiImageServiceImpl implements AiImageService {

    /** 文章正文送入 LLM 提炼时的最大字符数（避免 token 超限） */
    private static final int MAX_ARTICLE_TEXT_LENGTH = 2000;

    /** 默认 refine_prompt（给AI模型的系统提示词，当配置项为空时使用） */
    private static final String DEFAULT_REFINE_PROMPT =
            "你是一名 AI 生图 prompt 工程师。根据文章内容生成英文生图 prompt。\n\n" +
            "要求：\n" +
            "- 提炼文章的核心视觉意象，不要直译标题\n" +
            "- 以主体开头，包含场景、光影和风格\n" +
            "- 不超过 60 词，逗号分隔\n" +
            "- 直接输出 prompt，不要解释或前缀";

    /** LLM 提炼 prompt 的用户消息模板 */
    private static final String REFINE_USER_TEMPLATE =
            "标题：%s\n\n内容：%s";

    /** 测试生图连接使用的固定 prompt */
    private static final String TEST_PROMPT = "A minimalist abstract cover image with soft gradient colors";

    /** prompt 构造结果，携带是否发生 plain 降级的标记 */
    private record PromptResult(String prompt, boolean fallbackToPlain) {}

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private DynamicChatClientFactory dynamicChatClientFactory;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private OpenAiCompatibleImageClient openAiCompatibleImageClient;

    @Autowired
    private DashScopeImageClient dashScopeImageClient;

    @Autowired
    private GeminiImageClient geminiImageClient;

    @Autowired
    private SysAuditLogService sysAuditLogService;

    @Override
    public String generateCoverFromArticle(String articleTitle, String articleContentHtml) throws Exception {
        long startedAt = System.currentTimeMillis();
        ImageConfigDto imageConfig = null;
        PromptResult promptResult = null;
        String prompt = null;
        String imageForm = null;
        int imageByteCount = 0;
        boolean fallbackToPlain = false;

        try {
            SysAiConfig sysAiConfig = sysAiConfigService.getArticleAiConfigInternal("default");
            if (sysAiConfig == null || sysAiConfig.getImageConfig() == null || sysAiConfig.getImageConfig().isBlank()) {
                throw new IllegalStateException("AI生图配置不存在，请先在后台配置");
            }

            imageConfig = ImageConfigDto.fromJson(sysAiConfig.getImageConfig(), objectMapper);
            if (!imageConfig.isEnabled()) {
                throw new IllegalStateException("AI生图功能未启用");
            }

            // 1. 提炼正文文本
            String plainContent = ArticleSummaryTextUtil.toPlainText(articleContentHtml, MAX_ARTICLE_TEXT_LENGTH);
            String title = (articleTitle == null || articleTitle.isBlank()) ? "" : articleTitle.trim();

            // 2. 构造生图 prompt
            promptResult = buildPrompt(sysAiConfig, imageConfig, title, plainContent);
            prompt = promptResult.prompt();
            fallbackToPlain = promptResult.fallbackToPlain();

            // 3. 调用生图客户端
            GeneratedImage image = dispatchGenerate(prompt, imageConfig);

            // 4. 统一为字节
            byte[] imageBytes;
            String mimeType;
            if (image.hasBytes()) {
                imageBytes = image.getImageBytes();
                mimeType = image.getMimeType();
                imageForm = "bytes";
            } else if (image.hasUrl()) {
                log.info("生图返回 URL，开始下载: {}", image.getUrl());
                imageBytes = downloadImage(image.getUrl(), imageConfig.getTimeout());
                mimeType = guessMimeTypeFromUrl(image.getUrl());
                imageForm = "url";
            } else {
                throw new IllegalStateException("生图结果既无 URL 也无字节");
            }
            imageByteCount = imageBytes.length;

            // 5. 落库
            String storedUrl = storeImage(imageBytes, mimeType);
            log.info("AI生图完成，最终 URL: {}", storedUrl);

            recordImageAudit("AI_IMAGE_GENERATE", true, "article", null,
                    "AI生成封面: " + truncate(articleTitle, 100),
                    imageConfig, prompt, imageForm, imageByteCount,
                    startedAt, fallbackToPlain, null);
            return storedUrl;
        } catch (Exception e) {
            log.error("AI生图失败: {}", e.getMessage(), e);
            recordImageAudit("AI_IMAGE_GENERATE", false, "article", null,
                    "AI生成封面失败: " + truncate(articleTitle, 100),
                    imageConfig, prompt, imageForm, imageByteCount,
                    startedAt, fallbackToPlain, e);
            throw e;
        }
    }

    @Override
    public Map<String, Object> testImageGeneration(SysAiConfig config, String title, String content) {
        Map<String, Object> result = new HashMap<>();
        if (config == null || config.getImageConfig() == null || config.getImageConfig().isBlank()) {
            result.put("success", false);
            result.put("message", "生图配置为空");
            return result;
        }

        long startedAt = System.currentTimeMillis();
        ImageConfigDto imageConfig = null;
        PromptResult promptResult = null;
        String prompt = null;
        String imageForm = null;
        int imageByteCount = 0;
        boolean fallbackToPlain = false;

        try {
            imageConfig = ImageConfigDto.fromJson(config.getImageConfig(), objectMapper);
            if (!imageConfig.isEnabled()) {
                result.put("success", false);
                result.put("message", "生图功能未启用");
                return result;
            }

            // 判断是否走文章内容测试流程
            boolean hasArticleContent = (title != null && !title.isBlank())
                    || (content != null && !content.isBlank());

            if (hasArticleContent) {
                // 走完整生图流程：提炼正文 → 构造 prompt（含 LLM 提炼）→ 生图
                String plainContent = ArticleSummaryTextUtil.toPlainText(content, MAX_ARTICLE_TEXT_LENGTH);
                String articleTitle = (title == null || title.isBlank()) ? "" : title.trim();

                // global 模式下，若传入 config 缺少 llmConfig，从已保存配置回填
                if (imageConfig.useGlobalMode()
                        && (config.getLlmConfig() == null || config.getLlmConfig().isBlank())) {
                    SysAiConfig saved = sysAiConfigService.getArticleAiConfigInternal(
                            config.getConfigName() != null ? config.getConfigName() : "default");
                    if (saved != null) {
                        config.setLlmConfig(saved.getLlmConfig());
                    }
                }

                promptResult = buildPrompt(config, imageConfig, articleTitle, plainContent);
                prompt = promptResult.prompt();
                fallbackToPlain = promptResult.fallbackToPlain();
            } else {
                prompt = TEST_PROMPT;
                fallbackToPlain = false;
            }

            GeneratedImage image = dispatchGenerate(prompt, imageConfig);

            // 测试场景不落盘：统一转 base64 data URI 直接返回前端预览，避免遗留文件需要清理
            byte[] imageBytes;
            String mimeType;
            if (image.hasBytes()) {
                imageBytes = image.getImageBytes();
                mimeType = image.getMimeType();
                imageForm = "bytes";
            } else if (image.hasUrl()) {
                log.info("生图测试返回 URL，开始下载: {}", image.getUrl());
                imageBytes = downloadImage(image.getUrl(), imageConfig.getTimeout());
                mimeType = guessMimeTypeFromUrl(image.getUrl());
                imageForm = "url";
            } else {
                throw new IllegalStateException("生图结果既无 URL 也无字节");
            }
            imageByteCount = imageBytes.length;

            String dataUri = "data:" + (mimeType != null ? mimeType : "image/png")
                    + ";base64," + java.util.Base64.getEncoder().encodeToString(imageBytes);

            result.put("success", true);
            result.put("message", "生图测试成功");
            result.put("provider", imageConfig.getProvider());
            result.put("model", imageConfig.getModel());
            result.put("url", dataUri);
            result.put("prompt", prompt);
            result.put("durationMs", System.currentTimeMillis() - startedAt);
            result.put("form", imageForm);

            recordImageAudit("AI_IMAGE_TEST", true, "image", null,
                    "生图测试成功: " + imageConfig.getProvider() + "/" + imageConfig.getModel(),
                    imageConfig, prompt, imageForm, imageByteCount,
                    startedAt, fallbackToPlain, null);
            return result;
        } catch (Exception e) {
            log.error("生图测试失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "生图测试失败: " + e.getMessage());

            recordImageAudit("AI_IMAGE_TEST", false, "image", null,
                    "生图测试失败: " + (imageConfig != null ? imageConfig.getProvider() + "/" + imageConfig.getModel() : "unknown"),
                    imageConfig, prompt, imageForm, imageByteCount,
                    startedAt, fallbackToPlain, e);
            return result;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 记录 AI 生图审计日志，参照 AiChatService.recordAiAudit 模式。
     * 生图无 token 概念，promptTokens/completionTokens/totalTokens 传 null。
     */
    private void recordImageAudit(String action, boolean success, String targetType, String targetId,
                                   String summary, ImageConfigDto imageConfig,
                                   String prompt, String imageForm, int imageByteCount,
                                   long startedAt, boolean fallbackToPlain, Throwable failure) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("mode", imageConfig != null ? imageConfig.getImageMode() : "unknown");
            detail.put("durationMs", System.currentTimeMillis() - startedAt);

            if (imageConfig != null) {
                if (imageConfig.getProvider() != null) {
                    detail.put("provider", imageConfig.getProvider());
                }
                if (imageConfig.getModel() != null && !imageConfig.getModel().isEmpty()) {
                    detail.put("model", imageConfig.getModel());
                }
            }

            if (prompt != null) {
                detail.put("promptPreview", truncate(prompt, 160));
            }
            if (imageForm != null) {
                detail.put("imageForm", imageForm);
            }
            if (imageByteCount > 0) {
                detail.put("imageBytes", imageByteCount);
            }
            if (fallbackToPlain) {
                detail.put("fallbackToPlain", true);
            }
            if (failure != null) {
                detail.put("errorType", classifyImageError(failure));
                detail.put("error", truncate(failure.getMessage(), 200));
            }

            sysAuditLogService.recordAi(action, success, targetType, targetId, summary, detail,
                    null, null, null);
        } catch (Exception logErr) {
            log.warn("记录生图审计日志失败: {}", logErr.getMessage());
        }
    }

    /**
     * 生图错误分类，参照 AiChatService.classifyAiError 思路。
     */
    private String classifyImageError(Throwable e) {
        if (e == null) return "unknown";
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String cls = e.getClass().getSimpleName().toLowerCase();

        if (msg.contains("timeout") || cls.contains("timeout") || msg.contains("timed out")) {
            return "timeout";
        }
        if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("api key")
                || msg.contains("apikey") || msg.contains("forbidden") || msg.contains("403")) {
            return "auth";
        }
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("quota")) {
            return "rate_limit";
        }
        if (msg.contains("content filter") || msg.contains("safety") || msg.contains("blocked")) {
            return "content_filter";
        }
        if (cls.contains("connection") || cls.contains("socket") || msg.contains("network")
                || msg.contains("connect") || msg.contains("dns")) {
            return "network";
        }
        if (msg.contains("未启用") || msg.contains("配置不存在") || msg.contains("为空")) {
            return "invalid_config";
        }
        return "unknown";
    }

    /**
     * 调度到对应 provider 的生图客户端。
     */
    private GeneratedImage dispatchGenerate(String prompt, ImageConfigDto config) {
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("生图 provider 未配置");
        }
        return switch (provider.toLowerCase()) {
            case "openai", "siliconflow", "doubao", "custom" ->
                    openAiCompatibleImageClient.generate(prompt, config);
            case "dashscope" -> dashScopeImageClient.generate(prompt, config);
            case "gemini" -> geminiImageClient.generate(prompt, config);
            default -> throw new IllegalArgumentException("不支持的生图 provider: " + provider);
        };
    }

    /**
     * 构造生图 prompt（不含 style_prompt 前缀，前缀由各生图客户端自行拼接）。
     *
     * <p>按 imageMode 分三种模式：
     * <ul>
     *   <li>{@code plain}：不用AI提炼，直接拼接文章标题+内容</li>
     *   <li>{@code global}：用全局 llm_config 提炼</li>
     *   <li>{@code dedicated}：用 image_config.dedicated_llm 提炼</li>
     * </ul>
     * global/dedicated 模式下若 LLM 调用失败，自动降级为 plain 模式。
     */
    private PromptResult buildPrompt(SysAiConfig sysAiConfig, ImageConfigDto imageConfig,
                                      String title, String plainContent) {
        // plain 模式：直接拼接，不调用 LLM
        if (imageConfig.usePlainMode()) {
            return new PromptResult(buildPlainPrompt(title, plainContent), false);
        }

        // global / dedicated 模式：通过 LLM 提炼
        String refinePrompt = imageConfig.getRefinePrompt();
        if (refinePrompt == null || refinePrompt.isBlank()) {
            refinePrompt = DEFAULT_REFINE_PROMPT;
        }

        try {
            ChatModel chatModel = createLlmForPromptRefine(sysAiConfig, imageConfig);
            if (chatModel == null) {
                log.warn("LLM 创建失败，降级为 plain 模式");
                return new PromptResult(buildPlainPrompt(title, plainContent), true);
            }

            String userContent = String.format(REFINE_USER_TEMPLATE,
                    title.isEmpty() ? "(无标题)" : title,
                    plainContent.isEmpty() ? "(无内容)" : plainContent);

            String refined = ChatClient.create(chatModel)
                    .prompt()
                    .system(refinePrompt)
                    .user(userContent)
                    .call()
                    .content();

            if (refined == null || refined.isBlank()) {
                log.warn("LLM 提炼返回空，降级为 plain 模式");
                return new PromptResult(buildPlainPrompt(title, plainContent), true);
            }

            log.info("LLM 提炼生图 prompt 成功: {}", truncate(refined, 200));
            return new PromptResult(refined.trim(), false);
        } catch (Exception e) {
            log.warn("LLM 提炼生图 prompt 失败，降级为 plain 模式: {}", e.getMessage());
            return new PromptResult(buildPlainPrompt(title, plainContent), true);
        }
    }

    /**
     * plain 模式的 prompt 拼接：直接用文章标题+内容组成生图 prompt。
     */
    private String buildPlainPrompt(String title, String plainContent) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (plainContent != null && !plainContent.isBlank()) {
            if (sb.length() > 0) {
                sb.append(". ");
            }
            // 截断避免 prompt 过长
            int maxContent = 500;
            String content = plainContent.length() > maxContent
                    ? plainContent.substring(0, maxContent) + "..."
                    : plainContent;
            sb.append(content);
        }
        return sb.length() > 0 ? sb.toString() : "article cover image";
    }

    /**
     * 根据 imageMode 创建 LLM ChatModel：
     * <ul>
     *   <li>global：使用 sysAiConfig.llmConfig</li>
     *   <li>dedicated：使用 imageConfig.dedicated_llm</li>
     * </ul>
     */
    private ChatModel createLlmForPromptRefine(SysAiConfig sysAiConfig, ImageConfigDto imageConfig) {
        try {
            if (imageConfig.useDedicatedLlm()) {
                JsonNode dedicated = imageConfig.getDedicatedLlm();
                if (dedicated == null || dedicated.isMissingNode() || dedicated.isNull()) {
                    log.warn("dedicated_llm 配置缺失，回退到全局 llmConfig");
                    return createChatModelFromLlmJson(sysAiConfig.getLlmConfig(), "全局LLM(生图)");
                }
                return createChatModelFromLlmJson(dedicated.toString(), "生图专属LLM");
            }
            // global 模式
            return createChatModelFromLlmJson(sysAiConfig.getLlmConfig(), "全局LLM(生图)");
        } catch (Exception e) {
            log.error("创建生图 prompt 提炼 LLM 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 llm_config JSON 字符串构造 ChatModel。
     * JSON 字段：model / api_url / api_key / interface_type / timeout / max_tokens / top_p 等。
     */
    private ChatModel createChatModelFromLlmJson(String jsonConfig, String label) {
        if (jsonConfig == null || jsonConfig.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(jsonConfig);

            SysAiConfig temp = new SysAiConfig();
            temp.setModel(textOrNull(node, "model"));
            temp.setApiBase(textOrNull(node, "api_url"));
            temp.setApiKey(textOrNull(node, "api_key"));

            if (node.has("timeout") && node.get("timeout").isNumber()) {
                temp.setHttpReadTimeoutSeconds(node.get("timeout").asInt());
            }
            if (node.has("max_tokens") && node.get("max_tokens").isNumber()) {
                temp.setMaxTokens(node.get("max_tokens").asInt());
            }
            if (node.has("top_p") && node.get("top_p").isNumber()) {
                temp.setTopP(node.get("top_p").decimalValue());
            }
            if (node.has("frequency_penalty") && node.get("frequency_penalty").isNumber()) {
                temp.setFrequencyPenalty(node.get("frequency_penalty").decimalValue());
            }
            if (node.has("presence_penalty") && node.get("presence_penalty").isNumber()) {
                temp.setPresencePenalty(node.get("presence_penalty").decimalValue());
            }

            // interface_type 映射到 provider
            String interfaceType = textOrNull(node, "interface_type");
            if (interfaceType != null && !interfaceType.isBlank()) {
                temp.setProvider(switch (interfaceType.toLowerCase()) {
                    case "openai", "openai_chat", "openai_compatible", "chat_completions" -> "openai";
                    case "deepseek" -> "deepseek";
                    case "siliconflow" -> "siliconflow";
                    case "openrouter" -> "openrouter";
                    case "worldrouter" -> "worldrouter";
                    case "custom" -> "custom";
                    case "anthropic" -> "anthropic";
                    default -> "openai";
                });
            } else {
                temp.setProvider("openai");
            }

            // 生图 prompt 提炼使用适中温度（比翻译略高，保证创意）
            temp.setTemperature(new java.math.BigDecimal("0.6"));

            log.info("从 JSON 创建 {} ChatModel: provider={}, model={}",
                    label, temp.getProvider(), temp.getModel());

            return dynamicChatClientFactory.createChatModel(temp);
        } catch (Exception e) {
            log.error("从 JSON 创建 {} ChatModel 失败: {}", label, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 下载图片 URL 为字节数组。
     */
    private byte[] downloadImage(String url, int timeoutSeconds) {
        int readTimeout = (timeoutSeconds > 0 ? timeoutSeconds : 60) * 1000;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(Math.max(readTimeout, 10000));

        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        byte[] bytes = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("下载生图结果失败，返回空内容: " + url);
        }
        log.info("下载生图结果成功, {} bytes", bytes.length);
        return bytes;
    }

    /**
     * 将图片字节存入文件存储，返回可访问 URL。
     */
    private String storeImage(byte[] bytes, String mimeType) throws IOException {
        String ext = guessExtension(mimeType);
        String fileName = "ai_cover_" + UUID.randomUUID().toString().replace("-", "") + ext;
        MultipartFile multipart = new InMemoryMultipartFile(
                "file", fileName, mimeType != null ? mimeType : "image/png", bytes);

        FileVO fileVO = new FileVO();
        fileVO.setFile(multipart);
        fileVO.setType("aiCover");
        fileVO.setRelativePath("ai_covers/" + fileName);
        fileVO.setOriginalName(fileName);

        StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
        FileVO saved = storeService.saveFile(fileVO);
        return saved.getVisitPath();
    }

    private String guessMimeTypeFromUrl(String url) {
        if (url == null) return "image/png";
        String lower = url.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }

    private String guessExtension(String mimeType) {
        if (mimeType == null) return ".png";
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private String textOrNull(JsonNode node, String key) {
        if (node.has(key) && !node.get(key).isNull()) {
            String v = node.get(key).asText();
            return (v == null || v.isEmpty()) ? null : v;
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 轻量内存 MultipartFile 包装，将字节数组适配给 {@link StoreService#saveFile}。
     */
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content == null ? 0 : content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (OutputStream os = new java.io.FileOutputStream(dest)) {
                os.write(content);
            }
        }
    }
}
