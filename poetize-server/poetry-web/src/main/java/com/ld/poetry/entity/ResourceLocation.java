package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("resource_location")
public class ResourceLocation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("resource_id")
    private Integer resourceId;

    @TableField("store_type")
    private String storeType;

    @TableField("storage_key")
    private String storageKey;

    @TableField("access_path")
    private String accessPath;

    @TableField("content_hash")
    private String contentHash;

    @TableField("size")
    private Long size;

    @TableField("mime_type")
    private String mimeType;

    @TableField("status")
    private String status;

    @TableField("verified_at")
    private LocalDateTime verifiedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}