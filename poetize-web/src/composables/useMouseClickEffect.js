/**
 * 鼠标点击效果管理器
 * 通过插件系统动态加载和执行点击效果
 * 
 * 效果完全由插件定义，支持：
 * - JSON配置：通过config参数传递
 * - JS代码：在插件中编写自定义效果逻辑
 * - anime.js：可使用anime动画库创建复杂效果
 * 
 * 优先级：本地临时覆盖 > 后端配置
 * 刷新页面后恢复后端配置
 */
import request from '@/utils/request'
import { getPluginBootstrap } from '@/composables/usePluginBootstrap'

// 效果类型列表（用于循环切换）
let EFFECT_TYPES = ['none', 'text', 'firework']
let EFFECT_LABELS = {
  none: '无效果',
  text: '社会主义核心价值观',
  firework: '烟花粒子',
}

// 本地临时覆盖（刷新后失效）
let localOverride = null

// 插件代码缓存
const pluginCodeCache = {}
// 插件配置缓存
const pluginConfigCache = {}
// 编译后的插件函数缓存
const compiledPluginCache = {}

// 动态加载 anime.js 库
function loadAnimeJS() {
  return new Promise((resolve, reject) => {
    if (window.anime) {
      resolve(window.anime)
      return
    }

    const script = document.createElement('script')
    script.src = '/libs/js/anime.min.js'
    script.onload = () => resolve(window.anime)
    script.onerror = () => reject(new Error('Failed to load anime.js'))
    document.head.appendChild(script)
  })
}

// ============ 统一点击处理 ============
function shouldIgnoreClick(e) {
  return (
    e.target.closest('#waifu') ||
    e.target.closest('#waifu-chat') ||
    e.target.closest('#waifu-tool') ||
    e.target.closest('button') ||
    e.target.closest('a') ||
    e.target.closest('.el-dialog') ||
    e.target.closest('.el-message-box') ||
    e.target.closest('.el-popover') ||
    e.target.closest('.el-drawer') ||
    e.target.closest('.tool-box') ||
    e.target.closest('input') ||
    e.target.closest('textarea') ||
    e.target.nodeName === 'A' ||
    e.target.nodeName === 'IMG'
  )
}

/**
 * 编译并缓存插件代码
 * @param {string} pluginKey - 插件标识
 * @param {string} code - JS代码
 * @returns {Function|null} 编译后的函数
 */
function compilePluginCode(pluginKey, code) {
  if (!code || typeof code !== 'string') {
    return null
  }

  if (compiledPluginCache[pluginKey]) {
    return compiledPluginCache[pluginKey]
  }

  try {
    const fn = new Function(
      'x',
      'y',
      'config',
      'anime',
      `${code}\n//# sourceURL=poetize-mouse-effect-${pluginKey}.js`
    )
    compiledPluginCache[pluginKey] = fn
    return fn
  } catch (error) {
    console.error(`[鼠标点击特效] 插件代码编译失败 (${pluginKey}):`, error)
    return null
  }
}

/**
 * 执行自定义插件效果
 * @param {string} pluginKey - 插件标识
 * @param {number} x - 点击X坐标
 * @param {number} y - 点击Y坐标
 */
async function executeCustomPlugin(pluginKey, x, y) {
  const code = pluginCodeCache[pluginKey]
  if (!code) return false

  const fn = compilePluginCode(pluginKey, code)
  if (!fn) return false

  try {
    // 解析配置
    let config = {}
    const configStr = pluginConfigCache[pluginKey]
    if (configStr) {
      try {
        config = JSON.parse(configStr)
      } catch (e) {
        // 配置解析失败，使用空对象
      }
    }

    // 尝试加载 anime.js（如果插件需要）
    let anime = window.anime
    if (!anime) {
      try {
        anime = await loadAnimeJS()
      } catch (e) {
        // anime.js 加载失败，传入 null
        anime = null
      }
    }

    // 执行插件函数，传入 anime 库
    fn(x, y, config, anime)
    return true
  } catch (e) {
    console.error(`执行插件 ${pluginKey} 失败:`, e)
    return false
  }
}

/**
 * 应用鼠标点击效果插件列表（更新效果类型/标签/代码缓存）
 */
