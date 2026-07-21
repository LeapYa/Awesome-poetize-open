import {
  loadMermaidResources,
  isMermaidLoaded,
  loadEChartsResources,
  loadHighlightResources,
  isHighlightJsLoaded,
  ensureHighlightLanguages,
  loadClipboardResources,
  isClipboardLoaded,
  loadKatexResources,
  isKatexLoadedGlobal,
} from '@/utils/resourceLoaders/resourceLoader'
import {
  parseEChartsOption,
  extractEChartsChartTypes,
} from '@/utils/echartsOptionParser'

async function collectEChartsChartTypesFromMarkdown(content) {
  const chartTypes = new Set()
  const codeBlockRegex = /```echarts[^\n\r]*\r?\n([\s\S]*?)```/g
  let match

  while ((match = codeBlockRegex.exec(content)) !== null) {
    try {
      const types = await extractEChartsChartTypes(match[1])
      types.forEach((type) => chartTypes.add(type))
    } catch (error) {
      // 解析失败留给真正渲染阶段处理，这里只做预加载类型推断
    }
  }

  return Array.from(chartTypes)
}

function extractBlockLanguage(preCode) {
  const classNameStr = preCode.className || ''
  const classNameArr = classNameStr.split(' ')

  let lang = ''
  classNameArr.some((className) => {
    if (className.startsWith('language-')) {
      lang = className.substring('language-'.length)
      return true
    }
    return false
  })
  return lang
}

export async function highlight() {
  if (!isHighlightJsLoaded()) {
    return
  }

  const attributes = {
    autocomplete: 'off',
    autocorrect: 'off',
    autocapitalize: 'off',
    spellcheck: 'false',
    contenteditable: 'false',
  }

  const entryContent = document.querySelector('.entry-content')
  if (!entryContent) return

  const preElements = entryContent.querySelectorAll('pre')

  // 先扫描所有未处理代码块用到的语言，按需加载对应语言包后再统一高亮
  const neededLanguages = []
  preElements.forEach((item) => {
    if (item.classList.contains('highlight-wrap')) {
      return
    }
    const preCode = item.querySelector('code')
    if (!preCode) {
      return
    }
    const lang = extractBlockLanguage(preCode)
    if (lang && lang !== 'mermaid' && lang !== 'echarts') {
      neededLanguages.push(lang)
    }
  })
  await ensureHighlightLanguages(neededLanguages)

  preElements.forEach((item, i) => {
    if (item.classList.contains('highlight-wrap')) {
      return
    }

    const preCode = item.querySelector('code')
    if (!preCode) {
      return
    }

    const lang = extractBlockLanguage(preCode)

    if (lang === 'mermaid' || lang === 'echarts') {
      return
    }

    try {
      const language = lang ? hljs.getLanguage(lang.toLowerCase()) : undefined
      // displayName 仅用于 data-rel 标签展示，lang 保留为 CSS-safe 标识符
      let displayName = lang
      let shouldHighlight = false
      if (!lang) {
        // 未指定语言：不猜测、不显示语言标签，保持纯代码块
        displayName = ''
      } else if (language !== undefined) {
        // language.name 可能含空格（如 "Nginx config"），不能用于 classList
        displayName = language.name
        shouldHighlight = true
      }
      // 指定了未注册的语言：按用户原文显示标签，不做高亮

      item.classList.remove('code-loading')
      item.classList.add('highlight-wrap')
      Object.keys(attributes).forEach((key) => {
        item.setAttribute(key, attributes[key])
      })
      if (displayName) {
        preCode.setAttribute('data-rel', displayName.toUpperCase())
      }
      if (lang) {
        preCode.classList.add(lang.toLowerCase())
      }

      if (shouldHighlight) {
        if (typeof hljs.highlightElement === 'function') {
          hljs.highlightElement(preCode)
        } else if (typeof hljs.highlightBlock === 'function') {
          hljs.highlightBlock(preCode)
        }
      }

      this.addLineNumbersWithCSS(preCode)
    } catch (error) {
      console.error('Error highlighting code block:', error)
      item.classList.add('highlight-wrap')
      Object.keys(attributes).forEach((key) => {
        item.setAttribute(key, attributes[key])
      })
      if (lang) {
        preCode.setAttribute('data-rel', lang.toUpperCase())
        preCode.classList.add(lang.toLowerCase())
      }
      this.addLineNumbersWithCSS(preCode)
    }
  })

  const codeBlocks = entryContent.querySelectorAll('pre code')
  codeBlocks.forEach((block, i) => {
    if (
      block.nextElementSibling &&
      block.nextElementSibling.classList.contains('copy-code')
    ) {
      return
    }

    block.id = 'hljs-' + i

    const copyButton = document.createElement('a')
    copyButton.className = 'copy-code'
    copyButton.href = 'javascript:'
    copyButton.setAttribute('data-clipboard-target', '#hljs-' + i)
    copyButton.innerHTML =
      '<i class="fa fa-clipboard" aria-hidden="true"></i>'

    if (
      block.parentNode &&
      copyButton &&
      copyButton.nodeType === Node.ELEMENT_NODE
    ) {
      try {
        block.parentNode.insertBefore(copyButton, block.nextSibling)
      } catch (e) { }
    }
  })

  if (typeof ClipboardJS !== 'undefined') {
    const that = this
    const clipboard = new ClipboardJS('.copy-code')

    clipboard.on('success', () => {
      that.$message({
        message: '代码已复制到剪贴板',
        type: 'success',
        duration: 2000,
      })
    })

    clipboard.on('error', () => {
      that.$message({
        message: '复制失败，请手动复制',
        type: 'error',
        duration: 2000,
      })
    })
  }
}

