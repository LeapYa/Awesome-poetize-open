package com.ld.poetry.service.payment;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 支付宝当面付 Provider
 * <p>
 * 通过支付宝开放平台 alipay.trade.precreate 接口预创建交易并生成付款二维码，
 * 用户扫码完成支付，支付宝通过异步通知（notify_url）回调确认支付结果。
 * 签名算法：RSA2（SHA256withRSA）。
 * 文档：https://opendocs.alipay.com/open/02ekfg
 * </p>
 * <p>
 * 历史沿革：原以 Groovy 动态插件（alipay-f2f-plugin）形式提供，
 * Groovy 后端脚本引擎移除后移植为内置 Java Provider。
 * </p>
 *
 * @author LeapYa
 * @since 2026-08-05
 */
@Slf4j
@Component
public class AlipayF2FProvider implements PaymentProvider {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getPlatformKey() {
        return "alipay-f2f";
    }

    @Override
    public String getPaymentUrl(Integer articleId, Integer userId, Map<String, Object> config) {
        String gateway = getRequiredConfig(config, "gateway", "支付宝网关");
        String appId = getRequiredConfig(config, "appId", "APPID");
        String privateKeyStr = getRequiredConfig(config, "merchantPrivateKey", "商户应用私钥");
        String notifyUrl = String.valueOf(config.getOrDefault("notifyUrl", ""));

        // 会员订阅（articleId=0）与单篇文章使用不同的金额配置项
        boolean isMember = articleId != null && articleId == 0;
        BigDecimal amount = readAmount(config, isMember ? "memberFixedAmount" : "fixedAmount",
                isMember ? BigDecimal.valueOf(30) : BigDecimal.valueOf(5));
        String subject = isMember ? "全站会员订阅" : "文章解锁 #" + articleId;
        String outTradeNo = "u" + userId + "_a" + articleId + "_" + System.currentTimeMillis();

        // 构造 biz_content
        Map<String, String> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("total_amount", amount.setScale(2).toPlainString());
        bizContent.put("subject", subject);
        bizContent.put("timeout_express", "30m");

        // 公共请求参数（TreeMap 保证 ASCII 升序，签名依赖此顺序）
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("method", "alipay.trade.precreate");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        params.put("version", "1.0");
        if (!notifyUrl.isBlank()) {
            params.put("notify_url", notifyUrl);
        }
        params.put("biz_content", JSON.writeValueAsString(bizContent));

        // RSA2 签名
        String sign;
        try {
            sign = rsaSign(buildSignString(params), privateKeyStr);
        } catch (Exception e) {
            log.error("支付宝当面付签名失败", e);
            throw new IllegalStateException("支付签名失败: " + e.getMessage());
        }
        params.put("sign", sign);

        // POST 到支付宝网关并解析二维码
        String responseStr = httpPostForm(gateway, params);
        log.debug("支付宝 precreate 响应: {}", responseStr);

        Map<?, ?> resp = JSON.readValue(responseStr, Map.class);
        Object preRespObj = resp.get("alipay_trade_precreate_response");
        if (!(preRespObj instanceof Map<?, ?> preResp)) {
            throw new IllegalStateException("支付宝返回格式异常: " + responseStr);
        }
        String code = String.valueOf(preResp.get("code"));
        if (!"10000".equals(code)) {
            throw new IllegalStateException("支付宝预创建失败: code=" + code
                    + ", msg=" + preResp.get("msg") + ", sub_msg=" + preResp.get("sub_msg"));
        }

        String qrCode = String.valueOf(preResp.get("qr_code"));
        log.info("支付宝当面付二维码生成成功: outTradeNo={}, qrCode={}", outTradeNo, qrCode);
        return qrCode;
    }

