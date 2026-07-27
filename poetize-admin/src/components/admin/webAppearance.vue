<template>
  <div>
    <div class="page-header">
      <h3>外观与个性化</h3>
      <p class="page-desc">鼠标点击效果、首页横幅、夜间/灰色模式、动态标题、字体与随机配置等全局外观</p>
    </div>

    <!-- 全局外观开关 -->
    <div>
      <el-tag effect="dark" class="my-tag">
        <i class="el-icon-brush" style="font-size:16px;vertical-align:-2px;margin-right:4px;"></i>
        外观开关
      </el-tag>

      <el-form :model="webInfo" label-width="100px" class="demo-ruleForm">

        <!-- 鼠标点击效果 -->
        <el-form-item id="field-mouse-click-effect" label="鼠标点击效果">
          <el-select
            v-model="webInfo.mouseClickEffect"
            @change="handleMouseClickEffectChange"
            placeholder="请选择点击效果"
            :loading="mouseClickEffectLoading"
            style="width: 100%; max-width: 420px"
            popper-class="appearance-select-dropdown">
            <el-option
              v-for="effect in mouseClickEffectOptions"
              :key="effect.pluginKey"
              :label="effect.pluginName"
              :value="effect.pluginKey">
              <span>{{ effect.pluginName }}</span>
              <span v-if="effect.pluginDescription" style="color: #909399; font-size: 12px; margin-left: 8px;">（{{ effect.pluginDescription }}）</span>
            </el-option>
          </el-select>
          <router-link to="/pluginManager" style="margin-left: 10px; font-size: 12px;">管理插件</router-link>
        </el-form-item>

        <!-- 首页横幅高度 -->
        <el-form-item id="field-banner-height" label="首页横幅高度">
          <el-input-number v-model="webInfo.homePagePullUpHeight" :min="10" :max="100" style="width: 120px;"></el-input-number>
          <span style="margin-left: 8px; color: #909399;">vh</span>
        </el-form-item>

        <!-- 自动夜间 -->
        <el-form-item id="field-auto-night" label="自动夜间">
          <el-switch v-model="webInfo.enableAutoNight"></el-switch>
          <span :style="{
                marginLeft: '10px',
                fontSize: '12px',
                color: webInfo.enableAutoNight ? '#67c23a' : '#f56c6c'
              }">
              {{ webInfo.enableAutoNight ? '已开启' : '已关闭' }}
          </span>
        </el-form-item>

        <el-form-item v-if="webInfo.enableAutoNight" label="夜间开始(小时)">
          <el-input-number v-model="webInfo.autoNightStart" :min="0" :max="23"></el-input-number>
        </el-form-item>

        <el-form-item v-if="webInfo.enableAutoNight" label="夜间结束(小时)">
          <el-input-number v-model="webInfo.autoNightEnd" :min="0" :max="23"></el-input-number>
        </el-form-item>

        <!-- 灰色模式 -->
        <el-form-item id="field-gray-mode" label="灰色模式">
          <el-switch v-model="webInfo.enableGrayMode"></el-switch>
          <span :style="{
                marginLeft: '10px',
                fontSize: '12px',
                color: webInfo.enableGrayMode ? '#67c23a' : '#f56c6c'
              }">
              {{ webInfo.enableGrayMode ? '已开启' : '已关闭' }}
          </span>
        </el-form-item>

        <!-- 动态标题 -->
        <el-form-item id="field-dynamic-title" label="动态标题">
          <el-switch v-model="webInfo.enableDynamicTitle"></el-switch>
          <span :style="{
                marginLeft: '10px',
                fontSize: '12px',
                color: webInfo.enableDynamicTitle ? '#67c23a' : '#f56c6c'
              }">
              {{ webInfo.enableDynamicTitle ? '已开启' : '已关闭' }}
          </span>
          <div style="margin-top: 8px; font-size: 12px; color: #909399; line-height: 1.5;">
            <template v-if="webInfo.enableDynamicTitle">
              <span style="color: #67c23a;">✨ 当前状态：</span>
              当您离开页面时，标题会温柔地挽留"<span style="color: #f56c6c;">w(ﾟДﾟ)w 不要走！再看看嘛！</span>"；
              当您返回时，会热情地欢迎"<span style="color: #409EFF;">♪(^∇^*)欢迎肥来！</span>"，
              2秒后自动恢复原标题～
            </template>
            <template v-else>
              <span style="color: #c0c4cc;">📄 当前状态：</span>
              页面标题始终保持不变
            </template>
          </div>
        </el-form-item>

      </el-form>

      <!-- 字体优化管理区块 -->
      <div id="field-font-optimization" class="font-opt">
        <div class="font-opt__head">
          <h4 class="font-opt__title">
            <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="color:#409EFF;flex-shrink:0;"><polyline points="4 7 4 4 20 4 20 7"></polyline><line x1="9" y1="20" x2="15" y2="20"></line><line x1="12" y1="4" x2="12" y2="20"></line></svg>
            字体管理
          </h4>
          <p class="font-opt__desc">上传字体即可更换全站字体，系统会自动切片为约 48KB 的 WOFF2 分片以按需加载。若需进一步提升加载速度，可下载切割包上传至 CDN 并在下方配置。</p>
        </div>

        <!-- 步骤条 -->
        <div class="font-opt__steps">
          <div class="font-step"><span class="font-step__num">1</span><span class="font-step__text">上传更换字体</span></div>
          <span class="font-step__sep"></span>
          <div class="font-step"><span class="font-step__num">2</span><span class="font-step__text">下载切割包(可选)</span></div>
          <span class="font-step__sep"></span>
          <div class="font-step"><span class="font-step__num">3</span><span class="font-step__text">上传至CDN(可选)</span></div>
          <span class="font-step__sep"></span>
          <div class="font-step"><span class="font-step__num">4</span><span class="font-step__text">填写地址(可选)</span></div>
        </div>

        <div class="font-opt__grid">
          <!-- 状态 -->
          <div class="font-card">
            <div class="font-card__bar">
              <span class="font-card__label">切片状态</span>
              <el-button type="text" size="mini" icon="el-icon-refresh" @click="loadFontStatus" :loading="fontStatusLoading">刷新</el-button>
            </div>

            <div v-if="fontStatusLoading" class="font-card__skeleton">
              <i class="el-icon-loading"></i><span>加载中…</span>
            </div>

            <div v-else-if="fontStatus" class="font-card__inner">
              <div class="font-pill" :class="fontStatus.ready ? 'is-ready' : 'is-idle'">
                <span class="font-pill__dot"></span>
                <span>{{ fontStatus.ready ? (fontStatus.engine === 'cn-font-split' ? '自定义字体已就绪' : '子集字体已就绪') : '使用内置默认分片' }}</span>
              </div>

              <div v-if="fontStatus.ready" class="font-stats">
                <div class="font-stat">
                  <span class="font-stat__k">切片数</span>
                  <span class="font-stat__v">{{ fontStatus.chunkCount || 4 }}<span class="font-stat__u">个</span></span>
                </div>
                <div class="font-stat">
                  <span class="font-stat__k">总体积</span>
                  <span class="font-stat__v">{{ formatSize(fontStatus.totalSize) }}</span>
                </div>
                <div class="font-stat" v-if="fontStatus.cssFileSize">
                  <span class="font-stat__k">CSS 索引</span>
                  <span class="font-stat__v">{{ formatSize(fontStatus.cssFileSize) }}</span>
                </div>
              </div>

              <div class="font-card__actions">
                <el-tooltip content="下载当前生效的字体切割包（自定义或内置默认），解压后整体上传至 CDN" placement="top">
                  <el-button type="primary" size="mini" plain icon="el-icon-download" :loading="fontDownloading" @click="downloadFontPackage">下载切割包</el-button>
                </el-tooltip>
                <el-popconfirm v-if="fontStatus.ready" title="清理后将恢复内置分片字体，确定？" @confirm="cleanFontSubsets" confirm-button-type="danger">
                  <el-button slot="reference" type="text" size="mini" :loading="fontCleaning" style="color:#f56c6c;">清理</el-button>
                </el-popconfirm>
              </div>
            </div>
          </div>

          <!-- 上传 -->
          <div class="font-card">
            <div class="font-card__bar">
              <span class="font-card__label">上传字体</span>
            </div>
            <el-upload class="font-uploader" ref="fontUploadRef" drag :action="fontUploadUrl" :headers="fontUploadHeaders" :before-upload="beforeFontUpload" :on-success="onFontUploadSuccess" :on-error="onFontUploadError" :on-progress="onFontUploadProgress" :show-file-list="false" accept=".ttf,.otf" name="file">
              <div v-if="!fontProcessing" class="font-uploader__idle">
                <svg viewBox="0 0 24 24" width="28" height="28" stroke="currentColor" stroke-width="1.5" fill="none" class="font-uploader__icon"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                <span class="font-uploader__text">拖拽 TTF/OTF 至此，或 <em>点击上传</em></span>
              </div>
              <div v-else class="font-uploader__busy" @click.stop>
                <el-progress type="circle" :percentage="fontUploadProgress" :width="72" color="#409EFF"></el-progress>
                <span class="font-uploader__busy-text">{{ fontUploadProgress !== 100 ? '上传中…' : '切片压缩中…' }}</span>
              </div>
            </el-upload>
            <transition name="el-fade-in">
              <div v-if="fontResult" class="font-result">
                <i class="el-icon-circle-check"></i>
                <span>{{ fontResult.totalChars }} 字符 · {{ fontResult.chunkCount || 0 }} 分片 · {{ (fontResult.elapsedMs / 1000).toFixed(1) }}s · 原 {{ formatSize(fontResult.originalSize) }}</span>
              </div>
            </transition>
          </div>
        </div>

        <!-- CDN 配置 -->
        <div class="font-cdn">
          <div class="font-cdn__bar">
            <span class="font-cdn__label">CDN 地址</span>
            <el-tooltip placement="top" effect="dark">
              <div slot="content" style="line-height:1.8;">
                下载切割包解压后整体上传到 CDN，<br/>在此填写基础路径（末尾以 / 结尾）。<br/>留空则从本站 /static/assets/font_chunks/ 加载。
              </div>
              <i class="el-icon-question font-cdn__help"></i>
            </el-tooltip>
          </div>
          <label class="font-cdn__field-label">字体 CDN 基础路径（font.cdn.base-url）</label>
          <div class="font-cdn__row">
            <el-input v-model="fontCdnConfig.configValue" placeholder="https://your-cdn.com/font_chunks/（末尾带 /）" clearable @blur="normalizeFontCdnUrl"></el-input>
          </div>
          <p class="font-cdn__hint">当前生效：<strong>{{ mainStore.sysConfig['font.cdn.base-url'] || '/static/assets/font_chunks/' }}</strong></p>
        </div>
      </div>

      <div class="myCenter" style="margin-top: 32px; margin-bottom: 22px">
        <el-button type="primary" @click="saveAppearanceSettings" class="primary-save-btn">保存外观与排版设置</el-button>
      </div>
    </div>

    <!-- 随机名称/头像/封面 -->
    <RandomSettings
      :randomName="randomName"
      :randomAvatar="randomAvatar"
      :randomCover="randomCover"
      :webInfoId="webInfoId"
      @saved="getWebInfo" />

  </div>
