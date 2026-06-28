package com.ld.poetry.service;
import com.ld.poetry.utils.JsonUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.SysConfigMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.SysConfig;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.utils.RedisUtil;
import com.ld.poetry.utils.PageVisitUtils;
import com.ld.poetry.utils.SpringContextUtil;
import com.ld.poetry.utils.TokenValidationUtil;
import com.ld.poetry.utils.UserAgentClassifier;
import com.ld.poetry.utils.VisitRegionNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 缓存服务类
 * 统一管理各种业务数据的缓存操作
 * 
 * @author LeapYa
 * @since 2025-7-20
 */
@Service
@Slf4j
public class CacheService {
    private static final String VISIT_IGNORE_IP_CONFIG_KEY = "visit.ignore.ips";
    private static final String VISIT_IGNORE_IP_CONFIG_TYPE = "1";
    private static final int DAILY_VISIT_RECORD_RETENTION_DAYS = 7;
    private static final DateTimeFormatter VISIT_RECORD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private RedisUtil redisUtil;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    // ================================ 用户缓存 ================================

    /**
     * 缓存用户信息
     */
    public void cacheUser(User user) {
        if (user != null && user.getId() != null) {
            String key = CacheConstants.buildUserKey(user.getId());
            // 使用与token相同的过期时间，确保用户信息和会话同步
            redisUtil.set(key, user, CommonConst.TOKEN_EXPIRE);
            log.info("缓存用户信息: {}, 过期时间与token一致: {}秒", user.getId(), CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 获取缓存的用户信息
     */
    public User getCachedUser(Integer userId) {
        if (userId == null) return null;
        
        String key = CacheConstants.buildUserKey(userId);
        Object cached = redisUtil.get(key);
        if (cached instanceof User) {
            return (User) cached;
        }
        return null;
    }

    /**
     * 删除用户缓存
     */
    public void evictUser(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.buildUserKey(userId);
            redisUtil.del(key);
        }
    }

    // ================================ 文章缓存 ================================

    /**
     * 缓存文章信息
     */
    public void cacheArticle(Article article) {
        if (article != null && article.getId() != null) {
            String key = CacheConstants.buildArticleKey(article.getId());
            redisUtil.set(key, article, CacheConstants.LONG_EXPIRE_TIME);
        }
    }

    /**
     * 获取缓存的文章信息
     */
    public Article getCachedArticle(Integer articleId) {
        if (articleId == null) return null;
        
        String key = CacheConstants.buildArticleKey(articleId);
        Object cached = redisUtil.get(key);
        if (cached instanceof Article) {
            return (Article) cached;
        }
        return null;
    }

    /**
     * 删除文章缓存
     */
    public void evictArticle(Integer articleId) {
        if (articleId != null) {
            String key = CacheConstants.buildArticleKey(articleId);
            redisUtil.del(key);
        }
    }

    /**
     * 缓存文章列表
     */
    public void cacheArticleList(Integer sortId, Integer page, Integer size, List<Article> articles) {
        String key = CacheConstants.buildArticleListKey(sortId, page, size);
        redisUtil.set(key, articles, CacheConstants.DEFAULT_EXPIRE_TIME);
    }

    /**
     * 获取缓存的文章列表
     */
    @SuppressWarnings("unchecked")
    public List<Article> getCachedArticleList(Integer sortId, Integer page, Integer size) {
        String key = CacheConstants.buildArticleListKey(sortId, page, size);
        Object cached = redisUtil.get(key);
        if (cached instanceof List) {
            return (List<Article>) cached;
        }
        return null;
    }

    /**
     * 增加文章浏览量
     */
    public long incrementArticleView(Integer articleId) {
        if (articleId == null) return 0;
        
        String key = CacheConstants.ARTICLE_VIEW_PREFIX + articleId;
        return redisUtil.incr(key, 1);
    }

    /**
     * 获取文章浏览量
     */
    public long getArticleViewCount(Integer articleId) {
        if (articleId == null) return 0;

        String key = CacheConstants.ARTICLE_VIEW_PREFIX + articleId;
        Object count = redisUtil.get(key);
        if (count instanceof Number) {
            return ((Number) count).longValue();
        }
        return 0;
    }

    /**
     * 缓存分类文章列表
     */
    public void cacheSortArticleList(Map<Integer, List<Article>> sortArticleMap) {
        if (sortArticleMap != null) {
            redisUtil.set(CacheConstants.SORT_ARTICLE_LIST_KEY, sortArticleMap, CacheConstants.LONG_EXPIRE_TIME);
        }
    }

    /**
     * 获取缓存的分类文章列表
     * 处理Redis序列化导致的类型转换问题
     */
    @SuppressWarnings("unchecked")
    public Map<Integer, List<Article>> getCachedSortArticleList() {
        Object cached = redisUtil.get(CacheConstants.SORT_ARTICLE_LIST_KEY);
        if (cached instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) cached;
            Map<Integer, List<Article>> result = new HashMap<>();

            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                Integer sortId = convertToInteger(entry.getKey());
                if (sortId != null && entry.getValue() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Article> articles = (List<Article>) entry.getValue();
                    result.put(sortId, articles);
                } else {
                    log.warn("缓存数据类型异常 - sortId: {}, value类型: {}",
                            entry.getKey(),
                            entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
                }
            }
            return result;
        }
        return null;
    }

    /**
     * 安全地将对象转换为Integer类型
     * 处理Redis序列化导致的类型转换问题
     * @param obj 要转换的对象
     * @return 转换后的Integer，转换失败返回null
     */
    private Integer convertToInteger(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Integer) {
            return (Integer) obj;
        }

        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }

        if (obj instanceof String) {
            try {
                return Integer.valueOf((String) obj);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Integer: {}", obj);
                return null;
            }
        }

