<template>
  <div class="async-notification-container">
    <!-- 通知列表 -->
    <transition-group name="notification" tag="div" class="notification-list">
      <div
        v-for="notification in notifications"
        :key="notification.id"
        :class="['notification-item', notification.type, notification.onClick ? 'clickable' : '']"
        @click="handleNotificationClick(notification)"
      >
        <div class="notification-content">
          <div class="notification-icon">
            <i v-if="notification.type === 'loading'" class="el-icon-loading"></i>
            <i v-else-if="notification.type === 'success'" class="el-icon-success"></i>
            <i v-else-if="notification.type === 'error'" class="el-icon-error"></i>
            <i v-else class="el-icon-info"></i>
          </div>
          <div class="notification-text">
            <div class="notification-title">{{ notification.title }}</div>
            <div class="notification-message">{{ notification.message }}</div>
          </div>
          <div class="notification-close" @click.stop="removeNotification(notification.id)">
            <i class="el-icon-close"></i>
          </div>
        </div>
        <!-- 进度条 -->
        <div v-if="notification.type === 'loading'" class="notification-progress">
          <div class="progress-bar" :style="{width: notification.progress + '%'}"></div>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<script>
export default {
  name: 'AsyncNotification',
  data() {
    return {
      notifications: [],
      pollTimers: {}, // 存储每个任务的轮询定时器
      streamControllers: {}, // 存储每个任务的流式请求控制器
      streamHeartbeatTimers: {},
      streamReconnectTimers: {},
      streamReconnectAttempts: {},
      streamHeartbeatTimeoutMs: 25000,
      maxStreamReconnectAttempts: 2
    }
  },
  mounted() {
    // 确保组件能访问到全局常量
    if (!this.$constant && this.$parent && this.$parent.$constant) {
      this.$constant = this.$parent.$constant;
    }
  },
  beforeDestroy() {
    this.clearAllNotifications();
  },
  methods: {
    /**
     * 添加通知
     * @param {Object} notification - 通知对象
     * @param {string} notification.title - 标题
     * @param {string} notification.message - 消息
     * @param {string} notification.type - 类型：loading, success, error, info
     * @param {number} notification.duration - 持续时间（毫秒），0表示不自动消失
     * @param {string} notification.taskId - 任务ID（用于更新状态）
     */
    addNotification(notification) {
      if (notification.taskId) {
        const existingIndex = this.notifications.findIndex(n => n.taskId === notification.taskId)
        if (existingIndex > -1) {
          const existingNotification = this.notifications[existingIndex]
          Object.assign(existingNotification, {
            title: notification.title || existingNotification.title || '通知',
            message: notification.message || existingNotification.message || '',
            type: notification.type || existingNotification.type || 'info',
            duration: notification.duration !== undefined ? notification.duration : existingNotification.duration,
            onClick: notification.onClick || existingNotification.onClick || null
          })

          if (existingIndex > 0) {
            this.notifications.splice(existingIndex, 1)
            this.notifications.unshift(existingNotification)
          }

          return existingNotification.id
        }
      }

      const id = Date.now() + Math.random();
      const newNotification = {
        id,
        title: notification.title || '通知',
        message: notification.message || '',
        type: notification.type || 'info',
        duration: notification.duration !== undefined ? notification.duration : 3000,
        taskId: notification.taskId,
        onClick: notification.onClick || null,
        progress: 0,
        createTime: Date.now()
      };
      
      this.notifications.unshift(newNotification);
      
      // 自动移除（除非duration为0）
      if (newNotification.duration > 0) {
        setTimeout(() => {
          this.removeNotification(id);
        }, newNotification.duration);
      }
      
      return id;
    },
    
    /**
     * 处理通知点击
     */
    handleNotificationClick(notification) {
      if (notification.onClick && typeof notification.onClick === 'function') {
        notification.onClick();
      }
      this.removeNotification(notification.id);
    },

    /**
     * 更新通知
     */
    updateNotification(id, updates) {
      const notification = this.notifications.find(n => n.id === id);
      if (notification) {
        Object.assign(notification, updates);
        
        // 如果状态变为终态，设置自动移除
        if ((updates.type === 'success' || updates.type === 'error' || updates.type === 'info') && notification.duration === 0) {
          notification.duration = updates.duration !== undefined
            ? updates.duration
            : (updates.type === 'success' ? 2000 : 5000);
          setTimeout(() => {
            this.removeNotification(id);
          }, notification.duration);
        }
      }
    },
    
    /**
     * 根据任务ID更新通知
     */
    updateNotificationByTaskId(taskId, updates) {
      const notification = this.notifications.find(n => n.taskId === taskId);
      if (notification) {
        this.updateNotification(notification.id, updates);
      }
    },
    
    /**
     * 移除通知
     */
    removeNotification(id) {
      const index = this.notifications.findIndex(n => n.id === id);
      if (index > -1) {
        const notification = this.notifications[index];
        if (notification && notification.taskId) {
          this.stopPolling(notification.taskId);
          this.stopStream(notification.taskId);
        }
        this.notifications.splice(index, 1);
      }
    },
    
    /**
     * 清空所有通知
     */
    clearAllNotifications() {
      this.notifications = [];
      
      // 清理所有轮询定时器
      Object.values(this.pollTimers).forEach(timer => {
        if (timer) clearInterval(timer);
      });
      this.pollTimers = {};

      Object.values(this.streamControllers).forEach(controller => {
        if (controller) controller.abort();
      });
      this.streamControllers = {};
      Object.keys(this.streamHeartbeatTimers).forEach(taskId => {
        this.clearStreamHeartbeat(taskId);
      });
      Object.keys(this.streamReconnectTimers).forEach(taskId => {
        this.clearStreamReconnect(taskId);
      });
      this.streamReconnectAttempts = {};
    },
    
    /**
     * 开始轮询任务状态
     */
    startPolling(taskId) {
      if (!taskId) {
        console.error('startPolling: taskId为空');
        return;
      }
      
      // 只在开始轮询时输出一次日志
      
      // 清理之前的定时器（如果存在）
      if (this.pollTimers[taskId]) {
        clearInterval(this.pollTimers[taskId]);
      }
      
      // 立即执行一次检查
      this.checkTaskStatus(taskId);
      
      // 创建新的轮询定时器
      this.pollTimers[taskId] = setInterval(() => {
        this.checkTaskStatus(taskId);
      }, 2000);
    },
    
    /**
     * 停止轮询任务状态
     */
    stopPolling(taskId) {
      if (this.pollTimers[taskId]) {
        clearInterval(this.pollTimers[taskId]);
        delete this.pollTimers[taskId];
      }
    },

    startStream(taskId) {
      if (!taskId) {
        return;
      }

      this.stopStream(taskId, { preserveReconnectState: true });

      const baseURL = this.getBaseURL();
      const headers = this.getAuthHeaders();
      const controller = new AbortController();
      this.streamControllers[taskId] = controller;
      this.armStreamHeartbeat(taskId);

      fetch(`${baseURL}/article/streamArticleSaveStatus?taskId=${encodeURIComponent(taskId)}`, {
        method: 'GET',
        headers,
        credentials: 'include',
        signal: controller.signal
      })
      .then(response => {
        if (!response.ok || !response.body) {
          throw new Error('任务流式订阅失败');
        }
        this.streamReconnectAttempts[taskId] = 0;
        this.armStreamHeartbeat(taskId);
        return this.consumeStream(taskId, response.body.getReader());
      })
      .catch(error => {
        if (error.name !== 'AbortError') {
          console.error('任务流式订阅失败:', error);
        }
        this.stopStream(taskId, { preserveReconnectState: true });
        this.tryReconnectStream(taskId, error);
      });
    },

    stopStream(taskId, options) {
      const preserveReconnectState = options && options.preserveReconnectState;
      const controller = this.streamControllers[taskId];
      if (controller) {
        controller.abort();
        delete this.streamControllers[taskId];
      }
      this.clearStreamHeartbeat(taskId);
      if (!preserveReconnectState) {
        this.clearStreamReconnect(taskId);
        delete this.streamReconnectAttempts[taskId];
      }
    },

    async consumeStream(taskId, reader) {
      const decoder = new TextDecoder();
      let buffer = '';
      let currentEventName = null;

      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          this.stopStream(taskId, { preserveReconnectState: true });
          throw new Error('任务流已结束');
        }

        this.armStreamHeartbeat(taskId);
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        if (lines[lines.length - 1] !== '') {
          buffer = lines.pop();
        } else {
          buffer = '';
        }

        for (const line of lines) {
          const trimmedLine = line.trim();
          if (!trimmedLine) {
            currentEventName = null;
            continue;
          }

          if (trimmedLine.startsWith('event:')) {
            currentEventName = trimmedLine.substring(6).trim();
            continue;
          }

          if (!trimmedLine.startsWith('data:')) {
            continue;
          }

          const dataStr = trimmedLine.substring(5).trim();
          if (!dataStr) {
            continue;
          }

          try {
            const payload = JSON.parse(dataStr);
            this.handleTaskStreamEvent(taskId, currentEventName, payload);
          } catch (error) {
            console.error('解析任务流事件失败:', error);
          }
        }
      }
    },
    
    /**
     * 检查任务状态
     */
    checkTaskStatus(taskId) {
      const baseURL = this.getBaseURL();
      const url = `${baseURL}/article/getArticleSaveStatus`;
      const headers = this.getAuthHeaders();
      
      fetch(url + '?taskId=' + encodeURIComponent(taskId), {
        method: 'GET',
        headers: headers,
        credentials: 'include'
      })
      .then(response => response.json())
      .then(res => {
        if (res.code === 200 && res.data) {
          this.applyTaskStatus(taskId, res.data);
        } else {
          // 任务不存在或已过期
          this.stopPolling(taskId);
          this.stopStream(taskId);
          
          this.updateNotificationByTaskId(taskId, {
            type: 'error',
            title: '状态查询失败',
            message: '无法查询保存状态，任务可能已完成或过期'
          });
        }
      })
      .catch(error => {
        console.error('轮询请求失败:', error);
        // 网络错误时不再输出额外日志，避免刷屏
      });
    },

    handleTaskStreamEvent(taskId, eventName, payload) {
      this.armStreamHeartbeat(taskId);
      const terminalStatus = payload && this.isTerminalTaskStatus(payload.status);

      if (eventName === 'heartbeat') {
        return;
      }

      if (eventName === 'error') {
        if (terminalStatus) {
          this.applyTaskStatus(taskId, payload || {});
        } else {
          this.updateNotificationByTaskId(taskId, {
            message: (payload && payload.message) || '翻译阶段出现问题，正在继续处理...',
            progress: 60
          });
        }
        return;
      }

      if (eventName === 'complete') {
        if (terminalStatus) {
          this.applyTaskStatus(taskId, payload || {});
          return;
        }

        this.updateNotificationByTaskId(taskId, {
          message: (payload && payload.message) || '翻译流已完成，正在保存翻译结果...',
          progress: 70
        });
        return;
      }

      if (eventName === 'translation_complete') {
        this.updateNotificationByTaskId(taskId, {
          message: (payload && payload.message) || '翻译流已完成，正在保存翻译结果...',
          progress: 70
        });
        return;
      }

      if (eventName === 'seo_push') {
        this.applyTaskStatus(taskId, payload || {});
        return;
      }

      if (eventName === 'stage' || eventName === 'start' || eventName === 'retry') {
        this.applyTaskStatus(taskId, payload || {});
        return;
      }

      if (eventName === 'title_delta' || eventName === 'content_delta') {
        const currentLength = payload && payload.currentLength ? payload.currentLength : 0;
        const fieldLabel = eventName === 'title_delta' ? '标题' : '正文';
        this.updateNotificationByTaskId(taskId, {
          message: `正在流式翻译${fieldLabel}... 已接收 ${currentLength} 字`,
          progress: 55
        });
        return;
      }

      if (eventName === 'translation_delta') {
        const currentLength = payload && payload.currentLength ? payload.currentLength : 0;
        this.updateNotificationByTaskId(taskId, {
          message: payload.message || `正在接收AI翻译响应... 已接收 ${currentLength} 字`,
          progress: 55
        });
        return;
      }

      if (eventName === 'saved') {
        this.updateNotificationByTaskId(taskId, {
          message: payload.message || '翻译保存成功，正在继续后续任务...',
          progress: 75
        });
      }
    },

    isTerminalTaskStatus(status) {
      return status === 'success' || status === 'failed' || status === 'partial_success';
    },

    requiresSeoPush(status) {
      return !!(status && status.seoPushRequired);
    },

    isSeoPushPending(status) {
      if (!this.requiresSeoPush(status)) {
        return false;
      }
      const seoPushStatus = status && status.seoPushStatus;
      return !seoPushStatus || seoPushStatus === 'pending' || seoPushStatus === 'pushing';
    },

    getSeoPushProgress(status) {
      if (!this.requiresSeoPush(status)) {
        return 100;
      }
      if (this.isSeoPushPending(status)) {
        return status && status.seoPushStatus === 'pushing' ? 98 : 95;
      }
      return 100;
    },

    resolvePendingSeoMessage(status) {
      if (status && status.seoPushStatus === 'pushing' && status.seoPushMessage) {
        return status.seoPushMessage;
      }
      return (status && status.seoPushMessage) || '文章已保存，正在推送到搜索引擎...';
    },

    resolveFinalTaskNotification(status) {
      if (status.status === 'failed') {
        return {
          type: 'error',
          title: '保存失败',
          message: status.message || '文章保存失败',
          progress: 100
        };
      }

      if (status.status === 'partial_success') {
        return {
          type: 'info',
          title: '部分完成',
          message: status.seoPushStatus === 'failed'
            ? (status.seoPushMessage || status.summaryMessage || status.message || '文章已保存，但后续任务部分失败')
            : (status.summaryMessage || status.message || '文章已保存，但摘要未完成'),
          progress: 100,
          duration: 5000
        };
      }

      if (status.seoPushStatus === 'failed') {
        return {
          type: 'info',
          title: '保存成功，推送失败',
          message: status.seoPushMessage || status.message || '文章已保存，但搜索引擎推送失败',
          progress: 100,
          duration: 5000
        };
      }

      if (status.seoPushStatus === 'skipped') {
        return {
          type: 'info',
          title: '保存成功',
          message: status.seoPushMessage || status.message || '文章保存成功，搜索引擎推送已跳过',
          progress: 100,
          duration: 4000
        };
      }

      return {
        type: 'success',
        title: '保存成功',
        message: status.seoPushMessage || status.message || '文章保存成功！',
        progress: 100
      };
    },

    applyTaskStatus(taskId, status) {
      if (this.isTerminalTaskStatus(status.status) && this.isSeoPushPending(status)) {
        this.updateNotificationByTaskId(taskId, {
          message: this.resolvePendingSeoMessage(status),
          progress: this.getSeoPushProgress(status)
        });
        return;
      }

      const message = status.seoPushMessage || status.message || '任务处理中...';
      const progress = this.getProgressByStage(status.stage, status.status);

      this.updateNotificationByTaskId(taskId, {
        message,
        progress
      });

      if (this.isTerminalTaskStatus(status.status)) {
        this.stopPolling(taskId);
        this.stopStream(taskId);
        this.updateNotificationByTaskId(taskId, this.resolveFinalTaskNotification(status));
      }
    },

    armStreamHeartbeat(taskId) {
      this.clearStreamHeartbeat(taskId);
      this.streamHeartbeatTimers[taskId] = setTimeout(() => {
        this.handleStreamHeartbeatTimeout(taskId);
      }, this.streamHeartbeatTimeoutMs);
    },

    clearStreamHeartbeat(taskId) {
      if (this.streamHeartbeatTimers[taskId]) {
        clearTimeout(this.streamHeartbeatTimers[taskId]);
        delete this.streamHeartbeatTimers[taskId];
      }
    },

    clearStreamReconnect(taskId) {
      if (this.streamReconnectTimers[taskId]) {
        clearTimeout(this.streamReconnectTimers[taskId]);
        delete this.streamReconnectTimers[taskId];
      }
    },

    handleStreamHeartbeatTimeout(taskId) {
      if (this.isTaskStreamSettled(taskId)) {
        this.stopStream(taskId);
        return;
      }
      this.updateNotificationByTaskId(taskId, {
        message: '任务流连接中断，正在恢复实时进度...'
      });
      this.stopStream(taskId, { preserveReconnectState: true });
    },

    tryReconnectStream(taskId, error) {
      if (!taskId || this.isTaskStreamSettled(taskId)) {
        return;
      }

      const attempts = (this.streamReconnectAttempts[taskId] || 0) + 1;
      this.streamReconnectAttempts[taskId] = attempts;
      if (attempts > this.maxStreamReconnectAttempts) {
        this.clearStreamReconnect(taskId);
        this.updateNotificationByTaskId(taskId, {
          message: '任务流连接中断，已切换为状态轮询同步...'
        });
        this.checkTaskStatus(taskId);
        return;
      }

      const reconnectDelay = Math.min(attempts * 1500, 5000);
      this.updateNotificationByTaskId(taskId, {
        message: '任务流连接中断，正在恢复实时进度...'
      });
      this.checkTaskStatus(taskId);
      this.clearStreamReconnect(taskId);
      this.streamReconnectTimers[taskId] = setTimeout(() => {
        delete this.streamReconnectTimers[taskId];
        if (!this.isTaskStreamSettled(taskId)) {
          this.startStream(taskId);
        }
      }, reconnectDelay);

      if (error && error.name !== 'AbortError') {
        console.warn('任务流准备重连:', error.message || error);
      }
    },

    isTaskStreamSettled(taskId) {
      const notification = this.notifications.find(n => n.taskId === taskId);
      if (!notification) {
        return true;
      }
      return notification.progress === 100
        && (notification.type === 'success' || notification.type === 'error' || notification.type === 'info');
    },

    getProgressByStage(stage, status) {
      if (status === 'success' || status === 'failed' || status === 'partial_success') {
        return 100;
      }

      switch (stage) {
        case 'db_saved':
          return 20;
        case 'translating':
          return 50;
        case 'translation_retry':
          return 60;
        case 'translation_skipped':
          return 65;
        case 'saving_translation':
          return 75;
        case 'generating_summary':
          return 90;
        case 'complete':
          return 100;
        default:
          return 10;
      }
    },

    getBaseURL() {
      if (this.$constant && this.$constant.baseURL) {
        return this.$constant.baseURL;
      }
      if (window.VueAppConfig && window.VueAppConfig.baseURL) {
        return window.VueAppConfig.baseURL;
      }
      return window.location.protocol + '//' + window.location.host;
    },

    getAuthHeaders() {
      // Cookie-based auth: no need to inject Authorization header
      return {
        'Content-Type': 'application/json'
      };
    }
  }
}
</script>

