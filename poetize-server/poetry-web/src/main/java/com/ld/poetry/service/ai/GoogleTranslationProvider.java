package com.ld.poetry.service.ai;
import com.ld.poetry.utils.JsonUtils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URI;

@Service
public class GoogleTranslationProvider extends AbstractApiTranslationProvider {

    private static final String GOOGLE_API_URL = "https://translation.googleapis.com/language/translate/v2";

    @Override
    public String providerKey() {
        return "google";
    }

    @Override
    public String displayName() {
        return "Google Cloud Translation";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
        String apiKey = required(config, "api_key");
        String source = TranslationLanguageMapper.map(providerKey(), sourceLang, true);
        String target = TranslationLanguageMapper.map(providerKey(), targetLang, false);
        String format = optional(config, "format", "text");
        String model = config.getString("model");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("q", text);
        if (StringUtils.hasText(source) && !"auto".equalsIgnoreCase(source)) {
            formData.add("source", source);
        }
        formData.add("target", target);
        formData.add("format", format);
        if (StringUtils.hasText(model)) {
            formData.add("model", model);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        URI uri = URI.create(GOOGLE_API_URL + "?key=" + encode(apiKey));
        ResponseEntity<String> response = exchange(uri, HttpMethod.POST, headers, formData);
        JsonUtils.JsonObj body = parseObject(response.getBody());
        JsonUtils.JsonObj data = body == null ? null : body.getJSONObject("data");
        JsonUtils.JsonArr translations = data == null ? null : data.getJSONArray("translations");
        if (translations != null && !translations.isEmpty()) {
            return translations.getJSONObject(0).getString("translatedText");
        }
        JsonUtils.JsonObj error = body == null ? null : body.getJSONObject("error");
        if (error != null) {
            throw new IllegalStateException("Google 翻译错误: " + error.getString("code")
                    + " " + error.getString("message"));
        }
        return null;
    }
}
