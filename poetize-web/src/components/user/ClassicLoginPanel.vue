<template>
  <div class="in-up" :class="{ 'right-panel-active': rightPanelActive }">
    <div class="form-container sign-up-container">
      <div class="myCenter">
        <h1>注册</h1>
        <input
          :value="username"
          type="text"
          maxlength="30"
          placeholder="用户名"
          @input="$emit('update:username', $event.target.value)"
        />
        <div class="password-field">
          <input
            :value="password"
            :type="showRegisterPassword ? 'text' : 'password'"
            maxlength="30"
            placeholder="密码"
            @input="$emit('update:password', $event.target.value)"
          />
          <span
            v-show="password"
            role="button"
            tabindex="0"
            class="password-toggle"
            :aria-label="showRegisterPassword ? '隐藏密码' : '显示密码'"
            :title="showRegisterPassword ? '隐藏密码' : '显示密码'"
            @click="showRegisterPassword = !showRegisterPassword"
            @keydown.enter.prevent="showRegisterPassword = !showRegisterPassword"
            @keydown.space.prevent="showRegisterPassword = !showRegisterPassword"
          >
            <svg v-if="showRegisterPassword" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path :d="eyeOffPath" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path :d="eyePath" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
              <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.8" />
            </svg>
          </span>
        </div>
        <input
          :value="email"
          type="email"
          placeholder="邮箱"
          @input="$emit('update:email', $event.target.value)"
        />
        <input
          :value="code"
          type="text"
          placeholder="验证码"
          disabled
          @keyup.enter="$emit('register')"
        />
        <a style="margin: 0" href="#" @click="$emit('email-code')">获取验证码</a>
        <el-button
          type="primary"
          round
          class="auth-button"
          @click="$emit('register')"
          >注册</el-button
        >
      </div>
    </div>
    <div class="form-container sign-in-container">
      <form
        class="myCenter login-credential-form"
        autocomplete="on"
        @submit.prevent="$emit('login')"
      >
        <h1>登录</h1>
        <input
          :value="account"
          type="text"
          name="username"
          placeholder="用户名/邮箱/手机号"
          @input="$emit('update:account', $event.target.value)"
        />
        <div class="password-field">
          <input
            :value="password"
            :type="showLoginPassword ? 'text' : 'password'"
            name="password"
            placeholder="密码"
            @input="$emit('update:password', $event.target.value)"
          />
          <span
            v-show="password"
            role="button"
            tabindex="0"
            class="password-toggle"
            :aria-label="showLoginPassword ? '隐藏密码' : '显示密码'"
            :title="showLoginPassword ? '隐藏密码' : '显示密码'"
            @click="showLoginPassword = !showLoginPassword"
            @keydown.enter.prevent="showLoginPassword = !showLoginPassword"
            @keydown.space.prevent="showLoginPassword = !showLoginPassword"
          >
            <svg v-if="showLoginPassword" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path :d="eyeOffPath" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path :d="eyePath" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
              <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.8" />
            </svg>
          </span>
        </div>
        <a href="#" @click="$emit('forgot')">忘记密码？</a>
        <el-button
          type="primary"
          round
          class="auth-button"
          native-type="submit"
          >登 录</el-button
        >

        <!-- 第三方登录区域 - 根据配置动态显示 -->
        <div v-if="providers.length > 0">
          <p
            style="
              text-align: center;
              margin-top: 20px;
              margin-bottom: 10px;
              font-size: 14px;
              color: var(--articleGreyFontColor);
            "
          >
            第三方账号登录
          </p>

          <div
            class="third-party-login-container"
            style="
              padding: 0;
              position: relative;
              height: 50px;
              width: 100%;
              text-align: center;
              overflow: visible;
            "
          >
            <a
              v-for="provider in providers"
              :key="provider.key"
              href="javascript:void(0)"
              @click="$emit('third-party', provider.key)"
              :title="provider.title"
              class="third-party-login-btn"
              style="
                display: inline-block;
                width: 40px;
                height: 40px;
                margin: 0 10px;
                border-radius: 50%;
                vertical-align: middle;
                position: relative;
                transition: transform 0.3s ease, opacity 0.3s ease;
                transform: translateZ(0);
              "
            >
              <img
                :src="provider.icon"
                :alt="provider.name"
                height="25"
                style="
                  position: absolute;
                  top: 50%;
                  left: 50%;
                  transform: translate(-50%, -50%);
                "
              />
            </a>
          </div>
        </div>
      </form>
    </div>
    <div class="overlay-container">
      <div class="overlay">
        <div class="overlay-panel myCenter overlay-left">
          <h1>已有帐号？</h1>
          <p>请登录🚀</p>
          <button class="ghost" @click="signIn()">登录</button>
        </div>
        <div class="overlay-panel myCenter overlay-right">
          <h1>没有帐号？</h1>
          <p>立即注册吧😃</p>
          <button class="ghost" @click="signUp()">注册</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ClassicLoginPanel',
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
      // 数据驱动的滑块面板切换（true 显示注册面板）
      rightPanelActive: false,
      showLoginPassword: false,
      showRegisterPassword: false,
      // 眼睛图标路径（显示/隐藏密码）
      eyePath:
        'M1 12C2.73 7.91 7 4.8 12 4.8S21.27 7.91 23 12C21.27 16.09 17 19.2 12 19.2S2.73 16.09 1 12Z',
      eyeOffPath:
        'M3 3L21 21M10.58 10.58A2 2 0 0 0 13.41 13.41M9.88 5.09A9.77 9.77 0 0 1 12 4.8c5 0 9.27 3.11 11 7.2a11.83 11.83 0 0 1-4.05 5.19M6.61 6.61A11.8 11.8 0 0 0 1 12c1.73 4.09 6 7.2 11 7.2a9.6 9.6 0 0 0 4.24-.93M14.12 14.12A3 3 0 0 1 9.88 9.88',
    }
  },
  methods: {
    signUp() {
      this.rightPanelActive = true
    },
    signIn() {
      // 从注册切回登录时，把已填的用户名带到登录账号输入框
      if (this.$common.isEmpty(this.account) && !this.$common.isEmpty(this.username)) {
        this.$emit('update:account', this.username.trim())
      }
      this.rightPanelActive = false
    },
  },
}
</script>

