package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.dao.SysConfigMapper;
import com.ld.poetry.entity.SysConfig;
import com.ld.poetry.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 参数配置表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2024-03-23
 */
@Service
@Slf4j
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public String getConfigValueByKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            log.warn("配置键为空");
            return null;
        }
        
        try {
            LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysConfig::getConfigKey, configKey);
            
            SysConfig sysConfig = this.getOne(queryWrapper);
            if (sysConfig != null) {
                log.debug("获取配置成功，key: {}, value: {}", configKey, sysConfig.getConfigValue());
                return sysConfig.getConfigValue();
            } else {
                log.warn("未找到配置项，key: {}", configKey);
                return null;
            }
        } catch (Exception e) {
            log.error("获取配置失败，key: {}, 错误: {}", configKey, e.getMessage(), e);
            return null;
        }
    }
} 