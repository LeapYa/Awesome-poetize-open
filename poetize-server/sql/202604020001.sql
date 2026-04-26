-- 版本升级迁移脚本 - 2026-04-02
-- 功能: 文章草稿与修订草稿 CRDT 协同基础表
-- 注意: 此脚本设计为幂等，可安全重复执行

CREATE TABLE IF NOT EXISTS `article_draft` (
    `id` VARCHAR(64) NOT NULL COMMENT '草稿ID',
    `owner_user_id` INT NOT NULL COMMENT '草稿归属用户ID',
    `draft_type` VARCHAR(32) NOT NULL DEFAULT 'CREATE' COMMENT '草稿类型 [CREATE:新建草稿, REVISION:修订草稿]',
    `article_id` INT DEFAULT NULL COMMENT '关联原文章ID，仅修订草稿使用',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '草稿状态',
    `title_cache` VARCHAR(500) DEFAULT NULL COMMENT '标题缓存',
    `crdt_snapshot_base64` MEDIUMTEXT DEFAULT NULL COMMENT 'CRDT 快照 Base64',
    `last_editor_id` INT DEFAULT NULL COMMENT '最后编辑人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by` VARCHAR(32) DEFAULT NULL COMMENT '修改人',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_draft_revision` (`draft_type`, `article_id`),
    KEY `idx_owner_status` (`owner_user_id`, `status`),
    KEY `idx_article_draft_article_id` (`article_id`),
    KEY `idx_last_editor` (`last_editor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章草稿表';

ALTER TABLE `article_draft`
    ADD COLUMN IF NOT EXISTS `draft_type` VARCHAR(32) NOT NULL DEFAULT 'CREATE' COMMENT '草稿类型 [CREATE:新建草稿, REVISION:修订草稿]' AFTER `owner_user_id`,
    ADD COLUMN IF NOT EXISTS `article_id` INT DEFAULT NULL COMMENT '关联原文章ID，仅修订草稿使用' AFTER `draft_type`;

UPDATE `article_draft`
SET `draft_type` = 'CREATE'
WHERE `draft_type` IS NULL OR `draft_type` = '';

SET @uk_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'article_draft'
      AND INDEX_NAME = 'uk_article_draft_revision'
);
SET @sql = IF(
    @uk_exists = 0,
    'ALTER TABLE `article_draft` ADD UNIQUE INDEX `uk_article_draft_revision` (`draft_type`, `article_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'article_draft'
      AND INDEX_NAME = 'idx_article_draft_article_id'
);
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE `article_draft` ADD INDEX `idx_article_draft_article_id` (`article_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `article_draft_collaborator` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `draft_id` VARCHAR(64) NOT NULL COMMENT '草稿ID',
    `user_id` INT NOT NULL COMMENT '协作者用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_draft_user` (`draft_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章草稿协作者表';
