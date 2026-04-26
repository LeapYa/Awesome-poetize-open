/**
 * @file MouseHandler - 鼠标处理器
 * @description 处理鼠标点击、拖拽选择、双击选词等事件
 */

import { Position } from '../core/CursorManager';

/**
 * 鼠标处理器
 */
export class MouseHandler {
  /**
   * @param {Object} editor - 编辑器实例
   * @param {HTMLElement} container - 编辑器内容容器
   */
  constructor(editor, container) {
    this.editor = editor;
    this.container = container;
    
    // 鼠标状态
    this.isMouseDown = false;
    this.isDragging = false;
    this.dragStartPosition = null;
    this.lastClickTime = 0;
    this.clickCount = 0;
    
    // 自动滚动
    this.autoScrollTimer = null;
    this.autoScrollSpeed = 0;

    this.handleGlobalMouseMove = this.handleGlobalMouseMove.bind(this);
    this.handleGlobalMouseUp = this.handleGlobalMouseUp.bind(this);
  }

  /**
   * 设置容器元素
   * @param {HTMLElement} container 
   */
  setContainer(container) {
    this.container = container;
  }

  /**
   * 处理鼠标按下
   * @param {MouseEvent} e 
   */
  handleMouseDown(e) {
    // 只处理左键
    if (e.button !== 0) return;
    
    e.preventDefault();
    this.isMouseDown = true;
    this.isDragging = false;
    
    // 聚焦编辑器
    this.editor.focus();
    
    // 检测多击
    const now = Date.now();
    if (now - this.lastClickTime < 400) {
      this.clickCount++;
    } else {
      this.clickCount = 1;
    }
    this.lastClickTime = now;
    
    // 获取点击位置
    const pos = this.getPositionFromEvent(e);
    if (!pos) return;
    
    this.dragStartPosition = pos;
    
    // 根据点击次数处理
    if (this.clickCount === 1) {
      // 单击
      if (e.shiftKey) {
        // Shift+点击：扩展选区
        const anchor = this.editor.cursor.getSelection().start;
        this.editor.cursor.setSelection(
          anchor.line, anchor.column,
          pos.line, pos.column
        );
      } else {
        // 普通点击：设置光标
        this.editor.cursor.setPosition(pos.line, pos.column);
      }
    } else if (this.clickCount === 2) {
      // 双击：选中单词
      this.editor.cursor.setPosition(pos.line, pos.column);
      this.editor.cursor.selectWord();
    } else if (this.clickCount >= 3) {
      // 三击：选中整行
      this.editor.cursor.setPosition(pos.line, pos.column);
      this.editor.cursor.selectLine();
      this.clickCount = 0; // 重置
    }
    
    // 添加全局鼠标事件监听（用于拖拽选择）
    document.addEventListener('mousemove', this.handleGlobalMouseMove);
    document.addEventListener('mouseup', this.handleGlobalMouseUp);
  }

  /**
   * 处理鼠标移动（容器内）
   * @param {MouseEvent} e 
   */
  handleMouseMove(e) {
    if (!this.isMouseDown) return;
    
    this.isDragging = true;
    const pos = this.getPositionFromEvent(e);
    
    if (pos && this.dragStartPosition) {
      // 扩展选区
      this.editor.cursor.setSelection(
        this.dragStartPosition.line,
        this.dragStartPosition.column,
        pos.line,
        pos.column
      );
    }
  }

  /**
   * 处理全局鼠标移动（用于拖出容器时的选择）
   * @param {MouseEvent} e 
   */
  handleGlobalMouseMove(e) {
    if (!this.isMouseDown) return;
    
    this.isDragging = true;
    
    // 检查是否需要自动滚动
    this.checkAutoScroll(e);
    
    const pos = this.getPositionFromEvent(e);
    if (pos && this.dragStartPosition) {
      this.editor.cursor.setSelection(
        this.dragStartPosition.line,
        this.dragStartPosition.column,
        pos.line,
        pos.column
      );
    }
  }

