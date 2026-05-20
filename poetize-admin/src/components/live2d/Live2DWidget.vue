<template>
  <div v-if="enabled" class="live2d-widget-container">
    <div
      v-if="visible"
      id="waifu"
      class="live2d-widget"
      :class="{
        'live2d-widget--ready': modelReady && !modelLeaving,
        'live2d-widget--leaving': modelLeaving
      }"
      :style="widgetStyle"
    >
      <Live2DTips
        v-if="(modelReady || modelLeaving) && currentMessage"
        :message="currentMessage"
      />

      <Live2DCanvas
        :key="canvasKey"
        :model-id="currentModelId"
        :texture-id="currentTextureId"
        @click="handleCanvasClick"
        @model-loading="handleModelLoading"
        @model-ready="handleModelReady"
        @model-error="handleModelError"
      />

      <Live2DToolbar
        v-if="modelReady || modelLeaving"
        @chat="toggleChat"
        @change-model="handleChangeModel"
        @change-texture="handleChangeTexture"
        @toggle-mouse-animation="handleMouseAnimationToggle"
        @close="hide"
      />
    </div>
    
    <Live2DToggle
      v-show="!visible"
      @click="show"
    />
  </div>
</template>

<script>
import { computed, onMounted, ref } from 'vue'
import { useLive2D } from './composables/useLive2D'
import { useEvents } from './composables/useEvents'
import { useMouseAnimation } from './composables/useMouseAnimation'
import { useLive2DStore } from '@/stores/live2d'
import { preloadLive2DModel } from '@/utils/live2dAssets'

