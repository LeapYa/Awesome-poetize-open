package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.SysPluginActiveMapper;
import com.ld.poetry.entity.SysPlugin;
import com.ld.poetry.entity.SysPluginActive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前台首屏插件聚合数据构建器。
 *
 * <p>供 {@link com.ld.poetry.service.prerender.PluginBootstrapMaterializer} 物化为
 * /static/pb.[hash].js 静态 JS，通过 index.html 同步 <script> 加载到 window.__PB__，
 * 走 CDN 永久缓存，零 API 回源。
 *
 * <p>每一项独立 try-catch 兜底，单项失败不影响其余字段。
 *
 * @author LeapYa
 * @since 2026-07-16
 */
@Service
@Slf4j
public class PluginBootstrapDataProvider {

    @Autowired
    private SysPluginService sysPluginService;

    @Autowired
    private SysPluginActiveMapper sysPluginActiveMapper;

    /**
     * 构建首屏插件聚合数据（全量，供物化 JS 使用），
     * 包含 activePlugins、mouseClickEffects、activeMouseClickEffect、activeParticleEffect 四个 key。
     * 粒子特效 pluginCode 体积较大，物化 JS 一次性打包，走 CDN 永久缓存，首屏零额外 RTT。
     */
    public Map<String, Object> buildBootstrapData() {
        Map<String, Object> result = new HashMap<>();
        loadAggregatedFields(result);

        try {
            result.put("activeParticleEffect", loadActiveParticleEffect());
        } catch (Exception e) {
            log.warn("buildBootstrapData 获取激活粒子特效失败", e);
        }

        return result;
    }

    /**
     * 构建聚合 API 响应数据（首屏必需字段，不含粒子特效），
     * 包含 activePlugins、mouseClickEffects、activeMouseClickEffect 三个 key。
     * 粒子特效加载时机晚（waitForPageResourcesReady），不阻塞首屏，走 /sysPlugin/getActiveParticleEffect 单独请求。
     */
    public Map<String, Object> buildApiBootstrapData() {
        Map<String, Object> result = new HashMap<>();
        loadAggregatedFields(result);
        return result;
    }

    private void loadAggregatedFields(Map<String, Object> result) {
        try {
            result.put("activePlugins", loadActivePlugins());
        } catch (Exception e) {
            log.warn("buildBootstrapData 获取通用激活插件失败", e);
            result.put("activePlugins", Collections.emptyList());
        }

        try {
            result.put("mouseClickEffects", loadMouseClickEffects());
        } catch (Exception e) {
            log.warn("buildBootstrapData 获取鼠标点击效果列表失败", e);
            result.put("mouseClickEffects", Collections.emptyList());
        }

        try {
            result.put("activeMouseClickEffect", loadActiveMouseClickEffect());
        } catch (Exception e) {
            log.warn("buildBootstrapData 获取激活鼠标点击效果失败", e);
        }
    }

    private List<Map<String, Object>> loadActivePlugins() {
        LambdaQueryWrapper<SysPluginActive> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.orderByAsc(SysPluginActive::getId);
        List<SysPluginActive> activePlugins = sysPluginActiveMapper.selectList(activeWrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysPluginActive active : activePlugins) {
            SysPlugin plugin = sysPluginService.getPluginByTypeAndKey(active.getPluginType(), active.getPluginKey());
            if (plugin == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(plugin.getEnabled())) {
                continue;
            }
            if (!StringUtils.hasText(plugin.getVersion())) {
                continue;
            }
            result.add(buildFrontendPluginPayload(plugin));
        }

        return result;
    }

    private List<Map<String, Object>> loadMouseClickEffects() {
        List<SysPlugin> plugins = sysPluginService.getMouseClickEffectPlugins();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysPlugin plugin : plugins) {
            Map<String, Object> item = new HashMap<>();
            item.put("pluginKey", plugin.getPluginKey());
            item.put("pluginName", plugin.getPluginName());
            item.put("pluginCode", plugin.getPluginCode());
            item.put("pluginConfig", plugin.getPluginConfig());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> loadActiveMouseClickEffect() {
        String activeKey = sysPluginService.getActiveMouseClickEffect();
        SysPlugin plugin = sysPluginService.getPluginByTypeAndKey(SysPlugin.TYPE_MOUSE_CLICK_EFFECT, activeKey);

        Map<String, Object> result = new HashMap<>();
        result.put("pluginKey", activeKey);
        if (plugin != null) {
            result.put("pluginName", plugin.getPluginName());
            result.put("pluginConfig", plugin.getPluginConfig());
            result.put("pluginCode", plugin.getPluginCode());
            result.put("enabled", plugin.getEnabled());
        }
        return result;
    }

    private Map<String, Object> loadActiveParticleEffect() {
        SysPlugin plugin = sysPluginService.getActivePlugin(SysPlugin.TYPE_PARTICLE_EFFECT);
        if (plugin == null || !Boolean.TRUE.equals(plugin.getEnabled())) {
            return null;
        }
        return buildFrontendPluginPayload(plugin);
    }

    private Map<String, Object> buildFrontendPluginPayload(SysPlugin plugin) {
        Map<String, Object> result = new HashMap<>();
        result.put("pluginKey", plugin.getPluginKey());
        result.put("pluginName", plugin.getPluginName());
        result.put("pluginType", plugin.getPluginType());
        result.put("version", plugin.getVersion());
        result.put("pluginCode", plugin.getPluginCode());
        result.put("frontendCss", plugin.getFrontendCss());
        result.put("pluginConfig", plugin.getPluginConfig());
        result.put("enabled", plugin.getEnabled());
        return result;
    }

}
