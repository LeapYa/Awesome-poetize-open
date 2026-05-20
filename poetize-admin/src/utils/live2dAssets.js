import constant from '@/utils/constant'

const DEFAULT_LIVE2D_CDN_BASE_URLS = [
  'https://cdn.jsdelivr.net/gh/fghrsh/live2d_api/',
  'https://fastly.jsdelivr.net/gh/fghrsh/live2d_api/',
  'https://gcore.jsdelivr.net/gh/fghrsh/live2d_api/'
]

const LAST_WORKING_LIVE2D_BASE_URL_KEY = 'live2d-working-base-url'
const DEFAULT_MODEL_PROBE_TIMEOUT_MS = 12000
const LOCAL_MODEL_PROBE_TIMEOUT_MS = 60000

export function normalizeLive2DBaseUrl(baseUrl) {
  const fallback = constant.remoteLive2dApiPath || constant.cdnPath
  const value = (baseUrl || fallback || '').trim()
  return value.endsWith('/') ? value : `${value}/`
}

export function getDefaultLive2DBaseUrl() {
  return normalizeLive2DBaseUrl(constant.remoteLive2dApiPath || constant.cdnPath)
}

export function buildLive2DModelUrl(baseUrl, modelPath) {
  const cleanModelPath = String(modelPath || '').replace(/^\/+/, '')
  return `${normalizeLive2DBaseUrl(baseUrl)}model/${cleanModelPath}/index.json`
}

export function getLive2DBaseUrlCandidates(preferredBaseUrl, options = {}) {
  const preferred = normalizeLive2DBaseUrl(preferredBaseUrl)
  const allowRemoteFallback = options.allowRemoteFallback !== false

  if (!allowRemoteFallback && isLocalBaseUrl(preferred)) {
    return [preferred]
  }

  const saved = getSavedWorkingBaseUrl()
  const configuredUrls = [
    constant.remoteLive2dApiPath,
    constant.cdnPath
  ]

  const preferredIsDefaultCdn = DEFAULT_LIVE2D_CDN_BASE_URLS
    .map((url) => normalizeLive2DBaseUrl(url))
    .includes(preferred)

  const candidates = isLocalBaseUrl(preferred)
    ? [preferred, saved, ...configuredUrls, ...DEFAULT_LIVE2D_CDN_BASE_URLS]
    : preferredIsDefaultCdn
      ? [saved, ...DEFAULT_LIVE2D_CDN_BASE_URLS, preferred, ...configuredUrls]
      : [saved, preferred, ...configuredUrls, ...DEFAULT_LIVE2D_CDN_BASE_URLS]

  return uniqueNormalizedBaseUrls(candidates)
}

export async function resolveLive2DModelUrl(baseUrl, modelPath, options = {}) {
  const timeoutMs = options.timeoutMs || (
    options.allowRemoteFallback === false ? LOCAL_MODEL_PROBE_TIMEOUT_MS : DEFAULT_MODEL_PROBE_TIMEOUT_MS
  )
  const candidates = getLive2DBaseUrlCandidates(baseUrl, options)
  const failures = []

  for (const candidate of candidates) {
    try {
      const modelUrl = buildLive2DModelUrl(candidate, modelPath)
      if (options.skipValidation === true) {
        saveWorkingBaseUrl(candidate)
        return { baseUrl: candidate, modelUrl, failures }
      }
      await assertLive2DModelLoadable(modelUrl, timeoutMs)
      saveWorkingBaseUrl(candidate)
      return { baseUrl: candidate, modelUrl, failures }
    } catch (error) {
      failures.push({
        baseUrl: candidate,
        message: error?.message || String(error)
      })
    }
  }

  const fallbackBaseUrl = normalizeLive2DBaseUrl(baseUrl)
  return {
    baseUrl: fallbackBaseUrl,
    modelUrl: buildLive2DModelUrl(fallbackBaseUrl, modelPath),
    failures
  }
}

