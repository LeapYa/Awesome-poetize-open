<template>
  <div 
    ref="buttonRef"
    class="ai-chat-button-wrapper" 
    :style="buttonStyle"
  >
    <!-- 圆形AI聊天按钮 -->
    <transition name="fade">
      <button
        v-if="!showChat"
        class="ai-chat-button"
        :class="{ dragging: isDragging, 'button-dark': isDarkMode }"
        :title="config?.chat_name || 'AI助手'"
        @mousedown.stop="handleMouseDown"
        @touchstart.stop="handleTouchStart"
      >
        <img
          class="ai-avatar-icon"
          :src="chatAvatarUrl"
          :alt="config?.chat_name || 'AI助手'"
          draggable="false"
        />
        <!-- 亮色模式图标 -->
        <svg v-if="!isDarkMode" class="ai-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path d="M521.0112 4.608c151.808 0 278.784 47.4624 367.0528 137.728 88.2688 90.1632 137.9328 226.048 135.8848 377.344a511.9488 511.9488 0 0 1-47.5136 212.736c-102.7584 214.7328-307.3536 263.3728-472.4736 263.68-81.8176 0-163.2256-10.6496-242.2784-31.488H100.352a66.3552 66.3552 0 0 1-66.2528-76.0832l18.2272-129.024C23.04 711.4752 5.6832 635.904 0.768 534.016A501.1456 501.1456 0 0 1 368.0256 23.3472 610.2016 610.2016 0 0 1 521.0112 4.608z m0.1536 74.24a535.7056 535.7056 0 0 0-134.5024 16.384 426.6496 426.6496 0 0 0-311.6032 434.688c4.096 89.6512 18.7392 156.16 42.0352 192 8.2944 12.4416 11.776 27.4944 9.5744 42.3424l-17.8176 126.3104h155.136c6.0928 0 12.032 0.768 17.8688 2.4576 49.4592 13.6192 476.7744 123.5968 627.3024-191.488a444.1088 444.1088 0 0 0 40.7552-182.3744c2.4576-131.2256-38.2976-247.1936-114.688-324.096-76.288-76.8512-179.8656-116.224-314.0608-116.224z m-41.472 252.0064c4.1984 5.9392 7.68 12.288 10.3936 19.0464l10.8544 27.392 92.5696 228.5568c6.4512 12.032 10.5984 25.2416 12.3904 38.8608a31.744 31.744 0 0 1-10.0864 22.784c-6.4512 6.5536-15.36 10.24-24.7296 10.0864a29.2864 29.2864 0 0 1-24.7296-11.1104 68.7104 68.7104 0 0 1-8.6528-16.0768c-4.2496-7.0144-6.0928-13.4656-9.3184-18.944l-16.7936-44.4928H367.7696l-17.0496 45.1584c-4.1472 12.5952-9.728 24.576-16.8448 35.84a26.8288 26.8288 0 0 1-23.04 9.472 33.8432 33.8432 0 0 1-24.7296-9.8304 30.5664 30.5664 0 0 1-10.3424-22.528c0.1536-5.0176 0.9216-9.9328 2.4576-14.848a283.136 283.136 0 0 1 7.8848-21.504l90.7264-229.888c2.4576-6.656 5.7344-14.592 9.216-24.6784 2.9696-8.192 6.8608-15.7696 11.6224-23.04a46.4896 46.4896 0 0 1 16.0768-14.848 49.408 49.408 0 0 1 65.9968 14.592z m215.2448-19.1488a34.2016 34.2016 0 0 1 26.1632 10.5984c7.2704 9.216 10.8544 20.8384 9.8304 32.6144v278.784c1.024 11.776-2.56 23.552-9.8304 32.8704a33.9456 33.9456 0 0 1-26.112 10.8544 33.2288 33.2288 0 0 1-24.7296-10.8544 47.3088 47.3088 0 0 1-9.8816-25.8048V354.9184a46.336 46.336 0 0 1 9.8816-32.3584 32.768 32.768 0 0 1 24.6784-10.8544z m-256.1024 74.752L386.56 532.6848h105.472L438.8352 386.5088z" fill="#575757" />
        </svg>
        <!-- 暗色模式图标（白色） -->
        <svg v-else class="ai-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path d="M521.0112 4.608c151.808 0 278.784 47.4624 367.0528 137.728 88.2688 90.1632 137.9328 226.048 135.8848 377.344a511.9488 511.9488 0 0 1-47.5136 212.736c-102.7584 214.7328-307.3536 263.3728-472.4736 263.68-81.8176 0-163.2256-10.6496-242.2784-31.488H100.352a66.3552 66.3552 0 0 1-66.2528-76.0832l18.2272-129.024C23.04 711.4752 5.6832 635.904 0.768 534.016A501.1456 501.1456 0 0 1 368.0256 23.3472 610.2016 610.2016 0 0 1 521.0112 4.608z m0.1536 74.24a535.7056 535.7056 0 0 0-134.5024 16.384 426.6496 426.6496 0 0 0-311.6032 434.688c4.096 89.6512 18.7392 156.16 42.0352 192 8.2944 12.4416 11.776 27.4944 9.5744 42.3424l-17.8176 126.3104h155.136c6.0928 0 12.032 0.768 17.8688 2.4576 49.4592 13.6192 476.7744 123.5968 627.3024-191.488a444.1088 444.1088 0 0 0 40.7552-182.3744c2.4576-131.2256-38.2976-247.1936-114.688-324.096-76.288-76.8512-179.8656-116.224-314.0608-116.224z m-41.472 252.0064c4.1984 5.9392 7.68 12.288 10.3936 19.0464l10.8544 27.392 92.5696 228.5568c6.4512 12.032 10.5984 25.2416 12.3904 38.8608a31.744 31.744 0 0 1-10.0864 22.784c-6.4512 6.5536-15.36 10.24-24.7296 10.0864a29.2864 29.2864 0 0 1-24.7296-11.1104 68.7104 68.7104 0 0 1-8.6528-16.0768c-4.2496-7.0144-6.0928-13.4656-9.3184-18.944l-16.7936-44.4928H367.7696l-17.0496 45.1584c-4.1472 12.5952-9.728 24.576-16.8448 35.84a26.8288 26.8288 0 0 1-23.04 9.472 33.8432 33.8432 0 0 1-24.7296-9.8304 30.5664 30.5664 0 0 1-10.3424-22.528c0.1536-5.0176 0.9216-9.9328 2.4576-14.848a283.136 283.136 0 0 1 7.8848-21.504l90.7264-229.888c2.4576-6.656 5.7344-14.592 9.216-24.6784 2.9696-8.192 6.8608-15.7696 11.6224-23.04a46.4896 46.4896 0 0 1 16.0768-14.848 49.408 49.408 0 0 1 65.9968 14.592z m215.2448-19.1488a34.2016 34.2016 0 0 1 26.1632 10.5984c7.2704 9.216 10.8544 20.8384 9.8304 32.6144v278.784c1.024 11.776-2.56 23.552-9.8304 32.8704a33.9456 33.9456 0 0 1-26.112 10.8544 33.2288 33.2288 0 0 1-24.7296-10.8544 47.3088 47.3088 0 0 1-9.8816-25.8048V354.9184a46.336 46.336 0 0 1 9.8816-32.3584 32.768 32.768 0 0 1 24.6784-10.8544z m-256.1024 74.752L386.56 532.6848h105.472L438.8352 386.5088z" fill="#ffffff" />
        </svg>
      </button>
    </transition>
  </div>
