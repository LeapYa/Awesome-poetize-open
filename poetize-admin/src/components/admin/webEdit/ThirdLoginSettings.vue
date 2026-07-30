<template>
  <div id="field-third-login">
    <SectionTag>第三方登录配置</SectionTag>

    <el-card class="box-card third-login-config" shadow="never" style="margin-top: 20px; border: none;">
      <el-row style="margin-bottom: 20px;">
        <el-col :span="24">
          <el-form label-width="150px">
            <el-form-item label="启用第三方登录">
              <el-switch
                v-model="globalEnabled"
                active-color="#13ce66"
                inactive-color="#ff4949">
              </el-switch>
              <span style="margin-left: 10px; color: #909399; font-size: 12px;">
                {{ globalEnabled ? '已启用' : '已禁用' }}
              </span>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>

      <div v-loading="loadingList" class="platform-cards">
        <el-card
          v-for="platform in platforms"
          :key="platform.platformType"
          shadow="never"
          :class="['platform-card', `${platform.platformType}-card`]"
          style="border: none;">
          <div class="platform-header">
            <div class="platform-logo">
              <img :src="getIcon(platform.platformType)" width="28" height="28" :alt="platform.platformName">
              <span class="platform-name">{{ platform.platformName }}</span>
            </div>
            <el-switch
              v-model="platform.enabled"
              active-color="#13ce66"
              inactive-color="#ff4949"
              :disabled="!globalEnabled">
            </el-switch>
          </div>

          <div class="platform-form">
            <el-form label-position="top" :disabled="!globalEnabled || !platform.enabled">
              <template v-if="platform.platformType === 'twitter'">
                <el-form-item label="Client Key">
                  <el-input v-model="platform.clientKey" placeholder="请输入Client Key"></el-input>
                </el-form-item>
              </template>
              <template v-else-if="platform.platformType === 'steam'">
                <el-form-item label="Web API Key（可选）">
                  <el-input v-model="platform.clientSecret" placeholder="留空仅能登录；填入后可显示 Steam 昵称与头像" show-password></el-input>
                </el-form-item>
              </template>
              <template v-else>
                <el-form-item label="Client ID">
                  <el-input v-model="platform.clientId" placeholder="请输入Client ID"></el-input>
                </el-form-item>
                <el-form-item label="Client Secret">
                  <el-input v-model="platform.clientSecret" placeholder="请输入Client Secret" show-password></el-input>
                </el-form-item>
              </template>
              <template v-if="platform.platformType === 'custom'">
                <el-form-item label="平台显示名">
                  <el-input v-model="platform.platformName" placeholder="登录按钮上展示的名称，如：站长SSO"></el-input>
                </el-form-item>
                <el-form-item label="授权端点">
                  <el-input v-model="platform.authorizeUrl" placeholder="https://sso.example.com/oauth2/authorize"></el-input>
                </el-form-item>
                <el-form-item label="令牌端点">
                  <el-input v-model="platform.tokenUrl" placeholder="https://sso.example.com/oauth2/token"></el-input>
                </el-form-item>
                <el-form-item label="用户信息端点">
                  <el-input v-model="platform.userInfoUrl" placeholder="https://sso.example.com/oauth2/userinfo"></el-input>
                </el-form-item>
                <el-form-item label="授权范围 scope">
                  <el-input v-model="platform.scope" placeholder="openid profile email"></el-input>
                </el-form-item>
                <el-form-item label="字段映射（留空按OIDC标准）">
                  <div class="custom-field-mapping">
                    <el-input v-model="platform.uidField" placeholder="用户标识：sub"></el-input>
                    <el-input v-model="platform.usernameField" placeholder="用户名：name"></el-input>
                    <el-input v-model="platform.avatarField" placeholder="头像：picture"></el-input>
                    <el-input v-model="platform.emailField" placeholder="邮箱：email"></el-input>
                  </div>
                  <div class="redirect-uri-tip">
                    <span>支持点号路径取嵌套值，如 data.id</span>
                  </div>
                </el-form-item>
              </template>
              <el-form-item label="回调地址">
                <el-input v-if="!platform.customRedirect" :value="effectiveRedirectUri(platform)" readonly>
                  <el-button slot="append" icon="el-icon-document-copy" @click="copyRedirectUri(platform)">复制</el-button>
                </el-input>
                <el-input v-else v-model="platform.redirectUri" placeholder="请输入自定义回调地址"></el-input>
                <div class="redirect-uri-tip">
                  <span v-if="!platform.customRedirect">已按站点地址自动生成，申请应用时复制填入即可</span>
                  <span v-else>留空则自动生成</span>
                  <el-link type="primary" :underline="false" class="redirect-uri-toggle" @click="toggleCustomRedirect(platform)">
                    {{ platform.customRedirect ? '恢复自动生成' : '自定义' }}
                  </el-link>
                </div>
              </el-form-item>
            </el-form>
          </div>

          <div v-if="getNote(platform.platformType)" class="platform-note">
            <i class="el-icon-warning-outline"></i>
            {{ getNote(platform.platformType) }}
          </div>

          <div class="platform-actions">
            <el-button v-if="getDeveloperUrl(platform.platformType)" type="text" icon="el-icon-link" :disabled="!globalEnabled || !platform.enabled" @click="openDeveloperCenter(platform.platformType)">开发者中心</el-button>
            <el-button type="text" icon="el-icon-check" :disabled="!globalEnabled || !platform.enabled" @click="testLogin(platform)">测试</el-button>
          </div>
        </el-card>
      </div>

      <div class="form-tip" style="margin-top: 15px; font-size: 13px; color: #909399;">
        * 回调地址已根据站点地址自动生成，需与在第三方平台申请应用时填写的回调地址完全一致
      </div>

      <div style="margin-top: 20px; margin-bottom: 22px; text-align: center;">
        <el-button type="primary" @click="saveConfigs" :loading="loading">保存第三方登录配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';

