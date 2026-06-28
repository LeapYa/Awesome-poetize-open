package com.ld.poetry.service.ai.tools.webfetch;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

/**
 * llms.txt 客户端 — Fetcher Chain 第 4 层（AI 友好站点）。
 * <p>
 * 检查站点是否提供 {@code llms.txt}（一种为 LLM 设计的网站内容索引文件）。
 * 若存在，按其指引查找目标 URL 对应的 Markdown 版本或更友好的内容入口。
 * <p>
 * llms.txt 规范参考：https://llmstxt.org/
 * <p>
 * 检查位置（按优先级）：
 * <ol>
 *   <li>{@code /.well-known/llms.txt}（推荐标准位置）</li>
 *   <li>{@code /llms.txt}（兼容位置）</li>
 * </ol>
 * <p>
 * 完全本地处理，零外部 SaaS 依赖。
 */
public class LlmsTxtClient {

    private static final Logger log = LoggerFactory.getLogger(LlmsTxtClient.class);

    /** llms.txt 响应体上限 */
    private static final int MAX_RESPONSE_BYTES = 512 * 1024; // 512KB

    /** 最小有效长度 */
    private static final int MIN_VALID_LENGTH = 100;

    /** 候选 llms.txt 路径（按优先级） */
    private static final String[] LLMS_TXT_PATHS = {
            "/.well-known/llms.txt",
            "/llms.txt"
    };

    private final OkHttpClient httpClient;

    public LlmsTxtClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 检查目标 URL 所属站点是否提供 llms.txt，并尝试匹配目标文章。
     *
     * @param targetUrl 用户提供的原始文章 URL
     * @return {@code Optional.of(markdownContent)} 当找到匹配的 Markdown 内容；否则 {@code Optional.empty()}
     */
    public Optional<String> fetchLlmsTxtContent(String targetUrl) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(targetUrl);
            String origin = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

            // 第 1 步：抓取 llms.txt
            Optional<String> llmsTxt = fetchLlmsTxt(origin);
            if (!llmsTxt.isPresent()) {
                return Optional.empty();
            }

            String content = llmsTxt.get();
            log.info("llms.txt 命中: origin={}, len={}", origin, content.length());

            // 第 2 步：尝试在 llms.txt 中查找目标文章对应的 Markdown 链接
            Optional<String> markdownUrl = findMarkdownUrlForArticle(content, targetUrl, origin);
            if (!markdownUrl.isPresent()) {
                // 未找到具体 Markdown 链接，但 llms.txt 本身可作站点摘要返回
                return Optional.of(buildLlmsTxtSummary(content, origin));
            }

            // 第 3 步：抓取 Markdown 内容
            Optional<String> markdown = fetchMarkdown(markdownUrl.get());
            if (markdown.isPresent() && markdown.get().length() > MIN_VALID_LENGTH) {
                log.info("llms.txt Markdown 命中: mdUrl={}", markdownUrl.get());
                return markdown;
            }

            // Markdown 抓取失败，返回 llms.txt 摘要
            return Optional.of(buildLlmsTxtSummary(content, origin));
        } catch (Exception e) {
            log.debug("llms.txt 检查异常: url={}, error={}", targetUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 依次尝试候选路径抓取 llms.txt。
     */
    private Optional<String> fetchLlmsTxt(String origin) {
        for (String path : LLMS_TXT_PATHS) {
            String url = origin + path;
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "text/plain, text/markdown, */*")
                    .header("User-Agent", "Poetize-WebFetch/1.0 (llms.txt)")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    continue;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    continue;
                }
                String content = body.string();
                if (content != null && content.length() > MIN_VALID_LENGTH
                        && content.length() <= MAX_RESPONSE_BYTES) {
                    return Optional.of(content);
                }
            } catch (Exception e) {
                // 静默失败，尝试下一路径
                log.debug("llms.txt 抓取失败: url={}, error={}", url, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 在 llms.txt 内容中查找目标文章对应的 Markdown 链接。
     * <p>
     * llms.txt 格式参考 Markdown，常见结构：
     * <pre>
     * # Title
     * > Summary
     *
     * ## Section
     * - [Title](url): Description
     * - [Title](url): Description
     * </pre>
     * <p>
     * 本方法尝试按 URL 路径匹配。
     */
    private Optional<String> findMarkdownUrlForArticle(String llmsTxtContent, String targetUrl, String origin) {
        try {
            String targetPath = URI.create(targetUrl).getPath();
            if (targetPath == null || targetPath.isEmpty()) {
                return Optional.empty();
            }

            // 简单按行扫描，匹配包含目标路径的 Markdown 链接
            String[] lines = llmsTxtContent.split("\\r?\\n");
            for (String line : lines) {
                // 匹配 Markdown 链接 [text](url)
                int linkStart = line.indexOf("](");
                if (linkStart < 0) {
                    continue;
                }
                int urlStart = linkStart + 2;
                int urlEnd = line.indexOf(')', urlStart);
                if (urlEnd < 0) {
                    continue;
                }
                String url = line.substring(urlStart, urlEnd).trim();

                // 检查是否指向目标文章
                if (url.endsWith(".md") || url.endsWith(".markdown")) {
                    // 路径匹配（宽松判断）
                    String normalizedUrl = url.startsWith("http") ? url : (origin + (url.startsWith("/") ? "" : "/") + url);
                    if (matchesTarget(normalizedUrl, targetUrl, targetPath)) {
                        return Optional.of(normalizedUrl);
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("llms.txt Markdown 链接查找失败: error={}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean matchesTarget(String candidate, String targetUrl, String targetPath) {
        // 路径前缀匹配
        try {
            URI candidateUri = URI.create(candidate);
            String candidatePath = candidateUri.getPath();
            if (candidatePath == null) {
                return false;
            }
            // 去除 .md 扩展名后比较
            String basePath = candidatePath.replaceAll("\\.(md|markdown)$", "");
            String targetBase = targetPath.endsWith("/")
                    ? targetPath.substring(0, targetPath.length() - 1)
                    : targetPath;
            return targetBase.equals(basePath)
                    || targetBase.startsWith(basePath)
                    || basePath.startsWith(targetBase);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 抓取 Markdown 文件内容。
     */
    private Optional<String> fetchMarkdown(String mdUrl) {
        Request request = new Request.Builder()
                .url(mdUrl)
                .header("Accept", "text/plain, text/markdown, */*")
                .header("User-Agent", "Poetize-WebFetch/1.0 (llms.txt Markdown)")
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
            log.debug("Markdown 抓取失败: mdUrl={}, error={}", mdUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 构造 llms.txt 摘要返回（当未找到具体 Markdown 链接时使用）。
     */
    private String buildLlmsTxtSummary(String llmsTxtContent, String origin) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 站点 llms.txt 内容（来自 ").append(origin).append("）\n\n");
        sb.append("> 该站点提供了 llms.txt 文件（为 LLM 设计的内容索引）。以下是站点摘要：\n\n");
        sb.append("---\n\n");
        // 截取前 32000 字符避免超长
        if (llmsTxtContent.length() > 32000) {
            sb.append(llmsTxtContent, 0, 32000);
            sb.append("\n\n[... 内容已截断，原文共 ").append(llmsTxtContent.length()).append(" 字符]");
        } else {
            sb.append(llmsTxtContent);
        }
        return sb.toString();
    }
}
