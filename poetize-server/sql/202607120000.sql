-- ================================================================
-- 新增首页标题字段：home_title
-- 执行时间：2026-07-12
-- 说明：用于 ICP 备案名展示，首页标题与子页品牌后缀解耦
-- 为空时回退到 web_title，完全向后兼容
-- 本脚本可以安全重复执行（幂等性）
-- ================================================================

SET @dbname = DATABASE();
SET @tablename = 'web_info';
SET @columnname = 'home_title';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `home_title` VARCHAR(255) NULL DEFAULT NULL COMMENT ''首页标题（为空时回退到 web_title，常用于 ICP 备案名展示）'' AFTER `web_title`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 执行完成后，请重启后端服务以应用更改
-- 本脚本可以安全重复执行，不会报错
