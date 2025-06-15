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

// 健康检查
app.get('/health', (req, res) => res.send('ok'));

// 文章 SSR 路由
app.get('/article/:id', async (req, res) => {
  const { id } = req.params;
  try {
    // 获取文章详情
    const articleRes = await axios.get(`${JAVA_BACKEND_URL}/article/getArticleById`, { params: { id } });
    const articleData = (articleRes.data && articleRes.data.data) || null;

    if (!articleData) {
      return res.status(404).send('Article Not Found');
    }

    // 将 Markdown 转为 HTML（如果内容非 HTML）
    let contentHtml = articleData.articleContent || '';
    const looksLikeHtml = /<\s*(p|img|h1|h2|h3|h4|blockquote|ul|ol|li|section|div)[^>]*>/i.test(contentHtml);
    if (!looksLikeHtml) {
      contentHtml = marked.parse(contentHtml);
    }

    // 获取 SEO 元数据
    let meta = {};
    try {
      const seoRes = await axios.get(`${PYTHON_BACKEND_URL}/python/seo/getArticleMeta`, { params: { id } });
      if (seoRes.data && seoRes.data.status === 'success') {
        meta = seoRes.data.data || {};
      }
    } catch (e) {
      // 忽略 SEO 获取错误
    }

    // 渲染页面
    res.render('article', {
      title: meta.title || articleData.articleTitle || 'Poetize',
      meta,
      content: contentHtml
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