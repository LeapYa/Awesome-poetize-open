<template>
  <div class="weiyan-page">
    <!-- 两句诗 -->
    <div class="my-animation-slide-top">
      <twoPoem :isHitokoto="false"></twoPoem>
    </div>

    <div class="weiyan-content-wrap" style="background: var(--background); animation: hideToShow 2.5s">
      <div class="weiyan-content">
        <treeHole
          :treeHoleList="treeHoleList"
          :avatar="
            !$common.isEmpty(mainStore.currentUser)
              ? $common.getAvatarUrl(mainStore.currentUser.avatar)
              : $common.getAvatarUrl(mainStore.webInfo.avatar)
          "
          @launch="launch"
          @deleteTreeHole="deleteTreeHole"
        >
        </treeHole>
        <proPage
          :current="pagination.current"
          :size="pagination.size"
          :total="pagination.total"
          :buttonSize="3"
          :color="$constant.pageColor"
          @toPage="toPage"
        >
        </proPage>
      </div>

      <!-- 页脚 -->
      <myFooter :showFooter="showFooter"></myFooter>
    </div>

    <el-dialog
      title="微言"
      v-model="weiYanDialogVisible"
      width="40%"
      :before-close="handleClose"
      :append-to-body="true"
      custom-class="centered-dialog"
      destroy-on-close
      :close-on-click-modal="false"
      center
    >
      <div>
        <div class="myCenter" style="padding-bottom: 20px">
          <el-radio-group v-model="isPublic">
            <el-radio-button :label="true">公开</el-radio-button>
            <el-radio-button :label="false">私密</el-radio-button>
          </el-radio-group>
        </div>
        <commentBox :disableGraffiti="true" @submitComment="submitWeiYan">
        </commentBox>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'
import { useMainStore } from '@/stores/main'


export default {
  components: {
    twoPoem: defineAsyncComponent(() => import('./common/twoPoem')),
    myFooter: defineAsyncComponent(() => import('./common/myFooter')),
    treeHole: defineAsyncComponent(() => import('./im/common/treeHole')),
    proPage: defineAsyncComponent(() => import('./common/proPage')),
    commentBox: defineAsyncComponent(() => import('./comment/commentBox')),
  },

  data() {
    return {
      treeHoleList: [],
      pagination: {
        current: 1,
        size: 10,
        total: 0,
      },
      weiYanDialogVisible: false,
      isPublic: true,
      showFooter: false,
    }
  },

  computed: {
    mainStore() {
      return useMainStore()
    },
  },

  watch: {},

  created() {
    this.getWeiYan()
  },

  mounted() {},

  methods: {
    toPage(page) {
      this.pagination.current = page
      window.scrollTo({
        top: 240,
        behavior: 'smooth',
      })
      this.getWeiYan()
    },
    launch() {
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

      this.weiYanDialogVisible = true
    },
    handleClose() {
      this.weiYanDialogVisible = false
    },
    submitWeiYan(content) {
      // 树洞只允许一行内容，含换行或 <br> 等标签时拦截并提示
      const violation = this.$common.checkTreeHoleSingleLine(content)
      if (violation) {
        this.$message({
          message: violation,
          type: 'warning',
        })
        return
      }

      let weiYan = {
        content: content,
        isPublic: this.isPublic,
      }

      this.$http
        .post(this.$constant.baseURL + '/weiYan/saveWeiYan', weiYan)
        .then((res) => {
          this.getWeiYan()
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
      this.handleClose()
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
        customClass: 'mobile-responsive-confirm',
      })
        .then(() => {
          this.$http
            .get(this.$constant.baseURL + '/weiYan/deleteWeiYan', { id: id })
            .then((res) => {
              this.$message({
                type: 'success',
                message: '删除成功!',
              })
              this.pagination.current = 1
              this.getWeiYan()
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
    getWeiYan() {
      this.$http
        .post(this.$constant.baseURL + '/weiYan/listWeiYan', this.pagination)
        .then((res) => {
          this.showFooter = false
          if (!this.$common.isEmpty(res.data)) {
            // 内容交由 CommentMarkdownRenderer 安全渲染（换行/表情/图片统一处理）
            this.treeHoleList = res.data.records
            this.pagination.total = res.data.total
          }
          this.$nextTick(() => {
            this.showFooter = true
            this.$common.imgShow('.tree-hole-box .pictureReg')
          })
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
  },
}
</script>

<style scoped>
.weiyan-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.weiyan-content-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.weiyan-content {
  flex: 1;
}
</style>
