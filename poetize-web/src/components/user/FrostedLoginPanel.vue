<template>
  <div class="frosted-login">
    <!-- Logo 居中（无 Logo 时显示网站标题文字） -->
    <div class="frosted-login-logo-wrap">
      <img
        v-if="logoImage"
        :src="logoImage"
        :alt="siteTitle"
        class="frosted-login-logo"
      />
      <span v-else class="frosted-login-site-name">{{ siteTitle }}</span>
    </div>

    <!-- 标题 + 切换链接（左对齐，标题下方带玫红装饰条） -->
    <div class="frosted-login-head">
      <div class="frosted-login-title">
        {{ activeTab === 'login' ? '登录' : '注册' }}
      </div>
      <div
        class="frosted-login-switch"
        @click="activeTab = activeTab === 'login' ? 'register' : 'login'"
      >
        {{ activeTab === 'login' ? '没有账号？立即注册 >' : '已有账号？立即登录 >' }}
      </div>
    </div>

    <!-- 登录表单 -->
    <form
      v-if="activeTab === 'login'"
      autocomplete="on"
      @submit.prevent="$emit('login')"
    >
      <div class="frosted-login-line">
        <input
          class="frosted-login-input"
          :value="account"
          type="text"
          name="username"
          placeholder="用户名/邮箱"
          @input="$emit('update:account', $event.target.value)"
        />
      </div>
      <div class="frosted-login-line" style="margin-top: 20px">
        <input
          class="frosted-login-input"
          :value="password"
          type="password"
          name="password"
          autocomplete="current-password"
          placeholder="登录密码"
          @input="$emit('update:password', $event.target.value)"
        />
      </div>
      <div class="frosted-login-aux">
        <div class="frosted-login-aux-btn" @click="$emit('forgot')">忘记密码</div>
      </div>
      <button type="submit" class="frosted-login-btn frosted-login-btn--blue">
        <i class="fa fa-sign-out" aria-hidden="true"></i>
        登录
      </button>
    </form>

    <!-- 注册表单 -->
    <form v-else autocomplete="on" @submit.prevent="$emit('register')">
      <div class="frosted-login-line">
        <input
          class="frosted-login-input"
          :value="username"
          type="text"
          maxlength="30"
          placeholder="用户名"
          @input="$emit('update:username', $event.target.value)"
        />
      </div>
      <div class="frosted-login-line" style="margin-top: 20px">
        <input
          class="frosted-login-input"
          :value="password"
          type="password"
          autocomplete="new-password"
          maxlength="30"
          placeholder="登录密码"
          @input="$emit('update:password', $event.target.value)"
        />
      </div>
      <div class="frosted-login-line" style="margin-top: 20px">
        <input
          class="frosted-login-input"
          :value="email"
          type="email"
          placeholder="邮箱"
          @input="$emit('update:email', $event.target.value)"
        />
      </div>
      <div class="frosted-login-line" style="margin-top: 20px; position: relative">
        <input
          class="frosted-login-input"
          :value="code"
          type="text"
          placeholder="验证码"
          readonly
          @click="$emit('email-code')"
        />
        <button
          type="button"
          class="frosted-login-send-btn"
          @click="$emit('email-code')"
        >
          验证码
        </button>
      </div>
      <button type="submit" class="frosted-login-btn frosted-login-btn--green">
        <i class="fa fa-user-o" aria-hidden="true"></i>
        注册
      </button>
    </form>

    <!-- 第三方登录（居中小图标行） -->
    <div v-if="providers.length > 0" class="frosted-login-third">
      <div class="frosted-login-divider"><span>第三方账号登录</span></div>
      <div class="frosted-login-providers">
        <a
          v-for="provider in providers"
          :key="provider.key"
          href="javascript:void(0)"
          :title="provider.title"
          class="frosted-login-provider"
          @click="$emit('third-party', provider.key)"
        >
          <img :src="provider.icon" :alt="provider.name" />
        </a>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'FrostedLoginPanel',
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
    providers: {
      type: Array,
      default: () => [],
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
  data() {
    return {
      activeTab: 'login',
    }
  },
}
</script>

<style>
/* 磨砂典雅样式：磨砂玻璃定宽卡 + 左对齐大标题配玫红装饰条
   + 下划线式输入框 + 居中渐变胶囊按钮（蓝=登录/绿=注册） */
.frosted-login {
  position: relative;
  z-index: 1;
  width: min(400px, calc(100% - 32px));
  padding: 30px 30px 15px;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: saturate(5) blur(20px);
  -webkit-backdrop-filter: saturate(5) blur(20px);
  font-family: var(--globalFont), serif;
}

