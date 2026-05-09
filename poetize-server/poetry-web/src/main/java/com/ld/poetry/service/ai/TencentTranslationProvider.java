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
import java.time.Instant;

@Service
public class TencentTranslationProvider extends AbstractApiTranslationProvider {

    private static final String SERVICE = "tmt";
    private static final String HOST = "tmt.tencentcloudapi.com";
    private static final String VERSION = "2018-03-21";
    private static final String ACTION = "TextTranslate";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    @Override
    public String providerKey() {
        return "tencent";
    }

    @Override
    public String displayName() {
        return "腾讯云机器翻译";
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String secretId = required(config, "secret_id");
        String secretKey = required(config, "secret_key");
        String region = optional(config, "region", "ap-guangzhou");
        int projectId = config.getInteger("project_id") == null ? 0 : config.getInteger("project_id");

        JSONObject payload = new JSONObject(true);
        payload.put("SourceText", text);
        payload.put("Source", TranslationLanguageMapper.map(providerKey(), sourceLang, true));
        payload.put("Target", TranslationLanguageMapper.map(providerKey(), targetLang, false));
        payload.put("ProjectId", projectId);
        String payloadText = payload.toJSONString();

        long timestamp = Instant.now(clock).getEpochSecond();
        String date = utcDate();
        String canonicalHeaders = "content-type:application/json\nhost:" + HOST + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + sha256Hex(payloadText);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = hmacSha256Hex(secretSigning, stringToSign);
        String authorization = ALGORITHM
                + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Host", HOST);
        headers.set("Authorization", authorization);
        headers.set("X-TC-Action", ACTION);
        headers.set("X-TC-Timestamp", String.valueOf(timestamp));
        headers.set("X-TC-Version", VERSION);
        headers.set("X-TC-Region", region);

        ResponseEntity<String> response = exchange(URI.create("https://" + HOST), HttpMethod.POST, headers, payloadText);
        JSONObject body = parseObject(response.getBody());
        JSONObject responseObject = body == null ? null : body.getJSONObject("Response");
        if (responseObject == null) {
            return null;
        }
        JSONObject error = responseObject.getJSONObject("Error");
        if (error != null) {
            throw new IllegalStateException("腾讯云错误: " + error.getString("Code") + " " + error.getString("Message"));
        }
        String translated = responseObject.getString("TargetText");
        return StringUtils.hasText(translated) ? translated : null;
    }
}
