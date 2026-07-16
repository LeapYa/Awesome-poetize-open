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
 * <p>
 * 资源信息
 * </p>
 *
 * @author sara
 * @since 2022-03-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("resource")
public class Resource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 稳定资源公开ID，物理存储迁移时保持不变
     */
    @TableField("public_id")
    private String publicId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 资源类型
     */
    @TableField("type")
    private String type;

    /**
     * 是否启用[0:否，1:是]
     */
    @TableField("status")
    private Boolean status;

    /**
     * 存储平台
     */
    @TableField("store_type")
    private String storeType;

    /**
     * 存储平台对象键，用于校验和删除远端文件
     */
    @TableField("storage_key")
    private String storageKey;

    /**
     * 当前活动物理副本ID
     */
    @TableField("active_location_id")
    private Long activeLocationId;

    /**
     * 活动副本乐观锁版本
     */
    @TableField("location_version")
    private Integer locationVersion;

    /**
     * 资源路径
     */
    @TableField("path")
    private String path;

    /**
     * 资源内容的大小，单位：字节
     */
    @TableField("size")
    private Integer size;

    /**
     * 资源的 MIME 类型
     */
    @TableField("mime_type")
    private String mimeType;

    /**
     * 内容可用状态；待替换时禁止读取、迁移和活动副本切换
     */
    @TableField("content_state")
    private String contentState;

    /**
     * 资源内容哈希（SHA-256）
     */
    @TableField("resource_hash")
    private String resourceHash;

    /**
     * 内容哈希来源，例如 UPLOAD、LEGACY_ADOPTION、MIGRATION_VERIFY
     */
    @TableField("hash_source")
    private String hashSource;

    /**
     * 内容哈希最近严格校验时间
     */
    @TableField("hash_verified_at")
    private LocalDateTime hashVerifiedAt;

    /**
     * 文件名称
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 图片宽度（像素），非图片资源为 null
     */
    @TableField("width")
    private Integer width;

    /**
     * 图片高度（像素），非图片资源为 null
     */
    @TableField("height")
    private Integer height;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;


}
