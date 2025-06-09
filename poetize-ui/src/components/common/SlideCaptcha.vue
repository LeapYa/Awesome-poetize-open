<template>
  <div class="slide-captcha-wrapper">
    <div class="slide-captcha">
      <div class="slide-captcha-header">
        <div class="slide-captcha-title">
          安全验证
        </div>
        <div class="slide-captcha-close" @click="onClose">×</div>
        <div class="slide-captcha-subtitle">把它滑到右边去吧！</div>
      </div>
      
      <div class="slide-captcha-container">
        <div ref="slideTrack" class="slide-track">
          <div 
            ref="slideTrackFill" 
            class="slide-track-fill"
            :style="{ width: slidePosition + (isDragging ? buttonWidth/2 : 0) + 'px' }"
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
          :class="{ 'success': verified, 'active': isDragging }"
          :style="{ transform: `translateX(${slidePosition}px)` }"
          @mousedown.prevent="onDragStart"
          @touchstart.prevent="onDragStart"
        >
          <div class="slide-button-icon" v-if="!verified">></div>
          <div class="slide-button-icon success" v-else>√</div>
        </div>
      </div>
      
      <transition name="bounce">
        <div v-if="errorMsg" class="slide-message error">
          <i class="slide-message-icon">❌</i>
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
export default {
  name: 'SlideCaptcha',
  props: {
    // 精确度
    accuracy: {
      type: Number,
      default: 5
    },
    // 滑块文本
    sliderText: {
      type: String,
      default: '把滑块滑到右边~'
    },
    // 自定义背景图
    imgs: {
      type: Array,
      default: () => []
    },
    // 成功阈值
    successThreshold: {
      type: Number,
      default: 0.95 // 默认为95%
    }
  },
  data() {
    return {
      errorMsg: '',
      verified: false,
      screenWidth: window.innerWidth,
      slidePosition: 0,
      startX: 0,
      startLeft: 0,
      isDragging: false,
      maxSlideDistance: 0,
      buttonWidth: 44
    }
  },
  mounted() {
    // 监听窗口大小变化
    window.addEventListener('resize', this.updateScreenWidth);
    
    // 添加鼠标事件监听
    document.addEventListener('mousemove', this.onDragMove);
    document.addEventListener('mouseup', this.onDragEnd);
    
    // 添加触摸事件监听
    document.addEventListener('touchmove', this.onDragMove, { passive: false });
    document.addEventListener('touchend', this.onDragEnd);
    document.addEventListener('touchcancel', this.onDragEnd);
    
    // 计算最大滑动距离
    this.$nextTick(() => {
      this.updateMaxSlideDistance();
    });
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateScreenWidth);
    
    document.removeEventListener('mousemove', this.onDragMove);
    document.removeEventListener('mouseup', this.onDragEnd);
    
    document.removeEventListener('touchmove', this.onDragMove);
    document.removeEventListener('touchend', this.onDragEnd);
    document.removeEventListener('touchcancel', this.onDragEnd);
  },
  methods: {
    // 关闭验证
    onClose() {
      this.$emit('close');
    },
    
    // 更新屏幕宽度和计算最大滑动距离
    updateScreenWidth() {
      this.screenWidth = window.innerWidth;
      this.$nextTick(() => {
        this.updateMaxSlideDistance();
      });
    },
    
    // 计算最大滑动距离
    updateMaxSlideDistance() {
      if (this.$refs.slideTrack && this.$refs.slideButton) {
        const trackWidth = this.$refs.slideTrack.clientWidth;
        this.buttonWidth = this.$refs.slideButton.clientWidth;
        this.maxSlideDistance = trackWidth - this.buttonWidth;
      }
    },
    
    // 初始化验证码
    init() {
      this.slidePosition = 0;
      this.errorMsg = '';
      this.verified = false;
      this.isDragging = false;
      this.updateMaxSlideDistance();
    },
    
    // 拖动开始
    onDragStart(e) {
      if (this.verified) return;
      
      // 阻止默认事件和冒泡
      e.preventDefault();
      e.stopPropagation();
      
      this.isDragging = true;
      
      // 记录初始位置
      this.startX = e.clientX || (e.touches && e.touches[0].clientX) || 0;
      this.startLeft = this.slidePosition;
    },
    
    // 拖动中
    onDragMove(e) {
      if (!this.isDragging || this.verified) return;
      
      // 阻止默认滑动行为
      if (e.cancelable) {
        e.preventDefault();
      }
      
      // 计算移动距离
      const currentX = e.clientX || (e.touches && e.touches[0].clientX) || 0;
      const moveX = currentX - this.startX;
      
      // 计算新位置
      let newLeft = this.startLeft + moveX;
      
      // 限制在有效范围内
      if (newLeft < 0) {
        newLeft = 0;
      } else if (newLeft > this.maxSlideDistance) {
        newLeft = this.maxSlideDistance;
      }
      
      // 更新位置
      this.slidePosition = newLeft;
    },
    
    // 拖动结束
    onDragEnd() {
      if (!this.isDragging || this.verified) return;
      
      this.isDragging = false;
      
      // 判断是否验证成功
      if (this.slidePosition >= this.maxSlideDistance * this.successThreshold) {
        this.verifySuccess();
      } else {
        this.verifyFail();
      }
    },
    
    // 验证成功
    verifySuccess() {
      this.verified = true;
      this.errorMsg = '';
      
      // 设置为完全滑到末端
      this.slidePosition = this.maxSlideDistance;
      
      // 播放成功音效
      this.playSound('success');
      
      // 延时发送成功事件
      setTimeout(() => {
        this.$emit('success', 1);
      }, 600);
    },
    
    // 验证失败
    verifyFail() {
      this.verified = false;
      this.errorMsg = '没滑到终点哦，验证失败！';
      
      // 播放失败音效
      this.playSound('fail');
      
      // 动画返回起始位置
      const currentPosition = this.slidePosition;
      const duration = 400; // 动画时长
      const startTime = Date.now();
      
      const animateBack = () => {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const easeOut = this.bounceEaseOut(progress); // 弹跳效果
        
        this.slidePosition = currentPosition * (1 - easeOut);
        
        if (progress < 1) {
          requestAnimationFrame(animateBack);
        } else {
          this.slidePosition = 0;
          
          // 延时发送失败事件
          setTimeout(() => {
            this.errorMsg = '';
            this.$emit('fail');
          }, 1500);
        }
      };
      
      requestAnimationFrame(animateBack);
    },
    
    // 弹跳缓动函数
    bounceEaseOut(t) {
      const a = 4.0 / 11.0;
      const b = 8.0 / 11.0;
      const c = 9.0 / 10.0;
      
      const ca = 4356.0 / 361.0;
      const cb = 35442.0 / 1805.0;
      const cc = 16061.0 / 1805.0;
      
      const t2 = t * t;
      
      return t < a
        ? 7.5625 * t2
        : t < b
        ? 9.075 * t2 - 9.9 * t + 3.4
        : t < c
        ? ca * t2 - cb * t + cc
        : 10.8 * t * t - 20.52 * t + 10.72;
    },
    
    // 播放音效
    playSound(type) {
      try {
        // 如果需要，可以在这里添加音效播放逻辑
      } catch(e) {
        console.log('播放音效失败', e);
      }
    },
    
    // 刷新验证码
    refresh() {
      this.init();
      this.$emit('refresh');
    },
    
    // 重置验证码
    reset() {
      this.init();
    },
    
    // 外部获取验证状态
    isVerified() {
      return this.verified;
    }
  }
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

