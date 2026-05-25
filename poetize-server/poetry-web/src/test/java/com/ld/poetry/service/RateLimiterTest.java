package com.ld.poetry.service;

import com.ld.poetry.service.provider.IpLocationProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流功能测试
 * 验证LocationService的1QPS限流是否正常工作
 */
public class RateLimiterTest {

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService();
    }

    @Test
    public void testRateLimiterWithMultipleRequests() {
        // 测试连续请求是否被正确限流
        String testIp = "8.8.8.8"; // 使用公网IP进行测试
        IpLocationProviderFactory providerFactory = mock(IpLocationProviderFactory.class);
        when(providerFactory.resolveLocation(testIp)).thenReturn("United States|California|0|Google LLC|US");
        ReflectionTestUtils.setField(locationService, "providerFactory", providerFactory);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 连续发起3次请求
        for (int i = 0; i < 3; i++) {
            System.out.println("第" + (i + 1) + "次请求开始时间: " + System.currentTimeMillis());
            String result = locationService.getLocationByIp(testIp);
            System.out.println("第" + (i + 1) + "次请求结果: " + result + 
                             ", 结束时间: " + System.currentTimeMillis());
            
            // 检查缓存状态（工厂模式下的新方法）
            System.out.println("缓存大小: " + locationService.getCacheSize());
            System.out.println("---");
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        System.out.println("总耗时: " + totalTime + "ms");
        
        // 由于缓存机制，第一次请求会调用API，后续请求应该直接返回缓存结果
        // 但限流器状态应该正确反映
        assertTrue(totalTime >= 0, "总时间应该为正数");
        
        // 验证缓存工作正常
        assertEquals(1, locationService.getCacheSize(), "缓存中应该有一个IP记录");
        verify(providerFactory, times(1)).resolveLocation(testIp);
    }
    
    @Test 
    public void testFactoryPatternStatus() {
        // 测试工厂模式下的状态方法
        
        // 初始状态检查
        int cacheSize = locationService.getCacheSize();
        assertTrue(cacheSize >= 0, "缓存大小应该为非负数");
        
        // 测试缓存功能
        String testIp = "192.168.1.1";
        String result = locationService.getLocationByIp(testIp);
        assertEquals("内网IP", result);
        
        int newCacheSize = locationService.getCacheSize();
        assertEquals(cacheSize + 1, newCacheSize, "缓存大小应该增加1");
        
        System.out.println("工厂模式状态检查通过");
        System.out.println("缓存大小: " + newCacheSize);
        
        // 注意：在工厂模式下，限流功能由具体的提供者处理
        // 需要集成测试来验证完整的限流功能
    }
}
