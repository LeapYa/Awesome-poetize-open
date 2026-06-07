package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.service.SeoMetaService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SortService;
import com.ld.poetry.service.LabelService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.Label;
import com.ld.poetry.utils.ArticleUrlUtil;
import com.ld.poetry.utils.ArticleSummaryTextUtil;
import com.ld.poetry.utils.PoetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * SEO元数据生成服务实现类
 * </p>
 *
 * @author LeapYa
 * @since 2025-9-25
 */
@Service
@Slf4j
public class SeoMetaServiceImpl implements SeoMetaService {

    @Autowired
    private SeoConfigService seoConfigService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private SortService sortService;

    @Autowired
    private LabelService labelService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;

    @Autowired
    private com.ld.poetry.service.SysAiConfigService sysAiConfigService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    // ISO 8601 时区偏移量（北京时间 UTC+8）
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter ISO_OFFSET_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Override
    public Map<String, Object> generateArticleMeta(Integer articleId, String language) {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            Map<String, Object> meta = new HashMap<>();

            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                return createDisabledMeta();
            }

            // 获取文章信息
            Article article = articleService.getById(articleId);
            if (article == null) {
                return createNotFoundMeta();
            }

            // 基础元数据（支持多语言标题）
            String sourceLanguage = getSourceLanguage();
            String title = resolveArticleTitle(article, language, sourceLanguage);

            // 生成描述时传入语言参数，使用对应语言的摘要
            String description = generateArticleDescription(article, seoConfig, language);
            String keywords = generateArticleKeywords(article, seoConfig, language, sourceLanguage);

            meta.put("title", title);
            meta.put("description", description);
            // 仅在有文章相关关键词时才输出keywords，避免输出空值或无关的全站关键词
            if (StringUtils.hasText(keywords)) {
                meta.put("keywords", keywords);
            }
            meta.put("author",
                    StringUtils.hasText(seoConfig.get("default_author").toString()) ? seoConfig.get("default_author")
                            : getSiteTitle());

            // 文章特定信息
            meta.put("article_title", title);
            meta.put("article_id", articleId);
            meta.put("published_time", formatDateTimeWithTimezone(article.getCreateTime()));
            meta.put("modified_time", formatDateTimeWithTimezone(article.getUpdateTime()));

            // 获取分类信息
            if (article.getSortId() != null) {
                Sort sort = sortService.getById(article.getSortId());
                if (sort != null) {
                    meta.put("category", sort.getSortName());
                    meta.put("category_id", sort.getId());
                }
            }

            // 获取标签信息
            if (article.getLabelId() != null) {
                Label label = labelService.getById(article.getLabelId());
                if (label != null) {
                    meta.put("tag", label.getLabelName());
                    meta.put("tag_id", label.getId());
                }
            }

            // OpenGraph和Twitter Card
            addSocialMediaMeta(meta, seoConfig, title, description);

