/**
 * 主应用状态 - Pinia Store
 * 替代原有的 Vuex Store
 */
import { defineStore } from 'pinia'

// 缓存版本控制机制
const CACHE_VERSION = '1.0.0'
const storedVersion = localStorage.getItem('cacheVersion')

// 当缓存版本不匹配时，清除相关缓存
if (storedVersion !== CACHE_VERSION) {
  localStorage.removeItem('sortInfo')
  localStorage.removeItem('webInfo')
  localStorage.removeItem('articleList')
  localStorage.setItem('cacheVersion', CACHE_VERSION)
}

// 侧边栏聚合缓存 TTL：10 分钟。
// 后台改联系方式/快捷入口/侧栏背景后，前台同会话最长 10 分钟内自动刷新；
// 跨应用（admin→web）无法直接失效 Pinia，靠 TTL 兜底。
const ASIDE_BOOTSTRAP_TTL_MS = 10 * 60 * 1000

/**
 * 从localStorage获取数据，支持新的带时间戳格式和旧格式兼容
 */
const getFromLocalStorage = (key, defaultValue, maxAge = 86400000) => {
  // 默认1天过期
  const stored = localStorage.getItem(key)
  if (!stored) return defaultValue

  try {
    const parsed = JSON.parse(stored)

    // 检查是否是新格式(带时间戳)
    if (parsed && parsed.timestamp && parsed.data) {
      // 检查是否过期
      if (Date.now() - parsed.timestamp > maxAge) {
        localStorage.removeItem(key)
        return defaultValue
      }
      return parsed.data
    }

    // 旧格式直接返回
    return parsed
  } catch (e) {
    console.error(`解析${key}缓存出错:`, e)
    localStorage.removeItem(key)
    return defaultValue
  }
}

