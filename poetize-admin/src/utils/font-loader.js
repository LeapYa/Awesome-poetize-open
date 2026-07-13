/**
 * 字体动态加载器
 * 优先加载 cn-font-split 生成的 font.css，失败时回退到旧版 4 分片方案
 */

// 默认Unicode范围定义
let baseRange, level1Range, level2Range, otherRange

const DYNAMIC_FONT_STYLE_ID = 'dynamic-font-style';
const DYNAMIC_FONT_LINK_ID = 'dynamic-font-link';

function removeDynamicFontResources() {
  const oldStyle = document.getElementById(DYNAMIC_FONT_STYLE_ID);
  if (oldStyle?.parentNode) {
    oldStyle.parentNode.removeChild(oldStyle);
  }

  const oldLink = document.getElementById(DYNAMIC_FONT_LINK_ID);
  if (oldLink?.parentNode) {
    oldLink.parentNode.removeChild(oldLink);
  }
}

function appendToHead(node) {
  if (document.head && document.head.nodeType === Node.ELEMENT_NODE) {
    document.head.appendChild(node);
    return;
  }

  const head = document.querySelector('head');
  if (head && head.nodeType === Node.ELEMENT_NODE) {
    head.appendChild(node);
    return;
  }

  throw new Error('未找到可用的 head 节点');
}

/**
 * 给字体静态资源 URL 追加缓存失效参数 ?v=版本号。
 * 重新上传字体后版本号变化，浏览器会重新拉取新分片，无需手动清缓存或 Ctrl+Shift+R。
 */
