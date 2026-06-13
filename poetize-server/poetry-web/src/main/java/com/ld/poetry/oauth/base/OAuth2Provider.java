package com.ld.poetry.oauth.base;

import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.oauth.exception.OAuthException;
import com.ld.poetry.oauth.exception.TokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OAuth 2.0 提供商抽象基类
 * 提供OAuth 2.0通用实现
 *
 * @author LeapYa
 * @since 2026-01-10
 */
@Slf4j
public abstract class OAuth2Provider implements BaseOAuthProvider {

    @Autowired
    @Qualifier("oauthRestTemplate")
    protected RestTemplate restTemplate;

    @Autowired
    protected JsonMapper objectMapper;

    @Value("${oauth.proxy.domain:}")
    protected String oauthProxyDomain;

    protected ThirdPartyOauthConfig config;

    /**
     * 设置OAuth配置
     */
    public void setConfig(ThirdPartyOauthConfig config) {
        this.config = config;
    }

    /**
     * 获取OAuth配置
     */
    public ThirdPartyOauthConfig getConfig() {
        return config;
    }

    @Override
    public List<String> getRequiredConfigFields() {
        return Arrays.asList("clientId", "clientSecret", "redirectUri");
    }

    @Override
    public boolean validateConfig() {
        if (config == null) {
            return false;
        }
        return StringUtils.hasText(config.getClientId())
                && StringUtils.hasText(config.getClientSecret())
                && StringUtils.hasText(config.getRedirectUri());
    }

    @Override
    public String getAuthUrl(String state) {
        String authUrl = getAuthorizationUrl();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authUrl)
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code");

        // 添加scope
        String scope = getScope();
        if (StringUtils.hasText(scope)) {
            builder.queryParam("scope", scope);
        }

        // 添加平台特定参数
        addAuthUrlParams(builder);

        return builder.build().toUriString();
    }

    @Override
    public Map<String, Object> getAccessToken(String code) {
        String tokenUrl = getTokenUrl();

        try {
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("code", code);
            params.add("client_id", config.getClientId());
            params.add("client_secret", config.getClientSecret());
            params.add("redirect_uri", config.getRedirectUri());

            // 添加平台特定参数
            addTokenRequestParams(params);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.add("Accept", "application/json");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            log.debug("获取访问令牌: provider={}, tokenUrl={}", getProviderName(), tokenUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                Map<String, Object> tokenData = parseTokenResponse(responseBody);

                // 检查错误
                if (tokenData.containsKey("error")) {
                    String error = (String) tokenData.get("error");
                    String errorDescription = (String) tokenData.get("error_description");
                    log.warn("获取访问令牌失败: provider={}, error={}", getProviderName(), error);
                    throw new TokenException("OAuth授权失败: " + (errorDescription != null ? errorDescription : error), getProviderName());
                }

                // 检查access_token
                String accessToken = (String) tokenData.get("access_token");
                if (!StringUtils.hasText(accessToken)) {
                    throw new TokenException("未返回访问令牌", getProviderName());
                }

                return tokenData;
            } else {
                throw new TokenException("获取访问令牌失败: HTTP " + response.getStatusCode(), getProviderName());
            }
        } catch (RestClientException e) {
            log.error("获取访问令牌网络错误: provider={}", getProviderName(), e);
            throw new TokenException("网络请求失败", getProviderName(), e);
        }
    }

    /**
     * 解析Token响应
     * 默认实现支持JSON和URL编码格式
     */
    protected Map<String, Object> parseTokenResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return new HashMap<>();
        }

        // 尝试JSON解析
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonData = objectMapper.readValue(responseBody, Map.class);
            return jsonData;
        } catch (Exception e) {
            // JSON解析失败，尝试URL编码格式
            return parseUrlEncodedResponse(responseBody);
        }
    }

    /**
     * 解析URL编码格式响应
     */
    protected Map<String, Object> parseUrlEncodedResponse(String responseBody) {
        Map<String, Object> result = new HashMap<>();
        try {
            String[] pairs = responseBody.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            log.warn("解析URL编码响应失败", e);
        }
        return result;
    }

    /**
     * 发送带认证头的GET请求
     */
    protected Map<String, Object> sendAuthenticatedGetRequest(String url, String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAuthorizationHeader(accessToken));
            headers.add("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.getBody(), Map.class);
                return data;
            } else {
                throw new OAuthException("请求失败: HTTP " + response.getStatusCode(), "http_error", getProviderName());
            }
        } catch (RestClientException e) {
            throw new OAuthException("网络请求失败", "network_error", getProviderName(), e);
        } catch (Exception e) {
            throw new OAuthException("解析响应失败", "parse_error", getProviderName(), e);
        }
    }

    // ========== 需要子类实现或可覆盖的方法 ==========

    /**
     * 获取授权URL
     */
    protected abstract String getAuthorizationUrl();

    /**
     * 获取Token URL
     */
    protected abstract String getTokenUrl();

    /**
     * 获取用户信息URL
     */
    protected abstract String getUserInfoUrl();

    /**
     * 获取scope
     */
    protected abstract String getScope();

    /**
     * 添加授权URL的额外参数
     */
    protected void addAuthUrlParams(UriComponentsBuilder builder) {
        // 默认不添加额外参数，子类可覆盖
    }

    /**
     * 添加Token请求的额外参数
     */
    protected void addTokenRequestParams(MultiValueMap<String, String> params) {
        // 默认不添加额外参数，子类可覆盖
    }

    /**
     * 获取认证头格式
     */
    protected String getAuthorizationHeader(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * 检查是否使用代理
     */
    protected boolean useProxy() {
        return StringUtils.hasText(oauthProxyDomain) && isOverseasProvider();
    }

    /**
     * 是否为海外平台（需要代理）
     */
    protected boolean isOverseasProvider() {
        Set<String> overseasProviders = new HashSet<>(Arrays.asList("github", "google", "x", "twitter", "yandex"));
        return overseasProviders.contains(getProviderName().toLowerCase());
    }

    /**
     * 获取代理后的URL
     */
    protected String getProxiedUrl(String originalUrl, String proxyPath) {
        if (useProxy()) {
            return oauthProxyDomain + proxyPath;
        }
        return originalUrl;
    }
}
