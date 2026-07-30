package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LinuxDo Connect OAuth 2.0 提供商
 * 标准 OAuth2 流程，token 交换与用户信息接口均为标准实现
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("linuxDoOAuthProvider")
public class LinuxDoOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://connect.linux.do/oauth2/authorize";
    private static final String TOKEN_URL = "https://connect.linux.do/oauth2/token";
    private static final String USER_INFO_URL = "https://connect.linux.do/api/user";

    @Override
    public String getProviderName() {
        return "linuxdo";
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
        // LinuxDo Connect 默认即返回用户基本信息，无需显式 scope
        return "";
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            Map<String, Object> userInfo = sendAuthenticatedGetRequest(getUserInfoUrl(), accessToken);

            Object id = userInfo.get("id");
            if (id == null) {
                throw new UserInfoException("LinuxDo未返回用户ID", "linuxdo");
            }

            // 优先展示名，回退登录名
            Object name = userInfo.get("name");
            Object username = userInfo.get("username");
            String displayName = name != null && !String.valueOf(name).isEmpty()
                    ? String.valueOf(name) : String.valueOf(username);

            Object avatar = userInfo.get("avatar_url");

            // LinuxDo Connect 不提供邮箱
            Map<String, Object> result = new HashMap<>();
            result.put("provider", "linuxdo");
            result.put("uid", String.valueOf(id));
            result.put("username", displayName);
            result.put("email", "");
            result.put("avatar", avatar != null ? String.valueOf(avatar) : "");
            result.put("email_collection_needed", true);

            return result;

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取LinuxDo用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "linuxdo", e);
        }
    }
}
