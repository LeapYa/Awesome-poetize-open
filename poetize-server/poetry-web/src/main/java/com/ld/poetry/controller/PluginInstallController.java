package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.plugin.GroovyPluginEngine;
import com.ld.poetry.plugin.PluginInstallService;
import com.ld.poetry.service.prerender.PluginBootstrapMaterializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 插件安装/卸载 Controller（管理员专用）
 *
 * @author LeapYa
 * @since 2026-02-19
 */
@Slf4j
@RestController
@RequestMapping("/admin/plugin")
public class PluginInstallController {

    @Autowired
    private PluginInstallService pluginInstallService;

    @Autowired
    private GroovyPluginEngine groovyPluginEngine;

    @Autowired
    private PluginBootstrapMaterializer pluginBootstrapMaterializer;

    /**
     * 安装插件（上传 .zip）
     */
    @LoginCheck(0)
    @PostMapping(value = "/install", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PoetryResult<Map<String, Object>> install(@RequestParam("file") MultipartFile file) {
        try {
            PluginInstallService.InstallResult result = pluginInstallService.installPlugin(file);
            pluginBootstrapMaterializer.materializeAsync();
            return PoetryResult.success(Map.of(
                    "pluginKey", result.pluginKey(),
                    "version", result.version(),
                    "message", result.message()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("插件安装参数异常: {}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("插件安装失败", e);
            return PoetryResult.fail("插件安装失败: " + e.getMessage());
        }
    }

    /**
     * 卸载插件
     */
    @LoginCheck(0)
    @PostMapping("/uninstall")
    public PoetryResult<String> uninstall(@RequestBody Map<String, String> params) {
        String pluginKey = params.get("pluginKey");
        if (pluginKey == null || pluginKey.isBlank()) {
            return PoetryResult.fail("pluginKey 不能为空");
        }
        try {
            pluginInstallService.uninstallPlugin(pluginKey);
            pluginBootstrapMaterializer.materializeAsync();
            return PoetryResult.success("插件卸载成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("插件卸载参数异常: {}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("插件卸载失败", e);
            return PoetryResult.fail("插件卸载失败: " + e.getMessage());
        }
    }

    /**
     * 升级插件（上传新版本 .zip）
     */
    @LoginCheck(0)
    @PostMapping(value = "/upgrade", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PoetryResult<Map<String, Object>> upgrade(@RequestParam("file") MultipartFile file) {
        try {
            PluginInstallService.InstallResult result = pluginInstallService.upgradePlugin(file);
            pluginBootstrapMaterializer.materializeAsync();
            return PoetryResult.success(Map.of(
                    "pluginKey", result.pluginKey(),
                    "version", result.version(),
                    "message", result.message()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("插件升级参数异常: {}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("插件升级失败", e);
            return PoetryResult.fail("插件升级失败: " + e.getMessage());
        }
    }

    /**
     * 查看已加载的 Groovy 插件列表（调试用）
     */
    @LoginCheck(0)
    @GetMapping("/loaded-backends")
    public PoetryResult<Object> loadedBackends() {
        return PoetryResult.success(groovyPluginEngine.getLoadedPlugins());
    }
}
