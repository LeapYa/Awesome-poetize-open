package com.ld.poetry.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * Docker网络工具类
 * 用于检查请求是否来自Docker内部网络，增强服务间调用的安全性
 */
@Slf4j
@Component
public class DockerNetworkUtil {

    // 从环境变量获取Docker子网
    @Value("${DOCKER_SUBNET:172.28.147.0/28}")
    private String dockerSubnet;
    
    // 受信任的内部服务名称
    private static final String[] TRUSTED_SERVICES = {"poetize-python", "poetize-java", "poetize-nginx"};
    
    // 存储从环境变量获取的Docker子网
    private static String DOCKER_SUBNET;
    
    @PostConstruct
    public void init() {
        // 初始化时将环境变量的值赋给静态变量
        DOCKER_SUBNET = dockerSubnet;
        log.info("已初始化Docker子网: {}", DOCKER_SUBNET);
    }
    
    /**
     * 检查IP是否在Docker内部网络中
     * @param ip 要检查的IP地址
     * @return 是否在Docker内部网络中
     */
    public static boolean isInDockerNetwork(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // 检查是否为本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("::1")) {
            return true;
        }
        
        // 检查是否在配置的Docker子网范围内
        if (DOCKER_SUBNET != null) {
            return IpUtil.isIpInCidr(ip, DOCKER_SUBNET);
        }
        
        // 如果环境变量未设置，使用默认子网
        return IpUtil.isIpInCidr(ip, "172.28.147.0/28");
    }
    
    /**
     * 检查服务名是否为受信任的内部服务
     * @param serviceName 服务名
     * @return 是否为受信任的内部服务
     */
    public static boolean isTrustedService(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            return false;
        }
        
        for (String service : TRUSTED_SERVICES) {
            if (service.equals(serviceName)) {
                return true;
            }
        }
        
        return false;
    }
}
