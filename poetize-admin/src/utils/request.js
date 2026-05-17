import axios from "axios";
import constant from "./constant";
import cryptoUtil from "./crypto";

import router from "../router";
import { handleTokenExpire } from "./tokenExpireHandler";

// 缓存浏览器指纹，避免每次请求都重新计算
let cachedFingerprint = null;
let fingerprintPromise = null;

/**
 * 获取缓存的浏览器指纹
 * 使用Promise缓存确保并发请求不会重复计算指纹
 */
async function getCachedFingerprint() {
  if (cachedFingerprint) {
    return cachedFingerprint;
  }

  if (!fingerprintPromise) {
    fingerprintPromise = import('./fingerprintUtil')
      .then(({ getBrowserFingerprint }) => getBrowserFingerprint())
      .then(fp => {
        cachedFingerprint = fp;
        return fp;
      })
      .catch(err => {
        console.warn('获取浏览器指纹失败:', err);
        fingerprintPromise = null; // 重置，允许重试
        return null;
      });
  }

  return fingerprintPromise;
}

// 缓存翻译配置，避免重复请求
let cachedTranslationConfig = null;
let configCacheTime = 0;
const CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存
const FINGERPRINT_URL_PATTERNS = [
  '/user/regist',
  '/user/login',
  '/user/thirdLogin',
  '/user/getCodeForRegister',
  '/user/getCodeForBind',
  '/user/getCodeForForgetPassword',
  '/user/updateForForgetPassword',
  '/captcha/verify-checkbox',
  '/captcha/verify-slide',
  '/captcha/verify-token'
];

function getRequestPath(url) {
  if (!url) return '';

  try {
    if (/^https?:\/\//.test(url)) {
      return new URL(url).pathname;
    }

    return new URL(url, constant.baseURL).pathname;
  } catch (error) {
    return String(url).split('?')[0];
  }
}

function shouldAttachFingerprint(config) {
  const requestPath = getRequestPath(config && config.url);
  return FINGERPRINT_URL_PATTERNS.some(pattern => requestPath.endsWith(pattern));
}

function buildFriendlyHttpErrorMessage(error) {
  const status = error && error.response && error.response.status;
  if (status === 413) {
    return '上传的文件超过服务器允许大小，请压缩后再上传，或联系管理员调整上传大小限制';
  }
  if (status === 415) {
    return '当前文件格式暂不支持，请更换文件格式后再试';
  }
  if (status === 408) {
    return '上传等待时间过长，请检查网络后重试';
  }
  if (status === 429) {
    return (error.response && error.response.data && error.response.data.message) || '操作过于频繁，请稍后再试';
  }
  if (status >= 500) {
    return '服务器处理失败，请稍后重试或联系管理员';
  }
  if (status >= 400) {
    return (error.response && error.response.data && error.response.data.message) || '请求处理失败，请检查后重试';
  }
  if (error && error.code === 'ECONNABORTED') {
    return '请求超时，请检查网络或文件大小后重试';
  }
  if (error && error.message === 'Network Error') {
    return '网络异常，无法连接服务，请检查网络或服务状态';
  }
  return '';
}

// 获取翻译配置中的超时时间
async function getTranslationTimeout() {
  const now = Date.now();

  // 如果缓存有效，直接使用
  if (cachedTranslationConfig && (now - configCacheTime) < CACHE_DURATION) {
    return cachedTranslationConfig.timeout || 30;
  }

  try {
    const response = await axios.get(constant.baseURL + '/webInfo/ai/config/articleAi/get', {
      timeout: 10000,
      withCredentials: true,
      headers: { 'User-Agent': 'axios' }
    });

    if (response.data && response.data.code === 200 && response.data.data) {
      // 从 Java 端 article_ai 配置中提取 llmConfig JSON 的 timeout
      const llmConfigStr = response.data.data.llmConfig;
      let timeout = 30;
      if (llmConfigStr) {
        try {
          const llmConfig = typeof llmConfigStr === 'string' ? JSON.parse(llmConfigStr) : llmConfigStr;
          timeout = llmConfig.timeout || 30;
        } catch (e) {
          // 解析失败使用默认值
        }
      }
      cachedTranslationConfig = { timeout };
      configCacheTime = now;
      return timeout;
    }
  } catch (error) {
    // 静默失败，使用默认值
  }

  // 如果获取失败，返回默认值
  return 30;
}

