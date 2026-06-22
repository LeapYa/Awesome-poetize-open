package com.ld.poetry.service.ai.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.DynamicChatClientFactory;
import com.ld.poetry.service.ai.ImageMediaUtils;
import com.ld.poetry.service.ai.MemorySearchCoordinator;
import com.ld.poetry.service.ai.ToolCallbackEventBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 记忆搜索工具 — AI 可主动调用以检索历史对话记忆。
 * <p>
 * 聊天记录存储在客户端 IndexedDB 中（不存服务器），因此本工具通过两轮交互完成搜索：
 * <ol>
 *   <li>AI 调用 search_memory → 工具通过 SSE 发送 memory_search 事件给前端</li>
 *   <li>工具阻塞等待（最多30秒），前端搜索 IndexedDB 后 POST 结果回后端</li>
 *   <li>后端解除阻塞，工具返回结果给 AI，AI 继续生成回复</li>
 * </ol>
 * <p>
 * 图片处理：前端搜索结果中可能包含历史图片的 base64 数据（JSON 格式：{text, images}）。
 * 后端解析后，如果有图片且视觉模型可用，自动调用视觉模型识别图片内容，
 * 将图片描述融入搜索结果返回给 AI，让 AI 能"看到"历史图片。
 * <p>
 * 非流式模式下（emitter 为 null）工具不可用，返回提示信息。
 */
@Service
public class MemorySearchTool {

    private static final Logger log = LoggerFactory.getLogger(MemorySearchTool.class);

    /** 等待前端响应的最大超时时间（秒） */
    private static final long TIMEOUT_SECONDS = 30;

    private final MemorySearchCoordinator coordinator;
    private final SysAiConfigService sysAiConfigService;
    private final DynamicChatClientFactory chatClientFactory;
    private final ObjectMapper objectMapper;

    public MemorySearchTool(MemorySearchCoordinator coordinator,
                            SysAiConfigService sysAiConfigService,
                            DynamicChatClientFactory chatClientFactory,
                            ObjectMapper objectMapper) {
        this.coordinator = coordinator;
        this.sysAiConfigService = sysAiConfigService;
        this.chatClientFactory = chatClientFactory;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "搜索之前的对话记忆。当你需要回忆之前聊过的内容、用户提到过的信息、" +
            "或者觉得对之前的对话记忆模糊时，调用此工具。输入关键词来搜索相关的历史对话。" +
            "注意：记忆存储在客户端，调用后需要等待客户端返回搜索结果。" +
            "如果历史对话中包含图片，搜索结果会自动包含图片内容的描述。")
    public String searchMemory(
            @ToolParam(description = "搜索关键词，用自然语言描述你想查找的内容") String query,
            @ToolParam(description = "最大返回条数，可选，默认10", required = false) Integer limit,
            ToolContext toolContext) {

        log.info("MemorySearchTool.searchMemory 被调用: query={}", query);

        if (query == null || query.isBlank()) {
            return "搜索关键词为空，无法搜索。";
        }

        // 从 ToolContext 获取 SSE emitter（流式模式下可用）
        Map<String, Object> context = toolContext != null ? toolContext.getContext() : Map.of();
        SseEmitter emitter = (SseEmitter) context.get(ToolCallbackEventBridge.SSE_EMITTER_CONTEXT_KEY);
        String conversationId = (String) context.get(ToolCallbackEventBridge.CONVERSATION_ID_CONTEXT_KEY);

        if (emitter == null) {
            // 非流式模式下无法通知前端，降级返回
            log.warn("MemorySearchTool 在非流式模式下被调用，无法搜索");
            return "记忆搜索在当前模式下不可用。请基于现有上下文回答。";
        }

        int maxResults = limit != null ? Math.max(1, Math.min(limit, 20)) : 10;
        String requestId = UUID.randomUUID().toString();

        // 通过 SSE 发送 memory_search 事件给前端
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", requestId);
            payload.put("query", query);
            payload.put("conversationId", conversationId != null ? conversationId : "");
            payload.put("limit", maxResults);
            emitter.send(SseEmitter.event().name("memory_search").data(payload));
            log.info("已发送 memory_search 事件: requestId={}, query={}", requestId, query);
        } catch (Exception e) {
            log.error("发送 memory_search 事件失败", e);
            return "记忆搜索失败：无法通知客户端。";
        }

