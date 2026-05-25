package com.ld.poetry.service.impl;

import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.ApiTranslationProviderRegistry;
import com.ld.poetry.service.ai.LlmTranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TranslationServiceImplApiProviderTest {

    @Test
    void translateText_doesNotFallbackToLlmWhenApiProviderFails() {
        TranslationServiceImpl service = new TranslationServiceImpl();
        SysAiConfigService sysAiConfigService = mock(SysAiConfigService.class);
        ApiTranslationProviderRegistry registry = mock(ApiTranslationProviderRegistry.class);
        LlmTranslationService llmTranslationService = mock(LlmTranslationService.class);

        SysAiConfig config = new SysAiConfig();
        config.setTranslationType("google");
        when(sysAiConfigService.getArticleAiConfig("default")).thenReturn(config);
        when(registry.isApiProvider("google")).thenReturn(true);
        when(registry.translateText(config, "Hello", "en", "zh")).thenReturn(null);

        ReflectionTestUtils.setField(service, "sysAiConfigService", sysAiConfigService);
        ReflectionTestUtils.setField(service, "apiTranslationProviderRegistry", registry);
        ReflectionTestUtils.setField(service, "llmTranslationService", llmTranslationService);

        assertNull(service.translateText("Hello", "en", "zh"));
        verify(registry).translateText(config, "Hello", "en", "zh");
        verifyNoInteractions(llmTranslationService);
    }

    @Test
    void translateArticleOnly_dispatchesApiProviderThroughRegistry() {
        TranslationServiceImpl service = new TranslationServiceImpl();
        SysAiConfigService sysAiConfigService = mock(SysAiConfigService.class);
        ApiTranslationProviderRegistry registry = mock(ApiTranslationProviderRegistry.class);
        LlmTranslationService llmTranslationService = mock(LlmTranslationService.class);

        SysAiConfig config = new SysAiConfig();
        config.setTranslationType("deepl");
        when(sysAiConfigService.getArticleAiConfig("default")).thenReturn(config);
        when(sysAiConfigService.getDefaultLanguages()).thenReturn(Map.of(
                "default_source_lang", "en",
                "default_target_lang", "zh"));
        when(registry.isApiProvider("deepl")).thenReturn(true);
        when(registry.translateArticle(config, "Title", "Content", "en", "zh", null))
                .thenReturn(null);

        ReflectionTestUtils.setField(service, "sysAiConfigService", sysAiConfigService);
        ReflectionTestUtils.setField(service, "apiTranslationProviderRegistry", registry);
        ReflectionTestUtils.setField(service, "llmTranslationService", llmTranslationService);

        assertNull(service.translateArticleOnly("Title", "Content", false, null));
        verify(registry).translateArticle(config, "Title", "Content", "en", "zh", null);
        verifyNoInteractions(llmTranslationService);
    }
}
