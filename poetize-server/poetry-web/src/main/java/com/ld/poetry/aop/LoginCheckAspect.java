package com.ld.poetry.aop;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.handle.PoetryLoginException;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.cache.PoetryCache;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;


@Aspect
@Component
@Order(0)
@Slf4j
public class LoginCheckAspect {

    @Around("@annotation(loginCheck)")
    public Object around(ProceedingJoinPoint joinPoint, LoginCheck loginCheck) throws Throwable {
        // 检查是否来自内部服务的请求
        HttpServletRequest request = PoetryUtil.getRequest();
        String adminFlag = request.getHeader("X-Admin-Request");
        String internalService = request.getHeader("X-Internal-Service");
        
        // 如果是内部服务请求，直接通过
        if ("true".equals(adminFlag) && StringUtils.hasText(internalService)) {
            log.info("内部服务请求通过认证检查 - 服务: {}, IP: {}", 
                     internalService, PoetryUtil.getIpAddr(request));
            return joinPoint.proceed();
        }
        
        String token = PoetryUtil.getTokenWithoutBearer();
        if (!StringUtils.hasText(token)) {
            log.warn("Token为空，用户未登录");
            throw new PoetryLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        User user = (User) PoetryCache.get(token);
        if (user == null) {
            log.warn("Token无效或已过期: {}", token);
            throw new PoetryLoginException(CodeMsg.LOGIN_EXPIRED.getMsg());
        }

        // 验证token类型和权限
        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            if (loginCheck.value() == PoetryEnum.USER_TYPE_ADMIN.getCode() || loginCheck.value() == PoetryEnum.USER_TYPE_DEV.getCode()) {
                log.warn("普通用户尝试访问管理员接口: {}", user.getUsername());
                return PoetryResult.fail("请输入管理员账号！");
            }
        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
            log.info("管理员请求 - IP: {}, 用户: {}", PoetryUtil.getIpAddr(PoetryUtil.getRequest()), user.getUsername());
            if (loginCheck.value() == PoetryEnum.USER_TYPE_ADMIN.getCode() && user.getId().intValue() != CommonConst.ADMIN_USER_ID) {
                log.warn("非超级管理员尝试访问超级管理员接口: {}", user.getUsername());
                return PoetryResult.fail("请输入管理员账号！");
            }
        } else {
            log.warn("无效的token类型: {}", token);
            throw new PoetryLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        if (loginCheck.value() < user.getUserType()) {
            log.warn("用户权限不足 - 需要权限: {}, 当前权限: {}", loginCheck.value(), user.getUserType());
            throw new PoetryRuntimeException("权限不足！");
        }

        // 重置过期时间
        String userId = user.getId().toString();
        boolean needRefresh = false;
        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            needRefresh = PoetryCache.get(CommonConst.USER_TOKEN_INTERVAL + userId) == null;
        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
            needRefresh = PoetryCache.get(CommonConst.ADMIN_TOKEN_INTERVAL + userId) == null;
        }

        if (needRefresh) {
            synchronized (userId.intern()) {
                boolean shouldRefresh = false;
                if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
                    shouldRefresh = PoetryCache.get(CommonConst.USER_TOKEN_INTERVAL + userId) == null;
                } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
                    shouldRefresh = PoetryCache.get(CommonConst.ADMIN_TOKEN_INTERVAL + userId) == null;
                }

                if (shouldRefresh) {
                    log.info("刷新token过期时间 - 用户: {}", user.getUsername());
                    PoetryCache.put(token, user, CommonConst.TOKEN_EXPIRE);
                    if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
                        PoetryCache.put(CommonConst.USER_TOKEN + userId, token, CommonConst.TOKEN_EXPIRE);
                        PoetryCache.put(CommonConst.USER_TOKEN_INTERVAL + userId, token, CommonConst.TOKEN_INTERVAL);
                    } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
                        PoetryCache.put(CommonConst.ADMIN_TOKEN + userId, token, CommonConst.TOKEN_EXPIRE);
                        PoetryCache.put(CommonConst.ADMIN_TOKEN_INTERVAL + userId, token, CommonConst.TOKEN_INTERVAL);
                    }
                }
            }
        }
        return joinPoint.proceed();
    }
}