function applyMouseClickPlugins(plugins) {
  if (!Array.isArray(plugins)) return

  const newTypes = []
  const newLabels = {}

  plugins.forEach(plugin => {
    const key = plugin.pluginKey
    if (!newTypes.includes(key)) {
      newTypes.push(key)
    }
    newLabels[key] = plugin.pluginName

    if (plugin.pluginCode) {
      pluginCodeCache[key] = plugin.pluginCode
      delete compiledPluginCache[key]
    }
    if (plugin.pluginConfig) {
      pluginConfigCache[key] = plugin.pluginConfig
    }
  })

  EFFECT_TYPES = newTypes
  EFFECT_LABELS = newLabels
}

/**
 * 应用当前激活的鼠标点击效果
 */
function applyActiveMouseClickEffect(active) {
  if (active && active.pluginKey) {
    localOverride = active.pluginKey
  }
}

/**
 * 加载插件列表和当前激活状态
 * 优先消费首屏聚合数据，缺失字段才回退到各自的旧接口
 */
function loadPluginEffects() {
  getPluginBootstrap()
    .then(data => {
      // 效果列表
      if (data && Array.isArray(data.mouseClickEffects)) {
        applyMouseClickPlugins(data.mouseClickEffects)
      } else {
        request.get('/sysPlugin/getMouseClickEffects')
          .then(res => applyMouseClickPlugins(res && res.data))
          .catch(err => console.debug('加载鼠标点击效果插件失败，使用默认列表', err))
      }

      // 当前激活项
      if (data && Object.prototype.hasOwnProperty.call(data, 'activeMouseClickEffect')) {
        applyActiveMouseClickEffect(data.activeMouseClickEffect)
      } else {
        request.get('/sysPlugin/getActiveMouseClickEffect')
          .then(res => applyActiveMouseClickEffect(res && res.data))
          .catch(err => console.debug('获取当前激活鼠标点击效果失败:', err))
      }
    })
    .catch(err => {
      console.debug('加载鼠标点击效果聚合数据失败，回退旧接口', err)
      request.get('/sysPlugin/getMouseClickEffects')
        .then(res => applyMouseClickPlugins(res && res.data))
        .catch(() => {})
      request.get('/sysPlugin/getActiveMouseClickEffect')
        .then(res => applyActiveMouseClickEffect(res && res.data))
        .catch(() => {})
    })
}

/**
 * 获取当前生效的效果类型
 * 优先级：本地临时覆盖 > 后端配置
 */
function getCurrentEffect(mainStore) {
  if (localOverride !== null) {
    return localOverride
  }
  return mainStore?.webInfo?.mouseClickEffect || 'none'
}

/**
 * 循环切换到下一个效果（临时覆盖）
 * 同时同步更新到后端插件系统
 * @returns {Object} { type: 效果类型, label: 效果名称 }
 */
export function cycleMouseClickEffect(mainStore) {
  const currentEffect = getCurrentEffect(mainStore)
  const currentIndex = EFFECT_TYPES.indexOf(currentEffect)
  const nextIndex = (currentIndex + 1) % EFFECT_TYPES.length
  const nextEffect = EFFECT_TYPES[nextIndex]

  // 设置本地临时覆盖
  localOverride = nextEffect

  // 同步更新到后端插件系统
  request.post('/sysPlugin/setActivePlugin', {
    pluginType: 'mouse_click_effect',
    pluginKey: nextEffect
  }).catch(err => {
    console.debug('同步鼠标点击效果到后端失败:', err)
  })

  return {
    type: nextEffect,
    label: EFFECT_LABELS[nextEffect] || nextEffect,
  }
}

/**
 * 获取当前效果信息
 */
export function getMouseClickEffectInfo(mainStore) {
  const effect = getCurrentEffect(mainStore)
  return {
    type: effect,
    label: EFFECT_LABELS[effect] || effect,
    isOverridden: localOverride !== null,
  }
}

/**
 * 重置为后端配置（清除本地覆盖）
 */
export function resetMouseClickEffect() {
  localOverride = null
}

/**
 * 检测是否为低性能设备
 * @param {Object} settings - 用户自定义设置
 */
