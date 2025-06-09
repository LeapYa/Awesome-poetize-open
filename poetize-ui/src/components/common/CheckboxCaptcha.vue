<template>
  <div class="checkbox-captcha-wrapper">
    <div 
      class="checkbox-captcha" 
      :class="{ 'verified': verified, 'error': showError }"
      @mousemove="trackMouseMovement"
    >
      <div class="captcha-header">
        <div class="captcha-icon">
          <i class="el-icon-lock"></i>
        </div>
        <div class="captcha-title">安全验证</div>
        <div class="captcha-close" @click="onClose">×</div>
      </div>
      
      <div class="checkbox-container">
        <label class="custom-checkbox-label">
          <input 
            type="checkbox" 
            id="robot-check" 
            v-model="checked"
            @change="onCheckChange"
            :disabled="verified || verifying"
          >
          <span class="checkmark"></span>
          <span class="checkbox-text">我不是机器人</span>
        </label>
      </div>
      
      <div class="captcha-info">
        <template v-if="verified">
          <i class="el-icon-check"></i> 验证成功
        </template>
        <template v-else-if="verifying">
          <i class="el-icon-loading"></i> 验证中...
        </template>
        <template v-else-if="showError">
          <i class="el-icon-warning"></i> 验证失败，请重试
        </template>
        <template v-else>
          <span>点击勾选框进行验证</span>
        </template>
      </div>
      
      <div class="captcha-footer">
        <span v-if="!verified && !verifying && !showError" class="refresh-btn" @click="refresh">
          <i class="el-icon-refresh"></i>
        </span>
        <span class="captcha-brand">安全验证</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'CheckboxCaptcha',
  props: {
    // 是否使用后端验证
    useServerVerify: {
      type: Boolean,
      default: true
    },
    // 操作类型
    action: {
      type: String,
      default: 'login'
    },
    // 轨迹敏感度阈值
    trackSensitivity: {
      type: Number,
      default: 0.98
    },
    // 最少轨迹点数
    minTrackPoints: {
      type: Number,
      default: 3
    }
  },
  data() {
    return {
      checked: false,
      verified: false,
      showError: false,
      mouseTrack: [],
      startTime: 0,
      checkTime: 0,
      verificationToken: '',
      verifying: false
    }
  },
  methods: {
    /**
     * 关闭验证
     */
    onClose() {
      this.$emit('close');
    },
    
    /**
     * 记录鼠标移动轨迹
     */
    trackMouseMovement(e) {
      if (this.verified || this.verifying) return;
      
      if (this.mouseTrack.length === 0) {
        this.startTime = Date.now();
      }
      
      // 限制记录点数，避免过多消耗内存
      if (this.mouseTrack.length < 20) {
        this.mouseTrack.push({
          x: e.clientX,
          y: e.clientY,
          time: Date.now()
        });
      }
    },
    
    /**
     * 勾选框状态变化
     */
    onCheckChange() {
      if (!this.checked || this.verifying) return;
      
      this.checkTime = Date.now();
      
      if (this.useServerVerify) {
        // 使用后端验证
        this.verifyWithServer();
      } else {
        // 使用前端验证
        if (this.isHumanLike()) {
          this.verifySuccess();
        } else {
          this.verifyFail();
        }
      }
    },
    
    /**
     * 与服务器通信进行验证
     */
    verifyWithServer() {
      if (this.verifying) return;
      this.verifying = true;
      
      // 计算直线率
      const straightRatio = this.calculateStraightRatio();
      
      // 准备发送到服务器的数据
      const verifyData = {
        mouseTrack: this.mouseTrack,
        straightRatio: straightRatio,
        timestamp: Date.now(),
        action: this.action  // 添加操作类型
      };
      
      // 调用验证接口
      this.$http.post(this.$constant.pythonBaseURL + "/captcha/verify-checkbox", verifyData)
        .then(res => {
          this.verifying = false;
          console.log("验证响应:", res.data);
          
          // 检查返回的数据格式
          if (res.data && res.data.success === true) {
            // 直接返回success和token的格式
            this.verificationToken = res.data.token;
            this.verifySuccess();
          } else if (res.data && res.data.code === 200 && res.data.data && res.data.data.token) {
            // 返回code和data的格式
            this.verificationToken = res.data.data.token;
            this.verifySuccess();
          } else {
            console.error("验证失败，服务器返回:", res.data);
            this.verifyFail();
          }
        })
        .catch(error => {
          this.verifying = false;
          console.error("验证请求失败:", error);
          this.verifyFail();
        });
    },
    
    /**
     * 计算鼠标轨迹直线率
     */
    calculateStraightRatio() {
      if (this.mouseTrack.length < 3) return 1;
      
      const firstPoint = this.mouseTrack[0];
      const lastPoint = this.mouseTrack[this.mouseTrack.length - 1];
      
      // 计算直线距离
      const directDistance = Math.sqrt(
        Math.pow(lastPoint.x - firstPoint.x, 2) + 
        Math.pow(lastPoint.y - firstPoint.y, 2)
      );
      
      // 计算实际路径长度
      let pathDistance = 0;
      for (let i = 1; i < this.mouseTrack.length; i++) {
        const prev = this.mouseTrack[i-1];
        const curr = this.mouseTrack[i];
        
        pathDistance += Math.sqrt(
          Math.pow(curr.x - prev.x, 2) + 
          Math.pow(curr.y - prev.y, 2)
        );
      }
      
      // 计算直线率（越接近1越直）
      return pathDistance > 0 ? directDistance / pathDistance : 1;
    },
    
    /**
     * 判断是否符合人类行为模式（前端验证）
     */
    isHumanLike() {
      // 1. 轨迹点数量检查
      if (this.mouseTrack.length < this.minTrackPoints) {
        return false;
      }
      
      // 2. 检查直线率
      const straightRatio = this.calculateStraightRatio();
      if (straightRatio > this.trackSensitivity) {
        return false;
      }
      
      // 3. 检查动作速度
      const timeSpent = this.checkTime - this.startTime;
      if (timeSpent < 500) { // 如果勾选太快（小于500ms），可能是机器
        return false;
      }
      
      return true;
    },
    
    /**
     * 验证成功
     */
    verifySuccess() {
      this.verified = true;
      this.showError = false;
      setTimeout(() => {
        this.$emit('success', this.verificationToken);
      }, 1000);
    },
    
    /**
     * 验证失败
     */
    verifyFail() {
      this.checked = false;
      this.verified = false;
      this.showError = true;
      this.verificationToken = '';
      
      setTimeout(() => {
        this.showError = false;
        this.$emit('fail');
      }, 2000);
    },
    
    /**
     * 刷新验证码
     */
    refresh() {
      this.checked = false;
      this.verified = false;
      this.showError = false;
      this.mouseTrack = [];
      this.startTime = 0;
      this.checkTime = 0;
      this.verificationToken = '';
      this.verifying = false;
      
      this.$emit('refresh');
    },
    
    /**
     * 重置验证码
     */
    reset() {
      this.refresh();
    },
    
    /**
     * 外部获取验证状态
     */
    isVerified() {
      return this.verified;
    },
    
    /**
     * 获取验证令牌
     */
    getToken() {
      return this.verificationToken;
    }
  }
}
</script>

