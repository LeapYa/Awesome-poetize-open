package com.ld.poetry.service.ai.advisor;

import com.ld.poetry.service.ai.ImageMediaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章图片注入 Advisor。
 * <p>
 * 在 NATIVE 视觉模式下，扫描工具调用产生的 {@link ToolResponseMessage}，
 * 提取其中的 [图片: url] 标记（由 ArticleTools.getArticleContent 生成），
 * 将对应图片作为 {@link Media} 追加一条 {@link UserMessage} 注入到下一轮模型调用。
 * <p>
 * 这样主模型可以直接"看到"文章配图，无需调用 analyzeImage 工具，
 * 避免文本主模型 + 视觉模型的双倍 token 消耗。
 * <p>
 * 执行顺序：order 设为 {@link ToolCallingAdvisor#DEFAULT_ORDER} + 1，
 * 位于 ToolCallingAdvisor 内层。ToolCallingAdvisor 每轮模型调用都会
 * 通过 chain.nextCall/nextStream 触发本 Advisor，因此能在"工具执行后、
 * 下一轮模型推理前"的时机注入图片。
 * <p>
 * 去重：通过 {@link ChatClientRequest#context()} 维护已注入 URL 集合，
 * 避免同一张图在多轮工具调用中重复注入。该 context 随请求结束被 GC，
 * 无需主动清理。
 */
@Slf4j
public class ArticleImageInjectionAdvisor implements CallAdvisor, StreamAdvisor {

    /** Advisor 在链中的唯一标识 */
    public static final String NAME = "Article-Image-Injection-Advisor";

    /** context 中存放已注入图片 URL 集合的 key，避免多轮重复注入同一张图 */
    public static final String CONTEXT_INJECTED_IMAGES_KEY = NAME + ".injectedImages";

    /** 单轮最多注入的图片数量，防止文章图过多导致 token 爆炸 */
    public static final int MAX_IMAGES_PER_TURN = 3;

    /** 匹配 RagTextUtils.normalize 生成的 [图片: url] 标记 */
    private static final Pattern IMAGE_MARKER_PATTERN =
            Pattern.compile("\\[图片:\\s*([^\\]]+)\\]");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        // 位于 ToolCallingAdvisor 内层：ToolCallingAdvisor 每轮模型调用都通过
        // chain.nextCall/nextStream 触发内层 advisor，从而在工具执行后、下一轮
        // 模型推理前注入图片 Media。
        return ToolCallingAdvisor.DEFAULT_ORDER + 1;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest enriched = enrichWithArticleImages(request);
        return chain.nextCall(enriched);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest enriched = enrichWithArticleImages(request);
        return chain.nextStream(enriched);
    }

    /**
     * 扫描请求消息历史中的 ToolResponseMessage，提取 [图片: url] 标记，
     * 将图片作为 Media 追加一条 UserMessage 注入到消息末尾。
     * 没有图片标记时原样返回请求。
     */
    private ChatClientRequest enrichWithArticleImages(ChatClientRequest request) {
        List<String> imageUrls = extractArticleImageUrls(request);
        if (imageUrls.isEmpty()) {
            return request;
        }

        List<Media> mediaList = new ArrayList<>();
        for (String url : imageUrls) {
            try {
                // Media.Builder.data(URI) 内部会把 URI 转为 String 存储，
                // 因此 OpenAI 和 Anthropic 适配层都能正确处理：
                // - OpenAI：走 String 分支，把 URL 发给 OpenAI API 由其服务器拉图
                // - Anthropic：fromMediaData 命中 String 分支，createImageBlockParam
                //   判断 startsWith("https://") 走 ofUrl 路径，把 URL 发给 Anthropic API
                // 两厂商都由各自服务器拉图，本地无网络开销。
                mediaList.add(Media.builder()
                        .mimeType(ImageMediaUtils.resolveMimeType(url))
                        .data(URI.create(url))
                        .build());
            } catch (Exception e) {
                log.warn("文章图片 Media 构造失败，跳过: url={}, error={}", url, e.getMessage());
            }
        }
        if (mediaList.isEmpty()) {
            return request;
        }

        UserMessage imageMessage = UserMessage.builder()
                .text("以下是上文文章中出现的配图，供你参考用户关于图片内容的问题：")
                .media(mediaList)
                .build();

        List<Message> newInstructions = new ArrayList<>(request.prompt().getInstructions());
        newInstructions.add(imageMessage);

        return request.mutate()
                .prompt(new Prompt(newInstructions, request.prompt().getOptions()))
                .build();
    }

    /**
     * 从消息历史中提取文章图片 URL。
     * 仅扫描 ToolResponseMessage（工具返回结果），跳过"内联图片"等无 URL 标记，
     * 做 SSRF 防护后返回去重列表，最多 {@value #MAX_IMAGES_PER_TURN} 张。
     * <p>
     * 注意：ToolResponseMessage.getText() 永远返回空串（构造时 text 传 ""），
     * 工具返回的实际文本在每个 {@link ToolResponseMessage.ToolResponse#responseData()} 中。
     */
    @SuppressWarnings("unchecked")
    private List<String> extractArticleImageUrls(ChatClientRequest request) {
        Set<String> injected = getOrCreateInjectedSet(request);
        // 预扫描已有 UserMessage 的 Media URL，避免与初始 UserMessage 中
        // RAG 图片（NATIVE 模式作为 Media 注入）重复注入。
        seedExistingMediaUrls(request, injected);
        List<String> result = new ArrayList<>();
        for (Message message : request.prompt().getInstructions()) {
            if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                String text = response.responseData();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                Matcher matcher = IMAGE_MARKER_PATTERN.matcher(text);
                while (matcher.find() && result.size() < MAX_IMAGES_PER_TURN) {
                    String url = matcher.group(1).trim();
                    if ("内联图片".equals(url)) {
                        continue;
                    }
                    if (!StringUtils.hasText(url) || !injected.add(url)) {
                        // 空URL或本轮及历史已注入过，跳过
                        continue;
                    }
                    if (!ImageMediaUtils.isAllowedImageUrl(url)) {
                        log.debug("文章图片URL被SSRF防护拦截，跳过注入: url={}", url);
                        continue;
                    }
                    result.add(url);
                }
            }
        }
        return result;
    }

    /**
     * 扫描请求中已有 UserMessage 的 Media，将 URL 形式的图片数据加入去重集合，
     * 避免初始 UserMessage 中已可见的图片被工具返回的 [图片: URL] 标记重复注入。
     */
    private void seedExistingMediaUrls(ChatClientRequest request, Set<String> injected) {
        for (Message message : request.prompt().getInstructions()) {
            if (!(message instanceof UserMessage userMessage)) {
                continue;
            }
            List<Media> mediaList = userMessage.getMedia();
            if (mediaList == null || mediaList.isEmpty()) {
                continue;
            }
            for (Media media : mediaList) {
                Object data = media.getData();
                if (data != null) {
                    String url = data.toString();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        injected.add(url);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> getOrCreateInjectedSet(ChatClientRequest request) {
        Map<String, Object> context = request.context();
        Object existing = context.get(CONTEXT_INJECTED_IMAGES_KEY);
        if (existing instanceof Set) {
            return (Set<String>) existing;
        }
        Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
        context.put(CONTEXT_INJECTED_IMAGES_KEY, set);
        return set;
    }
}
