-- ============================================================
-- 变更说明：article 表新增 publish_time 首次公开发布时间列
-- 变更内容：
--   publish_time 记录文章第一次从隐藏变为公开（或创建即公开）的时间：
--     创建即公开 — 保存时立刻写入
--     先隐藏后发布 — 首次切换为可见时补记，之后隐藏/再公开不再刷新
--   用途：RSS 订阅源的 pubDate 与排序以此为准，
--   解决"先建隐藏稿、一个月后才发布"时 create_time 过旧导致
--   订阅时间失真、feed 顺序错乱的问题（sitemap 仍按 update_time）。
--   历史数据回填：已公开文章取 create_time 作为近似发布时间。
-- 日期：2026-07-26
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- 添加 publish_time 字段
ALTER TABLE `article`
    ADD COLUMN IF NOT EXISTS `publish_time` datetime DEFAULT NULL
    COMMENT '首次公开发布时间（RSS pubDate 口径，再次隐藏/公开不刷新）'
    AFTER `create_time`;

-- 回填存量已公开文章：以创建时间作为近似的首次发布时间
UPDATE `article`
    SET `publish_time` = `create_time`
    WHERE `publish_time` IS NULL AND `view_status` = 1;

-- 添加索引以优化 RSS 按发布时间排序的查询
ALTER TABLE `article`
    ADD INDEX IF NOT EXISTS `idx_article_publish_time` (`publish_time`);
