package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.dto.MailConfigDTO;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.SysMailConfigService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邮件配置Controller
 */
@Slf4j
@RestController
@RequestMapping("/webInfo")
public class SysMailConfigController {
    
    @Autowired
    private SysMailConfigService mailConfigService;
    
    @Autowired
    private MailService mailService;

    @Autowired
    private RagSyncService ragSyncService;
    
    /**
     * 获取所有邮件配置
     * GET /webInfo/getEmailConfigs
     */
    @GetMapping("/getEmailConfigs")
    public PoetryResult<List<MailConfigDTO>> getEmailConfigs() {
        try {
            List<MailConfigDTO> configs = mailConfigService.getAllConfigs();
            log.info("获取邮件配置成功，共{}个配置", configs.size());
            return PoetryResult.success(configs);
        } catch (Exception e) {
            log.error("获取邮件配置失败", e);
            return PoetryResult.fail("获取邮件配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取默认邮件配置索引
     * GET /webInfo/getDefaultMailConfig
     */
    @GetMapping("/getDefaultMailConfig")
    public PoetryResult<Integer> getDefaultMailConfig() {
        try {
            Integer defaultIndex = mailConfigService.getDefaultConfigIndex();
            log.info("获取默认邮件配置索引: {}", defaultIndex);
            return PoetryResult.success(defaultIndex);
        } catch (Exception e) {
            log.error("获取默认邮件配置索引失败", e);
            return PoetryResult.fail("获取默认邮件配置索引失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存邮件配置
     * POST /webInfo/saveEmailConfigs?defaultIndex={index}
     */
    @PostMapping("/saveEmailConfigs")
    @LoginCheck(0)
    @AuditLog(action = "MAIL_CONFIG_SAVE", targetType = "MAIL_CONFIG", summary = "保存邮件配置")
    public PoetryResult<Void> saveEmailConfigs(
            @RequestBody List<MailConfigDTO> configs,
            @RequestParam(value = "defaultIndex", required = false, defaultValue = "-1") Integer defaultIndex) {
        try {
            log.info("收到保存邮件配置请求，配置数量: {}, 默认索引: {}", 
                    configs != null ? configs.size() : 0, defaultIndex);
            
            boolean success = mailConfigService.saveConfigs(configs, defaultIndex);
            
            if (success) {
                log.info("邮件配置保存成功");
                return PoetryResult.success();
            } else {
                log.warn("邮件配置保存失败");
                return PoetryResult.fail("保存失败");
            }
        } catch (Exception e) {
            log.error("保存邮件配置失败", e);
            return PoetryResult.fail("保存失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试邮件配置
     * POST /webInfo/testEmailConfig
     */
    @PostMapping("/testEmailConfig")
    @LoginCheck(0)
    @AuditLog(action = "MAIL_CONFIG_TEST", targetType = "MAIL_CONFIG", summary = "测试邮件配置")
    public PoetryResult<Void> testEmailConfig(@RequestBody Map<String, Object> testData) {
        try {
            if (testData == null) {
                return PoetryResult.fail("请求数据不能为空");
            }
            
            // 获取测试邮箱
            String testEmail = null;
            if (testData.containsKey("testEmail")) {
                Object testEmailObj = testData.get("testEmail");
                if (testEmailObj != null) {
                    testEmail = testEmailObj.toString();
                }
            }
            
            if (testEmail == null || testEmail.trim().isEmpty()) {
                return PoetryResult.fail("测试邮箱不能为空");
            }
            
            // 获取配置对象
            Object configObj = testData.get("config");
            if (configObj == null) {
                return PoetryResult.fail("邮箱配置不能为空");
            }
            
            // 转换配置
            MailConfigDTO config = convertMapToConfig(configObj);
            
            if (config == null) {
                return PoetryResult.fail("邮箱配置格式错误");
            }
            
            // 基本验证
            if (config.getHost() == null || config.getHost().trim().isEmpty()) {
                return PoetryResult.fail("邮箱服务器地址不能为空");
            }
            if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
                return PoetryResult.fail("邮箱账号不能为空");
            }
            
            log.info("测试邮箱配置: 目标邮箱={}, 服务器={}", testEmail, config.getHost());
            boolean success = mailService.sendTestEmail(config, testEmail);
            
            if (success) {
                log.info("测试邮件发送成功");
                return PoetryResult.success();
            } else {
                log.warn("测试邮件发送失败");
                return PoetryResult.fail("测试邮件发送失败");
            }
        } catch (Exception e) {
            log.error("测试邮件发送异常", e);
            return PoetryResult.fail("测试邮件发送失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换Map到MailConfigDTO
     */
    private MailConfigDTO convertMapToConfig(Object configObj) {
        if (!(configObj instanceof Map)) {
            return null;
        }
        
        Map<String, Object> configMap = (Map<String, Object>) configObj;
        MailConfigDTO config = new MailConfigDTO();
        
        // 字符串字段
        if (configMap.containsKey("host")) config.setHost(String.valueOf(configMap.get("host")));
        if (configMap.containsKey("username")) config.setUsername(String.valueOf(configMap.get("username")));
        if (configMap.containsKey("password")) config.setPassword(String.valueOf(configMap.get("password")));
        if (configMap.containsKey("senderName")) config.setSenderName(String.valueOf(configMap.get("senderName")));
        if (configMap.containsKey("protocol")) config.setProtocol(String.valueOf(configMap.get("protocol")));
        if (configMap.containsKey("authMechanism")) config.setAuthMechanism(String.valueOf(configMap.get("authMechanism")));
        
        // 数值字段
        if (configMap.containsKey("port")) {
            try {
                Object portObj = configMap.get("port");
                config.setPort(portObj instanceof Number ? ((Number) portObj).intValue() : Integer.parseInt(portObj.toString()));
            } catch (Exception e) {
                config.setPort(25);
            }
        }
        
        if (configMap.containsKey("connectionTimeout")) {
            try {
                Object timeoutObj = configMap.get("connectionTimeout");
                config.setConnectionTimeout(timeoutObj instanceof Number ? ((Number) timeoutObj).intValue() : Integer.parseInt(timeoutObj.toString()));
            } catch (Exception ignored) {}
        }
        
        if (configMap.containsKey("timeout")) {
            try {
                Object timeoutObj = configMap.get("timeout");
                config.setTimeout(timeoutObj instanceof Number ? ((Number) timeoutObj).intValue() : Integer.parseInt(timeoutObj.toString()));
            } catch (Exception ignored) {}
        }
        
        // 布尔字段
        config.setUseSsl(getBooleanValue(configMap, "useSsl", false));
        config.setUseStarttls(getBooleanValue(configMap, "useStarttls", false));
        config.setAuth(getBooleanValue(configMap, "auth", true));
        config.setEnabled(getBooleanValue(configMap, "enabled", true));
        config.setTrustAllCerts(getBooleanValue(configMap, "trustAllCerts", false));
        config.setDebug(getBooleanValue(configMap, "debug", false));
        config.setUseProxy(getBooleanValue(configMap, "useProxy", false));
        
        return config;
    }
    
    /**
     * 从Map中获取布尔值
     */
    private Boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        if (!map.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value != null) {
            String str = value.toString().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        }
        
        return defaultValue;
    }
}

