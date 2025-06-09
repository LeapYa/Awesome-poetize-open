import httpx
import os
import secrets
from urllib.parse import parse_qs, urlencode, quote
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests
from oauthlib.oauth1 import Client
from fastapi import FastAPI, Request, HTTPException, Depends
from fastapi.responses import RedirectResponse, JSONResponse
from starlette.middleware.sessions import SessionMiddleware
from config import SECRET_KEY, JAVA_BACKEND_URL, FRONTEND_URL, JAVA_CONFIG_URL, BASE_BACKEND_URL

# 定义Yandex OAuth实现
class YandexOAuth:
    @staticmethod
    def get_session():
        return None  # 简化实现，不需要session对象
        
# 创建Yandex替代模块
yandex = YandexOAuth()

# 修复导入路径
ENV = os.environ.get("ENV", "development")
from web_admin_api import get_third_login_config

# 创建独立运行使用的FastAPI应用
app = FastAPI()
app.add_middleware(SessionMiddleware, secret_key=SECRET_KEY)

# ============================
# 工具函数
# ============================
def generate_state_token():
    """生成防CSRF的随机state"""
    return secrets.token_urlsafe(16)

def get_oauth_config(provider):
    try:
        # 从本地配置文件中获取第三方登录配置
        config = get_third_login_config()
        if not config:
            print(f"无法获取第三方登录配置")
            return None
        
        # 检查第三方登录功能是否启用
        if not config.get('enable', False):
            print(f"第三方登录功能未启用")
            return None
        
        # 获取特定提供商的配置
        provider_config = config.get(provider)
        if not provider_config:
            print(f"未找到{provider}的配置")
            return None
        
        # 检查该平台是否启用
        if not provider_config.get('enabled', True):
            print(f"{provider}平台登录功能未启用")
            return None
        
        # 检查配置是否完整
        if provider == 'twitter':
            if not (provider_config.get('client_key') and provider_config.get('client_secret')):
                print(f"{provider}配置不完整")
                return None
        else:
            if not (provider_config.get('client_id') and provider_config.get('client_secret')):
                print(f"{provider}配置不完整")
                return None
        
        return provider_config
    except Exception as e:
        print(f"获取{provider}配置时出错: {str(e)}")
        return None

# ============================
# 第三方登录配置
# ============================
def get_github_config():
    configs = get_oauth_config("github")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://github.com/login/oauth/authorize",
            "token_url": "https://github.com/login/oauth/access_token",
            "user_info_url": "https://api.github.com/user",
            "emails_url": "https://api.github.com/user/emails",
            "redirect_uri": "",
            "scope": "user:email"
        }
    
    github_config = configs
    return {
        "client_id": github_config.get("client_id", ""),
        "client_secret": github_config.get("client_secret", ""),
        "auth_url": "https://github.com/login/oauth/authorize",
        "token_url": "https://github.com/login/oauth/access_token",
        "user_info_url": "https://api.github.com/user",
        "emails_url": "https://api.github.com/user/emails",
        "redirect_uri": github_config.get("redirect_uri", ""),
        "scope": "user:email"
    }

def get_google_config():
    configs = get_oauth_config("google")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://accounts.google.com/o/oauth2/v2/auth",
            "token_url": "https://oauth2.googleapis.com/token",
            "user_info_url": "https://people.googleapis.com/v1/people/me",
            "redirect_uri": "",
            "scope": "openid email profile"
        }
    
    google_config = configs
    return {
        "client_id": google_config.get("client_id", ""),
        "client_secret": google_config.get("client_secret", ""),
        "auth_url": "https://accounts.google.com/o/oauth2/v2/auth",
        "token_url": "https://oauth2.googleapis.com/token",
        "user_info_url": "https://people.googleapis.com/v1/people/me",
        "redirect_uri": google_config.get("redirect_uri", ""),
        "scope": "openid email profile"
    }

