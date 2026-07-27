<template>
  <div>
    <div class="page-header">
      <h3>页脚设置</h3>
      <p class="page-desc">集中管理页脚文案、页脚背景、页脚友链与备案号</p>
    </div>

    <el-form :model="webInfo" ref="footerForm" label-width="100px" class="demo-ruleForm">
      <!-- 极简页脚开关 -->
      <el-form-item id="field-minimal-footer" label="极简页脚" prop="minimalFooter">
        <div style="display: flex; align-items: center;">
          <el-switch v-model="webInfo.minimalFooter"></el-switch>
          <span :style="{
              marginLeft: '10px',
              fontSize: '12px',
              color: webInfo.minimalFooter ? '#67c23a' : '#f56c6c'
            }">
            {{ webInfo.minimalFooter ? '已开启' : '已关闭' }}
          </span>
        </div>
        <span class="tip">开启后页脚仅保留版权与备案信息，不显示页脚文案。</span>
      </el-form-item>

      <el-form-item id="field-footer" label="页脚文案" prop="footer">
        <el-input
          v-model="webInfo.footer"
          placeholder="页脚文案（极简页脚开启时不显示）"
          :disabled="webInfo.minimalFooter">
        </el-input>
      </el-form-item>

      <el-form-item id="field-footer-background" label="页脚背景" prop="footerBackgroundImage">
        <div style="display: flex">
          <el-input v-model="webInfo.footerBackgroundImage" placeholder="页脚背景图片URL（可选）"></el-input>
          <el-image lazy class="table-td-thumb"
                    style="margin-left: 10px"
                    v-if="webInfo.footerBackgroundImage"
                    :preview-src-list="[webInfo.footerBackgroundImage]"
                    :src="webInfo.footerBackgroundImage"
                    fit="cover"></el-image>
        </div>
        <uploadPicture :isAdmin="true" :prefix="'footerBackground'" style="margin-top: 15px"
                       @addPicture="addFooterBackgroundImage"
                       :maxSize="10"
                       :maxNumber="1"
                       :showTip="false"></uploadPicture>

        <!-- 背景图片配置选项 -->
        <div v-if="webInfo.footerBackgroundImage" style="margin-top: 15px;">
          <el-divider content-position="left">背景图片设置</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="背景大小" label-width="80px">
                <el-select v-model="footerBgConfig.backgroundSize" placeholder="选择背景大小">
                  <el-option label="覆盖 (cover)" value="cover"></el-option>
                  <el-option label="包含 (contain)" value="contain"></el-option>
                  <el-option label="自动 (auto)" value="auto"></el-option>
                  <el-option label="拉伸 (100% 100%)" value="100% 100%"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="背景位置" label-width="80px">
                <el-select v-model="footerBgConfig.backgroundPosition" placeholder="选择背景位置">
                  <el-option label="居中" value="center center"></el-option>
                  <el-option label="顶部居中" value="center top"></el-option>
                  <el-option label="底部居中" value="center bottom"></el-option>
                  <el-option label="左上角" value="left top"></el-option>
                  <el-option label="右上角" value="right top"></el-option>
                  <el-option label="左下角" value="left bottom"></el-option>
                  <el-option label="右下角" value="right bottom"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="重复方式" label-width="80px">
                <el-select v-model="footerBgConfig.backgroundRepeat" placeholder="选择重复方式">
                  <el-option label="不重复" value="no-repeat"></el-option>
                  <el-option label="重复" value="repeat"></el-option>
                  <el-option label="水平重复" value="repeat-x"></el-option>
                  <el-option label="垂直重复" value="repeat-y"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="透明度" label-width="80px">
                <el-slider v-model="footerBgConfig.opacity"
                         :min="0"
                         :max="100"
                         :step="5"
                         :format-tooltip="val => val + '%'"
                         @input="handleOpacityChange">
                </el-slider>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="文字阴影" label-width="80px">
                <el-switch v-model="footerBgConfig.textShadow"></el-switch>
                <span style="margin-left: 10px; color: #999; font-size: 12px;">增强文字可读性</span>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="遮罩颜色" label-width="80px">
                <div style="display: flex; align-items: center; gap: 10px;">
                  <el-color-picker v-model="footerBgConfig.maskColor"
                                 :predefine="['#000000', '#1a1a1a', '#333333', '#444444', '#555555', '#666666', '#FFFFFF']"
                                 show-alpha
                                 color-format="rgba"
                                 @change="handleMaskColorChange">
                  </el-color-picker>
                  <span style="color: #999; font-size: 12px;">调整遮罩颜色和透明度</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="效果预览" label-width="80px">
                <div style="width: 100px; height: 30px; border: 1px solid #ddd; border-radius: 4px; position: relative; overflow: hidden;">
                  <div v-if="webInfo.footerBackgroundImage"
                       :style="{
                         position: 'absolute',
                         top: 0,
                         left: 0,
                         right: 0,
                         bottom: 0,
                         backgroundImage: 'url(' + webInfo.footerBackgroundImage + ')',
                         backgroundSize: footerBgConfig.backgroundSize || 'cover',
                         backgroundPosition: footerBgConfig.backgroundPosition || 'center center',
                         backgroundRepeat: footerBgConfig.backgroundRepeat || 'no-repeat'
                       }"></div>
                  <div v-else style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: #f0f0f0;"></div>
                  <div :style="{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: footerBgConfig.maskColor || 'rgba(0, 0, 0, 0.5)'
                  }"></div>
                  <span :style="{
                    position: 'relative',
                    zIndex: 10,
                    color: 'white',
                    fontSize: '11px',
                    display: 'block',
                    textAlign: 'center',
                    lineHeight: '30px',
                    textShadow: footerBgConfig.textShadow ? '0 2px 8px rgba(0, 0, 0, 0.8), 0 1px 3px rgba(0, 0, 0, 0.6)' : 'none'
                  }">样例文字</span>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-form-item>

      <!-- 页脚友链（服务提供商展示） -->
      <el-form-item id="field-footer-friend-links" label="页脚友链">
        <div style="width: 100%;">
          <div v-for="(link, index) in footerFriendLinks" :key="index"
               style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
            <el-input v-model="link.name" placeholder="名称（如：又拍云）" style="width: 25%;" size="small"></el-input>
            <el-input v-model="link.url" placeholder="链接（如：https://www.upyun.com）" style="width: 40%;" size="small"></el-input>
            <el-input v-model="link.logo" placeholder="Logo URL（可选）" style="width: 25%;" size="small"></el-input>
            <el-button type="danger" icon="el-icon-delete" size="small" circle
                       @click="footerFriendLinks.splice(index, 1)"></el-button>
          </div>
          <el-button type="primary" size="small" icon="el-icon-plus" plain
                     @click="footerFriendLinks.push({ name: '', url: '', logo: '' })">
            添加友链
          </el-button>
          <span class="tip">在页脚展示 CDN / 云服务等提供商链接（如“本站由 XX 提供加速”），留空则不显示。</span>
        </div>
      </el-form-item>

      <el-form-item id="field-beian" label="ICP备案号">
        <el-input v-model="beianConfig.configValue" placeholder="如：京ICP备xxxxxxxx号-1（非中国大陆服务器可留空）"></el-input>
        <span class="tip">显示于页脚底部并链接到工信部备案系统；中国大陆服务器必填。</span>
      </el-form-item>

      <el-form-item id="field-police-beian" label="公安备案号">
        <el-input v-model="policeBeianConfig.configValue" placeholder="如：京公网安备 xxxxxxxxxxxxxx号（未办理可留空）"></el-input>
        <span class="tip">显示于页脚底部并链接到公安备案系统，留空则不显示。</span>
      </el-form-item>
    </el-form>

    <div class="myCenter" style="margin-bottom: 22px">
      <el-button type="primary" @click="save()">保存页脚设置</el-button>
    </div>
  </div>
