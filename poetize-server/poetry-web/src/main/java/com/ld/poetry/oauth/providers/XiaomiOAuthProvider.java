package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.TokenException;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 小米账号 OAuth 2.0 提供商（OIDC 授权码模式）
 * token 交换为标准 POST 表单；用户身份与昵称、头像从 token 响应携带的 id_token 中解析，
 * 无需再调用用户信息接口
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("xiaomiOAuthProvider")
public class XiaomiOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://account.xiaomi.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://account.xiaomi.com/oauth2/auth/token";

    // token 响应中的用户信息暂存，供 getUserInfo 消费
    private final ThreadLocal<Map<String, Object>> userClaimsHolder = new ThreadLocal<>();

    @Override
    public String getProviderName() {
        return "xiaomi";
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
        // 用户信息来自 id_token，无独立接口
        return "";
    }

    @Override
    protected String getScope() {
        // OIDC 模式要求 scope 必须包含 openid
        return "openid";
    }

    @Override
    public Map<String, Object> getAccessToken(String code) {
        Map<String, Object> tokenData = super.getAccessToken(code);

        // 从 token 响应中提取用户标识与 id_token 声明，暂存供 getUserInfo 使用
        Map<String, Object> claims = new HashMap<>();
        Object unionId = tokenData.get("union_id");
        Object openId = tokenData.get("openId");
        if (unionId != null) {
            claims.put("union_id", unionId);
        }
        if (openId != null) {
            claims.put("openId", openId);
        }

        Object idToken = tokenData.get("id_token");
        if (idToken instanceof String token && !token.isBlank()) {
            claims.putAll(parseJwtPayload(token));
        }
        userClaimsHolder.set(claims);

        return tokenData;
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            Map<String, Object> claims = userClaimsHolder.get();
            if (claims == null || claims.isEmpty()) {
                throw new UserInfoException("小米未返回用户信息", "xiaomi");
            }

            // uid 优先取跨应用稳定的 union_id，回退 openId 与 id_token 的 sub
            Object uid = claims.get("union_id");
            if (uid == null) {
                uid = claims.get("openId");
            }
            if (uid == null) {
                uid = claims.get("sub");
            }
            if (uid == null || String.valueOf(uid).isEmpty()) {
                throw new UserInfoException("小米未返回用户标识", "xiaomi");
            }

            Object nickname = claims.get("nickname");
            Object picture = claims.get("picture");

            // 小米登录不返回邮箱
            Map<String, Object> result = new HashMap<>();
            result.put("provider", "xiaomi");
            result.put("uid", String.valueOf(uid));
            result.put("username", nickname != null ? String.valueOf(nickname) : "");
            result.put("email", "");
            result.put("avatar", picture != null ? String.valueOf(picture) : "");
            result.put("email_collection_needed", true);

            return result;

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取小米用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "xiaomi", e);
        } finally {
            userClaimsHolder.remove();
        }
    }

    /**
     * 解析 JWT 载荷段（不验签，token 由服务端直连小米换取，来源可信）
     */
    private Map<String, Object> parseJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new TokenException("id_token格式无效", "xiaomi");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            return claims;
        } catch (Exception e) {
            log.warn("解析小米id_token失败", e);
            return new HashMap<>();
        }
    }
}
