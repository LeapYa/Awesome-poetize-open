<template>
  <div>
    <div class="page-header">
      <h3>API 接口</h3>
      <p class="page-desc">对外开放 API 的启用、密钥与 IP 白名单管理</p>
    </div>

    <!-- API 配置 -->
    <ApiSettings />
  </div>
</template>

<script>
import ApiSettings from './webEdit/ApiSettings.vue';

export default {
  name: 'WebApi',
  components: {
    ApiSettings
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