export const useMainStore = defineStore('main', {
  state: () => ({
    // 工具栏状态
    toolbar: getFromLocalStorage('toolbar', { visible: false, enter: true }),

    // 分类信息
    sortInfo: getFromLocalStorage('sortInfo', []),

    // 用户信息
    currentUser: getFromLocalStorage('currentUser', {}),
    currentAdmin: getFromLocalStorage('currentAdmin', {}),

    // 系统配置 - 提供默认值确保静态资源路径可用
    sysConfig: getFromLocalStorage('sysConfig', {
      webStaticResourcePrefix: '/static/', // 默认静态资源前缀
    }),

    // 网站信息
    webInfo: getFromLocalStorage('webInfo', {
      webName: '',
      webTitle: '',
      homeTitle: '',
      notices: [],
      randomCover: [],
      footer: '',
      backgroundImage: '',
      avatar: '',
      minimalFooter: false,
      navConfig: '[]', // 初始为空数组字符串
      homePagePullUpHeight: 50, // 首页横幅高度默认值
      mobileDrawerConfig: '', // 移动端侧边栏配置
      enableDynamicTitle: true, // 动态标题开关，默认开启
      mouseClickEffect: 'none', // 鼠标点击效果：none/text/firework
    }),

    // 访问量统计
    visitCounts: {},

    // 侧边栏首屏聚合缓存（contactList/quickEntryList/asideBackground）
    // 由 myAside 首次加载后注入，路由切换回首页时复用，避免重复请求
    // asideBootstrapExpireAt 记录缓存过期时间戳，TTL 由 ASIDE_BOOTSTRAP_TTL_MS 控制
    asideBootstrap: null,
    asideBootstrapExpireAt: 0,

    // 验证码相关状态
    captcha: {
      show: false, // 是否显示验证码
      action: 'comment', // 验证码操作类型
      params: null, // 验证成功后的回调参数
      onSuccess: null, // 验证成功后的回调函数
      onCancel: null, // 验证取消后的回调函数
      isReplyComment: false, // 是否为回复评论场景
    },
  }),

  getters: {
    /**
     * 首页标题：优先 homeTitle，回退 webTitle
     */
    homePageTitle: (state) => {
      return state.webInfo.homeTitle || state.webInfo.webTitle
    },
    /**
     * 文章总数
     */
    articleTotal: (state) => {
      if (state.sortInfo !== null && state.sortInfo.length !== 0) {
        if (state.sortInfo.length === 1) {
          return state.sortInfo[0].countOfSort
        } else {
          return state.sortInfo.reduce((prev, curr) => {
            if (typeof prev === 'number') {
              return prev + curr.countOfSort
            } else {
              return prev.countOfSort + curr.countOfSort
            }
          })
        }
      } else {
        return 0
      }
    },

    /**
     * 导航栏分类（sortType === 0）
     */
    navigationBar: (state) => {
      if (state.sortInfo !== null && state.sortInfo.length !== 0) {
        return state.sortInfo.filter((f) => f.sortType === 0)
      } else {
        return []
      }
    },
  },

  actions: {
    /**
     * 修改工具栏状态
     */
    changeToolbarStatus(toolbarState) {
      this.toolbar = toolbarState
      localStorage.setItem('toolbar', JSON.stringify(toolbarState))
    },

    /**
     * 加载分类信息
     */
    loadSortInfo(sortInfo) {
      if (sortInfo !== null && sortInfo.length !== 0) {
        const sortedData = sortInfo.sort((s1, s2) => s1.priority - s2.priority)

        // 存储带时间戳的数据
        const cacheData = {
          timestamp: Date.now(),
          data: sortedData,
        }

        this.sortInfo = sortedData
        localStorage.setItem('sortInfo', JSON.stringify(cacheData))
      }
    },

    /**
     * 加载侧边栏首屏聚合数据（contactList/quickEntryList/asideBackground）
     * 首次加载后缓存到 store，路由切换回首页时复用，避免重复请求。
     * 同时记录过期时间戳（ASIDE_BOOTSTRAP_TTL_MS），过期后下次访问会重新拉取，
     * 保证后台改配置后前台能在 TTL 内自动刷新。
     */
    loadAsideBootstrap(data) {
      this.asideBootstrap = data
      this.asideBootstrapExpireAt = Date.now() + ASIDE_BOOTSTRAP_TTL_MS
    },

    /**
     * 侧边栏聚合缓存是否已过期（或不存在）
     */
    isAsideBootstrapExpired() {
      return !this.asideBootstrap || Date.now() >= this.asideBootstrapExpireAt
    },

    /**
     * 显式失效侧边栏聚合缓存。
     * 跨应用（admin→web）无法直接调用，预留供同应用内配置变更后手动触发。
     */
    invalidateAsideBootstrap() {
      this.asideBootstrap = null
      this.asideBootstrapExpireAt = 0
    },

    /**
     * 加载当前用户信息
     * 注意：此方法会移除用户对象中的accessToken，token应该独立存储在localStorage中
     */
    loadCurrentUser(user) {
      // 创建用户副本并移除token
      const userWithoutToken = { ...user }
      delete userWithoutToken.accessToken

      this.currentUser = userWithoutToken
      localStorage.setItem('currentUser', JSON.stringify(userWithoutToken))
    },

    /**
     * 加载系统配置
     */
    loadSysConfig(sysConfig) {
      // 定义默认配置
      const defaultConfig = {
        webStaticResourcePrefix: '/static/', // 默认静态资源前缀
      }
      // 合并默认配置和后端配置，后端配置优先
      this.sysConfig = { ...defaultConfig, ...sysConfig }
      localStorage.setItem('sysConfig', JSON.stringify(this.sysConfig))
    },

    /**
     * 加载管理员信息
     * 注意：此方法会移除用户对象中的accessToken，token应该独立存储在localStorage中
     */
    loadCurrentAdmin(user) {
      // 创建用户副本并移除token
      const userWithoutToken = { ...user }
      delete userWithoutToken.accessToken

      this.currentAdmin = userWithoutToken
      localStorage.setItem('currentAdmin', JSON.stringify(userWithoutToken))
    },

    /**
     * 加载网站信息
     */
    loadWebInfo(webInfo) {
      // 安全地解析JSON格式的数据
      try {
        webInfo.notices = webInfo.notices ? JSON.parse(webInfo.notices) : []
      } catch (error) {
        console.error('解析notices JSON失败:', error)
        webInfo.notices = []
      }

      try {
        webInfo.randomCover = webInfo.randomCover
          ? JSON.parse(webInfo.randomCover)
          : []
      } catch (error) {
        console.error('解析randomCover JSON失败:', error)
        webInfo.randomCover = []
      }

      // 确保navConfig是有效的JSON字符串
      if (
        !webInfo.navConfig ||
        webInfo.navConfig === '{}' ||
        webInfo.navConfig === ''
      ) {
        webInfo.navConfig = '[]'
      }

      // 确保homePagePullUpHeight有默认值
      if (!webInfo.homePagePullUpHeight || webInfo.homePagePullUpHeight <= 0) {
        webInfo.homePagePullUpHeight = 50
      }

      // 存储带时间戳的数据，但将访问量数据提取出来单独存储
      const visitCounts = {
        historyAllCount: webInfo.historyAllCount,
        historyDayCount: webInfo.historyDayCount,
      }

      // 存储网站信息时排除访问量
      const webInfoToCache = { ...webInfo }
      delete webInfoToCache.historyAllCount
      delete webInfoToCache.historyDayCount

      const cacheData = {
        timestamp: Date.now(),
        data: webInfoToCache,
      }

      // 合并数据展示
      this.webInfo = { ...webInfoToCache, ...visitCounts }
      localStorage.setItem('webInfo', JSON.stringify(cacheData))

      // 单独存储访问量数据，不做持久化缓存
      this.visitCounts = visitCounts
    },

    /**
     * 设置网站信息
     */
    setWebInfo(webInfo) {
      // 存储带时间戳的数据
      const cacheData = {
        timestamp: Date.now(),
        data: webInfo,
      }

      this.webInfo = webInfo
      localStorage.setItem('webInfo', JSON.stringify(cacheData))
    },

    /**
     * 显示或隐藏验证码
     */
    showCaptcha(show) {
      this.captcha.show = show
      // 如果隐藏验证码，执行取消回调并重置状态
      if (!show) {
        if (
          this.captcha.onCancel &&
          typeof this.captcha.onCancel === 'function'
        ) {
          this.captcha.onCancel()
        }
        this.captcha.params = null
        this.captcha.onSuccess = null
        this.captcha.onCancel = null
        this.captcha.isReplyComment = false
      }
    },

    /**
     * 设置验证码参数
     */
    setVerifyParams(params) {
      if (params) {
        this.captcha.action = params.action || 'comment'
        this.captcha.params = params
        this.captcha.onSuccess = params.onSuccess || null
        this.captcha.onCancel = params.onCancel || null
        this.captcha.isReplyComment = params.isReplyComment || false
      }
    },

    /**
     * 执行验证成功回调
     */
    executeCaptchaCallback(token) {
      if (
        this.captcha.onSuccess &&
        typeof this.captcha.onSuccess === 'function'
      ) {
        this.captcha.onSuccess(token)
      }
      // 重置状态
      this.captcha.show = false
      this.captcha.params = null
      this.captcha.onSuccess = null
      this.captcha.onCancel = null
      this.captcha.isReplyComment = false
    },

    /**
     * 获取网站配置信息
     */
    async getWebsitConfig() {
      // 获取网站配置信息的action
      // 由于在Pinia action中无法直接使用Vue实例的$http，
      // 这里只是提供一个占位符实现来避免错误
      // 实际的HTTP请求应该在组件中处理

      // 如果需要实际的实现，应该在调用此action的组件中
      // 直接调用getWebInfo()方法来获取网站配置信息
      return Promise.resolve()
    },
  },
})
