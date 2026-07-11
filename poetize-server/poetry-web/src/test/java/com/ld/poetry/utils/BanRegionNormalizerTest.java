package com.ld.poetry.utils;

import com.ld.poetry.service.provider.Ip2RegionProvider;
import com.ld.poetry.utils.BanRegionNormalizer.NormalizeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BanRegionNormalizer 单元测试。
 *
 * <p>用反射注入预构建的映射数据，不依赖真实 xdb 文件，专注测试 normalize 逻辑。
 * xdb 遍历逻辑由 Ip2RegionProvider 测试覆盖。
 */
class BanRegionNormalizerTest {

    private BanRegionNormalizer normalizer;

    @BeforeEach
    void setUp() {
        // mock Ip2RegionProvider，normalize 逻辑不依赖它（映射通过反射注入）
        Ip2RegionProvider mockProvider = org.mockito.Mockito.mock(Ip2RegionProvider.class);
        normalizer = new BanRegionNormalizer(mockProvider);

        // 预构建映射，模拟 init() 的结果
        Map<String, String> codeToName = new HashMap<>();
        codeToName.put("CN", "中国");
        codeToName.put("US", "United States");
        codeToName.put("JP", "Japan");
        codeToName.put("GB", "United Kingdom");
        codeToName.put("DE", "Germany");

        Map<String, String> zhToXdb = new HashMap<>();
        zhToXdb.put("中国", "中国");
        zhToXdb.put("美国", "United States");
        zhToXdb.put("日本", "Japan");
        zhToXdb.put("英国", "United Kingdom");
        zhToXdb.put("德国", "Germany");
        // xdb 名自身也作为 key
        zhToXdb.put("United States", "United States");
        zhToXdb.put("Japan", "Japan");
        zhToXdb.put("United Kingdom", "United Kingdom");
        zhToXdb.put("Germany", "Germany");

        Set<String> xdbNames = new TreeSet<>(codeToName.values());

        ReflectionTestUtils.setField(normalizer, "countryCodeToXdbName", codeToName);
        ReflectionTestUtils.setField(normalizer, "zhNameToXdbName", zhToXdb);
        ReflectionTestUtils.setField(normalizer, "xdbCountryNames", xdbNames);
    }

    // ================================ country 类型 ================================