export function wrapTables() {
  const entryContent = document.querySelector('.entry-content')
  if (!entryContent) return

  const tables = entryContent.querySelectorAll('table')
  tables.forEach((table) => {
    if (!table.parentElement.classList.contains('table-wrapper')) {
      const wrapper = document.createElement('div')
      wrapper.className = 'table-wrapper'
      if (
        table.parentNode &&
        wrapper &&
        wrapper.nodeType === Node.ELEMENT_NODE
      ) {
        try {
          table.parentNode.insertBefore(wrapper, table)
          if (typeof wrapper.appendChild === 'function') {
            wrapper.appendChild(table)
          }
        } catch (e) { }
      }
    }
  })
}

export function addLineNumbersWithCSS(codeBlock) {
  if (!codeBlock) return
  if (codeBlock.classList.contains('css-line-numbers')) {
    return
  }

  try {
    codeBlock.classList.add('css-line-numbers')
    const codeContent = codeBlock.innerHTML
    const lines = codeContent.split('\n')

    if (lines.length > 0 && lines[lines.length - 1].trim() === '') {
      lines.pop()
    }

    const linesHTML = lines
      .map((line) => {
        const content = line.trim() === '' ? '&nbsp;' : line
        return `<div class="code-line">${content}</div>`
      })
      .join('')

    codeBlock.innerHTML = linesHTML

    const totalLines = lines.length
    const charLength = totalLines.toString().length
    // 基础字符长度宽度计算，设定 16px 作为下限（即至少按2位数的空间渲染）
    const widthNum = Math.max(16, charLength * 8)
    // 依据用户的调整，伪元素的总 padding(左右约 12px) 加上与代码的安全距离 6px
    const totalPadding = widthNum + 12 + 10
    codeBlock.style.setProperty('--line-number-width', widthNum + 'px')
    codeBlock.style.setProperty('--code-padding-left', totalPadding + 'px')
  } catch (e) { }
}

export function addLoadingPlaceholders() {
  const entryContent = document.querySelector('.entry-content')
  if (!entryContent) return

  const mermaidBlocks = entryContent.querySelectorAll('pre code.language-mermaid')
  mermaidBlocks.forEach((codeBlock) => {
    const pre = codeBlock.parentElement
    if (!pre.classList.contains('chart-loading')) {
      pre.classList.add('chart-loading')
      pre.setAttribute('data-chart-type', 'Mermaid')
    }
  })

  const echartsBlocks = entryContent.querySelectorAll('pre code.language-echarts')
  echartsBlocks.forEach((codeBlock) => {
    const pre = codeBlock.parentElement
    if (!pre.classList.contains('chart-loading')) {
      pre.classList.add('chart-loading')
      pre.setAttribute('data-chart-type', 'ECharts')
    }
  })

  const codeBlocks = entryContent.querySelectorAll('pre code')
  codeBlocks.forEach((codeBlock) => {
    const pre = codeBlock.parentElement
    const classes = codeBlock.className || ''
    if (
      !classes.includes('language-mermaid') &&
      !classes.includes('language-echarts') &&
      !pre.classList.contains('highlight-wrap') &&
      !pre.classList.contains('code-loading')
    ) {
      pre.classList.add('code-loading')
    }
  })
}

export function detectAndLoadResources() {
  const content = this.getDisplayedMarkdownContent()
  const loadTasks = []
  const articleId = this.loadingArticleId

  this.addLoadingPlaceholders()

  if (content.includes('```') && !isHighlightJsLoaded()) {
    const highlightTask = loadHighlightResources().then(() => {
      if (this.loadingArticleId !== articleId) {
        return
      }
      return this.$nextTick(() => {
        return this.highlight()
      })
    })
    loadTasks.push(highlightTask)
  } else if (content.includes('```')) {
    loadTasks.push(Promise.resolve(this.highlight()))
  }

  if (content.includes('```') && !isClipboardLoaded()) {
    loadClipboardResources()
  }

  if (
    (content.includes('$') || content.includes('$$')) &&
    !isKatexLoadedGlobal()
  ) {
    loadKatexResources()
  }

  if (content.includes('```mermaid') && !isMermaidLoaded()) {
    const mermaidTask = loadMermaidResources().then(() => {
      if (this.loadingArticleId !== articleId) {
        return
      }
      this.$nextTick(() => {
        this.renderMermaid()
      })
    })
    loadTasks.push(mermaidTask)
  } else if (content.includes('```mermaid')) {
    this.renderMermaid()
  }

  if (content.includes('```echarts')) {
    const echartsTask = collectEChartsChartTypesFromMarkdown(content)
      .then((chartTypes) => loadEChartsResources(chartTypes))
      .then(() => {
        if (this.loadingArticleId !== articleId) {
          return
        }
        this.$nextTick(() => {
          this.renderECharts()
        })
      })
    loadTasks.push(echartsTask)
  }

  const refreshToc = () => {
    if (this.loadingArticleId !== articleId) {
      return
    }

    const waitForDOMStable = (callback) => {
      this.$nextTick(() => {
        if (this.loadingArticleId !== articleId) {
          return
        }

        this.addId()

        const entryContent = document.querySelector('.entry-content')
        if (!entryContent) {
          setTimeout(() => waitForDOMStable(callback), 50)
          return
        }

        let mutationTimer = null
        let observer = null
        let isCallbackCalled = false

        const callCallback = () => {
          if (isCallbackCalled) return
          isCallbackCalled = true

          if (observer) {
            observer.disconnect()
            observer = null
          }
          if (mutationTimer) {
            clearTimeout(mutationTimer)
            mutationTimer = null
          }

          requestAnimationFrame(() => {
            requestAnimationFrame(() => {
              if (this.loadingArticleId !== articleId) {
                return
              }
              callback()
            })
          })
        }

        observer = new MutationObserver(() => {
          if (mutationTimer) {
            clearTimeout(mutationTimer)
          }
          mutationTimer = setTimeout(() => {
            callCallback()
          }, 100)
        })

        observer.observe(entryContent, {
          childList: true,
          subtree: true,
          attributes: true,
          attributeFilter: ['class', 'style'],
        })

        mutationTimer = setTimeout(() => {
          callCallback()
        }, 100)

        setTimeout(() => {
          if (!isCallbackCalled) {
            callCallback()
          }
        }, 1000)
      })
    }

    setTimeout(() => {
      if (this.loadingArticleId !== articleId) {
        return
      }
      waitForDOMStable(() => this.getTocbot())
    }, 50)
  }

  if (loadTasks.length > 0) {
    Promise.all(loadTasks).then(() => {
      refreshToc()
    })
  } else {
    refreshToc()
  }
}

