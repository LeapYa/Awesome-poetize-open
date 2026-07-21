package com.ld.poetry.utils;

import com.ld.poetry.constants.CacheConstants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 验证自称搜索引擎的请求是否来自官方IP。
 */
@Slf4j
@Component
public class SearchEngineVerifier {

    private static final long VERIFIED_CACHE_SECONDS = 24 * 3600;
    private static final long FAILED_CACHE_SECONDS = 6 * 3600;
    private static final long UNKNOWN_CACHE_SECONDS = 30 * 60;
    private static final long PENDING_CACHE_SECONDS = 60;
    private static final long SHORT_SPOOF_WINDOW_SECONDS = 10 * 60;
    private static final long LONG_SPOOF_WINDOW_SECONDS = 60 * 60;
    private static final long SHORT_BLACKLIST_SECONDS = 60 * 60;
    // failed 状态语义是 DNS/IP 段验证已确认 IP 不属于官方搜索引擎（不是疑似），
    // 真搜索引擎永远不会 failed，因此阈值收紧到 3 次/10分钟、10 次/1小时。
    private static final int SHORT_SPOOF_THRESHOLD = 3;
    private static final int LONG_SPOOF_THRESHOLD = 10;
    // 后台 DNS 验证并发上限，超过的任务在虚拟线程内排队等待，避免一次抖动瞬间打爆 DNS。
    private static final int DNS_VERIFY_CONCURRENCY = 20;

    private final RedisUtil redisUtil;
    private final DnsResolver dnsResolver;
    private final IpPrefixProvider ipPrefixProvider;
    private final ExecutorService dnsVerifyExecutor;
    private final Semaphore dnsVerifyConcurrency;

    @Autowired
    public SearchEngineVerifier(RedisUtil redisUtil) {
        this(redisUtil, new InetAddressDnsResolver());
    }

    SearchEngineVerifier(RedisUtil redisUtil, DnsResolver dnsResolver) {
        this(redisUtil, dnsResolver, new HttpIpPrefixProvider());
    }

    SearchEngineVerifier(RedisUtil redisUtil, DnsResolver dnsResolver, IpPrefixProvider ipPrefixProvider) {
        this(redisUtil, dnsResolver, ipPrefixProvider, createDnsVerifyExecutor(),
                new Semaphore(DNS_VERIFY_CONCURRENCY));
    }

    SearchEngineVerifier(RedisUtil redisUtil, DnsResolver dnsResolver, ExecutorService dnsVerifyExecutor) {
        this(redisUtil, dnsResolver, new HttpIpPrefixProvider(), dnsVerifyExecutor,
                new Semaphore(DNS_VERIFY_CONCURRENCY));
    }

    SearchEngineVerifier(RedisUtil redisUtil, DnsResolver dnsResolver, IpPrefixProvider ipPrefixProvider,
                         ExecutorService dnsVerifyExecutor) {
        this(redisUtil, dnsResolver, ipPrefixProvider, dnsVerifyExecutor,
                new Semaphore(DNS_VERIFY_CONCURRENCY));
    }

    SearchEngineVerifier(RedisUtil redisUtil, DnsResolver dnsResolver, IpPrefixProvider ipPrefixProvider,
                         ExecutorService dnsVerifyExecutor, Semaphore dnsVerifyConcurrency) {
        this.redisUtil = redisUtil;
        this.dnsResolver = dnsResolver;
        this.ipPrefixProvider = ipPrefixProvider;
        this.dnsVerifyExecutor = dnsVerifyExecutor;
        this.dnsVerifyConcurrency = dnsVerifyConcurrency;
    }

    public UserAgentClassifier.BotVerification verify(String ip, String userAgent) {
        String claimedName = UserAgentClassifier.detectSearchEngineName(userAgent);
        if (!hasText(claimedName)) {
            return UserAgentClassifier.BotVerification.notApplicable();
        }

        String normalizedIp = normalizeIp(ip);
        if (!hasText(normalizedIp) || "unknown".equalsIgnoreCase(normalizedIp)) {
            return UserAgentClassifier.BotVerification.unknown(claimedName, "缺少可验证的访问IP");
        }

        SearchEngineRule rule = SearchEngineRule.match(claimedName);
        if (rule == null) {
            return UserAgentClassifier.BotVerification.unknown(claimedName, "暂无可信IP验证规则");
        }

        UserAgentClassifier.BotVerification cached = readCachedVerification(rule, normalizedIp);
        if (cached != null) {
            recordSpoofIfNeeded(normalizedIp, cached);
            return cached;
        }

        cachePendingVerification(rule, normalizedIp);
        verifyInBackground(rule, normalizedIp);
        return UserAgentClassifier.BotVerification.unknown(claimedName, "DNS验证已提交后台任务");
    }

