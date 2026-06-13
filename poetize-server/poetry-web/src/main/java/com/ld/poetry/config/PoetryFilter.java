package com.ld.poetry.config;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.utils.storage.FileFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class PoetryFilter extends OncePerRequestFilter {

    @Autowired
    private FileFilter fileFilter;

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        if (!"OPTIONS".equals(httpServletRequest.getMethod())) {
            // 页面访问量统计由前端 sendBeacon 通过 /api/track/pageview 接口完成
            // PoetryFilter 不负责记录页面访问

            if (fileFilter.doFilterFile(httpServletRequest, httpServletResponse)) {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpServletResponse.setContentType("application/json;charset=UTF-8");
                httpServletResponse.getWriter().write(JsonUtils.toJsonString(com.ld.poetry.config.PoetryResult.fail(CodeMsg.PARAMETER_ERROR.getCode(), CodeMsg.PARAMETER_ERROR.getMsg())));
                return;
            }
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}