export async function renderMermaid() {
  if (!isMermaidLoaded()) {
    return
  }

  const entryContent = document.querySelector('.entry-content')
  if (!entryContent) return

  const mermaidBlocks = entryContent.querySelectorAll('pre code.language-mermaid')
  if (mermaidBlocks.length === 0) return

  try {
    for (let i = 0; i < mermaidBlocks.length; i++) {
      const codeBlock = mermaidBlocks[i]
      const pre = codeBlock.parentElement

      if (pre.classList.contains('mermaid-rendered')) {
        continue
      }

      const code = codeBlock.textContent
      const id = `mermaid-${Date.now()}-${i}`

      if (!pre.parentNode) {
        continue
      }

      const container = document.createElement('div')
      container.className = 'mermaid-container'
      container.setAttribute('data-mermaid-code', code)

      const { svg } = await window.mermaid.render(id, code)
      container.innerHTML = svg

      this.applyMermaidThemeStyles(container)

      const zoomButton = document.createElement('button')
      zoomButton.className = 'mermaid-zoom-btn'
      zoomButton.setAttribute('aria-label', '放大图表')
      zoomButton.innerHTML = `
        <svg class="zoom-icon" viewBox="0 0 1024 1024" width="20" height="20">
          <path d="M815.4 706.9L695.1 586.6c25.3-45.8 39.8-98.3 39.8-154.3 0-176.5-143.1-319.6-319.6-319.6-176.5 0-319.6 143.1-319.6 319.6s143.1 319.6 319.6 319.6c56 0 108.5-14.5 154.3-39.8l120.3 120.3c34.7 34.7 90.9 34.7 125.6 0 34.6-34.6 34.6-90.8-0.1-125.5z m-631-274.6c0-127.3 103.6-230.8 230.8-230.8S646.1 305 646.1 432.3 542.6 663.1 415.3 663.1 184.4 559.6 184.4 432.3z" fill="currentColor"></path>
          <path d="M504.1 396.8h-53.3v-53.3c0-19.6-15.9-35.5-35.5-35.5s-35.5 15.9-35.5 35.5v53.3h-53.3c-19.6 0-35.5 15.9-35.5 35.5s15.9 35.5 35.5 35.5h53.3v53.3c0 19.6 15.9 35.5 35.5 35.5s35.5-15.9 35.5-35.5v-53.3h53.3c19.6 0 35.5-15.9 35.5-35.5s-15.9-35.5-35.5-35.5z" fill="currentColor"></path>
        </svg>
      `

      container.addEventListener('contextmenu', (event) => {
        this.handleGraphicContextMenu(event, container, 'mermaid')
      })

      zoomButton.addEventListener('click', (event) => {
        event.stopPropagation()
        this.toggleMermaidZoom(container, zoomButton)
      })

      this.applyZoomButtonTheme(zoomButton)
      container.appendChild(zoomButton)
      pre.classList.remove('chart-loading')
      pre.parentNode.replaceChild(container, pre)
    }
  } catch (error) {
    console.error('Mermaid渲染失败:', error)
  }
}

