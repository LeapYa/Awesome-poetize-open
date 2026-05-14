<script setup>
import { computed, nextTick, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import taskLists from 'markdown-it-task-lists'
import constant from '@/utils/constant'
import { useMainStore } from '@/stores/main'
import { useAIChatStore } from '@/stores/aiChat'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['rendered'])
const mainStore = useMainStore()
const aiChatStore = useAIChatStore()
const botMentionName = computed(() => {
  return aiChatStore.config?.chat_name || aiChatStore.config?.chatName || 'AI助手'
})

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: true,
}).use(taskLists, { enabled: false })

const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const hrefIndex = token.attrIndex('href')
  if (hrefIndex >= 0) {
    const href = token.attrs[hrefIndex][1]
    if (!isSafeUrl(href)) {
      token.attrs[hrefIndex][1] = '#'
    }
  }
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const defaultImage =
  md.renderer.rules.image ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const srcIndex = token.attrIndex('src')
  if (srcIndex >= 0 && !isSafeUrl(token.attrs[srcIndex][1])) {
    token.attrs[srcIndex][1] = ''
  }
  token.attrSet('loading', 'lazy')
  token.attrJoin('class', 'comment-markdown-image pictureReg')
  return defaultImage(tokens, idx, options, env, self)
}

const renderedContent = computed(() => renderCommentMarkdown(props.content))

watch(
  renderedContent,
  () => {
    nextTick(() => emit('rendered'))
  },
  { immediate: true }
)

function escapeRegExp(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function renderCommentMarkdown(content) {
  if (!content) {
    return ''
  }

  const assets = []
  let prepared = content.replace(/\[[^\[\]]+\]/g, (token) => {
    const html = legacyTokenToHtml(token)
    if (!html) {
      return token
    }
    const placeholder = `@@COMMENT_ASSET_${assets.length}@@`
    assets.push({ placeholder, html })
    return placeholder
  })

  // 解析并高亮 @ 机器人的文本
  if (botMentionName.value) {
    const mentionRegex = new RegExp(`(@${escapeRegExp(botMentionName.value)})(?!\\w)`, 'g')
    prepared = prepared.replace(mentionRegex, (match) => {
      const html = `<span class="mention-token">${escapeHtml(match)}</span>`
      const placeholder = `@@COMMENT_ASSET_${assets.length}@@`
      assets.push({ placeholder, html })
      return placeholder
    })
  }

  let html = md.render(prepared)
  assets.forEach((asset) => {
    html = html.split(asset.placeholder).join(asset.html)
  })
  return html
}

function legacyTokenToHtml(token) {
  const value = token.slice(1, -1)
  const emojiIndex = constant.emojiList.indexOf(value)
  if (emojiIndex > -1) {
    const prefix = mainStore.sysConfig?.webStaticResourcePrefix || ''
    const src = `${prefix}emoji/q${emojiIndex + 1}.gif`
    return `<img loading="lazy" class="comment-emoji" src="${escapeAttr(src)}" title="${escapeAttr(token)}" alt="${escapeAttr(token)}" />`
  }

  const commaIndex = value.indexOf(',')
  if (commaIndex > -1) {
    const name = value.slice(0, commaIndex).trim()
    const url = value.slice(commaIndex + 1).trim()
    if (!isSafeUrl(url)) {
      return escapeHtml(token)
    }
    return `<img loading="lazy" class="pictureReg comment-legacy-image" src="${escapeAttr(url)}" title="${escapeAttr(name)}" alt="${escapeAttr(name)}" />`
  }

  return ''
}

function isSafeUrl(url) {
  if (!url) {
    return false
  }
  const value = String(url).trim()
  return /^(https?:\/\/|\/(?!\/)|data:image\/(?:png|jpe?g|gif|webp);base64,|blob:)/i.test(value)
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/`/g, '&#96;')
}
</script>

<template>
  <div class="comment-markdown" v-html="renderedContent"></div>
</template>

<style scoped>
.comment-markdown {
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.comment-markdown :deep(p) {
  margin: 0 0 0.65em;
}

.comment-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.comment-markdown :deep(ul),
.comment-markdown :deep(ol) {
  margin: 0.35em 0 0.65em 1.25em;
  padding-left: 1em;
}

.comment-markdown :deep(blockquote) {
  margin: 0.5em 0;
  padding-left: 0.8em;
  border-left: 3px solid var(--themeBackground);
  color: var(--greyFont);
}

.comment-markdown :deep(code) {
  padding: 0.12em 0.35em;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.08);
  font-family: Consolas, Monaco, monospace;
  font-size: 0.92em;
}

.comment-markdown :deep(pre) {
  overflow-x: auto;
  padding: 0.75em;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.08);
}

.comment-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
}

.comment-markdown :deep(a) {
  color: var(--themeBackground);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.comment-markdown :deep(.mention-token) {
  color: #409eff;
  font-weight: 600;
  white-space: nowrap;
}

.comment-markdown :deep(.comment-emoji) {
  width: 32px;
  height: 32px;
  vertical-align: middle;
}

.comment-markdown :deep(.comment-legacy-image),
.comment-markdown :deep(.comment-markdown-image) {
  display: block;
  width: 100%;
  max-width: 250px;
  border-radius: 5px;
}
</style>
