-- ============================================================
-- 功能说明：AI Skill 管理系统改造
-- 变更内容：
--   1. sys_ai_skill — Skill 主表（存储 SKILL.md 全文与解析后的元数据）
-- 设计说明：
--   - 替代原先硬编码在 sys_ai_config.extraConfig.commentSkill 的单一评论 Skill
--   - 支持多场景（comment/chat/article/universal）的 Skill 安装、启停
--   - 内置 Skill（is_builtin=1）禁止删除，保证系统兜底能力
--   - 加载机制：启用的 Skill 进入 AI 索引，AI 按意图自主 load_skill 加载；
--       评论场景额外取优先级最高的启用 Skill 做服务端强制注入（确定性强约束）
--   - 同一场景可启用多个 Skill
-- 日期：2026-06-24
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_ai_skill` (
  `id`               INT AUTO_INCREMENT PRIMARY KEY,
  `skill_key`        VARCHAR(64)  NOT NULL COMMENT 'Skill 唯一标识 (frontmatter name)',
  `skill_name`       VARCHAR(128) NOT NULL COMMENT '显示名称',
  `description`      VARCHAR(512) NOT NULL COMMENT 'frontmatter description',
  `version`          VARCHAR(32)  DEFAULT '1.0.0',
  `author`           VARCHAR(64)  DEFAULT '',
  `scene`            VARCHAR(32)  NOT NULL DEFAULT 'comment' COMMENT '适用场景: comment/chat/article/universal',
  `skill_content`    MEDIUMTEXT   NOT NULL COMMENT '完整 SKILL.md 原文',
  `skill_body`       MEDIUMTEXT   NOT NULL COMMENT '解析后的正文',
  `placeholders`     VARCHAR(512) DEFAULT '' COMMENT '支持的占位符 JSON',
  `enabled`          TINYINT(1)   DEFAULT 1,
  `is_builtin`       TINYINT(1)   DEFAULT 0 COMMENT '内置 Skill 不可删',
  `sort_order`       INT          DEFAULT 0,
  `remark`           VARCHAR(256) DEFAULT '',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_skill_key` (`skill_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Skill 管理表';

