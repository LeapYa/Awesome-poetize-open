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
public class DeepLTranslationProvider extends AbstractApiTranslationProvider {

    @Override
    public String providerKey() {
        return "deepl";
    }

    @Override
    public String displayName() {
        return "DeepL";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
        String authKey = required(config, "auth_key", "api_key");
        String endpoint = endpoint(config);
        String source = TranslationLanguageMapper.map(providerKey(), sourceLang, true);
        String target = TranslationLanguageMapper.map(providerKey(), targetLang, false);

        JsonUtils.JsonObj payload = new JsonUtils.JsonObj(true);
        JsonUtils.JsonArr texts = new JsonUtils.JsonArr();
        texts.add(text);
        payload.put("text", texts);
        if (StringUtils.hasText(source) && !"auto".equalsIgnoreCase(source)) {
            payload.put("source_lang", source);
        }
        payload.put("target_lang", target);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "DeepL-Auth-Key " + authKey);

        ResponseEntity<String> response = exchange(URI.create(endpoint), HttpMethod.POST, headers, payload.toJSONString());
        JsonUtils.JsonObj body = parseObject(response.getBody());
        JsonUtils.JsonArr translations = body == null ? null : body.getJSONArray("translations");
        return translations == null || translations.isEmpty() ? null : translations.getJSONObject(0).getString("text");
    }

    private String endpoint(JsonUtils.JsonObj config) {
        String apiUrl = config.getString("api_url");
        if (StringUtils.hasText(apiUrl)) {
            return apiUrl.trim();
        }
        String endpointType = optional(config, "endpoint_type", "free");
        return "pro".equalsIgnoreCase(endpointType)
                ? "https://api.deepl.com/v2/translate"
                : "https://api-free.deepl.com/v2/translate";
    }
}
