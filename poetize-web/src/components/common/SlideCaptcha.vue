<template>
  <div class="slide-captcha-wrapper">
    <div class="slide-captcha">
      <div class="slide-captcha-header">
        <div class="slide-captcha-title">安全验证</div>
        <div class="slide-captcha-close" @click="onClose">×</div>
        <div class="slide-captcha-subtitle">把它滑到右边去吧！</div>
      </div>

      <div class="slide-captcha-container">
        <div ref="slideTrack" class="slide-track">
          <div
            ref="slideTrackFill"
            class="slide-track-fill"
            :style="{
              width:
                slidePosition +
                (isDragging || verified || isVerifying ? buttonWidth / 2 : 0) +
                'px',
            }"
          >
            <div class="track-stars" v-if="!verified">
              <span>✨</span>
              <span>✨</span>
              <span>✨</span>
            </div>
          </div>
          <div class="slide-track-text" v-if="!verified">{{ sliderText }}</div>
          <div class="slide-track-text success" v-else>验证成功！</div>
        </div>

        <div
          ref="slideButton"
          class="slide-button"
          :class="{ success: verified, active: isDragging }"
          :style="{ transform: `translateX(${slidePosition}px)` }"
          @mousedown.prevent="onDragStart"
          @touchstart.prevent="onDragStart"
        >
          <div class="slide-button-icon" v-if="!verified">
            <svg
              t="1761392312974"
              class="icon"
              viewBox="0 0 1024 1024"
              version="1.1"
              xmlns="http://www.w3.org/2000/svg"
              p-id="5450"
              width="24"
              height="24"
            >
              <path
                d="M761.055557 532.128047c0.512619-0.992555 1.343475-1.823411 1.792447-2.848649 8.800538-18.304636 5.919204-40.703346-9.664077-55.424808L399.935923 139.743798c-19.264507-18.208305-49.631179-17.344765-67.872168 1.888778-18.208305 19.264507-17.375729 49.631179 1.888778 67.872168l316.960409 299.839269L335.199677 813.631716c-19.071845 18.399247-19.648112 48.767639-1.247144 67.872168 9.407768 9.791372 21.984142 14.688778 34.560516 14.688778 12.000108 0 24.000215-4.479398 33.311652-13.439914l350.048434-337.375729c0.672598-0.672598 0.927187-1.599785 1.599785-2.303346 0.512619-0.479935 1.056202-0.832576 1.567101-1.343475C757.759656 538.879828 759.199462 535.391265 761.055557 532.128047z"
                fill="#ffffff"
                p-id="5451"
              ></path>
            </svg>
          </div>
          <div class="slide-button-icon success" v-else>
            <svg
              t="1761392930423"
              class="icon"
              viewBox="0 0 1024 1024"
              version="1.1"
              xmlns="http://www.w3.org/2000/svg"
              p-id="12241"
              width="24"
              height="24"
            >
              <path
                d="M380.343 801.646a53.394 53.394 0 0 1-36.572-16.092L125.44 568.32a55.589 55.589 0 0 1 0-77.166 54.126 54.126 0 0 1 76.8 0l178.103 179.2L835.29 272.091a53.394 53.394 0 0 1 76.435 0 54.126 54.126 0 0 1 0 76.8L418.743 785.554a54.491 54.491 0 0 1-38.4 16.092z"
                fill="#ffffff"
                p-id="12242"
              ></path>
            </svg>
          </div>
        </div>
      </div>

      <transition name="bounce">
        <div v-if="errorMsg" class="slide-message error">
          <i class="slide-message-icon">
            <svg
              t="1761392553630"
              class="icon"
              viewBox="0 0 1024 1024"
              version="1.1"
              xmlns="http://www.w3.org/2000/svg"
              p-id="7629"
              width="20"
              height="20"
            >
              <path
                d="M727 676.05a36.11 36.11 0 0 1 0 50.95 36.11 36.11 0 0 1-50.91 0L512 562.91 348 727a36.11 36.11 0 0 1-51 0 36.11 36.11 0 0 1 0-50.91l164-164L297 348a36.11 36.11 0 0 1 0-51 36.11 36.11 0 0 1 51 0l164 164.09 164-164a36.11 36.11 0 0 1 51-0.09 36.11 36.11 0 0 1 0 51L562.91 512z"
                fill="currentColor"
                p-id="7630"
              ></path>
            </svg>
          </i>
          {{ errorMsg }}
        </div>
      </transition>

      <div class="slide-captcha-footer">
        <div class="slide-captcha-refresh" @click="refresh">
          <i class="slide-refresh-icon">🔄</i>
          <span>重新开始</span>
        </div>
        <div class="slide-captcha-powered">
          <span>🌈 安全中心</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import axios from '@/utils/request'
