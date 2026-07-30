package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 第三方OAuth登录配置表
 * </p>
 *
 * @author LeapYa
 * @since 2025-07-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("third_party_oauth_config")
public class ThirdPartyOauthConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 平台类型（github, google, twitter, yandex, gitee等）
     */
    @TableField("platform_type")
    private String platformType;

    /**
     * 平台名称
     */
    @TableField("platform_name")
    private String platformName;

    /**
     * 客户端ID
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 客户端密钥
     */
    @TableField("client_secret")
    private String clientSecret;

    /**
     * 客户端Key（Twitter使用）
     */
    @TableField("client_key")
    private String clientKey;

    /**
     * 重定向URI
     */
    @TableField("redirect_uri")
    private String redirectUri;

    /**
     * 授权范围
     */
    @TableField("scope")
    private String scope;

    /**
     * 是否启用该平台
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 全局是否启用第三方登录
     */
    @TableField("global_enabled")
    private Boolean globalEnabled;

    /**
     * 排序顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除（0-未删除，1-已删除）
     */
    @TableField("deleted")
    @TableLogic
    private Boolean deleted;

    /**
     * 建议回调地址（根据站点地址自动生成，非数据库字段，仅供后台展示与复制）
     */
    @TableField(exist = false)
    private String suggestedRedirectUri;
}
