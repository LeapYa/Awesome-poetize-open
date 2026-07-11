package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdminController 扩展封禁规则接口（blockRule/unblockRule）参数校验单元测试。
 *
 * <p>不启动 Spring Context，通过 ReflectionTestUtils 注入 mock CacheService，
 * 直接调用 public 方法验证参数校验逻辑。
 */
class AdminBlockRuleControllerTest {

    private AdminController controller;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        controller = new AdminController();
        ReflectionTestUtils.setField(controller, "cacheService", cacheService);
    }

    // ================================ blockRule 参数校验 ================================

    @Test
    @DisplayName("blockRule：非法 CIDR '192.168.1.0/33'，应返回失败")
    void blockRule_invalidCidr_returnsFail() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "cidr");
        request.put("value", "192.168.1.0/33");

        PoetryResult<Map<String, Object>> result = controller.blockRule(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("CIDR"));
    }

    @Test
    @DisplayName("blockRule：region 缺少 regionType，应返回失败")
    void blockRule_missingRegionType_returnsFail() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "region");
        request.put("value", "广东");

        PoetryResult<Map<String, Object>> result = controller.blockRule(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("regionType"));
    }

    @Test
    @DisplayName("blockRule：UA 非法 matchMode 'regex'，应返回失败")
    void blockRule_invalidMatchMode_returnsFail() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "ua");
        request.put("value", "x");
        request.put("matchMode", "regex");

        PoetryResult<Map<String, Object>> result = controller.blockRule(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("matchMode"));
    }

    @Test
    @DisplayName("blockRule：非法 type 'unknown'，应返回失败")
    void blockRule_invalidType_returnsFail() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "unknown");
        request.put("value", "x");

        PoetryResult<Map<String, Object>> result = controller.blockRule(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("type"));
    }

    @Test
    @DisplayName("blockRule：value 为空，应返回失败")
    void blockRule_emptyValue_returnsFail() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "ua");
        request.put("value", "");

        PoetryResult<Map<String, Object>> result = controller.blockRule(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("blockRule：合法 UA 规则（contains），应返回成功")
    void blockRule_validUa_returnsSuccess() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "ua");
        request.put("value", "semrush");
        request.put("matchMode", "contains");

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("id", "test123");
        mockResult.put("existed", false);
        when(cacheService.addBanRule(anyString(), anyString(), nullable(String.class),
                nullable(String.class), nullable(String.class), anyLong()))
                .thenReturn(mockResult);

        PoetryResult<Map<String, Object>> result = controller.blockRule(request);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    // ================================ unblockRule 参数校验 ================================

    @Test
    @DisplayName("unblockRule：缺少 id，应返回失败")
    void unblockRule_missingId_returnsFail() {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "ua");

        PoetryResult<Map<String, Object>> result = controller.unblockRule(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("id"));
    }
}
