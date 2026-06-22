<template>
  <div class="chat-input-wrapper" :style="themeStyleVars">
    <!-- 编辑模式提示条 -->
    <div v-if="isEditing" class="edit-mode-bar">
      <span class="edit-mode-text">✂️ 编辑消息中...</span>
      <button class="cancel-edit-btn" @click="handleCancelEdit">取消</button>
    </div>

    <!-- 已附加页面提示条 -->
    <div v-if="attachedPage" class="attached-page-bar">
      <div class="attached-page-info">
        <svg
          class="page-icon"
          viewBox="0 0 1024 1024"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M854.6 288.6L639.4 73.4c-6-6-14.1-9.4-22.6-9.4H192c-17.7 0-32 14.3-32 32v832c0 17.7 14.3 32 32 32h640c17.7 0 32-14.3 32-32V311.3c0-8.5-3.4-16.7-9.4-22.7zM790.2 326H602V137.8L790.2 326z m1.8 562H232V136h302v216c0 23.2 18.8 42 42 42h216v494z"
            fill="currentColor"
          />
        </svg>
        <span class="page-title">{{ attachedPage.title }}</span>
      </div>
      <button
        class="remove-page-btn"
        @click="handleRemovePage"
        title="移除附加页面"
      >
        ×
      </button>
    </div>

    <!-- 已附加图片预览条 -->
    <div v-if="attachedImages.length > 0" class="attached-images-bar">
      <div class="attached-images-list">
        <div
          v-for="(img, index) in attachedImages"
          :key="index"
          class="attached-image-item"
        >
          <img :src="img.url" :alt="img.name || '图片'" class="attached-image-thumb" />
          <button
            class="remove-image-btn"
            @click="handleRemoveImage(index)"
            title="移除图片"
          >
            ×
          </button>
        </div>
      </div>
      <button
        v-if="attachedImages.length > 0"
        class="clear-images-btn"
        @click="handleClearImages"
        title="清空所有图片"
      >
        清空
      </button>
    </div>

    <!-- 已附加文档预览条 -->
    <div v-if="attachedDocuments.length > 0" class="attached-documents-bar">
      <div class="attached-documents-list">
        <div
          v-for="(doc, index) in attachedDocuments"
          :key="index"
          class="attached-document-item"
          :class="{ 'is-parsing': doc.parsing }"
        >
          <svg class="document-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M854.6 288.6L639.4 73.4c-6-6-14.1-9.4-22.6-9.4H192c-17.7 0-32 14.3-32 32v832c0 17.7 14.3 32 32 32h640c17.7 0 32-14.3 32-32V311.3c0-8.5-3.4-16.7-9.4-22.7zM790.2 326H602V137.8L790.2 326z m1.8 562H232V136h302v216c0 23.2 18.8 42 42 42h216v494z"
              fill="currentColor"
            />
          </svg>
          <div class="document-info">
            <span class="document-name" :title="doc.name">{{ doc.name }}</span>
            <span class="document-meta">
              <span v-if="doc.parsing" class="document-status parsing">解析中...</span>
              <span v-else-if="doc.error" class="document-status error">{{ doc.error }}</span>
              <span v-else class="document-status ready">{{ formatFileSize(doc.size) }} · 已就绪</span>
            </span>
          </div>
          <button
            class="remove-document-btn"
            @click="handleRemoveDocument(index)"
            title="移除文档"
          >
            ×
          </button>
        </div>
      </div>
      <button
        v-if="attachedDocuments.length > 0"
        class="clear-documents-btn"
        @click="handleClearDocuments"
        title="清空所有文档"
      >
        清空
      </button>
    </div>



    <!-- 附加按钮区域（弹出菜单：页面 / 图片） -->
    <div v-if="!attachedPage" class="attach-page-container">
      <button
        class="attach-page-btn"
        :disabled="sending"
        @click="toggleAttachMenu"
        title="附加内容"
      >
        <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M750.933333 648.567467V256a153.6 153.6 0 1 0-307.2 0V682.666667a68.266667 68.266667 0 1 0 136.533334 0V341.333333a34.133333 34.133333 0 1 1 68.266666 0v341.333334a136.533333 136.533333 0 0 1-273.066666 0V256a221.866667 221.866667 0 1 1 443.733333 0V682.666667a307.2 307.2 0 1 1-614.4 0V273.066667a34.133333 34.133333 0 1 1 68.266667 0v409.6a238.933333 238.933333 0 1 0 477.866666 0v-34.0992z"
            fill="currentColor"
          />
        </svg>
        <span class="attach-text">附加</span>
      </button>

      <!-- 附加内容弹出菜单 -->
      <transition name="attach-menu">
        <div v-if="showAttachMenu" class="attach-menu" @click.stop>
          <button class="attach-menu-item" @click="handleAttachPage">
            <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
              <path
                d="M854.6 288.6L639.4 73.4c-6-6-14.1-9.4-22.6-9.4H192c-17.7 0-32 14.3-32 32v832c0 17.7 14.3 32 32 32h640c17.7 0 32-14.3 32-32V311.3c0-8.5-3.4-16.7-9.4-22.7zM790.2 326H602V137.8L790.2 326z m1.8 562H232V136h302v216c0 23.2 18.8 42 42 42h216v494z"
                fill="currentColor"
              />
            </svg>
            <span>页面</span>
          </button>
          <button
            v-if="visionEnabled"
            class="attach-menu-item"
            @click="triggerImageUpload"
          >
            <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
              <path
                d="M864 128H160C107 128 64 171 64 224v576c0 53 43 96 96 96h704c53 0 96-43 96-96V224c0-53-43-96-96-96z m0 672H160V224h704v576z M320 384m-64 0a64 64 0 1 0 128 0 64 64 0 1 0-128 0Z M832 736H192v-64l160-160 128 128 192-192 160 160z"
                fill="currentColor"
              />
            </svg>
            <span>图片</span>
          </button>
          <button
            class="attach-menu-item"
            @click="triggerDocumentUpload"
          >
            <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
              <path
                d="M854.6 288.6L639.4 73.4c-6-6-14.1-9.4-22.6-9.4H192c-17.7 0-32 14.3-32 32v832c0 17.7 14.3 32 32 32h640c17.7 0 32-14.3 32-32V311.3c0-8.5-3.4-16.7-9.4-22.7zM790.2 326H602V137.8L790.2 326z m1.8 562H232V136h302v216c0 23.2 18.8 42 42 42h216v494z"
                fill="currentColor"
              />
            </svg>
            <span>文档</span>
          </button>
        </div>
      </transition>

      <!-- 隐藏的图片上传 input -->
      <input
        ref="imageInputRef"
        type="file"
        accept="image/*"
        multiple
        style="display: none"
        @change="handleImageSelected"
      />

      <!-- 隐藏的文档上传 input -->
      <input
        ref="documentInputRef"
        type="file"
        accept=".txt,.md,.markdown,.csv,.json,.pdf,.doc,.docx,.wps,.ppt,.pptx,.xls,.xlsx"
        multiple
        style="display: none"
        @change="handleDocumentSelected"
      />
    </div>

    <div class="chat-input-container">
      <textarea
        ref="inputRef"
        v-model="localValue"
        class="chat-input"
        :placeholder="placeholder"
        :disabled="sending"
        rows="1"
        @keydown.enter.exact="handleKeyDown"
        @input="handleInput"
      />

      <!-- 发送按钮（正常状态） -->
      <button
        v-if="!streaming"
        class="send-btn"
        :disabled="!canSend"
        @click="handleSend"
      >
        发送
      </button>

      <!-- 停止按钮（AI生成时） -->
      <button v-else class="stop-btn" @click="handleStop">
        <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M722.9375 933.875H301.0625a210.9375 210.9375 0 0 1-210.9375-210.9375V301.0625a210.9375 210.9375 0 0 1 210.9375-210.9375h421.875a210.9375 210.9375 0 0 1 210.9375 210.9375v421.875a210.9375 210.9375 0 0 1-210.9375 210.9375z"
          />
        </svg>
        <span>停止</span>
      </button>
    </div>
  </div>
