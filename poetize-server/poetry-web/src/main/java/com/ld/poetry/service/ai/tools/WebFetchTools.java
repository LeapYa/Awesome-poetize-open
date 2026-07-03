package com.ld.poetry.service.ai.tools;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.extended.Readability4JExtended;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.tools.webfetch.ArchiveOrgClient;
import com.ld.poetry.service.ai.tools.webfetch.CachedPage;
import com.ld.poetry.service.ai.tools.webfetch.JinaReaderClient;
import com.ld.poetry.service.ai.tools.webfetch.LlmsTxtClient;
import com.ld.poetry.service.ai.tools.webfetch.MetadataExtractor;
import com.ld.poetry.service.ai.tools.webfetch.PageMetadata;
import com.ld.poetry.service.ai.tools.webfetch.QualityVerifier;
import com.ld.poetry.service.ai.tools.webfetch.RssFeedClient;
import com.ld.poetry.service.ai.tools.webfetch.SafeDns;
import com.ld.poetry.service.ai.tools.webfetch.SpaDetector;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * AI 网页访问工具 — 允许主模型在工具循环中按 URL 抓取公开网页内容。
 * <p>
 * 六层 Fetcher Chain 数据清洗流水线（借鉴 FreeWeb MCP 思路，Java 内置实现）：
 * <ol>
 *   <li>Readability4J 正文提取 — Mozilla Readability 算法，从 SSR/SSG HTML 提取正文子树</li>
 *   <li>JSON-LD articleBody — SEO 友好的 SPA 站点常在 head 嵌入结构化数据</li>
 *   <li>RSS/Atom Feed — 覆盖几乎所有博客站点（WordPress/Ghost/Hexo/Hugo/Typecho）</li>
 *   <li>llms.txt — AI 友好站点的内容索引文件（llmstxt.org 规范）</li>
 *   <li>Archive.org 历史快照 — 死链/被封站点的兜底</li>
 *   <li>Jina Reader SPA fallback — 纯 CSR SPA 站点的可选渲染兜底（默认开启）</li>
 * </ol>
 * 前 5 层均为本地处理，零外部 SaaS 依赖；Jina 为可选兜底层，站长显式启用。
 * <p>
 * 安全设计：OkHttp + SafeDns 防 DNS 重绑定、手动重定向循环、SSRF 私有地址过滤、
 * 5MB 响应体上限防 GZIP 炸弹、Semaphore(5) 全局并发限制、X-Robots-Tag 尊重。
 * <p>
 * 异常隔离：所有 IOException/UnknownHostException 均捕获并转为中文文本返回，
 * 绝不抛出异常打断 ToolCallingAdvisor 循环。
 */
@Service
public class WebFetchTools {

    private static final Logger log = LoggerFactory.getLogger(WebFetchTools.class);

    // ========== 常量 ==========
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final int MAX_RETURN_CHARS = 32_000;
    private static final int HARD_RETURN_LIMIT = 33_000;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 分钟
    private static final int CACHE_MAX_SIZE = 200;
    private static final int CACHE_EVICT_COUNT = 50;
    private static final int MAX_CONCURRENT = 5;
    private static final int CHROME_VERSION;
    private static final String USER_AGENT;

    static {
        java.time.LocalDate baseDate = java.time.LocalDate.of(2022, 3, 29);
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(baseDate, java.time.LocalDate.now());
        CHROME_VERSION = 100 + (int) (daysDiff / 32);
        USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + CHROME_VERSION + ".0.0.0 Safari/537.36";
    }

    // 提取策略常量
    private static final String STRATEGY_READABILITY = "READABILITY";
    private static final String STRATEGY_JSONLD_ONLY = "JSONLD_ONLY";
    private static final String STRATEGY_RSS_FEED = "RSS_FEED";
    private static final String STRATEGY_LLMS_TXT = "LLMS_TXT";
    private static final String STRATEGY_ARCHIVE_ORG = "ARCHIVE_ORG";
    private static final String STRATEGY_JINA_READER = "JINA_READER";
    private static final String STRATEGY_RAW_FALLBACK = "RAW_FALLBACK";
    private static final String STRATEGY_METADATA_ONLY = "METADATA_ONLY";

    /**
     * 噪音元素选择器 — 这些元素的内容不是正文载体，在计算正文长度与 RAW_FALLBACK 输出前需统一移除：
     * <ul>
     *   <li>{@code script} / {@code style} — 代码与样式，非可见正文</li>
     *   <li>{@code noscript} / {@code template} — JS 禁用时的兜底内容或模板片段，非实际渲染正文</li>
     *   <li>{@code textarea} — 百度等站点用隐藏 textarea 存放转义 CSS/模板（反爬手段），
     *       Jsoup {@code .text()} 会反转义后当作正文输出，曾导致 bodyTextLength 从 ~1K 膨胀到 257K</li>
     *   <li>{@code iframe} — 外部嵌入内容，Jsoup 不递归解析其 src</li>
     *   <li>{@code svg} — 矢量图形内嵌文本，通常为图标/装饰</li>
     * </ul>
     */
    private static final String NOISE_ELEMENTS_SELECTOR =
            "script, style, noscript, template, textarea, iframe, svg";

    // ========== 依赖 ==========
    private final SysAiConfigService sysAiConfigService;

    // ========== 运行时组件（@PostConstruct 初始化）==========
    private OkHttpClient mainHttpClient;
    private OkHttpClient jinaHttpClient;
    private JinaReaderClient jinaReaderClient;
    private RssFeedClient rssFeedClient;
    private LlmsTxtClient llmsTxtClient;
    private ArchiveOrgClient archiveOrgClient;
    private FlexmarkHtmlConverter htmlToMarkdownConverter;
    private final Semaphore concurrencyLimit = new Semaphore(MAX_CONCURRENT, true);
    private final ConcurrentHashMap<String, CachedPage> cache = new ConcurrentHashMap<>();

    public WebFetchTools(SysAiConfigService sysAiConfigService) {
        this.sysAiConfigService = sysAiConfigService;
    }

