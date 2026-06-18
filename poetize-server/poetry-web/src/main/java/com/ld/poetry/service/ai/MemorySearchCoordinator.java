package com.ld.poetry.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆搜索协调器 — 协调后端工具调用与前端 IndexedDB 搜索。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>AI 调用 search_memory 工具 → 工具生成 requestId，注册 CompletableFuture</li>
 *   <li>工具通过 SSE 发送 memory_search 事件给前端，然后阻塞等待</li>
 *   <li>前端搜索 IndexedDB 后，POST 结果到 /ai/chat/memorySearchResult</li>
 *   <li>Controller 调用 {@link #complete} 解除工具阻塞</li>
 *   <li>工具返回结果给 AI，AI 继续生成回复</li>
 * </ol>
 * <p>
 * 聊天记录不存服务器，搜索在客户端 IndexedDB 中进行。
 */
@Component
public class MemorySearchCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MemorySearchCoordinator.class);

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 注册一个待完成的记忆搜索请求
     *
     * @param requestId 唯一请求ID
     * @return CompletableFuture，工具方法阻塞等待其完成
     */
    public CompletableFuture<String> register(String requestId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);
        log.debug("记忆搜索请求已注册: requestId={}", requestId);
        return future;
    }

    /**
     * 完成记忆搜索请求（由 Controller 在收到前端结果后调用）
     *
     * @param requestId 唯一请求ID
     * @param result    前端搜索结果
     * @return true 如果请求存在且已完成
     */
    public boolean complete(String requestId, String result) {
        CompletableFuture<String> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(result);
            log.debug("记忆搜索请求已完成: requestId={}", requestId);
            return true;
        }
        log.warn("记忆搜索请求不存在或已过期: requestId={}", requestId);
        return false;
    }

    /**
     * 取消记忆搜索请求（超时或异常时调用）
     *
     * @param requestId 唯一请求ID
     */
    public void cancel(String requestId) {
        CompletableFuture<String> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.cancel(true);
            log.debug("记忆搜索请求已取消: requestId={}", requestId);
        }
    }

    /**
     * 获取当前待完成的请求数（监控用）
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }
}
