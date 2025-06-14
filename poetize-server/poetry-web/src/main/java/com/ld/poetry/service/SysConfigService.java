package com.ld.poetry.service;

import com.ld.poetry.entity.SysConfig;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 参数配置表 服务类
 * </p>
 *
 * @author sara
 * @since 2024-03-23
 */
public interface SysConfigService extends IService<SysConfig> {
    
    /**
     * 根据配置键获取配置值
     * 
     * @param configKey 配置键
     * @return 配置值
     */
    String getConfigValueByKey(String configKey);
}