    @Test
    @DisplayName("country: 直接输入 xdb 标准名 'United States' 应原值通过")
    void normalizeCountry_xdbStandardName() {
        NormalizeResult result = normalizer.normalize("United States", "country");
        assertTrue(result.isSuccess());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 直接输入 xdb 标准名 '中国' 应原值通过")
    void normalizeCountry_chinaXdbName() {
        NormalizeResult result = normalizer.normalize("中国", "country");
        assertTrue(result.isSuccess());
        assertEquals("中国", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: ISO 国家代码 'US' 应映射到 'United States'")
    void normalizeCountry_isoCode() {
        NormalizeResult result = normalizer.normalize("US", "country");
        assertTrue(result.isSuccess());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: ISO 国家代码 'CN' 应映射到 '中国'")
    void normalizeCountry_chinaCode() {
        NormalizeResult result = normalizer.normalize("CN", "country");
        assertTrue(result.isSuccess());
        assertEquals("中国", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 中文国家名 '美国' 应映射到 'United States'")
    void normalizeCountry_chineseName() {
        NormalizeResult result = normalizer.normalize("美国", "country");
        assertTrue(result.isSuccess());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 别名 '中华人民共和国' 应映射到 '中国'")
    void normalizeCountry_chinaAlias() {
        NormalizeResult result = normalizer.normalize("中华人民共和国", "country");
        assertTrue(result.isSuccess());
        assertEquals("中国", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 别名 'USA' 应映射到 'United States'")
    void normalizeCountry_usaAlias() {
        NormalizeResult result = normalizer.normalize("USA", "country");
        assertTrue(result.isSuccess());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 英文全称 'United States of America' 应映射到 'United States'")
    void normalizeCountry_fullEnglishName() {
        NormalizeResult result = normalizer.normalize("United States of America", "country");
        assertTrue(result.isSuccess());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 大小写不敏感 'us' 应映射到 'United States'")
    void normalizeCountry_lowercaseCode() {
        NormalizeResult result = normalizer.normalize("us", "country");
        assertTrue(result.isSuccess());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("country: 无效输入 '火星' 应失败并返回建议值")
    void normalizeCountry_invalidInput() {
        NormalizeResult result = normalizer.normalize("火星", "country");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("火星"));
        assertFalse(result.getSuggestions().isEmpty());
    }

    @Test
    @DisplayName("country: 无效国家代码 'XX' 应失败")
    void normalizeCountry_invalidCode() {
        NormalizeResult result = normalizer.normalize("XX", "country");
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("country: 空值应失败")
    void normalizeCountry_emptyValue() {
        NormalizeResult result = normalizer.normalize("", "country");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("空"));
    }

    @Test
    @DisplayName("country: null 应失败")
    void normalizeCountry_nullValue() {
        NormalizeResult result = normalizer.normalize(null, "country");
        assertFalse(result.isSuccess());
    }

    // ================================ province 类型 ================================

    @Test
    @DisplayName("province: '江苏省' 应去后缀为 '江苏'")
    void normalizeProvince_withSuffix() {
        NormalizeResult result = normalizer.normalize("江苏省", "province");
        assertTrue(result.isSuccess());
        assertEquals("江苏", result.getNormalizedValue());
    }

    @Test
    @DisplayName("province: '广西壮族自治区' 应去复合后缀为 '广西'")
    void normalizeProvince_compoundSuffix() {
        NormalizeResult result = normalizer.normalize("广西壮族自治区", "province");
        assertTrue(result.isSuccess());
        assertEquals("广西", result.getNormalizedValue());
    }

    @Test
    @DisplayName("province: '新疆维吾尔自治区' 应去复合后缀为 '新疆'")
    void normalizeProvince_xinjiang() {
        NormalizeResult result = normalizer.normalize("新疆维吾尔自治区", "province");
        assertTrue(result.isSuccess());
        assertEquals("新疆", result.getNormalizedValue());
    }

    @Test
    @DisplayName("province: '北京市' 应去后缀为 '北京'")
    void normalizeProvince_beijing() {
        NormalizeResult result = normalizer.normalize("北京市", "province");
        assertTrue(result.isSuccess());
        assertEquals("北京", result.getNormalizedValue());
    }

    @Test
    @DisplayName("province: '北京' 无后缀应原值通过")
    void normalizeProvince_noSuffix() {
        NormalizeResult result = normalizer.normalize("北京", "province");
        assertTrue(result.isSuccess());
        assertEquals("北京", result.getNormalizedValue());
    }

    @Test
    @DisplayName("province: 国外省份 'California' 应原值通过（不严格校验）")
    void normalizeProvince_foreignProvince() {
        NormalizeResult result = normalizer.normalize("California", "province");
        assertTrue(result.isSuccess());
        assertEquals("California", result.getNormalizedValue());
    }

    @Test
    @DisplayName("province: '香港特别行政区' 应去后缀为 '香港'")
    void normalizeProvince_hongKong() {
        NormalizeResult result = normalizer.normalize("香港特别行政区", "province");
        assertTrue(result.isSuccess());
        assertEquals("香港", result.getNormalizedValue());
    }

    // ================================ fail-open 场景 ================================

    @Test
    @DisplayName("country: xdb 映射为空时应 fail-open（接受原值）")
    void normalizeCountry_xdbUnavailable_failOpen() {
        BanRegionNormalizer emptyNormalizer = new BanRegionNormalizer(
                org.mockito.Mockito.mock(Ip2RegionProvider.class));
        // 映射为空（init 未调用或 xdb 不可用）
        NormalizeResult result = emptyNormalizer.normalize("美国", "country");
        assertTrue(result.isSuccess());
        assertEquals("美国", result.getNormalizedValue());
    }
}
