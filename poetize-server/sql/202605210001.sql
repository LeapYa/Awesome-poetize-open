-- ============================================================
-- 功能说明：访问统计增加页面与 User-Agent 持久化
-- 变更内容：
--   1. history_info.page_uri：保存页面访问路径，用于文章页访问排行
--   2. history_info.user_agent：保存页面访问的 User-Agent
--   3. idx_history_create_time：加速今日/昨日访问统计查询
-- 日期：2026-05-21
-- ============================================================

ALTER TABLE `history_info`
ADD COLUMN IF NOT EXISTS `page_uri` varchar(512) DEFAULT NULL COMMENT '页面URI' AFTER `city`;

ALTER TABLE `history_info`
ADD COLUMN IF NOT EXISTS `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent' AFTER `page_uri`;

CREATE INDEX IF NOT EXISTS `idx_history_create_time` ON `history_info` (`create_time`);
