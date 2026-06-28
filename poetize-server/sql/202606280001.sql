-- ============================================================
-- 功能说明：访客来源统计（Referring Sites）
-- 变更内容：
--   为 history_info 表新增 referer 列，记录访客的 HTTP Referer
--   用于统计访客从哪些外部网站点击链接来到本站
-- 设计说明：
--   - referer 为空或站内跳转归为"直接访问(Direct)"
--   - 非空 referer 在查询时按 host 聚合统计来源域名
--   - 采集层(PageViewTrackController / NginxPageVisitLogConsumer)已捕获 Referer，
--     此前在 Redis 持久化环节被丢弃，本次补齐落库链路
--   - 查询走已有 idx_history_create_time 索引按时间范围过滤，无需额外加索引
-- 日期：2026-06-28
-- ============================================================

ALTER TABLE `history_info`
    ADD COLUMN `referer` varchar(512) DEFAULT NULL
        COMMENT 'HTTP Referer 来源页地址(用于来源站点统计)'
        AFTER `page_uri`;
