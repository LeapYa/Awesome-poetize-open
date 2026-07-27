<template>
  <div>
    <div class="page-header">
      <h3>登录页样式</h3>
      <p class="page-desc">前台登录/注册页的样式、主题色与第三方登录按钮位置</p>
    </div>

    <el-tag effect="dark" class="my-tag">
      <i class="el-icon-brush" style="font-size:16px;vertical-align:-2px;margin-right:4px;"></i>
      登录页配置
    </el-tag>

    <el-form :model="webInfo" label-width="100px" class="demo-ruleForm">
      <!-- 登录页样式 -->
      <el-form-item id="field-login-style" label="登录页样式">
        <el-select
          v-model="webInfo.loginStyle"
          placeholder="请选择登录页样式"
          style="width: 100%; max-width: 420px"
          popper-class="appearance-select-dropdown">
          <el-option
            v-for="style in loginStyleOptions"
            :key="style.value"
            :label="style.label"
            :value="style.value">
            <span>{{ style.label }}</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">（{{ style.description }}）</span>
          </el-option>
        </el-select>
        <div style="margin-top: 5px; font-size: 12px; color: #909399;">
          前台登录/注册页的展示样式，保存后刷新前台生效
        </div>
      </el-form-item>

      <!-- 登录页主题色（仅现代样式生效；classic 保持原版配色） -->
      <el-form-item
        v-if="webInfo.loginStyle && webInfo.loginStyle !== 'classic'"
        id="field-login-accent"
        label="登录页主题色">
        <el-color-picker
          v-model="webInfo.loginAccentColor"
          :predefine="loginAccentPresets">
        </el-color-picker>
        <span style="margin-left: 10px; font-size: 12px; color: #909399;">
          {{ webInfo.loginAccentColor || (webInfo.loginStyle === 'frosted' ? '默认玫粉' : '默认中性色') }}
        </span>
        <div style="margin-top: 5px; font-size: 12px; color: #909399;">
          卡片系主按钮、磨砂典雅装饰条/聚焦线与所有现代样式下的账号弹窗、验证码滑块跟随此色；清空后磨砂典雅默认玫粉、其余默认中性色；经典双滑块保持原版配色
        </div>
      </el-form-item>

      <!-- 第三方登录按钮位置（仅卡片系样式生效） -->
      <el-form-item
        v-if="['card', 'glass', 'minimal', 'split'].includes(webInfo.loginStyle)"
        id="field-login-third-position"
        label="第三方位置">
        <el-radio-group v-model="webInfo.loginThirdPosition">
          <el-radio label="top">
            <span>表单上方</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">（带平台名大按钮，下接"或"分隔线）</span>
          </el-radio>
          <el-radio label="bottom">
            <span>表单下方</span>
            <span style="color: #909399; font-size: 12px; margin-left: 8px;">（账号密码在前，纯图标行在后）</span>
          </el-radio>
        </el-radio-group>
        <div style="margin-top: 5px; font-size: 12px; color: #909399;">
          第三方登录按钮的展示位置，仅对简约卡片/毛玻璃/极简纯色/左右分栏生效，其余样式的第三方位置为各自设计的一部分
        </div>
      </el-form-item>
    </el-form>

    <div class="myCenter" style="margin-top: 32px; margin-bottom: 22px">
      <el-button type="primary" @click="saveLoginStyleSettings" class="primary-save-btn">保存登录页设置</el-button>
    </div>
  </div>
</template>

<script>
import { useMainStore } from '@/stores/main';

export default {
  name: 'WebLoginStyle',
  data() {
    return {
      mainStore: useMainStore(),
      webInfo: {
        id: null,
        loginStyle: 'classic',
        loginAccentColor: null,
        loginThirdPosition: 'top'
      },
      // 登录页主题色预设色板（中性黑/玫粉/经典粉/蓝/绿/紫/橙）
      loginAccentPresets: ['#1f1f1f', '#f04494', '#ff416c', '#409eff', '#67c23a', '#722ed1', '#fa8c16'],
      // 登录页样式候选项
      loginStyleOptions: [
        { value: 'classic', label: '经典双滑块', description: '原版样式，存量用户无感' },
        { value: 'card', label: '简约卡片', description: '背景大图 + 中性黑白灰卡片' },
        { value: 'glass', label: '毛玻璃卡片', description: '背景大图 + 磨砂半透明卡片' },
        { value: 'split', label: '左右分栏', description: '左侧封面图+站点Logo/站名 + 右侧表单区，未配置Logo时显示网站名称' },
        { value: 'minimal', label: '极简纯色', description: '无背景图，纯色底居中窄表单' },
        { value: 'terminal', label: '终端极客风', description: '仿命令行界面，技术博客彩蛋' },
        { value: 'immersive', label: '沉浸式大字排版', description: '无卡片容器，全屏封面+超大标题+下划线表单' },
        { value: 'frosted', label: '磨砂典雅', description: '磨砂玻璃卡+下划线输入+蓝绿渐变胶囊按钮' }
      ]
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
          this.webInfo.id = res.data.id;
          this.webInfo.loginStyle = res.data.loginStyle || 'classic';
          this.webInfo.loginAccentColor = res.data.loginAccentColor || null;
          this.webInfo.loginThirdPosition = res.data.loginThirdPosition || 'top';
        }
      } catch (error) {
        this.$message({ message: error.message, type: "error" });
      }
    },
    async saveLoginStyleSettings() {
      // 后端 updateWebInfo 按字段跳空更新，仅提交本页字段不影响其他页配置
      const updateData = {
        id: this.webInfo.id,
        loginStyle: this.webInfo.loginStyle,
        // 主题色清空时提交空串覆盖旧值（后端仅跳过 null）
        loginAccentColor: this.webInfo.loginAccentColor || '',
        loginThirdPosition: this.webInfo.loginThirdPosition || 'top'
      };
      try {
        await this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", updateData, true);
        this.mainStore.setWebInfo({ ...this.mainStore.webInfo, ...updateData });
        this.getWebInfo();
        this.$message({ message: "保存成功！前台刷新后生效", type: "success" });
      } catch (error) {
        this.$message({ message: error.message || '保存失败', type: 'error' });
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

<!-- 全局样式：带长描述的下拉弹层宽度封顶并允许换行（随本页自带，避免依赖其他页面加载） -->
<style>
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
</style>
