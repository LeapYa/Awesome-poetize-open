from fastapi import FastAPI, Request, HTTPException, Depends
import json
import os
from config import BASE_BACKEND_URL, JAVA_CONFIG_URL
import random
import time
import uuid
import traceback
from auth_decorator import admin_required  # 导入管理员权限装饰器
import httpx

path_prefix = os.path.dirname(os.path.abspath(__file__))
ENV = os.environ.get("ENV", "development")

# 数据存储路径
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

# 配置文件路径
EMAIL_CONFIG_FILE = os.path.join(DATA_DIR, 'email_configs.json')
THIRD_LOGIN_CONFIG_PATH = os.path.join(DATA_DIR, "third_login_config.json")

# Java API基础URL，用于获取和更新数据库中的配置
JAVA_API_URL = os.environ.get("JAVA_API_URL", BASE_BACKEND_URL)



# 初始化数据文件
def init_data_files():
    """初始化数据文件，如果文件存在但内容不正确也会修复"""
    # 确保数据目录存在
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)
    
    # 初始化邮箱配置
    if not os.path.exists(EMAIL_CONFIG_FILE):
        with open(EMAIL_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump({"configs": [], "defaultIndex": -1}, f, ensure_ascii=False)
    else:
        # 检查文件内容是否正确
        try:
            with open(EMAIL_CONFIG_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # 如果缺少必要字段，更新文件
                if not all(key in data for key in ["configs", "defaultIndex"]):
                    data = {"configs": data.get("configs", []), "defaultIndex": data.get("defaultIndex", -1)}
                    with open(EMAIL_CONFIG_FILE, 'w', encoding='utf-8') as f:
                        json.dump(data, f, ensure_ascii=False)
        except:
            # 如果文件损坏，重新创建
            with open(EMAIL_CONFIG_FILE, 'w', encoding='utf-8') as f:
                json.dump({"configs": [], "defaultIndex": -1}, f, ensure_ascii=False)
            
    # 初始化第三方登录配置
    if not os.path.exists(THIRD_LOGIN_CONFIG_PATH):
        with open(THIRD_LOGIN_CONFIG_PATH, 'w', encoding='utf-8') as f:
            default_host = "localhost:5000"  # 默认值，后续可以通过配置更新
            json.dump({
                "enable": False,
                "github": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{default_host}/callback/github",
                    "enabled": True
                },
                "google": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{default_host}/callback/google",
                    "enabled": True
                },
                "twitter": {
                    "client_key": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{default_host}/callback/x",
                    "enabled": True
                },
                "yandex": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{default_host}/callback/yandex",
                    "enabled": True
                },
                "gitee": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{default_host}/callback/gitee",
                    "enabled": True
                }
            }, f, ensure_ascii=False)
    else:
        # 检查现有配置文件是否完整，如果不完整则修复
        try:
            with open(THIRD_LOGIN_CONFIG_PATH, 'r', encoding='utf-8') as f:
                config = json.load(f)
            
            # 检查是否缺少gitee配置或enabled字段
            needs_update = False
            default_host = "localhost:5000"
            
            # 确保有gitee配置
            if "gitee" not in config:
                config["gitee"] = {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{default_host}/callback/gitee",
                    "enabled": True
                }
                needs_update = True
            
            # 确保每个平台都有enabled字段
            for platform in ["github", "google", "twitter", "yandex", "gitee"]:
                if platform in config and "enabled" not in config[platform]:
                    config[platform]["enabled"] = True
                    needs_update = True
            
            # 如果需要更新，保存文件
            if needs_update:
                with open(THIRD_LOGIN_CONFIG_PATH, 'w', encoding='utf-8') as f:
                    json.dump(config, f, ensure_ascii=False, indent=2)
                print("已修复第三方登录配置文件")
                
        except Exception as e:
            print(f"修复第三方登录配置文件时出错: {e}")
            # 如果文件损坏，重新创建
            with open(THIRD_LOGIN_CONFIG_PATH, 'w', encoding='utf-8') as f:
                default_host = "localhost:5000"
                json.dump({
                    "enable": False,
                    "github": {
                        "client_id": "",
                        "client_secret": "",
                        "redirect_uri": f"http://{default_host}/callback/github",
                        "enabled": True
                    },
                    "google": {
                        "client_id": "",
                        "client_secret": "",
                        "redirect_uri": f"http://{default_host}/callback/google",
                        "enabled": True
                    },
                    "twitter": {
                        "client_key": "",
                        "client_secret": "",
                        "redirect_uri": f"http://{default_host}/callback/x",
                        "enabled": True
                    },
                    "yandex": {
                        "client_id": "",
                        "client_secret": "",
                        "redirect_uri": f"http://{default_host}/callback/yandex",
                        "enabled": True
                    },
                    "gitee": {
                        "client_id": "",
                        "client_secret": "",
                        "redirect_uri": f"http://{default_host}/callback/gitee",
                        "enabled": True
                    }
                }, f, ensure_ascii=False, indent=2)

