package com.ld.poetry.service.ai;

import com.ld.poetry.controller.dto.ResourceBatchDeleteRequest;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.ManagedResourceUploadService;
import com.ld.poetry.service.ResourceBatchDeleteService;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.service.ai.image.CoverPromptTemplate;
import com.ld.poetry.service.ai.image.DashScopeImageClient;
import com.ld.poetry.service.ai.image.GeminiImageClient;
import com.ld.poetry.service.ai.image.GeneratedImage;
import com.ld.poetry.service.ai.image.GenericImageClient;
import com.ld.poetry.service.ai.image.ImageConfigDto;
import com.ld.poetry.service.ai.image.OpenAiCompatibleImageClient;
import com.ld.poetry.utils.ArticleSummaryTextUtil;
import com.ld.poetry.utils.PoetryUtil;
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
import java.util.List;
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
 *       <li>{@code plain}：使用 {@link CoverPromptTemplate} 默认值拼接，主体取文章标题/内容，不调用 LLM</li>
 *       <li>{@code global}：用全局 llm_config 按 cover_template 对应的系统提示词提炼完整 prompt</li>
 *       <li>{@code dedicated}：用 image_config.dedicated_llm 按 cover_template 对应的系统提示词提炼 prompt</li>
 *     </ul>
 *   </li>
 *   <li>按 provider 调度 {@link OpenAiCompatibleImageClient} / {@link DashScopeImageClient} / {@link GeminiImageClient}，
 *       prompt 已在前一步按 cover_template 构建完成，客户端不再额外拼接风格前缀</li>
 *   <li>对 URL 形态的结果下载为字节，统一通过 {@link ManagedResourceUploadService} 保存为受管资源</li>
 *   <li>返回稳定资源 URL</li>
 * </ol>
 */
@Slf4j
@Service
public class AiImageServiceImpl implements AiImageService {

    /** 文章正文送入 LLM 提炼时的最大字符数（避免 token 超限） */
    private static final int MAX_ARTICLE_TEXT_LENGTH = 2000;


    /** LLM 提炼 prompt 的用户消息模板 */
    private static final String REFINE_USER_TEMPLATE =
            "标题：%s\n\n内容：%s";

    /** 测试生图连接使用的固定 prompt */
    private static final String TEST_PROMPT = "A minimalist abstract cover image with soft gradient colors";

    /** 正式文章封面的资源类型与存储目录前缀 */
    private static final String COVER_TYPE = "aiCover";
    private static final String COVER_PATH_PREFIX = "ai_covers/";

    /** 测试生图的资源类型与存储目录前缀（与正式封面隔离，便于生成时清理旧图） */
    private static final String TEST_COVER_TYPE = "aiCoverTest";
    private static final String TEST_COVER_PATH_PREFIX = "ai_covers_test/";

    /** prompt 构造结果，携带是否发生 plain 降级的标记 */
    private record PromptResult(String prompt, boolean fallbackToPlain) {}

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private DynamicChatClientFactory dynamicChatClientFactory;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private ManagedResourceUploadService managedResourceUploadService;

    @Autowired
    private OpenAiCompatibleImageClient openAiCompatibleImageClient;

    @Autowired
    private DashScopeImageClient dashScopeImageClient;

    @Autowired
    private GeminiImageClient geminiImageClient;

    @Autowired
    private GenericImageClient genericImageClient;

    @Autowired
    private SysAuditLogService sysAuditLogService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceBatchDeleteService resourceBatchDeleteService;

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
            String storedUrl = storeImage(imageBytes, mimeType, false);
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

            // 测试场景统一转字节后落盘为受管资源，返回 /media/{publicId} 短 URL。
            // 早期返回 2.3MB base64 data URI 会撑大响应体，用户中途刷新易触发 Broken pipe，
            // 导致成功/失败结果都写不回前端；落盘后响应仅几十字节，规避该问题。
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

            // 相同内容按 SHA-256 去重复用，避免反复测试堆积文件
            String storedUrl = storeImage(imageBytes, mimeType, true);

