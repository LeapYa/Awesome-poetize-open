package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;

@Service
public class AzureTranslatorProvider extends AbstractApiTranslationProvider {

    @Override
    public String providerKey() {
        return "azure_translator";
    }

    @Override
    public String displayName() {
        return "Azure AI Translator";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String endpoint = optional(config, "endpoint", "https://api.cognitive.microsofttranslator.com");
        String subscriptionKey = required(config, "subscription_key", "api_key");
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

        String source = TranslationLanguageMapper.map(providerKey(), sourceLang, true);
        String target = TranslationLanguageMapper.map(providerKey(), targetLang, false);
        StringBuilder query = new StringBuilder("api-version=3.0");
        if (StringUtils.hasText(source) && !"auto".equalsIgnoreCase(source)) {
            query.append("&from=").append(encode(source));
        }
        query.append("&to=").append(encode(target));
        String category = config.getString("category");
        if (StringUtils.hasText(category)) {
            query.append("&category=").append(encode(category));
        }

        JSONArray payload = new JSONArray();
        JSONObject item = new JSONObject(true);
        item.put("Text", text);
        payload.add(item);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Ocp-Apim-Subscription-Key", subscriptionKey);
        String region = config.getString("region");
        if (StringUtils.hasText(region)) {
            headers.set("Ocp-Apim-Subscription-Region", region.trim());
        }

        ResponseEntity<String> response = exchange(
                URI.create(endpoint + "/translate?" + query),
                HttpMethod.POST,
                headers,
                payload.toJSONString());
        JSONArray body = JSONArray.parseArray(response.getBody());
        if (body == null || body.isEmpty()) {
            return null;
        }
        JSONArray translations = body.getJSONObject(0).getJSONArray("translations");
        return translations == null || translations.isEmpty() ? null : translations.getJSONObject(0).getString("text");
    }
}
