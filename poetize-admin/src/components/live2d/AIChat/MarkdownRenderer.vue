<template>
  <div class="markdown-renderer-wrapper">
    <!-- 流式模式：直接显示内容，不显示骨架屏 -->
    <!-- 非流式模式：渲染完成后才显示内容 -->
    <div
      v-show="streaming || renderComplete"
      ref="rendererEl"
      class="markdown-renderer"
      v-html="html"
    />

    <!-- 仅在非流式模式且未渲染完成时显示骨架屏 -->
    <div v-if="!streaming && !renderComplete" class="render-placeholder">
      <div class="placeholder-line"></div>
      <div class="placeholder-line"></div>
      <div class="placeholder-line short"></div>
    </div>
  </div>
</template>

<script>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { isMermaidLoaded } from '@/utils/resourceLoaders/resourceLoader'
import { loadMermaidResources } from '@/utils/resourceLoaders/mermaidLoader'
import { loadMarkdownIt, isMarkdownItLoaded } from '@/utils/markdownItLoader'

export default {
  name: 'MarkdownRenderer',

  props: {
    content: {
      type: String,
      required: true,
    },
    streaming: {
      type: Boolean,
      default: false,
    },
    typewriterSpeed: {
      type: Number,
      default: 30, // 每个字符的延迟（毫秒）
    },
    enableTypewriter: {
      type: Boolean,
      default: true, // 是否启用打字机效果
    },
  },

  emits: ['rendered'],

  setup(props, { emit }) {
    const html = ref('')
    const displayedContent = ref('')
    const typewriterTimer = ref(null)
    const currentIndex = ref(0)
    const mermaidLoaded = ref(false)
    const rendererEl = ref(null)
    const renderComplete = ref(false) // 渲染完成状态
    const renderThrottleTimer = ref(null) // 流式渲染节流定时器
    const lastRenderTime = ref(0) // 上次渲染时间
    const pendingContent = ref(null) // 待渲染内容

    /**
     * 等待依赖加载后渲染 Markdown（带重试机制）
     */
    const renderMarkdownWithRetry = async (
      content = props.content,
      retryCount = 0,
      maxRetries = 15
    ) => {
      if (!content) {
        html.value = ''
        return
      }

      // 尝试加载 markdown-it（如果还没加载）
      if (!isMarkdownItLoaded()) {
        const MarkdownIt = await loadMarkdownIt()
        if (!MarkdownIt) {
          if (retryCount < maxRetries) {
            setTimeout(() => {
              renderMarkdownWithRetry(content, retryCount + 1, maxRetries)
            }, 200)
          } else {
            html.value = escapeHtml(content)
            renderComplete.value = true
            nextTick(() => {
              emit('rendered')
            })
          }
          return
        }
      }

      // console.log('markdown-it 已加载，开始渲染')
      renderMarkdown(content)
    }

    /**
     * 预处理Markdown内容，移除意外的行首空格
     */
    const preprocessMarkdown = (content) => {
      // 分割成行
      const lines = content.split('\n')
      const processedLines = []
      let inCodeBlock = false

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]

        // 检测代码块边界（```开头）
        if (line.trim().startsWith('```')) {
          inCodeBlock = !inCodeBlock
          processedLines.push(line)
          continue
        }

        // 如果在代码块内，保持原样
        if (inCodeBlock) {
          processedLines.push(line)
          continue
        }

        // 检查是否是意外的缩进（4个或更多空格开头，但不是列表或引用）
        const leadingSpaces = line.match(/^( +)/)?.[1]?.length || 0
        if (leadingSpaces >= 4) {
          // 检查是否是有意的格式（列表项、引用等）
          const trimmedLine = line.trim()
          const isIntentionalIndent =
            trimmedLine.startsWith('-') || // 列表
            trimmedLine.startsWith('*') || // 列表
            trimmedLine.startsWith('+') || // 列表
            trimmedLine.startsWith('>') || // 引用
            /^\d+\./.test(trimmedLine) // 有序列表

          if (!isIntentionalIndent) {
            // 移除前导空格
            processedLines.push(trimmedLine)
            continue
          }
        }

        // 其他情况保持原样
        processedLines.push(line)
      }

      return processedLines.join('\n')
    }

    /**
     * 检测是否为简单文本（无需复杂渲染）
     */
    const isSimpleText = (content) => {
      if (!content) return true
      // 检测是否包含Markdown特殊语法
      const hasCodeBlock = content.includes('```')
      const hasImage = content.includes('![')
      const hasLink = /\[.*?\]\(.*?\)/.test(content)
      const hasHeading = /^#{1,6}\s/.test(content)
      const hasList = /^[\s]*[-*+]\s/m.test(content)
      const hasOrderedList = /^[\s]*\d+\.\s/m.test(content)
      const hasBlockquote = /^>\s/m.test(content)
      const hasTable = content.includes('|')

      return !(
        hasCodeBlock ||
        hasImage ||
        hasLink ||
        hasHeading ||
        hasList ||
        hasOrderedList ||
        hasBlockquote ||
        hasTable
      )
    }

    /**
     * 渲染Markdown
     */
    const renderMarkdown = (content = props.content) => {
      // 检测是否为简单文本
      const isSimple = isSimpleText(content)

      // 流式响应时，跳过骨架屏（避免闪烁）
      // 简单文本也跳过骨架屏（避免不必要的延迟）
      // 只在首次渲染（html为空）或非流式模式且非简单文本时显示骨架屏
      if (!props.streaming || !html.value || isSimple) {
        if (isSimple) {
          // 简单文本直接标记为完成，不显示骨架屏
          renderComplete.value = true
        } else {
          renderComplete.value = false
        }
      }

      if (!content) {
        html.value = ''
        renderComplete.value = true
        // 即使内容为空也要通知渲染完成
        nextTick(() => {
          emit('rendered')
        })
        return
      }

      try {
        // 检查markdown-it是否已加载
        if (!isMarkdownItLoaded()) {
          html.value = escapeHtml(content)
          renderComplete.value = true
          // 纯文本也要通知渲染完成
          nextTick(() => {
            emit('rendered')
          })
          return
        }

        // 预处理内容，移除意外的行首空格
        const processedContent = preprocessMarkdown(content)

        // 创建markdown-it实例（使用已加载的模块）
        const MarkdownIt = window.markdownit
        const md = new MarkdownIt({
          html: false, // 不允许HTML标签
          linkify: true, // 自动识别链接
          breaks: true, // 转换换行符
          typographer: true, // 智能标点
          highlight: function (str, lang) {
            // 代码高亮函数
            if (lang && typeof window.hljs !== 'undefined') {
              try {
                return window.hljs.highlight(str, {
                  language: lang,
                  ignoreIllegals: true,
                }).value
              } catch (e) {}
            }
            // 如果没有指定语言或highlight.js未加载，返回转义后的代码
            return escapeHtml(str)
          },
        })

        // 渲染
        html.value = md.render(processedContent)

        // 如果有KaTeX，渲染数学公式
        if (typeof window.katex !== 'undefined') {
          renderMath()
        }

        // 渲染Mermaid图表
        nextTick(() => {
          renderMermaid()
          // 渲染完成后再次检查代码高亮
          // 注意：不在这里触发 rendered 事件，而是在 applyCodeHighlight 完成后触发
          applyCodeHighlight()
        })
      } catch (error) {
        console.error('Markdown渲染失败:', error)
        html.value = escapeHtml(content)
        renderComplete.value = true
        // 错误情况也要通知渲染完成
        nextTick(() => {
          emit('rendered')
        })
      }
    }

    /**
     * 应用代码高亮（针对已经渲染的代码块）
     */
    const applyCodeHighlight = () => {
      if (typeof window.hljs === 'undefined') {
        // hljs 未加载，直接触发 rendered 事件
        renderComplete.value = true
        nextTick(() => {
          emit('rendered')
        })
        return
      }

      if (!rendererEl.value) {
        return
      }

      nextTick(() => {
        // 只在当前组件内查找未高亮的代码块
        const blocks = rendererEl.value.querySelectorAll('pre code:not(.hljs)')
        blocks.forEach((block) => {
          try {
            window.hljs.highlightElement(block)
          } catch (e) {}
        })

        // 使用CSS计数器添加行号（替代JS动态插入）
        addLineNumbersWithCSS()

        // 为代码块添加复制按钮（内部有 nextTick）
        addCopyButtons()

        // 等待DOM更新完成
        nextTick(() => {
          // 所有处理完成，显示内容
          renderComplete.value = true
          emit('rendered')
        })
      })
    }

    /**
     * 使用CSS计数器添加行号
     */
    const addLineNumbersWithCSS = () => {
      if (!rendererEl.value) {
        return
      }

      // 只在当前组件内查找代码块
      const codeBlocks = rendererEl.value.querySelectorAll(
        'pre code.hljs:not(.css-line-numbers)'
      )

      codeBlocks.forEach((block) => {
        try {
          // 检查代码内容是否有效
          if (!block.textContent || block.textContent.trim().length === 0) {
            return
          }

          // 标记已处理
          block.classList.add('css-line-numbers')

          // 获取代码内容
          const codeContent = block.innerHTML

          // 按行分割（保留HTML标签）
          let lines = codeContent.split('\n')

          // 移除末尾的空行（markdown-it 渲染后通常会在代码末尾添加换行符）
          if (lines.length > 0 && lines[lines.length - 1].trim() === '') {
            lines.pop()
          }

          // 创建包裹每一行的HTML
          const linesHTML = lines
            .map((line) => {
              // 如果是空行，用一个空格占位以保持高度
              const content = line.trim() === '' ? '&nbsp;' : line
              return `<div class="code-line">${content}</div>`
            })
            .join('')

          // 替换内容
          block.innerHTML = linesHTML

          // 根据总行数动态调整行号宽度
          const totalLines = lines.length
          let lineNumberWidth = '15px' // 默认宽度（1-9行）

          if (totalLines >= 10000) {
            lineNumberWidth = '40px'
          } else if (totalLines >= 1000) {
            lineNumberWidth = '30px'
          } else if (totalLines >= 100) {
            lineNumberWidth = '20px'
          } else if (totalLines >= 10) {
            lineNumberWidth = '15px'
          }

          // 设置CSS变量
          block.style.setProperty('--line-number-width', lineNumberWidth)
        } catch (e) {}
      })
    }

    /**
     * 为代码块添加复制按钮
     */
    const addCopyButtons = () => {
      if (!rendererEl.value) {
        return
      }

      nextTick(() => {
        // 只在当前组件内查找还没有添加复制按钮的pre标签
        const preBlocks = rendererEl.value.querySelectorAll(
          'pre:not(.has-copy-btn)'
        )

        preBlocks.forEach((pre) => {
          // 标记已添加
          pre.classList.add('has-copy-btn')

          // 获取代码内容
          const code = pre.querySelector('code')
          if (!code) return

          // 检测语言 - 支持多种格式
          let language = ''
          if (code.className) {
            // 尝试匹配 language-xxx 格式
            let match = code.className.match(/language-(\w+)/)
            if (match) {
              language = match[1]
            } else {
              // 尝试从 hljs 的 class 中提取语言
              // 例如: "hljs javascript" 或 "javascript hljs"
              const classes = code.className.split(' ')
              for (const cls of classes) {
                if (
                  cls &&
                  cls !== 'hljs' &&
                  cls !== 'has-line-numbers' &&
                  !cls.startsWith('hljs-')
                ) {
                  language = cls
                  break
                }
              }
            }
          }

          // 创建工具栏容器
          const toolbar = document.createElement('div')
          toolbar.className = 'code-toolbar'

          // 添加语言标签
          if (language) {
            const langLabel = document.createElement('span')
            langLabel.className = 'code-language'
            langLabel.textContent = language
            toolbar.appendChild(langLabel)
          }

          // 创建复制按钮
          const copyBtn = document.createElement('button')
          copyBtn.className = 'code-copy-btn'
          copyBtn.setAttribute('aria-label', '复制代码')
          copyBtn.innerHTML = `
            <svg class="copy-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <path d="M833.33 767.96h-91.9c-21.73 0-39.34-17.6-39.34-39.34s17.62-39.34 39.34-39.34h91.9c8.82 0 15.98-7.18 15.98-15.98V193.8c0-8.8-7.17-15.98-15.98-15.98H353.84c-8.82 0-15.98 7.18-15.98 15.98v90.86c0 21.75-17.62 39.34-39.34 39.34s-39.34-17.6-39.34-39.34V193.8c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.21-42.49 94.67-94.68 94.67z" fill="currentColor"></path>
              <path d="M675.96 925.33H196.47c-52.19 0-94.67-42.45-94.67-94.67V351.17c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.22-42.48 94.67-94.67 94.67zM196.47 335.19c-8.82 0-15.98 7.18-15.98 15.98v479.49c0 8.8 7.17 15.98 15.98 15.98h479.49c8.82 0 15.98-7.18 15.98-15.98V351.17c0-8.8-7.17-15.98-15.98-15.98H196.47z" fill="currentColor"></path>
            </svg>
            <span class="copy-text">复制</span>
          `

          // 复制功能
          copyBtn.addEventListener('click', async () => {
            // 获取代码文本
            let codeText = ''
            const codeLines = code.querySelectorAll('.code-line')
            if (codeLines.length > 0) {
              // 使用CSS计数器结构，只提取代码内容
              codeText = Array.from(codeLines)
                .map((line) => line.textContent || '')
                .join('\n')
            } else {
              // 兼容旧结构或无行号
              codeText = code.textContent || ''
            }

            try {
              await navigator.clipboard.writeText(codeText)

              // 更新按钮状态
              copyBtn.classList.add('copied')
              copyBtn.innerHTML = `
                <svg class="copy-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                  <path d="M406.664 621.983l-124.105-124.105c-12.498-12.498-32.757-12.498-45.255 0s-12.498 32.757 0 45.255l146.632 146.632c6.249 6.249 14.437 9.373 22.628 9.373s16.379-3.124 22.628-9.373l366.581-366.58c12.498-12.498 12.498-32.757 0-45.255s-32.757-12.498-45.255 0L406.664 621.983z" fill="currentColor"></path>
                </svg>
                <span class="copy-text">已复制</span>
              `

              // 2秒后恢复
              setTimeout(() => {
                copyBtn.classList.remove('copied')
                copyBtn.innerHTML = `
                  <svg class="copy-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                    <path d="M833.33 767.96h-91.9c-21.73 0-39.34-17.6-39.34-39.34s17.62-39.34 39.34-39.34h91.9c8.82 0 15.98-7.18 15.98-15.98V193.8c0-8.8-7.17-15.98-15.98-15.98H353.84c-8.82 0-15.98 7.18-15.98 15.98v90.86c0 21.75-17.62 39.34-39.34 39.34s-39.34-17.6-39.34-39.34V193.8c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.21-42.49 94.67-94.68 94.67z" fill="currentColor"></path>
                    <path d="M675.96 925.33H196.47c-52.19 0-94.67-42.45-94.67-94.67V351.17c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.22-42.48 94.67-94.67 94.67zM196.47 335.19c-8.82 0-15.98 7.18-15.98 15.98v479.49c0 8.8 7.17 15.98 15.98 15.98h479.49c8.82 0 15.98-7.18 15.98-15.98V351.17c0-8.8-7.17-15.98-15.98-15.98H196.47z" fill="currentColor"></path>
                  </svg>
                  <span class="copy-text">复制</span>
                `
              }, 2000)
            } catch (err) {
              console.error('复制失败:', err)
              // 降级方案
              try {
                const textarea = document.createElement('textarea')
                textarea.value = codeText
                textarea.style.position = 'fixed'
                textarea.style.opacity = '0'
                document.body.appendChild(textarea)
                textarea.select()
                document.execCommand('copy')
                document.body.removeChild(textarea)

                // 显示成功状态
                copyBtn.classList.add('copied')
                copyBtn.innerHTML = `
                  <svg class="copy-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                    <path d="M406.664 621.983l-124.105-124.105c-12.498-12.498-32.757-12.498-45.255 0s-12.498 32.757 0 45.255l146.632 146.632c6.249 6.249 14.437 9.373 22.628 9.373s16.379-3.124 22.628-9.373l366.581-366.58c12.498-12.498 12.498-32.757 0-45.255s-32.757-12.498-45.255 0L406.664 621.983z" fill="currentColor"></path>
                  </svg>
                  <span class="copy-text">已复制</span>
                `
                setTimeout(() => {
                  copyBtn.classList.remove('copied')
                  copyBtn.innerHTML = `
                    <svg class="copy-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                      <path d="M833.33 767.96h-91.9c-21.73 0-39.34-17.6-39.34-39.34s17.62-39.34 39.34-39.34h91.9c8.82 0 15.98-7.18 15.98-15.98V193.8c0-8.8-7.17-15.98-15.98-15.98H353.84c-8.82 0-15.98 7.18-15.98 15.98v90.86c0 21.75-17.62 39.34-39.34 39.34s-39.34-17.6-39.34-39.34V193.8c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.21-42.49 94.67-94.68 94.67z" fill="currentColor"></path>
                      <path d="M675.96 925.33H196.47c-52.19 0-94.67-42.45-94.67-94.67V351.17c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.22-42.48 94.67-94.67 94.67zM196.47 335.19c-8.82 0-15.98 7.18-15.98 15.98v479.49c0 8.8 7.17 15.98 15.98 15.98h479.49c8.82 0 15.98-7.18 15.98-15.98V351.17c0-8.8-7.17-15.98-15.98-15.98H196.47z" fill="currentColor"></path>
                    </svg>
                    <span class="copy-text">复制</span>
                  `
                }, 2000)
              } catch (e) {
                console.error('降级复制也失败:', e)
              }
            }
          })

          toolbar.appendChild(copyBtn)

          // 将工具栏插入到pre的开头
          pre.insertBefore(toolbar, pre.firstChild)
        })
      })
    }

    /**
     * 打字机效果
     */
    const startTypewriter = () => {
      // 清除之前的定时器
      stopTypewriter()

      // 重置状态
      currentIndex.value = 0
      displayedContent.value = ''

      const fullContent = props.content
      if (!fullContent) return

      // 立即显示第一个字符，避免延迟感
      if (fullContent.length > 0) {
        currentIndex.value = 1
        displayedContent.value = fullContent[0]
        renderMarkdown(displayedContent.value)
      }

      // 逐字显示
      typewriterTimer.value = setInterval(() => {
        if (currentIndex.value < fullContent.length) {
          currentIndex.value++
          displayedContent.value = fullContent.substring(0, currentIndex.value)
          renderMarkdown(displayedContent.value)
        } else {
          stopTypewriter()
        }
      }, props.typewriterSpeed)
    }

    /**
     * 停止打字机
     */
    const stopTypewriter = () => {
      if (typewriterTimer.value) {
        clearInterval(typewriterTimer.value)
        typewriterTimer.value = null
      }
    }

    /**
     * 立即显示全部内容
     */
    const showFullContent = () => {
      stopTypewriter()
      displayedContent.value = props.content
      renderMarkdown(props.content)
    }

    /**
     * 渲染数学公式
     */
    const renderMath = () => {
      // KaTeX渲染逻辑（简化版）
      // 实际项目中可以使用markdown-it-katex插件
      // console.log('KaTeX可用，可渲染数学公式')
    }

    /**
     * 转义HTML
     */
    const escapeHtml = (text) => {
      const div = document.createElement('div')
      div.textContent = text
      return div.innerHTML.replace(/\n/g, '<br>')
    }

    /**
     * 渲染Mermaid图表
     */
    const renderMermaid = async () => {
      if (!mermaidLoaded.value || !isMermaidLoaded()) {
        return
      }

      if (!rendererEl.value) {
        return
      }

      // 只在当前组件内查找mermaid代码块
      const mermaidBlocks = rendererEl.value.querySelectorAll(
        'code.language-mermaid'
      )

      if (mermaidBlocks.length === 0) return

      try {
        for (let i = 0; i < mermaidBlocks.length; i++) {
          const block = mermaidBlocks[i]
          const pre = block.parentElement

          // 跳过已经渲染过的
          if (pre.classList.contains('mermaid-rendered')) {
            continue
          }

          const code = block.textContent
          const id = `mermaid-${Date.now()}-${i}`

          // 检查父节点是否存在
          if (!pre.parentNode) {
            continue
          }

          // 创建容器
          const container = document.createElement('div')
          container.className = 'mermaid-container'

          // 渲染图表
          const { svg } = await window.mermaid.render(id, code)
          container.innerHTML = svg

          // 创建放大/缩小按钮
          const zoomButton = document.createElement('button')
          zoomButton.className = 'mermaid-zoom-btn'
          zoomButton.setAttribute('aria-label', '放大图表')
          zoomButton.innerHTML = `
            <svg class="zoom-icon zoom-in-icon" viewBox="0 0 1024 1024" width="20" height="20">
              <path d="M840.824471 180.766118l-178.115765 22.106353a7.469176 7.469176 0 0 0-4.397177 12.709647l51.501177 51.501176-144.504471 144.444235a7.529412 7.529412 0 0 0 0 10.661647l42.465883 42.465883a7.529412 7.529412 0 0 0 10.661647 0l144.564706-144.564706 51.440941 51.440941c4.457412 4.457412 11.986824 1.807059 12.709647-4.397176l22.046117-177.995294a7.408941 7.408941 0 0 0-8.432941-8.372706z m-412.611765 378.578823a7.529412 7.529412 0 0 0-10.661647 0l-144.444235 144.564706-51.501177-51.501176a7.469176 7.469176 0 0 0-12.649412 4.397176L186.729412 834.861176a7.529412 7.529412 0 0 0 8.372706 8.372706l178.055529-22.106353a7.469176 7.469176 0 0 0 4.457412-12.709647l-51.501177-51.501176 144.564706-144.564706a7.529412 7.529412 0 0 0 0-10.601412l-42.526117-42.345412z" fill="currentColor"></path>
            </svg>
            <svg class="zoom-icon zoom-out-icon" style="display: none;" viewBox="0 0 1024 1024" width="20" height="20">
              <path d="M851.2 214.186667l-41.386667-41.386667a7.381333 7.381333 0 0 0-10.368 0L654.933333 317.397333l-50.176-50.176a7.253333 7.253333 0 0 0-12.373333 4.266667l-21.589333 173.525333a7.338667 7.338667 0 0 0 8.192 8.149334l173.568-21.546667c6.058667-0.725333 8.533333-8.106667 4.309333-12.373333L706.688 369.066667l144.597333-144.64a7.338667 7.338667 0 0 0-0.085333-10.24z m-406.186667 356.608l-173.568 21.589333a7.338667 7.338667 0 0 0-4.309333 12.373333l50.176 50.176-144.512 144.512a7.381333 7.381333 0 0 0 0 10.368l41.386667 41.386667a7.381333 7.381333 0 0 0 10.368 0l144.597333-144.64 50.176 50.218667a7.253333 7.253333 0 0 0 12.373333-4.309334l21.461334-173.482666a7.253333 7.253333 0 0 0-8.106667-8.192z" fill="currentColor"></path>
            </svg>
          `

          // 添加点击事件处理
          zoomButton.addEventListener('click', (e) => {
            e.stopPropagation()
            toggleMermaidZoom(container, zoomButton)
          })

          // 将按钮添加到容器
          container.appendChild(zoomButton)

          // 替换代码块
          pre.parentNode.replaceChild(container, pre)
        }
      } catch (error) {
        console.error('Mermaid渲染失败:', error)
      }
    }

    /**
     * 切换Mermaid图表的放大/缩小状态
     */
    const toggleMermaidZoom = (container, button) => {
      // 检查是否已经有放大层
      let overlay = document.getElementById('mermaid-zoom-overlay')

      if (overlay) {
        // 关闭放大视图
        overlay.style.transition = 'opacity 0.3s ease'
        overlay.style.opacity = '0'
        setTimeout(() => {
          if (overlay && overlay.parentNode) {
            overlay.parentNode.removeChild(overlay)
          }
        }, 300)
        document.body.style.overflow = ''
        return
      }

      // 创建放大层
      overlay = document.createElement('div')
      overlay.id = 'mermaid-zoom-overlay'
      overlay.className = 'mermaid-zoom-overlay'

      // 获取SVG内容
      const svg = container.querySelector('svg')
      if (!svg) return

      // 创建内容容器
      const content = document.createElement('div')
      content.className = 'mermaid-zoom-content'

      // 直接复制HTML内容
      content.innerHTML = svg.outerHTML

      // 获取插入的SVG元素并设置样式
      const insertedSvg = content.querySelector('svg')
      if (insertedSvg) {
        const viewBox = insertedSvg.getAttribute('viewBox')
        insertedSvg.removeAttribute('style')

        if (viewBox) {
          const [x, y, width, height] = viewBox.split(' ').map(Number)
          const aspectRatio = width / height
          insertedSvg.setAttribute('width', '800')
          insertedSvg.setAttribute('height', `${800 / aspectRatio}`)
        } else {
          insertedSvg.setAttribute('width', '800')
          insertedSvg.setAttribute('height', '600')
        }

        insertedSvg.style.display = 'block'
        insertedSvg.style.maxWidth = '100%'
        insertedSvg.style.maxHeight = '100%'
        insertedSvg.style.width = 'auto'
        insertedSvg.style.height = 'auto'
        insertedSvg.style.margin = '0 auto'
      }

      // 创建关闭按钮
      const closeBtn = document.createElement('button')
      closeBtn.className = 'mermaid-zoom-close'
      closeBtn.setAttribute('aria-label', '关闭')
      closeBtn.innerHTML = `
        <svg viewBox="0 0 1024 1024" width="24" height="24">
          <path d="M557.312 513.248l265.28-263.904c12.544-12.48 12.608-32.704 0.128-45.248-12.512-12.576-32.704-12.608-45.248-0.128L512.128 467.904 246.72 204.096c-12.48-12.544-32.704-12.608-45.248-0.128-12.576 12.512-12.608 32.704-0.128 45.248l265.344 263.84-265.28 263.872c-12.544 12.48-12.608 32.704-0.128 45.248 6.24 6.272 14.464 9.44 22.688 9.44 8.16 0 16.32-3.104 22.56-9.312l265.344-263.872 265.376 263.904c6.272 6.272 14.464 9.408 22.688 9.408 8.16 0 16.32-3.104 22.56-9.312 12.544-12.48 12.608-32.704 0.128-45.248L557.312 513.248z" fill="currentColor"></path>
        </svg>
      `

      // 添加元素到overlay
      overlay.appendChild(content)
      overlay.appendChild(closeBtn)

      // 添加到body
      document.body.appendChild(overlay)

      // 淡入效果
      overlay.style.opacity = '0'
      setTimeout(() => {
        overlay.style.opacity = '1'
      }, 10)

      // 禁止body滚动
      document.body.style.overflow = 'hidden'

      // 点击overlay背景或关闭按钮关闭
      const closeOverlay = () => {
        overlay.style.transition = 'opacity 0.3s ease'
        overlay.style.opacity = '0'
        setTimeout(() => {
          if (overlay && overlay.parentNode) {
            overlay.parentNode.removeChild(overlay)
          }
        }, 300)
        document.body.style.overflow = ''
      }

      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
          closeOverlay()
        }
      })

      closeBtn.addEventListener('click', closeOverlay)
    }

    // 监听内容变化
    watch(
      () => props.content,
      (newVal, oldVal) => {
        if (!newVal) {
          html.value = ''
          return
        }

        // 如果是streaming模式
        if (props.streaming) {
          // 流式响应时使用节流渲染（减少重绘次数，避免闪烁）
          const now = Date.now()
          const throttleTime = 150 // 每 150ms 最多渲染一次

          // 保存待渲染的内容
          pendingContent.value = newVal

          if (now - lastRenderTime.value >= throttleTime) {
            // 距离上次渲染超过节流时间，立即渲染
            lastRenderTime.value = now
            renderMarkdownWithRetry(newVal)
            pendingContent.value = null
          } else {
            // 在节流时间内，延迟渲染
            if (renderThrottleTimer.value) {
              clearTimeout(renderThrottleTimer.value)
            }
            renderThrottleTimer.value = setTimeout(() => {
              if (pendingContent.value) {
                lastRenderTime.value = Date.now()
                renderMarkdownWithRetry(pendingContent.value)
                pendingContent.value = null
              }
            }, throttleTime - (now - lastRenderTime.value))
          }
        } else {
          // 非streaming模式
          // 如果启用打字机且是真正的新消息（有旧值的变化）
          if (
            props.enableTypewriter &&
            oldVal &&
            oldVal !== '' &&
            newVal !== oldVal
          ) {
            // 内容更新，使用打字机效果
            startTypewriter()
          } else {
            // 首次渲染或禁用打字机，直接渲染（使用带重试的版本）
            renderMarkdownWithRetry(newVal)
          }
        }
      },
      { immediate: true }
    )

    // 监听streaming状态变化
    watch(
      () => props.streaming,
      (isStreaming, wasStreaming) => {
        // 从streaming变为非streaming，需要重新渲染以添加行号等增强功能
        if (!isStreaming && wasStreaming && props.content) {
          // 清除节流定时器
          if (renderThrottleTimer.value) {
            clearTimeout(renderThrottleTimer.value)
            renderThrottleTimer.value = null
          }

          // 清除所有代码块的CSS行号标记，以便重新添加
          if (rendererEl.value) {
            const codeBlocks = rendererEl.value.querySelectorAll(
              'pre code.css-line-numbers'
            )
            codeBlocks.forEach((block) => {
              block.classList.remove('css-line-numbers')
            })
          }

          // 流式结束，渲染最终完整内容
          nextTick(() => {
            renderMarkdownWithRetry(props.content)
          })
        }
      }
    )

    // 挂载时加载Mermaid并渲染
    onMounted(async () => {
      // 加载Mermaid资源
      const loaded = await loadMermaidResources()
      if (loaded) {
        mermaidLoaded.value = true
      }

      // 使用带重试的版本确保依赖加载完成
      renderMarkdownWithRetry()
    })

    // 组件卸载时清理
    onUnmounted(() => {
      stopTypewriter()
      // 清理渲染节流定时器
      if (renderThrottleTimer.value) {
        clearTimeout(renderThrottleTimer.value)
      }
    })

    return {
      html,
      showFullContent,
      rendererEl,
      renderComplete,
    }
  },
}
</script>

