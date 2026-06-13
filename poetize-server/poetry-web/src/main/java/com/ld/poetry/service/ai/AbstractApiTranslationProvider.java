package com.ld.poetry.service.ai;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.utils.RetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractApiTranslationProvider implements ApiTranslationProvider {

    protected static final DateTimeFormatter BASIC_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    @Autowired
    protected RestTemplate restTemplate;

    protected Clock clock = Clock.systemUTC();

    protected abstract String doTranslate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config)
            throws Exception;

    @Override
    public String translate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config) {
        return translate(text, sourceLang, targetLang, config, null);
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang, JsonUtils.JsonObj config,
            TranslationService.TranslationProgressListener progressListener) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        try {
            String translated = RetryUtil.executeWithRetry(() -> {
                try {
                    return doTranslate(text, sourceLang, targetLang, config == null ? new JsonUtils.JsonObj() : config);
                } catch (Exception e) {
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(e);
                }
            }, 3, 1000, displayName() + "翻译", (attempt, maxAttempts, throwable) -> {
                if (progressListener != null) {
                    Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("attempt", attempt);
                    payload.put("maxAttempts", maxAttempts);
                    payload.put("message", displayName() + "翻译第" + attempt + "次失败，正在重试(" + attempt + "/" + maxAttempts + ")...");
                    payload.put("retryable", attempt < maxAttempts);
                    progressListener.onEvent("retry", payload);
                }
            });
            return StringUtils.hasText(translated) ? translated.trim() : null;
        } catch (RestClientResponseException e) {
            log.error("{} API 错误: status={}, body={}",
                    displayName(), e.getStatusCode(), abbreviate(e.getResponseBodyAsString()), e);
            return null;
        } catch (Exception e) {
            log.error("{} API 调用失败: {}", displayName(), e.getMessage(), e);
            return null;
        }
    }

    protected ResponseEntity<String> exchange(URI uri, HttpMethod method, HttpHeaders headers, Object body) {
        return restTemplate.exchange(uri, method, new HttpEntity<>(body, headers), String.class);
    }

    protected JsonUtils.JsonObj parseObject(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        return JsonUtils.parseObject(responseBody);
    }

    protected String firstText(JsonUtils.JsonObj json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    protected String firstText(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    protected String required(JsonUtils.JsonObj config, String... keys) {
        String value = firstText(config, keys);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(displayName() + " 缺少配置: " + String.join("/", keys));
        }
        return value.trim();
    }

    protected String optional(JsonUtils.JsonObj config, String key, String defaultValue) {
        String value = config == null ? null : config.getString(key);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    protected String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    protected String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    protected String md5Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    protected byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    protected String hmacSha256Hex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmacSha256(key, data));
    }

    protected byte[] hmacSha1(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 计算失败", e);
        }
    }

    protected String hmacSha1Base64(byte[] key, String data) {
        return Base64.getEncoder().encodeToString(hmacSha1(key, data));
    }

    protected String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    protected String utcTimestamp() {
        return BASIC_TIME_FORMATTER.format(Instant.now(clock));
    }

    protected String utcDate() {
        return DATE_FORMATTER.format(Instant.now(clock));
    }

    protected String nonce() {
        return UUID.randomUUID().toString();
    }

    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    protected String extractFirstFromArray(JsonUtils.JsonObj json, String arrayKey, String textKey) {
        if (json == null) {
            return null;
        }
        JsonUtils.JsonArr array = json.getJSONArray(arrayKey);
        if (array == null || array.size() == 0) {
            return null;
        }
        JsonUtils.JsonObj first = array.getJSONObject(0);
        return first == null ? null : first.getString(textKey);
    }

    protected String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }
}
