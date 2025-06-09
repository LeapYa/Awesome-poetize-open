package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 文章翻译内容表
 * </p>
 *
 * @author coding-poet
 * @since 2024-05-10
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("article_translation")
public class ArticleTranslation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 文章ID
     */
    @TableField("article_id")
    private Integer articleId;

    /**
     * 语言代码
     */
    @TableField("language")
    private String language;

    /**
     * 翻译后的标题
     */
    @TableField("title")
    private String title;

    /**
     * 翻译后的内容
     */
    @TableField("content")
    private String content;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
} 