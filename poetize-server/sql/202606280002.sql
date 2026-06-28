-- ============================================================
-- 功能说明：AI 网页访问（Web Fetch）工具配置字段
-- 变更内容：
--   为 sys_ai_config 表新增三列：
--   - enable_web_fetch：网页访问工具独立开关（NULL=继承 enable_tools）
--   - enable_jina_reader：SPA fallback Jina Reader 开关（默认 1=开启）
--   - jina_api_key：Jina Reader API Key（加密存储）
-- 设计说明：
--   - enable_web_fetch 采用三态（NULL/0/1），NULL 时继承 enable_tools，向后兼容
--   - enable_jina_reader 默认 1 开启（Fetcher Chain 第 6 层兜底，前 5 层本地处理后触发频率极低）
--   - 无 API Key 时走免费 20 RPM 模式（永久免费），超限排队等待
--   - jina_api_key 与 api_key/mem0_api_key/vision_api_key 一致，由 SysAiConfigServiceImpl 加解密
--   - 仅影响 ai_chat 配置类型，article_ai/ai_api 不受影响
-- 日期：2026-06-28
-- ============================================================

ALTER TABLE `sys_ai_config`
    ADD COLUMN `enable_web_fetch` TINYINT NULL DEFAULT NULL
        COMMENT 'AI 网页访问工具开关 NULL=继承enable_tools 0=关闭 1=开启'
        AFTER `enable_tools`,
    ADD COLUMN `enable_jina_reader` TINYINT NOT NULL DEFAULT 1
        COMMENT 'SPA fallback Jina Reader 开关 1=启用 0=关闭（默认开启，Fetcher Chain 第 6 层兜底）'
        AFTER `enable_web_fetch`,
    ADD COLUMN `jina_api_key` VARCHAR(512) NULL DEFAULT NULL
        COMMENT 'Jina Reader API Key（加密存储，留空使用免费 20 RPM 模式）'
        AFTER `enable_jina_reader`;
