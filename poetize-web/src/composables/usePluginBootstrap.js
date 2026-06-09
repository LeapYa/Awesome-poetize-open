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