<template>
  <div v-if="!$common.isEmpty(articleList)" class="recent-post-container">
    <div class="article-first">
      <svg
        viewBox="0 0 1024 1024"
        width="20"
        height="20"
        style="vertical-align: -0.15em"
      >
        <path
          d="M224.2 472.3c-13-5.7-3.7-23.5 8.2-19 91 34 146.8 108.7 182.4 138.5 5.6 4.7 14 2.9 17.3-3.5 16.8-32 45.8-113.7-57.1-168.6-87.3-46.5-188-53.6-247.3-82.2-14.5-7-31.1 4.6-29.9 20.7 5 69.7 28.9 124.7 62.3 181.5 67.3 114.3 140.6 132.9 216.6 104 2.2-0.9 4.5-1.8 7-3 7-3.4 8.3-12.9 2.5-18.1 0.1 0-45.7-69.3-162-150.3z"
          fill="#FFD401"
        ></path>
        <path
          d="M282.7 849.9c79.5-137 172.4-263.1 385.4-401.3 9.8-6.4 2.1-21.5-8.9-17.4C497.7 492.8 429.7 585 373.3 640.8c-8.7 8.7-23.4 6.3-29.1-4.6-27.2-51.8-69.5-174.1 97.3-263.1 147.7-78.8 319.9-91.4 429.7-93.3 18.9-0.3 31.5 19.4 23.3 36.4C863.7 380 842.6 478 789.9 567.6 680.8 753.1 545.5 766.7 422.2 719.8c-8.8-3.4-18.8-0.2-24 7.7-16.6 25.2-50.3 80.1-58.7 122.4-11.4 56.8-82.2 43.9-56.8 0z"
          fill="#8BC03C"
        ></path>
        <path
          d="M375 419.6c-30.1 28.2-45.8 57.7-52.4 86.1 40.6 32.4 70.2 67.7 92.1 85.9 1.2 1 2.5 1.6 3.9 2.1 6.5-6.7 13.3-13.7 20.4-20.7 15.2-37.9 25.3-105.7-64-153.4zM318.8 548.2c1.6 36.1 14.7 67.6 25.5 88.1 5.7 10.9 20.3 13.3 29.1 4.6 4.9-4.9 10-10 15.1-15.4-0.6-1-1.3-2-2.2-2.8 0-0.1-20.1-30.5-67.5-74.5z"
          fill="#8BA000"
        ></path>
      </svg>
      发现
    </div>
    <div
      class="recent-post-item shadow-box background-opacity"
      v-for="(article, index) in articleList"
      :key="index"
      draggable="true"
      @dragstart="handleDragStart($event, article)"
      @click="goToArticle(article)"
    >
      <!-- 封面 -->
      <div
        class="recent-post-item-image"
        :class="{ leftImage: index % 2 !== 0, rightImage: index % 2 === 0 }"
      >
        <div class="image-container">
          <el-image
            class="my-el-image"
            v-once
            lazy
            :src="article.articleCover"
            fit="cover"
            :alt="article.articleTitle"
          >
            <!--
              安全说明：文章标题、正文和翻译内容仅由站长/管理员写入，普通用户没有发布入口。
              本组件保留 v-html 是为了渲染后端搜索高亮标签；若未来开放投稿、外部导入或非可信 AI 自动回写，需先接入 HTML sanitize。
            -->
            <template v-slot:error>
              <div
                class="image-slot myCenter"
                style="background-color: var(--lightGreen)"
              >
                <div class="error-text">
                  <div v-html="'「' + article.articleTitle + '」'"></div>
                </div>
              </div>
            </template>
          </el-image>
          <div class="transformCenter hasVideo" v-if="article.hasVideo">
            <svg viewBox="0 0 1024 1024" width="60" height="60">
              <path
                d="M514 114.3c-219.9 0-398.9 178.9-398.9 398.9 0.1 219.9 179 398.8 398.8 398.8 219.9 0 398.8-178.9 398.8-398.8S733.9 114.3 514 114.3z m173 421.9L437.1 680.5c-17.7 10.2-39.8-2.6-39.8-23V368.9c0-20.4 22.1-33.2 39.8-23L687 490.2c17.7 10.2 17.7 35.8 0 46z"
                fill="#0C0C0C"
              ></path>
            </svg>
          </div>
        </div>
      </div>
      <!-- 内容 -->
      <div
        class="recent-post-item-post"
        :class="{ leftImage: index % 2 === 0, rightImage: index % 2 !== 0 }"
      >
        <!-- 时间 -->
        <div class="post-meta">
          <svg
            viewBox="0 0 1024 1024"
            width="14"
            height="14"
            style="vertical-align: -0.15em"
          >
            <path
              d="M512 512m-512 0a512 512 0 1 0 1024 0 512 512 0 1 0-1024 0Z"
              fill="#409EFF"
            ></path>
            <path
              d="M654.222222 256c-17.066667 0-28.444444 11.377778-28.444444 28.444444v56.888889c0 17.066667 11.377778 28.444444 28.444444 28.444445s28.444444-11.377778 28.444445-28.444445v-56.888889c0-17.066667-11.377778-28.444444-28.444445-28.444444zM369.777778 256c-17.066667 0-28.444444 11.377778-28.444445 28.444444v56.888889c0 17.066667 11.377778 28.444444 28.444445 28.444445s28.444444-11.377778 28.444444-28.444445v-56.888889c0-17.066667-11.377778-28.444444-28.444444-28.444444z"
              fill="#FFFFFF"
            ></path>
            <path
              d="M725.333333 312.888889H711.111111v28.444444c0 31.288889-25.6 56.888889-56.888889 56.888889s-56.888889-25.6-56.888889-56.888889v-28.444444h-170.666666v28.444444c0 31.288889-25.6 56.888889-56.888889 56.888889s-56.888889-25.6-56.888889-56.888889v-28.444444h-14.222222c-22.755556 0-42.666667 19.911111-42.666667 42.666667v341.333333c0 22.755556 19.911111 42.666667 42.666667 42.666667h426.666666c22.755556 0 42.666667-19.911111 42.666667-42.666667v-341.333333c0-22.755556-19.911111-42.666667-42.666667-42.666667zM426.666667 654.222222h-56.888889c-17.066667 0-28.444444-11.377778-28.444445-28.444444s11.377778-28.444444 28.444445-28.444445h56.888889c17.066667 0 28.444444 11.377778 28.444444 28.444445s-11.377778 28.444444-28.444444 28.444444z m227.555555 0h-56.888889c-17.066667 0-28.444444-11.377778-28.444444-28.444444s11.377778-28.444444 28.444444-28.444445h56.888889c17.066667 0 28.444444 11.377778 28.444445 28.444445s-11.377778 28.444444-28.444445 28.444444z m0-113.777778h-56.888889c-17.066667 0-28.444444-11.377778-28.444444-28.444444s11.377778-28.444444 28.444444-28.444444h56.888889c17.066667 0 28.444444 11.377778 28.444445 28.444444s-11.377778 28.444444-28.444445 28.444444z"
              fill="#FFFFFF"
            ></path>
          </svg>
          发布于 {{ article.createTime }}
        </div>
        <!-- 标题 -->
        <el-tooltip placement="top" effect="light">
          <template v-slot:content>
            <div v-html="getDisplayTitle(article)"></div>
          </template>
          <h3 v-html="getDisplayTitle(article)"></h3>
        </el-tooltip>

        <!-- 信息 -->
        <div class="post-meta" style="margin-bottom: 0.9em">
          <span>
            <svg
              viewBox="0 0 1024 1024"
              width="14"
              height="14"
              style="vertical-align: -0.15em"
            >
              <path
                d="M14.656 512a497.344 497.344 0 1 0 994.688 0 497.344 497.344 0 1 0-994.688 0z"
                fill="#FF0000"
              ></path>
              <path
                d="M374.976 872.64c-48.299-100.032-22.592-157.44 14.421-211.37 40.448-58.966 51.115-117.611 51.115-117.611s31.659 41.386 19.115 106.005c56.149-62.72 66.816-162.133 58.325-200.405 127.317 88.746 181.59 281.002 108.181 423.381C1016 652.501 723.093 323.2 672.277 285.867c16.939 37.333 20.054 100.032-14.101 130.474-58.027-219.84-201.664-265.002-201.664-265.002 16.96 113.536-61.781 237.397-137.344 330.24-2.816-45.163-5.632-76.544-29.483-119.808-5.333 82.176-68.373 149.269-85.29 231.445-22.912 111.637 17.237 193.173 170.581 279.424z"
                fill="#FFFFFF"
              ></path>
            </svg>
            {{ article.viewCount }} 热度
          </span>
          <span>
            <svg
              viewBox="0 0 1024 1024"
              width="14"
              height="14"
              style="vertical-align: -0.15em"
            >
              <path
                d="M113.834667 291.84v449.194667a29.013333 29.013333 0 0 0 28.842666 29.013333h252.928v90.453333l160.597334-90.453333h252.928a29.013333 29.013333 0 0 0 29.013333-29.013333V291.84a29.013333 29.013333 0 0 0-29.013333-29.013333h-665.6a29.013333 29.013333 0 0 0-29.696 29.013333z"
                fill="#FFDEAD"
              ></path>
              <path
                d="M809.130667 262.826667h-665.6a29.013333 29.013333 0 0 0-28.842667 29.013333v40.106667a29.013333 29.013333 0 0 1 28.842667-29.013334h665.6a29.013333 29.013333 0 0 1 29.013333 29.013334V291.84a29.013333 29.013333 0 0 0-29.013333-29.013333z"
                fill="#FFF3DB"
              ></path>
              <path
                d="M556.202667 770.048h252.928a29.013333 29.013333 0 0 0 29.013333-29.013333V362.837333s-59.733333 392.533333-724.309333 314.709334v63.488a29.013333 29.013333 0 0 0 28.842666 29.013333h253.098667v90.453333z"
                fill="#F2C182"
              ></path>
              <path
                d="M619.008 632.32l101.888-35.157333-131.754667-76.117334 29.866667 111.274667zM891.904 148.992a61.44 61.44 0 0 0-84.138667 22.528l-19.968 34.133333 106.666667 61.610667 19.968-34.133333a61.781333 61.781333 0 0 0-22.528-84.138667z"
                fill="#69BAF9"
              ></path>
              <path
                d="M775.338667 198.775467l131.669333 76.032-186.026667 322.218666-131.6864-76.032z"
                fill="#F7FBFF"
              ></path>
              <path
                d="M775.168 198.826667l-5.290667 9.216 59.221334 34.133333a34.133333 34.133333 0 0 1 12.458666 46.592l-139.946666 242.346667a34.133333 34.133333 0 0 1-46.762667 12.629333l-59.050667-34.133333-6.656 11.434666 88.746667 51.2L720.896 597.333333l186.026667-322.56z"
                fill="#D8E3F0"
              ></path>
              <path
                d="M616.448 622.592l2.56 9.728 101.888-35.157333-44.885333-25.941334-59.562667 51.370667zM891.904 148.992c-1.024 0-2.218667-0.853333-3.242667-1.536A61.610667 61.610667 0 0 1 887.466667 204.8l-19.968 34.133333-73.728-42.496-5.12 8.704 106.666666 61.610667 19.968-34.133333a61.781333 61.781333 0 0 0-23.381333-83.626667z"
                fill="#599ED4"
              ></path>
              <path
                d="M265.898667 417.621333H494.933333a17.066667 17.066667 0 1 0 0-34.133333H265.898667a17.066667 17.066667 0 1 0 0 34.133333zM265.898667 533.504H494.933333a17.066667 17.066667 0 0 0 0-34.133333H265.898667a17.066667 17.066667 0 0 0 0 34.133333z"
                fill="#3D3D63"
              ></path>
              <path
                d="M959.488 354.645333a99.84 99.84 0 0 0-23.722667-127.488 78.677333 78.677333 0 0 0-142.848-64.170666l-11.605333 20.138666a17.066667 17.066667 0 0 0-20.821333 7.168l-32.085334 55.466667H142.677333a46.250667 46.250667 0 0 0-45.909333 46.08v449.194667a46.08 46.08 0 0 0 45.909333 46.08h236.032v73.386666a17.066667 17.066667 0 0 0 8.362667 14.848 17.066667 17.066667 0 0 0 8.704 2.218667 17.066667 17.066667 0 0 0 8.362667-2.218667l156.672-88.234666h248.32a46.08 46.08 0 0 0 46.08-46.08V398.677333L921.6 283.306667a17.066667 17.066667 0 0 0-4.266667-21.504l1.877334-3.413334a65.365333 65.365333 0 0 1 10.410666 79.189334l-53.077333 91.989333a56.832 56.832 0 0 0 20.821333 77.653333 17.066667 17.066667 0 0 0 24.234667-6.314666 17.066667 17.066667 0 0 0-6.997333-23.04 23.04 23.04 0 0 1-8.362667-31.061334z m-138.410667 386.389334a11.946667 11.946667 0 0 1-11.946666 11.946666H556.202667a17.066667 17.066667 0 0 0-8.362667 2.218667l-134.997333 76.117333v-61.269333a17.066667 17.066667 0 0 0-17.066667-17.066667H142.677333a11.946667 11.946667 0 0 1-11.776-11.946666V291.84a11.946667 11.946667 0 0 1 11.776-11.946667h565.930667L574.464 512a17.066667 17.066667 0 0 0-1.706667 12.970667L597.333333 615.253333H265.898667a17.066667 17.066667 0 1 0 0 34.133334h352.938666a17.066667 17.066667 0 0 0 5.802667 0l102.4-35.328a17.066667 17.066667 0 0 0 9.216-7.509334l85.333333-147.968z m-204.8-184.661334l63.829334 36.864-49.322667 17.066667z m206.848-170.666666v1.365333l-108.373333 186.709333-102.4-59.050666L781.482667 221.866667l102.4 59.050666z m76.458667-161.28L887.466667 244.224l-76.970667-44.373333 11.264-19.797334a44.544 44.544 0 1 1 77.141333 44.544z"
                fill="#3D3D63"
              ></path>
            </svg>
            {{ article.commentCount }} 条评论
          </span>
        </div>
        <!-- 内容 -->
        <div class="recent-post-desc" v-html="getDisplayContent(article)"></div>

        <!-- 翻译匹配提示 -->
        <div v-if="article.hasTranslationMatch" class="translation-match-tip">
          <el-tag
            size="mini"
            type="info"
            effect="plain"
            @click.stop="toggleTranslationView(article)"
            class="clickable-tag"
          >
            {{
              article.showTranslation
                ? '查看原文'
                : `查看${getLanguageName(
                    article.matchedLanguage
                  )}文章的匹配内容`
            }}
          </el-tag>
        </div>

        <!-- 查看原文链接 -->
        <div v-if="article.isTranslationMatch" class="view-original">
          <el-button
            type="text"
            size="mini"
            @click.stop="viewOriginal(article)"
          >
            <el-icon><el-icon-document /></el-icon> 查看原文
          </el-button>
        </div>
        <!-- 分类 标签 -->
        <div class="sort-label">
          <span
            style="margin-right: 12px"
            @click.stop="$router.push('/sort/' + article.sortId)"
          >
            <svg
              viewBox="0 0 1024 1024"
              width="15"
              height="15"
              style="vertical-align: -0.2em"
            >
              <path
                d="M179.2 153.6m89.6 0l588.8 0q89.6 0 89.6 89.6l0 486.4q0 89.6-89.6 89.6l-588.8 0q-89.6 0-89.6-89.6l0-486.4q0-89.6 89.6-89.6Z"
                fill="#FF9C55"
              ></path>
              <path
                d="M25.6 193.4208A91.0208 91.0208 0 0 1 116.6208 102.4H382.592a91.0208 91.0208 0 0 1 87.5008 65.9712l24.5504 85.7216h412.7488A91.0208 91.0208 0 0 1 998.4 345.1392v485.4528A91.0208 91.0208 0 0 1 907.3792 921.6H116.608A91.0208 91.0208 0 0 1 25.6 830.5792V193.408z"
                fill="#FFD977"
              ></path>
              <path
                d="M128 665.6m25.6 0l76.8 0q25.6 0 25.6 25.6l0 0q0 25.6-25.6 25.6l-76.8 0q-25.6 0-25.6-25.6l0 0q0-25.6 25.6-25.6Z"
                fill="#FFF1C9"
              ></path>
              <path
                d="M128 768m25.6 0l179.2 0q25.6 0 25.6 25.6l0 0q0 25.6-25.6 25.6l-179.2 0q-25.6 0-25.6-25.6l0 0q0-25.6 25.6-25.6Z"
                fill="#FFF1C9"
              ></path>
              <path
                d="M128 486.4m51.2 0l0 0q51.2 0 51.2 51.2l0 0q0 51.2-51.2 51.2l0 0q-51.2 0-51.2-51.2l0 0q0-51.2 51.2-51.2Z"
                fill="#FFA86A"
              ></path>
            </svg>
            {{ article.sort.sortName }}
          </span>
          <span
            @click.stop="
              $router.push(
                '/sort/' + article.sortId + '?labelId=' + article.labelId
              )
            "
          >
            <svg
              viewBox="0 0 1024 1024"
              width="15"
              height="15"
              style="vertical-align: -0.2em"
            >
              <path
                d="M905.0112 560.4352l-342.784 342.784c-56.7808 56.7808-148.7872 56.7808-205.568 0l-231.5776-231.5776c-56.7808-56.7808-56.7808-148.7872 0-205.568l342.9376-342.9376a114.8928 114.8928 0 0 1 84.224-33.5872l266.3936 7.2192c60.7744 1.6384 109.7216 50.3808 111.5648 111.1552l8.2944 267.8272c1.024 31.6928-11.1104 62.3104-33.4848 84.6848z"
                fill="#8C7BFD"
              ></path>
              <path
                d="M675.2256 491.4688c-82.176 0-149.0432-66.8672-149.0432-149.0432s66.8672-149.0432 149.0432-149.0432 149.0432 66.8672 149.0432 149.0432-66.8672 149.0432-149.0432 149.0432z m0-192.2048c-23.808 0-43.2128 19.3536-43.2128 43.2128 0 23.808 19.3536 43.2128 43.2128 43.2128 23.808 0 43.2128-19.3536 43.2128-43.2128s-19.4048-43.2128-43.2128-43.2128z"
                fill="#FFE37B"
              ></path>
            </svg>
            {{ article.label.labelName }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Document as ElIconDocument } from '@element-plus/icons-vue'
