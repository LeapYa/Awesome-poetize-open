package com.ld.poetry.service.ai.tools.webfetch;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页元数据预提取器。
 * <p>
 * 在 Readability4J 正文提取之前/失败时，从 {@code <head>} 提取 SEO 友好的结构化信号：
 * <ul>
 *   <li>JSON-LD（schema.org Article/BlogPosting.articleBody）— 可能含完整正文</li>
 *   <li>OpenGraph（og:title/og:description/og:image/article:published_time/article:author）</li>
 *   <li>预取数据（window.__NUXT__、window.__INITIAL_STATE__）</li>
 *   <li>基础 meta（title、description、keywords）</li>
 * </ul>
 * 对纯 CSR SPA 站点也能提取出可用的元数据/正文片段。
 */
public final class MetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(MetadataExtractor.class);

    /** JsonMapper 是线程安全的，可作静态单例 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 预取数据上限（避免无界占用内存） */
    private static final int PRELOADED_STATE_MAX_LENGTH = 32_000;

    /** 匹配 window.__NUXT__= / window.__INITIAL_STATE__= / window.__INITIAL_DATA__= */
    private static final Pattern PRELOADED_STATE_PATTERN = Pattern.compile(
            "window\\.__NUXT__\\s*=|window\\.__INITIAL_STATE__\\s*=|window\\.__INITIAL_DATA__\\s*=",
            Pattern.CASE_INSENSITIVE);

    private MetadataExtractor() {
    }

    public static PageMetadata extract(Document document) {
        PageMetadata meta = new PageMetadata();
        if (document == null) {
            return meta;
        }

        try {
            // 1. 基础 meta
            Element titleEl = document.selectFirst("title");
            if (titleEl != null) {
                meta.setTitle(titleEl.text().trim());
            }
            meta.setDescription(metaContent(document, "description"));
            meta.setKeywords(metaContent(document, "keywords"));

            // 2. OpenGraph
            meta.setOgTitle(metaProperty(document, "og:title"));
            meta.setOgDescription(metaProperty(document, "og:description"));
            meta.setOgImage(metaProperty(document, "og:image"));
            meta.setOgUrl(metaProperty(document, "og:url"));
            meta.setPublishedTime(metaProperty(document, "article:published_time"));
            meta.setAuthor(metaProperty(document, "article:author"));

            // 3. JSON-LD
            extractJsonLd(document, meta);

            // 4. 预取数据
            meta.setPreloadedStateJson(extractPreloadedState(document));
        } catch (Exception e) {
            log.warn("元数据提取异常: {}", e.getMessage());
        }
        return meta;
    }

    private static String metaContent(Document doc, String name) {
        Element el = doc.selectFirst("meta[name=" + name + "]");
        return (el != null && el.hasAttr("content")) ? el.attr("content").trim() : null;
    }

    private static String metaProperty(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        if (el == null || !el.hasAttr("content") || el.attr("content").isEmpty()) {
            // fallback: 某些站点用 name 而非 property
            el = doc.selectFirst("meta[name=" + property + "]");
        }
        return (el != null && el.hasAttr("content")) ? el.attr("content").trim() : null;
    }

    /**
     * 解析所有 {@code <script type="application/ld+json">}，
     * 找到 {@code @type=Article/BlogPosting} 类型后提取 articleBody 等字段。
     */
    private static void extractJsonLd(Document doc, PageMetadata meta) {
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode node = JSON.readTree(json);
                JsonNode typeNode = node.path("@type");
                String typeStr;
                if (typeNode.isTextual()) {
                    typeStr = typeNode.asString();
                } else if (typeNode.isArray() && typeNode.size() > 0) {
                    typeStr = typeNode.get(0).asText();
                } else {
                    typeStr = "";
                }

                if (isArticleType(typeStr)) {
                    // 优先填充 articleBody（最值钱字段）
                    JsonNode bodyNode = node.path("articleBody");
                    if (bodyNode.isTextual()) {
                        String body = bodyNode.asString();
                        if (!body.isEmpty()) {
                            meta.setJsonLdArticleBody(body);
                        }
                    }

                    // 补充其他字段（不覆盖已存在的）
                    if (meta.getTitle() == null) {
                        JsonNode headline = node.path("headline");
                        if (headline.isTextual()) {
                            meta.setTitle(headline.asString());
                        }
                    }
                    if (meta.getPublishedTime() == null) {
                        JsonNode dp = node.path("datePublished");
                        if (dp.isTextual()) {
                            meta.setPublishedTime(dp.asString());
                        }
                    }
                    if (meta.getAuthor() == null) {
                        JsonNode authorNode = node.path("author");
                        if (authorNode.isObject()) {
                            JsonNode name = authorNode.path("name");
                            if (name.isTextual()) {
                                meta.setAuthor(name.asString());
                            }
                        } else if (authorNode.isTextual()) {
                            meta.setAuthor(authorNode.asString());
                        }
                    }
                    if (meta.getOgImage() == null) {
                        JsonNode imageNode = node.path("image");
                        if (imageNode.isTextual()) {
                            meta.setOgImage(imageNode.asString());
                        } else if (imageNode.isObject()) {
                            JsonNode url = imageNode.path("url");
                            if (url.isTextual()) {
                                meta.setOgImage(url.asString());
                            }
                        }
                    }
                    // 找到 Article 类型即停止
                    break;
                }
            } catch (Exception e) {
                log.debug("JSON-LD 解析失败: {}", e.getMessage());
            }
        }
    }

    private static boolean isArticleType(String type) {
        if (type == null) {
            return false;
        }
        String lower = type.toLowerCase();
        return lower.contains("article")
                || lower.contains("blogposting")
                || lower.contains("newsarticle")
                || lower.contains("techarticle")
                || lower.contains("scholarlyarticle");
    }

    /**
     * 在 inline script 中查找预取数据（{@code window.__NUXT__=} 等），
     * 截取匹配位置后续内容到 32K 上限。
     */
    private static String extractPreloadedState(Document doc) {
        for (Element script : doc.select("script:not([src])")) {
            String data = script.data();
            if (data == null || data.isEmpty()) {
                continue;
            }
            Matcher m = PRELOADED_STATE_PATTERN.matcher(data);
            if (m.find()) {
                int start = m.end();
                int end = Math.min(data.length(), start + PRELOADED_STATE_MAX_LENGTH);
                return data.substring(start, end).trim();
            }
        }
        return null;
    }
}
