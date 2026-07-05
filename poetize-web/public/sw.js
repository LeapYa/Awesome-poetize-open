// POETIZE PWA Service Worker
// 提供智能缓存和PWA功能

const CACHE_NAME = 'pwa-cache-v1.0.3-live2d';

// 需要预缓存的关键资源
const PRECACHE_RESOURCES = [
  '/',
  '/static/css/inline-styles.css',
  '/libs/css/highlight.min.css',
  '/libs/js/anime.min.js',
  '/libs/js/highlight.min.js'
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
            if (cacheName !== CACHE_NAME) {
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

  // 不同类型资源使用不同缓存策略（注意判断顺序：带 hash 的构建产物优先）
  if (isPageRequest(request)) {
    event.respondWith(handlePageRequest(request));
  } else if (isHashedBuildAsset(request)) {
    event.respondWith(handleHashedBuildAsset(request));
  } else if (isMutableDefaultIcon(request)) {
    event.respondWith(handleMutableDefaultIcon(request));
  } else if (isLive2DResource(request)) {
    event.respondWith(handleLive2DResource(request));
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
  // 排除 manifest.json，避免浏览器使用缓存导致网站名称等配置更新不生效
  if (url.pathname === '/manifest.json') return false;
  return url.pathname.match(/\.(css|js|png|jpg|jpeg|gif|svg|webp|ico|woff|woff2|ttf|eot|json|mp4)$/);
}

// Vite/Rolldown 构建产物通常带 hash（如 /static/index-CqjO_wHz.js、/static/ep-actions-O6yX_5R4.css）
// 文件名格式为 name-hash.ext，hash 字符集为 base64url（大小写字母+数字+下划线+连字符）
// 这类资源必须走网络优先，否则新版本部署后旧页面引用已删除的 chunk 会触发 vite:preloadError
function isHashedBuildAsset(request) {
  const url = new URL(request.url);
  const pathname = url.pathname;
  return /-[\w-]{6,}\.(js|css)(\?.*)?$/i.test(pathname) &&
    (pathname.startsWith('/static/') || pathname.startsWith('/assets/'));
}

// 检查是否为API请求
function isApiRequest(request) {
  const url = new URL(request.url);
  return url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/webInfo/') ||
    url.pathname.startsWith('/seo/');
}

function isMutableDefaultIcon(request) {
  const url = new URL(request.url);
  return url.pathname === '/static/assets/poetize.jpg' || url.pathname === '/assets/poetize.jpg';
}

function isLive2DResource(request) {
  const url = new URL(request.url);
  return url.pathname.startsWith('/static/live2d_api/') ||
    url.pathname.startsWith('/static/live2d-widget/');
}

function isHtmlFallbackResponse(request, response) {
  const url = new URL(request.url);
  const contentType = response.headers.get('content-type') || '';
  return !url.pathname.endsWith('.html') && contentType.includes('text/html');
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

// 处理带 hash 的 Vite/Rolldown 构建产物：网络优先
// 这些资源文件名包含内容 hash，新版本部署后旧文件会被删除，缓存优先会导致 vite:preloadError
async function handleHashedBuildAsset(request) {
  try {
    const networkResponse = await fetch(request, { cache: 'no-cache' });
    // 不要把 HTML fallback（如 SPA 回退页）缓存成 JS/CSS
    if (networkResponse.ok && !isHtmlFallbackResponse(request, networkResponse)) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    // 网络失败时回退到缓存，尽量保证可用性
    const cachedResponse = await caches.match(request);
    if (cachedResponse) return cachedResponse;
    return new Response('服务暂时不可用', {
      status: 503,
      statusText: 'Service Unavailable',
      headers: new Headers({ 'Content-Type': 'text/plain; charset=utf-8' })
    });
  }
}

// 处理静态资源：缓存优先
async function handleStaticAsset(request) {
  const cachedResponse = await caches.match(request);
  if (cachedResponse) return cachedResponse;

  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok && !isHtmlFallbackResponse(request, networkResponse)) {
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

// Live2D 模型和运行库：网络优先，避免把旧的 HTML fallback 缓存成 JS/JSON/图片
async function handleLive2DResource(request) {
  try {
    const networkResponse = await fetch(request, { cache: 'reload' });
    if (networkResponse.ok && !isHtmlFallbackResponse(request, networkResponse)) {
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

async function handleMutableDefaultIcon(request) {
  try {
    const networkResponse = await fetch(request, { cache: 'reload' });
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
