package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.IpUtil;
import com.ld.poetry.utils.PageVisitUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

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
        Map<String, Object> uaSignals = buildUaSignals(request);

        // log.info("[PageViewTrack] ✓ IP: {} | 页面: {} | Referer: {}", clientIp, cleanPath, referer);

        try {
            commonQuery.saveHistory(clientIp, cleanPath, userAgent, referer, lang, uaSignals);
        } catch (Exception e) {
            log.error("[PageViewTrack] 记录失败: {}", e.getMessage());
        }

        return PoetryResult.success();
    }

    private Map<String, Object> buildUaSignals(HttpServletRequest request) {
        Map<String, Object> signals = new HashMap<>();
        signals.put("visitSource", "track");
        signals.put("headerSnapshot", "true");
        putHeader(signals, request, "accept", "Accept");
        putHeader(signals, request, "acceptLanguage", "Accept-Language");
        putHeader(signals, request, "secFetchSite", "Sec-Fetch-Site");
        putHeader(signals, request, "secFetchMode", "Sec-Fetch-Mode");
        putHeader(signals, request, "secFetchDest", "Sec-Fetch-Dest");
        putHeader(signals, request, "secFetchUser", "Sec-Fetch-User");
        putHeader(signals, request, "secChUa", "Sec-CH-UA");
        putHeader(signals, request, "secChUaPlatform", "Sec-CH-UA-Platform");
        putHeader(signals, request, "upgradeInsecureRequests", "Upgrade-Insecure-Requests");
        putParam(signals, request, "webdriver", "wd");
        putParam(signals, request, "pluginCount", "pl");
        putParam(signals, request, "languageCount", "lg");
        putParam(signals, request, "hardwareConcurrency", "hc");
        putParam(signals, request, "maxTouchPoints", "tp");
        putParam(signals, request, "platform", "pf");
        putParam(signals, request, "deviceMemory", "dm");
        putParam(signals, request, "timezone", "tz");
        putParam(signals, request, "screenWidth", "sw");
        putParam(signals, request, "screenHeight", "sh");
        putParam(signals, request, "colorDepth", "cd");
        putParam(signals, request, "webdriverType", "wdt");
        putParam(signals, request, "automationScore", "as");
        putParam(signals, request, "automationVerdict", "av");
        putParam(signals, request, "automationSignals", "af");
        putParam(signals, request, "permissionsQueryNative", "pqn");
        putParam(signals, request, "pluginsItemNative", "pin");
        putParam(signals, request, "webdriverDescriptor", "wdd");
        putParam(signals, request, "webglVendor", "glv");
        putParam(signals, request, "webglRenderer", "glr");
        return signals;
    }

    private void putHeader(Map<String, Object> signals, HttpServletRequest request, String key, String header) {
        String value = request.getHeader(header);
        if (value != null && !value.isBlank()) {
            signals.put(key, value.trim());
        }
    }

    private void putParam(Map<String, Object> signals, HttpServletRequest request, String key, String param) {
        String value = request.getParameter(param);
        if (value != null && !value.isBlank()) {
            signals.put(key, value.trim());
        }
    }
}
