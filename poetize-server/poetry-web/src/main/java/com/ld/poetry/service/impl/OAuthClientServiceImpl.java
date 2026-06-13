package com.ld.poetry.service.impl;

import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.service.OAuthClientService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth客户端服务实现
 */
@Slf4j
@Service
public class OAuthClientServiceImpl implements OAuthClientService {

    @Autowired
    private ThirdPartyOauthConfigService configService;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    @Qualifier("oauthRestTemplate")
    private RestTemplate restTemplate;

    // OAuth代理域名配置
    @Value("${oauth.proxy.domain:}")
    private String oauthProxyDomain;

    @Override
    public String buildAuthUrl(String platformType, String state) {
        try {
            ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
            if (config == null || !config.getEnabled() || !config.getGlobalEnabled()) {
                throw new RuntimeException("平台未配置或未启用: " + platformType);
            }

            String authUrl = getAuthUrl(platformType);
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authUrl);

            // 添加通用参数
            if ("twitter".equals(platformType)) {
                builder.queryParam("client_id", config.getClientKey());
            } else {
                builder.queryParam("client_id", config.getClientId());
            }

            builder.queryParam("redirect_uri", config.getRedirectUri())
                    .queryParam("state", state)
                    .queryParam("response_type", "code");

            // 添加平台特定参数
            addPlatformSpecificParams(platformType, builder);

            String finalUrl = builder.build().toUriString();
            return finalUrl;
        } catch (Exception e) {
            log.error("构建授权URL失败: platform={}", platformType, e);
            throw new RuntimeException("构建授权URL失败", e);
        }
    }

    @Override
    public Map<String, Object> getAccessToken(String platformType, String code) {
        String tokenUrl = null;
        long startTime = System.currentTimeMillis();

        // 重试配置
        int maxRetries = 2;
        int retryDelay = 1000; // 1秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {

                ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
                if (config == null) {
                    throw new RuntimeException("平台配置不存在: " + platformType);
                }

                tokenUrl = getTokenUrl(platformType);

                // 构建请求参数
                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("grant_type", "authorization_code");
                params.add("code", code);
                params.add("redirect_uri", config.getRedirectUri());

                if ("twitter".equals(platformType)) {
                    params.add("client_id", config.getClientKey());
                } else {
                    params.add("client_id", config.getClientId());
                }
                params.add("client_secret", config.getClientSecret());

                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.add("Accept", "application/json");

                HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

                // 发送请求

                ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();

                    Map<String, Object> tokenData;

                    // 尝试解析JSON格式
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> jsonData = objectMapper.readValue(responseBody, Map.class);
                        tokenData = jsonData;
                    } catch (Exception jsonException) {
                        // 尝试解析URL编码格式
                        tokenData = parseUrlEncodedResponse(responseBody);
                    }

                    // 检查响应中是否包含错误信息
                    if (tokenData.containsKey("error")) {
                        String error = (String) tokenData.get("error");
                        String errorDescription = (String) tokenData.get("error_description");

                        log.warn("访问令牌请求失败: platform={}, error={}", platformType, error);

                        // 根据错误类型提供更友好的错误信息
                        String userFriendlyMessage = getOAuthErrorMessage(error, errorDescription);
                        throw new RuntimeException(userFriendlyMessage);
                    }

                    // 检查是否包含access_token
                    String accessToken = (String) tokenData.get("access_token");
                    if (accessToken == null || accessToken.trim().isEmpty()) {
                        log.error("访问令牌响应格式错误: platform={}", platformType);
                        throw new RuntimeException("服务器响应格式错误，缺少访问令牌");
                    }

                    return tokenData;
                } else {
                    log.warn("访问令牌请求失败: platform={}, statusCode={}, attempt={}/{}",
                            platformType, response.getStatusCode(), attempt, maxRetries);
                    throw new RuntimeException("获取访问令牌失败: HTTP " + response.getStatusCode());
                }
            } catch (ResourceAccessException e) {
                // 网络连接异常（超时、连接被拒绝等）
                log.warn("网络连接失败: platform={}, attempt={}/{}", platformType, attempt, maxRetries);

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                    continue; // 重试
                } else {
                    throw new RuntimeException("网络连接失败，已重试" + maxRetries + "次: " + e.getMessage(), e);
                }
            } catch (RestClientException e) {
                // 其他REST客户端异常
                log.error("请求客户端错误: platform={}, attempt={}/{}", platformType, attempt, maxRetries);
                throw new RuntimeException("请求失败: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("获取访问令牌失败: platform={}, attempt={}/{}", platformType, attempt, maxRetries, e);
                throw new RuntimeException("获取访问令牌失败: " + e.getMessage(), e);
            }
        }

        // 如果所有重试都失败了（理论上不会到达这里）
        throw new RuntimeException("获取访问令牌失败: 已重试" + maxRetries + "次");
    }

    /**
     * 解析URL编码格式的响应（如GitHub OAuth返回的格式）
     */
    private Map<String, Object> parseUrlEncodedResponse(String responseBody) {
        Map<String, Object> result = new HashMap<>();

        if (responseBody == null || responseBody.trim().isEmpty()) {
            return result;
        }

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
        }

        return result;
    }

    /**
     * 根据OAuth错误类型返回用户友好的错误信息
     */
    private String getOAuthErrorMessage(String error, String errorDescription) {
        if (error == null) {
            return "OAuth授权失败，请重新尝试";
        }

        switch (error) {
            case "bad_verification_code":
                // 授权码过期是最常见的问题，提供更详细的说明
                return "授权码已过期或无效。OAuth授权码通常在10分钟内有效，请重新进行授权";
            case "incorrect_client_credentials":
                return "OAuth客户端配置错误，请联系管理员";
            case "redirect_uri_mismatch":
                return "回调地址配置错误，请联系管理员";
            case "access_denied":
                return "用户拒绝了授权请求";
            case "unsupported_grant_type":
                return "不支持的授权类型";
            case "invalid_scope":
                return "请求的权限范围无效";
            case "server_error":
                return "OAuth服务器内部错误，请稍后重试";
            case "temporarily_unavailable":
                return "OAuth服务暂时不可用，请稍后重试";
            case "invalid_grant":
                return "授权码无效或已被使用，请重新进行授权";
            default:
                // 如果有详细描述，使用描述；否则使用通用错误信息
                if (errorDescription != null && !errorDescription.trim().isEmpty()) {
                    return "OAuth授权失败：" + errorDescription;
                } else {
                    return "OAuth授权失败：" + error;
                }
        }
    }

    @Override
    public Map<String, Object> getUserInfo(String platformType, String accessToken) {
        String userInfoUrl = null;
        try {
            userInfoUrl = getUserInfoUrl(platformType);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + accessToken);
            headers.add("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawUserInfo = objectMapper.readValue(response.getBody(), Map.class);

                // 标准化用户信息
                Map<String, Object> userInfo = normalizeUserInfo(platformType, rawUserInfo);
                return userInfo;
            } else {
                log.warn("用户信息请求失败: platform={}, statusCode={}", platformType, response.getStatusCode());
                throw new RuntimeException("获取用户信息失败: HTTP " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            // 网络连接异常（超时、连接被拒绝等）
            log.warn("用户信息请求网络连接失败: platform={}", platformType);
            throw new RuntimeException("网络连接失败，请检查网络设置或稍后重试: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // 其他REST客户端异常
            log.error("用户信息请求客户端错误: platform={}", platformType, e);
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("获取用户信息失败: platform={}", platformType, e);
            throw new RuntimeException("获取用户信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isPlatformConfigured(String platformType) {
        try {
            ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
            if (config == null || !config.getEnabled() || !config.getGlobalEnabled()) {
                return false;
            }

            // 检查必要的配置项
            if ("twitter".equals(platformType)) {
                return StringUtils.hasText(config.getClientKey()) &&
                        StringUtils.hasText(config.getClientSecret()) &&
                        StringUtils.hasText(config.getRedirectUri());
            } else {
                return StringUtils.hasText(config.getClientId()) &&
                        StringUtils.hasText(config.getClientSecret()) &&
                        StringUtils.hasText(config.getRedirectUri());
            }
        } catch (Exception e) {
            return false;
        }
    }

    // 私有辅助方法
    private String getAuthUrl(String platformType) {
        switch (platformType.toLowerCase()) {
            case "github":
                return "https://github.com/login/oauth/authorize";
            case "google":
                return "https://accounts.google.com/o/oauth2/v2/auth";
            case "twitter":
            case "x":
                return "https://twitter.com/i/oauth2/authorize";
            case "yandex":
                return "https://oauth.yandex.com/authorize";
            case "gitee":
                return "https://gitee.com/oauth/authorize";
            case "qq":
                return "https://graph.qq.com/oauth2.0/authorize";
            case "baidu":
                return "https://openapi.baidu.com/oauth/2.0/authorize";
            case "afdian":
                return "https://afdian.com/oauth2/authorize";
            default:
                throw new RuntimeException("不支持的平台: " + platformType);
        }
    }

    private String getTokenUrl(String platformType) {
        if (StringUtils.hasText(oauthProxyDomain)) {
            switch (platformType.toLowerCase()) {
                case "github":
                    return oauthProxyDomain + "/github/login/oauth/access_token";
                case "google":
                    return oauthProxyDomain + "/google/oauth2/token";
                case "twitter":
                case "x":
                    return oauthProxyDomain + "/x/api/2/oauth2/token";
                case "yandex":
                    return oauthProxyDomain + "/yandex/token";
                default:
                    break;
            }
        }

        switch (platformType.toLowerCase()) {
            case "github":
                return "https://github.com/login/oauth/access_token";
            case "google":
                return "https://oauth2.googleapis.com/token";
            case "twitter":
            case "x":
                return "https://api.twitter.com/2/oauth2/token";
            case "yandex":
                return "https://oauth.yandex.com/token";
            case "gitee":
                return "https://gitee.com/oauth/token";
            case "qq":
                return "https://graph.qq.com/oauth2.0/token";
            case "baidu":
                return "https://openapi.baidu.com/oauth/2.0/token";
            case "afdian":
                return "https://afdian.com/api/oauth2/access_token";
            default:
                throw new RuntimeException("不支持的平台: " + platformType);
        }
    }

    private String getUserInfoUrl(String platformType) {
        if (StringUtils.hasText(oauthProxyDomain)) {
            switch (platformType.toLowerCase()) {
                case "github":
                    return oauthProxyDomain + "/github/api/user";
                case "google":
                    return oauthProxyDomain + "/google/oauth2/v2/userinfo";
                case "twitter":
                case "x":
                    return oauthProxyDomain + "/x/api/2/users/me";
                case "yandex":
                    return oauthProxyDomain + "/yandex/login/info";
                default:
                    break;
            }
        }

        switch (platformType.toLowerCase()) {
            case "github":
                return "https://api.github.com/user";
            case "google":
                return "https://www.googleapis.com/oauth2/v2/userinfo";
            case "twitter":
            case "x":
                return "https://api.twitter.com/2/users/me";
            case "yandex":
                return "https://login.yandex.ru/info";
            case "gitee":
                return "https://gitee.com/api/v5/user";
            case "qq":
                return "https://graph.qq.com/user/get_user_info";
            case "baidu":
                return "https://openapi.baidu.com/rest/2.0/passport/users/getInfo";
            case "afdian":
                return null; // 爱发电没有独立的 userInfo 接口
            default:
                throw new RuntimeException("不支持的平台: " + platformType);
        }
    }

    private void addPlatformSpecificParams(String platformType, UriComponentsBuilder builder) {
        switch (platformType.toLowerCase()) {
            case "github":
                builder.queryParam("scope", "user:email");
                break;
            case "google":
                builder.queryParam("scope", "openid email profile");
                builder.queryParam("access_type", "offline");
                break;
            case "twitter":
            case "x":
                builder.queryParam("scope", "tweet.read users.read");
                builder.queryParam("code_challenge", "challenge");
                builder.queryParam("code_challenge_method", "plain");
                break;
            case "yandex":
                builder.queryParam("scope", "login:email login:info");
                break;
            case "gitee":
                builder.queryParam("scope", "user_info emails");
                break;
            case "qq":
                builder.queryParam("scope", "get_user_info");
                break;
            case "baidu":
                builder.queryParam("scope", "basic");
                break;
        }
    }

    private Map<String, Object> normalizeUserInfo(String platformType, Map<String, Object> rawUserInfo) {
        Map<String, Object> userInfo = new HashMap<>();

        switch (platformType.toLowerCase()) {
            case "github":
                userInfo.put("uid", String.valueOf(rawUserInfo.get("id")));
                userInfo.put("username", rawUserInfo.get("login"));
                userInfo.put("email", rawUserInfo.get("email"));
                userInfo.put("avatar", rawUserInfo.get("avatar_url"));
                break;
            case "google":
                userInfo.put("uid", rawUserInfo.get("id"));
                userInfo.put("username", rawUserInfo.get("name"));
                userInfo.put("email", rawUserInfo.get("email"));
                userInfo.put("avatar", rawUserInfo.get("picture"));
                break;
            case "twitter":
            case "x":
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) rawUserInfo.get("data");
                if (data != null) {
                    userInfo.put("uid", data.get("id"));
                    userInfo.put("username", data.get("username"));
                    userInfo.put("email", null); // Twitter API v2 不直接提供邮箱
                    userInfo.put("avatar", data.get("profile_image_url"));
                }
                break;
            case "yandex":
                userInfo.put("uid", rawUserInfo.get("id"));
                userInfo.put("username", rawUserInfo.get("login"));
                userInfo.put("email", rawUserInfo.get("default_email"));
                userInfo.put("avatar", rawUserInfo.get("default_avatar_id"));
                break;
            case "gitee":
                userInfo.put("uid", String.valueOf(rawUserInfo.get("id")));
                userInfo.put("username", rawUserInfo.get("login"));
                userInfo.put("email", rawUserInfo.get("email"));
                userInfo.put("avatar", rawUserInfo.get("avatar_url"));
                break;
            case "qq":
                userInfo.put("uid", rawUserInfo.get("openid"));
                userInfo.put("username", rawUserInfo.get("nickname"));
                userInfo.put("email", null); // QQ API不直接提供邮箱
                userInfo.put("avatar", rawUserInfo.get("figureurl_qq_2")); // 使用高清头像
                break;
            case "baidu":
                userInfo.put("uid", rawUserInfo.get("uid"));
                userInfo.put("username", rawUserInfo.get("uname"));
                userInfo.put("email", rawUserInfo.get(null)); // Baidu API不直接提供邮箱
                userInfo.put("avatar", rawUserInfo.get("portrait"));
                break;
            default:
                // 默认处理
                userInfo.putAll(rawUserInfo);
        }

        return userInfo;
    }
}
