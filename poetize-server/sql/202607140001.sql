-- ============================================================
-- 功能说明：资源批量迁移基础数据模型
-- 变更内容：
--   1. 资源表增加远端对象键并扩展路径长度
--   2. 创建迁移任务、迁移条目与旧路径重定向表
-- 日期：2026-07-14
-- ============================================================

SET @dbname = DATABASE();
SET @tablename = 'resource';
SET @columnname = 'storage_key';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `storage_key` VARCHAR(512) NULL DEFAULT NULL COMMENT ''存储平台对象键，用于校验和删除远端文件'' AFTER `store_type`'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @indexname = 'uk_path';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'resource'
      AND INDEX_NAME = @indexname
  ) > 0,
  'ALTER TABLE `resource` DROP INDEX `uk_path`',
  'SELECT 1'
));
PREPARE dropLegacyPathIndex FROM @preparedStatement;
EXECUTE dropLegacyPathIndex;
DEALLOCATE PREPARE dropLegacyPathIndex;

ALTER TABLE `resource`
  MODIFY COLUMN `path` VARCHAR(1024) NOT NULL COMMENT '资源访问路径';

SET @columnname = 'path_hash';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'resource'
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `path_hash` CHAR(64) GENERATED ALWAYS AS (SHA2(`path`, 256)) STORED COMMENT ''资源路径SHA-256摘要'' AFTER `path`'
));
PREPARE addPathHashIfNotExists FROM @preparedStatement;
EXECUTE addPathHashIfNotExists;
DEALLOCATE PREPARE addPathHashIfNotExists;

SET @indexname = 'uk_resource_path_hash';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'resource'
      AND INDEX_NAME = @indexname
  ) > 0,
  'SELECT 1',
  'CREATE UNIQUE INDEX `uk_resource_path_hash` ON `resource` (`path_hash`)'
));
PREPARE addPathHashIndexIfNotExists FROM @preparedStatement;
EXECUTE addPathHashIndexIfNotExists;
DEALLOCATE PREPARE addPathHashIndexIfNotExists;

CREATE TABLE IF NOT EXISTS `resource_migration_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID',
  `created_by` INT NOT NULL COMMENT '创建人用户ID',
  `source_store_type` VARCHAR(16) NOT NULL DEFAULT 'local' COMMENT '源存储平台',
  `target_store_type` VARCHAR(16) NOT NULL COMMENT '目标存储平台',
  `scope_type` VARCHAR(16) NOT NULL COMMENT '范围类型 SELECTED/FILTER',
  `resource_type` VARCHAR(32) DEFAULT NULL COMMENT 'FILTER范围的资源类型',
  `keep_source` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '迁移成功后是否保留源文件',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/PARTIAL_SUCCESS/SUCCESS/FAILED/CANCELLED',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '条目总数',
  `processed_count` INT NOT NULL DEFAULT 0 COMMENT '已完成条目数',
  `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功数',
  `skipped_count` INT NOT NULL DEFAULT 0 COMMENT '跳过数',
  `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败数',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '任务级错误',
  `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_migration_task_id` (`task_id`),
  KEY `idx_resource_migration_task_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源迁移任务';

CREATE TABLE IF NOT EXISTS `resource_migration_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID',
  `resource_id` INT NOT NULL COMMENT '资源ID',
  `source_path` VARCHAR(1024) NOT NULL COMMENT '迁移前访问路径',
  `source_store_type` VARCHAR(16) NOT NULL DEFAULT 'local' COMMENT '源存储平台',
  `source_storage_key` VARCHAR(512) DEFAULT NULL COMMENT '源存储对象键',
  `target_path` VARCHAR(2048) DEFAULT NULL COMMENT '目标访问路径',
  `target_store_type` VARCHAR(16) NOT NULL COMMENT '目标存储平台',
  `target_storage_key` VARCHAR(512) DEFAULT NULL COMMENT '目标存储对象键',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/UPLOADED/SWITCHED/SUCCESS/SKIPPED/FAILED',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `source_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '源文件是否已清理',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '条目错误',
  `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_migration_item` (`task_id`, `resource_id`),
  KEY `idx_resource_migration_item_status` (`task_id`, `status`),
  KEY `idx_resource_migration_item_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源迁移条目';

CREATE TABLE IF NOT EXISTS `resource_redirect` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `resource_id` INT NOT NULL COMMENT '当前资源ID',
  `source_path` VARCHAR(1024) NOT NULL COMMENT '旧访问路径',
  `source_path_hash` CHAR(64) GENERATED ALWAYS AS (SHA2(`source_path`, 256)) STORED COMMENT '旧路径SHA-256摘要',
  `target_url` VARCHAR(2048) NOT NULL COMMENT '当前目标URL',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_redirect_source_hash` (`source_path_hash`),
  KEY `idx_resource_redirect_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移资源旧路径重定向';