    @PostConstruct
    public void init() {
        SafeDns safeDns = new SafeDns();

        // 主 OkHttpClient：禁用自动重定向 + SafeDns 防 DNS 重绑定
        mainHttpClient = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .dns(safeDns)
                .build();

        // Jina 专用 OkHttpClient：始终发往 r.jina.ai 公网域名
        jinaHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .dns(safeDns)
                .build();

        jinaReaderClient = new JinaReaderClient(jinaHttpClient);

        // Fetcher Chain 第 3/4/5 层：RSS / llms.txt / Archive.org
        // 复用主 OkHttpClient（已配置 SafeDns 防 SSRF/DNS 重绑定）
        rssFeedClient = new RssFeedClient(mainHttpClient);
        llmsTxtClient = new LlmsTxtClient(mainHttpClient);
        archiveOrgClient = new ArchiveOrgClient(mainHttpClient);

        htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();

        log.info("WebFetchTools 初始化完成：主客户端 + Fetcher Chain（Readability → JSON-LD → RSS → llms.txt → Archive.org → Jina）+ 缓存（容量 {}，TTL {}ms）",
                CACHE_MAX_SIZE, CACHE_TTL_MS);
    }

    /**
     * Bean 销毁时显式关闭 OkHttpClient 的连接池与 dispatcher 线程池，避免容器重启时
     * 残留空闲连接与 ExecutorService 线程导致资源泄漏。
     * <p>
     * 子客户端（JinaReaderClient / RssFeedClient / LlmsTxtClient / ArchiveOrgClient）
     * 均复用这两个主客户端，无需单独关闭。
     */
    @PreDestroy
    public void destroy() {
        shutdownClient(mainHttpClient, "mainHttpClient");
        shutdownClient(jinaHttpClient, "jinaHttpClient");
        cache.clear();
    }

    private void shutdownClient(OkHttpClient client, String name) {
        if (client == null) {
            return;
        }
        try {
            // 先停止接收新请求，再清理连接池，最后关闭 dispatcher 的 ExecutorService
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
            log.info("OkHttpClient 已关闭: {}", name);
        } catch (Exception e) {
            log.warn("关闭 OkHttpClient 失败: name={}, error={}", name, e.getMessage());
        }
    }

    /**
     * 暴露 JinaReaderClient 供 JinaQueueController 查询排队状态。
     */
    public JinaReaderClient getJinaReaderClient() {
        return jinaReaderClient;
    }

