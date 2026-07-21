package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.dao.SysPluginActiveMapper;
import com.ld.poetry.dao.SysPluginMapper;
import com.ld.poetry.entity.SysPlugin;
import com.ld.poetry.entity.SysPluginActive;
import com.ld.poetry.service.SysPluginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 插件配置表 服务实现类
 * </p>
 *
 * @author LeapYa
 * @since 2026-01-06
 */
@Service
@Slf4j
public class SysPluginServiceImpl extends ServiceImpl<SysPluginMapper, SysPlugin> implements SysPluginService {

    @Autowired
    private SysPluginActiveMapper sysPluginActiveMapper;

    private boolean allowsNoActivePlugin(String pluginType) {
        return SysPlugin.TYPE_PARTICLE_EFFECT.equals(pluginType)
                || SysPlugin.TYPE_PAYMENT.equals(pluginType);
    }

    private void ensureBuiltInPlugins(String pluginType) {
        if (!SysPlugin.TYPE_EDITOR.equals(pluginType)) {
            return;
        }

        LambdaQueryWrapper<SysPlugin> pluginCountWrapper = new LambdaQueryWrapper<>();
        pluginCountWrapper.eq(SysPlugin::getPluginType, SysPlugin.TYPE_EDITOR);
        long count = this.count(pluginCountWrapper);
        if (count > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        SysPlugin vditor = new SysPlugin();
        vditor.setPluginType(SysPlugin.TYPE_EDITOR);
        vditor.setPluginKey("vditor");
        vditor.setPluginName("Vditor（功能完整）");
        vditor.setPluginDescription("功能最全，启动相对较慢");
        vditor.setPluginConfig("{\"editorKey\":\"vditor\"}");
        vditor.setPluginCode(null);
        vditor.setEnabled(true);
        vditor.setIsSystem(true);
        vditor.setSortOrder(0);
        vditor.setCreateTime(now);
        vditor.setUpdateTime(now);
        this.save(vditor);

        SysPlugin simple = new SysPlugin();
        simple.setPluginType(SysPlugin.TYPE_EDITOR);
        simple.setPluginKey("split_preview");
        simple.setPluginName("分屏预览编辑器");
        simple.setPluginDescription("左侧编辑、右侧实时预览的 Markdown 编辑器，功能完善，覆盖绝大多数写作场景");
        simple.setPluginConfig("{\"editorKey\":\"simple\"}");
        simple.setPluginCode(null);
        simple.setEnabled(true);
        simple.setIsSystem(true);
        simple.setSortOrder(1);
        simple.setCreateTime(now);
        simple.setUpdateTime(now);
        this.save(simple);

        LambdaQueryWrapper<SysPluginActive> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(SysPluginActive::getPluginType, SysPlugin.TYPE_EDITOR);
        SysPluginActive active = sysPluginActiveMapper.selectOne(activeWrapper);
        if (active == null) {
            SysPluginActive newActive = new SysPluginActive();
            newActive.setPluginType(SysPlugin.TYPE_EDITOR);
            newActive.setPluginKey("vditor");
            newActive.setUpdateTime(now);
            sysPluginActiveMapper.insert(newActive);
        }
    }

    @Override
    public List<SysPlugin> getPluginsByType(String pluginType) {
        ensureBuiltInPlugins(pluginType);
        LambdaQueryWrapper<SysPlugin> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPlugin::getPluginType, pluginType)
                    .orderByAsc(SysPlugin::getSortOrder);
        return this.list(queryWrapper);
    }

    @Override
    public SysPlugin getPluginByTypeAndKey(String pluginType, String pluginKey) {
        LambdaQueryWrapper<SysPlugin> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPlugin::getPluginType, pluginType)
                    .eq(SysPlugin::getPluginKey, pluginKey);
        return this.getOne(queryWrapper);
    }

    @Override
    public SysPlugin getActivePlugin(String pluginType) {
        ensureBuiltInPlugins(pluginType);
        // 先获取激活记录
        LambdaQueryWrapper<SysPluginActive> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(SysPluginActive::getPluginType, pluginType);
        SysPluginActive active = sysPluginActiveMapper.selectOne(activeWrapper);
        
        if (active == null) {
            if (SysPlugin.TYPE_EDITOR.equals(pluginType)) {
                return getPluginByTypeAndKey(SysPlugin.TYPE_EDITOR, "vditor");
            }
            log.debug("未找到插件类型 {} 的激活记录", pluginType);
            return null;
        }
        
        // 获取对应的插件
        SysPlugin plugin = getPluginByTypeAndKey(pluginType, active.getPluginKey());
        if (plugin == null) {
            log.warn("插件类型 {} 的激活记录指向了不存在的插件: {}", pluginType, active.getPluginKey());
            return null;
        }

        if (!Boolean.TRUE.equals(plugin.getEnabled()) && allowsNoActivePlugin(pluginType)) {
            sysPluginActiveMapper.deleteById(active.getId());
            log.info("清理已禁用插件的激活记录: type={}, key={}", pluginType, active.getPluginKey());
            return null;
        }

        return plugin;
    }

    @Override
    @Transactional
    public boolean setActivePlugin(String pluginType, String pluginKey) {
        try {
            ensureBuiltInPlugins(pluginType);
            // 验证插件是否存在
            SysPlugin plugin = getPluginByTypeAndKey(pluginType, pluginKey);
            if (plugin == null) {
                log.error("插件不存在: type={}, key={}", pluginType, pluginKey);
                return false;
            }
            
            // 验证插件是否启用
            if (!Boolean.TRUE.equals(plugin.getEnabled())) {
                log.error("插件未启用: type={}, key={}", pluginType, pluginKey);
                return false;
            }
            
            // 更新或插入激活记录
            LambdaQueryWrapper<SysPluginActive> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysPluginActive::getPluginType, pluginType);
            SysPluginActive active = sysPluginActiveMapper.selectOne(queryWrapper);
            
            if (active == null) {
                // 插入新记录
                active = new SysPluginActive();
                active.setPluginType(pluginType);
                active.setPluginKey(pluginKey);
                active.setUpdateTime(LocalDateTime.now());
                sysPluginActiveMapper.insert(active);
            } else {
                // 更新记录
                active.setPluginKey(pluginKey);
                active.setUpdateTime(LocalDateTime.now());
                sysPluginActiveMapper.updateById(active);
            }
            
            log.info("成功设置激活插件: type={}, key={}", pluginType, pluginKey);
            return true;
        } catch (Exception e) {
            log.error("设置激活插件失败: type={}, key={}, error={}", pluginType, pluginKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<SysPlugin> getMouseClickEffectPlugins() {
        LambdaQueryWrapper<SysPlugin> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPlugin::getPluginType, SysPlugin.TYPE_MOUSE_CLICK_EFFECT)
                    .eq(SysPlugin::getEnabled, true)
                    .orderByAsc(SysPlugin::getSortOrder);
        return this.list(queryWrapper);
    }

    @Override
    public String getActiveMouseClickEffect() {
        LambdaQueryWrapper<SysPluginActive> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPluginActive::getPluginType, SysPlugin.TYPE_MOUSE_CLICK_EFFECT);
        SysPluginActive active = sysPluginActiveMapper.selectOne(queryWrapper);
        
        if (active == null) {
            return "none"; // 默认无效果
        }
        return active.getPluginKey();
    }
}
