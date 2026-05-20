<template>
  <canvas
    id="live2d"
    ref="canvasRef"
    class="live2d-canvas"
    :class="{ 'dragging': isDragging }"
    width="800"
    height="800"
    :style="canvasStyle"
    @click="handleClick"
  />
</template>

<script>
import { ref, watch, onMounted, computed } from 'vue'
import { useLive2DStore } from '@/stores/live2d'
import { useDrag } from './composables/useDrag'
import { resolveLive2DModelUrl } from '@/utils/live2dAssets'

export default {
  name: 'Live2DCanvas',
  
  props: {
    modelId: {
      type: Number,
      required: true
    },
    textureId: {
      type: Number,
      default: 0
    }
  },
  
  emits: ['click', 'model-loading', 'model-ready', 'model-error'],
  
  setup(props, { emit }) {
    const store = useLive2DStore()
    const canvasRef = ref(null)
    const modelLoaded = ref(false)
    const loadToken = ref(0)
    
    // 拖拽功能
    const { isDragging } = useDrag(canvasRef)
    
    /**
     * 加载Live2D模型
     */
    const loadModel = async () => {
      const token = loadToken.value + 1
      loadToken.value = token
      modelLoaded.value = false
      emit('model-loading')

      try {
        // 检查Live2D库是否已加载
        if (typeof window.loadlive2d !== 'function') {
          await waitForLive2D()
        }
        
        // 确保模型列表已加载
        if (!store.modelList) {
          await store.loadModelList()
        }
        
        // 获取模型路径
        const modelList = store.modelList
        if (!modelList || !modelList.models || !modelList.models[props.modelId]) {
          throw new Error('模型列表无效')
        }
        
        const models = modelList.models[props.modelId]
        // 兼容两种格式：字符串或数组
        let modelPath
        if (Array.isArray(models)) {
          modelPath = models[props.textureId] || models[0]
        } else {
          modelPath = models
        }
        
        const assetStatus = await store.loadAssetStatus()
        const useLocalModelOnly = assetStatus?.installed === true
        const { baseUrl, modelUrl, failures } = await resolveLive2DModelUrl(
          store.modelBaseUrl,
          modelPath,
          {
            allowRemoteFallback: !useLocalModelOnly,
            skipValidation: useLocalModelOnly
          }
        )

        if (baseUrl !== store.modelBaseUrl) {
          store.modelBaseUrl = baseUrl
        }

        if (failures.length > 0) {
          console.warn(
            useLocalModelOnly
              ? 'Live2D本地模型预检查未完全通过，继续尝试本地加载:'
              : 'Live2D模型资源已切换到可用CDN:',
            { baseUrl, failures }
          )
        }
        
        
        // 调用Live2D加载函数
        window.loadlive2d('live2d', modelUrl)

        const rendered = await waitForRenderedFrame(canvasRef.value)
        if (token !== loadToken.value) {
          return
        }

        if (!rendered) {
          throw new Error('Live2D模型首帧渲染超时')
        }

        modelLoaded.value = true
        emit('model-ready')
        
      } catch (error) {
        console.error('模型加载失败:', error)
        if (token === loadToken.value) {
          emit('model-error', error)
        }
      }
    }

    const waitForRenderedFrame = (canvas, timeoutMs = 60000) => {
      return new Promise((resolve) => {
        const startedAt = performance.now()

        const check = () => {
          if (hasRenderedPixels(canvas)) {
            resolve(true)
            return
          }

          if (performance.now() - startedAt >= timeoutMs) {
            resolve(false)
            return
          }

          requestAnimationFrame(check)
        }

        requestAnimationFrame(() => {
          requestAnimationFrame(check)
        })
      })
    }

    const hasRenderedPixels = (canvas) => {
      if (!canvas) return false

      const gl =
        canvas.getContext('webgl') ||
        canvas.getContext('experimental-webgl')

      if (!gl) return false

      const width = gl.drawingBufferWidth || canvas.width
      const height = gl.drawingBufferHeight || canvas.height
      if (!width || !height) return false

      const pixel = new Uint8Array(4)
      const columns = 12
      const rows = 12

      try {
        for (let row = 1; row < rows; row++) {
          for (let column = 1; column < columns; column++) {
            const x = Math.floor((width * column) / columns)
            const y = Math.floor((height * row) / rows)
            gl.readPixels(x, y, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel)
            if (pixel[3] > 8) {
              return true
            }
          }
        }
      } catch (error) {
        return false
      }

      return false
    }
    
    /**
     * 等待Live2D库加载
     */
    const waitForLive2D = () => {
      return new Promise((resolve, reject) => {
        // 如果已经加载，立即返回
        if (typeof window.loadlive2d === 'function') {
          resolve()
          return
        }

        let attempts = 0
        const maxAttempts = 100 // 最多等待10秒
        
        const checkInterval = setInterval(() => {
          attempts++
          
          if (typeof window.loadlive2d === 'function') {
            clearInterval(checkInterval)
            resolve()
          } else if (attempts >= maxAttempts) {
            clearInterval(checkInterval)
            reject(new Error('Live2D库加载超时，请刷新页面重试'))
          }
        }, 100)
      })
    }
    
    /**
     * Canvas点击事件
     */
    const handleClick = () => {
      emit('click')
    }
    
    // 监听模型ID变化
    watch(() => props.modelId, () => {
      loadModel()
    })
    
    // 监听材质ID变化
    watch(() => props.textureId, () => {
      loadModel()
    })

    // 计算缩放样式（用户配置的scale，移动端整体缩放已在widgetStyle处理）
    const canvasStyle = computed(() => {
      const scale = store.currentModelScale || 1.0
      return {
        transform: `scale(${scale})`,
        transformOrigin: 'bottom left'
      }
    })
    
    // 组件挂载
    onMounted(() => {
      // 延迟加载，确保DOM已渲染
      setTimeout(() => {
        loadModel()
      }, 500)
    })
    
    return {
      canvasRef,
      isDragging,
      handleClick,
      canvasStyle
    }
  }
}
</script>

<style scoped>
.live2d-canvas {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 280px;
  height: 280px;
  cursor: grab;  /* 默认显示可拖拽光标 */
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  pointer-events: auto;
}

/* 拖拽时改变光标 */
.live2d-canvas.dragging {
  cursor: grabbing;
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .live2d-canvas {
    width: 200px;
    height: 200px;
  }
}
</style>
