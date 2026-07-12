package com.ld.poetry.utils;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.config.AsyncUserContext;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.cache.UserCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.PostConstruct;
import java.util.List;

import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.enums.PoetryEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Component
@Slf4j
public class PoetryUtil {

    @Autowired
    private UserCacheManager userCacheManager;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthCookieUtil authCookieUtil;

    private static UserCacheManager staticUserCacheManager;
    private static CacheService staticCacheService;
    private static UserMapper staticUserMapper;
    private static AuthCookieUtil staticAuthCookieUtil;

    @PostConstruct
    public void init() {
        staticUserCacheManager = userCacheManager;
        staticCacheService = cacheService;
        staticUserMapper = userMapper;
        staticAuthCookieUtil = authCookieUtil;
    }

    public static HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (IllegalStateException e) {
            // 在异步线程中RequestContextHolder可能不可用
            return null;
        }
    }

    public static void checkEmail() {
        // 优先从UserCacheManager获取，降级到Redis缓存
        User user = null;
        String token = PoetryUtil.getToken();

        if (staticUserCacheManager != null) {
            user = staticUserCacheManager.getUserByToken(token);
        }

        if (user == null && staticCacheService != null) {
            Integer userId = staticCacheService.getUserIdFromSession(token);
            if (userId != null) {
                user = staticCacheService.getCachedUser(userId);
            }
        }

        // 如果所有缓存都无法获取用户信息，记录日志
        if (user == null) {
            log.debug("无法从缓存获取用户信息，token: {}", token != null ? "已提供" : "未提供");
        }

        if (user != null && !StringUtils.hasText(user.getEmail())) {
            throw new PoetryRuntimeException("请先绑定邮箱！");
        }
    }

    public static String getToken() {
        // 首先尝试从异步上下文获取Token
        String asyncToken = AsyncUserContext.getToken();
        if (StringUtils.hasText(asyncToken)) {
            // 验证异步上下文中的token格式
            if (!TokenValidationUtil.isReasonableLength(asyncToken)) {
                log.warn("异步上下文中的token长度异常，可能存在安全风险");
                return null;
            }
            return asyncToken;
        }

        // 如果异步上下文中没有，尝试从请求中获取
        HttpServletRequest request = getRequest();
        if (request != null) {
            String token = staticAuthCookieUtil != null ? staticAuthCookieUtil.resolveToken(request) : null;
            if (!StringUtils.hasText(token) || "null".equals(token)) {
                return null;
            }

            // 验证从请求头获取的token格式
            if (!TokenValidationUtil.isReasonableLength(token)) {
                String clientIp = getIpAddr(request);
                TokenValidationUtil.logSuspiciousTokenActivity(token, "token长度异常", clientIp);
                return null;
            }

            return token;
        }

        return null;
    }

    public static String getTokenWithoutBearer() {
        String token = getToken();
        if (token != null && token.startsWith("Bearer ")) {
            String actualToken = token.substring(7);
            // 验证去除Bearer前缀后的token格式
            if (!TokenValidationUtil.isValidToken(actualToken)) {
                HttpServletRequest request = getRequest();
                String clientIp = request != null ? getIpAddr(request) : "unknown";
                TokenValidationUtil.logTokenValidationFailure(actualToken, "Bearer token格式无效", clientIp);
                return null;
            }
            return actualToken;
        }

        // 验证不带Bearer前缀的token格式
        if (token != null && !TokenValidationUtil.isValidToken(token)) {
            HttpServletRequest request = getRequest();
            String clientIp = request != null ? getIpAddr(request) : "unknown";
            TokenValidationUtil.logTokenValidationFailure(token, "token格式无效", clientIp);
            return null;
        }

        return token;
    }
    
    /**
     * 获取当前用户Token，如果获取不到则抛出异常
     * 适用于必须要求用户登录的场景
     * @return 当前用户Token
     * @throws PoetryRuntimeException 如果用户未登录或获取Token失败
     */
    public static String getTokenRequired() {
        String token = getToken();
        if (!StringUtils.hasText(token)) {
            throw new PoetryRuntimeException("用户未登录或Token获取失败，请重新登录");
        }
        return token;
    }

    public static User getCurrentUser() {
        return RetryUtil.executeWithRetry(() -> {
            // 首先尝试从异步上下文获取用户
            User asyncUser = AsyncUserContext.getUser();
            if (asyncUser != null) {
                return asyncUser;
            }
            
            // 如果异步上下文中没有，尝试通过Token从Redis缓存获取
            String token = getTokenWithoutBearer();
            if (StringUtils.hasText(token)) {
                // 统一使用用户缓存管理器获取用户信息（已重构为Redis缓存）
                if (staticUserCacheManager != null) {
                    User user = staticUserCacheManager.getUserByToken(token);
                    if (user != null) {
                        return user;
                    }
                }

                // 备用方案：直接从Redis缓存获取
                if (staticCacheService != null) {
                    Integer userId = staticCacheService.getUserIdFromSession(token);
                    if (userId != null) {
                        User user = staticCacheService.getCachedUser(userId);
                        if (user != null) {
                            return user;
                        }
                    }
                }
            }
            
            return null;
        }, 2, 50, "获取当前用户");
    }
    
    /**
     * 获取当前用户，如果获取不到则抛出异常
     * 适用于必须要求用户登录的场景
     * @return 当前用户
     * @throws PoetryRuntimeException 如果用户未登录或获取用户信息失败
     */
    public static User getCurrentUserRequired() {
        User user = getCurrentUser();
        if (user == null) {
            throw new PoetryRuntimeException("用户未登录或用户信息获取失败，请重新登录");
        }
        return user;
    }

    public static User getAdminUser() {
        // 优先从Redis缓存获取管理员信息
        if (staticCacheService != null) {
            User admin = staticCacheService.getCachedAdminUser();
            if (admin != null) {
                return admin;
            }
        }

        // 如果Redis缓存没有，从数据库回退查询
        if (staticUserMapper != null) {
            try {
                LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(User::getUserType, PoetryEnum.USER_TYPE_ADMIN.getCode());
                User admin = staticUserMapper.selectOne(queryWrapper);
                if (admin != null) {
                    // 重新缓存到Redis
                    if (staticCacheService != null) {
                        staticCacheService.cacheAdminUser(admin);
                        log.info("管理员用户从数据库重新加载并缓存: {}", admin.getId());
                    }
                    return admin;
                }
            } catch (Exception e) {
                log.error("从数据库查询管理员用户失败", e);
            }
        }

        log.warn("无法获取管理员用户信息，Redis缓存和数据库查询均失败");
        return null;
    }

    /**
     * 判断当前用户是否为管理员(Boss)
     * @return 是否为管理员
     */
    public static boolean isBoss() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        return currentUser.getUserType() != null && currentUser.getUserType() == 0; // 0 = 管理员
    }

    /** 站长(0)或管理员(1) */
    public static boolean isStaff() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        Integer userType = currentUser.getUserType();
        return userType != null && userType <= 1;
    }

    public static Integer getUserId() {
        return RetryUtil.executeWithRetry(() -> {
            try {
                // 首先尝试从异步上下文获取用户ID
                Integer asyncUserId = AsyncUserContext.getCurrentUserId();
                if (asyncUserId != null) {
                    return asyncUserId;
                }
                
                // 尝试获取token（不带Bearer前缀）
                String tokenWithoutBearer = getTokenWithoutBearer();
                if (StringUtils.hasText(tokenWithoutBearer)) {
                    // 统一使用用户缓存管理器获取用户信息（已重构为Redis缓存）
                    if (staticUserCacheManager != null) {
                        User user = staticUserCacheManager.getUserByToken(tokenWithoutBearer);
                        if (user != null) {
                            return user.getId();
                        }
                    }

                    // 备用方案：直接从Redis缓存获取
                    if (staticCacheService != null) {
                        Integer userId = staticCacheService.getUserIdFromSession(tokenWithoutBearer);
                        if (userId != null) {
                            return userId;
                        }
                    }
                    
                    // 如果缓存中都没有，尝试直接从安全token中提取用户ID
                    Integer userIdFromToken = TokenValidationUtil.extractUserId(tokenWithoutBearer);
                    if (userIdFromToken != null) {
                        return userIdFromToken;
                    }

                    // 如果直接获取失败，检查是否为管理员token - 使用安全的验证方法
                    if (TokenValidationUtil.isAdminToken(tokenWithoutBearer)) {
                        User adminUser = getAdminUser();
                        if (adminUser != null) {
                            return adminUser.getId();
                        }
                    }
                }
                
                // 如果获取不到，尝试从请求属性获取
                HttpServletRequest request = getRequest();
                if (request != null) {
                    // 从请求属性中获取用户ID
                    Object userIdAttr = request.getAttribute("userId");
                    if (userIdAttr != null) {
                        try {
                            return Integer.parseInt(userIdAttr.toString());
                        } catch (NumberFormatException e) {
                            // 忽略转换异常
                        }
                    }
                }
                
                return null;
                
            } catch (PoetryRuntimeException e) {
                // 重新抛出业务异常
                throw e;
            } catch (Exception e) {
                // 捕获所有其他异常，避免认证失败导致整个请求失败
                log.error("获取用户ID时发生异常: {}", e.getMessage(), e);
                return null;
            }
        }, 2, 50, "获取用户ID");
    }
    
    /**
     * 获取当前用户ID，如果获取不到则抛出异常
     * 适用于必须要求用户登录的场景
     * @return 当前用户ID
     * @throws PoetryRuntimeException 如果用户未登录或获取用户ID失败
     */
    public static Integer getUserIdRequired() {
        Integer userId = getUserId();
        if (userId == null) {
            throw new PoetryRuntimeException("用户未登录或用户ID获取失败，请重新登录");
        }
        return userId;
    }

    public static String getUsername() {
        return RetryUtil.executeWithRetry(() -> {
            // 首先尝试从异步上下文获取用户名
            String asyncUsername = AsyncUserContext.getCurrentUsername();
            if (StringUtils.hasText(asyncUsername)) {
                return asyncUsername;
            }
            
            // 如果异步上下文中没有，尝试通过Token从缓存获取
            String token = getTokenWithoutBearer();
            if (StringUtils.hasText(token)) {
                // 使用用户缓存管理器获取用户信息
                if (staticUserCacheManager != null) {
                    User user = staticUserCacheManager.getUserByToken(token);
                    if (user != null && StringUtils.hasText(user.getUsername())) {
                        return user.getUsername();
                    }
                }
                
                // 降级到Redis缓存获取
                if (staticCacheService != null) {
                    Integer userId = staticCacheService.getUserIdFromSession(token);
                    if (userId != null) {
                        User user = staticCacheService.getCachedUser(userId);
                        if (user != null && StringUtils.hasText(user.getUsername())) {
                            return user.getUsername();
                        }
                    }
                }

                // 如果Redis缓存也没有，返回null
            }
            
            return null;
        }, 2, 50, "获取用户名");
    }

    public static String getRandomAvatar(String key) {
        // 优先从Redis缓存获取网站信息
        WebInfo webInfo = null;
        if (staticCacheService != null) {
            webInfo = staticCacheService.getCachedWebInfo();
        }

        // 如果无法获取网站信息，记录日志
        if (webInfo == null) {
            log.debug("无法从缓存获取网站信息用于随机头像");
        }
        if (webInfo != null) {
            String randomAvatar = webInfo.getRandomAvatar();
            List<String> randomAvatars = JsonUtils.parseArray(randomAvatar, String.class);
            if (!CollectionUtils.isEmpty(randomAvatars)) {
                if (StringUtils.hasText(key)) {
                    return randomAvatars.get(PoetryUtil.hashLocation(key, randomAvatars.size()));
                } else {
                    String ipAddr = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    if (StringUtils.hasText(ipAddr)) {
                        return randomAvatars.get(PoetryUtil.hashLocation(ipAddr, randomAvatars.size()));
                    } else {
                        return randomAvatars.get(0);
                    }
                }
            }
        }
        return null;
    }

    public static String getRandomName(String key) {
        // 优先从Redis缓存获取网站信息
        WebInfo webInfo = null;
        if (staticCacheService != null) {
            webInfo = staticCacheService.getCachedWebInfo();
        }

        // 如果无法获取网站信息，记录日志
        if (webInfo == null) {
            log.debug("无法从缓存获取网站信息用于随机名称");
        }
        if (webInfo != null) {
            String randomName = webInfo.getRandomName();
            List<String> randomNames = JsonUtils.parseArray(randomName, String.class);
            if (!CollectionUtils.isEmpty(randomNames)) {
                if (StringUtils.hasText(key)) {
                    return randomNames.get(PoetryUtil.hashLocation(key, randomNames.size()));
                } else {
                    String ipAddr = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    if (StringUtils.hasText(ipAddr)) {
                        return randomNames.get(PoetryUtil.hashLocation(ipAddr, randomNames.size()));
                    } else {
                        return randomNames.get(0);
                    }
                }
            }
        }
        return null;
    }

    public static String getRandomCover(String key) {
        // 优先从Redis缓存获取网站信息
        WebInfo webInfo = null;
        if (staticCacheService != null) {
            webInfo = staticCacheService.getCachedWebInfo();
        }

        // 如果Redis缓存也没有，使用默认值
        if (webInfo == null) {
            log.warn("无法获取网站信息，使用默认随机封面");
        }
        if (webInfo != null) {
            String randomCover = webInfo.getRandomCover();
            List<String> randomCovers = JsonUtils.parseArray(randomCover, String.class);
            if (!CollectionUtils.isEmpty(randomCovers)) {
                if (StringUtils.hasText(key)) {
                    return randomCovers.get(PoetryUtil.hashLocation(key, randomCovers.size()));
                } else {
                    String ipAddr = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    if (StringUtils.hasText(ipAddr)) {
                        return randomCovers.get(PoetryUtil.hashLocation(ipAddr, randomCovers.size()));
                    } else {
                        return randomCovers.get(0);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取客户端IP地址
     * 使用增强的IP获取工具，提供更好的容错和监控能力
     */
    public static String getIpAddr(HttpServletRequest request) {
        return IpUtil.getClientRealIp(request);
    }
    
    /**
     * 获取客户端IP地址（兼容旧版本方法名）
     * @deprecated 建议使用 getIpAddr(HttpServletRequest request)
     */
    @Deprecated
    public static String getClientIp(HttpServletRequest request) {
        return getIpAddr(request);
    }
    
    /**
     * 获取当前请求的客户端IP地址
     */
    public static String getCurrentClientIp() {
        HttpServletRequest request = getRequest();
        return getIpAddr(request);
    }
    
    /**
     * 验证IP地址格式是否正确
     */
    public static boolean isValidIp(String ip) {
        return IpUtil.isValidIpFormat(ip);
    }
    
    /**
     * 判断是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        return IpUtil.isInternalIp(ip);
    }
    
    /**
     * 获取IP获取统计信息
     */
    public static String getIpStatistics() {
        return IpUtil.getIpStatistics();
    }

    public static int hashLocation(String key, int length) {
        int h = key.hashCode();
        return (h ^ (h >>> 16)) & (length - 1);
    }
}
