<template>
  <div>
    <!-- el过渡动画 -->
    <transition name="el-fade-in-linear">
      <!-- 导航栏 -->
      <div
        v-show="toolbar.visible || $common.mobile() || mobile"
        @mouseenter="hoverEnter = true"
        @mouseleave="hoverEnter = false"
        :class="[
          { enter: toolbar.enter },
          {
            hoverEnter:
              (hoverEnter ||
                this.$route.path === '/favorite' ||
                this.$route.path === '/friends' ||
                this.$route.path === '/music' ||
                this.$route.path === '/favorites' ||
                this.$route.path === '/travel' ||
                this.$route.path === '/privacy') &&
              !toolbar.enter,
          },
        ]"
        class="toolbar-content myBetween"
      >
        <!-- 网站名称（配置了Logo图片时优先显示Logo） -->
        <div class="toolbar-title">
          <img
            v-if="mainStore.webInfo.logoImage"
            class="toolbar-logo"
            :src="mainStore.webInfo.logoImage"
            :alt="mainStore.webInfo.webName"
            :title="mainStore.webInfo.webName"
            @click="$router.push({ path: '/' })"
          >
          <h2 v-else @click="$router.push({ path: '/' })">
            {{ mainStore.webInfo.webName }}
          </h2>
        </div>

        <!-- 手机导航按钮 -->
        <div
          v-if="$common.mobile() || mobile"
          class="toolbar-mobile-menu"
          @click="toolbarDrawer = !toolbarDrawer"
          :class="{ enter: toolbar.enter }"
        >
          <i class="fa fa-bars" aria-hidden="true"></i>
        </div>

        <!-- 导航列表 -->
        <div v-else>
          <ul class="scroll-menu">
            <!-- 遍历导航项并按配置顺序显示 -->
            <template v-for="(item, index) in orderedNavItems">
              <!-- 分类下拉菜单 -->
              <el-dropdown
                v-if="item.type === 'dropdown'"
                :hide-timeout="500"
                placement="bottom"
                :popper-options="{ modifiers: [{ name: 'offset', options: { offset: [0, -7] } }] }"
              >
                <li>
                  <div class="my-menu">
                    {{ item.icon }} <span>{{ item.name }}</span>
                  </div>
                </li>
                <template v-slot:dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="(sort, sortIndex) in sortInfo"
                      :key="sortIndex"
                      @click="$router.push('/sort/' + sort.id)"
                    >
                      <div
                        draggable="true"
                        style="width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;"
                        @dragstart="
                          handleNavDragStart(
                            $event,
                            '/sort/' + sort.id,
                            sort.sortName
                          )
                        "
                      >
                        {{ sort.sortName }}
                      </div>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>

              <!-- 特殊导航项（如联系我） -->
              <li
                v-else-if="item.type === 'special' && item.link === '#chat'"
                @click="goIm()"
              >
                <div class="my-menu">
                  {{ item.icon }} <span>{{ item.name }}</span>
                </div>
              </li>

              <!-- 首页 -->
              <li
                v-else-if="item.link === '/'"
                draggable="true"
                @dragstart="handleNavDragStart($event, item.link, item.name)"
                @click="goHome()"
              >
                <div class="my-menu">
                  {{ item.icon }} <span>{{ item.name }}</span>
                </div>
              </li>

              <!-- 其他内部链接导航项 -->
              <li
                v-else
                draggable="true"
                @dragstart="handleNavDragStart($event, item.link, item.name)"
                @click="$router.push({ path: item.link })"
              >
                <div class="my-menu">
                  {{ item.icon }} <span>{{ item.name }}</span>
                </div>
              </li>
            </template>

            <!-- 后台 -->
            <li
              @click="goAdmin()"
              draggable="true"
              @dragstart="handleNavDragStart($event, '/admin', '后台')"
              v-if="
                !$common.isEmpty(mainStore.currentUser) &&
                (mainStore.currentUser.userType === 0 ||
                  mainStore.currentUser.userType === 1)
              "
            >
              <div class="my-menu">💻️ <span>后台</span></div>
            </li>

            <!-- 登录/个人中心 -->
            <li>
              <!-- 未登录时显示粉色圆形登录按钮 -->
              <div
                v-if="$common.isEmpty(mainStore.currentUser)"
                class="circle-login-button"
                @click="goToLogin()"
              >
                登录
              </div>

              <!-- 已登录时显示头像和自定义下拉菜单 -->
              <div v-else class="avatar-dropdown-container">
                <el-avatar
                  class="user-avatar"
                  :size="36"
                  style="margin-top: 0.75em"
                  :src="$common.getAvatarUrl(mainStore.currentUser.avatar)"
                >
                  <img :src="$getDefaultAvatar()" />
                </el-avatar>

                <!-- 自定义下拉菜单 -->
                <div class="custom-user-menu">
                  <!-- 用户名 -->
                  <div class="user-menu-header">
                    <span class="user-menu-name">{{
                      mainStore.currentUser.username
                    }}</span>
                    <span
                      v-if="mainStore.currentUser"
                      class="user-role-badge"
                      :class="{
                        owner: mainStore.currentUser.userType === 0,
                        admin: mainStore.currentUser.userType === 1,
                      }"
                    >
                      {{
                        mainStore.currentUser.userType === 0
                          ? '站长'
                          : mainStore.currentUser.userType === 1
                          ? '管理员'
                          : '用户'
                      }}
                    </span>
                  </div>

                  <!-- 个人中心 -->
                  <div class="user-menu-item" @click="goToUserCenter()">
                    <i class="fa fa-user-circle" aria-hidden="true"></i>
                    <span>个人中心</span>
                    <i
                      class="fa fa-angle-right menu-arrow"
                      aria-hidden="true"
                    ></i>
                  </div>

                  <!-- 分割线 -->
                  <div class="user-menu-divider"></div>

                  <!-- 退出 -->
                  <div class="user-menu-item" @click="handleLogout()">
                    <i class="fa fa-sign-out" aria-hidden="true"></i>
                    <span>退出</span>
                  </div>
                </div>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </transition>

    <div id="main-container" :style="mainContainerStyle">
      <router-view></router-view>
    </div>

    <!-- 回到顶部按钮 -->
    <!--    <div href="#" class="cd-top" v-if="!$common.mobile()" @click="toTop()"></div>-->

    <teleport to="body">
      <div class="toolButton">
        <!-- 注释原因：通过CSS层叠上下文已解决article.vue中语言切换按钮的遮挡问题
                 原按钮在所有屏幕尺寸下都可用，不再需要这个备用的简化按钮 -->
        <!-- 简化语言切换按钮 - 只在文章页面且屏幕≤1050px时显示 -->
        <!-- <div class="simple-lang-switch"
                 v-if="showSimpleLangSwitch"
                 @click="handleSimpleLangSwitch()"
                 :title="getSimpleLangSwitchTitle()">
              <span class="simple-lang-text">{{ getSimpleLangDisplay() }}</span>
            </div> -->

        <!-- 目录按钮 - 只在文章页面显示 -->
        <div
          class="toc-button-container"
          v-if="showTocButton"
          @click="clickTocButton()"
        >
          <i class="fa fa-align-justify toc-button-icon" aria-hidden="true"></i>
        </div>

        <div class="backTop" v-if="toolButton" @click="toTop()">
          <!-- 回到顶部按钮 -->
          <svg viewBox="0 0 1024 1024" width="50" height="50">
            <path
              d="M696.741825 447.714002c2.717387-214.485615-173.757803-312.227566-187.33574-320.371729-10.857551 5.430775-190.050127 103.168727-187.33274 320.371729-35.297037 24.435488-73.306463 65.1623-67.875688 135.752376 5.430775 70.589076 76.018851 119.460051 103.168726 116.745664 27.152875-2.716387 19.004713-21.7221 19.004713-21.7221l8.148162-38.011425s40.721814 59.732525 51.583363 59.732525h146.609927c13.574938 0 51.585363-59.732525 51.585363-59.732525l8.147162 38.011425s-8.147162 19.005713 19.004713 21.7221c27.148876 2.714388 97.738951-46.156588 103.168727-116.745664s-32.57965-111.316888-67.876688-135.752376z m-187.33574-2.713388c-5.426776 0-70.589076-2.717387-78.733239-78.737238 2.713388-73.306463 73.306463-78.733239 78.733239-81.450626 5.430775 0 76.02385 8.144163 78.736238 81.450626-8.143163 76.019851-73.305463 78.737238-78.736238 78.737238z m0 0"
              fill="#000000"
            ></path>
            <path
              d="M423.602441 746.060699c6.47054-6.297579 12.823107-7.017417 21.629121-2.784372 34.520213 16.582259 70.232157 19.645568 107.031855 9.116944 8.118169-2.323476 15.974396-5.475765 23.598677-9.22392 13.712907-6.73648 26.003134 0.8878 26.080116 16.13936 0.109975 22.574907-0.024994 45.142816 0.080982 67.709725 0.031993 7.464316-2.277486 13.322995-9.44387 16.608254-7.277358 3.333248-13.765895 1.961558-19.526595-3.264264-3.653176-3.313253-7.063407-6.897444-10.634601-10.304675-6.563519-6.259588-6.676494-6.25259-10.625603 1.603638-8.437097 16.80121-16.821205 33.623415-25.257302 50.423625-2.489438 4.953882-5.706713 9.196925-11.411426 10.775569-8.355115 2.315478-15.772442-1.070758-20.272427-9.867774-8.774021-17.15313-17.269104-34.453228-25.918153-51.669344-3.750154-7.469315-3.9891-7.479313-10.141712-1.514658-3.715162 3.602187-7.31435 7.326347-11.142486 10.800563-5.571743 5.060858-11.934308 6.269586-18.936728 3.207277-6.82746-2.984327-9.869774-8.483086-9.892769-15.685462-0.070984-23.506697-0.041991-47.018393-0.020995-70.532089 0.007998-4.679944 1.46467-8.785018 4.803916-11.538397z"
              fill="#000000"
            ></path>
          </svg>
        </div>

        <el-popover :hide-after="500" placement="left" trigger="hover">
          <template v-slot:reference>
            <div>
              <i
                class="fa fa-cog iconRotate"
                style="color: var(--black)"
                aria-hidden="true"
              ></i>
            </div>
          </template>

          <div class="my-setting">
            <div>
              <!-- 太阳按钮 - 暗色模式时显示 -->
              <el-icon
                v-if="isDark"
                class="iconRotate"
                title="切换亮色主题"
                @click="changeColor()"
              >
                <Sunny />
              </el-icon>
              <!-- 月亮按钮 - 亮色模式时显示 -->
              <el-icon
                v-else
                title="切换暗色主题"
                @click="changeColor()"
              >
                <Moon />
              </el-icon>
            </div>
            <div>
              <!-- 鼠标点击效果切换按钮 -->
              <i
                class="fa fa-hand-pointer-o"
                aria-hidden="true"
                :title="'点击效果: ' + currentClickEffectLabel"
                @click="cycleClickEffect()"
              ></i>
            </div>
          </div>
        </el-popover>
      </div>
    </teleport>

    <!-- 图片预览 -->
    <div id="outerImg">
      <div id="innerImg" style="position: absolute">
        <img id="bigImg" :src="''" />
      </div>
    </div>

    <el-drawer v-model="toolbarDrawer"
               :show-close="false"
               size="65%"
               class="toolbarDrawer"
               direction="ltr"
               :append-to-body="true">
      <!-- 自定义头像标题 -->
      <template #header="{ close, titleId, titleClass }">
        <div v-if="showDrawerAvatar" class="drawer-avatar-container">
          <img :src="$common.getAvatarUrl(mainStore.webInfo.avatar)"
               :style="{
            width: drawerAvatarSize + 'px',
            height: drawerAvatarSize + 'px',
            borderRadius: '50%',
            objectFit: 'cover',
            cursor: 'pointer',
            transition: 'transform 0.3s'
          }"
               class="drawer-avatar"
               @error="handleAvatarError" />
          <!-- 头像模式下的分隔线 -->
          <hr :class="['drawer-divider', { 'show-snowflake': showDrawerSnowflake }]" />
        </div>
        <span v-else>{{ showDrawerTitle ? drawerTitle : '' }}</span>
      </template>
      <div>
        <ul class="small-menu">
          <!-- 遍历导航项并按配置顺序显示 -->
          <template v-for="(item, index) in orderedNavItems">
            <!-- 分类下拉菜单 -->
            <li v-if="item.type === 'dropdown'">
              <div @click="toggleSortMenu" class="sort-menu-header">
                {{ item.icon }} <span>{{ item.name }}</span>
                <i
                  class="fa fa-angle-right sort-menu-arrow"
                  aria-hidden="true"
                  :class="{ expanded: sortMenuExpanded }"
                ></i>
              </div>
              <div
                class="sort-submenu"
                :class="{ collapsed: !sortMenuExpanded }"
              >
                <div
                  v-for="(menu, menuIndex) in sortInfo"
                  :key="menuIndex"
                  class="sortMenu"
                  @click="smallMenu('/sort/' + menu.id)"
                >
                  {{ menu.sortName }}
                </div>
              </div>
            </li>

            <!-- 特殊导航项（如联系我） -->
            <li
              v-else-if="item.type === 'special' && item.link === '#chat'"
              @click="goIm()"
            >
              <div>
                {{ item.icon }} <span>{{ item.name }}</span>
              </div>
            </li>

            <!-- 首页 -->
            <li v-else-if="item.link === '/'" @click="goHomeMobile()">
              <div>
                {{ item.icon }} <span>{{ item.name }}</span>
              </div>
            </li>

            <!-- 其他内部链接导航项 -->
            <li v-else @click="smallMenu({ path: item.link })">
              <div>
                {{ item.icon }} <span>{{ item.name }}</span>
              </div>
            </li>
          </template>

          <!-- 后台 -->
          <li
            @click="goAdmin()"
            v-if="
              !$common.isEmpty(mainStore.currentUser) &&
              (mainStore.currentUser.userType === 0 ||
                mainStore.currentUser.userType === 1)
            "
          >
            <div>💻️ <span>后台</span></div>
          </li>

          <!-- 登录/个人中心 -->
          <li
            v-if="$common.isEmpty(mainStore.currentUser)"
            @click="goToLoginMobile()"
          >
            <div>
              <i class="fa fa-sign-in" aria-hidden="true"></i> <span>登录</span>
            </div>
          </li>

          <li
            v-if="!$common.isEmpty(mainStore.currentUser)"
            @click="smallMenu({ path: '/user' })"
          >
            <div>
              <i class="fa fa-user-circle" aria-hidden="true"></i>
              <span>个人中心</span>
            </div>
          </li>

          <li
            v-if="!$common.isEmpty(mainStore.currentUser)"
            @click="smallMenuLogout"
          >
            <div>
              <i class="fa fa-sign-out" aria-hidden="true"></i>
              <span>退出</span>
            </div>
          </li>
        </ul>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../utils/gogocodeTransfer'
