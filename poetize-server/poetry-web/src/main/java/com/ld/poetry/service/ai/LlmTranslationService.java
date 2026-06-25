package com.ld.poetry.service.ai;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.utils.RetryUtil;
import com.ld.poetry.utils.ToonFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 翻译与摘要服务
 * 使用 Spring AI ChatClient 替代 Python HTTP 调用
 *
 * 替代 Python 端: translation_api.py 中的 LLM 翻译和摘要生成逻辑
 *
 * 支持的翻译方式：
 * - llm: 使用全局 AI 模型（article_ai 配置的 llmConfig）
 * - dedicated_llm: 使用翻译独立 AI 模型（translationLlmConfig）
 *
 * 支持的摘要方式：
 * - global: 使用全局 AI 模型
 * - dedicated: 使用摘要独立 AI 模型
 */
@Slf4j
@Service
public class LlmTranslationService {

    private static final int SUMMARY_LENGTH_REPAIR_ATTEMPTS = 2;
    private static final double SUMMARY_TARGET_MIN_RATIO = 0.85;

    @Autowired
    private DynamicChatClientFactory chatClientFactory;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private com.ld.poetry.service.SysAuditLogService sysAuditLogService;

    // ==================== 翻译功能 ====================

    /**
     * LLM 翻译文章（标题 + 内容），替代 Python 端的 TOON 翻译
     *
     * @param title      文章标题
     * @param content    文章内容
     * @param sourceLang 源语言代码
     * @param targetLang 目标语言代码
     * @return 翻译结果 {title, content, language} 或 null
     */
    public Map<String, String> translateArticle(String title, String content,
            String sourceLang, String targetLang) {
        return translateArticleStream(title, content, sourceLang, targetLang, null);
    }

    /**
     * LLM 流式翻译文章（标题 + 内容）
     */
    public Map<String, String> translateArticleStream(String title, String content,
            String sourceLang, String targetLang,
            TranslationService.TranslationProgressListener progressListener) {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null) {
                log.error("未找到 article_ai 配置");
                return null;
            }

            ChatModel chatModel = createTranslationChatModel(config);
            if (chatModel == null) {
                log.error("无法创建翻译用 ChatModel");
                return null;
            }

            log.info("开始 LLM 文章翻译: {} -> {}, 标题长度={}, 内容长度={}",
                    sourceLang, targetLang, title.length(), content.length());

