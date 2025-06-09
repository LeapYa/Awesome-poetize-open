from fastapi import FastAPI, Request, HTTPException, Depends
import json
import os
import traceback
from auth_decorator import admin_required  # 导入管理员权限装饰器

# 数据存储路径
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

# 智能验证码配置文件路径
CAPTCHA_CONFIG_FILE = os.path.join(DATA_DIR, 'captcha_config.json')

# 默认智能验证码配置
DEFAULT_CAPTCHA_CONFIG = {
    "enable": False,
    "login": True,
    "register": True,
    "comment": False,
    "reset_password": True,
    "screenSizeThreshold": 768,  # 屏幕宽度阈值，小于此值使用滑动验证码，大于等于此值使用勾选验证码
    "forceSlideForMobile": True, # 在移动设备上强制使用滑动验证码
    "slide": {  # 滑动验证码参数
        "accuracy": 5,  # 精确度
        "successThreshold": 0.95  # 成功阈值，达到最大距离的95%即视为成功
    },
    "checkbox": {  # 勾选验证码参数
        "trackSensitivity": 0.98,  # 轨迹敏感度阈值
        "minTrackPoints": 3  # 最少轨迹点数
    }
}

# 初始化数据文件
def init_data_files():
    """初始化智能验证码配置文件"""
    if not os.path.exists(CAPTCHA_CONFIG_FILE):
        with open(CAPTCHA_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(DEFAULT_CAPTCHA_CONFIG, f, ensure_ascii=False, indent=2)

# 获取智能验证码配置
def get_captcha_config():
    """获取智能验证码配置"""
    try:
        if os.path.exists(CAPTCHA_CONFIG_FILE):
            with open(CAPTCHA_CONFIG_FILE, 'r', encoding='utf-8') as f:
                config = json.load(f)
                # 检查配置是否包含新增字段，如果没有则使用默认值
                if "screenSizeThreshold" not in config:
                    config["screenSizeThreshold"] = DEFAULT_CAPTCHA_CONFIG["screenSizeThreshold"]
                if "forceSlideForMobile" not in config:
                    config["forceSlideForMobile"] = DEFAULT_CAPTCHA_CONFIG["forceSlideForMobile"]
                if "slide" not in config:
                    config["slide"] = DEFAULT_CAPTCHA_CONFIG["slide"]
                if "checkbox" not in config:
                    config["checkbox"] = DEFAULT_CAPTCHA_CONFIG["checkbox"]
                return config
        else:
            # 返回默认配置并保存
            save_captcha_config(DEFAULT_CAPTCHA_CONFIG)
            return DEFAULT_CAPTCHA_CONFIG
    except Exception as e:
        print(f"获取智能验证码配置失败: {str(e)}")
        return DEFAULT_CAPTCHA_CONFIG

# 保存智能验证码配置
def save_captcha_config(config):
    """保存智能验证码配置"""
    try:
        with open(CAPTCHA_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(config, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        print(f"保存智能验证码配置失败: {str(e)}")
        return False

# 生成验证令牌
def generate_verification_token():
    """生成唯一验证令牌"""
    import uuid
    import time
    import hashlib
    
    # 使用uuid、时间戳和随机数生成唯一令牌
    unique_str = f"{uuid.uuid4()}-{time.time()}-{os.urandom(8).hex()}"
    token = hashlib.sha256(unique_str.encode()).hexdigest()
    
    return token

# 注册API到FastAPI应用
def register_captcha_api(app: FastAPI):
    """注册验证码相关API"""
    # 确保数据文件存在
    init_data_files()
    
    @app.get('/webInfo/getCaptchaConfig')
    @app.options('/webInfo/getCaptchaConfig')
    async def get_captcha_config_api(request: Request, _: bool = Depends(admin_required)):
        """获取智能验证码配置API"""
        try:
            print("收到获取智能验证码配置请求")
            config = get_captcha_config()
            print(f"返回智能验证码配置: {config}")
            return {
                "code": 200,
                "message": "获取成功",
                "data": config
            }
        except Exception as e:
            print(f"获取智能验证码配置出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"获取失败: {str(e)}",
                "data": None
            })
    
    @app.post('/webInfo/updateCaptchaConfig')
    async def update_captcha_config_api(request: Request, _: bool = Depends(admin_required)):
        """更新智能验证码配置API"""
        try:
            config = await request.json()
            print(f"收到更新智能验证码配置请求: {config}")
            
            # 确保配置中包含所有必要字段
            for key in DEFAULT_CAPTCHA_CONFIG:
                if key not in config:
                    config[key] = DEFAULT_CAPTCHA_CONFIG[key]
            
            # 保存配置
            success = save_captcha_config(config)
            
            if success:
                return {
                    "code": 200,
                    "message": "智能验证码配置保存成功",
                    "data": None
                }
            else:
                raise HTTPException(status_code=500, detail={
                    "code": 500,
                    "message": "智能验证码配置保存失败",
                    "data": None
                })
        except HTTPException:
            raise
        except Exception as e:
            print(f"保存智能验证码配置出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"保存失败: {str(e)}",
                "data": None
            })
    
    @app.get('/captcha/validate')
    async def is_captcha_required(request: Request):
        """检查特定操作是否需要验证码"""
        try:
            # 获取操作类型参数
            action = request.query_params.get('action', 'login')
            
            # 获取智能验证码配置
            config = get_captcha_config()
            
            # 检查是否启用验证码
            if not config.get('enable', False):
                print(f"验证码全局禁用，操作({action})不需要验证码")
                return {
                    "code": 200,
                    "message": "获取成功",
                    "data": {"required": False}
                }
            
            # 检查特定操作是否需要验证码
            required = config.get(action, False)
            print(f"验证码检查 - 操作: {action}, 需要验证: {required}")
            
            return {
                "code": 200,
                "message": "获取成功",
                "data": {"required": required}
            }
        except Exception as e:
            print(f"检查验证码需求出错: {str(e)}")
            # 出错时默认不需要验证码，确保用户可以正常操作
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"检查失败: {str(e)}",
                "data": {"required": False}
            })
    
    @app.post('/captcha/verify-checkbox')
    async def verify_checkbox_captcha(request: Request):
        """验证复选框验证码"""
        try:
            data = await request.json()
            
            # 获取验证数据
            mouse_track = data.get('mouseTrack', [])
            straight_ratio = data.get('straightRatio', 1.0)
            client_ip = request.client.host
            user_agent = request.headers.get('User-Agent', '')
            
            # 获取验证码配置
            config = get_captcha_config()
            checkbox_config = config.get('checkbox', DEFAULT_CAPTCHA_CONFIG['checkbox'])
            
            # 验证数据分析
            is_valid = True
            
            # 1. 轨迹点数量检查
            min_track_points = checkbox_config.get('minTrackPoints', 3)
            if len(mouse_track) < min_track_points:
                is_valid = False
                
            # 2. 直线率检查
            track_sensitivity = checkbox_config.get('trackSensitivity', 0.98)
            if straight_ratio > track_sensitivity:
                is_valid = False
            
            # 3. IP频率限制检查（防止短时间内多次验证）
            # 这里可以实现更复杂的IP验证逻辑
            
            # 记录验证结果（可以存入数据库或内存缓存）
            verification_token = ""
            if is_valid:
                # 生成唯一验证令牌
                verification_token = generate_verification_token()
                
                # 记录成功验证（可以存入Redis或简单的内存字典）
                # 这里简化处理，实际应用中应该使用更持久的存储
                
            # 返回验证结果
            return {
                "code": 200,
                "message": "验证完成",
                "data": {
                    "success": is_valid,
                    "token": verification_token if is_valid else ""
                }
            }
        except Exception as e:
            print(f"复选框验证出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"验证失败: {str(e)}",
                "data": {"success": False}
            })
            
    # 验证令牌有效性检查接口
    @app.post('/captcha/verify-token')
    async def verify_token(request: Request):
        """验证令牌有效性"""
        try:
            data = await request.json()
            token = data.get('token', '')
            
            # 这里应该检查令牌是否存在且有效
            # 简化处理，只要有令牌就认为有效
            is_valid = bool(token)
            
            return {
                "code": 200,
                "message": "验证完成",
                "data": {"valid": is_valid}
            }
        except Exception as e:
            print(f"验证令牌出错: {str(e)}")
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"验证失败: {str(e)}",
                "data": {"valid": False}
            })

    @app.get('/captcha/getConfig')
    async def get_public_captcha_config():
        """获取面向普通用户的验证码配置（不需要权限）"""
        try:
            config = get_captcha_config()
            
            # 只返回前端验证组件必要的配置信息
            public_config = {
                "enable": config.get('enable', False),
                "screenSizeThreshold": config.get('screenSizeThreshold', 768),
                "forceSlideForMobile": config.get('forceSlideForMobile', True),
                "slide": config.get('slide', DEFAULT_CAPTCHA_CONFIG['slide']),
                "checkbox": config.get('checkbox', DEFAULT_CAPTCHA_CONFIG['checkbox'])
            }
            
            return {
                "code": 200,
                "message": "获取成功",
                "data": public_config
            }
        except Exception as e:
            print(f"获取公共验证码配置出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"获取失败: {str(e)}",
                "data": None
            })

# 如果直接运行此文件，启动一个测试服务器
if __name__ == '__main__':
    import uvicorn
    app = FastAPI()
    register_captcha_api(app)
    
    uvicorn.run(app, host="0.0.0.0", port=5002, debug=True) 