# 从Java API读取网站信息
async def get_web_info():
    """从Java API获取网站信息"""
    try:
        # 通过Java API从数据库获取，添加随机参数防止缓存
        cache_breaker = int(time.time())
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{JAVA_API_URL}/webInfo/getWebInfo?_={cache_breaker}",
                headers={
                    'Cache-Control': 'no-cache, no-store, must-revalidate',
                    'Pragma': 'no-cache',
                    'Expires': '0'
                },
                timeout=5
            )
        if response.status_code == 200:
            data = response.json()
            if data.get("code") == 200 and data.get("data"):
                web_info = data.get("data")
                
                # 确保enableWaifu是布尔类型
                if "enableWaifu" in web_info:
                    web_info["enableWaifu"] = bool(web_info["enableWaifu"])
                    print(f"从Java获取的看板娘状态: {web_info['enableWaifu']}")
                
                # 确保navConfig字段存在，如果不存在则设置默认值
                if "navConfig" not in web_info or not web_info["navConfig"] or web_info["navConfig"] == "{}":
                    default_nav = [
                        {"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":True},
                        {"name":"记录","icon":"📒","link":"#","type":"dropdown","order":2,"enabled":True},
                        {"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":True},
                        {"name":"百宝箱","icon":"🧰","link":"/favorite","type":"internal","order":4,"enabled":True},
                        {"name":"留言","icon":"📪","link":"/message","type":"internal","order":5,"enabled":True},
                        {"name":"联系我","icon":"💬","link":"#chat","type":"special","order":6,"enabled":True}
                    ]
                    web_info["navConfig"] = json.dumps(default_nav)
                    print("导航栏配置不存在或为空，使用默认配置")
                
                # 获取第三方登录配置（Python管理的部分）
                third_login_config = get_third_login_config()
                if third_login_config:
                    # 将第三方登录配置保存为JSON字符串
                    web_info["thirdLoginConfig"] = json.dumps(third_login_config)
                
                return web_info
            else:
                print(f"从Java API获取网站信息失败: {data.get('message', '未知错误')}")
                return None
        else:
            print(f"从Java API获取网站信息失败: HTTP {response.status_code}")
            if response.text:
                print(f"响应内容: {response.text}")
            return None
    except Exception as e:
        print(f"读取网站信息出错: {str(e)}")
        traceback.print_exc()
        return None

