<template>
  <div class="myFooter-wrap" v-show="showFooter">
    <div class="myFooter" :class="{ 'has-bg-image': hasBgImage, 'minimal': isMinimalFooter }" :style="footerStyle">
      <div class="footer-title font" :style="textStyle" v-if="!isMinimalFooter">{{$store.state.webInfo.footer}}</div>
      <div class="icp font" :style="textStyle">让每一次访问都更美好 <a href="http://beian.miit.gov.cn/" target="_blank">{{ $store.state.sysConfig.beian }}</a></div>
      <div class="copyright font" :style="textStyle">© 2025 {{ $store.state.webInfo.webTitle }} | 保留所有权利 | <a href="/privacy" class="policy-link">隐私政策</a></div>
      <div class="extra-info font" :style="textStyle" v-if="!isMinimalFooter">用心创作，用爱传递，让文字的力量激发心灵共鸣</div>
      <div class="contact font" :style="textStyle" v-if="!isMinimalFooter">本站内容均为原创或合法转载，如有侵权请通过邮箱：{{ $store.state.webInfo.email || 'admin@poetize.cn' }} 与我们联系，确认后将立即删除</div>
    </div>
  </div>
</template>

<script>
  export default {
    props: {
      showFooter: {
        type: Boolean,
        default: true
      }
    },
    data() {
      return {
        // 当前视口宽度，用于判断是否移动端
        viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1920
      };
    },
    computed: {
      hasBgImage() {
        const img = this.$store.state.webInfo.footerBackgroundImage;
        if (!img) return false;
        const val = String(img).trim().toLowerCase();
        return val !== '' && val !== 'null' && val !== 'undefined';
      },
      isMinimalFooter() {
        const flag = this.$store.state.webInfo && this.$store.state.webInfo.minimalFooter;
        if (flag === true) return true;
        if (typeof flag === 'string') {
          return flag.toLowerCase() === 'true';
        }
        return false;
      },
      footerStyle() {
        const webInfo = this.$store.state.webInfo;
        // 基础公共样式
        const baseStyle = {
          borderRadius: '1.5rem 1.5rem 0 0',
          textAlign: 'center',
          color: 'var(--white)',
          // 根据设备宽度动态调整高度：移动端更紧凑
          minHeight: (() => {
            const isMobile = this.viewportWidth <= 768;
            if (this.isMinimalFooter) {
              return isMobile ? '80px' : '100px';
            }
            return isMobile ? '130px' : '180px';
          })()
        };

        // 1. 存在背景图 → 关闭渐变动画，使用伪元素展示图片
        if (webInfo.footerBackgroundImage) {
          const style = {
            ...baseStyle,
            background: 'transparent',
            animation: 'none',
            // 供 ::after / ::before 使用的变量
            '--footer-bg-image': `url(${webInfo.footerBackgroundImage})`
          };

          // 解析后台配置
          let bgConfig = {
            backgroundSize: 'cover',
            backgroundPosition: 'center center',
            backgroundRepeat: 'no-repeat',
            opacity: 100
          };
          if (webInfo.footerBackgroundConfig) {
            try {
              bgConfig = { ...bgConfig, ...JSON.parse(webInfo.footerBackgroundConfig) };
            } catch (e) {
              console.error("解析页脚背景配置失败:", e);
            }
          }

          style['--footer-bg-size'] = bgConfig.backgroundSize;
          style['--footer-bg-position'] = bgConfig.backgroundPosition;
          style['--footer-bg-repeat'] = bgConfig.backgroundRepeat;

          // 遮罩颜色
          if (bgConfig.maskColor) {
            style['--footer-mask-color'] = bgConfig.maskColor;
          } else {
            const maskOpacity = (100 - (bgConfig.opacity || 50)) / 100;
            style['--footer-mask-color'] = `rgba(0, 0, 0, ${maskOpacity})`;
          }
          return style;
        }

        // 2. 无背景图 → 使用渐变动画
        return {
          ...baseStyle,
          background: 'var(--gradientBG)',
          backgroundSize: '300% 300%',
          animation: 'gradientBG 10s ease infinite'
        };
      },
      textStyle() {
        const webInfo = this.$store.state.webInfo;
        let style = {
          color: 'var(--white)',
          position: 'relative',
          zIndex: 10
        };

        // 如果有背景图片，设置文字颜色但不添加阴影
        if (webInfo.footerBackgroundImage) {
          let bgConfig = {
            textColor: '#ffffff'
          };

          // 解析页脚背景配置
          if (webInfo.footerBackgroundConfig) {
            try {
              const config = JSON.parse(webInfo.footerBackgroundConfig);
              bgConfig = { ...bgConfig, ...config };
            } catch (e) {
              console.error("解析页脚背景配置失败:", e);
            }
          }

          // 设置文字颜色
          if (bgConfig.textColor) {
            style.color = bgConfig.textColor;
          }
        }

        return style;
      }
    },
    created() {
    },
    mounted() {
      // 监听窗口尺寸变化以实时更新页脚高度
      this._resizeHandler = () => {
        this.viewportWidth = window.innerWidth;
      };
      window.addEventListener('resize', this._resizeHandler);
    },
    beforeDestroy() {
      window.removeEventListener('resize', this._resizeHandler);
    }
  }
