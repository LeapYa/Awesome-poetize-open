package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth1Provider;
import com.ld.poetry.oauth.exception.TokenException;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Twitter/X OAuth 1.0a 提供商
 * Twitter使用OAuth 1.0a协议，需要三步认证流程
 *
 * @author LeapYa
 * @since 2026-01-10
 */
@Slf4j
@Component("twitterOAuthProvider")
public class TwitterOAuthProvider extends OAuth1Provider {

    private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
    private static final String AUTHORIZE_URL = "https://api.twitter.com/oauth/authenticate";
    private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
    private static final String USER_INFO_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";

    @Autowired
    @Qualifier("oauthRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public String getProviderName() {
        return "x";  // 使用x作为provider名称
    }

    @Override
    protected String getRequestTokenUrl() {
        return getProxiedUrl(REQUEST_TOKEN_URL, "/x/api/oauth/request_token");
    }

    @Override
    protected String getAuthorizeUrl() {
        return AUTHORIZE_URL;
    }

    @Override
    protected String getAccessTokenUrl() {
        return getProxiedUrl(ACCESS_TOKEN_URL, "/x/api/oauth/access_token");
    }

    @Override
    protected String getUserInfoUrl() {
        return getProxiedUrl(USER_INFO_URL, "/x/api/1.1/account/verify_credentials.json");
    }

    @Override
    public Map<String, String> getRequestToken(String callbackUrl) {
        try {
            // 1. 生成OAuth参数
            Map<String, String> oauthParams = generateOAuthParams();
            oauthParams.put("oauth_callback", callbackUrl);

            // 2. 生成签名
            String signature = generateSignature(
                    "POST",
                    REQUEST_TOKEN_URL,  // 原始URL用于签名
                    oauthParams,
                    config.getClientSecret(),
                    null
            );
            oauthParams.put("oauth_signature", signature);

            // 3. 构建Authorization头
            String authHeader = buildAuthorizationHeader(oauthParams);

            // 4. 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    getRequestTokenUrl(),
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, String> tokenData = parseOAuthResponse(response.getBody());

                String oauthToken = tokenData.get("oauth_token");
                String oauthTokenSecret = tokenData.get("oauth_token_secret");

                if (oauthToken == null || oauthTokenSecret == null) {
                    throw new TokenException("Twitter未返回请求令牌", "x");
                }

                return tokenData;
            }

            throw new TokenException("获取Twitter请求令牌失败: HTTP " + response.getStatusCode(), "x");

        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取Twitter请求令牌失败", e);
            throw new TokenException("获取请求令牌失败: " + e.getMessage(), "x", e);
        }
    }

    @Override
    public Map<String, String> getAccessTokenWithVerifier(String oauthToken, String oauthTokenSecret, String oauthVerifier) {
        try {
            // 1. 生成OAuth参数
            Map<String, String> oauthParams = generateOAuthParams();
            oauthParams.put("oauth_token", oauthToken);
            oauthParams.put("oauth_verifier", oauthVerifier);

            // 2. 生成签名
            String signature = generateSignature(
                    "POST",
                    ACCESS_TOKEN_URL,  // 原始URL用于签名
                    oauthParams,
                    config.getClientSecret(),
                    oauthTokenSecret
            );
            oauthParams.put("oauth_signature", signature);

            // 3. 构建Authorization头
            String authHeader = buildAuthorizationHeader(oauthParams);

            // 4. 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    getAccessTokenUrl(),
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, String> tokenData = parseOAuthResponse(response.getBody());

                String accessToken = tokenData.get("oauth_token");
                String accessTokenSecret = tokenData.get("oauth_token_secret");

                if (accessToken == null || accessTokenSecret == null) {
                    throw new TokenException("Twitter未返回访问令牌", "x");
                }

                // 转换为标准格式
                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("access_token_secret", accessTokenSecret);
                result.put("user_id", tokenData.get("user_id"));
                result.put("screen_name", tokenData.get("screen_name"));

                return result;
            }

            throw new TokenException("获取Twitter访问令牌失败: HTTP " + response.getStatusCode(), "x");

        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取Twitter访问令牌失败", e);
            throw new TokenException("获取访问令牌失败: " + e.getMessage(), "x", e);
        }
    }

    @Override
    public Map<String, Object> getUserInfoWithSecret(String accessToken, String accessTokenSecret) {
        try {
            // 1. 构建请求URL（包含邮箱）
            String userInfoUrl = USER_INFO_URL + "?include_email=true";

            // 2. 生成OAuth参数
            Map<String, String> oauthParams = generateOAuthParams();
            oauthParams.put("oauth_token", accessToken);

            // 添加查询参数用于签名
            Map<String, String> allParams = new HashMap<>(oauthParams);
            allParams.put("include_email", "true");

            // 3. 生成签名
            String signature = generateSignature(
                    "GET",
                    USER_INFO_URL,  // 原始URL用于签名（不含查询参数）
                    allParams,
                    config.getClientSecret(),
                    accessTokenSecret
            );
            oauthParams.put("oauth_signature", signature);

            // 4. 构建Authorization头
            String authHeader = buildAuthorizationHeader(oauthParams);

            // 5. 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", authHeader);
            headers.add("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    getProxiedUrl(userInfoUrl, "/x/api/1.1/account/verify_credentials.json?include_email=true"),
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userInfo = objectMapper.readValue(response.getBody(), Map.class);

                // 获取邮箱
                String email = (String) userInfo.get("email");
                Object[] emailCheck = checkEmailCollectionNeeded(email);
                String processedEmail = (String) emailCheck[0];
                boolean emailCollectionNeeded = (Boolean) emailCheck[1];

                // 获取头像（使用原图，移除_normal后缀）
                String profileImageUrl = (String) userInfo.get("profile_image_url_https");
                String avatar = profileImageUrl != null 
                        ? profileImageUrl.replace("_normal", "") 
                        : "";

                // 返回标准化用户信息
                Map<String, Object> result = new HashMap<>();
                result.put("provider", "x");
                result.put("uid", userInfo.get("id_str"));
                result.put("username", userInfo.get("screen_name"));
                result.put("email", processedEmail);
                result.put("avatar", avatar);
                result.put("email_collection_needed", emailCollectionNeeded);

                return result;
            }

            throw new UserInfoException("获取Twitter用户信息失败: HTTP " + response.getStatusCode(), "x");

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取Twitter用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "x", e);
        }
    }
}
