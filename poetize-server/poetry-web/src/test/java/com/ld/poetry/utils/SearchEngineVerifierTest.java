package com.ld.poetry.utils;

import com.ld.poetry.constants.CacheConstants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchEngineVerifierTest {

    private static final String GOOGLE_UA =
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
    private static final String BING_UA =
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)";

    @Test
    void verifiesGooglebotWhenReverseAndForwardDnsMatch() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("66.249.66.1", "crawl-66-249-66-1.googlebot.com")
                .forward("crawl-66-249-66-1.googlebot.com", List.of("66.249.66.1"));
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, dnsResolver);

        UserAgentClassifier.BotVerification result = awaitStatus(verifier, "66.249.66.1", GOOGLE_UA, "verified");

        assertEquals("Googlebot", result.claimedName());
        assertEquals("verified", result.status());
    }

    @Test
    void verifiesBingbotWhenReverseAndForwardDnsMatch() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("157.55.39.1", "msnbot-157-55-39-1.search.msn.com")
                .forward("msnbot-157-55-39-1.search.msn.com", List.of("157.55.39.1"));
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, dnsResolver);

        UserAgentClassifier.BotVerification result = awaitStatus(verifier, "157.55.39.1", BING_UA, "verified");

        assertEquals("Bingbot", result.claimedName());
        assertEquals("verified", result.status());
    }

    @Test
    void failsGooglebotWhenPtrUsesNonOfficialDomain() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("203.0.113.10", "crawler.example.com")
                .forward("crawler.example.com", List.of("203.0.113.10"));
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, dnsResolver);

        UserAgentClassifier.BotVerification result = awaitStatus(verifier, "203.0.113.10", GOOGLE_UA, "failed");
        UserAgentClassifier.UaInfo uaInfo = UserAgentClassifier.classify(GOOGLE_UA, Map.of(), result);

        assertEquals("failed", result.status());
        assertEquals("spoofed_search_engine", uaInfo.type());
        assertEquals("疑似伪装 Googlebot", uaInfo.name());
    }

    @Test
    void returnsUnknownImmediatelyWhenDnsIsSlowThenCachesResult() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, new SlowDnsResolver());

        long started = System.nanoTime();
        UserAgentClassifier.BotVerification result = verifier.verify("66.249.66.1", GOOGLE_UA);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        UserAgentClassifier.UaInfo uaInfo = UserAgentClassifier.classify(GOOGLE_UA, Map.of(), result);

        assertEquals("unknown", result.status());
        assertTrue(elapsedMillis < 80, "verify should return before slow DNS completes");
        assertEquals("search_engine", uaInfo.type());
        assertEquals("Googlebot（未验证）", uaInfo.name());

        UserAgentClassifier.BotVerification cached = awaitStatus(verifier, "66.249.66.1", GOOGLE_UA, "verified");
        assertEquals("verified", cached.status());
    }

    @Test
    void blacklistsRepeatedFailedSearchEngineSpoofing() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("203.0.113.10", "crawler.example.com")
                .forward("crawler.example.com", List.of("203.0.113.10"));
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, dnsResolver);

        awaitStatus(verifier, "203.0.113.10", GOOGLE_UA, "failed");
        for (int i = 0; i < 20; i++) {
            verifier.verify("203.0.113.10", GOOGLE_UA);
        }

        assertTrue(redisUtil.hasKey(CacheConstants.buildIpBlacklistKey("203.0.113.10")));
    }

    private UserAgentClassifier.BotVerification awaitStatus(SearchEngineVerifier verifier, String ip,
                                                            String userAgent, String expectedStatus)
            throws InterruptedException {
        UserAgentClassifier.BotVerification result = null;
        for (int i = 0; i < 100; i++) {
            result = verifier.verify(ip, userAgent);
            if (expectedStatus.equals(result.status())) {
                return result;
            }
            Thread.sleep(10);
        }
        return result;
    }

    private static final class FakeDnsResolver implements SearchEngineVerifier.DnsResolver {
        private final Map<String, String> reverseRecords = new HashMap<>();
        private final Map<String, List<String>> forwardRecords = new HashMap<>();

        private FakeDnsResolver reverse(String ip, String host) {
            reverseRecords.put(ip, host);
            return this;
        }

        private FakeDnsResolver forward(String host, List<String> ips) {
            forwardRecords.put(host, ips);
            return this;
        }

        @Override
        public String reverseLookup(String ip) {
            return reverseRecords.get(ip);
        }

        @Override
        public List<String> forwardLookup(String host) {
            return forwardRecords.getOrDefault(host, List.of());
        }
    }

    private static final class SlowDnsResolver implements SearchEngineVerifier.DnsResolver {
        @Override
        public String reverseLookup(String ip) throws Exception {
            Thread.sleep(100);
            return "crawl-66-249-66-1.googlebot.com";
        }

        @Override
        public List<String> forwardLookup(String host) {
            return List.of("66.249.66.1");
        }
    }

    private static final class FakeRedisUtil extends RedisUtil {
        private final Map<String, Object> values = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public boolean set(String key, Object value, long time) {
            values.put(key, value);
            return true;
        }

        @Override
        public long incr(String key, long delta) {
            long next = ((Number) values.getOrDefault(key, 0L)).longValue() + delta;
            values.put(key, next);
            return next;
        }

        @Override
        public boolean expire(String key, long time) {
            return true;
        }

        @Override
        public boolean hasKey(String key) {
            return values.containsKey(key);
        }
    }
}
