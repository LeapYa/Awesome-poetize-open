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
 * 华为账号 OAuth 2.0 提供商（华为开发者联盟 Account Kit，Web 方式接入）
 * token 交换为标准 POST 表单；用户信息经 GOpen.User.getInfo 接口获取
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("huaweiOAuthProvider")
public class HuaweiOAuthProvider extends OAuth2Provider {

    private static final String AUTH_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/authorize";
    private static final String TOKEN_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/token";
    private static final String USER_INFO_URL = "https://account.cloud.huawei.com/rest.php?nsp_svc=GOpen.User.getInfo";

    @Override
    public String getProviderName() {
        return "huawei";
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
        return "openid profile";
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        try {
            // 华为用户信息接口为 POST 表单，access_token 作为表单参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("access_token", accessToken);
            params.add("getNickName", "1");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(getUserInfoUrl(), request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userInfo = objectMapper.readValue(response.getBody(), Map.class);

                // 错误响应含 NSP_STATUS 字段
                Object nspStatus = userInfo.get("NSP_STATUS");
                if (nspStatus != null) {
                    throw new UserInfoException("华为返回错误: NSP_STATUS=" + nspStatus, "huawei");
                }

                Object openId = userInfo.get("openID");
                Object unionId = userInfo.get("unionID");
                String uid = openId != null ? String.valueOf(openId)
                        : (unionId != null ? String.valueOf(unionId) : null);
                if (uid == null || uid.isEmpty()) {
                    throw new UserInfoException("华为未返回用户标识", "huawei");
                }

                Object displayName = userInfo.get("displayName");
                Object avatar = userInfo.get("headPictureURL");

                // 华为基础授权不返回邮箱
                Map<String, Object> result = new HashMap<>();
                result.put("provider", "huawei");
                result.put("uid", uid);
                result.put("username", displayName != null ? String.valueOf(displayName) : "");
                result.put("email", "");
                result.put("avatar", avatar != null ? String.valueOf(avatar) : "");
                result.put("email_collection_needed", true);

                return result;
            }

            throw new UserInfoException("获取华为用户信息失败: HTTP " + response.getStatusCode(), "huawei");

        } catch (UserInfoException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取华为用户信息失败", e);
            throw new UserInfoException("获取用户信息失败: " + e.getMessage(), "huawei", e);
        }
    }
}
