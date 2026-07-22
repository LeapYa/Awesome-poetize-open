import { createRouter, createWebHistory } from 'vue-router'
import { useMainStore } from '../stores/main'
import constant from '../utils/constant'
import common from '../utils/common'
import {
  handleTokenExpire,
  isLoggedIn,
  getValidToken,
  clearAuthState,
} from '../utils/tokenExpireHandler'
import {
  ensureSessionValid,
  hasStoredSessionToken,
} from '../utils/sessionValidation'

const routes = [
  {
    path: '/',
    component: () => import('../components/home'),
    children: [
      {
        path: '/',
        name: 'index',
        component: () => import('../components/index'),
      },
      {
        path: '/sort',
        name: 'sort',
        component: () => import('../components/sort'),
      },
      {
        path: '/sort/:id',
        name: 'sort-category',
        component: () => import('../components/sort'),
      },
      {
        path: '/article/:lang/:id',
        name: 'article-translated',
        component: () => import('../components/article'),
      },
      {
        path: '/article/:id',
        name: 'article',
        component: () => import('../components/article'),
      },
      {
        path: '/weiYan',
        name: 'weiYan',
        component: () => import('../components/weiYan'),
      },
      {
        path: '/love',
        name: 'love',
        component: () => import('../components/love'),
      },
      {
        path: '/favorite',
        name: 'favorite',
        component: () => import('../components/favorite'),
      },
      {
        path: '/friends',
        name: 'friends',
        component: () => import('../components/FriendLinks'),
      },
      {
        path: '/music',
        name: 'music',
        component: () => import('../components/Music'),
      },
      {
        path: '/favorites',
        name: 'favorites',
        component: () => import('../components/Favorites'),
      },
      {
        path: '/travel',
        name: 'travel',
        component: () => import('../components/travel'),
      },
      {
        path: '/message',
        name: 'message',
        component: () => import('../components/message'),
      },
      {
        path: '/about',
        name: 'about',
        component: () => import('../components/about'),
      },
      {
        path: '/user',
        name: 'user',
        component: () => import('../components/user'),
      },
      {
        path: '/oauth-callback',
        name: 'oauth-callback',
        component: () => import('../components/oauth-callback'),
      },
      {
        path: '/letter',
        name: 'letter',
        component: () => import('../components/letter'),
      },
      {
        path: '/privacy',
        name: 'privacy',
        component: () => import('../views/Privacy'),
      },
      {
        path: '/payment-return',
        name: 'payment-return',
        component: () => import('../components/payment-return'),
      },
    ],
  },
  {
    path: '/verify',
    redirect: (to) => {
      const redirect = to.query.redirect
      const query = { fromVerify: 'true' }
      if (redirect) {
        query.redirect = redirect
      }
      return {
        path: '/user',
        query: query,
      }
    },
  },
  {
    path: '/archives',
    redirect: (to) => ({
      path: '/',
      query: to.query,
      hash: to.hash,
    }),
  },
  {
    path: '/categories',
    redirect: (to) => ({
      path: '/sort',
      query: to.query,
      hash: to.hash,
    }),
  },
  {
    path: '/tags',
    redirect: (to) => ({
      path: '/sort',
      query: to.query,
      hash: to.hash,
    }),
  },
  {
    path: '/im',
    name: 'im',
    meta: { requireAuth: true },
    component: () => import('../components/im/index'),
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('../components/Forbidden'),
  },
  {
    path: '/404',
    name: 'notFound',
    component: () => import('../components/NotFound'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'catchAll',
    component: () => import('../components/NotFound'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: routes,
  scrollBehavior(to, from, savedPosition) {
    return { left: 0, top: 0 }
  },
})

router.beforeEach(async (to, from, next) => {
  if (to.query.redirect === '403') {
    next('/403')
    return
  }

  const publicPaths = [
    '/user',
    '/verify',
    '/403',
    '/404',
    '/',
    '/about',
    '/privacy',
    '/payment-return',
  ]
  const isPublicPath =
    publicPaths.includes(to.path) ||
    to.path.startsWith('/article/') ||
    to.path.startsWith('/sort/')

  // 处理OAuth临时授权码
  if (to.query.code && to.path === '/') {
    await handleOAuthAuthCode(to, from, next)
    return
  }

  if (
    !hasStoredSessionToken() &&
    (localStorage.getItem('currentUser') || localStorage.getItem('currentAdmin'))
  ) {
    clearAuthState()
  }

  if (hasStoredSessionToken()) {
    const sessionValid = await ensureSessionValid({
      force: false,
      source: from.name ? 'route' : 'boot',
      currentPath: to.fullPath,
      preferAdmin: to.matched.some((record) => record.meta.isAdmin),
    })

    if (!sessionValid) {
      return
    }
  }

  if (!isPublicPath) {
    const needsAdminAuth = to.matched.some((record) => record.meta.isAdmin)

    if (needsAdminAuth) {
      const adminToken = getValidToken(true)
      const isAdminLoggedIn = isLoggedIn(true)

      if (!adminToken || !isAdminLoggedIn) {
        handleTokenExpire(true, to.fullPath, { showMessage: false })
        return
      }
    } else {
      const needsAuth = to.matched.some((record) => record.meta.requireAuth)

      if (needsAuth) {
        const userToken = getValidToken(false)
        const isUserLoggedIn = isLoggedIn(false)

        if (!userToken || !isUserLoggedIn) {
          handleTokenExpire(false, to.fullPath, { showMessage: false })
          return
        }
      }
    }
  }

  next()
})

function appendVisitSignals(params) {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return

  const automation = detectAutomationSignals()

  // 自动化检测信号放在 POST body 中，避免出现在 Nginx $request 日志里。
  setVisitSignal(params, 'wd', automation.webdriver)
  setVisitSignal(params, 'wdt', automation.webdriverType)
  setVisitSignal(params, 'pl', automation.pluginCount)
  setVisitSignal(params, 'lg', automation.languageCount)
  setVisitSignal(params, 'hc', automation.hardwareConcurrency)
  setVisitSignal(params, 'tp', automation.maxTouchPoints)
  setVisitSignal(params, 'pf', automation.platform)
  setVisitSignal(params, 'dm', automation.deviceMemory)
  setVisitSignal(params, 'tz', automation.timezone)
  setVisitSignal(params, 'sw', automation.screenWidth)
  setVisitSignal(params, 'sh', automation.screenHeight)
  setVisitSignal(params, 'cd', automation.colorDepth)
  setVisitSignal(params, 'as', automation.score)
  setVisitSignal(params, 'av', automation.verdict)
  setVisitSignal(params, 'af', automation.signals.join(','))
  setVisitSignal(params, 'pqn', automation.permissionsQueryNative)
  setVisitSignal(params, 'pin', automation.pluginsItemNative)
  setVisitSignal(params, 'wdd', automation.webdriverDescriptor)
  setVisitSignal(params, 'wchrome', automation.chromeNative)
  setVisitSignal(params, 'glv', automation.webglVendor, 128)
  setVisitSignal(params, 'glr', automation.webglRenderer, 128)
  // 传递 document.referrer（真实来源页），SPA 内部跳转时保持不变
  setVisitSignal(params, 'ref', document.referrer, 512)
}

let cachedWebglInfo

function getWebglInfo() {
  if (cachedWebglInfo) return cachedWebglInfo

  let canvas
  let gl
  const info = {
    vendor: '',
    renderer: '',
  }

  try {
    canvas = document.createElement('canvas')
    canvas.width = 1
    canvas.height = 1
    gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    const debugInfo = gl && gl.getExtension && gl.getExtension('WEBGL_debug_renderer_info')
    if (gl && debugInfo) {
      info.vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL)
      info.renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL)
    }
  } catch (e) {
  } finally {
    try {
      const loseContext = gl && gl.getExtension && gl.getExtension('WEBGL_lose_context')
      if (loseContext && typeof loseContext.loseContext === 'function') {
        loseContext.loseContext()
      }
    } catch (e) {}
    if (canvas) {
      canvas.width = 0
      canvas.height = 0
      canvas.remove()
    }
    gl = null
    canvas = null
  }

  cachedWebglInfo = info
  return info
}

