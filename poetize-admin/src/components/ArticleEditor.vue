<template>
  <div class="article-editor" @keydown.capture="handleKeydownCapture">
    <component
      v-if="currentComponent"
      :is="currentComponent"
      :key="componentRenderKey"
      ref="inner"
      :value="value"
      :placeholder="placeholder"
      :height="height"
      :mode="mode"
      :toolbar-config="toolbarConfig"
      :upload="upload"
      @input="forwardInput"
      @change="forwardChange"
      @ready="forwardReady"
      @image-add="forwardImageAdd"
      @focus="forwardFocus"
      @blur="forwardBlur"
      @save="forwardSave"
    />
  </div>
</template>

<script>
const VditorEditor = () => import('@/components/VditorEditor.vue')
const SplitPreviewMarkdownEditor = () => import('@/components/editors/SplitPreviewMarkdownEditor.vue')
const IRMarkdownEditor = () => import('@/components/editors/IRMarkdownEditor.vue')
const WYSIWYGMarkdownEditor = () => import('@/components/editors/WYSIWYGMarkdownEditor.vue')

const SUPPORTED_EDITOR_KEYS = ['vditor', 'split_preview', 'ir', 'wysiwyg']
const DEFAULT_EDITOR_KEY = 'vditor'

export default {
  name: 'ArticleEditor',
  components: {
    VditorEditor,
    SplitPreviewMarkdownEditor,
    IRMarkdownEditor,
    WYSIWYGMarkdownEditor,
  },
  props: {
    value: {
      type: String,
      default: '',
    },
    placeholder: {
      type: String,
      default: '请输入内容...',
    },
    height: {
      type: [String, Number],
      default: 600,
    },
    mode: {
      type: String,
      default: 'ir',
    },
    toolbarConfig: {
      type: Object,
      default: () => ({}),
    },
    upload: {
      type: Object,
      default: null,
    },
  },
  data() {
    return {
      activeKey: null,
      isResolved: false,
      isDisposed: false,
      componentVersion: 0,
      // 支持的编辑器类型
      // vditor: Vditor 编辑器
      // split_preview: 分屏预览 Markdown 编辑器（左编辑右预览）
      // ir: 自实现即时渲染编辑器
      // wysiwyg: 自实现所见即所得编辑器
    }
  },
  computed: {
    componentRenderKey() {
      return `${this.activeKey || DEFAULT_EDITOR_KEY}-${this.componentVersion}`
    },
    currentComponent() {
      if (!this.isResolved || !this.activeKey) {
        return null
      }
      switch (this.activeKey) {
        case 'split_preview':
          return 'SplitPreviewMarkdownEditor'
        case 'ir':
          return 'IRMarkdownEditor'
        case 'wysiwyg':
          return 'WYSIWYGMarkdownEditor'
        default:
          return 'VditorEditor'
      }
    },
  },
  created() {
    this.resolveInitialEditor()
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', this.handleStorageSync)
      window.addEventListener('editor-plugin-changed', this.handleEditorPluginChanged)
      window.addEventListener('focus', this.handleWindowFocus)
    }
  },
  activated() {
    this.syncEditorFromCache()
  },
  beforeDestroy() {
    this.isDisposed = true
    if (typeof window !== 'undefined') {
      window.removeEventListener('storage', this.handleStorageSync)
      window.removeEventListener('editor-plugin-changed', this.handleEditorPluginChanged)
      window.removeEventListener('focus', this.handleWindowFocus)
    }
  },
  methods: {
    normalizeEditorKey(editorKey) {
      return SUPPORTED_EDITOR_KEYS.includes(editorKey) ? editorKey : null
    },
    applyResolvedEditor(editorKey) {
      const normalizedKey = this.normalizeEditorKey(editorKey) || DEFAULT_EDITOR_KEY
      const changed = this.activeKey !== normalizedKey
      this.activeKey = normalizedKey
      this.isResolved = true
      if (changed) {
        this.componentVersion += 1
      }
      return normalizedKey
    },
    loadActiveFromCache() {
      try {
        return this.normalizeEditorKey(localStorage.getItem('activeEditorPluginKey'))
      } catch (error) {
        return null
      }
    },
    cacheActive(editorKey = this.activeKey) {
      const normalizedKey = this.normalizeEditorKey(editorKey)
      if (!normalizedKey) {
        return
      }
      try {
        localStorage.setItem('activeEditorPluginKey', normalizedKey)
      } catch (error) {}
    },
    syncEditorFromCache() {
      const cachedKey = this.loadActiveFromCache()
      if (cachedKey && cachedKey !== this.activeKey) {
        this.applyResolvedEditor(cachedKey)
      }
    },
    handleStorageSync(event) {
      if (event && event.key && event.key !== 'activeEditorPluginKey') {
        return
      }
      this.syncEditorFromCache()
    },
    handleEditorPluginChanged(event) {
      const editorKey = this.normalizeEditorKey(event && event.detail && event.detail.editorKey)
      if (!editorKey) {
        this.syncEditorFromCache()
        return
      }
      this.cacheActive(editorKey)
      if (editorKey !== this.activeKey) {
        this.applyResolvedEditor(editorKey)
      }
    },
    handleWindowFocus() {
      this.syncEditorFromCache()
    },
    async resolveInitialEditor() {
      const cachedKey = this.loadActiveFromCache()
      if (cachedKey) {
        this.applyResolvedEditor(cachedKey)
        this.fetchActivePlugin({ syncCacheOnly: true, fallbackKey: cachedKey })
        return
      }

      await this.fetchActivePlugin({ fallbackKey: DEFAULT_EDITOR_KEY })
    },
    async fetchActivePlugin(options = {}) {
      const fallbackKey = this.normalizeEditorKey(options.fallbackKey) || DEFAULT_EDITOR_KEY
      const syncCacheOnly = options.syncCacheOnly === true
      const base = this.$constant.baseURL
      try {
        const res = await this.$http.get(base + '/sysPlugin/getActivePlugin', { pluginType: 'editor' }, true)
        const active = res && res.code === 200 ? res.data : null
        const pluginKey = this.normalizeEditorKey(active && active.pluginKey)
        const resolvedKey = pluginKey || fallbackKey

        this.cacheActive(resolvedKey)

        if (!this.isDisposed && !syncCacheOnly) {
          this.applyResolvedEditor(resolvedKey)
        }

        return resolvedKey
      } catch (error) {
        if (!this.isDisposed && !syncCacheOnly) {
          this.applyResolvedEditor(fallbackKey)
        }
        return fallbackKey
      }
    },
    forwardInput(val) {
      this.$emit('input', val)
    },
    forwardChange(val) {
      this.$emit('change', val)
    },
    forwardReady(editor) {
      this.$emit('ready', editor)
    },
    forwardImageAdd(file) {
      this.$emit('image-add', file)
    },
    forwardFocus() {
      this.$emit('focus')
    },
    forwardBlur() {
      this.$emit('blur')
    },
    forwardSave() {
      this.$emit('save')
    },
    handleKeydownCapture(event) {
      const key = String(event.key || '').toLowerCase()
      if (!(event.ctrlKey || event.metaKey)) {
        return
      }
      if (key !== 'z' && key !== 'y') {
        return
      }
      this.$emit('shortcut', {
        key,
        shiftKey: event.shiftKey,
        ctrlKey: event.ctrlKey,
        metaKey: event.metaKey,
        originalEvent: event,
      })
    },
    /**
     * 切换编辑器类型
     * @param {string} editorKey - 编辑器类型：vditor, split_preview, ir, wysiwyg
     */
    switchEditor(editorKey) {
      const normalizedKey = this.normalizeEditorKey(editorKey)
      if (normalizedKey) {
        this.applyResolvedEditor(normalizedKey)
        this.cacheActive(normalizedKey)
      }
    },
    /**
     * 获取当前编辑器类型
     * @returns {string}
     */
    getEditorType() {
      return this.activeKey || DEFAULT_EDITOR_KEY
    },
    insertValue(text) {
      const inner = this.$refs.inner
      if (inner && typeof inner.insertValue === 'function') {
        inner.insertValue(text)
      }
    },
    resolveImageUpload(uploadId, text) {
      const inner = this.$refs.inner
      if (inner && typeof inner.resolveImageUpload === 'function') {
        const result = inner.resolveImageUpload(uploadId, text)
        return result !== false
      }
      if (inner && typeof inner.insertValue === 'function') {
        inner.insertValue(text)
        return true
      }
      return false
    },
    rejectImageUpload(uploadId) {
      const inner = this.$refs.inner
      if (inner && typeof inner.rejectImageUpload === 'function') {
        return inner.rejectImageUpload(uploadId)
      }
      return false
    },
    focus() {
      const inner = this.$refs.inner
      if (inner && typeof inner.focus === 'function') {
        inner.focus()
      }
    },
    blur() {
      const inner = this.$refs.inner
      if (inner && typeof inner.blur === 'function') {
        inner.blur()
      }
    },
    getValue() {
      const inner = this.$refs.inner
      if (inner && typeof inner.getValue === 'function') {
        return inner.getValue()
      }
      return this.value || ''
    },
    setValue(val) {
      const inner = this.$refs.inner
      if (inner && typeof inner.setValue === 'function') {
        inner.setValue(val)
      } else {
        this.$emit('input', val)
        this.$emit('change', val)
      }
    },
  },
}
</script>

<style scoped>
.article-editor {
  width: 100%;
}
</style>
