package com.ld.poetry.utils;

import com.ld.poetry.service.provider.Ip2RegionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 封禁规则地区名称标准化器。
 * <p>
 * 在 Java 输入端把管理员输入的地区名（如 "美国"/"US"/"中华人民共和国"/"江苏省"）
 * 统一校准为 xdb 实际返回值（如 "United States"/"中国"/"江苏"），
 * 与 Nginx 端 ban_check.lua 的精确匹配口径对齐。
 * <p>
 * 数据来源（三个互补渠道）：
 * <ol>
 *   <li>xdb 数据本身：遍历 xdb 提取 {国家代码 → xdb 国家名}，作为权威口径</li>
 *   <li>JDK Locale API：{国家代码 → 中文国家名}，用于把用户输入的中文别名映射到 xdb 名</li>
 *   <li>VisitRegionNormalizer 别名表：处理 "中华人民共和国"/"USA"/"UK" 等自然语言别名</li>
 * </ol>
 * <p>
 * 校准规则：
 * <ul>
 *   <li>country：严格校验。输入必须能映射到 xdb 实际国家名，否则拒绝并返回可选值列表</li>
 *   <li>province：仅去除中国省份后缀（与 Nginx parse_xdb_region 一致），不严格校验</li>
 * </ul>
 * <p>
 * fail-open 策略：xdb 不可用时（启动加载失败），country 类型接受原值并记录警告，
 * 避免阻塞管理员操作。xdb 可用时严格校验。
 */
@Slf4j
@Component
public class BanRegionNormalizer {

    private final Ip2RegionProvider ip2RegionProvider;

    /** {国家代码 → xdb 国家名}，例如 {"US" → "United States", "CN" → "中国"} */
    private volatile Map<String, String> countryCodeToXdbName = Collections.emptyMap();

    /** {中文国家名 → xdb 国家名}，例如 {"美国" → "United States", "中国" → "中国"} */
    private volatile Map<String, String> zhNameToXdbName = Collections.emptyMap();

    /** xdb 中所有出现过的国家名集合（用于直接匹配用户输入的 xdb 标准名） */
    private volatile Set<String> xdbCountryNames = Collections.emptySet();

    public BanRegionNormalizer(Ip2RegionProvider ip2RegionProvider) {
        this.ip2RegionProvider = ip2RegionProvider;
    }

    /**
     * 应用启动完成后异步构建映射（不阻塞启动）。
     * 遍历 xdb 约需 2-5 秒，放在 ApplicationReadyEvent 后执行。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            Map<String, String> codeToName = ip2RegionProvider.extractCountryCodeToNameMap();
            if (codeToName.isEmpty()) {
                log.warn("BanRegionNormalizer: xdb 国家映射为空，地区封禁规则校准将 fail-open（接受原值）");
                return;
            }

            // 构建 {中文国家名 → xdb 国家名} 反查映射
            Map<String, String> zhToXdb = new HashMap<>();
            for (Map.Entry<String, String> entry : codeToName.entrySet()) {
                String countryCode = entry.getKey();
                String xdbName = entry.getValue();
                // 用 JDK Locale 把国家代码转成中文国家名
                String zhName = getChineseCountryName(countryCode);
                if (zhName != null) {
                    zhToXdb.putIfAbsent(zhName, xdbName);
                }
                // xdb 国家名本身也作为 key（用户可能直接输入 xdb 名）
                zhToXdb.putIfAbsent(xdbName, xdbName);
            }

            this.countryCodeToXdbName = Collections.unmodifiableMap(codeToName);
            this.zhNameToXdbName = Collections.unmodifiableMap(zhToXdb);
            this.xdbCountryNames = Collections.unmodifiableSet(new TreeSet<>(codeToName.values()));
            log.info("BanRegionNormalizer 初始化完成：{} 个国家映射，{} 个中文别名", codeToName.size(), zhToXdb.size());
        } catch (Exception e) {
            log.warn("BanRegionNormalizer 初始化失败，地区封禁规则校准将 fail-open: {}", e.getMessage());
        }
    }

    /**
     * 标准化地区封禁规则的用户输入值。
     *
     * @param value      用户输入的地区名（如 "美国"/"US"/"中华人民共和国"/"江苏省"）
     * @param regionType "country" 或 "province"
     * @return 标准化结果
     */
    public NormalizeResult normalize(String value, String regionType) {
        if (value == null || value.trim().isEmpty()) {
            return NormalizeResult.fail("地区名不能为空", null);
        }
        String trimmed = value.trim();

        if ("province".equals(regionType)) {
            // 省份：仅去除中国省份后缀，不严格校验（与 Nginx parse_xdb_region 一致）
            return NormalizeResult.ok(normalizeProvinceSuffix(trimmed));
        }

        // country 类型
        if (zhNameToXdbName.isEmpty()) {
            // xdb 不可用，fail-open（接受原值，不阻塞管理员操作）
            log.warn("BanRegionNormalizer: xdb 映射不可用，country 类型 fail-open，接受原值: {}", trimmed);
            return NormalizeResult.ok(trimmed);
        }

        // 1. 用户直接输入了 xdb 标准国家名（如 "United States"/"中国"）
        if (xdbCountryNames.contains(trimmed)) {
            return NormalizeResult.ok(trimmed);
        }

        // 2. 通过 VisitRegionNormalizer 标准化为中文国家名，再反查 xdb 国家名
        String zhName = VisitRegionNormalizer.normalizeCountryName(trimmed);
        if (zhName == null) {
            return NormalizeResult.fail(
                    "无法识别的国家名: " + trimmed + "。请使用 xdb 标准国家名、ISO 国家代码或中文国家名",
                    suggestCountries(trimmed));
        }

        String xdbName = zhNameToXdbName.get(zhName);
        if (xdbName == null) {
            return NormalizeResult.fail(
                    "国家 '" + trimmed + "'（标准化为 '" + zhName + "'）不在 xdb 数据中，可能是无效国家或 xdb 未覆盖",
                    suggestCountries(trimmed));
        }

        return NormalizeResult.ok(xdbName);
    }

