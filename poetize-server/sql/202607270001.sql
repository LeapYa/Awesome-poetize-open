-- ============================================================
-- 变更说明：web_info 表新增登录页样式、登录页主题色、第三方按钮位置三列
-- 变更内容：
--   1. login_style 存储前台登录页样式
--      [classic:经典双滑块, card:简约卡片, glass:毛玻璃卡片, split:左右分栏,
--       minimal:极简纯色, terminal:终端极客风, immersive:沉浸式大字排版, frosted:磨砂典雅]：
--        classic 为原版双滑块登录/注册面板（默认值，存量站点升级后无变化）；
--        其余为新增的现代样式。
--   2. login_accent_color 存储登录页主题色（#rrggbb 十六进制，NULL/空 = 默认中性近黑）：
--        卡片系登录样式（card/glass/minimal/split）的主按钮，
--        以及所有非 classic 样式下的账号弹窗按钮、验证码滑块跟随此色；
--        classic 经典双滑块样式保持原版配色，不受影响。
--   3. login_third_position 存储卡片系样式的第三方登录按钮位置 [top:表单上方, bottom:表单下方]：
--        仅对 card/glass/minimal/split 生效，其余样式的第三方位置为各自设计的一部分。
--   后台"网站外观"页可切换样式、配置主题色（预设色板 + 自由取色）与第三方按钮位置。
-- 日期：2026-07-27
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- 添加 login_style 字段
ALTER TABLE `web_info`
    ADD COLUMN IF NOT EXISTS `login_style` varchar(20) DEFAULT 'classic'
    COMMENT '登录页样式 [classic:经典双滑块, card:简约卡片, glass:毛玻璃卡片, split:左右分栏, minimal:极简纯色, terminal:终端极客风, immersive:沉浸式大字排版, frosted:磨砂典雅]'
    AFTER `mouse_click_effect_config`;

-- 添加 login_accent_color 字段
ALTER TABLE `web_info`
    ADD COLUMN IF NOT EXISTS `login_accent_color` varchar(20) DEFAULT NULL
    COMMENT '登录页主题色（#rrggbb，空为默认中性色；仅现代登录样式生效）'
    AFTER `login_style`;

-- 添加 login_third_position 字段
ALTER TABLE `web_info`
    ADD COLUMN IF NOT EXISTS `login_third_position` varchar(10) DEFAULT 'top'
    COMMENT '卡片系登录样式的第三方按钮位置 [top:表单上方, bottom:表单下方]'
    AFTER `login_accent_color`;