<style scoped>
.checkbox-captcha-wrapper {
  width: 100%;
  max-width: 310px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.checkbox-captcha {
  width: 100%;
  padding: 20px;
  background-color: #fff;
  border: 2px solid #e5e5e5;
  border-radius: 16px;
  transition: all 0.3s;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  position: relative;
  overflow: hidden;
}

.captcha-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  position: relative;
}

.captcha-icon {
  font-size: 18px;
  color: #ff6b95;
  margin-right: 8px;
}

.captcha-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  flex-grow: 1;
}

.captcha-close {
  position: absolute;
  top: -10px;
  right: -10px;
  font-size: 24px;
  color: #8e9aaf;
  cursor: pointer;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
  z-index: 10;
}

.captcha-close:hover {
  color: #ff4778;
  background-color: #fff5f7;
}

.checkbox-captcha::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 5px;
  background: linear-gradient(90deg, #ff9a9e, #fad0c4);
  border-radius: 10px 10px 0 0;
}

.checkbox-captcha:hover {
  border-color: #ff9a9e;
  transform: translateY(-3px);
  box-shadow: 0 12px 25px rgba(0, 0, 0, 0.12);
}

.checkbox-captcha.verified {
  background-color: #f0f9eb;
  border-color: #67c23a;
}

.checkbox-captcha.verified::before {
  background: linear-gradient(90deg, #67c23a, #95d475);
}

.checkbox-captcha.verified .captcha-icon {
  background: linear-gradient(135deg, #67c23a, #95d475);
}

.checkbox-captcha.error {
  background-color: #fef0f0;
  border-color: #f56c6c;
}

.checkbox-captcha.error::before {
  background: linear-gradient(90deg, #f56c6c, #f78989);
}

.checkbox-captcha.error .captcha-icon {
  background: linear-gradient(135deg, #f56c6c, #f78989);
}

.checkbox-container {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.custom-checkbox-label {
  position: relative;
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  padding-left: 30px;
}

.custom-checkbox-label input {
  position: absolute;
  opacity: 0;
  cursor: pointer;
  height: 0;
  width: 0;
}

.checkmark {
  position: absolute;
  left: 0;
  height: 22px;
  width: 22px;
  background-color: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 6px;
  transition: all 0.3s;
}

.custom-checkbox-label:hover .checkmark {
  border-color: #ff9a9e;
}

.custom-checkbox-label input:checked ~ .checkmark {
  background-color: #ff9a9e;
  border-color: #ff9a9e;
}

.checkmark:after {
  content: "";
  position: absolute;
  display: none;
}

.custom-checkbox-label input:checked ~ .checkmark:after {
  display: block;
}

.custom-checkbox-label .checkmark:after {
  left: 7px;
  top: 3px;
  width: 5px;
  height: 10px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.checkbox-text {
  font-size: 16px;
  font-weight: 500;
  color: #606266;
  transition: all 0.3s;
}

.custom-checkbox-label:hover .checkbox-text {
  color: #ff9a9e;
}

.captcha-info {
  font-size: 14px;
  color: #606266;
  margin: 12px 0;
  height: 20px;
  line-height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.captcha-info i {
  margin-right: 5px;
  font-size: 16px;
}

.checkbox-captcha.verified .captcha-info {
  color: #67c23a;
  background-color: #f0f9eb;
}

.checkbox-captcha.error .captcha-info {
  color: #f56c6c;
  background-color: #fef0f0;
}

.captcha-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-top: 15px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}

.refresh-btn {
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background-color: #f5f7fa;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
}

.refresh-btn:hover {
  color: #ff9a9e;
  background-color: #fef0f0;
  transform: rotate(180deg);
}

.captcha-brand {
  font-style: italic;
  color: #c0c4cc;
  font-size: 11px;
  font-weight: 500;
}

/* 响应式样式 */
@media screen and (max-width: 768px) {
  .checkbox-captcha-wrapper {
    max-width: 95%;
    padding: 0 10px;
  }
  
  .checkbox-captcha {
    padding: 15px;
    border-radius: 12px;
  }
  
  .captcha-header {
    margin-bottom: 10px;
  }
  
  .captcha-icon {
    width: 24px;
    height: 24px;
  }
  
  .captcha-icon i {
    font-size: 14px;
  }
  
  .captcha-title {
    font-size: 14px;
  }
  
  .checkbox-text {
    font-size: 14px;
  }
  
  .checkmark {
    height: 18px;
    width: 18px;
  }
  
  .custom-checkbox-label .checkmark:after {
    left: 6px;
    top: 2px;
    width: 4px;
    height: 8px;
  }
  
  .captcha-info {
    font-size: 12px;
    padding: 6px;
  }
  
  .captcha-footer {
    margin-top: 10px;
  }
}
</style> 