package com.ld.poetry.config;

import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 RedisConfig 收紧后的 defaultTyping 白名单不误伤正常业务场景的反序列化。
 * <p>
 * 测试用 ObjectMapper 与 {@link RedisConfig#redisTemplate} 中构建的 JsonMapper 配置完全一致：
 *  - {@code allowIfSubType} 白名单：com.ld.poetry. / java.util. / java.lang. / java.time. / java.math. / byte[]
 *  - {@code DefaultTyping.NON_FINAL}
 * <p>
 * 覆盖 CacheService 实际缓存的类型：User/Article/WebInfo 实体、List&lt;Article&gt; 嵌套泛型、
 * Map 混合值、String/Long/byte[]/LocalDateTime 基础类型；并验证非白名单类（gadget 攻击面）被拦截。
 */
@DisplayName("RedisConfig defaultTyping 白名单回归测试")
public class RedisConfigDefaultTypingTest {

    private static ObjectMapper objectMapper;

    /**
     * 复刻 RedisConfig 中的 JsonMapper 构建逻辑。
     * 若 RedisConfig 配置变化，此处需同步修改，以保证测试与生产配置一致。
     */
    @BeforeAll
    static void setUp() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ld.poetry.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType(byte[].class)
                .build();
        objectMapper = JsonMapper.builder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();
    }

    @Test
    @DisplayName("User 实体往返：反序列化后 instanceof User 而非 LinkedHashMap")
    void userRoundTrip() {
        User user = new User();
        user.setId(1);
        user.setUsername("alice");
        user.setPhoneNumber("13800000000");
        user.setPassword("secret");

        byte[] bytes = objectMapper.writeValueAsBytes(user);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(User.class, deserialized, "User 应还原为具体类型而非 LinkedHashMap");
        User back = (User) deserialized;
        assertEquals(1, back.getId());
        assertEquals("alice", back.getUsername());
        assertEquals("secret", back.getPassword());
    }

    @Test
    @DisplayName("Article 实体往返：含 LocalDateTime/BigDecimal 字段")
    void articleRoundTrip() {
        Article article = new Article();
        article.setId(100);
        article.setUserId(1);
        article.setSortId(2);
        article.setArticleTitle("测试标题");
        article.setArticleContent("内容");
        article.setPayAmount(new BigDecimal("9.99"));
        LocalDateTime now = LocalDateTime.of(2026, 7, 4, 12, 0, 0);
        article.setCreateTime(now);
        article.setUpdateTime(now);

        byte[] bytes = objectMapper.writeValueAsBytes(article);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(Article.class, deserialized, "Article 应还原为具体类型");
        Article back = (Article) deserialized;
        assertEquals(100, back.getId());
        assertEquals("内容", back.getArticleContent());
        assertEquals("测试标题", back.getArticleTitle());
        assertEquals(new BigDecimal("9.99"), back.getPayAmount(), "BigDecimal 字段应正确往返");
        assertEquals(now, back.getCreateTime(), "LocalDateTime 字段应正确往返");
    }

    @Test
    @DisplayName("WebInfo 实体往返")
    void webInfoRoundTrip() {
        WebInfo webInfo = new WebInfo();
        webInfo.setId(1);
        webInfo.setWebName("我的博客");
        webInfo.setWebTitle("博客副标题");
        webInfo.setFooter("页脚内容");

        byte[] bytes = objectMapper.writeValueAsBytes(webInfo);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(WebInfo.class, deserialized, "WebInfo 应还原为具体类型");
        WebInfo back = (WebInfo) deserialized;
        assertEquals("我的博客", back.getWebName());
        assertEquals("博客副标题", back.getWebTitle());
        assertEquals("页脚内容", back.getFooter());
    }

    @Test
    @DisplayName("List<Article> 嵌套泛型往返：元素 instanceof Article（CacheService.getCachedArticleList 关键路径）")
    void articleListRoundTrip() {
        List<Article> list = new ArrayList<>();
        Article a1 = new Article();
        a1.setId(1);
        a1.setArticleContent("a1");
        Article a2 = new Article();
        a2.setId(2);
        a2.setArticleContent("a2");
        list.add(a1);
        list.add(a2);

        byte[] bytes = objectMapper.writeValueAsBytes(list);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(List.class, deserialized, "顶层应还原为 List");
        List<?> backList = (List<?>) deserialized;
        assertEquals(2, backList.size());
        // 关键断言：元素必须是 Article，不能是 LinkedHashMap——否则调用方 article.getXxx() 会 ClassCastException
        assertInstanceOf(Article.class, backList.get(0), "List 元素必须还原为 Article 而非 LinkedHashMap");
        assertInstanceOf(Article.class, backList.get(1), "List 元素必须还原为 Article 而非 LinkedHashMap");
        assertEquals(1, ((Article) backList.get(0)).getId());
        assertEquals("a2", ((Article) backList.get(1)).getArticleContent());
    }

    @Test
    @DisplayName("Map<String, List<Article>> 深嵌套泛型往返（CacheService 按 sortId 分组缓存路径）")
    void deepNestedMapRoundTrip() {
        // 模拟 CacheService 中 Map<String, List<Article>> 的存储：
        // defaultTyping.NON_FINAL 会递归打类型标识——HashMap / ArrayList / Article 三层，
        // 收紧后的白名单（com.ld.poetry. + java.util.）需全部放行才能还原。
        Map<String, List<Article>> grouped = new HashMap<>();
        List<Article> group1 = new ArrayList<>();
        Article a1 = new Article();
        a1.setId(1);
        a1.setArticleTitle("g1-a1");
        group1.add(a1);
        Article a2 = new Article();
        a2.setId(2);
        a2.setArticleTitle("g1-a2");
        group1.add(a2);
        grouped.put("sort1", group1);

        List<Article> group2 = new ArrayList<>();
        Article a3 = new Article();
        a3.setId(3);
        a3.setArticleTitle("g2-a3");
        group2.add(a3);
        grouped.put("sort2", group2);

        byte[] bytes = objectMapper.writeValueAsBytes(grouped);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(Map.class, deserialized, "外层应还原为 Map");
        Map<?, ?> back = (Map<?, ?>) deserialized;
        assertEquals(2, back.size());
        Object g1 = back.get("sort1");
        assertInstanceOf(List.class, g1, "值应还原为 List 而非 LinkedHashMap");
        List<?> g1List = (List<?>) g1;
        assertEquals(2, g1List.size());
        // 关键断言：深嵌套的最内层元素必须是 Article，不能是 LinkedHashMap
        assertInstanceOf(Article.class, g1List.get(0), "深嵌套 List 元素必须还原为 Article");
        assertEquals(1, ((Article) g1List.get(0)).getId());
        assertEquals("g1-a2", ((Article) g1List.get(1)).getArticleTitle());
        Object g2 = back.get("sort2");
        assertInstanceOf(List.class, g2);
        assertInstanceOf(Article.class, ((List<?>) g2).get(0), "第二组元素也必须还原为 Article");
        assertEquals(3, ((Article) ((List<?>) g2).get(0)).getId());
    }

    @Test
    @DisplayName("Map<String, Object> 混合值往返（SearchEnginePushServiceImpl SEO 配置缓存路径）")
    void mixedMapRoundTrip() {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", Boolean.TRUE);
        map.put("threshold", 100);
        map.put("ratio", new BigDecimal("0.85"));
        map.put("endpoint", "https://push.example.com");
        List<String> engines = new ArrayList<>();
        engines.add("baidu");
        engines.add("bing");
        map.put("engines", engines);

        byte[] bytes = objectMapper.writeValueAsBytes(map);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(Map.class, deserialized);
        Map<?, ?> back = (Map<?, ?>) deserialized;
        assertEquals(Boolean.TRUE, back.get("enabled"));
        assertEquals(100, back.get("threshold"));
        assertEquals("https://push.example.com", back.get("endpoint"));
        assertInstanceOf(BigDecimal.class, back.get("ratio"), "BigDecimal 应正确往返");
        assertInstanceOf(List.class, back.get("engines"), "嵌套 List 应正确往返");
    }

    @Test
    @DisplayName("String/Long/Integer 基础类型往返")
    void primitivesRoundTrip() {
        // 说明：DefaultTyping.NON_FINAL 仅对非 final 类写入类型标识。
        // String/Long/Integer/LocalDateTime/byte[] 等 final 类型或数组不会被打标，
        // 反序列化到 Object.class 时按 JSON 原始字面量还原（String→String、数字→Integer/Long）。
        // 白名单对这些类型不产生影响——它们作为实体字段时由外层实体的 schema 还原
        // （见 articleRoundTrip 的 LocalDateTime/BigDecimal 字段验证）。

        // String：final 类型，无类型标识
        String str = "hello 世界";
        byte[] strBytes = objectMapper.writeValueAsBytes(str);
        Object strBack = objectMapper.readValue(strBytes, Object.class);
        assertEquals(str, strBack);

        // Long：NginxPageVisitLogConsumer offset 缓存
        Long offset = 123456789L;
        byte[] longBytes = objectMapper.writeValueAsBytes(offset);
        Object longBack = objectMapper.readValue(longBytes, Object.class);
        assertInstanceOf(Number.class, longBack, "Long 应还原为 Number 子类");
        assertEquals(123456789L, ((Number) longBack).longValue());

        // Integer：CacheService.getCachedArticleViewCount 计数缓存
        Integer count = 42;
        byte[] intBytes = objectMapper.writeValueAsBytes(count);
        Object intBack = objectMapper.readValue(intBytes, Object.class);
        assertInstanceOf(Number.class, intBack);
        assertEquals(42, ((Number) intBack).intValue());
    }

    @Test
    @DisplayName("QR Code base64 String 往返：CacheService.cacheArticleQRCode 存储格式验证")
    void qrCodeBase64RoundTrip() {
        // 模拟 CacheService.cacheArticleQRCode 的存储链路：
        // 1. byte[] 显式 base64 编码成 String（写入侧）
        // 2. String 经 defaultTyping RedisTemplate 序列化/反序列化（Redis 层）
        // 3. String 经 base64 解码回 byte[]（读取侧）
        byte[] qrCode = {0x01, 0x02, 0x03, 0x04, (byte) 0xFF, 0x00, 0x7F};
        String base64 = Base64.getEncoder().encodeToString(qrCode);

        byte[] bytes = objectMapper.writeValueAsBytes(base64);
        Object deserialized = objectMapper.readValue(bytes, Object.class);

        assertInstanceOf(String.class, deserialized, "base64 String 应正确往返为 String");
        String back = (String) deserialized;
        assertEquals(base64, back);
        byte[] decoded = Base64.getDecoder().decode(back);
        assertEquals(qrCode.length, decoded.length);
        for (int i = 0; i < qrCode.length; i++) {
            assertEquals(qrCode[i], decoded[i], "byte[" + i + "] 应一致");
        }
    }

    @Test
    @DisplayName("非白名单类（java.net.URL）反序列化被拦截——gadget 攻击面防护")
    void nonWhitelistedTypeIsBlocked() {
        // 构造恶意 JSON：类型标识指向 java.net（不在白名单），模拟 gadget 攻击 payload
        String maliciousJson = "[\"java.net.URL\",\"http://evil.example.com\"]";

        // 反序列化应抛异常，阻止非白名单类被实例化
        assertThrows(Exception.class,
                () -> objectMapper.readValue(maliciousJson.getBytes(), Object.class),
                "非白名单类 java.net.URL 必须被 defaultTyping 校验拦截");
    }

    @Test
    @DisplayName("白名单收紧后仍能区分 LinkedHashMap 与具体实体（防 CacheService instanceof 守卫失效）")
    void linkedHashMapIsNotArticle() {
        // 直接序列化一个 LinkedHashMap（模拟"移除 defaultTyping 后会得到的结果"），
        // 验证它不是 Article——反向佐证：保留 defaultTyping + 白名单是必要的。
        Map<String, Object> plainMap = new LinkedHashMap<>();
        plainMap.put("id", 1);
        plainMap.put("articleContent", "内容");
        byte[] bytes = objectMapper.writeValueAsBytes(plainMap);

        Object deserialized = objectMapper.readValue(bytes, Object.class);
        // LinkedHashMap 经 defaultTyping 仍还原为 LinkedHashMap
        assertInstanceOf(LinkedHashMap.class, deserialized);
        assertFalse(deserialized instanceof Article,
                "LinkedHashMap 不应被误判为 Article，验证 instanceof 守卫的判别能力");
    }
}
