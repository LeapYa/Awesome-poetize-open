package com.ld.poetry.oauth.state;

import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OAuth临时授权码服务
 * 用于生成一次性临时授权码，替代直接在URL中传递token
 *
 * 安全机制：
 * 1. 临时码有效期极短（30秒）
 * 2. 一次性使用，使用后立即删除
 * 3. 使用加密安全的随机数生成
 * 4. 绑定用户ID，防止盗用
 *
 * @author LeapYa
 * @since 2026-01-12
 */
@Slf4j
@Service
public class OAuthAuthCodeService {

    private static final String AUTH_CODE_KEY_PREFIX = "poetize:oauth:authcode:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JsonMapper objectMapper;

    @Value("${oauth.authcode.ttl:30}")
    private int authCodeTtl;  // 默认30秒，极短有效期

    /**
     * 生成临时授权码
     *
     * @param userId              用户ID
     * @param accessToken         真实的访问令牌
     * @param redirectPath        重定向路径
     * @param emailCollectionNeeded 是否需要收集邮箱
     * @return 临时授权码
     */
    public String generateAuthCode(Integer userId, String accessToken, String redirectPath, boolean emailCollectionNeeded) {
        try {
            // 生成32字节的随机授权码
            byte[] codeBytes = new byte[32];
            RANDOM.nextBytes(codeBytes);
            String authCode = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);

            // 构建授权码数据
            Map<String, Object> codeData = new HashMap<>();
            codeData.put("user_id", userId);
            codeData.put("access_token", accessToken);
            codeData.put("redirect_path", redirectPath);
            codeData.put("email_collection_needed", emailCollectionNeeded);
            codeData.put("created_at", System.currentTimeMillis());

            // 存储到Redis
            String key = AUTH_CODE_KEY_PREFIX + authCode;
            String value = objectMapper.writeValueAsString(codeData);
            redisTemplate.opsForValue().set(key, value, authCodeTtl, TimeUnit.SECONDS);

            log.debug("生成临时授权码: userId={}, code={}", userId, authCode.substring(0, 8) + "...");
            return authCode;

        } catch (Exception e) {
            log.error("生成临时授权码失败", e);
            throw new RuntimeException("生成临时授权码失败", e);
        }
    }

    /**
     * 验证并消费临时授权码
     *
     * @param authCode 临时授权码
     * @return 授权码数据，包含 access_token、redirect_path 等；验证失败返回 null
     */
    public Map<String, Object> verifyAndConsumeAuthCode(String authCode) {
        try {
            if (authCode == null || authCode.isEmpty()) {
                log.warn("临时授权码为空");
                return null;
            }

            // 从Redis获取授权码数据
            String key = AUTH_CODE_KEY_PREFIX + authCode;
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                log.warn("临时授权码不存在或已过期: {}", authCode.substring(0, Math.min(8, authCode.length())) + "...");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> codeData = objectMapper.readValue(value, Map.class);

            // 立即删除已使用的授权码（一次性使用）
            redisTemplate.delete(key);

            log.debug("验证临时授权码成功: userId={}", codeData.get("user_id"));
            return codeData;

        } catch (Exception e) {
            log.error("验证临时授权码失败", e);
            return null;
        }
    }

    /**
     * 删除授权码
     *
     * @param authCode 授权码
     */
    public void deleteAuthCode(String authCode) {
        if (authCode != null && !authCode.isEmpty()) {
            String key = AUTH_CODE_KEY_PREFIX + authCode;
            redisTemplate.delete(key);
        }
    }
}
