<template>
  <div class="vditor-editor-isolate">
    <div ref="vditorContainer" class="vditor-wrapper"></div>
  </div>
</template>

<script>
import { downgradeMarkdownHeadings, upgradeMarkdownHeadings } from '@/utils/markdownHeadingUtils'
import { transformAttachmentLinksInElement } from '@/utils/attachmentCard'
import { parseEChartsOption } from '@/utils/echartsOptionParser'
import { initEditorTheme } from '@/utils/useEditorTheme'
// 导入公共编辑器标题样式
import '@/assets/css/editor-heading-styles.css'
// Vditor CSS 动态加载，只在需要时引入
let vditorStyleLoaded = false
let vditorCtorPromise = null

function loadVditorStyle() {
  if (!vditorStyleLoaded && typeof document !== 'undefined') {
    import('vditor/dist/index.css')
    vditorStyleLoaded = true
  }
}

function loadVditorConstructor() {
  if (!vditorCtorPromise) {
    vditorCtorPromise = import('vditor').then((mod) => mod.default || mod)
  }
  return vditorCtorPromise
}

export default {
  name: 'VditorEditor',
  props: {
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '请输入内容...'
    },
    height: {
      type: [String, Number],
      default: 600
    },
    mode: {
      type: String,
      default: 'ir', // ir: 即时渲染, sv: 分屏预览, wysiwyg: 所见即所得
      validator: (value) => ['ir', 'sv', 'wysiwyg'].includes(value)
    },
    toolbarConfig: {
      type: Object,
      default: () => ({})
    },
    upload: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      editor: null,
      isComposing: false,
      isDarkMode: false,
      isInternalUpdate: false, // 标记是否为内部更新，避免循环
      helpDialogVisible: false,
      editorInitHandle: null,
      isDisposed: false
    }
  },
  watch: {
    value(newVal) {
      // 如果是内部更新触发的，不处理
      if (this.isInternalUpdate) {
        return
      }
      
      if (this.editor && !this.isComposing) {
        const currentValue = this.editor.getValue()
        const displayValue = upgradeMarkdownHeadings(newVal || '')
        
        // 只有当内容真正不同时才更新编辑器
        // 这样可以避免用户输入时触发不必要的 setValue
        if (currentValue !== displayValue) {
          this.editor.setValue(displayValue)
        }
      }
    }
  },
  mounted() {
    // 动态加载 Vditor 样式
    loadVditorStyle()
    // 检测初始暗色模式状态
    this.checkDarkMode()
    this.scheduleEditorInit()
    // 监听暗色模式变化
    this.setupThemeListener()
    // 加载文章主题到编辑器 CSS 变量
    initEditorTheme()
  },
  beforeDestroy() {
    this.isDisposed = true
    if (this.editorInitHandle) {
      cancelAnimationFrame(this.editorInitHandle)
      this.editorInitHandle = null
    }
    // 清理全屏监听器
    if (this._fullscreenObserver) {
      this._fullscreenObserver.disconnect()
      this._fullscreenObserver = null
    }
    
    // 清理预览观察器
    if (this._previewObserver) {
      this._previewObserver.disconnect()
      this._previewObserver = null
    }
    
    // 清理 ECharts 渲染计时器
    if (this._echartsRenderTimer) {
      clearTimeout(this._echartsRenderTimer)
      this._echartsRenderTimer = null
    }
    
    // 清理所有 ECharts 实例
    if (this._echartsInstances) {
      this._echartsInstances.forEach(chart => {
        try {
          chart.dispose()
        } catch (e) {
        }
      })
      this._echartsInstances = []
    }
    
    // 移除主题监听
    if (this._themeListener) {
      this.$root.$off('theme-changed', this._themeListener)
      window.removeEventListener('storage', this._storageListener)
    }
    
    if (this.editor) {
      this.editor.destroy()
      this.editor = null
    }
  },
  methods: {
    scheduleEditorInit() {
      const startInit = async () => {
        try {
          const VditorCtor = await loadVditorConstructor()
          if (this.isDisposed || this.editor) return
          this.initEditor(VditorCtor)
        } catch (error) {
          console.error('Failed to load Vditor:', error)
        } finally {
          this.editorInitHandle = null
        }
      }

      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        this.editorInitHandle = window.requestAnimationFrame(() => {
          startInit()
        })
        return
      }

      startInit()
    },
    goPluginManager() {
      const doNavigate = () => {
        // Vditor 全屏模式下，退出全屏
        if (this.editor) {
          const vditorEl = this.editor.vditor?.element
          if (vditorEl && vditorEl.classList.contains('vditor--fullscreen')) {
            this.editor.tip('退出全屏...', 0)
            vditorEl.classList.remove('vditor--fullscreen')
            document.body.style.overflow = ''
          }
        }
        this.$router.push({ name: 'pluginManager', query: { type: 'editor' } })
      }

      // 检测内容是否被修改过
      const currentValue = this.getValue()
      const originalValue = this.value || ''
      if (currentValue !== originalValue) {
        this.$confirm('当前内容尚未保存，切换编辑器后修改将会丢失，确定要离开吗？', '提示', {
          confirmButtonText: '确定离开',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          doNavigate()
        }).catch(() => {})
      } else {
        doNavigate()
      }
    },
    initEditor(VditorCtor) {
      // 检查 window.hljs 是否可用（优先使用项目的 hljs）
      if (typeof window.hljs !== 'undefined') {
      } else {
      }
      
      const toolbar = [
        'emoji',
        'headings',
        'bold',
        'italic',
        'strike',
        'link',
        '|',
        'list',
        'ordered-list',
        'check',
        'outdent',
        'indent',
        '|',
        'quote',
        'line',
        'code',
        'inline-code',
        'insert-before',
        'insert-after',
        '|',
        {
          name: 'upload-file',
          tip: '上传文件',
          icon: '<svg viewBox="0 0 1024 1024"><path d="M682.667 170.667H170.667A85.333 85.333 0 0 0 85.333 256v512a85.333 85.333 0 0 0 85.334 85.333h682.666A85.333 85.333 0 0 0 938.667 768V341.333A85.333 85.333 0 0 0 853.333 256H525.355l-72.021-72.021a45.269 45.269 0 0 0-32-13.312H170.667zM149.333 256a21.333 21.333 0 0 1 21.334-21.333h242.602L499.2 320h354.133a21.333 21.333 0 0 1 21.334 21.333V768a21.333 21.333 0 0 1-21.334 21.333H170.667A21.333 21.333 0 0 1 149.333 768V256z"></path><path d="M512 704a32 32 0 0 1-32-32V448a32 32 0 1 1 64 0v224a32 32 0 0 1-32 32z"></path><path d="M416 576a32 32 0 0 1-22.613-54.613l96-96a32 32 0 0 1 45.226 0l96 96a32 32 0 1 1-45.226 45.226L512 493.227l-73.387 73.386A31.872 31.872 0 0 1 416 576z"></path></svg>',
          click: () => {
            this.triggerFileUpload(false)
          }
        },
        {
          name: 'upload-private-file',
          tip: '上传私有附件',
          icon: '<svg viewBox="0 0 1024 1024"><path d="M768 384h-42.667v-85.333C725.333 180.736 629.931 85.333 512 85.333S298.667 180.736 298.667 298.667V384H256a85.333 85.333 0 0 0-85.333 85.333v384A85.333 85.333 0 0 0 256 938.667h512a85.333 85.333 0 0 0 85.333-85.334v-384A85.333 85.333 0 0 0 768 384zM384 298.667A128 128 0 0 1 512 170.667a128 128 0 0 1 128 128V384H384v-85.333zM768 853.333H256v-384h512v384z"></path><path d="M512 746.667a42.667 42.667 0 0 1-42.667-42.667v-85.333a42.667 42.667 0 1 1 85.334 0V704A42.667 42.667 0 0 1 512 746.667z"></path></svg>',
          click: () => {
            this.triggerFileUpload(true)
          }
        },
        'upload',
        {
          name: 'insert-image-link',
          tip: '插入图片链接',
          icon: '<svg viewBox="0 0 1024 1024"><path d="M959.877 128l0.123 0.123v767.775l-0.123 0.122H64.102l-0.122-0.122V128.123l0.122-0.123h895.775zM960 64H64C28.795 64 0 92.795 0 128v768c0 35.205 28.795 64 64 64h896c35.205 0 64-28.795 64-64V128c0-35.205-28.795-64-64-64zM832 288.01c0 53.023-42.988 96.01-96.01 96.01s-96.01-42.987-96.01-96.01S682.967 192 735.99 192 832 234.988 832 288.01zM896 832H128V704l224.01-384 256 320h64l224.01-192z"></path></svg>',
          click: () => {
            // 直接插入图片Markdown模板
            this.editor.insertValue('![图片描述](url)')
            this.editor.focus()
          }
        },
        'table',
        '|',
        'undo',
        'redo',
        '|',
        'fullscreen',
        'edit-mode',
        {
          name: 'plugin-manager',
          tip: '切换编辑器（插件管理）',
          icon: '<svg t="1769589415141" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="4953" width="200" height="200"><path d="M915.2 1015.04H108.8a97.28 97.28 0 0 1-97.28-96.64V111.36A97.28 97.28 0 0 1 108.8 14.72h806.4a97.28 97.28 0 0 1 97.28 96.64v807.04a97.28 97.28 0 0 1-97.28 96.64zM108.8 80.64a30.72 30.72 0 0 0-30.72 30.72v807.04a30.72 30.72 0 0 0 30.72 30.72h806.4a30.72 30.72 0 0 0 30.72-30.72V111.36a30.72 30.72 0 0 0-30.72-30.72z" fill="#323333" p-id="4954"></path><path d="M323.84 817.28a32.64 32.64 0 0 1-32.64-33.28V245.76a33.28 33.28 0 1 1 64 0v538.24a33.28 33.28 0 0 1-31.36 33.28z" fill="#323333" p-id="4955"></path><path d="M323.84 638.08m-96.64 0a96.64 96.64 0 1 0 193.28 0 96.64 96.64 0 1 0-193.28 0Z" fill="#323333" p-id="4956"></path><path d="M700.16 817.28a33.28 33.28 0 0 1-33.28-33.28V245.76a33.28 33.28 0 1 1 64 0v538.24a32.64 32.64 0 0 1-30.72 33.28z" fill="#323333" p-id="4957"></path><path d="M700.16 391.68m-96.64 0a96.64 96.64 0 1 0 193.28 0 96.64 96.64 0 1 0-193.28 0Z" fill="#323333" p-id="4958"></path></svg>',
          click: () => {
            this.goPluginManager()
          }
        },
        {
          name: 'more',
          toolbar: [
            'both',
            'code-theme',
            'content-theme',
            'export',
            'outline',
            'preview',
            'devtools',
            'info',
          ],
        }
      ]

      const uploadConfig = this.upload || {
        accept: '*/*',
        handler: (files) => {
          // 触发自定义上传事件
          this.$emit('image-add', {
            file: files[0],
            privateAttachment: false,
            forceAttachment: false
          })
          return null
        }
      }

      this.editor = new VditorCtor(this.$refs.vditorContainer, {
        height: typeof this.height === 'number' ? this.height : parseInt(this.height),
        placeholder: this.placeholder,
        mode: this.mode,
        lang: 'zh_CN',
        i18nPath: '/static/vditor/i18n',
        theme: this.isDarkMode ? 'dark' : 'classic', // 编辑器整体主题
        toolbar,
        toolbarConfig: {
          pin: true,
          ...this.toolbarConfig
        },
        cache: {
          enable: false
        },
        preview: {
          markdown: {
            toc: true,
            mark: true,
            footnotes: true,
            autoSpace: true
          },
          math: {
            inlineDigit: true,
            engine: 'KaTeX'
          },
          hljs: {
            enable: true,
            lineNumber: true, // 启用行号
            // 固定使用 GitHub 主题，与文章显示页保持一致
            style: 'github',
          },
          theme: {
            current: this.isDarkMode ? 'dark' : 'light'
          }
        },
        hint: {
          emojiPath: 'https://cdn.jsdelivr.net/npm/vditor@3.9.0/dist/images/emoji',
        },
        upload: uploadConfig,
        // 所见即所得模式下的自定义工具栏配置，返回空数组表示不自定义
        customWysiwygToolbar: () => {
          return []
        },
        input: (value) => {
          if (!this.isComposing) {
            // 用户输入时，降级标题后发送给父组件（保存到数据库）
            const downgradedValue = downgradeMarkdownHeadings(value)
            
            // 设置标志，防止 watch 触发 setValue 导致光标跳转
            this.isInternalUpdate = true
            this.$emit('input', downgradedValue)
            this.$emit('change', downgradedValue)
            
            // 下一个 tick 后重置标志
            this.$nextTick(() => {
              this.isInternalUpdate = false
            })
          }
        },
        focus: () => {
          this.$emit('focus')
        },
        blur: () => {
          this.$emit('blur')
        },
        after: () => {
          if (this.value) {
            // 初始化时也需要升级标题
            const displayValue = upgradeMarkdownHeadings(this.value)
            this.editor.setValue(displayValue)
          }
          // 渲染附件卡片和 ECharts 图表（预览区域）
          this.renderAttachmentCardsInPreview()
          this.renderEChartsInPreview()
          this.$emit('ready', this.editor)
          
          // 监听全屏事件，将编辑器移到 body
          this.setupFullscreenHandler()
          
          // 监听预览内容变化，实时渲染 ECharts
          const previewElement = this.$refs.vditorContainer.querySelector('.vditor-preview')
          if (previewElement) {
            const observer = new MutationObserver(() => {
              // 使用防抖避免频繁渲染
              clearTimeout(this._echartsRenderTimer)
              this._echartsRenderTimer = setTimeout(() => {
                this.renderAttachmentCardsInPreview()
                this.renderEChartsInPreview()
              }, 300)
            })
            observer.observe(previewElement, {
              childList: true,
              subtree: true
            })
            this._previewObserver = observer
          }
        }
      })

      // 监听中文输入
      const editArea = this.$refs.vditorContainer.querySelector('.vditor-ir, .vditor-sv, .vditor-wysiwyg')
      if (editArea) {
        editArea.addEventListener('compositionstart', () => {
          this.isComposing = true
        })
        editArea.addEventListener('compositionend', () => {
          this.isComposing = false
          // 中文输入结束后，降级标题后发送给父组件
          const downgradedValue = downgradeMarkdownHeadings(this.editor.getValue())
          
          // 设置标志，防止 watch 触发 setValue 导致光标跳转
          this.isInternalUpdate = true
          this.$emit('input', downgradedValue)
          this.$emit('change', downgradedValue)
          
          // 下一个 tick 后重置标志
          this.$nextTick(() => {
            this.isInternalUpdate = false
          })
        })
      }
    },
    getValue() {
      // 获取编辑器内容时，降级标题后返回（保存到数据库）
      const editorValue = this.editor ? this.editor.getValue() : ''
      return downgradeMarkdownHeadings(editorValue)
    },
    setValue(value) {
      if (this.editor) {
        // 设置编辑器内容时，升级标题后显示
        const displayValue = upgradeMarkdownHeadings(value || '')
        this.editor.setValue(displayValue)
      }
    },
    insertValue(value) {
      if (this.editor) {
        this.editor.insertValue(value)
      }
    },
    triggerFileUpload(privateAttachment = false) {
      if (typeof document === 'undefined') {
        return
      }
      const input = document.createElement('input')
      input.type = 'file'
      input.style.display = 'none'
      input.addEventListener('change', () => {
        const file = input.files && input.files[0]
        if (file) {
          this.$emit('image-add', {
            file,
            privateAttachment: !!privateAttachment,
            forceAttachment: true
          })
        }
        input.remove()
      })
      document.body.appendChild(input)
      input.click()
    },
    focus() {
      if (this.editor) {
        this.editor.focus()
      }
    },
    blur() {
      if (this.editor) {
        this.editor.blur()
      }
    },
    disabled() {
      if (this.editor) {
        this.editor.disabled()
      }
    },
    enable() {
      if (this.editor) {
        this.editor.enable()
      }
    },
    getHTML() {
      return this.editor ? this.editor.getHTML() : ''
    },
    // 检查暗色模式状态
    checkDarkMode() {
      // 从localStorage读取主题设置
      const theme = localStorage.getItem('theme')
      if (theme === 'dark') {
        this.isDarkMode = true
      } else if (theme === 'light') {
        this.isDarkMode = false
      } else {
        // 如果没有设置，检查body的class
        this.isDarkMode = document.body.classList.contains('dark-mode')
      }
    },
    // 监听主题变化
    setupThemeListener() {
      // 监听全局主题变化事件（由 admin.vue 触发）
      this._themeListener = (isDark) => {
        this.isDarkMode = isDark
        this.switchEditorTheme(isDark)
      }
      this.$root.$on('theme-changed', this._themeListener)
      
      // 监听 storage 事件（跨标签页）
      this._storageListener = (e) => {
        if (e.key === 'theme') {
          const isDark = e.newValue === 'dark'
          this.isDarkMode = isDark
          this.switchEditorTheme(isDark)
        }
      }
      window.addEventListener('storage', this._storageListener)
    },
    renderAttachmentCardsInPreview() {
      const previewElement = this.$refs.vditorContainer?.querySelector('.vditor-preview') ||
                            this.$refs.vditorContainer?.querySelector('.vditor-ir__preview') ||
                            this.$refs.vditorContainer?.querySelector('.vditor-wysiwyg__preview') ||
                            this.$refs.vditorContainer?.querySelector('.vditor-wysiwyg')

      if (previewElement) {
        transformAttachmentLinksInElement(previewElement)
      }
    },
    // 渲染 ECharts 图表（预览区域）
    async renderEChartsInPreview() {
      // 防止重复执行
      if (this._isRenderingECharts) {
        return;
      }
      
      // 确保 ECharts 已加载
      if (typeof echarts === 'undefined') {
        return
      }
      
      // 获取预览容器（支持所有模式）
      const previewElement = this.$refs.vditorContainer.querySelector('.vditor-preview') ||
                            this.$refs.vditorContainer.querySelector('.vditor-ir__preview') ||
                            this.$refs.vditorContainer.querySelector('.vditor-wysiwyg__preview')
      
      if (!previewElement) return
      
      // 查找所有 echarts 代码块
      const echartsBlocks = previewElement.querySelectorAll('pre code.language-echarts')
      
      if (echartsBlocks.length === 0) return
      
      // 初始化实例数组
      if (!this._echartsInstances) {
        this._echartsInstances = []
      }
      
      this._isRenderingECharts = true;
      
      try {
        for (let index = 0; index < echartsBlocks.length; index++) {
          const codeBlock = echartsBlocks[index]
          const pre = codeBlock.parentElement

          if (!pre || !pre.parentNode) continue

          if (
            pre.classList.contains('echarts-rendered') ||
            pre.hasAttribute('data-echarts-rendered')
          ) {
            continue
          }

          const code = codeBlock.textContent
          let config
          try {
            config = await parseEChartsOption(code)
          } catch (parseError) {
            const errorEl = document.createElement('div')
            errorEl.className = 'echarts-error-message vditor-echarts-error'
            errorEl.textContent = `ECharts 配置解析失败：${String(
              parseError?.message || parseError
            )}\n请使用纯 JSON/JSON5（支持注释、单引号、尾逗号、未加引号的 key），暂不支持 function/=>`
            pre.classList.add('echarts-rendered')
            pre.setAttribute('data-echarts-rendered', 'error')
            pre.parentNode.replaceChild(errorEl, pre)
            continue
          }

          pre.classList.add('echarts-rendered')
          pre.setAttribute('data-echarts-rendered', 'true')

          const container = document.createElement('div')
          container.className = 'echarts-container vditor-echarts'
          container.style.width = '100%'
          container.style.height = config.height || '400px'
          container.style.marginBottom = '20px'
          container.setAttribute('data-echarts-config', code)
          pre.parentNode.replaceChild(container, pre)

          const chart = echarts.init(container, this.isDarkMode ? 'dark' : 'light')
          const finalConfig = {
            animation: true,
            animationDuration: 1000,
            animationEasing: 'cubicOut',
            animationDelay: 0,
            backgroundColor: 'transparent',
            ...config
          }
          chart.setOption(finalConfig)

          this._echartsInstances.push(chart)
          container._echartsInstance = chart
        }
      } finally {
        this._isRenderingECharts = false;
      }
    },
    // 切换编辑器主题
    switchEditorTheme(isDark) {
      if (!this.editor) return
      
      try {
        // 切换 ECharts 主题
        if (this._echartsInstances && this._echartsInstances.length > 0) {
          const previewElement = this.$refs.vditorContainer.querySelector('.vditor-preview')
          if (previewElement) {
            const echartsContainers = previewElement.querySelectorAll('.vditor-echarts')
            echartsContainers.forEach((container, index) => {
              const chart = this._echartsInstances[index]
              if (chart) {
                // 销毁旧实例
                chart.dispose()
                
                // 用新主题重新初始化
                const newChart = echarts.init(container, isDark ? 'dark' : 'light')
                
                // 获取原始配置（从容器的 textContent 获取）
                const config = chart.getOption()
                if (config) {
                  newChart.setOption(config, true)
                }
                
                // 更新实例引用
                this._echartsInstances[index] = newChart
              }
            })
          }
        }
        
        // 使用 Vditor 官方的 setTheme 方法
        // 参数：编辑器主题, 内容主题, 代码高亮主题
        const editorTheme = isDark ? 'dark' : 'classic'
        const contentTheme = isDark ? 'dark' : 'light'
        // 固定使用 GitHub 代码高亮主题，与文章显示页保持一致
        const codeTheme = 'github'
        
        if (this.editor.setTheme) {
          this.editor.setTheme(editorTheme, contentTheme, codeTheme)
        } else {
        }
      } catch (error) {
      }
    },
    setupFullscreenHandler() {
      // 保存原始父节点和位置信息
      let originalParent = null
      let originalNextSibling = null
      
      
      // 延迟查找元素，确保 DOM 已渲染
      this.$nextTick(() => {
        setTimeout(() => {
          // 监听全屏变化
          const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
              if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                const vditor = mutation.target
                const isFullscreen = vditor.classList.contains('vditor--fullscreen')
                
                
                if (isFullscreen && !originalParent) {
                  // 进入全屏：移到 body
                  originalParent = this.$refs.vditorContainer.parentNode
                  originalNextSibling = this.$refs.vditorContainer.nextSibling
                  
                  
                  document.body.appendChild(this.$refs.vditorContainer)
                } else if (!isFullscreen && originalParent) {
                  // 退出全屏：移回原位置
                  
                  if (originalNextSibling && originalNextSibling.parentNode === originalParent) {
                    originalParent.insertBefore(this.$refs.vditorContainer, originalNextSibling)
                  } else {
                    originalParent.appendChild(this.$refs.vditorContainer)
                  }
                  
                  
                  originalParent = null
                  originalNextSibling = null
                }
              }
            })
          })
          
          // vditorContainer 本身就是带有 vditor 类的元素
          const vditorElement = this.$refs.vditorContainer
          
          if (vditorElement) {
            observer.observe(vditorElement, {
              attributes: true,
              attributeFilter: ['class']
            })
            
            
            // 保存 observer 以便销毁时清理
            this._fullscreenObserver = observer
          } else {
            console.error('未找到 vditorContainer')
          }
        }, 100)
      })
    }
  }
}
</script>

