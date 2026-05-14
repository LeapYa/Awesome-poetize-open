package com.ld.poetry.controller;

import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.ApiTranslationProvider;
import com.ld.poetry.service.ai.ApiTranslationProviderRegistry;
import com.ld.poetry.service.ai.DynamicChatClientFactory;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.utils.SmartSummaryGenerator;
import com.ld.poetry.utils.ToonFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 翻译测试 API 端点
 * <p>
 * 提供翻译/摘要功能的测试和调试接口。
 * 兼容前端 translationModelManage.vue 发来的 tempConfig 格式。
 * 仅管理员可用（通过 LoginCheck 拦截器保护）。
 */
@RestController
@RequestMapping("/admin/translation")
@Slf4j
public class TranslationApiController {

    private static final int SUMMARY_LENGTH_REPAIR_ATTEMPTS = 2;
    private static final double SUMMARY_TARGET_MIN_RATIO = 0.85;

    private static final String[] API_TRANSLATION_SECRET_FIELDS = {
            "app_secret",
            "api_key",
            "secret_key",
            "access_key_secret",
            "token",
            "subscription_key",
            "auth_key",
            "secret_access_key",
            "session_token",
            "api_key_or_iam_token"
    };

    @Autowired
    private DynamicChatClientFactory chatClientFactory;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private ApiTranslationProviderRegistry apiTranslationProviderRegistry;

    // ========== 前端 translationModelManage.vue 兼容端点 ==========

    /**
     * 翻译测试（兼容前端 tempConfig 格式）
     * <p>
     * 前端调用格式:
     * POST /admin/translation/test/text
     * Body: { config: {type, llm: {...}, ...}, text: "...", title: "...", content:
     * "..." }
     */
    @PostMapping("/test/text")
    public PoetryResult<Map<String, Object>> testTranslateText(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) body.get("config");
            String text = (String) body.get("text");
            String title = (String) body.get("title");
            String content = (String) body.get("content");

            // 从 config 获取语言信息
            String sourceLang = "zh";
            String targetLang = "en";
            if (config != null) {
                sourceLang = (String) config.getOrDefault("default_source_lang", "zh");
                targetLang = (String) config.getOrDefault("default_target_lang", "en");
            }

            // 判断测试类型：有 title+content 则为 TOON 格式文章翻译
            boolean isToonTest = (title != null && !title.isBlank() && content != null && !content.isBlank());

            if (!isToonTest && (text == null || text.isBlank())) {
                return PoetryResult.fail("翻译文本不能为空");
            }

            if (isApiTranslation(config)) {
                return testApiTranslation(config, text, title, content, sourceLang, targetLang, isToonTest, start);
            }

            // 创建临时 ChatModel
            ChatModel chatModel = createChatModelFromConfig(config);
            if (chatModel == null) {
                return PoetryResult.fail("无法创建 AI 模型，请检查配置");
            }

            // 尝试从 config 中获取自定义提示词
            String customPrompt = null;
            if (config != null) {
                String type = (String) config.get("type");
                Map<String, Object> llmConfig = null;
                if ("dedicated_llm".equals(type) && config.containsKey("translation_llm")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tempLlmConfig = (Map<String, Object>) config.get("translation_llm");
                    llmConfig = tempLlmConfig;
                } else if (config.containsKey("llm")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tempLlmConfig = (Map<String, Object>) config.get("llm");
                    llmConfig = tempLlmConfig;
                }

                if (llmConfig != null) {
                    customPrompt = (String) llmConfig.get("prompt");
                }
            }

