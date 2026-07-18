package com.ld.poetry.service.prerender;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ResourcePathMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ResourcePath;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.SeoMetaService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.utils.ArticleUrlUtil;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.ResourcePathVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PrerenderService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private PrerenderEngine engine;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private SeoMetaService seoMetaService;

    @Autowired
    private SeoConfigService seoConfigService;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private PrerenderLanguageSupport languageSupport;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private ResourcePathMapper resourcePathMapper;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MailUtil mailUtil;

    public void renderArticle(Integer id, List<String> languages) {
        if (id == null) {
            return;
        }
        renderArticles(List.of(id), languages);
    }

    public void renderArticles(List<Integer> ids, List<String> languages) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        List<String> languagesToRender = resolveLanguages(languages);
        Map<String, Object> seoConfig = getSeoConfig();
        WebInfo webInfo = getWebInfo();
        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        String sourceLanguage = getSourceLanguage();

        for (Integer id : ids) {
            Article article = articleService.getById(id);
            if (article == null || !Boolean.TRUE.equals(article.getViewStatus())) {
                log.warn("跳过预渲染文章，未找到或不可见: {}", id);
                continue;
            }

            for (String lang : languagesToRender) {
                renderSingleArticle(article, lang, sourceLanguage, seoConfig, siteName, baseUrl);
            }
        }
    }

    public void deleteArticle(Integer id) {
        deleteArticle(id, null);
    }

    public void deleteArticle(Integer id, String articleSlug) {
        if (id == null) {
            return;
        }
        engine.deletePage("article/" + id);
        String normalizedSlug = ArticleUrlUtil.normalizeSlug(articleSlug);
        if (ArticleUrlUtil.isValidSlug(normalizedSlug)) {
            engine.deletePage("article/" + normalizedSlug);
        }
    }

    public void renderAdminShellPage() {
        if (!engine.isAdminTemplateAvailable()) {
            log.warn("后台 SPA 模板文件不存在，跳过后台入口预渲染");
            return;
        }

        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        String sourceLanguage = getSourceLanguage();
        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        String title = "后台管理 - " + siteName;
        String description = "后台管理系统";
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(webInfo.getAvatar(), stringValue(seoConfig.get("og_image"))), baseUrl);
        Map<String, Object> meta = createWebsiteMeta(title, description, "后台管理,管理系统", baseUrl,
                "/admin", "website", ogImage, webInfo, seoConfig);
        meta.put("robots", "noindex,nofollow,noarchive");

        String html = engine.buildAdminShellPage(PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content("")
                .lang(sourceLanguage)
                .pageType("admin")
                .build());
        engine.writeAdminShellPage(html);
    }

    public void renderHomePage() {
        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        List<Sort> sortInfo = getSortInfo();
        List<ArticleVO> recentArticles = listArticles(8, null, null, null);
        List<ArticleVO> recommendArticles = listArticles(5, Boolean.TRUE, null, null);
        String sourceLanguage = getSourceLanguage();

        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        // 首页 <title> 使用 homeTitle，为空时回退到 webTitle
        String title = firstNonBlank(webInfo.getHomeTitle(), webInfo.getWebTitle());
        String description = firstNonBlank(stringValue(seoConfig.get("site_description")),
                siteName + " - 个人博客网站，分享技术文章、生活感悟。");
        String keywords = firstNonBlank(stringValue(seoConfig.get("site_keywords")), "博客,个人网站,技术分享");
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(stringValue(seoConfig.get("og_image")), webInfo.getAvatar()), baseUrl);

        Map<String, Object> meta = new LinkedHashMap<>(seoMetaService.generateSiteMeta(sourceLanguage));
        if (meta.isEmpty() || meta.containsKey("error") || !meta.containsKey("title")) {
            meta = createWebsiteMeta(title, description, keywords, baseUrl, "", "website", ogImage, webInfo, seoConfig);
        } else {
            meta.putIfAbsent("twitter:card", firstNonBlank(stringValue(seoConfig.get("twitter_card")), "summary_large_image"));
        }

        String homeContent = "<div class=\"home-prerender\"><div class=\"home-hero\"><h1>" + text(firstNonBlank(webInfo.getWebName(), webInfo.getWebTitle(), siteName))
                + "</h1><p>" + text(description)
                + "</p></div><div class=\"home-categories\"><h2>文章分类</h2><ul>"
                + sortInfo.stream()
                        .map(sort -> "<li><a href=\"/sort/" + sort.getId() + "\" title=\""
                                + attr(firstNonBlank(sort.getSortDescription(), sort.getSortName())) + "\">"
                                + text(sort.getSortName()) + "</a></li>")
                        .collect(Collectors.joining())
                + "</ul></div>"
                + (recommendArticles.isEmpty() ? "" : "<div class=\"home-recommend-articles\"><h2>🔥推荐文章</h2><ul>"
                + recommendArticles.stream().map(article -> "<li><a href=\"" + articlePath(article) + "\" title=\""
                                + attr(article.getArticleTitle()) + "\">"
                                + (StringUtils.hasText(article.getArticleCover())
                                        ? "<img src=\"" + attr(article.getArticleCover()) + "\" alt=\"" + attr(article.getArticleTitle())
                                        + "\" width=\"120\" height=\"80\" loading=\"lazy\">"
                                        : "")
                                + "<div class=\"article-info\"><h3>" + text(article.getArticleTitle()) + "</h3>"
                                + (StringUtils.hasText(article.getSummary()) ? "<p>" + text(article.getSummary()) + "</p>" : "")
                                + "<time>" + text(formatDate(article.getCreateTime())) + "</time></div></a></li>")
                        .collect(Collectors.joining())
                + "</ul></div>")
                + "<div class=\"home-recent-articles\"><h2>最新文章</h2><ul>"
                + recentArticles.stream().map(article -> "<li><a href=\"" + articlePath(article) + "\" title=\""
                                + attr(article.getArticleTitle()) + "\"><h3>" + text(article.getArticleTitle()) + "</h3>"
                                + (StringUtils.hasText(article.getSummary()) ? "<p>" + text(article.getSummary()) + "</p>" : "")
                                + "<time>" + text(formatDate(article.getCreateTime())) + "</time></a></li>")
                        .collect(Collectors.joining())
                + "</ul></div></div>"
                // 完整页脚（版权 + 友链 + 联系方式）内联到预渲染 HTML，供爬虫/审核员直接看到
                + buildFooterHtml(webInfo);

        writePage("home", PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content(homeContent)
                .lang(sourceLanguage)
                .pageType("home")
                .build());
    }

    /**
     * 构建页脚 HTML 片段，内联到预渲染首页。
     * <p>包含版权信息、友链（CDN/云服务商，从 sysConfig['footer.friendLinks'] 读取）、联系方式。
     * <p>爬虫和友链审核员可直接从 HTML 源码看到这些链接，无需执行 JS。
     */
    private String buildFooterHtml(WebInfo webInfo) {
        int year = java.time.Year.now().getValue();
        String siteName = firstNonBlank(webInfo.getWebName(), webInfo.getWebTitle(), "本站");
        String email = firstNonBlank(webInfo.getEmail(), "admin@poetize.cn");
        List<FriendLinkItem> friendLinks = getFooterFriendLinks();

        StringBuilder sb = new StringBuilder("<footer class=\"prerender-footer\"><div class=\"prerender-copyright\">");
        sb.append("<span>© ").append(year).append(" ").append(text(siteName)).append("</span>");
        sb.append("<span>保留所有权利</span>");
        sb.append("<a href=\"/privacy\">隐私政策</a>");
        sb.append("</div>");

        if (!friendLinks.isEmpty()) {
            sb.append("<div class=\"prerender-friend-links\"><span>本站由</span>");
            for (FriendLinkItem link : friendLinks) {
                sb.append("<a href=\"").append(attr(link.url))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">")
                        .append(text(link.name)).append("</a>");
            }
            sb.append("<span>提供加速与云服务</span></div>");
        }
        sb.append("<div class=\"prerender-contact\">本站内容均为原创或合法转载，如有侵权请通过邮箱：")
                .append(text(email)).append(" 与我们联系，确认后将立即删除</div>");
        sb.append("</footer>");
        return sb.toString();
    }

    /**
     * 从 sysConfig['footer.friendLinks'] 读取并解析友链列表。
     * <p>配置值为 JSON 数组字符串：[{ name, url, logo?, ariaLabel? }]。
     * <p>读取失败或未配置时返回空列表，页脚不渲染友链区。
     */
    private List<FriendLinkItem> getFooterFriendLinks() {
        Map<String, String> sysConfigMap = cacheService.getCachedPublicSysConfigMap();
        if (sysConfigMap == null) return List.of();
        String raw = sysConfigMap.get("footer.friendLinks");
        if (!StringUtils.hasText(raw)) return List.of();
        try {
            JsonNode root = jsonMapper.readTree(raw);
            if (!root.isArray()) return List.of();
            List<FriendLinkItem> result = new ArrayList<>();
            for (JsonNode item : root) {
                String name = item.path("name").asText("");
                String url = item.path("url").asText("");
                if (!name.isBlank() && !url.isBlank()) {
                    result.add(new FriendLinkItem(name.trim(), url.trim()));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 footer.friendLinks 配置失败，已忽略: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 友链项，仅保留预渲染需要的字段（logo 在静态 HTML 中省略以保持轻量）
     */
    private static class FriendLinkItem {
        final String name;
        final String url;
        FriendLinkItem(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public void renderFriendsPage() {
        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        Map<String, List<ResourcePathVO>> friends = listGroupedResources(CommonConst.RESOURCE_PATH_TYPE_FRIEND, true);
        ResourcePathVO siteInfo = getSiteInfo();
        String sourceLanguage = getSourceLanguage();

        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        String title = "友人帐 - " + siteName;
        String description = "留下你的网站吧，让我们建立友谊的桥梁";
        String keywords = firstNonBlank(stringValue(seoConfig.get("site_keywords")), "博客,个人网站,技术分享") + ",友人帐,友链,朋友,网站交换";
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(webInfo.getAvatar(), stringValue(seoConfig.get("og_image"))), baseUrl);

        Map<String, Object> meta = createWebsiteMeta(title, description, keywords, baseUrl, "/friends", "website", ogImage, webInfo, seoConfig);

        String eliteFriendsKey = friends.containsKey("🌟青出于蓝") ? "🌟青出于蓝" : (friends.containsKey("♥️青出于蓝") ? "♥️青出于蓝" : null);
        String regularFriendsKey = friends.containsKey("🥇友情链接") ? "🥇友情链接" : null;

        String friendsContent = "<div class=\"friends-prerender\"><h1>友人帐</h1><p>留下你的网站吧，让我们建立友谊的桥梁</p>"
                + renderFriendGroup("🌟青出于蓝", eliteFriendsKey == null ? List.of() : friends.get(eliteFriendsKey))
                + renderFriendGroup("🥇友情链接", regularFriendsKey == null ? List.of() : friends.get(regularFriendsKey))
                + "<h2>✉️ 申请方式</h2><div><p>1. 添加本站链接</p><p>首先将本站链接添加至您的网站，信息如下：</p><p>网站名称："
                + text(firstNonBlank(siteInfo.getTitle(), siteName))
                + "</p><p>网站地址：" + text(baseUrl)
                + "</p><p>网站描述：" + text(firstNonBlank(siteInfo.getIntroduction(), webInfo.getWebTitle(), siteName))
                + "</p><p>网站封面：" + text(firstNonBlank(siteInfo.getRemark(), siteInfo.getCover(), ""))
                + "</p></div><div><p>2. 提交申请</p><p>点击下方信封 📮 填写您的网站信息提交申请</p></div><div><p>3. 等待审核</p><p>审核通过后将会添加至该页面中，请耐心等待</p></div><h2>⚠️ 温馨提示</h2><ul><li>不会添加带有广告营销和没有实质性内容的友链</li><li>申请之前请将本网站添加为您的友链</li><li>审核时间一般在一周内，请耐心等待</li></ul>"
                + (eliteFriendsKey == null && regularFriendsKey == null ? "<p>暂无友链，欢迎交换友链</p>" : "")
                + "<div id=\"dynamic-content-placeholder\" style=\"display:none;\"><script>window.PRERENDER_DATA = {type: 'friends',lang: '" + sourceLanguage + "',timestamp: "
                + System.currentTimeMillis() + "};</script></div></div>";

        writePage("friends", PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content(friendsContent)
                .lang(sourceLanguage)
                .pageType("friends")
                .build());
    }

    public void renderMusicPage() {
        String sourceLanguage = getSourceLanguage();
        renderPlaceholderPage("music", "music", "曲乐", "一曲肝肠断，天涯何处觅知音",
                "曲乐,音乐,娱乐,音频",
                "<div class=\"music-prerender\"><div class=\"music-hero\"><h1>曲乐</h1><p>一曲肝肠断，天涯何处觅知音</p></div><div class=\"music-main\"><div class=\"music-placeholder\"><p>音乐内容将在页面加载完成后显示</p></div></div><div id=\"dynamic-content-placeholder\" style=\"display:none;\"><script>window.PRERENDER_DATA = {type: 'music',lang: '" + sourceLanguage + "',timestamp: "
                        + System.currentTimeMillis() + "};</script></div></div>");
    }

    public void renderFavoritesPage() {
        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        Map<String, List<ResourcePathVO>> collects = listGroupedResources(CommonConst.RESOURCE_PATH_TYPE_FAVORITES, false);
        String sourceLanguage = getSourceLanguage();

        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        String title = "收藏夹 - " + siteName;
        String description = "将本网站添加到您的收藏夹吧，发现更多精彩内容";
        String keywords = firstNonBlank(stringValue(seoConfig.get("site_keywords")), "博客,个人网站,技术分享") + ",收藏夹,书签,网站收藏,精选网站";
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(webInfo.getAvatar(), stringValue(seoConfig.get("og_image"))), baseUrl);

        Map<String, Object> meta = createWebsiteMeta(title, description, keywords, baseUrl, "/favorites", "website", ogImage, webInfo, seoConfig);

        String favoritesContent = "<div class=\"favorites-prerender\"><div class=\"favorites-hero\"><h1>收藏夹</h1><p>将本网站添加到您的收藏夹吧，发现更多精彩内容</p></div><div class=\"favorites-main\">"
                + (collects.isEmpty() ? "<p>暂无收藏夹</p>"
                        : collects.entrySet().stream()
                                .map(entry -> "<div class=\"collect-category\"><h3>" + text(entry.getKey()) + "</h3><ul>"
                                        + entry.getValue().stream().map(item -> "<li><a href=\"" + attr(item.getUrl())
                                                        + "\" target=\"_blank\" rel=\"noopener\" title=\""
                                                        + attr(item.getIntroduction()) + "\"><img src=\"" + attr(item.getCover())
                                                        + "\" alt=\"" + attr(item.getTitle())
                                                        + "\" width=\"32\" height=\"32\" loading=\"lazy\"><span>"
                                                        + text(item.getTitle()) + "</span><small>" + text(item.getIntroduction())
                                                        + "</small></a></li>")
                                                .collect(Collectors.joining())
                                        + "</ul></div>")
                                .collect(Collectors.joining()))
                + "</div><div id=\"dynamic-content-placeholder\" style=\"display:none;\"><script>window.PRERENDER_DATA = {type: 'favorites',lang: '" + sourceLanguage + "',timestamp: "
                + System.currentTimeMillis() + "};</script></div></div>";

        writePage("favorites", PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content(favoritesContent)
                .lang(sourceLanguage)
                .pageType("favorites")
                .build());
    }

    public void renderSortIndexPage() {
        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        List<Sort> sortList = getSortInfo();
        String sourceLanguage = getSourceLanguage();

        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        String title = "文章分类 - " + siteName;
        String description = "浏览所有文章分类，找到您感兴趣的内容主题";
        String keywords = firstNonBlank(stringValue(seoConfig.get("site_keywords")), "博客,个人网站,技术分享") + ",文章分类,分类列表,内容导航";
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(stringValue(seoConfig.get("og_image")), webInfo.getAvatar()), baseUrl);

        Map<String, Object> meta = createWebsiteMeta(title, description, keywords, baseUrl, "/sort", "website", ogImage, webInfo, seoConfig);

        String defaultSortContent = "<div class=\"sort-list-prerender\"><div class=\"sort-hero\"><h1>文章分类</h1><p>探索不同主题的文章内容</p></div>"
                + (sortList.isEmpty() ? "<p class=\"no-categories\">暂无分类</p>"
                        : "<div class=\"sort-list-warp\">" + sortList.stream().map(sort -> "<div class=\"sort-card\"><a href=\"/sort/"
                                        + sort.getId() + "\" title=\""
                                        + attr(firstNonBlank(sort.getSortDescription(), sort.getSortName())) + "\"><div class=\"sort-card-header\"><h3>"
                                        + text(sort.getSortName()) + "</h3><span class=\"sort-card-count\">"
                                        + defaultInt(sort.getCountOfSort()) + " 篇</span></div><p class=\"sort-card-desc\">"
                                        + text(firstNonBlank(sort.getSortDescription(), "暂无描述")) + "</p>"
                                        + (CollectionUtils.isEmpty(sort.getLabels()) ? ""
                                                : "<div class=\"sort-card-labels\">"
                                                + sort.getLabels().stream().limit(5).map(label -> "<span class=\"label-tag\">"
                                                                + text(label.getLabelName()) + "</span>")
                                                        .collect(Collectors.joining())
                                                + (sort.getLabels().size() > 5 ? "<span class=\"sort-card-more\">+"
                                                        + (sort.getLabels().size() - 5) + "</span>" : "")
                                                + "</div>")
                                        + "</a></div>")
                                .collect(Collectors.joining()) + "</div>")
                + "<div id=\"dynamic-content-placeholder\" style=\"display:none;\"><script>window.PRERENDER_DATA = {type: 'sort-list',lang: '" + sourceLanguage + "',timestamp: "
                + System.currentTimeMillis() + "};</script></div></div>";

        writePage("sort", PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content(defaultSortContent)
                .lang(sourceLanguage)
                .pageType("sort-list")
                .build());
    }

    public void renderCategoryPage(Integer sortId) {
        renderCategoryPage(sortId, null);
    }

    public void renderCategoryPage(Integer sortId, Integer labelId) {
        if (sortId == null) {
            return;
        }

        Sort sortData = getSortInfo().stream()
                .filter(sort -> Objects.equals(sort.getId(), sortId))
                .findFirst()
                .orElse(null);

        if (sortData == null) {
            throw new IllegalStateException("分类不存在: " + sortId);
        }

        renderCategoryPage(sortData, labelId);
    }

    public void renderAllCategoryPages(List<Integer> sortIds) {
        if (CollectionUtils.isEmpty(sortIds)) {
            return;
        }

        Map<Integer, Sort> sortMap = getSortInfo().stream()
                .collect(Collectors.toMap(Sort::getId, sort -> sort, (left, right) -> left, LinkedHashMap::new));
        for (Integer sortId : sortIds) {
            Sort sort = sortMap.get(sortId);
            if (sort != null) {
                renderCategoryPage(sort, null);
            }
        }
    }

    public void renderAboutPage() {
        WebInfo webInfo = getWebInfo();
        String siteName = getSiteName(webInfo);
        String description = firstNonBlank(webInfo.getWebTitle(), siteName + " 的站点介绍");
        String content = "<div class=\"about-prerender\"><div class=\"about-hero\"><h1>关于" + text(siteName)
                + "</h1><p>" + text(description)
                + "</p></div><div class=\"about-content\"><div class=\"about-info\"><div class=\"about-text\">"
                + text(description)
                + "</div><div class=\"contact-info\"><h3>联系方式</h3><p>邮箱: "
                + text(firstNonBlank(webInfo.getEmail(), "暂未提供")) + "</p></div></div></div></div>";
        renderSimplePageWithTitle("about", "about", "关于 - " + siteName, description, "关于," + siteName + ",博客,个人简介", content, "website");
    }

    public void renderMessagePage() {
        renderPlaceholderPage("message", "message", "留言板", "欢迎在这里留下您的宝贵意见和建议", "留言,反馈,建议",
                "<div class=\"message-prerender\"><div class=\"message-hero\"><h1>留言板</h1><p>欢迎在这里留下您的宝贵意见和建议</p></div><div class=\"message-form-placeholder\"><p>留言功能将在页面加载完成后可用</p></div></div>");
    }

    public void renderWeiYanPage() {
        renderPlaceholderPage("weiYan", "weiyan", "微言", "记录生活点滴，分享心情随笔", "微言,动态,心情,随笔",
                "<div class=\"weiyan-prerender\"><div class=\"weiyan-hero\"><h1>微言</h1><p>记录生活点滴，分享心情随笔</p></div><div class=\"weiyan-list-placeholder\"><p>动态内容将在页面加载完成后显示</p></div></div>");
    }

    public void renderLovePage() {
        renderPlaceholderPage("love", "love", "恋爱记录", "记录美好的爱情时光", "恋爱,爱情,记录",
                "<div class=\"love-prerender\"><div class=\"love-hero\"><h1>恋爱记录</h1><p>记录美好的爱情时光</p></div><div class=\"love-timeline-placeholder\"><p>爱情时光轴将在页面加载完成后显示</p></div></div>");
    }

    public void renderTravelPage() {
        renderPlaceholderPage("travel", "travel", "旅行日记", "记录旅途中的美好时光和所见所闻", "旅行,日记,游记",
                "<div class=\"travel-prerender\"><div class=\"travel-hero\"><h1>旅行日记</h1><p>记录旅途中的美好时光和所见所闻</p></div><div class=\"travel-list-placeholder\"><p>旅行记录将在页面加载完成后显示</p></div></div>");
    }

    public void renderPrivacyPage() {
        renderSimplePageWithTitle("privacy", "privacy", "隐私政策 - " + getSiteName(getWebInfo()), "了解我们如何保护您的个人隐私信息",
                "隐私政策,隐私保护,个人信息",
                "<div class=\"privacy-prerender\"><div class=\"privacy-hero\"><h1>隐私政策</h1><p>了解我们如何保护您的个人隐私信息</p></div><div class=\"privacy-content\"><p>我们重视您的隐私，并致力于保护您的个人信息安全。</p><p>详细的隐私政策内容将在页面加载完成后显示。</p></div></div>",
                "article");
    }

    public void renderLetterPage() {
        renderPlaceholderPage("letter", "letter", "信件", "查看和管理您的信件", "信件,私信,消息",
                "<div class=\"letter-prerender\"><div class=\"letter-hero\"><h1>信件</h1><p>查看和管理您的信件</p></div><div class=\"letter-list-placeholder\"><p>信件内容将在页面加载完成后显示</p></div></div>");
    }

    public boolean isTemplateAvailable() {
        return engine.isTemplateAvailable();
    }

    public void clearTemplateCache() {
        engine.clearTemplateCache();
    }

    public void deletePage(String pageType) {
        if (!StringUtils.hasText(pageType)) {
            return;
        }
        engine.deletePage(pageType);
    }

    public void deleteSortIndexPage() {
        engine.deleteIndexFiles("sort");
    }

    public void deleteCategoryPage(Integer sortId) {
        deleteCategoryPage(sortId, null);
    }

    public void deleteCategoryPage(Integer sortId, Integer labelId) {
        if (sortId == null) {
            return;
        }
        String subPath = labelId == null ? "sort/" + sortId : "sort/" + sortId + "/" + labelId;
        engine.deletePage(subPath);
    }

    private void renderSingleArticle(Article article, String lang, String sourceLanguage, Map<String, Object> seoConfig, String siteName, String baseUrl) {
        String articlePath = ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug());
        String articleRoute = ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug(), lang, sourceLanguage);
        String articleTitle = article.getArticleTitle();
        String content = article.getArticleContent();

        if (!sourceLanguage.equals(lang)) {
            Map<String, String> translation = translationService.getArticleTranslation(article.getId(), lang);
            if (translation != null && !translation.isEmpty()) {
                articleTitle = firstNonBlank(translation.get("title"), articleTitle);
                content = firstNonBlank(translation.get("content"), content);
            }
        }

        content = applyAnonymousPaywall(article, content);

        Map<String, Object> meta = new LinkedHashMap<>(seoMetaService.generateArticleMeta(article.getId(), lang));
        enrichSeoMeta(meta, seoConfig, baseUrl);
        meta.putIfAbsent("author", firstNonBlank(stringValue(seoConfig.get("default_author")), siteName));
        meta.putIfAbsent("keywords", firstNonBlank(stringValue(seoConfig.get("site_keywords")), siteName));
        meta.putIfAbsent("canonical", buildUrl(baseUrl, articleRoute));
        if (!org.springframework.util.StringUtils.hasText(stringValue(meta.get("og:site_name")))) {
            meta.put("og:site_name", siteName);
        }
        meta.putIfAbsent("og:url", buildUrl(baseUrl, articleRoute));

        String socialImage = ensureAbsoluteImageUrl(firstNonBlank(stringValue(meta.get("og:image")), stringValue(seoConfig.get("og_image"))), baseUrl);
        if (StringUtils.hasText(socialImage)) {
            meta.put("og:image", socialImage);
            meta.put("twitter:image", socialImage);
        }
        meta.putIfAbsent("twitter:card", firstNonBlank(stringValue(seoConfig.get("twitter_card")), "summary_large_image"));

        String pageTitle = firstNonBlank(articleTitle, stringValue(meta.get("title")), siteName) + " - " + siteName;
        String contentHtml = engine.renderMarkdown(content);
        String fullContent = "<header><h1 class=\"article-main-title\">" + text(firstNonBlank(articleTitle, stringValue(meta.get("title")), siteName))
                + "</h1></header>\n<section>" + contentHtml + "</section>\n<footer>"
                + (article.getCreateTime() != null ? "<time datetime=\"" + article.getCreateTime() + "\">" + text(formatDate(article.getCreateTime())) + "</time>" : "")
                + "</footer>";

        String outputLang = sourceLanguage.equals(lang) ? null : lang;

        String html = engine.buildPage(PrerenderPageData.builder()
                .title(pageTitle)
                .articleTitle(articleTitle)
                .meta(meta)
                .content(fullContent)
                .lang(lang)
                .pageType("article")
                .build());
        if (ArticleUrlUtil.isValidSlug(article.getArticleSlug())) {
            engine.deletePage("article/" + article.getId());
        }
        engine.writePage(articlePath.replaceFirst("^/", ""), outputLang, html);
    }

    private void renderCategoryPage(Sort sortData, Integer labelId) {
        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        List<ArticleVO> articles = listArticles(20, null, sortData.getId(), labelId);
        String sourceLanguage = getSourceLanguage();

        String siteName = getSiteName(webInfo);
        String baseUrl = getBaseUrl(webInfo);
        String title = sortData.getSortName() + " - " + siteName;
        String description = firstNonBlank(sortData.getSortDescription(), sortData.getSortName() + "分类下的所有文章");
        String keywords = firstNonBlank(stringValue(seoConfig.get("site_keywords")), "博客,个人网站,技术分享") + "," + sortData.getSortName() + ",文章分类,博客";
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(stringValue(seoConfig.get("og_image")), webInfo.getAvatar()), baseUrl);

        Map<String, Object> meta = new LinkedHashMap<>(seoMetaService.generateCategoryMeta(sortData.getId(), sourceLanguage));
        meta.put("description", description);
        meta.put("keywords", keywords);
        meta.put("canonical", buildUrl(baseUrl, "/sort/" + sortData.getId() + (labelId != null ? "?labelId=" + labelId : "")));
        meta.put("og:url", buildUrl(baseUrl, "/sort/" + sortData.getId() + (labelId != null ? "?labelId=" + labelId : "")));
        meta.put("og:image", ogImage);
        meta.put("og:title", title);
        meta.put("og:description", description);
        meta.put("og:type", "website");
        meta.put("twitter:card", firstNonBlank(stringValue(seoConfig.get("twitter_card")), "summary"));
        meta.put("twitter:title", title);
        meta.put("twitter:description", description);
        meta.put("twitter:image", ogImage);
        enrichSeoMeta(meta, seoConfig, baseUrl);

        String sortContent = "<div class=\"sort-prerender\"><div class=\"sort-hero\"><h1>" + text(sortData.getSortName())
                + "</h1><p>" + text(firstNonBlank(sortData.getSortDescription(), ""))
                + "</p></div><div class=\"sort-articles\"><h2>文章列表</h2>"
                + (articles.isEmpty() ? "<p>暂无文章</p>"
                        : "<ul class=\"article-list\">" + articles.stream().map(article -> "<li class=\"article-item\"><a href=\""
                                        + articlePath(article) + "\" title=\"" + attr(article.getArticleTitle()) + "\">"
                                        + (StringUtils.hasText(article.getArticleCover())
                                                ? "<img src=\"" + attr(article.getArticleCover()) + "\" alt=\"" + attr(article.getArticleTitle()) + "\" loading=\"lazy\">"
                                                : "")
                                        + "<div class=\"article-info\"><h3>" + text(article.getArticleTitle()) + "</h3>"
                                        + (StringUtils.hasText(article.getSummary()) ? "<p>" + text(article.getSummary()) + "</p>" : "")
                                        + "<div class=\"article-meta\"><time>" + text(formatDate(article.getCreateTime()))
                                        + "</time><span class=\"view-count\">阅读 " + defaultInt(article.getViewCount()) + "</span>"
                                        + (article.getLabel() != null ? "<span class=\"label\">" + text(article.getLabel().getLabelName()) + "</span>" : "")
                                        + "</div></div></a></li>")
                                .collect(Collectors.joining()) + "</ul>")
                + "</div>"
                + (CollectionUtils.isEmpty(sortData.getLabels()) ? "" : "<div class=\"sort-labels\"><h3>标签筛选</h3><ul>"
                + sortData.getLabels().stream().map(label -> "<li><a href=\"/sort/" + sortData.getId() + "?labelId=" + label.getId()
                                + "\" title=\"" + attr(firstNonBlank(label.getLabelDescription(), label.getLabelName())) + "\">"
                                + text(label.getLabelName()) + " (" + defaultInt(label.getCountOfLabel()) + ")</a></li>")
                        .collect(Collectors.joining()) + "</ul></div>")
                + "<div id=\"dynamic-content-placeholder\" style=\"display:none;\"><script>window.PRERENDER_DATA = {type: 'sort',sortId: "
                + sortData.getId() + ",labelId: " + (labelId == null ? "null" : labelId) + ",lang: '" + sourceLanguage + "',timestamp: "
                + System.currentTimeMillis() + "};</script></div></div>";

        String subPath = labelId == null ? "sort/" + sortData.getId() : "sort/" + sortData.getId() + "/" + labelId;
        writePage(subPath, PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content(sortContent)
                .lang(sourceLanguage)
                .pageType("sort")
                .build());
    }

    private void renderPlaceholderPage(String route, String pageType, String heading, String description, String keywordSuffix, String content) {
        renderSimplePageWithTitle(route, pageType, heading + " - " + getSiteName(getWebInfo()), description, keywordSuffix + "," + getSiteName(getWebInfo()), content, "website", "noindex, follow");
    }

    private void renderSimplePageWithTitle(String route, String pageType, String title, String description, String keywords, String content, String ogType) {
        renderSimplePageWithTitle(route, pageType, title, description, keywords, content, ogType, null);
    }

    private void renderSimplePageWithTitle(String route, String pageType, String title, String description, String keywords, String content, String ogType, String robots) {
        WebInfo webInfo = getWebInfo();
        Map<String, Object> seoConfig = getSeoConfig();
        String sourceLanguage = getSourceLanguage();
        String baseUrl = getBaseUrl(webInfo);
        String ogImage = ensureAbsoluteImageUrl(firstNonBlank(stringValue(seoConfig.get("og_image")), webInfo.getAvatar()), baseUrl);
        Map<String, Object> meta = createWebsiteMeta(title, description, keywords, baseUrl, "/" + route, ogType, ogImage, webInfo, seoConfig);
        if (StringUtils.hasText(robots)) {
            meta.put("robots", robots);
        }

        writePage(route, PrerenderPageData.builder()
                .title(title)
                .meta(meta)
                .content(content)
                .lang(sourceLanguage)
                .pageType(pageType)
                .build());
    }

    private void writePage(String subPath, PrerenderPageData pageData) {
        String html = engine.buildPage(pageData);
        String outputLanguage = getSourceLanguage().equals(pageData.getLang()) ? null : pageData.getLang();
        engine.writePage(subPath, outputLanguage, html);
    }

    private Map<String, Object> createWebsiteMeta(String title, String description, String keywords, String baseUrl,
                                                  String routePath, String ogType, String ogImage, WebInfo webInfo,
                                                  Map<String, Object> seoConfig) {
        String canonicalUrl = StringUtils.hasText(routePath) ? buildUrl(baseUrl, routePath) : baseUrl;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("description", description);
        meta.put("keywords", keywords);
        meta.put("author", firstNonBlank(stringValue(seoConfig.get("default_author")), getSiteName(webInfo)));
        meta.put("canonical", canonicalUrl);
        meta.put("og:title", title);
        meta.put("og:description", description);
        meta.put("og:type", ogType);
        meta.put("og:url", canonicalUrl);
        meta.put("og:image", ogImage);
        meta.put("og:site_name", getSiteName(webInfo));
        meta.put("twitter:card", firstNonBlank(stringValue(seoConfig.get("twitter_card")), "summary"));
        meta.put("twitter:title", title);
        meta.put("twitter:description", description);
        meta.put("twitter:image", ogImage);
        enrichSeoMeta(meta, seoConfig, baseUrl);
        return meta;
    }

    private void enrichSeoMeta(Map<String, Object> meta, Map<String, Object> seoConfig, String baseUrl) {
        addSeoIconFields(meta, seoConfig, baseUrl);
        addSeoCommonFields(meta, seoConfig);
    }

    private void addSeoIconFields(Map<String, Object> meta, Map<String, Object> seoConfig, String baseUrl) {
        List<String> iconFields = List.of("site_icon", "apple_touch_icon", "site_icon_192", "site_icon_512", "site_logo");
        for (String field : iconFields) {
            if (!StringUtils.hasText(stringValue(meta.get(field))) && StringUtils.hasText(stringValue(seoConfig.get(field)))) {
                meta.put(field, ensureAbsoluteImageUrl(stringValue(seoConfig.get(field)), baseUrl));
            }
        }
    }

    private void addSeoCommonFields(Map<String, Object> meta, Map<String, Object> seoConfig) {
        List<String> verificationFields = List.of(
                "google_site_verification", "baidu_site_verification", "bing_site_verification",
                "yandex_site_verification", "sogou_site_verification", "so_site_verification",
                "shenma_site_verification", "yahoo_site_verification", "duckduckgo_site_verification",
                "twitter_site", "twitter_creator", "fb_app_id", "fb_page_url",
                "linkedin_company_id", "pinterest_verification", "pinterest_description",
                "wechat_miniprogram_id", "wechat_miniprogram_path", "qq_miniprogram_path",
                "custom_head_code");

        for (String field : verificationFields) {
            if (!StringUtils.hasText(stringValue(meta.get(field))) && StringUtils.hasText(stringValue(seoConfig.get(field)))) {
                meta.put(field, stringValue(seoConfig.get(field)));
            }
        }

        if (!StringUtils.hasText(stringValue(meta.get("robots"))) && StringUtils.hasText(stringValue(seoConfig.get("robots_default")))) {
            meta.put("robots", stringValue(seoConfig.get("robots_default")));
        }
    }

    private List<String> resolveLanguages(List<String> languages) {
        return languageSupport.resolveLanguages(languages);
    }

    private WebInfo getWebInfo() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            webInfo = webInfoService.lambdaQuery().last("limit 1").one();
            if (webInfo != null) {
                cacheService.cacheWebInfo(webInfo);
            }
        }

        if (webInfo == null) {
            throw new IllegalStateException("网站信息不存在，无法执行预渲染");
        }

        if (!StringUtils.hasText(webInfo.getSiteAddress())) {
            webInfo.setSiteAddress(mailUtil.getSiteUrl());
        }
        return webInfo;
    }

    private Map<String, Object> getSeoConfig() {
        Map<String, Object> seoConfig = new LinkedHashMap<>(seoConfigService.getSeoConfigAsJson());
        seoConfig.put("site_address", mailUtil.getSiteUrl());
        return seoConfig;
    }

    private List<Sort> getSortInfo() {
        List<Sort> sorts = new ArrayList<>(commonQuery.getSortInfo());
        sorts.sort(Comparator.comparing((Sort sort) -> defaultInt(sort.getSortType()))
                .thenComparing(sort -> defaultInt(sort.getPriority()))
                .thenComparing(sort -> sort.getSortName() == null ? "" : sort.getSortName(), String.CASE_INSENSITIVE_ORDER));
        return sorts;
    }

    private List<ArticleVO> listArticles(int size, Boolean recommendStatus, Integer sortId, Integer labelId) {
        BaseRequestVO requestVO = new BaseRequestVO();
        requestVO.setCurrent(1L);
        requestVO.setSize(size);
        requestVO.setRecommendStatus(recommendStatus);
        requestVO.setSortId(sortId);
        requestVO.setLabelId(labelId);

        PoetryResult<Page> result = articleService.listArticle(requestVO);
        if (result == null || !result.isSuccess() || result.getData() == null || CollectionUtils.isEmpty(result.getData().getRecords())) {
            return List.of();
        }

        List<ArticleVO> articles = new ArrayList<>();
        for (Object record : result.getData().getRecords()) {
            if (record instanceof ArticleVO articleVO) {
                articles.add(articleVO);
            }
        }
        return articles;
    }

    private String articlePath(ArticleVO article) {
        return ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug());
    }

    private Map<String, List<ResourcePathVO>> listGroupedResources(String type, boolean orderByCreateTime) {
        LambdaQueryChainWrapper<ResourcePath> wrapper = new LambdaQueryChainWrapper<>(resourcePathMapper);
        wrapper.eq(ResourcePath::getType, type)
                .eq(ResourcePath::getStatus, Boolean.TRUE);
        if (orderByCreateTime) {
            wrapper.orderByAsc(ResourcePath::getCreateTime);
        } else {
            wrapper.orderByAsc(ResourcePath::getTitle);
        }

        List<ResourcePath> resourcePaths = wrapper.list();
        if (CollectionUtils.isEmpty(resourcePaths)) {
            return Map.of();
        }

        return resourcePaths.stream()
                .map(this::toResourcePathVO)
                .collect(Collectors.groupingBy(ResourcePathVO::getClassify, LinkedHashMap::new, Collectors.toList()));
    }

    private ResourcePathVO getSiteInfo() {
        ResourcePath resourcePath = new LambdaQueryChainWrapper<>(resourcePathMapper)
                .eq(ResourcePath::getType, CommonConst.RESOURCE_PATH_TYPE_SITE_INFO)
                .eq(ResourcePath::getStatus, Boolean.TRUE)
                .one();

        if (resourcePath != null) {
            ResourcePathVO vo = toResourcePathVO(resourcePath);
            vo.setUrl(mailUtil.getSiteUrl());
            return vo;
        }

        ResourcePathVO defaultSiteInfo = new ResourcePathVO();
        defaultSiteInfo.setTitle("POETIZE");
        defaultSiteInfo.setUrl(mailUtil.getSiteUrl());
        defaultSiteInfo.setCover("https://s1.ax1x.com/2022/11/10/z9VlHs.png");
        defaultSiteInfo.setIntroduction("这是一个 Vue2 Vue3 与 SpringBoot 结合的产物～");
        return defaultSiteInfo;
    }

    private ResourcePathVO toResourcePathVO(ResourcePath resourcePath) {
        ResourcePathVO vo = new ResourcePathVO();
        BeanUtils.copyProperties(resourcePath, vo);
        return vo;
    }

    private String renderFriendGroup(String title, List<ResourcePathVO> friends) {
        if (CollectionUtils.isEmpty(friends)) {
            return "";
        }

        return "<h2>" + title + "</h2><ul>"
                + friends.stream().map(friend -> "<li><a href=\"" + attr(friend.getUrl()) + "\" target=\"_blank\" rel=\"noopener\" title=\""
                                + attr(friend.getIntroduction()) + "\">" + text(friend.getTitle()) + " - " + text(friend.getIntroduction()) + "</a></li>")
                        .collect(Collectors.joining())
                + "</ul>";
    }

    private String getSiteName(WebInfo webInfo) {
        return firstNonBlank(webInfo.getWebTitle(), webInfo.getWebName(), "POETIZE");
    }

    private String getBaseUrl(WebInfo webInfo) {
        return firstNonBlank(webInfo.getSiteAddress(), stringValue(getSeoConfig().get("site_address")), mailUtil.getSiteUrl());
    }

    private String buildUrl(String baseUrl, String routePath) {
        String normalizedBase = firstNonBlank(baseUrl, mailUtil.getSiteUrl()).replaceAll("/$", "");
        if (!StringUtils.hasText(routePath) || "/".equals(routePath)) {
            return normalizedBase;
        }
        if (routePath.startsWith("?")) {
            return normalizedBase + routePath;
        }
        return normalizedBase + (routePath.startsWith("/") ? routePath : "/" + routePath);
    }

    private String ensureAbsoluteImageUrl(String url, String baseUrl) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String normalizedBase = firstNonBlank(baseUrl, mailUtil.getSiteUrl()).replaceAll("/$", "");
        return url.startsWith("/") ? normalizedBase + url : normalizedBase + "/" + url;
    }

    private String getSourceLanguage() {
        return languageSupport.getSourceLanguage();
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATE_TIME_FORMATTER.format(dateTime);
    }

    private String applyAnonymousPaywall(Article article, String content) {
        if (article == null || !StringUtils.hasText(content)) {
            return firstNonBlank(content);
        }

        Integer payType = article.getPayType();
        if (payType == null || payType == 0) {
            return content;
        }

        String paywallMarker = "<!--paywall-->";
        int markerIndex = content.indexOf(paywallMarker);
        if (markerIndex >= 0) {
            return content.substring(0, markerIndex);
        }

        int freePercent = article.getFreePercent() == null ? 30 : article.getFreePercent();
        int normalizedPercent = Math.max(0, Math.min(freePercent, 100));
        int targetLength = (int) (content.length() * normalizedPercent / 100.0);
        int cutPoint = targetLength;
        int paragraphBreak = content.lastIndexOf("\n\n", targetLength);
        if (paragraphBreak > targetLength * 0.5) {
            cutPoint = paragraphBreak;
        } else {
            int lineBreak = content.lastIndexOf("\n", targetLength);
            if (lineBreak > targetLength * 0.5) {
                cutPoint = lineBreak;
            }
        }

        return content.substring(0, Math.max(0, cutPoint));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(String value) {
        String val = firstNonBlank(value);
        return val.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String attr(String value) {
        String val = firstNonBlank(value);
        return val.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
