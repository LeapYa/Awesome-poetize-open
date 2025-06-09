import functools
import httpx
import logging
import time
import os
from fastapi import Request, HTTPException, Depends, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional
from config import JAVA_AUTH_URL

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('auth_decorator')

# 创建限流器 - 单IP最大请求数/时间窗口
class RateLimiter:
    def __init__(self, max_requests=10, window_seconds=60):
        self.max_requests = max_requests  # 每个窗口允许的最大请求数
        self.window_seconds = window_seconds  # 窗口时间（秒）
        self.request_logs = {}  # 存储请求记录 {ip: [(timestamp1), (timestamp2), ...]}
        
    def is_allowed(self, ip):
        """检查IP是否被允许访问"""
        current_time = time.time()
        
        # 清理过期记录
        if ip in self.request_logs:
            self.request_logs[ip] = [t for t in self.request_logs[ip] 
                                    if current_time - t < self.window_seconds]
        else:
            self.request_logs[ip] = []
            
        # 检查是否超出限制
        if len(self.request_logs[ip]) >= self.max_requests:
            logger.warning(f"IP {ip} 请求过于频繁，被限流")
            return False
            
        # 记录本次请求
        self.request_logs[ip].append(current_time)
        return True

# 初始化限流器 - 管理员API限制更严格
admin_rate_limiter = RateLimiter(max_requests=20, window_seconds=60)

# 从环境变量获取内部服务IP列表
def get_trusted_internal_ips():
    # 默认的受信任IP
    trusted_ips = [
        'localhost',
        '127.0.0.1'
    ]
    
    # 从环境变量添加JAVA_SERVICE_IP
    java_service_ip = os.environ.get('JAVA_SERVICE_IP', 'poetize-java')
    if java_service_ip:
        trusted_ips.append(java_service_ip)
    
    # 从环境变量添加TRUSTED_IPS列表
    trusted_ips_env = os.environ.get('TRUSTED_IPS', '')
    if trusted_ips_env:
        for ip in trusted_ips_env.split(','):
            ip = ip.strip()
            if ip:
                trusted_ips.append(ip)
    
    # 添加Docker内部网络IP
    docker_ips = ['172.19.0.1', '172.19.0.2', '172.19.0.3', '172.19.0.4', '172.19.0.5']
    trusted_ips.extend(docker_ips)
    
    logger.info(f"初始化受信任的内部IP列表: {trusted_ips}")
    return trusted_ips

# 受信任的内部服务IP列表
TRUSTED_INTERNAL_IPS = get_trusted_internal_ips()

# FastAPI Security scheme
security = HTTPBearer(auto_error=False)

