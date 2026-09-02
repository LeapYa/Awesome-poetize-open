package com.ld.poetry.service.ai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剥离请求中 assistant 消息 reasoningContent metadata 的 Advisor（DeepSeek 系 API 专用）。
 * <p>
 * 背景：DeepSeek / SiliconFlow 的 API 禁止请求 messages 携带 reasoning_content 字段
 * （历史轮次的思考内容回传会直接返回 400），导致"思考模式 + 工具调用"组合必然失败：
 * ToolCallingAdvisor 的工具循环会把上一轮聚合的 assistant 消息（含 reasoningContent
 * metadata）回传给模型，OpenAiChatModel 构建请求时又会把该 metadata 写回
 * reasoning_content 字段。
 * <p>
 * 本 Advisor 位于 ToolCallingAdvisor 内层，在每轮模型调用前把消息历史中 assistant
 * 消息的 reasoningContent metadata 移除，保证思考与工具调用可以组合使用
 * （DeepSeek-V3.2 的 interleaved thinking 能力）。
 * <p>
 * 仅处理请求方向：响应 chunk 原样透传，前端思考内容的展示不受影响。
 * 仅对 DeepSeek 官方 / SiliconFlow profile 注册，OpenRouter 等需要回传
 * 思考内容的提供商不受影响。
 */
public class ReasoningContentStrippingAdvisor implements CallAdvisor, StreamAdvisor {

    /** Advisor 在链中的唯一标识 */
    public static final String NAME = "Reasoning-Content-Stripping-Advisor";

    /** OpenAiChatModel 写入 / 读取 assistant 消息 metadata 的思考内容键 */
    private static final String REASONING_CONTENT_KEY = "reasoningContent";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        // 位于 ToolCallingAdvisor 内层：工具循环的每一轮模型调用都会经过本 Advisor
        return ToolCallingAdvisor.DEFAULT_ORDER + 2;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(stripReasoningContent(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(stripReasoningContent(request));
    }

    /**
     * 移除消息历史中 assistant 消息的 reasoningContent metadata。
     * 没有任何思考内容时原样返回请求，不产生新对象。
     */
    private ChatClientRequest stripReasoningContent(ChatClientRequest request) {
        List<Message> instructions = request.prompt().getInstructions();
        if (!hasReasoningContent(instructions)) {
            return request;
        }

        List<Message> stripped = new ArrayList<>(instructions.size());
        for (Message message : instructions) {
            if (message instanceof AssistantMessage assistantMessage
                    && assistantMessage.getMetadata().get(REASONING_CONTENT_KEY) != null) {
                Map<String, Object> metadata = new LinkedHashMap<>(assistantMessage.getMetadata());
                metadata.remove(REASONING_CONTENT_KEY);
                stripped.add(AssistantMessage.builder()
                        .content(assistantMessage.getText())
                        .properties(metadata)
                        .toolCalls(assistantMessage.getToolCalls())
                        .media(assistantMessage.getMedia())
                        .build());
            } else {
                stripped.add(message);
            }
        }

        return request.mutate()
                .prompt(new Prompt(stripped, request.prompt().getOptions()))
                .build();
    }

    private boolean hasReasoningContent(List<Message> instructions) {
        for (Message message : instructions) {
            if (message instanceof AssistantMessage assistantMessage
                    && assistantMessage.getMetadata().get(REASONING_CONTENT_KEY) != null) {
                return true;
            }
        }
        return false;
    }
}
