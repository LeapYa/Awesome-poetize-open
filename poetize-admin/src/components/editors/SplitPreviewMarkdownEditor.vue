<template>
  <div class="split-preview-editor" :class="{ 'fullscreen': isFullscreen, 'dark-mode': isDarkMode }">
    <!-- 工具栏 -->
    <div class="editor-toolbar">
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
        <el-tooltip content="行内代码 (Ctrl+E)" placement="top" :enterable="false">
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
        <el-tooltip content="链接 (Ctrl+L)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('link')"><i class="el-icon-link"></i></div>
        </el-tooltip>
        <el-tooltip content="图片 (Ctrl+G / 粘贴)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="insertFormat('image')"><i class="el-icon-picture-outline"></i></div>
        </el-tooltip>
        <el-tooltip content="文件 (粘贴)" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerFileUpload"><i class="el-icon-folder-add"></i></div>
        </el-tooltip>
        <el-tooltip content="私有附件" placement="top" :enterable="false">
          <div class="toolbar-item" @click="triggerPrivateFileUpload"><i class="el-icon-lock"></i></div>
        </el-tooltip>
        <el-tooltip content="代码块 (Ctrl+K)" placement="top" :enterable="false">
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
        <el-tooltip :content="showPreview ? '隐藏预览' : '显示预览'" placement="top" :enterable="false">
          <div class="toolbar-item" :class="{ 'active': showPreview }" @click="togglePreview">
            <i class="el-icon-view"></i>
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

    <div class="editor-container">
      <!-- 编辑区域 -->
      <div class="editor-pane" :style="{ width: showPreview ? '50%' : '100%' }">
        <textarea
          ref="textarea"
          class="editor-textarea"
          :value="internalContent"
          :placeholder="placeholder"
          @focus="handleEditorFocus"
          @blur="$emit('blur')"
          @input="handleInput"
          @compositionstart="handleCompositionStart"
          @compositionend="handleCompositionEnd"
          @scroll="handleScroll"
          @keydown="handleKeydown"
          @paste="handlePaste"
        ></textarea>
      </div>
      
      <!-- 预览区域 -->
      <div 
        v-show="showPreview" 
        class="preview-pane"
        ref="preview"
        @click="handlePreviewClick"
      >
        <div class="entry-content" v-html="htmlContent"></div>
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
        <h3>✨ Split 编辑器特性</h3>
        <ul class="help-list">
          <li><strong>分屏预览</strong>：左侧编辑 Markdown 源码，右侧实时预览效果。</li>
          <li><strong>传统体验</strong>：经典的双栏编辑模式，源码与预览实时对照。</li>
          <li><strong>同步滚动</strong>：编辑区和预览区滚动位置自动同步。</li>
        </ul>
        <h3>⌨️ 常用快捷键</h3>
        <ul class="help-list">
          <li><strong>加粗</strong>：<code>Ctrl + B</code></li>
          <li><strong>斜体</strong>：<code>Ctrl + I</code></li>
          <li><strong>删除线</strong>：<code>Ctrl + D</code></li>
          <li><strong>行内代码</strong>：<code>Ctrl + E</code></li>
          <li><strong>引用</strong>：<code>Ctrl + Q</code></li>
          <li><strong>有序列表</strong>：<code>Ctrl + O</code></li>
          <li><strong>无序列表</strong>：<code>Ctrl + U</code></li>
          <li><strong>插入链接</strong>：<code>Ctrl + L</code></li>
          <li><strong>插入代码块</strong>：<code>Ctrl + K</code></li>
          <li><strong>插入图片</strong>：<code>Ctrl + G</code></li>
          <li><strong>保存</strong>：<code>Ctrl + S</code></li>
          <li><strong>撤销 / 重做</strong>：<code>Ctrl + Z</code> / <code>Ctrl + Y</code></li>
          <li><strong>全屏</strong>：<code>F11</code> / <code>Esc</code></li>
        </ul>
        <h3>📝 其他功能</h3>
        <ul class="help-list">
          <li><strong>Markdown 兼容</strong>：支持直接粘贴 Markdown 文本。</li>
          <li><strong>图片上传</strong>：支持截图直接粘贴。</li>
          <li><strong>文件上传</strong>：支持粘贴上传文件。</li>
          <li><strong>登录附件</strong>：私有附件使用前端登录拦截；知道真实静态地址仍可直接访问，适合轻量提示场景。</li>
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
            <tr>
              <td><strong>IR 编辑器</strong></td>
              <td>即时渲染</td>
              <td>源码与渲染自动切换，轻量流畅</td>
            </tr>
            <tr>
              <td><strong>WYSIWYG 编辑器</strong></td>
              <td>所见即所得</td>
              <td>富文本编辑体验，表格右键菜单</td>
            </tr>
            <tr class="current-editor">
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
import { renderMarkdown } from '@/utils/markdownLazyRenderer';
import { loadMermaidResources, normalizeMermaidDiagrams } from '@/utils/resourceLoaders/mermaidLoader';
import { loadEChartsResources } from '@/utils/resourceLoaders/echartsLoader';
import { parseEChartsOption } from '@/utils/echartsOptionParser';
import { downgradeMarkdownHeadings, upgradeMarkdownHeadings } from '@/utils/markdownHeadingUtils';
import { handlePaste as handlePasteUtil } from '@/utils/pasteHandler';
import { initEditorTheme } from '@/utils/useEditorTheme';
import SnapshotHistory from '@/components/editors/core/SnapshotHistory';
// 导入公共编辑器标题样式
import '@/assets/css/editor-heading-styles.css';
import 'katex/dist/katex.min.css';

