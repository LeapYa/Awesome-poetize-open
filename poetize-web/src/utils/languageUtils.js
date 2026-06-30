/**
 * 语言工具模块
 * 提供统一的语言映射功能，从Java后端API获取
 *
 * 两套映射：
 * 1. 前台展示用（getLanguageMapping） - 使用原生语言文字，如 English, 日本語
 * 2. 后台管理用（getAdminLanguageMapping） - 使用中文翻译，如 英文, 日文
 */

import axios from 'axios'
import constant from './constant.js'

// 默认语言映射 - 前台展示用（原生语言文字）
const DEFAULT_LANGUAGE_MAP = {
  zh: '中文',
  'zh-TW': '繁體中文',
  en: 'English',
  ja: '日本語',
  ko: '한국어',
  fr: 'Français',
  de: 'Deutsch',
  es: 'Español',
  ru: 'Русский',
  pt: 'Português',
  it: 'Italiano',
  ar: 'العربية',
  th: 'ไทย',
  vi: 'Tiếng Việt',
  auto: 'Auto Detect',
}

// 默认语言映射 - 后台管理用（中文）
const DEFAULT_ADMIN_LANGUAGE_MAP = {
  zh: '中文',
  'zh-TW': '繁体中文',
  en: '英文',
  ja: '日文',
  ko: '韩文',
  fr: '法文',
  de: '德文',
  es: '西班牙文',
  ru: '俄文',
  pt: '葡萄牙文',
  it: '意大利文',
  ar: '阿拉伯文',
  th: '泰文',
  vi: '越南文',
  auto: '自动检测',
}

// 目录标题映射（各语言中"目录"的翻译）
const TOC_TITLE_MAP = {
  zh: '目录',
  'zh-TW': '目錄',
  en: 'Index',
  ja: '目次',
  ko: '목차',
  fr: 'Table des matières',
  de: 'Inhaltsverzeichnis',
  es: 'Índice',
  ru: 'Содержание',
  pt: 'Índice',
  it: 'Indice',
  ar: 'الفهرس',
  th: 'สารบัญ',
  vi: 'Mục lục',
  auto: 'Index',
}

// 缓存语言映射，避免频繁请求
let cachedLanguageMap = null
let cachedAdminLanguageMap = null
let isLoading = false
let isAdminLoading = false
let loadPromise = null
let loadAdminPromise = null

const ARTICLE_DEFAULT_LANG_KEY = 'articleDefaultLanguages'
const LANGUAGE_CACHE_MAX_AGE = 24 * 60 * 60 * 1000

/**
 * 获取语言映射配置
 * 优先从数据库读取，失败则使用默认配置
 *
 * @returns {Promise<Object>} 语言代码到自然语言名称的映射对象
 */
export async function getLanguageMapping() {
  // 如果有缓存，直接返回
  if (cachedLanguageMap !== null) {
    return cachedLanguageMap
  }

  // 如果正在加载，等待加载完成
  if (isLoading && loadPromise) {
    return loadPromise
  }

  // 开始加载
  isLoading = true
  loadPromise = (async () => {
    try {
      const response = await axios.get(
        constant.baseURL + '/webInfo/ai/config/system/languageMapping'
      )

      if (response.data && response.data.code === 200 && response.data.data) {
        cachedLanguageMap = response.data.data
        return cachedLanguageMap
      }

      cachedLanguageMap = DEFAULT_LANGUAGE_MAP
      return cachedLanguageMap
    } catch (error) {
      cachedLanguageMap = DEFAULT_LANGUAGE_MAP
      return cachedLanguageMap
    } finally {
      isLoading = false
      loadPromise = null
    }
  })()

  return loadPromise
}

/**
 * 获取后台管理用语言映射配置（中文）
 * 优先从数据库读取，失败则使用默认配置
 *
 * @returns {Promise<Object>} 语言代码到中文名称的映射对象
 */
