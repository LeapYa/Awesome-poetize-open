import { getTocTitle } from '@/utils/languageUtils'
import { emitPluginHook } from '@/composables/usePluginLoader'
import { getTocEmoji } from '@/composables/useArticleTheme'

function formatTocLinks() {
  const tocLinks = document.querySelectorAll('.toc a.toc-link')
  const prefixRegex = /^(([0-9]+(?:\.[0-9]+)*|[一二三四五六七八九十百]+|[a-zA-Z]|[IVXLCDMivxlcdm]+)(?:\.|、|-|\s)+|[\(（][0-9a-zA-Z一二三四五六七八九十百]+[\)）](?:\.|、|-|\s)?)\s*(.*)$/

  tocLinks.forEach(link => {
    if (link.querySelector('.toc-text')) return

    const text = link.textContent.trim()
    const match = text.match(prefixRegex)
    if (match) {
      const prefix = match[1]
      const titleText = match[3]

      const prefixSpan = document.createElement('span')
      prefixSpan.className = 'toc-prefix'
      prefixSpan.textContent = prefix

      const textSpan = document.createElement('span')
      textSpan.className = 'toc-text'
      textSpan.textContent = titleText

      link.textContent = ''
      link.appendChild(prefixSpan)
      link.appendChild(textSpan)
    } else {
      const textSpan = document.createElement('span')
      textSpan.className = 'toc-text'
      textSpan.textContent = text

      link.textContent = ''
      link.appendChild(textSpan)
    }
  })
}

function getOffsetTopRelativeTo(elem, container) {
  let offsetTop = 0
  let current = elem
  while (current && current !== container && container.contains(current)) {
    offsetTop += current.offsetTop
    current = current.offsetParent
  }
  return offsetTop
}

function scrollActiveTocLinkIntoView() {
  const container = document.querySelector('.toc > .toc-list')
  if (!container) return

  const activeLink = container.querySelector('.is-active-link')
  if (!activeLink) return

  const containerTop = container.scrollTop
  const containerBottom = containerTop + container.clientHeight

  const elemTop = getOffsetTopRelativeTo(activeLink, container)
  let elemBottom = elemTop + activeLink.offsetHeight

  // Check if this active link has an expanded sub-list as a sibling
  const parentLi = activeLink.closest('.toc-list-item')
  if (parentLi) {
    const subList = parentLi.querySelector('.toc-list')
    if (subList && !subList.classList.contains('is-collapsed')) {
      const subListTop = getOffsetTopRelativeTo(subList, container)
      elemBottom = subListTop + subList.offsetHeight
    }
  }

  // If the active item (or its sublist) is below the visible scroll area
  if (elemBottom > containerBottom) {
    container.scrollTo({
      top: elemBottom - container.clientHeight + 20, // scroll down with some padding
      behavior: 'smooth'
    })
  }
  // If the active item is above the visible scroll area
  else if (elemTop < containerTop) {
    container.scrollTo({
      top: elemTop - 20, // scroll up with some padding
      behavior: 'smooth'
    })
  }
}

export function syncTocPosition() {
  const scrollingElement =
    document.scrollingElement || document.documentElement || document.body
  this.scrollTop = scrollingElement ? scrollingElement.scrollTop : 0
  const tocElements = document.querySelectorAll('.toc')
  tocElements.forEach((element) => {
    if (this.scrollTop < window.innerHeight / 4) {
      element.style.top = window.innerHeight / 4 + 'px'
    } else {
      element.style.top = '90px'
    }
  })
}

