<template>
  <div class="login-card" :class="'login-card--' + variant">
    <!-- 站点 Logo（仅在配置了 logo 图片时显示，居中） -->
    <div v-if="logoImage" class="login-card-logo-wrap">
      <img :src="logoImage" :alt="siteTitle" class="login-card-logo" />
    </div>

    <!-- 标题：登录/注册 + 网站标题（左对齐），副标题补层级 -->
    <div class="login-card-head">
      <h1 class="login-card-title">
        {{ activeTab === 'login' ? '登录' : '注册' }}
        <span v-if="siteTitle" class="login-card-title-site">{{ siteTitle }}</span>
      </h1>
      <p class="login-card-subtitle">
        {{ activeTab === 'login' ? '欢迎回来' : '创建一个新账号' }}
      </p>
    </div>

    <!-- 第三方登录（位置可配：top 置顶 + 下接"或"分隔线） -->
    <div
      v-if="providers.length > 0 && thirdPosition !== 'bottom'"
      class="login-card-third"
    >
      <div class="login-card-providers">
        <a
          v-for="provider in providers"
          :key="provider.key"
          href="javascript:void(0)"
          :title="provider.title"
          class="login-card-provider"
          @click="$emit('third-party', provider.key)"
        >
          <img :src="provider.icon" :alt="provider.name" />
          <span>{{ provider.name }}</span>
        </a>
      </div>
      <div class="login-card-divider"><span>或</span></div>
    </div>

    <!-- 登录表单 -->
    <form
      v-if="activeTab === 'login'"
      class="login-card-form"
      autocomplete="on"
      @submit.prevent="$emit('login')"
    >
      <div class="login-card-field">
        <input
          class="login-card-input"
          :value="account"
          type="text"
          name="username"
          placeholder=" "
          @input="$emit('update:account', $event.target.value)"
        />
        <label class="login-card-float-label">用户名/邮箱/手机号</label>
      </div>
      <div class="login-card-field login-card-password">
        <input
          class="login-card-input"
          :value="password"
          :type="showLoginPassword ? 'text' : 'password'"
          name="password"
          placeholder=" "
          @input="$emit('update:password', $event.target.value)"
        />
        <label class="login-card-float-label">密码</label>
        <span
          v-show="password"
          role="button"
          tabindex="0"
          class="login-card-password-toggle"
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
      <!-- bottom 模式：忘记密码右对齐小字，紧贴登录按钮上方 -->
      <div
        v-if="thirdPosition === 'bottom'"
        class="login-card-forgot-inline"
      >
        <a href="#" @click.prevent="$emit('forgot')">忘记密码？</a>
      </div>
      <button type="submit" class="login-card-submit">登 录</button>
    </form>

    <!-- 注册表单 -->
    <form
      v-else
      class="login-card-form"
      autocomplete="on"
      @submit.prevent="$emit('register')"
    >
      <div class="login-card-field">
        <input
          class="login-card-input"
          :value="username"
          type="text"
          maxlength="30"
          placeholder=" "
          @input="$emit('update:username', $event.target.value)"
        />
        <label class="login-card-float-label">用户名</label>
      </div>
      <div class="login-card-field login-card-password">
        <input
          class="login-card-input"
          :value="password"
          :type="showRegisterPassword ? 'text' : 'password'"
          maxlength="30"
          placeholder=" "
          @input="$emit('update:password', $event.target.value)"
        />
        <label class="login-card-float-label">密码</label>
        <span
          v-show="password"
          role="button"
          tabindex="0"
          class="login-card-password-toggle"
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
      <div class="login-card-field">
        <input
          class="login-card-input"
          :value="email"
          type="email"
          placeholder=" "
          @input="$emit('update:email', $event.target.value)"
        />
        <label class="login-card-float-label">邮箱</label>
      </div>
      <div class="login-card-field">
        <div class="login-card-code-row">
          <div class="login-card-code-float">
            <input
              class="login-card-input"
              :value="code"
              type="text"
              readonly
              placeholder=" "
              @click="$emit('email-code')"
            />
            <label class="login-card-float-label">验证码</label>
          </div>
          <button
            type="button"
            class="login-card-code-btn"
            @click="$emit('email-code')"
          >
            获取验证码
          </button>
        </div>
      </div>
      <button type="submit" class="login-card-submit">注 册</button>
    </form>

    <!-- 底部链接区（居中：先切换、后找回），紧跟表单 -->
    <div class="login-card-switch">
      <template v-if="activeTab === 'login'">
        <span>没有账号？</span>
        <a href="#" @click.prevent="activeTab = 'register'">立即注册</a>
      </template>
      <template v-else>
        <span>已有账号？</span>
        <a href="#" @click.prevent="activeTab = 'login'">去登录</a>
      </template>
    </div>
    <!-- top 模式的找回行（bottom 模式已移至登录按钮上方右对齐） -->
    <div
      v-if="activeTab === 'login' && thirdPosition !== 'bottom'"
      class="login-card-forgot"
    >
      <span>忘记了您的</span>
      <a href="#" @click.prevent="$emit('forgot')">密码</a>
      <span>？</span>
    </div>

    <!-- 第三方登录（bottom 位置：整卡收尾，"或"分隔线后接纯图标行，不带平台名） -->
    <div
      v-if="providers.length > 0 && thirdPosition === 'bottom'"
      class="login-card-third login-card-third--bottom"
    >
      <div class="login-card-divider"><span>第三方账号登录</span></div>
      <div class="login-card-provider-icons">
        <a
          v-for="provider in providers"
          :key="provider.key"
          href="javascript:void(0)"
          :title="provider.title || provider.name"
          :aria-label="provider.name"
          class="login-card-provider-icon"
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
  name: 'LoginCard',
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
    // 卡片形态：card 独立卡片 / glass 毛玻璃 / minimal 极简无卡片 / embedded 分栏内嵌
    variant: {
      type: String,
      default: 'card',
      validator: (value) =>
        ['card', 'glass', 'minimal', 'embedded'].includes(value),
    },
    // 第三方按钮位置：top 表单上方（默认）/ bottom 表单下方
    thirdPosition: {
      type: String,
      default: 'top',
      validator: (value) => ['top', 'bottom'].includes(value),
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
      showLoginPassword: false,
      showRegisterPassword: false,
      // 眼睛图标路径（显示/隐藏密码）
      eyePath:
        'M1 12C2.73 7.91 7 4.8 12 4.8S21.27 7.91 23 12C21.27 16.09 17 19.2 12 19.2S2.73 16.09 1 12Z',
      eyeOffPath:
        'M3 3L21 21M10.58 10.58A2 2 0 0 0 13.41 13.41M9.88 5.09A9.77 9.77 0 0 1 12 4.8c5 0 9.27 3.11 11 7.2a11.83 11.83 0 0 1-4.05 5.19M6.61 6.61A11.8 11.8 0 0 0 1 12c1.73 4.09 6 7.2 11 7.2a9.6 9.6 0 0 0 4.24-.93M14.12 14.12A3 3 0 0 1 9.88 9.88',
    }
  },
  watch: {
    // 切换面板时收起密码明文，避免状态串到另一个表单
    activeTab() {
      this.showLoginPassword = false
      this.showRegisterPassword = false
    },
  },
}
</script>

