-- ============================================================
-- 功能说明：AI 模型配置支持思考程度
-- 变更内容：
--   1. sys_ai_config 新增 reasoning_effort 字段
--   2. 兼容已启用思考模式的旧配置，默认补为 medium
-- 日期：2026-05-08
-- ============================================================

ALTER TABLE `sys_ai_config`
ADD COLUMN IF NOT EXISTS `reasoning_effort` varchar(20) DEFAULT NULL COMMENT '思考程度 (low/medium/high/xhigh)' AFTER `enable_thinking`;

UPDATE `sys_ai_config`
SET `reasoning_effort` = 'medium'
WHERE `config_type` = 'ai_chat'
  AND `enable_thinking` = 1
  AND (`reasoning_effort` IS NULL OR `reasoning_effort` = '');
