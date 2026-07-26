package com.ld.poetry.dao;

import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 第三方OAuth登录配置表 Mapper 接口
 * </p>
 *
 * @author LeapYa
 * @since 2025-07-19
 */
@Mapper
public interface ThirdPartyOauthConfigMapper extends BaseMapper<ThirdPartyOauthConfig> {

    /**
     * 根据平台类型获取配置
     */
    @Select("SELECT * FROM third_party_oauth_config WHERE platform_type = #{platformType} AND deleted = 0")
    ThirdPartyOauthConfig getByPlatformType(@Param("platformType") String platformType);

    /**
     * 获取所有启用的平台配置
     */
    @Select("SELECT * FROM third_party_oauth_config WHERE enabled = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<ThirdPartyOauthConfig> getEnabledConfigs();

    /**
     * 获取全局启用且平台启用的配置
     */
    @Select("SELECT * FROM third_party_oauth_config WHERE enabled = 1 AND global_enabled = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<ThirdPartyOauthConfig> getActiveConfigs();

    /**
     * 获取所有配置（按排序顺序）
     */
    @Select("SELECT * FROM third_party_oauth_config WHERE deleted = 0 ORDER BY sort_order ASC")
    List<ThirdPartyOauthConfig> getAllConfigs();

    /**
     * 更新全局启用状态
     */
    @Update("UPDATE third_party_oauth_config SET global_enabled = #{globalEnabled}, update_time = NOW() WHERE deleted = 0")
    int updateGlobalEnabled(@Param("globalEnabled") Boolean globalEnabled);

    /**
     * 更新平台启用状态
     */
    @Update("UPDATE third_party_oauth_config SET enabled = #{enabled}, update_time = NOW() WHERE platform_type = #{platformType} AND deleted = 0")
    int updatePlatformEnabled(@Param("platformType") String platformType, @Param("enabled") Boolean enabled);

    /**
     * 批量更新排序顺序
     */
    @Update("UPDATE third_party_oauth_config SET sort_order = #{sortOrder}, update_time = NOW() WHERE platform_type = #{platformType} AND deleted = 0")
    int updateSortOrder(@Param("platformType") String platformType, @Param("sortOrder") Integer sortOrder);

    /**
     * 检查是否存在指定平台类型的配置
     */
    @Select("SELECT COUNT(*) FROM third_party_oauth_config WHERE platform_type = #{platformType} AND deleted = 0")
    int countByPlatformType(@Param("platformType") String platformType);

    /**
     * 获取配置统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as total_count, " +
            "SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as enabled_count, " +
            "SUM(CASE WHEN global_enabled = 1 THEN 1 ELSE 0 END) as global_enabled_count, " +
            "SUM(CASE WHEN enabled = 1 AND global_enabled = 1 THEN 1 ELSE 0 END) as active_count " +
            "FROM third_party_oauth_config WHERE deleted = 0")
    java.util.Map<String, Object> getConfigStats();
}