function detectAutomationSignals() {
  const signals = []
  let score = 0
  const nativePattern = /\[native code\]/
  const result = {
    score: 0,
    verdict: 'LIKELY_HUMAN',
    signals,
    webdriver: '',
    webdriverType: '',
    pluginCount: '',
    languageCount: '',
    hardwareConcurrency: '',
    maxTouchPoints: '',
    platform: '',
    deviceMemory: '',
    timezone: '',
    screenWidth: '',
    screenHeight: '',
    colorDepth: '',
    permissionsQueryNative: '',
    pluginsItemNative: '',
    webdriverDescriptor: '',
    webglVendor: '',
    webglRenderer: '',
  }

  const addSignal = (code, points) => {
    signals.push(code)
    score += points
  }

  try {
    result.webdriver = navigator.webdriver === true ? '1' : (navigator.webdriver === false ? '0' : '')
    result.webdriverType = typeof navigator.webdriver
    if (navigator.webdriver === true) {
      addSignal('wd', 80)
    }
    if (navigator.webdriver !== undefined && typeof navigator.webdriver !== 'boolean') {
      addSignal('wdtype', 15)
    }
  } catch (e) {}

  try {
    if (/HeadlessChrome/i.test(navigator.userAgent || '')) {
      addSignal('hch', 80)
    }
  } catch (e) {}

  try {
    result.pluginCount = navigator.plugins ? navigator.plugins.length : ''
    result.languageCount = navigator.languages ? navigator.languages.length : ''
    result.hardwareConcurrency = navigator.hardwareConcurrency
    result.maxTouchPoints = navigator.maxTouchPoints
    result.platform = navigator.platform
    result.deviceMemory = navigator.deviceMemory == null ? 'null' : navigator.deviceMemory
    if (typeof screen !== 'undefined') {
      result.screenWidth = screen.width
      result.screenHeight = screen.height
      result.colorDepth = screen.colorDepth
    }
  } catch (e) {}

  try {
    const webglInfo = getWebglInfo()
    result.webglVendor = webglInfo.vendor
    result.webglRenderer = webglInfo.renderer
    if (/SwiftShader/i.test(result.webglRenderer || '')) {
      addSignal('swg', 50)
    }
  } catch (e) {}

  try {
    // window.chrome 在真实 Chrome 桌面浏览器中始终存在
    // 无头浏览器（即使清除 HeadlessChrome UA）往往缺少此对象
    result.chromeNative = (typeof window.chrome !== 'undefined' && window.chrome !== null) ? '1' : '0'
    if (result.chromeNative === '0') {
      addSignal('wchrome', 15)
    }
  } catch (e) {}

  try {
    // 屏幕尺寸为 0 或 1 是无头环境的典型特征
    if (typeof screen !== 'undefined' && (screen.width === 0 || screen.height === 0 || screen.width === 1 || screen.height === 1)) {
      addSignal('scr0', 15)
    }
  } catch (e) {}

  try {
    if (navigator.permissions && typeof navigator.permissions.query === 'function') {
      result.permissionsQueryNative = nativePattern.test(
        Function.prototype.toString.call(navigator.permissions.query)
      ) ? '1' : '0'
      if (result.permissionsQueryNative === '0') {
        addSignal('pqn', 75)
      }
    }
  } catch (e) {}

  try {
    if (navigator.plugins && typeof navigator.plugins.item === 'function') {
      result.pluginsItemNative = nativePattern.test(
        Function.prototype.toString.call(navigator.plugins.item)
      ) ? '1' : '0'
      if (result.pluginsItemNative === '0') {
        addSignal('pin', 60)
      }
    }
  } catch (e) {}

  try {
    const descriptor = Object.getOwnPropertyDescriptor(
      Object.getPrototypeOf(navigator),
      'webdriver'
    )
    if (descriptor) {
      result.webdriverDescriptor = descriptor.get ? 'getter' : ('value' in descriptor ? 'value' : 'other')
      if ('value' in descriptor && !descriptor.get) {
        addSignal('wdprop', 60)
      }
    }
  } catch (e) {}

  try {
    const leakPattern = /(__playwright|__puppeteer|__nightmare|callPhantom|cdc_|\$cdc)/
    if (Object.getOwnPropertyNames(window).some((key) => leakPattern.test(key))) {
      addSignal('gleak', 50)
    }
  } catch (e) {}

  try {
    result.timezone = typeof Intl !== 'undefined' && Intl.DateTimeFormat
      ? Intl.DateTimeFormat().resolvedOptions().timeZone
      : ''
    // UTC 时区在任何平台都可疑——真实用户的浏览器会设为本地时区
    // 无头浏览器跑在服务器上常默认 UTC
    if (result.timezone === 'UTC') {
      addSignal('wutc', 15)
    }
    // 时区为空或 null 可疑——无头环境可能未设置时区
    if (!result.timezone || result.timezone === '') {
      addSignal('tznull', 15)
    }
    if (result.platform === 'Win32' && navigator.deviceMemory == null) {
      addSignal('wdm', 15)
    }
  } catch (e) {}

  try {
    // 直链访问（无 referrer）——可能是机器人直接拿到 URL 抓取
    // 也可能是用户手动输入/书签，所以低分 10 分，仅作叠加贡献
    if (!document.referrer || document.referrer === '') {
      addSignal('noref', 10)
    }
  } catch (e) {}

  result.score = score
  result.verdict = score >= 70 ? 'LIKELY_BOT' : (score >= 25 ? 'SUSPICIOUS' : 'LIKELY_HUMAN')
  return result
}

