<template>
  <div :class="{ 'admin-dark-mode': isAdminDark }">
    <myHeader :isAdminDark="isAdminDark" @toggle-theme="toggleAdminTheme"></myHeader>
    <sidebar
      :isAdminDark="isAdminDark"
      :isAuthReady="canRenderProtectedContent"
      :isBusy="showAuthGate || isRedirectingToLogin"
    ></sidebar>
    <div class="content-box">
      <div class="content" ref="adminContent">
        <div v-if="!showAuthGate" class="content-stage">
          <router-view></router-view>
        </div>
      </div>
      <div v-if="showAuthGate" class="auth-gate">
        <div class="auth-gate-card">
          <i class="el-icon-loading auth-gate-icon"></i>
          <div class="auth-gate-title">{{ authGateTitle }}</div>
          <div class="auth-gate-desc">{{ authGateDescription }}</div>
        </div>
      </div>
      <transition name="el-fade-in-linear">
        <div v-if="showNavigationOverlay && !showAuthGate" class="content-overlay">
          <div class="content-overlay-chip">
            <i class="el-icon-loading"></i>
            <span>{{ navigationOverlayText }}</span>
          </div>
        </div>
      </transition>
    </div>

    <!-- Dynamic Guide Island -->
    <transition name="el-fade-in-linear">
      <div v-if="showGuideWidget && canRenderProtectedContent" class="guide-island" @click="resumeGuide">
        <div class="island-content">
          <div class="island-status">
            <span class="island-dot"></span>
            <span class="island-text">配置向导 {{ guideStep }} / {{ totalGuideSteps }}</span>
          </div>
          <div class="island-title">{{ currentGuideTitle }}</div>
          <div class="island-actions" @click.stop>
            <button class="island-btn ghost" @click="goPrevStep">上一步</button>
            <button class="island-btn ghost" @click="resumeGuide">查看说明</button>
            <button class="island-btn primary" @click="goNextStep">{{ isLastStep ? '完成' : '下一步' }}</button>
          </div>
        </div>
        <div class="island-close" @click.stop="closeGuide" title="不再显示">
          <i class="el-icon-close"></i>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
  import { useMainStore } from '@/stores/main';
  import { ensureSessionValid, getSessionState } from '@/utils/sessionValidation';
  import { handleTokenExpire } from '@/utils/tokenExpireHandler';

  import myHeader from "./common/myHeader.vue";
  import sidebar from "./common/sidebar.vue";

  const STORAGE_KEY = 'poetize_guide_step';
  const GUIDE_STEPS = [
    { element: '#field-update-check', route: '/welcome', title: '🆕 Step 1: 版本与更新', description: '欢迎页右下角显示当前项目的版本。点击 <b>检查更新</b> 可检查是否有新版本；发现更新后，按钮会变成 <b>去更新</b>，点击后跳转到 GitHub Releases 查看升级说明。', side: 'left', align: 'end' },
    { element: '#menu-webEdit', route: '/webEdit', title: '🌐 Step 2: 基础设置', description: '配置 <b>站点基础信息</b>（包括网站名称、标题、地址、头像、背景图、页脚），让网站告别默认空白状态。配置完成后点击灵动胶囊的下一步继续配置。' },
    { element: '#menu-webAppearance', route: '/webAppearance', title: '🎨 Step 3: 外观个性化', description: '配置 <b>外观与个性化</b>（看板娘、AI聊天助手、鼠标点击效果、自动夜间模式、灰色模式等），让你的网站更有个性。' },
    { element: '#menu-pluginManager', route: '/pluginManager', title: '🔌 Step 4: 插件管理', description: '想要更深度的个性化？在 <b>插件管理</b> 中，你可以定制 <b>鼠标点击效果</b>、<b>全屏飘落特效</b>、<b>看板娘模型</b>、<b>文章编辑器</b>、<b>文章阅读主题</b>，还能开启 <b>文章付费</b> 功能实现内容变现。支持安装第三方插件包，打造独一无二的网站体验。' },
    { element: '#menu-configList', route: '/configList', title: '🔧 Step 5: 配置管理', description: '查找并填写 <b>备案号</b>（beian、policeBeian）。中国大陆服务器必填（港澳台以及海外服务器可跳过）。' },
    { element: '#menu-configList', route: '/configList', title: '🗂️ Step 6: 存储与图床', description: '这是可选但常用的配置。搜索 <code>store.type</code> 来选择默认存储平台：<b>local</b> 适合本地或 Docker 挂载，<b>qiniu</b> 适合七牛云对象存储/CDN，<b>lsky</b> 适合兰空图床，<b>easyimage</b> 适合简单图床。使用第三方平台时，需要开启对应的 <code>qiniu.enable</code>、<code>lsky.enable</code> 或 <code>easyimage.enable</code>，并填写域名、API地址、Token、Bucket 等必要参数。' },
    { element: '#menu-configList', route: '/configList', title: '🖼️ Step 7: 图片优化与位置服务', description: '这是可选配置。搜索 <code>image.webp.enabled</code> 和 <code>image.compress.enabled</code>，决定是否启用图片上传时的 WebP 转换和压缩；还可以根据画质需求和服务器性能调整 <code>image.webp.min-size</code>、<code>image.webp.min-saving-ratio</code> 和 <code>image.compress.mode</code>。若评论区需要更精准的地理位置解析，搜索并填写 <code>tencent.lbs.key</code>（腾讯位置服务 Key），如果不填，则默认使用系统内置的 IP 库进行解析。' },
    { element: '#menu-webNotice', route: '/webNotice', title: '📧 Step 8: 通知与邮件', description: '进入 <b>通知与邮件</b> 页面，配置 <b>SMTP邮件服务</b> 和 <b>站点公告</b>。请至少配一个邮箱并测试通过。' },
    { element: '#menu-configList', route: '/configList', title: '✉️ Step 9: 邮件模板', description: '回到 <b>配置管理</b> 页面，搜索 <code>user.code.format</code> 和 <code>user.subscribe.format</code>，自定义 <b>邮箱验证码模板</b> 和 <b>邮箱订阅模板</b> 的正文内容，让通知邮件更具个性化。' },
    { element: '#menu-webSecurity', route: '/webSecurity', title: '🧩 Step 10: 安全与登录', description: '进入 <b>安全与登录</b> 页面，配置 <b>验证码防护</b> 和 <b>第三方登录</b>（GitHub、Gitee等），完善站点安全和用户体验。' },
    { element: '#field-api', route: '/webNavApi', title: '🛰️ Step 11: 对外 API 接口', description: '这里可以开启网站的 <b>对外 API 接口</b>。它主要给 <b>服务端集成</b> 使用，比如接到 <b>OpenClaw</b> 这类 <b>self-hosted AI agent gateway</b>，或者接入你自己的自动发布、内容运营工作流。启用后，可以通过 <code>X-API-KEY</code> 调用文章创建、更新、SEO、资源上传等能力。如果你平时只在后台手工发文，可以先不启用；如果要接服务端自动化，建议开启后立刻配置 <b>API IP 白名单</b>，只放行你的 OpenClaw 服务器或可信出口 IP。' },
    { element: '#menu-userList', route: '/userList', title: '👤 Step 12: 站长个人资料修改', description: '点击修改个人信息按钮，<b>修改个人信息</b>（头像、昵称、个人简介等），这些会作为站长资料在前台中展示。' },
    { element: '#menu-seoConfig', route: '/seoConfig', title: '🔍 Step 13: SEO全套配置', description: '这里是网站被搜索引擎收录的前提：开启SEO → 填写描述/关键词 → 一键生成图标 → 搜索引擎验证推送 → sitemap生成。' },
    { element: '#menu-resourcePathList', route: '/resourcePathList', title: '📦 Step 14: 本站信息 (siteInfo)', description: '在这里找到或新建 <code>siteInfo</code> 类型，配置你的网站名称、地址、描述和封面。它是友链交换页展示的本站专属“社交名片”。' },
    { element: '#menu-resourcePathList', route: '/resourcePathList', title: '🎯 Step 15: 侧边栏定制', description: '继续在 <b>资源聚合</b> 页面，点击新增资源聚合按钮，可以配置 <b>联系方式</b>（社交媒体小图标）、<b>快捷入口</b>（自定义跳转按钮）、<b>侧边栏背景</b>（渐变或图片），这些会展示在首页 PC端右侧侧边栏中。点击类型旁的问号图标可查看效果示例。' },
    { element: '#menu-translationModel', route: '/translationModel', title: '🤖 Step 16: 文章AI助手', description: '在这里选择合适的 <b>LLM 提供商</b>，填入 API Key，就能一键开启文章的自动翻译和智能摘要功能啦。' },
    { element: '#menu-postEdit', route: '/postEdit', title: '✍️ Step 17: 开启创作之旅', description: '最后一步！进入 <b>写文章</b> 页面。您可以在文章信息的左下角找到 <b>默认配置的设置按钮（齿轮图标）</b>，在此统一保存你常用的默认操作规则（如自动生成摘要等）。现在，开始您的创作之旅吧！配置至此全部完成！' }
  ];

  export default {
    components: {
      myHeader,
      sidebar
    },

    data() {
      return {
        isAdminDark: false,
        guideStep: 0,
        showGuideWidget: false,
        driverObj: null,
        driverFactory: null,
        driverLoaderPromise: null,
        isRedirectingToLogin: false,
        routePrefetchStarted: false,
      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      },
      sessionState() {
        return getSessionState();
      },
      authStatus() {
        return this.sessionState.status;
      },
      hasVerifiedSession() {
        return this.sessionState.verifiedAt > 0;
      },
      canRenderProtectedContent() {
        return this.hasVerifiedSession && !this.isRedirectingToLogin && this.authStatus !== 'invalid';
      },
      showAuthGate() {
        return !this.canRenderProtectedContent;
      },
      showNavigationOverlay() {
        return this.canRenderProtectedContent && (
          this.sessionState.navigationPending ||
          this.sessionState.contentLoadingCount > 0 ||
          this.authStatus === 'validating'
        );
      },
      authGateTitle() {
        if (this.isRedirectingToLogin || this.authStatus === 'invalid') {
          return '登录状态已失效';
        }
        if (this.authStatus === 'validating') {
          return '正在验证后台登录状态';
        }
        return '正在初始化后台';
      },
      authGateDescription() {
        if (this.isRedirectingToLogin || this.authStatus === 'invalid') {
          return '正在返回登录页，请稍候。';
        }
        return '验证完成前不会渲染后台页面内容。';
      },
      navigationOverlayText() {
        if (this.authStatus === 'validating') {
          return '正在重新验证登录状态...';
        }
        return '页面加载中...';
      },
      totalGuideSteps() {
        return GUIDE_STEPS.length;
      },
      currentGuideTitle() {
        if (this.guideStep === 0) {
          return '认识灵动胶囊';
        }
        if (this.guideStep < 0 || this.guideStep > GUIDE_STEPS.length) {
          return '点击继续开始配置';
        }
        const rawTitle = GUIDE_STEPS[this.guideStep - 1].title || '';
        return rawTitle.replace(/^.*?:\s*/, '');
      },
      isLastStep() {
        return this.guideStep >= GUIDE_STEPS.length;
      }
    },

    watch: {
      '$route'(to, from) {
        this.resetContentScroll();

        if (to.query.focus) {
          this.scrollToFocus(to.query.focus);
        }

        this.ensureAdminSession();
      }
    },

    created() {
      // 延迟获取系统配置，确保mainStore已经初始化
      this.$nextTick(() => {
        let sysConfig = this.mainStore.sysConfig;
        if (!this.$common.isEmpty(sysConfig) && !this.$common.isEmpty(sysConfig['webStaticResourcePrefix'])) {
          let root = document.querySelector(":root");
          let webStaticResourcePrefix = sysConfig['webStaticResourcePrefix'];
          root.style.setProperty("--backgroundPicture", "url(" + webStaticResourcePrefix + "assets/backgroundPicture.jpg)");
        }
        this.getWebsitConfig();
        this.ensureAdminSession({ force: true });
      });
    },

    mounted() {
      
      // 初始化后台暗色模式
      this.initAdminTheme();
      
      // 监听系统暗色模式变化
      this.setupAdminThemeListener();
      this.initGuide();
      
      // Listen for start guide event from welcome page or others
      this.$root.$on('start-guide', (step) => this.startGuide(step));
    },
    
    beforeDestroy() {
      this.$root.$off('start-guide');
      if (this.driverObj) {
        this.driverObj.destroy();
      }
    },

    methods: {
      resetContentScroll() {
        this.$nextTick(() => {
          const content = this.$refs.adminContent;
          if (content) {
            content.scrollTop = 0;
          }
        });
      },

      scrollToFocus(id) {
        const attemptScroll = (retryCount = 0) => {
          const el = document.getElementById(id);
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            el.classList.add('search-focus-highlight');
            setTimeout(() => {
              el.classList.remove('search-focus-highlight');
            }, 2000);
          } else if (retryCount < 10) {
            setTimeout(() => attemptScroll(retryCount + 1), 200);
          }
        };
        // Small delay to ensure route component has mounted
        setTimeout(() => attemptScroll(), 100);
      },
      runWhenIdle(callback, options = {}) {
        const { delay = 0, timeout = 1500 } = options;

        const invoke = () => {
          if (typeof window !== 'undefined' && typeof window.requestIdleCallback === 'function') {
            window.requestIdleCallback(() => callback(), { timeout });
          } else {
            window.setTimeout(callback, timeout);
          }
        };

        if (delay > 0) {
          window.setTimeout(invoke, delay);
        } else {
          invoke();
        }
      },
      scheduleRoutePrefetch() {
        if (this.routePrefetchStarted) {
          return;
        }

        this.routePrefetchStarted = true;
        const loaders = [
          () => import('./welcome'),
          () => import('./main'),
          () => import('./webEdit'),
          () => import('./webAppearance'),
          () => import('./postList'),
          () => import('./seoConfig')
        ];

        const preloadNext = (index) => {
          if (index >= loaders.length) {
            return;
          }

          loaders[index]().catch(() => {
            // 预取失败不影响正常导航，忽略即可。
          }).finally(() => {
            this.runWhenIdle(() => preloadNext(index + 1), { delay: 250, timeout: 1800 });
          });
        };

        this.runWhenIdle(() => preloadNext(0), { delay: 1200, timeout: 2200 });
      },
      ensureDriverLoaded() {
        if (this.driverFactory) {
          return Promise.resolve(this.driverFactory);
        }

        if (!this.driverLoaderPromise) {
          this.driverLoaderPromise = Promise.all([
            import('driver.js'),
            import('driver.js/dist/driver.css')
          ]).then(([module]) => {
            this.driverFactory = module.driver;
            return this.driverFactory;
          }).finally(() => {
            this.driverLoaderPromise = null;
          });
        }

        return this.driverLoaderPromise;
      },

      // 初始化后台主题
      initAdminTheme() {
        try {
          
          // 使用与前台共享的theme键
          const theme = localStorage.getItem('theme');
          
          if (theme === 'dark') {
            this.isAdminDark = true;
          } else if (theme === 'light') {
            this.isAdminDark = false;
          } else {
            // 用户未设置，检查系统偏好
            const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
            this.isAdminDark = prefersDark;
          }
          
          // 统一应用主题：在 body 上添加/移除 dark-mode 类（与前台保持一致）
          this.applyThemeToBody();
          
          
          // 触发全局事件，通知所有组件当前主题
          this.$nextTick(() => {
            this.$root.$emit('theme-changed', this.isAdminDark);
          });
        } catch (error) {
          console.error('初始化后台主题失败:', error);
        }
      },
      
      // 监听系统暗色模式变化（与前台共享）
      setupAdminThemeListener() {
        if (!window.matchMedia) return;
        
        const darkModeQuery = window.matchMedia('(prefers-color-scheme: dark)');
        
        const handleThemeChange = (e) => {
          // 使用与前台共享的theme键
          const theme = localStorage.getItem('theme');
          
          // 只有在用户未手动设置时才自动切换
          if (!theme) {
            this.isAdminDark = e.matches;
            // 统一应用主题到 body
            this.applyThemeToBody();
            // 触发全局事件
            this.$root.$emit('theme-changed', this.isAdminDark);
          }
        };
        
        if (darkModeQuery.addEventListener) {
          darkModeQuery.addEventListener('change', handleThemeChange);
        } else if (darkModeQuery.addListener) {
          darkModeQuery.addListener(handleThemeChange);
        }
      },
      
      // 切换后台主题（与前台共享）
      toggleAdminTheme() {
        this.isAdminDark = !this.isAdminDark;
        
        // 保存到与前台共享的theme键
        localStorage.setItem('theme', this.isAdminDark ? 'dark' : 'light');
        
        // 统一应用主题到 body（与前台保持一致）
        this.applyThemeToBody();
        
        
        // 触发全局事件，通知所有组件主题已切换
        this.$root.$emit('theme-changed', this.isAdminDark);
      },
      
      // 应用主题到 body（统一前台和后台的实现）
      applyThemeToBody() {
        if (this.isAdminDark) {
          // 暗色模式：添加 dark-mode 类到 body 和 html（与前台一致）
          document.body.classList.add('dark-mode');
          document.documentElement.classList.add('dark-mode');
        } else {
          // 亮色模式：移除 dark-mode 类（与前台一致）
          document.body.classList.remove('dark-mode');
          document.documentElement.classList.remove('dark-mode');
        }
      },
      
            // --- Guide Tour Methods ---
      initGuide() {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved !== null) {
          const val = parseInt(saved, 10);
          // val 0 = island intro, val 1-N = actual steps
          if (val >= 0 && val <= GUIDE_STEPS.length) {
            this.guideStep = val;
            this.showGuideWidget = true;
          }
        }
      },
      
      startGuide(stepIndex) {
        // Handle undefined or event object passed
        if (typeof stepIndex !== 'number') stepIndex = 0;

        if (this.driverObj) {
          try { this.driverObj.destroy(); } catch (e) {}
          this.driverObj = null;
        }
        
        if (stepIndex < 0) return;
        if (stepIndex > GUIDE_STEPS.length) {
          this.finishGuide();
          return;
        }

        // Step 0: Introduction to Dynamic Island, stay on welcome page
        if (stepIndex === 0) {
          this.guideStep = 0;
          this.showGuideWidget = true;
          localStorage.setItem(STORAGE_KEY, '0');
          // Make sure we're on welcome page
          if (this.$route.path !== '/welcome') {
            this.$router.push('/welcome').catch(() => {});
          }
          // Show island tutorial after a short delay
          setTimeout(() => {
            this.showIslandTutorial();
          }, 300);
          return;
        }

        // Step 1-N: Actual configuration steps
        this.guideStep = stepIndex;
        this.showGuideWidget = true;
        localStorage.setItem(STORAGE_KEY, String(this.guideStep));

        const stepConfig = GUIDE_STEPS[stepIndex - 1];
        
        // Navigate if needed
        let targetPath = stepConfig.route;
        if (!targetPath.startsWith('/')) targetPath = '/' + targetPath;

        if (this.$route.path !== targetPath) {
           this.$router.push(targetPath).catch(() => {});
        }

        // Wait for DOM
        setTimeout(() => {
          this.runDriverStep(stepIndex - 1);
        }, 800);
      },

      resumeGuide() {
        // guideStep 0 = island intro, guideStep 1-N = actual steps
        this.startGuide(this.guideStep);
      },

      goPrevStep() {
        // guideStep 0 = intro, guideStep 1-N = actual steps
        if (this.guideStep <= 0) {
          this.closeGuide();
          return;
        }
        this.startGuide(this.guideStep - 1);
      },

      goNextStep() {
        if (this.isLastStep) {
          this.finishGuide();
        } else {
          // guideStep 0 → start step 1, guideStep N → start step N+1
          this.startGuide(this.guideStep + 1);
        }
      },

      runDriverStep(index) {
        const step = GUIDE_STEPS[index];
        
        // Ensure driver instance is clean
        if (this.driverObj) {
          try { this.driverObj.destroy(); } catch (e) {}
        }

        const targetEl = document.querySelector(step.element);
        if (targetEl) {
          const parentSubmenu = targetEl.closest('.el-submenu');
          if (parentSubmenu && !parentSubmenu.classList.contains('is-opened')) {
            const submenuTitle = parentSubmenu.querySelector('.el-submenu__title');
            if (submenuTitle) {
              submenuTitle.click();
            }
            setTimeout(() => {
              this.scrollSidebarAndShowDriver(step, index);
            }, 260);
            return;
          }
        }

        this.scrollSidebarAndShowDriver(step, index);
      },

      scrollSidebarAndShowDriver(step, index) {
        const targetEl = document.querySelector(step.element);
        
        // Element not found retry logic
        if (!targetEl) {
          setTimeout(() => {
            const retryEl = document.querySelector(step.element);
            if (retryEl) {
              this.scrollSidebarAndShowDriver(step, index);
            } else {
              // Try expanding parent menu anyway just in case
              const sidebar = document.querySelector('.sidebar');
              if (sidebar) {
                const submenus = sidebar.querySelectorAll('.el-submenu__title');
                submenus.forEach(el => {
                   if (el.textContent.includes('网站设置')) el.click();
                });
                setTimeout(() => {
                   this.showDriverPopover(step, index);
                }, 500);
              } else {
                this.showDriverPopover(step, index);
              }
            }
          }, 300);
          return;
        }

        // Just use native scrollIntoView before Driver starts
        // This is robust and simple
        targetEl.scrollIntoView({ block: 'center', behavior: 'auto' });

        // Wait a tick for layout to settle, then start driver
        setTimeout(() => {
          this.showDriverPopover(step, index);
        }, 100);
      },

      async showDriverPopover(step, index) {
        const driver = await this.ensureDriverLoaded();
        const self = this;
        
        // Configurable hook to force scroll again after driver highlight
        const onHighlightStarted = (element) => {
            if (element) {
               element.scrollIntoView({ block: 'center', behavior: 'auto' });
            }
        };

        this.driverObj = driver({
          showProgress: false,
          animate: true, 
          // allowClose: true, // v1.x often uses allowClose in step or global?
          overlayColor: 'rgba(0,0,0,0.6)',
          popoverClass: 'elegant-driver-popover',
          steps: [
             {
               element: step.element,
               popover: {
                 title: step.title,
                 description: step.description,
                 side: step.side || 'right',
                 align: step.align || 'start',
                 showButtons: ['next', 'close'],
                 nextBtnText: '开始配置 →',
                 doneBtnText: '开始配置 →'
               },
               onHighlightStarted: onHighlightStarted
             }
          ],
          onNextClick: () => {
             self.driverObj.destroy();
          },
          onCloseClick: () => {
             self.driverObj.destroy();
          }
        });
        
        this.driverObj.drive();

        // Safety enforcement of scroll position
        const enforceScroll = () => {
           const el = document.querySelector(step.element);
           if (el) el.scrollIntoView({ block: 'center', behavior: 'auto' });
        };
        
        requestAnimationFrame(enforceScroll);
        setTimeout(enforceScroll, 50);
        setTimeout(enforceScroll, 200);

        this.$nextTick(() => {
          setTimeout(() => {
            this.injectDriverFooterControls(index);
          }, 0);
        });
      },

      injectDriverFooterControls(index) {
        const footer = document.querySelector('.driver-popover-footer');
        if (!footer) return;

        const oldCustom = footer.querySelector('.driver-custom-prev-btn');
        if (oldCustom) {
          oldCustom.remove();
        }
        const oldProgress = footer.querySelector('.driver-custom-progress');
        if (oldProgress) {
          oldProgress.remove();
        }

        const nextBtn = footer.querySelector('.driver-popover-next-btn') ||
          footer.querySelector('button:not(.driver-popover-close-btn)');
        if (!nextBtn) return;

        const prevBtn = document.createElement('button');
        prevBtn.type = 'button';
        prevBtn.className = 'driver-custom-prev-btn';
        prevBtn.textContent = '← 上一步';

        const progress = document.createElement('span');
        progress.className = 'driver-custom-progress';
        progress.textContent = `${index + 1} of ${GUIDE_STEPS.length}`;

        prevBtn.addEventListener('click', (event) => {
          event.preventDefault();
          event.stopPropagation();

          if (this.driverObj) {
            this.driverObj.destroy();
          }

          // index 0 → go back to island intro (guideStep 0)
          // index N → go back to step N (guideStep N)
          this.startGuide(index);
        });

        footer.insertBefore(prevBtn, nextBtn);
        footer.insertBefore(progress, nextBtn);
      },

      async showIslandTutorial() {
        const driver = await this.ensureDriverLoaded();
        const self = this;
        // Show a tutorial pointing to the dynamic island
        const tutorialDriver = driver({
          showProgress: false,
          animate: true,
          allowClose: true,
          overlayColor: 'rgba(0,0,0,0.6)',
          popoverClass: 'elegant-driver-popover',
          steps: [
            {
              element: '.guide-island',
              popover: {
                title: '💡 灵动胶囊',
                description: '这是你的<b>配置引导助手</b>！<br><br>👉 配置完成后，点击 <b>「下一步」</b> 继续配置引导<br>👉 忘记配置什么？点击 <b>「查看说明」</b> 重新显示提示<br>👉 想退出？点击右上角 ✕ 即可',
                side: 'bottom',
                align: 'center',
                showButtons: ['next', 'close'],
                nextBtnText: '知道了，开始配置 →',
                doneBtnText: '完成'
              }
            }
          ],
          onNextClick: () => {
            tutorialDriver.destroy();
            // Start the actual Step 1
            self.startGuide(1);
          },
          onCloseClick: () => {
            tutorialDriver.destroy();
            // Keep island visible so user can click "下一步" to proceed
          }
        });
        tutorialDriver.drive();
      },

      finishGuide() {
        this.guideStep = 0;
        this.showGuideWidget = false;
        localStorage.removeItem(STORAGE_KEY);
        this.$message.success('配置向导已全部完成！');
        if (this.$route.path !== '/welcome') {
          this.$router.push('/welcome');
        }
      },

      closeGuide() {
        this.$confirm('关闭后将不再显示引导向导，确定要关闭吗？', '提示', {
          confirmButtonText: '确定关闭',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.showGuideWidget = false;
          localStorage.removeItem(STORAGE_KEY);
          this.$message({ type: 'success', message: '已关闭引导向导' });
        }).catch(() => {
          // 取消操作，不做任何处理
        });
      },

      getWebsitConfig() {
        // 获取网站配置信息
        this.getWebInfo();
        this.getSysConfig();
      },
      
      getWebInfo() {
        this.$http.get(this.$constant.baseURL + "/webInfo/getWebInfo")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.mainStore.loadWebInfo( res.data);
            }
          })
          .catch((error) => {
            console.error("获取网站信息失败:", error);
          });
      },
      
      getSysConfig() {
        this.$http.get(this.$constant.baseURL + "/sysConfig/listSysConfig")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.mainStore.loadSysConfig( res.data);
            }
          })
          .catch((error) => {
            console.error("获取系统配置失败:", error);
          });
      },
      async ensureAdminSession(options = {}) {
        const { force = false } = options;

        if (this.isRedirectingToLogin) {
          return false;
        }

        const sessionValid = await ensureSessionValid({ force });
        if (!sessionValid && !this.isRedirectingToLogin) {
          this.isRedirectingToLogin = true;
          handleTokenExpire(true, this.$route.fullPath, { showMessage: false });
        }

        if (sessionValid) {
          this.scheduleRoutePrefetch();
        }

        return sessionValid;
      },
      loadFont() {
        
      }
    }
  }
