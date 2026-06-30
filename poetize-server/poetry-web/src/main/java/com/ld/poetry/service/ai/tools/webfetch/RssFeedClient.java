package com.ld.poetry.service.ai.tools.webfetch;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RSS/Atom Feed 客户端 — Fetcher Chain 第 3 层（博客正文抓取）。
 * <p>
 * 当 Readability 提取失败（如纯 CSR SPA 站点）时，尝试从站点的 RSS/Atom Feed
 * 中匹配目标文章 URL 并提取正文。覆盖几乎所有博客站点（WordPress/Ghost/Hexo/Hugo/Typecho 等）。
 * <p>
 * 抓取策略：
 * <ol>
 *   <li>从原始 HTML 的 {@code <link rel="alternate" type="application/rss+xml">} 自动发现 Feed URL</li>
 *   <li>若未发现，尝试常见路径：/feed、/rss、/atom.xml、/index.xml、/feed.xml、/rss.xml、/posts/feed</li>
 *   <li>解析 Feed，按 URL 匹配目标文章条目</li>
 *   <li>返回条目的 description/content（HTML 转 Markdown 由调用方处理）</li>
 * </ol>
 * <p>
 * 完全本地处理，零外部 SaaS 依赖。RSS 解析是纯字符串操作，资源开销极低。
 */
public class RssFeedClient {

    private static final Logger log = LoggerFactory.getLogger(RssFeedClient.class);

    /** RSS Feed 响应体上限（防恶意大响应） */
    private static final int MAX_FEED_BYTES = 2 * 1024 * 1024; // 2MB

    /** 候选 Feed 路径（按优先级，根路径相对） */
    private static final String[] COMMON_FEED_PATHS = {
            "/feed",
            "/rss",
            "/atom.xml",
            "/index.xml",
            "/feed.xml",
            "/rss.xml",
            "/posts/feed",
            "/blog/feed"
    };

    /** 最小正文长度（< 200 视为无效） */
    private static final int MIN_CONTENT_LENGTH = 200;

    private final OkHttpClient httpClient;

    public RssFeedClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 尝试从目标 URL 所属站点的 RSS/Atom Feed 中匹配文章正文。
     *
     * @param targetUrl       用户提供的原始文章 URL
     * @param originalDocument 已抓取的原始 HTML Document（用于自动发现 Feed 链接）
     * @return {@code Optional.of(htmlContent)} 当匹配到文章且正文长度 &gt; 200；否则 {@code Optional.empty()}
     */
    public Optional<String> fetchArticleContent(String targetUrl, Document originalDocument) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(targetUrl);
            String origin = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

            // 第 1 步：从原始 HTML 自动发现 Feed 链接
            List<String> feedUrls = discoverFeedUrls(originalDocument, origin);

            // 第 2 步：补充常见路径
            for (String path : COMMON_FEED_PATHS) {
                String candidate = origin + path;
                if (!feedUrls.contains(candidate)) {
                    feedUrls.add(candidate);
                }
            }

            log.info("RSS 候选 Feed 数量: {}", feedUrls.size());

            // 第 3 步：依次尝试每个 Feed URL，匹配到即返回
            for (String feedUrl : feedUrls) {
                Optional<String> content = tryFetchAndMatch(feedUrl, targetUrl);
                if (content.isPresent()) {
                    log.info("RSS 命中: feedUrl={}, targetUrl={}", feedUrl, targetUrl);
                    return content;
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("RSS 抓取异常: url={}, error={}", targetUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从 HTML 的 {@code <link rel="alternate">} 自动发现 Feed URL。
     */
    private List<String> discoverFeedUrls(Document document, String origin) {
        List<String> urls = new ArrayList<>();
        if (document == null) {
            return urls;
        }

        Elements links = document.select("link[rel=alternate]");
        for (Element link : links) {
            String type = link.attr("type");
            String href = link.attr("href");
            if (href == null || href.isEmpty()) {
                continue;
            }
            // 仅接受 RSS/Atom 类型
            if (type == null) {
                continue;
            }
            type = type.toLowerCase();
            if (type.contains("rss") || type.contains("atom")) {
                String fullUrl = normalizeUrl(href, origin);
                if (fullUrl != null) {
                    urls.add(fullUrl);
                }
            }
        }

        return urls;
    }

    private String normalizeUrl(String href, String origin) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }
        if (href.startsWith("/")) {
            return origin + href;
        }
        return origin + "/" + href;
    }

    /**
     * 抓取指定 Feed URL 并匹配目标文章。
     */
    private Optional<String> tryFetchAndMatch(String feedUrl, String targetUrl) {
        Request request = new Request.Builder()
                .url(feedUrl)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .header("User-Agent", "Poetize-WebFetch/1.0 (RSS Fetcher)")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Optional.empty();
            }
            ResponseBody body = response.body();
            if (body == null) {
                return Optional.empty();
            }

            byte[] bytes = body.bytes();
            if (bytes.length == 0 || bytes.length > MAX_FEED_BYTES) {
                return Optional.empty();
            }

            // 用 Rome 解析 Feed
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 XmlReader reader = new XmlReader(bais)) {
                feed = input.build(reader);
            }

            if (feed == null || feed.getEntries() == null || feed.getEntries().isEmpty()) {
                return Optional.empty();
            }

            // 在 Feed 中匹配目标文章 URL
            String normalizedTarget = normalizeArticleUrl(targetUrl);
            for (SyndEntry entry : feed.getEntries()) {
                if (matchesEntry(entry, normalizedTarget)) {
                    String content = extractEntryContent(entry);
                    if (content != null && content.length() > MIN_CONTENT_LENGTH) {
                        return Optional.of(content);
                    }
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            // 静默失败，Fallback Chain 会继续尝试下一层
            log.debug("RSS Feed 抓取失败: feedUrl={}, error={}", feedUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 规范化文章 URL（去除 query/fragment、统一 trailing slash）。
     */
    private String normalizeArticleUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            // 去除 trailing slash（除根路径外）
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return (uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                    + path).toLowerCase();
        } catch (Exception e) {
            return url.toLowerCase();
        }
    }

    /**
     * 判断 Feed 条目是否匹配目标文章 URL。
     * 支持：精确匹配、link 字段匹配、guid 字段匹配。
     */
    private boolean matchesEntry(SyndEntry entry, String normalizedTarget) {
        String link = entry.getLink();
        if (link != null && !link.isEmpty()) {
            if (normalizeArticleUrl(link).equals(normalizedTarget)) {
                return true;
            }
            // 部分 Feed 的 link 是相对路径或缺少协议
            if (link.toLowerCase().contains(normalizedTarget)) {
                return true;
            }
        }

        // 尝试 guid
        if (entry.getUri() != null && !entry.getUri().isEmpty()) {
            if (normalizeArticleUrl(entry.getUri()).equals(normalizedTarget)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提取 Feed 条目的正文内容。
     * 优先级：content (full) > description (summary) > title。
     */
    private String extractEntryContent(SyndEntry entry) {
        // 1. 优先取完整 content（RSS 2.0 content:encoded 或 Atom content）
        List<SyndContent> contents = entry.getContents();
        if (contents != null && !contents.isEmpty()) {
            for (SyndContent c : contents) {
                if (c != null && c.getValue() != null && c.getValue().length() > MIN_CONTENT_LENGTH) {
                    return c.getValue();
                }
            }
        }

        // 2. 取 description（摘要，可能是 HTML）
        SyndContent desc = entry.getDescription();
        if (desc != null && desc.getValue() != null && desc.getValue().length() > MIN_CONTENT_LENGTH) {
            return desc.getValue();
        }

        return null;
    }
}
