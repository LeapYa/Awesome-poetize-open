-- ============================================================
-- 新增 max_input_tokens 字段，升级 max_tokens 默认值
-- 支持模型上下文窗口感知的评论区增强功能
-- ============================================================

-- 1. 新增输入上下文令牌数限制字段
ALTER TABLE `sys_ai_config`
ADD COLUMN IF NOT EXISTS `max_input_tokens` int DEFAULT 131072 COMMENT '最大输入上下文令牌数(不填默认128K)';

-- 2. 升级 max_tokens 默认值（输出令牌数）
ALTER TABLE `sys_ai_config`
MODIFY COLUMN `max_tokens` int DEFAULT 8192 COMMENT '最大输出令牌数(不填默认8K)';