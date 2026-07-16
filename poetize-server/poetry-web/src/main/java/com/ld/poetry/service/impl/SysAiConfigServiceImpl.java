package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.SysAiConfigMapper;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.entity.Article;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.AiCommentSkillDefaults;
import com.ld.poetry.service.ai.AiThinkingAdapterRegistry;
import com.ld.poetry.service.ai.DynamicChatClientFactory;
import com.ld.poetry.utils.AESCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI配置服务实现类
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiConfigServiceImpl extends ServiceImpl<SysAiConfigMapper, SysAiConfig>
        implements SysAiConfigService {

    private final SysAiConfigMapper sysAiConfigMapper;
    private final ArticleMapper articleMapper;
    private final AESCryptoUtil aesCryptoUtil;
    private final DynamicChatClientFactory dynamicChatClientFactory;
    private final AiThinkingAdapterRegistry aiThinkingAdapterRegistry;
    private final JsonMapper objectMapper;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    @Lazy
    private com.ld.poetry.service.ai.LlmTranslationService llmTranslationService;

    @Autowired
    @Lazy
    private com.ld.poetry.service.CacheService cacheService;

    private static final String[] API_TRANSLATION_SECRET_FIELDS = {
            "api_key",
            "app_secret",
            "secret_key",
            "access_key_secret",
            "token",
            "subscription_key",
            "auth_key",
            "secret_access_key",
            "session_token",
            "api_key_or_iam_token"
    };

    private static final List<String> CUSTOM_CONFIG_API_TRANSLATION_TYPES = List.of(
            "custom",
            "youdao",
            "tencent",
            "aliyun",
            "volcengine",
            "huawei",
            "google",
            "azure_translator",
            "deepl",
            "aws",
            "yandex");

    // ========== 配置查询方法 ==========

    @Override
    public SysAiConfig getConfig(String configType, String configName) {
        if (!StringUtils.hasText(configName)) {
            configName = "default";
        }

        SysAiConfig config = sysAiConfigMapper.selectByTypeAndName(configType, configName);

        // 解密敏感字段并脱敏显示
        if (config != null) {
            decryptAndMaskConfig(config);
            applyDefaultCommentSkill(config);
        }

        return config;
    }

    @Override
    public SysAiConfig getAiChatConfig(String configName) {
        return getConfig("ai_chat", configName);
    }

    @Override
    public Map<String, Object> getStreamingConfig(String configName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取配置（已脱敏）
            SysAiConfig config = getAiChatConfig(configName);

            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                // 返回默认配置
                result.put("enabled", false);
                result.put("streaming_enabled", false);
                result.put("configured", false);
                result.put("require_login", false);
                result.put("chat_name", "AI助手");
                result.put("chat_avatar", "");
                result.put("welcome_message", "你好！我是你的AI助手，有什么可以帮助你的吗？");
                result.put("placeholder_text", "输入你想说的话...");
                result.put("theme_color", "#4facfe");
                result.put("enable_typing_indicator", true);
                result.put("show_timestamp", true);
                result.put("max_message_length", 500);
                result.put("max_conversation_length", 20);
                result.put("rate_limit", 20);
                result.put("vision_supported", false);
                result.put("vision_configured", false);
                return result;
            }

            // 基础配置
            result.put("enabled", config.getEnabled());
            result.put("streaming_enabled", Boolean.TRUE.equals(config.getEnableStreaming()));
            result.put("configured", StringUtils.hasText(config.getProvider())
                    && StringUtils.hasText(config.getApiKey())
                    && StringUtils.hasText(config.getModel()));

            // 聊天配置
            result.put("require_login", Boolean.TRUE.equals(config.getRequireLogin()));
            result.put("chat_name", StringUtils.hasText(config.getChatName()) ? config.getChatName() : "AI助手");
            result.put("chat_avatar", StringUtils.hasText(config.getChatAvatar()) ? config.getChatAvatar() : "");
            result.put("welcome_message", StringUtils.hasText(config.getWelcomeMessage())
                    ? config.getWelcomeMessage()
                    : "你好！我是你的AI助手，有什么可以帮助你的吗？");
            result.put("placeholder_text", StringUtils.hasText(config.getPlaceholderText())
                    ? config.getPlaceholderText()
                    : "输入你想说的话...");
            result.put("theme_color", StringUtils.hasText(config.getThemeColor()) ? config.getThemeColor() : "#4facfe");
            result.put("max_message_length", config.getMaxMessageLength() != null ? config.getMaxMessageLength() : 500);
            result.put("max_conversation_length",
                    config.getMaxConversationLength() != null ? config.getMaxConversationLength() : 20);
            result.put("rate_limit", config.getRateLimit() != null ? config.getRateLimit() : 20);
            result.put("enable_content_filter", Boolean.TRUE.equals(config.getEnableContentFilter()));

            // 显示配置
            result.put("enable_typing_indicator", Boolean.TRUE.equals(config.getEnableTypingIndicator()));
            result.put("show_timestamp", !Boolean.FALSE.equals(config.getShowTimestamp()));
            result.put("enable_chat_history", Boolean.TRUE.equals(config.getEnableChatHistory()));

            // 视觉配置：前端据此决定是否展示图片上传入口
            boolean visionSupported = Boolean.TRUE.equals(config.getVisionSupported());
            boolean visionModelConfigured = StringUtils.hasText(config.getVisionProvider())
                    && StringUtils.hasText(config.getVisionApiKey())
                    && StringUtils.hasText(config.getVisionModel());
            result.put("vision_supported", visionSupported);
            result.put("vision_configured", visionSupported || visionModelConfigured);

        } catch (Exception e) {
            log.error("获取流式响应配置失败: {}", e.getMessage(), e);
            // 返回默认配置
            result.put("enabled", false);
            result.put("streaming_enabled", false);
            result.put("configured", false);
            result.put("require_login", false);
            result.put("chat_name", "AI助手");
            result.put("chat_avatar", "");
            result.put("welcome_message", "你好！我是你的AI助手，有什么可以帮助你的吗？");
            result.put("placeholder_text", "输入你想说的话...");
            result.put("theme_color", "#4facfe");
            result.put("enable_typing_indicator", true);
            result.put("show_timestamp", true);
            result.put("max_message_length", 500);
            result.put("max_conversation_length", 20);
            result.put("rate_limit", 20);
            result.put("vision_supported", false);
            result.put("vision_configured", false);
        }

        return result;
    }

    @Override
    public SysAiConfig getAiChatConfigInternal(String configName) {
        return getDecryptedConfig("ai_chat", configName);
    }

    @Override
    public SysAiConfig getArticleAiConfig(String configName) {
        return getConfig("article_ai", configName);
    }

    @Override
    public SysAiConfig getArticleAiConfigInternal(String configName) {
        return getDecryptedConfig("article_ai", configName);
    }

    @Override
    public String resolveImageConfigSecretsForTest(String incomingImageConfig, String configName) {
        if (!StringUtils.hasText(incomingImageConfig)) {
            return incomingImageConfig;
        }
        try {
            // getArticleAiConfigInternal 返回已解密（未脱敏）的配置，可直接用于测试
            SysAiConfig saved = getArticleAiConfigInternal(
                    StringUtils.hasText(configName) ? configName : "default");
            if (saved == null || !StringUtils.hasText(saved.getImageConfig())) {
                return incomingImageConfig;
            }
            return mergeImageConfigSecretsFromDecrypted(incomingImageConfig, saved.getImageConfig());
        } catch (Exception e) {
            log.warn("生图测试回填密钥失败: {}", e.getMessage());
            return incomingImageConfig;
        }
    }

    @Override
    public SysAiConfig getAiApiConfig(String configName) {
        return getConfig("ai_api", configName);
    }

    // ========== 配置保存方法 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(SysAiConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigType())) {
            log.error("保存配置失败：配置对象或配置类型为空");
            return false;
        }

        try {
            // 设置默认配置名称
            if (!StringUtils.hasText(config.getConfigName())) {
                config.setConfigName("default");
            }

            // 加密敏感字段
            encryptSensitiveFields(config);

            // 检查是否已存在
            SysAiConfig existingConfig = sysAiConfigMapper.selectByTypeAndName(
                    config.getConfigType(), config.getConfigName());

            boolean success;
            if (existingConfig != null) {
                // 更新现有配置
                config.setId(existingConfig.getId());
                success = updateById(config);
                log.info("更新AI配置成功: type={}, name={}", config.getConfigType(), config.getConfigName());
            } else {
                // 插入新配置
                success = save(config);
                log.info("插入AI配置成功: type={}, name={}", config.getConfigType(), config.getConfigName());
            }

            return success;

        } catch (Exception e) {
            log.error("保存AI配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存AI配置失败: " + e.getMessage());
        }
    }

    @Override
    public boolean saveAiChatConfig(SysAiConfig config) {
        config.setConfigType("ai_chat");
        applyDefaultCommentSkill(config);
        normalizeRagConfig(config);
        return saveOrUpdateConfig(config);
    }

    @Override
    public boolean saveArticleAiConfig(SysAiConfig config) {
        config.setConfigType("article_ai");

        // 业务逻辑验证：如果系统中已有文章，不允许修改源语言
        SysAiConfig existingConfig = sysAiConfigMapper.selectByTypeAndName("article_ai",
                config.getConfigName() != null ? config.getConfigName() : "default");
        if (existingConfig != null && existingConfig.getDefaultSourceLang() != null) {
            // 检查是否修改了源语言
            if (config.getDefaultSourceLang() != null
                    && !config.getDefaultSourceLang().equals(existingConfig.getDefaultSourceLang())) {
                // 检查系统中是否已有文章
                if (hasArticles()) {
                    log.warn("系统中已有文章数据，不允许修改源语言从 {} 到 {}", existingConfig.getDefaultSourceLang(),
                            config.getDefaultSourceLang());
                    throw new RuntimeException("系统中已有文章数据，不允许修改源语言配置。修改源语言会导致现有文章的语言标识混乱，影响SEO和翻译关系。");
                }
            }
        }

        if (existingConfig != null) {
            preserveMissingArticleAiSecrets(config, existingConfig);
        }

        boolean success = saveOrUpdateConfig(config);
        if (success) {
            // 清除文章 AI 默认语言缓存（default_source_lang / default_target_lang 可能被修改）
            cacheService.evictArticleAiDefaultLang();
        }
        return success;
    }

    private void preserveMissingArticleAiSecrets(SysAiConfig config, SysAiConfig existingConfig) {
        try {
            boolean sameTranslationType = Objects.equals(config.getTranslationType(), existingConfig.getTranslationType());

            if (sameTranslationType && "baidu".equals(config.getTranslationType())) {
                config.setBaiduConfig(preserveJsonTextFields(
                        config.getBaiduConfig(), existingConfig.getBaiduConfig(), "app_secret"));
            }

            if (sameTranslationType && CUSTOM_CONFIG_API_TRANSLATION_TYPES.contains(config.getTranslationType())) {
                config.setCustomConfig(preserveJsonTextFields(
                        config.getCustomConfig(), existingConfig.getCustomConfig(), API_TRANSLATION_SECRET_FIELDS));
            }

            config.setLlmConfig(preserveJsonTextFields(
                    config.getLlmConfig(), existingConfig.getLlmConfig(), "api_key"));
            config.setTranslationLlmConfig(preserveJsonTextFields(
                    config.getTranslationLlmConfig(), existingConfig.getTranslationLlmConfig(), "api_key"));
            config.setSummaryConfig(preserveSummaryDedicatedLlmSecret(
                    config.getSummaryConfig(), existingConfig.getSummaryConfig()));
            config.setImageConfig(preserveImageConfigSecrets(
                    config.getImageConfig(), existingConfig.getImageConfig()));
        } catch (Exception e) {
            log.warn("保留已有密钥失败，将按本次提交配置保存: {}", e.getMessage());
        }
    }

    private String preserveJsonTextFields(String incomingJson, String existingJson, String... keys) throws Exception {
        if (!StringUtils.hasText(incomingJson) || !StringUtils.hasText(existingJson)) {
            return incomingJson;
        }

        JsonNode incomingNode = objectMapper.readTree(incomingJson);
        JsonNode existingNode = objectMapper.readTree(existingJson);
        if (!(incomingNode instanceof ObjectNode incomingObject)) {
            return incomingJson;
        }

        boolean modified = false;
        for (String key : keys) {
            if (isMissing(incomingObject, key)
                    && existingNode.has(key)
                    && StringUtils.hasText(existingNode.get(key).asText())) {
                incomingObject.put(key, resolveStoredSecretForSave(existingNode.get(key).asText()));
                modified = true;
            }
        }

        return modified ? objectMapper.writeValueAsString(incomingObject) : incomingJson;
    }

    private String preserveSummaryDedicatedLlmSecret(String incomingJson, String existingJson) throws Exception {
        if (!StringUtils.hasText(incomingJson) || !StringUtils.hasText(existingJson)) {
            return incomingJson;
        }

        JsonNode incomingNode = objectMapper.readTree(incomingJson);
        JsonNode existingNode = objectMapper.readTree(existingJson);
        if (!(incomingNode instanceof ObjectNode incomingObject)) {
            return incomingJson;
        }

        JsonNode incomingDedicated = incomingObject.get("dedicated_llm");
        JsonNode existingDedicated = existingNode.get("dedicated_llm");
        if (!(incomingDedicated instanceof ObjectNode incomingDedicatedObject)
                || existingDedicated == null
                || !existingDedicated.has("api_key")
                || !StringUtils.hasText(existingDedicated.get("api_key").asText())
                || !isMissing(incomingDedicatedObject, "api_key")) {
            return incomingJson;
        }

        incomingDedicatedObject.put("api_key", resolveStoredSecretForSave(existingDedicated.get("api_key").asText()));
        return objectMapper.writeValueAsString(incomingObject);
    }

    /**
     * 保留生图配置中的 api_key 与 dedicated_llm.api_key（前端提交缺失或 *** 占位时复用已存储密钥）
     */
    private String preserveImageConfigSecrets(String incomingJson, String existingJson) throws Exception {
        if (!StringUtils.hasText(incomingJson) || !StringUtils.hasText(existingJson)) {
            return incomingJson;
        }

        JsonNode incomingNode = objectMapper.readTree(incomingJson);
        JsonNode existingNode = objectMapper.readTree(existingJson);
        if (!(incomingNode instanceof ObjectNode incomingObject)) {
            return incomingJson;
        }

        boolean modified = false;

        // 保留顶层 api_key（生图服务商密钥）
        if (needsSecretBackfill(incomingObject, "api_key")
                && existingNode.has("api_key")
                && StringUtils.hasText(existingNode.get("api_key").asText())) {
            incomingObject.put("api_key", resolveStoredSecretForSave(existingNode.get("api_key").asText()));
            modified = true;
        }

        // 保留 dedicated_llm.api_key（独立LLM提炼prompt的密钥）
        JsonNode incomingDedicated = incomingObject.get("dedicated_llm");
        JsonNode existingDedicated = existingNode.get("dedicated_llm");
        if (incomingDedicated instanceof ObjectNode incomingDedicatedObject
                && existingDedicated != null
                && existingDedicated.has("api_key")
                && StringUtils.hasText(existingDedicated.get("api_key").asText())
                && needsSecretBackfill(incomingDedicatedObject, "api_key")) {
            incomingDedicatedObject.put("api_key", resolveStoredSecretForSave(existingDedicated.get("api_key").asText()));
            modified = true;
        }

        return modified ? objectMapper.writeValueAsString(incomingObject) : incomingJson;
    }

    private boolean isMissing(ObjectNode node, String key) {
        return !node.has(key);
    }

    /**
     * 判断密钥字段是否需要回填：字段缺失，或值为 {@code ***} 脱敏占位时返回 true。
     */
    private boolean needsSecretBackfill(ObjectNode node, String key) {
        if (!node.has(key)) {
            return true;
        }
        return "***".equals(node.get(key).asText());
    }

    /**
     * 将已解密的生图配置密钥合并到前端提交的配置中（用于测试场景）。
     *
     * <p>与 {@link #preserveImageConfigSecrets} 的区别：existingJson 中的 api_key 已是解密明文，
     * 无需再调用 {@link #resolveStoredSecretForSave}。仅合并 api_key 与 dedicated_llm.api_key，
     * 保留 incoming 中其它字段的当前值。
     */
    private String mergeImageConfigSecretsFromDecrypted(String incomingJson, String decryptedExistingJson) throws Exception {
        if (!StringUtils.hasText(incomingJson) || !StringUtils.hasText(decryptedExistingJson)) {
            return incomingJson;
        }

        JsonNode incomingNode = objectMapper.readTree(incomingJson);
        JsonNode existingNode = objectMapper.readTree(decryptedExistingJson);
        if (!(incomingNode instanceof ObjectNode incomingObject)) {
            return incomingJson;
        }

        boolean modified = false;

        if (needsSecretBackfill(incomingObject, "api_key")
                && existingNode.has("api_key")
                && StringUtils.hasText(existingNode.get("api_key").asText())) {
            incomingObject.put("api_key", existingNode.get("api_key").asText());
            modified = true;
        }

        JsonNode incomingDedicated = incomingObject.get("dedicated_llm");
        JsonNode existingDedicated = existingNode.get("dedicated_llm");
        if (incomingDedicated instanceof ObjectNode incomingDedicatedObject
                && existingDedicated != null
                && existingDedicated.has("api_key")
                && StringUtils.hasText(existingDedicated.get("api_key").asText())
                && needsSecretBackfill(incomingDedicatedObject, "api_key")) {
            incomingDedicatedObject.put("api_key", existingDedicated.get("api_key").asText());
            modified = true;
        }

        return modified ? objectMapper.writeValueAsString(incomingObject) : incomingJson;
    }

    private String resolveStoredSecretForSave(String storedValue) {
        String decrypted = aesCryptoUtil.decrypt(storedValue);
        return decrypted != null ? decrypted : storedValue;
    }

    private boolean encryptJsonSecretFields(ObjectNode node, String... fields) {
        boolean modified = false;
        for (String field : fields) {
            if (!node.has(field)) {
                continue;
            }
            String value = node.get(field).asText();
            if (StringUtils.hasText(value) && !value.startsWith("ENC(") && !value.contains("*")) {
                node.put(field, aesCryptoUtil.encrypt(value));
                modified = true;
            }
        }
        return modified;
    }

    private boolean decryptJsonSecretFields(ObjectNode node, boolean mask, String... fields) {
        boolean modified = false;
        for (String field : fields) {
            if (!node.has(field)) {
                continue;
            }
            String encrypted = node.get(field).asText();
            if (!StringUtils.hasText(encrypted)) {
                continue;
            }
            String decrypted = aesCryptoUtil.decrypt(encrypted);
            if (decrypted != null) {
                node.put(field, mask ? "***" : decrypted);
                modified = true;
            }
        }
        return modified;
    }

    /**
     * 检查系统中是否已有文章数据
     */
    @Override
    public boolean hasArticles() {
        try {
            // 查询文章表，检查是否有数据
            Long count = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                    .last("LIMIT 1"));
            boolean hasArticles = count != null && count > 0;
            log.info("检查文章数据结果: 总数={}, 有文章={}", count, hasArticles);
            return hasArticles;
        } catch (Exception e) {
            // 查询失败时为了安全起见，假设有文章数据，阻止修改源语言
            log.error("检查文章数据失败，默认认为有文章: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public boolean saveAiApiConfig(SysAiConfig config) {
        config.setConfigType("ai_api");
        return saveOrUpdateConfig(config);
    }

    // ========== 其他功能方法 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(Integer id) {
        if (id == null) {
            return false;
        }

        try {
            SysAiConfig config = getById(id);
            if (config == null) {
                log.error("配置不存在: id={}", id);
                return false;
            }

            boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
            int rows = sysAiConfigMapper.updateEnabled(id, newEnabled);

            log.info("切换配置启用状态成功: id={}, enabled={}", id, newEnabled);
            return rows > 0;

        } catch (Exception e) {
            log.error("切换配置启用状态失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> testConnection(SysAiConfig config) {
        Map<String, Object> result = new HashMap<>();

        if (config == null) {
            result.put("success", false);
            result.put("message", "配置对象为空");
            return result;
        }

        try {
            String configType = config.getConfigType();

            if ("ai_chat".equals(configType) || "ai_api".equals(configType)) {
                return testAiApiConnection(config);
            } else if ("translation".equals(configType)) {
                return testTranslationConnection(config);
            } else {
                result.put("success", false);
                result.put("message", "不支持的配置类型: " + configType);
                return result;
            }

        } catch (Exception e) {
            log.error("测试连接失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "测试连接失败: " + e.getMessage());
            return result;
        }
    }

    @Override
    public List<SysAiConfig> listAllConfigs() {
        List<SysAiConfig> configs = list(new LambdaQueryWrapper<SysAiConfig>()
                .orderByAsc(SysAiConfig::getConfigType)
                .orderByAsc(SysAiConfig::getId));

        // 解密并脱敏所有配置
        configs.forEach(this::decryptAndMaskConfig);

        return configs;
    }

    @Override
    public List<SysAiConfig> listConfigsByType(String configType) {
        List<SysAiConfig> configs = sysAiConfigMapper.selectByType(configType);

        // 解密并脱敏所有配置
        configs.forEach(this::decryptAndMaskConfig);

        return configs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConfig(Integer id) {
        if (id == null) {
            return false;
        }

        try {
            // 删除前先读取配置类型，用于决定是否清理缓存
            SysAiConfig existing = sysAiConfigMapper.selectById(id);
            boolean success = removeById(id);
            if (success) {
                log.info("删除AI配置成功: id={}", id);
                // 防御性 evict: 若删除的是 article_ai 配置，清掉 defaultLang 缓存
                if (existing != null && "article_ai".equals(existing.getConfigType())) {
                    cacheService.evictArticleAiDefaultLang();
                }
            }
            return success;

        } catch (Exception e) {
            log.error("删除AI配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getDefaultLanguages() {
        // 优先走 Redis 缓存（文章页每次访问都会调用，高频读）
        Map<String, Object> cached = cacheService.getCachedArticleAiDefaultLang();
        if (cached != null) {
            return cached;
        }

        Map<String, Object> result = new HashMap<>();

        // 只需要语言配置，直接查询数据库，无需解密敏感字段
        SysAiConfig config = sysAiConfigMapper.selectByTypeAndName("article_ai", "default");

        if (config != null) {
            result.put("default_source_lang",
                    config.getDefaultSourceLang() != null ? config.getDefaultSourceLang() : "zh");
            result.put("default_target_lang",
                    config.getDefaultTargetLang() != null ? config.getDefaultTargetLang() : "en");
        } else {
            // 配置不存在，返回默认值
            result.put("default_source_lang", "zh");
            result.put("default_target_lang", "en");
        }

        // 写入永久缓存（saveArticleAiConfig 时主动 evict）
        cacheService.cacheArticleAiDefaultLang(result);

        return result;
    }

    @Override
    public Map<String, String> getLanguageMapping() {
        // 优先走 Redis 缓存（数据源为硬编码 Map，运行期不变，永久缓存）
        Map<String, String> cached = cacheService.getCachedLanguageMapping();
        if (cached != null) {
            return cached;
        }

        // 前台展示用语言映射（原生语言文字）
        Map<String, String> mapping = new HashMap<>();
        mapping.put("zh", "中文");
        mapping.put("zh-TW", "繁體中文");
        mapping.put("en", "English");
        mapping.put("ja", "日本語");
        mapping.put("ko", "한국어");
        mapping.put("fr", "Français");
        mapping.put("de", "Deutsch");
        mapping.put("es", "Español");
        mapping.put("ru", "Русский");
        mapping.put("pt", "Português");
        mapping.put("it", "Italiano");
        mapping.put("ar", "العربية");
        mapping.put("th", "ไทย");
        mapping.put("vi", "Tiếng Việt");
        mapping.put("auto", "Auto Detect");

        // 写入永久缓存
        cacheService.cacheLanguageMapping(mapping);

        return mapping;
    }

    @Override
    public Map<String, String> getLanguageMappingAdmin() {
        // 后台管理用语言映射（中文）
        Map<String, String> mapping = new HashMap<>();
        mapping.put("zh", "中文");
        mapping.put("zh-TW", "繁体中文");
        mapping.put("en", "英文");
        mapping.put("ja", "日文");
        mapping.put("ko", "韩文");
        mapping.put("fr", "法文");
        mapping.put("de", "德文");
        mapping.put("es", "西班牙文");
        mapping.put("ru", "俄文");
        mapping.put("pt", "葡萄牙文");
        mapping.put("it", "意大利文");
        mapping.put("ar", "阿拉伯文");
        mapping.put("th", "泰文");
        mapping.put("vi", "越南文");
        mapping.put("auto", "自动检测");
        return mapping;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 加密敏感字段
     *
     * @param config AI配置对象
     */
    private void encryptSensitiveFields(SysAiConfig config) {
        // 加密API密钥
        if (StringUtils.hasText(config.getApiKey())) {
            String encrypted = aesCryptoUtil.encrypt(config.getApiKey());
            config.setApiKey(encrypted);
        }

        // 加密Mem0 API密钥
        if (StringUtils.hasText(config.getMem0ApiKey())) {
            String encrypted = aesCryptoUtil.encrypt(config.getMem0ApiKey());
            config.setMem0ApiKey(encrypted);
        }

        // 加密视觉模型API密钥
        if (StringUtils.hasText(config.getVisionApiKey())) {
            String encrypted = aesCryptoUtil.encrypt(config.getVisionApiKey());
            config.setVisionApiKey(encrypted);
        }

        // 加密 Jina Reader API Key（与 apiKey/visionApiKey 同套 AES 加密）
        if (StringUtils.hasText(config.getJinaApiKey())) {
            String encrypted = aesCryptoUtil.encrypt(config.getJinaApiKey());
            config.setJinaApiKey(encrypted);
        }

        // 加密JSON字段中的敏感信息
        encryptJsonFields(config);
    }

    /**
     * 加密JSON字段中的敏感信息
     */
    private void encryptJsonFields(SysAiConfig config) {
        try {
            // 1. 加密百度翻译配置中的app_secret
            if (StringUtils.hasText(config.getBaiduConfig())) {
                JsonNode baiduNode = objectMapper.readTree(config.getBaiduConfig());
                if (baiduNode.has("app_secret")) {
                    String appSecret = baiduNode.get("app_secret").asText();
                    if (StringUtils.hasText(appSecret) && !appSecret.startsWith("ENC(")) {
                        ((ObjectNode) baiduNode).put("app_secret", aesCryptoUtil.encrypt(appSecret));
                        config.setBaiduConfig(objectMapper.writeValueAsString(baiduNode));
                    }
                }
            }

            // 2. 加密API翻译扩展配置中的密钥类字段
            if (StringUtils.hasText(config.getCustomConfig())) {
                JsonNode customNode = objectMapper.readTree(config.getCustomConfig());
                if (customNode instanceof ObjectNode customObject
                        && encryptJsonSecretFields(customObject, API_TRANSLATION_SECRET_FIELDS)) {
                    config.setCustomConfig(objectMapper.writeValueAsString(customNode));
                }
            }

            // 3. 加密LLM配置中的api_key
            if (StringUtils.hasText(config.getLlmConfig())) {
                JsonNode llmNode = objectMapper.readTree(config.getLlmConfig());
                if (llmNode.has("api_key")) {
                    String apiKey = llmNode.get("api_key").asText();
                    if (StringUtils.hasText(apiKey) && !apiKey.startsWith("ENC(")) {
                        ((ObjectNode) llmNode).put("api_key", aesCryptoUtil.encrypt(apiKey));
                        config.setLlmConfig(objectMapper.writeValueAsString(llmNode));
                    }
                }
            }

            // 4. 加密摘要配置中的dedicated_llm.api_key
            if (StringUtils.hasText(config.getSummaryConfig())) {
                JsonNode summaryNode = objectMapper.readTree(config.getSummaryConfig());

                if (summaryNode.has("dedicated_llm")) {
                    JsonNode dedicatedLlmNode = summaryNode.get("dedicated_llm");

                    if (dedicatedLlmNode.has("api_key")) {
                        String apiKey = dedicatedLlmNode.get("api_key").asText();
                        if (StringUtils.hasText(apiKey) && !apiKey.startsWith("ENC(")) {
                            ((ObjectNode) dedicatedLlmNode).put("api_key", aesCryptoUtil.encrypt(apiKey));
                            config.setSummaryConfig(objectMapper.writeValueAsString(summaryNode));
                        }
                    }
                }
            }

            // 5. 加密生图配置中的 api_key 与 dedicated_llm.api_key
            if (StringUtils.hasText(config.getImageConfig())) {
                JsonNode imageNode = objectMapper.readTree(config.getImageConfig());
                boolean imageModified = false;

                if (imageNode.has("api_key")) {
                    String apiKey = imageNode.get("api_key").asText();
                    if (StringUtils.hasText(apiKey) && !apiKey.startsWith("ENC(")) {
                        ((ObjectNode) imageNode).put("api_key", aesCryptoUtil.encrypt(apiKey));
                        imageModified = true;
                    }
                }

                if (imageNode.has("dedicated_llm")) {
                    JsonNode dedicatedLlmNode = imageNode.get("dedicated_llm");
                    if (dedicatedLlmNode.has("api_key")) {
                        String apiKey = dedicatedLlmNode.get("api_key").asText();
                        if (StringUtils.hasText(apiKey) && !apiKey.startsWith("ENC(")) {
                            ((ObjectNode) dedicatedLlmNode).put("api_key", aesCryptoUtil.encrypt(apiKey));
                            imageModified = true;
                        }
                    }
                }

                if (imageModified) {
                    config.setImageConfig(objectMapper.writeValueAsString(imageNode));
                }
            }

            // 6. 加密 extraConfig.rag.embeddingApiKey
            if (StringUtils.hasText(config.getExtraConfig())) {
                JsonNode extraNode = objectMapper.readTree(config.getExtraConfig());
                JsonNode ragNode = extraNode.get("rag");
                if (ragNode instanceof ObjectNode ragObject && ragObject.has("embeddingApiKey")) {
                    String apiKey = ragObject.get("embeddingApiKey").asText();
                    if (StringUtils.hasText(apiKey) && !apiKey.contains("*")) {
                        ragObject.put("embeddingApiKey", aesCryptoUtil.encrypt(apiKey));
                        config.setExtraConfig(objectMapper.writeValueAsString(extraNode));
                    }
                }
            }

        } catch (Exception e) {
            log.error("加密JSON字段失败: {}", e.getMessage(), e);
            throw new RuntimeException("加密配置失败: " + e.getMessage());
        }
    }

    /**
     * 解密敏感字段并脱敏显示
     *
     * @param config AI配置对象
     */
    private void decryptAndMaskConfig(SysAiConfig config) {
        // 解密并脱敏API密钥
        if (StringUtils.hasText(config.getApiKey())) {
            String decrypted = aesCryptoUtil.decrypt(config.getApiKey());
            if (decrypted != null) {
                config.setApiKey(aesCryptoUtil.mask(decrypted));
            }
        }

        // 解密并脱敏Mem0 API密钥
        if (StringUtils.hasText(config.getMem0ApiKey())) {
            String decrypted = aesCryptoUtil.decrypt(config.getMem0ApiKey());
            if (decrypted != null) {
                config.setMem0ApiKey(aesCryptoUtil.mask(decrypted));
            }
        }

        // 解密并脱敏视觉模型API密钥
        if (StringUtils.hasText(config.getVisionApiKey())) {
            String decrypted = aesCryptoUtil.decrypt(config.getVisionApiKey());
            if (decrypted != null) {
                config.setVisionApiKey(aesCryptoUtil.mask(decrypted));
            }
        }

        // 解密并脱敏 Jina Reader API Key
        if (StringUtils.hasText(config.getJinaApiKey())) {
            String decrypted = aesCryptoUtil.decrypt(config.getJinaApiKey());
            if (decrypted != null) {
                config.setJinaApiKey(aesCryptoUtil.mask(decrypted));
            }
        }

        // 解密并脱敏JSON字段中的敏感信息
        decryptAndMaskJsonFields(config);
    }

    /**
     * 解密并脱敏JSON字段中的敏感信息（用于前端显示）
     */
    private void decryptAndMaskJsonFields(SysAiConfig config) {
        try {
            // 1. 解密并脱敏百度配置
            if (StringUtils.hasText(config.getBaiduConfig())) {
                JsonNode baiduNode = objectMapper.readTree(config.getBaiduConfig());
                if (baiduNode.has("app_secret")) {
                    String encrypted = baiduNode.get("app_secret").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            // 前端通过检查是否有值来显示"已有密钥"提示
                            ((ObjectNode) baiduNode).put("app_secret", "***");
                        }
                        config.setBaiduConfig(objectMapper.writeValueAsString(baiduNode));
                    }
                }
            }

            // 2. 解密并脱敏API翻译扩展配置
            if (StringUtils.hasText(config.getCustomConfig())) {
                JsonNode customNode = objectMapper.readTree(config.getCustomConfig());
                if (customNode instanceof ObjectNode customObject
                        && decryptJsonSecretFields(customObject, true, API_TRANSLATION_SECRET_FIELDS)) {
                    config.setCustomConfig(objectMapper.writeValueAsString(customNode));
                }
            }

            // 3. 解密并脱敏LLM配置
            if (StringUtils.hasText(config.getLlmConfig())) {
                JsonNode llmNode = objectMapper.readTree(config.getLlmConfig());
                if (llmNode.has("api_key")) {
                    String encrypted = llmNode.get("api_key").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ((ObjectNode) llmNode).put("api_key", "***");
                            config.setLlmConfig(objectMapper.writeValueAsString(llmNode));
                        }
                    }
                }
            }

            // 4. 解密并脱敏摘要配置中的dedicated_llm
            if (StringUtils.hasText(config.getSummaryConfig())) {
                JsonNode summaryNode = objectMapper.readTree(config.getSummaryConfig());

                if (summaryNode.has("dedicated_llm")) {
                    JsonNode dedicatedLlmNode = summaryNode.get("dedicated_llm");

                    if (dedicatedLlmNode.has("api_key")) {
                        String encrypted = dedicatedLlmNode.get("api_key").asText();
                        if (StringUtils.hasText(encrypted)) {
                            String decrypted = aesCryptoUtil.decrypt(encrypted);
                            if (decrypted != null) {
                                ((ObjectNode) dedicatedLlmNode).put("api_key", "***");
                                config.setSummaryConfig(objectMapper.writeValueAsString(summaryNode));
                            }
                        }
                    }
                }
            }

            // 5. 解密并脱敏生图配置中的 api_key 与 dedicated_llm.api_key
            if (StringUtils.hasText(config.getImageConfig())) {
                JsonNode imageNode = objectMapper.readTree(config.getImageConfig());
                boolean imageModified = false;

                if (imageNode.has("api_key")) {
                    String encrypted = imageNode.get("api_key").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ((ObjectNode) imageNode).put("api_key", "***");
                            imageModified = true;
                        }
                    }
                }

                if (imageNode.has("dedicated_llm")) {
                    JsonNode dedicatedLlmNode = imageNode.get("dedicated_llm");
                    if (dedicatedLlmNode.has("api_key")) {
                        String encrypted = dedicatedLlmNode.get("api_key").asText();
                        if (StringUtils.hasText(encrypted)) {
                            String decrypted = aesCryptoUtil.decrypt(encrypted);
                            if (decrypted != null) {
                                ((ObjectNode) dedicatedLlmNode).put("api_key", "***");
                                imageModified = true;
                            }
                        }
                    }
                }

                if (imageModified) {
                    config.setImageConfig(objectMapper.writeValueAsString(imageNode));
                }
            }

            // 6. 解密并脱敏 extraConfig.rag.embeddingApiKey
            if (StringUtils.hasText(config.getExtraConfig())) {
                JsonNode extraNode = objectMapper.readTree(config.getExtraConfig());
                JsonNode ragNode = extraNode.get("rag");
                if (ragNode instanceof ObjectNode ragObject && ragObject.has("embeddingApiKey")) {
                    String encrypted = ragObject.get("embeddingApiKey").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ragObject.put("embeddingApiKey", "***");
                            config.setExtraConfig(objectMapper.writeValueAsString(extraNode));
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("解密并脱敏JSON字段失败: {}", e.getMessage(), e);
            // 不抛异常，避免影响配置读取
        }
    }

    /**
     * 获取解密后的配置（用于内部调用，不脱敏）
     *
     * @param configType 配置类型
     * @param configName 配置名称
     * @return 解密后的配置
     */
    private SysAiConfig getDecryptedConfig(String configType, String configName) {
        SysAiConfig config = sysAiConfigMapper.selectByTypeAndName(configType, configName);

        if (config != null) {
            // 仅解密，不脱敏（用于内部调用）
            if (StringUtils.hasText(config.getApiKey())) {
                String decrypted = aesCryptoUtil.decrypt(config.getApiKey());
                config.setApiKey(decrypted);
            }

            if (StringUtils.hasText(config.getMem0ApiKey())) {
                String decrypted = aesCryptoUtil.decrypt(config.getMem0ApiKey());
                config.setMem0ApiKey(decrypted);
            }

            // 解密视觉模型API密钥（不脱敏，供内部调用使用）
            if (StringUtils.hasText(config.getVisionApiKey())) {
                String decrypted = aesCryptoUtil.decrypt(config.getVisionApiKey());
                config.setVisionApiKey(decrypted);
            }

            // 解密 Jina Reader API Key（不脱敏，供 WebFetchTools 内部调用使用）
            if (StringUtils.hasText(config.getJinaApiKey())) {
                String decrypted = aesCryptoUtil.decrypt(config.getJinaApiKey());
                config.setJinaApiKey(decrypted);
            }

            // 解密JSON字段（不脱敏，供Python服务使用）
            decryptJsonFieldsForInternal(config);
            applyDefaultCommentSkill(config);
        }

        return config;
    }

    /**
     * 解密JSON字段中的敏感信息（用于内部调用，完整返回）
     */
    private void decryptJsonFieldsForInternal(SysAiConfig config) {
        try {
            // 1. 解密百度配置
            if (StringUtils.hasText(config.getBaiduConfig())) {
                JsonNode baiduNode = objectMapper.readTree(config.getBaiduConfig());
                if (baiduNode.has("app_secret")) {
                    String encrypted = baiduNode.get("app_secret").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ((ObjectNode) baiduNode).put("app_secret", decrypted);
                            config.setBaiduConfig(objectMapper.writeValueAsString(baiduNode));
                        }
                    }
                }
            }

            // 2. 解密API翻译扩展配置
            if (StringUtils.hasText(config.getCustomConfig())) {
                JsonNode customNode = objectMapper.readTree(config.getCustomConfig());
                if (customNode instanceof ObjectNode customObject
                        && decryptJsonSecretFields(customObject, false, API_TRANSLATION_SECRET_FIELDS)) {
                    config.setCustomConfig(objectMapper.writeValueAsString(customNode));
                }
            }

            // 3. 解密LLM配置
            if (StringUtils.hasText(config.getLlmConfig())) {
                JsonNode llmNode = objectMapper.readTree(config.getLlmConfig());
                if (llmNode.has("api_key")) {
                    String encrypted = llmNode.get("api_key").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ((ObjectNode) llmNode).put("api_key", decrypted);
                            config.setLlmConfig(objectMapper.writeValueAsString(llmNode));
                        }
                    }
                }
            }

            // 4. 解密摘要配置中的dedicated_llm
            if (StringUtils.hasText(config.getSummaryConfig())) {
                JsonNode summaryNode = objectMapper.readTree(config.getSummaryConfig());

                if (summaryNode.has("dedicated_llm")) {
                    JsonNode dedicatedLlmNode = summaryNode.get("dedicated_llm");

                    if (dedicatedLlmNode.has("api_key")) {
                        String encrypted = dedicatedLlmNode.get("api_key").asText();
                        if (StringUtils.hasText(encrypted)) {
                            String decrypted = aesCryptoUtil.decrypt(encrypted);
                            if (decrypted != null) {
                                ((ObjectNode) dedicatedLlmNode).put("api_key", decrypted);
                                config.setSummaryConfig(objectMapper.writeValueAsString(summaryNode));
                            }
                        }
                    }
                }
            }

            // 5. 解密生图配置中的 api_key 与 dedicated_llm.api_key（不脱敏，供内部生图服务使用）
            if (StringUtils.hasText(config.getImageConfig())) {
                JsonNode imageNode = objectMapper.readTree(config.getImageConfig());
                boolean imageModified = false;

                if (imageNode.has("api_key")) {
                    String encrypted = imageNode.get("api_key").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ((ObjectNode) imageNode).put("api_key", decrypted);
                            imageModified = true;
                        }
                    }
                }

                if (imageNode.has("dedicated_llm")) {
                    JsonNode dedicatedLlmNode = imageNode.get("dedicated_llm");
                    if (dedicatedLlmNode.has("api_key")) {
                        String encrypted = dedicatedLlmNode.get("api_key").asText();
                        if (StringUtils.hasText(encrypted)) {
                            String decrypted = aesCryptoUtil.decrypt(encrypted);
                            if (decrypted != null) {
                                ((ObjectNode) dedicatedLlmNode).put("api_key", decrypted);
                                imageModified = true;
                            }
                        }
                    }
                }

                if (imageModified) {
                    config.setImageConfig(objectMapper.writeValueAsString(imageNode));
                }
            }

            // 6. 解密 extraConfig.rag.embeddingApiKey
            if (StringUtils.hasText(config.getExtraConfig())) {
                JsonNode extraNode = objectMapper.readTree(config.getExtraConfig());
                JsonNode ragNode = extraNode.get("rag");
                if (ragNode instanceof ObjectNode ragObject && ragObject.has("embeddingApiKey")) {
                    String encrypted = ragObject.get("embeddingApiKey").asText();
                    if (StringUtils.hasText(encrypted)) {
                        String decrypted = aesCryptoUtil.decrypt(encrypted);
                        if (decrypted != null) {
                            ragObject.put("embeddingApiKey", decrypted);
                            config.setExtraConfig(objectMapper.writeValueAsString(extraNode));
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("解密JSON字段失败: {}", e.getMessage(), e);
            // 不抛异常，避免影响配置读取
        }
    }

    /**
     * 测试AI API连接
     *
     * @param config AI配置
     * @return 测试结果
     */
    private Map<String, Object> testAiApiConnection(SysAiConfig config) {
        Map<String, Object> result = new HashMap<>();

        try {
            String apiKey = config.getApiKey();

            if (!StringUtils.hasText(apiKey)) {
                result.put("success", false);
                result.put("message", "API密钥为空");
                return result;
            }

            ChatModel chatModel = dynamicChatClientFactory.createChatModel(config);
            Map<String, Object> thinkingDiagnostics = aiThinkingAdapterRegistry.resolve(config).diagnostics();
            String response = ChatClient.create(chatModel)
                    .prompt()
                    .user("请只回复：ok")
                    .call()
                    .content();

            if (StringUtils.hasText(response)) {
                result.put("success", true);
                result.put("message", "连接成功");
                result.put("response", response.trim());
                result.put("thinkingProfile", thinkingDiagnostics.get("profile"));
                result.put("thinkingProfileName", thinkingDiagnostics.get("profileName"));
                result.put("thinkingParameters", thinkingDiagnostics);
            } else {
                result.put("success", false);
                result.put("message", "连接测试返回空响应");
                result.put("thinkingProfile", thinkingDiagnostics.get("profile"));
                result.put("thinkingProfileName", thinkingDiagnostics.get("profileName"));
                result.put("thinkingParameters", thinkingDiagnostics);
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试翻译服务连接
     *
     * @param config 翻译配置
     * @return 测试结果
     */
    private Map<String, Object> testTranslationConnection(SysAiConfig config) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 直接使用 Java 翻译服务进行测试
            String testText = "Hello";
            String translated = llmTranslationService.translateText(testText, "en", "zh");

            if (translated != null && !translated.isBlank()) {
                result.put("success", true);
                result.put("message", "翻译连接测试成功");
                result.put("testResult", translated);
            } else {
                result.put("success", false);
                result.put("message", "翻译测试返回空结果，请检查AI配置");
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "翻译测试失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> convertConfigToMap(SysAiConfig config) {
        Map<String, Object> result = new HashMap<>();

        if (config == null) {
            return result;
        }

        try {
            // 基本字段
            result.put("id", config.getId());
            result.put("configType", config.getConfigType());
            result.put("configName", config.getConfigName());
            result.put("enabled", config.getEnabled());
            result.put("translationType", config.getTranslationType());
            result.put("defaultSourceLang", config.getDefaultSourceLang());
            result.put("defaultTargetLang", config.getDefaultTargetLang());

            // 将JSON字符串字段解析为对象
            if (StringUtils.hasText(config.getBaiduConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> baiduConfig = objectMapper.readValue(config.getBaiduConfig(), Map.class);
                result.put("baiduConfig", baiduConfig);
            }

            if (StringUtils.hasText(config.getCustomConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> customConfig = objectMapper.readValue(config.getCustomConfig(), Map.class);
                result.put("customConfig", customConfig);
            }

            if (StringUtils.hasText(config.getLlmConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> llmConfig = objectMapper.readValue(config.getLlmConfig(), Map.class);
                result.put("llmConfig", llmConfig);
            }

            if (StringUtils.hasText(config.getTranslationLlmConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> translationLlmConfig = objectMapper.readValue(config.getTranslationLlmConfig(),
                        Map.class);
                result.put("translationLlmConfig", translationLlmConfig);
            }

            if (StringUtils.hasText(config.getSummaryConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> summaryConfig = objectMapper.readValue(config.getSummaryConfig(), Map.class);
                result.put("summaryConfig", summaryConfig);
            }

            if (StringUtils.hasText(config.getImageConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> imageConfig = objectMapper.readValue(config.getImageConfig(), Map.class);
                result.put("imageConfig", imageConfig);
            }

            if (StringUtils.hasText(config.getExtraConfig())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extraConfig = objectMapper.readValue(config.getExtraConfig(), Map.class);
                result.put("extraConfig", extraConfig);
            }

        } catch (Exception e) {
            log.error("转换配置为Map失败: {}", e.getMessage(), e);
            // 出错时至少返回基本字段
        }

        return result;
    }

    private void normalizeRagConfig(SysAiConfig config) {
        if (!StringUtils.hasText(config.getExtraConfig())) {
            return;
        }
        try {
            JsonNode extraNode = objectMapper.readTree(config.getExtraConfig());
            if (!(extraNode instanceof ObjectNode extraObject)) {
                return;
            }
            JsonNode ragNode = extraObject.get("rag");
            if (!(ragNode instanceof ObjectNode ragObject)) {
                return;
            }

            if (!isRagRuntimeSupported()) {
                ragObject.put("enabled", false);
            }
            config.setExtraConfig(objectMapper.writeValueAsString(extraObject));
        } catch (Exception e) {
            log.warn("规范化 RAG 配置失败，将继续按原配置保存: {}", e.getMessage());
        }
    }

    private void applyDefaultCommentSkill(SysAiConfig config) {
        if (config == null || !"ai_chat".equals(config.getConfigType())) {
            return;
        }
        config.setExtraConfig(AiCommentSkillDefaults.ensureCommentSkill(config.getExtraConfig(), objectMapper));
    }

    private boolean isRagRuntimeSupported() {
        String databaseType = environment.getProperty("DB_TYPE");
        if (!StringUtils.hasText(databaseType)) {
            String datasourceUrl = environment.getProperty("spring.datasource.url");
            if (StringUtils.hasText(datasourceUrl)) {
                databaseType = datasourceUrl.toLowerCase().startsWith("jdbc:mysql:") ? "mysql" : "mariadb";
            } else {
                databaseType = "mariadb";
            }
        }
        boolean runtimeEnabled = Boolean.parseBoolean(environment.getProperty("RAG_ENABLED", "true"));
        if (!runtimeEnabled || !"mariadb".equalsIgnoreCase(databaseType)) {
            return false;
        }
        DatabaseVersion databaseVersion = parseDatabaseVersion(readDatabaseVersionSafely());
        if (databaseVersion == null || !databaseVersion.isAtLeast(11, 7)) {
            return false;
        }
        return probeVectorFunctions();
    }

    private String readDatabaseVersionSafely() {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private DatabaseVersion parseDatabaseVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }
        String normalized = version.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)").matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new DatabaseVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean probeVectorFunctions() {
        try {
            Double result = jdbcTemplate.queryForObject(
                    "SELECT VEC_DISTANCE_COSINE(VEC_FromText('[1,0]'), VEC_FromText('[1,0]'))",
                    Double.class);
            return result != null;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private record DatabaseVersion(int major, int minor) {
        private boolean isAtLeast(int expectedMajor, int expectedMinor) {
            return major > expectedMajor || (major == expectedMajor && minor >= expectedMinor);
        }
    }
}
