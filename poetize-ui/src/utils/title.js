// 使用window.OriginTitile，这样其他组件可以更新它
window.OriginTitile = document.title;
let titleTime;

// 尝试从缓存获取网站标题
try {
  const cachedWebInfo = JSON.parse(localStorage.getItem('webInfo'));
  if (cachedWebInfo && cachedWebInfo.data && cachedWebInfo.data.webTitle) {
    window.OriginTitile = cachedWebInfo.data.webTitle;
    document.title = window.OriginTitile;
  }
} catch (e) {
  console.error("获取缓存标题失败:", e);
}

document.addEventListener("visibilitychange", (function () {
  document.hidden ? (document.title = "w(ﾟДﾟ)w 不要走！再看看嘛！", clearTimeout(titleTime)) : (document.title = "♪(^∇^*)欢迎肥来！", titleTime = setTimeout((function () {
    document.title = window.OriginTitile
  }), 2e3))
}));