<style scoped>
/* 隔离容器 - 防止 Vditor 样式泄漏到文章页面 */
.vditor-editor-isolate {
  width: 100%;
  position: relative;
  isolation: isolate; /* CSS 隔离上下文 */
}

/* 全屏模式时的样式（编辑器会被移到 body） */
body > .vditor-editor-isolate {
  z-index: 100 !important;
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
}

/* 覆盖 Vditor 全屏时的 z-index */
::v-deep .vditor--fullscreen {
  z-index: 100 !important;
}

.vditor-wrapper {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

/* 自定义 Vditor 样式 */
::v-deep .vditor {
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 16px !important;
  min-width: 0 !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
}

::v-deep .vditor-toolbar {
  background-color: #fafafa;
  border-bottom: 1px solid #e0e0e0;
}

::v-deep .vditor-toolbar__item:hover {
  background-color: #e8e8e8;
}

::v-deep .echarts-error-message {
  margin: 12px 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: rgba(245, 108, 108, 0.08);
  border: 1px solid rgba(245, 108, 108, 0.25);
  color: #f56c6c;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* ========== 预览区域样式 - 使用项目自定义样式 ========== */

/* 重置 vditor 编辑区域和预览区域的基础字体大小 */
::v-deep .vditor-wysiwyg,
::v-deep .vditor-ir,
::v-deep .vditor-sv {
  font-size: 16px !important;
  min-width: 0 !important;
  max-width: 100% !important;
}

::v-deep .vditor-reset {
  font-size: 16px !important;
  min-width: 0 !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
}

::v-deep .vditor-reset p {
  font-size: 16px !important;
  line-height: 1.75 !important;
  max-width: 100% !important;
  overflow-wrap: anywhere !important;
  word-break: break-word !important;
}

::v-deep .vditor-reset div,
::v-deep .vditor-reset span,
::v-deep .vditor-reset li {
  font-size: 16px !important;
  max-width: 100% !important;
  overflow-wrap: anywhere !important;
  word-break: break-word !important;
}


/* ========== WYSIWYG 模式专用样式覆盖 ========== */
/* 修复所见即所得模式下标题装饰符号与文字间距过大的问题 */
/* Vditor 的 wysiwyg 模式会给标题 before 伪元素添加 margin-left: -29px, color: var(--second-color) 等样式 */
/* 这里重置这些样式，使其与文章页样式保持一致 */
::v-deep .vditor-wysiwyg > .vditor-reset > h1:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: inherit !important;
  font-weight: inherit !important;
}

::v-deep .vditor-wysiwyg > .vditor-reset > h2:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: #ff6d6d !important;
  font-weight: inherit !important;
}

