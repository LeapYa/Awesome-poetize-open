package com.ld.poetry.utils;

import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能摘要生成器
 * 实现基于内容结构化分析的摘要提取
 * 
 * @author sara
 */
@Slf4j
public class SmartSummaryGenerator {
    
    // 默认摘要长度配置
    private static final int MIN_SUMMARY_LENGTH = 50;   // 最短摘要长度
    private static final int MAX_SUMMARY_LENGTH = 150;  // 最长摘要长度
    private static final int IDEAL_SUMMARY_LENGTH = 100; // 理想摘要长度
    
    // Markdown 标记的正则表达式
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`]*`");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]+\\)");
    private static final Pattern BOLD_ITALIC_PATTERN = Pattern.compile("\\*\\*?([^*]+)\\*\\*?");
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern LIST_PATTERN = Pattern.compile("^[\\s]*[-*+]\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^[\\s]*\\d+\\.\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern TABLE_PATTERN = Pattern.compile("^\\|.*\\|$", Pattern.MULTILINE);
    
    // 句子分隔符
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[。！？；.!?;]\\s*");
    
    /**
     * 生成智能摘要
     * 
     * @param content 原始文章内容
     * @return 生成的摘要
     */
    public static String generateSummary(String content) {
        return generateSummary(content, IDEAL_SUMMARY_LENGTH);
    }
    
    /**
     * 生成智能摘要（升级版：优先使用TextRank算法）
     * 
     * @param content 原始文章内容
     * @param targetLength 目标长度
     * @return 生成的摘要
     */
    public static String generateAdvancedSummary(String content, int targetLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        try {
            // 首先尝试使用TextRank算法
            String textRankSummary = TextRankSummaryGenerator.generateSummary(content, targetLength);
            
            // 检查TextRank结果质量
            if (StringUtils.hasText(textRankSummary) && isQualitySummary(textRankSummary)) {
                return textRankSummary;
            }
            
            // 如果TextRank效果不好，回退到原来的规则方法
            return generateSummary(content, targetLength);
            
        } catch (Exception e) {
            log.warn("TextRank算法失败，使用规则方法: {}", e.getMessage());
            return generateSummary(content, targetLength);
        }
    }
    
    /**
     * 生成指定长度的智能摘要
     * 
     * @param content 原始文章内容
     * @param targetLength 目标长度
     * @return 生成的摘要
     */
    public static String generateSummary(String content, int targetLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        try {
            // 步骤1：清理和预处理内容
            String cleanedContent = preprocessContent(content);
            
            // 步骤2：结构化分析，提取最适合的段落
            String extractedContent = extractBestContent(cleanedContent);
            
            // 步骤3：按句子边界智能截取
            String summary = truncateByNaturalBoundary(extractedContent, targetLength);
            
            // 步骤4：后处理和美化
            summary = postprocessSummary(summary);
            
            // 步骤5：质量检查
            if (!isQualitySummary(summary)) {
                // 如果质量不达标，回退到简单策略
                summary = simpleFallbackSummary(content, targetLength);
            }
            
            return summary;
            
        } catch (Exception e) {
            log.warn("智能摘要生成失败，使用简单策略: {}", e.getMessage());
            return simpleFallbackSummary(content, targetLength);
        }
    }
    
    /**
     * 预处理内容：清理 Markdown 标记
     */
    private static String preprocessContent(String content) {
        String result = content;
        
        // 移除代码块（保留内容但移除标记）
        result = CODE_BLOCK_PATTERN.matcher(result).replaceAll(match -> {
            try {
                String codeContent = match.group().replaceAll("```\\w*\\n?", "").trim();
                return codeContent.length() > 100 ? "" : codeContent; // 长代码块直接移除
            } catch (Exception e) {
                return "";
            }
        });
        
        // 移除行内代码标记
        result = INLINE_CODE_PATTERN.matcher(result).replaceAll(match -> {
            try {
                // 行内代码模式 `code` 没有捕获组，所以需要手动处理
                String matchedText = match.group();
                return matchedText.replaceAll("`", "");
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理链接（保留链接文本）
        result = LINK_PATTERN.matcher(result).replaceAll(match -> {
            try {
                return match.group(1) != null ? match.group(1) : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理图片（保留alt文本）
        result = IMAGE_PATTERN.matcher(result).replaceAll(match -> {
            try {
                String altText = match.group(1);
                return StringUtils.hasText(altText) ? altText : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理粗体和斜体（保留文本内容）
        result = BOLD_ITALIC_PATTERN.matcher(result).replaceAll(match -> {
            try {
                return match.group(1) != null ? match.group(1) : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理标题（保留标题文本，但不作为摘要的首选）
        result = HEADING_PATTERN.matcher(result).replaceAll(match -> {
            try {
                return match.group(1) != null ? match.group(1) : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理引用块
        result = BLOCKQUOTE_PATTERN.matcher(result).replaceAll(match -> {
            try {
                return match.group(1) != null ? match.group(1) : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理无序列表
        result = LIST_PATTERN.matcher(result).replaceAll(match -> {
            try {
                return match.group(1) != null ? match.group(1) : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 处理有序列表
        result = ORDERED_LIST_PATTERN.matcher(result).replaceAll(match -> {
            try {
                return match.group(1) != null ? match.group(1) : "";
            } catch (Exception e) {
                return "";
            }
        });
        
        // 移除表格
        result = TABLE_PATTERN.matcher(result).replaceAll("");
        
        // 清理多余的空白字符
        result = result.replaceAll("\\n{3,}", "\n\n")  // 多个换行符合并
                      .replaceAll("[ \\t]{2,}", " ")     // 多个空格合并
                      .trim();
        
        return result;
    }
    
    /**
     * 结构化分析，提取最适合作为摘要的内容
     */
    private static String extractBestContent(String content) {
        // 按段落分割
        String[] paragraphs = content.split("\\n\\s*\\n");
        
        if (paragraphs.length == 0) {
            return content;
        }
        
        // 寻找最佳段落
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            
            // 跳过空段落
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }
            
            // 跳过太短的段落（可能是标题或列表项）
            if (paragraph.length() < 20) {
                continue;
            }
            
            // 跳过看起来像标题的段落
            if (isLikelyTitle(paragraph)) {
                continue;
            }
            
            // 跳过看起来像列表的段落
            if (isLikelyList(paragraph)) {
                continue;
            }
            
            // 找到合适的段落
            return paragraph;
        }
        
        // 如果没找到合适的段落，使用第一个非空段落
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (StringUtils.hasText(paragraph) && paragraph.length() >= 10) {
                return paragraph;
            }
        }
        
        // 最后的回退
        return content;
    }
    
    /**
     * 按自然语言边界智能截取
     */
    private static String truncateByNaturalBoundary(String content, int targetLength) {
        if (content.length() <= targetLength) {
            return content;
        }
        
        // 按句子分割
        String[] sentences = SENTENCE_PATTERN.split(content);
        
        StringBuilder result = new StringBuilder();
        boolean lengthLimited = false;
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (!StringUtils.hasText(sentence)) {
                continue;
            }
            
            // 如果加上这个句子会超过目标长度
            if (result.length() + sentence.length() > targetLength) {
                lengthLimited = true;
                // 如果当前结果太短，尝试截取部分句子
                if (result.length() < MIN_SUMMARY_LENGTH) {
                    int remainingLength = targetLength - result.length();
                    if (remainingLength > 20) { // 至少保留20个字符
                        String truncatedSentence = sentence.substring(0, Math.min(remainingLength - 3, sentence.length()));
                        // 找到最后一个完整的词边界
                        int lastSpace = truncatedSentence.lastIndexOf(' ');
                        if (lastSpace > remainingLength / 2) {
                            truncatedSentence = truncatedSentence.substring(0, lastSpace);
                        }
                        result.append(truncatedSentence).append("...");
                    }
                }
                break;
            }
            
            result.append(sentence);
            
            // 如果不是最后一个句子，添加句号
            if (!sentence.matches(".*[。！？；.!?;]$")) {
                result.append("。");
            }
        }
        
        // 句子被长度限制丢弃时补省略号标记，避免下游无法区分“完整”与“被截断”
        String summaryText = result.toString();
        return lengthLimited ? appendEllipsis(summaryText) : summaryText;
    }
    
    /** 截断时统一补省略号；若末尾是拼句补的句号则替换掉，避免出现“。...” */
    private static String appendEllipsis(String text) {
        if (!StringUtils.hasText(text) || text.endsWith("...")) {
            return text;
        }
        String cleaned = text.endsWith("。") ? text.substring(0, text.length() - 1) : text;
        return cleaned + "...";
    }
    
    /**
     * 后处理和美化摘要
     */
    private static String postprocessSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return summary;
        }
        
        // 清理开头和结尾的特殊字符
        summary = summary.replaceAll("^[\\s\\-*+>]+", "").replaceAll("[\\s]+$", "");
        
        // 确保句子结尾有标点符号
        if (!summary.matches(".*[。！？；.!?;…]$")) {
            summary += "...";
        }
        
        return summary;
    }
    
    /**
     * 检查摘要质量
     */
    private static boolean isQualitySummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return false;
        }
        
        // 长度检查
        if (summary.length() < MIN_SUMMARY_LENGTH) {
            return false;
        }
        
        // 内容质量检查
        // 1. 不能全是标点符号或特殊字符
        String contentOnly = summary.replaceAll("[\\p{Punct}\\s]", "");
        if (contentOnly.length() < MIN_SUMMARY_LENGTH / 2) {
            return false;
        }
        
        // 2. 不能重复字符过多
        if (hasExcessiveRepetition(summary)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否有过多的重复字符
     */
    private static boolean hasExcessiveRepetition(String text) {
        if (text.length() < 10) {
            return false;
        }
        
        // 检查连续重复字符
        for (int i = 0; i < text.length() - 3; i++) {
            char c = text.charAt(i);
            int count = 1;
            for (int j = i + 1; j < text.length() && text.charAt(j) == c; j++) {
                count++;
            }
            if (count > 4) { // 连续超过4个相同字符
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查段落是否看起来像标题
     */
    private static boolean isLikelyTitle(String paragraph) {
        // 单行且相对较短
        if (!paragraph.contains("\n") && paragraph.length() < 50) {
            return true;
        }
        
        // 包含特定的标题关键词
        String[] titleKeywords = {"第", "章", "节", "部分", "简介", "概述", "前言", "介绍"};
        for (String keyword : titleKeywords) {
            if (paragraph.contains(keyword) && paragraph.length() < 30) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查段落是否看起来像列表
     */
    private static boolean isLikelyList(String paragraph) {
        // 包含多个列表项标记
        long bulletCount = paragraph.chars().filter(ch -> ch == '•' || ch == '-' || ch == '*').count();
        return bulletCount > 2;
    }
    
    /**
     * 简单回退策略
     */
    private static String simpleFallbackSummary(String content, int targetLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        // 简单清理
        String cleaned = content.replaceAll("[#*`>\\-\\[\\]]", "")  // 移除markdown标记
                              .replaceAll("\\n+", " ")               // 换行转空格
                              .replaceAll("\\s+", " ")               // 多空格合并
                              .trim();
        
        if (cleaned.length() <= targetLength) {
            return cleaned;
        }
        
        // 简单截取，尽量在单词边界
        String truncated = cleaned.substring(0, Math.min(targetLength - 3, cleaned.length()));
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > targetLength / 2) {
            truncated = truncated.substring(0, lastSpace);
        }
        
        return truncated + "...";
    }
} 