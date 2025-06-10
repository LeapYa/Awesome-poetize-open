-- 为web_info表添加页脚背景图片相关字段
-- 执行前请备份数据库

-- 检查字段是否已存在，如果不存在则添加
SELECT COLUMN_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'poetize' 
AND TABLE_NAME = 'web_info' 
AND COLUMN_NAME = 'footer_background_image';

-- 如果上述查询返回空结果，说明字段不存在，需要添加
ALTER TABLE `poetize`.`web_info` 
ADD COLUMN `footer_background_image` varchar(256) DEFAULT NULL COMMENT '页脚背景图片';

-- 检查第二个字段是否已存在
SELECT COLUMN_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'poetize' 
AND TABLE_NAME = 'web_info' 
AND COLUMN_NAME = 'footer_background_config';

-- 如果上述查询返回空结果，说明字段不存在，需要添加
ALTER TABLE `poetize`.`web_info` 
ADD COLUMN `footer_background_config` text DEFAULT NULL COMMENT '页脚背景图片位置配置(JSON格式)';

-- 验证字段是否成功添加
DESCRIBE `poetize`.`web_info`; 