// 纯前端渲染元数据：图标、开发者中心链接与平台接入提示，不涉及业务逻辑
// 新增平台只需在数据库插入一行，前端无需修改代码
const PLATFORM_META = {
  github:  { icon: '/admin/static/svg/github.svg',  developerUrl: 'https://github.com/settings/developers' },
  google:  { icon: '/admin/static/svg/google.svg',  developerUrl: 'https://console.cloud.google.com/apis/credentials' },
  twitter: { icon: '/admin/static/svg/x.svg',       developerUrl: 'https://developer.twitter.com/en/portal/dashboard' },
  yandex:  { icon: '/admin/static/svg/yandex.svg',  developerUrl: 'https://oauth.yandex.com/' },
  gitee:   { icon: '/admin/static/svg/gitee.svg',   developerUrl: 'https://gitee.com/oauth/applications' },
  qq:      { icon: '/admin/static/svg/qq.svg',      developerUrl: 'https://connect.qq.com/manage.html' },
  weibo:   { icon: '/admin/static/svg/weibo.svg',   developerUrl: 'https://open.weibo.com/apps' },
  // 百度老控制台 developer.baidu.com/console 年久失修易白屏，改指 OAuth 接入指南（含注册与建应用步骤）
  baidu:   {
    icon: '/admin/static/svg/baidu.svg',
    developerUrl: 'https://openauth.baidu.com/doc/regdevelopers.html',
    note: '百度新应用注册通道目前近乎瘫痪（官方注册页404、控制台白屏），已持有 API Key 的老应用仍可正常使用',
  },
  // 爱发电 OAuth 需联系官方人工开通（提供应用名称/可信域名换取 clientID），指向官方接入文档
  afdian:  {
    icon: '/admin/static/svg/afdian.svg',
    developerUrl: 'https://guide.afdian.com/creator/oauth2',
    note: '爱发电不支持自助开通，需联系官方提供应用名称与可信域名，由人工分配 Client ID/Secret',
  },
  linuxdo: { icon: '/admin/static/svg/linuxdo.svg', developerUrl: 'https://connect.linux.do' },
  microsoft: {
    icon: '/admin/static/svg/microsoft.svg',
    developerUrl: 'https://entra.microsoft.com',
    note: '登录服务国内可直连；个人 Microsoft 账户需先创建免费 Entra 租户才能注册应用',
  },
  gitlab: {
    icon: '/admin/static/svg/gitlab.svg',
    developerUrl: 'https://gitlab.com/-/user_settings/applications',
    note: 'gitlab.com 在国内访问不稳定，授权页可能加载缓慢或失败',
  },
  yuque:   { icon: '/admin/static/svg/yuque.svg',   developerUrl: 'https://www.yuque.com/settings/apps' },
  huawei: {
    icon: '/admin/static/svg/huawei.svg',
    developerUrl: 'https://developer.huawei.com/consumer/cn/console',
    note: '需在华为开发者联盟创建应用并开通账号服务（Account Kit），审核面向移动生态，网页接入流程较繁琐',
  },
  xiaomi: {
    icon: '/admin/static/svg/xiaomi.svg',
    developerUrl: 'https://dev.mi.com/console',
    note: '需在小米开放平台完成开发者实名认证并申请账号服务（OAuth）接入',
  },
  apple: {
    icon: '/admin/static/svg/apple.svg',
    developerUrl: 'https://developer.apple.com/account/resources/identifiers/list/serviceId',
    note: '需 Apple Developer Program 会员（99美元/年）；Client Secret 非静态密钥，需用 .p8 私钥按官方文档签发 JWT 填入，最长6个月有效期需定期更换',
  },
  custom: {
    icon: '/admin/static/svg/custom.svg',
    note: '适用于任意标准 OAuth2/OIDC 服务（Keycloak、Casdoor、Logto、Authelia 等自建 SSO）；字段映射留空时按 OIDC 标准声明（sub/name/picture/email）解析',
  },
  steam: {
    icon: '/admin/static/svg/steam.svg',
    developerUrl: 'https://steamcommunity.com/dev/apikey',
    note: 'Steam 使用 OpenID 2.0，无需 Client ID/Secret 即可登录；开发者中心用于免费申请 Web API Key（填入后才能显示昵称与头像）；steamcommunity.com 国内访问可能不稳定',
  },
};

