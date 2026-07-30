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
              <el-button
                size="small"
                icon="el-icon-plus"
                style="float: right;"
                @click="addCustomPlatform">
                添加自定义平台
              </el-button>
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
              <el-tooltip v-if="getNote(platform.platformType)" placement="top">
                <div slot="content" style="max-width: 280px; line-height: 1.6;">{{ getNote(platform.platformType) }}</div>
                <i class="el-icon-warning-outline platform-note-icon"></i>
              </el-tooltip>
            </div>
            <el-switch
              v-model="platform.enabled"
              active-color="#13ce66"
              inactive-color="#ff4949"
              :disabled="!globalEnabled">
            </el-switch>
          </div>

          <div class="platform-form">
            <!-- 自定义平台卡片仅展示摘要，完整配置在编辑对话框中 -->
            <div v-if="isCustomType(platform)" class="custom-card-summary">
              <div class="custom-summary-line"><span>授权端点</span>{{ platform.authorizeUrl || '未配置' }}</div>
              <div class="custom-summary-line"><span>令牌端点</span>{{ platform.tokenUrl || '未配置' }}</div>
              <div class="custom-summary-line"><span>用户信息端点</span>{{ platform.userInfoUrl || '未配置' }}</div>
            </div>
            <!-- 配置随时可填（先填凭据再开启是正常流程），开关仅控制平台是否生效 -->
            <el-form v-else label-position="top">
              <template v-if="platform.platformType === 'twitter'">
                <el-form-item label="Client Key">
                  <el-input v-model="platform.clientKey" placeholder="请输入Client Key"></el-input>
                </el-form-item>
                <el-form-item label="Client Secret">
                  <el-input v-model="platform.clientSecret" placeholder="请输入Client Secret" show-password></el-input>
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
              <el-form-item label="回调地址">
                <el-input v-if="!platform.customRedirect" key="redirect-auto" :value="effectiveRedirectUri(platform)" readonly>
                  <el-button slot="append" icon="el-icon-document-copy" @click="copyRedirectUri(platform)">复制</el-button>
                </el-input>
                <el-input v-else key="redirect-custom" v-model="platform.redirectUri" placeholder="请输入自定义回调地址"></el-input>
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

          <div class="platform-actions">
            <template v-if="isCustomType(platform)">
              <el-button type="text" icon="el-icon-edit" @click="editCustomPlatform(platform)">编辑</el-button>
              <el-button type="text" icon="el-icon-delete" style="color: #F56C6C;" @click="removeCustomPlatform(platform)">删除</el-button>
              <el-button type="text" icon="el-icon-check" :disabled="!globalEnabled || !platform.enabled" @click="testLogin(platform)">测试</el-button>
            </template>
            <template v-else>
              <el-button v-if="getDeveloperUrl(platform.platformType)" type="text" icon="el-icon-link" :disabled="!globalEnabled || !platform.enabled" @click="openDeveloperCenter(platform.platformType)">开发者中心</el-button>
              <el-button type="text" icon="el-icon-check" :disabled="!globalEnabled || !platform.enabled" @click="testLogin(platform)">测试</el-button>
            </template>
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

    <!-- 自定义 OAuth2/OIDC 平台配置对话框 -->
    <el-dialog
      :title="editingCustom ? `${editingCustom.platformName}（OAuth2/OIDC）` : '自定义平台'"
      :visible.sync="customDialogVisible"
      width="560px"
      custom-class="centered-dialog"
      append-to-body>
      <template v-if="editingCustom">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
          {{ getNote('custom') }}
        </el-alert>
        <el-form label-position="top">
          <el-form-item label="启用自定义平台">
            <el-switch
              v-model="editingCustom.enabled"
              active-color="#13ce66"
              inactive-color="#ff4949"
              :disabled="!globalEnabled">
            </el-switch>
            <span v-if="!globalEnabled" style="margin-left: 10px; color: #909399; font-size: 12px;">需先启用第三方登录总开关</span>
          </el-form-item>
        </el-form>
        <!-- 配置随时可填，开关仅控制平台是否生效 -->
        <el-form label-position="top">
          <el-form-item label="平台显示名">
            <el-input v-model="editingCustom.platformName" placeholder="登录按钮上展示的名称，如：站长SSO"></el-input>
          </el-form-item>
          <el-form-item label="Client ID">
            <el-input v-model="editingCustom.clientId" placeholder="请输入Client ID"></el-input>
          </el-form-item>
          <el-form-item label="Client Secret">
            <el-input v-model="editingCustom.clientSecret" placeholder="请输入Client Secret" show-password></el-input>
          </el-form-item>
          <el-form-item label="授权端点">
            <el-input v-model="editingCustom.authorizeUrl" placeholder="https://sso.example.com/oauth2/authorize"></el-input>
          </el-form-item>
          <el-form-item label="令牌端点">
            <el-input v-model="editingCustom.tokenUrl" placeholder="https://sso.example.com/oauth2/token"></el-input>
          </el-form-item>
          <el-form-item label="用户信息端点">
            <el-input v-model="editingCustom.userInfoUrl" placeholder="https://sso.example.com/oauth2/userinfo"></el-input>
          </el-form-item>
          <el-form-item label="授权范围 scope">
            <el-input v-model="editingCustom.scope" placeholder="openid profile email"></el-input>
          </el-form-item>
          <el-form-item label="字段映射（留空按OIDC标准）">
            <div class="custom-field-mapping">
              <el-input v-model="editingCustom.uidField" placeholder="用户标识：sub"></el-input>
              <el-input v-model="editingCustom.usernameField" placeholder="用户名：name"></el-input>
              <el-input v-model="editingCustom.avatarField" placeholder="头像：picture"></el-input>
              <el-input v-model="editingCustom.emailField" placeholder="邮箱：email"></el-input>
            </div>
            <div class="redirect-uri-tip">
              <span>支持点号路径取嵌套值，如 data.id</span>
            </div>
          </el-form-item>
          <el-form-item label="回调地址">
            <template v-if="editingCustom.isNew">
              <el-input value="保存后自动生成" readonly disabled></el-input>
              <div class="redirect-uri-tip">
                <span>保存后按站点地址自动生成，可再次打开本对话框复制并登记到授权服务</span>
              </div>
            </template>
            <template v-else>
              <el-input v-if="!editingCustom.customRedirect" key="custom-redirect-auto" :value="effectiveRedirectUri(editingCustom)" readonly>
                <el-button slot="append" icon="el-icon-document-copy" @click="copyRedirectUri(editingCustom)">复制</el-button>
              </el-input>
              <el-input v-else key="custom-redirect-custom" v-model="editingCustom.redirectUri" placeholder="请输入自定义回调地址"></el-input>
              <div class="redirect-uri-tip">
                <span v-if="!editingCustom.customRedirect">已按站点地址自动生成，在授权服务中登记此地址即可</span>
                <span v-else>留空则自动生成</span>
                <el-link type="primary" :underline="false" class="redirect-uri-toggle" @click="toggleCustomRedirect(editingCustom)">
                  {{ editingCustom.customRedirect ? '恢复自动生成' : '自定义' }}
                </el-link>
              </div>
            </template>
          </el-form-item>
        </el-form>
      </template>
      <span slot="footer">
        <el-button @click="customDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="loading" @click="saveCustomConfig">保 存</el-button>
      </span>
    </el-dialog>
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
      customDialogVisible: false,
      // 当前对话框正在编辑的自定义平台（指向 platforms 中的某一项）
      editingCustom: null,
    };
  },
  created() {
    this.loadConfigs();
  },
  methods: {
    // 判断是否为自定义平台（custom 或 custom_*）
    isCustomType(platform) {
      return platform.platformType && platform.platformType.indexOf('custom') === 0;
    },
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
    // 平台元数据键归一：custom_* 统一复用 custom 的图标与提示
    metaKey(type) {
      return type && type.indexOf('custom') === 0 ? 'custom' : type;
    },
    getIcon(type) {
      return (PLATFORM_META[this.metaKey(type)] || {}).icon || '';
    },
    getNote(type) {
      return (PLATFORM_META[this.metaKey(type)] || {}).note || '';
    },
    getDeveloperUrl(type) {
      return (PLATFORM_META[this.metaKey(type)] || {}).developerUrl || '';
    },
    // 打开编辑对话框：指向卡片对应的 platforms 项，直接双向绑定
    editCustomPlatform(platform) {
      this.editingCustom = platform;
      this.customDialogVisible = true;
    },
    // 点击“添加”仅打开空白草稿对话框，真正创建延迟到“保存”，避免产生空壳记录
    addCustomPlatform() {
      this.editingCustom = {
        isNew: true,
        platformType: null,
        platformName: '自定义平台',
        clientId: '',
        clientSecret: '',
        authorizeUrl: '',
        tokenUrl: '',
        userInfoUrl: '',
        scope: 'openid profile email',
        uidField: '',
        usernameField: '',
        avatarField: '',
        emailField: '',
        redirectUri: '',
        customRedirect: false,
        enabled: false,
        suggestedRedirectUri: '',
      };
      this.customDialogVisible = true;
    },
    // 删除自定义平台
    removeCustomPlatform(platform) {
      this.$confirm(`确定删除「${platform.platformName}」吗？删除后使用该平台登录的用户将无法再次登录。`, '提示', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(async () => {
        try {
          const res = await this.$http.delete(
            this.$constant.baseURL + '/admin/third-party-config/custom/' + platform.platformType, {}, true);
          if (res.code === 200) {
            this.$message.success('删除成功');
            await this.loadConfigs();
            this.$bus.$emit('thirdPartyLoginConfigChanged');
          } else {
            this.$message.error(res.message || '删除失败');
          }
        } catch (error) {
          this.$message.error(error.message || '删除失败');
        }
      }).catch(() => {});
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
          this.$message.error(`${platform.platformName} 的 Client Key 不能为空`); return false;
        } else if (type !== 'twitter' && !platform.clientId) {
          this.$message.error(`${platform.platformName} 的 Client ID 不能为空`); return false;
        }
        if (!platform.clientSecret) {
          this.$message.error(`${platform.platformName} 的 Client Secret 不能为空`); return false;
        }
        if (type.indexOf('custom') === 0 && (!platform.authorizeUrl || !platform.tokenUrl || !platform.userInfoUrl)) {
          this.$message.error(`${platform.platformName} 的授权/令牌/用户信息端点均不能为空`); return false;
        }
      }
      this.loading = true;
      // 将当前 globalEnabled 状态同步到每一条记录；非自定义回调地址存空值，由后端按站点地址自动生成
      const payload = this.platforms.map(p => {
        const { customRedirect, suggestedRedirectUri, isNew, ...rest } = p;
        return {
          ...rest,
          redirectUri: customRedirect ? (p.redirectUri || '') : '',
          globalEnabled: this.globalEnabled,
        };
      });
      return this.$http.put(this.$constant.baseURL + '/admin/third-party-config/batch', payload)
        .then(() => {
          this.$message({ message: '第三方登录配置保存成功', type: 'success' });
          this.$bus.$emit('thirdPartyLoginConfigChanged');
          return true;
        })
        .catch((error) => {
          this.$message({ message: error.message || '保存失败', type: 'error' });
          return false;
        })
        .finally(() => { this.loading = false; });
    },
    // 对话框内保存：新建平台先创建行拿到 platform_type，再复用整体保存持久化字段
    async saveCustomConfig() {
      const c = this.editingCustom;
      if (!c) return;
      // 端点为自定义平台运行必需项，保存时统一校验（无论是否启用）
      if (!c.clientId) { this.$message.error(`${c.platformName} 的 Client ID 不能为空`); return; }
      if (!c.clientSecret) { this.$message.error(`${c.platformName} 的 Client Secret 不能为空`); return; }
      if (!c.authorizeUrl || !c.tokenUrl || !c.userInfoUrl) {
        this.$message.error(`${c.platformName} 的授权/令牌/用户信息端点均不能为空`); return;
      }

      // 新建平台：此刻才在后端创建行，取回唯一 platform_type 后并入列表
      if (c.isNew) {
        this.loading = true;
        let created;
        try {
          const res = await this.$http.post(this.$constant.baseURL + '/admin/third-party-config/custom', {}, true);
          if (res.code !== 200 || !res.data) {
            this.$message.error(res.message || '创建失败');
            return;
          }
          created = res.data;
        } catch (error) {
          this.$message.error(error.message || '创建失败');
          return;
        } finally {
          this.loading = false;
        }
        c.platformType = created.platformType;
        c.suggestedRedirectUri = created.suggestedRedirectUri;
        c.isNew = false;
        this.platforms.push(c);
      }

      const result = await this.saveConfigs();
      if (result) {
        this.customDialogVisible = false;
      }
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
/* 卡片内容纵向布局：表单区弹性伸展，操作按钮统一贴底，避免卡片高低不一 */
.platform-card ::v-deep .el-card__body {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.platform-card .platform-form { flex: 1; }
.platform-note-icon {
  margin-left: 8px;
  color: #E6A23C;
  font-size: 16px;
  cursor: help;
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
.custom-card-summary {
  margin-bottom: 15px;
  font-size: 13px;
  color: #606266;
}
.custom-summary-line {
  display: flex;
  align-items: baseline;
  margin-bottom: 8px;
  line-height: 1.5;
  word-break: break-all;
}
.custom-summary-line span {
  flex-shrink: 0;
  width: 96px;
  color: #909399;
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