</template>

<script>
import { useMainStore } from '@/stores/main';
import RandomSettings from './webEdit/RandomSettings.vue';
import { setAdminContentLoading } from '@/utils/sessionValidation';

export default {
  name: 'WebAppearance',
  components: {
    RandomSettings
  },
  data() {
    return {
      mainStore: useMainStore(),
      loading: false,
      webInfoId: null,
      randomAvatar: [],
      randomName: [],
      randomCover: [],
      webInfo: {
        id: null,
        enableAutoNight: false,
        autoNightStart: 23,
        autoNightEnd: 7,
        enableGrayMode: false,
        enableDynamicTitle: true,
        mouseClickEffect: 'none',
        homePagePullUpHeight: 50
      },
      // 鼠标点击效果插件列表
      mouseClickEffectOptions: [],
      mouseClickEffectLoading: false,
      // 字体管理状态
      fontStatus: null,
      fontStatusLoading: false,
      fontProcessing: false,
      fontCleaning: false,
      fontDownloading: false,
      fontCdnConfig: {
        id: null,
        configName: '字体文件CDN基础路径(末尾必须有/)',
        configKey: 'font.cdn.base-url',
        configValue: '',
        configType: '2'
      },
      fontUploadProgress: 0,
      fontResult: null
    };
  },
  computed: {
    fontUploadUrl() {
      return this.$constant.baseURL + '/fontSubset/upload';
    },
    fontUploadHeaders() {
      // Cookie-based auth: no Authorization header needed, withCredentials handles it
      return {};
    }
  },
  created() {
    this.initializeData();
    this.loadFontStatus();
    this.loadFontCdnConfig();
  },
  beforeDestroy() {
    this.setContentLoading(false);
  },
  methods: {
    setContentLoading(loading) {
      if (this.loading === loading) {
        return;
      }
      this.loading = loading;
      setAdminContentLoading(loading);
    },

    async initializeData() {
      this.setContentLoading(true);
      try {
        await Promise.allSettled([
          this.getWebInfo(),
          this.loadMouseClickEffectPlugins()
        ]);
      } finally {
        this.setContentLoading(false);
      }
    },
    async getWebInfo() {
      try {
        const res = await this.$http.get(this.$constant.baseURL + "/admin/webInfo/getAdminWebInfoDetails", {}, true);
        if (!this.$common.isEmpty(res.data)) {
          this.webInfoId = res.data.id;
          this.webInfo.id = res.data.id;
          this.webInfo.enableAutoNight = res.data.enableAutoNight ?? false;
          this.webInfo.autoNightStart = res.data.autoNightStart ?? 23;
          this.webInfo.autoNightEnd = res.data.autoNightEnd ?? 7;
          this.webInfo.enableGrayMode = res.data.enableGrayMode ?? false;
          this.webInfo.enableDynamicTitle = res.data.enableDynamicTitle ?? true;
          this.webInfo.mouseClickEffect = res.data.mouseClickEffect || 'none';
          this.webInfo.homePagePullUpHeight = res.data.homePagePullUpHeight > 0 ? res.data.homePagePullUpHeight : 50;
          this.randomAvatar = JSON.parse(res.data.randomAvatar || '[]');
          this.randomName = JSON.parse(res.data.randomName || '[]');
          this.randomCover = JSON.parse(res.data.randomCover || '[]');
        }
      } catch (error) {
        this.$message({ message: error.message, type: "error" });
      }
    },

    // 保存外观设置（夜间、灰色、动态标题、鼠标特效、横幅高度）
    async persistAppearanceSettings(options = {}) {
      const { showMessage = true } = options;
      const updateData = {
        id: this.webInfo.id,
        enableAutoNight: this.webInfo.enableAutoNight,
        autoNightStart: this.webInfo.autoNightStart,
        autoNightEnd: this.webInfo.autoNightEnd,
        enableGrayMode: this.webInfo.enableGrayMode,
        enableDynamicTitle: this.webInfo.enableDynamicTitle,
        mouseClickEffect: this.webInfo.mouseClickEffect,
        homePagePullUpHeight: this.webInfo.homePagePullUpHeight
      };

      // CDN 地址协议一致性校验：在全部保存之前完成，避免 updateWebInfo 已保存但 CDN 未保存
      this.normalizeFontCdnUrl();
      const cdnUrl = (this.fontCdnConfig.configValue || '').trim();
      if (cdnUrl && cdnUrl.startsWith('http://') && window.location.protocol === 'https:') {
        try {
          await this.$confirm(
            '当前网站通过 HTTPS 访问，但 CDN 地址使用了 HTTP 协议。' +
            '浏览器会因混合内容策略阻断字体加载，导致前台回退到系统默认字体。' +
            '\n\n建议使用 HTTPS 协议的 CDN 地址，或留空从本站 /static/assets/font_chunks/ 加载。' +
            '\n\n确定仍要保存 HTTP 地址吗？',
            'CDN 协议不一致',
            { confirmButtonText: '仍要保存', cancelButtonText: '取消', type: 'warning' }
          );
        } catch (_) {
          return false;
        }
      }

      try {
        await this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", updateData, true);

        await this.$http.post(this.$constant.baseURL + '/sysConfig/saveOrUpdateConfig', this.fontCdnConfig, true);
        const sysConfRes = await this.$http.get(this.$constant.baseURL + '/sysConfig/listSysConfig');
        if (sysConfRes && sysConfRes.data) {
          this.mainStore.loadSysConfig(sysConfRes.data);
          this.$bus.$emit('sysConfigUpdated', sysConfRes.data);
        }

        this.getWebInfo();
        this.mainStore.setWebInfo({ ...this.mainStore.webInfo, ...updateData });
        if (showMessage) {
          this.$message({ message: "保存成功！", type: "success" });
        }

        return true;
      } catch (error) {
        if (showMessage) {
          this.$message({ message: error.message || '保存失败', type: 'error' });
        }
        return false;
      }
    },

    saveAppearanceSettings() {
      this.$confirm('确认保存所有外观与排版设置？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success',
        center: true
      }).then(async () => {
        await this.persistAppearanceSettings();
      }).catch(() => {
        this.$message({ type: 'info', message: '已取消保存' });
      });
    },

    handleMouseClickEffectChange(value) {
      this.webInfo.mouseClickEffect = value;
      // 同步更新插件系统的激活状态
      this.$http.post(this.$constant.baseURL + "/sysPlugin/setActivePlugin", {
        pluginType: 'mouse_click_effect',
        pluginKey: value
      }).catch(error => {
        console.error('同步插件激活状态失败:', error);
      });
    },
    async loadMouseClickEffectPlugins() {
      this.mouseClickEffectLoading = true;
      try {
        const res = await this.$http.get(this.$constant.baseURL + "/sysPlugin/getMouseClickEffects");
        if (res.code === 200 && res.data) {
          this.mouseClickEffectOptions = res.data.map(plugin => ({
            pluginKey: plugin.pluginKey,
            pluginName: plugin.pluginName,
            pluginDescription: plugin.pluginDescription
          }));
        }
        const activeRes = await this.$http.get(this.$constant.baseURL + "/sysPlugin/getActiveMouseClickEffect");
        if (activeRes.code === 200 && activeRes.data && activeRes.data.pluginKey) {
          this.webInfo.mouseClickEffect = activeRes.data.pluginKey;
        }
      } catch (error) {
        console.error('加载鼠标点击效果插件失败:', error);
        this.mouseClickEffectOptions = [
          { pluginKey: 'none', pluginName: '无效果', pluginDescription: '' },
          { pluginKey: 'love', pluginName: '爱心', pluginDescription: '点击显示爱心' },
          { pluginKey: 'star', pluginName: '星星', pluginDescription: '点击显示星星' },
          { pluginKey: 'text', pluginName: '文字', pluginDescription: '点击显示文字' }
        ];
      } finally {
        this.mouseClickEffectLoading = false;
      }
    },

    // ==========================================
    // 字体优化与子集化管理方法
    // ==========================================
    loadFontStatus() {
      this.fontStatusLoading = true;
      this.$http.get(this.$constant.baseURL + '/fontSubset/status')
        .then(res => {
          if (res.code === 200) {
            this.fontStatus = res.data;
          }
        })
        .finally(() => {
          this.fontStatusLoading = false;
        });
    },
    beforeFontUpload(file) {
      const ext = file.name.toLowerCase();
      if (!ext.endsWith('.ttf') && !ext.endsWith('.otf')) {
        this.$message.error('字体优化仅支持 .ttf 或 .otf 格式');
        return false;
      }
      if (file.size > 100 * 1024 * 1024) {
        this.$message.error('字体文件需在 100MB 以内');
        return false;
      }
      this.fontProcessing = true;
      this.fontUploadProgress = 0;
      this.fontResult = null;
      return true;
    },
    onFontUploadProgress(event) {
      if (event.percent) {
        this.fontUploadProgress = Math.min(Math.round(event.percent), 99);
      }
    },
    onFontUploadSuccess(response) {
      this.fontUploadProgress = 100;
      if (response.code === 200) {
        this.fontResult = response.data;
        this.$message.success('字体深度优化完成，前台加载性能已提升！');
        this.loadFontStatus();
        // 上传后刷新系统配置（含字体资源版本号）并重载后台预览字体，确保立即生效
        this.refreshFontConfig();
      } else {
        this.$message.error(response.message || '字体处理失败');
      }
      setTimeout(() => {
        this.fontProcessing = false;
      }, 500);
    },
    onFontUploadError() {
      this.fontProcessing = false;
      this.fontUploadProgress = 0;
      this.$message.error('上传或处理超时，请重试');
    },
    cleanFontSubsets() {
      this.fontCleaning = true;
      this.$http.delete(this.$constant.baseURL + '/fontSubset/clean')
        .then(res => {
          if (res.code === 200) {
            this.$message.success('自定义字体映射已移除，恢复系统字体');
            this.fontResult = null;
            this.loadFontStatus();
            this.refreshFontConfig();
          }
        })
        .finally(() => {
          this.fontCleaning = false;
        });
    },
    downloadFontPackage() {
      // 直接用浏览器原生导航触发下载：cookie 自动携带，浏览器下载列表立即显示进度条，
      // 避免 axios responseType:blob 把整个响应缓存在内存再触发 link.click() 导致的转圈。
      // 后端已设置 Content-Disposition: attachment，浏览器会按下载处理而非页面跳转。
      // 鉴权失败时浏览器会下载一个小的 JSON 错误文件，用户能直观感知。
      const downloadUrl = import.meta.env.DEV
        ? '/fontSubset/download'
        : (this.$constant.baseURL + '/fontSubset/download');

      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = 'font_chunks.zip';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      this.$message.success('字体切割包已开始下载');
    },
    loadFontCdnConfig() {
      this.$http.get(this.$constant.baseURL + '/sysConfig/listConfig', {}, true)
        .then(res => {
          if (res.data) {
            const found = res.data.find(c => c.configKey === 'font.cdn.base-url');
            if (found) {
              this.fontCdnConfig = { ...found };
            }
          }
        })
        .catch(() => {});
    },
    /**
     * 上传/清理字体后，重新拉取系统配置（含字体资源版本号 font.asset.version），
     * 更新到 store 后重载后台预览字体，使新字体无需硬刷新即可在前台/后台生效。
     */
    async refreshFontConfig() {
      try {
        const res = await this.$http.get(this.$constant.baseURL + '/sysConfig/listSysConfig');
        if (res && res.data) {
          this.mainStore.loadSysConfig(res.data);
          this.$bus.$emit('sysConfigUpdated', res.data);
          const { loadFonts } = await import('@/utils/font-loader');
          await loadFonts(this.mainStore.sysConfig);
        }
      } catch (e) {
        // 拉取失败不影响已完成的字体处理，下次进入前台页面会自动获取最新配置
        console.warn('刷新字体配置失败', e);
      }
    },
    normalizeFontCdnUrl() {
      let val = (this.fontCdnConfig.configValue || '').trim();
      if (val && !val.endsWith('/')) {
        val = val + '/';
        this.fontCdnConfig.configValue = val;
      }
    },
    formatSize(bytes) {
      if (!bytes || bytes <= 0) return '0 B';
      const units = ['B', 'KB', 'MB'];
      let i = 0, size = bytes;
      while (size >= 1024 && i < 2) { size /= 1024; i++; }
      return size.toFixed(1) + ' ' + units[i];
    }
  }
};
</script>

