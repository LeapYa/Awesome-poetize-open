<template>
  <div class="article-page">
    <div v-if="!$common.isEmpty(article)">
      <ArticleHero
        :article="article"
        :article-title="articleTitle"
        :available-language-buttons="availableLanguageButtons"
        :current-lang="currentLang"
        :can-manage-progress="canManageArticleProgress"
        @switch-language="handleLanguageSwitch"
        @language-mousedown="handleMouseDown"
        @language-touchstart="handleTouchStart"
        @open-news="weiYanDialogVisible = true"
      />
      <!-- 文章 -->
      <div style="background: var(--background)">
        <div class="article-container my-animation-slide-bottom">
          <div
            v-if="!$common.isEmpty(article.videoUrl) && decryptedVideoUrl"
            style="margin-bottom: 20px"
          >
            <videoPlayer
              :url="{ src: decryptedVideoUrl }"
              :cover="''"
            >
            </videoPlayer>
          </div>

          <!-- 最新进展 -->
          <div v-if="!$common.isEmpty(treeHoleList)" class="process-wrap">
            <el-collapse accordion model-value="1">
              <el-collapse-item title="最新进展" name="1">
                <process
                  :treeHoleList="treeHoleList"
                  @deleteTreeHole="deleteTreeHole"
                ></process>
              </el-collapse-item>
            </el-collapse>

            <hr />
          </div>

          <!-- 加载骨架 -->
          <div v-if="isLoading" class="entry-content">
            <el-skeleton :rows="10" animated />
          </div>
          <!-- 正文显示 -->
          <div
            v-else
            :key="articleContentKey"
            v-html="articleContentHtml"
            class="entry-content"
            :lang="currentLang"
          ></div>

          <ArticlePaywall
            :article="article"
            :payment-loading="paymentLoading"
            :verifying-payment="verifyingPayment"
            @pay="handlePayment"
            @verify="verifyPayment"
          />

          <!-- 最后更新时间 -->
          <div class="article-update-time">
            <span>文章最后更新于 {{ article.updateTime }}</span>
          </div>
          <!-- 分类 -->
          <div class="article-sort">
            <span
              draggable="true"
              @dragstart="handleSortDragStart($event)"
              @click="
                $router.push(
                  '/sort/' + article.sortId + '?labelId=' + article.labelId
                )
              "
              >{{
                article.sort.sortName + ' · ' + article.label.labelName
              }}</span
            >
          </div>
          <!-- 作者信息 -->
          <blockquote>
            <div>作者：{{ article.username }}</div>
            <div>
              <span>版权&许可请详阅</span>
              <span
                style="color: #38f; cursor: pointer"
                @click="copyrightDialogVisible = true"
              >
                版权声明
              </span>
            </div>
          </blockquote>
          <!-- 订阅和分享按钮 -->
          <div class="myCenter" id="article-like">
            <div
              class="subscribe-button"
              :class="{ subscribed: subscribe }"
              @click="subscribeLabel()"
            >
              {{ subscribe ? '已订阅' : '订阅' }}
              <el-icon><el-icon-upload /></el-icon>
            </div>
            <div class="share-card-button" @click="shareCardDialogVisible = true">
              卡片分享
              <el-icon><el-icon-share /></el-icon>
            </div>
          </div>

          <!-- 评论 -->
          <div v-if="article.commentStatus === true && enableComment">
            <div ref="commentSentinel" style="height: 1px"></div>
            <comment
              v-if="shouldLoadComments"
              :type="'article'"
              :source="article.id"
              :userId="article.userId"
            ></comment>
          </div>
        </div>

        <teleport to="body">
          <div id="toc" class="toc"></div>
        </teleport>
      </div>

      <div style="background: var(--background)">
        <myFooter></myFooter>
      </div>
    </div>

    <ArticleCopyrightDialog
      v-if="copyrightDialogVisible"
      v-model="copyrightDialogVisible"
      :avatar-url="$common.getAvatarUrl(mainStore.webInfo.avatar)"
      :default-avatar="$getDefaultAvatar()"
      :web-info="mainStore.webInfo"
      :host="$constant.host"
    />

    <ArticleWeiYanDialog
      v-if="weiYanDialogVisible"
      v-model="weiYanDialogVisible"
      v-model:news-time="newsTime"
      @submit-comment="submitWeiYan"
    />

    <ArticlePasswordDialog
      v-if="showPasswordDialog"
      v-model="showPasswordDialog"
      v-model:password="password"
      :tips="tips"
      :before-color="$constant.before_color_2"
      :after-color="$constant.after_color_2"
      @submit="submitPassword"
    />

    <ArticleShareCardDialog
      v-if="shareCardDialogVisible"
      v-model="shareCardDialogVisible"
      :article="article"
      :article-title="articleTitle"
      :avatar-url="$common.getAvatarUrl(article.avatar)"
      :web-title="mainStore.webInfo.webTitle"
    />

    <!-- 图表和图片 右键菜单 -->
    <div
      v-show="graphicContextMenu.visible"
      class="mermaid-context-menu"
      :style="{
        left: graphicContextMenu.x + 'px',
        top: graphicContextMenu.y + 'px',
      }"
      @click.stop
    >
      <div class="menu-item" @click="copyGraphicImage">
        <el-icon><CopyDocument /></el-icon>
        <span>复制图片</span>
      </div>
      <div class="menu-item" @click="downloadGraphicImage">
        <el-icon><Picture /></el-icon>
        <span>下载图片</span>
      </div>
    </div>
  </div>
</template>

<script>
import { defineAsyncComponent, h } from 'vue'
import { $on, $off, $emit } from '../utils/gogocodeTransfer'
import {
  Upload as ElIconUpload,
  Share as ElIconShare,
  CopyDocument,
  Picture,
} from '@element-plus/icons-vue'
import { useMainStore } from '@/stores/main'

