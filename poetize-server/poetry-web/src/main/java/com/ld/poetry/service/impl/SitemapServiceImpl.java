package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.SitemapService;
import com.ld.poetry.utils.ArticleUrlUtil;
import com.redfin.sitemapgenerator.ChangeFreq;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sitemap管理服务实现类
 * 使用sitemapgen4j库来生成标准的sitemap.xml
 * 
 * @author LeapYa
 * @since 2025-09-22
 */
@Service
@Slf4j
public class SitemapServiceImpl implements SitemapService {

    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private ArticleMapper articleMapper;
    
    @Autowired
    private SortMapper sortMapper;
    
    @Autowired
    private com.ld.poetry.service.SearchEnginePushService searchEnginePushService;
    
    @Autowired
    private com.ld.poetry.service.SeoConfigService seoConfigService;
    
    @Autowired
    private com.ld.poetry.service.TranslationService translationService;
    
    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    @Override
    public String generateSitemap() {
        
        // 尝试从缓存获取
        Object cachedSitemap = cacheService.get(CacheConstants.SITEMAP_KEY);
        if (cachedSitemap instanceof String) {
            return (String) cachedSitemap;
        }
        
        // 缓存中不存在，生成新的sitemap
        String sitemap = generateSitemapDirect();
        
        // 缓存sitemap（1小时有效期）
        if (sitemap != null) {
            cacheService.set(CacheConstants.SITEMAP_KEY, sitemap, CacheConstants.SITEMAP_EXPIRE_TIME);
        }
        
        return sitemap;
    }

    @Override
    public String generateSitemapDirect() {
        log.info("开始直接生成sitemap.xml");
        
        try {
            // 获取网站基础URL
            String siteUrl = getSiteBaseUrl();
            if (!StringUtils.hasText(siteUrl)) {
                log.error("无法获取网站URL，sitemap生成失败");
                return null;
            }
            
            log.info("使用网站URL生成sitemap: {}", siteUrl);
            
            // 创建临时目录和文件来生成sitemap
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "sitemap");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            // 创建sitemap生成器
            WebSitemapGenerator wsg = WebSitemapGenerator.builder(siteUrl, tempDir)
                    .build();
            
            // 添加首页
            addHomepageUrl(wsg, siteUrl);
            
            // 添加静态页面
            addStaticPages(wsg, siteUrl);
            
            // 添加分类页面
            addCategoryPages(wsg, siteUrl);
            
            // 添加文章页面
            addArticlePages(wsg, siteUrl);
            
            // 生成sitemap
            wsg.write();
            
            // 读取生成的sitemap文件内容
            java.io.File sitemapFile = new java.io.File(tempDir, "sitemap.xml");
            if (!sitemapFile.exists()) {
                log.error("Sitemap文件生成失败，文件不存在: {}", sitemapFile.getAbsolutePath());
                return null;
            }
            
            // 读取文件内容
            String sitemapContent = java.nio.file.Files.readString(sitemapFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            
            // 清理临时文件
            try {
                sitemapFile.delete();
                // 如果目录为空，也删除目录
                if (tempDir.list().length == 0) {
                    tempDir.delete();
                }
            } catch (Exception e) {
                log.warn("清理临时sitemap文件失败: {}", e.getMessage());
            }
            
            log.info("Sitemap生成成功，包含 {} 个URL", countUrls(sitemapContent));
            recordSitemapUpdateTime();
            return sitemapContent;
            
        } catch (Exception e) {
            log.error("生成sitemap时发生错误", e);
            return null;
        }
    }

    /**
     * 添加首页URL
     */
    private void addHomepageUrl(WebSitemapGenerator wsg, String siteUrl) throws MalformedURLException {
        WebSitemapUrl url = new WebSitemapUrl.Options(siteUrl + "/")
                .changeFreq(ChangeFreq.DAILY)
                .priority(1.0)
                .lastMod(getTodayDate())
                .build();
        wsg.addUrl(url);
    }