export default {
  name: 'SplitPreviewMarkdownEditor',
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
    }
  },
  data() {
    return {
      htmlContent: '',
      showPreview: true,
      isFullscreen: false,
      isDarkMode: false,
      renderTimer: null,
      lastRenderTime: 0,        // 上次渲染的时间戳
      maxRenderDelay: 500,      // 最大渲染延迟（ms）- 即使持续输入也要定期刷新
      renderDebounce: 150,      // 防抖时间（ms）
      isComposing: false,       // 是否处于中文/日文等 IME 合成输入中
      originalParent: null,
      originalNextSibling: null,
      themeObserver: null,
      // 编辑器内部显示的内容（升级后的标题）
      internalContent: '',
      // 标记是否是内部更新，防止循环
      isInternalUpdate: false,
      queuedImageUploads: [],
      pendingForceAttachmentUpload: false,
      pendingPrivateAttachmentUpload: false,
      history: null,
      historySuspend: false,
      historyNextMode: null,
      canUndo: false,
      canRedo: false,
      helpDialogVisible: false,
      isBootstrapped: false,
      bootstrapHandle: null
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
      handler(val) {
        // 如果是内部更新导致的 value 变化，不需要再次升级
        if (this.isInternalUpdate) {
          this.isInternalUpdate = false;
          return;
        }
        // 从数据库读取时，升级标题后显示
        this.internalContent = upgradeMarkdownHeadings(val || '');
        if (!this.isBootstrapped) {
          return;
        }
        this.updatePreview(this.internalContent);
        this.$nextTick(() => {
          this.resetHistory();
        });
      },
      immediate: true
    },
    internalContent(newVal, oldVal) {
      if (newVal === oldVal || this.historySuspend || this.isComposing || !this.history) {
        return;
      }

      this.recordHistorySnapshot({
        merge: this.historyNextMode === 'merge'
      });
      this.historyNextMode = null;
    }
  },
  mounted() {
    this.history = new SnapshotHistory({
      limit: 2000,
      mergeWindow: 600,
      isEqual: (a, b) => !!a && !!b && a.content === b.content
    });
    this.syncThemeFromDom();
    this.$root.$on('theme-changed', this.handleThemeChanged);
    this.themeObserver = new MutationObserver(() => {
      this.syncThemeFromDom();
    });
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    this.themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class'] });
    // 加载文章主题到编辑器 CSS 变量
    initEditorTheme();
    this.scheduleBootstrap();
  },
  beforeDestroy() {
    if (this.bootstrapHandle) {
      cancelAnimationFrame(this.bootstrapHandle);
      this.bootstrapHandle = null;
    }
    if (this.renderTimer) clearTimeout(this.renderTimer);
    this.$root.$off('theme-changed', this.handleThemeChanged);
    if (this.themeObserver) {
      this.themeObserver.disconnect();
      this.themeObserver = null;
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
        this.isBootstrapped = true;
        this.updatePreview(this.internalContent);
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
    createImageUploadId() {
      return `img-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    },
    createImageUploadToken(uploadId) {
      return `<!--poetize-image-upload:${uploadId}-->`;
    },
    replaceRangeValue(start, end, replacement, options = {}) {
      const textarea = this.$refs.textarea;
      const text = this.internalContent || '';
      const before = text.slice(0, start);
      const after = text.slice(end);
      const newValue = before + replacement + after;
      const selectionStart = options.selectionStart !== undefined ? options.selectionStart : start + replacement.length;
      const selectionEnd = options.selectionEnd !== undefined ? options.selectionEnd : selectionStart;

      this.historyNextMode = options.historyMode || 'push';
      this.internalContent = newValue;

      const downgradedValue = downgradeMarkdownHeadings(newValue);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedValue);
      this.$emit('change', downgradedValue);
      this.updatePreview(this.internalContent);

      this.$nextTick(() => {
        if (!textarea) return;
        if (options.focus !== false) {
          textarea.focus();
        }
        textarea.setSelectionRange(selectionStart, selectionEnd);
      });
    },
    queueOrStartImageUpload(file, options = {}) {
      if (!file) return null;
      if (this.isComposing) {
        this.queuedImageUploads.push({ file, options });
        return null;
      }

      const textarea = this.$refs.textarea;
      if (!textarea) return null;

      const uploadId = this.createImageUploadId();
      const token = this.createImageUploadToken(uploadId);
      this.replaceRangeValue(textarea.selectionStart, textarea.selectionEnd, token, {
        historyMode: 'push'
      });
      this.uploadFile(file, uploadId, options);
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
    replaceImageUploadToken(uploadId, replacement) {
      const token = this.createImageUploadToken(uploadId);
      const textarea = this.$refs.textarea;
      const text = this.internalContent || '';
      const index = text.indexOf(token);
      if (index === -1) return false;

      const currentStart = textarea ? textarea.selectionStart : 0;
      const currentEnd = textarea ? textarea.selectionEnd : 0;
      const tokenEnd = index + token.length;
      const delta = replacement.length - token.length;
      const nextStart = currentStart <= index ? currentStart : (currentStart >= tokenEnd ? currentStart + delta : index + replacement.length);
      const nextEnd = currentEnd <= index ? currentEnd : (currentEnd >= tokenEnd ? currentEnd + delta : index + replacement.length);

      this.replaceRangeValue(index, tokenEnd, replacement, {
        selectionStart: nextStart,
        selectionEnd: nextEnd,
        focus: !!textarea && document.activeElement === textarea,
        historyMode: 'push'
      });
      return true;
    },
    createHistorySnapshot() {
      const textarea = this.$refs.textarea;
      return {
        content: this.internalContent || '',
        selectionStart: textarea ? textarea.selectionStart : 0,
        selectionEnd: textarea ? textarea.selectionEnd : 0
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
      this.canUndo = !!this.history && this.history.canUndo();
      this.canRedo = !!this.history && this.history.canRedo();
    },
    applyHistorySnapshot(snapshot) {
      if (!snapshot) return;

      this.historySuspend = true;
      this.internalContent = snapshot.content || '';

      const downgradedValue = downgradeMarkdownHeadings(this.internalContent);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedValue);
      this.$emit('change', downgradedValue);
      this.updatePreview(this.internalContent);

      this.$nextTick(() => {
        const textarea = this.$refs.textarea;
        if (textarea) {
          textarea.focus();
          textarea.setSelectionRange(snapshot.selectionStart, snapshot.selectionEnd);
        }
        this.historySuspend = false;
        this.updateHistoryState();
      });
    },
    goPluginManager() {
      const doNavigate = () => {
        if (this.isFullscreen) {
          this.toggleFullscreen();
        }
        this.$router.push({ name: 'pluginManager', query: { type: 'editor' } });
      };

      // 检测内容是否被修改过
      const currentValue = downgradeMarkdownHeadings(this.internalContent);
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
    syncThemeFromDom() {
      const htmlDark = document.documentElement && document.documentElement.classList.contains('dark-mode');
      const bodyDark = document.body && document.body.classList.contains('dark-mode');
      this.isDarkMode = Boolean(htmlDark || bodyDark);
    },
    handleThemeChanged(isDark) {
      this.isDarkMode = Boolean(isDark);
    },
    handleCompositionStart() {
      this.closeFullscreenTooltip();
      this.isComposing = true;
    },
    handleCompositionEnd(e) {
      this.closeFullscreenTooltip();
      // 合成结束时，有些浏览器/输入法不会立刻触发一次 input，这里主动刷新
      this.isComposing = false;
      if (e && e.target) {
        this.handleInput(e);
      } else if (this.$refs.textarea) {
        this.handleInput({ target: this.$refs.textarea });
      }
      this.$nextTick(() => {
        this.flushQueuedImageUploads();
      });
    },
    handleInput(e) {
      this.closeFullscreenTooltip();
      // 合成输入过程中不做预览刷新（避免中间态频繁抖动）；等 compositionend 统一刷新
      if (this.isComposing) {
        // 仍然更新内部内容，保证光标/显示正确
        this.internalContent = e.target.value;
        return;
      }
      this.historyNextMode = 'merge';
      // 更新内部显示内容
      this.internalContent = e.target.value;
      // 降级标题后发送给父组件（保存到数据库）
      const downgradedValue = downgradeMarkdownHeadings(e.target.value);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedValue);
      this.$emit('change', downgradedValue);
      // 更新预览（使用原始内容，即升级后的标题）
      this.updatePreview(e.target.value);
    },

    handleEditorFocus() {
      this.closeFullscreenTooltip();
      this.$emit('focus');
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

    undo() {
      if (!this.history || !this.history.canUndo()) return;
      const snapshot = this.history.undo();
      this.updateHistoryState();
      this.applyHistorySnapshot(snapshot);
    },

    redo() {
      if (!this.history || !this.history.canRedo()) return;
      const snapshot = this.history.redo();
      this.updateHistoryState();
      this.applyHistorySnapshot(snapshot);
    },
    
    updatePreview(content) {
      if (!this.showPreview) return;
      
      const now = Date.now();
      const timeSinceLastRender = now - this.lastRenderTime;
      
      // 清除之前的防抖计时器
      if (this.renderTimer) clearTimeout(this.renderTimer);
      
      // 实际执行渲染的函数
      const doRender = async () => {
        this.lastRenderTime = Date.now();
        // 分屏预览：渲染编辑器显示内容（已升级标题），保持编辑时标题样式一致
        const previewContent = content;
        this.htmlContent = await renderMarkdown(content);
        
        // 渲染图表（Mermaid 和 ECharts）
        this.$nextTick(async () => {
          await this.renderDiagrams(previewContent);
        });
      };
      
      // 如果距离上次渲染已超过最大延迟，立即渲染（保证持续输入时也能定期刷新）
      if (timeSinceLastRender >= this.maxRenderDelay) {
        doRender();
      } else {
        // 否则使用防抖，等待用户停止输入后再渲染
        this.renderTimer = setTimeout(doRender, this.renderDebounce);
      }
    },

    // 渲染图表逻辑
    async renderDiagrams(content) {
      const previewEl = this.$refs.preview;
      if (!previewEl) return;

      // 1. Mermaid 渲染
      if (content.includes('```mermaid')) {
        const hasMermaid = await loadMermaidResources();
        if (hasMermaid && window.mermaid) {
          const mermaidDivs = previewEl.querySelectorAll('.mermaid');
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
                const id = `split-mermaid-${Date.now()}-${i}`;
                const { svg } = await window.mermaid.render(id, code);
                el.classList.add('mermaid-container');
                el.innerHTML = svg;
                normalizeMermaidDiagrams(el);
                el.setAttribute('data-rendered', newEncoded);
              } catch (e) {
              console.error('Mermaid render error:', e);
              el.textContent = code;
              el.removeAttribute('data-rendered');
            }
          }
        }
      }

      // 2. ECharts 渲染
      if (content.includes('```echarts')) {
        const hasECharts = await loadEChartsResources();
        if (hasECharts && window.echarts) {
          try {
            const echartsDivs = previewEl.querySelectorAll('.echarts-render');
            for (let i = 0; i < echartsDivs.length; i++) {
              const el = echartsDivs[i]
              // 避免重复初始化
              if (el.getAttribute('data-processed')) continue;
              
              const jsonContent = el.textContent || el.innerText;
              try {
                const option = await parseEChartsOption(jsonContent);
                const chart = window.echarts.init(el);
                chart.setOption(option);
                el.setAttribute('data-processed', 'true');
                
                // 监听窗口大小变化
                const resizeHandler = () => chart.resize();
                window.addEventListener('resize', resizeHandler);
                // 简单的清理逻辑绑定
                el._resizeHandler = resizeHandler;
              } catch (jsonError) {
                console.error('ECharts JSON parse error:', jsonError);
                el.innerHTML = `<div style="color:red;padding:10px;">ECharts 配置解析失败: ${jsonError.message}</div>`;
              }
            }
          } catch (e) {
            console.error('ECharts render error:', e);
          }
        }
      }
    },
    
    togglePreview() {
      this.showPreview = !this.showPreview;
      if (this.showPreview) {
        this.updatePreview(this.internalContent);
      }
    },
    
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
    
    handleScroll(e) {
      // 简单的同步滚动实现
      if (!this.showPreview) return;
      const textarea = e.target;
      const preview = this.$refs.preview;
      
      const percentage = textarea.scrollTop / (textarea.scrollHeight - textarea.clientHeight);
      preview.scrollTop = percentage * (preview.scrollHeight - preview.clientHeight);
    },

    // 格式化工具方法
    insertFormat(type) {
      const textarea = this.$refs.textarea;
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const text = this.internalContent;
      const selectedText = text.substring(start, end);
      
      let prefix = '';
      let suffix = '';
      let newText = '';
      let cursorOffset = 0;
      
      switch (type) {
        case 'bold':
          prefix = '**';
          suffix = '**';
          newText = selectedText || '粗体文本';
          break;
        case 'italic':
          prefix = '*';
          suffix = '*';
          newText = selectedText || '斜体文本';
          break;
        case 'strike':
          prefix = '~~';
          suffix = '~~';
          newText = selectedText || '删除线文本';
          break;
        case 'inline-code':
          prefix = '`';
          suffix = '`';
          newText = selectedText || '行内代码';
          break;
        case 'heading': {
          // 智能标题处理：自动检测当前行并轮询标题级别 (H1 -> H2 -> H3 -> None)
          // 获取光标所在行的开始和结束位置
          let lineStart = text.lastIndexOf('\n', start - 1) + 1;
          let lineEnd = text.indexOf('\n', end);
          if (lineEnd === -1) lineEnd = text.length;
          
          const currentLine = text.substring(lineStart, lineEnd);
          const headingMatch = currentLine.match(/^(#+)\s/);
          
          if (headingMatch) {
            const level = headingMatch[1].length;
            if (level >= 5) {
              newText = currentLine.substring(level + 1);
              prefix = '';
            } else {
              newText = currentLine.substring(level + 1);
              prefix = '#'.repeat(level + 1) + ' ';
            }
          } else {
            // 无标题 -> H1 (默认一级标题)
            prefix = '# ';
            newText = currentLine || '标题';
          }
          
          // 更新替换范围为整行
          const replacement = prefix + newText;
          const newValue = text.substring(0, lineStart) + replacement + text.substring(lineEnd);
          this.historyNextMode = 'push';
          this.internalContent = newValue;
          
          // 降级标题后发送给父组件
          const downgradedValue = downgradeMarkdownHeadings(newValue);
          this.isInternalUpdate = true;
          this.$emit('input', downgradedValue);
          this.$emit('change', downgradedValue);
          // 点击按钮也应立即刷新预览
          this.updatePreview(this.internalContent);
          
          this.$nextTick(() => {
            textarea.focus();
            // 光标定位到行末
            const newCursorPos = lineStart + replacement.length;
            textarea.setSelectionRange(newCursorPos, newCursorPos);
          });
          return; // 提前返回，跳过默认的替换逻辑
        }
        case 'quote':
          prefix = '> ';
          newText = selectedText || '引用文本';
          break;
        case 'code': {
          const before = text.substring(0, start);
          const after = text.substring(end);
          const needPrefixNewline = before && !before.endsWith('\n');
          const needSuffixNewline = after && !after.startsWith('\n');

          if (selectedText) {
            prefix = `${needPrefixNewline ? '\n' : ''}\`\`\`语言\n`;
            suffix = `\n\`\`\`${needSuffixNewline ? '\n' : ''}`;
            newText = selectedText;
          } else {
            prefix = `${needPrefixNewline ? '\n' : ''}\`\`\`语言\n`;
            newText = '代码';
            suffix = `\n\`\`\`${needSuffixNewline ? '\n' : ''}`;
          }
          break;
        }
        case 'link':
          prefix = '[';
          suffix = '](url)';
          newText = selectedText || '链接文本';
          cursorOffset = -1; // 光标定位到 url 处
          break;
        case 'image':
          // 如果点击图片按钮，触发文件选择
          this.$refs.fileInput.click();
          return;
        case 'ul':
          prefix = '- ';
          newText = selectedText || '列表项';
          break;
        case 'ol':
          prefix = '1. ';
          newText = selectedText || '列表项';
          break;
        case 'todo':
          prefix = '- [ ] ';
          newText = selectedText || '待办事项';
          break;
        case 'table':
          this.insertTableOrAddRow();
          return;
      }
      
      const replacement = prefix + newText + suffix;
      const newValue = text.substring(0, start) + replacement + text.substring(end);
      this.historyNextMode = 'push';
      this.internalContent = newValue;
      
      // 降级标题后发送给父组件
      const downgradedValue = downgradeMarkdownHeadings(newValue);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedValue);
      this.$emit('change', downgradedValue);
      // 点击按钮也应立即刷新预览
      this.updatePreview(this.internalContent);
      
      // 重新定位光标
      this.$nextTick(() => {
        textarea.focus();
        const newCursorPos = start + prefix.length + newText.length + suffix.length + cursorOffset;
        // 如果没有选中文字且有占位符，选中占位符方便修改
        if (!selectedText && newText) {
           textarea.setSelectionRange(start + prefix.length, start + prefix.length + newText.length);
        } else {
           textarea.setSelectionRange(newCursorPos, newCursorPos);
        }
      });
    },

    insertTableOrAddRow() {
      const textarea = this.$refs.textarea;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const text = this.internalContent || '';

      const lines = text.split('\n');
      let currentLineIndex = 0;
      let currentLineStartOffset = 0;
      let offset = 0;
      for (let i = 0; i < lines.length; i++) {
        const lineLen = lines[i].length;
        if (start <= offset + lineLen) {
          currentLineIndex = i;
          currentLineStartOffset = offset;
          break;
        }
        offset += lineLen + 1;
      }

      const isTableLine = (line) => /^\s*\|.*\|\s*$/.test(line || '');
      const isSeparatorLine = (line) => /^\s*\|(\s*:?-{3,}:?\s*\|)+\s*$/.test(line || '');
      const getColumnCount = (line) => {
        const cells = (line || '').trim().split('|').map(s => s.trim()).filter(Boolean);
        return cells.length;
      };

      if (lines.length > 0 && isTableLine(lines[currentLineIndex])) {
        let blockStart = currentLineIndex;
        while (blockStart > 0 && isTableLine(lines[blockStart - 1])) blockStart--;
        let blockEnd = currentLineIndex + 1;
        while (blockEnd < lines.length && isTableLine(lines[blockEnd])) blockEnd++;

        if (blockEnd - blockStart >= 2 && isSeparatorLine(lines[blockStart + 1])) {
          const colCount = getColumnCount(lines[blockStart]) || 3;
          const rowLine = '| ' + Array(colCount).fill('内容').join(' | ') + ' |';
          lines.splice(blockEnd, 0, rowLine);
          const newValue = lines.join('\n');
          this.historyNextMode = 'push';
          this.internalContent = newValue;
          
          // 降级标题后发送给父组件
          const downgradedValue = downgradeMarkdownHeadings(newValue);
          this.isInternalUpdate = true;
          this.$emit('input', downgradedValue);
          this.$emit('change', downgradedValue);
          // 点击按钮也应立即刷新预览
          this.updatePreview(this.internalContent);

          this.$nextTick(() => {
            textarea.focus();
            const rowStart = computeLineStartOffset(lines, blockEnd);
            const cellStart = rowStart + rowLine.indexOf('内容');
            const cellEnd = cellStart + '内容'.length;
            textarea.setSelectionRange(cellStart, cellEnd);
          });
          return;
        }
      }

      const before = text.substring(0, start);
      const after = text.substring(end);
      const needPrefixNewline = before && !before.endsWith('\n');

      const tableText = `| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |`;
      const insertion = `${needPrefixNewline ? '\n' : ''}${tableText}\n`;
      const newValue = before + insertion + after;
      this.historyNextMode = 'push';
      this.internalContent = newValue;
      const insertStart = before.length + (needPrefixNewline ? 1 : 0);
      const firstCellStart = insertStart + tableText.indexOf('列1');
      const firstCellEnd = firstCellStart + '列1'.length;

      // 降级标题后发送给父组件
      const downgradedValue = downgradeMarkdownHeadings(newValue);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedValue);
      this.$emit('change', downgradedValue);
      // 点击按钮也应立即刷新预览
      this.updatePreview(this.internalContent);
      this.$nextTick(() => {
        textarea.focus();
        textarea.setSelectionRange(firstCellStart, firstCellEnd);
      });
    },

    // 快捷键处理
    handleKeydown(e) {
      // Ctrl + Key 组合键
      if (e.ctrlKey || e.metaKey) {
        switch(e.key.toLowerCase()) {
          case 'b':
            e.preventDefault();
            this.insertFormat('bold');
            break;
          case 'i':
            e.preventDefault();
            this.insertFormat('italic');
            break;
          case 'd': // Delete strike
            e.preventDefault();
            this.insertFormat('strike');
            break;
          case 'e': // Inline code
            e.preventDefault();
            this.insertFormat('inline-code');
            break;
          case 'k': // Code
            e.preventDefault();
            this.insertFormat('code');
            break;
          case 'l': // Link
            e.preventDefault();
            this.insertFormat('link');
            break;
          case 'g': // Image (Graph)
            e.preventDefault();
            this.insertFormat('image');
            break;
          case 'q': // Quote
            e.preventDefault();
            this.insertFormat('quote');
            break;
          case 'u': // Unordered list
            e.preventDefault();
            this.insertFormat('ul');
            break;
          case 'o': // Ordered list
            e.preventDefault();
            this.insertFormat('ol');
            break;
          case 's': // Save
            e.preventDefault();
            this.$emit('save'); // 触发父组件保存
            break;
        }
      } else if (e.key === 'Tab') {
        // Tab 缩进
        e.preventDefault();
        const textarea = this.$refs.textarea;
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const text = this.internalContent;
        
        const newValue = text.substring(0, start) + '  ' + text.substring(end);
        this.historyNextMode = 'push';
        this.internalContent = newValue;
        // 降级标题后发送给父组件
        const downgradedValue = downgradeMarkdownHeadings(newValue);
        this.isInternalUpdate = true;
        this.$emit('input', downgradedValue);
        this.$emit('change', downgradedValue);
        this.$nextTick(() => {
          textarea.setSelectionRange(start + 2, start + 2);
        });
      } else if (e.key === 'Escape') {
        if (this.isFullscreen) {
          this.toggleFullscreen();
        }
      } else if (e.key === 'F11') {
        e.preventDefault();
        this.toggleFullscreen();
      }
    },

    // 粘贴处理（支持图片上传 + HTML转Markdown）
    handlePaste(e) {
      handlePasteUtil(e, {
        onFile: (file) => {
          this.queueOrStartImageUpload(file);
        },
        onText: (text) => {
          this.insertValue(text);
        }
      });
    },

    // 导出 Markdown
    exportMarkdown() {
      // 导出降级后的内容（与数据库一致）
      const exportContent = downgradeMarkdownHeadings(this.internalContent);
      const blob = new Blob([exportContent], { type: 'text/markdown' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'article.md';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    },

    // 复制到微信公众号/知乎
    async copyToWeChat() {
      if (!this.htmlContent) {
        this.$message.warning('请先输入内容');
        return;
      }
      
      try {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = this.htmlContent;
        
        // 应用内联样式
        this.inlineStyles(wrapper);
        
        const resultHtml = wrapper.innerHTML;
        const markdownText = this.internalContent || '';
        const plainText = wrapper.innerText || markdownText || '';
        const customMarkdownMime = 'text/x-poetize-markdown';
        
        // 方案一：使用现代 API (navigator.clipboard)
        // 注意：这通常需要 HTTPS 或 localhost 环境
        if (navigator.clipboard && navigator.clipboard.write && typeof ClipboardItem !== 'undefined') {
          try {
            // 尝试同时写入 html 和 text，这样在记事本里也能粘贴出文本
            const items = {};
            items["text/html"] = new Blob([resultHtml], { type: "text/html" });
            items["text/plain"] = new Blob([plainText], { type: "text/plain" });
            items[customMarkdownMime] = new Blob([markdownText], { type: customMarkdownMime });
            items["text/markdown"] = new Blob([markdownText], { type: "text/markdown" });
            
            const data = [new ClipboardItem(items)];
            await navigator.clipboard.write(data);
            this.$message.success('已复制 (支持公众号/知乎及纯文本)');
            return;
          } catch (e) {
            console.warn('navigator.clipboard.write failed, trying fallback...', e);
            // 如果现代 API 失败（可能是浏览器兼容性或权限问题），继续尝试降级方案
          }
        }

        // 方案二：降级方案 (document.execCommand)
        // 创建一个临时的 DOM 元素用于复制
        const tempDiv = document.createElement('div');
        // 必须让元素可见但移出视口，否则 execCommand 可能无效
        tempDiv.style.position = 'fixed';
        tempDiv.style.left = '-9999px';
        tempDiv.style.top = '0';
        tempDiv.innerHTML = resultHtml;
        // 添加一个特殊的属性以保留样式（部分编辑器可能需要）
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

        // 选中内容
        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(tempDiv);
        selection.removeAllRanges();
        selection.addRange(range);
        
        // 执行复制
        const success = document.execCommand('copy');
        
        // 清理
        tempDiv.removeEventListener('copy', handleCopy);
        document.body.removeChild(tempDiv);
        selection.removeAllRanges();
        
        if (success) {
          this.$message.success('已复制 (兼容模式)');
        } else {
          throw new Error('您的浏览器不支持自动复制，请手动选择预览区域内容进行复制');
        }
      } catch (err) {
        console.error('Copy failed:', err);
        this.$message.error('复制失败: ' + (err.message || '未知错误'));
      }
    },

    inlineStyles(root) {
      const styles = {
        'h1': 'font-size: 22px; font-weight: bold; margin: 20px 0 10px; color: #333;',
        'h2': 'font-size: 18px; font-weight: bold; margin: 18px 0 10px; color: #333; border-bottom: 1px solid #eaecef; padding-bottom: 10px;',
        'h3': 'font-size: 16px; font-weight: bold; margin: 16px 0 10px; color: #333;',
        'p': 'font-size: 15px; line-height: 1.75; margin: 10px 0; color: #333; text-align: justify;',
        'img': 'max-width: 100%; height: auto; display: block; margin: 10px auto; border-radius: 4px;',
        'blockquote': 'border-left: 4px solid #42b983; padding: 10px 15px; color: #666; background-color: #f8f9fa; margin: 10px 0;',
        'pre': 'background-color: #282c34; padding: 15px; border-radius: 5px; overflow-x: auto; color: #abb2bf; font-family: Consolas, Monaco, "Andale Mono", "Ubuntu Mono", monospace;',
        'code': 'font-family: Consolas, Monaco, "Andale Mono", "Ubuntu Mono", monospace; background-color: rgba(27,31,35,.05); padding: 0.2em 0.4em; border-radius: 3px;',
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

    // 文件选择处理
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
      // 清空 input 允许重复选择同一文件
      e.target.value = '';
    },

    triggerFileUpload() {
      this.pendingForceAttachmentUpload = true;
      this.pendingPrivateAttachmentUpload = false;
      this.$refs.fileInput?.click();
    },

    triggerPrivateFileUpload() {
      this.pendingForceAttachmentUpload = true;
      this.pendingPrivateAttachmentUpload = true;
      this.$refs.fileInput?.click();
    },

    // 上传文件逻辑
    uploadFile(file, uploadId = null, options = {}) {
      // 触发父组件的上传逻辑，保持与 Vditor 接口一致
      // 父组件通常监听 @image-add(file)
      this.$emit('image-add', uploadId ? {
        file,
        uploadId,
        privateAttachment: !!options.privateAttachment,
        forceAttachment: !!options.forceAttachment
      } : file);
    },

    // 供父组件调用的方法：插入内容（通常是图片上传后的回调）
    insertValue(content) {
      const textarea = this.$refs.textarea;
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const text = this.internalContent;
      
      const newValue = text.substring(0, start) + content + text.substring(end);
      this.historyNextMode = 'push';
      this.internalContent = newValue;
      
      // 降级标题后发送给父组件
      const downgradedValue = downgradeMarkdownHeadings(newValue);
      this.isInternalUpdate = true;
      this.$emit('input', downgradedValue);
      this.$emit('change', downgradedValue);
      // 程序插入内容（如图片回填）也应立即刷新预览
      this.updatePreview(this.internalContent);
      
      this.$nextTick(() => {
        const newCursorPos = start + content.length;
        textarea.focus();
        textarea.setSelectionRange(newCursorPos, newCursorPos);
      });
    },
    resolveImageUpload(uploadId, markdown) {
      return this.replaceImageUploadToken(uploadId, markdown);
    },
    rejectImageUpload(uploadId) {
      return this.replaceImageUploadToken(uploadId, '');
    },

    // 预览区域点击处理（事件委托）
    handlePreviewClick(e) {
      if (e.target.classList.contains('copy-code')) {
        const code = decodeURIComponent(e.target.dataset.code || '');
        if (code) {
          // 使用 navigator.clipboard API
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(code).then(() => {
              this.$message.success('代码已复制');
            }).catch(err => {
              console.error('复制失败:', err);
              this.$message.error('复制失败');
            });
          } else {
            // 降级处理：创建临时 textarea
            const textArea = document.createElement("textarea");
            textArea.value = code;
            document.body.appendChild(textArea);
            textArea.select();
            try {
              document.execCommand('copy');
              this.$message.success('代码已复制');
            } catch (err) {
              console.error('复制失败:', err);
              this.$message.error('复制失败');
            }
            document.body.removeChild(textArea);
          }
        }
      }
    }
  }
};

