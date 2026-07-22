package com.ld.poetry.service;

import com.ld.poetry.service.provider.Ip2RegionProvider;
import com.ld.poetry.service.provider.IpLocationProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP地理位置解析服务
 * 使用工厂模式管理多个IP解析提供者，支持自动降级和优先级选择
 * <p>
 * 在线IP服务（腾讯LBS/淘宝）仅用于评论区IP解析（getLocationByIp），
 * 其他统计场景应使用 getLocationByIpOffline 仅走IP2Region离线库。
 */
@Slf4j
@Service
public class LocationService {

    @Autowired
    private IpLocationProviderFactory providerFactory;

    @Autowired
    private Ip2RegionProvider ip2RegionProvider;
    
    // IP地理位置缓存，避免重复查询
    private final ConcurrentHashMap<String, String> locationCache = new ConcurrentHashMap<>();

    /**
     * 根据IP地址获取地理位置（在线优先，用于评论区）
     * <p>
     * 按优先级依次尝试：腾讯LBS → 淘宝IP → IP2Region离线库
     * @param ipAddress IP地址
     * @return 地理位置字符串
     */
    public String getLocationByIp(String ipAddress) {
        if (!StringUtils.hasText(ipAddress) || "unknown".equals(ipAddress)) {
            return "未知";
        }

        // 检查缓存
        String cachedLocation = locationCache.get(ipAddress);
        if (cachedLocation != null) {
            return cachedLocation;
        }

        // 检查是否为内网IP
        if (isInternalIp(ipAddress)) {
            String location = "内网IP";
            locationCache.put(ipAddress, location);
            return location;
        }

        // 使用工厂模式解析公网IP地理位置
        String location = providerFactory.resolveLocation(ipAddress);
        
        // 缓存结果
        locationCache.put(ipAddress, location);
        
        return location;
    }

    /**
     * 仅使用IP2Region离线库解析地理位置（用于审计日志等统计场景）
     * <p>
     * 不调用在线IP服务，避免外部API依赖和延迟。
     * @param ipAddress IP地址
     * @return 地理位置字符串
     */
    public String getLocationByIpOffline(String ipAddress) {
        if (!StringUtils.hasText(ipAddress) || "unknown".equals(ipAddress)) {
            return "未知";
        }

        // 检查缓存
        String cachedLocation = locationCache.get(ipAddress);
        if (cachedLocation != null) {
            return cachedLocation;
        }

        // 检查是否为内网IP
        if (isInternalIp(ipAddress)) {
            String location = "内网IP";
            locationCache.put(ipAddress, location);
            return location;
        }

        // 仅使用IP2Region离线库
        String location = "未知";
        if (ip2RegionProvider != null && ip2RegionProvider.isAvailable()) {
            try {
                String result = ip2RegionProvider.resolveLocation(ipAddress);
                if (StringUtils.hasText(result)) {
                    location = result;
                }
            } catch (Exception e) {
                log.warn("[getLocationByIpOffline] IP2Region解析失败: {}, 错误: {}", ipAddress, e.getMessage());
            }
        }

        // 缓存结果
        locationCache.put(ipAddress, location);

        return location;
    }

    /**
     * 清理位置缓存（可用于定期清理）
     */
    public void clearLocationCache() {
        locationCache.clear();
        log.info("IP地理位置缓存已清理");
    }

    /**
     * 获取缓存统计信息
     * @return 缓存大小
     */
    public int getCacheSize() {
        return locationCache.size();
    }
    
    /**
     * 获取提供者状态信息（用于调试和监控）
     * @return 提供者状态
     */
    public String getProvidersStatus() {
        return providerFactory.getProvidersStatus();
    }
    
    /**
     * 测试指定IP的解析结果（用于调试）
     * @param ipAddress IP地址
     * @return 详细测试信息
     */
    public String testIpResolution(String ipAddress) {
        StringBuilder result = new StringBuilder();
        result.append("IP解析测试结果 - ").append(ipAddress).append(":\n");
        
        boolean isIPv6 = isIPv6Address(ipAddress);
        boolean isInternal = isInternalIp(ipAddress);
        result.append("IP类型: ").append(isIPv6 ? "IPv6" : "IPv4").append("\n");
        result.append("是否内网: ").append(isInternal ? "是" : "否").append("\n");
        
        if (!isInternal) {
            result.append("提供者状态:\n").append(getProvidersStatus()).append("\n");
            var providers = providerFactory.getAvailableProviders(ipAddress);
            result.append("可用提供者: ").append(providers.size()).append("个\n");
        }
        
        String finalResult = getLocationByIp(ipAddress);
        result.append("最终结果: ").append(finalResult);
        
        return result.toString();
    }
    
    /**
     * 判断是否为IPv6地址
     * @param ip IP地址
     * @return 是否为IPv6
     */
    private boolean isIPv6Address(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            return inetAddress instanceof Inet6Address;
        } catch (Exception e) {
            return ip.contains(":") && !ip.contains(".");
        }
    }
    
    /**
     * 判断是否为内网IP（支持IPv4和IPv6）
     * @param ip IP地址
     * @return 是否为内网IP
     */
    private boolean isInternalIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return true;
        }

        ip = ip.trim();
        
        // IPv4本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("0.0.0.0")) {
            return true;
        }
        
        // IPv6本地回环地址
        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1") || 
            ip.equalsIgnoreCase("localhost")) {
            return true;
        }
        
        // 其他明确无效的IP
        if (ip.equals("unknown") || ip.equals("-") || ip.equals("null") || 
            ip.equals("undefined")) {
            return true;
        }
        
        // 使用Java内置方法检查是否为内网IP
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            
            // 检查是否为内网地址
            if (inetAddress.isSiteLocalAddress() || 
                inetAddress.isLinkLocalAddress() || 
                inetAddress.isLoopbackAddress()) {
                return true;
            }
            
            // IPv6特殊处理
            if (inetAddress instanceof Inet6Address) {
                return isInternalIPv6(ip);
            }
            
            // IPv4私有地址段检查
            if (ip.startsWith("192.168.") || 
                ip.startsWith("10.") || 
                (ip.startsWith("172.") && isInRange172(ip))) {
                return true;
            }
            
        } catch (Exception e) {
            return true; // 无法解析的IP认为是无效的
        }
        
        return false;
    }
    
    /**
     * 检查是否为内网IPv6地址
     * @param ipv6 IPv6地址
     * @return 是否为内网IPv6
     */
    private boolean isInternalIPv6(String ipv6) {
        if (!StringUtils.hasText(ipv6)) {
            return true;
        }
        
        ipv6 = ipv6.toLowerCase().trim();
        
        // IPv6本地回环
        if (ipv6.equals("::1")) {
            return true;
        }
        
        // IPv6链路本地地址 (fe80::/10)
        if (ipv6.startsWith("fe80:")) {
            return true;
        }
        
        // IPv6唯一本地地址 (fc00::/7)
        if (ipv6.startsWith("fc") || ipv6.startsWith("fd")) {
            return true;
        }
        
        // IPv6多播地址 (ff00::/8)
        if (ipv6.startsWith("ff")) {
            return true;
        }
        
        // IPv4映射的IPv6地址
        if (ipv6.contains("::ffff:")) {
            return true;
        }
        
        return false;
    }

    /**
     * 检查是否在172.16.0.0-172.31.255.255范围内
     * @param ip IP地址
     * @return 是否在范围内
     */
    private boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return false;
    }
}
