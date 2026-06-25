/**
 * Live2D看板娘 - Pinia Store
 * Vue2.7版本
 */
import { defineStore } from 'pinia'
import constant from '@/utils/constant'
import { fetchLive2DAssetStatus, getDefaultLive2DBaseUrl, normalizeLive2DBaseUrl } from '@/utils/live2dAssets'

export const useLive2DStore = defineStore('live2d', {
  state: () => ({
    // 基础状态
    enabled: false,
    visible: true,
    initialized: false,

    // 模型相关
    currentModelId: 0,
    currentTextureId: 0,
    modelList: null,
    modelLoading: false,
    assetStatus: null,
    modelBaseUrl: getDefaultLive2DBaseUrl(),

    // 位置和交互
    position: (() => {
      const saved = localStorage.getItem('waifu-position')
      if (!saved) return { x: null, y: null }
      const parsed = JSON.parse(saved)
      // 如果保存的是{x:0, y:0}，也认为是未设置（历史数据兼容）
      if (parsed.x === 0 && parsed.y === 0) return { x: null, y: null }
      return parsed
    })(),
    dragging: false,

    // 聊天窗口
    showChat: false,

    // AI聊天按钮位置（用于决定面板展开动画方向）
    // null 表示未拖动过，使用默认位置（左下角）
    aiButtonPosition: null,

    // 提示消息
    messageQueue: [],
    currentMessage: null,
    messageTimer: null,
  }),

  getters: {
    /**
     * 是否处于激活状态
     */
    isActive: (state) => state.enabled && state.visible,

    /**
     * 是否可以交互
     */
    canInteract: (state) => state.enabled && state.visible && !state.dragging,

    /**
     * 当前模型信息
     */
    currentModel: (state) => {
      if (!state.modelList || !state.modelList.models) return null
      const models = state.modelList.models[state.currentModelId]
      if (!models) return null
      // 兼容字符串和数组格式
      return Array.isArray(models)
        ? models[state.currentTextureId] || models[0]
        : models
    },

    /**
     * 当前模型的消息配置
     */
    currentModelMessages: (state) => {
      if (!state.modelList || !state.modelList.messages) return null
      return state.modelList.messages[state.currentModelId]
    },

    /**
     * 当前模型的缩放比例
     */
    currentModelScale: (state) => {
      if (!state.modelList || !state.modelList.scales) return 1.0
      return state.modelList.scales[state.currentModelId] || 1.0
    },
  },

  actions: {
    /**
     * 初始化看板娘
     */
    async init() {
      try {
        // 检查屏幕尺寸 - 已移除限制，允许移动端显示
        // if (window.screen.width <= 768) {
        //   this.enabled = false
        //   return false
        // }

        // 检查后端状态
        const enabled = await this.checkEnabled()
        this.enabled = enabled

        if (!enabled) {
          return false
        }

        // 检查用户是否手动关闭
        const displayTime = localStorage.getItem('waifu-display')
        if (displayTime && Date.now() - parseInt(displayTime) <= 86400000) {
          this.visible = false
          return false
        }

        this.initialized = true

        return true
      } catch (error) {
        console.error('Live2D Store初始化失败:', error)
        this.enabled = false
        return false
      }
    },

    /**
     * 从后端检查是否启用看板娘
     */
    async checkEnabled() {
      try {
        const response = await fetch(
          constant.baseURL + '/webInfo/getWaifuStatus'
        )
        const result = await response.json()

        if (result.code === 200) {
          return result.data?.enableWaifu === true
        }

        // 降级：从localStorage读取
        const webInfo = localStorage.getItem('webInfo')
        if (webInfo) {
          const data = JSON.parse(webInfo)
          return data.data?.enableWaifu === true || data.enableWaifu === true
        }

        return false
      } catch (error) {
        console.error('检查看板娘状态失败:', error)
        return false
      }
    },

    /**
     * 获取 Live2D 模型资源状态，本地包不存在时使用 CDN。
     */
    async loadAssetStatus(force = false) {
      if (this.assetStatus && !force) {
        return this.assetStatus
      }

      const status = await fetchLive2DAssetStatus()
      this.assetStatus = status
      this.modelBaseUrl = normalizeLive2DBaseUrl(status.modelBaseUrl)
      return status
    },

    /**
     * 加载模型列表
     * 优先从后端获取激活的看板娘模型配置
     */
    async loadModelList() {
      await this.loadAssetStatus()

      // 首先尝试从后端获取所有启用的看板娘模型
      try {
        const response = await fetch(
          constant.baseURL + '/sysPlugin/getAllWaifuModels'
        )
        const result = await response.json()

        if (result.code === 200 && Array.isArray(result.data) && result.data.length > 0) {
          const models = []
          const messages = []
          const scales = []

          // 直接使用后端返回的顺序（后端已将激活的插件排在第一位）
          const plugins = result.data

          plugins.forEach(plugin => {
            if (plugin.pluginConfig) {
              try {
                const config = JSON.parse(plugin.pluginConfig)
                // live2d-widget expect models to be an array of "model/path" strings OR array of arrays for textures
                // 这里的 config.textures 通常是 ["Potion-Maker/Pio"] 这种路径
                models.push(config.textures && config.textures.length > 0 ? config.textures : [config.modelPath])
                // 结构化存储消息和配置
                messages.push({
                  greeting: config.messages?.greeting || ['你好呀~'],
                  idle: config.messages?.idle || []
                })
                scales.push(config.scale || 1.0)
              } catch (e) {
                console.error('解析看板娘配置失败:', e)
              }
            }
          })

          this.modelList = {
            models: models,
            messages: messages,
            scales: scales
          }

          // 如果没有持久化的modelId，或者modelId越界，重置为0
          // 也可以考虑获取"active"的插件并找到它的index作为默认值，这里简化为重置或保留
          if (this.currentModelId >= models.length) {
            this.currentModelId = 0
            this.currentTextureId = 0
            this.currentModelId = 0
            this.currentTextureId = 0
          }

          console.log('从后端加载看板娘模型列表成功, 总数:', models.length)
          return this.modelList
        }
      } catch (e) {
        console.warn('从后端获取看板娘模型列表失败，使用本地配置:', e)
      }

      // 降级：使用本地 model_list.json
      const modelBaseUrl = normalizeLive2DBaseUrl(this.modelBaseUrl)
      const cacheKey = `model-list-cache:${modelBaseUrl}`
      const cacheTimeKey = `model-list-cache-time:${modelBaseUrl}`
      const cacheDuration = 24 * 60 * 60 * 1000 // 1天

      const cachedTime = localStorage.getItem(cacheTimeKey)
      const now = Date.now()

      if (cachedTime && now - parseInt(cachedTime) < cacheDuration) {
        try {
          const cachedData = localStorage.getItem(cacheKey)
          if (cachedData) {
            this.modelList = JSON.parse(cachedData)
            return this.modelList
          }
        } catch (e) {
          console.error('缓存解析失败:', e)
        }
      }

      try {
        const cdnPath = modelBaseUrl
        const response = await fetch(`${cdnPath}model_list.json?t=${now}`)

        if (!response.ok) {
          throw new Error(`模型列表响应错误: ${response.status}`)
        }

        this.modelList = await response.json()

        // 更新缓存
        localStorage.setItem(cacheKey, JSON.stringify(this.modelList))
        localStorage.setItem(cacheTimeKey, now.toString())

        return this.modelList
      } catch (error) {
        console.error('模型列表加载失败:', error)

        // 使用备用数据
        this.modelList = {
          models: [['HyperdimensionNeptunia/blanc_swimwear']],
          messages: ['我是备用模型'],
        }

        return this.modelList
      }
    },

    /**
     * 加载指定模型
     */
    async loadModel(modelId, message) {
      if (!this.modelList) {
        await this.loadModelList()
      }

      this.modelLoading = true
      this.currentModelId = modelId

      // 验证并重置材质ID（确保不超过新模型的材质数量）
      const models = this.modelList.models[modelId]
      const maxTextureId = Array.isArray(models) ? models.length - 1 : 0
      if (this.currentTextureId > maxTextureId) {
        this.currentTextureId = 0
      }

      if (message) {
        this.showMessage(message, 4000, 10)
      } else {
        // 如果没有传入特定消息，尝试显示配置的问候语
        const messageConfig = this.currentModelMessages
        if (messageConfig && messageConfig.greeting) {
          this.showMessage(messageConfig.greeting, 4000, 10)
        }
      }

      // 触发模型加载（实际渲染由Canvas组件处理）

      this.modelLoading = false
    },

    /**
     * 切换材质
     */
    changeTexture() {
      if (!this.modelList) return

      const textures = this.modelList.models[this.currentModelId]
      // 检查是否是数组且有多个材质
      if (!Array.isArray(textures) || textures.length <= 1) {
        this.showMessage('当前模型没有其他材质哦～', 3000, 10)
        return
      }

      this.currentTextureId = (this.currentTextureId + 1) % textures.length

      this.showMessage('新衣服好看吗？', 3000, 10)
      this.loadModel(this.currentModelId)
    },

    /**
     * 随机切换模型
     */
    async loadRandomModel() {
      if (!this.modelList) {
        await this.loadModelList()
      }

      if (!this.modelList || !Array.isArray(this.modelList.models)) return

      const modelCount = this.modelList.models.length
      if (modelCount <= 1) {
        this.showMessage('暂时没有其他角色哦～', 3000, 10)
        return
      }

      const newModelId = (this.currentModelId + 1) % modelCount
      this.currentTextureId = 0

      await this.loadModel(newModelId, '看看我的新造型吧！')
    },

    /**
     * 显示消息
     */
    showMessage(text, timeout = 3000, priority = 5) {
      if (!text) return

      // 优先级检查
      const currentPriority = parseInt(
        sessionStorage.getItem('waifu-text') || '0'
      )
      if (currentPriority > priority) return

      // 清除旧定时器
      if (this.messageTimer) {
        clearTimeout(this.messageTimer)
        this.messageTimer = null
      }

      // 随机选择文本（如果是数组）
      if (Array.isArray(text)) {
        text = text[Math.floor(Math.random() * text.length)]
      }

      this.currentMessage = { text, priority }
      sessionStorage.setItem('waifu-text', priority.toString())

      // 设置自动清除
      this.messageTimer = setTimeout(() => {
        this.currentMessage = null
        sessionStorage.removeItem('waifu-text')
        this.messageTimer = null
      }, timeout)
    },

    /**
     * 清除当前气泡消息
     */
    clearMessage() {
      if (this.messageTimer) {
        clearTimeout(this.messageTimer)
        this.messageTimer = null
      }
      this.currentMessage = null
      sessionStorage.removeItem('waifu-text')
    },

    /**
     * 更新AI聊天按钮位置（拖动时调用）
     */
    updateAiButtonPosition(x, y) {
      this.aiButtonPosition = { x, y }
    },

    /**
     * 切换聊天窗口
     */
    async toggleChat() {
      this.showChat = !this.showChat

      if (this.showChat) {
        this.showMessage('来聊天吧！我很想听听你的想法～', 3000, 8)

        // 延迟加载AI聊天配置（仅在打开聊天窗口时）
        try {
          const { useAIChatStore } = await import('./aiChat')
          const aiChatStore = useAIChatStore()

          // 如果配置未加载，现在加载
          if (!aiChatStore.configLoaded) {
            await aiChatStore.init()
          }
        } catch (error) {
          console.error('加载AI聊天配置失败:', error)
        }
      } else {
        this.showMessage('有什么想聊的随时找我哦！', 3000, 8)
      }
    },

    /**
     * 更新位置
     */
    updatePosition(x, y) {
      this.position = { x, y }
      localStorage.setItem('waifu-position', JSON.stringify(this.position))
    },

    /**
     * 开始拖拽
     */
    startDragging() {
      this.dragging = true
    },

    /**
     * 结束拖拽
     */
    stopDragging() {
      this.dragging = false
    },

    /**
     * 显示看板娘
     */
    show() {
      this.visible = true
      localStorage.removeItem('waifu-display')
    },

    /**
     * 隐藏看板娘
     */
    hide() {
      this.visible = false
      this.showChat = false
      localStorage.setItem('waifu-display', Date.now().toString())
    },
  },
})
