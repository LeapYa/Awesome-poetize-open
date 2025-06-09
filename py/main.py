"""
POETIZE博客系统 - FastAPI后端服务
支持SEO优化、第三方登录、邮件发送等功能

主要功能模块：
- SEO优化和搜索引擎推送
- 第三方OAuth登录 (GitHub, Google, Twitter, Yandex, Gitee)
- 邮件发送服务
- 网站管理API
- 访问统计
- 多语言翻译
- AI聊天配置
"""

import os
from fastapi import FastAPI, Request
from fastapi.responses import RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from config import SECRET_KEY, JAVA_BACKEND_URL, FRONTEND_URL, JAVA_CONFIG_URL, PYTHON_SERVICE_PORT
from web_admin_api import register_web_admin_api
from py_three_login import oauth_login, oauth_callback
from visit_stats_api import register_visit_stats_api
from email_api import register_email_api  # 仅处理邮箱配置和测试功能，实际邮件发送由Java后端处理
from captcha_api import register_captcha_api  # 处理滑动验证码配置功能
from seo_api import register_seo_api  # 处理SEO优化相关功能
from ai_chat_api import register_ai_chat_api  # 处理AI聊天配置功能
from translation_api import register_translation_api  # 处理翻译管理功能
from auth_decorator import admin_required  # 导入管理员权限验证装饰器
import httpx
import logging

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 定义静态文件夹
STATIC_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'static')
if not os.path.exists(STATIC_FOLDER):
    os.makedirs(STATIC_FOLDER)

# 创建FastAPI应用实例
app = FastAPI(
    title="POETIZE博客系统API",
    description="基于FastAPI的博客系统后端服务，提供SEO优化、第三方登录等功能",
    version="2.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    max_age=3600
)

@app.get("/")
async def index():
    return RedirectResponse(url=FRONTEND_URL)

# 高可用性检查接口
@app.get("/api/health")
async def health_check():
    """API健康检查端点，返回系统状态和版本信息"""
    return {
        'status': 'ok',
        'api_version': os.environ.get('VERSION', 'dev'),
        'app_version': os.environ.get('VERSION', 'dev'),
        'translation_model': 'integrated',  # 翻译功能已整合
    }

# 高可用性检查接口 - 同时支持旧路径
@app.get("/health")
async def health_check_old():
    """兼容旧的健康检查路径"""
    return await health_check()

def register_all_apis(app):
    """注册所有API模块"""
    # 注册OAuth路由
    app.add_api_route('/oauth/login/{provider}', oauth_login, methods=['GET'])
    app.add_api_route('/oauth/callback/{provider}', oauth_callback, methods=['GET'])
    
    # 注册各个API模块
    register_web_admin_api(app)
    register_visit_stats_api(app)
    register_email_api(app)
    register_captcha_api(app)
    register_seo_api(app)
    register_ai_chat_api(app)  # 注册AI聊天配置API
    register_translation_api(app)  # 注册翻译管理API
    
    logger.info("所有API模块已注册完成")

# 注册所有API
register_all_apis(app)

if __name__ == '__main__':
    import uvicorn
    
    port = int(os.environ.get('PORT', PYTHON_SERVICE_PORT))
    debug = os.environ.get('DEBUG', 'False').lower() == 'true'
    
    logger.info("==========================================")
    logger.info("POETIZE博客系统 - FastAPI后端服务启动中...")
    logger.info("==========================================")
    logger.info(f"服务端口: {port}")
    logger.info(f"调试模式: {debug}")
    logger.info(f"Java后端: {JAVA_BACKEND_URL}")
    logger.info(f"前端地址: {FRONTEND_URL}")
    logger.info("==========================================")
    
    uvicorn.run(
        "main:app", 
        host='0.0.0.0', 
        port=port, 
        reload=debug,
        log_level="info"
    ) 