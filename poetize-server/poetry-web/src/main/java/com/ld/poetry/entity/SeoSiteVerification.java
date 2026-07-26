package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 网站验证配置表
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("seo_site_verification")
public class SeoSiteVerification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * SEO配置ID
     */
    @TableField("seo_config_id")
    private Integer seoConfigId;

    /**
     * 平台名称
     */
    @TableField("platform")
    private String platform;

    /**
     * 平台显示名称
     */
    @TableField("platform_display_name")
    private String platformDisplayName;

    /**
     * 验证代码（支持完整HTML标签，如 &lt;meta name="baidu-site-verification" content="xxx" /&gt;）
     */
    @TableField("verification_code")
    private String verificationCode;

    /**
     * 验证方式
     */
    @TableField("verification_method")
    private String verificationMethod;

    /**
     * 是否已验证
     */
    @TableField("is_verified")
    private Boolean isVerified;

    /**
     * 验证时间
     */
    @TableField("verified_time")
    private LocalDateTime verifiedTime;

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

