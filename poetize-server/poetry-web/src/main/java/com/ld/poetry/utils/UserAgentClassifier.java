package com.ld.poetry.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User-Agent 分类与聚合工具。
 */
public final class UserAgentClassifier {

    private static final int DEFAULT_LIMIT = 10;

    private UserAgentClassifier() {
    }

    public static UaInfo classify(String userAgent) {
        return classify(userAgent, null);
    }

    public static UaInfo classify(String userAgent, Map<String, Object> signals) {
        return classify(userAgent, signals, null);
    }

    public static UaInfo classify(String userAgent, Map<String, Object> signals, BotVerification botVerification) {
        if (userAgent == null || userAgent.isBlank() || "-".equals(userAgent.trim())) {
            return new UaInfo("unknown", "未知", "未知客户端");
        }

        String ua = userAgent.trim();
        String lower = ua.toLowerCase(Locale.ROOT);

        String searchEngine = searchEngineName(lower);
        if (searchEngine != null) {
            return searchEngineInfo(searchEngine, botVerification);
        }

        String securityScanner = securityScannerName(lower);
        if (securityScanner != null) {
            return new UaInfo("scanner", "扫描器", securityScanner);
        }

        String automation = automationName(lower, signals);
        if (automation != null) {
            return new UaInfo("automation", "自动化访问", automation);
        }

        String crawler = crawlerName(lower);
        if (crawler != null) {
            return new UaInfo("crawler", "爬虫", crawler);
        }

        String disguisedClient = disguisedBrowserClientName(lower, signals);
        if (disguisedClient != null) {
            return new UaInfo("crawler", "爬虫", disguisedClient);
        }

        boolean mobile = isMobile(lower);
        return new UaInfo(mobile ? "mobile" : "pc", mobile ? "移动端" : "PC端", browserName(lower, mobile));
    }

