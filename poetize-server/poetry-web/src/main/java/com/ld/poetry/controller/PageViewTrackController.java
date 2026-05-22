package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.IpUtil;
import com.ld.poetry.utils.PageVisitUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 页面访问量追踪控制器
 * <p>
 * 前端 router.afterEach 上报 SPA 路由访问，Nginx 日志消费会补充直接页面访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/track")
public class PageViewTrackController {

    @Autowired
    private CommonQuery commonQuery;

    /**
     * 前端上报页面访问
     */
    @PostMapping("/pageview")
    public PoetryResult<Void> trackPageView(HttpServletRequest request,
                                             @RequestParam("path") String path) {
        if (path == null || path.isEmpty()) {
            return PoetryResult.success();
        }

        String userAgent = request.getHeader("User-Agent");
        String cleanPath = PageVisitUtils.normalizeVisitUri(path);
        if (!PageVisitUtils.isPageVisit(cleanPath)) {
            return PoetryResult.success();
        }

        // 获取客户端 IP
        String clientIp = IpUtil.getClientPublicIp(request);

        String referer = request.getHeader("Referer");
        String lang = request.getHeader("Accept-Language");

        // log.info("[PageViewTrack] ✓ IP: {} | 页面: {} | Referer: {}", clientIp, cleanPath, referer);

        try {
            commonQuery.saveHistory(clientIp, cleanPath, userAgent, referer, lang);
        } catch (Exception e) {
            log.error("[PageViewTrack] 记录失败: {}", e.getMessage());
        }

        return PoetryResult.success();
    }
}