/* 头部样式 */
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
  color: #ff6b95;
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
  transition: all 0.2s;
  z-index: 10;
}

.slide-captcha-close:hover {
  color: #ff4778;
  background-color: #fff5f7;
}

.slide-captcha-subtitle {
  font-size: 14px;
  color: #8e9aaf;
  margin-top: 5px;
  width: 100%;
}

/* 滑动轨道 */
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
  background-color: #fff5f7;
  border-radius: 23px;
  box-shadow: inset 0 2px 8px rgba(255, 107, 149, 0.1);
  overflow: hidden;
  border: 2px solid #ffd6e0;
}

.slide-track-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, #ffd6e0, #ffb6c1);
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
  0% { opacity: 0.3; transform: scale(0.8); }
  100% { opacity: 1; transform: scale(1.2); }
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
  color: #ff6b95;
  font-weight: 500;
}

.slide-track-text.success i {
  font-style: normal;
  margin-left: 6px;
}

/* 滑动按钮 */
.slide-button {
  position: absolute;
  top: 1px;
  left: 0;
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #ff758c, #ff7eb3);
  border-radius: 50%;
  box-shadow: 0 4px 10px rgba(255, 118, 140, 0.4);
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
  box-shadow: 0 6px 16px rgba(255, 118, 140, 0.6);
  background: linear-gradient(135deg, #ff5277, #ff6897);
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
}

.slide-button.active .slide-button-icon {
  transform: scale(1.1);
}

.slide-button.success .slide-button-icon {
  animation: bounce 0.6s;
}

@keyframes bounce {
  0%, 20%, 50%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-10px); }
  60% { transform: translateY(-5px); }
}

/* 消息提示 */
.slide-message {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 14px;
  margin: 8px 0;
  padding: 8px 14px;
  border-radius: 18px;
  transition: all 0.3s;
}

.slide-message.error {
  color: #ff6b95;
  background-color: #fff5f7;
  border: 1px solid #ffd6e0;
}

.slide-message-icon {
  font-style: normal;
  margin-right: 6px;
  font-size: 16px;
}

/* 页脚样式 */
.slide-captcha-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ffd6e0;
}

.slide-captcha-refresh {
  display: flex;
  align-items: center;
  color: #ff6b95;
  font-size: 13px;
  cursor: pointer;
  transition: transform 0.2s;
}

.slide-refresh-icon {
  font-style: normal;
  margin-right: 4px;
  font-size: 14px;
}

.slide-captcha-refresh:hover {
  color: #ff4778;
  transform: scale(1.05);
}

.slide-captcha-powered {
  font-size: 12px;
  color: #8e9aaf;
}

/* 动画效果 */
.bounce-enter-active {
  animation: bounce-in 0.5s;
}
.bounce-leave-active {
  animation: bounce-in 0.5s reverse;
}
@keyframes bounce-in {
  0% { transform: scale(0.5); opacity: 0; }
  50% { transform: scale(1.05); }
  100% { transform: scale(1); opacity: 1; }
}

/* 响应式样式调整 */
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