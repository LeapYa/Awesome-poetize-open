<template>
  <div>
    <!-- 登陆和注册 -->
    <div
      v-if="$common.isEmpty(currentUser)"
      class="myCenter in-up-container my-animation-hideToShow"
      :class="{
        'in-up-container--boxed':
          loginStyle !== 'immersive' && loginStyle !== 'split',
      }"
    >
      <!-- 背景图片（minimal 样式不需要；split 样式自行渲染左栏封面） -->
      <el-image
        v-if="showLoginBackground"
        class="my-el-image"
        style="position: absolute"
        v-once
        lazy
        :src="randomCoverUrl"
        fit="cover"
      >
        <template v-slot:error>
          <div class="image-slot"></div>
        </template>
      </el-image>
      <!-- webInfo 就绪前不渲染样式面板，避免首次访问时先闪 classic 再切换到配置样式 -->
      <template v-if="loginStyleReady">
      <!-- 经典双滑块样式（login_style = classic） -->
      <ClassicLoginPanel
        v-if="loginStyle === 'classic'"
        v-model:account="account"
        v-model:password="password"
        v-model:username="username"
        v-model:email="email"
        v-model:code="code"
        :providers="thirdPartyLoginConfig.enable ? enabledThirdPartyProviders : []"
        @login="showLoginVerify"
        @register="showRegistVerify"
        @forgot="changeDialog('找回密码')"
        @email-code="changeDialog('邮箱验证码')"
        @third-party="showThirdPartyLoginVerify"
      ></ClassicLoginPanel>

      <!-- 左右分栏样式（login_style = split） -->
      <SplitLoginPanel
        v-else-if="loginStyle === 'split'"
        v-model:account="account"
        v-model:password="password"
        v-model:username="username"
        v-model:email="email"
        v-model:code="code"
        :logo-image="mainStore.webInfo.logoImage"
        :site-title="loginSiteTitle"
        :cover-image="randomCoverUrl"
        :providers="thirdPartyLoginConfig.enable ? enabledThirdPartyProviders : []"
        :third-position="loginThirdPosition"
        @login="showLoginVerify"
        @register="showRegistVerify"
        @forgot="changeDialog('找回密码')"
        @email-code="changeDialog('邮箱验证码')"
        @third-party="showThirdPartyLoginVerify"
      ></SplitLoginPanel>

      <!-- 终端极客风样式（login_style = terminal） -->
      <TerminalLoginPanel
        v-else-if="loginStyle === 'terminal'"
        v-model:account="account"
        v-model:password="password"
        v-model:username="username"
        v-model:email="email"
        v-model:code="code"
        :site-title="loginSiteTitle"
        :providers="thirdPartyLoginConfig.enable ? enabledThirdPartyProviders : []"
        @login="showLoginVerify"
        @register="showRegistVerify"
        @forgot="changeDialog('找回密码')"
        @email-code="changeDialog('邮箱验证码')"
        @third-party="showThirdPartyLoginVerify"
      ></TerminalLoginPanel>

      <!-- 磨砂典雅样式（login_style = frosted） -->
      <FrostedLoginPanel
        v-else-if="loginStyle === 'frosted'"
        v-model:account="account"
        v-model:password="password"
        v-model:username="username"
        v-model:email="email"
        v-model:code="code"
        :logo-image="mainStore.webInfo.logoImage"
        :site-title="loginSiteTitle"
        :providers="thirdPartyLoginConfig.enable ? enabledThirdPartyProviders : []"
        @login="showLoginVerify"
        @register="showRegistVerify"
        @forgot="changeDialog('找回密码')"
        @email-code="changeDialog('邮箱验证码')"
        @third-party="showThirdPartyLoginVerify"
      ></FrostedLoginPanel>

      <!-- 沉浸式大字排版样式（login_style = immersive） -->
      <ImmersiveLoginPanel
        v-else-if="loginStyle === 'immersive'"
        v-model:account="account"
        v-model:password="password"
        v-model:username="username"
        v-model:email="email"
        v-model:code="code"
        :site-title="loginSiteTitle"
        :providers="thirdPartyLoginConfig.enable ? enabledThirdPartyProviders : []"
        @login="showLoginVerify"
        @register="showRegistVerify"
        @forgot="changeDialog('找回密码')"
        @email-code="changeDialog('邮箱验证码')"
        @third-party="showThirdPartyLoginVerify"
      ></ImmersiveLoginPanel>

      <!-- 卡片系样式（login_style = card / glass / minimal） -->
      <LoginCard
        v-else
        :variant="loginStyle"
        v-model:account="account"
        v-model:password="password"
        v-model:username="username"
        v-model:email="email"
        v-model:code="code"
        :logo-image="mainStore.webInfo.logoImage"
        :site-title="loginSiteTitle"
        :providers="thirdPartyLoginConfig.enable ? enabledThirdPartyProviders : []"
        :third-position="loginThirdPosition"
        @login="showLoginVerify"
        @register="showRegistVerify"
        @forgot="changeDialog('找回密码')"
        @email-code="changeDialog('邮箱验证码')"
        @third-party="showThirdPartyLoginVerify"
      ></LoginCard>
      </template>
    </div>

    <!-- 用户信息 -->
    <div v-else class="user-container myCenter my-animation-hideToShow">
      <!-- 背景图片 -->
      <el-image
        class="my-el-image"
        style="position: absolute"
        v-once
        lazy
        :src="
          mainStore.webInfo.randomCover &&
          mainStore.webInfo.randomCover.length > 0
            ? mainStore.webInfo.randomCover[
                Math.floor(Math.random() * mainStore.webInfo.randomCover.length)
              ]
            : '/assets/backgroundPicture.jpg'
        "
        fit="cover"
      >
        <template v-slot:error>
          <div class="image-slot"></div>
        </template>
      </el-image>
      <UserProfile
        :user="currentUser"
        @change-dialog="changeDialog"
        @submit="submitUserInfo"
      ></UserProfile>
    </div>

    <!-- 账号相关多用途弹窗（改手机/邮箱/密码/头像、找回密码、邮箱验证码） -->
    <AccountDialog
      :visible="showDialog"
      :title="dialogTitle"
      :code-string="codeString"
      :is-third-party-user="Boolean(isThirdPartyUser)"
      v-model:phoneNumber="phoneNumber"
      v-model:email="email"
      v-model:code="code"
      v-model:password="password"
      v-model:oldPassword="oldPassword"
      v-model:newPassword="newPassword"
      v-model:confirmPassword="confirmPassword"
      v-model:username="username"
      v-model:passwordFlag="passwordFlag"
      @get-code="getCode"
      @submit="submitDialog"
      @close="closeDialog"
      @add-picture="addPicture"
    ></AccountDialog>

    <!-- 添加滑动验证组件 -->
    <component
      :is="captchaWrapperComponent"
      v-if="showCaptchaWrapper && captchaWrapperComponent"
      :visible="showCaptchaWrapper"
      :action="captchaAction"
      :force-slide="false"
      @success="onVerifySuccess"
      @fail="closeVerify"
      @refresh="$emit('refresh')"
      @close="closeVerify"
    ></component>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'
