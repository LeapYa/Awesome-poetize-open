package com.ld.poetry.service.ai.dto;

import com.ld.poetry.service.ai.rag.dto.AdminNavigationAction;

import java.util.List;

/**
 * @param historyHash 本次响应处理后完整历史的哈希。前端下次请求时作为 baseHistoryHash 上送，
 *                    命中服务端 Redis 缓存即可走增量协议。null 表示本次未触发缓存写回（如流式接口）
 * @param cacheMiss  true 表示前端上送的 baseHistoryHash 在服务端已失效（Redis miss / 哈希不匹配）。
 *                    前端收到后应清空 lastHistoryHash 并用完整历史重试当前请求一次（防死循环）。
 *                    此标志与 content 互斥：cacheMiss=true 时 content 为空，模型不会被调用。
 */
public record AiChatResponsePayload(
        String content,
        String reasoningContent,
        List<AdminNavigationAction> actions,
        String historyHash,
        boolean cacheMiss) {

    public static AiChatResponsePayload of(String content, List<AdminNavigationAction> actions) {
        return of(content, "", actions, null, false);
    }

    public static AiChatResponsePayload of(String content, String reasoningContent, List<AdminNavigationAction> actions) {
        return of(content, reasoningContent, actions, null, false);
    }

    public static AiChatResponsePayload of(String content, String reasoningContent,
            List<AdminNavigationAction> actions, String historyHash) {
        return of(content, reasoningContent, actions, historyHash, false);
    }

    public static AiChatResponsePayload of(String content, String reasoningContent,
            List<AdminNavigationAction> actions, String historyHash, boolean cacheMiss) {
        return new AiChatResponsePayload(
                content != null ? content : "",
                reasoningContent != null ? reasoningContent : "",
                actions != null ? actions : List.of(),
                historyHash,
                cacheMiss);
    }

    /**
     * 构造 cacheMiss 短路响应：模型不会被调用，前端需用完整历史重试。
     */
    public static AiChatResponsePayload cacheMissShortcut() {
        return new AiChatResponsePayload("", "", List.of(), null, true);
    }
}
