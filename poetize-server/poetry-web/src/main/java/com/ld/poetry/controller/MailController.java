package com.ld.poetry.controller;

import com.ld.poetry.entity.dto.MailConfigDTO;
import com.ld.poetry.service.MailService;
import com.ld.poetry.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 邮件管理控制器
 */
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/api/mail")
@Slf4j
public class MailController {
    
    @Autowired
    private MailService mailService;
    
    /**
     * 获取所有邮箱配置
     */
    @GetMapping("/getConfigs")
    public Result<List<MailConfigDTO>> getMailConfigs() {
        try {
            List<MailConfigDTO> configs = mailService.getMailConfigs();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取邮箱配置失败", e);
            return Result.fail("获取邮箱配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取默认邮箱配置索引
     */
    @GetMapping("/getDefaultConfig")
    public Result<Integer> getDefaultMailConfig() {
        try {
            int defaultIndex = mailService.getDefaultMailConfigIndex();
            return Result.success(defaultIndex);
        } catch (Exception e) {
            log.error("获取默认邮箱配置失败", e);
            return Result.fail("获取默认邮箱配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存邮箱配置
     */
    @PostMapping("/saveConfigs")
    public Result<Void> saveMailConfigs(@RequestBody List<MailConfigDTO> configs, 
                                       @RequestParam(defaultValue = "-1") int defaultIndex) {
        try {
            log.info("保存邮箱配置: 配置数量: {}, 默认索引: {}", configs.size(), defaultIndex);
            boolean success = mailService.saveMailConfigs(configs, defaultIndex);
            
            if (success) {
                return Result.success();
            } else {
                return Result.fail("保存邮箱配置失败");
            }
        } catch (Exception e) {
            log.error("保存邮箱配置失败", e);
            return Result.fail("保存邮箱配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 同步邮箱配置（供Python服务调用）
     */
    @PostMapping("/syncConfig")
    public Result<Void> syncMailConfig(@RequestBody Map<String, Object> syncData) {
        try {
            if (syncData == null) {
                return Result.fail("请求数据不能为空");
            }
            
            // 获取默认索引
            Integer defaultIndex = null;
            if (syncData.containsKey("defaultIndex")) {
                Object indexObj = syncData.get("defaultIndex");
                if (indexObj instanceof Number) {
                    defaultIndex = ((Number) indexObj).intValue();
                } else if (indexObj != null) {
                    try {
                        defaultIndex = Integer.parseInt(indexObj.toString());
                    } catch (NumberFormatException e) {
                        log.warn("默认索引格式错误: {}", indexObj);
                        defaultIndex = -1;
                    }
                }
            }
            
            if (defaultIndex == null) {
                defaultIndex = -1;
            }
            
            // 获取配置列表
            List<MailConfigDTO> configs = new ArrayList<>();
            if (syncData.containsKey("configs")) {
                Object configsObj = syncData.get("configs");
                if (configsObj instanceof List) {
                    List<?> configList = (List<?>) configsObj;
                    
                    for (Object configItem : configList) {
                        if (configItem instanceof Map) {
                            Map<String, Object> configMap = (Map<String, Object>) configItem;
                            MailConfigDTO config = new MailConfigDTO();
                            
                            // 处理字符串字段
                            if (configMap.containsKey("host")) {
                                config.setHost(String.valueOf(configMap.get("host")));
                            }
                            if (configMap.containsKey("username")) {
                                config.setUsername(String.valueOf(configMap.get("username")));
                            }
                            if (configMap.containsKey("password")) {
                                config.setPassword(String.valueOf(configMap.get("password")));
                            }
                            if (configMap.containsKey("senderName")) {
                                config.setSenderName(String.valueOf(configMap.get("senderName")));
                            }
                            if (configMap.containsKey("jndiName")) {
                                config.setJndiName(String.valueOf(configMap.get("jndiName")));
                            }
                            
                            // 处理数值字段
                            if (configMap.containsKey("port")) {
                                try {
                                    Object portObj = configMap.get("port");
                                    if (portObj instanceof Number) {
                                        config.setPort(((Number) portObj).intValue());
                                    } else if (portObj != null) {
                                        config.setPort(Integer.parseInt(portObj.toString()));
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("端口格式错误: {}", configMap.get("port"));
                                    config.setPort(25); // 默认端口
                                }
                            }
                            
                            if (configMap.containsKey("connectionTimeout")) {
                                try {
                                    Object timeoutObj = configMap.get("connectionTimeout");
                                    if (timeoutObj instanceof Number) {
                                        config.setConnectionTimeout(((Number) timeoutObj).intValue());
                                    } else if (timeoutObj != null) {
                                        config.setConnectionTimeout(Integer.parseInt(timeoutObj.toString()));
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("连接超时格式错误: {}", configMap.get("connectionTimeout"));
                                }
                            }
                            
                            if (configMap.containsKey("timeout")) {
                                try {
                                    Object timeoutObj = configMap.get("timeout");
                                    if (timeoutObj instanceof Number) {
                                        config.setTimeout(((Number) timeoutObj).intValue());
                                    } else if (timeoutObj != null) {
                                        config.setTimeout(Integer.parseInt(timeoutObj.toString()));
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("读取超时格式错误: {}", configMap.get("timeout"));
                                }
                            }
                            
                            // 处理布尔字段
                            if (configMap.containsKey("ssl")) {
                                Object sslObj = configMap.get("ssl");
                                if (sslObj instanceof Boolean) {
                                    config.setSsl((Boolean) sslObj);
                                } else if (sslObj != null) {
                                    String sslStr = sslObj.toString().toLowerCase();
                                    config.setSsl("true".equals(sslStr) || "1".equals(sslStr) || "yes".equals(sslStr));
                                }
                            }
                            
                            if (configMap.containsKey("starttls")) {
                                Object starttlsObj = configMap.get("starttls");
                                if (starttlsObj instanceof Boolean) {
                                    config.setStarttls((Boolean) starttlsObj);
                                } else if (starttlsObj != null) {
                                    String starttlsStr = starttlsObj.toString().toLowerCase();
                                    config.setStarttls("true".equals(starttlsStr) || "1".equals(starttlsStr) || "yes".equals(starttlsStr));
                                }
                            }
                            
                            if (configMap.containsKey("auth")) {
                                Object authObj = configMap.get("auth");
                                if (authObj instanceof Boolean) {
                                    config.setAuth((Boolean) authObj);
                                } else if (authObj != null) {
                                    String authStr = authObj.toString().toLowerCase();
                                    config.setAuth("true".equals(authStr) || "1".equals(authStr) || "yes".equals(authStr));
                                }
                            }
                            
                            if (configMap.containsKey("enabled")) {
                                Object enabledObj = configMap.get("enabled");
                                if (enabledObj instanceof Boolean) {
                                    config.setEnabled((Boolean) enabledObj);
                                } else if (enabledObj != null) {
                                    String enabledStr = enabledObj.toString().toLowerCase();
                                    config.setEnabled("true".equals(enabledStr) || "1".equals(enabledStr) || "yes".equals(enabledStr));
                                }
                            }
                            
                            if (configMap.containsKey("trustAllCerts")) {
                                Object trustObj = configMap.get("trustAllCerts");
                                if (trustObj instanceof Boolean) {
                                    config.setTrustAllCerts((Boolean) trustObj);
                                } else if (trustObj != null) {
                                    String trustStr = trustObj.toString().toLowerCase();
                                    config.setTrustAllCerts("true".equals(trustStr) || "1".equals(trustStr) || "yes".equals(trustStr));
                                }
                            }
                            
                            configs.add(config);
                        }
                    }
                }
            }
            
            log.info("同步邮箱配置: 配置数量: {}, 默认索引: {}", configs.size(), defaultIndex);
            boolean success = mailService.saveMailConfigs(configs, defaultIndex);
            
            if (success) {
                return Result.success();
            } else {
                return Result.fail("同步邮箱配置失败");
            }
        } catch (Exception e) {
            log.error("同步邮箱配置失败", e);
            return Result.fail("同步邮箱配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试邮箱配置
     */
    @PostMapping("/test")
    public Result<Void> testMailConfig(@RequestBody Map<String, Object> testData) {
        try {
            if (testData == null) {
                return Result.fail("请求数据不能为空");
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
                return Result.fail("测试邮箱不能为空");
            }
            
            // 获取配置对象
            Object configObj = testData.get("config");
            if (configObj == null) {
                return Result.fail("邮箱配置不能为空");
            }
            
            // 创建一个新的配置对象，手动填充所有字段
            MailConfigDTO config = new MailConfigDTO();
            
            // 处理配置对象转换
            if (configObj instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) configObj;
                
                // 处理字符串字段
                if (configMap.containsKey("host")) {
                    config.setHost(String.valueOf(configMap.get("host")));
                }
                if (configMap.containsKey("username")) {
                    config.setUsername(String.valueOf(configMap.get("username")));
                }
                if (configMap.containsKey("password")) {
                    config.setPassword(String.valueOf(configMap.get("password")));
                }
                if (configMap.containsKey("senderName")) {
                    config.setSenderName(String.valueOf(configMap.get("senderName")));
                }
                if (configMap.containsKey("jndiName")) {
                    config.setJndiName(String.valueOf(configMap.get("jndiName")));
                }
                
                // 处理数值字段
                if (configMap.containsKey("port")) {
                    try {
                        Object portObj = configMap.get("port");
                        if (portObj instanceof Number) {
                            config.setPort(((Number) portObj).intValue());
                        } else if (portObj != null) {
                            config.setPort(Integer.parseInt(portObj.toString()));
                        }
                    } catch (NumberFormatException e) {
                        log.warn("端口格式错误: {}", configMap.get("port"));
                        config.setPort(25); // 默认端口
                    }
                }
                
                if (configMap.containsKey("connectionTimeout")) {
                    try {
                        Object timeoutObj = configMap.get("connectionTimeout");
                        if (timeoutObj instanceof Number) {
                            config.setConnectionTimeout(((Number) timeoutObj).intValue());
                        } else if (timeoutObj != null) {
                            config.setConnectionTimeout(Integer.parseInt(timeoutObj.toString()));
                        }
                    } catch (NumberFormatException e) {
                        log.warn("连接超时格式错误: {}", configMap.get("connectionTimeout"));
                    }
                }
                
                if (configMap.containsKey("timeout")) {
                    try {
                        Object timeoutObj = configMap.get("timeout");
                        if (timeoutObj instanceof Number) {
                            config.setTimeout(((Number) timeoutObj).intValue());
                        } else if (timeoutObj != null) {
                            config.setTimeout(Integer.parseInt(timeoutObj.toString()));
                        }
                    } catch (NumberFormatException e) {
                        log.warn("读取超时格式错误: {}", configMap.get("timeout"));
                    }
                }
                
                // 处理布尔字段
                if (configMap.containsKey("ssl")) {
                    Object sslObj = configMap.get("ssl");
                    if (sslObj instanceof Boolean) {
                        config.setSsl((Boolean) sslObj);
                    } else if (sslObj != null) {
                        String sslStr = sslObj.toString().toLowerCase();
                        config.setSsl("true".equals(sslStr) || "1".equals(sslStr) || "yes".equals(sslStr));
                    }
                }
                
                if (configMap.containsKey("starttls")) {
                    Object starttlsObj = configMap.get("starttls");
                    if (starttlsObj instanceof Boolean) {
                        config.setStarttls((Boolean) starttlsObj);
                    } else if (starttlsObj != null) {
                        String starttlsStr = starttlsObj.toString().toLowerCase();
                        config.setStarttls("true".equals(starttlsStr) || "1".equals(starttlsStr) || "yes".equals(starttlsStr));
                    }
                }
                
                if (configMap.containsKey("auth")) {
                    Object authObj = configMap.get("auth");
                    if (authObj instanceof Boolean) {
                        config.setAuth((Boolean) authObj);
                    } else if (authObj != null) {
                        String authStr = authObj.toString().toLowerCase();
                        config.setAuth("true".equals(authStr) || "1".equals(authStr) || "yes".equals(authStr));
                    }
                }
                
                if (configMap.containsKey("enabled")) {
                    Object enabledObj = configMap.get("enabled");
                    if (enabledObj instanceof Boolean) {
                        config.setEnabled((Boolean) enabledObj);
                    } else if (enabledObj != null) {
                        String enabledStr = enabledObj.toString().toLowerCase();
                        config.setEnabled("true".equals(enabledStr) || "1".equals(enabledStr) || "yes".equals(enabledStr));
                    }
                }
                
                if (configMap.containsKey("trustAllCerts")) {
                    Object trustObj = configMap.get("trustAllCerts");
                    if (trustObj instanceof Boolean) {
                        config.setTrustAllCerts((Boolean) trustObj);
                    } else if (trustObj != null) {
                        String trustStr = trustObj.toString().toLowerCase();
                        config.setTrustAllCerts("true".equals(trustStr) || "1".equals(trustStr) || "yes".equals(trustStr));
                    }
                }
            } else {
                return Result.fail("邮箱配置格式错误");
            }
            
            // 基本验证
            if (config.getHost() == null || config.getHost().trim().isEmpty()) {
                return Result.fail("邮箱服务器地址不能为空");
            }
            if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
                return Result.fail("邮箱账号不能为空");
            }
            
            log.info("测试邮箱配置: 目标邮箱: {}, 配置: {}", testEmail, config);
            boolean success = mailService.sendTestEmail(config, testEmail);
            
            if (success) {
                return Result.success();
            } else {
                return Result.fail("测试邮件发送失败");
            }
        } catch (Exception e) {
            log.error("测试邮件发送失败", e);
            return Result.fail("测试邮件发送失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送验证码邮件
     */
    @PostMapping("/sendVerificationCode")
    public Result<Void> sendVerificationCode(@RequestBody Map<String, Object> codeData) {
        try {
            String email = (String) codeData.get("email");
            String code = (String) codeData.get("code");
            
            log.info("发送验证码邮件: 目标邮箱: {}", email);
            boolean success = mailService.sendVerificationCode(email, code);
            
            if (success) {
                return Result.success();
            } else {
                return Result.fail("验证码邮件发送失败");
            }
        } catch (Exception e) {
            log.error("验证码邮件发送失败", e);
            return Result.fail("验证码邮件发送失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送普通邮件
     */
    @PostMapping("/send")
    public Result<Void> sendMail(@RequestBody Map<String, Object> mailData) {
        try {
            if (mailData == null) {
                return Result.fail("请求数据不能为空");
            }
            
            // 获取收件人列表
            List<String> to = null;
            if (mailData.containsKey("to")) {
                Object toObj = mailData.get("to");
                if (toObj instanceof List) {
                    to = new ArrayList<>();
                    for (Object item : (List<?>) toObj) {
                        if (item != null) {
                            to.add(item.toString());
                        }
                    }
                }
            }
            
            if (to == null || to.isEmpty()) {
                return Result.fail("收件人不能为空");
            }
            
            // 获取主题和内容
            String subject = mailData.containsKey("subject") ? String.valueOf(mailData.get("subject")) : "";
            String content = mailData.containsKey("content") ? String.valueOf(mailData.get("content")) : "";
            
            // 获取是否为HTML
            Boolean html = false;
            if (mailData.containsKey("html")) {
                Object htmlObj = mailData.get("html");
                if (htmlObj instanceof Boolean) {
                    html = (Boolean) htmlObj;
                } else if (htmlObj != null) {
                    String htmlStr = htmlObj.toString().toLowerCase();
                    html = "true".equals(htmlStr) || "1".equals(htmlStr) || "yes".equals(htmlStr);
                }
            }
            
            // 处理配置对象
            MailConfigDTO config = null;
            Object configObj = mailData.get("config");
            
            if (configObj != null) {
                config = new MailConfigDTO();
                
                if (configObj instanceof Map) {
                    Map<String, Object> configMap = (Map<String, Object>) configObj;
                    
                    // 处理字符串字段
                    if (configMap.containsKey("host")) {
                        config.setHost(String.valueOf(configMap.get("host")));
                    }
                    if (configMap.containsKey("username")) {
                        config.setUsername(String.valueOf(configMap.get("username")));
                    }
                    if (configMap.containsKey("password")) {
                        config.setPassword(String.valueOf(configMap.get("password")));
                    }
                    if (configMap.containsKey("senderName")) {
                        config.setSenderName(String.valueOf(configMap.get("senderName")));
                    }
                    if (configMap.containsKey("jndiName")) {
                        config.setJndiName(String.valueOf(configMap.get("jndiName")));
                    }
                    
                    // 处理数值字段
                    if (configMap.containsKey("port")) {
                        try {
                            Object portObj = configMap.get("port");
                            if (portObj instanceof Number) {
                                config.setPort(((Number) portObj).intValue());
                            } else if (portObj != null) {
                                config.setPort(Integer.parseInt(portObj.toString()));
                            }
                        } catch (NumberFormatException e) {
                            log.warn("端口格式错误: {}", configMap.get("port"));
                            config.setPort(25); // 默认端口
                        }
                    }
                    
                    if (configMap.containsKey("connectionTimeout")) {
                        try {
                            Object timeoutObj = configMap.get("connectionTimeout");
                            if (timeoutObj instanceof Number) {
                                config.setConnectionTimeout(((Number) timeoutObj).intValue());
                            } else if (timeoutObj != null) {
                                config.setConnectionTimeout(Integer.parseInt(timeoutObj.toString()));
                            }
                        } catch (NumberFormatException e) {
                            log.warn("连接超时格式错误: {}", configMap.get("connectionTimeout"));
                        }
                    }
                    
                    if (configMap.containsKey("timeout")) {
                        try {
                            Object timeoutObj = configMap.get("timeout");
                            if (timeoutObj instanceof Number) {
                                config.setTimeout(((Number) timeoutObj).intValue());
                            } else if (timeoutObj != null) {
                                config.setTimeout(Integer.parseInt(timeoutObj.toString()));
                            }
                        } catch (NumberFormatException e) {
                            log.warn("读取超时格式错误: {}", configMap.get("timeout"));
                        }
                    }
                    
                    // 处理布尔字段
                    if (configMap.containsKey("ssl")) {
                        Object sslObj = configMap.get("ssl");
                        if (sslObj instanceof Boolean) {
                            config.setSsl((Boolean) sslObj);
                        } else if (sslObj != null) {
                            String sslStr = sslObj.toString().toLowerCase();
                            config.setSsl("true".equals(sslStr) || "1".equals(sslStr) || "yes".equals(sslStr));
                        }
                    }
                    
                    if (configMap.containsKey("starttls")) {
                        Object starttlsObj = configMap.get("starttls");
                        if (starttlsObj instanceof Boolean) {
                            config.setStarttls((Boolean) starttlsObj);
                        } else if (starttlsObj != null) {
                            String starttlsStr = starttlsObj.toString().toLowerCase();
                            config.setStarttls("true".equals(starttlsStr) || "1".equals(starttlsStr) || "yes".equals(starttlsStr));
                        }
                    }
                    
                    if (configMap.containsKey("auth")) {
                        Object authObj = configMap.get("auth");
                        if (authObj instanceof Boolean) {
                            config.setAuth((Boolean) authObj);
                        } else if (authObj != null) {
                            String authStr = authObj.toString().toLowerCase();
                            config.setAuth("true".equals(authStr) || "1".equals(authStr) || "yes".equals(authStr));
                        }
                    }
                    
                    if (configMap.containsKey("enabled")) {
                        Object enabledObj = configMap.get("enabled");
                        if (enabledObj instanceof Boolean) {
                            config.setEnabled((Boolean) enabledObj);
                        } else if (enabledObj != null) {
                            String enabledStr = enabledObj.toString().toLowerCase();
                            config.setEnabled("true".equals(enabledStr) || "1".equals(enabledStr) || "yes".equals(enabledStr));
                        }
                    }
                    
                    if (configMap.containsKey("trustAllCerts")) {
                        Object trustObj = configMap.get("trustAllCerts");
                        if (trustObj instanceof Boolean) {
                            config.setTrustAllCerts((Boolean) trustObj);
                        } else if (trustObj != null) {
                            String trustStr = trustObj.toString().toLowerCase();
                            config.setTrustAllCerts("true".equals(trustStr) || "1".equals(trustStr) || "yes".equals(trustStr));
                        }
                    }
                }
            }
            
            log.info("发送普通邮件: 收件人: {}, 主题: {}", to, subject);
            boolean success = mailService.sendMail(to, subject, content, html, config);
            
            if (success) {
                return Result.success();
            } else {
                return Result.fail("邮件发送失败");
            }
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            return Result.fail("邮件发送失败: " + e.getMessage());
        }
    }
} 