</template>

<script>
import { computed, onMounted, onUnmounted, ref, getCurrentInstance } from 'vue'
import { useLive2DStore } from '@/stores/live2d'
import { getAiAvatarUrl } from '@/utils/ai-avatar'
// useAIChatStore 改为动态导入，避免 mermaid 等依赖被预加载

export default {
  name: 'AIChatButton',
  
  setup() {
    const instance = getCurrentInstance()
    const live2dStore = useLive2DStore()
    // aiChatStore 改为动态引用
    const aiChatStoreRef = ref(null)
    const buttonRef = ref(null) // 实际上是wrapper的ref
    
    // 拖拽状态
    const isDragging = ref(false)
    const startX = ref(0)
    const startY = ref(0)
    const startTime = ref(0)
    const currentX = ref(0)
    const currentY = ref(0)
    // 用户保存的原始位置（只有拖拽时才会修改）
    const userSavedX = ref(parseInt(localStorage.getItem('ai_button_x')) || null)
    const userSavedY = ref(parseInt(localStorage.getItem('ai_button_y')) || null)
    // 当前显示位置（会根据窗口大小自动调整）
    const savedX = ref(userSavedX.value)
    const savedY = ref(userSavedY.value)
    const hasMoved = ref(false) // 是否发生了移动
    const clickThreshold = 5 // 移动距离阈值（像素）
    const clickTimeThreshold = 300 // 点击时间阈值（毫秒）
    
    // 暗色模式检测
    const isDarkMode = ref(false)
    const checkDarkMode = () => {
      // 优先检查 localStorage 中的 theme 设置（用户手动设置）
      const theme = localStorage.getItem('theme')
      if (theme) {
        isDarkMode.value = theme === 'dark'
      } else if (document.documentElement.classList.contains('dark-mode') || document.body.classList.contains('dark-mode')) {
        // 其次检查 html 或 body 元素的 dark-mode 类（前台已应用的主题）
        isDarkMode.value = true
      } else {
        // 最后检查系统偏好（防止组件加载早于主题应用）
        isDarkMode.value = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      }
    }
    
    // 计算属性
    const showChat = computed(() => live2dStore.showChat)
    // config 改为从动态加载的 store 获取
    const config = computed(() => aiChatStoreRef.value?.config || null)
    const chatAvatarUrl = computed(() => getAiAvatarUrl(config.value?.chat_avatar))
    
    // 按钮位置样式
    const buttonStyle = computed(() => {
      const style = {}
      
      if (savedX.value !== null && savedY.value !== null) {
        style.left = `${savedX.value}px`
        style.top = `${savedY.value}px`
        style.right = 'auto'
        style.bottom = 'auto'
      }
      
      return style
    })
    
    /**
     * 鼠标按下
     */
    const handleMouseDown = (e) => {
      e.preventDefault()
      startDrag(e)
    }
    
    /**
     * 触摸开始
     */
    const handleTouchStart = (e) => {
      e.preventDefault()
      startDrag(e)
    }
    
    /**
     * 开始拖拽/点击检测
     */
    const startDrag = (e) => {
      if (!buttonRef.value) return
      
      isDragging.value = true
      hasMoved.value = false
      startTime.value = Date.now()
      
      // 获取初始位置
      const touch = e.touches ? e.touches[0] : e
      startX.value = touch.clientX
      startY.value = touch.clientY
      
      // 获取按钮当前位置
      const rect = buttonRef.value.getBoundingClientRect()
      currentX.value = rect.left
      currentY.value = rect.top
    }
    
    /**
     * 拖拽中
     */
    const handleDragMove = (e) => {
      if (!isDragging.value) return
      
      const touch = e.touches ? e.touches[0] : e
      const deltaX = touch.clientX - startX.value
      const deltaY = touch.clientY - startY.value
      const distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
      
      // 判断是否移动超过阈值
      if (distance > clickThreshold) {
        if (!hasMoved.value) {
          hasMoved.value = true
        }
        
        // 计算新位置
        const newX = currentX.value + deltaX
        const newY = currentY.value + deltaY
        
        // 限制在视口内
        const buttonSize = 58
        const maxX = window.innerWidth - buttonSize
        const maxY = window.innerHeight - buttonSize
        
        const boundedX = Math.max(0, Math.min(newX, maxX))
        const boundedY = Math.max(0, Math.min(newY, maxY))
        
        // 更新位置
        savedX.value = boundedX
        savedY.value = boundedY
      }
    }
    
    /**
     * 结束拖拽（鼠标松开或触摸结束）
     */
    const handleDragEnd = () => {
      if (!isDragging.value) return
      
      const endTime = Date.now()
      const duration = endTime - startTime.value
      
      // 保存位置
      if (hasMoved.value) {
        if (savedX.value !== null && savedY.value !== null) {
          // 保存用户拖拽的原始位置
          userSavedX.value = savedX.value
          userSavedY.value = savedY.value
          localStorage.setItem('ai_button_x', savedX.value)
          localStorage.setItem('ai_button_y', savedY.value)
        }
      } else {
        // 如果没有移动，并且按下时间很短，判定为点击
        if (duration < clickTimeThreshold) {
          // 这是一个点击操作，打开聊天
          live2dStore.toggleChat()
        }
      }
      
      // 重置状态
      isDragging.value = false
      hasMoved.value = false
    }
    
    /**
     * 窗口大小变化时，调整按钮位置确保不超出视口
     * 优先恢复用户保存的原始位置，如果原始位置超出视口才调整
     */
    const handleResize = () => {
      if (userSavedX.value === null || userSavedY.value === null) return
      
      const buttonSize = window.innerWidth <= 768 ? 52 : 58
      const maxX = window.innerWidth - buttonSize
      const maxY = window.innerHeight - buttonSize
      
      // 尝试使用用户保存的原始位置
      let newX = userSavedX.value
      let newY = userSavedY.value
      
      // 如果原始位置超出视口，调整到边界
      if (newX > maxX) {
        newX = maxX
      }
      if (newY > maxY) {
        newY = maxY
      }
      
      // 确保不小于0
      if (newX < 0) {
        newX = 0
      }
      if (newY < 0) {
        newY = 0
      }
      
      // 更新显示位置（不修改localStorage中的原始位置）
      savedX.value = newX
      savedY.value = newY
    }
    
    // 主题变化监听器引用
    let themeChangeHandler = null
    let mutationObserver = null
    
    // 挂载时绑定全局事件
    onMounted(async () => {
      document.addEventListener('mousemove', handleDragMove)
      document.addEventListener('mouseup', handleDragEnd)
      // touchmove 需要 { passive: false } 才能 preventDefault
      document.addEventListener('touchmove', handleDragMove, { passive: false })
      document.addEventListener('touchend', handleDragEnd)
      // 窗口大小变化监听
      window.addEventListener('resize', handleResize)
      
      // 初始加载时检查位置是否有效
      handleResize()
      
      // 检查暗色模式
      checkDarkMode()
      
      // 监听全局主题变化事件（由 admin.vue 触发）
      themeChangeHandler = () => {
        checkDarkMode()
      }
      instance?.proxy?.$root?.$on('theme-changed', themeChangeHandler)
      
      // 监听 class 变化（前台暗色模式切换）
      mutationObserver = new MutationObserver(checkDarkMode)
      mutationObserver.observe(document.documentElement, { 
        attributes: true, 
        attributeFilter: ['class'] 
      })
      
      // 轻量级初始化（不加载配置，减少初始请求）
      // 动态导入 aiChatStore，避免 mermaid 等依赖被预加载
      try {
        const { useAIChatStore } = await import('@/stores/aiChat')
        const aiChatStore = useAIChatStore()
        aiChatStoreRef.value = aiChatStore
        aiChatStore.lightInit()
      } catch (error) {
        console.error('AI聊天按钮初始化失败:', error)
      }
    })
    
    // 卸载时解绑
    onUnmounted(() => {
      document.removeEventListener('mousemove', handleDragMove)
      document.removeEventListener('mouseup', handleDragEnd)
      document.removeEventListener('touchmove', handleDragMove, { passive: false })
      document.removeEventListener('touchend', handleDragEnd)
      window.removeEventListener('resize', handleResize)
      
      // 清理全局事件监听
      if (themeChangeHandler) {
        instance?.proxy?.$root?.$off('theme-changed', themeChangeHandler)
      }
      
      // 清理 MutationObserver
      if (mutationObserver) {
        mutationObserver.disconnect()
      }
    })
    
    return {
      buttonRef,
      showChat,
      config,
      chatAvatarUrl,
      isDragging,
      isDarkMode,
      buttonStyle,
      handleMouseDown,
      handleTouchStart
    }
  }
}
</script>

