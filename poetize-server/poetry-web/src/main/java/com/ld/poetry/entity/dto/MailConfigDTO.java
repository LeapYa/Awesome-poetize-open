package com.ld.poetry.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 邮箱配置信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailConfigDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 邮箱服务器地址
     */
    private String host;
    
    /**
     * 邮箱服务器端口
     */
    private Integer port;
    
    /**
     * 邮箱账号
     */
    private String username;
    
    /**
     * 邮箱密码或授权码
     */
    private String password;
    
    /**
     * 发件人名称
     */
    private String senderName;
    
    /**
     * 是否启用SSL
     */
    private Boolean ssl;
    
    /**
     * 是否启用STARTTLS
     */
    private Boolean starttls;
    
    /**
     * 是否需要认证
     */
    private Boolean auth;
    
    /**
     * 是否已启用
     */
    private Boolean enabled;
    
    /**
     * 连接超时(毫秒)
     */
    private Integer connectionTimeout;
    
    /**
     * 读取超时(毫秒)
     */
    private Integer timeout;
    
    /**
     * JNDI名称
     */
    private String jndiName;
    
    /**
     * 是否信任所有证书
     */
    private Boolean trustAllCerts;
    
    /**
     * 协议(smtp, smtps等)
     */
    private String protocol;
    
    /**
     * 认证机制(默认、LOGIN、PLAIN、DIGEST-MD5等)
     */
    private String authMechanism;
    
    /**
     * 是否启用调试模式
     */
    private Boolean debug;
    
    /**
     * 是否使用代理
     */
    private Boolean useProxy;
    
    /**
     * 代理服务器地址
     */
    private String proxyHost;
    
    /**
     * 代理服务器端口
     */
    private Integer proxyPort;
    
    /**
     * 代理服务器用户名
     */
    private String proxyUser;
    
    /**
     * 代理服务器密码
     */
    private String proxyPassword;
    
    /**
     * 自定义属性
     */
    private java.util.Map<String, String> customProperties;
} 