import { getLanguageName } from '@/utils/languageUtils'
import { getArticlePath } from '@/utils/article-url'

export default {
  components: {
    ElIconDocument,
  },
  props: {
    articleList: {
      type: Array,
    },
    searchKey: {
      type: String,
      default: '',
    },
  },
  methods: {
    // 处理拖拽开始事件
    handleDragStart(event, article) {
      // 构建文章的完整URL
      const baseUrl = window.location.origin
      let articlePath
      if (article.isTranslationMatch && article.matchedLanguage) {
        articlePath = getArticlePath(article, article.matchedLanguage)
      } else {
        articlePath = getArticlePath(article)
      }
      const articleUrl = `${baseUrl}${articlePath}`

      // 设置拖拽数据
      event.dataTransfer.effectAllowed = 'link'
      event.dataTransfer.setData('text/uri-list', articleUrl)
      event.dataTransfer.setData('text/plain', articleUrl)

      // 设置拖拽时显示的文本
      const title = this.getDisplayTitle(article)
      event.dataTransfer.setData(
        'text/html',
        `<a href="${articleUrl}">${title}</a>`
      )
    },

    getSummaryDisplay(article) {
      if (article.summary && article.summary.trim()) {
        return article.summary
      }

      // 摘要为空时，回退显示文章内容截取
      return this.$common.removeMarkdown(article.articleContent)
    },

    // 获取显示标题
    getDisplayTitle(article) {
      // 如果正在显示翻译内容且有翻译标题，显示翻译标题
      if (article.showTranslation && article.translationTitle) {
        return article.translationTitle
      }
      return article.articleTitle
    },

    // 获取显示内容
    getDisplayContent(article) {
      if (this.searchKey && this.searchKey.trim()) {
        // 如果正在显示翻译内容且有翻译内容，显示翻译内容
        if (article.showTranslation && article.translationContent) {
          return article.translationContent
        }
        return article.articleContent || ''
      }

      // 非搜索场景：优先显示摘要
      return this.getSummaryDisplay(article)
    },
    // 使用统一的语言映射工具
    getLanguageName,

    // 跳转到文章页面
    goToArticle(article) {
      let path
      if (article.isTranslationMatch && article.matchedLanguage) {
        // 如果是翻译匹配，使用语言路径格式
        path = getArticlePath(article, article.matchedLanguage)
      } else {
        // 原文使用简洁格式
        path = getArticlePath(article)
      }
      this.$router.push({ path })
    },

    // 查看原文
    viewOriginal(article) {
      this.$router.push(getArticlePath(article))
    },

    // 切换翻译视图
    toggleTranslationView(article) {
      // 切换显示状态
      article['showTranslation'] = !article.showTranslation

      // 如果需要显示翻译内容，调用后端获取翻译匹配的内容
      if (article.showTranslation && !article.translationContent) {
        this.fetchTranslationContent(article)
      }

      // 强制更新组件
      this.$forceUpdate()
    },

    // 获取翻译匹配的内容
    async fetchTranslationContent(article) {
      try {
        // 构建查询参数
        const queryParams = new URLSearchParams()
        if (this.searchKey) {
          queryParams.append('searchKey', this.searchKey)
        }
        if (article.matchedLanguage) {
          queryParams.append('language', article.matchedLanguage)
        }

        const url = `/article/translation/${article.id}${
          queryParams.toString() ? '?' + queryParams.toString() : ''
        }`

        const response = await this.$http.get(url)

        // 检查响应是否成功
        if (
          response.data &&
          (response.code === 200 || response.code === '200')
        ) {
          // 使用 Vue.set 确保响应式更新
          article['translationContent'] = response.data.articleContent
          article['translationTitle'] = response.data.articleTitle

          // 等待下一个 tick 再强制更新
          this.$nextTick(() => {
            this.$forceUpdate()
          })
        } else {
          console.error('响应码不是200:', {
            code: response.data.code,
            codeType: typeof response.code,
            fullData: response.data,
          })
        }
      } catch (error) {
        console.error('获取翻译内容失败:', error)
        this.$message.error('获取翻译内容失败')
      }
    },
  },
}
</script>

