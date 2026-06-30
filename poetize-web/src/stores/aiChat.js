/**
 * AI聊天 - Pinia Store
 * Vue2.7版本
 */
import { defineStore } from 'pinia'
import constant from '@/utils/constant'
import {
  saveImage,
  getMessageImages,
  getBatchMessageImages,
  associateImagesToMessage,
  deleteImage,
  deleteMessageImages,
  clearAllImages,
  cleanupOldImages,
} from '@/utils/aiImageStorage'
import {
  saveMessages as saveMessagesToIDB,
  replaceAllMessages as replaceAllMessagesInIDB,
  getAllMessages as getAllMessagesFromIDB,
  clearMessages as clearMessagesFromIDB,
} from '@/utils/aiHistoryStorage'
import { calculateTotalSizeMB } from '@/utils/imageCompress'

// 混合存储配置
// localStorage 只保留最近 HOT_MESSAGE_COUNT 条热数据，保证启动快、写入不卡顿
// IndexedDB 存储完整历史，突破 localStorage 5-10MB 容量限制
const HOT_MESSAGE_COUNT = 50
// saveHistory 防抖间隔（毫秒），避免流式输出时频繁全量写入
const SAVE_HISTORY_DEBOUNCE_MS = 300
let saveHistoryTimer = null

const DEFAULT_AI_CHAT_CONFIG = {
  enabled: false,
  configured: false,
  chat_name: 'AI助手',
  chat_avatar: '',
  welcome_message: '你好！我是你的AI助手，有什么可以帮助你的吗？',
  placeholder_text: '输入你想说的话...',
  theme_color: '#4facfe',
  streaming_enabled: false,
  enable_streaming: false,
  enable_typing_indicator: true,
  show_timestamp: true,
  require_login: false,
  max_message_length: 500,
  max_conversation_length: 20,
  rate_limit: 20,
  vision_supported: false,
  vision_configured: false,
}

const hasOwn = (object, key) => Object.prototype.hasOwnProperty.call(object, key)

const pickConfigValue = (config, snakeKey, camelKey, fallback) => {
  if (hasOwn(config, snakeKey)) {
    return config[snakeKey] ?? fallback
  }
  if (hasOwn(config, camelKey)) {
    return config[camelKey] ?? fallback
  }
  return fallback
}

const normalizeAIChatConfig = (config = {}) => {
  if (!config || typeof config !== 'object') {
    return { ...DEFAULT_AI_CHAT_CONFIG }
  }

  const normalized = {
    ...DEFAULT_AI_CHAT_CONFIG,
    ...config,
    chat_name: pickConfigValue(config, 'chat_name', 'chatName', DEFAULT_AI_CHAT_CONFIG.chat_name),
    chat_avatar: pickConfigValue(config, 'chat_avatar', 'chatAvatar', DEFAULT_AI_CHAT_CONFIG.chat_avatar),
    welcome_message: pickConfigValue(config, 'welcome_message', 'welcomeMessage', DEFAULT_AI_CHAT_CONFIG.welcome_message),
    placeholder_text: pickConfigValue(config, 'placeholder_text', 'placeholderText', DEFAULT_AI_CHAT_CONFIG.placeholder_text),
    theme_color: pickConfigValue(config, 'theme_color', 'themeColor', DEFAULT_AI_CHAT_CONFIG.theme_color),
    streaming_enabled: pickConfigValue(config, 'streaming_enabled', 'enableStreaming', DEFAULT_AI_CHAT_CONFIG.streaming_enabled),
    enable_streaming: pickConfigValue(config, 'enable_streaming', 'enableStreaming', DEFAULT_AI_CHAT_CONFIG.enable_streaming),
    enable_typing_indicator: pickConfigValue(config, 'enable_typing_indicator', 'enableTypingIndicator', DEFAULT_AI_CHAT_CONFIG.enable_typing_indicator),
    show_timestamp: pickConfigValue(config, 'show_timestamp', 'showTimestamp', DEFAULT_AI_CHAT_CONFIG.show_timestamp),
    require_login: pickConfigValue(config, 'require_login', 'requireLogin', DEFAULT_AI_CHAT_CONFIG.require_login),
    max_message_length: pickConfigValue(config, 'max_message_length', 'maxMessageLength', DEFAULT_AI_CHAT_CONFIG.max_message_length),
    max_conversation_length: pickConfigValue(config, 'max_conversation_length', 'maxConversationLength', DEFAULT_AI_CHAT_CONFIG.max_conversation_length),
    rate_limit: pickConfigValue(config, 'rate_limit', 'rateLimit', DEFAULT_AI_CHAT_CONFIG.rate_limit),
    vision_supported: pickConfigValue(config, 'vision_supported', 'visionSupported', DEFAULT_AI_CHAT_CONFIG.vision_supported),
    vision_configured: pickConfigValue(config, 'vision_configured', 'visionConfigured', DEFAULT_AI_CHAT_CONFIG.vision_configured),
  }

  if (!hasOwn(config, 'enable_streaming') && hasOwn(config, 'streaming_enabled')) {
    normalized.enable_streaming = config.streaming_enabled
  }

  return normalized
}

/**
 * 规范化工具调用 arguments 字段为 JSON 字符串。
 * segments 中 arguments 可能是字符串、对象、null/undefined。
 * 后端必须收到合法 JSON 字符串，否则模型 API 400 (unexpected character)。
 */
