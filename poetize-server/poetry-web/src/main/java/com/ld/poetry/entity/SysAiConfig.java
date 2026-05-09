package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI配置统一管理实体类
 * 支持三种配置类型：ai_chat(AI聊天)、ai_api(AI接口)、article_ai(文章AI助手)
 * 
 * TODO [架构问题] ai_chat vs article_ai 的模型配置存储方式不一致：
 * - ai_chat: 使用顶层字段 provider/apiKey/apiBase/model 存储
 * - article_ai: 使用 llmConfig JSON 字段 {model, api_url, api_key, ...} 存储
 * 迁移到 Spring AI 时需统一为一种方式，否则 DynamicChatClientFactory 需要处理两套逻辑。
 * 建议统一使用顶层字段，article_ai 的 llmConfig 仅用于翻译专用 LLM 等场景。
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
@Data
@TableName("sys_ai_config")
public class SysAiConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 配置类型 (ai_chat:AI聊天 ai_api:AI接口 article_ai:文章AI助手)
     * 其中 article_ai 包含：翻译功能、智能摘要、内容优化等文章相关的AI功能
     */
    private String configType;

    /**
     * 配置名称/标识
     */
    private String configName;

    /**
     * 是否启用 (0:否 1:是)
     */
    private Boolean enabled;

    // ========== 通用AI配置字段 ==========

    /**
     * AI服务提供商 (openai/anthropic/custom/deepseek/siliconflow等)
     */
    private String provider;

    /**
     * API密钥(加密存储)
     */
    private String apiKey;

    /**
     * API基础地址
     */
    private String apiBase;

    /**
     * 模型名称
     */
    private String model;

    // ========== AI聊天参数配置 ==========

    /**
     * 温度参数(0.0-2.0)
     */
    private BigDecimal temperature;

    /**
     * 最大生成令牌数
     */
    private Integer maxTokens;

    /**
     * Top-p采样参数(0.0-1.0)
     */
    private BigDecimal topP;

    /**
     * 频率惩罚(-2.0到2.0)
     */
    private BigDecimal frequencyPenalty;

    /**
     * 存在惩罚(-2.0到2.0)
     */
    private BigDecimal presencePenalty;

    // ========== AI聊天外观设置 ==========

    /**
     * 聊天助手名称
     */
    private String chatName;

    /**
     * 聊天助手头像URL
     */
    private String chatAvatar;

    /**
     * 欢迎消息
     */
    private String welcomeMessage;

    /**
     * 输入框占位文本
     */
    private String placeholderText;

    /**
     * 主题颜色
     */
    private String themeColor;

    // ========== AI聊天功能设置 ==========

    /**
     * 对话历史最大长度
     */
    private Integer maxConversationLength;

    /**
     * 启用上下文 (0:否 1:是)
     */
    private Boolean enableContext;

    /**
     * 启用输入指示器 (0:否 1:是)
     */
    private Boolean enableTypingIndicator;

    /**
     * 显示时间戳 (0:否 1:是)
     */
    @TableField("show_timestamp")
    private Boolean showTimestamp;

    /**
     * 响应延迟(毫秒)
     */
    private Integer responseDelay;

    /**
     * 启用快捷操作 (0:否 1:是)
     */
    private Boolean enableQuickActions;

    /**
     * 启用聊天历史 (0:否 1:是)
     */
    private Boolean enableChatHistory;

    /**
     * 启用流式响应 (0:否 1:是)
     */
    private Boolean enableStreaming;

    /**
     * 速率限制(每分钟消息数)
     */
    private Integer rateLimit;

    /**
     * 单条消息最大长度
     */
    private Integer maxMessageLength;

    /**
     * 需要登录 (0:否 1:是)
     */
    private Boolean requireLogin;

    /**
     * 启用内容过滤 (0:否 1:是)
     */
    private Boolean enableContentFilter;

    // ========== AI聊天高级功能 ==========

    /**
     * 自定义指令/系统提示词
     */
    @TableField("custom_instructions")
    private String customInstructions;

    /**
     * 启用思考模式 (0:否 1:是)
     */
    private Boolean enableThinking;

    /**
     * 思考程度 (low/medium/high/xhigh)
     */
    private String reasoningEffort;

    /**
     * 启用MCP工具 (0:否 1:是)
     */
    private Boolean enableTools;

    // ========== 记忆管理功能 ==========

    /**
     * 启用Mem0记忆功能 (0:否 1:是)
     */
    private Boolean enableMemory;

    /**
     * Mem0 API密钥(加密存储)
     */
    private String mem0ApiKey;

    /**
     * 自动保存对话记忆 (0:否 1:是)
     */
    private Boolean memoryAutoSave;

    /**
     * 自动检索相关记忆 (0:否 1:是)
     */
    private Boolean memoryAutoRecall;

    /**
     * 检索记忆数量限制
     */
    private Integer memoryRecallLimit;

    // ========== 文章AI助手配置字段 ==========

    /**
     * 翻译实现方式 (none:不翻译 baidu:百度翻译 youdao:有道云翻译 custom:自定义HTTP接口
     * tencent/aliyun/volcengine/huawei/google/azure_translator/deepl/aws/yandex:传统翻译API
     * llm:使用全局AI模型 dedicated_llm:使用翻译独立AI模型)
     */
    private String translationType;

    /**
     * 默认源语言
     */
    private String defaultSourceLang;

    /**
     * 默认目标语言
     */
    private String defaultTargetLang;

    // ========== AI API配置字段 ==========

    /**
     * 包含文章数据 (0:否 1:是)
     */
    private Boolean includeArticles;

    // ========== JSON扩展字段 ==========

    /**
     * 百度翻译配置 {app_id, app_secret}
     * 存储为JSON字符串，前端自动解析
     */
    private String baiduConfig;

    /**
     * API翻译扩展配置 {provider, api_url, app_key, api_key, app_secret, ...}
     * 百度翻译继续使用 baiduConfig，其他传统翻译API统一存放于此字段。
     */
    private String customConfig;

    /**
     * LLM配置 {model, api_url, api_key, prompt, interface_type, timeout}
     * OpenAI 兼容 interface_type 使用 Chat Completions。
     * 
     * TODO [架构问题] article_ai 类型使用此 JSON 字段存储模型配置，
     * 而 ai_chat 类型使用顶层 provider/apiKey/apiBase/model 字段。
     * Spring AI 迁移时需统一，避免 DynamicChatClientFactory 需要两套解析逻辑。
     */
    private String llmConfig;

    /**
     * 翻译独立AI配置 {model, api_url, api_key, prompt, interface_type, timeout}
     * OpenAI 兼容 interface_type 使用 Chat Completions。
     * 仅当translationType=dedicated_llm时使用
     */
    private String translationLlmConfig;

    /**
     * 摘要生成配置 {summaryMode, style, max_length, prompt, dedicated_llm}
     * summaryMode: global(使用全局AI) | dedicated(使用独立AI) | textrank(使用TextRank算法)
     * dedicated_llm: {model, api_url, api_key, interface_type, timeout}
     * OpenAI 兼容 interface_type 使用 Chat Completions。
     * (仅summaryMode=dedicated时存在)
     */
    private String summaryConfig;

    /**
     * 其他扩展配置(JSON格式)
     */
    private String extraConfig;

    // ========== 元数据字段 ==========

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