function isLowPerformanceDevice(settings) {
  const cpuCoreThreshold = settings.cpuCoreThreshold ?? 2
  const memoryThreshold = settings.memoryThreshold ?? 4
  const disableMobile = settings.disableMobile ?? true

  // 检测CPU核心数
  const cpuCores = navigator.hardwareConcurrency || 4
  if (cpuCores <= cpuCoreThreshold) return true

  // 检测设备内存 (单位: GB)
  // 注意：navigator.deviceMemory 是非标准API，但在Chrome等浏览器中可用
  if (navigator.deviceMemory && navigator.deviceMemory <= memoryThreshold) return true

  // 检测移动设备（如果配置为移动设备视为低性能）
  if (disableMobile) {
    const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
    if (isMobile) return true
  }

  return false
}

/**
 * 读取特效设置
 */
function getEffectSettings(mainStore) {
  let settings = { disableLowPerf: false, disableInAdmin: false, cpuCoreThreshold: 2, disableMobile: true }

  // 仅从后端加载
  if (mainStore && mainStore.webInfo && mainStore.webInfo.mouseClickEffectConfig) {
    try {
      const backend = JSON.parse(mainStore.webInfo.mouseClickEffectConfig)
      settings = { ...settings, ...backend }
    } catch (e) { }
  }

  return settings
}

/**
 * 初始化鼠标点击效果（用于 Options API）
 * @param {Object} mainStore - Pinia store 实例
 * @returns {Function} 清理函数
 */
export function initMouseClickEffect(mainStore) {
  // 读取设置
  const settings = getEffectSettings(mainStore)

  // 如果开启了低性能设备禁用，且当前是低性能设备，则不初始化
  if (settings.disableLowPerf && isLowPerformanceDevice(settings)) {
    console.log('低性能设备，已禁用点击特效')
    return () => { } // 返回空清理函数
  }

  // 加载插件列表
  loadPluginEffects()

  const handleClick = (e) => {
    const effect = getCurrentEffect(mainStore)
    if (effect === 'none') return
    if (shouldIgnoreClick(e)) return

    // 使用 pageX/pageY 获取包含滚动偏移的页面坐标
    // 这样当页面滚动后，点击特效位置仍然正确
    const x = e.pageX
    const y = e.pageY

    // 执行插件代码
    if (pluginCodeCache[effect]) {
      executeCustomPlugin(effect, x, y)
    } else {
      console.warn(`插件 ${effect} 没有可执行的代码`)
    }
  }

  document.body.addEventListener('click', handleClick)

  // 原有的清理函数
  let cleanup = () => {
    document.body.removeEventListener('click', handleClick)
  }

  // FPS 动态监测
  let fpsCheckTimer = null
  if (settings.enableFpsCheck) {
    let frameCount = 0
    let lastTime = performance.now()
    let lowFpsCount = 0

    const checkFps = () => {
      const now = performance.now()
      frameCount++

      if (now - lastTime >= 1000) {
        const fps = Math.round((frameCount * 1000) / (now - lastTime))
        // console.log(`Current FPS: ${fps}`)

        if (fps < (settings.fpsThreshold ?? 30)) {
          lowFpsCount++
          // 连续 3 秒低于阈值，或者累计 5 次低于阈值
          if (lowFpsCount >= 3) {
            console.warn(`FPS过多低于阈值(${settings.fpsThreshold})，自动关闭点击特效`)
            cleanup() // 移除事件监听
            cleanup = () => { } // 防止重复调用
            cancelAnimationFrame(fpsCheckTimer)
            return
          }
        } else {
          lowFpsCount = Math.max(0, lowFpsCount - 1)
        }

        frameCount = 0
        lastTime = now
      }

      fpsCheckTimer = requestAnimationFrame(checkFps)
    }

    // 延迟启动监测，避免页面加载时的卡顿影响
    setTimeout(() => {
      fpsCheckTimer = requestAnimationFrame(checkFps)
    }, 2000)
  }

  // 返回组合的清理函数
  return () => {
    cleanup()
    if (fpsCheckTimer) {
      cancelAnimationFrame(fpsCheckTimer)
    }
  }
}