function setVisitSignal(params, key, value, maxLength = 64) {
  if (value === undefined || value === null || value === '') return
  params.set(key, String(value).slice(0, maxLength))
}

// ===== 页面访问量统计 =====
router.afterEach((to, from) => {
  // 404/403 不统计
  if (to.name === 'notFound' || to.name === 'forbidden' || to.name === 'catchAll') return
  // 同一页面不重复统计
  if (from.name && to.fullPath === from.fullPath) return

  try {
    const params = new URLSearchParams({ path: to.fullPath })
    appendVisitSignals(params)
    const url = constant.baseURL + '/track/pageview'
    // 使用 fetch + keepalive + credentials 代替 sendBeacon，cookie会自动携带用户身份
    fetch(url, {
      method: 'POST',
      keepalive: true,
      credentials: 'include',
      body: params
    }).catch(() => { })
  } catch (e) {
    // 统计失败不影响用户
  }
})

/**
 * 处理OAuth临时授权码
 * 使用一次性授权码换取真正的token
 */
async function handleOAuthAuthCode(to, from, next) {
  const authCode = to.query.code
  const baseURL = constant.baseURL

  try {
    // 调用后端接口，用授权码换取token
    const response = await fetch(baseURL + '/oauth/exchange', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: 'code=' + encodeURIComponent(authCode),
    })

    if (response.ok) {
      const result = await response.json()
      if (result && result.code === 200) {
        const data = result.data
        const accessToken = data.accessToken
        const redirectPath = data.redirectPath || sessionStorage.getItem('oauthRedirectPath') || '/'
        const emailCollectionNeeded = data.emailCollectionNeeded

        if (emailCollectionNeeded) {
          const tempUserData = {
            needsEmailCollection: true,
          }

          // Token由后端通过HttpOnly Cookie下发
          localStorage.setItem('tempUserData', JSON.stringify(tempUserData))

          next({
            path: redirectPath,
            query: { showEmailCollection: 'true' },
            replace: true,
          })
          return
        }

        // 正常登录流程 - Token由后端通过HttpOnly Cookie下发
        localStorage.removeItem('currentAdmin')
        localStorage.removeItem('currentUser')

        // 验证会话获取用户信息
        const tokenResponse = await fetch(baseURL + '/user/token', {
          method: 'POST',
          credentials: 'include',
        })

        if (tokenResponse.ok) {
          const tokenResult = await tokenResponse.json()
          if (tokenResult && tokenResult.code === 200) {
            const mainStore = useMainStore()
            mainStore.loadCurrentUser(tokenResult.data)
            mainStore.loadCurrentAdmin(tokenResult.data)
          }
        }

        // 清理sessionStorage中的临时数据
        sessionStorage.removeItem('oauthRedirectPath')

        next({
          path: redirectPath,
          replace: true,
        })
        return
      } else {
        console.error('OAuth授权码交换失败:', result)
        next({ path: '/', query: {}, replace: true })
        return
      }
    } else {
      console.error('OAuth授权码交换HTTP错误:', response.status)
      next({ path: '/', query: {}, replace: true })
      return
    }
  } catch (error) {
    console.error('OAuth授权码交换异常:', error)
    next({ path: '/', query: {}, replace: true })
    return
  }
}