</script>

<style scoped>
  .myFooter-wrap {
    user-select: none;
    animation: hideToShow 2s both;
  }

  .myFooter {
    border-radius: 1.5rem 1.5rem 0 0;
    background: var(--gradientBG);
    text-align: center;
    color: var(--white);
    background-size: 300% 300%;
    animation: gradientBG 10s ease infinite;
    position: relative;
    overflow: hidden;
    min-height: 180px;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  /* 当有背景图片时，通过类统一移除渐变动画和背景 */
  .myFooter.has-bg-image {
    background: transparent !important;
    animation: none !important;
  }

  /* 当有背景图片时，确保文字在合适的位置 */
  .myFooter.has-bg-image {
    background-attachment: fixed;
  }

  /* 兼容旧逻辑，如果项目其他地方仍依赖 style 查询，也保留选择器 */
  .myFooter[style*="--footer-bg-image"] {
    background: transparent !important;
    animation: none !important;
  }

  /* 使用伪元素处理背景图片，背景图片保持完全不透明 */
  .myFooter[style*="--footer-bg-image"]::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: var(--footer-bg-image);
    background-size: var(--footer-bg-size);
    background-position: var(--footer-bg-position);
    background-repeat: var(--footer-bg-repeat);
    opacity: 1;
    z-index: 0;
  }

  /* 遮罩层，透明度可通过设置控制 */
  .myFooter[style*="--footer-bg-image"]::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--footer-mask-color, rgba(0, 0, 0, 0.5));
    z-index: 1;
  }

  .footer-title {
    padding-top: 10px;
    font-size: 18px;
    position: relative;
    z-index: 10;
    font-weight: 600;
  }

  .icp, .icp a {
    color: var(--maxGreyFont);
    font-size: 16px;
    position: relative;
    z-index: 10;
    font-weight: 400;
  }

  /* 当有背景图片时，设置文字颜色 */
  .myFooter[style*="--footer-bg-image"] .footer-title {
    color: #ffffff !important; /* 页脚标题保持白色 */
    font-weight: 500;
  }

  .myFooter[style*="--footer-bg-image"] .icp,
  .myFooter[style*="--footer-bg-image"] .icp a {
    color: #ffd700 !important; /* "让每一次访问都更美好"和ICP备案信息设为金色 */
    font-weight: 400;
  }

  .myFooter[style*="--footer-bg-image"] .icp a:hover,
  .myFooter[style*="--footer-bg-image"] .copyright a:hover {
    color: #ffed4a !important; /* 悬停时稍微亮一点的金色 */
  }

  .myFooter[style*="--footer-bg-image"] .copyright a {
    color: var(--white) !important; /* 隐私政策链接保持白色 */
    font-weight: 400;
  }

  /* 隐私政策默认白色，悬停/激活时才变金色 */
  .myFooter[style*="--footer-bg-image"] .policy-link {
    color: var(--white);
    text-decoration: underline;
    font-weight: 500;
    padding: 0 2px;
    transition: color 0.3s;
  }

  .myFooter[style*="--footer-bg-image"] .policy-link:hover {
    color: var(--themeBackground);
  }

  .icp {
    padding-top: 10px;
    padding-bottom: 10px;
  }

  .icp a, .copyright a {
    text-decoration: none;
    transition: all 0.3s;
  }

  .icp a:hover, .copyright a:hover {
    color: var(--themeBackground);
  }

  .policy-link {
    color: var(--white);
    text-decoration: underline;
    font-weight: 500;
    padding: 0 2px;
    transition: color 0.3s;
  }

  .policy-link:hover {
    color: var(--themeBackground);
  }

  .copyright, .contact, .extra-info {
    color: var(--maxGreyFont);
    font-size: 16px;
    position: relative;
    z-index: 10;
    font-weight: 400;
    padding-top: 5px;
  }

  /* 新增内容在有背景图片时保持白色 */
  .myFooter[style*="--footer-bg-image"] .copyright,
  .myFooter[style*="--footer-bg-image"] .contact,
  .myFooter[style*="--footer-bg-image"] .extra-info {
    color: var(--white) !important; /* 版权和联系信息保持白色 */
    font-weight: 400;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .myFooter {
      border-radius: 0;
      min-height: 130px;
    }
    
    /* 极简模式下进一步减小高度 */
    .myFooter.minimal {
      min-height: 80px;
    }
    
    .footer-title {
      font-size: 16px;
      padding-top: 8px;
    }
    
    .icp, .copyright, .contact, .extra-info {
      font-size: 14px;
      padding-top: 8px;
      padding-bottom: 8px;
    }
  }

  .myFooter.minimal {
    min-height: 100px;
  }

</style>
