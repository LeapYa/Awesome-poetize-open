package com.ld.poetry.service.ai;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 聊天历史缓存服务：按 conversationId 把完整历史（含工具调用结果）持久化到 Redis，
 * 支持基于哈希校验的增量协议。
 * <p>
 * 设计目标：避免前端每轮都重传完整历史（在引入工具调用持久化后，单条 assistant 消息可能携带
 * 数千字的工具结果，20 条历史上行可达数十 KB）。
 * <p>
 * 协议：
 * <ul>
 *   <li>请求携带 {@code baseHistoryHash}（前端上次响应收到的哈希）+ {@code history}（增量或完整）</li>
 *   <li>服务端从 Redis 取缓存历史，若末尾哈希与 {@code baseHistoryHash} 匹配，则视 {@code history} 为增量，
 *       拼接：[缓存历史] + [增量 history]</li>
 *   <li>否则视 {@code history} 为完整历史，直接覆盖 Redis 缓存</li>
 *   <li>处理完成后把"完整历史 + 本次 user/assistant 消息"写回 Redis，并返回新哈希给客户端</li>
 * </ul>
 * <p>
 * 撤回/编辑场景：前端在截断消息时清空 {@code lastHistoryHash}，下次请求不送 {@code baseHistoryHash}，
 * 自动降级为完整历史同步，覆盖 Redis 中过期的内容。
 * <p>
 * 存储实现说明：使用 {@link StringRedisTemplate} 直接读写 JSON 字符串，绕开全局
 * {@code RedisTemplate<String, Object>} 启用的 {@code activateDefaultTyping(NON_FINAL)}
 * 类型标识机制——该机制对 {@code List<Map<String, Object>>} 嵌套结构的反序列化会因类型校验失败而报错。
 *
 * @author LeapYa
 * @since 2026-06-26
 */
@Service
@Slf4j
public class HistoryCacheService {

    /** Redis 缓存 TTL：2 小时（用户闲置后自动清理，避免无限增长） */
    private static final long CACHE_TTL_SECONDS = 2 * 60 * 60;

    private static final String KEY_PREFIX = "ai:chat:history:";

    private static final TypeReference<List<Map<String, Object>>> HISTORY_TYPE =
            new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public HistoryCacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析请求历史：根据 {@code baseHistoryHash} 决定是拼接增量还是采用完整历史。
     * <p>
     * 任何异常都降级为"使用前端发来的 history"（保证可用性，不阻断聊天）。
     * 当客户端带着 {@code baseHistoryHash} 但 Redis 缓存失效或哈希不匹配时，
     * 返回 {@code cacheMiss=true} 标志，调用方应据此通知前端用完整历史重试一次，
     * 避免把"增量"误当"完整历史"使用导致上下文丢失。
     *
     * @param conversationId  会话 ID
     * @param userId          用户 ID（防止跨用户碰撞）
     * @param baseHistoryHash 客户端上次收到的哈希（可能为 null/空）
     * @param requestHistory  客户端本次上送的历史（增量或完整）
     * @return 解析决策：包含历史列表与 cacheMiss 标志
     */
    public CacheDecision resolveHistory(
            String conversationId, String userId,
            String baseHistoryHash, List<Map<String, Object>> requestHistory) {

        if (requestHistory == null) {
            requestHistory = List.of();
        }

        // 没有哈希：客户端首次/撤回/降级，直接用完整历史
        if (baseHistoryHash == null || baseHistoryHash.isBlank()) {
            log.debug("历史增量协议: MISS no-hash conversationId={} requestSize={}",
                    conversationId, requestHistory.size());
            return new CacheDecision(requestHistory, false);
        }

        CachedHistory cached = loadFromRedis(conversationId, userId);
        if (cached == null) {
            // 关键降级场景：客户端带着 baseHistoryHash 来，但 Redis 缓存已过期 / 被清。
            // requestHistory 此时是增量（仅末尾 1-2 条），后端无法用它重建完整历史。
            // 返回 cacheMiss=true 让调用方短路，通知前端清空 hash 并用完整历史重试一次。
            log.warn("历史增量协议: MISS redis-expired conversationId={} baseHash={} requestSize={}",
                    conversationId, baseHistoryHash, requestHistory.size());
            return new CacheDecision(requestHistory, true);
        }

        if (!baseHistoryHash.equals(cached.hash)) {
            log.warn("历史增量协议: MISS hash-mismatch conversationId={} cachedHash={} baseHash={} requestSize={}",
                    conversationId, cached.hash, baseHistoryHash, requestHistory.size());
            return new CacheDecision(requestHistory, true);
        }

        // 命中：拼接缓存历史 + 增量
        List<Map<String, Object>> merged = new ArrayList<>(cached.history);
        merged.addAll(requestHistory);
        log.debug("历史增量协议: HIT conversationId={} cachedSize={} incrementalSize={} mergedSize={}",
                conversationId, cached.history.size(), requestHistory.size(), merged.size());
        return new CacheDecision(merged, false);
    }