function computeLineStartOffset(lines, lineIndex) {
  let offset = 0;
  for (let i = 0; i < lineIndex; i++) {
    offset += (lines[i] || '').length + 1;
  }
  return offset;
}
</script>

<style scoped>
.split-preview-editor {
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  height: 500px; /* 默认高度，会被 style 覆盖 */
  transition: all 0.3s;
  
  /* 定义 CSS 变量以支持文章样式 */
  --textColor: #333;
  --articleFontColor: #333;
  --articleGreyFontColor: #666;
  --lightGreen: #3eaf7c;
  --white: #fff;
}

.split-preview-editor.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh !important;
  z-index: 2000;
  border: none;
  border-radius: 0;
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
  /* 
     position: sticky 实现吸顶效果
     top: var(--editor-toolbar-top, -25px) 
     默认 -25px 适配主页面 .content 的 padding (30px) 带来的视觉间隙。
     可通过设置 CSS 变量 --editor-toolbar-top 来适配不同容器（如弹窗）。
  */
  position: sticky;
  top: var(--editor-toolbar-top, -25px);
  z-index: 10;
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

.toolbar-divider {
  width: 1px;
  height: 18px;
  background-color: #dcdfe6;
  margin: 0 6px;
}

.toolbar-spacer {
  flex: 1;
}

