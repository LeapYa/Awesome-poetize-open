<template>
  <div id="field-captcha">
    <SectionTag>智能验证码配置</SectionTag>

    <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
      <el-row style="margin-bottom: 20px;">
        <el-col :span="24">
          <el-form label-width="150px">
            <el-form-item label="启用智能验证码">
              <el-switch
                v-model="captchaConfig.enable"
                @change="handleCaptchaToggle">
              </el-switch>
              <span style="margin-left: 10px; color: #909399; font-size: 12px;">
                {{ captchaConfig.enable ? '已启用' : '已禁用' }}
              </span>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>

      <el-divider content-position="center">应用场景</el-divider>

      <el-row :gutter="20" style="margin-bottom: 20px;">
        <el-col :span="12">
          <el-card shadow="hover" :class="{ 'captcha-card-disabled': !captchaConfig.enable }" :body-style="{ padding: '15px' }">
            <div slot="header" class="clearfix">
              <span>登录验证</span>
              <el-switch v-model="captchaConfig.login" :disabled="!captchaConfig.enable" style="float: right;"></el-switch>
            </div>
            <div>
              <p>在用户登录时启用验证码</p>
              <p style="color: #909399; font-size: 12px;">推荐启用，可以防止暴力破解攻击</p>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" :class="{ 'captcha-card-disabled': !captchaConfig.enable }" :body-style="{ padding: '15px' }">
            <div slot="header" class="clearfix">
              <span>注册验证</span>
              <el-switch v-model="captchaConfig.register" :disabled="!captchaConfig.enable" style="float: right;"></el-switch>
            </div>
            <div>
              <p>在用户注册时启用验证码</p>
              <p style="color: #909399; font-size: 12px;">推荐启用，可以防止批量注册机器人</p>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" style="margin-bottom: 20px;">
        <el-col :span="12">
          <el-card shadow="hover" :class="{ 'captcha-card-disabled': !captchaConfig.enable }" :body-style="{ padding: '15px' }">
            <div slot="header" class="clearfix">
              <span>评论验证</span>
              <el-switch v-model="captchaConfig.comment" :disabled="!captchaConfig.enable" style="float: right;"></el-switch>
            </div>
            <div>
              <p>在用户发表评论时启用验证码</p>
              <p style="color: #909399; font-size: 12px;">可以减少垃圾评论</p>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" :class="{ 'captcha-card-disabled': !captchaConfig.enable }" :body-style="{ padding: '15px' }">
            <div slot="header" class="clearfix">
              <span>密码重置验证</span>
              <el-switch v-model="captchaConfig.reset_password" :disabled="!captchaConfig.enable" style="float: right;"></el-switch>
            </div>
            <div>
              <p>在用户重置密码时启用验证码</p>
              <p style="color: #909399; font-size: 12px;">推荐启用，可以提高账号安全性</p>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-divider content-position="center">设备适配</el-divider>

      <el-row :gutter="20" style="margin-bottom: 20px;">
        <el-col :span="12">
          <el-form label-width="200px">
            <el-form-item label="屏幕宽度阈值(px)">
              <el-tooltip content="小于此宽度使用滑动验证码，大于等于此宽度使用勾选验证码" placement="top">
                <el-input-number
                  v-model="captchaConfig.screenSizeThreshold"
                  :min="320" :max="1200" :step="1"
                  :disabled="!captchaConfig.enable">
                </el-input-number>
              </el-tooltip>
            </el-form-item>
          </el-form>
        </el-col>
        <el-col :span="12">
          <el-form label-width="200px">
            <el-form-item label="移动设备强制使用滑动验证">
              <el-tooltip content="在触摸设备上强制使用滑动验证码，无论屏幕大小" placement="top">
                <el-switch
                  v-model="captchaConfig.forceSlideForMobile"
                  :disabled="!captchaConfig.enable">
                </el-switch>
              </el-tooltip>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>

      <el-divider content-position="center">验证码参数</el-divider>

      <el-tabs v-model="activeCaptchaTab" :disabled="!captchaConfig.enable">
        <el-tab-pane label="勾选验证参数" name="checkbox">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form label-width="200px">
                <el-form-item label="轨迹敏感度阈值">
                  <el-tooltip content="直线率超过此值将被判定为机器人，值越小越严格" placement="top">
                    <el-slider
                      v-model="captchaConfig.checkbox.trackSensitivity"
                      :min="0.8" :max="0.99" :step="0.01"
                      :format-tooltip="value => value ? value.toFixed(2) : '0'"
                      :disabled="!captchaConfig.enable">
                    </el-slider>
                  </el-tooltip>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="12">
              <el-form label-width="200px">
                <el-form-item label="最少轨迹点数">
                  <el-tooltip content="鼠标轨迹至少需要记录的点数，越多越严格" placement="top">
                    <el-input-number
                      v-model="captchaConfig.checkbox.minTrackPoints"
                      :min="1" :max="10" :step="1"
                      :disabled="!captchaConfig.enable">
                    </el-input-number>
                  </el-tooltip>
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="滑动验证参数" name="slide">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form label-width="200px">
                <el-form-item label="精确度">
                  <el-tooltip content="滑动验证的精确度，值越小越精确" placement="top">
                    <el-input-number
                      v-model="captchaConfig.slide.accuracy"
                      :min="1" :max="10" :step="1"
                      :disabled="!captchaConfig.enable">
                    </el-input-number>
                  </el-tooltip>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="12">
              <el-form label-width="200px">
                <el-form-item label="成功阈值">
                  <el-tooltip content="滑动到最大距离的比例视为成功，值越大越难" placement="top">
                    <el-slider
                      v-model="captchaConfig.slide.successThreshold"
                      :min="0.8" :max="0.99" :step="0.01"
                      :format-tooltip="value => value ? (value * 100).toFixed(0) + '%' : '0%'"
                      :disabled="!captchaConfig.enable">
                    </el-slider>
                  </el-tooltip>
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>

      <div style="margin-top: 20px; margin-bottom: 22px; text-align: center;">
        <el-button type="primary" @click="saveCaptchaConfig" :loading="captchaLoading">保存智能验证码配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';

