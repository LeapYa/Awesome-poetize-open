package com.ld.poetry.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.AiChatRequest;
import com.ld.poetry.controller.dto.MemorySearchResultRequest;
import com.ld.poetry.service.ai.AiChatService;
import com.ld.poetry.service.ai.dto.AiChatResponsePayload;
import com.ld.poetry.utils.image.ImageCompressUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 聊天控制器
 * 替代 Python 端 ai_chat_api.py 中的聊天 API 路由
 *
 * 端点对照：
 * GET  /ai/chat/checkStatus        → check_ai_chat_status_route
 * POST /ai/chat/sendMessage        → send_chat_message
 * POST /ai/chat/sendMessageStream  → send_chat_message_stream (POST)
 * GET  /ai/chat/sendStreamMessage  → send_stream_message (GET, EventSource 兼容)
 */
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private static final Logger logger = LoggerFactory.getLogger(AiChatController.class);
    private static final JsonMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private com.ld.poetry.service.ai.MemorySearchCoordinator memorySearchCoordinator;

    /**
     * 检查 AI 聊天状态
     */
    @GetMapping("/checkStatus")
    public PoetryResult<Map<String, Object>> checkStatus() {
        Map<String, Object> status = aiChatService.checkStatus();
        return PoetryResult.success(status);
    }

    /**
     * 非流式聊天
     * 使用类型安全的 {@link AiChatRequest} Record DTO，避免 Map 强制转换风险
     */
    @PostMapping("/sendMessage")
    public PoetryResult<Map<String, Object>> sendMessage(@Valid @RequestBody AiChatRequest request) {
        try {
            AiChatResponsePayload response = aiChatService.chat(
                    request.message(),
                    request.history(),
                    request.conversationId(),
                    request.userId(),
                    request.pageContext(),
                    request.images());

            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("content", response != null ? response.content() : "");
            payload.put("reasoningContent", response != null ? response.reasoningContent() : "");
            payload.put("actions", response != null ? response.actions() : List.of());
            payload.put("conversationId", request.conversationId());
            return PoetryResult.success(payload);
        } catch (IllegalArgumentException e) {
            // 业务验证错误（如消息过长、频率限制）：可以将具体原因告知用户
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            // 系统内部错误：只记录日志，不向前端暴露内部信息
            logger.error("非流式聊天失败", e);
            return PoetryResult.fail("AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 流式聊天（SSE） - POST 方式
     *
     * SSE 事件协议：
     * event:start    → {conversationId}
     * data:          → {content: "..."}（文本 chunk）
     * event:complete → {conversationId, fullResponse}（完成）
     * event:error    → {message}（错误）
     */
    @PostMapping(value = "/sendMessageStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@Valid @RequestBody AiChatRequest request) {
        try {
            return aiChatService.streamChat(
                    request.message(),
                    request.history(),
                    request.conversationId(),
                    request.userId(),
                    request.pageContext(),
                    request.images());

        } catch (IllegalArgumentException e) {
            // 业务验证错误：通过 SSE error 事件将原因告知客户端
            return buildErrorEmitter(e.getMessage());
        } catch (Exception e) {
            // 系统内部错误：只记录日志，向前端返回通用错误提示
            logger.error("流式聊天初始化失败", e);
            return buildErrorEmitter("AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 流式聊天（SSE） - GET 方式（EventSource API 兼容）
     *
     * 前端可使用 new EventSource('/ai/chat/sendStreamMessage?message=...')
     * 参数通过 query string 传递，history/context 为 JSON 字符串
     */
    @GetMapping(value = "/sendStreamMessage", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendStreamMessage(
            @RequestParam String message,
            @RequestParam(defaultValue = "") String conversationId,
            @RequestParam(defaultValue = "[]") String history,
            @RequestParam(defaultValue = "{}") String context,
            @RequestParam(defaultValue = "anonymous") String userId) {
        try {
            // 解析 JSON 参数
            List<Map<String, String>> chatHistory = parseHistory(history);
            Map<String, Object> pageContext = parseContext(context);

            if (conversationId == null || conversationId.isBlank()) {
                conversationId = "conv_" + System.currentTimeMillis();
            }

            return aiChatService.streamChat(message, chatHistory, conversationId, userId, pageContext, List.of());

        } catch (IllegalArgumentException e) {
            return buildErrorEmitter(e.getMessage());
        } catch (Exception e) {
            logger.error("GET 流式聊天初始化失败", e);
            return buildErrorEmitter("AI 服务暂时不可用，请稍后重试");
        }
    }

    // ========== 内部辅助方法 ==========

    /**
     * 压缩图片并返回 base64 data URL（不持久化到服务器存储）
     * <p>
     * 前端上传图片文件，服务端使用 ImageCompressUtil 进行高质量压缩
     * （尺寸缩放 + 质量压缩 + 可选 WebP 转换），返回 base64 data URL。
     * 前端将 base64 存入 IndexedDB，发送聊天时内联到请求体。
     * <p>
     * 限制：单张 5MB，压缩后返回 base64。
     */
    @PostMapping(value = "/compressImage", produces = MediaType.APPLICATION_JSON_VALUE)
    public PoetryResult<Map<String, Object>> compressImage(@RequestParam("file") MultipartFile file) {
        // 校验 AI 聊天是否启用（该端点不经过 AiChatService，需独立校验）
        Map<String, Object> status = aiChatService.checkStatus();
        if (!Boolean.TRUE.equals(status.get("enabled"))) {
            return PoetryResult.fail("AI聊天未启用");
        }
        if (file == null || file.isEmpty()) {
            return PoetryResult.fail("图片文件为空");
        }
        // 限制单张 5MB
        if (file.getSize() > 5L * 1024 * 1024) {
            return PoetryResult.fail("图片大小不能超过5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return PoetryResult.fail("只能上传图片文件");
        }

        try {
            ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);
            String mimeType = result.getContentType();
            String base64 = Base64.getEncoder().encodeToString(result.getData());
            String dataUrl = "data:" + mimeType + ";base64," + base64;

            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("dataUrl", dataUrl);
            payload.put("mimeType", mimeType);
            payload.put("originalSize", result.getOriginalSize());
            payload.put("compressedSize", result.getCompressedSize());
            payload.put("compressionRatio", result.getCompressionRatio());
            return PoetryResult.success(payload);
        } catch (Exception e) {
            logger.error("图片压缩失败", e);
            return PoetryResult.fail("图片压缩失败: " + e.getMessage());
        }
    }

    /**
     * 接收前端记忆搜索结果 — 配合 MemorySearchTool 的两轮工具调用。
     * <p>
     * 前端收到 memory_search SSE 事件后，搜索本地 IndexedDB/localStorage，
     * 将结果 POST 到此端点，解除工具阻塞，AI 继续生成回复。
     */
    @PostMapping(value = "/memorySearchResult", produces = MediaType.APPLICATION_JSON_VALUE)
    public PoetryResult<Void> submitMemorySearchResult(@RequestBody MemorySearchResultRequest request) {
        if (request == null || request.requestId() == null || request.requestId().isBlank()) {
            return PoetryResult.fail("requestId 不能为空");
        }
        boolean success = memorySearchCoordinator.complete(request.requestId(), request.result());
        if (!success) {
            return PoetryResult.fail("无效或已过期的搜索请求");
        }
        return PoetryResult.success(null);
    }

    private List<Map<String, String>> parseHistory(String historyJson) {
        try {
            if (historyJson == null || historyJson.isBlank() || "[]".equals(historyJson)) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(historyJson, new TypeReference<>() {});
        } catch (Exception e) {
            logger.warn("解析 history 参数失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseContext(String contextJson) {
        try {
            if (contextJson == null || contextJson.isBlank() || "{}".equals(contextJson)) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(contextJson, new TypeReference<>() {});
        } catch (Exception e) {
            logger.warn("解析 context 参数失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 构建一个立即发送 error 事件后关闭的 SseEmitter
     */
    private SseEmitter buildErrorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(5000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message)));
            emitter.complete();
        } catch (Exception ignored) {
        }
        return emitter;
    }
}
