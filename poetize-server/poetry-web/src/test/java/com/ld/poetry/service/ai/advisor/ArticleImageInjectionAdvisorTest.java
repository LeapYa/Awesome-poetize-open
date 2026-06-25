package com.ld.poetry.service.ai.advisor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 {@link ArticleImageInjectionAdvisor} 在 NATIVE 视觉模式下，
 * 扫描工具返回的 [图片: url] 标记并将文章配图作为 Media 注入下一轮消息。
 * <p>
 * 不依赖真实模型调用：通过 mock {@link CallAdvisorChain} 捕获传入的
 * {@link ChatClientRequest}，断言 Advisor 是否正确注入了含 Media 的 UserMessage。
 *
 * @author LeapYa
 * @since 2026-06-25
 */
class ArticleImageInjectionAdvisorTest {

    private final ArticleImageInjectionAdvisor advisor = new ArticleImageInjectionAdvisor();

    /** 构造含图片标记的工具返回消息，模拟 getArticleContent 的输出 */
    private ToolResponseMessage toolResponseWith(String... markers) {
        StringBuilder sb = new StringBuilder("文章内容如下：\n\n");
        for (String m : markers) {
            sb.append("某段文字...").append(m).append("\n");
        }
        List<ToolResponseMessage.ToolResponse> responses = List.of(
                new ToolResponseMessage.ToolResponse("call_1", "getArticleContent", sb.toString()));
        return ToolResponseMessage.builder().responses(responses).build();
    }

    /** 构造 ChatClientRequest，包含系统消息、用户消息和可选的工具返回消息 */
    private ChatClientRequest buildRequest(Message... extraMessages) {
        List<Message> instructions = new ArrayList<>();
        instructions.add(new SystemMessage("你是助手"));
        instructions.add(new UserMessage("这篇文章配图说明了什么？"));
        instructions.addAll(List.of(extraMessages));
        return ChatClientRequest.builder()
                .prompt(new Prompt(instructions))
                .context(new java.util.HashMap<>())
                .build();
    }

    /** 捕获 Advisor 传给 chain 的 request，断言注入行为 */
    private ChatClientRequest invokeAndCapture(ChatClientRequest input) {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenReturn(ChatClientResponse.builder().build());
        advisor.adviseCall(input, chain);

        ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(captor.capture());
        return captor.getValue();
    }

