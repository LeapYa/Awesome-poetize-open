<template>
  <div ref="buttonRef" class="ai-chat-button-wrapper" :style="buttonStyle">
    <!-- 圆形AI聊天按钮：仅在配置的头像加载成功后显示，弱网未加载出来时不出现 -->
    <transition name="fade">
      <button
        v-if="!showChat && avatarLoaded"
        class="ai-chat-button"
        :class="{ dragging: isDragging, 'button-dark': isDarkMode, clicking: isClicking }"
        :title="config?.chat_name || 'AI助手'"
        @mousedown.stop="handleMouseDown"
        @touchstart.stop="handleTouchStart"
      >
        <img
          class="ai-avatar-icon"
          :src="displayAvatarUrl"
          :alt="config?.chat_name || 'AI助手'"
          draggable="false"
          @error="preloadAvatar"
        />
      </button>
    </transition>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import { computed, onMounted, onUnmounted, ref, getCurrentInstance, watch } from 'vue'
import { useLive2DStore } from '@/stores/live2d'
import { useAIChatStore } from '@/stores/aiChat'
import { getAiAvatarUrl } from '@/utils/ai-avatar'

export default {
  name: 'AIChatButton',

  setup() {
    const instance = getCurrentInstance()
    const live2dStore = useLive2DStore()
    const aiChatStore = useAIChatStore()
    const buttonRef = ref(null) // 实际上是wrapper的ref
    // 头像是否已加载成功：未加载成功前整个按钮不显示，
    // 保证任何情况下都不会出现默认/兜底头像
    const avatarLoaded = ref(false)
    // 头像加载失败后每 5 秒后台重试（不改动原 URL，避免破坏签名地址），
    // 加载成功后按钮自动浮现
    let avatarRetryTimer = null
    // 预加载令牌：URL 变化/重新预加载时使旧的回调失效，避免竞态
    let avatarPreloadToken = 0
    const AVATAR_RETRY_INTERVAL_MS = 5000

    // 拖拽状态
    const isDragging = ref(false)
    const startX = ref(0)
    const startY = ref(0)
    const startTime = ref(0)
    const currentX = ref(0)
    const currentY = ref(0)
    // 用户保存的原始位置（只有拖拽时才会修改）
    const userSavedX = ref(
      parseInt(localStorage.getItem('ai_button_x')) || null
    )
    const userSavedY = ref(
      parseInt(localStorage.getItem('ai_button_y')) || null
    )
    // 当前显示位置（会根据窗口大小自动调整）
    const savedX = ref(userSavedX.value)
    const savedY = ref(userSavedY.value)
    const hasMoved = ref(false) // 是否发生了移动
    const isClicking = ref(false) // 点击反馈动画状态
    const clickThreshold = 5 // 移动距离阈值（像素）
    const clickTimeThreshold = 300 // 点击时间阈值（毫秒）

    // 暗色模式检测
    const isDarkMode = ref(false)
    const checkDarkMode = () => {
      // 优先检查 localStorage 中的 theme 设置（用户手动设置）
      const theme = localStorage.getItem('theme')
      if (theme) {
        isDarkMode.value = theme === 'dark'
      } else if (
        document.documentElement.classList.contains('dark-mode') ||
        document.body.classList.contains('dark-mode')
      ) {
        // 其次检查 html 或 body 元素的 dark-mode 类（前台已应用的主题）
        isDarkMode.value = true
      } else {
        // 最后检查系统偏好（防止组件加载早于主题应用）
        isDarkMode.value =
          window.matchMedia &&
          window.matchMedia('(prefers-color-scheme: dark)').matches
      }
    }

    // 计算属性
    const showChat = computed(() => live2dStore.showChat)
    const config = computed(() => aiChatStore.config)
    const chatAvatar = computed(() => {
      if (!config.value) return ''
      if (Object.prototype.hasOwnProperty.call(config.value, 'chat_avatar')) {
        return config.value.chat_avatar || ''
      }
      return config.value.chatAvatar || ''
    })
    // 展示用头像 URL：
    // - 已配置头像时始终使用配置的地址
    // - 未配置时，只有在配置确实加载完成后才回退默认头像，
    //   避免配置还在路上（弱网）时闪现默认头像
    const displayAvatarUrl = computed(() => {
      if (chatAvatar.value) return chatAvatar.value
      if (!aiChatStore.configLoaded) return ''
      return getAiAvatarUrl('')
    })

    /**
     * 预加载头像：成功后才允许显示按钮，失败则隐藏并持续后台重试。
     * 使用独立 Image 预加载而非依赖按钮内 img 事件，
     * 因为按钮隐藏时 img 不存在，无法自我重试。
     */
    const preloadAvatar = () => {
      const url = displayAvatarUrl.value
      const token = ++avatarPreloadToken
      avatarLoaded.value = false
      if (!url) {
        return
      }
      const img = new Image()
      img.onload = () => {
        if (token !== avatarPreloadToken) return
        avatarLoaded.value = true
      }
      img.onerror = () => {
        if (token !== avatarPreloadToken) return
        avatarLoaded.value = false
        scheduleAvatarRetry()
      }
      img.src = url
    }

    const scheduleAvatarRetry = () => {
      if (avatarRetryTimer) return
      avatarRetryTimer = setTimeout(() => {
        avatarRetryTimer = null
        preloadAvatar()
      }, AVATAR_RETRY_INTERVAL_MS)
    }

    const clearAvatarRetryTimer = () => {
      if (avatarRetryTimer) {
        clearTimeout(avatarRetryTimer)
        avatarRetryTimer = null
      }
    }

    watch(displayAvatarUrl, () => {
      avatarLoaded.value = false
      clearAvatarRetryTimer()
      preloadAvatar()
    })

    /**
     * 网络恢复时立即重新预加载头像
     */
    const handleOnline = () => {
      clearAvatarRetryTimer()
      preloadAvatar()
    }

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
          // 同步到 store，用于决定面板动画方向
          live2dStore.updateAiButtonPosition(savedX.value, savedY.value)
        }
      } else {
        // 如果没有移动，并且按下时间很短，判定为点击
        if (duration < clickTimeThreshold) {
          // 播放点击反馈动画，再打开聊天
          // 让按钮"被按下"→ 面板从按钮位置展开，形成连贯的视觉衔接
          isClicking.value = true
          setTimeout(() => {
            isClicking.value = false
            live2dStore.toggleChat()
          }, 120)
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

    /**
     * 延迟启动 AI 按钮资源加载：
     * 弱网下配置请求与头像图片会和首屏关键资源抢带宽，
     * 因此挂载时先什么都不加载，等浏览器空闲（或最多 2 秒后）
     * 再发起配置请求与头像预加载，加载成功后按钮自然浮现
     */
    let aiResourcesStarted = false
    let deferredStartTimer = null
    let deferredIdleScheduled = false

    const startAiResources = () => {
      if (aiResourcesStarted) return
      aiResourcesStarted = true
      if (deferredStartTimer) {
        clearTimeout(deferredStartTimer)
        deferredStartTimer = null
      }
      try {
        aiChatStore.lightInit()
      } catch (error) {
        console.error('AI聊天按钮初始化失败:', error)
      }
      preloadAvatar()
    }

    const scheduleDeferredStart = () => {
      if (deferredIdleScheduled) return
      deferredIdleScheduled = true
      // 兜底定时器：即使一直不空闲，最多 2 秒后也开始加载
      deferredStartTimer = setTimeout(startAiResources, 2000)
      if (typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(startAiResources, { timeout: 2000 })
      }
    }

    // 挂载时绑定全局事件
    onMounted(async () => {
      document.addEventListener('mousemove', handleDragMove)
      document.addEventListener('mouseup', handleDragEnd)
      // touchmove 需要 { passive: false } 才能 preventDefault
      document.addEventListener('touchmove', handleDragMove, { passive: false })
      document.addEventListener('touchend', handleDragEnd)
      // 窗口大小变化监听
      window.addEventListener('resize', handleResize)
      // 网络恢复监听：弱网恢复后立即重试加载配置的头像
      window.addEventListener('online', handleOnline)

      // 初始加载时检查位置是否有效
      handleResize()

      // 同步初始按钮位置到 store，用于决定面板动画方向
      if (userSavedX.value !== null && userSavedY.value !== null) {
        live2dStore.updateAiButtonPosition(userSavedX.value, userSavedY.value)
      }

      // 检查暗色模式
      checkDarkMode()

      // 监听全局主题变化事件（由 admin.vue 触发）
      themeChangeHandler = () => {
        checkDarkMode()
      }
      $on(instance?.proxy?.$root, 'theme-changed', themeChangeHandler)

      // 监听 class 变化（前台暗色模式切换）
      mutationObserver = new MutationObserver(checkDarkMode)
      mutationObserver.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['class'],
      })

      // 延迟启动：不立即发起配置请求与头像加载，避免弱网下与首屏抢带宽
      scheduleDeferredStart()
    })

    // 卸载时解绑
    onUnmounted(() => {
      document.removeEventListener('mousemove', handleDragMove)
      document.removeEventListener('mouseup', handleDragEnd)
      document.removeEventListener('touchmove', handleDragMove, {
        passive: false,
      })
      document.removeEventListener('touchend', handleDragEnd)
      window.removeEventListener('resize', handleResize)
      window.removeEventListener('online', handleOnline)

      // 清理头像重试定时器并使在途预加载回调失效
      if (avatarRetryTimer) {
        clearTimeout(avatarRetryTimer)
        avatarRetryTimer = null
      }
      // 清理延迟启动的兜底定时器（requestIdleCallback 无法取消，
      // 通过置位幂等标记阻止卸载后的迟到回调再发起请求）
      aiResourcesStarted = true
      if (deferredStartTimer) {
        clearTimeout(deferredStartTimer)
        deferredStartTimer = null
      }
      avatarPreloadToken += 1

      // 清理全局事件监听
      if (themeChangeHandler) {
        $off(instance?.proxy?.$root, 'theme-changed', themeChangeHandler)
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
      displayAvatarUrl,
      avatarLoaded,
      isDragging,
      isClicking,
      isDarkMode,
      buttonStyle,
      handleMouseDown,
      handleTouchStart,
      preloadAvatar,
    }
  },
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
  background: radial-gradient(
    circle at center,
    rgba(102, 126, 234, 0.05) 0%,
    transparent 70%
  );
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
.ai-chat-button:hover:not(.dragging) .ai-avatar-icon {
  transform: scale(1.2);
}
.ai-chat-button.dragging .ai-avatar-icon {
  transform: scale(1.08);
}
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}
.fade-enter-from {
  opacity: 0;
  transform: scale(0.8);
}
/* 按钮消失时继续缩小，与点击反馈动画衔接 */
.fade-leave-to {
  opacity: 0;
  transform: scale(0.5);
}
/* 点击反馈动画：按钮被按下缩小，再交给面板展开动画 */
.ai-chat-button.clicking {
  animation: aiButtonClick 0.12s ease-out forwards;
}
@keyframes aiButtonClick {
  0% {
    transform: scale(1);
  }
  100% {
    transform: scale(0.7);
  }
}
@media screen and (max-width: 768px) {
  .ai-chat-button-wrapper {
    bottom: 20px;
    left: 20px;
  }
  .ai-chat-button {
    width: 52px;
    height: 52px;
  }
}
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
  background: radial-gradient(
    circle at center,
    rgba(102, 126, 234, 0.15) 0%,
    transparent 70%
  ) !important;
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
