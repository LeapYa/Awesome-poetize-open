package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 HTTP 翻译接口。用户必须填写完整接口 URL，本 provider 不做地址补全。
 */
@Slf4j
@Service
public class CustomHttpTranslationProvider extends AbstractApiTranslationProvider {

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Override
    public String providerKey() {
        return "custom";
    }

    @Override
    public String displayName() {
        return "自定义HTTP翻译";
    }

    /**
     * 兼容旧调用：从默认 article_ai.custom_config 读取自定义 HTTP 参数。
     */
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null || !StringUtils.hasText(config.getCustomConfig())) {
                log.error("自定义HTTP翻译配置未找到");
                return null;
            }
            return translate(text, sourceLang, targetLang, JSON.parseObject(config.getCustomConfig()));
        } catch (Exception e) {
            log.error("自定义HTTP翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public String translateWithConfig(String text, String sourceLang, String targetLang, String apiUrl,
            String apiKey, String appSecret) {
        JSONObject config = new JSONObject();
        config.put("api_url", apiUrl);
        config.put("api_key", apiKey);
        config.put("app_secret", appSecret);
        return translate(text, sourceLang, targetLang, config);
    }

    public java.util.Map<String, String> translateArticleWithConfig(String title, String content, String sourceLang,
            String targetLang, String apiUrl, String apiKey, String appSecret) {
        JSONObject config = new JSONObject();
        config.put("api_url", apiUrl);
        config.put("api_key", apiKey);
        config.put("app_secret", appSecret);
        return translateArticle(title, content, sourceLang, targetLang, config);
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String apiUrl = required(config, "api_url", "url", "endpoint");
        String fromLang = StringUtils.hasText(sourceLang) ? sourceLang.trim() : "auto";
        String toLang = StringUtils.hasText(targetLang) ? targetLang.trim() : "en";

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("text", text);
        requestBody.put("source_lang", fromLang);
        requestBody.put("target_lang", toLang);
        requestBody.put("from", fromLang);
        requestBody.put("to", toLang);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        headers.set("User-Agent", "Poetize-Custom-Translator");
        applyAuthHeaders(headers, config);

        ResponseEntity<String> response = exchange(URI.create(apiUrl.trim()), HttpMethod.POST, headers, requestBody);
        String translated = extractTranslatedText(response.getBody());
        if (!StringUtils.hasText(translated)) {
            log.error("自定义HTTP翻译响应中未找到翻译文本: {}", abbreviate(response.getBody()));
            return null;
        }
        return translated;
    }

    private void applyAuthHeaders(HttpHeaders headers, JSONObject config) {
        String apiKey = firstText(config, "api_key", "token");
        if (StringUtils.hasText(apiKey)) {
            String trimmedApiKey = apiKey.trim();
            if (startsWithIgnoreCase(trimmedApiKey, "Bearer ") || startsWithIgnoreCase(trimmedApiKey, "Basic ")) {
                headers.set(HttpHeaders.AUTHORIZATION, trimmedApiKey);
            } else {
                headers.setBearerAuth(trimmedApiKey);
            }
            headers.set("X-API-Key", trimmedApiKey);
        }

        String appSecret = config.getString("app_secret");
        if (StringUtils.hasText(appSecret)) {
            String trimmedSecret = appSecret.trim();
            headers.set("X-App-Secret", trimmedSecret);
            headers.set("X-API-Secret", trimmedSecret);
        }
    }

    private String extractTranslatedText(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }

        String trimmed = responseBody.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }

        try {
            return valueToText(JSON.parse(trimmed));
        } catch (Exception e) {
            log.warn("自定义HTTP翻译响应不是标准JSON，按纯文本处理: {}", e.getMessage());
            return trimmed;
        }
    }

    private String valueToText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof JSONObject json) {
            return textFromObject(json);
        }
        if (value instanceof JSONArray array) {
            return textFromArray(array);
        }
        return String.valueOf(value);
    }

    private String textFromObject(JSONObject json) {
        String direct = firstText(json,
                "translated_text",
                "translatedText",
                "translation",
                "target_text",
                "targetText",
                "translated",
                "dst",
                "text",
                "content",
                "message",
                "output");
        if (StringUtils.hasText(direct)) {
            return direct;
        }

        String resultText = valueToText(json.get("result"));
        if (StringUtils.hasText(resultText)) {
            return resultText;
        }
        String dataText = valueToText(json.get("data"));
        if (StringUtils.hasText(dataText)) {
            return dataText;
        }
        String openAiText = textFromOpenAiChoices(json.getJSONArray("choices"));
        if (StringUtils.hasText(openAiText)) {
            return openAiText;
        }
        String translationsText = textFromArray(json.getJSONArray("translations"));
        if (StringUtils.hasText(translationsText)) {
            return translationsText;
        }
        JSONArray baiduStyle = json.getJSONArray("trans_result");
        return baiduStyle == null ? null : textFromArray(baiduStyle);
    }

    private String textFromArray(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        for (Object item : array) {
            String text = valueToText(item);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String textFromOpenAiChoices(JSONArray choices) {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        if (!(first instanceof JSONObject choice)) {
            return valueToText(first);
        }
        JSONObject message = choice.getJSONObject("message");
        if (message != null) {
            String content = message.getString("content");
            if (StringUtils.hasText(content)) {
                return content;
            }
        }
        return choice.getString("text");
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