export async function preloadLive2DModel(baseUrl, modelPath, options = {}) {
  const timeoutMs = options.timeoutMs || (
    options.allowRemoteFallback === false ? LOCAL_MODEL_PROBE_TIMEOUT_MS : DEFAULT_MODEL_PROBE_TIMEOUT_MS
  )
  const candidates = getLive2DBaseUrlCandidates(baseUrl, options)
  const onProgress = typeof options.onProgress === 'function'
    ? options.onProgress
    : () => {}
  const progressReporter = createStableProgressReporter(onProgress)
  const failures = []

  for (let index = 0; index < candidates.length; index++) {
    const candidate = candidates[index]
    const modelUrl = buildLive2DModelUrl(candidate, modelPath)

    try {
      progressReporter(5, '正在连接模型资源')
      await preloadLive2DModelAssets(modelUrl, timeoutMs, progressReporter)
      saveWorkingBaseUrl(candidate)
      progressReporter(100, '模型资源加载完成')
      return { baseUrl: candidate, modelUrl, failures }
    } catch (error) {
      failures.push({
        baseUrl: candidate,
        message: error?.message || String(error)
      })
      if (index < candidates.length - 1) {
        progressReporter(15, '当前线路有点慢，正在换个入口')
      }
    }
  }

  throw new Error('所有 Live2D 模型资源线路都加载失败')
}

export async function fetchLive2DAssetStatus() {
  try {
    const response = await fetch(`${constant.baseURL}/webInfo/live2d/assets/status`)
    const result = await response.json()
    if (result.code === 200 && result.data) {
      return {
        ...result.data,
        modelBaseUrl: normalizeLive2DBaseUrl(result.data.modelBaseUrl)
      }
    }
  } catch (error) {
    console.warn('获取 Live2D 资源状态失败，使用 CDN:', error)
  }

  return {
    installed: false,
    ready: false,
    widgetRuntimeExists: false,
    localBaseUrl: constant.localLive2dApiPath,
    cdnBaseUrl: getDefaultLive2DBaseUrl(),
    modelBaseUrl: getDefaultLive2DBaseUrl()
  }
}

async function assertLive2DModelLoadable(modelUrl, timeoutMs) {
  const absoluteModelUrl = toAbsoluteUrl(modelUrl)
  const response = await fetchWithTimeout(absoluteModelUrl, { cache: 'no-cache' }, timeoutMs)

  if (!response.ok) {
    throw new Error(`model json responded with ${response.status}`)
  }

  const modelConfig = await response.json()
  const modelDirUrl = absoluteModelUrl.slice(0, absoluteModelUrl.lastIndexOf('/') + 1)
  const checks = []

  if (modelConfig.model) {
    checks.push(assertResourceReachable(resolveModelResourceUrl(modelConfig.model, modelDirUrl), timeoutMs))
  }

  if (Array.isArray(modelConfig.textures) && modelConfig.textures.length > 0) {
    checks.push(...modelConfig.textures.map((texture) => {
      return loadImageWithTimeout(resolveModelResourceUrl(texture, modelDirUrl), timeoutMs)
    }))
  } else {
    throw new Error('model json does not declare textures')
  }

  await Promise.all(checks)
}

async function preloadLive2DModelAssets(modelUrl, timeoutMs, onProgress) {
  const absoluteModelUrl = toAbsoluteUrl(modelUrl)
  reportProgress(onProgress, 3, '正在读取模型配置')

  const response = await fetchWithTimeout(absoluteModelUrl, { cache: 'no-cache' }, timeoutMs)
  if (!response.ok) {
    throw new Error(`model json responded with ${response.status}`)
  }

  const modelConfig = await response.json()
  const modelDirUrl = absoluteModelUrl.slice(0, absoluteModelUrl.lastIndexOf('/') + 1)
  const resources = collectLive2DModelResources(modelConfig, modelDirUrl)

  if (resources.length === 0) {
    throw new Error('model json does not declare loadable resources')
  }

  reportProgress(onProgress, 10, '模型配置读取完成')

  for (let index = 0; index < resources.length; index++) {
    const resource = resources[index]
    if (resource.type === 'image') {
      await loadImageWithTimeout(resource.url, timeoutMs)
    } else {
      const assetResponse = await fetchWithTimeout(resource.url, { cache: 'force-cache' }, timeoutMs)
      if (!assetResponse.ok) {
        throw new Error(`${resource.url} responded with ${assetResponse.status}`)
      }
      await assetResponse.arrayBuffer()
    }

    const progress = 10 + Math.round(((index + 1) / resources.length) * 90)
    reportProgress(onProgress, progress, resource.label)
  }
}