<style scoped>
.async-notification-container {
  position: fixed;
  top: 80px;
  right: 20px;
  z-index: 3000;
  max-width: 350px;
  pointer-events: none;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notification-item {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  cursor: pointer;
  pointer-events: auto;
  /* 性能优化: 只监听位移和透明度 */
  transition: transform 0.3s ease, opacity 0.3s ease, box-shadow 0.3s ease;
  max-width: 350px;
  border-left: 4px solid;
  transform: translateZ(0);
}

.notification-item:hover {
  transform: translateX(-5px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

.notification-item.loading {
  border-left-color: #409EFF;
}

.notification-item.success {
  border-left-color: #67C23A;
}

.notification-item.error {
  border-left-color: #F56C6C;
}

.notification-item.info {
  border-left-color: #909399;
}

.notification-content {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  gap: 12px;
}

.notification-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.notification-icon i {
  font-size: 18px;
}

.loading .notification-icon i {
  color: #409EFF;
  animation: rotate 1s linear infinite;
}

.success .notification-icon i {
  color: #67C23A;
}

.error .notification-icon i {
  color: #F56C6C;
}

.info .notification-icon i {
  color: #909399;
}

.notification-text {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
  line-height: 1.4;
}

.notification-message {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  word-break: break-word;
}

.notification-close {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.notification-close:hover {
  background-color: #f5f7fa;
}

.notification-close i {
  font-size: 12px;
  color: #909399;
}

.notification-progress {
  height: 3px;
  background-color: #f5f7fa;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  transition: width 0.3s ease;
  animation: shimmer 2s ease-in-out infinite;
}

@keyframes rotate {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes shimmer {
  0% { background-position: -200px 0; }
  100% { background-position: 200px 0; }
}

/* 动画效果 */
.notification-enter-active {
  /* 性能优化: Vue过渡动画，需要GPU加速 */
  transition: transform 0.3s ease, opacity 0.3s ease;
  will-change: transform, opacity;
  transform: translateZ(0);
}

.notification-leave-active {
  /* 性能优化: Vue过渡动画，需要GPU加速 */
  transition: transform 0.3s ease, opacity 0.3s ease;
  will-change: transform, opacity;
  transform: translateZ(0);
}

.notification-enter {
  transform: translateX(100%);
  opacity: 0;
}

.notification-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

.notification-move {
  transition: transform 0.3s ease;
}

/* 暗色模式适配 */
body.dark-mode .notification-item {
  background: #2d2d2d !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

body.dark-mode .notification-item:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.4);
}

body.dark-mode .notification-title {
  color: #e0e0e0 !important;
}

body.dark-mode .notification-message {
  color: #b0b0b0 !important;
}

body.dark-mode .notification-close:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

body.dark-mode .notification-close i {
  color: #b0b0b0;
}

body.dark-mode .notification-progress {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .async-notification-container {
    top: 60px;
    right: 10px;
    left: 10px;
    max-width: none;
  }
  
  .notification-item {
    max-width: none;
  }
  
  .notification-content {
    padding: 12px;
  }
  
  .notification-title {
    font-size: 13px;
  }
  
  .notification-message {
    font-size: 12px;
  }
}
</style> 
