package com.ld.poetry.service.ai;
import com.ld.poetry.utils.JsonUtils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class VolcengineTranslationProvider extends AbstractApiTranslationProvider {

    private static final String SERVICE = "translate";
    private static final String DEFAULT_HOST = "translate.volcengineapi.com";

    @Override
    public String providerKey() {
        return "volcengine";
    }

    @Override
    public String displayName() {
        return "火山引擎机器翻译";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
        String accessKeyId = required(config, "access_key_id");
        String secretKey = required(config, "secret_key");
        String region = optional(config, "region", "cn-north-1");
        String host = optional(config, "host", DEFAULT_HOST);
        String action = optional(config, "action", "TranslateText");
        String version = optional(config, "version", "2020-06-01");
        String query = "Action=" + encode(action) + "&Version=" + encode(version);
        URI uri = URI.create("https://" + host + "/?" + query);

        JsonUtils.JsonObj payload = new JsonUtils.JsonObj(true);
        JsonUtils.JsonArr texts = new JsonUtils.JsonArr();
        texts.add(text);
        payload.put("TextList", texts);
        payload.put("SourceLanguage", TranslationLanguageMapper.map(providerKey(), sourceLang, true));
        payload.put("TargetLanguage", TranslationLanguageMapper.map(providerKey(), targetLang, false));
        String payloadText = payload.toJSONString();
        String payloadHash = sha256Hex(payloadText);
        String requestDateTime = utcTimestamp();
        String requestDate = utcDate();
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalHeaders = "content-type:application/json\n"
                + "host:" + host + "\n"
                + "x-content-sha256:" + payloadHash + "\n"
                + "x-date:" + requestDateTime + "\n";
        String canonicalRequest = "POST\n/\n" + query + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String credentialScope = requestDate + "/" + region + "/" + SERVICE + "/request";
        String stringToSign = "HMAC-SHA256\n" + requestDateTime + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] kDate = hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8), requestDate);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        byte[] kSigning = hmacSha256(kService, "request");
        String signature = hmacSha256Hex(kSigning, stringToSign);
        String authorization = "HMAC-SHA256 Credential=" + accessKeyId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Host", host);
        headers.set("X-Date", requestDateTime);
        headers.set("X-Content-Sha256", payloadHash);
        headers.set("Authorization", authorization);

        ResponseEntity<String> response = exchange(uri, HttpMethod.POST, headers, payloadText);
        JsonUtils.JsonObj body = parseObject(response.getBody());
        JsonUtils.JsonObj metadata = body == null ? null : body.getJSONObject("ResponseMetadata");
        JsonUtils.JsonObj error = metadata == null ? null : metadata.getJSONObject("Error");
        if (error != null) {
            throw new IllegalStateException("火山引擎错误: "
                    + error.getString("Code") + " " + error.getString("Message"));
        }
        JsonUtils.JsonArr translations = body == null ? null : firstTranslationArray(body);
        if (translations == null || translations.isEmpty()) {
            return firstText(body, "Translation", "TranslatedText");
        }
        JsonUtils.JsonObj first = translations.getJSONObject(0);
        return first == null ? null : firstText(first, "Translation", "TranslatedText", "Text");
    }

    private JsonUtils.JsonArr firstTranslationArray(JsonUtils.JsonObj body) {
        JsonUtils.JsonArr array = body.getJSONArray("TranslationList");
        if (array != null) {
            return array;
        }
        JsonUtils.JsonObj result = body.getJSONObject("Result");
        return result == null ? null : result.getJSONArray("TranslationList");
    }
}
