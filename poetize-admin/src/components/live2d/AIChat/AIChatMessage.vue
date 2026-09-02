<template>
  <div class="message" :class="messageClass">
    <div v-if="isUser" class="message-row">
      <button class="message-edit-btn" @click="handleEdit" title="编辑消息">
        <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M319.184 567.24c-0.665 0.886-1.329 1.8-1.633 2.907l-51.532 195.307c-2.99 11.375 0.083 23.635 8.22 32.27 6.088 6.144 14.114 9.52 22.666 9.52 2.823 0 5.645-0.332 8.413-1.107l187.558-52.888c0.305 0 0.443 0.277 0.664 0.277 2.159 0 4.29-0.803 5.868-2.519l501.538-518.448c14.89-15.416 23.054-36.421 23.054-59.282 0-25.904-10.627-51.78-29.226-70.988l-47.353-49.041c-18.598-19.235-43.672-30.25-68.69-30.25-22.113 0-42.427 8.442-57.372 23.83L319.93 565.441c-0.526 0.498-0.388 1.217-0.747 1.799M951.85 181.774l-49.817 51.477-80.73-84.826L870.4 97.667c7.75-8.08 22.805-6.89 31.716 2.353l47.409 49.041c4.926 5.12 7.749 11.9 7.749 18.626-0.028 5.507-1.91 10.517-5.424 14.087M408.355 575.377l361.887-374.175 80.813 84.881L489.832 659.54l-81.477-84.162zM342.43 727.095l26.154-99.245 69.77 72.123-95.924 27.122z m641.19-339.857c-18.985 0-34.54 15.969-34.622 35.868V906.46c0 25.351-19.899 45.942-44.447 45.942h-790.86c-24.521 0-44.503-20.59-44.503-45.942v-788.95c0-25.379 19.982-45.97 44.503-45.97h509.343c19.096 0 34.594-16.051 34.594-35.784C657.63 16.052 642.131 0 623.035 0h-514.63C48.655 0 0 50.259 0 112.086v799.855C0 973.77 48.654 1024 108.406 1024h801.376c59.808 0 108.406-50.231 108.406-112.059V422.857c-0.055-19.65-15.581-35.619-34.567-35.619"
          />
        </svg>
      </button>

      <div class="message-content" :style="{ background: themeColor }">
        <div v-if="message.attachedPage" class="attached-page-badge">
          <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M750.933333 648.567467V256a153.6 153.6 0 1 0-307.2 0V682.666667a68.266667 68.266667 0 1 0 136.533334 0V341.333333a34.133333 34.133333 0 1 1 68.266666 0v341.333334a136.533333 136.533333 0 0 1-273.066666 0V256a221.866667 221.866667 0 1 1 443.733333 0V682.666667a307.2 307.2 0 1 1-614.4 0V273.066667a34.133333 34.133333 0 1 1 68.266667 0v409.6a238.933333 238.933333 0 1 0 477.866666 0v-34.0992z"
              fill="currentColor"
            />
          </svg>
          <span class="badge-text">{{ message.attachedPage.title }}</span>
        </div>

        <div v-if="hasImages" class="message-images">
          <img
            v-for="(url, index) in message.images"
            :key="index"
            :src="url"
            alt="图片"
            class="message-image-thumb"
            @click="openImagePreview(index)"
          />
        </div>

        <div v-if="hasDocuments" class="message-documents">
          <div
            v-for="(doc, index) in message.documents"
            :key="index"
            class="message-document-chip"
            :title="`${doc.name} · ${formatFileSize(doc.size)}`"
          >
            <svg class="message-document-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
                fill="currentColor" opacity="0.3"
              />
              <path
                d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zm0 2 6 6h-6z"
                fill="currentColor"
              />
            </svg>
            <span class="message-document-name">{{ doc.name }}</span>
            <span class="message-document-size">{{ formatFileSize(doc.size) }}</span>
          </div>
        </div>

        <!-- 自定义图片预览器 -->
        <div
          v-if="showImageViewer"
          class="custom-image-viewer-mask"
          @click="showImageViewer = false"
        >
          <div class="viewer-close-btn" @click="showImageViewer = false">×</div>
          <div
            v-if="previewImages.length > 1"
            class="viewer-arrow left"
            @click.stop="prevPreviewImage"
          >
            ‹
          </div>
          <div class="viewer-content" @click.stop>
            <img :src="previewImages[previewImageIndex]" class="viewer-img" alt="预览图片" />
          </div>
          <div
            v-if="previewImages.length > 1"
            class="viewer-arrow right"
            @click.stop="nextPreviewImage"
          >
            ›
          </div>
          <div class="viewer-index">{{ previewImageIndex + 1 }} / {{ previewImages.length }}</div>
        </div>

        <div class="message-text" v-text="message.content" />
      </div>
    </div>

    <div
      v-else
      class="message-content"
      :style="!isUser ? { '--link-color': themeColor } : {}"
    >
      <template v-if="isAssistant">
        <template v-for="segment in assistantSegments">
          <details
            v-if="segment.type === 'thinking-group'"
            :key="`${segment.id}-reasoning`"
            class="reasoning-panel"
            :class="{ 'is-thinking': segment.status === 'thinking' }"
            :open="segment.status === 'thinking'"
          >
            <summary class="reasoning-summary">
              <span class="reasoning-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"></path>
                </svg>
              </span>
              <span class="reasoning-label">{{ segment.status === 'thinking' ? '正在思考' : thinkingDoneLabel }}</span>
              <span v-if="segment.status === 'thinking'" class="reasoning-dots" aria-hidden="true">
                <i></i><i></i><i></i>
              </span>
              <svg class="reasoning-chevron" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <path d="M9 6l6 6-6 6"></path>
              </svg>
            </summary>
            <div class="reasoning-body">
              <template v-for="item in segment.items" :key="item.id">
                <MarkdownRenderer
                  v-if="item.type === 'reasoning'"
                  class="reasoning-content"
                  :content="item.content"
                  :streaming="false"
                  :enable-typewriter="false"
                />
                <div
                  v-else-if="item.type === 'tool'"
                  class="reasoning-tool"
                  :class="`is-${item.status || 'completed'}`"
                >
                  <span class="reasoning-tool-status" aria-hidden="true"></span>
                  <span class="reasoning-tool-label">{{ formatToolEventLabel(item) }}</span>
                </div>
              </template>
            </div>
          </details>
          <div v-else-if="segment.type === 'tool'" :key="`${segment.id}-tool`" class="tool-pill-row">
            <div
              class="tool-pill"
              :class="[`tool-pill-${segment.status || 'completed'}`]"
            >
              <span
                v-if="segment.status === 'executing'"
                class="tool-pill-icon tool-pill-funnel"
                aria-hidden="true"
              >
                <svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="24" cy="24" r="18.1" fill="#d7efff"></circle>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M18,9.5h12"
                  ></path>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M18,38.5h12"
                  ></path>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M19.5,10.5c0,6,4,7.6,6.3,9.5c-2.3,1.9-6.3,3.5-6.3,9.5"
                  ></path>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M28.5,10.5c0,6-4,7.6-6.3,9.5c2.3,1.9,6.3,3.5,6.3,9.5"
                  ></path>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M20.5,14.5h7"
                  ></path>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M20.5,33.5h7"
                  ></path>
                </svg>
              </span>
              <span
                v-else-if="segment.status === 'failed'"
                class="tool-pill-icon tool-pill-error"
                aria-hidden="true"
              >!</span>
              <span
                v-else
                class="tool-pill-icon tool-pill-check"
                aria-hidden="true"
              >
                <svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="28" cy="28" r="18.1" fill="#a5d6a7"></circle>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M34.3,39.4c-2.9,2-6.5,3.1-10.3,3.1C13.8,42.5,5.5,34.2,5.5,24c0-4.4,1.6-8.5,4.1-11.7"
                  ></path>
                  <path
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    d="M20.1,5.9c1.3-0.3,2.6-0.4,3.9-0.4c10.2,0,18.5,8.3,18.5,18.5c0,2.9-0.7,5.6-1.8,8"
                  ></path>
                  <polyline
                    fill="none"
                    stroke="#18193f"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="3"
                    points="16.5,24.5 21.5,29.5 31.5,19.5"
                  ></polyline>
                </svg>
              </span>
              <span class="tool-pill-text">{{ formatToolEventLabel(segment) }}</span>
            </div>
          </div>
          <MarkdownRenderer
            v-else
            :key="`${segment.id}-text`"
            :content="segment.content"
            :streaming="message.streaming || false"
            :enable-typewriter="enableTypewriter && message.isNew !== false"
            @rendered="handleRendered"
          />
        </template>
      </template>

      <div v-else class="message-text" v-text="message.content" />
    </div>

    <div v-if="showTimestamp || !isUser" class="message-footer">
      <div v-if="showTimestamp" class="message-time">
        {{ formattedTime }}
      </div>
      <button
        v-if="!isUser"
        class="message-copy-btn"
        @click="handleCopy"
        title="复制消息"
      >
        <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M833.33 767.96h-91.9c-21.73 0-39.34-17.6-39.34-39.34s17.62-39.34 39.34-39.34h91.9c8.82 0 15.98-7.18 15.98-15.98V193.8c0-8.8-7.17-15.98-15.98-15.98H353.84c-8.82 0-15.98 7.18-15.98 15.98v90.86c0 21.75-17.62 39.34-39.34 39.34s-39.34-17.6-39.34-39.34V193.8c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.21-42.49 94.67-94.68 94.67z"
          ></path>
          <path
            d="M675.96 925.33H196.47c-52.19 0-94.67-42.45-94.67-94.67V351.17c0-52.21 42.47-94.67 94.67-94.67h479.49c52.19 0 94.67 42.45 94.67 94.67v479.49c-0.01 52.22-42.48 94.67-94.67 94.67zM196.47 335.19c-8.82 0-15.98 7.18-15.98 15.98v479.49c0 8.8 7.17 15.98 15.98 15.98h479.49c8.82 0 15.98-7.18 15.98-15.98V351.17c0-8.8-7.17-15.98-15.98-15.98H196.47z"
          ></path>
        </svg>
      </button>
    </div>
  </div>
