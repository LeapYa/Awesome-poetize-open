package com.ld.poetry.service.provider;

import com.ld.poetry.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * IP地理位置解析提供者工厂
 * 工厂模式实现，根据配置和优先级自动选择合适的提供者
 * 
 * @author LeapYa
 */
@Slf4j
@Component
public class IpLocationProviderFactory {
    
    @Autowired
    private SysConfigService sysConfigService;
    
    @Autowired
    private TencentLbsProvider tencentLbsProvider;
    
    @Autowired
    private TaobaoIpProvider taobaoIpProvider;
    
    @Autowired
    private Ip2RegionProvider ip2RegionProvider;
    
    /**
     * 获取可用的IP解析提供者列表（按优先级排序）
     * @param ipAddress IP地址
     * @return 提供者列表
     */
    public List<IpLocationProvider> getAvailableProviders(String ipAddress) {
        List<IpLocationProvider> providers = new ArrayList<>();
        
        // 检查腾讯位置服务
        String tencentLbsKey = sysConfigService.getConfigValueByKey("tencent.lbs.key");
        if (StringUtils.hasText(tencentLbsKey)) {
            tencentLbsProvider.setApiKey(tencentLbsKey);
            if (tencentLbsProvider.isAvailable() && tencentLbsProvider.supportsIpType(ipAddress)) {
                providers.add(tencentLbsProvider);
            }
        }
        
        // 检查淘宝IP服务
        if (taobaoIpProvider.isAvailable() && taobaoIpProvider.supportsIpType(ipAddress)) {
            providers.add(taobaoIpProvider);
        }
        
        // 检查IP2Region离线库
        if (ip2RegionProvider.isAvailable() && ip2RegionProvider.supportsIpType(ipAddress)) {
            providers.add(ip2RegionProvider);
        }
        
        // 按优先级排序（数字越小优先级越高）
        providers.sort(Comparator.comparingInt(IpLocationProvider::getPriority));
        
        return providers;
    }
    
    /**
     * 获取最佳提供者（优先级最高且可用）
     * @param ipAddress IP地址
     * @return 最佳提供者，如果没有可用提供者则返回null
     */
    public IpLocationProvider getBestProvider(String ipAddress) {
        List<IpLocationProvider> providers = getAvailableProviders(ipAddress);
        
        if (providers.isEmpty()) {
            log.warn("没有找到支持IP地址 {} 的可用提供者", ipAddress);
            return null;
        }
        
        IpLocationProvider bestProvider = providers.get(0);
        return bestProvider;
    }
    
    /**
     * 使用工厂模式解析IP地理位置
     * 自动选择最佳提供者，支持降级策略
     * @param ipAddress IP地址
     * @return 地理位置
     */
    public String resolveLocation(String ipAddress) {
        List<IpLocationProvider> providers = getAvailableProviders(ipAddress);
        
        if (providers.isEmpty()) {
            log.warn("没有可用的IP解析提供者，IP: {}", ipAddress);
            return "未知";
        }
        
        // 按优先级尝试各个提供者
        for (IpLocationProvider provider : providers) {
            try {
                String result = provider.resolveLocation(ipAddress);
                
                if (!"未知".equals(result)) {
                    log.info("IP地址 {} 解析成功，提供者: {}，结果: {}", 
                             ipAddress, provider.getProviderName(), result);
                    return result;
                } else {
                    log.warn("提供者 {} 解析IP {} 失败，尝试下一个提供者", 
                             provider.getProviderName(), ipAddress);
                }
            } catch (Exception e) {
                log.warn("提供者 {} 解析IP {} 时发生异常: {}，尝试下一个提供者", 
                         provider.getProviderName(), ipAddress, e.getMessage());
            }
        }
        
        log.warn("所有提供者都无法解析IP地址: {}", ipAddress);
        return "未知";
    }
    
    /**
     * 获取提供者状态信息（用于调试和监控）
     * @return 状态信息
     */
    public String getProvidersStatus() {
        StringBuilder status = new StringBuilder();
        status.append("IP解析提供者状态:\n");
        
        // 腾讯位置服务
        String tencentLbsKey = sysConfigService.getConfigValueByKey("tencent.lbs.key");
        if (StringUtils.hasText(tencentLbsKey)) {
            tencentLbsProvider.setApiKey(tencentLbsKey);
            status.append("- 腾讯位置服务: ").append(tencentLbsProvider.isAvailable() ? "可用" : "不可用").append("\n");
        } else {
            status.append("- 腾讯位置服务: 未配置API Key\n");
        }
        
        // 淘宝IP服务
        status.append("- 淘宝IP服务: ").append(taobaoIpProvider.isAvailable() ? "可用" : "不可用").append("\n");
        
        // IP2Region离线库
        status.append("- IP2Region离线库: ").append(ip2RegionProvider.isAvailable() ? "可用" : "不可用");
        
        return status.toString();
    }
}