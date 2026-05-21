package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitRegionNormalizerTest {

    @Test
    void resolvesForeignVisitsToChineseCountry() {
        assertEquals("美国", VisitRegionNormalizer.resolveProvinceOrCountry("United States", "Santa Clara", null));
        assertEquals("美国", VisitRegionNormalizer.resolveProvinceOrCountry("United States", "Los Angeles", null));
        assertEquals("法国", VisitRegionNormalizer.resolveProvinceOrCountry(null, "France", null));
    }

    @Test
    void keepsChinaVisitsAtProvinceLevel() {
        assertEquals("广东", VisitRegionNormalizer.resolveProvinceOrCountry("中国", "广东省", "深圳市"));
        assertEquals("中国", VisitRegionNormalizer.resolveProvinceOrCountry("中国", null, null));
    }

    @Test
    void keepsUnresolvedAndReservedVisitsAsUnknown() {
        assertEquals("未知", VisitRegionNormalizer.resolveProvinceOrCountry(null, null, null));
        assertEquals("未知", VisitRegionNormalizer.resolveProvinceOrCountry("Reserved", "0", "-"));
    }

    @Test
    void mergesEnglishForeignRowsIntoChineseCountryStats() {
        List<Map<String, Object>> rows = List.of(
                row("United States", "Santa Clara", 26),
                row("United States", "Los Angeles", 4),
                row(null, "France", 1),
                row("中国", "广东省", 2),
                row("Reserved", null, 1)
        );

        List<Map<String, Object>> stats = VisitRegionNormalizer.normalizeProvinceStatistics(rows);

        assertEquals("美国", stats.get(0).get("province"));
        assertEquals(30L, stats.get(0).get("num"));
        assertEquals("广东", stats.get(1).get("province"));
        assertEquals(2L, stats.get(1).get("num"));
        assertEquals("法国", stats.get(2).get("province"));
        assertEquals(1L, stats.get(2).get("num"));
        assertEquals("未知", stats.get(3).get("province"));
        assertEquals(1L, stats.get(3).get("num"));
    }

    private static Map<String, Object> row(String nation, String province, long count) {
        Map<String, Object> row = new HashMap<>();
        if (nation != null) {
            row.put("nation", nation);
        }
        if (province != null) {
            row.put("province", province);
        }
        row.put("num", count);
        return row;
    }
}
