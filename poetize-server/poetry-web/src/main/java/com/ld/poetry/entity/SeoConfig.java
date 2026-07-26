package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * SEO主配置表
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("seo_config")
public class SeoConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * SEO功能总开关
     */
    @TableField("enable")
    private Boolean enable;

    /**
     * 网站描述
     */
    @TableField("site_description")
    private String siteDescription;

    /**
     * 网站关键词
     */
    @TableField("site_keywords")
    private String siteKeywords;

    /**
     * 网站Logo
     */
    @TableField("site_logo")
    private String siteLogo;

    /**
     * 网站图标
     */
    @TableField("site_icon")
    private String siteIcon;

    /**
     * 网站图标192x192
     */
    @TableField("site_icon_192")
    private String siteIcon192;

    /**
     * 网站图标512x512
     */
    @TableField("site_icon_512")
    private String siteIcon512;

    /**
     * Apple Touch图标
     */
    @TableField("apple_touch_icon")
    private String appleTouchIcon;

    /**
     * 网站短名称
     */
    @TableField("site_short_name")
    private String siteShortName;

    /**
     * 默认作者
     */
    @TableField("default_author")
    private String defaultAuthor;

    /**
     * 自定义头部代码
     */
    @TableField("custom_head_code")
    private String customHeadCode;

    /**
     * robots.txt内容
     */
    @TableField("robots_txt")
    private String robotsTxt;

    /**
     * 自动生成元标签
     */
    @TableField("auto_generate_meta_tags")
    private Boolean autoGenerateMetaTags;

    /**
     * 生成站点地图
     */
    @TableField("generate_sitemap")
    private Boolean generateSitemap;


    /**
     * 站点地图更新频率
     */
    @TableField("sitemap_change_frequency")
    private String sitemapChangeFrequency;

    /**
     * 站点地图优先级
     */
    @TableField("sitemap_priority")
    private String sitemapPriority;

    /**
     * 站点地图排除路径
     */
    @TableField("sitemap_exclude")
    private String sitemapExclude;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    @TableField("update_by")
    private String updateBy;

    // 关联数据（不存储在数据库中）
    @TableField(exist = false)
    private List<SeoSearchEnginePush> searchEnginePushList;

    @TableField(exist = false)
    private List<SeoSiteVerification> siteVerificationList;

    @TableField(exist = false)
    private SeoSocialMedia socialMedia;

    @TableField(exist = false)
    private SeoPwaConfig pwaConfig;

    @TableField(exist = false)
    private SeoNotificationConfig notificationConfig;
}