::v-deep .vditor-wysiwyg > .vditor-reset > h3:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: #ff6d6d !important;
  font-weight: inherit !important;
}

::v-deep .vditor-wysiwyg > .vditor-reset > h4:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: inherit !important;
  font-weight: inherit !important;
}

::v-deep .vditor-wysiwyg > .vditor-reset > h5:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: inherit !important;
  font-weight: inherit !important;
}

/* ========== IR 即时渲染模式专用样式覆盖 ========== */
/* 修复即时渲染模式下标题装饰符号与文字间距过大的问题 */
/* Vditor 的 ir 模式同样会给标题 before 伪元素添加 margin-left: -29px, color: var(--second-color) 等样式 */
/* 这里重置这些样式，使其与文章页样式保持一致 */
::v-deep .vditor-ir > .vditor-reset > h1:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: inherit !important;
  font-weight: inherit !important;
}

::v-deep .vditor-ir > .vditor-reset > h2:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: #ff6d6d !important;
  font-weight: inherit !important;
}

::v-deep .vditor-ir > .vditor-reset > h3:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: #ff6d6d !important;
  font-weight: inherit !important;
}

::v-deep .vditor-ir > .vditor-reset > h4:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: inherit !important;
  font-weight: inherit !important;
}

::v-deep .vditor-ir > .vditor-reset > h5:before {
  margin-left: 0 !important;
  float: none !important;
  padding-right: 0 !important;
  color: inherit !important;
  font-weight: inherit !important;
}

