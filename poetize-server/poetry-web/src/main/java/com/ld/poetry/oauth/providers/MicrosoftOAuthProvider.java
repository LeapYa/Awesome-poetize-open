package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Microsoft OAuth 2.0 提供商（Microsoft 标识平台 v2.0，common 租户，个人与工作账户均可登录）
 * 标准 OAuth2 流程；用户信息经 Microsoft Graph /me 获取
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("microsoftOAuthProvider")
public class MicrosoftOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String USER_INFO_URL = "https://graph.microsoft.com/v1.0/me";

    @Override
    public String getProviderName() {
        return "microsoft";
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
        return "openid profile email User.Read";
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            Map<String, Object> userInfo = sendAuthenticatedGetRequest(getUserInfoUrl(), accessToken);

            Object id = userInfo.get("id");
            if (id == null) {
                throw new UserInfoException("Microsoft未返回用户ID", "microsoft");
            }

            Object displayName = userInfo.get("displayName");

            // 个人账户邮箱在 mail 字段，部分账户回退 userPrincipalName
            Object mail = userInfo.get("mail");
            String emailStr = mail != null ? String.valueOf(mail) : "";
            if (emailStr.isEmpty()) {
                Object upn = userInfo.get("userPrincipalName");
                if (upn != null && String.valueOf(upn).contains("@")
                        && !String.valueOf(upn).contains("#EXT#")) {
                    emailStr = String.valueOf(upn);
                }
            }
            Object[] emailCheck = checkEmailCollectionNeeded(emailStr);

            // Graph 头像需单独接口且个人账户常不可用，留空走默认头像
            Map<String, Object> result = new HashMap<>();
            result.put("provider", "microsoft");
            result.put("uid", String.valueOf(id));
            result.put("username", displayName != null ? String.valueOf(displayName) : "");
            result.put("email", emailCheck[0]);
            result.put("avatar", "");
            result.put("email_collection_needed", emailCheck[1]);

            return result;

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取Microsoft用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "microsoft", e);
        }
    }
}