    /** 提取 request 消息列表中最后一条 UserMessage（Advisor 注入的图片消息） */
    private UserMessage lastUserMessage(ChatClientRequest request) {
        List<Message> msgs = request.prompt().getInstructions();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i) instanceof UserMessage um) {
                return um;
            }
        }
        return null;
    }

    /**
     * 核心场景：工具返回含 [图片: url] 标记，Advisor 应注入一条含 Media 的 UserMessage。
     * 模拟 AI 调 getArticleContent 后直接看到图片，无需再调 analyzeImage。
     */
    @Test
    void shouldInjectMediaWhenToolResponseContainsImageMarker() {
        String imageUrl = "https://example.com/article/img1.png";
        ToolResponseMessage toolMsg = toolResponseWith("[图片: " + imageUrl + "]");
        ChatClientRequest request = buildRequest(toolMsg);

        ChatClientRequest processed = invokeAndCapture(request);

        List<Message> msgs = processed.prompt().getInstructions();
        // 原有 3 条 + 新注入 1 条
        assertEquals(4, msgs.size(), "应追加一条图片 UserMessage");
        UserMessage injected = lastUserMessage(processed);
        assertNotNull(injected, "应注入 UserMessage");
        assertFalse(injected.getMedia().isEmpty(), "UserMessage 应含 Media");
        assertEquals(1, injected.getMedia().size(), "应注入 1 张图片");
        assertEquals(imageUrl, injected.getMedia().get(0).getData().toString(),
                "Media 数据应为图片 URL");
        assertTrue(injected.getText().contains("配图"),
                "注入消息文本应提示这是文章配图");
    }

    /** 无图片标记时，Advisor 不应修改消息列表 */
    @Test
    void shouldNotInjectWhenNoImageMarker() {
        ToolResponseMessage toolMsg = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call_1", "getArticleContent", "纯文本文章，无图片。")))
                .build();
        ChatClientRequest request = buildRequest(toolMsg);

        ChatClientRequest processed = invokeAndCapture(request);

        assertEquals(3, processed.prompt().getInstructions().size(),
                "无图片标记时不应追加消息");
        // 最后一条应仍是原 ToolResponseMessage，未被注入的 UserMessage 覆盖
        Message last = processed.prompt().getInstructions()
                .get(processed.prompt().getInstructions().size() - 1);
        assertInstanceOf(ToolResponseMessage.class, last,
                "最后一条应仍是 ToolResponseMessage，无注入");
    }

    /** [图片: 内联图片] 标记（data URI 转 text 标记）应被跳过 */
    @Test
    void shouldSkipInlineImageMarker() {
        ToolResponseMessage toolMsg = toolResponseWith("[图片: 内联图片]");
        ChatClientRequest request = buildRequest(toolMsg);

        ChatClientRequest processed = invokeAndCapture(request);

        assertEquals(3, processed.prompt().getInstructions().size(),
                "内联图片标记不应触发注入");
    }

    /** 同一张图在多次工具调用中不重复注入（context 去重） */
    @Test
    void shouldNotInjectSameImageTwice() {
        String imageUrl = "https://example.com/article/dup.png";
        ToolResponseMessage toolMsg = toolResponseWith("[图片: " + imageUrl + "]");
        ChatClientRequest request = buildRequest(toolMsg, toolMsg);

        ChatClientRequest processed = invokeAndCapture(request);

        // 4 条原消息（系统+用户+2个工具消息）+ 仅注入 1 条
        assertEquals(5, processed.prompt().getInstructions().size(),
                "重复 URL 只应注入一次");
        UserMessage injected = lastUserMessage(processed);
        assertEquals(1, injected.getMedia().size(), "去重后应只有 1 张 Media");
    }

    /** 内网 IP URL 应被 SSRF 防护拦截，不注入 */
    @Test
    void shouldRejectSsrfUrl() {
        ToolResponseMessage toolMsg = toolResponseWith("[图片: http://10.0.0.1/secret.png]");
        ChatClientRequest request = buildRequest(toolMsg);

        ChatClientRequest processed = invokeAndCapture(request);

        assertEquals(3, processed.prompt().getInstructions().size(),
                "SSRF URL 不应被注入");
    }

    /** 超过上限的图片应被截断为 MAX_IMAGES_PER_TURN 张 */
    @Test
    void shouldLimitToMaxImages() {
        ToolResponseMessage toolMsg = toolResponseWith(
                "[图片: https://example.com/a/1.png]",
                "[图片: https://example.com/a/2.png]",
                "[图片: https://example.com/a/3.png]",
                "[图片: https://example.com/a/4.png]",
                "[图片: https://example.com/a/5.png]");
        ChatClientRequest request = buildRequest(toolMsg);

        ChatClientRequest processed = invokeAndCapture(request);

        UserMessage injected = lastUserMessage(processed);
        assertNotNull(injected);
        assertEquals(ArticleImageInjectionAdvisor.MAX_IMAGES_PER_TURN,
                injected.getMedia().size(),
                "注入图片数不应超过上限 " + ArticleImageInjectionAdvisor.MAX_IMAGES_PER_TURN);
    }

    /** 非工具消息（如系统/用户消息）中的 [图片: url] 不应被提取 */
    @Test
    void shouldOnlyScanToolResponseMessage() {
        // 用户消息里也有 [图片: url]，但不应被提取
        UserMessage userWithMarker = new UserMessage(
                "用户消息 [图片: https://example.com/user/x.png]");
        ChatClientRequest request = buildRequest(userWithMarker);

        ChatClientRequest processed = invokeAndCapture(request);

        // 用户消息不是工具返回，不注入。原消息数 = 系统+用户+用户(带标记) = 3
        assertEquals(3, processed.prompt().getInstructions().size(),
                "不应从非 ToolResponseMessage 提取图片");
    }

    /** Advisor 顺序应位于 ToolCallingAdvisor 内层 */
    @Test
    void orderShouldBeInsideToolCallingAdvisor() {
        // order 必须 > ToolCallingAdvisor.DEFAULT_ORDER 才能在内层
        assertTrue(advisor.getOrder() > org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.DEFAULT_ORDER,
                "Advisor order 应大于 ToolCallingAdvisor.DEFAULT_ORDER 以位于内层");
    }
}