/* 代码块样式 */
/* 行内代码 */
::v-deep .vditor-reset code:not(.hljs):not(pre code) {
  background: #fefac7 !important;
  color: #e67474 !important;
  padding: 4px 6px !important;
  border-radius: 5px !important;
  word-break: break-word !important;
}
/* 引用块样式 */
::v-deep .vditor-reset blockquote {
  margin: 0 !important;
  padding: 15px 50px !important;
  position: relative !important;
  word-break: break-word !important;
  text-align: center !important;
  background: transparent !important;
  border-left: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  color: var(--articleFontColor, inherit) !important;
}

::v-deep .vditor-reset blockquote:before {
  content: "\f10d" !important;
  font-size: 1.5rem !important;
  position: absolute !important;
  top: 0 !important;
  left: 5px !important;
  color: orange !important;
  font-family: FontAwesome !important;
}

::v-deep .vditor-reset blockquote:after {
  content: '\f10e' !important;
  font-size: 1.5rem !important;
  position: absolute !important;
  bottom: 0 !important;
  right: 5px !important;
  color: orange !important;
  font-family: FontAwesome !important;
}

/* 列表样式 */
::v-deep .vditor-reset ul {
  list-style: disc !important;
  padding: 0 10px 0 35px !important;
}

::v-deep .vditor-reset ol {
  list-style: decimal !important;
  padding: 0 10px 0 35px !important;
}

