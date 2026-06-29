<template>
  <div class="system-log-page">
    <div class="handle-box log-filter-bar">
      <el-select v-model="filters.logType" clearable placeholder="日志类型" class="handle-select mrb10">
        <el-option label="登录日志" value="LOGIN"></el-option>
        <el-option label="安全日志" value="SECURITY"></el-option>
        <el-option label="操作日志" value="OPERATION"></el-option>
        <el-option label="AI日志" value="AI"></el-option>
      </el-select>
      <el-select v-model="filters.success" clearable placeholder="执行结果" class="handle-select mrb10">
        <el-option label="成功" :value="true"></el-option>
        <el-option label="失败" :value="false"></el-option>
      </el-select>
      <el-input v-model="filters.searchKey" placeholder="操作者/登录账号" class="handle-input mrb10"></el-input>
      <el-input v-model="filters.ip" placeholder="IP 地址" class="handle-input mrb10"></el-input>
      <el-date-picker
        v-model="filters.timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="yyyy-MM-dd HH:mm:ss"
        class="log-date-range mrb10">
      </el-date-picker>
      <el-button type="primary" icon="el-icon-search" @click="searchLogs">搜索</el-button>
      <el-button type="danger" @click="clearSearch">清除参数</el-button>
      <el-button type="warning" icon="el-icon-lock" @click="openBlacklistDialog">封禁列表</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="logs"
      border
      class="table"
      header-cell-class-name="table-header"
      empty-text="暂无系统日志">
      <el-table-column label="时间" width="180" align="center">
        <template slot-scope="scope">
          {{ formatLogTime(scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="92" align="center">
        <template slot-scope="scope">
          <el-tag :type="getTypeTag(scope.row.logType)" disable-transitions>
            {{ getTypeLabel(scope.row.logType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="78" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.success ? 'success' : 'danger'" disable-transitions>
            {{ scope.row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作者/登录账号" min-width="130" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ formatPrincipal(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="IP/地区" min-width="160" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ scope.row.ip || '-' }}</span>
          <span v-if="scope.row.location" class="muted-text"> / {{ scope.row.location }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="150" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ getActionLabel(scope.row.action) }}
        </template>
      </el-table-column>
      <el-table-column label="目标" min-width="130" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ formatTarget(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip></el-table-column>
      <el-table-column label="AI Token(输入/输出/合计)" width="170" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.logType === 'AI'" class="ai-token-cell">{{ formatTokenTriple(scope.row) }}</span>
          <span v-else class="muted-text">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" :width="isMobile ? 120 : 200" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" icon="el-icon-view" class="detail-btn" @click="openDetail(scope.row)">
            <span class="btn-text">详情</span>
          </el-button>
          <el-button
            type="text"
            icon="el-icon-document-copy"
            class="copy-btn"
            @click="copyLog(scope.row)"><span class="btn-text">复制</span></el-button>
          <el-button
            v-if="canBlockIp(scope.row)"
            type="text"
            icon="el-icon-lock"
            class="block-ip-btn"
            :loading="blockLoadingMap[scope.row.ip]"
            @click="handleBlockIp(scope.row)"><span class="btn-text">拉黑</span></el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :current-page="pagination.current"
        :page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        @size-change="handleSizeChange"
        @current-change="handlePageChange">
      </el-pagination>
    </div>

    <el-dialog
      title="日志详情"
      :visible.sync="detailVisible"
      width="680px"
      custom-class="centered-dialog"
      :append-to-body="true"
      destroy-on-close>
      <div v-if="currentLog" class="detail-dialog">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="时间">{{ formatLogTime(currentLog.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ getTypeLabel(currentLog.logType) }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="currentLog.success ? 'success' : 'danger'" size="mini">
              {{ currentLog.success ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作">{{ getActionLabel(currentLog.action) }}</el-descriptions-item>
          <el-descriptions-item label="操作者">{{ formatActor(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ currentLog.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="登录账号">{{ formatLoginAccount(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
          <el-descriptions-item label="地区">{{ currentLog.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求路径" :span="2">{{ currentLog.requestUri || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标对象" :span="2">{{ formatTarget(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ currentLog.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="AI Token" :span="2" v-if="currentLog.logType === 'AI'">
            输入 {{ tokenText(currentLog.promptTokens) }} ·
            输出 {{ tokenText(currentLog.completionTokens) }} ·
            合计 {{ tokenText(currentLog.totalTokens) }}
            <span v-if="isTokenEstimated(currentLog)" class="muted-text">（输入为本地估算，模型未上报输出）</span>
          </el-descriptions-item>
          <el-descriptions-item label="User-Agent" :span="2">{{ currentLog.userAgent || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-title">操作详情</div>
        <pre class="detail-json" v-text="formatDetail(currentLog.detail)"></pre>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button type="primary" @click="detailVisible = false">关 闭</el-button>
      </span>
    </el-dialog>

    <!-- 拉黑 IP 弹窗：选择封禁时长 -->
    <el-dialog
      :title="`拉黑 IP - ${blockDialog.ip || ''}`"
      :visible.sync="blockDialog.visible"
      width="520px"
      custom-class="centered-dialog"
      :append-to-body="true"
      destroy-on-close
      @closed="resetBlockDialog">
      <div class="block-form">
        <div class="block-row">
          <span class="block-label">目标 IP</span>
          <el-input
            v-model="blockDialog.ip"
            :disabled="!blockDialog.editable"
            :placeholder="blockDialog.editable ? '请输入 IP 地址' : ''"
            class="block-input"></el-input>
        </div>
        <div class="block-row">
          <span class="block-label">拉黑时长</span>
          <el-radio-group v-model="blockDialog.durationKey" class="block-input">
            <el-radio-button label="1h">1 小时</el-radio-button>
            <el-radio-button label="24h">24 小时</el-radio-button>
            <el-radio-button label="7d">7 天</el-radio-button>
            <el-radio-button label="30d">30 天</el-radio-button>
            <el-radio-button label="permanent">永久</el-radio-button>
          </el-radio-group>
        </div>
        <div class="block-row">
          <span class="block-label">原因</span>
          <el-input
            v-model="blockDialog.reason"
            type="textarea"
            :rows="2"
            :maxlength="200"
            show-word-limit
            placeholder="拉黑原因（可选，默认从日志生成）"
            class="block-input"></el-input>
        </div>
        <div class="block-tip">
          <i class="el-icon-warning-outline"></i>
          封禁后，该 IP 访问站点会被 Nginx 直接拦截并返回 403，连 HTML 源码也拿不到（爬虫/不执行 JS 的客户端均无效）。
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="blockDialog.visible = false">取 消</el-button>
        <el-button
          type="danger"
          :loading="blockDialog.loading"
          @click="confirmBlockIp">确定拉黑</el-button>
      </span>
    </el-dialog>

    <!-- 封禁列表弹窗 -->
    <el-dialog
      title="IP 封禁列表"
      :visible.sync="blacklistDialog.visible"
      width="820px"
      custom-class="centered-dialog"
      :append-to-body="true">
      <div class="blacklist-page">
        <el-tabs v-model="blacklistDialog.activeTab" @tab-click="handleBlacklistTabClick">
          <!-- 安全黑名单（全局拦截，403） -->
          <el-tab-pane label="安全黑名单" name="security">
            <div v-loading="blacklistDialog.loading" class="blacklist-tab-body">
              <div class="blacklist-toolbar">
                <el-input
                  v-model="blacklistDialog.search"
                  placeholder="按 IP / 原因搜索"
                  clearable
                  prefix-icon="el-icon-search"
                  class="blacklist-search"></el-input>
                <el-button type="danger" icon="el-icon-plus" @click="openAddBlockIp">添加封禁</el-button>
                <el-button type="primary" icon="el-icon-refresh" @click="loadBlacklist">刷新</el-button>
                <span class="blacklist-count">共 {{ blacklistFiltered.length }} 条</span>
              </div>
              <el-table
                :data="blacklistFiltered"
                border
                max-height="420"
                empty-text="暂无封禁记录"
                class="blacklist-table">
                <el-table-column prop="ip" label="IP" min-width="140"></el-table-column>
                <el-table-column label="类型" width="92" align="center">
                  <template slot-scope="scope">
                    <el-tag :type="scope.row.permanent ? 'danger' : 'warning'" size="mini" disable-transitions>
                      {{ scope.row.permanent ? '永久' : '定时' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="剩余时长" width="140" align="center">
                  <template slot-scope="scope">
                    {{ formatTtl(scope.row) }}
                  </template>
                </el-table-column>
                <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip></el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template slot-scope="scope">
                    <el-button
                      type="text"
                      icon="el-icon-unlock"
                      class="unblock-btn"
                      :loading="blacklistDialog.unblockLoading === scope.row.ip"
                      @click="confirmUnblock(scope.row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="blacklist-hint">
                <i class="el-icon-info"></i>
                安全黑名单由 SecurityFilter 在请求入口直接拦截（返回 403），来源：管理员手动拉黑 / 攻击阈值自动拉黑。默认 24 小时，可设永久。
              </div>
            </div>
          </el-tab-pane>
          <!-- 验证码自动封禁（仅拦截验证码流程，30 分钟） -->
          <el-tab-pane label="验证码自动封禁" name="captcha">
            <div v-loading="blacklistDialog.captchaLoading" class="blacklist-tab-body">
              <div class="blacklist-toolbar">
                <el-input
                  v-model="blacklistDialog.captchaSearch"
                  placeholder="按 IP 搜索"
                  clearable
                  prefix-icon="el-icon-search"
                  class="blacklist-search"></el-input>
                <el-button type="primary" icon="el-icon-refresh" @click="loadCaptchaBlockList">刷新</el-button>
                <span class="blacklist-count">共 {{ captchaFiltered.length }} 条</span>
              </div>
              <el-table
                :data="captchaFiltered"
                border
                max-height="420"
                empty-text="暂无封禁记录"
                class="blacklist-table">
                <el-table-column prop="ip" label="IP" min-width="160"></el-table-column>
                <el-table-column label="失败次数" width="120" align="center">
                  <template slot-scope="scope">
                    <el-tag type="warning" size="mini" disable-transitions>{{ scope.row.failCount }} 次</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="剩余封禁时间" width="160" align="center">
                  <template slot-scope="scope">
                    <el-tag :type="getCaptchaTimeTagType(scope.row.remainingMinutes)" size="mini" disable-transitions>
                      <i class="el-icon-time"></i> {{ scope.row.remainingMinutes }} 分钟
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template slot-scope="scope">
                    <el-button
                      type="text"
                      icon="el-icon-unlock"
                      class="unblock-btn"
                      :loading="blacklistDialog.captchaUnblockLoading === scope.row.ip"
                      @click="confirmCaptchaUnblock(scope.row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="blacklist-hint">
                <i class="el-icon-info"></i>
                验证码自动封禁由验证码服务在 5 分钟内验证失败 ≥15 次时触发，固定 30 分钟，<b>仅拦截验证码流程</b>，不影响其他请求。
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button type="primary" @click="blacklistDialog.visible = false">关 闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'systemLog',
  data() {
    return {
      loading: false,
      isMobile: false,
      logs: [],
      filters: {
        logType: '',
        success: '',
        searchKey: '',
        ip: '',
        timeRange: []
      },
      pagination: {
        current: 1,
        size: 10,
        total: 0
      },
      detailVisible: false,
      currentLog: null,
      blockLoadingMap: {},
      lastCopiedKey: '',
      blockDialog: {
        visible: false,
        ip: '',
        reason: '',
        durationKey: '24h',
        loading: false,
        sourceRow: null,
        editable: false
      },
      blacklistDialog: {
        visible: false,
        loading: false,
        unblockLoading: '',
        search: '',
        list: [],
        activeTab: 'security',
        captchaList: [],
        captchaLoading: false,
        captchaUnblockLoading: '',
        captchaSearch: '',
        captchaLoaded: false
      },
      durationKeyMap: {
        '1h': 3600,
        '24h': 86400,
        '7d': 604800,
        '30d': 2592000,
        'permanent': -1
      },
      typeLabels: {
        LOGIN: '登录日志',
        SECURITY: '安全日志',
        OPERATION: '操作日志',
        AI: 'AI日志'
      },
      actionLabels: {
        PASSWORD_LOGIN: '账号密码登录',
        THIRD_LOGIN: '第三方登录',
        OAUTH_LOGIN: 'OAuth 登录',
        LOGOUT: '退出登录',
        AUTH_REQUIRED: '未登录拦截',
        TOKEN_EXPIRED: '登录态失效',
        INVALID_TOKEN: '无效登录态',
        PERMISSION_DENIED: '权限拒绝',
        USER_STATUS_CHANGE: '用户状态变更',
        USER_ADMIRE_CHANGE: '用户赞赏变更',
        USER_TYPE_CHANGE: '用户类型变更',
        USER_DELETE: '删除用户',
        USER_REGISTER: '用户注册',
        USER_INFO_UPDATE: '更新用户信息',
        USER_SECRET_UPDATE: '修改密钥信息',
        USER_PASSWORD_RESET: '密码重置',
        ARTICLE_CREATE: '保存文章',
        ARTICLE_CREATE_ASYNC: '异步保存文章',
        ARTICLE_UPDATE: '更新文章',
        ARTICLE_UPDATE_ASYNC: '异步更新文章',
        ARTICLE_DELETE: '删除文章',
        ARTICLE_STATUS_CHANGE: '文章状态变更',
        ARTICLE_SUMMARY_BATCH_GENERATE: '批量生成摘要',
        ARTICLE_TRANSLATION_DELETE: '删除文章翻译',
        ARTICLE_TRANSLATION_DELETE_ALL: '删除全部翻译',
        ARTICLE_TRANSLATION_REGENERATE: '重生成翻译',
        ARTICLE_TRANSLATION_SAVE: '保存手动翻译',
        ARTICLE_SITEMAP_UPDATE: '更新文章站点地图',
        COMMENT_DELETE: '删除评论',
        TREE_HOLE_SAVE: '保存留言',
        TREE_HOLE_DELETE: '删除留言',
        RESOURCE_SAVE: '保存资源',
        RESOURCE_UPLOAD: '上传资源',
        RESOURCE_IMAGE_UPLOAD: '上传图片',
        RESOURCE_WAIFU_PREVIEW_UPLOAD: '上传看板预览',
        RESOURCE_DELETE: '删除资源',
        RESOURCE_REPLACE: '替换资源',
        RESOURCE_CHUNKS_MERGE: '合并分片',
        RESOURCE_STATUS_CHANGE: '资源状态变更',
        WEB_INFO_UPDATE: '网站信息更新',
        WEB_NOTICE_UPDATE: '公告更新',
        WEB_RANDOM_NAME_UPDATE: '随机名称更新',
        WEB_RANDOM_AVATAR_UPDATE: '随机头像更新',
        WEB_RANDOM_COVER_UPDATE: '随机封面更新',
        VISIT_DATA_CLEAN: '访问数据清理',
        THIRD_LOGIN_CONFIG_UPDATE: '第三方登录配置',
        API_CONFIG_SAVE: '接口配置保存',
        API_KEY_REGENERATE: '接口密钥重置',
        VISIT_CACHE_REFRESH: '访问缓存刷新',
        SYS_CONFIG_SAVE: '系统配置保存',
        SYS_CONFIG_DELETE: '系统配置删除',
        CAPTCHA_CONFIG_UPDATE: '验证码配置更新',
        MAIL_CONFIG_SAVE: '邮件配置保存',
        MAIL_CONFIG_TEST: '邮件配置测试',
        PLUGIN_ACTIVE_SET: '设置启用插件',
        PLUGIN_ADD: '新增插件',
        PLUGIN_UPDATE: '更新插件',
        PLUGIN_DELETE: '删除插件',
        PLUGIN_STATUS_TOGGLE: '插件状态变更',
        SEO_CONFIG_UPDATE: 'SEO 配置更新',
        SEO_ENABLE_UPDATE: 'SEO 状态变更',
        SEO_CACHE_CLEAR: 'SEO 缓存清理',
        SEO_ARTICLE_CACHE_CLEAR: '文章 SEO 缓存清理',
        SEO_ARTICLES_CACHE_CLEAR: '批量 SEO 缓存清理',
        SEO_IMAGE_PROCESS: 'SEO 图片处理',
        SEO_ICONS_BATCH_PROCESS: '批量图标处理',
        SEO_POETIZE_ICON_REPLACE: '替换站点图标',
        OAUTH_CONFIG_UPDATE: 'OAuth 平台配置',
        OAUTH_CONFIG_BATCH_UPDATE: 'OAuth 批量配置',
        OAUTH_CONFIG_GLOBAL_ENABLE: 'OAuth 全局开关',
        OAUTH_CONFIG_PLATFORM_ENABLE: 'OAuth 平台开关',
        OAUTH_CONFIG_MIGRATE_FILE: 'OAuth 文件迁移',
        OAUTH_CONFIG_MIGRATE_JSON: 'OAuth JSON 迁移',
        OAUTH_CONFIG_RESET: 'OAuth 恢复默认',
        SORT_SAVE: '保存分类',
        SORT_DELETE: '删除分类',
        SORT_UPDATE: '更新分类',
        LABEL_SAVE: '保存标签',
        LABEL_DELETE: '删除标签',
        LABEL_UPDATE: '更新标签',
        LOVE_SAVE: '保存表白墙',
        LOVE_DELETE: '删除表白墙',
        LOVE_STATUS_CHANGE: '表白墙状态变更',
        WEIYAN_SAVE: '保存微言',
        WEIYAN_NEWS_SAVE: '保存文章动态',
        WEIYAN_DELETE: '删除微言',
        AI_CHAT: 'AI聊天(同步)',
        AI_CHAT_STREAM: 'AI聊天(流式)',
        AI_COMMENT_REPLY: 'AI评论回复',
        AI_TRANSLATE: 'AI翻译',
        AI_SUMMARY: 'AI摘要'
      }
    };
  },
  created() {
    this.getLogs();
  },
  mounted() {
    this.handleResize();
    window.addEventListener('resize', this.handleResize);
    // 从其他页面跳转时通过 ?blacklist=1 自动打开封禁列表弹窗
    if (this.$route.query.blacklist === '1') {
      this.$nextTick(() => {
        this.openBlacklistDialog();
      });
    }
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize);
  },
  computed: {
    blacklistFiltered() {
      const kw = (this.blacklistDialog.search || '').trim().toLowerCase();
      if (!kw) {
        return this.blacklistDialog.list;
      }
      return this.blacklistDialog.list.filter(item => {
        const ip = (item.ip || '').toLowerCase();
        const reason = (item.reason || '').toLowerCase();
        return ip.indexOf(kw) >= 0 || reason.indexOf(kw) >= 0;
      });
    },
    captchaFiltered() {
      const kw = (this.blacklistDialog.captchaSearch || '').trim().toLowerCase();
      if (!kw) {
        return this.blacklistDialog.captchaList;
      }
      return this.blacklistDialog.captchaList.filter(item => {
        const ip = (item.ip || '').toLowerCase();
        return ip.indexOf(kw) >= 0;
      });
    }
  },
  methods: {
    handleResize() {
      this.isMobile = window.innerWidth <= 768;
    },
    buildQuery() {
      return {
        current: this.pagination.current,
        size: this.pagination.size,
        logType: this.filters.logType || null,
        success: this.filters.success === '' ? null : this.filters.success,
        searchKey: this.filters.searchKey || null,
        ip: this.filters.ip || null,
        startTime: this.filters.timeRange && this.filters.timeRange.length === 2 ? this.filters.timeRange[0] : null,
        endTime: this.filters.timeRange && this.filters.timeRange.length === 2 ? this.filters.timeRange[1] : null
      };
    },
    getLogs() {
      this.loading = true;
      this.$http.post(this.$constant.baseURL + '/admin/log/list', this.buildQuery(), true)
        .then((res) => {
          const page = res.data || {};
          this.logs = page.records || [];
          this.pagination.total = page.total || 0;
        })
        .catch((error) => {
          this.$message({
            message: error.message || '系统日志加载失败',
            type: 'error'
          });
        })
        .finally(() => {
          this.loading = false;
        });
    },
    searchLogs() {
      this.pagination.current = 1;
      this.getLogs();
    },
    clearSearch() {
      this.filters = {
        logType: '',
        success: '',
        searchKey: '',
        ip: '',
        timeRange: []
      };
      this.pagination.current = 1;
      this.getLogs();
    },
    handlePageChange(val) {
      this.pagination.current = val;
      this.getLogs();
    },
    handleSizeChange(val) {
      this.pagination.size = val;
      this.pagination.current = 1;
      this.getLogs();
    },
    openDetail(row) {
      this.currentLog = row;
      this.detailVisible = true;
    },
    getTypeLabel(type) {
      return this.typeLabels[type] || type || '-';
    },
    getTypeTag(type) {
      if (type === 'LOGIN') {
        return 'primary';
      }
      if (type === 'SECURITY') {
        return 'warning';
      }
      if (type === 'AI') {
        return 'success';
      }
      return 'info';
    },
    getActionLabel(action) {
      return this.actionLabels[action] || action || '-';
    },
    formatActor(row) {
      if (!row) {
        return '-';
      }
      return row.username || this.getLoginAccount(row) || '-';
    },
    formatPrincipal(row) {
      if (!row) {
        return '-';
      }
      const username = row.username || '';
      const account = this.getLoginAccount(row);
      if (username && account) {
        return username + ' / ' + account;
      }
      return username || account || '-';
    },
    formatLoginAccount(row) {
      return this.getLoginAccount(row) || '-';
    },
    getLoginAccount(row) {
      if (!row || !row.maskedAccount) {
        return '';
      }
      const account = String(row.maskedAccount).trim();
      if (!account) {
        return '';
      }
      if (row.username && account.toLowerCase() === row.username.toLowerCase()) {
        return '';
      }
      if (row.username && this.isLegacyMaskedUsername(account, row.username)) {
        return '';
      }
      if (row.username && row.logType === 'OPERATION') {
        return '';
      }
      return account;
    },
    isLegacyMaskedUsername(account, username) {
      const value = String(username || '').trim();
      if (!account || !value) {
        return false;
      }
      if (value.length <= 2) {
        return account === value.charAt(0) + '*';
      }
      if (value.length <= 4) {
        return account === value.charAt(0) + '**' + value.charAt(value.length - 1);
      }
      return account === value.substring(0, 2) + '***' + value.substring(value.length - 2);
    },
    formatTarget(row) {
      if (!row) {
        return '-';
      }
      if (row.targetType && row.targetId) {
        return row.targetType + ' #' + row.targetId;
      }
      return row.targetType || row.targetId || '-';
    },
    formatDetail(detail) {
      if (!detail) {
        return '{}';
      }
      if (typeof detail === 'object') {
        return JSON.stringify(detail, null, 2);
      }
      try {
        return JSON.stringify(JSON.parse(detail), null, 2);
      } catch (e) {
        return detail;
      }
    },
    formatTokenTriple(row) {
      if (!row) {
        return '-';
      }
      const p = row.promptTokens;
      const c = row.completionTokens;
      const t = row.totalTokens;
      if (p == null && c == null && t == null) {
        return '未上报';
      }
      return `${p != null ? p : '-'} / ${c != null ? c : '-'} / ${t != null ? t : '-'}`;
    },
    tokenText(value) {
      return value != null ? value : '-';
    },
    isTokenEstimated(row) {
      // 输入有值但输出为空 → 模型未上报 usage，输入走的是本地 jtokkit 估算兜底
      return !!row && row.promptTokens != null && row.completionTokens == null;
    },
    canBlockIp(row) {
      if (!row || !row.ip) {
        return false;
      }
      // 未知 IP 或本地占位不展示拉黑按钮
      const ip = String(row.ip).trim();
      return !!ip && ip !== '-' && !/^unknown$/i.test(ip);
    },
    buildLogText(row) {
      if (!row) {
        return '';
      }
      const lines = [];
      lines.push('=========== 系统日志 ===========');
      lines.push(`时间: ${this.formatLogTime(row.createTime)}`);
      lines.push(`类型: ${this.getTypeLabel(row.logType)}`);
      lines.push(`结果: ${row.success ? '成功' : '失败'}`);
      lines.push(`操作: ${this.getActionLabel(row.action)}`);
      lines.push(`操作者: ${this.formatActor(row) || '-'}`);
      if (row.userId) {
        lines.push(`用户 ID: ${row.userId}`);
      }
      const account = this.getLoginAccount(row);
      if (account) {
        lines.push(`登录账号: ${account}`);
      }
      const ipLine = row.ip || '-';
      const locLine = row.location || '';
      lines.push(`IP/地区: ${ipLine}${locLine ? ' / ' + locLine : ''}`);
      if (row.requestUri) {
        lines.push(`请求路径: ${row.requestUri}`);
      }
      const target = this.formatTarget(row);
      if (target && target !== '-') {
        lines.push(`目标对象: ${target}`);
      }
      if (row.summary) {
        lines.push(`摘要: ${row.summary}`);
      }
      if (row.logType === 'AI') {
        const p = row.promptTokens;
        const c = row.completionTokens;
        const t = row.totalTokens;
        if (p != null || c != null || t != null) {
          lines.push(`AI Token: 输入 ${p != null ? p : '-'} / 输出 ${c != null ? c : '-'} / 合计 ${t != null ? t : '-'}`);
          if (this.isTokenEstimated(row)) {
            lines.push('（输入为本地估算，模型未上报输出）');
          }
        }
      }
      if (row.userAgent) {
        lines.push(`User-Agent: ${row.userAgent}`);
      }
      const detailText = this.formatDetail(row.detail);
      if (detailText && detailText !== '{}') {
        lines.push('--------- 操作详情 ---------');
        lines.push(detailText);
      }
      lines.push('================================');
      return lines.join('\n');
    },
    async copyLog(row) {
      const text = this.buildLogText(row);
      if (!text) {
        this.$message.warning('没有可复制的内容');
        return;
      }
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text);
        } else {
          const ta = document.createElement('textarea');
          ta.value = text;
          ta.style.position = 'fixed';
          ta.style.top = '-9999px';
          document.body.appendChild(ta);
          ta.select();
          document.execCommand('copy');
          document.body.removeChild(ta);
        }
        this.lastCopiedKey = row.id != null ? String(row.id) : row.ip || '';
        this.$message({
          message: '已复制日志（格式化文本）',
          type: 'success',
          duration: 1500
        });
      } catch (e) {
        this.$message.error('复制失败，请手动选择文本');
      }
    },
    handleBlockIp(row) {
      const ip = row && row.ip ? String(row.ip).trim() : '';
      if (!ip) {
        this.$message.warning('该日志没有 IP 信息');
        return;
      }
      const defaultReason = `来自系统日志: ${this.getActionLabel(row.action) || row.logType || ''} - ${(row.summary || '').slice(0, 60)}`;
      this.blockDialog.ip = ip;
      this.blockDialog.reason = defaultReason;
      this.blockDialog.durationKey = '24h';
      this.blockDialog.sourceRow = row;
      this.blockDialog.loading = false;
      this.blockDialog.visible = true;
    },
    openAddBlockIp() {
      this.blockDialog.ip = '';
      this.blockDialog.reason = '';
      this.blockDialog.durationKey = '24h';
      this.blockDialog.sourceRow = null;
      this.blockDialog.editable = true;
      this.blockDialog.loading = false;
      this.blockDialog.visible = true;
    },
    resetBlockDialog() {
      this.blockDialog.ip = '';
      this.blockDialog.reason = '';
      this.blockDialog.durationKey = '24h';
      this.blockDialog.sourceRow = null;
      this.blockDialog.loading = false;
      this.blockDialog.editable = false;
    },
    durationKeyToSeconds(key) {
      return Object.prototype.hasOwnProperty.call(this.durationKeyMap, key)
        ? this.durationKeyMap[key]
        : 86400;
    },
    durationLabel(key) {
      const labels = {
        '1h': '1 小时',
        '24h': '24 小时',
        '7d': '7 天',
        '30d': '30 天',
        'permanent': '永久'
      };
      return labels[key] || '24 小时';
    },
    confirmBlockIp() {
      const ip = (this.blockDialog.ip || '').trim();
      if (!ip) {
        this.$message.warning('IP 不能为空');
        return;
      }
      const reason = (this.blockDialog.reason || '').trim();
      const durationSeconds = this.durationKeyToSeconds(this.blockDialog.durationKey);
      const permanent = durationSeconds < 0;

      this.$set(this.blockLoadingMap, ip, true);
      this.blockDialog.loading = true;
      this.$http.post(
        this.$constant.baseURL + '/admin/security/blockIp',
        { ip, reason, durationSeconds },
        true
      ).then((res) => {
        const data = (res && res.data) || {};
        const tip = data.alreadyBlocked
          ? `IP ${ip} 之前已在黑名单，已刷新为 ${this.durationLabel(this.blockDialog.durationKey)}`
          : `已拉黑 IP ${ip}，时长：${this.durationLabel(this.blockDialog.durationKey)}`;
        this.$message({ message: tip, type: 'success' });
        this.blockDialog.visible = false;
        this.getLogs();
        // 封禁列表弹窗打开时同步刷新
        if (this.blacklistDialog.visible) {
          this.loadBlacklist();
        }
      }).catch((error) => {
        this.$message({
          message: '拉黑 IP 失败: ' + (error && error.message ? error.message : '未知错误'),
          type: 'error'
        });
      }).finally(() => {
        this.$set(this.blockLoadingMap, ip, false);
        this.blockDialog.loading = false;
      });
    },
    openBlacklistDialog() {
      this.blacklistDialog.visible = true;
      this.loadBlacklist();
      // 验证码 tab 懒加载：仅在已访问过时刷新
      if (this.blacklistDialog.captchaLoaded) {
        this.loadCaptchaBlockList();
      }
    },
    handleBlacklistTabClick(tab) {
      if (tab && tab.name === 'captcha' && !this.blacklistDialog.captchaLoaded) {
        this.loadCaptchaBlockList();
      }
    },
    loadBlacklist() {
      this.blacklistDialog.loading = true;
      this.$http.get(this.$constant.baseURL + '/admin/security/blacklist', {}, true)
        .then((res) => {
          const list = (res && res.data) || [];
          this.blacklistDialog.list = list;
        })
        .catch((error) => {
          this.$message({
            message: '加载封禁列表失败: ' + (error && error.message ? error.message : '未知错误'),
            type: 'error'
          });
        })
        .finally(() => {
          this.blacklistDialog.loading = false;
        });
    },
    loadCaptchaBlockList() {
      this.blacklistDialog.captchaLoading = true;
      this.$http.get(this.$constant.baseURL + '/captcha/getBlockedIps', {}, true)
        .then((res) => {
          this.blacklistDialog.captchaList = (res && res.data) || [];
          this.blacklistDialog.captchaLoaded = true;
        })
        .catch((error) => {
          this.$message({
            message: '加载验证码封禁列表失败: ' + (error && error.message ? error.message : '未知错误'),
            type: 'error'
          });
        })
        .finally(() => {
          this.blacklistDialog.captchaLoading = false;
        });
    },
    confirmCaptchaUnblock(row) {
      const ip = (row && row.ip) || '';
      if (!ip) {
        this.$message.warning('IP 不存在');
        return;
      }
      this.$confirm(
        `确定要解除对 IP "${ip}" 的验证码封禁吗？解除后该 IP 可立即继续验证码流程。`,
        '解除验证码封禁',
        {
          confirmButtonText: '确定解除',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        this.blacklistDialog.captchaUnblockLoading = ip;
        this.$http.post(
          this.$constant.baseURL + '/captcha/unblockIp',
          { ip },
          true
        ).then(() => {
          this.$message({
            message: `已解除 IP ${ip} 的验证码封禁`,
            type: 'success'
          });
          this.loadCaptchaBlockList();
        }).catch((error) => {
          this.$message({
            message: '解除验证码封禁失败: ' + (error && error.message ? error.message : '未知错误'),
            type: 'error'
          });
        }).finally(() => {
          this.blacklistDialog.captchaUnblockLoading = '';
        });
      }).catch(() => {
        // 取消
      });
    },
    getCaptchaTimeTagType(minutes) {
      if (minutes > 20) return 'danger';
      if (minutes > 10) return 'warning';
      return 'info';
    },
    confirmUnblock(row) {
      const ip = (row && row.ip) || '';
      if (!ip) {
        this.$message.warning('IP 不存在');
        return;
      }
      this.$confirm(
        `确定要解除对 IP "${ip}" 的封禁吗？解除后该 IP 立即恢复访问。`,
        '解除封禁',
        {
          confirmButtonText: '确定解除',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        this.blacklistDialog.unblockLoading = ip;
        this.$http.post(
          this.$constant.baseURL + '/admin/security/unblockIp',
          { ip },
          true
        ).then((res) => {
          const data = (res && res.data) || {};
          if (data.success) {
            this.$message({
              message: `已解除 IP ${ip} 的封禁`,
              type: 'success'
            });
            this.loadBlacklist();
          } else {
            this.$message({
              message: '解除失败，请重试',
              type: 'error'
            });
          }
        }).catch((error) => {
          this.$message({
            message: '解除封禁失败: ' + (error && error.message ? error.message : '未知错误'),
            type: 'error'
          });
        }).finally(() => {
          this.blacklistDialog.unblockLoading = '';
        });
      }).catch(() => {
        // 取消
      });
    },
    formatTtl(row) {
      if (!row) {
        return '-';
      }
      const ttl = Number(row.ttl);
      if (ttl === -1) {
        return '永久';
      }
      if (ttl === -2 || ttl <= 0) {
        return '已过期';
      }
      const days = Math.floor(ttl / 86400);
      const hours = Math.floor((ttl % 86400) / 3600);
      const minutes = Math.floor((ttl % 3600) / 60);
      if (days > 0) {
        return `${days}天 ${hours}时`;
      }
      if (hours > 0) {
        return `${hours}时 ${minutes}分`;
      }
      return `${minutes}分`;
    },
    formatLogTime(dateTime) {
      if (!dateTime) return '-';
      let date = new Date(dateTime);
      if (isNaN(date.getTime()) && typeof dateTime === 'string') {
        date = new Date(dateTime.replace(/-/g, '/'));
      }
      if (isNaN(date.getTime())) {
        return dateTime;
      }
      const year = date.getFullYear();
      const month = date.getMonth() + 1;
      const day = date.getDate();
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      return `${year}年${month}月${day}日 ${hours}:${minutes}`;
    }
  }
};
</script>

<style scoped>
.system-log-page {
  width: 100%;
}

.log-filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.log-filter-bar > * {
  flex: 0 0 auto;
}

.log-filter-bar > .mrb10 {
  margin-right: 0 !important;
}

.log-filter-bar >>> .el-button + .el-button {
  margin-left: 0;
}

.handle-select {
  width: 130px;
}

.handle-input {
  width: 160px;
}

.log-date-range {
  width: 360px;
}

/* 亮色模式显式恢复，避免从暗色切换后仍残留暗色样式 */
.system-log-page >>> .log-date-range.el-date-editor {
  background-color: #ffffff !important;
  border-color: #dcdfe6 !important;
}

.system-log-page >>> .log-date-range .el-range-input {
  background-color: #ffffff !important;
  color: #606266 !important;
}

.system-log-page >>> .log-date-range .el-range-input::placeholder,
.system-log-page >>> .log-date-range .el-range-separator,
.system-log-page >>> .log-date-range .el-range__icon,
.system-log-page >>> .log-date-range .el-range__close-icon {
  color: #c0c4cc !important;
}

body.dark-mode .system-log-page >>> .log-date-range.el-date-editor {
  background-color: #2d2d2d !important;
  border-color: #4a4a4a !important;
}

body.dark-mode .system-log-page >>> .log-date-range .el-range-input {
  background-color: #2d2d2d !important;
  color: #e0e0e0 !important;
}

body.dark-mode .system-log-page >>> .log-date-range .el-range-input::placeholder,
body.dark-mode .system-log-page >>> .log-date-range .el-range-separator,
body.dark-mode .system-log-page >>> .log-date-range .el-range__icon,
body.dark-mode .system-log-page >>> .log-date-range .el-range__close-icon {
  color: #8a8a8a !important;
}

.muted-text {
  color: #909399;
}

.ai-token-cell {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Consolas, monospace;
  font-size: 12px;
  color: #67c23a;
}

.block-ip-btn,
::v-deep .block-ip-btn span,
::v-deep .block-ip-btn i {
  color: #f56c6c !important;
}

.block-ip-btn:hover,
.block-ip-btn:focus,
::v-deep .block-ip-btn:hover span,
::v-deep .block-ip-btn:hover i,
::v-deep .block-ip-btn:focus span,
::v-deep .block-ip-btn:focus i {
  color: #f78989 !important;
}

.copy-btn,
::v-deep .copy-btn span,
::v-deep .copy-btn i {
  color: #67c23a !important;
}

.copy-btn:hover,
.copy-btn:focus,
::v-deep .copy-btn:hover span,
::v-deep .copy-btn:hover i,
::v-deep .copy-btn:focus span,
::v-deep .copy-btn:focus i {
  color: #85ce61 !important;
}

.pagination {
  margin: 20px 0;
  text-align: right;
}

.detail-dialog {
  color: #303133;
}

.detail-title {
  margin: 16px 0 8px;
  font-weight: 600;
}

.detail-json {
  min-height: 120px;
  max-height: 300px;
  overflow: auto;
  padding: 12px;
  margin: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #f7f8fa;
  color: #303133;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

@media screen and (max-width: 768px) {
  .handle-select,
  .handle-input,
  .log-date-range {
    width: 100%;
  }

  ::v-deep .el-dialog {
    width: 95% !important;
  }

  .btn-text {
    display: none;
  }
}

/* ===== 拉黑 IP 弹窗 ===== */
.block-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.block-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.block-label {
  flex: 0 0 70px;
  text-align: right;
  color: #606266;
  font-size: 14px;
  line-height: 32px;
}

.block-input {
  flex: 1;
}

.block-tip {
  margin-top: 4px;
  padding: 10px 12px;
  background: #fdf6ec;
  border-left: 3px solid #e6a23c;
  color: #b88230;
  font-size: 12px;
  line-height: 1.6;
  border-radius: 4px;
}

.block-tip i {
  margin-right: 4px;
}

/* ===== 封禁列表弹窗 ===== */
.blacklist-page {
  min-height: 200px;
  padding: 0 8px;
}

/* tab 标签左右留白，避免紧贴边缘 */
.blacklist-page ::v-deep .el-tabs__header {
  margin-bottom: 16px;
}

.blacklist-page ::v-deep #tab-security {
  padding-left: 20px !important;
}

.blacklist-page ::v-deep #tab-captcha {
  padding-right: 20px !important;
}

body.dark-mode .blacklist-page ::v-deep .el-tabs__item.is-active {
  background-color: transparent !important;
}

body.dark-mode .blacklist-page ::v-deep .el-tabs__header {
  background-color: transparent !important;
}

.blacklist-page ::v-deep .el-tabs__nav-wrap::after {
  background-color: #ebeef5;
}

.blacklist-page ::v-deep .el-tabs__item {
  font-weight: 500;
}

.blacklist-tab-body {
  min-height: 200px;
  padding: 0 8px;
}

.blacklist-hint {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f4f6fa;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}

.blacklist-hint i {
  color: #409eff;
  margin-right: 4px;
}

.blacklist-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.blacklist-search {
  width: 280px;
}

.blacklist-count {
  color: #909399;
  font-size: 13px;
  margin-left: auto;
}

.unblock-btn {
  color: #67c23a;
}

.unblock-btn:hover,
.unblock-btn:focus {
  color: #85ce61;
}

body.dark-mode .block-tip {
  background: #3a2f1a !important;
  border-left-color: #e6a23c !important;
  color: #e6a23c !important;
}
</style>