export default {
  name: 'ThirdLoginSettings',
  components: { SectionTag },
  data() {
    return {
      globalEnabled: false,
      platforms: [],
      loadingList: false,
      loading: false,
    };
  },
  created() {
    this.loadConfigs();
  },
  methods: {
    async loadConfigs() {
      this.loadingList = true;
      try {
        const res = await this.$http.get(this.$constant.baseURL + '/admin/third-party-config/list');
        const list = res.data || [];
        // customRedirect：已保存的回调地址与自动生成值不一致时视为自定义
        this.platforms = list.map(p => ({
          ...p,
          customRedirect: !!(p.redirectUri && p.redirectUri !== p.suggestedRedirectUri),
        }));
        // globalEnabled 由后端统一控制，所有平台同步，取任意一条即可
        this.globalEnabled = list.length > 0 && list[0].globalEnabled;
      } catch (error) {
        console.error('获取第三方登录配置失败:', error);
        this.$message.error('获取第三方登录配置失败: ' + error.message);
      } finally {
        this.loadingList = false;
      }
    },
    // 生效的回调地址：自定义值优先，否则用后端自动生成的建议值
    effectiveRedirectUri(platform) {
      if (platform.customRedirect && platform.redirectUri) {
        return platform.redirectUri;
      }
      return platform.suggestedRedirectUri || platform.redirectUri || '';
    },
    toggleCustomRedirect(platform) {
      if (platform.customRedirect) {
        // 恢复自动生成：清空自定义值，保存后由后端按站点地址自动生成
        platform.customRedirect = false;
        platform.redirectUri = '';
      } else {
        // 切换自定义：预填当前生效值便于修改
        platform.redirectUri = this.effectiveRedirectUri(platform);
        platform.customRedirect = true;
      }
    },
    async copyRedirectUri(platform) {
      const text = this.effectiveRedirectUri(platform);
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text);
        } else {
          const textarea = document.createElement('textarea');
          textarea.value = text;
          textarea.style.position = 'fixed';
          textarea.style.opacity = '0';
          document.body.appendChild(textarea);
          textarea.select();
          document.execCommand('copy');
          document.body.removeChild(textarea);
        }
        this.$message.success('回调地址已复制');
      } catch (error) {
        this.$message.error('复制失败，请手动复制');
      }
    },
    getIcon(type) {
      return (PLATFORM_META[type] || {}).icon || '';
    },
    getNote(type) {
      return (PLATFORM_META[type] || {}).note || '';
    },
    getDeveloperUrl(type) {
      return (PLATFORM_META[type] || {}).developerUrl || '';
    },
    openDeveloperCenter(type) {
      const url = (PLATFORM_META[type] || {}).developerUrl;
      if (url) window.open(url, '_blank');
      else this.$message.warning('开发者中心链接未配置');
    },
    testLogin(platform) {
      if (!this.globalEnabled || !platform.enabled) {
        this.$message.error('该平台登录功能未启用'); return;
      }
      const type = platform.platformType;
      // Steam 免凭据，不做必填校验；custom 与其他平台仍需 Client ID/Key
      if (type === 'twitter' && !platform.clientKey) {
        this.$message.error('请先填写完整的 API Key 和 Secret'); return;
      } else if (type !== 'twitter' && type !== 'steam' && !platform.clientId) {
        this.$message.error('请先填写完整的 Client ID 和 Secret'); return;
      }
      const loginType = type === 'twitter' ? 'x' : type;
      window.open(`${this.$constant.baseURL}/login/${loginType}`, '_blank', 'width=800,height=600');
    },
    saveConfigs() {
      for (const platform of this.platforms) {
        if (!platform.enabled) continue;
        const type = platform.platformType;
        // Steam 免凭据（OpenID 2.0），Web API Key 也为可选，无必填项
        if (type === 'steam') continue;
        if (type === 'twitter' && !platform.clientKey) {
          this.$message.error(`${platform.platformName} 的 Client Key 不能为空`); return;
        } else if (type !== 'twitter' && !platform.clientId) {
          this.$message.error(`${platform.platformName} 的 Client ID 不能为空`); return;
        }
        if (!platform.clientSecret) {
          this.$message.error(`${platform.platformName} 的 Client Secret 不能为空`); return;
        }
        if (type === 'custom' && (!platform.authorizeUrl || !platform.tokenUrl || !platform.userInfoUrl)) {
          this.$message.error(`${platform.platformName} 的授权/令牌/用户信息端点均不能为空`); return;
        }
      }
      this.loading = true;
      // 将当前 globalEnabled 状态同步到每一条记录；非自定义回调地址存空值，由后端按站点地址自动生成
      const payload = this.platforms.map(p => {
        const { customRedirect, suggestedRedirectUri, ...rest } = p;
        return {
          ...rest,
          redirectUri: customRedirect ? (p.redirectUri || '') : '',
          globalEnabled: this.globalEnabled,
        };
      });
      this.$http.put(this.$constant.baseURL + '/admin/third-party-config/batch', payload)
        .then(() => {
          this.$message({ message: '第三方登录配置保存成功', type: 'success' });
          this.$bus.$emit('thirdPartyLoginConfigChanged');
        })
        .catch((error) => { this.$message({ message: error.message || '保存失败', type: 'error' }); })
        .finally(() => { this.loading = false; });
    },
  },
};
</script>