</template>

<script>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useAIChatStore } from '@/stores/aiChat'

const DEFAULT_THEME_COLOR = '#4facfe'

const clampChannel = (value) => Math.min(255, Math.max(0, Math.round(value)))

const parseColorToRgb = (color) => {
  if (typeof color !== 'string') {
    return null
  }

  const value = color.trim()
  const hexMatch = value.match(/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/)
  if (hexMatch) {
    const hex = hexMatch[1]
    const normalized =
      hex.length === 3
        ? hex
            .split('')
            .map((char) => char + char)
            .join('')
        : hex

    return {
      r: parseInt(normalized.slice(0, 2), 16),
      g: parseInt(normalized.slice(2, 4), 16),
      b: parseInt(normalized.slice(4, 6), 16),
    }
  }

  const rgbMatch = value.match(
    /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*(?:0|1|0?\.\d+))?\s*\)$/
  )
  if (rgbMatch) {
    return {
      r: clampChannel(Number(rgbMatch[1])),
      g: clampChannel(Number(rgbMatch[2])),
      b: clampChannel(Number(rgbMatch[3])),
    }
  }

  return null
}

const darkenRgb = (rgb, amount) => ({
  r: clampChannel(rgb.r * (1 - amount)),
  g: clampChannel(rgb.g * (1 - amount)),
  b: clampChannel(rgb.b * (1 - amount)),
})

