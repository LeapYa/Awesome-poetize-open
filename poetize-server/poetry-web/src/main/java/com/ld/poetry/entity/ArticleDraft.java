package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("article_draft")
public class ArticleDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("owner_user_id")
    private Integer ownerUserId;

    @TableField("draft_type")
    private String draftType;

    @TableField("article_id")
    private Integer articleId;

    @TableField("status")
    private String status;

    @TableField("title_cache")
    private String titleCache;

    @TableField("crdt_snapshot_base64")
    private String crdtSnapshotBase64;

    @TableField("last_editor_id")
    private Integer lastEditorId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField("deleted")
    @TableLogic
    private Boolean deleted;
}
