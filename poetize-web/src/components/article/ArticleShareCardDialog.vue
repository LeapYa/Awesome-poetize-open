<template>
  <el-dialog
    title="卡片分享"
    :model-value="modelValue"
    width="500px"
    :append-to-body="true"
    class="share-card-dialog centered-dialog"
    center
    @update:model-value="handleVisibleChange"
  >
    <div class="share-card-container">
      <div class="share-card-preview" ref="shareCard" id="shareCard">
        <div class="card-avatar-container">
          <img :src="avatarUrl" alt="作者头像" class="card-avatar" />
        </div>

        <div class="card-date">
          {{ formatDate(article.createTime) }}
        </div>

        <div class="card-title">
          {{ articleTitle }}
        </div>

        <div class="card-cover">
          <img :src="getArticleCoverUrl(article)" alt="文章封面" @error="handleCoverError(article)" />
        </div>

        <div class="card-footer">
          <div class="card-author">
            {{ article.username }}
          </div>

          <hr class="card-divider" />

          <div class="card-bottom">
            <div class="card-brand">
              {{ webTitle }}
            </div>
            <div class="card-qrcode" ref="qrcode"></div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleVisibleChange(false)">取消</el-button>
        <el-button type="primary" @click="downloadShareCard()">
          下载卡片
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import {
  preloadHtml2Canvas,
  formatDate,
  generateQRCode,
  downloadShareCard,
  captureAndDownloadCard,
} from '@/utils/article-share-card'
import { useArticleCover } from '@/composables/useArticleCover'

export default {
  setup() {
    const { getArticleCoverUrl, handleCoverError } = useArticleCover()
    return { getArticleCoverUrl, handleCoverError }
  },
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
    article: {
      type: Object,
      default: () => ({}),
    },
    articleTitle: {
      type: String,
      default: '',
    },
    avatarUrl: {
      type: String,
      default: '',
    },
    webTitle: {
      type: String,
      default: '',
    },
  },
  emits: ['update:modelValue'],
  watch: {
    modelValue: {
      immediate: true,
      handler(visible) {
        if (!visible) return

        this.preloadHtml2Canvas()
        this.$nextTick(() => {
          setTimeout(() => {
            this.generateQRCode()
          }, 300)
        })
      },
    },
  },
  methods: {
    handleVisibleChange(visible) {
      this.$emit('update:modelValue', visible)
    },
    preloadHtml2Canvas,
    formatDate,
    generateQRCode,
    downloadShareCard,
    captureAndDownloadCard,
  },
}
</script>