    /**
     * 获取所有可选的 xdb 标准国家名（用于前端提示或测试）。
     */
    public Set<String> getAvailableCountries() {
        return xdbCountryNames;
    }

    /**
     * 获取国家代码 → xdb 国家名映射（用于测试或调试）。
     */
    public Map<String, String> getCountryCodeToNameMap() {
        return countryCodeToXdbName;
    }

    /**
     * 标准化省份名：去除中国省份后缀。
     * 与 ban_check.lua parse_xdb_region 保持一致：
     * 循环去除末尾的 "自治区|特别行政区|维吾尔|回族|壮族|市|省" 后缀。
     * 例如："江苏省" → "江苏"，"广西壮族自治区" → "广西"，"北京" → "北京"
     */
    private String normalizeProvinceSuffix(String value) {
        String result = value;
        for (String suffix : new String[]{"自治区", "特别行政区", "维吾尔", "回族", "壮族", "市", "省"}) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }
        return result;
    }

    /**
     * 用 JDK Locale 获取国家代码对应的中文国家名。
     * 例如："US" → "美国"，"CN" → "中国"，"JP" → "日本"
     */
    private String getChineseCountryName(String countryCode) {
        try {
            if (countryCode == null || countryCode.length() != 2) {
                return null;
            }
            Locale locale = new Locale.Builder().setRegion(countryCode).build();
            String zhName = locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
            // JDK 对无效国家代码返回空字符串或国家代码本身
            if (zhName == null || zhName.isEmpty() || zhName.equals(countryCode)) {
                return null;
            }
            return zhName;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验失败时返回建议值（最多 10 个，按字母序）。
     * 如果用户输入是中文，优先返回中文国家名；否则返回 xdb 标准名。
     */
    private List<String> suggestCountries(String userInput) {
        if (xdbCountryNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> suggestions = new ArrayList<>();
        boolean isChineseInput = userInput != null && userInput.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);

        if (isChineseInput) {
            // 中文输入：返回中文国家名
            for (String xdbName : xdbCountryNames) {
                String zhName = VisitRegionNormalizer.normalizeCountryName(xdbName);
                if (zhName != null) {
                    suggestions.add(zhName);
                }
            }
        } else {
            // 英文/代码输入：返回 xdb 标准名
            suggestions.addAll(xdbCountryNames);
        }
        Collections.sort(suggestions);
        return suggestions.size() > 10 ? suggestions.subList(0, 10) : suggestions;
    }

    /**
     * 标准化结果。
     */
    public static class NormalizeResult {
        private final boolean success;
        private final String normalizedValue;
        private final String errorMessage;
        private final List<String> suggestions;

        private NormalizeResult(boolean success, String normalizedValue, String errorMessage, List<String> suggestions) {
            this.success = success;
            this.normalizedValue = normalizedValue;
            this.errorMessage = errorMessage;
            this.suggestions = suggestions;
        }

        public static NormalizeResult ok(String value) {
            return new NormalizeResult(true, value, null, null);
        }

        public static NormalizeResult fail(String message, List<String> suggestions) {
            return new NormalizeResult(false, null, message, suggestions);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getNormalizedValue() {
            return normalizedValue;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<String> getSuggestions() {
            return suggestions != null ? suggestions : Collections.emptyList();
        }
    }
}
