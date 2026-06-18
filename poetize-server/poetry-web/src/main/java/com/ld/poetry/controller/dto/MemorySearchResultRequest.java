package com.ld.poetry.controller.dto;

/**
 * 记忆搜索结果请求 DTO — 前端搜索完 IndexedDB 后提交结果。
 *
 * @param requestId 对应 memory_search SSE 事件中的 requestId
 * @param result    前端搜索到的记忆文本（AI 将作为工具结果使用）
 */
public record MemorySearchResultRequest(
        String requestId,
        String result
) {
}
