package com.ld.poetry.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import com.ld.poetry.dao.ArticlePaymentMapper;
import com.ld.poetry.entity.ArticlePayment;
import com.ld.poetry.entity.SysPlugin;
import com.ld.poetry.plugin.GroovyPluginEngine;
import com.ld.poetry.service.SysPluginService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 支付业务服务
 * <p>
 * 负责读取激活的支付插件 → 路由到对应 Provider → 处理支付逻辑
 * </p>
 *
 * @author LeapYa
 * @since 2026-02-18
 */
@Slf4j
@Service
public class PaymentService {

    private static final List<String> SECRET_FIELD_KEYWORDS = List.of(
            "key", "secret", "token", "password", "private", "api_key", "apikey", "apitoken"
    );

    @Autowired
    private SysPluginService sysPluginService;

    @Autowired
    private ArticlePaymentMapper articlePaymentMapper;

    @Autowired
    private List<PaymentProvider> providers;

    @Autowired
    private GroovyPluginEngine groovyPluginEngine;

    @Autowired
    private GroovyPaymentAdapter groovyPaymentAdapter;

    private static final JsonMapper objectMapper = JsonMapper.builder().build();

    /**
     * 获取当前激活的 Provider
     * <p>
     * 查找顺序：Java 实现（编译期注册）→ Groovy 实现（运行期安装）
     * </p>
     */
    public PaymentProvider getActiveProvider() {
        SysPlugin activePlugin = getActivePaymentPlugin();
        if (activePlugin == null) {
            return null;
        }
        return resolveProvider(activePlugin.getPluginKey());
    }

    /**
     * 获取当前激活插件的配置
     */
    public Map<String, Object> getActiveConfig() {
        SysPlugin activePlugin = getActivePaymentPlugin();
        if (activePlugin == null) {
            return null;
        }
        return parsePluginConfig(activePlugin);
    }

    public SysPlugin getActivePaymentPlugin() {
        return sysPluginService.getActivePlugin(SysPlugin.TYPE_PAYMENT);
    }

    public List<SysPlugin> listPaymentPlugins() {
        return sysPluginService.getPluginsByType(SysPlugin.TYPE_PAYMENT);
    }

    public SysPlugin getPaymentPluginByKey(String pluginKey) {
        if (!hasText(pluginKey)) {
            return null;
        }
        return getPluginByKey(pluginKey);
    }

