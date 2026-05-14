-- ============================================================
-- AI配置统一管理表
-- 将Python端的三个JSON配置文件迁移到MySQL数据库
-- 支持：AI聊天配置、AI API配置、文章AI助手配置
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_type` varchar(50) NOT NULL COMMENT '配置类型 (ai_chat:AI聊天 ai_api:AI接口 article_ai:文章AI助手)',
  `config_name` varchar(100) DEFAULT 'default' COMMENT '配置名称/标识',
  `enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用 (0:否 1:是)',
  
  -- ========== 通用AI配置字段 ==========
  `provider` varchar(50) DEFAULT NULL COMMENT 'AI服务提供商 (openai/anthropic/custom/deepseek/siliconflow等)',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API密钥(加密存储)',
  `api_base` varchar(500) DEFAULT NULL COMMENT 'API基础地址',
  `model` varchar(100) DEFAULT NULL COMMENT '模型名称',
  
  -- ========== AI聊天参数配置 ==========
  `temperature` decimal(3,2) DEFAULT 0.70 COMMENT '温度参数(0.0-2.0)',
  `max_tokens` int DEFAULT 1000 COMMENT '最大生成令牌数',
  `top_p` decimal(3,2) DEFAULT 1.00 COMMENT 'Top-p采样参数(0.0-1.0)',
  `frequency_penalty` decimal(3,2) DEFAULT 0.00 COMMENT '频率惩罚(-2.0到2.0)',
  `presence_penalty` decimal(3,2) DEFAULT 0.00 COMMENT '存在惩罚(-2.0到2.0)',
  
  -- ========== AI聊天外观设置 ==========
  `chat_name` varchar(50) DEFAULT 'AI助手' COMMENT '聊天助手名称',
  `chat_avatar` varchar(500) DEFAULT '' COMMENT '聊天助手头像URL',
  `welcome_message` varchar(500) DEFAULT '你好！我是你的AI助手，有什么可以帮助你的吗？' COMMENT '欢迎消息',
  `placeholder_text` varchar(200) DEFAULT '输入你想说的话...' COMMENT '输入框占位文本',
  `theme_color` varchar(20) DEFAULT '#4facfe' COMMENT '主题颜色',
  
  -- ========== AI聊天功能设置 ==========
  `max_conversation_length` int DEFAULT 20 COMMENT '对话历史最大长度',
  `enable_context` tinyint(1) DEFAULT 1 COMMENT '启用上下文 (0:否 1:是)',
  `enable_typing_indicator` tinyint(1) DEFAULT 1 COMMENT '启用输入指示器 (0:否 1:是)',
  `response_delay` int DEFAULT 1000 COMMENT '响应延迟(毫秒)',
  `enable_quick_actions` tinyint(1) DEFAULT 1 COMMENT '启用快捷操作 (0:否 1:是)',
  `enable_chat_history` tinyint(1) DEFAULT 1 COMMENT '启用聊天历史 (0:否 1:是)',
  `enable_streaming` tinyint(1) DEFAULT 0 COMMENT '启用流式响应 (0:否 1:是)',
  `rate_limit` int DEFAULT 20 COMMENT '速率限制(每分钟消息数)',
  `max_message_length` int DEFAULT 500 COMMENT '单条消息最大长度',
  `require_login` tinyint(1) DEFAULT 1 COMMENT '需要登录 (0:否 1:是)',
  `enable_content_filter` tinyint(1) DEFAULT 1 COMMENT '启用内容过滤 (0:否 1:是)',
  
  -- ========== AI聊天高级功能 ==========
  `custom_instructions` text DEFAULT NULL COMMENT '自定义指令/系统提示词',
  `enable_thinking` tinyint(1) DEFAULT 0 COMMENT '启用思考模式 (0:否 1:是)',
  `enable_tools` tinyint(1) DEFAULT 1 COMMENT '启用MCP工具 (0:否 1:是)',
  
  -- ========== 记忆管理功能 ==========
  `enable_memory` tinyint(1) DEFAULT 0 COMMENT '启用Mem0记忆功能 (0:否 1:是)',
  `mem0_api_key` varchar(500) DEFAULT NULL COMMENT 'Mem0 API密钥(加密存储)',
  `memory_auto_save` tinyint(1) DEFAULT 1 COMMENT '自动保存对话记忆 (0:否 1:是)',
  `memory_auto_recall` tinyint(1) DEFAULT 1 COMMENT '自动检索相关记忆 (0:否 1:是)',
  `memory_recall_limit` int DEFAULT 3 COMMENT '检索记忆数量限制',
  
  -- ========== 文章AI助手配置字段 ==========
  `translation_type` varchar(20) DEFAULT 'none' COMMENT '翻译实现方式 (none:不翻译 baidu:百度翻译 custom:自定义API llm:使用全局AI模型 dedicated_llm:使用翻译独立AI模型)',
  `default_source_lang` varchar(10) DEFAULT 'zh' COMMENT '默认源语言',
  `default_target_lang` varchar(10) DEFAULT 'en' COMMENT '默认目标语言',
  
  -- ========== AI API配置字段 ==========
  `include_articles` tinyint(1) DEFAULT 0 COMMENT '包含文章数据 (0:否 1:是)',
  
  -- ========== JSON扩展字段 ==========
  `baidu_config` json DEFAULT NULL COMMENT '百度翻译配置 {app_id, app_secret}',
  `custom_config` json DEFAULT NULL COMMENT '自定义API配置 {api_url, api_key, app_secret}',
  `llm_config` json DEFAULT NULL COMMENT 'LLM配置 {model, api_url, api_key, prompt, interface_type, timeout}',
  `translation_llm_config` json DEFAULT NULL COMMENT '翻译独立AI配置 {model, api_url, api_key, prompt, interface_type, timeout}',
  `summary_config` json DEFAULT NULL COMMENT '摘要生成配置 {summaryMode, style, max_length, prompt, dedicated_llm}',
  `extra_config` json DEFAULT NULL COMMENT '其他扩展配置(JSON格式)',
  
  -- ========== 元数据字段 ==========
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_type_name` (`config_type`, `config_name`) COMMENT '配置类型和名称联合唯一索引',
  KEY `idx_config_type` (`config_type`) COMMENT '配置类型索引',
  KEY `idx_enabled` (`enabled`) COMMENT '启用状态索引'
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='AI配置统一管理表';

-- ============================================================
-- 插入默认配置数据
-- ============================================================

-- 1. AI聊天配置默认值
INSERT INTO `sys_ai_config` (
  `config_type`, `config_name`, `enabled`,
  `provider`, `api_key`, `api_base`, `model`,
  `temperature`, `max_tokens`, `top_p`, `frequency_penalty`, `presence_penalty`,
  `chat_name`, `chat_avatar`, `welcome_message`, `placeholder_text`, `theme_color`,
  `max_conversation_length`, `enable_context`, `enable_typing_indicator`, 
  `response_delay`, `enable_quick_actions`, `enable_chat_history`, `enable_streaming`,
  `rate_limit`, `max_message_length`, `require_login`, `enable_content_filter`,
  `custom_instructions`, `enable_thinking`, `enable_tools`,
  `enable_memory`, `mem0_api_key`, `memory_auto_save`, `memory_auto_recall`, `memory_recall_limit`,
  `remark`
) VALUES (
  'ai_chat', 'default', 0,
  'openai', '', '', 'gpt-3.5-turbo',
  0.70, 1000, 1.00, 0.00, 0.00,
  'AI助手', '', '你好！我是你的AI助手，有什么可以帮助你的吗？', '输入你想说的话...', '#4facfe',
  20, 1, 1,
  1000, 1, 1, 0,
  20, 500, 1, 1,
  '', 0, 1,
  0, '', 1, 1, 3,
  'AI聊天默认配置'
) ON DUPLICATE KEY UPDATE id=id;

-- 2. AI API配置默认值
INSERT INTO `sys_ai_config` (
  `config_type`, `config_name`, `enabled`,
  `provider`, `api_key`, `api_base`, `model`,
  `include_articles`,
  `remark`
) VALUES (
  'ai_api', 'default', 0,
  'openai', '', '', 'gpt-3.5-turbo',
  0,
  'AI API默认配置'
) ON DUPLICATE KEY UPDATE id=id;

-- 3. 文章AI助手配置默认值
INSERT INTO `sys_ai_config` (
  `config_type`, `config_name`, `enabled`,
  `translation_type`, `default_source_lang`, `default_target_lang`,
  `llm_config`, `summary_config`,
  `remark`
) VALUES (
  'article_ai', 'default', 0,
  'none', 'zh', 'en',
  JSON_OBJECT(
    'model', 'gpt-3.5-turbo',
    'api_url', '',
    'api_key', '',
    'prompt', '请将以下{format}从{source_lang}翻译成{target_lang}，保持原意和格式，只返回翻译结果，不要添加任何说明或注释：',
    'interface_type', 'openai',
    'timeout', 240
  ),
  JSON_OBJECT(
    'summaryMode', 'global',
    'style', 'concise',
    'max_length', 150,
    'prompt', '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 请直接返回JSON格式的摘要，不要添加任何markdown代码块标记、前缀或说明\n5. JSON格式示例：{lang_json_example}\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请直接返回JSON格式的摘要：\n{json_example}'
  ),
  '文章AI助手配置默认值'
) ON DUPLICATE KEY UPDATE id=id;


-- ============================================================
-- 字段说明和使用指南
-- ============================================================

-- 配置类型说明：
-- 1. ai_chat: AI聊天配置
--    - 主要字段：provider, api_key, api_base, model, temperature等聊天参数
--    - 外观字段：chat_name, chat_avatar, welcome_message, theme_color
--    - 功能字段：enable_streaming, rate_limit, enable_memory等
--
-- 2. ai_api: AI接口配置
--    - 主要字段：provider, api_key, api_base, model
--    - 特殊字段：include_articles
--
-- 3. article_ai: 文章AI助手配置（包含翻译、智能摘要等文章相关AI功能）
--    - 主要字段：translation_type, default_source_lang, default_target_lang
--    - JSON字段：baidu_config, custom_config, llm_config, translation_llm_config, summary_config
--    - translation_type决定翻译功能使用哪个实现方式：
--      * none: 不翻译（默认值，不生成translation表记录）
--      * baidu: 百度翻译API
--      * custom: 自定义翻译API
--      * llm: 使用全局AI模型（llm_config）
--      * dedicated_llm: 使用翻译独立AI模型（translation_llm_config）
--    - summary_config.summaryMode决定摘要生成方式（global/dedicated/textrank）
--
-- 安全注意事项：
-- - api_key 和 mem0_api_key 字段在应用层需要加密存储
-- - 查询时需要在应用层解密后使用
-- - 前端展示时需要脱敏处理（仅显示前4位和后4位）
--
-- 扩展性说明：
-- - extra_config 字段用于存储未来可能新增的配置项
-- - JSON字段支持灵活的嵌套结构
-- - 可通过 config_name 支持同类型多套配置
--
-- 语言映射说明：
-- - 语言代码到自然语言的映射直接定义在Java代码中（SysAiConfigService）
-- - 提供两套映射：前台展示用（原生语言）、后台管理用（中文）
-- - 前后端通过API接口统一获取，避免重复定义

-- ============================================================
-- 为 article_translation 表添加 summary 字段
-- 用于存储各语言的文章摘要
-- ============================================================

ALTER TABLE `article_translation`
ADD COLUMN `summary` TEXT NULL COMMENT '文章摘要' AFTER `content`;

-- 添加索引以提升查询性能（可选）
-- CREATE INDEX idx_article_translation_article_id ON article_translation(article_id);
