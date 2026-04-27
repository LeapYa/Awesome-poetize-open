-- ============================================================
-- 功能说明：资源表增加内容哈希，用于本地存储上传前复用已有资源
-- 变更内容：
--   1. resource 表新增 resource_hash 字段
--   2. 新增 store_type + resource_hash 复合索引
-- 日期：2026-04-27
-- ============================================================

ALTER TABLE `resource`
    ADD COLUMN IF NOT EXISTS `resource_hash` VARCHAR(64) DEFAULT NULL COMMENT '资源内容哈希（SHA-256）' AFTER `mime_type`;

CREATE INDEX IF NOT EXISTS `idx_store_hash` ON `resource` (`store_type`, `resource_hash`);
