package com.ld.poetry.service.ai;
import com.ld.poetry.utils.JsonUtils;

import com.ld.poetry.service.TranslationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 传统机器翻译 API 服务商适配接口。
 */
public interface ApiTranslationProvider {

    String providerKey();

    default String displayName() {
        return providerKey();
    }

    String translate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config);

    default Map<String, String> translateArticle(String title, String content, String sourceLang,
            String targetLang, JsonUtils.JsonObj config) {
        return translateArticle(title, content, sourceLang, targetLang, config, null);
    }

    default Map<String, String> translateArticle(String title, String content, String sourceLang,
            String targetLang, JsonUtils.JsonObj config,
            TranslationService.TranslationProgressListener progressListener) {
        String translatedTitle = translate(title, sourceLang, targetLang, config, progressListener);
        String translatedContent = translate(content, sourceLang, targetLang, config, progressListener);
        if (hasText(translatedTitle)
                && hasText(translatedContent)
                && !translatedTitle.equals(title)
                && !translatedContent.equals(content)) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("title", translatedTitle);
            result.put("content", translatedContent);
            result.put("language", targetLang);
            return result;
        }
        return null;
    }

    default String translate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config,
            TranslationService.TranslationProgressListener progressListener) {
        return translate(text, sourceLang, targetLang, config);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
