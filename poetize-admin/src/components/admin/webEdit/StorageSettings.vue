<template>
  <div id="field-store-platform">
    <SectionTag>存储平台</SectionTag>
    <el-form label-width="140px" class="storage-form">
      <el-form-item id="field-store-type" label="默认存储平台">
        <el-radio-group v-model="values['store.type']">
          <el-radio label="local">本地存储</el-radio>
          <el-radio label="qiniu">七牛云</el-radio>
          <el-radio label="lsky">兰空图床</el-radio>
          <el-radio label="easyimage">简单图床</el-radio>
        </el-radio-group>
        <span class="tip">上传图片/附件时使用的存储平台，选中哪个就配置哪个；保存时会自动启用所选平台。切换到此前未启用过的平台后，需重启后端服务生效。</span>
      </el-form-item>

      <!-- 本地存储 -->
      <div v-if="values['store.type'] === 'local'" id="field-store-local" class="platform-block">
        <div class="platform-header">
          <span class="platform-title">本地存储配置</span>
        </div>
        <div class="platform-body">
          <el-form-item label="上传根目录" required>
            <el-input v-model="values['local.uploadUrl']" placeholder="如：/app/static/"></el-input>
            <span class="tip">文件在服务器上的存放根目录；Docker 部署时对应容器内挂载路径，修改前请确认目录存在且可写。资源访问地址由系统统一以 /media/ 形式提供，无需额外配置。</span>
          </el-form-item>
        </div>
      </div>

      <!-- 七牛云 -->
      <div v-if="values['store.type'] === 'qiniu'" id="field-store-qiniu" class="platform-block">
        <div class="platform-header">
          <span class="platform-title">七牛云配置</span>
        </div>
        <div class="platform-body">
          <el-form-item label="AccessKey" required>
            <el-input v-model="values['qiniu.accessKey']" show-password placeholder="七牛云密钥管理中的 AK"></el-input>
          </el-form-item>
          <el-form-item label="SecretKey" required>
            <el-input v-model="values['qiniu.secretKey']" show-password placeholder="七牛云密钥管理中的 SK"></el-input>
          </el-form-item>
          <el-form-item label="Bucket" required>
            <el-input v-model="values['qiniu.bucket']" placeholder="存储空间名称"></el-input>
          </el-form-item>
          <el-form-item label="访问域名" required>
            <el-input v-model="values['qiniu.downloadUrl']" placeholder="如：https://file.example.com/（末尾带 /）"></el-input>
            <span class="tip">绑定在该 Bucket 上的 CDN 加速域名或源站域名。</span>
          </el-form-item>
          <el-form-item label="上传地址" required>
            <el-input v-model="values['qiniuUrl']" placeholder="如：https://upload.qiniup.com"></el-input>
            <span class="tip">按 Bucket 所在区域选择七牛云上传入口，一般无需修改。</span>
          </el-form-item>
        </div>
      </div>

      <!-- 兰空图床 -->
      <div v-if="values['store.type'] === 'lsky'" id="field-store-lsky" class="platform-block">
        <div class="platform-header">
          <span class="platform-title">兰空图床配置</span>
        </div>
        <div class="platform-body">
          <el-form-item label="API地址" required>
            <el-input v-model="values['lsky.url']" placeholder="如：http://your-lsky-instance.com/api/v1"></el-input>
          </el-form-item>
          <el-form-item label="Token" required>
            <el-input v-model="values['lsky.token']" show-password placeholder="兰空图床的接口 Token"></el-input>
          </el-form-item>
          <el-form-item label="存储策略ID">
            <el-input v-model="values['lsky.strategy_id']" placeholder="可选，留空使用默认策略"></el-input>
          </el-form-item>
          <el-form-item label="可信下载域名">
            <el-input v-model="values['lsky.download_hosts']" placeholder="多个用逗号分隔，可留空"></el-input>
          </el-form-item>
        </div>
      </div>

      <!-- 简单图床 -->
      <div v-if="values['store.type'] === 'easyimage'" id="field-store-easyimage" class="platform-block">
        <div class="platform-header">
          <span class="platform-title">简单图床配置</span>
        </div>
        <div class="platform-body">
          <el-form-item label="API地址" required>
            <el-input v-model="values['easyimage.url']" placeholder="如：https://your-easyimage-instance.com/api/upload"></el-input>
          </el-form-item>
          <el-form-item label="Token" required>
            <el-input v-model="values['easyimage.token']" show-password placeholder="简单图床的接口 Token"></el-input>
          </el-form-item>
          <el-form-item label="可信下载域名">
            <el-input v-model="values['easyimage.download_hosts']" placeholder="多个用逗号分隔，可留空"></el-input>
          </el-form-item>
        </div>
      </div>

      <el-form-item id="field-migration-private" label="资源迁移私网访问">
        <div style="display: flex; align-items: center;">
          <el-switch v-model="values['resource.migration.remote.allow-private-hosts']"
                     active-value="true" inactive-value="false"></el-switch>
          <span :style="{
              marginLeft: '10px',
              fontSize: '12px',
              color: values['resource.migration.remote.allow-private-hosts'] === 'true' ? '#67c23a' : '#f56c6c'
            }">
            {{ values['resource.migration.remote.allow-private-hosts'] === 'true' ? '已允许' : '已禁止' }}
          </span>
        </div>
        <span class="tip">资源迁移时是否允许访问内网/私网地址的图床；仅当图床部署在内网时开启，公网环境建议保持禁止以防 SSRF。</span>
      </el-form-item>
    </el-form>

    <div class="myCenter" style="margin-bottom: 22px">
      <el-button type="primary" @click="save()" :loading="saving">保存存储配置</el-button>
    </div>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';
