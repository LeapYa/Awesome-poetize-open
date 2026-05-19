/**
 * 资源加载工具
 * 用于异步加载外部CSS和JS资源
 */

/**
 * 检查资源是否已加载
 */
export function isResourceLoaded(url, type) {
  const selector = type === 'css'
    ? `link[href*="${url}"]`
    : `script[src*="${url}"]`

  return document.querySelector(selector) !== null
}

/**
 * 加载外部资源
 * @param {string} url - 资源URL
 * @param {string} type - 资源类型 'css' | 'js'
 * @returns {Promise<string>}
 */
export function loadExternalResource(url, type) {
  return new Promise((resolve, reject) => {
    // 检查是否已加载
    if (isResourceLoaded(url, type)) {
      return resolve(url)
    }

    let tag

    if (type === 'css') {
      tag = document.createElement('link')
      tag.rel = 'stylesheet'
      tag.href = url
    } else if (type === 'js') {
      tag = document.createElement('script')
      tag.src = url
      tag.async = true
    } else {
      return reject(new Error(`不支持的资源类型: ${type}`))
    }

    tag.onload = () => {
      resolve(url)
    }

    tag.onerror = () => {
      console.error(`资源加载失败: ${url}`)
      reject(new Error(`资源加载失败: ${url}`))
    }

    document.head.appendChild(tag)
  })
}

/**
 * 批量加载资源
 * @param {Array} resources - 资源数组 [{url, type}]
 * @returns {Promise<Array>}
 */
export function loadResources(resources) {
  return Promise.all(
    resources.map(({ url, type }) => loadExternalResource(url, type))
  )
}

/**
 * 预加载资源（使用link preload）
 * @param {string} url - 资源URL
 * @param {string} as - 资源类型 'script' | 'style' | 'fetch'
 */
export function preloadResource(url, as) {
  if (document.querySelector(`link[rel="preload"][href="${url}"]`)) {
    return // 已存在
  }

  const link = document.createElement('link')
  link.rel = 'preload'
  link.href = url
  link.as = as

  if (as === 'fetch') {
    link.crossOrigin = 'anonymous'
  }

  document.head.appendChild(link)
}

/**
 * 检查Live2D库是否已加载
 */
export function isLive2DLoaded() {
  return typeof window.loadlive2d === 'function'
}

/**
 * 检查KaTeX是否已加载
 */
export function isKatexLoaded() {
  return typeof window.katex !== 'undefined'
}

/**
 * 检查Markdown-it是否已加载
 */
export function isMarkdownItLoaded() {
  return typeof window.markdownit !== 'undefined'
}

/**
 * 检查Mermaid是否已加载
 */
export function isMermaidLoaded() {
  return typeof window.mermaid !== 'undefined'
}

/**
 * 加载Live2D相关资源
 * @param {string} live2dPath - Live2D资源路径
 */
export async function loadLive2DResources(live2dPath) {
  const resources = [
    { url: `${live2dPath}live2d.min.js`, type: 'js' },
  ]

  try {
    await loadResources(resources)
    return true
  } catch (error) {
    console.error('Live2D资源加载失败:', error)
    return false
  }
}

/**
 * 加载Markdown渲染所需资源
 * 注意：markdown-it 现在通过 npm 包动态导入，不再使用外部 JS 文件
 */
export async function loadMarkdownResources() {
  if (!isKatexLoadedGlobal()) {
    await loadKatexResources()
  }
  return true
}

/**
 * 检查ECharts是否已加载
 */
export function isEChartsLoaded() {
  return typeof window.echarts !== 'undefined'
}



/**
 * 检查代码高亮库是否已加载
 */
export function isHighlightJsLoaded() {
  return typeof window.hljs !== 'undefined'
}

/**
 * 加载代码高亮资源
 */
export async function loadHighlightResources() {
  if (isHighlightJsLoaded()) {
    return true
  }

  try {
    const resources = [
      { url: '/libs/css/highlight.min.css', type: 'css' },
      { url: '/libs/js/highlight.min.js', type: 'js' }
    ]

    await loadResources(resources)

    // 加载行号插件
    if (typeof window.hljs !== 'undefined') {
      await loadExternalResource('/libs/js/highlightjs-line-numbers.min.js', 'js')
    }

    return true
  } catch (error) {
    return false
  }
}

/**
 * 检查Clipboard.js是否已加载
 */
export function isClipboardLoaded() {
  return typeof window.ClipboardJS !== 'undefined'
}

/**
 * 加载Clipboard.js（代码复制功能）
 */
export async function loadClipboardResources() {
  if (isClipboardLoaded()) {
    return true
  }

  try {
    await loadExternalResource('/libs/js/clipboard.min.js', 'js')
    return true
  } catch (error) {
    return false
  }
}

/**
 * 检查KaTeX是否已加载（更新为检查全局window对象）
 */
export function isKatexLoadedGlobal() {
  return typeof window.katex !== 'undefined'
}

/**
 * 加载KaTeX数学公式库
 * 使用 npm 包动态导入，支持 Tree Shaking
 */
export async function loadKatexResources() {
  if (isKatexLoadedGlobal()) {
    return true
  }

  try {
    // 动态导入 KaTeX
    const katex = await import('katex')
    window.katex = katex.default || katex

    // 动态加载 KaTeX CSS
    await loadExternalResource('/libs/css/katex.min.css', 'css')

    return true
  } catch (error) {
    console.error('KaTeX 加载失败:', error)
    return false
  }
}

/**
 * 检查Qiniu SDK是否已加载
 */
export function isQiniuLoaded() {
  return typeof window.qiniu !== 'undefined'
}

/**
 * 加载七牛云SDK（仅在上传时需要）
 */
export async function loadQiniuResources() {
  if (isQiniuLoaded()) {
    return true
  }

  try {
    await loadExternalResource('/libs/js/qiniu.min.js', 'js')
    return true
  } catch (error) {
    return false
  }
}

/**
 * 检查Markdown-it是否已加载（全局版本）
 */
export function isMarkdownItLoadedGlobal() {
  return typeof window.markdownit !== 'undefined'
}

/**
 * 加载Markdown-it库
 * 现在使用动态导入 npm 包，而不是外部 JS 文件
 */
export async function loadMarkdownItResources() {
  if (isMarkdownItLoadedGlobal()) {
    return true
  }

  try {
    const { loadMarkdownIt } = await import('@/utils/markdownItLoader.js')
    await loadMarkdownIt()
    return true
  } catch (error) {
    console.warn('Failed to load markdown-it:', error)
    return false
  }
}