/* 编辑器容器 */
.editor-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
}

.editor-pane {
  height: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  transition: width 0.3s;
}

.editor-textarea {
  width: 100%;
  height: 100%;
  min-width: 0;
  max-width: 100%;
  border: none;
  resize: none;
  outline: none;
  padding: 15px;
  font-size: 16px;
  font-family: Consolas, "Courier New", monospace;
  line-height: 1.6;
  color: #333;
  background-color: #fff;
}

/* 预览区域 */
.preview-pane {
  width: 50%;
  height: 100%;
  min-width: 0;
  max-width: 100%;
  overflow-y: auto;
  border-left: 1px solid #dcdfe6;
  padding: 20px;
  background-color: #fcfcfc;
  box-sizing: border-box;
}

.preview-pane :deep(.entry-content),
.preview-pane :deep(.entry-content > *),
.preview-pane :deep(.entry-content p),
.preview-pane :deep(.entry-content li),
.preview-pane :deep(.entry-content blockquote),
.preview-pane :deep(.entry-content a),
.preview-pane :deep(.entry-content td),
.preview-pane :deep(.entry-content th) {
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
  box-sizing: border-box;
}

/* 适配暗色模式 */
.split-preview-editor.dark-mode {
  border-color: #4c4d4f;
  background: #2b2d30;
  
  --textColor: #eee;
  --articleFontColor: #eee;
  --articleGreyFontColor: #aaa;
}