# 保存网站信息到Java API
async def save_web_info(web_info, request: Request = None):
    """将网站信息保存到数据库"""
    try:
        # 创建一个副本，去除Python专门管理的字段
        java_web_info = dict(web_info)
        
        # 确保字段名转换正确
        if 'enableWaifu' in java_web_info:
            # 确保使用布尔值
            java_web_info['enableWaifu'] = bool(java_web_info['enableWaifu'])
            print(f"准备发送看板娘状态: {java_web_info['enableWaifu']}, 类型: {type(java_web_info['enableWaifu'])}")
        
        # 如果navConfig为空，设置默认值
        if 'navConfig' not in java_web_info or not java_web_info['navConfig'] or java_web_info['navConfig'] == "{}":
            default_nav = [
                {"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":True},
                {"name":"记录","icon":"📒","link":"#","type":"dropdown","order":2,"enabled":True},
                {"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":True},
                {"name":"百宝箱","icon":"🧰","link":"/favorite","type":"internal","order":4,"enabled":True},
                {"name":"留言","icon":"📪","link":"/message","type":"internal","order":5,"enabled":True},
                {"name":"联系我","icon":"💬","link":"#chat","type":"special","order":6,"enabled":True}
            ]
            java_web_info['navConfig'] = json.dumps(default_nav)
            print("保存时导航栏配置为空，使用默认配置")
            
        # 移除Python管理的字段，避免发送给Java API
        if "thirdLoginConfig" in java_web_info:
            java_web_info.pop("thirdLoginConfig")
        
        # 从请求中获取认证token
        auth_token = None
        if request:
            auth_token = request.headers.get('Authorization')
        if not auth_token:
            print("未找到认证token")
            return False
            
        # 通过Java API保存到数据库
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{JAVA_API_URL}/webInfo/updateWebInfo",
                json=java_web_info,
                headers={
                    "Content-Type": "application/json",
                    "Authorization": auth_token  # 传递认证token
                },
                timeout=10
            )
        
        if response.status_code == 200:
            data = response.json()
            if data.get("code") == 200:
                print(f"保存网站信息成功，看板娘状态: {java_web_info.get('enableWaifu', '未设置')}")
                return True
            else:
                print(f"保存网站信息失败: {data.get('message', '未知错误')}")
                return False
        else:
            print(f"保存网站信息失败: HTTP {response.status_code}")
            if response.text:
                print(f"响应内容: {response.text}")
            return False
    except Exception as e:
        print(f"保存网站信息出错: {str(e)}")
        traceback.print_exc()  # 打印完整的错误堆栈
        return False

# 获取第三方登录配置
def get_third_login_config(host: str = "localhost:5000"):
    try:
        if os.path.exists(THIRD_LOGIN_CONFIG_PATH):
            with open(THIRD_LOGIN_CONFIG_PATH, "r", encoding="utf-8") as f:
                config = json.load(f)
                # 确保每个平台配置都有enabled字段
                for platform in ["github", "google", "twitter", "yandex", "gitee"]:
                    if platform in config and "enabled" not in config[platform]:
                        config[platform]["enabled"] = True
                return config
        else:
            # 返回默认配置
            default_config = {
                "enable": False,
                "github": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{host}/callback/github",
                    "enabled": True
                },
                "google": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{host}/callback/google",
                    "enabled": True
                },
                "twitter": {
                    "client_key": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{host}/callback/x",
                    "enabled": True
                },
                "yandex": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{host}/callback/yandex",
                    "enabled": True
                },
                "gitee": {
                    "client_id": "",
                    "client_secret": "",
                    "redirect_uri": f"http://{host}/callback/gitee",
                    "enabled": True
                }
            }
            # 保存默认配置
            save_third_login_config(default_config)
            return default_config
    except Exception as e:
        print(f"获取第三方登录配置失败: {str(e)}")
        return None