    @Tool(description = "抓取并阅读指定 URL 的公开网页内容，经六层 Fetcher Chain 流水线提取正文后返回。" +
            "Fetcher Chain 优先级：Readability4J 正文提取 → JSON-LD articleBody → RSS/Atom Feed → llms.txt → Archive.org 历史快照 → Jina Reader SPA 渲染。" +
            "前 5 层均为本地处理，零外部 SaaS 依赖；Jina 为可选兜底层（站长显式启用）。" +
            "仅当用户明确要求阅读、总结或提取某个具体 URL 的内容时才调用此工具。" +
            "每次返回约 32000 字符，若返回元信息中 Has-More=true 则使用更大的 offset 参数再次调用以续读后续内容。" +
            "续读应尽快完成（缓存 TTL 5 分钟）。" +
            "返回元信息中若包含 WARNING 字段，应在回答中诚实告知用户提取质量可能不佳，建议核对原文。" +
            "限制：仅支持公开网页，不支持 PDF/图片正文；不支持内网地址。")
    public String fetchWebPage(
            @ToolParam(description = "目标网页的完整 http(s):// URL") String url,
            @ToolParam(description = "从正文开始读取的字符偏移量，默认 0。续读时传入上次返回的 offset + returned_chars。",
                    required = false) Integer offset) {

        log.info("fetchWebPage 被调用: url={}, offset={}", url, offset);

        // ===== 第 1 步：URL 基础校验 =====
        if (!StringUtils.hasText(url)) {
            return "URL 为空，无法抓取。";
        }
        String trimmedUrl = url.trim();
        String urlCheckResult = validateUrl(trimmedUrl);
        if (urlCheckResult != null) {
            return urlCheckResult;
        }

        // ===== 第 2 步：offset 规范化 =====
        int effectiveOffset = (offset == null || offset < 0) ? 0 : offset;

        // ===== 第 3 步：查缓存 =====
        String cacheKey = normalizeCacheKey(trimmedUrl);
        CachedPage cached = cacheGet(cacheKey);
        if (cached != null) {
            log.info("缓存命中: url={}, strategy={}, totalLength={}", trimmedUrl, cached.getStrategy(), cached.getTotalLength());
            return sliceFromCache(cached, effectiveOffset);
        }

        // ===== 第 4 步：获取并发许可 =====
        boolean acquired;
        try {
            acquired = concurrencyLimit.tryAcquire(0, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "抓取请求被中断，请稍后重试。";
        }
        if (!acquired) {
            return "服务器繁忙，请稍后重试。（当前并发抓取请求数已达上限）";
        }

        try {
            // ===== 第 5 步：手动重定向循环（最多 5 跳） =====
            FetchResult fetchResult = fetchWithRedirects(trimmedUrl);
            if (fetchResult.errorMessage != null) {
                return fetchResult.errorMessage;
            }

            // ===== 第 6 步：X-Robots-Tag 检查 =====
            String xRobotsTag = fetchResult.xRobotsTag;
            if (xRobotsTag != null) {
                String lower = xRobotsTag.toLowerCase(Locale.ROOT);
                if (lower.contains("noindex") || lower.contains("none")) {
                    return "目标站点通过 X-Robots-Tag 标记为 noindex，本工具不读取其正文。\nURL: " + fetchResult.finalUrl;
                }
            }

            // ===== 第 7 步：Content-Type 过滤 =====
            String contentType = fetchResult.contentType;
            if (!isAcceptableContentType(contentType)) {
                return formatNonHtmlResponse(fetchResult, contentType);
            }

            // ===== 第 8 步：响应体已在 fetchWithRedirects 中读取（带 5MB 上限） =====
            byte[] bodyBytes = fetchResult.bodyBytes;
            if (bodyBytes == null || bodyBytes.length == 0) {
                return "目标页面响应体为空。\nURL: " + fetchResult.finalUrl;
            }

            // ===== 第 9 步：Jsoup 解析 HTML =====
            String html = new String(bodyBytes, StandardCharsets.UTF_8);
            Document document;
            try {
                document = Jsoup.parse(html, fetchResult.finalUrl);
            } catch (Exception e) {
                log.warn("Jsoup 解析失败: url={}, error={}", fetchResult.finalUrl, e.getMessage());
                return "页面 HTML 解析失败。\nURL: " + fetchResult.finalUrl;
            }

            // ===== 第 10 步：元数据预提取 =====
            PageMetadata metadata = MetadataExtractor.extract(document);

            // ===== 第 11 步：SPA 检测 =====
            boolean isSpa = SpaDetector.isSpa(document);
            // 计算正文文本长度前先移除噪音元素，避免隐藏 textarea 里转义的 CSS/模板
            // 虚增 bodyTextLength（百度首页曾因此从真实 ~1K 膨胀到 257K，
            // 导致 Readability ratio=0.001 触发 RAW_FALLBACK 并输出 CSS 噪音）。
            Document cleanForMetrics = document.clone();
            stripNoiseElements(cleanForMetrics, fetchResult.finalUrl, "bodyTextLength");
            int bodyTextLength = cleanForMetrics.body() != null ? cleanForMetrics.body().text().length() : 0;
            log.info("页面分析: url={}, isSpa={}, bodyTextLength={}, hasJsonLdBody={}, hasOgTitle={}",
                    fetchResult.finalUrl, isSpa, bodyTextLength,
                    metadata.getJsonLdArticleBody() != null, metadata.getOgTitle() != null);

            // ===== 第 12 步：决策树 =====
            SysAiConfig aiConfig = sysAiConfigService.getAiChatConfigInternal("default");
            ExtractResult extractResult = extractContent(document, fetchResult.finalUrl, metadata,
                    isSpa, bodyTextLength, aiConfig);

            // ===== 第 13 步：flexmark 转换已在 extractContent 内完成 =====

            // ===== 第 14 步：缓存转换结果 =====
            CachedPage page = new CachedPage();
            page.setMarkdown(extractResult.markdown);
            page.setTitle(extractResult.title);
            page.setByline(extractResult.byline);
            page.setFinalUrl(fetchResult.finalUrl);
            page.setContentType(contentType);
            page.setTotalLength(extractResult.markdown.length());
            page.setOriginalResponseTruncated(fetchResult.responseTruncated);
            page.setStrategy(extractResult.strategy);
            page.setQualityWarnings(extractResult.qualityWarnings);
            page.setFetchedAt(System.currentTimeMillis());
            cachePut(cacheKey, page);

            // ===== 第 15 步：语义边界截断 + 第 16 步：构造返回 =====
            String result = sliceFromCache(page, effectiveOffset);

            // ===== 第 18 步：审计日志 =====
            logAudit(fetchResult, extractResult, effectiveOffset);

            return result;

        } catch (Exception e) {
            log.error("fetchWebPage 异常: url={}, error={}", trimmedUrl, e.getMessage(), e);
            return "网页抓取过程中发生异常：" + e.getMessage();
        } finally {
            // ===== 第 17 步：释放并发许可 =====
            concurrencyLimit.release();
        }
    }

    // ========== URL 校验 ==========

    private String validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "URL 格式无效：" + e.getMessage();
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return "仅支持 http/https 协议的 URL。";
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return "URL 缺少有效主机名。";
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (hostLower.equals("localhost") || hostLower.endsWith(".local")) {
            return "拒绝访问本地地址。";
        }

        // 拒绝 IP 字面量中的内网/保留地址：OkHttp 对 IP 字面量直接建立 TCP 连接而跳过 DNS 解析，
        // 导致 SafeDns 无法拦截。需在 URL 校验阶段显式检测并拒绝。
        if (isIpLiteral(host)) {
            try {
                InetAddress addr = InetAddress.getByName(host);
                if (SafeDns.isPrivateOrReserved(addr)) {
                    return "拒绝访问内网或保留 IP 地址。";
                }
            } catch (UnknownHostException e) {
                return "URL 主机解析失败：" + e.getMessage();
            }
        }
        return null;
    }

    /**
     * 判断 host 是否为 IP 字面量（IPv4 或 IPv6），避免在 URL 校验阶段触发 DNS 查询。
     */
    private boolean isIpLiteral(String host) {
        // IPv4 字面量，如 127.0.0.1
        if (host.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
            return true;
        }
        // IPv6 字面量（URI.getHost() 返回不带方括号的地址，如 ::1）
        return host.contains(":");
    }

