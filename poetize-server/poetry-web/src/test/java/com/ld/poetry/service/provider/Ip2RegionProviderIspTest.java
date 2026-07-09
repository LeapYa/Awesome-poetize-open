package com.ld.poetry.service.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ip2RegionProvider 的 ISP 解析与 IDC 判定测试
 * <p>
 * 使用随应用打包的 xdb 文件加载真实 Searcher，通过反射注入到 provider，
 * 验证 {@link Ip2RegionProvider#resolveIsp(String)} 和
 * {@link Ip2RegionProvider#isDatacenterIp(String)} 的行为。
 * <p>
 * xdb v4 实际数据格式为 {@code 国家|区域|城市|运营商或公司名|国家代码}，
 * ISP 信息位于第 4 段（index 3）。
 */
class Ip2RegionProviderIspTest {

    private Ip2RegionProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new Ip2RegionProvider();

        // IPv4 searcher
        URL v4Resource = Ip2RegionProviderIspTest.class
                .getClassLoader().getResource("ip2region/ip2region_v4.xdb");
        assertNotNull(v4Resource, "测试数据库资源不存在: ip2region/ip2region_v4.xdb");
        LongByteArray v4Buff = Searcher.loadContentFromFile(Paths.get(v4Resource.toURI()).toString());
        Searcher v4Searcher = Searcher.newWithBuffer(Version.IPv4, v4Buff);
        injectSearcher("ipv4Searcher", v4Searcher);
        injectBoolean("ipv4Available", true);

        // IPv6 searcher
        URL v6Resource = Ip2RegionProviderIspTest.class
                .getClassLoader().getResource("ip2region/ip2region_v6.xdb");
        assertNotNull(v6Resource, "测试数据库资源不存在: ip2region/ip2region_v6.xdb");
        LongByteArray v6Buff = Searcher.loadContentFromFile(Paths.get(v6Resource.toURI()).toString());
        Searcher v6Searcher = Searcher.newWithBuffer(Version.IPv6, v6Buff);
        injectSearcher("ipv6Searcher", v6Searcher);
        injectBoolean("ipv6Available", true);
    }

    private void injectSearcher(String fieldName, Searcher searcher) throws Exception {
        Field field = Ip2RegionProvider.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(provider, searcher);
    }

    private void injectBoolean(String fieldName, boolean value) throws Exception {
        Field field = Ip2RegionProvider.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(provider, value);
    }

    // ==================== resolveIsp ====================

    @Test
    void resolveIspReturnsGoogleForGoogleDns() {
        // 8.8.8.8 是 Google DNS，xdb 标记为 "Google LLC"
        String isp = provider.resolveIsp("8.8.8.8");
        assertNotNull(isp);
        assertTrue(isp.toLowerCase().contains("google"));
    }

    @Test
    void resolveIspReturnsAliyunForAliyunIp() {
        // 47.94.236.91 是阿里云北京段，xdb 标记为 "阿里"
        String isp = provider.resolveIsp("47.94.236.91");
        assertNotNull(isp);
        assertTrue(isp.contains("阿里"));
    }

    @Test
    void resolveIspReturnsAmazonForAwsIp() {
        // 52.94.236.248 是 AWS Ireland 段，xdb 标记为 "Amazon.com, Inc."
        String isp = provider.resolveIsp("52.94.236.248");
        assertNotNull(isp);
        assertTrue(isp.toLowerCase().contains("amazon"));
    }

    @Test
    void resolveIspReturnsLiantongForNormalUser() {
        // 61.135.185.32 是北京联通普通段，ISP 应为 "联通"
        String isp = provider.resolveIsp("61.135.185.32");
        assertNotNull(isp);
        assertEquals("联通", isp);
    }

    @Test
    void resolveIspReturnsNullForUnidentifiedIp() {
        // 114.114.114.114 的 ISP 段为 "0"，应返回 null
        String isp = provider.resolveIsp("114.114.114.114");
        assertNull(isp);
    }

    @Test
    void resolveIspReturnsNullForNullIp() {
        assertNull(provider.resolveIsp(null));
    }

    @Test
    void resolveIspReturnsNullWhenUnavailable() throws Exception {
        // 同时关闭 v4 和 v6，模拟 provider 完全不可用
        injectBoolean("ipv4Available", false);
        injectBoolean("ipv6Available", false);
        assertNull(provider.resolveIsp("8.8.8.8"));
    }

    // ==================== isDatacenterIp ====================

    @Test
    void isDatacenterIpReturnsTrueForAliyunIp() {
        // 47.94.236.91, 120.24.62.184, 106.14.7.196 都是阿里云段
        assertTrue(provider.isDatacenterIp("47.94.236.91"));
        assertTrue(provider.isDatacenterIp("120.24.62.184"));
        assertTrue(provider.isDatacenterIp("106.14.7.196"));
    }

    @Test
    void isDatacenterIpReturnsTrueForAwsIp() {
        assertTrue(provider.isDatacenterIp("52.94.236.248"));
    }

    @Test
    void isDatacenterIpReturnsTrueForGoogleDns() {
        // Google DNS 标记为 "Google LLC"，命中 google 关键词
        assertTrue(provider.isDatacenterIp("8.8.8.8"));
    }

    @Test
    void isDatacenterIpReturnsFalseForNormalUser() {
        // 北京联通普通用户
        assertFalse(provider.isDatacenterIp("61.135.185.32"));
    }

    @Test
    void isDatacenterIpReturnsFalseForUnidentifiedIp() {
        // 114.114.114.114 的 ISP 段为 "0"，返回 null，isDatacenterIp 应为 false
        assertFalse(provider.isDatacenterIp("114.114.114.114"));
    }

    @Test
    void isDatacenterIpReturnsFalseForNullIp() {
        assertFalse(provider.isDatacenterIp(null));
    }

    @Test
    void isDatacenterIpReturnsFalseWhenUnavailable() throws Exception {
        // 同时关闭 v4 和 v6，模拟 provider 完全不可用
        injectBoolean("ipv4Available", false);
        injectBoolean("ipv6Available", false);
        assertFalse(provider.isDatacenterIp("8.8.8.8"));
    }

    // ==================== IPv6 ISP 解析 ====================

    @Test
    void resolveIspReturnsGoogleForIpv6GoogleDns() {
        // 2001:4860:4860::8888 是 Google DNS 的 IPv6 地址
        // xdb v6 数据格式与 v4 一致：国家|区域|城市|运营商|国家代码
        String isp = provider.resolveIsp("2001:4860:4860::8888");
        assertNotNull(isp);
        assertTrue(isp.toLowerCase().contains("google"));
    }

    @Test
    void isDatacenterIpReturnsTrueForIpv6GoogleDns() {
        // Google DNS v6，ISP 段为 "Google LLC"，命中 google 关键词
        assertTrue(provider.isDatacenterIp("2001:4860:4860::8888"));
    }

    @Test
    void resolveIspReturnsValueForIpv6ChinaTelecom() {
        // 240e:3b7:3272:d8d0:db09:c067:8d59:539e 是中国电信 IPv6 段
        String isp = provider.resolveIsp("240e:3b7:3272:d8d0:db09:c067:8d59:539e");
        assertNotNull(isp);
        // 应为 "电信" 之类的运营商名
    }

    @Test
    void isDatacenterIpReturnsFalseForIpv6NormalUser() {
        // 中国电信 IPv6 普通用户，不应被识别为机房
        boolean result = provider.isDatacenterIp("240e:3b7:3272:d8d0:db09:c067:8d59:539e");
        assertFalse(result);
    }

    @Test
    void resolveIspReturnsNullForIpv6WhenV6Unavailable() throws Exception {
        // 只关闭 v6，v4 还可用，验证 IPv6 查询降级
        injectBoolean("ipv6Available", false);
        injectSearcher("ipv6Searcher", null);
        assertNull(provider.resolveIsp("2001:4860:4860::8888"));
    }

    @Test
    void isDatacenterIpReturnsFalseForIpv6WhenV6Unavailable() throws Exception {
        // 只关闭 v6，v4 还可用，IPv6 查询应降级返回 false
        injectBoolean("ipv6Available", false);
        injectSearcher("ipv6Searcher", null);
        assertFalse(provider.isDatacenterIp("2001:4860:4860::8888"));
    }
}
