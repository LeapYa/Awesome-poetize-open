<template>
  <div 
    class="wysiwyg-editor" 
    :class="{ 'fullscreen': isFullscreen, 'dark-mode': isDarkMode }"
    :style="{ height: containerHeight }"
  >
    <!-- 工具栏 -->
    <div class="editor-toolbar" v-if="showToolbar" @mousedown.prevent>
      <div class="toolbar-group">
        <el-tooltip content="加粗 (Ctrl+B)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': formatState.bold }" @click="execFormat('bold')">
            <i class="fa fa-bold"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="斜体 (Ctrl+I)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': formatState.italic }" @click="execFormat('italic')">
            <i class="el-icon-edit"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="删除线 (Ctrl+D)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': formatState.strikethrough }" @click="execFormat('strikethrough')">
            <i class="el-icon-minus"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="行内代码" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': formatState.inlineCode }" @click="insertInlineCode">
            <i class="fa fa-code"></i>
          </div>
        </el-tooltip>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="toolbar-group">
        <el-dropdown trigger="click" @command="insertHeading">
          <el-tooltip content="标题" placement="top" :enterable="false">
            <div class="toolbar-item"><i class="el-icon-s-flag"></i></div>
          </el-tooltip>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="1">标题 1</el-dropdown-item>
            <el-dropdown-item command="2">标题 2</el-dropdown-item>
            <el-dropdown-item command="3">标题 3</el-dropdown-item>
            <el-dropdown-item command="4">标题 4</el-dropdown-item>
            <el-dropdown-item command="5">标题 5</el-dropdown-item>
            <el-dropdown-item command="6">标题 6</el-dropdown-item>
            <el-dropdown-item command="p" divided>正文</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
        <el-tooltip content="引用 (Ctrl+Q)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertBlockquote">
            <i class="el-icon-chat-line-square"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="无序列表 (Ctrl+U)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="execFormat('insertUnorderedList')">
            <i class="el-icon-tickets"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="有序列表 (Ctrl+O)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="execFormat('insertOrderedList')">
            <i class="el-icon-s-order"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="待办列表" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertTodoList">
            <i class="el-icon-circle-check"></i>
          </div>
        </el-tooltip>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="toolbar-group">
        <el-tooltip content="链接 (Ctrl+K)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertLink">
            <i class="el-icon-link"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="图片 (粘贴)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerImageUpload">
            <i class="el-icon-picture-outline"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="文件 (粘贴)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerFileUpload">
            <i class="el-icon-folder-add"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="私有附件" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerPrivateFileUpload">
            <i class="el-icon-lock"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="代码块" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertCodeBlock">
            <i class="fa fa-file-code-o"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="表格" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertTable">
            <i class="el-icon-s-grid"></i>
          </div>
        </el-tooltip>
        <el-tooltip content="分割线" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertHorizontalRule">
            <i class="el-icon-minus"></i>
          </div>
        </el-tooltip>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <el-tooltip content="在行前插入空行" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertLineBefore"><i class="el-icon-top"></i></div>
        </el-tooltip>
        <el-tooltip content="在行后插入空行 (Ctrl+Enter)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertLineAfter"><i class="el-icon-bottom"></i></div>
        </el-tooltip>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-group">
        <el-tooltip content="撤销 (Ctrl+Z)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'disabled': !formatState.undo }" @click="undo"><i class="el-icon-refresh-left"></i></div>
        </el-tooltip>
        <el-tooltip content="重做 (Ctrl+Y)" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'disabled': !formatState.redo }" @click="redo"><i class="el-icon-refresh-right"></i></div>
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
        <el-tooltip content="查看源码" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': showSource }" @click="toggleSource">
            <i class="el-icon-view"></i>
          </div>
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
        <el-tooltip ref="fullscreenTooltip" :content="isFullscreen ? '退出全屏 (Esc)' : '全屏 (F11)'" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': isFullscreen }" @click="toggleFullscreen">
            <i class="el-icon-full-screen"></i>
          </div>
        </el-tooltip>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="editor-body">
      <!-- WYSIWYG 编辑区域 -->
      <div 
        v-show="!showSource"
        ref="editorContent"
        class="editor-content entry-content"
        contenteditable="true"
        :placeholder="placeholder"
        @input="handleInput"
        @keydown="handleKeydown"
        @paste="handlePaste"
        @copy="handleCopy"
        @cut="handleCut"
        @click="handleEditorClick"
        @focus="handleFocus"
        @blur="handleBlur"
        @compositionstart="handleCompositionStart"
        @compositionend="handleCompositionEnd"
        @mouseup="updateFormatState"
        @keyup="updateFormatState"
      ></div>
      
      <!-- 源码编辑区域 -->
      <textarea
        v-show="showSource"
        ref="sourceEditor"
        class="source-editor"
        v-model="markdownContent"
        @input="handleSourceInput"
        @focus="closeFullscreenTooltip"
        @compositionstart="handleCompositionStart"
        @compositionend="handleCompositionEnd"
        @blur="syncFromSource"
      ></textarea>
    </div>
    
    <!-- 隐藏的文件上传输入框 -->
    <input 
      type="file" 
      ref="fileInput" 
      style="display: none" 
      @change="handleFileChange"
    >
    
    <!-- 链接输入对话框 -->
    <el-dialog
      title="插入链接"
      :visible.sync="linkDialogVisible"
      width="400px"
      append-to-body
    >
      <el-form :model="linkForm" label-width="80px">
        <el-form-item label="链接文本">
          <el-input v-model="linkForm.text" placeholder="显示的文本"></el-input>
        </el-form-item>
        <el-form-item label="链接地址">
          <el-input v-model="linkForm.url" placeholder="https://example.com"></el-input>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="linkDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmInsertLink">确定</el-button>
      </span>
    </el-dialog>

    <!-- 帮助对话框 -->
    <el-dialog
      title="编辑器使用说明"
      :visible.sync="helpDialogVisible"
      width="620px"
      append-to-body
      custom-class="centered-dialog editor-help-dialog"
    >
      <div class="help-content">
        <h3>✨ WYSIWYG 编辑器特性</h3>
        <ul class="help-list">
          <li><strong>所见即所得</strong>：直接操作富文本，实时显示格式效果。</li>
          <li><strong>富文本编辑</strong>：类似 Word 的编辑体验，无需了解 Markdown。</li>
          <li><strong>表格操作</strong>：支持表格右键菜单，添加/删除行列更便捷。</li>
        </ul>
        <h3>⌨️ 常用快捷键</h3>
        <ul class="help-list">
          <li><strong>加粗</strong>：<code>Ctrl + B</code></li>
          <li><strong>斜体</strong>：<code>Ctrl + I</code></li>
          <li><strong>删除线</strong>：<code>Ctrl + D</code></li>
          <li><strong>引用</strong>：<code>Ctrl + Q</code></li>
          <li><strong>有序列表</strong>：<code>Ctrl + O</code></li>
          <li><strong>无序列表</strong>：<code>Ctrl + U</code></li>
          <li><strong>插入链接</strong>：<code>Ctrl + K</code></li>
          <li><strong>强制换行</strong>：<code>Ctrl + Enter</code> 或 <code>Shift + Enter</code></li>
          <li><strong>保存</strong>：<code>Ctrl + S</code></li>
          <li><strong>撤销 / 重做</strong>：<code>Ctrl + Z</code> / <code>Ctrl + Y</code></li>
        </ul>

        <h3>📊 表格操作</h3>
        <p>在表格区域内点击 <strong>鼠标右键</strong> 可打开菜单，支持：</p>
        <ul class="help-list">
          <li>向上/下插入行</li>
          <li>向左/右插入列</li>
          <li>删除当前行/列或整个表格</li>
        </ul>

        <h3>📊 图表支持</h3>
        <ul class="help-list">
          <li><strong>Mermaid 图表</strong>：使用 <code>```mermaid</code> 代码块渲染流程图、时序图等。</li>
          <li><strong>ECharts 图表</strong>：使用 <code>```echarts</code> 代码块渲染 ECharts 图表。</li>
        </ul>

        <h3>📝 其他功能</h3>
        <ul class="help-list">
          <li><strong>代码块</strong>：点击左上角语言名称可修改语言类型。</li>
          <li><strong>全屏模式</strong>：按 <code>F11</code> 或点击工具栏图标切换。</li>
          <li><strong>Markdown 兼容</strong>：支持直接粘贴 Markdown 文本。</li>
          <li><strong>图片上传</strong>：支持截图直接粘贴上传。</li>
          <li><strong>文件上传</strong>：支持粘贴上传文件。</li>
          <li><strong>登录附件</strong>：私有附件使用前端登录拦截；知道真实静态地址仍可直接访问，适合轻量提示场景。</li>
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
            <tr>
              <td><strong>IR 编辑器</strong></td>
              <td>即时渲染</td>
              <td>源码与渲染自动切换，轻量流畅</td>
            </tr>
            <tr class="current-editor">
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

    <!-- 表格右键菜单 -->
    <div 
      v-show="contextMenu.visible"
      class="editor-context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @mousedown.prevent
    >
      <div class="menu-item" @click="execTableCommand('insertRowBefore')">
        <i class="el-icon-top"></i> 向上插入行
      </div>
      <div class="menu-item" @click="execTableCommand('insertRowAfter')">
        <i class="el-icon-bottom"></i> 向下插入行
      </div>
      <div class="menu-divider"></div>
      <div class="menu-item" @click="execTableCommand('insertColLeft')">
        <i class="el-icon-back"></i> 向左插入列
      </div>
      <div class="menu-item" @click="execTableCommand('insertColRight')">
        <i class="el-icon-right"></i> 向右插入列
      </div>
      <div class="menu-divider"></div>
      <div class="menu-item" @click="execTableCommand('deleteRow')">
        <i class="el-icon-minus"></i> 删除当前行
      </div>
      <div class="menu-item" @click="execTableCommand('deleteCol')">
        <i class="el-icon-minus"></i> 删除当前列
      </div>
      <div class="menu-divider"></div>
      <div class="menu-item delete" @click="execTableCommand('deleteTable')">
        <i class="el-icon-delete"></i> 删除表格
      </div>
    </div>
  </div>
</template>

<script>
import { renderMarkdown } from '@/utils/markdownLazyRenderer';
import { htmlToMarkdown } from '@/utils/htmlToMarkdown';
import { downgradeMarkdownHeadings, upgradeMarkdownHeadings } from '@/utils/markdownHeadingUtils';
import { loadMermaidResources, normalizeMermaidDiagrams } from '@/utils/resourceLoaders/mermaidLoader';
import { loadEChartsResources } from '@/utils/resourceLoaders/echartsLoader';
import { parseEChartsOption } from '@/utils/echartsOptionParser';
import { handlePaste as handlePasteUtil } from '@/utils/pasteHandler';
import SnapshotHistory from '@/components/editors/core/SnapshotHistory';
// 导入公共样式（与文章页、IR模式保持一致）
import '@/assets/css/markdown-highlight.css';
import '@/assets/css/editor-heading-styles.css';

