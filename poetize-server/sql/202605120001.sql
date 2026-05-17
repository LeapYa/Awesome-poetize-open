-- ============================================================
-- 功能说明：补录默认 AI 头像资源
-- 变更内容：
--   1. 将 public/static/assets 下的 ai_avatar.png 登记到 resource 表
--   2. 使用统一静态资源路径 /static/assets/ai_avatar.png
-- 日期：2026-05-12
-- ============================================================

UPDATE `resource`
SET `path` = '/static/assets/ai_avatar.png',
    `type` = 'assets',
    `size` = 1332990,
    `resource_hash` = '79E7B5285919D0783D11C371843D010B68CB9A1CE38E74ED8E326113F6D3A8CC',
    `original_name` = 'ai_avatar.png',
    `mime_type` = 'image/png',
    `status` = 1,
    `store_type` = 'local'
WHERE `path` IN ('/ai_avatar.png', '/static/assets/ai_avatar.png');

INSERT INTO `resource`
    (`user_id`, `type`, `path`, `size`, `resource_hash`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`)
SELECT
    1,
    'assets',
    '/static/assets/ai_avatar.png',
    1332990,
    '79E7B5285919D0783D11C371843D010B68CB9A1CE38E74ED8E326113F6D3A8CC',
    'ai_avatar.png',
    'image/png',
    1,
    'local',
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `resource` WHERE `path` = '/static/assets/ai_avatar.png'
);