    public static String detectSearchEngineName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return searchEngineName(userAgent.toLowerCase(Locale.ROOT));
    }

    public static List<Map<String, Object>> aggregateRawUserAgentCounts(List<Map<String, Object>> rows) {
        return aggregateRawUserAgentCounts(rows, DEFAULT_LIMIT);
    }

    public static List<Map<String, Object>> aggregateRawUserAgentCounts(List<Map<String, Object>> rows, int limit) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, UaBucket> buckets = new HashMap<>();
        addRawUserAgentCounts(buckets, rows);

        return toRows(buckets, limit);
    }

    public static List<Map<String, Object>> aggregateRawAndVisitRecords(List<Map<String, Object>> rows,
                                                                        List<Map<String, Object>> records) {
        return aggregateRawAndVisitRecords(rows, records, DEFAULT_LIMIT);
    }

    public static List<Map<String, Object>> aggregateRawAndVisitRecords(List<Map<String, Object>> rows,
                                                                        List<Map<String, Object>> records,
                                                                        int limit) {
        Map<String, UaBucket> buckets = new HashMap<>();
        addRawUserAgentCounts(buckets, rows);
        addVisitRecords(buckets, records);
        return toRows(buckets, limit);
    }

    private static void addRawUserAgentCounts(Map<String, UaBucket> buckets, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        for (Map<String, Object> row : rows) {
            if (row == null) continue;
            Object uaObj = firstNonNull(row.get("user_agent"), row.get("userAgent"));
            String ua = uaObj == null ? null : uaObj.toString();
            boolean hasUa = ua != null && !ua.isBlank() && !"-".equals(ua.trim());
            boolean hasClassifiedInfo = hasText(firstText(row, "uaType", "ua_type"))
                    && hasText(firstText(row, "uaName", "ua_name"));
            if (!hasUa && !hasClassifiedInfo) continue;

            long count = asLong(row.get("num"));
            if (count <= 0) continue;

            mergeBucket(buckets, hasUa ? ua : firstText(row, "uaName", "ua_name"), count, resolveInfo(ua, row));
        }
    }

    public static List<Map<String, Object>> aggregateVisitRecords(List<Map<String, Object>> records) {
        return aggregateVisitRecords(records, DEFAULT_LIMIT);
    }

    public static List<Map<String, Object>> aggregateVisitRecords(List<Map<String, Object>> records, int limit) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, UaBucket> buckets = new HashMap<>();
        addVisitRecords(buckets, records);

        return toRows(buckets, limit);
    }

    private static void addVisitRecords(Map<String, UaBucket> buckets, List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        for (Map<String, Object> record : records) {
            if (record == null) continue;
            Object uaObj = firstNonNull(record.get("userAgent"), record.get("user_agent"));
            String ua = uaObj == null ? null : uaObj.toString();
            boolean hasUa = ua != null && !ua.isBlank() && !"-".equals(ua.trim());
            boolean hasClassifiedInfo = hasText(firstText(record, "uaType", "ua_type"))
                    && hasText(firstText(record, "uaName", "ua_name"));
            if (!hasUa && !hasClassifiedInfo) continue;
            mergeBucket(buckets, hasUa ? ua : firstText(record, "uaName", "ua_name"), 1L, resolveInfo(ua, record));
        }
    }

    private static void mergeBucket(Map<String, UaBucket> buckets, String userAgent, long count, UaInfo resolvedInfo) {
        UaInfo info = resolvedInfo != null ? resolvedInfo : classify(userAgent);
        String key = info.type() + "|" + info.name();
        UaBucket bucket = buckets.computeIfAbsent(key, ignored -> new UaBucket(info, userAgent));
        bucket.num += count;
    }

    private static UaInfo resolveInfo(String userAgent, Map<String, Object> row) {
        String type = firstText(row, "uaType", "ua_type");
        String name = firstText(row, "uaName", "ua_name");
        if (hasText(type) && hasText(name)) {
            return new UaInfo(
                    type.trim(),
                    typeLabel(type),
                    name.trim(),
                    firstText(row, "botVerifyStatus", "bot_verify_status"),
                    firstText(row, "botVerifyReason", "bot_verify_reason")
            );
        }
        return classify(userAgent, row);
    }

    private static List<Map<String, Object>> toRows(Map<String, UaBucket> buckets, int limit) {
        int maxRows = limit > 0 ? limit : DEFAULT_LIMIT;
        return buckets.values().stream()
                .sorted(Comparator.comparingLong((UaBucket bucket) -> bucket.num).reversed()
                        .thenComparing(bucket -> bucket.info.typeLabel())
                        .thenComparing(bucket -> bucket.info.name()))
                .limit(maxRows)
                .map(bucket -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ua_type", bucket.info.type());
                    row.put("ua_type_label", bucket.info.typeLabel());
                    row.put("ua_name", bucket.info.name());
                    row.put("bot_verify_status", bucket.info.botVerifyStatus());
                    if (hasText(bucket.info.botVerifyReason())) {
                        row.put("bot_verify_reason", bucket.info.botVerifyReason());
                    }
                    row.put("num", bucket.num);
                    row.put("sample_ua", bucket.sampleUa);
                    return row;
                })
                .toList();
    }

    private static UaInfo searchEngineInfo(String searchEngine, BotVerification botVerification) {
        String status = botVerification != null && hasText(botVerification.status())
                ? botVerification.status().trim().toLowerCase(Locale.ROOT)
                : "unknown";
        String reason = botVerification != null && hasText(botVerification.reason())
                ? botVerification.reason().trim()
                : "未执行搜索引擎IP验证";
        if ("not_applicable".equals(status)) {
            status = "unknown";
        }
        if ("failed".equals(status)) {
            return new UaInfo(
                    "spoofed_search_engine",
                    "疑似伪装搜索引擎",
                    "疑似伪装 " + searchEngine,
                    status,
                    reason
            );
        }
        String name = "verified".equals(status) ? searchEngine : searchEngine + "（未验证）";
        return new UaInfo("search_engine", "搜索引擎", name, status, reason);
    }

    private static String searchEngineName(String lower) {
        if (lower.contains("googlebot")) return "Googlebot";
        if (lower.contains("baiduspider")) return "Baiduspider";
        if (lower.contains("bingbot")) return "Bingbot";
        if (lower.contains("sogou")) return "Sogou Spider";
        if (lower.contains("360spider") || lower.contains("haosouspider")) return "360 Spider";
        if (lower.contains("yandexbot")) return "YandexBot";
        if (lower.contains("duckduckbot")) return "DuckDuckBot";
        if (lower.contains("bytespider")) return "Bytespider";
        if (lower.contains("petalbot")) return "PetalBot";
        if (lower.contains("applebot")) return "Applebot";
        if (lower.contains("semrushbot")) return "SemrushBot";
        if (lower.contains("ahrefsbot")) return "AhrefsBot";
        return null;
    }

    private static String securityScannerName(String lower) {
        if (lower.contains("l9scan") || lower.contains("leakix")) return "LeakIX";
        if (lower.contains("censysinspect") || lower.contains("censys")) return "Censys";
        if (lower.contains("shodan")) return "Shodan";
        if (lower.contains("internetdb")) return "InternetDB";
        if (lower.contains("binaryedge")) return "BinaryEdge";
        if (lower.contains("onyphe")) return "ONYPHE";
        if (lower.contains("shadowserver")) return "Shadowserver";
        if (lower.contains("zgrab")) return "ZGrab";
        if (lower.contains("zmap")) return "ZMap";
        if (lower.contains("masscan")) return "Masscan";
        if (lower.contains("nuclei")) return "Nuclei";
        if (lower.contains("nikto")) return "Nikto";
        if (lower.contains("sqlmap")) return "sqlmap";
        if (lower.contains("nmap")) return "Nmap";
        if (lower.contains("gobuster")) return "Gobuster";
        if (lower.contains("dirbuster")) return "DirBuster";
        if (lower.contains("feroxbuster")) return "Feroxbuster";
        if (lower.contains("wpscan")) return "WPScan";
        if (lower.contains("openvas")) return "OpenVAS";
        if (lower.contains("nessus")) return "Nessus";
        if (lower.contains("acunetix")) return "Acunetix";
        if (lower.contains("burpsuite")) return "Burp Suite";
        if (lower.contains("appscan")) return "AppScan";
        if (lower.contains("netsparker")) return "Netsparker";
        if (lower.contains("whatweb")) return "WhatWeb";
        if (lower.contains(" jaeles") || lower.startsWith("jaeles")) return "Jaeles";
        if (lower.contains("ffuf")) return "ffuf";
        if (lower.contains("dirb/") || lower.equals("dirb")) return "DIRB";
        if (lower.contains("httpx")) return "ProjectDiscovery httpx";
        return null;
    }

    private static String crawlerName(String lower) {
        if (lower.contains("headlesschrome")) return "Headless Chrome";
        if (lower.contains("playwright")) return "Playwright";
        if (lower.contains("puppeteer")) return "Puppeteer";
        if (lower.contains("selenium") || lower.contains("webdriver")) return "Selenium/WebDriver";
        if (lower.contains("phantomjs")) return "PhantomJS";
        if (lower.contains("requests")) return "Python requests";
        if (lower.contains("okhttp")) return "OkHttp";
        if (lower.contains("aiohttp")) return "aiohttp";
        if (lower.contains("http.rb")) return "http.rb";
        if (lower.contains("libwww-perl")) return "libwww-perl";
        if (lower.contains("node-fetch")) return "node-fetch";
        if (lower.contains("axios")) return "Axios";
        if (lower.contains("curl")) return "curl";
        if (lower.contains("wget")) return "wget";
        if (lower.contains("python")) return "Python";
        if (lower.contains("scrapy")) return "Scrapy";
        if (lower.contains("go-http")) return "Go HTTP Client";
        if (lower.contains("httpclient")) return "HTTP Client";
        if (lower.contains("java")) return "Java HTTP Client";
        if (lower.contains("facebookexternalhit")) return "Facebook Crawler";
        if (lower.contains("twitterbot")) return "TwitterBot";
        if (lower.contains("bot")) return "Bot";
        if (lower.contains("spider")) return "Spider";
        if (lower.contains("crawler")) return "Crawler";
        if (lower.contains("slurp")) return "Slurp";
        return null;
    }

    private static String automationName(String lower, Map<String, Object> signals) {
        if (lower.contains("headlesschrome")) return "Headless Chrome";
        if (lower.contains("playwright")) return "Playwright";
        if (lower.contains("puppeteer")) return "Puppeteer";
        if (lower.contains("selenium") || lower.contains("webdriver")) return "Selenium/WebDriver";
        if (lower.contains("phantomjs")) return "PhantomJS";

        if (isTruthy(firstText(signals, "webdriver", "wd"))) {
            return browserName(lower, isMobile(lower)) + " WebDriver";
        }

        String scoredAutomation = scoredAutomationName(signals);
        if (scoredAutomation != null) {
            return scoredAutomation;
        }

        if (!hasRuntimeSignals(signals) || !looksLikeBrowserUa(lower)) {
            return null;
        }

        Integer pluginCount = asInteger(firstText(signals, "pluginCount", "plugins", "pl"));
        Integer languageCount = asInteger(firstText(signals, "languageCount", "languages", "lg"));
        boolean noPlugins = pluginCount != null && pluginCount == 0 && !isMobile(lower);
        boolean noLanguages = languageCount != null && languageCount == 0;
        if (noPlugins && noLanguages) {
            return browserName(lower, isMobile(lower)) + " 自动化环境";
        }

        return null;
    }

    private static String scoredAutomationName(Map<String, Object> signals) {
        if (signals == null || signals.isEmpty()) {
            return null;
        }

        int score = automationScore(signals);
        String verdict = firstText(signals, "automationVerdict", "av");
        boolean likelyBot = score >= 70 || contains(verdict, "bot");
        boolean suspicious = likelyBot || score >= 25 || "suspicious".equalsIgnoreCase(verdict);

        if (!suspicious) {
            return null;
        }

        if (isFalseFlag(signals, "permissionsQueryNative", "pqn")
                || isFalseFlag(signals, "pluginsItemNative", "pin")
                || hasSignalCode(signals, "pqn")
                || hasSignalCode(signals, "pin")) {
            return likelyBot ? "自动化浏览器（JS原生性异常）" : "疑似自动化浏览器（JS原生性异常）";
        }
        if (contains(firstText(signals, "webglRenderer", "glr"), "swiftshader") || hasSignalCode(signals, "swg")) {
            return likelyBot ? "自动化浏览器（SwiftShader）" : "疑似自动化浏览器（SwiftShader）";
        }
        if (hasSignalCode(signals, "gleak")) {
            return likelyBot ? "自动化浏览器（全局变量泄漏）" : "疑似自动化浏览器（全局变量泄漏）";
        }
        if ("value".equalsIgnoreCase(firstText(signals, "webdriverDescriptor", "wdd"))
                || hasSignalCode(signals, "wdprop")) {
            return likelyBot ? "自动化浏览器（webdriver属性异常）" : "疑似自动化浏览器（webdriver属性异常）";
        }
        return likelyBot ? "自动化浏览器" : "疑似自动化浏览器";
    }

    private static int automationScore(Map<String, Object> signals) {
        Integer reportedScore = asInteger(firstText(signals, "automationScore", "as"));
        if (reportedScore != null) {
            return Math.max(0, reportedScore);
        }

        int score = 0;
        if (isTruthy(firstText(signals, "webdriver", "wd")) || hasSignalCode(signals, "wd")) score += 80;
        if (hasSignalCode(signals, "hch")) score += 80;
        if (isFalseFlag(signals, "permissionsQueryNative", "pqn") || hasSignalCode(signals, "pqn")) score += 75;
        if (isFalseFlag(signals, "pluginsItemNative", "pin") || hasSignalCode(signals, "pin")) score += 60;
        if ("value".equalsIgnoreCase(firstText(signals, "webdriverDescriptor", "wdd"))
                || hasSignalCode(signals, "wdprop")) {
            score += 60;
        }
        if (contains(firstText(signals, "webglRenderer", "glr"), "swiftshader") || hasSignalCode(signals, "swg")) {
            score += 70;
        }
        if (hasSignalCode(signals, "gleak")) score += 50;
        if (hasSignalCode(signals, "wutc")) score += 15;
        if (hasSignalCode(signals, "wdm")) score += 15;
        if (hasSignalCode(signals, "wdtype")) score += 15;
        return score;
    }

    private static String disguisedBrowserClientName(String lower, Map<String, Object> signals) {
        if (!looksLikeBrowserUa(lower) || !hasTransportSignals(signals)) {
            return null;
        }

        boolean hasAcceptLanguage = hasText(firstText(signals, "acceptLanguage", "accept_language", "lang"));
        boolean acceptsHtml = contains(firstText(signals, "accept"), "text/html");
        boolean hasFetchMetadata = hasAnyText(signals,
                "secFetchSite", "sec_fetch_site",
                "secFetchMode", "sec_fetch_mode",
                "secFetchDest", "sec_fetch_dest",
                "secFetchUser", "sec_fetch_user",
                "secChUa", "sec_ch_ua",
                "secChUaPlatform", "sec_ch_ua_platform",
                "upgradeInsecureRequests", "upgrade_insecure_requests");

        if (!hasRuntimeSignals(signals) && !hasAcceptLanguage && !acceptsHtml && !hasFetchMetadata) {
            return "伪装浏览器请求";
        }

        return null;
    }

    private static boolean isMobile(String lower) {
        return lower.contains("mobile")
                || lower.contains("android")
                || lower.contains("iphone")
                || lower.contains("ipad")
                || lower.contains("ipod")
                || lower.contains("harmonyos")
                || lower.contains("windows phone");
    }

    private static String browserName(String lower, boolean mobile) {
        if (lower.contains("edg/") || lower.contains("edgios") || lower.contains("edga")) return "Edge";
        if (lower.contains("micromessenger")) return "微信内置浏览器";
        if (lower.contains("qqbrowser")) return "QQ浏览器";
        if (lower.contains("firefox/") || lower.contains("fxios")) return "Firefox";
        if (lower.contains("opr/") || lower.contains("opera")) return "Opera";
        if (lower.contains("chrome/") || lower.contains("crios")) return mobile ? "Chrome Mobile" : "Chrome";
        if (lower.contains("safari/")) return mobile ? "Mobile Safari" : "Safari";
        if (lower.contains("msie") || lower.contains("trident/")) return "Internet Explorer";
        return mobile ? "移动端浏览器" : "桌面浏览器";
    }

    private static boolean looksLikeBrowserUa(String lower) {
        return lower.contains("mozilla/")
                && (lower.contains("chrome/")
                || lower.contains("safari/")
                || lower.contains("firefox/")
                || lower.contains("edg/")
                || lower.contains("opr/")
                || lower.contains("trident/")
                || lower.contains("msie"));
    }

    private static boolean hasRuntimeSignals(Map<String, Object> signals) {
        return hasAnyText(signals,
                "webdriver", "wd",
                "pluginCount", "plugins", "pl",
                "languageCount", "languages", "lg",
                "hardwareConcurrency", "hc",
                "maxTouchPoints", "tp",
                "platform", "pf",
                "deviceMemory", "dm",
                "timezone", "tz",
                "screenWidth", "sw",
                "screenHeight", "sh",
                "colorDepth", "cd",
                "automationScore", "as",
                "automationVerdict", "av",
                "automationSignals", "af",
                "permissionsQueryNative", "pqn",
                "pluginsItemNative", "pin",
                "webdriverDescriptor", "wdd",
                "webglVendor", "glv",
                "webglRenderer", "glr");
    }

    private static boolean hasTransportSignals(Map<String, Object> signals) {
        return hasAnyText(signals,
                "headerSnapshot",
                "accept",
                "acceptLanguage", "accept_language", "lang",
                "secFetchSite", "sec_fetch_site",
                "secFetchMode", "sec_fetch_mode",
                "secFetchDest", "sec_fetch_dest",
                "secFetchUser", "sec_fetch_user",
                "secChUa", "sec_ch_ua",
                "secChUaPlatform", "sec_ch_ua_platform",
                "upgradeInsecureRequests", "upgrade_insecure_requests");
    }

    private static boolean hasAnyText(Map<String, Object> signals, String... keys) {
        return hasText(firstText(signals, keys));
    }

    private static String firstText(Map<String, Object> signals, String... keys) {
        if (signals == null || signals.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = signals.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text) && !"-".equals(text)) {
                return text;
            }
        }
        return null;
    }

    private static String typeLabel(String type) {
        if (type == null) {
            return "未知";
        }
        return switch (type.trim()) {
            case "search_engine" -> "搜索引擎";
            case "spoofed_search_engine" -> "疑似伪装搜索引擎";
            case "scanner" -> "扫描器";
            case "crawler" -> "爬虫";
            case "http_client" -> "HTTP客户端";
            case "automation" -> "自动化访问";
            case "mobile" -> "移动端";
            case "pc" -> "PC端";
            default -> "未知";
        };
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean contains(String value, String expected) {
        return value != null && expected != null && value.toLowerCase(Locale.ROOT).contains(expected);
    }

    private static boolean isFalseFlag(Map<String, Object> signals, String... keys) {
        String value = firstText(signals, keys);
        return "0".equals(value) || "false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value);
    }

    private static boolean hasSignalCode(Map<String, Object> signals, String code) {
        String value = firstText(signals, "automationSignals", "af");
        if (!hasText(value) || !hasText(code)) {
            return false;
        }
        for (String part : value.split("[,;|\\s]+")) {
            if (code.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private static Integer asInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public record UaInfo(String type, String typeLabel, String name,
                         String botVerifyStatus, String botVerifyReason) {
        public UaInfo(String type, String typeLabel, String name) {
            this(type, typeLabel, name, "not_applicable", null);
        }

        public UaInfo {
            botVerifyStatus = hasText(botVerifyStatus) ? botVerifyStatus : "not_applicable";
            botVerifyReason = hasText(botVerifyReason) ? botVerifyReason : null;
        }
    }

    public record BotVerification(String claimedName, String status, String reason) {
        public BotVerification {
            claimedName = hasText(claimedName) ? claimedName.trim() : null;
            status = hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "unknown";
            reason = hasText(reason) ? reason.trim() : null;
        }

        public static BotVerification notApplicable() {
            return new BotVerification(null, "not_applicable", null);
        }

        public static BotVerification verified(String claimedName, String reason) {
            return new BotVerification(claimedName, "verified", reason);
        }

        public static BotVerification failed(String claimedName, String reason) {
            return new BotVerification(claimedName, "failed", reason);
        }

        public static BotVerification unknown(String claimedName, String reason) {
            return new BotVerification(claimedName, "unknown", reason);
        }

        public static BotVerification error(String claimedName, String reason) {
            return new BotVerification(claimedName, "error", reason);
        }

        public static BotVerification timeout(String claimedName, String reason) {
            return new BotVerification(claimedName, "timeout", reason);
        }
    }

    private static final class UaBucket {
        private final UaInfo info;
        private final String sampleUa;
        private long num;

        private UaBucket(UaInfo info, String sampleUa) {
            this.info = info;
            this.sampleUa = sampleUa;
        }
    }
}