<style>
/* 中性黑白灰卡片，全部走全局CSS变量，暗色模式自动适配 */
.login-card {
  position: relative;
  z-index: 1;
  width: min(420px, calc(100% - 32px));
  padding: 36px 34px 30px;
  border-radius: 18px;
  background: var(--background);
  /* 分层阴影代替"边框+单层大阴影"：近层定轮廓、中层托浮起、远层造环境光 */
  box-shadow: 0 1px 2px var(--miniMask), 0 10px 28px var(--miniMask),
    0 32px 72px var(--miniMask);
  font-family: var(--globalFont), serif;
  animation: login-card-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}

/* 入场：上浮 + 淡入，一次性不打扰 */
@keyframes login-card-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-card {
    animation: none;
  }
}

/* 暗色模式下阴影不足以分离层级，补一根极淡描边 */
body.dark-mode .login-card {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08), 0 10px 28px rgba(0, 0, 0, 0.4);
}

/* Logo 居中 */
.login-card-logo-wrap {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.login-card-logo {
  max-height: 42px;
  max-width: 60%;
  object-fit: contain;
}

/* 标题（左对齐） */
.login-card-head {
  margin-bottom: 22px;
}

.login-card-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--fontColor);
}

/* 网站标题与“登录/注册”同色同字重，整体一句话 */
.login-card-title-site {
  margin-left: 6px;
}

