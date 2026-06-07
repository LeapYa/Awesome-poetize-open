package com.ld.poetry.utils;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

/**
 * Normalizes article summaries and content fallbacks for SEO/list snippets.
 */
public final class ArticleSummaryTextUtil {

    private ArticleSummaryTextUtil() {
    }

    public static String toPlainText(String text) {
        return toPlainText(text, 0);
    }

    public static String toPlainText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String result = StringEscapeUtils.unescapeHtml4(text);
        result = result.replaceAll("(?s)```.*?```", " ");
        result = result.replaceAll("(?s)~~~.*?~~~", " ");
        result = result.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        result = result.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        result = result.replaceAll("<[^>]*>", " ");
        result = result.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        result = result.replaceAll("(?m)^\\s*>\\s?", "");
        result = result.replaceAll("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)", "");
        result = result.replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", " ");
        result = result.replaceAll("`([^`]*)`", "$1");
        result = result.replaceAll("(\\*\\*|__)(.*?)\\1", "$2");
        result = result.replaceAll("(\\*|_)(.*?)\\1", "$2");
        result = result.replaceAll("~~(.*?)~~", "$1");
        result = result.replace('|', ' ');
        result = result.replaceAll("\\s+", " ").trim();

        if (maxLength > 0 && result.length() > maxLength) {
            return result.substring(0, Math.max(0, maxLength)).trim() + "...";
        }
        return result;
    }
}
