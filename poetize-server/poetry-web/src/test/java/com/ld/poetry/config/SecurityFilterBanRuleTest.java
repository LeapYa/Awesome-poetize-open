package com.ld.poetry.config;

import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.provider.Ip2RegionProvider;
import com.ld.poetry.utils.RedisUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SecurityFilter 扩展封禁规则（UA/CIDR/Region）匹配逻辑单元测试。
 *
 * <p>匹配方法（isCidrBanned/isRegionBanned/isUaBannedByAdmin）均为 private，测试策略为
 * 通过 {@code doFilter} 行为验证匹配结果：命中封禁返回 403 + 特定关键词，未命中则放行至 FilterChain。
 */
class SecurityFilterBanRuleTest {

    private SecurityFilter filter;
    private RedisUtil redisUtil;
    private CacheService cacheService;
    private Ip2RegionProvider ip2RegionProvider;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(redisUtil.incr(anyString(), anyLong())).thenReturn(0L);

        cacheService = mock(CacheService.class);
        when(cacheService.loadAllBanRules()).thenReturn(null);

        ip2RegionProvider = mock(Ip2RegionProvider.class);

        filter = new SecurityFilter();
        ReflectionTestUtils.setField(filter, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(filter, "cacheService", cacheService);
        ReflectionTestUtils.setField(filter, "ip2RegionProvider", ip2RegionProvider);
        // 设置最近刷新时间，避免 refreshBanRulesIfStale 触发 Redis 加载覆盖手动注入的规则
        ReflectionTestUtils.setField(filter, "lastRefreshAt", System.currentTimeMillis());
    }

    // ================================ UA 规则匹配 ================================

