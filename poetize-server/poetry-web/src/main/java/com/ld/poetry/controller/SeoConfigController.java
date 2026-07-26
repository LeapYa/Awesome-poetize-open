package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SeoConfig;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * SEO配置管理控制器
 * </p>
 *
 * @author sara
 * @since 2024-12-23
 */
@RestController
@RequestMapping("/admin/seo")
@Slf4j
public class SeoConfigController {

    @Autowired
    private SeoConfigService seoConfigService;

    @Autowired
    private RagSyncService ragSyncService;

    /**
     * 获取完整SEO配置（结构化）
     */
    @GetMapping("/getFullConfig")
    @LoginCheck(1)
    public PoetryResult<SeoConfig> getFullSeoConfig() {
        try {
            SeoConfig config = seoConfigService.getFullSeoConfig();
            return PoetryResult.success(config);
        } catch (Exception e) {
            log.error("获取完整SEO配置失败", e);
            return PoetryResult.fail("获取SEO配置失败");
        }
    }

    /**
     * 保存完整SEO配置（结构化）
     */
    @PostMapping("/saveFullConfig")
    @LoginCheck(1)
    public PoetryResult<Boolean> saveFullSeoConfig(@RequestBody SeoConfig seoConfig) {
        try {
            boolean success = seoConfigService.saveFullSeoConfig(seoConfig);
            if (success) {
            }
            return success ? PoetryResult.success(true) : PoetryResult.fail("保存SEO配置失败");
        } catch (Exception e) {
            log.error("保存完整SEO配置失败", e);
            return PoetryResult.fail("保存SEO配置失败");
        }
    }

    /**
     * 获取SEO配置（JSON格式）
     */
    @GetMapping("/getConfigAsJson")
    @LoginCheck(1)
    public PoetryResult<Map<String, Object>> getSeoConfigAsJson() {
        try {
            Map<String, Object> config = seoConfigService.getSeoConfigAsJson();
            return PoetryResult.success(config);
        } catch (Exception e) {
            log.error("获取JSON格式SEO配置失败", e);
            return PoetryResult.fail("获取SEO配置失败");
        }
    }

    /**
     * 更新SEO配置（JSON格式）
     */
    @PostMapping("/updateConfigFromJson")
    @LoginCheck(1)
    public PoetryResult<Boolean> updateSeoConfigFromJson(@RequestBody Map<String, Object> jsonConfig) {
        try {
            boolean success = seoConfigService.updateSeoConfigFromJson(jsonConfig);
            if (success) {
            }
            return success ? PoetryResult.success(true) : PoetryResult.fail("更新SEO配置失败");
        } catch (Exception e) {
            log.error("从JSON更新SEO配置失败", e);
            return PoetryResult.fail("更新SEO配置失败");
        }
    }

    /**
     * 初始化默认SEO配置
     */
    @PostMapping("/initDefaultConfig")
    @LoginCheck(1)
    public PoetryResult<Boolean> initDefaultSeoConfig() {
        try {
            boolean success = seoConfigService.initDefaultSeoConfig();
            if (success) {
            }
            return success ? PoetryResult.success(true) : PoetryResult.fail("初始化SEO配置失败");
        } catch (Exception e) {
            log.error("初始化默认SEO配置失败", e);
            return PoetryResult.fail("初始化SEO配置失败");
        }
    }

}
