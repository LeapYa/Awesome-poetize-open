<template>
  <div>
    <div class="page-header">
      <h3>导航与侧边栏</h3>
      <p class="page-desc">自定义导航菜单与移动端侧边栏外观</p>
    </div>

    <!-- 导航栏配置 -->
    <NavSettings :webInfoId="webInfoId" :navConfig="navConfig" @saved="getWebInfo" />

    <!-- 移动端侧边栏（导航栏的移动端抽屉形态，与导航菜单同页管理） -->
    <MobileDrawerSettings
      :webInfoId="webInfoId"
      :mobileDrawerConfig="mobileDrawerConfig"
      :avatar="avatar"
      @saved="getWebInfo" />
  </div>
</template>

<script>
import NavSettings from './webEdit/NavSettings.vue';
import MobileDrawerSettings from './webEdit/MobileDrawerSettings.vue';

export default {
  name: 'WebNavApi',
  components: {
    NavSettings,
    MobileDrawerSettings
  },
  data() {
    return {
      webInfoId: null,
      navConfig: '[]',
      mobileDrawerConfig: '',
      avatar: ''
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
          this.mobileDrawerConfig = res.data.mobileDrawerConfig || '';
          this.avatar = res.data.avatar || '';
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
