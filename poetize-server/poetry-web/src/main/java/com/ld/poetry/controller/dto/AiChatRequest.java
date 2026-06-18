package com.ld.poetry.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天请求 DTO（Record）
 * 替代原 Map<String, Object> 请求体，提供类型安全和自动验证
 *
 * @param message        用户消息内容（必填，最大 10000 字符）
 * @param conversationId 会话 ID（可选，默认 "default"）
 * @param userId         用户 ID（可选，默认 "anonymous"）
 * @param history        聊天历史记录（可选）
 * @param pageContext    页面上下文（可选，用于流式聊天）
 * @param images         用户上传的图片URL列表（可选，需视觉模型支持）
 */
public record AiChatRequest(
        @NotBlank(message = "消息内容不能为空") @Size(max = 10000, message = "消息长度不能超过 10000 字符") String message,

        String conversationId,

        String userId,

        List<Map<String, String>> history,

        Map<String, Object> pageContext,

        List<String> images) {
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
    }
}
