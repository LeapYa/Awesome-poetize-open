<template>
  <div ref="messagesContainer" class="chat-messages">
    <AIChatMessage
      v-for="message in messages"
      :key="message.id"
      :message="message"
      @rendered="scrollToBottom"
    />

    <!-- 打字指示器 -->
    <div v-if="typing" class="typing-indicator">
      <span class="typing-text">{{ typingMessage }}</span>
      <div class="typing-dots">
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, watch, nextTick, onMounted } from 'vue'
import AIChatMessage from './AIChatMessage.vue'

export default {
  name: 'AIChatMessages',

  components: {
    AIChatMessage,
  },

  props: {
    messages: {
      type: Array,
      required: true,
    },
    streaming: {
      type: Boolean,
      default: false,
    },
    typing: {
      type: Boolean,
      default: false,
    },
  },

  setup(props) {
    const messagesContainer = ref(null)
    const scrollTimer = ref(null) // 滚动节流定时器
    const lastScrollTime = ref(0) // 上次滚动时间

    // 打字提示消息
    const typingMessages = [
      '正在思考中...',
      '让我想想...',
      '正在组织语言...',
      '稍等一下...',
      '正在为你查询...',
    ]
    const typingMessage = ref(
      typingMessages[Math.floor(Math.random() * typingMessages.length)]
    )

    /**
     * 滚动到底部（带节流）
     */
    const scrollToBottom = (immediate = false) => {
      const now = Date.now()
      const throttleTime = props.streaming ? 150 : 0 // 流式时节流150ms，否则立即执行

      // 如果是立即滚动，或者距离上次滚动超过节流时间，则执行滚动
      if (immediate || now - lastScrollTime.value > throttleTime) {
        // 清除之前的定时器
        if (scrollTimer.value) {
          clearTimeout(scrollTimer.value)
          scrollTimer.value = null
        }

        lastScrollTime.value = now
        nextTick(() => {
          if (messagesContainer.value) {
            messagesContainer.value.scrollTop =
              messagesContainer.value.scrollHeight
          }
        })
      } else {
        // 节流期间，使用定时器延迟执行最后一次滚动
        if (scrollTimer.value) {
          clearTimeout(scrollTimer.value)
        }
        scrollTimer.value = setTimeout(() => {
          lastScrollTime.value = Date.now()
          nextTick(() => {
            if (messagesContainer.value) {
              messagesContainer.value.scrollTop =
                messagesContainer.value.scrollHeight
            }
          })
        }, throttleTime)
      }
    }

    // 监听消息变化，自动滚动
    watch(
      () => props.messages.length,
      () => {
        scrollToBottom()
      }
    )

    // 监听流式状态
    watch(
      () => props.streaming,
      (newVal) => {
        if (newVal) {
          scrollToBottom()
        }
      }
    )

    // 监听打字指示器状态
    watch(
      () => props.typing,
      (newVal) => {
        if (newVal) {
          scrollToBottom()
        }
      }
    )

    // 监听消息内容变化（用于打字机效果实时滚动）
    watch(
      () => props.messages,
      () => {
        scrollToBottom()
      },
      { deep: true }
    )

    // 组件挂载后滚动到底部（确保历史消息加载后在底部）
    onMounted(() => {
      // 延迟一下，确保DOM完全渲染
      setTimeout(() => {
        scrollToBottom()
      }, 100)
    })

    return {
      messagesContainer,
      typingMessage,
      scrollToBottom,
    }
  },
}
</script>

<style scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: transparent;
  scroll-behavior: auto;
}
.chat-messages::-webkit-scrollbar {
  width: 6px;
}
.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}
.chat-messages::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 3px;
}
.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #b0b0b0;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  font-size: 14px;
}
.empty-state i {
  font-size: 48px;
  margin-bottom: 15px;
  opacity: 0.5;
}
.message-wrapper {
  margin-bottom: 15px;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.typing-indicator {
  display: inline-flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 18px 18px 18px 4px;
  margin-bottom: 15px;
  animation: fadeInUp 0.3s ease-out;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}
.typing-text {
  font-size: 13px;
  color: #666;
  opacity: 0.9;
  margin: 0;
}
.typing-dots {
  display: flex;
  gap: 5px;
  align-items: center;
}
.typing-dot {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typingDot 1.4s infinite;
}
.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes typingDot {
  0%,
  60%,
  100% {
    transform: scale(1);
    opacity: 0.5;
  }
  30% {
    transform: scale(1.2);
    opacity: 1;
  }
}
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.dark-mode .typing-indicator {
  background: rgba(44, 62, 80, 0.9);
}
.dark-mode .typing-text {
  color: #ecf0f1;
}
.dark-mode .typing-dot {
  background: #666;
}
.dark-mode .empty-state {
  color: #b0b0b0;
}
.dark-mode .chat-messages::-webkit-scrollbar-thumb {
  background: #555;
}
.dark-mode .chat-messages::-webkit-scrollbar-thumb:hover {
  background: #666;
}
</style>
