/**
 * 图片工具函数
 * 提供图片大小计算等辅助功能
 * 图片压缩由服务端 ImageCompressUtil 处理（通过 /ai/chat/compressImage 接口）
 */

/**
 * 计算多个 base64 图片的总大小（MB）
 * @param {string[]} dataUrls base64 data URL 数组
 * @returns {number} 总大小（MB）
 */
export function calculateTotalSizeMB(dataUrls) {
  if (!dataUrls || dataUrls.length === 0) return 0;
  const totalBytes = dataUrls.reduce((sum, url) => {
    // base64 部分的长度（去掉 data:image/xxx;base64, 前缀）
    const commaIdx = url.indexOf(',');
    const base64Part = commaIdx >= 0 ? url.substring(commaIdx + 1) : url;
    // base64 编码后体积 ≈ 原始大小 * 4/3
    return sum + Math.round((base64Part.length * 3) / 4);
  }, 0);
  return totalBytes / (1024 * 1024);
}