    private void verifyInBackground(SearchEngineRule rule, String ip) {
        try {
            dnsVerifyExecutor.execute(() -> {
                UserAgentClassifier.BotVerification verification;
                try {
                    // 限流：超出并发上限的虚拟线程在此排队，避免瞬间打爆 DNS。
                    dnsVerifyConcurrency.acquire();
                    try {
                        verification = verifyRule(rule, ip);
                    } finally {
                        dnsVerifyConcurrency.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    verification = UserAgentClassifier.BotVerification.unknown(rule.displayName, "DNS验证被中断");
                } catch (Exception e) {
                    log.warn("搜索引擎IP验证失败: engine={}, ip={}, error={}", rule.displayName, ip, e.getMessage());
                    verification = UserAgentClassifier.BotVerification.error(rule.displayName, "搜索引擎验证异常");
                }
                cacheVerification(rule, ip, verification);
                recordSpoofIfNeeded(ip, verification);
            });
        } catch (RejectedExecutionException e) {
            // 仅在 executor 已关闭时抛出，回写未知状态避免持久卡死。
            cacheVerification(rule, ip,
                    UserAgentClassifier.BotVerification.unknown(rule.displayName, "DNS验证队列繁忙"));
        }
    }

    @PreDestroy
    public void shutdown() {
        dnsVerifyExecutor.shutdown();
        DNS_LOOKUP_EXECUTOR.shutdown();
        try {
            if (!dnsVerifyExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                dnsVerifyExecutor.shutdownNow();
            }
            if (!DNS_LOOKUP_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                DNS_LOOKUP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dnsVerifyExecutor.shutdownNow();
            DNS_LOOKUP_EXECUTOR.shutdownNow();
        }
    }

    // 每个验证任务一个虚拟线程
    // 配合 dnsVerifyConcurrency 信号量限流真实并发，避免 DNS 阻塞相互拖累。
    private static ExecutorService createDnsVerifyExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("search-bot-dns-verify-", 1)
                .factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    private UserAgentClassifier.BotVerification verifyRule(SearchEngineRule rule, String ip) {
        // 优先使用内置 IP 段（适用于官方不支持反向 DNS 的搜索引擎，如 360 Spider）
        if (rule.builtinIpPrefixes != null && !rule.builtinIpPrefixes.isEmpty()) {
            return verifyByBuiltinPrefixes(rule, ip);
        }
        if (hasText(rule.ipPrefixUrl)) {
            return verifyByIpPrefix(rule, ip);
        }
        return verifyByDns(rule, ip);
    }

    private UserAgentClassifier.BotVerification verifyByBuiltinPrefixes(SearchEngineRule rule, String ip) {
        boolean matched = rule.builtinIpPrefixes.stream()
                .anyMatch(prefix -> ipMatchesPrefix(ip, prefix));
        if (!matched) {
            return UserAgentClassifier.BotVerification.failed(rule.displayName, "访问IP不在官方IP段");
        }
        return UserAgentClassifier.BotVerification.verified(rule.displayName, "官方IP段验证通过");
    }

    private UserAgentClassifier.BotVerification verifyByDns(SearchEngineRule rule, String ip) {
        try {
            String host = normalizeHost(dnsResolver.reverseLookup(ip));
            if (!hasText(host) || host.equals(ip)) {
                return UserAgentClassifier.BotVerification.failed(rule.displayName, "PTR记录未指向官方域名");
            }
            if (!matchesAnySuffix(host, rule.allowedHostSuffixes)) {
                return UserAgentClassifier.BotVerification.failed(rule.displayName, "PTR域名不属于官方后缀");
            }

            List<String> forwardIps = dnsResolver.forwardLookup(host);
            boolean pointsBack = forwardIps != null && forwardIps.stream()
                    .map(SearchEngineVerifier::normalizeIp)
                    .anyMatch(ip::equals);
            if (!pointsBack) {
                return UserAgentClassifier.BotVerification.failed(rule.displayName, "官方PTR未正向回指访问IP");
            }

            return UserAgentClassifier.BotVerification.verified(rule.displayName, "PTR与正向DNS验证通过");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UserAgentClassifier.BotVerification verifyByIpPrefix(SearchEngineRule rule, String ip) {
        try {
            List<String> prefixes = ipPrefixProvider.fetch(rule.ipPrefixUrl);
            if (prefixes == null || prefixes.isEmpty()) {
                return UserAgentClassifier.BotVerification.unknown(rule.displayName, "官方IP列表为空");
            }
            boolean matched = prefixes.stream().anyMatch(prefix -> ipMatchesPrefix(ip, prefix));
            if (!matched) {
                return UserAgentClassifier.BotVerification.failed(rule.displayName, "访问IP不在官方IP列表");
            }
            return UserAgentClassifier.BotVerification.verified(rule.displayName, "官方IP列表验证通过");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UserAgentClassifier.BotVerification readCachedVerification(SearchEngineRule rule, String ip) {
        if (redisUtil == null) {
            return null;
        }
        Object cached = redisUtil.get(CacheConstants.buildSearchBotVerifyKey(rule.cacheName, ip));
        if (cached == null) {
            return null;
        }
        String text = cached.toString();
        int separator = text.indexOf('|');
        if (separator < 0) {
            return null;
        }
        String status = text.substring(0, separator);
        String reason = text.substring(separator + 1);
        return new UserAgentClassifier.BotVerification(rule.displayName, status, reason);
    }

    private void cacheVerification(SearchEngineRule rule, String ip,
                                   UserAgentClassifier.BotVerification verification) {
        if (redisUtil == null || verification == null) {
            return;
        }
        long ttl = switch (verification.status()) {
            case "verified" -> VERIFIED_CACHE_SECONDS;
            case "failed" -> FAILED_CACHE_SECONDS;
            default -> UNKNOWN_CACHE_SECONDS;
        };
        String reason = verification.reason() != null ? verification.reason() : "";
        redisUtil.set(CacheConstants.buildSearchBotVerifyKey(rule.cacheName, ip),
                verification.status() + "|" + reason, ttl);
    }

    private void cachePendingVerification(SearchEngineRule rule, String ip) {
        if (redisUtil == null) {
            return;
        }
        redisUtil.set(CacheConstants.buildSearchBotVerifyKey(rule.cacheName, ip),
                "pending|DNS验证已提交后台任务", PENDING_CACHE_SECONDS);
    }

    private void recordSpoofIfNeeded(String ip, UserAgentClassifier.BotVerification verification) {
        if (redisUtil == null || verification == null || !"failed".equals(verification.status())) {
            return;
        }

        String tenMinuteKey = CacheConstants.buildSearchBotSpoof10mKey(ip);
        long shortCount = redisUtil.incr(tenMinuteKey, 1);
        if (shortCount == 1) {
            redisUtil.expire(tenMinuteKey, SHORT_SPOOF_WINDOW_SECONDS);
        }

        String oneHourKey = CacheConstants.buildSearchBotSpoof1hKey(ip);
        long longCount = redisUtil.incr(oneHourKey, 1);
        if (longCount == 1) {
            redisUtil.expire(oneHourKey, LONG_SPOOF_WINDOW_SECONDS);
        }

        String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
        String blacklistReason = "spoofed_search_engine:" + verification.claimedName() + ":"
                + LocalDateTime.now();
        if (longCount >= LONG_SPOOF_THRESHOLD) {
            redisUtil.set(blacklistKey, blacklistReason, CacheConstants.IP_BLACKLIST_EXPIRE_TIME);
            return;
        }
        if (shortCount >= SHORT_SPOOF_THRESHOLD) {
            redisUtil.set(blacklistKey, blacklistReason, SHORT_BLACKLIST_SECONDS);
        }
    }

    private static boolean matchesAnySuffix(String host, List<String> suffixes) {
        if (!hasText(host) || suffixes == null) {
            return false;
        }
        for (String suffix : suffixes) {
            String normalizedSuffix = normalizeHost(suffix);
            if (host.equals(normalizedSuffix) || host.endsWith("." + normalizedSuffix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeHost(String host) {
        if (!hasText(host)) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeIp(String ip) {
        return ip == null ? "" : ip.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean ipMatchesPrefix(String ip, String prefix) {
        try {
            if (!hasText(ip) || !hasText(prefix)) {
                return false;
            }
            String[] parts = prefix.trim().split("/", 2);
            InetAddress target = InetAddress.getByName(ip);
            InetAddress network = InetAddress.getByName(parts[0]);
            byte[] targetBytes = target.getAddress();
            byte[] networkBytes = network.getAddress();
            if (targetBytes.length != networkBytes.length) {
                return false;
            }
            int maskBits = parts.length == 2 ? Integer.parseInt(parts[1]) : targetBytes.length * 8;
            if (maskBits < 0 || maskBits > targetBytes.length * 8) {
                return false;
            }
            int fullBytes = maskBits / 8;
            int remainingBits = maskBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (targetBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        } catch (Exception e) {
            return false;
        }
    }

    interface DnsResolver {
        String reverseLookup(String ip) throws Exception;

        List<String> forwardLookup(String host) throws Exception;
    }

    interface IpPrefixProvider {
        List<String> fetch(String url) throws Exception;
    }

    private record SearchEngineRule(String displayName, String cacheName, List<String> allowedHostSuffixes,
                                    String ipPrefixUrl, List<String> builtinIpPrefixes) {
        private SearchEngineRule(String displayName, String cacheName, List<String> allowedHostSuffixes) {
            this(displayName, cacheName, allowedHostSuffixes, null, List.of());
        }

        private SearchEngineRule(String displayName, String cacheName, String ipPrefixUrl) {
            this(displayName, cacheName, List.of(), ipPrefixUrl, List.of());
        }

        /**
         * 用于无法反向DNS验证但有官方公开IP段的搜索引擎（如360 Spider官方明确不支持nslookup）。
         * allowedHostSuffixes 仅作为兜底 DNS 验证后缀，builtinIpPrefixes 为主要验证路径。
         */
        private SearchEngineRule(String displayName, String cacheName,
                                 List<String> allowedHostSuffixes, List<String> builtinIpPrefixes) {
            this(displayName, cacheName, allowedHostSuffixes, null, builtinIpPrefixes);
        }

        private static final SearchEngineRule GOOGLE = new SearchEngineRule(
                "Googlebot",
                "googlebot",
                List.of("googlebot.com", "google.com", "googleusercontent.com")
        );
        private static final SearchEngineRule BING = new SearchEngineRule(
                "Bingbot",
                "bingbot",
                List.of("search.msn.com")
        );
        private static final SearchEngineRule YAHOO = new SearchEngineRule(
                "Yahoo Slurp",
                "yahooslurp",
                List.of("crawl.yahoo.net")
        );
        private static final SearchEngineRule BAIDU = new SearchEngineRule(
                "Baiduspider",
                "baiduspider",
                List.of("baidu.com", "baidu.jp")
        );
        // 360 Spider 官方声明不支持 nslookup（反向DNS查询），仅提供公开 IP 段。
        // 来源：https://www.so.com/help/spider_ip.html
        private static final SearchEngineRule QIHOO_360 = new SearchEngineRule(
                "360 Spider",
                "360spider",
                List.of("qhims.com"),
                List.of(
                        "180.153.232.0/24", "180.153.234.0/24", "180.153.236.0/24",
                        "180.163.220.0/24",
                        "42.236.10.0/24", "42.236.12.0/24", "42.236.13.0/24",
                        "42.236.14.0/24", "42.236.15.0/24", "42.236.16.0/24",
                        "42.236.17.0/24", "42.236.46.0/24", "42.236.48.0/24",
                        "42.236.49.0/24", "42.236.50.0/24", "42.236.51.0/24",
                        "42.236.52.0/24", "42.236.53.0/24", "42.236.54.0/24",
                        "42.236.55.0/24", "42.236.99.0/24", "42.236.101.0/24",
                        "42.236.102.0/24", "42.236.103.0/24"
                )
        );
        private static final SearchEngineRule BYTEDANCE = new SearchEngineRule(
                "Bytespider",
                "bytespider",
                List.of("bytedance.com")
        );
        private static final SearchEngineRule SOGOU = new SearchEngineRule(
                "Sogou Spider",
                "sogouspider",
                List.of("sogou.com")
        );
        private static final SearchEngineRule YANDEX = new SearchEngineRule(
                "YandexBot",
                "yandexbot",
                List.of("yandex.ru", "yandex.net", "yandex.com")
        );
        private static final SearchEngineRule DUCKDUCKGO = new SearchEngineRule(
                "DuckDuckBot",
                "duckduckbot",
                "https://duckduckgo.com/duckduckbot.json"
        );
        private static final SearchEngineRule APPLE = new SearchEngineRule(
                "Applebot",
                "applebot",
                List.of("applebot.apple.com")
        );
        private static final SearchEngineRule YISOU = new SearchEngineRule(
                "YisouSpider",
                "yisouspider",
                List.of("sm.cn")
        );
        private static final SearchEngineRule PETAL = new SearchEngineRule(
                "PetalBot",
                "petalbot",
                List.of("petalsearch.com", "aspiegel.com")
        );

        private static SearchEngineRule match(String claimedName) {
            if (!hasText(claimedName)) {
                return null;
            }
            return switch (claimedName.trim().toLowerCase(Locale.ROOT)) {
                case "googlebot" -> GOOGLE;
                case "bingbot" -> BING;
                case "yahoo slurp" -> YAHOO;
                case "baiduspider" -> BAIDU;
                case "360 spider" -> QIHOO_360;
                case "bytespider" -> BYTEDANCE;
                case "sogou spider" -> SOGOU;
                case "yandexbot" -> YANDEX;
                case "duckduckbot" -> DUCKDUCKGO;
                case "applebot" -> APPLE;
                case "yisouspider" -> YISOU;
                case "petalbot" -> PETAL;
                default -> null;
            };
        }
    }

    // InetAddress 的 DNS 查询没有原生超时 API，PTR 不存在时可能阻塞数十秒；
    // 用虚拟线程包装 + Future.get(timeout) 提前返回，避免拖垮验证线程池。
    private static final Duration DNS_LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final ExecutorService DNS_LOOKUP_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("dns-lookup-", 1).factory());

    private static final class InetAddressDnsResolver implements DnsResolver {
        @Override
        public String reverseLookup(String ip) throws Exception {
            Future<String> future = DNS_LOOKUP_EXECUTOR.submit(() ->
                    InetAddress.getByName(ip).getCanonicalHostName());
            try {
                return future.get(DNS_LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // 虚拟线程继续在后台跑直到 OS 超时，开销可忽略；上层据 SocketTimeoutException 判定失败。
                throw new SocketTimeoutException("DNS反向查询超时: " + ip);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }

        @Override
        public List<String> forwardLookup(String host) throws Exception {
            Future<List<String>> future = DNS_LOOKUP_EXECUTOR.submit(() ->
                    Arrays.stream(InetAddress.getAllByName(host))
                            .map(InetAddress::getHostAddress)
                            .toList());
            try {
                return future.get(DNS_LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new SocketTimeoutException("DNS正向查询超时: " + host);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }
    }

    private static final class HttpIpPrefixProvider implements IpPrefixProvider {
        private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
        private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
        private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
        private static final long CACHE_MILLIS = Duration.ofHours(1).toMillis();
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        private volatile String cachedUrl;
        private volatile List<String> cachedPrefixes;
        private volatile long cachedAtMillis;

        @Override
        public List<String> fetch(String url) throws Exception {
            long now = System.currentTimeMillis();
            List<String> localCache = cachedPrefixes;
            if (url.equals(cachedUrl) && localCache != null && now - cachedAtMillis < CACHE_MILLIS) {
                return localCache;
            }
            synchronized (this) {
                localCache = cachedPrefixes;
                now = System.currentTimeMillis();
                if (url.equals(cachedUrl) && localCache != null && now - cachedAtMillis < CACHE_MILLIS) {
                    return localCache;
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("官方IP列表请求失败: HTTP " + response.statusCode());
                }
                List<String> prefixes = parsePrefixes(response.body());
                cachedUrl = url;
                cachedPrefixes = prefixes;
                cachedAtMillis = now;
                return prefixes;
            }
        }

        private static List<String> parsePrefixes(String body) throws Exception {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode prefixesNode = root.path("prefixes");
            List<String> prefixes = new ArrayList<>();
            if (!prefixesNode.isArray()) {
                return prefixes;
            }
            for (JsonNode prefixNode : prefixesNode) {
                addPrefix(prefixes, prefixNode.path("ipv4Prefix").asText(null));
                addPrefix(prefixes, prefixNode.path("ipv6Prefix").asText(null));
            }
            return List.copyOf(prefixes);
        }

        private static void addPrefix(List<String> prefixes, String prefix) {
            if (hasText(prefix)) {
                prefixes.add(prefix.trim());
            }
        }
    }
}