import MarkdownIt from 'markdown-it'
import markdownItMultimdTable from 'markdown-it-multimd-table'
import markdownItTaskLists from 'markdown-it-task-lists'
// KaTeX 改为按需动态加载，只有文章包含数学公式时才加载
import { hasMathFormula, loadMarkdownItKatex } from '@/utils/katexLoader'
import { transformAttachmentLinks } from '@/utils/attachmentCard'
import { getLanguageMapping } from '@/utils/languageUtils'
import {
  applyThemeFromArticle,
  resetTheme,
} from '@/composables/useArticleTheme'
import { decrypt } from '@/utils/crypto-utils'
import { syncTocPosition, getTocbot } from '@/utils/article-toc'
import {
  setDefaultMetaTags,
  updateMetaTags,
  fetchArticleMeta,
} from '@/utils/article-meta'
import {
  setupLanguageSwitchEventDelegation,
  handleMouseDown,
  handleTouchStart,
  handleLanguageSwitch,
  switchLanguage,
  fetchTranslation,
  updateUrlWithLanguage,
  initializeLanguageSettings,
  getDefaultTargetLanguage,
  getArticleAvailableLanguages,
  generateLanguageButtons,
} from '@/utils/article-language'
import {
  highlight,
  wrapTables,
  addLineNumbersWithCSS,
  addLoadingPlaceholders,
  detectAndLoadResources,
  renderMermaid,
  renderECharts,
  handleThemeChange,
  applyZoomButtonTheme,
  applyMermaidThemeStyles,
  toggleImageZoom,
  toggleMermaidZoom,
  processImages,
  handleGraphicContextMenu,
  closeGraphicContextMenu,
  copyGraphicImage,
  downloadGraphicImage,
  inlineSvgStyles,
  convertSvgToCanvas,
} from '@/utils/article-rendering'
import ArticleHero from './article/ArticleHero.vue'
import ArticlePaywall from './article/ArticlePaywall.vue'
import '@/assets/css/article-page.css'
import '@/assets/css/article-page-dialogs.css'

const CommentLoading = {
  name: 'CommentLoading',
  render() {
    return h(
      'div',
      {
        style: {
          padding: '24px 0',
          textAlign: 'center',
          color: 'var(--text-color-secondary, #999)',
        },
      },
      '评论加载中...'
    )
  },
}

const CommentError = {
  name: 'CommentError',
  render() {
    return h(
      'div',
      {
        style: {
          padding: '24px 0',
          textAlign: 'center',
          color: 'var(--el-color-danger, #f56c6c)',
        },
      },
      '评论加载失败'
    )
  },
}

const AsyncComment = defineAsyncComponent({
  loader: () => import('./comment/comment'),
  delay: 200,
  timeout: 30000,
  suspensible: false,
  onError: (error, retry, fail, attempts) => {
    if (attempts <= 2) {
      retry()
      return
    }
    fail()
  },
  loadingComponent: CommentLoading,
  errorComponent: CommentError,
})

