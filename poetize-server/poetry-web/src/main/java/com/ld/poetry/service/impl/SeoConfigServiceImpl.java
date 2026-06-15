package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.dao.SeoConfigMapper;
import com.ld.poetry.dao.SeoSearchEnginePushMapper;
import com.ld.poetry.dao.SeoSiteVerificationMapper;
import com.ld.poetry.dao.SeoSocialMediaMapper;
import com.ld.poetry.dao.SeoPwaConfigMapper;
import com.ld.poetry.dao.SeoNotificationConfigMapper;
import com.ld.poetry.entity.SeoConfig;
import com.ld.poetry.entity.SeoSearchEnginePush;
import com.ld.poetry.entity.SeoSiteVerification;
import com.ld.poetry.entity.SeoSocialMedia;
import com.ld.poetry.entity.SeoPwaConfig;
import com.ld.poetry.entity.SeoNotificationConfig;
import com.ld.poetry.service.SeoConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * SEO配置服务实现类
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-25
 */
@Service
@Slf4j
public class SeoConfigServiceImpl extends ServiceImpl<SeoConfigMapper, SeoConfig> implements SeoConfigService {

    @Autowired
    private SeoSearchEnginePushMapper seoSearchEnginePushMapper;

    @Autowired
    private SeoSiteVerificationMapper seoSiteVerificationMapper;

    @Autowired
    private SeoSocialMediaMapper seoSocialMediaMapper;

    @Autowired
    private SeoPwaConfigMapper seoPwaConfigMapper;

    @Autowired
    private SeoNotificationConfigMapper seoNotificationConfigMapper;


    @Override
    public SeoConfig getFullSeoConfig() {
        try {
            // 获取主配置，如果不存在则创建默认配置
            SeoConfig seoConfig = this.getOne(new LambdaQueryWrapper<SeoConfig>().last("LIMIT 1"));
            if (seoConfig == null) {
                initDefaultSeoConfig();
                seoConfig = this.getOne(new LambdaQueryWrapper<SeoConfig>().last("LIMIT 1"));
            }

            if (seoConfig != null) {
                Integer configId = seoConfig.getId();

                // 加载搜索引擎推送配置
                seoConfig.setSearchEnginePushList(
                    seoSearchEnginePushMapper.selectList(
                        new LambdaQueryWrapper<SeoSearchEnginePush>()
                            .eq(SeoSearchEnginePush::getSeoConfigId, configId)
                    )
                );

                // 加载网站验证配置
                seoConfig.setSiteVerificationList(
                    seoSiteVerificationMapper.selectList(
                        new LambdaQueryWrapper<SeoSiteVerification>()
                            .eq(SeoSiteVerification::getSeoConfigId, configId)
                    )
                );

                // 加载社交媒体配置
                seoConfig.setSocialMedia(
                    seoSocialMediaMapper.selectOne(
                        new LambdaQueryWrapper<SeoSocialMedia>()
                            .eq(SeoSocialMedia::getSeoConfigId, configId)
                    )
                );

                // 加载PWA配置
                seoConfig.setPwaConfig(
                    seoPwaConfigMapper.selectOne(
                        new LambdaQueryWrapper<SeoPwaConfig>()
                            .eq(SeoPwaConfig::getSeoConfigId, configId)
                    )
                );

                // 加载通知配置
                seoConfig.setNotificationConfig(
                    seoNotificationConfigMapper.selectOne(
                        new LambdaQueryWrapper<SeoNotificationConfig>()
                            .eq(SeoNotificationConfig::getSeoConfigId, configId)
                    )
                );
            }

            return seoConfig;
        } catch (Exception e) {
            log.error("获取完整SEO配置失败", e);
            return null;
        }
    }

