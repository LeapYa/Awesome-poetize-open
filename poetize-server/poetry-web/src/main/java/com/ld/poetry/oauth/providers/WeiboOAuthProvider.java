package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

/**
 * 微博 OAuth 2.0 提供商
 * token 交换为标准 POST 表单（复用基类实现），
 * 用户信息接口需要 uid，通过 get_token_info 接口用 access_token 换取
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("weiboOAuthProvider")
public class WeiboOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://api.weibo.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.weibo.com/oauth2/access_token";
    private static final String TOKEN_INFO_URL = "https://api.weibo.com/oauth2/get_token_info";
    private static final String USER_INFO_URL = "https://api.weibo.com/2/users/show.json";

    @Override
    public String getProviderName() {
        return "weibo";
    }

    @Override
    protected String getAuthorizationUrl() {
        return AUTH_URL;
    }

    @Override
    protected String getTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    protected String getUserInfoUrl() {
        return USER_INFO_URL;
    }

    @Override
    protected String getScope() {
        // 微博基础授权无需显式 scope（邮箱接口需单独申请权限，默认不请求）
        return "";
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            // 1. 通过 get_token_info 获取 uid
            String uid = getUid(accessToken);

            // 2. 获取用户信息
            String userInfoUrl = getUserInfoUrl()
                    + "?access_token=" + accessToken
                    + "&uid=" + uid;

            HttpHeaders headers = new HttpHeaders();
            headers.add("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userInfo = objectMapper.readValue(response.getBody(), Map.class);

                // 检查返回状态
                Object errorCode = userInfo.get("error_code");
                if (errorCode != null) {
                    String error = (String) userInfo.get("error");
                    throw new UserInfoException("微博返回错误: " + error, "weibo");
                }

                // 微博登录默认不返回邮箱（邮箱接口需单独申请权限）
                boolean emailCollectionNeeded = true;

                // 返回标准化用户信息
                Map<String, Object> result = new HashMap<>();
                result.put("provider", "weibo");
                result.put("uid", uid);
                result.put("username", userInfo.get("screen_name"));
                result.put("email", "");
                result.put("avatar", getAvatar(userInfo));
                result.put("email_collection_needed", emailCollectionNeeded);

                return result;
            }

            throw new UserInfoException("获取微博用户信息失败: HTTP " + response.getStatusCode(), "weibo");

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取微博用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "weibo", e);
        }
    }

    /**
     * 通过 get_token_info 接口获取授权用户的 uid
     */
    private String getUid(String accessToken) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("access_token", accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_INFO_URL, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.getBody(), Map.class);

                Object uid = data.get("uid");
                if (uid == null || String.valueOf(uid).isEmpty()) {
                    throw new UserInfoException("微博未返回uid", "weibo");
                }

                return String.valueOf(uid);
            }

            throw new UserInfoException("获取微博uid失败", "weibo");

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            throw new UserInfoException("获取uid失败: " + e.getMessage(), "weibo", e);
        }
    }

    /**
     * 获取最佳头像（优先高清）
     */
    private String getAvatar(Map<String, Object> userInfo) {
        String avatar = (String) userInfo.get("avatar_hd");
        if (avatar != null && !avatar.isEmpty()) {
            return avatar;
        }

        avatar = (String) userInfo.get("avatar_large");
        if (avatar != null && !avatar.isEmpty()) {
            return avatar;
        }

        avatar = (String) userInfo.get("profile_image_url");
        if (avatar != null && !avatar.isEmpty()) {
            return avatar;
        }

        return "";
    }
}