        // 阻塞等待前端响应
        try {
            CompletableFuture<String> future = coordinator.register(requestId);
            String result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("记忆搜索完成: requestId={}, resultLength={}", requestId,
                    result != null ? result.length() : 0);

            // 处理搜索结果：解析 JSON，识别图片
            return processSearchResult(result);
        } catch (TimeoutException e) {
            coordinator.cancel(requestId);
            log.warn("记忆搜索超时: requestId={}", requestId);
            return "记忆搜索超时：客户端未在" + TIMEOUT_SECONDS + "秒内响应。请基于现有上下文回答。";
        } catch (Exception e) {
            coordinator.cancel(requestId);
            log.error("记忆搜索异常: requestId={}", requestId, e);
            return "记忆搜索失败：" + e.getMessage();
        }
    }

    /**
     * 处理前端返回的搜索结果。
     * <p>
     * 前端返回 JSON 格式：{ text: "搜索结果文字", images: ["data:image/...", ...] }
     * 如果包含图片且视觉模型可用，自动识别图片内容，将描述融入结果。
     * 如果不是 JSON 格式（兼容旧前端），直接返回原始字符串。
     */
    private String processSearchResult(String result) {
        if (result == null || result.isBlank()) {
            return "记忆搜索返回空结果。";
        }

        try {
            JsonNode root = objectMapper.readTree(result);
            String text = root.path("text").asText("");
            JsonNode imagesNode = root.path("images");

            // 没有图片，直接返回文字
            if (imagesNode == null || !imagesNode.isArray() || imagesNode.isEmpty()) {
                return text;
            }

            // 有图片，尝试调用视觉模型识别
            SysAiConfig config = sysAiConfigService.getAiChatConfigInternal("default");
            if (config == null || !isVisionAvailable(config)) {
                // 视觉模型不可用，在文字结果中标注图片存在但无法识别
                return text + "\n\n注：搜索结果中包含历史图片，但视觉模型未配置，无法识别图片内容。";
            }

            StringBuilder resultBuilder = new StringBuilder(text);
            resultBuilder.append("\n\n--- 历史图片内容识别 ---");

            for (int i = 0; i < imagesNode.size(); i++) {
                String imageDataUrl = imagesNode.get(i).asText();
                if (!StringUtils.hasText(imageDataUrl)) {
                    continue;
                }
                log.info("识别历史图片 {}/{}: dataUrlLength={}", i + 1, imagesNode.size(), imageDataUrl.length());
                String description = analyzeImageWithVisionModel(config, imageDataUrl);
                resultBuilder.append(String.format("\n图片%d: %s", i + 1, description));
            }

            return resultBuilder.toString();
        } catch (Exception e) {
            // 不是 JSON 格式（兼容旧前端或非 JSON 结果），直接返回原始字符串
            log.debug("搜索结果非 JSON 格式，直接返回: {}", e.getMessage());
            return result;
        }
    }

    /**
     * 检查视觉模型是否可用。
     * 主模型支持视觉（NATIVE）或配置了独立视觉模型（TOOL）均视为可用。
     */
    private boolean isVisionAvailable(SysAiConfig config) {
        if (config == null) {
            return false;
        }
        // 主模型支持视觉
        if (Boolean.TRUE.equals(config.getVisionSupported())) {
            return true;
        }
        // 配置了独立视觉模型
        return StringUtils.hasText(config.getVisionProvider())
                && StringUtils.hasText(config.getVisionApiKey())
                && StringUtils.hasText(config.getVisionModel());
    }

    /**
     * 使用视觉模型识别图片内容。
     * 优先使用独立视觉模型（TOOL 模式），其次使用主模型（NATIVE 模式）。
     */
    private String analyzeImageWithVisionModel(SysAiConfig config, String imageDataUrl) {
        try {
            // 选择视觉模型配置
            SysAiConfig visionConfig = new SysAiConfig();
            boolean hasIndependentVision = StringUtils.hasText(config.getVisionProvider())
                    && StringUtils.hasText(config.getVisionApiKey())
                    && StringUtils.hasText(config.getVisionModel());

            if (hasIndependentVision) {
                // 使用独立视觉模型
                visionConfig.setProvider(config.getVisionProvider());
                visionConfig.setApiKey(config.getVisionApiKey());
                visionConfig.setApiBase(config.getVisionApiBase());
                visionConfig.setModel(config.getVisionModel());
            } else {
                // 使用主模型（NATIVE 模式，主模型本身支持视觉）
                visionConfig.setProvider(config.getProvider());
                visionConfig.setApiKey(config.getApiKey());
                visionConfig.setApiBase(config.getApiBase());
                visionConfig.setModel(config.getModel());
            }
            visionConfig.setTemperature(BigDecimal.valueOf(0.3));

            // 创建视觉模型
            ChatModel visionChatModel = chatClientFactory.createChatModel(visionConfig);

            // 构建多模态消息
            Media imageMedia = Media.builder()
                    .mimeType(ImageMediaUtils.resolveMimeType(imageDataUrl))
                    .data(URI.create(imageDataUrl))
                    .build();

            UserMessage visionMessage = UserMessage.builder()
                    .text("请简要描述这张图片的主要内容，包括物体、文字、场景等关键信息。描述应当简洁但准确。")
                    .media(imageMedia)
                    .build();

            // 调用视觉模型
            log.info("调用视觉模型识别历史图片: provider={}, model={}",
                    visionConfig.getProvider(), visionConfig.getModel());
            ChatResponse response = visionChatModel.call(new Prompt(List.of(visionMessage)));

            if (response != null && response.getResult() != null
                    && response.getResult().getOutput() != null) {
                String description = response.getResult().getOutput().getText();
                return StringUtils.hasText(description) ? description : "视觉模型未返回有效描述。";
            }
            return "视觉模型返回空结果。";
        } catch (Exception e) {
            log.error("视觉模型识别历史图片失败: error={}", e.getMessage(), e);
            return "历史图片识别失败，无法获取图片描述。";
        }
    }
}
