import os
import secrets
import socket
import httpx
from urllib.parse import urlparse
import json
# FastAPI应用密钥（用于中间件加密）
SECRET_KEY = os.environ.get('SECRET_KEY', secrets.token_hex(16))

# 自动检测后端地址
def detect_backend_url():
    print("开始检测Java后端地址...")
    
    # 1. 优先使用环境变量
    if 'JAVA_BACKEND_HOST' in os.environ:
        host = os.environ.get('JAVA_BACKEND_HOST')
        port = os.environ.get('JAVA_BACKEND_PORT', '8081')
        url = f"http://{host}:{port}"
        print(f"从环境变量检测到Java后端: {url}")
        return url
    
    # 2. 尝试通过域名解析
    try:
        # 尝试解析"java-backend"，常见的Docker Compose或K8s服务名
        socket.gethostbyname("java-backend")
        url = "http://java-backend:8081"
        print(f"通过域名解析检测到Java后端: {url}")
        return url
    except socket.gaierror:
        print("无法解析'java-backend'域名")
    
    # 3. 尝试连接常见地址
    candidates = [
        "http://localhost:8081",  # 优先尝试8081端口
        "http://127.0.0.1:8081", 
        "http://host.docker.internal:8081",  # Docker on Mac/Windows
        "http://poetize-java:8081"
    ]
    
    for url in candidates:
        try:
            print(f"尝试连接Java后端: {url}")
            # 尝试连接可能的API端点
            endpoints = ['/actuator/health', '/health', '/api/health', '/mail/test', '/api/mail/test', '/']
            
            for endpoint in endpoints:
                try:
                    test_url = f"{url}{endpoint}"
                    print(f"尝试连接: {test_url}")
                    response = httpx.get(test_url, timeout=3.0)
                    print(f"成功连接到 {test_url}, 状态码: {response.status_code}")
                    return url
                except Exception as e:
                    print(f"连接 {test_url} 失败: {str(e)}")
                    continue
        except Exception as e:
            print(f"无法连接到 {url}: {str(e)}")
            continue
    
    # 4. 使用默认地址
    default_url = "http://localhost:8081"
    print(f"无法检测到Java后端，使用默认地址: {default_url}")
    return default_url

# 自动获取基础URL
BASE_BACKEND_URL = os.environ.get('JAVA_BACKEND_BASE_URL', detect_backend_url())

# API端点
JAVA_BACKEND_URL = os.environ.get('JAVA_BACKEND_URL', BASE_BACKEND_URL)

# 配置同步API
JAVA_CONFIG_URL = os.environ.get('JAVA_CONFIG_URL', f"{BASE_BACKEND_URL}")

# Java验证API
JAVA_AUTH_URL = os.environ.get('JAVA_AUTH_URL', f"{BASE_BACKEND_URL}/user/checkAdminAuth")

# 自动推断前端URL
def detect_frontend_url():    
    # 1. 优先使用环境变量
    if 'FRONTEND_HOST' in os.environ:
        host = os.environ.get('FRONTEND_HOST')
        port = os.environ.get('FRONTEND_PORT', '80')  # 默认使用80端口
        protocol = os.environ.get('FRONTEND_PROTOCOL', 'http')  # 支持HTTPS
        url = f"{protocol}://{host}"
        # 只有当端口不是标准端口(80/443)时才添加
        if (protocol == 'http' and port != '80') or (protocol == 'https' and port != '443'):
            url += f":{port}"
        print(f"从环境变量获取前端URL: {url}")
        return url
    
    # 2. 从后端URL推断（假设前端和后端在同一域名不同端口或路径）
    if BASE_BACKEND_URL != "http://localhost:8081":
        parsed = urlparse(BASE_BACKEND_URL)
        host = parsed.netloc.split(':')[0]
        # 如果不是本地地址，假设前端使用标准HTTP端口
        if host not in ('localhost', '127.0.0.1'):
            print(f"从后端URL推断前端URL: http://{host}")
            return f"http://{host}"
    
    # 3. 尝试读取SEO配置文件获取站点地址
    try:
        data_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'data')
        config_path = os.path.join(data_dir, 'seo_config.json')
        
        if os.path.exists(config_path):
            with open(config_path, 'r', encoding='utf-8') as f:
                seo_config = json.load(f)
                if seo_config.get('site_address'):
                    print(f"从SEO配置获取前端URL: {seo_config['site_address']}")
                    return seo_config['site_address']
    except Exception as e:
        print(f"读取SEO配置获取前端URL失败: {str(e)}")
    
    # 4. 默认前端URL - 不再假设8080
    default_url = "http://localhost"  # 不指定端口，依赖站点的默认配置
    print(f"无法检测到前端URL，使用默认值: {default_url}")
    return default_url

# 前端URL
FRONTEND_URL = os.environ.get('FRONTEND_URL', detect_frontend_url())

# Python服务端口
PYTHON_SERVICE_PORT = int(os.environ.get('PORT', 5000))

# 输出检测到的配置
print(f"检测到Java后端URL: {BASE_BACKEND_URL}")
print(f"第三方登录API: {JAVA_BACKEND_URL}")
print(f"配置获取API: {JAVA_CONFIG_URL}")
print(f"验证API: {JAVA_AUTH_URL}")
print(f"前端URL: {FRONTEND_URL}")
print(f"Python服务端口: {PYTHON_SERVICE_PORT}")