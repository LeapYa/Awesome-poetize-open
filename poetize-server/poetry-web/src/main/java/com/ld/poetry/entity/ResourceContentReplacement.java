package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("resource_content_replacement")
public class ResourceContentReplacement implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("operation_id")
    private String operationId;

    @TableField("resource_id")
    private Integer resourceId;

    @TableField("active_location_id")
    private Long activeLocationId;

    @TableField("expected_path")
    private String expectedPath;

    @TableField("original_location_version")
    private Integer originalLocationVersion;

    @TableField("claimed_location_version")
    private Integer claimedLocationVersion;

    @TableField("original_resource_hash")
    private String originalResourceHash;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("original_hash_source")
    private String originalHashSource;

    @TableField("original_hash_verified_at")
    private LocalDateTime originalHashVerifiedAt;

    @TableField("source_location_store_type")
    private String sourceLocationStoreType;

    @TableField("source_location_storage_key")
    private String sourceLocationStorageKey;

    @TableField("source_location_access_path")
    private String sourceLocationAccessPath;

    @TableField("source_location_hash")
    private String sourceLocationHash;

    @TableField("source_location_status")
    private String sourceLocationStatus;

    @TableField("new_hash")
    private String newHash;

    @TableField("new_size")
    private Integer newSize;

    @TableField("new_original_name")
    private String newOriginalName;

    @TableField("new_mime_type")
    private String newMimeType;

    @TableField("new_width")
    private Integer newWidth;

    @TableField("new_height")
    private Integer newHeight;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("artifacts_cleaned_at")
    private LocalDateTime artifactsCleanedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}