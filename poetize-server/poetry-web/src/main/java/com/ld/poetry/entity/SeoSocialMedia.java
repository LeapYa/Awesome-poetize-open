package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 社交媒体配置表
 * </p>
 *
 * @author sara
 * @since 2024-12-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("seo_social_media")
public class SeoSocialMedia implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * SEO配置ID
     */
    @TableField("seo_config_id")
    private Integer seoConfigId;

    /**
     * Twitter卡片类型
     */
    @TableField("twitter_card")
    private String twitterCard;

    /**
     * Twitter站点账号
     */
    @TableField("twitter_site")
    private String twitterSite;

    /**
     * Twitter创建者账号
     */
    @TableField("twitter_creator")
    private String twitterCreator;

    /**
     * Open Graph类型
     */
    @TableField("og_type")
    private String ogType;


    /**
     * Open Graph图片
     */
    @TableField("og_image")
    private String ogImage;

    /**
     * Facebook应用ID
     */
    @TableField("fb_app_id")
    private String fbAppId;

    /**
     * Facebook页面URL
     */
    @TableField("fb_page_url")
    private String fbPageUrl;

    /**
     * LinkedIn公司ID
     */
    @TableField("linkedin_company_id")
    private String linkedinCompanyId;

    /**
     * LinkedIn模式
     */
    @TableField("linkedin_mode")
    private String linkedinMode;

    /**
     * Pinterest验证码
     */
    @TableField("pinterest_verification")
    private String pinterestVerification;

    /**
     * Pinterest描述
     */
    @TableField("pinterest_description")
    private String pinterestDescription;

    /**
     * 微信小程序路径
     */
    @TableField("wechat_miniprogram_path")
    private String wechatMiniprogramPath;

    /**
     * 微信小程序ID
     */
    @TableField("wechat_miniprogram_id")
    private String wechatMiniprogramId;

    /**
     * QQ小程序路径
     */
    @TableField("qq_miniprogram_path")
    private String qqMiniprogramPath;

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

