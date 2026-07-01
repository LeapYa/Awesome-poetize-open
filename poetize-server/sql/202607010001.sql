-- AI生图封面模板：为 image_config JSON 新增 cover_template 字段
-- cover_template: object(物品类真实感) | portrait(人物类真实感) | felt(毛毡Q版可爱风) | cyberpunk(赛博朋克霓虹风)
--               | watercolor(水彩手绘风) | ink(国风水墨画) | pixel(像素复古风) | 3d(3D渲染卡通风)
--               | minimal(极简几何风) | collage(复古拼贴风) | custom(自定义模板)
-- custom_refine_prompt: 仅 cover_template=custom 时使用，用户自定义的 LLM 系统提示词
--
-- 说明：
-- 1. cover_template / custom_refine_prompt 是 image_config JSON 中的新增 key，不需要 ALTER TABLE（image_config 已是 TEXT 类型）
-- 2. 后端 ImageConfigDto.fromJson() 已兼容缺失 cover_template 的情况（默认 'object'），此脚本仅用于数据一致性
-- 3. 模板模式下，材质/镜头/光影等细节全部由 LLM 根据文章内容提炼，用户只需选择模板类型
-- 4. 模板模式仅对 global/dedicated 生图模式有效；plain 模式下使用预设默认值
-- 5. 模板模式下 style_prompt 被忽略（模板已包含完整风格描述），避免中文前缀干扰英文模板
-- 6. custom 模板下，custom_refine_prompt 为空时会降级为 object 模板
--
-- 使用 MySQL 5.7+ 的 JSON 函数为现有 image_config 补充 cover_template / custom_refine_prompt 默认值。

-- 1. 为不包含 cover_template 的记录注入默认值 'object'
UPDATE `sys_ai_config`
SET `image_config` = JSON_SET(
    `image_config`,
    '$.cover_template',
    'object'
)
WHERE `image_config` IS NOT NULL
  AND `image_config` != ''
  AND JSON_VALID(`image_config`)
  AND NOT JSON_CONTAINS_PATH(`image_config`, 'one', '$.cover_template');

-- 2. 将现有值为 'none' 的更新为 'object'
UPDATE `sys_ai_config`
SET `image_config` = JSON_SET(
    `image_config`,
    '$.cover_template',
    'object'
)
WHERE `image_config` IS NOT NULL
  AND `image_config` != ''
  AND JSON_VALID(`image_config`)
  AND JSON_UNQUOTE(JSON_EXTRACT(`image_config`, '$.cover_template')) = 'none';

-- 3. 为不包含 custom_refine_prompt 的记录注入默认值 ''（空字符串，仅 custom 模板生效）
UPDATE `sys_ai_config`
SET `image_config` = JSON_SET(
    `image_config`,
    '$.custom_refine_prompt',
    ''
)
WHERE `image_config` IS NOT NULL
  AND `image_config` != ''
  AND JSON_VALID(`image_config`)
  AND NOT JSON_CONTAINS_PATH(`image_config`, 'one', '$.custom_refine_prompt');

-- 4. 将现有的 timeout 从 60 扩展为 120 秒，以兼容耗时较长的生成任务
UPDATE `sys_ai_config`
SET `image_config` = JSON_SET(
    `image_config`,
    '$.timeout',
    120
)
WHERE `image_config` IS NOT NULL
  AND `image_config` != ''
  AND JSON_VALID(`image_config`)
  AND (
      JSON_EXTRACT(`image_config`, '$.timeout') = 60
      OR NOT JSON_CONTAINS_PATH(`image_config`, 'one', '$.timeout')
  );