<style scoped>
/* ui-ux-pro-max 规范样式补充 */
.primary-save-btn {
  padding: 12px 32px;
  font-weight: 500;
  border-radius: 8px;
  letter-spacing: 0.5px;
  box-shadow: 0 4px 12px rgba(64,158,255,0.3);
  transition: all 0.25s ease;
}
.primary-save-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(64,158,255,0.4);
}

/* ===== 字体优化区块 ===== */
.font-opt {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}
.font-opt__head {
  margin-bottom: 20px;
}
.font-opt__title {
  margin: 0 0 8px 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}
.font-opt__desc {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.7;
  max-width: 65ch;
}

/* 步骤条 */
.font-opt__steps {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  padding: 14px 20px;
  background: #f9fafb;
  border: 1px solid #f0f1f3;
  border-radius: 10px;
}
.font-step {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.font-step__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #e5e7eb;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.font-step__text {
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}
.font-step__sep {
  flex: 1;
  height: 1px;
  background: #e5e7eb;
  margin: 0 16px;
  min-width: 20px;
}
@media (max-width: 768px) {
  .font-opt__steps {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .font-step__sep {
    display: none;
  }
}

/* 卡片网格 */
.font-opt__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 768px) {
  .font-opt__grid {
    grid-template-columns: 1fr;
  }
}
.font-card {
  background: #ffffff;
  border: 1px solid #f0f1f3;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  transition: border-color 0.2s ease;
}
.font-card:hover {
  border-color: #d6dbe0;
}
.font-card__bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.font-card__label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  letter-spacing: 0.02em;
}
.font-card__skeleton {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: #9ca3af;
  font-size: 13px;
}
.font-card__inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}
.font-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 4px;
}

