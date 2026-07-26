<template>
  <div class="split-login">
    <!-- 左侧品牌区 -->
    <div class="split-login-brand">
      <el-image
        v-if="coverImage"
        class="split-login-cover"
        :src="coverImage"
        fit="cover"
        lazy
      >
        <template v-slot:error>
          <div class="split-login-cover-fallback"></div>
        </template>
      </el-image>
      <div class="split-login-brand-mask"></div>
      <div class="split-login-brand-content">
        <img
          v-if="logoImage"
          :src="logoImage"
          :alt="siteTitle"
          class="split-login-logo"
        />
        <span v-else class="split-login-name">{{ siteTitle }}</span>
        <!-- 有 Logo 时才显示标题副标，避免与文字品牌重复 -->
        <p v-if="logoImage && siteTitle" class="split-login-slogan">{{ siteTitle }}</p>
      </div>
    </div>

    <!-- 右侧表单区：内嵌简约卡片（embedded 形态） -->
    <div class="split-login-form">
      <LoginCard
        variant="embedded"
        :account="account"
        :password="password"
        :username="username"
        :email="email"
        :code="code"
        :logo-image="''"
        :site-title="siteTitle"
        :providers="providers"
        :third-position="thirdPosition"
        @update:account="$emit('update:account', $event)"
        @update:password="$emit('update:password', $event)"
        @update:username="$emit('update:username', $event)"
        @update:email="$emit('update:email', $event)"
        @update:code="$emit('update:code', $event)"
        @login="$emit('login')"
        @register="$emit('register')"
        @forgot="$emit('forgot')"
        @email-code="$emit('email-code')"
        @third-party="$emit('third-party', $event)"
      ></LoginCard>
    </div>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'

export default {
  name: 'SplitLoginPanel',
  components: {
    LoginCard: defineAsyncComponent(() => import('./LoginCard.vue')),
  },
  props: {
    account: {
      type: String,
      default: '',
    },
    password: {
      type: String,
      default: '',
    },
    username: {
      type: String,
      default: '',
    },
    email: {
      type: String,
      default: '',
    },
    code: {
      type: String,
      default: '',
    },
    logoImage: {
      type: String,
      default: '',
    },
    // 网站标题（webTitle，站点必填字段，无默认兜底）
    siteTitle: {
      type: String,
      default: '',
    },
    coverImage: {
      type: String,
      default: '',
    },
    providers: {
      type: Array,
      default: () => [],
    },
    // 第三方按钮位置（透传给内嵌 LoginCard）
    thirdPosition: {
      type: String,
      default: 'top',
    },
  },
  emits: [
    'update:account',
    'update:password',
    'update:username',
    'update:email',
    'update:code',
    'login',
    'register',
    'forgot',
    'email-code',
    'third-party',
  ],
}
</script>

<style>
.split-login {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  height: 100vh;
  background: var(--background);
}

/* 左侧品牌区 */
.split-login-brand {
  position: relative;
  flex: 1 1 55%;
  overflow: hidden;
}

.split-login-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.split-login-cover-fallback {
  width: 100%;
  height: 100%;
  background: #2b2b2b;
}

/* 深色遮罩保证品牌文字可读 */
.split-login-brand-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55), rgba(0, 0, 0, 0.2));
}

.split-login-brand-content {
  position: absolute;
  left: 48px;
  bottom: 48px;
  z-index: 1;
  color: #ffffff;
}

.split-login-logo {
  max-height: 52px;
  max-width: 260px;
  object-fit: contain;
}

.split-login-name {
  font-size: 32px;
  font-weight: 600;
  letter-spacing: 2px;
}

.split-login-slogan {
  margin-top: 14px;
  max-width: 420px;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.85);
}

/* 右侧表单区 */
.split-login-form {
  flex: 1 1 45%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 32px;
  background: var(--background);
  overflow-y: auto;
}

/* 移动端：收起左栏，只留表单 */
@media screen and (max-width: 768px) {
  .split-login-brand {
    display: none;
  }

  .split-login-form {
    flex: 1 1 100%;
  }
}
</style>
