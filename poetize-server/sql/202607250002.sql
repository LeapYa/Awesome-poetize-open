-- ============================================================
-- 变更说明：history_info 表新增 visit_source 访问来源渠道列
-- 变更内容：
--   visit_source 标记访问记录的采集渠道：
--     track — 前端 JS 上报（/track/pageview），说明访客真实执行了 JS
--     nginx — Nginx 日志消费补录，说明该访问从未触发前端 JS
--   用途：浏览器 UA + 从不执行 JS 的访问大概率是高仿爬虫，
--   访问统计以此把"UA 像浏览器"进一步拆分为 JS 已验证 / 无 JS 两档，
--   避免不执行 JS 的伪装爬虫被计入真实访客。
--   历史数据无法回溯采集渠道，保持 NULL（统计口径中单独归为存量未知）。
-- 日期：2026-07-25
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- 添加 visit_source 字段
ALTER TABLE `history_info`
    ADD COLUMN IF NOT EXISTS `visit_source` varchar(16) DEFAULT NULL 
    COMMENT '访问采集渠道 [track:前端 JS 上报，nginx:Nginx 日志补录]' 
    AFTER `bot_verify_reason`;

-- 添加联合索引以优化按渠道和时间过滤的查询性能
ALTER TABLE `history_info`
    ADD INDEX IF NOT EXISTS `idx_history_visit_source_time` (`visit_source`, `create_time`);
