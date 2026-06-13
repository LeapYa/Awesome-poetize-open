package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.dao.SysMailConfigMapper;
import com.ld.poetry.entity.SysMailConfig;
import com.ld.poetry.entity.dto.MailConfigDTO;
import com.ld.poetry.service.SysMailConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邮件配置服务实现
 */
@Slf4j
@Service
public class SysMailConfigServiceImpl extends ServiceImpl<SysMailConfigMapper, SysMailConfig> 
        implements SysMailConfigService {
    
    private static final JsonMapper objectMapper = JsonMapper.builder().build();
    
    @Override
    public List<MailConfigDTO> getAllConfigs() {
        List<SysMailConfig> entities = lambdaQuery()
                .orderByAsc(SysMailConfig::getSortOrder)
                .orderByDesc(SysMailConfig::getCreateTime)
                .list();
        
        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public MailConfigDTO getDefaultConfig() {
        SysMailConfig entity = lambdaQuery()
                .eq(SysMailConfig::getIsDefault, true)
                .eq(SysMailConfig::getEnabled, true)
                .one();
        
        if (entity == null) {
            // 如果没有默认配置，尝试获取第一个启用的配置
            entity = lambdaQuery()
                    .eq(SysMailConfig::getEnabled, true)
                    .orderByAsc(SysMailConfig::getSortOrder)
                    .last("LIMIT 1")
                    .one();
        }
        
        return entity != null ? convertToDTO(entity) : null;
    }
    
    @Override
    public Integer getDefaultConfigIndex() {
        List<SysMailConfig> allConfigs = lambdaQuery()
                .orderByAsc(SysMailConfig::getSortOrder)
                .orderByDesc(SysMailConfig::getCreateTime)
                .list();
        
        for (int i = 0; i < allConfigs.size(); i++) {
            if (Boolean.TRUE.equals(allConfigs.get(i).getIsDefault())) {
                return i;
            }
        }
        
        return -1;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConfigs(List<MailConfigDTO> configs, Integer defaultIndex) {
        try {
            // 1. 删除所有现有配置
            remove(new LambdaQueryWrapper<>());
            
            // 2. 保存新配置
            if (configs == null || configs.isEmpty()) {
                log.info("配置列表为空，已清空所有邮件配置");
                return true;
            }
            
            // 3. 转换并保存每个配置
            for (int i = 0; i < configs.size(); i++) {
                MailConfigDTO dto = configs.get(i);
                SysMailConfig entity = convertToEntity(dto);
                
                // 设置排序顺序
                entity.setSortOrder(i);
                
                // 设置是否为默认配置
                entity.setIsDefault(defaultIndex != null && defaultIndex == i);
                
                // 确保布尔值不为null
                if (entity.getUseSsl() == null) entity.setUseSsl(false);
                if (entity.getUseStarttls() == null) entity.setUseStarttls(false);
                if (entity.getAuth() == null) entity.setAuth(true);
                if (entity.getEnabled() == null) entity.setEnabled(true);
                if (entity.getTrustAllCerts() == null) entity.setTrustAllCerts(false);
                if (entity.getDebug() == null) entity.setDebug(false);
                if (entity.getUseProxy() == null) entity.setUseProxy(false);
                
                save(entity);
            }
            
            log.info("邮件配置保存成功，共{}个配置，默认索引: {}", configs.size(), defaultIndex);
            return true;
        } catch (Exception e) {
            log.error("保存邮件配置失败", e);
            throw new RuntimeException("保存邮件配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public MailConfigDTO convertToDTO(SysMailConfig entity) {
        if (entity == null) {
            return null;
        }
        
        MailConfigDTO dto = new MailConfigDTO();
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setUsername(entity.getUsername());
        dto.setPassword(entity.getPassword());
        dto.setSenderName(entity.getSenderName());
        dto.setUseSsl(entity.getUseSsl());
        dto.setUseStarttls(entity.getUseStarttls());
        dto.setAuth(entity.getAuth());
        dto.setEnabled(entity.getEnabled());
        dto.setConnectionTimeout(entity.getConnectionTimeout());
        dto.setTimeout(entity.getTimeout());
        dto.setTrustAllCerts(entity.getTrustAllCerts());
        dto.setProtocol(entity.getProtocol());
        dto.setAuthMechanism(entity.getAuthMechanism());
        dto.setDebug(entity.getDebug());
        dto.setUseProxy(entity.getUseProxy());
        dto.setProxyHost(entity.getProxyHost());
        dto.setProxyPort(entity.getProxyPort());
        dto.setProxyUser(entity.getProxyUser());
        dto.setProxyPassword(entity.getProxyPassword());
        
        // 转换JSON字符串到Map
        if (StringUtils.hasText(entity.getCustomProperties())) {
            try {
                Map<String, String> customProps = objectMapper.readValue(
                        entity.getCustomProperties(), 
                        new TypeReference<Map<String, String>>() {}
                );
                dto.setCustomProperties(customProps);
            } catch (Exception e) {
                log.warn("解析自定义属性失败", e);
                dto.setCustomProperties(new HashMap<>());
            }
        } else {
            dto.setCustomProperties(new HashMap<>());
        }
        
        return dto;
    }
    
    @Override
    public SysMailConfig convertToEntity(MailConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        
        SysMailConfig entity = new SysMailConfig();
        
        // 生成配置名称（如果没有提供）
        String configName = dto.getUsername();
        if (!StringUtils.hasText(configName)) {
            configName = dto.getHost();
        }
        entity.setConfigName(configName);
        
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setUsername(dto.getUsername());
        entity.setPassword(dto.getPassword());
        entity.setSenderName(dto.getSenderName());
        entity.setUseSsl(dto.getUseSsl());
        entity.setUseStarttls(dto.getUseStarttls());
        entity.setAuth(dto.getAuth());
        entity.setEnabled(dto.getEnabled());
        entity.setConnectionTimeout(dto.getConnectionTimeout());
        entity.setTimeout(dto.getTimeout());
        entity.setTrustAllCerts(dto.getTrustAllCerts());
        entity.setProtocol(dto.getProtocol());
        entity.setAuthMechanism(dto.getAuthMechanism());
        entity.setDebug(dto.getDebug());
        entity.setUseProxy(dto.getUseProxy());
        entity.setProxyHost(dto.getProxyHost());
        entity.setProxyPort(dto.getProxyPort());
        entity.setProxyUser(dto.getProxyUser());
        entity.setProxyPassword(dto.getProxyPassword());
        
        // 转换Map到JSON字符串
        if (dto.getCustomProperties() != null && !dto.getCustomProperties().isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(dto.getCustomProperties());
                entity.setCustomProperties(json);
            } catch (Exception e) {
                log.warn("序列化自定义属性失败", e);
                entity.setCustomProperties("{}");
            }
        }
        
        return entity;
    }
}

