package com.ld.poetry.service.ai;
import com.ld.poetry.utils.JsonUtils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class HuaweiTranslationProvider extends AbstractApiTranslationProvider {

    @Override
    public String providerKey() {
        return "huawei";
    }

    @Override
    public String displayName() {
        return "华为云NLP翻译";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
        String endpoint = required(config, "endpoint");
        String projectId = required(config, "project_id");
        String authType = optional(config, "auth_type", "token");
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        String path = "/v1/" + projectId + "/machine-translation/text-translation";
        URI uri = URI.create(endpoint + path);

        JsonUtils.JsonObj payload = new JsonUtils.JsonObj(true);
        payload.put("text", text);
        payload.put("from", TranslationLanguageMapper.map(providerKey(), sourceLang, true));
        payload.put("to", TranslationLanguageMapper.map(providerKey(), targetLang, false));
        String scene = config.getString("scene");
        if (StringUtils.hasText(scene)) {
            payload.put("scene", scene);
        }
        String payloadText = payload.toJSONString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if ("aksk".equalsIgnoreCase(authType)) {
            applyAkSkSignature(headers, uri, payloadText, config);
        } else {
            headers.set("X-Auth-Token", required(config, "token"));
        }

        ResponseEntity<String> response = exchange(uri, HttpMethod.POST, headers, payloadText);
        JsonUtils.JsonObj body = parseObject(response.getBody());
        String translated = firstText(body, "translated_text", "translatedText");
        if (StringUtils.hasText(translated)) {
            return translated;
        }
        JsonUtils.JsonObj result = body == null ? null : body.getJSONObject("result");
        translated = firstText(result, "translated_text", "translatedText");
        if (StringUtils.hasText(translated)) {
            return translated;
        }
        String code = body == null ? null : firstText(body, "error_code", "code");
        String message = body == null ? null : firstText(body, "error_msg", "message");
        if (StringUtils.hasText(code) || StringUtils.hasText(message)) {
            throw new IllegalStateException("华为云错误: " + code + " " + message);
        }
        return null;
    }

    private void applyAkSkSignature(HttpHeaders headers, URI uri, String payloadText, JsonUtils.JsonObj config) {
        String accessKeyId = required(config, "access_key_id", "ak");
        String secretKey = required(config, "access_key_secret", "sk");
        String requestDateTime = utcTimestamp();
        String host = uri.getHost();
        String signedHeaders = "content-type;host;x-sdk-date";
        String canonicalHeaders = "content-type:application/json\n"
                + "host:" + host + "\n"
                + "x-sdk-date:" + requestDateTime + "\n";
        String canonicalRequest = "POST\n" + uri.getRawPath() + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + sha256Hex(payloadText);
        String stringToSign = "SDK-HMAC-SHA256\n" + requestDateTime + "\n" + sha256Hex(canonicalRequest);
        String signature = hmacSha256Hex(secretKey.getBytes(StandardCharsets.UTF_8), stringToSign);
        String authorization = "SDK-HMAC-SHA256 Access=" + accessKeyId
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
        headers.set("Host", host);
        headers.set("X-Sdk-Date", requestDateTime);
        headers.set("Authorization", authorization);
    }
}