::v-deep .vditor-reset ol li,
::v-deep .vditor-reset ul li {
  padding: 8px 0 !important;
}

/* 链接样式 */
::v-deep .vditor-reset a {
  color: #e67474 !important;
}

/* JSX/TSX 特定语法高亮 */
::v-deep .vditor-reset .hljs-tag {
  color: #e06c75 !important;
}

::v-deep .vditor-reset .hljs-tag .hljs-name {
  color: #e06c75 !important;
}

::v-deep .vditor-reset .hljs-tag .hljs-attr {
  color: #d19a66 !important;
}

::v-deep .vditor-reset .hljs-tag .hljs-string {
  color: #98c379 !important;
}

::v-deep .vditor-reset .hljs-tag > .hljs-name {
  color: #61aeee !important;
}

::v-deep .vditor-reset .hljs-template-variable,
::v-deep .vditor-reset .hljs-template-tag {
  color: #c678dd !important;
}

/* 图片样式 */
::v-deep .vditor-reset img {
  max-width: 100% !important;
  border-radius: 5px !important;
}

/* 代码选中样式 - 与正文保持一致 */
::v-deep .vditor-reset .hljs::selection,
::v-deep .vditor-reset .hljs *::selection,
::v-deep .vditor-reset code::selection,
::v-deep .vditor-reset pre::selection,
::v-deep .vditor-reset pre code::selection {
  background: var(--lightGreen) !important;
  color: var(--white) !important;
}

