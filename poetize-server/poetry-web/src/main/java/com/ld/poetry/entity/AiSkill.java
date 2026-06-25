package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Skill 管理实体类
 * <p>
 * 存储完整的 SKILL.md 文档与解析后的元数据，支持多场景 Skill 安装、启停、活跃切换。
 * 替代原先硬编码在 {@code sys_ai_config.extraConfig.commentSkill} 的单一评论 Skill。
 *
 * @author LeapYa
 * @since 2026-06-24
 */
@Data
@TableName("sys_ai_skill")
public class AiSkill implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * Skill 唯一标识 (frontmatter name)
     */
    private String skillKey;

    /**
     * 显示名称
     */
    private String skillName;

    /**
     * frontmatter description
     */
    private String description;

    /**
     * 版本号
     */
    private String version;

    /**
     * 作者
     */
    private String author;

    /**
     * 适用场景: comment/chat/article/universal
     */
    private String scene;

    /**
     * 完整 SKILL.md 原文
     */
    private String skillContent;

    /**
     * 解析后的正文
     */
    private String skillBody;

    /**
     * 支持的占位符 JSON
     */
    private String placeholders;

    /**
     * 是否启用 (0:否 1:是)
     */
    private Boolean enabled;

    /**
     * 内置 Skill 不可删 (0:否 1:是)
     */
    private Boolean isBuiltin;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ========== 场景常量 ==========

    /**
     * 评论场景
     */
    public static final String SCENE_COMMENT = "comment";

    /**
     * 聊天场景
     */
    public static final String SCENE_CHAT = "chat";

    /**
     * 文章场景
     */
    public static final String SCENE_ARTICLE = "article";

    /**
     * 通用场景
     */
    public static final String SCENE_UNIVERSAL = "universal";
}
