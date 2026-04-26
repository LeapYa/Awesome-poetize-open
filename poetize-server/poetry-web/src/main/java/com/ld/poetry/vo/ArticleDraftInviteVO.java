package com.ld.poetry.vo;

import lombok.Data;

@Data
public class ArticleDraftInviteVO {
    private String draftId;
    private String inviteToken;
    private Long expireSeconds;
}
