package com.ld.poetry.service;

import java.util.Map;

/**
 * 摘要生成服务
 */
public interface SummaryService {

    record SummaryTaskResult(String status, String message, boolean failed) {
    }

    @FunctionalInterface
    interface SummaryProgressListener {
        void onEvent(String eventName, Map<String, Object> payload);
    }
    
    /**
     * 当前配置是否允许自动生成摘要
     */
    default boolean isAutoSummaryEnabled() {
        return true;
    }

    /**
     * 当前摘要生成模式
     */
    default String getSummaryGenerationMode() {
        return "global";
    }

    /**
     * 生成并保存文章多语言摘要
     * @param articleId 文章ID
     */
    default SummaryTaskResult generateAndSaveSummary(Integer articleId) {
        return generateAndSaveSummary(articleId, null);
    }

    SummaryTaskResult generateAndSaveSummary(Integer articleId, SummaryProgressListener progressListener);
    
    /**
     * 更新文章多语言摘要
     * @param articleId 文章ID  
     * @param content 文章内容
     */
    default SummaryTaskResult updateSummary(Integer articleId, String content) {
        return updateSummary(articleId, content, null);
    }

    SummaryTaskResult updateSummary(Integer articleId, String content, SummaryProgressListener progressListener);
    
    /**
     * 生成单语言摘要（简化版，用于特殊场景）
     * @param content 文章内容
     * @return 生成的摘要
     */
    String generateSummarySync(String content);
} 
