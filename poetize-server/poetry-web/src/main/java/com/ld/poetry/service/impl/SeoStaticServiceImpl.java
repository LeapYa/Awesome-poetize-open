package com.ld.poetry.service.impl;

import com.ld.poetry.service.SeoStaticService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * <p>
 * SEO静态文件服务实现类
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-25
 */
@Service
@Slf4j
public class SeoStaticServiceImpl implements SeoStaticService {

    @Autowired
    private SeoConfigService seoConfigService;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateManifestJson(HttpServletRequest request) {
        try {
            // 先查 Redis 缓存
            Object cached = redisUtil.get(CacheConstants.MANIFEST_JSON_KEY);
            if (cached instanceof Map) {
                return (Map<String, Object>) cached;
            }

            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            
            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "PWA功能未启用");
                return error;
            }

            // 动态检测站点地址
            String siteUrl = detectSiteUrl(request, seoConfig);

            // 构建manifest.json内容
            Map<String, Object> manifest = new HashMap<>();
            
            // 获取webInfo来使用webTitle
            String webTitle = "POETIZE"; // 默认值
            try {
                var webInfo = cacheService.getCachedWebInfo();
                if (webInfo != null && StringUtils.hasText(webInfo.getWebTitle())) {
                    webTitle = webInfo.getWebTitle();
                } else if (webInfo != null && StringUtils.hasText(webInfo.getWebName())) {
                    webTitle = webInfo.getWebName();
                }
            } catch (Exception e) {
                log.warn("获取webInfo失败，使用默认标题", e);
            }
            
            manifest.put("name", webTitle);
            manifest.put("short_name", getConfigValue(seoConfig, "site_short_name", webTitle));
            manifest.put("description", getConfigValue(seoConfig, "site_description", "一个优雅的博客平台"));
            manifest.put("start_url", "/");
            manifest.put("display", getConfigValue(seoConfig, "pwa_display", "standalone"));
            manifest.put("background_color", getConfigValue(seoConfig, "pwa_background_color", "#ffffff"));
            manifest.put("theme_color", getConfigValue(seoConfig, "pwa_theme_color", "#1976d2"));
            manifest.put("orientation", getConfigValue(seoConfig, "pwa_orientation", "portrait-primary"));
            manifest.put("scope", "/");
            manifest.put("lang", "zh-CN");

            // 添加图标数组
            List<Map<String, Object>> icons = buildIconsArray(seoConfig);
            manifest.put("icons", icons);

            // 添加截图（可选）
            List<Map<String, Object>> screenshots = buildScreenshotsArray(seoConfig);
            if (!screenshots.isEmpty()) {
                manifest.put("screenshots", screenshots);
            }

            // 添加分类和关键词
            addCategoriesFromKeywords(manifest, seoConfig);

            // 添加开发者信息
            addDeveloperInfo(manifest, seoConfig, siteUrl);

            // 添加相关应用
            addRelatedApplications(manifest, seoConfig);

            // 缓存到 Redis（24小时兜底过期，修改网站信息时会主动清除）
            redisUtil.set(CacheConstants.MANIFEST_JSON_KEY, manifest, CacheConstants.SEO_STATIC_EXPIRE_TIME);

