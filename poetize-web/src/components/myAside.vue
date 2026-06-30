<template>
  <div>
    <div class="myAside-container">
      <!-- 网站信息 -->
      <div
        v-if="!$common.mobile()"
        class="card-content1 shadow-box background-opacity"
        :style="asideBackgroundStyle"
      >
        <el-avatar
          style="
            margin-top: 20px;
            cursor: pointer;
            transition: transform 0.6s ease;
          "
          class="user-avatar"
          :size="120"
          :src="$common.getAvatarUrl(webInfo.avatar)"
          @mouseenter="handleAvatarHover"
          @mouseleave="handleAvatarLeave"
        >
          <img :src="$getDefaultAvatar()" />
        </el-avatar>
        <div class="web-name">{{ webInfo.webName }}</div>
        <div class="web-info">
          <div class="blog-info-box">
            <span>文章</span>
            <span class="blog-info-num">{{ mainStore.articleTotal }}</span>
          </div>
          <div class="blog-info-box">
            <span>分类</span>
            <span class="blog-info-num">{{ sortInfo.length }}</span>
          </div>
          <div class="blog-info-box">
            <span>访问量</span>
            <span class="blog-info-num">{{ webInfo.historyAllCount }}</span>
          </div>
        </div>
        <!-- 快捷入口按钮（可自定义） -->
        <template v-if="quickEntryList && quickEntryList.length > 0">
          <a
            v-for="(quickEntry, index) in quickEntryList"
            :key="quickEntry.id"
            :href="quickEntry.url || 'javascript:void(0)'"
            :target="quickEntry.url ? '_blank' : '_self'"
            class="collection-btn"
            :class="{
              'has-contact':
                contactList &&
                contactList.length > 0 &&
                index === quickEntryList.length - 1,
            }"
            :style="{
              width: quickEntry.btnWidth || '65%',
              height: quickEntry.btnHeight || '35px',
              lineHeight: quickEntry.btnHeight || '35px',
              borderRadius: quickEntry.btnRadius || '1rem',
            }"
            :title="
              quickEntry.introduction || removeIconPlaceholder(quickEntry.title)
            "
          >
            <span v-html="parseIconPlaceholder(quickEntry.title)"></span>
          </a>
        </template>
        <!-- 默认朋友圈按钮 -->
        <a
          v-else
          class="collection-btn"
          :class="{ 'has-contact': contactList && contactList.length > 0 }"
          @click="showTip()"
        >
          <span v-html="parseIconPlaceholder('[star]朋友圈')"></span>
        </a>

        <!-- 联系方式小图标 -->
        <div
          v-if="contactList && contactList.length > 0"
          class="contact-container"
        >
          <a
            v-for="contact in contactList"
            :key="contact.id"
            :href="contact.url || 'javascript:void(0)'"
            :target="contact.url ? '_blank' : '_self'"
            class="contact-item"
            :style="{
              width: contact.btnWidth || '25px',
              height: contact.btnHeight || '25px',
            }"
            :title="
              contact.title +
              (contact.introduction ? ' - ' + contact.introduction : '')
            "
          >
            <!-- 有封面时只显示封面，没有封面时才显示文字 -->
            <template v-if="contact.cover">
              <img
                :src="contact.cover"
                :alt="contact.title"
                class="contact-icon"
                :style="{
                  width: contact.btnWidth || '25px',
                  height: contact.btnHeight || '25px',
                  borderRadius: contact.btnRadius || '0',
                }"
              />
            </template>
            <template v-else>
              <span class="contact-text">{{ contact.title }}</span>
            </template>
          </a>
        </div>
      </div>

      <!-- 搜索 -->
      <div class="search-container shadow-box background-opacity" v-animate>
        <div class="search-title">搜索</div>
        <div class="search-box">
          <input
            class="ais-SearchBox-input"
            type="text"
            v-model="articleSearch"
            @keyup.enter="selectArticle()"
            @input="handleSearchInput"
            @focus="showSearchTipsOnFocus"
            @blur="hideSearchTipsOnBlur"
            placeholder="搜索文章"
            maxlength="50"
          />
          <div
            class="ais-SearchBox-submit"
            @click="selectArticle()"
            title="搜索"
            :class="{ 'search-active': articleSearch }"
          >
            <svg
              style="vertical-align: middle; margin-left: 18px"
              viewBox="0 0 1024 1024"
              width="20"
              height="20"
            >
              <path
                d="M51.2 508.8c0 256.8 208 464.8 464.8 464.8s464.8-208 464.8-464.8-208-464.8-464.8-464.8-464.8 208-464.8 464.8z"
                fill="#51C492"
              ></path>
              <path
                d="M772.8 718.4c48-58.4 76.8-132.8 76.8-213.6 0-186.4-151.2-337.6-337.6-337.6-186.4 0-337.6 151.2-337.6 337.6 0 186.4 151.2 337.6 337.6 337.6 81.6 0 156-28.8 213.6-76.8L856 896l47.2-47.2-130.4-130.4zM512 776c-149.6 0-270.4-121.6-270.4-271.2S363.2 233.6 512 233.6c149.6 0 271.2 121.6 271.2 271.2C782.4 654.4 660.8 776 512 776z"
                fill="#FFFFFF"
              ></path>
            </svg>
          </div>
          <div
            class="ais-SearchBox-clear"
            v-if="articleSearch"
            @click="clearSearch"
            title="清除"
          >
            <svg viewBox="0 0 24 24" width="14" height="14">
              <path
                d="M12 10.586l4.95-4.95 1.414 1.414-4.95 4.95 4.95 4.95-1.414 1.414-4.95-4.95-4.95 4.95-1.414-1.414 4.95-4.95-4.95-4.95 1.414-1.414 4.95 4.95z"
              ></path>
            </svg>
          </div>
        </div>

        <div
          class="search-tooltip"
          v-if="showSearchTips || (articleSearch && articleSearch.length >= 45)"
        >
          <div class="tooltip-content">
            <div class="tooltip-icon">💡</div>
            <div class="tooltip-text">
              <div>支持多关键词搜索，空格分隔</div>
              <div>例如：<span class="search-keyword">诗词 唐朝</span></div>
              <div>支持正则表达式搜索，用 / 包围</div>
              <div>例如：<span class="search-keyword">/^春.*诗$/</span></div>
              <div
                v-if="articleSearch && articleSearch.length >= 45"
                style="color: #ff7a7a; margin-top: 5px"
              >
                <el-icon><el-icon-warning /></el-icon> 搜索关键词限制为50字符
              </div>
            </div>
            <div class="tooltip-close" @click="hideSearchTips" title="关闭提示">
              <el-icon><el-icon-close /></el-icon>
            </div>
          </div>
        </div>

        <div v-if="recentSearches.length > 0" class="recent-searches">
          <div class="recent-search-title">
            最近搜索
            <span
              class="clear-history"
              @click="clearSearchHistory"
              title="清空历史"
            >
              <el-icon><el-icon-delete /></el-icon>
            </span>
          </div>
          <div class="recent-search-tags">
            <span
              v-for="(search, index) in displayedSearches"
              :key="index"
              @click="useRecentSearch(search)"
              class="recent-search-tag"
            >
              {{ search }}
            </span>
          </div>
        </div>
      </div>

      <!-- 推荐文章 -->
      <div
        v-if="!$common.isEmpty(recommendArticles)"
        style="
          padding: 25px;
          border-radius: 10px;
          animation: hideToShow 1s ease-in-out;
        "
        class="shadow-box background-opacity"
        v-animate
      >
        <div class="card-content2-title">
          <span>🔥推荐文章</span>
        </div>
        <div
          v-for="(article, index) in recommendArticles"
          :key="article.id"
          @click="goToArticle(article)"
        >
          <div class="aside-post-detail">
            <div class="aside-post-image">
              <el-image
                lazy
                class="my-el-image"
                :src="getArticleCoverUrl(article)"
                fit="cover"
                @error="handleCoverError(article)"
              >
                <template v-slot:error>
                  <div class="image-slot">
                    <div class="error-aside-image">
                      {{ article.username }}
                    </div>
                  </div>
                </template>
              </el-image>
              <div class="hasVideo transformCenter" v-if="article.hasVideo">
                <svg viewBox="0 0 1024 1024" width="30" height="30">
                  <path
                    d="M514 114.3c-219.9 0-398.9 178.9-398.9 398.9 0.1 219.9 179 398.8 398.9 398.8 219.9 0 398.8-178.9 398.8-398.8S733.9 114.3 514 114.3z m173 421.9L437.1 680.5c-17.7 10.2-39.8-2.6-39.8-23V368.9c0-20.4 22.1-33.2 39.8-23L687 490.2c17.7 10.2 17.7 35.8 0 46z"
                    fill="#0C0C0C"
                  ></path>
                </svg>
              </div>
            </div>
            <div class="aside-post-title">
              {{ article.articleTitle }}
            </div>
          </div>
          <div class="aside-post-date">
            <el-icon style="color: var(--greyFont)"><el-icon-date /></el-icon
            >{{ article.createTime }}
          </div>
        </div>
      </div>

      <!-- 速览 -->
      <div v-if="!$common.mobile()" class="selectSort">
        <div
          v-for="(sort, index) in sortInfo"
          @click="selectSort(sort)"
          :key="index"
          :style="{
            background: $constant.sortColor[index % $constant.sortColor.length],
          }"
          class="shadow-box-mini background-opacity"
          v-animate
          style="
            position: relative;
            padding: 10px 25px 15px;
            border-radius: 10px;
            animation: hideToShow 1s ease-in-out;
            cursor: pointer;
            color: var(--white);
          "
        >
          <div>速览</div>
          <div class="sort-name">
            {{ sort.sortName }}
          </div>
          <div
            style="
              font-weight: bold;
              margin-top: 15px;
              white-space: nowrap;
              text-overflow: ellipsis;
              overflow: hidden;
            "
          >
            {{ sort.sortDescription }}
          </div>
        </div>
      </div>

      <!-- 分类 -->
      <div
        class="shadow-box background-opacity"
        v-if="false"
        v-animate
        style="
          padding: 25px 25px 5px;
          border-radius: 10px;
          animation: hideToShow 1s ease-in-out;
        "
      >
        <div class="card-content2-title">
          <el-icon class="card-content2-icon"
            ><el-icon-folder-opened
          /></el-icon>
          <span>分类</span>
        </div>
        <div
          v-for="(sort, index) in sortInfo"
          :key="index"
          class="post-sort"
          @click="$router.push('/sort/' + sort.id)"
        >
          <div>
            <span v-for="(s, i) in sort.sortName.split('')" :key="i">{{
              s
            }}</span>
          </div>
        </div>
      </div>

      <!-- 赞赏 -->
      <div
        class="shadow-box-mini background-opacity admire-box"
        v-if="!$common.isEmpty(admires) && false"
        v-animate
      >
        <div style="font-weight: bold; margin-bottom: 20px">🧨赞赏名单</div>
        <div>
          <Vue3SeamlessScroll
            :list="admires"
            style="height: 200px; overflow: hidden"
          >
            <div
              v-for="(item, i) in admires"
              style="display: flex; justify-content: space-between"
              :key="i"
            >
              <div style="display: flex">
                <el-avatar
                  style="margin-bottom: 10px"
                  :size="36"
                  :src="$common.getAvatarUrl(item.avatar)"
                >
                  <img :src="$getDefaultAvatar()" />
                </el-avatar>
                <div
                  style="
                    margin-left: 10px;
                    height: 36px;
                    line-height: 36px;
                    overflow: hidden;
                    max-width: 80px;
                  "
                >
                  {{ item.username }}
                </div>
              </div>
              <div style="height: 36px; line-height: 36px">
                {{ item.admire }}元
              </div>
            </div>
          </Vue3SeamlessScroll>
        </div>
        <div class="admire-btn" @click="showAdmire()">赞赏</div>
      </div>
    </div>

    <!-- 微信 -->
    <el-dialog
      title="赞赏"
      v-model="showAdmireDialog"
      width="25%"
      :append-to-body="true"
      custom-class="centered-dialog"
      destroy-on-close
      center
    >
      <div>
        <div class="admire-image"></div>
        <div>
          <div class="admire-content">1. 感谢老铁送来的666</div>
          <div class="admire-content">
            2.
            申请通过后会加博客交流群，不需要加群或者退群后会定期清理好友（强迫症福利）
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../utils/gogocodeTransfer'
import {
  Warning as ElIconWarning,
  Close as ElIconClose,
  Delete as ElIconDelete,
  Calendar as ElIconDate,
  FolderOpened as ElIconFolderOpened,
} from '@element-plus/icons-vue'
import { Vue3SeamlessScroll } from 'vue3-seamless-scroll'
import { useMainStore } from '@/stores/main'
import { getArticlePath } from '@/utils/article-url'
import { useArticleCover } from '@/composables/useArticleCover'

