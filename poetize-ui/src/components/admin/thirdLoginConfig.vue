<template>
  <div>
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span style="line-height: 36px;"><i class="el-icon-key"></i> 第三方登录配置</span>
        <el-button 
          style="float: right; margin-left: 10px;" 
          size="small" 
          type="primary" 
          @click="saveConfig">保存配置</el-button>
        <el-tooltip content="启用/禁用第三方登录功能" placement="top">
          <el-switch
            style="float: right; margin-top: 10px"
            v-model="config.enable"
            active-color="#13ce66"
            inactive-color="#ff4949"
            @change="updateEnableStatus">
          </el-switch>
        </el-tooltip>
      </div>
      
      <el-alert
        title="配置说明"
        type="info"
        description="您需要在各平台开发者中心创建应用并获取Client ID和Secret。填写后用户可通过相应平台进行第三方登录。"
        show-icon
        :closable="false"
        style="margin-bottom: 20px;">
      </el-alert>
      
      <el-table
        :data="tableData"
        border
        style="width: 100%">
        <el-table-column
          prop="platform"
          label="平台"
          width="120">
          <template slot-scope="scope">
            <div style="display: flex; align-items: center;">
              <i :class="platformIcons[scope.row.type]" style="font-size: 20px; margin-right: 5px;"></i>
              {{ scope.row.platform }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="clientId"
          label="Client ID"
          width="220">
          <template slot-scope="scope">
            <template v-if="scope.row.type === 'twitter'">
              <el-input 
                v-model="config.twitter.client_key" 
                placeholder="请输入Twitter API Key"
                :disabled="!config.enable"></el-input>
            </template>
            <template v-else>
              <el-input 
                v-model="scope.row.config.client_id" 
                placeholder="请输入Client ID"
                :disabled="!config.enable"></el-input>
            </template>
          </template>
        </el-table-column>
        <el-table-column
          prop="clientSecret"
          label="Client Secret"
          width="220">
          <template slot-scope="scope">
            <el-input 
              v-model="scope.row.config.client_secret" 
              placeholder="请输入Client Secret"
              show-password
              :disabled="!config.enable"></el-input>
          </template>
        </el-table-column>
        <el-table-column
          prop="redirectUri"
          label="回调地址"
          width="280">
          <template slot-scope="scope">
            <el-input 
              v-model="scope.row.config.redirect_uri" 
              placeholder="请输入回调地址"
              :disabled="!config.enable"></el-input>
          </template>
        </el-table-column>
        <el-table-column
          label="启用"
          width="80"
          align="center">
          <template slot-scope="scope">
            <el-switch
              v-model="scope.row.enabled"
              active-color="#13ce66"
              inactive-color="#ff4949"
              :disabled="!config.enable">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="140"
          align="center">
          <template slot-scope="scope">
            <el-button 
              type="text" 
              size="small" 
              :disabled="!config.enable" 
              @click="window.open(scope.row.developerUrl, '_blank')">
              <i class="el-icon-link"></i> 开发者中心
            </el-button>
            <el-button 
              type="text" 
              size="small" 
              :disabled="!config.enable" 
              @click="testLogin(scope.row)">
              <i class="el-icon-check"></i> 测试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="form-tip" style="margin-top: 10px;">* 回调地址通常为 http://你的域名/callback/{平台标识}</div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'ThirdLoginConfig',
  data() {
    return {
      loading: false,
      config: {
        enable: true,
        github: {
          client_id: '',
          client_secret: '',
          redirect_uri: 'http://localhost:5000/callback/github',
          enabled: true
        },
        google: {
          client_id: '',
          client_secret: '',
          redirect_uri: 'http://localhost:5000/callback/google',
          enabled: true
        },
        twitter: {
          client_key: '',
          client_secret: '',
          redirect_uri: 'http://localhost:5000/callback/x',
          enabled: true
        },
        yandex: {
          client_id: '',
          client_secret: '',
          redirect_uri: 'http://localhost:5000/callback/yandex',
          enabled: true
        },
        gitee: {
          client_id: '',
          client_secret: '',
          redirect_uri: 'http://localhost:5000/callback/gitee',
          enabled: true
        }
      },
      platformIcons: {
        github: 'el-icon-s-custom',
        google: 'el-icon-s-promotion',
        twitter: 'el-icon-s-comment',
        yandex: 'el-icon-s-grid'
      }
    }
  },
  computed: {
    tableData() {
      return [
        {
          platform: 'GitHub',
          type: 'github',
          config: this.config.github,
          enabled: this.config.github.enabled,
          developerUrl: 'https://github.com/settings/developers'
        },
        {
          platform: 'Google',
          type: 'google',
          config: this.config.google,
          enabled: this.config.google.enabled,
          developerUrl: 'https://console.cloud.google.com/apis/credentials'
        },
        {
          platform: 'Twitter',
          type: 'twitter',
          config: this.config.twitter,
          enabled: this.config.twitter.enabled,
          developerUrl: 'https://developer.twitter.com/en/portal/dashboard'
        },
        {
          platform: 'Yandex',
          type: 'yandex',
          config: this.config.yandex,
          enabled: this.config.yandex.enabled,
          developerUrl: 'https://oauth.yandex.com/'
        },
        {
          platform: 'Gitee',
          type: 'gitee',
          config: this.config.gitee,
          enabled: this.config.gitee.enabled,
          developerUrl: 'https://gitee.com/settings/applications'
        }
      ];
    }
  },
  created() {
    this.getConfig();
  },
  methods: {
    // 获取配置
    getConfig() {
      this.loading = true;
      this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getThirdLoginConfig", {}, true)
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.config = res.data;
            // 确保每个平台配置都有enabled属性
            ['github', 'google', 'twitter', 'yandex', 'gitee'].forEach(platform => {
              if (this.config[platform] && !this.config[platform].hasOwnProperty('enabled')) {
                this.$set(this.config[platform], 'enabled', true);
              }
            });
          }
          this.loading = false;
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: "error"
          });
          this.loading = false;
        });
    },
    
    // 保存配置
    saveConfig() {
      this.loading = true;
      this.$http.post(this.$constant.pythonBaseURL + "/webInfo/updateThirdLoginConfig", this.config, true)
        .then((res) => {
          this.$message({
            message: "第三方登录配置保存成功",
            type: "success"
          });
          this.loading = false;
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: "error"
          });
          this.loading = false;
        });
    },
    
    // 启用/禁用第三方登录功能
    updateEnableStatus() {
      this.$message({
        message: this.config.enable ? "第三方登录功能已启用" : "第三方登录功能已禁用",
        type: "success"
      });
      this.saveConfig();
    },

    // 测试登录
    testLogin(platform) {
      if (!platform.config.client_id && (platform.type !== 'twitter' || !platform.config.client_key)) {
        this.$message({
          message: "请先填写Client ID/API Key",
          type: "warning"
        });
        return;
      }

      if (!platform.config.client_secret) {
        this.$message({
          message: "请先填写Client Secret",
          type: "warning"
        });
        return;
      }

      this.$message({
        message: `正在测试${platform.platform}登录...`,
        type: "info"
      });

      // 这里实际上应该打开登录窗口进行测试
      window.open(`/login/${platform.type}`, '_blank');
    }
  }
}
</script>

<style scoped>
.box-card {
  margin-bottom: 20px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
.el-table .cell {
  word-break: normal;
}
</style> 