/* 副标题：补充层级，避免标题孤立悬浮 */
.login-card-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--greyFont);
}

/* 第三方登录：置顶带名称按钮 */
.login-card-third {
  margin-bottom: 4px;
}

/* 第三方登录：6 列网格实现"每行最多 3 个"，
   余数行自动铺满（4 个 = 3+整行；5 个 = 3+两半；1 个独占、2 个平分） */
.login-card-providers {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.login-card-provider {
  grid-column: span 2;
}

/* 余 1：最后一个占整行 */
.login-card-provider:nth-child(3n + 1):nth-last-child(1) {
  grid-column: span 6;
}

/* 余 2：最后两个平分一行 */
.login-card-provider:nth-child(3n + 1):nth-last-child(2),
.login-card-provider:nth-child(3n + 2):nth-last-child(1) {
  grid-column: span 3;
}

.login-card-provider {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--lightGray);
  border-radius: 12px;
  color: var(--fontColor);
  font-size: 14px;
  text-decoration: none;
  transition: border-color 0.25s ease, background 0.25s ease, transform 0.25s ease;
}

.login-card-provider:hover {
  border-color: var(--greyFont);
  transform: translateY(-1px);
}

.login-card-provider:active {
  transform: scale(0.97);
}

.login-card-provider img {
  width: 20px;
  height: 20px;
  object-fit: contain;
  flex-shrink: 0;
}

/* 三列时单格较窄，长平台名省略号截断 */
.login-card-provider span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* bottom 位置：分隔线在前、纯图标行在后 */
.login-card-third--bottom {
  margin-bottom: 0;
}

.login-card-third--bottom .login-card-divider {
  margin: 18px 0 14px;
}

/* 纯图标行：居中圆形按钮，悬停显示平台名 tooltip */
.login-card-provider-icons {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14px;
}

.login-card-provider-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--lightGray);
  border-radius: 50%;
  transition: border-color 0.25s ease, transform 0.25s ease,
    box-shadow 0.25s ease;
}

.login-card-provider-icon:hover {
  border-color: var(--greyFont);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--miniMask);
}

.login-card-provider-icon:active {
  transform: scale(0.94);
}

.login-card-provider-icon img {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

/* “或”分隔线 */
.login-card-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0 4px;
  color: var(--greyFont);
  font-size: 12px;
}

.login-card-divider::before,
.login-card-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--lightGray);
}

/* 表单 */
.login-card-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 浮动标签字段：提示在框内，聚焦/有值时上浮为小标签 */
.login-card-field {
  position: relative;
}

.login-card-input {
  width: 100%;
  height: 50px;
  padding: 20px 14px 6px;
  /* 填充式输入框：静止无边框浅灰底，聚焦时提亮为卡片底色 + 主题色描边 */
  border: 1px solid transparent;
  border-radius: 12px;
  background: var(--maxLightGray);
  color: var(--fontColor);
  font-size: 14px;
  outline: none;
  transition: border-color 0.25s ease, box-shadow 0.25s ease,
    background 0.25s ease;
  box-sizing: border-box;
}

.login-card-input:hover {
  border-color: var(--lightGray);
}

.login-card-float-label {
  position: absolute;
  left: 15px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--greyFont);
  font-size: 14px;
  pointer-events: none;
  transition: top 0.2s ease, transform 0.2s ease, font-size 0.2s ease,
    color 0.2s ease;
}

