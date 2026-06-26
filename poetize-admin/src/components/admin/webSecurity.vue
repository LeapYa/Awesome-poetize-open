<template>
  <div>
    <div class="page-header">
      <h3>安全与登录</h3>
      <p class="page-desc">配置验证码防护和第三方登录</p>
    </div>

    <!-- 智能验证码配置 -->
    <CaptchaSettings />

    <!-- 第三方登录配置 -->
    <ThirdLoginSettings />
  </div>
</template>

<script>
import CaptchaSettings from './webEdit/CaptchaSettings.vue';
import ThirdLoginSettings from './webEdit/ThirdLoginSettings.vue';

export default {
  name: 'WebSecurity',
  components: {
    CaptchaSettings,
    ThirdLoginSettings
  }
};
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.page-header h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
}
.page-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
</style>
