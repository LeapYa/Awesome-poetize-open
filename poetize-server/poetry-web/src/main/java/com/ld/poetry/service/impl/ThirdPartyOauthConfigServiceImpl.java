package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.ThirdPartyOauthConfigMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;

/**
 * <p>
 * 第三方OAuth登录配置表 服务实现类
 * </p>
 *
 * @author LeapYa
 * @since 2025-07-19
 */
@Slf4j
@Service
public class ThirdPartyOauthConfigServiceImpl extends ServiceImpl<ThirdPartyOauthConfigMapper, ThirdPartyOauthConfig> implements ThirdPartyOauthConfigService {

    @Autowired
    private ThirdPartyOauthConfigMapper configMapper;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    @Override
    public ThirdPartyOauthConfig getByPlatformType(String platformType) {
        if (!StringUtils.hasText(platformType)) {
            return null;
        }
        return configMapper.getByPlatformType(platformType);
    }

    @Override
    public List<ThirdPartyOauthConfig> getEnabledConfigs() {
        return configMapper.getEnabledConfigs();
    }

    @Override
    public List<ThirdPartyOauthConfig> getActiveConfigs() {
        return configMapper.getActiveConfigs();
    }

    @Override
    public List<ThirdPartyOauthConfig> getAllConfigs() {
        return configMapper.getAllConfigs();
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> updateGlobalEnabled(Boolean globalEnabled) {
        try {
            int result = configMapper.updateGlobalEnabled(globalEnabled);
            if (result > 0) {
                log.info("全局启用状态更新成功: {}", globalEnabled);
                cacheService.evictThirdLoginStatus();
                return PoetryResult.success(true);
            } else {
                log.warn("全局启用状态更新失败，没有记录被更新");
                return PoetryResult.fail("更新失败");
            }
        } catch (Exception e) {
            log.error("更新全局启用状态失败", e);
            return PoetryResult.fail("更新失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> updatePlatformEnabled(String platformType, Boolean enabled) {
        try {
            if (!StringUtils.hasText(platformType)) {
                return PoetryResult.fail("平台类型不能为空");
            }

            ThirdPartyOauthConfig config = getByPlatformType(platformType);
            if (config == null) {
                return PoetryResult.fail("平台配置不存在: " + platformType);
            }

            config.setEnabled(enabled);
            config.setUpdateTime(LocalDateTime.now());
            boolean result = updateById(config);

            if (result) {
                log.info("平台启用状态更新成功: {} -> {}", platformType, enabled);
                cacheService.evictThirdLoginStatus();
                return PoetryResult.success(true);
            } else {
                log.warn("平台启用状态更新失败: {}", platformType);
                return PoetryResult.fail("更新失败");
            }
        } catch (Exception e) {
            log.error("更新平台启用状态失败: {}", platformType, e);
            return PoetryResult.fail("更新失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> batchUpdateConfigs(List<ThirdPartyOauthConfig> configs) {
        try {
            if (configs == null || configs.isEmpty()) {
                return PoetryResult.fail("配置列表为空");
            }

            for (ThirdPartyOauthConfig config : configs) {
                config.setUpdateTime(LocalDateTime.now());
            }

            boolean result = updateBatchById(configs);
            if (result) {
                log.info("批量更新配置成功，共更新 {} 条记录", configs.size());
                cacheService.evictThirdLoginStatus();
                return PoetryResult.success(true);
            } else {
                log.warn("批量更新配置失败");
                return PoetryResult.fail("批量更新失败");
            }
        } catch (Exception e) {
            log.error("批量更新配置失败", e);
            return PoetryResult.fail("批量更新失败: " + e.getMessage());
        }
    }

    @Override
    public PoetryResult<Map<String, Object>> getThirdLoginConfig() {
        try {
            List<ThirdPartyOauthConfig> allConfigs = getAllConfigs();
            Map<String, Object> result = new HashMap<>();

            // 检查是否有任何平台启用
            boolean globalEnabled = allConfigs.stream()
                    .anyMatch(config -> config.getEnabled() && config.getGlobalEnabled());
            result.put("enable", globalEnabled);

            // 为每个平台构建配置
            for (ThirdPartyOauthConfig config : allConfigs) {
                Map<String, Object> platformConfig = new HashMap<>();
                
                if ("twitter".equals(config.getPlatformType())) {
                    platformConfig.put("client_key", config.getClientKey());
                } else {
                    platformConfig.put("client_id", config.getClientId());
                }
                
                platformConfig.put("client_secret", config.getClientSecret());
                platformConfig.put("redirect_uri", config.getRedirectUri());
                platformConfig.put("enabled", config.getEnabled());
                
                result.put(config.getPlatformType(), platformConfig);
            }

            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("获取第三方登录配置失败", e);
            return PoetryResult.fail("获取配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> updateThirdLoginConfig(Map<String, Object> config) {
        try {
            log.info("开始更新第三方登录配置: {}", config);

            // 更新全局启用状态
            Boolean globalEnable = (Boolean) config.get("enable");
            if (globalEnable != null) {
                PoetryResult<Boolean> globalResult = updateGlobalEnabled(globalEnable);
                if (!globalResult.isSuccess()) {
                    return globalResult;
                }
            }

            // 更新各平台配置
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String key = entry.getKey();
                if ("enable".equals(key)) {
                    continue; // 跳过全局启用配置
                }

                Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> platformConfig = (Map<String, Object>) value;
                    updatePlatformConfig(key, platformConfig);
                }
            }

            log.info("第三方登录配置更新完成");
            cacheService.evictThirdLoginStatus();
            return PoetryResult.success(true);
        } catch (Exception e) {
            log.error("更新第三方登录配置失败", e);
            return PoetryResult.fail("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新单个平台配置
     */
    private void updatePlatformConfig(String platformType, Map<String, Object> platformConfig) {
        ThirdPartyOauthConfig config = getByPlatformType(platformType);
        if (config == null) {
            // 如果配置不存在，创建新配置
            config = createDefaultConfig(platformType);
        }

        // 更新配置字段
        if ("twitter".equals(platformType)) {
            config.setClientKey((String) platformConfig.get("client_key"));
        } else {
            config.setClientId((String) platformConfig.get("client_id"));
        }
        
        config.setClientSecret((String) platformConfig.get("client_secret"));
        config.setRedirectUri((String) platformConfig.get("redirect_uri"));
        
        Boolean enabled = (Boolean) platformConfig.get("enabled");
        if (enabled != null) {
            config.setEnabled(enabled);
        }

        config.setUpdateTime(LocalDateTime.now());

        // 保存或更新配置
        if (config.getId() == null) {
            save(config);
        } else {
            updateById(config);
        }
    }

    /**
     * 创建默认配置
     */
    private ThirdPartyOauthConfig createDefaultConfig(String platformType) {
        ThirdPartyOauthConfig config = new ThirdPartyOauthConfig();
        config.setPlatformType(platformType);
        config.setPlatformName(getPlatformName(platformType));
        config.setEnabled(true);
        config.setGlobalEnabled(true);
        config.setSortOrder(getDefaultSortOrder(platformType));
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        config.setDeleted(false);
        return config;
    }

    /**
     * 获取平台名称
     */
    private String getPlatformName(String platformType) {
        switch (platformType.toLowerCase()) {
            case "github": return "GitHub";
            case "google": return "Google";
            case "twitter": return "Twitter";
            case "yandex": return "Yandex";
            case "gitee": return "Gitee";
            case "qq": return "QQ";
            case "baidu": return "Baidu";
            case "afdian": return "爱发电";
            case "weibo": return "微博";
            case "linuxdo": return "LinuxDo";
            case "microsoft": return "Microsoft";
            case "gitlab": return "GitLab";
            case "yuque": return "语雀";
            case "huawei": return "华为";
            case "xiaomi": return "小米";
            case "apple": return "Apple";
            case "steam": return "Steam";
            case "custom": return "自定义";
            default: return platformType;
        }
    }

    /**
     * 获取默认排序顺序
     */
    private Integer getDefaultSortOrder(String platformType) {
        switch (platformType.toLowerCase()) {
            case "github": return 1;
            case "google": return 2;
            case "twitter": return 3;
            case "yandex": return 4;
            case "gitee": return 5;
            case "qq": return 6;
            case "baidu": return 7;
            case "afdian": return 8;
            case "weibo": return 9;
            case "linuxdo": return 10;
            case "microsoft": return 11;
            case "gitlab": return 12;
            case "yuque": return 13;
            case "huawei": return 14;
            case "xiaomi": return 15;
            case "apple": return 16;
            case "steam": return 17;
            case "custom": return 18;
            default: return 99;
        }
    }

    @Override
    public PoetryResult<Map<String, Object>> validateConfigs() {
        try {
            List<ThirdPartyOauthConfig> allConfigs = getAllConfigs();
            Map<String, Object> result = new HashMap<>();
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> validPlatforms = new ArrayList<>();
            List<String> invalidPlatforms = new ArrayList<>();

            for (ThirdPartyOauthConfig config : allConfigs) {
                String platform = config.getPlatformType();
                int errorsBeforePlatform = errors.size();

                // 检查必要字段
                if ("twitter".equals(platform)) {
                    if (!StringUtils.hasText(config.getClientKey())) {
                        errors.add(platform + ": 缺少 client_key");
                    }
                } else {
                    if (!StringUtils.hasText(config.getClientId())) {
                        errors.add(platform + ": 缺少 client_id");
                    }
                }

                if (!StringUtils.hasText(config.getClientSecret())) {
                    errors.add(platform + ": 缺少 client_secret");
                }

                // redirect_uri 可留空，留空时按站点地址自动生成，不再作为必填项校验

                // 检查启用状态
                if (config.getEnabled() && !config.getGlobalEnabled()) {
                    warnings.add(platform + ": 平台已启用但全局未启用");
                }

                if (errors.size() == errorsBeforePlatform) {
                    validPlatforms.add(platform);
                } else {
                    invalidPlatforms.add(platform);
                }
            }

            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            result.put("warnings", warnings);
            result.put("configCount", allConfigs.size());
            result.put("total_count", allConfigs.size());
            result.put("valid_count", validPlatforms.size());
            result.put("invalid_count", invalidPlatforms.size());
            result.put("valid_platforms", validPlatforms);
            result.put("invalid_platforms", invalidPlatforms);

            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("验证配置失败", e);
            return PoetryResult.fail("验证配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> migrateFromJsonFile(String jsonFilePath) {
        try {
            if (!StringUtils.hasText(jsonFilePath)) {
                return PoetryResult.fail("JSON文件路径不能为空");
            }

            File file = new File(jsonFilePath);
            if (!file.exists()) {
                return PoetryResult.fail("JSON文件不存在: " + jsonFilePath);
            }

            Map<String, Object> jsonConfig = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});
            return migrateFromJsonString(objectMapper.writeValueAsString(jsonConfig));
        } catch (Exception e) {
            log.error("从JSON文件迁移配置失败: {}", jsonFilePath, e);
            return PoetryResult.fail("从JSON文件迁移配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> migrateFromJsonString(String jsonString) {
        try {
            if (!StringUtils.hasText(jsonString)) {
                return PoetryResult.fail("JSON字符串不能为空");
            }

            Map<String, Object> jsonConfig = objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
            return updateThirdLoginConfig(jsonConfig);
        } catch (Exception e) {
            log.error("从JSON字符串迁移配置失败", e);
            return PoetryResult.fail("从JSON字符串迁移配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> resetToDefault() {
        try {
            // 删除所有现有配置
            remove(null);

            // 初始化默认配置
            return initDefaultConfigs();
        } catch (Exception e) {
            log.error("重置配置失败", e);
            return PoetryResult.fail("重置配置失败: " + e.getMessage());
        }
    }

    @Override
    public PoetryResult<String> exportToJson() {
        try {
            PoetryResult<Map<String, Object>> configResult = getThirdLoginConfig();
            if (!configResult.isSuccess()) {
                return PoetryResult.fail("获取配置失败: " + configResult.getMessage());
            }

            String jsonString = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(configResult.getData());
            return PoetryResult.success(jsonString);
        } catch (Exception e) {
            log.error("导出配置失败", e);
            return PoetryResult.fail("导出配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PoetryResult<Boolean> initDefaultConfigs() {
        try {
            List<ThirdPartyOauthConfig> defaultConfigs = createDefaultConfigs();

            for (ThirdPartyOauthConfig config : defaultConfigs) {
                // 检查是否已存在
                ThirdPartyOauthConfig existing = getByPlatformType(config.getPlatformType());
                if (existing == null) {
                    save(config);
                    log.info("初始化默认配置: {}", config.getPlatformType());
                }
            }

            return PoetryResult.success(true);
        } catch (Exception e) {
            log.error("初始化默认配置失败", e);
            return PoetryResult.fail("初始化默认配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建默认配置列表
     */
    private List<ThirdPartyOauthConfig> createDefaultConfigs() {
        List<ThirdPartyOauthConfig> configs = new ArrayList<>();

        // GitHub配置
        ThirdPartyOauthConfig github = createDefaultConfig("github");
        github.setScope("user:email");
        configs.add(github);

        // Google配置
        ThirdPartyOauthConfig google = createDefaultConfig("google");
        google.setScope("openid email profile");
        configs.add(google);

        // Twitter配置
        ThirdPartyOauthConfig twitter = createDefaultConfig("twitter");
        twitter.setScope("tweet.read users.read");
        configs.add(twitter);

        // Yandex配置
        ThirdPartyOauthConfig yandex = createDefaultConfig("yandex");
        yandex.setScope("login:email login:info");
        configs.add(yandex);

        // Gitee配置
        ThirdPartyOauthConfig gitee = createDefaultConfig("gitee");
        gitee.setScope("user_info emails");
        configs.add(gitee);
        
        // QQ配置
        ThirdPartyOauthConfig qq = createDefaultConfig("qq");
        qq.setScope("get_user_info");
        configs.add(qq);

        // Baidu配置
        ThirdPartyOauthConfig baidu = createDefaultConfig("baidu");
        baidu.setScope("basic");
        configs.add(baidu);

        return configs;
    }

    @Override
    @Transactional
    public PoetryResult<ThirdPartyOauthConfig> updatePlatformConfig(ThirdPartyOauthConfig config) {
        try {
            if (config == null || !StringUtils.hasText(config.getPlatformType())) {
                return PoetryResult.fail("配置信息不完整");
            }

            // 检查配置是否存在
            ThirdPartyOauthConfig existingConfig = getByPlatformType(config.getPlatformType());
            if (existingConfig == null) {
                return PoetryResult.fail("平台配置不存在: " + config.getPlatformType());
            }

            // 更新配置
            config.setId(existingConfig.getId());
            config.setUpdateTime(LocalDateTime.now());
            boolean result = updateById(config);

            if (result) {
                log.info("平台配置更新成功: {}", config.getPlatformType());
                return PoetryResult.success(config);
            } else {
                log.warn("平台配置更新失败: {}", config.getPlatformType());
                return PoetryResult.fail("更新失败");
            }
        } catch (Exception e) {
            log.error("更新平台配置失败: {}", config != null ? config.getPlatformType() : "null", e);
            return PoetryResult.fail("更新失败: " + e.getMessage());
        }
    }

    @Override
    public PoetryResult<Map<String, Object>> getConfigStats() {
        try {
            Map<String, Object> stats = configMapper.getConfigStats();
            return PoetryResult.success(stats);
        } catch (Exception e) {
            log.error("获取配置统计失败", e);
            return PoetryResult.fail("获取配置统计失败: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Boolean> getAllPlatformsStatus() {
        List<ThirdPartyOauthConfig> allConfigs = getAllConfigs();
        Map<String, Boolean> statusMap = new ConcurrentHashMap<>();
        
        // 使用结构化并发并行检查所有平台的可用性
        try (var scope = StructuredTaskScope.open()) {
            // 为每个平台创建检查任务
            for (ThirdPartyOauthConfig config : allConfigs) {
                String platform = config.getPlatformType();
                scope.fork(() -> {
                    boolean isAvailable = checkPlatformAvailability(config);
                    statusMap.put(platform, isAvailable);
                    return null;
                });
            }
            
            // 等待所有检查完成
            scope.join();
            
            // log.info("所有OAuth平台状态检查完成，共{}个平台", statusMap.size());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("OAuth平台状态检查被中断", e);
        } catch (Exception e) {
            log.error("OAuth平台状态检查失败", e);
        }
        
        return statusMap;
    }
    
    /**
     * 检查单个平台的可用性
     */
    private boolean checkPlatformAvailability(ThirdPartyOauthConfig config) {
        if (config == null || !config.getEnabled() || !config.getGlobalEnabled()) {
            return false;
        }
        
        // 检查必要的配置项（回调地址可留空，运行时按站点地址自动生成）
        if ("twitter".equals(config.getPlatformType())) {
            return StringUtils.hasText(config.getClientKey()) && 
                   StringUtils.hasText(config.getClientSecret());
        } else if ("steam".equals(config.getPlatformType())) {
            // Steam 免凭据（OpenID 2.0），回调地址可留空由站点地址自动生成，无需其他必填项
            return true;
        } else if ("custom".equals(config.getPlatformType())) {
            // 自定义平台还需配齐三个端点
            return StringUtils.hasText(config.getClientId()) &&
                   StringUtils.hasText(config.getClientSecret()) &&
                   StringUtils.hasText(config.getAuthorizeUrl()) &&
                   StringUtils.hasText(config.getTokenUrl()) &&
                   StringUtils.hasText(config.getUserInfoUrl());
        } else {
            return StringUtils.hasText(config.getClientId()) && 
                   StringUtils.hasText(config.getClientSecret());
        }
    }

    @Override
    public String buildDefaultRedirectUri(String platformType) {
        // 与 Nginx 路由一致：/callback/{platform} 会被重写到 /oauth/callback/{platform}
        return mailUtil.getSiteUrl() + "/callback/" + platformType;
    }

    @Override
    public String resolveRedirectUri(ThirdPartyOauthConfig config) {
        if (config == null) {
            return null;
        }
        if (StringUtils.hasText(config.getRedirectUri())) {
            return config.getRedirectUri().trim();
        }
        return buildDefaultRedirectUri(config.getPlatformType());
    }
}
