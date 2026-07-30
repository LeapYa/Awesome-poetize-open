package com.ld.poetry.oauth;

import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.oauth.base.BaseOAuthProvider;
import com.ld.poetry.oauth.base.OAuth1Provider;
import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.ConfigurationException;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * OAuth提供商工厂
 * 自动收集Spring容器中所有Provider Bean构建映射，
 * 新增平台只需编写Provider组件类并在数据库插入配置行，无需修改工厂代码
 *
 * @author LeapYa
 * @since 2026-01-10
 */
@Slf4j
@Component
public class OAuthProviderFactory {

    @Autowired
    private ThirdPartyOauthConfigService configService;

    /**
     * 平台标识 -> Provider实例映射（含x/twitter别名）
     */
    private final Map<String, BaseOAuthProvider> providerMap;

    /**
     * 支持的OAuth提供商列表
     */
    private final List<String> supportedProviders;

    public OAuthProviderFactory(List<BaseOAuthProvider> providers) {
        Map<String, BaseOAuthProvider> map = new HashMap<>();
        for (BaseOAuthProvider provider : providers) {
            map.put(provider.getProviderName().toLowerCase(), provider);
        }
        // x与twitter互为别名：TwitterOAuthProvider的providerName为x，数据库platform_type为twitter
        if (map.containsKey("x") && !map.containsKey("twitter")) {
            map.put("twitter", map.get("x"));
        } else if (map.containsKey("twitter") && !map.containsKey("x")) {
            map.put("x", map.get("twitter"));
        }
        this.providerMap = Collections.unmodifiableMap(map);
        this.supportedProviders = List.copyOf(new TreeSet<>(map.keySet()));
        log.info("OAuth提供商注册完成: {}", this.supportedProviders);
    }

    /**
     * 获取OAuth提供商
     *
     * @param platformType 平台类型
     * @return OAuth提供商实例
     */
    public BaseOAuthProvider getProvider(String platformType) {
        // 获取配置
        ThirdPartyOauthConfig oauthConfig = configService.getByPlatformType(platformType);
        if (oauthConfig == null) {
            throw new ConfigurationException("平台配置不存在: " + platformType, platformType);
        }

        if (!oauthConfig.getEnabled() || !oauthConfig.getGlobalEnabled()) {
            throw new ConfigurationException("平台未启用: " + platformType, platformType);
        }

        // 回调地址未配置时按站点地址自动生成
        if (!StringUtils.hasText(oauthConfig.getRedirectUri())) {
            oauthConfig.setRedirectUri(configService.buildDefaultRedirectUri(oauthConfig.getPlatformType()));
        }

        // 获取对应的Provider并设置配置
        BaseOAuthProvider provider = getProviderInstance(platformType);

        // 设置配置
        if (provider instanceof OAuth2Provider) {
            ((OAuth2Provider) provider).setConfig(oauthConfig);
        } else if (provider instanceof OAuth1Provider) {
            ((OAuth1Provider) provider).setConfig(oauthConfig);
        }

        // 验证配置
        if (!provider.validateConfig()) {
            throw new ConfigurationException("平台配置不完整: " + platformType, platformType);
        }

        return provider;
    }

    /**
     * 获取Provider实例
     * custom 与 custom_* 多实例自定义平台统一路由到通用提供商
     */
    private BaseOAuthProvider getProviderInstance(String platformType) {
        String key = platformType.toLowerCase();
        BaseOAuthProvider provider = providerMap.get(key);
        if (provider == null && key.startsWith("custom")) {
            provider = providerMap.get("custom");
        }
        if (provider == null) {
            throw new ConfigurationException("不支持的OAuth平台: " + platformType, platformType);
        }
        return provider;
    }

    /**
     * 获取支持的提供商列表
     */
    public List<String> getSupportedProviders() {
        return supportedProviders;
    }

    /**
     * 获取已启用的提供商列表（以数据库配置为准，包含动态新增的自定义平台）
     */
    public List<String> getEnabledProviders() {
        return configService.getActiveConfigs().stream()
                .map(ThirdPartyOauthConfig::getPlatformType)
                .filter(this::isProviderSupported)
                .collect(Collectors.toList());
    }

    /**
     * 检查提供商是否启用
     */
    public boolean isProviderEnabled(String platformType) {
        try {
            ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
            return config != null && config.getEnabled() && config.getGlobalEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查提供商是否支持
     */
    public boolean isProviderSupported(String platformType) {
        String key = platformType.toLowerCase();
        return providerMap.containsKey(key)
                || (key.startsWith("custom") && providerMap.containsKey("custom"));
    }
}
