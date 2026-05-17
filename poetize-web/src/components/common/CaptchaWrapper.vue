<template>
  <div class="captcha-wrapper" v-if="visible">
    <div v-if="isPreparing" class="captcha-loading-card">
      <button
        type="button"
        class="captcha-loading-close"
        aria-label="关闭验证"
        @click="onClose"
      >
        ×
      </button>
      <div class="captcha-loading-text">安全验证加载中...</div>
    </div>

    <!-- 根据实时窗口宽度选择验证码类型 -->
    <component
      :is="checkboxCaptchaComponent"
      v-else-if="
        configReady && shouldUseCheckboxCaptcha && checkboxCaptchaComponent
      "
      ref="checkboxCaptcha"
      :action="action"
      :track-sensitivity="checkboxOptions.trackSensitivity"
      :min-track-points="checkboxOptions.minTrackPoints"
      :is-reply-comment="isReplyComment"
      @success="onCheckboxSuccess"
      @fail="onCheckboxFail"
      @refresh="onCheckboxRefresh"
      @close="onClose"
    ></component>

    <!-- 小屏幕显示滑动验证 -->
    <component
      :is="slideCaptchaComponent"
      v-else-if="configReady && slideCaptchaComponent"
      ref="slideCaptcha"
      :accuracy="slideOptions.accuracy"
      :success-threshold="slideOptions.successThreshold"
      :slider-text="sliderText"
      :imgs="imgs"
      :action="action"
      @success="onSlideSuccess"
      @fail="onSlideFail"
      @refresh="onSlideRefresh"
      @close="onClose"
    ></component>
  </div>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'

