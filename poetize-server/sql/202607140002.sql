-- ============================================================
-- 功能说明：可逆资源管理与稳定 URL 基础数据模型
-- 变更内容：
--   1. 资源增加稳定 public_id、活动物理副本及内容哈希来源
--   2. 新增资源物理副本与历史地址别名表
--   3. 迁移条目增加源/目标副本及完整哈希快照
--   4. 新增历史资源接管任务与条目表
-- 日期：2026-07-14
--
-- 兼容策略：本脚本只登记现有物理位置和别名，不改写 resource.path
-- 或文章内容。稳定 /media/{publicId} 由管理员接管任务逐项启用。
-- ============================================================

SET @dbname = DATABASE();

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`)
SELECT '兰空图床-可信下载域名（多个用逗号分隔）', 'lsky.download_hosts', '', '1'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config`
  WHERE `config_key` = 'lsky.download_hosts' AND `config_type` = '1'
);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`)
SELECT '简单图床-可信下载域名（多个用逗号分隔）', 'easyimage.download_hosts', '', '1'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config`
  WHERE `config_key` = 'easyimage.download_hosts' AND `config_type` = '1'
);

INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`)
SELECT '资源迁移是否允许访问私网图床', 'resource.migration.remote.allow-private-hosts', 'false', '1'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config`
  WHERE `config_key` = 'resource.migration.remote.allow-private-hosts' AND `config_type` = '1'
);

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'public_id'
  ),
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `public_id` VARCHAR(32) NULL DEFAULT NULL COMMENT ''稳定资源公开ID'' AFTER `id`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'active_location_id'
  ),
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `active_location_id` BIGINT NULL DEFAULT NULL COMMENT ''当前活动物理副本ID'' AFTER `storage_key`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'hash_source'
  ),
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `hash_source` VARCHAR(32) NULL DEFAULT NULL COMMENT ''内容哈希来源'' AFTER `resource_hash`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'hash_verified_at'
  ),
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `hash_verified_at` DATETIME NULL DEFAULT NULL COMMENT ''内容哈希最近严格校验时间'' AFTER `hash_source`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'location_version'
  ),
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `location_version` INT NOT NULL DEFAULT 0 COMMENT ''活动副本乐观锁版本'' AFTER `active_location_id`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `resource`
SET `public_id` = LOWER(REPLACE(UUID(), '-', ''))
WHERE `public_id` IS NULL OR `public_id` = '';