function normalizeToolArguments(raw) {
  if (raw == null) return '{}'
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (trimmed === '' || trimmed === 'null') return '{}'
    return trimmed
  }
  try {
    return JSON.stringify(raw)
  } catch {
    return '{}'
  }
}

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
    editingOriginalAttachedImages: null,
    editingOriginalAttachedDocuments: null,
    // 已写入 IndexedDB 的消息数（用于增量写入判断）
    lastSavedCount: 0,
    // 上次响应收到的历史哈希（服务端 Redis 缓存末尾哈希）。
    // 存在且与 messageHistory 末尾一致时，下次请求只发增量（仅末尾 1-2 条新消息）；
    // 撤回/编辑/清空场景置 null，强制走完整历史同步。
    lastHistoryHash: null,
    // 上次 lastHistoryHash 对应的历史条数，用于判断本次是否仅追加（确定增量切片起点）
    lastSyncedHistoryLength: 0,
    attachedPageContext: null,
    attachedImages: [],
    attachedDocuments: [],
    // Jina Reader 排队轮询 interval ID
    jinaQueuePolling: null,
  }),

  getters: {
    requireLogin: (state) => {
      const requireLogin = state.config?.require_login || false
      return requireLogin
    },
    isStreamingEnabled: (state) => {
      return state.config?.streaming_enabled === true
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
        .filter((msg) => allowedRoles.includes(msg.role))
        .slice(-maxLength)
        .map((msg) => {
          const entry = {
            role: msg.role,
            content: msg.content,
          }
          // 仅对 assistant 携带 toolCalls，让后端重建为
          // AssistantMessage(toolCalls) + ToolResponseMessage 序列，
          // 模型在多轮对话中能"看到"之前工具调用与结果，避免重复调用 / 凭空猜测 / 缓存失效。
          if (msg.role === 'assistant' && Array.isArray(msg.segments)) {
            const toolCalls = msg.segments
              .filter(
                (seg) =>
                  seg &&
                  seg.type === 'tool' &&
                  seg.tool &&
                  (seg.status === 'completed' || seg.status === 'failed')
              )
              .map((seg) => ({
                id: seg.id != null ? String(seg.id) : '',
                tool: seg.tool,
                arguments: normalizeToolArguments(seg.arguments),
                result: seg.result ?? '',
                error: seg.error ?? '',
                status: seg.status || 'completed',
              }))
            if (toolCalls.length > 0) {
              entry.toolCalls = toolCalls
            }
          }
          return entry
        })
    },
    /**
     * 增量协议：根据 lastHistoryHash 决定返回完整 history 或增量切片。
     * 调用方据此决定 baseHistoryHash 上送值。
     * - lastHistoryHash 存在且消息仅追加（messages 数组长度 > lastSyncedHistoryLength
     *   且前 lastSyncedHistoryLength 条未被改动）→ 返回增量切片
     * - 否则 → 返回完整 history
     */
    requestHistoryPack: (state) => {
      const full = state.messages
        .filter((msg) => msg.role === 'user' || msg.role === 'assistant')
        .slice(0) // 不在此处截断，保留完整长度用于增量判断；服务端会做截断
        .map((msg) => {
          const entry = { role: msg.role, content: msg.content }
          if (msg.role === 'assistant' && Array.isArray(msg.segments)) {
            const toolCalls = msg.segments
              .filter(
                (seg) =>
                  seg &&
                  seg.type === 'tool' &&
                  seg.tool &&
                  (seg.status === 'completed' || seg.status === 'failed')
              )
              .map((seg) => ({
                id: seg.id != null ? String(seg.id) : '',
                tool: seg.tool,
                arguments: normalizeToolArguments(seg.arguments),
                result: seg.result ?? '',
                error: seg.error ?? '',
                status: seg.status || 'completed',
              }))
            if (toolCalls.length > 0) entry.toolCalls = toolCalls
          }
          return entry
        })

      // 没有 hash 或历史长度倒退/不变 → 完整同步
      if (!state.lastHistoryHash) {
        // eslint-disable-next-line no-console
        console.info('[hist-sync] FULL no-hash', {
          fullLen: full.length,
        })
        return { history: full, baseHistoryHash: null }
      }
      const synced = state.lastSyncedHistoryLength
      if (full.length < synced) {
        // 撤回/编辑后变短：完整重发
        // eslint-disable-next-line no-console
        console.info('[hist-sync] FULL truncated', {
          fullLen: full.length,
          syncedLen: synced,
        })
        return { history: full, baseHistoryHash: null }
      }
      if (full.length === synced) {
        // 没有新消息：发空 history + hash，服务端可正常取缓存（极少触发）
        // eslint-disable-next-line no-console
        console.info('[hist-sync] EMPTY no-new-msg', {
          fullLen: full.length,
          syncedLen: synced,
        })
        return { history: [], baseHistoryHash: state.lastHistoryHash }
      }
      // 仅追加：取末尾增量
      const incremental = full.slice(synced)
      // eslint-disable-next-line no-console
      console.info('[hist-sync] INCREMENTAL', {
        fullLen: full.length,
        syncedLen: synced,
        incrementalLen: incremental.length,
        hash: state.lastHistoryHash,
      })
      return { history: incremental, baseHistoryHash: state.lastHistoryHash }
    },
    /** 是否允许上传图片：主模型支持视觉 或 已配置视觉模型工具 */
    visionEnabled: (state) => {
      return state.config?.vision_supported === true || state.config?.vision_configured === true
    },
    /** 主模型原生支持视觉（决定前端是否构造多模态展示） */
    visionNative: (state) => {
      return state.config?.vision_supported === true
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
      this._registerUnloadHandler()
    },

    lightInit() {
      if (!this.configLoaded) {
        this.restoreCachedConfig()
      }
      this.restoreHistory()
      this.checkUserLogin()
      this.loadConfig().catch((error) => {
        console.warn('轻量加载AI聊天配置失败:', error)
      })
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
          {
            credentials: 'include',
          }
        )

        if (response.ok) {
          const result = await response.json()
          if (result.code === 200 && result.data) {
            this.config = normalizeAIChatConfig(result.data)
            this.configLoaded = true
            localStorage.setItem('ai_chat_config', JSON.stringify(this.config))
          } else {
            throw new Error(result.message || '配置加载失败')
          }
        } else {
          throw new Error('配置加载失败')
        }
      } catch {
        if (this.restoreCachedConfig()) {
          this.configLoaded = true
        } else {
          this.config = { ...DEFAULT_AI_CHAT_CONFIG }
          this.configLoaded = true
        }
      }
    },

    async refreshConfig() {
      this.configLoaded = false
      await this.loadConfig()
    },

    restoreCachedConfig() {
      try {
        const cached = localStorage.getItem('ai_chat_config')
        if (!cached) {
          return false
        }

        this.config = normalizeAIChatConfig(JSON.parse(cached))
        return true
      } catch {
        localStorage.removeItem('ai_chat_config')
        return false
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
          queueInfo: null,
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

    /**
     * 判断是否为网页访问工具（支持 camelCase 和 snake_case 命名）。
     */
    isWebFetchTool(toolName) {
      if (!toolName) return false
      const lower = toolName.toLowerCase()
      return lower === 'fetchwebpage' || lower === 'fetch_web_page'
    },

    /**
     * 启动 Jina Reader 排队状态轮询。
     * 在 fetchWebPage 工具执行期间，每 2 秒查询一次队列状态，
     * 若有排队则更新工具 segment 的 queueInfo 供 UI 展示。
     */
    startJinaQueuePolling(messageId) {
      this.stopJinaQueuePolling()

      // 使用 session 对象作为取消令牌，避免 setTimeout 重叠和旧轮询泄漏
      const session = { active: true, timeoutId: null }
      this.jinaQueuePolling = session

      const poll = async () => {
        if (!session.active) return
        try {
          const resp = await fetch(
            `${constant.baseURL}/ai/jina-queue/status`,
            { method: 'GET', credentials: 'include' }
          )
          if (!resp.ok) return
          const json = await resp.json()
          if (!json.success || !json.data) return

          const data = json.data
          if (!data.queueActive) {
            this.stopJinaQueuePolling()
            return
          }

          // 队列有排队请求，更新工具 segment
          const message = this.messages.find((m) => m.id === messageId)
          if (!message) {
            this.stopJinaQueuePolling()
            return
          }

          const segment = message.segments
            ?.slice()
            .reverse()
            .find(
              (s) =>
                s.type === 'tool' &&
                this.isWebFetchTool(s.tool) &&
                s.status === 'executing'
            )

          if (!segment) {
            this.stopJinaQueuePolling()
            return
          }

          // 估算排队位置：队列中最后一条最可能是当前请求（后入队者排后面）
          const entries = data.entries || []
          const queueSize = data.queueSize || entries.length
          const maxWaitMs = data.maxEstimatedWaitMs || 0

          segment.queueInfo = {
            queueSize,
            maxEstimatedWaitMs: maxWaitMs,
            maxEstimatedWaitSec: Math.ceil(maxWaitMs / 1000),
          }
        } catch (e) {
          // 轮询失败静默忽略，不打断聊天
        }
        if (session.active) {
          session.timeoutId = setTimeout(poll, 2000)
        }
      }

      // 立即执行一次
      poll()
    },

    /**
     * 停止 Jina Reader 排队状态轮询。
     */
    stopJinaQueuePolling() {
      const session = this.jinaQueuePolling
      if (session) {
        session.active = false
        if (session.timeoutId) {
          clearTimeout(session.timeoutId)
        }
        this.jinaQueuePolling = null
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
      // 携带图片附件到用户消息（用于前端展示）
      const pendingImages = (this.attachedImages || []).slice()
      if (pendingImages.length > 0) {
        messageMetadata.images = pendingImages.map((img) => img.url)
        messageMetadata.imageIds = pendingImages.map((img) => img.imageId).filter(Boolean)
      }
      // 携带文档附件到用户消息（用于前端展示，仅保留元信息，不携带全文）
      const pendingDocuments = (this.attachedDocuments || []).slice()
      if (pendingDocuments.length > 0) {
        messageMetadata.documents = pendingDocuments.map((doc) => ({
          name: doc.name,
          size: doc.size,
          type: doc.type,
        }))
      }
      const userMessage = this.addMessage(content, 'user', messageMetadata)

      // 将图片关联到用户消息（持久化到 IndexedDB）
      if (messageMetadata.imageIds && messageMetadata.imageIds.length > 0) {
        associateImagesToMessage(String(userMessage.id), messageMetadata.imageIds).catch(
          (err) => console.error('关联图片到消息失败:', err)
        )
      }

      this.checkUserLogin()

      if (this.requireLogin && !this.currentUser) {
        return {
          success: false,
          error: 'require_login',
          message: '需要登录后才能使用聊天功能',
        }
      }

      // 速率限制校验放在内容过滤与登录校验之后，避免被拦截的消息也计数
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

      try {
        if (this.isStreamingEnabled) {
          return await this.sendStreamingMessage(
            content,
            messageMetadata.images || [],
            pendingDocuments
          )
        }
        return await this.sendNormalMessage(
          content,
          messageMetadata.images || [],
          pendingDocuments
        )
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
          this.extractMainContentText() || this.extractBodyTextExcludingChat()

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

    /**
     * 提取页面主内容文本，自动排除 AI 聊天面板、看板娘等与正文无关的 DOM，
     * 避免聊天历史污染页面上下文。
     */
    extractBodyTextExcludingChat() {
      try {
        const EXCLUDE_SELECTOR =
          '#waifu, #waifu-tool, #waifu-chat, .ai-chat-panel, .waifu-toggle, .waifu-tips, .live2d-container, [role="dialog"][aria-modal="true"]'
        const SKIP_TAGS = new Set([
          'SCRIPT',
          'STYLE',
          'TEMPLATE',
          'SVG',
          'NOSCRIPT',
          'IFRAME',
          'CANVAS',
        ])
        const BLOCK_TAGS = new Set([
          'DIV',
          'P',
          'H1',
          'H2',
          'H3',
          'H4',
          'H5',
          'H6',
          'LI',
          'TR',
          'SECTION',
          'ARTICLE',
          'HEADER',
          'FOOTER',
          'NAV',
          'ASIDE',
          'BLOCKQUOTE',
          'PRE',
          'BR',
          'HR',
        ])

        // 收集需要排除的子树根节点（聊天框/看板娘等）
        const excludedRoots = Array.from(
          document.body.querySelectorAll(EXCLUDE_SELECTOR)
        )
        const excluded = new Set()
        excludedRoots.forEach((root) => {
          excluded.add(root)
          root.querySelectorAll('*').forEach((el) => excluded.add(el))
        })

        const chunks = []
        const walker = document.createTreeWalker(
          document.body,
          NodeFilter.SHOW_TEXT,
          {
            acceptNode(node) {
              const parent = node.parentNode
              if (!parent) return NodeFilter.FILTER_REJECT
              if (SKIP_TAGS.has(parent.tagName)) {
                return NodeFilter.FILTER_REJECT
              }
              if (excluded.has(parent)) {
                return NodeFilter.FILTER_REJECT
              }
              const text = node.nodeValue
              if (!text || !text.trim()) {
                return NodeFilter.FILTER_REJECT
              }
              return NodeFilter.FILTER_ACCEPT
            },
          }
        )

        let node
        while ((node = walker.nextNode())) {
          const text = node.nodeValue.replace(/\s+/g, ' ').trim()
          if (!text) continue
          // 在块级元素边界插入换行，保留基本结构
          const parent = node.parentNode
          if (parent && BLOCK_TAGS.has(parent.tagName)) {
            if (chunks.length > 0) chunks.push('\n')
          }
          chunks.push(text)
        }
        return chunks.join(' ').replace(/\n{3,}/g, '\n\n').trim()
      } catch (error) {
        console.error('提取页面文本失败:', error)
        return ''
      }
    },

    /**
     * 优先从语义化的主内容容器提取文本，绕开聊天框污染。
     * 仅当容器存在且非聊天面板本身时才返回。
     */
    extractMainContentText() {
      try {
        const candidates = [
          'main',
          '#main',
          '.main-content',
          '.page-content',
          '.post-content',
          '.article-content',
          '.entry-content',
          '#content',
          '.content-area',
        ]
        for (const sel of candidates) {
          const el = document.querySelector(sel)
          // 排除把聊天面板自身误判为主内容的情况
          if (el && !el.closest('#waifu-chat, .ai-chat-panel, #waifu')) {
            const text = el.innerText?.trim()
            if (text) return text
          }
        }
        return ''
      } catch (error) {
        return ''
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

    /**
     * 添加图片附件（base64 dataUrl，存入 IndexedDB）
     * @param {string} dataUrl base64 data URL
     * @param {string} name 图片名称（可选）
     * @param {number} size 原始字节大小（可选）
     * @returns {Promise<boolean>} 是否添加成功
     */
    async attachImage(dataUrl, name = '', size = 0) {
      if (!dataUrl) return false
      // 限制最多 4 张图片
      if (this.attachedImages.length >= 4) {
        return false
      }
      // 校验总大小不超过 20MB
      const currentTotal = calculateTotalSizeMB(
        this.attachedImages.map((img) => img.url)
      )
      const newTotal = calculateTotalSizeMB([dataUrl])
      if (currentTotal + newTotal > 20) {
        return false
      }
      try {
        const imageId = await saveImage(
          dataUrl,
          dataUrl.substring(dataUrl.indexOf(':') + 1, dataUrl.indexOf(';')),
          size || Math.round((dataUrl.length * 3) / 4)
        )
        this.attachedImages.push({ url: dataUrl, name, imageId })
        return true
      } catch (error) {
        console.error('保存图片到 IndexedDB 失败:', error)
        // 即使 IndexedDB 失败也允许发送（base64 已在内存中）
        this.attachedImages.push({ url: dataUrl, name, imageId: null })
        return true
      }
    },

    /**
     * 上传图片到服务端压缩，返回 base64 dataUrl 后存入 IndexedDB
     * @param {File} file 图片文件
     * @returns {Promise<boolean>} 是否添加成功
     */
    async attachImageFile(file) {
      if (!file || !file.type.startsWith('image/')) {
        return false
      }
      // 限制单张 5MB
      if (file.size > 5 * 1024 * 1024) {
        return false
      }
      try {
        const formData = new FormData()
        formData.append('file', file)
        const response = await fetch(
          `${constant.baseURL}/ai/chat/compressImage`,
          {
            method: 'POST',
            body: formData,
            credentials: 'include',
          }
        )
        const result = await response.json()
        if (!result.success || !result.data || !result.data.dataUrl) {
          console.error('服务端图片压缩失败:', result.message)
          return false
        }
        return await this.attachImage(
          result.data.dataUrl,
          file.name,
          result.data.compressedSize
        )
      } catch (error) {
        console.error('上传图片压缩失败:', error)
        return false
      }
    },

    /**
     * 移除指定索引的图片附件
     */
    async removeAttachedImage(index) {
      if (index < 0 || index >= this.attachedImages.length) return
      const img = this.attachedImages[index]
      if (img && img.imageId) {
        deleteImage(img.imageId).catch((err) =>
          console.error('从 IndexedDB 删除图片失败:', err)
        )
      }
      this.attachedImages.splice(index, 1)
    },

    /**
     * 清空所有图片附件
     */
    clearAttachedImages() {
      // 注意：这里只清空当前附件列表，不删 IndexedDB 中已关联到消息的图片
      this.attachedImages = []
    },

    /**
     * 上传文档到后端解析，返回文本内容后存入 store
     * @param {File} file 文档文件
     * @returns {Promise<{success: boolean, error?: string}>} 解析结果与错误信息
     */
    async attachDocumentFile(file) {
      if (!file) return { success: false, error: '文件为空' }
      // 限制单个文档 20MB
      if (file.size > 20 * 1024 * 1024) {
        return { success: false, error: '文档大小不能超过20MB' }
      }
      // 限制最多 4 个文档
      if (this.attachedDocuments.length >= 4) {
        return { success: false, error: '最多只能附加4个文档' }
      }

      // 先插入占位项，标记 parsing 状态
      const placeholderIndex = this.attachedDocuments.length
      this.attachedDocuments.push({
        name: file.name,
        size: file.size,
        type: file.type || '',
        content: '',
        parsing: true,
        error: '',
      })

      try {
        const formData = new FormData()
        formData.append('file', file)
        const response = await fetch(
          `${constant.baseURL}/ai/chat/parseDocument`,
          {
            method: 'POST',
            body: formData,
            credentials: 'include',
          }
        )
        const result = await response.json()
        if (!result.success || !result.data || !result.data.content) {
          const errMsg = result.message || '文档解析失败'
          this.attachedDocuments[placeholderIndex] = {
            name: file.name,
            size: file.size,
            type: file.type || '',
            content: '',
            parsing: false,
            error: errMsg,
          }
          return { success: false, error: errMsg }
        }
        this.attachedDocuments[placeholderIndex] = {
          name: file.name,
          size: file.size,
          type: file.type || result.data.mimeType || '',
          content: result.data.content,
          parsing: false,
          error: '',
        }
        return { success: true }
      } catch (error) {
        console.error('文档解析失败:', error)
        this.attachedDocuments[placeholderIndex] = {
          name: file.name,
          size: file.size,
          type: file.type || '',
          content: '',
          parsing: false,
          error: error.message || '网络错误',
        }
        return { success: false, error: error.message || '网络错误' }
      }
    },

    /**
     * 移除指定索引的文档附件
     */
    removeAttachedDocument(index) {
      if (index < 0 || index >= this.attachedDocuments.length) return
      this.attachedDocuments.splice(index, 1)
    },

    /**
     * 清空所有文档附件
     */
    clearAttachedDocuments() {
      this.attachedDocuments = []
    },

    /**
     * 发送同步聊天请求的内部实现。供 sendNormalMessage 在 cacheMiss 时重试复用。
     * 不处理 cacheMiss 重试，仅返回原始响应。
     */
    async _postNormal(content, images, documents) {
      const historyPack = this.requestHistoryPack
      const response = await fetch(`${constant.baseURL}/ai/chat/sendMessage`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          message: content,
          conversationId: 'default',
          history: historyPack.history,
          baseHistoryHash: historyPack.baseHistoryHash,
          userId: this.currentUser?.id || 'anonymous',
          pageContext: this.attachedPageContext,
          currentPage: this.attachedPageContext ? null : this.extractCurrentPageContent(),
          images: images,
          documents: documents.map((doc) => ({
            name: doc.name,
            type: doc.type,
            size: doc.size,
            content: doc.content,
          })),
        }),
        signal: this.abortController.signal,
      })
      return await response.json()
    },

    async sendNormalMessage(content, images = [], documents = []) {
      this.typing = true
      this.shouldStop = false
      this.abortController = new AbortController()

      try {
        let result = await this._postNormal(content, images, documents)

        // cacheMiss 重试：服务端告知前端上送的 baseHistoryHash 已失效，
        // 前端清空 lastHistoryHash 后用完整历史重试一次（防死循环，仅重试 1 次）
        if (result.success && result.data?.cacheMiss) {
          // eslint-disable-next-line no-console
          console.warn('[hist-sync] cacheMiss 收到，清空 hash 并用完整历史重试一次')
          this.lastHistoryHash = null
          this.lastSyncedHistoryLength = 0
          result = await this._postNormal(content, images, documents)
        }

        this.typing = false

        if (result.success && result.data?.content) {
          const aiMessage = this.addMessage(result.data.content, 'assistant')
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
          if (this.attachedImages && this.attachedImages.length > 0) {
            this.attachedImages = []
          }
          if (this.attachedDocuments && this.attachedDocuments.length > 0) {
            this.attachedDocuments = []
          }
          // 同步增量协议状态：记录服务端返回的 hash 与本次同步后的历史长度，
          // 下次请求时若仅追加消息即可走增量发送。
          if (result.data.historyHash) {
            this.lastHistoryHash = result.data.historyHash
            this.lastSyncedHistoryLength = this.messages.filter(
              (m) => m.role === 'user' || m.role === 'assistant'
            ).length
          }
          return {
            success: true,
            response: result.data.content,
          }
        }
        throw new Error(result.message || '未知错误')
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

    async sendStreamingMessage(content, images = [], documents = [], isRetry = false) {
      this.typing = true
      this.streaming = true
      this.shouldStop = false
      this.abortController = new AbortController()

      let cacheMissReceived = false

      try {
        const historyPack = this.requestHistoryPack
        const response = await fetch(
          `${constant.baseURL}/ai/chat/sendMessageStream`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            credentials: 'include',
            body: JSON.stringify({
              message: content,
              conversationId: 'default',
              history: historyPack.history,
              baseHistoryHash: historyPack.baseHistoryHash,
              userId: this.currentUser?.id || 'anonymous',
              pageContext: this.attachedPageContext,
              currentPage: this.attachedPageContext ? null : this.extractCurrentPageContent(),
              images: images,
              documents: documents.map((doc) => ({
                name: doc.name,
                type: doc.type,
                size: doc.size,
                content: doc.content,
              })),
            }),
            signal: this.abortController.signal,
          }
        )

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

                // cacheMiss 事件：服务端告知前端上送的 baseHistoryHash 已失效。
                // 标记后跳出循环，外层会清空 lastHistoryHash 并用完整历史重试一次
                if (currentEventName === 'cacheMiss') {
                  cacheMissReceived = true
                  // eslint-disable-next-line no-console
                  console.warn('[hist-sync] cacheMiss(stream) 收到，准备用完整历史重试一次')
                  reader.cancel()
                  break
                }

                if (
                  currentEventName === 'start' ||
                  currentEventName === 'complete'
                ) {
                  if (currentEventName === 'complete' && aiMessage) {
                    this.finishMessageReasoning(aiMessage.id)
                    // 同步增量协议状态：记录服务端返回的 hash 与本次同步后的历史长度，
                    // 下次请求时若仅追加消息即可走增量发送。
                    if (eventData && eventData.historyHash) {
                      this.lastHistoryHash = eventData.historyHash
                      this.lastSyncedHistoryLength = this.messages.filter(
                        (m) => m.role === 'user' || m.role === 'assistant'
                      ).length
                    }
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

                  const toolName = toolData.tool || '未知工具'
                  this.addOrUpdateToolEvent(aiMessage.id, {
                    type: 'call',
                    tool: toolName,
                    arguments: toolData.arguments ?? null,
                    status: toolData.status || 'executing',
                  })
                  // 网页访问工具执行期间启动 Jina 排队状态轮询
                  if (this.isWebFetchTool(toolName)) {
                    this.startJinaQueuePolling(aiMessage.id)
                  }
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

                  // 网页访问工具完成时停止 Jina 排队轮询
                  if (this.isWebFetchTool(toolData.tool)) {
                    this.stopJinaQueuePolling()
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
                  // AI 已通过 get_current_page 工具获取当前页面：
                  // 自动标记为已附加，避免用户再次手动附加导致页面内容在上下文中重复出现。
                  // 后续轮次将走「已附加」通道（currentPage=null），AI 不再重复调用工具。
                  if (
                    toolData.tool === 'get_current_page' &&
                    (toolData.status || 'completed') === 'completed' &&
                    !this.attachedPageContext
                  ) {
                    const resultStr = String(toolData.result ?? '')
                    if (
                      resultStr.startsWith('当前页面信息') &&
                      !resultStr.includes('没有可提取的文本内容')
                    ) {
                      this.attachedPageContext = this.extractCurrentPageContent()
                    }
                  }
                  continue
                }

                if (currentEventName === 'memory_search') {
                  const searchData = eventData.data || eventData
                  const requestId = searchData.requestId
                  const query = searchData.query || ''
                  const limit = searchData.limit || 10

                  if (!aiMessage) {
                    this.typing = false
                    aiMessage = this.addMessage('', 'assistant', {
                      streaming: true,
                    })
                    firstChunkReceived = true
                  }

                  this.addOrUpdateToolEvent(aiMessage.id, {
                    type: 'call',
                    tool: 'search_memory',
                    arguments: JSON.stringify({ query, limit }),
                    status: 'executing',
                  })
                  await this.flushStreamingToolState()

                  // 搜索本地历史（IndexedDB/localStorage），返回 JSON（text + images）
                  const searchResult = await this.searchLocalMemory(query, limit)

                  // POST 结果回后端，解除工具阻塞
                  try {
                    await fetch(
                      `${constant.baseURL}/ai/chat/memorySearchResult`,
                      {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'include',
                        body: JSON.stringify({
                          requestId,
                          result: searchResult,
                        }),
                      }
                    )

                    this.addOrUpdateToolEvent(aiMessage.id, {
                      type: 'result',
                      tool: 'search_memory',
                      result: searchResult,
                      status: 'completed',
                    })
                  } catch (searchErr) {
                    console.error('记忆搜索结果提交失败:', searchErr)
                    this.addOrUpdateToolEvent(aiMessage.id, {
                      type: 'result',
                      tool: 'search_memory',
                      error: searchErr.message || '搜索失败',
                      status: 'failed',
                    })
                  }
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
        this.stopJinaQueuePolling()

        // cacheMiss 重试：服务端告知 baseHistoryHash 已失效，清空 hash 并用完整历史重试一次
        // 仅允许重试一次（isRetry），避免服务端持续 cacheMiss 导致递归栈溢出
        if (cacheMissReceived && !isRetry) {
          this.lastHistoryHash = null
          this.lastSyncedHistoryLength = 0
          // 无条件移除本次流创建的 assistant 消息（可能已收到 reasoning/tool_call 段，
          // 若保留会在重试后产生重复消息）
          if (aiMessage) {
            const idx = this.messages.findIndex((m) => m.id === aiMessage.id)
            if (idx >= 0) {
              this.messages.splice(idx, 1)
            }
          }
          // 重置状态后重试一次（不带 baseHistoryHash → 走完整历史），标记 isRetry=true
          return this.sendStreamingMessage(content, images, documents, true)
        }

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

        if (this.attachedImages && this.attachedImages.length > 0) {
          this.attachedImages = []
        }

        if (this.attachedDocuments && this.attachedDocuments.length > 0) {
          this.attachedDocuments = []
        }

        return {
          success: true,
          response: fullText,
        }
      } catch (error) {
        this.typing = false
        this.streaming = false
        this.stopJinaQueuePolling()

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

    /**
     * 搜索本地聊天历史 — 供 MemorySearchTool 两轮调用使用。
     * 基于关键词匹配 this.messages（当前会话内存中的全部消息）
     * 中的消息内容，返回相关历史片段。
     * <p>
     * 图片处理：搜索结果中相关消息的图片会从 IndexedDB 加载 base64 数据，
     * 一并返回给后端。后端调用视觉模型识别图片内容，让 AI 能"看到"历史图片。
     * 限制最多返回 2 张图片，避免结果过大。
     */
    async searchLocalMemory(query, limit = 10) {
      try {
        const queryLower = (query || '').toLowerCase().trim()
        if (!queryLower) {
          return JSON.stringify({ text: '搜索关键词为空，未找到相关记忆。', images: [] })
        }

        // 分词：按空格/标点拆分，过滤过短的词
        const keywords = queryLower
          .split(/[\s,，。.!！?？;；:：、]+/)
          .filter((k) => k.length > 1)

        if (keywords.length === 0) {
          keywords.push(queryLower)
        }

        // 搜索当前内存中的全部消息
        const candidates = this.messages
          .filter((msg) => msg && typeof msg.content === 'string' && msg.content.trim())
          .map((msg, index) => {
            const content = msg.content.toLowerCase()
            let score = 0
            for (const kw of keywords) {
              if (content.includes(kw)) {
                score += kw.length
              }
            }
            return { msg, index, score }
          })
          .filter((item) => item.score > 0)
          .sort((a, b) => b.score - a.score)
          .slice(0, limit)

        if (candidates.length === 0) {
          return JSON.stringify({ text: `未找到与 "${query}" 相关的对话记忆。`, images: [] })
        }

        // 格式化文字结果
        let textResult = `找到 ${candidates.length} 条相关记忆：\n\n`
        candidates.forEach((item, i) => {
          const role = item.msg.role === 'user' ? '用户' : 'AI'
          const content = item.msg.content
          const truncated =
            content.length > 500
              ? content.substring(0, 500) + '...'
              : content

          const imageCount =
            (item.msg.imageIds && item.msg.imageIds.length) ||
            (item.msg.images && item.msg.images.length) ||
            0
          const imageMarker = imageCount > 0 ? ` [包含${imageCount}张图片]` : ''

          const timeStr = item.msg.timestamp
            ? new Date(item.msg.timestamp).toLocaleString('zh-CN', {
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
              })
            : ''

          textResult += `${i + 1}. [${role}]${timeStr ? `(${timeStr})` : ''}${imageMarker} ${truncated}\n\n`
        })

        // 从 IndexedDB 加载相关消息的图片（最多 2 张，避免结果过大）
        const images = []
        const MAX_IMAGES = 2
        for (const item of candidates) {
          if (images.length >= MAX_IMAGES) break
          // 只加载用户消息的图片
          if (item.msg.role !== 'user') continue
          const imageIds = item.msg.imageIds || []
          if (imageIds.length === 0) continue

          try {
            const msgImages = await getMessageImages(String(item.msg.id))
            for (const dataUrl of msgImages) {
              if (images.length >= MAX_IMAGES) break
              // 限制单张图片大小（base64 字符串长度不超过 1.5M，约 1MB 原始数据）
              if (dataUrl && dataUrl.length < 1500000) {
                images.push(dataUrl)
              }
            }
          } catch (e) {
            console.warn('加载历史图片失败:', e)
          }
        }

        return JSON.stringify({ text: textResult, images })
      } catch (error) {
        console.error('搜索本地记忆失败:', error)
        return JSON.stringify({
          text: '记忆搜索失败：' + (error.message || '未知错误'),
          images: [],
        })
      }
    },

    saveHistory() {
      // 防抖：流式输出/频繁更新时合并写入，避免主线程抖动
      if (saveHistoryTimer) {
        clearTimeout(saveHistoryTimer)
      }
      saveHistoryTimer = setTimeout(() => {
        saveHistoryTimer = null
        this._flushHistory()
      }, SAVE_HISTORY_DEBOUNCE_MS)
    },

    /**
     * 立即取消防抖并同步执行持久化。
     * 用于页面卸载（pagehide）等必须立即落盘的场景。
     * 注意：IndexedDB 异步写入无法在卸载时保证完成，至少保证 localStorage 热数据不丢。
     */
    flushNow() {
      if (saveHistoryTimer) {
        clearTimeout(saveHistoryTimer)
        saveHistoryTimer = null
      }
      this._flushHistory()
    },

    /**
     * 注册页面卸载监听器，确保防抖中的待写数据不丢失。
     * 使用 pagehide 而非 beforeunload：在移动端和 bfcache 场景下更可靠。
     * 用标记位避免重复注册；不使用 once，确保从 bfcache 恢复后再次隐藏仍能 flush。
     */
    _registerUnloadHandler() {
      if (typeof window === 'undefined' || this._unloadRegistered) {
        return
      }
      this._unloadRegistered = true
      window.addEventListener('pagehide', () => {
        this.flushNow()
      })
    },

    /**
     * 只写 localStorage 热数据（不含 IndexedDB 写入）。
     * 供 _flushHistory 和 saveEditAndResend 复用。
     */
    _flushLocalStorage() {
      try {
        const hot = this.messages.slice(-HOT_MESSAGE_COUNT).map((msg) => {
          const { images, imageIds, documents, ...rest } = msg
          const slim = { ...rest }
          if (imageIds && imageIds.length > 0) {
            slim.imageIds = imageIds
          }
          if (documents && documents.length > 0) {
            slim.documents = documents
          }
          return slim
        })
        localStorage.setItem('ai_chat_history', JSON.stringify(hot))
      } catch (error) {
        console.error('localStorage 写入失败，降级处理:', error)
        try {
          const hot = this.messages.slice(-Math.ceil(HOT_MESSAGE_COUNT / 2)).map((msg) => {
            const { images, imageIds, documents, ...rest } = msg
            const slim = { ...rest }
            if (imageIds && imageIds.length > 0) {
              slim.imageIds = imageIds
            }
            if (documents && documents.length > 0) {
              slim.documents = documents
            }
            return slim
          })
          localStorage.setItem('ai_chat_history', JSON.stringify(hot))
        } catch (fallbackError) {
          console.error('localStorage 降级写入仍失败，仅依赖 IndexedDB:', fallbackError)
        }
      }
    },

    /**
     * 实际执行持久化的方法（由 saveHistory 防抖触发）。
     * 混合存储策略：
     * - localStorage：只写最近 HOT_MESSAGE_COUNT 条（含 imageIds 引用），保证启动快
     * - IndexedDB：增量写入，只 put 新增的消息；消息数减少时全量替换
     */
    _flushHistory() {
      const totalCount = this.messages.length

      // 1. localStorage 只保留最近 N 条热数据
      this._flushLocalStorage()

      // 2. IndexedDB 增量写入
      if (totalCount < this.lastSavedCount) {
        // 消息数减少（截断场景）：全量替换，删除被丢弃的旧记录
        const toSave = this.messages.map((msg) => {
          const { images, imageIds, documents, ...rest } = msg
          const slim = { ...rest }
          if (imageIds && imageIds.length > 0) {
            slim.imageIds = imageIds
          }
          if (documents && documents.length > 0) {
            slim.documents = documents
          }
          return slim
        })
        replaceAllMessagesInIDB(toSave)
          .then(() => { this.lastSavedCount = this.messages.length })
          .catch((error) => console.error('IndexedDB 替换历史失败:', error))
      } else if (totalCount > this.lastSavedCount) {
        // 消息数增加：只 put 新增的消息，避免全量写入
        const newMessages = this.messages.slice(this.lastSavedCount).map((msg) => {
          const { images, imageIds, documents, ...rest } = msg
          const slim = { ...rest }
          if (imageIds && imageIds.length > 0) {
            slim.imageIds = imageIds
          }
          if (documents && documents.length > 0) {
            slim.documents = documents
          }
          return slim
        })
        if (newMessages.length > 0) {
          saveMessagesToIDB(newMessages)
            .then(() => { this.lastSavedCount = this.messages.length })
            .catch((error) => console.error('IndexedDB 增量写入失败:', error))
        }
      } else if (totalCount > 0) {
        // 消息数没变但内容可能变了（流式输出 append、reasoning 更新等）：
        // put 最后一条消息，覆盖流式输出期间的内容变更
        const lastMsg = this.messages[totalCount - 1]
        if (lastMsg) {
          const { images, imageIds, documents, ...rest } = lastMsg
          const slim = { ...rest }
          if (imageIds && imageIds.length > 0) {
            slim.imageIds = imageIds
          }
          if (documents && documents.length > 0) {
            slim.documents = documents
          }
          saveMessagesToIDB([slim]).catch((error) =>
            console.error('IndexedDB 更新最后一条消息失败:', error)
          )
        }
      }
    },

    /**
     * 用当前内存中的消息完全替换 IndexedDB 中的历史。
     * 用于编辑重发等截断场景：saveMessages 用 put 不会删除被截断的旧记录，
     * 必须用 replaceAllMessages 原子清空+重写，保证 IndexedDB 与内存一致。
     */
    _replaceHistoryInIDB() {
      const toSave = this.messages.map((msg) => {
        const { images, imageIds, documents, ...rest } = msg
        const slim = { ...rest }
        if (imageIds && imageIds.length > 0) {
          slim.imageIds = imageIds
        }
        if (documents && documents.length > 0) {
          slim.documents = documents
        }
        return slim
      })
      replaceAllMessagesInIDB(toSave)
        .then(() => { this.lastSavedCount = this.messages.length })
        .catch((error) => {
          console.error('IndexedDB 替换历史失败:', error)
        })
    },

    async restoreHistory() {
      // 阶段 1：同步从 localStorage 恢复最近热数据，让界面快速显示
      const allowedRoles = ['user', 'assistant']
      let hotMessages = []
      try {
        const saved = localStorage.getItem('ai_chat_history')
        if (saved) {
          const parsed = JSON.parse(saved)
          hotMessages = (parsed || []).filter(
            (msg) => msg && typeof msg.content === 'string' && allowedRoles.includes(msg.role)
          )
        }
      } catch (error) {
        console.error('从 localStorage 恢复热数据失败:', error)
      }

      // 先把热数据挂到内存（图片稍后异步加载），界面可立即渲染
      this.messages = hotMessages.map((msg) => ({ ...msg, isNew: false }))
      for (const msg of this.messages) {
        if (msg.role === 'assistant') {
          this.ensureMessageStructure(msg)
          this.syncToolEvents(msg)
        }
      }

      // 阶段 2：异步从 IndexedDB 加载完整历史，与热数据合并
      // 如果 IndexedDB 条数更多，说明有更早的冷数据需要补齐到前面
      try {
        const allMessages = await getAllMessagesFromIDB()
        if (allMessages.length > 0) {
          const safeAll = allMessages.filter(
            (msg) => msg && typeof msg.content === 'string' && allowedRoles.includes(msg.role)
          )
          // 按 id 去重：以 IndexedDB 全量为准，覆盖 localStorage 的热数据
          const idSet = new Set()
          const merged = []
          for (const msg of safeAll) {
            const key = String(msg.id)
            if (!idSet.has(key)) {
              idSet.add(key)
              merged.push({ ...msg, isNew: false })
            }
          }
          // 按 timestamp 升序排序，保证历史顺序正确
          merged.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0))
          this.messages = merged

          for (const msg of this.messages) {
            if (msg.role === 'assistant') {
              this.ensureMessageStructure(msg)
              this.syncToolEvents(msg)
            }
          }
        }
      } catch (error) {
        console.error('从 IndexedDB 恢复完整历史失败，仅使用 localStorage 热数据:', error)
      }

      // 同步 lastSavedCount，避免 _flushHistory 重复写入已存在的消息
      this.lastSavedCount = this.messages.length

      // 阶段 3：批量加载图片（单事务，避免 N 次独立事务）
      const msgIdsWithImages = this.messages
        .filter((msg) => msg.imageIds && msg.imageIds.length > 0)
        .map((msg) => String(msg.id))
      if (msgIdsWithImages.length > 0) {
        getBatchMessageImages(msgIdsWithImages)
          .then((imageMap) => {
            for (const msg of this.messages) {
              const urls = imageMap.get(String(msg.id))
              if (urls && urls.length > 0) {
                msg.images = urls
              }
            }
          })
          .catch((err) => console.warn('批量加载消息图片失败:', err))
      }

      // 清理超过 7 天的旧图片
      cleanupOldImages(7).catch((err) =>
        console.error('清理旧图片失败:', err)
      )
    },

    async clearHistory() {
      // 取消可能挂起的防抖写入，避免清空后又把旧数据写回
      if (saveHistoryTimer) {
        clearTimeout(saveHistoryTimer)
        saveHistoryTimer = null
      }
      this.messages = []
      this.lastSavedCount = 0
      // 清空缓存哈希：下轮将走完整历史同步
      this.lastHistoryHash = null
      this.lastSyncedHistoryLength = 0
      localStorage.removeItem('ai_chat_history')
      // 必须等待 IndexedDB 清空完成后再添加 welcome 消息，
      // 否则 addWelcomeMessage 触发的防抖写入可能与 clear 事务竞态，welcome 被清掉
      try {
        await Promise.all([
          clearMessagesFromIDB(),
          clearAllImages(),
        ])
      } catch (err) {
        console.error('清空 IndexedDB 失败:', err)
      }
      this.addWelcomeMessage()
    },

    startEditMessage(messageId, content) {
      this.editingMessageId = messageId
      this.editingContent = content

      const message = this.messages.find((m) => m.id === messageId)

      // 加载原消息的图片到编辑器
      if (message && Array.isArray(message.images) && message.images.length > 0) {
        this.editingOriginalAttachedImages = this.attachedImages.slice()
        this.attachedImages = message.images.map((url, index) => ({
          url,
          name: '图片',
          imageId: message.imageIds?.[index] || null,
        }))
      } else {
        this.editingOriginalAttachedImages = null
      }

      // 加载原消息的文档到编辑器（仅元信息，不携带全文）
      // 历史消息未持久化文档全文，编辑重发时需提示用户重新上传
      if (message && Array.isArray(message.documents) && message.documents.length > 0) {
        this.editingOriginalAttachedDocuments = this.attachedDocuments.slice()
        this.attachedDocuments = message.documents.map((doc) => ({
          name: doc.name,
          size: doc.size,
          type: doc.type,
          content: doc.content || '',
          parsing: false,
          error: doc.content ? null : '需重新上传以恢复内容',
        }))
      } else {
        this.editingOriginalAttachedDocuments = null
      }

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

      if (this.editingOriginalAttachedImages) {
        this.attachedImages = this.editingOriginalAttachedImages
        this.editingOriginalAttachedImages = null
      }

      if (this.editingOriginalAttachedDocuments) {
        this.attachedDocuments = this.editingOriginalAttachedDocuments
        this.editingOriginalAttachedDocuments = null
      } else {
        this.attachedDocuments = []
      }

      if (this.editingOriginalAttachedPage) {
        this.attachedPageContext = null
        this.editingOriginalAttachedPage = null
      }
    },

    updateMessageContent(messageId, newContent) {
      const message = this.messages.find((m) => m.id === messageId)
      if (message) {
        message.content = newContent
        // 消息数没变时 _flushHistory 只会覆盖最后一条；编辑的可能是中间消息，
        // 因此直接单独 put 被修改的消息，避免 saveHistory 造成重复写入。
        const { images, imageIds, ...rest } = message
        const slim = { ...rest }
        if (imageIds && imageIds.length > 0) {
          slim.imageIds = imageIds
        }
        saveMessagesToIDB([slim]).catch((error) =>
          console.error('IndexedDB 更新消息内容失败:', error)
        )
      }
    },

    async saveEditAndResend() {
      if (!this.editingMessageId) return

      const messageIndex = this.messages.findIndex(
        (m) => m.id === this.editingMessageId
      )
      if (messageIndex === -1) return

      // === 校验前置：任何校验失败都保持原状态，用户可通过 cancelEdit 恢复 ===
      const content = this.editingContent

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

      const documents = (this.attachedDocuments || []).slice()

      // 阻止在文档解析中发送消息：避免发送空内容文档并丢失正在解析的附件
      if (documents.some((doc) => doc.parsing)) {
        return {
          success: false,
          error: 'document_parsing',
          message: '文档正在解析中，请稍候再发送',
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

      // === 校验通过，开始执行不可逆的状态修改 ===
      this.messages[messageIndex].content = content
      if (this.attachedPageContext) {
        this.messages[messageIndex].attachedPage = {
          title: this.attachedPageContext.title,
          type: this.attachedPageContext.type,
          url: this.attachedPageContext.url,
          author: this.attachedPageContext.author,
        }
      }

      // 同步更新消息的图片附件（用户可能在编辑时删除/保留图片）
      // 解除 Vue reactive 包装，避免 IndexedDB/localStorage 克隆失败
      const editedImages = this.attachedImages.map((img) => img.url).slice()
      const editedImageIds = this.attachedImages
        .map((img) => img.imageId)
        .filter(Boolean)
        .slice()
      this.messages[messageIndex].images = editedImages
      this.messages[messageIndex].imageIds = editedImageIds

      // 同步更新消息的文档附件（用户可能在编辑时删除/保留文档）
      const editedDocuments = (this.attachedDocuments || [])
        .slice()
        .map((doc) => ({
          name: doc.name,
          size: doc.size,
          type: doc.type,
        }))
      this.messages[messageIndex].documents = editedDocuments

      this.messages = this.messages.slice(0, messageIndex + 1)
      // 截断历史后必须失效增量缓存：被删除的消息不再属于历史，
      // 下次请求需走完整历史同步，避免服务端 Redis 缓存拼接出错误序列
      this.lastHistoryHash = null
      this.lastSyncedHistoryLength = 0
      // 编辑重发截断历史：取消防抖，直接同步写 localStorage + 立即替换 IndexedDB
      // 避免 saveHistory 防抖 300ms 后 _flushHistory 与 _replaceHistoryInIDB 重复写入
      if (saveHistoryTimer) {
        clearTimeout(saveHistoryTimer)
        saveHistoryTimer = null
      }
      this._flushLocalStorage()
      this._replaceHistoryInIDB()
      // 同步图片与消息的关联关系
      const editedMessageId = String(this.messages[messageIndex].id)
      if (editedImageIds.length > 0) {
        associateImagesToMessage(editedMessageId, editedImageIds).catch((err) =>
          console.error('编辑后关联图片到消息失败:', err)
        )
      } else {
        deleteMessageImages(editedMessageId).catch((err) =>
          console.error('编辑后删除消息图片关联失败:', err)
        )
      }

      this.editingMessageId = null
      this.editingContent = ''
      this.editingOriginalAttachedPage = null
      this.editingOriginalAttachedImages = null
      this.editingOriginalAttachedDocuments = null

      const images = this.attachedImages.map((img) => img.url)

      // 过滤掉无内容的文档（如编辑加载的历史文档未重新上传）
      const documentsToSend = documents.filter((doc) => doc.content && doc.content.trim())

      try {
        if (this.isStreamingEnabled) {
          return await this.sendStreamingMessage(content, images, documentsToSend)
        }
        return await this.sendNormalMessage(content, images, documentsToSend)
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
      this.stopJinaQueuePolling()
    },
  },
})
