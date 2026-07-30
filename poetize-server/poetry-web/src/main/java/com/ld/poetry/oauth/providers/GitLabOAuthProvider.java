package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * GitLab OAuth 2.0 提供商（gitlab.com）
 * 标准 OAuth2 流程
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("gitLabOAuthProvider")
public class GitLabOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://gitlab.com/oauth/authorize";
    private static final String TOKEN_URL = "https://gitlab.com/oauth/token";
    private static final String USER_INFO_URL = "https://gitlab.com/api/v4/user";

    @Override
    public String getProviderName() {
        return "gitlab";
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
        return "read_user";
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            Map<String, Object> userInfo = sendAuthenticatedGetRequest(getUserInfoUrl(), accessToken);

            Object id = userInfo.get("id");
            if (id == null) {
                throw new UserInfoException("GitLab未返回用户ID", "gitlab");
            }

            // 优先展示名，回退登录名
            Object name = userInfo.get("name");
            Object username = userInfo.get("username");
            String displayName = name != null && !String.valueOf(name).isEmpty()
                    ? String.valueOf(name) : String.valueOf(username);

            Object avatar = userInfo.get("avatar_url");

            // read_user 权限下可能返回公开邮箱
            Object email = userInfo.get("email");
            String emailStr = email != null ? String.valueOf(email) : "";
            Object[] emailCheck = checkEmailCollectionNeeded(emailStr);

            Map<String, Object> result = new HashMap<>();
            result.put("provider", "gitlab");
            result.put("uid", String.valueOf(id));
            result.put("username", displayName);
            result.put("email", emailCheck[0]);
            result.put("avatar", avatar != null ? String.valueOf(avatar) : "");
            result.put("email_collection_needed", emailCheck[1]);

            return result;

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取GitLab用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "gitlab", e);
        }
    }
}
