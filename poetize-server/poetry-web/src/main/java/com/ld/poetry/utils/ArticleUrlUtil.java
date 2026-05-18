package com.ld.poetry.utils;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ArticleUrlUtil {

    private static final Pattern VALID_SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,158}[a-z0-9])?$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    private ArticleUrlUtil() {
    }

    public static String normalizeSlug(String rawSlug) {
        if (!StringUtils.hasText(rawSlug)) {
            return null;
        }
        String normalized = rawSlug.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    public static boolean isValidSlug(String slug) {
        return StringUtils.hasText(slug)
                && !NUMERIC_PATTERN.matcher(slug).matches()
                && VALID_SLUG_PATTERN.matcher(slug).matches();
    }

    public static boolean isNumericToken(String token) {
        return StringUtils.hasText(token) && NUMERIC_PATTERN.matcher(token.trim()).matches();
    }

    public static String resolveToken(Integer articleId, String articleSlug) {
        String normalizedSlug = normalizeSlug(articleSlug);
        if (isValidSlug(normalizedSlug)) {
            return normalizedSlug;
        }
        return articleId == null ? "" : String.valueOf(articleId);
    }

    public static String buildArticlePath(Integer articleId, String articleSlug) {
        return "/article/" + resolveToken(articleId, articleSlug);
    }

    public static String buildArticlePath(Integer articleId, String articleSlug, String language, String sourceLanguage) {
        String token = resolveToken(articleId, articleSlug);
        if (StringUtils.hasText(language) && !language.equals(sourceLanguage)) {
            return "/article/" + language + "/" + token;
        }
        return "/article/" + token;
    }
}