.split-preview-editor.dark-mode .editor-toolbar {
  background-color: #313337;
  border-bottom-color: #4c4d4f;
}

.split-preview-editor.dark-mode .toolbar-item {
  color: #a9b7c6;
}

.split-preview-editor.dark-mode .toolbar-item:hover {
  background-color: #3e4145;
  color: #fff;
}

.split-preview-editor.dark-mode .toolbar-item.active {
  background-color: #214283;
  color: #fff;
}

.split-preview-editor.dark-mode .toolbar-divider {
  background-color: #4c4d4f;
}

.split-preview-editor.dark-mode .editor-textarea {
  background-color: #1e1e1e;
  color: #dcdcdc;
}

.split-preview-editor.dark-mode .preview-pane {
  border-left-color: #4c4d4f;
  background-color: #252526;
}

/* ========== 暗色模式下预览内容的样式适配 ========== */

/* 段落和通用文本 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content),
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content p),
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content li) {
  color: #d4d4d4;
}

/* 引用块 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content blockquote) {
  color: #b0b0b0;
}

/* 行内代码 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content code:not(.hljs)) {
  background: #3a3a00;
  color: #f0c674;
}

/* 链接 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content a) {
  color: #f0a0a0;
}

.split-preview-editor.dark-mode .preview-pane :deep(.entry-content a:hover) {
  color: #ffa500;
}

/* 表格 - 暗色模式适配 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content table) {
  color: #d4d4d4;
}

.split-preview-editor.dark-mode .preview-pane :deep(.entry-content table th) {
  background-color: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.split-preview-editor.dark-mode .preview-pane :deep(.entry-content table td) {
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.split-preview-editor.dark-mode .preview-pane :deep(.entry-content table tr:nth-child(2n)) {
  background-color: rgba(255, 255, 255, 0.04);
}

.split-preview-editor.dark-mode .preview-pane :deep(.entry-content table tbody > tr:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

/* table-wrapper 包裹的表格 */
.split-preview-editor.dark-mode .preview-pane :deep(.table-wrapper > table) {
  color: #d4d4d4;
}