<style>
.markdown-renderer-wrapper {
  position: relative;
  min-height: 20px;
}
.render-placeholder {
  padding: 10px 0;
  animation: pulse 1.5s ease-in-out infinite;
}
.placeholder-line {
  height: 14px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: loading 1.5s ease-in-out infinite;
  border-radius: 4px;
  margin-bottom: 8px;
}
.placeholder-line.short {
  width: 60%;
}
@keyframes loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}
.markdown-renderer {
  line-height: 1.6;
  word-wrap: break-word;
}
.markdown-renderer p {
  margin: 0 0 10px 0;
}
.markdown-renderer p:last-child {
  margin-bottom: 0;
}
.markdown-renderer h1,
.markdown-renderer h2,
.markdown-renderer h3,
.markdown-renderer h4,
.markdown-renderer h5,
.markdown-renderer h6 {
  margin: 15px 0 10px 0;
  font-weight: bold;
  line-height: 1.3;
}
.markdown-renderer h1 {
  font-size: 1.5em;
}
.markdown-renderer h2 {
  font-size: 1.3em;
}
.markdown-renderer h3 {
  font-size: 1.1em;
}
.markdown-renderer a {
  color: var(--link-color, #667eea);
  text-decoration: none;
  border-bottom: 1px solid var(--link-color, #667eea);
}
.markdown-renderer a:hover {
  opacity: 0.8;
}
.markdown-renderer code {
  padding: 2px 6px;
  background: rgba(102, 126, 234, 0.1);
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.9em;
  color: #e83e8c;
}
.markdown-renderer pre {
  position: relative;
  padding: 0 0 12px 0;
  background: #282c34;
  border-radius: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  margin: 10px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.markdown-renderer pre code {
  background: none;
  color: #f8f8f2;
  padding: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  display: block;
  overflow-x: visible;
  width: max-content;
  min-width: 100%;
  box-sizing: border-box;
}
.markdown-renderer pre code.hljs {
  background: transparent;
}
.markdown-renderer .code-toolbar {
  position: sticky;
  top: 0;
  left: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px 8px 0 0;
  margin: 0 0 8px 0;
  z-index: 1;
}
.markdown-renderer .code-toolbar:before {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: #fc625d;
  width: 12px;
  height: 12px;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  box-shadow: 20px 0 #fdbc40, 40px 0 #35cd4b;
  z-index: 2;
}
.markdown-renderer .code-language {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  font-weight: 700;
  user-select: none;
  text-align: center;
  font-family: 'Ubuntu', 'Consolas', monospace;
}
.markdown-renderer .code-copy-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
  z-index: 3;
}
.markdown-renderer .code-copy-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
  transform: translateY(-50%) translateY(-1px);
}
.markdown-renderer .code-copy-btn:active {
  transform: translateY(-50%);
}
.markdown-renderer .code-copy-btn.copied {
  background: rgba(76, 175, 80, 0.3);
  border-color: rgba(76, 175, 80, 0.5);
  color: #4caf50;
}
.markdown-renderer .code-copy-btn .copy-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.markdown-renderer .code-copy-btn .copy-text {
  font-weight: 500;
  white-space: nowrap;
}
.markdown-renderer pre code.css-line-numbers {
  counter-reset: line-number;
  display: block;
}
.markdown-renderer pre code.css-line-numbers .code-line {
  counter-increment: line-number;
  position: relative;
  display: block;
  min-width: fit-content;
  padding-left: calc(var(--line-number-width, 25px) + 17px);
  padding-right: 12px;
  padding-top: 2px;
  padding-bottom: 2px;
  line-height: 1.5;
  white-space: pre;
}
.markdown-renderer pre code.css-line-numbers .code-line::before {
  counter-increment: line-number 0;
  content: counter(line-number);
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--line-number-width, 25px);
  padding: 2px 6px;
  text-align: right;
  color: rgba(255, 255, 255, 0.4);
  background: #282c34;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  user-select: none;
  white-space: nowrap;
  line-height: 1.5;
  z-index: 1;
}
.markdown-renderer pre code.css-line-numbers .code-line:hover {
  background: linear-gradient(
    to right,
    transparent 0%,
    transparent var(--line-number-width, 25px),
    rgba(255, 255, 255, 0.05) var(--line-number-width, 25px),
    rgba(255, 255, 255, 0.05) 100%
  );
}
.markdown-renderer pre code ::selection,
.markdown-renderer pre code::selection,
.markdown-renderer .hljs::selection,
.markdown-renderer .hljs *::selection,
.markdown-renderer .code-line::selection,
.markdown-renderer .code-line span::selection {
  background: var(--lightGreen) !important;
  color: var(--white) !important;
}
.markdown-renderer blockquote {
  margin: 10px 0;
  padding: 10px 15px;
  border-left: 4px solid #667eea;
  background: rgba(102, 126, 234, 0.05);
  color: #666;
}
.markdown-renderer ul,
.markdown-renderer ol {
  margin: 10px 0;
  padding-left: 25px;
}
.markdown-renderer li {
  margin: 5px 0;
}
.markdown-renderer table {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
}
.markdown-renderer th,
.markdown-renderer td {
  padding: 8px 12px;
  border: 1px solid #e0e0e0;
  text-align: left;
}
.markdown-renderer th {
  background: rgba(102, 126, 234, 0.1);
  font-weight: bold;
}
.markdown-renderer img {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 10px 0;
}
.markdown-renderer hr {
  border: none;
  border-top: 1px solid #e0e0e0;
  margin: 15px 0;
}
.mermaid-container {
  position: relative;
  margin: 15px 0;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
  overflow-x: auto;
  text-align: center;
  transition: all 0.3s ease;
}
.mermaid-container svg {
  max-width: 100%;
  height: auto;
}
.mermaid-zoom-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #ddd;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  z-index: 10;
  padding: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.mermaid-zoom-btn:hover {
  background: rgba(255, 255, 255, 1);
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transform: scale(1.05);
}
.mermaid-zoom-btn:active {
  transform: scale(0.95);
}
.mermaid-zoom-btn .zoom-icon {
  width: 18px;
  height: 18px;
  color: #333;
  transition: color 0.2s ease;
}
.mermaid-zoom-btn:hover .zoom-icon {
  color: #667eea;
}
.mermaid-zoom-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.85);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.3s ease;
}
.mermaid-zoom-content {
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  justify-content: center;
  overflow: auto;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}
