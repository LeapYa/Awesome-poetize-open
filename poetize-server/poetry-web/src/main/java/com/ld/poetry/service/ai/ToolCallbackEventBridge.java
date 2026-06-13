package com.ld.poetry.service.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 包装 Spring AI ToolCallback，在工具执行前后发出 SSE 事件。
 */
@Component
@Slf4j
public class ToolCallbackEventBridge {

    public static final String SSE_EMITTER_CONTEXT_KEY = "sseEmitter";
    public static final String CONVERSATION_ID_CONTEXT_KEY = "conversationId";
    public static final String USER_ID_CONTEXT_KEY = "userId";
    public static final String STREAM_CANCELLED_CONTEXT_KEY = "streamCancelled";

    private final JsonMapper objectMapper;

    public ToolCallbackEventBridge(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ToolCallback wrap(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                Map<String, Object> contextMap = toolContext != null
                        ? toolContext.getContext()
                        : Map.of();
                SseEmitter emitter = extractEmitter(contextMap);
                String conversationId = extractConversationId(contextMap);
                String userId = extractUserId(contextMap);
                AtomicBoolean streamCancelled = extractStreamCancelled(contextMap);
                String toolName = getToolDefinition().name();
                long startedAt = System.currentTimeMillis();

                if (isStreamCancelled(streamCancelled, null)) {
                    log.info("AI工具调用已跳过: userId={}, conversationId={}, tool={}",
                            userId, conversationId, toolName);
                    return buildCancelledToolResult(toolName);
                }

                log.info("AI工具调用开始: userId={}, conversationId={}, tool={}, arguments={}",
                        userId, conversationId, toolName, abbreviateForLog(toolInput, 200));

                sendEvent(emitter, streamCancelled, "tool_call", Map.of(
                        "conversationId", conversationId,
                        "tool", toolName,
                        "status", "executing",
                        "arguments", normalizeJson(toolInput)));

                try {
                    String result = delegate.call(toolInput, toolContext);
                    if (isStreamCancelled(streamCancelled, null)) {
                        log.info("AI工具调用已取消: userId={}, conversationId={}, tool={}, durationMs={}",
                                userId, conversationId, toolName, System.currentTimeMillis() - startedAt);
                        return buildCancelledToolResult(toolName);
                    }
                    sendEvent(emitter, streamCancelled, "tool_result", Map.of(
                            "conversationId", conversationId,
                            "tool", toolName,
                            "status", "completed",
                            "result", normalizeJson(result)));
                    log.info("AI工具调用完成: userId={}, conversationId={}, tool={}, durationMs={}, resultLength={}",
                            userId, conversationId, toolName, System.currentTimeMillis() - startedAt,
                            result != null ? result.length() : 0);
                    return result;
                } catch (RuntimeException ex) {
                    if (isStreamCancelled(streamCancelled, ex)) {
                        log.info("AI工具调用已取消: userId={}, conversationId={}, tool={}, durationMs={}",
                                userId, conversationId, toolName, System.currentTimeMillis() - startedAt);
                        return buildCancelledToolResult(toolName);
                    }

                    String safeErrorMessage = toSafeToolErrorMessage(ex);
                    log.error(
                            "AI工具调用失败，已降级为失败结果继续对话: userId={}, conversationId={}, tool={}, durationMs={}, arguments={}, error={}",
                            userId,
                            conversationId,
                            toolName,
                            System.currentTimeMillis() - startedAt,
                            abbreviateForLog(toolInput, 200),
                            ex.getMessage(),
                            ex);
                    sendEvent(emitter, streamCancelled, "tool_result", Map.of(
                            "conversationId", conversationId,
                            "tool", toolName,
                            "status", "failed",
                            "error", safeErrorMessage));
                    return buildFailedToolResult(toolName, safeErrorMessage);
                }
            }
        };
    }

    private SseEmitter extractEmitter(Map<String, Object> contextMap) {
        Object emitter = contextMap.get(SSE_EMITTER_CONTEXT_KEY);
        return emitter instanceof SseEmitter sseEmitter ? sseEmitter : null;
    }

    private String extractConversationId(Map<String, Object> contextMap) {
        Object conversationId = contextMap.get(CONVERSATION_ID_CONTEXT_KEY);
        return conversationId != null ? conversationId.toString() : "";
    }

    private String extractUserId(Map<String, Object> contextMap) {
        Object userId = contextMap.get(USER_ID_CONTEXT_KEY);
        return userId != null ? userId.toString() : "anonymous";
    }

    private AtomicBoolean extractStreamCancelled(Map<String, Object> contextMap) {
        Object cancelled = contextMap.get(STREAM_CANCELLED_CONTEXT_KEY);
        return cancelled instanceof AtomicBoolean atomicBoolean ? atomicBoolean : null;
    }

    private Object normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JacksonException ignored) {
            return value;
        }
    }

    private String buildFailedToolResult(String toolName, String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("tool", toolName);
        payload.put("status", "failed");
        payload.put("error", errorMessage);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ignored) {
            return "{\"success\":false,\"status\":\"failed\",\"error\":\"工具暂时不可用\"}";
        }
    }

    private String buildCancelledToolResult(String toolName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("tool", toolName);
        payload.put("status", "cancelled");
        payload.put("error", "客户端已断开");

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ignored) {
            return "{\"success\":false,\"status\":\"cancelled\",\"error\":\"客户端已断开\"}";
        }
    }

    private String toSafeToolErrorMessage(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException) {
            return "工具参数无效";
        }
        return "工具暂时不可用";
    }

    private String abbreviateForLog(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private boolean isStreamCancelled(AtomicBoolean streamCancelled, Throwable throwable) {
        return streamCancelled != null && streamCancelled.get()
                || SseRequestUtils.isClientCancellation(throwable);
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean streamCancelled, String eventName, Map<String, Object> payload) {
        if (emitter == null) {
            return;
        }

        try {
            Map<String, Object> data = new LinkedHashMap<>(payload);
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception ex) {
            if (streamCancelled != null) {
                streamCancelled.set(true);
            }
        }
    }
}