    public Map<String, Object> parsePluginConfig(SysPlugin plugin) {
        if (plugin == null || !hasText(plugin.getPluginConfig())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(plugin.getPluginConfig(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.error("解析支付插件配置失败: pluginKey={}", plugin.getPluginKey(), e);
            return new LinkedHashMap<>();
        }
    }

    public Map<String, Object> parseConfigSchema(SysPlugin plugin) {
        if (plugin == null || !hasText(plugin.getManifest())) {
            return new LinkedHashMap<>();
        }

        try {
            Map<String, Object> manifest = objectMapper.readValue(plugin.getManifest(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object schema = manifest.get("configSchema");
            return toObjectMap(schema);
        } catch (Exception e) {
            log.warn("解析支付插件 manifest 配置 schema 失败: pluginKey={}", plugin.getPluginKey(), e);
            return new LinkedHashMap<>();
        }
    }

    public boolean supportsConnectionTest(String pluginKey) {
        return resolveProvider(pluginKey) != null;
    }

    public boolean testConnection(String pluginKey, Map<String, Object> config) {
        PaymentProvider provider = resolveProvider(pluginKey);
        if (provider == null || config == null) {
            return false;
        }
        return provider.testConnection(config);
    }

    public Map<String, Object> mergeAndValidateConfig(String pluginKey,
                                                      Map<String, Object> existingConfig,
                                                      Map<String, Object> incomingConfig) {
        SysPlugin plugin = getPaymentPluginByKey(pluginKey);
        Map<String, Object> schema = parseConfigSchema(plugin);
        Map<String, Object> mergedConfig = new LinkedHashMap<>();
        if (existingConfig != null) {
            mergedConfig.putAll(existingConfig);
        }
        if (incomingConfig != null) {
            mergedConfig.putAll(incomingConfig);
        }

        List<String> missingFields = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();
        Map<String, Boolean> secretFieldStatus = new LinkedHashMap<>();
        Map<String, Object> nonSecretConfigPreview = new LinkedHashMap<>();

        Set<String> keys = new TreeSet<>();
        keys.addAll(schema.keySet());
        keys.addAll(mergedConfig.keySet());

        for (String key : keys) {
            Map<String, Object> fieldSchema = toObjectMap(schema.get(key));
            if (!mergedConfig.containsKey(key) && fieldSchema.containsKey("defaultValue")) {
                mergedConfig.put(key, fieldSchema.get("defaultValue"));
            }

            Object normalizedValue = normalizeSchemaValue(key, fieldSchema, mergedConfig.get(key), validationErrors);
            mergedConfig.put(key, normalizedValue);

            boolean secretField = isSecretField(key);
            boolean filled = hasConfigValue(normalizedValue);
            if (secretField) {
                secretFieldStatus.put(key, filled);
            }
            if (!secretField && filled) {
                nonSecretConfigPreview.put(key, normalizedValue);
            }
            if (!filled && schema.containsKey(key) && isRequiredSchemaField(fieldSchema)) {
                missingFields.add(key);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mergedConfig", mergedConfig);
        result.put("missingFields", missingFields);
        result.put("secretFieldStatus", secretFieldStatus);
        result.put("nonSecretConfigPreview", nonSecretConfigPreview);
        result.put("configured", validationErrors.isEmpty() && missingFields.isEmpty() && !mergedConfig.isEmpty());
        result.put("validationErrors", validationErrors);
        return result;
    }

    public boolean savePluginConfig(String pluginKey, Map<String, Object> config, boolean activate) {
        SysPlugin plugin = getPaymentPluginByKey(pluginKey);
        if (plugin == null) {
            return false;
        }

        plugin.setPluginConfig(serializePluginConfig(config));
        plugin.setUpdateTime(LocalDateTime.now());
        boolean updated = sysPluginService.updateById(plugin);
        if (!updated) {
            return false;
        }

        if (activate) {
            return sysPluginService.setActivePlugin(SysPlugin.TYPE_PAYMENT, pluginKey);
        }
        return true;
    }

    public String serializePluginConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config == null ? Collections.emptyMap() : config);
        } catch (JacksonException e) {
            throw new IllegalStateException("序列化支付插件配置失败", e);
        }
    }

    /**
     * 生成支付链接
     */
    public String getPaymentUrl(Integer articleId, Integer userId) {
        PaymentProvider provider = getActiveProvider();
        if (provider == null) {
            throw new IllegalStateException("未配置或未激活支付插件");
        }

        Map<String, Object> config = getActiveConfig();
        if (config == null) {
            throw new IllegalStateException("支付插件配置为空");
        }

        return provider.getPaymentUrl(articleId, userId, config);
    }

    /**
     * 处理回调通知
     *
     * @return 应返回给平台的响应内容，null 表示处理失败
     */
    public String handleCallback(String platformKey, HttpServletRequest request) {
        // 1. 先从 Java Provider 中找
        PaymentProvider provider = providers.stream()
                .filter(p -> !(p instanceof GroovyPaymentAdapter))
                .filter(p -> p.getPlatformKey().equals(platformKey))
                .findFirst()
                .orElse(null);

        // 2. 如果 Java 中没有，尝试 Groovy 插件
        if (provider == null && groovyPluginEngine.isLoaded(platformKey)) {
            groovyPaymentAdapter.setCurrentPluginKey(platformKey);
            provider = groovyPaymentAdapter;
        }

        if (provider == null) {
            log.warn("未找到平台 Provider: {}", platformKey);
            return null;
        }

        // 获取该平台的配置（不一定是激活的，回调要支持已配置的平台）
        SysPlugin plugin = getPluginByKey(platformKey);
        if (plugin == null || plugin.getPluginConfig() == null) {
            log.warn("平台 {} 未配置", platformKey);
            return null;
        }

        Map<String, Object> config;
        try {
            config = objectMapper.readValue(plugin.getPluginConfig(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.error("解析平台 {} 配置失败", platformKey, e);
            return null;
        }

        // 验签并解析
        CallbackResult result = provider.verifyCallback(request, config);
        if (result == null || !result.isVerified()) {
            log.warn("平台 {} 回调验签失败", platformKey);
            return null;
        }

        // 幂等检查
        if (result.getPlatformOrderId() != null) {
            LambdaQueryWrapper<ArticlePayment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ArticlePayment::getPlatformOrderId, result.getPlatformOrderId())
                    .eq(ArticlePayment::getPlatform, platformKey);
            ArticlePayment existing = articlePaymentMapper.selectOne(wrapper);
            if (existing != null) {
                log.info("订单 {} 已存在，跳过（幂等）", result.getPlatformOrderId());
                return provider.getSuccessResponse();
            }
        }

        // 创建支付记录
        if (result.getParsedUserId() != null && result.getParsedArticleId() != null) {
            ArticlePayment payment = new ArticlePayment();
            payment.setUserId(result.getParsedUserId());
            payment.setArticleId(result.getParsedArticleId());
            payment.setAmount(result.getAmount());
            payment.setPlatform(platformKey);
            payment.setPlatformOrderId(result.getPlatformOrderId());
            payment.setPlatformUserId(result.getPlatformUserId());
            payment.setCustomOrderId(result.getCustomOrderId());
            payment.setPaymentStatus(1); // 已支付
            payment.setPayType(result.getParsedArticleId() == 0 ? 2 : 1);
            payment.setRemark(result.getRemark());
            payment.setCreateTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());

            // 会员购买（articleId=0）设置过期时间
            if (result.getParsedArticleId() == 0) {
                int memberDays = 30; // 默认30天
                if (config.containsKey("memberDurationDays")) {
                    Object val = config.get("memberDurationDays");
                    if (val instanceof Number) {
                        memberDays = ((Number) val).intValue();
                    }
                }
                // 如果已有未过期的会员记录，在其基础上续期
                LambdaQueryWrapper<ArticlePayment> memberQuery = new LambdaQueryWrapper<>();
                memberQuery.eq(ArticlePayment::getUserId, result.getParsedUserId())
                        .eq(ArticlePayment::getArticleId, 0)
                        .eq(ArticlePayment::getPaymentStatus, 1)
                        .isNotNull(ArticlePayment::getExpireTime)
                        .gt(ArticlePayment::getExpireTime, LocalDateTime.now())
                        .orderByDesc(ArticlePayment::getExpireTime)
                        .last("LIMIT 1");
                ArticlePayment existingMember = articlePaymentMapper.selectOne(memberQuery);
                LocalDateTime baseTime = (existingMember != null) ? existingMember.getExpireTime()
                        : LocalDateTime.now();
                payment.setExpireTime(baseTime.plusDays(memberDays));
            }

            articlePaymentMapper.insert(payment);
            log.info("支付记录创建成功: userId={}, articleId={}, amount={}, orderId={}",
                    result.getParsedUserId(), result.getParsedArticleId(),
                    result.getAmount(), result.getPlatformOrderId());
        } else {
            log.warn("无法解析 customOrderId: {}，订单已记录但未关联用户和文章", result.getCustomOrderId());
            // 仍然记录，由管理员手动处理
            ArticlePayment payment = new ArticlePayment();
            payment.setUserId(0);
            payment.setArticleId(0);
            payment.setAmount(result.getAmount());
            payment.setPlatform(platformKey);
            payment.setPlatformOrderId(result.getPlatformOrderId());
            payment.setPlatformUserId(result.getPlatformUserId());
            payment.setCustomOrderId(result.getCustomOrderId());
            payment.setPaymentStatus(0); // 待确认
            payment.setPayType(1);
            payment.setRemark("无法解析用户和文章: " + result.getCustomOrderId());
            payment.setCreateTime(LocalDateTime.now());
            payment.setUpdateTime(LocalDateTime.now());

            articlePaymentMapper.insert(payment);
        }

        return provider.getSuccessResponse();
    }

    /**
     * 检查用户是否已付费
     */
    public boolean hasPaid(Integer userId, Integer articleId) {
        if (userId == null || articleId == null) {
            return false;
        }

        LambdaQueryWrapper<ArticlePayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticlePayment::getUserId, userId)
                .eq(ArticlePayment::getArticleId, articleId)
                .eq(ArticlePayment::getPaymentStatus, 1); // 已支付

        return articlePaymentMapper.selectCount(wrapper) > 0;
    }

    /**
     * 检查用户是否为全站会员（articleId=0 的支付记录，且未过期）
     */
    public boolean isMember(Integer userId) {
        if (userId == null) {
            return false;
        }

        LambdaQueryWrapper<ArticlePayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticlePayment::getUserId, userId)
                .eq(ArticlePayment::getArticleId, 0)
                .eq(ArticlePayment::getPaymentStatus, 1)
                .and(w -> w.isNull(ArticlePayment::getExpireTime)
                        .or().gt(ArticlePayment::getExpireTime, LocalDateTime.now()));

        return articlePaymentMapper.selectCount(wrapper) > 0;
    }

    /**
     * 获取文章付费人数
     */
    public int getPaidCount(Integer articleId) {
        if (articleId == null) {
            return 0;
        }

        LambdaQueryWrapper<ArticlePayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticlePayment::getArticleId, articleId)
                .eq(ArticlePayment::getPaymentStatus, 1);

        return Math.toIntExact(articlePaymentMapper.selectCount(wrapper));
    }