  /**
   * 处理鼠标释放
   * @param {MouseEvent} e 
   */
  handleMouseUp(e) {
    this.isMouseDown = false;
    this.isDragging = false;
    this.stopAutoScroll();
  }

  /**
   * 处理全局鼠标释放
   * @param {MouseEvent} e 
   */
  handleGlobalMouseUp(e) {
    this.isMouseDown = false;
    this.isDragging = false;
    this.stopAutoScroll();
    
    // 移除全局监听
    document.removeEventListener('mousemove', this.handleGlobalMouseMove);
    document.removeEventListener('mouseup', this.handleGlobalMouseUp);
  }

  /**
   * 处理双击
   * @param {MouseEvent} e 
   */
  handleDoubleClick(e) {
    const pos = this.getPositionFromEvent(e);
    if (pos) {
      this.editor.cursor.setPosition(pos.line, pos.column);
      this.editor.cursor.selectWord();
    }
  }

  /**
   * 处理三击（选中整行）
   * @param {MouseEvent} e 
   */
  handleTripleClick(e) {
    const pos = this.getPositionFromEvent(e);
    if (pos) {
      this.editor.cursor.setPosition(pos.line, pos.column);
      this.editor.cursor.selectLine();
    }
  }

  /**
   * 从鼠标事件获取文档位置
   * @param {MouseEvent} e 
   * @returns {Position|null}
   */
  getPositionFromEvent(e) {
    if (!this.container) return null;
    
    const linesContainer = this.container.querySelector('.lines-container') || this.container;
    const rect = linesContainer.getBoundingClientRect();
    
    // 计算相对位置（考虑滚动）
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top + linesContainer.scrollTop;
    
    // 找到行
    const lineElements = linesContainer.querySelectorAll('.editor-line');
    if (lineElements.length === 0) {
      return new Position(0, 0);
    }
    
    let lineIndex = 0;
    let accumulatedHeight = 0;
    
    for (let i = 0; i < lineElements.length; i++) {
      const lineHeight = lineElements[i].offsetHeight;
      
      if (y < accumulatedHeight + lineHeight) {
        lineIndex = i;
        break;
      }
      accumulatedHeight += lineHeight;
      lineIndex = i;
    }
    
    // 边界处理
    if (y < 0) {
      lineIndex = 0;
    } else if (lineIndex >= lineElements.length) {
      lineIndex = lineElements.length - 1;
    }
    
    // 找到列
    const column = this.getColumnFromX(lineIndex, x, lineElements[lineIndex]);
    
    return new Position(lineIndex, column);
  }

  /**
   * 从 X 坐标获取列号
   * @param {number} lineIndex 
   * @param {number} x 
   * @param {HTMLElement} lineElement 
   * @returns {number}
   */
  getColumnFromX(lineIndex, x, lineElement) {
    if (!lineElement) return 0;
    
    const lineText = this.editor.document.getLineText(lineIndex);
    if (!lineText) return 0;
    
    // 方法1：使用 Range API 精确定位（如果可能）
    const textNode = this.findTextNode(lineElement);
    if (textNode && textNode.nodeType === Node.TEXT_NODE) {
      return this.getColumnFromTextNode(textNode, x, lineElement);
    }
    
    // 方法2：使用临时元素测量
    return this.getColumnByMeasurement(lineIndex, x, lineElement, lineText);
  }

  /**
   * 查找行元素中的文本节点
   * @param {HTMLElement} element 
   * @returns {Node|null}
   */
  findTextNode(element) {
    const walker = document.createTreeWalker(
      element,
      NodeFilter.SHOW_TEXT,
      null,
      false
    );
    return walker.nextNode();
  }

