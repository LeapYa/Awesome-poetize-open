<template>
  <div>
    <div class="page-header">
      <h3>通知与邮件</h3>
      <p class="page-desc">管理站点公告和邮件发送服务</p>
    </div>

    <!-- 公告 -->
    <NoticeSettings :notices="notices" :webInfoId="webInfoId" @saved="getWebInfo" />

    <!-- 邮箱配置 -->
    <EmailSettings />

    <!-- 邮件模板（验证码/订阅邮件正文） -->
    <MailTemplateSettings />
  </div>
</template>

<script>
import NoticeSettings from './webEdit/NoticeSettings.vue';
import EmailSettings from './webEdit/EmailSettings.vue';
import MailTemplateSettings from './webEdit/MailTemplateSettings.vue';

export default {
  name: 'WebNotice',
  components: {
    NoticeSettings,
    EmailSettings,
    MailTemplateSettings
  },
  data() {
    return {
      webInfoId: null,
      notices: []
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
          this.notices = JSON.parse(res.data.notices || '[]');
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
