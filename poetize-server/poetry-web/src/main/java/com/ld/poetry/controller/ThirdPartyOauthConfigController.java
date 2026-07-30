package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.entity.dto.ThirdPartyOauthPublicConfigDTO;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 第三方OAuth登录配置管理 前端控制器
 * </p>
 *
 * @author LeapYa
 * @since 2025-07-19
 */
@Slf4j
@RestController
@RequestMapping("/admin/third-party-config")
public class ThirdPartyOauthConfigController {

    @Autowired
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @Autowired
    private RagSyncService ragSyncService;



    /**
     * 获取所有第三方登录配置
     */
    @LoginCheck(0)
    @GetMapping("/list")
    public PoetryResult<List<ThirdPartyOauthConfig>> getAllConfigs() {
        try {
            List<ThirdPartyOauthConfig> configs = thirdPartyOauthConfigService.getAllConfigs();
            // 下发自动生成的建议回调地址，供后台只读展示与一键复制
            for (ThirdPartyOauthConfig config : configs) {
                config.setSuggestedRedirectUri(
                        thirdPartyOauthConfigService.buildDefaultRedirectUri(config.getPlatformType()));
            }
            return PoetryResult.success(configs);
        } catch (Exception e) {
            log.error("获取第三方登录配置列表失败", e);
            return PoetryResult.fail("获取配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取启用的第三方登录配置
     */
    @LoginCheck(0)
    @GetMapping("/enabled")
    public PoetryResult<List<ThirdPartyOauthPublicConfigDTO>> getEnabledConfigs() {
        try {
            List<ThirdPartyOauthConfig> configs = thirdPartyOauthConfigService.getEnabledConfigs();
            return PoetryResult.success(toPublicConfigs(configs));
        } catch (Exception e) {
            log.error("获取启用的第三方登录配置失败", e);
            return PoetryResult.fail("获取启用配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取激活的第三方登录配置（全局启用且平台启用）
     */
    @LoginCheck(0)
    @GetMapping("/active")
    public PoetryResult<List<ThirdPartyOauthPublicConfigDTO>> getActiveConfigs() {
        try {
            List<ThirdPartyOauthConfig> configs = thirdPartyOauthConfigService.getActiveConfigs();
            return PoetryResult.success(toPublicConfigs(configs));
        } catch (Exception e) {
            log.error("获取激活的第三方登录配置失败", e);
            return PoetryResult.fail("获取激活配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据平台类型获取配置
     */
    @LoginCheck(0)
    @GetMapping("/platform/{platformType}")
    public PoetryResult<ThirdPartyOauthConfig> getConfigByPlatform(@PathVariable String platformType) {
        try {
            ThirdPartyOauthConfig config = thirdPartyOauthConfigService.getByPlatformType(platformType);
            if (config != null) {
                return PoetryResult.success(config);
            } else {
                return PoetryResult.fail("未找到指定平台的配置: " + platformType);
            }
        } catch (Exception e) {
            log.error("获取平台配置失败: {}", platformType, e);
            return PoetryResult.fail("获取平台配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新平台配置
     */
    @LoginCheck(0)
    @PutMapping("/platform")
    @AuditLog(action = "OAUTH_CONFIG_UPDATE", targetType = "OAUTH_CONFIG", targetIdParam = "platformType", summary = "更新第三方登录平台配置")
    public PoetryResult<ThirdPartyOauthConfig> updatePlatformConfig(@RequestBody ThirdPartyOauthConfig config) {
        try {
            PoetryResult<ThirdPartyOauthConfig> result = thirdPartyOauthConfigService.updatePlatformConfig(config);
            syncOauthIfSuccessful(result.isSuccess() && result.getData() != null);
            return result;
        } catch (Exception e) {
            log.error("更新平台配置失败", e);
            return PoetryResult.fail("更新平台配置失败: " + e.getMessage());
        }
    }

    /**
     * 批量更新配置
     */
    @LoginCheck(0)
    @PutMapping("/batch")
    @AuditLog(action = "OAUTH_CONFIG_BATCH_UPDATE", targetType = "OAUTH_CONFIG", summary = "批量更新第三方登录配置")
    public PoetryResult<Boolean> batchUpdateConfigs(@RequestBody List<ThirdPartyOauthConfig> configs) {
        try {
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.batchUpdateConfigs(configs);
            syncOauthIfSuccessful(result.isSuccess() && Boolean.TRUE.equals(result.getData()));
            return result;
        } catch (Exception e) {
            log.error("批量更新配置失败", e);
            return PoetryResult.fail("批量更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新全局启用状态
     */
    @LoginCheck(0)
    @PutMapping("/global-enabled/{enabled}")
    @AuditLog(action = "OAUTH_CONFIG_GLOBAL_ENABLE", targetType = "OAUTH_CONFIG", targetIdParam = "enabled", summary = "更新第三方登录全局状态")
    public PoetryResult<Boolean> updateGlobalEnabled(@PathVariable Boolean enabled) {
        try {
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.updateGlobalEnabled(enabled);
            syncOauthIfSuccessful(result.isSuccess() && Boolean.TRUE.equals(result.getData()));
            return result;
        } catch (Exception e) {
            log.error("更新全局启用状态失败", e);
            return PoetryResult.fail("更新全局启用状态失败: " + e.getMessage());
        }
    }

    /**
     * 更新平台启用状态
     */
    @LoginCheck(0)
    @PutMapping("/platform/{platformType}/enabled/{enabled}")
    @AuditLog(action = "OAUTH_CONFIG_PLATFORM_ENABLE", targetType = "OAUTH_CONFIG", targetIdParam = "platformType", summary = "更新第三方登录平台状态")
    public PoetryResult<Boolean> updatePlatformEnabled(@PathVariable String platformType, @PathVariable Boolean enabled) {
        try {
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.updatePlatformEnabled(platformType, enabled);
            syncOauthIfSuccessful(result.isSuccess() && Boolean.TRUE.equals(result.getData()));
            return result;
        } catch (Exception e) {
            log.error("更新平台启用状态失败: {}", platformType, e);
            return PoetryResult.fail("更新平台启用状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置统计信息
     */
    @LoginCheck(0)
    @GetMapping("/stats")
    public PoetryResult<Map<String, Object>> getConfigStats() {
        try {
            return thirdPartyOauthConfigService.getConfigStats();
        } catch (Exception e) {
            log.error("获取配置统计失败", e);
            return PoetryResult.fail("获取配置统计失败: " + e.getMessage());
        }
    }

    /**
     * 验证配置完整性
     */
    @LoginCheck(0)
    @GetMapping("/validate")
    public PoetryResult<Map<String, Object>> validateConfigs() {
        try {
            return thirdPartyOauthConfigService.validateConfigs();
        } catch (Exception e) {
            log.error("验证配置失败", e);
            return PoetryResult.fail("验证配置失败: " + e.getMessage());
        }
    }

    /**
     * 从JSON文件迁移配置
     */
    @LoginCheck(0)
    @PostMapping("/migrate/from-file")
    @AuditLog(action = "OAUTH_CONFIG_MIGRATE_FILE", targetType = "OAUTH_CONFIG", summary = "从文件迁移第三方登录配置")
    public PoetryResult<Boolean> migrateFromJsonFile(@RequestParam String jsonFilePath) {
        try {
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.migrateFromJsonFile(jsonFilePath);
            syncOauthIfSuccessful(result.isSuccess() && Boolean.TRUE.equals(result.getData()));
            return result;
        } catch (Exception e) {
            log.error("从JSON文件迁移配置失败", e);
            return PoetryResult.fail("从JSON文件迁移配置失败: " + e.getMessage());
        }
    }

    /**
     * 从JSON字符串迁移配置
     */
    @LoginCheck(0)
    @PostMapping("/migrate/from-json")
    @AuditLog(action = "OAUTH_CONFIG_MIGRATE_JSON", targetType = "OAUTH_CONFIG", summary = "从JSON迁移第三方登录配置")
    public PoetryResult<Boolean> migrateFromJsonString(@RequestBody Map<String, Object> request) {
        try {
            String jsonString = (String) request.get("jsonString");
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.migrateFromJsonString(jsonString);
            syncOauthIfSuccessful(result.isSuccess() && Boolean.TRUE.equals(result.getData()));
            return result;
        } catch (Exception e) {
            log.error("从JSON字符串迁移配置失败", e);
            return PoetryResult.fail("从JSON字符串迁移配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取迁移状态（简化版本）
     */
    @LoginCheck(0)
    @GetMapping("/migrate/status")
    public PoetryResult<Map<String, Object>> getMigrationStatus() {
        try {
            // 简化的迁移状态检查
            PoetryResult<Map<String, Object>> statsResult = thirdPartyOauthConfigService.getConfigStats();
            if (statsResult.isSuccess()) {
                Map<String, Object> stats = statsResult.getData();
                Map<String, Object> status = Map.of(
                    "migration_completed", true,
                    "database_records", stats.get("total_count"),
                    "active_configs", stats.get("active_count"),
                    "message", "配置已迁移到数据库"
                );
                return PoetryResult.success(status);
            } else {
                return PoetryResult.fail("获取迁移状态失败");
            }
        } catch (Exception e) {
            log.error("获取迁移状态失败", e);
            return PoetryResult.fail("获取迁移状态失败: " + e.getMessage());
        }
    }

    /**
     * 重置为默认配置
     */
    @LoginCheck(0)
    @PostMapping("/reset")
    @AuditLog(action = "OAUTH_CONFIG_RESET", targetType = "OAUTH_CONFIG", summary = "重置第三方登录配置")
    public PoetryResult<Boolean> resetToDefault() {
        try {
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.resetToDefault();
            syncOauthIfSuccessful(result.isSuccess() && Boolean.TRUE.equals(result.getData()));
            return result;
        } catch (Exception e) {
            log.error("重置配置失败", e);
            return PoetryResult.fail("重置配置失败: " + e.getMessage());
        }
    }

    /**
     * 导出配置为JSON格式
     */
    @LoginCheck(0)
    @GetMapping("/export")
    public PoetryResult<String> exportToJson() {
        try {
            return thirdPartyOauthConfigService.exportToJson();
        } catch (Exception e) {
            log.error("导出配置失败", e);
            return PoetryResult.fail("导出配置失败: " + e.getMessage());
        }
    }

    private List<ThirdPartyOauthPublicConfigDTO> toPublicConfigs(List<ThirdPartyOauthConfig> configs) {
        return configs.stream()
                .map(ThirdPartyOauthPublicConfigDTO::from)
                .toList();
    }

    private void syncOauthIfSuccessful(boolean success) {
        if (success) {
        }
    }
}
