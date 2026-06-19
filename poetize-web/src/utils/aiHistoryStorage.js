/**
 * AI 聊天历史消息 IndexedDB 存储（混合存储方案的持久层）
 *
 * 设计目的：
 * - localStorage 只保留最近 N 条热数据，保证启动快、写入不卡顿
 * - IndexedDB 存储完整历史，突破 5-10MB 容量限制，支持数千条消息
 *
 * 使用独立 DB（ai_chat_data），避免与 ai_chat_images 的版本升级互相干扰。
 *
 * 数据库结构：
 * - DB 名: ai_chat_data
 * - DB_VERSION: 1
 * - Store: messages
 *   - keyPath: id
 *   - index: timestamp（按时间排序/范围查询）
 *   - index: role（按角色筛选）
 *
 * 注意：消息中的图片只存 imageIds 引用，实际图片数据在 ai_chat_images DB 中。
 */

const DB_NAME = 'ai_chat_data';
const DB_VERSION = 1;
const STORE_MESSAGES = 'messages';

let dbInstance = null;
let dbOpenPromise = null;

function openDB() {
  if (dbInstance) {
    return Promise.resolve(dbInstance);
  }
  if (dbOpenPromise) {
    return dbOpenPromise;
  }

  dbOpenPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => {
      dbOpenPromise = null;
      reject(request.error);
    };
    request.onsuccess = () => {
      const db = request.result;
      db.onclose = () => {
        if (dbInstance === db) {
          dbInstance = null;
        }
      };
      db.onversionchange = () => {
        db.close();
        if (dbInstance === db) {
          dbInstance = null;
        }
      };
      dbInstance = db;
      dbOpenPromise = null;
      resolve(db);
    };

    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains(STORE_MESSAGES)) {
        const store = db.createObjectStore(STORE_MESSAGES, { keyPath: 'id' });
        store.createIndex('timestamp', 'timestamp', { unique: false });
        store.createIndex('role', 'role', { unique: false });
      }
    };
  });

  return dbOpenPromise;
}

/**
 * 规范化消息：剥离可能携带的运行时大对象（如 base64 图片），
 * 只保留 imageIds 引用，避免 IndexedDB 体积膨胀。
 */
function slimMessage(msg) {
  if (!msg || typeof msg !== 'object') return null;
  const { images, ...rest } = msg;
  const slim = { ...rest };
  // 显式保留 imageIds
  if (Array.isArray(msg.imageIds) && msg.imageIds.length > 0) {
    slim.imageIds = msg.imageIds.slice();
  } else {
    delete slim.imageIds;
  }
  return slim;
}

/**
 * 写入/更新单条消息（按 id 覆盖）
 * @param {Object} message
 */
export async function saveMessage(message) {
  const slim = slimMessage(message);
  if (!slim) return;
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readwrite');
    tx.objectStore(STORE_MESSAGES).put(slim);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 批量写入/更新消息（单事务，性能优于循环 saveMessage）
 * @param {Object[]} messages
 */
export async function saveMessages(messages) {
  if (!Array.isArray(messages) || messages.length === 0) return;
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readwrite');
    const store = tx.objectStore(STORE_MESSAGES);
    for (const msg of messages) {
      const slim = slimMessage(msg);
      if (slim) {
        store.put(slim);
      }
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 原子地用传入数组完全替换 IndexedDB 中的全部消息。
 * 单事务内先 clear 再批量 put，保证一致性。
 * 用于编辑重发等需要截断历史的场景（saveMessages 用 put 不会删除多余记录）。
 * @param {Object[]} messages 完整的消息数组（截断后的）
 */
export async function replaceAllMessages(messages) {
  const db = await openDB();
  const list = Array.isArray(messages) ? messages : [];
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readwrite');
    const store = tx.objectStore(STORE_MESSAGES);
    store.clear();
    for (const msg of list) {
      const slim = slimMessage(msg);
      if (slim) {
        store.put(slim);
      }
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 获取全部消息，按 timestamp 升序返回
 * @returns {Promise<Object[]>}
 */
export async function getAllMessages() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readonly');
    const index = tx.objectStore(STORE_MESSAGES).index('timestamp');
    const request = index.getAll();
    request.onsuccess = () => resolve(request.result || []);
    request.onerror = () => reject(request.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 获取最近 N 条消息（按 timestamp 升序，返回最后 limit 条）
 * @param {number} limit
 * @returns {Promise<Object[]>}
 */
export async function getRecentMessages(limit) {
  if (!limit || limit <= 0) return [];
  const all = await getAllMessages();
  return all.slice(-limit);
}

/**
 * 删除单条消息
 * @param {string|number} id
 */
export async function deleteMessage(id) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readwrite');
    tx.objectStore(STORE_MESSAGES).delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 清空全部消息
 */
export async function clearMessages() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readwrite');
    tx.objectStore(STORE_MESSAGES).clear();
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 清理早于指定时间戳的消息（用于历史归档/容量控制）
 * @param {number} beforeTimestamp
 * @returns {Promise<number>} 删除的条数
 */
export async function deleteMessagesBefore(beforeTimestamp) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readwrite');
    const index = tx.objectStore(STORE_MESSAGES).index('timestamp');
    const range = IDBKeyRange.upperBound(beforeTimestamp, true);
    let deleted = 0;
    const cursorReq = index.openCursor(range);
    cursorReq.onsuccess = (event) => {
      const cursor = event.target.result;
      if (cursor) {
        cursor.delete();
        deleted++;
        cursor.continue();
      }
    };
    cursorReq.onerror = () => reject(cursorReq.error);
    tx.oncomplete = () => resolve(deleted);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 获取存储的消息数量
 * @returns {Promise<number>}
 */
export async function countMessages() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGES], 'readonly');
    const request = tx.objectStore(STORE_MESSAGES).count();
    request.onsuccess = () => resolve(request.result || 0);
    request.onerror = () => reject(request.error);
    tx.onabort = () => reject(tx.error);
  });
}