export default {
  components: {
    Vue3SeamlessScroll,
    ElIconWarning,
    ElIconClose,
    ElIconDelete,
    ElIconDate,
    ElIconFolderOpened,
  },
  setup() {
    const { getArticleCoverUrl, handleCoverError } = useArticleCover()
    return { getArticleCoverUrl, handleCoverError }
  },
  data() {
    return {
      pagination: {
        current: 1,
        size: 5,
        recommendStatus: true,
      },
      recommendArticles: [],
      admires: [],
      showAdmireDialog: false,
      articleSearch: '',
      showSearchTips: false,
      searchTipsTimer: null,
      recentSearches: [],
      contactList: [], // 联系方式（小图标）
      quickEntryList: [], // 快捷入口（大按钮，可自定义）
      asideBackgroundImage: '', // 侧边栏主背景
      asideExtraBackground: '', // 侧边栏额外背景层
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    webInfo() {
      return this.mainStore.webInfo
    },
    sortInfo() {
      return this.mainStore.navigationBar
    },
    displayedSearches() {
      // 只显示前8个最近搜索
      return this.recentSearches.slice(0, 8)
    },
    asideBackgroundStyle() {
      if (this.asideBackgroundImage) {
        const layers = []

        // 处理额外背景层（如果有）
        if (this.asideExtraBackground) {
          const extraTrimmed = this.asideExtraBackground.trim()
          if (this.isCssBackground(extraTrimmed)) {
            layers.push(extraTrimmed)
          } else {
            layers.push(`url(${extraTrimmed})`)
          }
        }

        // 处理主背景层
        const mainTrimmed = this.asideBackgroundImage.trim()
        if (this.isCssBackground(mainTrimmed)) {
          layers.push(mainTrimmed)
        } else {
          layers.push(`url(${mainTrimmed})`)
        }

        // 如果有多层背景，使用逗号连接
        if (layers.length > 0) {
          return {
            background: layers.join(', '),
            backgroundRepeat: 'no-repeat',
            backgroundAttachment: 'inherit',
            backgroundSize: '100%',
          }
        }
      }
      return {}
    },
  },
  created() {
    // 同步操作：加载本地搜索历史
    this.loadRecentSearches()

    // 异步并行请求所有数据
    this.fetchAllData()
  },
  methods: {
    // 头像旋转动画处理
    handleAvatarHover(event) {
      event.target.style.transform = 'rotate(360deg)'
    },
    handleAvatarLeave(event) {
      event.target.style.transform = 'rotate(0deg)'
    },

    // 异步并行获取所有数据
    async fetchAllData() {
      try {
        await Promise.all([
          this.getRecommendArticles(),
          this.getAdmire(),
          this.getContactList(),
          this.getAsideBackground(),
        ])
      } catch (error) {
        console.error('获取侧边栏数据失败:', error)
      }
    },

    // 判断是否为CSS背景代码
    isCssBackground(str) {
      return (
        str.includes('linear-gradient') ||
        str.includes('radial-gradient') ||
        str.includes('repeating-') ||
        str.startsWith('#') ||
        str.startsWith('rgb')
      )
    },

    // 图标库 - 定义各种图标
    getIconSvg(iconName) {
      const icons = {
        xiaoche: `<svg class="icon-xiaoche" viewBox="0 0 1024 1024" width="28" height="28">
    <path d="M766.976 508.736c80.576 0 152.448 32.128 199.232 82.176" fill="#AEBCC3"></path>
    <path d="M64.704 684.992c10.816 19.2 32.064 32.192 56.576 32.192h784.64c35.84 0 64.832-27.648 64.832-61.76v-17.408h-36.608a15.744 15.744 0 0 1-16.064-15.296V550.912a277.568 277.568 0 0 0-150.144-44.16h1.6l-55.04-0.256c-53.632-115.2-157.504-210.752-294.208-210.752-136.512 0-251.008 89.728-282.176 210.688h-16.832c-35.456 0-56.128 27.392-56.128 61.184" fill="#E8447A"></path>
    <path d="M64.704 654.464h13.76a39.168 39.168 0 0 0 40.064-38.272v-17.6c0-21.12-17.92-38.208-40.064-38.208h-13.376" fill="#F5BB1D"></path>
    <path d="M160 684.992a101.632 96.832 0 1 0 203.264 0 101.632 96.832 0 1 0-203.264 0Z" fill="#455963"></path>
    <path d="M218.88 684.992a42.752 40.768 0 1 0 85.504 0 42.752 40.768 0 1 0-85.504 0Z" fill="#AEBCC3"></path>
    <path d="M652.032 684.992a101.568 96.832 0 1 0 203.136 0 101.568 96.832 0 1 0-203.136 0Z" fill="#455963"></path>
    <path d="M710.912 684.992a42.752 40.768 0 1 0 85.504 0 42.752 40.768 0 1 0-85.504 0Z" fill="#AEBCC3"></path>
    <path d="M966.272 591.104v-0.192a257.92 257.92 0 0 0-48.192-40V622.72c0 8.448 7.232 15.296 16.064 15.296h36.608v-42.304l-4.48-4.608z" fill="#F5BB1D"></path>
    <path d="M405.568 335.616c-104.896 6.336-191.296 76.8-216.64 170.816h216.64V335.616z" fill="#631536"></path>
    <path d="M445.696 506.432h216.64c-41.216-86.848-117.12-159.616-216.64-170.048v170.048z" fill="#631536"></path>
  </svg>`,
        star: `<svg class="icon-star" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="16" height="16"><path fill="currentColor" d="m512 747.84 228.16 119.936a6.4 6.4 0 0 0 9.28-6.72l-43.52-254.08 184.512-179.904a6.4 6.4 0 0 0-3.52-10.88l-255.104-37.12L517.76 147.904a6.4 6.4 0 0 0-11.52 0L392.192 379.072l-255.104 37.12a6.4 6.4 0 0 0-3.52 10.88L318.08 606.976l-43.584 254.08a6.4 6.4 0 0 0 9.28 6.72zM313.6 924.48a70.4 70.4 0 0 1-102.144-74.24l37.888-220.928L88.96 472.96A70.4 70.4 0 0 1 128 352.896l221.76-32.256 99.2-200.96a70.4 70.4 0 0 1 126.208 0l99.2 200.96 221.824 32.256a70.4 70.4 0 0 1 39.04 120.064L774.72 629.376l37.888 220.928a70.4 70.4 0 0 1-102.144 74.24L512 820.096l-198.4 104.32z"></path></svg>`,
        heart: `<svg class="icon-heart" viewBox="0 0 1024 1024" width="16" height="16">
    <path d="M512 896L85.333333 469.333333C0 384 0 234.666667 85.333333 149.333333c85.333333-85.333333 234.666667-85.333333 320 0L512 256l106.666667-106.666667c85.333333-85.333333 234.666667-85.333333 320 0 85.333333 85.333333 85.333333 234.666667 0 320L512 896z" fill="#FF6B9D"></path>
  </svg>`,
        rocket: `<svg class="icon-rocket" viewBox="0 0 1024 1024" width="18" height="18">
    <path d="M928 0C832 192 704 320 512 384c-64-64-128-96-192-96-128 0-256 96-256 256 0 32 32 64 64 64s64-32 64-64c0-96 64-160 128-160 32 0 96 32 128 64-64 192-192 320-384 416 96-192 224-320 416-384 64 192 192 320 384 416-96-192-224-320-384-416C672 288 800 160 992 64L928 0zM256 704c-35.2 0-64 28.8-64 64s28.8 64 64 64 64-28.8 64-64-28.8-64-64-64z" fill="#4A90E2"></path>
  </svg>`,
      }
      return icons[iconName] || ''
    },

    // 解析图标占位符，如：前往朋友圈[xiaoche] -> 前往朋友圈<svg...>
    parseIconPlaceholder(text) {
      if (!text) return ''

      // 匹配 [iconName] 格式
      return text.replace(/\[(\w+)\]/g, (match, iconName) => {
        const iconSvg = this.getIconSvg(iconName)
        return iconSvg || match // 如果找不到图标，保留原文
      })
    },

    // 移除图标占位符，如：前往朋友圈[xiaoche] -> 前往朋友圈
    removeIconPlaceholder(text) {
      if (!text) return ''
      // 移除所有 [xxx] 格式的占位符
      return text.replace(/\[(\w+)\]/g, '')
    },

    // 跳转到文章页面
    goToArticle(article) {
      // 使用简洁格式跳转到原文
      this.$router.push(getArticlePath(article))
    },

    selectSort(sort) {
      $emit(this, 'selectSort', sort)
    },
    selectArticle() {
      // 如果搜索框为空，返回到正常首页
      if (!this.articleSearch.trim()) {
        $emit(this, 'selectArticle', '') // 发送空字符串表示返回首页
        return
      }

      // 保存搜索记录
      this.saveSearch(this.articleSearch)

      $emit(this, 'selectArticle', this.articleSearch)
    },
    clearSearch() {
      this.articleSearch = ''
    },
    handleSearchInput() {
      // 检测是否按下ESC键
      if (event && event.keyCode === 27) {
        this.clearSearch()
      }

      // 输入时保持提示显示，重置定时器
      if (this.showSearchTips) {
        if (this.searchTipsTimer) {
          clearTimeout(this.searchTipsTimer)
        }
        // 重新设置定时器
        this.searchTipsTimer = setTimeout(() => {
          this.showSearchTips = false
        }, 8000)
      }
    },
    useRecentSearch(search) {
      this.articleSearch = search
      this.selectArticle()
    },
    hideSearchTips() {
      this.showSearchTips = false
      if (this.searchTipsTimer) {
        clearTimeout(this.searchTipsTimer)
        this.searchTipsTimer = null
      }
    },
    showSearchTipsOnFocus() {
      // 输入框获得焦点时显示搜索提示
      this.showSearchTips = true
      // 清除之前的定时器
      if (this.searchTipsTimer) {
        clearTimeout(this.searchTipsTimer)
      }
      // 设置较长的显示时间，让用户有足够时间阅读
      this.searchTipsTimer = setTimeout(() => {
        this.showSearchTips = false
      }, 10000) // 10秒后自动隐藏
    },
    hideSearchTipsOnBlur() {
      // 输入框失去焦点时延迟隐藏提示（给用户时间点击提示内容）
      setTimeout(() => {
        if (!this.articleSearch || this.articleSearch.length < 45) {
          this.showSearchTips = false
          if (this.searchTipsTimer) {
            clearTimeout(this.searchTipsTimer)
          }
        }
      }, 200) // 200ms延迟，避免点击提示时立即隐藏
    },
    loadRecentSearches() {
      const searches = localStorage.getItem('recentSearches')
      if (searches) {
        this.recentSearches = JSON.parse(searches)
      }
    },
    saveSearch(search) {
      search = search.trim()
      if (!search) return

      let searches = this.recentSearches
      // 如果已存在，先移除
      const index = searches.indexOf(search)
      if (index !== -1) {
        searches.splice(index, 1)
      }

      // 添加到最前面
      searches.unshift(search)

      // 限制保存8个
      if (searches.length > 8) {
        searches = searches.slice(0, 8)
      }

      this.recentSearches = searches
      localStorage.setItem('recentSearches', JSON.stringify(searches))
    },
    showAdmire() {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message({
          message: '请先登录！',
          type: 'error',
        })
        return
      }

      this.showAdmireDialog = true
    },
    getAdmire() {
      return this.$http
        .get(this.$constant.baseURL + '/webInfo/getAdmire')
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.admires = res.data
          }
        })
        .catch((error) => {
          console.error('获取赞赏名单失败:', error)
        })
    },
    getRecommendArticles() {
      return this.$http
        .post(this.$constant.baseURL + '/article/listArticle', this.pagination)
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.recommendArticles = res.data.records
          }
        })
        .catch((error) => {
          console.error('获取推荐文章失败:', error)
        })
    },
    showTip() {
      this.$router.push({ path: '/weiYan' })
    },
    getContactList() {
      // 从资源聚合中获取type为contact和quickEntry且启用的联系方式和快捷入口
      return Promise.all([
        this.$http.post(this.$constant.baseURL + '/webInfo/listResourcePath', {
          current: 1,
          size: 100,
          resourceType: 'contact',
          status: true,
        }),
        this.$http.post(this.$constant.baseURL + '/webInfo/listResourcePath', {
          current: 1,
          size: 100,
          resourceType: 'quickEntry',
          status: true,
        }),
      ])
        .then((results) => {
          // 分别存储联系方式和快捷入口
          if (
            !this.$common.isEmpty(results[0].data) &&
            !this.$common.isEmpty(results[0].data.records)
          ) {
            this.contactList = results[0].data.records
          }
          if (
            !this.$common.isEmpty(results[1].data) &&
            !this.$common.isEmpty(results[1].data.records)
          ) {
            // 后端已经解析好了快捷入口的按钮样式，直接使用即可
            this.quickEntryList = results[1].data.records
          }
        })
        .catch((error) => {
          console.error('获取联系方式和快捷入口失败:', error)
        })
    },
    getAsideBackground() {
      // 获取侧边栏背景配置
      return this.$http
        .post(this.$constant.baseURL + '/webInfo/listResourcePath', {
          current: 1,
          size: 1,
          resourceType: 'asideBackground',
          status: true,
        })
        .then((res) => {
          if (
            !this.$common.isEmpty(res.data) &&
            !this.$common.isEmpty(res.data.records) &&
            res.data.records.length > 0
          ) {
            const bgConfig = res.data.records[0]
            this.asideBackgroundImage = bgConfig.cover // 主背景
            this.asideExtraBackground = bgConfig.extraBackground || '' // 额外背景层（后端已解析）
          }
        })
        .catch((error) => {
          console.error('获取侧边栏背景失败:', error)
        })
    },
    clearSearchHistory() {
      this.$confirm('确定要清空搜索历史记录吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          this.recentSearches = []
          localStorage.removeItem('recentSearches')
          this.$message({
            type: 'success',
            message: '搜索历史已清空',
          })
        })
        .catch(() => {
          // 用户取消清空操作
        })
    },
  },
  emits: ['selectSort', 'selectArticle'],
}
</script>

