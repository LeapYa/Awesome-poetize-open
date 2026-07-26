<template>
  <div class="checkbox-captcha-wrapper">
    <div
      class="checkbox-captcha"
      :class="{ verified: verified, error: showError }"
      @mousemove="trackMouseMovement"
      @touchstart="handleTouchStart"
      @touchmove="handleTouchMove"
    >
      <div class="captcha-header">
        <div class="captcha-icon">
          <el-icon><el-icon-lock /></el-icon>
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
          />
          <span class="checkmark"></span>
          <span class="checkbox-text">我不是机器人</span>
        </label>
      </div>

      <div class="captcha-info">
        <template v-if="verified">
          <el-icon><el-icon-check /></el-icon> 验证成功
        </template>
        <template v-else-if="verifying">
          <el-icon><el-icon-loading /></el-icon> 验证中...
        </template>
        <template v-else-if="showError">
          <el-icon><el-icon-warning /></el-icon> {{ errorMessage || '验证失败，请重试' }}
        </template>
        <template v-else>
          <span>点击勾选框进行验证</span>
        </template>
      </div>

      <div class="captcha-footer">
        <span
          v-if="!verified && !verifying && !showError"
          class="refresh-btn"
          @click="refresh"
        >
          <el-icon><el-icon-refresh /></el-icon>
        </span>
        <span class="captcha-brand">安全验证</span>
      </div>
    </div>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import {
  Lock as ElIconLock,
  Check as ElIconCheck,
  Loading as ElIconLoading,
  Warning as ElIconWarning,
  Refresh as ElIconRefresh,
} from '@element-plus/icons-vue'
import axios from '@/utils/request'
import cryptoUtil from '@/utils/crypto'