    @Test
    @DisplayName("UA contains 匹配：规则 contains 'semrush'，请求 UA 含 'SemrushBot'，应返回 403")
    void uaContainsMatch_blocksRequest() throws Exception {
        setUaRules(Map.of("value", "semrush", "matchMode", "contains"));

        MockHttpServletRequest request = newRequest("1.2.3.4", "Mozilla/5.0 (compatible; SemrushBot/7~bl; +http://www.semrush.com/bot.html)");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("UA Blacklisted"));
    }

    @Test
    @DisplayName("UA equals 匹配：规则 equals 'curl/8.0.1'，请求 UA 恰好 'curl/8.0.1'，应返回 403")
    void uaEqualsMatch_blocksRequest() throws Exception {
        setUaRules(Map.of("value", "curl/8.0.1", "matchMode", "equals"));

        MockHttpServletRequest request = newRequest("1.2.3.4", "curl/8.0.1");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("UA Blacklisted"));
    }

    @Test
    @DisplayName("UA 大小写不敏感：规则 'SemRush' contains，请求 UA 'semrushbot'，应返回 403")
    void uaCaseInsensitive_blocksRequest() throws Exception {
        setUaRules(Map.of("value", "SemRush", "matchMode", "contains"));

        MockHttpServletRequest request = newRequest("1.2.3.4", "semrushbot/2.1");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("UA Blacklisted"));
    }

    @Test
    @DisplayName("UA 不匹配：规则 'semrush'，请求 UA 'Mozilla/5.0'，应放行")
    void uaNotMatch_passesThrough() throws Exception {
        setUaRules(Map.of("value", "semrush", "matchMode", "contains"));

        MockHttpServletRequest request = newRequest("1.2.3.4", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("空 UA 跳过检查：不设 UA 头，有 UA 规则，应放行")
    void emptyUa_skipsCheck() throws Exception {
        setUaRules(Map.of("value", "semrush", "matchMode", "contains"));

        MockHttpServletRequest request = newRequest("1.2.3.4", null);
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ================================ CIDR 规则匹配 ================================

    @Test
    @DisplayName("CIDR 匹配：规则 '192.168.1.0/24'，客户端 IP '192.168.1.55'，应返回 403")
    void cidrMatch_blocksRequest() throws Exception {
        setCidrRules(Map.of("value", "192.168.1.0/24"));

        MockHttpServletRequest request = newRequest("192.168.1.55", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CIDR Blacklisted"));
    }

    @Test
    @DisplayName("CIDR 不匹配：规则 '192.168.1.0/24'，客户端 IP '192.168.2.1'，应放行")
    void cidrNotMatch_passesThrough() throws Exception {
        setCidrRules(Map.of("value", "192.168.1.0/24"));

        MockHttpServletRequest request = newRequest("192.168.2.1", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("CIDR IPv6 匹配：规则 '2001:db8::/32'，客户端 IP '2001:db8::1'，应返回 403")
    void cidrIpv6Match_blocksRequest() throws Exception {
        setCidrRules(Map.of("value", "2001:db8::/32"));

        MockHttpServletRequest request = newRequest("2001:db8::1", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CIDR Blacklisted"));
    }

    @Test
    @DisplayName("CIDR IPv6 不匹配：规则 '2001:db8::/32'，客户端 IP '2001:db9::1'，应放行")
    void cidrIpv6NotMatch_passesThrough() throws Exception {
        setCidrRules(Map.of("value", "2001:db8::/32"));

        MockHttpServletRequest request = newRequest("2001:db9::1", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ================================ Region 规则匹配 ================================

    @Test
    @DisplayName("Region country 匹配：规则 country '美国'，ip2region 返回 ['美国','加利福尼亚','洛杉矶']，应返回 403")
    void regionCountryMatch_blocksRequest() throws Exception {
        setRegionRules(Map.of("value", "美国", "regionType", "country"));
        when(ip2RegionProvider.resolveLocationDetail(anyString()))
                .thenReturn(new String[]{"美国", "加利福尼亚", "洛杉矶"});

        MockHttpServletRequest request = newRequest("8.8.8.8", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Region Blacklisted"));
    }

    @Test
    @DisplayName("Region province 匹配：规则 province '广东'，ip2region 返回 ['中国','广东','深圳']，应返回 403")
    void regionProvinceMatch_blocksRequest() throws Exception {
        setRegionRules(Map.of("value", "广东", "regionType", "province"));
        when(ip2RegionProvider.resolveLocationDetail(anyString()))
                .thenReturn(new String[]{"中国", "广东", "深圳"});

        MockHttpServletRequest request = newRequest("120.239.141.213", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Region Blacklisted"));
    }

    @Test
    @DisplayName("Region 前缀容错：规则 '广东'，ip2region 返回省份 '广东省'，应返回 403")
    void regionPrefixTolerance_blocksRequest() throws Exception {
        setRegionRules(Map.of("value", "广东", "regionType", "province"));
        when(ip2RegionProvider.resolveLocationDetail(anyString()))
                .thenReturn(new String[]{"中国", "广东省", "深圳"});

        MockHttpServletRequest request = newRequest("120.239.141.213", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Region Blacklisted"));
    }

    @Test
    @DisplayName("Region 解析失败：有 region 规则，ip2region 返回 [null,null,null]，应放行（不误伤）")
    void regionResolveFail_passesThrough() throws Exception {
        setRegionRules(Map.of("value", "广东", "regionType", "province"));
        when(ip2RegionProvider.resolveLocationDetail(anyString()))
                .thenReturn(new String[]{null, null, null});

        MockHttpServletRequest request = newRequest("1.2.3.4", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ================================ 空规则 ================================

    @Test
    @DisplayName("三类规则都为空，应放行")
    void emptyRules_passesThrough() throws Exception {
        // 规则列表默认为 List.of()（空），无需额外设置

        MockHttpServletRequest request = newRequest("1.2.3.4", "Mozilla/5.0");
        MockHttpServletResponse response = newResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ================================ 辅助方法 ================================

    @SuppressWarnings("unchecked")
    private void setUaRules(Map<String, Object>... rules) {
        ReflectionTestUtils.setField(filter, "uaRules", Arrays.asList(rules));
    }

    @SuppressWarnings("unchecked")
    private void setCidrRules(Map<String, Object>... rules) {
        ReflectionTestUtils.setField(filter, "cidrRules", Arrays.asList(rules));
    }

    @SuppressWarnings("unchecked")
    private void setRegionRules(Map<String, Object>... rules) {
        ReflectionTestUtils.setField(filter, "regionRules", Arrays.asList(rules));
    }

    private MockHttpServletRequest newRequest(String remoteAddr, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/");
        request.setRemoteAddr(remoteAddr);
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        return request;
    }

    private MockHttpServletResponse newResponse() {
        return new MockHttpServletResponse();
    }
}