export async function getAdminLanguageMapping() {
  // 如果有缓存，直接返回
  if (cachedAdminLanguageMap !== null) {
    return cachedAdminLanguageMap
  }

  // 如果正在加载，等待加载完成
  if (isAdminLoading && loadAdminPromise) {
    return loadAdminPromise
  }

  // 开始加载
  isAdminLoading = true
  loadAdminPromise = (async () => {
    try {
      const response = await axios.get(
        constant.baseURL + '/webInfo/ai/config/system/languageMappingAdmin'
      )

      if (response.data && response.data.code === 200 && response.data.data) {
        cachedAdminLanguageMap = response.data.data
        return cachedAdminLanguageMap
      }

      cachedAdminLanguageMap = DEFAULT_ADMIN_LANGUAGE_MAP
      return cachedAdminLanguageMap
    } catch (error) {
      cachedAdminLanguageMap = DEFAULT_ADMIN_LANGUAGE_MAP
      return cachedAdminLanguageMap
    } finally {
      isAdminLoading = false
      loadAdminPromise = null
    }
  })()

  return loadAdminPromise
}

/**
 * 同步获取语言映射（使用缓存或默认值）
 *
 * @returns {Object} 语言代码到自然语言名称的映射对象
 */
export function getLanguageMappingSync() {
  return cachedLanguageMap || DEFAULT_LANGUAGE_MAP
}

export function applyLanguageBootstrap(bootstrap = {}) {
  if (bootstrap.languageMap && typeof bootstrap.languageMap === 'object') {
    cachedLanguageMap = bootstrap.languageMap
  }

  if (
    bootstrap.articleDefaultLanguages &&
    typeof bootstrap.articleDefaultLanguages === 'object'
  ) {
    localStorage.setItem(
      ARTICLE_DEFAULT_LANG_KEY,
      JSON.stringify({
        timestamp: Date.now(),
        data: bootstrap.articleDefaultLanguages,
      })
    )
  }
}

export function getCachedArticleDefaultLanguages() {
  const cached = localStorage.getItem(ARTICLE_DEFAULT_LANG_KEY)
  if (!cached) return null

  try {
    const parsed = JSON.parse(cached)
    if (!parsed || !parsed.timestamp || !parsed.data) return null
    if (Date.now() - parsed.timestamp > LANGUAGE_CACHE_MAX_AGE) {
      localStorage.removeItem(ARTICLE_DEFAULT_LANG_KEY)
      return null
    }
    return parsed.data
  } catch (error) {
    localStorage.removeItem(ARTICLE_DEFAULT_LANG_KEY)
    return null
  }
}

/**
 * 同步获取后台管理语言映射（使用缓存或默认值）
 *
 * @returns {Object} 语言代码到中文名称的映射对象
 */
export function getAdminLanguageMappingSync() {
  return cachedAdminLanguageMap || DEFAULT_ADMIN_LANGUAGE_MAP
}

/**
 * 获取语言代码对应的自然语言名称（前台展示用，原生语言）
 *
 * @param {string} langCode - 语言代码，如 'zh', 'en'
 * @returns {string} 自然语言名称，如 '中文', 'English'
 */
export function getLanguageName(langCode) {
  const mapping = getLanguageMappingSync()
  return mapping[langCode] || langCode
}

/**
 * 获取语言代码对应的中文名称（后台管理用）
 *
 * @param {string} langCode - 语言代码，如 'zh', 'en'
 * @returns {string} 中文名称，如 '中文', '英文'
 */
export function getAdminLanguageName(langCode) {
  const mapping = getAdminLanguageMappingSync()
  return mapping[langCode] || langCode
}

/**
 * 清除语言映射缓存（当数据库配置更新时调用）
 */
export function clearLanguageMappingCache() {
  cachedLanguageMap = null
  cachedAdminLanguageMap = null
}

/**
 * 预加载语言映射（在应用初始化时调用）
 *
 * @param {boolean} includeAdmin - 是否同时预加载后台管理映射
 */
export async function preloadLanguageMapping(includeAdmin = false) {
  try {
    await getLanguageMapping()

    if (includeAdmin) {
      await getAdminLanguageMapping()
    }
  } catch (error) {}
}

/**
 * 获取目录标题（根据语言代码）
 *
 * @param {string} langCode - 语言代码，如 'zh', 'en'
 * @returns {string} 目录标题，如 '目录', 'Index'
 */
export function getTocTitle(langCode) {
  return TOC_TITLE_MAP[langCode] || TOC_TITLE_MAP['en']
}
