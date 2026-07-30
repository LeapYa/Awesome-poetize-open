package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.ThirdPartyOauthConfig;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 第三方OAuth登录配置表 服务类
 * </p>
 *
 * @author LeapYa
 * @since 2025-07-19
 */
public interface ThirdPartyOauthConfigService extends IService<ThirdPartyOauthConfig> {

    /**
     * 根据平台类型获取配置
     * @param platformType 平台类型
     * @return 配置信息
     */
    ThirdPartyOauthConfig getByPlatformType(String platformType);

    /**
     * 获取所有启用的平台配置
     * @return 启用的配置列表
     */
    List<ThirdPartyOauthConfig> getEnabledConfigs();

    /**
     * 获取全局启用且平台启用的配置
     * @return 激活的配置列表
     */
    List<ThirdPartyOauthConfig> getActiveConfigs();

    /**
     * 获取所有配置（按排序顺序）
     * @return 所有配置列表
     */
    List<ThirdPartyOauthConfig> getAllConfigs();

    /**
     * 更新全局启用状态
     * @param globalEnabled 全局启用状态
     * @return 更新结果
     */
    PoetryResult<Boolean> updateGlobalEnabled(Boolean globalEnabled);

    /**
     * 更新平台启用状态
     * @param platformType 平台类型
     * @param enabled 启用状态
     * @return 更新结果
     */
    PoetryResult<Boolean> updatePlatformEnabled(String platformType, Boolean enabled);

    /**
     * 批量更新配置
     * @param configs 配置列表
     * @return 更新结果
     */
    PoetryResult<Boolean> batchUpdateConfigs(List<ThirdPartyOauthConfig> configs);

    /**
     * 获取第三方登录配置（兼容旧接口）
     * @return 配置信息
     */
    PoetryResult<Map<String, Object>> getThirdLoginConfig();

    /**
     * 更新第三方登录配置（兼容旧接口）
     * @param config 配置信息
     * @return 更新结果
     */
    PoetryResult<Boolean> updateThirdLoginConfig(Map<String, Object> config);

    /**
     * 验证配置完整性
     * @return 验证结果
     */
    PoetryResult<Map<String, Object>> validateConfigs();

    /**
     * 从JSON文件迁移配置
     * @param jsonFilePath JSON文件路径
     * @return 迁移结果
     */
    PoetryResult<Boolean> migrateFromJsonFile(String jsonFilePath);

    /**
     * 从JSON字符串迁移配置
     * @param jsonString JSON字符串
     * @return 迁移结果
     */
    PoetryResult<Boolean> migrateFromJsonString(String jsonString);

    /**
     * 重置为默认配置
     * @return 重置结果
     */
    PoetryResult<Boolean> resetToDefault();

    /**
     * 导出配置为JSON格式
     * @return JSON字符串
     */
    PoetryResult<String> exportToJson();

    /**
     * 初始化默认配置
     * @return 初始化结果
     */
    PoetryResult<Boolean> initDefaultConfigs();

    /**
     * 更新平台配置
     * @param config 配置信息
     * @return 更新结果
     */
    PoetryResult<ThirdPartyOauthConfig> updatePlatformConfig(ThirdPartyOauthConfig config);

    /**
     * 获取配置统计信息
     * @return 统计信息
     */
    PoetryResult<Map<String, Object>> getConfigStats();
    
    /**
     * 构建平台默认回调地址（根据站点地址自动生成）
     * @param platformType 平台类型
     * @return 默认回调地址，如 https://example.com/callback/github
     */
    String buildDefaultRedirectUri(String platformType);

    /**
     * 解析平台生效的回调地址：配置了自定义值则用自定义值，否则自动生成
     * @param config 平台配置
     * @return 生效的回调地址
     */
    String resolveRedirectUri(ThirdPartyOauthConfig config);

    /**
     * 新增一个自定义 OAuth2/OIDC 平台实例（platform_type 自动生成 custom_* 标识）
     * @return 新建的平台配置
     */
    PoetryResult<ThirdPartyOauthConfig> createCustomPlatform();

    /**
     * 删除自定义平台实例（仅允许 custom/custom_* 平台）
     * @param platformType 平台类型
     * @return 删除结果
     */
    PoetryResult<Boolean> deleteCustomPlatform(String platformType);

    /**
     * 并行检查所有OAuth平台的可用状态（用于登录页显示）
     * @return 平台状态Map，key为平台类型，value为是否可用
     */
    Map<String, Boolean> getAllPlatformsStatus();
}