            return manifest;

        } catch (Exception e) {
            log.error("生成manifest.json失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "生成PWA manifest失败");
            return error;
        }
    }

    @Override
    public String generateRobotsTxt(HttpServletRequest request) {
        try {
            // 先查 Redis 缓存
            Object cached = redisUtil.get(CacheConstants.ROBOTS_TXT_KEY);
            if (cached instanceof String) {
                return (String) cached;
            }

            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            String robotsTxt = getConfigValue(seoConfig, "robots_txt", getDefaultRobotsTxt());

            // 替换站点地址占位符
            String siteUrl = detectSiteUrl(request, seoConfig);
            robotsTxt = robotsTxt.replace("{site_address}", siteUrl);

            // 缓存到 Redis
            redisUtil.set(CacheConstants.ROBOTS_TXT_KEY, robotsTxt, CacheConstants.SEO_STATIC_EXPIRE_TIME);

            return robotsTxt;

        } catch (Exception e) {
            log.error("生成robots.txt失败", e);
            return getDefaultRobotsTxt();
        }
    }

    @Override
    public boolean needsUpdate(String fileType) {
        // 基于 Redis：key 不存在就需要更新
        String redisKey = getRedisKey(fileType);
        if (redisKey == null) return true;
        return redisUtil.get(redisKey) == null;
    }

    @Override
    public void clearStaticCache(String fileType) {
        if (fileType == null) {
            redisUtil.del(CacheConstants.MANIFEST_JSON_KEY, CacheConstants.ROBOTS_TXT_KEY);
            log.info("已清理所有SEO静态文件Redis缓存（manifest.json、robots.txt）");
        } else {
            String redisKey = getRedisKey(fileType);
            if (redisKey != null) {
                redisUtil.del(redisKey);
            }
            log.info("已清理{}类型的SEO静态文件Redis缓存", fileType);
        }
    }

    /**
     * 根据文件类型获取对应的 Redis 缓存键
     */
    private String getRedisKey(String fileType) {
        if ("manifest_json".equals(fileType)) {
            return CacheConstants.MANIFEST_JSON_KEY;
        } else if ("robots_txt".equals(fileType)) {
            return CacheConstants.ROBOTS_TXT_KEY;
        }
        return null;
    }

    // ========== 私有辅助方法 ==========

    private String detectSiteUrl(HttpServletRequest request, Map<String, Object> seoConfig) {
        // 直接从 MailUtil 获取网站地址
        String siteUrl = mailUtil.getSiteUrl();
        
        if (!StringUtils.hasText(siteUrl) || "http://localhost".equals(siteUrl)) {
            // 如果获取失败或是默认值，尝试从请求中检测
            String scheme = request.getHeader("X-Forwarded-Proto");
            if (!StringUtils.hasText(scheme)) {
                scheme = request.getScheme();
            }

            String host = request.getHeader("X-Forwarded-Host");
            if (!StringUtils.hasText(host)) {
                host = request.getHeader("Host");
            }
            if (!StringUtils.hasText(host)) {
                host = request.getServerName();
                int port = request.getServerPort();
                if (port != 80 && port != 443) {
                    host = host + ":" + port;
                }
            }

            siteUrl = scheme + "://" + host;
        }

        return siteUrl;
    }

    private String getConfigValue(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return (value != null && StringUtils.hasText(value.toString())) ? value.toString() : defaultValue;
    }

    private List<Map<String, Object>> buildIconsArray(Map<String, Object> seoConfig) {
        List<Map<String, Object>> icons = new ArrayList<>();

        // 添加192x192图标
        String icon192 = getConfigValue(seoConfig, "site_icon_192", "");
        if (StringUtils.hasText(icon192)) {
            Map<String, Object> icon = new HashMap<>();
            icon.put("src", icon192);
            icon.put("sizes", "192x192");
            icon.put("type", "image/png");
            icon.put("purpose", "any maskable");
            icons.add(icon);
        }

        // 添加512x512图标
        String icon512 = getConfigValue(seoConfig, "site_icon_512", "");
        if (StringUtils.hasText(icon512)) {
            Map<String, Object> icon = new HashMap<>();
            icon.put("src", icon512);
            icon.put("sizes", "512x512");
            icon.put("type", "image/png");
            icon.put("purpose", "any maskable");
            icons.add(icon);
        }

        // 如果有网站Logo，也加入图标列表
        String siteLogo = getConfigValue(seoConfig, "site_logo", "");
        if (StringUtils.hasText(siteLogo)) {
            Map<String, Object> icon = new HashMap<>();
            icon.put("src", siteLogo);
            icon.put("sizes", "any");
            icon.put("type", "image/png");
            icon.put("purpose", "any");
            icons.add(icon);
        }

        // 如果没有任何图标，使用默认图标
        if (icons.isEmpty()) {
            Map<String, Object> defaultIcon = new HashMap<>();
            defaultIcon.put("src", "/static/assets/poetize.jpg");
            defaultIcon.put("sizes", "any");
            defaultIcon.put("type", "image/jpeg");
            defaultIcon.put("purpose", "any");
            icons.add(defaultIcon);
        }

        return icons;
    }

    private List<Map<String, Object>> buildScreenshotsArray(Map<String, Object> seoConfig) {
        List<Map<String, Object>> screenshots = new ArrayList<>();

        String desktopScreenshot = getConfigValue(seoConfig, "pwa_screenshot_desktop", "");
        if (StringUtils.hasText(desktopScreenshot)) {
            Map<String, Object> screenshot = new HashMap<>();
            screenshot.put("src", desktopScreenshot);
            screenshot.put("sizes", "1280x720");
            screenshot.put("type", "image/png");
            screenshot.put("form_factor", "wide");
            screenshots.add(screenshot);
        }

        String mobileScreenshot = getConfigValue(seoConfig, "pwa_screenshot_mobile", "");
        if (StringUtils.hasText(mobileScreenshot)) {
            Map<String, Object> screenshot = new HashMap<>();
            screenshot.put("src", mobileScreenshot);
            screenshot.put("sizes", "375x667");
            screenshot.put("type", "image/png");
            screenshot.put("form_factor", "narrow");
            screenshots.add(screenshot);
        }

        return screenshots;
    }

    private void addCategoriesFromKeywords(Map<String, Object> manifest, Map<String, Object> seoConfig) {
        String keywords = getConfigValue(seoConfig, "site_keywords", "");
        if (StringUtils.hasText(keywords)) {
            String[] keywordArray = keywords.split("[,，]+");
            List<String> categories = new ArrayList<>();
            for (String keyword : keywordArray) {
                String trimmed = keyword.trim();
                if (StringUtils.hasText(trimmed) && categories.size() < 5) {
                    categories.add(trimmed);
                }
            }
            if (!categories.isEmpty()) {
                manifest.put("categories", categories);
            }
        }
    }

    private void addDeveloperInfo(Map<String, Object> manifest, Map<String, Object> seoConfig, String siteUrl) {
        String defaultAuthor = getConfigValue(seoConfig, "default_author", "");
        if (StringUtils.hasText(defaultAuthor)) {
            Map<String, Object> author = new HashMap<>();
            author.put("name", defaultAuthor);
            author.put("url", siteUrl);
            manifest.put("author", author);
        }
    }

    private void addRelatedApplications(Map<String, Object> manifest, Map<String, Object> seoConfig) {
        List<Map<String, Object>> relatedApplications = new ArrayList<>();

        String androidAppId = getConfigValue(seoConfig, "android_app_id", "");
        if (StringUtils.hasText(androidAppId)) {
            Map<String, Object> androidApp = new HashMap<>();
            androidApp.put("platform", "play");
            androidApp.put("url", "https://play.google.com/store/apps/details?id=" + androidAppId);
            androidApp.put("id", androidAppId);
            relatedApplications.add(androidApp);
        }

        String iosAppId = getConfigValue(seoConfig, "ios_app_id", "");
        if (StringUtils.hasText(iosAppId)) {
            Map<String, Object> iosApp = new HashMap<>();
            iosApp.put("platform", "itunes");
            iosApp.put("url", "https://apps.apple.com/app/id" + iosAppId);
            relatedApplications.add(iosApp);
        }

        if (!relatedApplications.isEmpty()) {
            manifest.put("related_applications", relatedApplications);
            manifest.put("prefer_related_applications", 
                Boolean.TRUE.equals(seoConfig.get("prefer_native_apps")));
        }
    }


    private String getDefaultRobotsTxt() {
        return "User-agent: *\n" +
               "Allow: /\n" +
               "Disallow: /admin/\n" +
               "Disallow: /api/\n" +
               "Disallow: /private/\n" +
               "\n" +
               "# Sitemap\n" +
               "Sitemap: {site_address}/sitemap.xml\n" +
               "\n" +
               "# 搜索引擎爬虫特定规则\n" +
               "User-agent: Baiduspider\n" +
               "Allow: /\n" +
               "\n" +
               "User-agent: Googlebot\n" +
               "Allow: /\n" +
               "\n" +
               "User-agent: Bingbot\n" +
               "Allow: /\n" +
               "\n" +
               "# 爬取延迟\n" +
               "Crawl-delay: 1";
    }
}
