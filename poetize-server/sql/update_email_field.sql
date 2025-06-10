-- 为web_info表添加email字段
ALTER TABLE `poetize`.`web_info` ADD COLUMN `email` varchar(255) DEFAULT NULL COMMENT '联系邮箱' AFTER `footer_background_config`;

-- 更新poetize.sql文件中的web_info表定义
-- 请将此字段添加到您的poetize.sql文件的web_info表创建语句中:
-- `email` varchar(255) DEFAULT NULL COMMENT '联系邮箱',