<style scoped>
.article-first {
  color: var(--greyFont);
  border-bottom: 1px dashed var(--lightGray);
  padding-bottom: 0.5em;
  margin-bottom: 50px;
  line-height: 1.6;
}
.recent-post-container {
  max-width: 780px;
  margin: auto;
}
.recent-post-container .recent-post-item:not(:last-child) {
  margin-bottom: 40px;
}
.recent-post-item {
  height: 300px;
  position: relative;
  display: flex;
  flex-direction: row;
  user-select: none;
  cursor: pointer;
  overflow: hidden;
  border-radius: 10px;
  animation: hideToShow 1s ease-in-out;
}
.recent-post-item-image {
  width: 50%;
  height: 100%;
}
.recent-post-item-image :deep(.el-image__inner){
  transition: transform 1s ease, opacity 1s ease;
  will-change: transform;
}
.recent-post-item-image :deep(.el-image__inner:hover){
  transform: scale(1.2);
}
.leftImage {
  position: absolute;
  left: 0;
}
.rightImage {
  position: absolute;
  right: 0;
  text-align: right;
}
.recent-post-item-post {
  width: 50%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 35px;
}
.recent-post-item-post h3 {
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  max-width: 100%;
}
.post-meta {
  font-size: 12px;
  color: var(--greyFont);
}
.post-meta span:not(:last-child) {
  margin-right: 10px;
}
.recent-post-desc {
  font-size: 15px;
  line-height: 1.7;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
}
.leftImage .sort-label {
  position: absolute;
  bottom: 20px;
}
.rightImage .sort-label {
  position: absolute;
  bottom: 20px;
  right: 35px;
}
.sort-label span {
  padding: 3px 10px;
  background-color: var(--maxLightGray);
  border-radius: 3px;
  font-size: 14px;
  color: var(--greyFont);
  transition: color 0.3s ease;
  cursor: pointer;
  user-select: none;
}
.sort-label span:hover {
  background-color: var(--themeBackground);
  color: var(--white);
}
.error-text {
  font-size: 20px;
  line-height: 1.8;
  letter-spacing: 8px;
  color: var(--white);
  text-align: center;
  padding: 20px;
  word-break: break-word;
}
.image-container {
  position: relative;
  width: 100%;
  height: 100%;
}
.hasVideo {
  padding: 0 20px;
  background: var(--maxMaxWhiteMask);
  border-radius: 15px;
}
@media screen and (max-width: 700px) {
  .recent-post-item {
    height: 450px;
    position: unset;
    display: block;
    flex-direction: unset;
  }
  .recent-post-item-image {
    width: 100%;
    height: 200px;
  }
  .leftImage {
    position: unset;
    left: unset;
  }
  .rightImage {
    position: unset;
    right: unset;
    text-align: unset;
  }
  .recent-post-item-post {
    width: 100%;
    height: 250px;
    position: relative;
  }
  .recent-post-desc {
    -webkit-line-clamp: 3;
  }
  .leftImage .sort-label {
    position: absolute;
    bottom: 20px;
  }
  .rightImage .sort-label {
    position: absolute;
    bottom: 20px;
    right: unset;
  }
}
:deep(.search-highlight) {
  color: var(--lightGreen) !important;
  font-weight: bold !important;
  background: rgba(81, 196, 146, 0.1);
  padding: 0 2px;
  border-radius: 2px;
}
h3:deep(.search-highlight) {
  font-size: 1.05em;
  text-decoration: underline;
}
.article-card {
  width: 100%;
  border-radius: 12px;
  margin-bottom: 12px;
  cursor: pointer;
  display: flex;
  box-shadow: 0 1px 8px 0 rgba(0, 0, 0, 0.1);
  overflow: hidden;
  background: rgba(255, 255, 255, 1);
  transition: transform 0.4s ease, box-shadow 0.4s ease;
  position: relative;
  transform: translateZ(0);
}
.article-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.15);
}
.translation-match-tip {
  margin-top: 8px;
  margin-bottom: 5px;
}
.translation-match-tip .el-tag {
  font-size: 11px;
  border-radius: 12px;
  padding: 2px 8px;
  background-color: rgba(64, 158, 255, 0.1);
  border-color: rgba(64, 158, 255, 0.2);
  color: #409eff;
  position: absolute;
  bottom: 20px;
  right: 20px;
  cursor: pointer;
  transition: color 0.3s ease;
}
.translation-match-tip .el-tag:hover {
  background-color: rgba(64, 158, 255, 0.2);
  border-color: rgba(64, 158, 255, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(64, 158, 255, 0.3);
}
.translation-match-tip .clickable-tag {
  user-select: none;
}
.view-original {
  margin-top: 0.5em;
  padding-top: 0.5em;
  border-top: 1px dashed var(--lightGray);
  line-height: 1.6;
}
.view-original .el-button {
  color: var(--greyFont);
  font-size: 12px;
  padding: 0;
}
.view-original .el-button:hover {
  color: var(--themeBackground);
}
</style>

<style>
/* tooltip内的高亮样式 - 确保在绿色背景下可见 */
/* tooltip内的高亮样式 - 确保在绿色背景下可见 */
.el-popper.is-light .search-highlight {
  color: #ffd700 !important; /* 使用金黄色，在绿色背景下清晰可见 */
  background: rgba(255, 215, 0, 0.2) !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1); /* 添加轻微阴影增强可读性 */
}

/* 暗色模式下的tooltip高亮样式 */
body.dark-mode .el-popper.is-light .search-highlight {
  color: #ffe44d !important; /* 暗色模式下使用更亮的黄色 */
  background: rgba(255, 228, 77, 0.15) !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

/* tooltip内的文字选中样式 - 使用对比度高的颜色 */
.el-popper.is-light ::selection {
  background: rgba(255, 255, 255, 0.9) !important; /* 白色半透明背景 */
  color: #2c3e50 !important; /* 深色文字 */
}

.el-popper.is-light ::-moz-selection {
  background: rgba(255, 255, 255, 0.9) !important;
  color: #2c3e50 !important;
}

/* 暗色模式下的tooltip文字选中样式 */
body.dark-mode .el-popper.is-light ::selection {
  background: rgba(255, 255, 255, 0.85) !important;
  color: #1a1a1a !important;
}

body.dark-mode .el-popper.is-light ::-moz-selection {
  background: rgba(255, 255, 255, 0.85) !important;
  color: #1a1a1a !important;
}
</style>
