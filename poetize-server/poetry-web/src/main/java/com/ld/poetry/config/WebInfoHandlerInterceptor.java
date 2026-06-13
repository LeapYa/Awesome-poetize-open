package com.ld.poetry.config;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class WebInfoHandlerInterceptor implements HandlerInterceptor {

    @Autowired
    private CacheService cacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            // 检查CacheService是否正确注入
            if (cacheService == null) {
                log.error("CacheService未正确注入到WebInfoHandlerInterceptor中");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(JsonUtils.toJsonString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), "系统初始化中，请稍后重试")));
                return false;
            }

            // 使用CacheService获取网站信息
            // 注意：我们已经修改了CacheService.getCachedWebInfo()方法
            // 当缓存不存在时，它会自动从数据库加载
            WebInfo webInfo = cacheService.getCachedWebInfo();

            // 如果webInfo仍然为null，说明数据库中也没有数据
            if (webInfo == null) {
                log.error("无法获取网站信息，缓存和数据库中均不存在");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(JsonUtils.toJsonString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), CodeMsg.SYSTEM_REPAIR.getMsg())));
                return false;
            }

            // 检查网站状态
            if (!webInfo.getStatus()) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(JsonUtils.toJsonString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), CodeMsg.SYSTEM_REPAIR.getMsg())));
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error("WebInfoHandlerInterceptor处理请求时发生异常", e);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JsonUtils.toJsonString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), "系统发生错误，请稍后重试")));
            return false;
        }
    }
}
