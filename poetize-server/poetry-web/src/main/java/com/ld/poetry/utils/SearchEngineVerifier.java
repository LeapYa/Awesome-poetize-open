package com.ld.poetry.utils;

import com.ld.poetry.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int SHORT_SPOOF_THRESHOLD = 20;
    private static final int LONG_SPOOF_THRESHOLD = 100;
    private static final ExecutorService DNS_VERIFY_EXECUTOR = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256),
            new DaemonThreadFactory("search-bot-dns-verify-"),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private final RedisUtil redisUtil;
    private final DnsResolver dnsResolver;

    @Autowired
    public SearchEngineVerifier(RedisUtil redisUtil) {
        this(redisUtil, new InetAddressDnsResolver());
    }

    SearchEngineVerifier(RedisUtil redisUtil, DnsResolver dnsResolver) {
        this.redisUtil = redisUtil;
        this.dnsResolver = dnsResolver;
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
            DNS_VERIFY_EXECUTOR.execute(() -> {
                UserAgentClassifier.BotVerification verification;
                try {
                    verification = verifyByDns(rule, ip);
                } catch (Exception e) {
                    log.warn("搜索引擎IP验证失败: engine={}, ip={}, error={}", rule.displayName, ip, e.getMessage());
                    verification = UserAgentClassifier.BotVerification.error(rule.displayName, "DNS验证异常");
                }
                cacheVerification(rule, ip, verification);
                recordSpoofIfNeeded(ip, verification);
            });
        } catch (RejectedExecutionException e) {
            cacheVerification(rule, ip,
                    UserAgentClassifier.BotVerification.unknown(rule.displayName, "DNS验证队列繁忙"));
        }
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

    interface DnsResolver {
        String reverseLookup(String ip) throws Exception;

        List<String> forwardLookup(String host) throws Exception;
    }

    private record SearchEngineRule(String displayName, String cacheName, List<String> allowedHostSuffixes) {
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

        private static SearchEngineRule match(String claimedName) {
            if ("Googlebot".equalsIgnoreCase(claimedName)) {
                return GOOGLE;
            }
            if ("Bingbot".equalsIgnoreCase(claimedName)) {
                return BING;
            }
            return null;
        }
    }

    private static final class InetAddressDnsResolver implements DnsResolver {
        @Override
        public String reverseLookup(String ip) throws Exception {
            return InetAddress.getByName(ip).getCanonicalHostName();
        }

        @Override
        public List<String> forwardLookup(String host) throws Exception {
            return Arrays.stream(InetAddress.getAllByName(host))
                    .map(InetAddress::getHostAddress)
                    .toList();
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger(1);

        private DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
