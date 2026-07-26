package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * PWA配置表
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("seo_pwa_config")
public class SeoPwaConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * SEO配置ID
     */
    @TableField("seo_config_id")
    private Integer seoConfigId;

    /**
     * PWA显示模式
     */
    @TableField("pwa_display")
    private String pwaDisplay;

    /**
     * PWA背景颜色
     */
    @TableField("pwa_background_color")
    private String pwaBackgroundColor;

    /**
     * PWA主题颜色
     */
    @TableField("pwa_theme_color")
    private String pwaThemeColor;

    /**
     * PWA屏幕方向
     */
    @TableField("pwa_orientation")
    private String pwaOrientation;

    /**
     * PWA桌面截图
     */
    @TableField("pwa_screenshot_desktop")
    private String pwaScreenshotDesktop;

    /**
     * PWA移动端截图
     */
    @TableField("pwa_screenshot_mobile")
    private String pwaScreenshotMobile;

    /**
     * Android应用ID
     */
    @TableField("android_app_id")
    private String androidAppId;

    /**
     * iOS应用ID
     */
    @TableField("ios_app_id")
    private String iosAppId;

    /**
     * 是否优先使用原生应用
     */
    @TableField("prefer_native_apps")
    private Boolean preferNativeApps;

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
}