            final int maxAttempts = 3;
            Exception lastException = null;
            AtomicInteger receivedChars = new AtomicInteger(0);

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    if (progressListener != null) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("attempt", attempt);
                        payload.put("maxAttempts", maxAttempts);
                        payload.put("message", attempt == 1 ? "开始流式翻译文章..." : "正在重试整篇流式翻译...");
                        progressListener.onEvent(attempt == 1 ? "start" : "retry", payload);
                    }

                    StreamingTranslationState state = streamArticleTranslationAttempt(
                            chatModel, title, content, sourceLang, targetLang, config,
                            attempt, receivedChars, progressListener);

                    Map<String, String> result = validateStreamingTranslationState(state, title, content, targetLang);
                    if (result != null) {
                        if (progressListener != null) {
                            progressListener.onEvent("complete", Map.of(
                                    "attempt", attempt,
                                    "titleLength", state.title().length(),
                                    "contentLength", state.content().length(),
                                    "receivedLength", receivedChars.get(),
                                    "message", "流式翻译完成"));
                        }
                        return result;
                    }

                    lastException = new IllegalStateException("流式翻译结果不完整");
                    log.warn("第{}次流式翻译结果不完整。state: titleClosed={}, contentClosed={}, titleLength={}, contentLength={}, isTitleSame={}, isContentSame={}. Raw response:\n{}",
                            attempt,
                            state.titleClosed(),
                            state.contentClosed(),
                            state.title().length(),
                            state.content().length(),
                            state.title().equals(title),
                            state.content().equals(content),
                            state.rawResponse());
                } catch (Exception e) {
                    lastException = e;
                    log.warn("第{}次流式翻译失败: {}", attempt, e.getMessage());
                    if (progressListener != null) {
                        progressListener.onEvent("error", Map.of(
                                "attempt", attempt,
                                "retryable", attempt < maxAttempts,
                                "message", e.getMessage() != null ? e.getMessage() : "流式翻译失败"));
                    }
                }
            }

            if (lastException != null) {
                log.error("LLM 流式文章翻译最终失败: {}", lastException.getMessage(), lastException);
            }
            return null;

        } catch (Exception e) {
            log.error("LLM 文章翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * LLM 翻译纯文本
     *
     * @param text       待翻译文本
     * @param sourceLang 源语言代码
     * @param targetLang 目标语言代码
     * @return 翻译后的文本，失败返回 null
     */
    public String translateText(String text, String sourceLang, String targetLang) {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null) {
                log.error("未找到 article_ai 配置");
                return null;
            }

            ChatModel chatModel = createTranslationChatModel(config);
            if (chatModel == null) {
                log.error("无法创建翻译用 ChatModel");
                return null;
            }

            String prompt = String.format("""
                    请将以下文本从 %s 翻译为 %s。
                    只返回翻译结果，不要添加任何解释或注释。

                    原文：
                    %s
                    """, getLanguageName(sourceLang), getLanguageName(targetLang), text);

            String response = RetryUtil.executeWithRetry(() -> ChatClient.create(chatModel)
                    .prompt()
                    .user(prompt)
                    .call()
                    .content(), 3, 1000, "LLM文本翻译");

            if (response != null && !response.isBlank() && !response.equals(text)) {
                log.info("LLM 文本翻译成功: {} -> {}", sourceLang, targetLang);
                return response.trim();
            }

            log.warn("LLM 翻译结果无效");
            return null;

        } catch (Exception e) {
            log.error("LLM 文本翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 摘要功能 ====================

    /**
     * 生成单语言 AI 摘要
     *
     * @param content   文章内容
     * @param maxLength 摘要最大长度
     * @return 摘要文本，失败返回 null
     */
    public String generateSummary(String content, int maxLength) {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null) {
                log.warn("未找到 article_ai 配置，无法生成 AI 摘要");
                return null;
            }

            ChatModel chatModel = createSummaryChatModel(config);
            if (chatModel == null) {
                log.warn("无法创建摘要用 ChatModel");
                return null;
            }

            // 尝试从 summaryConfig 获取自定义 prompt
            String customPromptTemplate = getSummaryCustomPrompt(config);
            String styleDesc = getSummaryStyleDesc(config);
            String prompt;

            if (customPromptTemplate != null && !customPromptTemplate.isBlank()) {
                // 使用自定义提示词，支持占位符替换（与 Python 原版一致）
                prompt = customPromptTemplate
                        .replace("{max_length}", String.valueOf(maxLength))
                        .replace("{style_desc}", styleDesc)
                        .replace("{content_text}", content)
                        .replace("{source_content}", content)
                        .replace("{languages}", "auto")
                        .replace("{source_lang}", "auto");
            } else {
                // 默认提示词（兜底）
                prompt = String.format("""
                        请为以下文章生成一段简洁的摘要。

                        要求：
                        - 摘要长度不超过 %d 个字符
                        - 提取文章的核心要点
                        - 使用与原文相同的语言
                        - 只返回摘要内容，不要添加标题或其他格式

                        文章内容：
                        %s
                        """, maxLength, content);
            }

            String response = RetryUtil.executeWithRetry(() -> {
                try {
                    return executeSummaryWithTimeout(config, () -> ChatClient.create(chatModel)
                            .prompt()
                            .user(prompt)
                            .call()
                            .content());
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }, 3, 1000, "AI摘要生成");

            if (response != null && !response.isBlank()) {
                String summary = ensureSummaryLength(chatModel, config, content, "原文相同语言", response, maxLength);
                log.info("AI 摘要生成成功，长度: {}", summary.length());
                return summary;
            }

            return null;

        } catch (Exception e) {
            Throwable cause = e.getCause() instanceof TimeoutException te ? te : null;
            if (cause != null) {
                log.error("AI 摘要生成超时: {}", cause.getMessage(), cause);
                throw new RuntimeException(cause);
            }
            log.error("AI 摘要生成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 生成多语言摘要
     *
     * @param articleId        文章 ID
     * @param languageContents 各语言内容 { langCode -> {title, content} }
     * @param maxLength        摘要最大长度
     * @return 各语言摘要 { langCode -> summary }，失败返回 null
     */
    public Map<String, String> generateMultiLangSummary(
            Integer articleId, Map<String, Map<String, String>> languageContents, int maxLength) throws TimeoutException {
        return generateMultiLangSummary(articleId, languageContents, maxLength, null);
    }

    public Map<String, String> generateMultiLangSummary(
            Integer articleId, Map<String, Map<String, String>> languageContents, int maxLength,
            SummaryService.SummaryProgressListener progressListener) throws TimeoutException {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null) {
                log.warn("未找到 article_ai 配置");
                return null;
            }

            ChatModel chatModel = createSummaryChatModel(config);
            if (chatModel == null) {
                log.warn("无法创建摘要用 ChatModel");
                return null;
            }

            // 收集语言列表和源内容
            StringBuilder languagesStr = new StringBuilder();
            String firstContent = null;
            String firstLangCode = null;
            for (Map.Entry<String, Map<String, String>> entry : languageContents.entrySet()) {
                if (languagesStr.length() > 0)
                    languagesStr.append("、");
                languagesStr.append(getLanguageName(entry.getKey()));
                if (firstContent == null) {
                    firstContent = entry.getValue().get("content");
                    firstLangCode = entry.getKey();
                }
            }

            // 生成 TOON 格式示例（与 Python 原版 toon_encode 一致）
            Map<String, Object> exampleSummaries = new LinkedHashMap<>();
            for (String langCode : languageContents.keySet()) {
                exampleSummaries.put(langCode, getLanguageName(langCode) + "摘要内容");
            }
            Map<String, Object> toonData = new LinkedHashMap<>();
            toonData.put("summaries", exampleSummaries);
            String toonExample = ToonFormatter.encode(toonData);
            String jsonExample = JsonUtils.getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(exampleSummaries);
            StringBuilder csvBuilder = new StringBuilder("lang,summary\n");
            for (Map.Entry<String, Object> e : exampleSummaries.entrySet()) {
                csvBuilder.append(csvEscape(e.getKey()))
                        .append(",")
                        .append(csvEscape(String.valueOf(e.getValue())))
                        .append("\n");
            }
            String csvExample = csvBuilder.toString().trim();

            // 尝试从 summaryConfig 获取自定义 prompt
            String customPromptTemplate = getSummaryCustomPrompt(config);
            String styleDesc = getSummaryStyleDesc(config);
            String sourceContent = firstContent != null ? firstContent : "";
            String prompt;

            if (customPromptTemplate != null && !customPromptTemplate.isBlank()) {
                // 使用自定义提示词，支持占位符替换
                prompt = customPromptTemplate
                        .replace("{max_length}", String.valueOf(maxLength))
                        .replace("{style_desc}", styleDesc)
                        .replace("{content_text}", sourceContent)
                        .replace("{source_content}", sourceContent)
                        .replace("{languages}", languagesStr.toString())
                        .replace("{source_lang}", getLanguageName(firstLangCode))
                        .replace("{toon_example}", toonExample)
                        .replace("{json_example}", jsonExample)
                        .replace("{lang_json_example}", jsonExample)
                        .replace("{csv_example}", csvExample);
            } else {
                // 默认提示词：使用 TOON 格式（与 Python 原版一致）
                prompt = String.format("""
                        请为以下%s文章生成多语言摘要，要求：
                        1. 生成语言：%s
                        2. 风格：%s
                        3. 每个语言的摘要长度控制在%d字符以内
                        4. 保持TOON格式结构不变（2个空格缩进）
                        5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记
                        6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）

                        文章内容：

                        %s

                        请返回TOON格式的摘要，格式如下：
                        %s""",
                        getLanguageName(firstLangCode), languagesStr, styleDesc,
                        maxLength, sourceContent, toonExample);
            }

            String response = RetryUtil.executeWithRetry(() -> {
                try {
                    return executeSummaryWithTimeout(config,
                            () -> streamSummaryAttempt(chatModel, prompt, resolveSummaryTimeoutSeconds(config), progressListener));
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }, 3, 1000, "多语言摘要生成");

            if (response == null || response.isBlank()) {
                log.warn("多语言摘要 LLM 返回空结果");
                return null;
            }

            Map<String, String> summaries = parseMultiLangSummaryResponse(response, languageContents, maxLength);
            return ensureMultiLangSummaryLengths(chatModel, config, sourceContent, languageContents, summaries, maxLength);

        } catch (Exception e) {
            Throwable cause = e.getCause() instanceof TimeoutException te ? te : null;
            if (cause != null) {
                log.error("多语言摘要生成超时, 文章ID={}: {}", articleId, cause.getMessage(), cause);
                throw (TimeoutException) cause;
            }
            log.error("多语言摘要生成失败, 文章ID={}: {}", articleId, e.getMessage(), e);
            return null;
        }
    }

    // ==================== ChatModel 创建 ====================

    /**
     * 创建翻译用 ChatModel
     * 优先使用 dedicated_llm（translationLlmConfig），否则使用全局 llmConfig
     */
    private ChatModel createTranslationChatModel(SysAiConfig config) {
        String translationType = config.getTranslationType();

        // dedicated_llm: 使用翻译独立 AI 模型
        if ("dedicated_llm".equals(translationType) && config.getTranslationLlmConfig() != null) {
            return createChatModelFromJson(config.getTranslationLlmConfig(), "翻译独立LLM");
        }

        // llm: 使用全局 AI 模型（llmConfig）
        if ("llm".equals(translationType) && config.getLlmConfig() != null) {
            return createChatModelFromJson(config.getLlmConfig(), "全局LLM");
        }

        // 兼容：尝试使用顶层字段
        if (config.getProvider() != null && config.getApiKey() != null) {
            log.info("使用顶层字段创建翻译 ChatModel");
            return chatClientFactory.createChatModel(config);
        }

        log.error("无可用的翻译 LLM 配置, translationType={}", translationType);
        return null;
    }

    /**
     * 创建摘要用 ChatModel
     * 优先使用 summaryConfig 中的 dedicated_llm，否则使用全局配置
     */
    private ChatModel createSummaryChatModel(SysAiConfig config) {
        if (config.getSummaryConfig() != null) {
            try {
                JsonUtils.JsonObj summaryJson = JsonUtils.parseObject(config.getSummaryConfig());
                String summaryMode = summaryJson.getString("summaryMode");

                if ("dedicated".equals(summaryMode)) {
                    JsonUtils.JsonObj dedicatedLlm = summaryJson.getJSONObject("dedicated_llm");
                    if (dedicatedLlm != null) {
                        return createChatModelFromJson(dedicatedLlm.toJSONString(), "摘要独立LLM");
                    }
                }
                // global 模式或无 dedicated_llm，降级到全局
            } catch (Exception e) {
                log.warn("解析 summaryConfig 失败，降级到全局配置: {}", e.getMessage());
            }
        }

        // 降级：使用全局 llmConfig 或顶层字段
        if (config.getLlmConfig() != null) {
            return createChatModelFromJson(config.getLlmConfig(), "全局LLM(摘要)");
        }

        if (config.getProvider() != null && config.getApiKey() != null) {
            return chatClientFactory.createChatModel(config);
        }

        log.error("无可用的摘要 LLM 配置");
        return null;
    }

    /**
     * 从 JSON 配置字符串创建 ChatModel
     * JSON 格式: {model, api_url, api_key, interface_type, timeout}
     * OpenAI 兼容模型统一按 Chat Completions 调用。
     */
    private ChatModel createChatModelFromJson(String jsonConfig, String label) {
        try {
            JsonUtils.JsonObj json = JsonUtils.parseObject(jsonConfig);

            // 构建临时 SysAiConfig 对象传给 factory
            SysAiConfig tempConfig = new SysAiConfig();
            tempConfig.setModel(json.getString("model"));
            tempConfig.setApiBase(json.getString("api_url"));
            tempConfig.setApiKey(json.getString("api_key"));
            tempConfig.setHttpReadTimeoutSeconds(readPositiveTimeout(json, "timeout"));
            tempConfig.setMaxTokens(json.getInteger("max_tokens"));
            tempConfig.setTopP(json.getBigDecimal("top_p"));
            tempConfig.setFrequencyPenalty(json.getBigDecimal("frequency_penalty"));
            tempConfig.setPresencePenalty(json.getBigDecimal("presence_penalty"));
            String reasoningEffort = json.getString("reasoning_effort");
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                tempConfig.setEnableThinking(true);
                tempConfig.setReasoningEffort(reasoningEffort);
            }
            applyThinkingAdapterConfig(tempConfig, json);

            // interface_type 映射到 provider
            String interfaceType = json.getString("interface_type");
            if (interfaceType != null) {
                tempConfig.setProvider(switch (interfaceType.toLowerCase()) {
                    case "openai", "openai_chat", "openai_compatible", "chat_completions" -> "openai";
                    case "deepseek" -> "deepseek";
                    case "siliconflow" -> "siliconflow";
                    case "openrouter" -> "openrouter";
                    case "worldrouter" -> "worldrouter";
                    case "custom" -> "custom";
                    case "anthropic" -> "anthropic";
                    default -> "openai"; // 默认 OpenAI 兼容
                });
            } else {
                tempConfig.setProvider("openai");
            }

            // 翻译使用较低温度确保准确性
            tempConfig.setTemperature(new java.math.BigDecimal("0.3"));

            log.info("从 JSON 创建 {} ChatModel: provider={}, model={}",
                    label, tempConfig.getProvider(), tempConfig.getModel());

            return chatClientFactory.createChatModel(tempConfig);

        } catch (Exception e) {
            log.error("从 JSON 创建 {} ChatModel 失败: {}", label, e.getMessage(), e);
            return null;
        }
    }

    private void applyThinkingAdapterConfig(SysAiConfig tempConfig, JsonUtils.JsonObj json) {
        JsonUtils.JsonObj extraConfig = new JsonUtils.JsonObj();
        String thinkingProfile = json.getString("thinking_profile");
        if (thinkingProfile != null && !thinkingProfile.isBlank()) {
            extraConfig.put("thinkingProfile", thinkingProfile);
        }
        Object thinkingExtraBody = json.get("thinking_extra_body");
        if (thinkingExtraBody != null) {
            extraConfig.put("thinkingExtraBody", thinkingExtraBody);
        }
        if (!extraConfig.isEmpty()) {
            tempConfig.setExtraConfig(extraConfig.toJSONString());
        }
    }

    // ==================== 摘要 Prompt 辅助方法 ====================

    /**
     * 从 summaryConfig JSON 中提取自定义 prompt 模板
     * 对应 Python 原版: summary_config.get('prompt')
     */
    private String getSummaryCustomPrompt(SysAiConfig config) {
        if (config.getSummaryConfig() == null)
            return null;
        try {
            JsonUtils.JsonObj summaryJson = JsonUtils.parseObject(config.getSummaryConfig());
            return summaryJson.getString("prompt");
        } catch (Exception e) {
            log.warn("解析 summaryConfig 获取 prompt 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 summaryConfig JSON 中获取摘要风格描述
     * 对应 Python 原版: style_prompts.get(request.style)
     */
    private String getSummaryStyleDesc(SysAiConfig config) {
        String style = "concise";
        if (config.getSummaryConfig() != null) {
            try {
                JsonUtils.JsonObj summaryJson = JsonUtils.parseObject(config.getSummaryConfig());
                String configStyle = summaryJson.getString("style");
                if (configStyle != null && !configStyle.isBlank()) {
                    style = configStyle;
                }
            } catch (Exception ignored) {
            }
        }
        return switch (style) {
            case "detailed" -> "详细全面，包含文章的主要内容和关键信息";
            case "academic" -> "学术风格，使用专业术语和结构化表达";
            default -> "简洁明了，突出文章的核心观点";
        };
    }

    // ==================== 翻译辅助方法 ====================

    /**
     * 写入 AI 翻译/摘要审计日志（log_type='AI'）。
     * 翻译/摘要通常无 HTTP 请求上下文（后台/异步触发），userId 由 recordAi 自行解析（多为 null）。
     * API 未上报 usage 时用 jtokkit 估算输入 token 兜底 prompt_tokens。
     */
    private void recordAiAudit(String action, String mode, boolean success, long startedAt,
            String summary, String promptText, String response,
            AiUsageSupport.Accumulator usageAcc, Integer fallbackInputTokens,
            Map<String, Object> extraDetail) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("mode", mode);
            detail.put("durationMs", System.currentTimeMillis() - startedAt);
            detail.put("messagePreview", AiUsageSupport.preview(promptText, 160));
            if (success && response != null) {
                detail.put("responsePreview", AiUsageSupport.preview(response, 160));
            }
            if (extraDetail != null) {
                detail.putAll(extraDetail);
            }
            AiUsageSupport.Snapshot snapshot = usageAcc != null ? usageAcc.snapshot() : AiUsageSupport.Snapshot.empty();
            snapshot = snapshot.withInputFallback(fallbackInputTokens);
            detail.put("usage", snapshot.describe());
            sysAuditLogService.recordAi(action, success, null, null,
                    AiUsageSupport.preview(summary, 200), detail,
                    snapshot.getPromptTokens(), snapshot.getCompletionTokens(), snapshot.getTotalTokens());
        } catch (Exception e) {
            log.debug("写入AI审计日志失败: action={}, error={}", action, e.getMessage());
        }
    }

    private StreamingTranslationState streamArticleTranslationAttempt(ChatModel chatModel, String title, String content,
            String sourceLang, String targetLang, SysAiConfig config, int attempt, AtomicInteger receivedChars,
            TranslationService.TranslationProgressListener progressListener) {
        String prompt = buildArticleTranslationPrompt(title, content, sourceLang, targetLang, config);
        Prompt translationPrompt = new Prompt(List.of(new UserMessage(prompt)));
        StringBuilder rawBuffer = new StringBuilder();
        final StreamingTranslationView[] previousView = {
                new StreamingTranslationView("", "", false, false)
        };

        Flux<ChatResponse> flux = chatModel.stream(translationPrompt)
                .timeout(Duration.ofSeconds(45));

        long startedAt = System.currentTimeMillis();
        AiUsageSupport.Accumulator usageAcc = new AiUsageSupport.Accumulator();
        try {
            flux.doOnNext(chatResponse -> {
                usageAcc.accept(chatResponse);
                if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                    return;
                }

                String text = chatResponse.getResult().getOutput().getText();
                if (text == null || text.isEmpty()) {
                    return;
                }

                rawBuffer.append(text);
                int receivedLength = receivedChars.addAndGet(text.length());
                int responseLength = rawBuffer.length();
                StreamingTranslationView currentView = parseStreamingTranslationView(rawBuffer.toString());
                if (currentView.title().isEmpty() && currentView.content().isEmpty()) {
                    emitTranslationRawProgress(text, responseLength, receivedLength, attempt, progressListener);
                }

                emitTranslationDelta("title_delta", previousView[0].title(), currentView.title(),
                        responseLength, receivedLength, attempt, progressListener);
                emitTranslationDelta("content_delta", previousView[0].content(), currentView.content(),
                        responseLength, receivedLength, attempt, progressListener);

                previousView[0] = currentView;
            }).blockLast();
        } catch (RuntimeException ex) {
            recordAiAudit("AI_TRANSLATE", "translate", false, startedAt,
                    "AI翻译[" + sourceLang + "→" + targetLang + "]: " + title, prompt, null,
                    usageAcc, AiTokenEstimator.countTokens(prompt),
                    Map.of("attempt", attempt, "sourceLang", String.valueOf(sourceLang),
                            "targetLang", String.valueOf(targetLang),
                            "error", String.valueOf(ex.getMessage())));
            throw ex;
        }

        String rawResponse = rawBuffer.toString();
        StreamingTranslationView finalView = parseStreamingTranslationView(rawResponse);
        recordAiAudit("AI_TRANSLATE", "translate", true, startedAt,
                "AI翻译[" + sourceLang + "→" + targetLang + "]: " + title, prompt,
                finalView.title() + "\n" + finalView.content(),
                usageAcc, AiTokenEstimator.countTokens(prompt),
                Map.of("attempt", attempt, "sourceLang", String.valueOf(sourceLang),
                        "targetLang", String.valueOf(targetLang)));
        if (!finalView.titleClosed() || !finalView.contentClosed()) {
            Map<String, String> parsed = parseArticleTranslationResponse(rawResponse, title, content, targetLang);
            if (parsed != null) {
                return new StreamingTranslationState(
                        parsed.getOrDefault("title", ""),
                        parsed.getOrDefault("content", ""),
                        true,
                        true,
                        rawResponse);
            }
        }
        return new StreamingTranslationState(
                finalView.title().trim(),
                finalView.content().trim(),
                finalView.titleClosed(),
                finalView.contentClosed(),
                rawResponse);
    }

    private String executeSummaryWithTimeout(SysAiConfig config, SummaryCall call) throws TimeoutException {
        int timeoutSeconds = resolveSummaryTimeoutSeconds(config);
        try (var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {
            Future<String> future = executor.submit(call::execute);
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException("摘要调用超过 " + timeoutSeconds + " 秒未完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("摘要调用被中断", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (containsTimeout(cause)) {
                    throw new TimeoutException("摘要调用超过 " + timeoutSeconds + " 秒未完成");
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException("摘要调用失败", cause);
            }
        }
    }

    private int resolveSummaryTimeoutSeconds(SysAiConfig config) {
        final int defaultTimeoutSeconds = 45;
        try {
            if (config == null) {
                return defaultTimeoutSeconds;
            }

            if (config.getSummaryConfig() != null) {
                JsonUtils.JsonObj summaryJson = JsonUtils.parseObject(config.getSummaryConfig());
                Integer summaryTimeout = readPositiveTimeout(summaryJson, "timeout");
                if (summaryTimeout != null) {
                    return summaryTimeout;
                }

                JsonUtils.JsonObj dedicatedLlm = summaryJson.getJSONObject("dedicated_llm");
                Integer dedicatedTimeout = readPositiveTimeout(dedicatedLlm, "timeout");
                if (dedicatedTimeout != null) {
                    return dedicatedTimeout;
                }
            }

            if (config.getLlmConfig() != null) {
                Integer llmTimeout = readPositiveTimeout(JsonUtils.parseObject(config.getLlmConfig()), "timeout");
                if (llmTimeout != null) {
                    return llmTimeout;
                }
            }
        } catch (Exception e) {
            log.warn("解析摘要超时配置失败，使用默认值 {} 秒: {}", defaultTimeoutSeconds, e.getMessage());
        }
        return defaultTimeoutSeconds;
    }

    private Integer readPositiveTimeout(JsonUtils.JsonObj jsonObject, String key) {
        if (jsonObject == null) {
            return null;
        }
        Integer timeout = jsonObject.getInteger(key);
        return timeout != null && timeout > 0 ? timeout : null;
    }

    private boolean containsTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String streamSummaryAttempt(ChatModel chatModel, String prompt, int timeoutSeconds,
            SummaryService.SummaryProgressListener progressListener) {
        Prompt summaryPrompt = new Prompt(List.of(new UserMessage(prompt)));
        StringBuilder rawBuffer = new StringBuilder();
        emitSummaryProgress(progressListener, "start", Map.of(
                "message", "开始流式生成AI摘要...",
                "currentLength", 0));

        Flux<ChatResponse> flux = chatModel.stream(summaryPrompt)
                .timeout(Duration.ofSeconds(Math.max(10, Math.min(timeoutSeconds, 45))));

        long startedAt = System.currentTimeMillis();
        AiUsageSupport.Accumulator usageAcc = new AiUsageSupport.Accumulator();
        try {
            flux.doOnNext(chatResponse -> {
                usageAcc.accept(chatResponse);
                if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                    return;
                }

                String text = chatResponse.getResult().getOutput().getText();
                if (text == null || text.isEmpty()) {
                    return;
                }

                rawBuffer.append(text);
                Map<String, Object> payload = new HashMap<>();
                payload.put("delta", text);
                payload.put("currentLength", rawBuffer.length());
                payload.put("preview", rawBuffer.length() <= 4000
                        ? rawBuffer.toString()
                        : rawBuffer.substring(rawBuffer.length() - 4000));
                emitSummaryProgress(progressListener, "summary_delta", payload);
            }).blockLast();
        } catch (RuntimeException ex) {
            recordAiAudit("AI_SUMMARY", "summary", false, startedAt,
                    "AI摘要生成", prompt, null,
                    usageAcc, AiTokenEstimator.countTokens(prompt),
                    Map.of("error", String.valueOf(ex.getMessage())));
            throw ex;
        }

        recordAiAudit("AI_SUMMARY", "summary", true, startedAt,
                "AI摘要生成", prompt, rawBuffer.toString(),
                usageAcc, AiTokenEstimator.countTokens(prompt), null);

        emitSummaryProgress(progressListener, "complete", Map.of(
                "message", "AI摘要流式生成完成",
                "currentLength", rawBuffer.length()));
        return rawBuffer.toString();
    }

    private void emitSummaryProgress(SummaryService.SummaryProgressListener progressListener, String eventName,
            Map<String, Object> payload) {
        if (progressListener == null) {
            return;
        }
        progressListener.onEvent(eventName, payload);
    }

    @FunctionalInterface
    private interface SummaryCall {
        String execute();
    }

    private void emitTranslationDelta(String eventName, String previous, String current,
            int responseLength, int receivedLength, int attempt,
            TranslationService.TranslationProgressListener progressListener) {
        if (progressListener == null || current == null || current.isEmpty()) {
            return;
        }

        String delta = current;
        if (previous != null && current.startsWith(previous)) {
            delta = current.substring(previous.length());
        }

        if (delta.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("delta", delta);
        payload.put("currentLength", current.length());
        payload.put("responseLength", responseLength);
        payload.put("receivedLength", receivedLength);
        payload.put("attempt", attempt);
        progressListener.onEvent(eventName, payload);
    }

    private void emitTranslationRawProgress(String delta, int currentLength, int receivedLength, int attempt,
            TranslationService.TranslationProgressListener progressListener) {
        if (progressListener == null || delta == null || delta.isEmpty()) {
            return;
        }
        progressListener.onEvent("translation_delta", Map.of(
                "delta", delta,
                "currentLength", currentLength,
                "responseLength", currentLength,
                "receivedLength", receivedLength,
                "attempt", attempt,
                "message", "正在接收AI翻译响应... 已接收 " + receivedLength + " 字"));
    }

    private Map<String, String> validateStreamingTranslationState(StreamingTranslationState state,
            String originalTitle, String originalContent, String targetLang) {
        if (state == null || !state.titleClosed() || !state.contentClosed()) {
            return null;
        }

        if (state.title().isBlank() || state.content().isBlank()) {
            return null;
        }

        boolean isOriginalContentPureAscii = originalContent.matches("^[\\x00-\\x7F]*$");
        if (!isOriginalContentPureAscii && state.content().equals(originalContent)) {
            return null;
        }

        boolean isOriginalTitlePureAscii = originalTitle.matches("^[\\x00-\\x7F]*$");
        if (!isOriginalTitlePureAscii && state.title().equals(originalTitle)) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("title", state.title());
        result.put("content", state.content());
        result.put("language", targetLang);
        return result;
    }

    private StreamingTranslationView parseStreamingTranslationView(String raw) {
        String normalized = raw == null ? "" : raw.replace("\r", "");
        String titleToken = extractToonFieldToken(normalized, "title", "content");
        String contentToken = extractToonFieldToken(normalized, "content", null);

        return new StreamingTranslationView(
                decodeToonToken(titleToken),
                decodeToonToken(contentToken),
                isCompleteToonToken(titleToken),
                isCompleteToonToken(contentToken));
    }

    private String extractToonFieldToken(String raw, String fieldName, String nextFieldName) {
        int articleIndex = raw.indexOf("article:");
        if (articleIndex < 0) {
            return "";
        }

        int fieldIndex = raw.indexOf("\n  " + fieldName + ":", articleIndex);
        if (fieldIndex < 0 && raw.startsWith("article:\n  " + fieldName + ":")) {
            fieldIndex = "article:\n".length();
        }
        if (fieldIndex < 0) {
            return "";
        }

        int valueStart = fieldIndex + ("  " + fieldName + ":").length() + (fieldIndex == "article:\n".length() ? 0 : 1);
        int valueEnd = raw.length();
        if (nextFieldName != null) {
            int nextFieldIndex = raw.indexOf("\n  " + nextFieldName + ":", valueStart);
            if (nextFieldIndex >= 0) {
                valueEnd = nextFieldIndex;
            }
        }

        return raw.substring(valueStart, valueEnd).trim();
    }

    private int findClosingQuoteIndex(String token) {
        if (token == null || token.length() < 2 || !token.startsWith("\"")) {
            return -1;
        }
        for (int i = 1; i < token.length(); i++) {
            if (token.charAt(i) == '"') {
                int backslashCount = 0;
                for (int j = i - 1; j >= 0 && token.charAt(j) == '\\'; j--) {
                    backslashCount++;
                }
                if (backslashCount % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String decodeToonToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }

        if (token.startsWith("\"")) {
            int closingIndex = findClosingQuoteIndex(token);
            if (closingIndex != -1) {
                return unescapePartialToonString(token.substring(1, closingIndex));
            } else {
                return unescapePartialToonString(token.substring(1));
            }
        }

        return token;
    }

    private boolean isCompleteToonToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        if (!token.startsWith("\"")) {
            return true;
        }

        return findClosingQuoteIndex(token) != -1;
    }

    private String unescapePartialToonString(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case 'n' -> {
                        sb.append('\n');
                        i++;
                    }
                    case 'r' -> {
                        sb.append('\r');
                        i++;
                    }
                    case 't' -> {
                        sb.append('\t');
                        i++;
                    }
                    case '"' -> {
                        sb.append('"');
                        i++;
                    }
                    case '\\' -> {
                        sb.append('\\');
                        i++;
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 构建文章翻译提示词
     */
    private String buildArticleTranslationPrompt(String title, String content,
            String sourceLang, String targetLang,
            SysAiConfig config) {
        // 尝试从 llmConfig 获取自定义 prompt
        String customPrompt = null;
        String llmJsonConfig = "dedicated_llm".equals(config.getTranslationType())
                ? config.getTranslationLlmConfig()
                : config.getLlmConfig();

        if (llmJsonConfig != null) {
            try {
                JsonUtils.JsonObj json = JsonUtils.parseObject(llmJsonConfig);
                customPrompt = json.getString("prompt");
            } catch (Exception ignored) {
            }
        }

        if (customPrompt != null && !customPrompt.isBlank()) {
            // 生成不同的格式数据供占位符使用
            Map<String, Object> articleData = new LinkedHashMap<>();
            articleData.put("title", title);
            articleData.put("content", content);
            Map<String, Object> toonDataMap = new LinkedHashMap<>();
            toonDataMap.put("article", articleData);
            String toonData = ToonFormatter.encode(toonDataMap);
            String jsonData = JsonUtils.toJsonString(articleData);
            String csvData = buildArticleCsv(title, content);
            String inputFormat = inferPromptDataFormat(customPrompt, "toon");

            // 使用自定义提示词，替换变量
            return customPrompt
                    .replace("{source_lang}", getLanguageName(sourceLang))
                    .replace("{target_lang}", getLanguageName(targetLang))
                    .replace("{title}", title)
                    .replace("{content}", content)
                    .replace("{toon_data}", toonData)
                    .replace("{json_data}", jsonData)
                    .replace("{csv_data}", csvData)
                    .replace("{format}", getFormatLabel(inputFormat));
        }

        // 默认提示词
        Map<String, Object> articleData = new LinkedHashMap<>();
        articleData.put("title", title);
        articleData.put("content", content);
        Map<String, Object> toonDataMap = new LinkedHashMap<>();
        toonDataMap.put("article", articleData);
        String toonData = ToonFormatter.encode(toonDataMap);

        return String.format("""
                将以下TOON格式数据从%s翻译为%s。

                规则：
                1. 保持TOON格式结构不变（2个空格缩进）
                2. 翻译title和content的值
                3. 保持Markdown格式
                4. 只返回TOON格式数据，不添加任何解释

                输入TOON数据：
                %s

                请返回翻译后的TOON数据，格式如下：
                article:
                  title: (翻译后的%s标题)
                  content: (翻译后的%s内容)
                """, getLanguageName(sourceLang), getLanguageName(targetLang),
                toonData,
                getLanguageName(targetLang), getLanguageName(targetLang));
    }

    private String inferPromptDataFormat(String prompt, String defaultFormat) {
        if (prompt == null || prompt.isBlank()) {
            return defaultFormat;
        }
        if (prompt.contains("{csv_data}")) {
            return "csv";
        }
        if (prompt.contains("{json_data}")) {
            return "json";
        }
        if (prompt.contains("{toon_data}")) {
            return "toon";
        }
        String lower = prompt.toLowerCase();
        if (lower.contains("csv")) {
            return "csv";
        }
        if (lower.contains("json")) {
            return "json";
        }
        if (lower.contains("toon")) {
            return "toon";
        }
        return defaultFormat;
    }

    private String getFormatLabel(String format) {
        return switch (format) {
            case "csv" -> "CSV格式";
            case "json" -> "JSON格式";
            case "toon" -> "TOON格式";
            default -> "自定义格式";
        };
    }

    private String buildArticleCsv(String title, String content) {
        return "title,content\n" + csvEscape(title) + "," + csvEscape(content);
    }

    private String csvEscape(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private record StreamingTranslationView(String title, String content, boolean titleClosed, boolean contentClosed) {
    }

    private record StreamingTranslationState(String title, String content, boolean titleClosed, boolean contentClosed, String rawResponse) {
    }

    /**
     * 解析文章翻译 LLM 响应
     */
    private Map<String, String> parseArticleTranslationResponse(
            String response, String originalTitle, String originalContent, String targetLang) {
        try {
            // 尝试提取 JSON
            String jsonStr = extractJson(response);
            if (jsonStr != null) {
                JsonUtils.JsonObj json = JsonUtils.parseObject(jsonStr);
                String translatedTitle = firstJsonString(json, "translated_title", "title");
                String translatedContent = firstJsonString(json, "translated_content", "content");

                if (translatedTitle != null && !translatedTitle.isBlank()
                        && translatedContent != null && !translatedContent.isBlank()
                        && !translatedTitle.equals(originalTitle)
                        && !translatedContent.equals(originalContent)) {

                    Map<String, String> result = new HashMap<>();
                    result.put("title", translatedTitle);
                    result.put("content", translatedContent);
                    result.put("language", targetLang);
                    log.info("LLM 文章翻译解析成功");
                    return result;
                }
            }

            Map<String, String> csvResult = parseCsvArticleTranslation(response, originalTitle, originalContent, targetLang);
            if (csvResult != null) {
                return csvResult;
            }

            // JSON 解析失败，尝试分段提取
            log.warn("LLM 翻译结果非标准 JSON，尝试分段提取");

            // 简单的分段提取：查找标题和内容分隔
            int titleEnd = response.indexOf("\n\n");
            if (titleEnd > 0 && titleEnd < response.length() - 10) {
                String translatedTitle = response.substring(0, titleEnd).trim();
                String translatedContent = response.substring(titleEnd + 2).trim();

                if (!translatedTitle.equals(originalTitle) && !translatedContent.equals(originalContent)) {
                    Map<String, String> result = new HashMap<>();
                    result.put("title", translatedTitle);
                    result.put("content", translatedContent);
                    result.put("language", targetLang);
                    return result;
                }
            }

            log.error("LLM 翻译结果解析失败");
            return null;

        } catch (Exception e) {
            log.error("解析翻译响应失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String firstJsonString(JsonUtils.JsonObj json, String... keys) {
        for (String key : keys) {
            String value = json.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> parseCsvArticleTranslation(
            String response, String originalTitle, String originalContent, String targetLang) {
        List<List<String>> rows = parseCsvRows(stripCodeFence(response));
        if (rows.isEmpty()) {
            return null;
        }

        int titleIndex = 0;
        int contentIndex = 1;
        int valueRowIndex = 0;
        List<String> firstRow = rows.get(0);
        for (int i = 0; i < firstRow.size(); i++) {
            String header = firstRow.get(i).trim().toLowerCase();
            if ("title".equals(header)) {
                titleIndex = i;
                valueRowIndex = rows.size() > 1 ? 1 : 0;
            } else if ("content".equals(header)) {
                contentIndex = i;
                valueRowIndex = rows.size() > 1 ? 1 : 0;
            }
        }

        if (rows.size() <= valueRowIndex) {
            return null;
        }
        List<String> values = rows.get(valueRowIndex);
        if (values.size() <= Math.max(titleIndex, contentIndex)) {
            return null;
        }

        String translatedTitle = values.get(titleIndex).trim();
        String translatedContent = values.get(contentIndex).trim();
        if (translatedTitle.isBlank() || translatedContent.isBlank()
                || translatedTitle.equals(originalTitle) || translatedContent.equals(originalContent)) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("title", translatedTitle);
        result.put("content", translatedContent);
        result.put("language", targetLang);
        log.info("LLM CSV 文章翻译解析成功");
        return result;
    }

    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            int fenceEnd = cleaned.lastIndexOf("```");
            if (fenceEnd >= 0) {
                cleaned = cleaned.substring(0, fenceEnd);
            }
        }
        return cleaned.trim();
    }

    private List<List<String>> parseCsvRows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (hasTextCell(row)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }

        row.add(field.toString());
        if (hasTextCell(row)) {
            rows.add(row);
        }
        return rows;
    }

    private boolean hasTextCell(List<String> row) {
        return row.stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private Map<String, String> ensureMultiLangSummaryLengths(ChatModel chatModel, SysAiConfig config,
            String sourceContent, Map<String, Map<String, String>> languageContents,
            Map<String, String> summaries, int maxLength) {
        if (summaries == null || summaries.isEmpty()) {
            return summaries;
        }

        Map<String, String> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : summaries.entrySet()) {
            String langCode = entry.getKey();
            String langName = getLanguageName(langCode);
            String referenceContent = sourceContent;
            Map<String, String> langContent = languageContents.get(langCode);
            if (langContent != null && langContent.get("content") != null && !langContent.get("content").isBlank()) {
                referenceContent = langContent.get("content");
            }
            adjusted.put(langCode, ensureSummaryLength(
                    chatModel, config, referenceContent, langName, entry.getValue(), maxLength));
        }
        return adjusted;
    }

    private String ensureSummaryLength(ChatModel chatModel, SysAiConfig config, String sourceContent,
            String languageName, String summary, int maxLength) {
        String current = clampSummaryLength(stripCodeFence(summary), maxLength);
        if (!shouldExpandSummary(current, sourceContent, maxLength)) {
            return current;
        }

        for (int attempt = 1; attempt <= SUMMARY_LENGTH_REPAIR_ATTEMPTS; attempt++) {
            int minLength = targetSummaryMinLength(maxLength);
            String repairPrompt = String.format("""
                    你正在修正一段已经生成好的摘要长度。

                    服务器实测当前摘要长度为 %d 字，目标长度为 %d-%d 字。
                    请在不编造事实、不改变原语种（%s）的前提下，把摘要扩写到目标长度区间。
                    保留摘要文体，不要添加标题、列表、解释、Markdown代码块或额外说明。

                    原文：
                    %s

                    当前摘要：
                    %s

                    请只返回修正后的摘要正文：
                    """, current.length(), minLength, maxLength, languageName,
                    abbreviateForRepair(sourceContent), current);

            try {
                String repaired = RetryUtil.executeWithRetry(() -> {
                    try {
                        return executeSummaryWithTimeout(config, () -> ChatClient.create(chatModel)
                                .prompt()
                                .user(repairPrompt)
                                .call()
                                .content());
                    } catch (TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                }, 2, 500, "摘要长度修正");

                if (repaired != null && !repaired.isBlank()) {
                    String normalized = clampSummaryLength(stripCodeFence(repaired), maxLength);
                    if (normalized.length() > current.length()) {
                        current = normalized;
                    }
                }
            } catch (Exception e) {
                log.warn("摘要长度修正失败，保留当前摘要: {}", e.getMessage());
                break;
            }

            if (!shouldExpandSummary(current, sourceContent, maxLength)) {
                break;
            }
        }
        return current;
    }

    private boolean shouldExpandSummary(String summary, String sourceContent, int maxLength) {
        if (summary == null || maxLength <= 0) {
            return false;
        }
        int minLength = targetSummaryMinLength(maxLength);
        if (sourceContent != null && sourceContent.length() <= minLength) {
            return false;
        }
        return summary.length() < minLength;
    }

    private int targetSummaryMinLength(int maxLength) {
        if (maxLength <= 0) {
            return 0;
        }
        double ratio = maxLength < 120 ? 0.75 : SUMMARY_TARGET_MIN_RATIO;
        return Math.max(1, (int) Math.ceil(maxLength * ratio));
    }

    private String clampSummaryLength(String summary, int maxLength) {
        String normalized = summary == null ? "" : summary.trim();
        if (maxLength > 0 && normalized.length() > maxLength) {
            return normalized.substring(0, maxLength).trim();
        }
        return normalized;
    }

    private String abbreviateForRepair(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        int maxRepairSourceLength = 8000;
        if (normalized.length() <= maxRepairSourceLength) {
            return normalized;
        }
        return normalized.substring(0, maxRepairSourceLength);
    }

    /**
     * 解析多语言摘要 LLM 响应
     * 先尝试 TOON 格式解析（与 Python 原版一致），再 JSON 兜底
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseMultiLangSummaryResponse(
            String response, Map<String, Map<String, String>> languageContents, int maxLength) {

        // 1. 先尝试 TOON 格式解析（Python 原版默认格式）
        try {
            Map<String, Object> toonDecoded = ToonFormatter.decode(response);
            if (toonDecoded != null) {
                // TOON 摘要格式: summaries: { zh: "...", en: "..." }，也兼容 result/data 等包装层
                Map<String, String> nestedResult = extractNestedSummaries(toonDecoded, languageContents, maxLength);
                if (nestedResult != null) {
                    log.info("TOON 嵌套格式摘要解析成功，包含 {} 个语言", nestedResult.size());
                    return nestedResult;
                }
                // TOON 解码成功但没有 summaries 字段，可能是语言码到摘要的结构
                Map<String, String> flatResult = extractSummaries(
                        toonDecoded, languageContents, maxLength);
                if (flatResult != null) {
                    log.info("TOON 语言映射格式摘要解析成功，包含 {} 个语言", flatResult.size());
                    return flatResult;
                }
            }
        } catch (Exception e) {
            log.debug("TOON 解析失败，尝试 JSON: {}", e.getMessage());
        }

        // 2. JSON 兜底（用户可能自定义了 JSON 格式的 prompt）
        try {
            String jsonStr = extractJson(response);
            if (jsonStr != null) {
                Map<String, Object> jsonMap = JsonUtils.parseObject(jsonStr, Map.class);
                // 检查是否有 summaries/result/data 等嵌套
                Map<String, String> nestedResult = extractNestedSummaries(jsonMap, languageContents, maxLength);
                if (nestedResult != null) {
                    log.info("JSON 嵌套格式摘要解析成功，包含 {} 个语言", nestedResult.size());
                    return nestedResult;
                }
                // JSON 语言映射: {"zh": "...", "en": "..."}
                Map<String, String> flatResult = extractSummaries(
                        jsonMap, languageContents, maxLength);
                if (flatResult != null) {
                    log.info("JSON 语言映射格式摘要解析成功，包含 {} 个语言", flatResult.size());
                    return flatResult;
                }
            }
        } catch (Exception e) {
            log.debug("JSON 解析也失败: {}", e.getMessage());
        }

        // 3. CSV 兜底（用户可能自定义了 CSV 格式的 prompt）
        Map<String, String> csvStructuredResult = extractCsvSummaries(response, languageContents, maxLength);
        if (csvStructuredResult != null) {
            log.info("CSV 摘要解析成功，包含 {} 个语言", csvStructuredResult.size());
            return csvStructuredResult;
        }

        // 4. 键值对 & TSV 兜底（针对缩进丢失、制表符/冒号分割的纯文本）
        try {
            Map<String, String> csvResult = new LinkedHashMap<>();
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                // 忽略空白行、markdown块标记以及可能的表头
                if (line.isBlank() || line.startsWith("```") || line.toLowerCase().startsWith("lang")
                        || line.toLowerCase().startsWith("summar")) {
                    continue;
                }
                // 支持 CSV/TSV 以及缩进丢失的 TOON（如 zh: summary 或 zh=summary）
                String[] parts = line.split("[,，\\t:]", 2);
                if (parts.length < 2) {
                    parts = line.split("：", 2); // 中文冒号
                }
                if (parts.length < 2) {
                    parts = line.split("=", 2); // 等号
                }

                if (parts.length == 2) {
                    String lang = parts[0].replace("\"", "").replace("'", "").trim();
                    String summary = parts[1].replaceAll("^[\"']|[\"']$", "").trim();
                    if (languageContents.containsKey(lang) && !summary.isBlank()) {
                        csvResult.put(lang, clampSummaryLength(summary, maxLength));
                    }
                }
            }
            if (!csvResult.isEmpty()) {
                log.info("CSV 格式摘要解析成功，包含 {} 个语言", csvResult.size());
                return csvResult;
            }
        } catch (Exception e) {
            log.debug("CSV 解析也失败: {}", e.getMessage());
        }

        log.warn("多语言摘要解析失败（TOON、JSON 和 CSV 均无法解析）");
        return null;
    }

    private Map<String, String> extractCsvSummaries(
            String response, Map<String, Map<String, String>> languageContents, int maxLength) {
        List<List<String>> rows = parseCsvRows(stripCodeFence(response));
        if (rows.isEmpty()) {
            return null;
        }

        int langIndex = 0;
        int summaryIndex = 1;
        int valueStart = 0;
        List<String> firstRow = rows.get(0);
        for (int i = 0; i < firstRow.size(); i++) {
            String header = firstRow.get(i).trim().toLowerCase();
            if ("lang".equals(header) || "language".equals(header)) {
                langIndex = i;
                valueStart = 1;
            } else if ("summary".equals(header) || "summaries".equals(header)) {
                summaryIndex = i;
                valueStart = 1;
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (int i = valueStart; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() <= Math.max(langIndex, summaryIndex)) {
                continue;
            }
            String langCode = row.get(langIndex).replace("\"", "").replace("'", "").trim();
            String summary = row.get(summaryIndex).trim();
            if (languageContents.containsKey(langCode) && !summary.isBlank()) {
                result.put(langCode, clampSummaryLength(summary, maxLength));
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 从解析后的 Map 中提取摘要，验证语言代码并截取长度
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractNestedSummaries(
            Map<String, Object> source, Map<String, Map<String, String>> languageContents, int maxLength) {
        Object summariesObj = source.get("summaries");
        if (summariesObj instanceof Map) {
            Map<String, String> result = extractSummaries(
                    (Map<String, Object>) summariesObj, languageContents, maxLength);
            if (result != null) {
                return result;
            }
        }

        for (Object value : source.values()) {
            if (value instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                Map<String, String> result = extractNestedSummaries(nestedMap, languageContents, maxLength);
                if (result != null) {
                    return result;
                }
                result = extractSummaries(nestedMap, languageContents, maxLength);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private Map<String, String> extractSummaries(
            Map<String, Object> source, Map<String, Map<String, String>> languageContents, int maxLength) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String langCode = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String summary && languageContents.containsKey(langCode)
                    && !summary.isBlank()) {
                result.put(langCode, clampSummaryLength(summary, maxLength));
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串
     */
    private String extractJson(String text) {
        if (text == null)
            return null;
        text = text.trim();

        // 移除 markdown 代码块标记
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        // 查找 JSON 对象
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    /**
     * 语言代码映射到语言名称
     */
    private String getLanguageName(String langCode) {
        if (langCode == null)
            return "中文";
        return switch (langCode.toLowerCase()) {
            case "zh", "zh-cn", "zh-hans" -> "中文";
            case "zh-tw", "zh-TW", "zh-hk", "zh-HK", "zh-hant", "zh-Hant" -> "繁体中文";
            case "en" -> "英文";
            case "ja" -> "日文";
            case "ko" -> "韩文";
            case "fr" -> "法文";
            case "de" -> "德文";
            case "es" -> "西班牙文";
            case "pt" -> "葡萄牙文";
            case "ru" -> "俄文";
            case "ar" -> "阿拉伯文";
            default -> langCode;
        };
    }
}
