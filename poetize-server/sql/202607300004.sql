-- Steam 登录配置（OpenID 2.0，无需 Client ID/Secret）
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
('steam', 'Steam', '', '', '', '', 0, 0, 17, 'Steam 登录配置（OpenID 2.0，无需 Client ID/Secret）；如需显示 Steam 昵称与头像，可在 steamcommunity.com/dev/apikey 免费申请 Web API Key 填入 Client Secret 字段')
ON DUPLICATE KEY UPDATE 
  `platform_name` = 'Steam',
  `sort_order` = 17,
  `remark` = 'Steam 登录配置（OpenID 2.0，无需 Client ID/Secret）；如需显示 Steam 昵称与头像，可在 steamcommunity.com/dev/apikey 免费申请 Web API Key 填入 Client Secret 字段';
