package com.ld.poetry.utils;

import com.ld.poetry.constants.CacheConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    private static final String YAHOO_UA =
            "Mozilla/5.0 (compatible; Yahoo! Slurp; http://help.yahoo.com/help/us/ysearch/slurp)";
    private static final String BAIDU_UA =
            "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)";
    private static final String QIHOO_360_UA =
            "Mozilla/5.0 (compatible; 360Spider; +http://www.so.com/help/help_3_2.html)";
    private static final String BYTESPIDER_UA =
            "Mozilla/5.0 (compatible; Bytespider; spider-feedback@bytedance.com)";
    private static final String SOGOU_UA =
            "Sogou web spider/4.0(+http://www.sogou.com/docs/help/webmasters.htm#07)";
    private static final String YANDEX_UA =
            "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)";
    private static final String DUCKDUCKBOT_UA =
            "DuckDuckBot/1.1; (+http://duckduckgo.com/duckduckbot.html)";
    private static final String APPLE_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Applebot/0.1";
    private static final String YISOU_UA =
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/69.0.3497.81 YisouSpider/5.0 Safari/537.36";
    private static final String SEZNAM_UA =
            "Mozilla/5.0 (compatible; SeznamBot/4.0; "
                    + "+https://o-seznam.cz/napoveda/vyhledavani/en/seznambot-crawler/)";
    private final List<SearchEngineVerifier> verifiers = new ArrayList<>();

    @AfterEach
    void shutdownVerifiers() {
        verifiers.forEach(SearchEngineVerifier::shutdown);
        verifiers.clear();
    }

    @Test
    void verifiesGooglebotWhenReverseAndForwardDnsMatch() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("66.249.66.1", "crawl-66-249-66-1.googlebot.com")
                .forward("crawl-66-249-66-1.googlebot.com", List.of("66.249.66.1"));
        SearchEngineVerifier verifier = newVerifier(redisUtil, dnsResolver);

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
        SearchEngineVerifier verifier = newVerifier(redisUtil, dnsResolver);

        UserAgentClassifier.BotVerification result = awaitStatus(verifier, "157.55.39.1", BING_UA, "verified");

        assertEquals("Bingbot", result.claimedName());
        assertEquals("verified", result.status());
    }

    @Test
    void verifiesAdditionalSearchEnginesWhenReverseAndForwardDnsMatch() throws Exception {
        List<BotCase> cases = List.of(
                new BotCase("Yahoo Slurp", YAHOO_UA, "74.6.67.218",
                        "lj612134.crawl.yahoo.net"),
                new BotCase("Baiduspider", BAIDU_UA, "180.76.15.10",
                        "baiduspider-180-76-15-10.crawl.baidu.com"),
                new BotCase("Bytespider", BYTESPIDER_UA, "110.249.201.100",
                        "bytespider-110-249-201-100.crawl.bytedance.com"),
                new BotCase("Sogou Spider", SOGOU_UA, "106.120.173.96",
                        "sogou-106-120-173-96.spider.sogou.com"),
                new BotCase("YandexBot", YANDEX_UA, "5.255.253.20",
                        "5-255-253-20.spider.yandex.com"),
                new BotCase("Applebot", APPLE_UA, "17.58.101.179",
                        "17-58-101-179.applebot.apple.com"),
                new BotCase("SeznamBot", SEZNAM_UA, "77.75.77.101",
                        "fulltextrobot-77-75-77-101.seznam.cz")
        );

        for (BotCase botCase : cases) {
            FakeRedisUtil redisUtil = new FakeRedisUtil();
            FakeDnsResolver dnsResolver = new FakeDnsResolver()
                    .reverse(botCase.ip(), botCase.host())
                    .forward(botCase.host(), List.of(botCase.ip()));
            SearchEngineVerifier verifier = newVerifier(redisUtil, dnsResolver);

            UserAgentClassifier.BotVerification result = awaitStatus(
                    verifier,
                    botCase.ip(),
                    botCase.userAgent(),
                    "verified");

            assertEquals(botCase.displayName(), result.claimedName());
            assertEquals("verified", result.status());
        }
    }

    @Test
    void verifies360SpiderAgainstBuiltinIpPrefixes() throws Exception {
        // 360 Spider 官方声明不支持反向 DNS，验证器必须走内置官方 IP 段，不依赖 PTR。
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        // dnsResolver 不提供任何 PTR 记录，确保走 IP 段路径
        SearchEngineVerifier verifier = newVerifier(redisUtil, new FakeDnsResolver());

        UserAgentClassifier.BotVerification result = awaitStatus(
                verifier, "180.153.236.84", QIHOO_360_UA, "verified");
        assertEquals("360 Spider", result.claimedName());
        assertEquals("verified", result.status());
    }

    @Test
    void fails360SpiderWhenIpIsNotInBuiltinPrefixes() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        SearchEngineVerifier verifier = newVerifier(redisUtil, new FakeDnsResolver());

        UserAgentClassifier.BotVerification result = awaitStatus(
                verifier, "203.0.113.42", QIHOO_360_UA, "failed");
        assertEquals("360 Spider", result.claimedName());
        assertEquals("failed", result.status());
    }

    @Test
    void failsYisouSpiderWhenIpIsNotInBuiltinPrefixes() throws Exception {
        // 伪装 YisouSpider 的非官方 IP 应直接判 failed，不触发任何 DNS 查询。
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        SearchEngineVerifier verifier = newVerifier(redisUtil, new FakeDnsResolver());

        UserAgentClassifier.BotVerification result = awaitStatus(
                verifier, "203.0.113.99", YISOU_UA, "failed");
        assertEquals("YisouSpider", result.claimedName());
        assertEquals("failed", result.status());
    }

    @Test
    void failsGooglebotWhenPtrUsesNonOfficialDomain() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("203.0.113.10", "crawler.example.com")
                .forward("crawler.example.com", List.of("203.0.113.10"));
        SearchEngineVerifier verifier = newVerifier(redisUtil, dnsResolver);

        UserAgentClassifier.BotVerification result = awaitStatus(verifier, "203.0.113.10", GOOGLE_UA, "failed");
        UserAgentClassifier.UaInfo uaInfo = UserAgentClassifier.classify(GOOGLE_UA, Map.of(), result);

        assertEquals("failed", result.status());
        assertEquals("spoofed_search_engine", uaInfo.type());
        assertEquals("疑似伪装 Googlebot", uaInfo.name());
    }

    @Test
    void failsBaiduspiderWhenPtrUsesNonOfficialDomain() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("203.0.113.20", "crawler.example.com")
                .forward("crawler.example.com", List.of("203.0.113.20"));
        SearchEngineVerifier verifier = newVerifier(redisUtil, dnsResolver);

        UserAgentClassifier.BotVerification result = awaitStatus(verifier, "203.0.113.20", BAIDU_UA, "failed");
        UserAgentClassifier.UaInfo uaInfo = UserAgentClassifier.classify(BAIDU_UA, Map.of(), result);

        assertEquals("failed", result.status());
        assertEquals("spoofed_search_engine", uaInfo.type());
        assertEquals("疑似伪装 Baiduspider", uaInfo.name());
    }

    @Test
    void verifiesDuckDuckBotAgainstOfficialIpPrefixes() throws Exception {
        FakeIpPrefixProvider ipPrefixProvider = new FakeIpPrefixProvider(List.of("20.191.44.119/32", "203.0.113.0/24"));
        SearchEngineVerifier verifier = newVerifier(new FakeRedisUtil(), new FakeDnsResolver(), ipPrefixProvider);

        UserAgentClassifier.BotVerification result = awaitStatus(
                verifier,
                "20.191.44.119",
                DUCKDUCKBOT_UA,
                "verified");

        assertEquals("DuckDuckBot", result.claimedName());
        assertEquals("verified", result.status());
    }

    @Test
    void failsDuckDuckBotWhenIpIsNotInOfficialPrefixes() throws Exception {
        FakeIpPrefixProvider ipPrefixProvider = new FakeIpPrefixProvider(List.of("20.191.44.119/32"));
        SearchEngineVerifier verifier = newVerifier(new FakeRedisUtil(), new FakeDnsResolver(), ipPrefixProvider);

        UserAgentClassifier.BotVerification result = awaitStatus(
                verifier,
                "198.51.100.42",
                DUCKDUCKBOT_UA,
                "failed");

        assertEquals("DuckDuckBot", result.claimedName());
        assertEquals("failed", result.status());
    }

    @Test
    void verifiesYisouByBuiltinPrefixesAndPetalByDns() throws Exception {
        // YisouSpider 无 PTR 记录，反向 DNS 必然超时，验证器必须走内置官方 IP 段，不依赖 DNS。
        FakeRedisUtil redisUtil1 = new FakeRedisUtil();
        // dnsResolver 不提供任何 PTR 记录，确保走 IP 段路径
        SearchEngineVerifier verifier1 = newVerifier(redisUtil1, new FakeDnsResolver());
        UserAgentClassifier.BotVerification result1 = awaitStatus(verifier1, "106.11.155.10", YISOU_UA, "verified");
        assertEquals("YisouSpider", result1.claimedName());
        assertEquals("verified", result1.status());

        // PetalBot
        String petalUa = "Mozilla/5.0 (compatible; PetalBot; +https://webmaster.petalsearch.com/site/petalbot)";
        FakeRedisUtil redisUtil2 = new FakeRedisUtil();
        FakeDnsResolver dnsResolver2 = new FakeDnsResolver()
                .reverse("114.119.160.10", "crawl.petalsearch.com")
                .forward("crawl.petalsearch.com", List.of("114.119.160.10"));
        SearchEngineVerifier verifier2 = newVerifier(redisUtil2, dnsResolver2);
        UserAgentClassifier.BotVerification result2 = awaitStatus(verifier2, "114.119.160.10", petalUa, "verified");
        assertEquals("PetalBot", result2.claimedName());
        assertEquals("verified", result2.status());
    }

    @Test
    void returnsUnknownImmediatelyWhenDnsIsSlowThenCachesResult() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        SearchEngineVerifier verifier = newVerifier(redisUtil, new SlowDnsResolver());

        long started = System.nanoTime();
        UserAgentClassifier.BotVerification result = verifier.verify("66.249.66.1", GOOGLE_UA);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        UserAgentClassifier.UaInfo uaInfo = UserAgentClassifier.classify(GOOGLE_UA, Map.of(), result);

        assertEquals("unknown", result.status());
        assertTrue(elapsedMillis < 80, "verify should return before slow DNS completes");
        assertEquals("search_engine", uaInfo.type());
        assertEquals("Googlebot", uaInfo.name());

        UserAgentClassifier.BotVerification cached = awaitStatus(verifier, "66.249.66.1", GOOGLE_UA, "verified");
        assertEquals("verified", cached.status());
    }

    @Test
    void blacklistsRepeatedFailedSearchEngineSpoofing() throws Exception {
        FakeRedisUtil redisUtil = new FakeRedisUtil();
        FakeDnsResolver dnsResolver = new FakeDnsResolver()
                .reverse("203.0.113.10", "crawler.example.com")
                .forward("crawler.example.com", List.of("203.0.113.10"));
        SearchEngineVerifier verifier = newVerifier(redisUtil, dnsResolver);

        awaitStatus(verifier, "203.0.113.10", GOOGLE_UA, "failed");
        // SHORT_SPOOF_THRESHOLD=3：首次 failed 已计入计数，再触发 2 次即达阈值拉黑。
        for (int i = 0; i < 2; i++) {
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

    private SearchEngineVerifier newVerifier(RedisUtil redisUtil, SearchEngineVerifier.DnsResolver dnsResolver) {
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, dnsResolver);
        verifiers.add(verifier);
        return verifier;
    }

    private SearchEngineVerifier newVerifier(RedisUtil redisUtil,
                                             SearchEngineVerifier.DnsResolver dnsResolver,
                                             SearchEngineVerifier.IpPrefixProvider ipPrefixProvider) {
        SearchEngineVerifier verifier = new SearchEngineVerifier(redisUtil, dnsResolver, ipPrefixProvider);
        verifiers.add(verifier);
        return verifier;
    }

    private record BotCase(String displayName, String userAgent, String ip, String host) {
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

    private static final class FakeIpPrefixProvider implements SearchEngineVerifier.IpPrefixProvider {
        private final List<String> prefixes;

        private FakeIpPrefixProvider(List<String> prefixes) {
            this.prefixes = prefixes;
        }

        @Override
        public List<String> fetch(String url) {
            return prefixes;
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
