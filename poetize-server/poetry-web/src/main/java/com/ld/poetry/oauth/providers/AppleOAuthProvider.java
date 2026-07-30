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
 * Apple（Sign in with Apple）OAuth 2.0 提供商
 * <p>说明：
 * <ul>
 *   <li>Client Secret 不是静态密钥，而是用 Apple 私钥（.p8）签发的 JWT（最长6个月有效期），
 *       管理员需按官方文档生成后填入后台的 Client Secret 字段并定期更换</li>
 *   <li>scope 留空以使用 query 回调模式（请求 name/email scope 时 Apple 强制 form_post，
 *       与本系统 GET 回调不兼容）；用户身份从 token 响应的 id_token 中解析</li>
 *   <li>Apple 不提供昵称与头像（昵称仅在首次授权且请求 name scope 时下发），用户名走系统兜底生成</li>
 * </ul>
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("appleOAuthProvider")
public class AppleOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://appleid.apple.com/auth/authorize";
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

    // token 响应中的 id_token 声明暂存，供 getUserInfo 消费
    private final ThreadLocal<Map<String, Object>> userClaimsHolder = new ThreadLocal<>();

    @Override
    public String getProviderName() {
        return "apple";
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
        // 留空保持 query 回调模式，见类注释
        return "";
    }

    @Override
    public Map<String, Object> getAccessToken(String code) {
        Map<String, Object> tokenData = super.getAccessToken(code);

        Object idToken = tokenData.get("id_token");
        if (idToken instanceof String token && !token.isBlank()) {
            userClaimsHolder.set(parseJwtPayload(token));
        }

        return tokenData;
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            Map<String, Object> claims = userClaimsHolder.get();
            if (claims == null || claims.isEmpty()) {
                throw new UserInfoException("Apple未返回id_token", "apple");
            }

            Object sub = claims.get("sub");
            if (sub == null || String.valueOf(sub).isEmpty()) {
                throw new UserInfoException("Apple未返回用户标识", "apple");
            }

            // id_token 中可能携带邮箱（含私密中转邮箱）
            Object email = claims.get("email");
            String emailStr = email != null ? String.valueOf(email) : "";
            Object[] emailCheck = checkEmailCollectionNeeded(emailStr);

            // Apple 不提供昵称与头像，用户名留空由系统兜底生成
            Map<String, Object> result = new HashMap<>();
            result.put("provider", "apple");
            result.put("uid", String.valueOf(sub));
            result.put("username", "");
            result.put("email", emailCheck[0]);
            result.put("avatar", "");
            result.put("email_collection_needed", emailCheck[1]);

            return result;

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取Apple用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "apple", e);
        } finally {
            userClaimsHolder.remove();
        }
    }

    /**
     * 解析 JWT 载荷段（不验签，token 由服务端直连 Apple 换取，来源可信）
     */
    private Map<String, Object> parseJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new TokenException("id_token格式无效", "apple");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            return claims;
        } catch (Exception e) {
            log.warn("解析Apple id_token失败", e);
            return new HashMap<>();
        }
    }
}
