<template>
    <div class="slider-verify-container" v-show="visible" :style="{ zIndex: zIndex }">
      <div class="slider-verify-mask" @click="handleClose" v-if="type === 'modal'"></div>
      <div class="slider-verify-content" :class="[type, className]">
        <div class="slider-verify-header">
          <span class="slider-verify-title">安全验证</span>
          <div class="slider-verify-actions">
            <span class="slider-verify-close" @click="handleClose" v-if="type === 'modal'"><i class="el-icon-close"></i></span>
          </div>
        </div>
        <div class="slider-verify-body">
          <div class="verify-tip-container">
            <div class="verify-tip-text">为了保证您的账号安全，请完成下方验证</div>
          </div>
          <div 
            ref="sliderContainer"
            class="slider-verify-drag-container"
          >
            <div 
              class="slider-verify-bg"
              :class="{ success: blockState === 2, error: showError }"
            >
              <span v-if="blockState !== 2 && !showError">{{ sliderText }}</span>
              <span v-else-if="showError" class="error-text">
                <i class="el-icon-warning"></i> {{ failText }}
              </span>
              <span v-else class="success-text">
                <i class="el-icon-check"></i> {{ successText }}
              </span>
            </div>
            <div
              @mousedown="onMouseDown"
              @touchstart="onTouchStart"
              :style="{ left: sliderPosition }"
              class="slider-verify-block"
              :class="{ success: blockState === 2, error: showError, 'block-active': isDragging }"
            >
              <div class="slider-block-container">
                <div class="slider-block-arrow">
                  <i class="el-icon-arrow-right"></i>
                </div>
              </div>
            </div>
            <div :style="{ width: `calc(${sliderPosition} + ${sliderSize/2}px)` }" class="slider-verify-progress" :class="{ error: showError }"></div>
          </div>
        </div>
      </div>
    </div>
  </template>
  
  <script>
  export default {
    name: 'SliderVerify',
    props: {
      show: {
        type: Boolean,
        default: false
      },
      type: {
        type: String,
        default: 'modal', // 'modal' or 'inside'
        validator: (value) => ['modal', 'inside'].includes(value)
      },
      containerWidth: {
        type: Number,
        default: 350
      },
      sliderSize: {
        type: Number,
        default: 40
      },
      successText: {
        type: String,
        default: '完成验证'
      },
      failText: {
        type: String,
        default: '滑动失败，请重新验证'
      },
      sliderText: {
        type: String,
        default: '请按住滑块，拖动到最右边'
      },
      className: {
        type: String,
        default: ''
      },
      zIndex: {
        type: Number,
        default: 999
      },
      verifyApi: {
        type: String,
        default: ''
      }
    },
    data() {
      return {
        visible: false,
        sliderPosition: '0px',
        blockState: 0, // 0: 初始, 1: 拖动中, 2: 验证成功
        startPosition: null,
        maxPosition: 0,
        showError: false,
        isDragging: false,
        lastPosition: 0,
        animationFrameId: null,
        
        startTime: 0,
        endTime: 0,
        lastMoveTime: 0,
        lastX: 0,
        moveTrack: [],
        velocities: [],
        accelerations: [],
        totalDistance: 0,
        dragAttempts: 0,
        jitterCount: 0,
        straightLineRatio: 0,
        
        thresholds: {
          minDragTime: 150,
          maxDragTime: 15000,
          suspiciousVelocity: 3000,
          minVelocityChanges: 3,
          minJitter: 0,
          maxStraightLineRatio: 0.98,
        }
      };
    },
    watch: {
      show(val) {
        this.visible = val;
        if (val) {
          this.$nextTick(() => {
            this.init();
          });
        }
      }
    },
    mounted() {
      this.visible = this.show;
      if (this.visible) {
        this.init();
      }
      
      document.addEventListener('mousemove', this.onGlobalMouseMove);
      document.addEventListener('mouseup', this.onGlobalMouseUp);
      document.addEventListener('touchmove', this.onGlobalTouchMove, { passive: false });
      document.addEventListener('touchend', this.onGlobalTouchEnd);
    },
    beforeDestroy() {
      document.removeEventListener('mousemove', this.onGlobalMouseMove);
      document.removeEventListener('mouseup', this.onGlobalMouseUp);
      document.removeEventListener('touchmove', this.onGlobalTouchMove);
      document.removeEventListener('touchend', this.onGlobalTouchEnd);
      
      if (this.animationFrameId) {
        cancelAnimationFrame(this.animationFrameId);
      }
    },
    methods: {
      init() {
        this.$nextTick(() => {
          if (this.$refs.sliderContainer) {
            this.maxPosition = this.$refs.sliderContainer.clientWidth - this.sliderSize;
          }
          this.resetSlider();
        });
      },
      
      resetSlider() {
        this.sliderPosition = '0px';
        this.blockState = 0;
        this.startPosition = null;
        this.showError = false;
        this.isDragging = false;
        this.lastPosition = 0;
        
        this.startTime = 0;
        this.endTime = 0;
        this.lastMoveTime = 0;
        this.lastX = 0;
        this.moveTrack = [];
        this.velocities = [];
        this.accelerations = [];
        this.totalDistance = 0;
        this.dragAttempts = 0;
        this.jitterCount = 0;
        this.straightLineRatio = 0;
      },
      
      onMouseDown(e) {
        if (this.blockState !== 2) {
          this.blockState = 1;
          this.showError = false;
          this.isDragging = true;
          this.startPosition = {
            clientX: e.clientX
          };
          this.lastPosition = parseInt(this.sliderPosition) || 0;
          
          this.startTime = Date.now();
          this.lastMoveTime = this.startTime;
          this.lastX = e.clientX;
          this.moveTrack = [{ x: e.clientX, y: e.clientY, t: this.startTime }];
          this.dragAttempts++;
        }
        e.preventDefault();
      },
      
      onTouchStart(e) {
        if (e.touches.length === 1 && this.blockState !== 2) {
          this.blockState = 1;
          this.showError = false;
          this.isDragging = true;
          this.startPosition = {
            clientX: e.touches[0].clientX
          };
          this.lastPosition = parseInt(this.sliderPosition) || 0;
          
          this.startTime = Date.now();
          this.lastMoveTime = this.startTime;
          this.lastX = e.touches[0].clientX;
          this.moveTrack = [{ 
            x: e.touches[0].clientX, 
            y: e.touches[0].clientY, 
            t: this.startTime 
          }];
          this.dragAttempts++;
        }
        e.preventDefault();
      },
      
      onGlobalMouseMove(e) {
        if (this.isDragging && this.blockState === 1) {
          this.updatePosition(e.clientX, e.clientY);
        }
      },
      
      onGlobalTouchMove(e) {
        if (this.isDragging && this.blockState === 1 && e.touches.length === 1) {
          this.updatePosition(e.touches[0].clientX, e.touches[0].clientY);
          e.preventDefault();
        }
      },
      
      updatePosition(clientX, clientY) {
        if (!this.startPosition || !this.isDragging) return;
        
        if (this.animationFrameId) {
          cancelAnimationFrame(this.animationFrameId);
        }
        
        this.animationFrameId = requestAnimationFrame(() => {
          const now = Date.now();
          const timeDiff = now - this.lastMoveTime;
          
          this.moveTrack.push({ x: clientX, y: clientY, t: now });
          
          const distance = Math.abs(clientX - this.lastX);
          this.totalDistance += distance;
          
          if (timeDiff > 0) {
            const velocity = distance / (timeDiff / 1000);
            this.velocities.push(velocity);
            
            if (this.velocities.length >= 2) {
              const currentVelocity = this.velocities[this.velocities.length - 1];
              const prevVelocity = this.velocities[this.velocities.length - 2];
              const acceleration = (currentVelocity - prevVelocity) / (timeDiff / 1000);
              this.accelerations.push(acceleration);
              
              if ((prevVelocity > 0 && currentVelocity < 0) || 
                  (prevVelocity < 0 && currentVelocity > 0)) {
                this.jitterCount++;
              }
            }
          }
          
          this.lastX = clientX;
          this.lastMoveTime = now;
          
          let width = clientX - this.startPosition.clientX;
          let newPos = this.lastPosition + width;
          
          if (newPos < 0) {
            newPos = 0;
          } else if (newPos > this.maxPosition) {
            newPos = this.maxPosition;
            this.endTime = now;
            this.verifySlide();
          }
          
          this.sliderPosition = newPos + 'px';
        });
      },
      
      verifySlide() {
        this.calculateStraightLineRatio();
        
        const dragTime = this.endTime - this.startTime;
        
        if (this.isHumanLike() && 
            (this.verifyApi ? this.verifyWithServer() : true)) {
          this.blockState = 2;
          this.isDragging = false;
          this.$emit('success');
        } else {
          this.showError = true;
          this.blockState = 0;
          
          this.animationFrameId = requestAnimationFrame(() => {
            this.sliderPosition = '0px';
            setTimeout(() => {
              this.showError = false;
            }, 1500);
          });
          
          this.$emit('fail');
        }
      },
      
      calculateStraightLineRatio() {
        if (this.moveTrack.length < 3) return 0;
        
        const start = this.moveTrack[0];
        const end = this.moveTrack[this.moveTrack.length - 1];
        const directDistance = Math.sqrt(
          Math.pow(end.x - start.x, 2) + 
          Math.pow(end.y - start.y, 2)
        );
        
        let pathDistance = 0;
        for (let i = 1; i < this.moveTrack.length; i++) {
          const curr = this.moveTrack[i];
          const prev = this.moveTrack[i-1];
          pathDistance += Math.sqrt(
            Math.pow(curr.x - prev.x, 2) + 
            Math.pow(curr.y - prev.y, 2)
          );
        }
        
        this.straightLineRatio = pathDistance > 0 ? directDistance / pathDistance : 0;
        return this.straightLineRatio;
      },
      
      isHumanLike() {
        const dragTime = this.endTime - this.startTime;
        
        if (dragTime < this.thresholds.minDragTime || 
            dragTime > this.thresholds.maxDragTime) {
          console.log('验证失败: 拖动时间', dragTime);
          return false;
        }
        
        if (this.velocities.length < this.thresholds.minVelocityChanges) {
          console.log('验证失败: 速度变化次数不足', this.velocities.length);
          return false;
        }
        
        const hasAbnormalVelocity = this.velocities.some(v => 
          v > this.thresholds.suspiciousVelocity
        );
        if (hasAbnormalVelocity) {
          console.log('验证失败: 存在异常高速');
          return false;
        }
        
        if (this.straightLineRatio > this.thresholds.maxStraightLineRatio) {
          console.log('验证失败: 轨迹过于直线', this.straightLineRatio);
          return false;
        }
        
        return true;
      },
      
      verifyWithServer() {
        if (!this.verifyApi) return true;
        
        try {
          const verifyData = {
            dragTime: this.endTime - this.startTime,
            moveTrack: this.moveTrack,
            velocities: this.velocities,
            accelerations: this.accelerations,
            totalDistance: this.totalDistance,
            straightLineRatio: this.straightLineRatio,
            jitterCount: this.jitterCount,
            dragAttempts: this.dragAttempts,
            timestamp: Date.now(),
            token: this.generateToken()
          };
          
          this.$emit('verify-data', verifyData);
          
          return true;
        } catch (error) {
          console.error('Verification data collection error:', error);
          return false;
        }
      },
      
      generateToken() {
        return Math.random().toString(36).substring(2) + Date.now().toString(36);
      },
      
      onGlobalMouseUp() {
        this.endDrag();
      },
      
      onGlobalTouchEnd() {
        this.endDrag();
      },
      
      endDrag() {
        if (!this.isDragging) return;
        
        this.endTime = Date.now();
        this.isDragging = false;
        
        if (this.blockState === 1) {
          this.showError = true;
          
          if (this.animationFrameId) {
            cancelAnimationFrame(this.animationFrameId);
          }
          
          this.animationFrameId = requestAnimationFrame(() => {
            this.sliderPosition = '0px';
            this.blockState = 0;
            
            setTimeout(() => {
              this.showError = false;
            }, 1500);
          });
          
          this.$emit('fail');
        }
        
        if (this.blockState === 2) {
          setTimeout(() => {
            this.$emit('close');
          }, 1000);
        }
      },
      
      reset() {
        this.resetSlider();
        this.$emit('reset');
      },
      
      handleClose() {
        this.visible = false;
        this.$emit('close');
      }
    }
  };
  </script>
  
  <style scoped>
  .slider-verify-container {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 9999;
  }
  
  .slider-verify-mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(5px);
    -webkit-backdrop-filter: blur(5px);
  }
  
  .slider-verify-content {
    position: relative;
    background-color: #fff;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 15px 40px rgba(0, 0, 0, 0.25);
    min-width: 380px;
    max-width: 90%;
    transition: all 0.3s ease;
    border: 1px solid rgba(0, 0, 0, 0.05);
    background-image: 
      radial-gradient(circle at 95% 15%, rgba(204, 44, 49, 0.05) 5%, transparent 30%),
      radial-gradient(circle at 5% 85%, rgba(204, 44, 49, 0.05) 5%, transparent 30%);
  }
  
  .slider-verify-content.inside {
    position: relative;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  }
  
  .slider-verify-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 18px 22px;
    border-bottom: 1px solid #f0f0f0;
    background: linear-gradient(135deg, #f8f9fa 0%, #f0f3f7 100%);
    position: relative;
  }
  
  .slider-verify-header::after {
    content: "";
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 4px;
    background: linear-gradient(90deg, 
      transparent 0%,
      rgba(204, 44, 49, 0.1) 15%,
      rgba(204, 44, 49, 0.2) 50%,
      rgba(204, 44, 49, 0.1) 85%,
      transparent 100%
    );
  }
  
  .slider-verify-title {
    font-size: 18px;
    font-weight: 600;
    color: #222;
    letter-spacing: 0.5px;
    display: flex;
    align-items: center;
  }
  
  .slider-verify-title::before {
    content: "";
    display: inline-block;
    width: 20px;
    height: 20px;
    margin-right: 8px;
    background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="%23cc2c31"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 6c1.4 0 2.8 1.1 2.8 2.5V11c.6 0 1.2.6 1.2 1.3v3.5c0 .6-.6 1.2-1.3 1.2H9.2c-.6 0-1.2-.6-1.2-1.3v-3.5c0-.6.6-1.2 1.2-1.2V9.5C9.2 8.1 10.6 7 12 7zm0 1.2c-.8 0-1.5.5-1.5 1.3V11h3V9.5c0-.8-.7-1.3-1.5-1.3z"/></svg>');
    background-repeat: no-repeat;
    background-position: center;
    background-size: contain;
  }
  
  .slider-verify-actions {
    display: flex;
    align-items: center;
  }
  
  .slider-verify-refresh, 
  .slider-verify-close {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 34px;
    height: 34px;
    cursor: pointer;
    border-radius: 50%;
    transition: all 0.3s ease;
    color: #555;
  }
  
  .slider-verify-refresh:hover, 
  .slider-verify-close:hover {
    background-color: rgba(0, 0, 0, 0.06);
    color: #000;
  }
  
  .slider-verify-close {
    margin-left: 8px;
  }
  
  .slider-verify-body {
    padding: 30px 28px;
    background: linear-gradient(180deg, #ffffff 0%, #fafbfd 100%);
    position: relative;
  }
  
  .slider-verify-body::before,
  .slider-verify-body::after {
    content: "";
    position: absolute;
    width: 120px;
    height: 120px;
    background-repeat: no-repeat;
    background-size: contain;
    opacity: 0.04;
    z-index: 1;
  }
  
  .slider-verify-body::before {
    top: 20px;
    left: 20px;
    background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" fill="%23cc2c31"><path d="M256 64C150 64 64 150 64 256s86 192 192 192 192-86 192-192S362 64 256 64zm-32 320c-17.7 0-32-14.3-32-32s14.3-32 32-32 32 14.3 32 32-14.3 32-32 32zm40-128c0 13.3-10.7 24-24 24h-16c-13.3 0-24-10.7-24-24v-64c0-13.3 10.7-24 24-24h16c13.3 0 24 10.7 24 24v64z"/></svg>');
    transform: rotate(-10deg);
  }
  
  .slider-verify-body::after {
    bottom: 20px;
    right: 20px;
    background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 512" fill="%23cc2c31"><path d="M632 248H217.7c-36.7 57.7-95.1 96-162.9 96-5.8 0-11.6-.3-17.2-.9l97.7 119.9c8.2 11 24.1 13.2 37 5.6l50.4-29.9c12.9-7.6 29.9-4.5 38.7 7.6l19.2 25.6c8.8 12.1 25.7 15.2 38.7 7.6l81.2-48.1c12.9-7.6 18-21.9 11.3-34.3l-35.5-64.1c-6.7-12.3-1.6-26.6 11.3-34.3l79.4-47c12.9-7.6 16.9-23.5 8.7-35.5L611.5 250c-8.2-12.1-25.1-10.1-32.5 1.4l-2.5 3.2c-4.5 5.7-13.5 6.4-19 1.6l-5.8-5c-5.4-4.9-14.3-4.1-18.8 1.6l-2.1 3.2c-4.8 5.9-13.8 6.4-19.2 1.2l-8.1-7.8c-4.6-5.7-13.5-6.4-19-1.6l-2.5 3.2c-4.5 5.7-13.5 6.4-19 1.6l-5.8-5c-5.4-4.9-14.3-4.1-18.8 1.6l-2.1 3.2c-4.5 5.7-13.5 6.4-19 1.6l-5.8-5c-5.4-4.8-14.2-4.1-18.8 1.6l-2.9 3.5c-4.7 5.9-13.7 6.5-19.1 1.3l-6.5-6.2c-5.3-5-14.1-5-19.4 0L354 260.4c-4 3.9-10.4 3.9-14.5 0l-9.6-9.2c-5.3-5-14.1-5-19.4 0l-9.6 9.2c-4 3.9-10.4 3.9-14.5 0l-9.7-9.3c-5.3-5-14.1-5-19.4 0l-3.9 3.7z"/></svg>');
    transform: rotate(10deg);
  }
  
  .verify-tip-container {
    margin-bottom: 20px;
    text-align: center;
    position: relative;
    z-index: 2;
  }
  
  .verify-tip-text {
    display: inline-block;
    padding: 12px 20px;
    background-color: rgba(204, 44, 49, 0.06);
    border-radius: 8px;
    font-size: 14px;
    color: #cc2c31;
    box-shadow: 0 2px 8px rgba(204, 44, 49, 0.1);
    position: relative;
  }
  
  .verify-tip-text::before {
    content: "";
    display: inline-block;
    width: 16px;
    height: 16px;
    margin-right: 8px;
    background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="%23cc2c31"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 15c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1s1 .45 1 1v4c0 .55-.45 1-1 1zm1-8h-2V7h2v2z"/></svg>');
    background-repeat: no-repeat;
    background-position: center;
    background-size: contain;
    vertical-align: middle;
  }
  
  .slider-verify-drag-container {
    width: 100%;
    height: 56px;
    position: relative;
    background-color: #f3f5f8;
    border-radius: 28px;
    overflow: hidden;
    box-shadow: inset 0 1px 4px rgba(0, 0, 0, 0.08);
    touch-action: none;
    border: 1px solid rgba(0, 0, 0, 0.03);
    margin-top: 20px;
    z-index: 2;
  }
  
  .slider-verify-bg {
    width: 100%;
    height: 100%;
    position: absolute;
    left: 0;
    top: 0;
    text-align: center;
    color: #555;
    line-height: 56px;
    font-size: 15px;
    font-weight: 500;
    user-select: none;
    -moz-user-select: none;
    -ms-user-select: none;
    -webkit-user-select: none;
    transition: all 0.3s ease;
    letter-spacing: 0.5px;
    padding: 0 20px;
    text-overflow: ellipsis;
    white-space: nowrap;
    overflow: hidden;
  }
  
  .slider-verify-bg.success {
    background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
    color: white;
  }
  
  .slider-verify-bg.error {
    background: linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%);
    color: white;
  }
  
  .success-text {
    color: white;
    font-weight: 600;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  }
  
  .error-text {
    color: white;
    font-weight: 600;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  }
  
  .slider-verify-progress {
    width: 0;
    height: 100%;
    position: absolute;
    left: 0;
    top: 0;
    background: linear-gradient(90deg, rgba(82, 196, 26, 0.1) 0%, rgba(82, 196, 26, 0.3) 100%);
    transition: width 0.1s;
    border-radius: 28px;
  }
  
  .slider-verify-progress.error {
    background: linear-gradient(90deg, rgba(255, 77, 79, 0.1) 0%, rgba(255, 77, 79, 0.3) 100%);
  }
  
  .slider-verify-block {
    width: 56px;
    height: 56px;
    background-color: transparent;
    position: absolute;
    left: 0;
    top: 0;
    cursor: pointer;
    transition: all 0.25s cubic-bezier(0.25, 0.46, 0.45, 0.94);
    z-index: 10;
    touch-action: none;
    overflow: visible;
    display: flex;
    justify-content: center;
    align-items: center;
    filter: drop-shadow(0 3px 5px rgba(0, 0, 0, 0.2));
  }
  
  .slider-verify-block.success {
    background-color: rgba(82, 196, 26, 0.1);
    transform: scale(1.05);
  }
  
  .slider-verify-block.error {
    background-color: rgba(255, 77, 79, 0.1);
  }
  
  .slider-verify-block:hover {
    transform: translateY(-3px) scale(1.05);
    filter: drop-shadow(0 5px 6px rgba(0, 0, 0, 0.3));
  }
  
  .slider-verify-block.block-active {
    transform: scale(0.92);
    filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.15));
  }
  
  .cat-image {
    width: 100%;
    height: 100%;
    object-fit: contain;
    border-radius: 50%;
  }
  
  .real-cat {
    position: relative;
    width: 100%;
    height: 100%;
  }
  
  .cat-head {
    position: absolute;
    width: 46px;
    height: 46px;
    background-color: #f3ede9;
    border-radius: 50%;
    box-shadow: 0 3px 6px rgba(0, 0, 0, 0.15);
    top: 22px;
    left: 2px;
    z-index: 1;
    overflow: visible;
  }
  
  .cat-ear {
    position: absolute;
    width: 22px;
    height: 25px;
    background-color: #74635a;
    border-radius: 50% 50% 0 0;
    z-index: 10;
    overflow: hidden;
  }
  
  .cat-ear.left {
    left: 4px;
    top: 5px;
    transform: rotate(-30deg) skew(10deg);
  }
  
  .cat-ear.right {
    right: 4px;
    top: 5px;
    transform: rotate(30deg) skew(-10deg);
  }
  
  .cat-ear-inner {
    position: absolute;
    width: 16px;
    height: 18px;
    background-color: #ffd3ca;
    border-radius: 50% 50% 0 0;
    top: 4px;
    left: 3px;
  }
  
  .cat-face {
    position: absolute;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    overflow: hidden;
  }
  
  .cat-forehead {
    position: absolute;
    width: 46px;
    height: 12px;
    background-color: #74635a;
    top: 0;
    left: 0;
    z-index: 2;
  }
  
  .cat-white-face {
    position: absolute;
    width: 46px;
    height: 38px;
    background-color: white;
    top: 8px;
    left: 0;
    border-radius: 60% 60% 50% 50%;
    z-index: 3;
  }
  
  .cat-mask {
    position: absolute;
    width: 36px;
    height: 20px;
    background-color: #74635a;
    top: 6px;
    left: 5px;
    border-radius: 35% 35% 60% 60%;
    z-index: 4;
    clip-path: polygon(0 0, 100% 0, 100% 60%, 50% 95%, 0 60%);
  }
  
  .cat-fur-top {
    position: absolute;
    width: 8px;
    height: 6px;
    background-color: #74635a;
    top: -3px;
    left: 19px;
    border-radius: 50% 50% 0 0;
    z-index: 2;
  }
  
  .cat-fur-left {
    position: absolute;
    width: 10px;
    height: 16px;
    background-color: #f3ede9;
    top: 4px;
    left: -2px;
    border-radius: 50% 0 50% 0;
    transform: rotate(-20deg);
    z-index: 1;
  }
  
  .cat-fur-right {
    position: absolute;
    width: 10px;
    height: 16px;
    background-color: #f3ede9;
    top: 4px;
    right: -2px;
    border-radius: 0 50% 0 50%;
    transform: rotate(20deg);
    z-index: 1;
  }
  
  .cat-eye {
    position: absolute;
    width: 12px;
    height: 12px;
    background-color: #a4d4f5;
    border-radius: 50%;
    top: 17px;
    border: 1px solid #333;
    overflow: hidden;
    z-index: 5;
  }
  
  .cat-eye.left {
    left: 10px;
  }
  
  .cat-eye.right {
    right: 10px;
  }
  
  .cat-pupil {
    position: absolute;
    width: 6px;
    height: 9px;
    background-color: #000;
    border-radius: 50%;
    top: 1px;
    left: 3px;
  }
  
  .cat-eye-shine {
    position: absolute;
    width: 4px;
    height: 4px;
    background-color: white;
    border-radius: 50%;
    top: 1px;
    left: 1px;
    z-index: 5;
  }
  
  .cat-eye.eye-active {
    height: 3px;
    background-color: #333;
    border-radius: 45%;
    border-color: #333;
    top: 21px;
  }
  
  .cat-eye.eye-active .cat-pupil,
  .cat-eye.eye-active .cat-eye-shine {
    display: none;
  }
  
  .cat-nose {
    position: absolute;
    width: 7px;
    height: 5px;
    background-color: #ff9a9e;
    border-radius: 40%;
    top: 26px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 5;
  }
  
  .cat-mouth {
    position: absolute;
    width: 14px;
    height: 6px;
    top: 32px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 5;
  }
  
  .cat-mouth-line {
    position: absolute;
    width: 6px;
    height: 3px;
    border-bottom: 1.5px solid #555;
    bottom: 0;
  }
  
  .cat-mouth-line.left {
    left: 0;
    border-radius: 0 0 0 100%;
    transform: rotate(-15deg);
  }
  
  .cat-mouth-line.right {
    right: 0;
    border-radius: 0 0 100% 0;
    transform: rotate(15deg);
  }
  
  .cat-whiskers {
    position: absolute;
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    z-index: 5;
  }
  
  .whisker {
    position: absolute;
    height: 1px;
    background-color: #bbb;
  }
  
  .whisker.left {
    left: -14px;
  }
  
  .whisker.right {
    right: -14px;
  }
  
  .whisker.top {
    width: 16px;
    top: 23px;
    transform: rotate(-10deg);
  }
  
  .whisker.middle {
    width: 18px;
    top: 27px;
  }
  
  .whisker.bottom {
    width: 16px;
    top: 31px;
    transform: rotate(10deg);
  }
  
  .whisker.right.top {
    transform: rotate(10deg);
  }
  
  .whisker.right.bottom {
    transform: rotate(-10deg);
  }
  
  .el-icon-check, .el-icon-close, .el-icon-warning {
    font-weight: bold;
  }
  
  @media (max-width: 768px) {
    .slider-verify-content {
      width: 90%;
      max-width: 320px;
    }
    
    .slider-verify-block {
      width: 50px;
      height: 50px;
    }
    
    .slider-verify-drag-container {
      height: 50px;
    }
    
    .slider-verify-bg {
      line-height: 50px;
    }
  }
  
  .slider-block-container {
    width: 100%;
    height: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
    background: linear-gradient(135deg, #fff 0%, #f0f0f0 100%);
    border-radius: 50%;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
    border: 1px solid rgba(0, 0, 0, 0.08);
    overflow: hidden;
  }
  
  .slider-block-arrow {
    display: flex;
    justify-content: center;
    align-items: center;
    color: #cc2c31;
    font-size: 24px;
    transition: all 0.3s ease;
  }
  
  .slider-verify-block.success .slider-block-arrow {
    color: #52c41a;
    transform: rotate(90deg);
  }
  
  .slider-verify-block.error .slider-block-arrow {
    color: #ff4d4f;
    animation: shake 0.5s;
  }
  
  @keyframes shake {
    0%, 100% { transform: translateX(0); }
    20%, 60% { transform: translateX(-5px); }
    40%, 80% { transform: translateX(5px); }
  }
  </style> 