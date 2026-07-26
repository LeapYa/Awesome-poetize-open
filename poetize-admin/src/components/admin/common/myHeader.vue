<template>
  <div class="my-header myBetween" :class="{ 'header-dark': isAdminDark }">
    <div class="logo">后台管理</div>
    <div class="header-right">
      <AdminConfigSearch />
      <div class="admin-index" @click="handleGoHome">
        <svg viewBox="0 0 1024 1024" width="25" height="25" style="vertical-align: -6px;">
          <path d="M221.4 152.6h141.8V396h-141.8V152.6z" fill="#FAD996"></path>
          <path
            d="M363.4 152.6c-9-16.6-138.2-14.2-142 0-3 11.4-23.6 287 15.8 182.4 10.4-27.4 22.6-47.6 37.2-78.6 10.6-22.6 22-42.4 32.6-54.4 16.6-18.6 18 9.6 33.6 22.6 41.6 34.4 31.8-55.6 22.8-72z"
            fill="#F9F8F7"></path>
          <path
            d="M834.2 851c0 24.8-20.2 45-45 45H234.8c-24.8 0-45-20.2-45-45v-370h1.2l321.2-270 321.2 270h0.6l0.2 370z"
            fill="#FAD996"></path>
          <path d="M833.4 481l-321.2-270-1.8 1.6V896h278.8c24.8 0 45-20.2 45-45l-0.2-370h-0.6z"
                fill="#F7C872"></path>
          <path
            d="M62 548L432.6 166.2s81.2-90.6 171.8 0c79 79 343.6 400 343.6 400s-55.4 41-125.2 14.2c-37.8-14.4-118.2-68-133.4-150.8-11-60.2-45.8 29-60.8-38.2-9.4-42.2-67.2-96.6-67.2-96.6s-48.4-55.4-100.2 0c-13.8 14.8-11.4 134.6-44 128-26.4-5.2-20-105.6-27-81.2-9.6 33-25 61-54.4 47.2-33.2-15.6-42.4 79.6-64.8 112.8-17.2 25.4-23.8-45.8-40.4-29.2-21.2 21-2.4 94-39.4 108s-132.8-2-129.2-32.4z"
            fill="#F9F8F7"></path>
          <path
            d="M512 126.2v144c28.8 1 49.4 24.6 49.4 24.6s57.8 54.4 67.2 96.6c15 67.2 49.8-22 60.8 38.2 15.2 82.8 95.6 136.4 133.4 150.8 69.8 26.8 125.2-14.2 125.2-14.2s-264.6-321-343.6-400c-33.2-33.2-65.2-42-92.4-40z"
            fill="#EFEBE8"></path>
          <path d="M405.2 495h99.8v99.8h-99.8v-99.8zM405.2 609.2h99.8v99.8h-99.8v-99.8z" fill="#FB8A5D"></path>
          <path d="M519 495h99.8v99.8h-99.8v-99.8zM519 609.2h99.8v99.8h-99.8v-99.8z" fill="#EA6D4B"></path>
        </svg>
        <span>&nbsp;首页</span>
      </div>
      <div class="theme-toggle" @click="$emit('toggle-theme')" :title="isAdminDark ? '切换到浅色模式' : '切换到暗色模式'">
        <svg v-if="!isAdminDark" viewBox="0 0 1024 1024" width="22" height="22">
          <path d="M593.216 143.488a368 368 0 1 0 287.232 287.264 272.576 272.576 0 0 1-287.232-287.232z" fill="currentColor"></path>
        </svg>
        <svg v-else viewBox="0 0 1024 1024" width="22" height="22">
          <path d="M512 224a288 288 0 1 0 288 288 288 288 0 0 0-288-288z m0 512a224 224 0 1 1 224-224 224 224 0 0 1-224 224z m0-640a32 32 0 0 0 32-32V32a32 32 0 0 0-64 0v32a32 32 0 0 0 32 32z m0 832a32 32 0 0 0-32 32v32a32 32 0 0 0 64 0v-32a32 32 0 0 0-32-32zM195.2 195.2a32 32 0 0 0 0-45.248l-22.624-22.624a32 32 0 0 0-45.248 45.248l22.624 22.624a32 32 0 0 0 45.248 0z m701.472 678.656a32 32 0 0 0-45.248 0l-22.624 22.624a32 32 0 0 0 45.248 45.248l22.624-22.624a32 32 0 0 0 0-45.248zM96 512a32 32 0 0 0-32-32H32a32 32 0 0 0 0 64h32a32 32 0 0 0 32-32z m896 0a32 32 0 0 0-32-32h-32a32 32 0 0 0 0 64h32a32 32 0 0 0 32-32zM195.2 828.8a32 32 0 0 0-45.248 0l-22.624 22.624a32 32 0 0 0 45.248 45.248l22.624-22.624a32 32 0 0 0 0-45.248z m678.656-678.656a32 32 0 0 0 0-45.248l-22.624-22.624a32 32 0 0 0-45.248 45.248l22.624 22.624a32 32 0 0 0 45.248 0z" fill="currentColor"></path>
        </svg>
      </div>
      <div class="header-user-con" @mouseenter="showMask = true" @mouseleave="showMask = false">
        <el-avatar class="user-avatar" :size="40"
                  @click="logout"
                  :src="$common.getAvatarUrl(mainStore.currentAdmin.avatar)">
          <img :src="$getDefaultAvatar()" />
        </el-avatar>
        <!-- 头像遮罩 -->
        <transition name="fade">
          <div v-if="showMask" class="avatar-mask" @click="logout" @mouseenter="showMask = true">
            <span class="mask-text">退出</span>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script>
