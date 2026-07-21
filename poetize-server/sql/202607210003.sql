-- ============================================================
-- 功能说明：清理已发布文章内容中的旧版 poetize-* 标记
-- 变更内容：
--   前端重构将对外元素标记统一为 pb-* 前缀（代码只认新前缀）。
--   旧版已发布的文章正文中仍烘焙有旧前缀标记，需一次性数据迁移，
--   避免附件卡片退化为普通链接、视频卡片丢失样式。
--   1. 附件链接 title 标记：poetize-attachment -> pb-attachment
--   2. 视频卡片 class：poetize-video-card / poetize-video-player /
--      poetize-video-play-overlay -> pb-video-*
--   范围：article.article_content、article_translation.content
--   （草稿 CRDT 快照为 base64 编码的临时协同状态，不在此处理）
-- 日期：2026-07-21
-- 本脚本可安全重复执行（幂等：REPLACE 无匹配时不改动，WHERE 限定受影响行）
-- ============================================================

-- 1. 附件链接 title 标记
UPDATE `article`
SET `article_content` = REPLACE(`article_content`, 'poetize-attachment', 'pb-attachment')
WHERE `article_content` LIKE '%poetize-attachment%';

UPDATE `article_translation`
SET `content` = REPLACE(`content`, 'poetize-attachment', 'pb-attachment')
WHERE `content` LIKE '%poetize-attachment%';

-- 2. 视频卡片 class（poetize-video-* -> pb-video-*）
UPDATE `article`
SET `article_content` = REPLACE(`article_content`, 'poetize-video-', 'pb-video-')
WHERE `article_content` LIKE '%poetize-video-%';

UPDATE `article_translation`
SET `content` = REPLACE(`content`, 'poetize-video-', 'pb-video-')
WHERE `content` LIKE '%poetize-video-%';
