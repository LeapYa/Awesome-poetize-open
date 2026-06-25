package com.ld.poetry.service.ai;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 模型调用 Token 用量采集工具。
 *
 * <p>Spring AI 2.0 通过 {@link ChatResponse#getMetadata()}#getUsage() 暴露 Token 用量：
 * <ul>
 *   <li>{@link Usage#getPromptTokens()} 输入 Token</li>
 *   <li>{@link Usage#getCompletionTokens()} 输出 Token</li>
 *   <li>{@link Usage#getTotalTokens()} 合计 Token</li>
 * </ul>
 *
 * <p>不同 provider 在流式响应中的 usage 行为不一致：
 * <ul>
 *   <li>OpenAI 兼容：默认仅在最后一个 chunk 携带完整 usage（需 stream_options.include_usage=true）</li>
 *   <li>部分 provider：每个 chunk 携带累计 usage</li>
 *   <li>部分 provider：完全不返回 usage</li>
 * </ul>
 * 因此 {@link Accumulator} 采用「逐字段取最大值」策略，同时兼容上述场景；
 * 若整条流均未上报 usage，则 snapshot 各字段保持 null（与"零消耗"区分）。
 */
public final class AiUsageSupport {

    private AiUsageSupport() {
    }

    /**
     * 从单个 {@link ChatResponse} 提取 usage 快照。
     *
     * @param chatResponse 流式响应中的一个 chunk，可空
     * @return usage 快照；chunk 为空或不含 usage 时返回全 null 快照
     */
    public static Snapshot extract(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return Snapshot.empty();
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return Snapshot.empty();
        }
        return new Snapshot(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 流式响应 token 累加器（线程安全）。
     * 多 chunk 场景下逐字段取最大值，兼容末尾完整 usage 与累计 usage 两种行为。
     */
    public static final class Accumulator {
        private final AtomicReference<Snapshot> ref = new AtomicReference<>(Snapshot.empty());

        public void accept(ChatResponse chatResponse) {
            Snapshot incoming = extract(chatResponse);
            if (incoming.isEmpty()) {
                return;
            }
            ref.updateAndGet(current -> current.mergeMax(incoming));
        }

        /** 合并另一个累加器的结果（用于重试场景跨 attempt 累加）。 */
        public void merge(Accumulator other) {
            if (other == null) {
                return;
            }
            Snapshot incoming = other.ref.get();
            if (incoming.isEmpty()) {
                return;
            }
            ref.updateAndGet(current -> current.mergeMax(incoming));
        }

        public Snapshot snapshot() {
            return ref.get();
        }
    }

    /**
     * Token 用量快照（不可变，字段可空）。
     */
    public static final class Snapshot {
        private final Integer promptTokens;
        private final Integer completionTokens;
        private final Integer totalTokens;

        public Snapshot(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public static Snapshot empty() {
            return new Snapshot(null, null, null);
        }

        /**
         * 逐字段取最大值合并。totalTokens 为空时尝试用 prompt+completion 推算。
         */
        public Snapshot mergeMax(Snapshot other) {
            if (other == null) {
                return this;
            }
            Integer p = max(this.promptTokens, other.promptTokens);
            Integer c = max(this.completionTokens, other.completionTokens);
            Integer t = max(this.totalTokens, other.totalTokens);
            if (t == null && p != null && c != null) {
                t = p + c;
            }
            return new Snapshot(p, c, t);
        }

        public boolean isEmpty() {
            return promptTokens == null && completionTokens == null && totalTokens == null;
        }

        /**
         * 当 API 完全未上报 usage 时（snapshot 为空），用本地估算的输入 token 填充 prompt_tokens。
         * 输出 token 仍保持 null（本地无法估算输出）；若 API 已上报 usage，则原样返回不覆盖。
         *
         * @param fallbackInputTokens 本地估算的输入 token 数（可空）
         * @return 兜底后的快照
         */
        public Snapshot withInputFallback(Integer fallbackInputTokens) {
            if (!isEmpty()) {
                return this;
            }
            if (fallbackInputTokens == null) {
                return this;
            }
            return new Snapshot(fallbackInputTokens, null, null);
        }

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        /** 人类可读摘要，用于日志输出。 */
        public String describe() {
            if (isEmpty()) {
                return "usage=n/a";
            }
            return String.format("prompt=%s, completion=%s, total=%s",
                    describe(promptTokens), describe(completionTokens), describe(totalTokens));
        }

        private static String describe(Integer value) {
            return value == null ? "-" : String.valueOf(value);
        }

        private static Integer max(Integer a, Integer b) {
            if (a == null) {
                return b;
            }
            if (b == null) {
                return a;
            }
            return Math.max(a, b);
        }
    }

    /**
     * 安全截断文本用于日志预览，避免过长污染审计 detail。
     */
    public static String preview(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
