-- ============================================================
-- 功能说明：第三方登录平台批量扩展
-- 变更内容：
--   1. third_party_oauth_config 表新增授权/令牌/用户信息端点与用户字段映射列（自定义平台使用）
--   2. 初始化新增平台配置行：微博、LinuxDo、Microsoft、GitLab、语雀、
--      华为、小米、Apple、Steam（OpenID 2.0）、自定义（OAuth2/OIDC）
-- 日期：2026-07-30
-- ============================================================

-- ---------- 1. 自定义平台所需的端点与字段映射列（幂等） ----------
SET @dbname = DATABASE();
SET @tablename = 'third_party_oauth_config';

SET @columnname = 'authorize_url';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `authorize_url` VARCHAR(512) NULL DEFAULT NULL COMMENT ''授权端点（自定义平台使用）'' AFTER `scope`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'token_url';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `token_url` VARCHAR(512) NULL DEFAULT NULL COMMENT ''令牌端点（自定义平台使用）'' AFTER `authorize_url`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'user_info_url';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `user_info_url` VARCHAR(512) NULL DEFAULT NULL COMMENT ''用户信息端点（自定义平台使用）'' AFTER `token_url`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'uid_field';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `uid_field` VARCHAR(64) NULL DEFAULT NULL COMMENT ''用户唯一标识字段路径，默认sub（自定义平台使用）'' AFTER `user_info_url`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'username_field';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `username_field` VARCHAR(64) NULL DEFAULT NULL COMMENT ''用户名字段路径，默认name（自定义平台使用）'' AFTER `uid_field`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'avatar_field';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `avatar_field` VARCHAR(64) NULL DEFAULT NULL COMMENT ''头像字段路径，默认picture（自定义平台使用）'' AFTER `username_field`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'email_field';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `third_party_oauth_config` ADD COLUMN `email_field` VARCHAR(64) NULL DEFAULT NULL COMMENT ''邮箱字段路径，默认email（自定义平台使用）'' AFTER `avatar_field`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ---------- 2. 初始化新增平台配置行（幂等） ----------
INSERT INTO `poetize`.`third_party_oauth_config`
(`platform_type`, `platform_name`, `client_id`, `client_secret`, `redirect_uri`, `scope`, `enabled`, `global_enabled`, `sort_order`, `remark`)
VALUES
('weibo', '微博', '', '', '', '', 0, 0, 9, '微博 OAuth 登录配置，需要在微博开放平台（open.weibo.com）创建网站接入应用获取 App Key 和 App Secret'),
('linuxdo', 'LinuxDo', '', '', '', '', 0, 0, 10, 'LinuxDo Connect OAuth登录配置，在 connect.linux.do 自助创建应用获取 Client ID 和 Client Secret'),
('microsoft', 'Microsoft', '', '', '', 'openid profile email User.Read', 0, 0, 11, 'Microsoft OAuth登录配置，需要在 Microsoft Entra 管理中心注册应用（个人账户需先创建免费租户）'),
('gitlab', 'GitLab', '', '', '', 'read_user', 0, 0, 12, 'GitLab OAuth登录配置，需要在 gitlab.com 用户设置的 Applications 中创建应用'),
('yuque', '语雀', '', '', '', '', 0, 0, 13, '语雀 OAuth登录配置，需要在语雀设置的三方应用中创建应用获取 Client ID 和 Client Secret'),
('huawei', '华为', '', '', '', 'openid profile', 0, 0, 14, '华为账号 OAuth登录配置，需要在华为开发者联盟创建应用并开通账号服务（Account Kit）'),
('xiaomi', '小米', '', '', '', 'openid', 0, 0, 15, '小米账号 OAuth登录配置，需要在小米开放平台完成实名认证并申请账号服务接入'),
('apple', 'Apple', '', '', '', '', 0, 0, 16, 'Apple 登录配置，需 Apple Developer Program 会员；Client Secret 为用 .p8 私钥签发的 JWT，最长6个月有效期需定期更换'),
('steam', 'Steam', '', '', '', '', 0, 0, 17, 'Steam 登录配置（OpenID 2.0，无需 Client ID/Secret）；如需显示 Steam 昵称与头像，可在 steamcommunity.com/dev/apikey 免费申请 Web API Key 填入 Client Secret 字段')
ON DUPLICATE KEY UPDATE
  `platform_name` = VALUES(`platform_name`),
  `scope` = VALUES(`scope`),
  `sort_order` = VALUES(`sort_order`),
  `remark` = VALUES(`remark`);
