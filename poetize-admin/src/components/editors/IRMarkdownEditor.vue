<template>
  <div 
    class="ir-editor" 
    :class="{ 'fullscreen': isFullscreen, 'dark-mode': isDarkMode }"
    :style="{ height: containerHeight }"
  >
    <!-- 工具栏 -->
    <div class="editor-toolbar" v-if="showToolbar" @mousedown.prevent>
      <div class="toolbar-group">
        <el-tooltip content="加粗 (Ctrl+B)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('bold')"><i class="fa fa-bold"></i></div>
        </el-tooltip>
        <el-tooltip content="斜体 (Ctrl+I)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('italic')"><i class="el-icon-edit"></i></div>
        </el-tooltip>
        <el-tooltip content="删除线 (Ctrl+D)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('strike')"><i class="el-icon-minus"></i></div>
        </el-tooltip>
        <el-tooltip content="行内代码 (Ctrl+`)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('inline-code')"><i class="fa fa-code"></i></div>
        </el-tooltip>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="toolbar-group">
        <el-tooltip content="标题" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('heading')"><i class="el-icon-s-flag"></i></div>
        </el-tooltip>
        <el-tooltip content="引用 (Ctrl+Q)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('quote')"><i class="el-icon-chat-line-square"></i></div>
        </el-tooltip>
        <el-tooltip content="无序列表 (Ctrl+U)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('ul')"><i class="el-icon-tickets"></i></div>
        </el-tooltip>
        <el-tooltip content="有序列表 (Ctrl+O)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('ol')"><i class="el-icon-s-order"></i></div>
        </el-tooltip>
        <el-tooltip content="待办列表" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('todo')"><i class="el-icon-circle-check"></i></div>
        </el-tooltip>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="toolbar-group">
        <el-tooltip content="链接 (Ctrl+K)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('link')"><i class="el-icon-link"></i></div>
        </el-tooltip>
        <el-tooltip content="图片 (粘贴)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerImageUpload"><i class="el-icon-picture-outline"></i></div>
        </el-tooltip>
        <el-tooltip content="文件 (粘贴)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerFileUpload"><i class="el-icon-folder-add"></i></div>
        </el-tooltip>
        <el-tooltip content="私有附件" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerPrivateFileUpload"><i class="el-icon-lock"></i></div>
        </el-tooltip>
        <el-tooltip content="代码块" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('code')"><i class="fa fa-file-code-o"></i></div>
        </el-tooltip>
        <el-tooltip content="表格" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('table')"><i class="el-icon-s-grid"></i></div>
        </el-tooltip>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <el-tooltip content="撤销 (Ctrl+Z)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'disabled': !canUndo }" @click="undo"><i class="el-icon-refresh-left"></i></div>
        </el-tooltip>
        <el-tooltip content="重做 (Ctrl+Y)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'disabled': !canRedo }" @click="redo"><i class="el-icon-refresh-right"></i></div>
        </el-tooltip>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <el-tooltip content="复制到公众号/知乎" placement="top" :enterable="false">
          <div class="toolbar-item" @click="copyToWeChat"><i class="el-icon-copy-document"></i></div>
        </el-tooltip>
        <el-tooltip content="导出 Markdown" placement="top" :enterable="false">
          <div class="toolbar-item" @click="exportMarkdown"><i class="el-icon-download"></i></div>
        </el-tooltip>
      </div>
      
      <div class="toolbar-spacer"></div>
      
      <div class="toolbar-group">
        <el-tooltip content="使用说明" placement="top" :enterable="false">
          <div class="toolbar-item" @click="helpDialogVisible = true">
            <i class="el-icon-question"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="切换编辑器（插件管理）" placement="top" :enterable="false">
          <div class="toolbar-item" @click="goPluginManager">
            <i class="el-icon-s-operation"></i>
          </div>
        </el-tooltip>
        <el-tooltip :content="isFullscreen ? '退出全屏 (Esc)' : '全屏 (F11)'" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': isFullscreen }" @click="toggleFullscreen">
            <i class="el-icon-full-screen"></i>
          </div>
        </el-tooltip>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="editor-body">
      <!-- 隐藏的输入区域（用于捕获输入） -->
      <textarea
        ref="hiddenInput"
        class="hidden-input"
        @focus="handleEditorFocus"
        @blur="handleEditorBlur"
        @keydown="handleKeydown"
        @input="handleInput"
        @compositionstart="handleCompositionStart"
        @compositionupdate="handleCompositionUpdate"
        @compositionend="handleCompositionEnd"
        @paste="handlePaste"
        @copy="handleCopy"
        @cut="handleCut"
        autocomplete="off"
        autocorrect="off"
        autocapitalize="off"
        spellcheck="false"
      ></textarea>
      
      <!-- 可视编辑区域 -->
      <div 
        ref="editorContent"
        class="editor-content"
        @mousedown="handleMouseDown"
        @mousemove="handleMouseMove"
        @mouseup="handleMouseUp"
        @click="handleClick"
        @dblclick="handleDoubleClick"
        @scroll="handleScroll"
        @touchstart="handleTouchStart"
        @touchmove="handleTouchMove"
        @touchend="handleTouchEnd"
        @selectstart="handleSelectStart"
      >
        <!-- 内容区域 - 行号与内容整合在同一行中 -->
        <div class="lines-container" ref="linesContainer">
          <template v-for="seg in segments">
            <!-- 单行渲染 -->
            <div
              v-if="seg.kind === 'line'"
              :key="'l-' + seg.lineIndex"
              class="editor-line"
              :class="{ 
                'cursor-line': isFocused && (seg.lineIndex === cursorLine || isLineInSelection(seg.lineIndex)),
                'has-selection': isLineInSelection(seg.lineIndex)
              }"
              :style="getLineInteractionStyle(seg.lineIndex)"
              :data-line-index="seg.lineIndex"
            >
              <!-- 光标行、选中行、或正在编辑的块内的行：显示源码 -->
              <template v-if="shouldShowSource(seg.lineIndex) || seg.inEditingBlock">
                <span class="line-content source-mode">
                    <span
                      v-for="(char, charIndex) in getLineChars(seg.lineIndex)"
                      :key="charIndex"
                      class="char"
                      :class="{
                        'selected': isCharSelected(seg.lineIndex, charIndex),
                        'cursor-before': seg.lineIndex === cursorLine && charIndex === cursorColumn && isFocused && !isComposing
                      }"
                    >{{ char === '' ? '\u200B' : char }}</span>
                    <!-- 行尾光标 -->
                    <span
                      v-if="seg.lineIndex === cursorLine && cursorColumn >= getLineLength(seg.lineIndex) && isFocused && !isComposing"
                      class="cursor-end"
                    ></span>
                  <!-- 输入法组合文本 -->
                  <span v-if="seg.lineIndex === cursorLine && isComposing && compositionText" class="composition-text">
                    {{ compositionText }}
                  </span>
                </span>
              </template>
              
              <!-- 非光标行：渲染为文章页同款 HTML，并使用 entry-content 样式体系 -->
              <template v-else>
                <div
                  v-if="renderedSegments['l:' + seg.lineIndex] !== undefined"
                  class="line-content rendered-mode entry-content"
                  v-html="renderedSegments['l:' + seg.lineIndex] || '&nbsp;'"
                ></div>
                <span v-else class="line-content source-mode">
                  {{ getLineText(seg.lineIndex) || '\u200B' }}
                </span>
              </template>
            </div>

            <!-- 多行块渲染（代码块、表格、列表、引用等） -->
            <div
              v-else
              :key="seg.key"
              class="editor-block"
              :class="{ 'has-selection': isBlockInSelection(seg.startLine, seg.endLine) }"
              :style="getBlockInteractionStyle(seg.key)"
              :data-start-line="seg.startLine"
              :data-end-line="seg.endLine"
              :data-block-type="seg.blockType"
            >
              <div class="block-content rendered-mode entry-content" v-html="renderedSegments[seg.key] || ''"></div>
            </div>
          </template>
          
          <!-- 确保编辑器有最小高度 -->
          <div class="editor-padding"></div>
        </div>
      </div>
    </div>
    
    <!-- 隐藏的文件上传输入框 -->
    <input 
      type="file" 
      ref="fileInput" 
      style="display: none" 
      @change="handleFileChange"
    >

    <!-- 帮助对话框 -->
    <el-dialog
      title="编辑器使用说明"
      :visible.sync="helpDialogVisible"
      width="620px"
      append-to-body
      custom-class="centered-dialog editor-help-dialog"
    >
      <div class="help-content">
        <h3>✨ IR 编辑器特性</h3>
        <ul class="help-list">
          <li><strong>即时渲染</strong>：非编辑行实时显示渲染效果，所见即所得。</li>
          <li><strong>源码编辑</strong>：点击任意行进入源码模式，直接编辑 Markdown。</li>
          <li><strong>块级展开</strong>：编辑列表、代码块、表格时，整个块展开为源码便于查看结构。</li>
          <li><strong>选区展开</strong>：选中多行时（如全选），被选中的块自动展开显示源码。</li>
        </ul>
        <h3>⌨️ 常用快捷键</h3>
        <ul class="help-list">
          <li><strong>加粗</strong>：<code>Ctrl + B</code></li>
          <li><strong>斜体</strong>：<code>Ctrl + I</code></li>
          <li><strong>删除线</strong>：<code>Ctrl + D</code></li>
          <li><strong>行内代码</strong>：<code>Ctrl + `</code></li>
          <li><strong>引用</strong>：<code>Ctrl + Q</code></li>
          <li><strong>有序列表</strong>：<code>Ctrl + O</code></li>
          <li><strong>无序列表</strong>：<code>Ctrl + U</code></li>
          <li><strong>插入链接</strong>：<code>Ctrl + K</code></li>
          <li><strong>保存</strong>：<code>Ctrl + S</code></li>
          <li><strong>撤销 / 重做</strong>：<code>Ctrl + Z</code> / <code>Ctrl + Y</code></li>
          <li><strong>全选</strong>：<code>Ctrl + A</code></li>
          <li><strong>全屏</strong>：<code>F11</code> / <code>Esc</code></li>
        </ul>
        <h3>📝 其他功能</h3>
        <ul class="help-list">
          <li><strong>Markdown 兼容</strong>：支持直接粘贴 Markdown 文本。</li>
          <li><strong>图片上传</strong>：支持截图直接粘贴上传。</li>
          <li><strong>文件上传</strong>：支持粘贴上传文件。</li>
          <li><strong>登录附件</strong>：私有附件使用前端登录拦截；知道真实静态地址仍可直接访问，适合轻量提示场景。</li>
          <li><strong>智能表格</strong>：在表格内点击工具栏表格按钮可添加新行。</li>
        </ul>
        <h3>📊 图表支持</h3>
        <ul class="help-list">
          <li><strong>Mermaid 图表</strong>：使用 <code>```mermaid</code> 代码块渲染流程图、时序图等。</li>
          <li><strong>ECharts 图表</strong>：使用 <code>```echarts</code> 代码块渲染 ECharts 图表。</li>
        </ul>
        <h3>🔄 编辑器对比</h3>
        <p style="margin-bottom: 8px; color: var(--text-color-secondary, #606266); font-size: 13px;">本项目提供多种编辑器，可在<strong>插件管理</strong>中切换：</p>
        <table class="editor-compare-table">
          <thead>
            <tr>
              <th>编辑器</th>
              <th>模式</th>
              <th>特点</th>
            </tr>
          </thead>
          <tbody>
            <tr class="current-editor">
              <td><strong>IR 编辑器</strong></td>
              <td>即时渲染</td>
              <td>源码与渲染自动切换，轻量流畅</td>
            </tr>
            <tr>
              <td><strong>WYSIWYG 编辑器</strong></td>
              <td>所见即所得</td>
              <td>富文本编辑体验，表格右键菜单</td>
            </tr>
            <tr>
              <td><strong>Split 编辑器</strong></td>
              <td>分屏预览</td>
              <td>左右分栏，实时对照，传统体验</td>
            </tr>
            <tr>
              <td><strong>Vditor 编辑器</strong></td>
              <td>多模式可选</td>
              <td>功能丰富，支持 IR/SV/WYSIWYG</td>
            </tr>
          </tbody>
        </table>
      </div>
      <span slot="footer">
        <el-button type="primary" @click="helpDialogVisible = false">知道了</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { DocumentModel } from './core/DocumentModel';
import { CursorManager, Position } from './core/CursorManager';
import { HistoryManager } from './core/HistoryManager';
import { IRRenderer } from './render/IRRenderer';
import { renderMarkdown } from '@/utils/markdownLazyRenderer';
import { downgradeMarkdownHeadings, upgradeMarkdownHeadings } from '@/utils/markdownHeadingUtils';
import { loadMermaidResources, normalizeMermaidDiagrams } from '@/utils/resourceLoaders/mermaidLoader';
import { loadEChartsResources } from '@/utils/resourceLoaders/echartsLoader';
import { parseEChartsOption } from '@/utils/echartsOptionParser';
import { handlePaste as handlePasteUtil } from '@/utils/pasteHandler';
import { initEditorTheme } from '@/utils/useEditorTheme';
// 导入公共编辑器标题样式（CSS 变量定义）
import '@/assets/css/editor-heading-styles.css';

export default {
  name: 'IRMarkdownEditor',
  
  props: {
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '开始写作...'
    },
    height: {
      type: [String, Number],
      default: 500
    },
    showToolbar: {
      type: Boolean,
      default: true
    },
    readonly: {
      type: Boolean,
      default: false
    }
  },

  data() {
    return {
      // 核心模块
      document: null,
      cursor: null,
      history: null,
      renderer: null,
      
      // 状态
      cursorLine: 0,
      cursorColumn: 0,
      selectionStart: null,
      selectionEnd: null,
      
      // 渲染缓存
      renderedSegments: {},
      // 段落/块结构（用于复用文章页样式渲染）
      segments: [],
      // 渲染任务序号（用于避免异步渲染结果“回写覆盖”导致闪烁/退回）
      renderJobId: 0,
      segmentRenderTimer: null,
      
      // 输入法状态
      isComposing: false,
      compositionText: '',
      
      // UI 状态
      isFullscreen: false,
      isDarkMode: false,
      isFocused: false,
      isMouseDown: false,
      isSelecting: false,
      mouseDragActivated: false,
      mouseDownShiftKey: false,
      mouseDownClientX: 0,
      mouseDownClientY: 0,
      mouseSelectionAnchor: null,
      selectionLockedLineHeights: {},
      selectionLockedBlockHeights: {},
      pendingSelectionRender: false,
      pendingRevealFrames: 0,
      
      // 撤销/重做状态
      canUndo: false,
      canRedo: false,
      
      // 文档版本号（用于触发响应式更新）
      documentVersion: 0,
      
      // 主题观察器
      themeObserver: null,
      
      // 原始父节点（用于全屏）
      originalParent: null,
      originalNextSibling: null,
      
      // 标记是否是内部更新，防止循环
      isInternalUpdate: false,
      queuedImageUploads: [],
      pendingForceAttachmentUpload: false,
      pendingPrivateAttachmentUpload: false,
      bootstrapHandle: null,
      
      // 帮助对话框
      helpDialogVisible: false
    };
  },

  computed: {
    containerHeight() {
      if (this.isFullscreen) return '100vh';
      return typeof this.height === 'number' ? `${this.height}px` : this.height;
    },
    
    // 保留 documentVersion 触发更新（segments 在渲染时重建）
    _docVersion() {
      // eslint-disable-next-line no-unused-vars
      const version = this.documentVersion;
      return version;
    }
  },

  watch: {
    value: {
      handler(newVal) {
        // 如果是内部更新导致的 value 变化，不需要再次处理
        if (this.isInternalUpdate) {
          this.isInternalUpdate = false;
          return;
        }
        
        // 从数据库读取时，升级标题后显示
        const displayValue = upgradeMarkdownHeadings(newVal || '');
        
        if (this.document && this.document.getContent() !== displayValue) {
          // 外部值变化，更新文档
          const oldCursorLine = this.cursorLine;
          const oldCursorColumn = this.cursorColumn;
          
          this.document.setContent(displayValue);
          
          // 尝试保持光标位置
          this.cursor.setPosition(
            Math.min(oldCursorLine, this.document.getLineCount() - 1),
            Math.min(oldCursorColumn, this.document.getLineLength(oldCursorLine))
          );
          
          this.renderAllSegments();
        }
      }
    }
  },

  mounted() {
    this.setupThemeObserver();
    this._onWindowResize = () => {
      this.resizeAllECharts();
    };
    window.addEventListener('resize', this._onWindowResize);
    // 加载文章主题到编辑器 CSS 变量
    initEditorTheme();
    this.scheduleBootstrap();
  },

  beforeDestroy() {
    if (this.bootstrapHandle) {
      cancelAnimationFrame(this.bootstrapHandle);
      this.bootstrapHandle = null;
    }
    if (this.segmentRenderTimer) {
      clearTimeout(this.segmentRenderTimer);
      this.segmentRenderTimer = null;
    }
    this.cleanupThemeObserver();
    if (this._onWindowResize) {
      window.removeEventListener('resize', this._onWindowResize);
      this._onWindowResize = null;
    }
    this.disposeAllECharts();
    if (this.document) {
      this.document.removeChangeListener(this.handleDocumentChange);
    }
    if (this.cursor) {
      this.cursor.removeChangeListener(this.handleCursorChange);
    }
    if (this.history) {
      this.history.destroy();
    }
  },

  methods: {
    scheduleBootstrap() {
      const finalizeBootstrap = () => {
        this.bootstrapHandle = null;
        this.initEditor();
        this.$emit('ready', this);
      };

      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        this.bootstrapHandle = window.requestAnimationFrame(finalizeBootstrap);
        return;
      }

      finalizeBootstrap();
    },
    createImageUploadId() {
      return `img-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    },
    createImageUploadToken(uploadId) {
      return `<!--poetize-image-upload:${uploadId}-->`;
    },
    textOffsetFromPosition(line, column) {
      if (!this.document) return 0;
      let offset = 0;
      const safeLine = Math.max(0, Math.min(line, this.document.getLineCount() - 1));
      for (let i = 0; i < safeLine; i++) {
        offset += this.document.getLineLength(i) + 1;
      }
      return offset + Math.max(0, Math.min(column, this.document.getLineLength(safeLine)));
    },
    positionFromTextOffset(offset) {
      if (!this.document) return { line: 0, column: 0 };
      let remaining = Math.max(0, offset);
      const lastLine = Math.max(0, this.document.getLineCount() - 1);
      for (let line = 0; line <= lastLine; line++) {
        const length = this.document.getLineLength(line);
        if (remaining <= length) {
          return { line, column: remaining };
        }
        remaining -= length;
        if (line < lastLine) {
          if (remaining === 0) {
            return { line: line + 1, column: 0 };
          }
          remaining -= 1;
        }
      }
      return { line: lastLine, column: this.document.getLineLength(lastLine) };
    },
    transformOffsetForReplacement(offset, start, end, replacementLength) {
      if (offset <= start) return offset;
      if (offset >= end) return offset + replacementLength - (end - start);
      return start + replacementLength;
    },
    replaceUploadToken(uploadId, replacement) {
      if (!this.document || !this.cursor) return false;
      const token = this.createImageUploadToken(uploadId);
      const content = this.document.getContent();
      const startOffset = content.indexOf(token);
      if (startOffset === -1) return false;

      const endOffset = startOffset + token.length;
      const selection = this.cursor.getSelection();
      const anchor = selection.getAnchor();
      const head = selection.getHead();
      const anchorOffset = this.textOffsetFromPosition(anchor.line, anchor.column);
      const headOffset = this.textOffsetFromPosition(head.line, head.column);

      const startPos = this.positionFromTextOffset(startOffset);
      const endPos = this.positionFromTextOffset(endOffset);
      this.replaceRangeWithText(startPos.line, startPos.column, endPos.line, endPos.column, replacement);

      const nextAnchorOffset = this.transformOffsetForReplacement(anchorOffset, startOffset, endOffset, replacement.length);
      const nextHeadOffset = this.transformOffsetForReplacement(headOffset, startOffset, endOffset, replacement.length);
      const nextAnchor = this.positionFromTextOffset(nextAnchorOffset);
      const nextHead = this.positionFromTextOffset(nextHeadOffset);
      this.cursor.setSelection(nextAnchor.line, nextAnchor.column, nextHead.line, nextHead.column);
      return true;
    },
    queueOrStartImageUpload(file, options = {}) {
      if (!file) return null;
      if (this.isComposing) {
        this.queuedImageUploads.push({ file, options });
        return null;
      }

      const uploadId = this.createImageUploadId();
      const token = this.createImageUploadToken(uploadId);
      this.insertText(token);
      this.$emit('image-add', {
        file,
        uploadId,
        privateAttachment: !!options.privateAttachment,
        forceAttachment: !!options.forceAttachment
      });
      return uploadId;
    },
    flushQueuedImageUploads() {
      if (this.isComposing || !this.queuedImageUploads.length) return;
      const queuedUploads = this.queuedImageUploads.slice();
      this.queuedImageUploads = [];
      queuedUploads.forEach((item) => {
        const upload = item && item.file ? item : { file: item, options: {} };
        this.queueOrStartImageUpload(upload.file, upload.options || {});
      });
    },
    resizeAllECharts() {
      if (!window.echarts) return;
      const container = this.$refs.linesContainer;
      if (!container) return;
      const blocks = container.querySelectorAll('.echarts-render');
      blocks.forEach(el => {
        const chart = window.echarts.getInstanceByDom(el);
        if (chart) {
          try {
            chart.resize();
          } catch (e) {}
        }
      });
    },

    disposeAllECharts() {
      if (!window.echarts) return;
      const container = this.$refs.linesContainer;
      if (!container) return;
      const blocks = container.querySelectorAll('.echarts-render');
      blocks.forEach(el => {
        const chart = window.echarts.getInstanceByDom(el);
        if (chart) {
          try {
            chart.dispose();
          } catch (e) {}
        }
      });
    },

    async renderDiagrams() {
      if (!this.document) return;
      const container = this.$refs.linesContainer;
      if (!container) return;
      const content = this.document.getContent() || '';
      if (!content.includes('```mermaid') && !content.includes('```echarts')) return;

      if (content.includes('```mermaid')) {
        const ok = await loadMermaidResources();
        if (ok && window.mermaid) {
          const mermaidDivs = container.querySelectorAll('.mermaid');
          for (let i = 0; i < mermaidDivs.length; i++) {
            const el = mermaidDivs[i];
            const encoded = el.getAttribute('data-source');
            let code = '';
            if (encoded) {
              try {
                code = decodeURIComponent(encoded);
              } catch (e) {
                code = '';
              }
            }
            if (!code) {
              code = el.textContent || '';
            }
            code = String(code).replace(/\s+$/, '');
            if (!code.trim()) continue;

            const newEncoded = encodeURIComponent(code);
            if (el.getAttribute('data-rendered') === newEncoded) continue;

            el.setAttribute('data-source', newEncoded);
            el.innerHTML = '';

              try {
                const id = `ir-mermaid-${Date.now()}-${i}`;
                const { svg } = await window.mermaid.render(id, code);
                el.classList.add('mermaid-container');
                el.innerHTML = svg;
                normalizeMermaidDiagrams(el);
                el.setAttribute('data-rendered', newEncoded);
              } catch (e) {
              el.textContent = code;
              el.removeAttribute('data-rendered');
            }
          }
        }
      }

      if (content.includes('```echarts')) {
        const ok = await loadEChartsResources();
        if (ok && window.echarts) {
          const blocks = container.querySelectorAll('.echarts-render');
          for (let i = 0; i < blocks.length; i++) {
            const el = blocks[i]
            const encoded = el.getAttribute('data-source');
            let jsonContent = '';
            if (encoded) {
              try {
                jsonContent = decodeURIComponent(encoded);
              } catch (e) {
                jsonContent = '';
              }
            }
            if (!jsonContent) {
              jsonContent = el.textContent || el.innerText || '';
            }
            jsonContent = String(jsonContent).trim();
            if (!jsonContent) continue;

            const newEncoded = encodeURIComponent(jsonContent);
            if (el.getAttribute('data-rendered') === newEncoded && window.echarts.getInstanceByDom(el)) {
              continue;
            }
            el.setAttribute('data-source', newEncoded);

            const existing = window.echarts.getInstanceByDom(el);
            if (existing) {
              try {
                existing.dispose();
              } catch (e) {}
            }

            el.innerHTML = '';
            try {
              const option = await parseEChartsOption(jsonContent);
              const chart = window.echarts.init(el);
              chart.setOption(option, true);
              el.setAttribute('data-rendered', newEncoded);
            } catch (e) {
              el.innerHTML = `<div style="color:red;padding:10px;">ECharts 配置解析失败: ${e.message}</div>`;
              el.removeAttribute('data-rendered');
            }
          }
        }
      }
    },

    goPluginManager() {
      const doNavigate = () => {
        // 如果处于全屏模式，先退出全屏
        if (this.isFullscreen) {
          this.toggleFullscreen();
        }
        this.$router.push({ name: 'pluginManager', query: { type: 'editor' } });
      };

      // 如果有未保存的修改（canUndo 为 true 说明做过编辑操作），弹出确认框
      if (this.canUndo) {
        this.$confirm('当前内容尚未保存，切换编辑器后修改将会丢失，确定要离开吗？', '提示', {
          confirmButtonText: '确定离开',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          doNavigate();
        }).catch(() => {
          // 用户取消，不做任何操作
        });
      } else {
        doNavigate();
      }
    },
    normalizeRange(startLine, startColumn, endLine, endColumn) {
      if (startLine > endLine || (startLine === endLine && startColumn > endColumn)) {
        return { startLine: endLine, startColumn: endColumn, endLine: startLine, endColumn: startColumn };
      }
      return { startLine, startColumn, endLine, endColumn };
    },

    deleteRangeWithHistory(startLine, startColumn, endLine, endColumn) {
      if (!this.document) return { line: startLine, column: startColumn };
      const r = this.normalizeRange(startLine, startColumn, endLine, endColumn);
      const deletedText = this.document.getTextRange(r.startLine, r.startColumn, r.endLine, r.endColumn);
      const newPos = this.document.deleteRange(r.startLine, r.startColumn, r.endLine, r.endColumn);
      if (this.history) {
        this.history.recordDelete(r.startLine, r.startColumn, r.endLine, r.endColumn, deletedText);
      }
      return newPos;
    },

    replaceRangeWithText(startLine, startColumn, endLine, endColumn, text) {
      if (!this.document) return { line: startLine, column: startColumn };
      const r = this.normalizeRange(startLine, startColumn, endLine, endColumn);
      const deletedText = this.document.getTextRange(r.startLine, r.startColumn, r.endLine, r.endColumn);
      this.document.deleteRange(r.startLine, r.startColumn, r.endLine, r.endColumn);
      this.cursor.setPosition(r.startLine, r.startColumn);
      const newPos = this.document.insertText(r.startLine, r.startColumn, text);
      this.cursor.setPosition(newPos.line, newPos.column);
      if (this.history) {
        this.history.recordReplace(r.startLine, r.startColumn, r.endLine, r.endColumn, deletedText, text, newPos.line, newPos.column);
      }
      return newPos;
    },

    /**
     * 初始化编辑器
     */
    initEditor() {
      // 创建文档模型（从数据库读取时，升级标题后显示）
      const displayValue = upgradeMarkdownHeadings(this.value || '');
      this.document = new DocumentModel(displayValue);
      this.document.addChangeListener(this.handleDocumentChange);
      
      // 创建光标管理器
      this.cursor = new CursorManager(this.document);
      this.cursor.addChangeListener(this.handleCursorChange);
      
      // 创建历史管理器
      this.history = new HistoryManager(this.document, this.cursor);
      
      // 创建渲染器
      this.renderer = new IRRenderer();
      
      // 初始渲染
      this.renderAllSegments();
      
      // 更新撤销/重做状态
      this.updateHistoryState();
    },

    /**
     * 获取指定行文本（template 使用）
     */
    getLineText(lineIndex) {
      if (!this.document) return '';
      return this.document.getLineText(lineIndex);
    },
    
    /**
     * 更新撤销/重做状态
     */
    updateHistoryState() {
      if (this.history) {
        this.canUndo = this.history.canUndo();
        this.canRedo = this.history.canRedo();
      }
    },

    /**
     * 设置主题观察器
     */
    setupThemeObserver() {
      this.syncThemeFromDom();
      
      this.themeObserver = new MutationObserver(() => {
        this.syncThemeFromDom();
      });
      
      this.themeObserver.observe(document.documentElement, { 
        attributes: true, 
        attributeFilter: ['class'] 
      });
      this.themeObserver.observe(document.body, { 
        attributes: true, 
        attributeFilter: ['class'] 
      });
    },

    /**
     * 清理主题观察器
     */
    cleanupThemeObserver() {
      if (this.themeObserver) {
        this.themeObserver.disconnect();
        this.themeObserver = null;
      }
    },

    /**
     * 从 DOM 同步主题状态
     */
    syncThemeFromDom() {
      const htmlDark = document.documentElement?.classList.contains('dark-mode');
      const bodyDark = document.body?.classList.contains('dark-mode');
      this.isDarkMode = Boolean(htmlDark || bodyDark);
    },

    /**
     * 处理文档变化
     */
    handleDocumentChange(change) {
      // 更新文档版本号，触发 Vue 响应式更新
      this.documentVersion++;
      
      // 发送内容变化事件（降级标题后发送给父组件，保存到数据库）
      const content = this.document.getContent();
      const downgradedContent = downgradeMarkdownHeadings(content);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedContent);
      this.$emit('change', downgradedContent);
      
      // 根据变化类型更新渲染（块结构可能变化，直接重建并重渲染）
      this.renderAllSegments();
      
      // 更新撤销/重做状态
      this.$nextTick(() => {
        this.updateHistoryState();
      });
    },

    /**
     * 处理光标变化
     */
    handleCursorChange(change) {
      const oldCursorLine = this.cursorLine;
      const hadSelection = !!(this.selectionStart && this.selectionEnd);
      const oldSelStart = this.selectionStart;
      const oldSelEnd = this.selectionEnd;
      
      this.cursorLine = change.position.line;
      this.cursorColumn = change.position.column;
      
      if (change.hasSelection) {
        this.selectionStart = change.selection.getMin();
        this.selectionEnd = change.selection.getMax();
      } else {
        this.selectionStart = null;
        this.selectionEnd = null;
      }
      
      // 检查选区是否变化（用于判断是否需要重渲染块）
      const selectionChanged = hadSelection !== change.hasSelection ||
        (oldSelStart?.line !== this.selectionStart?.line) ||
        (oldSelEnd?.line !== this.selectionEnd?.line);
      
      // 如果光标行变化或选区变化，需要重新渲染
      if (oldCursorLine !== this.cursorLine || selectionChanged) {
        if (this.isSelecting) {
          // 拖拽选区时冻结块折叠/展开，避免布局变化把选区“带跑偏”
          this.pendingSelectionRender = true;
        } else {
          // 光标/选区变化会导致块折叠/展开策略变化。
          // 这里做轻量防抖，减少选中标题/短行时源码态和渲染态来回闪烁。
          this.scheduleSegmentRender({
            delay: selectionChanged ? 60 : 24
          });
        }
      }
      
      // 确保光标可见
      this.$nextTick(() => {
        this.scrollCursorIntoView({ behavior: this.isSelecting ? 'auto' : 'smooth' });
      });
    },

    /**
     * 根据文档结构重建 segments（代码块/表格/引用/列表会折叠为一个块）
     * 注意：若光标或选区在多行块内，为了便于编辑会展开为逐行渲染，显示源码
     */
    rebuildSegments() {
      if (!this.document || !this.renderer) {
        this.segments = [];
        return;
      }

      this.renderer.analyzeBlocks(this.document);
      const blocks = this.renderer.blocks || [];
      const segs = [];

      for (const block of blocks) {
        const startLine = block.startLine;
        const endLine = block.endLine;
        const isMulti = endLine > startLine;
        const cursorInside = this.cursorLine >= startLine && this.cursorLine <= endLine;
        
        // 检查选区是否与块有交集
        const selectionOverlap = this.selectionStart && this.selectionEnd && 
          !(this.selectionEnd.line < startLine || this.selectionStart.line > endLine);

        // 光标在块内 或 选区与块有交集时，展开为单行 segments
        if (isMulti && (cursorInside || selectionOverlap)) {
          for (let i = startLine; i <= endLine; i++) {
            segs.push({ 
              kind: 'line', 
              lineIndex: i,
              // 标记：属于正在编辑的多行块，整个块都显示源码
              inEditingBlock: true,
              blockType: block.type
            });
          }
          continue;
        }

        if (isMulti) {
          segs.push({
            kind: 'block',
            startLine,
            endLine,
            key: `b:${startLine}-${endLine}`,
            blockType: block.type
          });
          continue;
        }

        segs.push({ kind: 'line', lineIndex: startLine });
      }

      this.segments = segs;
    },

    /**
     * 渲染所有 segments：单行使用 markdown-it 渲染；多行块合并渲染
     * 目标：输出与文章页一致的 HTML 结构，让 markdown-highlight.css 生效
     */
    async renderAllSegments() {
      if (!this.document) return;
      const jobId = ++this.renderJobId;

      this.rebuildSegments();

      const rendered = {};
      for (const seg of this.segments) {
        if (seg.kind === 'line') {
          if (this.isFocused && seg.lineIndex === this.cursorLine) continue;

          // 正在编辑的多行块内的行：整个块都显示源码，不渲染 HTML
          // 这样代码块在编辑时不会每行都产生 highlight-wrap
          if (seg.inEditingBlock) continue;
          const text = this.document.getLineText(seg.lineIndex);
          const html = await renderMarkdown(text || '');
          rendered[`l:${seg.lineIndex}`] = this.normalizeSingleLineHTML(html);
          continue;
        }

        // block
        const parts = [];
        for (let i = seg.startLine; i <= seg.endLine; i++) {
          parts.push(this.document.getLineText(i));
        }
        rendered[seg.key] = await renderMarkdown(parts.join('\n'));
      }

      // 只应用最新一次渲染，避免旧任务覆盖导致“点进去又退出来/闪烁”
      if (jobId !== this.renderJobId) return;
      this.renderedSegments = rendered;
      this.$nextTick(async () => {
        if (jobId !== this.renderJobId) return;
        await this.renderDiagrams();
      });
    },

    /**
     * 将单行 markdown-it 输出转成更适合“行级显示”的 HTML
     * - 若是单个 <p> 包裹，则去掉外层 <p>，避免每行都变成段落产生大高度
     * - 其他块级输出（如 <h1>/<hr> 等）保持不变
     */
    normalizeSingleLineHTML(html) {
      const trimmed = String(html || '').trim();
      const m = trimmed.match(/^<p>([\s\S]*)<\/p>$/);
      if (m) return m[1];
      return trimmed;
    },

    /**
     * 获取行的字符数组
     */
    getLineChars(lineIndex) {
      const text = this.document.getLineText(lineIndex);
      return text.split('');
    },

    /**
     * 获取行长度
     */
    getLineLength(lineIndex) {
      return this.document.getLineLength(lineIndex);
    },

    /**
     * 检查字符是否被选中
     */
    isCharSelected(lineIndex, charIndex) {
      if (!this.isFocused) return false;
      if (!this.selectionStart || !this.selectionEnd) return false;
      
      const pos = new Position(lineIndex, charIndex);
      const start = this.selectionStart;
      const end = this.selectionEnd;
      
      // 在选区范围内
      if (lineIndex < start.line || lineIndex > end.line) return false;
      if (lineIndex === start.line && charIndex < start.column) return false;
      if (lineIndex === end.line && charIndex >= end.column) return false;
      
      return true;
    },

    /**
     * 检查行是否在选区内
     */
    isLineInSelection(lineIndex) {
      if (!this.isFocused) return false;
      if (!this.selectionStart || !this.selectionEnd) return false;
      return lineIndex >= this.selectionStart.line && lineIndex <= this.selectionEnd.line;
    },

    isBlockInSelection(startLine, endLine) {
      if (!this.isFocused) return false;
      if (!this.selectionStart || !this.selectionEnd) return false;
      return !(this.selectionEnd.line < startLine || this.selectionStart.line > endLine);
    },

    captureSelectionLayoutLock() {
      const container = this.$refs.linesContainer;
      if (!container) return;

      const lockedLineHeights = {};
      const lockedBlockHeights = {};

      const lineElements = container.querySelectorAll('.editor-line[data-line-index]');
      lineElements.forEach((el) => {
        const lineIndex = Number(el.dataset.lineIndex);
        const height = Math.ceil(el.getBoundingClientRect().height);
        if (!Number.isNaN(lineIndex) && height > 0) {
          lockedLineHeights[lineIndex] = height;
        }
      });

      const blockElements = container.querySelectorAll('.editor-block[data-start-line][data-end-line]');
      blockElements.forEach((el) => {
        const startLine = Number(el.dataset.startLine);
        const endLine = Number(el.dataset.endLine);
        const height = Math.ceil(el.getBoundingClientRect().height);
        if (!Number.isNaN(startLine) && !Number.isNaN(endLine) && height > 0) {
          lockedBlockHeights[`b:${startLine}-${endLine}`] = height;
        }
      });

      this.selectionLockedLineHeights = lockedLineHeights;
      this.selectionLockedBlockHeights = lockedBlockHeights;
    },

    clearSelectionLayoutLock() {
      this.selectionLockedLineHeights = {};
      this.selectionLockedBlockHeights = {};
    },

    getLineInteractionStyle(lineIndex) {
      if (!this.isSelecting) return null;
      const lockedHeight = this.selectionLockedLineHeights[lineIndex];
      if (!lockedHeight) return null;
      return {
        minHeight: `${lockedHeight}px`
      };
    },

    getBlockInteractionStyle(blockKey) {
      if (!this.isSelecting) return null;
      const lockedHeight = this.selectionLockedBlockHeights[blockKey];
      if (!lockedHeight) return null;
      return {
        minHeight: `${lockedHeight}px`
      };
    },

    /**
     * 判断行是否应该显示源码
     * 规则：
     * 1. 是当前光标行
     * 2. 在当前选区范围内
     */
    shouldShowSource(lineIndex) {
      if (!this.isFocused) {
        return false;
      }
      // 拖拽选区过程中，已经进入选区的单行内容直接切到源码态，
      // 这样字符级“文本选中”能立刻出现，不必等 mouseup。
      // 配合上面的重渲染冻结，可避免之前那种来回闪烁。
      if (this.isSelecting) {
        return lineIndex === this.cursorLine || this.isLineInSelection(lineIndex);
      }

      // 规则1：当前光标行
      if (lineIndex === this.cursorLine) return true;
      
      // 规则2：在选区范围内
      return this.isLineInSelection(lineIndex);
    },

    /**
     * 滚动使光标可见
     */
    scrollCursorIntoView(options = {}) {
      const container = this.$refs.editorContent;
      const lineElement = container?.querySelector(`[data-line-index="${this.cursorLine}"]`);
      const behavior = options.behavior || 'smooth';
      
      if (lineElement) {
        const containerRect = container.getBoundingClientRect();
        const lineRect = lineElement.getBoundingClientRect();
        
        if (lineRect.top < containerRect.top) {
          lineElement.scrollIntoView({ block: 'start', behavior });
        } else if (lineRect.bottom > containerRect.bottom) {
          lineElement.scrollIntoView({ block: 'end', behavior });
        }
      }
    },

    revealCursorAfterLayout(frameCount = 3) {
      const frames = Math.max(1, frameCount);
      this.pendingRevealFrames = Math.max(this.pendingRevealFrames, frames);

      const tick = () => {
        this.$nextTick(() => {
          this.scrollCursorIntoView({ behavior: 'auto' });
          this.pendingRevealFrames -= 1;
          if (this.pendingRevealFrames > 0) {
            requestAnimationFrame(tick);
          } else {
            this.pendingRevealFrames = 0;
          }
        });
      };

      if (this.pendingRevealFrames === frames) {
        requestAnimationFrame(tick);
      }
    },

    scheduleSegmentRender(options = {}) {
      const delay = Math.max(0, options.delay ?? 40);
      if (this.segmentRenderTimer) {
        clearTimeout(this.segmentRenderTimer);
      }
      this.segmentRenderTimer = setTimeout(() => {
        this.segmentRenderTimer = null;
        this.renderAllSegments();
        this.documentVersion++;
      }, delay);
    },

    flushPendingSelectionRender() {
      if (!this.pendingSelectionRender) return;
      this.pendingSelectionRender = false;
      this.scheduleSegmentRender({ delay: 50 });
      this.revealCursorAfterLayout(2);
    },

    // ==================== 输入处理 ====================

    /**
     * 聚焦编辑器
     */
    focus() {
      this.$refs.hiddenInput?.focus();
    },

    /**
     * 失焦编辑器
     */
    blur() {
      this.$refs.hiddenInput?.blur();
    },

    handleEditorFocus() {
      this.isFocused = true;
      this.$emit('focus');
      this.scheduleSegmentRender({ delay: 0 });
    },

    handleEditorBlur() {
      this.isFocused = false;
      this.$emit('blur');
      this.scheduleSegmentRender({ delay: 0 });
    },

    /**
     * 处理键盘按下
     */
    handleKeydown(e) {
      if (this.readonly) return;
      
      const { key, ctrlKey, metaKey, shiftKey, altKey } = e;
      const modKey = ctrlKey || metaKey;
      const keyLower = String(key || '').toLowerCase();
      
      // 输入法期间不处理部分按键（除非是快捷键组合）
      if (this.isComposing && !modKey && !['Escape'].includes(key)) {
        return;
      }
      
      // 方向键
      if (key === 'ArrowLeft') {
        e.preventDefault();
        if (modKey) {
          this.cursor.moveWordLeft(shiftKey);
        } else {
          this.cursor.moveLeft(shiftKey);
        }
      } else if (key === 'ArrowRight') {
        e.preventDefault();
        if (modKey) {
          this.cursor.moveWordRight(shiftKey);
        } else {
          this.cursor.moveRight(shiftKey);
        }
      } else if (key === 'ArrowUp') {
        e.preventDefault();
        this.cursor.moveUp(shiftKey);
      } else if (key === 'ArrowDown') {
        e.preventDefault();
        this.cursor.moveDown(shiftKey);
      }
      // Home / End
      else if (key === 'Home') {
        e.preventDefault();
        if (modKey) {
          this.cursor.moveToDocumentStart(shiftKey);
        } else {
          this.cursor.moveToLineStart(shiftKey);
        }
      } else if (key === 'End') {
        e.preventDefault();
        if (modKey) {
          this.cursor.moveToDocumentEnd(shiftKey);
        } else {
          this.cursor.moveToLineEnd(shiftKey);
        }
      }
      // 回车
      else if (key === 'Enter') {
        e.preventDefault();
        this.handleEnter();
      }
      // 退格
      else if (key === 'Backspace') {
        e.preventDefault();
        this.handleBackspace();
      }
      // 删除
      else if (key === 'Delete') {
        e.preventDefault();
        this.handleDelete();
      }
      // Tab
      else if (key === 'Tab') {
        e.preventDefault();
        this.handleTab(shiftKey);
      }
      // 快捷键
      else if (modKey) {
        if (keyLower === 'a') {
          e.preventDefault();
          this.cursor.selectAll();
        } else if (keyLower === 'z') {
          e.preventDefault();
          if (shiftKey) {
            this.redo();
          } else {
            this.undo();
          }
        } else if (keyLower === 'y') {
          e.preventDefault();
          this.redo();
        } else if (keyLower === 's') {
          e.preventDefault();
          this.$emit('save');
        }
        // 格式化快捷键
        else if (keyLower === 'b') {
          e.preventDefault();
          this.insertFormat('bold');
        } else if (keyLower === 'i') {
          e.preventDefault();
          this.insertFormat('italic');
        } else if (keyLower === 'k') {
          e.preventDefault();
          this.insertFormat('link');
        }
      }
      // Escape
      else if (key === 'Escape') {
        if (this.isFullscreen) {
          this.toggleFullscreen();
        } else if (this.cursor.hasSelection()) {
          this.cursor.clearSelection();
        }
      }
      // F11 全屏
      else if (key === 'F11') {
        e.preventDefault();
        this.toggleFullscreen();
      }
    },

    /**
     * 处理输入
     */
    handleInput(e) {
      if (this.readonly || this.isComposing) return;
      
      const text = e.target.value;
      if (text) {
        this.insertText(text);
        e.target.value = '';
      }
    },

    /**
     * 处理输入法开始
     */
    handleCompositionStart() {
      this.isComposing = true;
      this.compositionText = '';
    },

    /**
     * 处理输入法更新
     */
    handleCompositionUpdate(e) {
      this.compositionText = e.data || '';
    },

    /**
     * 处理输入法结束
     */
    handleCompositionEnd(e) {
      this.isComposing = false;
      const text = e.data;
      this.compositionText = '';
      
      if (text) {
        this.insertText(text);
      }
      
      // 清空输入框
      this.$refs.hiddenInput.value = '';
      this.$nextTick(() => {
        this.flushQueuedImageUploads();
      });
    },

    /**
     * 插入文本
     */
    insertText(text) {
      if (!this.document) return;
      if (!text) return;

      if (this.cursor.hasSelection()) {
        const sel = this.cursor.getSelection();
        const min = sel.getMin();
        const max = sel.getMax();
        this.replaceRangeWithText(min.line, min.column, max.line, max.column, text);
        return;
      }

      const pos = this.cursor.getPosition();
      const newPos = this.document.insertText(pos.line, pos.column, text);
      this.cursor.setPosition(newPos.line, newPos.column);
      if (this.history) {
        this.history.recordInsert(pos.line, pos.column, text, newPos.line, newPos.column);
      }
      if (text.includes('\n') || text.length > 80) {
        this.revealCursorAfterLayout(4);
      }
    },

    /**
     * 处理回车
     */
    handleEnter() {
      this.insertText('\n');
    },

    /**
     * 处理退格
     */
    handleBackspace() {
      if (this.cursor.hasSelection()) {
        // 删除选区
        const sel = this.cursor.getSelection();
        const min = sel.getMin();
        const max = sel.getMax();
        this.deleteRangeWithHistory(min.line, min.column, max.line, max.column);
        this.cursor.setPosition(min.line, min.column);
      } else {
        const pos = this.cursor.getPosition();
        
        if (pos.column > 0) {
          // 删除前一个字符
          this.deleteRangeWithHistory(pos.line, pos.column - 1, pos.line, pos.column);
          this.cursor.setPosition(pos.line, pos.column - 1);
        } else if (pos.line > 0) {
          // 合并到上一行
          const prevLineLen = this.document.getLineLength(pos.line - 1);
          const newPos = this.deleteRangeWithHistory(pos.line - 1, prevLineLen, pos.line, 0);
          this.cursor.setPosition(newPos.line, newPos.column);
        }
      }
    },

    /**
     * 处理删除
     */
    handleDelete() {
      if (this.cursor.hasSelection()) {
        // 删除选区
        const sel = this.cursor.getSelection();
        const min = sel.getMin();
        const max = sel.getMax();
        this.deleteRangeWithHistory(min.line, min.column, max.line, max.column);
        this.cursor.setPosition(min.line, min.column);
      } else {
        const pos = this.cursor.getPosition();
        const lineLength = this.document.getLineLength(pos.line);
        
        if (pos.column < lineLength) {
          // 删除后一个字符
          this.deleteRangeWithHistory(pos.line, pos.column, pos.line, pos.column + 1);
        } else if (pos.line < this.document.getLineCount() - 1) {
          // 合并下一行
          this.deleteRangeWithHistory(pos.line, lineLength, pos.line + 1, 0);
        }
      }
    },

    /**
     * 处理 Tab
     */
    handleTab(shiftKey) {
      if (shiftKey) {
        // Shift+Tab: 减少缩进（暂不实现）
      } else {
        // Tab: 插入两个空格
        this.insertText('  ');
      }
    },

    // ==================== 鼠标处理 ====================

    /**
     * 处理鼠标按下
     */
    handleMouseDown(e) {
      if (e.button !== 0) return; // 只处理左键
      // 如果是触摸产生的合成 mousedown，跳过（已在 touchend 中处理）
      if (this._isTouching) return;

      this.isMouseDown = true;
      this.isSelecting = false;
      this.mouseDragActivated = false;
      this.mouseDownShiftKey = !!e.shiftKey;
      this.mouseDownClientX = e.clientX;
      this.mouseDownClientY = e.clientY;
      this.mouseSelectionAnchor = null;
      this.focus();
      
      const pos = this.getPositionFromEvent(e);
      if (pos) {
        // 成功获取位置后再阻止默认行为
        e.preventDefault();
        e.stopPropagation();
        
        if (e.shiftKey) {
          // Shift+点击：扩展选区
          this.cursor.setSelection(
            this.cursor.getSelection().start.line,
            this.cursor.getSelection().start.column,
            pos.line,
            pos.column
          );
        } else {
          // 普通点击：设置光标
          this.cursor.setPosition(pos.line, pos.column);
        }

        const selection = this.cursor.getSelection();
        this.mouseSelectionAnchor = selection.start.clone();
      }
    },

    /**
     * 处理鼠标移动
     */
    handleMouseMove(e) {
      if (!this.isMouseDown) return;
      e.preventDefault();
      
      const pos = this.getPositionFromEvent(e);
      if (pos) {
        const dx = e.clientX - this.mouseDownClientX;
        const dy = e.clientY - this.mouseDownClientY;
        const movedEnough = (dx * dx + dy * dy) > 9;

        if (!this.mouseDragActivated) {
          const startPos = this.mouseSelectionAnchor;
          const changedPosition = startPos &&
            (startPos.line !== pos.line || startPos.column !== pos.column);

          if (!movedEnough && !changedPosition) {
            return;
          }

          this.mouseDragActivated = true;
          this.captureSelectionLayoutLock();
          this.isSelecting = true;
        }

        // 扩展选区
        const anchor = this.mouseSelectionAnchor || this.cursor.getSelection().start;
        this.cursor.setSelection(anchor.line, anchor.column, pos.line, pos.column);
      }
    },

    /**
     * 处理鼠标释放
     */
    handleMouseUp(e) {
      this.isMouseDown = false;
      const hadDragSelection = this.mouseDragActivated;
      this.isSelecting = false;
      if (e && hadDragSelection) {
        const pos = this.getPositionFromEvent(e);
        if (pos) {
          const anchor = this.mouseSelectionAnchor || this.cursor.getSelection().start;
          this.cursor.setSelection(anchor.line, anchor.column, pos.line, pos.column);
        }
      }
      if (hadDragSelection) {
        this.flushPendingSelectionRender();
      } else if (!this.mouseDownShiftKey) {
        this.pendingSelectionRender = false;
      }
      this.clearSelectionLayoutLock();
      this.mouseDragActivated = false;
      this.mouseDownShiftKey = false;
      this.mouseSelectionAnchor = null;
    },

    /**
     * 处理点击
     */
    handleClick(e) {
      this.focus();
    },

    /**
     * 处理双击
     */
    handleDoubleClick(e) {
      const pos = this.getPositionFromEvent(e);
      if (pos) {
        this.cursor.setPosition(pos.line, pos.column);
        this.cursor.selectWord();
      }
    },

    // ==================== 触摸事件处理（移动端支持）====================

    /**
     * 处理触摸开始
     * 只记录起始信息，不做任何光标/选区操作，以免干扰滚动。
     */
    handleTouchStart(e) {
      if (this.readonly) return;

      const touch = e.touches[0];
      this._touchStartX = touch.clientX;
      this._touchStartY = touch.clientY;
      this._touchStartTime = Date.now();
      this._touchMoved = false;
      // 标记当前正处于触摸交互中，用于 handleSelectStart / handleMouseDown 判断
      this._isTouching = true;
    },

    /**
     * 处理触摸移动
     * 只做一件事：判断手指是否产生了足够的位移（>8px）。
     * 如果是，则标记为滚动手势，完全放行给浏览器处理。
     */
    handleTouchMove(e) {
      if (this.readonly) return;
      if (this._touchMoved) return; // 已经判定为滚动，后续 move 不用再算

      const touch = e.touches[0];
      const dx = touch.clientX - (this._touchStartX || 0);
      const dy = touch.clientY - (this._touchStartY || 0);
      if (dx * dx + dy * dy > 64) { // 8px 阈值 (64 = 8²)
        this._touchMoved = true;
      }
    },

    /**
     * 处理触摸结束
     * 核心逻辑：未滑动 → 视为"点击"，设置光标并聚焦。
     * 滑动了 → 什么都不做，浏览器已经完成了滚动。
     * 
     * 不调用 e.preventDefault()，让后续 click 事件自然触发。
     * 通过 _isTouching 标记让 handleMouseDown 知道这次 mousedown
     * 来自触摸，从而跳过重复处理。
     */
    handleTouchEnd(e) {
      if (this.readonly) {
        this._isTouching = false;
        return;
      }

      if (!this._touchMoved) {
        // 未滑动 → 当作点击处理
        this.focus();

        // touchend 的 e.changedTouches 有最后的坐标
        const touch = e.changedTouches && e.changedTouches[0];
        const cx = touch ? touch.clientX : this._touchStartX;
        const cy = touch ? touch.clientY : this._touchStartY;
        const pos = this.getPositionFromEvent({ clientX: cx, clientY: cy });

        if (pos) {
          // 双击检测
          const now = Date.now();
          if (this._lastTapTime && (now - this._lastTapTime) < 400) {
            this.cursor.setPosition(pos.line, pos.column);
            this.cursor.selectWord();
            this._lastTapTime = 0;
          } else {
            this.cursor.setPosition(pos.line, pos.column);
            this._lastTapTime = now;
          }
        }

        // 阻止后续的合成 mouse 事件，避免 handleMouseDown 重复处理
        e.preventDefault();
      }

      // 延迟重置 _isTouching，因为合成的 mousedown 可能在 touchend 之后
      // 同步触发（部分浏览器），也可能在下一个事件循环触发
      setTimeout(() => {
        this._isTouching = false;
      }, 50);
    },

    /**
     * 阻止浏览器默认选择行为
     * 触摸滚动进行中时放行，其余情况阻止（由编辑器自己管理选区）。
     */
    handleSelectStart(e) {
      if (this._touchMoved) return;
      e.preventDefault();
    },

    /**
     * 从鼠标/触摸事件获取文档位置
     */
    getPositionFromEvent(e) {
      const container = this.$refs.linesContainer;
      if (!container) return null;
      
      const { clientX, clientY } = e;
      
      // 找到点击的行元素
      const items = Array.from(container.querySelectorAll('.editor-line, .editor-block'));
      let targetEl = null;
      let nearestEl = null;
      let nearestDistance = Infinity;
      
      for (const item of items) {
        const r = item.getBoundingClientRect();
        if (clientY >= r.top && clientY < r.bottom) {
          targetEl = item;
          break;
        }

        const distance = clientY < r.top ? (r.top - clientY) : (clientY - r.bottom);
        if (distance < nearestDistance) {
          nearestDistance = distance;
          nearestEl = item;
        }
      }
      
      if (!targetEl && items.length > 0) {
        // 鼠标落在两个元素之间的空白区时，取“最近的那个元素”。
        // 旧逻辑会直接兜底到最后一个元素，导致轻微下拖就选中几百行。
        targetEl = nearestEl || items[0];
      }
      
      if (!targetEl) return new Position(0, 0);

      // 块渲染
      if (targetEl.classList.contains('editor-block')) {
        const start = Number(targetEl.dataset.startLine || 0);
        const end = Number(targetEl.dataset.endLine || start);
        const type = targetEl.dataset.blockType || '';
        
        const r = targetEl.getBoundingClientRect();
        const ratio = (clientY - r.top) / Math.max(1, r.height);
        let lineIndex = start + Math.floor(ratio * (end - start + 1));
        lineIndex = Math.min(lineIndex, end);
        
        if ((type === 'code_block' || type === 'mermaid' || type === 'echarts') && lineIndex === start && end > start) {
          lineIndex = start + 1;
        }
        return new Position(lineIndex, 0);
      }

      const lineIndex = Number(targetEl.dataset.lineIndex || 0);
      const lineText = this.document.getLineText(lineIndex);
      
      // 渲染模式：通过测量元素精确计算点击位置
      if (targetEl.querySelector('.rendered-mode')) {
        const contentEl = targetEl.querySelector('.line-content') || targetEl;
        const column = this.measureColumnAtPoint(clientX, clientY, contentEl, lineText);
        return new Position(lineIndex, column);
      }
      
      // 源码模式：遍历字符元素定位
      const column = this.getColumnAtPoint(clientX, clientY, targetEl, lineText.length);
      return new Position(lineIndex, column);
    },
    
    /**
     * 通过测量元素精确计算点击位置对应的列索引
     * 模拟源码模式的渲染方式，逐字符测量
     */
    measureColumnAtPoint(clientX, clientY, contentEl, lineText) {
      if (!lineText) return 0;
      
      const contentRect = contentEl.getBoundingClientRect();
      const clickX = clientX - contentRect.left;
      const clickY = clientY - contentRect.top;
      
      if (clickX <= 0 && clickY <= 0) return 0;
      
      // 创建测量容器，完全模拟源码模式的样式
      const container = document.createElement('div');
      container.style.cssText = `
        position: absolute;
        left: -9999px;
        top: 0;
        width: ${contentRect.width}px;
        font-family: Consolas, Monaco, 'Courier New', monospace;
        font-size: 16px;
        line-height: 1.6;
        white-space: pre-wrap;
        word-wrap: break-word;
      `;
      
      // 为每个字符创建 span，和源码模式一致
      for (let i = 0; i < lineText.length; i++) {
        const span = document.createElement('span');
        span.className = 'measure-char';
        span.textContent = lineText[i] === '' ? '\u200B' : lineText[i];
        container.appendChild(span);
      }
      
      document.body.appendChild(container);
      
      // 遍历测量的字符，找到最匹配点击位置的
      const charSpans = container.querySelectorAll('.measure-char');
      let result = 0;
      let found = false;
      
      for (let i = 0; i < charSpans.length; i++) {
        const rect = charSpans[i].getBoundingClientRect();
        // 转换为相对于容器的坐标
        const charLeft = rect.left - container.getBoundingClientRect().left;
        const charTop = rect.top - container.getBoundingClientRect().top;
        const charRight = charLeft + rect.width;
        const charBottom = charTop + rect.height;
        
        // 检查 Y 是否在这个字符的行内
        if (clickY >= charTop && clickY <= charBottom) {
          // 在同一视觉行
          if (clickX < charLeft) {
            // 点击在字符左边，检查是否是该视觉行的第一个字符
            if (i === 0) {
              result = 0;
              found = true;
              break;
            }
            const prevRect = charSpans[i - 1].getBoundingClientRect();
            const prevBottom = prevRect.top - container.getBoundingClientRect().top + prevRect.height;
            if (prevBottom <= charTop) {
              // 前一个字符在上一行，这是当前视觉行的开头
              result = i;
              found = true;
              break;
            }
          } else if (clickX <= charRight) {
            // 点击在字符内部
            const charCenter = charLeft + rect.width / 2;
            result = clickX < charCenter ? i : i + 1;
            found = true;
            break;
          }
          // 点击在字符右边，继续检查下一个字符
        }
      }
      
      // 如果没找到精确匹配（点击在最后一行的末尾之后）
      if (!found) {
        result = lineText.length;
      }
      
      document.body.removeChild(container);
      return Math.min(result, lineText.length);
    },
    
    /**
     * 遍历字符元素，找到点击位置对应的列索引
     * 支持自动换行的情况
     */
    getColumnAtPoint(clientX, clientY, lineEl, lineLength) {
      const charSpans = lineEl.querySelectorAll('.char');
      if (charSpans.length === 0) return 0;
      
      // 1. 先找到点击所在的视觉行（Y坐标匹配的字符）
      // 2. 再在该视觉行中找到最近的字符（X坐标匹配）
      
      let bestIndex = 0;
      let bestDistance = Infinity;
      
      for (let i = 0; i < charSpans.length; i++) {
        const rect = charSpans[i].getBoundingClientRect();
        
        // 检查 Y 是否在这个字符的行内
        if (clientY >= rect.top && clientY <= rect.bottom) {
          // 在同一视觉行，检查 X
          if (clientX < rect.left) {
            // 点击在字符左边，检查是否是该视觉行的第一个字符
            // （即前一个字符在上一行）
            if (i === 0 || charSpans[i - 1].getBoundingClientRect().bottom <= rect.top) {
              return i; // 这是换行后新行的开头
            }
          } else if (clientX <= rect.right) {
            // 点击在字符内部
            return clientX < rect.left + rect.width / 2 ? i : i + 1;
          }
          // 点击在字符右边，继续找这一视觉行的下一个字符
        }
        
        // 计算距离，用于没有精确匹配时的降级
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;
        const dist = Math.abs(clientX - centerX) + Math.abs(clientY - centerY) * 2; // Y权重更高
        
        if (dist < bestDistance) {
          bestDistance = dist;
          bestIndex = clientX < centerX ? i : i + 1;
        }
      }
      
      return Math.min(bestIndex, lineLength);
    },

    /**
     * 处理滚动
     */
    handleScroll() {
      // 可以在这里实现懒加载渲染
    },

    // ==================== 剪贴板处理 ====================

    /**
     * 处理粘贴（支持图片上传 + HTML转Markdown）
     */
    handlePaste(e) {
      handlePasteUtil(e, {
        onFile: (file) => {
          this.queueOrStartImageUpload(file);
        },
        onText: (text) => {
          this.insertText(text);
          this.revealCursorAfterLayout(5);
        }
      });
    },

    /**
     * 处理复制
     */
    handleCopy(e) {
      if (!this.cursor.hasSelection()) return;
      
      e.preventDefault();
      
      const sel = this.cursor.getSelection();
      const min = sel.getMin();
      const max = sel.getMax();
      const text = this.document.getTextRange(min.line, min.column, max.line, max.column);
      
      e.clipboardData.setData('text/plain', text);
    },

    /**
     * 处理剪切
     */
    handleCut(e) {
      if (!this.cursor.hasSelection()) return;
      
      this.handleCopy(e);
      
      const sel = this.cursor.getSelection();
      const min = sel.getMin();
      const max = sel.getMax();
      this.deleteRangeWithHistory(min.line, min.column, max.line, max.column);
      this.cursor.setPosition(min.line, min.column);
    },

    // ==================== 格式化 ====================

    /**
     * 插入格式化标记
     */
    insertFormat(type) {
      if (this.readonly) return;
      this.focus();

      const sel = this.cursor.getSelection();
      const hasSelection = this.cursor.hasSelection();
      const pos = this.cursor.getPosition();
      
      let prefix = '';
      let suffix = '';
      let placeholder = '';
      let isBlockFormat = false;
      
      switch (type) {
        case 'bold':
          prefix = '**';
          suffix = '**';
          placeholder = '粗体文本';
          break;
        case 'italic':
          prefix = '*';
          suffix = '*';
          placeholder = '斜体文本';
          break;
        case 'strike':
          prefix = '~~';
          suffix = '~~';
          placeholder = '删除线文本';
          break;
        case 'inline-code':
          prefix = '`';
          suffix = '`';
          placeholder = '行内代码';
          break;
        case 'link':
          prefix = '[';
          suffix = '](url)';
          placeholder = '链接文本';
          break;
        case 'heading':
          this.insertHeading();
          return;
        case 'quote':
          this.insertBlockPrefix('> ');
          return;
        case 'ul':
          this.insertBlockPrefix('- ');
          return;
        case 'ol':
          this.insertBlockPrefix('1. ');
          return;
        case 'todo':
          this.insertBlockPrefix('- [ ] ');
          return;
        case 'code':
          this.insertCodeBlock();
          return;
        case 'table':
          this.insertTable();
          return;
      }
      
      if (hasSelection) {
        const min = sel.getMin();
        const max = sel.getMax();
        const selectedText = this.document.getTextRange(min.line, min.column, max.line, max.column);
        this.replaceRangeWithText(min.line, min.column, max.line, max.column, prefix + selectedText + suffix);
      } else {
        // 无选区，插入模板
        this.insertText(prefix + placeholder + suffix);
        
        // 选中占位符
        const newPos = this.cursor.getPosition();
        this.cursor.setSelection(
          newPos.line,
          newPos.column - suffix.length - placeholder.length,
          newPos.line,
          newPos.column - suffix.length
        );
      }
    },
    
    /**
     * 插入标题（智能切换级别）
     */
    insertHeading(level = null) {
      const pos = this.cursor.getPosition();
      const lineText = this.document.getLineText(pos.line);
      
      // 检测当前行是否已有标题
      const headingMatch = lineText.match(/^(#{1,6})\s/);
      
      let newText;
      if (headingMatch) {
        const currentLevel = headingMatch[1].length;
        if (level !== null) {
          // 指定级别
          newText = '#'.repeat(level) + ' ' + lineText.substring(headingMatch[0].length);
        } else {
          // 自动切换：升级，超过6级则取消
          if (currentLevel >= 6) {
            newText = lineText.substring(headingMatch[0].length);
          } else {
            newText = '#'.repeat(currentLevel + 1) + ' ' + lineText.substring(headingMatch[0].length);
          }
        }
      } else {
        // 添加标题
        const targetLevel = level || 1;
        newText = '#'.repeat(targetLevel) + ' ' + (lineText || '标题');
      }
      
      const oldText = lineText;
      this.document.setLineText(pos.line, newText);
      this.cursor.setPosition(pos.line, newText.length);
      if (this.history) {
        this.history.recordReplace(pos.line, 0, pos.line, oldText.length, oldText, newText, pos.line, newText.length);
      }
    },
    
    /**
     * 插入块级前缀（引用、列表等）
     */
    insertBlockPrefix(prefix) {
      const pos = this.cursor.getPosition();
      const lineText = this.document.getLineText(pos.line);
      const oldText = lineText;
      let newText = lineText;
      
      // 检查是否已有相同前缀
      if (lineText.startsWith(prefix)) {
        // 取消前缀
        newText = lineText.substring(prefix.length);
        this.document.setLineText(pos.line, newText);
        this.cursor.setPosition(pos.line, Math.max(0, pos.column - prefix.length));
      } else {
        // 添加前缀
        newText = prefix + lineText;
        this.document.setLineText(pos.line, newText);
        this.cursor.setPosition(pos.line, pos.column + prefix.length);
      }
      if (this.history && oldText !== newText) {
        this.history.recordReplace(pos.line, 0, pos.line, oldText.length, oldText, newText, pos.line, newText.length);
      }
    },
    
    /**
     * 插入代码块
     */
    insertCodeBlock() {
      const pos = this.cursor.getPosition();
      const hasSelection = this.cursor.hasSelection();
      
      if (hasSelection) {
        const sel = this.cursor.getSelection();
        const min = sel.getMin();
        const max = sel.getMax();
        const selectedText = this.document.getTextRange(min.line, min.column, max.line, max.column);
        this.replaceRangeWithText(min.line, min.column, max.line, max.column, '```语言\n' + selectedText + '\n```');
      } else {
        this.insertText('```语言\n代码\n```');
        // 定位到语言位置
        this.cursor.setSelection(pos.line, 3, pos.line, 5);
      }
    },
    
    /**
     * 智能插入表格：如果光标在表格内则添加新行，否则插入新表格
     */
    insertTable() {
      const pos = this.cursor.getPosition();
      const lineCount = this.document.getLineCount();
      
      // 判断是否是表格行
      const isTableLine = (lineIndex) => {
        if (lineIndex < 0 || lineIndex >= lineCount) return false;
        const line = this.document.getLineText(lineIndex);
        return /^\s*\|.*\|\s*$/.test(line);
      };
      
      // 判断是否是分隔行
      const isSeparatorLine = (lineIndex) => {
        if (lineIndex < 0 || lineIndex >= lineCount) return false;
        const line = this.document.getLineText(lineIndex);
        return /^\s*\|(\s*:?-{3,}:?\s*\|)+\s*$/.test(line);
      };
      
      // 获取列数
      const getColumnCount = (lineIndex) => {
        const line = this.document.getLineText(lineIndex);
        const cells = line.trim().split('|').map(s => s.trim()).filter(Boolean);
        return cells.length;
      };
      
      // 检查当前行是否在表格内
      if (isTableLine(pos.line)) {
        // 找到表格的起始和结束行
        let blockStart = pos.line;
        while (blockStart > 0 && isTableLine(blockStart - 1)) blockStart--;
        let blockEnd = pos.line + 1;
        while (blockEnd < lineCount && isTableLine(blockEnd)) blockEnd++;
        
        // 确认是有效的表格（有表头和分隔行）
        if (blockEnd - blockStart >= 2 && isSeparatorLine(blockStart + 1)) {
          const colCount = getColumnCount(blockStart) || 3;
          const rowLine = '| ' + Array(colCount).fill('内容').join(' | ') + ' |';
          
          // 在表格末尾添加新行
          const lastLineText = this.document.getLineText(blockEnd - 1);
          this.cursor.setPosition(blockEnd - 1, lastLineText.length);
          this.insertText('\n' + rowLine);
          
          // 定位到新行的第一个单元格
          this.$nextTick(() => {
            this.cursor.setPosition(blockEnd, 2);
            // 选中"内容"方便修改
            this.cursor.setSelection(blockEnd, 2, blockEnd, 4);
          });
          return;
        }
      }
      
      // 不在表格内，插入新表格
      const tableText = '| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |';
      const lineText = this.document.getLineText(pos.line);
      
      // 如果当前行有内容，先换行
      if (lineText.trim()) {
        this.insertText('\n' + tableText + '\n');
      } else {
        this.insertText(tableText + '\n');
      }
      
      // 定位到表头第一个单元格并选中
      this.$nextTick(() => {
        const newLine = lineText.trim() ? pos.line + 1 : pos.line;
        this.cursor.setSelection(newLine, 2, newLine, 4);
      });
    },

    // ==================== 历史操作 ====================

    undo() {
      this.focus();
      if (!this.canUndo) return;
      if (this.history && this.history.undo()) {
        this.updateHistoryState();
      }
    },

    redo() {
      this.focus();
      if (!this.canRedo) return;
      if (this.history && this.history.redo()) {
        this.updateHistoryState();
      }
    },

    // ==================== 全屏 ====================

    toggleFullscreen() {
      this.isFullscreen = !this.isFullscreen;

      if (this.isFullscreen) {
        if (!this.originalParent) {
          this.originalParent = this.$el.parentNode;
          this.originalNextSibling = this.$el.nextSibling;
        }
        document.body.appendChild(this.$el);
        document.body.style.overflow = 'hidden';
      } else {
        if (this.originalParent) {
          if (this.originalNextSibling && this.originalNextSibling.parentNode === this.originalParent) {
            this.originalParent.insertBefore(this.$el, this.originalNextSibling);
          } else {
            this.originalParent.appendChild(this.$el);
          }
        }
        document.body.style.overflow = '';
      }
    },
    
    /**
     * 触发图片上传
     */
    triggerImageUpload() {
      this.pendingForceAttachmentUpload = false;
      this.pendingPrivateAttachmentUpload = false;
      this.$refs.fileInput?.click();
    },

    /**
     * 触发文件上传
     */
    triggerFileUpload() {
      this.pendingForceAttachmentUpload = true;
      this.pendingPrivateAttachmentUpload = false;
      this.$refs.fileInput?.click();
    },

    /**
     * 触发私有附件上传
     */
    triggerPrivateFileUpload() {
      this.pendingForceAttachmentUpload = true;
      this.pendingPrivateAttachmentUpload = true;
      this.$refs.fileInput?.click();
    },
    
    /**
     * 处理文件选择
     */
    handleFileChange(e) {
      const file = e.target.files[0];
      const uploadOptions = {
        forceAttachment: this.pendingForceAttachmentUpload,
        privateAttachment: this.pendingPrivateAttachmentUpload
      };
      this.pendingForceAttachmentUpload = false;
      this.pendingPrivateAttachmentUpload = false;
      if (file) {
        this.queueOrStartImageUpload(file, uploadOptions);
      }
      e.target.value = '';
    },
    
    /**
     * 导出 Markdown
     */
    exportMarkdown() {
      const content = this.getValue();
      const blob = new Blob([content], { type: 'text/markdown' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'article.md';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    },

    /**
     * 复制到微信公众号/知乎
     */
    async copyToWeChat() {
      const content = this.getValue();
      if (!content) {
        this.$message?.warning('请先输入内容');
        return;
      }
      
      try {
        // 渲染为 HTML
        const html = await renderMarkdown(content);
        
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html;
        
        // 应用内联样式
        this.inlineStyles(wrapper);
        
        const resultHtml = wrapper.innerHTML;
        const markdownText = content || '';
        const plainText = wrapper.innerText || markdownText || '';
        const customMarkdownMime = 'text/x-poetize-markdown';
        
        // 使用 Clipboard API
        if (navigator.clipboard && navigator.clipboard.write && typeof ClipboardItem !== 'undefined') {
          try {
            const items = {};
            items['text/html'] = new Blob([resultHtml], { type: 'text/html' });
            items['text/plain'] = new Blob([plainText], { type: 'text/plain' });
            items[customMarkdownMime] = new Blob([markdownText], { type: customMarkdownMime });
            items['text/markdown'] = new Blob([markdownText], { type: 'text/markdown' });
            
            await navigator.clipboard.write([new ClipboardItem(items)]);
            this.$message?.success('已复制 (支持公众号/知乎及纯文本)');
            return;
          } catch (e) {
            console.warn('Clipboard API failed:', e);
          }
        }
        
        // 降级方案
        const tempDiv = document.createElement('div');
        tempDiv.style.cssText = 'position:fixed;left:-9999px;top:0';
        tempDiv.innerHTML = resultHtml;
        tempDiv.setAttribute('contenteditable', 'true');
        document.body.appendChild(tempDiv);
        
        const handleCopy = (e) => {
          if (!e.clipboardData) return;
          e.preventDefault();
          e.clipboardData.setData('text/html', resultHtml);
          e.clipboardData.setData('text/plain', plainText);
          e.clipboardData.setData(customMarkdownMime, markdownText);
          e.clipboardData.setData('text/markdown', markdownText);
        };
        tempDiv.addEventListener('copy', handleCopy);

        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(tempDiv);
        selection.removeAllRanges();
        selection.addRange(range);
        
        const success = document.execCommand('copy');
        tempDiv.removeEventListener('copy', handleCopy);
        document.body.removeChild(tempDiv);
        selection.removeAllRanges();
        
        if (success) {
          this.$message?.success('已复制 (兼容模式)');
        } else {
          throw new Error('复制失败');
        }
      } catch (err) {
        console.error('Copy failed:', err);
        this.$message?.error('复制失败: ' + (err.message || '未知错误'));
      }
    },

    /**
     * 应用内联样式（用于公众号复制）
     */
    inlineStyles(root) {
      const styles = {
        'h1': 'font-size: 22px; font-weight: bold; margin: 20px 0 10px; color: #333;',
        'h2': 'font-size: 18px; font-weight: bold; margin: 18px 0 10px; color: #333; border-bottom: 1px solid #eaecef; padding-bottom: 10px;',
        'h3': 'font-size: 16px; font-weight: bold; margin: 16px 0 10px; color: #333;',
        'p': 'font-size: 15px; line-height: 1.75; margin: 10px 0; color: #333; text-align: justify;',
        'img': 'max-width: 100%; height: auto; display: block; margin: 10px auto; border-radius: 4px;',
        'blockquote': 'border-left: 4px solid #42b983; padding: 10px 15px; color: #666; background-color: #f8f9fa; margin: 10px 0;',
        'pre': 'background-color: #282c34; padding: 15px; border-radius: 5px; overflow-x: auto; color: #abb2bf;',
        'code': 'font-family: Consolas, Monaco, monospace; background-color: rgba(27,31,35,.05); padding: 0.2em 0.4em; border-radius: 3px;',
        'ul': 'margin: 10px 0; padding-left: 20px;',
        'ol': 'margin: 10px 0; padding-left: 20px;',
        'li': 'margin: 5px 0; font-size: 15px; color: #333;',
        'a': 'color: #409eff; text-decoration: none; border-bottom: 1px solid #409eff;',
        'table': 'border-collapse: collapse; width: 100%; margin: 15px 0; font-size: 14px;',
        'th': 'border: 1px solid #dfe2e5; padding: 8px 12px; background-color: #f2f2f2; font-weight: bold;',
        'td': 'border: 1px solid #dfe2e5; padding: 8px 12px;'
      };
      
      for (const tag in styles) {
        root.querySelectorAll(tag).forEach(el => {
          el.setAttribute('style', styles[tag] + (el.getAttribute('style') || ''));
        });
      }
    },

    // ==================== 公共 API ====================

    /**
     * 获取内容（降级标题后返回，用于保存到数据库）
     */
    getValue() {
      const content = this.document ? this.document.getContent() : '';
      return downgradeMarkdownHeadings(content);
    },

    /**
     * 设置内容（从数据库读取，升级标题后显示）
     */
    setValue(value) {
      if (this.document) {
        const displayValue = upgradeMarkdownHeadings(value || '');
        this.document.setContent(displayValue);
        this.cursor.setPosition(0, 0);
        this.renderAllSegments();
      }
    },

    /**
     * 在光标位置插入内容
     */
    insertValue(text) {
      this.insertText(text);
    },
    resolveImageUpload(uploadId, markdown) {
      return this.replaceUploadToken(uploadId, markdown);
    },
    rejectImageUpload(uploadId) {
      return this.replaceUploadToken(uploadId, '');
    }
  }
};
</script>

<style scoped>
.ir-editor {
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  overflow: visible;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.ir-editor.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh !important;
  z-index: 2000;
  border: none;
  border-radius: 0;
}

.ir-editor.dark-mode {
  background: #1e1e1e;
  border-color: #3a3a3a;
}

/* 工具栏 */
.editor-toolbar {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  flex-wrap: wrap;
  gap: 4px;
  position: sticky;
  top: var(--editor-toolbar-top, -25px);
  z-index: 10;
}

.ir-editor.dark-mode .editor-toolbar {
  background-color: #313337;
  border-bottom-color: #4c4d4f;
}

.toolbar-group {
  display: flex;
  gap: 2px;
}

.toolbar-item {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 4px;
  color: #606266;
  font-size: 16px;
  transition: all 0.2s;
}

.toolbar-item:hover {
  background-color: #e4e7ed;
  color: #409eff;
}

.toolbar-item.active {
  background-color: #ecf5ff;
  color: #409eff;
}

.toolbar-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar-item.disabled:hover {
  background-color: transparent;
  color: #606266;
}

.ir-editor.dark-mode .toolbar-item {
  color: #a9b7c6;
}

.ir-editor.dark-mode .toolbar-item:hover {
  background-color: #3e4145;
  color: #fff;
}

.ir-editor.dark-mode .toolbar-item.active {
  background-color: #214283;
  color: #fff;
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  background-color: #dcdfe6;
  margin: 0 6px;
}

.ir-editor.dark-mode .toolbar-divider {
  background-color: #4c4d4f;
}

.toolbar-spacer {
  flex: 1;
}

/* 编辑器主体 */
.editor-body {
  flex: 1;
  position: relative;
  overflow: hidden;
}

/* 隐藏的输入框 */
.hidden-input {
  position: absolute;
  left: -9999px;
  top: 0;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

/* 可视编辑区域 */
.editor-content {
  display: flex;
  height: 100%;
  overflow: auto;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  cursor: text;
  /* 阻止浏览器默认的文本选择，由编辑器自己管理 */
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  user-select: none;
  /* 移动端：阻止触摸时的高亮效果 */
  -webkit-tap-highlight-color: transparent;
  /* 移动端：阻止触摸时的调出菜单 */
  -webkit-touch-callout: none;
  /* 移动端：允许纵向滚动，横向由编辑器自行管理 */
  touch-action: pan-y;
  -webkit-overflow-scrolling: touch;
}

/* 内容区域 */
.lines-container {
  flex: 1;
  width: 100%;
  /* 左右留白加大，避免内容贴边 */
  padding: 14px 24px;
  min-height: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

/* 编辑器行 */
.editor-line {
  display: block;
  line-height: 1.6;
  min-height: 1.6em;
  font-size: 16px;
  position: relative;
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

/* 多行块容器：用于渲染 markdown-it 输出（代码块/表格/列表/引用等） */
.editor-block {
  display: flex;
  align-items: flex-start;
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.block-content {
  flex: 1;
  min-width: 0;
  max-width: 100%;
}

.editor-line.cursor-line {
  background: rgba(64, 158, 255, 0.05);
}

.ir-editor.dark-mode .editor-line.cursor-line {
  background: rgba(64, 158, 255, 0.1);
}

.editor-line.has-selection,
.editor-block.has-selection {
  border-radius: 6px;
}

.editor-line.has-selection .line-content.rendered-mode,
.editor-line.has-selection .line-content.rendered-mode *,
.editor-block.has-selection .block-content,
.editor-block.has-selection .block-content * {
  background: rgba(64, 158, 255, 0.22);
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}

.editor-line.has-selection .line-content.rendered-mode a,
.editor-block.has-selection .block-content a {
  text-decoration-thickness: 2px;
}

.ir-editor.dark-mode .editor-line.has-selection .line-content.rendered-mode,
.ir-editor.dark-mode .editor-line.has-selection .line-content.rendered-mode *,
.ir-editor.dark-mode .editor-block.has-selection .block-content,
.ir-editor.dark-mode .editor-block.has-selection .block-content * {
  background: rgba(64, 158, 255, 0.3);
}

/* 行内容容器 */
.line-content {
  display: inline;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-wrap: anywhere;
  word-break: break-word;
  min-width: 0;
  max-width: 100%;
}

/* 让 entry-content（文章页样式体系）在行内正常工作 */
.line-content.entry-content {
  display: flow-root;
  width: 100%;
}

.block-content.entry-content {
  display: flow-root;
  width: 100%;
}

/* 修正 markdown-it 生成的 <p> 默认外边距（否则会出现上下各 16px 的大间距） */
.ir-editor :deep(.entry-content p) {
  margin: 0 !important;
}

.ir-editor :deep(.entry-content),
.ir-editor :deep(.entry-content > *),
.ir-editor :deep(.entry-content p),
.ir-editor :deep(.entry-content li),
.ir-editor :deep(.entry-content blockquote),
.ir-editor :deep(.entry-content a),
.ir-editor :deep(.entry-content td),
.ir-editor :deep(.entry-content th) {
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
  box-sizing: border-box;
}

.ir-editor :deep(.mermaid-container) {
    margin: 20px 0;
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
    overflow-x: auto;
    text-align: center;
    transition: all 0.3s ease;
  }
  
.ir-editor :deep(.mermaid-container svg) {
    max-width: 100%;
    height: auto;
    overflow: visible !important;
  }
  
.ir-editor :deep(.mermaid-container svg foreignObject div),
.ir-editor :deep(.mermaid-container svg foreignObject span),
.ir-editor :deep(.mermaid-container svg foreignObject p) {
    word-break: normal !important;
    overflow-wrap: normal !important;
    white-space: pre !important;
    line-height: 1.2 !important;
    font-size: 14px !important;
  }

.ir-editor :deep(.mermaid-container svg foreignObject div),
.ir-editor :deep(.mermaid-container svg foreignObject span),
.ir-editor :deep(.mermaid-container svg foreignObject p) {
    margin: 0 !important;
    padding: 0 !important;
    text-align: center !important;
  }
  
.ir-editor :deep(.mermaid-container svg foreignObject) {
    overflow: visible !important;
  }
  
.ir-editor :deep(.mermaid-container svg .nodeLabel),
.ir-editor :deep(.mermaid-container svg .edgeLabel),
.ir-editor :deep(.mermaid-container svg .labelBkg) {
    overflow: visible !important;
  }
  
.ir-editor.dark-mode :deep(.mermaid-container) {
    background: #2d2d2d;
  }

.ir-editor :deep(.entry-content ul),
.ir-editor :deep(.entry-content ol) {
  margin: 0 !important;
}

/* 源码模式 */
.source-mode {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

/* 渲染模式样式 */
.rendered-mode {
  font-family: inherit;
}

/* 字符 */
.char {
  position: relative;
  display: inline-block;
  min-height: 1em;
}

.char.selected {
  background: rgba(64, 158, 255, 0.3);
}

/* 光标 */
.char.cursor-before::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 2px;
  height: 100%;
  background: #409eff;
  animation: cursor-blink 1s infinite;
}

.cursor-end::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
  background: #409eff;
  vertical-align: text-bottom;
  animation: cursor-blink 1s infinite;
}

@keyframes cursor-blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* 输入法组合文本 */
.composition-text {
  background: #fff3cd;
  border-bottom: 2px solid #ffc107;
  padding: 0 2px;
}

.ir-editor.dark-mode .composition-text {
  background: #4a3f00;
}

/* 编辑器底部填充 */
.editor-padding {
  height: 50%;
  min-height: 100px;
}

/* 标题样式已移至公共文件：@/assets/css/editor-heading-styles.css */
/* 通过 import 自动应用 .entry-content 的标题样式 */
/* 注意：这里不应该引入 article-style-protection.css，因为那会强制覆盖编辑器的标题图标 */
/* 编辑器应该使用自己的图标体系（editor-heading-styles.css） */

/* 暗色模式文本 */
.ir-editor.dark-mode {
  color: #d4d4d4;
}

.ir-editor.dark-mode .editor-line {
  color: #d4d4d4;
}

/* ========== 暗色模式下渲染内容的样式适配 ========== */

/* 段落和通用文本 */
.ir-editor.dark-mode :deep(.entry-content),
.ir-editor.dark-mode :deep(.entry-content p),
.ir-editor.dark-mode :deep(.entry-content li) {
  color: #d4d4d4;
}

/* 引用块 */
.ir-editor.dark-mode :deep(.entry-content blockquote) {
  color: #b0b0b0;
}

/* 行内代码 */
.ir-editor.dark-mode :deep(.entry-content code:not(.hljs)) {
  background: #3a3a00;
  color: #f0c674;
}

/* 链接 */
.ir-editor.dark-mode :deep(.entry-content a) {
  color: #f0a0a0;
}

.ir-editor.dark-mode :deep(.entry-content a:hover) {
  color: #ffa500;
}

/* 表格 - 暗色模式适配 */
.ir-editor.dark-mode :deep(.entry-content table) {
  color: #d4d4d4;
}

.ir-editor.dark-mode :deep(.entry-content table th) {
  background-color: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.ir-editor.dark-mode :deep(.entry-content table td) {
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.ir-editor.dark-mode :deep(.entry-content table tr:nth-child(2n)) {
  background-color: rgba(255, 255, 255, 0.04);
}

.ir-editor.dark-mode :deep(.entry-content table tbody > tr:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

/* table-wrapper 包裹的表格 */
.ir-editor.dark-mode :deep(.table-wrapper > table) {
  color: #d4d4d4;
}

.ir-editor.dark-mode :deep(.table-wrapper > table th) {
  background-color: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.ir-editor.dark-mode :deep(.table-wrapper > table td) {
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.ir-editor.dark-mode :deep(.table-wrapper > table tr:nth-child(2n)) {
  background-color: rgba(255, 255, 255, 0.04);
}

.ir-editor.dark-mode :deep(.table-wrapper > table tbody > tr:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 列表标记颜色 */
.ir-editor.dark-mode :deep(.entry-content ul),
.ir-editor.dark-mode :deep(.entry-content ol) {
  color: #d4d4d4;
}

/* 水平线 */
.ir-editor.dark-mode :deep(.entry-content hr) {
  border-color: rgba(252, 98, 93, 0.5);
}

/* 帮助内容样式 */
.help-content {
  padding: 0 10px;
}

.help-content h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 15px 0 10px;
  color: #303133;
}

.help-content h3:first-child {
  margin-top: 0;
}

.help-content p {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
}

.help-list {
  padding-left: 20px;
  margin: 0;
}

.help-list li {
  font-size: 14px;
  color: #606266;
  line-height: 2;
  list-style-type: disc;
}

.help-list code {
  background-color: #f2f6fc;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  color: #409eff;
  margin: 0 4px;
}

/* 编辑器对比表格样式 */
.editor-compare-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-top: 8px;
}

.editor-compare-table th,
.editor-compare-table td {
  border: 1px solid #e4e7ed;
  padding: 8px 10px;
  text-align: left;
}

.editor-compare-table th {
  background-color: #f5f7fa;
  color: #606266;
  font-weight: 600;
}

.editor-compare-table td {
  color: #606266;
}

.editor-compare-table tr.current-editor {
  background-color: #ecf5ff;
}

.editor-compare-table tr.current-editor td {
  color: #409eff;
}
</style>

<style>
/* 帮助弹窗内容样式（适配 Dark Mode） */
body.dark-mode .editor-help-dialog .help-content h3 {
  color: #d4d4d4;
}

body.dark-mode .editor-help-dialog .help-content p,
body.dark-mode .editor-help-dialog .help-content li {
  color: #a9b7c6;
}

body.dark-mode .editor-help-dialog .help-list code {
  background-color: #3e4145;
  color: #67c23a;
}

/* 暗色模式下的编辑器对比表格 */
body.dark-mode .editor-help-dialog .editor-compare-table th,
body.dark-mode .editor-help-dialog .editor-compare-table td {
  border-color: #4c5157;
}

body.dark-mode .editor-help-dialog .editor-compare-table th {
  background-color: #3e4145;
  color: #a9b7c6;
}

body.dark-mode .editor-help-dialog .editor-compare-table td {
  color: #a9b7c6;
}

body.dark-mode .editor-help-dialog .editor-compare-table tr.current-editor {
  background-color: rgba(64, 158, 255, 0.15);
}

body.dark-mode .editor-help-dialog .editor-compare-table tr.current-editor td {
  color: #67c23a;
}
</style>
