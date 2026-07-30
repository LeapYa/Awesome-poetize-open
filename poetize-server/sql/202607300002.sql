-- 新增第三方登录平台：LinuxDo、Microsoft、GitLab、语雀、华为、小米、Apple
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
('linuxdo', 'LinuxDo', '', '', '', '', 0, 0, 10, 'LinuxDo Connect OAuth登录配置，在 connect.linux.do 自助创建应用获取 Client ID 和 Client Secret'),
('microsoft', 'Microsoft', '', '', '', 'openid profile email User.Read', 0, 0, 11, 'Microsoft OAuth登录配置，需要在 Microsoft Entra 管理中心注册应用（个人账户需先创建免费租户）'),
('gitlab', 'GitLab', '', '', '', 'read_user', 0, 0, 12, 'GitLab OAuth登录配置，需要在 gitlab.com 用户设置的 Applications 中创建应用'),
('yuque', '语雀', '', '', '', '', 0, 0, 13, '语雀 OAuth登录配置，需要在语雀设置的三方应用中创建应用获取 Client ID 和 Client Secret'),
('huawei', '华为', '', '', '', 'openid profile', 0, 0, 14, '华为账号 OAuth登录配置，需要在华为开发者联盟创建应用并开通账号服务（Account Kit）'),
('xiaomi', '小米', '', '', '', 'openid', 0, 0, 15, '小米账号 OAuth登录配置，需要在小米开放平台完成实名认证并申请账号服务接入'),
('apple', 'Apple', '', '', '', '', 0, 0, 16, 'Apple 登录配置，需 Apple Developer Program 会员；Client Secret 为用 .p8 私钥签发的 JWT，最长6个月有效期需定期更换')
ON DUPLICATE KEY UPDATE 
  `platform_name` = VALUES(`platform_name`),
  `scope` = VALUES(`scope`),
  `sort_order` = VALUES(`sort_order`),
  `remark` = VALUES(`remark`);
