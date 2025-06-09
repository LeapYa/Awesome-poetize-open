package com.ld.poetry.service;

import com.ld.poetry.entity.dto.MailConfigDTO;

import java.util.List;

/**
 * 邮件服务接口
 */
public interface MailService {
    
    /**
     * 获取所有邮箱配置
     * @return 邮箱配置列表
     */
    List<MailConfigDTO> getMailConfigs();
    
    /**
     * 保存邮箱配置
     * @param configs 邮箱配置列表
     * @param defaultIndex 默认邮箱索引
     * @return 是否保存成功
     */
    boolean saveMailConfigs(List<MailConfigDTO> configs, int defaultIndex);
    
    /**
     * 获取默认邮箱配置索引
     * @return 默认邮箱索引
     */
    int getDefaultMailConfigIndex();
    
    /**
     * 随机获取一个启用的邮箱配置
     * @return 邮箱配置
     */
    MailConfigDTO getRandomMailConfig();
    
    /**
     * 获取默认邮箱配置
     * @return 默认邮箱配置
     */
    MailConfigDTO getDefaultMailConfig();
    
    /**
     * 发送测试邮件
     * @param config 邮箱配置
     * @param testEmail 测试邮箱地址
     * @return 是否发送成功
     */
    boolean sendTestEmail(MailConfigDTO config, String testEmail);
    
    /**
     * 发送验证码邮件
     * @param email 目标邮箱
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String email, String code);
    
    /**
     * 发送普通邮件
     * @param to 收件人列表
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param html 是否HTML格式
     * @param config 邮箱配置(null表示使用默认配置)
     * @return 是否发送成功
     */
    boolean sendMail(List<String> to, String subject, String content, boolean html, MailConfigDTO config);
} 