export default {
  name: 'Live2DWidget',
  
  components: {
    Live2DTips: () => import('./Live2DTips.vue'),
    Live2DCanvas: () => import('./Live2DCanvas.vue'),
    Live2DToolbar: () => import('./Live2DToolbar.vue'),
    Live2DToggle: () => import('./Live2DToggle.vue')
  },
  
  setup() {
    const store = useLive2DStore()
    const live2d = useLive2D()
    const modelReady = ref(false)
    const modelLeaving = ref(false)
    const switchingModel = ref(false)
    const pendingReadyMessage = ref('')
    
    // 事件监听
    useEvents()
    
    // 鼠标动画
    const mouseAnimation = useMouseAnimation()
    
    const currentModelId = computed(() => store.currentModelId)
    const currentTextureId = computed(() => store.currentTextureId)
    const canvasKey = computed(() => `${currentModelId.value}-${currentTextureId.value}`)
    const position = computed(() => store.position)
    const modelCount = computed(() => store.modelList?.models?.length || 0)
    const currentTextureCount = computed(() => {
      const textures = store.modelList?.models?.[store.currentModelId]
      return Array.isArray(textures) ? textures.length : 0
    })
    
    const widgetStyle = computed(() => {
      const style = {
        '--waifu-scale': 1,
        '--waifu-size': '280px',
        left: '0px',
        right: 'auto',
        bottom: '0px',
        top: 'auto'
      }
      
      // 如果有保存的X位置，使用保存的位置
      if (position.value.x !== null) {
        style.left = `${position.value.x}px`
        style.right = 'auto'
      }
      // Y 坐标保持底部对齐（使用 CSS 默认的 bottom: 0）

      // 移动端整体缩放（检测触摸设备或小屏幕）
      const isMobile = window.matchMedia('(max-width: 768px)').matches || 
                       window.matchMedia('(hover: none) and (pointer: coarse)').matches
      if (isMobile) {
        style['--waifu-scale'] = 0.7
        style['--waifu-size'] = '200px'
        style.transformOrigin = 'bottom left'
      }
      
      return style
    })
    
    const handleCanvasClick = () => {
      const messages = [
        '好开心你注意到我了！',
        '感谢你的互动！',
        '你好呀！很高兴认识你',
        '哇，你点我了！'
      ]
      live2d.showMessage(messages, 5000, 8)
    }
    
    const handleMouseAnimationToggle = () => {
      const isEnabled = mouseAnimation.toggle()
      live2d.showMessage(
        isEnabled ? '哈哈，要牢记社会主义核心价值观哦！' : '今天你爱国了吗？',
        6000,
        9
      )
    }

    const handleModelLoading = () => {
      modelLeaving.value = false
      modelReady.value = false
    }

    const handleModelReady = () => {
      requestAnimationFrame(() => {
        modelReady.value = true
        modelLeaving.value = false
        switchingModel.value = false

        const readyMessage = pendingReadyMessage.value
        pendingReadyMessage.value = ''

        if (readyMessage) {
          store.clearMessage()
          live2d.showMessage(readyMessage, 4000, 10)
        } else if (store.currentMessage?.text) {
          live2d.showMessage(
            store.currentMessage.text,
            4000,
            store.currentMessage.priority || 10
          )
        } else if (store.currentModelMessages?.greeting) {
          live2d.showMessage(store.currentModelMessages.greeting, 4000, 10)
        }
      })
    }

    const handleModelError = () => {
      modelLeaving.value = false
      modelReady.value = false
      switchingModel.value = false
    }

    const wait = (duration) => {
      return new Promise((resolve) => {
        window.setTimeout(resolve, duration)
      })
    }

    const ensureModelList = async () => {
      if (!store.modelList) {
        await store.loadModelList()
      }
    }

    const getModelPath = (modelId, textureId = 0) => {
      const models = store.modelList?.models?.[modelId]
      if (Array.isArray(models)) {
        return models[textureId] || models[0]
      }
      return models
    }

    const getModelSwitchTarget = async () => {
      await ensureModelList()

      if (modelCount.value <= 1) {
        live2d.showMessage('暂时没有其他角色哦～', 3000, 10)
        return null
      }

      const modelId = (store.currentModelId + 1) % modelCount.value
      return {
        modelId,
        textureId: 0,
        modelPath: getModelPath(modelId, 0),
        loadingText: '新角色',
        readyMessage: '看看我的新造型吧！'
      }
    }

    const getTextureSwitchTarget = async () => {
      await ensureModelList()

      if (currentTextureCount.value <= 1) {
        live2d.showMessage('当前模型没有其他材质哦～', 3000, 10)
        return null
      }

      const textureId = (store.currentTextureId + 1) % currentTextureCount.value
      return {
        modelId: store.currentModelId,
        textureId,
        modelPath: getModelPath(store.currentModelId, textureId),
        loadingText: '新衣服',
        readyMessage: '新衣服好看吗？'
      }
    }

    const buildLoadingMessage = (target, progress) => {
      const percent = Math.max(0, Math.min(100, Math.round(progress)))

      if (percent >= 100) {
        return `${target.loadingText}准备好啦，我先下去一下～`
      }

      if (percent < 20) {
        return `我先找找${target.loadingText}放哪儿了，${percent}%～`
      }

      if (percent < 80) {
        return `${target.loadingText}快准备好了，${percent}%～`
      }

      return `马上就好，${percent}%～`
    }

    const updateLoadingProgress = (target, payload) => {
      live2d.showMessage(
        buildLoadingMessage(target, payload.progress),
        20000,
        12
      )
    }

    const runModelTransition = async (targetFactory) => {
      if (switchingModel.value || !modelReady.value) {
        return
      }

      switchingModel.value = true

      try {
        const target = await targetFactory()

        if (!target || !target.modelPath) {
          switchingModel.value = false
          return
        }

        const assetStatus = await store.loadAssetStatus()
        const useLocalModelOnly = assetStatus?.installed === true
        const { baseUrl, failures } = await preloadLive2DModel(
          store.modelBaseUrl,
          target.modelPath,
          {
            allowRemoteFallback: !useLocalModelOnly,
            onProgress: (payload) => updateLoadingProgress(target, payload)
          }
        )

        if (baseUrl !== store.modelBaseUrl) {
          store.modelBaseUrl = baseUrl
        }

        if (failures.length > 0) {
          console.warn(
            useLocalModelOnly
              ? 'Live2D本地模型预加载失败，不切换CDN:'
              : 'Live2D预加载已切换到可用CDN:',
            { baseUrl, failures }
          )
        }

        updateLoadingProgress(target, {
          progress: 100,
          message: '加载完成，准备切换'
        })

        pendingReadyMessage.value = target.readyMessage
        await wait(250)

        modelLeaving.value = true
        modelReady.value = false
        await wait(700)

        store.clearMessage()
        store.currentTextureId = target.textureId
        store.currentModelId = target.modelId
      } catch (error) {
        console.error('Live2D预加载失败:', error)
        live2d.showMessage('这次没准备好，我先不乱换，等会儿再试～', 4000, 11)
        switchingModel.value = false
        modelLeaving.value = false
        modelReady.value = true
      }
    }

    const handleChangeModel = () => {
      return runModelTransition(getModelSwitchTarget)
    }

    const handleChangeTexture = () => {
      return runModelTransition(getTextureSwitchTarget)
    }
    
    onMounted(async () => {
      await live2d.init()
    })
    
    return {
      enabled: live2d.enabled,
      visible: live2d.visible,
      currentMessage: live2d.currentMessage,
      currentModelId,
      currentTextureId,
      canvasKey,
      modelReady,
      modelLeaving,
      widgetStyle,
      handleCanvasClick,
      handleModelLoading,
      handleModelReady,
      handleModelError,
      handleChangeModel,
      handleChangeTexture,
      toggleChat: live2d.toggleChat,
      handleMouseAnimationToggle,
      hide: live2d.hide,
      show: live2d.show
    }
  }
}
</script>

<style scoped>
.live2d-widget-container {
  position: fixed;
  z-index: 999;
}

#waifu.live2d-widget {
  position: fixed;
  left: 0;
  right: auto;
  bottom: 0;
  top: auto;
  width: var(--waifu-size, 280px);
  height: var(--waifu-size, 280px);
  z-index: 999;
  opacity: 0;
  pointer-events: none;
  transform: translateY(520px) scale(var(--waifu-scale, 1));
  transition: none;
}

#waifu.live2d-widget--ready {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0) scale(var(--waifu-scale, 1));
  animation: waifu-slide-up 1s cubic-bezier(0.22, 1, 0.36, 1) both;
}

#waifu.live2d-widget--leaving {
  opacity: 0;
  pointer-events: none;
  transform: translateY(520px) scale(var(--waifu-scale, 1));
  animation: waifu-slide-down 0.7s cubic-bezier(0.4, 0, 0.2, 1) both;
}

@keyframes waifu-slide-up {
  from {
    opacity: 0;
    transform: translateY(520px) scale(var(--waifu-scale, 1));
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(var(--waifu-scale, 1));
  }
}

@keyframes waifu-slide-down {
  from {
    opacity: 1;
    transform: translateY(0) scale(var(--waifu-scale, 1));
  }
  to {
    opacity: 0;
    transform: translateY(520px) scale(var(--waifu-scale, 1));
  }
}
</style>
