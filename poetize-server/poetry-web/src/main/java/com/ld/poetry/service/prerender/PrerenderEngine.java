package com.ld.poetry.service.prerender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class PrerenderEngine {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<html([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>[\\s\\S]*?</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_PATTERN = Pattern.compile("<body([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern APP_PATTERN = Pattern.compile("<div\\s+id\\s*=\\s*[\"']app[\"'][^>]*>[\\s\\S]*?</div>", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEBPACK_CSS_PATTERN = Pattern.compile(
            "<link[^>]*href=[\"'][^\"']*/static/[^\"']*\\.css[^\"']*[\"'][^>]*rel=[\"']stylesheet[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT_FAVICON_PATTERN = Pattern.compile(
            "<link\\s+[^>]*rel\\s*=\\s*[\"']icon[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT_FAVICON_ID_PATTERN = Pattern.compile(
            "<link\\s+[^>]*id\\s*=\\s*[\"']default-favicon[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> REMOVE_PATTERNS = List.of(
            Pattern.compile("<meta\\s+[^>]*name\\s*=\\s*[\"'](?:description|keywords|author)[\"'][^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*(?:property|name)\\s*=\\s*[\"'](?:og:|twitter:|article:)[^\"']*[\"'][^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*property\\s*=\\s*[\"']structured_data[\"'][^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script\\s+[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*data-prerender-structured-data\\s*=\\s*[\"']true[\"'][^>]*>[\\s\\S]*?</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<link\\s+[^>]*rel\\s*=\\s*[\"']canonical[\"'][^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<link\\s+[^>]*rel\\s*=\\s*[\"']alternate[\"'][^>]*>", Pattern.CASE_INSENSITIVE));

    private static final Set<String> SPECIAL_META_KEYS = Set.of(
            "structured_data", "title", "custom_head_code", "robots",
            "google_site_verification", "baidu_site_verification", "bing_site_verification",
            "yandex_site_verification", "sogou_site_verification", "so_site_verification",
            "shenma_site_verification", "yahoo_site_verification", "duckduckgo_site_verification",
            "twitter_site", "twitter_creator", "fb_app_id", "fb_page_url",
            "og_type", "og_site_name", "linkedin_company_id",
            "pinterest_verification", "pinterest_description",
            "wechat_miniprogram_id", "wechat_miniprogram_path", "qq_miniprogram_path",
            "_rawHtmlSnippets");

    private static final Set<String> ICON_META_KEYS = Set.of(
            "site_icon", "apple_touch_icon", "site_icon_192", "site_icon_512", "site_logo");

    private static final List<String> VERIFICATION_META_KEYS = List.of(
            "google_site_verification", "baidu_site_verification", "bing_site_verification",
            "yandex_site_verification", "sogou_site_verification", "so_site_verification",
            "shenma_site_verification", "yahoo_site_verification", "duckduckgo_site_verification");

    private static final Map<String, String> SOCIAL_MEDIA_META_KEYS = Map.ofEntries(
            Map.entry("twitter_site", "twitter:site"),
            Map.entry("twitter_creator", "twitter:creator"),
            Map.entry("fb_app_id", "fb:app_id"),
            Map.entry("fb_page_url", "fb:page_url"),
            Map.entry("og_type", "og:type"),
            Map.entry("og_site_name", "og:site_name"),
            Map.entry("linkedin_company_id", "linkedin:company"),
            Map.entry("pinterest_verification", "p:domain_verify"),
            Map.entry("pinterest_description", "pinterest:description"),
            Map.entry("wechat_miniprogram_id", "wechat:miniprogram"),
            Map.entry("wechat_miniprogram_path", "wechat:miniprogram:path"),
            Map.entry("qq_miniprogram_path", "qq:miniprogram:path"));

    @Value("${prerender.template-path:/app/web-dist/index.html}")
    private String templatePath;

    @Value("${prerender.output-root:/app/web-dist/prerender}")
    private String outputRoot;

    @Value("${prerender.admin-template-path:/app/admin-dist/index.html}")
    private String adminTemplatePath;

    @Value("${prerender.admin-output-root:/app/admin-dist/prerender}")
    private String adminOutputRoot;

    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final ObjectMapper objectMapper;

    private volatile String cachedTemplate;
    private volatile long templateLastModified = -1L;
    private volatile String cachedAdminTemplate;
    private volatile long adminTemplateLastModified = -1L;

    public PrerenderEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        MutableDataSet options = new MutableDataSet()
                .set(Parser.EXTENSIONS, List.of(TablesExtension.create()))
                .set(HtmlRenderer.ESCAPE_HTML, true)
                .set(HtmlRenderer.SOFT_BREAK, "<br />\n")
                // 尽量贴近 markdown-it-multimd-table 的列处理能力。
                .set(TablesExtension.COLUMN_SPANS, true)
                .set(TablesExtension.APPEND_MISSING_COLUMNS, false)
                .set(TablesExtension.DISCARD_EXTRA_COLUMNS, false)
                .set(TablesExtension.MIN_SEPARATOR_DASHES, 1)
                .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, false);
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    public String renderMarkdown(String markdown) {
        String safeMarkdown = StringEscapeUtils.unescapeHtml4(markdown == null ? "" : markdown);
        Node document = markdownParser.parse(safeMarkdown);
        return htmlRenderer.render(document);
    }

    public boolean isTemplateAvailable() {
        return Files.isRegularFile(Path.of(templatePath));
    }

    public boolean isAdminTemplateAvailable() {
        return Files.isRegularFile(Path.of(adminTemplatePath));
    }

    public void clearTemplateCache() {
        cachedTemplate = null;
        templateLastModified = -1L;
        cachedAdminTemplate = null;
        adminTemplateLastModified = -1L;
    }

    public String getTemplate() {
        Path path = Path.of(templatePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("SPA 模板文件不存在: " + templatePath);
        }

        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            long lastModified = lastModifiedTime.toMillis();
            if (cachedTemplate == null || templateLastModified != lastModified) {
                synchronized (this) {
                    if (cachedTemplate == null || templateLastModified != lastModified) {
                        cachedTemplate = Files.readString(path, StandardCharsets.UTF_8);
                        templateLastModified = lastModified;
                    }
                }
            }
            return cachedTemplate;
        } catch (IOException e) {
            throw new IllegalStateException("读取 SPA 模板失败: " + templatePath, e);
        }
    }

    public String getAdminTemplate() {
        Path path = Path.of(adminTemplatePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("后台 SPA 模板文件不存在: " + adminTemplatePath);
        }

        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            long lastModified = lastModifiedTime.toMillis();
            if (cachedAdminTemplate == null || adminTemplateLastModified != lastModified) {
                synchronized (this) {
                    if (cachedAdminTemplate == null || adminTemplateLastModified != lastModified) {
                        cachedAdminTemplate = Files.readString(path, StandardCharsets.UTF_8);
                        adminTemplateLastModified = lastModified;
                    }
                }
            }
            return cachedAdminTemplate;
        } catch (IOException e) {
            throw new IllegalStateException("读取后台 SPA 模板失败: " + adminTemplatePath, e);
        }
    }

    public String buildPage(PrerenderPageData data) {
        return buildPageFromTemplate(getTemplate(), data);
    }

    public String buildAdminShellPage(PrerenderPageData data) {
        return buildPageFromTemplate(getAdminTemplate(), data);
    }

    private String buildPageFromTemplate(String template, PrerenderPageData data) {
        Map<String, Object> meta = data.getMeta() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data.getMeta());
        String lang = StringUtils.hasText(data.getLang()) ? data.getLang() : "zh";
        String pageType = StringUtils.hasText(data.getPageType()) ? data.getPageType() : "article";
        String title = StringUtils.hasText(data.getTitle()) ? data.getTitle() : "POETIZE";
        String content = data.getContent() == null ? "" : data.getContent();

        String html = template;
        html = replaceHtmlLang(html, lang);
        html = replaceTitle(html, title);
        html = removeExistingSeoTags(html);

        if (StringUtils.hasText(stringValue(meta.get("site_icon")))) {
            html = DEFAULT_FAVICON_PATTERN.matcher(html).replaceAll("");
            html = DEFAULT_FAVICON_ID_PATTERN.matcher(html).replaceAll("");
        }

        List<String> headTags = buildHeadTags(meta);
        headTags.add(buildCriticalCss());
        html = insertBeforeTag(html, "</head>", String.join("\n", headTags));
        html = reorderWebpackCss(html);
        html = replaceBodyAttributes(html, pageType, lang);
        html = replaceAppContent(html, pageType, content);
        html = insertBeforeTag(html, "</body>", buildLoadingScript());
        return formatHead(html);
    }

    public void writeAdminShellPage(String html) {
        Path outputDir = Path.of(adminOutputRoot);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(
                    outputDir.resolve("index.html"),
                    html,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("写入后台预渲染文件失败: " + outputDir, e);
        }
    }

    public void writePage(String subPath, String lang, String html) {
        Path outputDir = resolveOutputDir(subPath);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(
                    outputDir.resolve(resolveIndexFilename(lang)),
                    html,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("写入预渲染文件失败: " + outputDir, e);
        }
    }

    public void deletePage(String subPath) {
        Path targetPath = resolveOutputDir(subPath);
        if (!Files.exists(targetPath)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(targetPath)) {
            pathStream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("删除预渲染文件失败: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("删除预渲染目录失败: " + targetPath, e);
        }
    }

    public void deleteIndexFiles(String subPath) {
        Path targetDir = resolveOutputDir(subPath);
        if (!Files.isDirectory(targetDir)) {
            return;
        }

        try (Stream<Path> fileStream = Files.list(targetDir)) {
            fileStream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("index(-[a-zA-Z-]+)?\\.html"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("删除预渲染首页文件失败: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("清理预渲染首页文件失败: " + targetDir, e);
        }
    }

    private Path resolveOutputDir(String subPath) {
        String normalized = normalizeSubPath(subPath);
        if (!StringUtils.hasText(normalized)) {
            return Path.of(outputRoot);
        }
        return Path.of(outputRoot).resolve(normalized);
    }

    private String normalizeSubPath(String subPath) {
        if (!StringUtils.hasText(subPath)) {
            return "";
        }
        return subPath.replace('\\', '/').replaceFirst("^/+", "");
    }

    private String resolveIndexFilename(String lang) {
        if (!StringUtils.hasText(lang) || "zh".equals(lang)) {
            return "index.html";
        }
        return "index-" + lang + ".html";
    }

    private String replaceHtmlLang(String html, String lang) {
        Matcher matcher = HTML_TAG_PATTERN.matcher(html);
        if (!matcher.find()) {
            return html;
        }

        String attrs = matcher.group(1);
        String replacement;
        if (attrs.matches("(?is).*\\slang\\s*=.*")) {
            replacement = "<html" + attrs.replaceAll("(?is)\\slang\\s*=\\s*[\"'][^\"']*[\"']", " lang=\"" + Matcher.quoteReplacement(lang) + "\"") + ">";
        } else {
            replacement = "<html" + attrs + " lang=\"" + escapeAttribute(lang) + "\">";
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private String replaceTitle(String html, String title) {
        return TITLE_PATTERN.matcher(html)
                .replaceFirst(Matcher.quoteReplacement("<title>" + escapeTitleHtml(title) + "</title>"));
    }

    private String escapeTitleHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String removeExistingSeoTags(String html) {
        String result = html;
        for (Pattern pattern : REMOVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }

    private List<String> buildHeadTags(Map<String, Object> meta) {
        List<String> headTags = new ArrayList<>();
        headTags.add("  <link rel=\"manifest\" href=\"/manifest.json\" data-prerender-manifest=\"true\">");
        headTags.add("  <link rel=\"dns-prefetch\" href=\"https://cdn.jsdelivr.net\">");

        if (meta.isEmpty()) {
            return headTags;
        }

        addIconTags(headTags, meta);
        addGenericMetaTags(headTags, meta);
        addStructuredDataTag(headTags, meta.get("structured_data"));
        addVerificationTags(headTags, meta);
        addRobotsTag(headTags, meta.get("robots"));
        addSocialMediaTags(headTags, meta);
        addHreflangTags(headTags, meta);
        addCustomHeadCode(headTags, meta.get("custom_head_code"));
        return headTags;
    }

    private void addIconTags(List<String> headTags, Map<String, Object> meta) {
        Map<String, Map<String, String>> iconMapping = Map.of(
                "site_icon", Map.of("rel", "icon", "sizes", "16x16 32x32 48x48"),
                "apple_touch_icon", Map.of("rel", "apple-touch-icon"),
                "site_icon_192", Map.of("rel", "icon", "sizes", "192x192"),
                "site_icon_512", Map.of("rel", "icon", "sizes", "512x512"),
                "site_logo", Map.of("rel", "icon", "sizes", "any"));

        for (Map.Entry<String, Map<String, String>> entry : iconMapping.entrySet()) {
            String url = stringValue(meta.get(entry.getKey()));
            if (!StringUtils.hasText(url)) {
                continue;
            }

            Map<String, String> attrs = new LinkedHashMap<>(entry.getValue());
            attrs.put("type", inferMimeType(url));

            StringBuilder tag = new StringBuilder("  <link href=\"")
                    .append(escapeAttribute(url))
                    .append("\"");
            attrs.forEach((key, value) -> tag.append(' ')
                    .append(key)
                    .append("=\"")
                    .append(escapeAttribute(value))
                    .append("\""));
            tag.append('>');
            headTags.add(tag.toString());
        }
    }

    private void addGenericMetaTags(List<String> headTags, Map<String, Object> meta) {
        Set<String> handledKeys = new HashSet<>(SPECIAL_META_KEYS);
        handledKeys.addAll(ICON_META_KEYS);

        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            String key = entry.getKey();
            if (handledKeys.contains(key) || key.startsWith("hreflang_")) {
                continue;
            }

            String value = stringValue(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }

            if ("canonical".equals(key)) {
                headTags.add("  <link rel=\"canonical\" href=\"" + escapeAttribute(value) + "\">");
            } else if ("description".equals(key) || "keywords".equals(key) || "author".equals(key)) {
                headTags.add("  <meta name=\"" + escapeAttribute(key) + "\" content=\"" + escapeAttribute(value) + "\">");
            } else {
                String attributeName = key.startsWith("twitter:") ? "name" : "property";
                headTags.add("  <meta " + attributeName + "=\"" + escapeAttribute(key) + "\" content=\"" + escapeAttribute(value) + "\">");
            }
        }

        if (!StringUtils.hasText(stringValue(meta.get("canonical")))) {
            String ogUrl = stringValue(meta.get("og:url"));
            if (StringUtils.hasText(ogUrl)) {
                headTags.add("  <link rel=\"canonical\" href=\"" + escapeAttribute(ogUrl) + "\">");
            }
        }
    }

    private void addStructuredDataTag(List<String> headTags, Object structuredData) {
        if (structuredData == null) {
            return;
        }

        String jsonLdContent = stringifyStructuredData(structuredData);
        if (!StringUtils.hasText(jsonLdContent)) {
            return;
        }
        headTags.add("  <script type=\"application/ld+json\" data-prerender-structured-data=\"true\">"
                + jsonLdContent + "</script>");
    }

    private void addVerificationTags(List<String> headTags, Map<String, Object> meta) {
        for (String tagKey : VERIFICATION_META_KEYS) {
            String value = stringValue(meta.get(tagKey));
            if (!StringUtils.hasText(value)) {
                continue;
            }

            String decoded = StringEscapeUtils.unescapeHtml4(value.trim());
            if (decoded.startsWith("<")) {
                headTags.add("  " + decoded);
            } else {
                headTags.add("  <meta name=\"" + escapeAttribute(tagKey.replace('_', '-'))
                        + "\" content=\"" + escapeAttribute(decoded) + "\">");
            }
        }
    }

    private void addRobotsTag(List<String> headTags, Object robots) {
        String robotsValue = stringValue(robots);
        if (!StringUtils.hasText(robotsValue)) {
            return;
        }
        headTags.add("  <meta name=\"robots\" content=\"" + escapeAttribute(robotsValue)
                + "\" data-prerender-robots=\"true\">");
    }

    private void addSocialMediaTags(List<String> headTags, Map<String, Object> meta) {
        for (Map.Entry<String, String> entry : SOCIAL_MEDIA_META_KEYS.entrySet()) {
            String value = stringValue(meta.get(entry.getKey()));
            if (!StringUtils.hasText(value)) {
                continue;
            }

            String attrName = entry.getValue().startsWith("og:") ? "property" : "name";
            headTags.add("  <meta " + attrName + "=\"" + escapeAttribute(entry.getValue())
                    + "\" content=\"" + escapeAttribute(value) + "\" data-prerender-social=\"true\">");
        }
    }

    private void addHreflangTags(List<String> headTags, Map<String, Object> meta) {
        meta.forEach((key, value) -> {
            if (key.startsWith("hreflang_") && StringUtils.hasText(stringValue(value))) {
                headTags.add("  " + value.toString());
            }
        });
    }

    private void addCustomHeadCode(List<String> headTags, Object customHeadCode) {
        String code = stringValue(customHeadCode);
        if (!StringUtils.hasText(code)) {
            return;
        }
        headTags.add("  " + StringEscapeUtils.unescapeHtml4(code));
    }

    private String buildCriticalCss() {
        return "  <style>\n"
                + "      /* 防止FOUC的关键样式 */\n"
                + "      html.prerender #app { visibility: visible; opacity: 1; }\n"
                + "      html:not(.loaded) #app { visibility: hidden; }\n"
                + "      html.loaded #app { visibility: visible; opacity: 1; transition: opacity 0.3s ease-in-out; }\n"
                + "      .article-detail, .home-prerender, .favorite-prerender, .favorites-prerender, .sort-prerender, .sort-list-prerender {\n"
                + "        min-height: 200px; position: relative; opacity: 1; transform: translateY(0); animation: fadeIn 0.5s ease-in-out;\n"
                + "      }\n"
                + "      @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }\n"
                + "      .article-detail::before, .home-prerender::before, .favorite-prerender::before, .favorites-prerender::before, .sort-prerender::before, .sort-list-prerender::before {\n"
                + "        content: ''; position: absolute; top: 0; left: 0; right: 0; bottom: 0;\n"
                + "        background: linear-gradient(90deg, rgba(240,240,240,0.1) 25%, transparent 37%, rgba(240,240,240,0.1) 63%);\n"
                + "        animation: shimmer 1.5s ease-in-out infinite; z-index: 1; opacity: 0; transition: opacity 0.3s ease; pointer-events: none;\n"
                + "      }\n"
                + "      html:not(.loaded) .article-detail::before, html:not(.loaded) .home-prerender::before, html:not(.loaded) .favorite-prerender::before, html:not(.loaded) .favorites-prerender::before, html:not(.loaded) .sort-prerender::before, html:not(.loaded) .sort-list-prerender::before { opacity: 1; }\n"
                + "      @keyframes shimmer { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }\n"
                + "      @media (max-width: 768px) { .article-detail, .home-prerender, .favorite-prerender, .favorites-prerender, .sort-prerender, .sort-list-prerender { min-height: 150px; padding: 1rem; } }\n"
                + "    </style>";
    }

    private String reorderWebpackCss(String html) {
        Matcher cssMatcher = WEBPACK_CSS_PATTERN.matcher(html);
        List<String> cssLinks = new ArrayList<>();
        while (cssMatcher.find()) {
            cssLinks.add(cssMatcher.group());
        }

        if (cssLinks.isEmpty()) {
            return html;
        }

        Matcher titleMatcher = TITLE_PATTERN.matcher(html);
        if (!titleMatcher.find()) {
            return html;
        }

        String titleTag = titleMatcher.group();
        String withoutCss = WEBPACK_CSS_PATTERN.matcher(html).replaceAll("");
        return TITLE_PATTERN.matcher(withoutCss)
                .replaceFirst(Matcher.quoteReplacement(String.join("\n  ", cssLinks) + "\n  " + titleTag));
    }

    private String replaceBodyAttributes(String html, String pageType, String lang) {
        Matcher matcher = BODY_PATTERN.matcher(html);
        if (!matcher.find()) {
            return html;
        }

        String attrs = matcher.group(1)
                .replaceAll("(?is)\\s*data-prerender-type\\s*=\\s*[\"'][^\"']*[\"']", "")
                .replaceAll("(?is)\\s*data-prerender-lang\\s*=\\s*[\"'][^\"']*[\"']", "");
        String replacement = "<body" + attrs
                + " data-prerender-type=\"" + escapeAttribute(pageType)
                + "\" data-prerender-lang=\"" + escapeAttribute(lang) + "\">";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private String replaceAppContent(String html, String pageType, String content) {
        String appClass = "article".equals(pageType) ? " class=\"article-detail\"" : "";
        return APP_PATTERN.matcher(html).replaceFirst(Matcher.quoteReplacement("<div id=\"app\"" + appClass + ">" + content + "</div>"));
    }

    private String buildLoadingScript() {
        return "<script>\n"
                + "(function(){\n"
                + "  document.documentElement.classList.add('prerender');\n"
                + "  function handleImageLoad(){\n"
                + "    var containers=document.querySelectorAll('.article-detail,.home-prerender,.favorite-prerender,.favorites-prerender,.sort-prerender,.sort-list-prerender');\n"
                + "    containers.forEach(function(c){\n"
                + "      c.querySelectorAll('img').forEach(function(img){\n"
                + "        if(img.complete&&img.naturalWidth>0){img.classList.add('loaded');}\n"
                + "        else if(img.src&&(img.src.startsWith('data:')||img.src.startsWith('blob:'))){img.classList.add('loaded');}\n"
                + "        else if(img.src){\n"
                + "          img.addEventListener('load',function(){this.classList.add('loaded');},{once:true});\n"
                + "          img.addEventListener('error',function(){this.classList.add('loaded');},{once:true});\n"
                + "          setTimeout(function(){if(!img.classList.contains('loaded'))img.classList.add('loaded');},5000);\n"
                + "        }else{img.classList.add('loaded');}\n"
                + "      });\n"
                + "    });\n"
                + "  }\n"
                + "  function markAsLoaded(){requestAnimationFrame(function(){document.documentElement.classList.add('loaded');document.documentElement.classList.remove('prerender');handleImageLoad();});}\n"
                + "  if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',markAsLoaded);}else{markAsLoaded();}\n"
                + "  window.addEventListener('app-mounted',function(){var a=document.getElementById('app');if(a)a.classList.add('loaded');handleImageLoad();});\n"
                + "  if(document.fonts){document.fonts.ready.then(function(){document.documentElement.classList.add('fonts-loaded');});}\n"
                + "})();\n"
                + "</script>";
    }

    private String formatHead(String html) {
        int headEnd = html.indexOf("</head>");
        if (headEnd <= 0) {
            return html;
        }

        String headPart = html.substring(0, headEnd)
                .replace("<meta", "\n  <meta")
                .replace("<link", "\n  <link")
                .replace("<style", "\n  <style")
                .replace("</style>", "</style>\n")
                .replaceAll("\n\\s*\n", "\n");
        return headPart + "\n</head>" + html.substring(headEnd + "</head>".length());
    }

    private String insertBeforeTag(String html, String tag, String content) {
        int position = html.indexOf(tag);
        if (position < 0) {
            return html;
        }
        return html.substring(0, position) + content + "\n" + html.substring(position);
    }

    private String stringifyStructuredData(Object structuredData) {
        if (structuredData instanceof String stringValue) {
            return stringValue.trim();
        }

        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (Exception e) {
            log.warn("序列化 structured_data 失败: {}", e.getMessage());
            return "";
        }
    }

    private String inferMimeType(String url) {
        String cleanUrl = url.split("\\?")[0].split("#")[0];
        int extensionIndex = cleanUrl.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == cleanUrl.length() - 1) {
            return "image/png";
        }

        return switch (cleanUrl.substring(extensionIndex + 1).toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "avif" -> "image/avif";
            default -> "image/png";
        };
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String escapeAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
