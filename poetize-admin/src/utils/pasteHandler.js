import { htmlToMarkdown, isRichHtml } from '@/utils/htmlToMarkdown';
import { isMarkdownContent } from '@/utils/markdownTypeDetection';
import { Message } from 'element-ui-ce';

/**
 * 统一粘贴处理器
 * @param {ClipboardEvent} event - 粘贴事件对象
 * @param {Object} callbacks - 回调函数集合
 * @param {Function} callbacks.onImage - 处理图片上传 (file) => void
 * @param {Function} callbacks.onFile - 处理文件上传 (file) => void
 * @param {Function} callbacks.onText - 处理文本插入 (text) => void
 * @returns {Promise<void>}
 */
export async function handlePaste(event, { onImage, onFile, onText }) {
  const clipboardData = event.clipboardData || window.clipboardData;
  if (!clipboardData) return;

  // 1. 优先处理文件
  // 注意：剪贴板可能包含多个项目，我们优先寻找文件
  const items = clipboardData.items;
  let hasFile = false;
  
  if (items) {
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.kind === 'file') {
        hasFile = true;
        const file = item.getAsFile();
        if (!file) {
          continue;
        }

        event.preventDefault();
        if (onFile) {
          onFile(file);
          return;
        }
        if (file.type.indexOf('image') !== -1 && onImage) {
          onImage(file);
          return;
        }
      }
    }
  }

  // 2. 检测自定义 Markdown 格式 (编辑器内部复制)
  const poetizeMarkdown = clipboardData.getData('text/x-poetize-markdown');
  if (poetizeMarkdown) {
    event.preventDefault();
    if (onText) onText(poetizeMarkdown);
    return;
  }

  // 3. 检测标准 Markdown 格式
  const standardMarkdown = clipboardData.getData('text/markdown');
  if (standardMarkdown) {
    event.preventDefault();
    if (onText) onText(standardMarkdown);
    return;
  }

  // 4. HTML / 纯文本处理
  const html = clipboardData.getData('text/html');
  const plainText = clipboardData.getData('text/plain');

  if (!html && !plainText) {
    if (hasFile) {
      event.preventDefault();
      Message.warning('当前浏览器没有提供可读取的文件内容');
    }
    return;
  }

  // 总是阻止默认行为，接管粘贴（因为需要异步检测，无法让浏览器默认行为正确执行）
  // 唯一的例外是如果调用方希望纯文本走默认行为，但为了统一体验，建议都由 JS 插入
  event.preventDefault();

  // 异步检测纯文本是否是 Markdown 代码
  let isMarkdown = false;
  if (plainText) {
    isMarkdown = await isMarkdownContent(plainText);
  }

  // 5. 决策逻辑
  // 优先使用 HTML 转 Markdown，除非检测到明显的 Markdown 特征 (避免双重转义)
  if (html && isRichHtml(html) && !isMarkdown) {
    try {
      const markdown = htmlToMarkdown(html);
      if (markdown && markdown.trim()) {
        if (onText) onText(markdown);
        return;
      }
    } catch (err) {
      console.error('HTML to Markdown conversion failed:', err);
      // 转换失败，回退到纯文本
    }
  }

  // 6. 回退到纯文本
  if (plainText) {
    if (onText) onText(plainText);
  }
}
