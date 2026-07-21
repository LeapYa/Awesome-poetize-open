/**
 * 语言工具模块
 * 提供统一的语言映射功能，从Java后端API获取
 * 
 * 后台管理用（getAdminLanguageMapping） - 使用中文翻译，如 英文, 日文
 */

import axios from 'axios';
import constant from './constant.js';


// 默认语言映射 - 后台管理用（中文）
const DEFAULT_ADMIN_LANGUAGE_MAP = {
  'zh': '中文',
  'zh-TW': '繁体中文',
  'en': '英文',
  'ja': '日文',
  'ko': '韩文',
  'fr': '法文',
  'de': '德文',
  'es': '西班牙文',
  'ru': '俄文',
  'pt': '葡萄牙文',
  'it': '意大利文',
  'ar': '阿拉伯文',
  'th': '泰文',
  'vi': '越南文',
  'auto': '自动检测'
};

// 缓存语言映射，避免频繁请求
let cachedAdminLanguageMap = null;
let isAdminLoading = false;
let loadAdminPromise = null;

/**
 * 获取后台管理用语言映射配置（中文）
 * 优先从数据库读取，失败则使用默认配置
 * 
 * @returns {Promise<Object>} 语言代码到中文名称的映射对象
 */
export async function getAdminLanguageMapping() {
  // 如果有缓存，直接返回
  if (cachedAdminLanguageMap !== null) {
    return cachedAdminLanguageMap;
  }

  // 如果正在加载，等待加载完成
  if (isAdminLoading && loadAdminPromise) {
    return loadAdminPromise;
  }

  // 开始加载
  isAdminLoading = true;
  loadAdminPromise = (async () => {
    try {

      const response = await axios.get(constant.baseURL + '/webInfo/ai/config/system/languageMappingAdmin');

      if (response.data && response.data.code === 200 && response.data.data) {
        cachedAdminLanguageMap = response.data.data;
        return cachedAdminLanguageMap;
      }

      cachedAdminLanguageMap = DEFAULT_ADMIN_LANGUAGE_MAP;
      return cachedAdminLanguageMap;

    } catch (error) {
      cachedAdminLanguageMap = DEFAULT_ADMIN_LANGUAGE_MAP;
      return cachedAdminLanguageMap;
    } finally {
      isAdminLoading = false;
      loadAdminPromise = null;
    }
  })();

  return loadAdminPromise;
}

/**
 * 同步获取后台管理语言映射（使用缓存或默认值）
 * 
 * @returns {Object} 语言代码到中文名称的映射对象
 */
export function getAdminLanguageMappingSync() {
  return cachedAdminLanguageMap || DEFAULT_ADMIN_LANGUAGE_MAP;
}

/**
 * 获取语言代码对应的中文名称（后台管理用）
 * 
 * @param {string} langCode - 语言代码，如 'zh', 'en'
 * @returns {string} 中文名称，如 '中文', '英文'
 */
export function getAdminLanguageName(langCode) {
  const mapping = getAdminLanguageMappingSync();
  return mapping[langCode] || langCode;
}

/**
 * 清除语言映射缓存（当数据库配置更新时调用）
 */
export function clearLanguageMappingCache() {
  cachedAdminLanguageMap = null;
}

/**
 * 预加载语言映射（在应用初始化时调用）
 */
export async function preloadLanguageMapping() {
  try {
    await getAdminLanguageMapping();
  } catch (error) {
  }
}
