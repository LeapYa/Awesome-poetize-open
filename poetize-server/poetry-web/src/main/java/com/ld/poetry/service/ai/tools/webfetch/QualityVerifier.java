package com.ld.poetry.service.ai.tools.webfetch;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 正文提取质量验证器。
 * <p>
 * 在 Readability4J 提取后，计算以下指标判断提取是否成功：
 * <ul>
 *   <li>{@code extractionRatio} = 提取文本长度 / 原始 body 文本长度（0.10~0.90 为正常区间）</li>
 *   <li>{@code hasTitle} = 提取的标题非空且长度 &gt; 5</li>
 *   <li>{@code keySignalCoverage} = {@code <title>}/{@code <meta description>}/H1 关键词在提取结果中的覆盖率</li>
 * </ul>
 * 异常时按规则触发回退：SPA fallback 或 raw body text。
 * <p>
 * 调用方根据返回的 {@link QualityResult} 中的指标与警告列表决定回退策略。
 */
public final class QualityVerifier {

    /** 简易中英文停用词表，用于关键词提取时过滤 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "你", "他", "她", "它", "们", "这", "那", "和", "与", "或",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "in", "on", "at", "to", "for", "of", "and", "or", "not", "with", "by",
            "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they");

    /** 关键词最大数量（取前 N 个） */
    private static final int MAX_KEYWORDS = 5;

    private QualityVerifier() {
    }

    /**
     * 验证 Readability 提取结果的质量。
     *
     * @param articleTitle          Readability 提取的标题（可能为 null）
     * @param articleText           Readability 提取的纯文本正文（可能为 null）
     * @param document              原始 Jsoup Document（用于 H1 关键词信号）
     * @param metadata              预提取的元数据（用于关键词信号）
     * @param originalBodyTextLength 原始 body 文本长度（Readability 解析前的快照，避免 DOM 被修改后失真）
     * @return 质量验证结果
     */
    public static QualityResult verify(String articleTitle, String articleText,
                                       Document document, PageMetadata metadata,
                                       int originalBodyTextLength) {
        QualityResult result = new QualityResult();
        if (articleText == null) {
            articleText = "";
        }

        // 1. extractionRatio：使用解析前保存的原始 body 长度，避免 Readability 修改 DOM 后指标失真
        int bodyTextLen = Math.max(0, originalBodyTextLength);
        double ratio = bodyTextLen > 0 ? (double) articleText.length() / bodyTextLen : 0.0;
        result.setExtractionRatio(ratio);

        // 2. hasTitle
        boolean hasTitle = articleTitle != null && articleTitle.length() > 5;
        result.setHasTitle(hasTitle);
        if (!hasTitle) {
            result.addWarning("标题提取失败");
        }

        // 3. keySignalCoverage
        Set<String> keywords = extractKeywords(document, metadata);
        if (!keywords.isEmpty()) {
            int hit = 0;
            String articleLower = articleText.toLowerCase();
            for (String kw : keywords) {
                if (articleLower.contains(kw.toLowerCase())) {
                    hit++;
                }
            }
            double coverage = (double) hit / keywords.size();
            result.setKeySignalCoverage(coverage);
            if (coverage < 0.30) {
                result.addWarning("关键信号覆盖率低，提取可能不完整");
            }
        } else {
            // 无信号可对比时视为通过
            result.setKeySignalCoverage(1.0);
        }

        return result;
    }

    /**
     * 从 {@code <title>}、{@code <meta description>}、H1 中提取关键词信号。
     * 去停用词后最多取 {@link #MAX_KEYWORDS} 个。
     */
    private static Set<String> extractKeywords(Document document, PageMetadata metadata) {
        List<String> candidates = new ArrayList<>();

        if (metadata != null) {
            if (metadata.getTitle() != null) {
                collectKeywords(metadata.getTitle(), candidates);
            }
            if (metadata.getDescription() != null) {
                collectKeywords(metadata.getDescription(), candidates);
            }
        }
        if (document != null) {
            for (Element h1 : document.select("h1")) {
                String text = h1.text().trim();
                if (text.length() > 2) {
                    candidates.add(text);
                }
            }
        }

        // 去停用词 + 去重 + 取前 N
        Set<String> result = new HashSet<>();
        for (String kw : candidates) {
            if (kw.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(kw.toLowerCase())) {
                continue;
            }
            result.add(kw);
            if (result.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        return result;
    }

    /**
     * 简单分词：英文按空格、标点拆分；中文按字符透传。
     * 仅保留长度 ≥ 3 的词作为信号。
     */
    private static void collectKeywords(String text, List<String> out) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String word : text.split("[\\s\\p{Punct}，。、；：！？「」『』（）【】《》·]+")) {
            String cleaned = word.replaceAll("[^\\w\\u4e00-\\u9fa5]", "");
            if (cleaned.length() >= 3) {
                out.add(cleaned);
            }
        }
    }

    /**
     * 质量验证结果。
     */
    public static class QualityResult {
        /** 提取文本长度 / 原始 body 文本长度 */
        private double extractionRatio;

        /** 提取的标题非空且长度 > 5 */
        private boolean hasTitle;

        /** 关键信号在提取结果中的覆盖率 [0.0, 1.0] */
        private double keySignalCoverage;

        /** 质量警告列表 */
        private final List<String> warnings = new ArrayList<>();

        public double getExtractionRatio() {
            return extractionRatio;
        }

        public void setExtractionRatio(double extractionRatio) {
            this.extractionRatio = extractionRatio;
        }

        public boolean isHasTitle() {
            return hasTitle;
        }

        public void setHasTitle(boolean hasTitle) {
            this.hasTitle = hasTitle;
        }

        public double getKeySignalCoverage() {
            return keySignalCoverage;
        }

        public void setKeySignalCoverage(double keySignalCoverage) {
            this.keySignalCoverage = keySignalCoverage;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }
    }
}
