/**
 * @file HTML 转 Markdown 工具
 * @description 使用 Turndown 将富文本（HTML）转换为 Markdown
 * 支持表格、代码块、删除线、任务列表等 GFM 扩展
 */

import TurndownService from 'turndown';
import { gfm, tables, strikethrough, taskListItems } from 'turndown-plugin-gfm';

let turndownInstance = null;

/**
 * 获取配置好的 Turndown 实例（单例）
 * @returns {TurndownService}
 */
function getTurndownService() {
  if (turndownInstance) return turndownInstance;

  turndownInstance = new TurndownService({
    headingStyle: 'atx',           // # 风格标题
    hr: '---',                     // 水平线
    bulletListMarker: '-',         // 无序列表标记
    codeBlockStyle: 'fenced',      // ``` 风格代码块
    fence: '```',                  // 代码块围栏
    emDelimiter: '*',              // 斜体
    strongDelimiter: '**',         // 粗体
    linkStyle: 'inlined',          // [text](url) 风格链接
    linkReferenceStyle: 'full',    // 链接引用风格
  });

  // 启用 GFM 扩展（表格、删除线、任务列表）
  turndownInstance.use(gfm);

  // 自定义规则：处理 highlight-wrap 包装的代码块（markdownLazyRenderer 生成的结构）
  turndownInstance.addRule('highlightWrapCodeBlock', {
    filter: function (node) {
      return (
        node.nodeName === 'DIV' &&
        node.classList &&
        node.classList.contains('highlight-wrap')
      );
    },
    replacement: function (content, node, options) {
      // 从 highlight-wrap 中提取 pre > code
      const pre = node.querySelector('pre');
      const codeNode = pre ? pre.querySelector('code') : null;
      if (!codeNode) return content;
      
      // 优先从工具栏的 hl-lang 获取语言（支持用户编辑后的语言）
      const langSpan = node.querySelector('.hl-lang');
      let language = '';
      if (langSpan) {
        // 优先使用 data-lang 属性，否则使用文本内容
        language = langSpan.getAttribute('data-lang') || langSpan.textContent.trim().toLowerCase();
      }
      // 兜底：从 code 的 class 中提取
      if (!language) {
        const className = codeNode.getAttribute('class') || '';
        const langMatch = className.match(/(?:language-|lang-|hljs\s+)(\w+)/i);
        language = langMatch ? langMatch[1] : '';
      }
      
      // 获取代码内容
      // markdownLazyRenderer 会将每行代码包裹在 <span class="code-line"> 中
      // 需要提取每行内容并用换行符连接
      const codeLines = codeNode.querySelectorAll('.code-line');
      let code;
      if (codeLines.length > 0) {
        code = Array.from(codeLines).map(line => line.textContent).join('\n');
      } else {
        code = codeNode.textContent || '';
      }
      
      return '\n\n' + options.fence + language + '\n' + code.replace(/\n$/, '') + '\n' + options.fence + '\n\n';
    }
  });

  turndownInstance.addRule('mermaidBlock', {
    filter: function (node) {
      return (
        node.nodeName === 'DIV' &&
        node.classList &&
        node.classList.contains('mermaid')
      );
    },
    replacement: function (content, node, options) {
      const encoded = node.getAttribute && node.getAttribute('data-source');
      let code = '';
      if (encoded) {
        try {
          code = decodeURIComponent(encoded);
        } catch (e) {
          code = '';
        }
      }
      if (!code) {
        code = node.textContent || '';
      }
      return '\n\n' + options.fence + 'mermaid' + '\n' + String(code).replace(/\n$/, '') + '\n' + options.fence + '\n\n';
    }
  });

  turndownInstance.addRule('echartsBlock', {
    filter: function (node) {
      return (
        node.nodeName === 'DIV' &&
        node.classList &&
        node.classList.contains('echarts-render')
      );
    },
    replacement: function (content, node, options) {
      const encoded = node.getAttribute && node.getAttribute('data-source');
      let code = '';
      if (encoded) {
        try {
          code = decodeURIComponent(encoded);
        } catch (e) {
          code = '';
        }
      }
      if (!code) {
        code = node.textContent || '';
      }
      return '\n\n' + options.fence + 'echarts' + '\n' + String(code).replace(/\n$/, '') + '\n' + options.fence + '\n\n';
    }
  });

  // 自定义规则：处理 table-wrapper 包装的表格
  turndownInstance.addRule('tableWrapper', {
    filter: function (node) {
      return (
        node.nodeName === 'DIV' &&
        node.classList &&
        node.classList.contains('table-wrapper')
      );
    },
    replacement: function (content, node, options) {
      // 从 table-wrapper 中提取 table，让 gfm 插件处理
      const table = node.querySelector('table');
      if (!table) return content;
      
      // 递归调用 turndown 处理 table
      return turndownInstance.turndown(table.outerHTML);
    }
  });

  // 自定义规则：处理代码块（优化语言检测）
  turndownInstance.addRule('fencedCodeBlock', {
    filter: function (node, options) {
      // 排除已被 highlight-wrap 规则处理的代码块
      const parent = node.parentNode;
      if (parent && parent.classList && parent.classList.contains('highlight-wrap')) {
        return false;
      }
      return (
        options.codeBlockStyle === 'fenced' &&
        node.nodeName === 'PRE' &&
        node.firstChild &&
        node.firstChild.nodeName === 'CODE'
      );
    },
    replacement: function (content, node, options) {
      const codeNode = node.firstChild;
      const className = codeNode.getAttribute('class') || '';
      // 从 class 中提取语言（支持 language-xxx, lang-xxx, hljs xxx 等格式）
      const langMatch = className.match(/(?:language-|lang-|hljs\s+)(\w+)/i);
      const language = langMatch ? langMatch[1] : '';
      
      // 获取代码内容（保留原始格式）
      const code = codeNode.textContent || '';
      
      return '\n\n' + options.fence + language + '\n' + code.replace(/\n$/, '') + '\n' + options.fence + '\n\n';
    }
  });

  // 自定义规则：处理行内代码
  turndownInstance.addRule('inlineCode', {
    filter: function (node) {
      const isCodeBlock = node.parentNode && node.parentNode.nodeName === 'PRE';
      return node.nodeName === 'CODE' && !isCodeBlock;
    },
    replacement: function (content) {
      if (!content) return '';
      // 处理内容中包含反引号的情况
      const hasBacktick = content.includes('`');
      if (hasBacktick) {
        return '`` ' + content + ' ``';
      }
      return '`' + content + '`';
    }
  });

  // 自定义规则：处理图片（保留 alt 和 title）
  turndownInstance.addRule('image', {
    filter: 'img',
    replacement: function (content, node) {
      const alt = node.getAttribute('alt') || '';
      const src = node.getAttribute('src') || '';
      const title = node.getAttribute('title');
      
      if (!src) return '';
      
      if (title) {
        return '![' + alt + '](' + src + ' "' + title + '")';
      }
      return '![' + alt + '](' + src + ')';
    }
  });

  // 自定义规则：处理链接（清理追踪参数等）
  turndownInstance.addRule('attachmentCard', {
    filter: function (node) {
      return (
        node.classList &&
        node.classList.contains('pb-attachment-card') &&
        node.getAttribute('data-pb-attachment') === 'true'
      ) || (
        node.nodeName === 'A' &&
        node.classList &&
        node.classList.contains('pb-attachment-card') &&
        node.getAttribute('href')
      );
    },
    replacement: function (content, node) {
      let href =
        node.getAttribute('data-pb-attachment-href') ||
        node.getAttribute('href') ||
        '';
      const nameNode = node.querySelector('.pb-attachment-pill-name');
      const name = (nameNode ? nameNode.textContent : content || '附件').trim();
      const isPrivate = node.getAttribute('data-pb-private-attachment') === 'true';

      if (!href) {
        const previewLink = node.querySelector('.pb-attachment-pill-btn[data-pb-attachment-action="preview"]');
        const downloadLink = node.querySelector('.pb-attachment-pill-btn[data-pb-attachment-action="download"]');
        href = (previewLink && previewLink.getAttribute('href')) ||
          (downloadLink && downloadLink.getAttribute('data-pb-attachment-href')) ||
          (downloadLink && downloadLink.getAttribute('href')) ||
          '';
      }

      if (isPrivate && href && !href.startsWith('u/')) {
        href = 'u/' + href.replace(/^\/+/, '');
      }

      return '[' + name.replace(/[\[\]\(\)]/g, '') + '](' + href + ' "pb-attachment")';
    }
  });

  // 自定义规则：处理链接（清理追踪参数等）
  turndownInstance.addRule('link', {
    filter: function (node, options) {
      return (
        options.linkStyle === 'inlined' &&
        node.nodeName === 'A' &&
        node.getAttribute('href')
      );
    },
    replacement: function (content, node) {
      const href = node.getAttribute('href') || '';
      const title = node.getAttribute('title');
      
      // 如果链接文本和 href 完全相同，直接返回 URL
      if (content === href) {
        return href;
      }
      
      if (title) {
        return '[' + content + '](' + href + ' "' + title + '")';
      }
      return '[' + content + '](' + href + ')';
    }
  });

  // 自定义规则：处理引用块
  turndownInstance.addRule('blockquote', {
    filter: 'blockquote',
    replacement: function (content) {
      // 清理内容，添加引用标记
      const lines = content.trim().split('\n');
      return '\n\n' + lines.map(line => '> ' + line).join('\n') + '\n\n';
    }
  });

  // 自定义规则：处理 <br> 标签
  turndownInstance.addRule('br', {
    filter: 'br',
    replacement: function () {
      return '\n';
    }
  });

  // 自定义规则：处理 <mark> 高亮
  turndownInstance.addRule('mark', {
    filter: 'mark',
    replacement: function (content) {
      return '==' + content + '==';
    }
  });

  // 自定义规则：处理微信公众号的特殊标签
  turndownInstance.addRule('wechatSection', {
    filter: function (node) {
      // 微信的 section 标签通常用于排版，直接提取内容
      return node.nodeName === 'SECTION';
    },
    replacement: function (content) {
      return content;
    }
  });

  // 自定义规则：处理待办列表（task-list），确保格式正确
  // 优先级要高于 gfm 的默认规则，所以放在后面添加
  turndownInstance.addRule('taskList', {
    filter: function (node) {
      return (
        node.nodeName === 'UL' &&
        node.classList &&
        node.classList.contains('task-list')
      );
    },
    replacement: function (content, node, options) {
      const items = Array.from(node.querySelectorAll('li.task-list-item'));
      if (items.length === 0) return '';
      
      const lines = items
        .map(li => {
          const checkbox = li.querySelector('input[type="checkbox"]');
          if (!checkbox) return null; // 没有 checkbox 的项跳过
          
          const isChecked = checkbox.checked;
          // 获取文本内容（排除 checkbox），清理多余空格
          const text = Array.from(li.childNodes)
            .filter(n => n.nodeName !== 'INPUT')
            .map(n => n.textContent || '')
            .join('')
            .trim()
            .replace(/\s+/g, ' '); // 多个空格合并为一个
          
          // 如果文本为空，跳过该项（不生成 `- [ ]`）
          if (!text) return null;
          
          const marker = isChecked ? 'x' : ' ';
          return `- [${marker}] ${text}`;
        })
        .filter(line => line !== null); // 过滤掉空项
      
      if (lines.length === 0) return '';
      return '\n' + lines.join('\n') + '\n';
    }
  });

  // 移除空白节点和无意义标签
  turndownInstance.remove(['script', 'style', 'meta', 'link', 'noscript']);

  // 保留但不转换的标签（直接输出内容）
  turndownInstance.keep(['iframe', 'video', 'audio']);

  return turndownInstance;
}

