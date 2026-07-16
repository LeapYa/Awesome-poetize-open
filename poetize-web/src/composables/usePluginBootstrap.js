/**
 * 前台首屏插件数据聚合加载器。
 *
 * 首屏初始化阶段，通用插件加载、鼠标点击效果、粒子特效分别散落在
 * home.vue / App.vue / main.js 中触发，过去会各自请求一个 /sysPlugin/* 接口。
 * 这里用单例 Promise 统一请求一次 /sysPlugin/frontendBootstrap，
 * 各消费方共享同一份结果；任一字段缺失时调用方回退到各自的旧接口。
 */
import request from '@/utils/request'

let bootstrapPromise = null

/**
 * 获取聚合后的插件数据（全局只发起一次请求）。
 * @returns {Promise<Object|null>} 形如 { activePlugins, mouseClickEffects, activeMouseClickEffect, activeParticleEffect }
 */
export function getPluginBootstrap() {
    if (bootstrapPromise) {
        return bootstrapPromise
    }

    // 优先使用物化的静态 JS 配置：后端在插件变更时把聚合数据写入
    // /static/pb.[hash].js（挂到 window.__PB__），
    // 并通过 index.html 中的 <script> 同步加载。该 JS 走 CDN 永久缓存，避免每次回源 API。
    if (typeof window !== 'undefined' && window.__PB__) {
        bootstrapPromise = Promise.resolve(window.__PB__)
        return bootstrapPromise
    }

    // 回退：物化文件缺失或未加载时走原 API
    bootstrapPromise = request
        .get('/sysPlugin/frontendBootstrap')
        .then(res => (res && res.data ? res.data : null))
        .catch(err => {
            console.debug('[PluginBootstrap] 聚合插件数据加载失败，调用方将回退旧接口:', err)
            // 失败时重置，允许后续消费方各自回退，不缓存失败结果
            bootstrapPromise = null
            return null
        })

    return bootstrapPromise
}