import { useMainStore } from '@/stores/main';

// 与 sys_config 种子数据保持一致的默认元信息（行缺失时按此新建）
const KEY_DEFS = [
  { key: 'store.type', name: '默认存储平台（local:本地，qiniu:七牛云，lsky:兰空图床，easyimage:简单图床）', type: '2', def: 'local' },
  { key: 'local.enable', name: '本地存储启用状态', type: '2', def: 'true' },
  { key: 'local.uploadUrl', name: '本地存储上传文件根目录', type: '1', def: '/app/static/' },
  { key: 'qiniu.enable', name: '七牛云存储启用状态', type: '2', def: 'false' },
  { key: 'qiniu.accessKey', name: '七牛云-accessKey', type: '1', def: '' },
  { key: 'qiniu.secretKey', name: '七牛云-secretKey', type: '1', def: '' },
  { key: 'qiniu.bucket', name: '七牛云-bucket', type: '1', def: '' },
  { key: 'qiniu.downloadUrl', name: '七牛云-域名', type: '2', def: '' },
  { key: 'qiniuUrl', name: '七牛云上传地址', type: '2', def: 'https://upload.qiniup.com' },
  { key: 'lsky.enable', name: '兰空图床存储启用状态', type: '1', def: 'false' },
  { key: 'lsky.url', name: '兰空图床-API地址', type: '1', def: '' },
  { key: 'lsky.token', name: '兰空图床-Token', type: '1', def: '' },
  { key: 'lsky.strategy_id', name: '兰空图床-存储策略ID', type: '1', def: '' },
  { key: 'lsky.download_hosts', name: '兰空图床-可信下载域名（多个用逗号分隔）', type: '1', def: '' },
  { key: 'easyimage.enable', name: '简单图床启用状态', type: '1', def: 'false' },
  { key: 'easyimage.url', name: '简单图床-API地址', type: '1', def: '' },
  { key: 'easyimage.token', name: '简单图床-Token', type: '1', def: '' },
  { key: 'easyimage.download_hosts', name: '简单图床-可信下载域名（多个用逗号分隔）', type: '1', def: '' },
  { key: 'resource.migration.remote.allow-private-hosts', name: '资源迁移是否允许访问私网图床', type: '1', def: 'false' }
];

// 平台 -> 启用开关 key 的映射（保存时自动启用所选平台）
const PLATFORM_ENABLE_KEYS = {
  local: 'local.enable',
  qiniu: 'qiniu.enable',
  lsky: 'lsky.enable',
  easyimage: 'easyimage.enable'
};

const PLATFORM_LABELS = {
  local: '本地存储',
  qiniu: '七牛云',
  lsky: '兰空图床',
  easyimage: '简单图床'
};

// 各平台的必填项（保存前校验，缺失时明确提示）
const PLATFORM_REQUIRED_FIELDS = {
  local: [
    { key: 'local.uploadUrl', label: '上传根目录' }
  ],
  qiniu: [
    { key: 'qiniu.accessKey', label: 'AccessKey' },
    { key: 'qiniu.secretKey', label: 'SecretKey' },
    { key: 'qiniu.bucket', label: 'Bucket' },
    { key: 'qiniu.downloadUrl', label: '访问域名' },
    { key: 'qiniuUrl', label: '上传地址' }
  ],
  lsky: [
    { key: 'lsky.url', label: 'API地址' },
    { key: 'lsky.token', label: 'Token' }
  ],
  easyimage: [
    { key: 'easyimage.url', label: 'API地址' },
    { key: 'easyimage.token', label: 'Token' }
  ]
};

