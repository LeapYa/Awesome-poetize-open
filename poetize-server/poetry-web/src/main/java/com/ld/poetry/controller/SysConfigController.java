package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysConfig;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 参数配置表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2024-03-23
 */
@RestController
@RequestMapping("/sysConfig")
@Slf4j
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;
    
    @Autowired
    private com.ld.poetry.service.SitemapService sitemapService;

    @Autowired
    private com.ld.poetry.service.RobotsService robotsService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PrerenderFacade prerenderFacade;

    /**
     * 查询系统参数
     */
    @GetMapping("/listSysConfig")
    public PoetryResult<Map<String, String>> listSysConfig() {
        LambdaQueryChainWrapper<SysConfig> wrapper = new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper());
        List<SysConfig> sysConfigs = wrapper.eq(SysConfig::getConfigType, Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()))
                .list();
        
        // 处理重复的configKey，保留最后一个值，并记录警告日志
        Map<String, String> collect = sysConfigs.stream().collect(
            Collectors.toMap(
                SysConfig::getConfigKey, 
                SysConfig::getConfigValue,
                (oldValue, newValue) -> {
                    log.warn("检测到重复的配置键，将使用新值覆盖旧值。旧值: {}, 新值: {}", oldValue, newValue);
                    return newValue;
                }
            )
        );
        return PoetryResult.success(collect);
    }

    /**
     * 保存或更新
     */
    @PostMapping("/saveOrUpdateConfig")
    @LoginCheck(0)
    @AuditLog(action = "SYS_CONFIG_SAVE", targetType = "SYS_CONFIG", targetIdParam = "configKey", summary = "保存系统配置")
    public PoetryResult saveConfig(@RequestBody SysConfig sysConfig) {
        if (!StringUtils.hasText(sysConfig.getConfigName()) ||
                !StringUtils.hasText(sysConfig.getConfigKey()) ||
                !StringUtils.hasText(sysConfig.getConfigType())) {
            return PoetryResult.fail("请完善所有配置信息！");
        }
        String configType = sysConfig.getConfigType();
        if (!Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()).equals(configType) &&
                !Integer.toString(PoetryEnum.SYS_CONFIG_PRIVATE.getCode()).equals(configType)) {
            return PoetryResult.fail("配置类型不正确！");
        }
        
        // 保存前获取旧的配置值（如果存在）
        String oldValue = null;
        if (sysConfig.getId() != null) {
            SysConfig oldConfig = sysConfigService.getById(sysConfig.getId());
            if (oldConfig != null) {
                oldValue = oldConfig.getConfigValue();
            }
        } else {
            // 未携带 id 时，按 configKey + configType 查找已有记录，自动转为更新
            LambdaQueryChainWrapper<SysConfig> checkWrapper = new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper());
            SysConfig existingConfig = checkWrapper
                    .eq(SysConfig::getConfigKey, sysConfig.getConfigKey())
                    .eq(SysConfig::getConfigType, sysConfig.getConfigType())
                    .one();
            
            if (existingConfig != null) {
                // 已存在同 key 配置：回填 id，走更新路径
                sysConfig.setId(existingConfig.getId());
                oldValue = existingConfig.getConfigValue();
            }
        }
        
        boolean success = sysConfigService.saveOrUpdate(sysConfig);

        // 公开配置变更后清除 bootstrap 聚合缓存，下次请求会回填最新值
        if (success && Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode())
                .equals(sysConfig.getConfigType())) {
            cacheService.evictPublicSysConfigMap();
            // 异步重建预渲染 HTML，让 sysConfig 变更（如 footer.friendLinks）反映到静态产物
            try {
                prerenderFacade.rebuildSiteAsync(java.time.Duration.ofSeconds(2));
            } catch (Exception e) {
                log.warn("sysConfig 变更后触发预渲染重建失败，配置项: {}", sysConfig.getConfigKey(), e);
            }
        }

        // 检查是否是影响sitemap的关键配置
        if (success && isConfigAffectingSitemap(sysConfig.getConfigKey(), oldValue, sysConfig.getConfigValue())) {
            try {
                // 重新生成sitemap并推送到搜索引擎（配置变更影响所有URL）
                if (sitemapService != null) {
                    String reason = String.format("系统配置更新: %s", sysConfig.getConfigKey());
                    sitemapService.updateSitemapAndPush(reason);
                    log.info("系统配置更新后已重新生成sitemap并推送到搜索引擎，配置项: {}", sysConfig.getConfigKey());
                }

                // 清除robots.txt缓存，让下次访问时重新生成
                if (robotsService != null) {
                    robotsService.clearRobotsCache();
                    log.info("系统配置更新后已清除robots.txt缓存，配置项: {}", sysConfig.getConfigKey());
                }
            } catch (Exception e) {
                log.warn("系统配置更新后清除SEO缓存失败，配置项: {}", sysConfig.getConfigKey(), e);
            }
        }

        return PoetryResult.success();
    }

    /**
     * 删除
     */
    @GetMapping("/deleteConfig")
    @LoginCheck(0)
    @AuditLog(action = "SYS_CONFIG_DELETE", targetType = "SYS_CONFIG", targetIdParam = "id", summary = "删除系统配置")
    public PoetryResult deleteConfig(@RequestParam("id") Integer id) {
        // 获取要删除的配置信息
        SysConfig config = sysConfigService.getById(id);
        if (config != null) {
            boolean success = sysConfigService.removeById(id);

            // 公开配置删除后清除 bootstrap 聚合缓存，下次请求会回填最新值
            if (success && Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode())
                    .equals(config.getConfigType())) {
                cacheService.evictPublicSysConfigMap();
                // 异步重建预渲染 HTML，让 sysConfig 删除（如 footer.friendLinks）反映到静态产物
                try {
                    prerenderFacade.rebuildSiteAsync(java.time.Duration.ofSeconds(2));
                } catch (Exception e) {
                    log.warn("sysConfig 删除后触发预渲染重建失败，配置项: {}", config.getConfigKey(), e);
                }
            }

            // 检查删除的配置是否影响sitemap
            if (success && isConfigAffectingSitemap(config.getConfigKey(), config.getConfigValue(), null)) {
                try {
                    // 清除sitemap缓存
                    if (sitemapService != null) {
                        sitemapService.clearSitemapCache();
                        log.info("系统配置删除后已清除sitemap缓存，配置项: {}", config.getConfigKey());
                    }
                    
                    // 清除robots.txt缓存
                    if (robotsService != null) {
                        robotsService.clearRobotsCache();
                        log.info("系统配置删除后已清除robots.txt缓存，配置项: {}", config.getConfigKey());
                    }
                } catch (Exception e) {
                    log.warn("系统配置删除后清除SEO缓存失败，配置项: {}", config.getConfigKey(), e);
                }
            }
        }
        return PoetryResult.success();
    }

    /**
     * 查询
     */
    @GetMapping("/listConfig")
    @LoginCheck(0)
    public PoetryResult<List<SysConfig>> listConfig() {
        return PoetryResult.success(new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper()).list());
    }
    
    /**
     * 判断配置变更是否影响sitemap生成
     * @param configKey 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     * @return 是否需要更新sitemap
     */
    private boolean isConfigAffectingSitemap(String configKey, String oldValue, String newValue) {
        if (configKey == null || java.util.Objects.equals(oldValue, newValue)) {
            return false;
        }
        
        // 定义影响sitemap的配置键列表
        String[] sitemapAffectingKeys = {
            "SITEMAP_EXCLUDE",   // sitemap排除页面
            "SITEMAP_PRIORITY",  // sitemap优先级
            "SITEMAP_CHANGE_FREQUENCY",  // sitemap更新频率
            "SEARCH_ENGINE_PING_ENABLED",  // 搜索引擎推送启用状态
            "SEARCH_ENGINE_PING_MIN_INTERVAL",  // 搜索引擎推送最小间隔
            "ENABLED_SEARCH_ENGINES"  // 启用的搜索引擎列表
            // 注意：网站URL现在从Python SEO配置或环境变量获取，不再依赖Java数据库配置
        };
        
        for (String key : sitemapAffectingKeys) {
            if (key.equalsIgnoreCase(configKey)) {
                log.info("检测到影响sitemap的配置变更: {} 从 '{}' 变为 '{}'", configKey, oldValue, newValue);
                return true;
            }
        }
        
        return false;
    }
}
