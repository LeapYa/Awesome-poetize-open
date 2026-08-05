import { useMainStore } from '../stores/main'
import { redirectToLogin } from './tokenExpireHandler'
import { getDefaultAvatar, getAvatarUrl } from './default-avatar'
import { getAiDefaultAvatar, getAiAvatarUrl } from './ai-avatar'
import constant from './constant'

function escapeHtmlText(value = '') {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
}

function escapeHtmlAttribute(value = '') {
  return escapeHtmlText(value)
}

function isSafeImageUrl(value = '') {
  const url = String(value).trim()
  if (!url || /[\u0000-\u001F\u007F\s"'<>`]/.test(url)) {
    return false
  }

  const protocolMatch = url.match(/^([a-z][a-z0-9+.-]*):/i)
  if (protocolMatch) {
    return ['http:', 'https:'].includes(protocolMatch[1].toLowerCase() + ':')
  }

  return true
}

export default {
  /**
   * 获取默认头像
   */
  getDefaultAvatar,

  /**
   * 获取头像URL（带默认头像回退）
   */
  getAvatarUrl,

  /**
   * 获取默认AI头像
   */
  getAiDefaultAvatar,

  /**
   * 获取AI头像URL（带默认AI头像回退）
   */
  getAiAvatarUrl,

  mobile() {
    let flag = navigator.userAgent.match(
      /(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i
    )
    return flag && flag.length && flag.length > 0
  },

  /**
   * 判断是否为空
   */
  isEmpty(value) {
    if (
      typeof value === 'undefined' ||
      value === null ||
      (typeof value === 'string' && value.trim() === '') ||
      (Array.prototype.isPrototypeOf(value) && value.length === 0) ||
      (Object.prototype.isPrototypeOf(value) && Object.keys(value).length === 0)
    ) {
      return true
    } else {
      return false
    }
  },

  /**
   * 树洞（微言/朋友圈/文章动态）内容单行校验
   * 树洞只允许一行内容，禁止换行符及 br/p/div 等换行类 HTML 标签
   * @param {string} content - 待校验内容
   * @returns {string|null} 违规时返回提示文案，合法时返回 null
   */
  checkTreeHoleSingleLine(content) {
    if (!content) {
      return null
    }
    if (/[\r\n]/.test(content) || /<\s*(?:br|p|div)\b/i.test(content)) {
      return '树洞只支持一行内容，请勿输入换行或 <br> 等换行标签！'
    }
    return null
  },

  /**
   * 表情包转换
   */
  faceReg(content) {
    content = content.replace(/\[[^\[^\]]+\]/g, (word) => {
      let index = constant.emojiList.indexOf(
        word.replace('[', '').replace(']', '')
      )
      if (index > -1) {
        const mainStore = useMainStore()
        let url =
          mainStore.sysConfig['webStaticResourcePrefix'] +
          'emoji/q' +
          (index + 1) +
          '.gif'
        if (!isSafeImageUrl(url)) {
          return escapeHtmlText(word)
        }
        return (
          '<img loading="lazy" style="vertical-align: middle;width: 32px;height: 32px" src="' +
          escapeHtmlAttribute(url) +
          '" title="' +
          escapeHtmlAttribute(word) +
          '"/>'
        )
      } else {
        return escapeHtmlText(word)
      }
    })
    return content
  },

  /**
   * 图片转换
   */
  pictureReg(content) {
    content = content.replace(/\[[^\[^\]]+\]/g, (word) => {
      let index = word.indexOf(',')
      if (index > -1) {
        const value = word.substring(1, word.length - 1)
        const commaIndex = value.indexOf(',')
        const title = value.substring(0, commaIndex)
        const url = value.substring(commaIndex + 1).trim()
        if (!isSafeImageUrl(url)) {
          return escapeHtmlText(word)
        }
        return (
          '<img loading="lazy" class="pictureReg" style="border-radius: 5px;width: 100%;max-width: 250px;display: block" src="' +
          escapeHtmlAttribute(url) +
          '" title="' +
          escapeHtmlAttribute(title) +
          '"/>'
        )
      } else {
        return escapeHtmlText(word)
      }
    })
    return content
  },

  /**
   * 移除Markdown语法，返回纯文本
   */
  removeMarkdown(text) {
    if (this.isEmpty(text)) return ''

    // 移除代码块
    let result = String(text).replace(/```[\s\S]*?```/g, ' ')
    result = result.replace(/~~~[\s\S]*?~~~/g, ' ')

    // 移除图片
    result = result.replace(/!\[([^\]]*)\]\([^)]+\)/g, ' ')

    // 移除行内代码
    result = result.replace(/`([^`]+)`/g, '$1')

    // 移除标题标记
    result = result.replace(/#{1,6}\s+/g, '')

    // 移除链接，只保留链接文本
    result = result.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')

    // 移除强调标记（加粗、斜体）
    result = result.replace(/(\*\*|__)(.*?)\1/g, '$2')
    result = result.replace(/(\*|_)(.*?)\1/g, '$2')
    result = result.replace(/~~(.*?)~~/g, '$1')

    // 移除引用标记
    result = result.replace(/^\s*>\s+/gm, '')

    // 移除分隔线
    result = result.replace(/^\s*[-*_]{3,}\s*$/gm, '')

    // 移除列表标记
    result = result.replace(/^\s*[-*+]\s+/gm, '')
    result = result.replace(/^\s*\d+\.\s+/gm, '')

    // 移除HTML标签
    result = result.replace(/<[^>]*>/g, ' ')

    // 摘要降级时保持单行纯文本，避免引号和换行进入HTML源码
    result = result
      .replace(/&nbsp;/g, ' ')
      .replace(/&quot;|&#34;|&#x22;/gi, '')
      .replace(/&#39;|&#x27;/gi, '')
      .replace(/["'“”‘’]/g, '')
      .replace(/\|/g, ' ')
      .replace(/\s+/g, ' ')

    // 移除首尾空白
    result = result.trim()

    return result
  },

  imgShow(select) {
    // 使用原生 JavaScript 替代 jQuery
    const elements = document.querySelectorAll(select)

    elements.forEach((element) => {
      element.addEventListener('click', function () {
        const src = this.getAttribute('src')
        const bigImg = document.getElementById('bigImg')
        const outerImg = document.getElementById('outerImg')
        const innerImg = document.getElementById('innerImg')

        if (!bigImg || !outerImg || !innerImg) {
          return
        }

        bigImg.setAttribute('src', src)

        /** 获取当前点击图片的真实大小，并显示弹出层及大图 */
        const tempImg = new Image()
        tempImg.onload = function () {
          const windowW = window.innerWidth // 获取当前窗口宽度
          const windowH = window.innerHeight // 获取当前窗口高度
          const realWidth = this.width // 获取图片真实宽度
          const realHeight = this.height // 获取图片真实高度
          let imgWidth, imgHeight
          const scale = 0.8 // 缩放尺寸，当图片真实宽度和高度大于窗口宽度和高度时进行缩放

          if (realHeight > windowH * scale) {
            // 判断图片高度
            imgHeight = windowH * scale // 如大于窗口高度，图片高度进行缩放
            imgWidth = (imgHeight / realHeight) * realWidth // 等比例缩放宽度
            if (imgWidth > windowW * scale) {
              // 如宽度仍大于窗口宽度
              imgWidth = windowW * scale // 再对宽度进行缩放
            }
          } else if (realWidth > windowW * scale) {
            // 如图片高度合适，判断图片宽度
            imgWidth = windowW * scale // 如大于窗口宽度，图片宽度进行缩放
            imgHeight = (imgWidth / realWidth) * realHeight // 等比例缩放高度
          } else {
            // 如果图片真实高度和宽度都符合要求，高宽不变
            imgWidth = realWidth
            imgHeight = realHeight
          }

          bigImg.style.width = imgWidth + 'px' // 以最终的宽度对图片缩放

          const w = (windowW - imgWidth) / 2 // 计算图片与窗口左边距
          const h = (windowH - imgHeight) / 2 // 计算图片与窗口上边距
          innerImg.style.top = h + 'px'
          innerImg.style.left = w + 'px'

          // 淡入显示效果
          outerImg.style.display = 'block'
          outerImg.style.opacity = '0'
          outerImg.style.transition = 'opacity 0.3s ease'
          setTimeout(() => {
            outerImg.style.opacity = '1'
          }, 10)
        }
        tempImg.src = src

        // 点击外层容器关闭图片
        outerImg.onclick = function () {
          this.style.transition = 'opacity 0.3s ease'
          this.style.opacity = '0'
          setTimeout(() => {
            this.style.display = 'none'
          }, 300)
        }
      })
    })
  },

  /**
   * 字符串转换为时间戳
   */
  getDateTimeStamp(dateStr) {
    return Date.parse(dateStr.replace(/-/gi, '/'))
  },

  getDateDiff(dateStr) {
    let publishTime = isNaN(Date.parse(dateStr.replace(/-/gi, '/')) / 1000)
      ? Date.parse(dateStr) / 1000
      : Date.parse(dateStr.replace(/-/gi, '/')) / 1000
    let d_seconds,
      d_minutes,
      d_hours,
      d_days,
      timeNow = Math.floor(new Date().getTime() / 1000),
      d,
      date = new Date(publishTime * 1000),
      Y = date.getFullYear(),
      M = date.getMonth() + 1,
      D = date.getDate(),
      H = date.getHours(),
      m = date.getMinutes(),
      s = date.getSeconds()
    //小于10的在前面补0
    if (M < 10) {
      M = '0' + M
    }
    if (D < 10) {
      D = '0' + D
    }
    if (H < 10) {
      H = '0' + H
    }
    if (m < 10) {
      m = '0' + m
    }
    if (s < 10) {
      s = '0' + s
    }
    d = timeNow - publishTime
    d_days = Math.floor(d / 86400)
    d_hours = Math.floor(d / 3600)
    d_minutes = Math.floor(d / 60)
    d_seconds = Math.floor(d)
    if (d_days > 0 && d_days < 3) {
      return d_days + '天前'
    } else if (d_days <= 0 && d_hours > 0) {
      return d_hours + '小时前'
    } else if (d_hours <= 0 && d_minutes > 0) {
      return d_minutes + '分钟前'
    } else if (d_seconds < 60) {
      if (d_seconds <= 0) {
        return '刚刚发表'
      } else {
        return d_seconds + '秒前'
      }
    } else if (d_days >= 3) {
      return Y + '-' + M + '-' + D + ' ' + H + ':' + m
    }
  },

  /**
   * 保存资源
   */
  saveResource(
    that,
    type,
    path,
    size,
    mimeType,
    originalName,
    storeType,
    isAdmin = false
  ) {
    let resource = {
      type: type,
      path: path,
      size: size,
      mimeType: mimeType,
      storeType: storeType,
      originalName: originalName,
    }

    that.$http
      .post(
        that.$constant.baseURL + '/resource/saveResource',
        resource,
        isAdmin
      )
      .catch((error) => {
        that.$message({
          message: error.message,
          type: 'error',
        })
      })
  },

  /**
   * 统一的登录跳转处理函数
   * 封装redirectToLogin函数，方便在组件中使用
   * @param {Object} router - Vue Router实例
   * @param {Object} options - 配置选项
   * @param {Object} vueInstance - Vue组件实例，用于显示消息
   */
  redirectToLogin(router, options = {}, vueInstance = null) {
    // 确保传入当前路径
    if (!options.currentPath && vueInstance && vueInstance.$route) {
      options.currentPath = vueInstance.$route.fullPath
    }

    // 将Vue实例传递给原始函数
    return redirectToLogin(router, {
      ...options,
      vueInstance: vueInstance,
    })
  },
}
