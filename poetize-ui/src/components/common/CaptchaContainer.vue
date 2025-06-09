<template>
  <div class="global-captcha-container" v-if="visible">
    <captcha-wrapper
      :visible="visible"
      :action="captchaAction"
      @success="onCaptchaSuccess"
      @fail="onCaptchaFail"
      @close="onCaptchaClose"
    ></captcha-wrapper>
  </div>
</template>

<script>
import CaptchaWrapper from './CaptchaWrapper.vue';
import { mapState, mapMutations } from 'vuex';

export default {
  name: 'CaptchaContainer',
  components: {
    CaptchaWrapper
  },
  computed: {
    ...mapState({
      visible: state => state.captcha.show,
      captchaAction: state => state.captcha.action,
      captchaParams: state => state.captcha.params
    })
  },
  methods: {
    ...mapMutations([
      'showCaptcha',
      'executeCaptchaCallback'
    ]),
    
    // 验证码成功回调
    onCaptchaSuccess(token) {
      console.log("验证码验证成功，令牌:", token);
      this.executeCaptchaCallback(token);
    },
    
    // 验证码失败回调
    onCaptchaFail() {
      console.log("验证码验证失败");
    },
    
    // 关闭验证码
    onCaptchaClose() {
      this.showCaptcha(false);
    }
  }
}
</script>

<style scoped>
.global-captcha-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 3000;
}
</style> 