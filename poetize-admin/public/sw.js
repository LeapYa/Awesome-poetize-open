// POETIZE PWA Service Worker
// 提供智能缓存和PWA功能

const CACHE_PREFIX = 'poetize-admin-cache-';
const CACHE_NAME = `${CACHE_PREFIX}v1.0.1`;
const BASE_PATH = new URL(self.registration.scope).pathname.replace(/\/$/, '');

function withBase(path = '') {
  return `${BASE_PATH}${path}`;
}

// 需要预缓存的关键资源
const PRECACHE_RESOURCES = [
  withBase('/'),
  withBase('/static/assets/poetize.jpg')
];

// 安装Service Worker时预缓存关键资源
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(PRECACHE_RESOURCES))
      .then(() => self.skipWaiting())
      .catch(error => console.error('SW: 预缓存失败:', error))
  );
});

// 激活Service Worker时清理旧缓存
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(cacheNames => {
        return Promise.all(
          cacheNames.map(cacheName => {
            if (cacheName.startsWith(CACHE_PREFIX) && cacheName !== CACHE_NAME) {
              return caches.delete(cacheName);
            }
          })
        );
      })
      .then(() => self.clients.claim())
  );
});

// 网络请求拦截和缓存策略
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);

  // 只处理GET请求
  if (request.method !== 'GET') return;

  // 跳过chrome-extension请求
  if (url.protocol === 'chrome-extension:') return;

  // 不同类型资源使用不同缓存策略
  if (isPageRequest(request)) {
    event.respondWith(handlePageRequest(request));
  } else if (isStaticAsset(request)) {
    event.respondWith(handleStaticAsset(request));
  } else if (isApiRequest(request)) {
    event.respondWith(handleApiRequest(request));
  }
});

// 检查是否为页面请求
function isPageRequest(request) {
  return request.mode === 'navigate' ||
    (request.method === 'GET' && request.headers.get('accept').includes('text/html'));
}

// 检查是否为静态资源
function isStaticAsset(request) {
  const url = new URL(request.url);
  return url.pathname.match(/\.(css|js|png|jpg|jpeg|gif|svg|webp|ico|woff|woff2|ttf|eot|json|mp4)$/);
}

// 检查是否为API请求
function isApiRequest(request) {
  const url = new URL(request.url);
  return url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/webInfo/') ||
    url.pathname.startsWith('/seo/');
}

// 处理页面请求：网络优先
async function handlePageRequest(request) {
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    const cachedResponse = await caches.match(request);
    if (cachedResponse) return cachedResponse;
    throw error;
  }
}

// 处理静态资源：缓存优先
async function handleStaticAsset(request) {
  const cachedResponse = await caches.match(request);
  if (cachedResponse) return cachedResponse;

  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    // 返回服务不可用响应，避免抛出未捕获的Promise错误
    return new Response('服务暂时不可用', {
      status: 503,
      statusText: 'Service Unavailable',
      headers: new Headers({ 'Content-Type': 'text/plain; charset=utf-8' })
    });
  }
}

// 处理API请求：网络优先
async function handleApiRequest(request) {
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok && shouldCacheApiResponse(request)) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    if (shouldReturnCachedApiResponse(request)) {
      const cachedResponse = await caches.match(request);
      if (cachedResponse) return cachedResponse;
    }
    throw error;
  }
}

// 判断是否应该缓存API响应
function shouldCacheApiResponse(request) {
  const url = new URL(request.url);
  return url.pathname.includes('/webInfo/getWebInfo') ||
    url.pathname.includes('/seo/getSeoConfig');
}

// 判断是否应该返回缓存的API响应
function shouldReturnCachedApiResponse(request) {
  const url = new URL(request.url);
  return url.pathname.includes('/webInfo/getWebInfo');
}

// 监听消息（用于与主线程通信）
self.addEventListener('message', event => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});
