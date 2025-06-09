package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 翻译服务实现类
 * 通过HTTP调用Python端的翻译服务
 */
@Service
@Slf4j
public class TranslationServiceImpl implements TranslationService {
    
    @Value("${python.service.url:http://localhost:5000}")
    private String pythonServiceUrl;
    
    @Autowired
    private ArticleMapper articleMapper;
    
    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;
    
    private final RestTemplate restTemplate;
    
    public TranslationServiceImpl() {
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public void translateAndSaveArticle(Integer articleId) {
        log.info("开始翻译并保存文章，ID: {}", articleId);
        
        try {
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
            
            // 2. 检查是否已有英文翻译
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                       .eq(ArticleTranslation::getLanguage, "en");
            
            ArticleTranslation existingTranslation = articleTranslationMapper.selectOne(queryWrapper);
            
            // 3. 翻译标题
            String translatedTitle = translateText(article.getArticleTitle(), "zh", "en");
            if (translatedTitle == null || translatedTitle.trim().isEmpty()) {
                log.warn("标题翻译失败，使用原标题，文章ID: {}", articleId);
                translatedTitle = article.getArticleTitle();
            }
            
            // 4. 翻译内容（分段处理长文本）
            String translatedContent = translateLongText(article.getArticleContent(), "zh", "en");
            if (translatedContent == null || translatedContent.trim().isEmpty()) {
                log.warn("内容翻译失败，使用原内容，文章ID: {}", articleId);
                translatedContent = article.getArticleContent();
            }
            
            // 5. 保存或更新翻译结果
            if (existingTranslation != null) {
                // 更新现有翻译
                existingTranslation.setTitle(translatedTitle);
                existingTranslation.setContent(translatedContent);
                existingTranslation.setUpdateTime(LocalDateTime.now());
                articleTranslationMapper.updateById(existingTranslation);
                log.info("更新文章翻译成功，文章ID: {}", articleId);
            } else {
                // 创建新翻译
                ArticleTranslation newTranslation = new ArticleTranslation();
                newTranslation.setArticleId(articleId);
                newTranslation.setLanguage("en");
                newTranslation.setTitle(translatedTitle);
                newTranslation.setContent(translatedContent);
                newTranslation.setCreateTime(LocalDateTime.now());
                newTranslation.setUpdateTime(LocalDateTime.now());
                articleTranslationMapper.insert(newTranslation);
                log.info("创建文章翻译成功，文章ID: {}", articleId);
            }
            
        } catch (Exception e) {
            log.error("翻译文章失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }
    
    /**
     * 删除文章的所有翻译
     */
    public void refreshArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            articleTranslationMapper.delete(queryWrapper);
            log.info("删除文章翻译成功，文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }
    
    /**
     * 翻译长文本
     */
    private String translateLongText(String content, String sourceLang, String targetLang) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        try {
            // 直接翻译整个文本，保持上下文连贯性
            String translatedContent = translateText(content, sourceLang, targetLang);
            if (translatedContent != null && !translatedContent.trim().isEmpty()) {
                return translatedContent;
            } else {
                // 翻译失败时保留原文
                return content;
            }
            
        } catch (Exception e) {
            log.error("长文本翻译失败: {}", e.getMessage(), e);
            return content; // 翻译失败时返回原文
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
                log.debug("成功获取文章翻译，文章ID: {}, 语言: {}", articleId, language);
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
    public String translateText(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        try {
            // 构建请求参数，设置默认中文转英文
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("source_lang", sourceLang != null ? sourceLang : "zh");  // 默认中文
            requestBody.put("target_lang", targetLang != null ? targetLang : "en");  // 默认英文
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // 调用Python端翻译服务
            String url = pythonServiceUrl + "/api/translation/translate";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer code = (Integer) responseBody.get("code");
                
                if (code != null && code == 200) {
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    if (data != null) {
                        String translatedText = (String) data.get("translated_text");
                        if (translatedText != null && !translatedText.trim().isEmpty()) {
                            log.debug("翻译成功: {} -> {}", 
                                    text.length() > 50 ? text.substring(0, 50) + "..." : text,
                                    translatedText.length() > 50 ? translatedText.substring(0, 50) + "..." : translatedText);
                            return translatedText;
                        }
                    }
                } else {
                    String message = (String) responseBody.get("message");
                    log.warn("翻译失败: {}", message);
                }
            }
            
            log.warn("翻译服务返回异常响应，使用原文");
            return text; // 翻译失败时返回原文
            
        } catch (Exception e) {
            log.error("调用翻译服务失败: {}", e.getMessage(), e);
            return text; // 翻译失败时返回原文
        }
    }
} 