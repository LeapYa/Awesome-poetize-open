/**
 * 资源加载工具
 * 用于异步加载外部CSS和JS资源
 */

/**
 * 检查资源是否已加载
 */
export function isResourceLoaded(url, type) {
  const selector =
    type === 'css' ? `link[href*="${url}"]` : `script[src*="${url}"]`

  return document.querySelector(selector) !== null
}

/**
 * 加载外部资源
 * @param {string} url - 资源URL
 * @param {string} type - 资源类型 'css' | 'js'
 * @returns {Promise<string>}
 */
export function loadExternalResource(url, type, options = {}) {
  return new Promise((resolve, reject) => {
    const validate =
      typeof options.validate === 'function' ? options.validate : null
    const timeoutMs = options.timeoutMs || 8000

    // 检查是否已加载
    if (isResourceLoaded(url, type)) {
      if (!validate) {
        return resolve(url)
      }

      waitForResourceValidation(validate, timeoutMs)
        .then(() => resolve(url))
        .catch(reject)
      return
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
      if (!validate) {
        resolve(url)
        return
      }

      waitForResourceValidation(validate, timeoutMs)
        .then(() => resolve(url))
        .catch(reject)
    }

    tag.onerror = () => {
      console.error(`资源加载失败: ${url}`)
      reject(new Error(`资源加载失败: ${url}`))
    }

    document.head.appendChild(tag)
  })
}

function waitForResourceValidation(validate, timeoutMs) {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now()

    const check = () => {
      try {
        if (validate()) {
          resolve()
          return
        }
      } catch (error) {
        reject(error)
        return
      }

      if (Date.now() - startedAt >= timeoutMs) {
        reject(new Error('资源加载后未通过校验'))
        return
      }

      window.setTimeout(check, 100)
    }

    check()
  })
}

