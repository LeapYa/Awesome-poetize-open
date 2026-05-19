import request from '@/utils/request'
import { ensurePluginSdk, loadFrontendPlugin } from '@/composables/usePluginLoader'

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
        .then(() => request.get('/sysPlugin/getActiveParticleEffect'))
        .then(res => {
            const plugin = res && res.data
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