            if (isToonTest) {
                // 生成不同格式文章数据
                Map<String, Object> toonDataMap = new LinkedHashMap<>();
                toonDataMap.put("title", title);
                toonDataMap.put("content", content);
                String toonData = ToonFormatter.encode(toonDataMap);
                String jsonData = com.alibaba.fastjson.JSON.toJSONString(toonDataMap);
                String csvData = buildArticleCsv(title, content);
                String inputFormat = inferPromptDataFormat(customPrompt, "json");

                String prompt;
                if (customPrompt != null && !customPrompt.isBlank()) {
                    prompt = customPrompt
                            .replace("{source_lang}", sourceLang)
                            .replace("{target_lang}", targetLang)
                            .replace("{title}", title)
                            .replace("{content}", content)
                            .replace("{toon_data}", toonData)
                            .replace("{json_data}", jsonData)
                            .replace("{csv_data}", csvData)
                            .replace("{format}", getFormatLabel(inputFormat));
                } else {
                    prompt = String.format("""
                            请将以下文章从 %s 翻译为 %s。
                            请按以下JSON格式返回，只返回JSON，不要添加任何解释：
                            {"title":"翻译后的标题","content":"翻译后的内容"}

                            标题：%s

                            内容：
                            %s
                            """, sourceLang, targetLang, title, content);
                }

                String response = ChatClient.create(chatModel)
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    Map<String, String> parsed = parseTranslationResponse(response);
                    String responseFormat = detectResponseFormat(response);
                    result.put("translated_title", parsed.get("title"));
                    result.put("translated_content", parsed.get("content"));
                    result.put("is_toon", true);
                    result.put("is_article", true);
                    result.put("engine", "llm");
                    result.put("input_format", inputFormat);
                    result.put("response_format", responseFormat);
                    result.put("source_lang", sourceLang);
                    result.put("target_lang", targetLang);
                    result.put("processing_time", elapsed / 1000.0);

                    addFormatEstimate(result, inputFormat, toonData, jsonData, csvData, toonDataMap);

                    return PoetryResult.success(result);
                } else {
                    return PoetryResult.fail("翻译返回空结果");
                }

            } else {
                // 纯文本翻译测试
                String prompt;
                if (customPrompt != null && !customPrompt.isBlank()) {
                    prompt = customPrompt
                            .replace("{source_lang}", sourceLang)
                            .replace("{target_lang}", targetLang)
                            .replace("{format}", "纯文本")
                            // 纯文本翻译只有文本，直接把文本映射到 content 或者占位符里
                            .replace("{content}", text)
                            .replace("{toon_data}", text)
                            .replace("{json_data}", text)
                            .replace("{csv_data}", text)
                            .replace("{title}", "");
                } else {
                    prompt = String.format("""
                            请将以下文本从 %s 翻译为 %s。
                            只返回翻译结果，不要添加任何解释或注释。

                            原文：
                            %s
                            """, sourceLang, targetLang, text);
                }

                String response = ChatClient.create(chatModel)
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    result.put("translated_text", response.trim());
                    result.put("engine", "llm");
                    result.put("source_lang", sourceLang);
                    result.put("target_lang", targetLang);
                    result.put("processing_time", elapsed / 1000.0);
                    return PoetryResult.success(result);
                } else {
                    return PoetryResult.fail("翻译返回空结果");
                }
            }

        } catch (Exception e) {
            log.error("翻译测试失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return PoetryResult.fail(500, "翻译异常: " + e.getMessage(), result);
        }
    }

    /**
     * 摘要生成测试（兼容前端 tempConfig 格式）
     * <p>
     * 前端调用格式:
     * POST /admin/translation/test/summary
     * Body: { config: {...}, article_id: 0, languages: {zh: "内容", en: ""},
     * max_length: 150, style: "concise" }
     */
    @PostMapping("/test/summary")
    public PoetryResult<Map<String, Object>> testGenerateSummary(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) body.get("config");
            @SuppressWarnings("unchecked")
            Map<String, String> languages = (Map<String, String>) body.get("languages");
            int maxLength = body.containsKey("max_length")
                    ? ((Number) body.get("max_length")).intValue()
                    : 150;
            String style = (String) body.getOrDefault("style", "concise");

            if (languages == null || languages.isEmpty()) {
                return PoetryResult.fail("语言内容不能为空");
            }

            // 找到有内容的源语言
            String sourceContent = null;
            String sourceLang = null;
            for (Map.Entry<String, String> entry : languages.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    sourceContent = entry.getValue();
                    sourceLang = entry.getKey();
                    break;
                }
            }

            if (sourceContent == null) {
                return PoetryResult.fail("请提供至少一种语言的内容");
            }

            String summaryMode = resolveSummaryMode(config);
            if ("disabled".equalsIgnoreCase(summaryMode)) {
                result.put("success", false);
                result.put("error_message", "自动摘要已关闭，无需测试");
                return PoetryResult.fail(400, "自动摘要已关闭", result);
            }

            if ("textrank".equalsIgnoreCase(summaryMode)) {
                Map<String, String> summaries = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : languages.entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isBlank()) {
                        summaries.put(entry.getKey(), SmartSummaryGenerator.generateAdvancedSummary(entry.getValue(), maxLength));
                    }
                }
                long elapsed = System.currentTimeMillis() - start;
                result.put("success", true);
                result.put("summaries", summaries);
                result.put("method", "local-excerpt");
                result.put("processing_time", String.format("%.2f", elapsed / 1000.0));
                return PoetryResult.success(result);
            }

            // 创建 ChatModel
            ChatModel chatModel = createSummaryChatModelFromConfig(config);
            if (chatModel == null) {
                return PoetryResult.fail("无法创建 AI 模型，请检查配置");
            }

            // 构建摘要提示词
            StringBuilder langListBuilder = new StringBuilder();
            for (String langCode : languages.keySet()) {
                if (langListBuilder.length() > 0)
                    langListBuilder.append("、");
                langListBuilder.append(langCode);
            }

            String styleDesc = switch (style) {
                case "detailed" -> "详细全面，包含文章的主要内容和关键信息";
                case "academic" -> "学术风格，使用专业术语和结构化表达";
                case "creative" -> "创意风格，引人入胜";
                default -> "简洁明了，突出文章的核心观点";
            };

            // 生成 TOON 格式示例
            Map<String, Object> exampleSummaries = new LinkedHashMap<>();
            for (String langCode : languages.keySet()) {
                exampleSummaries.put(langCode, langCode + "摘要内容");
            }
            Map<String, Object> toonData = new LinkedHashMap<>();
            toonData.put("summaries", exampleSummaries);
            String toonExample = ToonFormatter.encode(toonData);
            String jsonExample = com.alibaba.fastjson.JSON.toJSONString(
                    exampleSummaries, com.alibaba.fastjson.serializer.SerializerFeature.PrettyFormat);
            StringBuilder csvBuilder = new StringBuilder("lang,summary\n");
            for (Map.Entry<String, Object> e : exampleSummaries.entrySet()) {
                csvBuilder.append(csvEscape(e.getKey()))
                        .append(",")
                        .append(csvEscape(Objects.toString(e.getValue(), "")))
                        .append("\n");
            }
            String csvExample = csvBuilder.toString().trim();

            // 尝试从 config.summary.prompt 获取自定义提示词
            String customPromptTemplate = null;
            if (config != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> summaryConfig = (Map<String, Object>) config.get("summary");
                if (summaryConfig != null) {
                    customPromptTemplate = (String) summaryConfig.get("prompt");
                }
            }

            String prompt;
            if (customPromptTemplate != null && !customPromptTemplate.isBlank()) {
                // 使用自定义提示词，支持占位符替换
                prompt = customPromptTemplate
                        .replace("{max_length}", String.valueOf(maxLength))
                        .replace("{style_desc}", styleDesc)
                        .replace("{content_text}", sourceContent)
                        .replace("{source_content}", sourceContent)
                        .replace("{languages}", langListBuilder.toString())
                        .replace("{source_lang}", sourceLang)
                        .replace("{toon_example}", toonExample)
                        .replace("{json_example}", jsonExample)
                        .replace("{lang_json_example}", jsonExample)
                        .replace("{csv_example}", csvExample);
            } else {
                // 默认提示词：使用 TOON 格式
                prompt = String.format("""
                        请为以下%s文章生成多语言摘要，要求：
                        1. 生成语言：%s
                        2. 风格：%s
                        3. 每个语言的摘要长度控制在%d字符以内
                        4. 保持TOON格式结构不变（2个空格缩进）
                        5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记
                        6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）

                        文章内容（%s）：
                        %s

                        请返回TOON格式的摘要，格式如下：
                        %s""", sourceLang, langListBuilder, styleDesc, maxLength,
                        sourceLang, sourceContent, toonExample);
            }

            String response = ChatClient.create(chatModel)
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - start;

            if (response != null && !response.isBlank()) {
                Map<String, String> summaries = parseSummaryResponse(response, languages);
                summaries = ensureSummaryLengths(chatModel, sourceContent, summaries, languages, maxLength);

                result.put("success", true);
                result.put("summaries", summaries);
                result.put("method", "llm");
                result.put("processing_time", String.format("%.2f", elapsed / 1000.0));
                return PoetryResult.success(result);
            } else {
                result.put("success", false);
                result.put("error_message", "摘要生成返回空结果");
                return PoetryResult.fail(500, "摘要生成失败", result);
            }

        } catch (Exception e) {
            log.error("摘要生成测试失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error_message", e.getMessage());
            return PoetryResult.fail(500, "摘要生成异常: " + e.getMessage(), result);
        }
    }

    /**
     * 快速连接测试
     * <p>
     * 用极简文本测试 AI 连接是否正常
     */
    @PostMapping("/test/connection")
    public PoetryResult<Map<String, Object>> testConnection(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) body.get("config");
            String text = (String) body.getOrDefault("text", "Hi");

            ChatModel chatModel = createChatModelFromConfig(config);
            if (chatModel == null) {
                return PoetryResult.fail("无法创建 AI 模型，请检查配置");
            }

            String response = ChatClient.create(chatModel)
                    .prompt()
                    .user("请用一句话回复：" + text)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - start;

            if (response != null && !response.isBlank()) {
                result.put("success", true);
                result.put("response", response.trim());
                result.put("elapsed_ms", elapsed);
                return PoetryResult.success(result);
            } else {
                return PoetryResult.fail("连接测试返回空响应");
            }

        } catch (Exception e) {
            log.error("连接测试失败: {}", e.getMessage(), e);
            return PoetryResult.fail("连接测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 TOON 格式编解码
     */
    @PostMapping("/test/toon")
    public PoetryResult<Map<String, Object>> testToonFormat(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 编码测试
            String toonEncoded = ToonFormatter.encode(body);
            result.put("toon", toonEncoded);

            // 解码验证
            Map<String, Object> decoded = ToonFormatter.decode(toonEncoded);
            result.put("roundTrip", decoded);

            // Token 对比（粗略计算）
            String jsonStr = com.alibaba.fastjson.JSON.toJSONString(body);
            result.put("jsonLength", jsonStr.length());
            result.put("toonLength", toonEncoded.length());
            double saved = jsonStr.length() > 0
                    ? (1.0 - (double) toonEncoded.length() / jsonStr.length()) * 100
                    : 0;
            result.put("savedPercent", String.format("%.1f%%", saved));
            result.put("success", true);

            return PoetryResult.success(result);

        } catch (Exception e) {
            log.error("TOON 测试失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return PoetryResult.fail(500, "TOON 测试异常: " + e.getMessage(), result);
        }
    }

    // ========== 私有辅助方法 ==========

    private boolean isApiTranslation(Map<String, Object> config) {
        return config != null && "api".equals(config.get("type"));
    }

    private PoetryResult<Map<String, Object>> testApiTranslation(Map<String, Object> config, String text,
            String title, String content, String sourceLang, String targetLang, boolean isToonTest, long start) {
        String providerKey = resolveTempProviderKey(config);
        ApiTranslationProvider provider = apiTranslationProviderRegistry.getProvider(providerKey);
        if (provider == null) {
            return PoetryResult.fail("API翻译配置为空或服务商不支持，请选择已支持的 API 翻译服务商");
        }

        JSONObject providerConfig = resolveTempProviderConfig(config, providerKey);
        mergeSavedProviderSecrets(providerKey, providerConfig);

        Map<String, Object> result = new LinkedHashMap<>();
        if (isToonTest) {
            Map<String, String> translated = provider.translateArticle(
                    title, content, sourceLang, targetLang, providerConfig);
            if (translated == null || translated.isEmpty()) {
                return PoetryResult.fail(provider.displayName() + "返回空翻译结果");
            }
            result.put("translated_title", translated.get("title"));
            result.put("translated_content", translated.get("content"));
            result.put("is_toon", true);
        } else {
            String translated = provider.translate(text, sourceLang, targetLang, providerConfig);
            if (!StringUtils.hasText(translated)) {
                return PoetryResult.fail(provider.displayName() + "返回空翻译结果");
            }
            result.put("translated_text", translated.trim());
        }

        result.put("engine", providerKey);
        result.put("source_lang", sourceLang);
        result.put("target_lang", targetLang);
        result.put("processing_time", (System.currentTimeMillis() - start) / 1000.0);
        return PoetryResult.success(result);
    }

    private String resolveTempProviderKey(Map<String, Object> config) {
        String directProvider = stringValue(config.get("provider"));
        if (apiTranslationProviderRegistry.isApiProvider(directProvider)) {
            return directProvider;
        }
        for (String providerKey : apiTranslationProviderRegistry.providerKeys()) {
            if (config.get(providerKey) instanceof Map<?, ?>) {
                return providerKey;
            }
        }
        Object custom = config.get("custom");
        if (custom instanceof Map<?, ?> customMap) {
            String customProvider = stringValue(customMap.get("provider"));
            if (apiTranslationProviderRegistry.isApiProvider(customProvider)) {
                return customProvider;
            }
            return "custom";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private JSONObject resolveTempProviderConfig(Map<String, Object> config, String providerKey) {
        Object rawProviderConfig = config.get(providerKey);
        if (rawProviderConfig == null && !"baidu".equals(providerKey)) {
            rawProviderConfig = config.get("custom");
        }
        JSONObject providerConfig = new JSONObject();
        if (rawProviderConfig instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, value) -> providerConfig.put(String.valueOf(key), value));
        }
        if (!"baidu".equals(providerKey)) {
            providerConfig.put("provider", providerKey);
        }
        return providerConfig;
    }

    private void mergeSavedProviderSecrets(String providerKey, JSONObject providerConfig) {
        JSONObject savedConfig = "baidu".equals(providerKey)
                ? getSavedBaiduConfig()
                : getSavedCustomConfig(providerKey);
        if (savedConfig == null) {
            return;
        }
        for (String field : API_TRANSLATION_SECRET_FIELDS) {
            String incoming = providerConfig.getString(field);
            if (!hasPlainSecret(incoming) && StringUtils.hasText(savedConfig.getString(field))) {
                providerConfig.put(field, savedConfig.getString(field));
            }
        }
    }

    private JSONObject getSavedCustomConfig(String providerKey) {
        SysAiConfig savedConfig = sysAiConfigService.getArticleAiConfigInternal("default");
        if (savedConfig == null || !StringUtils.hasText(savedConfig.getCustomConfig())) {
            return null;
        }
        JSONObject customConfig = com.alibaba.fastjson.JSON.parseObject(savedConfig.getCustomConfig());
        String savedProvider = customConfig.getString("provider");
        boolean sameProvider = providerKey.equals(savedProvider)
                || (StringUtils.hasText(savedConfig.getTranslationType())
                        && providerKey.equals(savedConfig.getTranslationType()))
                || (!StringUtils.hasText(savedProvider) && "custom".equals(providerKey));
        return sameProvider ? customConfig : null;
    }

    private JSONObject getSavedBaiduConfig() {
        SysAiConfig savedConfig = sysAiConfigService.getArticleAiConfigInternal("default");
        if (savedConfig == null || !StringUtils.hasText(savedConfig.getBaiduConfig())) {
            return null;
        }
        return com.alibaba.fastjson.JSON.parseObject(savedConfig.getBaiduConfig());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = stringValue(map.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean hasPlainSecret(String value) {
        return StringUtils.hasText(value) && !value.contains("*");
    }

    /**
     * 从前端 tempConfig 中创建翻译用 ChatModel
     * 优先使用 translation_llm（独立翻译配置），否则使用全局 llm
     */
    private ChatModel createChatModelFromConfig(Map<String, Object> config) {
        if (config == null) {
            log.error("config 对象为空");
            return null;
        }

        try {
            // 确定使用哪个 LLM 配置
            Map<String, Object> llmConfig = null;
            String type = (String) config.getOrDefault("type", "llm");

            if ("dedicated_llm".equals(type)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> translationLlm = (Map<String, Object>) config.get("translation_llm");
                if (translationLlm != null) {
                    llmConfig = translationLlm;
                }
            }

            // 如果没有独立配置，使用全局 llm
            if (llmConfig == null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> globalLlm = (Map<String, Object>) config.get("llm");
                llmConfig = globalLlm;
            }

            if (llmConfig == null) {
                log.error("无法找到 LLM 配置");
                return null;
            }

            return createChatModelFromMap(llmConfig, "翻译测试");
        } catch (Exception e) {
            log.error("从 config 创建 ChatModel 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从前端 tempConfig 中创建摘要用 ChatModel
     * 优先使用 summary.dedicated_llm，否则使用全局 llm
     */
    private ChatModel createSummaryChatModelFromConfig(Map<String, Object> config) {
        if (config == null) {
            log.error("config 对象为空");
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> summaryConfig = (Map<String, Object>) config.get("summary");

            if (summaryConfig != null) {
                String summaryMode = (String) summaryConfig.getOrDefault("summaryMode", "global");
                if ("dedicated".equals(summaryMode)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dedicatedLlm = (Map<String, Object>) summaryConfig.get("dedicated_llm");
                    if (dedicatedLlm != null) {
                        return createChatModelFromMap(dedicatedLlm, "摘要独立AI");
                    }
                }
            }

            // 使用全局 llm
            @SuppressWarnings("unchecked")
            Map<String, Object> globalLlm = (Map<String, Object>) config.get("llm");
            if (globalLlm == null) {
                log.error("无法找到全局 LLM 配置用于摘要");
                return null;
            }
            return createChatModelFromMap(globalLlm, "摘要全局AI");
        } catch (Exception e) {
            log.error("从 config 创建摘要 ChatModel 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 Map 配置创建 ChatModel
     * Map 格式: {model, api_url, api_key, interface_type, timeout, ...}
     * OpenAI 兼容模型统一按 Chat Completions 调用。
     */
    private ChatModel createChatModelFromMap(Map<String, Object> llmConfig, String label) {
        try {
            SysAiConfig tempConfig = new SysAiConfig();
            tempConfig.setModel((String) llmConfig.get("model"));
            tempConfig.setApiBase((String) llmConfig.get("api_url"));
            tempConfig.setHttpReadTimeoutSeconds(toPositiveInteger(llmConfig.get("timeout")));
            Object maxTokens = llmConfig.get("max_tokens");
            if (maxTokens instanceof Number number) {
                tempConfig.setMaxTokens(number.intValue());
            }
            tempConfig.setTopP(toBigDecimal(llmConfig.get("top_p")));
            tempConfig.setFrequencyPenalty(toBigDecimal(llmConfig.get("frequency_penalty")));
            tempConfig.setPresencePenalty(toBigDecimal(llmConfig.get("presence_penalty")));
            Object reasoningEffort = llmConfig.get("reasoning_effort");
            if (reasoningEffort instanceof String effort && !effort.isBlank()) {
                tempConfig.setEnableThinking(true);
                tempConfig.setReasoningEffort(effort);
            }
            applyThinkingAdapterConfig(tempConfig, llmConfig);

            // API key 处理：前端仅在用户输入新密钥时才发送 api_key
            // 如果未发送，从数据库已保存的配置中读取
            String apiKey = (String) llmConfig.get("api_key");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = getFallbackApiKey(llmConfig);
            }
            tempConfig.setApiKey(apiKey);

            if (tempConfig.getApiKey() == null || tempConfig.getApiKey().isBlank()) {
                log.error("无法获取 API key：前端未提供且数据库中无已保存的配置");
                return null;
            }

            // interface_type 映射到 provider
            String interfaceType = (String) llmConfig.get("interface_type");
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

            // 翻译/测试使用较低温度确保准确性
            tempConfig.setTemperature(new java.math.BigDecimal("0.3"));

            log.info("从 tempConfig 创建 {} ChatModel: provider={}, model={}",
                    label, tempConfig.getProvider(), tempConfig.getModel());

            return chatClientFactory.createChatModel(tempConfig);
        } catch (Exception e) {
            log.error("从 Map 创建 {} ChatModel 失败: {}", label, e.getMessage(), e);
            return null;
        }
    }

    private void applyThinkingAdapterConfig(SysAiConfig tempConfig, Map<String, Object> llmConfig) {
        JSONObject extraConfig = new JSONObject();
        Object thinkingProfile = llmConfig.get("thinking_profile");
        if (thinkingProfile instanceof String profile && !profile.isBlank()) {
            extraConfig.put("thinkingProfile", profile);
        }
        Object thinkingExtraBody = llmConfig.get("thinking_extra_body");
        if (thinkingExtraBody != null) {
            extraConfig.put("thinkingExtraBody", thinkingExtraBody);
        }
        if (!extraConfig.isEmpty()) {
            tempConfig.setExtraConfig(extraConfig.toJSONString());
        }
    }

    /**
     * 从数据库已保存的 article_ai 配置中获取 API key 作为兜底
     * 根据 llmConfig 中的 api_url 匹配对应的密钥
     */
    private String getFallbackApiKey(Map<String, Object> llmConfig) {
        try {
            SysAiConfig savedConfig = sysAiConfigService.getArticleAiConfigInternal("default");
            if (savedConfig == null) {
                log.warn("数据库中无已保存的 article_ai 配置，无法兜底获取 API key");
                return null;
            }

            // 尝试从已保存配置的各级 JSON 中匹配 API key
            String requestUrl = (String) llmConfig.get("api_url");

            // 1. 尝试匹配全局 llmConfig
            String apiKey = extractApiKeyFromJson(savedConfig.getLlmConfig(), requestUrl);
            if (apiKey != null)
                return apiKey;

            // 2. 尝试匹配翻译独立 LLM 配置
            apiKey = extractApiKeyFromJson(savedConfig.getTranslationLlmConfig(), requestUrl);
            if (apiKey != null)
                return apiKey;

            // 3. 尝试匹配摘要配置中的 dedicated_llm
            if (savedConfig.getSummaryConfig() != null) {
                try {
                    com.alibaba.fastjson.JSONObject summaryJson = com.alibaba.fastjson.JSON
                            .parseObject(savedConfig.getSummaryConfig());
                    com.alibaba.fastjson.JSONObject dedicatedLlm = summaryJson.getJSONObject("dedicated_llm");
                    if (dedicatedLlm != null) {
                        String key = dedicatedLlm.getString("api_key");
                        if (key != null && !key.isBlank())
                            return key;
                    }
                } catch (Exception ignored) {
                }
            }

            // 4. 最后兜底：直接使用顶层 apiKey
            if (savedConfig.getApiKey() != null && !savedConfig.getApiKey().isBlank()) {
                log.info("使用顶层 apiKey 作为兜底");
                return savedConfig.getApiKey();
            }

            log.warn("数据库已保存配置中未找到匹配的 API key");
            return null;
        } catch (Exception e) {
            log.error("兜底获取 API key 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JSON 配置字符串中提取 api_key
     */
    private String extractApiKeyFromJson(String jsonConfig, String requestUrl) {
        if (jsonConfig == null || jsonConfig.isBlank())
            return null;
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(jsonConfig);
            String key = json.getString("api_key");
            if (key != null && !key.isBlank()) {
                return key;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer toPositiveInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int intValue = number.intValue();
            return intValue > 0 ? intValue : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                int intValue = Integer.parseInt(text.trim());
                return intValue > 0 ? intValue : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.math.BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new java.math.BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * 从 LLM 响应中提取 JSON 对象
     */
    private com.alibaba.fastjson.JSONObject extractJsonResponse(String text) {
        if (text == null)
            return null;

        // 尝试直接解析
        try {
            return com.alibaba.fastjson.JSON.parseObject(text.trim());
        } catch (Exception ignored) {
        }

        // 尝试从 markdown code block 中提取
        String cleaned = text;
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        } else if (cleaned.contains("```")) {
            cleaned = cleaned.substring(cleaned.indexOf("```") + 3);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        }

        try {
            return com.alibaba.fastjson.JSON.parseObject(cleaned.trim());
        } catch (Exception ignored) {
        }

        // 尝试找到第一个 { 和最后一个 }
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first >= 0 && last > first) {
            try {
                return com.alibaba.fastjson.JSON.parseObject(text.substring(first, last + 1));
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ========== 格式检测 ==========

    /**
     * 根据响应文本特征自动检测 LLM 返回的格式。
     * <p>规则：
     * <ul>
     *   <li>去掉首尾空白后以 { 或 [ 开头 → JSON</li>
     *   <li>包含换行+缩进 → TOON</li>
     *   <li>其余 → KEY_VALUE（后续再兜底纯文本）</li>
     * </ul>
     * 如需支持新格式，增加新的枚举值和检测逻辑即可。
     */
    private String detectResponseFormat(String response) {
        if (response == null || response.isBlank()) {
            return "plain";
        }
        String trimmed = response.trim();
        char first = trimmed.charAt(0);
        if (first == '{' || first == '[' || trimmed.startsWith("```json") || extractJsonResponse(trimmed) != null) {
            return "json";
        }
        if (trimmed.contains("\n  ") || trimmed.contains("\n\t")) {
            return "toon";
        }
        String[] lines = trimmed.split("\n");
        if (lines.length >= 2 && isCsvLike(lines)) {
            return "csv";
        }
        return "key_value";
    }

    private boolean isCsvLike(String[] lines) {
        int firstCommas = -1;
        int matched = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int commas = (int) trimmed.chars().filter(c -> c == ',').count();
            if (commas < 1) continue;
            if (firstCommas == -1) firstCommas = commas;
            if (commas == firstCommas) matched++;
        }
        return matched >= 2;
    }

    private String resolveSummaryMode(Map<String, Object> config) {
        if (config == null) {
            return "global";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> summaryConfig = (Map<String, Object>) config.get("summary");
        if (summaryConfig == null) {
            return "global";
        }
        Object mode = summaryConfig.get("summaryMode");
        return mode instanceof String summaryMode && StringUtils.hasText(summaryMode) ? summaryMode : "disabled";
    }

    private String inferPromptDataFormat(String prompt, String defaultFormat) {
        if (!StringUtils.hasText(prompt)) {
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

    private void addFormatEstimate(Map<String, Object> result, String inputFormat, String toonData,
            String jsonData, String csvData, Map<String, Object> articleData) {
        String selectedData = switch (inputFormat) {
            case "csv" -> csvData;
            case "toon" -> toonData;
            case "json" -> jsonData;
            default -> "";
        };
        if (!StringUtils.hasText(selectedData)) {
            return;
        }

        String traditionalJson = com.alibaba.fastjson.JSON.toJSONString(
                articleData, com.alibaba.fastjson.serializer.SerializerFeature.PrettyFormat);
        result.put("format_tokens", selectedData.length());
        result.put("toon_tokens", selectedData.length());
        result.put("token_baseline", "traditional_json");
        result.put("token_baseline_label", "传统JSON");
        if (traditionalJson.length() > 0) {
            double saved = (1.0 - (double) selectedData.length() / traditionalJson.length()) * 100;
            result.put("token_saved_percent", String.format("%.1f", saved));
        }
    }

    /**
     * 按检测到的格式解析摘要响应
     */
    private Map<String, String> parseSummaryResponse(String response, Map<String, String> languages) {
        String format = detectResponseFormat(response);
        Map<String, String> result = switch (format) {
            case "json" -> parseJsonSummary(response, languages);
            case "toon" -> parseToonSummary(response, languages);
            case "csv" -> parseCsvSummary(response, languages);
            default -> parseKeyValueSummary(response, languages);
        };
        if (result == null || result.isEmpty()) {
            result = new LinkedHashMap<>();
            result.put(languages.keySet().iterator().next(), response.trim());
        }
        return result;
    }

    private Map<String, String> ensureSummaryLengths(ChatModel chatModel, String sourceContent,
            Map<String, String> summaries, Map<String, String> languages, int maxLength) {
        if (summaries == null || summaries.isEmpty()) {
            return summaries;
        }
        Map<String, String> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : summaries.entrySet()) {
            adjusted.put(entry.getKey(), ensureSummaryLength(
                    chatModel, sourceContent, entry.getKey(), entry.getValue(), maxLength));
        }
        return adjusted;
    }

    private String ensureSummaryLength(ChatModel chatModel, String sourceContent, String languageName,
            String summary, int maxLength) {
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
                String repaired = ChatClient.create(chatModel)
                        .prompt()
                        .user(repairPrompt)
                        .call()
                        .content();
                if (StringUtils.hasText(repaired)) {
                    String normalized = clampSummaryLength(stripCodeFence(repaired), maxLength);
                    if (normalized.length() > current.length()) {
                        current = normalized;
                    }
                }
            } catch (Exception e) {
                log.warn("测试摘要长度修正失败，保留当前摘要: {}", e.getMessage());
                break;
            }

            if (!shouldExpandSummary(current, sourceContent, maxLength)) {
                break;
            }
        }
        return current;
    }

    private boolean shouldExpandSummary(String summary, String sourceContent, int maxLength) {
        if (!StringUtils.hasText(summary) || maxLength <= 0) {
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

    private Map<String, String> parseJsonSummary(String response, Map<String, String> languages) {
        com.alibaba.fastjson.JSONObject json = extractJsonResponse(response);
        if (json == null) return null;

        Map<String, String> nested = collectNestedSummaryStrings(json, languages);
        if (!nested.isEmpty()) return nested;

        Map<String, String> result = collectSummaryStrings(json, languages, true);
        if (!result.isEmpty()) return result;
        result = collectSummaryStrings(json, languages, false);
        return result.isEmpty() ? null : result;
    }

    private Map<String, String> collectNestedSummaryStrings(Map<?, ?> source, Map<String, String> languages) {
        Object summariesObj = source.get("summaries");
        if (summariesObj instanceof Map<?, ?> summariesMap) {
            Map<String, String> result = collectSummaryStrings(summariesMap, languages, true);
            if (!result.isEmpty()) return result;
            result = collectSummaryStrings(summariesMap, languages, false);
            if (!result.isEmpty()) return result;
        }

        for (Object value : source.values()) {
            if (value instanceof Map<?, ?> nestedMap) {
                Map<String, String> result = collectNestedSummaryStrings(nestedMap, languages);
                if (!result.isEmpty()) return result;
                result = collectSummaryStrings(nestedMap, languages, true);
                if (!result.isEmpty()) return result;
            }
        }
        return new LinkedHashMap<>();
    }

    private Map<String, String> collectSummaryStrings(
            Map<?, ?> source, Map<String, String> languages, boolean onlyRequestedLanguages) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = Objects.toString(entry.getKey(), "").trim();
            if (key.isBlank() || "summaries".equals(key)) continue;
            if (onlyRequestedLanguages && (languages == null || !languages.containsKey(key))) continue;

            Object value = entry.getValue();
            if (value instanceof String summary && !summary.isBlank()) {
                result.put(key, summary.trim());
            } else if (!onlyRequestedLanguages && value != null && !(value instanceof Map<?, ?>)) {
                String summary = Objects.toString(value, "").trim();
                if (!summary.isBlank()) result.put(key, summary);
            }
        }
        return result;
    }

    private Map<String, String> parseToonSummary(String response, Map<String, String> languages) {
        try {
            Map<String, Object> decoded = ToonFormatter.decode(response);
            if (decoded == null) return null;

            Map<String, String> nested = collectNestedSummaryStrings(decoded, languages);
            if (!nested.isEmpty()) return nested;

            Map<String, String> flat = collectSummaryStrings(decoded, languages, true);
            if (!flat.isEmpty()) return flat;
            flat = collectSummaryStrings(decoded, languages, false);
            return flat.isEmpty() ? null : flat;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, String> parseCsvSummary(String response, Map<String, String> languages) {
        Map<String, String> result = new LinkedHashMap<>();
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

        for (int i = valueStart; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() <= Math.max(langIndex, summaryIndex)) {
                continue;
            }
            String key = row.get(langIndex).replace("\"", "").replace("'", "").trim();
            String value = row.get(summaryIndex).trim();
            if (languages.containsKey(key) && !value.isBlank()) {
                result.put(key, value);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private Map<String, String> parseKeyValueSummary(String response, Map<String, String> languages) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : response.split("\n")) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("```") || line.toLowerCase().startsWith("lang") || line.toLowerCase().startsWith("summar"))
                continue;
            String[] parts = line.split("[：\\t:=]", 2);
            if (parts.length < 2) {
                parts = line.split("：", 2);
            }
            if (parts.length == 2) {
                String key = parts[0].replace("\"", "").replace("'", "").trim();
                String value = parts[1].replaceAll("^[\"']|[\"']$", "").trim();
                if (languages.containsKey(key) && !value.isBlank()) {
                    result.put(key, value);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String firstJsonString(com.alibaba.fastjson.JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 按检测到的格式解析翻译响应（TOON 文章翻译）
     */
    private Map<String, String> parseTranslationResponse(String response) {
        String format = detectResponseFormat(response);
        if ("json".equals(format)) {
            com.alibaba.fastjson.JSONObject json = extractJsonResponse(response);
            if (json != null) {
                Map<String, String> result = new LinkedHashMap<>();
                result.put("title", firstJsonString(json, "title", "translated_title"));
                result.put("content", firstJsonString(json, "content", "translated_content"));
                return result;
            }
        }
        if ("toon".equals(format)) {
            try {
                Map<String, Object> decoded = ToonFormatter.decode(response);
                if (decoded != null) {
                    Map<String, Object> article = decoded;
                    Object articleObj = decoded.get("article");
                    if (articleObj instanceof Map<?, ?> articleMap) {
                        article = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : articleMap.entrySet()) {
                            article.put(Objects.toString(entry.getKey(), ""), entry.getValue());
                        }
                    }
                    if (article != null) {
                        Map<String, String> result = new LinkedHashMap<>();
                        result.put("title", Objects.toString(article.get("title"), ""));
                        result.put("content", Objects.toString(article.get("content"), ""));
                        return result;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if ("csv".equals(format)) {
            Map<String, String> csvResult = parseCsvTranslation(response);
            if (csvResult != null) {
                return csvResult;
            }
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("title", "");
        fallback.put("content", response.trim());
        return fallback;
    }

    private Map<String, String> parseCsvTranslation(String response) {
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
            }
            if ("content".equals(header)) {
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

        Map<String, String> result = new LinkedHashMap<>();
        result.put("title", values.get(titleIndex).trim());
        result.put("content", values.get(contentIndex).trim());
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
                if (row.stream().anyMatch(StringUtils::hasText)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }

        row.add(field.toString());
        if (row.stream().anyMatch(StringUtils::hasText)) {
            rows.add(row);
        }
        return rows;
    }
}