<style scoped>
.third-login-config .platform-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}
.platform-card {
  border-radius: 8px;
  transition: background-color 0.3s ease, border-color 0.3s ease, transform 0.3s ease;
  transform: translateZ(0);
}
.platform-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.1);
}
.platform-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}
.platform-logo { display: flex; align-items: center; }
.platform-name { font-size: 18px; font-weight: 500; margin-left: 10px; }
.platform-form { margin-bottom: 15px; }
.redirect-uri-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.redirect-uri-toggle {
  font-size: 12px;
  flex-shrink: 0;
  margin-left: 8px;
}
.custom-field-mapping {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.platform-note {
  margin-bottom: 10px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #E6A23C;
  background: rgba(230, 162, 60, 0.08);
  border-radius: 4px;
}
.platform-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
  border-top: 1px dashed #f0f0f0;
}
.github-card .platform-header { color: #333; }
.google-card .platform-header { color: #4285F4; }
.twitter-card .platform-header { color: #1DA1F2; }
.yandex-card .platform-header { color: #FF0000; }

@media screen and (max-width: 768px) {
  .third-login-config .platform-cards { grid-template-columns: 1fr; gap: 15px; }
}
@media screen and (max-width: 500px) {
  .third-login-config .platform-cards { grid-template-columns: 1fr; gap: 10px; padding: 0; }
  .platform-card { margin: 0; border-radius: 4px; }
  .platform-card:hover { transform: none; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); }
  .platform-header { flex-direction: column; align-items: flex-start; gap: 10px; }
  .platform-actions { flex-direction: column; gap: 8px; }
  .platform-actions .el-button { width: 100%; margin: 0 !important; }
}
</style>
