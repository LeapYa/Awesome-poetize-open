package com.ld.poetry.service.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 本地 Token 估算器（jtokkit CL100K_BASE）。
 *
 * <p>仅用于<b>输入侧预算管理</b>与<b>审计兜底</b>：当模型 API 未上报 usage 时，
 * 用本地编码器估算输入 token 数作为 {@code prompt_tokens} 的近似值（输出仍为 null）。
 *
 * <p><b>不是计费口径</b>：CL100K_BASE 只近似 GPT-3.5/GPT-4，对 DeepSeek/Claude/GPT-4o
 * 等模型存在编码差异，估算结果仅作参考。真实消耗以 {@code AiUsageSupport} 从
 * {@code chatResponse.getMetadata().getUsage()} 提取的值为准。
 *
 * <p>从 {@code CommentAiReplyService} 抽取为共享工具，避免各 AI 路径各自维护编码器实例。
 */
public final class AiTokenEstimator {

    /** CL100K_BASE 编码，兼容 GPT/DeepSeek 等主流模型近似估算。 */
    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    private AiTokenEstimator() {
    }

    /** 暴露底层 Encoding，供需要 encode/decode 的截断逻辑复用（如 clipByTokens）。 */
    public static Encoding getEncoding() {
        return ENCODING;
    }

    /** 估算单段文本的 token 数；空串返回 0。 */
    public static int countTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    /**
     * 估算一组 Spring AI {@link Message} 的输入 token 数。
     * 仅累加各消息可见文本内容，不计 role/结构开销（粗略上界）。
     */
    public static int countMessages(Collection<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            String text = extractText(message);
            if (StringUtils.hasText(text)) {
                total += ENCODING.countTokens(text);
            }
        }
        return total;
    }

    private static String extractText(Message message) {
        try {
            return message.getText();
        } catch (Exception ignored) {
            return null;
        }
    }
}
