-- ============================================================
-- 功能说明：重命名评论区 AI 默认 Skill（去除品牌前缀）
-- 变更内容：
--   将 sys_ai_skill 与 sys_ai_config.extra_config.commentSkill 中
--   持久化的旧 skill_key "poetize-comment-reply" 重命名为 "comment-reply"，
--   并同步替换 SKILL.md 正文里的 H1 标题与描述文本中的品牌词
-- 设计说明：
--   - 默认 schema（poetry.sql / poetry_old.sql）的 sys_ai_config 种子
--     不包含 extra_config 字段、sys_ai_skill 无种子 INSERT，故无需改动
--   - 此脚本仅作用于已部署实例中由运行时迁移Runner或admin保存的旧数据
--   - 涉及三处文本替换：
--       1) skill_key 字符串 poetize-comment-reply → comment-reply
--       2) H1 标题 # Poetize Comment Reply → # Comment Reply
--       3) description 措辞 in Poetize shared comment sections → in shared comment sections
--   - 幂等：REPLACE 自然幂等，WHERE 子句过滤后二次执行命中 0 行
--   - 唯一索引冲突保护：若已存在 skill_key='comment-reply' 行（管理员手动创建），
--       跳过 skill_key 更新并删除残留的旧 poetize-comment-reply 行
-- 日期：2026-06-29
-- ============================================================

-- 1. 更新 sys_ai_skill 表中持久化的旧 Skill 内容

-- 1a. 更新文本字段 skill_content / skill_body / skill_name（无唯一约束风险）
UPDATE `sys_ai_skill`
SET `skill_content` = REPLACE(
        REPLACE(
            REPLACE(`skill_content`,
                'poetize-comment-reply', 'comment-reply'),
            '# Poetize Comment Reply', '# Comment Reply'),
        'in Poetize shared comment sections', 'in shared comment sections'),
    `skill_body`    = REPLACE(
        REPLACE(
            REPLACE(`skill_body`,
                'poetize-comment-reply', 'comment-reply'),
            '# Poetize Comment Reply', '# Comment Reply'),
        'in Poetize shared comment sections', 'in shared comment sections'),
    `skill_name`    = CASE
        WHEN `skill_name` = 'poetize-comment-reply' THEN 'comment-reply'
        ELSE `skill_name`
    END
WHERE `skill_key` = 'poetize-comment-reply'
   OR `skill_content` LIKE '%poetize-comment-reply%'
   OR `skill_content` LIKE '%Poetize Comment Reply%'
   OR `skill_content` LIKE '%in Poetize shared comment sections%'
   OR `skill_body`    LIKE '%poetize-comment-reply%'
   OR `skill_body`    LIKE '%Poetize Comment Reply%'
   OR `skill_body`    LIKE '%in Poetize shared comment sections%';

-- 1b. 更新 skill_key（唯一索引字段，需先检查冲突）
--     若已存在 skill_key='comment-reply' 的行，跳过更新（由 1c 删除残留旧行）
UPDATE `sys_ai_skill`
SET `skill_key` = 'comment-reply'
WHERE `skill_key` = 'poetize-comment-reply'
  AND NOT EXISTS (
      SELECT 1 FROM (
          SELECT 1 FROM `sys_ai_skill` WHERE `skill_key` = 'comment-reply'
      ) AS `existing_new_key`
  );

-- 1c. 若 1b 因冲突跳过，删除残留的旧 poetize-comment-reply 行
--     （管理员已手动创建 comment-reply，旧行已被取代）
DELETE FROM `sys_ai_skill`
WHERE `skill_key` = 'poetize-comment-reply'
  AND EXISTS (
      SELECT 1 FROM (
          SELECT 1 FROM `sys_ai_skill` WHERE `skill_key` = 'comment-reply'
      ) AS `existing_new_key`
  );

-- 2. 更新 sys_ai_config.extra_config 中持久化的 commentSkill JSON 字段
--    extra_config 为 json 类型列，使用 JSON_SET + JSON_UNQUOTE(JSON_EXTRACT) 链路替换
UPDATE `sys_ai_config`
SET `extra_config` = JSON_SET(
        `extra_config`,
        '$.commentSkill',
        REPLACE(
            REPLACE(
                REPLACE(
                    JSON_UNQUOTE(JSON_EXTRACT(`extra_config`, '$.commentSkill')),
                    'poetize-comment-reply', 'comment-reply'),
                '# Poetize Comment Reply', '# Comment Reply'),
            'in Poetize shared comment sections', 'in shared comment sections')
    )
WHERE JSON_EXTRACT(`extra_config`, '$.commentSkill') IS NOT NULL
  AND (
      JSON_UNQUOTE(JSON_EXTRACT(`extra_config`, '$.commentSkill')) LIKE '%poetize-comment-reply%'
      OR JSON_UNQUOTE(JSON_EXTRACT(`extra_config`, '$.commentSkill')) LIKE '%Poetize Comment Reply%'
      OR JSON_UNQUOTE(JSON_EXTRACT(`extra_config`, '$.commentSkill')) LIKE '%in Poetize shared comment sections%'
  );