    /**
     * 添加静态页面URL
     */
    private void addStaticPages(WebSitemapGenerator wsg, String siteUrl) throws MalformedURLException {
        // 使用今天的日期作为静态页面的lastmod
        java.util.Date today = getTodayDate();
        
        // 定义页面及其优先级：根据页面重要性和用户访问频率设置
        Map<String, Double> pagesPriorities = new HashMap<>();
        pagesPriorities.put("/sort", 0.6);        // 分类列表页 - 导航性质，较重要
        pagesPriorities.put("/user", 0.5);        // 用户页面（登录/个人中心） - 重要功能
        pagesPriorities.put("/weiYan", 0.5);      // 微言页面 - 重要功能
        pagesPriorities.put("/message", 0.5);     // 留言页面 - 重要功能
        pagesPriorities.put("/friends", 0.5);     // 友人帐页面 - 重要功能页面
        pagesPriorities.put("/about", 0.4);       // 关于页面 - 信息页面
        pagesPriorities.put("/music", 0.4);       // 曲乐页面 - 功能页面
        pagesPriorities.put("/favorites", 0.4);   // 收藏夹页面 - 功能页面
        pagesPriorities.put("/travel", 0.4);      // 旅拍页面 - 内容页面
        pagesPriorities.put("/love", 0.3);        // 恋爱笔记 - 个人页面
        pagesPriorities.put("/letter", 0.3);      // 信件页面 - 个人页面
        pagesPriorities.put("/privacy", 0.2);     // 隐私政策 - 法律页面
        
        for (Map.Entry<String, Double> entry : pagesPriorities.entrySet()) {
            String page = entry.getKey();
            Double priority = entry.getValue();
            
            // 检查是否在排除列表中
            if (isPageExcluded(page)) {
                continue;
            }
            
            // 根据页面类型设置更新频率
            ChangeFreq changeFreq = getChangeFreqForPage(page);
            
            WebSitemapUrl url = new WebSitemapUrl.Options(siteUrl + page)
                    .changeFreq(changeFreq)
                    .priority(priority)
                    .lastMod(today)
                    .build();
            wsg.addUrl(url);
        }
    }
    
    /**
     * 根据页面类型获取合适的更新频率
     */
    private ChangeFreq getChangeFreqForPage(String page) {
        switch (page) {
            case "/sort":
            case "/user":
                return ChangeFreq.WEEKLY;   // 导航和用户页面，更新较频繁
            case "/weiYan":
            case "/message":
                return ChangeFreq.DAILY;    // 动态内容页面，更新频繁
            case "/travel":
            case "/favorite":
                return ChangeFreq.WEEKLY;   // 内容和功能页面，定期更新
            case "/about":
                return ChangeFreq.MONTHLY;  // 关于页面，更新较少
            case "/love":
            case "/letter":
                return ChangeFreq.YEARLY;   // 个人页面，基本不变
            case "/privacy":
                return ChangeFreq.YEARLY;   // 法律页面，很少更新
            default:
                return ChangeFreq.WEEKLY;   // 默认值
        }
    }

    /**
     * 添加分类页面URL  
     */
    private void addCategoryPages(WebSitemapGenerator wsg, String siteUrl) throws MalformedURLException {
        List<Sort> sortList = sortMapper.selectList(null);
        if (CollectionUtils.isEmpty(sortList)) {
            return;
        }
        
        // 使用今天的日期作为分类页面的lastmod
        java.util.Date today = getTodayDate();
        
        for (Sort sort : sortList) {
            // 使用SEO友好的URL格式：/sort/1 而不是 /sort?sortId=1
            String sortUrl = siteUrl + "/sort/" + sort.getId();
            
            WebSitemapUrl url = new WebSitemapUrl.Options(sortUrl)
                    .changeFreq(ChangeFreq.WEEKLY)
                    .priority(0.6)  // 分类页面优先级：导航性质，中等重要
                    .lastMod(today)
                    .build();
            wsg.addUrl(url);
        }
        
        log.info("添加了 {} 个分类页面到sitemap", sortList.size());
    }

