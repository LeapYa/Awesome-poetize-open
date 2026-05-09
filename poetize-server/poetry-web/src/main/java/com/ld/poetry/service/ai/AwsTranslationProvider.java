package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class AwsTranslationProvider extends AbstractApiTranslationProvider {

    private static final String SERVICE = "translate";
    private static final String TARGET = "AWSShineFrontendService_20170701.TranslateText";

    @Override
    public String providerKey() {
        return "aws";
    }

    @Override
    public String displayName() {
        return "Amazon Translate";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String accessKeyId = required(config, "access_key_id");
        String secretAccessKey = required(config, "secret_access_key", "access_key_secret");
        String region = required(config, "region");
        String host = "translate." + region + ".amazonaws.com";
        URI uri = URI.create("https://" + host + "/");

        JSONObject payload = new JSONObject(true);
        payload.put("Text", text);
        payload.put("SourceLanguageCode", normalizeSource(sourceLang));
        payload.put("TargetLanguageCode", TranslationLanguageMapper.map(providerKey(), targetLang, false));
        String payloadText = payload.toJSONString();
        String payloadHash = sha256Hex(payloadText);
        String requestDateTime = utcTimestamp();
        String requestDate = utcDate();

        String sessionToken = config.getString("session_token");
        String signedHeaders = StringUtils.hasText(sessionToken)
                ? "content-type;host;x-amz-date;x-amz-security-token;x-amz-target"
                : "content-type;host;x-amz-date;x-amz-target";
        String canonicalHeaders = "content-type:application/x-amz-json-1.1\n"
                + "host:" + host + "\n"
                + "x-amz-date:" + requestDateTime + "\n"
                + (StringUtils.hasText(sessionToken) ? "x-amz-security-token:" + sessionToken.trim() + "\n" : "")
                + "x-amz-target:" + TARGET + "\n";
        String canonicalRequest = "POST\n/\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String credentialScope = requestDate + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + requestDateTime + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] kDate = hmacSha256(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), requestDate);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        String signature = hmacSha256Hex(kSigning, stringToSign);
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-amz-json-1.1"));
        headers.set("Host", host);
        headers.set("X-Amz-Date", requestDateTime);
        headers.set("X-Amz-Target", TARGET);
        headers.set("Authorization", authorization);
        if (StringUtils.hasText(sessionToken)) {
            headers.set("X-Amz-Security-Token", sessionToken.trim());
        }

        ResponseEntity<String> response = exchange(uri, HttpMethod.POST, headers, payloadText);
        JSONObject body = parseObject(response.getBody());
        String translated = body == null ? null : body.getString("TranslatedText");
        if (StringUtils.hasText(translated)) {
            return translated;
        }
        String message = body == null ? null : firstText(body, "message", "__type");
        if (StringUtils.hasText(message)) {
            throw new IllegalStateException("AWS 翻译错误: " + message);
        }
        return null;
    }

    private String normalizeSource(String sourceLang) {
        if (!StringUtils.hasText(sourceLang) || "auto".equalsIgnoreCase(sourceLang)) {
            return "auto";
        }
        return TranslationLanguageMapper.map(providerKey(), sourceLang, true);
    }
}