            result.put("success", true);
            result.put("message", "生图测试成功");
            result.put("provider", imageConfig.getProvider());
            result.put("model", imageConfig.getModel());
            result.put("url", storedUrl);
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
            case "generic" -> genericImageClient.generate(prompt, config);
            default -> throw new IllegalArgumentException("不支持的生图 provider: " + provider);
        };
    }

    /**
     * 构造生图 prompt。
     *
     * <p>按 imageMode 分三种模式：
     * <ul>
     *   <li>{@code plain}：不用AI提炼，使用 {@link CoverPromptTemplate} 默认值拼接，主体取文章标题/内容</li>
     *   <li>{@code global}：用全局 llm_config 按真实感模板系统提示词提炼完整 prompt</li>
     *   <li>{@code dedicated}：用 image_config.dedicated_llm 按真实感模板系统提示词提炼完整 prompt</li>
     * </ul>
     * global/dedicated 模式下若 LLM 调用失败，自动降级为默认模板拼接。
     */
    private PromptResult buildPrompt(SysAiConfig sysAiConfig, ImageConfigDto imageConfig,
                                      String title, String plainContent) {
        return buildTemplatePrompt(sysAiConfig, imageConfig, title, plainContent);
    }

    /**
     * 真实感封面模板模式：LLM 按真实感公式直接生成完整英文生图 prompt（含材质/镜头/光影等全部细节）。
     *
     * <p>用户只需在后台选择模板类型（物品类/人物类），无需手动选择材质/镜头/光影等子选项 —
     * 这些全部由 LLM 根据文章内容提炼。模板公式作为 refine_prompt 的"输出格式约束"，
     * 指导 LLM 按真实感摄影公式生成 prompt（用物理材质词和摄影参数词限制 AI 发散）。
     *
     * <p>三种 imageMode 下的行为：
     * <ul>
     *   <li>{@code plain}：不调用 LLM，用 {@link CoverPromptTemplate} 默认值拼接（材质/镜头/光影用预设默认值）</li>
     *   <li>{@code global}/{@code dedicated}：LLM 按模板 refine_prompt 生成完整 prompt，直接作为生图 prompt</li>
     * </ul>
     * global/dedicated 模式下 LLM 失败时降级为默认拼接。模板模式下 style_prompt 被忽略（避免中文前缀干扰英文模板）。
     */
    private PromptResult buildTemplatePrompt(SysAiConfig sysAiConfig, ImageConfigDto imageConfig,
                                              String title, String plainContent) {
        String refinePrompt = CoverPromptTemplate.getRefinePrompt(imageConfig);

        // plain 模式：不调用 LLM，用默认值拼接；主体取文章标题/内容，避免完全脱离文章
        if (imageConfig.usePlainMode()) {
            String subjectHint = title.isEmpty()
                    ? (plainContent.isEmpty() ? "" : truncate(plainContent, 120))
                    : title;
            String prompt = buildPlainPromptByTemplate(imageConfig, subjectHint);
            return new PromptResult(prompt, false);
        }

        // global / dedicated 模式：LLM 按模板公式生成完整 prompt，直接作为生图 prompt
        String subjectHint = title.isEmpty()
                ? (plainContent.isEmpty() ? "" : truncate(plainContent, 120))
                : title;

        try {
            ChatModel chatModel = createLlmForPromptRefine(sysAiConfig, imageConfig);
            if (chatModel == null) {
                log.warn("模板模式 LLM 创建失败，降级为默认拼接");
                String prompt = buildPlainPromptByTemplate(imageConfig, subjectHint);
                return new PromptResult(prompt, true);
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
                log.warn("模板模式 LLM 返回空，降级为默认拼接");
                String prompt = buildPlainPromptByTemplate(imageConfig, subjectHint);
                return new PromptResult(prompt, true);
            }

            // LLM 已按模板公式生成完整 prompt，直接作为生图 prompt（不再需要模板拼接）
            log.info("模板模式 LLM 生成完整 prompt 成功: {}", truncate(refined, 200));
            return new PromptResult(refined.trim(), false);
        } catch (Exception e) {
            log.warn("模板模式 LLM 生成失败，降级为默认拼接: {}", e.getMessage());
            String prompt = buildPlainPromptByTemplate(imageConfig, subjectHint);
            return new PromptResult(prompt, true);
        }
    }

    /**
     * 根据模板类型选择对应的 plain 模式默认拼接方法。
     * custom 模板无预设公式，降级时复用 object 模板拼接（保证总有可用 prompt）。
     */
    private String buildPlainPromptByTemplate(ImageConfigDto imageConfig, String subjectHint) {
        if (imageConfig.useFeltTemplate()) {
            return CoverPromptTemplate.buildFeltPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.useCyberpunkTemplate()) {
            return CoverPromptTemplate.buildCyberpunkPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.useWatercolorTemplate()) {
            return CoverPromptTemplate.buildWatercolorPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.useInkTemplate()) {
            return CoverPromptTemplate.buildInkPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.usePixelTemplate()) {
            return CoverPromptTemplate.buildPixelPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.use3dTemplate()) {
            return CoverPromptTemplate.build3dPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.useMinimalTemplate()) {
            return CoverPromptTemplate.buildMinimalPrompt(subjectHint, imageConfig);
        }
        if (imageConfig.useCollageTemplate()) {
            return CoverPromptTemplate.buildCollagePrompt(subjectHint, imageConfig);
        }
        if (imageConfig.usePortraitTemplate()) {
            return CoverPromptTemplate.buildPortraitPrompt(subjectHint, imageConfig);
        }
        // object 与 custom（无预设公式）统一降级为物品类拼接
        return CoverPromptTemplate.buildObjectPrompt(subjectHint, imageConfig);
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
        // 必须用 URI 对象传入：.uri(String) 会把 URL 当作 URI 模板再编码一次，
        // 而 OSS 签名 URL 中的 %2B/%3D 已是编码形态，二次编码会导致 SignatureDoesNotMatch
        byte[] bytes = restClient.get()
                .uri(java.net.URI.create(url))
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("下载生图结果失败，返回空内容: " + url);
        }
        log.info("下载生图结果成功, {} bytes", bytes.length);
        return bytes;
    }

    /**
     * 将图片字节保存为受管资源，返回稳定 URL。
     *
     * @param forTest true 为测试生图：用独立 type/目录与正式封面隔离，并先清理当前用户的旧测试图（每人最多留最新一张，避免堆积）；
     *                false 为正式封面：始终新建，保证每篇文章封面独立
     */
    private String storeImage(byte[] bytes, String mimeType, boolean forTest) throws IOException {
        String ext = guessExtension(mimeType);
        String fileName = "ai_cover_" + UUID.randomUUID().toString().replace("-", "") + ext;
        MultipartFile multipart = new InMemoryMultipartFile(
                "file", fileName, mimeType != null ? mimeType : "image/png", bytes);

        FileVO fileVO = new FileVO();
        fileVO.setFile(multipart);
        fileVO.setType(forTest ? TEST_COVER_TYPE : COVER_TYPE);
        fileVO.setRelativePath((forTest ? TEST_COVER_PATH_PREFIX : COVER_PATH_PREFIX) + fileName);
        fileVO.setOriginalName(fileName);

        Integer ownerId = resolveResourceOwnerId();

        // 测试图为一次性预览：落新图前先清掉该用户上一张测试图，无需定时任务即可避免堆积
        if (forTest) {
            cleanPreviousTestImages(ownerId);
        }

        ManagedResourceUploadService.ManagedUploadResult saved =
                forTest
                        ? managedResourceUploadService.uploadOrReuse(fileVO, ownerId)
                        : managedResourceUploadService.upload(fileVO, ownerId);
        return saved.stablePath();
    }

    /**
     * 清理指定用户已有的测试生图（type=aiCoverTest）。
     * 仅针对测试专用 type，与正式封面隔离；走 ResourceBatchDeleteService，
     * 其内置“被引用则跳过”保护，即便异常也不会影响本次生图。
     */
    private void cleanPreviousTestImages(Integer ownerId) {
        try {
            List<Resource> stale = resourceService.lambdaQuery()
                    .eq(Resource::getType, TEST_COVER_TYPE)
                    .eq(Resource::getUserId, ownerId)
                    .eq(Resource::getStatus, true)
                    .list();
            if (stale.isEmpty()) {
                return;
            }
            List<ResourceBatchDeleteRequest.Target> targets = stale.stream()
                    .map(r -> new ResourceBatchDeleteRequest.Target(r.getId(), r.getPath()))
                    .toList();
            resourceBatchDeleteService.delete(
                    new ResourceBatchDeleteRequest(targets, false, false, false));
        } catch (Exception e) {
            // 清理失败不影响本次测试生图，仅记日志
            log.warn("清理旧测试生图失败（不影响本次生图）: {}", e.getMessage());
        }
    }

    private Integer resolveResourceOwnerId() {
        Integer currentUserId = PoetryUtil.getUserId();
        if (currentUserId != null) {
            return currentUserId;
        }
        User admin = PoetryUtil.getAdminUser();
        if (admin == null || admin.getId() == null) {
            throw new IllegalStateException("无法确定AI生成资源的所有者");
        }
        return admin.getId();
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
