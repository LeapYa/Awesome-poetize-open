/**
 * 早期初始化脚本
 * 必须在 <body> 开始处同步加载，用于：
 * 1. 快速设置页面标题，减少闪烁
 * 2. 自动检测并应用暗色模式，避免白屏闪烁
 * 3. 阻止移动端浏览器强制暗色模式
 */

// 阻止移动端浏览器（如OPPO/小米/华为）强制暗色模式
(function blockForcedDarkMode() {
  try {
    // 移除任何可能被浏览器注入的 filter
    var style = document.createElement('style');
    style.id = 'block-forced-dark';
    style.textContent = 
      'html, body, * { ' +
        '-webkit-filter: none !important; ' +
        'filter: none !important; ' +
      '}' +
      '@media (prefers-color-scheme: dark) {' +
        'html:not(.dark-mode), html:not(.dark-mode) body {' +
          'background-color: #fff !important;' +
          'color: #333 !important;' +
        '}' +
      '}';
    
    // 尽早插入到 head
    if (document.head) {
      document.head.insertBefore(style, document.head.firstChild);
    } else {
      document.addEventListener('DOMContentLoaded', function() {
        document.head.insertBefore(style, document.head.firstChild);
      });
    }

    // 监听并阻止动态注入的暗色滤镜
    if (window.MutationObserver) {
      var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
          if (mutation.type === 'attributes' && mutation.attributeName === 'style') {
            var target = mutation.target;
            var computedStyle = window.getComputedStyle(target);
            var filter = computedStyle.filter || computedStyle.webkitFilter;
            
            // 如果检测到反转滤镜，移除它
            if (filter && filter !== 'none' && filter.indexOf('invert') !== -1) {
              target.style.setProperty('-webkit-filter', 'none', 'important');
              target.style.setProperty('filter', 'none', 'important');
            }
          }
        });
      });
      
      // 观察 html 和 body 元素的 style 变化
      observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style'] });
      document.addEventListener('DOMContentLoaded', function() {
        observer.observe(document.body, { attributes: true, attributeFilter: ['style'] });
      });
    }
  } catch (e) { /* 静默处理 */ }
})();

// 快速设置标题
(function initTitle() {
  try {
    var cachedWebInfo = localStorage.getItem('webInfo');
    if (cachedWebInfo) {
      var webInfoData = JSON.parse(cachedWebInfo);
      if (webInfoData && webInfoData.data && webInfoData.data.webTitle) {
        document.title = webInfoData.data.webTitle;
        window.OriginTitile = webInfoData.data.webTitle;
      }
    }
  } catch (e) { /* 静默处理 */ }
})();

// 自动检测暗色模式
(function initDarkMode() {
  try {
    var userTheme = localStorage.getItem('theme');
    var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;

    // 优先级：用户设置 > 系统偏好
    if (userTheme === 'dark' || (userTheme !== 'light' && prefersDark)) {
      document.documentElement.classList.add('dark-mode');
      document.body.classList.add('dark-mode');

      // 设置暗色模式 CSS 变量
      var root = document.documentElement;
      var darkVars = {
        '--background': '#272727',
        '--fontColor': 'white',
        '--borderColor': '#4F4F4F',
        '--borderHoverColor': 'black',
        '--articleFontColor': '#E4E4E4',
        '--articleGreyFontColor': '#D4D4D4',
        '--commentContent': '#383838',
        '--favoriteBg': '#1e1e1e',
        '--whiteMask': 'rgba(56, 56, 56, 0.3)',
        '--maxWhiteMask': 'rgba(56, 56, 56, 0.5)',
        '--maxMaxWhiteMask': 'rgba(56, 56, 56, 0.7)',
        '--miniWhiteMask': 'rgba(56, 56, 56, 0.15)',
        '--mask': 'rgba(0, 0, 0, 0.5)',
        '--miniMask': 'rgba(0, 0, 0, 0.3)',
        '--inputBackground': '#383838',
        '--secondaryText': '#B0B0B0',
        '--card-bg-rgb': '39, 39, 39'
      };

      for (var key in darkVars) {
        root.style.setProperty(key, darkVars[key]);
      }
    }
  } catch (e) { /* 静默处理 */ }
})();
