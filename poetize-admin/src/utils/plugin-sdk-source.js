/**
 * 插件 SDK 源码字符串（供预览 iframe 注入使用）
 *
 * 内容与 poetize-web/src/utils/plugin-sdk.js 保持一致。
 * 以字符串形式导出，避免 Vite `?raw` 导入在 Vue 2 SFC 编译器下的兼容性问题。
 * 注入预览 iframe 后在独立沙箱中执行，环境与前台等价。
 *
 * 注意：SDK 源码内含模板字符串 `${...}`，在本文件中需转义为 `\${...}` 避免冲突。
 * 当前只有 loadPluginCode 内一处需要转义。
 */
export const sdkCode = `(function (global) {
    'use strict'

    // ===== 内部状态 =====
    const _hooks = {}
    const _configs = {}
    const _pluginKeys = []

    function createPluginSourceUrl(pluginKey) {
        return 'poetize-plugin-' + String(pluginKey || 'unknown').replace(/[^a-zA-Z0-9_-]/g, '_') + '.js'
    }

    // ===== 公共 API =====
    const PoetizePlugin = {
        on(hookName, callback) {
            if (!_hooks[hookName]) _hooks[hookName] = []
            _hooks[hookName].push(callback)
        },

        emit(hookName, context) {
            const handlers = _hooks[hookName] || []
            handlers.forEach((fn) => {
                try {
                    fn(context)
                } catch (e) {
                    console.error('[PoetizePlugin] 钩子 ' + hookName + ' 执行错误:', e)
                }
            })
        },

        api: {
            getArticle() {
                return global.__poetize_article__ || null
            },

            getUser() {
                return global.__poetize_user__ || null
            },

            getConfig(pluginKey, configKey, defaultValue) {
                const config = _configs[pluginKey] || {}
                return config[configKey] !== undefined ? config[configKey] : defaultValue
            },

            showToast(message, type = 'info') {
                if (global.__poetize_toast__) {
                    global.__poetize_toast__(message, type)
                } else {
                    console.log('[PoetizePlugin Toast]', type, message)
                }
            },

            insertHtml(targetElement, position, html) {
                if (targetElement && typeof targetElement.insertAdjacentHTML === 'function') {
                    targetElement.insertAdjacentHTML(position, html)
                }
            },

            registerSidebarWidget(widgetId, title) {
                const mountId = 'plugin-sidebar-' + widgetId
                const sidebar = document.querySelector('.sidebar-wrapper, .right-panel, [class*="sidebar"]')
                if (!sidebar) return null
                let el = document.getElementById(mountId)
                if (!el) {
                    el = document.createElement('div')
                    el.id = mountId
                    el.className = 'plugin-sidebar-widget'
                    el.innerHTML = title ? '<div class="plugin-widget-title">' + title + '</div><div class="plugin-widget-body"></div>' : ''
                    sidebar.appendChild(el)
                }
                return el.querySelector('.plugin-widget-body') || el
            },
        },

        _internal: {
            setPluginConfig(pluginKey, config) {
                _configs[pluginKey] = config || {}
                if (!_pluginKeys.includes(pluginKey)) {
                    _pluginKeys.push(pluginKey)
                }
            },

            loadPluginCode(pluginKey, jsCode, config) {
                PoetizePlugin._internal.setPluginConfig(pluginKey, config)

                if (!jsCode || typeof jsCode !== 'string') {
                    return
                }

                try {
                    const runner = new Function(
                        'PoetizePlugin',
                        'window',
                        'document',
                        'config',
                        jsCode + '\\n//# sourceURL=' + createPluginSourceUrl(pluginKey)
                    )
                    runner(PoetizePlugin, global, global.document, config || {})
                } catch (error) {
                    console.error('[PoetizePlugin] 插件前端代码执行失败 (' + pluginKey + '):', error)
                }
            },

            loadPluginCss(pluginKey, cssCode) {
                if (!cssCode) return
                const styleId = 'plugin-style-' + pluginKey
                if (document.getElementById(styleId)) return
                const style = document.createElement('style')
                style.id = styleId
                style.textContent = cssCode
                document.head.appendChild(style)
            },
        },
    }

    global.PoetizePlugin = PoetizePlugin

})(typeof window !== 'undefined' ? window : globalThis)
`
