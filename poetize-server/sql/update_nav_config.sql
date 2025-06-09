-- 检查列是否存在，如果不存在则添加
SET @exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'poetize'
    AND TABLE_NAME = 'web_info'
    AND COLUMN_NAME = 'nav_config'
);

-- 如果nav_config列不存在，则添加
SET @query = IF(@exists = 0, 
    'ALTER TABLE poetize.web_info ADD COLUMN nav_config text DEFAULT NULL COMMENT "导航栏配置JSON"',
    'SELECT "nav_config column already exists" as Message');

PREPARE stmt FROM @query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 更新现有记录，设置默认导航栏配置
UPDATE poetize.web_info
SET nav_config = '[{"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":true},{"name":"记录","icon":"📒","link":"#","type":"dropdown","order":2,"enabled":true},{"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":true},{"name":"百宝箱","icon":"🧰","link":"/favorite","type":"internal","order":4,"enabled":true},{"name":"留言","icon":"📪","link":"/message","type":"internal","order":5,"enabled":true},{"name":"联系我","icon":"💬","link":"#chat","type":"special","order":6,"enabled":true}]'
WHERE nav_config IS NULL OR nav_config = '' OR nav_config = '{}'; 