/* ========== Mermaid 图表样式 ========== */
/* Mermaid 图表容器 - 为渲染后的图表预留样式 */
::v-deep .vditor-reset .mermaid-container,
::v-deep .vditor-wysiwyg__preview .mermaid-container,
::v-deep .vditor-ir__preview .mermaid-container {
  position: relative !important;
  margin: 15px 0 !important;
  padding: 15px !important;
  background: #f8f9fa !important;
  border-radius: 8px !important;
  overflow-x: auto !important;
  text-align: center !important;
}

::v-deep .vditor-reset .mermaid-container svg,
::v-deep .vditor-wysiwyg__preview .mermaid-container svg,
::v-deep .vditor-ir__preview .mermaid-container svg {
  max-width: 100% !important;
  height: auto !important;
}

/* 暗色主题适配 */
.dark-mode ::v-deep .vditor {
  border-color: #3a3a3a;
  background-color: #1e1e1e;
}

.dark-mode ::v-deep .vditor-toolbar {
  background-color: #2d2d2d;
  border-bottom-color: #3a3a3a;
}

.dark-mode ::v-deep .vditor-toolbar__item:hover {
  background-color: #3a3a3a;
}

.dark-mode ::v-deep .vditor-reset code:not(pre code) {
  background: #2d2d2d !important;
  color: #e67474 !important;
}

