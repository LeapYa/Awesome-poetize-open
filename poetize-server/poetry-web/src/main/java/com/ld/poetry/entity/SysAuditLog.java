package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台可审计日志。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("log_type")
    private String logType;

    @TableField("action")
    private String action;

    @TableField("success")
    private Boolean success;

    @TableField("masked_account")
    private String maskedAccount;

    @TableField("user_id")
    private Integer userId;

    @TableField("username")
    private String username;

    @TableField("ip")
    private String ip;

    @TableField("location")
    private String location;

    @TableField("user_agent")
    private String userAgent;

    @TableField("request_uri")
    private String requestUri;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("summary")
    private String summary;

    @TableField("detail")
    private String detail;

    @TableField("create_time")
    private LocalDateTime createTime;
}
