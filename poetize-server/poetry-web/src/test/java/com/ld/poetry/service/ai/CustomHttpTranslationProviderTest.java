package com.ld.poetry.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomHttpTranslationProviderTest {

    private CustomHttpTranslationProvider provider;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        provider = new CustomHttpTranslationProvider();
        ReflectionTestUtils.setField(provider, "restTemplate", restTemplate);
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void translateWithConfig_postsJsonAndExtractsTranslatedText() {
        server.expect(requestTo("https://example.com/translate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(header("X-API-Key", "test-key"))
                .andExpect(header("X-App-Secret", "secret"))
                .andExpect(content().json("""
                        {
                          "text": "Hello",
                          "source_lang": "en",
                          "target_lang": "zh",
                          "from": "en",
                          "to": "zh"
                        }
                        """))
                .andRespond(withSuccess("{\"translated_text\":\"你好\"}", MediaType.APPLICATION_JSON));

        String translated = provider.translateWithConfig(
                "Hello", "en", "zh", "https://example.com/translate", "test-key", "secret");

        assertEquals("你好", translated);
        server.verify();
    }

    @Test
    void translateWithConfig_acceptsPlainTextResponse() {
        server.expect(requestTo("https://example.com/translate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("Bonjour", MediaType.TEXT_PLAIN));

        String translated = provider.translateWithConfig(
                "Hello", "en", "fr", "https://example.com/translate", null, null);

        assertEquals("Bonjour", translated);
        server.verify();
    }

    @Test
    void translateArticleWithConfig_translatesTitleAndContentSeparately() {
        server.expect(requestTo("https://example.com/translate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"result\":\"Title\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.com/translate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"translation\":\"Content\"}", MediaType.APPLICATION_JSON));

        Map<String, String> translated = provider.translateArticleWithConfig(
                "标题", "正文", "zh", "en", "https://example.com/translate", null, null);

        assertEquals("Title", translated.get("title"));
        assertEquals("Content", translated.get("content"));
        assertEquals("en", translated.get("language"));
        server.verify();
    }
}