<style scoped>
.myAside-container > div:not(:last-child) {
  margin-bottom: 30px;
}
.selectSort > div:not(:last-child) {
  margin-bottom: 30px;
}
.card-content1 {
  background: linear-gradient(
    -45deg,
    #e8d8b9,
    #eccec5,
    #a3e9eb,
    #bdbdf0,
    #eec1ea
  );
  background-size: 400% 400%;
  animation: gradientBG 10s ease infinite;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 10px;
  position: relative;
  overflow: hidden;
}
.card-content1 :not(:first-child) {
  z-index: 10;
}
.card-content1 :deep(.user-avatar){
  cursor: pointer;
  transition: transform 0.6s ease;
  will-change: transform;
  transform: translateZ(0);
}
.card-content1 :deep(.user-avatar:hover){
  transform: rotate(360deg);
}
.web-name {
  font-size: 30px;
  font-weight: bold;
  margin: 20px 0;
  color: #333333;
}
.web-info {
  width: 80%;
  display: flex;
  flex-direction: row;
  justify-content: space-around;
}
.blog-info-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-around;
  color: #333333;
}
.blog-info-num {
  margin-top: 12px;
  color: #333333;
}
.collection-btn {
  position: relative;
  margin-top: 12px;
  background: var(--lightGreen);
  cursor: pointer;
  width: 65%;
  height: 35px;
  border-radius: 1rem;
  text-align: center;
  line-height: 35px;
  color: var(--white);
  overflow: hidden;
  z-index: 1;
  margin-bottom: 12px;
  text-decoration: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.collection-btn.has-contact {
  margin-bottom: 10px;
}
.collection-btn:last-of-type:not(.has-contact) {
  margin-bottom: 25px;
}
.collection-btn::before {
  background: var(--gradualRed);
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  content: '';
  transform: scaleX(0);
  transform-origin: 0;
  transition: transform 0.5s ease-out;
  transition-timing-function: cubic-bezier(0.45, 1.64, 0.47, 0.66);
  border-radius: 1rem;
  z-index: -1;
}
.collection-btn:hover::before {
  transform: scaleX(1);
}
.collection-btn > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  line-height: 1;
}
.collection-btn :deep(.icon-xiaoche),
.collection-btn :deep(.icon-star),
.collection-btn :deep(.icon-heart),
.collection-btn :deep(.icon-rocket),
.collection-btn :deep(.el-icon-star-off){
  margin-left: 4px;
  font-variant: normal;
  text-transform: none;
  line-height: 1;
  vertical-align: middle;
  display: inline-block;
  -webkit-font-smoothing: antialiased;
  flex-shrink: 0;
}
.collection-btn :deep(.icon-xiaoche){
  animation: carMove 2s linear infinite;
}
.collection-btn :deep(.icon-heart){
  animation: heartBeat 1.5s ease-in-out infinite;
}
.collection-btn :deep(.icon-rocket){
  animation: rocketFly 2.5s ease-in-out infinite;
}
.contact-container {
  width: 80%;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 20px;
  z-index: 10;
}
.contact-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  text-decoration: none;
  margin: 0px 10px;
}
.contact-item:hover {
  animation: scaleAndShake 2.5s ease-in-out infinite;
}
@keyframes scaleAndShake {
  0% {
    transform: scale(1) rotate(0deg);
  }
  10% {
    transform: scale(1.15) rotate(0deg);
  }
  18% {
    transform: scale(1.15) rotate(-8deg);
  }
  26% {
    transform: scale(1.15) rotate(8deg);
  }
  34% {
    transform: scale(1.15) rotate(-8deg);
  }
  42% {
    transform: scale(1.15) rotate(8deg);
  }
  50% {
    transform: scale(1.15) rotate(0deg);
  }
  60% {
    transform: scale(1) rotate(0deg);
  }
  100% {
    transform: scale(1) rotate(0deg);
  }
}
.contact-icon {
  object-fit: cover;
  display: block;
}
.contact-text {
  font-size: 0.7em;
  color: #333;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  text-align: center;
  padding: 0 2px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.contact-text :deep(.icon-xiaoche),
.contact-text :deep(.icon-star),
.contact-text :deep(.icon-heart),
.contact-text :deep(.icon-rocket),
.contact-text :deep(.el-icon-star-off){
  margin-right: 2px;
  font-family: element-icons !important;
  font-variant: normal;
  text-transform: none;
  line-height: 1;
  vertical-align: baseline;
  display: inline-block;
  -webkit-font-smoothing: antialiased;
}
.contact-text :deep(.icon-xiaoche){
  animation: carMove 2s linear infinite;
}
@keyframes carMove {
  0% {
    transform: translateX(-10px);
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
  50% {
    transform: translateX(0px);
    opacity: 1;
  }
  85% {
    opacity: 1;
  }
  100% {
    transform: translateX(20px);
    opacity: 0;
  }
}
.contact-text :deep(.icon-heart){
  animation: heartBeat 1.5s ease-in-out infinite;
}
@keyframes heartBeat {
  0%,
  100% {
    transform: scale(1);
  }
  25% {
    transform: scale(1.1);
  }
  50% {
    transform: scale(1);
  }
  75% {
    transform: scale(1.15);
  }
}
.contact-text :deep(.icon-rocket){
  animation: rocketFly 2.5s ease-in-out infinite;
}
@keyframes rocketFly {
  0% {
    transform: translateY(0px) rotate(0deg);
  }
  25% {
    transform: translateY(-5px) rotate(-5deg);
  }
  50% {
    transform: translateY(0px) rotate(0deg);
  }
  75% {
    transform: translateY(-3px) rotate(5deg);
  }
  100% {
    transform: translateY(0px) rotate(0deg);
  }
}
.card-content2-title {
  font-size: 18px;
  margin-bottom: 20px;
  color: var(--lightGreen);
  font-weight: bold;
}
.card-content2-icon {
  color: var(--red);
  margin-right: 5px;
  animation: scale 1s ease-in-out infinite;
}
.aside-post-detail {
  display: flex;
  cursor: pointer;
}
.aside-post-image {
  width: 40%;
  min-height: 50px;
  border-radius: 6px;
  margin-right: 8px;
  overflow: hidden;
  position: relative;
}
.error-aside-image {
  background: var(--themeBackground);
  color: var(--white);
  padding: 10px;
  text-align: center;
  width: 100%;
  height: 100%;
}
.aside-post-title {
  width: 60%;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
  line-height: 1.5;
}
.aside-post-date {
  margin-top: 0.5em;
  margin-bottom: 1.2em;
  color: var(--greyFont);
  font-size: 12px;
  line-height: 1.5;
}
.post-sort {
  border-radius: 1rem;
  margin-bottom: 15px;
  line-height: 1.8;
  transition: color 0.3s ease, background-color 0.3s ease;
}
.post-sort:hover {
  background: var(--themeBackground);
  padding: 2px 15px;
  cursor: pointer;
  color: var(--white);
}
.sort-name {
  font-weight: bold;
  font-size: 25px;
  position: relative;
  padding-bottom: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}
.sort-name:after {
  bottom: 0;
  width: 22px;
  left: 0;
  height: 2px;
  background: var(--white);
  content: '';
  border-radius: 1px;
  position: absolute;
}
.admire-box {
  background: var(--springBg) center center / cover no-repeat;
  padding: 25px;
  border-radius: 10px;
  animation: hideToShow 1s ease-in-out;
}
.admire-btn {
  padding: 13px 15px;
  background: var(--maxLightRed);
  border-radius: 3rem;
  color: var(--white);
  width: 100px;
  user-select: none;
  cursor: pointer;
  text-align: center;
  margin: 20px auto 0;
  transition: transform 1s ease, opacity 1s ease;
  will-change: transform;
  transform: translateZ(0);
}
.admire-btn:hover {
  transform: scale(1.2);
}
.admire-image {
  margin: 0 auto 10px;
  border-radius: 10px;
  height: 150px;
  width: 150px;
  background: var(--admireImage) center center / cover no-repeat;
}
.admire-content {
  font-size: 12px;
  color: var(--maxGreyFont);
  line-height: 1.5;
  margin: 5px;
}
.search-container {
  padding: 20px;
  border-radius: 10px;
  animation: hideToShow 1s ease-in-out;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  position: relative;
}
.search-title {
  color: var(--lightGreen);
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 15px;
  position: relative;
  padding-left: 10px;
}
.search-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  background-color: var(--lightGreen);
  border-radius: 3px;
}
.search-box {
  position: relative;
  display: flex;
  margin-bottom: 10px;
}
.ais-SearchBox-input {
  padding: 0 14px;
  height: 34px;
  width: calc(100% - 50px);
  outline: 0;
  border: 2px solid var(--lightGreen);
  border-right: 0;
  border-radius: 40px 0 0 40px;
  color: var(--maxGreyFont);
  background: var(--white);
  line-height: 34px;
}
.ais-SearchBox-submit {
  height: 34px;
  width: 50px;
  border: 2px solid var(--lightGreen);
  border-left: 0;
  border-radius: 0 40px 40px 0;
  background: var(--white);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ais-SearchBox-clear {
  position: absolute;
  right: 60px;
  top: 9px;
  cursor: pointer;
  color: var(--greyFont);
  transition: color 0.3s ease, background-color 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  opacity: 0.6;
}
.ais-SearchBox-clear:hover {
  opacity: 1;
  transform: scale(1.1);
}
.search-tooltip {
  margin-top: 10px;
  background: rgba(81, 196, 146, 0.1);
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 10px;
}
.tooltip-content {
  display: flex;
  align-items: flex-start;
}
.tooltip-icon {
  margin-right: 8px;
  font-size: 16px;
}
.tooltip-text {
  font-size: 12px;
  color: var(--greyFont);
  line-height: 1.5;
}
.search-keyword {
  color: var(--lightGreen);
  font-weight: bold;
  background: rgba(81, 196, 146, 0.15);
  padding: 0 4px;
  border-radius: 2px;
}
.recent-searches {
  margin-top: 15px;
  border-top: 1px dashed rgba(0, 0, 0, 0.1);
  padding-top: 10px;
}
.recent-search-title {
  font-size: 12px;
  color: var(--greyFont);
  margin-bottom: 8px;
  position: relative;
  padding-left: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.clear-history {
  cursor: pointer;
  color: var(--greyFont);
  opacity: 0.7;
  transition: color 0.3s ease, opacity 0.3s ease;
  padding: 2px 5px;
}
.clear-history:hover {
  opacity: 1;
  color: var(--lightGreen);
}
.recent-search-tags {
  display: flex;
  flex-wrap: wrap;
}
.recent-search-tag {
  font-size: 12px;
  color: var(--greyFont);
  background: rgba(81, 196, 146, 0.08);
  border-radius: 12px;
  padding: 3px 10px;
  margin-right: 6px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: color 0.3s ease, background-color 0.3s ease;
  display: inline-flex;
  align-items: center;
}
.recent-search-tag:hover {
  background: rgba(81, 196, 146, 0.2);
  color: var(--lightGreen);
  transform: translateY(-2px);
}
.tooltip-close {
  position: absolute;
  top: 8px;
  right: 8px;
  cursor: pointer;
  color: var(--greyFont);
  opacity: 0.6;
  transition: color 0.3s ease, opacity 0.3s ease;
  padding: 2px;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.tooltip-close:hover {
  opacity: 1;
  background: rgba(255, 0, 0, 0.1);
  color: #ff4757;
}
.tooltip-content {
  position: relative;
}
.dark-mode .search-container {
  background: rgba(39, 39, 39, 0.8);
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.3);
}
.dark-mode .ais-SearchBox-input {
  background: rgba(56, 56, 56, 0.8);
  color: #e0e0e0;
  border-color: var(--lightGreen);
}
.dark-mode .ais-SearchBox-input::placeholder {
  color: #888;
}
.dark-mode .ais-SearchBox-submit {
  background: rgba(56, 56, 56, 0.8);
  border-color: var(--lightGreen);
}
.dark-mode .search-tooltip {
  background: rgba(81, 196, 146, 0.15);
}
.dark-mode .tooltip-text {
  color: #b0b0b0;
}
.dark-mode .search-keyword {
  color: var(--lightGreen);
  background: rgba(81, 196, 146, 0.2);
}
.dark-mode .recent-searches {
  border-top-color: rgba(255, 255, 255, 0.1);
}
.dark-mode .recent-search-title {
  color: #b0b0b0;
}
.dark-mode .recent-search-tag {
  background: rgba(81, 196, 146, 0.15);
  color: #b0b0b0;
}
.dark-mode .recent-search-tag:hover {
  background: rgba(81, 196, 146, 0.25);
  color: var(--lightGreen);
}
.dark-mode .clear-history {
  color: #888;
}
.dark-mode .clear-history:hover {
  color: var(--lightGreen);
}
.dark-mode .tooltip-close {
  color: #888;
}
.dark-mode .tooltip-close:hover {
  background: rgba(255, 0, 0, 0.2);
  color: #ff6b81;
}
</style>

<style>
/* 侧边栏头像旋转动画 */
.user-avatar {
  cursor: pointer !important;
  transition: transform 0.4s ease !important;
}

.user-avatar:hover {
  transform: rotate(360deg) !important;
}
</style>
