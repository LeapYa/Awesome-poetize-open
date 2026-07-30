package com.ld.poetry.oauth.providers;

import com.ld.poetry.oauth.base.OAuth2Provider;
import com.ld.poetry.oauth.exception.OAuthException;
import com.ld.poetry.oauth.exception.TokenException;
import com.ld.poetry.oauth.exception.UserInfoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steam 登录提供商
 * <p>Steam 不使用 OAuth2，而是 OpenID 2.0 协议：
 * <ul>
 *   <li>无需 Client ID/Secret，任何站点可直接发起登录</li>
 *   <li>回调携带 openid.* 参数，服务端需将其回传 Steam 做 check_authentication 验签</li>
 *   <li>用户标识（SteamID64）从 openid.claimed_id 提取</li>
 *   <li>昵称与头像需 Steam Web API Key（免费申请，填入 Client Secret 字段，可选）</li>
 * </ul>
 * 回调分支由 OAuthLoginController 以 instanceof 特判处理（与 Twitter OAuth1 同模式）。
 *
 * @author LeapYa
 * @since 2026-07-30
 */
@Slf4j
@Component("steamOpenIdProvider")
public class SteamOpenIdProvider extends OAuth2Provider {

    private static final String STEAM_OPENID_URL = "https://steamcommunity.com/openid/login";
    private static final String PLAYER_SUMMARIES_URL = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/";
    private static final String OPENID_NS = "http://specs.openid.net/auth/2.0";
    private static final String IDENTIFIER_SELECT = "http://specs.openid.net/auth/2.0/identifier_select";
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("https?://steamcommunity\\.com/openid/id/(\\d+)");

    @Override
    public String getProviderName() {
        return "steam";
    }

    @Override
    protected String getAuthorizationUrl() {
        return STEAM_OPENID_URL;
    }

    @Override
    protected String getTokenUrl() {
        // OpenID 2.0 无令牌交换环节
        return "";
    }

    @Override
    protected String getUserInfoUrl() {
        return PLAYER_SUMMARIES_URL;
    }

    @Override
    protected String getScope() {
        return "";
    }

    @Override
    public List<String> getRequiredConfigFields() {
        // Steam 免凭据，仅需回调地址（由工厂按站点地址自动生成）
        return List.of("redirectUri");
    }

    @Override
    public boolean validateConfig() {
        return config != null && StringUtils.hasText(config.getRedirectUri());
    }

    /**
     * 构造 OpenID 2.0 checkid_setup 授权URL，state 附加在 return_to 上随回调原样带回
     */
    @Override
    public String getAuthUrl(String state) {
        String redirectUri = config.getRedirectUri();
        String returnTo = redirectUri + (redirectUri.contains("?") ? "&" : "?") + "state=" + state;
        String realm = extractRealm(redirectUri);

        return STEAM_OPENID_URL
                + "?openid.ns=" + encode(OPENID_NS)
                + "&openid.mode=checkid_setup"
                + "&openid.return_to=" + encode(returnTo)
                + "&openid.realm=" + encode(realm)
                + "&openid.identity=" + encode(IDENTIFIER_SELECT)
                + "&openid.claimed_id=" + encode(IDENTIFIER_SELECT);
    }

    /**
     * 验证 OpenID 断言：将回调收到的全部 openid.* 参数回传 Steam 做 check_authentication，
     * 验签通过后从 openid.claimed_id 提取 SteamID64
     *
     * @param callbackParams 回调请求的完整参数表
     * @return SteamID64
     */
    public String verifyAssertion(Map<String, String[]> callbackParams) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            for (Map.Entry<String, String[]> entry : callbackParams.entrySet()) {
                if (entry.getKey().startsWith("openid.") && entry.getValue() != null && entry.getValue().length > 0) {
                    params.add(entry.getKey(), entry.getValue()[0]);
                }
            }
            if (params.isEmpty()) {
                throw new OAuthException("回调缺少openid参数", "invalid_callback", "steam");
            }
            // 将模式替换为验签请求
            params.set("openid.mode", "check_authentication");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(STEAM_OPENID_URL, request, String.class);

            String body = response.getBody();
            if (response.getStatusCode() != HttpStatus.OK || body == null || !body.contains("is_valid:true")) {
                throw new OAuthException("Steam OpenID验签失败", "assertion_invalid", "steam");
            }

            // 提取 SteamID64
            String claimedId = params.getFirst("openid.claimed_id");
            if (claimedId == null) {
                throw new OAuthException("Steam未返回claimed_id", "invalid_callback", "steam");
            }
            Matcher matcher = STEAM_ID_PATTERN.matcher(claimedId);
            if (!matcher.matches()) {
                throw new OAuthException("无法解析SteamID: " + claimedId, "invalid_callback", "steam");
            }
            return matcher.group(1);

        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Steam OpenID验签失败", e);
            throw new OAuthException("验签失败: " + e.getMessage(), "network_error", "steam", e);
        }
    }

    /**
     * 构建标准化用户信息；配置了 Web API Key（Client Secret 字段）时拉取昵称与头像
     */
    public Map<String, Object> buildUserInfo(String steamId) {
        String username = "";
        String avatar = "";

        String apiKey = config.getClientSecret();
        if (StringUtils.hasText(apiKey)) {
            try {
                String url = PLAYER_SUMMARIES_URL + "?key=" + encode(apiKey) + "&steamids=" + steamId;
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
                    Object responseObj = body.get("response");
                    if (responseObj instanceof Map) {
                        Object playersObj = ((Map<?, ?>) responseObj).get("players");
                        if (playersObj instanceof List<?> players && !players.isEmpty()
                                && players.get(0) instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> player = (Map<String, Object>) players.get(0);
                            Object personaName = player.get("personaname");
                            Object avatarFull = player.get("avatarfull");
                            username = personaName != null ? String.valueOf(personaName) : "";
                            avatar = avatarFull != null ? String.valueOf(avatarFull) : "";
                        }
                    }
                }
            } catch (Exception e) {
                // 资料拉取失败不阻断登录，用户名走系统兜底生成
                log.warn("获取Steam用户资料失败，将使用兜底用户名: {}", e.getMessage());
            }
        }

        // Steam 不提供邮箱
        Map<String, Object> result = new HashMap<>();
        result.put("provider", "steam");
        result.put("uid", steamId);
        result.put("username", username);
        result.put("email", "");
        result.put("avatar", avatar);
        result.put("email_collection_needed", true);
        return result;
    }

    @Override
    public Map<String, Object> getAccessToken(String code) {
        throw new TokenException("Steam使用OpenID 2.0流程，请使用verifyAssertion方法", "steam");
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        throw new UserInfoException("Steam使用OpenID 2.0流程，请使用buildUserInfo方法", "steam");
    }

    /**
     * 从回调地址提取 realm（scheme://authority）
     */
    private String extractRealm(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return redirectUri;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
