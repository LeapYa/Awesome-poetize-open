import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.TreeMap

/**
 * 支付宝当面付 Groovy 插件
 *
 * 接口：alipay.trade.precreate（预创建交易，生成二维码）
 * 签名：RSA2（SHA256withRSA）
 * 文档：https://opendocs.alipay.com/open/02ekfg
 *
 * 实现约定的四个方法：
 *   getPaymentUrl(ctx)      → 返回支付宝付款二维码 URL（qr_code 字段）
 *   verifyCallback(ctx)     → 验证异步通知签名并解析订单
 *   testConnection(ctx)     → 检查配置是否完整
 *   getSuccessResponse(ctx) → 返回 "success"（支付宝要求）
 */

static final String ALIPAY_SDK_VERSION = "alipay-sdk-java-4.38.10.ALL"
static final JsonMapper JSON = new JsonMapper()

// ──────────────────────────────────────────────
// 1. 生成支付链接（实际是支付宝二维码 URL）
// ──────────────────────────────────────────────
def getPaymentUrl(ctx) {
    def config    = ctx.config
    def articleId = ctx.articleId as Integer
    def userId    = ctx.userId    as Integer

    def gateway        = requireConfig(config, "gateway",             "支付宝网关")
    def appId          = requireConfig(config, "appId",               "APPID")
    def privateKeyStr  = requireConfig(config, "merchantPrivateKey",  "商户应用私钥")
    def notifyUrl      = config.getOrDefault("notifyUrl", "") as String

    def amountKey = (articleId == 0) ? "memberFixedAmount" : "fixedAmount"
    def amount    = new BigDecimal(config.getOrDefault(amountKey, 5).toString()).setScale(2)
    def subject   = (articleId == 0) ? "全站会员订阅" : "文章解锁 #${articleId}"
    def outTradeNo = "u${userId}_a${articleId}_${System.currentTimeMillis()}"

    // 构造 biz_content
    def bizContent = [
        out_trade_no: outTradeNo,
        total_amount: amount.toPlainString(),
        subject:      subject,
        timeout_express: "30m"
    ]

    // 公共请求参数
    def params = buildBaseParams(appId, "alipay.trade.precreate", notifyUrl)
    params.put("biz_content", JSON.writeValueAsString(bizContent))

    // RSA2 签名
    params.put("sign", rsaSign(buildSignStr(params), privateKeyStr))

    // POST 到支付宝网关
    def responseStr = httpPost(gateway, params)
    log.debug("支付宝 precreate 响应: {}", responseStr)

    def resp = JSON.readValue(responseStr, Map.class)
    def preResp = resp.get("alipay_trade_precreate_response") as Map
    if (preResp == null) {
        throw new IllegalStateException("支付宝返回格式异常: " + responseStr)
    }
    def code = preResp.get("code") as String
    if (code != "10000") {
        throw new IllegalStateException("支付宝预创建失败: code=${code}, msg=${preResp.get('msg')}, sub_msg=${preResp.get('sub_msg')}")
    }

    def qrCode = preResp.get("qr_code") as String
    log.info("支付宝当面付二维码生成成功: outTradeNo={}, qrCode={}", outTradeNo, qrCode)
    return qrCode
}

// ──────────────────────────────────────────────
// 2. 验签回调并解析订单
// ──────────────────────────────────────────────
def verifyCallback(ctx) {
    def params    = ctx.params as Map<String, String>
    def config    = ctx.config
    def pubKeyStr = config.get("alipayPublicKey") as String

    if (!pubKeyStr) {
        log.warn("支付宝公钥未配置，无法验签")
        return [verified: false]
    }

    def sign     = params.get("sign")
    def signType = params.get("sign_type") ?: "RSA2"

    if (!sign) {
        log.warn("支付宝回调缺少 sign 参数")
        return [verified: false]
    }

    // 构建待验签字符串（去除 sign、sign_type，按 ASCII 排序）
    def signParams = new TreeMap<String, String>()
    params.each { k, v ->
        if (k != "sign" && k != "sign_type" && v) {
            signParams.put(k, v)
        }
    }
    def signStr = signParams.collect { k, v -> "${k}=${v}" }.join("&")

    if (!rsaVerify(signStr, sign, pubKeyStr)) {
        log.warn("支付宝回调验签失败")
        return [verified: false]
    }

    // 支付宝回调只在交易成功时才处理
    def tradeStatus = params.get("trade_status")
    if (tradeStatus != "TRADE_SUCCESS" && tradeStatus != "TRADE_FINISHED") {
        log.info("支付宝回调交易状态非成功: trade_status={}", tradeStatus)
        return [verified: false]
    }

    def outTradeNo = params.get("out_trade_no")   // u42_a15_1708000000000
    def tradeNo    = params.get("trade_no")
    def totalAmount = params.get("total_amount") ?: "0"

    // 解析 customOrderId：u42_a15_1708000000000 → userId=42, articleId=15
    def parsed = parseOrderId(outTradeNo)

    log.info("支付宝回调验签成功: outTradeNo={}, tradeNo={}", outTradeNo, tradeNo)

    return [
        verified:        true,
        platformOrderId: tradeNo,
        customOrderId:   outTradeNo,
        amount:          new BigDecimal(totalAmount),
        remark:          "trade_status=" + tradeStatus,
        parsedUserId:    parsed[0],
        parsedArticleId: parsed[1]
    ]
}

