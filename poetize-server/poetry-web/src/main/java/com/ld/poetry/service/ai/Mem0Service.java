package com.ld.poetry.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Mem0 记忆管理服务
 * 通过 HTTP API 与 Mem0 交互
 *
 */
@Service
public class Mem0Service {

    private static final Logger logger = LoggerFactory.getLogger(Mem0Service.class);
    private static final String MEM0_API_BASE = "https://api.mem0.ai/v1";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 搜索用户记忆
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> searchMemories(String query, String userId, String apiKey, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("user_id", userId);
            body.put("limit", limit);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    MEM0_API_BASE + "/memories/search/",
                    HttpMethod.POST, entity, Map.class);

            result.put("success", true);
            result.put("memories", response.getBody() != null ? response.getBody().get("results") : List.of());
        } catch (Exception e) {
            logger.error("搜索记忆失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("memories", List.of());
        }
        return result;
    }

    /**
     * 获取用户所有记忆
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listMemories(String userId, String apiKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    MEM0_API_BASE + "/memories/?user_id=" + userId,
                    HttpMethod.GET, entity, List.class);

            result.put("success", true);
            result.put("memories", response.getBody() != null ? response.getBody() : List.of());
        } catch (Exception e) {
            logger.error("获取记忆列表失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("memories", List.of());
        }
        return result;
    }

    /**
     * 添加记忆
     *
     * @param metadata 环境元数据（time/ip/location），随记忆保存供检索时回溯"当时"的时空信息；可为 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> addMemory(String content, String userId, String apiKey,
            Map<String, String> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", List.of(Map.of("role", "user", "content", content)));
            body.put("user_id", userId);
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    MEM0_API_BASE + "/memories/",
                    HttpMethod.POST, entity, Map.class);

            result.put("success", true);
            result.put("data", response.getBody());
        } catch (Exception e) {
            logger.error("添加记忆失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 删除单条记忆
     */
    public Map<String, Object> deleteMemory(String memoryId, String apiKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            restTemplate.exchange(
                    MEM0_API_BASE + "/memories/" + memoryId + "/",
                    HttpMethod.DELETE, entity, Void.class);

            result.put("success", true);
        } catch (Exception e) {
            logger.error("删除记忆失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 删除用户所有记忆
     */
    public Map<String, Object> deleteAllUserMemories(String userId, String apiKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            restTemplate.exchange(
                    MEM0_API_BASE + "/memories/?user_id=" + userId,
                    HttpMethod.DELETE, entity, Void.class);

            result.put("success", true);
        } catch (Exception e) {
            logger.error("删除用户所有记忆失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试连接
     */
    public Map<String, Object> testConnection(String apiKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            restTemplate.exchange(
                    MEM0_API_BASE + "/memories/?user_id=test_connection",
                    HttpMethod.GET, entity, Object.class);

            result.put("success", true);
            result.put("message", "Mem0 连接成功");
            result.put("duration", (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Mem0 连接失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 格式化记忆为上下文文本。
     * 若记忆携带环境元数据（保存时注入的 time/location），以中缀形式展示，
     * 让模型知道每条记忆发生的时间与地点。
     */
    public String formatMemoriesForContext(List<Map<String, Object>> memories) {
        if (memories == null || memories.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[用户相关记忆]\n");
        for (Map<String, Object> mem : memories) {
            String memory = (String) mem.getOrDefault("memory", "");
            if (!memory.isBlank()) {
                String envInfix = formatMemoryEnvInfix(mem.get("metadata"));
                sb.append("- ").append(envInfix).append(memory).append("\n");
            }
        }
        sb.append("\n请在回答中适当参考以上记忆信息。\n");
        return sb.toString();
    }

    /**
     * 从记忆元数据中提取时空中缀，如 {@code [2026年9月2日 21:39 · 广东广州] }。
     * 元数据缺失或格式不符时返回空串（旧记忆无元数据，正常降级）。
     */
    private String formatMemoryEnvInfix(Object metadata) {
        if (!(metadata instanceof Map<?, ?> meta)) {
            return "";
        }
        Object time = meta.get("time");
        Object location = meta.get("location");
        boolean hasTime = time != null && !String.valueOf(time).isBlank();
        boolean hasLocation = location != null && !String.valueOf(location).isBlank()
                && !"未知".equals(location);
        if (!hasTime && !hasLocation) {
            return "";
        }
        StringBuilder infix = new StringBuilder("[");
        if (hasTime) {
            infix.append(time);
        }
        if (hasTime && hasLocation) {
            infix.append(" · ");
        }
        if (hasLocation) {
            infix.append(location);
        }
        return infix.append("] ").toString();
    }

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Token " + apiKey);
        return headers;
    }
}
