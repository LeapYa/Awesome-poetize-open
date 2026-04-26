package com.ld.poetry.vo;

import lombok.Data;

@Data
public class ArticleDraftSnapshotRequest {
    private String titleCache;
    private String snapshotBase64;
}