</template>

<script>
  import { useMainStore } from '@/stores/main';
  import { setAdminContentLoading } from '@/utils/sessionValidation';

const uploadPicture = () => import( "../common/uploadPicture");

  export default {
    name: 'WebFooter',
    components: {
      uploadPicture
    },
    data() {
      return {
        webInfo: {
          id: null,
          footer: "",
          footerBackgroundImage: "",
          footerBackgroundConfig: "",
          minimalFooter: false
        },
        loading: false,

        footerBgConfig: {
          backgroundSize: 'cover',
          backgroundPosition: 'center center',
          backgroundRepeat: 'no-repeat',
          opacity: 100,
          textShadow: false,
          maskColor: 'rgba(0, 0, 0, 0.5)'
        },
        // 页脚友链配置
        footerFriendLinks: [],
        footerFriendLinksConfig: {
          id: null,
          configName: '页脚友链（服务提供商展示）',
          configKey: 'footer.friendLinks',
          configValue: '',
          configType: '2'
        },
        // 备案号配置（与配置管理共用 sys_config）
        beianConfig: {
          id: null,
          configName: '备案号',
          configKey: 'beian',
          configValue: '',
          configType: '2'
        },
        policeBeianConfig: {
          id: null,
          configName: '公安备案号',
          configKey: 'policeBeian',
          configValue: '',
          configType: '2'
        }
      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      }
    },

    created() {
      this.initializeData();
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
          await Promise.all([
            this.getWebInfo(),
            this.loadSysConfigs()
          ]);
        } catch (error) {
          console.error("初始化数据时出错:", error);
        } finally {
          this.setContentLoading(false);
        }
      },

      addFooterBackgroundImage(res) {
        this.webInfo.footerBackgroundImage = res;
      },

      // 处理透明度变化
      handleOpacityChange(val) {
        // 获取当前遮罩颜色
        let maskColor = this.footerBgConfig.maskColor;
        if (!maskColor) {
          maskColor = 'rgba(0, 0, 0, 0.5)';
        }

        // 如果是 rgba 格式，直接替换 alpha 值
        const rgbaMatch = maskColor.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+),?\s*([\d.]+)?\)$/);
        if (rgbaMatch) {
          const r = parseInt(rgbaMatch[1]);
          const g = parseInt(rgbaMatch[2]);
          const b = parseInt(rgbaMatch[3]);
          const alpha = (val / 100).toFixed(2);
          this.footerBgConfig.maskColor = `rgba(${r}, ${g}, ${b}, ${alpha})`;
        } else {
          // 如果是其他格式（hex等），转为 rgba
          const hex = maskColor.replace('#', '');
          let r, g, b;
          if (hex.length === 3) {
            r = parseInt(hex[0] + hex[0], 16);
            g = parseInt(hex[1] + hex[1], 16);
            b = parseInt(hex[2] + hex[2], 16);
          } else {
            r = parseInt(hex.substring(0, 2), 16);
            g = parseInt(hex.substring(2, 4), 16);
            b = parseInt(hex.substring(4, 6), 16);
          }
          const alpha = (val / 100).toFixed(2);
          this.footerBgConfig.maskColor = `rgba(${r}, ${g}, ${b}, ${alpha})`;
        }
      },

      // 处理遮罩颜色变化
      handleMaskColorChange(val) {
        // 从新的 maskColor 中提取 alpha 值，更新透明度滑块
        if (val) {
          const rgbaMatch = val.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+),?\s*([\d.]+)?\)$/);
          if (rgbaMatch && rgbaMatch[4]) {
            const alpha = parseFloat(rgbaMatch[4]);
            this.footerBgConfig.opacity = Math.round(alpha * 100);
          }
        }
      },

      async getWebInfo() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + "/admin/webInfo/getAdminWebInfoDetails", {}, true);
          if (!this.$common.isEmpty(res.data)) {
            this.webInfo.id = res.data.id;
            this.webInfo.footer = res.data.footer;
            this.webInfo.footerBackgroundImage = res.data.footerBackgroundImage || "";
            this.webInfo.footerBackgroundConfig = res.data.footerBackgroundConfig || "";
            this.webInfo.minimalFooter = !!res.data.minimalFooter;

            // 加载页脚背景配置
            if (this.webInfo.footerBackgroundConfig) {
              try {
                this.footerBgConfig = JSON.parse(this.webInfo.footerBackgroundConfig);
              } catch (e) {
                console.error("解析页脚背景配置失败:", e);
                // 使用默认配置
                this.footerBgConfig = {
                  backgroundSize: 'cover',
                  backgroundPosition: 'center center',
                  backgroundRepeat: 'no-repeat',
                  opacity: 100,
                  textShadow: false,
                  maskColor: 'rgba(0, 0, 0, 0.5)'
                };
              }
            }
          }
        } catch (error) {
          this.$message({
            message: error.message,
            type: "error"
          });
          throw error;
        }
      },

      // 加载页脚友链与备案号配置
      async loadSysConfigs() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + '/sysConfig/listConfig', {}, true);
          if (res && res.data) {
            const list = Array.isArray(res.data) ? res.data : (res.data.data || []);

            const friendLinksItem = list.find(c => c.configKey === 'footer.friendLinks');
            if (friendLinksItem) {
              this.footerFriendLinksConfig.id = friendLinksItem.id;
              this.footerFriendLinksConfig.configValue = friendLinksItem.configValue || '';
              try {
                const parsed = JSON.parse(friendLinksItem.configValue);
                this.footerFriendLinks = Array.isArray(parsed) ? parsed : [];
              } catch (_) {
                this.footerFriendLinks = [];
              }
            }

            const beianItem = list.find(c => c.configKey === 'beian');
            if (beianItem) {
              this.beianConfig.id = beianItem.id;
              this.beianConfig.configName = beianItem.configName || this.beianConfig.configName;
              this.beianConfig.configType = beianItem.configType || this.beianConfig.configType;
              this.beianConfig.configValue = beianItem.configValue || '';
            }

            const policeBeianItem = list.find(c => c.configKey === 'policeBeian');
            if (policeBeianItem) {
              this.policeBeianConfig.id = policeBeianItem.id;
              this.policeBeianConfig.configName = policeBeianItem.configName || this.policeBeianConfig.configName;
              this.policeBeianConfig.configType = policeBeianItem.configType || this.policeBeianConfig.configType;
              this.policeBeianConfig.configValue = policeBeianItem.configValue || '';
            }
          }
        } catch (e) {
          console.warn('加载页脚相关配置失败:', e);
        }
      },

      save() {
        this.$confirm('确认保存？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          // 仅更新页脚相关的 webInfo 字段
          const footerInfoToUpdate = {
            id: this.webInfo.id,
            footer: this.webInfo.footer,
            footerBackgroundImage: this.webInfo.footerBackgroundImage,
            footerBackgroundConfig: JSON.stringify(this.footerBgConfig),
            minimalFooter: this.webInfo.minimalFooter
          };

          const promises = [
            this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", footerInfoToUpdate, true)
          ];

          // 保存页脚友链配置
          const validLinks = this.footerFriendLinks.filter(l => l.name && l.url);
          this.footerFriendLinksConfig.configValue = validLinks.length > 0 ? JSON.stringify(validLinks) : '';
          promises.push(
            this.$http.post(this.$constant.baseURL + '/sysConfig/saveOrUpdateConfig', this.footerFriendLinksConfig, true)
          );

          // 保存备案号配置
          promises.push(
            this.$http.post(this.$constant.baseURL + '/sysConfig/saveOrUpdateConfig', this.beianConfig, true)
          );
          promises.push(
            this.$http.post(this.$constant.baseURL + '/sysConfig/saveOrUpdateConfig', this.policeBeianConfig, true)
          );

          Promise.all(promises)
            .then(async () => {
              await Promise.all([this.getWebInfo(), this.loadSysConfigs()]);
              // 刷新 sysConfig store
              try {
                const sysConfRes = await this.$http.get(this.$constant.baseURL + '/sysConfig/listSysConfig');
                if (sysConfRes && sysConfRes.data) {
                  this.mainStore.loadSysConfig(sysConfRes.data);
                }
              } catch (_) {}
              // 更新mainStore中的webInfo，确保组件能立即响应变化
              this.mainStore.setWebInfo({...this.mainStore.webInfo, ...this.webInfo});
              this.$message({
                message: "保存成功！",
                type: "success"
              });
            })
            .catch((error) => {
              this.$message({
                message: error.message || '部分保存失败，请检查',
                type: 'error'
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消保存!'
          });
        });
      }
    }
  }
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

  .tip {
    display: block;
    margin-top: 8px;
    font-size: 12px;
    color: #999;
    line-height: 1.5;
  }

  .table-td-thumb {
    border-radius: 2px;
    width: 40px;
    height: 40px;
  }

  /* ===========================================
     表单移动端样式 - PC端和移动端响应式
     =========================================== */

  /* PC端样式 - 768px以上 */
  @media screen and (min-width: 769px) {
    ::v-deep .el-form-item__label {
      float: left !important;
    }
  }

  /* 移动端样式 - 768px及以下 */
  @media screen and (max-width: 768px) {
    /* 表单标签 - 垂直布局 */
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

    /* 输入框移动端优化 */
    ::v-deep .el-input__inner {
      font-size: 16px !important;
      height: 44px !important;
      border-radius: 8px !important;
    }

    /* 选择器移动端优化 */
    ::v-deep .el-select {
      width: 100% !important;
    }

    ::v-deep .el-select .el-input__inner {
      height: 44px !important;
      line-height: 44px !important;
    }

    /* 按钮移动端优化 */
    ::v-deep .el-button {
      min-height: 40px !important;
      border-radius: 8px !important;
    }
  }

  /* 极小屏幕优化 - 480px及以下 */
  @media screen and (max-width: 480px) {
    ::v-deep .el-form-item__label {
      font-size: 13px !important;
    }

    ::v-deep .el-input__inner,
    ::v-deep .el-select .el-input__inner {
      height: 40px !important;
      line-height: 40px !important;
      font-size: 15px !important;
    }

    ::v-deep .el-button {
      min-height: 38px !important;
      font-size: 14px !important;
    }
  }
</style>