import cryptoUtil from '@/utils/crypto'

export default {
  name: 'SlideCaptcha',
  props: {
    action: {
      type: String,
      default: 'login',
    },
    // 精确度
    accuracy: {
      type: Number,
      default: 5,
    },
    // 滑块文本
    sliderText: {
      type: String,
      default: '把滑块滑到右边~',
    },
    // 自定义背景图
    imgs: {
      type: Array,
      default: () => [],
    },
    // 成功阈值
    successThreshold: {
      type: Number,
      default: 0.95, // 默认为95%
    },
  },
  data() {
    return {
      errorMsg: '',
      verified: false,
      isVerifying: false, // 是否正在验证中
      screenWidth: window.innerWidth,
      slidePosition: 0,
      startX: 0,
      startLeft: 0,
      isDragging: false,
      maxSlideDistance: 0,
      buttonWidth: 44,
      slideTrack: [], // 滑动轨迹记录
      slideStartTime: 0, // 滑动开始时间
      browserFingerprint: null, // 浏览器指纹
      verificationToken: '', // 验证成功后的token
    }
  },
  async mounted() {
    // 获取浏览器指纹
    try {
      const { getBrowserFingerprint } = await import('@/utils/fingerprintUtil')
      this.browserFingerprint = await getBrowserFingerprint()
    } catch (error) {}

    // 原有的mounted逻辑
    // 监听窗口大小变化
    window.addEventListener('resize', this.updateScreenWidth)

    // 添加鼠标事件监听
    document.addEventListener('mousemove', this.onDragMove)
    document.addEventListener('mouseup', this.onDragEnd)

    // 添加触摸事件监听
    document.addEventListener('touchmove', this.onDragMove, { passive: false })
    document.addEventListener('touchend', this.onDragEnd)
    document.addEventListener('touchcancel', this.onDragEnd)

    // 计算最大滑动距离
    this.$nextTick(() => {
      this.updateMaxSlideDistance()
    })
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.updateScreenWidth)

    document.removeEventListener('mousemove', this.onDragMove)
    document.removeEventListener('mouseup', this.onDragEnd)

    document.removeEventListener('touchmove', this.onDragMove)
    document.removeEventListener('touchend', this.onDragEnd)
    document.removeEventListener('touchcancel', this.onDragEnd)
  },
  methods: {
    // 关闭验证
    onClose() {
      $emit(this, 'close')
    },

    // 更新屏幕宽度和计算最大滑动距离
    updateScreenWidth() {
      this.screenWidth = window.innerWidth
      this.$nextTick(() => {
        this.updateMaxSlideDistance()
      })
    },

    // 计算最大滑动距离
    updateMaxSlideDistance() {
      if (this.$refs.slideTrack && this.$refs.slideButton) {
        const trackWidth = this.$refs.slideTrack.clientWidth
        this.buttonWidth = this.$refs.slideButton.clientWidth
        this.maxSlideDistance = trackWidth - this.buttonWidth
      }
    },

    // 初始化验证码
    init() {
      this.slidePosition = 0
      this.errorMsg = ''
      this.verified = false
      this.isDragging = false
      this.verificationToken = '' // 重置token
      this.updateMaxSlideDistance()
    },

    // 拖动开始
    onDragStart(e) {
      if (this.verified) return

      // 阻止默认事件和冒泡
      e.preventDefault()
      e.stopPropagation()

      this.isDragging = true

      // 记录初始位置
      this.startX = e.clientX || (e.touches && e.touches[0].clientX) || 0
      this.startLeft = this.slidePosition

      // 初始化轨迹记录
      this.slideTrack = []
      this.slideStartTime = Date.now()

      // 记录起始点
      this.slideTrack.push({
        x: this.slidePosition,
        timestamp: this.slideStartTime,
      })
    },

    // 拖动中
    onDragMove(e) {
      if (!this.isDragging || this.verified) return

      // 阻止默认滑动行为
      if (e.cancelable) {
        e.preventDefault()
      }

      // 计算移动距离
      const currentX = e.clientX || (e.touches && e.touches[0].clientX) || 0
      const moveX = currentX - this.startX

      // 计算新位置
      let newLeft = this.startLeft + moveX

      // 限制在有效范围内
      if (newLeft < 0) {
        newLeft = 0
      } else if (newLeft > this.maxSlideDistance) {
        newLeft = this.maxSlideDistance
      }

      // 更新位置
      this.slidePosition = newLeft

      // 记录轨迹（限制记录点数）
      if (this.slideTrack.length < 100) {
        this.slideTrack.push({
          x: newLeft,
          timestamp: Date.now(),
        })
      }
    },

    // 拖动结束
    onDragEnd() {
      if (!this.isDragging || this.verified) return

      this.isDragging = false

      // 判断是否验证成功（距离检测）
      if (this.slidePosition >= this.maxSlideDistance * this.successThreshold) {
        // 发送轨迹到后端验证
        this.verifyWithServer()
      } else {
        this.verifyFail()
      }
    },

    // 计算滑动轨迹的平均速度
    calculateAverageSpeed(track = this.slideTrack) {
      if (!Array.isArray(track) || track.length < 2) return 0
      let totalDistance = 0
      let totalTime = 0
      for (let i = 1; i < track.length; i++) {
        const dx = track[i].x - track[i - 1].x
        const dt = track[i].timestamp - track[i - 1].timestamp
        totalDistance += Math.abs(dx)
        totalTime += dt
      }
      return totalTime > 0 ? totalDistance / totalTime : 0
    },

    // 计算回退次数
    calculateBacktrackCount(track = this.slideTrack) {
      if (!Array.isArray(track) || track.length < 2) return 0
      let count = 0
      for (let i = 1; i < track.length; i++) {
        if (track[i].x < track[i - 1].x) {
          count++
        }
      }
      return count
    },

    normalizeTrack(track, endTime, finalX) {
      if (!Array.isArray(track)) return []

      const normalized = track
        .filter(
          (p) =>
            p &&
            Number.isFinite(Number(p.timestamp)) &&
            Number.isFinite(Number(p.x))
        )
        .map((p) => ({
          x: Number(p.x),
          timestamp: Number(p.timestamp),
        }))
        .sort((a, b) => a.timestamp - b.timestamp)

      if (
        normalized.length === 0 ||
        !Number.isFinite(Number(endTime)) ||
        !Number.isFinite(Number(finalX))
      ) {
        return normalized
      }

      const lastPoint = normalized[normalized.length - 1]
      const finalTime = Number(endTime)
      const finalPosition = Number(finalX)

      if (finalTime > lastPoint.timestamp) {
        const endPoint = { x: finalPosition, timestamp: finalTime }
        if (normalized.length < 100) {
          normalized.push(endPoint)
        } else {
          normalized[normalized.length - 1] = endPoint
        }
      } else {
        lastPoint.x = finalPosition
      }

      return normalized
    },

    // 后端验证
    async verifyWithServer() {
      this.isVerifying = true // 开始验证，保持填充条宽度
      const endTime = Date.now()
      const normalizedTrack = this.normalizeTrack(
        this.slideTrack,
        endTime,
        this.slidePosition
      )
      const totalTime = endTime - this.slideStartTime

      // 前端统计数据，用于验证一致性
      const avgSpeed = this.calculateAverageSpeed(normalizedTrack)
      const backtrackCount = this.calculateBacktrackCount(normalizedTrack)
      const trackPointCount = normalizedTrack.length

      // 准备验证数据
      const verifyData = {
        slideTrack: normalizedTrack,
        totalTime: totalTime,
        maxDistance: this.maxSlideDistance,
        finalPosition: this.slidePosition,
        browserFingerprint: this.browserFingerprint,
        action: this.action,
        avgSpeed: avgSpeed,
        backtrackCount: backtrackCount,
        trackPointCount: trackPointCount,
      }

      // 加密请求数据 - 使用GCM模式
      let encryptedData
      try {
        encryptedData = await cryptoUtil.encrypt(verifyData)
      } catch (error) {
        // 加密失败处理（如 HTTP 环境下 Web Crypto API 不可用）
        console.error('加密失败:', error)

        // 检查是否是 HTTPS 相关错误
        const isHttpsError =
          error.code === 'CRYPTO_NOT_AVAILABLE' ||
          (error.message && error.message.includes('HTTPS'))
        const errorMessage = isHttpsError
          ? '安全验证需要 HTTPS 连接。请使用 HTTPS 访问此网站，或联系站长启用 HTTPS。'
          : '加密失败，请刷新页面重试'

        this.$message({
          message: errorMessage,
          type: 'error',
          duration: 5000,
          showClose: true,
          customClass: 'captcha-error-message',
        })

        const displayError = isHttpsError ? '需要 HTTPS 连接' : '加密失败'
        this.verifyFail(displayError)
        return
      }

      const encryptedRequest = {
        encrypted: encryptedData,
      }

      // 调用后端验证接口
      axios
        .post(
          this.$constant.baseURL + '/captcha/verify-slide',
          encryptedRequest
        )
        .then((res) => {
          // 处理返回的数据（支持两种格式）
          let responseData = res.data
          if (res.data && res.data.code === 200 && res.data.data) {
            responseData = res.data.data
          }

          // 检查是否被封禁
          if (responseData && responseData.blocked) {
            const remainingMinutes = responseData.remainingMinutes || 30
            const blockMessage = `验证失败次数过多，已被限制 ${remainingMinutes} 分钟`

            this.$message({
              message:
                responseData.message ||
                `您的IP已被临时限制 ${remainingMinutes} 分钟，请稍后再试`,
              type: 'error',
              duration: 5000,
              showClose: true,
            })

            // 使用自定义错误消息
            this.verifyFail(blockMessage)
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
          if (responseData && responseData.success) {
            // 保存后端返回的验证token
            this.verificationToken = responseData.token || ''
            this.verifySuccess()
          } else {
            const failMessage = responseData?.message || '验证失败，请重试'

            // 显示具体的失败原因
            if (responseData && responseData.message) {
              this.$message({
                message: responseData.message,
                type: 'error',
                duration: 3000,
              })
            }

            // 使用自定义错误消息
            this.verifyFail(failMessage)
          }
        })
        .catch((error) => {
          console.error('滑动验证失败:', error)
          this.$message({
            message: '验证请求失败，请检查网络连接',
            type: 'error',
            duration: 3000,
          })

          // 使用自定义错误消息
          this.verifyFail('网络错误，请重试')
        })
    },

    // 验证成功
    verifySuccess() {
      this.verified = true
      this.isVerifying = false // 验证结束
      this.errorMsg = ''

      // 设置为完全滑到末端
      this.slidePosition = this.maxSlideDistance

      // 播放成功音效
      this.playSound('success')

      // 延时发送成功事件，传递真正的验证token
      setTimeout(() => {
        $emit(this, 'success', this.verificationToken)
      }, 600)
    },

    // 验证失败
    verifyFail(customErrorMsg = null) {
      this.verified = false
      this.isVerifying = false // 验证结束
      // 如果没有自定义错误消息，使用默认消息
      if (customErrorMsg) {
        this.errorMsg = customErrorMsg
      } else if (!this.errorMsg) {
        // 只有在errorMsg为空时才设置默认消息
        this.errorMsg = '没滑到终点哦，验证失败！'
      }

      // 播放失败音效
      this.playSound('fail')

      // 动画返回起始位置
      const currentPosition = this.slidePosition
      const duration = 400 // 动画时长
      const startTime = Date.now()

      const animateBack = () => {
        const elapsed = Date.now() - startTime
        const progress = Math.min(elapsed / duration, 1)
        const easeOut = this.bounceEaseOut(progress) // 弹跳效果

        this.slidePosition = currentPosition * (1 - easeOut)

        if (progress < 1) {
          requestAnimationFrame(animateBack)
        } else {
          this.slidePosition = 0

          // 延时发送失败事件
          setTimeout(() => {
            this.errorMsg = ''
            $emit(this, 'fail')
          }, 1500)
        }
      }

      requestAnimationFrame(animateBack)
    },

    // 弹跳缓动函数
    bounceEaseOut(t) {
      const a = 4.0 / 11.0
      const b = 8.0 / 11.0
      const c = 9.0 / 10.0

      const ca = 4356.0 / 361.0
      const cb = 35442.0 / 1805.0
      const cc = 16061.0 / 1805.0

      const t2 = t * t

      return t < a
        ? 7.5625 * t2
        : t < b
        ? 9.075 * t2 - 9.9 * t + 3.4
        : t < c
        ? ca * t2 - cb * t + cc
        : 10.8 * t * t - 20.52 * t + 10.72
    },

    // 播放音效
    playSound(type) {
      try {
        // 如果需要，可以在这里添加音效播放逻辑
      } catch (e) {}
    },

    // 刷新验证码
    refresh() {
      this.init()
      $emit(this, 'refresh')
    },

    // 重置验证码
    reset() {
      this.init()
    },

    // 外部获取验证状态
    isVerified() {
      return this.verified
    },
  },
  emits: ['success', 'close', 'fail', 'refresh'],
}
</script>

