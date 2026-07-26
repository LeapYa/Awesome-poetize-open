package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 通知配置表
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("seo_notification_config")
public class SeoNotificationConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * SEO配置ID
     */
    @TableField("seo_config_id")
    private Integer seoConfigId;

    /**
     * 推送延迟秒数
     */
    @TableField("push_delay_seconds")
    private Integer pushDelaySeconds;

    /**
     * 启用推送通知
     */
    @TableField("enable_push_notification")
    private Boolean enablePushNotification;

    /**
     * 仅失败时通知
     */
    @TableField("notify_only_on_failure")
    private Boolean notifyOnlyOnFailure;

    /**
     * 通知邮箱
     */
    @TableField("notification_email")
    private String notificationEmail;

    /**
     * 通知Webhook
     */
    @TableField("notification_webhook")
    private String notificationWebhook;

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

