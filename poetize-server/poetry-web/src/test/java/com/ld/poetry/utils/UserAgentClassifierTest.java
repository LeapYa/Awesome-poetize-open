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
    void keepsUnverifiedSearchEngineSeparateFromVerifiedSearchEngine() {
        String googlebotUa = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

        UserAgentClassifier.UaInfo unverified = UserAgentClassifier.classify(googlebotUa);
        UserAgentClassifier.UaInfo verified = UserAgentClassifier.classify(
                googlebotUa,
                Map.of(),
                UserAgentClassifier.BotVerification.verified("Googlebot", "PTR与正向DNS验证通过"));

        assertEquals("search_engine", unverified.type());
        assertEquals("Googlebot（未验证）", unverified.name());
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
        assertEquals("Yahoo Slurp（未验证）", yahoo.name());
        assertEquals("search_engine", yisou.type());
        assertEquals("YisouSpider（未验证）", yisou.name());
    }

    @Test
    void recognizesAdditionalKnownSearchEngineBots() {
        assertEquals("DuckDuckBot（未验证）", UserAgentClassifier.classify("DuckDuckBot/1.1").name());
        assertEquals("Applebot（未验证）", UserAgentClassifier.classify("Applebot/0.1").name());
        assertEquals("PetalBot（未验证）", UserAgentClassifier.classify("PetalBot").name());
        assertEquals("Sosospider（未验证）", UserAgentClassifier.classify("Sosospider+(+http://help.soso.com/webspider.htm)").name());
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
}
