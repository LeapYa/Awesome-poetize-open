-- ============================================================
-- 功能说明：清理已发布文章内容中的旧版 poetize-* 标记
-- 变更内容：
--   前端重构将对外元素标记统一为 pb-* 前缀（代码只认新前缀）。
--   旧版已发布的文章正文中仍烘焙有旧前缀标记，需一次性数据迁移，
--   避免附件卡片退化为普通链接、视频卡片丢失样式。
--   1. 附件标记：poetize-attachment -> pb-attachment
--      （title="poetize-attachment"、class="poetize-attachment-card"、
--        class="poetize-attachment-pill-*"、data-poetize-attachment-*）
--   2. 视频标记：精确替换完整名称，避免误伤 URL/代码块中的 poetize-video- 子串
--      poetize-video-card      -> pb-video-card       (class)
--      poetize-video-player    -> pb-video-player     (class)
--      poetize-video-play-overlay -> pb-video-play-overlay (class)
--      poetize-video-ready     -> pb-video-ready      (data-poetize-video-ready)
--   范围：article.article_content、article_translation.content
--   （草稿 CRDT 快照为 base64 编码的临时协同状态，不在此处理）
-- 日期：2026-07-21
-- 本脚本可安全重复执行（幂等：REPLACE 无匹配时不改动，WHERE 限定受影响行）
--
-- ⚠ 生产执行前请先备份数据库，并运行以下查询排查误伤：
--
--   SELECT id, article_title FROM article
--   WHERE article_content LIKE '%poetize-attachment%'
--     AND article_content NOT LIKE '%title="poetize-attachment%'
--     AND article_content NOT LIKE '%class="poetize-attachment%';
--
--   若结果非空，逐条确认是否为 HTML 属性中的合法标记后再执行。
-- ============================================================

-- 1. 附件标记（poetize-attachment 前缀足够独特，宽替换安全）
UPDATE `article`
SET `article_content` = REPLACE(`article_content`, 'poetize-attachment', 'pb-attachment')
WHERE `article_content` LIKE '%poetize-attachment%';

UPDATE `article_translation`
SET `content` = REPLACE(`content`, 'poetize-attachment', 'pb-attachment')
WHERE `content` LIKE '%poetize-attachment%';

-- 2. 视频标记（精确替换完整名称，不用 poetize-video- 宽前缀，防误伤 URL/代码块）
UPDATE `article`
SET `article_content` = REPLACE(REPLACE(REPLACE(REPLACE(
    `article_content`,
    'poetize-video-card', 'pb-video-card'),
    'poetize-video-player', 'pb-video-player'),
    'poetize-video-play-overlay', 'pb-video-play-overlay'),
    'poetize-video-ready', 'pb-video-ready')
WHERE `article_content` LIKE '%poetize-video-%';

UPDATE `article_translation`
SET `content` = REPLACE(REPLACE(REPLACE(REPLACE(
    `content`,
    'poetize-video-card', 'pb-video-card'),
    'poetize-video-player', 'pb-video-player'),
    'poetize-video-play-overlay', 'pb-video-play-overlay'),
    'poetize-video-ready', 'pb-video-ready')
WHERE `content` LIKE '%poetize-video-%';
