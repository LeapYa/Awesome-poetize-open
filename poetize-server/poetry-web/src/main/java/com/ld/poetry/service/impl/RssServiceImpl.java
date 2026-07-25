package com.ld.poetry.service.impl;

import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.RssService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.SitemapService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** 无摘要时从正文提取的纯文本描述长度上限 */
    private static final int DESCRIPTION_MAX_LENGTH = 300;

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SitemapService sitemapService;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private SeoConfigService seoConfigService;

    @Override
    public String generateRss() {
        Object cachedRss = cacheService.get(CacheConstants.RSS_KEY);
        if (cachedRss instanceof String) {
            return (String) cachedRss;
        }

        String rss = generateRssDirect();
        if (rss != null) {
            cacheService.set(CacheConstants.RSS_KEY, rss, CacheConstants.RSS_EXPIRE_TIME);
        }
        return rss;
    }

    @Override
    public String generateRssDirect() {
        try {
            String siteUrl = sitemapService.getSiteBaseUrl();
            if (!StringUtils.hasText(siteUrl)) {
                log.error("无法获取网站URL，RSS生成失败");
                return null;
            }
            siteUrl = siteUrl.replaceAll("/+$", "");

            WebInfo webInfo = getWebInfo();
            String siteName = webInfo != null && StringUtils.hasText(webInfo.getWebName())
                    ? webInfo.getWebName() : "POETIZE";
            String siteDescription = resolveSiteDescription(webInfo, siteName);

            List<Article> articles = sitemapService.getVisibleArticles();

            StringBuilder xml = new StringBuilder(8192);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
            xml.append("<channel>\n");
            xml.append("<title>").append(escapeXml(siteName)).append("</title>\n");
            xml.append("<link>").append(escapeXml(siteUrl)).append("/</link>\n");
            xml.append("<description>").append(escapeXml(siteDescription)).append("</description>\n");
            xml.append("<language>zh-cn</language>\n");
            xml.append("<generator>POETIZE</generator>\n");
            xml.append("<lastBuildDate>").append(formatRfc1123(LocalDateTime.now())).append("</lastBuildDate>\n");
            xml.append("<atom:link href=\"").append(escapeXml(siteUrl))
                    .append("/rss.xml\" rel=\"self\" type=\"application/rss+xml\"/>\n");

            int count = 0;
            if (!CollectionUtils.isEmpty(articles)) {
                for (Article article : articles) {
                    if (count >= MAX_ITEMS) {
                        break;
                    }
                    appendItem(xml, siteUrl, article);
                    count++;
                }
            }

            xml.append("</channel>\n");
            xml.append("</rss>\n");

            log.info("RSS生成成功，包含 {} 篇文章", count);
            return xml.toString();
        } catch (Exception e) {
            log.error("生成RSS时发生错误", e);
            return null;
        }
    }

    @Override
    public void clearRssCache() {
        cacheService.deleteKey(CacheConstants.RSS_KEY);
    }

    private void appendItem(StringBuilder xml, String siteUrl, Article article) {
        String token = ArticleUrlUtil.resolveToken(article.getId(), article.getArticleSlug());
        String link = siteUrl + "/article/" + token;

        xml.append("<item>\n");
        xml.append("<title>").append(escapeXml(article.getArticleTitle())).append("</title>\n");
        xml.append("<link>").append(escapeXml(link)).append("</link>\n");
        xml.append("<guid isPermaLink=\"true\">").append(escapeXml(link)).append("</guid>\n");
        xml.append("<description>").append(escapeXml(buildDescription(article))).append("</description>\n");
        LocalDateTime pubTime = article.getCreateTime() != null ? article.getCreateTime() : article.getUpdateTime();
        if (pubTime != null) {
            xml.append("<pubDate>").append(formatRfc1123(pubTime)).append("</pubDate>\n");
        }
        xml.append("</item>\n");
    }

    /** 优先使用文章摘要，否则从正文提取纯文本片段 */
    private String buildDescription(Article article) {
        if (StringUtils.hasText(article.getSummary())) {
            return article.getSummary().trim();
        }
        return extractPlainText(article.getArticleContent());
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
        if (text.length() > DESCRIPTION_MAX_LENGTH) {
            text = text.substring(0, DESCRIPTION_MAX_LENGTH) + "…";
        }
        return text;
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
