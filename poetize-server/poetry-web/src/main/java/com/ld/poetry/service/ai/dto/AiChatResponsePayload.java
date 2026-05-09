package com.ld.poetry.service.ai.dto;

import com.ld.poetry.service.ai.rag.dto.AdminNavigationAction;

import java.util.List;

public record AiChatResponsePayload(
        String content,
        String reasoningContent,
        List<AdminNavigationAction> actions) {

    public static AiChatResponsePayload of(String content, List<AdminNavigationAction> actions) {
        return of(content, "", actions);
    }

    public static AiChatResponsePayload of(String content, String reasoningContent, List<AdminNavigationAction> actions) {
        return new AiChatResponsePayload(
                content != null ? content : "",
                reasoningContent != null ? reasoningContent : "",
                actions != null ? actions : List.of());
    }
}
