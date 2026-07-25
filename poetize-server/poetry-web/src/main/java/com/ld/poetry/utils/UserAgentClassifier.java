package com.ld.poetry.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String aiCrawler = aiCrawlerName(lower);
        if (aiCrawler != null) {
            return new UaInfo("ai_crawler", "AI爬虫", aiCrawler);
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

        String genericBot = genericBotName(ua, lower);
        if (genericBot != null) {
            return new UaInfo("crawler", "爬虫", genericBot);
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
        if ("search_engine".equals(info.type())) {
            bucket.promoteBotVerification(info);
        }
    }

    private static UaInfo resolveInfo(String userAgent, Map<String, Object> row) {
        String type = firstText(row, "uaType", "ua_type");
        String name = firstText(row, "uaName", "ua_name");
        String status = firstText(row, "botVerifyStatus", "bot_verify_status");
        String reason = firstText(row, "botVerifyReason", "bot_verify_reason");
        BotVerification botVerification = null;
        if (hasText(status)) {
            botVerification = new BotVerification(null, status, reason);
        }
        if (hasText(type) && hasText(name)) {
            String cleanName = name.trim();
            if ("search_engine".equals(type.trim()) && cleanName.endsWith("（未验证）")) {
                cleanName = cleanName.substring(0, cleanName.length() - "（未验证）".length()).trim();
            }
            return new UaInfo(
                    type.trim(),
                    typeLabel(type),
                    cleanName,
                    status,
                    reason
            );
        }
        return classify(userAgent, row, botVerification);
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
                    String displayName = bucket.info.name();
                    if ("search_engine".equals(bucket.info.type()) && !"verified".equals(bucket.info.botVerifyStatus())) {
                        displayName = displayName + "（未验证）";
                    }
                    row.put("ua_name", displayName);
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
        return new UaInfo("search_engine", "搜索引擎", searchEngine, status, reason);
    }

    private static String searchEngineName(String lower) {
        if (lower.contains("googlebot")) return "Googlebot";
        if (lower.contains("baiduspider")) return "Baiduspider";
        if (lower.contains("bingbot")) return "Bingbot";
        if (lower.contains("yahoo! slurp") || lower.contains("yahoo slurp") || lower.contains("slurp")) {
            return "Yahoo Slurp";
        }
        if (lower.contains("sogou")) return "Sogou Spider";
        if (lower.contains("360spider") || lower.contains("haosouspider")) return "360 Spider";
        if (lower.contains("yandexbot")) return "YandexBot";
        if (lower.contains("duckduckbot")) return "DuckDuckBot";
        if (lower.contains("bytespider")) return "Bytespider";
        if (lower.contains("yisouspider")) return "YisouSpider";
        if (lower.contains("petalbot")) return "PetalBot";
        if (lower.contains("applebot")) return "Applebot";
        return null;
    }

    private static String securityScannerName(String lower) {
        if (lower.contains("palo alto networks") || lower.contains("expanse")) return "Palo Alto Networks Scanner";
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

    private static String aiCrawlerName(String lower) {
        if (lower.contains("gptbot")) return "GPTBot";
        if (lower.contains("oai-searchbot")) return "OAI-SearchBot";
        if (lower.contains("chatgpt-user")) return "ChatGPT-User";
        if (lower.contains("claudebot")) return "ClaudeBot";
        if (lower.contains("claude-user")) return "Claude-User";
        if (lower.contains("claude-searchbot")) return "Claude-SearchBot";
        if (lower.contains("claude-web")) return "Claude-Web";
        if (lower.contains("anthropic-ai")) return "anthropic-ai";
        if (lower.contains("perplexitybot")) return "PerplexityBot";
        if (lower.contains("perplexity-user")) return "Perplexity-User";
        if (lower.contains("google-extended")) return "Google-Extended";
        if (lower.contains("cohere-ai")) return "Cohere AI";
        if (lower.contains("meta-externalagent")) return "Meta AI";
        if (lower.contains("amazonbot")) return "Amazonbot";
        if (lower.contains("ccbot")) return "CCBot";
        if (lower.contains("diffbot")) return "Diffbot";
        if (lower.contains("bytedance-friendlyspider")) return "Bytedance AI";
        if (lower.contains("iaskspider")) return "iAsk Spider";
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
        if (lower.contains("semrushbot")) return "SemrushBot";
        if (lower.contains("ahrefsbot")) return "AhrefsBot";
        if (lower.contains("seranking")) return "SERankingBot";
        if (lower.contains("sosospider")) return "Sosospider";
        if (lower.contains("bot")) return "Bot";
        if (lower.contains("spider")) return "Spider";
        if (lower.contains("crawler")) return "Crawler";
        if (lower.contains("slurp")) return "Slurp";
        return null;
    }

    /** 自报身份式爬虫 UA 的名称提取：compatible; 后的产品标识（如 CMS-Checker/1.0） */
    private static final Pattern GENERIC_BOT_NAME_PATTERN =
            Pattern.compile("compatible;\\s*([A-Za-z0-9_.\\-]{2,60})(?:[/ ]|;|\\))", Pattern.CASE_INSENSITIVE);

    /**
     * 识别自报身份式爬虫 UA，形如 "Mozilla/5.0 (compatible; CMS-Checker/1.0; +https://example.com)"。
     * <p>判定依据：
     * <ul>
     * <li>UA 中含 "+http" 自报主页链接 —— 这是 bot 自报家门的事实标准，真实浏览器从不携带；</li>
     * <li>或 UA 含 "compatible;" 但不带任何浏览器引擎标识（chrome/safari/firefox 等）。</li>
     * </ul>
     * 旧 IE（MSIE/Trident）和 KHTML 系浏览器也使用 compatible 标记，需要排除。
     * 必须在 crawlerName 之后调用，作为兜底识别，避免此类 UA 落入桌面浏览器分类。
     */
    private static String genericBotName(String ua, String lower) {
        // 空指针保护
        if (ua == null || lower == null) {
            return null;
        }
        if (lower.contains("msie") || lower.contains("trident/") || lower.contains("khtml")) {
            return null;
        }
        boolean selfIdentifyingUrl = lower.contains("+http");
        boolean compatibleToken = lower.contains("compatible;") || lower.contains("compatible ;");
        if (!selfIdentifyingUrl && !compatibleToken) {
            return null;
        }
        // 仅有 compatible 标记时，若 UA 同时带浏览器引擎标识则不作爬虫处理，避免误伤真实浏览器
        if (!selfIdentifyingUrl && looksLikeBrowserUa(lower)) {
            return null;
        }
        Matcher matcher = GENERIC_BOT_NAME_PATTERN.matcher(ua);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return selfIdentifyingUrl ? "自报身份爬虫" : null;
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
        for (AutomationSignal sig : AUTOMATION_SIGNALS) {
            if (isSignalHit(signals, sig.code())) {
                score += sig.weight();
            }
        }
        return score;
    }

    /** 自动化浏览器拦截阈值：分数达到此值触发 403 */
    public static final int AUTOMATION_BLOCK_THRESHOLD = 70;

    /**
     * 自动化信号定义：信号码 + 权重 + 是否高置信度。
     * automationScore 和 evaluateAutomation 共享此列表，避免权重/条件不一致。
     */
    private record AutomationSignal(
            String code,           // 信号码（如 "wd"、"hch"）
            int weight,           // 权重
            boolean highConfidence, // 是否高置信度信号
            String label          // 日志标签（如 "webdriver=true(80)"）
    ) {
    }

    /** 所有自动化信号定义（高置信度 + 低置信度），权重和命中条件单一来源 */
    private static final List<AutomationSignal> AUTOMATION_SIGNALS = List.of(
            new AutomationSignal("wd", 80, true, "webdriver=true(80)"),
            new AutomationSignal("hch", 80, true, "HeadlessChrome(80)"),
            new AutomationSignal("pqn", 75, true, "permissions.query非native(75)"),
            new AutomationSignal("swg", 50, true, "SwiftShader(50)"),
            new AutomationSignal("pin", 60, true, "plugins.item非native(60)"),
            new AutomationSignal("wdprop", 60, true, "webdriver属性异常(60)"),
            new AutomationSignal("gleak", 50, true, "全局变量泄漏(50)"),
            new AutomationSignal("wutc", 15, false, "时区UTC(15)"),
            new AutomationSignal("tznull", 15, false, "时区为空(15)"),
            new AutomationSignal("wdm", 15, false, "设备内存为null(15)"),
            new AutomationSignal("wdtype", 15, false, "设备类型矛盾(15)"),
            new AutomationSignal("wchrome", 15, false, "window.chrome缺失(15)"),
            new AutomationSignal("scr0", 15, false, "屏幕尺寸异常(15)"),
            new AutomationSignal("noref", 10, false, "直链无referrer(10)")
    );

    /**
     * 判断指定信号是否命中。
     * 每个信号的命中条件与原来 automationScore 中的一致：
     * - wd：isTruthy(wd/webdriver) 或信号码 wd
     * - hch：信号码 hch（HeadlessChrome UA 特征）
     * - pqn：permissionsQueryNative 标志为 false 或信号码 pqn
     * - swg：webglRenderer 含 swiftshader 或信号码 swg
     * - pin：pluginsItemNative 标志为 false 或信号码 pin
     * - wdprop：webdriverDescriptor==value 或信号码 wdprop
     * - gleak：信号码 gleak
     * - wchrome：chromeNative 标志为 false 或信号码 wchrome
     * - wutc/tznull/wdm/wdtype/scr0/noref：仅信号码
     */
    private static boolean isSignalHit(Map<String, Object> signals, String code) {
        switch (code) {
            case "wd":
                return isTruthy(firstText(signals, "webdriver", "wd")) || hasSignalCode(signals, "wd");
            case "hch":
                return hasSignalCode(signals, "hch");
            case "pqn":
                return isFalseFlag(signals, "permissionsQueryNative", "pqn") || hasSignalCode(signals, "pqn");
            case "swg":
                return contains(firstText(signals, "webglRenderer", "glr"), "swiftshader")
                        || hasSignalCode(signals, "swg");
            case "pin":
                return isFalseFlag(signals, "pluginsItemNative", "pin") || hasSignalCode(signals, "pin");
            case "wdprop":
                return "value".equalsIgnoreCase(firstText(signals, "webdriverDescriptor", "wdd"))
                        || hasSignalCode(signals, "wdprop");
            case "wchrome":
                return isFalseFlag(signals, "chromeNative", "wchrome") || hasSignalCode(signals, "wchrome");
            default:
                return hasSignalCode(signals, code);
        }
    }

    /**
     * 评估自动化浏览器信号，返回结构化判定结果。
     * <p>
     * 遍历 {@link #AUTOMATION_SIGNALS} 列表打分，同时收集命中的高置信度信号，
     * 供拦截层（SecurityFilter）和日志使用。低置信度信号（wutc/wdm/wdtype）
     * 不触发拦截，仅影响分数供统计参考。
     *
     * @param signals 前端探针上报的运行时信号（可为 null）
     * @return 判定结果，永不返回 null
     */
    public static AutomationVerdict evaluateAutomation(Map<String, Object> signals) {
        if (signals == null || signals.isEmpty()) {
            return new AutomationVerdict(0, false, List.of(), "无运行时信号");
        }

        int score = automationScore(signals);
        List<String> hitHigh = new ArrayList<>();

        for (AutomationSignal sig : AUTOMATION_SIGNALS) {
            if (sig.highConfidence() && isSignalHit(signals, sig.code())) {
                hitHigh.add(sig.label());
            }
        }

        boolean shouldBlock = score >= AUTOMATION_BLOCK_THRESHOLD;
        String reason = hitHigh.isEmpty()
                ? "分数=" + score + "（未命中高置信度信号）"
                : "分数=" + score + " 命中: " + String.join(", ", hitHigh);

        return new AutomationVerdict(score, shouldBlock, List.copyOf(hitHigh), reason);
    }

    /**
     * 自动化浏览器判定结果。
     *
     * @param score              总分（高置信度50-80，低置信度10-15）
     * @param shouldBlock        是否应拦截（score &gt;= {@link #AUTOMATION_BLOCK_THRESHOLD}）
     * @param hitHighConfidence  命中的高置信度信号列表（含分值，用于日志）
     * @param reason             人类可读的判定理由
     */
    public record AutomationVerdict(
            int score,
            boolean shouldBlock,
            List<String> hitHighConfidence,
            String reason
    ) {
        public AutomationVerdict {
            hitHighConfidence = hitHighConfidence == null ? List.of() : List.copyOf(hitHighConfidence);
            reason = hasText(reason) ? reason : "";
        }
    }

    private static String disguisedBrowserClientName(String lower, Map<String, Object> signals) {
        if (!looksLikeBrowserUa(lower) || !hasTransportSignals(signals)) {
            return null;
        }

        boolean hasAcceptLanguage = hasText(firstText(signals, "acceptLanguage", "accept_language", "lang"));
        String accept = firstText(signals, "accept");
        boolean acceptsHtml = contains(accept, "text/html");
        boolean wildcardAccept = "*/*".equals(accept != null ? accept.trim() : "");
        boolean clearlyNonBrowserAccept = hasText(accept) && !acceptsHtml && !wildcardAccept;
        boolean hasFetchMetadata = hasAnyText(signals,
                "secFetchSite", "sec_fetch_site",
                "secFetchMode", "sec_fetch_mode",
                "secFetchDest", "sec_fetch_dest",
                "secFetchUser", "sec_fetch_user",
                "secChUa", "sec_ch_ua",
                "secChUaPlatform", "sec_ch_ua_platform",
                "upgradeInsecureRequests", "upgrade_insecure_requests");

        if (!hasRuntimeSignals(signals) && !hasAcceptLanguage && !acceptsHtml && !hasFetchMetadata
                && (!hasText(accept) || clearlyNonBrowserAccept)) {
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

    /** 对外暴露的 UA 类型中文标签查询 */
    public static String typeLabelOf(String type) {
        return typeLabel(type);
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
            case "ai_crawler" -> "AI爬虫";
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

        /**
         * UA 是否声称自己是搜索引擎且应跳过自动化封禁检查。
         * <p>verified（DNS 验证通过）、unknown（首次访问/无规则/验证排队中）、
         * error/timeout（验证过程异常，保守放行）均跳过自动化检查。
         * <p>仅 failed（DNS 确认 IP 不属于官方域名，即伪装 UA）接受自动化封禁检查，
         * 避免伪造搜索引擎声明的自动化工具在伪装计数达到拉黑阈值前绕过 Java 层拦截。
         */
        public boolean claimsSearchEngine() {
            return !"not_applicable".equals(status) && !"failed".equals(status);
        }
    }

    private static final class UaBucket {
        private UaInfo info;
        private final String sampleUa;
        private long num;

        private UaBucket(UaInfo info, String sampleUa) {
            this.info = info;
            this.sampleUa = sampleUa;
        }

        private void promoteBotVerification(UaInfo newInfo) {
            if (this.info == null || newInfo == null) return;
            String currentStatus = this.info.botVerifyStatus();
            String newStatus = newInfo.botVerifyStatus();
            if ("verified".equals(currentStatus)) {
                return;
            }
            if ("verified".equals(newStatus)) {
                this.info = new UaInfo(
                        this.info.type(),
                        this.info.typeLabel(),
                        this.info.name(),
                        newInfo.botVerifyStatus(),
                        newInfo.botVerifyReason()
                );
                return;
            }
            if ("unknown".equals(currentStatus)) {
                return;
            }
            if ("unknown".equals(newStatus)) {
                this.info = new UaInfo(
                        this.info.type(),
                        this.info.typeLabel(),
                        this.info.name(),
                        newInfo.botVerifyStatus(),
                        newInfo.botVerifyReason()
                );
                return;
            }
            if ("not_applicable".equals(currentStatus) || "none".equals(currentStatus)) {
                this.info = new UaInfo(
                        this.info.type(),
                        this.info.typeLabel(),
                        this.info.name(),
                        newInfo.botVerifyStatus(),
                        newInfo.botVerifyReason()
                );
            }
        }
    }
}