/* Logo 居中 */
.frosted-login-logo-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 6px;
}

.frosted-login-logo {
  height: 50px;
  max-width: 150px;
  object-fit: contain;
}

.frosted-login-site-name {
  font-size: 22px;
  font-weight: 600;
  color: #4e5358;
}

/* 标题区：左对齐大标题 + 玫红装饰条 + 切换链接 */
.frosted-login-head {
  margin-bottom: 14px;
}

.frosted-login-title {
  position: relative;
  display: inline-block;
  font-size: 30px;
  font-weight: 700;
  color: #4e5358;
}

.frosted-login-title::before {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 40px;
  height: 3px;
  background: var(--loginAccent, #f04494);
}

.frosted-login-switch {
  margin: 10px 0;
  font-size: 12px;
  color: #777777;
  cursor: pointer;
  user-select: none;
}

.frosted-login-switch:hover {
  color: var(--loginAccent, #f04494);
}

/* 下划线式输入行（无边框，仅 1px 浅灰底线；聚焦时主题色线从左到右展开） */
.frosted-login-line {
  position: relative;
  padding-bottom: 3px;
  background-image: linear-gradient(
    90deg,
    rgba(50, 50, 50, 0.12),
    rgba(50, 50, 50, 0.12)
  );
  background-size: 100% 1px;
  background-position: 0 100%;
  background-repeat: no-repeat;
}

/* 聚焦动效线：主题色纯色线，从左到右展开 */
.frosted-login-line::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 2px;
  background: var(--loginAccent, #f04494);
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform 0.35s ease;
  pointer-events: none;
}

.frosted-login-line:focus-within::after {
  transform: scaleX(1);
}

.frosted-login-input {
  width: 100%;
  padding: 6px 2px;
  border: none;
  background: transparent;
  color: #000000;
  font-size: 13.5px;
  outline: none;
  box-sizing: border-box;
}

.frosted-login-input::placeholder {
  color: #757575;
}

.frosted-login-input[readonly] {
  cursor: pointer;
}

/* 辅助链接（右对齐） */
.frosted-login-aux {
  display: flex;
  justify-content: flex-end;
}

.frosted-login-aux-btn {
  margin: 10px 2px;
  font-size: 13px;
  color: #999999;
  cursor: pointer;
  user-select: none;
}

.frosted-login-aux-btn:hover {
  color: var(--loginAccent, #f04494);
}

/* 渐变胶囊主按钮（约 80% 宽居中，蓝=登录 / 绿=注册） */
.frosted-login-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  width: 80%;
  height: 36px;
  margin: 30px auto 20px;
  border: none;
  border-radius: 25px;
  color: #ffffff;
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: box-shadow 0.3s ease;
}

.frosted-login-btn--blue {
  background: linear-gradient(135deg, #59c3fb 10%, #268df7 100%);
}

.frosted-login-btn--green {
  background: linear-gradient(135deg, #60e464 10%, #5cb85b 100%);
}

.frosted-login-btn:hover {
  box-shadow: 0 0 5px #39c5bb;
}

/* 验证码浮动按钮（浮在验证码输入行右端） */
.frosted-login-send-btn {
  position: absolute;
  right: 0;
  top: 0;
  height: 25px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: rgba(41, 151, 247, 0.1);
  color: #2997f7;
  font-size: 13px;
  cursor: pointer;
}

/* 第三方登录（居中小图标行） */
.frosted-login-third {
  margin-bottom: 10px;
}

.frosted-login-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #999999;
  font-size: 12px;
}

.frosted-login-divider::before,
.frosted-login-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(50, 50, 50, 0.12);
}

.frosted-login-providers {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 12px;
}

.frosted-login-provider {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  transition: transform 0.3s ease;
}

.frosted-login-provider:hover {
  transform: translateY(-2px) scale(1.08);
}

.frosted-login-provider img {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

/* 暗色模式：玻璃卡转深色半透明，文字提亮 */
body.dark-mode .frosted-login {
  background: rgba(32, 32, 32, 0.78);
}

body.dark-mode .frosted-login-title,
body.dark-mode .frosted-login-site-name {
  color: #e8e8e8;
}

body.dark-mode .frosted-login-switch {
  color: #aaaaaa;
}

body.dark-mode .frosted-login-line {
  background-image: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.18),
    rgba(255, 255, 255, 0.18)
  );
}

body.dark-mode .frosted-login-input {
  color: #f0f0f0;
}

body.dark-mode .frosted-login-input::placeholder {
  color: #8a8a8a;
}

@media screen and (max-width: 480px) {
  .frosted-login {
    padding: 24px 20px 12px;
  }
}
</style>
