/**
 * AI 聊天图片 IndexedDB 存储
 *
 * 使用原生 IndexedDB API 封装，存储 base64 图片数据。
 * 图片不经过服务器上传，直接以 base64 形式存储在浏览器端，
 * 发送时内联到请求体中，服务器零存储。
 *
 * 数据库结构：
 * - DB 名: ai_chat_images
 * - Store 名: images
 * - key: imageId（时间戳+随机数）
 * - value: { id, dataUrl, mimeType, size, createTime, messageId? }
 *
 * 另有一个 meta store 用于存储消息-图片关联：
 * - Store 名: message_images
 * - key: messageId
 * - value: { messageId, imageIds: [], createTime }
 */

const DB_NAME = 'ai_chat_images';
const DB_VERSION = 1;
const STORE_IMAGES = 'images';
const STORE_MESSAGE_IMAGES = 'message_images';

let dbInstance = null;
// 正在进行的 open 请求，用于并发去重
let dbOpenPromise = null;

/**
 * 打开/创建数据库
 * <p>
 * 注意：
 * - 使用 dbOpenPromise 去重，避免并发调用时发起多个 open 请求
 * - 监听 onclose（浏览器关闭连接）和 onversionchange（其他标签页升级），
 *   失效时重置 dbInstance 以便下次调用重新打开
 */
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
      // 连接被浏览器关闭（内存压力、清除数据等）时，重置缓存以便下次重新打开
      db.onclose = () => {
        if (dbInstance === db) {
          dbInstance = null;
        }
      };
      // 其他标签页要升级 DB 版本时，主动关闭当前连接避免阻塞升级
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

      // 图片存储
      if (!db.objectStoreNames.contains(STORE_IMAGES)) {
        const imageStore = db.createObjectStore(STORE_IMAGES, { keyPath: 'id' });
        imageStore.createIndex('createTime', 'createTime', { unique: false });
        imageStore.createIndex('messageId', 'messageId', { unique: false });
      }

      // 消息-图片关联存储
      if (!db.objectStoreNames.contains(STORE_MESSAGE_IMAGES)) {
        const msgStore = db.createObjectStore(STORE_MESSAGE_IMAGES, { keyPath: 'messageId' });
        msgStore.createIndex('createTime', 'createTime', { unique: false });
      }
    };
  });

  return dbOpenPromise;
}

/**
 * 生成唯一图片 ID
 */
