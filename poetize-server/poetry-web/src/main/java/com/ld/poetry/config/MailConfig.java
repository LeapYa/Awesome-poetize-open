package com.ld.poetry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 邮件配置类
 */
@Configuration
public class MailConfig {

    /**
     * 创建默认的JavaMailSender
     * 这个默认实现会被application.properties中的配置覆盖
     */
    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        // 设置默认值，实际使用时会被配置文件或邮箱配置功能覆盖
        mailSender.setHost("smtp.example.com");
        mailSender.setPort(25);
        
        // 配置默认属性
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "25000");
        props.put("mail.smtp.connectiontimeout", "25000");
        
        return mailSender;
    }
} 