// 超时配置常量
const TIMEOUT_CONFIG = {
  DEFAULT: 60000,           // 默认60秒
  SEO: 30000,               // SEO请求30秒
  TRANSLATION: 120000,      // 翻译请求2分钟
  ARTICLE_SAVE: 300000,     // 文章保存最少5分钟
  ARTICLE_BUFFER: 30        // 文章保存缓冲时间（秒）
};

// 设置请求基本配置
axios.defaults.baseURL = constant.baseURL;
axios.defaults.timeout = TIMEOUT_CONFIG.DEFAULT;
axios.defaults.withCredentials = true;

/**
 * 根据URL路径设置超时时间
 * @param {Object} config - axios请求配置
 * @returns {Object} 处理后的配置
 */
function configureTimeout(config) {
  if (!config.url) return config;

  const url = config.url;
  const isDefaultTimeout = !config.timeout || config.timeout === TIMEOUT_CONFIG.DEFAULT;

  // SEO请求：短超时 + 重试机制
  if (url.includes('/seo/')) {
    config.timeout = TIMEOUT_CONFIG.SEO;
    config.retry = 3;
    config.retryDelay = 1000;

    // 防止请求卡住
    const source = axios.CancelToken.source();
    config.cancelToken = source.token;
    setTimeout(() => source.cancel('SEO请求超时自动取消'), TIMEOUT_CONFIG.SEO + 5000);

    return config;
  }

  // 文章保存/更新：动态超时（根据翻译配置）
  if (url.includes('/article/saveArticle') || url.includes('/article/updateArticle')) {
    if (isDefaultTimeout) {
      const cachedTimeout = cachedTranslationConfig?.timeout || 30;
      const dynamicTimeout = (cachedTimeout + TIMEOUT_CONFIG.ARTICLE_BUFFER) * 1000;
      config.timeout = Math.max(dynamicTimeout, TIMEOUT_CONFIG.ARTICLE_SAVE);
    }
    return config;
  }

  // 翻译API：长超时（AI处理需要更多时间）
  if (url.includes('/api/translation/')) {
    if (isDefaultTimeout) {
      config.timeout = TIMEOUT_CONFIG.TRANSLATION;
    }
    return config;
  }

  return config;
}

// 添加请求拦截器
axios.interceptors.request.use(async function (config) {
  // 统一处理超时配置
  config = configureTimeout(config);

  // 确保headers对象存在
  if (!config.headers) {
    config.headers = {};
  }

  // 添加浏览器指纹到请求头（用于限流）
  if (shouldAttachFingerprint(config)) {
    try {
      const fingerprint = await getCachedFingerprint();
      if (fingerprint) {
        config.headers['X-Fingerprint'] = fingerprint;
      }
    } catch (err) {
      // 指纹获取失败不阻塞请求
      console.debug('添加指纹到请求头失败:', err);
    }
  }

  // 如果是验证码相关的请求，不需要token
  if (config.url && config.url.includes('/captcha/')) {
    // 特殊处理：getBlockedIps 接口需要管理员token，不能跳过
    if (config.url.includes('/captcha/getBlockedIps')) {
      // 这个接口需要token，继续执行token添加逻辑
    } else {
      // 对于验证码配置请求，添加加密处理
      if (config.url.includes('/captcha/getConfig') && config.method === 'get') {
        // 为GET请求添加加密标识
        if (!config.params) {
          config.params = {};
        }
        config.params.encrypted = 'true';
      }
      // 对于验证码验证请求，添加加密处理
      else if ((config.url.includes('/captcha/verify-checkbox') ||
        config.url.includes('/captcha/verify-slide')) &&
        config.method === 'post' && config.data) {
        // 检查数据是否已经加密
        if (config.data.encrypted) {
          // 数据已经加密，直接使用
          // console.log('数据已加密，跳过重复加密');
        } else {
          // 加密请求数据
          try {
            const encryptedData = await cryptoUtil.encrypt(config.data);
            if (encryptedData) {
              config.data = {
                encrypted: encryptedData
              };
            }
          } catch (error) {
            // 检查是否是 Web Crypto API 不可用的错误
            if (error.code === 'CRYPTO_NOT_AVAILABLE') {
              // 抛出用户友好的错误，阻止请求发送
              const friendlyError = new Error('安全验证需要 HTTPS 连接。请使用 HTTPS 访问此网站，或联系站长启用 HTTPS。');
              friendlyError.isHttpsRequired = true;
              throw friendlyError;
            }
            console.error('加密验证码验证请求失败:', error);
            // 其他加密失败也阻止请求，避免发送未加密数据
            throw new Error('安全验证失败，请刷新页面重试');
          }
        }
      }
      return config;
    }
  }

  // Cookie-based auth: withCredentials already set globally, no need to inject token header
  config.withCredentials = true;

  return config;
}, function (error) {
  return Promise.reject(error);
});