.split-preview-editor.dark-mode .preview-pane :deep(.table-wrapper > table th) {
  background-color: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.split-preview-editor.dark-mode .preview-pane :deep(.table-wrapper > table td) {
  border-color: rgba(255, 255, 255, 0.12);
  color: #d4d4d4;
}

.split-preview-editor.dark-mode .preview-pane :deep(.table-wrapper > table tr:nth-child(2n)) {
  background-color: rgba(255, 255, 255, 0.04);
}

.split-preview-editor.dark-mode .preview-pane :deep(.table-wrapper > table tbody > tr:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 列表标记颜色 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content ul),
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content ol) {
  color: #d4d4d4;
}

/* 水平线 */
.split-preview-editor.dark-mode .preview-pane :deep(.entry-content hr) {
  border-color: rgba(252, 98, 93, 0.5);
}

/* Mermaid 图表 */
.split-preview-editor.dark-mode .preview-pane :deep(.mermaid-container) {
    background: #2d2d2d;
  }

.preview-pane :deep(.mermaid-container) {
    margin: 20px 0;
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
    overflow-x: auto;
    text-align: center;
    transition: all 0.3s ease;
  }
 
.preview-pane :deep(.mermaid-container svg foreignObject div),
.preview-pane :deep(.mermaid-container svg foreignObject span),
.preview-pane :deep(.mermaid-container svg foreignObject p) {
  word-break: normal !important;
  overflow-wrap: normal !important;
  white-space: pre !important;
  line-height: 1.2 !important;
  font-size: 14px !important;
}

.preview-pane :deep(.mermaid-container svg foreignObject div),
.preview-pane :deep(.mermaid-container svg foreignObject span),
.preview-pane :deep(.mermaid-container svg foreignObject p) {
  margin: 0 !important;
  padding: 0 !important;
  text-align: center !important;
}

.preview-pane :deep(.mermaid-container svg) {
    overflow: visible !important;
  }
  
.preview-pane :deep(.mermaid-container svg foreignObject) {
    overflow: visible !important;
  }
  
.preview-pane :deep(.mermaid-container svg .nodeLabel),
.preview-pane :deep(.mermaid-container svg .edgeLabel),
.preview-pane :deep(.mermaid-container svg .labelBkg) {
    overflow: visible !important;
  }
  
  /* 滚动条美化 */
.editor-textarea::-webkit-scrollbar,
.preview-pane::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.editor-textarea::-webkit-scrollbar-thumb,
.preview-pane::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

.editor-textarea::-webkit-scrollbar-track,
.preview-pane::-webkit-scrollbar-track {
  background: transparent;
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