export default {
  components: {
    ArticleHero,
    ArticlePaywall,
    ArticleCopyrightDialog: defineAsyncComponent(() =>
      import('./article/ArticleCopyrightDialog.vue')
    ),
    ArticleWeiYanDialog: defineAsyncComponent(() =>
      import('./article/ArticleWeiYanDialog.vue')
    ),
    ArticlePasswordDialog: defineAsyncComponent(() =>
      import('./article/ArticlePasswordDialog.vue')
    ),
    ArticleShareCardDialog: defineAsyncComponent(() =>
      import('./article/ArticleShareCardDialog.vue')
    ),
    myFooter: defineAsyncComponent(() => import('./common/myFooter')),
    comment: AsyncComment,
    process: defineAsyncComponent(() => import('./common/process')),
    videoPlayer: defineAsyncComponent(() => import('./common/videoPlayer')),
    ElIconUpload,
    ElIconShare,
    CopyDocument,
    Picture,
  },
  data() {
    return {
      id: this.$route.params.id,
      articlePathToken: this.$route.params.id,
      lang: this.$route.params.lang,
      subscribe: false,
      article: {},
      decryptedVideoUrl: '',
      articleContentHtml: '',
      articleContentKey: Date.now(), // 强制重新渲染的key
      treeHoleList: [],
      weiYanDialogVisible: false,
      copyrightDialogVisible: false,
      newsTime: '',
      showPasswordDialog: false,
      password: '',
      tips: '',
      scrollTop: 0,
      hasInitTocbot: false,
      metaTags: null,
      metaTagRetryCount: 0,
      isLoadingMeta: false,
      currentLang: 'zh', // 默认中文
      isLoading: false,
      translatedTitle: '',
      translatedContent: '',
      tempComment: null, // 存储临时评论内容
      targetLanguage: 'en', // 目标语言
      targetLanguageName: 'English', // 目标语言名称
      sourceLanguage: 'zh', // 源语言
      sourceLanguageName: '中文', // 源语言名称
      languageMap: {}, // 语言映射
      availableLanguages: [], // 文章实际可用的翻译语言
      availableLanguageButtons: [], // 动态生成的语言按钮列表
      shareCardDialogVisible: false, // 卡片分享弹窗显示状态
      tocbotRefreshed: false, // 标记tocbot是否已在首次滚动时刷新
      tocbotRefreshTimer: null, // tocbot刷新定时器
      loadingArticleId: null, // 正在加载的文章ID（用于防止异步回调干扰）
      shouldLoadComments: false,
      commentObserver: null,
      graphicContextMenu: {
        visible: false,
        x: 0,
        y: 0,
        currentTarget: null,
        currentType: null,
      },
      articleThemeConfig: null, // 文章主题配置（缓存，供 TOC 使用）
      paymentLoading: false, // 付费按钮加载状态
      verifyingPayment: false, // 验证支付状态
    }
  },
  head() {
    if (!this.metaTags) {
      return {
        title: '',
        meta: [],
      }
    }

    return {
      title: this.metaTags.title,
      meta: [
        { name: 'description', content: this.metaTags.description },
        { name: 'keywords', content: this.metaTags.keywords },
        { name: 'author', content: this.metaTags.author },
        { property: 'og:title', content: this.metaTags.title },
        { property: 'og:description', content: this.metaTags.description },
        { property: 'og:type', content: 'article' },
        { property: 'og:url', content: this.metaTags['og:url'] },
        { property: 'og:image', content: this.metaTags['og:image'] },
        { name: 'twitter:card', content: this.metaTags['twitter:card'] },
        { name: 'twitter:title', content: this.metaTags.title },
        { name: 'twitter:description', content: this.metaTags.description },
        { name: 'twitter:image', content: this.metaTags['twitter:image'] },
        {
          property: 'article:published_time',
          content: this.metaTags['article:published_time'],
        },
        {
          property: 'article:modified_time',
          content: this.metaTags['article:modified_time'],
        },
      ],
    }
  },
  async created() {
    // 重置组件状态，防止缓存问题
    this.resetComponentState()

    // 先初始化语言映射（从数据库统一配置读取）
    this.languageMap = await getLanguageMapping()

    // 然后初始化语言设置，确保语言状态正确
    await this.initializeLanguageSettings()

    if (!this.$common.isEmpty(this.id)) {
      // 首次加载时强制清空预渲染内容，确保Vue重新渲染
      this.articleContentHtml = ''
      this.articleContentKey = Date.now()

      this.getArticle(localStorage.getItem('article_password_' + this.articlePathToken))

      if ('0' !== localStorage.getItem('showSubscribe')) {
        this.$notify.success('文章订阅', '点击文章下方订阅/取消订阅专栏', 15000)
        // 设置延时关闭提示
        setTimeout(() => {
          localStorage.setItem('showSubscribe', '0')
        }, 3000)
      }
    }

    // 检查是否有待执行的订阅操作
    this.checkPendingSubscribe()

    // 文章页面加载时触发看板娘检查
    this.$nextTick(() => {
      // 延迟触发事件，确保页面元素已加载
      setTimeout(() => {
        if (document && document.dispatchEvent) {
          document.dispatchEvent(new Event('checkWaifu'))
        }
      }, 1000)
    })
  },
  mounted() {
    window.addEventListener('scroll', this.onScrollPage)
    window.addEventListener('load', this.syncTocPosition)
    window.addEventListener('pageshow', this.syncTocPosition)
    window.addEventListener('resize', this.syncTocPosition)
    // 注意：不在这里调用getTocbot()，因为文章内容还没加载
    // getTocbot()会在getArticle()完成后的$nextTick中调用
    this.syncTocPosition()

    // 监听主题切换事件
    $on(this.$root, 'themeChanged', this.handleThemeChange)

    // 添加全局事件委托处理语言切换按钮点击
    this.setupLanguageSwitchEventDelegation()

    // 添加全局点击事件，关闭右键菜单
    document.addEventListener('click', this.closeGraphicContextMenu)

    // 注意：不需要实现JavaScript动态检测遮挡的响应式逻辑
    // 原因：通过CSS层叠上下文（.article-head z-index: 10 和 .article-container z-index: 1）
    // 已经彻底解决了语言切换按钮被遮挡的问题，无需动态调整按钮位置
    // 同时已注释掉 @media (max-width: 1050px) 中隐藏按钮的CSS规则

    // 添加看板娘初始化检查
    this.$nextTick(() => {
      // 检查当前配置是否启用看板娘
      const checkWaifuEnabled = () => {
        try {
          // 从本地存储获取配置
          const webInfoStr = localStorage.getItem('webInfo')
          if (webInfoStr) {
            const webInfoData = JSON.parse(webInfoStr)
            // 检查
            if (webInfoData.data) {
              return webInfoData.data.enableWaifu === true
            }
          }
          return this.mainStore.webInfo.enableWaifu === true
        } catch (e) {
          return false
        }
      }

      // 检查是否已加载Live2D
      const checkLive2DLoaded = () => {
        return (
          typeof window.loadlive2d === 'function' &&
          document.getElementById('waifu') &&
          document.getElementById('live2d')
        )
      }

      // 检查看板娘是否显示
      const checkWaifuVisible = () => {
        const waifu = document.getElementById('waifu')
        return (
          waifu &&
          waifu.style.display !== 'none' &&
          waifu.style.bottom !== '-500px'
        )
      }

      // 检查并在需要时通过事件触发看板娘检查
      setTimeout(() => {
        if (checkWaifuEnabled()) {
          if (!checkLive2DLoaded() || !checkWaifuVisible()) {
            // 使用事件驱动方式加载看板娘，避免直接操作DOM
            if (!localStorage.getItem('waifu-display')) {
              // 触发检查事件，让live2d.js完成初始化
              document.dispatchEvent(new Event('checkWaifu'))
            }
          }
        }
      }, 2000) // 延迟2秒检查，确保页面完全加载
    })

    // 检查是否有临时保存的评论
    this.checkTempComment()

    // 检查是否有保存的页面状态
    this.checkPageState()

    // 监听路由变化，检查是否从登录页面返回
    this.$watch(
      () => this.$route.query,
      (newQuery) => {
        if (newQuery.hasComment === 'true') {
          // 从登录页面返回且带有评论标记
          this.$nextTick(() => {
            this.checkTempComment()
          })
        }

        // 检查回复操作恢复标记
        if (newQuery.hasReplyAction === 'true') {
          // 从登录页面返回且带有回复操作标记
          this.$nextTick(() => {
            this.checkPageState()
          })
        }
      }
    )

    this.$nextTick(() => {
      this.setupCommentIntersectionObserver()
    })
  },
  unmounted() {
    window.removeEventListener('scroll', this.onScrollPage)
    window.removeEventListener('load', this.syncTocPosition)
    window.removeEventListener('pageshow', this.syncTocPosition)
    window.removeEventListener('resize', this.syncTocPosition)

    // 移除文章主题自定义 CSS 变量，恢复默认值
    resetTheme()

    // 移除主题切换事件监听
    $off(this.$root, 'themeChanged', this.handleThemeChange)

    // 清理语言切换事件监听器
    if (this.languageSwitchHandler) {
      document.removeEventListener('click', this.languageSwitchHandler, true)
      document.removeEventListener('touchend', this.languageSwitchHandler, true)
      document.removeEventListener(
        'mousedown',
        this.languageSwitchHandler,
        true
      )
      document.removeEventListener(
        'touchstart',
        this.languageSwitchHandler,
        true
      )
      this.languageSwitchHandler = null
    }

    // 清理FAB点击外部区域事件监听器
    if (this.fabClickOutsideHandler) {
      document.removeEventListener('click', this.fabClickOutsideHandler, true)
      this.fabClickOutsideHandler = null
    }

    document.removeEventListener('click', this.closeGraphicContextMenu)
    this.teardownCommentIntersectionObserver()
  },
  watch: {
    scrollTop(scrollTop, oldScrollTop) {
      // 滚动监听逻辑已移至home.vue的toolButton控制
    },
    '$route.params': function (newParams, oldParams) {
      // 检查文章ID或语言参数是否变化
      const newId = newParams.id
      const oldId = oldParams.id
      const newLang = newParams.lang
      const oldLang = oldParams.lang

      if (newId && newId !== this.articlePathToken) {
        // 重置组件状态，防止显示旧数据
        this.resetComponentState()

        // 更新组件的id和lang数据
        this.id = newId
        this.articlePathToken = newId
        this.lang = newLang

        // 重新初始化语言设置 - 关键修复：确保每次切换文章都重新初始化语言
        this.initializeLanguageSettings()
          .then(() => {
            // 语言初始化完成后再获取文章
            const password = localStorage.getItem('article_password_' + this.articlePathToken)
            this.getArticle(password)
          })
          .catch((error) => {
            // 即使语言初始化失败，也要获取文章
            const password = localStorage.getItem('article_password_' + this.articlePathToken)
            this.getArticle(password)
          })

        // 检查是否有待执行的订阅操作
        this.$nextTick(() => {
          this.checkPendingSubscribe()
        })
      } else if (newId === this.articlePathToken && newLang !== oldLang) {
        // 同一文章，仅语言参数变化
        this.lang = newLang

        if (newLang && this.languageMap[newLang]) {
          if (this.currentLang !== newLang) {
            this.switchLanguage(newLang)
          }
        } else {
          // 如果语言参数无效，切换到默认源语言
          this.switchLanguage(this.sourceLanguage)
        }
      }
    },
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    articleTitle() {
      // 如果当前语言不是源语言且已有翻译标题，则显示翻译标题，否则显示原始标题
      return this.currentLang !== this.sourceLanguage && this.translatedTitle
        ? this.translatedTitle
        : this.article.articleTitle
    },

    // 全局评论开关 - 从系统配置中读取
    enableComment() {
      const sysConfig = this.mainStore.sysConfig
      // 默认为 true，如果配置不存在或配置值为 'true' 则显示评论
      if (!sysConfig || !sysConfig.enableComment) {
        return true
      }
      return (
        sysConfig.enableComment === 'true' || sysConfig.enableComment === true
      )
    },

    canManageArticleProgress() {
      return (
        !this.$common.isEmpty(this.mainStore.currentUser) &&
        this.mainStore.currentUser.id === this.article.userId
      )
    },
  },
  beforeUnmount() {
    // 组件销毁时清理状态，防止影响下一个文章组件
    this.clearComponentState()
    this.teardownCommentIntersectionObserver()

    // 销毁tocbot实例
    if (window.tocbot) {
      try {
        window.tocbot.destroy()
      } catch (e) {
        // 忽略销毁失败
      }
    }

    // 清理定时器
    if (this.tocbotRefreshTimer) {
      clearTimeout(this.tocbotRefreshTimer)
      this.tocbotRefreshTimer = null
    }
  },
  methods: {
    /**
     * 创建 Markdown 渲染器，按需加载 KaTeX
     * @param {string} content - 要渲染的内容
     * @returns {Promise<Object>} MarkdownIt 实例
     */
    async createMarkdownRenderer(content) {
      const md = new MarkdownIt({ breaks: true, html: true })
        .use(markdownItMultimdTable)
        .use(markdownItTaskLists, {
          enabled: true,
          label: true,
          labelAfter: true
        })

      const defaultImageRenderer = md.renderer.rules.image
      md.renderer.rules.image = (tokens, idx, options, env, self) => {
        const token = tokens[idx]

        token.attrSet('loading', 'lazy')
        token.attrSet('decoding', 'async')

        if (defaultImageRenderer) {
          return defaultImageRenderer(tokens, idx, options, env, self)
        }

        return self.renderToken(tokens, idx, options)
      }
      
      // 只有检测到数学公式时才加载 katex
      if (hasMathFormula(content)) {
        const katexPlugin = await loadMarkdownItKatex()
        if (katexPlugin) {
          md.use(katexPlugin)
        }
      }
      
      return md
    },

    getDisplayedMarkdownContent() {
      if (
        this.currentLang &&
        this.currentLang !== this.sourceLanguage &&
        this.translatedContent
      ) {
        return this.translatedContent
      }

      return this.article?.articleContent || ''
    },

    async renderArticleBody(content, { setupCommentObserver = false } = {}) {
      const safeContent = content || ''
      const md = await this.createMarkdownRenderer(safeContent)
      // 使用 markdown-it 渲染标准 markdown
      let renderedHtml = transformAttachmentLinks(md.render(safeContent))
      
      // 额外处理：很多时候文章里会有直接手写的 HTML <img /> 标签，或者 Vditor 插入的 HTML 图片
      // 这里通过正则确保所有图片都被强行加上 loading="lazy"
      renderedHtml = renderedHtml.replace(
        /<img(?![^>]*\bloading=['"]lazy['"])[^>]*>/gi,
        (match) => {
          return match.replace('<img', '<img loading="lazy" decoding="async"')
        }
      )
      
      this.articleContentHtml = renderedHtml
      this.articleContentKey = Date.now()

      // 关键：必须在 $nextTick 之前将 isLoading 设为 false
      // 否则模板中 v-if="isLoading" 显示骨架屏，v-else 的 .entry-content 不在 DOM 中
      // 后续 processImages 等方法依赖 querySelector('.entry-content img') 会找不到元素
      this.isLoading = false

      await this.$nextTick()

      this.processImages()
      this.initializeVideoCards()
      this.bindAttachmentCardActions()
      this.normalizeTaskListCheckboxes()
      this.wrapTables()
      this.highlight()
      this.renderMermaid()
      this.renderECharts()
      this.addId()
      this.detectAndLoadResources()
      // 异步注入图片宽高，让浏览器提前预留占位，避免目录跳转偏移
      this.injectImageDimensions()

      if (setupCommentObserver) {
        this.setupCommentIntersectionObserver()
      }
    },

    initializeVideoCards(container) {
      const root =
        container ||
        (this.$el ? this.$el.querySelector('.entry-content') : null) ||
        document.querySelector('.entry-content')
      if (!root) return

      const videos = root.querySelectorAll('.poetize-video-card video')
      videos.forEach((video) => {
        const card = video.closest('.poetize-video-card')
        if (!card || video.dataset.poetizeVideoReady === 'true') return
        video.dataset.poetizeVideoReady = 'true'
        const syncPlayingState = () => {
          card.classList.toggle('is-playing', !video.paused && !video.ended)
        }
        video.addEventListener('play', syncPlayingState)
        video.addEventListener('pause', syncPlayingState)
        video.addEventListener('ended', syncPlayingState)
        syncPlayingState()
      })
    },

    bindAttachmentCardActions(container) {
      const root =
        container ||
        (this.$el ? this.$el.querySelector('.entry-content') : null) ||
        document.querySelector('.entry-content')
      if (!root) return

      const privateCards = root.querySelectorAll(
        '[data-poetize-private-attachment="true"]'
      )
      privateCards.forEach((card) => {
        if (card.dataset.poetizeAttachmentBound === 'true') return
        card.dataset.poetizeAttachmentBound = 'true'

        card.addEventListener(
          'click',
          (event) => {
            const hasUser =
              !this.$common.isEmpty(this.mainStore.currentUser) ||
              !this.$common.isEmpty(this.mainStore.currentAdmin)
            if (hasUser) return

            event.preventDefault()
            event.stopPropagation()
            this.$message.warning('请先登录后查看附件')
          },
          true
        )
      })
    },

    normalizeTaskListCheckboxes(container) {
      const root =
        container ||
        (this.$el ? this.$el.querySelector('.entry-content') : null) ||
        document.querySelector('.entry-content')
      if (!root) return

      const checkboxes = root.querySelectorAll(
        'li.task-list-item input[type="checkbox"], input.task-list-item-checkbox[type="checkbox"]'
      )

      checkboxes.forEach((checkbox) => {
        // 移除 disabled 属性以保证样式正常（非灰色）
        checkbox.removeAttribute('disabled')
        
        // 如果已经绑定过锁定逻辑，跳过
        if (checkbox.dataset.todoReadonlyBound === 'true') return

        const lockedChecked = checkbox.checked
        checkbox.dataset.todoReadonlyBound = 'true'
        
        // 核心锁定逻辑：阻止点击和状态改变
        const lock = (e) => {
          e.preventDefault()
          e.stopPropagation()
          checkbox.checked = lockedChecked
          return false
        }

        checkbox.addEventListener('click', lock, true)
        checkbox.addEventListener('change', lock, true)
        // 阻止键盘操作
        checkbox.addEventListener('keydown', (e) => {
           if (e.code === 'Space' || e.key === ' ') {
             lock(e)
           }
        }, true)
      })
    },

    // 处理分类标签拖拽开始事件
    handleSortDragStart(event) {
      // 构建分类页面的完整URL
      const baseUrl = window.location.origin
      const sortPath = `/sort/${this.article.sortId}?labelId=${this.article.labelId}`
      const sortUrl = `${baseUrl}${sortPath}`

      // 设置拖拽数据
      event.dataTransfer.effectAllowed = 'link'
      event.dataTransfer.setData('text/uri-list', sortUrl)
      event.dataTransfer.setData('text/plain', sortUrl)

      // 设置拖拽时显示的文本
      const title = `${this.article.sort.sortName} · ${this.article.label.labelName}`
      event.dataTransfer.setData(
        'text/html',
        `<a href="${sortUrl}">${title}</a>`
      )
    },

    // 重置组件状态，防止缓存问题
    resetComponentState() {
      this.article = {}
      this.decryptedVideoUrl = ''
      this.translatedTitle = ''
      this.translatedContent = ''
      this.articleContentHtml = ''
      this.articleContentKey = Date.now()
      this.isLoading = false
      this.shouldLoadComments = false
      this.teardownCommentIntersectionObserver()

      // 重置语言相关状态 - 这是关键修复
      this.currentLang = this.sourceLanguage || 'zh'
      this.availableLanguages = []
      this.availableLanguageButtons = []

      // 重置密码相关状态
      this.showPasswordDialog = false
      this.password = ''
      this.tips = ''

      // 重置订阅状态
      this.subscribe = false

      // 重置元标签相关状态
      this.metaTags = null
      this.metaTagRetryCount = 0
      this.isLoadingMeta = false

      // 重置目录相关状态
      this.tocbotRefreshed = false
      if (this.tocbotRefreshTimer) {
        clearTimeout(this.tocbotRefreshTimer)
        this.tocbotRefreshTimer = null
      }

      // 重置正在加载的文章ID（防止旧文章的异步回调影响新文章）
      this.loadingArticleId = null

      // 销毁旧的tocbot实例（路由切换时）
      if (window.tocbot) {
        try {
          window.tocbot.destroy()
        } catch (e) {
          // 忽略销毁失败的错误
        }
      }
    },

    // 清理组件状态
    clearComponentState() {
      // 清理其他可能的异步操作
      this.loading = false

      // 清理翻译内容
      this.translatedTitle = ''
      this.translatedContent = ''
    },

    async verifyPayment() {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message.warning('请先登录')
        return
      }
      this.verifyingPayment = true
      try {
        const res = await this.$http.get(
          this.$constant.baseURL + '/payment/checkPayment',
          { articleId: this.article.id }
        )
        if (res.code === 200 && res.data === true) {
          this.$message.success('验证成功！正在刷新文章...')
          this.getArticle()
        } else {
          this.$message.info('暂未查到支付记录，如已支付请稍后再试')
        }
      } catch (e) {
        this.$message.error('验证失败，请稍后重试')
      } finally {
        this.verifyingPayment = false
      }
    },

    async handlePayment() {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message.warning('请先登录后再进行付费操作')
        return
      }
      this.paymentLoading = true
      try {
        // 会员专属文章传 articleId=0，其他传实际文章ID
        const payArticleId = this.article.payType === 2 ? 0 : this.article.id
        const res = await this.$http.get(
          this.$constant.baseURL + '/payment/getPaymentUrl',
          { articleId: payArticleId }
        )
        if (res.code === 200 && res.data) {
          window.open(res.data, '_blank')
          // 轮询检查支付状态
          this.$message({
            message: '请在新窗口完成支付，支付后将自动刷新',
            type: 'info',
            duration: 10000
          })
          let checkCount = 0
          const checkInterval = setInterval(async () => {
            checkCount++
            if (checkCount > 60) { // 5分钟超时
              clearInterval(checkInterval)
              return
            }
            try {
              const checkRes = await this.$http.get(
                this.$constant.baseURL + '/payment/checkPayment',
                { articleId: this.article.id }
              )
              if (checkRes.code === 200 && checkRes.data === true) {
                clearInterval(checkInterval)
                this.$message.success('支付成功！正在刷新文章...')
                this.getArticle()
              }
            } catch (e) {
              // 静默忽略检查错误
            }
          }, 5000)
        } else {
          this.$message.error(res.message || '获取支付链接失败，请稍后重试')
        }
      } catch (error) {
        this.$message.error('网络错误，请稍后重试')
      } finally {
        this.paymentLoading = false
      }
    },

    subscribeLabel() {
      // 首先显示确认订阅对话框
      const confirmMessage = this.subscribe
        ? '确认取消订阅专栏【' + this.article.label.labelName + '】？'
        : '确认订阅专栏【' +
          this.article.label.labelName +
          '】？订阅专栏后，该专栏发布新文章将通过邮件通知订阅用户。'

      const confirmTitle = this.subscribe ? '取消订阅' : '文章订阅'

      this.$confirm(confirmMessage, confirmTitle, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        center: true,
      })
        .then(() => {
          // 用户确认订阅意图后，检查登录状态
          if (this.$common.isEmpty(this.mainStore.currentUser)) {

            // 立即保存订阅意图并跳转到登录页面
            this.saveSubscribeIntentAndRedirectToLogin()
            return
          }

          // 已登录，直接执行订阅操作
          this.executeSubscribe()
        })
        .catch(() => {
          this.$message({
            type: 'success',
            message: '已取消!',
          })
        })
    },

    // 保存订阅意图并跳转到登录页面
    saveSubscribeIntentAndRedirectToLogin() {
      const subscribeIntent = {
        articleId: this.article.id || this.id,
        labelId: this.article.labelId,
        labelName: this.article.label.labelName,
        action: this.subscribe ? 'unsubscribe' : 'subscribe',
        timestamp: Date.now(),
      }

      // 保存订阅意图到localStorage
      localStorage.setItem('pendingSubscribe', JSON.stringify(subscribeIntent))

      // 使用统一的登录跳转函数
      this.$common.redirectToLogin(
        this.$router,
        {
          message: '请先登录！',
        },
        this
      )
    },

    // 执行订阅操作
    executeSubscribe() {
      this.$http
        .get(this.$constant.baseURL + '/user/subscribe', {
          labelId: this.article.labelId,
          flag: !this.subscribe,
        })
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.mainStore.loadCurrentUser(res.data)
          }
          this.subscribe = !this.subscribe

          // 显示成功消息
          const message = this.subscribe ? '订阅成功！' : '取消订阅成功！'
          this.$message({
            message: message,
            type: 'success',
          })
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },

    // 检查并处理待执行的订阅操作
    checkPendingSubscribe() {
      const pendingSubscribe = localStorage.getItem('pendingSubscribe')
      if (!pendingSubscribe) {
        return
      }

      try {
        const subscribeIntent = JSON.parse(pendingSubscribe)

        // 检查是否是当前文章的订阅意图
        if (String(subscribeIntent.articleId) === String(this.article.id || this.id)) {
          // 清除待执行的订阅意图
          localStorage.removeItem('pendingSubscribe')

          // 检查用户是否已登录
          if (!this.$common.isEmpty(this.mainStore.currentUser)) {
            // 延迟执行订阅操作，确保页面数据已加载完成
            this.$nextTick(() => {
              setTimeout(() => {
                this.executeSubscribe()
              }, 500)
            })
          }
        }
      } catch (error) {
        localStorage.removeItem('pendingSubscribe')
      }
    },
    submitPassword() {
      if (this.$common.isEmpty(this.password)) {
        this.$message({
          message: '请先输入密码！',
          type: 'error',
        })
        return
      }

      this.getArticle(this.password)
    },
    deleteTreeHole(id) {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        // 使用统一的登录跳转函数
        this.$common.redirectToLogin(
          this.$router,
          {
            message: '请先登录！',
          },
          this
        )
        return
      }

      this.$confirm('确认删除？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success',
        center: true,
      })
        .then(() => {
          this.$http
            .get(this.$constant.baseURL + '/weiYan/deleteWeiYan', { id: id })
            .then((res) => {
              this.$message({
                type: 'success',
                message: '删除成功!',
              })
              this.getNews()
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: 'error',
              })
            })
        })
        .catch(() => {
          this.$message({
            type: 'success',
            message: '已取消删除!',
          })
        })
    },
    submitWeiYan(content) {
      let weiYan = {
        content: content,
        createTime: this.newsTime,
        source: this.article.id,
      }

      this.$http
        .post(this.$constant.baseURL + '/weiYan/saveNews', weiYan)
        .then((res) => {
          this.weiYanDialogVisible = false
          this.newsTime = ''
          this.getNews()
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    getNews() {
      this.$http
        .post(this.$constant.baseURL + '/weiYan/listNews', {
          current: 1,
          size: 9999,
          source: this.article.id,
        })
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            res.data.records.forEach((c) => {
              c.content = c.content.replace(
                /\n{2,}/g,
                '<div style="height: 12px"></div>'
              )
              c.content = c.content.replace(/\n/g, '<br/>')
              c.content = this.$common.faceReg(c.content)
              c.content = this.$common.pictureReg(c.content)
            })
            this.treeHoleList = res.data.records
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    onScrollPage() {
      const scrollingElement =
        document.scrollingElement || document.documentElement || document.body
      this.scrollTop = scrollingElement ? scrollingElement.scrollTop : 0
      this.syncTocPosition()

      // 在用户首次滚动时刷新tocbot，确保位置计算准确
      if (!this.tocbotRefreshed && window.tocbot && window.tocbot.refresh) {
        if (this.tocbotRefreshTimer) {
          clearTimeout(this.tocbotRefreshTimer)
        }
        this.tocbotRefreshTimer = setTimeout(() => {
          if (window.tocbot && window.tocbot.refresh) {
            window.tocbot.refresh()
            this.tocbotRefreshed = true
          }
        }, 50)
      }
    },
    syncTocPosition,
    getTocbot,
    addId() {
      const entryContent = document.querySelector('.entry-content')
      if (entryContent) {
        const headings = entryContent.querySelectorAll('h1, h2, h3, h4, h5, h6')
        headings.forEach((heading, index) => {
          if (!heading.id) {
            heading.id = 'toc-' + index
          }
        })
      }
    },
    setDefaultMetaTags,
    updateMetaTags,
    getArticle(password) {
      this.isLoading = true
      const articlePathToken = this.articlePathToken || this.id

      // 设置正在加载的文章URL标识（数字ID或URL别名）
      this.loadingArticleId = articlePathToken

      // 重置状态，防止显示旧数据
      this.article = {}
      this.articleContentHtml = ''
      this.translatedTitle = ''
      this.translatedContent = ''
      this.tocbotRefreshed = false // 重置tocbot刷新标志
      this.shouldLoadComments = false
      this.teardownCommentIntersectionObserver()

      // 使用Promise.all并行处理所有请求
      // 如果当前语言不是源语言，在第一次请求时就带上语言参数
      const articleParams = { path: articlePathToken, password: password }
      if (this.currentLang && this.currentLang !== this.sourceLanguage) {
        articleParams.language = this.currentLang
      }

      Promise.all([
        this.$http.get(
          this.$constant.baseURL + '/article/getArticleByPath',
          articleParams
        ),
        this.fetchArticleMeta(),
      ])
        .then(async ([articleRes]) => {
          // 处理文章数据
          if (!this.$common.isEmpty(articleRes.data)) {
            this.article = articleRes.data
            this.id = this.article.id
            this.articlePathToken = this.article.articleSlug || String(this.article.id)

            // 解密视频URL
            if (this.article.videoUrl) {
              decrypt(this.article.videoUrl).then(url => {
                this.decryptedVideoUrl = url || ''
              })
            }

            // 在渲染内容之前，先同步应用文章主题（从接口合并返回，无额外请求）
            // 这样标题装饰在首次渲染时就是正确的，彻底避免闪烁
            if (this.article.articleThemeConfig) {
              this.articleThemeConfig = applyThemeFromArticle(this.article.articleThemeConfig)
            }

            // 检查当前语言状态，决定显示内容
            // 确定要渲染的内容
            const contentToRender =
              this.currentLang !== this.sourceLanguage &&
              this.article.translatedContent
                ? this.article.translatedContent
                : this.article.articleContent

            // 判断显示原文还是翻译
            if (
              this.currentLang !== this.sourceLanguage &&
              this.article.translatedContent
            ) {
              // 显示翻译内容（后端已一次性返回）
              this.translatedTitle = this.article.translatedTitle
              this.translatedContent = this.article.translatedContent
            } else {
              // 显示原文
              this.translatedTitle = ''
              this.translatedContent = ''
            }
            await this.renderArticleBody(contentToRender, {
              setupCommentObserver: true,
            })

            // 确保样式正确应用的保险措施
            setTimeout(() => {
              // 检查是否有代码块没有正确处理
              const entryContent = document.querySelector('.entry-content')
              if (entryContent) {
                const unprocessedBlocks = entryContent.querySelectorAll(
                  'pre:not(.highlight-wrap)'
                )
                if (unprocessedBlocks.length > 0) {
                  this.highlight()
                  this.renderMermaid()
                  this.renderECharts()
                }
              }
            }, 1000)

            if (!this.$common.isEmpty(password)) {
              localStorage.setItem('article_password_' + this.articlePathToken, password)
            }
            this.showPasswordDialog = false
            if (
              !this.$common.isEmpty(this.mainStore.currentUser) &&
              !this.$common.isEmpty(this.mainStore.currentUser.subscribe)
            ) {
              this.subscribe = JSON.parse(
                this.mainStore.currentUser.subscribe
              ).includes(this.article.labelId)
            }

            // 获取文章可用的翻译语言并生成动态按钮
            this.getArticleAvailableLanguages()
            this.checkPendingSubscribe()
          } else {
            // 文章数据为空，说明文章不存在，跳转到404页面
            this.$router.push('/404')
            return
          }

          // 处理"最新进展"数据
          const newsRes = await this.$http
            .post(this.$constant.baseURL + '/weiYan/listNews', {
              current: 1,
              size: 9999,
              source: this.article.id,
            })
            .catch(() => ({ data: null }))
          if (!this.$common.isEmpty(newsRes.data)) {
            newsRes.data.records.forEach((c) => {
              c.content = c.content.replace(
                /\n{2,}/g,
                '<div style="height: 12px"></div>'
              )
              c.content = c.content.replace(/\n/g, '<br/>')
              c.content = this.$common.faceReg(c.content)
              c.content = this.$common.pictureReg(c.content)
            })
            this.treeHoleList = newsRes.data.records
          }
        })
        .catch((error) => {
          console.error('获取文章失败:', error)

          // 统一错误处理
          if (
            error &&
            error.message &&
            '密码错误' === error.message.substr(0, 4)
          ) {
            // 密码错误，显示密码输入框
            if (!this.$common.isEmpty(password)) {
              localStorage.removeItem('article_password_' + this.articlePathToken)
              this.$message({
                message: '密码错误，请重新输入！',
                type: 'error',
                customClass: 'message-index',
              })
            }
            this.tips = error.message.substr(4)
            this.showPasswordDialog = true
          } else if (
            error &&
            error.message &&
            (error.message.includes('文章不存在') ||
              error.message.includes('文章未找到') ||
              error.message.includes('404') ||
              error.message.includes('Not Found'))
          ) {
            // 文章不存在，跳转到404页面
            this.$router.push('/404')
            return
          } else {
            // 其他错误（网络错误等），显示错误消息但不跳转
            this.$message({
              message: error ? error.message : '加载失败，请重试',
              type: 'error',
              customClass: 'message-index',
            })
          }
        })
        .finally(() => {
          this.isLoading = false
          this.$nextTick(() => {
            this.normalizeTaskListCheckboxes()
          })
        })
    },
    fetchArticleMeta,
    highlight,
    
    /**
     * 处理表格样式 - 将表格包装到 table-wrapper 中以应用样式
     * 此方法独立于代码高亮，确保无论是否有代码块，表格都能正确显示样式
     */
    wrapTables,

    /**
     * 使用CSS计数器添加行号
     */
    addLineNumbersWithCSS,

    // 给代码块添加 loading 占位符
    addLoadingPlaceholders,

    // 检测文章内容中需要加载的资源（异步并行，不阻塞渲染）
    // 注意：此方法应在 $nextTick 中调用，确保 DOM 已渲染
    detectAndLoadResources,

    /**
     * 异步查询文章图片宽高并注入 width/height 属性，使浏览器提前分配占位空间。
     * 这样在目录跳转时，图片还没加载也不会引起布局偏移（Layout Shift）。
     * 采用 best-effort 策略：失败不影响正常渲染。
     */
    async injectImageDimensions() {
      await this.$nextTick()
      const imgs = Array.from(document.querySelectorAll('.entry-content img'))
      if (!imgs.length) return

      // 只处理尚未有明确尺寸属性的图片
      const targets = imgs.filter(
        (img) => !img.getAttribute('width') && !img.getAttribute('height')
      )
      if (!targets.length) return

      // 收集所有需要查询的路径（绝对 URL 和 attribute 原始值都发过去）
      const pathSet = new Set()
      targets.forEach((img) => {
        const absSrc = img.src
        const attrSrc = img.getAttribute('src')
        if (absSrc) pathSet.add(absSrc)
        if (attrSrc && attrSrc !== absSrc) pathSet.add(attrSrc)
      })

      if (!pathSet.size) return

      try {
        const res = await this.$http.post(
          this.$constant.baseURL + '/resource/imageDimensions',
          { paths: Array.from(pathSet) }
        )
        const dimMap = res?.data
        if (!dimMap) return

        targets.forEach((img) => {
          const absSrc = img.src
          const attrSrc = img.getAttribute('src')
          const dims = dimMap[absSrc] || dimMap[attrSrc]
          if (dims?.width && dims?.height) {
            img.setAttribute('width', dims.width)
            img.setAttribute('height', dims.height)
          }
        })
      } catch (e) {
        // best-effort，静默忽略错误
      }
    },

    renderMermaid,

    // 渲染 ECharts 图表
    renderECharts,

    // 处理主题切换事件
    handleThemeChange,

    // 应用放大按钮主题样式
    applyZoomButtonTheme,

    // 应用 Mermaid 主题样式（容器背景 + 线条/箭头颜色）
    // 注意：使用 default 主题时，节点颜色和文字颜色保持不变，只需调整线条颜色
    applyMermaidThemeStyles,

    // 切换Mermaid图表的放大/缩小状态
    toggleMermaidZoom,

    // 处理 Mermaid 右键菜单
    processImages,
    toggleImageZoom,
    handleGraphicContextMenu,

    // 关闭右键菜单
    closeGraphicContextMenu,

    // 复制图片
    copyGraphicImage,

    // 下载图片
    downloadGraphicImage,

    // 辅助方法：内联 SVG 样式
    inlineSvgStyles,

    // 辅助方法：SVG 转 Canvas
    convertSvgToCanvas,

    setupLanguageSwitchEventDelegation,
    handleMouseDown,
    handleTouchStart,
    handleLanguageSwitch,
    switchLanguage,
    fetchTranslation,
    updateUrlWithLanguage,
    /**
     * 检查是否有临时保存的评论
     */
    checkTempComment() {
      const articleId = this.id
      const tempCommentKey = `tempComment_${articleId}`

      try {
        const savedComment = localStorage.getItem(tempCommentKey)
        if (savedComment) {
          const commentData = JSON.parse(savedComment)

          // 检查是否过期(24小时)
          const now = Date.now()
          const commentAge = now - commentData.timestamp

          if (commentAge < 24 * 60 * 60 * 1000) {
            this.tempComment = commentData.content
            this.shouldLoadComments = true

            // 延迟一点时间确保评论组件已加载
            setTimeout(() => {
              // 使用事件总线将评论内容发送到评论框组件
              $emit(this.$bus, 'restore-comment', this.tempComment)

              // 提示用户
              this.$message({
                message: '已恢复您之前的评论内容',
                type: 'success',
              })

              // 滚动到评论区
              this.$nextTick(() => {
                const commentElement = document.querySelector('.comment-head')
                if (commentElement) {
                  commentElement.scrollIntoView({ behavior: 'smooth' })
                }
              })

              // 清除临时评论
              localStorage.removeItem(tempCommentKey)
            }, 500)
          } else {
            // 过期则删除
            localStorage.removeItem(tempCommentKey)
          }
        }
      } catch (error) {
        console.error('恢复评论出错:', error)
        localStorage.removeItem(tempCommentKey)
      }
    },

    /**
     * 检查是否有保存的页面状态
     */
    checkPageState() {
      const articleId = this.id
      const pageStateKey = `pageState_${articleId}`

      try {
        const savedState = localStorage.getItem(pageStateKey)
        if (savedState) {
          const stateData = JSON.parse(savedState)

          // 检查是否过期(1小时)
          const now = Date.now()
          const stateAge = now - stateData.timestamp

          if (stateAge < 60 * 60 * 1000) {
            this.shouldLoadComments = true

            // 延迟一点时间确保评论组件已加载
            setTimeout(() => {
              // 使用事件总线将状态数据发送到评论组件
              $emit(this.$bus, 'restore-page-state', stateData)

              // 恢复滚动位置
              if (stateData.scrollPosition) {
                window.scrollTo({
                  top: stateData.scrollPosition,
                  behavior: 'smooth',
                })
              }

              // 提示用户
              this.$message({
                message: '已恢复您的操作状态',
                type: 'success',
              })

              // 清除保存的状态
              localStorage.removeItem(pageStateKey)
            }, 1000) // 延迟确保评论组件完全加载
          } else {
            // 过期则删除
            localStorage.removeItem(pageStateKey)
          }
        }
      } catch (error) {
        console.error('恢复页面状态出错:', error)
        localStorage.removeItem(pageStateKey)
      }
    },

    setupCommentIntersectionObserver() {
      if (this.shouldLoadComments) {
        this.teardownCommentIntersectionObserver()
        return
      }

      this.teardownCommentIntersectionObserver()

      const target = this.$refs.commentSentinel
      if (!target) {
        return
      }

      if (
        typeof window === 'undefined' ||
        typeof IntersectionObserver === 'undefined'
      ) {
        this.shouldLoadComments = true
        return
      }

      this.commentObserver = new IntersectionObserver(
        (entries) => {
          const shouldLoad = entries.some(
            (entry) => entry.isIntersecting || entry.intersectionRatio > 0
          )
          if (shouldLoad) {
            this.shouldLoadComments = true
            this.teardownCommentIntersectionObserver()
          }
        },
        {
          root: null,
          rootMargin: '800px 0px',
          threshold: 0,
        }
      )

      this.commentObserver.observe(target)
    },

    teardownCommentIntersectionObserver() {
      if (this.commentObserver) {
        try {
          this.commentObserver.disconnect()
        } catch (e) {
        } finally {
          this.commentObserver = null
        }
      }
    },

    /**
     * 初始化语言设置
     * 修复重复调用 /api/translation/default-lang 接口的问题
     * 统一处理语言配置获取和语言设置逻辑
     */
    initializeLanguageSettings,
    getDefaultTargetLanguage,
    getArticleAvailableLanguages,
    generateLanguageButtons,

  },
  emits: ['restore-comment', 'restore-page-state'],
}
</script>
