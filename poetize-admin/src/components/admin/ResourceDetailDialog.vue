<template>
  <el-dialog
    title="资源详情与副本管理"
    :visible.sync="visible"
    width="920px"
    custom-class="centered-dialog"
    :append-to-body="true"
    :close-on-click-modal="false"
    :before-close="handleClose">
    <div v-loading="loading" class="resource-detail">
      <template v-if="detail">
        <!-- 基本信息区 -->
        <div class="detail-section">
          <div class="detail-section__title">稳定地址与内容身份</div>
          <div class="detail-grid">
            <div class="detail-cell detail-cell--full">
              <label>稳定 URL</label>
              <div class="detail-cell__value">
                <code>{{ detail.stableUrl || '—' }}</code>
                <el-button
                  type="text"
                  icon="el-icon-document-copy"
                  @click="copyStableUrl">
                  复制
                </el-button>
              </div>
            </div>
            <div class="detail-cell">
              <label>资源 ID</label>
              <span>{{ detail.resource.id || '—' }}</span>
            </div>
            <div class="detail-cell">
              <label>publicId</label>
              <span>{{ detail.resource.publicId || '—' }}</span>
            </div>
            <div class="detail-cell">
              <label>资源类型</label>
              <span>{{ detail.resource.type || '—' }}</span>
            </div>
            <div class="detail-cell">
              <label>原始文件名</label>
              <span>{{ detail.resource.originalName || '—' }}</span>
            </div>
            <div class="detail-cell">
              <label>启用状态</label>
              <el-tag :type="detail.resource.status ? 'success' : 'info'" size="mini">
                {{ detail.resource.status ? '启用' : '停用' }}
              </el-tag>
            </div>
            <div class="detail-cell">
              <label>内容状态</label>
              <el-tag :type="getContentStateType(detail.resource.contentState)" size="mini">
                {{ getContentStateLabel(detail.resource.contentState) }}
              </el-tag>
            </div>
            <div class="detail-cell">
              <label>业务引用</label>
              <span :class="{ 'is-warn': detail.referenceCount > 0 }">
                {{ detail.referenceCount }} 处
              </span>
            </div>
            <div class="detail-cell">
              <label>locationVersion</label>
              <span>{{ detail.resource.locationVersion != null ? detail.resource.locationVersion : '—' }}</span>
            </div>
            <div class="detail-cell">
              <label>活动副本 ID</label>
              <span>{{ detail.resource.activeLocationId != null ? detail.resource.activeLocationId : '—' }}</span>
            </div>
            <div class="detail-cell detail-cell--full">
              <label>SHA-256</label>
              <div class="detail-cell__value">
                <code class="hash-code">{{ detail.resource.resourceHash || '未登记' }}</code>
                <span class="hash-meta" v-if="detail.resource.resourceHash">
                  来源：{{ detail.resource.hashSource || '—' }}
                  · 验证：{{ formatTime(detail.resource.hashVerifiedAt) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 副本列表区 -->
        <div class="detail-section">
          <div class="detail-section__title">
            物理副本（{{ (detail.locations || []).length }}）
            <el-tooltip
              content="活动副本删除前必须先激活一个已验证的 RETAINED 副本作为替代；DELETING 状态可恢复续删"
              placement="top">
              <i class="el-icon-info detail-section__hint"></i>
            </el-tooltip>
          </div>
          <el-table
            :data="detail.locations || []"
            border
            size="mini"
            max-height="320"
            class="detail-table">
            <el-table-column prop="id" label="副本 ID" width="80" align="center"></el-table-column>
            <el-table-column label="存储" width="92" align="center">
              <template slot-scope="scope">
                {{ getStoreLabel(scope.row.storeType) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96" align="center">
              <template slot-scope="scope">
                <el-tag :type="getLocationStatusType(scope.row.status)" size="mini">
                  {{ getLocationStatusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="字节数" width="92" align="right">
              <template slot-scope="scope">
                {{ formatBytes(scope.row.size) }}
              </template>
            </el-table-column>
            <el-table-column label="副本 SHA-256" min-width="180" show-overflow-tooltip>
              <template slot-scope="scope">
                <code class="hash-code">{{ scope.row.contentHash || '—' }}</code>
                <i v-if="!scope.row.contentHash" class="el-icon-warning" title="未登记哈希"></i>
              </template>
            </el-table-column>
            <el-table-column label="回读验证" width="150" align="center">
              <template slot-scope="scope">
                {{ formatTime(scope.row.verifiedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template slot-scope="scope">
                <el-button
                  v-if="canActivate(scope.row)"
                  type="text"
                  size="mini"
                  :disabled="operating"
                  @click="activateLocation(scope.row)">
                  激活为活动
                </el-button>
                <el-button
                  v-if="canDelete(scope.row)"
                  type="text"
                  size="mini"
                  class="is-danger"
                  :disabled="operating"
                  @click="deleteLocation(scope.row)">
                  删除副本
                </el-button>
                <el-button
                  v-if="canResume(scope.row)"
                  type="text"
                  size="mini"
                  :disabled="operating"
                  @click="resumeDeleteLocation(scope.row)">
                  恢复删除
                </el-button>
                <span v-if="!canActivate(scope.row) && !canDelete(scope.row) && !canResume(scope.row)" class="detail-table__none">
                  —
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 别名列表区 -->
        <div class="detail-section" v-if="(detail.aliases || []).length">
          <el-collapse>
            <el-collapse-item :title="'历史别名（' + (detail.aliases || []).length + '）'">
              <el-table
                :data="detail.aliases || []"
                border
                size="mini"
                max-height="200">
                <el-table-column prop="id" label="别名 ID" width="80" align="center"></el-table-column>
                <el-table-column prop="aliasUrl" label="别名 URL" min-width="260" show-overflow-tooltip></el-table-column>
                <el-table-column prop="sourceType" label="来源" width="120" align="center"></el-table-column>
                <el-table-column label="状态" width="80" align="center">
                  <template slot-scope="scope">
                    <el-tag :type="scope.row.status ? 'success' : 'info'" size="mini">
                      {{ scope.row.status ? '活动' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="登记时间" width="150" align="center">
                  <template slot-scope="scope">
                    {{ formatTime(scope.row.createTime) }}
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </template>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button :disabled="operating" @click="handleClose">关闭</el-button>
      <el-button :disabled="loading" @click="loadDetail">刷新</el-button>
    </span>

    <!-- 替代副本选择子对话框 -->
    <el-dialog
      title="删除活动副本前选择替代副本"
      :visible.sync="replacementVisible"
      width="520px"
      custom-class="centered-dialog"
      :append-to-body="true"
      :close-on-click-modal="false">
      <div v-if="pendingDeleteActiveLocation">
        <el-alert
          type="warning"
          :closable="false"
          show-icon>
          将删除当前活动副本 #{{ pendingDeleteActiveLocation.id }}（{{ getStoreLabel(pendingDeleteActiveLocation.storeType) }}），
          必须先指定一个已完整回读验证的 RETAINED 副本接管活动指针。
        </el-alert>
        <el-radio-group v-model="selectedReplacementId" class="replacement-list">
          <el-radio
            v-for="loc in retainedCandidates"
            :key="loc.id"
            :label="loc.id"
            class="replacement-item">
            <span>副本 #{{ loc.id }} · {{ getStoreLabel(loc.storeType) }} · {{ formatBytes(loc.size) }}</span>
            <code class="hash-code">{{ loc.contentHash || '—' }}</code>
          </el-radio>
        </el-radio-group>
        <div v-if="!retainedCandidates.length" class="replacement-empty">
          没有可用的 RETAINED 副本。活动副本删除前必须存在已验证的替代副本，请先迁移一个副本到其他存储再重试。
        </div>
      </div>
      <span slot="footer">
        <el-button :disabled="deleting" @click="replacementVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="deleting"
          :disabled="!selectedReplacementId || !retainedCandidates.length"
          @click="confirmDeleteActiveWithReplacement">
          确认删除
        </el-button>
      </span>
    </el-dialog>
  </el-dialog>
</template>

<script>
export default {
  name: 'ResourceDetailDialog',
  data() {
    return {
      visible: false,
      loading: false,
      operating: false,
      deleting: false,
      resourceId: null,
      detail: null,
      replacementVisible: false,
      pendingDeleteActiveLocation: null,
      selectedReplacementId: null
    };
  },
  computed: {
    retainedCandidates() {
      const list = (this.detail && this.detail.locations) || [];
      return list.filter((loc) => loc && loc.status === 'RETAINED');
    }
  },
  methods: {
    open(resourceId) {
      if (!resourceId) {
        this.$message({ message: '资源 ID 缺失', type: 'warning' });
        return;
      }
      this.resourceId = resourceId;
      this.detail = null;
      this.visible = true;
      this.loadDetail();
    },
    loadDetail() {
      if (!this.resourceId) {
        return;
      }
      this.loading = true;
      this.$http.get(this.$constant.baseURL + '/resource/location/' + this.resourceId + '/detail', {}, true)
        .then((res) => {
          this.detail = (res && res.data) || null;
        })
        .catch((error) => {
          this.$message({ message: error.message || '加载资源详情失败', type: 'error' });
        })
        .finally(() => {
          this.loading = false;
        });
    },
    handleClose(done) {
      if (this.operating || this.deleting) {
        return;
      }
      this.visible = false;
      if (typeof done === 'function') {
        done();
      }
    },
    copyStableUrl() {
      const url = this.detail && this.detail.stableUrl;
      if (!url) {
        return;
      }
      const fullUrl = window.location.origin + url;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(fullUrl).then(() => {
          this.$message({ message: '已复制稳定 URL', type: 'success' });
        }).catch(() => {
          this.fallbackCopy(fullUrl);
        });
      } else {
        this.fallbackCopy(fullUrl);
      }
    },
    fallbackCopy(text) {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      try {
        document.execCommand('copy');
        this.$message({ message: '已复制稳定 URL', type: 'success' });
      } catch (e) {
        this.$message({ message: '复制失败，请手动复制：' + text, type: 'warning' });
      }
      document.body.removeChild(textarea);
    },
    canActivate(loc) {
      return !!loc && loc.status === 'RETAINED';
    },
    canDelete(loc) {
      return !!loc && (loc.status === 'ACTIVE' || loc.status === 'RETAINED');
    },
    canResume(loc) {
      return !!loc && loc.status === 'DELETING';
    },
    activateLocation(loc) {
      if (!this.detail || !this.detail.resource) {
        return;
      }
      const activeLocationId = this.detail.resource.activeLocationId;
      const message = '确认把副本 #' + loc.id + '（' + this.getStoreLabel(loc.storeType) +
        '）提升为活动副本？当前活动副本 #' + (activeLocationId != null ? activeLocationId : '—') +
        ' 将降级为保留副本。';
      this.$confirm(message, '切换活动副本', {
        confirmButtonText: '确认激活',
        cancelButtonText: '取消',
        type: 'warning',
        center: true,
        customClass: 'mobile-responsive-confirm'
      }).then(() => {
        this.operating = true;
        this.$http.post(
          this.$constant.baseURL + '/resource/location/' + this.resourceId + '/' + loc.id + '/activate',
          { expectedActiveLocationId: activeLocationId },
          true
        ).then(() => {
          this.$message({ message: '活动副本已切换', type: 'success' });
          this.loadDetail();
          this.$emit('changed');
        }).catch((error) => {
          this.$message({ message: error.message || '激活失败', type: 'error' });
        }).finally(() => {
          this.operating = false;
        });
      }).catch(() => {});
    },
    deleteLocation(loc) {
      if (!this.detail || !this.detail.resource) {
        return;
      }
      if (loc.status === 'ACTIVE') {
        // 活动副本删除前必须选择替代副本
        this.pendingDeleteActiveLocation = loc;
        this.selectedReplacementId = this.retainedCandidates.length === 1
          ? this.retainedCandidates[0].id
          : null;
        this.replacementVisible = true;
        return;
      }
      // 非活动副本可直接删除（replacementLocationId 留空）
      this.confirmDeleteLocation(loc, null);
    },
    confirmDeleteActiveWithReplacement() {
      const loc = this.pendingDeleteActiveLocation;
      const replacementId = this.selectedReplacementId;
      if (!loc || !replacementId) {
        return;
      }
      this.replacementVisible = false;
      this.confirmDeleteLocation(loc, replacementId);
      this.pendingDeleteActiveLocation = null;
      this.selectedReplacementId = null;
    },
    confirmDeleteLocation(loc, replacementLocationId) {
      const isActive = loc.status === 'ACTIVE';
      const message = '确认删除副本 #' + loc.id + '（' + this.getStoreLabel(loc.storeType) + '）？' +
        (isActive
          ? '将先激活替代副本 #' + replacementLocationId + '，再删除当前活动副本。'
          : '该副本为保留副本，删除后不可恢复，删除失败时会保留记录并尝试严格回读恢复。');
      this.$confirm(message, '删除物理副本', {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        center: true,
        customClass: 'mobile-responsive-confirm'
      }).then(() => {
        this.deleting = true;
        const payload = {
          resourceId: this.resourceId,
          locationId: loc.id,
          replacementLocationId: replacementLocationId
        };
        this.$http.post(
          this.$constant.baseURL + '/resource/location/delete',
          payload,
          true
        ).then((res) => {
          const result = (res && res.data) || null;
          if (result && result.status === 'DELETING') {
            this.$message({
              message: result.message || '删除未决，已保持 DELETING 状态等待回读确认',
              type: 'warning'
            });
          } else if (result && result.recordMarkedRemoved) {
            this.$message({
              message: result.physicalDeleted ? '副本已删除' : '副本记录已移除',
              type: 'success'
            });
          } else {
            this.$message({
              message: (result && result.message) || '删除失败',
              type: 'error'
            });
          }
          this.loadDetail();
          this.$emit('changed');
        }).catch((error) => {
          this.$message({ message: error.message || '删除失败', type: 'error' });
        }).finally(() => {
          this.deleting = false;
        });
      }).catch(() => {});
    },
    resumeDeleteLocation(loc) {
      this.$confirm(
        '确认恢复副本 #' + loc.id + ' 的删除流程？租约过期后会继续执行物理删除并严格回读校验。',
        '恢复副本删除',
        {
          confirmButtonText: '确认恢复',
          cancelButtonText: '取消',
          type: 'warning',
          center: true,
          customClass: 'mobile-responsive-confirm'
        }
      ).then(() => {
        this.operating = true;
        this.$http.post(
          this.$constant.baseURL + '/resource/location/' + this.resourceId + '/' + loc.id + '/resume-delete',
          {},
          true
        ).then((res) => {
          const result = (res && res.data) || null;
          if (result && result.status === 'DELETING') {
            this.$message({
              message: result.message || '仍在 DELETING 状态，等待回读确认',
              type: 'warning'
            });
          } else if (result && result.recordMarkedRemoved) {
            this.$message({
              message: result.physicalDeleted ? '副本已删除' : '副本记录已移除',
              type: 'success'
            });
          } else {
            this.$message({
              message: (result && result.message) || '恢复删除失败',
              type: 'error'
            });
          }
          this.loadDetail();
          this.$emit('changed');
        }).catch((error) => {
          this.$message({ message: error.message || '恢复删除失败', type: 'error' });
        }).finally(() => {
          this.operating = false;
        });
      }).catch(() => {});
    },
    getContentStateType(state) {
      if (!state || state === 'ACTIVE') {
        return 'success';
      }
      if (state === 'REPLACEMENT_PENDING') {
        return 'warning';
      }
      if (state === 'DELETION_PENDING') {
        return 'danger';
      }
      return 'info';
    },
    getContentStateLabel(state) {
      if (!state || state === 'ACTIVE') {
        return '正常';
      }
      if (state === 'REPLACEMENT_PENDING') {
        return '替换待决';
      }
      if (state === 'DELETION_PENDING') {
        return '删除待决';
      }
      return state;
    },
    getLocationStatusType(status) {
      const map = {
        STAGED: 'info',
        ACTIVE: 'success',
        RETAINED: 'primary',
        STALE: 'info',
        DELETING: 'warning',
        DELETED: 'info',
        MISSING: 'danger',
        DETACHED: 'info'
      };
      return map[status] || 'info';
    },
    getLocationStatusLabel(status) {
      const map = {
        STAGED: '暂存',
        ACTIVE: '活动',
        RETAINED: '保留',
        STALE: '过期',
        DELETING: '删除中',
        DELETED: '已删除',
        MISSING: '缺失',
        DETACHED: '已分离'
      };
      return map[status] || status || '—';
    },
    getStoreLabel(storeType) {
      const map = {
        local: '服务器',
        qiniu: '七牛',
        lsky: '兰空',
        easyimage: 'EasyImage'
      };
      return map[storeType] || storeType || '—';
    },
    formatBytes(value) {
      const bytes = Number(value) || 0;
      if (!bytes) {
        return '0 B';
      }
      if (bytes < 1024) {
        return bytes + ' B';
      }
      if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(1) + ' KB';
      }
      if (bytes < 1024 * 1024 * 1024) {
        return (bytes / 1024 / 1024).toFixed(1) + ' MB';
      }
      return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    },
    formatTime(value) {
      if (!value) {
        return '—';
      }
      const date = new Date(value);
      if (isNaN(date.getTime())) {
        return String(value);
      }
      const pad = (n) => (n < 10 ? '0' + n : '' + n);
      return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) +
        ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    }
  }
};
</script>

<style scoped>
.resource-detail {
  min-height: 120px;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-section__hint {
  color: #909399;
  font-size: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 18px;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
}

.detail-cell {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  font-size: 13px;
}

.detail-cell--full {
  grid-column: 1 / -1;
  flex-wrap: wrap;
}

.detail-cell label {
  color: #909399;
  white-space: nowrap;
  flex-shrink: 0;
}

.detail-cell span,
.detail-cell code {
  color: #303133;
  word-break: break-all;
}

.detail-cell__value {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.hash-code {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: #606266;
  background: #f4f6f8;
  padding: 1px 5px;
  border-radius: 3px;
  word-break: break-all;
}

.hash-meta {
  color: #909399;
  font-size: 12px;
}

.is-warn {
  color: #e6a23c;
  font-weight: 600;
}

.detail-table {
  width: 100%;
}

.detail-table__none {
  color: #c0c4cc;
}

.detail-table .is-danger {
  color: #f56c6c;
}

.replacement-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.replacement-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
}

.replacement-item span {
  margin-right: 8px;
  color: #303133;
}

.replacement-empty {
  margin-top: 16px;
  padding: 14px;
  border: 1px dashed #e6a23c;
  border-radius: 6px;
  background: #fdf6ec;
  color: #e6a23c;
  font-size: 13px;
  line-height: 1.6;
}

@media screen and (max-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
