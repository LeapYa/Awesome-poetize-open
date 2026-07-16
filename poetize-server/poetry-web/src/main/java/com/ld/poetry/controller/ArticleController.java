package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.entity.Article;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.service.SeoMetaService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.Label;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.dao.LabelMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.service.ai.AiImageService;
import java.util.Map;
import java.util.HashMap;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.ld.poetry.service.impl.ArticleServiceImpl;
import org.springframework.web.client.RestTemplate;
import com.ld.poetry.event.ArticleSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.ld.poetry.service.MailTemplateService;
import com.ld.poetry.service.QRCodeService;
import com.ld.poetry.service.SysPluginService;
import com.ld.poetry.entity.SysPlugin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * <p>
 * 文章表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 * @version 2026-01-17 优化线程模型，适配Java 25虚拟线程
 */
@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private MailService mailService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private SeoService seoService;

    @Autowired
    private SeoMetaService seoMetaService;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private QRCodeService qrCodeService;

    @Autowired
    private MailTemplateService mailTemplateService;

    @Autowired
    private SysPluginService sysPluginService;

    @Autowired
    @Qualifier("cpuIntensiveExecutor")
    private Executor cpuIntensiveExecutor;

    @Autowired
    @Qualifier("asyncExecutor")
    private Executor asyncExecutor;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private AiImageService aiImageService;

    /**
     * 分类名称 → 锁 的映射，防止并发导入时同名分类被重复创建。
     * key 为 sort_name（小写），value 为对应的互斥锁。
     * 使用 ConcurrentHashMap + ReentrantLock 实现细粒度锁：
     * 不同名称互不阻塞，同名并发只有一个线程执行 INSERT。
     */
    private final ConcurrentHashMap<String, ReentrantLock> sortNameLocks = new ConcurrentHashMap<>();

    /**
     * 标签名称 → 锁 的映射，防止并发导入时同名标签被重复创建。
     * key 为 "sortId:label_name"（小写），value 为对应的互斥锁。
     */
    private final ConcurrentHashMap<String, ReentrantLock> labelNameLocks = new ConcurrentHashMap<>();

    /**
     * 保存文章（同步版本）
     */
    @LoginCheck(1)
    @PostMapping("/saveArticle")
    @AuditLog(action = "ARTICLE_CREATE", targetType = "ARTICLE", targetIdParam = "id", summary = "保存文章")
    public PoetryResult saveArticle(@Validated @RequestBody ArticleVO articleVO,
                                   @RequestParam(value = "skipAiTranslation", required = false) Boolean skipAiTranslation,
                                   @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                   @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                   @RequestParam(value = "pendingTranslationSummary", required = false) String pendingTranslationSummary,
                                   @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        // 如果传入了 sortName/labelName 而没有 sortId/labelId，自动解析或创建
        resolveSortAndLabelByName(articleVO);
        
        // 手动校验：经过 resolveSortAndLabelByName 后 sortId/labelId 必须有值
        if (articleVO.getSortId() == null) {
            return PoetryResult.fail("文章分类不能为空，请指定 sortId 或 sortName");
        }
        if (articleVO.getLabelId() == null) {
            return PoetryResult.fail("文章标签不能为空，请指定 labelId 或 labelName");
        }
        
        try {
            long step1Time = System.currentTimeMillis();
            
            // 确保用户ID不为空
            if (articleVO.getUserId() == null) {
                // 尝试获取当前用户ID
                Integer currentUserId = PoetryUtil.getUserId();
                if (currentUserId == null) {
                    // 使用PoetryUtil获取当前用户（已集成Redis缓存）
                    User user = PoetryUtil.getCurrentUser();
                    if (user != null) {
                        currentUserId = user.getId();
                    }
                }
                
                if (currentUserId == null) {
                    return PoetryResult.fail("无法获取当前用户信息，请重新登录后再试");
                }
                articleVO.setUserId(currentUserId);
            }
            
            // 使用Redis缓存清理替换PoetryCache
            if (articleVO.getUserId() != null) {
                // 清理用户文章列表缓存
                String userArticleKey = CacheConstants.buildUserArticleListKey(articleVO.getUserId());
                cacheService.deleteKey(userArticleKey);
            }
            // 清理文章相关缓存
            cacheService.evictSortArticleList();
            
            boolean resolvedSkipAiTranslation = resolveSkipAiTranslation(articleVO, skipAiTranslation);
            Map<String, String> pendingTranslation = buildPendingTranslation(articleVO,
                    pendingTranslationTitle, pendingTranslationContent, pendingTranslationSummary,
                    pendingTranslationLanguage);
            
            // 保存文章（传递skipAiTranslation和pendingTranslation参数）
            PoetryResult result = articleService.saveArticle(articleVO, resolvedSkipAiTranslation, pendingTranslation);
            
            // 如果保存成功并且文章有ID，执行后续任务
            if (result.getCode() == 200 && articleVO.getId() != null) {
                final Integer articleId = articleVO.getId();
                
                // 异步预生成文章二维码
                new Thread(() -> {
                    try {
                        qrCodeService.preGenerateArticleQRCode(articleId);
                    } catch (Exception e) {
                        log.warn("二维码预生成失败（不影响保存），文章ID: {}: {}", articleId, e.getMessage());
                    }
                }).start();
                
                // 如果需要推送至搜索引擎且文章可见，异步处理
                if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()) && Boolean.TRUE.equals(articleVO.getViewStatus())) {
                    // 异步执行SEO推送，避免阻塞用户操作
                    new Thread(() -> {
                        try {
                            Map<String, Object> seoResult = seoService.submitToSearchEngines(articleId);
                            String status = (String) seoResult.get("status");
                            String message = (String) seoResult.get("message");
                            log.info("搜索引擎推送完成，文章ID: {}, 状态: {}, {}", articleId, status, message);
                        } catch (Exception e) {
                            log.error("搜索引擎推送失败，但不影响文章保存，文章ID: " + articleId, e);
                        }
                    }).start();
                }
            }
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("保存文章失败: " + e.getMessage());
        }
    }

    /**
     * 异步保存文章（快速响应版本）
     */
    @LoginCheck(1)
    @PostMapping("/saveArticleAsync")
    @AuditLog(action = "ARTICLE_CREATE_ASYNC", targetType = "ARTICLE", targetIdParam = "id", summary = "异步保存文章")
    public PoetryResult<String> saveArticleAsync(@Validated @RequestBody ArticleVO articleVO,
                                                @RequestParam(value = "skipAiTranslation", required = false) Boolean skipAiTranslation,
                                                @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                                @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                                @RequestParam(value = "pendingTranslationSummary", required = false) String pendingTranslationSummary,
                                                @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        // 如果传入了 sortName/labelName 而没有 sortId/labelId，自动解析或创建
        resolveSortAndLabelByName(articleVO);
        
        // 手动校验：经过 resolveSortAndLabelByName 后 sortId/labelId 必须有值
        if (articleVO.getSortId() == null) {
            return PoetryResult.fail("文章分类不能为空，请指定 sortId 或 sortName");
        }
        if (articleVO.getLabelId() == null) {
            return PoetryResult.fail("文章标签不能为空，请指定 labelId 或 labelName");
        }
        
        try {
            // 确保用户ID不为空
            if (articleVO.getUserId() == null) {
                Integer currentUserId = PoetryUtil.getUserId();
                if (currentUserId == null) {
                    // 使用PoetryUtil获取当前用户（已集成Redis缓存）
                    User user = PoetryUtil.getCurrentUser();
                    if (user != null) {
                        currentUserId = user.getId();
                    }
                }
                
                if (currentUserId == null) {
                    return PoetryResult.fail("无法获取当前用户信息，请重新登录后再试");
                }
                articleVO.setUserId(currentUserId);
            }
            
            boolean resolvedSkipAiTranslation = resolveSkipAiTranslation(articleVO, skipAiTranslation);
            Map<String, String> pendingTranslation = buildPendingTranslation(articleVO,
                    pendingTranslationTitle, pendingTranslationContent, pendingTranslationSummary,
                    pendingTranslationLanguage);

            // 调用异步保存服务
            PoetryResult<String> result = articleService.saveArticleAsync(articleVO, resolvedSkipAiTranslation, pendingTranslation);
            
            // 使用Redis缓存清理替换PoetryCache
            if (articleVO.getUserId() != null) {
                // 清理用户文章列表缓存
                String userArticleKey = CacheConstants.buildUserArticleListKey(articleVO.getUserId());
                cacheService.deleteKey(userArticleKey);
            }
            // 清理文章相关缓存
            cacheService.evictSortArticleList();
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("启动异步保存失败: " + e.getMessage());
        }
    }

    /**
     * 查询文章保存状态
     */
    @LoginCheck(value = 1, silentLog = true)
    @GetMapping("/getArticleSaveStatus")
    public PoetryResult<ArticleServiceImpl.ArticleSaveStatus> getArticleSaveStatus(@RequestParam("taskId") String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return PoetryResult.fail("任务ID不能为空");
        }
        
        try {
            PoetryResult<ArticleServiceImpl.ArticleSaveStatus> result = articleService.getArticleSaveStatus(taskId);
            return result;
        } catch (Exception e) {
            log.error("查询保存状态异常: {}", e.getMessage(), e);
            return PoetryResult.fail("查询保存状态失败: " + e.getMessage());
        }
    }

    @LoginCheck(value = 1, silentLog = true)
    @GetMapping(value = "/streamArticleSaveStatus", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamArticleSaveStatus(@RequestParam("taskId") String taskId) {
        if (!StringUtils.hasText(taskId)) {
            SseEmitter emitter = new SseEmitter(5000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "任务ID不能为空")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        return articleService.streamArticleSaveStatus(taskId);
    }

    @LoginCheck(value = 1, silentLog = true)
    @GetMapping(value = "/streamArticleSaveStatusBatch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamArticleSaveStatusBatch(@RequestParam("taskIds") String taskIds) {
        if (!StringUtils.hasText(taskIds)) {
            SseEmitter emitter = new SseEmitter(5000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("task_error")
                        .data(Map.of("message", "任务ID不能为空")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        java.util.List<String> taskIdList = java.util.Arrays.stream(taskIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (taskIdList.isEmpty()) {
            SseEmitter emitter = new SseEmitter(5000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("task_error")
                        .data(Map.of("message", "未提供有效任务ID")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        return articleService.streamArticleSaveStatusBatch(taskIdList);
    }

    /**
     * 删除文章
     */
    @GetMapping("/deleteArticle")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DELETE", targetType = "ARTICLE", targetIdParam = "id", summary = "删除文章")
    public PoetryResult deleteArticle(@RequestParam("id") Integer id) {
        // 在删除前先获取文章信息，以便获取分类ID用于预渲染
        Integer sortId = null;
        Integer labelId = null;
        String articleSlug = null;
        try {
            Article article = articleService.getById(id);
            if (article != null) {
                sortId = article.getSortId();
                labelId = article.getLabelId();
                articleSlug = article.getArticleSlug();
                log.info("删除文章前获取分类和标签ID: 文章ID={}, 分类ID={}, 标签ID={}", id, sortId, labelId);
            }
        } catch (Exception e) {
            log.warn("删除文章前获取分类/标签ID失败，将影响分类页面预渲染: 文章ID={}, 错误={}", id, e.getMessage());
        }
        
        // 使用Redis缓存清理替换PoetryCache
        Integer userId = PoetryUtil.getUserId();
        if (userId != null) {
            String userArticleKey = CacheConstants.buildUserArticleListKey(userId);
            cacheService.deleteKey(userArticleKey);
        }
        // 清理文章相关缓存
        cacheService.evictSortArticleList();
        
        // 删除文章翻译（仅删除，不重新翻译）
        try {
            translationService.deleteArticleTranslation(id);
        } catch (Exception e) {
            log.error("删除文章翻译失败", e);
        }

        PoetryResult result = articleService.deleteArticle(id);

        if (result.getCode() == 200) {
            // 发布文章删除事件，触发预渲染清理（在事务提交后执行）
            // 传递正确的分类ID，确保分类页面也会被重新渲染
            eventPublisher.publishEvent(new ArticleSavedEvent(id, sortId, labelId, sortId, labelId, null, false, "DELETE", null, articleSlug));
            
            // 清除文章二维码缓存
            qrCodeService.evictArticleQRCode(id);
            log.info("文章ID {} 删除成功，已清除二维码缓存", id);
            
            // 注意：sitemap更新已通过ArticleEventListener中的updateSitemapAsync方法处理
            // 无需在这里重复处理sitemap更新逻辑
        }

        return result;
    }


    /**
     * 更新文章
     */
    @LoginCheck(1)
    @PostMapping("/updateArticle")
    @AuditLog(action = "ARTICLE_UPDATE", targetType = "ARTICLE", targetIdParam = "id", summary = "更新文章")
    public PoetryResult updateArticle(@Validated @RequestBody ArticleVO articleVO,
                                     @RequestParam(value = "skipAiTranslation", required = false) Boolean skipAiTranslation,
                                     @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                     @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                     @RequestParam(value = "pendingTranslationSummary", required = false) String pendingTranslationSummary,
                                     @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 使用Redis缓存清理替换PoetryCache
        Integer userId = PoetryUtil.getUserId();
        if (userId != null) {
            String userArticleKey = CacheConstants.buildUserArticleListKey(userId);
            cacheService.deleteKey(userArticleKey);
        }
        // 清理文章相关缓存
        cacheService.evictSortArticleList();
        
        boolean resolvedSkipAiTranslation = resolveSkipAiTranslation(articleVO, skipAiTranslation);
        Map<String, String> pendingTranslation = buildPendingTranslation(articleVO,
                pendingTranslationTitle, pendingTranslationContent, pendingTranslationSummary,
                pendingTranslationLanguage);
        Integer currentUserId = PoetryUtil.getUserId();
        
        // 更新文章（传递skipAiTranslation和pendingTranslation参数）
        PoetryResult result = articleService.updateArticle(articleVO, resolvedSkipAiTranslation, pendingTranslation, currentUserId);
        
        // 更新文章成功后执行后续任务
        if (result.getCode() == 200 && articleVO.getId() != null) {
            final Integer articleId = articleVO.getId();
            
            // 清除旧的二维码缓存并异步预生成新的二维码
            cpuIntensiveExecutor.execute(() -> {
                try {
                    // 先清除旧缓存
                    qrCodeService.evictArticleQRCode(articleId);
                    // 再预生成新二维码
                    qrCodeService.preGenerateArticleQRCode(articleId);
                } catch (Exception e) {
                    log.warn("二维码更新失败（不影响更新），文章ID: {}: {}", articleId, e.getMessage());
                }
            });
            
            // 如果需要推送至搜索引擎且文章可见，异步处理
            if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()) && Boolean.TRUE.equals(articleVO.getViewStatus())) {
                // 异步执行SEO推送，避免阻塞用户操作
                asyncExecutor.execute(() -> {
                    try {
                        Map<String, Object> seoResult = seoService.submitToSearchEngines(articleId);
                        String status = (String) seoResult.get("status");
                        String message = (String) seoResult.get("message");
                        log.info("搜索引擎推送完成，文章ID: {}, 状态: {}, {}", articleId, status, message);
                    } catch (Exception e) {
                        log.error("搜索引擎推送失败，但不影响文章更新，文章ID: " + articleId, e);
                    }
                });
            }
        }
        
        return result;
    }


    /**
     * 查询文章List
     */
    @PostMapping("/listArticle")
    public PoetryResult<Page> listArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listArticle(baseRequestVO);
    }

    /**
     * 获取翻译匹配的内容
     */
    @GetMapping("/translation/{id}")
    public PoetryResult<ArticleVO> getTranslationContent(@PathVariable("id") Integer id,
                                                         @RequestParam(value = "searchKey", required = false) String searchKey,
                                                         @RequestParam(value = "language", required = false) String language) {
        return PoetryResult.success(articleService.getTranslationContent(id, searchKey, language));
    }

    /**
     * 查询分类文章List
     */
    @GetMapping("/listSortArticle")
    public PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        return articleService.listSortArticle();
    }

    /**
     * 获取文章翻译
     */
    @GetMapping("/getTranslation")
    public PoetryResult<Map<String, String>> getTranslation(@RequestParam("id") Integer id,
                                     @RequestParam(value = "language", defaultValue = "en") String language) {
        // 检查参数
        if (id == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        if (!StringUtils.hasText(language)) {
            return PoetryResult.fail("翻译语言不能为空");
        }

        try {
            // 获取文章翻译
            Map<String, String> translationResult = translationService.getArticleTranslation(id, language);
            return PoetryResult.success(translationResult);
        } catch (Exception e) {
            log.error("获取文章翻译失败", e);
            return PoetryResult.fail("获取翻译失败：" + e.getMessage());
        }
    }

    /**
     * 查询文章 - 使用路径参数，仅匹配数字ID，避免与具体路径冲突
     */
    @GetMapping("/{id:\\d+}")
    public PoetryResult<ArticleVO> getArticleByPathId(@PathVariable Integer id, @RequestParam(value = "password", required = false) String password) {
        PoetryResult<ArticleVO> result = articleService.getArticleById(id, password);
        enrichArticleResponse(result, null);
        return result;
    }

    /**
     * 查询文章 - 使用请求参数
     * @param id 文章ID
     * @param password 文章密码（可选）
     * @param language 目标语言（可选，如：en, zh, ja等）- 优化：一次请求返回翻译内容
     */
    @GetMapping("/getArticleById")
    public PoetryResult<ArticleVO> getArticleById(
            @RequestParam("id") Integer id, 
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "language", required = false) String language) {
        
        // 获取文章
        PoetryResult<ArticleVO> result = articleService.getArticleById(id, password);
        
        enrichArticleResponse(result, language);
        
        return result;
    }

    /**
     * 查询文章(不增加浏览量)
     * 用于元数据获取、SEO等不需要增加访问量的场景
     */
    @GetMapping("/getArticleByIdNoCount")
    public PoetryResult<ArticleVO> getArticleByIdNoCount(@RequestParam("id") Integer id, @RequestParam(value = "password", required = false) String password) {
        return ((ArticleServiceImpl)articleService).getArticleById(id, password, false);
    }

    /**
     * 查询文章 - 支持数字ID或URL别名
     */
    @GetMapping("/getArticleByPath")
    public PoetryResult<ArticleVO> getArticleByPath(
            @RequestParam("path") String path,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "language", required = false) String language) {
        PoetryResult<ArticleVO> result = articleService.getArticleByPath(path, password);
        enrichArticleResponse(result, language);
        return result;
    }

    /**
     * 查询文章 - 支持数字ID或URL别名，不增加浏览量
     */
    @GetMapping("/getArticleByPathNoCount")
    public PoetryResult<ArticleVO> getArticleByPathNoCount(
            @RequestParam("path") String path,
            @RequestParam(value = "password", required = false) String password) {
        return articleService.getArticleByPath(path, password, false);
    }

    private void enrichArticleResponse(PoetryResult<ArticleVO> result, String language) {
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            return;
        }

        ArticleVO article = result.getData();
        Integer articleId = article.getId();

        try {
            SysPlugin themePlugin = sysPluginService.getActivePlugin(SysPlugin.TYPE_ARTICLE_THEME);
            if (themePlugin != null && StringUtils.hasText(themePlugin.getPluginConfig())) {
                article.setArticleThemeConfig(themePlugin.getPluginConfig());
            }
        } catch (Exception e) {
            log.warn("获取文章主题配置失败（不影响主流程）: {}", e.getMessage());
        }

        if (articleId != null) {
            try {
                article.setAvailableLanguages(translationService.getArticleAvailableLanguages(articleId));
            } catch (Exception e) {
                log.warn("获取文章可用翻译语言失败（不影响主流程）: 文章ID={}, 错误={}",
                        articleId, e.getMessage());
            }
        }

        try {
            Map<String, Object> defaultLanguages = sysAiConfigService.getDefaultLanguages();
            if (defaultLanguages != null) {
                Object sourceLang = defaultLanguages.get("default_source_lang");
                Object targetLang = defaultLanguages.get("default_target_lang");
                article.setDefaultSourceLang(sourceLang == null ? "zh" : sourceLang.toString());
                article.setDefaultTargetLang(targetLang == null ? "en" : targetLang.toString());
            }
            article.setLanguageMap(sysAiConfigService.getLanguageMapping());
        } catch (Exception e) {
            log.warn("获取文章语言基础配置失败（不影响主流程）: {}", e.getMessage());
        }

        if (articleId != null) {
            try {
                String metaLanguage = StringUtils.hasText(language)
                        ? language
                        : (StringUtils.hasText(article.getDefaultSourceLang()) ? article.getDefaultSourceLang() : "zh");
                article.setSeoMeta(seoMetaService.generateArticleMeta(articleId, metaLanguage));
            } catch (Exception e) {
                log.warn("生成文章SEO元信息失败（不影响主流程）: 文章ID={}, 错误={}",
                        articleId, e.getMessage());
            }
        }

        if (StringUtils.hasText(language) && articleId != null) {
            try {
                Map<String, String> translation = translationService.getArticleTranslation(articleId, language);
                if (translation != null && !translation.isEmpty()) {
                    article.setTranslatedTitle(translation.get("title"));
                    article.setTranslatedContent(translation.get("content"));
                    if (StringUtils.hasText(translation.get("summary"))) {
                        article.setSummary(translation.get("summary"));
                    }
                }
            } catch (Exception e) {
                log.warn("获取文章翻译失败（不影响主流程）: 文章ID={}, 语言={}, 错误={}",
                        articleId, language, e.getMessage());
            }
        }
    }

    /**
     * 获取热门文章列表（智能热度算法排序）
     * 综合考虑浏览量、点赞数、评论数、发布时间、互动率等多个因素
     */
    @GetMapping("/getArticlesByLikesTop")
    public PoetryResult<List<ArticleVO>> getArticlesByLikesTop() {
        return articleService.getArticlesByLikesTop();
    }

    /**
     * 获取热门文章列表（智能热度算法排序）
     * 综合考虑浏览量、点赞数、评论数、发布时间、互动率等多个因素
     * 推荐使用此端点，命名更准确
     */
    @GetMapping("/getHotArticles")
    public PoetryResult<List<ArticleVO>> getHotArticles() {
        return articleService.getArticlesByLikesTop();
    }

    /**
     * 接收SEO推送结果并发送邮件通知
     * 此接口由Python SEO模块调用
     */
    @PostMapping("/notifySeoResult")
    public PoetryResult notifySeoResult(@RequestBody Map<String, Object> notificationData) {
        try {
            log.info("收到SEO推送结果通知: {}", notificationData);
            
            // 1. 提取所需数据
            Integer articleId = null;
            if (notificationData.containsKey("articleId") && notificationData.get("articleId") != null) {
                articleId = Integer.parseInt(notificationData.get("articleId").toString());
            }
            
            String title = notificationData.containsKey("title") ? notificationData.get("title").toString() : "未知文章";
            String url = notificationData.containsKey("url") ? notificationData.get("url").toString() : "";
            boolean success = notificationData.containsKey("success") && Boolean.parseBoolean(notificationData.get("success").toString());
            String timestamp = notificationData.containsKey("timestamp") ? notificationData.get("timestamp").toString() : 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> results = notificationData.containsKey("results") ? 
                (Map<String, Object>) notificationData.get("results") : new HashMap<>();
            
            // 确定收件人列表
            List<String> recipients = new ArrayList<>();
            
            // 直接使用文章作者的邮箱（移除了独立的通知邮箱字段）
            if (articleId != null) {
                // 查询文章信息以获取作者ID
                ArticleVO article = articleService.getArticleById(articleId, null).getData();
                if (article == null) {
                    log.warn("未找到文章信息，文章ID: {}", articleId);
                    return PoetryResult.success("已接收SEO推送结果，但未找到文章信息");
                }
                
                Integer authorId = article.getUserId();
                if (authorId == null) {
                    log.warn("无法确定文章作者，文章ID: {}", articleId);
                    return PoetryResult.success("已接收SEO推送结果，但无法确定文章作者");
                }
                
                // 查询作者信息
                User author = userService.getById(authorId);
                if (author == null) {
                    log.warn("未找到文章作者信息，作者ID: {}", authorId);
                    return PoetryResult.success("已接收SEO推送结果，但未找到作者信息");
                }
                
                // 如果作者有邮箱，添加到收件人列表
                if (author.getEmail() != null && !author.getEmail().isEmpty()) {
                    recipients.add(author.getEmail());
                    log.info("使用文章作者邮箱: {}", author.getEmail());
                }
            }
            
            // 如果没有收件人，不发送邮件
            if (recipients.isEmpty()) {
                log.info("没有有效的收件人，不发送SEO推送结果通知");
                return PoetryResult.success("已接收SEO推送结果，但无法发送通知");
            }
            
            // 4. 使用模板服务生成邮件内容
            String emailContent = mailTemplateService.generateSeoNotificationEmail(title, url, success, timestamp, results);
            
            // 5. 发送邮件通知
            String subject = (success ? "SEO推送成功: " : "SEO推送失败: ") + title;
            boolean mailSent = mailService.sendMail(recipients, subject, emailContent, true, null);
            
            if (mailSent) {
                log.info("SEO推送结果通知邮件发送成功，收件人: {}", recipients);
                return PoetryResult.success("SEO推送结果通知已发送");
            } else {
                log.warn("SEO推送结果通知邮件发送失败，收件人: {}", recipients);
                return PoetryResult.fail("SEO推送结果通知邮件发送失败");
            }
        } catch (Exception e) {
            log.error("处理SEO推送结果通知出错", e);
            return PoetryResult.fail("处理SEO推送结果通知出错: " + e.getMessage());
        }
    }
    
    /**
     * 手动保存文章翻译
     */
    @PostMapping("/saveManualTranslation")
    @AuditLog(action = "ARTICLE_TRANSLATION_SAVE", targetType = "ARTICLE", targetIdParam = "id", summary = "保存手动翻译")
    public PoetryResult<String> saveManualTranslation(@RequestParam("id") Integer id,
                                                     @RequestParam("targetLanguage") String targetLanguage,
                                                     @RequestParam("translatedTitle") String translatedTitle,
                                                     @RequestParam("translatedContent") String translatedContent,
                                                     @RequestParam(value = "translatedSummary", required = false) String translatedSummary) {
        // 检查参数
        if (id == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        if (!StringUtils.hasText(targetLanguage)) {
            return PoetryResult.fail("目标语言不能为空");
        }

        if (!StringUtils.hasText(translatedTitle)) {
            return PoetryResult.fail("翻译标题不能为空");
        }

        if (!StringUtils.hasText(translatedContent)) {
            return PoetryResult.fail("翻译内容不能为空");
        }

        try {
            // 保存手动翻译
            Map<String, Object> result = translationService.saveManualTranslation(id, targetLanguage,
                                                                                 translatedTitle, translatedContent,
                                                                                 translatedSummary);

            if ((Boolean) result.get("success")) {
                return PoetryResult.success((String) result.get("message"));
            } else {
                return PoetryResult.fail((String) result.get("message"));
            }
        } catch (Exception e) {
            log.error("保存手动翻译失败", e);
            return PoetryResult.fail("保存翻译失败：" + e.getMessage());
        }
    }

    /**
     * 生成文章摘要 - 供Python端调用
     */
    @PostMapping("/generateSummary")
    public PoetryResult<String> generateSummary(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");
            Integer maxLength = request.get("maxLength") != null ?
                Integer.parseInt(request.get("maxLength").toString()) : 150;

            return articleService.generateSummary(content, maxLength);
        } catch (Exception e) {
            log.error("摘要生成API调用失败", e);
            return PoetryResult.fail("摘要生成失败: " + e.getMessage());
        }
    }

    /**
     * AI 生图：根据文章标题与正文生成封面图。
     * 调用前需在后台「智能摘要功能配置」下方的「AI生图功能配置」中启用。
     *
     * <p>请求体：{ "title": "...", "content": "..." }
     * 返回：存储后的图片可访问 URL
     */
    @PostMapping("/generateCover")
    @LoginCheck(1)
    public PoetryResult<String> generateCover(@RequestBody Map<String, Object> request) {
        try {
            String title = request.get("title") != null ? request.get("title").toString() : "";
            String content = request.get("content") != null ? request.get("content").toString() : "";

            if (!StringUtils.hasText(content)) {
                return PoetryResult.fail("文章内容不能为空");
            }

            String url = aiImageService.generateCoverFromArticle(title, content);
            return PoetryResult.success(url);
        } catch (IllegalStateException e) {
            // 配置缺失/未启用等业务错误
            log.warn("AI生图被拒绝: {}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("AI生图失败", e);
            return PoetryResult.fail("AI生图失败: " + e.getMessage());
        }
    }

    /**
     * 异步更新文章（快速响应版本）
     */
    @LoginCheck(1)
    @PostMapping("/updateArticleAsync")
    @AuditLog(action = "ARTICLE_UPDATE_ASYNC", targetType = "ARTICLE", targetIdParam = "id", summary = "异步更新文章")
    public PoetryResult<String> updateArticleAsync(@Validated @RequestBody ArticleVO articleVO,
                                                  @RequestParam(value = "skipAiTranslation", required = false) Boolean skipAiTranslation,
                                                  @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                                  @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                                  @RequestParam(value = "pendingTranslationSummary", required = false) String pendingTranslationSummary,
                                                  @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        if (articleVO.getId() == null) {
            return PoetryResult.fail("文章ID不能为空");
        }
        
        try {
            boolean resolvedSkipAiTranslation = resolveSkipAiTranslation(articleVO, skipAiTranslation);
            Map<String, String> pendingTranslation = buildPendingTranslation(articleVO,
                    pendingTranslationTitle, pendingTranslationContent, pendingTranslationSummary,
                    pendingTranslationLanguage);
            Integer currentUserId = PoetryUtil.getUserId();
            String currentUsername = PoetryUtil.getUsername();

            // 调用异步更新服务
            PoetryResult<String> result = articleService.updateArticleAsync(articleVO,
                    resolvedSkipAiTranslation,
                    pendingTranslation,
                    currentUserId,
                    currentUsername,
                    null,
                    null);
            
            // 使用Redis缓存清理替换PoetryCache
            if (currentUserId != null) {
                String userArticleKey = CacheConstants.buildUserArticleListKey(currentUserId);
                cacheService.deleteKey(userArticleKey);
            }
            // 清理文章相关缓存
            cacheService.evictSortArticleList();
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("启动异步更新失败: " + e.getMessage());
        }
    }

    private boolean resolveSkipAiTranslation(ArticleVO articleVO, Boolean requestParamValue) {
        if (requestParamValue != null) {
            return requestParamValue;
        }
        return Boolean.TRUE.equals(articleVO.getSkipAiTranslation());
    }

    private Map<String, String> buildPendingTranslation(ArticleVO articleVO,
                                                        String requestTitle,
                                                        String requestContent,
                                                        String requestSummary,
                                                        String requestLanguage) {
        String title = StringUtils.hasText(requestTitle) ? requestTitle : articleVO.getPendingTranslationTitle();
        String content = StringUtils.hasText(requestContent) ? requestContent : articleVO.getPendingTranslationContent();
        String summary = requestSummary != null ? requestSummary : articleVO.getPendingTranslationSummary();
        String language = StringUtils.hasText(requestLanguage) ? requestLanguage : articleVO.getPendingTranslationLanguage();
        boolean hasManualContent = StringUtils.hasText(title) && StringUtils.hasText(content);
        boolean hasManualSummary = StringUtils.hasText(summary);

        if (!StringUtils.hasText(language) || (!hasManualContent && !hasManualSummary)) {
            return null;
        }

        Map<String, String> pendingTranslation = new HashMap<>();
        if (hasManualContent) {
            pendingTranslation.put("title", title);
            pendingTranslation.put("content", content);
        }
        if (hasManualSummary) {
            pendingTranslation.put("summary", summary);
        }
        pendingTranslation.put("language", language);
        return pendingTranslation;
    }

    /**
     * 根据 sortName / labelName 自动解析或创建分类和标签，
     * 将结果回填到 articleVO 的 sortId / labelId 字段。
     * 供导入文章等场景使用，当前端传入名称而非 ID 时自动处理。
     *
     * @param articleVO 文章VO
     */
    private void resolveSortAndLabelByName(ArticleVO articleVO) {
        // 处理分类名称 → sortId（并发安全：同名分类只创建一次）
        if (articleVO.getSortId() == null && StringUtils.hasText(articleVO.getSortName())) {
            String sortKey = articleVO.getSortName().trim().toLowerCase();
            ReentrantLock sortLock = sortNameLocks.computeIfAbsent(sortKey, k -> new ReentrantLock());
            sortLock.lock();
            try {
                // 双重检查：拿到锁后再查一次，可能其他线程已经创建了
                Sort sort = sortMapper.selectOne(
                        new QueryWrapper<Sort>().eq("sort_name", articleVO.getSortName()));
                if (sort == null) {
                    sort = new Sort();
                    sort.setSortName(articleVO.getSortName());
                    sort.setSortDescription("通过导入创建的分类");
                    sort.setSortType(1);
                    sort.setPriority(99);
                    sortMapper.insert(sort);
                    log.info("导入文章时自动创建分类: {}", articleVO.getSortName());
                }
                articleVO.setSortId(sort.getId());
            } finally {
                sortLock.unlock();
                // 清理锁对象，防止长期运行后 Map 无限增长
                // 只在无人持有时移除，避免影响正在等待的线程
                sortNameLocks.computeIfPresent(sortKey, (k, v) -> v.isLocked() ? v : null);
            }
        }

        // 处理标签名称 → labelId（并发安全：同名标签只创建一次）
        if (articleVO.getLabelId() == null && StringUtils.hasText(articleVO.getLabelName())) {
            // 标签是分类下唯一的，所以锁的 key 需要包含 sortId
            String labelKey = (articleVO.getSortId() != null ? articleVO.getSortId() : "0") + ":" + articleVO.getLabelName().trim().toLowerCase();
            ReentrantLock labelLock = labelNameLocks.computeIfAbsent(labelKey, k -> new ReentrantLock());
            labelLock.lock();
            try {
                // 双重检查
                Label label = labelMapper.selectOne(
                        new QueryWrapper<Label>().eq("label_name", articleVO.getLabelName()));
                if (label == null) {
                    label = new Label();
                    label.setLabelName(articleVO.getLabelName());
                    label.setLabelDescription("通过导入创建的标签");
                    if (articleVO.getSortId() != null) {
                        label.setSortId(articleVO.getSortId());
                    } else {
                        Sort defaultSort = sortMapper.selectOne(
                                new QueryWrapper<Sort>().orderByAsc("id").last("LIMIT 1"));
                        label.setSortId(defaultSort != null ? defaultSort.getId() : 1);
                    }
                    labelMapper.insert(label);
                    log.info("导入文章时自动创建标签: {}", articleVO.getLabelName());
                }
                articleVO.setLabelId(label.getId());
            } finally {
                labelLock.unlock();
                labelNameLocks.computeIfPresent(labelKey, (k, v) -> v.isLocked() ? v : null);
            }
        }
    }
}