    private boolean isAcceptableContentType(String contentType) {
        if (contentType == null) {
            // 无 Content-Type 头视为可处理（部分服务器不返回）
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("text/html")
                || lower.contains("application/xhtml+xml")
                || lower.contains("text/plain");
    }

    private String formatNonHtmlResponse(FetchResult fetchResult, String contentType) {
        StringBuilder sb = new StringBuilder();
        sb.append("目标页面非 HTML 内容，无法提取正文。\n");
        sb.append("URL: ").append(fetchResult.finalUrl).append("\n");
        sb.append("Content-Type: ").append(contentType).append("\n");
        sb.append("Content-Length: ").append(fetchResult.contentLength).append(" bytes\n");
        sb.append("建议：如需阅读此内容，请直接在浏览器中打开或复制关键段落。");
        return sb.toString();
    }

    // ========== HTTP 抓取 + 手动重定向 ==========

    private FetchResult fetchWithRedirects(String startUrl) {
        FetchResult result = new FetchResult();
        String currentUrl = startUrl;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            // URL 校验（每跳重新校验）
            String urlError = validateUrl(currentUrl);
            if (urlError != null) {
                result.errorMessage = urlError;
                return result;
            }

            // 注意：不要手动设置 Accept-Encoding: gzip。
            // OkHttp 的 BridgeInterceptor 仅在「自己添加」该头时才会透明解压 gzip；
            // 一旦手动设置，OkHttp 会跳过解压逻辑，body.byteStream() 将返回原始压缩字节，
            // 导致后续按 UTF-8/HTML 解析得到二进制乱码。这里省略该头，让 OkHttp 自动协商并解压。
            Request request = new Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"" + CHROME_VERSION + "\", \"Google Chrome\";v=\"" + CHROME_VERSION + "\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build();

            try (Response response = mainHttpClient.newCall(request).execute()) {
                int code = response.code();

                // 处理 3xx 重定向
                if (code >= 300 && code < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isEmpty()) {
                        result.errorMessage = "重定向响应缺少 Location 头（status=" + code + "）。";
                        return result;
                    }
                    // 解析相对路径
                    URI baseUri = new URI(currentUrl);
                    URI resolved = baseUri.resolve(location);
                    currentUrl = resolved.toString();
                    log.info("重定向: hop={}, {} -> {}", hop, currentUrl, location);
                    continue;
                }

                // 非 2xx 错误
                if (code < 200 || code >= 300) {
                    result.errorMessage = "目标返回错误状态码：" + code + "。\nURL: " + currentUrl;
                    return result;
                }

                // 200 OK：提取元信息
                result.finalUrl = currentUrl;
                result.contentType = response.header("Content-Type");
                result.xRobotsTag = response.header("X-Robots-Tag");
                String contentLength = response.header("Content-Length");
                if (contentLength != null) {
                    try {
                        result.contentLength = Long.parseLong(contentLength);
                    } catch (NumberFormatException ignored) {
                    }
                }

                // 检查 Content-Encoding：OkHttp 自动解压 gzip/deflate 并移除该头，
                // 但 br(brotli)/zstd 不会自动解压，直接读取会乱码。
                String contentEncoding = response.header("Content-Encoding");
                if (contentEncoding != null && !"identity".equalsIgnoreCase(contentEncoding)) {
                    result.errorMessage = "不支持的 Content-Encoding: " + contentEncoding + "（无法解压，可能乱码）";
                    return result;
                }

                // 流式读取响应体（5MB 上限）
                ResponseBody body = response.body();
                if (body == null) {
                    result.errorMessage = "响应体为空。\nURL: " + currentUrl;
                    return result;
                }
                try (InputStream in = body.byteStream()) {
                    result.bodyBytes = readBodyWithLimit(in, MAX_RESPONSE_BYTES, result);
                }
                return result;

            } catch (UnknownHostException e) {
                log.warn("DNS 解析失败（可能为 SafeDns 拦截的内网地址）: url={}, error={}", currentUrl, e.getMessage());
                result.errorMessage = "无法访问目标地址（DNS 解析失败或被 SSRF 防护拦截）。";
                return result;
            } catch (IOException e) {
                log.warn("HTTP 请求失败: url={}, error={}", currentUrl, e.getMessage());
                result.errorMessage = "网络请求失败：" + e.getMessage() + "。\nURL: " + currentUrl;
                return result;
            } catch (URISyntaxException e) {
                result.errorMessage = "URL 格式无效：" + e.getMessage();
                return result;
            }
        }

