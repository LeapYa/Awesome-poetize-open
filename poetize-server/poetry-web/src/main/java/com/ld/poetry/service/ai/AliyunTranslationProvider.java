package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AliyunTranslationProvider extends AbstractApiTranslationProvider {

    @Override
    public String providerKey() {
        return "aliyun";
    }

    @Override
    public String displayName() {
        return "阿里云机器翻译";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String accessKeyId = required(config, "access_key_id");
        String accessKeySecret = required(config, "access_key_secret");
        String region = optional(config, "region", "cn-hangzhou");
        String endpoint = optional(config, "endpoint", "mt." + region + ".aliyuncs.com");
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

        Map<String, String> params = new TreeMap<>();
        params.put("Action", "TranslateGeneral");
        params.put("Version", "2018-10-12");
        params.put("Format", "JSON");
        params.put("AccessKeyId", accessKeyId);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", nonce());
        params.put("Timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now(clock)));
        params.put("SourceLanguage", TranslationLanguageMapper.map(providerKey(), sourceLang, true));
        params.put("TargetLanguage", TranslationLanguageMapper.map(providerKey(), targetLang, false));
        params.put("SourceText", text);
        params.put("FormatType", optional(config, "format_type", "text"));
        params.put("Scene", optional(config, "scene", "general"));

        String canonicalQuery = canonicalQuery(params);
        String stringToSign = "POST&%2F&" + encode(canonicalQuery);
        params.put("Signature", hmacSha1Base64((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), stringToSign));

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        params.forEach(formData::add);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = exchange(URI.create(endpoint), HttpMethod.POST, headers, formData);
        JSONObject body = parseObject(response.getBody());
        JSONObject data = body == null ? null : body.getJSONObject("Data");
        String translated = data == null ? null : firstText(data, "Translated", "TranslatedText");
        if (StringUtils.hasText(translated)) {
            return translated;
        }
        String code = body == null ? null : firstText(body, "Code", "ErrorCode");
        String message = body == null ? null : firstText(body, "Message", "ErrorMessage");
        if (StringUtils.hasText(code) || StringUtils.hasText(message)) {
            throw new IllegalStateException("阿里云错误: " + code + " " + message);
        }
        return null;
    }

    private String canonicalQuery(Map<String, String> params) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (canonical.length() > 0) {
                canonical.append('&');
            }
            canonical.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return canonical.toString();
    }
}
