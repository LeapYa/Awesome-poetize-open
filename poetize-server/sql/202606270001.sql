-- AI生图功能配置：为 sys_ai_config 表新增 image_config JSON 列（镜像 summary_config 范式）
-- 存储 {imageMode, provider, model, api_url, api_key, size(宽高比), resolution(像素), quality, style_prompt, refine_prompt, timeout, dedicated_llm?}
-- imageMode: disabled(关闭) | plain(直接拼接,不用AI提炼) | global(使用全局AI提炼prompt) | dedicated(使用独立AI提炼prompt)
ALTER TABLE `sys_ai_config`
    ADD COLUMN `image_config` TEXT NULL
        COMMENT 'AI生图功能配置JSON：{imageMode, provider, model, api_url, api_key, size(宽高比), resolution(像素), quality, style_prompt, refine_prompt, timeout, dedicated_llm?}'
        AFTER `summary_config`;
