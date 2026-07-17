import request from '@/utils/request'
import { ensurePluginSdk, loadFrontendPlugin } from '@/composables/usePluginLoader'
import { getPluginBootstrap } from '@/composables/usePluginBootstrap'

let particleEffectPromise = null
let windowLoadedPromise = null

function waitForWindowLoaded() {
    if (typeof window === 'undefined') {
        return Promise.resolve()
    }

    if (document.readyState === 'complete') {
        return Promise.resolve()
    }

    if (!windowLoadedPromise) {
        windowLoadedPromise = new Promise(resolve => {
            window.addEventListener('load', resolve, { once: true })
        })
    }

    return windowLoadedPromise
}

function waitForNextPaint() {
    if (typeof window === 'undefined' || typeof window.requestAnimationFrame !== 'function') {
        return Promise.resolve()
    }

    return new Promise(resolve => {
        window.requestAnimationFrame(() => {
            window.requestAnimationFrame(resolve)
        })
    })
}

async function waitForPageResourcesReady() {
    await waitForWindowLoaded()
    await waitForNextPaint()
}

export function initParticleEffect() {
    if (typeof window === 'undefined') {
        return Promise.resolve(null)
    }

    if (particleEffectPromise) {
        return particleEffectPromise
    }

    particleEffectPromise = waitForPageResourcesReady()
        .then(() => ensurePluginSdk())
        .then(() => getPluginBootstrap())
        .then(data => {
            // 物化 JS 命中时 data 含 activeParticleEffect 字段（null 表示无激活特效，不再回退）
            // 聚合 API fallback 时 data 不含该字段，走单字段接口
            if (data && Object.prototype.hasOwnProperty.call(data, 'activeParticleEffect')) {
                return data.activeParticleEffect
            }
            return request.get('/sysPlugin/getActiveParticleEffect').then(res => res && res.data)
        })
        .then(plugin => {
            if (plugin && plugin.enabled) {
                loadFrontendPlugin(plugin)
                return plugin
            }
            return null
        })
        .catch(err => {
            console.debug('[ParticleEffect] 加载粒子特效失败:', err)
            return null
        })

    return particleEffectPromise
}