// 全局路由错误处理：捕获异步组件（懒加载 chunk）加载失败、导航守卫异常等，
// 避免网络波动或部署后 chunk hash 失效导致空白页。
router.onError((error, to) => {
  console.error('[Router] 路由错误:', error?.message || error)
  const msg = error?.message || String(error)
  const isChunkLoadError = /Loading chunk|Failed to fetch dynamically imported module|Loading CSS chunk|Importing a module script failed/.test(msg)
  if (isChunkLoadError && typeof window !== 'undefined' && typeof sessionStorage !== 'undefined') {
    // chunk 加载失败多为网络波动或部署后旧 hash 失效；整页刷新一次重新拉取 manifest。
    // 用 sessionStorage 标记防止刷新后仍失败时陷入刷新死循环。
    const reloadKey = '__routeChunkReloaded'
    if (!sessionStorage.getItem(reloadKey)) {
      sessionStorage.setItem(reloadKey, '1')
      const target = to?.fullPath || (window.location.pathname + window.location.search + window.location.hash)
      window.location.assign(target)
      return
    }
    // 已尝试过一次仍失败：清除标记，让用户后续手动刷新可重新重试
    sessionStorage.removeItem(reloadKey)
  }
  // 非chunk错误，或chunk重试已失败：回到首页避免停留空白页
  if (to && to.fullPath && to.fullPath !== '/') {
    router.replace('/')
  }
})

export default router
