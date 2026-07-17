<template>
  <!--
    页脚友链/服务商展示区（默认关闭）。
    启用方式：后台「参数配置」→ 新增配置
      configKey   = footer.friendLinks
      configType  = 公开
      configValue = JSON 数组字符串，每项结构：
        {
          "name":      展示文字，必填，如 "雨云"
          "url":       跳转链接，必填，可携带 AFF 邀请码，如 "https://www.rainyun.com/?ref=YOUR_AFF"
          "logo":      可选，内联 SVG 字符串（currentColor 自动继承文字颜色）。仅允许纯 SVG，禁用脚本/事件处理器。
                       不需要 logo 时：整字段省略 / 留空串 / 不写该键均可，会自动降级为纯文字链接
          "ariaLabel": 可选，无障碍标签，留空时回退到 name
        }
    示例 configValue（混合：一条带 logo、一条纯文字）：
      [{"name":"又拍云联盟","url":"https://www.upyun.com/","logo":"<svg viewBox=\"0 0 24 24\" xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"><path d=\"M17.5 19a4.5 4.5 0 0 0 0-9h-1.1A5 5 0 0 0 6 8.5 4 4 0 0 0 6.5 16.5\"/></svg>"},
       {"name":"雨云","url":"https://www.rainyun.com/?ref=YOUR_AFF"}]
    未配置该键、配置为空、或 JSON 解析失败时，本区域不渲染（即默认关闭）。
  -->
  <div class="myFooter-wrap" v-show="showFooter">
    <div
      class="myFooter"
      :class="{ 'has-bg-image': hasBgImage, minimal: isMinimalFooter }"
      :style="footerStyle"
    >
      <div
        class="footer-title font"
        :style="textStyle"
        v-if="!isMinimalFooter && mainStore.webInfo.footer"
      >
        {{ mainStore.webInfo.footer }}
      </div>
      <div
        class="icp font"
        :style="textStyle"
        v-if="mainStore.sysConfig.beian || mainStore.sysConfig.policeBeian"
      >
        <a
          href="http://beian.miit.gov.cn/"
          target="_blank"
          v-if="mainStore.sysConfig.beian"
          >{{ mainStore.sysConfig.beian }}</a
        >
        <a
          href="http://www.beian.gov.cn/portal/registerSystemInfo"
          target="_blank"
          v-if="mainStore.sysConfig.policeBeian"
        >
          <img
            src="/static/assets/gonganbei.svg"
            alt="公安备案"
            style="margin-left: 10px; width: 14px; height: 14px"
          />
          {{ mainStore.sysConfig.policeBeian }}
        </a>
      </div>
      <div class="copyright font" :style="textStyle">
        <span class="copyright-left"
          >© {{ currentYear }} {{ mainStore.webInfo.webName }}</span
        >
        <span class="copyright-center">保留所有权利</span>
        <span class="copyright-right"
          ><a href="/privacy" class="policy-link">隐私政策</a></span
        >
      </div>
      <!-- 友链/服务商展示区：由 sysConfig['footer.friendLinks'] 驱动，未配置则不渲染 -->
      <div
        class="footer-links font"
        :style="textStyle"
        v-if="!isMinimalFooter && footerLinks.length"
      >
        <span class="footer-links-text">本站由</span>
        <a
          v-for="link in footerLinks"
          :key="link.name"
          :href="link.url"
          target="_blank"
          rel="noopener noreferrer nofollow"
          class="footer-link"
          :aria-label="link.ariaLabel || link.name"
        >
          <span
            v-if="link.logo"
            class="footer-link-logo"
            v-html="link.logo"
          ></span>
          <span class="footer-link-name">{{ link.name }}</span>
        </a>
        <span class="footer-links-text">提供加速与云服务</span>
      </div>
      <div class="contact font" :style="textStyle" v-if="!isMinimalFooter">
        本站内容均为原创或合法转载，如有侵权请通过邮箱：{{
          mainStore.webInfo.email || 'admin@poetize.cn'
        }}
        与我们联系，确认后将立即删除
      </div>
    </div>
  </div>
</template>

<script>
import { useMainStore } from '@/stores/main'