    @Override
    public CallbackResult verifyCallback(HttpServletRequest request, Map<String, Object> config) {
        try {
            String pubKeyStr = (String) config.get("alipayPublicKey");
            if (pubKeyStr == null || pubKeyStr.isBlank()) {
                log.warn("支付宝公钥未配置，无法验签");
                return null;
            }

            String sign = request.getParameter("sign");
            if (sign == null || sign.isBlank()) {
                log.warn("支付宝回调缺少 sign 参数");
                return null;
            }

            // 构建待验签字符串（去除 sign、sign_type，空值不参与，按 ASCII 升序）
            Map<String, String> signParams = new TreeMap<>();
            Enumeration<String> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                String value = request.getParameter(name);
                if (!"sign".equals(name) && !"sign_type".equals(name)
                        && value != null && !value.isEmpty()) {
                    signParams.put(name, value);
                }
            }

            if (!rsaVerify(buildSignString(signParams), sign, pubKeyStr)) {
                log.warn("支付宝回调验签失败");
                return null;
            }

            // 支付宝回调只在交易成功时才处理
            String tradeStatus = request.getParameter("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info("支付宝回调交易状态非成功: trade_status={}", tradeStatus);
                return null;
            }

            String outTradeNo = request.getParameter("out_trade_no");
            String tradeNo = request.getParameter("trade_no");
            String totalAmount = request.getParameter("total_amount");

            log.info("支付宝回调验签成功: outTradeNo={}, tradeNo={}", outTradeNo, tradeNo);

            CallbackResult result = new CallbackResult();
            result.setVerified(true);
            result.setPlatformOrderId(tradeNo);
            result.setCustomOrderId(outTradeNo);
            result.setAmount(new BigDecimal(totalAmount != null ? totalAmount : "0"));
            result.setStatus(1); // 已支付
            result.setRemark("trade_status=" + tradeStatus);
            parseOrderId(result, outTradeNo);
            return result;

        } catch (Exception e) {
            log.error("支付宝当面付回调处理异常", e);
            return null;
        }
    }

    @Override
    public boolean testConnection(Map<String, Object> config) {
        List<String> required = List.of("gateway", "appId", "merchantPrivateKey", "alipayPublicKey");
        List<String> missing = required.stream()
                .filter(key -> {
                    Object value = config.get(key);
                    return value == null || String.valueOf(value).isBlank();
                })
                .toList();
        if (!missing.isEmpty()) {
            log.warn("支付宝当面付配置不完整，缺少: {}", missing);
            return false;
        }
        log.info("支付宝当面付配置检查通过: appId={}", config.get("appId"));
        return true;
    }

    @Override
    public String getSuccessResponse() {
        // 支付宝要求异步通知处理成功时返回 "success"
        return "success";
    }

    // ========== 内部方法 ==========

    /**
     * 读取金额配置，缺失或非法时使用默认值
     */
    private BigDecimal readAmount(Map<String, Object> config, String key, BigDecimal defaultValue) {
        Object val = config.get(key);
        if (val instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (val instanceof String str && !str.isBlank()) {
            try {
                return new BigDecimal(str.trim());
            } catch (NumberFormatException e) {
                log.warn("支付宝当面付金额配置非法: {}={}, 使用默认值 {}", key, str, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * 构造签名字符串：参数按 key ASCII 升序排序，用 & 连接 key=value（排除 sign）
     */
    private String buildSignString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.entrySet().stream()
                .filter(e -> !"sign".equals(e.getKey()))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .forEach(e -> {
                    if (sb.length() > 0)
                        sb.append("&");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                });
        return sb.toString();
    }

    /**
     * RSA2（SHA256withRSA）私钥签名，返回 Base64
     */
    private String rsaSign(String data, String privateKeyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(stripPemKey(privateKeyStr));
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * RSA2 公钥验签
     */
    private boolean rsaVerify(String data, String signBase64, String publicKeyStr) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(stripPemKey(publicKeyStr));
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            log.error("支付宝 RSA 验签异常", e);
            return false;
        }
    }

    /**
     * 去除 PEM 头尾行与空白字符，兼容直接粘贴 PEM 全文的配置
     */
    private String stripPemKey(String key) {
        return key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    /**
     * HTTP POST（application/x-www-form-urlencoded）
     */
    private String httpPostForm(String url, Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        params.forEach((k, v) -> {
            if (body.length() > 0)
                body.append("&");
            body.append(urlEncode(k)).append("=").append(urlEncode(v));
        });
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("支付宝网关请求被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("支付宝网关请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析订单号（格式: u{userId}_a{articleId}_{timestamp}）
     */
    private void parseOrderId(CallbackResult result, String outTradeNo) {
        if (outTradeNo == null || outTradeNo.isEmpty())
            return;
        try {
            String[] parts = outTradeNo.split("_");
            if (parts.length >= 2 && parts[0].startsWith("u") && parts[1].startsWith("a")) {
                result.setParsedUserId(Integer.parseInt(parts[0].substring(1)));
                result.setParsedArticleId(Integer.parseInt(parts[1].substring(1)));
            }
        } catch (NumberFormatException e) {
            log.warn("支付宝当面付订单号解析失败: {}", outTradeNo);
        }
    }

    private String getRequiredConfig(Map<String, Object> config, String key, String name) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException("支付宝当面付" + name + "(" + key + ")未配置，请在后台插件管理中填写");
        }
        return String.valueOf(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
