package com.ld.poetry.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于TextRank算法的智能摘要生成器
 * 
 * TextRank算法原理：
 * 1. 将文本分割成句子
 * 2. 计算句子之间的相似度（基于词汇重叠度）
 * 3. 构建句子相似度图，权重为相似度值
 * 4. 运行类似PageRank的算法计算句子重要性得分
 * 5. 选择得分最高的几个句子，按原文顺序组成摘要
 * 
 * 相比基础规则方法的优势：
 * - 考虑句子间的关系和重要性
 * - 能识别文章的核心主题
 * - 避免选择孤立的句子
 * - 数学基础扎实，效果稳定
 * 
 * @author sara
 */
@Slf4j
public class TextRankSummaryGenerator {
    
    // 算法参数配置
    private static final double DAMPING_FACTOR = 0.85;        // PageRank阻尼系数
    private static final int MAX_ITERATIONS = 100;            // 最大迭代次数
    private static final double CONVERGENCE_THRESHOLD = 1e-6; // 收敛阈值
    private static final double MIN_SIMILARITY = 0.1;         // 最小相似度阈值
    
    // 摘要配置
    private static final int DEFAULT_SENTENCE_COUNT = 3;       // 默认选择句子数
    private static final int MIN_SENTENCE_LENGTH = 15;        // 最短句子长度
    private static final int MAX_SUMMARY_LENGTH = 200;        // 最大摘要长度
    