function appendVersion(url, version) {
  if (!version) return url;
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}v=${encodeURIComponent(version)}`;
}

function buildFontCssPath(fontCdnBaseUrl, sysConfig) {
  return sysConfig['font.css.path'] || `${fontCdnBaseUrl}font.css`;
}

async function loadFontCss(fontCssPath) {
  // 使用 fetch 而非 <link>: SPA 会对 404 返回 HTML（200），<link> 的 onload 会误触发
  const resp = await fetch(fontCssPath);
  if (!resp.ok) {
    throw new Error(`加载字体 CSS 失败: HTTP ${resp.status}`);
  }
  const contentType = resp.headers.get('content-type') || '';
  if (!contentType.includes('css') && !contentType.includes('text/plain')) {
    // SPA fallback 返回的是 text/html，不是真正的 CSS
    throw new Error(`字体 CSS 响应类型不正确: ${contentType}`);
  }
  const cssText = await resp.text();
  if (!cssText.includes('@font-face')) {
    throw new Error('字体 CSS 内容无效：未包含 @font-face 规则');
  }
  // 注入根据 cn-font-split 生成的 CSS
  const link = document.createElement('link');
  link.id = DYNAMIC_FONT_LINK_ID;
  link.rel = 'stylesheet';
  link.href = fontCssPath;
  try {
    appendToHead(link);
  } catch (error) {
    throw error;
  }
}

/**
 * 从远程URL加载JSON
 * @param {string} url 远程文件URL
 * @returns {Promise<object>} JSON对象
 */
async function fetchJson(url) {
  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`HTTP error ${response.status}`);
    }
    return await response.json();
  } catch (error) {
    console.error(`获取JSON文件失败: ${url}`, error);
    return null;
  }
}

/**
 * 加载字体
 * @param {Object} sysConfig 系统配置
 */
export async function loadFonts(sysConfig) {
  const fontCdnBaseUrl = sysConfig['font.cdn.base-url'] || '/static/assets/font_chunks/';
  const fontCssPath = buildFontCssPath(fontCdnBaseUrl, sysConfig);
  // 字体资源版本号：重新上传字体后变化，用于给字体静态文件追加 ?v= 缓存失效参数
  const fontVersion = sysConfig['font.asset.version'] || '';
  const versionedFontCssPath = appendVersion(fontCssPath, fontVersion);
  // 是否使用单一字体文件
  const useSingleFont = sysConfig['font.use.single'] === 'true';
  // 单一字体文件名
  const singleFontName = sysConfig['font.single.filename'] || 'font.woff2';
  // 是否从远程加载Unicode范围
  const loadUnicodeFromRemote = sysConfig['font.unicode.remote'] === 'true';
  // Unicode范围文件路径
  const unicodeJsonPath = sysConfig['font.unicode.path'] || '/static/assets/font_chunks/unicode_ranges.json';

  removeDynamicFontResources();

  if (!useSingleFont) {
    try {
      await loadFontCss(versionedFontCssPath);
      return;
    } catch (error) {
      console.warn('加载 cn-font-split 字体 CSS 失败，回退旧版分片方案', error);
    }
  }

  // 如果需要从远程加载Unicode范围
  if (loadUnicodeFromRemote && !useSingleFont) {
    try {
      // 加载JSON文件
      const unicodeRanges = await fetchJson(unicodeJsonPath);

      if (unicodeRanges) {
        // 更新Unicode范围变量
        if (unicodeRanges.base) baseRange = unicodeRanges.base.join(',');
        if (unicodeRanges.level1) level1Range = unicodeRanges.level1.join(',');
        if (unicodeRanges.level2) level2Range = unicodeRanges.level2.join(',');
        if (unicodeRanges.other) otherRange = unicodeRanges.other.join(',');

      } else {
      }
    } catch (error) {
      console.error('加载远程Unicode范围失败，使用默认值', error);
    }
  } else if (!loadUnicodeFromRemote && !useSingleFont) {
    // 从本地加载Unicode范围
    const localUnicodeJsonPath = '/static/assets/font_chunks/unicode_ranges.json';
    try {
      // 加载本地JSON文件
      const unicodeRanges = await fetchJson(localUnicodeJsonPath);

      if (unicodeRanges) {
        // 更新Unicode范围变量
        if (unicodeRanges.base) baseRange = unicodeRanges.base.join(',');
        if (unicodeRanges.level1) level1Range = unicodeRanges.level1.join(',');
        if (unicodeRanges.level2) level2Range = unicodeRanges.level2.join(',');
        if (unicodeRanges.other) otherRange = unicodeRanges.other.join(',');

      } else {
      }
    } catch (error) {
      console.error('加载本地Unicode范围失败，使用默认值', error);
    }
  }

  // 创建style元素
  const style = document.createElement('style');
  style.type = 'text/css';
  style.id = DYNAMIC_FONT_STYLE_ID;

  // 构建字体CSS
  let css = '';

  if (useSingleFont) {
    // 使用单一字体文件
    css = `
      @font-face {
        font-family: 'MyAwesomeFont';
        src: url('${appendVersion(fontCdnBaseUrl + singleFontName, fontVersion)}') format('woff2');
        font-weight: normal;
        font-style: normal;
        font-display: swap;
      }
    `;
  } else {
    // 使用分块字体文件
    // 如果 unicode-range 未加载（unicode_ranges.json 不存在），则省略 unicode-range 属性
    // 浏览器会按顺序尝试每个字体源，直到找到包含所需字形的文件
    const chunks = [
      { file: 'font.base.woff2', range: baseRange },
      { file: 'font.level1.woff2', range: level1Range },
      { file: 'font.level2.woff2', range: level2Range },
      { file: 'font.other.woff2', range: otherRange },
    ];
    css = chunks
      .map(
        ({ file, range }) => `
      @font-face {
        font-family: 'MyAwesomeFont';
        src: url('${appendVersion(fontCdnBaseUrl + file, fontVersion)}') format('woff2');
        font-weight: normal;
        font-style: normal;
        font-display: swap;${range ? `\n        unicode-range: ${range};` : ''}
      }`
      )
      .join('\n');
  }

  // 设置样式内容并添加到文档
  style.textContent = css;

  // 安全地添加样式到head，避免appendChild在文本节点上的错误
  try {
    appendToHead(style);
  } catch (error) {
    console.error('添加字体样式失败:', error);
  }
}

// 导出默认方法
export default {
  loadFonts
}; 