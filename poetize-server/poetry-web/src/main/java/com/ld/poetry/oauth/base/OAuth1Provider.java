package com.ld.poetry.oauth.base;

import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.oauth.exception.TokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * OAuth 1.0 提供商抽象基类
 * 用于Twitter等使用OAuth 1.0a协议的平台
 *
 * @author LeapYa
 * @since 2026-01-10
 */
@Slf4j
public abstract class OAuth1Provider implements BaseOAuthProvider {

    @Autowired
    protected JsonMapper objectMapper;

    @Value("${oauth.proxy.domain:}")
    protected String oauthProxyDomain;

    protected ThirdPartyOauthConfig config;

    private static final String OAUTH_SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String OAUTH_VERSION = "1.0";
    private static final SecureRandom RANDOM = new SecureRandom();

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
        return Arrays.asList("clientKey", "clientSecret", "redirectUri");
    }

    @Override
    public boolean validateConfig() {
        if (config == null) {
            return false;
        }
        return StringUtils.hasText(config.getClientKey())
                && StringUtils.hasText(config.getClientSecret())
                && StringUtils.hasText(config.getRedirectUri());
    }

    /**
     * OAuth 1.0 需要先获取Request Token
     * 此方法返回的是需要重定向到的授权URL
     */
    @Override
    public String getAuthUrl(String state) {
        // OAuth 1.0需要先获取request token
        // 实际实现在具体Provider中
        throw new UnsupportedOperationException("OAuth 1.0需要先获取Request Token，请使用getRequestToken方法");
    }

    /**
     * 获取Request Token（OAuth 1.0 第一步）
     * @return 包含oauth_token和oauth_token_secret的Map
     */
    public abstract Map<String, String> getRequestToken(String callbackUrl);

    /**
     * 获取Access Token（OAuth 1.0 第三步）
     * @param oauthToken request阶段获得的token
     * @param oauthTokenSecret request阶段获得的token secret
     * @param oauthVerifier 用户授权后返回的verifier
     * @return 包含access_token和access_token_secret的Map
     */
    public abstract Map<String, String> getAccessTokenWithVerifier(String oauthToken, String oauthTokenSecret, String oauthVerifier);

    /**
     * 获取用户信息（OAuth 1.0 需要双Token签名）
     * @param accessToken 访问令牌
     * @param accessTokenSecret 访问令牌密钥
     * @return 标准化的用户信息
     */
    public abstract Map<String, Object> getUserInfoWithSecret(String accessToken, String accessTokenSecret);

    @Override
    public Map<String, Object> getAccessToken(String code) {
        // OAuth 1.0不使用code，而是使用verifier
        throw new TokenException("OAuth 1.0请使用getAccessTokenWithVerifier方法", getProviderName());
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        // OAuth 1.0需要access_token_secret
        throw new TokenException("OAuth 1.0请使用getUserInfoWithSecret方法", getProviderName());
    }

    // ========== OAuth 1.0 签名相关方法 ==========

    /**
     * 生成OAuth签名
     */
    protected String generateSignature(String method, String url, Map<String, String> params,
                                        String consumerSecret, String tokenSecret) {
        try {
            // 1. 构建签名基字符串
            String paramString = buildParamString(params);
            String signatureBase = method.toUpperCase() + "&"
                    + urlEncode(url) + "&"
                    + urlEncode(paramString);

            // 2. 构建签名密钥
            String signingKey = urlEncode(consumerSecret) + "&" + (tokenSecret != null ? urlEncode(tokenSecret) : "");

            // 3. HMAC-SHA1签名
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));

            // 4. Base64编码
            return Base64.getEncoder().encodeToString(signatureBytes);

        } catch (Exception e) {
            log.error("生成OAuth签名失败", e);
            throw new TokenException("生成签名失败", getProviderName(), e);
        }
    }

    /**
     * 构建OAuth请求头
     */
    protected String buildAuthorizationHeader(Map<String, String> oauthParams) {
        StringBuilder header = new StringBuilder("OAuth ");
        boolean first = true;

        // 对参数按key排序
        List<String> sortedKeys = new ArrayList<>(oauthParams.keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            if (!first) {
                header.append(", ");
            }
            header.append(urlEncode(key))
                  .append("=\"")
                  .append(urlEncode(oauthParams.get(key)))
                  .append("\"");
            first = false;
        }

        return header.toString();
    }

    /**
     * 生成OAuth参数
     */
    protected Map<String, String> generateOAuthParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("oauth_consumer_key", config.getClientKey());
        params.put("oauth_nonce", generateNonce());
        params.put("oauth_signature_method", OAUTH_SIGNATURE_METHOD);
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_version", OAUTH_VERSION);
        return params;
    }

    /**
     * 生成随机nonce
     */
    protected String generateNonce() {
        byte[] nonceBytes = new byte[16];
        RANDOM.nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }

    /**
     * URL编码
     */
    protected String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 构建参数字符串（用于签名）
     */
    private String buildParamString(Map<String, String> params) {
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(urlEncode(key)).append("=").append(urlEncode(params.get(key)));
        }
        return sb.toString();
    }

    /**
     * 解析OAuth响应
     */
    protected Map<String, String> parseOAuthResponse(String response) {
        Map<String, String> result = new HashMap<>();
        if (response != null) {
            String[] pairs = response.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    result.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return result;
    }

    /**
     * 检查是否使用代理
     */
    protected boolean useProxy() {
        return StringUtils.hasText(oauthProxyDomain);
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

    // ========== 需要子类实现的方法 ==========

    /**
     * 获取Request Token URL
     */
    protected abstract String getRequestTokenUrl();

    /**
     * 获取Access Token URL
     */
    protected abstract String getAccessTokenUrl();

    /**
     * 获取授权URL
     */
    protected abstract String getAuthorizeUrl();

    /**
     * 获取用户信息URL
     */
    protected abstract String getUserInfoUrl();
}
