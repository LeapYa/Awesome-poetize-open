<template>
  <div id="field-mail-templates">
    <SectionTag>邮件模板</SectionTag>
    <el-form label-width="140px">
      <el-form-item label="验证码邮件模板">
        <el-input type="textarea" :rows="3"
                  v-model="values['user.code.format']"
                  placeholder="如：【POETIZE】%s为本次验证的验证码，请在5分钟内完成验证。"></el-input>
        <span class="tip">邮箱验证码邮件的正文内容，须包含一个 %s 占位符（验证码），删除占位符会导致用户收不到验证码。</span>
      </el-form-item>

      <el-form-item label="订阅邮件模板">
        <el-input type="textarea" :rows="3"
                  v-model="values['user.subscribe.format']"
                  placeholder="如：【POETIZE】您订阅的专栏【%s】新增一篇文章：%s。"></el-input>
        <span class="tip">专栏订阅通知邮件的正文内容，须依次包含两个 %s 占位符（专栏名称、文章标题）。</span>
      </el-form-item>
    </el-form>

    <div class="myCenter" style="margin-bottom: 22px">
      <el-button type="primary" @click="save()" :loading="saving">保存邮件模板</el-button>
    </div>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';

// 与 sys_config 种子数据保持一致的默认元信息（行缺失时按此新建）
const KEY_DEFS = [
  { key: 'user.code.format', name: '邮箱验证码模板', type: '1', def: '【POETIZE】%s为本次验证的验证码，请在5分钟内完成验证。为保证账号安全，请勿泄漏此验证码。' },
  { key: 'user.subscribe.format', name: '邮箱订阅模板', type: '1', def: '【POETIZE】您订阅的专栏【%s】新增一篇文章：%s。' }
];

export default {
  name: 'MailTemplateSettings',
  components: { SectionTag },
  data() {
    const values = {};
    KEY_DEFS.forEach(d => { values[d.key] = d.def; });
    return {
      values,
      meta: {},
      original: {},
      saving: false
    };
  },
  created() {
    this.loadConfigs();
  },
  methods: {
    async loadConfigs() {
      try {
        const res = await this.$http.get(this.$constant.baseURL + '/sysConfig/listConfig', {}, true);
        const list = res && res.data ? (Array.isArray(res.data) ? res.data : (res.data.data || [])) : [];
        KEY_DEFS.forEach(d => {
          const item = list.find(c => c.configKey === d.key);
          if (item) {
            this.meta[d.key] = { id: item.id, configName: item.configName || d.name, configType: item.configType || d.type };
            this.values[d.key] = item.configValue != null ? item.configValue : d.def;
          } else {
            this.meta[d.key] = { id: null, configName: d.name, configType: d.type };
          }
        });
        this.original = { ...this.values };
      } catch (e) {
        this.$message({ message: '加载邮件模板失败：' + (e.message || e), type: 'error' });
      }
    },
    save() {
      // 校验占位符，避免模板失效
      if (this.values['user.code.format'] && !this.values['user.code.format'].includes('%s')) {
        this.$message({ message: '验证码模板必须包含 %s 占位符', type: 'error' });
        return;
      }
      const subscribeFormat = this.values['user.subscribe.format'] || '';
      if (subscribeFormat && (subscribeFormat.split('%s').length - 1) < 2) {
        this.$message({ message: '订阅模板必须包含两个 %s 占位符', type: 'error' });
        return;
      }

      const changedKeys = KEY_DEFS.map(d => d.key).filter(key => this.values[key] !== this.original[key]);
      if (changedKeys.length === 0) {
        this.$message({ message: '配置没有变化', type: 'info' });
        return;
      }
      this.saving = true;
      const promises = changedKeys.map(key => {
        const m = this.meta[key];
        return this.$http.post(this.$constant.baseURL + '/sysConfig/saveOrUpdateConfig', {
          id: m.id,
          configName: m.configName,
          configKey: key,
          configValue: this.values[key],
          configType: m.configType
        }, true);
      });
      Promise.all(promises)
        .then(async () => {
          await this.loadConfigs();
          this.$message({ message: '保存成功！', type: 'success' });
        })
        .catch((error) => {
          this.$message({ message: error.message || '部分保存失败，请检查', type: 'error' });
        })
        .finally(() => {
          this.saving = false;
        });
    }
  }
};
</script>

<style scoped>
.tip {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #999;
  line-height: 1.5;
}

/* PC端样式 - 768px以上 */
@media screen and (min-width: 769px) {
  ::v-deep .el-form-item__label {
    float: left !important;
  }
}

/* 移动端样式 - 768px及以下 */
@media screen and (max-width: 768px) {
  ::v-deep .el-form-item__label {
    float: none !important;
    width: 100% !important;
    text-align: left !important;
    margin-bottom: 8px !important;
    font-weight: 500 !important;
    font-size: 14px !important;
    padding-bottom: 0 !important;
    line-height: 1.5 !important;
  }

  ::v-deep .el-form-item__content {
    margin-left: 0 !important;
    width: 100% !important;
  }

  ::v-deep .el-form-item {
    margin-bottom: 20px !important;
  }

  ::v-deep .el-textarea__inner {
    font-size: 16px !important;
    border-radius: 8px !important;
  }

  ::v-deep .el-button {
    min-height: 40px !important;
    border-radius: 8px !important;
  }
}
</style>