export function getTocbot() {
  const tocContainer = document.getElementById('toc')
  const hasOldContent = tocContainer && tocContainer.children.length > 0

  if (hasOldContent) {
    const tocElements = document.querySelectorAll('.toc')
    tocElements.forEach((el) => {
      el.style.transition = 'opacity 0.15s ease-out'
      el.style.opacity = '0.3'
    })
  }

  if (window.tocActiveObserver) {
    try {
      window.tocActiveObserver.disconnect()
    } catch (e) {}
    window.tocActiveObserver = null
  }

  if (window.tocbot) {
    try {
      window.tocbot.destroy()
    } catch (e) {}
  }

  const initTocbot = () => {
    this.$nextTick(() => {
      const entryContent = document.querySelector('.entry-content')
      if (!entryContent) {
        setTimeout(() => initTocbot(), 50)
        return
      }

      const headings = entryContent.querySelectorAll('h1, h2, h3, h4, h5')
      if (headings.length === 0) {
        setTimeout(() => initTocbot(), 50)
        return
      }

      if (window.tocActiveObserver) {
        try {
          window.tocActiveObserver.disconnect()
        } catch (e) {}
        window.tocActiveObserver = null
      }

      if (window.tocbot) {
        try {
          window.tocbot.destroy()

          window.tocbot.init({
            tocSelector: '#toc',
            contentSelector: '.entry-content',
            headingSelector: 'h1, h2, h3, h4, h5',
            scrollSmooth: true,
            fixedSidebarOffset: 'auto',
            scrollSmoothOffset: -100,
            hasInnerContainers: false,
            headingsOffset: 100,
            scrollSmoothDuration: 420,
            includeHtml: false,
          })

          formatTocLinks()

          // Active link scroll synchronization
          const tocElement = document.querySelector('.toc')
          if (tocElement) {
            if (window.tocActiveObserver) {
              window.tocActiveObserver.disconnect()
            }
            const observer = new MutationObserver((mutations) => {
              let activeLinkChanged = false
              for (const mutation of mutations) {
                if (mutation.attributeName === 'class') {
                  const target = mutation.target
                  if (
                    target.classList.contains('is-active-link') ||
                    target.classList.contains('is-collapsible')
                  ) {
                    activeLinkChanged = true
                    break
                  }
                }
              }
              if (activeLinkChanged) {
                scrollActiveTocLinkIntoView()
                setTimeout(scrollActiveTocLinkIntoView, 150)
                setTimeout(scrollActiveTocLinkIntoView, 320)
              }
            })
            observer.observe(tocElement, {
              subtree: true,
              attributes: true,
              attributeFilter: ['class'],
            })
            window.tocActiveObserver = observer
          }

          this.$nextTick(() => {
            const tocElement = document.querySelector('.toc')
            if (tocElement) {
              const tocTitle = getTocTitle(this.currentLang || 'zh')
              const rawEmoji = getTocEmoji(this.articleThemeConfig)
              const prefix = rawEmoji === null ? '🏖️' : rawEmoji
              tocElement.setAttribute('data-toc-title', `${prefix}${tocTitle}`)
            }
          })

          this.$nextTick(() => {
            emitPluginHook('onArticleRender', {
              articleId: this.article.id,
              title: this.article.articleTitle,
              element: document.querySelector('.entry-content'),
            })
          })

          const forceReflow = () => {
            const toc = document.getElementById('toc')
            const content = document.querySelector('.entry-content')
            if (toc) void toc.offsetHeight
            if (content) void content.offsetHeight
          }

          this.$nextTick(() => {
            forceReflow()
            this.syncTocPosition()
            if (window.tocbot && window.tocbot.refresh) {
              window.tocbot.refresh()
            }
            formatTocLinks()

            requestAnimationFrame(() => {
              const tocElements = document.querySelectorAll('.toc')
              tocElements.forEach((el) => {
                el.style.transition = 'opacity 0.2s ease-in'
                el.style.opacity = '1'
              })
            })
          })
        } catch (e) {
          const tocElements = document.querySelectorAll('.toc')
          tocElements.forEach((el) => {
            el.style.opacity = '1'
          })
        }
      } else {
        setTimeout(() => initTocbot(), 50)
      }
    })
  }

  if (window.tocbot) {
    initTocbot()
  } else {
    const existingScript = document.querySelector(
      `script[src="${this.$constant.tocbot}"]`
    )
    if (existingScript) {
      existingScript.addEventListener('load', initTocbot)
    } else {
      let script = document.createElement('script')
      script.type = 'text/javascript'
      script.src = this.$constant.tocbot
      script.onload = initTocbot
      script.onerror = () => {}

      const head = document.getElementsByTagName('head')[0]
      if (
        script &&
        script.nodeType === Node.ELEMENT_NODE &&
        head &&
        typeof head.appendChild === 'function'
      ) {
        try {
          head.appendChild(script)
        } catch (e) {}
      }
    }
  }

  if (this.$common.mobile()) {
    this.$nextTick(() => {
      const tocElements = document.querySelectorAll('.toc')
      tocElements.forEach((element) => {
        element.style.display = 'none'
      })
      this.syncTocPosition()
    })
  }
}
