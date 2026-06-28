package com.ld.poetry.service.ai.tools.webfetch;

import lombok.Data;

/**
 * 网页元数据 DTO — 由 {@link MetadataExtractor} 从 HTML 中提取的结构化信号。
 * <p>
 * SEO 友好的 SPA 站点常在 {@code <head>} 嵌入这些元数据；当 Readability4J 提取失败时，
 * 这些字段是模型能拿到的唯一信息源。
 */
@Data
public class PageMetadata {

    /** {@code <title>} 标签内容 */
    private String title;

    /** {@code <meta name="description">} */
    private String description;

    /** {@code <meta name="keywords">} */
    private String keywords;

    /** {@code og:title} */
    private String ogTitle;

    /** {@code og:description} */
    private String ogDescription;

    /** {@code og:image} */
    private String ogImage;

    /** {@code og:url}（规范 URL） */
    private String ogUrl;

    /** {@code article:published_time}（ISO 8601） */
    private String publishedTime;

    /** {@code article:author} */
    private String author;

    /** JSON-LD Article/BlogPosting 的 {@code articleBody} 字段（可能含完整正文） */
    private String jsonLdArticleBody;

    /** {@code window.__NUXT__} / {@code window.__INITIAL_STATE__} 等预取数据（截取前 32K） */
    private String preloadedStateJson;
}
