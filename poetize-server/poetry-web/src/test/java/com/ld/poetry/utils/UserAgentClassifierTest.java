package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserAgentClassifierTest {

    @Test
    void classifiesLeakixL9scanAsScannerBeforeDesktopFallback() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (l9scan/2.0.1313e22383e27383e27343; +https://leakix.net)");

        assertEquals("scanner", info.type());
        assertEquals("扫描器", info.typeLabel());
        assertEquals("LeakIX", info.name());
    }

    @Test
    void keepsNormalDesktopBrowserAsPc() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36");

        assertEquals("pc", info.type());
        assertEquals("PC端", info.typeLabel());
        assertEquals("Chrome", info.name());
    }

    @Test
    void aggregatesScannerRowsSeparatelyFromPcBrowsers() {
        List<Map<String, Object>> rows = List.of(
                Map.of("user_agent",
                        "Mozilla/5.0 (l9scan/2.0.1313e22383e27383e27343; +https://leakix.net)",
                        "num", 26),
                Map.of("user_agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                        "num", 3));

        List<Map<String, Object>> result = UserAgentClassifier.aggregateRawUserAgentCounts(rows);

        assertEquals("scanner", result.get(0).get("ua_type"));
        assertEquals("扫描器", result.get(0).get("ua_type_label"));
        assertEquals("LeakIX", result.get(0).get("ua_name"));
        assertEquals(26L, result.get(0).get("num"));
    }
}
