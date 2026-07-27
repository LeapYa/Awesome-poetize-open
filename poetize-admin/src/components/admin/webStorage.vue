<template>
  <div>
    <div class="page-header">
      <h3>存储与图床</h3>
      <p class="page-desc">配置文件上传的存储平台与图片自动优化策略</p>
    </div>

    <!-- 存储平台配置 -->
    <StorageSettings />

    <!-- 图片优化配置 -->
    <ImageOptimizeSettings />
  </div>
</template>

<script>
import StorageSettings from './webEdit/StorageSettings.vue';
import ImageOptimizeSettings from './webEdit/ImageOptimizeSettings.vue';

export default {
  name: 'WebStorage',
  components: {
    StorageSettings,
    ImageOptimizeSettings
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