</template>

<script>
import { computed, onMounted, onUnmounted, nextTick, ref } from 'vue'
import { useAIChatStore } from '@/stores/aiChat'
import { useLive2DStore } from '@/stores/live2d'
import MarkdownRenderer from './MarkdownRenderer.vue'

export default {
  name: 'AIChatMessage',

  components: {
    MarkdownRenderer,
  },

  props: {
    message: {
      type: Object,
      required: true,
    },
  },

  emits: ['rendered'],

  setup(props, { emit }) {
    const aiChatStore = useAIChatStore()
    const live2dStore = useLive2DStore()

    const isUser = computed(() => props.message.role === 'user')
    const isAssistant = computed(() => props.message.role === 'assistant')
    const isSystem = computed(() => props.message.role === 'system')
    const hasImages = computed(
      () =>
        Array.isArray(props.message.images) && props.message.images.length > 0
    )
    const hasDocuments = computed(
      () =>
        Array.isArray(props.message.documents) && props.message.documents.length > 0
    )

    const formatFileSize = (bytes) => {
      if (bytes == null || isNaN(bytes)) return ''
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / 1024 / 1024).toFixed(1) + ' MB'
    }

    const showImageViewer = ref(false)
    const previewImages = ref([])
    const previewImageIndex = ref(0)

    const openImagePreview = (index) => {
      if (!Array.isArray(props.message.images) || props.message.images.length === 0) {
        return
      }
      previewImages.value = props.message.images.slice()
      previewImageIndex.value = index
      showImageViewer.value = true
    }

    const prevPreviewImage = () => {
      if (previewImages.value.length <= 1) return
      previewImageIndex.value =
        (previewImageIndex.value - 1 + previewImages.value.length) %
        previewImages.value.length
    }

    const nextPreviewImage = () => {
      if (previewImages.value.length <= 1) return
      previewImageIndex.value =
        (previewImageIndex.value + 1) % previewImages.value.length
    }

    const assistantSegments = computed(() => {
      if (!isAssistant.value) {
        return []
      }

      const normalizeSegments = (segments) =>
        segments.filter((segment) => {
          if (segment.type !== 'text') {
            return true
          }
          return Boolean(segment.content && segment.content.trim())
        })

      // 思考期间（interleaved thinking）调用的工具归组进思考面板：
      // 首个正文段之前、从首个思考段开始的连续区域（思考片段 + 工具，store 在工具
      // 调用时已封存当时的思考段）合成一个 thinking-group，面板内按 segments
      // 原始顺序渲染，还原"思考 → 工具 → 继续思考"的真实时序。
      // 非思考模式的工具（无思考段）不受影响，仍在正文流中显示。
      const groupThinkingTools = (segments) => {
        const firstTextIdx = segments.findIndex((s) => s.type === 'text')
        const boundary = firstTextIdx === -1 ? segments.length : firstTextIdx
        const firstReasoningIdx = segments.findIndex(
          (s, i) => i < boundary && s.type === 'reasoning'
        )
        if (firstReasoningIdx === -1) {
          return segments
        }
        const items = segments.slice(firstReasoningIdx, boundary)
        const lastReasoning = [...items]
          .reverse()
          .find((s) => s.type === 'reasoning')
        return [
          ...segments.slice(0, firstReasoningIdx),
          {
            id: `${items[0].id}-group`,
            type: 'thinking-group',
            status: lastReasoning?.status || 'completed',
            items,
          },
          ...segments.slice(boundary),
        ]
      }

      if (Array.isArray(props.message.segments) && props.message.segments.length) {
        return groupThinkingTools(normalizeSegments(props.message.segments))
      }
      if (props.message.content) {
        return normalizeSegments([
          {
            id: `${props.message.id}-text`,
            type: 'text',
            content: props.message.content,
          },
        ])
      }
      return []
    })

    const themeColor = computed(
      () => aiChatStore.config?.theme_color || '#4facfe'
    )
    const enableTypewriter = computed(() => aiChatStore.typingAnimationEnabled)
    const showTimestamp = computed(() => aiChatStore.showTimestampEnabled)

    // 思考完成后的标题：有用时数据时显示"已思考（用时 N 秒）"，旧数据回退"思考过程"
    const thinkingDoneLabel = computed(() => {
      const { thinkingStartedAt, thinkingEndedAt } = props.message
      if (thinkingStartedAt && thinkingEndedAt) {
        const seconds = Math.max(
          1,
          Math.round((thinkingEndedAt - thinkingStartedAt) / 1000)
        )
        return `已思考（用时 ${seconds} 秒）`
      }
      return '思考过程'
    })

    const formatToolEventLabel = (segment) => {
      const toolName = segment.tool || '未知工具'
      if (segment.status === 'executing') {
        let label = `正在调用 ${toolName}`
        if (segment.queueInfo && segment.queueInfo.queueSize > 0) {
          label += `（排队 ${segment.queueInfo.queueSize} 人，预计 ${segment.queueInfo.maxEstimatedWaitSec}s）`
        }
        return label
      }
      if (segment.status === 'failed') {
        return `${toolName} 调用失败`
      }
      return `${toolName} 已完成`
    }

    const handleCopy = async () => {
      try {
        await navigator.clipboard.writeText(props.message.content)
        live2dStore.showMessage('复制成功！', 3000, 9)
      } catch (error) {
        console.error('复制失败:', error)
        try {
          const textarea = document.createElement('textarea')
          textarea.value = props.message.content
          textarea.style.position = 'fixed'
          textarea.style.opacity = '0'
          document.body.appendChild(textarea)
          textarea.select()
          document.execCommand('copy')
          document.body.removeChild(textarea)
          live2dStore.showMessage('复制成功！', 3000, 9)
        } catch (err) {
          live2dStore.showMessage('复制失败，请手动复制', 3000, 9)
        }
      }
    }

    const handleEdit = () => {
      aiChatStore.startEditMessage(props.message.id, props.message.content)
      live2dStore.showMessage('编辑消息中...', 2000, 9)
    }

    const handleRendered = () => {
      emit('rendered')
    }

    onMounted(() => {
      if (!isAssistant.value) {
        nextTick(() => {
          emit('rendered')
        })
      }
    })

    onUnmounted(() => {
      // 组件销毁时停止 Jina 排队轮询，防止 interval/setTimeout 泄漏
      aiChatStore.stopJinaQueuePolling()
    })

    const messageClass = computed(() => ({
      'message-user': isUser.value,
      'message-assistant': isAssistant.value,
      'message-system': isSystem.value,
    }))

    const formattedTime = computed(() => {
      if (!props.message.timestamp) return ''

      const date = new Date(props.message.timestamp)
      const now = new Date()
      const diff = now - date

      if (diff < 60000) {
        return '刚刚'
      }
      if (diff < 3600000) {
        return `${Math.floor(diff / 60000)}分钟前`
      }
      if (date.toDateString() === now.toDateString()) {
        return date.toLocaleTimeString('zh-CN', {
          hour: '2-digit',
          minute: '2-digit',
        })
      }
      return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    })

    return {
      isUser,
      isAssistant,
      isSystem,
      hasImages,
      hasDocuments,
      formatFileSize,
      showImageViewer,
      previewImages,
      previewImageIndex,
      openImagePreview,
      prevPreviewImage,
      nextPreviewImage,
      assistantSegments,
      messageClass,
      formattedTime,
      themeColor,
      enableTypewriter,
      showTimestamp,
      thinkingDoneLabel,
      formatToolEventLabel,
      handleCopy,
      handleEdit,
      handleRendered,
    }
  },
}
</script>

