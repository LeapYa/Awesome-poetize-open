/**
 * AI助手默认头像配置
 * 提供统一的AI助手头像回退
 */

/**
 * 获取默认AI头像
 * 返回 public 目录下的静态图片
 *
 * @returns {string} 头像 URL
 */
export function getAiDefaultAvatar() {
  return '/static/assets/ai_avatar.png';
}

/**
 * 获取AI头像 URL（带默认头像回退）
 *
 * @param {string} avatar - AI头像 URL
 * @returns {string} 头像 URL 或默认AI头像
 */
export function getAiAvatarUrl(avatar) {
  if (avatar && typeof avatar === 'string' && avatar.trim()) {
    return avatar;
  }
  return getAiDefaultAvatar();
}