export default {
  name: 'CaptchaSettings',
  components: { SectionTag },
  data() {
    return {
      captchaConfig: {
        enable: false,
        login: true,
        register: true,
        comment: false,
        reset_password: true,
        screenSizeThreshold: 768,
        forceSlideForMobile: false,
        checkbox: {
          trackSensitivity: 0.9,
          minTrackPoints: 5
        },
        slide: {
          accuracy: 5,
          successThreshold: 0.9
        }
      },
      captchaLoading: false,
      activeCaptchaTab: 'checkbox'
    }
  },
  created() {
    this.getCaptchaConfig();
  },
  methods: {
    async getCaptchaConfig() {
      try {
        const res = await this.$http.get(this.$constant.baseURL + "/webInfo/getCaptchaConfig", {}, true);
        if (res.data) {
          this.captchaConfig = res.data;
        }
      } catch (error) {
        this.$message.error("获取智能验证码配置失败: " + error.message);
        throw error;
      }
    },
    handleCaptchaToggle(value) {
      this.captchaConfig.enable = value;
      if (!value) {
        this.$confirm('禁用智能验证码将降低网站安全性，可能导致机器人攻击，确定要禁用吗?', '安全提示', {
          confirmButtonText: '确定禁用',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.saveCaptchaConfig();
        }).catch(() => {
          this.captchaConfig.enable = true;
        });
      } else {
        this.saveCaptchaConfig();
      }
    },
    saveCaptchaConfig() {
      this.captchaLoading = true;
      this.$http.post(this.$constant.baseURL + "/webInfo/updateCaptchaConfig", this.captchaConfig, true)
        .then(() => {
          this.$message.success("智能验证码配置保存成功");
        })
        .catch((error) => {
          this.$message.error("保存智能验证码配置失败: " + error.message);
        })
        .finally(() => {
          this.captchaLoading = false;
        });
    }
  }
}
</script>

<style scoped>
.captcha-card-disabled {
  opacity: 0.5;
  pointer-events: none;
}

@media screen and (max-width: 768px) {
  .el-col {
    margin-bottom: 15px;
  }
}

@media screen and (max-width: 500px) {
  .el-row .el-col {
    width: 100% !important;
    max-width: 100% !important;
    flex: 0 0 100% !important;
  }
  #field-captcha .el-form-item__label {
    float: none;
    display: block;
    text-align: left;
    width: auto !important;
    padding-bottom: 6px;
  }
  #field-captcha .el-form-item__content {
    margin-left: 0 !important;
    clear: both;
  }
  #field-captcha .el-slider {
    width: 100%;
  }
}
</style>
