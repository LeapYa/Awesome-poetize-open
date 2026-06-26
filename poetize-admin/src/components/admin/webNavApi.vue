<template>
  <div>
    <div class="page-header">
      <h3>导航与接口</h3>
      <p class="page-desc">自定义导航菜单和管理外部 API 接口</p>
    </div>

    <!-- 导航栏配置 -->
    <NavSettings :webInfoId="webInfoId" :navConfig="navConfig" @saved="getWebInfo" />

    <!-- API 配置 -->
    <ApiSettings />
  </div>
</template>

<script>
import NavSettings from './webEdit/NavSettings.vue';
import ApiSettings from './webEdit/ApiSettings.vue';

export default {
  name: 'WebNavApi',
  components: {
    NavSettings,
    ApiSettings
  },
  data() {
    return {
      webInfoId: null,
      navConfig: '[]'
    };
  },
  created() {
    this.getWebInfo();
  },
  methods: {
    async getWebInfo() {
      try {
        const res = await this.$http.get(this.$constant.baseURL + "/admin/webInfo/getAdminWebInfoDetails", {}, true);
        if (!this.$common.isEmpty(res.data)) {
          this.webInfoId = res.data.id;
          this.navConfig = res.data.navConfig || '[]';
        }
      } catch (error) {
        this.$message({ message: error.message, type: "error" });
      }
    }
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
