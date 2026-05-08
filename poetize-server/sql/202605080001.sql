-- ============================================================
-- 功能说明：补录站点默认图标资源
-- 变更内容：
--   1. 将 public 根目录下的 poetize.jpg 登记到 resource 表
--   2. 使用站点访问路径 /poetize.jpg，构建后对应前台 dist 根目录文件
-- 日期：2026-05-08
-- ============================================================

UPDATE `resource`
SET `type` = 'assets',
    `size` = 30312,
    `resource_hash` = '7460056A7CE3125039B7F35604CA5BCE867E3022B79F698A21308827E3791213',
    `original_name` = 'poetize.jpg',
    `mime_type` = 'image/jpeg',
    `status` = 1,
    `store_type` = 'local'
WHERE `path` = '/poetize.jpg';

INSERT INTO `resource`
    (`user_id`, `type`, `path`, `size`, `resource_hash`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`)
SELECT
    1,
    'assets',
    '/poetize.jpg',
    30312,
    '7460056A7CE3125039B7F35604CA5BCE867E3022B79F698A21308827E3791213',
    'poetize.jpg',
    'image/jpeg',
    1,
    'local',
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `resource` WHERE `path` = '/poetize.jpg'
);
