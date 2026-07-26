<template>
  <div class="terminal-login">
    <!-- 窗口标题栏 -->
    <div class="terminal-login-titlebar">
      <span class="terminal-login-dot"></span>
      <span class="terminal-login-dot"></span>
      <span class="terminal-login-dot"></span>
      <span class="terminal-login-title">guest@{{ siteTitle }}: ~/login</span>
    </div>

    <div class="terminal-login-body">
      <!-- 模式切换：伪命令行 -->
      <div class="terminal-login-line">
        <span class="terminal-login-prompt">$</span>
        <span>auth --mode</span>
        <button
          type="button"
          :class="['terminal-login-mode', { active: activeTab === 'login' }]"
          @click="activeTab = 'login'"
        >
          login
        </button>
        <span class="terminal-login-sep">|</span>
        <button
          type="button"
          :class="['terminal-login-mode', { active: activeTab === 'register' }]"
          @click="activeTab = 'register'"
        >
          register
        </button>
      </div>

      <!-- 登录表单 -->
      <form
        v-if="activeTab === 'login'"
        autocomplete="on"
        @submit.prevent="$emit('login')"
      >
        <label class="terminal-login-line">
          <span class="terminal-login-prompt">&gt;</span>
          <span class="terminal-login-label">account:</span>
          <input
            :value="account"
            type="text"
            name="username"
            class="terminal-login-input"
            spellcheck="false"
            placeholder="用户名/邮箱/手机号"
            @input="$emit('update:account', $event.target.value)"
          />
        </label>
        <label class="terminal-login-line">
          <span class="terminal-login-prompt">&gt;</span>
          <span class="terminal-login-label">password:</span>
          <input
            :value="password"
            :type="showPassword ? 'text' : 'password'"
            name="password"
            class="terminal-login-input"
            placeholder="密码"
            @input="$emit('update:password', $event.target.value)"
          />
          <button
            v-show="password"
            type="button"
            class="terminal-login-toggle"
            @click="showPassword = !showPassword"
          >
            [{{ showPassword ? 'hide' : 'show' }}]
          </button>
        </label>
        <div class="terminal-login-line">
          <span class="terminal-login-prompt">$</span>
          <a href="#" class="terminal-login-link" @click.prevent="$emit('forgot')"
            >forgot-password</a
          >
        </div>
        <div class="terminal-login-line">
          <span class="terminal-login-prompt">$</span>
          <button type="submit" class="terminal-login-submit">
            ./login.sh --run<span class="terminal-login-cursor"></span>
          </button>
        </div>
      </form>

      <!-- 注册表单 -->
      <form v-else autocomplete="on" @submit.prevent="$emit('register')">
        <label class="terminal-login-line">
          <span class="terminal-login-prompt">&gt;</span>
          <span class="terminal-login-label">username:</span>
          <input
            :value="username"
            type="text"
            maxlength="30"
            class="terminal-login-input"
            spellcheck="false"
            placeholder="用户名"
            @input="$emit('update:username', $event.target.value)"
          />
        </label>
        <label class="terminal-login-line">
          <span class="terminal-login-prompt">&gt;</span>
          <span class="terminal-login-label">password:</span>
          <input
            :value="password"
            :type="showPassword ? 'text' : 'password'"
            maxlength="30"
            class="terminal-login-input"
            placeholder="密码"
            @input="$emit('update:password', $event.target.value)"
          />
          <button
            v-show="password"
            type="button"
            class="terminal-login-toggle"
            @click="showPassword = !showPassword"
          >
            [{{ showPassword ? 'hide' : 'show' }}]
          </button>
        </label>
        <label class="terminal-login-line">
          <span class="terminal-login-prompt">&gt;</span>
          <span class="terminal-login-label">email:</span>
          <input
            :value="email"
            type="email"
            class="terminal-login-input"
            spellcheck="false"
            placeholder="邮箱"
            @input="$emit('update:email', $event.target.value)"
          />
        </label>
        <div class="terminal-login-line">
          <span class="terminal-login-prompt">&gt;</span>
          <span class="terminal-login-label">code:</span>
          <input
            :value="code"
            type="text"
            class="terminal-login-input"
            placeholder="验证码"
            readonly
            @click="$emit('email-code')"
          />
          <a
            href="#"
            class="terminal-login-link"
            @click.prevent="$emit('email-code')"
            >[send-code]</a
          >
        </div>
        <div class="terminal-login-line">
          <span class="terminal-login-prompt">$</span>
          <button type="submit" class="terminal-login-submit">
            ./register.sh --run<span class="terminal-login-cursor"></span>
          </button>
        </div>
      </form>

      <!-- 第三方登录 -->
      <div v-if="providers.length > 0" class="terminal-login-third">
        <div class="terminal-login-line">
          <span class="terminal-login-prompt">$</span>
          <span>oauth --provider</span>
        </div>
        <div class="terminal-login-providers">
          <a
            v-for="provider in providers"
            :key="provider.key"
            href="javascript:void(0)"
            :title="provider.title"
            class="terminal-login-provider"
            @click="$emit('third-party', provider.key)"
          >
            <img :src="provider.icon" :alt="provider.name" />
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TerminalLoginPanel',
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
    // 切换模式时收起密码明文
    activeTab() {
      this.showPassword = false
    },
  },
}
</script>

