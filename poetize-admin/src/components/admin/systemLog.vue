<template>
  <div class="system-log-page">
    <div class="handle-box log-filter-bar">
      <el-select v-model="filters.logType" clearable placeholder="日志类型" class="handle-select mrb10">
        <el-option label="登录日志" value="LOGIN"></el-option>
        <el-option label="安全日志" value="SECURITY"></el-option>
        <el-option label="操作日志" value="OPERATION"></el-option>
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
    </div>

    <el-table
      v-loading="loading"
      :data="logs"
      border
      class="table"
      header-cell-class-name="table-header"
      empty-text="暂无系统日志">
      <el-table-column prop="createTime" label="时间" width="165" align="center"></el-table-column>
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
      <el-table-column label="操作者/登录账号" min-width="160" show-overflow-tooltip>
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
      <el-table-column label="操作" width="82" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
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
          <el-descriptions-item label="时间">{{ currentLog.createTime || '-' }}</el-descriptions-item>
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
          <el-descriptions-item label="User-Agent" :span="2">{{ currentLog.userAgent || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-title">操作详情</div>
        <pre class="detail-json" v-text="formatDetail(currentLog.detail)"></pre>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button type="primary" @click="detailVisible = false">关 闭</el-button>
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
      typeLabels: {
        LOGIN: '登录日志',
        SECURITY: '安全日志',
        OPERATION: '操作日志'
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
        ARTICLE_SITEMAP_UPDATE: '更新文章站点地图',
        COMMENT_DELETE: '删除评论',
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
        WEIYAN_DELETE: '删除微言'
      }
    };
  },
  created() {
    this.getLogs();
  },
  methods: {
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

body.dark-mode .system-log-page >>> .log-date-range.el-date-editor {
  background-color: #2d2d2d !important;
  border-color: #4a4a4a !important;
}

body.dark-mode .system-log-page >>> .log-date-range .el-range-input {
  background-color: transparent !important;
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
}
</style>