    /**
     * 添加文章页面URL（包含翻译语言版本）
     */
    private void addArticlePages(WebSitemapGenerator wsg, String siteUrl) throws MalformedURLException {
        List<Article> articles = getVisibleArticles();
        if (CollectionUtils.isEmpty(articles)) {
            log.info("没有可见的文章，跳过文章页面添加");
            return;
        }
        
        int totalUrlCount = 0;
        int originalUrlCount = 0;
        int translationUrlCount = 0;
        
        for (Article article : articles) {
            // 将LocalDateTime转换为Date，只保留日期部分（去除时分秒）
            Date lastModDate = null;
            if (article.getUpdateTime() != null) {
                lastModDate = getDateOnly(article.getUpdateTime());
            } else if (article.getCreateTime() != null) {
                lastModDate = getDateOnly(article.getCreateTime());
            } else {
                lastModDate = getTodayDate(); // 如果都没有，使用今天
            }
            
            // 1. 添加原文章URL（标准格式，不需要语言代码）
            String articleToken = ArticleUrlUtil.resolveToken(article.getId(), article.getArticleSlug());
            String originalArticleUrl = siteUrl + "/article/" + articleToken;
            
            WebSitemapUrl.Options originalUrlOptions = new WebSitemapUrl.Options(originalArticleUrl)
                    .changeFreq(getSitemapChangeFreq())
                    .priority(getSitemapPriority());
            
            if (lastModDate != null) {
                originalUrlOptions.lastMod(lastModDate);
            }
            
            wsg.addUrl(originalUrlOptions.build());
            originalUrlCount++;
            totalUrlCount++;
            
            // 2. 获取并添加文章的翻译语言版本URL
            try {
                // getArticleAvailableLanguages返回数据库中实际存在的翻译语言，不包含源语言
                List<String> translationLanguages = translationService.getArticleAvailableLanguages(article.getId());
                
                if (!CollectionUtils.isEmpty(translationLanguages)) {
                    
                    for (String language : translationLanguages) {
                        // 生成翻译文章的URL格式：/article/语言代码/文章ID或URL别名
                        String translatedArticleUrl = siteUrl + "/article/" + language + "/" + articleToken;
                        
                        WebSitemapUrl.Options translatedUrlOptions = new WebSitemapUrl.Options(translatedArticleUrl)
                                .changeFreq(getSitemapChangeFreq())
                                .priority(getSitemapPriority() * 0.9); // 翻译版本略低于原文优先级
                        
                        if (lastModDate != null) {
                            translatedUrlOptions.lastMod(lastModDate);
                        }
                        
                        wsg.addUrl(translatedUrlOptions.build());
                        translationUrlCount++;
                        totalUrlCount++;
                    }
                    
                } else {
                }
                
            } catch (Exception e) {
                log.warn("获取文章 {} 的翻译语言失败，跳过翻译URL添加: {}", article.getId(), e.getMessage());
                // 翻译URL获取失败不影响主文章URL的添加
            }
        }
        
        log.info("添加了 {} 个文章页面到sitemap - 原文: {} 篇, 翻译: {} 个, 总计: {} 个URL", 
                 articles.size(), originalUrlCount, translationUrlCount, totalUrlCount);
    }

    @Override
    public void updateArticleSitemap(Integer articleId) {
        log.info("文章更新，清除sitemap缓存，文章ID: {}", articleId);
        clearSitemapCache();
    }
    
