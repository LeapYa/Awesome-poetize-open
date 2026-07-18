package com.ld.poetry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ResourceReferenceService {

    private static final Pattern ABSOLUTE_URL_PATTERN = Pattern.compile(
            "(?i)(?:https?:)?//[^\\s\\\"'<>\\)\\]\\}]+"
    );

    private static final List<ReferenceColumn> REFERENCE_COLUMNS = List.of(
            articleColumn("article_cover"),
            articleColumn("video_url"),
            articleColumn("article_content"),
            column("article_translation", "content", "article_id", CacheDomain.ARTICLE),
            column("comment", "comment_content", null, CacheDomain.COMMENT),
            column("wei_yan", "content", null, CacheDomain.COMMENT),
            column("web_info", "background_image", null, CacheDomain.WEB_INFO),
            column("web_info", "avatar", null, CacheDomain.WEB_INFO),
            column("web_info", "random_avatar", null, CacheDomain.WEB_INFO),
            column("web_info", "random_cover", null, CacheDomain.WEB_INFO),
            column("web_info", "waifu_json", null, CacheDomain.WEB_INFO),
            column("web_info", "nav_config", null, CacheDomain.WEB_INFO),
            column("web_info", "footer", null, CacheDomain.WEB_INFO),
            column("web_info", "notices", null, CacheDomain.WEB_INFO),
            column("web_info", "footer_background_image", null, CacheDomain.WEB_INFO),
            column("web_info", "footer_background_config", null, CacheDomain.WEB_INFO),
            column("web_info", "mouse_click_effect_config", null, CacheDomain.WEB_INFO),
            column("web_info", "mobile_drawer_config", null, CacheDomain.WEB_INFO),
            column("resource_path", "cover", null, CacheDomain.PRERENDER),
            column("resource_path", "url", null, CacheDomain.PRERENDER),
            column("resource_path", "introduction", null, CacheDomain.PRERENDER),
            column("resource_path", "remark", null, CacheDomain.PRERENDER),
            column("user", "avatar", "id", CacheDomain.USER),
            column("tree_hole", "avatar", null, CacheDomain.OTHER),
            column("family", "bg_cover", null, CacheDomain.FAMILY),
            column("family", "man_cover", null, CacheDomain.FAMILY),
            column("family", "woman_cover", null, CacheDomain.FAMILY),
            column("im_chat_group", "avatar", null, CacheDomain.OTHER),
            column("im_chat_user_message", "content", null, CacheDomain.OTHER),
            column("im_chat_user_group_message", "content", null, CacheDomain.OTHER),
            column("seo_config", "site_logo", null, CacheDomain.SEO),
            column("seo_config", "site_icon", null, CacheDomain.SEO),
            column("seo_config", "site_icon_192", null, CacheDomain.SEO),
            column("seo_config", "site_icon_512", null, CacheDomain.SEO),
            column("seo_config", "apple_touch_icon", null, CacheDomain.SEO),
            column("seo_config", "custom_head_code", null, CacheDomain.SEO),
            column("seo_social_media", "og_image", null, CacheDomain.SEO),
            column("seo_pwa_config", "pwa_screenshot_desktop", null, CacheDomain.SEO),
            column("seo_pwa_config", "pwa_screenshot_mobile", null, CacheDomain.SEO),
            column("sys_ai_config", "chat_avatar", null, CacheDomain.CONFIG),
            column("sys_config", "config_value", null, CacheDomain.CONFIG),
            column("sys_plugin", "plugin_config", null, CacheDomain.CONFIG),
            column("sys_plugin", "manifest", null, CacheDomain.CONFIG),
            column("sys_plugin", "frontend_css", null, CacheDomain.CONFIG)
    );

    private final JdbcTemplate jdbcTemplate;

    @Value("${local.downloadUrl:/static/}")
    private String localDownloadUrl = "/static/";

    /**
     * 扫描白名单字段中的完整 URL 和本站静态相对地址。
     * 候选身份包含查询参数但不包含片段；片段不会参与 HTTP 取回内容。
     */
    public List<ReferenceCandidate> scanReferences() {
        Map<String, MutableReferenceCandidate> candidates = new LinkedHashMap<>();
        Pattern relativePattern = relativeUrlPattern();
        for (ReferenceColumn reference : REFERENCE_COLUMNS) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(reference.scanSql());
            for (Map<String, Object> row : rows) {
                Object value = row.get("ref_value");
                if (value == null) {
                    continue;
                }
                collectCandidates(String.valueOf(value), ABSOLUTE_URL_PATTERN, candidates);
                collectCandidates(String.valueOf(value), relativePattern, candidates);
            }
        }
        return candidates.values().stream()
                .map(MutableReferenceCandidate::freeze)
                .sorted(Comparator.comparing(ReferenceCandidate::sourceUrl))
                .toList();
    }

    public int countReferences(String sourceUrl) {
        String sourceIdentity = normalizeSourceIdentity(sourceUrl);
        Pattern pattern = isAbsoluteUrl(sourceIdentity) ? ABSOLUTE_URL_PATTERN : relativeUrlPattern();
        int count = 0;
        for (ReferenceColumn reference : REFERENCE_COLUMNS) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(reference.selectSql(), sourceIdentity);
            for (Map<String, Object> row : rows) {
                Object value = row.get("ref_value");
                if (value != null) {
                    count += countExactTokens(String.valueOf(value), pattern, sourceIdentity);
                }
            }
        }
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReplacementResult replaceReferences(String sourceUrl, String targetUrl) {
        if (!StringUtils.hasText(sourceUrl) || !StringUtils.hasText(targetUrl)) {
            throw new IllegalArgumentException("资源引用替换路径不能为空");
        }
        String sourceIdentity = normalizeSourceIdentity(sourceUrl);
        if (sourceIdentity.equals(targetUrl)) {
            return ReplacementResult.empty();
        }

        Pattern pattern = isAbsoluteUrl(sourceIdentity) ? ABSOLUTE_URL_PATTERN : relativeUrlPattern();
        MutableReplacementResult result = new MutableReplacementResult();
        for (ReferenceColumn reference : REFERENCE_COLUMNS) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(reference.selectSql(), sourceIdentity);
            for (Map<String, Object> row : rows) {
                Object rowId = row.get("row_id");
                String originalValue = String.valueOf(row.get("ref_value"));
                String replacedValue = replaceExactTokens(originalValue, pattern, sourceIdentity, targetUrl);
                if (originalValue.equals(replacedValue)) {
                    continue;
                }
                int updated = jdbcTemplate.update(
                        reference.updateSql(),
                        replacedValue,
                        rowId,
                        originalValue
                );
                if (updated != 1) {
                    throw new ConcurrentModificationException(
                            "资源引用在接管期间被修改: " + reference.table() + "." + reference.column()
                    );
                }
                result.record(reference, row);
            }
        }
        return result.freeze();
    }

    private void collectCandidates(String value,
                                   Pattern pattern,
                                   Map<String, MutableReferenceCandidate> candidates) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            String identity = stripFragment(matcher.group());
            if (!StringUtils.hasText(identity) || identity.startsWith("/media/")) {
                continue;
            }
            candidates.computeIfAbsent(identity, MutableReferenceCandidate::new).increment();
        }
    }

    private int countExactTokens(String value, Pattern pattern, String sourceIdentity) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            if (sourceIdentity.equals(stripFragment(matcher.group()))) {
                count++;
            }
        }
        return count;
    }

    private String replaceExactTokens(String value,
                                      Pattern pattern,
                                      String sourceIdentity,
                                      String targetUrl) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer(value.length());
        while (matcher.find()) {
            String candidate = matcher.group();
            String replacement = candidate;
            if (sourceIdentity.equals(stripFragment(candidate))) {
                replacement = targetUrl + fragment(candidate);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Pattern relativeUrlPattern() {
        String prefix = normalizedLocalDownloadPrefix();
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}_:/.-])"
                        + Pattern.quote(prefix)
                        + "[^\\s\\\"'<>\\)\\]\\}]+"
        );
    }

    private String normalizedLocalDownloadPrefix() {
        String value = StringUtils.hasText(localDownloadUrl) ? localDownloadUrl.trim() : "/static/";
        if (isAbsoluteUrl(value)) {
            try {
                java.net.URI uri = java.net.URI.create(value.startsWith("//") ? "https:" + value : value);
                value = uri.getRawPath();
            } catch (IllegalArgumentException ignored) {
                value = "/static/";
            }
        }
        if (!StringUtils.hasText(value) || !value.startsWith("/")) {
            value = "/static/";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private String normalizeSourceIdentity(String sourceUrl) {
        String normalized = stripFragment(sourceUrl == null ? null : sourceUrl.trim());
        if (!StringUtils.hasText(normalized) || normalized.length() > 2048) {
            throw new IllegalArgumentException("历史资源URL不合法");
        }
        return normalized;
    }

    private String stripFragment(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        int fragment = value.indexOf('#');
        return fragment >= 0 ? value.substring(0, fragment) : value;
    }

    private String fragment(String value) {
        int fragment = value.indexOf('#');
        return fragment >= 0 ? value.substring(fragment) : "";
    }

    private boolean isAbsoluteUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//");
    }

    private static ReferenceColumn articleColumn(String column) {
        return column("article", column, "id", CacheDomain.ARTICLE);
    }

    private static ReferenceColumn column(String table, String column, String cacheIdColumn, CacheDomain domain) {
        return new ReferenceColumn(table, column, cacheIdColumn, domain);
    }

    private enum CacheDomain {
        ARTICLE,
        USER,
        COMMENT,
        WEB_INFO,
        FAMILY,
        CONFIG,
        SEO,
        PRERENDER,
        OTHER
    }

    private record ReferenceColumn(String table, String column, String cacheIdColumn, CacheDomain domain) {
        private String scanSql() {
            String cacheIdSelect = StringUtils.hasText(cacheIdColumn)
                    ? ", `" + cacheIdColumn + "` AS cache_id"
                    : "";
            return "SELECT `id` AS row_id, `" + column + "` AS ref_value" + cacheIdSelect
                    + " FROM `" + table + "`"
                    + " WHERE `" + column + "` IS NOT NULL AND `" + column + "` <> ''";
        }

        private String selectSql() {
            return scanSql() + " AND LOCATE(?, `" + column + "`) > 0";
        }

        private String updateSql() {
            return "UPDATE `" + table + "` SET `" + column + "` = ?"
                    + " WHERE `id` = ? AND `" + column + "` = ?";
        }
    }

    public record ReferenceCandidate(String sourceUrl, int referenceCount) {
    }

    public record ReplacementResult(
            int updatedRows,
            Set<Integer> articleIds,
            Set<Integer> userIds,
            Set<String> changedDomains
    ) {
        private static ReplacementResult empty() {
            return new ReplacementResult(0, Set.of(), Set.of(), Set.of());
        }
    }

    private static final class MutableReferenceCandidate {
        private final String sourceUrl;
        private int referenceCount;

        private MutableReferenceCandidate(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        private void increment() {
            referenceCount++;
        }

        private ReferenceCandidate freeze() {
            return new ReferenceCandidate(sourceUrl, referenceCount);
        }
    }

    private static final class MutableReplacementResult {
        private int updatedRows;
        private final Set<Integer> articleIds = new LinkedHashSet<>();
        private final Set<Integer> userIds = new LinkedHashSet<>();
        private final Set<String> changedDomains = new LinkedHashSet<>();

        private void record(ReferenceColumn reference, Map<String, Object> row) {
            updatedRows++;
            changedDomains.add(reference.domain().name());
            Object cacheId = row.get("cache_id");
            if (!(cacheId instanceof Number number)) {
                return;
            }
            if (reference.domain() == CacheDomain.ARTICLE) {
                articleIds.add(number.intValue());
            } else if (reference.domain() == CacheDomain.USER) {
                userIds.add(number.intValue());
            }
        }

        private ReplacementResult freeze() {
            return new ReplacementResult(
                    updatedRows,
                    Set.copyOf(articleIds),
                    Set.copyOf(userIds),
                    Set.copyOf(changedDomains)
            );
        }
    }
}