function collectLive2DModelResources(modelConfig, modelDirUrl) {
  const resources = []
  const addFetch = (resourcePath, label) => {
    if (!resourcePath) return
    resources.push({
      type: 'fetch',
      url: resolveModelResourceUrl(resourcePath, modelDirUrl),
      label
    })
  }
  const addImage = (resourcePath, label) => {
    if (!resourcePath) return
    resources.push({
      type: 'image',
      url: resolveModelResourceUrl(resourcePath, modelDirUrl),
      label
    })
  }

  addFetch(modelConfig.model, '正在加载模型骨架')

  if (Array.isArray(modelConfig.textures) && modelConfig.textures.length > 0) {
    modelConfig.textures.forEach((texture) => addImage(texture, '正在加载模型贴图'))
  } else {
    throw new Error('model json does not declare textures')
  }

  return resources
}

function createStableProgressReporter(onProgress) {
  let lastProgress = 0
  return (progress, message) => {
    const payload = typeof progress === 'object' && progress !== null
      ? progress
      : { progress, message }
    const nextProgress = Math.max(lastProgress, Math.round(payload.progress || 0))
    lastProgress = nextProgress
    reportProgress(onProgress, nextProgress, payload.message)
  }
}

function reportProgress(onProgress, progress, message) {
  onProgress({
    progress: Math.max(0, Math.min(100, Math.round(progress))),
    message
  })
}

async function assertResourceReachable(url, timeoutMs) {
  const response = await fetchWithTimeout(url, { method: 'HEAD', cache: 'no-cache' }, timeoutMs)
  if (!response.ok) {
    throw new Error(`${url} responded with ${response.status}`)
  }
}

function loadImageWithTimeout(url, timeoutMs) {
  if (typeof Image === 'undefined') {
    return assertResourceReachable(url, timeoutMs)
  }

  return new Promise((resolve, reject) => {
    const image = new Image()
    const timer = window.setTimeout(() => {
      image.onload = null
      image.onerror = null
      image.src = ''
      reject(new Error(`image timed out: ${url}`))
    }, timeoutMs)

    image.crossOrigin = 'Anonymous'
    image.onload = () => {
      window.clearTimeout(timer)
      resolve(url)
    }
    image.onerror = () => {
      window.clearTimeout(timer)
      reject(new Error(`image failed: ${url}`))
    }
    image.src = url
  })
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    return await fetch(url, {
      ...options,
      signal: controller.signal
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`request timed out: ${url}`)
    }
    throw error
  } finally {
    window.clearTimeout(timer)
  }
}

function resolveModelResourceUrl(resourcePath, modelDirUrl) {
  return new URL(resourcePath, modelDirUrl).href
}

function toAbsoluteUrl(url) {
  const base = typeof window === 'undefined' ? 'http://localhost/' : window.location.href
  return new URL(url, base).href
}

function uniqueNormalizedBaseUrls(urls) {
  const seen = new Set()
  const result = []

  urls.filter(Boolean).forEach((url) => {
    const normalized = normalizeLive2DBaseUrl(url)
    if (!seen.has(normalized)) {
      seen.add(normalized)
      result.push(normalized)
    }
  })

  return result
}

function getSavedWorkingBaseUrl() {
  try {
    return window.localStorage.getItem(LAST_WORKING_LIVE2D_BASE_URL_KEY)
  } catch (error) {
    return ''
  }
}

function saveWorkingBaseUrl(baseUrl) {
  try {
    window.localStorage.setItem(LAST_WORKING_LIVE2D_BASE_URL_KEY, normalizeLive2DBaseUrl(baseUrl))
  } catch (error) {
    // Storage can be unavailable in privacy modes; loading should still work.
  }
}

function isLocalBaseUrl(baseUrl) {
  if (baseUrl.startsWith('/')) return true
  return typeof window !== 'undefined' && baseUrl.startsWith(window.location.origin)
}