import { useMainStore } from '@/stores/main'
import { Sunny, Moon } from '@element-plus/icons-vue'
import { applyLanguageBootstrap } from '@/utils/languageUtils'

export default {
  components: {
    Sunny,
    Moon
  },
  data() {
    return {
      toolButton: false,
      showTocButton: false, // 控制目录按钮显示
      // showSimpleLangSwitch: false, // 控制简化语言切换按钮显示（已注释，不再需要）
      currentClickEffectLabel: '无效果', // 当前点击效果名称
      hoverEnter: false,
      isDark: false,
      scrollTop: 0,
      toolbarDrawer: false,
      mobile: false,
      visitCountInterval: null,
      resizeHandler: null,
      // 移动端侧边栏配置
      drawerConfig: null,
      // 移动端侧边栏"分类"菜单展开状态（智能判断）- 初始化为 false，在 created 中设置
      sortMenuExpanded: false,
    }
  },
  mounted() {
    window.addEventListener('scroll', this.onScrollPage)
    window.addEventListener('load', this.syncFloatingUiState)
    window.addEventListener('pageshow', this.syncFloatingUiState)

    // 初始化点击效果标签
    this.updateClickEffectLabel()

    // 优先从localStorage恢复用户保存的主题
    try {
      // 清理旧的格式数据（迁移）
      if (localStorage.getItem('poetize-theme')) {
        localStorage.removeItem('poetize-theme')
      }

      const userTheme = localStorage.getItem('theme')

      if (userTheme === 'dark') {
        this.isDark = true
        this.applyDarkTheme(false) // 已经保存过了，不再重复保存
      } else if (userTheme === 'light') {
        this.isDark = false
        this.applyLightTheme(false) // 已经保存过了，不再重复保存
      } else {
        // 用户未手动设置，检查系统偏好或使用时间判断
        const prefersDark =
          window.matchMedia &&
          window.matchMedia('(prefers-color-scheme: dark)').matches

        if (prefersDark) {
          this.isDark = true
          this.applyDarkTheme(false) // 不保存，跟随系统
        } else if (this.isDaylight()) {
          // 如果系统是浅色模式，则使用原来的白天夜晚逻辑
          this.isDark = true
          this.applyDarkTheme(false) // 不保存，跟随时间
        } else {
          this.isDark = false
          this.applyLightTheme(false) // 不保存，跟随时间
        }
      }
    } catch (error) {
      // 出错时检查系统偏好或时间
      const prefersDark =
        window.matchMedia &&
        window.matchMedia('(prefers-color-scheme: dark)').matches
      if (prefersDark || this.isDaylight()) {
        this.isDark = true
        this.applyDarkTheme(false) // 不保存，自动逻辑
      }
    }

    // 监听系统暗色模式变化
    this.setupSystemThemeListener()

    // 灰色模式
    if (this.mainStore.webInfo && this.mainStore.webInfo.enableGrayMode) {
      this.applyGrayMask()
    }

    // 初始化目录按钮显示状态
    this.updateTocButtonVisibility()
    this.syncFloatingUiState()

    // 初始化插件加载器（加载已安装的 .zip 插件的前端 JS/CSS）
    this.runWhenIdle(async () => {
      const { initPluginLoader } = await import('@/composables/usePluginLoader')
      initPluginLoader()
    })

    // 初始化简化语言切换按钮显示状态（已注释，不再需要）
    // this.updateSimpleLangSwitchVisibility();
  },
  unmounted() {
    window.removeEventListener('scroll', this.onScrollPage)
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler, true)
      this.resizeHandler = null
    }
    window.removeEventListener('load', this.syncFloatingUiState)
    window.removeEventListener('pageshow', this.syncFloatingUiState)

    // 清除访问量刷新定时器
    if (this.visitCountInterval) {
      clearInterval(this.visitCountInterval)
      this.visitCountInterval = null
    }
  },
  watch: {
    // 监听路由变化，控制目录按钮显示
    $route(to, from) {
      this.updateTocButtonVisibility()
      this.$nextTick(() => {
        this.syncFloatingUiState()
      })
      // this.updateSimpleLangSwitchVisibility(); // 已注释，不再需要
    },

    scrollTop(scrollTop, oldScrollTop) {
      //如果滑动距离超过实际背景高度的一半视为进入页面，背景改为白色
      const backgroundHeight = this.getActualBackgroundHeight()
      let enter = scrollTop > backgroundHeight / 2
      const top = scrollTop - oldScrollTop < 0
      let isShow = scrollTop - backgroundHeight > 30
      this.toolButton = isShow
      if (isShow && !this.$common.mobile()) {
        const cdTopElements = document.querySelectorAll('.cd-top')
        cdTopElements.forEach((element) => {
          if (window.innerHeight > 950) {
            element.style.top = '0'
          } else {
            element.style.top = window.innerHeight - 950 + 'px'
          }
        })
      } else if (!isShow && !this.$common.mobile()) {
        const cdTopElements = document.querySelectorAll('.cd-top')
        cdTopElements.forEach((element) => {
          element.style.top = '-900px'
        })
      }

      //导航栏显示与颜色
      let toolbarStatus = {
        enter: enter,
        visible: top,
      }
      this.mainStore.changeToolbarStatus(toolbarStatus)
    },

    // 监听侧边栏打开状态，应用动态样式
    toolbarDrawer(newVal) {
      if (newVal) {
        // drawer打开时应用样式
        this.$nextTick(() => {
          this.applyDrawerStyles()
        })
      }
    },

    // 监听 store 中的移动端侧边栏配置变化
    'mainStore.webInfo.mobileDrawerConfig': {
      handler(newVal) {
        if (newVal) {
          try {
            this.drawerConfig = JSON.parse(newVal)
            // 如果侧边栏当前是打开的，立即应用新样式
            if (this.toolbarDrawer) {
              this.$nextTick(() => {
                this.applyDrawerStyles()
              })
            }
          } catch (e) {
            // 解析失败，使用默认配置
          }
        }
      },
      deep: true,
    },
  },
  created() {
    // 初始化分类菜单展开状态
    this.sortMenuExpanded = this.getInitialSortMenuState()

    // 获取站点启动配置（合并 webInfo/sysConfig/sortInfo，减少首屏并发请求）
    this.getBootstrap()

    // 性能优化: resize事件防抖优化
    let resizeTimer = null
    this.resizeHandler = () => {
      if (resizeTimer) clearTimeout(resizeTimer)
      resizeTimer = setTimeout(() => {
        this.getWindowWidth()
        let docWidth = document.body.clientWidth
        this.mobile = docWidth < 810
        this.syncFloatingUiState()
      }, 150) // 150ms防抖
    }

    window.addEventListener('resize', this.resizeHandler, true)

    this.getWindowWidth()
    this.mobile = document.body.clientWidth < 1100
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    toolbar() {
      return this.mainStore.toolbar
    },
    sortInfo() {
      return this.mainStore.sortInfo
    },
    mainContainerStyle() {
      return {}
    },
    drawerTitle() {
      if (this.drawerConfig && this.drawerConfig.titleText) {
        return this.drawerConfig.titleText
      }
      return '欢迎光临'
    },
    showDrawerTitle() {
      return !this.drawerConfig || this.drawerConfig.titleType === 'text'
    },
    showDrawerAvatar() {
      return this.drawerConfig && this.drawerConfig.titleType === 'avatar'
    },
    drawerAvatarSize() {
      return (this.drawerConfig && this.drawerConfig.avatarSize) || 100
    },
    showDrawerSnowflake() {
      return this.drawerConfig && this.drawerConfig.showSnowflake !== false
    },
    orderedNavItems() {
      try {
        if (this.mainStore.webInfo && this.mainStore.webInfo.navConfig) {
          const navConfig = this.mainStore.webInfo.navConfig
          // 处理空JSON对象或空字符串的情况
          if (navConfig === '{}' || navConfig === '' || navConfig === '[]') {
            return this.defaultNavItems.filter((item) => item.enabled !== false)
          }

          // 正常解析导航配置，只返回启用的导航项
          return JSON.parse(navConfig).filter((item) => item.enabled !== false)
        }
      } catch (e) {
        // 解析失败，使用默认配置
      }

      // 如果出错或没有配置，返回默认导航项（只包含启用的）
      return this.defaultNavItems.filter((item) => item.enabled !== false)
    },
    defaultNavItems() {
      // 默认导航顺序
      return [
        {
          name: '首页',
          icon: '🏡',
          link: '/',
          type: 'internal',
          order: 1,
          enabled: true,
        },
        {
          name: '分类',
          icon: '📑',
          link: '#',
          type: 'dropdown',
          order: 2,
          enabled: true,
        },
        {
          name: '家',
          icon: '❤️‍🔥',
          link: '/love',
          type: 'internal',
          order: 3,
          enabled: true,
        },
        {
          name: '友人帐',
          icon: '🤝',
          link: '/friends',
          type: 'internal',
          order: 4,
          enabled: true,
        },
        {
          name: '曲乐',
          icon: '🎵',
          link: '/music',
          type: 'internal',
          order: 5,
          enabled: true,
        },
        {
          name: '收藏夹',
          icon: '📁',
          link: '/favorites',
          type: 'internal',
          order: 6,
          enabled: true,
        },
        {
          name: '留言',
          icon: '📪',
          link: '/message',
          type: 'internal',
          order: 7,
          enabled: true,
        },
        {
          name: '联系我',
          icon: '💬',
          link: '#chat',
          type: 'special',
          order: 8,
          enabled: true,
        },
      ]
    },
  },
  methods: {
    runWhenIdle(task) {
      if (typeof window !== 'undefined' && typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(() => task(), { timeout: 1500 })
        return
      }

      setTimeout(() => task(), 0)
    },
    // 处理导航项拖拽开始事件
    handleNavDragStart(event, path, title) {
      // 构建完整URL
      const baseUrl = window.location.origin
      const fullUrl = `${baseUrl}${path}`

      // 设置拖拽数据
      event.dataTransfer.effectAllowed = 'link'
      event.dataTransfer.setData('text/uri-list', fullUrl)
      event.dataTransfer.setData('text/plain', fullUrl)
      event.dataTransfer.setData(
        'text/html',
        `<a href="${fullUrl}">${title}</a>`
      )
    },

    // 返回首页
    goHome() {
      // 如果当前在首页路由，触发首页重置事件
      if (this.$route.path === '/') {
        $emit(this.$root, 'resetIndexPage')
      } else {
        // 如果不在首页，跳转到首页
        this.$router.push({ path: '/' })
      }
    },

    // 移动端返回首页
    goHomeMobile() {
      this.toolbarDrawer = false
      // 如果当前在首页路由，触发首页重置事件
      if (this.$route.path === '/') {
        $emit(this.$root, 'resetIndexPage')
      } else {
        // 如果不在首页，跳转到首页
        this.$router.push({ path: '/' })
      }
    },

    smallMenu(data) {
      this.$router.push(data)
      this.toolbarDrawer = false
    },

    // 获取初始展开状态（智能判断）
    getInitialSortMenuState() {
      // 1. 优先使用用户之前的选择
      const savedState = localStorage.getItem('sortMenuExpanded')
      if (savedState !== null) {
        return savedState === 'true'
      }

      // 2. 首次访问，根据分类数量智能判断
      const sortCount = this.mainStore?.sortInfo?.length || 0

      // 分类少（≤5个）默认展开，分类多（>5个）默认折叠
      return sortCount <= 5
    },

    // 切换移动端侧边栏"分类"菜单的展开/折叠状态
    toggleSortMenu() {
      this.sortMenuExpanded = !this.sortMenuExpanded
      // 记住用户的选择
      localStorage.setItem('sortMenuExpanded', this.sortMenuExpanded)
    },

    smallMenuLogout() {
      this.logout()
      this.toolbarDrawer = false
    },

    async goIm() {
      // 检查IM是否启用
      if (this.mainStore.sysConfig && this.mainStore.sysConfig['im.enable'] === 'false') {
        this.$message({
          message: '聊天室功能暂时关闭',
          type: 'warning',
        })
        return
      }

      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message({
          message: '请先登录！',
          type: 'error',
        })
        return
      }

      try {
        // 获取WebSocket临时token
        const response = await this.$http.get(
          this.$constant.baseURL + '/im/getWsToken',
          {},
          true
        )

        if (response.code === 200 && response.data) {
          const wsToken = response.data
          
          // 使用Vue Router直接跳转到IM页面（IM现在是主站的一部分）
          this.$router.push({ path: '/im', query: { token: wsToken } })
        } else {
          this.$message({
            message: response.message || '获取聊天室访问凭证失败',
            type: 'error',
          })
        }
      } catch (error) {
        this.$message({
          message: '进入聊天室失败，请稍后重试',
          type: 'error',
        })
      }
    },

    goAdmin() {
      window.location.href = this.$constant.adminURL
    },

    // 跳转到登录页，携带当前页面路径作为重定向参数
    goToLogin() {
      if (this.$route.path === '/user') {
        return
      }
      const currentPath = this.$route.fullPath
      this.$router.push({
        path: '/user',
        query: { redirect: currentPath },
      })
    },

    // 移动端跳转到登录页，携带当前页面路径作为重定向参数
    goToLoginMobile() {
      if (this.$route.path === '/user') {
        this.toolbarDrawer = false
        return
      }
      const currentPath = this.$route.fullPath
      this.toolbarDrawer = false
      this.$router.push({
        path: '/user',
        query: { redirect: currentPath },
      })
    },

    // 跳转到个人中心
    goToUserCenter() {
      this.$router.push({ path: '/user' })
    },

    // 处理退出登录
    handleLogout() {
      this.logout()
    },

    logout() {
      // 定义清除状态和跳转的公共逻辑
      const clearAndRedirect = () => {
        this.mainStore.loadCurrentUser({})
        this.mainStore.loadCurrentAdmin({})

        // 只有在需要登录的页面才跳转到首页，否则留在当前页面
        const currentPath = this.$route.path
        const needsAuthPaths = ['/user', '/admin', '/verify']
        const needsRedirect = needsAuthPaths.some((path) =>
          currentPath.startsWith(path)
        )

        if (needsRedirect) {
          this.$router.push({ path: '/' })
        } else {
          // 留在当前页面，显示退出成功提示
          this.$message({
            message: '退出成功',
            type: 'success',
          })
        }
      }

      this.$http
        .get(this.$constant.baseURL + '/user/logout')
        .then((res) => {
          // 退出接口成功返回后清除token和用户信息
          clearAndRedirect()
        })
        .catch((error) => {
          // 即使接口调用失败（如token已过期），也执行本地退出
          clearAndRedirect()
        })
    },
    getBootstrap() {
      this.$http
        .get(this.$constant.baseURL + '/webInfo/bootstrap')
        .then((res) => {
          const data = (res && res.data) || {}
          const webInfo = data.webInfo
          const sysConfig = data.sysConfig
          const sortInfo = data.sortInfo

          if (!this.$common.isEmpty(webInfo)) {
            // 浏览器标签仅使用 webTitle（回退 webName）；homeTitle 是备案名，不显示在标签上
            const originalWebTitle = webInfo.webTitle || webInfo.webName
            this.mainStore.loadWebInfo(webInfo)

            // 文章页/分类页自行管理标题，跳过全局覆盖（防异步竞态覆盖文章标题）
            const path = this.$route.path
            const selfManaged = path.startsWith('/article/') || path.startsWith('/sort/')
            if (originalWebTitle && !selfManaged) {
              document.title = originalWebTitle
              window.OriginTitile = originalWebTitle
            }

            this.maybeApplyAutoNight()
          }

          if (!this.$common.isEmpty(sysConfig)) {
            this.mainStore.loadSysConfig(sysConfig)
            this.buildCssPicture()
          }

          if (!this.$common.isEmpty(sortInfo)) {
            this.mainStore.loadSortInfo(sortInfo)
          }

          applyLanguageBootstrap(data)
        })
        .catch((error) => {
          console.warn('获取站点启动配置失败，回退旧接口:', error)
          this.getWebInfo()
          this.getSysConfig()
          this.getSortInfo()
        })
    },
    getWebInfo() {
      this.$http
        .get(this.$constant.baseURL + '/webInfo/getWebInfo')
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            // 浏览器标签仅使用 webTitle（回退 webName）；homeTitle 是备案名，不显示在标签上
            const originalWebTitle = res.data.webTitle || res.data.webName

            // 处理网站信息
            this.mainStore.loadWebInfo(res.data)

            // 更新浏览器标签栏标题 - 使用 webTitle（文章页/分类页自管理，跳过覆盖）
            const path = this.$route.path
            const selfManaged = path.startsWith('/article/') || path.startsWith('/sort/')
            if (originalWebTitle && !selfManaged) {
              document.title = originalWebTitle
              // 同时更新title.js中保存的原始标题
              window.OriginTitile = originalWebTitle
            }

            // 获取完 webInfo 后再执行一次自动夜间判断
            this.maybeApplyAutoNight()
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },

    // 已移除定时刷新访问量的逻辑
    getSysConfig() {
      this.$http
        .get(this.$constant.baseURL + '/sysConfig/listSysConfig')
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.mainStore.loadSysConfig(res.data)
            this.buildCssPicture()
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    buildCssPicture() {
      let root = document.querySelector(':root')
      let webStaticResourcePrefix =
        this.mainStore.sysConfig['webStaticResourcePrefix']
      root.style.setProperty(
        '--commentURL',
        'url(' + webStaticResourcePrefix + 'assets/commentURL.png)'
      )
      root.style.setProperty(
        '--springBg',
        'url(' + webStaticResourcePrefix + 'assets/springBg.png)'
      )
      root.style.setProperty(
        '--admireImage',
        'url(' + webStaticResourcePrefix + 'assets/admireImage.jpg)'
      )
      root.style.setProperty(
        '--toTop',
        'url(' + webStaticResourcePrefix + 'assets/toTop.png)'
      )
      root.style.setProperty(
        '--bannerWave1',
        'url(' + webStaticResourcePrefix + 'assets/bannerWave1.png) repeat-x'
      )
      root.style.setProperty(
        '--bannerWave2',
        'url(' + webStaticResourcePrefix + 'assets/bannerWave2.png) repeat-x'
      )
      root.style.setProperty(
        '--backgroundPicture',
        'url(' + webStaticResourcePrefix + 'assets/backgroundPicture.jpg)'
      )
      root.style.setProperty(
        '--toolbar',
        'url(' + webStaticResourcePrefix + 'assets/toolbar.jpg)'
      )
      root.style.setProperty(
        '--love',
        'url(' + webStaticResourcePrefix + 'assets/love.jpg)'
      )
    },
    getSortInfo() {
      this.$http
        .get(this.$constant.baseURL + '/webInfo/getSortInfo')
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.mainStore.loadSortInfo(res.data)
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    changeColor() {
      this.isDark = !this.isDark
      if (this.isDark) {
        this.applyDarkTheme() // savePreference默认为true，会保存用户偏好
      } else {
        this.applyLightTheme() // savePreference默认为true，会保存用户偏好
      }

      // 重新初始化Mermaid主题并触发重新渲染
      this.reinitializeMermaidTheme()
    },

    // 监听系统暗色模式变化
    setupSystemThemeListener() {
      // 检查浏览器是否支持媒体查询
      if (!window.matchMedia) {
        return
      }

      const darkModeQuery = window.matchMedia('(prefers-color-scheme: dark)')

      // 监听系统主题变化
      const handleThemeChange = (e) => {
        // 检查用户是否手动设置过主题
        const userTheme = localStorage.getItem('theme')

        // 只有在用户未手动设置时才自动切换
        if (!userTheme) {
          if (e.matches) {
            this.isDark = true
            this.applyDarkTheme(false) // false表示不保存用户偏好
          } else {
            this.isDark = false
            this.applyLightTheme(false) // false表示不保存用户偏好
          }

          // 触发主题切换事件（用于Mermaid等组件）
          $emit(this.$root, 'theme-changed', {
            isDark: this.isDark,
            source: 'system',
          })
        } else {
        }
      }

      // 添加监听器
      if (darkModeQuery.addEventListener) {
        darkModeQuery.addEventListener('change', handleThemeChange)
      } else if (darkModeQuery.addListener) {
        // 兼容旧版浏览器
        darkModeQuery.addListener(handleThemeChange)
      }
    },

    applyDarkTheme(savePreference = true) {
      let root = document.querySelector(':root')
      document.body.classList.add('dark-mode')
      document.documentElement.classList.add('dark-mode') // 同时在html元素上添加，确保所有组件都能检测到
      root.style.setProperty('--background', '#272727')
      root.style.setProperty('--fontColor', 'white')
      root.style.setProperty('--borderColor', '#4F4F4F')
      root.style.setProperty('--borderHoverColor', 'black')
      root.style.setProperty('--articleFontColor', '#E4E4E4')
      root.style.setProperty('--articleGreyFontColor', '#D4D4D4')
      root.style.setProperty('--commentContent', '#383838')
      root.style.setProperty('--favoriteBg', '#1e1e1e')
      // 修复遮罩相关变量
      root.style.setProperty('--whiteMask', 'rgba(56, 56, 56, 0.3)')
      root.style.setProperty('--maxWhiteMask', 'rgba(56, 56, 56, 0.5)')
      root.style.setProperty('--maxMaxWhiteMask', 'rgba(56, 56, 56, 0.7)')
      root.style.setProperty('--miniWhiteMask', 'rgba(56, 56, 56, 0.15)')
      root.style.setProperty('--mask', 'rgba(0, 0, 0, 0.5)')
      root.style.setProperty('--miniMask', 'rgba(0, 0, 0, 0.3)')
      root.style.setProperty('--inputBackground', '#383838')
      root.style.setProperty('--secondaryText', '#B0B0B0')
      // 设置卡片背景RGB值用于半透明背景
      root.style.setProperty('--card-bg-rgb', '39, 39, 39')

      // 保存用户手动设置的主题偏好
      if (savePreference) {
        localStorage.setItem('theme', 'dark')
      }
    },

    applyLightTheme(savePreference = true) {
      let root = document.querySelector(':root')
      document.body.classList.remove('dark-mode')
      document.documentElement.classList.remove('dark-mode') // 同时从html元素移除
      root.style.setProperty('--background', 'white')
      root.style.setProperty('--fontColor', 'black')
      root.style.setProperty('--borderColor', 'rgba(0, 0, 0, 0.5)')
      root.style.setProperty('--borderHoverColor', 'rgba(110, 110, 110, 0.4)')
      root.style.setProperty('--articleFontColor', '#1F1F1F')
      root.style.setProperty('--articleGreyFontColor', '#616161')
      root.style.setProperty('--commentContent', '#F7F9FE')
      root.style.setProperty('--favoriteBg', '#f7f9fe')
      // 恢复亮色模式的遮罩变量
      root.style.setProperty('--whiteMask', 'rgba(255, 255, 255, 0.3)')
      root.style.setProperty('--maxWhiteMask', 'rgba(255, 255, 255, 0.5)')
      root.style.setProperty('--maxMaxWhiteMask', 'rgba(255, 255, 255, 0.7)')
      root.style.setProperty('--miniWhiteMask', 'rgba(255, 255, 255, 0.15)')
      root.style.setProperty('--mask', 'rgba(0, 0, 0, 0.3)')
      root.style.setProperty('--miniMask', 'rgba(0, 0, 0, 0.15)')
      root.style.setProperty('--inputBackground', '#f5f5f5')
      root.style.setProperty('--secondaryText', '#666666')
      // 设置卡片背景RGB值用于半透明背景
      root.style.setProperty('--card-bg-rgb', '255, 255, 255')

      // 保存用户手动设置的主题偏好
      if (savePreference) {
        localStorage.setItem('theme', 'light')
      }
    },

    // 重新初始化Mermaid主题并重新渲染图表
    reinitializeMermaidTheme() {
      try {
        // 检查Mermaid是否已加载
        if (!window.mermaid) {
          return
        }

        // 重新初始化Mermaid配置
        // 始终使用 'default' 主题保持原始的节点颜色
        // 暗色模式下的文字颜色会通过 JavaScript 直接修改 SVG 元素来处理

        window.mermaid.initialize({
          startOnLoad: false,
          theme: 'default', // 始终使用 default 主题，保留原始节点颜色
          securityLevel: 'loose',
          fontFamily: 'Arial, sans-serif',
          themeVariables: {
            fontSize: '14px',
          },
        })

        // 触发全局事件，通知文章页面重新渲染Mermaid图表
        $emit(this.$root, 'themeChanged', {
          isDark: this.isDark,
          theme: 'default', // 始终使用 default 主题
        })
      } catch (error) {
        console.error('重新初始化Mermaid主题失败:', error)
      }
    },

    // 更新目录按钮显示状态
    updateTocButtonVisibility() {
      // 只在文章页面显示目录按钮
      // 支持新的URL格式：/article/id 或 /article/lang/id
      this.showTocButton =
        this.$route.path.startsWith('/article/') && this.$route.params.id
    },

    // 目录按钮点击事件
    clickTocButton() {
      const tocElements = document.querySelectorAll('.toc')
      tocElements.forEach((element) => {
        const currentDisplay = window.getComputedStyle(element).display
        if (currentDisplay === 'none') {
          element.style.display = 'unset'
        } else {
          element.style.display = 'none'
        }
      })
    },

    // 注释原因：通过CSS层叠上下文已解决article.vue中语言切换按钮的遮挡问题，不再需要简化按钮
    // 更新简化语言切换按钮显示状态
    /* updateSimpleLangSwitchVisibility() {
// 只在文章页面显示简化语言切换按钮
// 支持新的URL格式：/article/id 或 /article/lang/id
this.showSimpleLangSwitch = this.$route.path.startsWith('/article/') && this.$route.params.id;
}, */

    // 获取当前语言的简化显示
    /* getSimpleLangDisplay() {
// 从article组件获取当前语言，如果获取不到则默认为中文
const articleComponent = this.getArticleComponent();

if (articleComponent && articleComponent.currentLang) {
const langMap = {
  'zh': '简',
  'zh-TW': '繁',
  'zh-CN': '简',
  'zh-HK': '港',
  'zh-Hant': '繁',
  'zh-Hans': '简',
  'en': 'EN',
  'ja': 'JP',
  'ko': '한',
  'fr': 'FR',
  'de': 'DE',
  'es': 'ES',
  'ru': 'RU',
  'pt': 'PT',
  'it': 'IT',
  'ar': 'AR',
  'th': 'TH',
  'vi': 'VI'
};
return langMap[articleComponent.currentLang] || articleComponent.currentLang.toUpperCase();
}

// 如果无法获取article组件，尝试从URL或localStorage获取默认语言
const urlParams = new URLSearchParams(window.location.search);
const urlLang = urlParams.get('lang');
const savedLang = localStorage.getItem('preferredLanguage');

// 优先使用URL参数，然后是保存的偏好，最后是默认值
const defaultLang = urlLang || savedLang || 'zh';

const langMap = {
'zh': '简',
'zh-TW': '繁',
'zh-CN': '简',
'zh-HK': '港',
'zh-Hant': '繁',
'zh-Hans': '简',
'en': 'EN',
'ja': 'JP',
'ko': '한',
'fr': 'FR',
'de': 'DE',
'es': 'ES',
'ru': 'RU',
'pt': 'PT',
'it': 'IT',
'ar': 'AR',
'th': 'TH',
'vi': 'VI'
};

return langMap[defaultLang] || '简';
}, */

    // 获取简化语言切换按钮的提示文本
    /* getSimpleLangSwitchTitle() {
const articleComponent = this.getArticleComponent();
if (articleComponent && articleComponent.availableLanguageButtons) {
const nextLang = this.getNextAvailableLanguage();
if (nextLang) {
  return `点击切换到${nextLang.name}`;
}
}
return '语言切换';
}, */

    // 获取下一个可用语言
    /* getNextAvailableLanguage() {
const articleComponent = this.getArticleComponent();
if (articleComponent && articleComponent.availableLanguageButtons && articleComponent.availableLanguageButtons.length > 1) {
const currentIndex = articleComponent.availableLanguageButtons.findIndex(
  lang => lang.code === articleComponent.currentLang
);
const nextIndex = (currentIndex + 1) % articleComponent.availableLanguageButtons.length;
return articleComponent.availableLanguageButtons[nextIndex];
}
return null;
}, */

    // 获取article组件实例
    /* getArticleComponent() {
// 通过多种方式查找article组件
const findArticleComponent = (children) => {
for (let child of children) {
  // 检查组件是否有article相关的数据属性（更严格的检查）
  if (child.availableLanguageButtons !== undefined &&
      child.currentLang !== undefined &&
      child.handleLanguageSwitch !== undefined &&
      child.sourceLanguage !== undefined &&
      child.languageMap !== undefined) {
    return child;
  }
  // 检查组件名称和文件路径
  if (child.$options.name === 'article' ||
      child.$vnode?.tag?.includes('article') ||
      child.$options._componentTag === 'article' ||
      child.$options.__file?.includes('article.vue')) {
    return child;
  }
  // 递归查找子组件
  if (child.$children && child.$children.length > 0) {
    const found = findArticleComponent(child.$children);
    if (found) return found;
  }
}
return null;
};

// 首先尝试从$children查找
let articleComponent = findArticleComponent(this.$children);

// 如果没找到，尝试从$refs查找
if (!articleComponent && this.$refs) {
for (let refName in this.$refs) {
  const ref = this.$refs[refName];
  if (ref && ref.availableLanguageButtons !== undefined &&
      ref.currentLang !== undefined &&
      ref.handleLanguageSwitch !== undefined &&
      ref.sourceLanguage !== undefined) {
    articleComponent = ref;
    break;
  }
}
}

// 如果还没找到，尝试从全局查找
if (!articleComponent) {
const allComponents = this.$root.$children;
articleComponent = findArticleComponent(allComponents);
}

// 最后尝试从router-view中查找
if (!articleComponent) {
const routerView = this.$children.find(child =>
  child.$vnode?.componentOptions?.tag === 'router-view'
);
if (routerView && routerView.$children) {
  articleComponent = findArticleComponent(routerView.$children);
}
}

return articleComponent;
}, */

    // 处理简化语言切换按钮点击
    /* handleSimpleLangSwitch() {
const articleComponent = this.getArticleComponent();
if (articleComponent && articleComponent.handleLanguageSwitch) {
const nextLang = this.getNextAvailableLanguage();
if (nextLang) {
  // 调用article组件的语言切换方法
  articleComponent.handleLanguageSwitch(nextLang.code);

  // 强制更新显示
  this.$forceUpdate();
}
}
}, */

    toTop() {
      window.scrollTo({
        top: 0,
        behavior: 'smooth',
      })
    },
    onScrollPage() {
      const scrollingElement =
        document.scrollingElement || document.documentElement || document.body
      this.scrollTop = scrollingElement ? scrollingElement.scrollTop : 0
    },
    syncFloatingUiState() {
      this.onScrollPage()
      requestAnimationFrame(() => {
        this.onScrollPage()
      })
    },
    isDaylight() {
      // 后台可配置：enableAutoNight, autoNightStart, autoNightEnd
      const cfg = this.mainStore?.webInfo || {}

      // 若未开启自动夜间则直接返回 false
      if (cfg.enableAutoNight === false) return false

      // 读取小时区间，提供默认值 23~7
      const start =
        typeof cfg.autoNightStart === 'number' ? cfg.autoNightStart : 23
      const end = typeof cfg.autoNightEnd === 'number' ? cfg.autoNightEnd : 7

      const h = new Date().getHours()

      // 跨午夜区间的判断
      if (start > end) {
        return h >= start || h < end
      }
      // 同日区间
      return h >= start && h < end
    },
    // 循环切换鼠标点击效果
    cycleClickEffect() {
      import('@/composables/useMouseClickEffect').then(({ cycleMouseClickEffect }) => {
        const result = cycleMouseClickEffect(this.mainStore)
        this.currentClickEffectLabel = result.label
        this.$message({
          message: `点击效果已切换为: ${result.label}`,
          type: 'success',
          duration: 2000,
        })
      })
    },
    // 更新当前点击效果标签
    updateClickEffectLabel() {
      this.runWhenIdle(async () => {
        try {
          const { getMouseClickEffectInfo } = await import('@/composables/useMouseClickEffect')
          const info = getMouseClickEffectInfo(this.mainStore)
          this.currentClickEffectLabel = info.label
        } catch (error) {
          this.currentClickEffectLabel = '无效果'
        }
      })
    },
    getWindowWidth() {
      // Implementation of getWindowWidth method
    },
    getRandomFont() {
      // Implementation of getRandomFont method
    },
    // 根据后台配置重新判断并自动应用夜间主题（仅当用户未手动设置主题时调用）
    maybeApplyAutoNight() {
      try {
        // 检查用户是否手动设置过主题偏好
        const userTheme = localStorage.getItem('theme')
        if (userTheme) {
          return // 尊重用户手动设置
        }

        // 检查系统偏好优先，否则使用时间判断
        const prefersDark =
          window.matchMedia &&
          window.matchMedia('(prefers-color-scheme: dark)').matches
        if (prefersDark) {
          this.isDark = true
          this.applyDarkTheme(false) // 不保存，跟随系统
        } else if (this.isDaylight()) {
          this.isDark = true
          this.applyDarkTheme(false) // 不保存，跟随时间
        } else {
          this.isDark = false
          this.applyLightTheme(false) // 不保存，跟随时间
        }
      } catch (e) {}
    },
    applyGrayMask() {
      if (document.getElementById('gray-mask')) return
      const mask = document.createElement('div')
      mask.id = 'gray-mask'
      mask.style.position = 'fixed'
      mask.style.inset = '0'
      mask.style.pointerEvents = 'none'
      mask.style.background = '#000'
      mask.style.mixBlendMode = 'saturation'
      mask.style.zIndex = '2147483647'
      // 安全地添加mask元素到body
      if (
        mask &&
        mask.nodeType === Node.ELEMENT_NODE &&
        document.body &&
        typeof document.body.appendChild === 'function'
      ) {
        try {
          document.body.appendChild(mask)
        } catch (e) {}
      }
    },
    getWebsitConfig() {
      this.mainStore.getWebsitConfig()
      // 加载侧边栏配置
      this.loadDrawerConfig()
    },
    loadDrawerConfig() {
      try {
        if (
          this.mainStore.webInfo &&
          this.mainStore.webInfo.mobileDrawerConfig
        ) {
          this.drawerConfig = JSON.parse(
            this.mainStore.webInfo.mobileDrawerConfig
          )
          // 应用动态样式
          this.$nextTick(() => {
            this.applyDrawerStyles()
          })
        }
      } catch (e) {
        console.error('解析移动端侧边栏配置失败:', e)
      }
    },
    applyDrawerStyles() {
      if (!this.drawerConfig) return

      const drawerElement = document.querySelector('.toolbarDrawer')
      if (!drawerElement) return

      // 设置背景
      if (
        this.drawerConfig.backgroundType === 'image' &&
        this.drawerConfig.backgroundImage
      ) {
        drawerElement.style.background = `url(${this.drawerConfig.backgroundImage}) center center / cover no-repeat`
      } else if (this.drawerConfig.backgroundType === 'color') {
        drawerElement.style.background = this.drawerConfig.backgroundColor
      } else if (this.drawerConfig.backgroundType === 'gradient') {
        drawerElement.style.background = this.drawerConfig.backgroundGradient
      }

      // 设置遮罩透明度
      let maskOpacity = 0.7 // 默认值
      if (this.drawerConfig.maskOpacity !== undefined) {
        maskOpacity = this.drawerConfig.maskOpacity
        drawerElement.style.setProperty(
          '--drawer-mask-opacity',
          maskOpacity
        )
      }

      // 动态设置滚动条颜色 - 根据遮罩透明度调整亮度
      // Mask 1.0 -> Scrollbar 0.15
      // Mask 0.4 -> Scrollbar 0.39
      const scrollbarOpacity = 0.15 + (1 - maskOpacity) * 0.4
      drawerElement.style.setProperty(
        '--scrollbar-bg',
        `rgba(255, 255, 255, ${scrollbarOpacity})`
      )

      // 设置标题和菜单字体颜色
      const headerElement = drawerElement.querySelector('.el-drawer__header')
      if (headerElement && this.drawerConfig.menuFontColor) {
        headerElement.style.color = this.drawerConfig.menuFontColor
      }

      // 设置菜单字体颜色
      if (this.drawerConfig.menuFontColor) {
        drawerElement.style.setProperty(
          '--menu-font-color',
          this.drawerConfig.menuFontColor
        )
      }

      // 设置边框样式
      const menuItems = drawerElement.querySelectorAll('.small-menu li')
      menuItems.forEach((item) => {
        if (this.drawerConfig.showBorder) {
          item.style.borderBottom = `1px solid ${this.drawerConfig.borderColor}`
        } else {
          item.style.borderBottom = 'none'
        }
      })

      // 最后一个菜单项不显示边框
      if (menuItems.length > 0 && this.drawerConfig.showBorder) {
        menuItems[menuItems.length - 1].style.borderBottom = 'none'
      }
    },
    handleAvatarError(e) {
      // 防止无限循环：检查当前 src 是否已经是默认头像
      if (e.target.src.includes('/assets/avatar.jpg')) {
        // 移除 error 监听器，防止继续触发
        e.target.onerror = null
        // 不做任何处理，让浏览器显示默认的图片加载失败状态
        return
      }

      // 第一次失败时尝试使用默认头像
      e.target.src = '/assets/avatar.jpg'
    },
    loadFont() {},
    getActualBackgroundHeight() {
      // 获取当前设置的首页上拉高度，与bannerStyle()保持一致的计算逻辑
      const height = this.mainStore.webInfo.homePagePullUpHeight

      // 如果是有效的数值且在0-100范围内，直接使用该值作为vh
      if (typeof height === 'number' && height >= 0 && height <= 100) {
        // height值直接对应vh，100 = 100vh = window.innerHeight
        return window.innerHeight * (height / 100)
      }

      // 否则使用默认的50vh
      return window.innerHeight / 2
    },
  },
  emits: ['theme-changed', 'themeChanged', 'resetIndexPage'],
}
</script>