    @Override
    @Transactional
    public boolean saveFullSeoConfig(SeoConfig seoConfig) {
        try {
            // 保存或更新主配置
            seoConfig.setUpdateTime(LocalDateTime.now());
            boolean success = this.saveOrUpdate(seoConfig);
            if (!success) {
                return false;
            }

            Integer configId = seoConfig.getId();

            // 保存搜索引擎推送配置
            if (seoConfig.getSearchEnginePushList() != null) {
                // 先删除现有配置
                seoSearchEnginePushMapper.delete(
                    new LambdaQueryWrapper<SeoSearchEnginePush>()
                        .eq(SeoSearchEnginePush::getSeoConfigId, configId)
                );
                
                // 插入新配置
                for (SeoSearchEnginePush push : seoConfig.getSearchEnginePushList()) {
                    push.setSeoConfigId(configId);
                    push.setCreateTime(LocalDateTime.now());
                    push.setUpdateTime(LocalDateTime.now());
                    seoSearchEnginePushMapper.insert(push);
                }
            }

            // 保存网站验证配置
            if (seoConfig.getSiteVerificationList() != null) {
                seoSiteVerificationMapper.delete(
                    new LambdaQueryWrapper<SeoSiteVerification>()
                        .eq(SeoSiteVerification::getSeoConfigId, configId)
                );
                
                for (SeoSiteVerification verification : seoConfig.getSiteVerificationList()) {
                    verification.setSeoConfigId(configId);
                    verification.setCreateTime(LocalDateTime.now());
                    verification.setUpdateTime(LocalDateTime.now());
                    seoSiteVerificationMapper.insert(verification);
                }
            }

            // 保存社交媒体配置
            if (seoConfig.getSocialMedia() != null) {
                seoSocialMediaMapper.delete(
                    new LambdaQueryWrapper<SeoSocialMedia>()
                        .eq(SeoSocialMedia::getSeoConfigId, configId)
                );
                
                seoConfig.getSocialMedia().setSeoConfigId(configId);
                seoConfig.getSocialMedia().setCreateTime(LocalDateTime.now());
                seoConfig.getSocialMedia().setUpdateTime(LocalDateTime.now());
                seoSocialMediaMapper.insert(seoConfig.getSocialMedia());
            }

            // 保存PWA配置
            if (seoConfig.getPwaConfig() != null) {
                seoPwaConfigMapper.delete(
                    new LambdaQueryWrapper<SeoPwaConfig>()
                        .eq(SeoPwaConfig::getSeoConfigId, configId)
                );
                
                seoConfig.getPwaConfig().setSeoConfigId(configId);
                seoConfig.getPwaConfig().setCreateTime(LocalDateTime.now());
                seoConfig.getPwaConfig().setUpdateTime(LocalDateTime.now());
                seoPwaConfigMapper.insert(seoConfig.getPwaConfig());
            }

            // 保存通知配置
            if (seoConfig.getNotificationConfig() != null) {
                seoNotificationConfigMapper.delete(
                    new LambdaQueryWrapper<SeoNotificationConfig>()
                        .eq(SeoNotificationConfig::getSeoConfigId, configId)
                );
                
                seoConfig.getNotificationConfig().setSeoConfigId(configId);
                seoConfig.getNotificationConfig().setCreateTime(LocalDateTime.now());
                seoConfig.getNotificationConfig().setUpdateTime(LocalDateTime.now());
                seoNotificationConfigMapper.insert(seoConfig.getNotificationConfig());
            }

            return true;

        } catch (Exception e) {
            log.error("保存完整SEO配置失败", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getSeoConfigAsJson() {
        try {
            SeoConfig fullConfig = getFullSeoConfig();
            if (fullConfig == null) {
                return createDefaultJsonConfig();
            }

            Map<String, Object> jsonConfig = new HashMap<>();

            // 主配置转换
            jsonConfig.put("enable", fullConfig.getEnable() != null ? fullConfig.getEnable() : true);
            jsonConfig.put("site_description", nullSafeString(fullConfig.getSiteDescription(), "一个优雅的博客系统，支持多用户和多种内容格式"));
            jsonConfig.put("site_keywords", nullSafeString(fullConfig.getSiteKeywords(), "博客,文章,笔记,技术,Java,Spring Boot,Vue.js"));
            jsonConfig.put("site_logo", nullSafeString(fullConfig.getSiteLogo(), ""));
            jsonConfig.put("site_icon", nullSafeString(fullConfig.getSiteIcon(), ""));
            jsonConfig.put("site_icon_192", nullSafeString(fullConfig.getSiteIcon192(), ""));
            jsonConfig.put("site_icon_512", nullSafeString(fullConfig.getSiteIcon512(), ""));
            jsonConfig.put("apple_touch_icon", nullSafeString(fullConfig.getAppleTouchIcon(), ""));
            jsonConfig.put("site_short_name", nullSafeString(fullConfig.getSiteShortName(), ""));
            jsonConfig.put("default_author", nullSafeString(fullConfig.getDefaultAuthor(), "Admin"));
            jsonConfig.put("custom_head_code", nullSafeString(fullConfig.getCustomHeadCode(), ""));
            jsonConfig.put("robots_txt", nullSafeString(fullConfig.getRobotsTxt(), getDefaultRobotsTxt()));
            jsonConfig.put("auto_generate_meta_tags", fullConfig.getAutoGenerateMetaTags() != null ? fullConfig.getAutoGenerateMetaTags() : true);
            jsonConfig.put("generate_sitemap", fullConfig.getGenerateSitemap() != null ? fullConfig.getGenerateSitemap() : true);
            jsonConfig.put("sitemap_change_frequency", nullSafeString(fullConfig.getSitemapChangeFrequency(), "weekly"));
            jsonConfig.put("sitemap_priority", nullSafeString(fullConfig.getSitemapPriority(), "0.7"));
            // 对于sitemap_exclude，空字符串是有效值（表示不排除任何页面），只有null时才使用默认值
            String sitemapExclude = fullConfig.getSitemapExclude();
            jsonConfig.put("sitemap_exclude", nullSafeString(sitemapExclude, ""));

            // 搜索引擎推送配置转换
            Map<String, SeoSearchEnginePush> pushMap = new HashMap<>();
            if (fullConfig.getSearchEnginePushList() != null) {
                pushMap = fullConfig.getSearchEnginePushList()
                    .stream()
                    .collect(Collectors.toMap(
                        SeoSearchEnginePush::getEngineName, 
                        push -> push
                    ));
            }

            // 转换搜索引擎配置到JSON格式
            addSearchEngineConfig(jsonConfig, pushMap, "baidu", "baidu_push_enabled", "baidu_token", "baidu_push_token");
            addSearchEngineConfig(jsonConfig, pushMap, "bing", "bing_push_enabled", "bing_api_key", null);
            addSearchEngineConfig(jsonConfig, pushMap, "yandex", "yandex_push_enabled", "yandex_api_key", null);
            addSearchEngineConfig(jsonConfig, pushMap, "sogou", "sogou_push_enabled", "sogou_token", "sogou_push_token");
            addSearchEngineConfig(jsonConfig, pushMap, "shenma", "shenma_push_enabled", "shenma_token", null);
            addSearchEngineConfig(jsonConfig, pushMap, "duckduckgo", "duckduckgo_push_enabled", null, null);

            // 网站验证配置转换
            Map<String, SeoSiteVerification> verificationMap = new HashMap<>();
            if (fullConfig.getSiteVerificationList() != null) {
                verificationMap = fullConfig.getSiteVerificationList()
                    .stream()
                    .collect(Collectors.toMap(
                        SeoSiteVerification::getPlatform,
                        verification -> verification
                    ));
            }

            addSiteVerification(jsonConfig, verificationMap, "baidu", "baidu_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "google", "google_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "bing", "bing_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "yandex", "yandex_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "sogou", "sogou_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "so", "so_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "shenma", "shenma_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "yahoo", "yahoo_site_verification");
            addSiteVerification(jsonConfig, verificationMap, "duckduckgo", "duckduckgo_site_verification");

            // 社交媒体配置转换
            SeoSocialMedia social = fullConfig.getSocialMedia();
            if (social != null) {
                jsonConfig.put("twitter_card", nullSafeString(social.getTwitterCard(), "summary_large_image"));
                jsonConfig.put("twitter_site", nullSafeString(social.getTwitterSite(), ""));
                jsonConfig.put("twitter_creator", nullSafeString(social.getTwitterCreator(), ""));
                jsonConfig.put("og_type", nullSafeString(social.getOgType(), "article"));
                jsonConfig.put("og_image", nullSafeString(social.getOgImage(), ""));
                jsonConfig.put("fb_app_id", nullSafeString(social.getFbAppId(), ""));
                jsonConfig.put("fb_page_url", nullSafeString(social.getFbPageUrl(), ""));
                jsonConfig.put("linkedin_company_id", nullSafeString(social.getLinkedinCompanyId(), ""));
                jsonConfig.put("linkedin_mode", nullSafeString(social.getLinkedinMode(), "standard"));
                jsonConfig.put("pinterest_verification", nullSafeString(social.getPinterestVerification(), ""));
                jsonConfig.put("pinterest_description", nullSafeString(social.getPinterestDescription(), ""));
                jsonConfig.put("wechat_miniprogram_path", nullSafeString(social.getWechatMiniprogramPath(), ""));
                jsonConfig.put("wechat_miniprogram_id", nullSafeString(social.getWechatMiniprogramId(), ""));
                jsonConfig.put("qq_miniprogram_path", nullSafeString(social.getQqMiniprogramPath(), ""));
            } else {
                // 设置社交媒体默认值
                setSocialMediaDefaults(jsonConfig);
            }

            // PWA配置转换
            SeoPwaConfig pwa = fullConfig.getPwaConfig();
            if (pwa != null) {
                jsonConfig.put("pwa_display", nullSafeString(pwa.getPwaDisplay(), "standalone"));
                jsonConfig.put("pwa_background_color", nullSafeString(pwa.getPwaBackgroundColor(), "#ffffff"));
                jsonConfig.put("pwa_theme_color", nullSafeString(pwa.getPwaThemeColor(), "#1976d2"));
                jsonConfig.put("pwa_orientation", nullSafeString(pwa.getPwaOrientation(), "portrait-primary"));
                jsonConfig.put("pwa_screenshot_desktop", nullSafeString(pwa.getPwaScreenshotDesktop(), ""));
                jsonConfig.put("pwa_screenshot_mobile", nullSafeString(pwa.getPwaScreenshotMobile(), ""));
                jsonConfig.put("android_app_id", nullSafeString(pwa.getAndroidAppId(), ""));
                jsonConfig.put("ios_app_id", nullSafeString(pwa.getIosAppId(), ""));
                jsonConfig.put("prefer_native_apps", pwa.getPreferNativeApps() != null ? pwa.getPreferNativeApps() : false);
            } else {
                // 设置PWA默认值
                setPwaDefaults(jsonConfig);
            }

            // 通知配置转换
            SeoNotificationConfig notification = fullConfig.getNotificationConfig();
            if (notification != null) {
                jsonConfig.put("push_delay_seconds", notification.getPushDelaySeconds() != null ? notification.getPushDelaySeconds() : 300);
                jsonConfig.put("enable_push_notification", notification.getEnablePushNotification() != null ? notification.getEnablePushNotification() : false);
                jsonConfig.put("notify_only_on_failure", notification.getNotifyOnlyOnFailure() != null ? notification.getNotifyOnlyOnFailure() : false);
                // 移除notification_email字段，改为自动发送给文章作者邮箱
            } else {
                // 设置通知配置默认值
                setNotificationDefaults(jsonConfig);
            }

            return jsonConfig;

        } catch (Exception e) {
            log.error("转换SEO配置为JSON格式失败", e);
            return createDefaultJsonConfig();
        }
    }

    @Override
    @Transactional
    public boolean updateSeoConfigFromJson(Map<String, Object> jsonConfig) {
        try {
            // 获取或创建主配置
            SeoConfig seoConfig = this.getOne(new LambdaQueryWrapper<SeoConfig>().last("LIMIT 1"));
            if (seoConfig == null) {
                seoConfig = new SeoConfig();
                seoConfig.setCreateTime(LocalDateTime.now());
            }
            seoConfig.setUpdateTime(LocalDateTime.now());

            // 更新主配置字段
            updateMainConfigFromJson(seoConfig, jsonConfig);

            // 保存主配置
            boolean success = this.saveOrUpdate(seoConfig);
            if (!success) {
                return false;
            }
            
            // 特殊处理sitemap_exclude字段：强制更新以确保空值能被正确保存
            if (jsonConfig.containsKey("sitemap_exclude")) {
                this.update()
                    .set("sitemap_exclude", seoConfig.getSitemapExclude())
                    .eq("id", seoConfig.getId())
                    .update();
            }

            Integer configId = seoConfig.getId();

            // 更新搜索引擎推送配置
            updateSearchEnginePushConfigFromJson(configId, jsonConfig);

            // 更新网站验证配置
            updateSiteVerificationConfigFromJson(configId, jsonConfig);

            // 更新社交媒体配置
            updateSocialMediaConfigFromJson(configId, jsonConfig);

            // 更新PWA配置
            updatePwaConfigFromJson(configId, jsonConfig);

            // 更新通知配置
            updateNotificationConfigFromJson(configId, jsonConfig);

            return true;

        } catch (Exception e) {
            log.error("从JSON更新SEO配置失败", e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean initDefaultSeoConfig() {
        try {
            // 检查是否已存在配置
            long count = this.count();
            if (count > 0) {
                return true;
            }

            // 创建默认主配置
            SeoConfig defaultConfig = createDefaultMainConfig();
            boolean success = this.save(defaultConfig);
            if (!success) {
                return false;
            }

            Integer configId = defaultConfig.getId();

            // 初始化默认搜索引擎推送配置
            initDefaultSearchEnginePushConfig(configId);

            // 初始化默认网站验证配置
            initDefaultSiteVerificationConfig(configId);

            // 初始化默认社交媒体配置
            initDefaultSocialMediaConfig(configId);

            // 初始化默认PWA配置
            initDefaultPwaConfig(configId);

            // 初始化默认通知配置
            initDefaultNotificationConfig(configId);

            log.info("SEO配置初始化完成");
            return true;

        } catch (Exception e) {
            log.error("初始化默认SEO配置失败", e);
            return false;
        }
    }

    // ========== 私有辅助方法 ==========

    private String nullSafeString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void addSearchEngineConfig(Map<String, Object> jsonConfig, 
                                     Map<String, SeoSearchEnginePush> pushMap, 
                                     String engineName, 
                                     String enabledKey, 
                                     String apiKeyKey, 
                                     String tokenKey) {
        SeoSearchEnginePush push = pushMap.get(engineName);
        if (push != null) {
            jsonConfig.put(enabledKey, push.getPushEnabled() != null ? push.getPushEnabled() : false);
            if (apiKeyKey != null) {
                jsonConfig.put(apiKeyKey, nullSafeString(push.getApiKey(), ""));
            }
            if (tokenKey != null) {
                jsonConfig.put(tokenKey, nullSafeString(push.getApiToken(), ""));
            }
        } else {
            jsonConfig.put(enabledKey, false);
            if (apiKeyKey != null) {
                jsonConfig.put(apiKeyKey, "");
            }
            if (tokenKey != null) {
                jsonConfig.put(tokenKey, "");
            }
        }
    }

    private void addSiteVerification(Map<String, Object> jsonConfig,
                                   Map<String, SeoSiteVerification> verificationMap,
                                   String platform,
                                   String jsonKey) {
        SeoSiteVerification verification = verificationMap.get(platform);
        jsonConfig.put(jsonKey, verification != null ? 
            nullSafeString(verification.getVerificationCode(), "") : "");
    }

    private void setSocialMediaDefaults(Map<String, Object> jsonConfig) {
        jsonConfig.put("twitter_card", "summary_large_image");
        jsonConfig.put("twitter_site", "");
        jsonConfig.put("twitter_creator", "");
        jsonConfig.put("og_type", "article");
        jsonConfig.put("og_image", "");
        jsonConfig.put("fb_app_id", "");
        jsonConfig.put("fb_page_url", "");
        jsonConfig.put("linkedin_company_id", "");
        jsonConfig.put("linkedin_mode", "standard");
        jsonConfig.put("pinterest_verification", "");
        jsonConfig.put("pinterest_description", "");
        jsonConfig.put("wechat_miniprogram_path", "");
        jsonConfig.put("wechat_miniprogram_id", "");
        jsonConfig.put("qq_miniprogram_path", "");
    }

    private void setPwaDefaults(Map<String, Object> jsonConfig) {
        jsonConfig.put("pwa_display", "standalone");
        jsonConfig.put("pwa_background_color", "#ffffff");
        jsonConfig.put("pwa_theme_color", "#1976d2");
        jsonConfig.put("pwa_orientation", "portrait-primary");
        jsonConfig.put("pwa_screenshot_desktop", "");
        jsonConfig.put("pwa_screenshot_mobile", "");
        jsonConfig.put("android_app_id", "");
        jsonConfig.put("ios_app_id", "");
        jsonConfig.put("prefer_native_apps", false);
    }

    private void setNotificationDefaults(Map<String, Object> jsonConfig) {
        jsonConfig.put("push_delay_seconds", 300);
        jsonConfig.put("enable_push_notification", false);
        jsonConfig.put("notify_only_on_failure", false);
        // 移除notification_email字段，改为自动发送给文章作者邮箱
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

    private Map<String, Object> createDefaultJsonConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();
        
        // 基础配置
        defaultConfig.put("enable", true);
        defaultConfig.put("site_description", "一个优雅的博客系统，支持多用户和多种内容格式");
        defaultConfig.put("site_keywords", "博客,文章,笔记,技术,Java,Spring Boot,Vue.js");
        defaultConfig.put("default_author", "Admin");
        defaultConfig.put("robots_txt", getDefaultRobotsTxt());
        
        // Sitemap配置
        defaultConfig.put("auto_generate_meta_tags", true);
        defaultConfig.put("generate_sitemap", true);
        defaultConfig.put("sitemap_change_frequency", "weekly");
        defaultConfig.put("sitemap_priority", "0.7");
        defaultConfig.put("sitemap_exclude", "/love");
        
        // 设置所有搜索引擎配置为默认值
        String[] engines = {"baidu", "google", "bing", "yandex", "yahoo", "sogou", "so", "shenma", "duckduckgo"};
        for (String engine : engines) {
            switch (engine) {
                case "baidu":
                    defaultConfig.put("baidu_push_enabled", false);
                    defaultConfig.put("baidu_token", "");
                    defaultConfig.put("baidu_push_token", "");
                    defaultConfig.put("baidu_site_verification", "");
                    break;
                case "google":
                    defaultConfig.put("google_site_verification", "");
                    break;
                case "bing":
                    defaultConfig.put("bing_push_enabled", false);
                    defaultConfig.put("bing_api_key", "");
                    defaultConfig.put("bing_site_verification", "");
                    break;
                case "yandex":
                    defaultConfig.put("yandex_push_enabled", false);
                    defaultConfig.put("yandex_api_key", "");
                    defaultConfig.put("yandex_site_verification", "");
                    break;
                case "yahoo":
                    defaultConfig.put("yahoo_site_verification", "");
                    break;
                case "sogou":
                    defaultConfig.put("sogou_push_enabled", false);
                    defaultConfig.put("sogou_token", "");
                    defaultConfig.put("sogou_push_token", "");
                    defaultConfig.put("sogou_site_verification", "");
                    break;
                case "so":
                    defaultConfig.put("so_site_verification", "");
                    break;
                case "shenma":
                    defaultConfig.put("shenma_push_enabled", false);
                    defaultConfig.put("shenma_token", "");
                    defaultConfig.put("shenma_site_verification", "");
                    break;
                case "duckduckgo":
                    defaultConfig.put("duckduckgo_push_enabled", false);
                    defaultConfig.put("duckduckgo_site_verification", "");
                    break;
            }
        }
        
        // 设置其他默认值
        setSocialMediaDefaults(defaultConfig);
        setPwaDefaults(defaultConfig);
        setNotificationDefaults(defaultConfig);
        
        return defaultConfig;
    }

    private void updateMainConfigFromJson(SeoConfig seoConfig, Map<String, Object> jsonConfig) {
        seoConfig.setEnable(getBooleanValue(jsonConfig, "enable", true));
        seoConfig.setSiteDescription(getStringValue(jsonConfig, "site_description", ""));
        seoConfig.setSiteKeywords(getStringValue(jsonConfig, "site_keywords", ""));
        seoConfig.setSiteLogo(getStringValue(jsonConfig, "site_logo", ""));
        seoConfig.setSiteIcon(getStringValue(jsonConfig, "site_icon", ""));
        seoConfig.setSiteIcon192(getStringValue(jsonConfig, "site_icon_192", ""));
        seoConfig.setSiteIcon512(getStringValue(jsonConfig, "site_icon_512", ""));
        seoConfig.setAppleTouchIcon(getStringValue(jsonConfig, "apple_touch_icon", ""));
        seoConfig.setSiteShortName(getStringValue(jsonConfig, "site_short_name", ""));
        seoConfig.setDefaultAuthor(getStringValue(jsonConfig, "default_author", "Admin"));
        seoConfig.setCustomHeadCode(getStringValue(jsonConfig, "custom_head_code", ""));
        seoConfig.setRobotsTxt(getStringValue(jsonConfig, "robots_txt", getDefaultRobotsTxt()));
        seoConfig.setAutoGenerateMetaTags(getBooleanValue(jsonConfig, "auto_generate_meta_tags", true));
        seoConfig.setGenerateSitemap(getBooleanValue(jsonConfig, "generate_sitemap", true));
        seoConfig.setSitemapChangeFrequency(getStringValue(jsonConfig, "sitemap_change_frequency", "weekly"));
        seoConfig.setSitemapPriority(getStringValue(jsonConfig, "sitemap_priority", "0.7"));
        // 特殊处理sitemap_exclude字段：明确保存空字符串，即使前端发送空值也要更新
        String sitemapExcludeValue = getStringValue(jsonConfig, "sitemap_exclude", null);
        seoConfig.setSitemapExclude(sitemapExcludeValue);
    }

    private void updateSearchEnginePushConfigFromJson(Integer configId, Map<String, Object> jsonConfig) {
        // 删除现有配置
        seoSearchEnginePushMapper.delete(
            new LambdaQueryWrapper<SeoSearchEnginePush>()
                .eq(SeoSearchEnginePush::getSeoConfigId, configId)
        );

        // 创建搜索引擎推送配置
        List<Map<String, Object>> engineConfigs = Arrays.asList(
            createEngineConfigMap("baidu", "百度", "baidu_push_enabled", "baidu_token", "baidu_push_token"),
            createEngineConfigMap("bing", "必应", "bing_push_enabled", "bing_api_key", null),
            createEngineConfigMap("yandex", "Yandex", "yandex_push_enabled", "yandex_api_key", null),
            createEngineConfigMap("sogou", "搜狗", "sogou_push_enabled", "sogou_token", "sogou_push_token"),
            createEngineConfigMap("shenma", "神马搜索", "shenma_push_enabled", "shenma_token", null),
            createEngineConfigMap("duckduckgo", "DuckDuckGo", "duckduckgo_push_enabled", null, null)
        );

        for (Map<String, Object> engineConfig : engineConfigs) {
            String engineName = (String) engineConfig.get("name");
            String displayName = (String) engineConfig.get("displayName");
            String enabledKey = (String) engineConfig.get("enabledKey");
            String apiKeyKey = (String) engineConfig.get("apiKeyKey");
            String tokenKey = (String) engineConfig.get("tokenKey");

            boolean enabled = getBooleanValue(jsonConfig, enabledKey, false);
            if (enabled || hasApiConfig(jsonConfig, apiKeyKey, tokenKey)) {
                SeoSearchEnginePush push = new SeoSearchEnginePush();
                push.setSeoConfigId(configId);
                push.setEngineName(engineName);
                push.setEngineDisplayName(displayName);
                push.setPushEnabled(enabled);
                push.setApiKey(apiKeyKey != null ? getStringValue(jsonConfig, apiKeyKey, "") : null);
                push.setApiToken(tokenKey != null ? getStringValue(jsonConfig, tokenKey, "") : null);
                push.setPushDelaySeconds(300);
                push.setPushCount(0);
                push.setCreateTime(LocalDateTime.now());
                push.setUpdateTime(LocalDateTime.now());
                seoSearchEnginePushMapper.insert(push);
            }
        }
    }

    private void updateSiteVerificationConfigFromJson(Integer configId, Map<String, Object> jsonConfig) {
        // 删除现有配置
        seoSiteVerificationMapper.delete(
            new LambdaQueryWrapper<SeoSiteVerification>()
                .eq(SeoSiteVerification::getSeoConfigId, configId)
        );

        // 创建网站验证配置
        String[] platforms = {"baidu", "google", "bing", "yandex", "sogou", "so", "shenma", "yahoo", "duckduckgo"};
        Map<String, String> platformNames = Map.of(
            "baidu", "百度",
            "google", "Google",
            "bing", "必应",
            "yandex", "Yandex", 
            "sogou", "搜狗",
            "so", "360搜索",
            "shenma", "神马搜索",
            "yahoo", "Yahoo",
            "duckduckgo", "DuckDuckGo"
        );

        for (String platform : platforms) {
            String verificationKey = platform + "_site_verification";
            String verificationCode = getStringValue(jsonConfig, verificationKey, "");
            
            if (StringUtils.hasText(verificationCode)) {
                SeoSiteVerification verification = new SeoSiteVerification();
                verification.setSeoConfigId(configId);
                verification.setPlatform(platform);
                verification.setPlatformDisplayName(platformNames.get(platform));
                verification.setVerificationCode(verificationCode);
                verification.setVerificationMethod("meta_tag");
                verification.setIsVerified(false);
                verification.setCreateTime(LocalDateTime.now());
                verification.setUpdateTime(LocalDateTime.now());
                seoSiteVerificationMapper.insert(verification);
            }
        }
    }

    private void updateSocialMediaConfigFromJson(Integer configId, Map<String, Object> jsonConfig) {
        // 删除现有配置
        seoSocialMediaMapper.delete(
            new LambdaQueryWrapper<SeoSocialMedia>()
                .eq(SeoSocialMedia::getSeoConfigId, configId)
        );

        // 创建社交媒体配置
        SeoSocialMedia social = new SeoSocialMedia();
        social.setSeoConfigId(configId);
        social.setTwitterCard(getStringValue(jsonConfig, "twitter_card", "summary_large_image"));
        social.setTwitterSite(getStringValue(jsonConfig, "twitter_site", ""));
        social.setTwitterCreator(getStringValue(jsonConfig, "twitter_creator", ""));
        social.setOgType(getStringValue(jsonConfig, "og_type", "article"));
        social.setOgImage(getStringValue(jsonConfig, "og_image", ""));
        social.setFbAppId(getStringValue(jsonConfig, "fb_app_id", ""));
        social.setFbPageUrl(getStringValue(jsonConfig, "fb_page_url", ""));
        social.setLinkedinCompanyId(getStringValue(jsonConfig, "linkedin_company_id", ""));
        social.setLinkedinMode(getStringValue(jsonConfig, "linkedin_mode", "standard"));
        social.setPinterestVerification(getStringValue(jsonConfig, "pinterest_verification", ""));
        social.setPinterestDescription(getStringValue(jsonConfig, "pinterest_description", ""));
        social.setWechatMiniprogramPath(getStringValue(jsonConfig, "wechat_miniprogram_path", ""));
        social.setWechatMiniprogramId(getStringValue(jsonConfig, "wechat_miniprogram_id", ""));
        social.setQqMiniprogramPath(getStringValue(jsonConfig, "qq_miniprogram_path", ""));
        social.setCreateTime(LocalDateTime.now());
        social.setUpdateTime(LocalDateTime.now());
        seoSocialMediaMapper.insert(social);
    }

    private void updatePwaConfigFromJson(Integer configId, Map<String, Object> jsonConfig) {
        // 删除现有配置
        seoPwaConfigMapper.delete(
            new LambdaQueryWrapper<SeoPwaConfig>()
                .eq(SeoPwaConfig::getSeoConfigId, configId)
        );

        // 创建PWA配置
        SeoPwaConfig pwa = new SeoPwaConfig();
        pwa.setSeoConfigId(configId);
        pwa.setPwaDisplay(getStringValue(jsonConfig, "pwa_display", "standalone"));
        pwa.setPwaBackgroundColor(getStringValue(jsonConfig, "pwa_background_color", "#ffffff"));
        pwa.setPwaThemeColor(getStringValue(jsonConfig, "pwa_theme_color", "#1976d2"));
        pwa.setPwaOrientation(getStringValue(jsonConfig, "pwa_orientation", "portrait-primary"));
        pwa.setPwaScreenshotDesktop(getStringValue(jsonConfig, "pwa_screenshot_desktop", ""));
        pwa.setPwaScreenshotMobile(getStringValue(jsonConfig, "pwa_screenshot_mobile", ""));
        pwa.setAndroidAppId(getStringValue(jsonConfig, "android_app_id", ""));
        pwa.setIosAppId(getStringValue(jsonConfig, "ios_app_id", ""));
        pwa.setPreferNativeApps(getBooleanValue(jsonConfig, "prefer_native_apps", false));
        pwa.setCreateTime(LocalDateTime.now());
        pwa.setUpdateTime(LocalDateTime.now());
        seoPwaConfigMapper.insert(pwa);
    }

    private void updateNotificationConfigFromJson(Integer configId, Map<String, Object> jsonConfig) {
        // 删除现有配置
        seoNotificationConfigMapper.delete(
            new LambdaQueryWrapper<SeoNotificationConfig>()
                .eq(SeoNotificationConfig::getSeoConfigId, configId)
        );

        // 创建通知配置
        SeoNotificationConfig notification = new SeoNotificationConfig();
        notification.setSeoConfigId(configId);
        notification.setPushDelaySeconds(getIntegerValue(jsonConfig, "push_delay_seconds", 300));
        notification.setEnablePushNotification(getBooleanValue(jsonConfig, "enable_push_notification", false));
        notification.setNotifyOnlyOnFailure(getBooleanValue(jsonConfig, "notify_only_on_failure", false));
        // 移除notification_email字段，改为自动发送给文章作者邮箱
        notification.setCreateTime(LocalDateTime.now());
        notification.setUpdateTime(LocalDateTime.now());
        seoNotificationConfigMapper.insert(notification);
    }

    private SeoConfig createDefaultMainConfig() {
        SeoConfig config = new SeoConfig();
        config.setEnable(true);
        config.setSiteDescription("一个优雅的博客系统，支持多用户和多种内容格式");
        config.setSiteKeywords("博客,文章,笔记,技术,Java,Spring Boot,Vue.js");
        config.setDefaultAuthor("Admin");
        config.setRobotsTxt(getDefaultRobotsTxt());
        config.setAutoGenerateMetaTags(true);
        config.setGenerateSitemap(true);
        config.setSitemapChangeFrequency("weekly");
        config.setSitemapPriority("0.7");
        config.setSitemapExclude("/love");
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        return config;
    }

    private void initDefaultSearchEnginePushConfig(Integer configId) {
        // 这里可以初始化一些默认的搜索引擎配置，暂时留空
    }

    private void initDefaultSiteVerificationConfig(Integer configId) {
        // 这里可以初始化一些默认的网站验证配置，暂时留空
    }

    private void initDefaultSocialMediaConfig(Integer configId) {
        SeoSocialMedia social = new SeoSocialMedia();
        social.setSeoConfigId(configId);
        social.setTwitterCard("summary_large_image");
        social.setOgType("article");
        social.setLinkedinMode("standard");
        social.setCreateTime(LocalDateTime.now());
        social.setUpdateTime(LocalDateTime.now());
        seoSocialMediaMapper.insert(social);
    }

    private void initDefaultPwaConfig(Integer configId) {
        SeoPwaConfig pwa = new SeoPwaConfig();
        pwa.setSeoConfigId(configId);
        pwa.setPwaDisplay("standalone");
        pwa.setPwaBackgroundColor("#ffffff");
        pwa.setPwaThemeColor("#1976d2");
        pwa.setPwaOrientation("portrait-primary");
        pwa.setPreferNativeApps(false);
        pwa.setCreateTime(LocalDateTime.now());
        pwa.setUpdateTime(LocalDateTime.now());
        seoPwaConfigMapper.insert(pwa);
    }

    private void initDefaultNotificationConfig(Integer configId) {
        SeoNotificationConfig notification = new SeoNotificationConfig();
        notification.setSeoConfigId(configId);
        notification.setPushDelaySeconds(300);
        notification.setEnablePushNotification(false);
        notification.setNotifyOnlyOnFailure(false);
        // 移除notification_email字段，改为自动发送给文章作者邮箱
        notification.setCreateTime(LocalDateTime.now());
        notification.setUpdateTime(LocalDateTime.now());
        seoNotificationConfigMapper.insert(notification);
    }

    // 工具方法
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        // 如果值存在（包括空字符串），就使用该值；只有在值为null时才使用默认值
        return value != null ? value.toString() : defaultValue;
    }

    private Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Map<String, Object> createEngineConfigMap(String name, String displayName, String enabledKey, String apiKeyKey, String tokenKey) {
        Map<String, Object> config = new HashMap<>();
        config.put("name", name);
        config.put("displayName", displayName);
        config.put("enabledKey", enabledKey);
        config.put("apiKeyKey", apiKeyKey);
        config.put("tokenKey", tokenKey);
        return config;
    }

    private boolean hasApiConfig(Map<String, Object> jsonConfig, String apiKeyKey, String tokenKey) {
        boolean hasApiKey = apiKeyKey != null && StringUtils.hasText(getStringValue(jsonConfig, apiKeyKey, ""));
        boolean hasToken = tokenKey != null && StringUtils.hasText(getStringValue(jsonConfig, tokenKey, ""));
        return hasApiKey || hasToken;
    }
}
