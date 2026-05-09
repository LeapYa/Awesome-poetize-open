/**
 * AI聊天 - Pinia Store
 * Vue2.7版本
 */
import { defineStore } from 'pinia'
import constant from '@/utils/constant'
import { getBrowserFingerprint } from '@/utils/fingerprintUtil'
import { getValidToken } from '@/utils/tokenExpireHandler'

export const useAIChatStore = defineStore('aiChat', {
  state: () => ({
    messages: [],
    config: null,
    configLoaded: false,
    streaming: false,
    typing: false,
    connected: false,
    currentUser: null,
    rateLimitData: {
      count: 0,
      resetTime: 0,
    },
    abortController: null,
    shouldStop: false,
    editingMessageId: null,
    editingContent: '',
    editingOriginalAttachedPage: null,
    attachedPageContext: null,
  }),

  getters: {
    requireLogin: (state) => {
      const requireLogin = state.config?.require_login || false
      return requireLogin
    },
    isStreamingEnabled: (state) => {
      return (
        state.config?.streaming_enabled === true ||
        state.config?.enable_streaming === true
      )
    },
    themeColor: (state) => {
      return state.config?.theme_color || '#4facfe'
    },
    typingAnimationEnabled: (state) => {
      return (
        state.config?.enable_typing_indicator !== false &&
        state.config?.enableTypingIndicator !== false
      )
    },
    showTimestampEnabled: (state) => {
      return (
        state.config?.show_timestamp !== false &&
        state.config?.showTimestamp !== false
      )
    },
    messageHistory: (state) => {
      const maxLength = state.config?.max_conversation_length || 20
      const allowedRoles = ['user', 'assistant']
      return state.messages
        .filter(
          (msg) =>
            allowedRoles.includes(msg.role) &&
            typeof msg.content === 'string' &&
            msg.content.trim()
        )
        .slice(-maxLength)
        .map((msg) => ({
          role: msg.role,
          content: msg.content,
        }))
    },
  },

  actions: {
    async init() {
      await this.loadConfig()
      this.restoreHistory()
      this.checkUserLogin()
      if (this.messages.length === 0) {
        this.addWelcomeMessage()
      }
    },

    lightInit() {
      this.restoreHistory()
      this.checkUserLogin()
    },

    addWelcomeMessage() {
      const welcomeText =
        this.config?.welcome_message ||
        this.config?.welcomeMessage ||
        '你好！我是你的AI助手，有什么可以帮助你的吗？'

      this.addMessage(welcomeText, 'assistant', { isWelcome: true })
    },

    async loadConfig() {
      if (this.configLoaded) {
        return
      }

      try {
        const response = await fetch(
          `${constant.baseURL}/webInfo/ai/config/chat/getStreamingConfig?configName=default`,
          { credentials: 'include' }
        )

        if (response.ok) {
          const result = await response.json()
          if (result.code === 200 && result.data) {
            this.config = result.data
            this.configLoaded = true
            localStorage.setItem('ai_chat_config', JSON.stringify(this.config))
          } else {
            throw new Error(result.message || '配置加载失败')
          }
        } else {
          throw new Error('配置加载失败')
        }
      } catch (error) {
        const cached = localStorage.getItem('ai_chat_config')
        if (cached) {
          this.config = JSON.parse(cached)
          this.configLoaded = true
        } else {
          this.config = {
            chat_name: 'AI助手',
            welcome_message: '你好！我是你的AI助手，有什么可以帮助你的吗？',
            theme_color: '#4facfe',
            streaming_enabled: false,
            enable_typing_indicator: true,
            show_timestamp: true,
            require_login: false,
            max_message_length: 500,
            max_conversation_length: 20,
            rate_limit: 20,
          }
        }
      }
    },

    checkUserLogin() {
      try {
        const userStr =
          localStorage.getItem('currentUser') ||
          sessionStorage.getItem('currentUser')
        if (userStr) {
          this.currentUser = JSON.parse(userStr)
        } else {
          this.currentUser = null
        }
      } catch (error) {
        this.currentUser = null
      }
    },

    createTextSegment(content = '') {
      return {
        id: Date.now() + Math.random(),
        type: 'text',
        content,
      }
    },

    createReasoningSegment(content = '') {
      return {
        id: Date.now() + Math.random(),
        type: 'reasoning',
        content,
        status: 'thinking',
      }
    },

    ensureMessageStructure(message) {
      if (!Array.isArray(message.segments)) {
        message.segments = message.content
          ? [this.createTextSegment(message.content)]
          : []
      }
      if (!Array.isArray(message.toolEvents)) {
        message.toolEvents = []
      }
    },

    syncToolEvents(message) {
      message.toolEvents = (message.segments || [])
        .filter((segment) => segment.type === 'tool')
        .map((segment) => ({ ...segment }))
    },

    addMessage(content, role = 'user', metadata = {}) {
      const message = {
        id: Date.now() + Math.random(),
        role,
        content,
        timestamp: Date.now(),
        isNew: true,
        segments:
          role === 'assistant'
            ? content
              ? [this.createTextSegment(content)]
              : []
            : [],
        toolEvents: [],
        ...metadata,
      }

      this.messages.push(message)
      this.saveHistory()
      return message
    },

    updateMessage(messageId, content) {
      const message = this.messages.find((m) => m.id === messageId)
      if (message) {
        message.content = content
        if (message.role === 'assistant') {
          this.ensureMessageStructure(message)
          const textSegments = message.segments.filter(
            (segment) => segment.type === 'text'
          )
          if (textSegments.length === 0) {
            message.segments.push(this.createTextSegment(content))
          } else {
            textSegments[textSegments.length - 1].content = content
          }
        }
        this.saveHistory()
      }
    },

    appendMessageText(messageId, text) {
      const message = this.messages.find((m) => m.id === messageId)
      if (!message) {
        return
      }

      this.ensureMessageStructure(message)
      message.content += text

      const lastSegment = message.segments[message.segments.length - 1]
      if (lastSegment && lastSegment.type === 'text') {
        lastSegment.content += text
      } else {
        message.segments.push(this.createTextSegment(text))
      }

      this.saveHistory()
    },

    appendMessageReasoning(messageId, text) {
      const message = this.messages.find((m) => m.id === messageId)
      if (!message || !text) {
        return
      }

      this.ensureMessageStructure(message)
      let reasoningSegment = message.segments.find(
        (segment) => segment.type === 'reasoning' && segment.status === 'thinking'
      )

      if (!reasoningSegment) {
        reasoningSegment = this.createReasoningSegment('')
        message.segments.push(reasoningSegment)
      }

      reasoningSegment.content += text
      this.saveHistory()
    },

    finishMessageReasoning(messageId) {
      const message = this.messages.find((m) => m.id === messageId)
      if (!message) {
        return
      }

      this.ensureMessageStructure(message)
      message.segments
        .filter((segment) => segment.type === 'reasoning')
        .forEach((segment) => {
          segment.status = 'completed'
        })
      this.saveHistory()
    },

    addOrUpdateToolEvent(messageId, toolEvent) {
      const message = this.messages.find((m) => m.id === messageId)
      if (!message) {
        return
      }

      this.ensureMessageStructure(message)

      if (toolEvent.type === 'call') {
        message.segments.push({
          id: Date.now() + Math.random(),
          type: 'tool',
          tool: toolEvent.tool,
          arguments: toolEvent.arguments ?? null,
          result: '',
          error: '',
          status: toolEvent.status || 'executing',
          startedAt: Date.now(),
        })
      } else {
        const target = [...message.segments]
          .reverse()
          .find(
            (segment) =>
              segment.type === 'tool' &&
              segment.tool === toolEvent.tool &&
              segment.status === 'executing'
          )

        if (target) {
          target.status = toolEvent.status || 'completed'
          target.result = toolEvent.result ?? ''
          target.error = toolEvent.error ?? ''
        } else {
          message.segments.push({
            id: Date.now() + Math.random(),
            type: 'tool',
            tool: toolEvent.tool,
            arguments: null,
            result: toolEvent.result ?? '',
            error: toolEvent.error ?? '',
            status: toolEvent.status || 'completed',
          })
        }
      }

      this.syncToolEvents(message)
      this.saveHistory()
    },

    async flushStreamingToolState() {
      await Promise.resolve()

      if (
        typeof window !== 'undefined' &&
        typeof window.requestAnimationFrame === 'function'
      ) {
        await new Promise((resolve) => {
          window.requestAnimationFrame(() => resolve())
        })
      }
    },

    async ensureToolIndicatorVisible(messageId, toolName, minimumDuration = 480) {
      const message = this.messages.find((m) => m.id === messageId)
      const toolSegment = message?.segments
        ?.slice()
        .reverse()
        .find(
          (segment) =>
            segment.type === 'tool' &&
            segment.tool === toolName &&
            segment.status === 'executing'
        )

      if (!toolSegment?.startedAt) {
        return
      }

      const remaining = minimumDuration - (Date.now() - toolSegment.startedAt)
      if (remaining > 0) {
        await new Promise((resolve) => setTimeout(resolve, remaining))
      }
    },

    async sendMessage(content) {
      const maxLength = this.config?.max_message_length || 500
      if (content.length > maxLength) {
        return {
          success: false,
          error: 'too_long',
          message: `消息太长了，请控制在${maxLength}个字符以内`,
        }
      }

      if (!this.checkRateLimit()) {
        const remainingTime = Math.ceil(
          (this.rateLimitData.resetTime - Date.now()) / 1000
        )
        return {
          success: false,
          error: 'rate_limit',
          message: `发送频率太快了，请等待${remainingTime}秒后再试`,
        }
      }

      if (this.config?.enable_content_filter) {
        const filtered = this.filterContent(content)
        if (!filtered.pass) {
          return {
            success: false,
            error: 'content_filter',
            message: '请文明聊天，避免使用不当词汇',
          }
        }
      }

      const messageMetadata = {}
      if (this.attachedPageContext) {
        messageMetadata.attachedPage = {
          title: this.attachedPageContext.title,
          type: this.attachedPageContext.type,
          url: this.attachedPageContext.url,
        }
      }
      this.addMessage(content, 'user', messageMetadata)

      this.checkUserLogin()

      if (this.requireLogin && !this.currentUser) {
        return {
          success: false,
          error: 'require_login',
          message: '需要登录后才能使用聊天功能',
        }
      }

      try {
        if (this.isStreamingEnabled) {
          return await this.sendStreamingMessage(content)
        }
        return await this.sendNormalMessage(content)
      } catch (error) {
        console.error('发送消息失败:', error)
        return {
          success: false,
          error: 'network',
          message: '网络错误，请稍后重试',
        }
      }
    },

    extractCurrentPageContent() {
      try {
        const route = window.location.pathname

        if (route.includes('/article/')) {
          const title =
            document.querySelector('.article-title')?.innerText || ''
          const content =
            document.querySelector('.entry-content')?.innerText || ''
          const author =
            document.querySelector('.article-info span')?.innerText || ''

          const languageInfo = this.extractArticleLanguageInfo()
          const maxChars = 8000
          const trimmedContent =
            content.length > maxChars
              ? content.substring(0, maxChars) + '\n...(内容已截断)'
              : content

          return {
            type: 'article',
            title: title.trim(),
            content: trimmedContent.trim(),
            author: author.trim(),
            url: window.location.href,
            ...languageInfo,
          }
        }

        const mainContent =
          document.querySelector('main')?.innerText ||
          document.querySelector('.content')?.innerText ||
          document.querySelector('article')?.innerText ||
          document.body.innerText

        const maxChars = 5000
        const trimmedContent =
          mainContent?.length > maxChars
            ? mainContent.substring(0, maxChars) + '\n...(内容已截断)'
            : mainContent

        return {
          type: 'page',
          title: document.title,
          content: trimmedContent?.trim() || '',
          url: window.location.href,
        }
      } catch (error) {
        console.error('提取页面内容失败:', error)
        return null
      }
    },

    extractArticleLanguageInfo() {
      try {
        const languageInfo = {}
        const htmlLang = document.documentElement.getAttribute('lang')
        if (htmlLang) {
          languageInfo.currentLanguage = htmlLang
        }

        let languageButtons = document.querySelectorAll(
          '.article-language-switch button[data-lang]'
        )

        if (!languageButtons || languageButtons.length === 0) {
          languageButtons = document.querySelectorAll('button[data-lang]')
        }

        if (!languageButtons || languageButtons.length === 0) {
          const allButtons = document.querySelectorAll('.el-button--mini')
          languageButtons = Array.from(allButtons).filter((btn) =>
            btn.hasAttribute('data-lang')
          )
        }

        if (languageButtons && languageButtons.length > 0) {
          const availableLanguages = []
          let sourceLanguage = null
          let currentLanguageButton = null

          languageButtons.forEach((btn) => {
            const langCode = btn.getAttribute('data-lang')
            const langName = btn.textContent?.trim()
            const isPrimary = btn.classList.contains('el-button--primary')

            if (langCode && langName) {
              availableLanguages.push({
                code: langCode,
                name: langName,
              })

              if (!sourceLanguage) {
                sourceLanguage = {
                  code: langCode,
                  name: langName,
                }
              }

              if (isPrimary) {
                currentLanguageButton = {
                  code: langCode,
                  name: langName,
                }
              }
            }
          })

          if (availableLanguages.length > 0) {
            languageInfo.availableLanguages = availableLanguages
            languageInfo.sourceLanguage = sourceLanguage

            if (currentLanguageButton) {
              languageInfo.currentLanguage = currentLanguageButton.code
              languageInfo.currentLanguageName = currentLanguageButton.name
            }
          }
        }

        const urlParams = new URLSearchParams(window.location.search)
        const urlLang = urlParams.get('lang')
        if (urlLang) {
          languageInfo.urlLanguage = urlLang
        }

        return languageInfo
      } catch (error) {
        console.error('提取文章语言信息失败:', error)
        return {}
      }
    },

    attachCurrentPage() {
      const pageContext = this.extractCurrentPageContent()
      if (pageContext) {
        this.attachedPageContext = pageContext
        return true
      }
      return false
    },

    removeAttachedPage() {
      this.attachedPageContext = null
    },

    async buildRequestHeaders() {
      const headers = {
        'Content-Type': 'application/json',
      }

      // Cookie-based auth: no Authorization header needed

      try {
        const fingerprint = await getBrowserFingerprint()
        if (fingerprint) {
          headers['X-Fingerprint'] = fingerprint
        }
      } catch (error) {
        console.warn('获取聊天请求指纹失败:', error)
      }

      return headers
    },

    async sendNormalMessage(content) {
      this.typing = true
      this.shouldStop = false
      this.abortController = new AbortController()

      try {
        const headers = await this.buildRequestHeaders()
        const response = await fetch(`${constant.baseURL}/ai/chat/sendMessage`, {
          method: 'POST',
          headers,
          credentials: 'include',
          body: JSON.stringify({
            message: content,
            conversationId: 'default',
            history: this.messageHistory,
            userId: this.currentUser?.id || 'anonymous',
            pageContext: this.attachedPageContext,
          }),
          signal: this.abortController.signal,
        })

        const result = await response.json().catch(() => null)
        this.typing = false

        const isSuccess =
          response.ok &&
          (result?.code === 200 ||
            result?.success === true ||
            result?.flag === true)

        if (isSuccess && typeof result?.data?.content === 'string') {
          const contentText = result?.data?.content || ''
          const aiMessage = this.addMessage(contentText, 'assistant')
          if (result.data.reasoningContent) {
            aiMessage.segments.unshift({
              ...this.createReasoningSegment(result.data.reasoningContent),
              status: 'completed',
            })
            this.saveHistory()
          }
          if (this.attachedPageContext) {
            this.attachedPageContext = null
          }
          return {
            success: true,
            response: contentText,
          }
        }
        throw new Error(result?.message || '未知错误')
      } catch (error) {
        this.typing = false
        if (error.name === 'AbortError') {
          return {
            success: false,
            cancelled: true,
            message: '已停止生成',
          }
        }
        console.error('发送消息失败:', error)
        throw error
      }
    },

    async sendStreamingMessage(content) {
      this.typing = true
      this.streaming = true
      this.shouldStop = false
      this.abortController = new AbortController()

      try {
        const headers = await this.buildRequestHeaders()
        const response = await fetch(
          `${constant.baseURL}/ai/chat/sendMessageStream`,
          {
            method: 'POST',
            headers,
            credentials: 'include',
            body: JSON.stringify({
              message: content,
              conversationId: 'default',
              history: this.messageHistory,
              userId: this.currentUser?.id || 'anonymous',
              pageContext: this.attachedPageContext,
            }),
            signal: this.abortController.signal,
          }
        )

        if (!response.ok || !response.body) {
          const result = await response.json().catch(() => null)
          throw new Error(result?.message || 'AI 服务暂时不可用，请稍后重试')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let fullText = ''
        let buffer = ''
        let aiMessage = null
        let firstChunkReceived = false
        let currentEventName = null

        while (true) {
          if (this.shouldStop) {
            reader.cancel()
            break
          }

          const { value, done } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          if (lines[lines.length - 1] !== '') {
            buffer = lines.pop()
          } else {
            buffer = ''
          }

          for (const line of lines) {
            const trimmedLine = line.trim()
            if (!trimmedLine) {
              currentEventName = null
              continue
            }

            if (trimmedLine.startsWith('event:')) {
              currentEventName = trimmedLine.substring(6).trim()
            } else if (trimmedLine.startsWith('data:')) {
              const dataStr = trimmedLine.substring(5).trim()
              if (!dataStr) continue

              try {
                const eventData = JSON.parse(dataStr)

                if (
                  currentEventName === 'start' ||
                  currentEventName === 'complete'
                ) {
                  if (currentEventName === 'complete' && aiMessage) {
                    this.finishMessageReasoning(aiMessage.id)
                  }
                  continue
                }

                if (currentEventName === 'error') {
                  const errMsg = eventData.message || '未知错误'
                  console.error('流式响应错误:', errMsg)

                  if (!aiMessage) {
                    this.typing = false
                    aiMessage = this.addMessage('', 'assistant', {
                      streaming: true,
                    })
                    firstChunkReceived = true
                  }

                  this.appendMessageText(aiMessage.id, '\n\n❌ 错误: ' + errMsg)
                  break
                }

                if (currentEventName === 'tool_call') {
                  const toolData = eventData.data || eventData
                  if (!aiMessage) {
                    this.typing = false
                    aiMessage = this.addMessage('', 'assistant', {
                      streaming: true,
                    })
                    firstChunkReceived = true
                  }

                  this.addOrUpdateToolEvent(aiMessage.id, {
                    type: 'call',
                    tool: toolData.tool || '未知工具',
                    arguments: toolData.arguments ?? null,
                    status: toolData.status || 'executing',
                  })
                  await this.flushStreamingToolState()
                  continue
                }

                if (currentEventName === 'reasoning') {
                  if (!aiMessage) {
                    this.typing = false
                    aiMessage = this.addMessage('', 'assistant', {
                      streaming: true,
                    })
                    firstChunkReceived = true
                  }

                  if (eventData.content) {
                    this.appendMessageReasoning(aiMessage.id, eventData.content)
                  }
                  continue
                }

                if (currentEventName === 'tool_result') {
                  const toolData = eventData.data || eventData
                  if (!aiMessage) {
                    this.typing = false
                    aiMessage = this.addMessage('', 'assistant', {
                      streaming: true,
                    })
                    firstChunkReceived = true
                  }

                  await this.ensureToolIndicatorVisible(
                    aiMessage.id,
                    toolData.tool || '未知工具'
                  )
                  this.addOrUpdateToolEvent(aiMessage.id, {
                    type: 'result',
                    tool: toolData.tool || '未知工具',
                    result: toolData.result ?? '',
                    error: toolData.error ?? '',
                    status: toolData.status || 'completed',
                  })
                  continue
                }

                if (eventData.content) {
                  fullText += eventData.content

                  if (!firstChunkReceived) {
                    this.typing = false
                    aiMessage = this.addMessage('', 'assistant', {
                      streaming: true,
                    })
                    firstChunkReceived = true
                  }

                  if (aiMessage) {
                    this.appendMessageText(aiMessage.id, eventData.content)
                  }
                }
              } catch (e) {
                console.error('解析 data JSON 失败:', e, dataStr)
              }
            }
          }
        }

        this.streaming = false
        this.typing = false

        if (aiMessage) {
          const message = this.messages.find((m) => m.id === aiMessage.id)
          if (message) {
            message.streaming = false
          }
          this.finishMessageReasoning(aiMessage.id)
        }

        if (this.attachedPageContext) {
          this.attachedPageContext = null
        }

        return {
          success: true,
          response: fullText,
        }
      } catch (error) {
        this.typing = false
        this.streaming = false

        if (error.name === 'AbortError' || this.shouldStop) {
          return {
            success: false,
            cancelled: true,
            message: '已停止生成',
          }
        }

        console.error('流式消息失败:', error)
        throw error
      }
    },

    checkRateLimit() {
      const now = Date.now()
      const limit = this.config?.rate_limit || 20

      if (now > this.rateLimitData.resetTime) {
        this.rateLimitData = {
          count: 0,
          resetTime: now + 60000,
        }
      }

      if (this.rateLimitData.count >= limit) {
        return false
      }

      this.rateLimitData.count++
      const userId = this.currentUser?.id || 'anonymous'
      localStorage.setItem(
        `chat_rate_limit_${userId}`,
        JSON.stringify(this.rateLimitData)
      )

      return true
    },

    filterContent(content) {
      const badWords = ['垃圾', '傻逼', '废物', '妈的', '草泥马']

      for (const word of badWords) {
        if (content.includes(word)) {
          return { pass: false, word }
        }
      }

      return { pass: true }
    },

    saveHistory() {
      try {
        const maxMessages = 100
        const toSave = this.messages.slice(-maxMessages)
        localStorage.setItem('ai_chat_history', JSON.stringify(toSave))
      } catch (error) {
        console.error('保存聊天历史失败:', error)
      }
    },

    restoreHistory() {
      try {
        const saved = localStorage.getItem('ai_chat_history')
        if (saved) {
          const parsed = JSON.parse(saved)
          // 安全校验：只允许 user/assistant 角色，防止 localStorage 被篡改注入 system 消息
          const allowedRoles = ['user', 'assistant']
          this.messages = parsed.filter(
            (msg) => msg && typeof msg.content === 'string' && allowedRoles.includes(msg.role)
          )
          this.messages.forEach((msg) => {
            msg.isNew = false
            if (msg.role === 'assistant') {
              this.ensureMessageStructure(msg)
              this.syncToolEvents(msg)
              }
            })
        }
      } catch (error) {
        console.error('恢复聊天历史失败:', error)
        this.messages = []
      }
    },

    clearHistory() {
      this.messages = []
      localStorage.removeItem('ai_chat_history')
      this.addWelcomeMessage()
    },

    startEditMessage(messageId, content) {
      this.editingMessageId = messageId
      this.editingContent = content

      const message = this.messages.find((m) => m.id === messageId)
      if (message && message.attachedPage) {
        this.editingOriginalAttachedPage = message.attachedPage
        this.attachedPageContext = {
          title: message.attachedPage.title,
          type: message.attachedPage.type,
          url: message.attachedPage.url,
          content: '',
          author: message.attachedPage.author || '',
        }
      } else {
        this.editingOriginalAttachedPage = null
      }
    },

    cancelEdit() {
      this.editingMessageId = null
      this.editingContent = ''

      if (this.editingOriginalAttachedPage) {
        this.attachedPageContext = null
        this.editingOriginalAttachedPage = null
      }
    },

    updateMessageContent(messageId, newContent) {
      const message = this.messages.find((m) => m.id === messageId)
      if (message) {
        message.content = newContent
        this.saveHistory()
      }
    },

    async saveEditAndResend() {
      if (!this.editingMessageId) return

      const messageIndex = this.messages.findIndex(
        (m) => m.id === this.editingMessageId
      )
      if (messageIndex === -1) return

      this.messages[messageIndex].content = this.editingContent
      if (this.attachedPageContext) {
        this.messages[messageIndex].attachedPage = {
          title: this.attachedPageContext.title,
          type: this.attachedPageContext.type,
          url: this.attachedPageContext.url,
          author: this.attachedPageContext.author,
        }
      }

      this.messages = this.messages.slice(0, messageIndex + 1)
      this.saveHistory()

      const content = this.editingContent
      this.editingMessageId = null
      this.editingContent = ''
      this.editingOriginalAttachedPage = null

      if (!this.checkRateLimit()) {
        const remainingTime = Math.ceil(
          (this.rateLimitData.resetTime - Date.now()) / 1000
        )
        return {
          success: false,
          error: 'rate_limit',
          message: `发送频率太快了，请等待${remainingTime}秒后再试`,
        }
      }

      if (this.config?.enable_content_filter) {
        const filtered = this.filterContent(content)
        if (!filtered.pass) {
          return {
            success: false,
            error: 'content_filter',
            message: '请文明聊天，避免使用不当词汇',
          }
        }
      }

      this.checkUserLogin()
      if (this.requireLogin && !this.currentUser) {
        return {
          success: false,
          error: 'require_login',
          message: '需要登录后才能使用聊天功能',
        }
      }

      try {
        if (this.isStreamingEnabled) {
          return await this.sendStreamingMessage(content)
        }
        return await this.sendNormalMessage(content)
      } catch (error) {
        console.error('重新发送消息失败:', error)
        return {
          success: false,
          error: 'network',
          message: '网络错误，请稍后重试',
        }
      }
    },

    stopGeneration() {
      this.shouldStop = true
      if (this.abortController) {
        this.abortController.abort()
        this.abortController = null
      }
      this.typing = false
      this.streaming = false
    },
  },
})
