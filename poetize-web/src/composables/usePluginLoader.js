/**
 * 通用插件加载器
 * 负责在应用启动时从后端拉取所有激活的"通用型"插件（非鼠标效果、非看板娘等特定类型），
 * 通过 PoetizePlugin SDK 注册并执行其 JS 代码 + 注入 CSS。
 */
import request from '@/utils/request'
import { getPluginBootstrap } from '@/composables/usePluginBootstrap'

/** 已加载的插件 key 集合，防止重复加载 */
const loadedPlugins = new Set()
let pluginSdkPromise = null

/**
 * 确保前端插件 SDK 已挂载
 */
export function ensurePluginSdk() {
    if (typeof window === 'undefined') {
        return Promise.resolve(null)
    }

    if (window.PoetizePlugin) {
        return Promise.resolve(window.PoetizePlugin)
    }

    if (!pluginSdkPromise) {
        pluginSdkPromise = import('@/utils/plugin-sdk.js')
            .then(() => window.PoetizePlugin || null)
            .catch(err => {
                console.debug('[PluginLoader] 加载插件 SDK 失败:', err)
                return null
            })
    }

    return pluginSdkPromise
}

/**
 * 加载并执行单个前端插件
 */
export function loadFrontendPlugin(plugin) {
    const key = plugin.pluginKey
    if (loadedPlugins.has(key)) return
    loadedPlugins.add(key)

    try {
        let config = {}
        if (plugin.pluginConfig) {
            try { config = JSON.parse(plugin.pluginConfig) } catch (e) { /* ignore */ }
        }

        if (window.PoetizePlugin) {
            window.PoetizePlugin._internal.setPluginConfig(key, config)
        }

        // 注入 CSS
        if (plugin.frontendCss) {
            const style = document.createElement('style')
            style.id = 'plugin-style-' + key
            style.textContent = plugin.frontendCss
            document.head.appendChild(style)
        }

        // 执行前端 JS
        if (plugin.pluginCode && window.PoetizePlugin) {
            window.PoetizePlugin._internal.loadPluginCode(key, plugin.pluginCode, config)
        } else if (plugin.pluginCode) {
            console.warn('[PluginLoader] 插件 SDK 不可用，跳过前端 JS 执行 (' + key + ')')
        }
    } catch (e) {
        console.error('[PluginLoader] 插件加载失败 (' + key + '):', e)
    }
}

/**
 * 卸载插件（移除 CSS，清除缓存）
 */
function unloadPlugin(key) {
    const style = document.getElementById('plugin-style-' + key)
    if (style) style.remove()
    loadedPlugins.delete(key)
}

/**
 * 初始化通用插件加载器
 * 在 home.vue mounted 时调用一次
 */
export function initPluginLoader() {
    if (typeof window === 'undefined') return

    ensurePluginSdk().finally(() => {
        const applyPlugins = list => {
            if (Array.isArray(list)) {
                list.forEach(plugin => {
                    if (plugin.enabled) loadFrontendPlugin(plugin)
                })
            }
        }

        getPluginBootstrap()
            .then(data => {
                if (data && Array.isArray(data.activePlugins)) {
                    applyPlugins(data.activePlugins)
                    return
                }
                // 聚合数据缺失，回退旧接口
                return request.get('/sysPlugin/listActivePlugins')
                    .then(res => applyPlugins(res && res.data))
            })
            .catch(err => {
                console.debug('[PluginLoader] 加载插件失败:', err)
            })
    })
}

/**
 * 获取已加载插件列表
 */
export function getLoadedPlugins() {
    return [...loadedPlugins]
}

/**
 * 触发前端钩子（供各页面组件调用）
 * @param {string} hookName 钩子名称，如 'onArticleRender'
 * @param {object} context  上下文数据
 */
export function emitPluginHook(hookName, context) {
    if (window.PoetizePlugin && typeof window.PoetizePlugin.emit === 'function') {
        window.PoetizePlugin.emit(hookName, context)
    }
}
