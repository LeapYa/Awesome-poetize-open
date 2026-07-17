<template>
  <div class="resource-migration">
    <div
      v-if="taskView"
      class="resource-migration__task-bar"
      :class="'is-' + taskStatus.toLowerCase()">
      <div class="resource-migration__task-main">
        <i :class="taskStatusIcon"></i>
        <div class="resource-migration__task-copy">
          <strong>{{ taskStatusLabel }}</strong>
          <span>
            {{ taskProcessedCount }} / {{ taskTotalCount }}
            · 成功 {{ taskSuccessCount }}
            · 跳过 {{ taskSkippedCount }}
            · 失败 {{ taskFailedCount }}
          </span>
        </div>
      </div>
      <div class="resource-migration__task-progress">
        <el-progress
          :percentage="taskProgressPercent"
          :status="taskProgressStatus"
          :show-text="false"
          :stroke-width="6">
        </el-progress>
      </div>
      <el-button type="text" @click="openTask">查看任务</el-button>
    </div>

    <el-dialog
      title="迁移存储位置"
      :visible.sync="dialogVisible"
      width="820px"
      custom-class="centered-dialog"
      :append-to-body="true"
      :close-on-click-modal="false"
      :before-close="handleDialogClose">
      <div v-if="panelMode === 'config'" v-loading="capabilitiesLoading || previewLoading || creating">
        <el-alert
          title="迁移会把资源复制到目标存储并切换为活动副本，源文件默认保留，完成后可单独清理。"
          type="info"
          :closable="false"
          show-icon>
        </el-alert>

        <section class="resource-migration__section">
          <h4>1. 选择迁移范围</h4>
          <el-radio-group v-model="form.scopeType" @change="handleConfigChange">
            <el-radio-button label="SELECTED" :disabled="selectedCount === 0">
              当前勾选（{{ selectedCount }} 项）
            </el-radio-button>
            <el-radio-button label="FILTER" :disabled="!filterScopeAvailable">
              当前筛选下全部资源
            </el-radio-button>
          </el-radio-group>
          <p class="resource-migration__hint">
            <template v-if="form.scopeType === 'SELECTED'">
              创建任务时会固化当前勾选资源的 ID 与路径快照。
            </template>
            <template v-else>
              筛选类型：{{ resourceTypeLabel || '全部资源' }}。创建任务后，后续筛选变化不会影响任务范围。
            </template>
          </p>
          <el-alert
            v-if="!filterScopeAvailable"
            title="全局关键词搜索仅在浏览器端过滤，不能作为“当前筛选全部”迁移范围；请明确勾选资源或先清除全局搜索。"
            type="warning"
            :closable="false"
            show-icon>
          </el-alert>
        </section>

        <section class="resource-migration__section">
          <h4>2. 选择目标存储</h4>
          <el-select
            v-model="form.targetStoreType"
            placeholder="请选择已启用且支持上传的存储"
            class="resource-migration__target"
            @change="handleConfigChange">
            <el-option
            v-for="capability in availableCapabilities"
            :key="capability.storeType"
            :label="getStoreLabel(capability.storeType)"
            :value="capability.storeType">
          </el-option>
        </el-select>
        <p class="resource-migration__hint" v-if="form.targetStoreType">
          选择“服务器本地”即把图床资源迁回本地，选择图床即把本地资源迁出。
        </p>

          <div v-if="selectedCapability" class="resource-migration__capability">
            <div class="resource-migration__capability-head">
              <strong>{{ getStoreLabel(selectedCapability.storeType) }}</strong>
              <el-tag type="success" size="mini">支持上传</el-tag>
              <el-tag :type="selectedCapability.verifySupported ? 'success' : 'warning'" size="mini">
                {{ selectedCapability.verifySupported ? '支持平台校验' : '通用远端校验' }}
              </el-tag>
              <el-tag :type="selectedCapability.deleteSupported ? 'success' : 'warning'" size="mini">
                {{ selectedCapability.deleteSupported ? '支持远端删除' : '不支持远端删除' }}
              </el-tag>
            </div>
            <dl>
              <div>
                <dt>单文件上限</dt>
                <dd>{{ formatLimit(selectedCapability.maxFileSize) }}</dd>
              </div>
              <div>
                <dt>支持类型</dt>
                <dd>{{ formatMimePrefixes(selectedCapability.acceptedMimePrefixes) }}</dd>
              </div>
            </dl>
          </div>

          <el-empty
            v-else-if="!capabilitiesLoading && availableCapabilities.length === 0"
            description="没有已启用且支持上传的目标存储">
          </el-empty>
        </section>

        <section class="resource-migration__section">
          <div class="resource-migration__section-title">
            <h4>3. 迁移预检</h4>
            <el-button
              type="primary"
              plain
              size="small"
              :loading="previewLoading"
              :disabled="!canPreview"
              @click="previewMigration">
              执行预检
            </el-button>
          </div>

          <div v-if="preview" class="resource-migration__preview">
            <div class="resource-migration__stats">
              <div>
                <strong>{{ preview.selectedCount || 0 }}</strong>
                <span>范围总数</span>
              </div>
              <div class="is-success">
                <strong>{{ preview.eligibleCount || 0 }}</strong>
                <span>可迁移</span>
              </div>
              <div class="is-warning">
                <strong>{{ preview.skippedCount || 0 }}</strong>
                <span>将跳过</span>
              </div>
              <div>
                <strong>{{ formatBytes(preview.eligibleBytes) }}</strong>
                <span>预计流量</span>
              </div>
            </div>

            <el-table
              v-if="skippedPreviewItems.length"
              :data="skippedPreviewItems"
              border
              size="mini"
              max-height="260">
              <el-table-column prop="resourceId" label="ID" width="64" align="center"></el-table-column>
              <el-table-column prop="path" label="跳过资源" min-width="250" show-overflow-tooltip></el-table-column>
              <el-table-column prop="mimeType" label="类型" width="130" show-overflow-tooltip></el-table-column>
              <el-table-column prop="reason" label="原因" min-width="210" show-overflow-tooltip></el-table-column>
            </el-table>
            <p v-if="preview.itemsTruncated" class="resource-migration__hint">
              预检明细仅展示前 200 项，任务创建时仍会固化并处理完整范围。
            </p>
          </div>
          <el-empty v-else description="选择范围和目标图床后执行预检"></el-empty>
        </section>
      </div>

      <div v-else-if="taskView" v-loading="taskLoading || taskActionLoading">
        <div class="resource-migration__task-header">
          <div>
            <span>任务 ID</span>
            <strong>{{ currentTask.taskId }}</strong>
          </div>
          <el-tag :type="taskStatusTagType">{{ taskStatusLabel }}</el-tag>
        </div>

        <el-progress
          :percentage="taskProgressPercent"
          :status="taskProgressStatus"
          :stroke-width="10">
        </el-progress>

        <div class="resource-migration__stats resource-migration__task-stats">
          <div>
            <strong>{{ taskTotalCount }}</strong>
            <span>总数</span>
          </div>
          <div>
            <strong>{{ taskProcessedCount }}</strong>
            <span>已处理</span>
          </div>
          <div class="is-success">
            <strong>{{ taskSuccessCount }}</strong>
            <span>成功</span>
          </div>
          <div class="is-warning">
            <strong>{{ taskSkippedCount }}</strong>
            <span>跳过</span>
          </div>
          <div class="is-danger">
            <strong>{{ taskFailedCount }}</strong>
            <span>失败</span>
          </div>
        </div>

        <el-alert
          v-if="currentTask.errorMessage"
          :title="currentTask.errorMessage"
          :type="taskStatus === 'FAILED' ? 'error' : 'warning'"
          :closable="false"
          show-icon>
        </el-alert>

        <div class="resource-migration__task-meta">
          <span>目标：{{ getStoreLabel(currentTask.targetStoreType) }}</span>
          <span>范围：{{ currentTask.scopeType === 'FILTER' ? '当前筛选全部' : '明确勾选' }}</span>
          <span>源文件：保留</span>
        </div>

        <el-table
          :data="taskItems"
          border
          size="mini"
          max-height="360"
          class="resource-migration__items">
          <el-table-column prop="resourceId" label="ID" width="64" align="center"></el-table-column>
          <el-table-column prop="sourcePath" label="原路径" min-width="220" show-overflow-tooltip></el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template slot-scope="scope">
              <el-tag :type="getItemStatusType(scope.row.status)" size="mini">
                {{ getItemStatusLabel(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试" width="66" align="center">
            <template slot-scope="scope">{{ scope.row.retryCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="说明" min-width="200" show-overflow-tooltip>
            <template slot-scope="scope">
              {{ scope.row.errorMessage || (scope.row.status === 'SUCCESS' ? scope.row.targetPath : '-') }}
            </template>
          </el-table-column>
        </el-table>
        <p v-if="taskView.itemsTruncated" class="resource-migration__hint">
          任务明细仅展示前 500 项，统计数字仍以完整任务为准。
        </p>
      </div>

      <span slot="footer" class="dialog-footer">
        <template v-if="panelMode === 'config'">
          <el-button :disabled="creating" @click="dialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="creating"
            :disabled="!canCreateTask"
            @click="createTask">
            创建迁移任务
          </el-button>
        </template>
        <template v-else>
          <el-button
            v-if="isTaskActive"
            type="warning"
            plain
            :loading="taskActionLoading"
            @click="cancelTask">
            取消任务
          </el-button>
          <el-button
            v-if="canRetry"
            type="primary"
            plain
            :loading="taskActionLoading"
            @click="retryTask">
            重试失败项
          </el-button>
          <el-button
            v-if="canCleanup"
            type="danger"
            plain
            :loading="taskActionLoading"
            @click="cleanupSources">
            清理源文件
          </el-button>
          <el-button
            v-if="isTaskTerminal"
            @click="showConfig">
            新建迁移
          </el-button>
          <el-button @click="dialogVisible = false">关闭</el-button>
        </template>
      </span>
    </el-dialog>
  </div>
</template>

<script>
const ACTIVE_TASK_STATUSES = ['PENDING', 'RUNNING'];
const TERMINAL_TASK_STATUSES = ['PARTIAL_SUCCESS', 'SUCCESS', 'FAILED', 'CANCELLED'];
const TASK_STORAGE_KEY = 'poetize.resource.migration.taskId';
const POLL_INTERVAL = 1500;

export default {
  name: 'ResourceMigrationDialog',
  props: {
    selectedResources: {
      type: Array,
      default: () => []
    },
    resourceType: {
      type: String,
      default: ''
    },
    resourceTypeLabel: {
      type: String,
      default: ''
    },
    filterScopeAvailable: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      dialogVisible: false,
      panelMode: 'config',
      capabilities: [],
      capabilitiesLoading: false,
      previewLoading: false,
      creating: false,
      preview: null,
      previewSignature: '',
      form: {
        scopeType: 'SELECTED',
        targetStoreType: ''
      },
      taskView: null,
      taskLoading: false,
      taskActionLoading: false,
      pollTimer: null,
      polling: false,
      emittedTerminalTaskKey: ''
    };
  },
  computed: {
    selectedCount() {
      return this.selectedResources.length;
    },
    availableCapabilities() {
      return this.capabilities.filter((capability) => {
        return capability && capability.enabled && capability.uploadSupported;
      });
    },
    selectedCapability() {
      return this.availableCapabilities.find((capability) => {
        return capability.storeType === this.form.targetStoreType;
      }) || null;
    },
    canPreview() {
      if (!this.form.targetStoreType) {
        return false;
      }
      if (this.form.scopeType === 'SELECTED') {
        return this.selectedCount > 0;
      }
      return this.filterScopeAvailable;
    },
    currentRequestSignature() {
      const targetSignature = this.form.scopeType === 'SELECTED'
        ? this.selectedResources.map((resource) => String(resource.id) + ':' + (resource.path || '')).sort().join('|')
        : this.resourceType;
      return [this.form.scopeType, this.form.targetStoreType, targetSignature].join('::');
    },
    canCreateTask() {
      return this.canPreview &&
        !!this.preview &&
        this.previewSignature === this.currentRequestSignature &&
        Number(this.preview.eligibleCount) > 0;
    },
    skippedPreviewItems() {
      if (!this.preview || !Array.isArray(this.preview.items)) {
        return [];
      }
      return this.preview.items.filter((item) => !item.eligible);
    },
    currentTask() {
      return this.taskView && this.taskView.task ? this.taskView.task : {};
    },
    taskStatus() {
      return this.currentTask.status || '';
    },
    isTaskActive() {
      return ACTIVE_TASK_STATUSES.includes(this.taskStatus);
    },
    isTaskTerminal() {
      return TERMINAL_TASK_STATUSES.includes(this.taskStatus);
    },
    taskTotalCount() {
      return Number(this.currentTask.totalCount) || 0;
    },
    taskProcessedCount() {
      return Number(this.currentTask.processedCount) || 0;
    },
    taskSuccessCount() {
      return Number(this.currentTask.successCount) || 0;
    },
    taskSkippedCount() {
      return Number(this.currentTask.skippedCount) || 0;
    },
    taskFailedCount() {
      return Number(this.currentTask.failedCount) || 0;
    },
    taskProgressPercent() {
      if (!this.taskTotalCount) {
        return this.isTaskTerminal ? 100 : 0;
      }
      return Math.min(100, Math.round(this.taskProcessedCount / this.taskTotalCount * 100));
    },
    taskProgressStatus() {
      if (this.taskStatus === 'SUCCESS') {
        return 'success';
      }
      if (this.taskStatus === 'FAILED') {
        return 'exception';
      }
      if (this.taskStatus === 'PARTIAL_SUCCESS' || this.taskStatus === 'CANCELLED') {
        return 'warning';
      }
      return undefined;
    },
    taskStatusLabel() {
      const labels = {
        PENDING: '迁移任务等待执行',
        RUNNING: '资源迁移中',
        PARTIAL_SUCCESS: '迁移部分完成',
        SUCCESS: '迁移完成',
        FAILED: '迁移失败',
        CANCELLED: '迁移已取消'
      };
      return labels[this.taskStatus] || '资源迁移任务';
    },
    taskStatusIcon() {
      if (this.isTaskActive) {
        return 'el-icon-loading';
      }
      if (this.taskStatus === 'SUCCESS') {
        return 'el-icon-success';
      }
      if (this.taskStatus === 'FAILED') {
        return 'el-icon-error';
      }
      return 'el-icon-warning';
    },
    taskStatusTagType() {
      if (this.taskStatus === 'SUCCESS') {
        return 'success';
      }
      if (this.taskStatus === 'FAILED') {
        return 'danger';
      }
      if (this.taskStatus === 'PARTIAL_SUCCESS' || this.taskStatus === 'CANCELLED') {
        return 'warning';
      }
      return 'info';
    },
    taskItems() {
      return this.taskView && Array.isArray(this.taskView.items) ? this.taskView.items : [];
    },
    canRetry() {
      return ['FAILED', 'PARTIAL_SUCCESS'].includes(this.taskStatus) &&
        this.taskFailedCount > 0 &&
        !this.taskActionLoading;
    },
    canCleanup() {
      return this.isTaskTerminal && this.taskStatus !== 'CANCELLED' &&
        this.taskSuccessCount > 0 && !this.taskActionLoading;
    }
  },
  created() {
    this.restoreLatestTask();
  },
  beforeDestroy() {
    this.stopPolling();
  },
  methods: {
    open() {
      this.dialogVisible = true;
      this.panelMode = this.taskView && this.isTaskActive ? 'task' : 'config';
      if (!this.selectedCount && this.filterScopeAvailable) {
        this.form.scopeType = 'FILTER';
      } else if (this.selectedCount) {
        this.form.scopeType = 'SELECTED';
      }
      this.loadCapabilities();
    },
    openTask() {
      this.dialogVisible = true;
      this.panelMode = 'task';
      this.pollTask();
    },
    showConfig() {
      this.panelMode = 'config';
      this.preview = null;
      this.previewSignature = '';
      this.loadCapabilities();
    },
    handleDialogClose(done) {
      if (!this.creating && !this.taskActionLoading) {
        done();
      }
    },
    async loadCapabilities() {
      if (this.capabilitiesLoading || this.capabilities.length) {
        return;
      }
      this.capabilitiesLoading = true;
      try {
        const response = await this.$http.get(
          this.$constant.baseURL + '/resource/migration/capabilities',
          {},
          true
        );
        this.capabilities = response && Array.isArray(response.data) ? response.data : [];
        if (!this.availableCapabilities.some((item) => item.storeType === this.form.targetStoreType)) {
          this.form.targetStoreType = this.availableCapabilities.length
            ? this.availableCapabilities[0].storeType
            : '';
        }
      } catch (error) {
        this.$message({
          message: error.message || '读取图床能力失败',
          type: 'error'
        });
      } finally {
        this.capabilitiesLoading = false;
      }
    },
    handleConfigChange() {
      this.preview = null;
      this.previewSignature = '';
    },
    buildRequest() {
      return {
        targets: this.form.scopeType === 'SELECTED'
          ? this.selectedResources.map((resource) => ({
            resourceId: resource.id,
            expectedPath: resource.path
          }))
          : [],
        scopeType: this.form.scopeType,
        resourceType: this.form.scopeType === 'FILTER' ? this.resourceType : '',
        targetStoreType: this.form.targetStoreType,
        keepSource: true
      };
    },
    async previewMigration() {
      if (!this.canPreview) {
        this.$message({ message: '请选择有效的迁移范围和目标图床', type: 'warning' });
        return null;
      }
      const signature = this.currentRequestSignature;
      this.previewLoading = true;
      try {
        const response = await this.$http.post(
          this.$constant.baseURL + '/resource/migration/preview',
          this.buildRequest(),
          true
        );
        this.preview = response && response.data ? response.data : null;
        this.previewSignature = this.preview ? signature : '';
        return this.preview;
      } catch (error) {
        this.preview = null;
        this.previewSignature = '';
        this.$message({
          message: error.message || '迁移预检失败',
          type: 'error'
        });
        return null;
      } finally {
        this.previewLoading = false;
      }
    },
    async createTask() {
      let preview = this.preview;
      if (!preview || this.previewSignature !== this.currentRequestSignature) {
        preview = await this.previewMigration();
      }
      if (!preview || !preview.eligibleCount) {
        this.$message({ message: '当前范围没有可迁移资源', type: 'warning' });
        return;
      }

      const message = '将创建迁移任务：可迁移 ' + preview.eligibleCount + ' 项（' +
        this.formatBytes(preview.eligibleBytes) + '）' +
        (preview.skippedCount ? '，另有 ' + preview.skippedCount + ' 项因目标能力限制而跳过。' : '。') +
        '<br><br><strong>源文件默认保留，不会在迁移任务中自动删除。</strong>';
      try {
        await this.$confirm(message, '确认创建迁移任务', {
          confirmButtonText: '创建任务',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true,
          customClass: 'mobile-responsive-confirm'
        });
      } catch (error) {
        return;
      }

      this.creating = true;
      try {
        const response = await this.$http.post(
          this.$constant.baseURL + '/resource/migration',
          this.buildRequest(),
          true
        );
        const task = response && response.data ? response.data : null;
        if (!task || !task.taskId) {
          throw new Error('迁移任务返回数据异常');
        }
        this.taskView = { task, items: [], itemsTruncated: false };
        this.persistTaskId(task.taskId);
        this.panelMode = 'task';
        this.emittedTerminalTaskKey = '';
        this.$emit('task-created', task);
        this.startPolling();
        this.pollTask();
        this.$message({ message: '迁移任务已创建', type: 'success' });
      } catch (error) {
        this.$message({
          message: error.message || '创建迁移任务失败',
          type: 'error'
        });
      } finally {
        this.creating = false;
      }
    },
    restoreLatestTask() {
      let taskId = '';
      try {
        taskId = window.localStorage.getItem(TASK_STORAGE_KEY) || '';
      } catch (error) {
        taskId = '';
      }
      if (!taskId) {
        return;
      }
      this.taskView = {
        task: { taskId, status: 'PENDING' },
        items: [],
        itemsTruncated: false
      };
      this.pollTask(true);
    },
    persistTaskId(taskId) {
      try {
        window.localStorage.setItem(TASK_STORAGE_KEY, taskId);
      } catch (error) {
      }
    },
    clearPersistedTask() {
      try {
        window.localStorage.removeItem(TASK_STORAGE_KEY);
      } catch (error) {
      }
    },
    startPolling() {
      this.stopPolling();
      this.pollTimer = window.setInterval(() => this.pollTask(), POLL_INTERVAL);
    },
    stopPolling() {
      if (this.pollTimer) {
        window.clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },
    async pollTask(restoring) {
      const taskId = this.currentTask.taskId;
      if (!taskId || this.polling) {
        return;
      }
      this.polling = true;
      if (!restoring) {
        this.taskLoading = !this.taskView || !this.taskView.items || !this.taskView.items.length;
      }
      try {
        const response = await this.$http.get(
          this.$constant.baseURL + '/resource/migration/' + encodeURIComponent(taskId),
          {},
          true
        );
        const view = response && response.data ? response.data : null;
        if (!view || !view.task) {
          throw new Error('迁移任务状态返回数据异常');
        }
        this.taskView = view;
        this.persistTaskId(view.task.taskId);
        if (ACTIVE_TASK_STATUSES.includes(view.task.status)) {
          this.startPollingIfNeeded();
        } else if (TERMINAL_TASK_STATUSES.includes(view.task.status)) {
          this.stopPolling();
          this.emitTaskFinished(view);
        }
      } catch (error) {
        if (restoring && error.message === '迁移任务不存在') {
          this.taskView = null;
          this.clearPersistedTask();
        } else if (restoring) {
          this.startPollingIfNeeded();
        }
      } finally {
        this.polling = false;
        this.taskLoading = false;
      }
    },
    startPollingIfNeeded() {
      if (!this.pollTimer) {
        this.pollTimer = window.setInterval(() => this.pollTask(), POLL_INTERVAL);
      }
    },
    emitTaskFinished(view) {
      const key = view.task.taskId + ':' + view.task.status + ':' + (view.task.updateTime || '');
      if (this.emittedTerminalTaskKey === key) {
        return;
      }
      this.emittedTerminalTaskKey = key;
      this.$emit('task-finished', view);
    },
    async cancelTask() {
      try {
        await this.$confirm(
          '取消会在当前文件处理完成后生效；已经迁移成功的资源不会回滚。',
          '取消迁移任务',
          {
            confirmButtonText: '确认取消',
            cancelButtonText: '继续迁移',
            type: 'warning',
            customClass: 'mobile-responsive-confirm'
          }
        );
      } catch (error) {
        return;
      }
      await this.runTaskAction(
        '/resource/migration/' + encodeURIComponent(this.currentTask.taskId) + '/cancel',
        '已提交取消请求'
      );
    },
    async retryTask() {
      if (!this.canRetry) {
        return;
      }
      await this.runTaskAction(
        '/resource/migration/' + encodeURIComponent(this.currentTask.taskId) + '/retry',
        '失败项已重新排队',
        true
      );
    },
    async cleanupSources() {
      try {
        await this.$confirm(
          '系统会重新校验迁移目标，只清理已验证成功且位于允许上传目录中的本地源文件。此操作无法通过管理端恢复。',
          '清理迁移源文件',
          {
            confirmButtonText: '确认清理',
            cancelButtonText: '保留源文件',
            type: 'warning',
            customClass: 'mobile-responsive-confirm'
          }
        );
      } catch (error) {
        return;
      }

      this.taskActionLoading = true;
      try {
        const response = await this.$http.post(
          this.$constant.baseURL + '/resource/migration/' + encodeURIComponent(this.currentTask.taskId) + '/cleanup',
          {},
          true
        );
        const result = response && response.data ? response.data : {};
        this.$message({
          message: '已清理 ' + (result.cleanedCount || 0) + ' 项' +
            (result.skippedCount ? '，跳过 ' + result.skippedCount + ' 项' : '') +
            (result.failedCount ? '，失败 ' + result.failedCount + ' 项' : ''),
          type: result.failedCount ? 'warning' : 'success'
        });
        await this.pollTask();
      } catch (error) {
        this.$message({
          message: error.message || '源文件清理失败',
          type: 'error'
        });
      } finally {
        this.taskActionLoading = false;
      }
    },
    async runTaskAction(path, successMessage, resumePolling) {
      this.taskActionLoading = true;
      try {
        const response = await this.$http.post(this.$constant.baseURL + path, {}, true);
        if (resumePolling && response && response.data) {
          this.taskView = {
            task: response.data,
            items: this.taskItems,
            itemsTruncated: this.taskView ? this.taskView.itemsTruncated : false
          };
          this.emittedTerminalTaskKey = '';
          this.startPolling();
        }
        this.$message({ message: successMessage, type: 'success' });
        await this.pollTask();
      } catch (error) {
        this.$message({
          message: error.message || '迁移任务操作失败',
          type: 'error'
        });
      } finally {
        this.taskActionLoading = false;
      }
    },
    getStoreLabel(storeType) {
      const labels = {
        qiniu: '七牛云',
        lsky: '兰空图床',
        easyimage: '简单图床',
        local: '服务器本地'
      };
      return labels[storeType] ? labels[storeType] + ' (' + storeType + ')' : (storeType || '-');
    },
    formatLimit(value) {
      const size = Number(value) || 0;
      return size > 0 ? this.formatBytes(size) : '由目标平台决定';
    },
    formatMimePrefixes(prefixes) {
      if (!Array.isArray(prefixes) || !prefixes.length) {
        return '不限类型';
      }
      return prefixes.join('、');
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
    },
    getItemStatusLabel(status) {
      const labels = {
        PENDING: '等待处理',
        UPLOADING: '上传中',
        UPLOADED: '已上传',
        SWITCHED: '已切换',
        SUCCESS: '成功',
        SKIPPED: '跳过',
        FAILED: '失败'
      };
      return labels[status] || status || '-';
    },
    getItemStatusType(status) {
      if (status === 'SUCCESS') {
        return 'success';
      }
      if (status === 'FAILED') {
        return 'danger';
      }
      if (status === 'SKIPPED') {
        return 'warning';
      }
      return 'info';
    }
  }
};
</script>

<style scoped>
.resource-migration__task-bar {
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(120px, 1fr) auto;
  align-items: center;
  gap: 14px;
  margin: 0 0 14px;
  padding: 11px 14px;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  background: #f4f8ff;
}

.resource-migration__task-bar.is-success {
  border-color: #e1f3d8;
  background: #f0f9eb;
}

.resource-migration__task-bar.is-failed,
.resource-migration__task-bar.is-partial_success {
  border-color: #fde2e2;
  background: #fef0f0;
}

.resource-migration__task-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.resource-migration__task-main > i {
  color: #409eff;
  font-size: 22px;
}

.resource-migration__task-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.resource-migration__task-copy span {
  overflow: hidden;
  color: #909399;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-migration__section {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.resource-migration__section h4 {
  margin: 0 0 14px;
  color: #303133;
  font-size: 15px;
}

.resource-migration__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.resource-migration__section-title h4 {
  margin-bottom: 0;
}

.resource-migration__hint {
  margin: 10px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.resource-migration__target {
  width: 100%;
}

.resource-migration__capability {
  margin-top: 12px;
  padding: 12px;
  border-radius: 6px;
  background: #f5f7fa;
}

.resource-migration__capability-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.resource-migration__capability-head strong {
  margin-right: 4px;
}

.resource-migration__capability dl {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
}

.resource-migration__capability dl > div {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 10px;
  color: #606266;
  font-size: 13px;
}

.resource-migration__capability dt {
  color: #909399;
}

.resource-migration__capability dd {
  margin: 0;
  word-break: break-word;
}

.resource-migration__preview {
  margin-top: 16px;
}

.resource-migration__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.resource-migration__stats > div {
  display: flex;
  flex-direction: column;
  min-width: 92px;
  gap: 2px;
  padding: 9px 12px;
  border-radius: 6px;
  background: #f5f7fa;
}

.resource-migration__stats strong {
  color: #303133;
  font-size: 18px;
}

.resource-migration__stats span {
  color: #909399;
  font-size: 12px;
}

.resource-migration__stats .is-success strong {
  color: #67c23a;
}

.resource-migration__stats .is-warning strong {
  color: #e6a23c;
}

.resource-migration__stats .is-danger strong {
  color: #f56c6c;
}

.resource-migration__task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.resource-migration__task-header > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}

.resource-migration__task-header span {
  color: #909399;
  font-size: 12px;
}

.resource-migration__task-header strong {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
}

.resource-migration__task-stats {
  margin-top: 16px;
}

.resource-migration__task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin: 14px 0;
  color: #606266;
  font-size: 13px;
}

.resource-migration__items {
  width: 100%;
}

@media screen and (max-width: 768px) {
  .resource-migration__task-bar {
    grid-template-columns: 1fr auto;
  }

  .resource-migration__task-progress {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .resource-migration__section {
    padding: 12px;
  }

  .resource-migration__section-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .resource-migration__capability dl > div {
    grid-template-columns: 1fr;
    gap: 3px;
  }
}
</style>