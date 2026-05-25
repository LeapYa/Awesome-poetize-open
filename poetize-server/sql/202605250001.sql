-- ============================================================
-- 功能说明：访问统计持久化UA分类与搜索引擎真实性验证结果
-- 变更内容：
--   1. history_info.ua_type：保存UA分类类型
--   2. history_info.ua_name：保存UA聚合展示名称
--   3. history_info.bot_verify_status：保存搜索引擎IP验证状态
--   4. history_info.bot_verify_reason：保存搜索引擎IP验证原因
-- 日期：2026-05-25
-- ============================================================

ALTER TABLE `history_info`
ADD COLUMN IF NOT EXISTS `ua_type` varchar(32) DEFAULT NULL COMMENT 'User-Agent类型' AFTER `user_agent`;

ALTER TABLE `history_info`
ADD COLUMN IF NOT EXISTS `ua_name` varchar(128) DEFAULT NULL COMMENT 'User-Agent聚合名称' AFTER `ua_type`;

ALTER TABLE `history_info`
ADD COLUMN IF NOT EXISTS `bot_verify_status` varchar(32) DEFAULT NULL COMMENT '搜索引擎真实性验证状态' AFTER `ua_name`;

ALTER TABLE `history_info`
ADD COLUMN IF NOT EXISTS `bot_verify_reason` varchar(255) DEFAULT NULL COMMENT '搜索引擎真实性验证原因' AFTER `bot_verify_status`;

CREATE INDEX IF NOT EXISTS `idx_history_ua_type_time` ON `history_info` (`ua_type`, `create_time`);

CREATE INDEX IF NOT EXISTS `idx_history_ua_type_name` ON `history_info` (`ua_type`, `ua_name`);