<style scoped>
.ai-chat-button-wrapper {
  position: fixed;
  bottom: 30px;
  left: 30px;
  z-index: 998;
}

.ai-chat-button {
  width: 58px;
  height: 58px;
  min-width: 58px;
  min-height: 58px;
  padding: 0;
  border-radius: 50%;
  background: #f8fbff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}

.ai-chat-button.dragging {
  cursor: grabbing;
  transition: none;
  transform: scale(1.05);
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.15);
}

.ai-chat-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(circle at center, rgba(102, 126, 234, 0.05) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.ai-chat-button:hover:not(.dragging) {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  border-color: rgba(102, 126, 234, 0.2);
}

.ai-chat-button:hover:not(.dragging)::before {
  opacity: 1;
}

.ai-chat-button:active:not(.dragging) {
  transform: scale(0.95);
}

.ai-avatar-icon {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transform: scale(1.14);
  transition: transform 0.3s ease;
  pointer-events: none;
  user-select: none;
}

.ai-icon {
  display: none;
  width: 28px;
  height: 28px;
  transition: transform 0.3s ease;
}

.ai-chat-button:hover:not(.dragging) .ai-avatar-icon {
  transform: scale(1.2);
}

.ai-chat-button.dragging .ai-avatar-icon {
  transform: scale(1.08);
}

.ai-chat-button:hover:not(.dragging) .ai-icon {
  transform: scale(1.1);
}

.ai-chat-button.dragging .ai-icon {
  transform: scale(0.95);
}

/* 淡入淡出动画 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .ai-chat-button-wrapper {
    bottom: 20px;
    left: 20px;
  }
  
  .ai-chat-button {
    width: 52px;
    height: 52px;
  }
  
  .ai-icon {
    width: 24px;
    height: 24px;
  }
}

/* 深色模式 - 深色按钮配白色图标 */
.dark-mode .ai-chat-button,
.button-dark {
  background: #000 !important;
  border-color: rgba(255, 255, 255, 0.15) !important;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4) !important;
}

.dark-mode .ai-chat-button.dragging,
.button-dark.dragging {
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.6) !important;
}

.dark-mode .ai-chat-button::before,
.button-dark::before {
  background: radial-gradient(circle at center, rgba(102, 126, 234, 0.15) 0%, transparent 70%) !important;
}

.dark-mode .ai-chat-button:hover:not(.dragging),
.button-dark:hover:not(.dragging) {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.5) !important;
  border-color: rgba(102, 126, 234, 0.4) !important;
  background: #000 !important;
}

.dark-mode .ai-chat-button:hover:not(.dragging)::before,
.button-dark:hover:not(.dragging)::before {
  opacity: 1;
}

.dark-mode .ai-chat-button:active:not(.dragging),
.button-dark:active:not(.dragging) {
  transform: scale(0.95);
}
</style>
