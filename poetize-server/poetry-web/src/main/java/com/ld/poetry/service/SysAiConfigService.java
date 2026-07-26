package com.ld.poetry.service;

import com.ld.poetry.entity.SysAiConfig;

import java.util.List;
import java.util.Map;

/**
 * AI配置服务接口
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
public interface SysAiConfigService {

    /**
     * 根据配置类型和名称获取配置
     *
     * @param configType 配置类型 (ai_chat/ai_api/article_ai)
     * @param configName 配置名称 (默认为"default")
     * @return AI配置对象，不存在返回null
     */
    SysAiConfig getConfig(String configType, String configName);

    /**
     * 获取AI聊天配置（脱敏显示）
     *
     * @param configName 配置名称
     * @return AI配置对象（API密钥已脱敏）
     */
    SysAiConfig getAiChatConfig(String configName);

    /**
     * 获取AI聊天流式配置（前端初始化用）
     * 返回简化的配置信息，用于前端聊天界面初始化
     *
     * @param configName 配置名称
     * @return 流式配置Map
     */
    Map<String, Object> getStreamingConfig(String configName);

    /**
     * 获取AI聊天配置（完整未脱敏，供内部服务使用）
     *
     * @param configName 配置名称
     * @return AI配置对象（API密钥完整）
     */
    SysAiConfig getAiChatConfigInternal(String configName);

    /**
     * 获取文章AI助手配置（脱敏显示）
     *
     * @param configName 配置名称
     * @return AI配置对象（API密钥已脱敏）
     */
    SysAiConfig getArticleAiConfig(String configName);

    /**
     * 获取文章AI助手配置（完整未脱敏，供内部服务使用）
     *
     * @param configName 配置名称
     * @return AI配置对象（API密钥完整）
     */
    SysAiConfig getArticleAiConfigInternal(String configName);

    /**
     * 为生图测试回填已保存的密钥。
     *
     * <p>前端加载已保存配置后 api_key 字段为空，提交测试时不会携带该字段。
     * 此方法当 incomingImageConfig 中 api_key 或 dedicated_llm.api_key 缺失/为 {@code ***} 占位时，
     * 从已保存配置中解密回填真实密钥，保留其它字段的当前值（不整体覆盖）。
     *
     * @param incomingImageConfig 前端提交的生图配置 JSON
     * @param configName          配置名称
     * @return 合并后的生图配置 JSON（密钥已回填则更新，否则原样返回）
     */
    String resolveImageConfigSecretsForTest(String incomingImageConfig, String configName);

    /**
     * 获取AI API配置
     *
     * @param configName 配置名称
     * @return AI配置对象
     */
    SysAiConfig getAiApiConfig(String configName);

    /**
     * 保存或更新配置
     *
     * @param config AI配置对象
     * @return 是否成功
     */
    boolean saveOrUpdateConfig(SysAiConfig config);

    /**
     * 保存AI聊天配置
     *
     * @param config AI配置对象
     * @return 是否成功
     */
    boolean saveAiChatConfig(SysAiConfig config);

    /**
     * 保存文章AI助手配置
     *
     * @param config AI配置对象
     * @return 是否成功
     */
    boolean saveArticleAiConfig(SysAiConfig config);

    /**
     * 保存AI API配置
     *
     * @param config AI配置对象
     * @return 是否成功
     */
    boolean saveAiApiConfig(SysAiConfig config);

    /**
     * 切换启用状态
     *
     * @param id 配置ID
     * @return 是否成功
     */
    boolean toggleEnabled(Integer id);

    /**
     * 测试AI连接
     *
     * @param config AI配置对象
     * @return 测试结果
     */
    Map<String, Object> testConnection(SysAiConfig config);

    /**
     * 查询所有配置列表
     *
     * @return 配置列表
     */
    List<SysAiConfig> listAllConfigs();

    /**
     * 根据类型查询配置列表
     *
     * @param configType 配置类型
     * @return 配置列表
     */
    List<SysAiConfig> listConfigsByType(String configType);

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return 是否成功
     */
    boolean deleteConfig(Integer id);

    /**
     * 获取默认语言配置
     *
     * @return 语言配置Map
     */
    Map<String, Object> getDefaultLanguages();

    /**
     * 检查系统是否有文章数据
     *
     * @return 是否有文章
     */
    boolean hasArticles();

    /**
     * 获取语言映射配置（前台展示用，使用原生语言文字）
     * 从数据库统一配置中读取语言代码到自然语言的映射
     *
     * @return 语言映射Map，key为语言代码，value为自然语言名称
     */
    Map<String, String> getLanguageMapping();

    /**
     * 获取语言映射配置（后台管理用，使用中文）
     * 方便管理员理解
     *
     * @return 语言映射Map，key为语言代码，value为中文名称
     */
    Map<String, String> getLanguageMappingAdmin();
    
    /**
     * 将配置对象转换为Map，JSON字符串字段解析为对象
     * 用于内部服务调用，以结构化形式取用嵌套配置
     *
     * @param config AI配置对象
     * @return 转换后的Map，JSON字段已解析为对象
     */
    Map<String, Object> convertConfigToMap(SysAiConfig config);
}

