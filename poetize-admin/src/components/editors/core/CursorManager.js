/**
 * @file CursorManager - 光标和选区管理器
 * @description 维护光标位置、选区范围，支持移动操作
 */

/**
 * 表示一个位置（行号和列号）
 */
export class Position {
  constructor(line = 0, column = 0) {
    this.line = line;
    this.column = column;
  }

  /**
   * 克隆位置
   * @returns {Position}
   */
  clone() {
    return new Position(this.line, this.column);
  }

  /**
   * 判断是否与另一个位置相等
   * @param {Position} other 
   * @returns {boolean}
   */
  equals(other) {
    return this.line === other.line && this.column === other.column;
  }

  /**
   * 比较两个位置
   * @param {Position} other 
   * @returns {number} -1 表示在前，0 表示相同，1 表示在后
   */
  compare(other) {
    if (this.line < other.line) return -1;
    if (this.line > other.line) return 1;
    if (this.column < other.column) return -1;
    if (this.column > other.column) return 1;
    return 0;
  }

  /**
   * 判断是否在另一个位置之前
   * @param {Position} other 
   * @returns {boolean}
   */
  isBefore(other) {
    return this.compare(other) < 0;
  }

  /**
   * 判断是否在另一个位置之后
   * @param {Position} other 
   * @returns {boolean}
   */
  isAfter(other) {
    return this.compare(other) > 0;
  }
}

/**
 * 表示一个选区（开始位置和结束位置）
 */
export class Selection {
  constructor(start = new Position(), end = null) {
    this.start = start;
    this.end = end || start.clone();
  }

  /**
   * 获取选区的锚点（开始位置）
   * @returns {Position}
   */
  getAnchor() {
    return this.start;
  }

  /**
   * 获取选区的头部（结束位置/当前光标位置）
   * @returns {Position}
   */
  getHead() {
    return this.end;
  }

  /**
   * 判断是否为空选区（即光标，没有选中内容）
   * @returns {boolean}
   */
  isEmpty() {
    return this.start.equals(this.end);
  }

  /**
   * 获取选区的最小位置（用于获取实际的开始位置）
   * @returns {Position}
   */
  getMin() {
    return this.start.isBefore(this.end) ? this.start : this.end;
  }

  /**
   * 获取选区的最大位置（用于获取实际的结束位置）
   * @returns {Position}
   */
  getMax() {
    return this.start.isAfter(this.end) ? this.start : this.end;
  }

  /**
   * 收缩选区到光标位置
   */
  collapse() {
    this.start = this.end.clone();
  }

  /**
   * 收缩到开始位置
   */
  collapseToStart() {
    const min = this.getMin();
    this.start = min.clone();
    this.end = min.clone();
  }

  /**
   * 收缩到结束位置
   */
  collapseToEnd() {
    const max = this.getMax();
    this.start = max.clone();
    this.end = max.clone();
  }

  /**
   * 克隆选区
   * @returns {Selection}
   */
  clone() {
    return new Selection(this.start.clone(), this.end.clone());
  }
}

/**
 * 光标管理器
 */
export class CursorManager {
  /**
   * @param {import('./DocumentModel').DocumentModel} document 
   */
  constructor(document) {
    this.document = document;
    this.selection = new Selection();
    this.preferredColumn = 0; // 垂直移动时的首选列位置
    this.listeners = new Set();
  }

  /**
   * 获取当前光标位置
   * @returns {Position}
   */
  getPosition() {
    return this.selection.end.clone();
  }

  /**
   * 获取当前行号
   * @returns {number}
   */
  getLine() {
    return this.selection.end.line;
  }

  /**
   * 获取当前列号
   * @returns {number}
   */
  getColumn() {
    return this.selection.end.column;
  }

  /**
   * 获取选区
   * @returns {Selection}
   */
  getSelection() {
    return this.selection.clone();
  }

  /**
   * 判断是否有选区
   * @returns {boolean}
   */
  hasSelection() {
    return !this.selection.isEmpty();
  }

  /**
   * 设置光标位置（无选区）
   * @param {number} line 
   * @param {number} column 
   */
  setPosition(line, column) {
    const validatedPos = this.validatePosition(line, column);
    this.selection.start = validatedPos;
    this.selection.end = validatedPos.clone();
    this.preferredColumn = validatedPos.column;
    this.notifyChange();
  }

  /**
   * 设置选区
   * @param {number} startLine 
   * @param {number} startColumn 
   * @param {number} endLine 
   * @param {number} endColumn 
   */
  setSelection(startLine, startColumn, endLine, endColumn) {
    this.selection.start = this.validatePosition(startLine, startColumn);
    this.selection.end = this.validatePosition(endLine, endColumn);
    this.preferredColumn = this.selection.end.column;
    this.notifyChange();
  }

  /**
   * 验证并修正位置
   * @param {number} line 
   * @param {number} column 
   * @returns {Position}
   */
  validatePosition(line, column) {
    const lineCount = this.document.getLineCount();
    const validLine = Math.max(0, Math.min(line, lineCount - 1));
    const lineLength = this.document.getLineLength(validLine);
    const validColumn = Math.max(0, Math.min(column, lineLength));
    return new Position(validLine, validColumn);
  }