<style scoped>
.slide-captcha-wrapper {
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 15px;
  box-sizing: border-box;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}
.slide-captcha {
  width: 100%;
  max-width: 320px;
  padding: 16px;
  box-sizing: border-box;
  border-radius: 12px;
  background-color: #fff;
}
.slide-captcha-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  position: relative;
}
.slide-captcha-title {
  font-size: 20px;
  font-weight: 600;
  /* 标题用中性深色，主题色只落在滑块等交互主体上 */
  color: #303133;
  flex-grow: 1;
}
.slide-captcha-close {
  position: absolute;
  top: -5px;
  right: -5px;
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
.slide-captcha-close:hover {
  color: #606266;
  background-color: #f2f3f5;
}
.slide-captcha-subtitle {
  font-size: 14px;
  color: #8e9aaf;
  margin-top: 5px;
  width: 100%;
}
.slide-captcha-container {
  position: relative;
  width: 100%;
  height: 46px;
  margin-bottom: 20px;
  touch-action: none;
}
.slide-track {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  /* 轨道用中性浅灰，让主题色填充与滑块成为唯一焦点 */
  background-color: #f7f8fa;
  border-radius: 23px;
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  border: 2px solid #e4e7ed;
}
.slide-track-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  /* 登录页现代样式下跟随主题色柔和变体，其他场景回退原版粉色 */
  background: var(--loginAccentSoft, linear-gradient(90deg, #ffd6e0, #ffb6c1));
  border-radius: 23px;
  transition: background-color 0.3s;
}
.track-stars {
  position: absolute;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: space-around;
  align-items: center;
  font-size: 12px;
  opacity: 0.6;
}
.track-stars span {
  animation: twinkle 1.5s infinite alternate;
}
.track-stars span:nth-child(2) {
  animation-delay: 0.5s;
}
.track-stars span:nth-child(3) {
  animation-delay: 1s;
}
@keyframes twinkle {
  0% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  100% {
    opacity: 1;
    transform: scale(1.2);
  }
}
.slide-track-text {
  position: absolute;
  width: 100%;
  height: 100%;
  text-align: center;
  line-height: 44px;
  color: #8e9aaf;
  font-size: 14px;
  user-select: none;
  transition: opacity 0.2s;
}
.slide-track-text.success {
  /* 成功文案与成功态滑块同为语义绿 */
  color: #67c23a;
  font-weight: 500;
}
.slide-track-text.success i {
  font-style: normal;
  margin-left: 6px;
}
.slide-button {
  position: absolute;
  top: 1px;
  left: 0;
  width: 44px;
  height: 44px;
  /* 登录页现代样式下跟随主题色，其他场景回退原版粉色 */
  background: var(--loginAccent, linear-gradient(135deg, #ff758c, #ff7eb3));
  border-radius: 50%;
  box-shadow: 0 4px 10px var(--loginAccentSoft, rgba(255, 118, 140, 0.4));
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  transform: translateX(0);
  transition: transform 0.05s linear, box-shadow 0.2s, background 0.2s;
  will-change: transform;
}
.slide-button.active {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25);
  background: var(--loginAccent, linear-gradient(135deg, #ff5277, #ff6897));
  filter: brightness(1.1);
}
.slide-button.success {
  background: linear-gradient(135deg, #b5ed5f, #75e075);
  box-shadow: 0 4px 10px rgba(120, 213, 120, 0.6);
}
.slide-button-icon {
  color: #fff;
  font-size: 24px;
  user-select: none;
  transition: transform 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.slide-button.active .slide-button-icon {
  transform: scale(1.1);
}
.slide-button.success .slide-button-icon {
  animation: bounce 0.6s;
}
@keyframes bounce {
  0%,
  20%,
  50%,
  80%,
  100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-10px);
  }
  60% {
    transform: translateY(-5px);
  }
}
.slide-message {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 14px;
  margin: 8px 0;
  padding: 8px 14px;
  border-radius: 18px;
  transition: color 0.3s ease, background-color 0.3s ease;
}
.slide-message.error {
  /* 错误提示回归语义红，不跟随主题色 */
  color: #f56c6c;
  background-color: #fef0f0;
  border: 1px solid #fde2e2;
}
.slide-message-icon {
  font-style: normal;
  margin-right: 6px;
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.slide-captcha-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}
.slide-captcha-refresh {
  display: flex;
  align-items: center;
  color: #909399;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s, transform 0.2s;
}
.slide-refresh-icon {
  font-style: normal;
  margin-right: 4px;
  font-size: 14px;
}
.slide-captcha-refresh:hover {
  color: var(--loginAccent, #ff6b95);
  transform: scale(1.05);
}
.slide-captcha-powered {
  font-size: 12px;
  color: #8e9aaf;
}
.bounce-enter-active {
  animation: bounce-in 0.5s;
}
.bounce-leave-active {
  animation: bounce-in 0.5s reverse;
}
@keyframes bounce-in {
  0% {
    transform: scale(0.5);
    opacity: 0;
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
@media screen and (max-width: 360px) {
  .slide-captcha {
    padding: 12px;
  }
  .slide-captcha-title {
    font-size: 18px;
  }
  .slide-captcha-subtitle {
    font-size: 12px;
  }
  .slide-captcha-container {
    height: 42px;
  }
  .slide-track {
    border-radius: 21px;
  }
  .slide-button {
    width: 40px;
    height: 40px;
  }
  .slide-track-text {
    font-size: 13px;
    line-height: 40px;
  }
}
</style>
