package com.ld.poetry.service.ai.tools.webfetch;

import lombok.Data;

import java.util.List;

/**
 * 网页抓取结果缓存条目。
 * <p>
 * 缓存完整的转换后 Markdown 字符串（含元信息），offset 分页时直接切片返回，
 * 避免重复 HTTP 抓取触发目标站点限流。
 */
@Data
public class CachedPage {

    /** 转换后的完整 Markdown 正文 */
    private String markdown;

    /** 页面标题（用于返回元信息） */
    private String title;

    /** 作者署名（Readability 提取的 byline） */
    private String byline;

    /** 最终 URL（经重定向后的最终落地 URL） */
    private String finalUrl;

    /** Content-Type */
    private String contentType;

    /** 完整正文字符数 */
    private int totalLength;

    /** 原始响应是否被 5MB 截断 */
    private boolean originalResponseTruncated;

    /** 提取策略：READABILITY / JSONLD_ONLY / JINA_READER / RAW_FALLBACK / METADATA_ONLY */
    private String strategy;

    /** 质量警告列表 */
    private List<String> qualityWarnings;

    /** 抓取时间戳（epoch millis） */
    private long fetchedAt;
}
