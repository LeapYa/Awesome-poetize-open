package com.ld.poetry.utils;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 访问地域统计统一口径：国内显示省份，国外显示中文国家。
 */
public final class VisitRegionNormalizer {

    public static final String UNKNOWN_REGION = "未知";
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;
    private static final Set<String> INVALID_VALUES = Set.of(
            "0", "-", "unknown", "reserved", "null", "undefined", "未知", "内网IP", "内网ip"
    );
    private static final Set<String> CHINA_NAMES = Set.of(
            "中国", "china", "cn", "chn", "prc", "people s republic of china", "people's republic of china"
    );
    private static final Map<String, String> COUNTRY_NAMES = buildCountryNames();
    private static final Map<String, String> REGION_COUNTRY_ALIASES = buildRegionCountryAliases();

    private VisitRegionNormalizer() {
    }

    public static String resolveProvinceOrCountry(Object nation, Object province, Object city) {
        String country = normalizeCountryName(nation);
        if (StringUtils.hasText(country) && !"中国".equals(country)) {
            return country;
        }

        String provinceValue = normalizeLocationValue(province);
        if ("中国".equals(country)) {
            String chinaProvince = normalizeChinaProvince(provinceValue);
            return StringUtils.hasText(chinaProvince) ? chinaProvince : "中国";
        }

        String provinceCountry = normalizeCountryName(provinceValue);
        if (StringUtils.hasText(provinceCountry)) {
            return provinceCountry;
        }

        String inferredFromProvince = inferCountryFromRegion(provinceValue);
        if (StringUtils.hasText(inferredFromProvince)) {
            return inferredFromProvince;
        }

        String cityValue = normalizeLocationValue(city);
        String cityCountry = normalizeCountryName(cityValue);
        if (StringUtils.hasText(cityCountry)) {
            return cityCountry;
        }

        String inferredFromCity = inferCountryFromRegion(cityValue);
        if (StringUtils.hasText(inferredFromCity)) {
            return inferredFromCity;
        }

        String chinaProvince = normalizeChinaProvince(provinceValue);
        if (StringUtils.hasText(chinaProvince)) {
            return chinaProvince;
        }

        return UNKNOWN_REGION;
    }

