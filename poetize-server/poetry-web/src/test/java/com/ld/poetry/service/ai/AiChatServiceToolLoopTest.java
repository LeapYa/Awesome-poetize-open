package com.ld.poetry.service.ai;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Spring AI 2.0 下 {@code ChatModel.stream()} 与 {@code ChatClient.stream()}
 * 在工具调用（Tool Calling）行为上的差异。
 * <p>
 * 核心假设（来自 Spring AI 2.0.0-RC1 发布说明）：
 * <ul>
 *   <li>{@code ChatModel} 层已移除内置 tool execution loop</li>
 *   <li>{@code ChatClient} 会自动注册 {@code ToolCallingAdvisor}，完整支持多轮 tool loop</li>
 * </ul>
 * 本测试通过 mock 一个返回 tool call 请求的 {@link ChatModel}，
 * 观察 {@link ToolCallback#call(String)} 是否被触发来验证上述假设。
 *
 * @author LeapYa
 * @since 2026-06-24
 */
class AiChatServiceToolLoopTest {

    /**
     * 测试 1：{@code ChatModel.stream()} 直接调用时，工具不应被执行。
     * <p>
     * 预期：mock 的 ChatModel 返回一个包含 tool call 的响应，
     * 但 ToolCallback.call() 永远不会被触发（因为没有 tool loop 驱动）。
     */
    @Test
    void chatModelStreamShouldNotExecuteToolCall() {
        AtomicInteger toolCallCount = new AtomicInteger(0);
        ToolCallback toolCallback = createCountingToolCallback("get_time", toolCallCount);

        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.stream(Mockito.any(Prompt.class)))
                .thenReturn(Flux.just(buildToolCallResponse("get_time", "{}")));

        Prompt prompt = new Prompt(List.of(new UserMessage("What time is it?")));

        // 直接用 ChatModel.stream() —— 不经过 ChatClient
        List<ChatResponse> responses = chatModel.stream(prompt).collectList().block();

        assertTrue(responses != null && !responses.isEmpty(), "应至少返回一个响应");
        assertEquals(0, toolCallCount.get(),
                "ChatModel.stream() 不应自动执行工具（Spring AI 2.0 已移除内置 tool loop）");
    }

    /**
     * 测试 2：{@code ChatClient.stream()} 调用时，工具应被自动执行。
     * <p>
     * 预期：ChatClient 内部注册 ToolCallingAdvisor，
     * 当模型返回 tool call 时，advisor 会调用 ToolCallback.call() 执行工具，
     * 然后把结果送回模型继续推理（这里 mock 第二次调用返回最终文本）。
     */
    @Test
    void chatClientStreamShouldExecuteToolCall() {
        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicInteger modelCallCount = new AtomicInteger(0);
        ToolCallback toolCallback = createCountingToolCallback("get_time", toolCallCount);

        // 必须使用 ToolCallingChatOptions 实现（如 OpenAiChatOptions），
        // 否则 ToolCallingAdvisor 会跳过 tool loop（见 ToolCallingAdvisor.adviseCall 第 126 行）
        OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-4o-mini").build();

        ChatModel chatModel = Mockito.mock(ChatModel.class);
        Mockito.when(chatModel.getOptions()).thenReturn(options);
        // ChatClient.call() 内部调用 chatModel.call()（同步），不是 stream()
        // 第一次调用：返回 tool call 请求
        // 第二次调用（tool loop 继续）：返回最终文本
        Mockito.when(chatModel.call(Mockito.any(Prompt.class)))
                .thenAnswer(invocation -> {
                    if (modelCallCount.incrementAndGet() == 1) {
                        return buildToolCallResponse("get_time", "{}");
                    }
                    return buildTextResponse("现在是 2026 年 6 月 24 日。");
                });

        String response = ChatClient.create(chatModel)
                .prompt(new Prompt(List.of(new UserMessage("What time is it?")), options))
                .tools(toolCallback)
                .call()
                .content();

        assertTrue(toolCallCount.get() > 0,
                "ChatClient 应通过 ToolCallingAdvisor 自动执行工具，实际执行次数: " + toolCallCount.get());
        assertTrue(response != null && !response.isBlank(), "应返回最终文本响应");
    }

    /**
     * 构造一个包含 tool call 请求的 ChatResponse（模拟模型要求调用工具）。
     */
    private ChatResponse buildToolCallResponse(String toolName, String arguments) {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_001", "function", toolName, arguments);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    /**
     * 构造一个普通文本响应的 ChatResponse（模拟模型最终回复）。
     */
    private ChatResponse buildTextResponse(String text) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(text)
                .build();
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    /**
     * 创建一个计数型 ToolCallback，记录被调用次数。
     */
    private ToolCallback createCountingToolCallback(String name, AtomicInteger counter) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description("Returns the current time")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                counter.incrementAndGet();
                return "{\"time\":\"2026-06-24T12:00:00Z\"}";
            }
        };
    }
}