# 保存第三方登录配置
def save_third_login_config(config):
    try:
        with open(THIRD_LOGIN_CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(config, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        print(f"保存第三方登录配置失败: {str(e)}")
        return False

# 读取邮箱配置
def get_email_configs():
    """获取邮箱配置"""
    try:
        config_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'email_configs.json')
        if not os.path.exists(config_file):
            return {"configs": [], "defaultIndex": -1}
        
        with open(config_file, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"获取邮箱配置出错: {e}")
        return {"configs": [], "defaultIndex": -1}

def save_email_configs(configs, default_index=None):
    """保存邮箱配置
    
    Args:
        configs: 邮箱配置列表
        default_index: 默认配置索引
    """
    try:
        config_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
        if not os.path.exists(config_dir):
            os.makedirs(config_dir)
        
        config_file = os.path.join(config_dir, 'email_configs.json')
        
        # 读取现有配置以获取当前的默认索引
        current_config = {"configs": [], "defaultIndex": -1}
        if os.path.exists(config_file):
            with open(config_file, 'r', encoding='utf-8') as f:
                current_config = json.load(f)
        
        # 如果未提供默认索引，则使用当前的默认索引
        if default_index is None:
            default_index = current_config.get("defaultIndex", -1)
        
        # 为每个配置添加或更新ID，确保高级配置参数被保存
        for i, config in enumerate(configs):
            # 确保配置有唯一ID
            if not config.get('id'):
                config['id'] = str(uuid.uuid4())
            
            # 确保保存高级配置参数
            config['protocol'] = config.get('protocol', 'SMTP')
            config['connectionTimeout'] = config.get('connectionTimeout', 30)
            config['authMechanism'] = config.get('authMechanism', 'LOGIN')
            
            # 确保代理设置被保存
            if config.get('proxyHost'):
                config['proxyPort'] = config.get('proxyPort', 0)
                config['proxyType'] = config.get('proxyType', 'SOCKS5')
            
            # 确保自定义属性被保存
            if not config.get('customProperties'):
                config['customProperties'] = {}
            
            # 确保标记默认配置
            config['isDefault'] = (i == default_index)
        
        # 保存配置
        config_data = {"configs": configs, "defaultIndex": default_index}
        with open(config_file, 'w', encoding='utf-8') as f:
            json.dump(config_data, f, ensure_ascii=False, indent=2)
        
        # 同步到Java后端
        sync_config('mail_configs', configs)
        
        return True
    except Exception as e:
        print(f"保存邮箱配置出错: {e}")
        return False

# 获取随机邮箱配置
def get_random_email_config():
    """获取随机邮箱配置，用于发送验证码等邮件"""
    email_data = get_email_configs()
    configs = email_data.get("configs", [])
    
    # 过滤出已启用的邮箱配置
    enabled_configs = [config for config in configs if config.get("enabled", False)]
    
    if not enabled_configs:
        # 如果没有启用的配置，尝试使用默认配置
        default_index = email_data.get("defaultIndex", -1)
        if default_index >= 0 and default_index < len(configs):
            print(f"没有启用的邮箱配置，使用默认配置 (索引: {default_index})")
            return configs[default_index]
        return None
    
    # 随机选择一个启用的配置
    selected_config = random.choice(enabled_configs)
    print(f"随机选择邮箱配置: {selected_config.get('username')}")
    return selected_config

# 注册API到FastAPI应用
def register_web_admin_api(app: FastAPI):
    # 确保数据文件存在
    init_data_files()
    
    @app.get('/admin/webInfo/getAdminWebInfo')
    async def get_admin_web_info(request: Request, _: bool = Depends(admin_required)):
        """获取管理员网站基本信息"""
        web_info = await get_web_info()
        return {
            "code": 200,
            "message": "获取成功",
            "data": web_info
        }
    
    @app.get('/admin/webInfo/getAdminWebInfoDetails')
    @app.options('/admin/webInfo/getAdminWebInfoDetails')
    async def get_admin_web_info_details(request: Request, _: bool = Depends(admin_required)):
        """获取管理员网站详细信息"""
        web_info = await get_web_info()
        return {
            "code": 200,
            "message": "获取成功",
            "data": web_info
        }
    
    @app.get('/webInfo/getThirdLoginConfig')
    @app.options('/webInfo/getThirdLoginConfig')
    async def get_third_login_config_api(request: Request):
        """获取第三方登录配置"""
        print("收到获取第三方登录配置请求")
        host = request.headers.get('host', 'localhost:5000')
        config_data = get_third_login_config(host)
        print(f"返回第三方登录配置数据")
        return {
            "code": 200,
            "message": "获取成功",
            "data": config_data
        }
    
    @app.post('/webInfo/updateThirdLoginConfig')
    @app.options('/webInfo/updateThirdLoginConfig')
    async def update_third_login_config_api(request: Request, _: bool = Depends(admin_required)):
        """更新第三方登录配置"""
        try:
            config = await request.json()
            print(f"收到更新第三方登录配置请求")
            
            # 保存配置
            success = save_third_login_config(config)
            
            if success:
                return {
                    "code": 200,
                    "message": "第三方登录配置保存成功",
                    "data": None
                }
            else:
                raise HTTPException(status_code=500, detail={
                    "code": 500,
                    "message": "第三方登录配置保存失败",
                    "data": None
                })
        except Exception as e:
            print(f"保存第三方登录配置出错: {str(e)}")
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"保存第三方登录配置失败: {str(e)}",
                "data": None
            })
    
    @app.get('/webInfo/getEmailConfigs')
    async def get_email_configs_api():
        """获取邮箱配置"""
        print("收到获取邮箱配置请求")
        email_data = get_email_configs()
        print(f"返回邮箱配置数据: {email_data}")
        return {
            "code": 200,
            "message": "获取成功",
            "data": email_data["configs"]
        }
    
    @app.get('/webInfo/getDefaultMailConfig')
    async def get_default_mail_config():
        """获取默认邮箱配置索引"""
        print("收到获取默认邮箱配置索引请求")
        email_data = get_email_configs()
        print(f"返回默认邮箱配置索引: {email_data['defaultIndex']}")
        return {
            "code": 200,
            "message": "获取成功",
            "data": email_data["defaultIndex"]
        }
    
    @app.post('/webInfo/saveEmailConfigs')
    async def save_email_configs_api(request: Request, _: bool = Depends(admin_required)):
        """保存邮箱配置API"""
        try:
            # 获取URL中的默认索引参数
            default_index_str = request.query_params.get('defaultIndex', '-1')
            try:
                default_index = int(default_index_str)
            except ValueError:
                default_index = -1

            # 获取请求数据
            data = await request.json()
            print(f"收到邮箱配置保存请求: 数据类型={type(data).__name__}")
            
            # 兼容两种数据格式：列表格式和{configs:[...]}格式
            if isinstance(data, list):
                configs = data  # 如果直接是列表，直接使用
                print(f"接收到列表格式的邮箱配置数据，配置数量: {len(configs)}")
            elif isinstance(data, dict):
                configs = data.get('configs', [])  # 如果是字典，获取configs字段
                # 如果字典中有defaultIndex，优先使用
                if 'defaultIndex' in data:
                    default_index = data.get('defaultIndex', -1)
                print(f"接收到字典格式的邮箱配置数据，配置数量: {len(configs)}")
            else:
                raise HTTPException(status_code=400, detail={
                    "code": 400,
                    "message": "无效的数据格式",
                    "data": None
                })
            
            # 处理配置中的高级参数
            for config in configs:
                # 确保所有字段都被正确保存
                config['host'] = config.get('host', '')
                config['port'] = config.get('port', 25)
                config['username'] = config.get('username', '')
                config['password'] = config.get('password', '')
                config['nickname'] = config.get('nickname', '诗词站')
                config['ssl'] = config.get('ssl', False)
                
                # 高级配置参数
                config['protocol'] = config.get('protocol', 'SMTP')
                config['connectionTimeout'] = config.get('connectionTimeout', 30)
                config['authMechanism'] = config.get('authMechanism', 'LOGIN')
                
                # 代理设置
                config['proxyHost'] = config.get('proxyHost', '')
                config['proxyPort'] = config.get('proxyPort', 0)
                config['proxyType'] = config.get('proxyType', '')
                
                # 自定义属性
                config['customProperties'] = config.get('customProperties', {})
            
            save_result = save_email_configs(configs, default_index)
            
            if save_result:
                print(f"邮箱配置保存成功，共{len(configs)}个配置，默认索引: {default_index}")
                return {
                    "code": 200,
                    "message": "保存成功",
                    "data": None
                }
            else:
                print(f"邮箱配置保存失败")
                raise HTTPException(status_code=500, detail={
                    "code": 500,
                    "message": "保存失败",
                    "data": None
                })
        except HTTPException:
            raise
        except Exception as e:
            print(f"保存邮箱配置出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"保存失败: {str(e)}",
                "data": None
            })

    @app.get('/webInfo/getWaifuStatus')
    async def get_waifu_status():
        """获取看板娘状态"""
        print("收到获取看板娘状态请求")
        web_info = await get_web_info()
        if web_info:
            enable_waifu = web_info.get('enableWaifu', False)
            print(f"返回看板娘状态: {enable_waifu}")
            return {
                "code": 200,
                "message": "获取成功",
                "data": {
                    "enableWaifu": enable_waifu,
                    "id": web_info.get('id')
                }
            }
        else:
            print("网站信息不存在")
            raise HTTPException(status_code=404, detail={
                "code": 404,
                "message": "网站信息不存在",
                "data": None
            })

    @app.post('/webInfo/updateWaifuStatus')
    @app.options('/webInfo/updateWaifuStatus')
    async def update_waifu_status(request: Request, _: bool = Depends(admin_required)):
        """更新看板娘状态"""
        try:
            data = await request.json()
            print(f"收到更新看板娘状态请求: {data}")
            
            if "enableWaifu" not in data:
                raise HTTPException(status_code=400, detail={
                    "code": 400,
                    "message": "缺少enableWaifu字段",
                    "data": None
                })
                
            web_info = await get_web_info()
            if not web_info:
                raise HTTPException(status_code=404, detail={
                    "code": 404,
                    "message": "网站信息不存在",
                    "data": None
                })
                
            # 仅更新enableWaifu字段
            web_info['enableWaifu'] = bool(data['enableWaifu'])
            print(f"更新看板娘状态为: {web_info['enableWaifu']}")
            
            # 保存到数据库
            success = await save_web_info(web_info, request)
            if success:
                print(f"更新成功，看板娘状态: {web_info['enableWaifu']}")
                return {
                    "code": 200,
                    "message": "更新成功",
                    "data": {
                        "enableWaifu": web_info['enableWaifu'],
                        "id": web_info.get('id')
                    }
                }
            else:
                print(f"保存失败")
                raise HTTPException(status_code=500, detail={
                    "code": 500,
                    "message": "保存失败",
                    "data": None
                })
        except HTTPException:
            raise
        except Exception as e:
            print(f"更新看板娘状态出错: {str(e)}")
            traceback.print_exc()  # 打印完整的错误堆栈
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"更新失败: {str(e)}",
                "data": None
            })

    @app.post('/webInfo/updateNavConfig')
    @app.options('/webInfo/updateNavConfig')
    async def update_nav_config(request: Request, _: bool = Depends(admin_required)):
        """更新导航栏配置"""
        try:
            data = await request.json()
            print(f"收到更新导航栏配置请求: {data}")
            
            if "navConfig" not in data:
                raise HTTPException(status_code=400, detail={
                    "code": 400,
                    "message": "缺少navConfig字段",
                    "data": None
                })
                
            web_info = await get_web_info()
            if not web_info:
                raise HTTPException(status_code=404, detail={
                    "code": 404,
                    "message": "网站信息不存在",
                    "data": None
                })
                
            # 更新navConfig字段
            web_info['navConfig'] = data['navConfig']
            print(f"更新导航栏配置为: {web_info['navConfig']}")
            
            # 保存到数据库
            success = await save_web_info(web_info, request)
            if success:
                print(f"导航栏配置更新成功")
                return {
                    "code": 200,
                    "message": "导航栏配置更新成功",
                    "data": None
                }
            else:
                print(f"导航栏配置保存失败")
                raise HTTPException(status_code=500, detail={
                    "code": 500,
                    "message": "导航栏配置保存失败",
                    "data": None
                })
        except HTTPException:
            raise
        except Exception as e:
            print(f"更新导航栏配置出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"更新导航栏配置失败: {str(e)}",
                "data": None
            })

