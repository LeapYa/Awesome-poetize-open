package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.SysCaptchaConfigService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 验证码配置Controller
 */
@Slf4j
@RestController
@RequestMapping("/webInfo")
public class SysCaptchaConfigController {
    
    @Autowired
    private SysCaptchaConfigService captchaConfigService;

    @Autowired
    private RagSyncService ragSyncService;
    
    /**
     * 获取验证码配置
     * GET /webInfo/getCaptchaConfig
     */
    @GetMapping("/getCaptchaConfig")
    public PoetryResult<Map<String, Object>> getCaptchaConfig() {
        try {
            Map<String, Object> config = captchaConfigService.getCaptchaConfig();
            log.info("获取验证码配置成功");
            return PoetryResult.success(config);
        } catch (Exception e) {
            log.error("获取验证码配置失败", e);
            return PoetryResult.fail("获取验证码配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存验证码配置
     * POST /webInfo/updateCaptchaConfig
     */
    @PostMapping("/updateCaptchaConfig")
    @LoginCheck(0)
    @AuditLog(action = "CAPTCHA_CONFIG_UPDATE", targetType = "WEB_INFO", summary = "更新验证码配置")
    public PoetryResult<Void> updateCaptchaConfig(@RequestBody Map<String, Object> config) {
        try {
            log.info("收到保存验证码配置请求");
            boolean success = captchaConfigService.saveCaptchaConfig(config);
            
            if (success) {
                log.info("验证码配置保存成功");
                return PoetryResult.success();
            } else {
                log.warn("验证码配置保存失败");
                return PoetryResult.fail("保存失败");
            }
        } catch (Exception e) {
            log.error("保存验证码配置失败", e);
            return PoetryResult.fail("保存失败: " + e.getMessage());
        }
    }
}

