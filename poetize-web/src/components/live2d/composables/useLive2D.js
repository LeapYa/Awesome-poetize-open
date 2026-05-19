/**
 * Live2D核心逻辑 Composable
 * Vue2.7 Composition API
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useLive2DStore } from '@/stores/live2d'
import { loadLive2DResources } from '@/utils/resourceLoaders/resourceLoader'
import constant from '@/utils/constant'

export function useLive2D() {
  const store = useLive2DStore()

  // 响应式状态
  const loading = ref(false)
  const error = ref(null)

  // 计算属性
  const enabled = computed(() => store.enabled)
  const visible = computed(() => store.visible)
  const currentMessage = computed(() => store.currentMessage)
  const isActive = computed(() => store.isActive)

  /**
   * 初始化Live2D
   */
  const init = async () => {
    loading.value = true
    error.value = null

    try {
      // 初始化Store
      const success = await store.init()

      if (!success) {
        return false
      }

      // 加载资源
      await loadLive2DResources(constant.live2d_path)

      // 加载模型列表
      await store.loadModelList()

      // 加载当前模型
      await store.loadModel(store.currentModelId)

      return true
    } catch (err) {
      console.error('Live2D初始化失败:', err)
      error.value = err.message
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 显示消息
   */
  const showMessage = (text, timeout = 3000, priority = 5) => {
    store.showMessage(text, timeout, priority)
  }

  /**
   * 切换聊天窗口
   */
  const toggleChat = () => {
    store.toggleChat()
  }

  /**
   * 切换模型
   */
  const loadRandomModel = () => {
    return store.loadRandomModel()
  }

  /**
   * 切换材质
   */
  const changeTexture = () => {
    return store.changeTexture()
  }

  /**
   * 隐藏看板娘
   */
  let hideTimer = null
  const hide = () => {
    // 清除之前的定时器
    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }

    showMessage('愿你有一天能与重要的人重逢。', 2000, 11)

    hideTimer = setTimeout(() => {
      store.hide()
      hideTimer = null
    }, 2000)
  }

  /**
   * 显示看板娘
   */
  const show = async () => {
    // 清除隐藏定时器，防止之前的定时器干扰
    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }

    // 确保 enabled 和 visible 都是 true
    store.enabled = true
    store.show()

    // 如果模型列表未加载，重新初始化
    if (!store.modelList || !store.initialized) {
      loading.value = true
      try {
        // 加载资源
        await loadLive2DResources(constant.live2d_path)

        // 加载模型列表
        await store.loadModelList()

        // 加载当前模型
        await store.loadModel(store.currentModelId)

        // 标记已初始化
        store.initialized = true
      } catch (err) {
        console.error('重新加载模型失败:', err)
      } finally {
        loading.value = false
      }
    }
  }

  return {
    // 状态
    loading,
    error,
    enabled,
    visible,
    currentMessage,
    isActive,

    // 方法
    init,
    showMessage,
    toggleChat,
    loadRandomModel,
    changeTexture,
    hide,
    show,
  }
}
