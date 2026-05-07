/**
 * @file Markdown 懒加载渲染器工具
 * @description 封装 MarkdownIt 及其插件的动态导入，通过代码分割减少首页 Bundle 体积。
 */

import { transformAttachmentLinks } from './attachmentCard';

let mdInstance = null;

/**
 * 异步预热 Markdown 渲染引擎核心
 * @description 仅加载 markdown-it 核心包，不执行插件加载，建议在应用空闲或鼠标悬浮前期执行。
 * @returns {Promise<void>}
 */
export async function warmupMarkdown() {
    if (mdInstance) return;
    try {
        await import('markdown-it');
    } catch (e) {
        console.error('Markdown 核心预热失败:', e);
    }
}

/**
 * 解析并渲染 Markdown 内容为 HTML 字符串
 * @description 若库尚未加载，将通过动态导入并发获取核心引擎及其常用插件（表格、数学公式、高亮）。
 * @param {string} content - 待渲染的原始 Markdown 文本字符串
 * @returns {Promise<string>} 渲染后的 HTML 字符串
 */
export async function renderMarkdown(content) {
    if (!mdInstance) {
        try {
            const [
                { default: MarkdownIt },
                { default: markdownItMultimdTable },
                { default: markdownItKatex },
                { default: markdownItTaskLists },
                { default: hljs }
            ] = await Promise.all([
                import('markdown-it'),
                import('markdown-it-multimd-table'),
                import('@iktakahiro/markdown-it-katex'),
                import('markdown-it-task-lists'),
                import('highlight.js')
            ]);

            mdInstance = new MarkdownIt({ 
                breaks: true,
                html: true, // 允许 HTML 标签，以便渲染复杂的自定义结构
                linkify: true
            })
                .use(markdownItMultimdTable)
                .use(markdownItKatex)
                .use(markdownItTaskLists, {
                    label: true,
                    labelAfter: true
                });

            const defaultTableOpen = mdInstance.renderer.rules.table_open;
            const defaultTableClose = mdInstance.renderer.rules.table_close;

            mdInstance.renderer.rules.table_open = function (tokens, idx, options, env, self) {
                const rendered = defaultTableOpen ? defaultTableOpen(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options);
                return `<div class="table-wrapper">${rendered}`;
            };

            mdInstance.renderer.rules.table_close = function (tokens, idx, options, env, self) {
                const rendered = defaultTableClose ? defaultTableClose(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options);
                return `${rendered}</div>`;
            };

            // 自定义 fence 渲染规则，避免外层被包裹 pre 和 code
            mdInstance.renderer.rules.fence = function (tokens, idx, options, env, self) {
                const token = tokens[idx];
                const info = token.info ? mdInstance.utils.unescapeAll(token.info).trim() : '';
                let lang = '';
                let langName = '';

                if (info) {
                    langName = info.split(/\s+/g)[0];
                    lang = langName;
                }

                let codeHTML = '';
                // 将代码按行分割，以便添加行号结构
                const lines = token.content.split(/\r?\n/);
                // markdown-it 的 fence content 通常末尾带有一个换行符，分割后会多出一个空串，需移除
                if (lines.length > 0 && lines[lines.length - 1] === '') {
                    lines.pop();
                }

                if (lang === 'mermaid') {
                    // Mermaid 图表容器
                    const encodedSource = mdInstance.utils.escapeHtml(encodeURIComponent(token.content));
                    return `<div class="mermaid" data-source="${encodedSource}">${mdInstance.utils.escapeHtml(token.content)}</div>`;
                }

                if (lang === 'echarts') {
                    // ECharts 图表容器
                    const encodedSource = mdInstance.utils.escapeHtml(encodeURIComponent(token.content));
                    return `<div class="echarts-render" data-source="${encodedSource}" style="width: 100%; height: 400px; margin: 10px 0;">${mdInstance.utils.escapeHtml(token.content)}</div>`;
                }

                if (lang && hljs.getLanguage(lang)) {
                    try {
                        // 逐行高亮以保持行结构安全（牺牲部分多行语法的准确性）
                        codeHTML = lines.map(line => {
                            const h = hljs.highlight(line, { language: lang, ignoreIllegals: true }).value;
                            return `<span class="code-line">${h || '&nbsp;'}</span>`;
                        }).join('');
                    } catch (__) {}
                }

                // 兜底或无高亮情况
                if (!codeHTML) {
                    codeHTML = lines.map(line => {
                        const escaped = mdInstance.utils.escapeHtml(line);
                        return `<span class="code-line">${escaped || '&nbsp;'}</span>`;
                    }).join('');
                }

                // 构造符合 editor-preview.css 要求的结构
                const langLabel = langName ? langName.toUpperCase() : '';
                const encodedCode = encodeURIComponent(token.content);
                const escapedLangLabel = mdInstance.utils.escapeHtml(langLabel);

                // 添加 css-line-numbers 类以启用 CSS 计数器
                return `<div class="highlight-wrap has-toolbar">
                    <div class="highlight-toolbar">
                        <span class="hl-dots" aria-hidden="true"></span>
                        <span class="hl-lang">${escapedLangLabel}</span>
                        <i class="el-icon-document-copy copy-code" data-code="${encodedCode}" title="复制"></i>
                    </div>
                    <pre><code class="hljs ${lang} css-line-numbers">${codeHTML}</code></pre>
                </div>`;
            };
        } catch (error) {
            console.error('Markdown 渲染器加载失败:', error);
            return content || '';
        }
    }

    return transformAttachmentLinks(mdInstance.render(content || ''));
}

/**
 * 保持兼容性的简单 Markdown 渲染接口
 * @param {string} content - Markdown 文本
 * @returns {Promise<string>} HTML 字符串
 */
export async function renderSimpleMarkdown(content) {
    return renderMarkdown(content);
}

/**
 * 预加载接口（保持向下兼容，逻辑重连至核心预热）
 * @returns {Promise<void>}
 */
export async function preloadMarkdown() {
    return warmupMarkdown();
}
