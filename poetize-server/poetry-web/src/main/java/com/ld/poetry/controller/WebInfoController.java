package com.ld.poetry.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 * 网站信息表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Slf4j
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/webInfo")
public class WebInfoController {

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private TreeHoleMapper treeHoleMapper;

    @Autowired
    private FamilyMapper familyMapper;

    @Autowired
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.ld.poetry.service.SitemapService sitemapService;

    @Autowired
    private PrerenderFacade prerenderFacade;

    @Autowired
    private com.ld.poetry.service.SysPluginService sysPluginService;

    @Autowired
    private com.ld.poetry.service.SeoStaticService seoStaticService;

    @Autowired
    private com.ld.poetry.service.ai.rag.RagSyncService ragSyncService;

    @Autowired
    private com.ld.poetry.service.Live2dAssetService live2dAssetService;

    /**
     * 更新完整网站信息（用于基本信息保存）
     */
    @LoginCheck(0)
    @PostMapping("/updateWebInfo")
    @AuditLog(action = "WEB_INFO_UPDATE", targetType = "WEB_INFO", targetIdParam = "id", summary = "更新网站基础信息")
    public PoetryResult<WebInfo> updateWebInfo(@RequestBody Map<String, Object> params) {
        try {
            // 从Map中提取参数
            Integer id = (Integer) params.get("id");
            String webName = (String) params.get("webName");
            String webTitle = (String) params.get("webTitle");
            String siteAddress = (String) params.get("siteAddress");
            String footer = (String) params.get("footer");
            String backgroundImage = (String) params.get("backgroundImage");
            String avatar = (String) params.get("avatar");
            String waifuJson = (String) params.get("waifuJson");
            Boolean status = (Boolean) params.get("status");
            Boolean enableWaifu = (Boolean) params.get("enableWaifu");
            String waifuDisplayMode = (String) params.get("waifuDisplayMode");
            Integer homePagePullUpHeight = (Integer) params.get("homePagePullUpHeight");
            Boolean apiEnabled = (Boolean) params.get("apiEnabled");
            String apiKey = (String) params.get("apiKey");
            String apiIpWhitelist = (String) params.get("apiIpWhitelist");
            String navConfig = (String) params.get("navConfig");
            String footerBackgroundImage = (String) params.get("footerBackgroundImage");
            String footerBackgroundConfig = (String) params.get("footerBackgroundConfig");
            String email = (String) params.get("email");
            Boolean minimalFooter = (Boolean) params.get("minimalFooter");
            Boolean enableAutoNight = (Boolean) params.get("enableAutoNight");
            Integer autoNightStart = (Integer) params.get("autoNightStart");
            Integer autoNightEnd = (Integer) params.get("autoNightEnd");
            Boolean enableGrayMode = (Boolean) params.get("enableGrayMode");
            Boolean enableDynamicTitle = (Boolean) params.get("enableDynamicTitle");
            String mobileDrawerConfig = (String) params.get("mobileDrawerConfig");
            String mouseClickEffectConfig = (String) params.get("mouseClickEffectConfig");

            // 记录更新前的详细信息
            log.info("开始更新网站基本信息 - ID: {}, webName: {}, webTitle: {}", id, webName, webTitle);

            // 调用专门的基本信息更新方法
            int updateResult = webInfoMapper.updateWebInfoById(id, webName, webTitle, siteAddress, footer,
                    backgroundImage,
                    avatar, waifuJson, status, enableWaifu, waifuDisplayMode, homePagePullUpHeight, apiEnabled, apiKey,
                    apiIpWhitelist,
                    navConfig, footerBackgroundImage, footerBackgroundConfig, email, minimalFooter,
                    enableAutoNight, autoNightStart, autoNightEnd, enableGrayMode, enableDynamicTitle,
                    mouseClickEffectConfig, mobileDrawerConfig);

            log.info("网站基本信息数据库更新结果: {} 行受影响, ID: {}", updateResult, id);

            if (updateResult == 0) {
                log.error("数据库更新失败：没有行受影响，可能ID不存在或数据未变化");
                return PoetryResult.fail("更新失败：网站信息不存在或数据未变化");
            }

            // 验证更新是否成功：重新查询最新数据
            log.info("重新查询数据库验证更新结果...");
            WebInfo latestWebInfo = webInfoService.getById(id);

            if (latestWebInfo != null) {
                // 验证数据是否真正更新
                log.info("数据库查询结果 - webName: {}, webTitle: {}, mouseClickEffectConfig: {}",
                        latestWebInfo.getWebName(), latestWebInfo.getWebTitle(),
                        latestWebInfo.getMouseClickEffectConfig());

                // 先删除旧缓存，再设置新缓存
                cacheService.evictWebInfo();
                cacheService.cacheWebInfo(latestWebInfo);
                log.info("网站信息缓存更新成功");

                // 网站信息更新时，清除各种缓存并重新渲染页面
                try {
                    // 1. 清除sitemap缓存（网站名称、标题等可能影响sitemap内容）
                    if (sitemapService != null) {
                        sitemapService.clearSitemapCache();
                        log.info("网站信息更新后已清除sitemap缓存");
                    }

                    // 1.5 清除manifest.json等SEO静态文件缓存（网站名称会影响PWA安装名称）
                    if (seoStaticService != null) {
                        seoStaticService.clearStaticCache(null);
                        log.info("网站信息更新后已清除manifest.json等静态文件缓存");
                    }

                    // 2. 异步触发清理操作和预渲染
                    CompletableFuture.runAsync(() -> {
                        try {
                            log.info("开始触发预渲染");
                            prerenderFacade.rebuildSite();
                            log.info("网站信息更新后成功触发页面预渲染");
                        } catch (Exception e) {
                            log.warn("异步任务执行失败", e);
                        }
                    });

                } catch (Exception e) {
                    // 预渲染失败不影响主流程，只记录日志
                    log.warn("网站信息更新后缓存清除和页面预渲染失败", e);
                }
            } else {
                log.warn("更新后未找到网站信息数据");
            }


            return PoetryResult.success();
        } catch (Exception e) {
            log.error("更新网站信息失败", e);
            return PoetryResult.fail("更新网站信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Live2D 模型资源状态。
     * 未安装本地资源时，前端会自动使用 CDN。
     */
    @GetMapping("/live2d/assets/status")
    public PoetryResult<Map<String, Object>> getLive2dAssetStatus() {
        return PoetryResult.success(live2dAssetService.getStatus());
    }

    /**
     * 创建 Live2D 模型资源后台下载任务。
     */
    @LoginCheck(0)
    @PostMapping("/live2d/assets/install")
    @AuditLog(action = "LIVE2D_ASSETS_INSTALL", targetType = "WEB_INFO", summary = "安装Live2D资源")
    public PoetryResult<Map<String, Object>> installLive2dAssets(@RequestBody(required = false) Map<String, Object> params) {
        try {
            boolean force = params != null && Boolean.TRUE.equals(params.get("force"));
            return PoetryResult.success(live2dAssetService.install(force));
        } catch (Exception e) {
            log.error("安装 Live2D 模型资源失败", e);
            return PoetryResult.fail("安装 Live2D 模型资源失败: " + e.getMessage());
        }
    }

    /**
     * 更新公告
     */
    @LoginCheck(0)
    @PostMapping("/updateNotices")
    @AuditLog(action = "WEB_NOTICE_UPDATE", targetType = "WEB_INFO", targetIdParam = "id", summary = "更新网站通知")
    public PoetryResult<String> updateNotices(@RequestBody Map<String, Object> request) {
        try {
            Integer id = (Integer) request.get("id");
            String notices = (String) request.get("notices");

            if (id == null) {
                return PoetryResult.fail("网站信息ID不能为空");
            }

            int updateResult = webInfoMapper.updateNoticesOnly(id, notices);
            if (updateResult > 0) {
                // 更新缓存
                refreshWebInfoCache();
                log.info("公告更新成功，ID: {}", id);
                return PoetryResult.success("公告更新成功");
            } else {
                return PoetryResult.fail("公告更新失败");
            }
        } catch (Exception e) {
            log.error("更新公告失败", e);
            return PoetryResult.fail("更新公告失败: " + e.getMessage());
        }
    }

    /**
     * 更新随机名称
     */
    @LoginCheck(0)
    @PostMapping("/updateRandomName")
    @AuditLog(action = "WEB_RANDOM_NAME_UPDATE", targetType = "WEB_INFO", targetIdParam = "id", summary = "更新随机名称")
    public PoetryResult<String> updateRandomName(@RequestBody Map<String, Object> request) {
        try {
            Integer id = (Integer) request.get("id");
            String randomName = (String) request.get("randomName");

            if (id == null) {
                return PoetryResult.fail("网站信息ID不能为空");
            }

            int updateResult = webInfoMapper.updateRandomNameOnly(id, randomName);
            if (updateResult > 0) {
                // 更新缓存
                refreshWebInfoCache();
                log.info("随机名称更新成功，ID: {}", id);
                return PoetryResult.success("随机名称更新成功");
            } else {
                return PoetryResult.fail("随机名称更新失败");
            }
        } catch (Exception e) {
            log.error("更新随机名称失败", e);
            return PoetryResult.fail("更新随机名称失败: " + e.getMessage());
        }
    }

    /**
     * 更新随机头像
     */
    @LoginCheck(0)
    @PostMapping("/updateRandomAvatar")
    @AuditLog(action = "WEB_RANDOM_AVATAR_UPDATE", targetType = "WEB_INFO", targetIdParam = "id", summary = "更新随机头像")
    public PoetryResult<String> updateRandomAvatar(@RequestBody Map<String, Object> request) {
        try {
            Integer id = (Integer) request.get("id");
            String randomAvatar = (String) request.get("randomAvatar");

            if (id == null) {
                return PoetryResult.fail("网站信息ID不能为空");
            }

            int updateResult = webInfoMapper.updateRandomAvatarOnly(id, randomAvatar);
            if (updateResult > 0) {
                // 更新缓存
                refreshWebInfoCache();
                log.info("随机头像更新成功，ID: {}", id);
                return PoetryResult.success("随机头像更新成功");
            } else {
                return PoetryResult.fail("随机头像更新失败");
            }
        } catch (Exception e) {
            log.error("更新随机头像失败", e);
            return PoetryResult.fail("更新随机头像失败: " + e.getMessage());
        }
    }

    /**
     * 更新随机封面
     */
    @LoginCheck(0)
    @PostMapping("/updateRandomCover")
    @AuditLog(action = "WEB_RANDOM_COVER_UPDATE", targetType = "WEB_INFO", targetIdParam = "id", summary = "更新随机封面")
    public PoetryResult<String> updateRandomCover(@RequestBody Map<String, Object> request) {
        try {
            Integer id = (Integer) request.get("id");
            String randomCover = (String) request.get("randomCover");

            if (id == null) {
                return PoetryResult.fail("网站信息ID不能为空");
            }

            int updateResult = webInfoMapper.updateRandomCoverOnly(id, randomCover);
            if (updateResult > 0) {
                // 更新缓存
                refreshWebInfoCache();
                log.info("随机封面更新成功，ID: {}", id);
                return PoetryResult.success("随机封面更新成功");
            } else {
                return PoetryResult.fail("随机封面更新失败");
            }
        } catch (Exception e) {
            log.error("更新随机封面失败", e);
            return PoetryResult.fail("更新随机封面失败: " + e.getMessage());
        }
    }

    /**
     * 刷新网站信息缓存的通用方法
     */
    private void refreshWebInfoCache() {
        try {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                WebInfo latestWebInfo = list.get(0);
                cacheService.cacheWebInfo(latestWebInfo);
            }
        } catch (Exception e) {
            log.error("刷新网站信息缓存失败", e);
        }
    }

    /**
     * 获取网站信息
     */
    @GetMapping("/getWebInfo")
    public PoetryResult<WebInfo> getWebInfo() {
        try {
            // 直接从Redis缓存获取网站信息
            WebInfo webInfo = cacheService.getCachedWebInfo();
            if (webInfo != null) {
                WebInfo result = new WebInfo();
                BeanUtils.copyProperties(webInfo, result);

                // 清理敏感信息，不对外暴露
                result.setRandomAvatar(null);
                result.setRandomName(null);
                result.setWaifuJson(null);

                // 覆盖鼠标点击效果，使用插件系统的配置
                try {
                    String activeEffect = sysPluginService.getActiveMouseClickEffect();
                    result.setMouseClickEffect(activeEffect);
                } catch (Exception e) {
                    log.error("获取鼠标点击效果插件失败", e);
                }

                // 并行加载访问统计和文章总数
                try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                    // Fork 访问统计数据加载
                    scope.fork(() -> {
                        addHistoryStatsToWebInfo(result);
                        return null;
                    });

                    // Fork 文章总数查询
                    var articleCountTask = scope.fork(() -> {
                        Long count = new LambdaQueryChainWrapper<>(articleMapper)
                                .eq(Article::getViewStatus, true)
                                .count();
                        return count != null ? count.intValue() : 0;
                    });

                    // 等待两个任务完成
                    scope.join();

                    // 设置文章总数
                    if (articleCountTask.state() == java.util.concurrent.StructuredTaskScope.Subtask.State.SUCCESS) {
                        result.setArticleCount(articleCountTask.get());
                    } else {
                        result.setArticleCount(0);
                        log.warn("计算文章总数失败");
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("并行加载网站信息被中断", e);
                    result.setArticleCount(0);
                } catch (Exception e) {
                    log.error("并行加载网站信息失败", e);
                    result.setArticleCount(0);
                }

                return PoetryResult.success(result);
            }

            log.warn("Redis缓存中未找到网站信息");
            return PoetryResult.success();

        } catch (Exception e) {
            log.error("获取网站信息时发生错误", e);
            return PoetryResult.success();
        }
    }

    /**
     * 为WebInfo添加访问统计数据
     */
    private void addHistoryStatsToWebInfo(WebInfo result) {
        try {
            // 使用安全的缓存获取方法，内置了默认值处理
            Map<String, Object> historyStats = cacheService.getCachedIpHistoryStatisticsSafely();

            // 获取总访问量
            Long historyCount = (Long) historyStats.get(CommonConst.IP_HISTORY_COUNT);
            if (historyCount != null) {
                result.setHistoryAllCount(historyCount.toString());
            } else {
                result.setHistoryAllCount("0");
                log.warn("总访问量数据为空，使用默认值0");
            }

            // 获取今日访问量（从数据库获取）
            try {
                Long todayVisitCount = historyInfoMapper.getTodayHistoryCount();
                result.setHistoryDayCount(String.valueOf(todayVisitCount != null ? todayVisitCount : 0));
            } catch (Exception e) {
                log.warn("获取数据库今日访问量失败，使用默认值0", e);
                result.setHistoryDayCount("0");
            }

            // 检查是否需要刷新缓存
            if (Boolean.TRUE.equals(historyStats.get("_cache_refresh_needed"))) {
                log.info("检测到访问统计缓存需要刷新");
                // 可以在这里触发异步缓存刷新
            }

        } catch (Exception e) {
            // 访问统计获取失败不影响主要功能，使用默认值
            log.warn("获取访问统计时出错，使用默认值", e);
            result.setHistoryAllCount("0");
            result.setHistoryDayCount("0");
        }
    }

    /**
     * 获取用户IP地址 - 用于403页面显示
     */
    @GetMapping("/getUserIP")
    public PoetryResult<Map<String, Object>> getUserIP() {
        Map<String, Object> result = new HashMap<>();
        String clientIP = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
        result.put("ip", clientIP);
        result.put("timestamp", System.currentTimeMillis());
        return PoetryResult.success(result);
    }

    /**
     * 获取当前管理员请求IP，用于访问统计清理。
     */
    @LoginCheck(1)
    @GetMapping("/getCurrentVisitIp")
    public PoetryResult<Map<String, Object>> getCurrentVisitIp(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String currentIp = cacheService.normalizeVisitIp(PoetryUtil.getIpAddr(request));
        result.put("ip", currentIp);
        result.put("valid", isValidVisitCleanIp(currentIp));
        result.put("ignored", cacheService.isVisitIpIgnored(currentIp));
        result.put("timestamp", System.currentTimeMillis());
        return PoetryResult.success(result);
    }

    /**
     * 清理指定IP的访问统计数据，可选择同时加入忽略名单。
     */
    @LoginCheck(1)
    @PostMapping("/cleanVisitData")
    @AuditLog(action = "VISIT_DATA_CLEAN", targetType = "VISIT", targetIdParam = "targetIp", summary = "清理访问统计数据")
    public PoetryResult<Map<String, Object>> cleanVisitData(@RequestBody(required = false) Map<String, Object> params,
                                                            HttpServletRequest request) {
        try {
            Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();
            String targetIp = resolveCleanVisitIp(safeParams, request);
            if (!isValidVisitCleanIp(targetIp)) {
                return PoetryResult.fail("无法识别有效IP，请确认后重试");
            }

            boolean addToIgnore = isTruthy(safeParams.get("addToIgnore"));
            boolean wasIgnored = cacheService.isVisitIpIgnored(targetIp);

            int deletedDbCount = historyInfoMapper.deleteByIp(targetIp);
            int removedRedisCount = cacheService.removeRecentVisitRecordsByIp(targetIp, 7);

            boolean addedToIgnore = false;
            if (addToIgnore) {
                addedToIgnore = cacheService.addVisitIgnoreIp(targetIp);
            }

            cacheService.refreshLocationStatisticsCache();

            Map<String, Object> result = new HashMap<>();
            result.put("ip", targetIp);
            result.put("deletedDbCount", deletedDbCount);
            result.put("removedRedisCount", removedRedisCount);
            result.put("totalRemovedCount", deletedDbCount + removedRedisCount);
            result.put("addedToIgnore", addedToIgnore);
            result.put("alreadyIgnored", wasIgnored || (addToIgnore && !addedToIgnore));
            result.put("ignoreRequested", addToIgnore);
            result.put("cleanTime", System.currentTimeMillis());
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("清理访问统计数据失败", e);
            return PoetryResult.fail("清理访问统计数据失败: " + e.getMessage());
        }
    }

    @LoginCheck(0)
    @PostMapping("/updateThirdLoginConfig")
    @AuditLog(action = "THIRD_LOGIN_CONFIG_UPDATE", targetType = "WEB_INFO", summary = "更新第三方登录配置")
    public PoetryResult<Object> updateThirdLoginConfig(@RequestBody Map<String, Object> config) {
        try {
            log.info("更新第三方登录配置: {}", config);

            // 直接使用数据库服务更新配置
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.updateThirdLoginConfig(config);

            if (result.isSuccess()) {
                log.info("第三方登录配置更新成功");
                return PoetryResult.success("配置更新成功");
            } else {
                log.warn("第三方登录配置更新失败: {}", result.getMessage());
                return PoetryResult.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("第三方登录配置更新失败", e);
            return PoetryResult.fail("第三方登录配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取网站统计信息
     */
    @LoginCheck(0)
    @GetMapping("/getHistoryInfo")
    public PoetryResult<Map<String, Object>> getHistoryInfo() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 使用安全的缓存获取方法，内置了默认值处理
            Map<String, Object> history = cacheService.getCachedIpHistoryStatisticsSafely();

            // 检查是否需要刷新缓存
            if (Boolean.TRUE.equals(history.get("_cache_refresh_needed"))) {
                log.info("检测到缓存需要刷新，主动刷新统计数据");
                try {
                    // 主动刷新缓存
                    Map<String, Object> refreshedHistory = new HashMap<>();
                    refreshedHistory.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince());
                    refreshedHistory.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp());
                    refreshedHistory.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryByYesterday());
                    refreshedHistory.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount());

                    // 缓存新数据
                    cacheService.cacheIpHistoryStatistics(refreshedHistory);
                    history = refreshedHistory;
                    log.info("缓存刷新成功，总访问量: {}", history.get(CommonConst.IP_HISTORY_COUNT));
                } catch (Exception refreshException) {
                    log.error("主动刷新缓存失败", refreshException);
                    // 刷新失败时删除标记，避免频繁刷新
                    history.remove("_cache_refresh_needed");
                }
            }

            // 从缓存中获取历史数据（getCachedIpHistoryStatisticsSafely已确保非null）
            result.put(CommonConst.IP_HISTORY_PROVINCE, filterProvinceStatistics(history.get(CommonConst.IP_HISTORY_PROVINCE)));
            result.put(CommonConst.IP_HISTORY_IP, history.get(CommonConst.IP_HISTORY_IP));
            result.put(CommonConst.IP_HISTORY_COUNT, history.get(CommonConst.IP_HISTORY_COUNT));

            String today = java.time.LocalDate.now().toString();
            List<Map<String, Object>> unsyncedTodayRecords;
            try {
                unsyncedTodayRecords = cacheService.getUnsyncedDailyVisitRecords(today);
            } catch (Exception e) {
                log.error("获取今日未同步访问记录失败", e);
                unsyncedTodayRecords = new ArrayList<>();
            }

            try {
                result.put("ua_history_top", UserAgentClassifier.aggregateRawAndVisitRecords(
                        historyInfoMapper.getHistoryByUserAgent(),
                        unsyncedTodayRecords));
            } catch (Exception e) {
                log.error("获取总UA访问统计失败", e);
                result.put("ua_history_top", new ArrayList<>());
            }

            try {
                result.put("ua_yest", UserAgentClassifier.aggregateRawUserAgentCounts(
                        historyInfoMapper.getHistoryByUserAgentYesterday()));
            } catch (Exception e) {
                log.error("获取昨日UA访问统计失败", e);
                result.put("ua_yest", new ArrayList<>());
            }

            try {
                result.put("article_history_top", buildArticleVisitStats(
                        historyInfoMapper.getHistoryByArticlePageUri(),
                        unsyncedTodayRecords));
            } catch (Exception e) {
                log.error("获取文章页面总访问统计失败", e);
                result.put("article_history_top", new ArrayList<>());
            }

            try {
                result.put("article_yest", buildArticleVisitStats(
                        historyInfoMapper.getHistoryByArticlePageUriYesterday(),
                        null));
            } catch (Exception e) {
                log.error("获取昨日文章页面访问统计失败", e);
                result.put("article_yest", new ArrayList<>());
            }

            // 处理24小时数据（昨日数据）
            List<Map<String, Object>> ipHistoryCount = (List<Map<String, Object>>) history
                    .get(CommonConst.IP_HISTORY_HOUR);

            if (ipHistoryCount != null && !ipHistoryCount.isEmpty()) {
                result.put("ip_count_yest", ipHistoryCount.stream()
                        .map(m -> m != null ? m.get("ip") : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count());
            } else {
                result.put("ip_count_yest", 0L);
            }
            // 安全地处理昨日用户信息（添加访问次数统计）
            if (ipHistoryCount != null && !ipHistoryCount.isEmpty()) {
                // 统计每个用户的访问次数
                Map<Integer, Long> userVisitCount = ipHistoryCount.stream()
                        .filter(Objects::nonNull)
                        .map(m -> {
                            try {
                                Object userId = m.get("user_id");
                                if (userId != null) {
                                    return Integer.valueOf(userId.toString());
                                }
                            } catch (Exception e) {
                                log.warn("处理昨日用户ID时出错: {}", e.getMessage());
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(
                                userId -> userId,
                                Collectors.counting()));

                List<Map<String, Object>> usernameYest = userVisitCount.entrySet().stream()
                        .map(entry -> {
                            try {
                                Integer userId = entry.getKey();
                                Long visitCount = entry.getValue();
                                User user = commonQuery.getUser(userId);
                                if (user != null) {
                                    Map<String, Object> userInfo = new HashMap<>();
                                    userInfo.put("avatar", user.getAvatar());
                                    userInfo.put("username", user.getUsername());
                                    userInfo.put("visitCount", visitCount);
                                    return userInfo;
                                }
                            } catch (Exception e) {
                                log.warn("处理昨日用户信息时出错: {}", e.getMessage());
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .sorted((o1, o2) -> Long.valueOf(o2.get("visitCount").toString())
                                .compareTo(Long.valueOf(o1.get("visitCount").toString()))) // 按访问次数降序排列
                        .collect(Collectors.toList());
                result.put("username_yest", usernameYest);
            } else {
                result.put("username_yest", new ArrayList<>());
            }

            // 获取今日访问数据的实时统计（从Redis）
            try {
                Map<String, Object> todayStats = cacheService.getTodayVisitStatisticsFromRedis();

                // 设置今日IP数量
                result.put("ip_count_today", todayStats.get("ip_count_today"));

                // 处理今日登录用户信息（补充用户详细信息）
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> userInfos = (List<Map<String, Object>>) todayStats.get("username_today");
                List<Map<String, Object>> usernameToday = userInfos.stream()
                        .map(userInfoMap -> {
                            try {
                                String userId = (String) userInfoMap.get("userId");
                                Long visitCount = (Long) userInfoMap.get("visitCount");
                                if (userId != null) {
                                    User user = commonQuery.getUser(Integer.valueOf(userId));
                                    if (user != null) {
                                        Map<String, Object> userInfo = new HashMap<>();
                                        userInfo.put("avatar", user.getAvatar());
                                        userInfo.put("username", user.getUsername());
                                        userInfo.put("visitCount", visitCount);
                                        return userInfo;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("处理今日用户信息时出错: {}", e.getMessage());
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                result.put("username_today", usernameToday);

                // 设置今日省份统计
                result.put("province_today", todayStats.get("province_today"));
                result.put("ua_today", todayStats.get("ua_today"));
                result.put("article_today", buildArticleVisitStats(null, cacheService.getDailyVisitRecords(today)));

            } catch (Exception e) {
                log.error("从Redis获取今日访问统计失败，使用默认值", e);
                result.put("ip_count_today", 0L);
                result.put("username_today", new ArrayList<>());
                result.put("province_today", new ArrayList<>());
                result.put("ua_today", new ArrayList<>());
                result.put("article_today", new ArrayList<>());
            }

            // 统计各种总数
            try {
                result.put("user_count", userMapper.selectCount(null));
                result.put("article_count", articleMapper.selectCount(null));
                result.put("sort_count", sortMapper.selectCount(null));
                result.put("resource_count", resourceMapper.selectCount(null));
                result.put("comment_count", commentMapper.selectCount(null));
                result.put("tree_hole_count", treeHoleMapper.selectCount(null));
                result.put("love_count", familyMapper.selectCount(null));
            } catch (Exception e) {
                log.error("获取统计数据失败", e);
            }

            return PoetryResult.success(result);

        } catch (Exception e) {
            log.error("获取历史统计信息时发生错误", e);
            // 返回默认的空数据，避免前端报错
            Map<String, Object> defaultResult = createDefaultHistoryResult();
            return PoetryResult.success(defaultResult);
        }
    }

    /**
     * 创建默认的历史结果数据
     */
    private Map<String, Object> createDefaultHistoryResult() {
        Map<String, Object> defaultResult = new HashMap<>();
        defaultResult.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
        defaultResult.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
        defaultResult.put(CommonConst.IP_HISTORY_COUNT, 0L);
        defaultResult.put("ip_count_yest", 0L);
        defaultResult.put("username_yest", new ArrayList<>());
        defaultResult.put("ip_count_today", 0L);
        defaultResult.put("username_today", new ArrayList<>());
        defaultResult.put("province_today", new ArrayList<>());
        defaultResult.put("ua_history_top", new ArrayList<>());
        defaultResult.put("ua_today", new ArrayList<>());
        defaultResult.put("ua_yest", new ArrayList<>());
        defaultResult.put("article_history_top", new ArrayList<>());
        defaultResult.put("article_today", new ArrayList<>());
        defaultResult.put("article_yest", new ArrayList<>());

        log.info("返回默认历史统计结果");
        return defaultResult;
    }

    private List<Map<String, Object>> buildArticleVisitStats(List<Map<String, Object>> rawPageRows,
                                                             List<Map<String, Object>> visitRecords) {
        Map<String, ArticleVisitBucket> buckets = new HashMap<>();
        addRawArticlePageCounts(buckets, rawPageRows);
        addArticleVisitRecords(buckets, visitRecords);

        if (buckets.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Article> articlesByToken = loadArticlesByToken(buckets.keySet());
        return buckets.values().stream()
                .sorted((a, b) -> Long.compare(b.num, a.num))
                .limit(10)
                .map(bucket -> toArticleVisitRow(bucket, articlesByToken.get(bucket.token)))
                .collect(Collectors.toList());
    }

    private void addRawArticlePageCounts(Map<String, ArticleVisitBucket> buckets, List<Map<String, Object>> rawPageRows) {
        if (rawPageRows == null || rawPageRows.isEmpty()) {
            return;
        }

        for (Map<String, Object> row : rawPageRows) {
            if (row == null) continue;
            Object pageUriObj = row.get("page_uri");
            if (pageUriObj == null) {
                pageUriObj = row.get("pageUri");
            }
            long count = toLong(row.get("num"));
            if (pageUriObj != null && count > 0) {
                mergeArticleVisitBucket(buckets, pageUriObj.toString(), count);
            }
        }
    }

    private void addArticleVisitRecords(Map<String, ArticleVisitBucket> buckets, List<Map<String, Object>> visitRecords) {
        if (visitRecords == null || visitRecords.isEmpty()) {
            return;
        }

        for (Map<String, Object> record : visitRecords) {
            if (record == null) continue;
            Object pageUriObj = record.get("pageUri");
            if (pageUriObj == null) {
                pageUriObj = record.get("page_uri");
            }
            if (pageUriObj != null) {
                mergeArticleVisitBucket(buckets, pageUriObj.toString(), 1L);
            }
        }
    }

    private void mergeArticleVisitBucket(Map<String, ArticleVisitBucket> buckets, String pageUri, long count) {
        String token = PageVisitUtils.extractArticleToken(pageUri);
        if (token == null) {
            return;
        }

        String path = PageVisitUtils.extractPath(pageUri);
        ArticleVisitBucket bucket = buckets.computeIfAbsent(token, ignored -> new ArticleVisitBucket(token, path));
        bucket.num += count;
    }

    private Map<String, Article> loadArticlesByToken(Set<String> tokens) {
        Map<String, Article> articleMap = new HashMap<>();
        if (tokens == null || tokens.isEmpty()) {
            return articleMap;
        }

        List<Integer> ids = tokens.stream()
                .filter(this::isPositiveInteger)
                .map(Integer::valueOf)
                .collect(Collectors.toList());
        if (!ids.isEmpty()) {
            try {
                List<Article> articles = new LambdaQueryChainWrapper<>(articleMapper)
                        .select(Article::getId, Article::getArticleTitle, Article::getArticleSlug, Article::getDeleted)
                        .in(Article::getId, ids)
                        .eq(Article::getDeleted, false)
                        .list();
                for (Article article : articles) {
                    if (article == null) continue;
                    articleMap.put(String.valueOf(article.getId()), article);
                    if (StringUtils.hasText(article.getArticleSlug())) {
                        articleMap.put(article.getArticleSlug(), article);
                    }
                }
            } catch (Exception e) {
                log.warn("按ID加载文章访问统计标题失败: {}", e.getMessage());
            }
        }

        List<String> slugs = tokens.stream()
                .filter(token -> !isPositiveInteger(token))
                .collect(Collectors.toList());
        if (!slugs.isEmpty()) {
            try {
                List<Article> articles = new LambdaQueryChainWrapper<>(articleMapper)
                        .select(Article::getId, Article::getArticleTitle, Article::getArticleSlug, Article::getDeleted)
                        .in(Article::getArticleSlug, slugs)
                        .eq(Article::getDeleted, false)
                        .list();
                for (Article article : articles) {
                    if (article != null && StringUtils.hasText(article.getArticleSlug())) {
                        articleMap.put(article.getArticleSlug(), article);
                    }
                }
            } catch (Exception e) {
                log.warn("按别名加载文章访问统计标题失败: {}", e.getMessage());
            }
        }

        return articleMap;
    }

    private Map<String, Object> toArticleVisitRow(ArticleVisitBucket bucket, Article article) {
        Map<String, Object> row = new HashMap<>();
        row.put("article_token", bucket.token);
        row.put("article_path", bucket.samplePath);
        row.put("num", bucket.num);
        if (article != null) {
            row.put("article_id", article.getId());
            row.put("article_title", StringUtils.hasText(article.getArticleTitle())
                    ? article.getArticleTitle()
                    : bucket.token);
        } else {
            row.put("article_title", bucket.token);
        }
        return row;
    }

    private long toLong(Object value) {
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

    private boolean isPositiveInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static final class ArticleVisitBucket {
        private final String token;
        private final String samplePath;
        private long num;

        private ArticleVisitBucket(String token, String samplePath) {
            this.token = token;
            this.samplePath = samplePath;
        }
    }

    /**
     * 获取赞赏
     */
    @GetMapping("/getAdmire")
    public PoetryResult<List<User>> getAdmire() {
        return PoetryResult.success(commonQuery.getAdmire());
    }

    /**
     * 获取看板娘状态
     * 替代Python端的getWaifuStatus端点，统一架构设计
     */
    @GetMapping("/getWaifuStatus")
    public PoetryResult<Map<String, Object>> getWaifuStatus() {
        try {

            // 从缓存获取网站信息以保持性能
            WebInfo webInfo = cacheService.getCachedWebInfo();

            if (webInfo != null) {
                Boolean enableWaifu = webInfo.getEnableWaifu();
                if (enableWaifu == null) {
                    enableWaifu = false;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("enableWaifu", enableWaifu);
                data.put("id", webInfo.getId());

                return PoetryResult.success(data);
            } else {
                log.warn("网站信息不存在");
                return PoetryResult.fail("网站信息不存在");
            }
        } catch (Exception e) {
            log.error("获取看板娘状态失败", e);
            return PoetryResult.fail("获取看板娘状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取看板娘消息
     */
    @GetMapping("/getWaifuJson")
    public String getWaifuJson() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo != null && StringUtils.hasText(webInfo.getWaifuJson())) {
            return webInfo.getWaifuJson();
        }
        return "{}";
    }

    /**
     * 清除分类信息缓存（已禁用）
     */
    @GetMapping("/clearSortCache")
    public PoetryResult<String> clearSortCache() {
        // 分类缓存功能已移除，直接返回成功
        return PoetryResult.success("分类缓存功能已禁用");
    }

    /**
     * 获取API配置
     */
    @LoginCheck(0)
    @GetMapping("/getApiConfig")
    public PoetryResult<Map<String, Object>> getApiConfig() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
                cacheService.cacheWebInfo(webInfo);
            } else {
                webInfo = new WebInfo();
            }
        }

        Map<String, Object> apiConfig = new HashMap<>();
        apiConfig.put("enabled", webInfo.getApiEnabled() != null ? webInfo.getApiEnabled() : false);
        apiConfig.put("apiKey", webInfo.getApiKey() != null ? webInfo.getApiKey() : generateApiKey());
        apiConfig.put("ipWhitelist", webInfo.getApiIpWhitelist() != null ? webInfo.getApiIpWhitelist() : "");
        apiConfig.put("currentIp", PoetryUtil.getIpAddr(PoetryUtil.getRequest()));

        return PoetryResult.success(apiConfig);
    }

    /**
     * 保存API配置
     */
    @LoginCheck(0)
    @PostMapping("/saveApiConfig")
    @AuditLog(action = "API_CONFIG_SAVE", targetType = "WEB_INFO", summary = "保存接口配置")
    public PoetryResult<String> saveApiConfig(@RequestBody Map<String, Object> apiConfig) {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }

        Boolean enabled = (Boolean) apiConfig.get("enabled");
        String apiKey = (String) apiConfig.get("apiKey");
        String rawIpWhitelist = apiConfig.get("ipWhitelist") instanceof String ? (String) apiConfig.get("ipWhitelist") : "";

        // 如果提交的配置不包含apiKey，生成一个新的
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = generateApiKey();
        }

        List<String> whitelistEntries = IpUtil.normalizeIpWhitelist(rawIpWhitelist);
        for (String entry : whitelistEntries) {
            if (!IpUtil.isValidIpWhitelistEntry(entry)) {
                return PoetryResult.fail("IP白名单格式错误: " + entry + "。支持单个IP或CIDR网段，例如 203.0.113.10 或 203.0.113.0/24");
            }
        }
        String normalizedIpWhitelist = String.join("\n", whitelistEntries);

        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiEnabled(enabled);
        updateInfo.setApiKey(apiKey);
        updateInfo.setApiIpWhitelist(normalizedIpWhitelist);
        webInfoService.updateById(updateInfo);

        // 清理Redis缓存并重新缓存最新数据
        cacheService.evictWebInfo();
        webInfo.setApiEnabled(enabled);
        webInfo.setApiKey(apiKey);
        webInfo.setApiIpWhitelist(normalizedIpWhitelist);
        cacheService.cacheWebInfo(webInfo);
        log.info("API配置更新成功，已刷新Redis缓存");

        return PoetryResult.success();
    }

    /**
     * 重新生成API密钥
     */
    @LoginCheck(0)
    @PostMapping("/regenerateApiKey")
    @AuditLog(action = "API_KEY_REGENERATE", targetType = "WEB_INFO", summary = "重新生成接口密钥")
    public PoetryResult<String> regenerateApiKey() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }

        String newApiKey = generateApiKey();

        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiKey(newApiKey);
        webInfoService.updateById(updateInfo);

        // 更新缓存
        webInfo.setApiKey(newApiKey);
        cacheService.cacheWebInfo(webInfo);

        return PoetryResult.success(newApiKey);
    }

    /**
     * 生成API密钥
     */
    private String generateApiKey() {
        return UUID.randomUUID().toString().replaceAll("-", "") +
                UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
    }

    /**
     * 获取每日访问量统计（结合数据库历史数据和Redis实时数据）
     * 
     * @param days 查询天数(1-365)，默认7
     */
    @LoginCheck(0)
    @GetMapping("/getDailyVisitStats")
    public PoetryResult<List<Map<String, Object>>> getDailyVisitStats(
            @RequestParam(value = "days", defaultValue = "7") Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        } else if (days > 365) {
            days = 365;
        }

        try {
            // 1. 获取数据库中的历史数据（不包括今天）
            List<Map<String, Object>> dbStats = historyInfoMapper.getDailyVisitStatsExcludeToday(days);
            if (dbStats == null) {
                dbStats = new ArrayList<>();
            }

            // 2. 获取Redis中今天的实时数据
            Map<String, Object> todayStats = getTodayVisitStatsFromRedis();

            // 3. 合并数据
            List<Map<String, Object>> allStats = new ArrayList<>(dbStats);
            if (todayStats != null) {
                allStats.add(todayStats);
            }

            // 4. 填充缺失的日期（确保图表连续）
            List<Map<String, Object>> completeStats = fillMissingDates(allStats, days);

            // 5. 计算平均值
            if (!completeStats.isEmpty()) {
                double avg = completeStats.stream()
                        .map(m -> (Number) m.get("unique_visits"))
                        .filter(Objects::nonNull)
                        .mapToDouble(Number::doubleValue)
                        .average()
                        .orElse(0);
                avg = Math.round(avg * 100.0) / 100.0;

                for (Map<String, Object> m : completeStats) {
                    m.put("avg_unique_visits", avg);
                }
            }

            return PoetryResult.success(completeStats);

        } catch (Exception e) {
            log.error("获取每日访问统计失败", e);
            return PoetryResult.fail("获取访问统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 从Redis获取今天的访问统计
     */
    private Map<String, Object> getTodayVisitStatsFromRedis() {
        try {
            String todayKey = CacheConstants.DAILY_VISIT_RECORDS_PREFIX +
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 获取今天的访问记录
            List<Object> todayRecords = redisTemplate.opsForList().range(todayKey, 0, -1);
            if (todayRecords == null || todayRecords.isEmpty()) {
                return null;
            }

            // 统计今日数据
            Set<String> uniqueIps = new HashSet<>();
            int totalVisits = 0;

            for (Object record : todayRecords) {
                try {
                    // 将JSON字符串解析为Map对象
                    Map<String, Object> visitRecord = JSON.parseObject(record.toString(), Map.class);
                    String ip = (String) visitRecord.get("ip");
                    if (ip != null && !ip.isEmpty()) {
                        uniqueIps.add(ip);
                        totalVisits++;
                    }
                } catch (Exception e) {
                    log.warn("解析Redis访问记录失败: {}", record, e);
                }
            }

            Map<String, Object> todayStats = new HashMap<>();
            todayStats.put("visit_date", java.time.LocalDate.now().toString());
            todayStats.put("unique_visits", uniqueIps.size());
            todayStats.put("total_visits", totalVisits);

            log.info("今日实时统计 - 独立访客: {}, 总访问量: {}", uniqueIps.size(), totalVisits);
            return todayStats;

        } catch (Exception e) {
            log.error("从Redis获取今日访问统计失败", e);
            return null;
        }
    }

    /**
     * 填充缺失的日期，确保图表数据连续
     */
    private List<Map<String, Object>> fillMissingDates(List<Map<String, Object>> stats, int days) {
        Map<String, Map<String, Object>> statsMap = new HashMap<>();

        // 将现有数据放入Map中
        for (Map<String, Object> stat : stats) {
            String date = (String) stat.get("visit_date");
            if (date != null) {
                statsMap.put(date, stat);
            }
        }

        // 生成完整的日期范围
        List<Map<String, Object>> completeStats = new ArrayList<>();
        java.time.LocalDate endDate = java.time.LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            java.time.LocalDate date = endDate.minusDays(i);
            String dateStr = date.toString();

            Map<String, Object> dayStats = statsMap.get(dateStr);
            if (dayStats == null) {
                // 创建空数据
                dayStats = new HashMap<>();
                dayStats.put("visit_date", dateStr);
                dayStats.put("unique_visits", 0);
                dayStats.put("total_visits", 0);
            }

            completeStats.add(dayStats);
        }

        return completeStats;
    }

    /**
     * 手动刷新访问统计缓存（管理员专用）
     * 同步Redis访问记录到数据库，并重新生成统计数据
     */
    @LoginCheck(1)
    @PostMapping("/refreshHistoryCache")
    @AuditLog(action = "VISIT_CACHE_REFRESH", targetType = "VISIT", summary = "刷新访问统计缓存")
    public PoetryResult<Map<String, Object>> refreshHistoryCache() {
        try {
            log.info("管理员手动刷新访问统计缓存");

            // 1. 先同步当前Redis中的访问记录到数据库
            syncCurrentRedisRecordsToDatabase();

            // 重新构建统计数据（仅基于数据库数据，无Redis实时计数）
            cacheService.refreshLocationStatisticsCache();

            // 获取刷新后的统计数据用于返回
            Object cachedStats = cacheService.getCachedIpHistoryStatistics();
            Map<String, Object> statistics = (Map<String, Object>) cachedStats;

            // 返回统计结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            if (statistics != null) {
                Object totalCountObj = statistics.get(CommonConst.IP_HISTORY_COUNT);
                Object provincesObj = statistics.get(CommonConst.IP_HISTORY_PROVINCE);
                Object ipsObj = statistics.get(CommonConst.IP_HISTORY_IP);
                Object hoursObj = statistics.get(CommonConst.IP_HISTORY_HOUR);

                result.put("totalCount", totalCountObj instanceof Number ? ((Number) totalCountObj).longValue() : 0L);
                result.put("provinceCount", provincesObj instanceof List ? ((List<?>) provincesObj).size() : 0);
                result.put("ipCount", ipsObj instanceof List ? ((List<?>) ipsObj).size() : 0);
                result.put("hourCount", hoursObj instanceof List ? ((List<?>) hoursObj).size() : 0);
            } else {
                result.put("totalCount", 0L);
                result.put("provinceCount", 0);
                result.put("ipCount", 0);
                result.put("hourCount", 0);
            }
            result.put("refreshTime", System.currentTimeMillis());

            log.info("访问统计缓存刷新完成");
            return PoetryResult.success(result);

        } catch (Exception e) {
            log.error("手动刷新访问统计缓存失败", e);
            return PoetryResult.fail("刷新失败: " + e.getMessage());
        }
    }

    @LoginCheck(0)
    @GetMapping("/getThirdLoginConfig")
    public PoetryResult<Object> getThirdLoginConfig() {
        try {
            log.info("获取第三方登录配置");

            // 直接从数据库获取配置
            PoetryResult<Map<String, Object>> result = thirdPartyOauthConfigService.getThirdLoginConfig();

            if (result.isSuccess()) {
                log.info("第三方登录配置获取成功");
                return PoetryResult.success(result.getData());
            } else {
                log.warn("第三方登录配置获取失败: {}", result.getMessage());
                return PoetryResult.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("获取第三方登录配置失败", e);
            return PoetryResult.fail("获取第三方登录配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取第三方登录状态（轻量级接口，用于前端状态检查）
     * 使用结构化并发并行检查所有平台状态
     */
    @GetMapping("/getThirdLoginStatus")
    public PoetryResult<Object> getThirdLoginStatus(@RequestParam(required = false) String provider) {
        try {

            // 获取所有配置
            List<ThirdPartyOauthConfig> allConfigs = thirdPartyOauthConfigService.getAllConfigs();

            // 使用并行方法检查所有平台状态
            Map<String, Boolean> platformsStatus = thirdPartyOauthConfigService.getAllPlatformsStatus();

            // 构建状态响应
            Map<String, Object> status = new HashMap<>();

            // 检查是否有任何平台可用
            boolean globalEnabled = platformsStatus.values().stream().anyMatch(Boolean::booleanValue);
            status.put("enable", globalEnabled);

            // 如果指定了平台，检查该平台状态
            if (provider != null && !provider.trim().isEmpty()) {
                Boolean platformEnabled = platformsStatus.getOrDefault(provider, false);
                status.put(provider, Map.of("enabled", platformEnabled));
            } else {
                // 返回所有平台状态（包括未启用的）
                for (ThirdPartyOauthConfig config : allConfigs) {
                    Map<String, Object> platformStatus = new HashMap<>();

                    // 使用并行检查的结果
                    Boolean enabled = platformsStatus.getOrDefault(config.getPlatformType(), false);
                    platformStatus.put("enabled", enabled);

                    // 添加平台基本信息
                    platformStatus.put("platformName", config.getPlatformName());
                    platformStatus.put("sortOrder", config.getSortOrder());

                    status.put(config.getPlatformType(), platformStatus);
                }
            }

            return PoetryResult.success(status);
        } catch (Exception e) {
            log.error("获取第三方登录状态失败", e);
            return PoetryResult.fail("获取第三方登录状态失败: " + e.getMessage());
        }
    }

    private String resolveCleanVisitIp(Map<String, Object> params, HttpServletRequest request) {
        boolean useCurrentIp = isTruthy(params.get("useCurrentIp"));
        Object rawIp = useCurrentIp ? PoetryUtil.getIpAddr(request) : params.get("ip");
        return cacheService.normalizeVisitIp(rawIp != null ? rawIp.toString() : "");
    }

    private boolean isValidVisitCleanIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        String normalizedIp = ip.trim();
        return !"unknown".equalsIgnoreCase(normalizedIp)
                && !"null".equalsIgnoreCase(normalizedIp)
                && !"undefined".equalsIgnoreCase(normalizedIp)
                && !"-".equals(normalizedIp)
                && !"localhost".equalsIgnoreCase(normalizedIp)
                && IpUtil.isValidIpFormat(normalizedIp);
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private List<Map<String, Object>> filterProvinceStatistics(Object provinceStats) {
        if (!(provinceStats instanceof List<?> rawList)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> filteredStats = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object province = rawMap.get("province");
            if (!isValidProvinceName(province)) {
                continue;
            }
            Map<String, Object> stat = new HashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    stat.put(key.toString(), value);
                }
            });
            filteredStats.add(stat);
        }
        return filteredStats;
    }

    private boolean isValidProvinceName(Object province) {
        if (province == null) {
            return false;
        }
        String normalizedProvince = province.toString().trim();
        return StringUtils.hasText(normalizedProvince)
                && !"0".equals(normalizedProvince)
                && !"未知".equals(normalizedProvince)
                && !"unknown".equalsIgnoreCase(normalizedProvince)
                && !"reserved".equalsIgnoreCase(normalizedProvince)
                && !"null".equalsIgnoreCase(normalizedProvince)
                && !"undefined".equalsIgnoreCase(normalizedProvince)
                && !"-".equals(normalizedProvince);
    }

    /**
     * 同步当前Redis中的访问记录到数据库（手动刷新时调用）
     */
    private void syncCurrentRedisRecordsToDatabase() {
        try {
            String today = java.time.LocalDate.now().toString();
            log.info("开始同步{}的Redis访问记录到数据库", today);

            // 获取今天的未同步访问记录
            List<Map<String, Object>> visitRecords = cacheService.getUnsyncedDailyVisitRecords(today);

            if (visitRecords.isEmpty()) {
                log.info("{}没有未同步的Redis访问记录需要同步", today);
                return;
            }

            int successCount = 0;
            int failCount = 0;
            List<Map<String, Object>> successfullyInsertedRecords = new ArrayList<>();

            // 批量插入访问记录到数据库
            for (Map<String, Object> record : visitRecords) {
                try {
                    com.ld.poetry.entity.HistoryInfo historyInfo = new com.ld.poetry.entity.HistoryInfo();
                    historyInfo.setIp((String) record.get("ip"));

                    Object userIdObj = record.get("userId");
                    if (userIdObj != null) {
                        historyInfo.setUserId(Integer.valueOf(userIdObj.toString()));
                    }

                    historyInfo.setNation((String) record.get("nation"));
                    historyInfo.setProvince((String) record.get("province"));
                    historyInfo.setCity((String) record.get("city"));
                    Object pageUriObj = record.get("pageUri");
                    if (pageUriObj != null) {
                        historyInfo.setPageUri(pageUriObj.toString());
                    }
                    Object userAgentObj = record.get("userAgent");
                    if (userAgentObj != null) {
                        historyInfo.setUserAgent(userAgentObj.toString());
                    }

                    // 设置创建时间
                    String createTimeStr = (String) record.get("createTime");
                    if (createTimeStr != null) {
                        // 使用与CacheService相同的日期格式 yyyy-MM-dd HH:mm:ss
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd HH:mm:ss");
                        historyInfo.setCreateTime(java.time.LocalDateTime.parse(createTimeStr, formatter));
                    } else {
                        historyInfo.setCreateTime(java.time.LocalDateTime.now());
                    }

                    // 插入数据库
                    historyInfoMapper.insert(historyInfo);
                    successCount++;

                    // 记录成功插入的记录，用于后续标记
                    successfullyInsertedRecords.add(record);

                } catch (Exception e) {
                    log.error("插入访问记录失败: {}", record, e);
                    failCount++;
                }
            }

            log.info("{}的Redis访问记录同步完成: 成功{}, 失败{}", today, successCount, failCount);

            // 标记成功同步的记录，而不是清空整个缓存
            if (successCount > 0) {
                cacheService.markVisitRecordsAsSynced(today, successfullyInsertedRecords);
                log.info("已标记{}的{}条Redis访问记录为已同步", today, successCount);
            }

        } catch (Exception e) {
            log.error("同步Redis访问记录到数据库失败", e);
        }
    }
}