/* 状态胶囊 */
.font-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  width: fit-content;
}
.font-pill.is-ready {
  background: #ecfdf5;
  color: #059669;
}
.font-pill.is-idle {
  background: #f3f4f6;
  color: #6b7280;
}
.font-pill__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.font-pill.is-ready .font-pill__dot {
  background: #059669;
  animation: fontDotPulse 2s ease-in-out infinite;
}
.font-pill.is-idle .font-pill__dot {
  background: #d1d5db;
}
@keyframes fontDotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

/* 指标 */
.font-stats {
  display: flex;
  gap: 12px;
}
.font-stat {
  flex: 1;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.font-stat__k {
  font-size: 12px;
  color: #9ca3af;
}
.font-stat__v {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  font-variant-numeric: tabular-nums;
}
.font-stat__u {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
  margin-left: 3px;
}

/* 上传区 */
::v-deep .font-uploader .el-upload {
  width: 100%;
}
::v-deep .font-uploader .el-upload-dragger {
  width: 100%;
  height: 150px;
  border: 1px dashed #dcdfe6;
  border-radius: 10px;
  background: #fbfdff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  transition: border-color 0.2s ease, background 0.2s ease;
}
::v-deep .font-uploader .el-upload-dragger:hover {
  border-color: #409EFF;
  background: rgba(64,158,255,0.03);
}
.font-uploader__idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.font-uploader__icon {
  color: #c0c4cc;
  transition: color 0.2s ease;
}
::v-deep .font-uploader .el-upload-dragger:hover .font-uploader__icon {
  color: #409EFF;
}
.font-uploader__text {
  font-size: 13px;
  color: #606266;
}
.font-uploader__text em {
  color: #409EFF;
  font-style: normal;
  font-weight: 500;
}
.font-uploader__busy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.font-uploader__busy-text {
  font-size: 13px;
  color: #409EFF;
  font-weight: 500;
}

/* 结果回显 */
.font-result {
  margin-top: 16px;
  padding: 10px 14px;
  background: #ecfdf5;
  border: 1px solid #d1fae5;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #047857;
}
.font-result i {
  font-size: 16px;
}

/* CDN 配置区 */
.font-cdn {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}
.font-cdn__bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
}
.font-cdn__label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.font-cdn__help {
  color: #c0c4cc;
  cursor: help;
  transition: color 0.2s;
}
.font-cdn__help:hover {
  color: #909399;
}
.font-cdn__field-label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}
.font-cdn__row {
  display: flex;
  gap: 12px;
  align-items: center;
}
.font-cdn__row .el-input {
  flex: 1;
}
.font-cdn__hint {
  margin: 10px 0 0 0;
  font-size: 12px;
  color: #9ca3af;
}
.font-cdn__hint strong {
  color: #409EFF;
  font-weight: 500;
}


