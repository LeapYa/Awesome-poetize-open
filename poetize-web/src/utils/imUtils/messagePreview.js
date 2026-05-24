/**
 * 消息预览工具函数
 * 用于处理聊天消息的预览显示，正确处理表情符号和文本混合内容
 */

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

function parseInertHtml(content) {
  return new DOMParser().parseFromString(String(content), 'text/html').body;
}

/**
 * 获取消息预览HTML（保留表情图标）
 * @param {string} content - 消息内容（可能包含HTML标签）
 * @returns {string} 预览HTML（包含表情图标）
 */
export function getMessagePreview(content) {
  if (!content) return '';
  const parsedBody = parseInertHtml(content);
  
  let previewHtml = '';
  let textLength = 0;
  const maxLength = 10; // 最大显示长度（10个字左右，避免换行）
  let hasMoreContent = false; // 标记是否有未显示的内容
  
  // 遍历所有子节点，保留表情图标
  function processNode(node) {
    if (textLength >= maxLength) {
      hasMoreContent = true; // 标记还有内容未显示
      return;
    }
    
    if (node.nodeType === Node.TEXT_NODE) {
      // 文本节点
      const text = node.textContent;
      const remainingLength = maxLength - textLength;

      if (text.length <= remainingLength) {
        previewHtml += escapeHtml(text);
        textLength += text.length;
      } else {
        previewHtml += escapeHtml(text.substr(0, remainingLength));
        textLength = maxLength;
        hasMoreContent = true; // 文本被截断，标记有更多内容
      }
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      if (node.tagName === 'IMG') {
        // 图片节点
        const src = node.getAttribute('src');
        const title = node.getAttribute('title');
        
        if (src && src.includes('emoji/q') && isSafeImageUrl(src)) {
          // 检查是否还有足够空间显示表情
          if (textLength + 2 <= maxLength) {
            // 表情符号，保留img标签，调整大小适配列表
            previewHtml += `<img src="${escapeHtml(src)}" ${title ? `title="${escapeHtml(title)}"` : ''} style="width: 20px; height: 20px; vertical-align: middle; margin: 0 2px;">`;
            textLength += 2; // 表情算2个字符长度（因为表情占用空间较大）
          } else {
            hasMoreContent = true; // 表情显示不下，标记有更多内容
          }
        } else {
          // 普通图片，显示文字
          if (textLength + 4 <= maxLength) {
            previewHtml += '[图片]';
            textLength += 4;
          } else {
            hasMoreContent = true;
          }
        }
      } else if (node.tagName === 'BR') {
        // 换行符，转换为空格
        previewHtml += ' ';
        textLength += 1;
      } else {
        // 其他HTML元素，递归处理子节点
        for (let child of node.childNodes) {
          if (textLength >= maxLength) {
            hasMoreContent = true;
            break;
          }
          processNode(child);
        }
      }
    }
  }
  
  // 处理所有子节点
  for (let child of parsedBody.childNodes) {
    if (textLength >= maxLength) {
      hasMoreContent = true;
      break;
    }
    processNode(child);
  }
  
  // 清理多余的空白字符
  previewHtml = previewHtml.replace(/\s+/g, ' ').trim();
  
  // 如果有更多内容未显示，添加省略号
  if (hasMoreContent && previewHtml) {
    previewHtml += '...';
  }
  
  // 如果没有提取到任何内容，检查是否是纯图片消息
  if (!previewHtml && content.includes('<img')) {
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
  
  const parsedBody = parseInertHtml(content);
  
  // 检查是否只包含图片标签，没有其他文本内容
  const textContent = parsedBody.textContent || '';
  const hasImages = parsedBody.querySelector('img') !== null;
  
  return hasImages && textContent.trim() === '';
}

/**
 * 获取消息中的表情符号列表
 * @param {string} content - 消息内容
 * @returns {Array<string>} 表情符号列表
 */
export function getEmojisFromMessage(content) {
  if (!content) return [];
  
  const emojis = [];
  const parsedBody = parseInertHtml(content);
  
  const images = parsedBody.querySelectorAll('img');
  images.forEach(img => {
    const src = img.getAttribute('src');
    const title = img.getAttribute('title');
    
    if (src && src.includes('emoji/q') && title) {
      emojis.push(title);
    }
  });
  
  return emojis;
}

export default {
  getMessagePreview,
  hasEmoji,
  isImageMessage,
  getEmojisFromMessage
};
