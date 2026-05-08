import constant from "./constant";
import { useMainStore } from '../stores/main';
import { redirectToLogin } from './tokenExpireHandler';
import { getDefaultAvatar, getAvatarUrl } from './default-avatar';

export default {
  /**
   * 获取默认头像
   */
  getDefaultAvatar,

  /**
   * 获取头像URL（带默认头像回退）
   */
  getAvatarUrl,

  pushNotification(notices, isNotification) {
    // 统一的空值检查，防止null/undefined错误
    if (this.isEmpty(notices)) {
      return isNotification ? [] : {};
    }

    // 确保notices是数组类型
    if (!Array.isArray(notices)) {
      return isNotification ? [] : {};
    }

    if (isNotification) {
      // 返回过滤后的通知数组
      return notices.filter(f =>
        typeof f === 'string' &&
        "推送标题：" !== f.substr(0, 5) &&
        "推送封面：" !== f.substr(0, 5) &&
        "推送链接：" !== f.substr(0, 5)
      );
    } else {
      // 解析推送信息对象
      let push = {};
      notices.forEach(notice => {
        if (typeof notice === 'string') {
          if ("推送标题：" === notice.substr(0, 5)) {
            push['标题'] = notice.substr(5);
          } else if ("推送封面：" === notice.substr(0, 5)) {
            push['封面'] = notice.substr(5);
          } else if ("推送链接：" === notice.substr(0, 5)) {
            push['链接'] = notice.substr(5);
          }
        }
      });
      return push;
    }
  },

  mobile() {
    let flag = navigator.userAgent.match(/(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i);
    return flag && flag.length && flag.length > 0;
  },

  /**
   * 判断是否为空
   */
  isEmpty(value) {
    if (typeof value === "undefined" || value === null || (typeof value === "string" && value.trim() === "") || (Array.prototype.isPrototypeOf(value) && value.length === 0) || (Object.prototype.isPrototypeOf(value) && Object.keys(value).length === 0)) {
      return true;
    } else {
      return false;
    }
  },

  /**
   * 检查 Web Crypto API 是否可用
   * 在移动端 HTTP 环境下，crypto.subtle 不可用
   */
  isWebCryptoAvailable() {
    return typeof crypto !== 'undefined' &&
      typeof crypto.subtle !== 'undefined' &&
      typeof crypto.subtle.encrypt === 'function';
  },

  /**
   * 加密 - 使用AES-GCM模式（异步）
   * 需要 Web Crypto API 支持（HTTPS 或 localhost）
   */
  async encrypt(plaintText) {
    // 检查 Web Crypto API 是否可用
    if (!this.isWebCryptoAvailable()) {
      const errorMsg = '安全警告！当前环境不支持加密功能。请使用 HTTPS 访问此网站，或联系站长启用 HTTPS。';
      console.error(errorMsg);
      // 抛出错误让调用方处理，而不是返回明文
      throw new Error(errorMsg);
    }

    try {
      // 使用Web Crypto API实现GCM模式
      const key = await crypto.subtle.importKey(
        'raw',
        new TextEncoder().encode(constant.cryptojs_key),
        { name: 'AES-GCM' },
        false,
        ['encrypt']
      );

      // 生成随机IV
      const iv = crypto.getRandomValues(new Uint8Array(12));

      // 加密
      const encrypted = await crypto.subtle.encrypt(
        {
          name: 'AES-GCM',
          iv: iv,
          tagLength: 128
        },
        key,
        new TextEncoder().encode(plaintText)
      );

      // 组合IV和密文
      const combined = new Uint8Array(iv.length + encrypted.byteLength);
      combined.set(iv, 0);
      combined.set(new Uint8Array(encrypted), iv.length);

      // 转换为Base64并处理特殊字符
      const base64 = btoa(String.fromCharCode(...combined));
      return base64.replace(/\//g, "_").replace(/\+/g, "-");
    } catch (error) {
      console.error('加密失败:', error);
      throw error; // 重新抛出错误
    }
  },

  /**
   * 解密 - 使用AES-GCM模式（异步）
   * 需要 Web Crypto API 支持（HTTPS 或 localhost）
   */
  async decrypt(encryptedBase64Str) {
    // 检查 Web Crypto API 是否可用
    if (!this.isWebCryptoAvailable()) {
      console.error('Web Crypto API 不可用，无法解密');
      return null;
    }

    try {
      // 还原Base64特殊字符
      let base64 = encryptedBase64Str.replace(/-/g, '+').replace(/_/g, '/');

      // 转换为ArrayBuffer
      const binary = atob(base64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }

      // 提取IV和密文
      const iv = bytes.slice(0, 12);
      const ciphertext = bytes.slice(12);

      // 导入密钥
      const key = await crypto.subtle.importKey(
        'raw',
        new TextEncoder().encode(constant.cryptojs_key),
        { name: 'AES-GCM' },
        false,
        ['decrypt']
      );

      // 解密
      const decrypted = await crypto.subtle.decrypt(
        {
          name: 'AES-GCM',
          iv: iv,
          tagLength: 128
        },
        key,
        ciphertext
      );

      return new TextDecoder().decode(decrypted);
    } catch (error) {
      console.error('解密失败:', error);
      return null;
    }
  },

  /**
   * 表情包转换
   */
  faceReg(content) {
    content = content.replace(/\[[^\[^\]]+\]/g, (word) => {
      let index = constant.emojiList.indexOf(word.replace("[", "").replace("]", ""));
      if (index > -1) {
        const mainStore = useMainStore();
        let url = mainStore.sysConfig['webStaticResourcePrefix'] + "emoji/q" + (index + 1) + ".gif";
        return '<img loading="lazy" style="vertical-align: middle;width: 32px;height: 32px" src="' + url + '" title="' + word + '"/>';
      } else {
        return word;
      }
    });
    return content;
  },

  /**
   * 图片转换
   */
  pictureReg(content) {
    content = content.replace(/\[[^\[^\]]+\]/g, (word) => {
      let index = word.indexOf(",");
      if (index > -1) {
        let arr = word.replace("[", "").replace("]", "").split(",");
        return '<img loading="lazy" class="pictureReg" style="border-radius: 5px;width: 100%;max-width: 250px;display: block" src="' + arr[1] + '" title="' + arr[0] + '"/>';
      } else {
        return word;
      }
    });
    return content;
  },

  /**
   * 移除Markdown语法，返回纯文本
   */
  removeMarkdown(text) {
    if (this.isEmpty(text)) return '';

    // 移除代码块
    let result = text.replace(/```[\s\S]*?```/g, '');

    // 移除行内代码
    result = result.replace(/`([^`]+)`/g, '$1');

    // 移除标题标记
    result = result.replace(/#{1,6}\s+/g, '');

    // 移除链接，只保留链接文本
    result = result.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

    // 移除图片
    result = result.replace(/!\[([^\]]*)\]\([^)]+\)/g, '');

    // 移除强调标记（加粗、斜体）
    result = result.replace(/(\*\*|__)(.*?)\1/g, '$2');
    result = result.replace(/(\*|_)(.*?)\1/g, '$2');

    // 移除引用标记
    result = result.replace(/^\s*>\s+/gm, '');

    // 移除分隔线
    result = result.replace(/^\s*[-*_]{3,}\s*$/gm, '');

    // 移除列表标记
    result = result.replace(/^\s*[-*+]\s+/gm, '');
    result = result.replace(/^\s*\d+\.\s+/gm, '');

    // 移除HTML标签
    result = result.replace(/<[^>]*>/g, '');

    // 移除首尾空白
    result = result.trim();

    return result;
  },

  imgShow(select) {
    // 使用原生 JavaScript 替代 jQuery
    const elements = document.querySelectorAll(select);

    elements.forEach(element => {
      element.addEventListener('click', function () {
        const src = this.getAttribute('src');
        const bigImg = document.getElementById('bigImg');
        const outerImg = document.getElementById('outerImg');
        const innerImg = document.getElementById('innerImg');

        if (!bigImg || !outerImg || !innerImg) {
          return;
        }

        bigImg.setAttribute('src', src);

        /** 获取当前点击图片的真实大小，并显示弹出层及大图 */
        const tempImg = new Image();
        tempImg.onload = function () {
          const windowW = window.innerWidth; // 获取当前窗口宽度
          const windowH = window.innerHeight; // 获取当前窗口高度
          const realWidth = this.width; // 获取图片真实宽度
          const realHeight = this.height; // 获取图片真实高度
          let imgWidth, imgHeight;
          const scale = 0.8; // 缩放尺寸，当图片真实宽度和高度大于窗口宽度和高度时进行缩放

          if (realHeight > windowH * scale) { // 判断图片高度
            imgHeight = windowH * scale; // 如大于窗口高度，图片高度进行缩放
            imgWidth = imgHeight / realHeight * realWidth; // 等比例缩放宽度
            if (imgWidth > windowW * scale) { // 如宽度仍大于窗口宽度
              imgWidth = windowW * scale; // 再对宽度进行缩放
            }
          } else if (realWidth > windowW * scale) { // 如图片高度合适，判断图片宽度
            imgWidth = windowW * scale; // 如大于窗口宽度，图片宽度进行缩放
            imgHeight = imgWidth / realWidth * realHeight; // 等比例缩放高度
          } else { // 如果图片真实高度和宽度都符合要求，高宽不变
            imgWidth = realWidth;
            imgHeight = realHeight;
          }

          bigImg.style.width = imgWidth + 'px'; // 以最终的宽度对图片缩放

          const w = (windowW - imgWidth) / 2; // 计算图片与窗口左边距
          const h = (windowH - imgHeight) / 2; // 计算图片与窗口上边距
          innerImg.style.top = h + 'px';
          innerImg.style.left = w + 'px';

          // 淡入显示效果
          outerImg.style.display = 'block';
          outerImg.style.opacity = '0';
          outerImg.style.transition = 'opacity 0.3s ease';
          setTimeout(() => {
            outerImg.style.opacity = '1';
          }, 10);
        };
        tempImg.src = src;

        // 点击外层容器关闭图片
        outerImg.onclick = function () {
          this.style.transition = 'opacity 0.3s ease';
          this.style.opacity = '0';
          setTimeout(() => {
            this.style.display = 'none';
          }, 300);
        };
      });
    });
  },

  getDateDiff(dateStr) {
    let publishTime = isNaN(Date.parse(dateStr.replace(/-/gi, "/")) / 1000) ? Date.parse(dateStr) / 1000 : Date.parse(dateStr.replace(/-/gi, "/")) / 1000;
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
      s = date.getSeconds();
    //小于10的在前面补0
    if (M < 10) {
      M = '0' + M;
    }
    if (D < 10) {
      D = '0' + D;
    }
    if (H < 10) {
      H = '0' + H;
    }
    if (m < 10) {
      m = '0' + m;
    }
    if (s < 10) {
      s = '0' + s;
    }
    d = timeNow - publishTime;
    d_days = Math.floor(d / 86400);
    d_hours = Math.floor(d / 3600);
    d_minutes = Math.floor(d / 60);
    d_seconds = Math.floor(d);
    if (d_days > 0 && d_days < 3) {
      return d_days + '天前';
    } else if (d_days <= 0 && d_hours > 0) {
      return d_hours + '小时前';
    } else if (d_hours <= 0 && d_minutes > 0) {
      return d_minutes + '分钟前';
    } else if (d_seconds < 60) {
      if (d_seconds <= 0) {
        return '刚刚发表';
      } else {
        return d_seconds + '秒前';
      }
    } else if (d_days >= 3) {
      return Y + '-' + M + '-' + D + ' ' + H + ':' + m;
    }
  },

  /**
   * 保存资源
   */
  saveResource(that, type, path, size, mimeType, originalName, storeType, isAdmin = false) {
    let resource = {
      type: type,
      path: path,
      size: size,
      mimeType: mimeType,
      storeType: storeType,
      originalName: originalName
    };

    return that.$http.post(that.$constant.baseURL + "/resource/saveResource", resource, isAdmin)
      .catch((error) => {
        that.$message({
          message: error.message,
          type: "error"
        });
      });
  },

  /**
   * 计算两个时间相差的年、月、日、时、分、秒
   *
   * https://juejin.cn/post/7153816601156911118
   */
  timeDiff(oldTime, newTime) {
    oldTime = oldTime.replace(new RegExp("-", "gm"), "/");
    if (newTime) {
      newTime = newTime.replace(new RegExp("-", "gm"), "/");
    } else {
      newTime = new Date();
    }

    // 计算比较日期
    const getMaxMinDate = (time, twoTime, type) => {
      let minTime = new Date(time).getTime() - new Date(twoTime).getTime() > 0 ? twoTime : time;
      let maxTime = new Date(time).getTime() - new Date(twoTime).getTime() > 0 ? time : twoTime;
      let maxDateDay = new Date(new Date(maxTime).getFullYear(), new Date(maxTime).getMonth() + 1, 0).getDate();
      let maxMinDate = new Date(minTime).getDate() > maxDateDay ? maxDateDay : new Date(minTime).getDate();
      let maxMinTong;
      if (type === 'month') {
        maxMinTong = new Date(maxTime).getFullYear() + '/' + (new Date(minTime).getMonth() + 1) + '/' + maxMinDate + ' ' + new Date(minTime).toLocaleTimeString('chinese', { hour12: false });
      } else {
        maxMinTong = new Date(maxTime).getFullYear() + '/' + (new Date(maxTime).getMonth() + 1) + '/' + maxMinDate + ' ' + new Date(minTime).toLocaleTimeString('chinese', { hour12: false });
      }
      return {
        minTime,
        maxTime,
        maxMinTong
      }
    }

    // 相差年份
    const getYear = (time, twoTime) => {
      let oneYear = new Date(time).getFullYear();
      let twoYear = new Date(twoTime).getFullYear();
      const { minTime, maxTime, maxMinTong } = getMaxMinDate(time, twoTime, 'month');
      let chaYear = Math.abs(oneYear - twoYear);
      if (new Date(maxMinTong).getTime() > new Date(maxTime).getTime()) {
        chaYear--;
      }
      return chaYear;
    }

    // 相差月份
    const getMonth = (time, twoTime, value) => {
      let oneMonth = new Date(time).getFullYear() * 12 + (new Date(time).getMonth() + 1);
      let twoMonth = new Date(twoTime).getFullYear() * 12 + (new Date(twoTime).getMonth() + 1);
      const { minTime, maxTime, maxMinTong } = getMaxMinDate(time, twoTime, 'day');
      let chaMonth = Math.abs(oneMonth - twoMonth);
      if (new Date(maxMinTong).getTime() > new Date(maxTime).getTime()) {
        chaMonth--;
      }
      if (value) {
        return chaMonth - value;
      } else {
        return chaMonth;
      }
    }

    // 相差天数
    const getDay = (time, twoTime, value) => {
      let chaTime = Math.abs(new Date(time).getTime() - new Date(twoTime).getTime());
      if (value) {
        return parseInt(chaTime / 86400000) - value;
      } else {
        return parseInt(chaTime / 86400000);
      }
    }

    // 相差小时
    const getHour = (time, twoTime, value) => {
      let chaTime = Math.abs(new Date(time).getTime() - new Date(twoTime).getTime());
      if (value) {
        return parseInt(chaTime / 3600000) - value;
      } else {
        return parseInt(chaTime / 3600000);
      }
    }

    // 相差分钟
    const getMinute = (time, twoTime, value) => {
      let chaTime = Math.abs(new Date(time).getTime() - new Date(twoTime).getTime());
      if (value) {
        return parseInt(chaTime / 60000) - value;
      } else {
        return parseInt(chaTime / 60000);
      }
    }

    // 相差秒
    const getSecond = (time, twoTime, value) => {
      let chaTime = Math.abs(new Date(time).getTime() - new Date(twoTime).getTime());
      if (value) {
        return parseInt(chaTime / 1000) - value;
      } else {
        return parseInt(chaTime / 1000);
      }
    }

    // 相差年月日时分秒
    const getDiffYMDHMS = (time, twoTime) => {
      const { minTime, maxTime, maxMinTong } = getMaxMinDate(time, twoTime, 'day');
      let diffDay1 = getDay(minTime, maxMinTong);
      if (new Date(maxMinTong).getTime() > new Date(maxTime).getTime()) {
        let prevMonth = new Date(maxMinTong).getMonth() - 1;
        let lastTime = new Date(maxMinTong).setMonth(prevMonth);
        diffDay1 = diffDay1 - getDay((new Date(lastTime).getFullYear() + '/' + (new Date(lastTime).getMonth() + 1) + '/' + new Date(lastTime).getDate()), maxMinTong);
      }
      let diffYear = getYear(time, twoTime);
      let diffMonth = getMonth(time, twoTime, diffYear * 12);
      let diffDay = getDay(time, twoTime, diffDay1);
      let diffHour = getHour(time, twoTime, getDay(time, twoTime) * 24);
      let diffMinute = getMinute(time, twoTime, (getDay(time, twoTime) * 24 * 60 + diffHour * 60));
      let diffSecond = getSecond(time, twoTime, (getDay(time, twoTime) * 24 * 60 * 60 + diffHour * 60 * 60 + diffMinute * 60));
      return {
        diffYear,
        diffMonth,
        diffDay,
        diffHour,
        diffMinute,
        diffSecond
      }
    }

    return getDiffYMDHMS(oldTime, newTime);
  },

  countdown(time) {
    time = new Date(time.replace(new RegExp("-", "gm"), "/"));
    let nowTime = new Date();
    //两个时间点的时间差(秒)
    let seconds = parseInt((time.getTime() - nowTime.getTime()) / 1000);
    let d = parseInt(seconds / 3600 / 24);
    let h = parseInt(seconds / 3600 % 24);
    let m = parseInt(seconds / 60 % 60);
    let s = parseInt(seconds % 60);
    return {
      d,
      h,
      m,
      s
    }
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
      options.currentPath = vueInstance.$route.fullPath;
    }

    // 将Vue实例传递给原始函数
    return redirectToLogin(router, {
      ...options,
      vueInstance: vueInstance
    });
  }
}
