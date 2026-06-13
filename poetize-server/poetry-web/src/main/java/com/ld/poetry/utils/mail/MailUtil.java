package com.ld.poetry.utils.mail;

import com.ld.poetry.utils.JsonUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ResourcePathMapper;
import com.ld.poetry.entity.ResourcePath;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.MailService;
import com.ld.poetry.utils.AsyncTaskUtil;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.config.AsyncUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class MailUtil {

    /**
     * 1. 来源人名
     * 2. 来源内容
     */
    public static final String originalText = "<hr style=\"border: 1px dashed #ef859d2e;margin: 20px 0\">\n" +
            "            <div>\n" +
            "                <div style=\"font-size: 18px;font-weight: bold;color: #C5343E\">\n" +
            "                    %s\n" +
            "                </div>\n" +
            "                <div style=\"margin-top: 6px;font-size: 16px;color: #000000\">\n" +
            "                    <p>\n" +
            "                        %s\n" +
            "                    </p>\n" +
            "                </div>\n" +
            "            </div>";

    /**
     * 发件人
     */
    public static final String replyMail = "你之前的评论收到来自 %s 的回复";
    public static final String commentMail = "你的文章 %s 收到来自 %s 的评论";
    public static final String messageMail = "你收到来自 %s 的留言";
    public static final String loveMail = "你收到来自 %s 的祝福";
    public static final String imMail = "你收到来自 %s 的消息";
    public static final String notificationMail = "你收到来自 %s 的订阅";

    @Autowired
    private MailService mailService;

    @Autowired
    private com.ld.poetry.dao.WebInfoMapper webInfoMapper;

    private String mailText;

    /**
     * 检查邮箱配置是否存在
     * @return 是否存在有效的邮箱配置
     */
    public boolean isEmailConfigured() {
        try {
            return mailService != null && mailService.getDefaultMailConfig() != null;
        } catch (Exception e) {
            log.error("检查邮箱配置出错", e);
            return false;
        }
    }

    @PostConstruct
    public void init() {
        // 邮件模板，URL将在发送时动态替换
        this.mailText = "<div style=\"font-family: serif;line-height: 22px;padding: 30px\">\n" +
                "    <div style=\"display: flex;flex-direction: column;align-items: center\">\n" +
                "        <div style=\"margin: 10px auto 20px;text-align: center\">\n" +
                "            <div style=\"line-height: 32px;font-size: 26px;font-weight: bold;color: #000000\">\n" +
                "                您在 %s 中收到一条新消息。\n" +
                "            </div>\n" +
                "            <div style=\"font-size: 16px;font-weight: bold;color: rgba(0, 0, 0, 0.19);margin-top: 21px\">\n" +
                "                %s\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div style=\"min-width: 250px;max-width: 800px;min-height: 128px;background: #F7F7F7;border-radius: 10px;padding: 32px\">\n" +
                "            <div>\n" +
                "                <div style=\"font-size: 18px;font-weight: bold;color: #C5343E\">\n" +
                "                    %s\n" +
                "                </div>\n" +
                "                <div style=\"margin-top: 6px;font-size: 16px;color: #000000\">\n" +
                "                    <p>\n" +
                "                        %s\n" +
                "                    </p>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            %s\n" +
                "            <a style=\"width: 150px;height: 38px;background: #ef859d38;border-radius: 32px;display: flex;align-items: center;justify-content: center;text-decoration: none;margin: 40px auto 0\"\n" +
                "               href=\"{SITE_URL}\" target=\"_blank\">\n" +
                "                <span style=\"color: #DB214B\">有朋自远方来</span>\n" +
                "            </a>\n" +
                "        </div>\n" +
                "        <div style=\"margin-top: 20px;font-size: 12px;color: #00000045\">\n" +
                "            此邮件由 %s 自动发出，直接回复无效（一天最多发送 " + CommonConst.COMMENT_IM_MAIL_COUNT + " 条通知邮件和 " + CommonConst.CODE_MAIL_COUNT + " 条验证码邮件），退订请联系站长。\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</div>";
    }

    /**
     * 获取网站URL
     * 优先级：web_info.site_address > 环境变量SITE_URL > 默认值
     * 
     * @return 网站URL（不带尾部斜杠）
     */
    public String getSiteUrl() {
        try {
            // 1. 优先从 web_info 表获取 site_address 字段
            com.ld.poetry.entity.WebInfo webInfo = webInfoMapper.selectById(1);
            if (webInfo != null && StringUtils.hasText(webInfo.getSiteAddress())) {
                String url = webInfo.getSiteAddress().trim();
                // 确保URL以http://或https://开头
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                // 移除末尾的斜杠
                if (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                return url;
            }
        } catch (Exception e) {
            log.warn("从 web_info 表获取网站URL失败，尝试从环境变量获取: {}", e.getMessage());
        }
        
        // 2. 如果数据库中未配置或获取失败，尝试从环境变量获取
        String envSiteUrl = System.getenv("SITE_URL");
        if (StringUtils.hasText(envSiteUrl)) {
            String url = envSiteUrl.trim();
            // 确保URL以http://或https://开头
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            // 移除末尾的斜杠
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
        
        // 3. 如果环境变量也未配置，返回默认URL
        return "http://localhost";
    }

    /**
     * 获取邮件模板，动态替换网站URL
     * @return 包含动态URL的邮件模板
     */
    public String getMailText() {
        // 每次获取邮件模板时都动态获取最新的网站URL
        String siteUrl = getSiteUrl();
        return mailText.replace("{SITE_URL}", siteUrl);
    }

    /**
     * 异步发送邮件
     */
    @org.springframework.scheduling.annotation.Async
    public void sendMailMessage(List<String> to, String subject, String text) {
        // 获取当前用户信息（用于日志记录）
        String username = AsyncTaskUtil.getCurrentUsername();
        Integer userId = AsyncTaskUtil.getCurrentUserId();
        User currentUser = AsyncTaskUtil.getCurrentUser();
        
        if (userId == null && currentUser == null) {
            // 尝试从请求上下文中获取用户信息（作为备份方案）
            try {
                currentUser = PoetryUtil.getCurrentUser();
                if (currentUser != null) {
                    username = currentUser.getUsername();
                    userId = currentUser.getId();
                }
            } catch (Exception e) {
            }
        }
        
        // 记录异步任务开始
        AsyncTaskUtil.logUserOperation("邮件发送", String.format("收件人: %s, 主题: %s", 
                JsonUtils.toJsonString(to), subject));
        
        log.info("异步邮件发送开始 - 用户: {}{}, 收件人: {}, 主题: {}", 
                username, 
                userId != null ? " (ID: " + userId + ")" : "",
                JsonUtils.toJsonString(to), subject);
        
        try {
            // 验证用户上下文（邮件发送通常不强制要求用户上下文）
            if (AsyncTaskUtil.hasUserContext()) {
            } else if (currentUser != null) {
                // 如果异步上下文中没有，但我们已经获取到用户信息，则手动设置
                AsyncUserContext.setUser(currentUser);
                if (StringUtils.hasText(PoetryUtil.getToken())) {
                    AsyncUserContext.setToken(PoetryUtil.getToken());
                }
            } else {
            }
            
            // 使用MailService发送邮件，按顺序尝试所有可用的邮箱配置
            boolean success = mailService.sendMailWithSequentialRetry(to, subject, text, true);
            
            if (success) {
                AsyncTaskUtil.logUserOperation("邮件发送成功", String.format("收件人: %s", JsonUtils.toJsonString(to)));
                log.info("异步邮件发送成功 - 用户: {}, 收件人: {}", 
                        AsyncTaskUtil.getCurrentUsername(), JsonUtils.toJsonString(to));
            } else {
                AsyncTaskUtil.logUserOperation("邮件发送失败", "邮件服务返回失败");
                log.error("异步邮件发送失败 - 用户: {}, 收件人: {}, 原因: 邮件服务返回失败", 
                        AsyncTaskUtil.getCurrentUsername(), JsonUtils.toJsonString(to));
            }
        } catch (Exception e) {
            AsyncTaskUtil.logUserOperation("邮件发送异常", e.getMessage());
            log.error("异步邮件发送异常 - 用户: {}, 收件人: {}, 异常: {}", 
                    AsyncTaskUtil.getCurrentUsername(), JsonUtils.toJsonString(to), e.getMessage(), e);
        } finally {
            // 记录任务执行时间
            AsyncTaskUtil.logTaskExecutionTime("邮件发送");
        }
    }
}
