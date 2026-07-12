<template>
  <div>
    <!-- 框 -->
    <div
      ref="commentInputWrapper"
      class="comment-input-wrapper"
      @click="focusEditor"
    >
      <div
        ref="commentEditor"
        class="comment-editor"
        :class="{ 'is-empty': !commentContent }"
        :data-placeholder="commentPlaceholder"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        spellcheck="false"
        @beforeinput="handleEditorBeforeInput"
        @input="handleEditorInput"
        @keydown="handleEditorKeydown"
        @keyup="handleEditorSelectionChange"
        @click="handleEditorSelectionChange"
        @paste="handleEditorPaste"
        @blur="handleEditorBlur"
      ></div>

      <transition name="mention-pop">
        <div
          v-if="showMentionPanel"
          class="mention-panel"
          @mousedown.prevent
        >
          <div class="mention-scroll-area">
            <div class="mention-option" @mousedown.prevent="selectBotMention">
              <img
                v-if="resolvedBotMentionAvatar"
                class="mention-avatar"
                :src="resolvedBotMentionAvatar"
                alt=""
                @error="handleBotAvatarError"
              />
              <div v-else class="mention-avatar mention-avatar-fallback">AI</div>
              <div class="mention-name">
                {{ botMentionName }}
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
    <!-- 按钮 -->
    <div class="myBetween" style="margin-bottom: 10px">
      <div style="display: flex">
        <div
          :class="{ 'emoji-active': showEmoji }"
          @click="showEmoji = !showEmoji"
        >
          <el-icon class="myEmoji"><el-icon-orange /></el-icon>
        </div>
        <div @click="openPicture()">
          <el-icon class="myPicture"><el-icon-picture /></el-icon>
        </div>
      </div>

      <div style="display: flex">
        <!--        <proButton :info="'涂鸦'"-->
        <!--                   v-show="!$common.mobile() && !disableGraffiti"-->
        <!--                   @click.native="showGraffiti()"-->
        <!--                   :before="$constant.before_color_1"-->
        <!--                   :after="$constant.after_color_1"-->
        <!--                   style="margin-right: 6px">-->
        <!--        </proButton>-->
        <proButton
          :info="'提交'"
          @click="submitComment()"
          :before="$constant.before_color_2"
          :after="$constant.after_color_2"
        >
        </proButton>
      </div>
    </div>
    <!-- 表情 -->
    <emoji @addEmoji="addEmoji" :showEmoji="showEmoji"></emoji>

    <el-dialog
      title="图片"
      v-model="showPicture"
      width="25%"
      :append-to-body="true"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      destroy-on-close
      center
    >
      <div>
        <uploadPicture
          :prefix="'commentPicture'"
          @addPicture="addPicture"
          :maxSize="5"
          :maxNumber="1"
        ></uploadPicture>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import {
  Orange as ElIconOrange,
  Picture as ElIconPicture,
} from '@element-plus/icons-vue'
import { useMainStore } from '@/stores/main'
import { useAIChatStore } from '@/stores/aiChat'


