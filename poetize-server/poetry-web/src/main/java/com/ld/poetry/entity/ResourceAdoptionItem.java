package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("resource_adoption_item")
public class ResourceAdoptionItem implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("resource_id")
    private Integer resourceId;

    @TableField("reference_count")
    private Integer referenceCount;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("snapshot_size")
    private Long snapshotSize;

    @TableField("hash_baselined")
    private Boolean hashBaselined;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}