export default {
  components: {
    ElIconLock,
    ElIconCheck,
    ElIconLoading,
    ElIconWarning,
    ElIconRefresh,
  },
  name: 'CheckboxCaptcha',
  props: {
    // 是否使用后端验证（强制为true，前端验证不安全）
    // 保留此属性是为了向后兼容，但实际上总是使用后端验证
    useServerVerify: {
      type: Boolean,
      default: true,
    },
    // 操作类型
    action: {
      type: String,
      default: 'login',
    },
    // 轨迹敏感度阈值
    trackSensitivity: {
      type: Number,
      default: 0.9, // 进一步降低敏感度到0.90
    },
    // 最少轨迹点数
    minTrackPoints: {
      type: Number,
      default: 2, // 保持较低的轨迹点要求
    },
    // 是否为回复评论场景（更宽松的验证）
    isReplyComment: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      checked: false,
      verified: false,
      showError: false,
      errorMessage: '', // 动态错误消息
      mouseTrack: [],
      startTime: 0,
      checkTime: 0,
      verificationToken: '',
      verifying: false,
      isTrackingMouse: false, // 添加鼠标轨迹跟踪状态
      retryCount: 0, // 添加重试计数
      browserFingerprint: null, // 浏览器指纹
      isTouchDevice: false, // 是否为触屏设备
      touchTrack: [], // 触屏轨迹
      componentMountTime: 0, // 组件挂载时间
    }
  },
  async mounted() {
    // 记录组件挂载时间（用于计算用户思考时间）
    this.componentMountTime = Date.now()
    
    // 检测是否为触屏设备
    this.isTouchDevice = this.detectTouchDevice()
    
    // 组件加载时获取浏览器指纹
    try {
      const { getBrowserFingerprint } = await import('@/utils/fingerprintUtil')
      this.browserFingerprint = await getBrowserFingerprint()
    } catch (error) {}
  },
  methods: {
    /**
     * 检测是否为触屏设备
     */
    detectTouchDevice() {
      return (
        'ontouchstart' in window ||
        navigator.maxTouchPoints > 0 ||
        navigator.msMaxTouchPoints > 0 ||
        /iPad|iPhone|iPod|Android/i.test(navigator.userAgent)
      )
    },

    /**
     * 处理触屏开始事件
     */
    handleTouchStart(e) {
      if (this.verified || this.verifying) return
      
      this.isTouchDevice = true
      if (!this.isTrackingMouse) {
        this.isTrackingMouse = true
        this.startTime = Date.now()
      }
      
      if (e.touches && e.touches.length > 0) {
        const touch = e.touches[0]
        this.touchTrack.push({
          x: touch.clientX,
          y: touch.clientY,
          timestamp: Date.now(),
        })
      }
    },

    /**
     * 处理触屏移动事件
     */
    handleTouchMove(e) {
      if (this.verified || this.verifying) return
      
      if (e.touches && e.touches.length > 0 && this.touchTrack.length < 30) {
        const touch = e.touches[0]
        this.touchTrack.push({
          x: touch.clientX,
          y: touch.clientY,
          timestamp: Date.now(),
        })
      }
    },

    /**
     * 关闭验证
     */
    onClose() {
      $emit(this, 'close')
    },

    /**
     * 记录鼠标移动轨迹
     */
    trackMouseMovement(e) {
      if (this.verified || this.verifying) return

      // 开始轨迹跟踪
      if (!this.isTrackingMouse) {
        this.isTrackingMouse = true
        this.startTime = Date.now()
      }

      // 限制记录点数，避免过多消耗内存
      if (this.mouseTrack.length < 30) {
        // 增加记录点数上限
        this.mouseTrack.push({
          x: e.clientX,
          y: e.clientY,
          timestamp: Date.now(), // 使用timestamp字段（后端需要）
        })
      }
    },

    /**
     * 勾选框状态变化
     */
    onCheckChange() {
      if (!this.checked || this.verifying) return
      this.checkTime = Date.now()
      this.verifyWithServer()
    },

    /**
     * 与服务器通信进行验证
     */
    verifyWithServer() {
      if (this.verifying) return
      this.verifying = true

      const allTrack = this.normalizeTrack(
        [...this.mouseTrack, ...this.touchTrack],
        this.checkTime
      )

      const straightRatio = this.calculateStraightRatioByTrack(allTrack)
      const firstTimestamp =
        allTrack.length > 0 && allTrack[0] && Number.isFinite(Number(allTrack[0].timestamp))
          ? Number(allTrack[0].timestamp)
          : this.startTime || this.checkTime
      const clickDelay = this.checkTime - firstTimestamp
      const thinkingTime = this.checkTime - this.componentMountTime

      const verifyData = {
        mouseTrack: allTrack,
        browserFingerprint: this.browserFingerprint,
        timestamp: Date.now(),
        action: this.action,
        isReplyComment: this.isReplyComment,
        retryCount: this.retryCount,
        straightRatio: straightRatio,
        clickDelay: clickDelay,
        thinkingTime: thinkingTime,
        isTouchDevice: this.isTouchDevice,
      }

      axios
        .post(this.$constant.baseURL + '/captcha/verify-checkbox', verifyData)
        .then((res) => {
          this.verifying = false

          // 处理返回的数据（支持两种格式）
          let responseData = res.data
          if (res.data && res.data.code === 200 && res.data.data) {
            responseData = res.data.data
          }

          // 检查是否被封禁
          if (responseData && responseData.blocked) {
            const remainingMinutes = responseData.remainingMinutes || 30
            this.$message({
              message:
                responseData.message ||
                `您的IP已被临时限制 ${remainingMinutes} 分钟，请稍后再试`,
              type: 'error',
              duration: 5000,
              showClose: true,
            })
            this.verifyFail()
            return
          }

          // 显示警告信息（剩余次数少时）
          if (responseData && responseData.warning) {
            this.$message({
              message: responseData.warning,
              type: 'warning',
              duration: 4000,
              showClose: true,
            })
          }

          // 检查验证是否成功
          if (responseData && responseData.success === true) {
            this.verificationToken = responseData.token
            this.verifySuccess()
          } else {
            console.error('验证失败，服务器返回:', responseData)
            // 显示具体的失败原因
            if (responseData && responseData.message) {
              this.$message({
                message: responseData.message,
                type: 'error',
                duration: 3000,
              })
            }
            this.verifyFail()
          }
        })
        .catch((error) => {
          this.verifying = false
          console.error('验证请求失败:', error)
          
          // 检查是否是 HTTPS 相关错误
          const isHttpsError = error.isHttpsRequired || 
            (error.message && error.message.includes('HTTPS'))
          const errorMessage = isHttpsError
            ? '安全验证需要 HTTPS 连接。请使用 HTTPS 访问此网站，或联系站长启用 HTTPS。'
            : '验证请求失败，请检查网络连接'
          
          this.$message({
            message: errorMessage,
            type: 'error',
            duration: 5000,
            showClose: true,
            customClass: 'captcha-error-message',
          })
          
          const displayError = isHttpsError ? '需要 HTTPS 连接' : '网络错误'
          this.verifyFail(displayError)
        })
    },

    /**
     * 计算鼠标轨迹直线率
     */
    calculateStraightRatio() {
      return this.calculateStraightRatioByTrack(this.mouseTrack)
    },

    calculateStraightRatioByTrack(track) {
      if (!Array.isArray(track) || track.length < 3) return 1

      const firstPoint = track[0]
      const lastPoint = track[track.length - 1]

      const directDistance = Math.sqrt(
        Math.pow(lastPoint.x - firstPoint.x, 2) + Math.pow(lastPoint.y - firstPoint.y, 2)
      )

      let pathDistance = 0
      for (let i = 1; i < track.length; i++) {
        const prev = track[i - 1]
        const curr = track[i]

        pathDistance += Math.sqrt(
          Math.pow(curr.x - prev.x, 2) + Math.pow(curr.y - prev.y, 2)
        )
      }

      return pathDistance > 0 ? directDistance / pathDistance : 1
    },

    normalizeTrack(track, checkTime) {
      if (!Array.isArray(track)) return []

      const normalized = track
        .filter(
          (p) =>
            p &&
            Number.isFinite(Number(p.timestamp)) &&
            Number.isFinite(Number(p.x)) &&
            Number.isFinite(Number(p.y))
        )
        .map((p) => ({
          x: Number(p.x),
          y: Number(p.y),
          timestamp: Number(p.timestamp),
        }))
        .sort((a, b) => a.timestamp - b.timestamp)

      if (normalized.length === 0 || !Number.isFinite(Number(checkTime))) return normalized

      const lastPoint = normalized[normalized.length - 1]
      const clickTime = Number(checkTime)
      if (clickTime > lastPoint.timestamp) {
        const clickPoint = { ...lastPoint, timestamp: clickTime }
        if (normalized.length < 30) {
          normalized.push(clickPoint)
        } else {
          normalized[normalized.length - 1] = clickPoint
        }
      }

      return normalized
    },

    /**
     * 判断是否符合人类行为模式
     * 注意：此方法仅用于前端预检和数据收集，不作为最终验证依据
     * 真正的安全验证在后端进行，后端会：
     * 1. 验证轨迹数据的合理性
     * 2. 检查请求频率和IP限制
     * 3. 结合浏览器指纹进行风控
     * 4. 生成一次性token防止重放攻击
     */
    isHumanLike() {
      // 合并鼠标和触屏轨迹
      const allTrack = [...this.mouseTrack, ...this.touchTrack]
      
      // 计算用户思考时间（从组件挂载到点击）
      const thinkingTime = this.checkTime - this.componentMountTime
      
      // 计算操作时间（从开始移动/触摸到点击）
      const operationTime = this.checkTime - this.startTime

      // 触屏设备特殊处理
      if (this.isTouchDevice) {
        // 触屏设备：只要有思考时间就认为是人类
        // 因为触屏用户可能直接点击，没有移动轨迹
        if (thinkingTime >= 300) {
          return true
        }
        // 如果有触屏轨迹，降低要求
        if (this.touchTrack.length >= 1) {
          return true
        }
      }

      // 1. 智能时间检查
      // 场景1：用户有足够的思考时间（组件显示后等待了一会儿）
      if (thinkingTime >= 800) {
        // 用户看了组件0.8秒以上，即使操作很快也可能是人类
        // 只需要基本的轨迹检查
        if (allTrack.length >= 1 || this.isReplyComment) {
          return true
        }
      }

      // 场景2：操作时间在合理范围内
      const minOperationTime = this.getMinOperationTime()
      if (operationTime >= minOperationTime) {
        // 2. 轨迹点数量检查 - 根据场景调整要求
        const minPoints = this.getMinTrackPoints()
        if (allTrack.length >= minPoints) {
          // 3. 检查直线率 - 根据场景调整敏感度
          const straightRatio = this.calculateStraightRatio()
          const sensitivity = this.getTrackSensitivity()
          if (straightRatio <= sensitivity || allTrack.length < 3) {
            return true
          }
        }
      }

      // 场景3：快速但有合理轨迹的用户
      // 有些用户鼠标本来就在附近，移动很快但轨迹是自然的
      if (operationTime >= 150 && allTrack.length >= 3) {
        const straightRatio = this.calculateStraightRatio()
        // 轨迹不是完全直线就通过
        if (straightRatio < 0.95) {
          return true
        }
      }

      // 场景4：回复评论等宽松场景
      if (this.isReplyComment && thinkingTime >= 200) {
        return true
      }

      return false
    },

    /**
     * 获取最小操作时间（根据场景调整）
     */
    getMinOperationTime() {
      // 触屏设备几乎不需要操作时间
      if (this.isTouchDevice) {
        return 50
      }
      
      // 回复评论场景放宽要求
      if (this.isReplyComment) {
        return 150
      }
      
      // 重试时逐渐降低要求
      const baseTime = 300
      const reduction = this.retryCount * 50
      return Math.max(100, baseTime - reduction)
    },

    /**
     * 获取最少轨迹点数（根据场景调整）
     */
    getMinTrackPoints() {
      // 触屏设备可能没有轨迹
      if (this.isTouchDevice) {
        return 0
      }
      
      if (this.isReplyComment) {
        return 0 // 回复评论场景不强制要求轨迹
      }
      
      // 重试时降低要求
      const basePoints = Math.max(1, this.minTrackPoints)
      return Math.max(0, basePoints - this.retryCount)
    },

    /**
     * 获取轨迹敏感度（根据场景和重试次数调整）
     */
    getTrackSensitivity() {
      let sensitivity = this.trackSensitivity

      // 回复评论场景大幅降低敏感度
      if (this.isReplyComment) {
        sensitivity = Math.max(0.75, sensitivity - 0.1) // 回复评论场景更宽松
      }

      // 重试次数越多，要求越宽松，递减幅度更大
      if (this.retryCount > 0) {
        const decrement = this.retryCount * 0.04 // 每次重试降低0.04
        sensitivity = Math.max(0.7, sensitivity - decrement) // 最低降到0.70
      }

      return sensitivity
    },

    /**
     * 验证成功
     */
    verifySuccess() {
      this.verified = true
      this.showError = false
      setTimeout(() => {
        $emit(this, 'success', this.verificationToken)
      }, 1000)
    },

    /**
     * 验证失败
     */
    verifyFail(customMessage = null) {
      this.checked = false
      this.verified = false
      this.showError = true
      this.errorMessage = customMessage || '验证失败，请重试'
      this.verificationToken = ''
      this.retryCount++ // 增加重试计数

      // 重置轨迹跟踪状态，准备下次验证
      this.resetTrackingState()

      setTimeout(() => {
        this.showError = false
        this.errorMessage = ''
        $emit(this, 'fail')
      }, 3000)
    },

    /**
     * 重置轨迹跟踪状态
     */
    resetTrackingState() {
      this.mouseTrack = []
      this.touchTrack = []
      this.startTime = 0
      this.checkTime = 0
      this.isTrackingMouse = false
    },

    /**
     * 刷新验证码
     */
    refresh() {
      this.checked = false
      this.verified = false
      this.showError = false
      this.errorMessage = ''
      this.mouseTrack = []
      this.touchTrack = []
      this.startTime = 0
      this.checkTime = 0
      this.verificationToken = ''
      this.verifying = false
      this.isTrackingMouse = false
      this.retryCount = 0 // 重置重试计数
      this.componentMountTime = Date.now() // 重置挂载时间

      $emit(this, 'refresh')
    },

    /**
     * 重置验证码
     */
    reset() {
      this.refresh()
    },

    /**
     * 外部获取验证状态
     */
    isVerified() {
      return this.verified
    },

    /**
     * 获取验证令牌
     */
    getToken() {
      return this.verificationToken
    },
  },
  emits: ['success', 'close', 'fail', 'refresh'],
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
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
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
  /* 登录页现代样式下跟随主题色，其他场景回退原版粉色 */
  color: var(--loginAccent, #ff6b95);
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
  transition: background-color 0.2s ease, color 0.2s ease;
  z-index: 10;
}
.captcha-close:hover {
  color: #606266;
  background-color: #f2f3f5;
}
.checkbox-captcha::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 5px;
  /* 登录页现代样式下跟随主题色，其他场景回退原版粉色 */
  background: var(--loginAccent, linear-gradient(90deg, #ff9a9e, #fad0c4));
  border-radius: 10px 10px 0 0;
}
.checkbox-captcha:hover {
  /* 悬停边框保持中性，主题色只落在顶部色条与复选框上 */
  border-color: #dcdfe6;
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
  transition: border-color 0.3s ease, background-color 0.3s ease;
}
.custom-checkbox-label:hover .checkmark {
  border-color: var(--loginAccent, #ff9a9e);
}
.custom-checkbox-label input:checked ~ .checkmark {
  background-color: var(--loginAccent, #ff9a9e);
  border-color: var(--loginAccent, #ff9a9e);
}
.checkmark:after {
  content: '';
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
  transition: color 0.3s ease;
}
.custom-checkbox-label:hover .checkbox-text {
  color: var(--loginAccent, #ff9a9e);
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
  transition: color 0.3s ease, background-color 0.3s ease;
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
  color: var(--loginAccent, #ff9a9e);
  background-color: #f2f3f5;
  transform: rotate(180deg);
}
.captcha-brand {
  font-style: italic;
  color: #c0c4cc;
  font-size: 11px;
  font-weight: 500;
}
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