/* placeholder=" " 占位技巧：聚焦或已输入时标签上浮缩小 */
.login-card-input:focus + .login-card-float-label,
.login-card-input:not(:placeholder-shown) + .login-card-float-label {
  top: 7px;
  transform: none;
  font-size: 11px;
}

.login-card-input:focus + .login-card-float-label {
  color: var(--loginAccent, var(--fontColor));
}

.login-card-input:focus {
  border-color: var(--loginAccent, var(--fontColor));
  background: var(--background);
  box-shadow: 0 0 0 3px var(--loginAccentSoft, var(--miniMask));
}

.login-card-password {
  position: relative;
}

.login-card-password .login-card-input {
  padding-right: 42px;
}

.login-card-password-toggle {
  position: absolute;
  top: 50%;
  right: 12px;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  color: var(--greyFont);
  cursor: pointer;
}

.login-card-password-toggle:hover {
  color: var(--fontColor);
}

/* bottom 模式：忘记密码右对齐小字（登录按钮上方） */
.login-card-forgot-inline {
  display: flex;
  justify-content: flex-end;
  margin: -6px 0 -4px;
  font-size: 13px;
}

.login-card-forgot-inline a {
  color: var(--greyFont);
  text-decoration: none;
}

.login-card-forgot-inline a:hover {
  color: var(--loginAccent, var(--fontColor));
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 忘记密码（top 模式：底部居中） */
.login-card-forgot {
  margin-top: 10px;
  text-align: center;
  font-size: 13px;
  color: var(--greyFont);
}

.login-card-forgot a {
  color: var(--loginAccent, var(--fontColor));
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 验证码行：浮动标签输入框 + 内联获取按钮 */
.login-card-code-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.login-card-code-float {
  position: relative;
  flex: 1;
}

.login-card-code-float .login-card-input {
  cursor: pointer;
}

.login-card-code-btn {
  flex-shrink: 0;
  height: 50px;
  padding: 0 14px;
  border: 1px solid var(--lightGray);
  border-radius: 12px;
  background: transparent;
  color: var(--loginAccent, var(--fontColor));
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.25s ease;
}

.login-card-code-btn:hover {
  border-color: var(--loginAccent, var(--greyFont));
}

/* 主按钮：全宽，默认近黑，配置主题色时跟随 --loginAccent，带图标 */
.login-card-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 44px;
  margin-top: 6px;
  border: none;
  border-radius: 12px;
  /* 主按钮用专属变量：豁免暗色重映射，两种模式保持原色 */
  background: var(--loginAccentButton, #1f1f1f);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  transition: filter 0.25s ease, transform 0.25s ease, box-shadow 0.25s ease;
}

.login-card-submit:hover {
  filter: brightness(1.25);
  transform: translateY(-2px);
  /* 主题色同色投影，比纯黑阴影更有光源感 */
  box-shadow: 0 8px 20px var(--loginAccentSoft, var(--miniMask));
}

/* 按压反馈：模拟物理点击 */
.login-card-submit:active {
  transform: translateY(0) scale(0.98);
  box-shadow: none;
}

/* 登录/注册互切链接（居中） */
.login-card-switch {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--greyFont);
}

.login-card-switch a {
  margin-left: 4px;
  color: var(--loginAccent, var(--fontColor));
  font-weight: 600;
  text-decoration: none;
}

.login-card-switch a:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

/* 暗色模式：卡内链接/聚焦描边/投影重映射为暗色安全值（过暗自动提亮）；
   主按钮走 --loginAccentButton 不受影响；弹窗与验证码是浅色表面，仍用原主题色 */
body.dark-mode .login-card {
  --loginAccent: var(--loginAccentDark, #f5f5f5);
  --loginAccentSoft: var(--loginAccentSoftDark, rgba(245, 245, 245, 0.25));
}

/* 暗色模式：主按钮保持原色，仅加一圈极淡描边定义边缘 */
body.dark-mode .login-card-submit {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12);
}

/* glass 变体：磨砂半透明，透出背景大图（基础卡片已无边框，这里需完整声明） */
.login-card--glass {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(16px) saturate(1.2);
  -webkit-backdrop-filter: blur(16px) saturate(1.2);
}

/* 半透明磨砂底上 --lightGray(#ddd) 近乎隐形，
   改用半透明深色边框 + 微白底提升输入区对比度 */
.login-card--glass .login-card-input,
.login-card--glass .login-card-provider,
.login-card--glass .login-card-provider-icon,
.login-card--glass .login-card-code-btn {
  border-color: rgba(60, 60, 60, 0.32);
}

.login-card--glass .login-card-input {
  background: rgba(255, 255, 255, 0.45);
}

/* "或"分隔线的两根线同样需要半透明深色才看得见 */
.login-card--glass .login-card-divider::before,
.login-card--glass .login-card-divider::after {
  background: rgba(60, 60, 60, 0.32);
}

body.dark-mode .login-card--glass {
  background: rgba(32, 32, 32, 0.66);
  border-color: rgba(255, 255, 255, 0.12);
}

body.dark-mode .login-card--glass .login-card-input,
body.dark-mode .login-card--glass .login-card-provider,
body.dark-mode .login-card--glass .login-card-provider-icon,
body.dark-mode .login-card--glass .login-card-code-btn {
  border-color: rgba(255, 255, 255, 0.28);
}

body.dark-mode .login-card--glass .login-card-input {
  background: rgba(0, 0, 0, 0.25);
}

/* glass 聚焦态：覆盖高优先级的变体边框色，保证主题色描边可见 */
.login-card--glass .login-card-input:focus {
  border-color: var(--loginAccent, var(--fontColor));
  background: rgba(255, 255, 255, 0.7);
}

body.dark-mode .login-card--glass .login-card-input:focus {
  border-color: var(--loginAccent, #f5f5f5);
  background: rgba(0, 0, 0, 0.4);
}

body.dark-mode .login-card--glass .login-card-divider::before,
body.dark-mode .login-card--glass .login-card-divider::after {
  background: rgba(255, 255, 255, 0.28);
}

/* minimal 变体：无卡片外观，融入纯色页面 */
.login-card--minimal {
  border: none;
  box-shadow: none;
  background: transparent;
  width: min(360px, calc(100% - 32px));
}

/* embedded 变体：分栏布局右栏内嵌，无卡片外观 */
.login-card--embedded {
  border: none;
  box-shadow: none;
  background: transparent;
  width: min(400px, 100%);
  padding: 0;
}

@media screen and (max-width: 480px) {
  .login-card {
    padding: 28px 20px 24px;
  }

  .login-card--embedded {
    padding: 0;
  }

  /* 窄屏下沿用"每行最多 3 个"网格，仅收紧间距与字号 */
  .login-card-providers {
    gap: 8px;
  }

  .login-card-provider {
    font-size: 13px;
    padding: 0 8px;
    gap: 6px;
  }
}

/* 矮视口（iPhone SE 等 ≤700px 高）：收紧垂直节奏，尽量让注册面板一屏放下 */
@media screen and (max-height: 700px) {
  .login-card {
    padding: 22px 24px 18px;
  }

  .login-card--embedded {
    padding: 0;
  }

  .login-card-logo-wrap {
    margin-bottom: 12px;
  }

  .login-card-logo {
    max-height: 34px;
  }

  .login-card-head {
    margin-bottom: 14px;
  }

  .login-card-title {
    font-size: 22px;
  }

  .login-card-form {
    gap: 12px;
  }

  .login-card-input,
  .login-card-code-btn {
    height: 46px;
  }

  .login-card-provider {
    height: 38px;
  }

  .login-card-submit {
    height: 40px;
  }

  .login-card-divider {
    margin: 12px 0 2px;
  }

  .login-card-switch {
    margin-top: 14px;
  }
}
</style>
