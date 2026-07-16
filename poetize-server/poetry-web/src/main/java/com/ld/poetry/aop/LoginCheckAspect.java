package com.ld.poetry.aop;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.entity.User;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.handle.PoetryLoginException;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@Aspect
@Component
@Order(0)
@Slf4j
public class LoginCheckAspect {

    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private LockManager lockManager;

    @Autowired
    private SysAuditLogService sysAuditLogService;
    
    /**
     * 管理员请求日志限流缓存
     * Key: userId, Value: 上次记录日志的时间戳
     */
    private final ConcurrentHashMap<Integer, Long> adminLogThrottle = new ConcurrentHashMap<>();
    
    /**
     * 日志记录间隔（毫秒），默认3分钟
     */
    private static final long LOG_INTERVAL_MS = TimeUnit.MINUTES.toMillis(3);

    @Around("@annotation(loginCheck)")
    public Object around(ProceedingJoinPoint joinPoint, LoginCheck loginCheck) throws Throwable {
        HttpServletRequest request = PoetryUtil.getRequest();
        String clientIp = PoetryUtil.getIpAddr(request);
        String requestURI = request.getRequestURI();

        String token = PoetryUtil.getTokenWithoutBearer();
        if (!StringUtils.hasText(token)) {
            log.warn("Token为空，登录已过期");
            recordSecurityEvent("AUTH_REQUIRED", null, "未登录访问受保护接口", requestURI,
                    "TOKEN_EMPTY", loginCheck.value(), null);
            throw new PoetryLoginException(CodeMsg.LOGIN_EXPIRED.getMsg());
        }

        // 使用PoetryUtil获取用户信息（已集成Redis缓存）
        User user = PoetryUtil.getCurrentUser();
        if (user == null) {
            // 如果允许过期token通过（如退出登录接口），直接放行
            if (loginCheck.allowExpired()) {
                log.info("Token已过期但允许通过（allowExpired=true）- IP: {}, URI: {}", clientIp, requestURI);
                return joinPoint.proceed();
            }
            log.warn("Token无效或已过期: prefix={}", TokenValidationUtil.getTokenPrefix(token));
            recordSecurityEvent("TOKEN_EXPIRED", null, "Token无效或已过期", requestURI,
                    "TOKEN_INVALID_OR_EXPIRED", loginCheck.value(), null);
            throw new PoetryLoginException(CodeMsg.LOGIN_EXPIRED.getMsg());
        }

        // 验证token类型和权限 - 使用更安全的startsWith()方法防止权限绕过攻击
        if (TokenValidationUtil.isUserToken(token)) {
            if (loginCheck.value() == PoetryEnum.USER_TYPE_ADMIN.getCode() || loginCheck.value() == PoetryEnum.USER_TYPE_DEV.getCode()) {
                log.warn("普通用户尝试访问管理员接口 - 用户: {}, IP: {}, token前缀: {}",
                    user.getUsername(), clientIp, TokenValidationUtil.getTokenPrefix(token));
                recordSecurityEvent("PERMISSION_DENIED", user, "普通用户尝试访问管理员接口", requestURI,
                        "USER_TOKEN_FOR_ADMIN_API", loginCheck.value(), user.getUserType());
                return PoetryResult.fail("请输入管理员账号！");
            }
        } else if (TokenValidationUtil.isAdminToken(token)) {
            // 管理员请求日志限流：每分钟最多记录一次
            if (!loginCheck.silentLog()) {
                logAdminRequestThrottled(user.getId(), clientIp, user.getUsername());
            }
            // 检查是否为需要超级管理员权限的接口（保留某些特殊接口的限制）
            if (loginCheck.value() == PoetryEnum.USER_TYPE_ADMIN.getCode()) {
                // 对于@LoginCheck(0)的接口，检查用户是否为管理员类型
                if (user.getUserType() != PoetryEnum.USER_TYPE_ADMIN.getCode() &&
                    user.getUserType() != PoetryEnum.USER_TYPE_DEV.getCode()) {
                    log.warn("非管理员用户尝试访问管理员接口 - 用户: {}, userType: {}, IP: {}",
                            user.getUsername(), user.getUserType(), clientIp);
                    recordSecurityEvent("PERMISSION_DENIED", user, "非管理员用户尝试访问管理员接口", requestURI,
                            "ADMIN_ROLE_REQUIRED", loginCheck.value(), user.getUserType());
                    return PoetryResult.fail("请输入管理员账号！");
                }
                log.info("管理员用户访问管理员接口 - 用户: {}, userType: {}, IP: {}",
                        user.getUsername(), user.getUserType(), clientIp);
            }
        } else {
            log.warn("无效的token类型或格式 - IP: {}, token前缀: {}, token长度: {}",
                clientIp, TokenValidationUtil.getTokenPrefix(token), token.length());
            recordSecurityEvent("INVALID_TOKEN", user, "无效的Token类型或格式", requestURI,
                    "TOKEN_TYPE_INVALID", loginCheck.value(), user.getUserType());
            throw new PoetryLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        if (loginCheck.value() < user.getUserType()) {
            log.warn("用户权限不足 - 需要权限: {}, 当前权限: {}", loginCheck.value(), user.getUserType());
            recordSecurityEvent("PERMISSION_DENIED", user, "用户权限不足", requestURI,
                    "USER_TYPE_TOO_LOW", loginCheck.value(), user.getUserType());
            throw new PoetryRuntimeException("权限不足！");
        }

        // 重置过期时间 - 使用Redis缓存替换PoetryCache
        try {
            Integer userId = user.getId();
            boolean needRefresh = false;

            // 检查是否需要刷新token间隔
            if (TokenValidationUtil.isUserToken(token)) {
                String intervalKey = CacheConstants.buildUserTokenIntervalKey(userId);
                needRefresh = cacheService.get(intervalKey) == null;
            } else if (TokenValidationUtil.isAdminToken(token)) {
                String intervalKey = CacheConstants.buildAdminTokenIntervalKey(userId);
                needRefresh = cacheService.get(intervalKey) == null;
            }

            if (needRefresh) {
                // 使用 LockManager 替代 String.intern()，避免内存泄漏
                lockManager.executeWithLock("refreshToken:" + userId, () -> {
                    boolean shouldRefresh = false;

                    // 双重检查锁定模式
                    if (TokenValidationUtil.isUserToken(token)) {
                        String intervalKey = CacheConstants.buildUserTokenIntervalKey(userId);
                        shouldRefresh = cacheService.get(intervalKey) == null;
                    } else if (TokenValidationUtil.isAdminToken(token)) {
                        String intervalKey = CacheConstants.buildAdminTokenIntervalKey(userId);
                        shouldRefresh = cacheService.get(intervalKey) == null;
                    }

                    if (shouldRefresh) {
                        log.info("刷新token过期时间 - 用户: {}, token类型: {}, userId: {}",
                            user.getUsername(), TokenValidationUtil.getTokenType(token).getDescription(), userId);

                        // 刷新用户会话缓存
                        cacheService.cacheUserSession(token, userId);
                        cacheService.cacheUser(user);

                        if (TokenValidationUtil.isUserToken(token)) {
                            // 刷新用户token相关缓存
                            cacheService.cacheUserToken(userId, token);
                            cacheService.cacheTokenInterval(userId, false);
                        } else if (TokenValidationUtil.isAdminToken(token)) {
                            // 刷新管理员token相关缓存
                            cacheService.cacheAdminToken(userId, token);
                            cacheService.cacheTokenInterval(userId, true);
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.error("刷新token过期时间时发生错误: userId={}, tokenPrefix={}", user.getId(), TokenValidationUtil.getTokenPrefix(token), e);
            // 发生错误时不阻止请求继续执行
        }

        // 将用户信息设置到request attribute中，供Controller使用
        request.setAttribute("currentUser", user);
        
        return joinPoint.proceed();
    }
    
    /**
     * 限流记录管理员请求日志
     * 每个用户每分钟最多记录一次日志
     * 
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param username 用户名
     */
    private void logAdminRequestThrottled(Integer userId, String clientIp, String username) {
        long now = System.currentTimeMillis();
        Long lastLogTime = adminLogThrottle.get(userId);
        
        // 如果是第一次请求或距离上次记录超过指定间隔，则记录日志
        if (lastLogTime == null || (now - lastLogTime) >= LOG_INTERVAL_MS) {
            log.info("管理员请求 - IP: {}, 用户: {} (限流：3分钟内的其他请求已省略)", clientIp, username);
            adminLogThrottle.put(userId, now);
            
            // 定期清理过期的记录，避免内存泄漏
            // 如果缓存大小超过1000，清理5分钟前的记录
            if (adminLogThrottle.size() > 1000) {
                long fiveMinutesAgo = now - TimeUnit.MINUTES.toMillis(5);
                adminLogThrottle.entrySet().removeIf(entry -> entry.getValue() < fiveMinutesAgo);
            }
        }
    }

    private void recordSecurityEvent(String action, User user, String summary, String requestURI,
                                     String reason, Integer requiredUserType, Integer actualUserType) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", reason);
            detail.put("requestUri", requestURI);
            detail.put("requiredUserType", requiredUserType);
            detail.put("actualUserType", actualUserType);
            sysAuditLogService.recordSecurity(action, false,
                    user == null ? null : user.getUsername(),
                    user == null ? null : user.getId(),
                    user == null ? null : user.getUsername(),
                    summary,
                    detail);
        } catch (Exception e) {
            log.debug("记录安全审计日志失败: action={}, error={}", action, e.getMessage());
        }
    }
}
