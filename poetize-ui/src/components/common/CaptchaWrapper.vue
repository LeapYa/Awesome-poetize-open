<template>
  <div class="captcha-wrapper" v-if="visible">
    <!-- 根据实时窗口宽度选择验证码类型 -->
    <checkbox-captcha
      v-if="shouldUseCheckboxCaptcha"
      ref="checkboxCaptcha"
      :action="action"
      :track-sensitivity="checkboxOptions.trackSensitivity"
      :min-track-points="checkboxOptions.minTrackPoints"
      @success="onCheckboxSuccess"
      @fail="onCheckboxFail"
      @refresh="onCheckboxRefresh"
      @close="onClose"
    ></checkbox-captcha>
    
    <!-- 小屏幕显示滑动验证 -->
    <slide-captcha
      v-else
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
    ></slide-captcha>
  </div>
</template>

<script>
import CheckboxCaptcha from './CheckboxCaptcha.vue';
import SlideCaptcha from './SlideCaptcha.vue';

export default {
  name: 'CaptchaWrapper',
  components: {
    CheckboxCaptcha,
    SlideCaptcha
  },
  props: {
    // 显示状态
    visible: {
      type: Boolean,
      default: false
    },
    // 操作类型
    action: {
      type: String,
      default: 'login'
    },
    // 强制使用滑动验证码
    forceSlide: {
      type: Boolean,
      default: false
    },
    // 精确度（滑动验证使用）
    accuracy: {
      type: Number,
      default: 5
    },
    // 滑块文本（滑动验证使用）
    sliderText: {
      type: String,
      default: '向右滑动完成验证'
    },
    // 自定义背景图（滑动验证使用）
    imgs: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      currentWidth: window.innerWidth,  // 当前窗口宽度
      verified: false,                  // 验证状态
      isTouchDevice: false,             // 是否为触摸设备
      captchaConfig: null               // 验证码配置
    }
  },
  computed: {
    // 是否应该使用勾选验证码
    shouldUseCheckboxCaptcha() {
      // 如果强制使用滑动验证码，则返回false
      if (this.forceSlide) {
        return false;
      }
      
      // 如果是触摸设备且配置为触摸设备强制使用滑动验证码
      if (this.isTouchDevice && this.captchaConfig && this.captchaConfig.forceSlideForMobile) {
        return false;
      }
      
      // 如果有配置且有屏幕阈值，根据阈值判断
      if (this.captchaConfig && this.captchaConfig.screenSizeThreshold) {
        return this.currentWidth >= this.captchaConfig.screenSizeThreshold;
      }
      
      // 默认大屏使用勾选，小屏使用滑动
      return this.currentWidth >= 768;
    },
    
    // 勾选验证码选项
    checkboxOptions() {
      const options = {
        trackSensitivity: 0.98,
        minTrackPoints: 3
      };
      
      // 如果有配置，使用配置中的参数
      if (this.captchaConfig && this.captchaConfig.checkbox) {
        if (this.captchaConfig.checkbox.trackSensitivity) {
          options.trackSensitivity = this.captchaConfig.checkbox.trackSensitivity;
        }
        if (this.captchaConfig.checkbox.minTrackPoints) {
          options.minTrackPoints = this.captchaConfig.checkbox.minTrackPoints;
        }
      }
      
      return options;
    },
    
    // 滑动验证码选项
    slideOptions() {
      const options = {
        accuracy: this.accuracy || 5,
        successThreshold: 0.95
      };
      
      // 如果有配置，使用配置中的参数
      if (this.captchaConfig && this.captchaConfig.slide) {
        if (this.captchaConfig.slide.accuracy) {
          options.accuracy = this.captchaConfig.slide.accuracy;
        }
        if (this.captchaConfig.slide.successThreshold) {
          options.successThreshold = this.captchaConfig.slide.successThreshold;
        }
      }
      
      return options;
    }
  },
  watch: {
    // 监听visible属性变化，当显示验证码时更新窗口宽度和加载配置
    visible(newVal) {
      if (newVal) {
        this.updateWidth();
        this.loadCaptchaConfig();
      }
    }
  },
  mounted() {
    // 监听窗口大小变化
    window.addEventListener('resize', this.updateWidth);
    // 初始化时更新一次宽度
    this.updateWidth();
    // 检测是否为触摸设备
    this.detectTouchDevice();
    // 加载验证码配置
    this.loadCaptchaConfig();
  },
  beforeDestroy() {
    // 移除监听
    window.removeEventListener('resize', this.updateWidth);
  },
  methods: {
    /**
     * 关闭验证
     */
    onClose() {
      this.$emit('close');
    },
    
    /**
     * 更新窗口宽度
     */
    updateWidth() {
      const newWidth = window.innerWidth;
      const wasLargeScreen = this.shouldUseCheckboxCaptcha;
      
      // 更新宽度
      this.currentWidth = newWidth;
      
      // 如果屏幕类型发生变化（大变小或小变大），重置验证状态
      if (wasLargeScreen !== this.shouldUseCheckboxCaptcha) {
        this.reset();
      }
    },
    
    /**
     * 检测是否为触摸设备
     */
    detectTouchDevice() {
      this.isTouchDevice = (('ontouchstart' in window) || 
        (navigator.maxTouchPoints > 0) || 
        (navigator.msMaxTouchPoints > 0));
    },
    
    /**
     * 加载验证码配置
     */
    loadCaptchaConfig() {
      this.$http.get(this.$constant.pythonBaseURL + "/captcha/getConfig", {}, false)
        .then(res => {
          if (res.data) {
            this.captchaConfig = res.data;
          }
        })
        .catch(error => {
          console.error("加载验证码配置失败:", error);
          // 加载失败使用默认配置
          this.captchaConfig = {
            screenSizeThreshold: 768,
            forceSlideForMobile: true,
            slide: { accuracy: 5, successThreshold: 0.95 },
            checkbox: { trackSensitivity: 0.98, minTrackPoints: 3 }
          };
        });
    },
    
    /**
     * 复选框验证成功回调
     */
    onCheckboxSuccess(token) {
      this.verified = true;
      this.$emit('success', token);
    },
    
    /**
     * 复选框验证失败回调
     */
    onCheckboxFail() {
      this.verified = false;
      this.$emit('fail');
    },
    
    /**
     * 复选框刷新回调
     */
    onCheckboxRefresh() {
      this.verified = false;
      this.$emit('refresh');
    },
    
    /**
     * 滑动验证成功回调
     */
    onSlideSuccess(times) {
      this.verified = true;
      this.$emit('success', times);
    },
    
    /**
     * 滑动验证失败回调
     */
    onSlideFail() {
      this.verified = false;
      this.$emit('fail');
    },
    
    /**
     * 滑动验证刷新回调
     */
    onSlideRefresh() {
      this.verified = false;
      this.$emit('refresh');
    },
    
    /**
     * 重置验证码
     */
    reset() {
      if (this.shouldUseCheckboxCaptcha && this.$refs.checkboxCaptcha) {
        this.$refs.checkboxCaptcha.reset();
      } else if (!this.shouldUseCheckboxCaptcha && this.$refs.slideCaptcha) {
        this.$refs.slideCaptcha.reset();
      }
      this.verified = false;
    },
    
    /**
     * 外部获取验证状态
     */
    isVerified() {
      return this.verified;
    }
  }
}
</script>

<style scoped>
.captcha-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 9999;
}

/* 确保验证码组件在小屏幕上也能完整显示 */
:deep(.checkbox-captcha-wrapper),
:deep(.slide-captcha-wrapper) {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  width: auto;
  max-width: 95%;
  margin: 10px;
}

/* 滑动验证码特殊处理 */
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

/* 响应式调整 */
@media screen and (max-width: 768px) {
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