export default {
  components: {
    emoji: defineAsyncComponent(() => import('../common/emoji')),
    proButton: defineAsyncComponent(() => import('../common/proButton')),
    uploadPicture: defineAsyncComponent(() => import('../common/uploadPicture')),
    ElIconOrange,
    ElIconPicture,
  },
  props: {
    disableGraffiti: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      commentContent: '',
      showEmoji: false,
      showPicture: false,
      showMentionPanel: false,
      mentionTriggerRange: null,
      botMentionAvatarBroken: false,
      picture: {
        name: '',
        url: '',
      },
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    aiChatStore() {
      return useAIChatStore()
    },
    botMentionName() {
      return (
        this.aiChatStore.config?.chat_name ||
        this.aiChatStore.config?.chatName ||
        'AI助手'
      )
    },
    aiCommentMentionEnabled() {
      return (
        this.mainStore.webInfo?.enableWaifu === true &&
        this.aiChatStore.config?.enabled === true
      )
    },
    commentPlaceholder() {
      if (!this.aiCommentMentionEnabled) {
        return '说点什么...'
      }
      return `说点什么... 想让 AI 回你？试试 @${this.botMentionName}`
    },
    botMentionAvatar() {
      const config = this.aiChatStore.config
      if (!config) return ''
      // 优先使用 API 返回的 snake_case 字段（空字符串不应回退到 camelCase 缓存值）
      if ('chat_avatar' in config) {
        return config.chat_avatar || ''
      }
      // 向后兼容：camelCase 缓存值
      return config.chatAvatar || ''
    },
    resolvedBotMentionAvatar() {
      if (this.botMentionAvatarBroken) {
        return this.$common.getAiDefaultAvatar()
      }
      if (!this.botMentionAvatar || !this.botMentionAvatar.trim()) {
        return this.$common.getAiDefaultAvatar()
      }
      return this.botMentionAvatar
    },
  },
  watch: {
    botMentionAvatar() {
      this.botMentionAvatarBroken = false
    },
    aiCommentMentionEnabled(enabled) {
      if (!enabled) {
        this.hideMentionPanel()
      }
    },
  },
  mounted() {
    this.aiChatStore.loadConfig().catch(() => {})

    // 监听恢复评论的事件
    $on(this.$bus, 'restore-comment', (comment) => {
      if (comment) {
        this.setEditorContentFromText(comment)
      }
    })

    // 初始化图片名称为当前用户名
    if (this.mainStore.currentUser && this.mainStore.currentUser.username) {
      this.picture.name = this.mainStore.currentUser.username
    }
  },
  beforeUnmount() {
    // 清除事件监听
    $off(this.$bus, 'restore-comment')
  },
  methods: {
    focusEditor() {
      const editor = this.$refs.commentEditor
      if (editor) {
        editor.focus()
      }
    },

    handleBotAvatarError() {
      this.botMentionAvatarBroken = true
    },

    handleEditorBeforeInput(event) {
      if (!event.inputType || !event.inputType.startsWith('insert')) {
        return
      }
      const selectedTextLength = this.getSelectedTextLength()
      if (this.commentContent.length - selectedTextLength >= 1000) {
        event.preventDefault()
      }
    },

    handleEditorInput() {
      this.syncCommentContentFromEditor()
      this.updateMentionPanel()
    },

    handleEditorKeydown(event) {
      if (this.showMentionPanel) {
        if (event.key === 'Enter' || event.key === 'Tab') {
          event.preventDefault()
          this.selectBotMention()
          return
        }
        if (event.key === 'Escape') {
          event.preventDefault()
          this.hideMentionPanel()
          return
        }
        if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
          event.preventDefault()
          return
        }
      }

      if ((event.key === 'Backspace' || event.key === 'Delete') && this.removeAdjacentMention(event.key)) {
        event.preventDefault()
        this.syncCommentContentFromEditor()
        this.hideMentionPanel()
        return
      }

      if (event.key === '@') {
        this.$nextTick(() => this.updateMentionPanel())
      }
    },

    handleEditorSelectionChange() {
      this.$nextTick(() => this.updateMentionPanel())
    },

    handleEditorBlur() {
      window.setTimeout(() => this.hideMentionPanel(), 120)
    },

    handleEditorPaste(event) {
      event.preventDefault()
      const text = (event.clipboardData || window.clipboardData)?.getData('text/plain') || ''
      const selectedTextLength = this.getSelectedTextLength()
      const remaining = 1000 - (this.commentContent.length - selectedTextLength)
      if (remaining <= 0) {
        return
      }
      this.insertPlainText(text.slice(0, remaining))
    },

    syncCommentContentFromEditor() {
      this.commentContent = this.serializeEditorContent()
      if (this.commentContent.trim() === '' && this.$refs.commentEditor?.textContent === '') {
        this.$refs.commentEditor.innerHTML = ''
      }
    },

    serializeEditorContent() {
      const editor = this.$refs.commentEditor
      if (!editor) {
        return ''
      }

      let result = ''
      const visit = (node) => {
        if (node.nodeType === Node.TEXT_NODE) {
          result += node.textContent.replace(/\u00a0/g, ' ')
          return
        }

        if (node.nodeType !== Node.ELEMENT_NODE) {
          return
        }

        const element = node
        if (element.classList.contains('mention-token')) {
          result += element.dataset.mention || element.textContent
          return
        }

        if (element.tagName === 'BR') {
          result += '\n'
          return
        }

        Array.from(element.childNodes).forEach(visit)
        if (['DIV', 'P'].includes(element.tagName) && !result.endsWith('\n')) {
          result += '\n'
        }
      }

      Array.from(editor.childNodes).forEach(visit)
      return result.replace(/\n{3,}/g, '\n\n')
    },

    updateMentionPanel() {
      if (!this.aiCommentMentionEnabled) {
        this.hideMentionPanel()
        return
      }

      const trigger = this.getMentionTrigger()
      if (!trigger) {
        this.hideMentionPanel()
        return
      }

      const query = trigger.query.toLowerCase()
      if (query && !this.botMentionName.toLowerCase().startsWith(query)) {
        this.hideMentionPanel()
        return
      }

      this.mentionTriggerRange = trigger
      this.showMentionPanel = true
    },

    getMentionTrigger() {
      const editor = this.$refs.commentEditor
      const selection = window.getSelection()
      if (!editor || !selection || selection.rangeCount === 0 || !selection.isCollapsed) {
        return null
      }

      const range = selection.getRangeAt(0)
      if (!editor.contains(range.startContainer) || range.startContainer.nodeType !== Node.TEXT_NODE) {
        return null
      }

      const text = range.startContainer.textContent.slice(0, range.startOffset)
      const match = text.match(/(^|\s)@([^\s@]*)$/)
      if (!match) {
        return null
      }

      const query = match[2] || ''
      return {
        node: range.startContainer,
        start: range.startOffset - query.length - 1,
        end: range.startOffset,
        query,
      }
    },

    selectBotMention() {
      if (!this.aiCommentMentionEnabled) {
        this.hideMentionPanel()
        return
      }

      const trigger = this.mentionTriggerRange || this.getMentionTrigger()
      if (!trigger || !trigger.node || !trigger.node.parentNode) {
        this.hideMentionPanel()
        return
      }

      const range = document.createRange()
      range.setStart(trigger.node, trigger.start)
      range.setEnd(trigger.node, trigger.end)
      range.deleteContents()

      const token = this.createMentionToken()
      const space = document.createTextNode(' ')
      const fragment = document.createDocumentFragment()
      fragment.appendChild(token)
      fragment.appendChild(space)
      range.insertNode(fragment)

      const selection = window.getSelection()
      const nextRange = document.createRange()
      nextRange.setStart(space, 1)
      nextRange.collapse(true)
      selection.removeAllRanges()
      selection.addRange(nextRange)

      this.syncCommentContentFromEditor()
      this.hideMentionPanel()
    },

    createMentionToken() {
      const token = document.createElement('span')
      token.className = 'mention-token'
      token.contentEditable = 'false'
      token.dataset.mention = `@${this.botMentionName}`
      token.appendChild(document.createTextNode('@'))

      const name = document.createElement('span')
      name.className = 'mention-token-name'
      name.textContent = this.botMentionName
      token.appendChild(name)
      return token
    },

    insertPlainText(text) {
      if (!text) {
        return
      }
      const editor = this.$refs.commentEditor
      if (!editor) {
        this.commentContent += text
        return
      }

      editor.focus()
      const selection = window.getSelection()
      let range = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null
      if (!range || !editor.contains(range.startContainer)) {
        range = document.createRange()
        range.selectNodeContents(editor)
        range.collapse(false)
      }

      range.deleteContents()
      const textNode = document.createTextNode(text)
      range.insertNode(textNode)
      range.setStart(textNode, text.length)
      range.collapse(true)
      selection.removeAllRanges()
      selection.addRange(range)

      this.syncCommentContentFromEditor()
      this.updateMentionPanel()
    },

    removeAdjacentMention(key) {
      const editor = this.$refs.commentEditor
      const selection = window.getSelection()
      if (!editor || !selection || selection.rangeCount === 0 || !selection.isCollapsed) {
        return false
      }

      const range = selection.getRangeAt(0)
      if (!editor.contains(range.startContainer)) {
        return false
      }

      return key === 'Backspace'
        ? this.removeMentionBeforeCaret(range)
        : this.removeMentionAfterCaret(range)
    },

    removeMentionBeforeCaret(range) {
      const node = range.startContainer
      const offset = range.startOffset

      if (node.nodeType === Node.TEXT_NODE) {
        if (offset === 1 && node.textContent === ' ' && this.isMentionToken(node.previousSibling)) {
          const parent = node.parentNode
          const token = node.previousSibling
          const caretIndex = Array.prototype.indexOf.call(parent.childNodes, token)
          token.remove()
          node.remove()
          this.placeCaretInNode(parent, caretIndex)
          return true
        }
        if (offset === 0 && this.isMentionToken(node.previousSibling)) {
          const parent = node.parentNode
          const token = node.previousSibling
          const caretIndex = Array.prototype.indexOf.call(parent.childNodes, token)
          token.remove()
          this.placeCaretInNode(parent, caretIndex)
          return true
        }
        return false
      }

      const previous = node.childNodes[offset - 1]
      if (this.isMentionToken(previous)) {
        previous.remove()
        this.placeCaretInNode(node, offset - 1)
        return true
      }
      if (previous?.nodeType === Node.TEXT_NODE && previous.textContent === ' ' && this.isMentionToken(previous.previousSibling)) {
        const token = previous.previousSibling
        const caretIndex = Array.prototype.indexOf.call(node.childNodes, token)
        previous.previousSibling.remove()
        previous.remove()
        this.placeCaretInNode(node, caretIndex)
        return true
      }
      return false
    },

    removeMentionAfterCaret(range) {
      const node = range.startContainer
      const offset = range.startOffset

      if (node.nodeType === Node.TEXT_NODE) {
        if (offset === node.textContent.length && this.isMentionToken(node.nextSibling)) {
          node.nextSibling.remove()
          return true
        }
        return false
      }

      const next = node.childNodes[offset]
      if (this.isMentionToken(next)) {
        const following = next.nextSibling
        next.remove()
        if (following?.nodeType === Node.TEXT_NODE && following.textContent === ' ') {
          following.remove()
        }
        this.placeCaretInNode(node, offset)
        return true
      }
      return false
    },

    isMentionToken(node) {
      return node?.nodeType === Node.ELEMENT_NODE && node.classList.contains('mention-token')
    },

    placeCaretAtEndOfEditor() {
      const editor = this.$refs.commentEditor
      if (!editor) {
        return
      }
      const range = document.createRange()
      range.selectNodeContents(editor)
      range.collapse(false)
      const selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
    },

    placeCaretInNode(node, offset) {
      const range = document.createRange()
      range.setStart(node, Math.max(0, Math.min(offset, node.childNodes.length)))
      range.collapse(true)
      const selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
    },

    getSelectedTextLength() {
      const selection = window.getSelection()
      return selection ? selection.toString().length : 0
    },

    hideMentionPanel() {
      this.showMentionPanel = false
      this.mentionTriggerRange = null
    },

    setEditorContentFromText(content) {
      const editor = this.$refs.commentEditor
      if (!editor) {
        this.commentContent = content || ''
        return
      }
      editor.innerHTML = ''
      const text = content || ''
      const mention = `@${this.botMentionName}`
      const lines = text.split('\n')

      lines.forEach((line, lineIndex) => {
        let start = 0
        let index = line.indexOf(mention)
        while (index !== -1) {
          if (index > start) {
            editor.appendChild(document.createTextNode(line.slice(start, index)))
          }
          editor.appendChild(this.createMentionToken())
          start = index + mention.length
          index = line.indexOf(mention, start)
        }
        if (start < line.length) {
          editor.appendChild(document.createTextNode(line.slice(start)))
        }
        if (lineIndex < lines.length - 1) {
          editor.appendChild(document.createElement('br'))
        }
      })

      this.commentContent = text
    },

    openPicture() {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message({
          message: '请先登录！',
          type: 'error',
        })
        return
      }

      this.showPicture = true
    },

    addPicture(res) {
      this.picture.url = res
      this.savePicture()
    },
    savePicture() {
      // 确保有用户名，如果没有则使用当前用户名
      const username =
        this.picture.name ||
        (this.mainStore.currentUser && this.mainStore.currentUser.username) ||
        '匿名'
      let img = '[' + username + ',' + this.picture.url + ']'
      this.insertPlainText(img)
      this.picture.url = ''
      this.showPicture = false
    },
    addEmoji(key) {
      this.insertPlainText(key)
    },
    showGraffiti() {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message({
          message: '请先登录！',
          type: 'error',
        })
        return
      }

      this.commentContent = ''
      this.setEditorContentFromText('')
      $emit(this, 'showGraffiti')
    },
    submitComment() {
      this.syncCommentContentFromEditor()
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        // 保存评论内容和当前页面URL到localStorage
        const articleId = this.$route.params.id
        const tempComment = {
          content: this.commentContent.trim(),
          timestamp: Date.now(),
          articleUrl: window.location.href,
        }
        localStorage.setItem(
          `tempComment_${articleId}`,
          JSON.stringify(tempComment)
        )

        // 使用统一的登录跳转函数
        this.$common.redirectToLogin(
          this.$router,
          {
            extraQuery: { hasComment: 'true' },
            message: '请先登录！评论内容已保存，登录后将自动恢复',
          },
          this
        )
        return
      }

      if (this.commentContent.trim() === '') {
        this.$message({
          message: '你还没写呢~',
          type: 'warning',
        })
        return
      }
      $emit(this, 'submitComment', this.commentContent.trim())
      // 注意：不在这里清空评论内容，由父组件根据验证码流程决定何时清空
    },

    // 清空评论内容（由父组件调用）
    clearComment() {
      this.hideMentionPanel()
      this.setEditorContentFromText('')
    },

    // 恢复评论内容（验证码取消时调用）
    restoreComment(content) {
      if (content) {
        this.setEditorContentFromText(content)
      }
    },
  },
  emits: ['submitComment', 'showGraffiti'],
}
</script>