  /**
   * 向左移动光标
   * @param {boolean} extend 是否扩展选区
   */
  moveLeft(extend = false) {
    const pos = this.selection.end;
    
    if (!extend && this.hasSelection()) {
      // 有选区时，移动到选区开始
      const min = this.selection.getMin();
      this.setPosition(min.line, min.column);
      return;
    }

    let newLine = pos.line;
    let newColumn = pos.column;

    if (pos.column > 0) {
      newColumn = pos.column - 1;
    } else if (pos.line > 0) {
      newLine = pos.line - 1;
      newColumn = this.document.getLineLength(newLine);
    }

    if (extend) {
      this.selection.end = this.validatePosition(newLine, newColumn);
    } else {
      this.setPosition(newLine, newColumn);
    }
    
    this.preferredColumn = this.selection.end.column;
    this.notifyChange();
  }

  /**
   * 向右移动光标
   * @param {boolean} extend 是否扩展选区
   */
  moveRight(extend = false) {
    const pos = this.selection.end;
    
    if (!extend && this.hasSelection()) {
      // 有选区时，移动到选区结束
      const max = this.selection.getMax();
      this.setPosition(max.line, max.column);
      return;
    }

    let newLine = pos.line;
    let newColumn = pos.column;
    const lineLength = this.document.getLineLength(pos.line);

    if (pos.column < lineLength) {
      newColumn = pos.column + 1;
    } else if (pos.line < this.document.getLineCount() - 1) {
      newLine = pos.line + 1;
      newColumn = 0;
    }

    if (extend) {
      this.selection.end = this.validatePosition(newLine, newColumn);
    } else {
      this.setPosition(newLine, newColumn);
    }
    
    this.preferredColumn = this.selection.end.column;
    this.notifyChange();
  }

  /**
   * 向上移动光标
   * @param {boolean} extend 是否扩展选区
   */
  moveUp(extend = false) {
    const pos = this.selection.end;
    
    if (pos.line === 0) {
      // 已在第一行，移动到行首
      if (extend) {
        this.selection.end = new Position(0, 0);
      } else {
        this.setPosition(0, 0);
      }
      this.notifyChange();
      return;
    }

    const newLine = pos.line - 1;
    const lineLength = this.document.getLineLength(newLine);
    const newColumn = Math.min(this.preferredColumn, lineLength);

    if (extend) {
      this.selection.end = new Position(newLine, newColumn);
    } else {
      this.selection.start = new Position(newLine, newColumn);
      this.selection.end = this.selection.start.clone();
    }
    
    this.notifyChange();
  }

  /**
   * 向下移动光标
   * @param {boolean} extend 是否扩展选区
   */
  moveDown(extend = false) {
    const pos = this.selection.end;
    const lineCount = this.document.getLineCount();
    
    if (pos.line === lineCount - 1) {
      // 已在最后一行，移动到行尾
      const lineLength = this.document.getLineLength(pos.line);
      if (extend) {
        this.selection.end = new Position(pos.line, lineLength);
      } else {
        this.setPosition(pos.line, lineLength);
      }
      this.notifyChange();
      return;
    }

    const newLine = pos.line + 1;
    const lineLength = this.document.getLineLength(newLine);
    const newColumn = Math.min(this.preferredColumn, lineLength);

    if (extend) {
      this.selection.end = new Position(newLine, newColumn);
    } else {
      this.selection.start = new Position(newLine, newColumn);
      this.selection.end = this.selection.start.clone();
    }
    
    this.notifyChange();
  }

  /**
   * 移动到行首
   * @param {boolean} extend 是否扩展选区
   */
  moveToLineStart(extend = false) {
    const pos = this.selection.end;
    const lineText = this.document.getLineText(pos.line);
    
    // 智能行首：第一次移动到第一个非空白字符，第二次移动到真正的行首
    const firstNonWhitespace = lineText.search(/\S/);
    const targetColumn = firstNonWhitespace >= 0 && pos.column > firstNonWhitespace 
      ? firstNonWhitespace 
      : 0;

    if (extend) {
      this.selection.end = new Position(pos.line, targetColumn);
    } else {
      this.setPosition(pos.line, targetColumn);
    }
    
    this.notifyChange();
  }

  /**
   * 移动到行尾
   * @param {boolean} extend 是否扩展选区
   */
  moveToLineEnd(extend = false) {
    const pos = this.selection.end;
    const lineLength = this.document.getLineLength(pos.line);

    if (extend) {
      this.selection.end = new Position(pos.line, lineLength);
    } else {
      this.setPosition(pos.line, lineLength);
    }
    
    this.notifyChange();
  }

  /**
   * 移动到文档开头
   * @param {boolean} extend 是否扩展选区
   */
  moveToDocumentStart(extend = false) {
    if (extend) {
      this.selection.end = new Position(0, 0);
    } else {
      this.setPosition(0, 0);
    }
    this.notifyChange();
  }