// 添加响应拦截器
axios.interceptors.response.use(async function (response) {
  if (response.data !== null && response.data.hasOwnProperty("code") && response.data.code !== 200) {
    // 处理限流响应 (429)
    if (response.data.code === 429) {
      const retryAfter = response.headers['retry-after'] || 60;
      const error = new Error(response.data.message || '操作过于频繁，请稍后再试');
      error.code = 429;
      error.retryAfter = parseInt(retryAfter, 10);
      error.isRateLimited = true;

      // 显示限流提示（使用全局消息提示，如果可用）
      if (typeof window !== 'undefined' && window.$message) {
        window.$message.warning(`${error.message}（${retryAfter}秒后可重试）`);
      } else if (typeof window !== 'undefined' && window.ElMessage) {
        window.ElMessage.warning(`${error.message}（${retryAfter}秒后可重试）`);
      }

      return Promise.reject(error);
    }

    if (response.data.code === 300 || response.data.code === 401) {
      // token失效，使用统一的token过期处理逻辑
      const isAdminRequest = response.config.isAdmin || false;
      handleTokenExpire(isAdminRequest, router.currentRoute.fullPath, {
        showMessage: true
      });
    }
    const error = new Error(response.data.message || '请求失败');
    error.code = response.data.code;
    error.data = response.data.data;
    error.responseData = response.data;
    return Promise.reject(error);
  }

  // 处理验证码配置响应的解密
  if (response.config.url && response.config.url.includes('/captcha/getConfig') &&
    response.config.method === 'get' && response.data && response.data.data) {
    try {
      // 检查响应是否加密
      if (response.data.data.encrypted) {
        // 使用decryptBase64方法解密验证码配置响应数据
        const decryptedData = await cryptoUtil.decryptBase64(response.data.data.encrypted);
        if (decryptedData) {
          response.data.data = decryptedData;
        }
      }
    } catch (error) {
      console.error('解密验证码配置失败:', error);
      // 解密失败时返回原始数据
    }
  }

  // 处理验证码验证响应的解密
  if (response.config.url &&
    (response.config.url.includes('/captcha/verify-checkbox') ||
      response.config.url.includes('/captcha/verify-slide')) &&
    response.config.method === 'post' && response.data && response.data.data) {
    try {
      // 检查响应是否加密
      if (response.data.data.encrypted) {
        // 使用decryptBase64方法解密验证码验证响应数据
        const decryptedData = await cryptoUtil.decryptBase64(response.data.data.encrypted);
        if (decryptedData) {
          response.data.data = decryptedData;
        }
      }
    } catch (error) {
      console.error('解密验证码验证响应失败:', error);
      // 解密失败时返回原始数据
    }
  }

  // 处理登录接口响应的解密
  if (response.config.url &&
    response.config.url.includes('/user/login') &&
    response.config.method === 'post' && response.data && response.data.data &&
    response.data.data.data) {
    try {
      // 使用decryptBase64方法解密登录响应数据
      const decryptedData = await cryptoUtil.decryptBase64(response.data.data.data);
      if (decryptedData) {
        response.data.data = decryptedData;
      }
    } catch (error) {
      console.error('解密登录响应失败:', error);
      // 解密失败时返回原始数据
    }
  }

  return response;
}, function (error) {
  // 处理网络错误
  if (error.response) {
    // 处理HTTP状态码429（限流）
    if (error.response.status === 429) {
      const retryAfter = error.response.headers['retry-after'] || 60;
      const message = error.response.data?.message || '操作过于频繁，请稍后再试';

      error.isRateLimited = true;
      error.retryAfter = parseInt(retryAfter, 10);
      error.message = message;

      // 显示限流提示
      if (typeof window !== 'undefined' && window.$message) {
        window.$message.warning(`${message}（${retryAfter}秒后可重试）`);
      } else if (typeof window !== 'undefined' && window.ElMessage) {
        window.ElMessage.warning(`${message}（${retryAfter}秒后可重试）`);
      }

      return Promise.reject(error);
    }

    // 服务器返回错误状态码
    if (error.response.status === 401 || error.response.status === 403) {
      // token相关错误，使用统一的token过期处理逻辑
      const isAdminRequest = error.config && error.config.isAdmin || false;
      handleTokenExpire(isAdminRequest, router.currentRoute.fullPath, {
        showMessage: true
      });
    }

    const friendlyMessage = buildFriendlyHttpErrorMessage(error);
    if (friendlyMessage) {
      error.message = friendlyMessage;
      error.friendlyMessage = friendlyMessage;
      error.status = error.response.status;
      return Promise.reject(error);
    }
  } else {
    const friendlyMessage = buildFriendlyHttpErrorMessage(error);
    if (friendlyMessage) {
      error.message = friendlyMessage;
      error.friendlyMessage = friendlyMessage;
    }
  }
  return Promise.reject(error);
});

