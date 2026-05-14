-- ============================================================
-- 功能说明：补录默认 AI 头像资源
-- 变更内容：
--   1. 将 public 根目录下的 ai_avatar.png 登记到 resource 表
--   2. 使用站点访问路径 /ai_avatar.png，构建后对应前台 dist 根目录文件
-- 日期：2026-05-12
-- ============================================================

UPDATE `resource`
SET `type` = 'assets',
    `size` = 1332990,
    `resource_hash` = '79E7B5285919D0783D11C371843D010B68CB9A1CE38E74ED8E326113F6D3A8CC',
    `original_name` = 'ai_avatar.png',
    `mime_type` = 'image/png',
    `status` = 1,
    `store_type` = 'local'
WHERE `path` = '/ai_avatar.png';

INSERT INTO `resource`
    (`user_id`, `type`, `path`, `size`, `resource_hash`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`)
SELECT
    1,
    'assets',
    '/ai_avatar.png',
    1332990,
    '79E7B5285919D0783D11C371843D010B68CB9A1CE38E74ED8E326113F6D3A8CC',
    'ai_avatar.png',
    'image/png',
    1,
    'local',
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `resource` WHERE `path` = '/ai_avatar.png'
);
