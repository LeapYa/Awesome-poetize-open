package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 OAuth2/OIDC 通用提供商
 * <p>端点与用户字段映射全部来自数据库配置，可对接任意标准 OAuth2/OIDC 授权服务
 * （如 Keycloak、Casdoor、Logto、Authelia 等自建 SSO）。
 * <p>字段映射支持点号路径取嵌套值（如 data.id）；映射留空时按 OIDC 标准声明解析：
 * uid=sub、username=name、avatar=picture、email=email。
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("genericOAuthProvider")
public class GenericOAuthProvider extends OAuth2Provider {

    @Override
    public String getProviderName() {
        return "custom";
    }

    @Override
    protected String getAuthorizationUrl() {
        return config.getAuthorizeUrl();
    }

    @Override
    protected String getTokenUrl() {
        return config.getTokenUrl();
    }

    @Override
    protected String getUserInfoUrl() {
        return config.getUserInfoUrl();
    }

    @Override
    protected String getScope() {
        return config.getScope() != null ? config.getScope() : "";
    }

    @Override
    public boolean validateConfig() {
        // 在基础校验之上，自定义平台还必须配置三个端点
        return super.validateConfig()
                && StringUtils.hasText(config.getAuthorizeUrl())
                && StringUtils.hasText(config.getTokenUrl())
                && StringUtils.hasText(config.getUserInfoUrl());
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            Map<String, Object> userInfo = sendAuthenticatedGetRequest(getUserInfoUrl(), accessToken);

            // 字段映射留空时按 OIDC 标准声明解析
            String uidField = defaultIfBlank(config.getUidField(), "sub");
            String usernameField = defaultIfBlank(config.getUsernameField(), "name");
            String avatarField = defaultIfBlank(config.getAvatarField(), "picture");
            String emailField = defaultIfBlank(config.getEmailField(), "email");

            Object uid = extractByPath(userInfo, uidField);
            if (uid == null || String.valueOf(uid).isEmpty()) {
                throw new UserInfoException("自定义平台未返回用户标识（字段: " + uidField + "）", "custom");
            }

            Object username = extractByPath(userInfo, usernameField);
            Object avatar = extractByPath(userInfo, avatarField);
            Object email = extractByPath(userInfo, emailField);

            String emailStr = email != null ? String.valueOf(email) : "";
            Object[] emailCheck = checkEmailCollectionNeeded(emailStr);

            Map<String, Object> result = new HashMap<>();
            result.put("provider", "custom");
            result.put("uid", String.valueOf(uid));
            result.put("username", username != null ? String.valueOf(username) : "");
            result.put("email", emailCheck[0]);
            result.put("avatar", avatar != null ? String.valueOf(avatar) : "");
            result.put("email_collection_needed", emailCheck[1]);

            return result;

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取自定义平台用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "custom", e);
        }
    }

    /**
     * 按点号路径从嵌套Map中取值（如 data.id）
     */
    private Object extractByPath(Map<String, Object> source, String path) {
        if (source == null || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