.mermaid-zoom-content svg {
  max-width: 100%;
  max-height: 100%;
  width: auto !important;
  height: auto !important;
  display: block !important;
  visibility: visible !important;
  opacity: 1 !important;
}
.dark-mode .mermaid-zoom-content {
  background: #2d2d2d;
}
.mermaid-zoom-close {
  position: fixed;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #ddd;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  padding: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  z-index: 10001;
}
.mermaid-zoom-close:hover {
  background: rgba(255, 255, 255, 1);
  transform: scale(1.1) rotate(90deg);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}
.mermaid-zoom-close svg {
  color: #333;
  transition: color 0.2s ease;
}
.mermaid-zoom-close:hover svg {
  color: #ff4444;
}
.dark-mode .placeholder-line {
  background: linear-gradient(90deg, #2a2a2a 25%, #3a3a3a 50%, #2a2a2a 75%);
  background-size: 200% 100%;
  animation: loading 1.5s ease-in-out infinite;
}
.dark-mode .markdown-renderer code {
  background: rgba(102, 126, 234, 0.2);
  color: #ff79c6;
}
.dark-mode .markdown-renderer pre {
  background: #1e1e1e;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}
.dark-mode .markdown-renderer pre code {
  color: #f8f8f2;
}
.dark-mode .markdown-renderer pre code.hljs {
  background: transparent;
}
.dark-mode .markdown-renderer .code-toolbar {
  background: rgba(0, 0, 0, 0.3);
  border-bottom-color: rgba(255, 255, 255, 0.05);
}
.dark-mode .markdown-renderer .code-language {
  color: rgba(255, 255, 255, 0.6);
}
.dark-mode .markdown-renderer .code-copy-btn {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.7);
}
.dark-mode .markdown-renderer .code-copy-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.95);
}
.dark-mode .markdown-renderer .code-copy-btn.copied {
  background: rgba(76, 175, 80, 0.2);
  border-color: rgba(76, 175, 80, 0.4);
  color: #66bb6a;
}
.dark-mode .markdown-renderer pre code.css-line-numbers .code-line:hover {
  background: linear-gradient(
    to right,
    transparent 0%,
    transparent var(--line-number-width, 25px),
    rgba(255, 255, 255, 0.03) var(--line-number-width, 25px),
    rgba(255, 255, 255, 0.03) 100%
  );
}
.dark-mode .markdown-renderer pre code.css-line-numbers .code-line::before {
  color: rgba(255, 255, 255, 0.3);
  border-right-color: rgba(255, 255, 255, 0.08);
  background: #1e1e1e;
}
.dark-mode .markdown-renderer blockquote {
  background: rgba(102, 126, 234, 0.1);
  color: #b0b0b0;
  border-left-color: #667eea;
}
.dark-mode .markdown-renderer th,
.dark-mode .markdown-renderer td {
  border-color: #404040;
}
.dark-mode .markdown-renderer th {
  background: rgba(102, 126, 234, 0.2);
}
.dark-mode .markdown-renderer hr {
  border-top-color: #404040;
}
.dark-mode .markdown-renderer a {
  color: var(--link-color, #8ab4f8);
  border-bottom-color: var(--link-color, #8ab4f8);
}
.dark-mode .markdown-renderer a:hover {
  color: var(--link-color, #a8c7fa);
}
.dark-mode .mermaid-container {
  background: #2d2d2d;
}
.dark-mode .mermaid-zoom-btn {
  background: rgba(45, 45, 45, 0.9);
  border-color: #555;
}
.dark-mode .mermaid-zoom-btn:hover {
  background: rgba(45, 45, 45, 1);
  border-color: #667eea;
}
.dark-mode .mermaid-zoom-btn .zoom-icon {
  color: #ddd;
}
.dark-mode .mermaid-zoom-btn:hover .zoom-icon {
  color: #667eea;
}
.dark-mode .mermaid-zoom-close {
  background: rgba(45, 45, 45, 0.9);
  border-color: #555;
}
.dark-mode .mermaid-zoom-close:hover {
  background: rgba(45, 45, 45, 1);
}
.dark-mode .mermaid-zoom-close svg {
  color: #ddd;
}
.dark-mode .mermaid-zoom-close:hover svg {
  color: #ff6666;
}
</style>
