"""
POETIZE博客系统 - SEO优化模块 (FastAPI版本)

主要功能：
- 自动生成文章/分类/站点的SEO元数据
- 支持多搜索引擎推送 (百度、Google、Bing、Yandex、搜狗、360、神马、Yahoo)
- 自动生成并维护sitemap.xml和robots.txt
- 支持AI智能SEO分析和建议
- OpenGraph和Twitter Card支持
- 多语言hreflang标签支持

版本: 2.0.0 (FastAPI)
"""

import os
import json
import httpx
import asyncio
import time
import jwt
import re
from functools import wraps
from fastapi import FastAPI, Request, Depends, Response
from fastapi.responses import JSONResponse, PlainTextResponse
from openai import AsyncOpenAI
from anthropic import AsyncAnthropic
import logging
from urllib.parse import urlparse
from config import JAVA_BACKEND_URL, FRONTEND_URL
from cryptography.fernet import Fernet
import threading
from datetime import datetime
from auth_decorator import admin_required  # 导入管理员权限装饰器
from fnmatch import fnmatch
import xml.etree.ElementTree as ET

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('seo_api')

# 设置默认请求超时时间（秒）
DEFAULT_TIMEOUT = 90

# 数据存储路径
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

# SEO配置文件路径
SEO_CONFIG_FILE = os.path.join(DATA_DIR, 'seo_config.json')
# AI API配置文件路径
AI_API_CONFIG_FILE = os.path.join(DATA_DIR, 'ai_api_config.json')

# 默认SEO配置
DEFAULT_SEO_CONFIG = {
    'robots_txt': '''User-agent: *
Allow: /
Disallow: /admin/
Disallow: /api/
Disallow: /private/

# Sitemap
Sitemap: {site_address}/sitemap.xml

# 搜索引擎爬虫特定规则
User-agent: Baiduspider
Allow: /

User-agent: Googlebot
Allow: /

User-agent: Bingbot
Allow: /

# 爬取延迟
Crawl-delay: 1''',
    # 站点地图排除列表，逗号分隔
    'sitemap_exclude': '/love'
}

# 服务就绪检查
def is_service_ready():
    """
    检查服务是否就绪
    :return: True或False
    """
    try:
        # 检查后端API是否可访问
        if not is_valid_url(JAVA_BACKEND_URL):
            logger.error(f"无效的Java后端URL: {JAVA_BACKEND_URL}")
            return False
            
        # 检查数据目录是否存在
        if not os.path.exists(DATA_DIR):
            logger.error(f"数据目录不存在: {DATA_DIR}")
            return False
            
        # 检查SEO配置是否存在
        if not os.path.exists(SEO_CONFIG_FILE):
            logger.warning(f"SEO配置文件不存在: {SEO_CONFIG_FILE}")
            # 这里不返回False，因为会自动初始化配置
            
        return True
    except Exception as e:
        logger.error(f"服务就绪检查失败: {str(e)}")
        return False

# 处理服务未就绪的通用响应
def handle_service_not_ready(operation_name):
    """
    处理服务未就绪的情况
    :param operation_name: 操作名称
    :return: 错误响应
    """
    logger.error(f"服务未就绪，无法执行：{operation_name}")
    return {"status": "error", "message": f"服务未就绪，请稍后再试。(操作: {operation_name})"}

# 验证URL是否有效
def is_valid_url(url):
    """
    检查URL是否有效
    :param url: 要检查的URL
    :return: True或False
    """
    if not url:
        return False
        
    try:
        result = urlparse(url)
        return all([result.scheme, result.netloc])
    except Exception as e:
        logger.error(f"URL验证失败: {str(e)}")
        return False

# 获取认证头信息
def get_auth_headers():
    """
    获取API请求的认证头信息
    :return: 包含认证信息的请求头字典
    """
    # 这里根据实际认证需求实现
    # 例如，可能需要从配置或环境变量中读取API密钥
    headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    }
    
    # 如果有API密钥，添加到头信息中
    api_key = os.environ.get('API_KEY')
    if api_key:
        headers['X-API-KEY'] = api_key
        
    return headers

# 获取加密密钥
def get_encryption_key():
    """
    获取或生成加密密钥
    :return: 加密密钥
    """
    key_file = os.path.join(DATA_DIR, '.encryption_key')
    
    if os.path.exists(key_file):
        with open(key_file, 'rb') as f:
            return f.read()
    else:
        # 生成新的密钥
        key = Fernet.generate_key()
        with open(key_file, 'wb') as f:
            f.write(key)
        return key

# 加密和解密函数
def encrypt_data(data):
    try:
        key = get_encryption_key()
        cipher = Fernet(key)
        return cipher.encrypt(data.encode()).decode()
    except Exception as e:
        logger.error(f"加密数据出错: {str(e)}")
        return None

def decrypt_data(encrypted_data):
    try:
        key = get_encryption_key()
        cipher = Fernet(key)
        return cipher.decrypt(encrypted_data.encode()).decode()
    except Exception as e:
        logger.error(f"解密数据出错: {str(e)}")
        return None

# 获取SEO配置
async def get_seo_config():
    """获取SEO配置"""
    try:
        # 直接读取配置文件
        with open(SEO_CONFIG_FILE, 'r', encoding='utf-8') as f:
            config = json.load(f)
        return config
    except Exception as e:
        logger.error(f"获取SEO配置出错: {str(e)}")
        logger.exception("获取SEO配置详细错误信息:")
        return {}

# 同步版本的get_seo_config用于向后兼容
def get_seo_config_sync():
    """获取SEO配置（同步版本）"""
    try:
        # 直接读取配置文件
        with open(SEO_CONFIG_FILE, 'r', encoding='utf-8') as f:
            config = json.load(f)
        return config
    except Exception as e:
        logger.error(f"获取SEO配置出错: {str(e)}")
        logger.exception("获取SEO配置详细错误信息:")
        return {}

# 保存SEO配置
def save_seo_config(config):
    try:
        logger.info(f"开始保存SEO配置，配置项数量: {len(config)}")
        logger.info(f"保存的SEO开关状态: {config.get('enable', False)}")
        with open(SEO_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(config, f, ensure_ascii=False, indent=2)
        logger.info(f"SEO配置保存成功: {SEO_CONFIG_FILE}")
        return True
    except Exception as e:
        logger.error(f"保存SEO配置出错: {str(e)}")
        logger.exception("保存SEO配置详细错误信息:")
        return False

# 生成文章的元数据
async def generate_article_meta_tags(article_id, lang=None):
    """
    生成文章页面的元标签
    :param article_id: 文章ID
    :param lang: 语言参数，支持'zh'或'en'
    :return: 包含元标签的字典
    """
    if not is_service_ready():
        return handle_service_not_ready("生成文章元标签")

    # 加载SEO配置
    try:
        seo_config = await get_seo_config()
    except Exception as e:
        logger.error(f"加载SEO配置失败: {e}")
        seo_config = {}

    try:
        logger.info(f"尝试获取文章元数据，文章ID: {article_id}")
        logger.info(f"请求URL: {JAVA_BACKEND_URL}/article/getArticleById?id={article_id}")

        if not is_valid_url(JAVA_BACKEND_URL):
            logger.error(f"后端URL无效: {JAVA_BACKEND_URL}")
            return {"status": "error", "message": "后端URL无效"}

        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{JAVA_BACKEND_URL}/article/getArticleById?id={article_id}",
                headers=get_auth_headers(),
                timeout=DEFAULT_TIMEOUT
            )
        
        logger.info(f"获取文章信息响应状态码: {response.status_code}")
        
        if response.status_code != 200:
            logger.error(f"API请求失败，状态码: {response.status_code}")
            return {"status": "error", "message": f"API请求失败，状态码: {response.status_code}"}

        article_data = response.json().get('data', {})
        if not article_data:
            logger.warning("API返回的数据为空")
            return {"status": "error", "message": "未找到文章数据"}

        logger.info(f"成功获取文章信息，标题: {article_data.get('articleTitle', '无标题')}")

        # 基本元标签
        meta_tags = {
            # OpenGraph标签
            "og:title": article_data.get('articleTitle', ''),
            "og:description": get_article_description(article_data.get('articleContent', '')),
            "og:type": seo_config.get('og_type', 'article'),
            "og:url": f"{seo_config.get('site_address', FRONTEND_URL)}/{seo_config.get('article_url_format', 'article/{id}')}".replace('{id}', str(article_id)),
            "og:image": article_data.get('articleCover', seo_config.get('og_image', '')),
            "og:site_name": seo_config.get('og_site_name', seo_config.get('site_name', '')),
            
            # Twitter标签
            "twitter:card": seo_config.get('twitter_card', 'summary_large_image'),
            "twitter:title": article_data.get('articleTitle', ''),
            "twitter:description": seo_config.get('site_description', ''),
            "twitter:image": article_data.get('articleCover', seo_config.get('og_image', '')),
            "twitter:site": seo_config.get('twitter_site', ''),
            "twitter:creator": seo_config.get('twitter_creator', ''),
            
            # 文章特有标签
            "article:published_time": article_data.get('createTime', ''),
            "article:modified_time": article_data.get('updateTime', ''),
            
            # 文章作者
            "article:author": article_data.get('username', seo_config.get('default_author', '')),
        }
        
        # 基础元标签 (用于HTML head)
        title = article_data.get('articleTitle', '')
        description = get_article_description(article_data.get('articleContent', ''))
        keywords = await get_article_keywords(article_data)
        
        meta_tags.update({
            "title": title,
            "description": description,
            "keywords": keywords,
            "author": article_data.get('username', seo_config.get('default_author', '')),
        })

        # 添加hreflang标签
        article_url = f"{seo_config.get('site_address', FRONTEND_URL)}/{seo_config.get('article_url_format', 'article/{id}')}".replace('{id}', str(article_id))
        
        meta_tags["hreflang_zh"] = f'<link rel="alternate" hreflang="zh" href="{article_url}" />'
        meta_tags["hreflang_en"] = f'<link rel="alternate" hreflang="en" href="{article_url}?lang=en" />'
        
        # 如果指定了语言，添加canonical标签
        if lang:
            if lang == 'en':
                meta_tags["canonical"] = f"{article_url}?lang=en"
            else:
                meta_tags["canonical"] = article_url
        else:
            meta_tags["canonical"] = article_url

        # 添加Pinterest标签
        if seo_config.get('pinterest_description'):
            meta_tags["pinterest:description"] = seo_config.get('pinterest_description')
            
        # 添加LinkedIn标签
        if seo_config.get('linkedin_company_id'):
            meta_tags["linkedin:owner"] = seo_config.get('linkedin_company_id')
            
        # 添加微信/QQ小程序标签
        if seo_config.get('wechat_miniprogram_id'):
            meta_tags["wechat:miniprogram:id"] = seo_config.get('wechat_miniprogram_id')

        if seo_config.get('enable_wechat_miniprogram', False):
            meta_tags["wechat:miniprogram:appid"] = seo_config.get('wechat_miniprogram_appid', '')
            meta_tags["wechat:miniprogram:path"] = seo_config.get('wechat_miniprogram_path', f"pages/article/detail?id={article_id}")
        # 添加网站Logo
        if seo_config.get('site_logo'):
            meta_tags["og:logo"] = seo_config.get('site_logo')
            
        logger.info(f"生成文章元数据成功，元标签数量: {len(meta_tags)}")
        # 移除空值
        meta_tags = {k: v for k, v in meta_tags.items() if v}
        
        return {"status": "success", "data": meta_tags}
    except Exception as e:
        logger.exception(f"生成文章元标签时发生错误: {e}")
        return {"status": "error", "message": f"生成文章元标签时发生错误: {str(e)}"}

# 从文章内容中提取描述
def get_article_description(content, max_length=200):
    try:
        # 移除Markdown标记和HTML标签 - 使用与前端一致的处理方式
        # 移除代码块
        plain_text = re.sub(r'```[\s\S]*?```', '', content)
        
        # 移除行内代码
        plain_text = re.sub(r'`([^`]+)`', r'\1', plain_text)
        
        # 移除标题标记
        plain_text = re.sub(r'#{1,6}\s+', '', plain_text)
        
        # 移除链接，只保留链接文本
        plain_text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', plain_text)
        
        # 移除图片
        plain_text = re.sub(r'!\[([^\]]*)\]\([^)]+\)', '', plain_text)
        
        # 移除强调标记（加粗、斜体）
        plain_text = re.sub(r'(\*\*|__)(.*?)\1', r'\2', plain_text)
        plain_text = re.sub(r'(\*|_)(.*?)\1', r'\2', plain_text)
        
        # 移除引用标记
        plain_text = re.sub(r'^\s*>\s+', '', plain_text, flags=re.MULTILINE)
        
        # 移除分隔线
        plain_text = re.sub(r'^\s*[-*_]{3,}\s*$', '', plain_text, flags=re.MULTILINE)
        
        # 移除列表标记
        plain_text = re.sub(r'^\s*[-*+]\s+', '', plain_text, flags=re.MULTILINE)
        plain_text = re.sub(r'^\s*\d+\.\s+', '', plain_text, flags=re.MULTILINE)
        
        # 移除HTML标签
        plain_text = re.sub(r'<[^>]*>', '', plain_text)
        
        # 将多个换行转换为单个空格
        plain_text = re.sub(r'\n{2,}', ' ', plain_text)
        plain_text = re.sub(r'\n', ' ', plain_text)  # 单个换行转空格
        
        # 清理多余的空格
        plain_text = re.sub(r'\s+', ' ', plain_text).strip()
        
        # 截取适当长度
        if len(plain_text) > max_length:
            return plain_text[:max_length-3] + '...'
        return plain_text
    except Exception as e:
        logger.error(f"提取文章描述出错: {str(e)}")
        return ""

# 从文章数据中提取关键词
async def get_article_keywords(article_data):
    try:
        keywords = []
        
        # 添加文章标题关键词
        title = article_data.get('articleTitle', '')
        if title:
            keywords.append(title)
        
        # 添加分类和标签
        if article_data.get('sort') and article_data['sort'].get('sortName'):
            keywords.append(article_data['sort']['sortName'])
            
        if article_data.get('label') and article_data['label'].get('labelName'):
            keywords.append(article_data['label']['labelName'])
        
        # 获取全局SEO配置的关键词
        seo_config = await get_seo_config()
        site_keywords = seo_config.get('site_keywords', '').split(',')
        keywords.extend([k.strip() for k in site_keywords if k.strip()])
        
        # 去重并限制数量
        unique_keywords = list(dict.fromkeys(keywords))
        return ','.join(unique_keywords[:10])  # 最多10个关键词
    except Exception as e:
        logger.error(f"提取文章关键词出错: {str(e)}")
        return ""