export default {
  name: 'WYSIWYGMarkdownEditor',
  
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
      // Markdown 内容（内部存储）
      markdownContent: '',
      
      // UI 状态
      isFullscreen: false,
      isDarkMode: false,
      isFocused: false,
      showSource: false,
      
      // 格式状态（用于工具栏按钮高亮）
      formatState: {
        bold: false,
        italic: false,
        strikethrough: false,
        underline: false,
        undo: false,
        redo: false,
        inlineCode: false
      },
      
      // 链接对话框
      linkDialogVisible: false,
      linkForm: {
        text: '',
        url: ''
      },
      
      // 帮助对话框
      helpDialogVisible: false,
      
      savedSelection: null,
      
      // 防抖定时器
      syncTimer: null,

      // 代码块高亮防抖（WYSIWYG 内编辑代码块）
      codeHighlightTimer: null,
      hljsInstance: null,
      hljsLoading: null,
      
      // 主题观察器
      themeObserver: null,
      
      // 原始父节点（用于全屏）
      originalParent: null,
      originalNextSibling: null,
      
      // 标记是否是内部更新
      isInternalUpdate: false,
      
      // 渲染任务 ID
      renderJobId: 0,

      // 快照历史
      history: null,
      historySuspend: false,
      historyNextMode: null,
      queuedImageUploads: [],
      pendingForceAttachmentUpload: false,
      pendingPrivateAttachmentUpload: false,
      isComposing: false,
      isBootstrapped: false,
      bootstrapHandle: null,
      
      // 表格右键菜单状态
      contextMenu: {
        visible: false,
        x: 0,
        y: 0,
        targetCell: null // 当前右键点击的单元格
      }
    };
  },

  computed: {
    containerHeight() {
      if (this.isFullscreen) return '100vh';
      return typeof this.height === 'number' ? `${this.height}px` : this.height;
    }
  },

  watch: {
    value: {
      handler(newVal) {
        if (this.isInternalUpdate) {
          this.isInternalUpdate = false;
          return;
        }
        
        // 从外部获取值时，升级标题后显示
        const displayValue = upgradeMarkdownHeadings(newVal || '');
        
        if (this.markdownContent !== displayValue) {
          this.markdownContent = displayValue;
          if (!this.isBootstrapped) {
            return;
          }
          this.historySuspend = true;
          this.renderContent();
          this.$nextTick(() => {
            this.resetHistory();
            this.historySuspend = false;
          });
        }
      },
      immediate: true
    },
    markdownContent(newVal, oldVal) {
      if (newVal === oldVal || this.historySuspend || !this.history) {
        return;
      }

      this.recordHistorySnapshot({
        merge: this.historyNextMode !== 'push'
      });
      this.historyNextMode = null;
    }
  },

  mounted() {
    this.history = new SnapshotHistory({
      limit: 2000,
      mergeWindow: 800,
      isEqual: (a, b) => !!a && !!b && a.markdownContent === b.markdownContent && a.showSource === b.showSource
    });
    this.setupThemeObserver();
    this._onWindowResize = () => {
      this.resizeAllECharts();
    };
    window.addEventListener('resize', this._onWindowResize);
    document.addEventListener('keydown', this.handleDocumentKeydown, true);
    document.addEventListener('click', this.closeContextMenu);
    
    // 监听编辑器区域的右键事件
    const editor = this.$refs.editorContent;
    if (editor) {
      editor.addEventListener('contextmenu', this.handleContextMenu);
    }
    
    this.updateFormatState();
    this.scheduleBootstrap();
  },

  beforeDestroy() {
    if (this.bootstrapHandle) {
      cancelAnimationFrame(this.bootstrapHandle);
      this.bootstrapHandle = null;
    }
    this.cleanupThemeObserver();
    if (this._onWindowResize) {
      window.removeEventListener('resize', this._onWindowResize);
      this._onWindowResize = null;
    }
    document.removeEventListener('keydown', this.handleDocumentKeydown, true);
    document.removeEventListener('click', this.closeContextMenu);
    
    const editor = this.$refs.editorContent;
    if (editor) {
      editor.removeEventListener('contextmenu', this.handleContextMenu);
    }
    
    if (this.syncTimer) {
      clearTimeout(this.syncTimer);
    }
    if (this.history) {
      this.history.clear();
      this.history = null;
    }
  },

  methods: {
    scheduleBootstrap() {
      const finalizeBootstrap = () => {
        this.bootstrapHandle = null;
        this.initEditor();
        this.isBootstrapped = true;
        this.$nextTick(() => {
          this.resetHistory();
          this.$emit('ready', this);
        });
      };

      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        this.bootstrapHandle = window.requestAnimationFrame(finalizeBootstrap);
        return;
      }

      finalizeBootstrap();
    },
    resizeAllECharts() {
      if (!window.echarts) return;
      const editor = this.$refs.editorContent;
      if (!editor) return;
      const blocks = editor.querySelectorAll('.echarts-render');
      blocks.forEach(el => {
        const chart = window.echarts.getInstanceByDom(el);
        if (chart) {
          try {
            chart.resize();
          } catch (e) {}
        }
      });
    },

    async renderDiagramsInEditor() {
      const editor = this.$refs.editorContent;
      if (!editor || this.showSource) return;
      const content = this.markdownContent || '';
      if (!content.includes('```mermaid') && !content.includes('```echarts')) return;

      await this.renderMermaidInElement(editor, content);
      await this.renderEChartsInElement(editor, content);
    },

    async renderMermaidInElement(rootEl, content) {
      if (!content.includes('```mermaid')) return;
      const ok = await loadMermaidResources();
      if (!ok || !window.mermaid) return;

      const blocks = rootEl.querySelectorAll('div.mermaid');
      if (!blocks.length) return;

      for (let i = 0; i < blocks.length; i++) {
        const el = blocks[i];
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
        el.setAttribute('contenteditable', 'false');
        el.innerHTML = '';

          try {
            const id = `mermaid-${Date.now()}-${i}`;
            const { svg } = await window.mermaid.render(id, code);
            const container = document.createElement('div');
            container.className = 'mermaid-container';
            container.innerHTML = svg;
            normalizeMermaidDiagrams(container);
            el.appendChild(container);
            el.setAttribute('data-rendered', newEncoded);
          } catch (e) {
          el.textContent = code;
          el.removeAttribute('data-rendered');
        }
      }
    },

    async renderEChartsInElement(rootEl, content) {
      if (!content.includes('```echarts')) return;
      const ok = await loadEChartsResources();
      if (!ok || !window.echarts) return;

      const blocks = rootEl.querySelectorAll('div.echarts-render');
      if (!blocks.length) return;

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
        el.setAttribute('data-source', newEncoded);
        el.setAttribute('contenteditable', 'false');

        if (el.getAttribute('data-rendered') === newEncoded && window.echarts.getInstanceByDom(el)) {
          continue;
        }

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
    },

    getNodePath(root, node) {
      if (!root || !node) return null;
      const path = [];
      let current = node;
      while (current && current !== root) {
        const parent = current.parentNode;
        if (!parent) return null;
        const index = Array.prototype.indexOf.call(parent.childNodes, current);
        if (index < 0) return null;
        path.unshift(index);
        current = parent;
      }
      if (current !== root) return null;
      return path;
    },

    resolveNodeFromPath(root, path) {
      if (!root || !Array.isArray(path)) return null;
      let node = root;
      for (const idx of path) {
        if (!node || !node.childNodes || idx < 0 || idx >= node.childNodes.length) return null;
        node = node.childNodes[idx];
      }
      return node;
    },

    clampOffset(node, offset) {
      if (!node) return 0;
      const n = Number.isFinite(offset) ? offset : 0;
      if (node.nodeType === Node.TEXT_NODE) {
        return Math.max(0, Math.min(n, (node.textContent || '').length));
      }
      return Math.max(0, Math.min(n, node.childNodes ? node.childNodes.length : 0));
    },

    /**
     * 初始化编辑器
     */
    initEditor() {
      // 设置初始内容
      const displayValue = upgradeMarkdownHeadings(this.value || '');
      this.markdownContent = displayValue;
      this.renderContent();
    },
    
    /**
     * 渲染 Markdown 到编辑区域
     */
    async renderContent() {
      const jobId = ++this.renderJobId;
      
      try {
        const html = await renderMarkdown(this.renderImageUploadPlaceholders(this.markdownContent || ''));
        
        // 确保是最新的渲染任务
        if (jobId !== this.renderJobId) return;
        
        const editor = this.$refs.editorContent;
        if (editor) {
          // 保存光标位置
          const selection = this.saveSelection();
          
          editor.innerHTML = html || '<p><br></p>';
          
          // 绑定待办列表 checkbox 事件
          this.bindTodoCheckboxes(editor);
          
          // 恢复光标位置
          if (selection && this.isFocused) {
            this.$nextTick(async () => {
              this.restoreSelection(selection);
              await this.renderDiagramsInEditor();
            });
          } else {
            this.$nextTick(async () => {
              await this.renderDiagramsInEditor();
            });
          }
        }
      } catch (err) {
        console.error('Render error:', err);
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
    createImageUploadId() {
      return `img-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    },
    createImageUploadToken(uploadId) {
      return `[[poetize-image-upload:${uploadId}]]`;
    },
    createImageUploadPlaceholderElement(uploadId) {
      const span = document.createElement('span');
      span.className = 'image-upload-placeholder';
      span.setAttribute('data-upload-id', uploadId);
      span.setAttribute('contenteditable', 'false');
      span.textContent = '[文件上传中]';
      return span;
    },
    renderImageUploadPlaceholders(markdown) {
      return String(markdown || '').replace(/\[\[poetize-image-upload:([^\]]+)\]\]/g, (_match, uploadId) => {
        const placeholder = this.createImageUploadPlaceholderElement(uploadId);
        return placeholder.outerHTML;
      });
    },
    replaceUploadPlaceholdersWithTokens(root) {
      if (!root) return root;
      root.querySelectorAll('.image-upload-placeholder[data-upload-id]').forEach((node) => {
        const uploadId = node.getAttribute('data-upload-id');
        if (!uploadId || !node.parentNode) return;
        node.parentNode.replaceChild(
          document.createTextNode(this.createImageUploadToken(uploadId)),
          node
        );
      });
      return root;
    },
    escapeRegExp(value) {
      return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    },
    queueOrStartImageUpload(file, options = {}) {
      if (!file) return null;
      if (this.isComposing) {
        this.queuedImageUploads.push({ file, options });
        return null;
      }

      const uploadId = this.createImageUploadId();
      if (this.showSource) {
        this.insertSourceUploadPlaceholder(uploadId);
      } else {
        this.insertWysiwygUploadPlaceholder(uploadId);
      }
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
    insertSourceUploadPlaceholder(uploadId) {
      const textarea = this.$refs.sourceEditor;
      if (!textarea) return false;
      const token = this.createImageUploadToken(uploadId);
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const value = textarea.value || '';
      this.historyNextMode = 'push';
      this.markdownContent = value.slice(0, start) + token + value.slice(end);
      this.emitChange();
      this.$nextTick(() => {
        textarea.focus();
        const offset = start + token.length;
        textarea.setSelectionRange(offset, offset);
      });
      return true;
    },
    insertWysiwygUploadPlaceholder(uploadId) {
      const editor = this.$refs.editorContent;
      if (!editor) return false;

      this.focus();
      this.updateSavedSelection();
      const selectionSnapshot = this.saveSelection() || this.savedSelection;
      if (selectionSnapshot) {
        this.restoreSelection(selectionSnapshot);
      }

      const selection = window.getSelection();
      if (!selection || !selection.rangeCount) return false;
      const range = selection.getRangeAt(0);
      const placeholder = this.createImageUploadPlaceholderElement(uploadId);
      range.deleteContents();
      range.insertNode(placeholder);

      const nextRange = document.createRange();
      nextRange.setStartAfter(placeholder);
      nextRange.collapse(true);
      selection.removeAllRanges();
      selection.addRange(nextRange);

      this.savedSelection = this.saveSelection();
      this.historyNextMode = 'push';
      this.syncToMarkdown({ historyMode: 'push' });
      this.updateFormatState();
      return true;
    },
    cloneSelectionSnapshot(selection) {
      if (!selection) return null;
      return {
        startContainer: selection.startContainer || null,
        startOffset: selection.startOffset || 0,
        endContainer: selection.endContainer || null,
        endOffset: selection.endOffset || 0,
        startPath: Array.isArray(selection.startPath) ? selection.startPath.slice() : null,
        endPath: Array.isArray(selection.endPath) ? selection.endPath.slice() : null
      };
    },
    createHistorySnapshot() {
      if (this.showSource) {
        const textarea = this.$refs.sourceEditor;
        return {
          markdownContent: this.markdownContent || '',
          showSource: true,
          selectionStart: textarea ? textarea.selectionStart : 0,
          selectionEnd: textarea ? textarea.selectionEnd : 0
        };
      }

      return {
        markdownContent: this.markdownContent || '',
        showSource: false,
        selection: this.cloneSelectionSnapshot(this.saveSelection() || this.savedSelection)
      };
    },
    resetHistory() {
      if (!this.history) return;
      this.history.reset(this.createHistorySnapshot());
      this.updateHistoryState();
      this.historyNextMode = null;
    },
    recordHistorySnapshot(options = {}) {
      if (!this.history || this.historySuspend) return;
      this.history.push(this.createHistorySnapshot(), options);
      this.updateHistoryState();
    },
    updateHistoryState() {
      this.formatState = {
        ...this.formatState,
        undo: !!this.history && this.history.canUndo(),
        redo: !!this.history && this.history.canRedo()
      };
    },
    flushPendingSync() {
      if (this.syncTimer) {
        clearTimeout(this.syncTimer);
        this.syncTimer = null;
        this.syncToMarkdown({ historyMode: 'merge' });
      }
    },
    applyHistorySnapshot(snapshot) {
      if (!snapshot) return;

      this.historySuspend = true;
      this.showSource = !!snapshot.showSource;
      this.markdownContent = snapshot.markdownContent || '';
      this.emitChange();

      const finalize = () => {
        this.$nextTick(() => {
          if (snapshot.showSource) {
            const textarea = this.$refs.sourceEditor;
            if (textarea) {
              textarea.focus();
              textarea.setSelectionRange(snapshot.selectionStart || 0, snapshot.selectionEnd || 0);
            }
          } else {
            this.savedSelection = this.cloneSelectionSnapshot(snapshot.selection);
            this.focus();
            if (this.savedSelection) {
              this.restoreSelection(this.savedSelection);
            }
          }
          this.historySuspend = false;
          this.updateFormatState();
        });
      };

      if (snapshot.showSource) {
        finalize();
      } else {
        this.renderContent().finally(finalize);
      }
    },
    
    // ==================== 输入处理 ====================
    
    handleCompositionStart() {
      this.closeFullscreenTooltip();
      this.isComposing = true;
    },
    handleCompositionEnd() {
      this.closeFullscreenTooltip();
      this.isComposing = false;
      this.$nextTick(() => {
        this.flushQueuedImageUploads();
      });
    },

    /**
     * 处理输入事件
     */
    handleInput(e) {
      if (this.readonly) return;
      this.closeFullscreenTooltip();
      
      // 如果是语言标签的输入，同步更新 data-lang
      if (e && e.target && e.target.classList && e.target.classList.contains('hl-lang')) {
        const newLang = e.target.textContent.trim().toLowerCase();
        e.target.setAttribute('data-lang', newLang);
        
        // 同步更新 code 元素的 class
        const highlightWrap = e.target.closest('.highlight-wrap');
        if (highlightWrap) {
          const codeEl = highlightWrap.querySelector('pre code');
          if (codeEl) {
            // 移除旧的语言类，添加新的
            codeEl.className = codeEl.className.replace(/\bhljs\s+\w+\b/, `hljs ${newLang}`);
          }
        }
      }

      // 若是在代码块内编辑：保持 code-line 结构 + 重新高亮（行号依赖 code-line）
      const wrap = this.getHighlightWrapFromEvent(e);
      if (wrap) {
        this.scheduleRehighlightCodeBlock(wrap);
      }
      
      // 确保新插入的待办列表 checkbox 已绑定事件
      this.$nextTick(() => {
        this.bindTodoCheckboxes();
        // 清理空的待办列表
        this.cleanupEmptyTodoLists();
      });
      
      // 防抖同步到 Markdown
      if (this.syncTimer) {
        clearTimeout(this.syncTimer);
      }
      
      this.syncTimer = setTimeout(() => {
        this.syncToMarkdown();
      }, 300);

      this.updateFormatState();
    },
    
    /**
     * 处理源码编辑器输入
     */
    handleSourceInput() {
      this.closeFullscreenTooltip();
      // 源码模式下直接更新
      this.historyNextMode = 'merge';
      this.emitChange();
    },
    
    /**
     * 从编辑器同步到 Markdown
     */
    syncToMarkdown(options = {}) {
      const editor = this.$refs.editorContent;
      if (!editor) return;
      
      // 同步前清理空的待办列表和嵌套结构
      this.cleanupEmptyTodoLists(editor);

      this.normalizeTodoCheckboxAttributes(editor);
      
      const clonedEditor = editor.cloneNode(true);
      this.replaceUploadPlaceholdersWithTokens(clonedEditor);
      const html = clonedEditor.innerHTML;
      const markdown = htmlToMarkdown(html);
      
      if (this.markdownContent !== markdown) {
        this.historyNextMode = options.historyMode || this.historyNextMode;
        this.markdownContent = markdown;
        this.emitChange();
      } else {
        this.updateHistoryState();
      }
    },
    
    /**
     * 从源码同步到编辑器
     */
    syncFromSource() {
      this.renderContent();
    },
    
    /**
     * 发送变更事件
     */
    emitChange() {
      // 降级标题后发送给父组件
      const downgradedContent = downgradeMarkdownHeadings(this.markdownContent);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedContent);
      this.$emit('change', downgradedContent);
    },
    
    /**
     * 处理键盘事件
     */
    handleKeydown(e) {
      if (this.readonly) return;
      
      const { key, ctrlKey, metaKey, shiftKey } = e;
      const modKey = ctrlKey || metaKey;
      const keyLower = String(key || '').toLowerCase();

      // 代码块（highlight-wrap）内的结构化编辑：Enter 新行、Backspace 删除空行、Tab 插入空格
      const codeCtx = this.getCodeBlockContext();
      if (codeCtx && this.handleCodeBlockKeydown(e, codeCtx)) {
        return;
      }
      
      // Enter 键特殊处理：跳出代码块/引用块
      if (key === 'Enter') {
        // Ctrl+Enter 或 Shift+Enter：强制跳出当前块，在后面插入新行
        if (modKey || shiftKey) {
          e.preventDefault();
          this.insertLineAfter();
          return;
        }
        
        // 普通 Enter：检查是否在特殊块中，尝试跳出
        if (this.handleEnterInBlock(e)) {
          return; // 已处理
        }
      }
      
      // 快捷键
      if (modKey) {
        if (keyLower === 'b') {
          e.preventDefault();
          this.execFormat('bold');
        } else if (keyLower === 'i') {
          e.preventDefault();
          this.execFormat('italic');
        } else if (keyLower === 'd') {
          e.preventDefault();
          this.execFormat('strikethrough');
        } else if (keyLower === 'k') {
          e.preventDefault();
          this.insertLink();
        } else if (keyLower === 'q') {
          e.preventDefault();
          this.insertBlockquote();
        } else if (keyLower === 'u' && !shiftKey) {
          e.preventDefault();
          this.execFormat('insertUnorderedList');
        } else if (keyLower === 'o') {
          e.preventDefault();
          this.execFormat('insertOrderedList');
        } else if (keyLower === 's') {
          e.preventDefault();
          this.$emit('save');
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
        }
      }
      
      // 处理方向键跳出 code 标签
      if (key === 'ArrowRight' || key === 'ArrowLeft') {
        const selection = window.getSelection();
        if (selection.rangeCount > 0) {
          const range = selection.getRangeAt(0);
          let node = range.startContainer;
          let offset = range.startOffset;
          
          if (node.nodeType === Node.TEXT_NODE) {
            const parent = node.parentNode;
            if (parent.nodeName === 'CODE' && !parent.closest('.highlight-wrap')) {
              // 在 code 标签内
              if (key === 'ArrowRight') {
                // 如果光标在末尾
                if (offset === node.length) {
                  // 检查下一个节点是否是文本节点（包含 ZWS）
                  const next = parent.nextSibling;
                  if (next && next.nodeType === Node.TEXT_NODE) {
                    e.preventDefault();
                    // 移动到下一个文本节点（ZWS 之后）
                    const newRange = document.createRange();
                    // 如果下一个节点只有 ZWS，则光标在 ZWS 后
                    newRange.setStart(next, Math.min(1, next.length));
                    newRange.collapse(true);
                    selection.removeAllRanges();
                    selection.addRange(newRange);
                  }
                }
              } else if (key === 'ArrowLeft') {
                // 如果光标在开头
                if (offset === 0) {
                  // 检查上一个节点是否是文本节点（包含 ZWS）
                  const prev = parent.previousSibling;
                  if (prev && prev.nodeType === Node.TEXT_NODE) {
                    e.preventDefault();
                    // 移动到上一个文本节点（ZWS 之前）
                    const newRange = document.createRange();
                    newRange.setStart(prev, Math.max(0, prev.length - 1));
                    newRange.collapse(true);
                    selection.removeAllRanges();
                    selection.addRange(newRange);
                  }
                }
              }
            }
          }
        }
      }

      // Tab 键
      if (key === 'Tab') {
        e.preventDefault();
        document.execCommand('insertHTML', false, '&nbsp;&nbsp;&nbsp;&nbsp;');
      }
      
      // Escape
      if (key === 'Escape') {
        if (this.isFullscreen) {
          this.toggleFullscreen();
        }
      }
      
      // F11 全屏
      if (key === 'F11') {
        e.preventDefault();
        this.toggleFullscreen();
      }
    },

    handleDocumentKeydown(e) {
      if (this.readonly || this.showSource) return;
      const keyLower = String(e.key || '').toLowerCase();
      const isEnter = keyLower === 'enter' || e.keyCode === 13 || e.which === 13;
      if (!isEnter) return;
      if (e.ctrlKey || e.metaKey || e.shiftKey) return;

      const editor = this.$refs.editorContent;
      if (!editor) return;

      const targetEl = e.target && e.target.nodeType === Node.ELEMENT_NODE ? e.target : e.target?.parentElement;
      if (!targetEl || !editor.contains(targetEl)) return;

      const ctx = this.getTodoContextFromElement(targetEl);
      if (!ctx) return;

      e.preventDefault();
      e.stopPropagation();

      this.insertTodoItemAfter(ctx);
    },

    getTodoContextFromElement(el) {
      if (!el || !el.closest) return null;
      const li = el.closest('li');
      if (!li) return null;
      const checkbox = li.querySelector('input[type="checkbox"]');
      if (!checkbox) return null;
      const list = li.closest('ul,ol');
      if (!list) return null;
      const editor = this.$refs.editorContent;
      if (!editor || !editor.contains(li)) return null;
      return { editor, list, li, checkbox };
    },

    insertTodoItemAfter(ctx) {
      const { editor, list, li, checkbox } = ctx;
      const selection = window.getSelection();

      let range = null;
      const hasRange = selection && selection.rangeCount;
      if (hasRange) {
        range = selection.getRangeAt(0);
      }

      const rangeInLi = range && li.contains(range.startContainer.nodeType === Node.TEXT_NODE ? range.startContainer.parentNode : range.startContainer);
      if (!rangeInLi) {
        const r = document.createRange();
        r.setStartAfter(checkbox);
        r.collapse(true);
        selection.removeAllRanges();
        selection.addRange(r);
        range = r;
      }

      const text = Array.from(li.childNodes)
        .filter(n => n.nodeName !== 'INPUT')
        .map(n => n.textContent || '')
        .join('')
        .replace(/\u200B/g, '')
        .trim();

      if (!text) {
        const p = document.createElement('p');
        p.innerHTML = '<br>';
        if (list.nextSibling) {
          list.parentNode.insertBefore(p, list.nextSibling);
        } else {
          list.parentNode.appendChild(p);
        }
        li.remove();
        if (!list.querySelector('li')) {
          list.remove();
        }

        editor.focus();
        const caretRange = document.createRange();
        caretRange.setStart(p, 0);
        caretRange.collapse(true);
        selection.removeAllRanges();
        selection.addRange(caretRange);
        this.handleInput();
        return;
      }

      let extracted = null;
      let extractedHasText = false;
      if (rangeInLi && range) {
        const afterRange = range.cloneRange();
        afterRange.setEndAfter(li.lastChild);
        extracted = afterRange.extractContents();
        extracted.querySelectorAll?.('input[type="checkbox"]').forEach(n => n.remove());
        extractedHasText = Boolean(extracted.textContent && extracted.textContent.trim());
      }

      const newLi = document.createElement('li');
      newLi.className = li.className || 'task-list-item';

      const newCheckbox = document.createElement('input');
      newCheckbox.type = 'checkbox';
      newCheckbox.setAttribute('data-todo-bound', 'true');
      newCheckbox.addEventListener('change', this.handleTodoCheckboxChange);

      const contentSpan = document.createElement('span');
      let isPlaceholder = false;

      if (extracted) {
        contentSpan.appendChild(extracted);
      }

      if (!contentSpan.textContent || !contentSpan.textContent.trim()) {
        contentSpan.textContent = '待办事项';
        isPlaceholder = true;
      }

      newLi.appendChild(newCheckbox);
      newLi.appendChild(document.createTextNode(' '));
      newLi.appendChild(contentSpan);

      if (li.nextSibling) {
        list.insertBefore(newLi, li.nextSibling);
      } else {
        list.appendChild(newLi);
      }

      editor.focus();
      const caretRange = document.createRange();
      if (isPlaceholder && !extractedHasText) {
        caretRange.selectNodeContents(contentSpan);
      } else {
        caretRange.setStart(contentSpan, 0);
        caretRange.collapse(true);
      }
      selection.removeAllRanges();
      selection.addRange(caretRange);

      this.$nextTick(() => {
        this.bindTodoCheckboxes();
      });

      this.handleInput();
    },
    
    /**
     * 处理在特殊块（代码块、引用块）中按 Enter 的行为
     * @returns {boolean} 是否已处理（阻止了默认行为）
     */
    handleEnterInBlock(e) {
      const selection = window.getSelection();
      if (!selection.rangeCount) return false;
      
      const range = selection.getRangeAt(0);
      let node = range.startContainer;
      
      // 找到最近的块级父元素
      let blockParent = null;
      let blockType = null;
      
      while (node && node !== this.$refs.editorContent) {
        const tagName = node.nodeName?.toLowerCase();
        if (tagName === 'pre' || tagName === 'blockquote') {
          blockParent = node;
          blockType = tagName;
          break;
        }
        node = node.parentNode;
      }
      
      if (!blockParent) return false;

      // highlight-wrap 的代码块使用“每行 code-line”结构化编辑；不在这里做自动跳出
      if (blockType === 'pre') {
        const wrap = blockParent.closest && blockParent.closest('.highlight-wrap');
        if (wrap) return false;
      }
      
      // 检查光标是否在块的末尾，或当前行是否为空
      const isAtEnd = this.isCursorAtBlockEnd(blockParent, range);
      const isEmptyLine = this.isCurrentLineEmpty(range);
      
      // 在代码块末尾或空行按 Enter：跳出代码块
      if (blockType === 'pre' && (isAtEnd || isEmptyLine)) {
        e.preventDefault();
        this.exitBlockAndInsertParagraph(blockParent, isEmptyLine);
        return true;
      }
      
      // 在引用块中，如果当前段落为空，则跳出
      if (blockType === 'blockquote' && isEmptyLine) {
        e.preventDefault();
        this.exitBlockAndInsertParagraph(blockParent, true);
        return true;
      }
      
      return false;
    },

    /**
     * 从事件目标定位代码块容器
     */
    getHighlightWrapFromEvent(e) {
      const editor = this.$refs.editorContent;
      if (!editor || !e) return null;
      const target = e.target;
      if (!target) return null;
      const el = target.nodeType === 1 ? target : target.parentElement;
      if (!el) return null;
      const wrap = el.closest ? el.closest('.highlight-wrap') : null;
      if (!wrap) return null;
      // 必须在当前编辑器内
      if (!editor.contains(wrap)) return null;
      return wrap;
    },

    /**
     * 获取当前是否在 highlight-wrap 的 code 中编辑
     */
    getCodeBlockContext() {
      const editor = this.$refs.editorContent;
      if (!editor) return null;

      const selection = window.getSelection();
      if (!selection || !selection.rangeCount) return null;
      const range = selection.getRangeAt(0);

      const container = range.startContainer;
      const el = container.nodeType === 1 ? container : container.parentElement;
      if (!el) return null;

      const wrap = el.closest ? el.closest('.highlight-wrap') : null;
      if (!wrap || !editor.contains(wrap)) return null;

      const codeEl = wrap.querySelector('pre code.css-line-numbers');
      if (!codeEl) return null;

      const lineEl = el.closest ? el.closest('.code-line') : null;

      // 语言：优先工具栏（可编辑）
      const langSpan = wrap.querySelector('.hl-lang');
      const lang = (langSpan?.getAttribute('data-lang') || langSpan?.textContent || '').trim().toLowerCase();

      return { wrap, codeEl, lineEl, lang };
    },

    /**
     * 处理代码块中的按键（保持 code-line 结构）
     * @returns {boolean} 是否已处理
     */
    handleCodeBlockKeydown(e, ctx) {
      const { key, ctrlKey, metaKey, shiftKey } = e;
      const modKey = ctrlKey || metaKey;

      // Ctrl/Shift + Enter：沿用外层逻辑（跳出代码块）
      if (key === 'Enter' && (modKey || shiftKey)) return false;

      // Tab：插入空格（不要用 execCommand 产生 &nbsp; 破坏结构）
      if (key === 'Tab') {
        e.preventDefault();
        this.insertTextAtSelection('    ');
        this.scheduleRehighlightCodeBlock(ctx.wrap);
        return true;
      }

      if (key === 'Enter') {
        e.preventDefault();
        this.ensureCodeLines(ctx.codeEl);
        const line = this.getOrCreateCurrentCodeLine(ctx);
        this.insertCodeLineAfter(line);
        this.scheduleRehighlightCodeBlock(ctx.wrap);
        return true;
      }

      if (key === 'Backspace') {
        const selection = window.getSelection();
        if (!selection || !selection.rangeCount) return false;
        const range = selection.getRangeAt(0);
        if (!range.collapsed) return false;

        this.ensureCodeLines(ctx.codeEl);
        const line = this.getOrCreateCurrentCodeLine(ctx);
        const { offset } = this.getCaretTextOffsetInElement(line, range);

        // 在行首 Backspace：若当前行为空则删除该行并移动到上一行末尾
        if (offset === 0) {
          const prev = line.previousElementSibling;
          const isEmpty = (line.textContent || '').replace(/\u00A0/g, '').trim() === '';
          if (prev && prev.classList.contains('code-line') && isEmpty) {
            e.preventDefault();
            line.remove();
            this.placeCaretAtEnd(prev);
            this.scheduleRehighlightCodeBlock(ctx.wrap);
            return true;
          }
        }
      }

      return false;
    },

    /**
     * 确保 code 内部只有 .code-line 结构（行号依赖此结构）
     */
    ensureCodeLines(codeEl) {
      if (!codeEl) return;

      const directLines = Array.from(codeEl.children || []).filter(n => n.classList && n.classList.contains('code-line'));
      const hasNonLineChild = Array.from(codeEl.childNodes || []).some(n => {
        if (n.nodeType === 3) return (n.nodeValue || '').trim() !== '';
        if (n.nodeType !== 1) return false;
        return !n.classList.contains('code-line');
      });

      if (directLines.length === 0 || hasNonLineChild) {
        const text = (codeEl.textContent || '').replace(/\r\n/g, '\n');
        const lines = text.split('\n');
        codeEl.innerHTML = '';
        lines.forEach((t) => {
          const span = document.createElement('span');
          span.className = 'code-line';
          span.innerHTML = this.escapeHtml(t) || '&nbsp;';
          codeEl.appendChild(span);
        });
      }

      // 至少保留一行，保证可编辑/有行号
      if (!codeEl.querySelector('.code-line')) {
        const span = document.createElement('span');
        span.className = 'code-line';
        span.innerHTML = '&nbsp;';
        codeEl.appendChild(span);
      }
    },

    /**
     * 获取当前所在 code-line（没有则创建第一行）
     */
    getOrCreateCurrentCodeLine(ctx) {
      if (ctx.lineEl && ctx.lineEl.classList.contains('code-line')) return ctx.lineEl;
      const first = ctx.codeEl.querySelector('.code-line');
      if (first) return first;
      const span = document.createElement('span');
      span.className = 'code-line';
      span.innerHTML = '&nbsp;';
      ctx.codeEl.appendChild(span);
      return span;
    },

    /**
     * 在指定 code-line 后插入新行，并把光标移动过去
     */
    insertCodeLineAfter(lineEl) {
      const newLine = document.createElement('span');
      newLine.className = 'code-line';
      newLine.innerHTML = '&nbsp;';
      lineEl.insertAdjacentElement('afterend', newLine);
      this.placeCaretAtStart(newLine);
    },

    /**
     * 获取光标在某元素内的“文本偏移”
     */
    getCaretTextOffsetInElement(el, range) {
      const r = range.cloneRange();
      const pre = document.createRange();
      pre.selectNodeContents(el);
      pre.setEnd(r.startContainer, r.startOffset);
      const offset = pre.toString().length;
      return { offset };
    },

    /**
     * 将光标放到元素开头
     */
    placeCaretAtStart(el) {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(el);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
    },

    /**
     * 将光标放到元素末尾
     */
    placeCaretAtEnd(el) {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(el);
      range.collapse(false);
      selection.removeAllRanges();
      selection.addRange(range);
    },

    /**
     * 在当前选区插入纯文本（用于 Tab 等）
     */
    insertTextAtSelection(text) {
      const selection = window.getSelection();
      if (!selection || !selection.rangeCount) return;
      const range = selection.getRangeAt(0);
      range.deleteContents();
      const node = document.createTextNode(text);
      range.insertNode(node);
      range.setStartAfter(node);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
    },

    /**
     * 防抖重新高亮某个代码块（保持行号结构不被浏览器默认行为破坏）
     */
    scheduleRehighlightCodeBlock(wrap) {
      if (!wrap) return;
      if (this.codeHighlightTimer) clearTimeout(this.codeHighlightTimer);
      this.codeHighlightTimer = setTimeout(() => {
        this.rehighlightCodeBlock(wrap);
      }, 180);
    },

    /**
     * 重新高亮代码块（逐行高亮，结构与 markdownLazyRenderer 一致）
     */
    async rehighlightCodeBlock(wrap) {
      const codeEl = wrap.querySelector('pre code.css-line-numbers');
      if (!codeEl) return;

      // 规范化结构（保证有 code-line）
      this.ensureCodeLines(codeEl);

      // 保存光标位置（若当前在该代码块内）
      const selection = window.getSelection();
      let caret = null;
      if (selection && selection.rangeCount) {
        const range = selection.getRangeAt(0);
        const container = range.startContainer.nodeType === 1 ? range.startContainer : range.startContainer.parentElement;
        const lineEl = container?.closest ? container.closest('.code-line') : null;
        if (lineEl && codeEl.contains(lineEl)) {
          const lines = Array.from(codeEl.querySelectorAll('.code-line'));
          const lineIndex = lines.indexOf(lineEl);
          const offset = this.getCaretTextOffsetInElement(lineEl, range).offset;
          caret = { lineIndex, offset };
        }
      }

      // 语言：优先可编辑标签
      const langSpan = wrap.querySelector('.hl-lang');
      const lang = (langSpan?.getAttribute('data-lang') || langSpan?.textContent || '').trim().toLowerCase();

      // 懒加载 hljs
      let hljs = null;
      try {
        if (!this.hljsInstance) {
          if (!this.hljsLoading) {
            this.hljsLoading = import('highlight.js').then(m => {
              this.hljsInstance = m.default || m;
              return this.hljsInstance;
            });
          }
          hljs = await this.hljsLoading;
        } else {
          hljs = this.hljsInstance;
        }
      } catch (__) {
        hljs = null;
      }

      const canHighlight = Boolean(hljs && lang && typeof hljs.getLanguage === 'function' && hljs.getLanguage(lang));

      const lines = Array.from(codeEl.querySelectorAll('.code-line'));
      lines.forEach((lineEl) => {
        const text = (lineEl.textContent || '').replace(/\u00A0/g, '');
        if (!text) {
          lineEl.innerHTML = '&nbsp;';
          return;
        }
        if (canHighlight) {
          try {
            const h = hljs.highlight(text, { language: lang, ignoreIllegals: true }).value;
            lineEl.innerHTML = h || this.escapeHtml(text);
          } catch (__) {
            lineEl.innerHTML = this.escapeHtml(text);
          }
        } else {
          lineEl.innerHTML = this.escapeHtml(text);
        }
      });

      // 恢复光标
      if (caret) {
        const newLines = Array.from(codeEl.querySelectorAll('.code-line'));
        const targetLine = newLines[caret.lineIndex] || newLines[newLines.length - 1];
        if (targetLine) {
          this.placeCaretByTextOffset(targetLine, caret.offset);
        }
      }
    },

    /**
     * 在元素内按“文本偏移”放置光标（支持内部高亮 span）
     */
    placeCaretByTextOffset(container, offset) {
      const selection = window.getSelection();
      const range = document.createRange();

      const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null);
      let node = null;
      let current = 0;

      while ((node = walker.nextNode())) {
        const len = node.nodeValue?.length || 0;
        if (current + len >= offset) {
          range.setStart(node, Math.max(0, offset - current));
          range.collapse(true);
          selection.removeAllRanges();
          selection.addRange(range);
          return;
        }
        current += len;
      }

      // 兜底：放到末尾
      range.selectNodeContents(container);
      range.collapse(false);
      selection.removeAllRanges();
      selection.addRange(range);
    },
    
    /**
     * 检查光标是否在块元素末尾
     */
    isCursorAtBlockEnd(block, range) {
      // 创建一个范围从光标到块末尾
      const testRange = document.createRange();
      testRange.selectNodeContents(block);
      testRange.setStart(range.endContainer, range.endOffset);
      
      // 检查这个范围内是否只有空白内容
      const remainingText = testRange.toString().trim();
      return remainingText === '';
    },
    
    /**
     * 检查当前行是否为空
     */
    isCurrentLineEmpty(range) {
      let node = range.startContainer;
      
      // 如果是文本节点，获取父元素
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent;
        // 检查整行是否为空或只有空白
        return text.trim() === '';
      }
      
      // 如果是元素节点
      if (node.nodeType === Node.ELEMENT_NODE) {
        const text = node.textContent;
        return text.trim() === '';
      }
      
      return false;
    },
    
    /**
     * 跳出块元素并在后面插入新段落
     */
    exitBlockAndInsertParagraph(block, removeEmptyLine = false) {
      const editor = this.$refs.editorContent;
      if (!editor) return;
      
      // 如果需要移除空行（例如在代码块中的空行触发跳出）
      if (removeEmptyLine) {
        const selection = window.getSelection();
        if (selection.rangeCount) {
          const range = selection.getRangeAt(0);
          let node = range.startContainer;
          
          // 对于代码块，移除末尾的空行
          if (block.nodeName.toLowerCase() === 'pre') {
            const code = block.querySelector('code') || block;
            const text = code.textContent;
            // 移除末尾的空行
            if (text.endsWith('\n')) {
              code.textContent = text.slice(0, -1);
            }
          }
        }
      }
      
      // 创建新段落
      const newParagraph = document.createElement('p');
      newParagraph.innerHTML = '<br>';
      
      // 在块元素后面插入新段落
      if (block.nextSibling) {
        block.parentNode.insertBefore(newParagraph, block.nextSibling);
      } else {
        block.parentNode.appendChild(newParagraph);
      }
      
      // 将光标移动到新段落
      const selection = window.getSelection();
      const newRange = document.createRange();
      newRange.setStart(newParagraph, 0);
      newRange.collapse(true);
      selection.removeAllRanges();
      selection.addRange(newRange);
      
      this.handleInput();
    },
    
    /**
     * 在当前行/块之前插入空行
     */
    insertLineBefore() {
      this.focus();
      
      const block = this.getCurrentBlock();
      if (!block) {
        // 没有找到块元素，在编辑器开头插入
        const editor = this.$refs.editorContent;
        const newParagraph = document.createElement('p');
        newParagraph.innerHTML = '<br>';
        editor.insertBefore(newParagraph, editor.firstChild);
        this.moveCursorTo(newParagraph);
        this.handleInput();
        return;
      }
      
      const newParagraph = document.createElement('p');
      newParagraph.innerHTML = '<br>';
      block.parentNode.insertBefore(newParagraph, block);
      
      // 移动光标到新段落
      this.moveCursorTo(newParagraph);
      this.handleInput();
    },
    
    /**
     * 在当前行/块之后插入空行
     */
    insertLineAfter() {
      this.focus();
      
      const block = this.getCurrentBlock();
      if (!block) {
        // 没有找到块元素，在编辑器末尾插入
        const editor = this.$refs.editorContent;
        const newParagraph = document.createElement('p');
        newParagraph.innerHTML = '<br>';
        editor.appendChild(newParagraph);
        this.moveCursorTo(newParagraph);
        this.handleInput();
        return;
      }
      
      const newParagraph = document.createElement('p');
      newParagraph.innerHTML = '<br>';
      
      if (block.nextSibling) {
        block.parentNode.insertBefore(newParagraph, block.nextSibling);
      } else {
        block.parentNode.appendChild(newParagraph);
      }
      
      // 移动光标到新段落
      this.moveCursorTo(newParagraph);
      this.handleInput();
    },
    
    /**
     * 获取当前光标所在的块级元素
     */
    getCurrentBlock() {
      const selection = window.getSelection();
      if (!selection.rangeCount) return null;
      
      const range = selection.getRangeAt(0);
      let node = range.startContainer;
      
      // 如果是文本节点，获取父元素
      if (node.nodeType === Node.TEXT_NODE) {
        node = node.parentNode;
      }
      
      const editor = this.$refs.editorContent;
      
      // 向上查找块级元素（编辑器的直接子元素）
      while (node && node !== editor && node.parentNode !== editor) {
        node = node.parentNode;
      }
      
      // 如果找到的是编辑器的直接子元素，返回它
      if (node && node.parentNode === editor) {
        return node;
      }
      
      // 如果光标直接在编辑器内（没有块级元素包裹），返回 null
      return null;
    },
    
    /**
     * 移动光标到指定元素
     */
    moveCursorTo(element) {
      const selection = window.getSelection();
      const range = document.createRange();
      range.setStart(element, 0);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
    },
    
    /**
     * 处理编辑器点击事件（代码块复制等）
     */
    handleEditorClick(e) {
      const target = e.target;
      
      // 处理代码块复制按钮点击
      if (target.classList.contains('copy-code')) {
        e.preventDefault();
        e.stopPropagation();
        
        // 从代码块重新获取最新的代码内容
        const highlightWrap = target.closest('.highlight-wrap');
        const codeNode = highlightWrap ? highlightWrap.querySelector('pre code') : null;
        let code = '';
        
        if (codeNode) {
          // 从 code-line 结构中提取
          const codeLines = codeNode.querySelectorAll('.code-line');
          if (codeLines.length > 0) {
            code = Array.from(codeLines).map(line => line.textContent).join('\n');
          } else {
            code = codeNode.textContent || '';
          }
        } else {
          // 降级：使用 data-code
          const encodedCode = target.getAttribute('data-code');
          if (encodedCode) code = decodeURIComponent(encodedCode);
        }
        
        if (code) {
          navigator.clipboard.writeText(code).then(() => {
            this.$message.success('代码已复制');
          }).catch(() => {
            // 降级方案
            const textarea = document.createElement('textarea');
            textarea.value = code;
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
            this.$message.success('代码已复制');
          });
        }
        return;
      }
      
      // 处理语言标签点击（选中全部文本便于编辑）
      if (target.classList.contains('hl-lang')) {
        // 选中全部文本
        const range = document.createRange();
        range.selectNodeContents(target);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
      }
      
      this.updateSavedSelection();
    },
    
    /**
     * 处理焦点
     */
    handleFocus() {
      this.isFocused = true;
      this.closeFullscreenTooltip();
      this.updateSavedSelection();
      this.updateFormatState();
      this.$emit('focus');
    },
    
    /**
     * 处理失焦
     */
    handleBlur() {
      this.isFocused = false;
      // 失焦时立即同步
      this.syncToMarkdown();
      this.$emit('blur');
    },
    
    // ==================== 格式化操作 ====================
    
    /**
     * 执行格式化命令
     */
    execFormat(command) {
      this.focus();
      
      const selection = window.getSelection();
      let insertedZWS = false;

      // 如果是空选区（collapsed），插入零宽空格并选中，以便让 format 命令生效并支持 toggle
      if (selection.rangeCount > 0 && selection.getRangeAt(0).collapsed) {
        // 检查当前是否已经在该格式中（如果是，则不需要插入 ZWS，直接执行命令即可取消）
        const isStateActive = document.queryCommandState(command);
        if (!isStateActive) {
          document.execCommand('insertHTML', false, '\u200B');
          // 选中刚才插入的 ZWS
          const range = selection.getRangeAt(0);
          range.setStart(range.startContainer, range.startOffset - 1);
          selection.removeAllRanges();
          selection.addRange(range);
          insertedZWS = true;
        }
      }
      
      document.execCommand(command, false, null);
      
      // 如果是为了激活格式而插入了 ZWS，执行完命令后将光标折叠到末尾
      // 这样 IME 输入时是追加内容而不是替换选区，防止格式丢失
      if (insertedZWS) {
        selection.collapseToEnd();
      }

      this.updateFormatState();
      this.handleInput();
    },
    
    /**
     * 更新格式状态
     */
    updateFormatState() {
      // 检测行内代码状态
      const selection = window.getSelection();
      let inlineCode = false;
      if (selection.rangeCount > 0) {
        const range = selection.getRangeAt(0);
        let container = range.commonAncestorContainer;
        if (container.nodeType === Node.TEXT_NODE) {
          container = container.parentNode;
        }
        // 排除 highlight-wrap 内的 code
        const codeEl = container.closest('code');
        if (codeEl && !codeEl.closest('.highlight-wrap')) {
          inlineCode = true;
        }
      }

      this.formatState = {
        bold: document.queryCommandState('bold'),
        italic: document.queryCommandState('italic'),
        strikethrough: document.queryCommandState('strikethrough'),
        underline: document.queryCommandState('underline'),
        undo: !!this.history && this.history.canUndo(),
        redo: !!this.history && this.history.canRedo(),
        inlineCode
      };
      this.updateSavedSelection();
    },
    
    /**
     * 插入标题
     */
    insertHeading(level) {
      this.focus();
      
      if (level === 'p') {
        document.execCommand('formatBlock', false, 'p');
      } else {
        document.execCommand('formatBlock', false, `h${level}`);
      }
      
      this.handleInput();
    },
    
    /**
     * 插入引用
     */
    insertBlockquote() {
      this.focus();
      
      const selection = window.getSelection();
      if (!selection.rangeCount) return;
      
      const range = selection.getRangeAt(0);
      const blockquote = document.createElement('blockquote');
      
      // 获取选中的内容
      const content = range.extractContents();
      let isDefaultContent = false;
      
      if (content.textContent.trim()) {
        blockquote.appendChild(content);
      } else {
        blockquote.innerHTML = '<p>引用内容</p>';
        isDefaultContent = true;
      }
      
      range.insertNode(blockquote);
      
      // 如果是默认内容，选中它
      if (isDefaultContent) {
        const p = blockquote.querySelector('p');
        if (p) {
          range.selectNodeContents(p);
          selection.removeAllRanges();
          selection.addRange(range);
        } else {
          selection.collapse(blockquote, blockquote.childNodes.length);
        }
      } else {
        // 移动光标到引用内部末尾
        selection.collapse(blockquote, blockquote.childNodes.length);
      }
      
      this.handleInput();
    },
    
    /**
     * 插入行内代码
     */
    insertInlineCode() {
      this.focus();
      
      const selection = window.getSelection();
      if (!selection.rangeCount) return;
      
      const range = selection.getRangeAt(0);
      
      // 检查是否已经在 code 标签内（支持 toggle 取消）
      let container = range.commonAncestorContainer;
      if (container.nodeType === Node.TEXT_NODE) {
        container = container.parentNode;
      }
      const existingCode = container.closest('code');
      
      if (existingCode) {
        // 如果在代码块内（highlight-wrap），不应该取消行内代码逻辑，这里只处理普通行内 code
        if (existingCode.closest('.highlight-wrap')) return;
        
        // 取消行内代码：将 code 替换为其文本内容
        const textContent = existingCode.textContent;
        const textNode = document.createTextNode(textContent);
        
        existingCode.parentNode.replaceChild(textNode, existingCode);
        
        // 选中该文本
      range.selectNode(textNode);
      selection.removeAllRanges();
      selection.addRange(range);
      
      this.handleInput();
      return;
    }

    const selectedText = range.toString();
    const isDefault = !selectedText;
    
    // 创建包含 ZWS 的片段
    const fragment = document.createDocumentFragment();
    // 前置 ZWS
    fragment.appendChild(document.createTextNode('\u200B'));
    
    const code = document.createElement('code');
    code.textContent = selectedText || '代码';
    fragment.appendChild(code);
    
    // 后置 ZWS
    fragment.appendChild(document.createTextNode('\u200B'));
    
    range.deleteContents();
    range.insertNode(fragment);
    
    if (isDefault) {
      // 选中默认文本（在 code 内部）
      range.selectNodeContents(code);
      selection.removeAllRanges();
      selection.addRange(range);
    } else {
      // 移动光标到后置 ZWS 后面（跳出代码）
      // 注意：fragment 插入后不再存在，需要定位节点
      const nextNode = code.nextSibling;
      if (nextNode && nextNode.nodeType === Node.TEXT_NODE) {
        range.setStart(nextNode, 1); // ZWS 长度为 1，光标放在它后面
        range.collapse(true);
        selection.removeAllRanges();
        selection.addRange(range);
      }
    }
    
    this.handleInput();
  },
    
    /**
     * 插入代码块（生成与 markdownLazyRenderer 一致的结构）
     */
    insertCodeBlock() {
      this.focus();
      if (this.savedSelection) {
        this.restoreSelection(this.savedSelection);
      }
      
      const selection = window.getSelection();
      const editor = this.$refs.editorContent;
      if (!selection.rangeCount && editor) {
        const endRange = document.createRange();
        endRange.selectNodeContents(editor);
        endRange.collapse(false);
        selection.removeAllRanges();
        selection.addRange(endRange);
      }
      if (!selection.rangeCount) return;
      
      let range = selection.getRangeAt(0);
      if (editor) {
        const startNode = range.startContainer;
        const inEditor = editor.contains(startNode.nodeType === Node.TEXT_NODE ? startNode.parentNode : startNode);
        if (!inEditor) {
          const endRange = document.createRange();
          endRange.selectNodeContents(editor);
          endRange.collapse(false);
          selection.removeAllRanges();
          selection.addRange(endRange);
          range = selection.getRangeAt(0);
        } else {
          let container = range.startContainer;
          if (container.nodeType === Node.TEXT_NODE) container = container.parentNode;
          const wrap = container && container.closest ? container.closest('.highlight-wrap') : null;
          if (wrap && editor.contains(wrap)) {
            const afterWrap = document.createRange();
            afterWrap.setStartAfter(wrap);
            afterWrap.collapse(true);
            selection.removeAllRanges();
            selection.addRange(afterWrap);
            range = selection.getRangeAt(0);
          }
        }
      }

      const selectedText = range.toString() || '// 代码';
      const lang = 'javascript';
      const langLabel = lang.toUpperCase();
      const encodedCode = encodeURIComponent(selectedText);
      const markerId = `code-block-marker-${Math.random().toString(36).slice(2)}`;
      
      // 构造代码行 HTML（与 markdownLazyRenderer 一致）
      const lines = selectedText.split(/\r?\n/);
      const codeHTML = lines.map(line => {
        const escaped = this.escapeHtml(line);
        return `<span class="code-line">${escaped || '&nbsp;'}</span>`;
      }).join('');
      
      // 生成与 markdownLazyRenderer 一致的代码块结构
      // 工具栏中：点按钮不可编辑，语言标签可编辑
      const codeBlockHtml = `
        <div class="highlight-wrap has-toolbar">
          <div class="highlight-toolbar">
            <span class="hl-dots" contenteditable="false" aria-hidden="true"></span>
            <span class="hl-lang" data-lang="${lang}">${langLabel}</span>
            <i class="el-icon-document-copy copy-code" contenteditable="false" data-code="${encodedCode}" title="复制"></i>
          </div>
          <pre><code class="hljs ${lang} css-line-numbers">${codeHTML}</code></pre>
        </div>
        <p><span id="${markerId}"></span><br></p>
      `;
      document.execCommand('insertHTML', false, codeBlockHtml);
      
      this.$nextTick(() => {
        const marker = document.getElementById(markerId);
        if (marker && editor && editor.contains(marker)) {
          const caretRange = document.createRange();
          caretRange.setStartAfter(marker);
          caretRange.collapse(true);
          
          const sel = window.getSelection();
          sel.removeAllRanges();
          sel.addRange(caretRange);
          
          marker.parentNode?.removeChild(marker);
          this.updateSavedSelection();
        }
        
        this.handleInput();
      });
    },
    
    /**
     * HTML 转义辅助方法
     */
    escapeHtml(str) {
      const div = document.createElement('div');
      div.textContent = str;
      return div.innerHTML;
    },
    
    /**
     * 插入表格（生成与 markdownLazyRenderer 一致的结构）
     */
    insertTable() {
      this.focus();
      
      // 生成带 table-wrapper 的表格结构
      const tableHtml = `
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>列1</th>
                <th>列2</th>
                <th>列3</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>内容</td>
                <td>内容</td>
                <td>内容</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p><br></p>
      `;
      
      document.execCommand('insertHTML', false, tableHtml);
      this.handleInput();
      
      this.$message.info('提示：在表格内点击右键可进行行列操作');
    },
    
    /**
     * 插入分割线
     */
    insertHorizontalRule() {
      this.focus();
      document.execCommand('insertHorizontalRule', false, null);
      this.handleInput();
    },
    
    /**
     * 插入待办列表
     */
    insertTodoList() {
      this.focus();
      
      const selection = window.getSelection();
      if (!selection.rangeCount) return;
      
      const range = selection.getRangeAt(0);
      let container = range.startContainer;
      
      // 如果是文本节点，获取父元素
      if (container.nodeType === Node.TEXT_NODE) {
        container = container.parentNode;
      }
      
      // 检查是否已经在待办列表中
      const existingList = container.closest ? container.closest('ul.task-list') : null;
      
      if (existingList) {
        // 在现有列表中插入新项
        const newLi = document.createElement('li');
        newLi.className = 'task-list-item';
        newLi.innerHTML = '<input type="checkbox"> 待办事项';
        
        // 找到当前所在的 li
        const currentLi = container.closest ? container.closest('li.task-list-item') : null;
        if (currentLi && currentLi.nextSibling) {
          existingList.insertBefore(newLi, currentLi.nextSibling);
        } else {
          existingList.appendChild(newLi);
        }
        
        // 移动光标到新项
        const checkbox = newLi.querySelector('input[type="checkbox"]');
        if (checkbox) {
          checkbox.setAttribute('data-todo-bound', 'true');
          checkbox.addEventListener('change', this.handleTodoCheckboxChange);
          
          // 选中“待办事项”文本，方便用户直接输入替换
          const textNode = newLi.lastChild;
          if (textNode) {
            range.selectNode(textNode);
            selection.removeAllRanges();
            selection.addRange(range);
          } else {
            // 降级：光标放到末尾
            range.setStartAfter(checkbox);
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
          }
        }
      } else {
        // 创建新的待办列表
        const todoHtml = `
          <ul class="task-list">
            <li class="task-list-item">
              <input type="checkbox"> 待办事项
            </li>
          </ul>
        `;
        
        // 检查光标是否在段落中，如果是，替换整个段落
        const currentBlock = this.getCurrentBlock();
        if (currentBlock && currentBlock.nodeName === 'P' && (!currentBlock.textContent || currentBlock.textContent.trim() === '')) {
          currentBlock.outerHTML = todoHtml;
        } else {
          range.deleteContents();
          const temp = document.createElement('div');
          temp.innerHTML = todoHtml;
          const fragment = document.createDocumentFragment();
          while (temp.firstChild) {
            fragment.appendChild(temp.firstChild);
          }
          range.insertNode(fragment);
        }
        
        // 为 checkbox 添加点击事件，并选中文本
      this.$nextTick(() => {
        const editor = this.$refs.editorContent;
        if (editor) {
          const checkbox = editor.querySelector('.task-list input[type="checkbox"]:not([data-todo-bound])');
          if (checkbox) {
            checkbox.setAttribute('data-todo-bound', 'true');
            checkbox.addEventListener('change', this.handleTodoCheckboxChange);
            
            // 选中“待办事项”文本
            const li = checkbox.parentNode;
            if (li && li.lastChild) {
              const selection = window.getSelection();
              const range = document.createRange();
              range.selectNode(li.lastChild);
              selection.removeAllRanges();
              selection.addRange(range);
            }
          }
        }
      });
      }
      
      this.handleInput();
    },
    
    /**
     * 绑定所有待办列表 checkbox 的事件
     */
    bindTodoCheckboxes(container) {
      if (!container) container = this.$refs.editorContent;
      if (!container) return;
      
      const checkboxes = container.querySelectorAll('li.task-list-item input[type="checkbox"]:not([data-todo-bound]), .task-list input[type="checkbox"]:not([data-todo-bound])');
      checkboxes.forEach(checkbox => {
        checkbox.setAttribute('data-todo-bound', 'true');
        checkbox.addEventListener('change', this.handleTodoCheckboxChange);
      });
    },

    normalizeTodoCheckboxAttributes(container) {
      if (!container) container = this.$refs.editorContent;
      if (!container) return;
      const checkboxes = container.querySelectorAll('li.task-list-item input[type="checkbox"], .task-list input[type="checkbox"]');
      checkboxes.forEach((checkbox) => {
        if (checkbox.checked) {
          checkbox.setAttribute('checked', '');
        } else {
          checkbox.removeAttribute('checked');
        }
      });
    },
    
    /**
     * 清理空的待办列表（避免产生嵌套结构）
     */
    cleanupEmptyTodoLists(container) {
      if (!container) container = this.$refs.editorContent;
      if (!container) return;
      
      // 获取当前光标所在的 li（如果焦点在编辑器内）
      const selection = window.getSelection();
      let activeLi = null;
      if (selection.rangeCount > 0) {
        const range = selection.getRangeAt(0);
        const startNode = range.startContainer;
        // 找到最近的 li
        const node = startNode.nodeType === Node.TEXT_NODE ? startNode.parentNode : startNode;
        activeLi = node.closest('li.task-list-item');
      }

      const todoLists = container.querySelectorAll('ul.task-list');
      todoLists.forEach(ul => {
        // 先清理空的列表项
        const items = Array.from(ul.querySelectorAll('li.task-list-item'));
        items.forEach(li => {
          // 如果是当前正在编辑的行，跳过清理
          if (li === activeLi) return;

          const text = Array.from(li.childNodes)
            .filter(n => n.nodeName !== 'INPUT')
            .map(n => n.textContent || '')
            .join('')
            .trim();
          // 如果列表项没有文本内容，删除它
          if (!text) {
            li.remove();
          }
        });
        
        // 检查是否有有效的列表项（有文本内容，或者正在编辑中）
        const remainingItems = Array.from(ul.querySelectorAll('li.task-list-item'));
        const hasValidItems = remainingItems.some(li => {
          if (li === activeLi) return true; // 正在编辑的行视为有效

          const text = Array.from(li.childNodes)
            .filter(n => n.nodeName !== 'INPUT')
            .map(n => n.textContent || '')
            .join('')
            .trim();
          return text.length > 0;
        });
        
        // 如果没有有效项，删除整个列表
        if (!hasValidItems && remainingItems.length === 0) {
          // 如果列表有父元素，用段落替换
          const parent = ul.parentNode;
          if (parent) {
            const p = document.createElement('p');
            p.innerHTML = '<br>';
            parent.replaceChild(p, ul);
          } else {
            ul.remove();
          }
        }
        
        // 检查是否有嵌套的 task-list（不应该存在）
        const nestedLists = ul.querySelectorAll('ul.task-list');
        nestedLists.forEach(nested => {
          // 将嵌套的列表项提升到外层
          const nestedItems = Array.from(nested.querySelectorAll('li.task-list-item'));
          nestedItems.forEach(item => {
            ul.appendChild(item);
          });
          nested.remove();
        });
      });
    },
    
    /**
     * 处理待办列表 checkbox 状态变化
     */
    handleTodoCheckboxChange(e) {
      const checkbox = e && e.target ? e.target : null;
      if (checkbox && checkbox.tagName === 'INPUT' && checkbox.type === 'checkbox') {
        if (checkbox.checked) {
          checkbox.setAttribute('checked', '');
        } else {
          checkbox.removeAttribute('checked');
        }
      }
      this.syncToMarkdown();
    },
    
    /**
     * 插入链接
     */
    insertLink() {
      // 保存当前选区
      this.savedSelection = this.saveSelection();
      
      // 获取选中的文本
      const selection = window.getSelection();
      this.linkForm.text = selection.toString() || '';
      this.linkForm.url = '';
      
      this.linkDialogVisible = true;
    },
    
    /**
     * 确认插入链接
     */
    confirmInsertLink() {
      this.linkDialogVisible = false;
      
      // 恢复选区
      if (this.savedSelection) {
        this.restoreSelection(this.savedSelection);
      }
      
      this.focus();
      
      const { text, url } = this.linkForm;
      if (!url) return;
      
      const linkText = text || url;
      const linkHtml = `<a href="${url}" target="_blank">${linkText}</a>`;
      
      document.execCommand('insertHTML', false, linkHtml);
      this.handleInput();
    },
    
    // ==================== 剪贴板处理 ====================
    
    /**
     * 处理粘贴
     */
    handlePaste(e) {
      handlePasteUtil(e, {
        onFile: (file) => {
          this.queueOrStartImageUpload(file);
        },
        onText: (text) => {
          this.insertMarkdownContent(text);
        }
      });
    },
    
    /**
     * 插入 Markdown 内容
     */
    async insertMarkdownContent(markdown, selectionSnapshot = null) {
      if (!markdown) return;
      
      try {
        if (selectionSnapshot) {
          this.savedSelection = this.cloneSelectionSnapshot(selectionSnapshot);
          this.focus();
          this.restoreSelection(selectionSnapshot);
        }
        const html = await renderMarkdown(markdown);
        document.execCommand('insertHTML', false, html);
        this.handleInput();
      } catch (err) {
        console.error('Insert markdown error:', err);
        // 降级：插入纯文本
        document.execCommand('insertText', false, markdown);
        this.handleInput();
      }
    },
    
    /**
     * 处理复制
     */
    handleCopy(e) {
      // 同步获取 Markdown
      this.syncToMarkdown();
      
      const selection = window.getSelection();
      if (!selection.rangeCount) return;
      
      // 获取选中的 HTML
      const range = selection.getRangeAt(0);
      const container = document.createElement('div');
      container.appendChild(range.cloneContents());
      const html = container.innerHTML;
      
      // 转换为 Markdown
      const markdown = htmlToMarkdown(html);
      
      e.clipboardData.setData('text/html', html);
      e.clipboardData.setData('text/plain', markdown);
      e.clipboardData.setData('text/x-poetize-markdown', markdown);
      e.clipboardData.setData('text/markdown', markdown);
      
      e.preventDefault();
    },
    
    /**
     * 处理剪切
     */
    handleCut(e) {
      this.handleCopy(e);
      document.execCommand('delete');
      this.handleInput();
    },
    
    // ==================== 选区操作 ====================
    
    /**
     * 保存选区
     */
    saveSelection() {
      const selection = window.getSelection();
      if (!selection.rangeCount) return null;
      
      const range = selection.getRangeAt(0);
      const editor = this.$refs.editorContent;
      const startPath = editor && editor.contains(range.startContainer)
        ? this.getNodePath(editor, range.startContainer)
        : null;
      const endPath = editor && editor.contains(range.endContainer)
        ? this.getNodePath(editor, range.endContainer)
        : null;
      return {
        startContainer: range.startContainer,
        startOffset: range.startOffset,
        endContainer: range.endContainer,
        endOffset: range.endOffset,
        startPath,
        endPath
      };
    },
    
    /**
     * 恢复选区
     */
    restoreSelection(savedSel) {
      if (!savedSel) return;
      
      try {
        const editor = this.$refs.editorContent;
        const selection = window.getSelection();
        const range = document.createRange();
        
        const startContainerValid = !editor || editor.contains(savedSel.startContainer);
        const endContainerValid = !editor || editor.contains(savedSel.endContainer);
        
        if (startContainerValid && endContainerValid) {
          range.setStart(savedSel.startContainer, this.clampOffset(savedSel.startContainer, savedSel.startOffset));
          range.setEnd(savedSel.endContainer, this.clampOffset(savedSel.endContainer, savedSel.endOffset));
        } else if (editor && savedSel.startPath && savedSel.endPath) {
          const startNode = this.resolveNodeFromPath(editor, savedSel.startPath) || editor;
          const endNode = this.resolveNodeFromPath(editor, savedSel.endPath) || startNode;
          range.setStart(startNode, this.clampOffset(startNode, savedSel.startOffset));
          range.setEnd(endNode, this.clampOffset(endNode, savedSel.endOffset));
        } else {
          return;
        }
        
        selection.removeAllRanges();
        selection.addRange(range);
      } catch (e) {
        // 选区恢复失败，忽略
      }
    },

    updateSavedSelection() {
      if (this.showSource) return;
      const editor = this.$refs.editorContent;
      const selection = window.getSelection();
      if (!editor || !selection.rangeCount) return;
      
      const range = selection.getRangeAt(0);
      let container = range.commonAncestorContainer;
      if (container && container.nodeType === Node.TEXT_NODE) {
        container = container.parentNode;
      }
      if (!container || !editor.contains(container)) return;
      
      this.savedSelection = this.saveSelection();
    },
    
    // ==================== 历史操作 ====================
    
    /**
     * 撤销
     */
    undo() {
      this.flushPendingSync();
      if (!this.history || !this.history.canUndo()) return;
      const snapshot = this.history.undo();
      this.updateHistoryState();
      this.applyHistorySnapshot(snapshot);
    },
    
    /**
     * 重做
     */
    redo() {
      if (!this.history || !this.history.canRedo()) return;
      const snapshot = this.history.redo();
      this.updateHistoryState();
      this.applyHistorySnapshot(snapshot);
    },
    
    // ==================== 工具操作 ====================
    
    /**
     * 切换源码显示
     */
    toggleSource() {
      if (this.showSource) {
        // 从源码切换到 WYSIWYG
        this.syncFromSource();
      } else {
        // 从 WYSIWYG 切换到源码
        this.syncToMarkdown();
      }
      
      this.showSource = !this.showSource;
    },
    
    /**
     * 切换全屏
     */
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

    goPluginManager() {
      const doNavigate = () => {
        if (this.isFullscreen) {
          this.toggleFullscreen();
        }
        this.$router.push({ name: 'pluginManager', query: { type: 'editor' } });
      };

      // 检测内容是否被修改过
      const currentValue = this.getValue();
      const originalValue = this.value || '';
      if (currentValue !== originalValue) {
        this.$confirm('当前内容尚未保存，切换编辑器后修改将会丢失，确定要离开吗？', '提示', {
          confirmButtonText: '确定离开',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          doNavigate();
        }).catch(() => {});
      } else {
        doNavigate();
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
        const html = await renderMarkdown(content);
        
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html;
        
        // 应用内联样式
        this.inlineStyles(wrapper);
        
        const resultHtml = wrapper.innerHTML;
        const markdownText = content;
        const plainText = wrapper.innerText || markdownText;
        
        // 使用 Clipboard API
        if (navigator.clipboard && navigator.clipboard.write && typeof ClipboardItem !== 'undefined') {
          try {
            const items = {};
            items['text/html'] = new Blob([resultHtml], { type: 'text/html' });
            items['text/plain'] = new Blob([plainText], { type: 'text/plain' });
            
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
        
        const handleCopy = (evt) => {
          if (!evt.clipboardData) return;
          evt.preventDefault();
          evt.clipboardData.setData('text/html', resultHtml);
          evt.clipboardData.setData('text/plain', plainText);
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
     * 聚焦编辑器
     */
    focus() {
      if (this.showSource) {
        this.$refs.sourceEditor?.focus();
      } else {
        this.$refs.editorContent?.focus();
        if (this.savedSelection) {
          this.restoreSelection(this.savedSelection);
        }
      }
    },
    
    /**
     * 失焦编辑器
     */
    blur() {
      if (this.showSource) {
        this.$refs.sourceEditor?.blur();
      } else {
        this.$refs.editorContent?.blur();
      }
    },

    closeFullscreenTooltip() {
      const tooltip = this.$refs.fullscreenTooltip;
      if (!tooltip) return;

      if (typeof tooltip.doClose === 'function') {
        tooltip.doClose();
      } else {
        tooltip.showPopper = false;
      }
    },

    /**
     * 获取内容（降级标题后返回）
     */
    getValue() {
      // 确保同步
      if (!this.showSource) {
        this.syncToMarkdown();
      }
      return downgradeMarkdownHeadings(this.markdownContent);
    },

    /**
     * 设置内容（升级标题后显示）
     */
    setValue(value) {
      const displayValue = upgradeMarkdownHeadings(value || '');
      this.historySuspend = true;
      this.markdownContent = displayValue;
      this.renderContent();
      this.$nextTick(() => {
        this.resetHistory();
        this.historySuspend = false;
      });
    },

    /**
     * 在光标位置插入内容
     */
    insertValue(text) {
      this.focus();
      
      if (this.showSource) {
        // 源码模式：直接插入
        const textarea = this.$refs.sourceEditor;
        if (textarea) {
          const start = textarea.selectionStart;
          const end = textarea.selectionEnd;
          const value = textarea.value;
          this.historyNextMode = 'push';
          textarea.value = value.slice(0, start) + text + value.slice(end);
          textarea.selectionStart = textarea.selectionEnd = start + text.length;
          this.markdownContent = textarea.value;
          this.emitChange();
        }
      } else {
        // WYSIWYG 模式：插入 Markdown 渲染后的 HTML
        this.insertMarkdownContent(text);
      }
    },
    replaceSourceUploadToken(uploadId, replacement) {
      const token = this.createImageUploadToken(uploadId);
      const textarea = this.$refs.sourceEditor;
      const content = this.markdownContent || '';
      const index = content.indexOf(token);
      if (index === -1) return false;

      const start = textarea ? textarea.selectionStart : 0;
      const end = textarea ? textarea.selectionEnd : 0;
      const tokenEnd = index + token.length;
      const delta = replacement.length - token.length;
      const nextStart = start <= index ? start : (start >= tokenEnd ? start + delta : index + replacement.length);
      const nextEnd = end <= index ? end : (end >= tokenEnd ? end + delta : index + replacement.length);

      this.historyNextMode = 'push';
      this.markdownContent = content.slice(0, index) + replacement + content.slice(tokenEnd);
      this.emitChange();
      this.$nextTick(() => {
        if (!textarea) return;
        if (document.activeElement === textarea) {
          textarea.focus();
        }
        textarea.setSelectionRange(nextStart, nextEnd);
      });
      return true;
    },
    async replaceWysiwygUploadPlaceholder(uploadId, markdown) {
      const editor = this.$refs.editorContent;
      if (!editor) return false;
      const placeholder = editor.querySelector(`.image-upload-placeholder[data-upload-id="${uploadId}"]`);
      if (!placeholder) return false;

      const selection = this.saveSelection() || this.savedSelection;
      const html = markdown ? await renderMarkdown(markdown) : '';
      const wrapper = document.createElement('div');
      wrapper.innerHTML = html;
      const fragment = document.createDocumentFragment();
      while (wrapper.firstChild) {
        fragment.appendChild(wrapper.firstChild);
      }

      if (markdown) {
        placeholder.replaceWith(fragment);
      } else {
        placeholder.remove();
      }

      if (selection) {
        this.savedSelection = this.cloneSelectionSnapshot(selection);
      }
      this.historyNextMode = 'push';
      this.syncToMarkdown({ historyMode: 'push' });
      this.$nextTick(() => {
        if (selection) {
          this.restoreSelection(selection);
        }
        this.updateFormatState();
      });
      return true;
    },
    async resolveImageUpload(uploadId, markdown) {
      if (!uploadId) return false;
      if (this.showSource) {
        return this.replaceSourceUploadToken(uploadId, markdown);
      }

      const replaced = await this.replaceWysiwygUploadPlaceholder(uploadId, markdown);
      if (replaced) return true;
      return this.replaceSourceUploadToken(uploadId, markdown);
    },
    async rejectImageUpload(uploadId) {
      if (!uploadId) return false;
      if (this.showSource) {
        return this.replaceSourceUploadToken(uploadId, '');
      }

      const removed = await this.replaceWysiwygUploadPlaceholder(uploadId, '');
      if (removed) return true;
      return this.replaceSourceUploadToken(uploadId, '');
    },

    // ==================== 表格操作 ====================

    handleContextMenu(e) {
      // 检查点击目标是否在表格内
      let target = e.target;
      let cell = null;
      
      while (target && target !== this.$refs.editorContent) {
        if (target.tagName === 'TD' || target.tagName === 'TH') {
          cell = target;
          break;
        }
        target = target.parentNode;
      }
      
      if (cell) {
        e.preventDefault();
        this.contextMenu.visible = true;
        this.contextMenu.x = e.clientX;
        this.contextMenu.y = e.clientY;
        this.contextMenu.targetCell = cell;
      } else {
        this.closeContextMenu();
      }
    },

    closeContextMenu() {
      this.contextMenu.visible = false;
      this.contextMenu.targetCell = null;
    },

    execTableCommand(command) {
      const cell = this.contextMenu.targetCell;
      if (!cell) return;
      
      const row = cell.parentNode;
      const table = row.closest('table');
      if (!row || !table) return;

      // 恢复焦点
      this.focus();
      
      switch (command) {
        case 'insertRowBefore':
          this.insertRow(table, row, true);
          break;
        case 'insertRowAfter':
          this.insertRow(table, row, false);
          break;
        case 'insertColLeft':
          this.insertColumn(table, cell, true);
          break;
        case 'insertColRight':
          this.insertColumn(table, cell, false);
          break;
        case 'deleteRow':
          this.deleteRow(table, row);
          break;
        case 'deleteCol':
          this.deleteColumn(table, cell);
          break;
        case 'deleteTable':
          this.deleteTable(table);
          break;
      }
      
      this.closeContextMenu();
      this.handleInput(); // 触发更新
    },

    insertRow(table, currentRow, isBefore) {
      const newRow = currentRow.cloneNode(true);
      // 清空新行的内容
      Array.from(newRow.cells).forEach(cell => {
        // 保持原有的 cell 类型（th/td）
        cell.innerHTML = '<br>';
      });
      
      if (isBefore) {
        currentRow.parentNode.insertBefore(newRow, currentRow);
      } else {
        currentRow.parentNode.insertBefore(newRow, currentRow.nextSibling);
      }
    },

    insertColumn(table, currentCell, isBefore) {
      const cellIndex = currentCell.cellIndex;
      const rows = table.rows;
      
      for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        // 找到对应位置的 cell
        const targetCell = row.cells[cellIndex];
        if (!targetCell) continue;

        const newCell = targetCell.cloneNode(false);
        newCell.innerHTML = '<br>'; 
        
        if (isBefore) {
          row.insertBefore(newCell, targetCell);
        } else {
          row.insertBefore(newCell, targetCell.nextSibling);
        }
      }
    },

    deleteRow(table, currentRow) {
      currentRow.remove();
      // 如果表格没有行了，删除表格
      if (table.rows.length === 0) {
        this.deleteTable(table);
      }
    },

    deleteColumn(table, currentCell) {
      const cellIndex = currentCell.cellIndex;
      const rows = Array.from(table.rows);
      
      rows.forEach(row => {
        if (row.cells[cellIndex]) {
          row.deleteCell(cellIndex);
        }
      });
      
      // 如果没有列了，删除表格
      if (table.rows.length > 0 && table.rows[0].cells.length === 0) {
        this.deleteTable(table);
      }
    },

    deleteTable(table) {
      const wrapper = table.closest('.table-wrapper');
      if (wrapper) {
        wrapper.remove();
      } else {
        table.remove();
      }
    }

  }
};
</script>

<style scoped>
.wysiwyg-editor {
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  overflow: visible;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.wysiwyg-editor.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh !important;
  z-index: 2000;
  border: none;
  border-radius: 0;
}

.wysiwyg-editor.dark-mode {
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

.wysiwyg-editor.dark-mode .editor-toolbar {
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

.wysiwyg-editor.dark-mode .toolbar-item {
  color: #a9b7c6;
}

.wysiwyg-editor.dark-mode .toolbar-item:hover {
  background-color: #3e4145;
  color: #fff;
}

.wysiwyg-editor.dark-mode .toolbar-item.active {
  background-color: #214283;
  color: #fff;
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  background-color: #dcdfe6;
  margin: 0 6px;
}

.wysiwyg-editor.dark-mode .toolbar-divider {
  background-color: #4c4d4f;
}

.toolbar-spacer {
  flex: 1;
}

/* 编辑器主体 */
.editor-body {
  flex: 1;
  overflow: auto;
}

/* 编辑内容区域 */
.editor-content {
  min-height: 100%;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  padding: 16px 24px;
  outline: none;
  font-size: 16px;
  line-height: 1.75;
  color: #333;
  box-sizing: border-box;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.editor-content :deep(.image-upload-placeholder) {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  margin: 0 2px;
  border: 1px dashed #c0c4cc;
  border-radius: 999px;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.editor-content:empty::before {
  content: attr(placeholder);
  color: #c0c4cc;
  pointer-events: none;
}

.wysiwyg-editor.dark-mode .editor-content {
  color: #d4d4d4;
}

.wysiwyg-editor.dark-mode .editor-content :deep(.image-upload-placeholder) {
  border-color: #606266;
  background: #2b2b2b;
  color: #a8abb2;
}

.wysiwyg-editor.dark-mode .editor-content:empty::before {
  color: #666;
}

/* 源码编辑区域 */
.source-editor {
  width: 100%;
  height: 100%;
  min-height: 100%;
  min-width: 0;
  max-width: 100%;
  padding: 16px 24px;
  border: none;
  outline: none;
  resize: none;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  background: #f5f7fa;
  color: #333;
}

.wysiwyg-editor.dark-mode .source-editor {
  background: #1e1e1e;
  color: #d4d4d4;
}

/* 
 * 编辑器内容样式：复用 entry-content (markdown-highlight.css) 的样式
 * 只添加 contenteditable 特有的覆盖样式
 */

/* 表格在编辑器中需要居中显示（与文章页一致） */
.editor-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

.editor-content :deep(th),
.editor-content :deep(td) {
  border: 1px solid #dfe2e5;
  padding: 8px 16px;
  text-align: center;
  line-height: 1.5;
}

.editor-content :deep(th) {
  background-color: rgba(0, 0, 0, 0.1);
}

.editor-content :deep(tr:nth-child(2n)) {
  background-color: #f8f8f8;
}

/* 代码块在编辑器中：使用 highlight-wrap 结构，复用全局样式 */
/* 只覆盖必要的编辑相关样式 */
.editor-content :deep(.highlight-wrap pre) {
  margin: 0;  /* 去掉 pre 外边距，由 highlight-wrap 控制 */
}

.editor-content :deep(pre code) {
  white-space: pre-wrap;
  word-wrap: break-word;
}

.editor-content :deep(p),
.editor-content :deep(li),
.editor-content :deep(blockquote),
.editor-content :deep(a),
.editor-content :deep(span),
.editor-content :deep(td),
.editor-content :deep(th) {
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* 让语言标签可编辑时的样式 */
.editor-content :deep(.highlight-toolbar .hl-lang) {
  pointer-events: auto !important; /* 覆盖 markdown-highlight.css 中的 pointer-events: none */
  cursor: text;
  min-width: 40px;
  user-select: text;
}

/* 图片最大宽度 */
.editor-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.editor-content :deep(.mermaid-container) {
    position: relative;
    margin: 20px 0;
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
    overflow-x: auto;
    text-align: center;
    transition: all 0.3s ease;
  }

.editor-content :deep(.mermaid-container svg) {
  max-width: 100%;
  height: auto;
  overflow: visible !important;
}

.editor-content :deep(.mermaid-container svg foreignObject div),
.editor-content :deep(.mermaid-container svg foreignObject span),
.editor-content :deep(.mermaid-container svg foreignObject p) {
  word-break: normal !important;
  overflow-wrap: normal !important;
  white-space: pre !important;
  line-height: 1.2 !important;
  font-size: 14px !important;
}

.editor-content :deep(.mermaid-container svg foreignObject div),
.editor-content :deep(.mermaid-container svg foreignObject span),
.editor-content :deep(.mermaid-container svg foreignObject p) {
  margin: 0 !important;
  padding: 0 !important;
  text-align: center !important;
}

.editor-content :deep(.mermaid-container svg foreignObject) {
  overflow: visible !important;
}

.editor-content :deep(.mermaid-container svg .nodeLabel),
.editor-content :deep(.mermaid-container svg .edgeLabel),
.editor-content :deep(.mermaid-container svg .labelBkg) {
    overflow: visible !important;
  }
  
.wysiwyg-editor.dark-mode .editor-content :deep(.mermaid-container) {
    background: #2d2d2d;
  }

/* 暗色模式表格 */
.wysiwyg-editor.dark-mode .editor-content :deep(table) {
  color: #d4d4d4;
}

.wysiwyg-editor.dark-mode .editor-content :deep(th) {
  background-color: rgba(255, 255, 255, 0.15);
  color: #d4d4d4;
}

.wysiwyg-editor.dark-mode .editor-content :deep(tr:nth-child(2n)) {
  background-color: rgba(255, 255, 255, 0.04);
}

.wysiwyg-editor.dark-mode .editor-content :deep(th),
.wysiwyg-editor.dark-mode .editor-content :deep(td) {
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.wysiwyg-editor.dark-mode .editor-content :deep(tbody > tr:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 暗色模式其他内容 */
.wysiwyg-editor.dark-mode .editor-content :deep(p),
.wysiwyg-editor.dark-mode .editor-content :deep(li),
.wysiwyg-editor.dark-mode .editor-content :deep(span) {
  color: #d4d4d4;
}

.wysiwyg-editor.dark-mode .editor-content :deep(blockquote) {
  color: #b0b0b0;
}

.wysiwyg-editor.dark-mode .editor-content :deep(code:not(.hljs)) {
  background: #3a3a00;
  color: #f0c674;
}

.wysiwyg-editor.dark-mode .editor-content :deep(a) {
  color: #f0a0a0;
}

.wysiwyg-editor.dark-mode .editor-content :deep(ul),
.wysiwyg-editor.dark-mode .editor-content :deep(ol) {
  color: #d4d4d4;
}

.wysiwyg-editor.dark-mode .editor-content :deep(hr) {
  border-color: rgba(252, 98, 93, 0.5);
}

/* 右键菜单 */
.editor-context-menu {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 5px 0;
  min-width: 150px;
}

.menu-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
}

.menu-item i {
  margin-right: 8px;
  font-size: 16px;
}

.menu-item:hover {
  background-color: #ecf5ff;
  color: #409eff;
}

.menu-item.delete:hover {
  background-color: #fef0f0;
  color: #f56c6c;
}

.menu-divider {
  height: 1px;
  background-color: #ebeef5;
  margin: 5px 0;
}

.wysiwyg-editor.dark-mode .editor-context-menu {
  background: #313337;
  border-color: #4c4d4f;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.3);
}

.wysiwyg-editor.dark-mode .menu-item {
  color: #a9b7c6;
}

.wysiwyg-editor.dark-mode .menu-item:hover {
  background-color: #3e4145;
  color: #fff;
}

.wysiwyg-editor.dark-mode .menu-item.delete:hover {
  background-color: #4a3b3b;
  color: #f56c6c;
}

.wysiwyg-editor.dark-mode .menu-divider {
  background-color: #4c4d4f;
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