function generateImageId() {
  return `img_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}

/**
 * 存储一张图片
 * @param {string} dataUrl base64 data URL
 * @param {string} mimeType MIME 类型
 * @param {number} size 原始字节大小
 * @returns {Promise<string>} imageId
 */
export async function saveImage(dataUrl, mimeType, size) {
  const db = await openDB();
  const imageId = generateImageId();
  const record = {
    id: imageId,
    dataUrl,
    mimeType,
    size,
    createTime: Date.now(),
    messageId: null,
  };

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES], 'readwrite');
    const store = tx.objectStore(STORE_IMAGES);
    store.add(record);
    // 等待事务提交完成再返回，避免事务被中止后调用方仍拿到 imageId
    tx.oncomplete = () => resolve(imageId);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 获取一张图片
 * @param {string} imageId
 * @returns {Promise<Object|null>} 图片记录
 */
export async function getImage(imageId) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES], 'readonly');
    const store = tx.objectStore(STORE_IMAGES);
    const request = store.get(imageId);
    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 批量获取图片
 * @param {string[]} imageIds
 * @returns {Promise<Object[]>} 图片记录数组（顺序与 imageIds 一致，跳过不存在的）
 */
export async function getImages(imageIds) {
  const db = await openDB();
  if (!imageIds || imageIds.length === 0) {
    return [];
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES], 'readonly');
    const store = tx.objectStore(STORE_IMAGES);
    // 预分配数组按索引填充，保证结果顺序与输入一致
    const results = new Array(imageIds.length).fill(null);
    let completed = 0;

    imageIds.forEach((id, index) => {
      const request = store.get(id);
      request.onsuccess = () => {
        if (request.result) {
          results[index] = request.result;
        }
        completed++;
        if (completed === imageIds.length) {
          resolve(results.filter((r) => r !== null));
        }
      };
      request.onerror = () => {
        completed++;
        if (completed === imageIds.length) {
          resolve(results.filter((r) => r !== null));
        }
      };
    });

    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 将图片关联到某条消息
 * @param {string} messageId 消息 ID
 * @param {string[]} imageIds 图片 ID 数组
 */
export async function associateImagesToMessage(messageId, imageIds) {
  if (!imageIds || imageIds.length === 0) return;

  const db = await openDB();

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES, STORE_MESSAGE_IMAGES], 'readwrite');

    // 更新图片记录的 messageId
    const imageStore = tx.objectStore(STORE_IMAGES);
    imageIds.forEach((id) => {
      const getRequest = imageStore.get(id);
      getRequest.onsuccess = () => {
        if (getRequest.result) {
          getRequest.result.messageId = messageId;
          imageStore.put(getRequest.result);
        }
      };
    });

    // 写入消息-图片关联
    const msgStore = tx.objectStore(STORE_MESSAGE_IMAGES);
    msgStore.put({
      messageId,
      imageIds,
      createTime: Date.now(),
    });

    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 获取某条消息关联的图片 dataUrl 数组
 * @param {string} messageId
 * @returns {Promise<string[]>} dataUrl 数组（顺序与关联的 imageIds 一致）
 */
export async function getMessageImages(messageId) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGE_IMAGES, STORE_IMAGES], 'readonly');
    const msgStore = tx.objectStore(STORE_MESSAGE_IMAGES);
    const msgRequest = msgStore.get(messageId);

    msgRequest.onsuccess = () => {
      const record = msgRequest.result;
      if (!record || !record.imageIds || record.imageIds.length === 0) {
        resolve([]);
        return;
      }

      const imageStore = tx.objectStore(STORE_IMAGES);
      // 预分配数组按索引填充，保证结果顺序与关联记录一致
      const dataUrls = new Array(record.imageIds.length).fill(null);
      let completed = 0;

      record.imageIds.forEach((id, index) => {
        const imgRequest = imageStore.get(id);
        imgRequest.onsuccess = () => {
          if (imgRequest.result) {
            dataUrls[index] = imgRequest.result.dataUrl;
          }
          completed++;
          if (completed === record.imageIds.length) {
            resolve(dataUrls.filter((d) => d !== null));
          }
        };
        imgRequest.onerror = () => {
          completed++;
          if (completed === record.imageIds.length) {
            resolve(dataUrls.filter((d) => d !== null));
          }
        };
      });
    };

    msgRequest.onerror = () => reject(msgRequest.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 删除一张图片
 * @param {string} imageId
 */
export async function deleteImage(imageId) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES], 'readwrite');
    const store = tx.objectStore(STORE_IMAGES);
    const request = store.delete(imageId);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 删除某条消息关联的所有图片
 * @param {string} messageId
 */
export async function deleteMessageImages(messageId) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_MESSAGE_IMAGES, STORE_IMAGES], 'readwrite');
    const msgStore = tx.objectStore(STORE_MESSAGE_IMAGES);
    const msgRequest = msgStore.get(messageId);

    msgRequest.onsuccess = () => {
      const record = msgRequest.result;
      if (record && record.imageIds) {
        const imageStore = tx.objectStore(STORE_IMAGES);
        record.imageIds.forEach((id) => {
          imageStore.delete(id);
        });
      }
      msgStore.delete(messageId);
    };

    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 清空所有图片
 */
export async function clearAllImages() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES, STORE_MESSAGE_IMAGES], 'readwrite');
    tx.objectStore(STORE_IMAGES).clear();
    tx.objectStore(STORE_MESSAGE_IMAGES).clear();
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 获取存储统计信息
 * @returns {Promise<{count: number, totalSizeMB: number}>}
 */
export async function getStorageStats() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES], 'readonly');
    const store = tx.objectStore(STORE_IMAGES);
    const request = store.getAll();

    request.onsuccess = () => {
      const records = request.result || [];
      const totalSize = records.reduce((sum, r) => sum + (r.size || 0), 0);
      resolve({
        count: records.length,
        totalSizeMB: totalSize / (1024 * 1024),
      });
    };
    request.onerror = () => reject(request.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 清理超过指定天数的图片及对应的关联记录
 * @param {number} maxAgeDays 最大保留天数
 * @returns {Promise<number>} 删除的图片数量
 */
export async function cleanupOldImages(maxAgeDays = 7) {
  const db = await openDB();
  const cutoffTime = Date.now() - maxAgeDays * 24 * 60 * 60 * 1000;

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_IMAGES, STORE_MESSAGE_IMAGES], 'readwrite');
    let deletedCount = 0;

    // 清理过期的图片记录
    const imageStore = tx.objectStore(STORE_IMAGES);
    const imageIndex = imageStore.index('createTime');
    const range = IDBKeyRange.upperBound(cutoffTime);

    const imageCursorRequest = imageIndex.openCursor(range);
    imageCursorRequest.onsuccess = (event) => {
      const cursor = event.target.result;
      if (cursor) {
        cursor.delete();
        deletedCount++;
        cursor.continue();
      }
    };
    imageCursorRequest.onerror = () => reject(imageCursorRequest.error);

    // 同时清理过期的消息-图片关联，避免孤儿关联残留
    const msgStore = tx.objectStore(STORE_MESSAGE_IMAGES);
    const msgIndex = msgStore.index('createTime');
    const msgCursorRequest = msgIndex.openCursor(range);
    msgCursorRequest.onsuccess = (event) => {
      const cursor = event.target.result;
      if (cursor) {
        cursor.delete();
        cursor.continue();
      }
    };
    msgCursorRequest.onerror = () => reject(msgCursorRequest.error);

    tx.oncomplete = () => resolve(deletedCount);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}
