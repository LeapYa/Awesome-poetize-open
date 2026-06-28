package com.ld.poetry.service.ai.tools.webfetch;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Archive.org 历史快照客户端 — Fetcher Chain 第 5 层（死链/被封站点兜底）。
 * <p>
 * 当目标 URL 直接抓取失败（404、403、超时、被封禁）时，调用
 * <a href="https://web.archive.org">Internet Archive</a> 的 Wayback Machine API
 * 获取该 URL 的最近一次历史快照。
 * <p>
 * Wayback Machine API：
 * <ul>
 *   <li>查询最近快照：{@code https://archive.org/wayback/available?url={url}}</li>
 *   <li>获取快照内容：{@code https://web.archive.org/web/{timestamp}/{url}}</li>
 * </ul>
 * <p>
 * 完全开源（Internet Archive 是非营利组织），无 API Key、无速率限制（合理使用）。
 */
public class ArchiveOrgClient {

    private static final Logger log = LoggerFactory.getLogger(ArchiveOrgClient.class);

    /** Wayback Machine API 端点 */
    private static final String AVAILABILITY_API = "https://archive.org/wayback/available?url=";

    /** 快照内容响应体上限 */
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024; // 5MB

    /** 最小有效长度 */
    private static final int MIN_VALID_LENGTH = 200;

    private final OkHttpClient httpClient;

    public ArchiveOrgClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 尝试从 Archive.org Wayback Machine 获取目标 URL 的最近历史快照 HTML。
     *
     * @param targetUrl 用户提供的原始文章 URL
     * @return {@code Optional.of(htmlContent)} 当成功获取快照且长度 &gt; 200；否则 {@code Optional.empty()}
     */
    public Optional<String> fetchArchivedSnapshot(String targetUrl) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Optional.empty();
        }

        try {
            // 第 1 步：查询最近可用快照
            Optional<String> snapshotUrl = queryLatestSnapshot(targetUrl);
            if (!snapshotUrl.isPresent()) {
                return Optional.empty();
            }

            String archiveUrl = snapshotUrl.get();
            log.info("Archive.org 命中快照: archiveUrl={}", archiveUrl);

            // 第 2 步：抓取快照内容
            Optional<String> content = fetchSnapshotContent(archiveUrl);
            if (content.isPresent() && content.get().length() > MIN_VALID_LENGTH) {
                return content;
            }

            return Optional.empty();
        } catch (Exception e) {
            log.debug("Archive.org 抓取异常: url={}, error={}", targetUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查询目标 URL 的最近可用快照。
     * 返回完整的快照 URL（含时间戳），形如：
     * {@code https://web.archive.org/web/20240101000000/https://example.com/article}
     */
    private Optional<String> queryLatestSnapshot(String targetUrl) {
        String apiUrl = AVAILABILITY_API + URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Poetize-WebFetch/1.0 (Archive.org)")
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
            String json = body.string();
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }

            // 简单 JSON 解析（避免引入 Jackson）
            // 响应格式：{"archived_snapshots":{"closest":{"available":true,"url":"https://web.archive.org/web/.../...","timestamp":"...","status":"200"}}}
            String url = extractJsonStringField(json, "url");
            if (url == null || url.isEmpty()) {
                return Optional.empty();
            }

            // 检查 available 字段
            String available = extractJsonStringField(json, "available");
            if (!"true".equalsIgnoreCase(available)) {
                return Optional.empty();
            }

            return Optional.of(url);
        } catch (Exception e) {
            log.debug("Archive.org 查询失败: url={}, error={}", targetUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 抓取快照页面内容（HTML）。
     */
    private Optional<String> fetchSnapshotContent(String archiveUrl) {
        Request request = new Request.Builder()
                .url(archiveUrl)
                .header("Accept", "text/html, application/xhtml+xml, */*")
                .header("User-Agent", "Poetize-WebFetch/1.0 (Archive.org)")
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
            String content = body.string();
            if (content != null && content.length() > MIN_VALID_LENGTH
                    && content.length() <= MAX_RESPONSE_BYTES) {
                return Optional.of(content);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Archive.org 快照内容抓取失败: archiveUrl={}, error={}", archiveUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 简单 JSON 字符串字段提取（不引入完整 JSON 解析器）。
     * <p>
     * 仅适用于本场景的简单 JSON 结构。
     */
    private String extractJsonStringField(String json, String fieldName) {
        // 匹配 "fieldName":"value" 或 "fieldName" : "value"
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        // 跳过字段名和冒号
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) {
            return null;
        }
        // 跳过空白
        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            // 可能是 boolean/number 字段
            int end = i;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}'
                    && !Character.isWhitespace(json.charAt(end))) {
                end++;
            }
            return json.substring(i, end);
        }
        // 字符串字段
        int start = i + 1;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\' && end + 1 < json.length()) {
                end += 2;
            } else {
                end++;
            }
        }
        if (end >= json.length()) {
            return null;
        }
        return json.substring(start, end);
    }
}
