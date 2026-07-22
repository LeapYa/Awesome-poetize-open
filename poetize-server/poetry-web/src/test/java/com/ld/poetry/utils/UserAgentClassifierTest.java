package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void classifiesPaloAltoNetworksAsScanner() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Hello from Palo Alto Networks, find out more about our scans in https://docs-cortex.paloaltonetworks.com/r/1/Cortex-Xpanse/Scanning-activity");

        assertEquals("scanner", info.type());
        assertEquals("扫描器", info.typeLabel());
        assertEquals("Palo Alto Networks Scanner", info.name());
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
    void classifiesWebdriverBrowserAsAutomation() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of("webdriver", "true"));

        assertEquals("automation", info.type());
        assertEquals("自动化访问", info.typeLabel());
        assertEquals("Chrome WebDriver", info.name());
    }

    @Test
    void classifiesJsNativeTamperingAsAutomation() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of(
                        "automationScore", "75",
                        "automationVerdict", "LIKELY_BOT",
                        "automationSignals", "pqn",
                        "permissionsQueryNative", "0"));

        assertEquals("automation", info.type());
        assertEquals("自动化浏览器（JS原生性异常）", info.name());
    }

    @Test
    void classifiesEnvironmentInconsistencyAsSuspiciousAutomation() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of(
                        "automationScore", "30",
                        "automationVerdict", "SUSPICIOUS",
                        "automationSignals", "wutc,wdm",
                        "platform", "Win32",
                        "timezone", "UTC",
                        "deviceMemory", "null"));

        assertEquals("automation", info.type());
        assertEquals("疑似自动化浏览器", info.name());
    }

    @Test
    void keepsNativeConsistentBrowserSignalsAsPc() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of(
                        "automationScore", "0",
                        "automationVerdict", "LIKELY_HUMAN",
                        "permissionsQueryNative", "1",
                        "pluginsItemNative", "1",
                        "platform", "Win32",
                        "timezone", "Asia/Shanghai",
                        "deviceMemory", "8",
                        "pluginCount", "5",
                        "languageCount", "2"));

        assertEquals("pc", info.type());
        assertEquals("Chrome", info.name());
    }

    @Test
    void classifiesBrowserUaWithoutBrowserHeadersAsDisguisedCrawler() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of("visitSource", "nginx", "headerSnapshot", "1", "accept", "application/json"));

        assertEquals("crawler", info.type());
        assertEquals("爬虫", info.typeLabel());
        assertEquals("伪装浏览器请求", info.name());
    }

    @Test
    void doesNotTreatWildcardAcceptAloneAsDisguisedBrowserRequest() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of("visitSource", "nginx", "headerSnapshot", "1", "accept", "*/*"));

        assertEquals("pc", info.type());
        assertEquals("Chrome", info.name());
    }

    @Test
    void classifiesCurlAsCrawler() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify("curl/8.5.0");

        assertEquals("crawler", info.type());
        assertEquals("爬虫", info.typeLabel());
        assertEquals("curl", info.name());
    }

    @Test
    void classifiesFailedSearchEngineVerificationAsSpoofedSearchEngine() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                Map.of(),
                UserAgentClassifier.BotVerification.failed("Googlebot", "PTR域名不属于官方后缀"));

        assertEquals("spoofed_search_engine", info.type());
        assertEquals("疑似伪装搜索引擎", info.typeLabel());
        assertEquals("疑似伪装 Googlebot", info.name());
        assertEquals("failed", info.botVerifyStatus());
    }

    @Test
    void keepsSearchEnginePureNameAndAggregatesTogether() {
        String googlebotUa = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

        UserAgentClassifier.UaInfo unverified = UserAgentClassifier.classify(googlebotUa);
        UserAgentClassifier.UaInfo verified = UserAgentClassifier.classify(
                googlebotUa,
                Map.of(),
                UserAgentClassifier.BotVerification.verified("Googlebot", "PTR与正向DNS验证通过"));

        assertEquals("search_engine", unverified.type());
        assertEquals("Googlebot", unverified.name());
        assertEquals("unknown", unverified.botVerifyStatus());
        assertEquals("Googlebot", verified.name());
        assertEquals("verified", verified.botVerifyStatus());
    }

    @Test
    void recognizesYahooAndYisouAsSearchEngines() {
        UserAgentClassifier.UaInfo yahoo = UserAgentClassifier.classify(
                "Mozilla/5.0 (compatible; Yahoo! Slurp; http://help.yahoo.com/help/us/ysearch/slurp)");
        UserAgentClassifier.UaInfo yisou = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/69.0.3497.81 YisouSpider/5.0 Safari/537.36");

        assertEquals("search_engine", yahoo.type());
        assertEquals("Yahoo Slurp", yahoo.name());
        assertEquals("search_engine", yisou.type());
        assertEquals("YisouSpider", yisou.name());
    }

    @Test
    void recognizesAdditionalKnownSearchEngineBots() {
        assertEquals("DuckDuckBot", UserAgentClassifier.classify("DuckDuckBot/1.1").name());
        assertEquals("Applebot", UserAgentClassifier.classify("Applebot/0.1").name());
        assertEquals("PetalBot", UserAgentClassifier.classify("PetalBot").name());
        // Sosospider was moved to crawler
        UserAgentClassifier.UaInfo soso = UserAgentClassifier.classify("Sosospider+(+http://help.soso.com/webspider.htm)");
        assertEquals("crawler", soso.type());
        assertEquals("Sosospider", soso.name());
    }

    @Test
    void recognizesAiCrawlersSpecifically() {
        UserAgentClassifier.UaInfo gpt = UserAgentClassifier.classify("Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko); compatible; GPTBot/1.1; +https://openai.com/gptbot");
        assertEquals("ai_crawler", gpt.type());
        assertEquals("AI爬虫", gpt.typeLabel());
        assertEquals("GPTBot", gpt.name());

        UserAgentClassifier.UaInfo claude = UserAgentClassifier.classify("Mozilla/5.0 (compatible; ClaudeBot/1.0; +http://www.anthropic.com/claudebot)");
        assertEquals("ai_crawler", claude.type());
        assertEquals("ClaudeBot", claude.name());

        UserAgentClassifier.UaInfo perplexity = UserAgentClassifier.classify("Mozilla/5.0 (compatible; PerplexityBot/1.0; +http://www.perplexity.ai/bot)");
        assertEquals("ai_crawler", perplexity.type());
        assertEquals("PerplexityBot", perplexity.name());
    }

    @Test
    void promotesVerificationStatusDuringAggregation() {
        List<Map<String, Object>> records = List.of(
                Map.of(
                        "userAgent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                        "botVerifyStatus", "unknown"
                ),
                Map.of(
                        "userAgent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                        "botVerifyStatus", "verified"
                )
        );

        List<Map<String, Object>> result = UserAgentClassifier.aggregateVisitRecords(records);
        assertEquals(1, result.size());
        assertEquals("Googlebot", result.get(0).get("ua_name")); // Since it's verified, no suffix
        assertEquals("verified", result.get(0).get("bot_verify_status"));
    }

    @Test
    void appendsUnverifiedSuffixForUnverifiedSearchEnginesInToRows() {
        List<Map<String, Object>> records = List.of(
                Map.of(
                        "userAgent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                        "botVerifyStatus", "unknown"
                )
        );

        List<Map<String, Object>> result = UserAgentClassifier.aggregateVisitRecords(records);
        assertEquals(1, result.size());
        assertEquals("Googlebot（未验证）", result.get(0).get("ua_name"));
        assertEquals("unknown", result.get(0).get("bot_verify_status"));
    }

    @Test
    void keepsBrowserUaWithHtmlHeadersAsPc() {
        UserAgentClassifier.UaInfo info = UserAgentClassifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                Map.of(
                        "visitSource", "nginx",
                        "accept", "text/html,application/xhtml+xml",
                        "acceptLanguage", "zh-CN,zh;q=0.9"));

        assertEquals("pc", info.type());
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

    @Test
    void aggregatesDifferentDisguisedBrowserUasIntoOneBucket() {
        List<Map<String, Object>> records = List.of(
                Map.of(
                        "userAgent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                        "visitSource", "nginx",
                        "headerSnapshot", "1",
                        "accept", "application/json"),
                Map.of(
                        "userAgent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) "
                                + "Gecko/20100101 Firefox/126.0",
                        "visitSource", "nginx",
                        "headerSnapshot", "1",
                        "accept", "application/json"));

        List<Map<String, Object>> result = UserAgentClassifier.aggregateVisitRecords(records);

        assertEquals(1, result.size());
        assertEquals("crawler", result.get(0).get("ua_type"));
        assertEquals("伪装浏览器请求", result.get(0).get("ua_name"));
        assertEquals(2L, result.get(0).get("num"));
    }

    @Test
    void aggregatesPreclassifiedRowsBeforeRawBrowserFallback() {
        String chromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
        List<Map<String, Object>> rows = List.of(
                Map.of("user_agent", chromeUa, "ua_type", "automation", "ua_name", "Chrome WebDriver", "num", 7),
                Map.of("user_agent", chromeUa, "num", 3));

        List<Map<String, Object>> result = UserAgentClassifier.aggregateRawUserAgentCounts(rows);

        assertEquals("automation", result.get(0).get("ua_type"));
        assertEquals("自动化访问", result.get(0).get("ua_type_label"));
        assertEquals("Chrome WebDriver", result.get(0).get("ua_name"));
        assertEquals(7L, result.get(0).get("num"));
        assertEquals("pc", result.get(1).get("ua_type"));
        assertEquals(3L, result.get(1).get("num"));
    }

    @Test
    void aggregatesPersistedUaInfoWithoutRawUserAgent() {
        List<Map<String, Object>> rows = List.of(
                Map.of(
                        "ua_type", "spoofed_search_engine",
                        "ua_name", "疑似伪装 Googlebot",
                        "bot_verify_status", "failed",
                        "bot_verify_reason", "PTR域名不属于官方后缀",
                        "num", 4));

        List<Map<String, Object>> result = UserAgentClassifier.aggregateRawUserAgentCounts(rows);

        assertEquals(1, result.size());
        assertEquals("spoofed_search_engine", result.get(0).get("ua_type"));
        assertEquals("疑似伪装搜索引擎", result.get(0).get("ua_type_label"));
        assertEquals("疑似伪装 Googlebot", result.get(0).get("ua_name"));
        assertEquals("failed", result.get(0).get("bot_verify_status"));
        assertEquals(4L, result.get(0).get("num"));
    }

    // ==================== evaluateAutomation 打分与拦截判定测试 ====================

    @Test
    void evaluateAutomationReturnsZeroForEmptySignals() {
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(null);

        assertEquals(0, verdict.score());
        assertFalse(verdict.shouldBlock());
        assertTrue(verdict.hitHighConfidence().isEmpty());
    }

    @Test
    void evaluateAutomationReturnsZeroForNormalBrowser() {
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationScore", "0",
                "automationVerdict", "LIKELY_HUMAN",
                "permissionsQueryNative", "1",
                "pluginsItemNative", "1",
                "platform", "Win32",
                "timezone", "Asia/Shanghai",
                "deviceMemory", "8",
                "pluginCount", "5",
                "languageCount", "2"));

        assertEquals(0, verdict.score());
        assertFalse(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationBlocksWebdriverTrue() {
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "webdriver", "true"));

        assertEquals(80, verdict.score());
        assertTrue(verdict.shouldBlock());
        assertTrue(verdict.hitHighConfidence().stream().anyMatch(s -> s.contains("webdriver")));
    }

    @Test
    void evaluateAutomationBlocksHeadlessChrome() {
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "hch"));

        assertEquals(80, verdict.score());
        assertTrue(verdict.shouldBlock());
        assertTrue(verdict.hitHighConfidence().stream().anyMatch(s -> s.contains("HeadlessChrome")));
    }

    @Test
    void evaluateAutomationDoesNotBlockSwiftShaderAlone() {
        // SwiftShader = 50分，单独不达阈值70，不拦截（避免误封关闭硬件加速的真实用户）
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "swg",
                "webglRenderer", "SwiftShader"));

        assertEquals(50, verdict.score());
        assertFalse(verdict.shouldBlock());
        assertTrue(verdict.hitHighConfidence().stream().anyMatch(s -> s.contains("SwiftShader")));
    }

    @Test
    void evaluateAutomationBlocksSwiftShaderWithLowConfidenceSignals() {
        // SwiftShader(50) + wchrome(15) + scr0(15) = 80，超过阈值
        // 真实无头浏览器会同时泄漏多个信号，叠加后触发拦截
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "swg,wchrome,scr0",
                "webglRenderer", "SwiftShader",
                "chromeNative", "0"));

        assertEquals(80, verdict.score());
        assertTrue(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationDoesNotBlockWchromeAlone() {
        // window.chrome缺失 = 15分，低置信度，单独不拦截
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "wchrome",
                "chromeNative", "0"));

        assertEquals(15, verdict.score());
        assertFalse(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationDoesNotBlockNorefAlone() {
        // 直链无referrer = 10分，低置信度，单独不拦截
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "noref"));

        assertEquals(10, verdict.score());
        assertFalse(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationBlocksSwiftShaderWithTimezoneAndNoref() {
        // SwiftShader(50) + wutc(15) + noref(10) = 75，超过阈值
        // 典型无头浏览器：软件渲染 + UTC时区 + 直链抓取
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "swg,wutc,noref",
                "webglRenderer", "SwiftShader"));

        assertEquals(75, verdict.score());
        assertTrue(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationBlocksPermissionsQueryNonNative() {
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "permissionsQueryNative", "0",
                "automationSignals", "pqn"));

        assertEquals(75, verdict.score());
        assertTrue(verdict.shouldBlock());
        assertTrue(verdict.hitHighConfidence().stream().anyMatch(s -> s.contains("permissions.query")));
    }

    @Test
    void evaluateAutomationDoesNotBlockPluginsItemAlone() {
        // plugins.item非native = 60分，单独不足以触发拦截（阈值70）
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "pluginsItemNative", "0",
                "automationSignals", "pin"));

        assertEquals(60, verdict.score());
        assertFalse(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationDoesNotBlockGlobalLeakAlone() {
        // 全局变量泄漏 = 50分，单独不足以触发拦截
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "gleak"));

        assertEquals(50, verdict.score());
        assertFalse(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationBlocksCombinationOfPluginsAndGlobalLeak() {
        // 60 + 50 = 110，超过阈值
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "pluginsItemNative", "0",
                "automationSignals", "pin,gleak"));

        assertEquals(110, verdict.score());
        assertTrue(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationDoesNotBlockLowConfidenceSignals() {
        // 低置信度信号组合：wutc(15) + wdm(15) + wdtype(15) = 45，不达阈值
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "automationSignals", "wutc,wdm,wdtype",
                "platform", "Win32",
                "timezone", "UTC",
                "deviceMemory", "null",
                "webdriverType", "string"));

        assertEquals(45, verdict.score());
        assertFalse(verdict.shouldBlock());
        assertTrue(verdict.hitHighConfidence().isEmpty());
    }

    @Test
    void evaluateAutomationDoesNotBlockRssReaderWithoutRuntimeSignals() {
        // RSS阅读器只有传输层信号，无运行时信号 -> 不会被拦截
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "visitSource", "nginx",
                "headerSnapshot", "1",
                "accept", "application/json"));

        assertEquals(0, verdict.score());
        assertFalse(verdict.shouldBlock());
    }

    @Test
    void evaluateAutomationReasonContainsScoreAndHitSignals() {
        UserAgentClassifier.AutomationVerdict verdict = UserAgentClassifier.evaluateAutomation(Map.of(
                "webdriver", "true",
                "automationSignals", "hch"));

        assertTrue(verdict.reason().contains("160"));
        assertTrue(verdict.reason().contains("webdriver"));
        assertTrue(verdict.reason().contains("HeadlessChrome"));
    }
}
