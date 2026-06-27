<template>
  <div>
    <!-- 评论框 -->
    <div style="margin-bottom: 40px">
      <div class="comment-head">
        <el-icon style="font-weight: bold; font-size: 22px"
          ><el-icon-edit-outline
        /></el-icon>
        留言
      </div>
      <div>
        <!-- 文字评论 -->
        <div v-show="!isGraffiti">
          <commentBox
            ref="commentBox"
            @showGraffiti="isGraffiti = !isGraffiti"
            @submitComment="submitComment"
          >
          </commentBox>
        </div>
        <!-- 画笔 -->
        <!--        <div v-show="isGraffiti">-->
        <!--          <graffiti @showComment="isGraffiti = !isGraffiti"-->
        <!--                    @addGraffitiComment="addGraffitiComment">-->
        <!--          </graffiti>-->
        <!--        </div>-->
      </div>
    </div>

    <!-- 评论内容 -->
    <div v-if="comments.length > 0" id="comment-content">
      <!-- 评论数量 -->
      <div class="commentInfo-title">
        <span style="font-size: 1.15rem">Comments | </span>
        <span>{{ total }} 条留言</span>
      </div>
      <!-- 评论详情 -->
      <div
        :id="'comment-item-' + item.id"
        class="commentInfo-detail comment-item-wrapper"
        v-for="(item, index) in comments"
        :key="index"
      >
        <!-- 头像 -->
        <el-avatar
          shape="circle"
          class="commentInfo-avatar"
          :size="40"
          :src="getAvatarForComment(item)"
          style="margin-top: 2px;"
        >
          <img :src="$getDefaultAvatar()" />
        </el-avatar>

        <div style="flex: 1; padding-left: 14px">
          <!-- 评论信息头部 -->
          <div class="commentInfo-header">
            <span class="commentInfo-username">{{
              item.displayUsername || item.username
            }}</span>
            <span class="commentInfo-master" v-if="!item.aiReply && item.userId === userId"
              >主人翁</span
            >
          </div>
          <!-- 评论内容 -->
          <div class="commentInfo-content" :style="item.aiReply ? 'margin-bottom: 4px;' : ''">
            <CommentMarkdownRenderer
              :content="item.commentContent"
              @rendered="bindCommentImages"
            />
          </div>
          <!-- AI生成提示 -->
          <div class="comment-ai-notice" v-if="item.aiReply">
            <span>评论由AI生成</span>
            <svg class="ai-notice-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="13" height="13">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
          </div>
          <!-- 评论信息底部 -->
          <div class="commentInfo-meta-bottom">
            <span class="commentInfo-other">{{
              $common.getDateDiff(item.createTime)
            }}</span>
            <span v-if="item.location">
              <span class="commentInfo-separator">·</span>
              <span class="commentInfo-location">{{
                item.location
              }}</span>
            </span>
            <span class="commentInfo-reply-btn" @click="replyDialog(item, item)">
              回复
            </span>
            <!-- 个人博客评论珍贵，暂时隐藏删除按钮以留住互动。如需启用请取消注释下方代码 -->
            <!-- <span v-if="canDeleteComment(item)" class="commentInfo-delete-btn" @click="deleteComment(item)">
              删除
            </span> -->
            <div class="commentInfo-like" :class="{ 'liked': isLiked(item.id) }" @click="likeComment(item)">
              <svg viewBox="0 0 24 24" :fill="isLiked(item.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
              </svg>
              <span class="like-count">{{ item.likeCount || 0 }}</span>
            </div>
          </div>
          <!-- 懒加载子评论展示 -->
          <div v-if="item.childComments && item.childComments.total > 0">
            <!-- 子评论列表 -->
            <div
              v-if="getDisplayReplies(item).length > 0"
              :id="'replies-container-' + item.id"
              class="replies-container"
            >
              <div
                class="commentInfo-detail"
                v-for="replyItem in getDisplayReplies(item)"
                :key="replyItem.id"
              >
                <!-- 头像 -->
                <el-avatar
                  shape="circle"
                  class="commentInfo-avatar"
                  :size="32"
                  :src="getAvatarForComment(replyItem)"
                  style="margin-top: 2px;"
                >
                  <img :src="$getDefaultAvatar()" />
                </el-avatar>

                <div style="flex: 1; padding-left: 14px">
                  <!-- 评论信息头部 -->
                  <div class="commentInfo-header">
                    <span class="commentInfo-username-small">{{
                      replyItem.displayUsername || replyItem.username
                    }}</span>
                    <span
                      class="commentInfo-master"
                      v-if="!replyItem.aiReply && replyItem.userId === userId"
                      >主人翁</span
                    >
                    <span
                      class="commentInfo-reply-indicator"
                      v-if="shouldShowReplyIndicator(replyItem, item)"
                    >
                      回复了 {{ replyItem.parentUsername }}
                    </span>
                  </div>
                  <!-- 评论内容 -->
                  <div class="commentInfo-content" :style="replyItem.aiReply ? 'margin-bottom: 4px;' : ''">
                    <CommentMarkdownRenderer
                      :content="replyItem.commentContent"
                      @rendered="bindCommentImages"
                    />
                  </div>
                  <!-- AI生成提示 -->
                  <div class="comment-ai-notice" v-if="replyItem.aiReply">
                    <span>评论由AI生成</span>
                    <svg class="ai-notice-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="13" height="13">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="8" x2="12" y2="12"></line>
                      <line x1="12" y1="16" x2="12.01" y2="16"></line>
                    </svg>
                  </div>
                  <!-- 评论信息底部 -->
                  <div class="commentInfo-meta-bottom">
                    <span class="commentInfo-other">{{
                      $common.getDateDiff(replyItem.createTime)
                    }}</span>
                    <span v-if="replyItem.location">
                      <span class="commentInfo-separator">·</span>
                      <span
                        class="commentInfo-location-small"
                      >{{ replyItem.location }}</span>
                    </span>
                    <span
                      class="commentInfo-reply-btn"
                      @click="replyDialog(replyItem, item)"
                    >
                      回复
                    </span>
                    <!-- 个人博客评论珍贵，暂时隐藏删除按钮以留住互动。如需启用请取消注释下方代码 -->
                    <!-- <span v-if="canDeleteComment(replyItem)" class="commentInfo-delete-btn" @click="deleteComment(replyItem, item)">
                      删除
                    </span> -->
                    <div class="commentInfo-like" :class="{ 'liked': isLiked(replyItem.id) }" @click="likeComment(replyItem)">
                      <svg viewBox="0 0 24 24" :fill="isLiked(replyItem.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                      </svg>
                      <span class="like-count">{{ replyItem.likeCount || 0 }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 加载更多回复按钮 -->
              <div class="pagination-wrap" v-if="item.expanded && item.hasMoreReplies">
                <div
                  class="pagination"
                  @click="loadMoreReplies(item)"
                  :disabled="item.loadingReplies"
                >
                  <span v-if="!item.loadingReplies">加载更多回复</span>
                  <span v-else
                    ><el-icon><el-icon-loading /></el-icon> 加载中...</span
                  >
                </div>
              </div>

              <!-- 折叠回复按钮 -->
              <div class="pagination-wrap" v-if="item.expanded && !item.hasMoreReplies && !item.collapsing">
                <div
                  class="collapse-replies-btn"
                  @click="collapseReplies(item)"
                >
                  <span class="collapse-text">折叠回复</span>
                  <el-icon class="collapse-icon"><el-icon-arrow-up /></el-icon>
                </div>
              </div>
            </div>

            <!-- 展开按钮（当回复未展开或有更多未展开回复时显示） -->
            <div v-if="!item.expanded && (item.childComments.total > (item.newLocalReplies ? item.newLocalReplies.length : 0))" class="pagination-wrap">
              <div
                class="expand-replies-btn"
                @click="expandReplies(item)"
                :disabled="item.loadingReplies"
              >
                <span class="expand-text" v-if="!item.loadingReplies">
                  展开 {{ item.childComments.total }} 条回复
                </span>
                <span class="expand-text" v-else>
                  <el-icon><el-icon-loading /></el-icon> 加载中...
                </span>
                <el-icon class="expand-icon"><el-icon-arrow-down /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- 🔧 懒加载UI：替换传统分页 -->
      <div v-if="enableLazyLoad" class="lazy-load-container">
        <!-- 加载更多按钮 -->
        <div
          v-if="hasMoreComments && !isLoadingMore"
          class="load-more-btn-container"
        >
          <el-button
            type="text"
            class="load-more-btn"
            @click="loadMoreComments"
            :disabled="isLoadingMore"
          >
            <el-icon><el-icon-arrow-down /></el-icon>
            加载更多评论
          </el-button>
        </div>

        <!-- 加载中状态 -->
        <div v-if="isLoadingMore" class="loading-container">
          <el-icon><el-icon-loading /></el-icon>
          <span>正在加载更多评论...</span>
        </div>

        <!-- 没有更多评论提示 -->
        <div
          v-if="!hasMoreComments && comments.length > 0"
          class="no-more-comments"
        >
          <span>没有更多评论了</span>
        </div>
      </div>

      <!-- 🔧 传统分页（备用，可通过enableLazyLoad控制） -->
      <proPage
        v-if="!enableLazyLoad"
        :current="pagination.current"
        :size="pagination.size"
        :total="pagination.total"
        :buttonSize="6"
        :color="$constant.commentPageColor"
        @toPage="toPage"
      >
      </proPage>
    </div>

    <div v-else class="myCenter" style="color: var(--greyFont)">
      <i>来发第一个留言啦~</i>
    </div>

    <el-dialog
      title="留言"
      v-model="replyDialogVisible"
      width="30%"
      :before-close="handleClose"
      :append-to-body="true"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      destroy-on-close
      center
    >
      <div>
        <commentBox
          ref="replyCommentBox"
          :disableGraffiti="true"
          @submitComment="submitReply"
        >
        </commentBox>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import {
  Edit as ElIconEditOutline,
  Loading as ElIconLoading,
  ArrowDown as ElIconArrowDown,
  ArrowUp as ElIconArrowUp,
} from '@element-plus/icons-vue'
import { useMainStore } from '@/stores/main'

// ;
import { checkCaptchaWithCache } from '@/utils/captchaUtil'
import CommentMarkdownRenderer from './CommentMarkdownRenderer.vue'

export default {
  components: {
    // graffiti: defineAsyncComponent(() => import( "./graffiti")),
    commentBox: defineAsyncComponent(() => import('./commentBox')),
    proPage: defineAsyncComponent(() => import('../common/proPage')),
    ElIconEditOutline,
    ElIconLoading,
    ElIconArrowDown,
    ElIconArrowUp,
    CommentMarkdownRenderer,
  },
  props: {
    source: {
      type: Number,
    },
    type: {
      type: String,
    },
    userId: {
      type: Number,
    },
  },
  data() {
    return {
      isGraffiti: false,
      total: 0,
      replyDialogVisible: false,
      floorComment: {},
      replyComment: {},
      comments: [],
      pagination: {
        current: 1,
        size: 15, // 一级评论每页显示数量，适当增加
        total: 0,
        source: this.source,
        commentType: this.type,
        floorCommentId: null,
      },
      // 折叠显示相关状态
      expandedComments: {}, // 记录每个一级评论的展开状态 {commentId: {expanded: boolean, displayCount: number}}
      pageSize: 10, // 每次展开显示的回复数量
      isLoadingMore: false, // 是否正在加载更多评论
      hasMoreComments: true, // 是否还有更多评论
      enableLazyLoad: true, // 是否启用懒加载模式
      scrollThreshold: 200, // 距离底部多少像素时触发加载
      scrollTimer: null, // 滚动防抖定时器
      likedComments: {},
      aiChatConfig: null, // AI 聊天配置以获取机器人名字
      pollingInterval: null, // 轮询定时器
      pendingAiReplies: [], // 等待 AI 回复的任务列表
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
  },
  watch: {
    source: {
      immediate: true,
      handler(newVal) {
        this.stopAiReplyPolling()
        if (newVal !== undefined && newVal !== null) {
          this.pagination.source = newVal
          this.pagination.current = 1
          this.comments = []
          this.expandedComments = {}
          this.isLoadingMore = false
          this.hasMoreComments = true
          this.getComments(this.pagination)
          this.getTotal()
        } else {
          this.comments = []
          this.total = 0
          this.pagination.source = null
        }
      }
    }
  },
  created() {
    this.expandedComments = {}
    this.comments = []
    this.isLoadingMore = false
    this.hasMoreComments = true

    this.loadLikedComments()
  },
  mounted() {
    // 滚动监听
    if (this.enableLazyLoad) {
      this.addScrollListener()
    }

    // 监听页面状态恢复事件
    $on(this.$bus, 'restore-page-state', this.handleRestorePageState)

    // 加载 AI 配置
    this.loadAiChatConfig()
  },
  beforeUnmount() {
    // 停止 AI 回复轮询
    this.stopAiReplyPolling()

    // 🔧 移除滚动监听
    if (this.enableLazyLoad) {
      this.removeScrollListener()
    }
    // 清理定时器
    if (this.scrollTimer) {
      clearTimeout(this.scrollTimer)
    }
    // 移除页面状态恢复事件监听
    $off(this.$bus, 'restore-page-state', this.handleRestorePageState)
  },
  methods: {
    loadAiChatConfig() {
      this.$http
        .get(
          this.$constant.baseURL + '/webInfo/ai/config/chat/getStreamingConfig',
          { configName: 'default' }
        )
        .then((res) => {
          if (res.data) {
            this.aiChatConfig = res.data
          }
        })
        .catch((err) => {
          console.warn('Failed to load AI chat config:', err)
        })
    },
    isMentioningAi(content) {
      if (!content) return false
      const botName =
        (this.aiChatConfig && this.aiChatConfig.chat_name) || 'AI助手'
      return (
        content.includes('@' + botName) ||
        content.includes('@AI助手') ||
        content.includes('@AI')
      )
    },
    registerAiReplyPolling(task) {
      task.startTime = Date.now()
      task.pollCount = 0
      this.pendingAiReplies.push(task)

      if (!this.pollingInterval) {
        this.pollingInterval = setInterval(() => {
          this.pollPendingAiReplies()
        }, 5000)
      }
    },
    pollPendingAiReplies() {
      if (this.pendingAiReplies.length === 0) {
        this.stopAiReplyPolling()
        return
      }

      const now = Date.now()
      this.pendingAiReplies = this.pendingAiReplies.filter((task) => {
        if (now - task.startTime > 120000) {
          console.warn(`AI reply polling timed out for comment ID: ${task.commentId}`)
          return false
        }
        return true
      })

      if (this.pendingAiReplies.length === 0) {
        this.stopAiReplyPolling()
        return
      }

      this.pendingAiReplies.forEach((task) => {
        this.checkAiReplyForTask(task)
      })
    },
    checkAiReplyForTask(task) {
      const baseUrl = this.$constant.baseURL + '/comment/listChildComments'
      const floorComment = this.comments.find(
        (c) => c.id === task.floorCommentId
      )
      const currentRecordsCount = floorComment && floorComment.childComments && floorComment.childComments.records
        ? floorComment.childComments.records.length : 10
      const size = Math.max(Math.ceil(currentRecordsCount / 10) * 10 + 10, 10)
      const urlParams = new URLSearchParams({
        parentCommentId: task.floorCommentId.toString(),
        current: '1',
        size: size.toString(),
      })
      const fullUrl = `${baseUrl}?${urlParams.toString()}`
      const requestBody = {
        source: this.source,
        commentType: this.type,
      }

      this.$http
        .post(fullUrl, requestBody)
        .then((res) => {
          let childCommentsData = null
          if (res.data && res.data.data && res.data.data.records) {
            childCommentsData = res.data.data
          } else if (res.data && res.data.records) {
            childCommentsData = res.data
          }

          if (childCommentsData && childCommentsData.records) {
            const aiReply = childCommentsData.records.find(
              (c) => c.aiReply === true && c.parentCommentId === task.commentId
            )

            if (aiReply) {
              this.pendingAiReplies = this.pendingAiReplies.filter(
                (t) => t.commentId !== task.commentId
              )
              this.handleAiReplyFound(task, childCommentsData)
            }
          }
        })
        .catch((err) => {
          console.warn('Error checking AI reply status:', err)
        })
    },
    handleAiReplyFound(task, childCommentsData) {
      const floorComment = this.comments.find(
        (c) => c.id === task.floorCommentId
      )
      if (floorComment) {
        if (!floorComment.childComments) {
          floorComment.childComments = { records: [], total: 0 }
        }

        floorComment.childComments.records = childCommentsData.records
        floorComment.childComments.total = childCommentsData.total
        floorComment.totalReplies = childCommentsData.total
        floorComment.expanded = true
        floorComment.collapsedByUser = false
        floorComment.newLocalReplies = []

        const loadedCount = childCommentsData.records.length
        floorComment.currentPage = Math.max(1, Math.ceil(loadedCount / 10))
        floorComment.hasMoreReplies = loadedCount < childCommentsData.total

        this.emoji(floorComment.childComments.records, false)
        this.getTotal()
        this.$forceUpdate()

        const botName =
          (this.aiChatConfig && this.aiChatConfig.chat_name) || 'AI'
        this.$message({
          type: 'success',
          message: `${botName}已回复你的消息啦！`,
        })
      } else {
        this.getComments(this.pagination)
      }
    },
    stopAiReplyPolling() {
      if (this.pollingInterval) {
        clearInterval(this.pollingInterval)
        this.pollingInterval = null
      }
      this.pendingAiReplies = []
    },
    bindCommentImages() {
      this.$nextTick(() => {
        this.$common.imgShow('#comment-content .pictureReg')
      })
    },
    getAvatarForComment(item) {
      if (item.aiReply) {
        // AI 回复：只使用 displayAvatar，不回退到用户表 avatar
        // 使用 getAiAvatarUrl 以在未设置时显示专属的 AI 头像
        return this.$common.getAiAvatarUrl(item.displayAvatar)
      }
      return this.$common.getAvatarUrl(item.displayAvatar || item.avatar)
    },
    loadLikedComments() {
      const liked = localStorage.getItem('likedComments')
      if (liked) {
        try {
          const parsed = JSON.parse(liked)
          this.likedComments = Array.isArray(parsed)
            ? Object.fromEntries(parsed.map(id => [id, true]))
            : parsed
        } catch {
          this.likedComments = {}
        }
      }
    },
    isLiked(id) {
      return !!this.likedComments[id]
    },
    canDeleteComment(item) {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        return false
      }
      if (item.aiReply) {
        return false
      }
      return item.userId === this.mainStore.currentUser.id
    },
    likeComment(item) {
      const isLike = !this.isLiked(item.id)

      this.$http
        .post(this.$constant.baseURL + '/comment/likeComment', { id: item.id, isLike: isLike }, false, false)
        .then((res) => {
          if (res.data !== undefined && res.data !== null) {
            item.likeCount = res.data
          } else {
            item.likeCount = isLike ? (item.likeCount || 0) + 1 : Math.max(0, (item.likeCount || 1) - 1)
          }
          if (isLike) {
            this.likedComments[item.id] = true
          } else {
            delete this.likedComments[item.id]
          }
          localStorage.setItem('likedComments', JSON.stringify(this.likedComments))
        })
        .catch(() => {
          this.$message.warning('操作失败，请稍后再试')
        })
    },
    deleteComment(comment, parentComment) {
      this.$confirm('确定要删除这条评论吗？删除后不可恢复。', '删除评论', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => {
          this.$http
            .get(this.$constant.baseURL + '/comment/deleteComment', {
              id: comment.id,
            })
            .then(() => {
              this.$message.success('删除成功')
              if (comment.parentCommentId && comment.parentCommentId !== 0) {
                if (parentComment && parentComment.childComments && parentComment.childComments.records) {
                  const idx = parentComment.childComments.records.findIndex(c => c.id === comment.id)
                  if (idx > -1) {
                    parentComment.childComments.records.splice(idx, 1)
                    if (parentComment.childComments.total > 0) {
                      parentComment.childComments.total--
                    }
                  }
                }
              } else {
                const idx = this.comments.findIndex(c => c.id === comment.id)
                if (idx > -1) {
                  this.comments.splice(idx, 1)
                }
                this.total = Math.max(0, this.total - 1)
              }
            })
            .catch(() => {
              this.$message.warning('删除失败，请稍后再试')
            })
        })
        .catch(() => {})
    },
    toPage(page) {
      this.pagination.current = page
      window.scrollTo({
        top: document.getElementById('comment-content').offsetTop,
      })
      this.getComments(this.pagination)
    },
    getTotal() {
      this.$http
        .get(this.$constant.baseURL + '/comment/getCommentCount', {
          source: this.source,
          type: this.type,
        })
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.total = res.data
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    toChildPage(floorComment) {
      if (!floorComment.childComments.current) {
        floorComment.childComments.current = 1
      }
      floorComment.childComments.current += 1
      let pagination = {
        current: floorComment.childComments.current,
        size: 5,
        total: 0,
        source: this.source,
        commentType: this.type,
        floorCommentId: floorComment.id,
      }
      this.getComments(pagination, floorComment, true)
    },
    
    /**
     * 加载更多回复
     */
    loadMoreReplies(comment) {
      const currentState = this.expandedComments[comment.id]
      const newDisplayCount = Math.min(
        currentState.displayCount + this.pageSize,
        comment.flatReplies.length
      )

      // 如果当前显示的回复数量已经等于已加载的回复数量，且还有更多回复，则需要从服务器加载
      if (
        currentState.displayCount >= comment.flatReplies.length &&
        comment.flatReplies.length <
          (comment.totalReplies || comment.childComments.total)
      ) {
        this.loadMoreRepliesFromServer(comment)
      } else {
        // 直接显示更多已加载的回复
        this.expandedComments[comment.id] = {
          expanded: true,
          displayCount: newDisplayCount,
        }
      }
    },

    /**
     * 从服务器加载更多回复数据
     */
    loadMoreRepliesFromServer(comment) {
      if (!comment.childComments.current) {
        comment.childComments.current = 1
      }
      comment.childComments.current += 1

      let pagination = {
        current: comment.childComments.current,
        size: this.pageSize,
        total: 0,
        source: this.source,
        commentType: this.type,
        floorCommentId: comment.id,
      }

      this.getComments(pagination, comment, true)
    },

    /**
     * 加载回复数据
     */
    loadRepliesData(comment) {
      let pagination = {
        current: 1,
        size: this.pageSize,
        total: 0,
        source: this.source,
        commentType: this.type,
        floorCommentId: comment.id,
      }

      this.getComments(pagination, comment, false)
    },

    /**
     * 获取当前应该显示的回复列表
     */
    getDisplayReplies(item) {
      if (item.collapsedByUser) {
        return []
      }

      const records = (item.childComments && item.childComments.records) ? item.childComments.records : []
      const locals = item.newLocalReplies ? item.newLocalReplies : []

      if (item.expanded) {
        // 返回所有已加载记录 + 不在记录中的本地 session 回复（以避免重复）
        const recordIds = new Set(records.map(r => r.id))
        const uniqueLocals = locals.filter(l => !recordIds.has(l.id))
        return [...records, ...uniqueLocals]
      } else {
        // 如果未展开，只显示本地 session 发表的回复
        return locals
      }
    },

    /**
     * 检查是否还有更多回复可以展开
     */
    hasMoreReplies(comment) {
      const expandState = this.expandedComments[comment.id]
      if (!expandState || !expandState.expanded) return false

      const totalReplies =
        comment.totalReplies ||
        (comment.childComments ? comment.childComments.total : 0)
      const currentDisplayCount = expandState.displayCount || 0
      const loadedRepliesCount = comment.flatReplies
        ? comment.flatReplies.length
        : 0

      // 检查是否还有更多回复需要显示
      // 条件1：当前显示数量小于总回复数量
      // 条件2：当前显示数量小于已加载的回复数量（有缓存的回复未显示）
      return (
        currentDisplayCount < totalReplies ||
        currentDisplayCount < loadedRepliesCount
      )
    },

    emoji(comments, flag) {
      comments.forEach((c) => {
        c.commentContent = c.commentContent.replace(/\n/g, '<br/>')
        c.commentContent = this.$common.faceReg(c.commentContent)
        c.commentContent = this.$common.pictureReg(c.commentContent)
        if (flag) {
          if (
            !this.$common.isEmpty(c.childComments) &&
            !this.$common.isEmpty(c.childComments.records)
          ) {
            c.childComments.records.forEach((cc) => {
              c.commentContent = c.commentContent.replace(/\n/g, '<br/>')
              cc.commentContent = this.$common.faceReg(cc.commentContent)
              cc.commentContent = this.$common.pictureReg(cc.commentContent)
            })
          }
        }
      })
    },

    /**
     * 计算评论的直接回复数量
     * @param {Object} comment - 评论对象
     * @param {Array} allReplies - 所有回复列表
     * @returns {Number} - 直接回复数量
     */
    calculateDirectReplyCount(comment, allReplies) {
      if (!comment || !allReplies || !allReplies.length) {
        return 0
      }

      // 只统计parentCommentId等于当前评论id的直接回复
      return allReplies.filter((reply) => reply.parentCommentId === comment.id)
        .length
    },

    /**
     * 处理主评论数据，只处理统计信息，不平铺子评论
     * @param {Array} comments - 主评论列表
     */
    processMainComments(comments) {
      if (!comments || !comments.length) return

      comments.forEach((comment, index) => {
        // 只处理子评论统计信息，不加载子评论内容
        if (comment.childComments && comment.childComments.total > 0) {
          // 初始化懒加载状态
          comment.expanded = false
          comment.loadingReplies = false
          comment.currentPage = 1
          comment.hasMoreReplies = comment.childComments.total > 10 // 假设每页10条

          // 确保childComments.records为空数组（懒加载模式）
          if (!comment.childComments.records) {
            comment.childComments.records = []
          }
        } else {
          comment.expanded = false
          comment.loadingReplies = false
          comment.hasMoreReplies = false

          if (!comment.childComments) {
            comment.childComments = {
              records: [],
              total: 0,
            }
          }
        }
      })
    },

    /**
     * 展开子评论（懒加载）
     * @param {Object} comment - 主评论对象
     */
    async expandReplies(comment) {
      if (comment.loadingReplies) return

      comment['collapsedByUser'] = false
      comment.loadingReplies = true

      try {
        const baseUrl = this.$constant.baseURL + '/comment/listChildComments'
        const urlParams = new URLSearchParams({
          parentCommentId: comment.id.toString(),
          current: '1',
          size: '10',
        })
        const fullUrl = `${baseUrl}?${urlParams.toString()}`

        const requestBody = {
          source: this.source,
          commentType: this.type,
        }

        const response = await this.$http.post(fullUrl, requestBody)

        let childCommentsData = null
        if (response.data && response.data.data && response.data.data.records) {
          childCommentsData = response.data.data
        } else if (response.data && response.data.records) {
          childCommentsData = response.data
        }

        if (childCommentsData && childCommentsData.records) {
          comment.childComments['records'] = childCommentsData.records
          comment['expanded'] = true
          comment['currentPage'] = 1
          comment['hasMoreReplies'] =
            childCommentsData.records.length < childCommentsData.total
          this.$forceUpdate()
        } else {
          this.$message({
            type: 'error',
            message: '数据格式错误，请重试',
          })
        }
      } catch (error) {
        let errorMessage = '加载回复失败，请重试'
        if (
          error.response &&
          error.response.data &&
          error.response.data.message
        ) {
          errorMessage = `加载失败: ${error.response.data.message}`
        }

        this.$message({
          type: 'error',
          message: errorMessage,
        })
      } finally {
        comment.loadingReplies = false
      }
    },

    /**
     * 加载更多子评论
     * @param {Object} comment - 主评论对象
     */
    async loadMoreReplies(comment) {
      if (comment.loadingReplies) return

      comment.loadingReplies = true

      try {
        const baseUrl = this.$constant.baseURL + '/comment/listChildComments'
        const urlParams = new URLSearchParams({
          parentCommentId: comment.id.toString(),
          current: (comment.currentPage + 1).toString(),
          size: '10',
        })
        const fullUrl = `${baseUrl}?${urlParams.toString()}`

        const requestBody = {
          source: this.source,
          commentType: this.type,
        }

        const response = await this.$http.post(fullUrl, requestBody)

        let childCommentsData = null
        if (response.data && response.data.data && response.data.data.records) {
          childCommentsData = response.data.data
        } else if (response.data && response.data.records) {
          childCommentsData = response.data
        }

        if (childCommentsData && childCommentsData.records) {
          const newRecords = [
            ...comment.childComments.records,
            ...childCommentsData.records,
          ]
          comment.childComments['records'] = newRecords
          comment['currentPage'] = comment.currentPage + 1

          const totalLoaded = newRecords.length
          comment['hasMoreReplies'] = totalLoaded < comment.childComments.total
          this.$forceUpdate()
        } else {
          this.$message({
            type: 'error',
            message: '加载更多数据格式错误',
          })
        }
      } catch (error) {
        let errorMessage = '加载更多回复失败，请重试'
        if (
          error.response &&
          error.response.data &&
          error.response.data.message
        ) {
          errorMessage = `加载更多失败: ${error.response.data.message}`
        }

        this.$message({
          type: 'error',
          message: errorMessage,
        })
      } finally {
        comment.loadingReplies = false
      }
    },

    /**
     * 收起子评论
     * @param {Object} comment - 主评论对象
     */
    collapseReplies(comment) {
      if (comment.collapsing) return
      comment.collapsing = true

      const repliesEl = document.getElementById(`replies-container-${comment.id}`)
      const commentEl = document.getElementById(`comment-item-${comment.id}`)

      if (repliesEl && commentEl) {
        const headerOffset = 80
        
        // 临时关闭全局平滑滚动
        const htmlEl = document.documentElement
        const originalScrollBehavior = window.getComputedStyle(htmlEl).scrollBehavior
        if (originalScrollBehavior === 'smooth') {
          htmlEl.style.scrollBehavior = 'auto'
        }

        // 使用 requestAnimationFrame 锁定滚动位置，抵消浏览器的 scroll anchoring 调整
        const keepScrollLocked = () => {
          if (!comment.collapsing) return
          
          const elementPosition = commentEl.getBoundingClientRect().top
          const targetScrollTop = elementPosition + window.pageYOffset - headerOffset
          
          window.scrollTo({
            top: targetScrollTop,
            behavior: 'auto'
          })
          
          requestAnimationFrame(keepScrollLocked)
        }
        
        // 启动滚动锁定
        requestAnimationFrame(keepScrollLocked)

        // 2. 测量当前高度并固定，为过渡动画做准备
        const currentHeight = repliesEl.scrollHeight
        repliesEl.style.height = `${currentHeight}px`

        // 强制重绘
        repliesEl.offsetHeight

        // 3. 平滑收缩高度至 0
        repliesEl.style.transition = 'height 300ms cubic-bezier(0.4, 0, 0.2, 1)'
        repliesEl.style.height = '0px'

        setTimeout(() => {
          // 修改状态，Vue 将在下一个 tick 更新 DOM 并移除 replies-container
          comment['expanded'] = false
          comment['collapsedByUser'] = true

          // 在 DOM 卸载和布局整理期间，继续保持 RAF 滚动锁定 150ms
          setTimeout(() => {
            // 停止滚动锁定 loop
            comment.collapsing = false

            // 再次在下一个渲染帧恢复 smooth 滚动，确保浏览器滚动彻底稳定
            requestAnimationFrame(() => {
              setTimeout(() => {
                if (originalScrollBehavior === 'smooth') {
                  htmlEl.style.scrollBehavior = ''
                }
              }, 100)
            })
          }, 150)

          this.$forceUpdate()
        }, 300)
      } else {
        comment['expanded'] = false
        comment['collapsedByUser'] = true
        comment.collapsing = false
        this.$forceUpdate()
      }
    },

    /**
     * 判断是否应该显示回复指示器
     * 只在嵌套回复时显示，直接回复主评论时隐藏
     * @param {Object} replyItem - 子评论对象
     * @param {Object} mainComment - 主评论对象
     * @return {Boolean} 是否显示回复指示器
     */
    shouldShowReplyIndicator(replyItem, mainComment) {
      if (!replyItem.parentUsername) {
        return false
      }

      // 如果是直接回复主评论，且回复的对象确实是主评论的作者，则隐藏指示器
      // 通过 parentCommentId 判断是否挂在主评论下，再通过 parentUserId 与主评论作者核对
      if (replyItem.parentCommentId == mainComment.id && replyItem.parentUserId == mainComment.userId) {
        return false
      }

      // 如果是嵌套回复（回复的回复），或者因父评论被删导致提升层级但原本回复的是别人，则显示指示器
      return true
    },

    /**
     * 递归提取所有嵌套评论的ID（用于数据分析）
     */
    extractNestedCommentIds(comment) {
      const ids = []
      if (!comment.childComments || !comment.childComments.records) {
        return ids
      }

      comment.childComments.records.forEach((child) => {
        ids.push(child.id)
        ids.push(...this.extractNestedCommentIds(child))
      })

      return ids
    },

    /**
     * 递归计算嵌套评论总数
     */
    countNestedComments(comment) {
      if (!comment.childComments) {
        return 0
      }

      return comment.childComments.total || 0
    },
    getComments(
      pagination,
      floorComment = {},
      isToPage = false,
      isLazyLoad = false
    ) {
      this.$http
        .post(this.$constant.baseURL + '/comment/listComment', pagination)
        .then((res) => {
          if (
            !this.$common.isEmpty(res.data) &&
            !this.$common.isEmpty(res.data.records)
          ) {
            if (this.$common.isEmpty(floorComment)) {
              // 懒加载模式处理
              if (isLazyLoad) {
                // 追加新评论到现有列表
                this.comments = this.comments.concat(res.data.records)
                // 更新懒加载状态
                this.hasMoreComments =
                  res.data.records.length === pagination.size
                this.isLoadingMore = false
              } else {
                // 初始加载或传统分页模式
                // 在拿到新数据后再清空旧数据，避免网络延迟时评论区短暂变空
                this.expandedComments = {}
                this.comments = res.data.records
                this.hasMoreComments =
                  res.data.records.length === pagination.size
                // 非懒加载模式下也要重置isLoadingMore状态
                this.isLoadingMore = false
              }
              pagination.total = res.data.total

              this.processMainComments(
                isLazyLoad ? res.data.records : this.comments
              )
              this.emoji(isLazyLoad ? res.data.records : this.comments, true)
            } else {
              if (isToPage === false) {
                const newReplies = res.data.records
                newReplies.sort(
                  (a, b) => new Date(a.createTime) - new Date(b.createTime)
                )

                floorComment.flatReplies = newReplies
                floorComment.totalReplies = res.data.total
                floorComment.childComments = {
                  records: [],
                  total: res.data.total,
                }

                this.expandedComments[floorComment.id] = {
                  expanded: true,
                  displayCount: Math.min(this.pageSize, newReplies.length),
                }
              } else {
                const newReplies = res.data.records

                floorComment.flatReplies =
                  floorComment.flatReplies.concat(newReplies)
                floorComment.flatReplies.sort(
                  (a, b) => new Date(a.createTime) - new Date(b.createTime)
                )
                floorComment.totalReplies = res.data.total // 使用服务器返回的总数
                floorComment.childComments.total = res.data.total

                // 更新展开状态，显示更多回复
                const currentState = this.expandedComments[floorComment.id]
                this.expandedComments[floorComment.id] = {
                  expanded: true,
                  displayCount: Math.min(
                    currentState.displayCount + this.pageSize,
                    floorComment.flatReplies.length
                  ),
                }
              }
              this.emoji(floorComment.flatReplies, false)
            }
            this.$nextTick(() => {
              this.$common.imgShow('#comment-content .pictureReg')
            })
          } else {
            // 即使没有评论数据，也要重置isLoadingMore状态
            if (this.$common.isEmpty(floorComment)) {
              this.isLoadingMore = false
              this.hasMoreComments = false
              // 非懒加载模式下接口返回空，才清掉旧评论（正常换页/切文章场景）
              if (!isLazyLoad) {
                this.expandedComments = {}
                this.comments = []
              }
            }
          }
        })
        .catch((error) => {
          // 懒加载错误处理
          if (isLazyLoad) {
            this.isLoadingMore = false
            this.pagination.current -= 1 // 回退页码
            this.$message({
              message: '加载更多评论失败：' + error.message,
              type: 'error',
            })
          } else {
            this.$message({
              message: error.message,
              type: 'error',
            })
          }
        })
    },
    addGraffitiComment(graffitiComment) {
      this.submitComment(graffitiComment)
    },
    submitComment(commentContent) {
      let comment = {
        source: this.source,
        type: this.type,
        commentContent: commentContent,
      }

      // 保存评论内容到内存中，以便验证码取消时恢复
      this.pendingCommentContent = commentContent

      // 检查是否需要验证码
      checkCaptchaWithCache('comment').then((required) => {
        if (required) {
          // 需要验证码：立即清空评论框，显示验证码组件
          this.clearCommentBox()

          this.mainStore.setVerifyParams({
            action: 'comment',
            isReplyComment: false, // 主评论
            onSuccess: (token) => this.saveCommentToServer(comment, token),
            onCancel: () => this.restorePendingComment(),
          })
          this.mainStore.showCaptcha(true)
        } else {
          // 不需要验证码，直接发表评论并清空评论框
          this.clearCommentBox()
          this.saveCommentToServer(comment)
        }
      })
    },

    // 将评论保存到服务器
    saveCommentToServer(comment, verificationToken) {
      // 如果有验证token，添加到请求中
      if (verificationToken) {
        comment.verificationToken = verificationToken
      }

      this.$http
        .post(this.$constant.baseURL + '/comment/saveComment', comment)
        .then((res) => {
          this.$message({
            type: 'success',
            message: '保存成功！',
          })

          // 评论提交成功后，确保评论框被清空
          this.pendingCommentContent = null
          this.clearCommentBox()

          // 重置懒加载状态，防止显示"正在加载更多评论..."
          this.isLoadingMore = false
          this.hasMoreComments = true // 重置为true，等待getComments更新

          this.pagination = {
            current: 1,
            size: 10,
            total: 0,
            source: this.source,
            commentType: this.type,
            floorCommentId: null,
          }
          this.getComments(this.pagination)
          this.getTotal()

          if (this.isMentioningAi(comment.commentContent)) {
            const serverComment = res.data || {}
            this.registerAiReplyPolling({
              commentId: serverComment.id,
              floorCommentId: serverComment.id,
              isFloor: true,
            })
          }
        })
        .catch((error) => {
          if (error && (error.code === 460 || error.code === 461)) {
            this.mainStore.setVerifyParams({
              action: 'comment',
              isReplyComment: false,
              onSuccess: (token) => this.saveCommentToServer(comment, token),
              onCancel: () => this.restorePendingComment(),
            })
            this.mainStore.showCaptcha(true)
            return
          }
          this.$message({
            message: error.message,
            type: 'error',
          })

          // 评论提交失败时，恢复评论内容
          this.restorePendingComment()
        })
    },
    submitReply(commentContent) {
      // 此时用户必须已登录（因为未登录用户不会看到回复对话框）
      let comment = {
        source: this.source,
        type: this.type,
        floorCommentId: this.floorComment.id,
        commentContent: commentContent,
        parentCommentId: this.replyComment.id,
        parentUserId: this.replyComment.userId,
      }

      let floorComment = this.floorComment
      const parentComment = { ...this.replyComment }

      // 保存回复内容和对话框状态，以便验证码取消时恢复
      this.pendingReplyContent = {
        content: commentContent,
        floorComment: { ...floorComment }, // 深拷贝避免引用问题
        replyComment: { ...this.replyComment }, // 深拷贝避免引用问题
      }

      // 检查是否需要验证码
      checkCaptchaWithCache('comment').then((required) => {
        if (required) {
          // 需要验证码：先关闭回复对话框，显示验证码组件
          this.handleClose()
          this.mainStore.setVerifyParams({
            action: 'comment',
            isReplyComment: true, // 回复评论
            onSuccess: (token) =>
              this.saveReplyToServer(comment, floorComment, parentComment, token),
            onCancel: () => this.restorePendingReply(),
          })
          this.mainStore.showCaptcha(true)
        } else {
          // 不需要验证码，直接发表回复并关闭对话框
          this.saveReplyToServer(comment, floorComment, parentComment)
          this.handleClose()
          // 清除待恢复的回复内容
          this.pendingReplyContent = null
        }
      })
    },

    // 将回复保存到服务器
    saveReplyToServer(comment, floorComment, parentComment, verificationToken) {
      // 如果有验证token，添加到请求中
      if (verificationToken) {
        comment.verificationToken = verificationToken
      }

      this.$http
        .post(this.$constant.baseURL + '/comment/saveComment', comment)
        .then((res) => {
          this.$message({
            type: 'success',
            message: '回复成功！',
          })

          // 回复提交成功后，确保对话框关闭
          this.pendingReplyContent = null
          this.handleClose()

          // 确保新回复是可见的（不处于折叠状态）
          floorComment['collapsedByUser'] = false

          // 将回复加入到本 session 的本地回复列表中，确保用户能立即看到
          if (!floorComment.newLocalReplies) {
            floorComment.newLocalReplies = []
          }

          const serverComment = res.data || {}
          let newReply = {
            id: serverComment.id || ('local_' + Date.now()),
            username: this.mainStore.currentUser.username || '我',
            displayUsername: this.mainStore.currentUser.nickname || this.mainStore.currentUser.username || '我',
            avatar: this.mainStore.currentUser.avatar || '',
            displayAvatar: this.mainStore.currentUser.avatar || '',
            userId: this.mainStore.currentUser.id,
            commentContent: comment.commentContent,
            createTime: serverComment.createTime || new Date().toISOString(),
            location: serverComment.location || '内网IP',
            parentCommentId: comment.parentCommentId,
            parentUserId: parentComment.userId,
            parentUsername: parentComment.username || parentComment.displayUsername,
          }

          // 处理表情和图片渲染
          newReply.commentContent = newReply.commentContent.replace(/\n/g, '<br/>')
          newReply.commentContent = this.$common.faceReg(newReply.commentContent)
          newReply.commentContent = this.$common.pictureReg(newReply.commentContent)

          floorComment.newLocalReplies.push(newReply)

          // 更新子评论总数计数
          if (!floorComment.childComments) {
            floorComment.childComments = { records: [], total: 0 }
          }
          floorComment.childComments.total += 1
          floorComment.totalReplies = (floorComment.totalReplies || 0) + 1

          // 如果已经处于展开状态，从服务器重新拉取最新数据以保持同步
          if (floorComment.expanded) {
            this.refreshNestedReplies(floorComment)
          }

          // 更新文章总评论数
          this.getTotal()
          this.$forceUpdate()

          if (this.isMentioningAi(comment.commentContent)) {
            this.registerAiReplyPolling({
              commentId: serverComment.id,
              floorCommentId: floorComment.id,
              isFloor: false,
            })
          }
        })
        .catch((error) => {
          if (error && (error.code === 460 || error.code === 461)) {
            this.mainStore.setVerifyParams({
              action: 'comment',
              isReplyComment: true,
              onSuccess: (token) =>
                this.saveReplyToServer(comment, floorComment, parentComment, token),
              onCancel: () => this.restorePendingReply(),
            })
            this.mainStore.showCaptcha(true)
            return
          }
          this.$message({
            message: error.message,
            type: 'error',
          })

          // 回复提交失败时，恢复回复内容
          this.restorePendingReply()
        })
    },

    /**
     * 刷新嵌套回复（用于三级评论提交后的显示更新）
     * @param {Object} floorComment - 楼层评论对象
     */
    async refreshNestedReplies(floorComment) {
      // 楼层评论对象验证
      if (!floorComment || !floorComment.id) {
        console.error('楼层评论对象无效:', floorComment)
        return
      }

      try {
        // 使用懒加载接口重新获取所有子评论
        const baseUrl = this.$constant.baseURL + '/comment/listChildComments'
        const currentRecordsCount = floorComment.childComments && floorComment.childComments.records ? floorComment.childComments.records.length : 10
        // 将 size 对齐到 10 的整数倍，与 loadMoreReplies 的分页基准一致
        const size = Math.max(Math.ceil(currentRecordsCount / 10) * 10, 10)
        const urlParams = new URLSearchParams({
          parentCommentId: floorComment.id.toString(),
          current: '1',
          size: size.toString(), // 获取当前已加载的回复数量，避免自动展开多余的评论
        })
        const fullUrl = `${baseUrl}?${urlParams.toString()}`

        const requestBody = {
          source: this.source,
          commentType: this.type,
        }

        const response = await this.$http.post(fullUrl, requestBody)

        let childCommentsData = null
        if (response.data && response.data.data && response.data.data.records) {
          childCommentsData = response.data.data
        } else if (response.data && response.data.records) {
          childCommentsData = response.data
        }

        if (childCommentsData && childCommentsData.records) {
          // 确保楼层评论有childComments属性
          if (!floorComment.childComments) {
            floorComment['childComments'] = { records: [], total: 0 }
          }

          // 更新楼层评论的子评论数据
          floorComment.childComments['records'] = childCommentsData.records
          floorComment.childComments['total'] = childCommentsData.total
          floorComment['expanded'] = true
          floorComment['currentPage'] = Math.ceil(size / 10)
          floorComment['hasMoreReplies'] =
            childCommentsData.records.length < childCommentsData.total

          // 刷新成功后清空本地回复，避免与服务器数据重复
          floorComment['newLocalReplies'] = []

          // 强制更新视图
          this.$forceUpdate()
        } else {
        }
      } catch (error) {
        console.error('刷新嵌套回复失败:', error)
        // 如果懒加载失败，回退到传统方式
        let pagination = {
          current: 1,
          size: 5,
          total: 0,
          source: this.source,
          commentType: this.type,
          floorCommentId: floorComment.id,
        }
        this.getComments(pagination, floorComment)
      }
    },
    replyDialog(comment, floorComment) {
      // 检查用户登录状态
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        // 未登录用户：保存页面状态并直接跳转到登录页面
        this.savePageStateAndRedirectToLogin(comment, floorComment)
        return
      }

      // 已登录用户：正常打开回复对话框
      this.replyComment = comment
      this.floorComment = floorComment
      this.replyDialogVisible = true
    },

    /**
     * 保存页面状态并跳转到登录页面
     * @param {Object} comment - 被回复的评论对象
     * @param {Object} floorComment - 楼层评论对象
     */
    savePageStateAndRedirectToLogin(comment, floorComment) {
      const articleId = this.$route.params.id

      // 保存页面状态到localStorage
      const pageState = {
        timestamp: Date.now(),
        articleUrl: window.location.href,
        scrollPosition:
          window.pageYOffset || document.documentElement.scrollTop,
        // 保存回复上下文
        replyContext: {
          replyComment: {
            id: comment.id,
            userId: comment.userId,
            username: comment.username,
            commentContent: comment.commentContent,
          },
          floorComment: {
            id: floorComment.id,
            username: floorComment.username,
            expanded: floorComment.expanded || false,
          },
        },
        // 保存当前展开的评论状态
        expandedComments: { ...this.expandedComments },
      }

      localStorage.setItem(`pageState_${articleId}`, JSON.stringify(pageState))

      // 使用统一的登录跳转函数
      this.$common.redirectToLogin(
        this.$router,
        {
          extraQuery: { hasReplyAction: 'true' },
          message: '请先登录！',
        },
        this
      )
    },
    handleClose() {
      this.replyDialogVisible = false
      this.floorComment = {}
      this.replyComment = {}
    },

    // 清空评论框内容
    clearCommentBox() {
      if (this.$refs.commentBox) {
        this.$refs.commentBox.clearComment()
      }
    },

    // 恢复待提交的评论内容（验证码取消时调用）
    restorePendingComment() {
      if (this.pendingCommentContent && this.$refs.commentBox) {
        this.$refs.commentBox.restoreComment(this.pendingCommentContent)
        this.pendingCommentContent = null
      }
    },

    // 恢复待提交的回复内容（验证码取消时调用）
    restorePendingReply() {
      if (this.pendingReplyContent) {
        // 重新打开回复对话框并恢复状态
        this.replyComment = this.pendingReplyContent.replyComment
        this.floorComment = this.pendingReplyContent.floorComment
        this.replyDialogVisible = true

        // 等待对话框完全打开后，恢复输入框内容
        this.$nextTick(() => {
          setTimeout(() => {
            if (this.$refs.replyCommentBox) {
              this.$refs.replyCommentBox.restoreComment(
                this.pendingReplyContent.content
              )
            } else {
            }
            // 清除待恢复的回复内容
            this.pendingReplyContent = null
          }, 200) // 增加延迟确保组件完全渲染
        })
      } else {
      }
    },

    /**
     * 处理登录后的页面状态恢复
     * @param {Object} stateData - 保存的页面状态数据
     */
    handleRestorePageState(stateData) {
      if (!stateData || !stateData.replyContext) {
        return
      }

      // 恢复展开的评论状态
      if (stateData.expandedComments) {
        this.expandedComments = { ...stateData.expandedComments }
        this.$forceUpdate()
      }

      const context = stateData.replyContext

      // 确保楼层评论的展开状态正确恢复
      const targetFloorComment = this.comments.find(
        (c) => c.id === context.floorComment.id
      )
      if (targetFloorComment) {
        if (context.floorComment.expanded && !targetFloorComment.expanded) {
          // 如果原本是展开的但现在未展开，则展开它
          this.expandReplies(targetFloorComment)
        } else if (
          context.floorComment.expanded &&
          targetFloorComment.expanded
        ) {
          // 如果原本就是展开的且现在也是展开的，确保子评论数据是最新的
          this.refreshNestedReplies(targetFloorComment)
        }
      }

      // 智能的等待机制确保评论列表完全加载
      const waitForCommentAndOpenDialog = (retryCount = 0) => {
        const maxRetries = 10 // 最多重试10次
        const retryDelay = 300 // 每次重试间隔300ms

        // 从实际的评论列表中找到完整的楼层评论对象
        const actualFloorComment = this.comments.find(
          (c) => c.id === context.floorComment.id
        )

        if (!actualFloorComment) {
          if (retryCount < maxRetries) {
            setTimeout(
              () => waitForCommentAndOpenDialog(retryCount + 1),
              retryDelay
            )
            return
          } else {
            console.error('无法找到楼层评论，状态恢复失败')
            return
          }
        }

        // 构造回复对象
        this.replyComment = {
          id: context.replyComment.id,
          userId: context.replyComment.userId,
          username: context.replyComment.username,
          commentContent: context.replyComment.commentContent,
        }

        // 使用实际的楼层评论对象，确保包含所有必要的属性和状态
        this.floorComment = actualFloorComment

        // 打开回复对话框
        this.replyDialogVisible = true
      }

      // 延迟一点时间确保评论列表已更新，然后开始等待和打开对话框
      setTimeout(() => waitForCommentAndOpenDialog(), 500)
    },

    // 🔧 懒加载相关方法
    /**
     * 加载更多一级评论
     */
    loadMoreComments() {
      if (this.isLoadingMore || !this.hasMoreComments) {
        return
      }

      this.isLoadingMore = true
      this.pagination.current += 1

      // 调用getComments，传入isLazyLoad=true
      this.getComments(this.pagination, {}, false, true)
    },

    /**
     * 添加滚动监听
     */
    addScrollListener() {
      window.addEventListener('scroll', this.handleScroll)
    },

    /**
     * 移除滚动监听
     */
    removeScrollListener() {
      window.removeEventListener('scroll', this.handleScroll)
    },

    /**
     * 处理滚动事件（带防抖）
     */
    handleScroll() {
      // 清除之前的定时器
      if (this.scrollTimer) {
        clearTimeout(this.scrollTimer)
      }

      // 设置防抖定时器
      this.scrollTimer = setTimeout(() => {
        if (this.isLoadingMore || !this.hasMoreComments) {
          return
        }

        const scrollTop =
          window.pageYOffset || document.documentElement.scrollTop
        const windowHeight = window.innerHeight
        const documentHeight = document.documentElement.scrollHeight

        // 当滚动到距离底部scrollThreshold像素时触发加载
        if (scrollTop + windowHeight >= documentHeight - this.scrollThreshold) {
          this.loadMoreComments()
        }
      }, 100) // 100ms防抖
    },
  },
}
</script>

<style scoped>
.comment-ai-notice {
  font-size: 12px;
  color: #999;
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  user-select: none;
}
.comment-ai-notice .ai-notice-icon {
  margin-left: 4px;
  font-size: 13px;
}
.comment-head {
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: bold;
  margin: 40px 0 20px 0;
  user-select: none;
  color: var(--themeBackground);
}
.commentInfo-title {
  margin-bottom: 20px;
  color: var(--greyFont);
  user-select: none;
}
.comment-item-wrapper {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
  overflow-anchor: none;
}
.commentInfo-detail {
  display: flex;
  padding: 15px 0;
}
.commentInfo-avatar {
  border-radius: 50% !important;
  flex-shrink: 0;
}
.commentInfo-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.commentInfo-username {
  color: var(--orangeRed);
  font-size: 16px;
  font-weight: 600;
  margin-right: 5px;
}
.commentInfo-username-small {
  color: var(--orangeRed);
  font-size: 14px;
  font-weight: 600;
  margin-right: 5px;
}
.commentInfo-master {
  color: var(--green);
  border: 1px solid var(--green);
  border-radius: 20px;
  font-size: 10px;
  padding: 0 6px;
  margin-left: 6px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  height: 18px;
  line-height: 1;
}
.commentInfo-ai {
  color: var(--blue);
  border: 1px solid var(--blue);
  border-radius: 20px;
  font-size: 10px;
  padding: 0 6px;
  margin-left: 6px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  height: 18px;
  line-height: 1;
  user-select: none;
}
.commentInfo-meta-bottom {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--greyFont);
}
.commentInfo-location {
  color: #999;
  font-size: 12px;
  user-select: none;
}
.commentInfo-location-small {
  color: #999;
  font-size: 12px;
  user-select: none;
}
.commentInfo-separator {
  margin: 0 4px;
  color: #999;
}
.commentInfo-other {
  font-size: 12px;
  color: var(--greyFont);
  user-select: none;
}
.commentInfo-reply-indicator {
  font-size: 13px;
  color: #666;
  margin: 0 6px;
  user-select: none;
}
.commentInfo-reply-btn {
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  color: #666;
  margin-left: 24px;
  font-weight: 600;
}
.commentInfo-reply-btn:hover {
  color: var(--themeBackground);
}
.commentInfo-delete-btn {
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  color: #999;
  margin-left: 16px;
  font-weight: 600;
}
.commentInfo-delete-btn:hover {
  color: #f56c6c;
}
.commentInfo-like {
  margin-left: auto;
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #999;
  transition: all 0.3s;
  user-select: none;
}
.commentInfo-like:hover {
  color: #ff2442;
}
.commentInfo-like.liked {
  color: #ff2442;
}
.commentInfo-like svg {
  margin-right: 4px;
}
.commentInfo-like .like-count {
  font-size: 13px;
}
.commentInfo-content {
  margin: 4px 0 10px;
  padding: 0;
  background: none !important;
  border-radius: 0;
  color: var(--black);
  word-break: break-word;
  line-height: 1.6;
}
.dark-mode .commentInfo-content {
  background: none !important;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-bottom: 10px;
}
.pagination {
  padding: 6px 20px;
  border: 1px solid var(--lightGray);
  border-radius: 3rem;
  color: var(--greyFont);
  user-select: none;
  cursor: pointer;
  text-align: center;
  font-size: 12px;
}
.pagination:hover {
  border: 1px solid var(--themeBackground);
  color: var(--themeBackground);
  box-shadow: 0 0 5px var(--themeBackground);
}
.pagination-wrap .expand-replies-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 20px !important;
  margin: 0 !important;
  border: 1px solid var(--lightGray) !important;
  border-radius: 3rem !important;
  background: var(--background) !important;
  cursor: pointer;
  user-select: none;
  text-align: center;
  font-size: 12px !important;
  color: var(--greyFont) !important;
  transition: background-color 0.3s ease, color 0.3s ease,
    border-color 0.3s ease;
  width: auto;
  min-width: 120px;
}
.pagination-wrap .expand-replies-btn:hover {
  border: 1px solid var(--themeBackground) !important;
  color: var(--themeBackground) !important;
  box-shadow: 0 0 5px var(--themeBackground) !important;
  background: var(--background) !important;
}
.expand-text {
  font-size: 12px;
  color: inherit;
  margin-right: 5px;
}
.expand-icon {
  font-size: 12px;
  color: inherit;
  transition: transform 0.3s ease;
}
.pagination-wrap .expand-replies-btn:hover .expand-icon {
  transform: translateY(1px);
}
.pagination-wrap .collapse-replies-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 20px !important;
  margin: 0 !important;
  border: 1px solid var(--lightGray) !important;
  border-radius: 3rem !important;
  background: var(--background) !important;
  cursor: pointer;
  user-select: none;
  text-align: center;
  font-size: 12px !important;
  color: var(--greyFont) !important;
  transition: background-color 0.3s ease, color 0.3s ease,
    border-color 0.3s ease;
  width: auto;
  min-width: 120px;
}
.pagination-wrap .collapse-replies-btn:hover {
  border: 1px solid var(--orangeRed) !important;
  color: var(--orangeRed) !important;
  box-shadow: 0 0 5px var(--orangeRed) !important;
  background: var(--background) !important;
}
.collapse-text {
  font-size: 12px;
  color: inherit;
  margin-right: 4px;
}
.collapse-icon {
  font-size: 12px;
  color: inherit;
  transition: transform 0.3s ease;
}
.pagination-wrap .collapse-replies-btn:hover .collapse-icon {
  transform: translateY(-1px);
}
.lazy-load-container {
  margin-top: 20px;
  text-align: center;
}
.load-more-btn-container {
  margin: 20px 0;
}
.load-more-btn {
  padding: 10px 30px !important;
  border: 1px solid var(--lightGray) !important;
  border-radius: 20px !important;
  background: var(--background) !important;
  color: var(--greyFont) !important;
  font-size: 14px !important;
  transition: background-color 0.3s ease, color 0.3s ease,
    border-color 0.3s ease;
}
.load-more-btn:hover {
  background: var(--lightGray) !important;
  color: var(--fontColor) !important;
  transform: translateY(-1px);
}
.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--greyFont);
  font-size: 14px;
}
.loading-container i {
  margin-right: 8px;
  font-size: 16px;
}
.no-more-comments {
  padding: 20px;
  color: var(--greyFont);
  font-size: 12px;
  margin-top: 10px;
}
.ai-reply-indicator {
  display: flex;
  align-items: center;
  margin-top: 0;
  margin-bottom: 25px;
  font-size: 12px;
  color: #999;
}
.ai-reply-text {
  margin-right: 4px;
}
.ai-reply-icon {
  width: 14px;
  height: 14px;
}
.replies-container {
  overflow: hidden;
  overflow-anchor: none;
}
</style>