<style scoped>
.in-up {
  opacity: 0.9;
  border-radius: 10px;
  box-shadow: 0 15px 30px var(--miniMask), 0 10px 10px var(--miniMask);
  position: relative;
  overflow: hidden;
  width: 750px;
  max-width: 100%;
  min-height: 450px;
  margin: 10px;
}
.in-up p {
  font-size: 14px;
  letter-spacing: 1px;
  margin: 20px 0 30px 0;
  color: var(--articleGreyFontColor);
}
.in-up a {
  color: var(--fontColor);
  font-size: 14px;
  text-decoration: none;
  margin: 15px 0;
}
.form-container {
  position: absolute;
  height: 100%;
  transition: transform 0.5s ease-in-out, left 0.5s ease-in-out;
  will-change: transform, left;
  transform: translateZ(0);
}
.sign-in-container {
  left: 0;
  width: 50%;
  z-index: 2;
  visibility: visible;
  transition: all 0.5s ease-in-out;
}
.sign-up-container {
  left: 0;
  width: 50%;
  opacity: 0;
  z-index: 1;
  visibility: hidden;
  transition: all 0.5s ease-in-out;
}
.form-container > div,
.form-container > form {
  background: var(--background);
  flex-direction: column;
  padding: 10px 20px;
  height: 100%;
  color: var(--fontColor);
}
.login-credential-form {
  width: 100%;
}
.form-container input {
  background: var(--inputBackground);
  border-radius: 3px;
  border: none;
  padding: 12px 15px;
  margin: 10px 0;
  width: 90%;
  height: 40px;
  outline: none;
  color: var(--fontColor);
  line-height: 1.5;
  box-sizing: border-box;
}
.password-field {
  position: relative;
  width: 90%;
  height: 40px;
  margin: 10px 0;
}
.password-field input {
  width: 100%;
  height: 100%;
  margin: 0;
  padding-right: 42px;
  box-sizing: border-box;
}
.password-toggle {
  position: absolute;
  top: 50%;
  right: 12px;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--articleGreyFontColor);
  cursor: pointer;
  z-index: 2;
  line-height: 1;
  user-select: none;
}
.password-toggle:hover {
  color: var(--fontColor);
  transform: translateY(-50%);
}
.password-toggle:focus {
  outline: none;
  color: var(--fontColor);
}
.form-container input::placeholder {
  color: var(--articleGreyFontColor);
}
.in-up button {
  border-radius: 2rem;
  border: none;
  background: var(--gradualRed);
  color: var(--white);
  font-size: 16px;
  font-weight: bold;
  padding: 12px 45px;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 3px 3px 6px var(--miniMask), -1px -1px 4px var(--miniWhiteMask);
  transition: box-shadow 0.3s ease, transform 0.3s ease;
  transform: translateZ(0);
}
.in-up button:hover {
  background: var(--gradualRed);
  box-shadow: 4px 4px 8px var(--mask), -2px -2px 6px var(--miniWhiteMask);
  transform: translateY(-3px);
}
.in-up button:active {
  transform: translateY(1px);
  box-shadow: 2px 2px 4px var(--mask);
}
.in-up button.ghost {
  background: linear-gradient(145deg, var(--miniWhiteMask), var(--transparent));
  border: 1px solid var(--miniWhiteMask);
  box-shadow: 3px 3px 6px var(--mask), -1px -1px 4px var(--miniWhiteMask);
}
.in-up button.ghost:hover {
  background: linear-gradient(145deg, var(--whiteMask), var(--miniWhiteMask));
  box-shadow: 4px 4px 8px var(--translucent), -2px -2px 6px var(--miniWhiteMask);
  transform: translateY(-3px);
}
.in-up button.ghost:active {
  transform: translateY(1px);
  box-shadow: 2px 2px 4px var(--mask);
}
.sign-up-container button {
  margin-top: 20px;
}