<style scoped>
.toolbar-content {
  width: 100%;
  height: 60px;
  color: var(--white);
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  user-select: none;
  transition: transform 0.3s ease-in-out, opacity 0.3s ease-in-out,
    background-color 0.3s ease-in-out;
  font-family: 'MyAwesomeFont', serif;
  will-change: transform, opacity;
  transform: translateZ(0);
}
.toolbar-content.enter {
  background: var(--toolbarBackground);
  color: var(--toolbarFont);
  box-shadow: 0 1px 3px 0 rgba(0, 34, 77, 0.05);
}
.toolbar-content.hoverEnter {
  background: var(--translucent);
  box-shadow: 0 1px 3px 0 rgba(0, 34, 77, 0.05);
}
.toolbar-title {
  margin-left: 30px;
  cursor: pointer;
  font-family: 'MyAwesomeFont', serif;
}
.toolbar-logo {
  height: 40px;
  width: auto;
  max-width: 200px;
  display: block;
  object-fit: contain;
}
@media screen and (max-width: 768px) {
  .toolbar-logo {
    height: 32px;
    max-width: 140px;
  }
}
.toolbar-mobile-menu {
  font-size: 30px;
  margin-right: 15px;
  cursor: pointer;
}
.scroll-menu {
  margin: 0 25px 0 0;
  display: flex;
  justify-content: flex-end;
  padding: 0;
  font-family: 'MyAwesomeFont', serif;
}
.scroll-menu li {
  list-style: none;
  margin: 0 12px;
  font-size: 17px;
  height: 60px;
  line-height: 60px;
  position: relative;
  cursor: pointer;
  font-family: 'MyAwesomeFont', serif;
}
.scroll-menu li:hover .my-menu span {
  color: var(--themeBackground);
}
.scroll-menu li:hover .my-menu i {
  color: var(--themeBackground);
  animation: scale 1.5s ease-in-out infinite;
}
.scroll-menu li .my-menu:after {
  content: '';
  display: block;
  position: absolute;
  bottom: 0;
  height: 6px;
  background-color: var(--themeBackground);
  width: 100%;
  max-width: 0;
  transition: max-width 0.25s ease-in-out;
}
.scroll-menu li:hover .my-menu:after {
  max-width: 100%;
}
.sortMenu {
  margin-left: 44px;
  font-size: 17px;
  position: relative;
  color: var(--menu-font-color);
  padding: 8px 15px;
  transition: background-color 0.3s ease, color 0.3s ease;
}
.sortMenu:hover {
  background-color: rgba(255, 255, 255, 0.1);
}
.sortMenu:after {
  top: 32px;
  width: 35px;
  left: 0;
  height: 2px;
  background: var(--themeBackground);
  content: '';
  border-radius: 1px;
  position: absolute;
}
.el-dropdown {
  font-size: unset;
  color: unset;
}
.el-popper[x-placement^='bottom'] {
  margin-top: -8px;
}
.el-dropdown-menu {
  padding: 5px 0;
}
.el-dropdown-menu__item {
  font-size: unset;
}
.el-dropdown-menu__item:hover {
  background-color: var(--white);
  color: var(--themeBackground);
}
.toolButton {
  position: fixed;
  right: 3vh;
  bottom: 3vh;
  z-index: 100;
  cursor: pointer;
  font-size: 25px;
  width: 30px;
}
.my-setting {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-around;
  cursor: pointer;
  font-size: 20px;
}
.my-setting i {
  padding: 5px;
}
.my-setting i:hover {
  color: var(--themeBackground);
}
.cd-top {
  background: var(--toTop) no-repeat center;
  position: fixed;
  right: 5vh;
  top: -900px;
  z-index: 99;
  width: 70px;
  height: 900px;
  background-size: contain;
  transition: opacity 0.5s ease-in-out, transform 0.5s ease-in-out;
  cursor: pointer;
}
.backTop {
  transition: transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1),
    opacity 0.3s ease-in;
  position: relative;
  left: -13px;
}
.backTop:hover {
  transform: translateY(-10px);
}
#outerImg {
  position: fixed;
  top: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 10;
  width: 100%;
  height: 100%;
  display: none;
}
.toc-button-container {
  cursor: pointer;
  font-size: 25px;
  width: 30px;
  transition: transform 0.3s ease, color 0.3s ease, opacity 0.3s ease;
  margin-bottom: 4px;
}
.toc-button-icon {
  font-size: 23px;
  color: var(--black);
}
.toc-button-container:hover .toc-button-icon {
  color: var(--themeBackground);
}
@media screen and (max-width: 400px) {
  .toolButton {
    right: 0.5vh;
  }
}
body.dark-mode .toc-button-icon {
  color: #ffffff !important;
}
body.dark-mode .toc-button-container:hover .toc-button-icon {
  color: var(--lightGreen) !important;
}
body.dark-mode .backTop svg path {
  fill: #ffffff !important;
}
body.dark-mode .iconRotate {
  color: #ffffff !important;
}
body.dark-mode .iconRotate:hover {
  color: var(--lightGreen) !important;
}