    @Override
    public void updateSitemapAndPush(String reason) {
        log.info("内容变化触发sitemap更新和推送，原因: {}", reason);
        
        try {
            // 清除缓存，确保下次生成是最新的
            clearSitemapCache();
            
            // 立即重新生成sitemap（不推送）
            String sitemap = generateSitemapDirect();
            if (sitemap != null) {
                // 缓存新的sitemap
                cacheService.set(CacheConstants.SITEMAP_KEY, sitemap, CacheConstants.SITEMAP_EXPIRE_TIME);
                
                // 推送到搜索引擎（使用虚拟线程异步执行，避免阻塞）
                if (searchEnginePushService.isPushEnabled()) {
                    Thread.ofVirtual().name("sitemap-push").start(() -> {
                        try {
                            log.info("内容变化，开始推送sitemap到所有启用的搜索引擎，原因: {}", reason);
                            Map<String, Object> pushResult = searchEnginePushService.pushSitemapToAllEngines();
                            Boolean success = (Boolean) pushResult.get("success");
                            String message = (String) pushResult.get("message");
                            
                            if (Boolean.TRUE.equals(success)) {
                                log.info("内容变化推送sitemap成功: {}, 原因: {}", message, reason);
                            } else {
                                log.warn("内容变化推送sitemap失败: {}, 原因: {}", message, reason);
                            }
                        } catch (Exception e) {
                            log.warn("内容变化推送sitemap时发生错误，原因: {}", reason, e);
                        }
                    });
                } else {
                }
                
                int urlCount = countUrls(sitemap);
                log.info("内容变化sitemap更新完成，包含 {} 个URL，原因: {}", urlCount, reason);
            } else {
                log.warn("内容变化sitemap重新生成失败，原因: {}", reason);
            }
        } catch (Exception e) {
            log.error("内容变化sitemap更新和推送失败，原因: {}", reason, e);
        }
    }

    @Override
    public void clearSitemapCache() {
        cacheService.deleteKey(CacheConstants.SITEMAP_KEY);
        // RSS 与 sitemap 同源（可见文章列表），内容变化时一并失效（含各语言版本缓存）
        cacheService.deleteKeysByPattern(CacheConstants.RSS_KEY + "*");
    }

    private void recordSitemapUpdateTime() {
        cacheService.set(
                CacheConstants.SITEMAP_LAST_UPDATE_KEY,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    @Override
    public List<Article> getVisibleArticles() {
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Article::getViewStatus, true)  // 只获取可见的文章
                    .eq(Article::getDeleted, false)      // 未删除
                    .orderByDesc(Article::getUpdateTime); // 按更新时间降序排列
        
        List<Article> articles = articleMapper.selectList(queryWrapper);
        
        return articles;
    }

    @Override
    public String getSiteBaseUrl() {
        // 使用统一的网站URL获取方法（MailUtil）
        // 优先级：web_info表 > 环境变量SITE_URL > 默认值
        try {
            String siteUrl = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteUrl)) {
                return siteUrl;
            }
        } catch (Exception e) {
            log.warn("获取网站URL失败: {}", e.getMessage());
        }
        
