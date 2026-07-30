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
     * 授权端点（自定义平台使用）
     */
    @TableField("authorize_url")
    private String authorizeUrl;

    /**
     * 令牌端点（自定义平台使用）
     */
    @TableField("token_url")
    private String tokenUrl;

    /**
     * 用户信息端点（自定义平台使用）
     */
    @TableField("user_info_url")
    private String userInfoUrl;

    /**
     * 用户唯一标识字段路径，默认sub（自定义平台使用）
     */
    @TableField("uid_field")
    private String uidField;

    /**
     * 用户名字段路径，默认name（自定义平台使用）
     */
    @TableField("username_field")
    private String usernameField;

    /**
     * 头像字段路径，默认picture（自定义平台使用）
     */
    @TableField("avatar_field")
    private String avatarField;

    /**
     * 邮箱字段路径，默认email（自定义平台使用）
     */
    @TableField("email_field")
    private String emailField;

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
