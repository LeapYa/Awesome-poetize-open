package com.ld.poetry.service.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 爱发电支付 Provider
 * <p>
 * API 签名: MD5(token + "params" + json + "ts" + ts + "user_id" + userId)
 * Webhook 验签: RSA-SHA256 公钥验签, sign_str = out_trade_no + user_id + plan_id +
 * total_amount
 * </p>
 *
 * @author LeapYa
 * @since 2026-02-18
 */
@Slf4j
@Component
public class AfdianProvider implements PaymentProvider {

    private static final String API_BASE = "https://afdian.com";
    private static final JsonMapper objectMapper = JsonMapper.builder().build();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 爱发电 Webhook RSA 公钥
     */
    private static final String AFDIAN_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwwdaCg1Bt+UKZKs0R54y" +
            "lYnuANma49IpgoOwNmk3a0rhg/PQuhUJ0EOZSowIC44l0K3+fqGns3Ygi4AfmEfS" +
            "4EKbdk1ahSxu7Zkp2rHMt+R9GarQFQkwSS/5x1dYiHNVMiR8oIXDgjmvxuNes2Cr" +
            "8fw9dEF0xNBKdkKgG2qAawcN1nZrdyaKWtPVT9m2Hl0ddOO9thZmVLFOb9NVzgYf" +
            "jEgI+KWX6aY19Ka/ghv/L4t1IXmz9pctablN5S0CRWpJW3Cn0k6zSXgjVdKm4uN7" +
            "jRlgSRaf/Ind46vMCm3N2sgwxu/g3bnooW+db0iLo13zzuvyn727Q3UDQ0MmZcEW" +
            "MQIDAQAB";

    @Override
    public String getPlatformKey() {
        return "afdian";
    }

    @Override
    public String getPaymentUrl(Integer articleId, Integer userId, Map<String, Object> config) {
        String planId = (String) config.get("planId");
        if (planId == null || planId.isEmpty()) {
            throw new IllegalArgumentException("爱发电方案ID(planId)未配置");
        }

        String customOrderId = "u" + userId + "_a" + articleId;

        StringBuilder sb = new StringBuilder(API_BASE);
        sb.append("/order/create");
        sb.append("?plan_id=").append(urlEncode(planId));
        sb.append("&product_type=0");
        sb.append("&custom_order_id=").append(urlEncode(customOrderId));

        return sb.toString();
    }

    @Override
    public CallbackResult verifyCallback(HttpServletRequest request, Map<String, Object> config) {
        try {
            // 读取请求体
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            String requestBody = body.toString();
            log.info("爱发电 Webhook 收到回调: {}", requestBody);

            JsonNode root = objectMapper.readTree(requestBody);
            JsonNode data = root.get("data");
            if (data == null) {
                log.warn("爱发电回调数据缺少 data 字段");
                return null;
            }

            // 获取签名
            String signature = null;
            if (root.has("data") && root.get("data").has("sign")) {
                signature = root.get("data").get("sign").asText();
            }

            JsonNode order = data.get("order");
            if (order == null) {
                log.warn("爱发电回调数据缺少 order 字段");
                return null;
            }

            String outTradeNo = order.get("out_trade_no").asText();
            String afdianUserId = order.get("user_id").asText();
            String planId = order.get("plan_id").asText();
            String totalAmount = order.get("total_amount").asText();
            String customOrderId = order.has("custom_order_id") ? order.get("custom_order_id").asText() : "";
            int status = order.get("status").asInt();
            String remark = order.has("remark") ? order.get("remark").asText() : "";

            // RSA-SHA256 验签
            if (signature != null && !signature.isEmpty()) {
                String signStr = outTradeNo + afdianUserId + planId + totalAmount;
                if (!verifyRsaSignature(signStr, signature)) {
                    log.warn("爱发电 Webhook RSA 验签失败, signStr={}", signStr);
                    return null;
                }
                log.info("爱发电 Webhook RSA 验签成功");
            } else {
                log.warn("爱发电 Webhook 未包含签名字段，跳过验签");
            }

            // 构建结果
            CallbackResult result = new CallbackResult();
            result.setVerified(true);
            result.setPlatformOrderId(outTradeNo);
            result.setPlatformUserId(afdianUserId);
            result.setCustomOrderId(customOrderId);
            result.setAmount(new BigDecimal(totalAmount));
            result.setStatus(status);
            result.setPlanId(planId);
            result.setRemark(remark);

            // 解析 customOrderId
            result.parseCustomOrderId();

            return result;

        } catch (Exception e) {
            log.error("爱发电 Webhook 处理异常", e);
            return null;
        }
    }

    @Override
    public boolean testConnection(Map<String, Object> config) {
        String userId = (String) config.get("userId");
        String apiToken = (String) config.get("apiToken");

        if (userId == null || apiToken == null) {
            return false;
        }

        try {
            String params = "{}";
            long ts = System.currentTimeMillis() / 1000;
            String sign = generateApiSign(apiToken, params, ts, userId);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "user_id", userId,
                    "params", params,
                    "ts", ts,
                    "sign", sign));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/api/open/ping"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode responseJson = objectMapper.readTree(response.body());

            int ec = responseJson.get("ec").asInt();
            if (ec == 200) {
                log.info("爱发电连接测试成功");
                return true;
            } else {
                log.warn("爱发电连接测试失败, ec={}, em={}", ec, responseJson.get("em").asText());
                return false;
            }
        } catch (Exception e) {
            log.error("爱发电连接测试异常", e);
            return false;
        }
    }

    @Override
    public String getSuccessResponse() {
        return "{\"ec\":200,\"em\":\"\"}";
    }

    // ========== 内部方法 ==========

    /**
     * 生成 API 请求签名
     * sign = md5(token + "params" + paramsJson + "ts" + ts + "user_id" + userId)
     */
    private String generateApiSign(String token, String params, long ts, String userId) {
        String raw = token + "params" + params + "ts" + ts + "user_id" + userId;
        return md5(raw);
    }

    /**
     * RSA-SHA256 公钥验签
     */
    private boolean verifyRsaSignature(String data, String signatureBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(AFDIAN_PUBLIC_KEY);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));

            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.error("RSA 验签异常", e);
            return false;
        }
    }

    /**
     * MD5 哈希
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