.my-menu {
  font-family: 'MyAwesomeFont', serif;
}
.my-menu span {
  font-family: 'MyAwesomeFont', serif;
}

.circle-login-button {
  background-color: #ff8da1;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
}
.toolbarDrawer {
  --menu-font-color: #ffffff;
}
.toolbarDrawer :deep(.el-drawer__header){
  padding: 20px 0 0;
}
.small-menu {
  padding: 0;
  margin: 0;
  list-style: none;
}
.small-menu li {
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: background-color 0.3s ease;
}
.small-menu li:last-child {
  border-bottom: none;
}
.small-menu li > div:first-child {
  padding: 10px 20px;
  font-size: 16px;
  font-weight: bold;
  color: var(--menu-font-color);
  transition: color 0.3s ease;
}
.small-menu li:hover > div:first-child {
  background-color: rgba(255, 255, 255, 0.1);
}
.small-menu li:active > div:first-child {
  background-color: rgba(255, 255, 255, 0.2);
}
.drawer-avatar-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 10px 0;
  width: 100%;
}
.drawer-divider {
  align-self: stretch;
  width: 100%;
  position: relative;
  margin: 30px auto 0;
  border: 0;
  border-top: 1px dashed var(--lightGreen);
  overflow: visible;
}
.drawer-divider::before {
  position: absolute;
  top: 50%;
  left: 5%;
  transform: translateY(-50%);
  color: var(--lightGreen);
  content: '';
  font-size: 28px;
  line-height: 1;
}
.drawer-divider.show-snowflake::before {
  content: '❄';
}
.small-menu .sortMenu {
  padding: 0;
}
.sort-menu-header {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  position: relative;
  width: 100%;
  padding: 10px 20px;
  font-size: 16px;
  font-weight: bold;
  color: var(--menu-font-color);
}
.sort-menu-arrow {
  font-size: 12px;
  color: var(--menu-font-color);
  transition: transform 0.3s ease;
  transform: rotate(0deg);
  position: absolute;
  right: 10%;
}
.sort-menu-arrow.expanded {
  transform: rotate(90deg);
}
.sort-submenu {
  max-height: 1000px;
  overflow: hidden;
  transition: max-height 0.4s ease, opacity 0.4s ease;
  opacity: 1;
}
.sort-submenu.collapsed {
  max-height: 0;
  opacity: 0;
}
</style>

