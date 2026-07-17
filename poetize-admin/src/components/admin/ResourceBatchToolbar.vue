<template>
  <div class="resource-batch">
    <div class="resource-batch__bar">
      <div class="resource-batch__summary">
        <i class="el-icon-finished resource-batch__icon"></i>
        <div>
          <strong>已选 {{ selectedCount }} 项</strong>
          <span>合计 {{ formatBytes(selectedBytes) }}</span>
        </div>
      </div>
      <div class="resource-batch__actions">
        <el-button
          type="text"
          :disabled="selectedCount === 0"
          @click="$emit('clear')">
          清空选择
        </el-button>
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          :disabled="selectedCount === 0"
          @click="openDelete()">
          批量删除
        </el-button>
        <el-tooltip
          :disabled="canMigrate"
          content="当前搜索结果无法准确映射为后端筛选范围，请先勾选资源或清除全局搜索"
          placement="top">
          <span>
            <el-button
              type="primary"
              icon="el-icon-upload2"
              :disabled="!canMigrate"
              @click="$emit('migrate')">
              迁移存储位置
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <el-dialog
      title="批量删除安全预检"
      :visible.sync="deleteDialogVisible"
      width="760px"
      custom-class="resource-batch-delete-dialog"
      :append-to-body="true"
      :close-on-click-modal="false"
      :before-close="handleDeleteDialogClose">
      <div v-loading="previewLoading || deleting">
        <el-alert
          title="默认只删除通过引用检查且存储平台确认可删除的资源。下列例外项必须单独授权。"
          type="warning"
          :closable="false"
          show-icon>
        </el-alert>

        <div class="resource-batch__overrides">
          <label class="resource-batch__override">
            <el-checkbox
              v-model="deleteOptions.forceReferenced"
              :disabled="previewLoading || deleting"
              @change="handleDeleteOptionChange">
              强制删除仍被业务数据引用的资源
            </el-checkbox>
            <span>可能导致文章、评论、用户资料或站点配置中的链接失效。</span>
          </label>
          <label class="resource-batch__override">
            <el-checkbox
              v-model="deleteOptions.removeMissingRecords"
              :disabled="previewLoading || deleting"
              @change="handleDeleteOptionChange">
              物理文件缺失时仅移除资源记录
            </el-checkbox>
            <span>只清理已确认不存在的文件记录。</span>
          </label>
          <label class="resource-batch__override">
            <el-checkbox
              v-model="deleteOptions.removeUnsupportedRecords"
              :disabled="previewLoading || deleting"
              @change="handleDeleteOptionChange">
              存储平台不支持删除时仅移除资源记录
            </el-checkbox>
            <span>远端文件会继续保留，管理端不再记录它。</span>
          </label>
        </div>

        <div v-if="deleteResult" class="resource-batch__stats">
          <div>
            <strong>{{ deleteResult.requestedCount || 0 }}</strong>
            <span>已检查</span>
          </div>
          <div class="is-ready">
            <strong>{{ deleteResult.readyCount || 0 }}</strong>
            <span>可执行</span>
          </div>
          <div v-if="deleteResult.deletedCount" class="is-success">
            <strong>{{ deleteResult.deletedCount }}</strong>
            <span>已删除</span>
          </div>
          <div v-if="deleteResult.blockedCount" class="is-warning">
            <strong>{{ deleteResult.blockedCount }}</strong>
            <span>已阻止</span>
          </div>
          <div v-if="deleteResult.failedCount" class="is-danger">
            <strong>{{ deleteResult.failedCount }}</strong>
            <span>失败</span>
          </div>
        </div>

        <el-table
          v-if="deleteResult"
          :data="deleteResult.items || []"
          border
          size="mini"
          max-height="360"
          class="resource-batch__table">
          <el-table-column prop="resourceId" label="ID" width="64" align="center"></el-table-column>
          <el-table-column prop="path" label="资源路径" min-width="210" show-overflow-tooltip></el-table-column>
          <el-table-column label="状态" width="104" align="center">
            <template slot-scope="scope">
              <el-tag :type="getDeleteStatusType(scope.row)" size="mini">
                {{ getDeleteStatusLabel(scope.row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="引用" width="66" align="center">
            <template slot-scope="scope">
              {{ scope.row.referenceCount || 0 }}
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="190" show-overflow-tooltip></el-table-column>
        </el-table>
      </div>

      <span slot="footer" class="dialog-footer">
        <el-button :disabled="deleting" @click="deleteDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="previewLoading"
          :disabled="deleting"
          @click="previewDelete">
          重新预检
        </el-button>
        <el-button
          type="danger"
          :loading="deleting"
          :disabled="previewLoading || !deleteResult || !deleteResult.readyCount"
          @click="executeDelete">
          删除可执行项
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
const MAX_BATCH_SIZE = 500;

export default {
  name: 'ResourceBatchToolbar',
  props: {
    selectedResources: {
      type: Array,
      default: () => []
    },
    filterScopeAvailable: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      deleteDialogVisible: false,
      previewLoading: false,
      deleting: false,
      activeResources: [],
      deleteResult: null,
      previewSequence: 0,
      deleteOptions: {
        forceReferenced: false,
        removeMissingRecords: false,
        removeUnsupportedRecords: false
      }
    };
  },
  computed: {
    selectedCount() {
      return this.selectedResources.length;
    },
    selectedBytes() {
      return this.selectedResources.reduce((total, resource) => {
        const size = Number(resource && resource.size);
        return total + (Number.isFinite(size) && size > 0 ? size : 0);
      }, 0);
    },
    canMigrate() {
      return this.selectedCount > 0 || this.filterScopeAvailable;
    }
  },
  methods: {
    openDelete(resources) {
      const candidates = Array.isArray(resources) && resources.length
        ? resources
        : this.selectedResources;
      const uniqueResources = Array.from(new Map(
        candidates
          .filter((resource) => resource && resource.id != null && resource.path)
          .map((resource) => [String(resource.id), resource])
      ).values());

      if (!uniqueResources.length) {
        this.$message({ message: '请先勾选要删除的资源', type: 'warning' });
        return;
      }
      if (uniqueResources.length > MAX_BATCH_SIZE) {
        this.$message({
          message: '单次最多删除 ' + MAX_BATCH_SIZE + ' 个资源，请减少选择后重试',
          type: 'warning'
        });
        return;
      }

      this.activeResources = uniqueResources;
      this.deleteResult = null;
      this.deleteOptions = {
        forceReferenced: false,
        removeMissingRecords: false,
        removeUnsupportedRecords: false
      };
      this.deleteDialogVisible = true;
      this.$nextTick(() => this.previewDelete());
    },
    buildDeletePayload() {
      return {
        targets: this.activeResources.map((resource) => ({
          resourceId: resource.id,
          expectedPath: resource.path
        })),
        forceReferenced: this.deleteOptions.forceReferenced,
        removeMissingRecords: this.deleteOptions.removeMissingRecords,
        removeUnsupportedRecords: this.deleteOptions.removeUnsupportedRecords
      };
    },
    async previewDelete() {
      if (!this.activeResources.length || this.deleting) {
        return null;
      }
      const sequence = ++this.previewSequence;
      this.previewLoading = true;
      try {
        const response = await this.$http.post(
          this.$constant.baseURL + '/resource/batchDelete/preview',
          this.buildDeletePayload(),
          true
        );
        if (sequence === this.previewSequence) {
          this.deleteResult = response && response.data ? response.data : null;
        }
        return this.deleteResult;
      } catch (error) {
        if (sequence === this.previewSequence) {
          this.$message({
            message: error.message || '批量删除预检失败',
            type: 'error'
          });
        }
        return null;
      } finally {
        if (sequence === this.previewSequence) {
          this.previewLoading = false;
        }
      }
    },
    handleDeleteOptionChange() {
      this.deleteResult = null;
      this.previewDelete();
    },
    async executeDelete() {
      const latestPreview = await this.previewDelete();
      if (!latestPreview || !latestPreview.readyCount) {
        this.$message({ message: '当前没有可执行的删除项', type: 'warning' });
        return;
      }

      const overrideMessages = [];
      if (this.deleteOptions.forceReferenced) {
        overrideMessages.push('允许删除仍被业务引用的资源');
      }
      if (this.deleteOptions.removeMissingRecords) {
        overrideMessages.push('允许移除物理文件缺失的记录');
      }
      if (this.deleteOptions.removeUnsupportedRecords) {
        overrideMessages.push('允许仅移除不支持远端删除的资源记录');
      }
      const message = '将删除 ' + latestPreview.readyCount + ' 个可执行项，' +
        (latestPreview.blockedCount ? '其余 ' + latestPreview.blockedCount + ' 个阻止项保持不变。' : '没有阻止项。') +
        (overrideMessages.length ? '<br><br><strong>本次额外授权：</strong><br>' + overrideMessages.join('<br>') : '');

      try {
        await this.$confirm(message, this.deleteOptions.forceReferenced ? '高风险删除确认' : '删除确认', {
          confirmButtonText: '确认删除',
          cancelButtonText: '取消',
          type: 'warning',
          center: true,
          dangerouslyUseHTMLString: true,
          customClass: 'mobile-responsive-confirm'
        });
      } catch (error) {
        return;
      }

      this.deleting = true;
      try {
        const response = await this.$http.post(
          this.$constant.baseURL + '/resource/batchDelete',
          this.buildDeletePayload(),
          true
        );
        const result = response && response.data ? response.data : null;
        this.deleteResult = result;
        if (!result) {
          throw new Error('删除接口返回数据异常');
        }

        const messageType = result.failedCount > 0 || result.blockedCount > 0 ? 'warning' : 'success';
        this.$message({
          message: '已删除 ' + result.deletedCount + ' 项' +
            (result.blockedCount ? '，阻止 ' + result.blockedCount + ' 项' : '') +
            (result.failedCount ? '，失败 ' + result.failedCount + ' 项' : ''),
          type: messageType
        });
        this.$emit('deleted', result);
      } catch (error) {
        this.$message({
          message: error.message || '批量删除失败',
          type: 'error'
        });
      } finally {
        this.deleting = false;
      }
    },
    handleDeleteDialogClose(done) {
      if (!this.deleting) {
        done();
      }
    },
    getDeleteStatusLabel(item) {
      if (item && item.recordDeleted) {
        return item.physicalDeleted ? '已删除' : '已移除记录';
      }
      const status = item && item.status;
      const labels = {
        READY: '可安全删除',
        RECORD_ONLY: '仅删记录',
        REFERENCED: '仍被引用',
        UNSUPPORTED: '不支持删除',
        MISSING: '文件缺失',
        CHANGED: '记录已变化',
        FAILED: '删除失败'
      };
      return labels[status] || status || '未知';
    },
    getDeleteStatusType(item) {
      if (item && item.recordDeleted) {
        return 'success';
      }
      const status = item && item.status;
      if (status === 'READY') {
        return 'success';
      }
      if (status === 'RECORD_ONLY' || status === 'MISSING') {
        return 'warning';
      }
      if (status === 'REFERENCED' || status === 'UNSUPPORTED' || status === 'CHANGED' || status === 'FAILED') {
        return 'danger';
      }
      return 'info';
    },
    formatBytes(value) {
      const bytes = Number(value) || 0;
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
    }
  }
};
</script>

<style scoped>
.resource-batch__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 14px;
  padding: 12px 14px;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  background: #f4f8ff;
}

.resource-batch__summary {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  color: #303133;
}

.resource-batch__summary > div {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.resource-batch__summary span {
  color: #909399;
  font-size: 12px;
}

.resource-batch__icon {
  color: #409eff;
  font-size: 22px;
}

.resource-batch__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.resource-batch__actions .el-button + .el-button {
  margin-left: 0;
}

.resource-batch__overrides {
  display: grid;
  gap: 10px;
  margin: 16px 0;
}

.resource-batch__override {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
}

.resource-batch__override > span {
  padding-left: 24px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.resource-batch__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.resource-batch__stats > div {
  display: flex;
  align-items: baseline;
  gap: 5px;
  min-width: 82px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
}

.resource-batch__stats strong {
  color: #303133;
  font-size: 18px;
}

.resource-batch__stats span {
  font-size: 12px;
}

.resource-batch__stats .is-ready strong,
.resource-batch__stats .is-success strong {
  color: #67c23a;
}

.resource-batch__stats .is-warning strong {
  color: #e6a23c;
}

.resource-batch__stats .is-danger strong {
  color: #f56c6c;
}

.resource-batch__table {
  width: 100%;
}

@media screen and (max-width: 768px) {
  .resource-batch__bar {
    align-items: stretch;
    flex-direction: column;
  }

  .resource-batch__actions {
    justify-content: flex-start;
  }

  .resource-batch__actions .el-button,
  .resource-batch__actions > span {
    flex: 1 1 auto;
  }

  .resource-batch__actions > span .el-button {
    width: 100%;
  }
}
</style>