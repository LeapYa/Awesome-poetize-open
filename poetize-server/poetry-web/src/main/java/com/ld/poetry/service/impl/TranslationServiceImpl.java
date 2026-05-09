package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.service.ai.ApiTranslationProviderRegistry;
import com.ld.poetry.service.ai.LlmTranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 翻译服务实现类
 * 使用 Spring AI (LlmTranslationService) 和百度翻译 (BaiduTranslationProvider) 替代
 * Python 后端
 */
@Service
@Slf4j
public class TranslationServiceImpl implements TranslationService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;

    @Autowired
    private com.ld.poetry.service.SitemapService sitemapService;

    @Autowired
    private com.ld.poetry.service.SysAiConfigService sysAiConfigService;

    @Autowired
    private LlmTranslationService llmTranslationService;

    @Autowired
    private ApiTranslationProviderRegistry apiTranslationProviderRegistry;

    @Override
    public void translateAndSaveArticle(Integer articleId) {
        // 调用新的重载方法，使用默认参数
        translateAndSaveArticle(articleId, false, null);
    }

    @Override
    public void translateAndSaveArticle(Integer articleId, boolean skipAiTranslation,
            Map<String, String> pendingTranslation) {
        log.info("开始翻译并保存文章，ID: {}, 跳过AI翻译: {}, 有暂存翻译: {}",
                articleId, skipAiTranslation, pendingTranslation != null && !pendingTranslation.isEmpty());

        try {
            // 0. 检查翻译配置模式
            com.ld.poetry.entity.SysAiConfig aiConfig = sysAiConfigService.getArticleAiConfig("default");
            boolean isNoneMode = aiConfig != null && "none".equals(aiConfig.getTranslationType());
            boolean hasPendingTranslation = pendingTranslation != null && !pendingTranslation.isEmpty();

            if (isNoneMode && !hasPendingTranslation) {
                log.info("翻译模式为'不翻译'且无手动翻译，跳过翻译处理，文章ID: {}", articleId);
                return;
            }
            if (isNoneMode && hasPendingTranslation) {
                log.info("翻译模式为'不翻译'，但检测到手动编辑的翻译内容，将保存手动翻译，文章ID: {}", articleId);
                // 强制跳过AI翻译，只保存手动翻译
                skipAiTranslation = true;
            }

            // 1. 获取文章内容
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                log.warn("文章不存在，ID: {}", articleId);
                return;
            }

            // 检查文章是否有内容
            if (article.getArticleTitle() == null || article.getArticleTitle().trim().isEmpty() ||
                    article.getArticleContent() == null || article.getArticleContent().trim().isEmpty()) {
                log.warn("文章标题或内容为空，跳过翻译，ID: {}", articleId);
                return;
            }

            // 2. 获取翻译配置
            Map<String, Object> defaultLangs = sysAiConfigService.getDefaultLanguages();
            String sourceLanguage = defaultLangs != null
                    ? (String) defaultLangs.getOrDefault("default_source_lang", "zh")
                    : "zh";
            String targetLanguage = defaultLangs != null
                    ? (String) defaultLangs.getOrDefault("default_target_lang", "en")
                    : "en";

            log.info("翻译配置 - 源语言: {}, 目标语言: {}", sourceLanguage, targetLanguage);

            // 3. 处理跳过AI翻译的情况
            if (skipAiTranslation) {
                log.info("跳过AI自动翻译，文章ID: {}", articleId);

                // 如果有暂存的翻译数据，保存它
                if (pendingTranslation != null && !pendingTranslation.isEmpty()) {
                    String translatedTitle = pendingTranslation.get("title");
                    String translatedContent = pendingTranslation.get("content");
                    String translationLanguage = pendingTranslation.get("language");

                    if (translatedTitle != null && translatedContent != null && translationLanguage != null) {
                        boolean success = saveOrUpdateTranslation(articleId, translationLanguage,
                                translatedTitle, translatedContent);
                        if (success) {
                            log.info("暂存翻译保存成功，文章ID: {}, 目标语言: {}，预渲染将由事件监听器自动处理",
                                    articleId, translationLanguage);
                        } else {
                            log.error("暂存翻译保存失败，文章ID: {}, 目标语言: {}", articleId, translationLanguage);
                        }
                    }
                } else {
                    // 没有暂存翻译，预渲染将由事件监听器自动处理
                    log.info("跳过AI翻译且无暂存翻译，预渲染将由事件监听器自动处理，文章ID: {}", articleId);
                }
                return;
            }

            // 4. 翻译文章（使用协程并行翻译标题和内容）
            Map<String, String> translationResult = translateArticleOnly(
                    article.getArticleTitle(),
                    article.getArticleContent(),
                    skipAiTranslation,
                    pendingTranslation);

            // 如果翻译失败或被跳过，直接返回
            if (translationResult == null || translationResult.isEmpty()) {
                log.warn("文章翻译失败或被跳过，文章ID: {}", articleId);
                return;
            }

            String translatedTitle = translationResult.get("title");
            String translatedContent = translationResult.get("content");
            String resultTargetLang = translationResult.get("language");

            // 5. 保存或更新翻译结果（使用事务和重试机制处理并发）
            boolean success = saveOrUpdateTranslation(articleId, resultTargetLang, translatedTitle, translatedContent);

            if (success) {
                log.info("AI翻译保存成功，文章ID: {}, 目标语言: {}，预渲染将由事件监听器自动处理",
                        articleId, resultTargetLang);
            } else {
                log.error("AI翻译保存失败，文章ID: {}, 目标语言: {}", articleId, resultTargetLang);
            }

        } catch (Exception e) {
            log.error("翻译文章失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> translateArticleOnly(String title, String content, boolean skipAiTranslation,
            Map<String, String> pendingTranslation) {
        return translateArticleOnly(title, content, skipAiTranslation, pendingTranslation, null);
    }

    @Override
    public Map<String, String> translateArticleOnly(String title, String content, boolean skipAiTranslation,
            Map<String, String> pendingTranslation, TranslationService.TranslationProgressListener progressListener) {

        try {
            // 0. 检查翻译配置模式
            com.ld.poetry.entity.SysAiConfig aiConfig = sysAiConfigService.getArticleAiConfig("default");
            boolean isNoneMode = aiConfig != null && "none".equals(aiConfig.getTranslationType());
            boolean hasPendingTranslation = pendingTranslation != null && !pendingTranslation.isEmpty();

            if (isNoneMode && !hasPendingTranslation) {
                log.info("翻译模式为'不翻译'且无手动翻译，跳过翻译处理");
                return null;
            }
            if (isNoneMode && hasPendingTranslation) {
                log.info("翻译模式为'不翻译'，但检测到手动编辑的翻译内容，将返回手动翻译");
                return pendingTranslation;
            }

            // 1. 检查文章是否有内容
            if (title == null || title.trim().isEmpty() ||
                    content == null || content.trim().isEmpty()) {
                log.warn("文章标题或内容为空，跳过翻译");
                return null;
            }

            // 2. 获取翻译配置
            Map<String, Object> defaultLangs = sysAiConfigService.getDefaultLanguages();
            String sourceLanguage = defaultLangs != null
                    ? (String) defaultLangs.getOrDefault("default_source_lang", "zh")
                    : "zh";
            String targetLanguage = defaultLangs != null
                    ? (String) defaultLangs.getOrDefault("default_target_lang", "en")
                    : "en";

            log.info("翻译配置 - 源语言: {}, 目标语言: {}", sourceLanguage, targetLanguage);

            // 3. 处理跳过AI翻译的情况
            if (skipAiTranslation) {
                log.info("跳过AI自动翻译");
                if (pendingTranslation != null && !pendingTranslation.isEmpty()) {
                    return pendingTranslation;
                }
                return null;
            }

            // 4. 根据翻译类型选择翻译方式（本地调用，不再依赖 Python 后端）
            String translationType = aiConfig != null ? aiConfig.getTranslationType() : "llm";
            log.info("使用翻译方式: {}", translationType);

            if (apiTranslationProviderRegistry.isApiProvider(translationType)) {
                Map<String, String> result = apiTranslationProviderRegistry.translateArticle(
                        aiConfig, title, content, sourceLanguage, targetLanguage);
                if (result != null && !result.isEmpty()) {
                    log.info("API 文章翻译成功: provider={}", translationType);
                    return result;
                }
                log.warn("API 文章翻译失败，不回退到 LLM: provider={}", translationType);
                return null;
            }

            if (!"llm".equals(translationType) && !"dedicated_llm".equals(translationType)) {
                log.warn("未知翻译方式或已禁用: {}", translationType);
                return null;
            }

            // LLM 翻译（llm / dedicated_llm 均走 LlmTranslationService）
            Map<String, String> result = llmTranslationService.translateArticleStream(
                    title, content, sourceLanguage, targetLanguage, progressListener);

            if (result != null && !result.isEmpty()) {
                log.info("LLM 文章翻译成功");
                return result;
            }

            log.warn("LLM 文章翻译失败");
            return null;

        } catch (Exception e) {
            log.error("文章翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean saveTranslationResult(Integer articleId, String translatedTitle, String translatedContent,
            String targetLanguage) {
        return saveOrUpdateTranslation(articleId, targetLanguage, translatedTitle, translatedContent);
    }

    /**
     * 保存或更新翻译结果，处理并发重复插入问题
     */
    private boolean saveOrUpdateTranslation(Integer articleId, String targetLanguage, String translatedTitle,
            String translatedContent) {
        // 使用重试机制处理并发问题
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 再次检查是否已存在翻译记录（防止并发情况下的重复插入）
                LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                        .eq(ArticleTranslation::getLanguage, targetLanguage);

                ArticleTranslation existingTranslation = articleTranslationMapper.selectOne(queryWrapper);

                if (existingTranslation != null) {
                    // 更新现有翻译
                    existingTranslation.setTitle(translatedTitle);
                    existingTranslation.setContent(translatedContent);
                    existingTranslation.setUpdateTime(LocalDateTime.now());
                    articleTranslationMapper.updateById(existingTranslation);
                    log.info("更新文章翻译成功，文章ID: {}, 目标语言: {} (尝试第{}次)", articleId, targetLanguage, attempt);

                    // 翻译更新成功后，清除sitemap缓存（翻译URL可能需要更新）
                    updateSitemapForTranslation(articleId, "翻译更新");
                    return true;
                } else {
                    // 创建新翻译
                    ArticleTranslation newTranslation = new ArticleTranslation();
                    newTranslation.setArticleId(articleId);
                    newTranslation.setLanguage(targetLanguage);
                    newTranslation.setTitle(translatedTitle);
                    newTranslation.setContent(translatedContent);
                    newTranslation.setCreateTime(LocalDateTime.now());
                    newTranslation.setUpdateTime(LocalDateTime.now());

                    try {
                        articleTranslationMapper.insert(newTranslation);
                        log.info("创建文章翻译成功，文章ID: {}, 目标语言: {} (尝试第{}次)", articleId, targetLanguage, attempt);

                        // 翻译创建成功后，清除sitemap缓存（新增翻译URL）
                        updateSitemapForTranslation(articleId, "翻译创建");
                        return true;
                    } catch (org.springframework.dao.DuplicateKeyException e) {
                        // 如果遇到重复键异常，说明在我们检查后有其他线程插入了记录
                        log.warn("检测到并发插入，尝试更新现有记录，文章ID: {}, 目标语言: {} (尝试第{}次)", articleId, targetLanguage, attempt);
                        if (attempt < maxRetries) {
                            Thread.sleep(100 * attempt); // 短暂等待后重试
                            continue;
                        } else {
                            // 最后一次尝试：直接尝试更新
                            existingTranslation = articleTranslationMapper.selectOne(queryWrapper);
                            if (existingTranslation != null) {
                                existingTranslation.setTitle(translatedTitle);
                                existingTranslation.setContent(translatedContent);
                                existingTranslation.setUpdateTime(LocalDateTime.now());
                                articleTranslationMapper.updateById(existingTranslation);
                                log.info("最终更新文章翻译成功，文章ID: {}, 目标语言: {}", articleId, targetLanguage);

                                // 翻译最终更新成功后，清除sitemap缓存
                                updateSitemapForTranslation(articleId, "翻译最终更新");
                                return true;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("翻译保存被中断，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
                return false;
            } catch (Exception e) {
                log.error("保存翻译失败，文章ID: {}, 目标语言: {}, 尝试第{}次, 错误: {}", articleId, targetLanguage, attempt,
                        e.getMessage());
                if (attempt == maxRetries) {
                    return false;
                }
            }
        }

        log.error("保存翻译最终失败，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
        return false;
    }

    /**
     * 删除文章的所有翻译
     */
    public void refreshArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            int rows = articleTranslationMapper.delete(queryWrapper);
            if (rows > 0) {
                translateAndSaveArticle(articleId); // 重新翻译并将在内部触发 prerender

                // 刷新翻译后，清除sitemap缓存（翻译URL可能发生变化）
                updateSitemapForTranslation(articleId, "刷新翻译");
            }
            log.info("删除文章翻译成功，文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getArticleTranslation(Integer articleId, String language) {
        Map<String, String> result = new HashMap<>();

        if (articleId == null || language == null || language.trim().isEmpty()) {
            result.put("error", "文章ID或语言参数无效");
            return result;
        }

        try {
            // 查询文章翻译
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                    .eq(ArticleTranslation::getLanguage, language);

            ArticleTranslation translation = articleTranslationMapper.selectOne(queryWrapper);

            if (translation != null) {
                result.put("title", translation.getTitle() != null ? translation.getTitle() : "");
                result.put("content", translation.getContent() != null ? translation.getContent() : "");
                result.put("language", translation.getLanguage());
                result.put("status", "success");
            } else {
                result.put("error", "未找到对应语言的翻译");
                result.put("status", "not_found");
                log.warn("未找到文章翻译，文章ID: {}, 语言: {}", articleId, language);
            }

        } catch (Exception e) {
            log.error("获取文章翻译失败，文章ID: {}, 语言: {}, 错误: {}", articleId, language, e.getMessage(), e);
            result.put("error", "获取翻译失败: " + e.getMessage());
            result.put("status", "error");
        }

        return result;
    }

    @Override
    public List<String> getArticleAvailableLanguages(Integer articleId) {
        List<String> availableLanguages = new ArrayList<>();

        if (articleId == null) {
            log.warn("文章ID为空，无法获取可用翻译语言");
            return availableLanguages;
        }

        try {
            // 查询文章的所有翻译语言
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                    .select(ArticleTranslation::getLanguage);

            List<ArticleTranslation> translations = articleTranslationMapper.selectList(queryWrapper);

            if (translations != null && !translations.isEmpty()) {
                availableLanguages = translations.stream()
                        .map(ArticleTranslation::getLanguage)
                        .filter(lang -> lang != null && !lang.trim().isEmpty())
                        .distinct()
                        .collect(Collectors.toList());

            } else {
            }

        } catch (Exception e) {
            log.error("获取文章可用翻译语言失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }

        return availableLanguages;
    }

    @Override
    public Map<String, Object> saveManualTranslation(Integer articleId, String targetLanguage,
            String translatedTitle, String translatedContent) {
        Map<String, Object> result = new HashMap<>();

        if (articleId == null || targetLanguage == null || targetLanguage.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "参数无效：文章ID或目标语言不能为空");
            return result;
        }

        if (translatedTitle == null || translatedTitle.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "翻译标题不能为空");
            return result;
        }

        if (translatedContent == null || translatedContent.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "翻译内容不能为空");
            return result;
        }

        try {
            // 检查文章是否存在
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                result.put("success", false);
                result.put("message", "文章不存在");
                return result;
            }

            // 保存手动翻译
            boolean success = saveOrUpdateTranslation(articleId, targetLanguage,
                    translatedTitle.trim(), translatedContent.trim());

            if (success) {
                result.put("success", true);
                result.put("message", "翻译保存成功");
                log.info("手动翻译保存成功，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
                // 注意：sitemap更新已经在saveOrUpdateTranslation方法中处理
            } else {
                result.put("success", false);
                result.put("message", "翻译保存失败");
                log.error("手动翻译保存失败，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存翻译时发生错误: " + e.getMessage());
            log.error("手动翻译保存异常，文章ID: {}, 目标语言: {}", articleId, targetLanguage, e);
        }

        return result;
    }

    @Override
    public boolean shouldSkipAutoTranslation(Integer articleId, String targetLanguage) {
        if (articleId == null || targetLanguage == null || targetLanguage.trim().isEmpty()) {
            return false;
        }

        try {
            // 检查是否已存在翻译记录（无论是手动还是自动生成的）
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                    .eq(ArticleTranslation::getLanguage, targetLanguage);

            ArticleTranslation existingTranslation = articleTranslationMapper.selectOne(queryWrapper);

            // 如果存在翻译记录，则跳过自动翻译
            boolean shouldSkip = existingTranslation != null;

            if (shouldSkip) {
            }

            return shouldSkip;

        } catch (Exception e) {
            log.error("检查是否跳过自动翻译时发生异常，文章ID: {}, 目标语言: {}", articleId, targetLanguage, e);
            return false;
        }
    }

    @Override
    public String translateText(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String src = sourceLang != null ? sourceLang : "zh";
        String tgt = targetLang != null ? targetLang : "en";

        try {
            // 根据翻译类型选择翻译方式
            com.ld.poetry.entity.SysAiConfig aiConfig = sysAiConfigService.getArticleAiConfig("default");
            String translationType = aiConfig != null ? aiConfig.getTranslationType() : "llm";

            if (apiTranslationProviderRegistry.isApiProvider(translationType)) {
                String result = apiTranslationProviderRegistry.translateText(aiConfig, text, src, tgt);
                if (result != null && !result.isBlank() && !result.equals(text)) {
                    return result;
                }
                log.warn("API 文本翻译失败，不回退到 LLM: provider={}", translationType);
            } else if ("llm".equals(translationType) || "dedicated_llm".equals(translationType)) {
                // LLM 翻译 (llm / dedicated_llm)
                String result = llmTranslationService.translateText(text, src, tgt);
                if (result != null && !result.isBlank() && !result.equals(text)) {
                    return result;
                }
            }

            log.warn("翻译未成功, 类型={}, 原文前50字: {}",
                    translationType,
                    text.length() > 50 ? text.substring(0, 50) + "..." : text);
            return null;

        } catch (Exception e) {
            log.error("翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void deleteArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            int rows = articleTranslationMapper.delete(queryWrapper);
            log.info("仅删除文章翻译，无重译，文章ID: {}, 行数: {}", articleId, rows);

            // 删除翻译后，清除sitemap缓存（翻译URL需要从sitemap中移除）
            if (rows > 0) {
                updateSitemapForTranslation(articleId, "删除所有翻译");
            }
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}", articleId, e);
        }
    }

    @Override
    public boolean deleteSpecificTranslation(Integer articleId, String language) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                    .eq(ArticleTranslation::getLanguage, language);

            int rows = articleTranslationMapper.delete(queryWrapper);
            log.info("删除文章特定语言翻译，文章ID: {}, 语言: {}, 删除行数: {}", articleId, language, rows);

            // 删除特定语言翻译后，清除sitemap缓存（该语言的翻译URL需要从sitemap中移除）
            if (rows > 0) {
                updateSitemapForTranslation(articleId, "删除" + language + "翻译");
            }

            return rows > 0;
        } catch (Exception e) {
            log.error("删除文章特定语言翻译失败，文章ID: {}, 语言: {}", articleId, language, e);
            return false;
        }
    }

    /**
     * 翻译操作后更新sitemap的辅助方法（只清除缓存）
     * 
     * @param articleId 文章ID
     * @param operation 操作描述
     */
    private void updateSitemapForTranslation(Integer articleId, String operation) {
        try {
            if (sitemapService != null) {
                sitemapService.updateArticleSitemap(articleId);
            }
        } catch (Exception e) {
            log.warn("{}后清除sitemap缓存失败，不影响翻译操作，文章ID: {}, 错误: {}", operation, articleId, e.getMessage());
        }
    }

}