// ──────────────────────────────────────────────
// 3. 测试连接（检查配置项完整性）
// ──────────────────────────────────────────────
def testConnection(ctx) {
    def config = ctx.config
    def required = ["gateway", "appId", "merchantPrivateKey", "alipayPublicKey"]
    def missing = required.findAll { k -> !config.get(k) }
    if (missing) {
        log.warn("支付宝当面付配置不完整，缺少: {}", missing)
        return false
    }
    log.info("支付宝当面付配置检查通过: appId={}", config.get("appId"))
    return true
}

// ──────────────────────────────────────────────
// 4. 回调成功响应（支付宝要求返回 "success"）
// ──────────────────────────────────────────────
def getSuccessResponse(ctx) {
    return "success"
}

// ══════════════════════════════════════════════
// 内部工具函数
// ══════════════════════════════════════════════

/** 构造公共请求参数 Map */
def buildBaseParams(appId, method, notifyUrl) {
    def params = new TreeMap<String, String>()
    params.put("app_id",       appId)
    params.put("method",       method)
    params.put("format",       "JSON")
    params.put("charset",      "utf-8")
    params.put("sign_type",    "RSA2")
    params.put("timestamp",    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
    params.put("version",      "1.0")
    if (notifyUrl) params.put("notify_url", notifyUrl)
    return params
}

/** 构建待签名字符串（按参数名 ASCII 升序，k=v&k=v，排除 sign） */
def buildSignStr(Map<String, String> params) {
    return params.findAll { it.key != "sign" }
                 .collect { k, v -> "${k}=${v}" }
                 .join("&")
}

/** RSA2（SHA256withRSA）私钥签名，返回 Base64 */
def rsaSign(data, privateKeyBase64) {
    def keyBytes   = Base64.getDecoder().decode(privateKeyBase64.replaceAll("\\s", ""))
    def privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
    def sig        = Signature.getInstance("SHA256withRSA")
    sig.initSign(privateKey)
    sig.update(data.getBytes(StandardCharsets.UTF_8))
    return Base64.getEncoder().encodeToString(sig.sign())
}

/** RSA2 公钥验签 */
def rsaVerify(data, signBase64, publicKeyBase64) {
    def keyBytes  = Base64.getDecoder().decode(publicKeyBase64.replaceAll("\\s", ""))
    def publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes))
    def sig       = Signature.getInstance("SHA256withRSA")
    sig.initVerify(publicKey)
    sig.update(data.getBytes(StandardCharsets.UTF_8))
    return sig.verify(Base64.getDecoder().decode(signBase64))
}

/** HTTP POST（application/x-www-form-urlencoded）*/
def httpPost(url, Map<String, String> params) {
    def body = params.collect { k, v ->
        URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
    }.join("&")

    def client   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    def request  = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
    def response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    return response.body()
}

/** 解析订单号 u{userId}_a{articleId}_{timestamp}，返回 [userId, articleId] */
def parseOrderId(orderId) {
    if (!orderId) return [null, null]
    try {
        def parts = orderId.split("_")
        if (parts.length >= 2 && parts[0].startsWith("u") && parts[1].startsWith("a")) {
            return [parts[0].substring(1).toInteger(), parts[1].substring(1).toInteger()]
        }
    } catch (ignore) {}
    return [null, null]
}

/** 读取必填配置项 */
def requireConfig(config, key, label) {
    def val = config.get(key)?.toString()
    if (!val || val.isBlank()) {
        throw new IllegalStateException("支付宝当面付插件配置缺少必填字段：${label}（${key}），请在后台插件管理中填写。")
    }
    return val
}
