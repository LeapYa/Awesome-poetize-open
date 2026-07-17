-- ============================================================
-- 功能说明：清理已废弃的老版 4 分片字体方案残留
-- 变更内容：
--   1. 删除 sys_config 中老方案独有的 4 项配置（font.use.single / font.single.filename /
--      font.unicode.remote / font.unicode.path），保留新方案仍在使用的 font.cdn.base-url
--   2. 删除 resource 中老方案 4 个固定分片文件记录（font.base/level1/level2/other.woff2）
--      及其在 resource_location / resource_alias / resource_redirect /
--      resource_content_replacement(_target) 等关联表的物理副本与别名
-- 说明：当前字体方案已切换为 cn-font-split，运行时按 unicode-range 动态生成细粒度哈希分片，
--      不再需要老方案的固定 4 分片与 unicode_ranges.json 加载逻辑
-- 日期：2026-07-17
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- 1) 清理 sys_config 中老方案独有的废弃配置项（新方案仅依赖 font.cdn.base-url）
DELETE FROM `poetize`.`sys_config`
WHERE `config_key` IN (
    'font.use.single',
    'font.single.filename',
    'font.unicode.remote',
    'font.unicode.path'
);

-- 2) 清理老方案 4 个固定分片资源在关联表的记录
--    （resource 相关表无 ON DELETE CASCADE 外键，需手动级联删除）
-- 2.1) resource_content_replacement_target：通过 resource_content_replacement 关联到 resource
DELETE rcrt FROM `poetize`.`resource_content_replacement_target` rcrt
INNER JOIN `poetize`.`resource_content_replacement` rcr ON rcrt.`replacement_id` = rcr.`id`
INNER JOIN `poetize`.`resource` r ON rcr.`resource_id` = r.`id`
WHERE r.`path` IN (
    '/static/assets/font_chunks/font.base.woff2',
    '/static/assets/font_chunks/font.level1.woff2',
    '/static/assets/font_chunks/font.level2.woff2',
    '/static/assets/font_chunks/font.other.woff2'
);

-- 2.2) resource_content_replacement：资源内容替换事务
DELETE rcr FROM `poetize`.`resource_content_replacement` rcr
INNER JOIN `poetize`.`resource` r ON rcr.`resource_id` = r.`id`
WHERE r.`path` IN (
    '/static/assets/font_chunks/font.base.woff2',
    '/static/assets/font_chunks/font.level1.woff2',
    '/static/assets/font_chunks/font.level2.woff2',
    '/static/assets/font_chunks/font.other.woff2'
);

-- 2.3) resource_alias：资源别名
DELETE ra FROM `poetize`.`resource_alias` ra
INNER JOIN `poetize`.`resource` r ON ra.`resource_id` = r.`id`
WHERE r.`path` IN (
    '/static/assets/font_chunks/font.base.woff2',
    '/static/assets/font_chunks/font.level1.woff2',
    '/static/assets/font_chunks/font.level2.woff2',
    '/static/assets/font_chunks/font.other.woff2'
);

-- 2.4) resource_location：资源物理副本
DELETE rl FROM `poetize`.`resource_location` rl
INNER JOIN `poetize`.`resource` r ON rl.`resource_id` = r.`id`
WHERE r.`path` IN (
    '/static/assets/font_chunks/font.base.woff2',
    '/static/assets/font_chunks/font.level1.woff2',
    '/static/assets/font_chunks/font.level2.woff2',
    '/static/assets/font_chunks/font.other.woff2'
);

-- 2.5) resource_redirect：资源旧路径重定向
DELETE rr FROM `poetize`.`resource_redirect` rr
INNER JOIN `poetize`.`resource` r ON rr.`resource_id` = r.`id`
WHERE r.`path` IN (
    '/static/assets/font_chunks/font.base.woff2',
    '/static/assets/font_chunks/font.level1.woff2',
    '/static/assets/font_chunks/font.level2.woff2',
    '/static/assets/font_chunks/font.other.woff2'
);

-- 3) 最后删除 resource 主表中的 4 条老分片记录
DELETE FROM `poetize`.`resource`
WHERE `path` IN (
    '/static/assets/font_chunks/font.base.woff2',
    '/static/assets/font_chunks/font.level1.woff2',
    '/static/assets/font_chunks/font.level2.woff2',
    '/static/assets/font_chunks/font.other.woff2'
);