export default {
  name: 'CaptchaWrapper',
  props: {
    // 显示状态
    visible: {
      type: Boolean,
      default: false,
    },
    // 操作类型
    action: {
      type: String,
      default: 'login',
    },
    // 强制使用滑动验证码
    forceSlide: {
      type: Boolean,
      default: false,
    },
    // 精确度（滑动验证使用）
    accuracy: {
      type: Number,
      default: 5,
    },
    // 滑块文本（滑动验证使用）
    sliderText: {
      type: String,
      default: '向右滑动完成验证',
    },
    // 自定义背景图（滑动验证使用）
    imgs: {
      type: Array,
      default: () => [],
    },
    // 是否为回复评论场景
    isReplyComment: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      currentWidth: window.innerWidth, // 当前窗口宽度
      verified: false, // 验证状态
      isTouchDevice: false, // 是否为触摸设备
      captchaConfig: null, // 验证码配置
      configReady: false,
      isPreparing: false,
      loadingConfigPromise: null,
      checkboxCaptchaComponent: null,
      slideCaptchaComponent: null,
      checkboxCaptchaLoadingPromise: null,
      slideCaptchaLoadingPromise: null,
    }
  },
  computed: {
    // 是否应该使用勾选验证码
    shouldUseCheckboxCaptcha() {
      // 如果强制使用滑动验证码，则返回false
      if (this.forceSlide) {
        return false
      }

      // 如果是触摸设备且配置为触摸设备强制使用滑动验证码
      if (
        this.isTouchDevice &&
        this.captchaConfig &&
        this.captchaConfig.forceSlideForMobile
      ) {
        return false
      }

      // 如果有配置且有屏幕阈值，根据阈值判断
      if (this.captchaConfig && this.captchaConfig.screenSizeThreshold) {
        return this.currentWidth >= this.captchaConfig.screenSizeThreshold
      }

      // 默认大屏使用勾选，小屏使用滑动
      return this.currentWidth >= 768
    },

    // 勾选验证码选项
    checkboxOptions() {
      const options = {
        trackSensitivity: 0.9, // 降低默认敏感度
        minTrackPoints: 2, // 降低默认轨迹点要求
      }

      // 如果有配置，使用配置中的参数
      if (this.captchaConfig && this.captchaConfig.checkbox) {
        if (this.captchaConfig.checkbox.trackSensitivity) {
          options.trackSensitivity =
            this.captchaConfig.checkbox.trackSensitivity
        }
        if (this.captchaConfig.checkbox.minTrackPoints) {
          options.minTrackPoints = this.captchaConfig.checkbox.minTrackPoints
        }
      }

      return options
    },

    // 滑动验证码选项
    slideOptions() {
      const options = {
        accuracy: this.accuracy || 5,
        successThreshold: 0.95,
      }

      // 如果有配置，使用配置中的参数
      if (this.captchaConfig && this.captchaConfig.slide) {
        if (this.captchaConfig.slide.accuracy) {
          options.accuracy = this.captchaConfig.slide.accuracy
        }
        if (this.captchaConfig.slide.successThreshold) {
          options.successThreshold = this.captchaConfig.slide.successThreshold
        }
      }

      return options
    },
  },
  watch: {
    // 监听visible属性变化，当显示验证码时更新窗口宽度和加载配置
    visible(newVal) {
      if (newVal) {
        this.prepareCaptcha()
      }
    },
  },
  mounted() {
    // 监听窗口大小变化
    window.addEventListener('resize', this.updateWidth)
    // 初始化时更新一次宽度
    this.updateWidth()
    // 检测是否为触摸设备
    this.detectTouchDevice()
    if (this.visible) {
      this.prepareCaptcha()
    }
  },
  beforeUnmount() {
    // 移除监听
    window.removeEventListener('resize', this.updateWidth)
  },
  methods: {
    /**
     * 关闭验证
     */
    onClose() {
      $emit(this, 'close')
    },

    /**
     * 更新窗口宽度
     */
    updateWidth() {
      const newWidth = window.innerWidth
      const wasLargeScreen = this.configReady
        ? this.shouldUseCheckboxCaptcha
        : null

      // 更新宽度
      this.currentWidth = newWidth

      // 如果屏幕类型发生变化（大变小或小变大），重置验证状态
      if (
        this.configReady &&
        wasLargeScreen !== this.shouldUseCheckboxCaptcha
      ) {
        this.isPreparing = true
        this.verified = false
        this.ensureCurrentCaptchaComponentLoaded()
          .then(() => {
            this.$nextTick(() => {
              this.reset()
            })
          })
          .finally(() => {
            this.isPreparing = false
          })
      }
    },

    /**
     * 检测是否为触摸设备
     */
    detectTouchDevice() {
      this.isTouchDevice =
        'ontouchstart' in window ||
        navigator.maxTouchPoints > 0 ||
        navigator.msMaxTouchPoints > 0
    },

    createDefaultCaptchaConfig() {
      return {
        screenSizeThreshold: 768,
        forceSlideForMobile: true,
        slide: { accuracy: 5, successThreshold: 0.95 },
        checkbox: { trackSensitivity: 0.9, minTrackPoints: 2 },
      }
    },

    prepareCaptcha() {
      this.updateWidth()
      this.detectTouchDevice()
      this.configReady = false
      this.isPreparing = true

      return this.loadCaptchaConfig()
        .then(() => this.ensureCurrentCaptchaComponentLoaded())
        .finally(() => {
          this.configReady = true
          this.isPreparing = false
        })
    },

    ensureCurrentCaptchaComponentLoaded() {
      return this.shouldUseCheckboxCaptcha
        ? this.ensureCheckboxCaptchaLoaded()
        : this.ensureSlideCaptchaLoaded()
    },

    ensureCheckboxCaptchaLoaded() {
      if (this.checkboxCaptchaComponent) {
        return Promise.resolve(this.checkboxCaptchaComponent)
      }

      if (!this.checkboxCaptchaLoadingPromise) {
        this.checkboxCaptchaLoadingPromise = import('./CheckboxCaptcha.vue')
          .then((module) => {
            this.checkboxCaptchaComponent = module.default || module
            return this.checkboxCaptchaComponent
          })
          .finally(() => {
            this.checkboxCaptchaLoadingPromise = null
          })
      }

      return this.checkboxCaptchaLoadingPromise
    },

    ensureSlideCaptchaLoaded() {
      if (this.slideCaptchaComponent) {
        return Promise.resolve(this.slideCaptchaComponent)
      }

      if (!this.slideCaptchaLoadingPromise) {
        this.slideCaptchaLoadingPromise = import('./SlideCaptcha.vue')
          .then((module) => {
            this.slideCaptchaComponent = module.default || module
            return this.slideCaptchaComponent
          })
          .finally(() => {
            this.slideCaptchaLoadingPromise = null
          })
      }

      return this.slideCaptchaLoadingPromise
    },

    /**
     * 加载验证码配置
     */
    loadCaptchaConfig() {
      if (this.loadingConfigPromise) {
        return this.loadingConfigPromise
      }

      // 添加加密标识，让后端知道需要加密响应
      this.loadingConfigPromise = this.$http
        .get(
          this.$constant.baseURL + '/captcha/getConfig',
          { encrypted: true },
          false
        )
        .then((res) => {
          this.captchaConfig = res.data || this.createDefaultCaptchaConfig()
        })
        .catch((error) => {
          console.error('加载验证码配置失败:', error)
          this.captchaConfig = this.createDefaultCaptchaConfig()
        })
        .finally(() => {
          this.loadingConfigPromise = null
        })

      return this.loadingConfigPromise
    },

    /**
     * 复选框验证成功回调
     */
    onCheckboxSuccess(token) {
      this.verified = true
      $emit(this, 'success', token)
    },

    /**
     * 复选框验证失败回调
     */
    onCheckboxFail() {
      this.verified = false
      $emit(this, 'fail')
    },

    /**
     * 复选框刷新回调
     */
    onCheckboxRefresh() {
      this.verified = false
      $emit(this, 'refresh')
    },

    /**
     * 滑动验证成功回调
     */
    onSlideSuccess(times) {
      this.verified = true
      $emit(this, 'success', times)
    },

    /**
     * 滑动验证失败回调
     */
    onSlideFail() {
      this.verified = false
      $emit(this, 'fail')
    },

    /**
     * 滑动验证刷新回调
     */
    onSlideRefresh() {
      this.verified = false
      $emit(this, 'refresh')
    },

    /**
     * 重置验证码
     */
    reset() {
      if (this.shouldUseCheckboxCaptcha && this.$refs.checkboxCaptcha) {
        this.$refs.checkboxCaptcha.reset()
      } else if (!this.shouldUseCheckboxCaptcha && this.$refs.slideCaptcha) {
        this.$refs.slideCaptcha.reset()
      }
      this.verified = false
    },

    /**
     * 外部获取验证状态
     */
    isVerified() {
      return this.verified
    },
  },
  emits: ['success', 'close', 'fail', 'refresh'],
}
</script>

