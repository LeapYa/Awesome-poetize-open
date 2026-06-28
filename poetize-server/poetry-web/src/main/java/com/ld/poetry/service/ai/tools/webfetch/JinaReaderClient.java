package com.ld.poetry.service.ai.tools.webfetch;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Jina Reader API 客户端 — SPA 渲染 fallback。
 * <p>
 * 端点：{@code https://r.jina.ai/{url-encoded-target-url}}
 * <p>
 * 认证（可选）：{@code Authorization: Bearer {jinaApiKey}}
 * <ul>
 *   <li><b>无 Key 模式</b>：20 RPM，永久免费，不消耗 Token 额度。推荐个人/轻度使用。超限排队等待。</li>
 *   <li><b>有 Key 模式</b>：500 RPM + 10M 免费 Token（注册即送），用完后可付费续费（$0.02/M Token）或退回无 Key 模式</li>
 * </ul>
 * <p>
 * 响应：纯文本 Markdown（Jina 服务端渲染 SPA + 调用 Readability 等价算法）
 * <p>
 * Jina 调用始终发往 {@code r.jina.ai} 公网域名，DNS 解析结果固定，
 * 不会触发用户控制的 URL 重定向。但工具服务端仍需对 Jina 返回的 Markdown 做长度上限校验。
 * <p>
 * 排队机制：两种模式均使用排队队列，超限时进入队列等待，前端可通过 requestId 查询排队位置。
 */
public class JinaReaderClient {

    private static final Logger log = LoggerFactory.getLogger(JinaReaderClient.class);

    private static final String JINA_ENDPOINT = "https://r.jina.ai/";

    /** Jina 返回过短视为渲染失败 */
    private static final int MIN_VALID_LENGTH = 500;

    /** Jina 响应体硬上限（防止恶意响应体） */
    private static final int MAX_RESPONSE_LENGTH = 5 * 1024 * 1024;

    /** 排队超时时间（秒）— 覆盖大多数排队场景 */
    private static final long QUEUE_TIMEOUT_SECONDS = 120;

    private final OkHttpClient httpClient;
    private final JinaRateLimiter rateLimiter;

    public JinaReaderClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
        this.rateLimiter = new JinaRateLimiter();
    }

    /**
     * 调用 Jina Reader 抓取并渲染目标 URL（含排队逻辑）。
     *
     * @param targetUrl 目标网页 URL（公网可访问）
     * @param apiKey    Jina API Key（可选；为空时走免费 20 RPM 模式 + 排队）
     * @return {@code Optional.of(markdown)} 当响应非空且长度 &gt; 500；否则 {@code Optional.empty()}
     */
    public Optional<String> fetch(String targetUrl, String apiKey) {
        return fetch(targetUrl, apiKey, null);
    }

    /**
     * 调用 Jina Reader 抓取并渲染目标 URL（含排队逻辑）。
     *
     * @param targetUrl     目标网页 URL（公网可访问）
     * @param apiKey        Jina API Key（可选；为空时走免费 20 RPM 模式 + 排队）
     * @param queueInfoOut  可选输出对象：传入一个 StringBuilder，方法会追加排队信息（requestId、位置、等待时间）
     * @return {@code Optional.of(markdown)} 当响应非空且长度 &gt; 500；否则 {@code Optional.empty()}
     */
    public Optional<String> fetch(String targetUrl, String apiKey, StringBuilder queueInfoOut) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Optional.empty();
        }

        boolean hasApiKey = apiKey != null && !apiKey.isEmpty();

        // ===== 第 1 步：获取调用许可（含排队） =====
        JinaRateLimiter.AcquireResult acquireResult = rateLimiter.acquire(hasApiKey);
        if (!acquireResult.isAcquired()) {
            log.warn("Jina 调用被拒绝: status={}, position={}", acquireResult.getStatus(), acquireResult.getQueuePosition());
            if (queueInfoOut != null) {
                queueInfoOut.append("Jina 服务繁忙（").append(acquireResult.getStatus())
                        .append("），请稍后重试。");
            }
            return Optional.empty();
        }

        String requestId = acquireResult.getRequestId();
        int queuePosition = acquireResult.getQueuePosition();
        long estimatedWaitMs = acquireResult.getEstimatedWaitMs();

        // 记录排队信息供调用方使用
        if (queueInfoOut != null && requestId != null) {
            queueInfoOut.append("requestId=").append(requestId);
            queueInfoOut.append(",position=").append(queuePosition);
            queueInfoOut.append(",estimatedWaitMs=").append(estimatedWaitMs);
        }

        // ===== 第 2 步：如果排队中，阻塞等待轮到自己 =====
        if (requestId != null) {
            log.info("Jina 请求排队中: requestId={}, position={}, estimatedWait={}ms",
                    requestId, queuePosition, estimatedWaitMs);
            boolean acquired = rateLimiter.waitForTurn(requestId, QUEUE_TIMEOUT_SECONDS);
            if (!acquired) {
                log.warn("Jina 排队超时: requestId={}", requestId);
                if (queueInfoOut != null) {
                    queueInfoOut.append(",result=TIMEOUT");
                }
                return Optional.empty();
            }
            if (queueInfoOut != null) {
                queueInfoOut.append(",result=ACQUIRED");
            }
        }

        // ===== 第 3 步：调用 Jina Reader API =====
        return callJinaApi(targetUrl, apiKey, hasApiKey, queueInfoOut);
    }

    /**
     * 实际调用 Jina Reader API。
     * <p>
     * 检测 HTTP 429（服务端限流）时，立即触发 {@link JinaRateLimiter#penalize(boolean)}
     * 把本地窗口塞满，避免后续请求继续撞 Jina 限流。本地窗口将在一个窗口期（默认 60 秒）后恢复。
     */
    private Optional<String> callJinaApi(String targetUrl, String apiKey, boolean hasApiKey,
                                         StringBuilder queueInfoOut) {
        String encodedUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
        Request.Builder reqBuilder = new Request.Builder()
                .url(JINA_ENDPOINT + encodedUrl)
                .header("Accept", "text/plain")
                .header("User-Agent", "Poetize-WebFetch/1.0 (Jina Reader fallback)");

        if (hasApiKey) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        Request request = reqBuilder.build();
        long start = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            int duration = (int) (System.currentTimeMillis() - start);

            // 429 自适应惩罚：本地算法"超前"导致 Jina 实际拒绝
            if (response.code() == 429) {
                String retryAfter = response.header("Retry-After");
                log.warn("Jina 返回 429（服务端限流），触发本地窗口惩罚: url={}, retryAfter={}",
                        targetUrl, retryAfter != null ? retryAfter : "未提供");
                rateLimiter.penalize(hasApiKey);
                if (queueInfoOut != null) {
                    queueInfoOut.append(",jina=429");
                    if (retryAfter != null) {
                        queueInfoOut.append(",retryAfter=").append(retryAfter);
                    }
                }
                return Optional.empty();
            }

            if (!response.isSuccessful()) {
                log.warn("Jina Reader 调用失败: status={}, duration={}ms, url={}",
                        response.code(), duration, targetUrl);
                return Optional.empty();
            }
            ResponseBody body = response.body();
            if (body == null) {
                return Optional.empty();
            }

            String markdown = body.string();
            if (markdown == null || markdown.length() <= MIN_VALID_LENGTH) {
                log.warn("Jina Reader 响应过短: len={}, url={}",
                        markdown != null ? markdown.length() : 0, targetUrl);
                return Optional.empty();
            }
            if (markdown.length() > MAX_RESPONSE_LENGTH) {
                markdown = markdown.substring(0, MAX_RESPONSE_LENGTH);
                log.warn("Jina Reader 响应体超限，已截断到 5MB: origLen={}, url={}",
                        markdown.length(), targetUrl);
            }
            log.info("Jina Reader 调用成功: len={}, duration={}ms, url={}",
                    markdown.length(), duration, targetUrl);
            return Optional.of(markdown);
        } catch (IOException e) {
            int duration = (int) (System.currentTimeMillis() - start);
            log.warn("Jina Reader 调用异常: duration={}ms, url={}, error={}",
                    duration, targetUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查询 requestId 的当前排队位置。
     *
     * @param requestId 排队 ID（来自 fetch 方法的 queueInfoOut 输出）
     * @return 排队位置（0 = 正在执行或已完成；>0 = 前面还有 N 个请求）
     */
    public int getQueuePosition(String requestId) {
        return rateLimiter.getQueuePosition(requestId);
    }

    /**
     * 查询指定 requestId 的排队详情（含实际间隔）。
     */
    public JinaRateLimiter.QueueEntryInfo getQueueEntryInfo(String requestId) {
        return rateLimiter.getQueueEntryInfo(requestId);
    }

    /**
     * 获取队列状态摘要。
     */
    public JinaRateLimiter.QueueStatus getQueueStatus() {
        return rateLimiter.getQueueStatus();
    }

    /**
     * 获取队列快照（所有排队中的请求详情），供前端轮询展示。
     */
    public List<JinaRateLimiter.QueueEntryInfo> getQueueSnapshot() {
        return rateLimiter.getQueueSnapshot();
    }

    /**
     * 判断当前是否有请求正在排队。
     */
    public boolean isQueueActive() {
        return rateLimiter.isQueueActive();
    }
}
