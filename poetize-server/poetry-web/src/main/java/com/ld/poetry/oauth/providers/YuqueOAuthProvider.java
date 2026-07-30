package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 语雀 OAuth 2.0 提供商
 * token 交换为标准流程；用户信息接口要求使用 X-Auth-Token 请求头而非 Bearer
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("yuqueOAuthProvider")
public class YuqueOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://www.yuque.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://www.yuque.com/oauth2/token";
    private static final String USER_INFO_URL = "https://www.yuque.com/api/v2/user";

    @Override
    public String getProviderName() {
        return "yuque";
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
        // 仅读取用户基本信息即可完成登录
        return "";
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Auth-Token", accessToken);
            headers.add("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    getUserInfoUrl(), HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);

                // 语雀响应为 { data: { id, login, name, avatar_url } }
                Object dataObj = body.get("data");
                if (!(dataObj instanceof Map)) {
                    throw new UserInfoException("语雀未返回用户信息", "yuque");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) dataObj;

                Object id = data.get("id");
                if (id == null) {
                    throw new UserInfoException("语雀未返回用户ID", "yuque");
                }

                // 优先展示名，回退登录名
                Object name = data.get("name");
                Object login = data.get("login");
                String displayName = name != null && !String.valueOf(name).isEmpty()
                        ? String.valueOf(name) : String.valueOf(login);

                Object avatar = data.get("avatar_url");

                // 语雀不提供邮箱
                Map<String, Object> result = new HashMap<>();
                result.put("provider", "yuque");
                result.put("uid", String.valueOf(id));
                result.put("username", displayName);
                result.put("email", "");
                result.put("avatar", avatar != null ? String.valueOf(avatar) : "");
                result.put("email_collection_needed", true);

                return result;
            }

            throw new UserInfoException("获取语雀用户信息失败: HTTP " + response.getStatusCode(), "yuque");

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取语雀用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "yuque", e);
        }
    }
}
