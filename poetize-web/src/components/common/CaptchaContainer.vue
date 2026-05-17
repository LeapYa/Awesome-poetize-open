<template>
  <div class="global-captcha-container" v-if="visible">
    <component
      :is="captchaWrapperComponent"
      v-if="captchaWrapperComponent"
      :visible="visible"
      :action="captchaAction"
      :is-reply-comment="isReplyComment"
      @success="onCaptchaSuccess"
      @fail="onCaptchaFail"
      @close="onCaptchaClose"
    ></component>
  </div>
</template>

<script>
import { useMainStore } from '@/stores/main'

export default {
  name: 'CaptchaContainer',
  data() {
    return {
      captchaWrapperComponent: null,
      captchaWrapperLoadingPromise: null,
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    visible() {
      return this.mainStore.captcha.show
    },
    captchaAction() {
      return this.mainStore.captcha.action
    },
    captchaParams() {
      return this.mainStore.captcha.params
    },
    isReplyComment() {
      return this.mainStore.captcha.isReplyComment
    },
  },
  watch: {
    visible(newVal) {
      if (newVal) {
        this.ensureCaptchaWrapperLoaded()
      }
    },
  },
  mounted() {
    if (this.visible) {
      this.ensureCaptchaWrapperLoaded()
    }
  },
  methods: {
    ensureCaptchaWrapperLoaded() {
      if (this.captchaWrapperComponent) {
        return Promise.resolve(this.captchaWrapperComponent)
      }

      if (!this.captchaWrapperLoadingPromise) {
        this.captchaWrapperLoadingPromise = import('./CaptchaWrapper.vue')
          .then((module) => {
            this.captchaWrapperComponent = module.default || module
            return this.captchaWrapperComponent
          })
          .finally(() => {
            this.captchaWrapperLoadingPromise = null
          })
      }

      return this.captchaWrapperLoadingPromise
    },
    // 验证码成功回调
    onCaptchaSuccess(token) {
      this.mainStore.executeCaptchaCallback(token)
    },

    // 验证码失败回调
    onCaptchaFail() {},

    // 关闭验证码
    onCaptchaClose() {
      this.mainStore.showCaptcha(false)
    },
  },
}
</script>

<style scoped>
.global-captcha-container {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 3000;
}
</style>