  /**
   * 从文本节点获取列号（使用 Range API）
   * @param {Node} textNode 
   * @param {number} x 
   * @param {HTMLElement} lineElement 
   * @returns {number}
   */
  getColumnFromTextNode(textNode, x, lineElement) {
    const text = textNode.textContent;
    const lineRect = lineElement.getBoundingClientRect();
    const relativeX = x;
    
    // 二分查找最接近的位置
    let low = 0;
    let high = text.length;
    
    const range = document.createRange();
    
    while (low < high) {
      const mid = Math.floor((low + high) / 2);
      
      range.setStart(textNode, 0);
      range.setEnd(textNode, mid);
      
      const rangeRect = range.getBoundingClientRect();
      const rangeWidth = rangeRect.width;
      
      if (rangeWidth < relativeX) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    
    // 检查是否更接近前一个还是当前位置
    if (low > 0 && low <= text.length) {
      range.setStart(textNode, 0);
      range.setEnd(textNode, low);
      const currentWidth = range.getBoundingClientRect().width;
      
      range.setEnd(textNode, low - 1);
      const prevWidth = range.getBoundingClientRect().width;
      
      if (relativeX - prevWidth < currentWidth - relativeX) {
        low--;
      }
    }
    
    return Math.max(0, Math.min(low, text.length));
  }

  /**
   * 通过测量获取列号
   * @param {number} lineIndex 
   * @param {number} x 
   * @param {HTMLElement} lineElement 
   * @param {string} lineText 
   * @returns {number}
   */
  getColumnByMeasurement(lineIndex, x, lineElement, lineText) {
    // 创建临时测量元素
    const measureSpan = document.createElement('span');
    measureSpan.style.cssText = `
      position: absolute;
      visibility: hidden;
      white-space: pre;
      font: inherit;
    `;
    lineElement.appendChild(measureSpan);
    
    let column = 0;
    let prevWidth = 0;
    
    for (let i = 0; i <= lineText.length; i++) {
      measureSpan.textContent = lineText.substring(0, i);
      const currentWidth = measureSpan.offsetWidth;
      
      if (currentWidth >= x) {
        // 判断更接近前一个还是当前位置
        if (i > 0 && (x - prevWidth) < (currentWidth - x)) {
          column = i - 1;
        } else {
          column = i;
        }
        break;
      }
      
      prevWidth = currentWidth;
      column = i;
    }
    
    lineElement.removeChild(measureSpan);
    
    return Math.min(column, lineText.length);
  }

  /**
   * 检查是否需要自动滚动
   * @param {MouseEvent} e 
   */
  checkAutoScroll(e) {
    if (!this.container) return;
    
    const rect = this.container.getBoundingClientRect();
    const scrollMargin = 30; // 触发滚动的边距
    const maxSpeed = 10; // 最大滚动速度
    
    let speed = 0;
    
    if (e.clientY < rect.top + scrollMargin) {
      // 向上滚动
      speed = -Math.min(maxSpeed, (rect.top + scrollMargin - e.clientY) / 2);
    } else if (e.clientY > rect.bottom - scrollMargin) {
      // 向下滚动
      speed = Math.min(maxSpeed, (e.clientY - rect.bottom + scrollMargin) / 2);
    }
    
    if (speed !== 0 && speed !== this.autoScrollSpeed) {
      this.startAutoScroll(speed);
    } else if (speed === 0) {
      this.stopAutoScroll();
    }
  }

  /**
   * 开始自动滚动
   * @param {number} speed 
   */
  startAutoScroll(speed) {
    this.stopAutoScroll();
    this.autoScrollSpeed = speed;
    
    const scroll = () => {
      if (!this.container || !this.isMouseDown) {
        this.stopAutoScroll();
        return;
      }
      
      this.container.scrollTop += this.autoScrollSpeed;
      this.autoScrollTimer = requestAnimationFrame(scroll);
    };
    
    this.autoScrollTimer = requestAnimationFrame(scroll);
  }

  /**
   * 停止自动滚动
   */
  stopAutoScroll() {
    if (this.autoScrollTimer) {
      cancelAnimationFrame(this.autoScrollTimer);
      this.autoScrollTimer = null;
    }
    this.autoScrollSpeed = 0;
  }

  /**
   * 销毁处理器
   */
  destroy() {
    this.stopAutoScroll();
    document.removeEventListener('mousemove', this.handleGlobalMouseMove);
    document.removeEventListener('mouseup', this.handleGlobalMouseUp);
    this.editor = null;
    this.container = null;
  }
}

export default MouseHandler;
