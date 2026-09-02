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
import java.util.List;
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
    /**
     * 当前用户显示名称（用户名），匿名用户为空。供需要身份感知的工具使用。
     */
    public static final String USER_NAME_CONTEXT_KEY = "userName";
    /**
     * 当前用户类型（0=站长, 1=管理员, 2=普通用户），匿名用户为 null。
     */
    public static final String USER_TYPE_CONTEXT_KEY = "userType";
    /**
     * 当前用户是否为站长或管理员（可管理 Skill 等敏感资源）。
     */
    public static final String USER_IS_ADMIN_CONTEXT_KEY = "userIsAdmin";

    /**
     * 用户当前浏览的页面上下文（标题、类型、URL、正文等）。
     * <p>
     * 由前端随聊天请求一并提交，供 {@code get_current_page} 工具按需读取，
     * 避免在用户未手动附加页面、却针对"当前页面"提问时凭空猜测。
     */
    public static final String CURRENT_PAGE_CONTEXT_KEY = "currentPage";

    /**
     * 工具调用记录器（可选）：值为 {@code List<Map<String,Object>>}。
     * <p>
     * 若 toolContext 中存在该 key，每次工具执行结束（completed/failed/cancelled）
     * 会向该 list 追加一条 {@code {tool, status, durationMs}} 记录，
     * 供 AI 审计日志在调用结束后读取，写入 detail.toolCalls。
     * <p>
     * 不存在该 key 时不记录，对原有行为无影响。
     */
    public static final String TOOL_CALL_RECORDER_CONTEXT_KEY = "toolCallRecorder";

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
                    recordToolCall(contextMap, toolName, "skipped", startedAt);
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
                        recordToolCall(contextMap, toolName, "cancelled", startedAt);
                        return buildCancelledToolResult(toolName);
                    }
                    sendEvent(emitter, streamCancelled, "tool_result", Map.of(
                            "conversationId", conversationId,
                            "tool", toolName,
                            "status", "completed",
                            "result", normalizeJson(result)));
                    long durationMs = System.currentTimeMillis() - startedAt;
                    log.info("AI工具调用完成: userId={}, conversationId={}, tool={}, durationMs={}, resultLength={}",
                            userId, conversationId, toolName, durationMs,
                            result != null ? result.length() : 0);
                    recordToolCall(contextMap, toolName, "completed", startedAt);
                    return appendExecutionTime(result, durationMs);
                } catch (RuntimeException ex) {
                    if (isStreamCancelled(streamCancelled, ex)) {
                        log.info("AI工具调用已取消: userId={}, conversationId={}, tool={}, durationMs={}",
                                userId, conversationId, toolName, System.currentTimeMillis() - startedAt);
                        recordToolCall(contextMap, toolName, "cancelled", startedAt);
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
                    recordToolCall(contextMap, toolName, "failed", startedAt);
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

    /**
     * 向 toolContext 中的工具调用记录器追加一条记录（若存在）。
     * <p>
     * 线程安全由 {@code synchronized List} 保证：工具执行可能并发（ToolCallingAdvisor 默认并行），
     * 使用 {@code Collections.synchronizedList} 包装的 list 即可。
     * 失败时静默忽略，不影响工具调用主流程。
     */
    @SuppressWarnings("unchecked")
    private void recordToolCall(Map<String, Object> contextMap, String toolName, String status, long startedAt) {
        try {
            Object recorder = contextMap.get(TOOL_CALL_RECORDER_CONTEXT_KEY);
            if (!(recorder instanceof List<?> list)) {
                return;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tool", toolName);
            entry.put("status", status);
            entry.put("durationMs", System.currentTimeMillis() - startedAt);
            ((List<Map<String, Object>>) list).add(entry);
        } catch (Exception ignored) {
            // 记录失败不影响工具调用主流程
        }
    }

    /**
     * 规范化工具调用相关字段，保留原始 JSON 字符串形式。
     * <p>
     * 历史实现曾把 JSON 字符串反序列化为 Map/对象，导致：
     * <ol>
     *   <li>SSE {@code tool_call} 事件的 {@code arguments} 变成对象</li>
     *   <li>前端 JSON.parse 后仍是对象，存入 segments.arguments</li>
     *   <li>前端发回后端时序列化为 Map，后端 {@code objToString} 输出 {@code {key=value}}</li>
     *   <li>模型 API 收到非 JSON 的 arguments → 400 unexpected character</li>
     * </ol>
     * 现统一返回字符串：合法 JSON 原样返回，空值返回 {@code ""}，
     * 前端若需展示可自行 JSON.parse。
     */
    private String normalizeJson(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    /**
     * 在返回给模型的工具结果末尾附带执行耗时。
     * <p>
     * SSE {@code tool_result} 事件仍发原始结果（前端展示不受影响），
     * 仅模型侧的工具响应消息带耗时，供其感知工具开销。
     */
    private String appendExecutionTime(String result, long durationMs) {
        String body = result != null ? result : "";
        return body + "\n（工具执行耗时 " + durationMs + " 毫秒）";
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
            // 与 AiChatService 的 SSE 心跳互斥：SseEmitter 非线程安全，并发 send 会损坏 SSE 帧
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            }
        } catch (Exception ex) {
            if (streamCancelled != null) {
                streamCancelled.set(true);
            }
        }
    }
}