def get_twitter_config():
    configs = get_oauth_config("x")
    if not configs:
        return {
            "client_key": "",
            "client_secret": "",
            "request_token_url": "https://api.twitter.com/oauth/request_token",
            "auth_url": "https://api.twitter.com/oauth/authenticate",
            "access_token_url": "https://api.twitter.com/oauth/access_token",
            "user_info_url": "https://api.twitter.com/1.1/account/verify_credentials.json",
            "redirect_uri": "",
            "include_email": "true"
        }
    
    twitter_config = configs
    return {
        "client_key": twitter_config.get("client_key", ""),
        "client_secret": twitter_config.get("client_secret", ""),
        "request_token_url": "https://api.twitter.com/oauth/request_token",
        "auth_url": "https://api.twitter.com/oauth/authenticate",
        "access_token_url": "https://api.twitter.com/oauth/access_token",
        "user_info_url": "https://api.twitter.com/1.1/account/verify_credentials.json",
        "redirect_uri": twitter_config.get("redirect_uri", ""),
        "include_email": "true"
    }

def get_yandex_config():
    configs = get_oauth_config("yandex")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://oauth.yandex.com/authorize",
            "token_url": "https://oauth.yandex.com/token",
            "user_info_url": "https://login.yandex.ru/info",
            "redirect_uri": "",
            "scope": "login:email login:info"
        }
    
    yandex_config = configs
    return {
        "client_id": yandex_config.get("client_id", ""),
        "client_secret": yandex_config.get("client_secret", ""),
        "auth_url": "https://oauth.yandex.com/authorize",
        "token_url": "https://oauth.yandex.com/token",
        "user_info_url": "https://login.yandex.ru/info",
        "redirect_uri": yandex_config.get("redirect_uri", ""),
        "scope": "login:email login:info"
    }

def get_gitee_config():
    configs = get_oauth_config("gitee")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://gitee.com/oauth/authorize",
            "token_url": "https://gitee.com/oauth/token",
            "user_info_url": "https://gitee.com/api/v5/user",
            "redirect_uri": "",
            "scope": "user_info emails"
        }
    
    gitee_config = configs
    return {
        "client_id": gitee_config.get("client_id", ""),
        "client_secret": gitee_config.get("client_secret", ""),
        "auth_url": "https://gitee.com/oauth/authorize",
        "token_url": "https://gitee.com/oauth/token",
        "user_info_url": "https://gitee.com/api/v5/user",
        "redirect_uri": gitee_config.get("redirect_uri", ""),
        "scope": "user_info emails"
    }

# ============================
# 路由定义
# ============================
async def oauth_login(provider: str, request: Request):
    """统一登录入口"""
    config = None
    if provider == "github":
        config = get_github_config()
    elif provider == "google":
        config = get_google_config()
    elif provider == "x":
        config = get_twitter_config()
    elif provider == "yandex":
        config = get_yandex_config()
    elif provider == "gitee":
        config = get_gitee_config()
    
    if not config:
        return JSONResponse({"error": "Unsupported provider"}, status_code=400)
    
    # 检查配置有效性
    if provider != "x" and (not config.get("client_id") or not config.get("client_secret")):
        return JSONResponse({"error": "未配置OAuth信息，请先在后台设置"}, status_code=400)
    elif provider == "x" and (not config.get("client_key") or not config.get("client_secret")):
        return JSONResponse({"error": "未配置OAuth信息，请先在后台设置"}, status_code=400)

    try:
        # Twitter OAuth 1.0 特殊处理
        if provider == "x":
            client = Client(config["client_key"], config["client_secret"])
            uri, headers, body = client.sign(
                config["request_token_url"],
                http_method="POST",
                callback_uri=config["redirect_uri"]
            )
            async with httpx.AsyncClient() as client:
                response = await client.post(uri, headers=headers, data=body)
            if response.status_code != 200:
                return JSONResponse({"error": "Twitter request token failed"}, status_code=500)

            request_token = parse_qs(response.text)
            oauth_token = request_token.get("oauth_token", [None])[0]
            request.session["x_oauth_token_secret"] = request_token.get("oauth_token_secret", [None])[0]

            auth_url = f"{config['auth_url']}?oauth_token={oauth_token}"
            return RedirectResponse(auth_url)

        # OAuth 2.0 平台处理
        state = generate_state_token()
        request.session[f"{provider}_state"] = state

        auth_params = {
            "client_id": config["client_id"],
            "redirect_uri": config["redirect_uri"],
            "scope": config.get("scope", ""),
            "state": state,
            "response_type": "code"
        }
        if provider == "google":
            auth_params["access_type"] = "offline"  # 获取refresh_token

        auth_url = config["auth_url"] + "?" + urlencode(auth_params)
        return RedirectResponse(auth_url)

    except Exception as e:
        print(f"Login init failed: {str(e)}")
        return JSONResponse({"error": "Service unavailable"}, status_code=500)

