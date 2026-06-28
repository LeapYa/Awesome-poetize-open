package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.ai.AiChatService;
import com.ld.poetry.service.ai.tools.WebFetchTools;
import com.ld.poetry.service.ai.tools.webfetch.JinaRateLimiter;
import com.ld.poetry.service.ai.tools.webfetch.JinaReaderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jina Reader 排队状态查询控制器。
 * <p>
 * 当无 API Key 模式下多个用户请求超过 20 RPM 限制时，请求进入排队队列。
 * 前端可在 fetchWebPage 工具调用期间轮询此端点，实时显示排队位置和预计等待时间。
 * <p>
 * 端点：
 * <ul>
 *   <li>GET /ai/jina-queue/status?requestId=xxx — 查询指定请求的排队位置</li>
 *   <li>GET /ai/jina-queue/status — 获取完整队列快照（所有排队条目 + 统计）</li>
 * </ul>
 */
@RestController
@RequestMapping("/ai/jina-queue")
public class JinaQueueController {

    private static final Logger logger = LoggerFactory.getLogger(JinaQueueController.class);

    @Autowired
    private WebFetchTools webFetchTools;

    @Autowired
    private AiChatService aiChatService;

    /**
     * 查询 Jina Reader 排队状态。
     * <p>
     * 若提供 requestId 参数，返回该请求的排队位置和预计等待时间；
     * 否则返回完整队列快照（所有排队条目 + 统计信息）。
     *
     * @param requestId 可选，排队 ID（来自工具返回的排队信息）
     * @return 排队状态
     */
    @GetMapping("/status")
    public PoetryResult<Map<String, Object>> getQueueStatus(
            @RequestParam(required = false) String requestId) {

        // 校验 AI 聊天是否启用（与 AiChatController 保持一致，不使用 @LoginCheck
        // 因为 AI 聊天配置 require_login=false 时允许未登录用户聊天）
        Map<String, Object> chatStatus = aiChatService.checkStatus();
        if (!Boolean.TRUE.equals(chatStatus.get("enabled"))) {
            return PoetryResult.fail("AI聊天未启用");
        }

        JinaReaderClient jinaClient = webFetchTools.getJinaReaderClient();
        if (jinaClient == null) {
            return PoetryResult.fail("Jina Reader 未初始化");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // 指定 requestId：返回该请求的排队详情（含实际间隔计算）
        if (requestId != null && !requestId.isEmpty()) {
            JinaRateLimiter.QueueEntryInfo entryInfo = jinaClient.getQueueEntryInfo(requestId);
            if (entryInfo != null) {
                result.put("requestId", requestId);
                result.put("position", entryInfo.getPosition());
                result.put("waitedMs", entryInfo.getWaitedMs());
                result.put("estimatedWaitMs", entryInfo.getEstimatedWaitMs());
                result.put("status", "QUEUED");
            } else {
                result.put("requestId", requestId);
                result.put("position", 0);
                result.put("status", "COMPLETED_OR_NOT_FOUND");
            }
            return PoetryResult.success(result);
        }

        // 无 requestId：返回完整队列快照
        JinaRateLimiter.QueueStatus status = jinaClient.getQueueStatus();
        List<JinaRateLimiter.QueueEntryInfo> entries = jinaClient.getQueueSnapshot();

        result.put("queueSize", status.getCurrentQueueSize());
        result.put("totalQueued", status.getTotalQueued());
        result.put("totalProcessed", status.getTotalProcessed());
        result.put("totalTimedOut", status.getTotalTimedOut());
        result.put("queueActive", jinaClient.isQueueActive());
        result.put("entries", entries);

        // 计算总体预计等待时间（队尾条目的 estimatedWaitMs）
        long maxEstimatedWaitMs = 0;
        for (JinaRateLimiter.QueueEntryInfo entry : entries) {
            if (entry.getEstimatedWaitMs() > maxEstimatedWaitMs) {
                maxEstimatedWaitMs = entry.getEstimatedWaitMs();
            }
        }
        result.put("maxEstimatedWaitMs", maxEstimatedWaitMs);

        return PoetryResult.success(result);
    }
}