/* 暗色模式下的 Mermaid 图表样式 - 需要更高的优先级 */
.dark-mode ::v-deep .vditor-reset .mermaid-container,
.dark-mode ::v-deep .vditor-wysiwyg__preview .mermaid-container,
.dark-mode ::v-deep .vditor-ir__preview .mermaid-container {
  background: #2d2d2d !important;
}

/* 暗色模式下的 Mermaid pre 容器 - 提高优先级 */
/* 排除 Vditor 的内部标记元素 */
body.dark-mode ::v-deep .vditor-reset pre:not(.vditor-ir__marker):not(.vditor-ir__marker--pre):has(> code.language-mermaid),
body.dark-mode ::v-deep .vditor-reset pre:not(.vditor-ir__marker):not(.vditor-ir__marker--pre):has(> div.language-mermaid),
body.dark-mode ::v-deep pre.vditor-wysiwyg__preview:has(> code.language-mermaid),
body.dark-mode ::v-deep pre.vditor-wysiwyg__preview:has(> div.language-mermaid),
body.dark-mode ::v-deep pre.vditor-ir__preview:has(> code.language-mermaid),
body.dark-mode ::v-deep pre.vditor-ir__preview:has(> div.language-mermaid) {
  background: #2d2d2d !important;
  border: 1px solid #3a3a3a !important;
}