        result.errorMessage = "重定向次数超过上限（" + MAX_REDIRECTS + " 次）。";
        return result;
    }

    /**
     * 流式读取响应体，硬上限 maxBytes。超出标记 truncated=true，并主动丢弃剩余数据。
     */
    private byte[] readBodyWithLimit(InputStream in, int maxBytes, FetchResult result) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            int toWrite = Math.min(n, maxBytes - total);
            if (toWrite <= 0) {
                result.responseTruncated = true;
                // 已达上限：主动跳过并丢弃剩余数据，确保连接正确关闭
                in.skip(Long.MAX_VALUE);
                break;
            }
            out.write(buffer, 0, toWrite);
            total += toWrite;
        }
        return out.toByteArray();
    }

    // ========== 内容提取决策树 ==========

    private ExtractResult extractContent(Document document, String finalUrl, PageMetadata metadata,
                                         boolean isSpa, int bodyTextLength, SysAiConfig config) {
        ExtractResult result = new ExtractResult();
        result.qualityWarnings = new ArrayList<>();
        result.title = pickTitle(metadata);
        result.byline = metadata.getAuthor();

        // 决策树：根据 body 文本长度和 SPA 签名选择路径
        if (bodyTextLength < 500 && isSpa) {
            // SPA 路径
            return extractFromSpa(document, finalUrl, metadata, config, result);
        }

        // 常规 / SSR / SSG 路径：Readability4J 主路径
        return extractWithReadability(document, finalUrl, metadata, isSpa, bodyTextLength, config, result);
    }

    private ExtractResult extractWithReadability(Document document, String finalUrl, PageMetadata metadata,
                                                  boolean isSpa, int bodyTextLength, SysAiConfig config,
                                                  ExtractResult result) {
        // Readability4J.parse() 会修改传入的 Document（清理 DOM 结构），
        // 因此先用 clone 保护原始 DOM，供质量验证和 raw body fallback 使用。
        Document originalDocument = document.clone();

        Article article;
        try {
            Readability4JExtended readability = new Readability4JExtended(finalUrl, document);
            article = readability.parse();
        } catch (Exception e) {
            log.warn("Readability4J 解析失败: url={}, error={}", finalUrl, e.getMessage());
            return fallbackToRawText(originalDocument, finalUrl, result,
                    "Readability 解析异常：" + e.getMessage());
        }

        if (article == null) {
            return fallbackToRawText(originalDocument, finalUrl, result, "Readability 返回 null");
        }

        String articleTitle = article.getTitle();
        String articleHtml = article.getContent();
        String articleText = article.getTextContent();
        if (articleText == null) {
            articleText = "";
        }

        // 质量验证：使用 parse 前保存的原始 body 长度，避免 Readability 破坏 DOM 后指标失真
        QualityVerifier.QualityResult quality = QualityVerifier.verify(
                articleTitle, articleText, originalDocument, metadata, bodyTextLength);
        double ratio = quality.getExtractionRatio();
        result.qualityWarnings.addAll(quality.getWarnings());

        log.info("Readability 提取: ratio={}, hasTitle={}, coverage={}, bodyLen={}, articleLen={}",
                String.format("%.3f", ratio), quality.isHasTitle(),
                String.format("%.3f", quality.getKeySignalCoverage()), bodyTextLength, articleText.length());

        // 决策回退（统一使用 originalDocument，避免 Readability 清理后的 DOM 导致 fallback 为空）
        if (ratio < 0.10 && isSpa) {
            // 提取失败且为 SPA → 转入完整 SPA fallback 路径（JSON-LD → Jina → 元数据）
            log.info("比例 < 0.10 且 SPA 签名，转入 SPA fallback 路径");
            // 保留已收集的警告，复用 SPA 提取逻辑
            result.qualityWarnings.add("WARNING: Readability 提取比例过低（"
                    + String.format("%.1f%%", ratio * 100) + "），SPA 签名触发 fallback");
            return extractFromSpa(originalDocument, finalUrl, metadata, config, result);
        }

        if (ratio < 0.10) {
            // 提取失败，无 SPA 签名 → 回退到 raw body text
            return fallbackToRawText(originalDocument, finalUrl, result,
                    "Readability 提取比例过低（" + String.format("%.1f%%", ratio * 100) + "），可能页面结构特殊");
        }

        if (ratio > 0.98) {
            // 提取结果几乎等于原始 body（常见于预渲染 SPA 或单区块页面），
            // 不再强制回退到 raw body（Readability 已清理 script/style，通常更干净），
            // 仅追加警告供调用方感知。
            result.qualityWarnings.add("Readability 提取比例接近 100%，可能为预渲染页面或正文占满 body");
            log.info("Readability 提取比例接近 100%，接受 Readability 结果: url={}, ratio={}",
                    finalUrl, String.format("%.3f", ratio));
        }

        // 低质量短页面检测：关键信号覆盖率低 + 提取比例低 + 提取内容过短 + 页面本身也短。
        // 典型场景：搜索引擎门户/工具型页面（如百度首页），Readability 倾向于提取页脚/备案信息，
        // 遗漏搜索框 placeholder、导航链接等页面主体。此时 RAW_FALLBACK 反而能输出更完整的可见文本。
        // 4 项条件同时满足才触发，避免误伤：
        //   - 正常短文章通常 coverage>0.30（title 关键词在正文中命中）
        //   - Readability 已提取大部分内容的页面 ratio>0.80，RAW_FALLBACK 不会带来更多内容，
        //     反而会丢失 markdown 中的链接 URL 等结构化信息（如 juejin 首页 ratio=0.949）
        if (quality.getKeySignalCoverage() < 0.30
                && ratio < 0.80
                && articleText.length() < 1000
                && bodyTextLength < 2000) {
            log.info("低质量短页面检测: url={}, coverage={}, ratio={}, articleLen={}, bodyLen={} → 改用 RAW_FALLBACK",
                    finalUrl, String.format("%.3f", quality.getKeySignalCoverage()),
                    String.format("%.3f", ratio), articleText.length(), bodyTextLength);
            return fallbackToRawText(originalDocument, finalUrl, result,
                    "Readability 提取内容不完整（关键信号覆盖率低，可能为非文章型页面）");
        }

        // 正常路径：将 Readability 提取的 HTML 转为 Markdown
        String markdown;
        if (StringUtils.hasText(articleHtml)) {
            markdown = convertHtmlToMarkdown(articleHtml, finalUrl);
        } else {
            markdown = articleText;
        }

        result.markdown = markdown;
        result.strategy = STRATEGY_READABILITY;
        if (StringUtils.hasText(articleTitle) && articleTitle.length() > 5) {
            result.title = articleTitle;
        }
        String byline = article.getByline();
        if (StringUtils.hasText(byline)) {
            result.byline = byline;
        }
        return result;
    }

    private ExtractResult extractFromSpa(Document document, String finalUrl, PageMetadata metadata,
                                         SysAiConfig config, ExtractResult result) {
        // SPA 路径 1：JSON-LD articleBody 优先
        if (StringUtils.hasText(metadata.getJsonLdArticleBody())
                && metadata.getJsonLdArticleBody().length() > 200) {
            result.markdown = metadata.getJsonLdArticleBody();
            result.strategy = STRATEGY_JSONLD_ONLY;
            result.qualityWarnings.add("提取策略: JSON-LD 结构化数据（articleBody）");
            log.info("SPA 路径: JSON-LD articleBody, len={}", metadata.getJsonLdArticleBody().length());
            return result;
        }

        // SPA 路径 2：RSS/Atom Feed — Fetcher Chain 第 3 层（本地，零 SaaS 依赖）
        // 覆盖几乎所有博客站点（WordPress/Ghost/Hexo/Hugo/Typecho 等）
        try {
            Optional<String> rssContent = rssFeedClient.fetchArticleContent(finalUrl, document);
            if (rssContent.isPresent()) {
                String content = rssContent.get();
                // RSS 内容可能是 HTML，转换为 Markdown
                String markdown = convertHtmlToMarkdown(content, finalUrl);
                if (markdown.length() > 200) {
                    result.markdown = markdown;
                    result.strategy = STRATEGY_RSS_FEED;
                    result.qualityWarnings.add("提取策略: RSS/Atom Feed（本地解析，零外部 SaaS 依赖）");
                    log.info("SPA 路径: RSS/Atom Feed 命中, len={}", markdown.length());
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("RSS Feed 抓取失败，继续下一层: url={}, error={}", finalUrl, e.getMessage());
        }

        // SPA 路径 3：llms.txt — Fetcher Chain 第 4 层（本地，AI 友好站点）
        try {
            Optional<String> llmsContent = llmsTxtClient.fetchLlmsTxtContent(finalUrl);
            if (llmsContent.isPresent() && llmsContent.get().length() > 200) {
                result.markdown = llmsContent.get();
                result.strategy = STRATEGY_LLMS_TXT;
                result.qualityWarnings.add("提取策略: llms.txt（AI 友好站点索引，本地解析）");
                log.info("SPA 路径: llms.txt 命中, len={}", llmsContent.get().length());
                return result;
            }
        } catch (Exception e) {
            log.debug("llms.txt 抓取失败，继续下一层: url={}, error={}", finalUrl, e.getMessage());
        }

        // SPA 路径 4：Archive.org 历史快照 — Fetcher Chain 第 5 层（死链/被封兜底）
        try {
            Optional<String> archivedHtml = archiveOrgClient.fetchArchivedSnapshot(finalUrl);
            if (archivedHtml.isPresent()) {
                String archivedMarkdown = convertHtmlToMarkdown(archivedHtml.get(), finalUrl);
                if (archivedMarkdown.length() > 200) {
                    result.markdown = archivedMarkdown;
                    result.strategy = STRATEGY_ARCHIVE_ORG;
                    result.qualityWarnings.add("提取策略: Archive.org Wayback Machine 历史快照");
                    log.info("SPA 路径: Archive.org 命中, len={}", archivedMarkdown.length());
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("Archive.org 抓取失败，继续下一层: url={}, error={}", finalUrl, e.getMessage());
        }

        // SPA 路径 5：Jina Reader fallback — Fetcher Chain 第 6 层（可选 SaaS 兜底，默认开启）
        // 无 Key 模式：免费 20 RPM（永久免费，不消耗 Token 额度），超限排队等待（最多 120 秒）
        // 有 Key 模式：500 RPM + 10M 免费 Token，用完后可付费续费或退回无 Key 模式
        boolean jinaEnabled = config != null
                && Integer.valueOf(1).equals(config.getEnableJinaReader());

        if (jinaEnabled) {
            String effectiveApiKey = StringUtils.hasText(config.getJinaApiKey())
                    ? config.getJinaApiKey() : null;
            StringBuilder queueInfo = new StringBuilder();
            Optional<String> jinaMarkdown = jinaReaderClient.fetch(finalUrl, effectiveApiKey, queueInfo);
            if (jinaMarkdown.isPresent()) {
                result.markdown = jinaMarkdown.get();
                result.strategy = STRATEGY_JINA_READER;
                String mode = effectiveApiKey != null ? "有 Key (500 RPM)" : "无 Key (20 RPM 免费)";
                log.info("SPA 路径: Jina Reader fallback 成功, len={}, mode={}, queueInfo={}",
                        jinaMarkdown.get().length(), mode, queueInfo);
                return result;
            }
            // Jina 失败/排队超时 — 在警告中包含排队信息供前端解析
            if (queueInfo.length() > 0) {
                result.qualityWarnings.add("Jina Reader fallback 失败，排队信息: " + queueInfo);
            } else {
                result.qualityWarnings.add("Jina Reader fallback 调用失败，已降级");
            }
        }

        // SPA 路径 6：拼装元数据 + 友好提示
        return extractFromSpaFallback(finalUrl, metadata, config, result);
    }

    private ExtractResult extractFromSpaFallback(String finalUrl, PageMetadata metadata,
                                                  SysAiConfig config, ExtractResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(pickTitle(metadata)).append("\n\n");
        if (StringUtils.hasText(metadata.getAuthor())) {
            sb.append("**作者**: ").append(metadata.getAuthor()).append("\n\n");
        }
        if (StringUtils.hasText(metadata.getPublishedTime())) {
            sb.append("**发布时间**: ").append(metadata.getPublishedTime()).append("\n\n");
        }
        if (StringUtils.hasText(metadata.getDescription())) {
            sb.append("**摘要**: ").append(metadata.getDescription()).append("\n\n");
        }
        if (StringUtils.hasText(metadata.getOgImage())) {
            sb.append("**封面图**: ").append(metadata.getOgImage()).append("\n\n");
        }
        sb.append("---\n\n");
        sb.append("该页面为客户端渲染 SPA，工具已尽力尝试以下路径但均未获取到完整正文：\n");
        sb.append("- JSON-LD articleBody（未嵌入或为空）\n");
        sb.append("- RSS/Atom Feed（未找到匹配条目）\n");
        sb.append("- llms.txt（站点未提供）\n");
        sb.append("- Archive.org 历史快照（无可用快照）\n");
        sb.append("- Jina Reader fallback（未启用）\n\n");
        sb.append("如需完整正文，请使用浏览器手动复制，或在管理后台启用 Jina Reader fallback（enableJinaReader=true）。\n");

        result.markdown = sb.toString();
        result.strategy = STRATEGY_METADATA_ONLY;
        log.info("SPA 路径: 元数据 + 友好提示");
        return result;
    }

    /**
     * 从文档中移除噪音元素（{@link #NOISE_ELEMENTS_SELECTOR}）并记录详细日志。
     * <p>
     * 统一应用于以下路径，确保噪音元素不会虚增正文长度或泄漏到输出：
     * <ul>
     *   <li>bodyTextLength 计算 — 影响 Readability ratio 决策树</li>
     *   <li>RAW_FALLBACK 输出 — 直接作为正文返回给 AI</li>
     *   <li>convertHtmlToMarkdown 兜底 — flexmark 转换失败时回退到 Jsoup 纯文本</li>
     * </ul>
     * <p>
     * 日志输出各类型噪音元素的数量，便于排查反爬手段（如百度隐藏 textarea CSS）。
     *
     * @param document 待清洗的文档（会被原地修改，调用方应先 clone）
     * @param url      页面 URL，用于日志定位
     * @param context  调用上下文标记（如 "bodyTextLength"、"RAW_FALLBACK"），用于日志区分
     * @return 被移除的噪音元素总数（0 表示无需清洗）
     */
    private int stripNoiseElements(Document document, String url, String context) {
        int scriptCount = document.select("script").size();
        int styleCount = document.select("style").size();
        int textareaCount = document.select("textarea").size();
        int noscriptCount = document.select("noscript").size();
        int templateCount = document.select("template").size();
        int iframeCount = document.select("iframe").size();
        int svgCount = document.select("svg").size();
        int totalBefore = scriptCount + styleCount + textareaCount
                + noscriptCount + templateCount + iframeCount + svgCount;

        if (totalBefore == 0) {
            log.debug("噪音元素清洗: url={}, context={}, 无噪音元素", url, context);
            return 0;
        }

        document.select(NOISE_ELEMENTS_SELECTOR).remove();

        // 常规 script/style/iframe 等噪音属于常态，降为 debug 避免污染生产日志；
        // 仅当出现 textarea 反爬手段（百度等隐藏 CSS 的典型特征）时升级为 info 告警。
        if (textareaCount > 0) {
            log.info("噪音元素清洗(检测到 textarea 反爬): url={}, context={}, textarea={}, script={}, "
                            + "style={}, noscript={}, template={}, iframe={}, svg={}, 总计={}",
                    url, context, textareaCount, scriptCount, styleCount,
                    noscriptCount, templateCount, iframeCount, svgCount, totalBefore);
        } else {
            log.debug("噪音元素清洗: url={}, context={}, 移除 script={}, style={}, textarea={}, "
                            + "noscript={}, template={}, iframe={}, svg={}, 总计={}",
                    url, context, scriptCount, styleCount, textareaCount,
                    noscriptCount, templateCount, iframeCount, svgCount, totalBefore);
        }

        return totalBefore;
    }

    private ExtractResult fallbackToRawText(Document document, String finalUrl, ExtractResult result, String reason) {
        // 克隆后移除噪音元素，避免把隐藏 textarea 里转义的 CSS/模板当作正文。
        // 关键点：百度等站点把大段 CSS 以转义文本存放在 <textarea style="display:none"> 中
        // （如 s_is_result_css），Jsoup .text() 会将其反转义后当作正文输出，导致 RAW_FALLBACK
        // 返回 250KB+ 的 CSS 噪音。
        Document clean = document.clone();
        stripNoiseElements(clean, finalUrl, "RAW_FALLBACK");
        String rawText = clean.body() != null ? clean.body().text() : "";
        // 压缩多余空白
        rawText = rawText.replaceAll("\\s{2,}", " ").trim();

        result.markdown = rawText;
        result.strategy = STRATEGY_RAW_FALLBACK;
        result.qualityWarnings.add("WARNING: Readability 提取异常（" + reason + "），已回退到清洗后的 body 文本，可能含噪音");
        log.warn("回退到 raw body text: url={}, reason={}, rawLen={}", finalUrl, reason, rawText.length());
        return result;
    }

    private String convertHtmlToMarkdown(String html, String url) {
        try {
            return htmlToMarkdownConverter.convert(html);
        } catch (Exception e) {
            log.warn("flexmark HTML→Markdown 转换失败: url={}, error={}, 回退到 Jsoup 纯文本（含噪音清洗）",
                    url, e.getMessage());
            // flexmark 转换失败时回退到 Jsoup 纯文本，同样需清洗噪音元素，
            // 避免 script/style/textarea 内容泄漏到正文
            try {
                Document doc = Jsoup.parse(html);
                stripNoiseElements(doc, url, "convertHtmlToMarkdown-fallback");
                String text = doc.body() != null ? doc.body().text() : "";
                return text.replaceAll("\\s{2,}", " ").trim();
            } catch (Exception e2) {
                log.error("Jsoup 纯文本回退也失败: url={}, error={}", url, e2.getMessage());
                return "";
            }
        }
    }

    // ========== 截断与输出格式化 ==========

    private String sliceFromCache(CachedPage page, int offset) {
        String markdown = page.getMarkdown();
        int total = markdown.length();

        if (offset >= total) {
            // offset 越界
            if (page.isOriginalResponseTruncated() && offset >= MAX_RESPONSE_BYTES) {
                return formatOverLimitResponse(page, offset);
            }
            return formatEmptySliceResponse(page, offset);
        }

        // 语义边界截断
        TruncateResult trunc = semanticTruncate(markdown, offset);
        String slice = trunc.chunk;
        int returned = slice.length();
        boolean hasMore = offset + returned < total;

        return formatSuccessResponse(page, offset, returned, hasMore, slice);
    }

    /**
     * 语义边界截断：在 Markdown 块级元素边界处截断，不破坏代码块/表格/链接语法。
     */
    private TruncateResult semanticTruncate(String markdown, int offset) {
        int total = markdown.length();
        int end = Math.min(offset + MAX_RETURN_CHARS, total);

        if (end == total) {
            return new TruncateResult(markdown.substring(offset, end), false);
        }

        // 1. 尝试在段落边界（双换行）截断
        int boundary = markdown.lastIndexOf("\n\n", end);
        if (boundary <= offset) {
            // 无段落边界，尝试单换行
            boundary = markdown.lastIndexOf("\n", end);
        }
        if (boundary > offset) {
            end = boundary;
        }

        // 2. 检查代码块 ``` 配对
        String chunk = markdown.substring(offset, end);
        long fenceCount = countOccurrences(chunk, "```");
        if (fenceCount % 2 != 0) {
            // 代码块未闭合
            int nextFence = markdown.indexOf("```", end);
            if (nextFence >= 0 && nextFence + 3 <= offset + HARD_RETURN_LIMIT) {
                // 可在硬上限内延伸到代码块闭合
                end = nextFence + 3;
                chunk = markdown.substring(offset, end);
            } else {
                // 无法延伸 — 回退到代码块开始之前
                int lastFenceOpen = chunk.lastIndexOf("```");
                if (lastFenceOpen > 0) {
                    end = offset + lastFenceOpen;
                    chunk = markdown.substring(offset, end);
                }
            }
        }

        // 3. 检查未闭合的链接 [text](url
        int lastLinkOpen = chunk.lastIndexOf("](");
        if (lastLinkOpen >= 0) {
            int closeParen = chunk.indexOf(")", lastLinkOpen);
            if (closeParen < 0) {
                // 链接未闭合 — 回退到 [ 之前
                int lastOpenBracket = chunk.lastIndexOf("[", lastLinkOpen);
                if (lastOpenBracket > 0) {
                    end = offset + lastOpenBracket;
                    chunk = markdown.substring(offset, end);
                }
            }
        }

        return new TruncateResult(chunk, end < total);
    }

    private long countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String formatSuccessResponse(CachedPage page, int offset, int returned, boolean hasMore, String slice) {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(page.getFinalUrl()).append("\n");
        if (StringUtils.hasText(page.getTitle())) {
            sb.append("Title: ").append(page.getTitle()).append("\n");
        }
        if (StringUtils.hasText(page.getByline())) {
            sb.append("Byline: ").append(page.getByline()).append("\n");
        }
        if (StringUtils.hasText(page.getContentType())) {
            sb.append("Content-Type: ").append(page.getContentType()).append("\n");
        }
        sb.append("Total-Length: ").append(page.getTotalLength()).append("\n");
        sb.append("Offset: ").append(offset).append("\n");
        sb.append("Returned-Range: [").append(offset).append(", ").append(offset + returned).append(")\n");
        sb.append("Has-More: ").append(hasMore).append("\n");
        sb.append("Original-Truncated: ").append(page.isOriginalResponseTruncated()).append("\n");
        sb.append("Strategy: ").append(page.getStrategy()).append("\n");
        if (page.getQualityWarnings() != null && !page.getQualityWarnings().isEmpty()) {
            sb.append("Quality-Warnings: ").append(String.join("; ", page.getQualityWarnings())).append("\n");
        }
        if (hasMore) {
            int nextOffset = offset + returned;
            sb.append("Next-Offset: ").append(nextOffset).append("\n");
            sb.append("提示: 内容已截断，如需继续阅读请用 offset=").append(nextOffset).append(" 再次调用。\n");
        }
        sb.append("\n---\n\n");
        sb.append(slice);
        return sb.toString();
    }

    private String formatEmptySliceResponse(CachedPage page, int offset) {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(page.getFinalUrl()).append("\n");
        sb.append("Total-Length: ").append(page.getTotalLength()).append("\n");
        sb.append("Offset: ").append(offset).append("\n");
        sb.append("Has-More: false\n");
        sb.append("提示: offset 已超出内容范围，无更多内容可读。\n");
        return sb.toString();
    }

    private String formatOverLimitResponse(CachedPage page, int offset) {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(page.getFinalUrl()).append("\n");
        sb.append("Total-Length: ").append(page.getTotalLength()).append("\n");
        sb.append("Offset: ").append(offset).append("\n");
        sb.append("Original-Truncated: true\n");
        sb.append("Has-More: false\n");
        sb.append("提示: 原始响应已达 5MB 上限，无法获取后续内容。\n");
        return sb.toString();
    }

    // ========== 缓存管理 ==========

    private String normalizeCacheKey(String url) {
        // 规范化：移除 fragment，统一小写 scheme/host/path，去除末尾斜杠
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            path = path.toLowerCase(Locale.ROOT);
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String key = (uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "")
                    + "://" + (uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "")
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                    + path
                    + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return key;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    private CachedPage cacheGet(String key) {
        CachedPage page = cache.get(key);
        if (page == null) {
            return null;
        }
        // TTL 检查
        if (System.currentTimeMillis() - page.getFetchedAt() > CACHE_TTL_MS) {
            cache.remove(key);
            return null;
        }
        return page;
    }

    private void cachePut(String key, CachedPage page) {
        // 容量淘汰
        if (cache.size() >= CACHE_MAX_SIZE) {
            evictOldest(CACHE_EVICT_COUNT);
        }
        cache.put(key, page);
    }

    private void evictOldest(int count) {
        // 按 fetchedAt 升序，删除最旧的 N 条
        List<Map.Entry<String, CachedPage>> entries = new ArrayList<>(cache.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().getFetchedAt(), b.getValue().getFetchedAt()));
        int toRemove = Math.min(count, entries.size());
        for (int i = 0; i < toRemove; i++) {
            cache.remove(entries.get(i).getKey());
        }
        log.info("缓存淘汰: 清除 {} 条最旧条目，剩余 {}", toRemove, cache.size());
    }

    // ========== 辅助 ==========

    private String pickTitle(PageMetadata metadata) {
        if (StringUtils.hasText(metadata.getTitle())) {
            return metadata.getTitle();
        }
        if (StringUtils.hasText(metadata.getOgTitle())) {
            return metadata.getOgTitle();
        }
        return null;
    }

    private void logAudit(FetchResult fetchResult, ExtractResult extractResult, int offset) {
        log.info("WebFetch 审计: url={}, strategy={}, totalLength={}, offset={}, " +
                        "originalTruncated={}, qualityWarnings={}",
                fetchResult.finalUrl,
                extractResult.strategy,
                extractResult.markdown != null ? extractResult.markdown.length() : 0,
                offset,
                fetchResult.responseTruncated,
                extractResult.qualityWarnings);
    }

    // ========== 内部数据结构 ==========

    private static class FetchResult {
        String errorMessage;
        String finalUrl;
        String contentType;
        String xRobotsTag;
        long contentLength;
        byte[] bodyBytes;
        boolean responseTruncated;
    }

    private static class ExtractResult {
        String markdown;
        String title;
        String byline;
        String strategy;
        List<String> qualityWarnings;
    }

    private static class TruncateResult {
        final String chunk;
        final boolean hasMore;

        TruncateResult(String chunk, boolean hasMore) {
            this.chunk = chunk;
            this.hasMore = hasMore;
        }
    }
}