</script>

<style scoped>

  .content-box {
    position: absolute;
    left: 130px;
    right: 0;
    top: 70px;
    bottom: 0;
    transition: left .3s ease-in-out;
  }

  .content {
    position: relative;
    width: auto;
    height: 100%;
    padding: 30px;
    overflow-y: scroll;
    background-color: #f5f7fa; /* 亮色模式默认背景色 */
    transition: background-color 0.3s ease;
  }
  
  /* ========== 后台深色模式样式 ========== */
  .admin-dark-mode .content {
    background-color: #1e1e1e;
  }

  .content-stage {
    position: relative;
    min-height: 100%;
  }

  .auth-gate,
  .content-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .auth-gate {
    z-index: 2;
  }

  .auth-gate-card,
  .content-overlay-chip {
    display: inline-flex;
    align-items: center;
    gap: 12px;
    color: #334155;
    background: rgba(255, 255, 255, 0.92);
    border: 1px solid rgba(148, 163, 184, 0.25);
    box-shadow: 0 16px 36px rgba(15, 23, 42, 0.12);
    backdrop-filter: blur(10px);
  }

  .auth-gate-card {
    min-width: 300px;
    max-width: 420px;
    padding: 22px 24px;
    border-radius: 18px;
    flex-direction: column;
    text-align: center;
  }

  .auth-gate-icon {
    font-size: 28px;
    color: #409EFF;
  }

  .auth-gate-title {
    font-size: 18px;
    font-weight: 600;
    color: #1e293b;
  }

  .auth-gate-desc {
    font-size: 13px;
    line-height: 1.6;
    color: #64748b;
  }

  .content-overlay {
    z-index: 3;
    background: rgba(245, 247, 250, 0.55);
  }

  .content-overlay-chip {
    padding: 10px 16px;
    border-radius: 999px;
    font-size: 13px;
    font-weight: 600;
  }

  .admin-dark-mode .auth-gate-card,
  .admin-dark-mode .content-overlay-chip {
    color: #e2e8f0;
    background: rgba(30, 41, 59, 0.92);
    border-color: rgba(148, 163, 184, 0.18);
    box-shadow: 0 18px 40px rgba(2, 6, 23, 0.35);
  }

  .admin-dark-mode .auth-gate-title {
    color: #f8fafc;
  }

  .admin-dark-mode .auth-gate-desc {
    color: #cbd5e1;
  }

  .admin-dark-mode .content-overlay {
    background: rgba(15, 23, 42, 0.52);
  }

  /* ========== Guide Dynamic Island ========== */
  .guide-island {
    position: fixed;
    bottom: 30px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 9999;
    display: flex;
    align-items: center;
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    padding: 10px 16px;
    border-radius: 999px;
    box-shadow: 0 10px 36px rgba(0, 0, 0, 0.14);
    border: 1px solid rgba(255, 255, 255, 0.5);
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    cursor: pointer;
  }

  .admin-dark-mode .guide-island {
    background: rgba(40, 40, 40, 0.85);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  }

  .guide-island:hover {
    transform: translateX(-50%) translateY(-2px);
    box-shadow: 0 14px 44px rgba(0, 0, 0, 0.18);
  }

  .island-content {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-right: 12px;
  }

  .island-status {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }
  
  .admin-dark-mode .island-status {
    color: #eee;
  }

  .island-dot {
    width: 8px;
    height: 8px;
    background-color: #409EFF;
    border-radius: 50%;
    animation: pulse 2s infinite;
  }

  .island-title {
    font-size: 13px;
    color: #1f2937;
    font-weight: 600;
    display: flex;
    align-items: center;
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .admin-dark-mode .island-title {
    color: #f3f4f6;
  }

  .island-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .island-btn {
    border: 1px solid rgba(64, 158, 255, 0.3);
    background: transparent;
    color: #409EFF;
    border-radius: 999px;
    padding: 4px 10px;
    font-size: 12px;
    line-height: 1.2;
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .island-btn.primary {
    background: #409EFF;
    color: #fff;
  }

  .island-btn:hover {
    border-color: #409EFF;
    transform: translateY(-1px);
  }

  .island-btn.primary:hover {
    background: #66b1ff;
  }

  .admin-dark-mode .island-btn.ghost {
    border-color: rgba(147, 197, 253, 0.45);
    color: #93c5fd;
  }

  .island-close {
    border-left: 1px solid #ddd;
    padding-left: 12px;
    color: #999;
    transition: color 0.2s;
    display: flex;
    align-items: center;
  }
  
  .admin-dark-mode .island-close {
    border-left-color: #555;
    color: #777;
  }

  .island-close:hover {
    color: #f56c6c;
  }

  @keyframes pulse {
    0% { box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.4); }
    70% { box-shadow: 0 0 0 6px rgba(64, 158, 255, 0); }
    100% { box-shadow: 0 0 0 0 rgba(64, 158, 255, 0); }
  }

</style>

<style>
/* Global styles for Driver.js Popover using UI/UX Pro Max Principles */
.elegant-driver-popover {
  background: rgba(255, 255, 255, 0.95) !important;
  backdrop-filter: blur(12px) !important;
  border-radius: 12px !important;
  border: 1px solid rgba(15, 23, 42, 0.08) !important;
  box-shadow: 0 12px 20px -4px rgba(0, 0, 0, 0.08), 0 4px 8px -4px rgba(0, 0, 0, 0.06) !important;
  padding: 1rem 1.25rem !important;
  max-width: 320px !important;
  color: #0F172A !important;
  font-family: inherit !important;
}

div.driver-popover-title {
  font-weight: 600 !important;
  font-size: 0.95rem !important;
  margin-bottom: 0.35rem !important;
  color: #0F172A !important;
  display: flex !important;
  align-items: center !important;
}

div.driver-popover-description {
  color: #475569 !important;
  font-size: 0.85rem !important;
  line-height: 1.5 !important;
  margin-bottom: 0.75rem !important;
}

.elegant-driver-popover .driver-popover-progress-text {
  display: none !important;
}

.elegant-driver-popover .driver-popover-footer button {
  background: #0F172A !important;
  color: #ffffff !important;
  text-shadow: none !important;
  border: none !important;
  border-radius: 8px !important;
  padding: 0.5rem 1rem !important;
  font-size: 0.85rem !important;
  font-weight: 500 !important;
  cursor: pointer !important;
  transition: all 0.2s ease !important;
}

.elegant-driver-popover .driver-popover-footer button:hover {
  background: #1E293B !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1) !important;
}

.elegant-driver-popover .driver-popover-footer .driver-popover-prev-btn {
  background: transparent !important;
  color: #64748B !important;
  border: 1px solid rgba(15, 23, 42, 0.15) !important;
}

.elegant-driver-popover .driver-popover-footer .driver-popover-prev-btn:hover {
  background: rgba(15, 23, 42, 0.04) !important;
  color: #0F172A !important;
  border-color: rgba(15, 23, 42, 0.3) !important;
  box-shadow: none !important;
  transform: none !important;
}

.elegant-driver-popover .driver-popover-footer .driver-custom-prev-btn {
  background: transparent !important;
  color: #64748B !important;
  border: 1px solid rgba(15, 23, 42, 0.15) !important;
}

.elegant-driver-popover .driver-popover-footer .driver-custom-prev-btn:hover {
  background: rgba(15, 23, 42, 0.04) !important;
  color: #0F172A !important;
  border-color: rgba(15, 23, 42, 0.3) !important;
  box-shadow: none !important;
  transform: none !important;
}

.elegant-driver-popover .driver-popover-footer .driver-custom-progress {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 58px;
  font-size: 12px;
  font-weight: 600;
  color: #64748B;
  margin: 0 8px;
}

.elegant-driver-popover .driver-popover-footer .driver-popover-close-btn {
  background: transparent !important;
  color: #64748B !important;
}

.elegant-driver-popover .driver-popover-footer .driver-popover-close-btn:hover {
  background: rgba(15, 23, 42, 0.05) !important;
  color: #0F172A !important;
  box-shadow: none !important;
}

/** 
 * IMPORTANT: Driver.js creates arrow using 4 borders. 
 * We must override border-color based on placement.
 * Driver.js auto-updates these classes.
 */
.driver-popover-arrow-side-left {
  border-left-color: rgba(255, 255, 255, 0.95) !important;
}
.driver-popover-arrow-side-right {
  border-right-color: rgba(255, 255, 255, 0.95) !important;
}
.driver-popover-arrow-side-top {
  border-top-color: rgba(255, 255, 255, 0.95) !important;
}
.driver-popover-arrow-side-bottom {
  border-bottom-color: rgba(255, 255, 255, 0.95) !important;
}

@keyframes highlight-flash {
  0% { background-color: rgba(64, 158, 255, 0.2); box-shadow: 0 0 10px rgba(64, 158, 255, 0.3); }
  50% { background-color: rgba(64, 158, 255, 0.1); box-shadow: 0 0 5px rgba(64, 158, 255, 0.1); }
  100% { background-color: transparent; box-shadow: none; }
}

.search-focus-highlight {
  animation: highlight-flash 2.5s ease-out forwards;
  border-radius: 4px;
}
</style>