    // 文本处理正则表达式
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[。！？.!?])\\s*");
    private static final Pattern WORD_EXTRACTOR = Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9]+");
    private static final Pattern MARKDOWN_CLEANER = Pattern.compile(
        "```[\\s\\S]*?```|" +           // 代码块
        "`[^`]*`|" +                    // 行内代码
        "!\\[[^\\]]*\\]\\([^)]*\\)|" +  // 图片
        "\\[[^\\]]*\\]\\([^)]*\\)|" +   // 链接
        "#{1,6}\\s+|" +                 // 标题
        "\\*{1,2}([^*]+)\\*{1,2}|" +    // 粗体斜体
        "_{1,2}([^_]+)_{1,2}|" +        // 下划线
        "~~([^~]+)~~"                   // 删除线
    );
    
    // 停用词表
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这样", "现在", "可以", "但是", "这个", "来", "他", "时候", "如果", "那么", "只是", "还是", "为了", "这些", "那些", "因为", "所以", "虽然", "然后", "或者", "而且",
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from", "as", "is", "was", "are", "were", "be", "been", "have", "has", "had", "do", "does", "did", "will", "would", "should", "could", "may", "might", "must", "can", "this", "that", "these", "those"
    );
    
    /**
     * 使用TextRank算法生成摘要（默认参数）
     */
    public static String generateSummary(String content) {
        return generateSummary(content, DEFAULT_SENTENCE_COUNT, MAX_SUMMARY_LENGTH);
    }
    
    /**
     * 使用TextRank算法生成指定长度的摘要
     */
    public static String generateSummary(String content, int maxLength) {
        return generateSummary(content, DEFAULT_SENTENCE_COUNT, maxLength);
    }
    
    /**
     * 使用TextRank算法生成摘要（完整参数控制）
     */
    public static String generateSummary(String content, int sentenceCount, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        try {
            
            // 步骤1：预处理文本
            String cleanedContent = preprocessText(content);
            
            // 步骤2：句子分割和过滤
            List<String> sentences = extractSentences(cleanedContent);
            
            if (sentences.isEmpty()) {
                return "";
            }
            
            if (sentences.size() == 1) {
                return truncateToLength(sentences.get(0), maxLength);
            }
            
            // 步骤3：构建句子相似度矩阵
            double[][] similarityMatrix = buildSimilarityMatrix(sentences);
            
            // 步骤4：运行TextRank算法
            double[] scores = runTextRankAlgorithm(similarityMatrix);
            
            // 步骤5：选择和排序最重要的句子
            List<SentenceScore> rankedSentences = rankSentencesByScore(sentences, scores);
            
            // 步骤6：生成最终摘要
            String summary = buildFinalSummary(rankedSentences, sentenceCount, maxLength);
            
            
            return summary;
            
        } catch (Exception e) {
            log.warn("TextRank算法失败，回退到简单策略: {}", e.getMessage());
            return SmartSummaryGenerator.generateSummary(content, maxLength);
        }
    }
    
    /**
     * 预处理文本：清理Markdown标记和格式化
     */
    private static String preprocessText(String content) {
        String cleaned = MARKDOWN_CLEANER.matcher(content).replaceAll(match -> {
            if (match.group(1) != null) return match.group(1);
            if (match.group(2) != null) return match.group(2);
            if (match.group(3) != null) return match.group(3);
            return " ";
        });
        
        return cleaned.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * 提取和过滤句子
     */
    private static List<String> extractSentences(String text) {
        String[] rawSentences = SENTENCE_SPLITTER.split(text);
        
        List<String> sentences = new ArrayList<>();
        
        for (String sentence : rawSentences) {
            sentence = sentence.trim();
            
            if (sentence.length() < MIN_SENTENCE_LENGTH) continue;
            if (isLowQualitySentence(sentence)) continue;
            
            sentences.add(sentence);
        }
        
        return sentences;
    }
    
    /**
     * 检查是否为低质量句子
     */
    private static boolean isLowQualitySentence(String sentence) {
        String effectiveContent = sentence.replaceAll("[\\p{Punct}\\s\\d]", "");
        double contentRatio = (double) effectiveContent.length() / sentence.length();
        return contentRatio < 0.3;
    }
    
    /**
     * 构建句子相似度矩阵
     */
    private static double[][] buildSimilarityMatrix(List<String> sentences) {
        int size = sentences.size();
        double[][] matrix = new double[size][size];
        
        List<Set<String>> sentenceWords = sentences.stream()
            .map(TextRankSummaryGenerator::extractWords)
            .collect(Collectors.toList());
        
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double similarity = calculateSentenceSimilarity(sentenceWords.get(i), sentenceWords.get(j));
                
                if (similarity > MIN_SIMILARITY) {
                    matrix[i][j] = similarity;
                    matrix[j][i] = similarity;
                }
            }
        }
        
        return matrix;
    }
    
    /**
     * 从句子中提取有效词汇
     */
    private static Set<String> extractWords(String sentence) {
        Set<String> words = new HashSet<>();
        var matcher = WORD_EXTRACTOR.matcher(sentence.toLowerCase());
        
        while (matcher.find()) {
            String word = matcher.group();
            
            if (word.length() < 2) continue;
            if (STOP_WORDS.contains(word)) continue;
            if (word.matches("\\d+")) continue;
            
            words.add(word);
        }
        
        return words;
    }
    
    /**
     * 计算两个句子的相似度
     */
    private static double calculateSentenceSimilarity(Set<String> words1, Set<String> words2) {
        if (words1.isEmpty() || words2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        double jaccard = (double) intersection.size() / union.size();
        double lengthWeight = Math.min(words1.size(), words2.size()) / (double) Math.max(words1.size(), words2.size());
        
        return jaccard * (0.8 + 0.2 * lengthWeight);
    }
    
    /**
     * 运行TextRank算法
     */
    private static double[] runTextRankAlgorithm(double[][] similarityMatrix) {
        int size = similarityMatrix.length;
        double[] scores = new double[size];
        double[] newScores = new double[size];
        
        Arrays.fill(scores, 1.0);
        
        double[] outDegreeWeights = new double[size];
        for (int i = 0; i < size; i++) {
            outDegreeWeights[i] = Arrays.stream(similarityMatrix[i]).sum();
            if (outDegreeWeights[i] == 0) {
                outDegreeWeights[i] = 1.0;
            }
        }
        
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean converged = true;
            
            for (int i = 0; i < size; i++) {
                double score = (1.0 - DAMPING_FACTOR);
                
                for (int j = 0; j < size; j++) {
                    if (i != j && similarityMatrix[j][i] > 0) {
                        score += DAMPING_FACTOR * scores[j] * similarityMatrix[j][i] / outDegreeWeights[j];
                    }
                }
                
                newScores[i] = score;
                
                if (Math.abs(newScores[i] - scores[i]) > CONVERGENCE_THRESHOLD) {
                    converged = false;
                }
            }
            
            System.arraycopy(newScores, 0, scores, 0, size);
            
            if (converged) {
                break;
            }
        }
        
        return scores;
    }
    
    /**
     * 根据得分对句子进行排序
     */
    private static List<SentenceScore> rankSentencesByScore(List<String> sentences, double[] scores) {
        List<SentenceScore> rankedSentences = new ArrayList<>();
        
        for (int i = 0; i < sentences.size(); i++) {
            rankedSentences.add(new SentenceScore(sentences.get(i), scores[i], i));
        }
        
        rankedSentences.sort((a, b) -> Double.compare(b.score, a.score));
        
        return rankedSentences;
    }
    
    /**
     * 构建最终摘要
     */
    private static String buildFinalSummary(List<SentenceScore> rankedSentences, int targetCount, int maxLength) {
        List<SentenceScore> selectedSentences = rankedSentences.subList(
            0, Math.min(targetCount, rankedSentences.size())
        );
        
        selectedSentences.sort(Comparator.comparingInt(s -> s.originalIndex));
        
        StringBuilder summary = new StringBuilder();
        boolean lengthLimited = false;
        
        for (SentenceScore sentenceScore : selectedSentences) {
            String sentence = sentenceScore.sentence;
            
            if (summary.length() + sentence.length() + 1 > maxLength) {
                lengthLimited = true;
                if (summary.length() < maxLength * 0.6) {
                    int remainingLength = maxLength - summary.length() - 3;
                    if (remainingLength > 20) {
                        String truncated = truncateToLength(sentence, remainingLength);
                        summary.append(truncated);
                    }
                }
                break;
            }
            
            if (summary.length() > 0) {
                summary.append(" ");
            }
            summary.append(sentence);
            
            if (!sentence.matches(".*[。！？.!?]$")) {
                summary.append("。");
            }
        }
        
        // 句子被长度限制丢弃时补省略号标记，避免下游无法区分“完整”与“被截断”
        String result = summary.toString().trim();
        return lengthLimited ? appendEllipsis(result) : result;
    }
    
    /** 截断时统一补省略号；若末尾是拼句补的句号则替换掉，避免出现“。...” */
    private static String appendEllipsis(String text) {
        if (text == null || text.isEmpty() || text.endsWith("...")) {
            return text;
        }
        String cleaned = text.endsWith("。") ? text.substring(0, text.length() - 1) : text;
        return cleaned + "...";
    }
    
    /**
     * 截取文本到指定长度
     */
    private static String truncateToLength(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        
        String truncated = text.substring(0, maxLength - 3);
        
        int lastSpace = truncated.lastIndexOf(' ');
        int lastChinese = -1;
        
        for (int i = truncated.length() - 1; i >= maxLength * 0.7; i--) {
            if (truncated.charAt(i) >= '\u4e00' && truncated.charAt(i) <= '\u9fa5') {
                lastChinese = i + 1;
                break;
            }
        }
        
        int cutPoint = Math.max(lastSpace, lastChinese);
        if (cutPoint > maxLength * 0.7) {
            truncated = truncated.substring(0, cutPoint);
        }
        
        return truncated + "...";
    }
    
    /**
     * 句子得分数据结构
     */
    private static class SentenceScore {
        final String sentence;
        final double score;
        final int originalIndex;
        
        SentenceScore(String sentence, double score, int originalIndex) {
            this.sentence = sentence;
            this.score = score;
            this.originalIndex = originalIndex;
        }
        
        @Override
        public String toString() {
            return String.format("SentenceScore{score=%.4f, index=%d, text='%s'}", 
                score, originalIndex, sentence.substring(0, Math.min(50, sentence.length())));
        }
    }
    
    /**
     * 获取算法统计信息（用于调试和优化）
     */
    public static Map<String, Object> getAlgorithmStats(String content) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            String cleanedContent = preprocessText(content);
            List<String> sentences = extractSentences(cleanedContent);
            
            stats.put("originalLength", content.length());
            stats.put("cleanedLength", cleanedContent.length());
            stats.put("sentenceCount", sentences.size());
            stats.put("avgSentenceLength", sentences.stream().mapToInt(String::length).average().orElse(0));
            
            if (!sentences.isEmpty()) {
                double[][] matrix = buildSimilarityMatrix(sentences);
                double[] scores = runTextRankAlgorithm(matrix);
                
                stats.put("maxScore", Arrays.stream(scores).max().orElse(0));
                stats.put("minScore", Arrays.stream(scores).min().orElse(0));
                stats.put("avgScore", Arrays.stream(scores).average().orElse(0));
                
                // 计算相似度矩阵的密度
                int totalPairs = sentences.size() * (sentences.size() - 1) / 2;
                long nonZeroPairs = 0;
                for (int i = 0; i < matrix.length; i++) {
                    for (int j = i + 1; j < matrix.length; j++) {
                        if (matrix[i][j] > 0) nonZeroPairs++;
                    }
                }
                stats.put("similarityDensity", totalPairs > 0 ? (double) nonZeroPairs / totalPairs : 0);
            }
            
        } catch (Exception e) {
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
} 