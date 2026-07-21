package com.ld.poetry.config;

import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.provider.Ip2RegionProvider;
import com.ld.poetry.utils.IpUtil;
import com.ld.poetry.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 安全过滤器 - 拦截常见的恶意扫描请求并实现IP拉黑
 * 记录恶意IP，达到阈值后自动拉黑一段时间
 */
@Component
@Slf4j
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private Ip2RegionProvider ip2RegionProvider;

    // 攻击次数阈值 - 超过此次数将被拉黑
    private static final int ATTACK_THRESHOLD = 3;

    // 拉黑时长（小时）
    private static final int BLACKLIST_DURATION_HOURS = 24;

    // 明确的恶意扫描路径黑名单
    private static final Set<String> MALICIOUS_PATHS = Set.of(
            "/.env", "/.env.local", "/.env.production",
            "/.git", "/.git/config",
            "/phpmyadmin", "/pma",
            "/wp-admin", "/wp-login.php", "/wp-config.php",
            "/config.php", "/database.php", "/xmlrpc.php",
            "/.aws", "/.docker", "/docker-compose.yml", "/Dockerfile",
            "/.DS_Store",
            "/admin.php", "/admin/login.php", "/administrator.php",
            "/manager.php", "/console.php", "/debug.php",
            "/test.php", "/info.php", "/phpinfo.php",
            "/sql.php", "/backup.sql", "/database.sql",
            "/.htaccess", "/.htpasswd",
            "/web.config", "/server.xml",
            "/application.properties", "/application.yml",
            "/build.sh", "/index.html",
            "/translation/test-summary",
            "/translation/definite_notexist_path",
            "/definite_notexist_path");

    // 敏感文件扩展名（精确匹配后缀）
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
            ".sql", ".bak", ".backup", ".old", ".tmp", ".log");

    // 常见 CMS / 框架路径特征（包含匹配）
    private static final Set<String> CMS_PATTERNS = Set.of(
            "/wp-", "/wordpress", "/drupal", "/joomla", "/magento",
            "/administrator.php", "/admin/login.php", "/phpmyadmin", "/xmlrpc",
            "/blog/wp-", "/cms/", "/old/", "/new/", "/backup/", "/bak/",
            "/beta/", "/temp/", "/dev/");

    // 拦截的 AI 爬虫 UA 关键词（小写匹配），单一数据源见 CacheConstants.BUILTIN_AI_CRAWLER_UA
    // 只拦截训练数据采集爬虫，保留搜索索引和用户实时引用爬虫
    private static final Set<String> BLOCKED_AI_CRAWLER_UA = Set.copyOf(CacheConstants.BUILTIN_AI_CRAWLER_UA);

    // 自动化工具 UA 关键词（小写 contains 匹配），单一数据源见 CacheConstants.BUILTIN_AUTOMATION_UA
    // 这些关键词只会出现在自动化工具声明的 UA 中，正常浏览器 UA 不会包含，无需 token 边界匹配
    private static final List<String> AUTOMATION_UA_KEYWORDS = CacheConstants.BUILTIN_AUTOMATION_UA;

    // ================================ 扩展封禁规则内存缓存 ================================
    // volatile 保证多线程可见性；定期从 Redis 刷新，避免每请求扫描 Redis
    private volatile List<Map<String, Object>> uaRules = List.of();
    private volatile List<Map<String, Object>> cidrRules = List.of();
    private volatile List<Map<String, Object>> regionRules = List.of();
    // 被管理员禁用的内置 AI 爬虫硬名单（小写），命中则在 isBlockedAiCrawler 中跳过
    private volatile Set<String> disabledAiCrawlerUa = Set.of();
    // 被管理员禁用的内置自动化工具 UA 硬名单（小写），命中则在 isAutomationToolUa 中跳过
    private volatile Set<String> disabledAutomationUa = Set.of();
    private volatile long lastRefreshAt = 0;
    private static final long REFRESH_INTERVAL_MS = 15000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String clientIP = getClientIpAddress(request);

        // 清理过期的拉黑记录和攻击计数
        cleanupExpiredRecords();

        // 检查IP是否被拉黑
        if (isIPBlacklisted(clientIP)) {
            log.warn("拒绝已拉黑IP的访问: {} from IP: {}", requestURI, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - IP Blacklisted");
            return;
        }

        // 检查自动化浏览器拦截（探针上报判定 score >= 70 时写入）
        if (isAutomationBlocked(clientIP)) {
            log.warn("拒绝自动化浏览器访问: {} from IP: {}", requestURI, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - Automation Detected");
            return;
        }

        // CIDR/UA/Region 规则定期刷新（Nginx 与 Java 共用同一份 Redis 规则快照）
        refreshBanRulesIfStale();

        // 解析 IP 地区供 Java 层兜底拦截使用。
        // Nginx ban_check.lua 已改用 lua_c xdb_searcher 直接查 xdb 文件做地区封禁，不再依赖 Redis 缓存。
        // Java 层作为 Nginx 之后的第二道拦截，仍需解析地区做兜底。
        String[] resolvedRegion = null;
        if (!regionRules.isEmpty()) {
            resolvedRegion = resolveIpRegion(clientIP);
        }

        // 拦截指定 AI 爬虫（基于 UA 关键词，不依赖探针上报）
        String userAgent = request.getHeader("User-Agent");
        if (isBlockedAiCrawler(userAgent)) {
            log.info("拦截 AI 爬虫: {} from IP: {}", userAgent, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - AI Crawler Blocked");
            return;
        }

        // 拦截自动化工具（UA 中明确声明了自动化工具标识，不依赖前端探针 JS 信号）
        if (isAutomationToolUa(userAgent)) {
            log.info("拦截自动化工具: {} from IP: {}", userAgent, clientIP);
            // 写入封禁缓存，后续同 IP 请求走 isAutomationBlocked 快速路径，无需重复解析 UA
            String blockKey = CacheConstants.buildAutomationBlockKey(clientIP);
            cacheService.set(blockKey, "UA声明自动化工具",
                    CacheConstants.AUTOMATION_BLOCK_EXPIRE_TIME);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - Automation Detected");
            return;
        }

        // CIDR/UA/Region 封禁（始终启用，作为 Nginx 之后的第二道拦截）
        // 生产环境 Nginx ban_check.lua 在 access 阶段拦截，请求正常不会到达此处；
        // 本地开发（无 Nginx）或 Nginx 规则刷新延迟内，Java 层兜底。
        if (!cidrRules.isEmpty() && isCidrBanned(clientIP)) {
            log.warn("拒绝CIDR网段封禁访问: {} from IP: {}", requestURI, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - CIDR Blacklisted");
            return;
        }
        if (!uaRules.isEmpty() && isUaBannedByAdmin(userAgent)) {
            log.warn("拒绝管理员UA封禁访问: {} from IP: {}", requestURI, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - UA Blacklisted");
            return;
        }
        if (resolvedRegion != null && isRegionBannedByRules(resolvedRegion)) {
            log.warn("拒绝地区封禁访问: {} from IP: {}", requestURI, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - Region Blacklisted");
            return;
        }

        boolean isMaliciousRequest = false;
        String attackType = "";

        // 检查是否为恶意扫描路径
        if (MALICIOUS_PATHS.contains(requestURI)) {
            isMaliciousRequest = true;
            attackType = "恶意路径扫描";
        }
        // 检查是否包含恶意扫描特征
        else if (containsMaliciousPattern(requestURI)) {
            isMaliciousRequest = true;
            attackType = "可疑请求特征";
        }
        // 检查无参数的敏感API调用
        else if (isInvalidApiCall(request)) {
            isMaliciousRequest = true;
            attackType = "恶意API探测";
        }

        if (isMaliciousRequest) {
            // 统计拦截请求总数 - 使用Redis计数器
            long blocked = redisUtil.incr(CacheConstants.CACHE_PREFIX + "security:blocked:total", 1);

            // 每100次拦截记录一次统计信息
            if (blocked % 100 == 0) {
                log.info("安全过滤器已累计拦截 {} 次恶意请求", blocked);
            }

            // 记录攻击并检查是否需要拉黑
            recordAttackAndCheckBlacklist(clientIP, requestURI, attackType);

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("404 Not Found");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 检查是否包含恶意模式
     *
     * <p>
     * 将检测逻辑拆分为语义独立的子方法，便于单独测试和维护。
     * 检测顺序：路径遍历 → 敏感扩展名 → PHP 特征 → CMS 路径 → 注入攻击
     */
    private boolean containsMaliciousPattern(String requestURI) {
        try {
            // URL 解码防止编码绕过（如 %2e%2e/ → ../）
            String decoded = URLDecoder.decode(requestURI, StandardCharsets.UTF_8);
            String lower = decoded.toLowerCase();

            return isPathTraversal(decoded)
                    || hasSensitiveExtension(lower)
                    || hasPhpSignature(lower)
                    || hasCmsPattern(lower)
                    || hasInjectionPattern(lower);

        } catch (Exception e) {
            // URL 解码失败本身即可疑（如恶意构造的畸形编码）
            log.warn("URL 解码失败，视为可疑请求: {}", requestURI);
            return true;
        }
    }

    /** 路径遍历攻击检测（../ 或 ./） */
    private boolean isPathTraversal(String decoded) {
        return decoded.contains("..") || decoded.contains("./");
    }

    /** 敏感文件扩展名检测（.sql/.bak/.log 等备份/日志文件） */
    private boolean hasSensitiveExtension(String lower) {
        return SENSITIVE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /**
     * PHP 相关恶意路径特征检测
     * 注：已知翻译探测路径也归入此类处理
     */
    private boolean hasPhpSignature(String lower) {
        // 常见 PHP 文件特征
        if (lower.contains("/wp-") || lower.contains(".php.")
                || lower.contains("admin.php") || lower.contains("login.php")
                || lower.contains("config.php") || lower.contains("phpinfo")) {
            return true;
        }
        // 已知的翻译模型路径探测
        if (lower.equals("/translation/test-summary")
                || lower.equals("/translation/definite_notexist_path")) {
            return true;
        }
        // 通用恶意探测关键词
        return lower.contains("notexist")
                || lower.contains("scanner")
                || lower.contains("probe");
    }

    /** 常见 CMS / 框架路径扫描检测 */
    private boolean hasCmsPattern(String lower) {
        return CMS_PATTERNS.stream().anyMatch(lower::contains);
    }

    /**
     * 注入攻击检测：XSS + SQL 注入
     *
     * <p>
     * 使用 switch 表达式枚举 SQL 关键词对，逐对检测。
     */
    private boolean hasInjectionPattern(String lower) {
        // XSS / 脚本注入
        if (lower.contains("<script") || lower.contains("javascript:")
                || lower.contains("eval(") || lower.contains("base64_decode")) {
            return true;
        }

        // SQL 注入关键词对检测（switch 表达式）
        record SqlPair(String kw1, String kw2) {
        }
        var sqlPairs = new SqlPair[] {
                new SqlPair("union", "select"),
                new SqlPair("drop", "table"),
                new SqlPair("insert", "into"),
                new SqlPair("delete", "from"),
                new SqlPair("update", "set"),
                new SqlPair("select", "from"),
        };

        for (SqlPair pair : sqlPairs) {
            if (lower.contains(pair.kw1()) && lower.contains(pair.kw2())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取客户端真实IP地址
     * 使用增强的IP获取工具，提供更好的容错和监控能力
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = IpUtil.getClientRealIp(request);

        // 如果获取到的是unknown，在安全过滤器中使用unknown_ip以区分
        if ("unknown".equals(ip)) {
            log.warn("无法获取客户端真实IP地址，请求URI: {}, User-Agent: {}",
                    request.getRequestURI(), request.getHeader("User-Agent"));
            return "unknown_ip";
        }

        return ip;
    }

    /**
     * 检查IP是否被拉黑
     */
    private boolean isIPBlacklisted(String ip) {
        String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
        return redisUtil.hasKey(blacklistKey);
    }

    /**
     * 检查IP是否因自动化浏览器行为被拦截
     * <p>
     * 探针上报判定为高置信度自动化（score >= 70，命中 webdriver/headless/SwiftShader/
     * JS原生性异常/全局变量泄漏等硬证据）时写入缓存，2小时过期。
     */
    private boolean isAutomationBlocked(String ip) {
        String blockKey = CacheConstants.buildAutomationBlockKey(ip);
        return redisUtil.hasKey(blockKey);
    }

    /**
     * 检查 UA 是否匹配被拦截的 AI 爬虫关键词
     * 使用 token 边界匹配：关键词前后必须是非字母数字字符或字符串边界
     * 避免误伤 UA 中恰好包含关键词子串的正常请求（如 "mygptbotabc" 不会匹配 "gptbot"）
     */
    private boolean isBlockedAiCrawler(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        Set<String> disabled = disabledAiCrawlerUa;
        for (String pattern : BLOCKED_AI_CRAWLER_UA) {
            // 管理员已禁用（删除）的内置硬名单跳过，实现"内置 UA 弃用后可删除"
            if (disabled != null && disabled.contains(pattern)) {
                continue;
            }
            int idx = lower.indexOf(pattern);
            while (idx != -1) {
                int end = idx + pattern.length();
                // 关键词后必须是边界：字符串结束、或非字母数字字符
                boolean isBoundary = end >= lower.length()
                        || !Character.isLetterOrDigit(lower.charAt(end));
                // 关键词前也必须是边界，避免 "mygptbot" 这种误匹配
                boolean isPrefixBoundary = idx == 0
                        || !Character.isLetterOrDigit(lower.charAt(idx - 1));
                if (isBoundary && isPrefixBoundary) {
                    return true;
                }
                idx = lower.indexOf(pattern, end);
            }
        }
        return false;
    }

    /**
     * 检查 UA 是否包含自动化工具标识关键词。
     * 这些关键词（headlesschrome/playwright/puppeteer/selenium/webdriver/phantomjs）
     * 只会出现在自动化工具主动声明的 UA 中，正常浏览器不会包含，直接 contains 匹配即可。
     * 管理员已禁用（删除）的内置硬名单跳过，实现"内置 UA 弃用后可删除"。
     */
    private boolean isAutomationToolUa(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        Set<String> disabled = disabledAutomationUa;
        for (String keyword : AUTOMATION_UA_KEYWORDS) {
            if (disabled != null && disabled.contains(keyword)) {
                continue;
            }
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ================================ 扩展封禁规则缓存与匹配 ================================

    /**
     * 定期从 Redis 刷新 UA/CIDR/Region 封禁规则到内存缓存。
     * <p>距上次刷新不足 {@link #REFRESH_INTERVAL_MS} 则直接返回；加载失败时保留旧缓存且不更新刷新时间，
     * 下次请求仍会重试。
     */
    private void refreshBanRulesIfStale() {
        if (System.currentTimeMillis() - lastRefreshAt < REFRESH_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            // double-checked：进入同步块后再次确认，避免多线程同时通过首次检查
            if (System.currentTimeMillis() - lastRefreshAt < REFRESH_INTERVAL_MS) {
                return;
            }
            try {
                Map<String, List<Map<String, Object>>> all = cacheService.loadAllBanRules();
                if (all != null) {
                    uaRules = all.getOrDefault("ua", List.of());
                    cidrRules = all.getOrDefault("cidr", List.of());
                    regionRules = all.getOrDefault("region", List.of());
                }
                Set<String> disabled = cacheService.loadDisabledAiCrawlerUa();
                disabledAiCrawlerUa = disabled != null ? disabled : Set.of();
                Set<String> disabledAutomation = cacheService.loadDisabledAutomationUa();
                disabledAutomationUa = disabledAutomation != null ? disabledAutomation : Set.of();
                lastRefreshAt = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("加载封禁规则缓存失败，保留旧缓存", e);
            }
        }
    }

    /**
     * 客户端 IP 是否命中任一 CIDR 网段封禁规则。
     */
    private boolean isCidrBanned(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> rules = cidrRules;
        for (Map<String, Object> rule : rules) {
            try {
                Object cidrObj = rule.get("value");
                if (cidrObj == null) continue;
                String cidr = String.valueOf(cidrObj);
                if (IpUtil.isIpInCidr(ip, cidr)) {
                    return true;
                }
            } catch (Exception ignored) {
                // 单条规则异常容错，继续检查下一条
            }
        }
        return false;
    }

    /**
     * 解析 IP 地区，供 Java 层兜底地区封禁使用。
     * Nginx ban_check.lua 已改用 lua_c xdb_searcher 直接查 xdb 文件，不再依赖 Redis 地区缓存。
     *
     * @return {nation, province} 或 null（解析失败时）
     */
    private String[] resolveIpRegion(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        try {
            String[] location = ip2RegionProvider.resolveLocationDetail(ip);
            if (location == null || location.length < 1) {
                return null;
            }
            String nation = location.length >= 1 ? location[0] : null;
            String province = location.length >= 2 ? location[1] : null;
            if (nation == null && province == null) {
                return null;
            }
            return new String[]{nation, province};
        } catch (Exception e) {
            log.warn("IP 地区解析失败: ip={}", ip, e);
            return null;
        }
    }

    /**
     * 检查已解析的地区是否命中封禁规则（Java 兜底用，生产环境由 Nginx 拦截）。
     */
    private boolean isRegionBannedByRules(String[] location) {
        if (location == null || location.length < 1) {
            return false;
        }
        String nation = location.length >= 1 ? location[0] : null;
        String province = location.length >= 2 ? location[1] : null;
        List<Map<String, Object>> rules = regionRules;
        for (Map<String, Object> rule : rules) {
            try {
                Object rtObj = rule.get("regionType");
                Object valObj = rule.get("value");
                if (rtObj == null || valObj == null) continue;
                String regionType = String.valueOf(rtObj).toLowerCase(Locale.ROOT);
                String ruleValue = String.valueOf(valObj).toLowerCase(Locale.ROOT);
                if (ruleValue.isEmpty()) continue;

                if ("country".equals(regionType)) {
                    if (nation != null) {
                        String n = nation.toLowerCase(Locale.ROOT);
                        if (n.equals(ruleValue) || n.startsWith(ruleValue)) {
                            return true;
                        }
                    }
                } else if ("province".equals(regionType)) {
                    if (province != null) {
                        String p = province.toLowerCase(Locale.ROOT);
                        if (p.equals(ruleValue) || p.startsWith(ruleValue)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
                // 单条规则异常容错
            }
        }
        return false;
    }

    /**
     * 请求 User-Agent 是否命中管理员配置的 UA 封禁规则（大小写不敏感）。
     */
    private boolean isUaBannedByAdmin(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        String lowerUa = userAgent.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rules = uaRules;
        for (Map<String, Object> rule : rules) {
            try {
                Object valObj = rule.get("value");
                if (valObj == null) continue;
                String value = String.valueOf(valObj);
                if (value.isEmpty()) continue;
                String lowerValue = value.toLowerCase(Locale.ROOT);

                Object mmObj = rule.get("matchMode");
                String matchMode = mmObj == null ? "contains"
                        : String.valueOf(mmObj).toLowerCase(Locale.ROOT);

                if ("equals".equals(matchMode)) {
                    if (lowerUa.equals(lowerValue)) {
                        return true;
                    }
                } else {
                    if (lowerUa.contains(lowerValue)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // 单条规则异常容错
            }
        }
        return false;
    }

    /**
     * 记录攻击并检查是否需要拉黑
     */
    private void recordAttackAndCheckBlacklist(String ip, String requestURI, String attackType) {
        // 对于无法获取真实IP的情况，只记录攻击日志，不拉黑共享的 unknown_ip 键。
        // 原因：unknown_ip 是所有"无法解析真实IP"请求的共享标识（代理配置错误、内网直连、
        // 头部伪造等都可能命中），直接拉黑会误伤合法用户，且攻击者可借此让全站对正常代理用户失效。
        // 当前请求仍会被本次过滤拦截，仅不写入持久黑名单。
        if ("unknown_ip".equals(ip)) {
            log.error("拦截{}攻击: {} from 未知IP (无法获取真实IP地址，可能存在代理配置问题或恶意伪造，仅记录不拉黑)",
                    attackType, requestURI);
            return;
        }

        // 正常IP的处理逻辑 - 使用Redis计数器
        String attackKey = CacheConstants.buildIpAttackKey(ip);
        long currentCount = redisUtil.incr(attackKey, 1);

        // 设置攻击计数的过期时间
        if (currentCount == 1) {
            redisUtil.expire(attackKey, CacheConstants.IP_ATTACK_EXPIRE_TIME);
        }

        log.warn("拦截{}攻击: {} from IP: {} (攻击次数: {})", attackType, requestURI, ip, currentCount);

        // 检查是否达到拉黑阈值
        if (currentCount >= ATTACK_THRESHOLD) {
            String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
            redisUtil.set(blacklistKey, "auto:" + LocalDateTime.now().toString(), CacheConstants.IP_BLACKLIST_EXPIRE_TIME);

            log.error("IP {} 因连续{}次恶意攻击被拉黑{}小时，拉黑时间: {}",
                    ip, currentCount, BLACKLIST_DURATION_HOURS,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // 重置攻击计数
            redisUtil.del(attackKey);
        }
    }

    /**
     * 清理过期的拉黑记录和攻击计数
     * 注意：Redis会自动处理过期键，这个方法主要用于兼容性
     */
    private void cleanupExpiredRecords() {
        // Redis会自动清理过期的键，无需手动处理
    }

    /**
     * 检查是否为无参数的敏感API调用
     * 识别常见的恶意API探测模式，特别是那些缺少必要参数的请求
     */
    private boolean isInvalidApiCall(HttpServletRequest request) {
        String requestURI = request.getRequestURI();

        // 1. 忘记密码API无参数调用
        if (requestURI.equals("/user/getCodeForForgetPassword")) {
            // 检查是否缺少必要参数'place'
            String place = request.getParameter("place");
            if (place == null || place.isEmpty()) {
                log.warn("检测到无参数的密码找回API调用: {}, IP: {}",
                        requestURI, getClientIpAddress(request));
                return true;
            }
        }

        return false;
    }

}
