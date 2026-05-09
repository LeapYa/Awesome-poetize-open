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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;

/**
 * 有道智云文本翻译 API。
 */
@Slf4j
@Service
public class YoudaoTranslationProvider extends AbstractApiTranslationProvider {

    private static final String YOUDAO_API_URL = "https://openapi.youdao.com/api";

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Override
    public String providerKey() {
        return "youdao";
    }

    @Override
    public String displayName() {
        return "有道云翻译";
    }

    /**
     * 兼容旧调用：从默认 article_ai.custom_config 读取有道参数。
     */
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null || !StringUtils.hasText(config.getCustomConfig())) {
                log.error("有道云翻译配置未找到");
                return null;
            }
            return translate(text, sourceLang, targetLang, JSON.parseObject(config.getCustomConfig()));
        } catch (Exception e) {
            log.error("有道云翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public String translateWithConfig(String text, String sourceLang, String targetLang,
            String appKey, String appSecret) {
        JSONObject config = new JSONObject();
        config.put("app_key", appKey);
        config.put("app_secret", appSecret);
        return translate(text, sourceLang, targetLang, config);
    }

    public java.util.Map<String, String> translateArticleWithConfig(String title, String content, String sourceLang,
            String targetLang, String appKey, String appSecret) {
        JSONObject config = new JSONObject();
        config.put("app_key", appKey);
        config.put("app_secret", appSecret);
        return translateArticle(title, content, sourceLang, targetLang, config);
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String appKey = required(config, "app_key", "api_key");
        String appSecret = required(config, "app_secret");
        String from = TranslationLanguageMapper.map(providerKey(), sourceLang, true);
        String to = TranslationLanguageMapper.map(providerKey(), targetLang, false);
        String salt = nonce();
        String curtime = String.valueOf(Instant.now(clock).getEpochSecond());
        String sign = sha256Hex(appKey + truncate(text) + salt + curtime + appSecret);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("q", text);
        formData.add("from", from);
        formData.add("to", to);
        formData.add("appKey", appKey);
        formData.add("salt", salt);
        formData.add("sign", sign);
        formData.add("signType", "v3");
        formData.add("curtime", curtime);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = exchange(URI.create(YOUDAO_API_URL), HttpMethod.POST, headers, formData);
        JSONObject json = parseObject(response.getBody());
        if (json == null) {
            return null;
        }
        String errorCode = json.getString("errorCode");
        if (StringUtils.hasText(errorCode) && !"0".equals(errorCode)) {
            log.error("有道云翻译 API 错误: code={}, body={}", errorCode, abbreviate(response.getBody()));
            return null;
        }
        JSONArray translations = json.getJSONArray("translation");
        return translations == null || translations.isEmpty() ? null : translations.getString(0);
    }

    private String truncate(String text) {
        int length = text.length();
        if (length <= 20) {
            return text;
        }
        return text.substring(0, 10) + length + text.substring(length - 10);
    }
}