const toCssRgb = (rgb) => `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`

export default {
  name: 'AIChatInput',
  props: {
    modelValue: {
      type: String,
      default: '',
    },
    placeholder: {
      type: String,
      default: '输入消息...',
    },
    sending: {
      type: Boolean,
      default: false,
    },
    streaming: {
      type: Boolean,
      default: false,
    },
    isEditing: {
      type: Boolean,
      default: false,
    },
  },
  setup(props, { emit }) {
    const aiChatStore = useAIChatStore()
    const inputRef = ref(null)
    const imageInputRef = ref(null)
    const documentInputRef = ref(null)
    const localValue = ref(props.modelValue)
    const showAttachMenu = ref(false)
    const imageUploading = ref(false)
    const documentUploading = ref(false)
    const themeColor = computed(() => aiChatStore.themeColor || DEFAULT_THEME_COLOR)
    const themeStyleVars = computed(() => {
      const rgb =
        parseColorToRgb(themeColor.value) ||
        parseColorToRgb(DEFAULT_THEME_COLOR)

      const hoverRgb = darkenRgb(rgb, 0.12)
      const deepRgb = darkenRgb(rgb, 0.24)

      return {
        '--ai-chat-theme-color': toCssRgb(rgb),
        '--ai-chat-theme-color-hover': toCssRgb(hoverRgb),
        '--ai-chat-theme-color-deep': toCssRgb(deepRgb),
        '--ai-chat-theme-rgb': `${rgb.r}, ${rgb.g}, ${rgb.b}`,
      }
    })

    // 检测是否为移动端
    const isMobile = computed(() => window.innerWidth <= 768)

    // 已附加的页面
    const attachedPage = computed(() => aiChatStore.attachedPageContext)

    // 已附加的图片
    const attachedImages = computed(() => aiChatStore.attachedImages)

    // 已附加的文档
    const attachedDocuments = computed(() => aiChatStore.attachedDocuments)

    // 视觉能力是否启用（控制图片上传入口可见性）
    const visionEnabled = computed(() => aiChatStore.visionEnabled)

    // 是否可发送（文档解析中禁止发送，避免丢失正在解析的附件）
    const canSend = computed(() => {
      const hasParsingDoc = (aiChatStore.attachedDocuments || []).some((doc) => doc.parsing)
      return localValue.value.trim().length > 0 && !props.sending && !hasParsingDoc
    })

    /**
     * 切换附加菜单显示
     */
    const toggleAttachMenu = () => {
      showAttachMenu.value = !showAttachMenu.value
    }

    /**
     * 关闭附加菜单（点击外部时）
     */
    const closeAttachMenu = (event) => {
      const container = event.target.closest('.attach-page-container')
      if (!container) {
        showAttachMenu.value = false
      }
    }

    /**
     * 触发图片上传
     */
    const triggerImageUpload = () => {
      showAttachMenu.value = false
      if (imageInputRef.value) {
        imageInputRef.value.value = ''
        imageInputRef.value.click()
      }
    }

    /**
     * 触发文档上传
     */
    const triggerDocumentUpload = () => {
      showAttachMenu.value = false
      if (documentInputRef.value) {
        documentInputRef.value.value = ''
        documentInputRef.value.click()
      }
    }

    /**
     * 格式化文件大小
     */
    const formatFileSize = (bytes) => {
      if (!bytes || bytes <= 0) return '0 B'
      const units = ['B', 'KB', 'MB', 'GB']
      let size = bytes
      let unitIndex = 0
      while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024
        unitIndex++
      }
      return `${size.toFixed(size < 10 ? 1 : 0)} ${units[unitIndex]}`
    }

    /**
     * 处理文档选择
     * 调用后端 /ai/chat/parseDocument 解析文档文本，存入 store
     */
    const handleDocumentSelected = async (event) => {
      const files = event.target.files
      if (!files || files.length === 0) return

      // 检查文档数量限制
      const remaining = 4 - aiChatStore.attachedDocuments.length
      if (remaining <= 0) {
        emit('document-upload-error', '最多只能附加4个文档')
        return
      }

      const toUpload = Array.from(files).slice(0, remaining)
      documentUploading.value = true

      try {
        for (const file of toUpload) {
          // 校验文件大小（最大 20MB）
          if (file.size > 20 * 1024 * 1024) {
            emit('document-upload-error', `文档 ${file.name} 大小不能超过20MB`)
            continue
          }
          const result = await aiChatStore.attachDocumentFile(file)
          if (!result.success) {
            emit('document-upload-error', `文档 ${file.name} 解析失败：${result.error || '未知错误'}`)
          }
        }
        emit('documents-attached', aiChatStore.attachedDocuments)
      } catch (error) {
        console.error('文档处理失败:', error)
        emit('document-upload-error', error.message || '文档处理失败')
      } finally {
        documentUploading.value = false
      }
    }

    /**
     * 移除指定文档
     */
    const handleRemoveDocument = (index) => {
      aiChatStore.removeAttachedDocument(index)
    }

    /**
     * 清空所有文档
     */
    const handleClearDocuments = () => {
      aiChatStore.clearAttachedDocuments()
    }

    /**
     * 处理图片选择
     * 图片在本地压缩为 base64，存入 IndexedDB，不再上传到服务器
     */
    const handleImageSelected = async (event) => {
      const files = event.target.files
      if (!files || files.length === 0) return

      // 检查图片数量限制
      const remaining = 4 - aiChatStore.attachedImages.length
      if (remaining <= 0) {
        emit('image-upload-error', '最多只能上传4张图片')
        return
      }

      const toUpload = Array.from(files).slice(0, remaining)
      imageUploading.value = true

      try {
        for (const file of toUpload) {
          // 校验文件类型和大小（最大 5MB，压缩前）
          if (!file.type.startsWith('image/')) {
            emit('image-upload-error', '只能上传图片文件')
            continue
          }
          if (file.size > 5 * 1024 * 1024) {
            emit('image-upload-error', '图片大小不能超过5MB')
            continue
          }

          // 压缩并存储到 IndexedDB，返回是否成功
          const success = await aiChatStore.attachImageFile(file)
          if (!success) {
            emit('image-upload-error', '图片处理失败，可能超出总大小限制')
          }
        }
        emit('images-attached', aiChatStore.attachedImages)
      } catch (error) {
        console.error('图片处理失败:', error)
        emit('image-upload-error', error.message || '图片处理失败')
      } finally {
        imageUploading.value = false
      }
    }

    /**
     * 移除指定图片
     */
    const handleRemoveImage = (index) => {
      aiChatStore.removeAttachedImage(index)
    }

    /**
     * 清空所有图片
     */
    const handleClearImages = () => {
      aiChatStore.clearAttachedImages()
    }

    /**
     * 输入处理
     */
    const handleInput = () => {
      emit('update:modelValue', localValue.value)
      adjustTextareaHeight()
    }

    /**
     * 处理键盘事件
     */
    const handleKeyDown = (e) => {
      // 移动端不处理回车发送，允许换行
      if (isMobile.value) {
        return
      }

      // PC端：回车发送
      e.preventDefault()
      handleSend()
    }

    /**
     * 发送消息
     */
    const handleSend = () => {
      if (canSend.value) {
        emit('send')
      } else {
      }
    }

    /**
     * 停止生成
     */
    const handleStop = () => {
      emit('stop')
    }

    /**
     * 取消编辑
     */
    const handleCancelEdit = () => {
      emit('cancel-edit')
    }

    /**
     * 附加当前页面
     */
    const handleAttachPage = () => {
      showAttachMenu.value = false
      const success = aiChatStore.attachCurrentPage()
      if (success) {
        // 可以发射事件通知父组件显示提示
        emit('page-attached', aiChatStore.attachedPageContext)
      } else {
      }
    }

    /**
     * 移除附加的页面
     */
    const handleRemovePage = () => {
      aiChatStore.removeAttachedPage()
      emit('page-removed')
    }

    /**
     * 自动调整输入框高度
     */
    const adjustTextareaHeight = () => {
      nextTick(() => {
        const textarea = inputRef.value
        if (!textarea) return

        // 重置高度
        textarea.style.height = 'auto'

        // 计算新高度（最多5行）
        const maxHeight = 120 // 约5行
        const newHeight = Math.min(textarea.scrollHeight, maxHeight)

        textarea.style.height = `${newHeight}px`
      })
    }

    // 监听外部值变化
    watch(
      () => props.modelValue,
      (newVal) => {
        localValue.value = newVal
        adjustTextareaHeight()
      }
    )

    // 组件挂载时添加全局点击监听（用于关闭附加菜单）
    onMounted(() => {
      document.addEventListener('click', closeAttachMenu)
    })

    onBeforeUnmount(() => {
      document.removeEventListener('click', closeAttachMenu)
    })

    return {
      inputRef,
      imageInputRef,
      documentInputRef,
      localValue,
      themeStyleVars,
      isMobile,
      canSend,
      attachedPage,
      attachedImages,
      attachedDocuments,
      visionEnabled,
      showAttachMenu,
      imageUploading,
      documentUploading,
      handleInput,
      handleKeyDown,
      handleSend,
      handleStop,
      handleCancelEdit,
      handleAttachPage,
      handleRemovePage,
      toggleAttachMenu,
      triggerImageUpload,
      triggerDocumentUpload,
      handleImageSelected,
      handleRemoveImage,
      handleClearImages,
      handleDocumentSelected,
      handleRemoveDocument,
      handleClearDocuments,
      formatFileSize,
    }
  },
  emits: [
    'update:modelValue',
    'send',
    'stop',
    'cancel-edit',
    'page-attached',
    'page-removed',
    'images-attached',
    'image-upload-error',
    'documents-attached',
    'document-upload-error',
  ],
}
</script>

