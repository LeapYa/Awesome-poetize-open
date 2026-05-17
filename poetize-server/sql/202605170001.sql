-- ============================================================
-- 功能说明：将内置根路径图片迁移到统一静态资源目录
-- 变更内容：
--   1. /poetize.jpg -> /static/assets/poetize.jpg
--   2. /ai_avatar.png -> /static/assets/ai_avatar.png
-- 日期：2026-05-17
-- ============================================================

DELETE old_resource
FROM `resource` old_resource
JOIN `resource` new_resource
  ON new_resource.`path` = '/static/assets/poetize.jpg'
WHERE old_resource.`path` = '/poetize.jpg'
  AND old_resource.`id` <> new_resource.`id`;

UPDATE `resource`
SET `path` = '/static/assets/poetize.jpg',
    `type` = 'assets',
    `size` = 30312,
    `resource_hash` = '7460056A7CE3125039B7F35604CA5BCE867E3022B79F698A21308827E3791213',
    `original_name` = 'poetize.jpg',
    `mime_type` = 'image/jpeg',
    `status` = 1,
    `store_type` = 'local'
WHERE `path` IN ('/poetize.jpg', '/static/assets/poetize.jpg');

INSERT INTO `resource`
    (`user_id`, `type`, `path`, `size`, `resource_hash`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`)
SELECT
    1,
    'assets',
    '/static/assets/poetize.jpg',
    30312,
    '7460056A7CE3125039B7F35604CA5BCE867E3022B79F698A21308827E3791213',
    'poetize.jpg',
    'image/jpeg',
    1,
    'local',
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `resource` WHERE `path` = '/static/assets/poetize.jpg'
);

DELETE old_resource
FROM `resource` old_resource
JOIN `resource` new_resource
  ON new_resource.`path` = '/static/assets/ai_avatar.png'
WHERE old_resource.`path` = '/ai_avatar.png'
  AND old_resource.`id` <> new_resource.`id`;

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
