-- ============================================================
-- 变更说明：web_info 表新增 logo_image 网站Logo图片列
-- 变更内容：
--   logo_image 存储网站 Logo 图片URL：
--     前台导航栏左侧优先显示 Logo 图片，为空时回退显示网站名称 web_name。
--   后台"网站设置-基础信息"页可配置（支持上传图片或填写图片URL）。
-- 日期：2026-07-26
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- 添加 logo_image 字段
ALTER TABLE `web_info`
    ADD COLUMN IF NOT EXISTS `logo_image` varchar(512) DEFAULT NULL
    COMMENT '网站Logo图片URL（前台导航栏优先显示Logo，为空时显示网站名称）'
    AFTER `web_title`;