<style>
/* 终端极客风：纯黑白灰单色系（登录页禁用粉色调规范） */
.terminal-login {
  position: relative;
  z-index: 1;
  width: min(560px, calc(100% - 32px));
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #3a3a3a;
  background: #161616;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
  font-family: 'Cascadia Code', 'JetBrains Mono', Consolas, 'Courier New',
    monospace;
  font-size: 14px;
  color: #d4d4d4;
}

/* 标题栏 */
.terminal-login-titlebar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #262626;
  border-bottom: 1px solid #3a3a3a;
}

.terminal-login-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #4a4a4a;
}

.terminal-login-title {
  margin-left: 8px;
  font-size: 12px;
  color: #8a8a8a;
  user-select: none;
}

.terminal-login-body {
  padding: 20px 22px 24px;
}

/* 行结构 */
.terminal-login-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
}

.terminal-login-prompt {
  color: #8a8a8a;
  user-select: none;
}

.terminal-login-label {
  color: #9c9c9c;
  user-select: none;
  white-space: nowrap;
}

/* 输入框：无边框融入终端 */
.terminal-login-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: #f0f0f0;
  font: inherit;
  caret-color: #f0f0f0;
  padding: 6px 0;
}

.terminal-login-input::placeholder {
  color: #5c5c5c;
}

.terminal-login-input[readonly] {
  cursor: pointer;
}

/* 模式切换与链接 */
.terminal-login-mode {
  border: none;
  background: transparent;
  color: #8a8a8a;
  font: inherit;
  cursor: pointer;
  padding: 2px 4px;
}

.terminal-login-mode.active {
  color: #ffffff;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.terminal-login-sep {
  color: #4a4a4a;
  user-select: none;
}

.terminal-login-link {
  color: #b0b0b0;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.terminal-login-link:hover,
.terminal-login-mode:hover {
  color: #ffffff;
}

.terminal-login-toggle {
  border: none;
  background: transparent;
  color: #8a8a8a;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.terminal-login-toggle:hover {
  color: #ffffff;
}

/* 提交按钮：伪命令 + 闪烁光标 */
.terminal-login-submit {
  display: inline-flex;
  align-items: center;
  border: 1px solid #3a3a3a;
  border-radius: 6px;
  background: #202020;
  color: #f0f0f0;
  font: inherit;
  padding: 8px 14px;
  margin: 6px 0;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.terminal-login-submit:hover {
  background: #2c2c2c;
  border-color: #5c5c5c;
}

.terminal-login-cursor {
  display: inline-block;
  width: 8px;
  height: 15px;
  margin-left: 6px;
  background: #f0f0f0;
  animation: terminal-cursor-blink 1s steps(1) infinite;
}

@keyframes terminal-cursor-blink {
  50% {
    opacity: 0;
  }
}

/* 第三方登录 */
.terminal-login-third {
  margin-top: 14px;
  border-top: 1px dashed #3a3a3a;
  padding-top: 12px;
}

.terminal-login-providers {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding: 6px 0 0 22px;
}

.terminal-login-provider {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid #3a3a3a;
  border-radius: 6px;
  background: #202020;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.terminal-login-provider:hover {
  border-color: #6c6c6c;
  transform: translateY(-2px);
}

/* 图标统一去色，维持单色终端观感 */
.terminal-login-provider img {
  width: 20px;
  height: 20px;
  object-fit: contain;
  filter: grayscale(100%) brightness(1.6);
}

@media screen and (max-width: 480px) {
  .terminal-login-body {
    padding: 16px 14px 18px;
  }

  .terminal-login-label {
    min-width: 72px;
  }
}
</style>
