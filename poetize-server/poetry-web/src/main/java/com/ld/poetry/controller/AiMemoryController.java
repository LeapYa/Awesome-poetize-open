package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.Mem0Service;
import com.ld.poetry.utils.AESCryptoUtil;
import com.ld.poetry.utils.PoetryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 记忆管理控制器
 *
 * 端点对照：
 * GET /ai/memory/list → get_user_memories
 * POST /ai/memory/search → search_memories
 * DELETE /ai/memory/{id} → delete_memory
 * DELETE /ai/memory/user/{uid} → delete_all_user_memories
 * POST /ai/memory/testConnection → test_memory_connection
 */
@RestController
@RequestMapping("/ai/memory")
public class AiMemoryController {

    private static final Logger logger = LoggerFactory.getLogger(AiMemoryController.class);

    @Autowired
    private Mem0Service mem0Service;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private AESCryptoUtil aesCryptoUtil;

    /**
     * 获取用户所有记忆
     */
    @LoginCheck
    @GetMapping("/list")
    public PoetryResult<Map<String, Object>> listMemories(
            @RequestParam(required = false) String userId) {
        try {
            String apiKey = getMem0ApiKey();
            if (apiKey == null) {
                return PoetryResult.fail("Mem0 API Key 未配置");
            }

            String resolvedUserId = resolveMemoryUserId(userId);
            Map<String, Object> result = mem0Service.listMemories(resolvedUserId, apiKey);
            return PoetryResult.success(result);
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("获取记忆列表失败", e);
            return PoetryResult.fail("获取记忆列表失败: " + e.getMessage());
        }
    }

    /**
     * 搜索用户记忆
     */
    @LoginCheck
    @PostMapping("/search")
    public PoetryResult<Map<String, Object>> searchMemories(@RequestBody Map<String, Object> request) {
        try {
            String apiKey = getMem0ApiKey();
            if (apiKey == null) {
                return PoetryResult.fail("Mem0 API Key 未配置");
            }

            String query = (String) request.getOrDefault("query", "");
            String userId = request.get("user_id") instanceof String requestedUserId ? requestedUserId : null;
            int limit = request.containsKey("limit") ? ((Number) request.get("limit")).intValue() : 5;

            String resolvedUserId = resolveMemoryUserId(userId);
            Map<String, Object> result = mem0Service.searchMemories(query, resolvedUserId, apiKey, limit);
            return PoetryResult.success(result);
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("搜索记忆失败", e);
            return PoetryResult.fail("搜索记忆失败: " + e.getMessage());
        }
    }

    /**
     * 删除单条记忆
     */
    @LoginCheck
    @DeleteMapping("/{memoryId}")
    public PoetryResult<Map<String, Object>> deleteMemory(@PathVariable String memoryId) {
        try {
            String apiKey = getMem0ApiKey();
            if (apiKey == null) {
                return PoetryResult.fail("Mem0 API Key 未配置");
            }

            verifyMemoryOwnership(memoryId, apiKey);
            Map<String, Object> result = mem0Service.deleteMemory(memoryId, apiKey);
            return PoetryResult.success(result);
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("删除记忆失败", e);
            return PoetryResult.fail("删除记忆失败: " + e.getMessage());
        }
    }

    /**
     * 删除用户所有记忆
     */
    @LoginCheck
    @DeleteMapping("/user/{userId}")
    public PoetryResult<Map<String, Object>> deleteAllUserMemories(@PathVariable String userId) {
        try {
            String apiKey = getMem0ApiKey();
            if (apiKey == null) {
                return PoetryResult.fail("Mem0 API Key 未配置");
            }

            String resolvedUserId = resolveMemoryUserId(userId);
            Map<String, Object> result = mem0Service.deleteAllUserMemories(resolvedUserId, apiKey);
            return PoetryResult.success(result);
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("删除用户记忆失败", e);
            return PoetryResult.fail("删除用户记忆失败: " + e.getMessage());
        }
    }

    /**
     * 测试 Mem0 连接
     */
    @LoginCheck(0)
    @PostMapping("/testConnection")
    public PoetryResult<Map<String, Object>> testConnection() {
        try {
            String apiKey = getMem0ApiKey();
            if (apiKey == null) {
                return PoetryResult.fail("Mem0 API Key 未配置");
            }

            Map<String, Object> result = mem0Service.testConnection(apiKey);
            return PoetryResult.success(result);
        } catch (Exception e) {
            logger.error("测试 Mem0 连接失败", e);
            return PoetryResult.fail("测试连接失败: " + e.getMessage());
        }
    }

    private String resolveMemoryUserId(String requestedUserId) {
        String currentUserId = String.valueOf(PoetryUtil.getUserIdRequired());

        if (PoetryUtil.isBoss() && StringUtils.hasText(requestedUserId)) {
            return requestedUserId.trim();
        }

        if (StringUtils.hasText(requestedUserId) && !currentUserId.equals(requestedUserId.trim())) {
            throw new IllegalArgumentException("无权操作其他用户的记忆");
        }

        return currentUserId;
    }

    @SuppressWarnings("unchecked")
    private void verifyMemoryOwnership(String memoryId, String apiKey) {
        if (PoetryUtil.isBoss()) {
            return;
        }

        String currentUserId = resolveMemoryUserId(null);
        Map<String, Object> result = mem0Service.listMemories(currentUserId, apiKey);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            throw new IllegalArgumentException("无法验证记忆归属，请稍后重试");
        }

        List<Map<String, Object>> memories = (List<Map<String, Object>>) result.get("memories");
        boolean ownedByCurrentUser = memories != null && memories.stream().anyMatch(memory -> memoryIdMatches(memoryId, memory));
        if (!ownedByCurrentUser) {
            throw new IllegalArgumentException("无权删除其他用户的记忆");
        }
    }

    private boolean memoryIdMatches(String memoryId, Map<String, Object> memory) {
        Object id = memory.get("id");
        if (id != null && memoryId.equals(String.valueOf(id))) {
            return true;
        }

        Object altId = memory.get("memory_id");
        return altId != null && memoryId.equals(String.valueOf(altId));
    }

    /**
     * 从 AI 聊天配置中获取 Mem0 API Key
     */
    private String getMem0ApiKey() {
        SysAiConfig config = sysAiConfigService.getAiChatConfigInternal("default");
        if (config == null || config.getMem0ApiKey() == null || config.getMem0ApiKey().isBlank()) {
            return null;
        }

        String key = config.getMem0ApiKey();
        // 尝试 AES 解密
        if (!key.startsWith("m0-")) {
            String decrypted = aesCryptoUtil.decrypt(key);
            if (decrypted != null) {
                return decrypted;
            }
        }
        return key;
    }
}
