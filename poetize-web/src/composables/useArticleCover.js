/**
 * 文章封面兜底逻辑
 *
 * 优先使用 article.articleCover；加载失败或为空时回退到 randomCover 稳定 hash 兜底，
 * 若 randomCover 也为空则回退到 /assets/backgroundPicture.jpg。
 *
 * 用法：
 *   - `<script setup>` 组件：`const { getArticleCoverUrl, handleCoverError } = useArticleCover()`
 *   - Options API 组件：在 `setup()` 中调用并 return，模板与方法均可访问
 */
import { reactive } from 'vue'
import { useMainStore } from '@/stores/main'

export function useArticleCover() {
  const mainStore = useMainStore()
  // 记录原始封面加载失败的文章 id，触发回退到 randomCover 兜底
  const coverFailedMap = reactive({})

  function getArticleCoverUrl(article) {
    const rawCover = article && article.articleCover
      ? String(article.articleCover).trim()
      : ''
    const articleId = article ? article.id : undefined
    const hasFailed = articleId != null && !!coverFailedMap[articleId]
    if (rawCover && !hasFailed) {
      return rawCover
    }
    const covers = Array.isArray(mainStore.webInfo?.randomCover)
      ? mainStore.webInfo.randomCover.filter(item => item && String(item).trim())
      : []
    if (covers.length > 0) {
      const key = String(article?.id || article?.articleTitle || '')
      let hash = 0
      for (let i = 0; i < key.length; i++) {
        hash = ((hash << 5) - hash) + key.charCodeAt(i)
        hash |= 0
      }
      return covers[Math.abs(hash) % covers.length]
    }
    return '/assets/backgroundPicture.jpg'
  }

  // el-image 加载失败回调：标记该文章，触发 getArticleCoverUrl 重新计算走兜底
  function handleCoverError(article) {
    if (article && article.id != null && !coverFailedMap[article.id]) {
      coverFailedMap[article.id] = true
    }
  }

  return { getArticleCoverUrl, handleCoverError }
}