# 生成网站地图
async def generate_sitemap():
    try:
        seo_config = await get_seo_config()
        # 如果SEO功能关闭，跳过生成
        if not seo_config.get('enable', False):
            logger.info("SEO功能已关闭，跳过生成网站地图")
            return None
            
        if not seo_config.get('generate_sitemap', True):
            logger.info("网站地图生成功能已禁用")
            return None
        
        # 确保使用正确的前端URL
        site_url = seo_config.get('site_address', FRONTEND_URL)
        logger.info(f"使用网站地址生成站点地图: {site_url}")
            
        # 创建sitemap基本结构
        sitemap = '<?xml version="1.0" encoding="UTF-8"?>\n'
        sitemap += '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        
        # 初始化去重集合
        url_set = set()
        
        # 添加首页
        sitemap += f'  <url>\n'
        sitemap += f'    <loc>{site_url}/</loc>\n'
        sitemap += f'    <lastmod>{time.strftime("%Y-%m-%d")}</lastmod>\n'
        sitemap += f'    <changefreq>daily</changefreq>\n'
        sitemap += f'    <priority>1.0</priority>\n'
        sitemap += f'  </url>\n'
        
        # 将首页加入去重集合（统一去掉末尾'/')
        url_set.add(f"{site_url}".rstrip('/'))
        
        """ 
        根据后台 webInfo.navConfig 生成通用页面列表，避免硬编码带来无效路径。
        navConfig 示例：
        [
          {"name":"首页","link":"/"},
          {"name":"记录","link":"#","type":"dropdown"},
          {"name":"百宝箱","link":"/favorite"}
        ]
        规则：
          1. 取 link 字段，如未提供则根据 name 转换成已知 path；
          2. 外链、锚点(#) 或空链接不加入 sitemap；
        """

        # 读取排除路径列表
        exclude_raw = seo_config.get('sitemap_exclude', '')
        EXCLUDED_PAGES = {p.strip() for p in exclude_raw.split(',') if p.strip()}

        common_pages: list[str] = []

        try:
            async with httpx.AsyncClient() as client:
                web_resp = await client.get(f"{JAVA_BACKEND_URL}/webInfo/getWebInfo", timeout=5)
            if web_resp.status_code == 200:
                nav_config_raw = web_resp.json().get('data', {}).get('navConfig', '[]')
                nav_items = json.loads(nav_config_raw) if nav_config_raw else []

                # 内置映射，当 link 为空时使用
                name_to_path = {
                    "首页": "/",
                    "记录": "/sort",
                    "百宝箱": "/favorite",
                    "留言": "/message",
                    "联系我": "/user"
                }

                for item in nav_items:
                    raw_link = (item.get('link') or '').strip()
                    if not raw_link or raw_link == "#":
                        raw_link = name_to_path.get(item.get('name', ''), '')

                    # 过滤外链及空值
                    if not raw_link or raw_link.startswith('http') or raw_link.startswith('mailto'):
                        continue

                    # 确保以 / 开头
                    if not raw_link.startswith('/'):
                        raw_link = f"/{raw_link}"

                    # 排除不需要的路径
                    if raw_link not in EXCLUDED_PAGES:
                        common_pages.append(raw_link)

        except Exception as e:
            logger.warning(f"动态获取导航配置失败，回退到默认页面列表: {e}")
            common_pages = [
                "/about",
                "/message",
                "/favorite",
                "/privacy"
            ]

        # 去重保持顺序
        seen = set()
        filtered_pages = []
        for p in common_pages:
            if p not in seen:
                seen.add(p)
                filtered_pages.append(p)

        for page in filtered_pages:
            # 避免出现重复的 //
            full_url = f"{site_url}{page}".replace("//", "/")
            if full_url.startswith("http:/") and not full_url.startswith("http://"):
                full_url = full_url.replace("http:/", "http://")
            if full_url.startswith("https:/") and not full_url.startswith("https://"):
                full_url = full_url.replace("https:/", "https://")

            # 去掉末尾'/'用于集合比较
            normalized = full_url.rstrip('/')

            # 解析路径并使用通配符匹配排除列表
            path_only = urlparse(normalized).path or '/'

            # 若 EXCLUDED_PAGES 为空，则不过滤；支持如 "/admin/*"、"/love"、"*.html" 等通配符
            if EXCLUDED_PAGES and any(fnmatch(path_only, pattern.strip()) for pattern in EXCLUDED_PAGES):
                continue

            if normalized in url_set:
                continue  # 忽略重复
            url_set.add(normalized)

            sitemap += f'  <url>\n'
            sitemap += f'    <loc>{full_url}</loc>\n'
            sitemap += f'    <lastmod>{time.strftime("%Y-%m-%d")}</lastmod>\n'
            sitemap += f'    <changefreq>weekly</changefreq>\n'
            sitemap += f'    <priority>0.8</priority>\n'
            sitemap += f'  </url>\n'
        
        # 尝试添加分类页面
        try:
            logger.info(f"尝试从Java后端获取分类信息: {JAVA_BACKEND_URL}/webInfo/getSortInfo")
            
            # 添加headers来模拟浏览器请求
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
            
            async with httpx.AsyncClient() as client:
                sort_response = await client.get(
                    f"{JAVA_BACKEND_URL}/webInfo/getSortInfo", 
                    headers=headers,
                    timeout=5
                )
            
            logger.info(f"分类信息响应状态码: {sort_response.status_code}")
            
            if sort_response.status_code == 200:
                response_text = sort_response.text[:500] + "..." if len(sort_response.text) > 500 else sort_response.text
                logger.info(f"分类信息响应数据(部分): {response_text}")
                
                sort_data = sort_response.json().get('data', [])
                logger.info(f"成功获取分类信息，共 {len(sort_data)} 个分类")
                
                for sort in sort_data:
                    sort_id = sort.get('id')
                    if sort_id:
                        sort_url_format = seo_config.get('category_url_format', 'sort?sortId={id}')
                        sort_url = f"{site_url}/{sort_url_format}".replace('{id}', str(sort_id))
                        sort_url = sort_url.replace("//", "/")
                        if sort_url.rstrip('/') in url_set:
                            continue
                        url_set.add(sort_url.rstrip('/'))
                        sitemap += f'  <url>\n'
                        sitemap += f'    <loc>{sort_url}</loc>\n'
                        sitemap += f'    <changefreq>{seo_config.get("sitemap_change_frequency", "weekly")}</changefreq>\n'
                        sitemap += f'    <priority>0.8</priority>\n'
                        sitemap += f'  </url>\n'
            else:
                logger.warning(f"获取分类信息失败，状态码: {sort_response.status_code}")
                logger.warning(f"响应内容: {sort_response.text}")
                
                # 尝试备用API
                logger.info("尝试使用备用API获取分类信息...")
                async with httpx.AsyncClient() as client:
                    alt_response = await client.get(
                        f"{JAVA_BACKEND_URL}/sort/getSortList", 
                        headers=headers,
                        timeout=5
                    )
                logger.info(f"备用API响应状态码: {alt_response.status_code}")
                
                if alt_response.status_code == 200:
                    alt_text = alt_response.text[:500] + "..." if len(alt_response.text) > 500 else alt_response.text
                    logger.info(f"备用API响应数据(部分): {alt_text}")
                    
                    alt_data = alt_response.json().get('data', [])
                    logger.info(f"使用备用API成功获取分类信息，共 {len(alt_data)} 个分类")
                    
                    for sort in alt_data:
                        sort_id = sort.get('id')
                        if sort_id:
                            sort_url_format = seo_config.get('category_url_format', 'sort?sortId={id}')
                            sort_url = f"{site_url}/{sort_url_format}".replace('{id}', str(sort_id))
                            sort_url = sort_url.replace("//", "/")
                            if sort_url.rstrip('/') in url_set:
                                continue
                            url_set.add(sort_url.rstrip('/'))
                            sitemap += f'  <url>\n'
                            sitemap += f'    <loc>{sort_url}</loc>\n'
                            sitemap += f'    <changefreq>{seo_config.get("sitemap_change_frequency", "weekly")}</changefreq>\n'
                            sitemap += f'    <priority>0.8</priority>\n'
                            sitemap += f'  </url>\n'
        except Exception as e:
            logger.error(f"添加分类页面到网站地图出错: {str(e)}")
            logger.exception("详细错误信息:")
        
        # 尝试添加文章页面
        try:
            logger.info(f"尝试从Java后端获取文章列表: {JAVA_BACKEND_URL}/article/listArticle")
            # 构造符合Java后端预期的请求正文
            request_data = {
                "pageSize": 1000, 
                "pageNum": 1,
                "current": 1,
                "size": 1000
            }
            logger.info(f"请求数据: {request_data}")
            
            async with httpx.AsyncClient() as client:
                article_response = await client.post(
                    f"{JAVA_BACKEND_URL}/article/listArticle", 
                    json=request_data,
                    timeout=5
                )
            
            logger.info(f"响应状态码: {article_response.status_code}")
            
            if article_response.status_code == 200:
                response_text = article_response.text[:500] + "..." if len(article_response.text) > 500 else article_response.text
                logger.info(f"响应数据(部分): {response_text}")
                
                article_data = article_response.json().get('data', {})
                records = article_data.get('records', [])
                logger.info(f"成功获取文章列表，共 {len(records)} 篇文章")
                
                for article in records:
                    article_id = article.get('id')
                    update_time = article.get('updateTime', article.get('createTime', time.strftime("%Y-%m-%d")))
                    view_status = article.get('viewStatus')
                    
                    # 只添加公开的文章
                    if article_id and view_status:
                        article_url = f"{site_url}/{seo_config.get('article_url_format', 'article/{id}')}".replace('{id}', str(article_id))
                        if article_url.rstrip('/') in url_set:
                            continue
                        url_set.add(article_url.rstrip('/'))
                        sitemap += f'  <url>\n'
                        sitemap += f'    <loc>{article_url}</loc>\n'
                        sitemap += f'    <lastmod>{update_time}</lastmod>\n'
                        sitemap += f'    <changefreq>{seo_config.get("sitemap_change_frequency", "weekly")}</changefreq>\n'
                        sitemap += f'    <priority>{seo_config.get("sitemap_priority", "0.7")}</priority>\n'
                        sitemap += f'  </url>\n'
            else:
                logger.warning(f"获取文章列表失败，状态码: {article_response.status_code}")
                logger.warning(f"响应内容: {article_response.text}")
                
                # 尝试不同的请求格式
                logger.info("尝试使用alternative请求格式...")
                alternative_data = {"current": 1, "size": 1000}
                async with httpx.AsyncClient() as client:
                    alternative_response = await client.post(
                        f"{JAVA_BACKEND_URL}/article/listArticle", 
                        json=alternative_data,
                        timeout=5
                    )
                logger.info(f"Alternative响应状态码: {alternative_response.status_code}")
                if alternative_response.status_code == 200:
                    alternative_text = alternative_response.text[:500] + "..." if len(alternative_response.text) > 500 else alternative_response.text
                    logger.info(f"Alternative响应数据(部分): {alternative_text}")
                    
                    article_data = alternative_response.json().get('data', {})
                    records = article_data.get('records', [])
                    logger.info(f"使用alternative格式成功获取文章列表，共 {len(records)} 篇文章")
                    
                    for article in records:
                        article_id = article.get('id')
                        update_time = article.get('updateTime', article.get('createTime', time.strftime("%Y-%m-%d")))
                        view_status = article.get('viewStatus')
                        
                        # 只添加公开的文章
                        if article_id and view_status:
                            article_url = f"{site_url}/{seo_config.get('article_url_format', 'article/{id}')}".replace('{id}', str(article_id))
                            if article_url.rstrip('/') in url_set:
                                continue
                            url_set.add(article_url.rstrip('/'))
                            sitemap += f'  <url>\n'
                            sitemap += f'    <loc>{article_url}</loc>\n'
                            sitemap += f'    <lastmod>{update_time}</lastmod>\n'
                            sitemap += f'    <changefreq>{seo_config.get("sitemap_change_frequency", "weekly")}</changefreq>\n'
                            sitemap += f'    <priority>{seo_config.get("sitemap_priority", "0.7")}</priority>\n'
                            sitemap += f'  </url>\n'
        except Exception as e:
            logger.error(f"添加文章页面到网站地图出错: {str(e)}")
            logger.exception("详细错误信息:")
        
        # 关闭sitemap
        sitemap += '</urlset>'
        
        # 保存到文件
        sitemap_path = os.path.join(DATA_DIR, 'sitemap.xml')
        with open(sitemap_path, 'w', encoding='utf-8') as f:
            f.write(sitemap)
            
        logger.info(f"网站地图生成成功，保存到: {sitemap_path}")
        return sitemap
    except Exception as e:
        logger.error(f"生成网站地图出错: {str(e)}")
        return None

# 生成robots.txt
async def generate_robots_txt():
    try:
        seo_config = await get_seo_config()
        # 如果SEO功能关闭，跳过生成
        if not seo_config.get('enable', False):
            logger.info("SEO功能已关闭，跳过生成robots.txt")
            return None
        
        robots_content = seo_config.get('robots_txt', DEFAULT_SEO_CONFIG['robots_txt'])
        
        # 替换占位符
        site_address = seo_config.get('site_address', FRONTEND_URL)
        robots_content = robots_content.replace('{site_address}', site_address)
        
        # 保存到文件
        robots_path = os.path.join(DATA_DIR, 'robots.txt')
        with open(robots_path, 'w', encoding='utf-8') as f:
            f.write(robots_content)
            
        return robots_content
    except Exception as e:
        logger.error(f"生成robots.txt出错: {str(e)}")
        return None

