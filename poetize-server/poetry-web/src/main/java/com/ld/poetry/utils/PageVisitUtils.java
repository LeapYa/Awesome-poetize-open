package com.ld.poetry.utils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 页面访问统计的路径过滤与标准化工具。
 */
public final class PageVisitUtils {

    private static final Map<Character, String[]> EXCLUDED_PREFIX_BUCKETS;

    private static final Set<String> EXCLUDED_EXACT = Set.of(
            "/favicon.ico", "/robots.txt", "/manifest.json", "/sw.js", "/sitemap.xml"
    );

    private static final Set<String> API_ROUTE_SEGMENTS = Set.of(
            "user", "article", "weiYan", "treeHole", "comment", "sort", "label"
    );

    private static final Set<String> ARTICLE_API_ACTIONS = Set.of(
            "getArticleByPathNoCount", "getArticleById", "getTranslation", "getAvailableLanguages",
            "getArticleSaveStatus", "streamArticleSaveStatus", "streamArticleSaveStatusBatch",
            "listArticle", "listSortArticle", "saveArticle", "saveArticleAsync",
            "updateArticle", "updateArticleAsync", "deleteArticle"
    );

    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            ".js", ".mjs", ".css", ".map",
            ".jpg", ".jpeg", ".png", ".gif", ".ico", ".webp", ".svg",
            ".woff", ".woff2", ".ttf", ".eot",
            ".json", ".xml", ".txt", ".mp4", ".webm", ".ogg", ".avi", ".mov", ".flv", ".wmv", ".mkv"
    );

    static {
        String[] allPrefixes = {
                "/api/", "/admin", "/track/",
                "/webInfo", "/sysConfig", "/sysPlugin", "/resource",
                "/imageCompress", "/captcha", "/comment", "/family",
                "/qiniu", "/qrcode", "/imChat", "/collect", "/internal/monitor",
                "/static/", "/css/", "/js/", "/images/", "/assets/", "/libs/", "/uploads/",
                "/seo/", "/python/", "/ws/", "/login/", "/callback/",
                "/oauth/", "/internal_proxy/", "/sitemap", "/.well-known/"
        };

        Map<Character, java.util.List<String>> temp = new HashMap<>();
        for (String prefix : allPrefixes) {
            char key = prefix.length() > 1 ? prefix.charAt(1) : '/';
            temp.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(prefix);
        }

        Map<Character, String[]> buckets = new HashMap<>(temp.size());
        temp.forEach((key, values) -> buckets.put(key, values.toArray(new String[0])));
        EXCLUDED_PREFIX_BUCKETS = Map.copyOf(buckets);
    }

    private PageVisitUtils() {
    }

    public static boolean isPageVisit(String uri) {
        String path = extractPath(uri);
        if (path == null || path.isEmpty()) return false;

        if (EXCLUDED_EXACT.contains(path)) return false;

        if (path.length() > 1) {
            String[] bucket = EXCLUDED_PREFIX_BUCKETS.get(path.charAt(1));
            if (bucket != null) {
                for (String prefix : bucket) {
                    if (path.startsWith(prefix)) return false;
                }
            }
        }

        if (path.indexOf("/upload/") >= 0 || path.indexOf("/download/") >= 0) return false;

        if (isArticlePageVisit(path)) {
            return true;
        }

        if (path.length() > 2 && path.charAt(0) == '/') {
            int secondSlash = path.indexOf('/', 1);
            if (secondSlash > 1 && secondSlash + 1 < path.length()) {
                char afterSlash = path.charAt(secondSlash + 1);
                if ((afterSlash >= 'a' && afterSlash <= 'z') || (afterSlash >= 'A' && afterSlash <= 'Z')) {
                    String segment = path.substring(1, secondSlash);
                    if (API_ROUTE_SEGMENTS.contains(segment)) return false;
                }
            }
        }

        int dotIdx = path.lastIndexOf('.');
        if (dotIdx > 0 && EXCLUDED_EXTENSIONS.contains(path.substring(dotIdx).toLowerCase())) {
            return "/index.html".equals(path);
        }

        return path.indexOf("%E") < 0 && path.indexOf("%e") < 0;
    }

    public static boolean isArticlePageVisit(String uri) {
        return extractArticleToken(uri) != null;
    }

    public static String extractArticleToken(String uri) {
        String path = extractPath(uri);
        if (path == null || !path.startsWith("/article/")) {
            return null;
        }

        String[] segments = path.split("/");
        if (segments.length == 3) {
            return articleTokenOrNull(segments[2]);
        }
        if (segments.length == 4 && looksLikeLanguage(segments[2])) {
            return articleTokenOrNull(segments[3]);
        }
        return null;
    }

    public static String extractPath(String uri) {
        String normalized = normalizeVisitUri(uri);
        int queryIdx = normalized.indexOf('?');
        return queryIdx > 0 ? normalized.substring(0, queryIdx) : normalized;
    }

    public static String normalizeVisitUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return "/";
        }

        String value = uri.trim();
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                URI parsed = URI.create(value);
                String path = parsed.getRawPath();
                String query = parsed.getRawQuery();
                return buildPathAndQuery(path, query);
            }
        } catch (Exception ignored) {
            // Fall back to string normalization below.
        }

        int fragmentIdx = value.indexOf('#');
        if (fragmentIdx >= 0) {
            value = value.substring(0, fragmentIdx);
        }

        if (value.isEmpty()) {
            return "/";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    private static String buildPathAndQuery(String path, String query) {
        String safePath = (path == null || path.isBlank()) ? "/" : path;
        if (!safePath.startsWith("/")) {
            safePath = "/" + safePath;
        }
        return query == null || query.isBlank() ? safePath : safePath + "?" + query;
    }

    private static String articleTokenOrNull(String token) {
        if (token == null || token.isBlank() || ARTICLE_API_ACTIONS.contains(token)) {
            return null;
        }
        return token;
    }

    private static boolean looksLikeLanguage(String value) {
        if (value == null || value.isBlank() || ARTICLE_API_ACTIONS.contains(value)) {
            return false;
        }
        return value.matches("[A-Za-z]{2,8}([-_][A-Za-z0-9]{2,8})?");
    }
}