export async function renderECharts() {
  if (this._isRenderingECharts) {
    return
  }

  const entryContent = document.querySelector('.entry-content')
  if (!entryContent) return

  const echartsBlocks = entryContent.querySelectorAll('pre code.language-echarts')
  if (echartsBlocks.length === 0) return

  const parsedConfigs = new Map()
  const requiredChartTypes = new Set()

  for (let i = 0; i < echartsBlocks.length; i++) {
    const codeBlock = echartsBlocks[i]
    const pre = codeBlock.parentElement
    if (!pre || pre.classList.contains('echarts-rendered')) {
      continue
    }

    const code = codeBlock.textContent
    try {
      const config = await parseEChartsOption(code)
      parsedConfigs.set(pre, config)

      const chartTypes = await extractEChartsChartTypes(config)
      chartTypes.forEach((type) => requiredChartTypes.add(type))
    } catch (error) {
      // 解析失败时在实际渲染循环中回显错误提示
    }
  }

  if (parsedConfigs.size > 0) {
    await loadEChartsResources(Array.from(requiredChartTypes))
  }

  if (!window.echarts) {
    return
  }

  this._isRenderingECharts = true

  try {
    for (let i = 0; i < echartsBlocks.length; i++) {
      const codeBlock = echartsBlocks[i]
      const pre = codeBlock.parentElement

      if (pre.classList.contains('echarts-rendered')) {
        continue
      }

      try {
        const code = codeBlock.textContent
        const config = parsedConfigs.get(pre)
        if (!config) {
          try {
            await parseEChartsOption(code)
          } catch (parseError) {
            pre.classList.remove('chart-loading')
            pre.classList.add('echarts-rendered')
            pre.setAttribute(
              'data-echarts-error',
              String(parseError?.message || parseError)
            )
            if (!pre.hasAttribute('data-echarts-error-rendered')) {
              const errorEl = document.createElement('div')
              errorEl.className = 'echarts-error-message'
              errorEl.textContent = `ECharts 配置解析失败：${String(
                parseError?.message || parseError
              )}\n请使用纯 JSON/JSON5（支持注释、单引号、尾逗号、未加引号的 key），暂不支持 function/=>`
              pre.parentNode.insertBefore(errorEl, pre)
              pre.setAttribute('data-echarts-error-rendered', 'true')
            }
          }
          continue
        }

        if (!pre.parentNode) {
          continue
        }

        pre.classList.add('echarts-rendered')

        const container = document.createElement('div')
        container.className = 'echarts-container'
        container.style.width = '100%'
        container.style.height = config.height || '400px'
        container.style.marginBottom = '20px'
        container.setAttribute('data-echarts-config', code)
        pre.classList.remove('chart-loading')
        pre.parentNode.replaceChild(container, pre)

        await this.$nextTick()

        const isDark =
          document.documentElement.classList.contains('dark-mode') ||
          document.body.classList.contains('dark-mode')

        const chart = window.echarts.init(
          container,
          isDark ? 'dark' : 'light'
        )

        const finalConfig = {
          animation: true,
          animationDuration: 1000,
          animationEasing: 'cubicOut',
          animationDelay: 0,
          backgroundColor: 'transparent',
          ...config,
        }

        chart.setOption(finalConfig)
        container._echartsInstance = chart

        const resizeHandler = () => {
          if (chart && !chart.isDisposed()) {
            chart.resize()
          }
        }
        window.addEventListener('resize', resizeHandler)
        container._resizeHandler = resizeHandler
      } catch (renderError) {
        console.error('ECharts渲染失败:', renderError)
      }
    }
  } catch (error) {
    console.error('ECharts渲染失败:', error)
  } finally {
    this._isRenderingECharts = false
  }
}

export async function handleThemeChange(themeData) {
  try {
    const mermaidContainers = document.querySelectorAll('.mermaid-container')

    if (mermaidContainers.length !== 0) {
      for (let i = 0; i < mermaidContainers.length; i++) {
        const container = mermaidContainers[i]
        const originalCode = container.getAttribute('data-mermaid-code')
        if (!originalCode) {
          continue
        }

        const newId = `mermaid-theme-${Date.now()}-${i}`
        const { svg } = await window.mermaid.render(newId, originalCode)
        const zoomButton = container.querySelector('.mermaid-zoom-btn')

        container.innerHTML = svg

        if (zoomButton) {
          container.appendChild(zoomButton)
          this.applyZoomButtonTheme(zoomButton)
        } else {
          const newZoomButton = document.createElement('button')
          newZoomButton.className = 'mermaid-zoom-btn'
          newZoomButton.setAttribute('aria-label', '放大图表')
          newZoomButton.innerHTML = `
            <svg class="zoom-icon zoom-in-icon" viewBox="0 0 1024 1024" width="20" height="20">
              <path d="M840.824471 180.766118l-178.115765 22.106353a7.469176 7.469176 0 0 0-4.397177 12.709647l51.501177 51.501176-144.504471 144.444235a7.529412 7.529412 0 0 0 0 10.661647l42.465883 42.465883a7.529412 7.529412 0 0 0 10.661647 0l144.564706-144.564706 51.440941 51.440941c4.457412 4.457412 11.986824 1.807059 12.709647-4.397176l22.046117-177.995294a7.408941 7.408941 0 0 0-8.432941-8.372706z m-412.611765 378.578823a7.529412 7.529412 0 0 0-10.661647 0l-144.444235 144.564706-51.501177-51.501176a7.469176 7.469176 0 0 0-12.649412 4.397176L186.729412 834.861176a7.529412 7.529412 0 0 0 8.372706 8.372706l178.055529-22.106353a7.469176 7.469176 0 0 0 4.457412-12.709647l-51.501177-51.501176 144.564706-144.564706a7.529412 7.529412 0 0 0 0-10.601412l-42.526117-42.345412z" fill="currentColor"></path>
            </svg>
            <svg class="zoom-icon zoom-out-icon" style="display: none;" viewBox="0 0 1024 1024" width="20" height="20">
              <path d="M851.2 214.186667l-41.386667-41.386667a7.381333 7.381333 0 0 0-10.368 0L654.933333 317.397333l-50.176-50.176a7.253333 7.253333 0 0 0-12.373333 4.266667l-21.589333 173.525333a7.338667 7.338667 0 0 0 8.192 8.149334l173.568-21.546667c6.058667-0.725333 8.533333-8.106667 4.309333-12.373333L706.688 369.066667l144.597333-144.64a7.338667 7.338667 0 0 0-0.085333-10.24z m-406.186667 356.608l-173.568 21.589333a7.338667 7.338667 0 0 0-4.309333 12.373333l50.176 50.176-144.512 144.512a7.381333 7.381333 0 0 0 0 10.368l41.386667 41.386667a7.381333 7.381333 0 0 0 10.368 0l144.597333-144.64 50.176 50.218667a7.253333 7.253333 0 0 0 12.373333-4.309334l21.461334-173.482666a7.253333 7.253333 0 0 0-8.106667-8.192z" fill="currentColor"></path>
            </svg>
          `

          newZoomButton.addEventListener('click', (event) => {
            event.stopPropagation()
            this.toggleMermaidZoom(container, newZoomButton)
          })

          this.applyZoomButtonTheme(newZoomButton)
          container.appendChild(newZoomButton)
        }

        this.applyMermaidThemeStyles(container)
      }
    }
  } catch (error) {
    console.error('主题切换时重新渲染Mermaid失败:', error)
  }

  try {
    const echartsContainers = document.querySelectorAll('.echarts-container')

    if (echartsContainers.length === 0) {
      return
    }

    const isDark =
      (themeData && themeData.theme === 'dark') ||
      document.body.classList.contains('dark-mode')

    for (let i = 0; i < echartsContainers.length; i++) {
      const container = echartsContainers[i]
      const chart = container._echartsInstance

      if (!chart) {
        continue
      }

      const configStr = container.getAttribute('data-echarts-config')
      if (!configStr) {
        continue
      }

      try {
        const config = JSON.parse(configStr)

        chart.dispose()

        const newChart = window.echarts.init(
          container,
          isDark ? 'dark' : 'light'
        )

        const finalConfig = {
          animation: true,
          animationDuration: 1000,
          animationEasing: 'cubicOut',
          animationDelay: 0,
          backgroundColor: 'transparent',
          ...config,
        }

        newChart.setOption(finalConfig)
        container._echartsInstance = newChart

        if (container._resizeHandler) {
          window.removeEventListener('resize', container._resizeHandler)
        }
        const resizeHandler = () => newChart.resize()
        window.addEventListener('resize', resizeHandler)
        container._resizeHandler = resizeHandler
      } catch (parseError) {
        console.error('ECharts 配置解析失败:', parseError)
      }
    }
  } catch (error) {
    console.error('主题切换时重新渲染ECharts失败:', error)
  }
}