# 百度搜索引擎自动推送函数
async def baidu_push_urls(urls):
    try:
        seo_config = await get_seo_config()
        # 修正字段名：从baidu_push_token改为baidu_token
        baidu_push_token = seo_config.get('baidu_token', '')
        if not baidu_push_token:
            logger.error("百度推送Token未设置")
            return False, "百度推送Token未设置"
        
        # 构建推送请求
        push_url = f"http://data.zz.baidu.com/urls?site={FRONTEND_URL}&token={baidu_push_token}"
        headers = {'Content-Type': 'text/plain'}
        
        # 确保URLs是列表
        if isinstance(urls, str):
            urls = [urls]
        
        # 将URL列表转换为换行符分隔的字符串
        data = '\n'.join(urls)
        
        # 发送推送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(push_url, headers=headers, data=data.encode('utf-8'))
        
        if response.status_code == 200:
            result = response.json()
            logger.info(f"百度推送成功: {result}")
            return True, result
        else:
            logger.error(f"百度推送失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"百度URL推送出错: {str(e)}")
        return False, str(e)

# Google索引API提交函数
async def google_index_api(url):
    try:
        seo_config = await get_seo_config()
        # 使用正确的字段名
        google_api_key = seo_config.get('google_api_key', '')
        if not google_api_key:
            logger.error("Google API密钥未设置")
            return False, "Google API密钥未设置"
        
        # 构建Google索引API请求 (使用Indexing API方法)
        index_url = "https://indexing.googleapis.com/v3/urlNotifications:publish"
        headers = {'Content-Type': 'application/json', 'Authorization': f'Bearer {google_api_key}'}
        
        data = {
            "url": url,
            "type": "URL_UPDATED"  # 或者 URL_DELETED 如果是删除
        }
        
        # 发送索引请求
        async with httpx.AsyncClient() as client:
            response = await client.post(index_url, headers=headers, json=data)
        
        if response.status_code == 200:
            result = response.json()
            logger.info(f"Google索引提交成功: {result}")
            return True, result
        else:
            logger.error(f"Google索引提交失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"Google索引API提交出错: {str(e)}")
        return False, str(e)

# Bing索引API提交函数
async def bing_index_api(url):
    try:
        seo_config = await get_seo_config()
        # 使用正确的字段名
        bing_api_key = seo_config.get('bing_api_key', '')
        if not bing_api_key:
            logger.error("Bing API密钥未设置")
            return False, "Bing API密钥未设置"
        
        # Bing提交URL API
        api_url = "https://ssl.bing.com/webmaster/api.svc/json/SubmitUrl"
        headers = {
            'Content-Type': 'application/json',
            'Ocp-Apim-Subscription-Key': bing_api_key
        }
        
        # 准备数据
        data = {
            "siteUrl": FRONTEND_URL,
            "url": url
        }
        
        # 发送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(api_url, headers=headers, json=data)
        
        if response.status_code == 200:
            result = response.json()
            logger.info(f"Bing索引提交成功: {result}")
            return True, result
        else:
            logger.error(f"Bing索引提交失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"Bing索引API提交出错: {str(e)}")
        return False, str(e)

# Yandex索引API提交函数
async def yandex_index_api(url):
    try:
        seo_config = await get_seo_config()
        # 使用正确的字段名
        yandex_api_key = seo_config.get('yandex_api_key', '')
        if not yandex_api_key:
            logger.error("Yandex API密钥未设置")
            return False, "Yandex API密钥未设置"
        
        # Yandex Webmaster API
        api_url = "https://api.webmaster.yandex.net/v4/user/hosts/recrawl"
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'OAuth {yandex_api_key}'
        }
        
        # 准备数据
        data = {
            "url": url
        }
        
        # 发送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(api_url, headers=headers, json=data)
        
        if response.status_code in [200, 201]:
            result = response.json() if response.text else {'status': 'success'}
            logger.info(f"Yandex索引提交成功: {result}")
            return True, result
        else:
            logger.error(f"Yandex索引提交失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"Yandex索引API提交出错: {str(e)}")
        return False, str(e)

# 搜狗推送函数
async def sogou_push_url(url):
    try:
        seo_config = await get_seo_config()
        # 修正字段名：从sogou_push_token改为sogou_token
        sogou_push_token = seo_config.get('sogou_token', '')
        if not sogou_push_token:
            logger.error("搜狗推送Token未设置")
            return False, "搜狗推送Token未设置"
        
        # 搜狗站长推送API
        api_url = f"https://zhanzhang.sogou.com/api/index/push?token={sogou_push_token}"
        headers = {'Content-Type': 'text/plain'}
        
        # 发送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(api_url, headers=headers, data=url)
        
        if response.status_code == 200:
            result = response.json() if response.text else {'status': 'success'}
            logger.info(f"搜狗推送成功: {result}")
            return True, result
        else:
            logger.error(f"搜狗推送失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"搜狗推送出错: {str(e)}")
        return False, str(e)

# 360搜索推送函数
async def so_push_url(url):
    try:
        seo_config = await get_seo_config()
        so_push_token = seo_config.get('so_token', '')
        if not so_push_token:
            logger.error("360推送Token未设置")
            return False, "360推送Token未设置"
        
        # 360站长推送API
        api_url = f"http://zhanzhang.so.com/push?site={FRONTEND_URL}&token={so_push_token}"
        headers = {'Content-Type': 'text/plain'}
        
        # 发送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(api_url, headers=headers, data=url)
        
        if response.status_code == 200:
            result = response.json() if response.text else {'status': 'success'}
            logger.info(f"360推送成功: {result}")
            return True, result
        else:
            logger.error(f"360推送失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"360推送出错: {str(e)}")
        return False, str(e)

# 神马搜索推送
async def shenma_push_url(url):
    try:
        # 获取SEO配置
        seo_config = await get_seo_config()
        token = seo_config.get('shenma_token', '')
        
        if not token:
            logger.warning("神马搜索推送失败：未配置token")
            return False, "未配置token"
            
        # 神马搜索推送API
        api_url = f"https://data.zhanzhang.sm.cn/push?site={token}&data={url}"
        
        # 发送请求
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Content-Type': 'text/plain'
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.get(api_url, headers=headers, timeout=5)
        
        # 检查响应
        if response.status_code == 200:
            result = response.json()
            if result.get('success'):
                logger.info(f"神马搜索推送成功: {url}")
                return True, "提交成功"
            else:
                error_msg = result.get('message', '未知错误')
                logger.warning(f"神马搜索推送失败: {error_msg}")
                return False, error_msg
        else:
            error_msg = f"请求失败，状态码: {response.status_code}"
            logger.warning(f"神马搜索推送请求失败，{error_msg}")
            return False, error_msg
    except Exception as e:
        error_msg = str(e)
        logger.error(f"神马搜索推送出错: {error_msg}")
        return False, error_msg

# 注册SEO API路由
def register_seo_api(app: FastAPI):
    # 创建异步任务生成网站地图和robots.txt
    asyncio.create_task(generate_sitemap())
    asyncio.create_task(generate_robots_txt())
    
    # SEO配置API
    @app.get('/seo/getSeoConfig')
    @app.options('/seo/getSeoConfig')
    async def get_seo_config_api(request: Request, _: bool = Depends(admin_required)):
        # 处理预检请求
        if request.method == 'OPTIONS':
            logger.info("接收到SEO配置预检请求")
            return JSONResponse({"code": 200, "message": "预检请求成功"})
            
        # 记录请求来源信息
        origin = request.headers.get('Origin', 'Unknown')
        user_agent = request.headers.get('User-Agent', 'Unknown')
        remote_ip = request.client.host if request.client else 'Unknown'
        logger.info(f"收到获取SEO配置请求: IP={remote_ip}, Origin={origin}, UA={user_agent}")
        
        try:
            config = await get_seo_config()
            logger.info(f"成功获取SEO配置，返回配置项数量: {len(config) if config else 0}, 开关状态: {config.get('enable', False)}")
            return JSONResponse({
                "code": 200,
                "message": "获取SEO配置成功",
                "data": config
            })
        except Exception as e:
            logger.error(f"获取SEO配置出错: {str(e)}")
            logger.exception("获取SEO配置详细错误信息:")
            return JSONResponse({
                "code": 500,
                "message": f"获取SEO配置出错: {str(e)}",
                "data": None
            })

    @app.post('/python/seo/updateSeoConfig')
    async def update_seo_config_api(request: Request, _: bool = Depends(admin_required)):
        try:
            config = await request.json()
            remote_ip = request.client.host if request.client else 'Unknown'
            user_agent = request.headers.get('User-Agent', 'Unknown')
            logger.info(f"收到更新SEO配置请求: IP={remote_ip}, UA={user_agent}")
            
            if not config:
                logger.warning("更新SEO配置请求参数为空")
                return JSONResponse({"code": 400, "message": "参数错误", "data": None})
                
            logger.info(f"请求更新的配置项数量: {len(config)}, 包含字段: {', '.join(config.keys())}")
            
            # 记录开关状态变化
            if 'enable' in config:
                logger.info(f"请求中包含开关状态变更: {config['enable']}")
                
            # 检查是否包含网站标题更新
            site_title_updated = False
            new_site_title = None
            if 'site_name' in config and config['site_name']:
                new_site_title = config['site_name']
                site_title_updated = True
                logger.info(f"检测到网站名称更新请求: {new_site_title}")
                
            # 更新配置
            current_config = await get_seo_config()
            old_enable = current_config.get('enable', False)
            current_config.update(config)
            new_enable = current_config.get('enable', False)
            
            if old_enable != new_enable:
                logger.info(f"SEO开关状态将变更: {old_enable} -> {new_enable}")
            
            if save_seo_config(current_config):
                logger.info("SEO配置更新成功，开始更新网站地图和robots.txt")
                
                # 如果网站标题被更新，同步更新Java后端的webInfo
                if site_title_updated and new_site_title:
                    try:
                        logger.info(f"开始同步更新Java后端的网站名称: {new_site_title}")
                        
                        # 首先获取当前的webInfo
                        async with httpx.AsyncClient() as client:
                            web_info_response = await client.get(
                                f"{JAVA_BACKEND_URL}/webInfo/getWebInfo",
                                headers=get_auth_headers(),
                                timeout=10
                            )
                        
                        if web_info_response.status_code == 200:
                            web_info_data = web_info_response.json()
                            if web_info_data and web_info_data.get('data'):
                                # 更新webTitle字段
                                web_info_update = web_info_data['data']
                                web_info_update['webTitle'] = new_site_title
                                
                                # 发送更新请求到Java后端
                                async with httpx.AsyncClient() as client:
                                    update_response = await client.post(
                                        f"{JAVA_BACKEND_URL}/webInfo/updateWebInfo",
                                        json=web_info_update,
                                        headers=get_auth_headers(),
                                        timeout=10
                                    )
                                
                                if update_response.status_code == 200:
                                    logger.info(f"成功同步更新Java后端网站名称: {new_site_title}")
                                else:
                                    logger.error(f"更新Java后端网站名称失败，状态码: {update_response.status_code}, 响应: {update_response.text}")
                            else:
                                logger.error("获取Java后端webInfo数据格式错误")
                        else:
                            logger.error(f"获取Java后端webInfo失败，状态码: {web_info_response.status_code}")
                            
                    except Exception as e:
                        logger.error(f"同步更新Java后端网站名称失败: {str(e)}")
                        # 不影响SEO配置的保存，继续执行其他逻辑
                
                # 更新网站地图和robots.txt
                sitemap_result = await generate_sitemap()
                robots_result = await generate_robots_txt()
                logger.info(f"网站地图生成结果: {'成功' if sitemap_result else '失败'}")
                logger.info(f"robots.txt生成结果: {'成功' if robots_result else '失败'}")
                nginx_url = os.environ.get('NGINX_URL', 'http://localhost/flush_seo_cache')
                try:
                    # 发送清理请求
                    logger.info("正在清理Nginx SEO缓存...")
                    async with httpx.AsyncClient(verify=False) as client:
                        response = await client.post(nginx_url, timeout=5)
                    logger.info(f"Nginx SEO缓存清理结果: {'成功' if response.status_code == 200 else f'失败,非200状态码: {response.status_code}, 响应: {response.text}'}")
                except Exception as e:
                    logger.error(f"清理Nginx SEO缓存失败: {str(e)}")

                return JSONResponse({"code": 200, "message": "更新SEO配置成功", "data": current_config})
            else:
                logger.error("保存SEO配置失败")
                return JSONResponse({"code": 500, "message": "保存SEO配置失败", "data": None})
        except Exception as e:
            logger.error(f"更新SEO配置出错: {str(e)}")
            logger.exception("更新SEO配置详细错误信息:")
            return JSONResponse({"code": 500, "message": f"更新SEO配置出错: {str(e)}", "data": None})
    
    # 专门处理SEO开关状态的API
    @app.post('/python/seo/updateEnableStatus')
    async def update_enable_status_api(request: Request, _: bool = Depends(admin_required)):
        try:
            data = await request.json()
            if data is None:
                logger.warning("请求体为空")
                return JSONResponse({"code": 400, "message": "请求体不能为空", "data": None})
                
            if 'enable' not in data:
                logger.warning("请求中缺少enable参数")
                return JSONResponse({"code": 400, "message": "缺少enable参数", "data": None})
            
            # 确保enable值是布尔类型
            enable_status = bool(data.get('enable'))
            logger.info(f"收到更新SEO开关状态请求: {enable_status}")
            
            # 获取当前配置
            current_config = await get_seo_config()
            old_status = current_config.get('enable', False)
            logger.info(f"当前SEO开关状态: {old_status}, 将更改为: {enable_status}")
            
            # 更新配置
            current_config['enable'] = enable_status
            
            # 如果关闭SEO，清除文件
            if not enable_status:
                clear_seo_files()
            
            if save_seo_config(current_config):
                logger.info(f"SEO开关状态已更新: {old_status} -> {enable_status}")
                
                try:
                    nginx_url = os.environ.get('NGINX_URL', 'http://localhost/flush_seo_cache')
                    # 发送清理请求
                    logger.info("正在清理Nginx SEO缓存...")
                    async with httpx.AsyncClient(verify=False) as client:
                        response = await client.post(nginx_url, timeout=5)
                    logger.info(f"Nginx SEO缓存清理结果: {'成功' if response.status_code == 200 else f'失败,非200状态码: {response.status_code}, 响应: {response.text}'}")
                except Exception as e:
                    logger.error(f"清理Nginx SEO缓存失败: {str(e)}")
                
                return JSONResponse({"code": 200, "message": "SEO开关状态更新成功", "data": {"enable": enable_status}})
            else:
                logger.error("保存SEO开关状态失败")
                return JSONResponse({"code": 500, "message": "保存SEO开关状态失败", "data": None})
        except Exception as e:
            logger.error(f"更新SEO开关状态出错: {str(e)}")
            logger.exception("更新SEO开关状态详细错误信息:")
            return JSONResponse({"code": 500, "message": f"更新SEO开关状态出错: {str(e)}", "data": None})
    
    # 文章META标签API
    @app.get('/python/seo/getArticleMeta')
    async def get_article_meta(request: Request):
        """
        获取文章元数据，用于SEO优化
        支持从URL参数或请求头中检测语言偏好
        """
        try:
            article_id = request.query_params.get('id')
            
            # 获取语言参数，优先级：URL参数 > Accept-Language头
            lang = request.query_params.get('lang')
            
            # 如果URL中没有指定语言，尝试从Accept-Language头判断
            if not lang:
                accept_language = request.headers.get('Accept-Language', '')
                # 简单解析Accept-Language头，例如：en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7
                if accept_language and accept_language.lower().startswith('en'):
                    lang = 'en'
                else:
                    lang = 'zh'  # 默认中文
            
            if not article_id:
                return JSONResponse({"status": "error", "message": "缺少文章ID参数"})
            
            meta_tags = await generate_article_meta_tags(article_id, lang)
            return JSONResponse(meta_tags)
        except Exception as e:
            logger.exception(f"获取文章元数据时发生错误: {e}")
            return JSONResponse({"status": "error", "message": f"获取文章元数据时发生错误: {str(e)}"})
    
    # 网站地图API
    @app.get('/sitemap.xml')
    async def get_sitemap_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            sitemap_path = os.path.join(DATA_DIR, 'sitemap.xml')
            if os.path.exists(sitemap_path):
                with open(sitemap_path, 'r', encoding='utf-8') as f:
                    sitemap = f.read()
                return Response(
                    content=sitemap,
                    media_type='application/xml'
                )
            else:
                # 如果文件不存在，就生成一个
                sitemap = await generate_sitemap()
                if sitemap:
                    return Response(
                        content=sitemap,
                        media_type='application/xml'
                    )
                else:
                    return JSONResponse({"code": 500, "message": "生成网站地图失败", "data": None})
        except Exception as e:
            logger.error(f"获取网站地图出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"获取网站地图出错: {str(e)}", "data": None})
    
    # robots.txt API
    @app.get('/robots.txt')
    async def get_robots_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            robots_path = os.path.join(DATA_DIR, 'robots.txt')
            if os.path.exists(robots_path):
                with open(robots_path, 'r', encoding='utf-8') as f:
                    robots = f.read()
                return PlainTextResponse(content=robots)
            else:
                # 如果文件不存在，就生成一个
                robots = await generate_robots_txt()
                if robots:
                    return PlainTextResponse(content=robots)
                else:
                    return JSONResponse({"code": 500, "message": "生成robots.txt失败", "data": None})
        except Exception as e:
            logger.error(f"获取robots.txt出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"获取robots.txt出错: {str(e)}", "data": None})
    
    # 手动更新SEO数据
    @app.post('/python/seo/updateSeoData')
    async def update_seo_data_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            sitemap = await generate_sitemap()
            robots = await generate_robots_txt()
            
            return JSONResponse({
                "code": 200, 
                "message": "更新SEO数据成功", 
                "data": {
                    "sitemap_generated": sitemap is not None,
                    "robots_generated": robots is not None
                }
            })
        except Exception as e:
            logger.error(f"更新SEO数据出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"更新SEO数据出错: {str(e)}", "data": None})
    
    # 百度推送API
    @app.post('/python/seo/baiduPush')
    async def baidu_push_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            urls = data.get('urls', [])
            
            if not urls:
                return JSONResponse({"code": 400, "message": "URL列表不能为空", "data": None})
                
            success, result = await baidu_push_urls(urls)
            
            if success:
                return JSONResponse({"code": 200, "message": "百度推送成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"百度推送失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"百度推送API出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"百度推送API出错: {str(e)}", "data": None})
    
    # Google索引API
    @app.post('/python/seo/googleIndex')
    async def google_index_api_route(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url', '')
            
            if not url:
                return JSONResponse({"code": 400, "message": "URL不能为空", "data": None})
                
            success, result = await google_index_api(url)
            
            if success:
                return JSONResponse({"code": 200, "message": "Google索引提交成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"Google索引提交失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"Google索引API提交出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"Google索引API提交出错: {str(e)}", "data": None})
    
    # Bing索引API
    @app.post('/python/seo/bingIndex')
    async def bing_index_api_route(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url', '')
            
            if not url:
                return JSONResponse({"code": 400, "message": "URL不能为空", "data": None})
                
            success, result = await bing_index_api(url)
            
            if success:
                return JSONResponse({"code": 200, "message": "Bing索引提交成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"Bing索引提交失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"Bing索引API提交出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"Bing索引API提交出错: {str(e)}", "data": None})
    
    # Yandex索引API
    @app.post('/python/seo/yandexIndex')
    async def yandex_index_api_route(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url', '')
            
            if not url:
                return JSONResponse({"code": 400, "message": "URL不能为空", "data": None})
                
            success, result = await yandex_index_api(url)
            
            if success:
                return JSONResponse({"code": 200, "message": "Yandex索引提交成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"Yandex索引提交失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"Yandex索引API提交出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"Yandex索引API提交出错: {str(e)}", "data": None})
    
    # 搜狗推送API
    @app.post('/python/seo/sogouPush')
    async def sogou_push_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url', '')
            
            if not url:
                return JSONResponse({"code": 400, "message": "URL不能为空", "data": None})
                
            success, result = await sogou_push_url(url)
            
            if success:
                return JSONResponse({"code": 200, "message": "搜狗推送成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"搜狗推送失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"搜狗推送API出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"搜狗推送API出错: {str(e)}", "data": None})
    
    # 360搜索推送API
    @app.post('/python/seo/soPush')
    async def so_push_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url', '')
            
            if not url:
                return JSONResponse({"code": 400, "message": "URL不能为空", "data": None})
                
            success, result = await so_push_url(url)
            
            if success:
                return JSONResponse({"code": 200, "message": "360推送成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"360推送失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"360推送API出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"360推送API出错: {str(e)}", "data": None})
    
    # 神马搜索推送API
    @app.post('/python/seo/shenmaIndex')
    async def shenma_index_api_route(request: Request):
        """神马搜索索引API"""
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url')
            
            if not url:
                return JSONResponse({
                    'code': 400,
                    'message': '请提供要推送的URL'
                })
                
            success, result = await shenma_push_url(url)
            
            return JSONResponse({
                'code': 200 if success else 500,
                'message': '神马搜索推送成功' if success else f'神马搜索推送失败: {result}',
                'data': {
                    'success': success,
                    'result': result
                }
            })
        except Exception as e:
            logger.error(f"神马搜索推送API出错: {str(e)}")
            return JSONResponse({
                'code': 500,
                'message': f'神马搜索推送失败: {str(e)}'
            })
    
    # 文章发布后的SEO处理（自动提交给各搜索引擎）
    @app.post('/python/seo/submitArticle')
    async def submit_article_api(request: Request):
        """提交文章到各搜索引擎"""
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            # 记录请求开始
            logger.info("======================= SEO提交开始 =======================")
            data = await request.json()
            logger.info(f"收到SEO提交请求: {json.dumps(data, ensure_ascii=False)}")
            
            article_id = data.get('articleId')
            article_url = data.get('url')
            article_title = data.get('title', '未指定标题')
            
            logger.info(f"处理文章ID: {article_id}, URL: {article_url}, 标题: {article_title}")
            
            # 至少需要一个参数
            if not article_id and not article_url:
                logger.error("缺少必要参数：既没有提供articleId也没有提供url")
                return JSONResponse({
                    'code': 400, 
                    'message': '请提供articleId或url参数'
                })
            
            # 获取SEO配置
            seo_config = await get_seo_config()
            logger.info(f"已加载SEO配置，站点地址: {seo_config.get('site_address', '未配置')}")
            
            # 检查搜索引擎配置状态
            enabled_engines = []
            if seo_config.get('baidu_push_enabled', False) and seo_config.get('baidu_token'):
                enabled_engines.append('百度')
            if seo_config.get('google_index_enabled', False) and seo_config.get('google_api_key'):
                enabled_engines.append('谷歌')
            if seo_config.get('bing_push_enabled', False) and seo_config.get('bing_api_key'):
                enabled_engines.append('必应')
            if seo_config.get('yandex_push_enabled', False) and seo_config.get('yandex_api_key'):
                enabled_engines.append('Yandex')
            if seo_config.get('yahoo_push_enabled', False) and seo_config.get('yahoo_api_key'):
                enabled_engines.append('Yahoo')
            if seo_config.get('sogou_push_enabled', False) and seo_config.get('sogou_token'):
                enabled_engines.append('搜狗')
            if seo_config.get('so_push_enabled', False) and seo_config.get('so_token'):
                enabled_engines.append('360搜索')
            if seo_config.get('shenma_push_enabled', False) and seo_config.get('shenma_token'):
                enabled_engines.append('神马搜索')
                
            if not enabled_engines:
                logger.warning("没有启用任何搜索引擎推送功能，请检查SEO配置")
            else:
                logger.info(f"已启用的搜索引擎: {', '.join(enabled_engines)}")
            
            # 如果没有提供URL但提供了文章ID，构建URL
            if not article_url and article_id:
                site_url = seo_config.get('site_address', FRONTEND_URL)
                article_format = seo_config.get('article_url_format', 'article/{id}')
                article_url = f"{site_url}/{article_format.replace('{id}', str(article_id))}"
                logger.info(f"根据文章ID构建URL: {article_url}")
            
            # 获取文章内容以便纯文本处理
            article_content = None
            if article_id:
                try:
                    logger.info(f"尝试获取文章内容，文章ID: {article_id}")
                    # 获取文章信息
                    headers = {
                        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                        'Accept': 'application/json',
                        'Content-Type': 'application/json'
                    }
                    
                    api_url = f"{JAVA_BACKEND_URL}/article/getArticleById?id={article_id}"
                    logger.info(f"请求文章API: {api_url}")
                    
                    async with httpx.AsyncClient() as client:
                        response = await client.get(
                            api_url, 
                            headers=headers,
                            timeout=5
                        )
                    
                    logger.info(f"文章API响应状态码: {response.status_code}")
                    
                    if response.status_code == 200:
                        response_json = response.json()
                        logger.info(f"文章API响应code: {response_json.get('code')}")
                        
                        article_data = response_json.get('data', {})
                        if article_data:
                            article_content = article_data.get('articleContent', '')
                            logger.info(f"成功获取文章内容，长度: {len(article_content) if article_content else 0}字符")
                            
                            # 如果未提供标题，使用文章标题
                            if article_title == '未指定标题':
                                article_title = article_data.get('articleTitle', '未指定标题')
                                logger.info(f"使用从API获取的文章标题: {article_title}")
                            
                            # 处理文章内容，移除Markdown标记
                            if article_content:
                                clean_content = get_article_description(article_content, max_length=5000)  # 使用较大的长度限制
                                logger.info(f"已处理文章内容，移除Markdown标记，处理后长度: {len(clean_content)}字符")
                                article_content = clean_content
                        else:
                            logger.warning(f"文章API返回了空数据，响应: {json.dumps(response_json, ensure_ascii=False)}")
                    else:
                        logger.error(f"获取文章失败，状态码: {response.status_code}, 响应: {response.text[:200]}...")
                except Exception as e:
                    logger.error(f"获取文章内容时发生异常: {str(e)}")
                    logger.exception("详细错误栈:")
                    logger.warning(f"获取文章内容出错，将继续推送URL: {article_url}")
            
            # 验证URL格式
            if not article_url.startswith(('http://', 'https://')):
                logger.error(f"提交的URL格式不正确: {article_url}")
                return JSONResponse({
                    'code': 400,
                    'message': 'URL格式不正确，必须以http://或https://开头'
                })
                
            # 记录推送开始
            logger.info(f"开始向搜索引擎推送文章，URL: {article_url}")
            if article_content:
                logger.info(f"文章内容示例: {article_content[:100]}...")
            
            push_results = {}
            
            # 百度推送
            if seo_config.get('baidu_push_enabled', False) and seo_config.get('baidu_token'):
                try:
                    logger.info(f"开始百度推送，token长度: {len(seo_config.get('baidu_token', ''))}")
                    baidu_success, baidu_result = await baidu_push_urls([article_url])
                    logger.info(f"百度推送结果: {json.dumps(baidu_result, ensure_ascii=False)}")
                    push_results['baidu'] = {
                        'success': baidu_success,
                        'result': baidu_result
                    }
                except Exception as e:
                    error_msg = f"百度推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("百度推送详细错误:")
                    push_results['baidu'] = {"success": False, "message": error_msg}
            else:
                logger.info("百度推送未启用或token未配置，已跳过")
            
            # Google索引
            if seo_config.get('google_index_enabled', False) and seo_config.get('google_api_key'):
                try:
                    logger.info(f"开始Google索引推送，API密钥长度: {len(seo_config.get('google_api_key', ''))}")
                    google_success, google_result = await google_index_api(article_url)
                    logger.info(f"谷歌推送结果: {json.dumps(google_result, ensure_ascii=False)}")
                    push_results['google'] = {
                        'success': google_success,
                        'result': google_result
                    }
                except Exception as e:
                    error_msg = f"谷歌推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("谷歌推送详细错误:")
                    push_results['google'] = {"success": False, "message": error_msg}
            else:
                logger.info("谷歌推送未启用或API密钥未配置，已跳过")
            
            # Bing推送
            if seo_config.get('bing_push_enabled', False) and seo_config.get('bing_api_key'):
                try:
                    logger.info(f"开始Bing索引推送，API密钥长度: {len(seo_config.get('bing_api_key', ''))}")
                    bing_success, bing_result = await bing_index_api(article_url)
                    logger.info(f"必应推送结果: {json.dumps(bing_result, ensure_ascii=False)}")
                    push_results['bing'] = {
                        'success': bing_success,
                        'result': bing_result
                    }
                except Exception as e:
                    error_msg = f"必应推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("必应推送详细错误:")
                    push_results['bing'] = {"success": False, "message": error_msg}
            else:
                logger.info("必应推送未启用或API密钥未配置，已跳过")
                    
            # Yandex推送
            if seo_config.get('yandex_push_enabled', False) and seo_config.get('yandex_api_key'):
                try:
                    logger.info(f"开始Yandex索引推送，API密钥长度: {len(seo_config.get('yandex_api_key', ''))}")
                    yandex_success, yandex_result = await yandex_index_api(article_url)
                    logger.info(f"Yandex推送结果: {json.dumps(yandex_result, ensure_ascii=False)}")
                    push_results['yandex'] = {
                        'success': yandex_success,
                        'result': yandex_result
                    }
                except Exception as e:
                    error_msg = f"Yandex推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("Yandex推送详细错误:")
                    push_results['yandex'] = {"success": False, "message": error_msg}
            else:
                logger.info("Yandex推送未启用或API密钥未配置，已跳过")
                    
            # Yahoo推送
            if seo_config.get('yahoo_push_enabled', False) and seo_config.get('yahoo_api_key'):
                try:
                    logger.info(f"开始Yahoo推送，API密钥长度: {len(seo_config.get('yahoo_api_key', ''))}")
                    yahoo_success, yahoo_result = await yahoo_push_url(article_url)
                    logger.info(f"Yahoo推送结果: {json.dumps(yahoo_result, ensure_ascii=False)}")
                    push_results['yahoo'] = {
                        'success': yahoo_success,
                        'result': yahoo_result
                    }
                except Exception as e:
                    error_msg = f"Yahoo推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("Yahoo推送详细错误:")
                    push_results['yahoo'] = {"success": False, "message": error_msg}
            else:
                logger.info("Yahoo推送未启用或API密钥未配置，已跳过")
                    
            # 搜狗推送
            if seo_config.get('sogou_push_enabled', False) and seo_config.get('sogou_token'):
                try:
                    logger.info(f"开始搜狗推送，token长度: {len(seo_config.get('sogou_token', ''))}")
                    sogou_success, sogou_result = await sogou_push_url(article_url)
                    logger.info(f"搜狗推送结果: {json.dumps(sogou_result, ensure_ascii=False)}")
                    push_results['sogou'] = {
                        'success': sogou_success,
                        'result': sogou_result
                    }
                except Exception as e:
                    error_msg = f"搜狗推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("搜狗推送详细错误:")
                    push_results['sogou'] = {"success": False, "message": error_msg}
            else:
                logger.info("搜狗推送未启用或token未配置，已跳过")
                    
            # 360搜索推送
            if seo_config.get('so_push_enabled', False) and seo_config.get('so_token'):
                try:
                    logger.info(f"开始360搜索推送，token长度: {len(seo_config.get('so_token', ''))}")
                    so_success, so_result = await so_push_url(article_url)
                    logger.info(f"360推送结果: {json.dumps(so_result, ensure_ascii=False)}")
                    push_results['so'] = {
                        'success': so_success,
                        'result': so_result
                    }
                except Exception as e:
                    error_msg = f"360推送出错: {str(e)}"
                    logger.error(error_msg)
                    logger.exception("360推送详细错误:")
                    push_results['so'] = {"success": False, "message": error_msg}
            else:
                logger.info("360推送未启用或token未配置，已跳过")
                    
            # 神马搜索推送
            if seo_config.get('shenma_push_enabled', False) and seo_config.get('shenma_token'):
                try:
                    logger.info(f"开始神马搜索推送，token长度: {len(seo_config.get('shenma_token', ''))}")
                    shenma_success, shenma_result = await shenma_push_url(article_url)
                    logger.info(f"神马搜索推送结果: {json.dumps(shenma_result, ensure_ascii=False)}")
                    push_results['shenma'] = {
                        'success': shenma_success,
                        'result': shenma_result
                    }
                except Exception as e:
                    error_msg = f"神马搜索推送出错: {str(e)}"
                    logger.error(error_msg) 
                    logger.exception("神马搜索推送详细错误:")
                    push_results['shenma'] = {"success": False, "message": error_msg}
            else:
                logger.info("神马搜索推送未启用或token未配置，已跳过")
            
            # 推送完成后，异步刷新站点地图
            try:
                asyncio.create_task(add_or_update_sitemap_url(article_url, time.strftime('%Y-%m-%d')))
            except Exception as _e:
                logger.error(f"异步刷新网站地图失败: {_e}")

            # 判断整体是否成功 - 只要有一个成功就算成功
            is_success = False
            if push_results:
                is_success = any(result.get('success', False) for result in push_results.values())
                logger.info(f"整体推送结果: {'成功' if is_success else '失败'}")
            else:
                logger.warning("没有任何搜索引擎被推送，请检查SEO配置")
            
            # 检查是否需要发送邮件通知
            enable_push_notification = seo_config.get('enable_push_notification', False)
            notify_only_on_failure = seo_config.get('notify_only_on_failure', False)
            
            # 判断是否需要发送通知
            should_notify = (
                enable_push_notification and 
                (not notify_only_on_failure or (notify_only_on_failure and not is_success))
            )
            
            # 如果需要发送通知，将推送结果发送给Java后端
            if should_notify:
                try:
                    # 准备发送给Java后端的数据
                    notification_data = {
                        'articleId': article_id,
                        'title': article_title,
                        'url': article_url, 
                        'results': push_results,
                        'success': is_success,
                        'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                        'notificationEmail': seo_config.get('notification_email', '')  # 可选的指定通知邮箱
                    }
                    
                    # 调用Java后端API
                    java_api_url = f"{JAVA_BACKEND_URL}/article/notifySeoResult"
                    headers = {
                        'Content-Type': 'application/json',
                        'User-Agent': 'Python-SEO-Module/1.0'
                    }
                    
                    logger.info(f"准备发送SEO结果通知到Java后端: {java_api_url}")
                    
                    # 异步发送通知，不阻塞当前请求
                    def send_notification():
                        try:
                            with httpx.Client() as client:
                                notification_response = client.post(
                                    java_api_url, 
                                    headers=headers,
                                    json=notification_data,
                                    timeout=5
                                )
                            logger.info(f"通知发送结果: 状态码={notification_response.status_code}, 响应={notification_response.text[:100]}...")
                        except Exception as e:
                            logger.error(f"发送通知请求时出错: {str(e)}")
                    
                    threading.Thread(target=send_notification).start()
                    logger.info("SEO推送结果通知已发送至Java后端")
                except Exception as e:
                    logger.error(f"准备SEO推送结果通知时出错: {str(e)}")
                    logger.exception("通知错误详情:")
                    # 继续处理，不影响主流程
            else:
                logger.info("SEO推送通知功能未启用或不满足通知条件，不发送通知")
            
            # 记录返回结果
            response_data = {
                'code': 200 if is_success else 500,
                'message': '文章提交成功' if is_success else '文章提交失败',
                'data': {
                    'url': article_url,
                    'results': push_results
                }
            }
            logger.info(f"SEO提交响应: {json.dumps(response_data, ensure_ascii=False)}")
            logger.info("======================= SEO提交完成 =======================")
            
            return JSONResponse(response_data)
        except Exception as e:
            error_msg = f"提交文章到搜索引擎出错: {str(e)}"
            logger.error(error_msg)
            logger.exception("SEO提交过程中发生异常，详细错误信息:")
            
            response_data = {
                'code': 500,
                'message': f'提交文章失败: {str(e)}',
                'data': {'url': data.get('url') or f"article/{data.get('articleId')}" if 'data' in locals() else "未知文章"}
            }
            logger.info(f"SEO提交错误响应: {json.dumps(response_data, ensure_ascii=False)}")
            logger.info("======================= SEO提交异常结束 =======================")
            
            return JSONResponse(response_data)
    
    # AI SEO分析API
    @app.get('/python/seo/aiAnalyzeSite')
    async def ai_analyze_site_api(request: Request):
        """使用AI分析网站SEO情况"""
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            # 获取SEO配置
            seo_config = await get_seo_config()
            
            # 准备网站信息数据
            site_info = {
                "site_name": seo_config.get('site_name', ''),
                "site_description": seo_config.get('site_description', ''),
                "site_keywords": seo_config.get('site_keywords', ''),
                "baidu_push_enabled": seo_config.get('baidu_push_enabled', False),
                "google_index_enabled": seo_config.get('google_index_enabled', False),
                "bing_push_enabled": seo_config.get('bing_push_enabled', False),
                "yandex_push_enabled": seo_config.get('yandex_push_enabled', False),
                "yahoo_push_enabled": seo_config.get('yahoo_push_enabled', False),
                "sogou_push_enabled": seo_config.get('sogou_push_enabled', False),
                "so_push_enabled": seo_config.get('so_push_enabled', False),
                "shenma_push_enabled": seo_config.get('shenma_push_enabled', False),
                "auto_generate_meta_tags": seo_config.get('auto_generate_meta_tags', True),
                "generate_sitemap": seo_config.get('generate_sitemap', True),
                "custom_head_code": seo_config.get('custom_head_code', ''),
                # 添加更多配置
                "article_url_format": seo_config.get('article_url_format', 'article/{id}'),
                "category_url_format": seo_config.get('category_url_format', 'category/{id}'),
                "tag_url_format": seo_config.get('tag_url_format', 'tag/{id}'),
                "sitemap_change_frequency": seo_config.get('sitemap_change_frequency', 'weekly'),
                "sitemap_priority": seo_config.get('sitemap_priority', '0.7'),
                "twitter_card": seo_config.get('twitter_card', 'summary_large_image'),
                "twitter_site": seo_config.get('twitter_site', ''),
                "twitter_creator": seo_config.get('twitter_creator', ''),
                "og_type": seo_config.get('og_type', 'article'),
                "site_logo": seo_config.get('site_logo', ''),
                "fb_app_id": seo_config.get('fb_app_id', ''),
                "fb_page_url": seo_config.get('fb_page_url', ''),
                "linkedin_company_id": seo_config.get('linkedin_company_id', ''),
                "linkedin_mode": seo_config.get('linkedin_mode', 'standard'),
                "pinterest_verification": seo_config.get('pinterest_verification', ''),
                "pinterest_description": seo_config.get('pinterest_description', ''),
                "wechat_miniprogram_path": seo_config.get('wechat_miniprogram_path', ''),
                "wechat_miniprogram_id": seo_config.get('wechat_miniprogram_id', ''),
                "qq_miniprogram_path": seo_config.get('qq_miniprogram_path', ''),
                "og_image": seo_config.get('og_image', ''),
                "default_author": seo_config.get('default_author', 'Admin'),
                "baidu_site_verification": seo_config.get('baidu_site_verification', ''),
                "google_site_verification": seo_config.get('google_site_verification', ''),
                "bing_site_verification": seo_config.get('bing_site_verification', '')
            }
            
            # 获取API配置
            api_config, _ = get_ai_api_config()
            
            if not api_config or not api_config.get('api_key'):
                return JSONResponse({
                    "code": 401, 
                    "message": "AI API未配置，请先配置API", 
                    "data": None
                })
            
            # 如果用户选择包含文章内容，获取最近的文章
            if api_config.get('include_articles', False):
                site_info['recent_articles'] = get_recent_articles(5)
            else:
                site_info['recent_articles'] = []
            
            # 根据提供商调用不同的分析函数
            provider = api_config.get('provider', 'openai')
            
            try:
                # 统一调用新的通用AI分析函数
                result = await analyze_with_ai(site_info, api_config)
                
                # 处理API返回的结果
                return JSONResponse({
                    "code": 200, 
                    "message": "AI SEO分析完成", 
                    "data": {
                        "analysis": result.get('analysis', ''),
                        "suggestions": result.get('suggestions', []),
                        "seo_score": result.get('seo_score', 70),
                        "stats": {
                            "errors": len([s for s in result.get('suggestions', []) if s.get('type') == 'error']),
                            "warnings": len([s for s in result.get('suggestions', []) if s.get('type') == 'warning']),
                            "infos": len([s for s in result.get('suggestions', []) if s.get('type') == 'info'])
                        }
                    }
                })
            except ImportError as e:
                # 依赖项缺失
                logger.error(f"缺少必要的依赖项: {str(e)}")
                return JSONResponse({"code": 500, "message": f"缺少必要的依赖项: {str(e)}", "data": None})
            except Exception as e:
                # AI API调用失败
                logger.error(f"AI分析出错: {str(e)}")
                return JSONResponse({"code": 500, "message": f"AI分析出错: {str(e)}", "data": None})
        except Exception as e:
            logger.error(f"AI SEO分析出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"AI SEO分析出错: {str(e)}", "data": None})

    # 检查AI API配置
    @app.get('/python/seo/checkAiApiConfig')
    async def check_ai_api_config(request: Request, _: bool = Depends(admin_required)):
        try:
            decrypted_config, display_config = get_ai_api_config()
            
            if decrypted_config and display_config:
                # 检查必要字段是否存在
                provider = decrypted_config.get('provider')
                api_key = decrypted_config.get('api_key')
                model = decrypted_config.get('model')
                
                configured = bool(provider and api_key and model)
                
                # 对于自定义AI服务，还需要检查API URL
                if provider == 'custom':
                    custom_api_url = decrypted_config.get('custom_api_url')
                    configured = configured and bool(custom_api_url)
                
                return JSONResponse({
                    "code": 200, 
                    "message": "获取AI API配置成功", 
                    "data": {
                        "configured": configured,
                        "provider": provider,
                        "model": model,
                        "updated_at": display_config.get('updated_at')
                    }
                })
            else:
                return JSONResponse({
                    "code": 200, 
                    "message": "AI API未配置", 
                    "data": {"configured": False}
                })
        except Exception as e:
            logger.error(f"检查AI API配置出错: {str(e)}")
            return JSONResponse({
                "code": 500, 
                "message": f"检查AI API配置出错: {str(e)}", 
                "data": {"configured": False}
            })

    # 更新AI API配置
    @app.post('/python/seo/updateAiApiConfig')
    async def update_ai_api_config(request: Request, _: bool = Depends(admin_required)):
        try:
            config = await request.json()
            if not config:
                return JSONResponse({"code": 400, "message": "参数错误", "data": None})
                
            # 验证必填字段
            required_fields = ['provider', 'api_key', 'model']
            for field in required_fields:
                if field not in config:
                    return JSONResponse({"code": 400, "message": f"缺少必填字段: {field}", "data": None})
            
            # 保存配置
            if save_ai_api_config(config):
                # 获取不含敏感信息的配置用于返回
                _, config_for_display = get_ai_api_config()
                return JSONResponse({
                    "code": 200, 
                    "message": "更新AI API配置成功", 
                    "data": config_for_display
                })
            else:
                return JSONResponse({"code": 500, "message": "保存AI API配置失败", "data": None})
        except Exception as e:
            logger.error(f"更新AI API配置出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"更新AI API配置出错: {str(e)}", "data": None})
            
    # 站点优化建议API
    @app.get('/python/seo/analyzeSite')
    async def analyze_site_api(request: Request):
        """分析网站SEO配置并提供改进建议"""
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            seo_config = await get_seo_config()
            suggestions = []
                
            # 检查基本SEO配置
            if not seo_config.get('site_name'):
                suggestions.append({
                    "type": "error",
                    "message": "网站名称未设置，这是必须的SEO信息"
                })
                
            if not seo_config.get('site_description') or len(seo_config.get('site_description', '')) < 50:
                suggestions.append({
                    "type": "warning",
                    "message": "网站描述过短或未设置，建议使用50-160个字符的描述"
                })
                
            if not seo_config.get('site_keywords'):
                suggestions.append({
                    "type": "warning",
                    "message": "网站关键词未设置，这对SEO有一定影响"
                })
                
            if not seo_config.get('baidu_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "百度推送功能未启用，建议启用以提高百度搜索引擎收录速度"
                })
                    
            if not seo_config.get('google_index_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "Google索引功能未启用，建议启用以提高Google搜索引擎收录速度"
                })
                    
            if not seo_config.get('bing_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "Bing推送功能未启用，建议启用以提高Bing搜索收录速度"
                })
                    
            if not seo_config.get('yandex_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "Yandex推送功能未启用，建议启用以提高在俄罗斯地区的搜索可见度"
                })
                    
            if not seo_config.get('sogou_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "搜狗推送功能未启用，建议启用以提高在中国的搜索可见度"
                })
                    
            if not seo_config.get('so_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "360搜索推送功能未启用，建议启用以提高在中国的搜索可见度"
                })
                
            if not seo_config.get('shenma_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "神马搜索推送功能未启用，建议启用以提高在移动端的搜索可见度"
                })
                
            if not seo_config.get('yahoo_push_enabled', False):
                suggestions.append({
                    "type": "warning",
                    "message": "Yahoo推送功能未启用，建议启用以提高在国际的搜索可见度"
                })
                
            # 检查网站验证
            if not seo_config.get('baidu_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "百度站点验证未设置，这会影响百度搜索引擎对网站的信任度"
                })
                
            if not seo_config.get('google_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "Google站点验证未设置，这会影响对Google Search Console的访问"
                })
                
            if not seo_config.get('bing_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "Bing站点验证未设置，这会影响对Bing Webmaster Tools的访问"
                })
                
            if not seo_config.get('yandex_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "Yandex站点验证未设置，这会影响在俄罗斯地区的搜索可见度"
                })
                
            if not seo_config.get('sogou_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "搜狗站点验证未设置，这会影响在中国的搜索可见度"
                })
                
            if not seo_config.get('so_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "360搜索站点验证未设置，这会影响在中国的搜索可见度"
                })
                
            if not seo_config.get('shenma_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "神马搜索站点验证未设置，这会影响在移动端的搜索可见度"
                })
                
            if not seo_config.get('yahoo_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "Yahoo（雅虎）站点验证未设置，这会影响在国际的搜索可见度"
                })
                
            if not seo_config.get('duckduckgo_site_verification'):
                suggestions.append({
                    "type": "info",
                    "message": "DuckDuckGo站点验证未设置，这会影响在注重隐私保护的用户中的搜索可见度"
                })
                
            # 返回分析结果
            return JSONResponse({
                "code": 200, 
                "message": "站点SEO分析完成", 
                "data": {
                    "suggestions": suggestions,
                    "seo_score": 100 - min(len(suggestions) * 5, 90)  # 简单计算SEO得分，保证至少10分
                }
            })        
        except Exception as e:
            logger.error(f"站点SEO分析出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"站点SEO分析出错: {str(e)}", "data": None})

    # 获取AI API配置
    @app.get('/python/seo/getAiApiConfig')
    async def get_ai_api_config_route(request: Request, _: bool = Depends(admin_required)):
        try:
            decrypted_config, display_config = get_ai_api_config()
            
            if display_config:
                return JSONResponse({
                    "code": 200, 
                    "message": "获取AI API配置成功", 
                    "data": display_config
                })
            else:
                # 返回默认配置结构
                default_config = {
                    "provider": "openai",
                    "api_key": "",
                    "api_base": "",
                    "model": "gpt-3.5-turbo",
                    "include_articles": False,
                    "custom_api_url": "",
                    "request_format": "openai",
                    "custom_headers_list": [],
                    "custom_payload_json": "",
                    "response_path_str": "",
                    "configured": False
                }
                return JSONResponse({
                    "code": 200, 
                    "message": "AI API未配置，返回默认配置", 
                    "data": default_config
                })
        except Exception as e:
            logger.error(f"获取AI API配置出错: {str(e)}")
            return JSONResponse({
                "code": 500, 
                "message": f"获取AI API配置出错: {str(e)}", 
                "data": None
            })

    # 保存AI API配置
    @app.post('/python/seo/saveAiApiConfig')
    async def save_ai_api_config_route(request: Request, _: bool = Depends(admin_required)):
        try:
            config = await request.json()
            if not config:
                return JSONResponse({"code": 400, "message": "参数错误", "data": None})
                
            # 验证必填字段
            required_fields = ['provider', 'api_key', 'model']
            for field in required_fields:
                if field not in config:
                    return JSONResponse({"code": 400, "message": f"缺少必填字段: {field}", "data": None})
            
            # 保存配置
            if save_ai_api_config(config):
                # 获取不含敏感信息的配置用于返回
                _, config_for_display = get_ai_api_config()
                return JSONResponse({
                    "code": 200, 
                    "message": "更新AI API配置成功", 
                    "data": config_for_display
                })
            else:
                return JSONResponse({"code": 500, "message": "保存AI API配置失败", "data": None})
        except Exception as e:
            logger.error(f"保存AI API配置出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"保存AI API配置出错: {str(e)}", "data": None})

    # Yahoo索引API
    @app.post('/python/seo/yahooPush')
    async def yahoo_push_api(request: Request):
        # 检查SEO是否启用
        config = await get_seo_config()
        if not config.get('enable', False):
            return JSONResponse({"code": 403, "message": "SEO功能未启用"}, status_code=403)
            
        try:
            data = await request.json()
            url = data.get('url', '')
            
            if not url:
                return JSONResponse({"code": 400, "message": "URL不能为空", "data": None})
                
            success, result = await yahoo_push_url(url)
            
            if success:
                return JSONResponse({"code": 200, "message": "Yahoo推送成功", "data": result})
            else:
                return JSONResponse({"code": 500, "message": f"Yahoo推送失败: {result}", "data": None})
        except Exception as e:
            logger.error(f"Yahoo推送API出错: {str(e)}")
            return JSONResponse({"code": 500, "message": f"Yahoo推送API出错: {str(e)}", "data": None})

    @app.get('/seo/getSiteMeta')
    async def get_site_meta(request: Request):
        """
        获取整个站点的SEO元数据
        :return: JSON格式的站点元数据
        """
        try:
            if not is_service_ready():
                return JSONResponse(handle_service_not_ready("获取站点元数据"))
                
            seo_config = await get_seo_config()
            
            # 如果SEO功能关闭，返回简单的元数据
            if not seo_config.get('enable', False):
                logger.info("SEO功能已关闭，返回基本站点元数据")
                return JSONResponse({
                    "status": "success",
                    "data": generate_site_meta_tags(),
                    "message": "返回基本站点元数据"
                })
                
            # 获取站点元数据
            try:
                meta_tags = generate_site_meta_tags()
                
                return JSONResponse({
                    "status": "success",
                    "data": meta_tags,
                    "message": "获取站点元数据成功"
                })
            except Exception as e:
                logger.error(f"生成站点元数据失败: {str(e)}")
                return JSONResponse({
                    "status": "error",
                    "message": f"生成站点元数据失败: {str(e)}"
                })
                
        except Exception as e:
            logger.error(f"获取站点元数据异常: {str(e)}")
            return JSONResponse({
                "status": "error",
                "message": f"获取站点元数据异常: {str(e)}"
            })
            
    # IM聊天室元数据获取API
    @app.get('/seo/getIMSiteMeta')
    async def get_im_site_meta(request: Request):
        """
        获取IM聊天室页面的SEO元数据
        :return: JSON格式的IM聊天室元数据
        """
        try:
            if not is_service_ready():
                return JSONResponse(handle_service_not_ready("获取IM聊天室元数据"))
                
            seo_config = await get_seo_config()
            
            # 如果SEO功能关闭，返回简单的元数据
            if not seo_config.get('enable', False):
                logger.info("SEO功能已关闭，返回基本IM聊天室元数据")
                return JSONResponse({
                    "status": "success",
                    "data": generate_im_site_meta_tags(),
                    "message": "返回基本IM聊天室元数据"
                })
                
            # 获取IM聊天室元数据
            try:
                meta_tags = generate_im_site_meta_tags()
                
                return JSONResponse({
                    "status": "success",
                    "data": meta_tags,
                    "message": "获取IM聊天室元数据成功"
                })
            except Exception as e:
                logger.error(f"生成IM聊天室元数据失败: {str(e)}")
                return JSONResponse({
                    "status": "error",
                    "message": f"生成IM聊天室元数据失败: {str(e)}"
                })
                
        except Exception as e:
            logger.error(f"获取IM聊天室元数据异常: {str(e)}")
            return JSONResponse({
                "status": "error",
                "message": f"获取IM聊天室元数据异常: {str(e)}"
            })

    # 新增分类页元数据获取API
    @app.get('/python/seo/getCategoryMeta')
    async def get_category_meta(request: Request):
        """
        获取分类页面的SEO元数据
        :param id: 分类ID
        :return: JSON格式的分类页面元数据
        """
        try:
            if not is_service_ready():
                return JSONResponse(handle_service_not_ready("获取分类页元数据"))
                
            category_id = request.query_params.get('id')
            if not category_id:
                return JSONResponse({
                    "status": "error",
                    "message": "缺少必要参数: id"
                })
                
            # 限制查询频率，防止滥用
            # time.sleep(0.1)
                
            seo_config = await get_seo_config()
            
            # 如果SEO功能关闭，返回简单的元数据
            if not seo_config.get('enable', False):
                logger.info("SEO功能已关闭，返回基本分类页元数据")
                return JSONResponse({
                    "status": "success",
                    "data": await generate_category_meta_tags(category_id),
                    "message": "返回基本分类页元数据"
                })
                
            # 获取分类页元数据
            try:
                meta_tags = await generate_category_meta_tags(category_id)
                
                return JSONResponse({
                    "status": "success",
                    "data": meta_tags,
                    "message": "获取分类页元数据成功"
                })
            except Exception as e:
                logger.error(f"生成分类页元数据失败: {str(e)}")
                return JSONResponse({
                    "status": "error",
                    "message": f"生成分类页元数据失败: {str(e)}"
                })
                
        except Exception as e:
            logger.error(f"获取分类页元数据异常: {str(e)}")
            return JSONResponse({
                "status": "error",
                "message": f"获取分类页元数据异常: {str(e)}"
            })

    @app.post('/seo/removeArticleFromSitemap')
    async def remove_article_from_sitemap(request: Request, _: bool = Depends(admin_required)):
        """接收 {"url": "http://site/article/123"} 或 {"id":123} 移除 sitemap 条目"""
        try:
            data = await request.json()
            if not data:
                return JSONResponse({"code":400,"message":"参数为空"})
            seo_config = await get_seo_config()
            site_url = seo_config.get('site_address', FRONTEND_URL)
            if 'url' in data and data['url']:
                target_url = data['url']
            elif 'id' in data:
                target_url = f"{site_url}/{seo_config.get('article_url_format','article/{id}').replace('{id}', str(data['id']))}"
            else:
                return JSONResponse({"code":400,"message":"缺少 url 或 id"})

            asyncio.create_task(remove_sitemap_url(target_url))
            return JSONResponse({"code":200,"message":"已提交删除任务","data":target_url})
        except Exception as e:
            logger.error(f"删除 sitemap URL API 失败: {e}")
            return JSONResponse({"code":500,"message":str(e)})

# 获取AI API配置
def get_ai_api_config():
    """获取AI API配置，返回加密和解密版本"""
    try:
        if not os.path.exists(AI_API_CONFIG_FILE):
            logger.warning("AI API配置文件不存在")
            return None, None
            
        with open(AI_API_CONFIG_FILE, 'r', encoding='utf-8') as f:
            encrypted_config = json.load(f)
        
        # 解密配置用于内部使用
        decrypted_config = encrypted_config.copy()
        if 'api_key' in encrypted_config and encrypted_config['api_key']:
            decrypted_api_key = decrypt_data(encrypted_config['api_key'])
            if decrypted_api_key:
                decrypted_config['api_key'] = decrypted_api_key
            else:
                logger.error("解密API密钥失败")
                return None, None
        
        # 创建用于前端显示的配置（不包含敏感信息）
        display_config = encrypted_config.copy()
        
        # 隐藏敏感信息
        if 'api_key' in display_config:
            # 只显示API密钥的前4位和后4位
            api_key = decrypted_config.get('api_key', '')
            if len(api_key) > 8:
                display_config['api_key'] = f"{api_key[:4]}{'*' * (len(api_key) - 8)}{api_key[-4:]}"
            else:
                display_config['api_key'] = '*' * len(api_key)
        
        # 处理自定义AI服务的配置回显
        if encrypted_config.get('provider') == 'custom':
            # 将custom_headers转换回custom_headers_list格式
            if 'custom_headers' in encrypted_config:
                custom_headers_list = []
                for key, value in encrypted_config.get('custom_headers', {}).items():
                    custom_headers_list.append({'key': key, 'value': value})
                display_config['custom_headers_list'] = custom_headers_list
                decrypted_config['custom_headers_list'] = custom_headers_list
            
            # 将custom_payload转换回JSON字符串格式
            if 'custom_payload' in encrypted_config:
                display_config['custom_payload_json'] = json.dumps(
                    encrypted_config['custom_payload'], 
                    ensure_ascii=False, 
                    indent=2
                )
                decrypted_config['custom_payload_json'] = display_config['custom_payload_json']
            
            # 将response_path转换回字符串格式
            if 'response_path' in encrypted_config:
                display_config['response_path_str'] = '.'.join(encrypted_config['response_path'])
                decrypted_config['response_path_str'] = display_config['response_path_str']
        
        logger.info(f"成功获取AI API配置，提供商: {encrypted_config.get('provider', 'unknown')}")
        return decrypted_config, display_config
        
    except Exception as e:
        logger.error(f"获取AI API配置出错: {str(e)}")
        logger.exception("获取AI API配置详细错误信息:")
        return None, None

# 保存AI API配置
def save_ai_api_config(config):
    try:
        # 处理并验证配置
        processed_config = config.copy()
        
        # 加密API密钥
        if 'api_key' in config and config['api_key']:
            processed_config['api_key'] = encrypt_data(config['api_key'])
        else:
            logger.error("API密钥不能为空")
            return False
        
        # 处理自定义AI服务的特殊配置
        if config.get('provider') == 'custom':
            # 验证自定义API的必填字段
            if not config.get('custom_api_url'):
                logger.error("自定义AI服务需要提供API端点URL")
                return False
            
            # 处理自定义请求头列表转换为字典
            if 'custom_headers_list' in config:
                custom_headers = {}
                for header in config['custom_headers_list']:
                    if header.get('key') and header.get('value'):
                        custom_headers[header['key']] = header['value']
                processed_config['custom_headers'] = custom_headers
                # 保留原始列表格式以便前端回显
            
            # 处理自定义载荷JSON
            if 'custom_payload_json' in config and config['custom_payload_json']:
                try:
                    processed_config['custom_payload'] = json.loads(config['custom_payload_json'])
                except json.JSONDecodeError as e:
                    logger.error(f"自定义载荷JSON格式错误: {str(e)}")
                    return False
            
            # 处理响应解析路径
            if 'response_path_str' in config and config['response_path_str']:
                processed_config['response_path'] = config['response_path_str'].split('.')
        
        # 添加配置更新时间戳
        processed_config['updated_at'] = datetime.now().isoformat()
        
        # 保存到文件
        with open(AI_API_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(processed_config, f, ensure_ascii=False, indent=2)
        
        logger.info(f"AI API配置保存成功，提供商: {config.get('provider', 'unknown')}")
        return True
        
    except Exception as e:
        logger.error(f"保存AI API配置出错: {str(e)}")
        logger.exception("保存AI API配置详细错误信息:")
        return False

# AI分析 - 通用函数
async def analyze_with_ai(site_info, api_config):
    """
    通用AI分析函数，支持多种AI服务商
    """
    try:
        provider = api_config.get('provider', 'openai')
        api_key = api_config.get('api_key')
        model = api_config.get('model', 'gpt-4o')
        api_base = api_config.get('api_base', '')
        
        # 构建简化的提示词
        prompt = build_seo_analysis_prompt(site_info)
        
        # 根据不同AI服务商处理
        if provider == 'openai':
            return await analyze_with_openai_api(prompt, api_key, model, api_base)
        elif provider == 'deepseek':
            return await analyze_with_deepseek_api(prompt, api_key, model)
        elif provider == 'baidu':
            return await analyze_with_baidu_api(prompt, api_key, model)
        elif provider == 'zhipu':
            return await analyze_with_zhipu_api(prompt, api_key, model)
        elif provider == 'doubao':
            return await analyze_with_doubao_api(prompt, api_key, model)
        elif provider == 'claude':
            return await analyze_with_claude_api(prompt, api_key, model)
        elif provider == 'custom':
            return await analyze_with_custom_api(prompt, api_config)
        else:
            raise ValueError(f"不支持的AI提供商: {provider}")
            
    except Exception as e:
        logger.error(f"AI分析出错: {str(e)}")
        raise

# 构建SEO分析提示词
def build_seo_analysis_prompt(site_info):
    """构建优化的SEO分析提示词"""
    
    # 基础信息
    basic_info = f"""网站基础信息：
- 名称：{site_info.get('site_name', '未设置')}
- 描述：{site_info.get('site_description', '未设置')}
- 关键词：{site_info.get('site_keywords', '未设置')}"""
    
    # 搜索引擎配置
    search_engines = []
    engines = [
        ('baidu_push_enabled', '百度'),
        ('google_index_enabled', 'Google'),
        ('bing_push_enabled', 'Bing'),
        ('yandex_push_enabled', 'Yandex'),
        ('yahoo_push_enabled', 'Yahoo'),
        ('sogou_push_enabled', '搜狗'),
        ('so_push_enabled', '360搜索'),
        ('shenma_push_enabled', '神马搜索')
    ]
    
    for key, name in engines:
        status = '已启用' if site_info.get(key, False) else '未启用'
        search_engines.append(f"- {name}：{status}")
    
    search_config = "搜索引擎配置：\n" + "\n".join(search_engines)
    
    # SEO功能配置
    seo_features = f"""SEO功能：
- 自动META标签：{'已启用' if site_info.get('auto_generate_meta_tags', True) else '未启用'}
- 网站地图生成：{'已启用' if site_info.get('generate_sitemap', True) else '未启用'}
- URL结构：{site_info.get('article_url_format', 'article/{id}')}"""
    
    # 文章内容分析（如果有）
    content_analysis = ""
    if site_info.get('recent_articles'):
        articles = site_info.get('recent_articles')
        content_analysis = f"""
最近文章分析（共{len(articles)}篇）：
{chr(10).join([f"- {article.get('title', '无标题')} [{article.get('category', '无分类')}]" for article in articles[:3]])}"""
    
    # 完整提示词
    prompt = f"""作为SEO专家，请分析以下网站配置并提供专业建议：

{basic_info}

{search_config}

{seo_features}{content_analysis}

请从以下方面分析：
1. 基础SEO设置合理性
2. 搜索引擎覆盖策略
3. 技术SEO配置优化
4. 内容SEO建议（如有文章数据）

返回JSON格式，包含：
{{
  "seo_score": 85,
  "analysis": "<p>详细分析内容，使用HTML格式</p>",
  "suggestions": [
    {{"type": "error", "message": "错误提示", "detail": "详细说明"}},
    {{"type": "warning", "message": "警告提示", "detail": "改进建议"}},
    {{"type": "info", "message": "信息提示", "detail": "优化建议"}}
  ]
}}"""
    
    return prompt

# OpenAI API调用（使用官方SDK）
async def analyze_with_openai_api(prompt, api_key, model, api_base=None):
    """OpenAI API调用 - 使用官方SDK"""
    try:
        # 确保API base URL格式正确
        if api_base and not api_base.endswith('/v1'):
            if api_base.endswith('/'):
                api_base += 'v1'
            else:
                api_base += '/v1'
        
        if not api_base:
            api_base = "https://api.openai.com/v1"
        
        # 创建OpenAI客户端
        client = AsyncOpenAI(
            api_key=api_key,
            base_url=api_base,
            timeout=30.0
        )
        
        try:
            # 使用OpenAI SDK调用API
            response = await client.chat.completions.create(
                model=model,
                messages=[
            {"role": "system", "content": "你是一个专业的SEO专家，擅长网站优化分析。请用中文回答。"},
            {"role": "user", "content": prompt}
        ],
                temperature=0.7,
                max_tokens=2000
            )
            
            if response.choices:
                ai_response = response.choices[0].message.content
                return parse_ai_response(ai_response)
            else:
                raise ValueError("OpenAI API响应格式异常")
                
        finally:
            await client.close()
            
    except Exception as e:
        logger.error(f"OpenAI API调用异常: {str(e)}")
        raise ValueError(f"OpenAI API调用失败: {str(e)}")

# DeepSeek API调用（OpenAI兼容）
async def analyze_with_deepseek_api(prompt, api_key, model):
    """DeepSeek API调用 - 使用OpenAI兼容格式"""
    return await analyze_with_openai_api(prompt, api_key, model, "https://api.deepseek.com/v1")

# 百度文心API调用
async def analyze_with_baidu_api(prompt, api_key, model):
    """百度文心API调用"""
    if not api_key or ":" not in api_key:
        raise ValueError("百度API需要提供格式为'client_id:client_secret'的api_key")
        
    try:
        # 解析API密钥
        client_id, client_secret = api_key.split(":", 1)
        
        # 获取access_token
        access_token_url = "https://aip.baidubce.com/oauth/2.0/token"
        
        async with httpx.AsyncClient(timeout=30) as client:
            token_response = await client.post(
                access_token_url,
                params={
                    "grant_type": "client_credentials",
                    "client_id": client_id,
                    "client_secret": client_secret
                }
            )
            token_response.raise_for_status()
            
            token_data = token_response.json()
            if "access_token" not in token_data:
                logger.error(f"百度API token响应异常: {token_data}")
                raise ValueError("获取百度API access_token失败")
                
            access_token = token_data["access_token"]
        
        # 调用聊天API
        api_url = f"https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/{model}"
        payload = {
            "messages": [
                {"role": "user", "content": f"你是一个专业的SEO专家，擅长网站优化分析。请用中文回答。\n\n{prompt}"}
            ],
            "temperature": 0.7
        }
        
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{api_url}?access_token={access_token}",
                headers={"Content-Type": "application/json"},
                json=payload
            )
            response.raise_for_status()
        
            result = response.json()
            
            # 检查响应中是否有错误
            if "error_code" in result:
                logger.error(f"百度API错误: {result}")
                raise ValueError(f"百度API请求失败: {result.get('error_msg', '未知错误')}")
            
            ai_response = result.get("result", "")
            if not ai_response:
                logger.error(f"百度API响应格式错误: {result}")
                raise ValueError("百度API响应格式错误")
            
            return parse_ai_response(ai_response)
        
    except httpx.HTTPStatusError as e:
        logger.error(f"百度API HTTP错误: {e.response.status_code} - {e.response.text}")
        raise ValueError(f"百度API请求失败: HTTP {e.response.status_code}")
    except httpx.TimeoutException:
        logger.error("百度API请求超时")
        raise ValueError("百度API请求超时，请检查网络连接")
    except ValueError:
        raise
    except Exception as e:
        logger.error(f"百度API调用出错: {str(e)}")
        raise ValueError(f"百度API调用失败: {str(e)}")

# 智谱AI API调用
async def analyze_with_zhipu_api(prompt, api_key, model):
    """智谱AI API调用"""
    if not api_key or "." not in api_key:
        raise ValueError("智谱AI密钥格式错误，应为: id.secret")
        
    try:
        # 解析API密钥
        api_key_parts = api_key.split(".", 1)
        if len(api_key_parts) != 2:
            raise ValueError("智谱AI密钥格式错误，应为: id.secret")
            
        id, secret = api_key_parts
        
        # 生成JWT token
        payload = {
            "api_key": id,
            "exp": int(time.time()) + 3600,
            "timestamp": int(time.time())
        }
        
        try:
            token = jwt.encode(payload, secret, algorithm="HS256")
        except Exception as e:
            logger.error(f"智谱AI JWT生成失败: {str(e)}")
            raise ValueError("智谱AI JWT生成失败，请检查密钥格式")
    
        api_url = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    
        request_payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": "你是一个专业的SEO专家，擅长网站优化分析。请用中文回答。"},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0.7,
            "max_tokens": 2000
        }
        
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}"
        }
        
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(api_url, headers=headers, json=request_payload)
            response.raise_for_status()
        
            result = response.json()
            
            # 检查响应格式
            if "choices" not in result or not result["choices"]:
                logger.error(f"智谱AI响应格式错误: {result}")
                raise ValueError("智谱AI响应格式错误")
            
            ai_response = result["choices"][0]["message"]["content"]
            if not ai_response:
                logger.error(f"智谱AI响应内容为空: {result}")
                raise ValueError("智谱AI响应内容为空")
            
            return parse_ai_response(ai_response)
        
    except httpx.HTTPStatusError as e:
        logger.error(f"智谱AI HTTP错误: {e.response.status_code} - {e.response.text}")
        raise ValueError(f"智谱AI请求失败: HTTP {e.response.status_code}")
    except httpx.TimeoutException:
        logger.error("智谱AI请求超时")
        raise ValueError("智谱AI请求超时，请检查网络连接")
    except ValueError:
        raise
    except Exception as e:
        logger.error(f"智谱AI调用出错: {str(e)}")
        raise ValueError(f"智谱AI调用失败: {str(e)}")

# 豆包API调用（OpenAI兼容）
async def analyze_with_doubao_api(prompt, api_key, model):
    """豆包API调用 - 使用OpenAI兼容格式"""
    return await analyze_with_openai_api(prompt, api_key, model, "https://api.doubao.com/v1")

# Claude API调用（使用官方SDK）
async def analyze_with_claude_api(prompt, api_key, model):
    """Claude API调用 - 使用Anthropic官方SDK"""
    try:
        # 创建Anthropic客户端
        client = AsyncAnthropic(
            api_key=api_key,
            timeout=30.0
        )
        
        try:
            # 使用Anthropic SDK调用API
            response = await client.messages.create(
                model=model,
                max_tokens=2000,
                temperature=0.7,
                system="你是一个专业的SEO专家，擅长网站优化分析。请用中文回答。",
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )
            
            if response.content:
                ai_response = response.content[0].text
                return parse_ai_response(ai_response)
            else:
                raise ValueError("Claude API响应格式异常")
                
        finally:
            await client.close()
            
    except Exception as e:
        logger.error(f"Claude API调用异常: {str(e)}")
        raise ValueError(f"Claude API调用失败: {str(e)}")


# Gemini API调用
async def analyze_with_gemini_api(prompt, api_key, model="gemini-pro"):
    """Google Gemini API调用"""
    if not api_key:
        raise ValueError("Gemini API需要提供api_key")
    
    try:
        # 构建API URL
        api_url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        
        payload = {
            "contents": [{
                "parts": [{
                    "text": f"你是一个专业的SEO专家，擅长网站优化分析。请用中文回答。\n\n{prompt}"
                }]
            }],
            "generationConfig": {
                "temperature": 0.7,
                "topK": 40,
                "topP": 0.8,
                "maxOutputTokens": 1024
            }
        }
        
        headers = {
            "Content-Type": "application/json"
        }
        
        params = {"key": api_key}
        
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(api_url, headers=headers, json=payload, params=params)
        response.raise_for_status()
        
        result = response.json()
        
        # 提取生成的内容
        ai_response = result.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "")
        
        if not ai_response:
            logger.error(f"Gemini API响应格式错误: {result}")
            raise ValueError("Gemini API响应格式错误")
        
        return parse_ai_response(ai_response)
        
    except httpx.HTTPStatusError as e:
        logger.error(f"Gemini API HTTP错误: {e.response.status_code} - {e.response.text}")
        raise ValueError(f"Gemini API请求失败: HTTP {e.response.status_code}")
    except httpx.TimeoutException:
        logger.error("Gemini API请求超时")
        raise ValueError("Gemini API请求超时，请检查网络连接")
    except Exception as e:
        logger.error(f"Gemini API调用出错: {str(e)}")
        raise ValueError(f"Gemini API调用失败: {str(e)}")

# 自定义API调用（简化版）
async def analyze_with_custom_api(prompt, api_config):
    """自定义AI API调用 - 仅支持OpenAI和Anthropic兼容格式"""
    api_url = api_config.get('custom_api_url')
    api_key = api_config.get('api_key')
    model = api_config.get('model', 'custom-model')
    request_format = api_config.get('request_format', 'openai')  # 默认openai格式
    
    if not api_url:
        raise ValueError("自定义API需要提供custom_api_url")
    
    if not api_key:
        raise ValueError("自定义API需要提供api_key")
    
    try:
        if request_format == 'openai':
            # 使用OpenAI兼容格式（大部分AI服务都采用此格式）
            return await analyze_with_openai_api(prompt, api_key, model, api_url)
            
        elif request_format == 'anthropic':
            # 使用Anthropic兼容格式
            # 注意：这里需要确保API URL兼容Anthropic格式
            return await analyze_with_claude_api(prompt, api_key, model)
            
        else:
            raise ValueError(f"不支持的请求格式: {request_format}。仅支持 'openai' 或 'anthropic' 格式")
            
    except Exception as e:
        logger.error(f"自定义API调用出错: {str(e)}")
        raise ValueError(f"自定义API调用失败: {str(e)}")

# 解析AI响应
def parse_ai_response(ai_response):
    """解析AI返回的响应"""
    try:
        # 尝试直接解析JSON
        result = json.loads(ai_response)
        
        # 验证必需字段
        if not all(key in result for key in ['analysis', 'seo_score', 'suggestions']):
            logger.warning("AI返回的结果格式不完整，使用备用解析")
            return parse_text_response(ai_response)
            
        return result
    except json.JSONDecodeError:
        # 如果不是JSON，尝试提取内容
        logger.info("AI返回非JSON格式，尝试文本解析")
        return parse_text_response(ai_response)

# 解析文本响应（备用方案）
def parse_text_response(text_response):
    """当AI返回非JSON格式时的备用解析方案"""
    try:
        # 简单的评分提取
        score_match = re.search(r'(\d+)\s*分|评分\s*[:：]\s*(\d+)|得分\s*[:：]\s*(\d+)', text_response)
        seo_score = 70  # 默认分数
        if score_match:
            scores = [int(s) for s in score_match.groups() if s]
            if scores:
                seo_score = min(max(scores[0], 0), 100)  # 限制在0-100之间
        
        # 生成简单的建议列表
        suggestions = []
        if '错误' in text_response or '问题' in text_response:
            suggestions.append({
                "type": "warning",
                "message": "检测到需要改进的地方",
                "detail": "请查看详细分析内容"
            })
        
        return {
            "seo_score": seo_score,
            "analysis": f"<p>{text_response}</p>",
            "suggestions": suggestions
        }
    except Exception as e:
        logger.error(f"解析文本响应出错: {str(e)}")
        return {
            "seo_score": 70,
            "analysis": "<p>AI分析完成，但格式解析出现问题。</p>",
            "suggestions": [
                {"type": "info", "message": "分析完成", "detail": "建议检查SEO配置"}
            ]
        }

# 获取最近的文章数据
def get_recent_articles(limit=5):
    try:
        # 从Java后端获取最近文章
        response = httpx.get(f"{JAVA_BACKEND_URL}/article/listArticle?current=1&size={limit}&status=1")
        if response.status_code != 200:
            logger.error(f"获取文章数据失败: HTTP状态码 {response.status_code}")
            return []
            
        articles_data = response.json()
        if not articles_data or not articles_data.get('data') or not articles_data.get('data').get('records'):
            logger.warning("获取文章数据成功，但无文章记录")
            return []
            
        articles = articles_data.get('data').get('records')
        
        # 提取需要的文章信息
        article_summaries = []
        for article in articles:
            # 获取简化的文章内容（前500个字符）
            content = article.get('articleContent', '')
            if content:
                # 移除Markdown和HTML标记
                content = re.sub(r'<[^>]+>', '', content)
                content = re.sub(r'\[.*?\]\(.*?\)', '', content)
                content = content[:500] + "..." if len(content) > 500 else content
                
            article_summaries.append({
                "id": article.get('id'),
                "title": article.get('articleTitle', ''),
                "category": article.get('categoryName', ''),
                "tags": article.get('tagNames', []),
                "summary": content
            })
            
        return article_summaries
    except Exception as e:
        logger.error(f"获取文章数据出错: {str(e)}")
        return []

# 生成分类的元数据
async def generate_category_meta_tags(category_id):
    try:
        # 获取分类信息
        logger.info(f"尝试获取分类元数据，分类ID: {category_id}")
        logger.info(f"请求URL: {JAVA_BACKEND_URL}/category/getCategoryById?id={category_id}")
        
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{JAVA_BACKEND_URL}/category/getCategoryById?id={category_id}", 
                headers=headers,
                timeout=5
            )
        
        logger.info(f"获取分类信息响应状态码: {response.status_code}")
        
        if response.status_code != 200:
            logger.warning(f"获取分类信息失败，状态码: {response.status_code}, 响应: {response.text[:100]}")
            return None
        
        try:
            category_data = response.json().get('data', {})
        except Exception as e:
            logger.error(f"解析分类信息响应失败: {str(e)}")
            logger.error(f"响应文本: {response.text[:200]}")
            return None
            
        if not category_data:
            logger.warning("获取分类信息成功，但数据为空")
            return None
            
        logger.info(f"成功获取分类信息，名称: {category_data.get('categoryName', '无名称')}")
        
        seo_config = await get_seo_config()
        
        category_name = category_data.get('categoryName', '')
        category_desc = category_data.get('categoryDescription', '')
        
        meta_tags = {
            "title": f"{category_name} - {seo_config.get('site_name', '')}",
            "description": category_desc or f"查看关于{category_name}的所有文章 - {seo_config.get('site_name', '')}",
            "keywords": f"{category_name},{seo_config.get('keywords', '')}",
            "og:title": f"{category_name} - {seo_config.get('site_name', '')}",
            "og:description": category_desc or f"查看关于{category_name}的所有文章 - {seo_config.get('site_name', '')}",
            "og:type": "website",
            "og:url": f"{seo_config.get('site_address', FRONTEND_URL)}/{seo_config.get('category_url_format', 'category/{id}')}".replace('{id}', str(category_id)),
            "og:image": seo_config.get('og_image', ''),
            "twitter:card": seo_config.get('twitter_card', "summary_large_image"),
            "twitter:title": f"{category_name} - {seo_config.get('site_name', '')}",
            "twitter:description": category_desc or f"查看关于{category_name}的所有文章 - {seo_config.get('site_name', '')}"
        }
        
        logger.info(f"生成分类元数据成功，元标签数量: {len(meta_tags)}")
        return meta_tags
    except Exception as e:
        logger.error(f"生成分类元数据出错: {str(e)}")
        logger.exception("详细错误信息:")
        return None

# 生成网站的元数据标签
def generate_site_meta_tags(lang=None):
    """
    生成站点的元数据标签内容
    :param lang: 语言参数，用于设置HTML语言标识
    :return: HTML格式的元数据标签内容
    """
    try:
        # 获取SEO配置
        seo_config = get_seo_config_sync()
        
        # 统一使用配置文件中的站点信息（中文）
        site_name = seo_config.get('site_name', '')
        site_description = seo_config.get('site_description', '')
        site_keywords = seo_config.get('site_keywords', '')
        
        logger.info(f"使用SEO配置中的网站标题: {site_name} (语言: {lang or '默认'})")
        
        # 构建基础meta标签列表
        html_tags = []
        
        # 1. 基础必需标签
        if site_name:
            html_tags.append(f'<title>{site_name}</title>')
        if site_description:
            html_tags.append(f'<meta name="description" content="{site_description}">')
        if site_keywords:
            html_tags.append(f'<meta name="keywords" content="{site_keywords}">')
        
        # 2. 添加固定标签
        html_tags.append('<meta name="robots" content="index, follow">')
        
        # 3. 语言标识（如果有lang参数）
        if lang:
            html_tags.append(f'<meta name="language" content="{lang}">')
            locale = "en_US" if lang == 'en' else "zh_CN"
            html_tags.append(f'<meta property="og:locale" content="{locale}">')
        
        # 4. OpenGraph标签
        if site_name:
            html_tags.append(f'<meta property="og:title" content="{site_name}">')
        if site_description:
            html_tags.append(f'<meta property="og:description" content="{site_description}">')
        
        og_type = seo_config.get('og_type', 'website')
        html_tags.append(f'<meta property="og:type" content="{og_type}">')
        
        site_address = seo_config.get('site_address', FRONTEND_URL)
        if site_address:
            html_tags.append(f'<meta property="og:url" content="{site_address}">')
        
        # 5. Twitter标签
        twitter_card = seo_config.get('twitter_card', 'summary_large_image')
        html_tags.append(f'<meta name="twitter:card" content="{twitter_card}">')
        if site_name:
            html_tags.append(f'<meta name="twitter:title" content="{site_name}">')
        if site_description:
            html_tags.append(f'<meta name="twitter:description" content="{site_description}">')
        
        # 6. 批量处理可选配置项
        optional_tags = {
            'default_author': lambda v: f'<meta name="author" content="{v}">',
            'og_image': lambda v: [f'<meta property="og:image" content="{v}">', f'<meta name="twitter:image" content="{v}">'],
            'og_site_name': lambda v: f'<meta property="og:site_name" content="{v}">',
            'twitter_site': lambda v: f'<meta name="twitter:site" content="{v}">',
            'twitter_creator': lambda v: f'<meta name="twitter:creator" content="{v}">',
            'site_logo': lambda v: f'<meta property="og:logo" content="{v}">',
            'site_favicon': lambda v: f'<link rel="shortcut icon" href="{v}">',
            'fb_app_id': lambda v: f'<meta property="fb:app_id" content="{v}">',
            'fb_page_url': lambda v: f'<meta property="article:publisher" content="{v}">',
        }
        
        for key, tag_func in optional_tags.items():
            value = seo_config.get(key)
            if value:
                result = tag_func(value)
                if isinstance(result, list):
                    html_tags.extend(result)
                else:
                    html_tags.append(result)
        
        # 7. 批量处理搜索引擎验证标签
        verification_mapping = {
            'baidu_site_verification': 'baidu-site-verification',
            'google_site_verification': 'google-site-verification',
            'bing_site_verification': 'msvalidate.01',
            'yandex_site_verification': 'yandex-verification',
            'sogou_site_verification': 'sogou_site_verification',
            'so_site_verification': '360-site-verification',
            'shenma_site_verification': 'shenma-site-verification',
            'yahoo_site_verification': 'y_key',
            'duckduckgo_site_verification': 'duckduckgo-site-verification'
        }
        
        for config_key, meta_name in verification_mapping.items():
            value = seo_config.get(config_key)
            if value:
                html_tags.append(f'<meta name="{meta_name}" content="{value}">')
        
        # 8. 微信小程序相关（批量处理）
        if seo_config.get('enable_wechat_miniprogram', False):
            wechat_tags = {
                'wechat_miniprogram_appid': 'wechat:miniprogram:appid',
                'wechat_miniprogram_path': 'wechat:miniprogram:path'
            }
            for config_key, meta_name in wechat_tags.items():
                value = seo_config.get(config_key)
                if value:
                    html_tags.append(f'<meta name="{meta_name}" content="{value}">')
        
        # 9. 其他可选标签（批量处理）
        other_optional_tags = {
            'pinterest_verification': 'p:domain_verify',
            'pinterest_description': 'pinterest:description',
            'linkedin_company_id': 'linkedin:owner'
        }
        
        for config_key, meta_name in other_optional_tags.items():
            value = seo_config.get(config_key)
            if value:
                html_tags.append(f'<meta name="{meta_name}" content="{value}">')
        
        # 10. 自定义头部代码
        custom_head_code = seo_config.get('custom_head_code')
        if custom_head_code:
            html_tags.append(custom_head_code)
        
        logger.info(f"生成网站元数据成功，元标签数量: {len(html_tags)}，语言: {lang or '默认'}")
        return "\n".join(html_tags)
        
    except Exception as e:
        logger.error(f"生成站点元数据标签异常: {str(e)}")
        logger.exception("详细错误信息:")
        return ""

# 生成IM聊天室的元数据标签
def generate_im_site_meta_tags():
    """
    生成IM聊天室页面的元数据标签内容
    :return: HTML格式的元数据标签内容
    """
    try:
        # 获取SEO配置
        seo_config = get_seo_config_sync()
        
        # 直接使用SEO配置中的网站名称
        site_name = seo_config.get('site_name', '博客系统')
        logger.info(f"使用SEO配置中的网站标题: {site_name}")
        
        # IM聊天室专用的元数据
        im_title = f"{site_name} IM聊天室"
        im_description = "实时在线聊天室，支持公共聊天、私聊、表情、图片分享等功能"
        im_keywords = "聊天室,在线聊天,IM,即时通讯,WebSocket,私聊,表情,图片分享"
        
        # 构建HTML标签列表
        html_tags = []
        
        # 1. 基础必需标签
        html_tags.append(f'<title>{im_title}</title>')
        html_tags.append(f'<meta name="description" content="{im_description}">')
        html_tags.append(f'<meta name="keywords" content="{im_keywords}">')
        html_tags.append('<meta name="robots" content="index, follow">')
        
        # 2. 作者标签
        author = seo_config.get('default_author')
        if author:
            html_tags.append(f'<meta name="author" content="{author}">')
        
        # 3. OpenGraph标签
        site_address = seo_config.get('site_address', FRONTEND_URL)
        og_image = seo_config.get('og_image')
        og_site_name = seo_config.get('og_site_name', site_name)
        
        og_tags = [
            f'<meta property="og:title" content="{im_title}">',
            f'<meta property="og:description" content="{im_description}">',
            f'<meta property="og:type" content="website">',
            f'<meta property="og:url" content="{site_address}/im">',
            f'<meta property="og:site_name" content="{og_site_name}">'
        ]
        if og_image:
            og_tags.append(f'<meta property="og:image" content="{og_image}">')
        
        html_tags.extend(og_tags)
        
        # 4. Twitter标签
        twitter_card = seo_config.get('twitter_card', 'summary_large_image')
        twitter_site = seo_config.get('twitter_site')
        twitter_creator = seo_config.get('twitter_creator')
        
        twitter_tags = [
            f'<meta name="twitter:card" content="{twitter_card}">',
            f'<meta name="twitter:title" content="{im_title}">',
            f'<meta name="twitter:description" content="{im_description}">'
        ]
        if og_image:
            twitter_tags.append(f'<meta name="twitter:image" content="{og_image}">')
        if twitter_site:
            twitter_tags.append(f'<meta name="twitter:site" content="{twitter_site}">')
        if twitter_creator:
            twitter_tags.append(f'<meta name="twitter:creator" content="{twitter_creator}">')
        
        html_tags.extend(twitter_tags)
        
        # 5. 批量处理可选配置项
        optional_tags = {
            'site_logo': lambda v: f'<meta property="og:logo" content="{v}">',
            'site_favicon': lambda v: f'<link rel="shortcut icon" href="{v}">',
            'fb_app_id': lambda v: f'<meta property="fb:app_id" content="{v}">',
            'fb_page_url': lambda v: f'<meta property="article:publisher" content="{v}">'
        }
        
        for key, tag_func in optional_tags.items():
            value = seo_config.get(key)
            if value:
                html_tags.append(tag_func(value))
        
        # 6. IM页面特有的元标签
        im_specific_tags = [
            f'<meta name="application-name" content="{site_name} IM">',
            f'<meta name="application-type" content="Chat">',
            f'<meta name="application-tooltip" content="在线聊天室">',
            f'<meta name="revisit-after" content="1 day">'
        ]
        html_tags.extend(im_specific_tags)
        
        # 7. 批量处理搜索引擎验证标签（使用与站点主页相同的映射）
        verification_mapping = {
            'baidu_site_verification': 'baidu-site-verification',
            'google_site_verification': 'google-site-verification',
            'bing_site_verification': 'msvalidate.01',
            'yandex_site_verification': 'yandex-verification',
            'sogou_site_verification': 'sogou_site_verification',
            'so_site_verification': '360-site-verification',
            'shenma_site_verification': 'shenma-site-verification',
            'yahoo_site_verification': 'y_key',
            'duckduckgo_site_verification': 'duckduckgo-site-verification'
        }
        
        for config_key, meta_name in verification_mapping.items():
            value = seo_config.get(config_key)
            if value:
                html_tags.append(f'<meta name="{meta_name}" content="{value}">')
        
        # 8. IM页面特有的预加载标签
        preload_tags = [
            f'<link rel="preload" href="/im/css/chat.css" as="style">',
            f'<link rel="preload" href="/im/js/socket.io.min.js" as="script">'
        ]
        html_tags.extend(preload_tags)
        
        logger.info(f"生成IM聊天室元数据成功，元标签数量: {len(html_tags)}")
        return "\n".join(html_tags)
        
    except Exception as e:
        logger.error(f"生成IM聊天室元数据标签异常: {str(e)}")
        logger.exception("详细错误信息:")
        return ""

# 装饰器：检查SEO功能是否启用
def check_seo_enabled(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        seo_config = get_seo_config_sync()
        if not seo_config.get('enable', False):
            func_name = func.__name__
            logger.info(f"SEO功能已关闭，跳过执行: {func_name}")
            # 对于返回网页的接口，返回404
            if func_name in ['get_sitemap_api', 'get_robots_api']:
                return JSONResponse({"code": 404, "message": "Not Found", "data": None})
            # 对于返回JSON的API接口，返回提示信息
            return JSONResponse({"code": 400, "message": "SEO功能已关闭", "data": None})
        return func(*args, **kwargs)
    return wrapper

# 清除SEO相关文件
def clear_seo_files():
    """清除sitemap.xml和robots.txt文件"""
    try:
        # 清除sitemap.xml
        sitemap_path = os.path.join(DATA_DIR, 'sitemap.xml')
        if os.path.exists(sitemap_path):
            os.remove(sitemap_path)
            logger.info('已清除sitemap.xml文件')
        
        # 清除robots.txt
        robots_path = os.path.join(DATA_DIR, 'robots.txt')
        if os.path.exists(robots_path):
            os.remove(robots_path)
            logger.info('已清除robots.txt文件')
            
        return True
    except Exception as e:
        logger.error(f'清除SEO文件时发生错误: {str(e)}')
        return False

# Yahoo搜索引擎推送函数
async def yahoo_push_url(url):
    try:
        seo_config = await get_seo_config()
        yahoo_api_key = seo_config.get('yahoo_api_key', '')
        if not yahoo_api_key:
            logger.error("Yahoo API密钥未设置")
            return False, "Yahoo API密钥未设置"
        
        # Yahoo站长工具API
        api_url = f"https://api.yahoo.com/indexing/v3/index"
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {yahoo_api_key}'
        }
        
        # 构建请求数据
        payload = {
            'url': url,
            'type': 'URL_SUBMISSION'
        }
        
        # 发送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(api_url, headers=headers, json=payload, timeout=10)
        
        if response.status_code in [200, 201, 202]:
            result = response.json() if response.text else {'status': 'success'}
            logger.info(f"Yahoo推送成功: {result}")
            return True, result
        else:
            logger.error(f"Yahoo推送失败: {response.text}")
            return False, response.text
    except Exception as e:
        logger.error(f"Yahoo推送出错: {str(e)}")
        return False, str(e)

# 增量更新Sitemap工具

def _load_or_init_sitemap(site_url: str):
    """加载现有 sitemap.xml，如不存在则生成并加载"""
    sitemap_path = os.path.join(DATA_DIR, 'sitemap.xml')
    if not os.path.exists(sitemap_path):
        # 同步生成完整站点地图
        loop = asyncio.get_event_loop()
        if loop.is_running():
            loop.run_until_complete(generate_sitemap())
        else:
            asyncio.run(generate_sitemap())

    tree = ET.parse(sitemap_path)
    root = tree.getroot()
    return tree, root, sitemap_path

async def add_or_update_sitemap_url(url: str, lastmod: str = None, changefreq: str = 'weekly', priority: str = '0.7'):
    """在 sitemap.xml 中新增或更新单条 <url> 节点"""
    try:
        seo_config = await get_seo_config()
        site_url = seo_config.get('site_address', FRONTEND_URL)
        tree, root, sitemap_path = _load_or_init_sitemap(site_url)

        # 去重比较使用去掉末尾 /
        target = url.rstrip('/')

        # 查找已存在节点
        found = None
        for url_elem in root.findall('{http://www.sitemaps.org/schemas/sitemap/0.9}url'):
            loc_elem = url_elem.find('{http://www.sitemaps.org/schemas/sitemap/0.9}loc')
            if loc_elem is not None and loc_elem.text.rstrip('/') == target:
                found = url_elem
                break

        if not lastmod:
            lastmod = time.strftime('%Y-%m-%d')

        ns = {'sm': 'http://www.sitemaps.org/schemas/sitemap/0.9'}

        if found is None:
            # 创建新节点
            new_url = ET.SubElement(root, '{http://www.sitemaps.org/schemas/sitemap/0.9}url')
            ET.SubElement(new_url, '{http://www.sitemaps.org/schemas/sitemap/0.9}loc').text = url
            ET.SubElement(new_url, '{http://www.sitemaps.org/schemas/sitemap/0.9}lastmod').text = lastmod
            ET.SubElement(new_url, '{http://www.sitemaps.org/schemas/sitemap/0.9}changefreq').text = changefreq
            ET.SubElement(new_url, '{http://www.sitemaps.org/schemas/sitemap/0.9}priority').text = priority
        else:
            # 更新 lastmod
            lm_elem = found.find('sm:lastmod', ns)
            if lm_elem is not None:
                lm_elem.text = lastmod

        tree.write(sitemap_path, encoding='utf-8', xml_declaration=True)
        logger.info(f"增量更新 sitemap 成功: {url}")
    except Exception as e:
        logger.error(f"增量更新 sitemap 失败: {e}")

async def remove_sitemap_url(url: str):
    """将指定 URL 从 sitemap.xml 中移除"""
    try:
        seo_config = await get_seo_config()
        site_url = seo_config.get('site_address', FRONTEND_URL)
        tree, root, sitemap_path = _load_or_init_sitemap(site_url)

        target = url.rstrip('/')
        removed = False
        for url_elem in list(root):
            loc_elem = url_elem.find('{http://www.sitemaps.org/schemas/sitemap/0.9}loc')
            if loc_elem is not None and loc_elem.text.rstrip('/') == target:
                root.remove(url_elem)
                removed = True
                break

        if removed:
            tree.write(sitemap_path, encoding='utf-8', xml_declaration=True)
            logger.info(f"已从 sitemap 移除 URL: {url}")
    except Exception as e:
        logger.error(f"移除 sitemap URL 失败: {e}")