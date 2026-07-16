package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("resource_migration_item")
public class ResourceMigrationItem implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("resource_id")
    private Integer resourceId;

    @TableField("source_location_id")
    private Long sourceLocationId;

    @TableField("source_location_version")
    private Integer sourceLocationVersion;

    @TableField("target_location_id")
    private Long targetLocationId;

    @TableField("source_path")
    private String sourcePath;

    @TableField("source_store_type")
    private String sourceStoreType;

    @TableField("source_storage_key")
    private String sourceStorageKey;

    @TableField("source_expected_hash")
    private String sourceExpectedHash;

    @TableField("source_hash_source")
    private String sourceHashSource;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("source_size")
    private Long sourceSize;

    @TableField("source_mime_type")
    private String sourceMimeType;

    @TableField("target_path")
    private String targetPath;

    @TableField("target_store_type")
    private String targetStoreType;

    @TableField("target_storage_key")
    private String targetStorageKey;

    @TableField("target_hash")
    private String targetHash;

    @TableField("snapshot_size")
    private Long snapshotSize;

    @TableField("hash_baselined")
    private Boolean hashBaselined;

    @TableField("target_created")
    private Boolean targetCreated;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("source_deleted")
    private Boolean sourceDeleted;

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