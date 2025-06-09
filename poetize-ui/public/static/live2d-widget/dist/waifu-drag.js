/**
 * 看板娘拖拽功能
 */
(function() {
  // 等待DOM加载完成
  document.addEventListener('DOMContentLoaded', function() {
    // 等待看板娘元素加载
    const waitForWaifu = setInterval(function() {
      const waifu = document.getElementById('waifu');
      if (waifu) {
        clearInterval(waitForWaifu);
        initDrag(waifu);
      }
    }, 500);
  });

  // 初始化拖拽功能
  function initDrag(element) {
    let isDragging = false;
    let startX, startY;
    let offsetX = 0, offsetY = 0;
    let hasMoved = false;
    let mouseDownTime = 0;
    let clickThreshold = 5; // 移动小于5像素视为点击而非拖拽
    let longPressThreshold = 300; // 长按超过300ms才开始拖拽
    let dragTimeout = null; // 拖拽延时器
    let canStartDrag = false; // 标记是否可以开始拖拽
    
    // 获取canvas元素 - 只有canvas可以拖拽
    const canvas = document.getElementById('live2d');
    if (!canvas) {
      console.warn('Canvas元素未找到，无法初始化拖拽');
      return;
    }
    
    // 从localStorage读取位置
    const savedPosition = localStorage.getItem('waifu-position');
    if (savedPosition) {
      try {
        const position = JSON.parse(savedPosition);
        element.style.left = position.left + 'px';
        element.style.bottom = position.bottom + 'px';
      } catch (e) {
        console.error('读取看板娘位置失败', e);
      }
    }

    // 检查是否在受保护的区域内
    function isInProtectedArea(target) {
      if (!target) return false;
      
      // 检查是否在聊天框内
      if (target.closest('#waifu-chat') || target.closest('.waifu-chat-container')) {
        return true;
      }
      
      // 检查是否在tips框内
      if (target.closest('#waifu-tips')) {
        return true;
      }
      
      // 检查是否在工具栏内
      if (target.closest('#waifu-tool')) {
        return true;
      }
      
      return false;
    }

    // ===== 鼠标事件 - 只绑定到canvas =====
    // 鼠标按下事件
    canvas.addEventListener('mousedown', function(e) {
      // 记录起始位置和时间
      startX = e.clientX;
      startY = e.clientY;
      mouseDownTime = Date.now();
      hasMoved = false;
      isDragging = false;
      canStartDrag = true; // 标记可以开始拖拽
      
      // 获取当前位置
      const style = window.getComputedStyle(element);
      offsetX = parseInt(style.left) || 0;
      offsetY = parseInt(style.bottom) || 0;
      
      // 清除之前的延时器
      if (dragTimeout) {
        clearTimeout(dragTimeout);
        dragTimeout = null;
      }
      
      // 设置延时器
      dragTimeout = setTimeout(() => {
        if (hasMoved && e.buttons === 1 && canStartDrag) {
          isDragging = true;
          element.classList.add('dragging');
        }
      }, longPressThreshold);
      
      e.preventDefault(); // 只对canvas阻止默认行为
    });

    // 鼠标移动事件 - 绑定到document但只在从canvas开始拖拽时响应
    document.addEventListener('mousemove', function(e) {
      // 如果鼠标不是按下状态，或者没有从canvas开始，或者在受保护区域，则不处理
      if (e.buttons !== 1 || !canStartDrag || mouseDownTime === 0) return;
      
      // 检查当前鼠标位置是否在受保护区域
      const elementUnderMouse = document.elementFromPoint(e.clientX, e.clientY);
      if (isInProtectedArea(elementUnderMouse)) {
        return; // 如果鼠标移动到受保护区域，停止拖拽逻辑
      }
      
      // 计算移动距离
      const moveX = Math.abs(e.clientX - startX);
      const moveY = Math.abs(e.clientY - startY);
      
      // 判断是否达到拖拽阈值
      if (moveX > clickThreshold || moveY > clickThreshold) {
        hasMoved = true;
        
        // 如果按下时间已超过阈值，立即开始拖拽
        if (Date.now() - mouseDownTime > longPressThreshold) {
          isDragging = true;
          element.classList.add('dragging');
        }
      }
      
      // 只有确认为拖拽时才移动
      if (isDragging) {
        // 计算新位置
        const newLeft = offsetX + (e.clientX - startX);
        const newBottom = offsetY - (e.clientY - startY);
        
        // 设置新位置，确保不超出视窗
        element.style.left = Math.max(0, newLeft) + 'px';
        element.style.bottom = Math.max(0, newBottom) + 'px';
      }
    });

    // 鼠标释放事件
    document.addEventListener('mouseup', function(e) {
      if (!canStartDrag || mouseDownTime === 0) return; // 如果没有从canvas开始，不处理
      
      // 清除延时器
      if (dragTimeout) {
        clearTimeout(dragTimeout);
        dragTimeout = null;
      }
      
      // 如果按下后几乎没有移动，且释放时间很短，视为点击
      if (!hasMoved && Date.now() - mouseDownTime < longPressThreshold) {
        // 允许点击事件正常触发
        console.log('看板娘被点击');
      }
      else if (isDragging) {
        // 结束拖拽状态
        isDragging = false;
        element.classList.remove('dragging');
        
        // 保存位置到localStorage
        savePosition(element);
      }
      
      // 重置状态
      isDragging = false;
      hasMoved = false;
      mouseDownTime = 0;
      canStartDrag = false; // 重置拖拽标记
      element.classList.remove('dragging');
    });

    // ===== 触摸事件 - 只绑定到canvas =====
    // 触摸开始事件
    canvas.addEventListener('touchstart', function(e) {
      // 记录起始位置和时间
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      mouseDownTime = Date.now();
      hasMoved = false;
      isDragging = false;
      canStartDrag = true; // 标记可以开始拖拽
      
      // 获取当前位置
      const style = window.getComputedStyle(element);
      offsetX = parseInt(style.left) || 0;
      offsetY = parseInt(style.bottom) || 0;
      
      // 清除之前的延时器
      if (dragTimeout) {
        clearTimeout(dragTimeout);
        dragTimeout = null;
      }
      
      // 延迟设置拖拽状态
      dragTimeout = setTimeout(() => {
        if (hasMoved && canStartDrag) {
          isDragging = true;
          element.classList.add('dragging');
        }
      }, longPressThreshold);
    });

    // 触摸移动事件 - 绑定到document但只在从canvas开始触摸时响应
    document.addEventListener('touchmove', function(e) {
      if (!canStartDrag || mouseDownTime === 0 || !e.touches[0]) return;
      
      // 检查当前触摸位置是否在受保护区域
      const elementUnderTouch = document.elementFromPoint(e.touches[0].clientX, e.touches[0].clientY);
      if (isInProtectedArea(elementUnderTouch)) {
        return; // 如果触摸移动到受保护区域，停止拖拽逻辑
      }
      
      // 计算移动距离
      const moveX = Math.abs(e.touches[0].clientX - startX);
      const moveY = Math.abs(e.touches[0].clientY - startY);
      
      // 判断是否达到拖拽阈值
      if (moveX > clickThreshold || moveY > clickThreshold) {
        hasMoved = true;
        
        // 如果按下时间已超过阈值，立即开始拖拽
        if (Date.now() - mouseDownTime > longPressThreshold) {
          isDragging = true;
          element.classList.add('dragging');
        }
      }
      
      // 只有确认为拖拽时才移动
      if (isDragging) {
        // 计算新位置
        const newLeft = offsetX + (e.touches[0].clientX - startX);
        const newBottom = offsetY - (e.touches[0].clientY - startY);
        
        // 设置新位置，确保不超出视窗
        element.style.left = Math.max(0, newLeft) + 'px';
        element.style.bottom = Math.max(0, newBottom) + 'px';
        
        e.preventDefault(); // 阻止滚动
      }
    }, { passive: false });

    // 触摸结束事件
    document.addEventListener('touchend', function(e) {
      if (!canStartDrag || mouseDownTime === 0) return;
      
      // 清除延时器
      if (dragTimeout) {
        clearTimeout(dragTimeout);
        dragTimeout = null;
      }
      
      // 如果几乎没有移动，且触摸时间很短，视为点击
      if (!hasMoved && Date.now() - mouseDownTime < longPressThreshold) {
        // 允许事件冒泡，正常触发点击事件
        console.log('看板娘被触摸点击');
      }
      else if (isDragging) {
        // 结束拖拽状态
        isDragging = false;
        element.classList.remove('dragging');
        
        // 保存位置到localStorage
        savePosition(element);
      }
      
      // 重置状态
      isDragging = false;
      hasMoved = false;
      mouseDownTime = 0;
      canStartDrag = false; // 重置拖拽标记
      element.classList.remove('dragging');
    });

    // 保存位置到localStorage
    function savePosition(element) {
      const position = {
        left: parseInt(element.style.left) || 0,
        bottom: parseInt(element.style.bottom) || 0
      };
      localStorage.setItem('waifu-position', JSON.stringify(position));
    }
  }
})(); 