<template>
  <div id="app">
    <router-view />
    <!-- 全局验证码容器 -->
    <Teleport to="body">
      <component
        :is="captchaContainerComponent"
        v-if="captchaVisible && captchaContainerComponent"
      />
    </Teleport>
    <!-- 全局异步通知组件 -->
    <async-notification ref="globalNotification" />
    <!-- 全局邮箱收集组件 -->
    <GlobalEmailCollectionAsync
      v-if="showGlobalEmailCollection"
      :visible="showGlobalEmailCollection"
      :userInfo="tempUserData"
      :provider="emailCollectionProvider"
      @complete="handleEmailCollectionComplete"
    />
    <!-- AI聊天（支持Live2D看板娘模式或简单按钮模式） -->
    <!-- mode从后台配置读取，默认为 'live2d' -->
    <Live2DAsync v-if="showLive2D" :mode="waifuDisplayMode" />
  </div>
</template>

<script>
import { useMainStore } from '@/stores/main'
import globalEmailCollectionMixin from '@/mixins/globalEmailCollection.js'
import { defineAsyncComponent } from 'vue'
import { ensureSessionValid, hasStoredSessionToken } from '@/utils/sessionValidation'

export default {
  name: 'App',
  mixins: [globalEmailCollectionMixin],
  components: {
    AsyncNotification: defineAsyncComponent(() => import('@/components/common/AsyncNotification.vue')),
    GlobalEmailCollectionAsync: defineAsyncComponent(() => import('@/components/common/GlobalEmailCollection.vue')),
    Live2DAsync: defineAsyncComponent(() => import('@/components/live2d/index.vue')),
  },
  data() {
    return {
      currentLang: 'zh', // 默认中文
      captchaContainerComponent: null,
      captchaContainerLoadingPromise: null,
      showLive2D: false,
    }
  },

  computed: {
    mainStore() {
      return useMainStore()
    },
    waifuDisplayMode() {
      // 从 webInfo 中读取显示模式，默认为 'auto'，交由组件自身兜底
      return this.mainStore?.webInfo?.waifuDisplayMode || 'auto'
    },
    captchaVisible() {
      return this.mainStore.captcha.show
    },
  },

  watch: {
    captchaVisible(visible) {
      if (visible) {
        this.ensureCaptchaContainerLoaded()
      }
    },
    '$route.path': function (newPath) {
      // 部分页面自行管理标题（如分类页、文章页），跳过全局覆盖
      const selfManagedTitlePaths = ['/sort', '/article']
      const isSelfManaged = selfManagedTitlePaths.some(p => newPath === p || newPath.startsWith(p + '/'))
      if (!isSelfManaged) {
        const homePageTitle = this.mainStore.homePageTitle
        if (homePageTitle) {
          document.title = homePageTitle
          window.OriginTitile = homePageTitle
        }
      }
    },
  },

  created() {
    // 获取用户语言偏好
    const savedLang = localStorage.getItem('preferredLanguage')
    if (savedLang === 'en' || savedLang === 'zh') {
      this.currentLang = savedLang
    } else {
      // 检测浏览器语言
      const browserLang = navigator.language || navigator.userLanguage
      if (browserLang.toLowerCase().startsWith('en')) {
        this.currentLang = 'en'
      }
    }

    // 初始化时设置浏览器标签标题（仅使用 webTitle，回退 webName；homeTitle 是备案名，不显示在标签上）
    const homePageTitle = this.mainStore.homePageTitle
    if (homePageTitle) {
      document.title = homePageTitle
      window.OriginTitile = homePageTitle
    }

    // 确保导航栏初始状态正确（修复首次访问时导航栏不显示的问题）
    this.mainStore.changeToolbarStatus({
      visible: true,
      enter: false,
    })
  },

  mounted() {
    // 确保字体加载
    document.body.style.fontFamily = 'var(--globalFont), serif'

    if (this.captchaVisible) {
      this.ensureCaptchaContainerLoaded()
    }

    // 注册全局通知实例
    if (this.$refs.globalNotification) {
      this.$notify.setInstance(this.$refs.globalNotification)
    }

    this.runWhenIdle(() => {
      this.showLive2D = true
    })

    // 初始化鼠标点击效果（根据后端配置自动选择效果类型）
    this.runWhenIdle(() => {
      import('@/composables/useMouseClickEffect')
        .then(({ initMouseClickEffect }) => {
          this.mouseClickEffectCleanup = initMouseClickEffect(this.mainStore)
        })
        .catch((err) => {
          console.error('初始化鼠标点击效果失败:', err)
        })
    })

    document.addEventListener('visibilitychange', this.handleVisibilityChange)
  },

  beforeUnmount() {
    // 清理鼠标点击效果
    if (this.mouseClickEffectCleanup) {
      this.mouseClickEffectCleanup()
    }

    document.removeEventListener('visibilitychange', this.handleVisibilityChange)
  },

  methods: {
    runWhenIdle(task) {
      if (typeof window !== 'undefined' && typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(() => task(), { timeout: 1500 })
        return
      }

      setTimeout(() => task(), 0)
    },
    ensureCaptchaContainerLoaded() {
      if (this.captchaContainerComponent) {
        return Promise.resolve(this.captchaContainerComponent)
      }

      if (!this.captchaContainerLoadingPromise) {
        this.captchaContainerLoadingPromise = import(
          '@/components/common/CaptchaContainer.vue'
        )
          .then((module) => {
            this.captchaContainerComponent = module.default || module
            return this.captchaContainerComponent
          })
          .finally(() => {
            this.captchaContainerLoadingPromise = null
          })
      }

      return this.captchaContainerLoadingPromise
    },
    handleVisibilityChange() {
      if (document.visibilityState !== 'visible' || !hasStoredSessionToken()) {
        return
      }

      ensureSessionValid({
        force: true,
        source: 'visibility',
        currentPath: this.$route.fullPath,
      }).catch(() => {})
    },
    handleLanguageChange(lang) {
      if (this.currentLang === lang) return

      this.currentLang = lang
      localStorage.setItem('preferredLanguage', lang)

      // 更新URL参数并刷新页面以应用语言更改
      const url = new URL(window.location)
      url.searchParams.set('lang', lang)
      window.location.href = url.toString()
    },
  },
}
</script>

<style>
* {
  font-family: 'MyAwesomeFont', serif;
}
/* 消息通知全局层级，始终最高优先级显示 */
.el-message {
  z-index: 10000 !important;
}

/* 验证码错误消息同样需要最高层级 */
.captcha-error-message {
  z-index: 10000 !important;
}
.global-language-switch {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  background-color: rgba(255, 255, 255, 0.7);
  padding: 5px 10px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
.el-dropdown-link {
  cursor: pointer;
  color: #409eff;
}
</style>