async def admin_required(
    request: Request,
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)
):
    """
    FastAPI依赖函数，验证请求是否来自站长(userType=0)或管理员(userType=1)
    使用Java后端的验证API进行权限校验
    
    注意：此依赖允许站长(userType=0)和管理员(userType=1)访问，都有完全的管理权限
    """
    # 开始时间
    start_time = time.time()
    
    # 获取客户端IP
    client_ip = request.client.host
    if request.headers.get('X-Forwarded-For'):
        # 使用最左侧的IP（通常是客户端真实IP）
        client_ip = request.headers.get('X-Forwarded-For').split(',')[0].strip()
    
    # 检查是否来自内部服务的请求 - 直接信任
    if client_ip in TRUSTED_INTERNAL_IPS:
        logger.info(f"来自内部服务的请求，直接信任: {client_ip}, 请求: {request.url.path}")
        return True
    
    # 检查是否有Java服务传来的管理员标志
    admin_flag = request.headers.get('X-Admin-Request')
    if admin_flag == 'true':
        logger.info(f"检测到Java服务的管理员标志，直接通过: {request.url.path}")
        return True
    
    # 模型代理API特殊处理 - 允许Java服务访问模型管理API
    if request.url.path.startswith('/api/model/'):
        if client_ip.startswith('172.19.0.'):  # Docker网络内部IP
            logger.info(f"检测到Java服务访问模型管理API，直接通过: {request.url.path}")
            return True
    
    # 检查限流
    if not admin_rate_limiter.is_allowed(client_ip):
        logger.warning(f"IP {client_ip} 请求频率过高被拒绝访问管理员API")
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={
                'code': 429,
                'message': '请求过于频繁，请稍后再试',
                'data': None
            }
        )
        
    # 记录API访问信息
    endpoint = request.url.path
    logger.info(f"管理员API请求: {endpoint}, IP: {client_ip}")
    
    # 从请求中获取token
    token = None
    
    # 从Authorization头获取token
    if credentials:
        token = credentials.credentials
        logger.info(f"从请求头获取到token: {token[:10]}...")
    
    # 也从请求参数中检查token
    if not token:
        token = request.query_params.get('token')
        if token:
            logger.info(f"从请求参数获取到token: {token[:10]}...")
    
    # 从cookie中检查token
    if not token:
        token = request.cookies.get('Admin-Token') or request.cookies.get('User-Token')
        if token:
            logger.info(f"从cookie获取到token: {token[:10]}...")
    
    # 没有token则拒绝访问
    if not token:
        logger.warning(f"未提供有效token, IP: {client_ip}, API: {endpoint}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                'code': 401,
                'message': '未登录或token无效',
                'data': None
            }
        )
        
    try:
        # 处理token - 添加兼容性，确保Java后端也能接受不带Bearer前缀的token
        # 如果token已经有Bearer前缀，直接使用；否则增加Bearer前缀
        java_token = token
        if not java_token.startswith('Bearer '):
            java_token = f"Bearer {token}"
        
        headers = {
            'Authorization': java_token,  # 添加Bearer前缀，确保Java后端能正确识别token
            'X-Forwarded-For': client_ip  # 将客户端IP传递给Java后端
        }
        logger.info(f"正在调用Java后端验证API: {JAVA_AUTH_URL}")
        logger.info(f"请求头: {headers}")
        
        async with httpx.AsyncClient() as client:
            response = await client.get(JAVA_AUTH_URL, headers=headers, timeout=5)
        logger.info(f"Java后端响应状态码: {response.status_code}")
        logger.info(f"Java后端响应头: {dict(response.headers)}")
        
        # 解析响应
        if response.status_code == 200:
            data = response.json()
            logger.info(f"Java后端验证API响应: {data}")
            # 检查Java后端返回的权限验证结果
            if data.get('code') == 200 and data.get('data') is True:
                # 权限验证通过
                elapsed = time.time() - start_time
                logger.info(f"管理员API权限验证通过: {endpoint}, IP: {client_ip}, 耗时: {elapsed:.3f}秒")
                return True
            else:
                logger.warning(f"Java后端权限验证失败: {data.get('message', '未知错误')}")
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail={
                        'code': 403,
                        'message': data.get('message', '权限不足，需要站长或管理员权限'),
                        'data': None
                    }
                )
        elif response.status_code == 401:
            # 处理未授权情况
            logger.warning(f"Java后端返回401未授权, IP: {client_ip}, API: {endpoint}")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={
                    'code': 401,
                    'message': '登录已过期或token无效',
                    'data': None
                }
            )
        else:
            data = response.json() if response.content else {}
            
            # 处理登录过期的情况，允许管理员继续访问
            if data.get('code') == 500 and "登录已过期" in data.get('message', ''):
                logger.warning(f"登录已过期，但允许管理员继续访问: {endpoint}")
                elapsed = time.time() - start_time
                logger.info(f"登录过期情况下管理员API请求完成: {endpoint}, IP: {client_ip}, 耗时: {elapsed:.3f}秒")
                return True
            
        # 权限验证失败
        logger.warning(f"权限验证失败, IP: {client_ip}, API: {endpoint}, 响应码: {response.status_code}")
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={
                'code': 403,
                'message': '权限不足，需要站长或管理员权限',
                'data': None
            }
        )
            
    except HTTPException:
        # 重新抛出HTTPException
        raise
    except Exception as e:
        # 记录异常并返回错误响应
        logger.error(f"验证权限时发生错误: {str(e)}, IP: {client_ip}, API: {endpoint}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={
                'code': 500,
                'message': f'验证权限时发生错误: {str(e)}',
                'data': None
            }
        ) 