@media (max-width: 768px) {
  .font-cdn__row {
    flex-direction: column;
    align-items: stretch;
  }
}

/* 按钮触觉反馈 */
.font-card__actions .el-button:active,
.font-cdn__row .el-button:active {
  transform: translateY(1px);
}

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

.my-tag {
  margin-bottom: 20px !important;
  width: 100%;
  text-align: left;
  background: var(--lightYellow);
  border: none;
  height: 40px;
  line-height: 40px;
  font-size: 16px;
  color: var(--black);
}

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
    padding: 0 10px !important;
  }
}

</style>

<!-- 全局样式：下拉弹层与字体区块暗色适配 -->
<style>
/* 带长描述的下拉（鼠标点击效果等）：弹层宽度封顶为视口宽并允许换行，避免移动端溢出屏幕 */
.appearance-select-dropdown {
  max-width: calc(100vw - 24px);
}

.appearance-select-dropdown .el-select-dropdown__item {
  white-space: normal;
  height: auto;
  line-height: 1.5;
  padding-top: 7px;
  padding-bottom: 7px;
}

/* 字体管理暗色模式适配 */
body.dark-mode .font-opt,
body.dark-mode .font-cdn {
  border-top-color: #374151;
}
body.dark-mode .font-opt__title,
body.dark-mode .font-cdn__label {
  color: #e5e7eb;
}
body.dark-mode .font-opt__desc,
body.dark-mode .font-cdn__field-label {
  color: #9ca3af;
}
body.dark-mode .font-opt__steps {
  background: #1f2937;
  border-color: #374151;
}
body.dark-mode .font-step__num {
  background: #374151;
  color: #d1d5db;
}
body.dark-mode .font-step__text {
  color: #e5e7eb;
}
body.dark-mode .font-step__sep {
  background: #374151;
}
body.dark-mode .font-card {
  background: #1f2937;
  border-color: #374151;
}
body.dark-mode .font-card:hover {
  border-color: #4b5563;
}
body.dark-mode .font-card__label {
  color: #e5e7eb;
}
body.dark-mode .font-card__skeleton {
  color: #6b7280;
}
body.dark-mode .font-pill.is-ready {
  background: rgba(5, 150, 105, 0.2);
  color: #67c23a;
}
body.dark-mode .font-pill.is-idle {
  background: #374151;
  color: #9ca3af;
}
body.dark-mode .font-pill.is-idle .font-pill__dot {
  background: #6b7280;
}
body.dark-mode .font-stat {
  background: #111827;
}
body.dark-mode .font-stat__k,
body.dark-mode .font-stat__u {
  color: #6b7280;
}
body.dark-mode .font-stat__v {
  color: #e5e7eb;
}
body.dark-mode .font-uploader .el-upload-dragger {
  background: #1f2937;
  border-color: #374151;
}
body.dark-mode .font-uploader .el-upload-dragger:hover {
  border-color: #409EFF;
  background: rgba(64,158,255,0.05);
}
body.dark-mode .font-uploader__text {
  color: #9ca3af;
}
body.dark-mode .font-result {
  background: rgba(5, 150, 105, 0.2);
  border-color: #059669;
  color: #67c23a;
}
body.dark-mode .font-cdn__help {
  color: #6b7280;
}
</style>
