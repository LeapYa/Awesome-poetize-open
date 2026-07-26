package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.LabelService;
import com.ld.poetry.service.RssService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.SitemapService;
import com.ld.poetry.service.SortService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.utils.ArticleUrlUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RSS 2.0 订阅源服务实现
 * <p>
 * 复用 SitemapService 的可见文章查询与站点URL解析，生成结果缓存1小时，
 * 文章内容变化时随 sitemap 缓存一起清除。
 *
 * @author LeapYa
 * @since 2026-07-25
 */
@Service
@Slf4j
public class RssServiceImpl implements RssService {

    /** RSS 输出的最新文章数量上限 */
    private static final int MAX_ITEMS = 30;

    /** 描述文本长度缺省上限：配置缺失时与全站 AI 摘要默认口径一致 */
    private static final int DEFAULT_DESCRIPTION_MAX_LENGTH = 150;

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);

    /** lang 参数合法格式（防注入，仅允许语言代码字符） */
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{2,16}$");

    /**
     * RSS generator 软件标识（含版本号）。
     * 按 RSS 2.0 规范，generator 表示“生成该 feed 的软件名”，
     * 本项目为 POETIZE 的 AGPL 分支（awesome-poetize-open，LeapYa 维护），
     * 使用分支自身标识；名称中保留 poetize 字样以体现与原项目的渊源。
     * 版本号来自 APP_VERSION 环境变量（与前端 VITE_APP_VERSION 同源，
     * 由 docker-compose 注入），dev/缺省时省略版本段。
     */
    private static final String GENERATOR = resolveGenerator();

    private static String resolveGenerator() {
        String version = System.getenv("APP_VERSION");
        if (version == null || version.isBlank() || "dev".equalsIgnoreCase(version.trim())) {
            return "awesome-poetize-open";
        }
        return "awesome-poetize-open/" + version.trim();
    }

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SitemapService sitemapService;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private SeoConfigService seoConfigService;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private SummaryService summaryService;

    @Autowired
    private SortService sortService;

    @Autowired
    private LabelService labelService;

    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;

    @Override
    public String generateRss() {
        return generateRss(null);
    }

    @Override
    public String generateRss(String language) {
        String lang = normalizeLangParam(language);
        String cacheKey = buildCacheKey(lang);
        Object cachedRss = cacheService.get(cacheKey);
        if (cachedRss instanceof String) {
            return (String) cachedRss;
        }

        String rss = generateRssDirect(lang);
        if (rss != null) {
            cacheService.set(cacheKey, rss, CacheConstants.RSS_EXPIRE_TIME);
        }
        return rss;
    }

    @Override
    public String generateRssDirect() {
        return generateRssDirect(null);
    }

    @Override
    public String generateRssDirect(String language) {
        String lang = normalizeLangParam(language);
        try {
            String siteUrl = sitemapService.getSiteBaseUrl();
            if (!StringUtils.hasText(siteUrl)) {
                log.error("无法获取网站URL，RSS生成失败");
                return null;
            }
            siteUrl = siteUrl.replaceAll("/+$", "");

            WebInfo webInfo = getWebInfo();
            // 频道标题用网站标题 webTitle；webName 在本项目语义中是站长/用户名，仅作兜底
            String siteName = resolveSiteTitle(webInfo);
            String siteDescription = resolveSiteDescription(webInfo, siteName);
            // 频道语言：基础版取文章AI配置的源语言，翻译版取 lang 参数
            String channelLanguage = lang != null ? normalizeLanguageTag(lang) : resolveSourceLanguageTag();
            String selfLink = siteUrl + "/rss.xml" + (lang != null ? "?lang=" + lang : "");

            List<Article> articles = sitemapService.getVisibleArticles();
            // getVisibleArticles 为 sitemap 按更新时间排序，RSS 改按首次发布时间降序，
            // 与 pubDate 口径一致，避免“先建隐藏稿、后发布”或编辑老文导致顺序错乱
            if (!CollectionUtils.isEmpty(articles)) {
                articles = new ArrayList<>(articles);
                articles.sort(Comparator.comparing(this::resolvePubTime,
                        Comparator.nullsLast(Comparator.reverseOrder())));
            }

            // 批量预取翻译/分类/标签，避免逐条查询
            Map<Integer, ArticleTranslation> translationMap = lang != null ? loadTranslations(articles, lang) : Map.of();
            Map<Integer, String> sortNameMap = loadSortNames(articles);
            Map<Integer, String> labelNameMap = loadLabelNames(articles);

            StringBuilder xml = new StringBuilder(8192);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
            xml.append("<channel>\n");
            xml.append("<title>").append(escapeXml(siteName)).append("</title>\n");
            xml.append("<link>").append(escapeXml(siteUrl)).append("/</link>\n");
            xml.append("<description>").append(escapeXml(siteDescription)).append("</description>\n");
            xml.append("<language>").append(escapeXml(channelLanguage)).append("</language>\n");
            xml.append("<generator>").append(GENERATOR).append("</generator>\n");
            xml.append("<lastBuildDate>").append(formatRfc1123(LocalDateTime.now())).append("</lastBuildDate>\n");
            xml.append("<atom:link href=\"").append(escapeXml(selfLink))
                    .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
            appendAlternateLanguageLink(xml, siteUrl, lang);

            int count = 0;
            if (!CollectionUtils.isEmpty(articles)) {
                for (Article article : articles) {
                    if (count >= MAX_ITEMS) {
                        break;
                    }
                    appendItem(xml, siteUrl, article,
                            translationMap.get(article.getId()),
                            sortNameMap.get(article.getSortId()),
                            labelNameMap.get(article.getLabelId()),
                            lang);
                    count++;
                }
            }

            xml.append("</channel>\n");
            xml.append("</rss>\n");

            log.info("RSS生成成功，语言: {}，包含 {} 篇文章", channelLanguage, count);
            return xml.toString();
        } catch (Exception e) {
            log.error("生成RSS时发生错误", e);
            return null;
        }
    }

    @Override
    public void clearRssCache() {
        // 清掉所有语言版本的缓存
        cacheService.deleteKeysByPattern(CacheConstants.RSS_KEY + "*");
    }

    private void appendItem(StringBuilder xml, String siteUrl, Article article,
            ArticleTranslation translation, String sortName, String labelName, String lang) {
        String baseLink = siteUrl + ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug());
        // 与预渲染口径一致：仅当该文章的翻译真实存在时，链接才指向 /article/{lang}/{id} 翻译页，
        // 否则回退原文链接（预渲染不会为不存在的翻译生成页面）
        boolean hasTranslation = translation != null;
        String link = lang != null && hasTranslation
                ? siteUrl + ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug(), lang, null)
                : baseLink;

        String title = hasTranslation && StringUtils.hasText(translation.getTitle())
                ? translation.getTitle() : article.getArticleTitle();
        String description = buildDescription(article, translation);

        xml.append("<item>\n");
        xml.append("<title>").append(escapeXml(title)).append("</title>\n");
        xml.append("<link>").append(escapeXml(link)).append("</link>\n");
        xml.append("<guid isPermaLink=\"true\">").append(escapeXml(baseLink)).append("</guid>\n");
        if (StringUtils.hasText(sortName)) {
            xml.append("<category>").append(escapeXml(sortName)).append("</category>\n");
        }
        if (StringUtils.hasText(labelName)) {
            xml.append("<category>").append(escapeXml(labelName)).append("</category>\n");
        }
        xml.append("<description>").append(escapeXml(description)).append("</description>\n");
        LocalDateTime pubTime = resolvePubTime(article);
        if (pubTime != null) {
            xml.append("<pubDate>").append(formatRfc1123(pubTime)).append("</pubDate>\n");
        }
        xml.append("</item>\n");
    }

    /** 发布时间口径：首次公开时间优先，存量数据降级为创建时间/更新时间 */
    private LocalDateTime resolvePubTime(Article article) {
        if (article.getPublishTime() != null) {
            return article.getPublishTime();
        }
        return article.getCreateTime() != null ? article.getCreateTime() : article.getUpdateTime();
    }

    /** 校验并规范化 lang 参数：仅接受语言代码格式，否则视为无语言（基础版） */
    private String normalizeLangParam(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }
        String lang = language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (!LANGUAGE_PATTERN.matcher(lang).matches()) {
            log.warn("RSS lang 参数格式非法，按基础版处理: {}", language);
            return null;
        }
        return lang;
    }

    /** 语言代码归一为 RSS 语言标签（BCP-47 小写惯例，如 zh/zh-hans→zh-cn） */
    private String normalizeLanguageTag(String lang) {
        String lower = lang.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "zh", "zh-cn", "zh-hans" -> "zh-cn";
            case "zh-tw", "zh-hant", "zh-hk" -> "zh-tw";
            default -> lower;
        };
    }

    /** 基础版频道语言：读取文章AI配置的源语言（带缓存），默认 zh-cn */
    private String resolveSourceLanguageTag() {
        try {
            Map<String, Object> langs = sysAiConfigService.getDefaultLanguages();
            Object sourceLang = langs != null ? langs.get("default_source_lang") : null;
            if (sourceLang != null && StringUtils.hasText(sourceLang.toString())) {
                return normalizeLanguageTag(sourceLang.toString());
            }
        } catch (Exception e) {
            log.warn("获取源语言配置失败，RSS频道语言使用默认 zh-cn: {}", e.getMessage());
        }
        return "zh-cn";
    }

    private String buildCacheKey(String lang) {
        return lang == null ? CacheConstants.RSS_KEY : CacheConstants.RSS_KEY + ":" + lang;
    }

    /**
     * 跨语言互链（atom:link rel=alternate hreflang，标准 Atom 扩展）：
     * 基础版广告翻译版 feed，翻译版回链基础版，供支持 Atom 扩展的阅读器发现其他语言订阅源。
     * 目标语言取自文章AI配置 default_target_lang；与源语言相同或未配置时不输出。
     */
    private void appendAlternateLanguageLink(StringBuilder xml, String siteUrl, String lang) {
        try {
            Map<String, Object> langs = sysAiConfigService.getDefaultLanguages();
            if (langs == null) {
                return;
            }
            String sourceLang = normalizeLangParam(Objects.toString(langs.get("default_source_lang"), null));
            String targetLang = normalizeLangParam(Objects.toString(langs.get("default_target_lang"), null));
            if (targetLang == null || targetLang.equals(sourceLang)) {
                return;
            }
            if (lang == null) {
                // 基础版 → 广告翻译版（仅当确实存在该语言的翻译文章，与预渲染"翻译行存在才生成页面"口径一致）
                if (!hasAnyTranslation(targetLang)) {
                    return;
                }
                xml.append("<atom:link rel=\"alternate\" hreflang=\"").append(normalizeLanguageTag(targetLang))
                        .append("\" type=\"application/rss+xml\" href=\"").append(escapeXml(siteUrl))
                        .append("/rss.xml?lang=").append(targetLang).append("\"/>\n");
            } else if (sourceLang != null) {
                // 翻译版 → 回链基础版（当前语言已在 rel=self 中声明，无需自指）
                xml.append("<atom:link rel=\"alternate\" hreflang=\"").append(normalizeLanguageTag(sourceLang))
                        .append("\" type=\"application/rss+xml\" href=\"").append(escapeXml(siteUrl))
                        .append("/rss.xml\"/>\n");
            }
        } catch (Exception e) {
            log.warn("生成跨语言 RSS 互链失败，跳过: {}", e.getMessage());
        }
    }

    /** 是否存在指定语言的任何翻译记录（决定是否值得广告该语言的 feed） */
    private boolean hasAnyTranslation(String lang) {
        try {
            LambdaQueryWrapper<ArticleTranslation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ArticleTranslation::getLanguage, lang).last("LIMIT 1");
            Long count = articleTranslationMapper.selectCount(wrapper);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("检查翻译记录存在性失败，跳过跨语言互链: {}", e.getMessage());
            return false;
        }
    }

    /** 批量加载指定语言的翻译，key 为文章ID */
    private Map<Integer, ArticleTranslation> loadTranslations(List<Article> articles, String lang) {
        if (CollectionUtils.isEmpty(articles)) {
            return Map.of();
        }
        List<Integer> articleIds = articles.stream().map(Article::getId).filter(Objects::nonNull).toList();
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        try {
            LambdaQueryWrapper<ArticleTranslation> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(ArticleTranslation::getArticleId, articleIds)
                    .eq(ArticleTranslation::getLanguage, lang);
            List<ArticleTranslation> translations = articleTranslationMapper.selectList(wrapper);
            return translations.stream().collect(Collectors.toMap(
                    ArticleTranslation::getArticleId, t -> t, (a, b) -> a, HashMap::new));
        } catch (Exception e) {
            log.warn("批量加载文章翻译失败，RSS条目降级为原文: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 批量加载分类名称，key 为分类ID */
    private Map<Integer, String> loadSortNames(List<Article> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return Map.of();
        }
        List<Integer> sortIds = articles.stream().map(Article::getSortId).filter(Objects::nonNull).distinct().toList();
        if (sortIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Sort> sorts = sortService.listByIds(sortIds);
            return sorts.stream().collect(Collectors.toMap(Sort::getId, Sort::getSortName, (a, b) -> a, HashMap::new));
        } catch (Exception e) {
            log.warn("批量加载分类失败，RSS条目跳过分类输出: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 批量加载标签名称，key 为标签ID */
    private Map<Integer, String> loadLabelNames(List<Article> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return Map.of();
        }
        List<Integer> labelIds = articles.stream().map(Article::getLabelId).filter(Objects::nonNull).distinct().toList();
        if (labelIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Label> labels = labelService.listByIds(labelIds);
            return labels.stream().collect(Collectors.toMap(Label::getId, Label::getLabelName, (a, b) -> a, HashMap::new));
        } catch (Exception e) {
            log.warn("批量加载标签失败，RSS条目跳过标签输出: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 优先使用文章摘要（翻译版优先取翻译摘要），否则从正文提取纯文本片段；所有路径统一截断并追加省略号 */
    private String buildDescription(Article article, ArticleTranslation translation) {
        if (translation != null) {
            if (StringUtils.hasText(translation.getSummary())) {
                return withTruncationMarker(translation.getSummary().trim());
            }
            if (StringUtils.hasText(translation.getContent())) {
                return extractPlainText(translation.getContent());
            }
        }
        if (StringUtils.hasText(article.getSummary())) {
            return withTruncationMarker(article.getSummary().trim());
        }
        return extractPlainText(article.getArticleContent());
    }

    /**
     * 描述统一出口。历史存量摘要可能被上游静默截断到恰好上限（无省略号标记），
     * 此处补救：长度达到上限且末尾不是终止标点/省略号时，视为已被截断，补省略号。
     */
    private String withTruncationMarker(String text) {
        int limit = descriptionMaxLength();
        if (text.length() > limit) {
            return truncateWithEllipsis(text);
        }
        if (text.length() == limit && !hasSentenceEnding(text)) {
            return text + "...";
        }
        return text;
    }

    /** 是否以句子终止标点或省略号结尾（用于判断摘要是否完整） */
    private boolean hasSentenceEnding(String text) {
        return text.endsWith("...") || text.endsWith("\u2026")
                || text.matches(".*[。！？.!?]$");
    }

    /**
     * 超长文本截断并追加省略号（未超长按原样返回）。
     * 截断点若落在代理对高位（emoji 等增补字符）则退一位，避免输出半个乱码字符。
     */
    private String truncateWithEllipsis(String text) {
        int limit = descriptionMaxLength();
        if (text == null || text.length() <= limit) {
            return text;
        }
        int cut = limit;
        if (Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
        return text.substring(0, cut) + "...";
    }

    /** 描述长度上限：跟随摘要配置 summary.max_length，缺省 150 */
    private int descriptionMaxLength() {
        try {
            int configured = summaryService.getConfiguredSummaryMaxLength();
            return configured > 0 ? configured : DEFAULT_DESCRIPTION_MAX_LENGTH;
        } catch (Exception e) {
            log.warn("获取摘要长度配置失败，使用默认值 {}: {}", DEFAULT_DESCRIPTION_MAX_LENGTH, e.getMessage());
            return DEFAULT_DESCRIPTION_MAX_LENGTH;
        }
    }

    /** 去掉 Markdown/HTML 标记，截取指定长度的纯文本 */
    private String extractPlainText(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content
                // 代码块与行内代码
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("`[^`]*`", " ")
                // 图片与链接保留可读文字
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                // HTML 标签
                .replaceAll("<[^>]+>", " ")
                // 标题/引用/强调等 Markdown 符号
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("[*_~]{1,3}", "")
                // 折叠空白
                .replaceAll("\\s+", " ")
                .trim();
        return truncateWithEllipsis(text);
    }

    /** 频道标题：优先 webTitle（网站标题），其次 webName（站长名），最后兜底软件名 */
    private String resolveSiteTitle(WebInfo webInfo) {
        if (webInfo != null) {
            if (StringUtils.hasText(webInfo.getWebTitle())) {
                return webInfo.getWebTitle().trim();
            }
            if (StringUtils.hasText(webInfo.getWebName())) {
                return webInfo.getWebName().trim();
            }
        }
        return "POETIZE";
    }

    private WebInfo getWebInfo() {
        try {
            WebInfo webInfo = cacheService.getCachedWebInfo();
            if (webInfo == null) {
                webInfo = webInfoService.lambdaQuery().last("limit 1").one();
                if (webInfo != null) {
                    cacheService.cacheWebInfo(webInfo);
                }
            }
            return webInfo;
        } catch (Exception e) {
            log.warn("获取网站信息失败，RSS使用默认站点信息: {}", e.getMessage());
            return null;
        }
    }

    private String resolveSiteDescription(WebInfo webInfo, String siteName) {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            if (seoConfig != null) {
                Object description = seoConfig.get("site_description");
                if (description != null && StringUtils.hasText(description.toString())) {
                    return description.toString().trim();
                }
            }
        } catch (Exception e) {
            log.warn("获取SEO站点描述失败，RSS使用默认描述: {}", e.getMessage());
        }
        if (webInfo != null && StringUtils.hasText(webInfo.getWebTitle())) {
            return webInfo.getWebTitle().trim();
        }
        return siteName;
    }

    private String formatRfc1123(LocalDateTime time) {
        return RFC_1123.format(ZonedDateTime.of(time, ZoneId.systemDefault()));
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        // 完整的 XML 转义：标准 5 个字符 + 控制字符清理
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                // 移除无效的控制字符（XML 1.0 不允许）
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
