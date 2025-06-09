package com.ld.poetry.service;

import java.util.Map;

/**
 * 翻译服务接口
 */
public interface TranslationService {
    
    /**
     * 翻译并保存文章
     * @param articleId 文章ID
     */
    void translateAndSaveArticle(Integer articleId);
    
    /**
     * 删除文章的所有翻译
     * @param articleId 文章ID
     */
    void refreshArticleTranslation(Integer articleId);
    
    /**
     * 翻译文本
     * @param text 待翻译文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     */
    String translateText(String text, String sourceLang, String targetLang);
    
    /**
     * 获取文章翻译
     * @param articleId 文章ID
     * @param language 目标语言
     * @return 翻译结果
     */
    Map<String, String> getArticleTranslation(Integer articleId, String language);
} 