import { useMainStore } from '@/stores/main';
import AdminConfigSearch from './AdminConfigSearch.vue';

export default {
  components: {
    AdminConfigSearch
  },

  props: {
    isAdminDark: {
      type: Boolean,
      default: false
    }
  },

  data() {
    return {
      showMask: false
    };
  },

  computed: {
    mainStore() {
      return useMainStore();
    }
  },

  methods: {
    handleGoHome() {
      window.location.href = this.$constant.frontendURL;
    },

    logout() {
      this.$http.get(this.$constant.baseURL + '/user/logout', {}, true)
        .then(() => {
          this.mainStore.loadCurrentUser({});
          this.mainStore.loadCurrentAdmin({});
          // 清除 localStorage 中的缓存
          localStorage.removeItem('currentUser');
          localStorage.removeItem('currentAdmin');
          localStorage.removeItem('token');
          // 跳转到登录页面
          this.$router.push({ path: '/' });
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error'
          });
        });
    }
  }
};
</script>

<style scoped>
.my-header {
  position: relative;
  width: 100%;
  height: 70px;
  color: black;
  background-color: #ebf1f6;
}

.my-header .logo {
  line-height: 70px;
  margin-left: 70px;
  font-size: 22px;
}

@media screen and (max-width: 768px) {
  .my-header .logo {
    margin-left: 40px;
  }
}

@media screen and (max-width: 480px) {
  .my-header .logo {
    margin-left: 30px;
    font-size: 20px;
  }
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-right: 40px;
}

@media screen and (max-width: 768px) {
  .header-right {
    margin-right: 30px;
  }
}

@media screen and (max-width: 640px) {
  .header-right {
    margin-right: 20px;
    padding-right: 44px;
  }
}

@media screen and (max-width: 480px) {
  .header-right {
    margin-right: 16px;
    padding-right: 40px;
  }
}

.admin-index {
  height: 70px;
  line-height: 70px;
  font-size: 15px;
  margin-right: 20px;
  cursor: pointer;
}

@media screen and (max-width: 480px) {
  .admin-index {
    margin-right: 8px;
  }

  .theme-toggle {
    margin-right: 8px;
    padding: 0 6px;
  }
}

.header-user-con {
  display: flex;
  align-items: center;
  position: relative;
}

.avatar-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  z-index: 1000;
  border-radius: 50%;
}

.theme-toggle {
  height: 70px;
  display: flex;
  align-items: center;
  margin-right: 20px;
  cursor: pointer;
  padding: 0 12px;
  border-radius: 4px;
  transition: all 0.3s ease;
  color: #606266;
}

.theme-toggle:hover {
  background-color: rgba(0, 0, 0, 0.05);
  color: #409EFF;
}

.header-dark {
  background-color: #2d2d2d !important;
  color: #e0e0e0 !important;
}

.header-dark .logo,
.header-dark .admin-index {
  color: #e0e0e0;
}

.header-dark .admin-index:hover {
  color: #409EFF;
}

.header-dark .theme-toggle {
  color: #b0b0b0;
}

.theme-dark .header-user-con .el-avatar.user-avatar:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 头像遮罩过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.logout-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  color: #606266;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.logout-item:hover {
  background-color: #f5f7fa;
  color: #409EFF;
}

.logout-item i {
  font-size: 16px;
}

.mask-text {
  font-size: 14px;
  line-height: 1;
}


</style>

<style>
.header-user-con .el-avatar.user-avatar {
  cursor: pointer;
  transition: transform 0.6s ease;
  will-change: transform;
  transform: translateZ(0);
}

.header-user-con .el-avatar.user-avatar:hover {
  transform: rotate(360deg);
}
</style>
