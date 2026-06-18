-- ============================================================
-- 功能说明：为 AI 聊天新增图像识别（视觉模型）配置字段
-- 变更内容：
--   1. vision_supported  — 主模型是否原生支持视觉（默认关闭，由用户手动开启）
--   2. vision_provider   — 视觉模型服务商（openai/anthropic/deepseek/siliconflow/custom 等）
--   3. vision_api_key    — 视觉模型 API 密钥（加密存储）
--   4. vision_api_base   — 视觉模型 API 基础地址
--   5. vision_model      — 视觉模型名称
-- 设计说明：
--   - 当 vision_supported=1 时，前端上传图片后端直接构造多模态 UserMessage 发给主模型
--   - 当 vision_supported=0 且视觉模型已配置时，注册 analyze_image 工具，主模型按需调用视觉模型
--   - 当 vision_supported=0 且视觉模型未配置时，前端不展示图片上传入口
-- 日期：2026-06-17
-- ============================================================

ALTER TABLE `sys_ai_config`
    ADD COLUMN `vision_supported` tinyint(1) DEFAULT 0 COMMENT '主模型是否支持视觉(0:否 1:是)' AFTER `enable_tools`,
    ADD COLUMN `vision_provider` varchar(50) DEFAULT NULL COMMENT '视觉模型服务商(openai/anthropic/deepseek/siliconflow/custom等)' AFTER `vision_supported`,
    ADD COLUMN `vision_api_key` varchar(500) DEFAULT NULL COMMENT '视觉模型API密钥(加密存储)' AFTER `vision_provider`,
    ADD COLUMN `vision_api_base` varchar(500) DEFAULT NULL COMMENT '视觉模型API基础地址' AFTER `vision_api_key`,
    ADD COLUMN `vision_model` varchar(100) DEFAULT NULL COMMENT '视觉模型名称' AFTER `vision_api_base`;
