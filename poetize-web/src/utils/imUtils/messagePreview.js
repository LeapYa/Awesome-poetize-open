/**
 * 消息预览工具函数
 * 用于处理聊天消息的预览显示，正确处理表情符号和文本混合内容
 *
 * 注意：消息内容统一以原始文本（含 \n、[表情]、[名称,图片] token）流入，
 * 本模块不再解析/接收 HTML，输出仅包含转义文本与受控的表情 img 标签，无注入风险
 */
import constant from '@/utils/constant'
import { useMainStore } from '@/stores/main'

function escapeHtml(value = '') {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

function isSafeImageUrl(value = '') {
  const url = String(value).trim();
  if (!url || /[\u0000-\u001F\u007F\s"'<>`]/.test(url)) {
    return false;
  }

  const protocolMatch = url.match(/^([a-z][a-z0-9+.-]*):/i);
  if (protocolMatch) {
    return ['http:', 'https:'].includes(protocolMatch[1].toLowerCase() + ':');
  }

  return true;
}

// 与渲染器保持一致的 token 匹配（不含嵌套方括号）
const TOKEN_PATTERN = /\[[^\[\]]+\]/g;

// 判断 token 是否为内置表情，是则返回表情图地址
function resolveEmojiSrc(token) {
  const value = token.slice(1, -1);
  const index = constant.emojiList.indexOf(value);
  if (index === -1) {
    return null;
  }
  const prefix = useMainStore().sysConfig?.webStaticResourcePrefix || '';
  return `${prefix}emoji/q${index + 1}.gif`;
}

// 判断 token 是否为图片 token（[名称,图片地址]）
function isImageToken(token) {
  return token.slice(1, -1).includes(',');
}

/**
 * 获取消息预览HTML（保留表情图标）
 * @param {string} content - 消息原始内容（含 [表情]/[名称,图片] token）
 * @returns {string} 预览HTML（仅转义文本与受控表情 img）
 */
export function getMessagePreview(content) {
  if (!content) return '';

  let previewHtml = '';
  let textLength = 0;
  const maxLength = 10; // 最大显示长度（10个字左右，避免换行）
  let hasMoreContent = false; // 标记是否有未显示的内容

  function appendText(chunk) {
    if (!chunk || textLength >= maxLength) {
      if (chunk) hasMoreContent = true;
      return;
    }
    const remainingLength = maxLength - textLength;
    if (chunk.length <= remainingLength) {
      previewHtml += escapeHtml(chunk);
      textLength += chunk.length;
    } else {
      previewHtml += escapeHtml(chunk.substr(0, remainingLength));
      textLength = maxLength;
      hasMoreContent = true; // 文本被截断，标记有更多内容
    }
  }

  // 换行统一转空格，预览不换行
  const text = String(content).replace(/\r?\n/g, ' ');
  let lastIndex = 0;
  let hasImageToken = false;

  for (const match of text.matchAll(TOKEN_PATTERN)) {
    if (textLength >= maxLength) {
      hasMoreContent = true;
      break;
    }
    // 先处理 token 之前的普通文本
    appendText(text.slice(lastIndex, match.index));

    const token = match[0];
    const emojiSrc = resolveEmojiSrc(token);
    if (emojiSrc && isSafeImageUrl(emojiSrc)) {
      // 内置表情：保留图标，调整大小适配列表
      if (textLength + 2 <= maxLength) {
        previewHtml += `<img src="${escapeHtml(emojiSrc)}" title="${escapeHtml(token)}" style="width: 20px; height: 20px; vertical-align: middle; margin: 0 2px;">`;
        textLength += 2; // 表情算2个字符长度（因为表情占用空间较大）
      } else {
        hasMoreContent = true; // 表情显示不下，标记有更多内容
      }
    } else if (isImageToken(token)) {
      // 图片 token：显示占位文字
      hasImageToken = true;
      if (textLength + 4 <= maxLength) {
        previewHtml += '[图片]';
        textLength += 4;
      } else {
        hasMoreContent = true;
      }
    } else {
      // 未知 token：按普通文本处理
      appendText(token);
    }
    lastIndex = match.index + token.length;
  }
  // 处理最后一段普通文本
  if (textLength < maxLength) {
    appendText(text.slice(lastIndex));
  } else if (lastIndex < text.length) {
    hasMoreContent = true;
  }

  // 清理多余的空白字符
  previewHtml = previewHtml.replace(/\s+/g, ' ').trim();

  // 如果有更多内容未显示，添加省略号
  if (hasMoreContent && previewHtml) {
    previewHtml += '...';
  }

  // 如果没有提取到任何内容，检查是否是纯图片消息
  if (!previewHtml && hasImageToken) {
    previewHtml = '[图片]';
  }

  return previewHtml;
}

/**
 * 检查消息是否包含表情符号
 * @param {string} content - 消息内容
 * @returns {boolean} 是否包含表情符号
 */
export function hasEmoji(content) {
  if (!content) return false;
  return content.includes('emoji/q') || /\[.*?\]/.test(content);
}

/**
 * 检查消息是否为纯图片消息
 * @param {string} content - 消息内容
 * @returns {boolean} 是否为纯图片消息
 */
export function isImageMessage(content) {
  if (!content) return false;

  const text = String(content).trim();
  const tokens = text.match(TOKEN_PATTERN) || [];
  // 只包含图片 token、没有其他文字内容时视为纯图片消息
  const hasImageToken = tokens.some(token => isImageToken(token));
  const withoutTokens = text.replace(TOKEN_PATTERN, '').trim();

  return hasImageToken && withoutTokens === '';
}

/**
 * 获取消息中的表情符号列表
 * @param {string} content - 消息内容
 * @returns {Array<string>} 表情符号列表（如 [微笑]）
 */
export function getEmojisFromMessage(content) {
  if (!content) return [];

  const emojis = [];
  for (const match of String(content).matchAll(TOKEN_PATTERN)) {
    if (resolveEmojiSrc(match[0])) {
      emojis.push(match[0]);
    }
  }

  return emojis;
}

export default {
  getMessagePreview,
  hasEmoji,
  isImageMessage,
  getEmojisFromMessage
};
