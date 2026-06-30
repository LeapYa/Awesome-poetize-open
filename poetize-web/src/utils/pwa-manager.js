/**
 * PWA Service Worker 管理模块
 * 负责 Service Worker 注册、更新检测和通知
 */

import { useMainStore } from '../stores/main'

/**
 * 获取 PWA 通知图标
 * 优先级：Manifest 图标 > SEO 配置图标 > 网站头像 > 默认图标
 */
async function getPwaNotificationIcon() {
  try {
    // 1. 尝试从 manifest.json 获取图标
    try {
      const manifestResponse = await fetch('/manifest.json')
      if (manifestResponse.ok) {
        const manifest = await manifestResponse.json()
        if (manifest.icons && manifest.icons.length > 0) {
          // 优先选择 192x192 或更大的图标
          const preferredIcon = manifest.icons.find(
            (icon) =>
              icon.sizes &&
              (icon.sizes.includes('192x192') ||
                icon.sizes.includes('256x256') ||
                icon.sizes.includes('512x512'))
          )
          if (preferredIcon) return preferredIcon.src

          return manifest.icons[0].src
        }
      }
    } catch (e) {
      // 忽略 manifest 读取错误
    }

    // 2. 尝试从缓存的 SEO 配置获取图标
    const cachedSeoConfig = localStorage.getItem('seoConfig')
    if (cachedSeoConfig) {
      try {
        const seoConfig = JSON.parse(cachedSeoConfig)
        if (seoConfig.site_icon_192) return seoConfig.site_icon_192
        if (seoConfig.site_icon_512) return seoConfig.site_icon_512
        if (seoConfig.site_icon) return seoConfig.site_icon
        if (seoConfig.apple_touch_icon) return seoConfig.apple_touch_icon
      } catch (e) {
        // 忽略解析错误
      }
    }

    // 3. 使用网站信息中的头像
    const mainStore = useMainStore()
    const webInfo = mainStore.webInfo || {}
    if (webInfo.avatar) return webInfo.avatar

    // 4. 默认图标
    return '/static/assets/poetize.jpg'
  } catch (error) {
    return '/static/assets/poetize.jpg'
  }
}

/**
 * 处理 Service Worker 更新
 * @param {ServiceWorker} worker - Service Worker 实例
 * @param {Function} notifyFn - 通知函数
 */
async function handleSwUpdate(worker, notifyFn) {
  try {
    // 优先使用 Vue 的全局通知系统
    if (notifyFn) {
      notifyFn('PWA更新', '应用有新版本可用，刷新页面以使用最新功能', 5000)
    }
  } catch (error) {
    // 降级到浏览器原生通知
    if ('Notification' in window && Notification.permission === 'granted') {
      try {
        const icon = await getPwaNotificationIcon()
        new Notification('PWA更新', {
          body: '应用有新版本可用，刷新页面以使用最新功能',
          icon: icon,
        })
      } catch (e) {
        // 没有图标也显示通知
        new Notification('PWA更新', {
          body: '应用有新版本可用，刷新页面以使用最新功能',
        })
      }
    }
  }
}

/**
 * 注册 Service Worker
 * @param {Function} notifyFn - 通知函数（可选）
 */
export function registerServiceWorker(notifyFn = null) {
  // 检查浏览器是否支持 Service Worker
  if (!('serviceWorker' in navigator)) {
    return
  }

  // 开发态禁用 Service Worker：SW 会缓存旧 chunk，导致改代码后 F5 拿到旧缓存
  // 出现 Vue 渲染错乱（ref owner context / resolveComponent 警告）→ 白屏
  // 只能 Ctrl+Shift+R 绕过 SW 才能恢复。开发态主动注销残留 SW 并清理缓存。
  if (import.meta.env.DEV) {
    navigator.serviceWorker.getRegistrations()
      .then((regs) => {
        if (regs.length > 0) {
          console.info('[PWA] 开发环境：注销残留 Service Worker', regs.length)
          regs.forEach((r) => r.unregister())
          // 清理 SW 缓存（caches API）
          if ('caches' in window) {
            caches.keys().then((keys) => keys.forEach((k) => caches.delete(k)))
          }
        }
      })
      .catch(() => {})
    return
  }

  navigator.serviceWorker
    .register('/sw.js')
    .then(function (registration) {
      // 检查是否有等待中的 Service Worker
      if (registration.waiting) {
        handleSwUpdate(registration.waiting, notifyFn)
      }

      // 监听 Service Worker 更新
      registration.addEventListener('updatefound', function () {
        const newWorker = registration.installing
        if (newWorker) {
          newWorker.addEventListener('statechange', function () {
            if (
              newWorker.state === 'installed' &&
              navigator.serviceWorker.controller
            ) {
              handleSwUpdate(newWorker, notifyFn)
            }
          })
        }
      })

      // 监听 Service Worker 控制器变化
      registration.addEventListener('controllerchange', function () {
        // 可在此处理页面重新加载逻辑
      })
    })
    .catch(function (error) {
      console.error('Service Worker 注册失败:', error)
    })

  // 监听 Service Worker 消息
  navigator.serviceWorker.addEventListener('message', function (event) {
    if (event.data && event.data.type === 'SW_UPDATED') {
      // 处理更新消息
    }
  })
}
