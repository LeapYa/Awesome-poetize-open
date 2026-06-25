-- ============================================================
-- 功能说明：审计日志表新增 AI 调用 Token 用量字段
-- 变更内容：
--   1. prompt_tokens      — 输入提示词消耗 Token
--   2. completion_tokens  — 模型输出消耗 Token
--   3. total_tokens       — 本次 AI 调用合计 Token
-- 设计说明：
--   - 仅 AI 类日志（log_type='AI'）会写入这三列，其它日志保持 NULL
--   - 列可空，便于区分"未上报 usage"（NULL）与"零消耗"（0）
--   - 配合前端日志页可直接筛选/排序 Token 用量
-- 日期：2026-06-26
-- ============================================================

ALTER TABLE `sys_audit_log`
    ADD COLUMN `prompt_tokens`     INT DEFAULT NULL COMMENT 'AI输入Token(仅AI日志)' AFTER `detail`,
    ADD COLUMN `completion_tokens` INT DEFAULT NULL COMMENT 'AI输出Token(仅AI日志)' AFTER `prompt_tokens`,
    ADD COLUMN `total_tokens`       INT DEFAULT NULL COMMENT 'AI合计Token(仅AI日志)' AFTER `completion_tokens`;