<style scoped>
.chat-input-wrapper {
  --ai-chat-theme-color: #4facfe;
  --ai-chat-theme-color-hover: #3498db;
  --ai-chat-theme-color-deep: #2f7eb8;
  --ai-chat-theme-rgb: 79, 172, 254;
  display: flex;
  flex-direction: column;
}
.edit-mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  background: linear-gradient(90deg, #fff3cd 0%, #ffe8a1 100%);
  border-top: 1px solid rgba(255, 193, 7, 0.3);
  font-size: 13px;
  color: #856404;
}
.edit-mode-text {
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}
.cancel-edit-btn {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(108, 117, 125, 0.3);
  border-radius: 4px;
  color: #6c757d;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.cancel-edit-btn:hover {
  background: white;
  border-color: #6c757d;
  color: #495057;
}
.attached-page-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  background: linear-gradient(90deg, rgba(var(--ai-chat-theme-rgb), 0.12) 0%, rgba(var(--ai-chat-theme-rgb), 0.2) 100%);
  border-top: 1px solid rgba(var(--ai-chat-theme-rgb), 0.26);
  font-size: 13px;
  color: var(--ai-chat-theme-color-deep);
}
.attached-page-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.page-icon {
  width: 16px;
  height: 16px;
  fill: currentColor;
  flex-shrink: 0;
}
.page-title {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.remove-page-btn {
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(var(--ai-chat-theme-rgb), 0.26);
  border-radius: 4px;
  color: var(--ai-chat-theme-color-deep);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.remove-page-btn:hover {
  background: white;
  border-color: var(--ai-chat-theme-color);
  color: var(--ai-chat-theme-color);
}
.attach-page-container {
  padding: 10px 20px 5px;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  position: relative;
}
.attach-menu {
  position: absolute;
  bottom: 100%;
  left: 20px;
  margin-bottom: 8px;
  background: rgba(248, 249, 250, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 12px;
  filter: drop-shadow(0 4px 16px rgba(0, 0, 0, 0.12));
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  z-index: 100;
  min-width: 110px;
}
.attach-menu::before {
  content: '';
  position: absolute;
  bottom: -5px;
  left: 30px;
  width: 12px;
  height: 12px;
  background: inherit;
  transform: rotate(45deg);
  border-bottom-right-radius: 3px;
  z-index: -1;
}
.attach-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #2c3e50;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  white-space: nowrap;
}
.attach-menu-item:hover {
  background: rgba(var(--ai-chat-theme-rgb), 0.1);
  color: var(--ai-chat-theme-color);
}
.attach-menu-item svg {
  width: 16px;
  height: 16px;
  fill: currentColor;
  flex-shrink: 0;
}
.attach-menu-enter-active,
.attach-menu-leave-active {
  transition: all 0.2s ease;
}
.attach-menu-enter-from,
.attach-menu-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
.attached-images-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 20px;
  background: linear-gradient(90deg, rgba(var(--ai-chat-theme-rgb), 0.08) 0%, rgba(var(--ai-chat-theme-rgb), 0.14) 100%);
  border-top: 1px solid rgba(var(--ai-chat-theme-rgb), 0.2);
}
.attached-images-list {
  display: flex;
  gap: 8px;
  flex: 1;
  overflow-x: auto;
  min-width: 0;
}
.attached-images-list::-webkit-scrollbar {
  height: 4px;
}
.attached-images-list::-webkit-scrollbar-thumb {
  background: rgba(var(--ai-chat-theme-rgb), 0.3);
  border-radius: 2px;
}
.attached-image-item {
  position: relative;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid rgba(var(--ai-chat-theme-rgb), 0.3);
}
.attached-image-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.remove-image-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 16px;
  height: 16px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition: background 0.2s;
}
.remove-image-btn:hover {
  background: rgba(255, 59, 48, 0.85);
}
.clear-images-btn {
  flex-shrink: 0;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(var(--ai-chat-theme-rgb), 0.26);
  border-radius: 4px;
  color: var(--ai-chat-theme-color-deep);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.clear-images-btn:hover {
  background: white;
  border-color: var(--ai-chat-theme-color);
  color: var(--ai-chat-theme-color);
}
.attached-documents-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 20px;
  background: linear-gradient(90deg, rgba(var(--ai-chat-theme-rgb), 0.08) 0%, rgba(var(--ai-chat-theme-rgb), 0.14) 100%);
  border-top: 1px solid rgba(var(--ai-chat-theme-rgb), 0.2);
}
.attached-documents-list {
  display: flex;
  gap: 8px;
  flex: 1;
  overflow-x: auto;
  min-width: 0;
}
.attached-documents-list::-webkit-scrollbar {
  height: 4px;
}
.attached-documents-list::-webkit-scrollbar-thumb {
  background: rgba(var(--ai-chat-theme-rgb), 0.3);
  border-radius: 2px;
}
.attached-document-item {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  padding-right: 24px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(var(--ai-chat-theme-rgb), 0.3);
  border-radius: 6px;
  min-width: 140px;
  max-width: 240px;
}
.attached-document-item.is-parsing {
  opacity: 0.7;
}
.document-icon {
  width: 18px;
  height: 18px;
  fill: var(--ai-chat-theme-color);
  flex-shrink: 0;
}
.document-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.document-name {
  font-size: 12px;
  font-weight: 500;
  color: #2c3e50;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.document-meta {
  font-size: 11px;
  color: #6c757d;
}
.document-status.ready {
  color: #28a745;
}
.document-status.parsing {
  color: #ffc107;
}
.document-status.error {
  color: #dc3545;
}
.remove-document-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 16px;
  height: 16px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition: background 0.2s;
}
.remove-document-btn:hover {
  background: rgba(255, 59, 48, 0.85);
}
.clear-documents-btn {
  flex-shrink: 0;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(var(--ai-chat-theme-rgb), 0.26);
  border-radius: 4px;
  color: var(--ai-chat-theme-color-deep);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.clear-documents-btn:hover {
  background: white;
  border-color: var(--ai-chat-theme-color);
  color: var(--ai-chat-theme-color);
}
.chat-input-container {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px 20px 15px;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}
.attach-page-btn {
  flex-shrink: 0;
  padding: 0 12px;
  height: 32px;
  border: 1px solid rgba(var(--ai-chat-theme-rgb), 0.42);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  color: var(--ai-chat-theme-color);
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}
.attach-page-btn:hover:not(:disabled) {
  background: rgba(var(--ai-chat-theme-rgb), 0.08);
  border-color: var(--ai-chat-theme-color);
  box-shadow: 0 2px 8px rgba(var(--ai-chat-theme-rgb), 0.28);
}
.attach-page-btn:active:not(:disabled) {
  transform: scale(0.95);
}
.attach-page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.attach-page-btn svg {
  width: 14px;
  height: 14px;
  fill: currentColor;
  flex-shrink: 0;
}
.attach-text {
  color: currentColor;
}

.chat-input {
  flex: 1;
  min-height: 40px;
  max-height: 120px;
  padding: 10px 15px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 20px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: all 0.3s ease;
  overflow-y: hidden;
  background: rgba(255, 255, 255, 0.6);
  color: #2c3e50;
}
.chat-input::-webkit-scrollbar {
  display: none;
}
.chat-input:focus {
  border-color: rgba(var(--ai-chat-theme-rgb), 0.65);
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 0 0 3px rgba(var(--ai-chat-theme-rgb), 0.12);
}
.chat-input:disabled {
  background: rgba(245, 245, 245, 0.6);
  cursor: not-allowed;
}
.chat-input::placeholder {
  color: #999;
}
.send-btn {
  flex-shrink: 0;
  padding: 0 20px;
  height: 40px;
  border: none;
  border-radius: 20px;
  background: var(--ai-chat-theme-color);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(var(--ai-chat-theme-rgb), 0.32);
}
.send-btn:hover:not(:disabled) {
  background: var(--ai-chat-theme-color-hover);
  box-shadow: 0 4px 12px rgba(var(--ai-chat-theme-rgb), 0.42);
}
.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}
.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}
.stop-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 18px;
  height: 40px;
  border: none;
  border-radius: 20px;
  background: rgba(255, 59, 48, 0.1);
  border: 1px solid rgba(255, 59, 48, 0.3);
  color: #ff3b30;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.stop-btn:hover {
  background: rgba(255, 59, 48, 0.2);
  border-color: rgba(255, 59, 48, 0.5);
  box-shadow: 0 2px 8px rgba(255, 59, 48, 0.3);
}
.stop-btn:active {
  transform: scale(0.95);
}
.stop-btn svg {
  width: 14px;
  height: 14px;
  fill: currentColor;
}

