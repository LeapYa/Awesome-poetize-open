package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("resource_migration_task")
public class ResourceMigrationTask implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("source_store_type")
    private String sourceStoreType;

    @TableField("target_store_type")
    private String targetStoreType;

    @TableField("scope_type")
    private String scopeType;

    @TableField("resource_type")
    private String resourceType;

    @TableField("keep_source")
    private Boolean keepSource;

    @TableField("status")
    private String status;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("processed_count")
    private Integer processedCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("skipped_count")
    private Integer skippedCount;

    @TableField("failed_count")
    private Integer failedCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}