/**
 * 将 HTML 转换为 Markdown
 * @param {string} html - 输入的 HTML 字符串
 * @returns {string} 转换后的 Markdown 字符串
 */
export function htmlToMarkdown(html) {
  if (!html || typeof html !== 'string') return '';
  
  // 预处理 HTML
  let processedHtml = html
    // 移除零宽空格（ZWS，用于辅助光标定位）
    .replace(/\u200B/g, '')
    // 移除 Word/Office 特有的标签和属性
    .replace(/<o:p[^>]*>[\s\S]*?<\/o:p>/gi, '')
    .replace(/<!--\[if[^>]*>[\s\S]*?<!\[endif\]-->/gi, '')
    // 规范化换行
    .replace(/<br\s*\/?>/gi, '<br>')
    // 处理微信公众号的 span 样式（粗体、斜体等）
    .replace(/<span[^>]*style="[^"]*font-weight:\s*bold[^"]*"[^>]*>([\s\S]*?)<\/span>/gi, '<strong>$1</strong>')
    .replace(/<span[^>]*style="[^"]*font-style:\s*italic[^"]*"[^>]*>([\s\S]*?)<\/span>/gi, '<em>$1</em>')
    .replace(/<span[^>]*style="[^"]*text-decoration:\s*line-through[^"]*"[^>]*>([\s\S]*?)<\/span>/gi, '<del>$1</del>');
  
  // 清理待办列表中的多余空格（在 task-list-item 中，checkbox 后的文本）
  processedHtml = processedHtml.replace(
    /(<li[^>]*class="[^"]*task-list-item[^"]*"[^>]*>)\s*<input[^>]*type="checkbox"[^>]*>\s+/gi,
    (match, liStart) => {
      const checked = /\schecked(\s|=|>)/i.test(match) ? ' checked' : ''
      return `${liStart}<input type="checkbox"${checked}> `
    }
  );
  
  // 移除多余的空白（但保留换行）
  processedHtml = processedHtml
    .replace(/[ \t]+/g, ' ')  // 多个空格/制表符合并为一个空格
    .replace(/[ \t]*\n[ \t]*/g, '\n');  // 清理换行前后的空格

  const turndown = getTurndownService();
  let markdown = turndown.turndown(processedHtml);
  
  // 后处理 Markdown
  markdown = markdown
    // 清理待办列表行尾的多余空格（匹配 `- [ ] 文本    ` 或 `- [x] 文本    `）
    .replace(/^(\s*[-*+] \[[ x]\] .+?)\s+$/gm, '$1')
    // 删除空的待办列表行（只有 `- [ ]` 或 `- [x]` 没有文本）
    .replace(/^(\s*[-*+] \[[ x]\])\s*$/gm, '')
    // 移除连续的空行（最多保留两个换行）
    .replace(/\n{3,}/g, '\n\n')
    // 移除行首行尾的空白
    .trim();
  
  return markdown;
}

/**
 * 检测 HTML 是否值得转换（排除纯文本包装）
 * @param {string} html - HTML 字符串
 * @returns {boolean} 是否包含有意义的 HTML 标签
 */
export function isRichHtml(html) {
  if (!html || typeof html !== 'string') return false;
  
  // 检测是否包含有意义的 HTML 标签
  const meaningfulTags = /<(h[1-6]|p|div|table|tr|td|th|ul|ol|li|blockquote|pre|code|a|img|strong|em|b|i|del|s|mark|hr|br)[^>]*>/i;
  return meaningfulTags.test(html);
}

/**
 * 清理 HTML 中的样式和脚本
 * @param {string} html - 原始 HTML
 * @returns {string} 清理后的 HTML
 */
export function cleanHtml(html) {
  if (!html) return '';
  
  return html
    // 移除 style 标签及内容
    .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
    // 移除 script 标签及内容
    .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
    // 移除 HTML 注释
    .replace(/<!--[\s\S]*?-->/g, '')
    // 移除内联 style 属性（可选，保留结构）
    // .replace(/\s*style="[^"]*"/gi, '')
    .trim();
}

export default {
  htmlToMarkdown,
  isRichHtml,
  cleanHtml
};