// 当data为URLSearchParams对象时设置为application/x-www-form-urlencoded;charset=utf-8
// 当data为普通对象时，会被设置为application/json;charset=utf-8

// 导出工具函数
export { getTranslationTimeout };

export default {
  post(url, params = {}, isAdmin = false, json = true) {
    if (params === null || typeof params !== 'object') {
      params = {};
    }
    let config = {
      isAdmin: isAdmin,
      headers: {}
    };

    // 注意：token处理已移至请求拦截器中统一处理，此处不再重复处理

    // 显式设置Content-Type，确保JSON请求正确识别
    if (json) {
      config.headers['Content-Type'] = 'application/json;charset=UTF-8';
    }

    // 如果不是json格式，将参数转换为URLSearchParams
    const data = json ? params : new URLSearchParams(params);

    return new Promise((resolve, reject) => {
      axios
        .post(url, data, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  get(url, params = {}, isAdmin = false) {
    // 防御：如果调用方传入 null / 非对象，转为空对象，避免 axios 1.x toFormData 报错
    if (params === null || typeof params !== 'object') {
      params = {};
    }
    // Axios 1.x 要求 headers 必须是纯对象，不能为 undefined。
    // 先生成基础配置，然后在有 token 时再补充 headers，避免 "target must be an object" 错误。
    const config = {
      params,
      isAdmin,
      headers: {}
    };

    // 注意：token处理已移至请求拦截器中统一处理，此处不再重复处理

    return new Promise((resolve, reject) => {
      axios
        .get(url, config)
        .then((res) => resolve(res.data))
        .catch((err) => reject(err));
    });
  },

  put(url, params = {}, isAdmin = false, json = true) {
    if (params === null || typeof params !== 'object') {
      params = {}
    }
    const config = {
      isAdmin,
      headers: {}
    }
    if (json) {
      config.headers['Content-Type'] = 'application/json;charset=UTF-8'
    }
    const data = json ? params : new URLSearchParams(params)
    return new Promise((resolve, reject) => {
      axios
        .put(url, data, config)
        .then(res => resolve(res.data))
        .catch(err => reject(err))
    })
  },

  delete(url, params = {}, isAdmin = false) {
    if (params === null || typeof params !== 'object') {
      params = {}
    }
    return new Promise((resolve, reject) => {
      axios
        .delete(url, {
          params,
          isAdmin,
          headers: {}
        })
        .then(res => resolve(res.data))
        .catch(err => reject(err))
    })
  },

  upload(url, param, isAdmin = false, option) {
    let config = {
      isAdmin: isAdmin,
      headers: { "Content-Type": "multipart/form-data" },
      timeout: option && option.timeout ? option.timeout : 60000
    };

    // 注意：token处理已移至请求拦截器中统一处理，此处不再重复处理

    if (typeof option !== "undefined") {
      config.onUploadProgress = progressEvent => {
        if (progressEvent.total > 0) {
          progressEvent.percent = progressEvent.loaded / progressEvent.total * 100;
        }
        option.onProgress(progressEvent);
      };
    }

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  uploadQiniu(url, param) {
    let config = {
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 60000
    };

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