<style>
/* 头像下拉容器 */
.avatar-dropdown-container {
  position: relative;
  display: inline-block;
}

/* 初始响应区域（伪元素） */
.avatar-dropdown-container::before {
  content: '';
  position: absolute;
  top: -20px;
  left: -10px;
  right: -30px;
  bottom: 0px;
  min-height: 80px;
  z-index: 99;
  transition: all 0.3s ease;
  /* 调试用 */
  /* background: rgba(0, 255, 0, 0.1); */
}

/* hover时动态扩大响应区域 - 覆盖头像移动路径和菜单 */
.avatar-dropdown-container:hover::before {
  top: -20px;
  left: -160px; /* 向左扩展，覆盖头像移动后的位置 */
  right: -150px; /* 向右扩展，覆盖菜单 */
  bottom: -10px;
  min-height: 380px;
  /* 调试用：hover时变成红色 */
  /* background: rgba(255, 0, 0, 0.1); */
}

/* 导航栏头像样式 */
.user-avatar {
  cursor: pointer;
  transition: all 0.3s ease;
  will-change: transform;
  transform: translateZ(0);
  position: relative;
  z-index: 102;
  box-shadow: 0 0 0 1px #ffffff;
  border-radius: 50%;
}

/* 悬停在容器上时，头像偏移 */
.avatar-dropdown-container:hover .user-avatar {
  transform: translate(-105px, 43px) scale(1.7) !important;
}

