// 使用window.OriginTitile，这样其他组件可以更新它
window.OriginTitile = document.title
let titleTime
let enableDynamicTitle = true // 默认开启动态标题

// 尝试从缓存获取网站配置
try {
  const cachedWebInfo = JSON.parse(localStorage.getItem('webInfo'))
  if (cachedWebInfo && cachedWebInfo.data) {
    // 更新浏览器标签标题（仅使用 webTitle，回退 webName；homeTitle 仅用于 ICP 备案，不显示在标签上）
    var tabTitle = cachedWebInfo.data.webTitle || cachedWebInfo.data.webName
    if (tabTitle) {
      window.OriginTitile = tabTitle
      document.title = window.OriginTitile
    }
    // 检查是否启用动态标题 - 优先使用缓存中的配置
    if (cachedWebInfo.data.hasOwnProperty('enableDynamicTitle')) {
      enableDynamicTitle = cachedWebInfo.data.enableDynamicTitle
    } else {
      // 如果缓存中没有该字段，保持默认开启状态
      enableDynamicTitle = true
    }
  }
} catch (e) {
  console.error('获取缓存配置失败:', e)
  // 出错时保持默认开启状态
  enableDynamicTitle = true
}

// 只有启用动态标题时才添加事件监听器
if (enableDynamicTitle) {
  document.addEventListener('visibilitychange', function () {
    document.hidden
      ? ((document.title = 'w(ﾟДﾟ)w 不要走！再看看嘛！'),
        clearTimeout(titleTime))
      : ((document.title = '♪(^∇^*)欢迎肥来！'),
        (titleTime = setTimeout(function () {
          document.title = window.OriginTitile
        }, 2e3)))
  })
}