    /**
     * 测试当前激活平台的连接
     */
    public boolean testConnection() {
        PaymentProvider provider = getActiveProvider();
        if (provider == null) {
            return false;
        }

        Map<String, Object> config = getActiveConfig();
        if (config == null) {
            return false;
        }

        return provider.testConnection(config);
    }

    /**
     * 根据 pluginKey 获取支付插件
     */
    private SysPlugin getPluginByKey(String pluginKey) {
        LambdaQueryWrapper<SysPlugin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPlugin::getPluginType, SysPlugin.TYPE_PAYMENT)
                .eq(SysPlugin::getPluginKey, pluginKey);

        List<SysPlugin> plugins = sysPluginService.list(wrapper);
        return plugins.isEmpty() ? null : plugins.get(0);
    }

    private PaymentProvider resolveProvider(String pluginKey) {
        if (!hasText(pluginKey)) {
            return null;
        }

        PaymentProvider javaProvider = providers.stream()
                .filter(p -> !(p instanceof GroovyPaymentAdapter))
                .filter(p -> p.getPlatformKey().equals(pluginKey))
                .findFirst()
                .orElse(null);
        if (javaProvider != null) {
            return javaProvider;
        }

        if (groovyPluginEngine.isLoaded(pluginKey)) {
            groovyPaymentAdapter.setCurrentPluginKey(pluginKey);
            return groovyPaymentAdapter;
        }

        log.warn("未找到匹配的支付 Provider: pluginKey={}，请确认已安装对应支付插件", pluginKey);
        return null;
    }

    private Map<String, Object> toObjectMap(Object source) {
        if (!(source instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private Object normalizeSchemaValue(String key,
                                        Map<String, Object> fieldSchema,
                                        Object rawValue,
                                        List<String> validationErrors) {
        if (fieldSchema.isEmpty()) {
            return rawValue;
        }

        if (!hasConfigValue(rawValue)) {
            return rawValue;
        }

        String type = Objects.toString(fieldSchema.get("type"), "");
        try {
            return switch (type) {
                case "string" -> String.valueOf(rawValue);
                case "number" -> normalizeNumberValue(key, rawValue);
                case "boolean" -> normalizeBooleanValue(key, rawValue);
                case "select" -> normalizeSelectValue(key, fieldSchema, rawValue);
                default -> rawValue;
            };
        } catch (IllegalArgumentException e) {
            validationErrors.add(e.getMessage());
            return rawValue;
        }
    }

    private Object normalizeNumberValue(String key, Object rawValue) {
        if (rawValue instanceof Number) {
            return rawValue;
        }
        if (rawValue instanceof String strValue) {
            String trimmed = strValue.trim();
            if (!hasText(trimmed)) {
                return trimmed;
            }
            if (trimmed.contains(".")) {
                return Double.parseDouble(trimmed);
            }
            return Long.parseLong(trimmed);
        }
        throw new IllegalArgumentException(key + " 必须是数字");
    }

    private Object normalizeBooleanValue(String key, Object rawValue) {
        if (rawValue instanceof Boolean) {
            return rawValue;
        }
        if (rawValue instanceof String strValue) {
            if ("true".equalsIgnoreCase(strValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(strValue)) {
                return false;
            }
        }
        throw new IllegalArgumentException(key + " 必须是布尔值");
    }

    private Object normalizeSelectValue(String key, Map<String, Object> fieldSchema, Object rawValue) {
        List<?> options = fieldSchema.get("options") instanceof List<?> list ? list : Collections.emptyList();
        if (options.isEmpty()) {
            return rawValue;
        }

        for (Object option : options) {
            if (option instanceof Map<?, ?> optionMap) {
                Object optionValue = optionMap.get("value");
                if (Objects.equals(optionValue, rawValue)
                        || Objects.equals(String.valueOf(optionValue), String.valueOf(rawValue))) {
                    return optionValue;
                }
            }
        }
        throw new IllegalArgumentException(key + " 不是允许的选项");
    }

    private boolean isSecretField(String key) {
        String lowerKey = key == null ? "" : key.toLowerCase();
        return SECRET_FIELD_KEYWORDS.stream().anyMatch(lowerKey::contains);
    }

    private boolean hasConfigValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String strValue) {
            return hasText(strValue);
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isRequiredSchemaField(Map<String, Object> fieldSchema) {
        if (fieldSchema == null || fieldSchema.isEmpty()) {
            return false;
        }
        if (Boolean.FALSE.equals(fieldSchema.get("required")) || Boolean.TRUE.equals(fieldSchema.get("optional"))) {
            return false;
        }
        return true;
    }
}