export function applyZoomButtonTheme(button) {
  if (!button) return

  const isDark = document.body.classList.contains('dark-mode')

  if (isDark) {
    button.style.background = 'rgba(55, 55, 55, 0.95)'
    button.style.borderColor = '#555'
    button.style.boxShadow = '0 2px 8px rgba(0, 0, 0, 0.3)'

    const icons = button.querySelectorAll('.zoom-icon')
    icons.forEach((icon) => {
      icon.style.color = '#e0e0e0'
    })
  } else {
    button.style.background = ''
    button.style.borderColor = ''
    button.style.boxShadow = ''

    const icons = button.querySelectorAll('.zoom-icon')
    icons.forEach((icon) => {
      icon.style.color = ''
    })
  }
}

export function applyMermaidThemeStyles(container) {
  try {
    const svg = container.querySelector('svg')
    if (!svg) return

    const isDark = document.body.classList.contains('dark-mode')

    if (isDark) {
      container.style.backgroundColor = '#2d2d2d'
    } else {
      container.style.backgroundColor = '#f8f9fa'
    }

    if (isDark) {
      const backgrounds = svg.querySelectorAll(
        'rect[fill="#f8f9fa"], rect[fill="#F8F9FA"], rect[fill="rgb(248, 249, 250)"], rect.background, g.background rect'
      )
      backgrounds.forEach((rect) => {
        rect.setAttribute('fill', '#2d2d2d')
      })

      if (svg.style.backgroundColor && svg.style.backgroundColor !== 'transparent') {
        svg.style.backgroundColor = '#2d2d2d'
      }

      if (!backgrounds.length && !svg.style.backgroundColor) {
        svg.style.backgroundColor = '#2d2d2d'
      }
    } else {
      const backgrounds = svg.querySelectorAll(
        'rect[fill="#2d2d2d"], rect.background, g.background rect'
      )
      backgrounds.forEach((rect) => {
        rect.setAttribute('fill', '#f8f9fa')
      })

      if (svg.style.backgroundColor) {
        svg.style.backgroundColor = ''
      }
    }

    if (isDark) {
      const lines = svg.querySelectorAll('path, line, polyline')
      lines.forEach((line) => {
        const stroke = line.getAttribute('stroke')
        if (
          stroke === 'black' ||
          stroke === '#000' ||
          stroke === '#000000' ||
          stroke === '#333' ||
          stroke === '#333333' ||
          stroke === 'rgb(0, 0, 0)'
        ) {
          line.setAttribute('stroke', '#a0a0a0')
        }
      })

      const markers = svg.querySelectorAll('marker path, marker polygon')
      markers.forEach((marker) => {
        const fill = marker.getAttribute('fill')
        if (
          fill === 'black' ||
          fill === '#000' ||
          fill === '#000000' ||
          fill === '#333' ||
          fill === '#333333'
        ) {
          marker.setAttribute('fill', '#a0a0a0')
        }
        const stroke = marker.getAttribute('stroke')
        if (
          stroke === 'black' ||
          stroke === '#000' ||
          stroke === '#000000' ||
          stroke === '#333' ||
          stroke === '#333333'
        ) {
          marker.setAttribute('stroke', '#a0a0a0')
        }
      })

      const styleTag = svg.querySelector('style')
      if (styleTag) {
        let cssText = styleTag.textContent
        cssText = cssText.replace(
          /stroke:\s*(black|#000000|#000|#333333|#333)/gi,
          'stroke: #a0a0a0'
        )

        const classesToLighten = [
          'pieTitleText',
          'titleText',
          'messageText',
          'labelText',
          'loopText',
          'sequenceNumber',
          'sectionTitle',
          'taskTextOutsideRight',
          'taskTextOutsideLeft',
          'noteText',
          'legend',
          'legendText',
          'slice',
        ]

        classesToLighten.forEach((className) => {
          const regex = new RegExp(
            `(\\.${className}\\s*\\{[^}]*?)fill:\\s*(black|#000000|#000|#333333|#333|rgb\\s*\\(\\s*51\\s*,\\s*51\\s*,\\s*51\\s*\\))([^}]*\\})`,
            'gi'
          )
          cssText = cssText.replace(regex, '$1fill: #e0e0e0$3')
        })

        const nestedSelectorsToLighten = [
          '\\.legend\\s+text',
          '\\.legend\\s+>\\s*text',
          '\\.pie\\s+text',
        ]

        nestedSelectorsToLighten.forEach((selectorPattern) => {
          const regex = new RegExp(
            `(${selectorPattern}\\s*\\{[^}]*?)fill:\\s*(black|#000000|#000|#333333|#333|rgb\\s*\\(\\s*51\\s*,\\s*51\\s*,\\s*51\\s*\\))([^}]*\\})`,
            'gi'
          )
          cssText = cssText.replace(regex, '$1fill: #e0e0e0$3')
        })

        styleTag.textContent = cssText
      }

      const titles = svg.querySelectorAll(
        '.titleText, .title, text.title, .pieTitleText'
      )
      titles.forEach((title) => {
        const fill = title.getAttribute('fill')
        if (!fill || fill === 'black' || fill === '#000' || fill === '#000000' || fill === '#333') {
          title.setAttribute('fill', '#e0e0e0')
        }
      })

      const messageTexts = svg.querySelectorAll(
        '.messageText, .sequenceNumber, .loopText'
      )
      messageTexts.forEach((text) => {
        const fill = text.getAttribute('fill')
        if (!fill || fill === 'black' || fill === '#000' || fill === '#000000' || fill === '#333') {
          text.setAttribute('fill', '#e0e0e0')
        }
      })

      const ganttTexts = svg.querySelectorAll(
        '.sectionTitle, .taskTextOutsideRight, .taskTextOutsideLeft'
      )
      ganttTexts.forEach((text) => {
        const fill = text.getAttribute('fill')
        if (!fill || fill === 'black' || fill === '#000' || fill === '#000000' || fill === '#333') {
          text.setAttribute('fill', '#e0e0e0')
        }
      })

      const legendTexts = svg.querySelectorAll('.legend text')
      legendTexts.forEach((text) => {
        const fill = text.getAttribute('fill')
        const computedFill = window.getComputedStyle(text).fill
        if (
          !fill ||
          fill === 'black' ||
          fill === '#000' ||
          fill === '#000000' ||
          fill === '#333' ||
          computedFill === 'rgb(0, 0, 0)'
        ) {
          text.setAttribute('fill', '#e0e0e0')
        }
      })
    } else {
      const lines = svg.querySelectorAll(
        'path[stroke="#a0a0a0"], line[stroke="#a0a0a0"], polyline[stroke="#a0a0a0"]'
      )
      lines.forEach((line) => {
        line.setAttribute('stroke', '#333')
      })

      const markers = svg.querySelectorAll(
        'marker path[fill="#a0a0a0"], marker polygon[fill="#a0a0a0"]'
      )
      markers.forEach((marker) => {
        marker.setAttribute('fill', '#333')
      })

      const edgeLabels = svg.querySelectorAll(
        '.edgeLabel text[fill="#e0e0e0"], .edgeLabel span, .labelText[fill="#e0e0e0"]'
      )
      edgeLabels.forEach((label) => {
        label.setAttribute('fill', '#333')
        label.style.color = ''
      })

      const titles = svg.querySelectorAll(
        '.titleText[fill="#e0e0e0"], .title[fill="#e0e0e0"], text.title[fill="#e0e0e0"]'
      )
      titles.forEach((title) => {
        title.setAttribute('fill', '#333')
      })
    }
  } catch (error) {
    console.error('应用Mermaid主题样式失败:', error)
  }
}

export function toggleMermaidZoom(container) {
  let overlay = document.getElementById('mermaid-zoom-overlay')

  if (overlay) {
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

  overlay = document.createElement('div')
  overlay.id = 'mermaid-zoom-overlay'
  overlay.className = 'mermaid-zoom-overlay'

  const svg = container.querySelector('svg')
  if (!svg) return

  const content = document.createElement('div')
  content.className = 'mermaid-zoom-content'
  content.innerHTML = svg.outerHTML

  const insertedSvg = content.querySelector('svg')
  if (insertedSvg) {
    const viewBox = insertedSvg.getAttribute('viewBox')
    insertedSvg.removeAttribute('style')

    if (viewBox) {
      const [, , width, height] = viewBox.split(' ').map(Number)
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

  content.addEventListener('contextmenu', (event) => {
    this.handleGraphicContextMenu(event, content, 'mermaid')
  })

  const closeBtn = document.createElement('button')
  closeBtn.className = 'mermaid-zoom-close'
  closeBtn.setAttribute('aria-label', '关闭')
  closeBtn.innerHTML = `
    <svg viewBox="0 0 1024 1024" width="24" height="24">
      <path d="M557.312 513.248l265.28-263.904c12.544-12.48 12.608-32.704 0.128-45.248-12.512-12.576-32.704-12.608-45.248-0.128L512.128 467.904 246.72 204.096c-12.48-12.544-32.704-12.608-45.248-0.128-12.576 12.512-12.608 32.704-0.128 45.248l265.344 263.84-265.28 263.872c-12.544 12.48-12.608 32.704-0.128 45.248 6.24 6.272 14.464 9.44 22.688 9.44 8.16 0 16.32-3.104 22.56-9.312l265.344-263.872 265.376 263.904c6.272 6.272 14.464 9.408 22.688 9.408 8.16 0 16.32-3.104 22.56-9.312 12.544-12.48 12.608-32.704 0.128-45.248L557.312 513.248z" fill="currentColor"></path>
    </svg>
  `

  overlay.appendChild(content)
  overlay.appendChild(closeBtn)
  document.body.appendChild(overlay)

  overlay.style.opacity = '0'
  setTimeout(() => {
    overlay.style.opacity = '1'
  }, 10)

  document.body.style.overflow = 'hidden'

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

  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) {
      closeOverlay()
    }
  })

  closeBtn.addEventListener('click', closeOverlay)
}

export function handleGraphicContextMenu(event, target, type) {
  event.preventDefault()
  this.graphicContextMenu.visible = true
  this.graphicContextMenu.x = event.pageX
  this.graphicContextMenu.y = event.pageY
  this.graphicContextMenu.currentTarget = target
  this.graphicContextMenu.currentType = type
}

export function closeGraphicContextMenu() {
  this.graphicContextMenu.visible = false
  this.graphicContextMenu.currentTarget = null
  this.graphicContextMenu.currentType = null
}

export async function copyGraphicImage() {
  const { currentTarget, currentType } = this.graphicContextMenu
  if (!currentTarget) return

  try {
    if (currentType === 'mermaid') {
      const svg = currentTarget.querySelector('svg')
      if (!svg) throw new Error('SVG not found')
      const canvas = await this.convertSvgToCanvas(svg)
      canvas.toBlob(async (blob) => {
        try {
          const item = new window.ClipboardItem({ 'image/png': blob })
          await navigator.clipboard.write([item])
          this.$message.success('图片已复制到剪贴板！')
        } catch (err) {
          console.error('Copy failed:', err)
          this.$message.error('复制失败，请尝试下载 PNG (浏览器可能不支持直接复制图片)')
        }
      })
    } else if (currentType === 'image') {
      const isBase64 = currentTarget.src.startsWith('data:')
      const response = await fetch(currentTarget.src)
      const blob = await response.blob()
      try {
        const item = new window.ClipboardItem({ [blob.type]: blob })
        await navigator.clipboard.write([item])
        this.$message.success('图片已复制到剪贴板！')
      } catch (err) {
        console.error('Copy failed:', err)
        this.$message.error('由于跨域或格式限制复制失败，请尝试直接下载')
      }
    }
  } catch (error) {
    console.error('Copy processing failed:', error)
    this.$message.error('图片处理失败')
  }

  this.closeGraphicContextMenu()
}

export async function downloadGraphicImage() {
  const { currentTarget, currentType } = this.graphicContextMenu
  if (!currentTarget) return

  try {
    if (currentType === 'mermaid') {
      const svg = currentTarget.querySelector('svg')
      if (!svg) throw new Error('SVG not found')
      const canvas = await this.convertSvgToCanvas(svg)
      const imgUrl = canvas.toDataURL('image/png')
      const downloadLink = document.createElement('a')
      downloadLink.href = imgUrl
      downloadLink.download = `mermaid-diagram-${Date.now()}.png`
      document.body.appendChild(downloadLink)
      downloadLink.click()
      document.body.removeChild(downloadLink)
      this.$message.success('PNG 下载已开始')
    } else if (currentType === 'image') {
      const urlPart = currentTarget.src.split('?')[0]
      const filename = urlPart.split('/').pop() || `image-${Date.now()}.png`
      fetch(currentTarget.src)
        .then(response => response.blob())
        .then(blob => {
          const blobUrl = URL.createObjectURL(blob)
          const downloadLink = document.createElement('a')
          downloadLink.href = blobUrl
          downloadLink.download = filename
          document.body.appendChild(downloadLink)
          downloadLink.click()
          document.body.removeChild(downloadLink)
          URL.revokeObjectURL(blobUrl)
          this.$message.success('下载已开始')
        })
        .catch(() => {
          window.open(currentTarget.src, '_blank')
        })
    }
  } catch (error) {
    console.error('Download failed:', error)
    this.$message.error('下载失败')
  }

  this.closeGraphicContextMenu()
}

export function processImages() {
  const entryContent = document.querySelector('.entry-content')
  if (!entryContent) return

  const images = entryContent.querySelectorAll('img:not(.pictureReg):not(.emoji)')

  images.forEach(img => {
    if (img.hasAttribute('data-image-processed')) return
    img.setAttribute('data-image-processed', 'true')

    img.style.cursor = 'zoom-in'

    img.addEventListener('click', (event) => {
      event.stopPropagation()
      this.toggleImageZoom(img.src, img)
    })

    img.addEventListener('contextmenu', (event) => {
      this.handleGraphicContextMenu(event, img, 'image')
    })
  })
}

export function toggleImageZoom(src, clickedImg) {
  const entryContent = document.querySelector('.entry-content')
  const images = entryContent ? Array.from(entryContent.querySelectorAll('img:not(.pictureReg):not(.emoji)')) : []
  const urlList = images.map(img => img.src)

  let initialIndex = 0
  if (clickedImg) {
    initialIndex = images.indexOf(clickedImg)
  } else {
    initialIndex = urlList.indexOf(src)
  }

  this.previewImages = urlList.length > 0 ? urlList : [src]
  this.previewImageIndex = initialIndex >= 0 ? initialIndex : 0
  this.showImageViewer = true
}

export function inlineSvgStyles(source, target) {
  if (source.nodeType !== 1) return

  const computed = window.getComputedStyle(source)
  const properties = [
    'fill',
    'stroke',
    'stroke-width',
    'stroke-dasharray',
    'stroke-linecap',
    'stroke-linejoin',
    'opacity',
    'text-anchor',
    'dominant-baseline',
    'alignment-baseline',
    'font-family',
    'font-size',
    'font-weight',
    'font-style',
    'letter-spacing',
    'text-decoration',
    'line-height',
    'color',
    'display',
    'visibility',
    'margin-top',
    'margin-right',
    'margin-bottom',
    'margin-left',
    'padding-top',
    'padding-right',
    'padding-bottom',
    'padding-left',
    'border-top-width',
    'border-right-width',
    'border-bottom-width',
    'border-left-width',
    'border-top-style',
    'border-right-style',
    'border-bottom-style',
    'border-left-style',
    'border-top-color',
    'border-right-color',
    'border-bottom-color',
    'border-left-color',
    'background-color',
    'width',
    'height',
    'box-sizing',
    'white-space',
    'overflow',
    'text-overflow',
    'word-wrap',
    'word-break',
    'flex-direction',
    'justify-content',
    'align-items',
    'border-radius',
    'z-index',
  ]

  properties.forEach((prop) => {
    const val = computed.getPropertyValue(prop)
    if (val && val !== 'initial' && val !== 'none' && val !== 'inherit') {
      target.style[prop] = val
    }

    const attrMap = [
      'fill',
      'stroke',
      'stroke-width',
      'font-family',
      'font-size',
      'font-weight',
      'opacity',
      'text-anchor',
      'dominant-baseline',
      'alignment-baseline',
    ]

    if (
      attrMap.includes(prop) &&
      val &&
      val !== 'none' &&
      val !== 'auto' &&
      val !== 'inherit'
    ) {
      target.setAttribute(prop, val)
    }

    if (prop === 'dominant-baseline' && (val === 'middle' || val === 'central')) {
      target.style.dominantBaseline = 'auto'
      target.setAttribute('dominant-baseline', 'auto')

      if (source.tagName.toLowerCase() === 'text' && !target.hasAttribute('dy')) {
        target.setAttribute('dy', '0.35em')
      }
    }
  })

  const sourceChildren = source.children
  const targetChildren = target.children
  if (sourceChildren.length === targetChildren.length) {
    for (let i = 0; i < sourceChildren.length; i++) {
      this.inlineSvgStyles(sourceChildren[i], targetChildren[i])
    }
  }
}

export function convertSvgToCanvas(svgElement) {
  return new Promise((resolve, reject) => {
    const clonedSvg = svgElement.cloneNode(true)
    this.inlineSvgStyles(svgElement, clonedSvg)

    const bbox = svgElement.getBoundingClientRect()
    let width = bbox.width
    let height = bbox.height

    if (width === 0 || height === 0) {
      const viewBoxVal = clonedSvg.getAttribute('viewBox')
      if (viewBoxVal) {
        const parts = viewBoxVal.split(/\s+|,/)
        if (parts.length === 4) {
          width = parseFloat(parts[2])
          height = parseFloat(parts[3])
        }
      }
      if (!width) width = parseFloat(clonedSvg.getAttribute('width')) || 800
      if (!height) height = parseFloat(clonedSvg.getAttribute('height')) || 600
    }

    clonedSvg.setAttribute('width', width)
    clonedSvg.setAttribute('height', height)

    const serializer = new XMLSerializer()
    let svgString = serializer.serializeToString(clonedSvg)

    if (!svgString.match(/^<svg[^>]+xmlns="http\:\/\/www\.w3\.org\/2000\/svg"/)) {
      svgString = svgString.replace(
        /^<svg/,
        '<svg xmlns="http://www.w3.org/2000/svg"'
      )
    }

    const img = new Image()
    img.crossOrigin = 'Anonymous'
    const base64Svg = window.btoa(unescape(encodeURIComponent(svgString)))
    const url = `data:image/svg+xml;base64,${base64Svg}`

    img.onload = () => {
      const canvas = document.createElement('canvas')
      const scale = 2
      canvas.width = width * scale
      canvas.height = height * scale
      const ctx = canvas.getContext('2d')
      ctx.scale(scale, scale)

      const isDark =
        document.body.classList.contains('dark-mode') ||
        document.documentElement.classList.contains('dark-mode')
      const bgColor = isDark ? '#1a1a1a' : '#ffffff'

      ctx.fillStyle = bgColor
      ctx.fillRect(0, 0, width, height)
      ctx.drawImage(img, 0, 0, width, height)

      resolve(canvas)
    }

    img.onerror = (error) => {
      reject(error)
    }

    img.src = url
  })
}
