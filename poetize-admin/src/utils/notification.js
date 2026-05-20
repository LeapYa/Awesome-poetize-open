/**
 * 全局通知系统管理模块
 */

let globalNotificationInstance = null;

/**
 * 全局通知管理器
 */
export const notificationManager = {
  /**
   * 获取通知实例
   */
  getInstance() {
    if (!globalNotificationInstance) {
      // 返回占位对象，避免组件未初始化时报错
      return {
        addNotification: () => console.warn('通知组件尚未初始化'),
        updateNotificationByTaskId: () => console.warn('通知组件尚未初始化'),
        removeNotification: () => console.warn('通知组件尚未初始化'),
        clearAllNotifications: () => console.warn('通知组件尚未初始化'),
        startPolling: () => console.warn('通知组件尚未初始化'),
        startStream: () => console.warn('通知组件尚未初始化')
      };
    }
    return globalNotificationInstance;
  },
  
  /**
   * 设置全局实例
   */
  setInstance(instance) {
    globalNotificationInstance = instance;
  },
  
  /**
   * 显示加载中通知
   */
  loading(title, message, taskId) {
    const notificationId = this.getInstance().addNotification({
      title,
      message,
      type: 'loading',
      duration: 0,
      taskId
    });
    
    if (taskId && this.getInstance().startStream) {
      this.getInstance().startStream(taskId);
    } else if (taskId && this.getInstance().startPolling) {
      this.getInstance().startPolling(taskId);
    }
    
    return notificationId;
  },
  
  /**
   * 显示成功通知
   */
  success(title, message, duration = 2000) {
    return this.getInstance().addNotification({
      title,
      message,
      type: 'success',
      duration
    });
  },
  
  /**
   * 显示错误通知
   */
  error(title, message, duration = 5000) {
    return this.getInstance().addNotification({
      title,
      message,
      type: 'error',
      duration
    });
  },
  
  /**
   * 显示提示通知
   */
  info(title, message, duration = 3000, onClick = null) {
    return this.getInstance().addNotification({
      title,
      message,
      type: 'info',
      duration,
      onClick
    });
  },
  
  /**
   * 通过任务ID更新通知
   */
  updateByTaskId(taskId, updates) {
    return this.getInstance().updateNotificationByTaskId(taskId, updates);
  }
};

