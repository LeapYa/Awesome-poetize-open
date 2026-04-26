package com.ld.poetry.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleDraftDetailVO {
    private String id;
    private String draftType;
    private Integer articleId;
    private String status;
    private String titleCache;
    private String crdtSnapshotBase64;
    private Integer ownerUserId;
    private String ownerUsername;
    private Integer lastEditorId;
    private String lastEditorUsername;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String sourceArticleTitle;
    private ArticleVO sourceArticle;
    private List<ArticleDraftCollaboratorVO> collaborators;
}
