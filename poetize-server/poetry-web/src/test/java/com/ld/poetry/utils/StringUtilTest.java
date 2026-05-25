package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringUtilTest {

    @Test
    void removeHtmlShouldNeutralizeAttributeBreakingCharacters() {
        String input = "[x,x\" onerror=\"alert(1)] <b>&";

        String actual = StringUtil.removeHtml(input);

        assertEquals("[x,x&quot; onerror=&quot;alert(1)] 《b》&amp;", actual);
    }

    @Test
    void removeHtmlShouldAllowNullInput() {
        assertNull(StringUtil.removeHtml(null));
    }

    @Test
    void highlightTextShouldHandleRegexReplacementCharacters() {
        String actual = StringUtil.highlightText("Price is $9.99 and path C:\\temp", "$9.99 C:\\temp",
                "<mark>", "</mark>");

        assertEquals("Price is <mark>$9.99</mark> and path <mark>C:\\temp</mark>", actual);
    }

    @Test
    void highlightTextWithRegexShouldHandleDollarAndBackslashMatches() {
        String actual = StringUtil.highlightTextWithRegex("Price is $9.99 and path C:\\temp",
                "\\$9\\.99|C:\\\\temp",
                "<mark>",
                "</mark>");

        assertEquals("Price is <mark>$9.99</mark> and path <mark>C:\\temp</mark>", actual);
    }
}
