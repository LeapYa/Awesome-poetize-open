package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * <p>
 * 后台文章 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminArticleController {

    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private SummaryService summaryService;
    
    @Autowired
    private TranslationService translationService;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private com.ld.poetry.service.SitemapService sitemapService;

    @Autowired
    private com.ld.poetry.service.ai.rag.RagSyncService ragSyncService;

    /**
     * 用户查询文章
     */
    @PostMapping("/article/user/list")
    @LoginCheck(1)
    public PoetryResult<Page> listUserArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listAdminArticle(baseRequestVO, false);
    }

    /**
     * Boss查询文章
     */
    @PostMapping("/article/boss/list")
    @LoginCheck(1)
    public PoetryResult<Page> listBossArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listAdminArticle(baseRequestVO, true);
    }

    /**
     * 修改文章状态
     */
    @GetMapping("/article/changeArticleStatus")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_STATUS_CHANGE", targetType = "ARTICLE", targetIdParam = "articleId", summary = "修改文章状态")
    public PoetryResult changeArticleStatus(@RequestParam("articleId") Integer articleId,
                                            @RequestParam(value = "viewStatus", required = false) Boolean viewStatus,
                                            @RequestParam(value = "commentStatus", required = false) Boolean commentStatus,
                                            @RequestParam(value = "recommendStatus", required = false) Boolean recommendStatus) {
        LambdaUpdateChainWrapper<Article> updateChainWrapper = articleService.lambdaUpdate()
                .eq(Article::getId, articleId)
                .eq(Article::getUserId, PoetryUtil.getUserId());
        if (viewStatus != null) {
            updateChainWrapper.set(Article::getViewStatus, viewStatus);
        }
        if (commentStatus != null) {
            updateChainWrapper.set(Article::getCommentStatus, commentStatus);
        }
        if (recommendStatus != null) {
            updateChainWrapper.set(Article::getRecommendStatus, recommendStatus);
        }
        updateChainWrapper.update();
        
        // 如果修改了文章可见性，需要更新sitemap
        if (viewStatus != null) {
            try {
                // 清除sitemap缓存（文章可见性变更会影响sitemap内容）
                if (sitemapService != null) {
                    sitemapService.updateArticleSitemap(articleId);
                    log.info("文章可见性状态变更后已清除sitemap缓存，文章ID: {}, 新状态: {}", articleId, viewStatus);
                }
            } catch (Exception e) {
                log.warn("文章可见性状态变更后更新sitemap失败，文章ID: {}", articleId, e);
            }
            ragSyncService.syncArticleAsync(articleId);
        }
        
        return PoetryResult.success();
    }

    /**
     * 查询文章
     */
    @GetMapping("/article/getArticleById")
    @LoginCheck(1)
    public PoetryResult<ArticleVO> getArticleByIdForUser(@RequestParam("id") Integer id) {
        return articleService.getArticleByIdForUser(id);
    }
    
    /**
     * 手动生成文章摘要
     */
    @PostMapping("/article/generateSummary")
    @LoginCheck(1)
    public PoetryResult generateSummary(@RequestParam Integer articleId) {
        try {
            // 使用虚拟线程在后台执行
            Thread.ofVirtual().start(() -> summaryService.generateAndSaveSummary(articleId));
            return PoetryResult.success("摘要生成任务已启动");
        } catch (Exception e) {
            log.error("启动摘要生成任务失败", e);
            return PoetryResult.fail("启动摘要生成任务失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量生成摘要（为所有没有摘要的文章生成摘要）
     */
    @PostMapping("/article/generateAllSummaries") 
    @LoginCheck(0)  // Boss权限
    @AuditLog(action = "ARTICLE_SUMMARY_BATCH_GENERATE", targetType = "ARTICLE", summary = "批量生成文章摘要")
    public PoetryResult generateAllSummaries() {
        try {
            // 查找所有没有摘要的文章
            java.util.List<Article> articlesWithoutSummary = articleService.lambdaQuery()
                .select(Article::getId)
                .and(wrapper -> wrapper.isNull(Article::getSummary).or().eq(Article::getSummary, ""))
                .list();
                
            if (articlesWithoutSummary.isEmpty()) {
                return PoetryResult.success("所有文章都已有摘要");
            }
            
            // 使用虚拟线程批量生成所有摘要
            for (Article article : articlesWithoutSummary) {
                Thread.ofVirtual().start(() -> summaryService.generateAndSaveSummary(article.getId()));
            }
            
            return PoetryResult.success("已启动" + articlesWithoutSummary.size() + "篇文章的摘要生成任务");
        } catch (Exception e) {
            log.error("批量生成摘要失败", e);
            return PoetryResult.fail("批量生成摘要失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取文章的所有翻译语言
     */
    @GetMapping("/article/getAvailableLanguages")
    @LoginCheck(1)
    public PoetryResult getArticleAvailableLanguages(@RequestParam("articleId") Integer articleId) {
        try {
            java.util.List<String> languages = translationService.getArticleAvailableLanguages(articleId);
            return PoetryResult.success(languages);
        } catch (Exception e) {
            log.error("获取文章可用翻译语言失败", e);
            return PoetryResult.fail("获取文章可用翻译语言失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除文章的特定语言翻译
     */
    @PostMapping("/article/deleteTranslation")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_TRANSLATION_DELETE", targetType = "ARTICLE", targetIdParam = "articleId", summary = "删除文章翻译")
    public PoetryResult deleteArticleTranslation(@RequestBody Map<String, Object> requestData) {
        Integer articleId = (Integer) requestData.get("articleId");
        String language = (String) requestData.get("language");
        
        if (articleId == null || language == null) {
            return PoetryResult.fail("参数不完整");
        }
        try {
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限（只能删除自己的文章翻译，或者Boss可以删除所有）
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限删除此文章的翻译");
            }
            
            // 删除特定语言的翻译
            boolean deleted = translationService.deleteSpecificTranslation(articleId, language);
            
            if (deleted) {
                log.info("用户 {} 删除了文章 {} 的 {} 翻译", currentUserId, articleId, language);
                return PoetryResult.success("翻译删除成功");
            } else {
                return PoetryResult.fail("翻译不存在或删除失败");
            }
        } catch (Exception e) {
            log.error("删除文章翻译失败", e);
            return PoetryResult.fail("删除翻译失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除文章的所有翻译
     */
    @PostMapping("/article/deleteAllTranslations")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_TRANSLATION_DELETE_ALL", targetType = "ARTICLE", targetIdParam = "articleId", summary = "删除文章全部翻译")
    public PoetryResult deleteAllArticleTranslations(@RequestBody Map<String, Object> requestData) {
        Integer articleId = (Integer) requestData.get("articleId");
        
        if (articleId == null) {
            return PoetryResult.fail("参数不完整");
        }
        try {
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限删除此文章的翻译");
            }
            
            // 删除所有翻译
            translationService.deleteArticleTranslation(articleId);
            
            log.info("用户 {} 删除了文章 {} 的所有翻译", currentUserId, articleId);
            return PoetryResult.success("所有翻译删除成功");
        } catch (Exception e) {
            log.error("删除文章所有翻译失败", e);
            return PoetryResult.fail("删除所有翻译失败：" + e.getMessage());
        }
    }
    
    /**
     * 重新生成文章翻译
     */
    @PostMapping("/article/regenerateTranslations")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_TRANSLATION_REGENERATE", targetType = "ARTICLE", targetIdParam = "articleId", summary = "重新生成文章翻译")
    public PoetryResult regenerateArticleTranslations(@RequestParam("articleId") Integer articleId) {
        try {
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限重新生成此文章的翻译");
            }
            
            // 删除现有翻译并重新生成
            translationService.refreshArticleTranslation(articleId);
            
            log.info("用户 {} 重新生成了文章 {} 的翻译", currentUserId, articleId);
            return PoetryResult.success("翻译重新生成任务已启动");
        } catch (Exception e) {
            log.error("重新生成文章翻译失败", e);
            return PoetryResult.fail("重新生成翻译失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新文章sitemap
     */
    /**
     * 手动更新文章sitemap
     */
    @PostMapping("/article/updateSitemap")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_SITEMAP_UPDATE", targetType = "ARTICLE", targetIdParam = "articleId", summary = "更新文章Sitemap")
    public PoetryResult updateArticleSitemap(@RequestBody Map<String, Object> requestData) {
        try {
            Integer articleId = (Integer) requestData.get("articleId");
            String action = (String) requestData.get("action");
            
            if (articleId == null) {
                return PoetryResult.fail("缺少文章ID参数");
            }
            
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限操作此文章的sitemap");
            }
            
            // 使用Java端的sitemap服务
            try {
                log.info("用户 {} 手动更新文章 {} 的sitemap，操作: {}", currentUserId, articleId, action);
                
                // 调用Java端sitemap服务
                sitemapService.updateArticleSitemap(articleId);
                
                // 如果是手动操作，可以立即重新生成sitemap
                if ("regenerate".equals(action)) {
                    String sitemap = sitemapService.generateSitemapDirect();
                    if (sitemap != null) {
                        int urlCount = sitemap.split("<url>").length - 1;
                        log.info("手动重新生成sitemap成功，包含 {} 个URL", urlCount);
                        return PoetryResult.success("Sitemap已重新生成，包含 " + urlCount + " 个URL");
                    } else {
                        return PoetryResult.fail("Sitemap重新生成失败");
                    }
                } else {
                    log.info("文章sitemap缓存已清除，下次访问时将包含最新内容");
                    return PoetryResult.success("文章sitemap已更新，缓存已清除");
                }
                
            } catch (Exception serviceException) {
                log.error("调用Java端sitemap服务失败，文章ID: {}, 错误: {}", articleId, serviceException.getMessage(), serviceException);
                return PoetryResult.fail("sitemap更新失败：" + serviceException.getMessage());
            }
            
        } catch (Exception e) {
            log.error("更新文章sitemap失败", e);
            return PoetryResult.fail("更新sitemap失败：" + e.getMessage());
        }
    }
}
