package com.ld.poetry.controller;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.utils.MarkdownSectionEditor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.SeoConfig;
import com.ld.poetry.entity.SeoSearchEnginePush;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.SysPlugin;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.event.ArticleSavedEvent;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.LabelService;
import com.ld.poetry.service.ManagedResourceUploadService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.service.SitemapService;
import com.ld.poetry.service.SortService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.SysPluginService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.service.payment.PaymentService;
import com.ld.poetry.service.impl.ArticleServiceImpl.ArticleSaveStatus;
import com.ld.poetry.service.CommentService;
import com.ld.poetry.entity.Comment;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.vo.CommentVO;
import com.ld.poetry.utils.XssFilterUtil;
import com.ld.poetry.utils.IpUtil;
import com.ld.poetry.utils.ArticleUrlUtil;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.UserAgentClassifier;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;

/**
 * <p>
 * 外部API接口控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {

    private static final Set<String> ALLOWED_SEO_CONFIG_FIELDS = Set.of(
            "enable",
            "site_description",
            "site_keywords",
            "default_author",
            "og_image",
            "site_logo",
            "og_type",
            "twitter_card",
            "twitter_site",
            "twitter_creator",
            "baidu_push_enabled",
            "bing_push_enabled",
            "baidu_site_verification",
            "google_site_verification"
    );

    private final ArticleService articleService;

    private final LabelMapper labelMapper;

    private final SortMapper sortMapper;
    
    private final SortService sortService;
    
    private final LabelService labelService;

    private final SeoService seoService;

    private final TranslationService translationService;

    private final SummaryService summaryService;

    private final CacheService cacheService;

    private final WebInfoService webInfoService;

    private final PaymentService paymentService;

    private final SysPluginService sysPluginService;

    private final SeoConfigService seoConfigService;

    private final SitemapService sitemapService;

    private final HistoryInfoMapper historyInfoMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ManagedResourceUploadService managedResourceUploadService;

    private final FileSecurityValidator fileSecurityValidator;

    private final ApplicationEventPublisher eventPublisher;

    private final Executor asyncExecutor;
    
    private final CommentService commentService;

    public ApiController(ArticleService articleService,
                        LabelMapper labelMapper,
                        SortMapper sortMapper,
                        SortService sortService,
                        LabelService labelService,
                        SeoService seoService,
                        TranslationService translationService,
                        SummaryService summaryService,
                        CacheService cacheService,
                        WebInfoService webInfoService,
                        PaymentService paymentService,
                        SysPluginService sysPluginService,
                        SeoConfigService seoConfigService,
                        SitemapService sitemapService,
                        HistoryInfoMapper historyInfoMapper,
                        RedisTemplate<String, Object> redisTemplate,
                        ManagedResourceUploadService managedResourceUploadService,
                        FileSecurityValidator fileSecurityValidator,
                        ApplicationEventPublisher eventPublisher,
                        @Qualifier("asyncExecutor") Executor asyncExecutor,
                        CommentService commentService) {
        this.articleService = articleService;
        this.labelMapper = labelMapper;
        this.sortMapper = sortMapper;
        this.sortService = sortService;
        this.labelService = labelService;
        this.seoService = seoService;
        this.translationService = translationService;
        this.summaryService = summaryService;
        this.cacheService = cacheService;
        this.webInfoService = webInfoService;
        this.paymentService = paymentService;
        this.sysPluginService = sysPluginService;
        this.seoConfigService = seoConfigService;
        this.sitemapService = sitemapService;
        this.historyInfoMapper = historyInfoMapper;
        this.redisTemplate = redisTemplate;
        this.managedResourceUploadService = managedResourceUploadService;
        this.fileSecurityValidator = fileSecurityValidator;
        this.eventPublisher = eventPublisher;
        this.asyncExecutor = asyncExecutor;
        this.commentService = commentService;
    }

    /**
     * 验证API密钥
     */
    private WebInfo validateApiKey(HttpServletRequest request) {
        // 使用Redis缓存获取网站信息
        WebInfo webInfo = cacheService.getCachedWebInfo();

        // 如果Redis缓存为空，从数据库重新加载并缓存
        if (webInfo == null) {
            try {
                LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
                List<WebInfo> list = wrapper.list();
                if (!CollectionUtils.isEmpty(list)) {
                    webInfo = list.get(0);
                    // 重新缓存到Redis
                    cacheService.cacheWebInfo(webInfo);
                    log.info("API验证时从数据库重新加载网站信息并缓存");
                }
            } catch (Exception e) {
                log.error("API验证时从数据库加载网站信息失败", e);
            }
        }

        if (webInfo == null || webInfo.getApiEnabled() == null || !webInfo.getApiEnabled()) {
            log.warn("API请求被拒绝：API未启用");
            throw new PoetryRuntimeException("API未启用");
        }

        // 验证API密钥
        String apiKey = request.getHeader("X-API-KEY");
        if (!StringUtils.hasText(apiKey) || !apiKey.equals(webInfo.getApiKey())) {
            log.warn("API请求被拒绝：无效的API密钥");
            throw new PoetryRuntimeException("无效的API密钥");
        }

        String clientIp = PoetryUtil.getIpAddr(request);
        if (!IpUtil.isIpAllowedByWhitelist(clientIp, webInfo.getApiIpWhitelist())) {
            log.warn("API请求被拒绝：IP不在白名单中 - ip={}", clientIp);
            throw new PoetryRuntimeException("当前IP不在API白名单中");
        }

        return webInfo;
    }

    /**
     * API创建文章
     */
    @PostMapping("/article/create")
    public PoetryResult<Map<String, Object>> createArticle(@RequestBody ArticleVO articleVO, HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser == null) {
                log.error("API请求失败：无法获取管理员账号信息");
                return PoetryResult.fail("服务器内部错误：无法获取管理员账号");
            }

            normalizeArticlePayload(articleVO, adminUser);
            if (articleVO.getUserId() == null) {
                articleVO.setUserId(adminUser.getId());
            }

            PoetryResult<?> result = articleService.saveArticle(articleVO);
            if (result.getCode() == 200) {
                Integer articleId = extractArticleId(result.getData());
                if (articleId != null) {
                    return PoetryResult.success(buildArticleResponseData(articleId, articleVO, webInfo, request));
                }
            }

            return PoetryResult.fail(result.getMessage() != null ? result.getMessage() : "文章创建失败");
        } catch (IllegalArgumentException e) {
            log.error("API创建文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (PoetryRuntimeException e) {
            log.error("API创建文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API创建文章出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API异步创建文章
     */
    @PostMapping("/article/createAsync")
    public PoetryResult<Map<String, Object>> createArticleAsync(@RequestBody ArticleVO articleVO,
                                                                HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser == null) {
                log.error("API异步创建文章失败：无法获取管理员账号信息");
                return PoetryResult.fail("服务器内部错误：无法获取管理员账号");
            }

            normalizeArticlePayload(articleVO, adminUser);
            articleVO.setUserId(adminUser.getId());
            articleVO.setUpdateBy(adminUser.getUsername());

            PoetryResult<String> result = articleService.saveArticleAsync(
                    articleVO,
                    Boolean.TRUE.equals(articleVO.getSkipAiTranslation()),
                    buildPendingTranslation(articleVO));
            if (result.getCode() == 200 && StringUtils.hasText(result.getData())) {
                return PoetryResult.success(buildTaskCreatedResponseData(result.getData(), webInfo, request));
            }

            return PoetryResult.fail(result.getMessage() != null ? result.getMessage() : "异步创建文章失败");
        } catch (IllegalArgumentException e) {
            log.error("API异步创建文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (PoetryRuntimeException e) {
            log.error("API异步创建文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API异步创建文章出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API更新文章
     */
    @PostMapping("/article/update")
    public PoetryResult<Map<String, Object>> updateArticle(@RequestBody ArticleVO articleVO, HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            if (articleVO == null || articleVO.getId() == null || articleVO.getId() <= 0) {
                return PoetryResult.fail("文章ID不能为空");
            }

            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser == null) {
                return PoetryResult.fail("服务器内部错误：无法获取管理员账号");
            }

            Article article = articleService.getById(articleVO.getId());
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            applyExistingArticleDefaults(articleVO, article);
            normalizeArticlePayload(articleVO, adminUser);

            articleVO.setUserId(article.getUserId());
            articleVO.setUpdateBy(adminUser.getUsername());
            PoetryResult<?> result = articleService.updateArticle(
                    articleVO,
                    Boolean.TRUE.equals(articleVO.getSkipAiTranslation()),
                    buildPendingTranslation(articleVO),
                    article.getUserId());
            if (result.getCode() != 200) {
                return PoetryResult.fail(result.getMessage() != null ? result.getMessage() : "文章更新失败");
            }

            return PoetryResult.success(buildArticleResponseData(article.getId(), articleVO, webInfo, request));
        } catch (IllegalArgumentException e) {
            log.error("API更新文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (PoetryRuntimeException e) {
            log.error("API更新文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API更新文章出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API异步更新文章
     */
    @PostMapping("/article/updateAsync")
    public PoetryResult<Map<String, Object>> updateArticleAsync(@RequestBody ArticleVO articleVO,
                                                                HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            if (articleVO == null || articleVO.getId() == null || articleVO.getId() <= 0) {
                return PoetryResult.fail("文章ID不能为空");
            }

            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser == null) {
                return PoetryResult.fail("服务器内部错误：无法获取管理员账号");
            }

            Article article = articleService.getById(articleVO.getId());
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            applyExistingArticleDefaults(articleVO, article);
            normalizeArticlePayload(articleVO, adminUser);
            articleVO.setUserId(article.getUserId());
            articleVO.setUpdateBy(adminUser.getUsername());

            PoetryResult<String> result = articleService.updateArticleAsync(
                    articleVO,
                    Boolean.TRUE.equals(articleVO.getSkipAiTranslation()),
                    buildPendingTranslation(articleVO),
                    article.getUserId(),
                    adminUser.getUsername(),
                    null,
                    null);
            if (result.getCode() == 200 && StringUtils.hasText(result.getData())) {
                return PoetryResult.success(buildTaskCreatedResponseData(result.getData(), webInfo, request));
            }

            return PoetryResult.fail(result.getMessage() != null ? result.getMessage() : "异步更新文章失败");
        } catch (IllegalArgumentException e) {
            log.error("API异步更新文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (PoetryRuntimeException e) {
            log.error("API异步更新文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API异步更新文章出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API查询异步任务状态
     */
    @GetMapping("/article/task/{taskId}")
    public PoetryResult<Map<String, Object>> getArticleTaskStatus(@PathVariable("taskId") String taskId,
                                                                  HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            if (!StringUtils.hasText(taskId)) {
                return PoetryResult.fail("任务ID不能为空");
            }

            PoetryResult<ArticleSaveStatus> result = articleService.getArticleSaveStatus(taskId);
            if (result.getCode() != 200 || result.getData() == null) {
                return PoetryResult.fail(result.getMessage() != null ? result.getMessage() : "任务不存在或已过期");
            }

            return PoetryResult.success(buildTaskStatusResponseData(result.getData(), webInfo, request));
        } catch (PoetryRuntimeException e) {
            log.error("API查询文章任务状态失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询文章任务状态出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取文章主题状态
     */
    @GetMapping("/article-theme/status")
    public PoetryResult<Map<String, Object>> getArticleThemeStatus(HttpServletRequest request) {
        try {
            validateApiKey(request);

            List<SysPlugin> plugins = sysPluginService.getPluginsByType(SysPlugin.TYPE_ARTICLE_THEME);
            List<Map<String, Object>> items = new ArrayList<>();
            SysPlugin activePlugin = sysPluginService.getActivePlugin(SysPlugin.TYPE_ARTICLE_THEME);

            for (SysPlugin plugin : plugins) {
                if (!Boolean.TRUE.equals(plugin.getEnabled())) {
                    continue;
                }
                items.add(buildThemePluginItem(plugin, activePlugin));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activePluginKey", activePlugin != null ? activePlugin.getPluginKey() : null);
            data.put("plugins", items);
            data.put("activeTheme", activePlugin != null ? buildThemePluginItem(activePlugin, activePlugin) : null);
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API获取文章主题状态失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取文章主题状态出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API激活文章主题
     */
    @PostMapping("/article-theme/activate")
    public PoetryResult<Map<String, Object>> activateArticleTheme(@RequestBody Map<String, Object> payload,
                                                                  HttpServletRequest request) {
        try {
            validateApiKey(request);

            String pluginKey = payload == null ? null : valueAsString(payload.get("pluginKey"));
            if (!StringUtils.hasText(pluginKey)) {
                return PoetryResult.fail("pluginKey 不能为空");
            }

            SysPlugin targetPlugin = sysPluginService.getPluginByTypeAndKey(SysPlugin.TYPE_ARTICLE_THEME, pluginKey);
            if (targetPlugin == null) {
                return PoetryResult.fail("文章主题不存在");
            }
            if (!Boolean.TRUE.equals(targetPlugin.getEnabled())) {
                return PoetryResult.fail("该文章主题未启用，请先在插件管理启用");
            }

            boolean activated = sysPluginService.setActivePlugin(SysPlugin.TYPE_ARTICLE_THEME, pluginKey);
            if (!activated) {
                return PoetryResult.fail("文章主题切换失败");
            }

            SysPlugin activePlugin = sysPluginService.getActivePlugin(SysPlugin.TYPE_ARTICLE_THEME);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activePluginKey", activePlugin != null ? activePlugin.getPluginKey() : null);
            data.put("activeTheme", activePlugin != null ? buildThemePluginItem(activePlugin, activePlugin) : null);
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API激活文章主题失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API激活文章主题出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取文章级复盘数据
     */
    @GetMapping("/article/analytics/{id:\\d+}")
    public PoetryResult<Map<String, Object>> getArticleAnalytics(@PathVariable("id") Integer id,
                                                                 HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            if (id == null || id <= 0) {
                return PoetryResult.fail("无效的文章ID");
            }

            Article article = articleService.getById(id);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("articleId", article.getId());
            data.put("articleTitle", article.getArticleTitle());
            data.put("articleSlug", article.getArticleSlug());
            data.put("articleUrl", buildArticleUrl(webInfo, request, article.getId()));
            data.put("viewCount", article.getViewCount());
            data.put("commentStatus", article.getCommentStatus());
            data.put("recommendStatus", article.getRecommendStatus());
            data.put("viewStatus", article.getViewStatus());
            data.put("submitToSearchEngine", article.getSubmitToSearchEngine());
            data.put("createTime", article.getCreateTime());
            data.put("updateTime", article.getUpdateTime());
            data.put("sortId", article.getSortId());
            data.put("labelId", article.getLabelId());

            if (article.getSortId() != null) {
                Sort sort = sortService.getById(article.getSortId());
                if (sort != null) {
                    data.put("sortName", sort.getSortName());
                }
            }

            if (article.getLabelId() != null) {
                Label label = labelService.getById(article.getLabelId());
                if (label != null) {
                    data.put("labelName", label.getLabelName());
                }
            }

            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API获取文章复盘数据失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取文章复盘数据出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取站点访问趋势（含人机分离、UA 构成、来源交叉统计）
     * <p>
     * 除每日趋势外，额外返回 UA 分类汇总、Top UA、来源站点×人机交叉等信息，
     * 帮助 Agent 判断流量增长是真实访客还是爬虫噪声（爬虫无 Referer 会被归入 Direct）。
     */
    @GetMapping("/analytics/site/visits")
    public PoetryResult<Map<String, Object>> getSiteVisitAnalytics(
            @RequestParam(value = "days", defaultValue = "7") Integer days,
            HttpServletRequest request) {
        try {
            validateApiKey(request);

            if (days == null || (days != 7 && days != 30)) {
                return PoetryResult.fail("days 仅支持 7 或 30");
            }

            List<String> ignoredIps = cacheService.getVisitIgnoreIpList();

            List<Map<String, Object>> dbStats = historyInfoMapper.getDailyVisitStatsExcludeToday(days, ignoredIps);
            if (dbStats == null) {
                dbStats = new ArrayList<>();
            }

            // 历史每日人机分离数据，按日期合并进趋势行
            Map<String, Map<String, Object>> uaDailyMap = new HashMap<>();
            List<Map<String, Object>> uaDaily = historyInfoMapper.getDailyVisitUaStatsExcludeToday(days, ignoredIps);
            if (uaDaily != null) {
                for (Map<String, Object> row : uaDaily) {
                    String date = valueAsString(row.get("visit_date"));
                    if (StringUtils.hasText(date)) {
                        uaDailyMap.put(date, row);
                    }
                }
            }
            for (Map<String, Object> stat : dbStats) {
                Map<String, Object> uaRow = uaDailyMap.get(valueAsString(stat.get("visit_date")));
                stat.put("human_visits", uaRow != null ? asLongValue(uaRow.get("human_visits")) : 0L);
                stat.put("human_unique_visits", uaRow != null ? asLongValue(uaRow.get("human_unique_visits")) : 0L);
                stat.put("js_verified_visits", uaRow != null ? asLongValue(uaRow.get("js_verified_visits")) : 0L);
                stat.put("js_verified_unique_visits", uaRow != null ? asLongValue(uaRow.get("js_verified_unique_visits")) : 0L);
                stat.put("browser_no_js_visits", uaRow != null ? asLongValue(uaRow.get("browser_no_js_visits")) : 0L);
                stat.put("bot_visits", uaRow != null ? asLongValue(uaRow.get("bot_visits")) : 0L);
                stat.put("unclassified_visits", uaRow != null ? asLongValue(uaRow.get("unclassified_visits")) : 0L);
            }

            // 今日实时记录（已过滤忽略 IP），同时用于趋势行、UA 汇总与来源交叉
            List<Map<String, Object>> todayRecords = cacheService.getDailyVisitRecords(java.time.LocalDate.now().toString());
            Map<String, Object> todayStats = buildTodayVisitStats(todayRecords);

            List<Map<String, Object>> allStats = new ArrayList<>(dbStats);
            if (todayStats != null) {
                allStats.add(todayStats);
            }

            List<Map<String, Object>> completeStats = fillMissingDates(allStats, days);
            applyVisitAverages(completeStats);

            // UA 类型汇总（历史 + 今日）
            List<Map<String, Object>> uaTypeBreakdown = buildUaTypeBreakdown(
                    historyInfoMapper.getUaTypeSummaryExcludeToday(days, ignoredIps), todayRecords);

            // Top UA（复用后台的 UA 聚合逻辑，含 bot 验证状态与样本 UA）
            List<Map<String, Object>> topUas = UserAgentClassifier.aggregateRawAndVisitRecords(
                    historyInfoMapper.getHistoryByUserAgentForDays(days, ignoredIps), todayRecords, 15);

            // 来源站点 × 人机交叉（历史 + 今日）
            String siteHost = resolveSiteHostFromRequest(request);
            List<Map<String, Object>> referrerBreakdown = buildReferrerBreakdown(
                    historyInfoMapper.getReferrerUaSplitExcludeToday(days, siteHost, ignoredIps),
                    todayRecords, siteHost);

            // 地区分布（历史 + 今日，海外按国家、中国按省份）
            List<Map<String, Object>> regionBreakdown = buildRegionBreakdown(
                    historyInfoMapper.getProvinceStatsByDays(days, ignoredIps), todayRecords);

            // 周期汇总与解读提示
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("daily", completeStats);
            data.put("summary", buildVisitSummary(days, completeStats, uaTypeBreakdown));
            data.put("ua_type_breakdown", uaTypeBreakdown);
            data.put("top_uas", topUas);
            data.put("referrer_breakdown", referrerBreakdown);
            data.put("region_breakdown", regionBreakdown);
            data.put("agent_guide", buildVisitAgentGuide());
            log.info("API站点访问趋势已生成: days={}, uaTypes={}, topUas={}, referrers={}, regions={}",
                    days, uaTypeBreakdown.size(), topUas.size(), referrerBreakdown.size(), regionBreakdown.size());
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API获取站点访问趋势失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取站点访问趋势出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取SEO状态
     */
    @GetMapping("/seo/status")
    public PoetryResult<Map<String, Object>> getSeoStatus(HttpServletRequest request) {
        try {
            validateApiKey(request);

            Map<String, Object> config = seoConfigService.getSeoConfigAsJson();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("enabled", Boolean.TRUE.equals(config.get("enable")));
            data.put("searchEnginePushEnabled", buildSearchEnginePushOverview(config));
            data.put("siteVerificationConfigured", buildSiteVerificationOverview(config));
            data.put("sitemapAvailable", true);
            data.put("lastSitemapUpdateTime", getLastSitemapUpdateTime());
            data.put("searchEnginePingEnabled", sitemapService.isSearchEnginePingEnabled());
            data.put("sitemapBaseUrl", sitemapService.getSiteBaseUrl());
            data.put("summary", buildSeoHealthSummary(config));
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API获取SEO状态失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取SEO状态出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取受控SEO配置
     */
    @GetMapping("/seo/config")
    public PoetryResult<Map<String, Object>> getSeoConfig(HttpServletRequest request) {
        try {
            validateApiKey(request);
            return PoetryResult.success(filterSeoConfig(seoConfigService.getSeoConfigAsJson()));
        } catch (PoetryRuntimeException e) {
            log.error("API获取SEO配置失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取SEO配置出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API更新受控SEO配置
     */
    @PostMapping("/seo/config")
    public PoetryResult<Map<String, Object>> updateSeoConfig(@RequestBody Map<String, Object> payload,
                                                             HttpServletRequest request) {
        try {
            validateApiKey(request);

            Map<String, Object> incoming = toObjectMap(payload);
            List<String> unsupportedFields = new ArrayList<>();
            for (String key : incoming.keySet()) {
                if (!ALLOWED_SEO_CONFIG_FIELDS.contains(key)) {
                    unsupportedFields.add(key);
                }
            }
            if (!unsupportedFields.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("unsupportedFields", unsupportedFields);
                return PoetryResult.fail(400, "包含不允许通过 API-key 修改的 SEO 字段", data);
            }

            Map<String, Object> mergedConfig = new LinkedHashMap<>(seoConfigService.getSeoConfigAsJson());
            mergedConfig.putAll(incoming);
            boolean success = seoConfigService.updateSeoConfigFromJson(mergedConfig);
            if (!success) {
                return PoetryResult.fail("SEO配置更新失败");
            }

            try {
                sitemapService.updateSitemapAndPush("API-key SEO配置更新");
            } catch (Exception e) {
                log.warn("API-key SEO配置更新后触发sitemap失败: {}", e.getMessage());
            }

            return PoetryResult.success(filterSeoConfig(seoConfigService.getSeoConfigAsJson()));
        } catch (PoetryRuntimeException e) {
            log.error("API更新SEO配置失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API更新SEO配置出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API触发sitemap更新
     */
    @PostMapping("/seo/sitemap/update")
    public PoetryResult<Map<String, Object>> updateSeoSitemap(HttpServletRequest request) {
        try {
            validateApiKey(request);
            sitemapService.updateSitemapAndPush("API-key 手动触发 sitemap 更新");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("triggered", true);
            data.put("lastSitemapUpdateTime", getLastSitemapUpdateTime());
            data.put("searchEnginePingEnabled", sitemapService.isSearchEnginePingEnabled());
            data.put("siteBaseUrl", sitemapService.getSiteBaseUrl());
            data.put("message", "sitemap 更新已触发");
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API触发sitemap更新失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API触发sitemap更新出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取支付插件状态
     */
    @GetMapping("/payment/plugin/status")
    public PoetryResult<Map<String, Object>> getPaymentPluginStatus(@RequestParam(value = "pluginKey", required = false) String pluginKey,
                                                                    HttpServletRequest request) {
        try {
            validateApiKey(request);

            List<SysPlugin> paymentPlugins = paymentService.listPaymentPlugins();
            if (CollectionUtils.isEmpty(paymentPlugins)) {
                return PoetryResult.fail("请先在插件管理安装至少一个文章付费插件");
            }

            SysPlugin activePlugin = paymentService.getActivePaymentPlugin();
            SysPlugin targetPlugin = resolveTargetPaymentPlugin(pluginKey, activePlugin, paymentPlugins);
            if (StringUtils.hasText(pluginKey) && targetPlugin == null) {
                return PoetryResult.fail("支付插件不存在");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activePluginKey", activePlugin != null ? activePlugin.getPluginKey() : null);
            data.put("plugins", buildPaymentPluginItems(paymentPlugins, activePlugin));
            if (targetPlugin != null) {
                data.put("targetPlugin", buildPaymentPluginDetail(targetPlugin, activePlugin));
            }
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API获取支付插件状态失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取支付插件状态出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API配置支付插件
     */
    @PostMapping("/payment/plugin/configure")
    public PoetryResult<Map<String, Object>> configurePaymentPlugin(@RequestBody Map<String, Object> payload,
                                                                    HttpServletRequest request) {
        try {
            validateApiKey(request);

            String pluginKey = payload == null ? null : valueAsString(payload.get("pluginKey"));
            if (!StringUtils.hasText(pluginKey)) {
                return PoetryResult.fail("pluginKey 不能为空");
            }

            SysPlugin plugin = paymentService.getPaymentPluginByKey(pluginKey);
            if (plugin == null) {
                return PoetryResult.fail("支付插件不存在");
            }
            if (!Boolean.TRUE.equals(plugin.getEnabled())) {
                return PoetryResult.fail("该支付插件未启用，请先在插件管理启用");
            }

            Map<String, Object> incomingConfig = toObjectMap(payload.get("pluginConfig"));
            boolean activate = payload == null || payload.get("activate") == null || Boolean.TRUE.equals(payload.get("activate"));
            Map<String, Object> existingConfig = paymentService.parsePluginConfig(plugin);
            Map<String, Object> evaluation = paymentService.mergeAndValidateConfig(pluginKey, existingConfig, incomingConfig);

            List<String> validationErrors = toStringList(evaluation.get("validationErrors"));
            if (!CollectionUtils.isEmpty(validationErrors)) {
                return PoetryResult.fail(400, String.join("；", validationErrors),
                        buildPaymentConfigureResponse(plugin, evaluation, false, "配置字段校验失败"));
            }

            List<String> missingFields = toStringList(evaluation.get("missingFields"));
            if (!CollectionUtils.isEmpty(missingFields)) {
                return PoetryResult.fail(400, "支付插件配置不完整",
                        buildPaymentConfigureResponse(plugin, evaluation, false, "缺少必要配置字段"));
            }

            Map<String, Object> mergedConfig = toObjectMap(evaluation.get("mergedConfig"));
            boolean connectionOk = paymentService.testConnection(pluginKey, mergedConfig);
            if (!connectionOk) {
                return PoetryResult.fail(400, "支付插件连接测试失败",
                        buildPaymentConfigureResponse(plugin, evaluation, false, "连接测试失败，请检查配置"));
            }

            boolean saved = paymentService.savePluginConfig(pluginKey, mergedConfig, activate);
            if (!saved) {
                return PoetryResult.fail("支付插件配置保存失败");
            }

            SysPlugin refreshedPlugin = paymentService.getPaymentPluginByKey(pluginKey);
            return PoetryResult.success(buildPaymentConfigureResponse(refreshedPlugin != null ? refreshedPlugin : plugin,
                    evaluation, true, "配置已保存并通过连接测试"));
        } catch (PoetryRuntimeException e) {
            log.error("API配置支付插件失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API配置支付插件出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API测试支付插件连接
     */
    @PostMapping("/payment/plugin/testConnection")
    public PoetryResult<Map<String, Object>> testPaymentPluginConnection(@RequestBody Map<String, Object> payload,
                                                                         HttpServletRequest request) {
        try {
            validateApiKey(request);

            String pluginKey = payload == null ? null : valueAsString(payload.get("pluginKey"));
            if (!StringUtils.hasText(pluginKey)) {
                return PoetryResult.fail("pluginKey 不能为空");
            }

            SysPlugin plugin = paymentService.getPaymentPluginByKey(pluginKey);
            if (plugin == null) {
                return PoetryResult.fail("支付插件不存在");
            }

            Map<String, Object> existingConfig = paymentService.parsePluginConfig(plugin);
            Map<String, Object> incomingConfig = toObjectMap(payload.get("pluginConfig"));
            Map<String, Object> evaluation = paymentService.mergeAndValidateConfig(pluginKey, existingConfig, incomingConfig);

            List<String> validationErrors = toStringList(evaluation.get("validationErrors"));
            if (!CollectionUtils.isEmpty(validationErrors)) {
                return PoetryResult.fail(400, String.join("；", validationErrors),
                        buildPaymentConnectionTestResponse(plugin, evaluation, false, "配置字段校验失败"));
            }

            List<String> missingFields = toStringList(evaluation.get("missingFields"));
            if (!CollectionUtils.isEmpty(missingFields)) {
                return PoetryResult.fail(400, "支付插件配置不完整",
                        buildPaymentConnectionTestResponse(plugin, evaluation, false, "缺少必要配置字段"));
            }

            Map<String, Object> mergedConfig = toObjectMap(evaluation.get("mergedConfig"));
            boolean connectionOk = paymentService.testConnection(pluginKey, mergedConfig);
            String message = connectionOk ? "连接测试成功" : "连接测试失败，请检查配置";
            Map<String, Object> responseData = buildPaymentConnectionTestResponse(plugin, evaluation, connectionOk, message);
            if (!connectionOk) {
                return PoetryResult.fail(400, message, responseData);
            }
            return PoetryResult.success(responseData);
        } catch (PoetryRuntimeException e) {
            log.error("API测试支付插件连接失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API测试支付插件连接出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API上传资源
     */
    @PostMapping("/resource/upload")
    public PoetryResult<Map<String, Object>> uploadResource(@RequestParam("file") MultipartFile file,
                                                            @RequestParam(value = "type", required = false) String type,
                                                            @RequestParam(value = "relativePath", required = false) String relativePath,
                                                            @RequestParam(value = "storeType", required = false) String storeType,
                                                            HttpServletRequest request) {
        try {
            validateApiKey(request);

            if (file == null || file.isEmpty()) {
                return PoetryResult.fail("请选择要上传的文件");
            }

            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser == null) {
                return PoetryResult.fail("服务器内部错误：无法获取管理员账号");
            }

            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());
            if (!validationResult.isSuccess()) {
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            FileVO fileVO = new FileVO();
            fileVO.setFile(file);
            fileVO.setType(StringUtils.hasText(type) ? type : "articleCover");
            fileVO.setStoreType(storeType);
            fileVO.setOriginalName(file.getOriginalFilename());
            fileVO.setRelativePath(StringUtils.hasText(relativePath)
                    ? relativePath
                    : buildApiUploadPath(fileVO.getType(), file.getOriginalFilename()));

            ManagedResourceUploadService.ManagedUploadResult saved =
                    managedResourceUploadService.uploadOrReuse(fileVO, adminUser.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("url", saved.stablePath());
            data.put("path", saved.stablePath());
            data.put("type", saved.resource().getType());
            data.put("originalName", saved.resource().getOriginalName());
            data.put("size", saved.resource().getSize());
            data.put("mimeType", saved.resource().getMimeType());
            data.put("reused", saved.reused());
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API上传资源失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API上传资源出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API查询文章列表
     */
    @GetMapping("/article/list")
    public PoetryResult getArticleList(BaseRequestVO baseRequestVO, HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 设置默认分页参数
            if (baseRequestVO.getCurrent() < 1) {
                baseRequestVO.setCurrent(1);
            }
            if (baseRequestVO.getSize() < 1) {
                baseRequestVO.setSize(10);
            }
            
            // 调用文章列表服务
            // 管理语义：API Key + IP 白名单认证的入口默认返回全部文章（含隐藏），
            // 每条记录自带 viewStatus 供调用方区分；公开接口不受影响
            return articleService.listArticle(baseRequestVO, true);
        } catch (PoetryRuntimeException e) {
            log.error("API查询文章列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询文章列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API获取文章详情
     */
    @GetMapping("/article/{id:\\d+}")
    public PoetryResult getArticleDetail(@PathVariable("id") Integer id, HttpServletRequest request) {
        try {
            WebInfo webInfo = validateApiKey(request);
            
            // 验证参数
            if (id == null || id <= 0) {
                return PoetryResult.fail("无效的文章ID");
            }
            
            // 查询文章
            Article article = articleService.getById(id);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 将文章转为VO
            ArticleVO articleVO = new ArticleVO();
            articleVO.setId(article.getId());
            articleVO.setUserId(article.getUserId());
            articleVO.setArticleTitle(article.getArticleTitle());
            articleVO.setArticleSlug(article.getArticleSlug());
            articleVO.setArticleContent(article.getArticleContent());
            articleVO.setArticleCover(article.getArticleCover());
            articleVO.setSortId(article.getSortId());
            articleVO.setLabelId(article.getLabelId());
            articleVO.setViewCount(article.getViewCount());
            articleVO.setCommentStatus(article.getCommentStatus());
            articleVO.setRecommendStatus(article.getRecommendStatus());
            articleVO.setViewStatus(article.getViewStatus());
            articleVO.setSubmitToSearchEngine(article.getSubmitToSearchEngine());
            articleVO.setCreateTime(article.getCreateTime());
            articleVO.setUpdateTime(article.getUpdateTime());
            articleVO.setVideoUrl(article.getVideoUrl());
            articleVO.setTips(article.getTips());
            articleVO.setPassword(article.getPassword());
            articleVO.setArticleUrl(buildArticleUrl(webInfo, request, article.getId()));
            
            // 获取分类和标签信息
            if (article.getSortId() != null) {
                Sort sort = sortService.getById(article.getSortId());
                if (sort != null) {
                    articleVO.setSort(sort);
                    articleVO.setSortName(sort.getSortName());
                }
            }
            
            if (article.getLabelId() != null) {
                Label label = labelService.getById(article.getLabelId());
                if (label != null) {
                    articleVO.setLabel(label);
                    articleVO.setLabelName(label.getLabelName());
                }
            }
            
            return PoetryResult.success(articleVO);
        } catch (PoetryRuntimeException e) {
            log.error("API获取文章详情失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取文章详情出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API按数字ID或URL别名获取文章详情
     */
    @GetMapping("/article/path/{path:[A-Za-z0-9-]+}")
    public PoetryResult getArticleDetailByPath(@PathVariable("path") String path, HttpServletRequest request) {
        try {
            if (!StringUtils.hasText(path)) {
                return PoetryResult.fail("文章路径不能为空");
            }
            Integer articleId = articleService.resolveArticleIdByPath(path);
            if (articleId == null) {
                return PoetryResult.fail("文章不存在");
            }
            return getArticleDetail(articleId, request);
        } catch (PoetryRuntimeException e) {
            log.error("API按路径获取文章详情失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API按路径获取文章详情出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    private void normalizeArticlePayload(ArticleVO articleVO, User adminUser) {
        if (articleVO == null) {
            throw new PoetryRuntimeException("文章内容不能为空");
        }

        if (!StringUtils.hasText(articleVO.getArticleTitle()) && StringUtils.hasText(articleVO.getTitle())) {
            articleVO.setArticleTitle(articleVO.getTitle());
        }
        if (!StringUtils.hasText(articleVO.getArticleContent()) && StringUtils.hasText(articleVO.getContent())) {
            articleVO.setArticleContent(articleVO.getContent());
        }
        if (articleVO.getArticleSlug() == null && articleVO.getSlug() != null) {
            articleVO.setArticleSlug(articleVO.getSlug());
        }
        if (articleVO.getSortId() == null && articleVO.getClassify() != null) {
            articleVO.setSortId(articleVO.getClassify());
        }
        if (articleVO.getArticleCover() == null && articleVO.getCover() != null) {
            articleVO.setArticleCover(articleVO.getCover());
        }
        if (!StringUtils.hasText(articleVO.getTips()) && StringUtils.hasText(articleVO.getSummary())) {
            articleVO.setTips(articleVO.getSummary());
        }

        if (articleVO.getUserId() == null && adminUser != null) {
            articleVO.setUserId(adminUser.getId());
        }
        if (articleVO.getViewStatus() == null) {
            articleVO.setViewStatus(true);
        }
        if (articleVO.getCommentStatus() == null) {
            articleVO.setCommentStatus(true);
        }
        if (articleVO.getRecommendStatus() == null) {
            articleVO.setRecommendStatus(false);
        }
        if (articleVO.getSubmitToSearchEngine() == null) {
            articleVO.setSubmitToSearchEngine(Boolean.TRUE.equals(articleVO.getViewStatus()));
        }

        validatePaymentSettings(articleVO);
        resolveSortAndLabel(articleVO);

        if (!StringUtils.hasText(articleVO.getArticleTitle())) {
            throw new PoetryRuntimeException("文章标题不能为空");
        }
        if (!StringUtils.hasText(articleVO.getArticleContent())) {
            throw new PoetryRuntimeException("文章内容不能为空");
        }
        MarkdownSectionEditor.validateArticleBody(articleVO.getArticleContent());
        if (StringUtils.hasText(articleVO.getPendingTranslationContent())) {
            MarkdownSectionEditor.validateArticleBody(articleVO.getPendingTranslationContent());
        }
        if (articleVO.getSortId() == null) {
            throw new PoetryRuntimeException("分类不能为空");
        }
        if (articleVO.getLabelId() == null) {
            throw new PoetryRuntimeException("标签不能为空");
        }
        if (Boolean.FALSE.equals(articleVO.getViewStatus())
                && (!StringUtils.hasText(articleVO.getPassword()) || !StringUtils.hasText(articleVO.getTips()))) {
            throw new PoetryRuntimeException("私密文章必须提供 password 和 tips");
        }
    }

    private void validatePaymentSettings(ArticleVO articleVO) {
        if (articleVO == null || articleVO.getPayType() == null || articleVO.getPayType() == 0) {
            return;
        }

        Integer payType = articleVO.getPayType();
        if (payType < 0 || payType > 4) {
            throw new PoetryRuntimeException("无效的付费类型");
        }

        if (articleVO.getFreePercent() != null
                && (articleVO.getFreePercent() < 0 || articleVO.getFreePercent() > 100)) {
            throw new PoetryRuntimeException("免费预览比例必须在 0 到 100 之间");
        }

        if (payType == 4 && (articleVO.getPayAmount() == null || articleVO.getPayAmount().signum() <= 0)) {
            throw new PoetryRuntimeException("固定金额解锁必须提供大于 0 的 payAmount");
        }

        SysPlugin activePlugin = paymentService.getActivePaymentPlugin();
        if (activePlugin == null || !Boolean.TRUE.equals(activePlugin.getEnabled()) || paymentService.getActiveProvider() == null) {
            throw new PoetryRuntimeException("请先在插件管理 -> 文章付费中启用付费插件");
        }

        Map<String, Object> paymentCheck = paymentService.mergeAndValidateConfig(
                activePlugin.getPluginKey(),
                paymentService.parsePluginConfig(activePlugin),
                new LinkedHashMap<>()
        );
        List<String> missingFields = toStringList(paymentCheck.get("missingFields"));
        if (!Boolean.TRUE.equals(paymentCheck.get("configured")) || !CollectionUtils.isEmpty(missingFields)) {
            throw new PoetryRuntimeException("请先在插件管理 -> 文章付费中配置付费插件");
        }
    }

    private void applyExistingArticleDefaults(ArticleVO articleVO, Article article) {
        if (articleVO == null || article == null) {
            return;
        }

        if (!StringUtils.hasText(articleVO.getArticleTitle()) && !StringUtils.hasText(articleVO.getTitle())) {
            articleVO.setArticleTitle(article.getArticleTitle());
        }
        if (!StringUtils.hasText(articleVO.getArticleContent()) && !StringUtils.hasText(articleVO.getContent())) {
            articleVO.setArticleContent(article.getArticleContent());
        }
        if (articleVO.getSortId() == null && !StringUtils.hasText(articleVO.getSortName())) {
            articleVO.setSortId(article.getSortId());
        }
        if (articleVO.getLabelId() == null && !StringUtils.hasText(articleVO.getLabelName())) {
            articleVO.setLabelId(article.getLabelId());
        }
        if (articleVO.getArticleCover() == null && articleVO.getCover() == null) {
            articleVO.setArticleCover(article.getArticleCover());
        }
        if (articleVO.getArticleSlug() == null && articleVO.getSlug() == null) {
            articleVO.setArticleSlug(article.getArticleSlug());
        }
        if (articleVO.getVideoUrl() == null) {
            articleVO.setVideoUrl(article.getVideoUrl());
        }
        if (articleVO.getCommentStatus() == null) {
            articleVO.setCommentStatus(article.getCommentStatus());
        }
        if (articleVO.getRecommendStatus() == null) {
            articleVO.setRecommendStatus(article.getRecommendStatus());
        }
        if (articleVO.getViewStatus() == null) {
            articleVO.setViewStatus(article.getViewStatus());
        }
        if (articleVO.getSubmitToSearchEngine() == null) {
            articleVO.setSubmitToSearchEngine(article.getSubmitToSearchEngine());
        }
        if (!StringUtils.hasText(articleVO.getPassword())) {
            articleVO.setPassword(article.getPassword());
        }
        if (!StringUtils.hasText(articleVO.getTips())) {
            articleVO.setTips(article.getTips());
        }
        if (articleVO.getPayType() == null) {
            articleVO.setPayType(article.getPayType());
        }
        if (articleVO.getPayAmount() == null) {
            articleVO.setPayAmount(article.getPayAmount());
        }
        if (articleVO.getFreePercent() == null) {
            articleVO.setFreePercent(article.getFreePercent());
        }
        if (articleVO.getUserId() == null) {
            articleVO.setUserId(article.getUserId());
        }
    }

    private void resolveSortAndLabel(ArticleVO articleVO) {
        if (StringUtils.hasText(articleVO.getSortName())) {
            Sort sort = sortMapper.selectOne(new QueryWrapper<Sort>().eq("sort_name", articleVO.getSortName()));
            if (sort == null) {
                sort = new Sort();
                sort.setSortName(articleVO.getSortName());
                sort.setSortDescription(StringUtils.hasText(articleVO.getSortDescription())
                    ? articleVO.getSortDescription() : articleVO.getSortName());
                sort.setSortType(1);
                sort.setPriority(99);
                sortMapper.insert(sort);
                cacheService.evictSortArticleList();
            }
            articleVO.setSortId(sort.getId());
        }

        Object labelObj = articleVO.getLabel();
        if (articleVO.getLabelId() == null && labelObj instanceof Number) {
            articleVO.setLabelId(((Number) labelObj).intValue());
        }

        if (StringUtils.hasText(articleVO.getLabelName())) {
            QueryWrapper<Label> labelQuery = new QueryWrapper<Label>().eq("label_name", articleVO.getLabelName());
            if (articleVO.getSortId() != null) {
                labelQuery.eq("sort_id", articleVO.getSortId());
            }

            Label label = labelMapper.selectOne(labelQuery);
            if (label == null) {
                label = new Label();
                label.setLabelName(articleVO.getLabelName());
                label.setLabelDescription(StringUtils.hasText(articleVO.getLabelDescription())
                    ? articleVO.getLabelDescription() : articleVO.getLabelName());
                label.setSortId(resolveLabelSortId(articleVO.getSortId()));
                labelMapper.insert(label);
                cacheService.evictSortArticleList();
            }
            articleVO.setLabelId(label.getId());
        }
    }

    private Integer resolveLabelSortId(Integer sortId) {
        if (sortId != null) {
            return sortId;
        }
        Sort defaultSort = sortMapper.selectOne(new QueryWrapper<Sort>().orderByAsc("id").last("LIMIT 1"));
        return defaultSort != null ? defaultSort.getId() : 1;
    }

    private Integer extractArticleId(Object data) {
        if (data instanceof Integer) {
            return (Integer) data;
        }
        if (data instanceof Number) {
            return ((Number) data).intValue();
        }
        if (data instanceof Map<?, ?> map) {
            Object idObj = map.get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).intValue();
            }
        }
        return null;
    }

    private Map<String, String> buildPendingTranslation(ArticleVO articleVO) {
        if (!StringUtils.hasText(articleVO.getPendingTranslationTitle())
                || !StringUtils.hasText(articleVO.getPendingTranslationContent())
                || !StringUtils.hasText(articleVO.getPendingTranslationLanguage())) {
            return null;
        }

        Map<String, String> pendingTranslation = new HashMap<>();
        pendingTranslation.put("title", articleVO.getPendingTranslationTitle());
        pendingTranslation.put("content", articleVO.getPendingTranslationContent());
        pendingTranslation.put("language", articleVO.getPendingTranslationLanguage());
        return pendingTranslation;
    }

    private void mergeArticleUpdate(Article article, ArticleVO articleVO, User adminUser) {
        if (StringUtils.hasText(articleVO.getArticleTitle())) {
            article.setArticleTitle(articleVO.getArticleTitle());
        }
        if (StringUtils.hasText(articleVO.getArticleContent())) {
            article.setArticleContent(articleVO.getArticleContent());
        }
        if (articleVO.getSortId() != null) {
            article.setSortId(articleVO.getSortId());
        }
        if (articleVO.getLabelId() != null) {
            article.setLabelId(articleVO.getLabelId());
        }
        if (articleVO.getArticleCover() != null) {
            article.setArticleCover(articleVO.getArticleCover());
        }
        if (articleVO.getVideoUrl() != null) {
            article.setVideoUrl(StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null);
        }
        if (articleVO.getCommentStatus() != null) {
            article.setCommentStatus(articleVO.getCommentStatus());
        }
        if (articleVO.getRecommendStatus() != null) {
            article.setRecommendStatus(articleVO.getRecommendStatus());
        }
        if (articleVO.getViewStatus() != null) {
            article.setViewStatus(articleVO.getViewStatus());
        }
        if (articleVO.getSubmitToSearchEngine() != null) {
            article.setSubmitToSearchEngine(articleVO.getSubmitToSearchEngine());
        }
        if (Boolean.FALSE.equals(articleVO.getViewStatus())) {
            article.setPassword(articleVO.getPassword());
            article.setTips(articleVO.getTips());
        } else if (Boolean.TRUE.equals(articleVO.getViewStatus())) {
            article.setPassword(null);
            article.setTips(null);
        }
        if (articleVO.getPayType() != null) {
            article.setPayType(articleVO.getPayType());
        }
        if (articleVO.getPayAmount() != null) {
            article.setPayAmount(articleVO.getPayAmount());
        }
        if (articleVO.getFreePercent() != null) {
            article.setFreePercent(articleVO.getFreePercent());
        }
        if (adminUser != null) {
            article.setUpdateBy(adminUser.getUsername());
        }
    }

    private void processPostUpdate(Article article, ArticleVO articleVO, Integer previousSortId, Integer previousLabelId,
                                   String previousArticleSlug) {
        cacheService.evictSortArticleList();

        Map<String, String> pendingTranslation = buildPendingTranslation(articleVO);
        boolean skipAiTranslation = Boolean.TRUE.equals(articleVO.getSkipAiTranslation());

        asyncExecutor.execute(() -> {
            try {
                translationService.translateAndSaveArticle(article.getId(), skipAiTranslation, pendingTranslation);
            } catch (Exception e) {
                log.error("API更新文章后自动翻译失败，文章ID: {}", article.getId(), e);
            }
        });

        if (StringUtils.hasText(article.getArticleContent())) {
            try {
                summaryService.updateSummary(article.getId(), article.getArticleContent());
            } catch (Exception e) {
                log.error("API更新文章后摘要更新失败，文章ID: {}", article.getId(), e);
            }
        }

        try {
            eventPublisher.publishEvent(new ArticleSavedEvent(
                    article.getId(),
                    article.getSortId(),
                    article.getLabelId(),
                    previousSortId,
                    previousLabelId,
                    null,
                    article.getViewStatus(),
                    "UPDATE",
                    article.getSubmitToSearchEngine(),
                    previousArticleSlug
            ));
        } catch (Exception e) {
            log.error("API更新文章后发布事件失败，文章ID: {}", article.getId(), e);
        }
    }

    private Map<String, Object> buildArticleResponseData(Integer articleId,
                                                         ArticleVO articleVO,
                                                         WebInfo webInfo,
                                                         HttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", articleId);
        data.put("articleId", articleId);
        data.put("articleUrl", buildArticleUrl(webInfo, request, articleId));
        data.put("articleSlug", articleVO.getArticleSlug());
        data.put("viewStatus", articleVO.getViewStatus());
        data.put("sortId", articleVO.getSortId());
        data.put("labelId", articleVO.getLabelId());
        return data;
    }

    private Map<String, Object> buildTaskCreatedResponseData(String taskId,
                                                             WebInfo webInfo,
                                                             HttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "processing");
        data.put("completed", false);
        data.put("taskStatusUrl", buildTaskStatusUrl(webInfo, request, taskId));
        return data;
    }

    private Map<String, Object> buildTaskStatusResponseData(ArticleSaveStatus status,
                                                            WebInfo webInfo,
                                                            HttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", status.getTaskId());
        data.put("status", status.getStatus());
        data.put("stage", status.getStage());
        data.put("message", status.getMessage());
        data.put("articleId", status.getArticleId());
        data.put("translationStatus", status.getTranslationStatus());
        data.put("summaryStatus", status.getSummaryStatus());
        data.put("summaryMessage", status.getSummaryMessage());
        data.put("summaryReceivedChars", status.getSummaryReceivedChars());
        data.put("summaryPreview", status.getSummaryPreview());
        data.put("translationAttempt", status.getTranslationAttempt());
        data.put("streaming", status.getStreaming());
        data.put("translatedTitlePreview", status.getTranslatedTitlePreview());
        data.put("translatedContentPreview", status.getTranslatedContentPreview());
        data.put("lastUpdateTime", status.getLastUpdateTime());
        data.put("completed", isTaskCompleted(status.getStatus()));
        data.put("success", "success".equals(status.getStatus()) || "partial_success".equals(status.getStatus()));
        data.put("failed", "failed".equals(status.getStatus()));

        if (status.getArticleId() != null) {
            Article article = articleService.getById(status.getArticleId());
            if (article != null) {
                data.put("articleSlug", article.getArticleSlug());
            }
            data.put("articleUrl", buildArticleUrl(webInfo, request, status.getArticleId()));
        }

        return data;
    }

    private SysPlugin resolveTargetPaymentPlugin(String pluginKey, SysPlugin activePlugin, List<SysPlugin> paymentPlugins) {
        if (StringUtils.hasText(pluginKey)) {
            return paymentService.getPaymentPluginByKey(pluginKey);
        }
        if (activePlugin != null) {
            return activePlugin;
        }
        return CollectionUtils.isEmpty(paymentPlugins) ? null : paymentPlugins.get(0);
    }

    private List<Map<String, Object>> buildPaymentPluginItems(List<SysPlugin> paymentPlugins, SysPlugin activePlugin) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysPlugin plugin : paymentPlugins) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pluginKey", plugin.getPluginKey());
            item.put("pluginName", plugin.getPluginName());
            item.put("enabled", plugin.getEnabled());
            item.put("active", activePlugin != null && plugin.getId() != null && plugin.getId().equals(activePlugin.getId()));
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> buildPaymentPluginDetail(SysPlugin plugin, SysPlugin activePlugin) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> evaluation = paymentService.mergeAndValidateConfig(
                plugin.getPluginKey(),
                paymentService.parsePluginConfig(plugin),
                new LinkedHashMap<>()
        );
        detail.put("pluginKey", plugin.getPluginKey());
        detail.put("pluginName", plugin.getPluginName());
        detail.put("enabled", plugin.getEnabled());
        detail.put("active", activePlugin != null && plugin.getId() != null && plugin.getId().equals(activePlugin.getId()));
        detail.put("configSchema", paymentService.parseConfigSchema(plugin));
        detail.put("configured", evaluation.get("configured"));
        detail.put("missingFields", evaluation.get("missingFields"));
        detail.put("secretFieldStatus", evaluation.get("secretFieldStatus"));
        detail.put("nonSecretConfigPreview", evaluation.get("nonSecretConfigPreview"));
        detail.put("supportsConnectionTest", paymentService.supportsConnectionTest(plugin.getPluginKey()));
        return detail;
    }

    private Map<String, Object> buildPaymentConfigureResponse(SysPlugin plugin,
                                                              Map<String, Object> evaluation,
                                                              boolean connectionOk,
                                                              String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        SysPlugin activePlugin = paymentService.getActivePaymentPlugin();
        data.put("pluginKey", plugin.getPluginKey());
        data.put("pluginName", plugin.getPluginName());
        data.put("active", activePlugin != null && plugin.getId() != null && plugin.getId().equals(activePlugin.getId()));
        data.put("configured", evaluation.get("configured"));
        data.put("connectionOk", connectionOk);
        data.put("missingFields", evaluation.get("missingFields"));
        data.put("secretFieldStatus", evaluation.get("secretFieldStatus"));
        data.put("nonSecretConfigPreview", evaluation.get("nonSecretConfigPreview"));
        data.put("message", message);
        return data;
    }

    private Map<String, Object> buildPaymentConnectionTestResponse(SysPlugin plugin,
                                                                   Map<String, Object> evaluation,
                                                                   boolean connectionOk,
                                                                   String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pluginKey", plugin.getPluginKey());
        data.put("connectionOk", connectionOk);
        data.put("message", message);
        data.put("missingFields", evaluation.get("missingFields"));
        data.put("secretFieldStatus", evaluation.get("secretFieldStatus"));
        data.put("nonSecretConfigPreview", evaluation.get("nonSecretConfigPreview"));
        return data;
    }

    private boolean isTaskCompleted(String status) {
        return "success".equals(status) || "failed".equals(status) || "partial_success".equals(status);
    }

    private String buildTaskStatusUrl(WebInfo webInfo, HttpServletRequest request, String taskId) {
        String baseUrl = buildApiBaseUrl(request);
        if (!StringUtils.hasText(baseUrl) && webInfo != null && StringUtils.hasText(webInfo.getSiteAddress())) {
            baseUrl = webInfo.getSiteAddress().trim().replaceAll("/+$", "");
        }
        if (!StringUtils.hasText(baseUrl)) {
            return "/api/article/task/" + taskId;
        }
        return baseUrl.replaceAll("/+$", "") + "/api/article/task/" + taskId;
    }

    private String buildArticleUrl(WebInfo webInfo, HttpServletRequest request, Integer articleId) {
        String baseUrl = null;
        if (webInfo != null && StringUtils.hasText(webInfo.getSiteAddress())) {
            baseUrl = webInfo.getSiteAddress().trim();
        }
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = buildApiBaseUrl(request);
        }
        if (!StringUtils.hasText(baseUrl)) {
            return buildArticlePath(articleId);
        }
        return baseUrl.replaceAll("/+$", "") + buildArticlePath(articleId);
    }

    private String buildArticlePath(Integer articleId) {
        Article article = articleId == null ? null : articleService.getById(articleId);
        if (article == null) {
            return "/article/" + articleId;
        }
        return ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug());
    }

    private String buildApiBaseUrl(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(request.getScheme()).append("://").append(request.getServerName());
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            builder.append(":").append(request.getServerPort());
        }
        return builder.toString();
    }

    private String buildApiUploadPath(String type, String originalFilename) {
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String safeType = StringUtils.hasText(type) ? type : "articleCover";
        String dateSegment = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return safeType + "/api/" + dateSegment + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private Map<String, Object> buildThemePluginItem(SysPlugin plugin, SysPlugin activePlugin) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pluginKey", plugin.getPluginKey());
        item.put("pluginName", plugin.getPluginName());
        item.put("enabled", plugin.getEnabled());
        item.put("active", activePlugin != null
                && plugin.getId() != null
                && plugin.getId().equals(activePlugin.getId()));
        item.put("pluginConfig", parsePluginConfigValue(plugin.getPluginConfig()));
        return item;
    }

    private Object parsePluginConfigValue(String rawConfig) {
        if (!StringUtils.hasText(rawConfig)) {
            return new LinkedHashMap<>();
        }
        try {
            return JsonUtils.parseObject(rawConfig);
        } catch (Exception e) {
            return rawConfig;
        }
    }

    private Map<String, Object> filterSeoConfig(Map<String, Object> sourceConfig) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (sourceConfig == null) {
            return filtered;
        }
        for (String key : ALLOWED_SEO_CONFIG_FIELDS) {
            filtered.put(key, sourceConfig.get(key));
        }
        return filtered;
    }

    private Map<String, Object> buildSearchEnginePushOverview(Map<String, Object> config) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("baidu", Boolean.TRUE.equals(config.get("baidu_push_enabled")));
        overview.put("bing", Boolean.TRUE.equals(config.get("bing_push_enabled")));
        return overview;
    }

    private Map<String, Object> buildSiteVerificationOverview(Map<String, Object> config) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("baidu", StringUtils.hasText(valueAsString(config.get("baidu_site_verification"))));
        overview.put("google", StringUtils.hasText(valueAsString(config.get("google_site_verification"))));
        return overview;
    }

    private Map<String, Object> buildSeoHealthSummary(Map<String, Object> config) {
        List<String> warnings = new ArrayList<>();
        if (!Boolean.TRUE.equals(config.get("enable"))) {
            warnings.add("SEO 功能当前未启用");
        }
        if (!StringUtils.hasText(valueAsString(config.get("site_description")))) {
            warnings.add("网站描述未配置");
        }
        if (!StringUtils.hasText(valueAsString(config.get("site_keywords")))) {
            warnings.add("网站关键词未配置");
        }
        if (!StringUtils.hasText(valueAsString(config.get("default_author")))) {
            warnings.add("默认作者未配置");
        }
        if (!Boolean.TRUE.equals(config.get("baidu_push_enabled"))
                && !Boolean.TRUE.equals(config.get("bing_push_enabled"))) {
            warnings.add("搜索引擎推送尚未启用");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("healthy", warnings.isEmpty());
        summary.put("warnings", warnings);
        return summary;
    }

    private String getLastSitemapUpdateTime() {
        Object cachedValue = cacheService.get(CacheConstants.SITEMAP_LAST_UPDATE_KEY);
        String cachedTimestamp = valueAsString(cachedValue);
        if (StringUtils.hasText(cachedTimestamp)) {
            return cachedTimestamp;
        }

        try {
            SeoConfig fullSeoConfig = seoConfigService.getFullSeoConfig();
            if (fullSeoConfig == null || CollectionUtils.isEmpty(fullSeoConfig.getSearchEnginePushList())) {
                return null;
            }

            LocalDateTime lastPushTime = fullSeoConfig.getSearchEnginePushList().stream()
                    .map(SeoSearchEnginePush::getLastPushTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            if (lastPushTime == null) {
                return null;
            }

            return lastPushTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("获取 sitemap 最近更新时间失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 基于今日 Redis 访问记录构建趋势行（含人机分离计数）。
     * 记录已由 CacheService.getDailyVisitRecords 过滤掉忽略 IP。
     */
    private Map<String, Object> buildTodayVisitStats(List<Map<String, Object>> todayRecords) {
        if (CollectionUtils.isEmpty(todayRecords)) {
            return null;
        }

        Set<String> uniqueIps = new HashSet<>();
        Set<String> humanUniqueIps = new HashSet<>();
        Set<String> jsVerifiedUniqueIps = new HashSet<>();
        long totalVisits = 0;
        long humanVisits = 0;
        long jsVerifiedVisits = 0;
        long browserNoJsVisits = 0;
        long botVisits = 0;
        long unclassifiedVisits = 0;

        for (Map<String, Object> record : todayRecords) {
            if (record == null) {
                continue;
            }
            String ip = valueAsString(record.get("ip"));
            if (!StringUtils.hasText(ip)) {
                continue;
            }
            uniqueIps.add(ip);
            totalVisits++;
            String uaKind = classifyUaKind(valueAsString(record.get("uaType")));
            switch (uaKind) {
                case "human" -> {
                    humanVisits++;
                    humanUniqueIps.add(ip);
                    // JS 验证口径：track 渠道说明真实执行了 JS；nginx 渠道说明从未执行 JS
                    String source = valueAsString(record.get("visitSource"));
                    if ("track".equals(source)) {
                        jsVerifiedVisits++;
                        jsVerifiedUniqueIps.add(ip);
                    } else if ("nginx".equals(source)) {
                        browserNoJsVisits++;
                    }
                }
                case "bot" -> botVisits++;
                default -> unclassifiedVisits++;
            }
        }

        if (totalVisits == 0) {
            return null;
        }

        Map<String, Object> todayStats = new LinkedHashMap<>();
        todayStats.put("visit_date", java.time.LocalDate.now().toString());
        todayStats.put("unique_visits", uniqueIps.size());
        todayStats.put("total_visits", totalVisits);
        todayStats.put("human_visits", humanVisits);
        todayStats.put("human_unique_visits", (long) humanUniqueIps.size());
        todayStats.put("js_verified_visits", jsVerifiedVisits);
        todayStats.put("js_verified_unique_visits", (long) jsVerifiedUniqueIps.size());
        todayStats.put("browser_no_js_visits", browserNoJsVisits);
        todayStats.put("bot_visits", botVisits);
        todayStats.put("unclassified_visits", unclassifiedVisits);
        return todayStats;
    }

    /** ua_type 归并为 human/bot/unclassified 三类 */
    private String classifyUaKind(String uaType) {
        if (!StringUtils.hasText(uaType) || "unknown".equals(uaType)) {
            return "unclassified";
        }
        return ("pc".equals(uaType) || "mobile".equals(uaType)) ? "human" : "bot";
    }

    /**
     * 合并历史与今日的 UA 类型汇总，输出各类型访问次数、独立 IP 与占比。
     */
    private List<Map<String, Object>> buildUaTypeBreakdown(List<Map<String, Object>> dbRows,
                                                           List<Map<String, Object>> todayRecords) {
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, Set<String>> todayIpsByType = new HashMap<>();

        if (dbRows != null) {
            for (Map<String, Object> row : dbRows) {
                if (row == null) {
                    continue;
                }
                String type = valueAsString(row.get("ua_type"));
                if (!StringUtils.hasText(type)) {
                    type = "unknown";
                }
                long[] acc = agg.computeIfAbsent(type, k -> new long[2]);
                acc[0] += asLongValue(row.get("visits"));
                acc[1] += asLongValue(row.get("unique_ips"));
            }
        }

        if (todayRecords != null) {
            for (Map<String, Object> record : todayRecords) {
                if (record == null) {
                    continue;
                }
                String type = valueAsString(record.get("uaType"));
                if (!StringUtils.hasText(type)) {
                    type = "unknown";
                }
                long[] acc = agg.computeIfAbsent(type, k -> new long[2]);
                acc[0] += 1;
                String ip = valueAsString(record.get("ip"));
                if (StringUtils.hasText(ip)
                        && todayIpsByType.computeIfAbsent(type, k -> new HashSet<>()).add(ip)) {
                    acc[1] += 1;
                }
            }
        }

        long total = agg.values().stream().mapToLong(a -> a[0]).sum();
        List<Map<String, Object>> rows = new ArrayList<>();
        agg.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ua_type", e.getKey());
                    row.put("ua_type_label", UserAgentClassifier.typeLabelOf(e.getKey()));
                    row.put("kind", classifyUaKind(e.getKey()));
                    row.put("visits", e.getValue()[0]);
                    row.put("unique_ips", e.getValue()[1]);
                    row.put("percentage", total > 0
                            ? Math.round(e.getValue()[0] * 10000.0 / total) / 100.0
                            : 0.0);
                    rows.add(row);
                });
        return rows;
    }

    /**
     * 合并历史与今日的来源站点 × 人机交叉统计。
     * 爬虫通常不携带 Referer，因此 Direct 行的 bot/unclassified 占比是判断爬虫噪声的关键信号。
     */
    private List<Map<String, Object>> buildReferrerBreakdown(List<Map<String, Object>> dbRows,
                                                             List<Map<String, Object>> todayRecords,
                                                             String siteHost) {
        // host -> [visits, uniqueVisitors, humanVisits, botVisits, unclassifiedVisits, jsVerifiedVisits, browserNoJsVisits]
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, Set<String>> todayIpsByHost = new HashMap<>();

        if (dbRows != null) {
            for (Map<String, Object> row : dbRows) {
                if (row == null) {
                    continue;
                }
                String host = valueAsString(row.get("referrer_host"));
                if (!StringUtils.hasText(host)) {
                    host = "Direct";
                }
                long[] acc = agg.computeIfAbsent(host, k -> new long[7]);
                acc[0] += asLongValue(row.get("visits"));
                acc[1] += asLongValue(row.get("unique_visitors"));
                acc[2] += asLongValue(row.get("human_visits"));
                acc[3] += asLongValue(row.get("bot_visits"));
                acc[4] += asLongValue(row.get("unclassified_visits"));
                acc[5] += asLongValue(row.get("js_verified_visits"));
                acc[6] += asLongValue(row.get("browser_no_js_visits"));
            }
        }

        if (todayRecords != null) {
            for (Map<String, Object> record : todayRecords) {
                if (record == null) {
                    continue;
                }
                String host = extractRefererHost(valueAsString(record.get("referer")));
                if (host == null || (siteHost != null && host.equals(siteHost))) {
                    host = "Direct";
                }
                long[] acc = agg.computeIfAbsent(host, k -> new long[7]);
                acc[0] += 1;
                String ip = valueAsString(record.get("ip"));
                if (StringUtils.hasText(ip)
                        && todayIpsByHost.computeIfAbsent(host, k -> new HashSet<>()).add(ip)) {
                    acc[1] += 1;
                }
                String uaKind = classifyUaKind(valueAsString(record.get("uaType")));
                switch (uaKind) {
                    case "human" -> {
                        acc[2] += 1;
                        String source = valueAsString(record.get("visitSource"));
                        if ("track".equals(source)) {
                            acc[5] += 1;
                        } else if ("nginx".equals(source)) {
                            acc[6] += 1;
                        }
                    }
                    case "bot" -> acc[3] += 1;
                    default -> acc[4] += 1;
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        agg.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(30)
                .forEach(e -> {
                    long visits = e.getValue()[0];
                    // 可疑量 = 已识别爬虫 + UA未识别 + 浏览器UA但从未执行JS（高仿爬虫）
                    long suspicious = e.getValue()[3] + e.getValue()[4] + e.getValue()[6];
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("referrer_host", e.getKey());
                    row.put("visits", visits);
                    row.put("unique_visitors", e.getValue()[1]);
                    row.put("human_visits", e.getValue()[2]);
                    row.put("js_verified_visits", e.getValue()[5]);
                    row.put("browser_no_js_visits", e.getValue()[6]);
                    row.put("bot_visits", e.getValue()[3]);
                    row.put("unclassified_visits", e.getValue()[4]);
                    row.put("suspicious_share_percent", visits > 0
                            ? Math.round(suspicious * 10000.0 / visits) / 100.0
                            : 0.0);
                    rows.add(row);
                });
        return rows;
    }

    /**
     * 合并历史与今日的地区访客统计（海外按国家、中国按省份）。
     * 输出访问次数、独立IP与JS已验证真实访客，供 Agent 判断地域分布与爬虫聚集地。
     */
    private List<Map<String, Object>> buildRegionBreakdown(List<Map<String, Object>> dbRows,
                                                           List<Map<String, Object>> todayRecords) {
        // region -> [visits, uniqueVisitors, jsVerified]
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, Set<String>> todayIpsByRegion = new HashMap<>();

        if (dbRows != null) {
            for (Map<String, Object> row : dbRows) {
                if (row == null) {
                    continue;
                }
                String region = valueAsString(row.get("province"));
                if (!StringUtils.hasText(region) || "未知".equals(region)) {
                    continue;
                }
                long[] acc = agg.computeIfAbsent(region, k -> new long[3]);
                acc[0] += asLongValue(row.get("num"));
                acc[1] += asLongValue(row.get("unique_visitors"));
                acc[2] += asLongValue(row.get("js_verified_visits"));
            }
        }

        if (todayRecords != null) {
            for (Map<String, Object> record : todayRecords) {
                if (record == null) {
                    continue;
                }
                String region = com.ld.poetry.utils.VisitRegionNormalizer.resolveProvinceOrCountry(
                        record.get("nation"), record.get("province"), record.get("city"));
                if (!StringUtils.hasText(region)) {
                    continue;
                }
                long[] acc = agg.computeIfAbsent(region, k -> new long[3]);
                acc[0] += 1;
                String ip = valueAsString(record.get("ip"));
                if (StringUtils.hasText(ip)
                        && todayIpsByRegion.computeIfAbsent(region, k -> new HashSet<>()).add(ip)) {
                    acc[1] += 1;
                }
                String uaType = valueAsString(record.get("uaType"));
                if (("pc".equals(uaType) || "mobile".equals(uaType))
                        && "track".equals(valueAsString(record.get("visitSource")))) {
                    acc[2] += 1;
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        agg.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(30)
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("region", e.getKey());
                    row.put("visits", e.getValue()[0]);
                    row.put("unique_visitors", e.getValue()[1]);
                    row.put("js_verified_visits", e.getValue()[2]);
                    rows.add(row);
                });
        return rows;
    }

    /** 周期汇总：全周期人机分离总量与占比（含JS验证口径） */
    private Map<String, Object> buildVisitSummary(int days,
                                                  List<Map<String, Object>> daily,
                                                  List<Map<String, Object>> uaTypeBreakdown) {
        long total = 0;
        long human = 0;
        long jsVerified = 0;
        long browserNoJs = 0;
        long bot = 0;
        long unclassified = 0;
        for (Map<String, Object> row : daily) {
            total += asLongValue(row.get("total_visits"));
            human += asLongValue(row.get("human_visits"));
            jsVerified += asLongValue(row.get("js_verified_visits"));
            browserNoJs += asLongValue(row.get("browser_no_js_visits"));
            bot += asLongValue(row.get("bot_visits"));
            unclassified += asLongValue(row.get("unclassified_visits"));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("days", days);
        summary.put("total_visits", total);
        summary.put("human_visits", human);
        summary.put("js_verified_visits", jsVerified);
        summary.put("browser_no_js_visits", browserNoJs);
        summary.put("bot_visits", bot);
        summary.put("unclassified_visits", unclassified);
        summary.put("human_share_percent", total > 0 ? Math.round(human * 10000.0 / total) / 100.0 : 0.0);
        summary.put("js_verified_share_percent", total > 0 ? Math.round(jsVerified * 10000.0 / total) / 100.0 : 0.0);
        summary.put("bot_share_percent", total > 0 ? Math.round(bot * 10000.0 / total) / 100.0 : 0.0);
        summary.put("unclassified_share_percent", total > 0 ? Math.round(unclassified * 10000.0 / total) / 100.0 : 0.0);
        summary.put("ua_type_count", uaTypeBreakdown.size());
        return summary;
    }

    /** 面向 Agent 的流量解读提示，避免把爬虫噪声误判为真实流量暴涨 */
    private Map<String, Object> buildVisitAgentGuide() {
        Map<String, Object> guide = new LinkedHashMap<>();
        guide.put("message", "判断真实流量请优先看 js_verified_visits（执行过JS的真实浏览器），其次 human_visits，最后才是 total_visits。");
        guide.put("notes", List.of(
                "js_verified_visits 是最硬的真实访客口径：记录由前端 JS 上报（visit_source=track），说明访客真实执行了 JS；不执行 JS 的高仿爬虫无法伪造此标记。",
                "browser_no_js_visits 是 UA 像浏览器但从未执行 JS 的访问（仅被 Nginx 日志补录），大概率是伪装正常 UA 的爬虫，不应计入真实访客。",
                "human_visits 仅基于 UA 判断（pc/mobile），会被高仿 UA 爬虫污染；human_visits 远大于 js_verified_visits + browser_no_js_visits 时，差值部分是存量无渠道标记的历史数据（visit_source 为空）。",
                "爬虫通常不携带 Referer，会被归入 Direct。referrer_breakdown 中 Direct 行的 suspicious_share_percent（含 browser_no_js）偏高时，Direct 增长大概率是爬虫噪声。",
                "region_breakdown 是地区分布（海外按国家、中国按省份）：若某地区 visits 很高但 js_verified_visits 接近 0，说明该地区流量基本是爬虫（常见于数据中心所在地）。",
                "top_uas 中 bot_verify_status=failed 表示自称搜索引擎但 DNS 反查失败，属于伪装爬虫；verified 才是经过验证的官方搜索引擎。",
                "若 daily 中 js_verified_visits 平稳而 total_visits 或 human_visits 突增，基本可判定为爬虫/扫描活动，不应报告为流量暴涨。"
        ));
        return guide;
    }

    /** 解析站点自身 host（去端口、小写），用于识别站内跳转 */
    private String resolveSiteHostFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String host = request.getHeader("Host");
        if (host == null || host.isEmpty()) {
            return null;
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.toLowerCase();
    }

    /** 从 Referer 字符串提取 host（去协议、去端口、小写） */
    private String extractRefererHost(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            String s = referer.trim();
            int schemeIdx = s.indexOf("://");
            if (schemeIdx >= 0) {
                s = s.substring(schemeIdx + 3);
            }
            int slash = s.indexOf('/');
            if (slash >= 0) {
                s = s.substring(0, slash);
            }
            int colon = s.indexOf(':');
            if (colon >= 0) {
                s = s.substring(0, colon);
            }
            s = s.trim();
            return s.isEmpty() ? null : s.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private long asLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private List<Map<String, Object>> fillMissingDates(List<Map<String, Object>> stats, int days) {
        Map<String, Map<String, Object>> statsMap = new HashMap<>();
        for (Map<String, Object> stat : stats) {
            String date = valueAsString(stat.get("visit_date"));
            if (StringUtils.hasText(date)) {
                statsMap.put(date, stat);
            }
        }

        List<Map<String, Object>> completeStats = new ArrayList<>();
        java.time.LocalDate endDate = java.time.LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            java.time.LocalDate date = endDate.minusDays(i);
            String dateStr = date.toString();

            Map<String, Object> dayStats = statsMap.get(dateStr);
            if (dayStats == null) {
                dayStats = new LinkedHashMap<>();
                dayStats.put("visit_date", dateStr);
                dayStats.put("unique_visits", 0);
                dayStats.put("total_visits", 0);
                dayStats.put("human_visits", 0);
                dayStats.put("human_unique_visits", 0);
                dayStats.put("js_verified_visits", 0);
                dayStats.put("js_verified_unique_visits", 0);
                dayStats.put("browser_no_js_visits", 0);
                dayStats.put("bot_visits", 0);
                dayStats.put("unclassified_visits", 0);
            }
            completeStats.add(dayStats);
        }
        return completeStats;
    }

    private void applyVisitAverages(List<Map<String, Object>> stats) {
        if (CollectionUtils.isEmpty(stats)) {
            return;
        }

        double avgUniqueVisits = stats.stream()
                .map(item -> item.get("unique_visits"))
                .filter(Objects::nonNull)
                .mapToDouble(value -> ((Number) value).doubleValue())
                .average()
                .orElse(0D);
        double avgTotalVisits = stats.stream()
                .map(item -> item.get("total_visits"))
                .filter(Objects::nonNull)
                .mapToDouble(value -> ((Number) value).doubleValue())
                .average()
                .orElse(0D);

        double roundedAvgUniqueVisits = Math.round(avgUniqueVisits * 100.0) / 100.0;
        double roundedAvgTotalVisits = Math.round(avgTotalVisits * 100.0) / 100.0;

        for (Map<String, Object> item : stats) {
            item.put("avg_unique_visits", roundedAvgUniqueVisits);
            item.put("avg_total_visits", roundedAvgTotalVisits);
        }
    }

    private Map<String, Object> toObjectMap(Object source) {
        if (!(source instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private List<String> toStringList(Object source) {
        if (!(source instanceof List<?> rawList)) {
            return List.of();
        }

        List<String> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    
    /**
     * API查询分类列表
     */
    @GetMapping("/categories")
    public PoetryResult getCategoryList(HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 查询所有分类
            List<Sort> sortList = sortService.list();
            
            return PoetryResult.success(sortList);
        } catch (PoetryRuntimeException e) {
            log.error("API查询分类列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询分类列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API查询标签列表
     */
    @GetMapping("/tags")
    public PoetryResult getTagList(HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 查询所有标签
            List<Label> labelList = labelService.list();
            
            return PoetryResult.success(labelList);
        } catch (PoetryRuntimeException e) {
            log.error("API查询标签列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询标签列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API查询评论列表
     */
    @PostMapping("/comment/list")
    public PoetryResult getCommentList(HttpServletRequest request, @RequestBody BaseRequestVO baseRequestVO) {
        try {
            validateApiKey(request);
            return commentService.listComment(baseRequestVO);
        } catch (PoetryRuntimeException e) {
            log.error("API查询评论列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询评论列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API发表/回复评论（免验证码，支持通过 commentInfo 设置 aiReply 属性进行 AI/管理员 模拟）
     */
    @PostMapping("/comment/save")
    public PoetryResult saveComment(HttpServletRequest request, @RequestBody CommentVO commentVO) {
        try {
            validateApiKey(request);
            
            if (CommentTypeEnum.getEnumByCode(commentVO.getType()) == null) {
                return PoetryResult.fail("评论来源类型不存在！");
            }
            
            if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentVO.getType())) {
                Article one = articleService.lambdaQuery().eq(Article::getId, commentVO.getSource())
                        .select(Article::getId, Article::getCommentStatus).one();
                if (one == null) {
                    return PoetryResult.fail("文章不存在");
                }
                if (!one.getCommentStatus()) {
                    return PoetryResult.fail("评论功能已关闭！");
                }
            }
            
            String content = XssFilterUtil.clean(commentVO.getCommentContent());
            if (!StringUtils.hasText(content)) {
                return PoetryResult.fail("评论内容不合法！");
            }
            commentVO.setCommentContent(content);
            
            return commentService.saveAiReplyComment(commentVO);
        } catch (PoetryRuntimeException e) {
            log.error("API保存评论失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API保存评论出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    // ========================================================================
    // 翻译管理端点（API Key 认证）
    // 这些端点复用 TranslationService 的已有能力，但走 /api/api/translation/*
    // 路径，允许通过 API Key 而非浏览器会话登录来管理文章翻译。
    // ========================================================================

    /**
     * API获取文章翻译
     */
    @GetMapping("/translation/get")
    public PoetryResult<Map<String, String>> getTranslation(
            @RequestParam("articleId") Integer articleId,
            @RequestParam(value = "language", defaultValue = "en") String language,
            HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (articleId == null || articleId <= 0) {
                return PoetryResult.fail("文章ID不能为空");
            }
            if (!StringUtils.hasText(language)) {
                return PoetryResult.fail("翻译语言不能为空");
            }
            Map<String, String> translationResult = translationService.getArticleTranslation(articleId, language);
            String translationStatus = translationResult.get("status");
            if (!"success".equals(translationStatus)) {
                return PoetryResult.fail(translationResult.getOrDefault("error", "翻译获取失败"));
            }
            return PoetryResult.success(translationResult);
        } catch (PoetryRuntimeException e) {
            log.error("API获取翻译失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取翻译出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API获取文章可用翻译语言列表
     */
    @GetMapping("/translation/languages")
    public PoetryResult<List<String>> getTranslationLanguages(
            @RequestParam("articleId") Integer articleId,
            HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (articleId == null || articleId <= 0) {
                return PoetryResult.fail("文章ID不能为空");
            }
            List<String> languages = translationService.getArticleAvailableLanguages(articleId);
            return PoetryResult.success(languages);
        } catch (PoetryRuntimeException e) {
            log.error("API获取翻译语言列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取翻译语言列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API手动保存/更新文章翻译
     * <p>
     * 允许 Agent 直接编辑翻译内容而不需要重新运行 AI 翻译。
     * 如果该语言的翻译已存在，则覆盖更新；否则新增。
     */
    @PostMapping("/translation/save")
    public PoetryResult<Map<String, Object>> saveTranslation(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            validateApiKey(request);
            Integer articleId = parseInteger(body.get("articleId"));
            String targetLanguage = stringValue(body.get("targetLanguage"));
            String translatedTitle = stringValue(body.get("translatedTitle"));
            String translatedContent = stringValue(body.get("translatedContent"));
            String translatedSummary = stringValue(body.get("translatedSummary"));

            if (articleId == null || articleId <= 0) {
                return PoetryResult.fail("articleId 不能为空");
            }
            if (!StringUtils.hasText(targetLanguage)) {
                return PoetryResult.fail("targetLanguage 不能为空");
            }
            if (!StringUtils.hasText(translatedTitle)) {
                return PoetryResult.fail("translatedTitle 不能为空");
            }
            if (!StringUtils.hasText(translatedContent)) {
                return PoetryResult.fail("translatedContent 不能为空");
            }
            try {
                MarkdownSectionEditor.validateArticleBody(translatedContent);
            } catch (IllegalArgumentException e) {
                return PoetryResult.fail(e.getMessage());
            }

            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            Map<String, Object> result = translationService.saveManualTranslation(
                    articleId, targetLanguage, translatedTitle, translatedContent, translatedSummary);

            if (Boolean.TRUE.equals(result.get("success"))) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("articleId", articleId);
                data.put("language", targetLanguage);
                data.put("message", result.get("message"));
                return PoetryResult.success(data);
            }
            return PoetryResult.fail(String.valueOf(result.getOrDefault("message", "翻译保存失败")));
        } catch (PoetryRuntimeException e) {
            log.error("API保存翻译失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API保存翻译出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API删除文章的特定语言翻译
     */
    @PostMapping("/translation/delete")
    public PoetryResult<Map<String, Object>> deleteTranslation(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            validateApiKey(request);
            Integer articleId = parseInteger(body.get("articleId"));
            String language = stringValue(body.get("language"));

            if (articleId == null || articleId <= 0) {
                return PoetryResult.fail("articleId 不能为空");
            }
            if (!StringUtils.hasText(language)) {
                return PoetryResult.fail("language 不能为空");
            }
            language = language.trim();

            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            boolean deleted = translationService.deleteSpecificTranslation(articleId, language);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("articleId", articleId);
            data.put("language", language);
            data.put("deleted", deleted);
            if (deleted) {
                data.put("message", "翻译删除成功");
                return PoetryResult.success(data);
            }
            return PoetryResult.fail("翻译不存在或删除失败");
        } catch (PoetryRuntimeException e) {
            log.error("API删除翻译失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API删除翻译出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    /**
     * API重新生成文章翻译（删除现有翻译并重新翻译）
     */
    @PostMapping("/translation/regenerate")
    public PoetryResult<Map<String, Object>> regenerateTranslation(
            @RequestParam("articleId") Integer articleId,
            HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (articleId == null || articleId <= 0) {
                return PoetryResult.fail("文章ID不能为空");
            }

            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            translationService.refreshArticleTranslation(articleId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("articleId", articleId);
            data.put("message", "翻译重新生成已完成");
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API重新生成翻译失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API重新生成翻译出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    // ========================================================================
    // 文章局部内容更新端点（API Key 认证）
    // 允许 Agent 按标题定位章节进行替换/插入/删除，避免整篇重新生成。
    // ========================================================================

    /**
     * API局部更新文章内容（按章节标题定位）
     * <p>
     * 支持的操作：
     * <ul>
     *   <li>replace — 替换匹配章节（标题+正文）为新内容</li>
     *   <li>insert_after — 在匹配章节之后插入新内容</li>
     *   <li>insert_before — 在匹配标题之前插入新内容</li>
     *   <li>delete — 删除匹配章节（标题+正文）</li>
     *   <li>append — 在文章末尾追加内容</li>
     * </ul>
     *
     * @param body 包含 articleId, heading, action, content, newHeadingLevel, skipAiTranslation
     */
    @PostMapping("/article/updateSection")
    public PoetryResult<Map<String, Object>> updateArticleSection(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            validateApiKey(request);
            Integer articleId = parseInteger(body.get("articleId"));
            String heading = stringValue(body.get("heading"));
            String action = stringValue(body.get("action"));
            String content = stringValue(body.get("content"));
            Integer newHeadingLevel = parseInteger(body.get("newHeadingLevel"));
            Integer headingIndex = parseInteger(body.get("headingIndex"));
            boolean skipAiTranslation = Boolean.TRUE.equals(body.get("skipAiTranslation"));
            boolean dryRun = Boolean.TRUE.equals(body.get("dryRun"));

            if (articleId == null || articleId <= 0) {
                return PoetryResult.fail("articleId 不能为空");
            }
            if (!StringUtils.hasText(action)) {
                return PoetryResult.fail("action 不能为空");
            }
            if (!"append".equals(action) && !StringUtils.hasText(heading)) {
                return PoetryResult.fail("非 append 操作必须指定 heading");
            }
            if (!"delete".equals(action) && !StringUtils.hasText(content)) {
                return PoetryResult.fail("非 delete 操作必须提供 content");
            }
            if (newHeadingLevel != null) {
                if (!"replace".equals(action)) {
                    return PoetryResult.fail("newHeadingLevel 仅用于 replace 操作");
                }
                if (newHeadingLevel < 2 || newHeadingLevel > 6) {
                    return PoetryResult.fail("newHeadingLevel 必须在 2 到 6 之间；正文一级标题（H1）由文章大标题独占");
                }
            }

            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }

            String originalContent = article.getArticleContent();
            if (!StringUtils.hasText(originalContent)) {
                return PoetryResult.fail("文章内容为空，无法执行章节更新");
            }

            String updatedContent;
            String originalSectionContent = null;
            String warning = null;
            try {
                MarkdownSectionEditor.SectionEditResult editResult = MarkdownSectionEditor.apply(
                        originalContent,
                        new MarkdownSectionEditor.SectionUpdate(
                                heading, headingIndex, action, content, newHeadingLevel));
                updatedContent = editResult.updatedContent();
                originalSectionContent = editResult.originalSectionContent();
                warning = editResult.warning();
            } catch (IllegalArgumentException e) {
                return PoetryResult.fail(e.getMessage());
            }

            if (updatedContent.equals(originalContent)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("articleId", articleId);
                data.put("message", "内容未发生变化");
                data.put("changed", false);
                return PoetryResult.success(data);
            }

            // Dry-run 路径：只返回预览结果，不执行任何持久化或副作用
            if (dryRun) {
                log.info("章节更新 dry-run 预览请求，文章ID: {}", articleId);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("articleId", articleId);
                data.put("dryRun", true);
                data.put("changed", true);
                data.put("action", action);
                data.put("heading", heading);
                if (newHeadingLevel != null) {
                    data.put("newHeadingLevel", newHeadingLevel);
                }
                data.put("originalContent", originalContent);
                data.put("updatedContent", updatedContent);
                data.put("originalSectionContent", originalSectionContent);
                if (warning != null) {
                    data.put("warning", warning);
                }
                data.put("message", "Dry-run 预览：内容未持久化，未触发翻译/摘要/事件");
                return PoetryResult.success(data);
            }

            // 保存修改后的内容（仅更新必要字段，避免全量写回覆盖并发 viewCount 等字段）
            // CAS 保护：仅当 content 仍为读取时的原值才更新，防止并发编辑互相覆盖
            User adminUser = PoetryUtil.getAdminUser();
            String updateBy = adminUser != null ? adminUser.getUsername() : null;
            boolean updated = articleService.lambdaUpdate()
                    .eq(Article::getId, articleId)
                    .eq(Article::getArticleContent, originalContent)
                    .set(Article::getArticleContent, updatedContent)
                    .set(Article::getUpdateTime, LocalDateTime.now())
                    .set(updateBy != null, Article::getUpdateBy, updateBy)
                    .update();
            if (!updated) {
                return PoetryResult.fail("章节内容更新失败：文章内容可能已被其他请求修改，请重试");
            }

            // 清理缓存
            cacheService.evictSortArticleList();

            // 异步触发翻译和摘要更新
            final Integer finalArticleId = articleId;
            asyncExecutor.execute(() -> {
                try {
                    translationService.translateAndSaveArticle(finalArticleId, skipAiTranslation, null);
                } catch (Exception e) {
                    log.error("章节更新后自动翻译失败，文章ID: {}", finalArticleId, e);
                }
            });

            try {
                summaryService.updateSummary(articleId, updatedContent);
            } catch (Exception e) {
                log.error("章节更新后摘要更新失败，文章ID: {}", articleId, e);
            }

            try {
                eventPublisher.publishEvent(new ArticleSavedEvent(
                        articleId, article.getSortId(), article.getLabelId(),
                        null, null, null,
                        article.getViewStatus(), "UPDATE",
                        article.getSubmitToSearchEngine(), null));
            } catch (Exception e) {
                log.warn("章节更新后事件发布失败，文章ID: {}", articleId, e);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("articleId", articleId);
            data.put("changed", true);
            data.put("action", action);
            data.put("heading", heading);
            if (newHeadingLevel != null) {
                data.put("newHeadingLevel", newHeadingLevel);
            }
            data.put("skipAiTranslation", skipAiTranslation);
            data.put("originalSectionContent", originalSectionContent);
            if (warning != null) {
                data.put("warning", warning);
            }
            data.put("message", "章节内容更新成功");
            data.put("agent_guide", Map.of(
                    "next_steps", List.of(
                            "查看更新后的文章内容: python poetize_cli.py manage get-article --article-id " + articleId,
                            skipAiTranslation
                                    ? "翻译已跳过，如需更新翻译可使用: python poetize_cli.py manage save-translation --article-id " + articleId
                                    : "翻译正在异步重新生成，查看翻译状态: python poetize_cli.py manage get-translation --article-id " + articleId)));
            return PoetryResult.success(data);
        } catch (PoetryRuntimeException e) {
            log.error("API章节更新失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API章节更新出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // ================================ 分类管理 API ================================

    @PostMapping("/sort/create")
    public PoetryResult createSort(@RequestBody Sort sort, HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (!StringUtils.hasText(sort.getSortName()) || !StringUtils.hasText(sort.getSortDescription())) {
                return PoetryResult.fail("分类名称和分类描述不能为空");
            }
            if (sort.getSortType() == null) sort.setSortType(1);
            if (sort.getPriority() == null) sort.setPriority(99);
            sortMapper.insert(sort);
            cacheService.evictSortArticleList();
            if (sitemapService != null) {
                sitemapService.updateSitemapAndPush("API分类创建 (ID=" + sort.getId() + ")");
            }
            log.info("API分类创建成功: {}", sort.getSortName());
            return PoetryResult.success(sort);
        } catch (PoetryRuntimeException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API创建分类失败", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    @PostMapping("/sort/update")
    public PoetryResult updateSort(@RequestBody Sort sort, HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (sort.getId() == null) {
                return PoetryResult.fail("分类ID不能为空");
            }
            sortMapper.updateById(sort);
            cacheService.evictSortArticleList();
            if (sitemapService != null) {
                sitemapService.updateSitemapAndPush("API分类更新 (ID=" + sort.getId() + ")");
            }
            log.info("API分类更新成功: {}", sort.getId());
            return PoetryResult.success();
        } catch (PoetryRuntimeException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API更新分类失败", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    @PostMapping("/sort/delete")
    public PoetryResult deleteSort(@RequestBody Map<String, Integer> payload, HttpServletRequest request) {
        try {
            validateApiKey(request);
            Integer id = payload.get("id");
            if (id == null) {
                return PoetryResult.fail("分类 ID 不能为空");
            }
            sortMapper.deleteById(id);
            cacheService.evictSortArticleList();
            if (sitemapService != null) {
                sitemapService.updateSitemapAndPush("API 分类删除 (ID=" + id + ")");
            }
            log.info("API 分类删除成功：{}", id);
            return PoetryResult.success();
        } catch (PoetryRuntimeException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API 删除分类失败", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    // ================================ 标签管理 API ================================

    @PostMapping("/label/create")
    public PoetryResult createLabel(@RequestBody Label label, HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (!StringUtils.hasText(label.getLabelName()) || !StringUtils.hasText(label.getLabelDescription()) || label.getSortId() == null) {
                return PoetryResult.fail("标签名称、标签描述和分类ID不能为空");
            }
            labelMapper.insert(label);
            cacheService.evictSortArticleList();
            if (sitemapService != null) {
                sitemapService.clearSitemapCache();
            }
            log.info("API标签创建成功: {}", label.getLabelName());
            return PoetryResult.success(label);
        } catch (PoetryRuntimeException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API创建标签失败", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    @PostMapping("/label/update")
    public PoetryResult updateLabel(@RequestBody Label label, HttpServletRequest request) {
        try {
            validateApiKey(request);
            if (label.getId() == null) {
                return PoetryResult.fail("标签ID不能为空");
            }
            labelMapper.updateById(label);
            cacheService.evictSortArticleList();
            if (sitemapService != null) {
                sitemapService.clearSitemapCache();
            }
            log.info("API标签更新成功: {}", label.getId());
            return PoetryResult.success();
        } catch (PoetryRuntimeException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API更新标签失败", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }

    @PostMapping("/label/delete")
    public PoetryResult deleteLabel(@RequestBody Map<String, Integer> payload, HttpServletRequest request) {
        try {
            validateApiKey(request);
            Integer id = payload.get("id");
            if (id == null) {
                return PoetryResult.fail("标签 ID 不能为空");
            }
            labelMapper.deleteById(id);
            cacheService.evictSortArticleList();
            if (sitemapService != null) {
                sitemapService.clearSitemapCache();
            }
            log.info("API 标签删除成功：{}", id);
            return PoetryResult.success();
        } catch (PoetryRuntimeException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API 删除标签失败", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
}
