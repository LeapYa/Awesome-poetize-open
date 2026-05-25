package com.ld.poetry.controller;

import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.ApiTranslationProvider;
import com.ld.poetry.service.ai.ApiTranslationProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TranslationApiControllerTest {

    @Test
    void adminTestEndpointsRequireArticleEditorLogin() throws NoSuchMethodException {
        assertArticleEditorOnly("testTranslateText");
        assertArticleEditorOnly("testGenerateSummary");
        assertArticleEditorOnly("testConnection");
        assertArticleEditorOnly("testToonFormat");
    }

    @Test
    void testTranslateText_dispatchesTempConfigThroughProviderRegistry() {
        TranslationApiController controller = new TranslationApiController();
        RecordingProvider provider = new RecordingProvider("google", "Google Cloud Translation", "你好");
        SysAiConfigService sysAiConfigService = mock(SysAiConfigService.class);
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(null);

        ReflectionTestUtils.setField(controller, "apiTranslationProviderRegistry",
                new ApiTranslationProviderRegistry(List.of(provider)));
        ReflectionTestUtils.setField(controller, "sysAiConfigService", sysAiConfigService);

        PoetryResult<Map<String, Object>> result = controller.testTranslateText(Map.of(
                "text", "Hello",
                "config", Map.of(
                        "type", "api",
                        "provider", "google",
                        "default_source_lang", "en",
                        "default_target_lang", "zh",
                        "google", Map.of("api_key", "key"))));

        assertTrue(result.isSuccess());
        assertEquals("你好", result.getData().get("translated_text"));
        assertEquals("google", result.getData().get("engine"));
        assertEquals("Hello", provider.lastText);
        assertEquals("en", provider.lastSourceLang);
        assertEquals("zh", provider.lastTargetLang);
        assertEquals("key", provider.lastConfig.getString("api_key"));
    }

    @Test
    void testTranslateText_returnsFailureWhenApiProviderReturnsEmpty() {
        TranslationApiController controller = new TranslationApiController();
        RecordingProvider provider = new RecordingProvider("deepl", "DeepL", null);
        SysAiConfigService sysAiConfigService = mock(SysAiConfigService.class);
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(new SysAiConfig());

        ReflectionTestUtils.setField(controller, "apiTranslationProviderRegistry",
                new ApiTranslationProviderRegistry(List.of(provider)));
        ReflectionTestUtils.setField(controller, "sysAiConfigService", sysAiConfigService);

        PoetryResult<Map<String, Object>> result = controller.testTranslateText(Map.of(
                "text", "Hello",
                "config", Map.of(
                        "type", "api",
                        "provider", "deepl",
                        "default_source_lang", "en",
                        "default_target_lang", "zh",
                        "deepl", Map.of("auth_key", "key"))));

        assertEquals(500, result.getCode());
        assertEquals("DeepL返回空翻译结果", result.getMessage());
    }

    @Test
    void parseSummaryResponse_unwrapsNestedJsonSummaries() {
        TranslationApiController controller = new TranslationApiController();
        Map<String, String> languages = Map.of("zh", "内容", "en", "");

        @SuppressWarnings("unchecked")
        Map<String, String> summaries = (Map<String, String>) ReflectionTestUtils.invokeMethod(
                controller,
                "parseSummaryResponse",
                "{\"summaries\":{\"en\":\"English summary\",\"zh\":\"中文摘要\"}}",
                languages);

        assertEquals(2, summaries.size());
        assertEquals("English summary", summaries.get("en"));
        assertEquals("中文摘要", summaries.get("zh"));
        assertTrue(!summaries.containsKey("summaries"));
    }

    @Test
    void parseSummaryResponse_extractsJsonFromMarkdownBlock() {
        TranslationApiController controller = new TranslationApiController();
        Map<String, String> languages = Map.of("zh", "内容", "en", "");

        @SuppressWarnings("unchecked")
        Map<String, String> summaries = (Map<String, String>) ReflectionTestUtils.invokeMethod(
                controller,
                "parseSummaryResponse",
                "结果如下：\n```json\n{\"summaries\":{\"en\":\"English summary\",\"zh\":\"中文摘要\"}}\n```",
                languages);

        assertEquals("English summary", summaries.get("en"));
        assertEquals("中文摘要", summaries.get("zh"));
    }

    @Test
    void parseSummaryResponse_acceptsPrettyJsonLanguageMap() {
        TranslationApiController controller = new TranslationApiController();
        Map<String, String> languages = Map.of("zh", "内容", "en", "");

        @SuppressWarnings("unchecked")
        Map<String, String> summaries = (Map<String, String>) ReflectionTestUtils.invokeMethod(
                controller,
                "parseSummaryResponse",
                """
                        {
                          "en": "English summary",
                          "zh": "中文摘要"
                        }
                        """,
                languages);

        assertEquals("English summary", summaries.get("en"));
        assertEquals("中文摘要", summaries.get("zh"));
    }

    @Test
    void parseSummaryResponse_extractsNestedToonFromWrapper() {
        TranslationApiController controller = new TranslationApiController();
        Map<String, String> languages = Map.of("zh", "内容", "en", "");

        @SuppressWarnings("unchecked")
        Map<String, String> summaries = (Map<String, String>) ReflectionTestUtils.invokeMethod(
                controller,
                "parseSummaryResponse",
                """
                        结果如下：
                        ```toon
                        result:
                          summaries:
                            en: English summary
                            zh: 中文摘要
                        ```
                        """,
                languages);

        assertEquals("English summary", summaries.get("en"));
        assertEquals("中文摘要", summaries.get("zh"));
    }

    @Test
    void parseSummaryResponse_keepsJsonLanguageMap() {
        TranslationApiController controller = new TranslationApiController();
        Map<String, String> languages = Map.of("zh", "内容", "en", "");

        @SuppressWarnings("unchecked")
        Map<String, String> summaries = (Map<String, String>) ReflectionTestUtils.invokeMethod(
                controller,
                "parseSummaryResponse",
                "{\"en\":\"English summary\",\"zh\":\"中文摘要\"}",
                languages);

        assertEquals("English summary", summaries.get("en"));
        assertEquals("中文摘要", summaries.get("zh"));
    }

    private static class RecordingProvider implements ApiTranslationProvider {
        private final String key;
        private final String name;
        private final String translated;
        private String lastText;
        private String lastSourceLang;
        private String lastTargetLang;
        private JSONObject lastConfig;

        private RecordingProvider(String key, String name, String translated) {
            this.key = key;
            this.name = name;
            this.translated = translated;
        }

        @Override
        public String providerKey() {
            return key;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public String translate(String text, String sourceLang, String targetLang, JSONObject config) {
            this.lastText = text;
            this.lastSourceLang = sourceLang;
            this.lastTargetLang = targetLang;
            this.lastConfig = config;
            return translated;
        }
    }

    private void assertArticleEditorOnly(String methodName) throws NoSuchMethodException {
        Method method = TranslationApiController.class.getDeclaredMethod(methodName, Map.class);
        LoginCheck loginCheck = method.getAnnotation(LoginCheck.class);

        assertNotNull(loginCheck);
        assertEquals(1, loginCheck.value());
    }
}
