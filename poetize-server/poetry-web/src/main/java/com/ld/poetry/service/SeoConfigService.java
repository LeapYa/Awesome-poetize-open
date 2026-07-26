package com.ld.poetry.service;

import com.ld.poetry.entity.SeoConfig;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.Map;

/**
 * <p>
 * SEO配置服务接口
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
public interface SeoConfigService extends IService<SeoConfig> {
    
    /**
     * 获取完整的SEO配置（包含所有关联数据）
     */
    SeoConfig getFullSeoConfig();
    
    /**
     * 保存完整的SEO配置（包含所有关联数据）
     */
    boolean saveFullSeoConfig(SeoConfig seoConfig);
    
    /**
      * 获取SEO配置的JSON格式
     */
    Map<String, Object> getSeoConfigAsJson();
    
    /**
     * 从JSON格式更新SEO配置
     */
    boolean updateSeoConfigFromJson(Map<String, Object> jsonConfig);
    
    /**
     * 初始化默认SEO配置
     */
    boolean initDefaultSeoConfig();
}

