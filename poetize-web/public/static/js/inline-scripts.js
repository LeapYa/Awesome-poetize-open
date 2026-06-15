// 看板娘拖拽功能，仅在启用看板娘功能时执行
(function() {
  // 检查看板娘功能是否启用
  function isWaifuEnabled() {
    try {
      // 从本地存储获取配置
      const webInfoStr = localStorage.getItem('webInfo');
      if (webInfoStr) {
        const webInfoData = JSON.parse(webInfoStr);
        // 检查
        if (webInfoData.data) {
          return webInfoData.data.enableWaifu === true;
        }
      }
      return false; // 默认为禁用
    } catch (e) {
      console.error('检查看板娘状态出错:', e);
      return false;
    }
  }

  // 仅在看板娘功能启用时执行拖拽代码
  if (!isWaifuEnabled()) {
    return;
  }

  // 等待DOM加载完成
  document.addEventListener('DOMContentLoaded', function() {
    // 等待看板娘元素加载
    const waitForWaifu = setInterval(function() {
      const waifu = document.getElementById('waifu');
      if (waifu) {
        clearInterval(waitForWaifu);
        
        // 样式修改
        waifu.style.cursor = 'e-resize'; // 水平拖动的鼠标样式
        waifu.style.touchAction = 'none';
        waifu.style.zIndex = '1000';
        
        // 初始化拖拽
        initDrag(waifu);
      }
    }, 1000);
  });

  // 初始化拖拽功能
  function initDrag(element) {
    let isDragging = false;
    let startX, startY;
    let offsetX = 0;
    let hasSaid = false;
    
    // 显示消息的函数
    function showMessage(text, timeout) {
      const tips = document.getElementById('waifu-tips');
      if (tips) {
        tips.innerHTML = text;
        tips.classList.add('waifu-tips-active');
        
        // 清除之前的计时器
        if (window.messageTimer) {
          clearTimeout(window.messageTimer);
          window.messageTimer = null;
        }
        
        // 设置消息显示时间
        window.messageTimer = setTimeout(() => {
          tips.classList.remove('waifu-tips-active');
        }, timeout || 5000);
      }
    }
    
    // 鼠标按下事件
    element.addEventListener('mousedown', function(e) {
      // 防止拖动工具栏时触发拖拽
      if (e.target.closest('#waifu-tool') || e.target.closest('#waifu-tips')) {
        return;
      }
      
      isDragging = true;
      hasSaid = false;
      element.classList.add('dragging');
      element.style.transition = 'none';
      element.style.opacity = '0.7';
      
      // 记录起始位置
      startX = e.clientX;
      
      // 获取当前位置
      const style = window.getComputedStyle(element);
      offsetX = parseInt(style.left) || 0;
      
      e.preventDefault();
    });

    // 鼠标移动事件
    document.addEventListener('mousemove', function(e) {
      if (!isDragging) return;
      
      // 第一次移动时显示消息
      if (!hasSaid) {
        showMessage("怎么啦，是嫌我挡路了嘛", 3000);
        hasSaid = true;
      }
      
      // 计算新位置（只允许水平移动）
      const newLeft = offsetX + (e.clientX - startX);
      
      // 设置新位置，确保不超出视窗
      const maxLeft = window.innerWidth - element.offsetWidth;
      element.style.left = Math.min(Math.max(0, newLeft), maxLeft) + 'px';
    });

    // 鼠标释放事件
    document.addEventListener('mouseup', function() {
      if (isDragging) {
        isDragging = false;
        element.classList.remove('dragging');
        element.style.transition = '';
        element.style.opacity = '1';
      }
    });

    // 触摸开始事件
    element.addEventListener('touchstart', function(e) {
      // 防止拖动工具栏时触发拖拽
      if (e.target.closest('#waifu-tool') || e.target.closest('#waifu-tips')) {
        return;
      }
      
      isDragging = true;
      hasSaid = false;
      element.style.transition = 'none';
      element.style.opacity = '0.7';
      
      // 记录起始位置
      startX = e.touches[0].clientX;
      
      // 获取当前位置
      const style = window.getComputedStyle(element);
      offsetX = parseInt(style.left) || 0;
      
      e.preventDefault(); // 阻止滚动
    });

    // 触摸移动事件
    document.addEventListener('touchmove', function(e) {
      if (!isDragging) return;
      
      // 第一次移动时显示消息
      if (!hasSaid) {
        showMessage("怎么啦，是嫌我挡路了吗", 3000);
        hasSaid = true;
      }
      
      // 计算新位置（只允许水平移动）
      const newLeft = offsetX + (e.touches[0].clientX - startX);
      
      // 设置新位置，确保不超出视窗
      const maxLeft = window.innerWidth - element.offsetWidth;
      element.style.left = Math.min(Math.max(0, newLeft), maxLeft) + 'px';
      
      e.preventDefault(); // 阻止滚动
    }, { passive: false });

    // 触摸结束事件
    document.addEventListener('touchend', function() {
      if (isDragging) {
        isDragging = false;
        element.style.transition = '';
        element.style.opacity = '1';
      }
    });
  }
})(); 