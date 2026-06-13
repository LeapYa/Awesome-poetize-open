package com.ld.poetry.service.ai;
import com.ld.poetry.utils.JsonUtils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;

@Service
public class YandexTranslationProvider extends AbstractApiTranslationProvider {

    private static final String YANDEX_API_URL = "https://translate.api.cloud.yandex.net/translate/v2/translate";

    @Override
    public String providerKey() {
        return "yandex";
    }

    @Override
    public String displayName() {
        return "Yandex Cloud Translate";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
        String token = required(config, "api_key_or_iam_token", "api_key", "iam_token");
        JsonUtils.JsonObj payload = new JsonUtils.JsonObj(true);
        String source = TranslationLanguageMapper.map(providerKey(), sourceLang, true);
        if (StringUtils.hasText(source) && !"auto".equalsIgnoreCase(source)) {
            payload.put("sourceLanguageCode", source);
        }
        payload.put("targetLanguageCode", TranslationLanguageMapper.map(providerKey(), targetLang, false));
        payload.put("format", optional(config, "format", "PLAIN_TEXT"));
        JsonUtils.JsonArr texts = new JsonUtils.JsonArr();
        texts.add(text);
        payload.put("texts", texts);
        String folderId = config.getString("folder_id");
        if (StringUtils.hasText(folderId)) {
            payload.put("folderId", folderId.trim());
        }
        String model = config.getString("model");
        if (StringUtils.hasText(model)) {
            payload.put("model", model.trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyAuth(headers, token);

        ResponseEntity<String> response = exchange(
                URI.create(YANDEX_API_URL), HttpMethod.POST, headers, payload.toJSONString());
        JsonUtils.JsonObj body = parseObject(response.getBody());
        JsonUtils.JsonArr translations = body == null ? null : body.getJSONArray("translations");
        return translations == null || translations.isEmpty() ? null : translations.getJSONObject(0).getString("text");
    }

    private void applyAuth(HttpHeaders headers, String token) {
        String trimmed = token.trim();
        if (startsWithIgnoreCase(trimmed, "Bearer ")) {
            headers.set(HttpHeaders.AUTHORIZATION, trimmed);
        } else if (startsWithIgnoreCase(trimmed, "Api-Key ")) {
            headers.set(HttpHeaders.AUTHORIZATION, trimmed);
        } else if (trimmed.startsWith("t1.")) {
            headers.setBearerAuth(trimmed);
        } else {
            headers.set(HttpHeaders.AUTHORIZATION, "Api-Key " + trimmed);
        }
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
