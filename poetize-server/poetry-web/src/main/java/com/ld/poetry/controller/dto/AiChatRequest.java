package com.ld.poetry.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天请求 DTO（Record）
 * 提供类型安全和自动验证
 *
 * @param message         用户消息内容（必填，最大 10000 字符）
 * @param conversationId  会话 ID（可选，默认 "default"）
 * @param userId          用户 ID（可选，默认 "anonymous"）
 * @param history         聊天历史记录（可选）。当 baseHistoryHash 与服务端缓存匹配时为增量（仅末尾新增条目）；
 *                         否则为完整历史。服务端据此决定拼接策略
 * @param pageContext     页面上下文（可选，用于流式聊天）
 * @param images          用户上传的图片URL列表（可选，需视觉模型支持）
 * @param documents       用户上传的文档附件列表（可选，每项含 name/type/size/content）
 * @param currentPage     用户当前浏览的页面上下文（可选，供 get_current_page 工具按需读取）
 * @param baseHistoryHash 前端上次响应收到的历史哈希（可选）。用于增量协议：
 *                         服务端比对 Redis 中的历史末尾哈希，匹配则用缓存历史 + 增量拼接，
 *                         避免每轮重传完整历史（含工具调用结果，可能达数十 KB）
 */
public record AiChatRequest(
        @NotBlank(message = "消息内容不能为空") @Size(max = 10000, message = "消息长度不能超过 10000 字符") String message,

        String conversationId,

        String userId,

        List<Map<String, Object>> history,

        Map<String, Object> pageContext,

        List<String> images,

        List<Map<String, Object>> documents,

        Map<String, Object> currentPage,

        String baseHistoryHash) {
    /**
     * Compact constructor：提供默认值，保证非 null 字段安全
     */
    public AiChatRequest {
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = "default";
        }
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }
        if (history == null) {
            history = List.of();
        }
        if (images == null) {
            images = List.of();
        }
        if (documents == null) {
            documents = List.of();
        }
    }
}