/* 暗色模式下的数学公式 - 保持透明背景 */
body.dark-mode ::v-deep .vditor-reset pre:has(.katex),
body.dark-mode ::v-deep .vditor-reset pre:has(.katex-display),
body.dark-mode ::v-deep .vditor-reset pre:has(.language-math),
body.dark-mode ::v-deep pre.vditor-wysiwyg__preview:has(.katex),
body.dark-mode ::v-deep pre.vditor-wysiwyg__preview:has(.language-math),
body.dark-mode ::v-deep pre.vditor-ir__preview:has(.katex),
body.dark-mode ::v-deep pre.vditor-ir__preview:has(.language-math) {
  background: transparent !important;
  border: none !important;
}

.dark-mode ::v-deep .vditor-reset pre > code.language-mermaid,
.dark-mode ::v-deep pre.vditor-wysiwyg__preview > code.language-mermaid,
.dark-mode ::v-deep pre.vditor-ir__preview > code.language-mermaid {
  color: #e0e0e0 !important;
}

/* 暗色模式下的代码块语言标签 */
.dark-mode ::v-deep pre code[data-rel]:before {
  color: #e0e0e0 !important;
  background: #1d1f21 !important;
}
</style>

<!-- 全局样式：覆盖 Vditor 全屏 z-index -->
<style>
/* 强制覆盖 Vditor 全屏模式的 z-index */
.vditor-wrapper.vditor--fullscreen,
body > .vditor-editor-isolate > .vditor-wrapper.vditor--fullscreen,
.vditor-editor-isolate .vditor-wrapper.vditor--fullscreen {
  z-index: 100 !important;
}

/* 确保全屏容器也是 100 */
body > .vditor-editor-isolate {
  z-index: 100 !important;
}

/* 暗色模式下的 Mermaid 图表样式 - 全局样式，优先级更高 */
/* 排除 Vditor 的内部标记元素 */
body.dark-mode .vditor-reset pre:not(.vditor-ir__marker):not(.vditor-ir__marker--pre):has(> code.language-mermaid),
body.dark-mode .vditor-reset pre:not(.vditor-ir__marker):not(.vditor-ir__marker--pre):has(> div.language-mermaid),
body.dark-mode pre.vditor-wysiwyg__preview:has(> code.language-mermaid),
body.dark-mode pre.vditor-wysiwyg__preview:has(> div.language-mermaid),
body.dark-mode pre.vditor-ir__preview:has(> code.language-mermaid),
body.dark-mode pre.vditor-ir__preview:has(> div.language-mermaid) {
  background: #2d2d2d !important;
  border: 1px solid #3a3a3a !important;
}

body.dark-mode .vditor-reset .mermaid-container,
body.dark-mode pre.vditor-wysiwyg__preview .mermaid-container,
body.dark-mode pre.vditor-ir__preview .mermaid-container {
  background: #2d2d2d !important;
}

body.dark-mode .vditor-reset pre > code.language-mermaid,
body.dark-mode pre.vditor-wysiwyg__preview > code.language-mermaid,
body.dark-mode pre.vditor-ir__preview > code.language-mermaid {
  color: #e0e0e0 !important;
}

/* 全局暗色模式下的代码块语言标签 */
body.dark-mode pre code[data-rel]:before {
  color: #e0e0e0 !important;
  background: #1d1f21 !important;
}

/* 全局数学公式样式 - 保持透明背景 */
body .vditor-reset pre:has(.katex),
body .vditor-reset pre:has(.katex-display),
body .vditor-reset pre:has(.language-math),
body pre.vditor-wysiwyg__preview:has(.katex),
body pre.vditor-wysiwyg__preview:has(.language-math),
body pre.vditor-ir__preview:has(.katex),
body pre.vditor-ir__preview:has(.language-math) {
  background: transparent !important;
  border: none !important;
}

/* 全局暗色模式下的数学公式 */
body.dark-mode .vditor-reset pre:has(.katex),
body.dark-mode .vditor-reset pre:has(.katex-display),
body.dark-mode .vditor-reset pre:has(.language-math),
body.dark-mode pre.vditor-wysiwyg__preview:has(.katex),
body.dark-mode pre.vditor-wysiwyg__preview:has(.language-math),
body.dark-mode pre.vditor-ir__preview:has(.katex),
body.dark-mode pre.vditor-ir__preview:has(.language-math) {
  background: transparent !important;
  border: none !important;
}
</style>