import { $on, $off, $once, $emit } from '../utils/gogocodeTransfer'
import { useMainStore } from '@/stores/main'
import { encrypt } from '@/utils/crypto-utils'

import { checkCaptchaWithCache } from '@/utils/captchaUtil'
import { handleLoginRedirect } from '../utils/tokenExpireHandler'

export default {
  components: {
    ClassicLoginPanel: defineAsyncComponent(() =>
      import('./user/ClassicLoginPanel.vue')
    ),
    LoginCard: defineAsyncComponent(() => import('./user/LoginCard.vue')),
    SplitLoginPanel: defineAsyncComponent(() =>
      import('./user/SplitLoginPanel.vue')
    ),
    TerminalLoginPanel: defineAsyncComponent(() =>
      import('./user/TerminalLoginPanel.vue')
    ),
    ImmersiveLoginPanel: defineAsyncComponent(() =>
      import('./user/ImmersiveLoginPanel.vue')
    ),
    FrostedLoginPanel: defineAsyncComponent(() =>
      import('./user/FrostedLoginPanel.vue')
    ),
    UserProfile: defineAsyncComponent(() => import('./user/UserProfile.vue')),
    AccountDialog: defineAsyncComponent(() =>
      import('./user/AccountDialog.vue')
    ),
  },
  data() {
    return {
      currentUser: {},
      username: '',
      account: '',
      password: '',
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
      phoneNumber: '',
      email: '',
      avatar: '',
      showDialog: false,
      code: '',
      dialogTitle: '',
      codeString: '验证码',
      passwordFlag: null,
      intervalCode: null,
      showCaptchaWrapper: false,
      verifyAction: null,
      captchaAction: 'login',
      verifyParams: null,
      captchaWrapperComponent: null,
      captchaWrapperLoadingPromise: null,
      hasShownExpiredMessage: false,
      // webInfo 未就绪时的兜底标记（防止 bootstrap 异常导致登录页长期空白）
      loginStyleFallbackReady: false,
      loginStyleFallbackTimer: null,
      thirdPartyLoginConfig: {
        enable: false,
      },
      enabledThirdPartyProviders: [],
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    // 判断当前用户是否为第三方登录用户
    isThirdPartyUser() {
      return this.currentUser && this.currentUser.platformType
    },
    // 登录页样式：classic/card/glass/split/minimal/terminal/immersive/frosted，异常值回退 classic
    loginStyle() {
      const style = this.mainStore.webInfo.loginStyle
      const validStyles = ['classic', 'card', 'glass', 'split', 'minimal', 'terminal', 'immersive', 'frosted']
      return validStyles.includes(style) ? style : 'classic'
    },
    // minimal 纯色底不需要背景图；split 由组件自行渲染左栏封面
    showLoginBackground() {
      return this.loginStyle !== 'minimal' && this.loginStyle !== 'split'
    },
    // webInfo 是否已就绪（来自接口或本地缓存，默认空对象无 id）；超时兜底后强制渲染
    loginStyleReady() {
      return !!this.mainStore.webInfo.id || this.loginStyleFallbackReady
    },
    // 登录页展示的网站标题：直接使用 webTitle（必填字段，面板在 webInfo 就绪后才渲染）
    loginSiteTitle() {
      return this.mainStore.webInfo.webTitle || ''
    },
    // 登录页主题色（仅接受合法 #rrggbb，否则视为未配置）
    loginAccentColor() {
      const color = this.mainStore.webInfo.loginAccentColor
      return /^#[0-9a-fA-F]{6}$/.test(color || '') ? color : ''
    },
    // 卡片系第三方按钮位置：top 表单上方 / bottom 表单下方，异常值回退 top
    loginThirdPosition() {
      return this.mainStore.webInfo.loginThirdPosition === 'bottom'
        ? 'bottom'
        : 'top'
    },
    // 随机封面图（背景与分栏左栏共用）
    randomCoverUrl() {
      const covers = this.mainStore.webInfo.randomCover
      return covers && covers.length > 0
        ? covers[Math.floor(Math.random() * covers.length)]
        : '/assets/backgroundPicture.jpg'
    },
  },
  created() {
    // 初始化当前用户
    this.currentUser = this.mainStore.currentUser
    
    // 如果本地有用户信息，则主动校验一下 Token 是否已过期
    if (!this.$common.isEmpty(this.currentUser)) {
      this.checkUserToken()
    }

    // 动态设置页面SEO信息
    this.updatePageSEO()
    this.showExpiredSessionNotice()
  },
  watch: {
    showCaptchaWrapper(newVal) {
      if (newVal) {
        this.ensureCaptchaWrapperLoaded()
      }
    },
    '$route.query.expired': function () {
      this.showExpiredSessionNotice()
    },
    // 样式或主题色变化时同步登录主题色 CSS 变量
    loginStyle() {
      this.applyLoginAccentVars()
    },
    loginAccentColor() {
      this.applyLoginAccentVars()
    },
  },
  mounted() {
    // 获取第三方登录配置
    this.loadThirdPartyLoginConfig()

    // 注入登录主题色 CSS 变量（挂根节点以穿透 teleport 弹窗/验证码）
    this.applyLoginAccentVars()

    // 3 秒兜底：bootstrap 异常时也要渲染登录面板（按当前已知样式回退）
    this.loginStyleFallbackTimer = setTimeout(() => {
      this.loginStyleFallbackReady = true
    }, 3000)

    // 监听第三方登录配置变更事件
    $on(
      this.$bus,
      'thirdPartyLoginConfigChanged',
      this.handleThirdPartyConfigChange
    )

    // 监听登录状态变化，动态更新SEO
    this.$watch('mainStore.currentUser', () => {
      this.updatePageSEO()
    })

    // bootstrap 晚于本组件挂载时，状态到达后刷新第三方登录图标
    this.$watch('mainStore.thirdLoginStatus', (status) => {
      if (status) {
        this.applyThirdPartyLoginConfig(status)
      }
    })
  },
  beforeUnmount() {
    // 离开页面时清除主题色变量，避免影响评论区等其他场景的验证码配色
    this.clearLoginAccentVars()

    // 清理验证码倒计时，避免卸载后间隔器继续持有组件引用
    if (this.intervalCode) {
      clearInterval(this.intervalCode)
      this.intervalCode = null
    }

    // 清理兜底定时器
    if (this.loginStyleFallbackTimer) {
      clearTimeout(this.loginStyleFallbackTimer)
      this.loginStyleFallbackTimer = null
    }

    // 移除事件监听
    $off(
      this.$bus,
      'thirdPartyLoginConfigChanged',
      this.handleThirdPartyConfigChange
    )
  },
  methods: {
    // 计算 #rrggbb 的相对亮度（0~1，用于暗色安全色阈值判断）
    hexLuminance(hex) {
      const r = parseInt(hex.slice(1, 3), 16)
      const g = parseInt(hex.slice(3, 5), 16)
      const b = parseInt(hex.slice(5, 7), 16)
      return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
    },
    // 暗色安全色：主题色过暗时向白混合提亮，避免在暗色卡片上"隐身"；亮度足够则原样保留
    toDarkModeSafe(hex) {
      const lum = this.hexLuminance(hex)
      if (lum >= 0.35) {
        return hex
      }
      const ratio = (0.6 - lum) / (1 - lum)
      const mixed = [1, 3, 5].map((i) => {
        const c = parseInt(hex.slice(i, i + 2), 16)
        return Math.round(c + (255 - c) * ratio)
          .toString(16)
          .padStart(2, '0')
      })
      return '#' + mixed.join('')
    },
    // 注入登录主题色 CSS 变量：现代样式下弹窗按钮/验证码滑块/卡片主按钮跟随此色；
    // classic 不设置变量（回退原版配色）；frosted 默认主题色为其原版玫粉 #f04494
    applyLoginAccentVars() {
      const root = document.documentElement
      if (this.loginStyle === 'classic') {
        this.clearLoginAccentVars()
        return
      }

      const accent =
        this.loginAccentColor ||
        (this.loginStyle === 'frosted' ? '#f04494' : '')
      root.style.setProperty('--loginAccent', accent || '#1f1f1f')
      // 主按钮专用：不参与暗色重映射，两种模式保持原色（黑底白字在暗色卡上成立）
      root.style.setProperty('--loginAccentButton', accent || '#1f1f1f')
      // 柔和变体：验证码轨道/聚焦光晕等浅色背景使用（#rrggbb + 40 透明度）
      root.style.setProperty('--loginAccentSoft', (accent || '#1f1f1f') + '40')
      // 暗色安全色变量族：登录卡片内的链接/聚焦描边等细小元素在暗色模式下重映射为此组值，
      // 配置过暗时自动提亮；主按钮已豁免，不受影响
      const darkAccent = accent ? this.toDarkModeSafe(accent) : '#f5f5f5'
      root.style.setProperty('--loginAccentDark', darkAccent)
      root.style.setProperty('--loginAccentSoftDark', darkAccent + '40')
    },
    clearLoginAccentVars() {
      const root = document.documentElement
      root.style.removeProperty('--loginAccent')
      root.style.removeProperty('--loginAccentButton')
      root.style.removeProperty('--loginAccentSoft')
      root.style.removeProperty('--loginAccentDark')
      root.style.removeProperty('--loginAccentSoftDark')
    },
    ensureCaptchaWrapperLoaded() {
      if (this.captchaWrapperComponent) {
        return Promise.resolve(this.captchaWrapperComponent)
      }

      if (!this.captchaWrapperLoadingPromise) {
        this.captchaWrapperLoadingPromise = import('./common/CaptchaWrapper.vue')
          .then((module) => {
            this.captchaWrapperComponent = module.default || module
            return this.captchaWrapperComponent
          })
          .finally(() => {
            this.captchaWrapperLoadingPromise = null
          })
      }

      return this.captchaWrapperLoadingPromise
    },
    // 根据登录状态动态更新页面SEO信息
    updatePageSEO() {
      // 优先使用webTitle，fallback到webName，最后使用默认值
      const webTitle =
        this.mainStore.webInfo?.webTitle ||
        this.mainStore.webInfo?.webName ||
        'POETIZE'
      const isLoggedIn = !this.$common.isEmpty(this.mainStore.currentUser)

      let title, description, keywords

      if (isLoggedIn) {
        // 已登录：个人中心
        const userName = this.mainStore.currentUser?.username || '用户'
        title = `个人中心 - ${webTitle}`
        description = `${userName}的个人中心，管理个人资料和账户设置`
        keywords = `个人中心,用户资料,账户设置,${webTitle}`
      } else {
        // 未登录：登录页面
        title = `登录 - ${webTitle}`
        description = `登录${webTitle}，开始您的精彩之旅`
        keywords = `登录,注册,用户登录,${webTitle}`
      }

      // 更新页面title
      document.title = title
      window.OriginTitile = title

      // 更新meta标签
      this.updateMetaTags({
        title,
        description,
        keywords,
        'og:title': title,
        'og:description': description,
        'og:type': 'website',
      })
    },

    // 更新meta标签的通用方法
    updateMetaTags(metaData) {
      // 移除旧的动态meta标签
      document
        .querySelectorAll('meta[data-dynamic-seo="true"]')
        .forEach((el) => el.remove())

      // 添加新的meta标签
      Object.entries(metaData).forEach(([key, value]) => {
        if (!value || key === 'title') return // title已经设置过

        const meta = document.createElement('meta')
        const isProperty = key.startsWith('og:') || key.startsWith('twitter:')

        if (isProperty) {
          meta.setAttribute('property', key)
        } else {
          meta.setAttribute('name', key)
        }

        meta.setAttribute('content', value)
        meta.setAttribute('data-dynamic-seo', 'true')

        if (document.head) {
          document.head.appendChild(meta)
        }
      })
    },
    showExpiredSessionNotice() {
      if (
        this.$route.query.expired !== 'true' ||
        this.hasShownExpiredMessage
      ) {
        return
      }

      this.hasShownExpiredMessage = true
      this.$nextTick(() => {
        this.$message.warning(
          '你之前的登录状态已经失效，可能是 Token 已过期，请重新登录。'
        )
      })
    },
    // 主动校验前台 Token 是否过期
    checkUserToken() {
      // 通过发出一个需要验证的请求来判断前台Token是否已被移出或过期（401/300）
      this.$http.get(this.$constant.baseURL + '/user/current', {}, false).catch(() => {
        // 请求失败(例如 401)，统一拦截器会自动处理清理本地数据并退出
      });
    },
    addPicture(res) {
      this.avatar = res
      this.submitDialog()
    },
    showLoginVerify() {
      if (
        this.$common.isEmpty(this.account) ||
        this.$common.isEmpty(this.password)
      ) {
        this.$message({
          message: '请输入账号或密码！',
          type: 'error',
        })
        return
      }

      // 检查是否需要验证码
      checkCaptchaWithCache('login')
        .then((required) => {
          if (required) {
            this.verifyAction = 'login'
            this.captchaAction = 'login'
            this.showCaptchaWrapper = true
          } else {
            // 不需要验证码，直接登录
            this.login()
          }
        })
        .catch((err) => {
          console.error('验证码检查出错:', err)
          // 出错时默认不使用验证码
          this.login()
        })
    },

    showRegistVerify() {
      if (
        this.$common.isEmpty(this.username) ||
        this.$common.isEmpty(this.password)
      ) {
        this.$message({
          message: '请输入用户名或密码！',
          type: 'error',
        })
        return
      }

      if (this.$common.isEmpty(this.email)) {
        this.$message({
          message: '请输入邮箱！',
          type: 'error',
        })
        return false
      }

      if (this.$common.isEmpty(this.code)) {
        this.$message({
          message: '请输入验证码！',
          type: 'error',
        })
        return
      }

      if (
        this.username.indexOf(' ') !== -1 ||
        this.password.indexOf(' ') !== -1
      ) {
        this.$message({
          message: '用户名或密码不能包含空格！',
          type: 'error',
        })
        return
      }

      // 检查是否需要验证码
      checkCaptchaWithCache('register').then((required) => {
        if (required) {
          this.verifyAction = 'regist'
          this.captchaAction = 'register'
          this.showCaptchaWrapper = true
        } else {
          // 不需要验证码，直接注册
          this.regist()
        }
      })
    },

    showThirdPartyLoginVerify(provider) {
      // 检查是否需要验证码
      checkCaptchaWithCache('login').then((required) => {
        if (required) {
          this.verifyAction = 'thirdPartyLogin'
          this.captchaAction = 'login'
          this.verifyParams = provider
          this.showCaptchaWrapper = true
        } else {
          // 不需要验证码，直接执行第三方登录
          this.thirdPartyLogin(provider)
        }
      })
    },

    onVerifySuccess(token) {
      this.showCaptchaWrapper = false

      // 根据当前操作类型继续相应流程
      if (this.verifyAction === 'login') {
        this.login(token)
      } else if (this.verifyAction === 'regist') {
        this.regist(token)
      } else if (this.verifyAction === 'thirdPartyLogin') {
        this.thirdPartyLogin(this.verifyParams, token)
      } else if (this.verifyAction === 'sendVerificationCode') {
        this.sendVerificationCode({ ...this.verifyParams, verificationToken: token })
      }
    },

    closeVerify() {
      this.showCaptchaWrapper = false

      if (this.verifyAction === 'sendVerificationCode') {
        // 重新打开之前的对话框
        this.dialogTitle = this.verifyParams.dialogTitle
        this.$nextTick(() => {
          this.showDialog = true
        })
      }

      // 重置验证相关状态
      this.verifyAction = null
      this.captchaAction = 'login'
      this.verifyParams = null
    },
    /**
     * 登录
     * 注意：虽然前端将同一个token同时存储为userToken和adminToken
     * 但实际的权限控制是在后端严格执行的，不会导致权限绕过问题：
     * 1. 后端通过token前缀和HMAC签名验证token类型
     * 2. 验证用户在数据库中的userType字段
     * 3. 使用@LoginCheck注解进行权限级别验证
     * 4. 即使前端错误设置了adminToken，后端也会拒绝非管理员访问管理员接口
     */
    async login(verificationToken = '') {
      if (
        this.$common.isEmpty(this.account) ||
        this.$common.isEmpty(this.password)
      ) {
        this.$message({
          message: '请输入账号或密码！',
          type: 'error',
        })
        return
      }

      try {
        let user = {
          account: this.account.trim(),
          password: await encrypt(this.password.trim()),
          isAdmin: false, // 普通用户登录，设置为false
        }

        // 添加验证令牌
        if (verificationToken) {
          user.verificationToken = verificationToken
        }

        // 对整个请求体进行加密
        let encryptedUser = await encrypt(JSON.stringify(user))

        this.$http
          .post(
            this.$constant.baseURL + '/user/login',
            { data: encryptedUser },
            true,
            true
          )
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              // Token由后端通过HttpOnly Cookie下发，前端不再存储
              this.mainStore.loadCurrentUser(res.data)
              this.mainStore.loadCurrentAdmin(res.data)

              // 显示登录成功消息
              if (this.$route.query.expired === 'true') {
                this.$message.success('重新登录成功')
              } else {
                this.$message.success('登录成功')
              }

              // 如果来自 /verify 路径，需要根据用户类型进行不同跳转

              if (this.$route.query.fromVerify === 'true') {
                // 检查是否是管理员（userType为0或1）
                if (res.data.userType === 0 || res.data.userType === 1) {
                  // 管理员用户，跳转到 /welcome（忽略 redirect 参数）
                  this.$router.replace('/welcome')
                } else {
                  // 普通用户，跳转到首页
                  this.$router.replace('/')
                }
              } else {
                // 正常情况下的重定向处理
                // 如果有redirect参数且不是/user或/verify，则跳转到该地址
                const redirect = this.$route.query.redirect
                if (
                  redirect &&
                  redirect !== '/user' &&
                  redirect !== '/verify'
                ) {
                  // 如果是跳转到后台管理系统（/admin 开头），需要跨应用跳转
                  if (redirect.startsWith('/admin')) {
                    window.location.href = redirect
                  } else {
                    this.$router.replace(redirect)
                  }
                } else {
                  this.$router.replace('/')
                }
              }
            }
          })
          .catch((error) => {
            if (error && (error.code === 460 || error.code === 461)) {
              this.verifyAction = 'login'
              this.captchaAction = 'login'
              this.showCaptchaWrapper = true
              return
            }
            this.$message({
              message: error.message,
              type: 'error',
            })
          })
      } catch (error) {
        this.$message({
          message: '加密失败: ' + error.message,
          type: 'error',
        })
      }
    },
    async regist(verificationToken) {
      if (
        this.$common.isEmpty(this.username) ||
        this.$common.isEmpty(this.password)
      ) {
        this.$message({
          message: '请输入用户名或密码！',
          type: 'error',
        })
        return
      }

      if (
        this.dialogTitle === '邮箱验证码' &&
        this.$common.isEmpty(this.email)
      ) {
        this.$message({
          message: '请输入邮箱！',
          type: 'error',
        })
        return false
      }

      if (this.$common.isEmpty(this.code)) {
        this.$message({
          message: '请输入验证码！',
          type: 'error',
        })
        return
      }

      if (
        this.username.indexOf(' ') !== -1 ||
        this.password.indexOf(' ') !== -1
      ) {
        this.$message({
          message: '用户名或密码不能包含空格！',
          type: 'error',
        })
        return
      }

      try {
        let user = {
          username: this.username.trim(),
          code: this.code.trim(),
          password: await encrypt(this.password.trim()),
        }

        if (this.dialogTitle === '邮箱验证码') {
          user.email = this.email
        }

        // 添加验证令牌
        if (verificationToken) {
          user.verificationToken = verificationToken
        }

        this.$http
          .post(this.$constant.baseURL + '/user/regist', user)
          .then(async (res) => {
            if (!this.$common.isEmpty(res.data)) {
              // Token由后端通过HttpOnly Cookie下发
              this.mainStore.loadCurrentUser(res.data)
              this.username = ''
              this.password = ''
              this.email = ''
              this.code = ''

              // 检查是否有重定向URL
              const redirect = this.$route.query.redirect
              const hasComment = this.$route.query.hasComment
              const hasReplyAction = this.$route.query.hasReplyAction

              if (redirect) {
                // 保留hasComment和hasReplyAction参数以触发评论/回复状态恢复
                const query = {}
                if (hasComment === 'true') query.hasComment = 'true'
                if (hasReplyAction === 'true') query.hasReplyAction = 'true'
                
                // 如果是跳转到后台管理系统（/admin 开头），需要跨应用跳转
                if (redirect.startsWith('/admin')) {
                  const queryString = Object.keys(query).length > 0 
                    ? '?' + new URLSearchParams(query).toString() 
                    : ''
                  window.location.href = redirect + queryString
                } else {
                  this.$router.push({ path: redirect, query: query })
                }
              } else {
                // 如果没有重定向，则跳转到IM聊天室（IM现在是主站的一部分）
                this.$router.push({ path: '/im' })
              }
            }
          })
          .catch((error) => {
            if (error && (error.code === 460 || error.code === 461)) {
              this.verifyAction = 'regist'
              this.captchaAction = 'register'
              this.showCaptchaWrapper = true
              return
            }
            this.$message({
              message: error.message,
              type: 'error',
            })
          })
      } catch (error) {
        this.$message({
          message: '加密失败: ' + error.message,
          type: 'error',
        })
      }
    },
    submitUserInfo() {
      if (!this.checkParameters()) {
        return
      }

      let user = {
        username: this.currentUser.username,
        gender: this.currentUser.gender,
      }

      if (!this.$common.isEmpty(this.currentUser.introduction)) {
        user.introduction = this.currentUser.introduction.trim()
      }

      this.$confirm('确认保存？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success',
        center: true,
      })
        .then(() => {
          this.$http
            .post(this.$constant.baseURL + '/user/updateUserInfo', user)
            .then((res) => {
              if (!this.$common.isEmpty(res.data)) {
                this.mainStore.loadCurrentUser(res.data)
                this.currentUser = this.mainStore.currentUser
                this.$message({
                  message: '修改成功！',
                  type: 'success',
                })
              }
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
            message: '已取消保存!',
          })
        })
    },
    checkParams(params) {
      if (
        this.dialogTitle === '修改手机号' ||
        this.dialogTitle === '绑定手机号' ||
        (this.dialogTitle === '找回密码' && this.passwordFlag === 1)
      ) {
        params.flag = 1
        if (this.dialogTitle === '找回密码') {
          if (this.$common.isEmpty(this.username)) {
            this.$message.error('请输入用户名！')
            return false
          }
          params.username = this.username.trim()
        }
        if (this.$common.isEmpty(this.phoneNumber)) {
          this.$message({
            message: '请输入手机号！',
            type: 'error',
          })
          return false
        }
        if (!/^1[345789]\d{9}$/.test(this.phoneNumber)) {
          this.$message({
            message: '手机号格式有误！',
            type: 'error',
          })
          return false
        }
        params.place = this.phoneNumber
        return true
      } else if (
        this.dialogTitle === '修改邮箱' ||
        this.dialogTitle === '绑定邮箱' ||
        this.dialogTitle === '邮箱验证码' ||
        (this.dialogTitle === '找回密码' && this.passwordFlag === 2)
      ) {
        params.flag = 2
        if (this.dialogTitle === '找回密码') {
          if (this.$common.isEmpty(this.username)) {
            this.$message.error('请输入用户名！')
            return false
          }
          params.username = this.username.trim()
        }
        if (this.$common.isEmpty(this.email)) {
          this.$message({
            message: '请输入邮箱！',
            type: 'error',
          })
          return false
        }
        if (!/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(this.email)) {
          this.$message({
            message: '邮箱格式有误！',
            type: 'error',
          })
          return false
        }
        params.place = this.email
        return true
      } else if (this.dialogTitle === '修改密码') {
        params.flag = 2
        params.place = this.currentUser.email
        return true
      }
      return false
    },
    checkParameters() {
      if (this.$common.isEmpty(this.currentUser.username)) {
        this.$message({
          message: '请输入用户名！',
          type: 'error',
        })
        return false
      }

      if (this.currentUser.username.indexOf(' ') !== -1) {
        this.$message({
          message: '用户名不能包含空格！',
          type: 'error',
        })
        return false
      }
      return true
    },
    changeDialog(value) {
      if (value === '邮箱验证码') {
        if (this.$common.isEmpty(this.email)) {
          this.$message({
            message: '请输入邮箱！',
            type: 'error',
          })
          return false
        }
        if (!/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(this.email)) {
          this.$message({
            message: '邮箱格式有误！',
            type: 'error',
          })
          return false
        }
      } else if (value === '修改密码') {
        if (this.$common.isEmpty(this.currentUser.email)) {
          this.$message.error('安全起见，请先绑定邮箱后再修改密码！')
          return false
        }
      }

      this.dialogTitle = value
      this.showDialog = true
    },
    submitDialog() {
      if (this.dialogTitle === '修改头像') {
        if (this.$common.isEmpty(this.avatar)) {
          this.$message({
            message: '请上传头像！',
            type: 'error',
          })
        } else {
          let user = {
            avatar: this.avatar.trim(),
          }

          this.$http
            .post(this.$constant.baseURL + '/user/updateUserInfo', user)
            .then((res) => {
              if (!this.$common.isEmpty(res.data)) {
                this.mainStore.loadCurrentUser(res.data)
                this.currentUser = this.mainStore.currentUser
                this.clearDialog()
                this.$message({
                  message: '修改成功！',
                  type: 'success',
                })
              }
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: 'error',
              })
            })
        }
      } else if (
        this.dialogTitle === '修改手机号' ||
        this.dialogTitle === '绑定手机号' ||
        this.dialogTitle === '修改邮箱' ||
        this.dialogTitle === '绑定邮箱'
      ) {
        this.updateSecretInfo()
      } else if (this.dialogTitle === '修改密码') {
        this.updatePassword()
      } else if (this.dialogTitle === '找回密码') {
        if (this.passwordFlag !== 1 && this.passwordFlag !== 2) {
          this.$message({
            message: '请选择找回方式！',
            type: 'error',
          })
        } else {
          this.updateSecretInfo()
        }
      } else if (this.dialogTitle === '邮箱验证码') {
        this.showDialog = false
      }
    },
    async updateSecretInfo() {
      if (this.$common.isEmpty(this.code)) {
        this.$message({
          message: '请输入验证码！',
          type: 'error',
        })
        return
      }
      // 只有普通注册用户才需要验证密码，第三方登录用户没有密码
      if (!this.isThirdPartyUser && this.$common.isEmpty(this.password)) {
        this.$message({
          message: '请输入密码！',
          type: 'error',
        })
        return
      }

      try {
        let params = {
          code: this.code.trim(),
          // 第三方用户没有密码，传空字符串
          password: this.isThirdPartyUser
            ? ''
            : await encrypt(this.password.trim()),
        }
        
        if (this.dialogTitle === '找回密码') {
          if (this.$common.isEmpty(this.username)) {
            this.$message.error('请输入用户名！')
            return
          }
          params.username = this.username.trim()
        }

        if (!this.checkParams(params)) {
          return
        }

        if (this.dialogTitle === '找回密码') {
          this.$http
            .post(
              this.$constant.baseURL + '/user/updateForForgetPassword',
              params,
              false,
              false
            )
            .then((res) => {
              this.clearDialog()
              this.$message({
                message: '修改成功，请重新登陆！',
                type: 'success',
              })
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: 'error',
              })
            })
        } else {
          this.$http
            .post(
              this.$constant.baseURL + '/user/updateSecretInfo',
              params,
              false,
              false
            )
            .then((res) => {
              if (!this.$common.isEmpty(res.data)) {
                this.mainStore.loadCurrentUser(res.data)
                this.currentUser = this.mainStore.currentUser
                this.clearDialog()
                this.$message({
                  message: '修改成功！',
                  type: 'success',
                })
              }
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: 'error',
              })
            })
        }
      } catch (error) {
        this.$message({
          message: '加密失败: ' + error.message,
          type: 'error',
        })
      }
    },
    async updatePassword() {
      if (this.$common.isEmpty(this.oldPassword)) {
        this.$message.error('请输入旧密码！')
        return
      }
      if (this.$common.isEmpty(this.newPassword)) {
        this.$message.error('请输入新密码！')
        return
      }
      if (this.newPassword !== this.confirmPassword) {
        this.$message.error('两次输入的新密码不一致！')
        return
      }
      if (this.$common.isEmpty(this.code)) {
        this.$message.error('请输入邮箱验证码！')
        return
      }

      try {
        let params = {
          place: await encrypt(this.oldPassword.trim()),
          password: await encrypt(this.newPassword.trim()),
          code: this.code.trim(),
          flag: 3
        }

        this.$http
          .post(
            this.$constant.baseURL + '/user/updateSecretInfo',
            params,
            false,
            false
          )
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.clearDialog()
              this.$message.success('密码修改成功，请使用新密码重新登录！')

              // 清除所有登录状态（token由后端通过cookie管理）
              this.mainStore.loadCurrentUser({})
              this.mainStore.loadCurrentAdmin({})
              
              // 强制刷新页面触发现有登出和重载判断，或者跳转回特定页面
              window.location.reload()
            }
          })
          .catch((error) => {
            this.$message.error(error.message)
          })
      } catch (error) {
        this.$message.error('加密失败: ' + error.message)
      }
    },
    getCode() {
      if (this.codeString === '验证码') {
        // 获取验证码前先进行参数检查
        let params = {}
        if (!this.checkParams(params)) {
          return
        }

        // 用业务类型而不是标题文案决定验证码动作，避免“邮箱验证码”串到找回密码流程
        const verificationPurpose = this.getVerificationPurpose()
        const action =
          verificationPurpose === 'forgetPassword' ? 'reset_password' : 'register'

        // 检查是否需要验证码
        checkCaptchaWithCache(action).then((required) => {
          if (required) {
            // 保存当前对话框状态
            const currentDialogTitle = this.dialogTitle

            // 先关闭对话框，避免遮挡验证组件
            this.showDialog = false

            // 设置验证操作为发送验证码，同时保存当前对话框信息
            this.verifyAction = 'sendVerificationCode'
            this.captchaAction = action
            this.verifyParams = {
              ...params,
              verificationPurpose,
              dialogTitle: currentDialogTitle,
            }

            // 显示滑块验证
            this.$nextTick(() => {
              this.showCaptchaWrapper = true
            })
          } else {
            // 不需要验证码，直接发送验证码
            this.sendVerificationCode({
              ...params,
              verificationPurpose,
              dialogTitle: this.dialogTitle,
            })
          }
        })
      } else {
        this.$message({
          message: '请稍后再试！',
          type: 'warning',
        })
      }
    },
    /**
     * 发送验证码
     */
    sendVerificationCode(params) {
      // 提取出保存的对话框标题
      const savedDialogTitle = params.dialogTitle
      const verificationPurpose = params.verificationPurpose

      // 从params中移除前端内部辅助字段，避免发送到后端API
      delete params.dialogTitle
      delete params.verificationPurpose

      // 如果有验证令牌，添加到参数中
      if (params.verificationToken) {
      }

      let url
      if (verificationPurpose === 'forgetPassword') {
        url = '/user/getCodeForForgetPassword'
      } else if (verificationPurpose === 'register') {
        url = '/user/getCodeForRegister'
      } else if (savedDialogTitle === '修改密码') {
        url = '/user/getCode' // 和后台复用一致机制的发送
      } else {
        url = '/user/getCodeForBind'
      }

      this.$http
        .get(this.$constant.baseURL + url, params)
        .then((res) => {
          this.$message({
            message: '验证码已发送，请注意查收！',
            type: 'success',
          })

          // 重新打开之前的对话框
          this.dialogTitle = savedDialogTitle
          this.$nextTick(() => {
            this.showDialog = true
          })
        })
        .catch((error) => {
          console.error('验证码发送失败:', error)
          this.$message({
            message: error.message,
            type: 'error',
          })

          // 发生错误也重新打开对话框
          this.dialogTitle = savedDialogTitle
          this.$nextTick(() => {
            this.showDialog = true
          })
        })

      // 开始倒计时
      this.codeString = '30'
      this.intervalCode = setInterval(() => {
        if (this.codeString === '0') {
          clearInterval(this.intervalCode)
          this.codeString = '验证码'
        } else {
          this.codeString = parseInt(this.codeString) - 1 + ''
        }
      }, 1000)
    },
    getVerificationPurpose() {
      if (this.dialogTitle === '找回密码') {
        return 'forgetPassword'
      }

      if (this.dialogTitle === '邮箱验证码') {
        return this.passwordFlag === 2 ? 'forgetPassword' : 'register'
      }

      if (this.dialogTitle === '修改密码') {
        return 'changePassword'
      }

      return 'bindOrUpdate'
    },
    closeDialog(done) {
      this.showDialog = false
      this.code = ''
      this.dialogTitle = ''
      this.passwordFlag = null
      this.avatar = ''
      this.oldPassword = ''
      this.newPassword = ''
      this.confirmPassword = ''

      if (typeof done === 'function') {
        done()
      }
    },
    clearDialog() {
      this.password = ''
      this.oldPassword = ''
      this.newPassword = ''
      this.confirmPassword = ''
      this.phoneNumber = ''
      this.email = ''
      this.avatar = ''
      this.showDialog = false
      this.code = ''
      this.dialogTitle = ''
      this.passwordFlag = null
    },
    thirdPartyLogin(provider, verificationToken) {
      if (!provider) return

      // 提取真正的重定向目标地址
      // 如果当前在登录页且有 redirect 参数，使用该参数作为目标地址
      // 否则使用当前路径（非登录页面的情况）
      const urlParams = new URLSearchParams(window.location.search)
      const redirectParam = urlParams.get('redirect')

      let targetRedirect
      if (window.location.pathname === '/user' && redirectParam) {
        // 在登录页面，使用 redirect 参数指定的目标地址
        targetRedirect = redirectParam
      } else if (window.location.pathname === '/user') {
        // 在登录页面但没有 redirect 参数，默认重定向到首页
        targetRedirect = '/'
      } else {
        // 不在登录页面，登录后返回当前页面
        targetRedirect = window.location.pathname + window.location.search
      }

      sessionStorage.setItem('oauthRedirectPath', targetRedirect)

      const params = {
        provider: provider,
      }

      // 添加验证令牌
      if (verificationToken) {
        params.verificationToken = verificationToken
      }

      // 构建请求URL - 使用Java后端OAuth端点（通过Nginx代理，使用相对路径）
      const loginUrl = `${this.$constant.baseURL}/oauth/login/${provider}?redirect=${encodeURIComponent(targetRedirect)}`

      // 记录当前登录方式
      localStorage.setItem('thirdPartyLoginProvider', provider)

      // 使用window.open打开第三方登录授权页面
      window.open(loginUrl, '_self')
    },

    // 处理第三方登录配置变更事件
    handleThirdPartyConfigChange() {
      this.loadThirdPartyLoginConfig()
    },

    // 加载第三方登录配置（优先复用 bootstrap 已写入 store 的状态，避免单独请求）
    loadThirdPartyLoginConfig() {
      const cachedStatus = this.mainStore.thirdLoginStatus
      if (cachedStatus && typeof cachedStatus === 'object') {
        this.applyThirdPartyLoginConfig(cachedStatus)
        return
      }

      // store 无缓存（首次访问且 bootstrap 未返回）时回退旧接口
      this.getThirdPartyLoginConfig().then((config) => {
        this.applyThirdPartyLoginConfig(config)
      })
    },

    // 根据状态配置提取启用的提供商列表（完全由后端 thirdLoginStatus 驱动，新增平台前端零改动）
    applyThirdPartyLoginConfig(config) {
      this.thirdPartyLoginConfig = config

      // 提取启用的第三方登录提供商列表：图标按 /static/svg/{平台标识}.svg 约定解析
      this.enabledThirdPartyProviders = []
      if (config.enable) {
        // 图标文件名与平台标识不一致的特例（twitter 的图标为 x.svg）
        const iconAlias = { twitter: 'x' }

        this.enabledThirdPartyProviders = Object.keys(config)
          .filter(
            (key) =>
              key !== 'enable' &&
              config[key] &&
              typeof config[key] === 'object' &&
              config[key].enabled === true
          )
          .map((key) => {
            const name = config[key].platformName || key
            return {
              key,
              name,
              icon: `/static/svg/${iconAlias[key] || key}.svg`,
              title: `${name}登录`,
              sortOrder: Number.isFinite(config[key].sortOrder)
                ? config[key].sortOrder
                : 99,
            }
          })
          .sort((a, b) => a.sortOrder - b.sortOrder)
      }
    },

    // 获取第三方登录配置
    getThirdPartyLoginConfig() {
      return new Promise((resolve, reject) => {
        this.$http
          .get(this.$constant.baseURL + '/webInfo/getThirdLoginStatus')
          .then((res) => {
            if (res.code === 200 && res.data) {
              resolve(res.data)
            } else {
              resolve({ enable: false })
            }
          })
          .catch((error) => {
            console.error('获取第三方登录配置失败:', error)
            resolve({ enable: false })
          })
      })
    },

    testShowCaptcha() {
      this.showCaptchaWrapper = true
    },
  },
  emits: ['refresh'],
}
</script>

<style scoped>
.in-up-container {
  /* 卡片超过视口高度（小屏 + 多第三方 + 注册面板）时容器随内容增高，
     flex 居中不再裁剪顶部，页面自然滚动，背景图（height:100%）跟随铺满 */
  min-height: 100vh;
  position: relative;
}
/* 盒状样式（卡片/终端/磨砂等）保留上下呼吸空间；全屏样式（immersive/split）不加 */
.in-up-container--boxed {
  padding: 28px 0;
  box-sizing: border-box;
}
.user-container {
  width: 100vw;
  height: 100vh;
  position: relative;
}
</style>
