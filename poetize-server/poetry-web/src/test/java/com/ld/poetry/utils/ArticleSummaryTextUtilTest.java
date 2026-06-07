package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleSummaryTextUtilTest {

    @Test
    void stripsMarkdownNewlinesHtmlAndQuotes() {
        String markdown = "# 标题\n\n这是 \"Spring\" 的 [链接](https://example.com) 和 `code`\n"
                + "- 列表项\n<img src=\"x\">**重点**";

        String result = ArticleSummaryTextUtil.toPlainText(markdown, 200);

        assertEquals("标题 这是 \"Spring\" 的 链接 和 code 列表项 重点", result);
        assertFalse(result.contains("\n"));
        assertTrue(result.contains("\""));
        assertFalse(result.contains("<img"));
    }

    @Test
    void truncatesLongTextAfterNormalization() {
        assertEquals("abc...", ArticleSummaryTextUtil.toPlainText("abcdef", 3));
    }
}
