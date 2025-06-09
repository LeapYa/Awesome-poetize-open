package com.ld.poetry.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class ArticleVO {

    private Integer id;

    private Integer userId;

    //查询为空时，随机选择
    private String articleCover;

    @NotBlank(message = "文章标题不能为空")
    private String articleTitle;

    @NotBlank(message = "文章内容不能为空")
    private String articleContent;

    private Integer viewCount;

    private Integer likeCount;

    private Boolean commentStatus;

    private Boolean recommendStatus;

    private String videoUrl;

    private String password;

    private String tips;

    private Boolean viewStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private String updateBy;

    @NotNull(message = "文章分类不能为空")
    private Integer sortId;

    @NotNull(message = "文章标签不能为空")
    private Integer labelId;

    // 需要查询封装
    private Integer commentCount;
    private String username;
    private Sort sort;
    private Label label;
    private Boolean hasVideo = false;
    
    // API接口兼容字段
    private String title;      // 对应 articleTitle
    private String content;    // 对应 articleContent
    private Integer classify;  // 对应 sortId
    private String cover;      // 对应 articleCover
    private String summary;    // 对应 tips
    
    // 搜索引擎推送控制
    private Boolean submitToSearchEngine; // 是否推送至搜索引擎
    
    // 分类名称和标签名称（用于API创建文章时自动创建分类和标签）
    private String sortName;    // 分类名称
    private String labelName;   // 标签名称
}
