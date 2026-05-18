package com.ld.poetry.service;

import java.util.List;
import java.util.Map;

/**
 * 翻译服务接口
 */
public interface TranslationService {

    @FunctionalInterface
    interface TranslationProgressListener {
        void onEvent(String eventName, Map<String, Object> payload);
    }

    /**
     * 翻译并保存文章
     * @param articleId 文章ID
     */
    void translateAndSaveArticle(Integer articleId);

    /**
     * 翻译并保存文章（支持跳过AI翻译和暂存翻译数据）
     * @param articleId 文章ID
     * @param skipAiTranslation 是否跳过AI翻译
     * @param pendingTranslation 暂存的翻译数据
     */
    void translateAndSaveArticle(Integer articleId, boolean skipAiTranslation, Map<String, String> pendingTranslation);

    /**
     * 只翻译文章内容，不保存到数据库（用于并行优化）
     * @param title 文章标题
     * @param content 文章内容
     * @param skipAiTranslation 是否跳过AI翻译
     * @param pendingTranslation 暂存的翻译数据
     * @return 翻译结果 Map {title: 翻译后标题, content: 翻译后内容, language: 目标语言}，如果跳过翻译则返回null
     */
    Map<String, String> translateArticleOnly(String title, String content, boolean skipAiTranslation, Map<String, String> pendingTranslation);

    /**
     * 只翻译文章内容，不保存到数据库，并回调流式进度
     * @param title 文章标题
     * @param content 文章内容
     * @param skipAiTranslation 是否跳过AI翻译
     * @param pendingTranslation 暂存的翻译数据
     * @param progressListener 进度监听器
     * @return 翻译结果
     */
    Map<String, String> translateArticleOnly(String title, String content, boolean skipAiTranslation,
                                            Map<String, String> pendingTranslation,
                                            TranslationProgressListener progressListener);

    /**
     * 保存翻译结果到数据库
     * @param articleId 文章ID
     * @param translatedTitle 翻译后的标题
     * @param translatedContent 翻译后的内容
     * @param targetLanguage 目标语言
     * @return 保存是否成功
     */
    boolean saveTranslationResult(Integer articleId, String translatedTitle, String translatedContent, String targetLanguage);

    /**
     * 保存翻译结果（包含手动摘要）
     * @param articleId 文章ID
     * @param translatedTitle 翻译后的标题
     * @param translatedContent 翻译后的内容
     * @param targetLanguage 目标语言
     * @param translatedSummary 翻译摘要
     * @return 保存是否成功
     */
    boolean saveTranslationResult(Integer articleId, String translatedTitle, String translatedContent,
                                  String targetLanguage, String translatedSummary);

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

    /**
     * 获取文章所有可用的翻译语言
     * @param articleId 文章ID
     * @return 可用翻译语言列表
     */
    List<String> getArticleAvailableLanguages(Integer articleId);

    /**
     * 手动保存文章翻译
     * @param articleId 文章ID
     * @param targetLanguage 目标语言
     * @param translatedTitle 翻译后的标题
     * @param translatedContent 翻译后的内容
     * @return 保存结果
     */
    Map<String, Object> saveManualTranslation(Integer articleId, String targetLanguage,
                                            String translatedTitle, String translatedContent,
                                            String translatedSummary);

    /**
     * 检查文章是否应该跳过自动翻译
     * @param articleId 文章ID
     * @param targetLanguage 目标语言
     * @return 是否跳过自动翻译
     */
    boolean shouldSkipAutoTranslation(Integer articleId, String targetLanguage);

    /**
     * 删除文章所有翻译，不做重新翻译
     */
    void deleteArticleTranslation(Integer articleId);

    /**
     * 删除文章的特定语言翻译
     * @param articleId 文章ID
     * @param language 要删除的语言代码
     * @return 是否删除成功
     */
    boolean deleteSpecificTranslation(Integer articleId, String language);
}
