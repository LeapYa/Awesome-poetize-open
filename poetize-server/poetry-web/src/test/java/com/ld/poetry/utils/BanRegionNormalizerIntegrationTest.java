package com.ld.poetry.utils;

import com.ld.poetry.service.provider.Ip2RegionProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BanRegionNormalizer 集成测试：验证 xdb 遍历 → 映射构建 → normalize 全链路。
 *
 * <p>使用 classpath 中的真实 xdb 文件（resources/ip2region/ip2region_v4.xdb），
 * 验证 extractCountryCodeToNameMap 能正确遍历 xdb 并提取国家映射。
 */
class BanRegionNormalizerIntegrationTest {

    private static Ip2RegionProvider provider;
    private static BanRegionNormalizer normalizer;

    @BeforeAll
    static void setUp() {
        provider = new Ip2RegionProvider();
        provider.initIp2Region();
        normalizer = new BanRegionNormalizer(provider);
        normalizer.init();
    }

    @Test
    @DisplayName("extractCountryCodeToNameMap 应返回非空映射，包含 CN 和 US")
    void extractCountryMap_containsCommonCountries() {
        Map<String, String> map = provider.extractCountryCodeToNameMap();
        assertFalse(map.isEmpty(), "国家映射不应为空");
        assertTrue(map.containsKey("CN"), "应包含 CN（中国）");
        assertTrue(map.containsKey("US"), "应包含 US（美国）");
        assertEquals("中国", map.get("CN"));
        assertEquals("United States", map.get("US"));
        System.out.println("xdb 国家映射数量: " + map.size());
        System.out.println("CN -> " + map.get("CN"));
        System.out.println("US -> " + map.get("US"));
        System.out.println("JP -> " + map.get("JP"));
    }

    @Test
    @DisplayName("全链路: '美国' → 'United States'（真实 xdb）")
    void fullChain_usa() {
        BanRegionNormalizer.NormalizeResult result = normalizer.normalize("美国", "country");
        assertTrue(result.isSuccess(), "应成功: " + result.getErrorMessage());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("全链路: '中华人民共和国' → '中国'（真实 xdb）")
    void fullChain_chinaAlias() {
        BanRegionNormalizer.NormalizeResult result = normalizer.normalize("中华人民共和国", "country");
        assertTrue(result.isSuccess(), "应成功: " + result.getErrorMessage());
        assertEquals("中国", result.getNormalizedValue());
    }

    @Test
    @DisplayName("全链路: 'US' → 'United States'（真实 xdb）")
    void fullChain_usCode() {
        BanRegionNormalizer.NormalizeResult result = normalizer.normalize("US", "country");
        assertTrue(result.isSuccess(), "应成功: " + result.getErrorMessage());
        assertEquals("United States", result.getNormalizedValue());
    }

    @Test
    @DisplayName("全链路: '江苏省' → '江苏'（省份后缀去除）")
    void fullChain_jiangsuProvince() {
        BanRegionNormalizer.NormalizeResult result = normalizer.normalize("江苏省", "province");
        assertTrue(result.isSuccess());
        assertEquals("江苏", result.getNormalizedValue());
    }
}
