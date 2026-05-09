package com.ld.poetry.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YoudaoTranslationProviderTest {

    private YoudaoTranslationProvider provider;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        provider = new YoudaoTranslationProvider();
        ReflectionTestUtils.setField(provider, "restTemplate", restTemplate);
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void translateWithConfig_postsYoudaoFormAndExtractsTranslation() {
        server.expect(requestTo("https://openapi.youdao.com/api"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("q=Hello")))
                .andExpect(content().string(containsString("from=en")))
                .andExpect(content().string(containsString("to=zh-CHS")))
                .andExpect(content().string(containsString("appKey=app-key")))
                .andExpect(content().string(containsString("signType=v3")))
                .andRespond(withSuccess("{\"errorCode\":\"0\",\"translation\":[\"你好\"]}",
                        MediaType.APPLICATION_JSON));

        String translated = provider.translateWithConfig("Hello", "en", "zh", "app-key", "app-secret");

        assertEquals("你好", translated);
        server.verify();
    }

    @Test
    void translateArticleWithConfig_translatesTitleAndContentSeparately() {
        server.expect(requestTo("https://openapi.youdao.com/api"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errorCode\":\"0\",\"translation\":[\"Title\"]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.youdao.com/api"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errorCode\":\"0\",\"translation\":[\"Content\"]}",
                        MediaType.APPLICATION_JSON));

        Map<String, String> translated = provider.translateArticleWithConfig(
                "标题", "正文", "zh", "en", "app-key", "app-secret");

        assertEquals("Title", translated.get("title"));
        assertEquals("Content", translated.get("content"));
        assertEquals("en", translated.get("language"));
        server.verify();
    }
}