UPDATE `resource`
SET `hash_source` = 'LEGACY_EXISTING'
WHERE `resource_hash` IS NOT NULL
  AND `resource_hash` <> ''
  AND (`hash_source` IS NULL OR `hash_source` = '');

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND INDEX_NAME = 'uk_resource_public_id'
  ),
  'SELECT 1',
  'CREATE UNIQUE INDEX `uk_resource_public_id` ON `resource` (`public_id`)'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND INDEX_NAME = 'idx_resource_active_location'
  ),
  'SELECT 1',
  'CREATE INDEX `idx_resource_active_location` ON `resource` (`active_location_id`)'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `resource_location` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理副本ID',
  `resource_id` INT NOT NULL COMMENT '逻辑资源ID',
  `store_type` VARCHAR(16) NOT NULL COMMENT '存储平台',
  `storage_key` VARCHAR(512) DEFAULT NULL COMMENT '存储平台对象键',
  `access_path` VARCHAR(2048) NOT NULL COMMENT '物理副本访问地址',
  `access_path_hash` CHAR(64) GENERATED ALWAYS AS (SHA2(`access_path`, 256)) STORED COMMENT '物理地址SHA-256摘要',
  `content_hash` CHAR(64) DEFAULT NULL COMMENT '副本内容SHA-256',
  `size` BIGINT DEFAULT NULL COMMENT '副本字节数',
  `mime_type` VARCHAR(256) DEFAULT NULL COMMENT '副本MIME类型',
  `status` VARCHAR(32) NOT NULL DEFAULT 'STAGED' COMMENT 'STAGED/ACTIVE/RETAINED/STALE/DELETING/DELETED/MISSING/DETACHED',
  `verified_at` DATETIME DEFAULT NULL COMMENT '最近完整回读校验时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_location_store_path` (`store_type`, `access_path_hash`),
  KEY `idx_resource_location_resource_status` (`resource_id`, `status`),
  KEY `idx_resource_location_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源物理副本';

CREATE TABLE IF NOT EXISTS `resource_alias` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '别名ID',
  `resource_id` INT NOT NULL COMMENT '逻辑资源ID',
  `alias_url` VARCHAR(2048) NOT NULL COMMENT '历史地址或旧物理URL',
  `alias_hash` CHAR(64) GENERATED ALWAYS AS (SHA2(`alias_url`, 256)) STORED COMMENT '别名SHA-256摘要',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'CURRENT_PATH' COMMENT 'CURRENT_PATH/REDIRECT_IMPORT/DISCOVERED_REFERENCE',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_alias_hash` (`alias_hash`),
  KEY `idx_resource_alias_resource` (`resource_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源历史地址别名';

-- 只为尚未建立活动副本的旧资源登记当前物理位置；不会修改现有URL。
INSERT IGNORE INTO `resource_location` (
  `resource_id`, `store_type`, `storage_key`, `access_path`, `content_hash`, `size`, `mime_type`, `status`
)
SELECT
  r.`id`, COALESCE(NULLIF(r.`store_type`, ''), 'local'), r.`storage_key`, r.`path`,
  LOWER(r.`resource_hash`), r.`size`, r.`mime_type`, 'ACTIVE'
FROM `resource` r
WHERE r.`active_location_id` IS NULL
  AND r.`path` IS NOT NULL
  AND r.`path` <> ''
  AND r.`path` NOT LIKE '/media/%';

UPDATE `resource` r
JOIN `resource_location` l
  ON l.`resource_id` = r.`id`
 AND l.`store_type` = COALESCE(NULLIF(r.`store_type`, ''), 'local')
 AND l.`access_path_hash` = SHA2(r.`path`, 256)
 AND l.`access_path` = r.`path`
SET r.`active_location_id` = l.`id`
WHERE r.`active_location_id` IS NULL;

INSERT IGNORE INTO `resource_alias` (`resource_id`, `alias_url`, `source_type`, `status`)
SELECT r.`id`, r.`path`, 'CURRENT_PATH', 1
FROM `resource` r
WHERE r.`path` IS NOT NULL
  AND r.`path` <> ''
  AND r.`path` NOT LIKE '/media/%';

INSERT IGNORE INTO `resource_alias` (`resource_id`, `alias_url`, `source_type`, `status`)
SELECT rr.`resource_id`, rr.`source_path`, 'REDIRECT_IMPORT', rr.`status`
FROM `resource_redirect` rr
WHERE rr.`source_path` IS NOT NULL
  AND rr.`source_path` <> '';

ALTER TABLE `resource_migration_item`
  MODIFY COLUMN `source_path` VARCHAR(2048) NOT NULL COMMENT '迁移前物理访问地址';

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_location_id'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_location_id` BIGINT NULL DEFAULT NULL COMMENT ''源物理副本ID'' AFTER `resource_id`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_location_version'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_location_version` INT NULL DEFAULT NULL COMMENT ''任务创建时活动副本版本'' AFTER `source_location_id`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'target_location_id'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `target_location_id` BIGINT NULL DEFAULT NULL COMMENT ''目标物理副本ID'' AFTER `source_location_version`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_expected_hash'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_expected_hash` CHAR(64) NULL DEFAULT NULL COMMENT ''任务创建时资源基准SHA-256'' AFTER `source_storage_key`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_hash_source'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_hash_source` VARCHAR(32) NULL DEFAULT NULL COMMENT ''任务创建时基准哈希来源'' AFTER `source_expected_hash`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_hash'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_hash` CHAR(64) NULL DEFAULT NULL COMMENT ''源快照SHA-256'' AFTER `source_hash_source`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_size'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_size` BIGINT NULL DEFAULT NULL COMMENT ''任务创建时源副本字节数'' AFTER `source_hash`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'source_mime_type'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `source_mime_type` VARCHAR(256) NULL DEFAULT NULL COMMENT ''任务创建时源副本MIME类型'' AFTER `source_size`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'target_hash'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `target_hash` CHAR(64) NULL DEFAULT NULL COMMENT ''目标完整回读SHA-256'' AFTER `target_storage_key`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'snapshot_size'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `snapshot_size` BIGINT NULL DEFAULT NULL COMMENT ''源快照字节数'' AFTER `target_hash`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'hash_baselined'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `hash_baselined` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否由本任务建立历史哈希基准'' AFTER `snapshot_size`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND COLUMN_NAME = 'target_created'
  ),
  'SELECT 1',
  'ALTER TABLE `resource_migration_item` ADD COLUMN `target_created` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''目标副本是否由本任务新建'' AFTER `hash_baselined`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 升级前创建的未完成任务没有冻结物理副本与哈希证据，禁止按旧的 URL/大小语义自动恢复。
UPDATE `resource_migration_item` i
JOIN `resource_migration_task` t ON t.`task_id` = i.`task_id`
SET i.`status` = 'FAILED',
    i.`error_message` = '迁移模型已升级且旧任务缺少冻结副本/哈希证据，请重新创建任务',
    i.`finished_at` = COALESCE(i.`finished_at`, NOW()),
    i.`retry_count` = COALESCE(i.`retry_count`, 0) + 1
WHERE t.`status` IN ('PENDING', 'RUNNING')
  AND i.`status` NOT IN ('SUCCESS', 'SKIPPED', 'SOURCE_CHANGED', 'FAILED')
  AND (i.`source_location_id` IS NULL OR i.`source_location_version` IS NULL);

UPDATE `resource_migration_task` t
SET t.`status` = CASE WHEN t.`success_count` > 0 THEN 'PARTIAL_SUCCESS' ELSE 'FAILED' END,
    t.`error_message` = '迁移模型已升级，旧未完成任务缺少严格校验证据，请重新创建任务',
    t.`finished_at` = COALESCE(t.`finished_at`, NOW())
WHERE t.`status` IN ('PENDING', 'RUNNING')
  AND EXISTS (
    SELECT 1
    FROM `resource_migration_item` i
    WHERE i.`task_id` = t.`task_id`
      AND i.`error_message` = '迁移模型已升级且旧任务缺少冻结副本/哈希证据，请重新创建任务'
  );

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource_migration_item' AND INDEX_NAME = 'idx_resource_migration_item_locations'
  ),
  'SELECT 1',
  'CREATE INDEX `idx_resource_migration_item_locations` ON `resource_migration_item` (`source_location_id`, `target_location_id`)'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `resource_adoption_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '接管任务ID',
  `created_by` INT NOT NULL COMMENT '创建人用户ID',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/PARTIAL_SUCCESS/SUCCESS/FAILED/CANCELLED',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '候选总数',
  `processed_count` INT NOT NULL DEFAULT 0 COMMENT '已处理数',
  `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功数',
  `skipped_count` INT NOT NULL DEFAULT 0 COMMENT '跳过数',
  `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败数',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '任务级错误',
  `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_adoption_task_id` (`task_id`),
  KEY `idx_resource_adoption_task_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史资源接管任务';

CREATE TABLE IF NOT EXISTS `resource_adoption_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '接管任务ID',
  `source_url` VARCHAR(2048) NOT NULL COMMENT '发现的历史资源URL',
  `source_url_hash` CHAR(64) GENERATED ALWAYS AS (SHA2(`source_url`, 256)) STORED COMMENT '历史URL摘要',
  `resource_id` INT DEFAULT NULL COMMENT '接管后的逻辑资源ID',
  `reference_count` INT NOT NULL DEFAULT 0 COMMENT '发现的引用数量',
  `source_hash` CHAR(64) DEFAULT NULL COMMENT '安全读取后的内容SHA-256',
  `snapshot_size` BIGINT DEFAULT NULL COMMENT '安全读取后的字节数',
  `hash_baselined` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否由本任务建立哈希基准',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/ADOPTED/SKIPPED/FAILED',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '条目错误',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_adoption_item_url` (`task_id`, `source_url_hash`),
  KEY `idx_resource_adoption_item_status` (`task_id`, `status`),
  KEY `idx_resource_adoption_item_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史资源接管条目';