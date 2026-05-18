-- ============================================================
-- 功能说明：为文章增加可选的SEO友好URL别名
-- 变更内容：
--   1. article.article_slug：文章URL别名，留空时继续使用数字ID
--   2. idx_article_slug：保证URL别名唯一并加速文章详情查询
-- 日期：2026-05-18
-- ============================================================

ALTER TABLE `article`
ADD COLUMN IF NOT EXISTS `article_slug` varchar(160) DEFAULT NULL COMMENT 'URL别名' AFTER `article_title`;

CREATE UNIQUE INDEX IF NOT EXISTS `idx_article_slug` ON `article` (`article_slug`);