export default {
  props: {
    showFooter: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      // 当前视口宽度，用于判断是否移动端
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1920,
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    currentYear() {
      return new Date().getFullYear()
    },
    /**
     * 页脚友链/服务商列表：从 sysConfig['footer.friendLinks'] 读取。
     * 配置值是 JSON 字符串，结构：[{ name, url, logo?, ariaLabel? }]。
     * 默认未配置该键 → 返回空数组 → 模板不渲染该区域（功能默认关闭）。
     * 在后台「参数配置」中新增 configKey=footer.friendLinks、configType=公开，
     * 即可启用。配置示例见本文件顶部注释。
     */
    footerLinks() {
      const raw = this.mainStore.sysConfig
        ? this.mainStore.sysConfig['footer.friendLinks']
        : null
      if (!raw || typeof raw !== 'string' || !raw.trim()) return []
      try {
        const parsed = JSON.parse(raw)
        if (!Array.isArray(parsed)) return []
        // 仅保留必要字段并通过校验的项，过滤非法/空项以保证渲染安全
        return parsed
          .filter(
            (item) =>
              item &&
              typeof item === 'object' &&
              typeof item.name === 'string' &&
              item.name.trim() &&
              typeof item.url === 'string' &&
              item.url.trim()
          )
          .map((item) => ({
            name: item.name,
            url: item.url,
            // logo 仅允许 <svg>...</svg> 形态，过滤脚本/事件处理器以防 XSS
            logo: this.sanitizeSvgLogo(item.logo),
            ariaLabel:
              typeof item.ariaLabel === 'string' && item.ariaLabel.trim()
                ? item.ariaLabel
                : item.name,
          }))
      } catch (e) {
        // 配置 JSON 解析失败时静默降级为空，避免拖垮整个页脚
        console.warn('[myFooter] footer.friendLinks 配置解析失败，已忽略:', e)
        return []
      }
    },
    hasBgImage() {
      const img = this.mainStore.webInfo.footerBackgroundImage
      if (!img) return false
      const val = String(img).trim().toLowerCase()
      return val !== '' && val !== 'null' && val !== 'undefined'
    },
    isMinimalFooter() {
      const flag =
        this.mainStore.webInfo && this.mainStore.webInfo.minimalFooter
      if (flag === true) return true
      if (typeof flag === 'string') {
        return flag.toLowerCase() === 'true'
      }
      return false
    },
    footerStyle() {
      const webInfo = this.mainStore.webInfo
      // 基础公共样式
      const baseStyle = {
        borderRadius: '1.5rem 1.5rem 0 0',
        textAlign: 'center',
        color: 'var(--white)',
        // 根据设备宽度动态调整高度：移动端更紧凑
        minHeight: (() => {
          const isMobile = this.viewportWidth <= 768
          const hasFooterTitle =
            this.mainStore.webInfo && this.mainStore.webInfo.footer
          const hasBeianInfo =
            this.mainStore.sysConfig &&
            (this.mainStore.sysConfig.beian ||
              this.mainStore.sysConfig.policeBeian)

          if (this.isMinimalFooter) {
            if (hasBeianInfo) {
              return isMobile ? '70px' : '90px'
            }
            return isMobile ? '50px' : '70px'
          }

          let baseHeight
          // 根据是否有 footer 标题来调整基础高度
          if (hasFooterTitle) {
            baseHeight = isMobile ? 120 : 150
          } else {
            baseHeight = isMobile ? 105 : 135
          }

          // 如果没有备案信息，再减少30px
          if (!hasBeianInfo) {
            baseHeight -= 30
          }

          return `${baseHeight}px`
        })(),
      }

      // 1. 存在背景图 → 关闭渐变动画，使用伪元素展示图片
      if (webInfo.footerBackgroundImage) {
        const style = {
          ...baseStyle,
          background: 'transparent',
          animation: 'none',
          // 供 ::after / ::before 使用的变量
          '--footer-bg-image': `url(${webInfo.footerBackgroundImage})`,
        }

        // 解析后台配置
        let bgConfig = {
          backgroundSize: 'cover',
          backgroundPosition: 'center center',
          backgroundRepeat: 'no-repeat',
          opacity: 100,
        }
        if (webInfo.footerBackgroundConfig) {
          try {
            bgConfig = {
              ...bgConfig,
              ...JSON.parse(webInfo.footerBackgroundConfig),
            }
          } catch (e) {
            console.error('解析页脚背景配置失败:', e)
          }
        }

        style['--footer-bg-size'] = bgConfig.backgroundSize
        style['--footer-bg-position'] = bgConfig.backgroundPosition
        style['--footer-bg-repeat'] = bgConfig.backgroundRepeat

        // 遮罩颜色
        if (bgConfig.maskColor) {
          style['--footer-mask-color'] = bgConfig.maskColor
        } else {
          const maskOpacity = (100 - (bgConfig.opacity || 50)) / 100
          style['--footer-mask-color'] = `rgba(0, 0, 0, ${maskOpacity})`
        }
        return style
      }

      // 2. 无背景图 → 使用渐变动画
      return {
        ...baseStyle,
        background: 'var(--gradientBG)',
        backgroundSize: '300% 300%',
        animation: 'gradientBG 10s ease infinite',
      }
    },
    textStyle() {
      const webInfo = this.mainStore.webInfo
      let style = {
        color: 'var(--white)',
        position: 'relative',
        zIndex: 10,
      }

      // 如果有背景图片，设置文字颜色和阴影
      if (webInfo.footerBackgroundImage) {
        let bgConfig = {
          textColor: '#ffffff',
          textShadow: false,
        }

        // 解析页脚背景配置
        if (webInfo.footerBackgroundConfig) {
          try {
            const config = JSON.parse(webInfo.footerBackgroundConfig)
            bgConfig = { ...bgConfig, ...config }
          } catch (e) {
            console.error('解析页脚背景配置失败:', e)
          }
        }

        // 设置文字颜色
        if (bgConfig.textColor) {
          style.color = bgConfig.textColor
        }

        // 设置文字阴影（增强可读性）
        if (bgConfig.textShadow) {
          style.textShadow =
            '0 2px 8px rgba(0, 0, 0, 0.8), 0 1px 3px rgba(0, 0, 0, 0.6)'
        } else {
          style.textShadow = 'none'
        }
      }

      return style
    },
  },
  created() {},
  mounted() {
    // 监听窗口尺寸变化以实时更新页脚高度
    this._resizeHandler = () => {
      this.viewportWidth = window.innerWidth
    }
    window.addEventListener('resize', this._resizeHandler)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this._resizeHandler)
  },
  methods: {
    /**
     * 对外部配置注入的 SVG 字符串做最小化安全过滤。
     * 仅允许形如 <svg ...>...</svg> 的纯 SVG，剔除 <script>/事件处理器/javascript: 等危险内容。
     * 任何不合法内容统一返回空串，渲染降级为纯文字链接。
     */
    sanitizeSvgLogo(raw) {
      if (typeof raw !== 'string') return ''
      const s = raw.trim()
      if (!s) return ''
      if (!/^<svg[\s>]/i.test(s) || !/<\/svg>\s*$/i.test(s)) return ''
      if (/<script[\s>]/i.test(s) || /\son\w+\s*=/i.test(s)) return ''
      if (/javascript:/i.test(s)) return ''
      return s
    },
  },
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
  min-height: 160px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.55rem;
  padding: 20px 24px 24px;
  box-sizing: border-box;
}
.myFooter.has-bg-image {
  background: transparent !important;
  animation: none !important;
}
.myFooter.has-bg-image {
  background-attachment: fixed;
}
.myFooter[style*='--footer-bg-image'] {
  background: transparent !important;
  animation: none !important;
}
.myFooter[style*='--footer-bg-image']::after {
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
.myFooter[style*='--footer-bg-image']::before {
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
  font-size: 16px;
  line-height: 1.7;
  position: relative;
  z-index: 10;
  font-weight: 600;
  margin: 0;
}
.icp,
.icp a {
  color: var(--white);
  font-size: 14px;
  line-height: 1.7;
  position: relative;
  z-index: 10;
  font-weight: 400;
}
.myFooter[style*='--footer-bg-image'] .footer-title {
  color: #ffffff !important;
  font-weight: 500;
}
.myFooter[style*='--footer-bg-image'] .icp,
.myFooter[style*='--footer-bg-image'] .icp a {
  color: #ffd700 !important;
  font-weight: 400;
}
.myFooter[style*='--footer-bg-image'] .icp a:hover,
.myFooter[style*='--footer-bg-image'] .copyright a:hover {
  color: #ffed4a !important;
}
.myFooter[style*='--footer-bg-image'] .copyright a {
  color: var(--white) !important;
  font-weight: 400;
}
.myFooter[style*='--footer-bg-image'] .policy-link {
  color: var(--white);
  font-weight: 500;
  padding: 0 2px;
  transition: color 0.3s;
}
.myFooter[style*='--footer-bg-image'] .policy-link:hover {
  color: var(--themeBackground);
}
.icp {
  margin: 0;
}
.icp a,
.copyright a {
  text-decoration: none;
  transition: color 0.3s ease, opacity 0.3s ease;
}
.icp a:hover,
.copyright a:hover {
  color: var(--themeBackground);
}
.policy-link {
  color: var(--white);
  font-weight: 500;
  padding: 0 2px;
  transition: color 0.3s;
}
.policy-link:hover {
  color: var(--themeBackground);
}
.copyright {
  font-size: 14px;
  line-height: 1.7;
  position: relative;
  z-index: 10;
  font-weight: 400;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  margin: 0;
}
.copyright-left {
  justify-self: end;
}
.copyright-center {
  justify-self: center;
  white-space: nowrap;
}
.copyright-right {
  justify-self: start;
}
.contact,
.extra-info {
  font-size: 14px;
  line-height: 1.8;
  position: relative;
  z-index: 10;
  font-weight: 400;
  margin: 0;
}
.myFooter[style*='--footer-bg-image'] .copyright,
.myFooter[style*='--footer-bg-image'] .contact,
.myFooter[style*='--footer-bg-image'] .extra-info {
  color: var(--white) !important;
  font-weight: 400;
}
/* 友链/服务商展示区：自适应换行、可继承文字颜色 */
.footer-links {
  font-size: 13px;
  line-height: 1.7;
  position: relative;
  z-index: 10;
  font-weight: 400;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 2px 6px;
  margin: 0;
}
.footer-links-text {
  font-weight: 400;
  opacity: 0.85;
}
.footer-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: inherit;
  font-weight: 500;
  transition: color 0.3s ease, opacity 0.3s ease;
}
/* 多个友链之间用 · 分隔，仅在两条及以上时生效 */
.footer-link + .footer-link::before {
  content: '·';
  margin-right: 4px;
  opacity: 0.6;
  font-weight: 400;
}
.footer-link-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
/* v-html 注入的 SVG 需穿透 scoped 样式 */
.footer-link-logo :deep(svg) {
  width: 14px;
  height: 14px;
  display: block;
}
.footer-link:hover {
  color: var(--themeBackground);
  opacity: 0.85;
}
.myFooter[style*='--footer-bg-image'] .footer-links,
.myFooter[style*='--footer-bg-image'] .footer-link {
  color: var(--white) !important;
  font-weight: 500;
}
.myFooter[style*='--footer-bg-image'] .footer-link:hover {
  color: var(--themeBackground) !important;
}
@media (max-width: 768px) {
  .myFooter {
    border-radius: 0;
    min-height: 120px;
    gap: 0.45rem;
    padding: 16px 18px 20px;
  }
  .myFooter.minimal {
    min-height: 70px;
  }
  .footer-title {
    font-size: 16px;
  }
  .icp,
  .contact,
  .extra-info {
    font-size: 14px;
  }
  .copyright {
    font-size: 14px;
    gap: 4px;
  }
  .copyright-left,
  .copyright-center,
  .copyright-right {
    font-size: 14px;
  }
  .footer-links {
    font-size: 12px;
    gap: 2px 4px;
  }
  .footer-link-logo :deep(svg) {
    width: 12px;
    height: 12px;
  }
}
.myFooter.minimal {
  min-height: 90px;
  gap: 0.35rem;
  padding-top: 14px;
  padding-bottom: 16px;
}
</style>
