package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("resource_content_replacement_target")
public class ResourceContentReplacementTarget implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("replacement_id")
    private Long replacementId;

    @TableField("target_path")
    private String targetPath;

    @TableField("temp_path")
    private String tempPath;

    @TableField("backup_path")
    private String backupPath;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("new_hash")
    private String newHash;

    @TableField("observed_hash")
    private String observedHash;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}