        log.warn("不支持的类型转换: {} -> Integer", obj.getClass().getSimpleName());
        return null;
    }

    /**
     * 删除分类文章列表缓存
     */
    public void evictSortArticleList() {
        redisUtil.del(CacheConstants.SORT_ARTICLE_LIST_KEY);
    }



    /**
     * 删除文章相关的所有缓存
     */
    public void evictArticleRelatedCache(Integer articleId) {
        if (articleId != null) {
            // 删除文章详情缓存
            evictArticle(articleId);
            // 删除文章列表缓存（模糊删除）
            // 注意：这里简化处理，实际可以使用Redis的SCAN命令
            evictSortArticleList();
        }
    }

    // ================================ 二维码缓存 ================================

    /**
     * 缓存文章二维码
     * 
     * @param articleId 文章ID
     * @param qrCodeData 二维码字节数组
     */
    public void cacheArticleQRCode(Integer articleId, byte[] qrCodeData) {
        if (articleId != null && qrCodeData != null && qrCodeData.length > 0) {
            String key = CacheConstants.buildArticleQRCodeKey(articleId);
            redisUtil.set(key, qrCodeData, CacheConstants.QRCODE_EXPIRE_TIME);
        }
    }

    /**
     * 获取缓存的文章二维码
     * 
     * @param articleId 文章ID
     * @return 二维码字节数组，如果不存在返回null
     */
    public byte[] getCachedArticleQRCode(Integer articleId) {
        if (articleId == null) return null;
        
        String key = CacheConstants.buildArticleQRCodeKey(articleId);
        Object cached = redisUtil.get(key);
        if (cached instanceof byte[]) {
            return (byte[]) cached;
        }
        return null;
    }

    /**
     * 删除文章二维码缓存
     * 
     * @param articleId 文章ID
     */
    public void evictArticleQRCode(Integer articleId) {
        if (articleId != null) {
            String key = CacheConstants.buildArticleQRCodeKey(articleId);
            redisUtil.del(key);
        }
    }

    // ================================ 系统配置缓存 ================================

    /**
     * 缓存网站信息
     */
    public void cacheWebInfo(WebInfo webInfo) {
        if (webInfo != null) {
            // 使用PERMANENT_EXPIRE_TIME常量（值为0）表示永久缓存
            redisUtil.set(CacheConstants.WEB_INFO_KEY, webInfo, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存网站信息成功(永久缓存) - Key: {}, webName: {}, webTitle: {}",
                    CacheConstants.WEB_INFO_KEY, webInfo.getWebName(), webInfo.getWebTitle());
        } else {
            log.warn("尝试缓存空的网站信息");
        }
    }

    /**
     * 获取缓存的网站信息
     */
    public WebInfo getCachedWebInfo() {
        try {
            Object cached = redisUtil.get(CacheConstants.WEB_INFO_KEY);
            if (cached instanceof WebInfo) {
                WebInfo webInfo = (WebInfo) cached;
                return webInfo;
            } else {
                log.info("缓存中未找到网站信息 - Key: {}, 缓存值类型: {}",
                        CacheConstants.WEB_INFO_KEY, cached != null ? cached.getClass().getSimpleName() : "null");
                
                // 尝试从数据库加载
                try {
                    WebInfo webInfo = loadWebInfoFromDatabase();
                    if (webInfo != null) {
                        // 加载成功，更新缓存
                        cacheWebInfo(webInfo);
                        return webInfo;
                    }
                } catch (Exception e) {
                    log.error("从数据库加载网站信息失败", e);
                }
                
                return null;
            }
        } catch (Exception e) {
            log.error("从缓存获取网站信息失败 - Key: {}", CacheConstants.WEB_INFO_KEY, e);
            
            // 尝试从数据库加载
            try {
                WebInfo webInfo = loadWebInfoFromDatabase();
                if (webInfo != null) {
                    // 加载成功，更新缓存
                    cacheWebInfo(webInfo);
                    return webInfo;
                }
            } catch (Exception dbError) {
                log.error("缓存失败后尝试从数据库加载网站信息也失败", dbError);
            }
            
            return null;
        }
    }
    
    /**
     * 从数据库加载网站信息
     * 私有方法，供getCachedWebInfo使用
     */
    private WebInfo loadWebInfoFromDatabase() {
        try {
            WebInfoMapper webInfoMapper = SpringContextUtil.getBean(WebInfoMapper.class);
            List<WebInfo> list = new LambdaQueryChainWrapper<>(webInfoMapper).list();
            
            if (list != null && !list.isEmpty()) {
                WebInfo webInfo = list.get(0);
                // 确保status字段有默认值
                if (webInfo.getStatus() == null) {
                    webInfo.setStatus(true);
                    log.info("WebInfo status字段为null，设置为默认值true");
                }
                log.info("成功从数据库加载网站信息");
                return webInfo;
            } else {
                log.warn("数据库中未找到网站信息");
                return null;
            }
        } catch (Exception e) {
            log.error("从数据库加载网站信息异常", e);
            return null;
        }
    }

    /**
     * 删除网站信息缓存
     */
    public void evictWebInfo() {
        try {
            redisUtil.del(CacheConstants.WEB_INFO_KEY);
            log.info("删除网站信息缓存成功 - Key: {}", CacheConstants.WEB_INFO_KEY);
        } catch (Exception e) {
            log.error("删除网站信息缓存失败 - Key: {}", CacheConstants.WEB_INFO_KEY, e);
        }
    }

    /**
     * 缓存系统配置
     */
    public void cacheSysConfig(String configKey, String configValue) {
        if (configKey != null) {
            String key = CacheConstants.buildSysConfigKey(configKey);
            redisUtil.set(key, configValue, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存系统配置(永久): {}", configKey);
        }
    }

    /**
     * 获取缓存的系统配置
     */
    public String getCachedSysConfig(String configKey) {
        if (configKey == null) return null;

        String key = CacheConstants.buildSysConfigKey(configKey);
        Object cached = redisUtil.get(key);
        if (cached instanceof String) {
            return (String) cached;
        }
        return null;
    }

    /**
     * 删除系统配置缓存
     */
    public void evictSysConfig(String configKey) {
        if (configKey != null) {
            String key = CacheConstants.buildSysConfigKey(configKey);
            redisUtil.del(key);
        }
    }

    /**
     * 规范化访问统计使用的IP值。
     */
    public String normalizeVisitIp(String ip) {
        if (ip == null) {
            return "";
        }
        String normalizedIp = ip.trim();
        if (normalizedIp.contains(",")) {
            normalizedIp = normalizedIp.split(",")[0].trim();
        }
        return normalizedIp;
    }

    /**
     * 标准化访问记录中的User-Agent，保持与写入Redis/数据库时一致。
     */
    public String normalizeVisitUserAgent(String userAgent) {
        String normalizedUserAgent = limitText(userAgent, 512);
        return normalizedUserAgent == null ? "" : normalizedUserAgent;
    }

    /**
     * 判断访问统计是否应忽略指定IP。
     */
    public boolean isVisitIpIgnored(String ip) {
        String normalizedIp = normalizeVisitIp(ip);
        return !normalizedIp.isEmpty() && getVisitIgnoreIps().contains(normalizedIp);
    }

    /**
     * 添加访问统计忽略IP。
     * @return true 表示本次新增，false 表示已存在或IP为空
     */
    public boolean addVisitIgnoreIp(String ip) {
        String normalizedIp = normalizeVisitIp(ip);
        if (normalizedIp.isEmpty()) {
            return false;
        }

        Set<String> ignoreIps = new LinkedHashSet<>(getVisitIgnoreIps());
        boolean added = ignoreIps.add(normalizedIp);
        if (added) {
            saveVisitIgnoreIps(ignoreIps);
        }
        return added;
    }

    /**
     * 获取访问统计忽略IP列表。
     */
    public Set<String> getVisitIgnoreIps() {
        try {
            String configValue = getCachedSysConfig(VISIT_IGNORE_IP_CONFIG_KEY);
            if (configValue == null) {
                SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, VISIT_IGNORE_IP_CONFIG_KEY)
                        .eq(SysConfig::getConfigType, VISIT_IGNORE_IP_CONFIG_TYPE)
                        .last("limit 1"));
                configValue = config != null ? config.getConfigValue() : "";
                cacheSysConfig(VISIT_IGNORE_IP_CONFIG_KEY, configValue == null ? "" : configValue);
            }
            return parseVisitIgnoreIps(configValue);
        } catch (Exception e) {
            log.error("获取访问统计忽略IP失败", e);
            return new LinkedHashSet<>();
        }
    }

    /**
     * 获取访问统计忽略IP列表，供动态SQL安全绑定。
     */
    public List<String> getVisitIgnoreIpList() {
        return new ArrayList<>(getVisitIgnoreIps());
    }

    /**
     * 站长/管理员登录成功后，自动把当前IP加入访问统计忽略名单，并清理登录前短时间窗口内的访问记录。
     */
    public Map<String, Object> ignoreVisitIpAndCleanRecent(String ip, int minutes) {
        String normalizedIp = normalizeVisitIp(ip);
        Map<String, Object> result = new HashMap<>();
        result.put("ip", normalizedIp);
        result.put("valid", !normalizedIp.isEmpty());
        result.put("addedToIgnore", false);
        result.put("alreadyIgnored", false);
        result.put("deletedDbCount", 0);
        result.put("removedRedisCount", 0);

        if (normalizedIp.isEmpty()) {
            return result;
        }

        boolean wasIgnored = isVisitIpIgnored(normalizedIp);
        boolean added = addVisitIgnoreIp(normalizedIp);
        int safeMinutes = Math.max(1, minutes);
        LocalDateTime since = LocalDateTime.now().minusMinutes(safeMinutes);
        int deletedDbCount = 0;
        int removedRedisCount = 0;

        // 查找该时间范围内站长 IP 访问的文章并扣减浏览量，然后清除对应缓存
        try {
            Map<Integer, Integer> visitedArticles = findVisitedArticleCounts(normalizedIp, since);
            if (!visitedArticles.isEmpty()) {
                com.ld.poetry.dao.ArticleMapper articleMapper = SpringContextUtil.getBean(com.ld.poetry.dao.ArticleMapper.class);
                for (Map.Entry<Integer, Integer> entry : visitedArticles.entrySet()) {
                    Integer articleId = entry.getKey();
                    Integer count = entry.getValue();
                    if (articleId != null && count != null && count > 0) {
                        articleMapper.decrementViewCount(articleId, count);
                        evictArticleRelatedCache(articleId);
                    }
                }
                log.info("已扣减站长IP最近访问的文章浏览量: {}", visitedArticles);
            }
        } catch (Exception e) {
            log.warn("扣减站长IP最近访问文章浏览量失败: ip={}, since={}", normalizedIp, since, e);
        }

        try {
            deletedDbCount = historyInfoMapper.deleteByIpSince(normalizedIp, since);
        } catch (Exception e) {
            log.warn("清理站长IP最近数据库访问记录失败: ip={}, since={}", normalizedIp, since, e);
        }

        try {
            removedRedisCount = removeRecentVisitRecordsByIpSince(normalizedIp, since);
        } catch (Exception e) {
            log.warn("清理站长IP最近Redis访问记录失败: ip={}, since={}", normalizedIp, since, e);
        }

        if (added || wasIgnored || deletedDbCount > 0 || removedRedisCount > 0) {
            refreshLocationStatisticsCache();
        }

        result.put("addedToIgnore", added);
        result.put("alreadyIgnored", wasIgnored || !added);
        result.put("deletedDbCount", deletedDbCount);
        result.put("removedRedisCount", removedRedisCount);
        result.put("totalRemovedCount", deletedDbCount + removedRedisCount);
        result.put("windowMinutes", safeMinutes);
        log.info("站长IP访问统计过滤完成: {}", result);
        return result;
    }

    private Map<Integer, Integer> findVisitedArticleCounts(String ip, LocalDateTime since) {
        Map<Integer, Integer> articleCounts = new HashMap<>();
        String normalizedIp = normalizeVisitIp(ip);
        if (normalizedIp.isEmpty() || since == null) {
            return articleCounts;
        }

        // 1. 从数据库中查询该 IP 且创建时间 >= since 的访问记录
        try {
            List<com.ld.poetry.entity.HistoryInfo> dbHistoryList = historyInfoMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ld.poetry.entity.HistoryInfo>()
                            .eq(com.ld.poetry.entity.HistoryInfo::getIp, normalizedIp)
                            .ge(com.ld.poetry.entity.HistoryInfo::getCreateTime, since)
            );
            if (dbHistoryList != null) {
                for (com.ld.poetry.entity.HistoryInfo history : dbHistoryList) {
                    processPageUriToArticleCounts(history.getPageUri(), articleCounts);
                }
            }
        } catch (Exception e) {
            log.warn("查询站长IP数据库历史记录提取文章ID失败: ip={}, since={}", normalizedIp, since, e);
        }

        // 2. 从 Redis 每日记录中查询
        try {
            LocalDate today = LocalDate.now();
            LocalDate sinceDate = since.toLocalDate();
            for (LocalDate date = sinceDate; !date.isAfter(today); date = date.plusDays(1)) {
                String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date.toString());
                List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
                if (recordJsonList != null && !recordJsonList.isEmpty()) {
                    for (Object recordJson : recordJsonList) {
                        if (recordJson == null) {
                            continue;
                        }
                        try {
                            Map<String, Object> record = JsonUtils.parseObject(recordJson.toString(), Map.class);
                            String recordIp = normalizeVisitIp(record != null ? String.valueOf(record.get("ip")) : "");
                            LocalDateTime recordTime = resolveVisitRecordTime(record);
                            if (normalizedIp.equals(recordIp) && recordTime != null && !recordTime.isBefore(since)) {
                                Object pageUriObj = record.get("pageUri");
                                if (pageUriObj != null) {
                                    processPageUriToArticleCounts(pageUriObj.toString(), articleCounts);
                                }
                            }
                        } catch (Exception e) {
                            // 忽略单个解析失败
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 Redis 每日访问记录中提取文章ID失败: ip={}, since={}", normalizedIp, since, e);
        }

        return articleCounts;
    }

    private void processPageUriToArticleCounts(String pageUri, Map<Integer, Integer> articleCounts) {
        if (pageUri == null || pageUri.isEmpty()) {
            return;
        }
        String token = PageVisitUtils.extractArticleToken(pageUri);
        if (token == null || token.isEmpty()) {
            return;
        }
        
        // 解析 token 为 articleId
        try {
            Integer articleId = null;
            if (com.ld.poetry.utils.ArticleUrlUtil.isNumericToken(token)) {
                articleId = Integer.valueOf(token.trim());
            } else {
                String slug = com.ld.poetry.utils.ArticleUrlUtil.normalizeSlug(token);
                if (com.ld.poetry.utils.ArticleUrlUtil.isValidSlug(slug)) {
                    com.ld.poetry.dao.ArticleMapper articleMapper = SpringContextUtil.getBean(com.ld.poetry.dao.ArticleMapper.class);
                    Article article = articleMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article>()
                                    .select(Article::getId)
                                    .eq(Article::getArticleSlug, slug)
                                    .last("limit 1")
                    );
                    if (article != null) {
                        articleId = article.getId();
                    }
                }
            }
            if (articleId != null) {
                articleCounts.put(articleId, articleCounts.getOrDefault(articleId, 0) + 1);
            }
        } catch (Exception e) {
            log.warn("从 token 解析文章 ID 失败: token={}, error={}", token, e.getMessage());
        }
    }

    private void saveVisitIgnoreIps(Set<String> ignoreIps) {
        String configValue = JsonUtils.toJsonString(new ArrayList<>(ignoreIps));
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, VISIT_IGNORE_IP_CONFIG_KEY)
                .eq(SysConfig::getConfigType, VISIT_IGNORE_IP_CONFIG_TYPE)
                .last("limit 1"));

        if (config == null) {
            config = new SysConfig();
            config.setConfigName("访问统计忽略IP列表");
            config.setConfigKey(VISIT_IGNORE_IP_CONFIG_KEY);
            config.setConfigValue(configValue);
            config.setConfigType(VISIT_IGNORE_IP_CONFIG_TYPE);
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(configValue);
            sysConfigMapper.updateById(config);
        }
        cacheSysConfig(VISIT_IGNORE_IP_CONFIG_KEY, configValue);
    }

    private Set<String> parseVisitIgnoreIps(String configValue) {
        Set<String> ignoreIps = new LinkedHashSet<>();
        if (configValue == null || configValue.trim().isEmpty()) {
            return ignoreIps;
        }

        try {
            List<String> parsedIps = JsonUtils.parseArray(configValue, String.class);
            if (parsedIps != null) {
                for (String ip : parsedIps) {
                    String normalizedIp = normalizeVisitIp(ip);
                    if (!normalizedIp.isEmpty()) {
                        ignoreIps.add(normalizedIp);
                    }
                }
                return ignoreIps;
            }
        } catch (Exception e) {
            log.warn("访问统计忽略IP配置不是JSON数组，尝试按分隔符解析");
        }

        String[] rawIps = configValue.split("[,;\\r\\n]+");
        for (String rawIp : rawIps) {
            String normalizedIp = normalizeVisitIp(rawIp);
            if (!normalizedIp.isEmpty()) {
                ignoreIps.add(normalizedIp);
            }
        }
        return ignoreIps;
    }

    /**
     * 获取缓存的标签信息列表
     * 如果缓存不存在或已过期，会自动尝试从数据库重新加载
     */
    @SuppressWarnings("unchecked")
    public List<?> getCachedLabelList(Integer sortId) {
        if (sortId == null) return null;

        String key = CacheConstants.LABEL_LIST_PREFIX + sortId;
        Object cached = redisUtil.get(key);
        if (cached instanceof List) {
            return (List<?>) cached;
        }
        
        // 缓存不存在或已过期，尝试从数据库重新加载
        try {
            org.springframework.context.ApplicationContext context = SpringContextUtil.getApplicationContext();
            if (context != null) {
                com.ld.poetry.dao.LabelMapper labelMapper = context.getBean(com.ld.poetry.dao.LabelMapper.class);
                List<?> labelList = new com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<>(labelMapper)
                    .eq(com.ld.poetry.entity.Label::getSortId, sortId)
                    .list();
                if (labelList != null) {
                    // 重新缓存
                    cacheLabelList(sortId, labelList);
                    log.info("标签信息缓存已过期，已从数据库重新加载: sortId={}, {} 条记录", sortId, labelList.size());
                    return labelList;
                }
            }
        } catch (Exception e) {
            log.error("从数据库重新加载标签信息失败: sortId={}", sortId, e);
        }
        
        return null;
    }

    /**
     * 缓存标签信息列表
     */
    public void cacheLabelList(Integer sortId, List<?> labelList) {
        if (sortId != null && labelList != null) {
            String key = CacheConstants.LABEL_LIST_PREFIX + sortId;
            redisUtil.set(key, labelList, CacheConstants.VERY_LONG_EXPIRE_TIME);
            log.info("缓存标签信息列表(24小时): sortId={}", sortId);
        }
    }

    /**
     * 删除标签信息列表缓存
     */
    public void evictLabelList(Integer sortId) {
        if (sortId != null) {
            String key = CacheConstants.LABEL_LIST_PREFIX + sortId;
            redisUtil.del(key);
        }
    }

    // ================================ IP历史记录和统计缓存 ================================

    /**
     * 缓存IP历史记录集合
     */
    public void cacheIpHistory(Object ipHistorySet) {
        if (ipHistorySet != null) {
            redisUtil.set(CacheConstants.IP_HISTORY_KEY, ipHistorySet, CacheConstants.DEFAULT_EXPIRE_TIME);
        }
    }

    /**
     * 获取缓存的IP历史记录集合
     */
    public Object getCachedIpHistory() {
        Object cached = redisUtil.get(CacheConstants.IP_HISTORY_KEY);
        if (cached != null) {
            return cached;
        }
        return null;
    }

    /**
     * 删除IP历史记录缓存
     */
    public void evictIpHistory() {
        redisUtil.del(CacheConstants.IP_HISTORY_KEY);
    }

    /**
     * 缓存IP历史统计信息
     * 注意：统计数据每天0点更新，过期时间设置为2天以确保缓存不会在定时任务执行前过期
     */
    public void cacheIpHistoryStatistics(Object statistics) {
        if (statistics != null) {
            // 使用2天过期时间（172800秒），确保在下次定时任务刷新前缓存不会过期
            redisUtil.set(CacheConstants.IP_HISTORY_STATS_KEY, statistics, 172800L);
        }
    }

    /**
     * 获取缓存的IP历史统计信息
     */
    public Object getCachedIpHistoryStatistics() {
        try {
            Object cached = redisUtil.get(CacheConstants.IP_HISTORY_STATS_KEY);
            if (cached != null) {
                return cached;
            } else {
                // 使用debug级别，因为缓存为空是正常情况（如系统刚启动）
                log.debug("IP历史统计信息缓存为空，可能是系统刚启动或缓存已过期");
            }
        } catch (Exception e) {
            log.error("获取IP历史统计信息缓存时出错", e);
        }
        return null;
    }

    /**
     * 安全地获取缓存的IP历史统计信息，如果缓存为空则尝试刷新缓存
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedIpHistoryStatisticsSafely() {
        try {
            Object cached = getCachedIpHistoryStatistics();
            if (cached instanceof Map) {
                Map<String, Object> stats = (Map<String, Object>) cached;
                // 检查关键统计数据是否为空或异常
                Object countObj = stats.get(CommonConst.IP_HISTORY_COUNT);
                if (countObj != null && countObj instanceof Number && ((Number) countObj).longValue() >= 0) {
                    return stats;
                }
                log.warn("缓存中的总访问量数据异常: {}", countObj);
            }
        } catch (Exception e) {
            log.error("安全获取IP历史统计信息时出错", e);
        }

        // 使用debug级别，因为返回默认值是正常的容错机制
        log.debug("IP历史统计缓存为空或异常，返回带刷新标记的默认值");
        // 返回默认值，但标记需要刷新
        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
        defaultStats.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
        defaultStats.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
        defaultStats.put(CommonConst.IP_HISTORY_COUNT, 0L);
        defaultStats.put("_cache_refresh_needed", true);

        return defaultStats;
    }

    /**
     * 删除IP历史统计信息缓存
     */
    public void evictIpHistoryStatistics() {
        redisUtil.del(CacheConstants.IP_HISTORY_STATS_KEY);
    }

    /**
     * 缓存管理员家庭信息
     */
    public void cacheAdminFamily(Object family) {
        if (family != null) {
            String key = CacheConstants.CACHE_PREFIX + "admin:family";
            redisUtil.set(key, family, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存管理员家庭信息(永久)");
        }
    }

    /**
     * 获取缓存的管理员家庭信息
     */
    public Object getCachedAdminFamily() {
        String key = CacheConstants.CACHE_PREFIX + "admin:family";
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return cached;
        }
        return null;
    }

    // ================================ Token管理缓存 ================================

    /**
     * 缓存管理员token
     */
    public void cacheAdminToken(Integer userId, String token) {
        if (userId != null && token != null) {
            String key = CacheConstants.buildAdminTokenKey(userId);
            redisUtil.set(key, token, CommonConst.TOKEN_EXPIRE);
            log.info("缓存管理员token: userId={}, 过期时间: {}秒", userId, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 获取管理员token
     */
    public String getAdminToken(Integer userId) {
        if (userId == null) return null;

        String key = CacheConstants.buildAdminTokenKey(userId);
        Object cached = redisUtil.get(key);
        if (cached instanceof String) {
            return (String) cached;
        }
        return null;
    }

    /**
     * 删除管理员token
     */
    public void evictAdminToken(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.buildAdminTokenKey(userId);
            redisUtil.del(key);
        }
    }

    /**
     * 缓存用户token
     */
    public void cacheUserToken(Integer userId, String token) {
        if (userId != null && token != null) {
            String key = CacheConstants.buildUserTokenKey(userId);
            redisUtil.set(key, token, CommonConst.TOKEN_EXPIRE);
            log.info("缓存用户token: userId={}, 过期时间: {}秒", userId, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 删除用户token
     */
    public void evictUserToken(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.buildUserTokenKey(userId);
            redisUtil.del(key);
        }
    }

    /**
     * 缓存token间隔检查
     */
    public void cacheTokenInterval(Integer userId, boolean isAdmin) {
        if (userId != null) {
            String key = isAdmin ?
                CacheConstants.buildAdminTokenIntervalKey(userId) :
                CacheConstants.buildUserTokenIntervalKey(userId);
            redisUtil.set(key, System.currentTimeMillis(), CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 删除token间隔检查
     */
    public void evictTokenInterval(Integer userId, boolean isAdmin) {
        if (userId != null) {
            String key = isAdmin ?
                CacheConstants.buildAdminTokenIntervalKey(userId) :
                CacheConstants.buildUserTokenIntervalKey(userId);
            redisUtil.del(key);
        }
    }

    /**
     * 清理用户的所有token相关缓存
     */
    public void evictAllUserTokens(Integer userId) {
        if (userId != null) {
            // 清理管理员token
            evictAdminToken(userId);
            evictTokenInterval(userId, true);

            // 清理用户token
            evictUserToken(userId);
            evictTokenInterval(userId, false);

            // 清理用户会话
            // 注意：这里需要根据token清理会话，但我们没有反向映射
            // 建议在实际使用中维护userId到token的映射

        }
    }

    // ================================ 安全相关缓存 ================================

    /**
     * 解除IP拉黑
     */
    public boolean unblacklistIP(String ip) {
        if (ip == null) return false;

        String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
        String attackKey = CacheConstants.buildIpAttackKey(ip);

        redisUtil.del(blacklistKey, attackKey);
        log.info("管理员手动解除IP拉黑: {}", ip);
        return true;
    }

    /**
     * 管理员手动拉黑IP
     * <p>写入 SecurityFilter 使用的黑名单键，由 SecurityFilter 在请求入口最早期拦截，
     * 返回 403 + "403 Forbidden - IP Blacklisted"。无论客户端是否执行 JS、是否为爬虫，
     * 只要 HTTP 请求到达服务器即被拒绝，连 HTML 源码都不会输出。
     *
     * <p>支持三种时长模式：
     * <ul>
     *     <li>{@code durationSeconds > 0}：按指定秒数拉黑（如 3600=1h, 86400=1d, 604800=7d）</li>
     *     <li>{@code durationSeconds == 0}：使用默认 24 小时（{@link CacheConstants#IP_BLACKLIST_EXPIRE_TIME}）</li>
     *     <li>{@code durationSeconds < 0}（如 -1）：永久拉黑，不带 TTL，需手动解除才会失效</li>
     * </ul>
     *
     * @param ip              目标IP
     * @param reason          拉黑原因（写入 Redis value，便于审计）
     * @param durationSeconds 拉黑时长（秒）：&gt;0 按指定秒数，==0 走默认 24h，&lt;0（如 -1）永久拉黑
     * @return 是否成功
     */
    public boolean blacklistIP(String ip, String reason, long durationSeconds) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
            String value = reason == null || reason.isEmpty()
                    ? "manual_block"
                    : "manual:" + reason;

            boolean permanent = durationSeconds < 0;
            long ttl;
            if (permanent) {
                redisUtil.set(blacklistKey, value); // 无 TTL，永久
                ttl = -1;
            } else if (durationSeconds == 0) {
                ttl = CacheConstants.IP_BLACKLIST_EXPIRE_TIME;
                redisUtil.set(blacklistKey, value, ttl);
            } else {
                ttl = durationSeconds;
                redisUtil.set(blacklistKey, value, ttl);
            }
            log.warn("管理员手动拉黑IP: {}, 原因: {}, 时长: {}",
                    ip, value, permanent ? "永久" : ttl + "秒");
            return true;
        } catch (Exception e) {
            log.error("管理员手动拉黑IP失败: {}", ip, e);
            return false;
        }
    }

    /**
     * 查询IP是否在黑名单中
     */
    public boolean isIPBlacklisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
            return redisUtil.hasKey(blacklistKey);
        } catch (Exception e) {
            log.error("查询IP黑名单状态失败: {}", ip, e);
            return false;
        }
    }

    /**
     * 查询IP剩余封禁时间（秒）
     * @return -1 永久封禁；-2 不存在；其余正数剩余秒数
     */
    public long getIpBlacklistTtl(String ip) {
        if (ip == null || ip.isEmpty()) {
            return -2;
        }
        try {
            String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
            return redisUtil.getExpire(blacklistKey);
        } catch (Exception e) {
            log.error("查询IP剩余封禁时间失败: {}", ip, e);
            return -2;
        }
    }

    /**
     * 列出所有 SecurityFilter 黑名单 IP
     * <p>扫描 {@code poetize:security:blacklist:*} 键，返回每条记录的 ip、原因（value）、
     * 剩余秒数（ttl，-1 表示永久）。仅用于管理员后台展示与解除。
     *
     * @return 黑名单列表（按剩余时间倒序：永久优先，再按剩余秒数降序）
     */
    public List<Map<String, Object>> listBlacklistedIps() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(CacheConstants.IP_BLACKLIST_PREFIX + "*")
                    .count(200)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }

            String prefix = CacheConstants.IP_BLACKLIST_PREFIX;
            for (String key : keys) {
                String ip = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
                Object value = redisUtil.get(key);
                long ttl = redisUtil.getExpire(key);

                Map<String, Object> item = new HashMap<>();
                item.put("ip", ip);
                item.put("reason", value == null ? "" : String.valueOf(value));
                item.put("ttl", ttl); // -1 永久，-2 已过期/不存在
                item.put("permanent", ttl == -1);
                result.add(item);
            }

            // 排序：永久优先 → 剩余时间长的优先 → ip 字典序
            result.sort((a, b) -> {
                long ta = ((Number) a.get("ttl")).longValue();
                long tb = ((Number) b.get("ttl")).longValue();
                if (ta == tb) {
                    return String.valueOf(a.get("ip")).compareTo(String.valueOf(b.get("ip")));
                }
                // 永久(-1) 视为最大；-2（不存在）视为最小
                long va = ta < 0 ? (ta == -1 ? Long.MAX_VALUE : Long.MIN_VALUE) : ta;
                long vb = tb < 0 ? (tb == -1 ? Long.MAX_VALUE : Long.MIN_VALUE) : tb;
                return Long.compare(vb, va);
            });
            return result;
        } catch (Exception e) {
            log.error("列出IP黑名单失败", e);
            return result;
        }
    }

    /**
     * 获取被拦截的恶意请求总数
     */
    public long getTotalBlockedRequests() {
        Object count = redisUtil.get(CacheConstants.CACHE_PREFIX + "security:blocked:total");
        if (count instanceof Number) {
            return ((Number) count).longValue();
        }
        return 0;
    }

    // ================================ 会话管理 ================================

    /**
     * 缓存用户会话
     * 使用统一的TOKEN_EXPIRE时间，确保与token过期时间一致
     */
    public void cacheUserSession(String token, Integer userId) {
        if (token != null && userId != null) {
            String key = CacheConstants.buildSessionKey(token);
            redisUtil.set(key, userId, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 缓存用户Token映射
     * 使用统一的TOKEN_EXPIRE时间，确保与token过期时间一致
     */
    public void cacheUserTokenMapping(Integer userId, String token) {
        if (userId != null && token != null) {
            String key = CacheConstants.CACHE_PREFIX + "user:token:" + userId;
            redisUtil.set(key, token, CommonConst.TOKEN_EXPIRE);
            log.info("缓存用户Token映射: userId={}, tokenPrefix={}, 过期时间: {}秒", userId, TokenValidationUtil.getTokenPrefix(token), CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 获取用户的Token
     */
    public String getUserToken(Integer userId) {
        if (userId == null) return null;

        String key = CacheConstants.buildUserTokenKey(userId);
        Object cached = redisUtil.get(key);
        if (cached instanceof String) {
            return (String) cached;
        }
        return null;
    }

    /**
     * 获取会话中的用户ID
     */
    public Integer getUserIdFromSession(String token) {
        if (token == null) return null;

        String key = CacheConstants.buildSessionKey(token);
        Object cached = redisUtil.get(key);
        if (cached instanceof Integer) {
            return (Integer) cached;
        }
        return null;
    }

    /**
     * 删除用户会话
     */
    public void evictUserSession(String token) {
        if (token != null) {
            String key = CacheConstants.buildSessionKey(token);
            redisUtil.del(key);
        }
    }

    /**
     * 删除用户Token映射
     */
    public void evictUserTokenMapping(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.CACHE_PREFIX + "user:token:" + userId;
            redisUtil.del(key);
        }
    }

    /**
     * 缓存管理员用户信息
     */
    public void cacheAdminUser(User admin) {
        if (admin != null) {
            redisUtil.set(CacheConstants.CACHE_PREFIX + "admin", admin, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存管理员用户信息(永久): {}", admin.getId());
        }
    }

    /**
     * 获取缓存的管理员用户信息
     */
    public User getCachedAdminUser() {
        Object cached = redisUtil.get(CacheConstants.CACHE_PREFIX + "admin");
        if (cached instanceof User) {
            return (User) cached;
        }
        return null;
    }

    // ================================ 评论缓存 ================================

    /**
     * 缓存评论列表
     */
    public void cacheCommentList(Integer source, String type, List<?> comments) {
        if (source != null && type != null && comments != null) {
            String key = CacheConstants.buildCommentListKey(source, type);
            redisUtil.set(key, comments, CacheConstants.DEFAULT_EXPIRE_TIME);
        }
    }

    /**
     * 获取缓存的评论列表
     */
    @SuppressWarnings("unchecked")
    public List<?> getCachedCommentList(Integer source, String type) {
        if (source == null || type == null) return null;

        String key = CacheConstants.buildCommentListKey(source, type);
        Object cached = redisUtil.get(key);
        if (cached instanceof List) {
            return (List<?>) cached;
        }
        return null;
    }

    /**
     * 删除评论列表缓存
     */
    public void evictCommentList(Integer source, String type) {
        if (source != null && type != null) {
            String key = CacheConstants.buildCommentListKey(source, type);
            redisUtil.del(key);
        }
    }

    /**
     * 缓存评论数量
     */
    public void cacheCommentCount(Integer source, String type, Long count) {
        if (source != null && type != null && count != null) {
            String key = CacheConstants.buildCommentListKey(source, type) + ":count";
            redisUtil.set(key, count, CacheConstants.LONG_EXPIRE_TIME);
        }
    }

    /**
     * 获取缓存的评论数量
     */
    public Long getCachedCommentCount(Integer source, String type) {
        if (source == null || type == null) return null;

        String key = CacheConstants.buildCommentListKey(source, type) + ":count";
        Object cached = redisUtil.get(key);
        if (cached instanceof Number) {
            return ((Number) cached).longValue();
        }
        return null;
    }

    /**
     * 递增评论数量
     */
    public long incrementCommentCount(Integer source, String type) {
        if (source == null || type == null) return 0;

        String key = CacheConstants.buildCommentListKey(source, type) + ":count";
        return redisUtil.incr(key, 1);
    }

    /**
     * 递减评论数量
     */
    public long decrementCommentCount(Integer source, String type) {
        if (source == null || type == null) return 0;

        String key = CacheConstants.buildCommentListKey(source, type) + ":count";
        return redisUtil.decr(key, 1);
    }

    /**
     * 删除评论相关的所有缓存
     */
    public void evictCommentRelatedCache(Integer source, String type) {
        if (source != null && type != null) {
            evictCommentList(source, type);
            String countKey = CacheConstants.buildCommentListKey(source, type) + ":count";
            redisUtil.del(countKey);
        }
    }

    // ================================ 通用缓存操作 ================================

    /**
     * 通用缓存设置方法（带过期时间）
     */
    public boolean set(String key, Object value, long expireTime) {
        return redisUtil.set(key, value, (int) expireTime);
    }

    /**
     * 通用缓存设置方法（永久缓存）
     */
    public void set(String key, Object value) {
        redisUtil.set(key, value);
    }

    /**
     * 通用缓存获取方法
     */
    public Object get(String key) {
        Object value = redisUtil.get(key);
        if (value != null) {
        }
        return value;
    }

    /**
     * 检查缓存键是否存在
     */
    public boolean hasKey(String key) {
        return redisUtil.hasKey(key);
    }

    /**
     * 删除缓存键
     */
    public void deleteKey(String key) {
        redisUtil.del(key);
    }

    /**
     * 设置缓存过期时间
     */
    public boolean expire(String key, long time) {
        return redisUtil.expire(key, time);
    }

    /**
     * 删除缓存（别名方法，与deleteKey功能相同）
     */
    public void delete(String key) {
        deleteKey(key);
    }

    /**
     * 递增缓存值
     */
    public long incr(String key, long delta) {
        return redisUtil.incr(key, delta);
    }

    /**
     * 根据模式删除多个缓存键
     */
    public void deleteKeysByPattern(String pattern) {
        try {
            // 使用scan命令代替keys命令，避免在大数据量时阻塞Redis
            log.info("开始删除匹配模式的缓存键: {}", pattern);
            
            Set<String> keysToDelete = new HashSet<>();
            
            // 使用RedisTemplate的scan方法获取匹配的键
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                try {
                    Cursor<byte[]> cursor = connection.keyCommands().scan(
                        ScanOptions.scanOptions()
                            .match(pattern)
                            .count(1000) // 每次扫描1000个键
                            .build());
                    
                    try {
                        while (cursor.hasNext()) {
                            keysToDelete.add(new String(cursor.next(), StandardCharsets.UTF_8));
                        }
                    } finally {
                        cursor.close();
                    }
                } catch (Exception e) {
                    log.error("扫描Redis键时发生异常", e);
                }
                return null;
            });
            
            // 批量删除匹配的键
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("成功删除{}个匹配模式的缓存键: {}", keysToDelete.size(), pattern);
            } else {
                log.info("没有找到匹配模式的缓存键: {}", pattern);
            }
            
        } catch (Exception e) {
            log.error("根据模式删除缓存键失败: pattern={}", pattern, e);
        }
    }
    
    // ================================ 访问统计Redis缓存方法 ================================

    /**
     * 标记页面访问去重窗口。
     * 同一 IP + UA + 页面 URI 在 5 分钟内只记录一次，避免一次页面加载里的重复来源互相叠加。
     */
    public boolean tryMarkPageVisit(String ip, String pageUri, String userAgent) {
        try {
            String normalizedUri = PageVisitUtils.normalizeVisitUri(pageUri);
            String key = CacheConstants.buildVisitDedupeKey(
                    sha256Hex(normalizeDedupePart(ip)),
                    sha256Hex(normalizeDedupePart(userAgent)),
                    sha256Hex(normalizedUri)
            );
            return redisUtil.setIfAbsent(key, "1", CacheConstants.SHORT_EXPIRE_TIME);
        } catch (Exception e) {
            log.warn("页面访问去重失败，继续记录访问: {}", e.getMessage());
            return true;
        }
    }
    
    /**
     * 记录访问信息到Redis（不立即写数据库）
     * @param ip IP地址
     * @param userId 用户ID（可为null）
     * @param nation 国家
     * @param province 省份
     * @param city 城市
     */
    public void recordVisitToRedis(String ip, Integer userId, String nation, String province, String city) {
        recordVisitToRedis(ip, userId, nation, province, city, null, null, null, null);
    }

    /**
     * 记录访问信息到Redis（带用户请求元数据，不立即写数据库）
     * @param ip IP地址
     * @param userId 用户ID（可为null）
     * @param nation 国家
     * @param province 省份
     * @param city 城市
     * @param pageUri 访问的页面URI
     * @param userAgent 用户浏览器User-Agent
     * @param referer 来源页面Referer
     * @param acceptLanguage 用户语言偏好Accept-Language
     */
    public void recordVisitToRedis(String ip, Integer userId, String nation, String province, String city,
                                   String pageUri, String userAgent, String referer, String acceptLanguage) {
        recordVisitToRedis(ip, userId, nation, province, city, pageUri, userAgent, referer, acceptLanguage,
                UserAgentClassifier.classify(userAgent));
    }

    /**
     * 记录访问信息到Redis（带UA分类结果，不立即写数据库）
     */
    public void recordVisitToRedis(String ip, Integer userId, String nation, String province, String city,
                                   String pageUri, String userAgent, String referer, String acceptLanguage,
                                   UserAgentClassifier.UaInfo uaInfo) {
        try {
            String normalizedIp = normalizeVisitIp(ip);
            if (normalizedIp.isEmpty() || isVisitIpIgnored(normalizedIp)) {
                return;
            }

            String today = java.time.LocalDate.now().toString();
            String normalizedPageUri = PageVisitUtils.normalizeVisitUri(pageUri);
            String safeCity = normalizeLocationField(city);
            String safeNation = Optional.ofNullable(VisitRegionNormalizer.normalizeCountryName(nation))
                    .orElseGet(() -> {
                        String normalizedNation = normalizeLocationField(nation);
                        return normalizedNation != null ? normalizedNation : VisitRegionNormalizer.UNKNOWN_REGION;
                    });
            String safeProvince = VisitRegionNormalizer.resolveProvinceOrCountry(
                    safeNation != null ? safeNation : nation,
                    province,
                    safeCity
            );
            String safeUserAgent = limitText(userAgent, 512);
            UserAgentClassifier.UaInfo safeUaInfo = uaInfo != null ? uaInfo : UserAgentClassifier.classify(safeUserAgent);
            String safeReferer = limitText(referer, 512);
            
            // 将访问记录添加到当日记录集合中（每次访问都记录）
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(today);
            
            // 构建访问记录JSON
            java.util.Map<String, Object> visitRecord = new java.util.HashMap<>();
            visitRecord.put("ip", normalizedIp);
            visitRecord.put("userId", userId);
            visitRecord.put("nation", safeNation);
            visitRecord.put("province", safeProvince);
            visitRecord.put("city", safeCity);
            // 使用数据库兼容的时间格式 yyyy-MM-dd HH:mm:ss
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            visitRecord.put("createTime", java.time.LocalDateTime.now().format(formatter));
            // 添加同步标记，默认未同步
            visitRecord.put("synced", false);

            // 用户请求元数据（可选，用于后续分析）
            if (normalizedPageUri != null && !normalizedPageUri.isEmpty()) {
                visitRecord.put("pageUri", normalizedPageUri);
            }
            if (safeReferer != null && !safeReferer.isEmpty()) {
                visitRecord.put("referer", safeReferer);
            }
            if (safeUserAgent != null && !safeUserAgent.isEmpty()) {
                visitRecord.put("userAgent", safeUserAgent);
            }
            if (safeUaInfo != null) {
                visitRecord.put("uaType", safeUaInfo.type());
                visitRecord.put("uaName", safeUaInfo.name());
                visitRecord.put("botVerifyStatus", safeUaInfo.botVerifyStatus());
                if (safeUaInfo.botVerifyReason() != null && !safeUaInfo.botVerifyReason().isEmpty()) {
                    visitRecord.put("botVerifyReason", limitText(safeUaInfo.botVerifyReason(), 255));
                }
            }
            
            // 将记录序列化为JSON字符串并添加到Redis List中
            String recordJson = JsonUtils.toJsonString(visitRecord);
            redisUtil.lSet(recordsKey, recordJson);
            
            // 设置记录的过期时间为7天
            redisUtil.expire(recordsKey, 7 * 24 * 3600);
            
            // log.info("访问记录已保存到Redis: ip={}, userId={}, province={}", ip, userId, province);
            
        } catch (Exception e) {
            log.error("记录访问信息到Redis失败: ip={}, userId={}", ip, userId, e);
        }
    }
    
    /**
     * 获取指定日期的访问记录（用于同步到数据库）
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 访问记录列表
     */
    @SuppressWarnings("unchecked")
    public java.util.List<java.util.Map<String, Object>> getDailyVisitRecords(String date) {
        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            java.util.List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
            
            java.util.List<java.util.Map<String, Object>> records = new java.util.ArrayList<>();
            Set<String> ignoredIps = getVisitIgnoreIps();
            
            if (recordJsonList != null) {
                for (Object recordJson : recordJsonList) {
                    try {
                        java.util.Map<String, Object> record = JsonUtils.parseObject(recordJson.toString(), java.util.Map.class);
                        String recordIp = normalizeVisitIp(record != null ? String.valueOf(record.get("ip")) : "");
                        if (!ignoredIps.contains(recordIp)) {
                            records.add(record);
                        }
                    } catch (Exception e) {
                        log.warn("解析访问记录JSON失败: {}", recordJson, e);
                    }
                }
            }
            
            log.info("获取{}的访问记录: {} 条", date, records.size());
            return records;
            
        } catch (Exception e) {
            log.error("获取每日访问记录失败: date={}", date, e);
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * 清空指定日期的访问记录缓存（同步到数据库后调用）
     * @param date 日期（格式：yyyy-MM-dd）
     */
    public void clearDailyVisitRecords(String date) {
        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            redisUtil.del(recordsKey);
            log.info("已清空{}的访问记录缓存", date);
        } catch (Exception e) {
            log.error("清空每日访问记录缓存失败: date={}", date, e);
        }
    }

    /**
     * 删除最近若干天Redis访问记录中的指定IP。
     */
    public int removeRecentVisitRecordsByIp(String ip, int days) {
        String normalizedIp = normalizeVisitIp(ip);
        if (normalizedIp.isEmpty()) {
            return 0;
        }

        int safeDays = Math.max(1, Math.min(days, DAILY_VISIT_RECORD_RETENTION_DAYS));
        int removedCount = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < safeDays; i++) {
            removedCount += removeDailyVisitRecordsByIp(today.minusDays(i).toString(), normalizedIp);
        }
        return removedCount;
    }

    /**
     * 删除最近若干天Redis访问记录中的指定User-Agent。
     */
    public int removeRecentVisitRecordsByUserAgent(String userAgent, int days) {
        String normalizedUserAgent = normalizeVisitUserAgent(userAgent);
        if (normalizedUserAgent.isEmpty()) {
            return 0;
        }

        int safeDays = Math.max(1, Math.min(days, DAILY_VISIT_RECORD_RETENTION_DAYS));
        int removedCount = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < safeDays; i++) {
            removedCount += removeDailyVisitRecordsByUserAgent(today.minusDays(i).toString(), normalizedUserAgent);
        }
        return removedCount;
    }

    /**
     * 删除最近若干分钟内Redis访问记录中的指定IP。
     */
    public int removeRecentVisitRecordsByIpSince(String ip, LocalDateTime since) {
        String normalizedIp = normalizeVisitIp(ip);
        if (normalizedIp.isEmpty() || since == null) {
            return 0;
        }

        int removedCount = 0;
        LocalDate today = LocalDate.now();
        LocalDate sinceDate = since.toLocalDate();
        for (LocalDate date = sinceDate; !date.isAfter(today); date = date.plusDays(1)) {
            removedCount += removeDailyVisitRecordsByIpSince(date.toString(), normalizedIp, since);
        }
        return removedCount;
    }

    /**
     * 删除指定日期Redis访问记录中的指定IP。
     */
    @SuppressWarnings("unchecked")
    public int removeDailyVisitRecordsByIp(String date, String ip) {
        String normalizedIp = normalizeVisitIp(ip);
        if (normalizedIp.isEmpty()) {
            return 0;
        }

        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
            if (recordJsonList == null || recordJsonList.isEmpty()) {
                return 0;
            }

            int removedCount = 0;
            List<String> retainedRecords = new ArrayList<>();
            for (Object recordJson : recordJsonList) {
                if (recordJson == null) {
                    continue;
                }
                String recordText = recordJson.toString();
                try {
                    Map<String, Object> record = JsonUtils.parseObject(recordText, Map.class);
                    String recordIp = normalizeVisitIp(record != null ? String.valueOf(record.get("ip")) : "");
                    if (normalizedIp.equals(recordIp)) {
                        removedCount++;
                    } else {
                        retainedRecords.add(recordText);
                    }
                } catch (Exception e) {
                    log.warn("解析访问记录JSON失败，清理IP时保留原记录: {}", recordJson, e);
                    retainedRecords.add(recordText);
                }
            }

            if (removedCount > 0) {
                redisUtil.del(recordsKey);
                for (String retainedRecord : retainedRecords) {
                    redisUtil.lSet(recordsKey, retainedRecord);
                }
                if (!retainedRecords.isEmpty()) {
                    redisUtil.expire(recordsKey, DAILY_VISIT_RECORD_RETENTION_DAYS * 24 * 3600);
                }
                log.info("已从{}的Redis访问记录中删除IP {} 的 {} 条记录", date, normalizedIp, removedCount);
            }
            return removedCount;
        } catch (Exception e) {
            log.error("删除每日访问记录中的指定IP失败: date={}, ip={}", date, normalizedIp, e);
            return 0;
        }
    }

    /**
     * 删除指定日期Redis访问记录中的指定User-Agent。
     */
    @SuppressWarnings("unchecked")
    public int removeDailyVisitRecordsByUserAgent(String date, String userAgent) {
        String normalizedUserAgent = normalizeVisitUserAgent(userAgent);
        if (normalizedUserAgent.isEmpty()) {
            return 0;
        }

        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
            if (recordJsonList == null || recordJsonList.isEmpty()) {
                return 0;
            }

            int removedCount = 0;
            List<String> retainedRecords = new ArrayList<>();
            for (Object recordJson : recordJsonList) {
                if (recordJson == null) {
                    continue;
                }
                String recordText = recordJson.toString();
                try {
                    Map<String, Object> record = JsonUtils.parseObject(recordText, Map.class);
                    Object userAgentObj = record != null ? Optional.ofNullable(record.get("userAgent"))
                            .orElse(record.get("user_agent")) : null;
                    String recordUserAgent = normalizeVisitUserAgent(userAgentObj != null ? userAgentObj.toString() : "");
                    if (normalizedUserAgent.equals(recordUserAgent)) {
                        removedCount++;
                    } else {
                        retainedRecords.add(recordText);
                    }
                } catch (Exception e) {
                    log.warn("解析访问记录JSON失败，清理UA时保留原记录: {}", recordJson, e);
                    retainedRecords.add(recordText);
                }
            }

            if (removedCount > 0) {
                redisUtil.del(recordsKey);
                for (String retainedRecord : retainedRecords) {
                    redisUtil.lSet(recordsKey, retainedRecord);
                }
                if (!retainedRecords.isEmpty()) {
                    redisUtil.expire(recordsKey, DAILY_VISIT_RECORD_RETENTION_DAYS * 24 * 3600);
                }
                log.info("已从{}的Redis访问记录中删除UA {} 的 {} 条记录", date, normalizedUserAgent, removedCount);
            }
            return removedCount;
        } catch (Exception e) {
            log.error("删除每日访问记录中的指定UA失败: date={}, userAgent={}", date, normalizedUserAgent, e);
            return 0;
        }
    }

    /**
     * 删除指定日期Redis访问记录中某个时间点之后的指定IP。
     */
    @SuppressWarnings("unchecked")
    public int removeDailyVisitRecordsByIpSince(String date, String ip, LocalDateTime since) {
        String normalizedIp = normalizeVisitIp(ip);
        if (normalizedIp.isEmpty() || since == null) {
            return 0;
        }

        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
            if (recordJsonList == null || recordJsonList.isEmpty()) {
                return 0;
            }

            int removedCount = 0;
            List<String> retainedRecords = new ArrayList<>();
            for (Object recordJson : recordJsonList) {
                if (recordJson == null) {
                    continue;
                }
                String recordText = recordJson.toString();
                try {
                    Map<String, Object> record = JsonUtils.parseObject(recordText, Map.class);
                    String recordIp = normalizeVisitIp(record != null ? String.valueOf(record.get("ip")) : "");
                    LocalDateTime recordTime = resolveVisitRecordTime(record);
                    if (normalizedIp.equals(recordIp) && recordTime != null && !recordTime.isBefore(since)) {
                        removedCount++;
                    } else {
                        retainedRecords.add(recordText);
                    }
                } catch (Exception e) {
                    log.warn("解析访问记录JSON失败，按时间清理IP时保留原记录: {}", recordJson, e);
                    retainedRecords.add(recordText);
                }
            }

            if (removedCount > 0) {
                redisUtil.del(recordsKey);
                for (String retainedRecord : retainedRecords) {
                    redisUtil.lSet(recordsKey, retainedRecord);
                }
                if (!retainedRecords.isEmpty()) {
                    redisUtil.expire(recordsKey, DAILY_VISIT_RECORD_RETENTION_DAYS * 24 * 3600);
                }
                log.info("已从{}的Redis访问记录中删除IP {} 在 {} 后的 {} 条记录", date, normalizedIp, since, removedCount);
            }
            return removedCount;
        } catch (Exception e) {
            log.error("按时间删除每日访问记录中的指定IP失败: date={}, ip={}, since={}", date, normalizedIp, since, e);
            return 0;
        }
    }

    /**
     * 获取指定日期的未同步访问记录
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 未同步的访问记录列表
     */
    @SuppressWarnings("unchecked")
    public java.util.List<java.util.Map<String, Object>> getUnsyncedDailyVisitRecords(String date) {
        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            java.util.List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
            
            java.util.List<java.util.Map<String, Object>> unsyncedRecords = new java.util.ArrayList<>();
            Set<String> ignoredIps = getVisitIgnoreIps();
            
            if (recordJsonList != null) {
                for (Object recordJson : recordJsonList) {
                    try {
                        java.util.Map<String, Object> record = JsonUtils.parseObject(recordJson.toString(), java.util.Map.class);
                        String recordIp = normalizeVisitIp(record != null ? String.valueOf(record.get("ip")) : "");
                        if (ignoredIps.contains(recordIp)) {
                            continue;
                        }
                        // 只返回未同步的记录
                        Boolean synced = (Boolean) record.get("synced");
                        if (synced == null || !synced) {
                            unsyncedRecords.add(record);
                        }
                    } catch (Exception e) {
                        log.warn("解析访问记录JSON失败: {}", recordJson, e);
                    }
                }
            }
            
            log.info("获取{}的未同步访问记录: {} 条", date, unsyncedRecords.size());
            return unsyncedRecords;
            
        } catch (Exception e) {
            log.error("获取未同步访问记录失败: date={}", date, e);
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 标记指定日期的访问记录为已同步
     * @param date 日期（格式：yyyy-MM-dd）
     * @param syncedRecords 已同步的记录列表
     */
    @SuppressWarnings("unchecked")
    public void markVisitRecordsAsSynced(String date, java.util.List<java.util.Map<String, Object>> syncedRecords) {
        try {
            String recordsKey = CacheConstants.buildDailyVisitRecordsKey(date);
            java.util.List<Object> recordJsonList = redisUtil.lGet(recordsKey, 0, -1);
            
            if (recordJsonList == null || recordJsonList.isEmpty()) {
                return;
            }

            // 创建已同步记录的标识集合（用于快速查找）
            java.util.Set<String> syncedRecordIds = new java.util.HashSet<>();
            for (java.util.Map<String, Object> syncedRecord : syncedRecords) {
                // 使用ip+createTime作为唯一标识
                String recordId = syncedRecord.get("ip") + "_" + syncedRecord.get("createTime");
                syncedRecordIds.add(recordId);
            }

            // 更新Redis中的记录，标记已同步的记录
            java.util.List<String> updatedRecords = new java.util.ArrayList<>();
            for (Object recordJson : recordJsonList) {
                try {
                    java.util.Map<String, Object> record = JsonUtils.parseObject(recordJson.toString(), java.util.Map.class);
                    String recordId = record.get("ip") + "_" + record.get("createTime");
                    
                    // 如果这条记录已同步，则标记为已同步
                    if (syncedRecordIds.contains(recordId)) {
                        record.put("synced", true);
                    }
                    
                    updatedRecords.add(JsonUtils.toJsonString(record));
                } catch (Exception e) {
                    log.warn("更新访问记录同步标记失败: {}", recordJson, e);
                    // 保留原记录
                    updatedRecords.add(recordJson.toString());
                }
            }

            // 清空原记录并重新插入更新后的记录
            redisUtil.del(recordsKey);
            for (String updatedRecord : updatedRecords) {
                redisUtil.lSet(recordsKey, updatedRecord);
            }

            // 重新设置过期时间
            redisUtil.expire(recordsKey, 7 * 24 * 3600);
            
            log.info("已标记{}的{}条访问记录为已同步", date, syncedRecords.size());
            
        } catch (Exception e) {
            log.error("标记访问记录为已同步失败: date={}", date, e);
        }
    }


    /**
     * 刷新地理位置统计缓存 (混合Redis+数据库)
     */
    public void refreshLocationStatisticsCache() {
        try {
            Map<String, Object> statistics = new HashMap<>();
            
            // 1. 获取数据库的历史统计（省份、IP统计）
            try {
                List<String> ignoredIps = getVisitIgnoreIpList();
                List<Map<String, Object>> provinceStats = historyInfoMapper.getHistoryByProvince(ignoredIps);
                List<Map<String, Object>> ipStats = historyInfoMapper.getHistoryByIp(ignoredIps);
                
                statistics.put(CommonConst.IP_HISTORY_PROVINCE, provinceStats != null ? provinceStats : new ArrayList<>());
                statistics.put(CommonConst.IP_HISTORY_IP, ipStats != null ? ipStats : new ArrayList<>());
                
                log.info("成功获取数据库统计: 省份{}, IP{}", 
                    provinceStats != null ? provinceStats.size() : 0, 
                    ipStats != null ? ipStats.size() : 0);
            } catch (Exception e) {
                log.error("获取数据库统计失败", e);
                statistics.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
                statistics.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
            }
            
            // 2. 获取昨日访问统计（按日历天计算）
            try {
                List<Map<String, Object>> yesterdayStats = getYesterdayStatisticsFromDatabase();
                statistics.put(CommonConst.IP_HISTORY_HOUR, yesterdayStats);
                log.info("成功获取昨日访问统计: {}", yesterdayStats.size());
            } catch (Exception e) {
                log.error("获取昨日访问统计失败", e);
                statistics.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
            }
            
            // 3. 计算总访问量（仅统计数据库数据）
            try {
                // 只获取数据库总数，不再统计Redis实时数据
                Long dbCount = historyInfoMapper.getHistoryCount(getVisitIgnoreIpList());
                long totalCount = dbCount != null ? dbCount : 0;
                
                statistics.put(CommonConst.IP_HISTORY_COUNT, totalCount);
                log.info("成功计算总访问量: 数据库总计={}", totalCount);
            } catch (Exception e) {
                log.error("计算总访问量失败", e);
                statistics.put(CommonConst.IP_HISTORY_COUNT, 0L);
            }
            
            // 缓存统计结果
            cacheIpHistoryStatistics(statistics);
            log.info("成功刷新地理位置统计缓存 (仅数据库统计)");
            
        } catch (Exception e) {
            log.error("刷新地理位置统计缓存失败", e);
            
            // 完全失败时的fallback
            try {
                log.warn("混合统计失败，完全fallback到数据库查询");
                Map<String, Object> fallbackStats = generateLocationStatisticsFromDatabase();
                cacheIpHistoryStatistics(fallbackStats);
                log.info("成功使用数据库fallback刷新统计缓存");
            } catch (Exception dbException) {
                log.error("数据库fallback也失败", dbException);
            }
        }
    }

    /**
     * Fallback: 基于数据库生成地理位置统计 (保留作为备用方案)
     */
    private Map<String, Object> generateLocationStatisticsFromDatabase() {
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            statistics.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince(getVisitIgnoreIpList()));
        } catch (Exception e) {
            log.error("数据库省份统计查询失败", e);
            statistics.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
        }
        
        try {
            statistics.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp(getVisitIgnoreIpList()));
        } catch (Exception e) {
            log.error("数据库IP统计查询失败", e);
            statistics.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
        }
        
        try {
            statistics.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryByYesterday(getVisitIgnoreIpList()));
        } catch (Exception e) {
            log.error("数据库昨日访问统计查询失败", e);
            statistics.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
        }
        
        try {
            Long totalCount = historyInfoMapper.getHistoryCount(getVisitIgnoreIpList());
            statistics.put(CommonConst.IP_HISTORY_COUNT, totalCount != null ? totalCount : 0L);
        } catch (Exception e) {
            log.error("数据库总访问量查询失败", e);
            statistics.put(CommonConst.IP_HISTORY_COUNT, 0L);
        }
        
        return statistics;
    }

    /**
     * 从数据库获取昨日访问统计（按日历天计算）
     */
    private List<Map<String, Object>> getYesterdayStatisticsFromDatabase() {
        try {
            List<Map<String, Object>> yesterdayRecords = historyInfoMapper.getHistoryByYesterday(getVisitIgnoreIpList());
            return yesterdayRecords != null ? yesterdayRecords : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取昨日访问统计失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 基于今天Redis数据生成24小时统计
     */
    private List<Map<String, Object>> generate24HourStatisticsFromToday() {
        try {
            String today = LocalDate.now().toString();
            List<Map<String, Object>> todayRecords = getDailyVisitRecords(today);
            
            if (todayRecords.isEmpty()) {
                log.info("今天暂无访问记录用于24小时统计");
                return new ArrayList<>();
            }
            
            return generate24HourStatisticsFromRecords(todayRecords);
            
        } catch (Exception e) {
            log.error("生成24小时统计失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 基于访问记录生成24小时统计
     */
    private List<Map<String, Object>> generate24HourStatisticsFromRecords(List<Map<String, Object>> records) {
        List<Map<String, Object>> recentHourData = new ArrayList<>();
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

        for (Map<String, Object> record : records) {
            Object timestampObj = record.get("timestamp");
            if (timestampObj != null) {
                try {
                    long timestamp = Long.parseLong(timestampObj.toString());
                    LocalDateTime recordTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                    if (recordTime.isAfter(cutoffTime)) {
                        Map<String, Object> hourData = new HashMap<>();
                        hourData.put("ip", record.get("ip"));
                        hourData.put("user_id", record.get("userId"));
                        hourData.put("nation", record.get("nation"));
                        hourData.put("province", record.get("province"));
                        recentHourData.add(hourData);
                    }
                } catch (Exception e) {
                    log.warn("解析时间戳失败: {}", timestampObj);
                }
            }
        }
        return recentHourData;
    }
    
    /**
     * 获取今日访问数据的实时统计（从Redis）
     * @return 今日访问统计数据
     */
    public Map<String, Object> getTodayVisitStatisticsFromRedis() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String today = java.time.LocalDate.now().toString();
            List<Map<String, Object>> todayRecords = getDailyVisitRecords(today);
            
            if (todayRecords.isEmpty()) {
                log.info("今日暂无访问记录");
                result.put("ip_count_today", 0L);
                result.put("username_today", new ArrayList<>());
                result.put("province_today", new ArrayList<>());
                result.put("ua_today", new ArrayList<>());
                return result;
            }
            
            // 1. 计算今日访问IP数量（去重）
            long ipCountToday = todayRecords.stream()
                .map(record -> (String) record.get("ip"))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
            result.put("ip_count_today", ipCountToday);
            
            // 2. 获取今日访问登录用户列表（只统计登录用户，未登录的不算"用户"）
            Map<String, Long> userVisitCount = todayRecords.stream()
                .filter(java.util.Objects::nonNull)
                .map(record -> {
                    try {
                        Object userIdObj = record.get("userId");
                        if (userIdObj != null) {
                            return Integer.valueOf(userIdObj.toString()).toString();
                        }
                    } catch (Exception e) {
                        log.warn("处理今日用户信息时出错: {}", e.getMessage());
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                    userId -> userId, 
                    java.util.stream.Collectors.counting()
                ));
            
            List<Map<String, Object>> usernameToday = userVisitCount.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("userId", entry.getKey());
                    userInfo.put("visitCount", entry.getValue());
                    return userInfo;
                })
                .sorted((o1, o2) -> Long.valueOf(o2.get("visitCount").toString())
                    .compareTo(Long.valueOf(o1.get("visitCount").toString())))
                .collect(java.util.stream.Collectors.toList());
            result.put("username_today", usernameToday);
            
            // 3. 处理今日省份统计
            List<Map<String, Object>> provinceToday = todayRecords.stream()
                .map(record -> VisitRegionNormalizer.resolveProvinceOrCountry(
                    record.get("nation"),
                    record.get("province"),
                    record.get("city")
                ))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                    province -> province, 
                    java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("province", entry.getKey());
                    map.put("num", entry.getValue());
                    return map;
                })
                .sorted((o1, o2) -> Long.valueOf(o2.get("num").toString())
                    .compareTo(Long.valueOf(o1.get("num").toString())))
                .collect(java.util.stream.Collectors.toList());
            result.put("province_today", provinceToday);

            // 4. 处理今日 UA 统计
            result.put("ua_today", UserAgentClassifier.aggregateVisitRecords(todayRecords));
            
            log.info("获取今日访问统计: IP数量={}, 用户数量={}, 省份数量={}", 
                ipCountToday, usernameToday.size(), provinceToday.size());
            
        } catch (Exception e) {
            log.error("获取今日访问统计失败", e);
            result.put("ip_count_today", 0L);
            result.put("username_today", new ArrayList<>());
            result.put("province_today", new ArrayList<>());
            result.put("ua_today", new ArrayList<>());
        }
        
        return result;
    }

    private String normalizeDedupePart(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private String limitText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed)) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String normalizeLocationField(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || "0".equals(trimmed)
                || "-".equals(trimmed)
                || "未知".equals(trimmed)
                || "unknown".equalsIgnoreCase(trimmed)
                || "reserved".equalsIgnoreCase(trimmed)
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private LocalDateTime resolveVisitRecordTime(Map<String, Object> record) {
        if (record == null) {
            return null;
        }

        Object createTime = record.get("createTime");
        if (createTime != null) {
            try {
                return LocalDateTime.parse(createTime.toString(), VISIT_RECORD_TIME_FORMATTER);
            } catch (Exception e) {
                log.warn("解析访问记录createTime失败: {}", createTime);
            }
        }

        Object timestamp = record.get("timestamp");
        if (timestamp != null) {
            try {
                long millis = Long.parseLong(timestamp.toString());
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            } catch (Exception e) {
                log.warn("解析访问记录timestamp失败: {}", timestamp);
            }
        }

        return null;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    // ==================== 用户界面状态缓存方法 ====================
    
    /**
     * 用户界面状态缓存前缀
     */
    private static final String USER_UI_STATE_PREFIX = "user_ui_state:";
    
    /**
     * 缓存用户界面状态（移动端聊天列表显示状态）
     *
     * @param userId 用户ID
     * @param showBodyLeft 是否显示左侧面板
     */
    public void cacheUserUIState(Integer userId, Boolean showBodyLeft) {
        if (userId != null) {
            String key = USER_UI_STATE_PREFIX + userId;
            Map<String, Object> uiState = new HashMap<>();
            uiState.put("showBodyLeft", showBodyLeft);
            uiState.put("timestamp", System.currentTimeMillis());
            
            // 缓存24小时
            redisUtil.set(key, uiState, 24 * 60 * 60);
        }
    }
    
    /**
     * 获取用户界面状态
     *
     * @param userId 用户ID
     * @return 界面状态Map，包含showBodyLeft等信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserUIState(Integer userId) {
        if (userId == null) return null;
        
        String key = USER_UI_STATE_PREFIX + userId;
        Object cached = redisUtil.get(key);
        
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        
        return null;
    }
    
    /**
     * 删除用户界面状态缓存
     *
     * @param userId 用户ID
     */
    public void removeUserUIState(Integer userId) {
        if (userId != null) {
            String key = USER_UI_STATE_PREFIX + userId;
            redisUtil.del(key);
        }
    }

    // ==================== WebSocket会话缓存方法 ====================
    
    /**
     * WebSocket会话缓存前缀
     */
    private static final String WS_SESSION_PREFIX = "poetize:ws_session:";

    /**
     * 缓存WebSocket会话
     * 
     * @param wsToken WebSocket token
     * @param userId 用户ID
     * @param expireSeconds 过期时间（秒）
     */
    public void cacheWebSocketSession(String wsToken, Integer userId, int expireSeconds) {
        if (wsToken != null && userId != null) {
            String key = WS_SESSION_PREFIX + wsToken;
            redisUtil.set(key, userId, expireSeconds);
            log.info("缓存WebSocket会话: wsTokenPrefix={}, userId={}, 过期时间: {}秒", TokenValidationUtil.getTokenPrefix(wsToken), userId, expireSeconds);
        }
    }

    /**
     * 从WebSocket会话缓存中获取用户ID
     * 
     * @param wsToken WebSocket token
     * @return 用户ID，不存在返回null
     */
    public Integer getUserIdFromWebSocketSession(String wsToken) {
        if (wsToken == null) return null;
        
        String key = WS_SESSION_PREFIX + wsToken;
        Object cached = redisUtil.get(key);
        if (cached instanceof Integer) {
            return (Integer) cached;
        }
        return null;
    }

    /**
     * 删除WebSocket会话缓存
     * 
     * @param wsToken WebSocket token
     */
    public void removeWebSocketSession(String wsToken) {
        if (wsToken != null) {
            String key = WS_SESSION_PREFIX + wsToken;
            redisUtil.del(key);
        }
    }

    /**
     * 延长WebSocket会话有效期
     * 
     * @param wsToken WebSocket token
     * @param expireSeconds 新的过期时间（秒）
     */
    public void extendWebSocketSession(String wsToken, int expireSeconds) {
        if (wsToken != null) {
            String key = WS_SESSION_PREFIX + wsToken;
            redisUtil.expire(key, expireSeconds);
        }
    }
}
