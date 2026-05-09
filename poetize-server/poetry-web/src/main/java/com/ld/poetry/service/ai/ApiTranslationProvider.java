package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSONObject;

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

    String translate(String text, String sourceLang, String targetLang, JSONObject config);

    default Map<String, String> translateArticle(String title, String content, String sourceLang,
            String targetLang, JSONObject config) {
        String translatedTitle = translate(title, sourceLang, targetLang, config);
        String translatedContent = translate(content, sourceLang, targetLang, config);
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

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