    public static String normalizeCountryName(Object value) {
        String normalized = normalizeLocationValue(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        String key = normalizeKey(normalized);
        if (CHINA_NAMES.contains(key) || CHINA_NAMES.contains(normalized)) {
            return "中国";
        }

        String country = COUNTRY_NAMES.get(key);
        return StringUtils.hasText(country) ? country : null;
    }

    public static boolean isChina(Object value) {
        return "中国".equals(normalizeCountryName(value));
    }

    public static List<Map<String, Object>> normalizeProvinceStatistics(Object provinceStats) {
        if (!(provinceStats instanceof List<?> rawList)) {
            return List.of();
        }

        Map<String, Long> merged = new HashMap<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String region = resolveProvinceOrCountry(
                    rawMap.get("nation"),
                    rawMap.get("province"),
                    rawMap.get("city")
            );
            long count = toLong(rawMap.get("num"));
            if (count <= 0) {
                continue;
            }
            merged.merge(region, count, Long::sum);
        }

        return merged.entrySet().stream()
                .sorted(VisitRegionNormalizer::compareRegionStats)
                .limit(10)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("province", entry.getKey());
                    row.put("num", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }

    private static int compareRegionStats(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
        int countCompare = Long.compare(right.getValue(), left.getValue());
        if (countCompare != 0) {
            return countCompare;
        }
        boolean leftUnknown = UNKNOWN_REGION.equals(left.getKey());
        boolean rightUnknown = UNKNOWN_REGION.equals(right.getKey());
        if (leftUnknown != rightUnknown) {
            return leftUnknown ? 1 : -1;
        }
        return left.getKey().compareTo(right.getKey());
    }

    private static String normalizeChinaProvince(String value) {
        String normalized = normalizeLocationValue(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String country = normalizeCountryName(normalized);
        if (StringUtils.hasText(country) && !"中国".equals(country)) {
            return country;
        }
        if ("中国".equals(country)) {
            return "中国";
        }
        if (!containsChinese(normalized)) {
            return null;
        }
        return normalized.replaceAll("省|市|自治区|特别行政区|壮族|回族|维吾尔", "");
    }

    private static String inferCountryFromRegion(String value) {
        String normalized = normalizeLocationValue(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return REGION_COUNTRY_ALIASES.get(normalizeKey(normalized));
    }

    private static String normalizeLocationValue(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        String key = normalizeKey(trimmed);
        if (INVALID_VALUES.contains(key) || INVALID_VALUES.contains(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static boolean containsChinese(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static long toLong(Object value) {
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

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(".", "")
                .replaceAll("\\s+", " ");
    }

    private static Map<String, String> buildCountryNames() {
        Map<String, String> names = new HashMap<>();
        for (String countryCode : Locale.getISOCountries()) {
            Locale countryLocale = new Locale.Builder().setRegion(countryCode).build();
            String zhName = countryLocale.getDisplayCountry(ZH_CN);
            names.put(normalizeKey(countryCode), zhName);
            names.put(normalizeKey(countryLocale.getDisplayCountry(Locale.ENGLISH)), zhName);
            names.put(normalizeKey(zhName), zhName);
        }

        names.put(normalizeKey("USA"), "美国");
        names.put(normalizeKey("U.S."), "美国");
        names.put(normalizeKey("U.S.A."), "美国");
        names.put(normalizeKey("America"), "美国");
        names.put(normalizeKey("United States of America"), "美国");
        names.put(normalizeKey("UK"), "英国");
        names.put(normalizeKey("U.K."), "英国");
        names.put(normalizeKey("Great Britain"), "英国");
        names.put(normalizeKey("England"), "英国");
        names.put(normalizeKey("Korea"), "韩国");
        names.put(normalizeKey("South Korea"), "韩国");
        names.put(normalizeKey("Russia"), "俄罗斯");
        names.put(normalizeKey("Vietnam"), "越南");
        names.put(normalizeKey("Hong Kong"), "中国香港");
        names.put(normalizeKey("Macao"), "中国澳门");
        names.put(normalizeKey("Macau"), "中国澳门");
        names.put(normalizeKey("Taiwan"), "中国台湾");
        return names;
    }

    private static Map<String, String> buildRegionCountryAliases() {
        Map<String, String> aliases = new HashMap<>();
        putAliases(aliases, "美国",
                "California", "Los Angeles", "Santa Clara", "San Francisco", "San Jose",
                "New York", "Virginia", "Ashburn", "Seattle", "Dallas", "Chicago",
                "Miami", "Phoenix", "Oregon", "Texas", "Washington");
        putAliases(aliases, "印度",
                "Mumbai", "Maharashtra", "Delhi", "Bangalore", "Bengaluru", "Chennai", "Hyderabad");
        putAliases(aliases, "英国", "London", "Manchester", "Birmingham", "England", "Scotland", "Wales");
        putAliases(aliases, "法国", "Paris", "Ile de France", "Île-de-France", "Marseille", "Lyon");
        putAliases(aliases, "德国", "Frankfurt", "Hesse", "Berlin", "Bavaria", "Munich");
        putAliases(aliases, "日本", "Tokyo", "Osaka", "Saitama", "Kanagawa");
        putAliases(aliases, "新加坡", "Singapore");
        putAliases(aliases, "中国香港", "Hong Kong");
        putAliases(aliases, "中国台湾", "Taipei", "Taiwan");
        return aliases;
    }

    private static void putAliases(Map<String, String> aliases, String country, String... values) {
        for (String value : values) {
            aliases.put(normalizeKey(value), country);
        }
    }
}
