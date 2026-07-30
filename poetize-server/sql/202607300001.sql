-- 微博 OAuth 登录配置
INSERT INTO `poetize`.`third_party_oauth_config` 
(
  `platform_type`, 
  `platform_name`, 
  `client_id`, 
  `client_secret`, 
  `redirect_uri`, 
  `scope`, 
  `enabled`, 
  `global_enabled`, 
  `sort_order`, 
  `remark`
) 
VALUES 
(
  'weibo',
  '微博',
  '',
  '',
  '',
  '',
  0,
  0,
  9,
  '微博 OAuth 登录配置，需要在微博开放平台（open.weibo.com）创建网站接入应用获取 App Key 和 App Secret'
)
ON DUPLICATE KEY UPDATE 
  `platform_name` = '微博',
  `sort_order` = 9,
  `remark` = '微博 OAuth 登录配置，需要在微博开放平台（open.weibo.com）创建网站接入应用获取 App Key 和 App Secret';