        // 如果所有方法都失败，返回默认值
        String defaultUrl = "http://localhost";
        log.warn("无法获取网站URL，使用默认值: {}", defaultUrl);
        return defaultUrl;
    }



    /**
     * 检查页面是否在排除列表中
     * 支持通配符 * 匹配，多个路径用逗号分隔
     */
    private boolean isPageExcluded(String page) {
        // 从Java SEO配置服务获取排除路径
        String excludeList = null;
        
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            if (seoConfig != null) {
                excludeList = (String) seoConfig.get("sitemap_exclude");
            }
        } catch (Exception e) {
            log.warn("从Java SEO配置获取排除路径失败: {}", e.getMessage());
        }
        
        if (!StringUtils.hasText(excludeList)) {
            return false;
        }
        
        // 按逗号分割排除路径列表
        String[] excludePaths = excludeList.split(",");
        
        for (String excludePath : excludePaths) {
            excludePath = excludePath.trim(); // 去除空格
            if (!StringUtils.hasText(excludePath)) {
                continue;
            }
            
            // 检查是否匹配排除路径
            if (isPathMatched(page, excludePath)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查路径是否匹配排除模式
     * 支持通配符 * 匹配
     */
    private boolean isPathMatched(String path, String pattern) {
        // 如果模式不包含通配符，直接精确匹配
        if (!pattern.contains("*")) {
            return path.equals(pattern);
        }
        
        // 处理通配符匹配
        // 将通配符 * 替换为正则表达式的 .*
        String regexPattern = pattern
            .replace(".", "\\.")  // 转义点号
            .replace("*", ".*");  // 将 * 替换为 .*
        
        try {
            return path.matches(regexPattern);
        } catch (Exception e) {
            log.warn("无效的排除路径模式: {}, 错误: {}", pattern, e.getMessage());
            // 如果正则表达式有问题，回退到精确匹配
            return path.equals(pattern);
        }
    }

    /**
     * 获取文章页面的sitemap优先级配置
     * 注意：此优先级配置仅用于文章页面，其他页面使用固定优先级
     */
    private double getSitemapPriority() {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            if (seoConfig != null) {
                String priority = (String) seoConfig.get("sitemap_priority");
                if (StringUtils.hasText(priority)) {
                    try {
                        double value = Double.parseDouble(priority);
                        return value;
                    } catch (NumberFormatException e) {
                        log.warn("无效的sitemap优先级配置: {}, 使用默认值0.7", priority);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取SEO配置失败，使用默认文章优先级: {}", e.getMessage());
        }
        
        return 0.7; // 文章页面默认优先级
    }

    /**
     * 获取文章页面的sitemap更新频率配置
     * 注意：此更新频率配置仅用于文章页面，其他页面使用固定更新频率
     */
    private ChangeFreq getSitemapChangeFreq() {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            if (seoConfig != null) {
                String changeFrequency = (String) seoConfig.get("sitemap_change_frequency");
                if (StringUtils.hasText(changeFrequency)) {
                    try {
                        ChangeFreq freq = ChangeFreq.valueOf(changeFrequency.toUpperCase());
                        return freq;
                    } catch (IllegalArgumentException e) {
                        log.warn("无效的sitemap更新频率配置: {}, 使用默认值WEEKLY", changeFrequency);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取SEO配置失败，使用默认文章更新频率: {}", e.getMessage());
        }
        
        return ChangeFreq.WEEKLY; // 文章页面默认更新频率
    }

    /**
     * 统计URL数量（简单计算）
     */
    private int countUrls(String sitemapContent) {
        if (!StringUtils.hasText(sitemapContent)) {
            return 0;
        }
        return sitemapContent.split("<url>").length - 1;
    }

    @Override
    public Map<String, Object> pingSitemapToSearchEngines() {
        log.info("开始推送sitemap到搜索引擎");
        
        // 直接使用SearchEnginePushService来处理sitemap推送
        return searchEnginePushService.pushSitemapToAllEngines();
    }

    @Override
    public Map<String, Object> pingSitemapToSearchEnginesDirect() {
        log.info("开始直接推送sitemap到搜索引擎（跳过缓存检查）");
        
        // 直接使用SearchEnginePushService来处理sitemap推送
        return searchEnginePushService.pushSitemapToAllEngines();
    }

    @Override
    public boolean isSearchEnginePingEnabled() {
        // 使用SearchEnginePushService的判断逻辑
        return searchEnginePushService.isPushEnabled();
    }

    /**
     * 获取今天的日期，格式化为sitemap使用的ISO 8601扩展格式
     * 用于sitemap的lastmod字段，符合W3C Datetime标准
     */
    private java.util.Date getTodayDate() {
        return new java.util.Date();
    }

    /**
     * 将LocalDateTime转换为Date对象
     * 用于文章的lastmod字段，保持完整的时间信息以符合ISO 8601格式
     */
    private java.util.Date getDateOnly(java.time.LocalDateTime dateTime) {
        if (dateTime == null) {
            return new java.util.Date();
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
