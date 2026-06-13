package com.ld.poetry.oauth.state;

import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OAuth状态管理服务
 * 基于Redis实现OAuth状态的生成、存储和验证
 *
 * @author LeapYa
 * @since 2026-01-10
 */
@Slf4j
@Service
public class OAuthStateService {

    private static final String STATE_KEY_PREFIX = "poetize:oauth:state:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JsonMapper objectMapper;

    @Value("${oauth.state.ttl:600}")
    private int stateTtl;  // 默认10分钟

    /**
     * 生成OAuth状态token
     *
     * @param provider     OAuth提供商
     * @param sessionId    会话ID（可选）
     * @param redirectPath 相对重定向路径（可选）
     * @return 状态token
     */
    public String generateState(String provider, String sessionId, String redirectPath) {
        try {
            // 生成随机token
            byte[] tokenBytes = new byte[24];
            RANDOM.nextBytes(tokenBytes);
            String stateToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

            long timestamp = System.currentTimeMillis() / 1000;

            // 构建状态数据
            Map<String, Object> stateData = new HashMap<>();
            stateData.put("provider", provider);
            stateData.put("session_id", sessionId);
            if (redirectPath != null) stateData.put("redirect_path", redirectPath);
            stateData.put("timestamp", timestamp);
            stateData.put("expires_at", timestamp + stateTtl);
            stateData.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // 存储到Redis
            String key = STATE_KEY_PREFIX + stateToken;
            String value = objectMapper.writeValueAsString(stateData);
            redisTemplate.opsForValue().set(key, value, stateTtl, TimeUnit.SECONDS);

            log.debug("生成OAuth状态: provider={}, state={}", provider, stateToken);
            return stateToken;

        } catch (Exception e) {
            log.error("生成OAuth状态失败", e);
            throw new RuntimeException("生成OAuth状态失败", e);
        }
    }
    
    /**
     * 生成OAuth状态token（兼容旧方法）
     */
    public String generateState(String provider, String sessionId) {
        return generateState(provider, sessionId, null);
    }

    /**
     * 验证并消费OAuth状态token
     *
     * @param stateToken 状态token
     * @param provider   期望的OAuth提供商
     * @return 状态数据，验证失败返回null
     */
    public Map<String, Object> verifyAndConsumeState(String stateToken, String provider) {
        try {
            if (stateToken == null || stateToken.isEmpty()) {
                log.warn("状态token为空");
                return null;
            }

            // 从Redis获取状态数据
            String key = STATE_KEY_PREFIX + stateToken;
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                log.warn("状态不存在或已过期: {}", stateToken);
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> stateData = objectMapper.readValue(value, Map.class);

            // 验证provider
            String storedProvider = (String) stateData.get("provider");
            if (!provider.equals(storedProvider)) {
                log.warn("provider不匹配: 期望={}, 实际={}", provider, storedProvider);
                return null;
            }

            // 删除已使用的状态（一次性使用）
            redisTemplate.delete(key);

            log.debug("验证OAuth状态成功: provider={}, state={}", provider, stateToken);
            return stateData;

        } catch (Exception e) {
            log.error("验证OAuth状态失败", e);
            return null;
        }
    }

    /**
     * 获取状态信息（不消费）
     *
     * @param stateToken 状态token
     * @return 状态数据，不存在返回null
     */
    public Map<String, Object> getStateInfo(String stateToken) {
        try {
            if (stateToken == null || stateToken.isEmpty()) {
                return null;
            }

            String key = STATE_KEY_PREFIX + stateToken;
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> stateData = objectMapper.readValue(value, Map.class);
            return stateData;

        } catch (Exception e) {
            log.error("获取OAuth状态信息失败", e);
            return null;
        }
    }

    /**
     * 删除状态
     *
     * @param stateToken 状态token
     */
    public void deleteState(String stateToken) {
        if (stateToken != null && !stateToken.isEmpty()) {
            String key = STATE_KEY_PREFIX + stateToken;
            redisTemplate.delete(key);
        }
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("type", "redis");
        stats.put("key_prefix", STATE_KEY_PREFIX);
        stats.put("ttl_seconds", stateTtl);
        stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            stats.put("redis_status", "connected");
        } catch (Exception e) {
            stats.put("redis_status", "disconnected");
        }

        return stats;
    }
}
