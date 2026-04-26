package com.ld.poetry.vo;

import lombok.Data;

@Data
public class ArticleDraftCollaboratorVO {
    private Integer userId;
    private String username;
    private String avatar;
    private Integer userType;
}
