-- ============================================================
-- 功能说明：资源内容替换两阶段状态与恢复证据
-- 变更内容：
--   1. resource 增加独立 content_state，禁止复用 hash_source 表达流程状态
--   2. 新增内容替换事务，冻结资源版本、活动副本及新旧 SHA-256
--   3. 新增逐物理文件恢复证据，支持进程中断后的确定性提交或回滚
-- 日期：2026-07-14
--
-- 状态约束：
--   ACTIVE              可读取、迁移和切换活动副本
--   REPLACEMENT_PENDING 内容替换尚未决议，所有读取与迁移入口必须阻断
--   DELETION_PENDING    逻辑删除已声明，禁止重新启用、读取、迁移和激活；副本清理完成后资源行才物理删除
-- ============================================================

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  EXISTS(
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'content_state'
  ),
  'SELECT 1',
  'ALTER TABLE `resource` ADD COLUMN `content_state` VARCHAR(32) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''ACTIVE/REPLACEMENT_PENDING/DELETION_PENDING'' AFTER `location_version`'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `resource`
SET `content_state` = 'ACTIVE'
WHERE `content_state` IS NULL OR `content_state` = '';

CREATE TABLE IF NOT EXISTS `resource_content_replacement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '替换事务ID',
  `operation_id` VARCHAR(64) NOT NULL COMMENT '公开操作ID',
  `resource_id` INT NOT NULL COMMENT '逻辑资源ID',
  `active_location_id` BIGINT DEFAULT NULL COMMENT '声明时活动物理副本ID',
  `expected_path` VARCHAR(1024) NOT NULL COMMENT '声明时逻辑资源路径',
  `original_location_version` INT NOT NULL COMMENT '声明前资源版本',
  `claimed_location_version` INT NOT NULL COMMENT 'REPLACEMENT_PENDING版本',
  `original_resource_hash` VARCHAR(64) DEFAULT NULL COMMENT '声明前资源哈希原值',
  `source_hash` CHAR(64) NOT NULL COMMENT '替换前物理文件完整SHA-256',
  `original_hash_source` VARCHAR(32) DEFAULT NULL COMMENT '声明前哈希来源',
  `original_hash_verified_at` DATETIME DEFAULT NULL COMMENT '声明前严格校验时间',
  `source_location_store_type` VARCHAR(16) DEFAULT NULL COMMENT '声明时活动副本存储类型',
  `source_location_storage_key` VARCHAR(512) DEFAULT NULL COMMENT '声明时活动副本对象键',
  `source_location_access_path` VARCHAR(2048) DEFAULT NULL COMMENT '声明时活动副本访问地址',
  `source_location_hash` VARCHAR(64) DEFAULT NULL COMMENT '声明时活动副本哈希原值',
  `source_location_status` VARCHAR(32) DEFAULT NULL COMMENT '声明时活动副本状态',
  `new_hash` CHAR(64) NOT NULL COMMENT '替换后文件完整SHA-256',
  `new_size` INT NOT NULL COMMENT '替换后文件字节数',
  `new_original_name` VARCHAR(512) DEFAULT NULL COMMENT '替换后文件名称',
  `new_mime_type` VARCHAR(256) DEFAULT NULL COMMENT '替换后MIME类型',
  `new_width` INT DEFAULT NULL COMMENT '替换后图片宽度',
  `new_height` INT DEFAULT NULL COMMENT '替换后图片高度',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/RECOVERY_REQUIRED/COMMITTED/ABORTED',
  `open_resource_id` INT GENERATED ALWAYS AS (
    CASE
      WHEN `status` IN ('PENDING', 'RECOVERY_REQUIRED') THEN `resource_id`
      ELSE NULL
    END
  ) STORED COMMENT '开放事务资源ID，用于并发唯一约束',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '恢复或失败说明',
  `finished_at` DATETIME DEFAULT NULL COMMENT '事务决议时间',
  `artifacts_cleaned_at` DATETIME DEFAULT NULL COMMENT '临时文件和备份文件清理完成时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_content_replacement_operation` (`operation_id`),
  UNIQUE KEY `uk_resource_content_replacement_open` (`open_resource_id`),
  KEY `idx_resource_content_replacement_resource` (`resource_id`, `status`),
  KEY `idx_resource_content_replacement_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源内容替换事务';

CREATE TABLE IF NOT EXISTS `resource_content_replacement_target` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '替换物理目标ID',
  `replacement_id` BIGINT NOT NULL COMMENT '替换事务ID',
  `target_path` VARCHAR(2048) NOT NULL COMMENT '实际替换文件绝对路径',
  `target_path_hash` CHAR(64) GENERATED ALWAYS AS (SHA2(`target_path`, 256)) STORED COMMENT '目标路径SHA-256摘要',
  `temp_path` VARCHAR(2048) NOT NULL COMMENT '已完整校验的新文件临时路径',
  `backup_path` VARCHAR(2048) NOT NULL COMMENT '已完整校验的旧文件备份路径',
  `source_hash` CHAR(64) NOT NULL COMMENT '旧文件完整SHA-256',
  `new_hash` CHAR(64) NOT NULL COMMENT '新文件完整SHA-256',
  `observed_hash` CHAR(64) DEFAULT NULL COMMENT '最近完整回读的目标SHA-256',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/NEW_VERIFIED/OLD_VERIFIED/UNKNOWN',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_content_replacement_target` (`replacement_id`, `target_path_hash`),
  KEY `idx_resource_content_replacement_target_status` (`replacement_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源内容替换物理目标与恢复证据';