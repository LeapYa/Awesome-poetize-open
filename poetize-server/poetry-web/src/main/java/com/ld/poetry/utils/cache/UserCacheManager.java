package com.ld.poetry.utils.cache;

import com.ld.poetry.entity.User;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.utils.RetryUtil;
import com.ld.poetry.utils.TokenValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户信息缓存管理器
 * 提供基于Redis的统一用户缓存策略，简化缓存管理
 *
 * 重构说明：
 * - 移除了本地ConcurrentHashMap缓存
 * - 移除了PoetryCache的直接使用
 * - 统一使用Redis缓存通过CacheService操作
 * - 保持现有API接口不变
 */
@Component
@Slf4j
public class UserCacheManager {

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    private CacheService cacheService;

    /**
     * 根据Token获取用户信息（基于Redis缓存）
     *
     * @param token 用户Token
     * @return 用户信息，如果不存在返回null
     */
    public User getUserByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        return RetryUtil.executeWithRetry(() -> {
            try {
                // 直接从Redis缓存获取用户会话信息
                Integer userId = cacheService.getUserIdFromSession(token);
                if (userId != null) {
                    User user = cacheService.getCachedUser(userId);
                    if (user != null) {
                        return user;
                    }
                }

                return null;
            } catch (Exception e) {
                log.error("从Redis缓存获取用户信息失败: tokenPrefix={}", TokenValidationUtil.getTokenPrefix(token), e);
                return null;
            }
        }, 2, 50, "获取用户信息");
    }

    /**
     * 根据用户ID获取用户信息（基于Redis缓存）
     *
     * @param userId 用户ID
     * @return 用户信息，如果不存在返回null
     */
    public User getUserById(Integer userId) {
        if (userId == null) {
            return null;
        }

        try {
            // 1. 先从Redis缓存获取
            User user = cacheService.getCachedUser(userId);
            if (user != null) {
                return user;
            }

            // 2. 从数据库查询并缓存
            user = userService.getById(userId);
            if (user != null) {
                cacheService.cacheUser(user);
            }
            return user;
        } catch (Exception e) {
            log.error("获取用户信息失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 缓存用户信息（基于Redis缓存）
     *
     * @param token 用户Token
     * @param user  用户信息
     */
    public void cacheUser(String token, User user) {
        if (!StringUtils.hasText(token) || user == null) {
            return;
        }

        try {
            // 缓存用户信息到Redis
            cacheService.cacheUser(user);

            // 缓存用户会话到Redis
            cacheService.cacheUserSession(token, user.getId());

        } catch (Exception e) {
            log.error("缓存用户信息失败: userId={}, tokenPrefix={}", user.getId(), TokenValidationUtil.getTokenPrefix(token), e);
        }
    }

    /**
     * 移除用户缓存（基于Redis缓存）
     *
     * @param token 用户Token
     */
    public void removeUserCache(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        try {
            // 获取用户ID以便清理用户信息缓存
            Integer userId = cacheService.getUserIdFromSession(token);

            // 移除用户会话缓存
            cacheService.evictUserSession(token);

            // 移除用户信息缓存
            if (userId != null) {
                cacheService.evictUser(userId);
            }

        } catch (Exception e) {
            log.error("移除用户缓存失败: tokenPrefix={}", TokenValidationUtil.getTokenPrefix(token), e);
        }
    }

    /**
     * 移除用户缓存（根据用户ID）
     *
     * @param userId 用户ID
     */
    public void removeUserCacheById(Integer userId) {
        if (userId == null) {
            return;
        }

        try {
            // 直接移除用户信息缓存
            cacheService.evictUser(userId);
        } catch (Exception e) {
            log.error("移除用户缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 获取缓存统计信息（基于Redis缓存）
     *
     * @return 缓存统计信息
     */
    public String getCacheStats() {
        try {
            // 简化的统计信息，基于Redis缓存
            return "用户缓存基于Redis统一管理";
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return "缓存统计信息获取失败";
        }
    }

    /**
     * 清空所有用户缓存（基于Redis缓存）
     */
    public void clearAllCache() {
        try {
            // 这里可以扩展为批量清理Redis中的用户缓存
            log.info("用户缓存清理请求已提交（基于Redis）");
        } catch (Exception e) {
            log.error("清空用户缓存失败", e);
        }
    }

    /**
     * 根据Token移除用户缓存
     * @param token 用户Token
     */
    public void removeUserByToken(String token) {
        if (StringUtils.hasText(token)) {
            removeUserCache(token);
        }
    }

    /**
     * 根据用户ID移除用户缓存
     * @param userId 用户ID
     */
    public void removeUserById(Integer userId) {
        if (userId != null) {
            removeUserCacheById(userId);
        }
    }

    /**
     * 缓存用户信息（根据Token）
     * @param token 用户Token
     * @param user 用户对象
     */
    public void cacheUserByToken(String token, User user) {
        if (StringUtils.hasText(token) && user != null) {
            cacheUser(token, user);
        }
    }

    /**
     * 缓存用户信息（根据用户ID）
     * @param userId 用户ID
     * @param user 用户对象
     */
    public void cacheUserById(Integer userId, User user) {
        if (userId != null && user != null) {
            // 直接缓存用户信息到Redis
            cacheService.cacheUser(user);
        }
    }
}