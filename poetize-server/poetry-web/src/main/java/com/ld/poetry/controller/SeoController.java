package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.SeoMetaService;
import com.ld.poetry.service.SeoStaticService;
import com.ld.poetry.service.SeoConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * SEO公开功能控制器
 * 提供前端页面SEO元数据生成和静态文件服务
 * </p>
 *
 * @author sara
 * @since 2024-12-23
 */
@RestController
@RequestMapping("/seo")
@Slf4j
public class SeoController {

    @Autowired
    private SeoMetaService seoMetaService;

    @Autowired
    private SeoStaticService seoStaticService;

    @Autowired
    private SeoConfigService seoConfigService;

    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    // ========== 元数据生成API（公开接口） ==========


    /**
     * 获取网站首页SEO元数据
     */
    @GetMapping("/getSiteMeta")
    public PoetryResult<Map<String, Object>> getSiteMeta(
            @RequestParam(value = "lang", defaultValue = "zh-CN") String language) {
        try {
            Map<String, Object> meta = seoMetaService.generateSiteMeta(language);
            return PoetryResult.success(meta);
        } catch (Exception e) {
            log.error("获取网站SEO元数据失败", e);
            return PoetryResult.fail("获取网站元数据失败");
        }
    }

    /**
     * 获取分类页面SEO元数据
     */
    @GetMapping("/getCategoryMeta")
    public PoetryResult<Map<String, Object>> getCategoryMeta(
            @RequestParam("categoryId") Integer categoryId,
            @RequestParam(value = "lang", defaultValue = "zh-CN") String language) {
        try {
            Map<String, Object> meta = seoMetaService.generateCategoryMeta(categoryId, language);
            return PoetryResult.success(meta);
        } catch (Exception e) {
            log.error("获取分类SEO元数据失败: categoryId={}", categoryId, e);
            return PoetryResult.fail("获取分类元数据失败");
        }
    }

    /**
     * 获取IM站点SEO元数据
     */
    @GetMapping("/getIMSiteMeta")
    public PoetryResult<Map<String, Object>> getImSiteMeta(
            @RequestParam(value = "lang", defaultValue = "zh-CN") String language) {
        try {
            Map<String, Object> meta = seoMetaService.generateImSiteMeta(language);
            return PoetryResult.success(meta);
        } catch (Exception e) {
            log.error("获取IM站点SEO元数据失败", e);
            return PoetryResult.fail("获取IM站点元数据失败");
        }
    }

    // ========== 静态文件生成API（公开接口） ==========

    /**
     * 动态生成PWA manifest.json
     */
    @GetMapping(value = "/manifest.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getManifestJson(HttpServletRequest request) {
        try {
            Map<String, Object> manifest = seoStaticService.generateManifestJson(request);
            
            if (manifest.containsKey("error")) {
                return ResponseEntity.status(404).body(manifest);
            }

            return ResponseEntity.ok()
                    .header("Cache-Control", "public, max-age=3600")
                    .body(manifest);
        } catch (Exception e) {
            log.error("生成manifest.json失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "生成PWA manifest失败");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 生成robots.txt
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRobotsTxt(HttpServletRequest request) {
        try {
            String robotsTxt = seoStaticService.generateRobotsTxt(request);
            
            return ResponseEntity.ok()
                    .header("Cache-Control", "public, max-age=3600")
                    .body(robotsTxt);
        } catch (Exception e) {
            log.error("生成robots.txt失败", e);
            return ResponseEntity.status(500)
                    .body("# robots.txt生成失败");
        }
    }

    /**
     * 供Nginx使用的SEO配置接口（无权限验证）
     * 修复预渲染缺少自定义头部代码和站点验证标签的问题
     */
    @GetMapping("/getSeoConfig/nginx")
    public ResponseEntity<Map<String, Object>> getSeoConfigForNginx() {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            
            // 返回预渲染服务需要的完整字段（保持原有字段以确保向后兼容）
            Map<String, Object> configForNginx = new HashMap<>();
            
            // 基础配置字段
            configForNginx.put("enable", seoConfig.get("enable"));
            configForNginx.put("site_icon", seoConfig.get("site_icon"));
            configForNginx.put("site_logo", seoConfig.get("site_logo"));
            configForNginx.put("apple_touch_icon", seoConfig.get("apple_touch_icon"));
            configForNginx.put("site_icon_192", seoConfig.get("site_icon_192"));
            configForNginx.put("site_icon_512", seoConfig.get("site_icon_512"));
            configForNginx.put("og_image", seoConfig.get("og_image"));
            configForNginx.put("site_description", seoConfig.get("site_description"));
            configForNginx.put("site_keywords", seoConfig.get("site_keywords"));
            // 直接从 MailUtil 获取网站地址
            configForNginx.put("site_address", mailUtil.getSiteUrl());
            configForNginx.put("default_author", seoConfig.get("default_author"));
            configForNginx.put("site_short_name", seoConfig.get("site_short_name"));
            configForNginx.put("robots_default", seoConfig.get("robots_default"));

            // 自定义头部代码
            configForNginx.put("custom_head_code", seoConfig.get("custom_head_code"));

            // 站点验证标签
            configForNginx.put("baidu_site_verification", seoConfig.get("baidu_site_verification"));
            configForNginx.put("google_site_verification", seoConfig.get("google_site_verification"));
            configForNginx.put("bing_site_verification", seoConfig.get("bing_site_verification"));
            configForNginx.put("yandex_site_verification", seoConfig.get("yandex_site_verification"));
            configForNginx.put("sogou_site_verification", seoConfig.get("sogou_site_verification"));
            configForNginx.put("so_site_verification", seoConfig.get("so_site_verification"));
            configForNginx.put("shenma_site_verification", seoConfig.get("shenma_site_verification"));
            configForNginx.put("yahoo_site_verification", seoConfig.get("yahoo_site_verification"));
            configForNginx.put("duckduckgo_site_verification", seoConfig.get("duckduckgo_site_verification"));

            // 社交媒体相关字段（预渲染能处理的高价值字段）
            configForNginx.put("twitter_site", seoConfig.get("twitter_site"));
            configForNginx.put("twitter_creator", seoConfig.get("twitter_creator"));
            configForNginx.put("twitter_card", seoConfig.get("twitter_card"));
            
            // Facebook相关
            configForNginx.put("fb_app_id", seoConfig.get("fb_app_id"));
            configForNginx.put("fb_page_url", seoConfig.get("fb_page_url"));
            
            // Open Graph增强
            configForNginx.put("og_type", seoConfig.get("og_type"));
            
            // LinkedIn支持
            configForNginx.put("linkedin_company_id", seoConfig.get("linkedin_company_id"));
            
            // Pinterest增强
            configForNginx.put("pinterest_verification", seoConfig.get("pinterest_verification"));
            configForNginx.put("pinterest_description", seoConfig.get("pinterest_description"));
            
            // 小程序支持
            configForNginx.put("wechat_miniprogram_id", seoConfig.get("wechat_miniprogram_id"));
            configForNginx.put("wechat_miniprogram_path", seoConfig.get("wechat_miniprogram_path"));
            configForNginx.put("qq_miniprogram_path", seoConfig.get("qq_miniprogram_path"));

            // 注意：PWA相关配置通过 /manifest.json 端点提供，预渲染只需添加 manifest 链接

            return ResponseEntity.ok(configForNginx);
        } catch (Exception e) {
            log.error("获取Nginx用SEO配置失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取SEO配置失败");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
