<template>
  <div class="myFooter-wrap" v-show="showFooter">
    <div class="myFooter" :style="footerStyle">
      <div class="footer-title font" :style="textStyle">{{$store.state.webInfo.footer}}</div>
      <div class="icp font" :style="textStyle">让每一次访问都更美好 <a href="http://beian.miit.gov.cn/" target="_blank">{{ $store.state.sysConfig.beian }}</a></div>
      <div class="copyright font" :style="textStyle">© 2025 {{ $store.state.webInfo.webTitle }} | 保留所有权利 | <a href="/privacy" class="policy-link">隐私政策</a></div>
      <div class="extra-info font" :style="textStyle">用心创作，用爱传递，让文字的力量激发心灵共鸣</div>
      <div class="contact font" :style="textStyle">本站内容均为原创或合法转载，如有侵权请通过邮箱：{{ $store.state.webInfo.email || 'admin@poetize.cn' }} 与我们联系，确认后将立即删除</div>
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
      return {}
    },
    computed: {
      footerStyle() {
        const webInfo = this.$store.state.webInfo;
        let style = {
          borderRadius: '1.5rem 1.5rem 0 0',
          textAlign: 'center',
          color: 'var(--white)',
          backgroundSize: '300% 300%',
          animation: 'gradientBG 10s ease infinite'
        };

        // 如果有页脚背景图片，使用图片背景
        if (webInfo.footerBackgroundImage) {
          // 完全移除原来的背景和动画
          style.background = 'transparent';
          style.animation = 'none';
          
          // 添加一个标识，用于CSS选择器
          style['--footer-bg-image'] = `url(${webInfo.footerBackgroundImage})`;
          
          // 解析页脚背景配置
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

          // 将背景配置设置为CSS变量，供伪元素使用
          style['--footer-bg-size'] = bgConfig.backgroundSize || 'cover';
          style['--footer-bg-position'] = bgConfig.backgroundPosition || 'center center';
          style['--footer-bg-repeat'] = bgConfig.backgroundRepeat || 'no-repeat';
          // 透明度用于控制遮罩层，透明度越高遮罩越浅
          style['--footer-mask-opacity'] = (100 - (bgConfig.opacity || 50)) / 100;
        } else {
          // 使用原来的渐变背景
          style.background = 'var(--gradientBG)';
        }

        return style;
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
    }
  }
</script>

<style scoped>
  .myFooter-wrap {
    user-select: none;
    animation: hideToShow 2s;
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

  /* 当有背景图片时，确保文字在合适的位置 */
  .myFooter[style*="background-image"] {
    background-attachment: fixed;
  }

  /* 当有背景图片时，完全移除默认背景和动画 */
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
    background: rgba(0, 0, 0, var(--footer-mask-opacity));
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
    color: #ffffff !important; /* 隐私政策链接保持白色 */
    font-weight: 400;
  }

  .myFooter[style*="--footer-bg-image"] .policy-link {
    color: #ffd700 !important; /* 隐私政策链接使用金色 */
    text-decoration: underline;
    font-weight: 500;
  }

  .myFooter[style*="--footer-bg-image"] .policy-link:hover {
    color: #ffed4a !important; /* 悬停时稍微亮一点的金色 */
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
    color: var(--themeBackground);
    text-decoration: underline;
    font-weight: 500;
    padding: 0 2px;
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
    color: #ffffff !important; /* 版权和联系信息保持白色 */
    font-weight: 400;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .myFooter {
      border-radius: 0;
      min-height: 130px;
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

</style>
