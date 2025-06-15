'use strict';

const express = require('express');
const axios = require('axios');
const path = require('path');
const { marked } = require('marked');
const compression = require('compression');
const helmet = require('helmet');

// 环境变量
const PORT = process.env.PORT || 3000;
const JAVA_BACKEND_URL = process.env.JAVA_BACKEND_URL || 'http://poetize-java:8081';
const PYTHON_BACKEND_URL = process.env.PYTHON_BACKEND_URL || 'http://poetize-python:5000';

const app = express();

// 安全与性能中间件
app.use(compression());
app.use(helmet());

// 设置模板引擎
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');
app.locals.rmWhitespace = true;

// 健康检查
app.get('/health', (req, res) => res.send('ok'));

const cache = {
  assets: null,
  lastFetch: 0
};

/**
 * 从首页 HTML 中解析出 app.css 与 app.js 的真实哈希文件名。
 * 解析结果会在内存中缓存 10 分钟以减少额外请求。
 *
 * @param {string} host 请求方的 host，例如 poetize.cn 或 127.0.0.1
 * @returns {Promise<{ css: string, js: string }>} 资源路径对象，找不到则返回空字符串
 */
async function getFrontEndAssets(host) {
  const TEN_MINUTES = 10 * 60 * 1000;
  if (cache.assets && Date.now() - cache.lastFetch < TEN_MINUTES) {
    return cache.assets;
  }

  try {
    const indexUrl = `http://${host}`;
    const htmlRes = await axios.get(indexUrl, { timeout: 5000 });
    const html = htmlRes.data || '';

    const cssMatch = html.match(/\/(css|static\/css)\/app[^"']+\.css/);
    const jsMatch = html.match(/\/(js|static\/js)\/app[^"']+\.js/);

    cache.assets = {
      css: cssMatch ? cssMatch[0] : '/css/app.css',
      js: jsMatch ? jsMatch[0] : '/js/app.js'
    };
    cache.lastFetch = Date.now();
  } catch (e) {
    console.warn('获取前端资源路径失败，使用默认路径 /css/app.css, /js/app.js');
    cache.assets = {
      css: '/css/app.css',
      js: '/js/app.js'
    };
  }

  return cache.assets;
}

// 文章 SSR 路由
app.get('/article/:id', async (req, res) => {
  const { id } = req.params;
  const { lang } = req.query;
  try {
    // 获取文章详情
    const articleRes = await axios.get(`${JAVA_BACKEND_URL}/article/getArticleById`, { params: { id, lang } });
    const articleData = (articleRes.data && articleRes.data.data) || null;

    if (!articleData) {
      return res.status(404).send('Article Not Found');
    }

    let contentHtml = articleData.articleContent || '';
    let articleTitle = articleData.articleTitle || '';

    // 若需要英文或其他语言版本，尝试获取翻译
    if (lang && lang !== 'zh') {
      try {
        const translationRes = await axios.get(`${JAVA_BACKEND_URL}/article/getTranslation`, {
          params: { id, language: lang }
        });
        if (translationRes.data && translationRes.data.code === 200 && translationRes.data.data) {
          const tData = translationRes.data.data;
          if (tData.content) {
            contentHtml = tData.content;
          }
          if (tData.title) {
            articleTitle = tData.title;
          }
        }
      } catch (e) {
        console.warn(`获取文章翻译失败，使用原文，id=${id}, lang=${lang}`);
      }
    }

    // 将 Markdown 或纯文本 转为 HTML（如果内容非 HTML）
    const looksLikeHtml = /<\s*(p|img|h1|h2|h3|h4|blockquote|ul|ol|li|section|div)[^>]*>/i.test(contentHtml);
    if (!looksLikeHtml) {
      contentHtml = marked.parse(contentHtml);
    }

    // 获取 SEO 元数据
    let meta = {};
    try {
      const seoRes = await axios.get(`${PYTHON_BACKEND_URL}/python/seo/getArticleMeta`, { params: { id, lang } });
      if (seoRes.data && seoRes.data.status === 'success') {
        meta = seoRes.data.data || {};
      }
    } catch (e) {
      // 忽略 SEO 获取错误
    }

    // 获取前端静态资源路径
    const assets = await getFrontEndAssets(req.headers.host);

    // 渲染页面
    res.render('article', {
      title: meta.title || articleTitle || 'Poetize',
      meta,
      content: contentHtml,
      assets,
      lang: lang || 'zh'
    });
  } catch (err) {
    console.error('SSR error:', err.message);
    res.status(500).send('Internal Server Error');
  }
});

// 兜底：直接返回 404
app.use((req, res) => res.status(404).send('Not Found'));

app.listen(PORT, () => {
  console.log(`Article SSR service listening on port ${PORT}`);
}); 