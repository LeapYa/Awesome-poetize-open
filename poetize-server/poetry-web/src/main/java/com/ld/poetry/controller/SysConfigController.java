package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysConfig;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 参数配置表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2024-03-23
 */
@RestController
@RequestMapping("/sysConfig")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    /**
     * 查询系统参数
     */
    @GetMapping("/listSysConfig")
    public PoetryResult<Map<String, String>> listSysConfig() {
        LambdaQueryChainWrapper<SysConfig> wrapper = new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper());
        List<SysConfig> sysConfigs = wrapper.eq(SysConfig::getConfigType, Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()))
                .list();
        Map<String, String> collect = sysConfigs.stream().collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        return PoetryResult.success(collect);
    }

    /**
     * 保存或更新
     */
    @PostMapping("/saveOrUpdateConfig")
    @LoginCheck(0)
    public PoetryResult saveConfig(@RequestBody SysConfig sysConfig) {
        if (!StringUtils.hasText(sysConfig.getConfigName()) ||
                !StringUtils.hasText(sysConfig.getConfigKey()) ||
                !StringUtils.hasText(sysConfig.getConfigType())) {
            return PoetryResult.fail("请完善所有配置信息！");
        }
        String configType = sysConfig.getConfigType();
        if (!Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()).equals(configType) &&
                !Integer.toString(PoetryEnum.SYS_CONFIG_PRIVATE.getCode()).equals(configType)) {
            return PoetryResult.fail("配置类型不正确！");
        }
        sysConfigService.saveOrUpdate(sysConfig);
        return PoetryResult.success();
    }

    /**
     * 删除
     */
    @GetMapping("/deleteConfig")
    @LoginCheck(0)
    public PoetryResult deleteConfig(@RequestParam("id") Integer id) {
        sysConfigService.removeById(id);
        return PoetryResult.success();
    }

    /**
     * 查询
     */
    @GetMapping("/listConfig")
    @LoginCheck(0)
    public PoetryResult<List<SysConfig>> listConfig() {
        return PoetryResult.success(new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper()).list());
    }

    /**
     * 获取第三方登录配置
     */
    @GetMapping("/getThirdLoginConfig")
    public PoetryResult<Map<String, Map<String, String>>> getThirdLoginConfig() {
        // 检查第三方登录是否启用
        String isEnabled = sysConfigService.getConfigValueByKey("third.login.enable");
        if (!"true".equals(isEnabled)) {
            return PoetryResult.fail("第三方登录功能未启用");
        }

        // 获取所有第三方登录相关配置
        Map<String, Map<String, String>> configs = new java.util.HashMap<>();
        
        // GitHub配置
        Map<String, String> githubConfig = new java.util.HashMap<>();
        githubConfig.put("client_id", sysConfigService.getConfigValueByKey("github.client_id"));
        githubConfig.put("client_secret", sysConfigService.getConfigValueByKey("github.client_secret"));
        githubConfig.put("redirect_uri", sysConfigService.getConfigValueByKey("github.redirect_uri"));
        configs.put("github", githubConfig);
        
        // Google配置
        Map<String, String> googleConfig = new java.util.HashMap<>();
        googleConfig.put("client_id", sysConfigService.getConfigValueByKey("google.client_id"));
        googleConfig.put("client_secret", sysConfigService.getConfigValueByKey("google.client_secret"));
        googleConfig.put("redirect_uri", sysConfigService.getConfigValueByKey("google.redirect_uri"));
        configs.put("google", googleConfig);
        
        // Twitter配置
        Map<String, String> twitterConfig = new java.util.HashMap<>();
        twitterConfig.put("client_key", sysConfigService.getConfigValueByKey("twitter.client_key"));
        twitterConfig.put("client_secret", sysConfigService.getConfigValueByKey("twitter.client_secret"));
        twitterConfig.put("redirect_uri", sysConfigService.getConfigValueByKey("twitter.redirect_uri"));
        configs.put("twitter", twitterConfig);
        
        // Yandex配置
        Map<String, String> yandexConfig = new java.util.HashMap<>();
        yandexConfig.put("client_id", sysConfigService.getConfigValueByKey("yandex.client_id"));
        yandexConfig.put("client_secret", sysConfigService.getConfigValueByKey("yandex.client_secret"));
        yandexConfig.put("redirect_uri", sysConfigService.getConfigValueByKey("yandex.redirect_uri"));
        configs.put("yandex", yandexConfig);
        
        // Gitee配置
        Map<String, String> giteeConfig = new java.util.HashMap<>();
        giteeConfig.put("client_id", sysConfigService.getConfigValueByKey("gitee.client_id"));
        giteeConfig.put("client_secret", sysConfigService.getConfigValueByKey("gitee.client_secret"));
        giteeConfig.put("redirect_uri", sysConfigService.getConfigValueByKey("gitee.redirect_uri"));
        configs.put("gitee", giteeConfig);

        return PoetryResult.success(configs);
    }
}