<style scoped>
.comment-input-wrapper {
  position: relative;
  width: 100%;
  margin-bottom: 10px;
}

.comment-editor {
  border: 1px solid var(--lightGray);
  width: 100%;
  font-size: 14px;
  padding: 15px;
  min-height: 180px;
  max-height: 360px;
  overflow-y: auto;
  outline: none;
  border-radius: 4px;
  background-color: var(--inputBackground);
  background-image: var(--commentURL);
  background-size: contain;
  background-repeat: no-repeat;
  background-position: 100%;
  color: var(--fontColor);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  caret-color: var(--themeBackground);
}
.comment-editor.is-empty::before {
  content: attr(data-placeholder);
  color: #9aa0a6;
  pointer-events: none;
}
.comment-editor :deep(.mention-token) {
  display: inline-flex;
  align-items: center;
  padding: 0;
  margin: 0;
  border-radius: 0;
  background: transparent;
  color: #409eff;
  font-weight: 600;
  white-space: nowrap;
  user-select: all;
}
.comment-editor :deep(.mention-token-name) {
  color: inherit;
  font-weight: inherit;
}
.mention-panel {
  position: absolute;
  z-index: 20;
  left: 0;
  right: 0;
  bottom: 100%;
  width: 100%;
  border-radius: 16px 16px 0 0;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(10px);
  transform-origin: center bottom;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--lightGray);
  border-bottom: none;
}
.mention-scroll-area {
  display: flex;
  flex-direction: row;
  align-items: center;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 14px 16px;
  gap: 16px;
  scrollbar-width: none;
}
.mention-scroll-area::-webkit-scrollbar {
  display: none;
}
.mention-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  width: 52px;
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.16s ease;
}
.mention-option:hover {
  transform: scale(1.05);
}
.mention-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 6px;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.04);
}
.mention-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.mention-name {
  width: 100%;
  color: #333;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mention-pop-enter-active,
.mention-pop-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}
.mention-pop-enter-from,
.mention-pop-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
.comment-editor:focus {
  border-color: var(--themeBackground);
}
body.dark-mode .comment-editor {
  border-color: var(--borderColor);
}
body.dark-mode .comment-editor:focus {
  border-color: var(--themeBackground);
}
body.dark-mode .mention-panel {
  background: rgba(32, 33, 36, 0.98);
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.42);
  border-color: rgba(255, 255, 255, 0.1);
}
body.dark-mode .mention-name {
  color: #e5eaf3;
}
.myEmoji {
  font-size: 18px;
  cursor: pointer;
  transition: transform 0.5s ease, color 0.5s ease;
  margin-right: 12px;
  will-change: transform;
  transform: translateZ(0);
}
.myEmoji:hover {
  transform: rotate(360deg);
  font-size: 22px;
}
.myPicture {
  font-size: 18px;
  cursor: pointer;
}
.emoji-active {
  color: var(--red);
}
</style>