  /**
   * 移动到文档结尾
   * @param {boolean} extend 是否扩展选区
   */
  moveToDocumentEnd(extend = false) {
    const lastLine = this.document.getLineCount() - 1;
    const lastColumn = this.document.getLineLength(lastLine);
    
    if (extend) {
      this.selection.end = new Position(lastLine, lastColumn);
    } else {
      this.setPosition(lastLine, lastColumn);
    }
    this.notifyChange();
  }

  /**
   * 按单词向左移动
   * @param {boolean} extend 是否扩展选区
   */
  moveWordLeft(extend = false) {
    const pos = this.selection.end;
    const lineText = this.document.getLineText(pos.line);
    
    if (pos.column === 0) {
      // 在行首，移动到上一行末尾
      if (pos.line > 0) {
        const prevLineLength = this.document.getLineLength(pos.line - 1);
        if (extend) {
          this.selection.end = new Position(pos.line - 1, prevLineLength);
        } else {
          this.setPosition(pos.line - 1, prevLineLength);
        }
      }
      this.notifyChange();
      return;
    }

    // 找到上一个单词边界
    let col = pos.column - 1;
    const beforeCursor = lineText.substring(0, pos.column);
    
    // 跳过空白
    while (col > 0 && /\s/.test(beforeCursor[col])) {
      col--;
    }
    
    // 跳过单词字符或非单词字符
    if (col > 0) {
      const isWordChar = /\w/.test(beforeCursor[col]);
      while (col > 0 && (isWordChar ? /\w/ : /[^\s\w]/).test(beforeCursor[col - 1])) {
        col--;
      }
    }

    if (extend) {
      this.selection.end = new Position(pos.line, col);
    } else {
      this.setPosition(pos.line, col);
    }
    
    this.notifyChange();
  }

  /**
   * 按单词向右移动
   * @param {boolean} extend 是否扩展选区
   */
  moveWordRight(extend = false) {
    const pos = this.selection.end;
    const lineText = this.document.getLineText(pos.line);
    const lineLength = lineText.length;
    
    if (pos.column >= lineLength) {
      // 在行尾，移动到下一行开头
      if (pos.line < this.document.getLineCount() - 1) {
        if (extend) {
          this.selection.end = new Position(pos.line + 1, 0);
        } else {
          this.setPosition(pos.line + 1, 0);
        }
      }
      this.notifyChange();
      return;
    }

    // 找到下一个单词边界
    let col = pos.column;
    
    // 跳过当前单词字符
    const isWordChar = /\w/.test(lineText[col]);
    while (col < lineLength && (isWordChar ? /\w/ : /[^\s\w]/).test(lineText[col])) {
      col++;
    }
    
    // 跳过空白
    while (col < lineLength && /\s/.test(lineText[col])) {
      col++;
    }

    if (extend) {
      this.selection.end = new Position(pos.line, col);
    } else {
      this.setPosition(pos.line, col);
    }
    
    this.notifyChange();
  }

  /**
   * 选中全部
   */
  selectAll() {
    const lastLine = this.document.getLineCount() - 1;
    const lastColumn = this.document.getLineLength(lastLine);
    
    this.selection.start = new Position(0, 0);
    this.selection.end = new Position(lastLine, lastColumn);
    
    this.notifyChange();
  }

  /**
   * 选中当前行
   */
  selectLine() {
    const line = this.selection.end.line;
    const lineLength = this.document.getLineLength(line);
    
    this.selection.start = new Position(line, 0);
    this.selection.end = new Position(line, lineLength);
    
    this.notifyChange();
  }

  /**
   * 选中当前单词
   */
  selectWord() {
    const pos = this.selection.end;
    const lineText = this.document.getLineText(pos.line);
    
    if (pos.column >= lineText.length) return;
    
    const wordCharRegex = /[\w\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff\u3040-\u30ff\uac00-\ud7af]/;
    const char = lineText[pos.column];
    if (!wordCharRegex.test(char)) return;
    
    // 找单词边界
    let start = pos.column;
    let end = pos.column;
    
    while (start > 0 && wordCharRegex.test(lineText[start - 1])) {
      start--;
    }
    
    while (end < lineText.length && wordCharRegex.test(lineText[end])) {
      end++;
    }
    
    this.selection.start = new Position(pos.line, start);
    this.selection.end = new Position(pos.line, end);
    
    this.notifyChange();
  }

  /**
   * 清除选区（保持光标位置）
   */
  clearSelection() {
    if (!this.hasSelection()) return;
    this.selection.collapse();
    this.notifyChange();
  }

  /**
   * 添加变更监听器
   * @param {Function} listener 
   */
  addChangeListener(listener) {
    this.listeners.add(listener);
  }

  /**
   * 移除变更监听器
   * @param {Function} listener 
   */
  removeChangeListener(listener) {
    this.listeners.delete(listener);
  }

  /**
   * 通知变更
   */
  notifyChange() {
    const change = {
      position: this.getPosition(),
      selection: this.getSelection(),
      hasSelection: this.hasSelection()
    };
    
    this.listeners.forEach(listener => {
      try {
        listener(change);
      } catch (e) {
        console.error('CursorManager change listener error:', e);
      }
    });
  }
}

export default CursorManager;
