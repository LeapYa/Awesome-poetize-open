/**
 * 验证码工具类
 */
import axios from 'axios'
import constant from '@/utils/constant'

/**
 * 检查指定操作是否需要验证码
 * @param {string} action - 操作类型: login, register, comment, reset_password
 * @returns {Promise<boolean>} - 是否需要验证码
 */
export function checkCaptchaRequired(action) {
  return new Promise((resolve, reject) => {
    // 使用Python服务器URL并直接在URL中传递action参数
    axios.get(`${constant.pythonBaseURL}/captcha/validate?action=${action}`)
      .then(res => {
        if (res && res.data && res.data.code === 200) {
          // 正确解析API返回的{required: true/false}格式
          const required = res.data.data && res.data.data.required === true;
          console.log(`验证码检查 - 操作: ${action}, 需要验证: ${required}`);
          resolve(required);
        } else {
          // 如果API出错，默认不需要验证（修改：使验证码出错时跳过验证，确保用户能登录）
          console.log(`验证码API返回错误码: ${res && res.data ? res.data.code : 'unknown'}, 默认不需要验证`);
          resolve(false);
        }
      })
      .catch(error => {
        console.error('验证码检查失败:', error);
        // 如果网络错误，默认不需要验证（确保在验证码服务失败时用户仍能登录）
        console.log('验证码服务不可用，默认不需要验证');
        resolve(false);
      });
  });
}

/**
 * 验证码状态本地缓存
 * 避免频繁请求API
 */
const captchaStatusCache = {
  data: {},
  timestamp: 0,
  // 缓存有效期5分钟
  TTL: 5 * 60 * 1000
}

/**
 * 检查指定操作是否需要验证码(带缓存)
 * @param {string} action - 操作类型
 * @returns {Promise<boolean>} - 是否需要验证码
 */
export function checkCaptchaWithCache(action) {
  const now = Date.now()
  
  // 如果缓存有效且包含请求的action
  if (now - captchaStatusCache.timestamp < captchaStatusCache.TTL && 
      captchaStatusCache.data.hasOwnProperty(action)) {
    return Promise.resolve(captchaStatusCache.data[action])
  }
  
  // 否则请求API并更新缓存
  return checkCaptchaRequired(action)
    .then(required => {
      // 更新缓存
      if (now - captchaStatusCache.timestamp >= captchaStatusCache.TTL) {
        // 如果缓存过期，重置缓存
        captchaStatusCache.data = {}
        captchaStatusCache.timestamp = now
      }
      
      captchaStatusCache.data[action] = required
      return required
    })
} 