<style scoped>
.captcha-wrapper {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 2100; /* 验证码遮罩层，高于 el-dialog(2000-2001)，低于消息提示(10000) */
}
.captcha-loading-card {
  position: relative;
  min-width: 260px;
  max-width: 90vw;
  padding: 28px 24px;
  border-radius: 14px;
  background: #fff;
  color: #303133;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.2);
  text-align: center;
}
.captcha-loading-close {
  position: absolute;
  top: 10px;
  right: 12px;
  border: none;
  background: transparent;
  color: #909399;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
}
.captcha-loading-text {
  font-size: 14px;
  letter-spacing: 0.5px;
}
:deep(.checkbox-captcha-wrapper),
:deep(.slide-captcha-wrapper) {
  background: transparent !important;
  box-shadow: none !important;
  padding: 0 !important;
  margin: 0 !important;
  border: none !important;
}
:deep(.slide-captcha-wrapper) {
  width: 320px;
  max-width: 95vw;
  padding: 10px;
}
:deep(.slide-verify) {
  width: 100% !important;
}
:deep(.slide-verify-slider-mask) {
  width: 100% !important;
}
@media screen and (max-width: 768px) {
  .captcha-loading-card {
    min-width: 220px;
    padding: 24px 18px;
  }
  :deep(.slide-captcha-wrapper) {
    width: 300px;
    padding: 8px;
  }
}
@media screen and (max-width: 360px) {
  :deep(.slide-captcha-wrapper) {
    width: 280px;
    padding: 5px;
  }
}
</style>
