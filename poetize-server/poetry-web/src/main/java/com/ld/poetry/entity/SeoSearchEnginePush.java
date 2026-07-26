package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 搜索引擎推送配置表
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("seo_search_engine_push")
public class SeoSearchEnginePush implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * SEO配置ID
     */
    @TableField("seo_config_id")
    private Integer seoConfigId;

    /**
     * 搜索引擎名称
     */
    @TableField("engine_name")
    private String engineName;

    /**
     * 搜索引擎显示名称
     */
    @TableField("engine_display_name")
    private String engineDisplayName;

    /**
     * 是否启用推送
     */
    @TableField("push_enabled")
    private Boolean pushEnabled;

    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * API令牌
     */
    @TableField("api_token")
    private String apiToken;

    /**
     * 推送URL
     */
    @TableField("push_url")
    private String pushUrl;

    /**
     * 推送延迟秒数
     */
    @TableField("push_delay_seconds")
    private Integer pushDelaySeconds;

    /**
     * 最后推送时间
     */
    @TableField("last_push_time")
    private LocalDateTime lastPushTime;

    /**
     * 推送次数
     */
    @TableField("push_count")
    private Integer pushCount;

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

