package com.ld.poetry.service.prerender;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PrerenderPageData {

    private String title;

    private String articleTitle;

    private Map<String, Object> meta;

    private String content;

    private String lang;

    private String pageType;
}