<style scoped>
.message {
  margin-bottom: 15px;
  animation: slideIn 0.3s ease;
  display: flex;
  flex-direction: column;
  position: relative;
}
@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.message-assistant {
  align-items: flex-start;
}
.message-user {
  align-items: flex-end;
}
.message-row {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: flex-end;
  max-width: 90%;
}
.message-row .message-content {
  flex: 1;
  max-width: 100%;
}
.message-system {
  align-items: center;
}
.message-content {
  padding: 12px 16px;
  border-radius: 18px;
  max-width: 85%;
  word-wrap: break-word;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}
.message-assistant .message-content {
  background: rgba(255, 255, 255, 0.9);
  color: #333;
  border-radius: 18px 18px 18px 4px;
}
.message-user .message-content {
  color: white;
  border-radius: 18px 18px 4px 18px;
  max-width: none;
}
.message-system .message-content {
  background: #e3f2fd;
  color: #1976d2;
  border-radius: 18px;
  font-size: 13px;
  padding: 8px 12px;
}
.attached-page-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  margin-bottom: 8px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.4);
}
.attached-page-badge svg {
  width: 12px;
  height: 12px;
  fill: currentColor;
  flex-shrink: 0;
}
.badge-text {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding: 0 4px;
}
.message-time {
  font-size: 11px;
  color: #999;
}
.message-copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  opacity: 1;
  transition: transform 0.1s;
}
.message-copy-btn:hover {
  transform: scale(1.15);
}
.message-copy-btn:active {
  transform: scale(0.95);
}
.message-copy-btn svg {
  width: 14px;
  height: 14px;
  fill: #999;
  transition: fill 0.2s;
}
.message-copy-btn:hover svg {
  fill: #667eea;
}
.message-edit-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  margin-right: 8px;
  margin-top: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s, transform 0.1s;
  flex-shrink: 0;
}
.message-row:hover .message-edit-btn {
  opacity: 1;
}
.message-edit-btn:hover {
  transform: scale(1.1);
}
.message-edit-btn:active {
  transform: scale(0.95);
}
.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.message-image-thumb {
  max-width: 120px;
  max-height: 120px;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  transition: transform 0.2s;
}
.message-image-thumb:hover {
  transform: scale(1.03);
}
.message-documents {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.message-document-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 240px;
  padding: 4px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.35);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.95);
  overflow: hidden;
}
.message-document-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.9);
}
.message-document-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-document-size {
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.7);
  font-size: 11px;
}
.message-edit-btn svg {
  width: 18px;
  height: 18px;
  fill: #999;
  transition: fill 0.2s;
}
.message-edit-btn:hover svg {
  fill: #667eea;
}
.tool-pill-row {
  margin: 4px 0;
}
.tool-pill-row + .tool-pill-row {
  margin-top: 2px;
}
.tool-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(248, 250, 252, 0.96);
  font-size: 12px;
  line-height: 30px;
  color: #475569;
}
.tool-pill-executing {
  background: rgba(14, 165, 233, 0.1);
  border-color: rgba(14, 165, 233, 0.24);
  color: #0369a1;
}
.tool-pill-completed {
  background: rgba(34, 197, 94, 0.1);
  border-color: rgba(34, 197, 94, 0.24);
  color: #15803d;
}
.tool-pill-failed {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.24);
  color: #b91c1c;
}
.tool-pill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.tool-pill-funnel {
  animation: toolSpin 1s linear infinite;
}
.tool-pill-funnel svg {
  width: 20px;
  height: 20px;
  display: block;
}
.tool-pill-check,
.tool-pill-error {
  font-weight: 700;
  font-size: 12px;
}
.tool-pill-check svg {
  width: 18px;
  height: 18px;
  display: block;
}
.tool-pill-text {
  white-space: nowrap;
}
.reasoning-panel {
  width: 100%;
  margin: 0 0 10px;
}
/* 整体灰色低调化（DeepSeek 风格）：标题、边线均用灰色系 */
.reasoning-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  border-radius: 8px;
  cursor: pointer;
  color: #9ca3af;
  font-size: 12px;
  font-weight: 600;
  user-select: none;
  list-style: none;
  transition: background 0.2s ease;
}
.reasoning-summary::-webkit-details-marker {
  display: none;
}
.reasoning-summary:hover {
  background: rgba(148, 163, 184, 0.14);
}
/* 四角星图标用主题色（与用户气泡一致），其余保持灰色低调 */
.reasoning-icon {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
  display: inline-flex;
  color: var(--link-color, #4facfe);
}
.reasoning-icon svg {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linejoin: round;
}
.is-thinking .reasoning-icon {
  animation: reasoningPulse 1.6s ease-in-out infinite;
}
.reasoning-label {
  flex: 0 0 auto;
}
.reasoning-dots {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.reasoning-dots i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
  animation: reasoningDot 1.2s ease-in-out infinite;
}
.reasoning-dots i:nth-child(2) {
  animation-delay: 0.18s;
}
.reasoning-dots i:nth-child(3) {
  animation-delay: 0.36s;
}
.reasoning-chevron {
  margin-left: auto;
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0.5;
  transition: transform 0.25s ease;
}
.reasoning-panel[open] .reasoning-chevron {
  transform: rotate(90deg);
}
.reasoning-body {
  margin: 2px 0 4px 15px;
  padding: 2px 0 2px 12px;
  border-left: 2px solid rgba(148, 163, 184, 0.4);
  animation: reasoningReveal 0.25s ease;
}
/* 思考中不限高（随内容增长，聊天区自动滚动），完成后限高内部滚动 */
.reasoning-panel:not(.is-thinking) .reasoning-body {
  max-height: 240px;
  overflow-y: auto;
}
/* 思考内容由 MarkdownRenderer 渲染：颜色/字号级联，间距收紧保持面板紧凑 */
.reasoning-content {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.7;
}
.reasoning-content :deep(.markdown-renderer p) {
  margin: 0 0 6px 0;
}
.reasoning-content :deep(.markdown-renderer p:last-child) {
  margin-bottom: 0;
}
/* 工具穿插导致的多段思考：段间留白区分时序 */
.reasoning-content + .reasoning-content {
  margin-top: 6px;
}
/* 工具行样式对齐正文 tool-pill：带边框的胶囊，状态点保留 */
.reasoning-tool {
  display: flex;
  width: fit-content;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(248, 250, 252, 0.96);
  font-size: 12px;
  color: #475569;
}
.reasoning-tool-status {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: 0 0 6px;
}
.reasoning-tool.is-executing {
  background: rgba(14, 165, 233, 0.1);
  border-color: rgba(14, 165, 233, 0.24);
  color: #0369a1;
}
.reasoning-tool.is-executing .reasoning-tool-status {
  background: #0ea5e9;
  animation: reasoningPulse 1.2s ease-in-out infinite;
}
.reasoning-tool.is-completed {
  background: rgba(34, 197, 94, 0.1);
  border-color: rgba(34, 197, 94, 0.24);
  color: #15803d;
}
.reasoning-tool.is-completed .reasoning-tool-status {
  background: #34d399;
}
.reasoning-tool.is-failed {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.24);
  color: #b91c1c;
}
.reasoning-tool.is-failed .reasoning-tool-status {
  background: #f87171;
}
@keyframes reasoningPulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.45;
    transform: scale(0.92);
  }
}
@keyframes reasoningDot {
  0%,
  60%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-2px);
  }
}
@keyframes reasoningReveal {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes toolSpin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
.message-text {
  white-space: pre-wrap;
}
.dark-mode .message-assistant .message-content {
  background: rgba(44, 62, 80, 0.9);
  color: #ecf0f1;
}
.dark-mode .message-user .message-content {
  background: #3498db;
}
.dark-mode .attached-page-badge {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.95);
}
.dark-mode .message-system .message-content {
  background: #1e3a5f;
  color: #64b5f6;
}
.dark-mode .tool-pill {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(148, 163, 184, 0.16);
  color: #cbd5e1;
}
.dark-mode .tool-pill-executing {
  background: rgba(3, 105, 161, 0.22);
  border-color: rgba(56, 189, 248, 0.24);
  color: #7dd3fc;
}
.dark-mode .tool-pill-completed {
  background: rgba(21, 128, 61, 0.24);
  border-color: rgba(74, 222, 128, 0.24);
  color: #86efac;
}
.dark-mode .tool-pill-failed {
  background: rgba(127, 29, 29, 0.24);
  border-color: rgba(248, 113, 113, 0.24);
  color: #fca5a5;
}
.dark-mode .reasoning-summary {
  color: #8b9bb4;
}
.dark-mode .reasoning-summary:hover {
  background: rgba(139, 155, 180, 0.12);
}
.dark-mode .reasoning-body {
  border-left-color: rgba(139, 155, 180, 0.4);
}
.dark-mode .reasoning-content {
  color: #8b9bb4;
}
.dark-mode .reasoning-tool {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(148, 163, 184, 0.16);
  color: #cbd5e1;
}
.dark-mode .reasoning-tool.is-executing {
  background: rgba(3, 105, 161, 0.22);
  border-color: rgba(56, 189, 248, 0.24);
  color: #7dd3fc;
}
.dark-mode .reasoning-tool.is-completed {
  background: rgba(21, 128, 61, 0.24);
  border-color: rgba(74, 222, 128, 0.24);
  color: #86efac;
}
.dark-mode .reasoning-tool.is-failed {
  background: rgba(127, 29, 29, 0.24);
  border-color: rgba(248, 113, 113, 0.24);
  color: #fca5a5;
}
.dark-mode .message-time {
  color: #8e8ea0;
}
.dark-mode .message-copy-btn svg {
  fill: #a0a0b0;
}
.dark-mode .message-copy-btn:hover svg {
  fill: #a29bfe;
}
.dark-mode .message-edit-btn svg {
  fill: #a0a0b0;
}
.dark-mode .message-edit-btn:hover svg {
  fill: #a29bfe;
}

/* 自定义图片预览器样式 */
.custom-image-viewer-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}
.viewer-close-btn {
  position: absolute;
  top: 30px;
  right: 30px;
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  color: #fff;
  font-size: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}
.viewer-close-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: scale(1.05);
}
.viewer-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 50px;
  height: 50px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  z-index: 100000;
}
.viewer-arrow:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-50%) scale(1.05);
}
.viewer-arrow.left {
  left: 30px;
}
.viewer-arrow.right {
  right: 30px;
}
.viewer-content {
  max-width: 80%;
  max-height: 80%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.viewer-img {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  animation: zoomIn 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.viewer-index {
  position: absolute;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  color: #fff;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.4);
  padding: 6px 16px;
  border-radius: 20px;
}
@keyframes zoomIn {
  from {
    transform: scale(0.95);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