async def oauth_callback(provider: str, request: Request):
    """统一回调处理"""
    config = None
    if provider == "github":
        config = get_github_config()
    elif provider == "google":
        config = get_google_config()
    elif provider == "x":
        config = get_twitter_config()
    elif provider == "yandex":
        config = get_yandex_config()
    elif provider == "gitee":
        config = get_gitee_config()
        
    if not config:
        return JSONResponse({"error": "Unsupported provider"}, status_code=400)

    try:
        # Twitter OAuth 1.0 处理
        if provider == "x":
            oauth_token = request.query_params.get("oauth_token")
            oauth_verifier = request.query_params.get("oauth_verifier")
            oauth_token_secret = request.session.get("x_oauth_token_secret")

            if not all([oauth_token, oauth_verifier, oauth_token_secret]):
                return JSONResponse({"error": "Invalid parameters"}, status_code=400)

            client = Client(
                config["client_key"],
                config["client_secret"],
                resource_owner_key=oauth_token,
                resource_owner_secret=oauth_token_secret,
                verifier=oauth_verifier
            )
            uri, headers, body = client.sign(config["access_token_url"], http_method="POST")
            async with httpx.AsyncClient() as client:
                response = await client.post(uri, headers=headers, data=body)
            access_data = parse_qs(response.text)
            access_token = access_data.get("oauth_token", [None])[0]
            access_token_secret = access_data.get("oauth_token_secret", [None])[0]

            # 获取用户信息（带邮箱）
            auth_client = Client(
                config["client_key"],
                config["client_secret"],
                resource_owner_key=access_token,
                resource_owner_secret=access_token_secret
            )
            user_info_url = f"{config['user_info_url']}?include_email=true"
            async with httpx.AsyncClient() as client:
                uri, headers, body = auth_client.sign(user_info_url)
                user_response = await client.get(uri, headers=headers)
            user_info = user_response.json()

            unified_data = {
                "provider": "x",
                "uid": user_info.get("id_str"),
                "username": user_info.get("screen_name"),
                "email": user_info.get("email"),
                "avatar": user_info.get("profile_image_url_https", "").replace("_normal", "")
            }

        # Yandex 处理
        elif provider == "yandex":
            code = request.query_params.get("code")
            state = request.query_params.get("state")
            if not code or state != request.session.get(f"{provider}_state"):
                return JSONResponse({"error": "Invalid state"}, status_code=403)

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    data={
                        "grant_type": "authorization_code",
                        "code": code,
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "redirect_uri": config["redirect_uri"]
                    }
                )
            token_data = token_response.json()
            access_token = token_data.get("access_token")

            async with httpx.AsyncClient() as client:
                user_response = await client.get(
                    config["user_info_url"],
                    params={"format": "json"},
                    headers={"Authorization": f"OAuth {access_token}"}
                )
            user_info = user_response.json()

            unified_data = {
                "provider": "yandex",
                "uid": user_info.get("id"),
                "username": user_info.get("login"),
                "email": user_info.get("default_email"),
                "avatar": f"https://avatars.yandex.net/get-yapic/{user_info.get('default_avatar_id')}/islands-200"
            }

        # GitHub 处理
        elif provider == "github":
            code = request.query_params.get("code")
            state = request.query_params.get("state")
            if not code or state != request.session.get(f"{provider}_state"):
                return JSONResponse({"error": "Invalid state"}, status_code=403)

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    headers={"Accept": "application/json"},
                    data={
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "code": code,
                        "redirect_uri": config["redirect_uri"]
                    }
                )
            access_token = token_response.json().get("access_token")

            async with httpx.AsyncClient() as client:
                user_info_response = await client.get(
                    config["user_info_url"],
                    headers={"Authorization": f"token {access_token}"}
                )
                user_info = user_info_response.json()

                emails_response = await client.get(
                    config["emails_url"],
                    headers={"Authorization": f"token {access_token}"}
                )
                emails = emails_response.json()

            primary_email = next((e["email"] for e in emails if e["primary"] and e["verified"]), None)

            unified_data = {
                "provider": "github",
                "uid": user_info.get("id"),
                "username": user_info.get("login"),
                "email": primary_email,
                "avatar": user_info.get("avatar_url")
            }

        # Google 处理
        elif provider == "google":
            code = request.query_params.get("code")
            state = request.query_params.get("state")
            if not code or state != request.session.get(f"{provider}_state"):
                return JSONResponse({"error": "Invalid state"}, status_code=403)

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    data={
                        "code": code,
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "redirect_uri": config["redirect_uri"],
                        "grant_type": "authorization_code"
                    }
                )
            token_data = token_response.json()
            access_token = token_data.get("access_token")
            id_token_jwt = token_data.get("id_token")

            # 验证ID Token
            id_info = id_token.verify_oauth2_token(
                id_token_jwt,
                google_requests.Request(),
                config["client_id"]
            )
            if id_info['iss'] not in ['accounts.google.com', 'https://accounts.google.com']:
                raise ValueError("Invalid issuer")

            async with httpx.AsyncClient() as client:
                user_response = await client.get(
                    config["user_info_url"],
                    params={"personFields": "names,emailAddresses,photos"},
                    headers={"Authorization": f"Bearer {access_token}"}
                )
            user_data = user_response.json()

            unified_data = {
                "provider": "google",
                "uid": id_info["sub"],
                "username": user_data.get("names", [{}])[0].get("displayName"),
                "email": id_info.get("email"),
                "avatar": user_data.get("photos", [{}])[0].get("url")
            }

        # Gitee 处理
        elif provider == "gitee":
            code = request.query_params.get("code")
            state = request.query_params.get("state")
            if not code or state != request.session.get(f"{provider}_state"):
                return JSONResponse({"error": "Invalid state"}, status_code=403)

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    data={
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "code": code,
                        "grant_type": "authorization_code",
                        "redirect_uri": config["redirect_uri"]
                    }
                )
            token_data = token_response.json()
            access_token = token_data.get("access_token")

            async with httpx.AsyncClient() as client:
                user_response = await client.get(
                    config["user_info_url"],
                    headers={"Authorization": f"token {access_token}"}
                )
            user_info = user_response.json()

            unified_data = {
                "provider": "gitee",
                "uid": user_info.get("id"),
                "username": user_info.get("login"),
                "email": user_info.get("email"),
                "avatar": user_info.get("avatar_url")
            }

        else:
            return JSONResponse({"error": "Unsupported provider"}, status_code=400)

        # 转发到Java后端
        async with httpx.AsyncClient() as client:
            java_response = await client.post(
                f"{JAVA_BACKEND_URL}/oauth/callback",
                json=unified_data,
                timeout=5
            )
        
        # 解析Java响应
        response_data = java_response.json()
        
        # 如果成功登录，重定向到前端站点并携带token
        if java_response.status_code == 200 and response_data.get("code") == 200:
            user_data = response_data.get("data", {})
            access_token = user_data.get("accessToken")
            
            if access_token:
                # 重定向到前端首页，并传递token
                return RedirectResponse(f"{FRONTEND_URL}?token={access_token}")
            
        # 返回原始响应
        return JSONResponse(response_data)

    except httpx.RequestError as e:
        print(f"API请求失败: {str(e)}")
        return JSONResponse({"error": "第三方服务不可用"}, status_code=502)
    except ValueError as e:
        print(f"Token验证失败: {str(e)}")
        return JSONResponse({"error": "认证失败"}, status_code=401)
    except Exception as e:
        print(f"未知错误: {str(e)}")
        return JSONResponse({"error": "服务器内部错误"}, status_code=500)

# 注册第三方登录API到FastAPI应用
def register_third_login_api(app: FastAPI):
    """注册第三方登录相关API"""
    
    @app.get('/login/{provider}')
    async def login_route(provider: str, request: Request):
        return await oauth_login(provider, request)
    
    @app.get('/callback/{provider}')
    async def callback_route(provider: str, request: Request):
        return await oauth_callback(provider, request)
    
    @app.get('/health')
    async def health_check():
        """健康检查接口"""
        return {"status": "ok", "service": "third-party-login-service"}

# 当作为独立模块运行时
if __name__ == '__main__':
    import uvicorn
    from web_admin_api import register_web_admin_api
    
    # 注册网站管理API
    register_web_admin_api(app)
    
    # 注册第三方登录路由
    register_third_login_api(app)
    
    # 启动服务
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("ENV") == "development"
    print(f"启动第三方登录服务，端口: {port}，调试模式: {debug}")
    uvicorn.run(app, host="0.0.0.0", port=port, debug=debug) 