function removeResourceTag(url, type) {
  const selector =
    type === 'css' ? `link[href="${url}"]` : `script[src="${url}"]`
  document.querySelector(selector)?.remove()
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
 * 使用 markdownItLoader 模块统一管理
 */
export function isMarkdownItLoaded() {
  return typeof window.markdownit !== 'undefined'
}

/**
 * 检查Mermaid是否已加载
 * 注意：loadMermaidResources 已移至 mermaidLoader.js，需要时请动态导入该文件
 */
export function isMermaidLoaded() {
  return typeof window.mermaid !== 'undefined'
}

/**
 * 动态加载 Mermaid 资源
 * 使用动态导入避免静态依赖
 */
export async function loadMermaidResources() {
  if (isMermaidLoaded()) {
    return true
  }
  // 动态导入 mermaidLoader 模块
  const { loadMermaidResources: loadMermaid } = await import('./mermaidLoader.js')
  return loadMermaid()
}

/**
 * 加载Live2D相关资源
 * @param {string} live2dPath - Live2D资源路径
 */
export async function loadLive2DResources(live2dPath) {
  if (isLive2DLoaded()) {
    return true
  }

  const candidates = uniqueResourceUrls([
    `${live2dPath}live2d.min.js`,
    'https://cdn.jsdelivr.net/gh/stevenjoezhang/live2d-widget@latest/live2d.min.js',
    'https://fastly.jsdelivr.net/gh/stevenjoezhang/live2d-widget@latest/live2d.min.js',
    'https://gcore.jsdelivr.net/gh/stevenjoezhang/live2d-widget@latest/live2d.min.js'
  ])

  for (const url of candidates) {
    try {
      await loadExternalResource(url, 'js', {
        validate: isLive2DLoaded,
        timeoutMs: 8000
      })
      return true
    } catch (error) {
      removeResourceTag(url, 'js')
      console.error(`Live2D资源加载失败: ${url}`, error)
    }
  }

  return false
}

function uniqueResourceUrls(urls) {
  return Array.from(new Set(urls.filter(Boolean)))
}

/**
 * 加载Markdown渲染所需资源
 * 注意：markdown-it 现在通过 npm 包动态导入，不再使用外部 JS 文件
 */
export async function loadMarkdownResources() {
  // KaTeX 使用动态导入
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

const ECHARTS_TYPE_TO_GROUP = {
  line: 'basic',
  bar: 'basic',
  pie: 'basic',
  scatter: 'advanced',
  radar: 'advanced',
  gauge: 'advanced',
}

const ECHARTS_DEFAULT_GROUPS = ['basic', 'advanced']
const echartsLoadedGroups = new Set()
const echartsGroupPromises = new Map()
let echartsCorePromise = null

function normalizeEChartsChartTypes(chartTypes) {
  if (!chartTypes) {
    return []
  }

  if (typeof chartTypes === 'string') {
    return [chartTypes.trim().toLowerCase()].filter(Boolean)
  }

  if (Array.isArray(chartTypes)) {
    return chartTypes
      .map((item) => String(item ?? '').trim().toLowerCase())
      .filter(Boolean)
  }

  if (chartTypes instanceof Set) {
    return Array.from(chartTypes)
      .map((item) => String(item ?? '').trim().toLowerCase())
      .filter(Boolean)
  }

  return []
}

function resolveEChartsGroups(chartTypes) {
  const normalizedTypes = normalizeEChartsChartTypes(chartTypes)
  if (normalizedTypes.length === 0) {
    return [...ECHARTS_DEFAULT_GROUPS]
  }

  const groups = new Set()
  normalizedTypes.forEach((type) => {
    const groupName = ECHARTS_TYPE_TO_GROUP[type]
    if (groupName) {
      groups.add(groupName)
    }
  })

  if (groups.size === 0) {
    return [...ECHARTS_DEFAULT_GROUPS]
  }

  return Array.from(groups)
}

async function ensureEChartsCore() {
  if (!echartsCorePromise) {
    echartsCorePromise = import('./echarts-core-loader.js')
      .then((module) => {
        const echarts = module.ensureEChartsCore()
        window.echarts = echarts
        return echarts
      })
      .catch((error) => {
        echartsCorePromise = null
        throw error
      })
  }

  const echarts = await echartsCorePromise
  window.echarts = echarts
  return echarts
}

const ECHARTS_GROUP_LOADERS = {
  basic: () => import('./echarts-basic-loader.js'),
  advanced: () => import('./echarts-advanced-loader.js'),
}

function loadEChartsChartGroup(groupName) {
  if (echartsLoadedGroups.has(groupName)) {
    return Promise.resolve(window.echarts)
  }

  if (!echartsGroupPromises.has(groupName)) {
    const loaderPromise = ensureEChartsCore()
      .then(async (echarts) => {
        const loadGroupModule = ECHARTS_GROUP_LOADERS[groupName]

        if (!loadGroupModule) {
          throw new Error(`未知的 ECharts 图表分组: ${groupName}`)
        }

        const loaderModule = await loadGroupModule()

        loaderModule.registerEChartsChartGroup(echarts)
        echartsLoadedGroups.add(groupName)
        return echarts
      })
      .catch((error) => {
        echartsGroupPromises.delete(groupName)
        throw error
      })

    echartsGroupPromises.set(groupName, loaderPromise)
  }

  return echartsGroupPromises.get(groupName)
}

/**
 * 加载ECharts图表库（按图表类型拆分加载）
 */
export async function loadEChartsResources(chartTypes) {
  try {
    const groups = resolveEChartsGroups(chartTypes)
    await Promise.all(groups.map((groupName) => loadEChartsChartGroup(groupName)))
    return true
  } catch (error) {
    console.error('ECharts按需加载失败:', error)
    return false
  }
}

/**
 * 检查代码高亮库是否已加载
 */
export function isHighlightJsLoaded() {
  return typeof window.hljs !== 'undefined'
}

/**
 * 加载代码高亮资源（按需导入优化）
 * 使用 npm 包动态导入，只加载需要的语言包，实现 Tree Shaking
 */
export async function loadHighlightResources() {
  if (isHighlightJsLoaded()) {
    return true
  }

  try {
    // 动态导入 highlight.js 核心
    const hljs = await import('highlight.js/lib/core')

    // 按需导入常用语言包（并行加载提高速度）
    const [
      javascript,
      typescript,
      css,
      xml, // HTML 使用 xml 语言包
      python,
      java,
      sql,
      bash,
      json,
      markdown,
      yaml,
      go,
      rust,
      cpp,
      csharp,
      php,
      ruby,
      swift,
      kotlin,
      shell,
      dockerfile,
      nginx,
      properties,
      ini,
    ] = await Promise.all([
      import('highlight.js/lib/languages/javascript'),
      import('highlight.js/lib/languages/typescript'),
      import('highlight.js/lib/languages/css'),
      import('highlight.js/lib/languages/xml'),
      import('highlight.js/lib/languages/python'),
      import('highlight.js/lib/languages/java'),
      import('highlight.js/lib/languages/sql'),
      import('highlight.js/lib/languages/bash'),
      import('highlight.js/lib/languages/json'),
      import('highlight.js/lib/languages/markdown'),
      import('highlight.js/lib/languages/yaml'),
      import('highlight.js/lib/languages/go'),
      import('highlight.js/lib/languages/rust'),
      import('highlight.js/lib/languages/cpp'),
      import('highlight.js/lib/languages/csharp'),
      import('highlight.js/lib/languages/php'),
      import('highlight.js/lib/languages/ruby'),
      import('highlight.js/lib/languages/swift'),
      import('highlight.js/lib/languages/kotlin'),
      import('highlight.js/lib/languages/shell'),
      import('highlight.js/lib/languages/dockerfile'),
      import('highlight.js/lib/languages/nginx'),
      import('highlight.js/lib/languages/properties'),
      import('highlight.js/lib/languages/ini'),
    ])

    const hljsCore = hljs.default

    // 注册语言包
    hljsCore.registerLanguage('javascript', javascript.default)
    hljsCore.registerLanguage('js', javascript.default) // 别名
    hljsCore.registerLanguage('typescript', typescript.default)
    hljsCore.registerLanguage('ts', typescript.default) // 别名
    hljsCore.registerLanguage('css', css.default)
    hljsCore.registerLanguage('xml', xml.default)
    hljsCore.registerLanguage('html', xml.default) // HTML 使用 xml
    hljsCore.registerLanguage('python', python.default)
    hljsCore.registerLanguage('py', python.default) // 别名
    hljsCore.registerLanguage('java', java.default)
    hljsCore.registerLanguage('sql', sql.default)
    hljsCore.registerLanguage('bash', bash.default)
    hljsCore.registerLanguage('sh', bash.default) // 别名
    hljsCore.registerLanguage('json', json.default)
    hljsCore.registerLanguage('markdown', markdown.default)
    hljsCore.registerLanguage('md', markdown.default) // 别名
    hljsCore.registerLanguage('yaml', yaml.default)
    hljsCore.registerLanguage('yml', yaml.default) // 别名
    hljsCore.registerLanguage('go', go.default)
    hljsCore.registerLanguage('golang', go.default) // 别名
    hljsCore.registerLanguage('rust', rust.default)
    hljsCore.registerLanguage('rs', rust.default) // 别名
    hljsCore.registerLanguage('cpp', cpp.default)
    hljsCore.registerLanguage('c++', cpp.default) // 别名
    hljsCore.registerLanguage('c', cpp.default) // C 使用 cpp
    hljsCore.registerLanguage('csharp', csharp.default)
    hljsCore.registerLanguage('cs', csharp.default) // 别名
    hljsCore.registerLanguage('php', php.default)
    hljsCore.registerLanguage('ruby', ruby.default)
    hljsCore.registerLanguage('rb', ruby.default) // 别名
    hljsCore.registerLanguage('swift', swift.default)
    hljsCore.registerLanguage('kotlin', kotlin.default)
    hljsCore.registerLanguage('kt', kotlin.default) // 别名
    hljsCore.registerLanguage('shell', shell.default)
    hljsCore.registerLanguage('dockerfile', dockerfile.default)
    hljsCore.registerLanguage('docker', dockerfile.default) // 别名
    hljsCore.registerLanguage('nginx', nginx.default)
    hljsCore.registerLanguage('properties', properties.default)
    hljsCore.registerLanguage('ini', ini.default)

    // 挂载到 window 对象
    window.hljs = hljsCore

    // 动态导入行号插件
    await import('highlightjs-line-numbers.js')

    // 动态加载 highlight.js 样式
    await loadExternalResource('/libs/css/highlight.min.css', 'css')

    return true
  } catch (error) {
    console.error('highlight.js 模块化加载失败:', error)
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
 * 使用 npm 包动态导入，支持 Tree Shaking
 */
export async function loadClipboardResources() {
  if (isClipboardLoaded()) {
    return true
  }

  try {
    const ClipboardJS = await import('clipboard')
    window.ClipboardJS = ClipboardJS.default || ClipboardJS
    return true
  } catch (error) {
    console.error('Clipboard.js 加载失败:', error)
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

    // 动态加载 KaTeX CSS（仍使用外部文件，因为 Vite 对 CSS 动态导入支持有限）
    await loadExternalResource('/libs/css/katex.min.css', 'css')

    return true
  } catch (error) {
    console.error('KaTeX 加载失败:', error)
    return false
  }
}

/**
 * 检查Markdown-it是否已加载（全局版本）
 * 使用 markdownItLoader 模块统一管理
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
    // 动态导入 markdownItLoader 模块
    const { loadMarkdownIt } = await import('@/utils/markdownItLoader.js')
    await loadMarkdownIt()
    return true
  } catch (error) {
    console.warn('Failed to load markdown-it:', error)
    return false
  }
}