/* 登录/注册按钮样式 */
.auth-button {
  border-radius: 10px !important;
  width: 90% !important;
  height: 40px !important;
  background: var(--gradualRed) !important;
  border: none !important;
  box-shadow: 3px 3px 6px var(--miniMask), -1px -1px 4px var(--miniWhiteMask) !important;
  transition: transform 0.3s ease, box-shadow 0.3s ease !important;
  padding: 12px 30px !important;
  font-weight: 600 !important;
  letter-spacing: 1px !important;
  transform: translateZ(0);
  line-height: 1.5 !important;
}

.auth-button:hover {
  background: var(--gradualRed) !important;
  box-shadow: 4px 4px 8px var(--mask), -2px -2px 6px var(--miniWhiteMask) !important;
  transform: translateY(-3px) !important;
}

.auth-button:active {
  transform: translateY(1px) !important;
  box-shadow: 2px 2px 4px var(--mask) !important;
}

.overlay-container {
  position: absolute;
  left: 50%;
  width: 50%;
  height: 100%;
  overflow: hidden;
  transition: transform 0.5s ease-in-out, left 0.5s ease-in-out;
  will-change: transform, left;
}
.overlay {
  background: var(--gradualRed);
  color: var(--white);
  position: relative;
  left: -100%;
  height: 100%;
  width: 200%;
}
.overlay-panel {
  position: absolute;
  top: 0;
  flex-direction: column;
  height: 100%;
  width: 50%;
  transition: transform 0.5s ease-in-out, left 0.5s ease-in-out;
  will-change: transform, left;
}
.overlay-panel p,
.overlay-panel h1 {
  color: var(--white);
}
.overlay-right {
  right: 0;
  transform: translateY(0);
  background: var(--gradualRed);
  box-shadow: -4px 0 15px rgba(0, 0, 0, 0.1);
  border-left: 1px solid rgba(255, 255, 255, 0.2);
}
.overlay-left {
  transform: translateY(-20%);
}
.in-up.right-panel-active .sign-in-container {
  transform: translateY(100%);
  opacity: 0;
  visibility: hidden;
  z-index: 1;
}
.in-up.right-panel-active .overlay-container {
  transform: translateX(-100%);
}
.in-up.right-panel-active .sign-up-container {
  transform: translateX(100%);
  opacity: 1;
  z-index: 5;
  visibility: visible;
}
.in-up.right-panel-active .overlay {
  transform: translateX(50%);
}
.in-up.right-panel-active .overlay-left {
  transform: translateY(0);
}
.in-up.right-panel-active .overlay-right {
  transform: translateY(20%);
}
div > a[href='javascript:void(0)'] {
  overflow: hidden;
}
div > a[href='javascript:void(0)']:hover {
  transform: scale(1.1);
  box-shadow: 0 0 10px var(--borderHoverColor);
}
div > a[href='javascript:void(0)']:hover::before {
  content: '';
  position: absolute;
  top: -10px;
  left: -10px;
  right: -10px;
  bottom: -10px;
  border-radius: 50%;
  animation: pulse 1s infinite;
  z-index: -1;
}
@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 var(--borderHoverColor);
  }
  70% {
    box-shadow: 0 0 0 5px var(--transparent);
  }
  100% {
    box-shadow: 0 0 0 0 var(--transparent);
  }
}
@media screen and (max-width: 480px) {
  .myCenter {
    flex-direction: column !important;
  }
}
@media screen and (max-width: 768px) {
  .third-party-login-container {
    height: auto !important;
    min-height: 50px !important;
    padding: 10px 5px !important;
    display: flex !important;
    flex-wrap: wrap !important;
    justify-content: center !important;
    align-items: center !important;
    gap: 8px !important;
    flex-direction: row !important;
  }
  .third-party-login-btn {
    width: 35px !important;
    height: 35px !important;
    margin: 4px !important;
    flex-shrink: 0 !important;
  }
  .third-party-login-btn img {
    height: 20px !important;
  }
}
@media screen and (max-width: 480px) {
  .third-party-login-container {
    padding: 8px 2px !important;
    gap: 4px !important;
    max-width: 100% !important;
    overflow: hidden !important;
    flex-direction: row !important;
  }
  .third-party-login-btn {
    width: 30px !important;
    height: 30px !important;
    margin: 2px !important;
  }
  .third-party-login-btn img {
    height: 17px !important;
  }
}
@media screen and (max-width: 420px) {
  .third-party-login-container {
    padding: 6px 1px !important;
    gap: 3px !important;
    flex-direction: row !important;
  }
  .third-party-login-btn {
    width: 28px !important;
    height: 28px !important;
    margin: 1px !important;
  }
  .third-party-login-btn img {
    height: 16px !important;
  }
}
@media screen and (max-width: 360px) {
  .third-party-login-container {
    padding: 4px 1px !important;
    gap: 2px !important;
  }
  .third-party-login-btn {
    width: 26px !important;
    height: 26px !important;
    margin: 1px !important;
  }
  .third-party-login-btn img {
    height: 15px !important;
  }
}
@media screen and (max-width: 320px) {
  .third-party-login-container {
    padding: 3px 1px !important;
    gap: 1px !important;
    max-width: 100% !important;
  }
  .third-party-login-btn {
    width: 24px !important;
    height: 24px !important;
    margin: 1px !important;
  }
  .third-party-login-btn img {
    height: 13px !important;
  }
}
</style>