.dark-mode .chat-input-container {
  background: rgba(0, 0, 0, 0.2);
  border-top-color: rgba(255, 255, 255, 0.1);
}
.dark-mode .chat-input {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(255, 255, 255, 0.2);
  color: #e0e0e0;
}
.dark-mode .chat-input:focus {
  border-color: rgba(var(--ai-chat-theme-rgb), 0.72);
  background: rgba(0, 0, 0, 0.4);
  box-shadow: 0 0 0 3px rgba(var(--ai-chat-theme-rgb), 0.2);
}
.dark-mode .chat-input:disabled {
  background: #252525;
}
.dark-mode .chat-input::placeholder {
  color: #888;
}
.dark-mode .edit-mode-bar {
  background: linear-gradient(90deg, #856404 0%, #aa7a04 100%);
  border-top-color: rgba(255, 193, 7, 0.2);
  color: #ffd54f;
}
.dark-mode .cancel-edit-btn {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(255, 255, 255, 0.2);
  color: #e0e0e0;
}
.dark-mode .cancel-edit-btn:hover {
  background: rgba(0, 0, 0, 0.5);
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
}
.dark-mode .attached-page-bar {
  background: linear-gradient(90deg, rgba(var(--ai-chat-theme-rgb), 0.24) 0%, rgba(var(--ai-chat-theme-rgb), 0.36) 100%);
  border-top-color: rgba(var(--ai-chat-theme-rgb), 0.3);
  color: #eef4ff;
}
.dark-mode .remove-page-btn {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(var(--ai-chat-theme-rgb), 0.35);
  color: #eef4ff;
}
.dark-mode .remove-page-btn:hover {
  background: rgba(var(--ai-chat-theme-rgb), 0.18);
  border-color: var(--ai-chat-theme-color);
  color: #fff;
}
.dark-mode .attach-page-container {
  background: rgba(0, 0, 0, 0.2);
  border-top-color: rgba(255, 255, 255, 0.1);
}
.dark-mode .attach-page-btn {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(var(--ai-chat-theme-rgb), 0.45);
  color: var(--ai-chat-theme-color);
}
.dark-mode .attach-page-btn:hover:not(:disabled) {
  background: rgba(var(--ai-chat-theme-rgb), 0.18);
  border-color: var(--ai-chat-theme-color);
  box-shadow: 0 2px 10px rgba(var(--ai-chat-theme-rgb), 0.28);
}
.dark-mode .attach-menu {
  background: rgba(54, 54, 54, 0.95);
  filter: drop-shadow(0 4px 16px rgba(0, 0, 0, 0.3));
}
.dark-mode .attach-menu-item {
  color: #e0e0e0;
}
.dark-mode .attach-menu-item:hover {
  background: rgba(var(--ai-chat-theme-rgb), 0.22);
  color: var(--ai-chat-theme-color);
}
.dark-mode .attached-images-bar {
  background: linear-gradient(90deg, rgba(var(--ai-chat-theme-rgb), 0.2) 0%, rgba(var(--ai-chat-theme-rgb), 0.3) 100%);
  border-top-color: rgba(var(--ai-chat-theme-rgb), 0.3);
}
.dark-mode .attached-image-item {
  border-color: rgba(var(--ai-chat-theme-rgb), 0.4);
}
.dark-mode .clear-images-btn {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(var(--ai-chat-theme-rgb), 0.35);
  color: #eef4ff;
}
.dark-mode .clear-images-btn:hover {
  background: rgba(var(--ai-chat-theme-rgb), 0.18);
  border-color: var(--ai-chat-theme-color);
  color: #fff;
}
.dark-mode .attached-documents-bar {
  background: linear-gradient(90deg, rgba(var(--ai-chat-theme-rgb), 0.2) 0%, rgba(var(--ai-chat-theme-rgb), 0.3) 100%);
  border-top-color: rgba(var(--ai-chat-theme-rgb), 0.3);
}
.dark-mode .attached-document-item {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(var(--ai-chat-theme-rgb), 0.4);
}
.dark-mode .document-name {
  color: #eef4ff;
}
.dark-mode .document-meta {
  color: #a8b3cf;
}
.dark-mode .clear-documents-btn {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(var(--ai-chat-theme-rgb), 0.35);
  color: #eef4ff;
}
.dark-mode .clear-documents-btn:hover {
  background: rgba(var(--ai-chat-theme-rgb), 0.18);
  border-color: var(--ai-chat-theme-color);
  color: #fff;
}
</style>
