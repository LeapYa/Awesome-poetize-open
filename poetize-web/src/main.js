/**
 * 应用入口文件 - Vue 3 + Vite 版本
 * 负责 Vue 应用初始化、插件注册和全局配置
 */

import { createApp, nextTick } from 'vue'
import App from './App.vue'
import router from './router'
// Element Plus 按需导入由 unplugin-vue-components 自动处理
// CSS 也会由 ElementPlusResolver 自动导入
// 但命令式组件 (ElMessage, ElMessageBox 等) 的样式需要手动导入
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import { createPinia, setActivePinia } from 'pinia'
import mitt from 'mitt'

// 工具函数
import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
import initAntiDebug from './utils/anti-debug'
import { getDefaultAvatar, getAvatarUrl } from './utils/default-avatar'
import animateDirective from './utils/animateDirective'
import {
    message,
    confirm,
    alert,
    prompt,
    msgbox,
    notification,
    loading,
} from './utils/element-command-services'

// 业务模块
import { notificationManager } from './utils/notification'
import { initPromiseErrorHandler } from './utils/error-handler'
import { initGrayMode } from './utils/gray-mode'

// Stores - 注意：useMainStore 只能在 app.use(pinia) 之后调用
import { useMainStore } from './stores/main'

// 样式文件
import './utils/title'
import './assets/css/animation.css'
import './assets/css/index.css'
import './assets/css/tocbot.css'
import './assets/css/color.css'
import './assets/css/font-awesome.min.css'
import './assets/css/centered-dialog.css'
import './assets/css/article-style-protection.css' // 文章页面唯一样式来源（含主题变量、hljs颜色、表格、任务列表）
import './assets/css/im.css'
// ==================== 创建 Vue 应用 ====================
const app = createApp(App)

// 初始化 Pinia - 必须在 app.use(pinia) 之前创建
const pinia = createPinia()
setActivePinia(pinia)

// ==================== 插件注册 ====================
// 重要：pinia 必须最先注册，因为其他代码可能依赖它
app.use(pinia)
app.use(router)
// Element Plus 组件由 unplugin-vue-components 按需自动导入，无需 app.use(ElementPlus)

// ==================== 现在可以安全使用 store 了 ====================
// 在 app.use(pinia) 之后立即创建 store 实例
const mainStore = useMainStore(pinia)

// 注册全局指令
app.directive('animate', animateDirective)

// ==================== 全局属性挂载 ====================
app.config.globalProperties.$http = http
app.config.globalProperties.$common = common
app.config.globalProperties.$constant = constant
app.config.globalProperties.$notify = notificationManager
app.config.globalProperties.$getDefaultAvatar = getDefaultAvatar
app.config.globalProperties.$getAvatarUrl = getAvatarUrl

// Element Plus 命令式组件挂载到全局（兼容 this.$message 等用法）
app.config.globalProperties.$message = message
app.config.globalProperties.$confirm = confirm
app.config.globalProperties.$alert = alert
app.config.globalProperties.$prompt = prompt
app.config.globalProperties.$msgbox = msgbox
app.config.globalProperties.$notification = notification
app.config.globalProperties.$loading = loading

if (typeof window !== 'undefined') {
    window.$message = message
    window.ElMessage = message
    window.ElNotification = notification
    window.ElLoading = {
        service: loading,
    }
}

// Vue 3 事件总线 - 使用 mitt 替代 Vue 2 的 new Vue()
const eventBus = mitt()
app.config.globalProperties.$bus = eventBus

// ==================== 初始化模块 ====================

// 错误处理 - Vue 3 使用 app.config.errorHandler
app.config.errorHandler = (err, instance, info) => {
    console.error('Vue Error:', err, info)
}
initPromiseErrorHandler()

// 灰度模式 - 使用 Pinia store
initGrayMode(mainStore)

// 反调试（生产环境） - 使用 Vite 环境变量
const disposeAntiDebug = initAntiDebug({
    enableInDev: import.meta.env.VITE_PRODUCTION_MODE === 'true'
})
if (disposeAntiDebug) {
    window.__disableAntiDebug = () => {
        disposeAntiDebug()
        delete window.__disableAntiDebug
    }
}

// ==================== 挂载应用 ====================
app.mount('#app')

function deferNonCriticalTask(task) {
    if (typeof window !== 'undefined' && typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(() => task(), { timeout: 2000 })
        return
    }

    setTimeout(() => task(), 0)
}

// ==================== 应用挂载后初始化 ====================
nextTick(() => {
    // 触发应用挂载完成事件
    window.dispatchEvent(new CustomEvent('app-mounted', {
        detail: {
            timestamp: Date.now(),
            prerendered: !!window.PRERENDER_DATA,
            mountType: window.PRERENDER_DATA ? 'hydration' : 'normal'
        }
    }))

    // 添加挂载完成标记
    const appElement = document.getElementById('app')
    if (appElement) {
        appElement.classList.add('vue-mounted')
    }
    document.documentElement.classList.add('loaded')
    document.documentElement.classList.remove('prerender')

    deferNonCriticalTask(async () => {
        try {
            const [{ initImageLoader }, { registerServiceWorker }, { initParticleEffect }] = await Promise.all([
                import('./utils/image-loader'),
                import('./utils/pwa-manager'),
                import('./composables/useParticleEffect'),
            ])

            // 初始化图片懒加载
            initImageLoader()

            // 注册 PWA Service Worker
            registerServiceWorker(notificationManager.info)

            await router.isReady().catch(err => {
                console.debug('等待首屏路由就绪失败:', err)
            })

            // 启动当前激活的粒子特效插件
            initParticleEffect()
        } catch (err) {
            console.error('初始化非关键模块失败:', err)
        }
    })

    // 字体加载 - 在应用挂载后执行，确保 store 已完全初始化
    if (mainStore.sysConfig) {
        deferNonCriticalTask(async () => {
            try {
                const { loadFonts } = await import('./utils/font-loader')
                await loadFonts(mainStore.sysConfig)
            } catch (err) {
                console.error('加载字体失败:', err)
            }
        })
    }
})
