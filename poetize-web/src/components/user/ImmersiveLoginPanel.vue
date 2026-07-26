<template>
  <div class="immersive-login">
    <!-- 深色渐变遮罩：保证白色文字在任意封面图上可读 -->
    <div class="immersive-login-mask"></div>

    <div class="immersive-login-inner">
      <!-- 左侧：超大标题排版区（标题即视觉主体） -->
      <div class="immersive-login-hero">
        <h1 class="immersive-login-title">{{ siteTitle }}</h1>
        <p class="immersive-login-sub">
          {{ activeTab === 'login' ? '欢迎回来，登录以继续' : '创建账号，从这里开始' }}
        </p>
      </div>

      <!-- 右侧：无容器表单区（下划线输入框，文字直接写在图上） -->
      <div class="immersive-login-form-area">
        <!-- 登录表单 -->
        <form
          v-if="activeTab === 'login'"
          class="immersive-login-form"
          autocomplete="on"
          @submit.prevent="$emit('login')"
        >
          <input
            class="immersive-input"
            :value="account"
            type="text"
            name="username"
            placeholder="用户名 / 邮箱 / 手机号"
            @input="$emit('update:account', $event.target.value)"
          />
          <div class="immersive-password">
            <input
              class="immersive-input"
              :value="password"
              :type="showPassword ? 'text' : 'password'"
              name="password"
              placeholder="密码"
              @input="$emit('update:password', $event.target.value)"
            />
            <button
              v-show="password"
              type="button"
              class="immersive-password-toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
          <div class="immersive-login-links">
            <a href="#" @click.prevent="$emit('forgot')">忘记密码？</a>
          </div>
          <button type="submit" class="immersive-submit">
            <span>登 录</span>
            <span class="immersive-submit-arrow" aria-hidden="true">→</span>
          </button>
        </form>

        <!-- 注册表单 -->
        <form
          v-else
          class="immersive-login-form"
          autocomplete="on"
          @submit.prevent="$emit('register')"
        >
          <input
            class="immersive-input"
            :value="username"
            type="text"
            maxlength="30"
            placeholder="用户名"
            @input="$emit('update:username', $event.target.value)"
          />
          <div class="immersive-password">
            <input
              class="immersive-input"
              :value="password"
              :type="showPassword ? 'text' : 'password'"
              maxlength="30"
              placeholder="密码"
              @input="$emit('update:password', $event.target.value)"
            />
            <button
              v-show="password"
              type="button"
              class="immersive-password-toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
          <input
            class="immersive-input"
            :value="email"
            type="email"
            placeholder="邮箱"
            @input="$emit('update:email', $event.target.value)"
          />
          <div class="immersive-code-row">
            <input
              class="immersive-input"
              :value="code"
              type="text"
              placeholder="验证码"
              readonly
              @click="$emit('email-code')"
            />
            <a href="#" class="immersive-code-link" @click.prevent="$emit('email-code')"
              >获取验证码</a
            >
          </div>
          <button type="submit" class="immersive-submit">
            <span>注 册</span>
            <span class="immersive-submit-arrow" aria-hidden="true">→</span>
          </button>
        </form>

        <!-- 登录/注册互切 -->
        <div class="immersive-login-switch">
          <template v-if="activeTab === 'login'">
            <span>没有账号？</span>
            <a href="#" @click.prevent="activeTab = 'register'">立即注册</a>
          </template>
          <template v-else>
            <span>已有账号？</span>
            <a href="#" @click.prevent="activeTab = 'login'">去登录</a>
          </template>
        </div>

        <!-- 第三方登录 -->
        <div v-if="providers.length > 0" class="immersive-third">
          <div class="immersive-third-label">第三方账号登录</div>
          <div class="immersive-providers">
            <a
              v-for="provider in providers"
              :key="provider.key"
              href="javascript:void(0)"
              :title="provider.title"
              class="immersive-provider"
              @click="$emit('third-party', provider.key)"
            >
              <img :src="provider.icon" :alt="provider.name" />
            </a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ImmersiveLoginPanel',
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
      showPassword: false,
    }
  },
  watch: {
    // 切换面板时收起密码明文
    activeTab() {
      this.showPassword = false
    },
  },
}
</script>

<style>
/* 沉浸式大字排版：无容器设计，页面本身就是表单（黑白双色，符合登录页禁粉规范） */
.immersive-login {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* 左深右浅的渐变遮罩，压住封面图保证可读性 */
.immersive-login-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to right,
    rgba(0, 0, 0, 0.7) 0%,
    rgba(0, 0, 0, 0.45) 55%,
    rgba(0, 0, 0, 0.55) 100%
  );
}

