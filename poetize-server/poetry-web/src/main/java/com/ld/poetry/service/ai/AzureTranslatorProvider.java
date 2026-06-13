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
    protected String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
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

        JsonUtils.JsonArr payload = new JsonUtils.JsonArr();
        JsonUtils.JsonObj item = new JsonUtils.JsonObj(true);
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
        // Azure Translator API 返回数组: [{translations:[{text:"..."}]}]
        JsonUtils.JsonArr outerArray = JsonUtils.parseArray(response.getBody());
        if (outerArray == null || outerArray.size() == 0) {
            return null;
        }
        JsonUtils.JsonObj firstElement = outerArray.getJSONObject(0);
        if (firstElement == null) {
            return null;
        }
        JsonUtils.JsonArr translations = firstElement.getJSONArray("translations");
        return translations == null || translations.size() == 0 ? null : translations.getJSONObject(0).getString("text");
    }
}
