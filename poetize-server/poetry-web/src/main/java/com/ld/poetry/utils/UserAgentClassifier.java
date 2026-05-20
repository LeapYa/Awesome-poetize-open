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
        if (userAgent == null || userAgent.isBlank() || "-".equals(userAgent.trim())) {
            return new UaInfo("unknown", "未知", "未知客户端");
        }

        String ua = userAgent.trim();
        String lower = ua.toLowerCase(Locale.ROOT);

        String searchEngine = searchEngineName(lower);
        if (searchEngine != null) {
            return new UaInfo("search_engine", "搜索引擎", searchEngine);
        }

        String crawler = crawlerName(lower);
        if (crawler != null) {
            return new UaInfo("crawler", "爬虫", crawler);
        }

        boolean mobile = isMobile(lower);
        return new UaInfo(mobile ? "mobile" : "pc", mobile ? "移动端" : "PC端", browserName(lower, mobile));
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
            if (ua == null || ua.isBlank() || "-".equals(ua.trim())) continue;

            long count = asLong(row.get("num"));
            if (count <= 0) continue;

            mergeBucket(buckets, ua, count);
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
            if (ua == null || ua.isBlank() || "-".equals(ua.trim())) continue;
            mergeBucket(buckets, ua, 1L);
        }
    }

    private static void mergeBucket(Map<String, UaBucket> buckets, String userAgent, long count) {
        UaInfo info = classify(userAgent);
        String key = info.type() + "|" + info.name();
        UaBucket bucket = buckets.computeIfAbsent(key, ignored -> new UaBucket(info, userAgent));
        bucket.num += count;
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
                    row.put("num", bucket.num);
                    row.put("sample_ua", bucket.sampleUa);
                    return row;
                })
                .toList();
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

    private static String crawlerName(String lower) {
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

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
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

    public record UaInfo(String type, String typeLabel, String name) {
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