# 同步配置到Java后端
async def sync_config(config_type, config_data):
    """将配置同步到Java后端
    
    Args:
        config_type: 配置类型，如'mail_configs'
        config_data: 配置数据
    
    Returns:
        成功返回True，失败返回False
    """
    try:
        if not JAVA_CONFIG_URL:
            print("未配置Java后端URL，跳过同步配置")
            return True
        
        # 构造请求URL
        sync_url = f"{JAVA_CONFIG_URL}/api/config/sync/{config_type}"
        print(f"同步配置到Java后端: {sync_url}")
        
        # 发送请求
        async with httpx.AsyncClient() as client:
            response = await client.post(
                sync_url, 
                json=config_data, 
                headers={"Content-Type": "application/json"},
                timeout=10
            )
        
        if response.status_code == 200:
            print(f"配置同步成功: {config_type}")
            return True
        else:
            print(f"配置同步失败: {response.status_code}, {response.text}")
            return False
    except Exception as e:
        print(f"配置同步出错: {str(e)}")
        return False

# 如果直接运行此文件，启动一个测试服务器
if __name__ == '__main__':
    import uvicorn
    from fastapi import FastAPI
    app = FastAPI()
    register_web_admin_api(app)
    
    uvicorn.run(app, host="0.0.0.0", port=5001, debug=True) 