/* 自定义用户下拉菜单 */
.custom-user-menu {
  position: absolute;
  top: 70px;
  right: -10px;
  width: 260px;
  background: #fff;
  backdrop-filter: blur(20px);
  border-radius: 8px;
  z-index: 101;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 0 24px 18px;
  box-shadow: 0 0 30px rgba(0, 0, 0, 0.1);
  border: 1px solid #e3e5e7;
  /* 默认隐藏 */
  opacity: 0;
  transform: translateY(-10px);
  pointer-events: none;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

/* 悬停时显示菜单 */
.avatar-dropdown-container:hover .custom-user-menu {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

/* 用户名头部 */
.user-menu-header {
  color: rgb(24, 25, 28);
  font-weight: bold;
  font-size: 18px;
  margin-top: 23px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.user-menu-name {
  font-size: 20px;
  font-weight: 700;
  display: inline-block;
  letter-spacing: 0.5px;
}

/* 角色徽章（用户名右侧的小标签） */
.user-role-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  color: #606266;
  background: #f0f2f5;
  border: 1px solid #e5e7eb;
}

/* 管理员样式 */
.user-role-badge.admin {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.1);
  border-color: #ff4d4f;
}

/* 站长样式 */
.user-role-badge.owner {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
  border-color: #2563eb;
}

/* 菜单项 */
.user-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  height: 38px;
  border-radius: 8px;
  color: #61666d;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin-bottom: 2px;
  box-sizing: border-box;
}

.user-menu-item i.fa-user-circle,
.user-menu-item i.fa-sign-out {
  margin-right: 5px;
  font-size: 14px;
  width: 16px;
  text-align: center;
  color: #718096;
  flex-shrink: 0;
}

.user-menu-item span {
  flex: 1;
  font-weight: 500;
}

.user-menu-item i.menu-arrow {
  margin-left: auto;
  font-size: 16px;
  opacity: 0.6;
  color: #8e9299;
  flex-shrink: 0;
}

.user-menu-item:hover {
  background-color: #f7fafc;
}

/* 分割线 */
.user-menu-divider {
  margin: 6px 0 12px 0;
  border-bottom: 1px solid #ddd;
}
</style>
