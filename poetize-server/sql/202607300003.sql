-- ============================================================
-- 功能说明：第三方登录支持自定义 OAuth2/OIDC 平台接入
-- 变更内容：
--   1. third_party_oauth_config 表新增授权/令牌/用户信息端点与用户字段映射列
--   2. 初始化 custom（自定义）平台配置行
-- 日期：2026-07-30
-- ============================================================

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

-- 初始化自定义平台配置行
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
('custom', '自定义', '', '', '', 'openid profile email', 0, 0, 17, '自定义 OAuth2/OIDC 平台接入，可对接 Keycloak、Casdoor、Logto、Authelia 等任意标准授权服务')
ON DUPLICATE KEY UPDATE 
  `sort_order` = 17,
  `remark` = '自定义 OAuth2/OIDC 平台接入，可对接 Keycloak、Casdoor、Logto、Authelia 等任意标准授权服务';
