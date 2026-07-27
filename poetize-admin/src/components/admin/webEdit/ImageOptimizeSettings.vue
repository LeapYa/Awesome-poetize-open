<template>
  <div id="field-image-optimize">
    <SectionTag>图片优化</SectionTag>
    <el-form label-width="140px">
      <el-form-item label="WebP转换">
        <div style="display: flex; align-items: center;">
          <el-switch v-model="values['image.webp.enabled']" active-value="true" inactive-value="false"></el-switch>
          <span :style="{
              marginLeft: '10px',
              fontSize: '12px',
              color: values['image.webp.enabled'] === 'true' ? '#67c23a' : '#f56c6c'
            }">
            {{ values['image.webp.enabled'] === 'true' ? '已开启' : '已关闭' }}
          </span>
        </div>
        <span class="tip">上传图片时自动转换为体积更小的 WebP 格式，节省存储与带宽。</span>
      </el-form-item>

      <template v-if="values['image.webp.enabled'] === 'true'">
        <el-form-item label="最小文件大小(KB)">
          <el-input-number v-model="webpMinSize" :min="0" :max="10240" :step="10" :controls="false"></el-input-number>
          <span class="tip">小于该大小的图片不做 WebP 转换（转换收益低）。</span>
        </el-form-item>
        <el-form-item label="最小节省比例(%)">
          <el-input-number v-model="webpMinSavingRatio" :min="0" :max="100" :step="5" :controls="false"></el-input-number>
          <span class="tip">转换后体积节省低于该比例时保留原图。</span>
        </el-form-item>
      </template>

      <el-form-item label="图片压缩">
        <div style="display: flex; align-items: center;">
          <el-switch v-model="values['image.compress.enabled']" active-value="true" inactive-value="false"></el-switch>
          <span :style="{
              marginLeft: '10px',
              fontSize: '12px',
              color: values['image.compress.enabled'] === 'true' ? '#67c23a' : '#f56c6c'
            }">
            {{ values['image.compress.enabled'] === 'true' ? '已开启' : '已关闭' }}
          </span>
        </div>
        <span class="tip">上传图片时进行压缩，可根据画质需求与服务器性能选择模式。</span>
      </el-form-item>

      <el-form-item v-if="values['image.compress.enabled'] === 'true'" label="压缩模式">
        <el-select v-model="values['image.compress.mode']" placeholder="选择压缩模式">
          <el-option label="有损压缩（体积更小）" value="lossy"></el-option>
          <el-option label="无损压缩（画质无损）" value="lossless"></el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <div class="myCenter" style="margin-bottom: 22px">
      <el-button type="primary" @click="save()" :loading="saving">保存图片优化配置</el-button>
    </div>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';
import { useMainStore } from '@/stores/main';

// 与 sys_config 种子数据保持一致的默认元信息（行缺失时按此新建）
const KEY_DEFS = [
  { key: 'image.webp.enabled', name: 'WebP图片转换启用状态', type: '2', def: 'true' },
  { key: 'image.webp.min-size', name: 'WebP转换最小文件大小(KB)', type: '2', def: '50' },
  { key: 'image.webp.min-saving-ratio', name: 'WebP转换最小节省比例(%)', type: '2', def: '10' },
  { key: 'image.compress.enabled', name: '图片压缩启用状态', type: '2', def: 'true' },
  { key: 'image.compress.mode', name: '图片压缩模式(lossy:有损,lossless:无损)', type: '2', def: 'lossy' }
];

export default {
  name: 'ImageOptimizeSettings',
  components: { SectionTag },
  data() {
    const values = {};
    KEY_DEFS.forEach(d => { values[d.key] = d.def; });
    return {
      values,
      // 数值型字段用 el-input-number 编辑，保存时转回字符串
      webpMinSize: 50,
      webpMinSavingRatio: 10,
      meta: {},
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
        this.webpMinSize = parseInt(this.values['image.webp.min-size'], 10) || 0;
        this.webpMinSavingRatio = parseInt(this.values['image.webp.min-saving-ratio'], 10) || 0;
        this.original = { ...this.values };
      } catch (e) {
        this.$message({ message: '加载图片优化配置失败：' + (e.message || e), type: 'error' });
      }
    },
    save() {
      // 数值编辑框同步回字符串值
      this.values['image.webp.min-size'] = String(this.webpMinSize == null ? 0 : this.webpMinSize);
      this.values['image.webp.min-saving-ratio'] = String(this.webpMinSavingRatio == null ? 0 : this.webpMinSavingRatio);

      const changedKeys = KEY_DEFS.map(d => d.key).filter(key => this.values[key] !== this.original[key]);
      if (changedKeys.length === 0) {
        this.$message({ message: '配置没有变化', type: 'info' });
        return;
      }
      this.$confirm('确认保存图片优化配置？', '提示', {
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
