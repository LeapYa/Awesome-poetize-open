-- ============================================================
-- 功能说明：新增支付宝当面付内置支付插件
-- 变更内容：
--   1. sys_plugin 表初始化 payment/alipay-f2f 插件行（幂等，INSERT IGNORE）
--      原 Groovy 动态插件 alipay-f2f-plugin 随 Groovy 引擎移除后失效，
--      现由内置 Java Provider（AlipayF2FProvider）承接，plugin_key 为 alipay-f2f
-- 日期：2026-08-05
-- ============================================================

INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`, `manifest`) VALUES
('payment', 'alipay-f2f', '支付宝当面付', '通过支付宝开放平台当面付接口生成付款二维码，用户扫码完成支付，RSA2签名',
'{"gateway":"https://openapi.alipay.com/gateway.do","appId":"","merchantPrivateKey":"","alipayPublicKey":"","notifyUrl":"","defaultPayType":1,"fixedAmount":5.00,"memberFixedAmount":30.00,"freePercent":30,"memberDurationDays":30}',
NULL, 0, 1, 1,
'{"name":"alipay-f2f","displayName":"支付宝当面付","version":"1.0.0","author":"LeapYa","pluginType":"payment","description":"通过支付宝开放平台 alipay.trade.precreate 接口生成付款二维码（当面付），用户扫码完成支付。使用 RSA2（SHA256withRSA）签名。","configSchema":{"gateway":{"type":"string","label":"支付宝网关","description":"正式环境填 https://openapi.alipay.com/gateway.do，沙箱填 https://openapi-sandbox.dl.alipaydev.com/gateway.do","defaultValue":"https://openapi.alipay.com/gateway.do"},"appId":{"type":"string","label":"APPID","description":"支付宝开放平台应用ID（在开放平台控制台获取）","defaultValue":""},"merchantPrivateKey":{"type":"string","label":"商户应用私钥（RSA2）","description":"PKCS#8 格式 Base64，不含头尾行，在开放平台密钥工具生成","defaultValue":""},"alipayPublicKey":{"type":"string","label":"支付宝公钥","description":"用于验证支付宝回调签名（在开放平台绑定密钥后获取）","defaultValue":""},"notifyUrl":{"type":"string","label":"异步通知URL","description":"例如 https://yourdomain.com/payment/webhook/alipay-f2f","defaultValue":""},"defaultPayType":{"type":"number","label":"默认付费类型","description":"1=按文章付费, 2=会员专属, 3=赞赏解锁, 4=固定金额","defaultValue":1},"fixedAmount":{"type":"number","label":"文章付费金额（元）","description":"单篇文章解锁金额","defaultValue":5.00},"memberFixedAmount":{"type":"number","label":"全站会员金额（元）","description":"全站会员订阅金额","defaultValue":30.00},"freePercent":{"type":"number","label":"免费预览比例(%)","description":"付费文章可免费预览的内容比例","defaultValue":30},"memberDurationDays":{"type":"number","label":"会员有效期（天）","description":"购买会员后的有效天数","defaultValue":30}}}');
