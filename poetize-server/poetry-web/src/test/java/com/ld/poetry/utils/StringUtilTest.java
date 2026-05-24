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
}