// URL 类字段（需以 http(s):// 开头，可拦截种子数据里的占位说明文字）
const PLATFORM_URL_FIELDS = {
  qiniu: [
    { key: 'qiniu.downloadUrl', label: '访问域名' },
    { key: 'qiniuUrl', label: '上传地址' }
  ],
  lsky: [
    { key: 'lsky.url', label: 'API地址' }
  ],
  easyimage: [
    { key: 'easyimage.url', label: 'API地址' }
  ]
};

export default {
  name: 'StorageSettings',
  components: { SectionTag },
  data() {
    const values = {};
    KEY_DEFS.forEach(d => { values[d.key] = d.def; });
    return {
      values,
      // configKey -> { id, configName, configType }，来自已存在的 sys_config 行
      meta: {},
      // 加载时的快照，保存时只提交有变化的 key
      original: {},
      saving: false
    };
  },
  computed: {
    mainStore() {
      return useMainStore();
    }
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
        this.$message({ message: '加载存储配置失败：' + (e.message || e), type: 'error' });
      }
    },
    save() {
      const storeType = this.values['store.type'];

      // 所选平台的必填项校验，缺失时明确提示缺什么
      const requiredFields = PLATFORM_REQUIRED_FIELDS[storeType] || [];
      const missingLabels = requiredFields
        .filter(f => !this.values[f.key] || !String(this.values[f.key]).trim())
        .map(f => f.label);
      if (missingLabels.length > 0) {
        this.$message({
          message: `请先填写${PLATFORM_LABELS[storeType] || '所选平台'}的：${missingLabels.join('、')}`,
          type: 'error'
        });
        return;
      }

      // URL 格式校验（拦截未替换的占位说明文字等非法值）
      const urlFields = PLATFORM_URL_FIELDS[storeType] || [];
      const invalidLabels = urlFields
        .filter(f => !/^https?:\/\/.+/.test(String(this.values[f.key] || '').trim()))
        .map(f => f.label);
      if (invalidLabels.length > 0) {
        this.$message({
          message: `${PLATFORM_LABELS[storeType]}的【${invalidLabels.join('、')}】需为 http:// 或 https:// 开头的完整地址`,
          type: 'error'
        });
        return;
      }

      // 自动启用所选平台；其他平台的 enable 保持原值，
      // 避免旧平台上已有文件的删除/迁移能力被意外关闭
      const enableKey = PLATFORM_ENABLE_KEYS[storeType];
      if (enableKey) {
        this.values[enableKey] = 'true';
      }

      const changedKeys = KEY_DEFS.map(d => d.key).filter(key => this.values[key] !== this.original[key]);
      if (changedKeys.length === 0) {
        this.$message({ message: '配置没有变化', type: 'info' });
        return;
      }
      this.$confirm('确认保存存储配置？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success',
        center: true
      }).then(() => {
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
            // 刷新 sysConfig store，让上传组件等立即使用新平台
            try {
              const sysConfRes = await this.$http.get(this.$constant.baseURL + '/sysConfig/listSysConfig');
              if (sysConfRes && sysConfRes.data) {
                this.mainStore.loadSysConfig(sysConfRes.data);
              }
            } catch (_) {}
            this.$message({ message: '保存成功！', type: 'success' });
          })
          .catch((error) => {
            this.$message({ message: error.message || '部分保存失败，请检查', type: 'error' });
          })
          .finally(() => {
            this.saving = false;
          });
      }).catch(() => {
        this.$message({ type: 'success', message: '已取消保存!' });
      });
    }
  }
};
</script>

<style scoped>
.platform-block {
  border: 1px solid #c0c4cc;
  border-radius: 8px;
  margin-bottom: 16px;
  overflow: hidden;
}
.platform-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f0f2f5;
  border-bottom: 1px solid #c0c4cc;
}
.platform-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.platform-body {
  padding: 16px 16px 0;
}

/* 暗色模式适配（dark-mode 类挂在 body 上） */
body.dark-mode .platform-block {
  border-color: rgba(255, 255, 255, 0.12);
}
body.dark-mode .platform-header {
  background: #2d2d2d;
  border-bottom-color: rgba(255, 255, 255, 0.12);
}
body.dark-mode .platform-title {
  color: #e0e0e0;
}
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

  ::v-deep .el-input__inner {
    font-size: 16px !important;
    height: 44px !important;
    border-radius: 8px !important;
  }

  ::v-deep .el-button {
    min-height: 40px !important;
    border-radius: 8px !important;
  }
}
</style>
