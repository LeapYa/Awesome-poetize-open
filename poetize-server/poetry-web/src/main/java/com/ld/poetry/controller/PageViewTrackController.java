package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.utils.CommonQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 页面访问量追踪控制器
 * <p>
 * 前端 router.afterEach 通过 sendBeacon 上报每次页面访问。
 * 黑名单排除 + 爬虫排除。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/track")
public class PageViewTrackController {

    @Autowired
    private CommonQuery commonQuery;

    /** 爬虫 User-Agent 关键词 */
    private static final String[] BOT_KEYWORDS = {
            "bot", "spider", "crawler", "slurp", "curl", "wget",
            "python", "java", "go-http", "httpclient", "scrapy",
            "let's encrypt", "semrush", "ahrefs", "mj12bot"
    };

    /** 需要排除的路径前缀——按首字符分桶，避免全量遍历 */
    private static final Map<Character, String[]> EXCLUDED_PREFIX_BUCKETS;

    static {
        // 所有需要排除的前缀
        String[] allPrefixes = {
                "/api/", "/admin",
                "/webInfo", "/sysConfig", "/sysPlugin", "/resource",
                "/imageCompress", "/captcha", "/comment", "/family",
                "/qiniu", "/qrcode", "/imChat", "/collect", "/actuator",
                "/static/", "/css/", "/js/", "/images/", "/assets/", "/libs/",
                "/seo/", "/python/", "/ws/", "/login/", "/callback/",
                "/oauth/", "/internal_proxy/", "/sitemap", "/.well-known/"
        };

        // 按第二个字符分桶（所有前缀都以 '/' 开头，用第二个字符区分）
        Map<Character, java.util.List<String>> temp = new HashMap<>();
        for (String prefix : allPrefixes) {
            char key = prefix.length() > 1 ? prefix.charAt(1) : '/';
            temp.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(prefix);
        }
        Map<Character, String[]> buckets = new HashMap<>(temp.size());
        temp.forEach((k, v) -> buckets.put(k, v.toArray(new String[0])));
        EXCLUDED_PREFIX_BUCKETS = Map.copyOf(buckets);
    }

    /** 需要精确排除的路径 */
    private static final Set<String> EXCLUDED_EXACT = Set.of(
            "/favicon.ico", "/robots.txt", "/manifest.json", "/sw.js"
    );

    /** API 路由段名：/xxx/字母开头 视为 API 调用 */
    private static final Set<String> API_ROUTE_SEGMENTS = Set.of(
            "user", "article", "weiYan", "treeHole", "comment", "sort", "label"
    );

    /** 需要排除的静态资源文件后缀 */
    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            ".js", ".css", ".map",
            ".jpg", ".jpeg", ".png", ".gif", ".ico", ".webp", ".svg",
            ".woff", ".woff2", ".ttf", ".eot",
            ".json", ".xml", ".txt", ".mp4", ".webm"
    );

    /**
     * 前端 sendBeacon 上报页面访问
     */
    @PostMapping("/pageview")
    public PoetryResult<Void> trackPageView(HttpServletRequest request,
                                             @RequestParam("path") String path) {
        if (path == null || path.isEmpty()) {
            return PoetryResult.success();
        }

        String userAgent = request.getHeader("User-Agent");
        if (isBot(userAgent)) {
            return PoetryResult.success();
        }

        // 提取纯路径并过滤
        String cleanPath = extractPath(path);
        if (!isPageVisit(cleanPath)) {
            return PoetryResult.success();
        }

        // 获取客户端 IP
        String clientIp = getClientIp(request);

        String referer = request.getHeader("Referer");
        String lang = request.getHeader("Accept-Language");

        // log.info("[PageViewTrack] ✓ IP: {} | 页面: {} | Referer: {}", clientIp, cleanPath, referer);

        try {
            commonQuery.saveHistory(clientIp, path, userAgent, referer, lang);
        } catch (Exception e) {
            log.error("[PageViewTrack] 记录失败: {}", e.getMessage());
        }

        return PoetryResult.success();
    }

    // ========== 过滤逻辑 ==========

    /**
     * 判断是否为页面访问（黑名单排除法）。
     * 排除 API、静态资源、后台管理等非页面请求，剩下的就是页面。
     * <p>
     * 性能要点：
     * - 前缀按第二个字符分桶，O(1) 定位后只遍历 2~5 个候选项
     * - 精确匹配 / 后缀匹配用 HashSet，O(1)
     * - API 路由段用 HashSet + 手动解析，避免 Pattern/Matcher 对象分配
     * - 全程零 Stream、零 lambda、零临时对象（除 substring）
     */
    private boolean isPageVisit(String path) {
        if (path == null || path.isEmpty()) return false;

        // 1. 精确匹配排除 —— HashSet O(1)
        if (EXCLUDED_EXACT.contains(path)) return false;

        // 2. 前缀排除 —— 按第二个字符分桶
        if (path.length() > 1) {
            String[] bucket = EXCLUDED_PREFIX_BUCKETS.get(path.charAt(1));
            if (bucket != null) {
                for (String prefix : bucket) {
                    if (path.startsWith(prefix)) return false;
                }
            }
        }

        // 3. 子串排除（upload / download）—— indexOf 比 contains 无差，直接内联
        if (path.indexOf("/upload/") >= 0 || path.indexOf("/download/") >= 0) return false;

        // 4. API 路由 vs 页面路由 —— 手动解析，零正则
        //    格式: /segment/X...  如果 segment ∈ API_ROUTE_SEGMENTS 且 X 是字母 → API
        if (path.length() > 2 && path.charAt(0) == '/') {
            int secondSlash = path.indexOf('/', 1);
            if (secondSlash > 1 && secondSlash + 1 < path.length()) {
                char afterSlash = path.charAt(secondSlash + 1);
                if ((afterSlash >= 'a' && afterSlash <= 'z') || (afterSlash >= 'A' && afterSlash <= 'Z')) {
                    String segment = path.substring(1, secondSlash);
                    if (API_ROUTE_SEGMENTS.contains(segment)) return false;
                }
            }
        }

        // 5. 静态资源后缀排除（/index.html 除外）—— HashSet O(1)
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx > 0 && EXCLUDED_EXTENSIONS.contains(path.substring(dotIdx))) {
            return "/index.html".equals(path);
        }

        // 6. URL 编码中文路径 —— indexOf 线性扫描，但 path 通常很短
        if (path.indexOf("%E") >= 0 || path.indexOf("%e") >= 0) return false;

        return true;
    }

    private String extractPath(String uri) {
        if (uri == null || uri.isEmpty()) return "/";
        int qIdx = uri.indexOf('?');
        return qIdx > 0 ? uri.substring(0, qIdx) : uri;
    }

    private String getClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Real-IP");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getHeader("X-Forwarded-For");
            if (clientIp != null && clientIp.contains(",")) {
                clientIp = clientIp.split(",")[0].trim();
            }
        }
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }

    private boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return true;
        String uaLower = userAgent.toLowerCase();
        for (String keyword : BOT_KEYWORDS) {
            if (uaLower.contains(keyword)) return true;
        }
        return false;
    }
}