            // Canonical URL (规范链接) - 翻译文章使用 /article/{lang}/{id}
            String siteUrl = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteUrl)) {
                String articleUrl = buildArticleUrl(siteUrl, article, language, sourceLanguage);
                meta.put("canonical", articleUrl);
                meta.put("og:url", articleUrl);

                // hreflang 标签 — 标识所有语言版本的关系
                addHreflangTags(meta, siteUrl, article, sourceLanguage);
            }

            // 获取文章封面图（与前端展示逻辑一致：优先用文章封面，否则用随机封面）
            String articleImage = getArticleCoverUrl(article);
            if (StringUtils.hasText(articleImage)) {
                meta.put("og:image", articleImage);
            }

            // 自定义头部代码
            meta.put("custom_head_code", seoConfig.get("custom_head_code"));

            // 结构化数据（传入语言参数，使JSON-LD中的headline/description也使用翻译内容）
            meta.put("structured_data", generateArticleStructuredData(article, seoConfig, language, sourceLanguage));

            return meta;

        } catch (Exception e) {
            log.error("生成文章SEO元数据失败: articleId={}", articleId, e);
            return createErrorMeta();
        }
    }

    @Override
    public Map<String, Object> generateSiteMeta(String language) {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            Map<String, Object> meta = new HashMap<>();

            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                return createDisabledMeta();
            }

            // 基础站点信息
            meta.put("title", getSiteTitle());
            meta.put("description", seoConfig.get("site_description"));
            meta.put("keywords", seoConfig.get("site_keywords"));
            meta.put("author", seoConfig.get("default_author"));
            meta.put("site_name", seoConfig.get("og_site_name"));

            // 网站图标
            addIconMeta(meta, seoConfig);

            // OpenGraph和Twitter Card
            addSocialMediaMeta(meta, seoConfig,
                    getSiteTitle(),
                    seoConfig.get("site_description").toString());

            // Canonical URL (规范链接)
            String siteUrl = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteUrl)) {
                meta.put("canonical", siteUrl);
                meta.put("og:url", siteUrl);
            }

            // PWA相关
            addPwaMeta(meta, seoConfig);

            // 自定义头部代码
            meta.put("custom_head_code", seoConfig.get("custom_head_code"));

            // 结构化数据
            meta.put("structured_data", generateWebsiteStructuredData(seoConfig));

            return meta;

        } catch (Exception e) {
            log.error("生成网站SEO元数据失败", e);
            return createErrorMeta();
        }
    }

    @Override
    public Map<String, Object> generateCategoryMeta(Integer categoryId, String language) {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            Map<String, Object> meta = new HashMap<>();

            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                return createDisabledMeta();
            }

            // 获取分类信息
            Sort category = sortService.getById(categoryId);
            if (category == null) {
                return createNotFoundMeta();
            }

            String title = category.getSortName() + " - " + getSiteTitle();
            String description = StringUtils.hasText(category.getSortDescription()) ? category.getSortDescription()
                    : "查看 " + category.getSortName() + " 分类下的所有文章 - " + seoConfig.get("site_description");

            meta.put("title", title);
            meta.put("description", description);
            meta.put("keywords", category.getSortName() + "," + seoConfig.get("site_keywords"));
            meta.put("category_name", category.getSortName());
            meta.put("category_id", categoryId);

            // OpenGraph和Twitter Card
            addSocialMediaMeta(meta, seoConfig, title, description);

            // Canonical URL (规范链接)
            String siteUrl = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteUrl)) {
                meta.put("canonical", siteUrl + "/sort/" + categoryId);
                meta.put("og:url", siteUrl + "/sort/" + categoryId);
            }

            // 自定义头部代码
            meta.put("custom_head_code", seoConfig.get("custom_head_code"));

            // 结构化数据
            meta.put("structured_data", generateCategoryStructuredData(category, seoConfig));

            return meta;

        } catch (Exception e) {
            log.error("生成分类SEO元数据失败: categoryId={}", categoryId, e);
            return createErrorMeta();
        }
    }

    @Override
    public Map<String, Object> generateTagMeta(Integer tagId, String language) {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            Map<String, Object> meta = new HashMap<>();

            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                return createDisabledMeta();
            }

            // 获取标签信息
            Label tag = labelService.getById(tagId);
            if (tag == null) {
                return createNotFoundMeta();
            }

            String title = tag.getLabelName() + " - " + getSiteTitle();
            String description = StringUtils.hasText(tag.getLabelDescription()) ? tag.getLabelDescription()
                    : "查看标签 " + tag.getLabelName() + " 下的所有文章 - " + seoConfig.get("site_description");

            meta.put("title", title);
            meta.put("description", description);
            meta.put("keywords", tag.getLabelName() + "," + seoConfig.get("site_keywords"));
            meta.put("tag_name", tag.getLabelName());
            meta.put("tag_id", tagId);

            // OpenGraph和Twitter Card
            addSocialMediaMeta(meta, seoConfig, title, description);

            // Canonical URL (规范链接)
            String siteUrl = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteUrl)) {
                meta.put("canonical", siteUrl + "/tag/" + tagId);
                meta.put("og:url", siteUrl + "/tag/" + tagId);
            }

            // 自定义头部代码
            meta.put("custom_head_code", seoConfig.get("custom_head_code"));

            return meta;

        } catch (Exception e) {
            log.error("生成标签SEO元数据失败: tagId={}", tagId, e);
            return createErrorMeta();
        }
    }

    @Override
    public Map<String, Object> generateImSiteMeta(String language) {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            Map<String, Object> meta = new HashMap<>();

            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                return createDisabledMeta();
            }

            String title = "即时通讯 - " + getSiteTitle();
            String description = "在线聊天和即时通讯功能 - " + seoConfig.get("site_description");

            meta.put("title", title);
            meta.put("description", description);
            meta.put("keywords", "即时通讯,在线聊天,IM," + seoConfig.get("site_keywords"));

            // OpenGraph和Twitter Card
            addSocialMediaMeta(meta, seoConfig, title, description);

            // 自定义头部代码
            meta.put("custom_head_code", seoConfig.get("custom_head_code"));

            return meta;

        } catch (Exception e) {
            log.error("生成IM站点SEO元数据失败", e);
            return createErrorMeta();
        }
    }

    @Override
    public Map<String, Object> detectSiteUrl(HttpServletRequest request) {
        try {
            // 从请求头检测URL
            String detectedUrl = detectUrlFromRequest(request);

            Map<String, Object> result = new HashMap<>();
            result.put("detected_url", detectedUrl);
            result.put("fallback_url", "http://localhost");
            result.put("detection_source", "request_headers");

            log.info("检测到网站URL: {}", detectedUrl);
            return result;

        } catch (Exception e) {
            log.error("检测网站URL失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("detected_url", "http://localhost");
            result.put("fallback_url", "http://localhost");
            result.put("detection_source", "fallback");
            return result;
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取系统配置的源语言
     * 
     * @return 源语言代码，默认为 "zh"
     */
    private String getSourceLanguage() {
        try {
            Map<String, Object> defaultLangs = sysAiConfigService.getDefaultLanguages();
            return defaultLangs != null ? (String) defaultLangs.getOrDefault("default_source_lang", "zh") : "zh";
        } catch (Exception e) {
            log.warn("获取源语言配置失败，使用默认值 zh: {}", e.getMessage());
            return "zh";
        }
    }

    /**
     * 解析文章标题（支持多语言）
     * 当请求语言非源语言时，从翻译表获取翻译后的标题
     * 
     * @param article        文章对象
     * @param language       目标语言代码
     * @param sourceLanguage 源语言代码
     * @return 对应语言的文章标题
     */
    private String resolveArticleTitle(Article article, String language, String sourceLanguage) {
        // 如果指定了非源语言，尝试从翻译表获取翻译后的标题
        if (StringUtils.hasText(language) && !language.equals(sourceLanguage)) {
            try {
                ArticleTranslation translation = articleTranslationMapper.selectOne(
                        new LambdaQueryWrapper<ArticleTranslation>()
                                .eq(ArticleTranslation::getArticleId, article.getId())
                                .eq(ArticleTranslation::getLanguage, language));
                if (translation != null && StringUtils.hasText(translation.getTitle())) {
                    return translation.getTitle();
                }
            } catch (Exception e) {
                log.warn("获取翻译标题失败，使用原文标题: articleId={}, lang={}, error={}",
                        article.getId(), language, e.getMessage());
            }
        }
        // 回退到中文原文标题
        return StringUtils.hasText(article.getArticleTitle()) ? article.getArticleTitle() : getSiteTitle();
    }

    /**
     * 构建文章URL（支持翻译文章路径）
     * 翻译文章使用 /article/{lang}/{id} 格式，源语言文章使用 /article/{id}
     */
    private String buildArticleUrl(String siteUrl, Article article, String language, String sourceLanguage) {
        return siteUrl + ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug(), language, sourceLanguage);
    }

    /**
     * 添加 hreflang 标签到 meta 中
     * 查询文章的所有翻译版本，生成对应的 alternate 链接
     * 用于告诉搜索引擎各语言版本之间的关系
     *
     * @param meta           meta map，hreflang 标签以 hreflang_ 前缀存入
     * @param siteUrl        网站根URL
     * @param article        文章
     * @param sourceLanguage 源语言代码
     */
    private void addHreflangTags(Map<String, Object> meta, String siteUrl, Article article, String sourceLanguage) {
        Integer articleId = article == null ? null : article.getId();
        try {
            String articleToken = ArticleUrlUtil.resolveToken(article.getId(), article.getArticleSlug());
            // 查询该文章的所有翻译版本（只查 language 字段即可）
            List<ArticleTranslation> translations = articleTranslationMapper.selectList(
                    new LambdaQueryWrapper<ArticleTranslation>()
                            .eq(ArticleTranslation::getArticleId, articleId)
                            .select(ArticleTranslation::getLanguage));

            if (translations == null || translations.isEmpty()) {
                // 没有翻译版本，无需 hreflang
                return;
            }

            // 源语言版本 — 始终包含
            String sourceUrl = siteUrl + "/article/" + articleToken;
            meta.put("hreflang_" + sourceLanguage,
                    "<link rel=\"alternate\" hreflang=\"" + sourceLanguage + "\" href=\"" + sourceUrl + "\">");

            // x-default 指向源语言版本
            meta.put("hreflang_x-default",
                    "<link rel=\"alternate\" hreflang=\"x-default\" href=\"" + sourceUrl + "\">");

            // 各翻译版本
            for (ArticleTranslation t : translations) {
                String lang = t.getLanguage();
                if (StringUtils.hasText(lang) && !lang.equals(sourceLanguage)) {
                    String translatedUrl = siteUrl + "/article/" + lang + "/" + articleToken;
                    meta.put("hreflang_" + lang,
                            "<link rel=\"alternate\" hreflang=\"" + lang + "\" href=\"" + translatedUrl + "\">");
                }
            }
        } catch (Exception e) {
            log.warn("生成hreflang标签失败: articleId={}, error={}", articleId, e.getMessage());
        }
    }

    /**
     * 生成文章描述（支持多语言）
     * 
     * @param article   文章对象
     * @param seoConfig SEO配置
     * @param language  语言代码，如果为null则使用源语言
     * @return 文章描述
     */
    private String generateArticleDescription(Article article, Map<String, Object> seoConfig, String language) {
        String description = "";

        // 如果指定了语言参数，尝试从翻译表获取对应语言的摘要
        if (StringUtils.hasText(language)) {
            try {
                // 获取默认源语言
                Map<String, Object> defaultLangs = sysAiConfigService.getDefaultLanguages();
                String sourceLanguage = defaultLangs != null
                        ? (String) defaultLangs.getOrDefault("default_source_lang", "zh")
                        : "zh";

                // 如果不是源语言，从翻译表获取摘要
                if (!language.equals(sourceLanguage)) {
                    ArticleTranslation translation = articleTranslationMapper.selectOne(
                            new LambdaQueryWrapper<ArticleTranslation>()
                                    .eq(ArticleTranslation::getArticleId, article.getId())
                                    .eq(ArticleTranslation::getLanguage, language));

                    if (translation != null && StringUtils.hasText(translation.getSummary())) {
                        description = cleanSummaryText(translation.getSummary());
                        return description;
                    } else {
                    }
                }
            } catch (Exception e) {
                log.warn("获取翻译摘要失败，使用原文摘要: {}", e.getMessage());
            }
        }

        // 使用源语言摘要或内容
        if (StringUtils.hasText(article.getSummary())) {
            description = cleanSummaryText(article.getSummary());
        } else if (StringUtils.hasText(article.getArticleContent())) {
            // 从内容中提取描述，保底限制在前500字
            description = cleanSummaryText(article.getArticleContent(), 500);
        }

        // 如果还是没有，使用网站默认描述
        if (!StringUtils.hasText(description)) {
            Object siteDescription = seoConfig.get("site_description");
            description = cleanSummaryText(siteDescription == null ? "" : siteDescription.toString());
        }

        return description;
    }

    /**
     * 生成文章关键词（仅包含与当前文章相关的关键词，避免关键词堆砌，支持多语言）
     * 
     * 策略说明：
     * 1. 只使用经过人工审核的结构化数据：分类名 + 标签名
     * 2. 不对标题做自动分词——中文标题无法用简单的标点分割来提取有意义的关键词，
     * 粗暴分割会产生"与"、"的"、"实现"等停用词，反而降低相关性
     * 3. 不追加全站通用关键词(site_keywords)到文章页，避免关键词堆砌
     * 4. 如果文章既无分类也无标签，返回空字符串，宁缺勿滥
     * （Google 2009年起已不使用 meta keywords 作为排名信号，
     * 但保留精准的关键词对百度等引擎仍有一定参考价值）
     *
     * @param article        文章对象
     * @param seoConfig      SEO配置
     * @param language       目标语言代码
     * @param sourceLanguage 源语言代码
     */
    private String generateArticleKeywords(Article article, Map<String, Object> seoConfig, String language,
            String sourceLanguage) {
        StringBuilder keywords = new StringBuilder();

        // 添加分类名称作为关键词
        if (article.getSortId() != null) {
            Sort sort = sortService.getById(article.getSortId());
            if (sort != null && StringUtils.hasText(sort.getSortName())) {
                keywords.append(sort.getSortName());
            }
        }

        // 添加标签名称作为关键词
        if (article.getLabelId() != null) {
            Label label = labelService.getById(article.getLabelId());
            if (label != null && StringUtils.hasText(label.getLabelName())) {
                if (keywords.length() > 0) {
                    keywords.append(",");
                }
                keywords.append(label.getLabelName());
            }
        }

        // 添加文章标题作为整体关键词（支持多语言：翻译文章使用翻译标题）
        String articleTitle = resolveArticleTitle(article, language, sourceLanguage);
        if (StringUtils.hasText(articleTitle)) {
            if (keywords.length() > 0) {
                keywords.append(",");
            }
            keywords.append(articleTitle);
        }

        return keywords.toString();
    }

    private void addSocialMediaMeta(Map<String, Object> meta, Map<String, Object> seoConfig, String title,
            String description) {
        // OpenGraph
        meta.put("og:type", seoConfig.get("og_type"));
        meta.put("og:title", title);
        meta.put("og:description", description);
        meta.put("og:site_name", seoConfig.get("og_site_name"));
        meta.put("og:image", seoConfig.get("og_image"));

        // Twitter Card
        meta.put("twitter:card", seoConfig.get("twitter_card"));
        meta.put("twitter:title", title);
        meta.put("twitter:description", description);
        meta.put("twitter:site", seoConfig.get("twitter_site"));
        meta.put("twitter:creator", seoConfig.get("twitter_creator"));
    }

    /**
     * 获取文章封面图URL（与前端buildArticleVO逻辑一致）
     * 优先使用文章自身封面，如果为空则使用随机封面
     * 始终返回绝对路径URL，满足Google结构化数据和OpenGraph规范要求
     */
    private String getArticleCoverUrl(Article article) {
        String coverUrl = null;
        if (StringUtils.hasText(article.getArticleCover())) {
            coverUrl = article.getArticleCover();
        } else {
            // 与 ArticleServiceImpl.buildArticleVO 保持一致，使用文章ID生成随机封面
            coverUrl = PoetryUtil.getRandomCover(article.getId().toString());
        }
        return toAbsoluteUrl(coverUrl);
    }

    /**
     * 将可能的相对路径URL转换为绝对路径URL
     * 如果已经是绝对路径（以http://或https://开头），则原样返回
     * 如果是相对路径（如/static/xxx），则拼接网站域名前缀
     * Google结构化数据和OpenGraph规范要求图片等资源使用绝对路径
     */
    private String toAbsoluteUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        // 已经是绝对路径，直接返回
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        // 拼接网站域名前缀
        String siteUrl = mailUtil.getSiteUrl();
        if (StringUtils.hasText(siteUrl)) {
            // 确保不会出现双斜杠：siteUrl末尾无/，url以/开头
            if (url.startsWith("/")) {
                return siteUrl + url;
            } else {
                return siteUrl + "/" + url;
            }
        }
        // 无法获取站点URL时返回原始路径
        return url;
    }

    private void addIconMeta(Map<String, Object> meta, Map<String, Object> seoConfig) {
        meta.put("site_icon", seoConfig.get("site_icon"));
        meta.put("site_icon_192", seoConfig.get("site_icon_192"));
        meta.put("site_icon_512", seoConfig.get("site_icon_512"));
        meta.put("apple_touch_icon", seoConfig.get("apple_touch_icon"));
        meta.put("site_logo", seoConfig.get("site_logo"));
    }

    private void addPwaMeta(Map<String, Object> meta, Map<String, Object> seoConfig) {
        meta.put("pwa_theme_color", seoConfig.get("pwa_theme_color"));
        meta.put("pwa_background_color", seoConfig.get("pwa_background_color"));
        meta.put("pwa_display", seoConfig.get("pwa_display"));
    }

    private String generateArticleStructuredData(Article article, Map<String, Object> seoConfig, String language,
            String sourceLanguage) {
        // 生成JSON-LD结构化数据
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("@context", "https://schema.org");
        structuredData.put("@type", "Article");
        // headline 使用翻译后的标题（与 <title> 保持一致）
        structuredData.put("headline", resolveArticleTitle(article, language, sourceLanguage));
        structuredData.put("datePublished", formatDateTimeWithTimezone(article.getCreateTime()));
        structuredData.put("dateModified", formatDateTimeWithTimezone(article.getUpdateTime()));

        // 文章封面图片 (image)
        String articleImage = getArticleCoverUrl(article);
        if (StringUtils.hasText(articleImage)) {
            structuredData.put("image", articleImage);
        } else if (seoConfig.get("og_image") != null && StringUtils.hasText(seoConfig.get("og_image").toString())) {
            // 最终降级使用全局默认OG图片，确保使用绝对路径
            structuredData.put("image", toAbsoluteUrl(seoConfig.get("og_image").toString()));
        }

        // mainEntityOfPage - 指向文章的规范URL（翻译文章使用翻译版URL）
        String siteUrl = mailUtil.getSiteUrl();
        if (StringUtils.hasText(siteUrl)) {
            Map<String, Object> mainEntity = new HashMap<>();
            mainEntity.put("@type", "WebPage");
            mainEntity.put("@id", buildArticleUrl(siteUrl, article, language, sourceLanguage));
            structuredData.put("mainEntityOfPage", mainEntity);
        }

        // 文章描述（使用翻译后的描述）
        String description = generateArticleDescription(article, seoConfig, language);
        if (StringUtils.hasText(description)) {
            structuredData.put("description", description);
        }

        Map<String, Object> author = new HashMap<>();
        author.put("@type", "Person");
        author.put("name", seoConfig.get("default_author"));
        // 作者URL - 有助于建立作者实体 (Author Entity)，增强E-E-A-T
        String authorUrl = mailUtil.getSiteUrl();
        if (StringUtils.hasText(authorUrl)) {
            author.put("url", authorUrl);
        }
        structuredData.put("author", author);

        // Publisher - 使用与og:site_name一致的名称，保持品牌一致性
        String siteName = getSiteTitle();
        Map<String, Object> publisher = new HashMap<>();
        publisher.put("@type", "Organization");
        publisher.put("name", siteName);
        // Publisher logo - 确保使用绝对路径
        if (seoConfig.get("site_logo") != null && StringUtils.hasText(seoConfig.get("site_logo").toString())) {
            Map<String, Object> logo = new HashMap<>();
            logo.put("@type", "ImageObject");
            logo.put("url", toAbsoluteUrl(seoConfig.get("site_logo").toString()));
            publisher.put("logo", logo);
        }
        structuredData.put("publisher", publisher);

        // 如果是翻译文章，添加 inLanguage 字段
        if (StringUtils.hasText(language)) {
            structuredData.put("inLanguage", language);
        }

        return toJsonString(structuredData);
    }

    private String generateWebsiteStructuredData(Map<String, Object> seoConfig) {
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("@context", "https://schema.org");
        structuredData.put("@type", "WebSite");
        structuredData.put("name", getSiteTitle());
        structuredData.put("description", seoConfig.get("site_description"));
        // 直接从 MailUtil 获取网站地址
        structuredData.put("url", mailUtil.getSiteUrl());

        return toJsonString(structuredData);
    }

    private String generateCategoryStructuredData(Sort category, Map<String, Object> seoConfig) {
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("@context", "https://schema.org");
        structuredData.put("@type", "CollectionPage");
        structuredData.put("name", category.getSortName());
        structuredData.put("description", category.getSortDescription());

        return toJsonString(structuredData);
    }

    /**
     * 将LocalDateTime格式化为带时区偏移的ISO 8601字符串
     * 例如: 2026-01-29T17:20:36+08:00
     * Google结构化数据要求带时区信息，避免因时区差异导致文章时间显示不准确
     */
    private String formatDateTimeWithTimezone(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atOffset(ZONE_OFFSET).format(ISO_OFFSET_FORMATTER);
    }

    private String cleanSummaryText(String content) {
        return cleanSummaryText(content, 0);
    }

    private String cleanSummaryText(String content, int maxLength) {
        return ArticleSummaryTextUtil.toPlainText(content, maxLength);
    }

    private String detectUrlFromRequest(HttpServletRequest request) {
        // 检测协议
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }

        // 检测主机
        String host = request.getHeader("X-Forwarded-Host");
        if (!StringUtils.hasText(host)) {
            host = request.getHeader("Host");
        }
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }

        return scheme + "://" + host;
    }

    private Map<String, Object> createDisabledMeta() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("seo_enabled", false);
        meta.put("message", "SEO功能已禁用");
        return meta;
    }

    private Map<String, Object> createNotFoundMeta() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("error", "资源未找到");
        meta.put("status", 404);
        return meta;
    }

    private Map<String, Object> createErrorMeta() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("error", "生成SEO元数据时发生错误");
        meta.put("status", 500);
        return meta;
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("转换JSON字符串失败", e);
            return "{}";
        }
    }

    /**
     * 获取网站标题，优先使用webInfo.webTitle，然后尝试SEO配置中的og_site_name
     */
    private String getSiteTitle() {
        try {
            var webInfo = cacheService.getCachedWebInfo();
            if (webInfo != null && StringUtils.hasText(webInfo.getWebTitle())) {
                return webInfo.getWebTitle();
            } else if (webInfo != null && StringUtils.hasText(webInfo.getWebName())) {
                return webInfo.getWebName();
            }
        } catch (Exception e) {
            log.warn("获取webInfo失败，尝试从SEO配置获取站点名称", e);
        }
        // 尝试从SEO配置获取og_site_name作为备选
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            Object ogSiteName = seoConfig.get("og_site_name");
            if (ogSiteName != null && StringUtils.hasText(ogSiteName.toString())) {
                return ogSiteName.toString();
            }
        } catch (Exception e) {
            log.warn("获取SEO配置失败", e);
        }
        return "My Blog";
    }
}