    /**
     * 历史解析决策。
     *
     * @param history   解析后的历史（cacheMiss=true 时为前端发来的增量，不可直接使用）
     * @param cacheMiss true 表示客户端的 baseHistoryHash 已失效（Redis miss / 哈希不匹配），
     *                  调用方应短路响应并通知前端用完整历史重试一次
     */
    public record CacheDecision(List<Map<String, Object>> history, boolean cacheMiss) {
    }

    /**
     * 写回历史到 Redis 并返回新哈希。
     * <p>
     * 完整历史 = 已解析历史 + 本次新增的 [user 消息, assistant 回复]。
     * 任意异常吞掉，避免影响主流程。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param resolvedHistory 已解析的完整历史（不含本次新增消息）
     * @param userMessage    本次用户消息（map 形式：{role, content, toolCalls?, ...}）
     * @param assistantReply 本次 assistant 回复（map 形式）
     * @return 新历史的哈希；写回失败返回 null（前端下次走完整同步）
     */
    public String putHistory(
            String conversationId, String userId,
            List<Map<String, Object>> resolvedHistory,
            Map<String, Object> userMessage,
            Map<String, Object> assistantReply) {

        try {
            List<Map<String, Object>> fullHistory = new ArrayList<>(resolvedHistory);
            if (userMessage != null && !userMessage.isEmpty()) {
                fullHistory.add(userMessage);
            }
            if (assistantReply != null && !assistantReply.isEmpty()) {
                fullHistory.add(assistantReply);
            }
            return putHistoryInternal(conversationId, userId, fullHistory);
        } catch (Exception e) {
            log.warn("写回历史缓存失败（不阻断主流程）: conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 计算历史末尾的哈希。基于完整历史的 JSON 序列化做 SHA-256 截断，
     * 客户端只比对字符串相等性，不关心算法。
     */
    private String computeHash(List<Map<String, Object>> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private CachedHistory loadFromRedis(String conversationId, String userId) {
        String key = buildKey(conversationId, userId);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            // 防御：旧实现用 RedisTemplate<String,Object> 写入，启用了 defaultTyping，
            // 数据格式为 ["java.util.LinkedHashMap", {...}]（数组带类型标识），
            // 与当前 StringRedisTemplate 的纯 JSON 格式不兼容，直接清理让下次重写。
            String trimmed = json.stripLeading();
            if (trimmed.startsWith("[")) {
                log.warn("历史缓存检测到旧 defaultTyping 格式，清理重写: conversationId={}", conversationId);
                stringRedisTemplate.delete(key);
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = objectMapper.readValue(json, Map.class);
            Object hashObj = envelope.get("hash");
            Object historyObj = envelope.get("history");
            if (hashObj == null || historyObj == null) {
                return null;
            }
            List<Map<String, Object>> history = objectMapper.convertValue(historyObj, HISTORY_TYPE);
            String hash = hashObj.toString();
            return new CachedHistory(history, hash);
        } catch (Exception e) {
            log.warn("读取历史缓存失败（已清理）: conversationId={}, error={}", conversationId, e.getMessage());
            // 任何解析失败都清理 key，避免脏数据反复触发 cacheMiss
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception ignored) {
                // ignore
            }
            return null;
        }
    }

    private String putHistoryInternal(String conversationId, String userId,
            List<Map<String, Object>> fullHistory) {
        String hash = computeHash(fullHistory);
        if (hash == null) {
            return null;
        }
        try {
            // 使用 LinkedHashMap 保证字段顺序（hash 在前，便于人工排查）
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("hash", hash);
            envelope.put("history", fullHistory);
            String json = objectMapper.writeValueAsString(envelope);
            stringRedisTemplate.opsForValue().set(
                    buildKey(conversationId, userId), json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("历史缓存已写回: conversationId={} size={} hash={} ttl={}s",
                    conversationId, fullHistory.size(), hash, CACHE_TTL_SECONDS);
            return hash;
        } catch (Exception e) {
            log.warn("写回历史缓存失败: conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }

    private String buildKey(String conversationId, String userId) {
        // 加 userId 防止跨用户使用同一 conversationId（虽然概率低，但安全起见）
        return KEY_PREFIX + userId + ":" + conversationId;
    }

    private record CachedHistory(List<Map<String, Object>> history, String hash) {
    }
}