.immersive-login-inner {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  height: 100%;
  padding: 0 8vw;
  box-sizing: border-box;
  gap: 6vw;
}

/* 左侧超大标题排版 */
.immersive-login-hero {
  flex: 1 1 auto;
  min-width: 0;
  color: #ffffff;
  user-select: none;
}

.immersive-login-title {
  margin: 0;
  font-family: var(--globalFont), serif;
  font-size: clamp(44px, 7vw, 104px);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 2px;
  word-break: break-word;
  text-shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
}

.immersive-login-sub {
  margin: 22px 0 0 4px;
  font-size: 15px;
  letter-spacing: 6px;
  color: rgba(255, 255, 255, 0.75);
}

/* 右侧无容器表单 */
.immersive-login-form-area {
  flex: 0 0 340px;
  max-width: 340px;
}

.immersive-login-form {
  display: flex;
  flex-direction: column;
  gap: 26px;
}

/* 下划线输入框：无边框无底色，文字直接写在图上 */
.immersive-input {
  width: 100%;
  padding: 10px 2px;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.35);
  background: transparent;
  color: #ffffff;
  font-size: 15px;
  letter-spacing: 1px;
  outline: none;
  border-radius: 0;
  box-sizing: border-box;
  caret-color: #ffffff;
  transition: border-color 0.3s ease;
}

.immersive-input::placeholder {
  color: rgba(255, 255, 255, 0.45);
}

.immersive-input:focus {
  border-bottom-color: #ffffff;
}

.immersive-input[readonly] {
  cursor: pointer;
}

/* 浏览器自动填充时保持透明底白字 */
.immersive-input:-webkit-autofill {
  -webkit-text-fill-color: #ffffff;
  -webkit-box-shadow: 0 0 0 1000px transparent inset;
  transition: background-color 9999s ease-in-out 0s;
}

.immersive-password {
  position: relative;
}

.immersive-password .immersive-input {
  padding-right: 44px;
}

.immersive-password-toggle {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  letter-spacing: 2px;
  cursor: pointer;
}

.immersive-password-toggle:hover {
  color: #ffffff;
}

.immersive-login-links {
  display: flex;
  justify-content: flex-end;
  margin-top: -12px;
}

.immersive-login-links a {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: none;
  transition: color 0.3s ease;
}

.immersive-login-links a:hover {
  color: #ffffff;
}

.immersive-code-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.immersive-code-row .immersive-input {
  flex: 1;
}

.immersive-code-link {
  flex-shrink: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: none;
}

.immersive-code-link:hover {
  color: #ffffff;
}

/* 白描边胶囊按钮，hover 反白 */
.immersive-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 46px;
  margin-top: 6px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 23px;
  background: transparent;
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  cursor: pointer;
  transition: background 0.3s ease, color 0.3s ease;
}

.immersive-submit:hover {
  background: #ffffff;
  color: #111111;
}

.immersive-submit-arrow {
  letter-spacing: 0;
  transition: transform 0.3s ease;
}

.immersive-submit:hover .immersive-submit-arrow {
  transform: translateX(4px);
}

/* 登录/注册互切 */
.immersive-login-switch {
  margin-top: 22px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.immersive-login-switch a {
  margin-left: 4px;
  color: #ffffff;
  font-weight: 600;
  text-decoration: none;
}

.immersive-login-switch a:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

/* 第三方登录 */
.immersive-third {
  margin-top: 30px;
}

.immersive-third-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.5);
}

.immersive-providers {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
}

.immersive-provider {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 50%;
  transition: border-color 0.3s ease, background 0.3s ease, transform 0.3s ease;
}

.immersive-provider:hover {
  border-color: #ffffff;
  background: rgba(255, 255, 255, 0.12);
  transform: translateY(-2px);
}

/* 图标统一白化，维持黑白双色观感 */
.immersive-provider img {
  width: 20px;
  height: 20px;
  object-fit: contain;
  filter: grayscale(100%) brightness(2.2);
}

/* 移动端：标题置顶缩小，表单在下 */
@media screen and (max-width: 768px) {
  .immersive-login-inner {
    flex-direction: column;
    justify-content: center;
    align-items: stretch;
    gap: 40px;
    padding: 0 8vw;
  }

  .immersive-login-hero {
    flex: 0 0 auto;
  }

  .immersive-login-title {
    font-size: clamp(34px, 10vw, 56px);
  }

  .immersive-login-sub {
    margin-top: 14px;
    letter-spacing: 4px;
  }

  .immersive-login-form-area {
    flex: 0 0 auto;
    max-width: none;
  }
}
</style>
