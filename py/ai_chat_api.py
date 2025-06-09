"""
POETIZE博客系统 - AI聊天配置模块 (FastAPI版本)

主要功能：
- AI聊天模型配置管理
- API密钥安全存储
- 聊天参数设置
- 外观和高级配置

版本: 1.0.0 (FastAPI)
"""

import os
import json
import httpx
import asyncio
import time
import jwt
import subprocess
import uuid
import sys
import platform
import threading
import queue
import re
import logging
from fastapi import FastAPI, Request, HTTPException, Depends
from fastapi.responses import JSONResponse, StreamingResponse
from config import JAVA_BACKEND_URL, FRONTEND_URL
from cryptography.fernet import Fernet
from datetime import datetime
from auth_decorator import admin_required
from typing import Dict, List, Optional, Any
from fastmcp import FastMCP
from openai import AsyncOpenAI

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('ai_chat_api')

logger.info("FastMCP库已加载")

# 数据存储路径
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

# AI聊天配置文件路径
AI_CHAT_CONFIG_FILE = os.path.join(DATA_DIR, 'ai_chat_config.json')

# 加密密钥
def get_encryption_key():
    """获取用于数据加密的密钥"""
    key_file = os.path.join(DATA_DIR, 'encryption_key.key')
    if os.path.exists(key_file):
        with open(key_file, 'rb') as f:
            return f.read()
    else:
        key = Fernet.generate_key()
        with open(key_file, 'wb') as f:
            f.write(key)
        return key

# 数据加密和解密
def encrypt_data(data):
    """加密数据"""
    try:
        key = get_encryption_key()
        cipher_suite = Fernet(key)
        encrypted_data = cipher_suite.encrypt(data.encode())
        return encrypted_data.decode()
    except Exception as e:
        logger.error(f"数据加密失败: {str(e)}")
        return None

def decrypt_data(encrypted_data):
    """解密数据"""
    try:
        key = get_encryption_key()
        cipher_suite = Fernet(key)
        decrypted_data = cipher_suite.decrypt(encrypted_data.encode())
        return decrypted_data.decode()
    except Exception as e:
        logger.error(f"数据解密失败: {str(e)}")
        return None

# 默认AI聊天配置
DEFAULT_AI_CHAT_CONFIG = {
    # 基础配置
    "enabled": False,
    "provider": "openai",
    "api_key": "",
    "api_base": "",
    "model": "gpt-3.5-turbo",
    
    # 聊天参数
    "temperature": 0.7,
    "max_tokens": 1000,
    "top_p": 1.0,
    "frequency_penalty": 0.0,
    "presence_penalty": 0.0,
    
    # 外观设置
    "chat_name": "AI助手",
    "chat_avatar": "",
    "welcome_message": "你好！我是你的AI助手，有什么可以帮助你的吗？",
    "placeholder_text": "输入你想说的话...",
    "theme_color": "#4facfe",
    
    # 聊天功能设置
    "max_conversation_length": 20,
    "enable_context": True,
    "enable_typing_indicator": True,
    "response_delay": 1000,
    "enable_quick_actions": True,
    "enable_chat_history": True,
    "enable_streaming": False,  # 是否启用流式响应
    "rate_limit": 20,  # 每分钟最多消息数
    "max_message_length": 500,  # 单条消息最大长度
    "require_login": True,  # 是否需要登录
    "enable_content_filter": True,  # 是否启用内容过滤
    
    # 工具设置
    "enable_web_tools": True,
    "enable_webpage_content": True,
    "enable_web_search": True,
    "web_tools_auto_call": True,  # 是否自动调用工具
    
    # 自定义设置
    "custom_instructions": "",
    "enable_thinking": False,
    
    # 时间戳
    "created_at": datetime.now().isoformat(),
    "updated_at": datetime.now().isoformat(),
    
    # MCP工具设置
    "enable_mcp_tools": True,
    "enable_builtin_servers": True,
    "enable_external_servers": False,  # 是否允许外部MCP服务器
    "mcp_tools_auto_call": True,  # 是否自动调用MCP工具
    "mcp_server_timeout": 30,  # MCP服务器响应超时时间（秒）
    
    # 向后兼容的设置（保留旧配置名）
    "enable_web_tools": True,
    "enable_webpage_content": True,
    "enable_web_search": True,
    "web_tools_auto_call": True,  # 是否自动调用工具
}

def get_ai_chat_config():
    """获取AI聊天配置"""
    try:
        if not os.path.exists(AI_CHAT_CONFIG_FILE):
            logger.info("AI聊天配置文件不存在，使用默认配置")
            return DEFAULT_AI_CHAT_CONFIG.copy(), DEFAULT_AI_CHAT_CONFIG.copy()
            
        with open(AI_CHAT_CONFIG_FILE, 'r', encoding='utf-8') as f:
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
        
        # 创建用于前端显示的配置（隐藏敏感信息）
        display_config = encrypted_config.copy()
        if 'api_key' in display_config and display_config['api_key']:
            api_key = decrypted_config.get('api_key', '')
            if len(api_key) > 8:
                display_config['api_key'] = api_key[:4] + '*' * (len(api_key) - 8) + api_key[-4:]
            else:
                display_config['api_key'] = '*' * len(api_key)
        
        # 添加配置状态信息
        display_config['configured'] = bool(
            decrypted_config.get('provider') and 
            decrypted_config.get('api_key') and 
            decrypted_config.get('model')
        )
        
        return decrypted_config, display_config
        
    except Exception as e:
        logger.error(f"获取AI聊天配置出错: {str(e)}")
        return None, None

def save_ai_chat_config(config):
    """保存AI聊天配置"""
    try:
        # 处理并验证配置
        processed_config = config.copy()
        
        # 处理API密钥
        if 'api_key' in config and config['api_key']:
            # 前端发送了新的API密钥，加密保存
            processed_config['api_key'] = encrypt_data(config['api_key'])
            logger.info("收到新的API密钥，已加密保存")
        elif 'api_key' not in config:
            logger.info("未提供API密钥字段，保持原有密钥不变")
            # 直接从文件中读取加密的配置，而不是解密后的配置
            if os.path.exists(AI_CHAT_CONFIG_FILE):
                with open(AI_CHAT_CONFIG_FILE, 'r', encoding='utf-8') as f:
                    existing_encrypted_config = json.load(f)
                if 'api_key' in existing_encrypted_config and existing_encrypted_config['api_key']:
                    # 保持原有的加密密钥
                    processed_config['api_key'] = existing_encrypted_config['api_key']
                    logger.info("保持原有加密密钥不变")
                else:
                    logger.error("无现有API密钥可用")
                    return False
            else:
                logger.error("配置文件不存在，无法保持原有密钥")
                return False
        else:
            logger.error("API密钥不能为空")
            return False
        
        # 更新时间戳
        processed_config['updated_at'] = datetime.now().isoformat()
        if 'created_at' not in processed_config:
            processed_config['created_at'] = datetime.now().isoformat()
        
        # 验证必填字段
        required_fields = ['provider', 'model']
        for field in required_fields:
            if field not in processed_config or not processed_config[field]:
                logger.error(f"缺少必填字段: {field}")
                return False
        
        # 保存到文件
        with open(AI_CHAT_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(processed_config, f, ensure_ascii=False, indent=2)
        
        logger.info("AI聊天配置保存成功")
        return True
        
    except Exception as e:
        logger.error(f"保存AI聊天配置出错: {str(e)}")
        return False

async def test_ai_chat_connection(config):
    """测试AI聊天API连接"""
    try:
        provider = config.get('provider', 'openai')
        api_key = config.get('api_key', '')
        model = config.get('model', '')
        enable_thinking = config.get('enable_thinking', False)
        
        if not api_key:
            return False, "API密钥不能为空"
        
        if provider == 'openai' or provider == 'custom':
            # OpenAI 和自定义API都使用OpenAI兼容格式
            api_base = config.get('api_base', 'https://api.openai.com/v1')
            if not api_base.endswith('/'):
                api_base += '/'
            api_url = api_base + 'chat/completions'
            
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            
            # 针对ModelScope API的特殊处理
            is_modelscope = 'modelscope' in api_base.lower()
            if is_modelscope:
                headers = {
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                    "User-Agent": "ModelScope-Python-SDK"
                }
                # ModelScope支持基本参数，包括max_tokens
                payload = {
                    "model": model,
                    "messages": [{"role": "user", "content": "Hello"}],
                    "max_tokens": 10,
                    "stream": False
                }
            else:
                payload = {
                    "model": model,
                    "messages": [{"role": "user", "content": "Hello"}],
                    "max_tokens": 10,
                    "stream": False
                }
                
                # 只有非ModelScope API才添加思考模式
                if enable_thinking:
                    payload["enable_thinking"] = True
        elif provider == 'anthropic':
            api_url = "https://api.anthropic.com/v1/messages"
            headers = {
                "x-api-key": api_key,
                "Content-Type": "application/json",
                "anthropic-version": "2023-06-01"
            }
            payload = {
                "model": model,
                "max_tokens": 10,
                "messages": [{"role": "user", "content": "Hello"}]
            }
            
            # Anthropic也支持思考模式
            if enable_thinking:
                payload["enable_thinking"] = True
        elif provider == 'deepseek':
            # DeepSeek 使用 OpenAI 兼容格式
            api_base = config.get('api_base', 'https://api.deepseek.com/v1')
            if not api_base.endswith('/'):
                api_base += '/'
            api_url = api_base + 'chat/completions'
            
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            payload = {
                "model": model,
                "messages": [{"role": "user", "content": "Hello"}],
                "max_tokens": 10,
                "stream": False
            }
            
            if enable_thinking:
                payload["enable_thinking"] = True
        else:
            # 对于其他提供商，也使用OpenAI兼容格式作为默认
            api_base = config.get('api_base', 'https://api.openai.com/v1')
            if not api_base.endswith('/'):
                api_base += '/'
            api_url = api_base + 'chat/completions'
            
            # 检查是否是ModelScope
            is_modelscope = 'modelscope' in api_base.lower()
            
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            
            if is_modelscope:
                headers["User-Agent"] = "ModelScope-Python-SDK"
                payload = {
                    "model": model,
                    "messages": [{"role": "user", "content": "Hello"}],
                    "max_tokens": 10,
                    "stream": False
                }
            else:
                payload = {
                    "model": model,
                    "messages": [{"role": "user", "content": "Hello"}],
                    "max_tokens": 10,
                    "stream": False
                }
                
                if enable_thinking:
                    payload["enable_thinking"] = True
        
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.post(api_url, headers=headers, json=payload)
            
        if response.status_code == 200:
            return True, "连接测试成功"
        elif response.status_code == 401:
            if 'modelscope' in api_url.lower():
                return False, "ModelScope API密钥无效，请检查Token是否正确。获取地址：https://modelscope.cn/my/myaccesstoken"
            else:
                return False, "API密钥无效或权限不足"
        elif response.status_code == 404:
            return False, "API端点不存在，请检查API base URL"
        elif response.status_code == 429:
            return False, "API调用频率限制，请稍后重试"
        else:
            try:
                error_data = response.json()
                error_message = error_data.get('error', {}).get('message', f'HTTP {response.status_code}')
                # 特殊处理ModelScope错误
                if 'modelscope' in api_url.lower():
                    if 'errors' in error_data:
                        error_message = error_data.get('errors', {}).get('message', error_message)
                    elif 'message' in error_data:
                        error_message = error_data.get('message', error_message)
                    
                    # 添加ModelScope特殊错误信息
                    if 'bad request' in error_message.lower():
                        error_message += "。ModelScope API可能不支持某些参数，已自动简化请求参数。"
                        
                return False, f"连接测试失败: {error_message}"
            except:
                error_text = response.text[:200] if response.text else "无详细错误信息"
                return False, f"连接测试失败: HTTP {response.status_code} - {error_text}"
            
    except httpx.TimeoutException:
        return False, "连接超时，请检查网络或API端点"
    except Exception as e:
        return False, f"连接测试失败: {str(e)}"

def register_ai_chat_api(app: FastAPI):
    """注册AI聊天配置API路由"""
    
    # 引用get_ai_chat_config作为get_ai_config
    get_ai_config = get_ai_chat_config
    
    # 获取AI聊天配置
    @app.get('/python/ai/chat/getConfig')
    async def get_ai_chat_config_route(request: Request, _: bool = Depends(admin_required)):
        try:
            decrypted_config, display_config = get_ai_chat_config()
            
            if display_config:
                return JSONResponse({
                    "flag": True,
                    "code": 200,
                    "message": "获取AI聊天配置成功",
                    "data": display_config
                })
            else:
                # 返回默认配置
                default_config = DEFAULT_AI_CHAT_CONFIG.copy()
                default_config['configured'] = False
                return JSONResponse({
                    "flag": True,
                    "code": 200,
                    "message": "AI聊天未配置，返回默认配置",
                    "data": default_config
                })
        except Exception as e:
            logger.error(f"获取AI聊天配置出错: {str(e)}")
            return JSONResponse({
                "flag": False,
                "code": 500,
                "message": f"获取AI聊天配置出错: {str(e)}",
                "data": None
            })
    
    # 保存AI聊天配置
    @app.post('/python/ai/chat/saveConfig')
    async def save_ai_chat_config_route(request: Request, _: bool = Depends(admin_required)):
        try:
            config = await request.json()
            if not config:
                return JSONResponse({
                    "flag": False,
                    "code": 400,
                    "message": "参数错误",
                    "data": None
                })
            
            # 验证必填字段
            required_fields = ['provider', 'model']  # 移除api_key，因为前端可能不发送隐藏的key
            for field in required_fields:
                if field not in config or not config[field]:
                    return JSONResponse({
                        "flag": False,
                        "code": 400,
                        "message": f"缺少必填字段: {field}",
                        "data": None
                    })
            
            # 单独处理api_key验证 - 如果提供了api_key但为空，则报错
            if 'api_key' in config and not config['api_key']:
                return JSONResponse({
                    "flag": False,
                    "code": 400,
                    "message": "API密钥不能为空",
                    "data": None
                })
            
            # 保存配置
            if save_ai_chat_config(config):
                # 获取不含敏感信息的配置用于返回
                _, config_for_display = get_ai_chat_config()
                return JSONResponse({
                    "flag": True,
                    "code": 200,
                    "message": "保存AI聊天配置成功",
                    "data": config_for_display
                })
            else:
                return JSONResponse({
                    "flag": False,
                    "code": 500,
                    "message": "保存AI聊天配置失败",
                    "data": None
                })
        except Exception as e:
            logger.error(f"保存AI聊天配置出错: {str(e)}")
            return JSONResponse({
                "flag": False,
                "code": 500,
                "message": f"保存AI聊天配置出错: {str(e)}",
                "data": None
            })
    
    # 测试AI聊天连接
    @app.post('/python/ai/chat/testConnection')
    async def test_ai_chat_connection_route(request: Request, _: bool = Depends(admin_required)):
        try:
            config = await request.json()
            if not config:
                return JSONResponse({
                    "flag": False,
                    "code": 400,
                    "message": "参数错误",
                    "data": None
                })
            
            # 检查是否使用保存的配置
            if config.get('use_saved_config', False):
                # 使用已保存的配置进行测试
                saved_config, _ = get_ai_chat_config()
                if not saved_config:
                    return JSONResponse({
                        "flag": False,
                        "code": 400,
                        "message": "未找到已保存的配置，请先保存配置",
                        "data": None
                    })
                
                # 使用保存的配置，但可能需要更新模型等信息
                test_config = saved_config.copy()
                if config.get('model'):
                    test_config['model'] = config['model']
                if config.get('api_base'):
                    test_config['api_base'] = config['api_base']
                if config.get('provider'):
                    test_config['provider'] = config['provider']
                
                success, message = await test_ai_chat_connection(test_config)
            else:
                # 使用前端传来的配置进行测试
                success, message = await test_ai_chat_connection(config)
            
            return JSONResponse({
                "flag": success,
                "code": 200 if success else 500,
                "message": message,
                "data": {"success": success}
            })
        except Exception as e:
            logger.error(f"测试AI聊天连接出错: {str(e)}")
            return JSONResponse({
                "flag": False,
                "code": 500,
                "message": f"测试连接出错: {str(e)}",
                "data": {"success": False}
            })
    
    # 检查AI聊天配置状态
    @app.get('/python/ai/chat/checkStatus')
    async def check_ai_chat_status_route(request: Request, _: bool = Depends(admin_required)):
        try:
            decrypted_config, display_config = get_ai_chat_config()
            
            if decrypted_config and display_config:
                # 检查必要字段是否存在
                configured = bool(
                    decrypted_config.get('provider') and 
                    decrypted_config.get('api_key') and 
                    decrypted_config.get('model')
                )
                
                return JSONResponse({
                    "code": 200,
                    "message": "获取AI聊天状态成功",
                    "data": {
                        "configured": configured,
                        "enabled": decrypted_config.get('enabled', False),
                        "provider": decrypted_config.get('provider'),
                        "model": decrypted_config.get('model'),
                        "updated_at": display_config.get('updated_at')
                    }
                })
            else:
                return JSONResponse({
                    "code": 200,
                    "message": "AI聊天未配置",
                    "data": {
                        "configured": False,
                        "enabled": False
                    }
                })
        except Exception as e:
            logger.error(f"检查AI聊天状态出错: {str(e)}")
            return JSONResponse({
                "code": 500,
                "message": f"检查AI聊天状态出错: {str(e)}",
                "data": {"configured": False, "enabled": False}
            })
    
    # 启用/禁用AI聊天
    @app.post('/python/ai/chat/toggleStatus')
    async def toggle_ai_chat_status_route(request: Request, _: bool = Depends(admin_required)):
        """切换AI聊天服务状态"""
        try:
            decrypted_config, _ = get_ai_chat_config()
            if not decrypted_config:
                return JSONResponse(
                    status_code=500,
                    content={"error": "获取配置失败"}
                )
            
            # 切换启用状态
            decrypted_config['enabled'] = not decrypted_config.get('enabled', False)
            
            if save_ai_chat_config(decrypted_config):
                return JSONResponse(content={
                    "success": True,
                    "enabled": decrypted_config['enabled'],
                    "message": f"AI聊天服务已{'启用' if decrypted_config['enabled'] else '禁用'}"
                })
            else:
                return JSONResponse(
                    status_code=500,
                    content={"error": "保存配置失败"}
                )
        except Exception as e:
            logger.error(f"切换AI聊天状态出错: {str(e)}")
            return JSONResponse(
                status_code=500,
                content={"error": "切换状态失败"}
            )

    @app.get('/python/ai/chat/getStreamingConfig')
    async def get_streaming_config_route(request: Request):
        """获取流式响应配置（不需要管理员权限）"""
        try:
            decrypted_config, _ = get_ai_chat_config()
            if not decrypted_config:
                return JSONResponse(content={
                    "enabled": False,
                    "streaming_enabled": False,
                    "configured": False
                })
            
            return JSONResponse(content={
                "enabled": decrypted_config.get('enabled', False),
                "streaming_enabled": decrypted_config.get('enable_streaming', False),
                "configured": bool(
                    decrypted_config.get('provider') and 
                    decrypted_config.get('api_key') and 
                    decrypted_config.get('model')
                )
            })
        except Exception as e:
            logger.error(f"获取流式响应配置出错: {str(e)}")
            return JSONResponse(content={
                "enabled": False,
                "streaming_enabled": False,
                "configured": False
            })

    @app.post("/python/ai/chat/sendMessage")
    async def send_chat_message(request: Request):
        """发送聊天消息到AI"""
        try:
            # 获取请求数据
            data = await request.json()
            message = data.get('message', '').strip()
            conversation_id = data.get('conversationId', 'default')
            chat_history = data.get('history', [])  # 接收聊天历史
            user_id = data.get('user_id', 'anonymous')  # 获取用户ID
            
            if not message:
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": "消息内容不能为空", "data": None}
                )
            
            # 获取AI配置
            config, config_data = get_ai_chat_config()
            
            if not config:
                return JSONResponse(
                    status_code=500,
                    content={"flag": False, "code": 500, "message": "获取AI配置失败", "data": None}
                )
            
            # 调试日志
            logger.info(f"获取到的AI配置: provider={config.get('provider')}, api_base={config.get('api_base')}, model={config.get('model')}")
            
            if not config.get('enabled', False):
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": "AI聊天功能未启用，请先在配置中启用", "data": None}
                )
            
            # 检查频率限制
            rate_limit = config.get('rate_limit', 20)
            if not check_rate_limit(user_id, rate_limit):
                return JSONResponse(
                    status_code=429,
                    content={"flag": False, "code": 429, "message": f"发送消息过于频繁，每分钟最多 {rate_limit} 条消息", "data": None}
                )
            
            # 验证消息内容和长度
            max_length = config.get('max_message_length', 500)
            enable_filter = config.get('enable_content_filter', True)
            
            validation_valid, validation_reason = validate_message_content(message, max_length, enable_filter)
            if not validation_valid:
                logger.warning(f"用户 {user_id} 消息验证失败: {validation_reason}")
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": validation_reason, "data": None}
                )
            
            # 检查必要的配置
            if not config.get('apiKey') and not config.get('api_key'):
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": "API密钥未配置，请先在后台配置", "data": None}
                )
            
            # 限制对话历史长度
            max_conversation_length = config.get('max_conversation_length', 10)
            if len(chat_history) > max_conversation_length:
                chat_history = chat_history[-max_conversation_length:]
            
            # 调用AI API获取回复（包含聊天历史）
            ai_response = await get_ai_response(config, message, chat_history)
            
            return JSONResponse(
                status_code=200,
                content={
                    "flag": True,
                    "code": 200, 
                    "message": "success", 
                    "data": {
                        "response": ai_response,
                        "conversationId": conversation_id,
                        "timestamp": datetime.now().isoformat()
                    }
                }
            )
            
        except Exception as e:
            logger.error(f"AI聊天失败: {str(e)}")
            return JSONResponse(
                status_code=500,
                content={"flag": False, "code": 500, "message": f"AI聊天失败: {str(e)}", "data": None}
            )

    @app.post("/python/ai/chat/sendMessageStream")
    async def send_chat_message_stream(request: Request):
        """发送聊天消息到AI（流式响应）"""
        try:
            # 获取请求数据
            data = await request.json()
            message = data.get('message', '').strip()
            conversation_id = data.get('conversationId', 'default')
            chat_history = data.get('history', [])  # 接收聊天历史
            user_id = data.get('user_id', 'anonymous')  # 获取用户ID
            
            if not message:
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": "消息内容不能为空", "data": None}
                )
            
            # 获取AI配置
            config, config_data = get_ai_chat_config()
            
            if not config:
                return JSONResponse(
                    status_code=500,
                    content={"flag": False, "code": 500, "message": "获取AI配置失败", "data": None}
                )
            
            if not config.get('enabled', False):
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": "AI聊天功能未启用，请先在配置中启用", "data": None}
                )
            
            # 检查必要的配置
            if not config.get('apiKey') and not config.get('api_key'):
                return JSONResponse(
                    status_code=400,
                    content={"flag": False, "code": 400, "message": "API密钥未配置，请先在后台配置", "data": None}
                )
            
            # 启用流式输出
            config['stream'] = True
            
            # 创建SSE响应
            async def event_generator():
                try:
                    # 发送开始事件
                    yield f"data: {json.dumps({'event': 'start', 'conversationId': conversation_id})}\n\n"
                    
                    # 流式获取AI回复
                    async for chunk in get_ai_response_stream(config, message, chat_history):
                        # 处理工具调用特殊事件
                        if chunk.get('event') == 'tool_call':
                            # 工具调用开始
                            yield f"data: {json.dumps({'event': 'tool_call', 'data': chunk['data']})}\n\n"
                        elif chunk.get('event') == 'tool_result':
                            # 工具调用结果
                            yield f"data: {json.dumps({'event': 'tool_result', 'data': chunk['data']})}\n\n"
                        else:
                            # 普通文本块
                            yield f"data: {json.dumps({'event': 'message', 'content': chunk.get('content', ''), 'conversationId': conversation_id})}\n\n"
                    
                    # 发送完成事件
                    yield f"data: {json.dumps({'event': 'end', 'conversationId': conversation_id})}\n\n"
                except Exception as e:
                    logger.error(f"流式输出错误: {str(e)}")
                    yield f"data: {json.dumps({'event': 'error', 'message': str(e)})}\n\n"
            
            return StreamingResponse(event_generator(), media_type="text/event-stream")
            
        except Exception as e:
            logger.error(f"AI聊天流式输出失败: {str(e)}")
            return JSONResponse(
                status_code=500,
                content={"flag": False, "code": 500, "message": f"AI聊天失败: {str(e)}", "data": None}
            )

    @app.get("/python/ai/chat/sendStreamMessage")
    async def send_stream_message(request: Request):
        """发送聊天消息并获取流式响应"""
        try:
            # 从查询参数获取数据
            message = request.query_params.get('message', '')
            conversation_id = request.query_params.get('conversationId', '')
            history_param = request.query_params.get('history', '[]')
            context_param = request.query_params.get('context', '{}')
            user_id = request.query_params.get('userId', 'anonymous')
            
            # 解析历史记录和上下文
            try:
                chat_history = json.loads(history_param) if history_param else []
            except json.JSONDecodeError:
                chat_history = []
                
            try:
                context = json.loads(context_param) if context_param else {}
            except json.JSONDecodeError:
                context = {}
            
            # 将用户ID添加到context中
            context['user_id'] = user_id
            
            # 检查必要参数
            if not message:
                return StreamingResponse(stream_error_response("消息内容不能为空"), media_type="text/event-stream")
            
            if not conversation_id:
                # 生成新的会话ID
                conversation_id = f"conv_{int(time.time() * 1000)}"
            
            # 检查AI配置
            config, _ = get_ai_chat_config()
            if not config or not config.get('api_key'):
                return StreamingResponse(stream_error_response("未配置AI API密钥"), media_type="text/event-stream")
            
            # 添加调试日志
            logger.info(f"流式处理 - AI配置: provider={config.get('provider')}, api_base={config.get('api_base')}, model={config.get('model')}")
            
            logger.info(f"接收到流式聊天消息请求: user_id={user_id}, message={message[:50]}{'...' if len(message) > 50 else ''}")
            
            # 创建流式响应，添加CORS头
            headers = {
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Methods': 'GET',
                'Access-Control-Allow-Headers': 'Content-Type',
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive'
            }
            
            # 创建流式响应
            return StreamingResponse(
                process_stream_message(config, message, chat_history, conversation_id, context), 
                media_type="text/event-stream",
                headers=headers
            )
        
        except Exception as e:
            logger.error(f"处理流式聊天消息请求失败: {str(e)}")
            return StreamingResponse(stream_error_response(f"处理请求失败: {str(e)}"), media_type="text/event-stream")

    logger.info("AI聊天配置API已注册")

async def stream_error_response(error_message):
    """生成错误响应流"""
    yield f"data: {json.dumps({'error': error_message}, ensure_ascii=False)}\n\n"
    
async def process_stream_message(config, message, chat_history, conversation_id, context=None):
    """处理流式消息并返回SSE格式的响应"""
    try:
        # 从context中获取用户ID，如果没有则使用'anonymous'
        user_id = context.get('user_id', 'anonymous') if context else 'anonymous'
        
        # 检查频率限制
        rate_limit = config.get('rate_limit', 20)
        if not check_rate_limit(user_id, rate_limit):
            error_msg = f"发送消息过于频繁，请稍后再试。限制：{rate_limit}条/分钟"
            logger.warning(f"用户 {user_id} 触发频率限制")
            yield f"data: {json.dumps({'error': error_msg}, ensure_ascii=False)}\n\n"
            return
        
        # 验证消息内容和长度
        max_length = config.get('max_message_length', 500)
        enable_filter = config.get('enable_content_filter', True)
        
        validation_valid, validation_reason = validate_message_content(message, max_length, enable_filter)
        if not validation_valid:
            logger.warning(f"用户 {user_id} 消息验证失败: {validation_reason}")
            yield f"data: {json.dumps({'error': validation_reason}, ensure_ascii=False)}\n\n"
            return
        
        # 格式化聊天历史
        formatted_history = []
        for msg in chat_history:
            role = msg.get('role', 'user').lower()
            content = msg.get('content', '')
            
            # 确保角色是有效的
            if role not in ['user', 'assistant', 'system']:
                role = 'user'
                
            formatted_history.append({"role": role, "content": content})
        
        # 限制对话历史长度
        max_conversation_length = config.get('max_conversation_length', 20)
        if len(formatted_history) > max_conversation_length:
            formatted_history = formatted_history[-max_conversation_length:]
        
        # 发送事件流的开始标记
        yield f"data: {json.dumps({'event': 'start'}, ensure_ascii=False)}\n\n"
        
        # 添加详细的配置调试日志
        logger.info(f"准备调用AI流式API - provider: {config.get('provider')}, api_base: {config.get('api_base')}, model: {config.get('model')}")
        
        # 获取AI回复
        buffer = ""
        chunk_count = 0
        
        async for chunk in get_ai_response_stream(config, message, formatted_history):
            chunk_count += 1
            
            # 检查是否是工具相关事件
            if isinstance(chunk, dict) and "event" in chunk:
                yield f"data: {json.dumps(chunk, ensure_ascii=False)}\n\n"
            # 普通文本内容
            elif isinstance(chunk, dict) and "content" in chunk:
                content = chunk.get("content", "")
                buffer += content
                yield f"data: {json.dumps({'content': content}, ensure_ascii=False)}\n\n"
            else:
                logger.warning(f"未知chunk格式: {chunk}")
        
        # 发送完成事件，包含完整响应
        yield f"data: {json.dumps({'event': 'complete', 'conversationId': conversation_id, 'fullResponse': buffer}, ensure_ascii=False)}\n\n"
        
        # 保存消息到数据库或其他存储
        try:
            # 异步保存聊天记录的代码（如果需要）
            pass
        except Exception as save_error:
            logger.error(f"保存聊天记录失败: {str(save_error)}")
            
    except Exception as e:
        logger.error(f"流式处理消息失败: {str(e)}")
        logger.exception("详细错误信息:")
        yield f"data: {json.dumps({'error': f'处理失败: {str(e)}'}, ensure_ascii=False)}\n\n"
        
    # 确保流结束
    yield f"data: [DONE]\n\n"

# 原来的execute_tool_call函数完全替换为基于MCP的实现
async def execute_tool_call(tool_name: str, parameters: dict):
    """通过MCP协议执行工具调用 - 完全基于MCP协议"""
    try:
        logger.info(f"执行MCP工具调用: {tool_name} with {parameters}")
        
        # 确保参数不为空
        if not tool_name:
            return {
                "success": False,
                "error": "工具名称不能为空"
            }
        
        if parameters is None:
            parameters = {}
        
        # 检查MCP管理器是否可用
        if not mcp_manager:
            return {
                "success": False,
                "error": "MCP管理器未初始化"
            }
        
        # 通过MCP服务管理器调用工具
        result = await mcp_manager.call_tool(tool_name, parameters)
        
        logger.info(f"MCP工具调用结果: {result.get('success', False)}")
        
        return result
    
    except Exception as e:
        logger.error(f"MCP工具调用异常 {tool_name}: {e}")
        return {
            "success": False,
            "error": f"工具调用异常: {str(e)}"
        }

# 替换原来的get_web_tools函数，改为通过MCP动态获取
async def get_web_tools() -> List[dict]:
    """获取MCP工具定义 - 完全通过MCP协议动态发现，带降级处理"""
    try:
        # 确保MCP管理器已初始化
        await ensure_mcp_manager()
        
        if not mcp_manager or mcp_manager.initialization_failed:
            logger.info("MCP不可用，返回空工具列表")
            return []
        
        tools = await mcp_manager.get_available_tools()
        logger.info(f"通过MCP获取到 {len(tools)} 个工具")
        return tools
    except Exception as e:
        logger.warning(f"获取MCP工具失败: {e}")
        return []

async def get_ai_response(config, user_message, chat_history=[]):
    """获取AI响应 - 使用MCP协议获取工具"""
    try:
        provider = config.get("provider", "openai")
        api_key = config.get("api_key", "")
        model = config.get("model", "gpt-3.5-turbo")
        # 统一字段名，优先使用api_base，兼容base_url
        api_base = config.get("api_base", config.get("base_url", ""))
        max_tokens = config.get("max_tokens", 2000)
        temperature = config.get("temperature", 0.7)
        
        web_tools_enabled = config.get("enable_web_tools", False)
        web_tools_auto_call = config.get("web_tools_auto_call", False)
        
        # 系统消息
        system_prompt = config.get("system_prompt", "你是一个有用的AI助手。")
        
        # 构建消息列表
        messages = [{"role": "system", "content": system_prompt}]
        
        # 添加历史对话
        if chat_history:
            messages.extend(chat_history[-10:])  # 限制历史消息数量
        
        # 添加当前用户消息
        messages.append({"role": "user", "content": user_message})
        
        # 检查是否需要使用工具
        tools = []
        if web_tools_enabled:
            # 完全通过MCP协议获取工具定义
            tools = await get_web_tools()
            
        # 构建请求参数
        request_params = {
            "model": model,
            "messages": messages,
            "max_tokens": int(max_tokens),
            "temperature": float(temperature),
            "stream": False
        }
        
        # 如果有工具可用，添加工具参数
        if tools:
            request_params["tools"] = tools
            if web_tools_auto_call:
                request_params["tool_choice"] = "auto"
        
        # 根据提供商调用相应的API
        if provider == "openai":
            response = await call_openai_api(api_key, api_base, request_params)
        elif provider == "claude":
            response = await call_claude_api(api_key, request_params)
        elif provider == "qwen":
            response = await call_qwen_api(api_key, request_params)
        elif provider == "custom":
            response = await call_custom_api(api_base, api_key, request_params)
        else:
            return {"error": f"不支持的AI提供商: {provider}", "success": False}
        
        if not response.get("success"):
            return response
        
        # 处理工具调用
        if response.get("tool_calls") and web_tools_enabled and web_tools_auto_call:
            # 执行工具调用 - 完全基于MCP协议
            tool_results = []
            
            for tool_call in response["tool_calls"]:
                tool_name = tool_call.get("function", {}).get("name", "")
                tool_args = tool_call.get("function", {}).get("arguments", {})
                
                if isinstance(tool_args, str):
                    try:
                        tool_args = json.loads(tool_args)
                    except json.JSONDecodeError:
                        tool_args = {}
                
                # 通过MCP执行工具调用
                tool_result = await execute_tool_call(tool_name, tool_args)
                tool_results.append({
                    "tool_call_id": tool_call.get("id", ""),
                    "output": tool_result
                })
            
            # 将工具结果添加到对话中继续生成回复
            messages.append({
                "role": "assistant",
                "content": response.get("content", ""),
                "tool_calls": response["tool_calls"]
            })
            
            for tool_result in tool_results:
                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_result["tool_call_id"],
                    "content": json.dumps(tool_result["output"], ensure_ascii=False)
                })
            
            # 重新调用API获取最终回复
            request_params["messages"] = messages
            # 移除工具参数避免循环调用
            if "tools" in request_params:
                del request_params["tools"]
            if "tool_choice" in request_params:
                del request_params["tool_choice"]
            
            if provider == "openai":
                final_response = await call_openai_api(api_key, api_base, request_params)
            elif provider == "claude":
                final_response = await call_claude_api(api_key, request_params)
            elif provider == "qwen":
                final_response = await call_qwen_api(api_key, request_params)
            elif provider == "custom":
                final_response = await call_custom_api(api_base, api_key, request_params)
            else:
                final_response = response
            
            if final_response.get("success"):
                # 合并工具调用结果到最终回复中
                final_content = final_response.get("content", "")
                tool_summary = "\n\n**工具调用结果:**\n"
                for i, tool_result in enumerate(tool_results, 1):
                    result_content = tool_result["output"].get("content", "")
                    if result_content:
                        tool_summary += f"{i}. {result_content}\n"
                
                final_response["content"] = final_content + tool_summary
                final_response["tool_results"] = tool_results
                
            return final_response
        
        return response
        
    except Exception as e:
        logger.error(f"获取AI响应失败: {e}")
        return {"error": f"获取AI响应失败: {str(e)}", "success": False}

async def call_openai_api(api_key, base_url, request_params):
    """调用OpenAI API"""
    try:
        # 确保API base URL格式正确
        if base_url and not base_url.endswith('/v1') and not base_url.endswith('/v1/'):
            if base_url.endswith('/'):
                base_url += 'v1'
            else:
                base_url += '/v1'
        
        if not base_url:
            base_url = "https://api.openai.com/v1"
        
        # 创建OpenAI客户端
        client = AsyncOpenAI(
            api_key=api_key,
            base_url=base_url,
            timeout=60.0,
            # 为ModelScope添加特殊的headers
            default_headers=(
                {"User-Agent": "ModelScope-Python-SDK"} 
                if 'modelscope' in base_url.lower() 
                else {}
            )
        )
        
        try:
            # 调用OpenAI SDK
            response = await client.chat.completions.create(**request_params)
            
            if response.choices:
                choice = response.choices[0]
                
                # 检查是否有工具调用
                tool_calls = None
                if hasattr(choice.message, 'tool_calls') and choice.message.tool_calls:
                    tool_calls = [
                        {
                            "id": tc.id,
                            "type": tc.type,
                            "function": {
                                "name": tc.function.name,
                                "arguments": tc.function.arguments
                            }
                        }
                        for tc in choice.message.tool_calls
                    ]
                
                return {
                    "success": True,
                    "content": choice.message.content or '',
                    "tool_calls": tool_calls,
                    "usage": response.usage.model_dump() if response.usage else {}
                }
            else:
                return {"success": False, "error": "API响应格式异常"}
                
        finally:
            await client.close()
            
    except Exception as e:
        logger.error(f"OpenAI SDK API调用异常: {e}")
        return {"success": False, "error": f"API调用异常: {str(e)}"}

async def call_claude_api(api_key, request_params):
    """调用Claude API"""
    try:
        headers = {
            "x-api-key": api_key,
            "Content-Type": "application/json",
            "anthropic-version": "2023-06-01"
        }
        
        # 转换消息格式
        claude_messages = []
        system_content = ""
        
        for msg in request_params.get('messages', []):
            if msg['role'] == 'system':
                system_content = msg['content']
            else:
                claude_messages.append(msg)
        
        claude_params = {
            "model": request_params.get('model', 'claude-3-sonnet-20240229'),
            "max_tokens": request_params.get('max_tokens', 1000),
            "temperature": request_params.get('temperature', 0.7),
            "messages": claude_messages
        }
        
        if system_content:
            claude_params["system"] = system_content
        
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(
                "https://api.anthropic.com/v1/messages", 
                headers=headers, 
                json=claude_params
            )
            
            if response.status_code == 200:
                data = response.json()
                content = ""
                if data.get('content'):
                    for item in data['content']:
                        if item.get('type') == 'text':
                            content += item.get('text', '')
                
                return {
                    "success": True,
                    "content": content,
                    "usage": data.get('usage', {})
                }
            else:
                error_text = response.text
                return {"success": False, "error": f"Claude API调用失败: {response.status_code} - {error_text}"}
                
    except Exception as e:
        logger.error(f"Claude API调用异常: {e}")
        return {"success": False, "error": f"API调用异常: {str(e)}"}

async def call_qwen_api(api_key, request_params):
    """调用Qwen API"""
    try:
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        
        # Qwen API URL
        api_url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        
        # 转换请求格式
        qwen_params = {
            "model": request_params.get('model', 'qwen-turbo'),
            "input": {
                "messages": request_params.get('messages', [])
            },
            "parameters": {
                "temperature": request_params.get('temperature', 0.7),
                "max_tokens": request_params.get('max_tokens', 1000),
            }
        }
        
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(api_url, headers=headers, json=qwen_params)
            
            if response.status_code == 200:
                data = response.json()
                if data.get('output', {}).get('choices'):
                    choice = data['output']['choices'][0]
                    return {
                        "success": True,
                        "content": choice.get('message', {}).get('content', ''),
                        "usage": data.get('usage', {})
                    }
                else:
                    return {"success": False, "error": "Qwen API响应格式异常"}
            else:
                error_text = response.text
                return {"success": False, "error": f"Qwen API调用失败: {response.status_code} - {error_text}"}
                
    except Exception as e:
        logger.error(f"Qwen API调用异常: {e}")
        return {"success": False, "error": f"API调用异常: {str(e)}"}

async def call_custom_api(base_url, api_key, request_params):
    """调用自定义API（OpenAI兼容格式）"""
    return await call_openai_api(api_key, base_url, request_params)

async def get_ai_response_stream(config, user_message, chat_history=[]):
    """获取AI回复（流式输出）"""
    try:
        provider = config.get('provider', 'openai')
        api_key = config.get('api_key', '')
        model = config.get('model', 'gpt-3.5-turbo')
        api_base = config.get('api_base', '')
        
        # 检查是否需要自动调用工具
        web_tools_enabled = config.get('enable_web_tools', True)
        web_tools_auto_call = config.get('web_tools_auto_call', True)
        
        # 自动识别网页分析请求并调用工具
        auto_tool_call_result = None
        if web_tools_enabled and web_tools_auto_call:
            # 网站URL正则模式
            url_pattern = r'https?://[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+(:[0-9]{1,5})?(/[-a-zA-Z0-9.%_~:/?#[\]@!$&\'()*+,;=]*)*'
            domain_pattern = r'([a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+)'
            
            # 检查用户消息是否包含URL或网站域名
            urls = re.findall(url_pattern, user_message)
            domains = re.findall(domain_pattern, user_message)
            
            # 分析类请求关键词
            analysis_keywords = ['分析', '获取', '查看', '浏览', '打开', '内容', '信息', '数据', 'analyze', 'check', 'view', 'open', 'content']
            
            is_analysis_request = any(keyword in user_message.lower() for keyword in analysis_keywords)
            
            # 如果是分析请求且包含URL或域名
            if is_analysis_request and (urls or domains):
                target_url = None
                if urls:
                    target_url = urls[0]
                elif domains:
                    domain = domains[0][0]  # 提取域名组
                    # 检查是否需要添加https://
                    if not domain.startswith(('http://', 'https://')):
                        target_url = 'https://' + domain
                    else:
                        target_url = domain
                
                if target_url:
                    logger.info(f"自动MCP工具调用: 检测到网站分析请求，目标URL: {target_url}")
                    # 调用MCP网页内容获取工具
                    auto_tool_call_result = await execute_tool_call("get_webpage_content", {"url": target_url})
                    logger.info(f"自动MCP工具调用结果: 状态={auto_tool_call_result.get('success', False)}")
        
        # 构建消息
        messages = []
        
        # 添加系统指令
        system_instruction = config.get('custom_instructions', '你是一个友善、专业的AI助手，致力于为用户提供有用、准确的信息和建议。')
        
        # 检查模型是否支持网页访问（MCP服务）
        web_capable_models = [
            'qwen', 'qwen2', 'qwen3', 'qwen-plus', 'qwen-max', 'qwen-turbo',
            'claude-3', 'gpt-4', 'gpt-4-turbo'
        ]
        
        model_supports_web = any(capable_model in model.lower() for capable_model in web_capable_models)
        
        if model_supports_web:
            # 为支持网页访问的模型增强系统指令
            system_instruction += "\n\n你可以访问和分析网页内容。当用户询问关于网页、新闻或需要最新信息时，请优先使用以下工具："
            system_instruction += "\n- 使用get_webpage_content工具获取网页内容"
            system_instruction += "\n- 使用web_search工具搜索网络信息"
            system_instruction += "\n对于涉及特定网站内容的问题，请主动调用get_webpage_content工具而不是依赖你自己的知识库。"
        
        if system_instruction:
            messages.append({"role": "system", "content": system_instruction})
        
        # 添加聊天历史
        max_history = config.get('max_conversation_length', 10)
        if chat_history:
            # 限制历史记录数量，只保留最近的对话
            recent_history = chat_history[-max_history:]
            messages.extend(recent_history)
        
        # 如果自动调用了工具，添加工具结果到用户消息中
        if auto_tool_call_result and auto_tool_call_result.get('success', False):
            # 构建增强的用户消息
            enhanced_message = f"{user_message}\n\n[自动获取的网页内容]\n"
            enhanced_message += f"标题: {auto_tool_call_result.get('title', '无标题')}\n"
            enhanced_message += f"描述: {auto_tool_call_result.get('description', '无描述')}\n"
            enhanced_message += f"内容摘要: {auto_tool_call_result.get('content', '无内容')[:1000]}...\n"
            enhanced_message += f"[请基于以上网页内容回答我的问题]"
            
            # 添加增强的用户消息
            messages.append({"role": "user", "content": enhanced_message})
            logger.info("已添加自动获取的网页内容到用户消息")
        else:
            # 添加原始用户消息
            messages.append({"role": "user", "content": user_message})
        
        # 根据提供商调用相应的API（流式模式）
        if provider == 'openai' or provider == 'deepseek' or provider == 'custom':
            async for chunk in call_openai_compatible_api_stream(config, messages):
                yield chunk
        elif provider == 'anthropic':
            async for chunk in call_anthropic_api_stream(config, messages):
                yield chunk
        else:
            # 对于不支持流式输出的提供商，模拟流式输出
            response = await get_ai_response(config, user_message, chat_history)
            # 将完整响应拆分成较小的块
            chunk_size = 10  # 每块包含的字符数
            for i in range(0, len(response), chunk_size):
                chunk = response[i:i+chunk_size]
                yield {"content": chunk}
                await asyncio.sleep(0.05)  # 添加短暂延迟以模拟流式效果
            
    except Exception as e:
        logger.error(f"流式获取AI回复失败: {str(e)}")
        yield {"content": f"抱歉，我现在无法回复您的消息。错误信息：{str(e)}"}

async def call_openai_compatible_api_stream(config, messages):
    """调用OpenAI兼容的API（流式输出）"""
    try:
        api_key = config.get('api_key', '')
        model = config.get('model', 'gpt-3.5-turbo')
        api_base = config.get('api_base') or config.get('base_url') or 'https://api.openai.com/v1'
        
        # 确保API base URL格式正确
        if not api_base.endswith('/v1') and not api_base.endswith('/v1/'):
            if api_base.endswith('/'):
                api_base += 'v1'
            else:
                api_base += '/v1'
        
        # 检查API密钥
        if not api_key:
            logger.error("API密钥为空")
            yield {"content": "错误：API密钥未配置"}
            return
        
        # 构建请求参数
        stream_params = {
            "model": model,
            "messages": messages,
            "stream": True,
            "temperature": config.get('temperature', 0.7),
            "max_tokens": config.get('max_tokens', 1000),
        }
        
        # 添加OpenAI格式参数
        provider = config.get('provider', 'openai')
        if provider not in ['anthropic']:
            stream_params.update({
                "top_p": config.get('top_p', 1.0),
                "frequency_penalty": config.get('frequency_penalty', 0.0),
                "presence_penalty": config.get('presence_penalty', 0.0),
            })
        
        # 根据用户配置决定是否启用思考模式
        enable_thinking = config.get('enable_thinking', False)
        
        if enable_thinking:
            stream_params["enable_thinking"] = True
        
        # 检查模型是否支持工具调用
        tool_capable_models = [
            'qwen', 'qwen2', 'qwen3', 'qwen-plus', 'qwen-max', 'qwen-turbo',
            'gpt-4', 'gpt-4-turbo', 'gpt-3.5-turbo',
            'claude-3', 'deepseek', 'glm'
        ]
        
        model_supports_tools = any(capable_model in model.lower() for capable_model in tool_capable_models)
        web_tools_enabled = config.get('enable_web_tools', True) and config.get('web_tools_auto_call', True)
        
        # 过滤可用工具 - 通过MCP动态获取
        available_tools = []
        if web_tools_enabled and model_supports_tools:
            try:
                # 通过MCP获取可用工具
                mcp_tools = await get_web_tools()
                for tool in mcp_tools:
                    tool_name = tool.get("function", {}).get("name", "")
                    if tool_name == "get_webpage_content" and config.get('enable_webpage_content', True):
                        available_tools.append(tool)
                    elif tool_name == "web_search" and config.get('enable_web_search', True):
                        available_tools.append(tool)
                    else:
                        # 添加其他MCP工具
                        available_tools.append(tool)
            except Exception as e:
                logger.warning(f"获取MCP工具失败: {e}")
        
        # 只有在有工具时才添加工具参数
        if available_tools:
            stream_params["tools"] = available_tools
            stream_params["tool_choice"] = "auto"
        
        logger.info(f"流式API调用参数: provider={provider}, api_base={api_base}, model={model}")
        
        # 创建OpenAI客户端
        client = AsyncOpenAI(
            api_key=api_key,
            base_url=api_base,
            timeout=120.0,
        )
        
        # 用于跟踪工具调用的变量
        tool_calls = []
        current_tool_call = None
        thinking_started = False
        thinking_buffer = ""
        
        try:
            # 调用OpenAI SDK的流式接口
            stream = await client.chat.completions.create(**stream_params)
            
            # 处理流式响应
            async for chunk in stream:
                try:
                    if not chunk.choices:
                        continue
                        
                    delta = chunk.choices[0].delta
                    
                    # 处理思考内容（如果支持）
                    if hasattr(delta, 'reasoning_content') and delta.reasoning_content:
                        if not thinking_started:
                            thinking_started = True
                            yield {"content": "💭 **思考过程**: "}
                        yield {"content": delta.reasoning_content}
                        thinking_buffer += delta.reasoning_content
                    elif delta.content:
                        if thinking_started and thinking_buffer:
                            yield {"content": "\n\n---\n\n"}
                            thinking_started = False
                        yield {"content": delta.content}
                    
                    # 处理工具调用
                    if delta.tool_calls:
                        for tool_call_delta in delta.tool_calls:
                            index = tool_call_delta.index
                            
                            # 确保有足够的位置
                            while len(tool_calls) <= index:
                                tool_calls.append({
                                    "id": "",
                                    "type": "function",
                                    "function": {"name": "", "arguments": ""}
                                })
                            
                            # 更新工具调用信息
                            if tool_call_delta.id:
                                tool_calls[index]["id"] = tool_call_delta.id
                            
                            if tool_call_delta.function:
                                if tool_call_delta.function.name:
                                    tool_calls[index]["function"]["name"] = tool_call_delta.function.name
                                    
                                    # 发送工具调用开始事件
                                    yield {
                                        "event": "tool_call",
                                        "data": {
                                            "tool": tool_call_delta.function.name,
                                            "status": "starting"
                                        }
                                    }
                                
                                if tool_call_delta.function.arguments:
                                    tool_calls[index]["function"]["arguments"] += tool_call_delta.function.arguments
                    
                    # 检查完成状态
                    if chunk.choices[0].finish_reason == "tool_calls" and tool_calls:
                        # 执行工具调用
                        for tool_call in tool_calls:
                            if tool_call["function"]["name"]:
                                try:
                                    tool_name = tool_call["function"]["name"]
                                    try:
                                        arguments_str = tool_call["function"]["arguments"]
                                        tool_args = json.loads(arguments_str) if arguments_str.strip() else {}
                                    except json.JSONDecodeError:
                                        logger.error(f"工具参数JSON解析失败: {arguments_str}")
                                        tool_args = {}
                                    
                                    # 执行工具
                                    yield {
                                        "event": "tool_call",
                                        "data": {
                                            "tool": tool_name,
                                            "status": "executing",
                                            "arguments": tool_args
                                        }
                                    }
                                    
                                    tool_result = await execute_tool_call(tool_name, tool_args)
                                    
                                    yield {
                                        "event": "tool_result",
                                        "data": {
                                            "tool": tool_name,
                                            "status": "completed",
                                            "result": tool_result
                                        }
                                    }
                                    
                                    # 构建工具结果消息继续对话
                                    tool_messages = messages.copy()
                                    tool_messages.append({
                                        "role": "assistant",
                                        "content": None,
                                        "tool_calls": [tool_call]
                                    })
                                    tool_messages.append({
                                        "role": "tool",
                                        "tool_call_id": tool_call["id"],
                                        "content": json.dumps(tool_result, ensure_ascii=False)
                                    })
                                    
                                    # 发送第二次请求
                                    second_params = stream_params.copy()
                                    second_params["messages"] = tool_messages
                                    if "tools" in second_params:
                                        del second_params["tools"]
                                    if "tool_choice" in second_params:
                                        del second_params["tool_choice"]
                                    
                                    yield {"content": "\n\n**工具调用结果:**\n\n"}
                                    
                                    # 创建新的客户端进行第二次调用
                                    second_stream = await client.chat.completions.create(**second_params)
                                    
                                    async for second_chunk in second_stream:
                                        if second_chunk.choices and second_chunk.choices[0].delta.content:
                                            yield {"content": second_chunk.choices[0].delta.content}
                                    
                                except Exception as e:
                                    logger.error(f"处理工具调用时出错: {str(e)}")
                                    yield {"content": f"\n\n**工具调用失败:** {str(e)}\n\n"}
                        
                        break  # 工具调用完成后退出
                        
                except Exception as e:
                    logger.warning(f"处理流式chunk时出错: {str(e)}")
                    continue
                    
        finally:
            # 确保客户端正确关闭
            await client.close()
    
    except Exception as e:
        logger.error(f"OpenAI SDK流式API调用异常: {str(e)}")
        yield {"content": f"抱歉，我现在无法回复您的消息: {str(e)}"}

async def call_anthropic_api_stream(config, messages):
    """调用Anthropic Claude API（流式输出）"""
    try:
        api_key = config.get('api_key', '')
        model = config.get('model', 'claude-3-sonnet-20240229')
        
        headers = {
            "x-api-key": api_key,
            "Content-Type": "application/json",
            "anthropic-version": "2023-06-01"
        }
        
        # Anthropic API格式转换
        claude_messages = []
        for msg in messages:
            if msg['role'] != 'system':  # Claude在messages中不支持system role
                claude_messages.append(msg)
        
        # 系统指令单独处理
        system_content = ""
        for msg in messages:
            if msg['role'] == 'system':
                system_content = msg['content']
                break
        
        payload = {
            "model": model,
            "max_tokens": config.get('max_tokens', 1000),
            "temperature": config.get('temperature', 0.7),
            "messages": claude_messages,
            "stream": True
        }
        
        if system_content:
            payload["system"] = system_content
        
        async with httpx.AsyncClient(timeout=60) as client:
            async with client.stream("POST", "https://api.anthropic.com/v1/messages", headers=headers, json=payload, timeout=60) as response:
                if response.status_code != 200:
                    error_text = await response.aread()
                    logger.error(f"Claude流式API调用失败: {response.status_code} - {error_text.decode('utf-8', errors='ignore')}")
                    yield {"content": f"抱歉，Claude API调用失败 ({response.status_code}): {error_text.decode('utf-8', errors='ignore')[:200]}..."}
                    return
                
                # 处理流式响应
                async for chunk in response.aiter_lines():
                    if not chunk.strip() or chunk.startswith(":"):
                        continue
                    
                    if chunk.startswith("data: "):
                        chunk = chunk[6:]
                    
                    if chunk == "[DONE]":
                        break
                    
                    try:
                        data = json.loads(chunk)
                        delta = data.get("delta", {})
                        content = delta.get("text", "")
                        
                        if content:
                            yield {"content": content}
                    except json.JSONDecodeError:
                        logger.warning(f"解析Claude JSON失败: {chunk}")
    except Exception as e:
        logger.error(f"Claude流式API调用异常: {str(e)}")
        yield {"content": f"抱歉，我现在无法回复您的消息: {str(e)}"} 

# AI聊天用户频率限制跟踪
user_request_times = {}

def check_rate_limit(user_id, rate_limit=20):
    """检查用户发言频率限制"""
    current_time = time.time()
    
    # 如果用户不存在于记录中，直接通过
    if user_id not in user_request_times:
        user_request_times[user_id] = [current_time]
        return True
    
    # 清理一小时前的记录
    one_hour_ago = current_time - 3600
    user_request_times[user_id] = [t for t in user_request_times[user_id] if t > one_hour_ago]
    
    # 检查是否超过频率限制
    if len(user_request_times[user_id]) >= rate_limit:
        return False
    
    # 记录当前请求时间
    user_request_times[user_id].append(current_time)
    return True

def validate_message_content(message, max_length=500, enable_filter=True):
    """验证消息内容，包括长度限制和内容过滤"""
    try:
        # 长度检查
        if len(message) > max_length:
            return False, f"消息长度不能超过{max_length}个字符"
        
        # 基本内容过滤
        if enable_filter:
            prohibited_keywords = ['spam', '垃圾', '广告', '色情', '暴力', '违法']
            message_lower = message.lower()
            for keyword in prohibited_keywords:
                if keyword in message_lower:
                    return False, f"消息包含不当内容: {keyword}"
        
        return True, "消息内容验证通过"
        
    except Exception as e:
        logger.error(f"验证消息内容时出错: {str(e)}")
        return False, "消息内容验证失败"

# MCP服务管理器 - 使用FastMCP Client的MCP服务管理器
class MCPServerManager:
    """使用FastMCP Client的MCP服务管理器"""
    
    def __init__(self):
        self.clients = {}  # 存储MCP客户端连接
        self.available_tools = []
        self.config_file = os.path.join(DATA_DIR, 'mcp_config.json')
        self.servers_config = {}
        self.initialization_failed = False
        self.load_config()
        self.setup_builtin_servers()
    
    def setup_builtin_servers(self):
        """设置内置MCP服务器配置 - 包含多个实用服务器"""
        import sys
        current_python = sys.executable
        
        # 内置服务器配置
        builtin_servers = {
            "poetize-theme-controller": {
                "command": [current_python, os.path.join(os.path.dirname(__file__), "server.py")],
                "description": "Poetize主题切换控制器",
                "enabled": True
            },
            "12306-mcp": {
                "command": ["npx", "-y", "12306-mcp"],
                "description": "12306购票信息查询服务器",
                "enabled": True
            },
            "mcp-server-time": {
                "command": ["uvx", "mcp-server-time"],
                "description": "时间服务器",
                "enabled": True
            },
            "mcp-deepwiki": {
                "command": ["npx", "-y", "mcp-deepwiki@latest"],
                "description": "深度知识库",
                "enabled": True
            }
        }
        
        # Windows环境特殊处理
        if platform.system() == 'Windows':
            # 在Windows环境下，某些命令可能需要调整
            # 但保持配置结构不变，只是可能禁用某些服务器
            for server_name, config in builtin_servers.items():
                if server_name == "poetize-theme-controller":
                    # 主题控制器在Windows下也应该可用
                    pass
                elif "npx" in config["command"] or "uvx" in config["command"]:
                    # 检查npm/uvx是否可用
                    config["enabled"] = True
        
        # 合并配置
        for name, config in builtin_servers.items():
            if name not in self.servers_config:
                self.servers_config[name] = config
    
    def load_config(self):
        """加载MCP服务配置"""
        try:
            if os.path.exists(self.config_file):
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                    self.servers_config = config.get('servers', {})
                    logger.info(f"加载了 {len(self.servers_config)} 个MCP服务器配置")
        except Exception as e:
            logger.error(f"加载MCP配置失败: {e}")
            self.servers_config = {}
    
    def save_config(self):
        """保存MCP服务配置"""
        try:
            config = {'servers': self.servers_config}
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump(config, f, ensure_ascii=False, indent=2)
            return True
        except Exception as e:
            logger.error(f"保存MCP配置失败: {e}")
            return False
    
    async def initialize(self):
        """初始化MCP客户端连接 - 增强错误处理"""
        try:
            # 检查是否在Windows环境
            if platform.system() == 'Windows':
                logger.warning("检测到Windows环境，MCP功能可能受限")
                # 在Windows上先尝试简化初始化
                return await self._initialize_windows_compatible()
            
            # 非Windows环境的标准初始化
            return await self._initialize_standard()
                
        except Exception as e:
            logger.error(f"MCP初始化失败: {e}")
            self.initialization_failed = True
            # 返回False但不抛出异常，允许系统继续运行
            return False
    
    async def _initialize_windows_compatible(self):
        """Windows兼容的初始化方式"""
        try:
            # 暂时禁用所有MCP服务器，只提供基础功能
            logger.info("Windows环境下暂时禁用MCP服务器")
            self.available_tools = []
            return False  # 表示MCP不可用，但不是错误
        except Exception as e:
            logger.error(f"Windows兼容初始化失败: {e}")
            return False
    
    async def _initialize_standard(self):
        """标准初始化方式"""
        try:
            from fastmcp import Client
            from fastmcp.utilities.mcp_config import MCPConfig, StdioMCPServer
            
            # 连接启用的服务器
            servers_connected = 0
            for name, config in self.servers_config.items():
                if config.get('enabled', False):  # 只连接明确启用的服务器
                    try:
                        # 创建StdioMCPServer配置
                        stdio_server = StdioMCPServer(
                            command=config['command'][0],
                            args=config['command'][1:] if len(config['command']) > 1 else [],
                            env={}
                        )
                        
                        # 创建MCP配置
                        mcp_config = MCPConfig(
                            mcpServers={name: stdio_server}
                        )
                        
                        # 创建客户端连接
                        client = Client(mcp_config)
                        
                        # 连接到服务器，添加超时控制
                        await asyncio.wait_for(client.__aenter__(), timeout=5.0)
                        
                        # 存储客户端
                        self.clients[name] = client
                        servers_connected += 1
                        logger.info(f"已连接MCP服务器: {name}")
                        
                    except asyncio.TimeoutError:
                        logger.warning(f"连接MCP服务器 {name} 超时")
                        config['enabled'] = False
                    except Exception as e:
                        logger.warning(f"连接MCP服务器 {name} 失败: {e}")
                        config['enabled'] = False
            
            if servers_connected > 0:
                # 获取可用工具
                await self.refresh_tools()
                
                logger.info(f"MCP初始化成功，连接了 {servers_connected} 个服务器，发现 {len(self.available_tools)} 个工具")
                return True
            else:
                logger.info("没有可用的MCP服务器，MCP功能将被禁用")
                return False
                
        except ImportError:
            logger.warning("FastMCP库未安装，MCP功能将被禁用")
            return False
        except Exception as e:
            logger.error(f"MCP标准初始化失败: {e}")
            return False
    
    async def refresh_tools(self):
        """刷新可用工具列表 - 增强错误处理"""
        if not self.clients:
            self.available_tools = []
            return
        
        try:
            self.available_tools = []
            
            # 从所有连接的客户端获取工具
            for server_name, client in self.clients.items():
                try:
                    # 获取工具列表，添加超时
                    tools = await asyncio.wait_for(client.list_tools(), timeout=3.0)
                    
                    # 转换为标准格式
                    for tool in tools:
                        tool_def = {
                            "type": "function",
                            "function": {
                                "name": f"{server_name}_{tool.name}",  # 添加服务器前缀避免冲突
                                "description": tool.description or '',
                                "parameters": tool.inputSchema or {
                                    "type": "object",
                                    "properties": {},
                                    "required": []
                                }
                            },
                            "_server": server_name,  # 内部标记，用于调用时识别服务器
                            "_original_name": tool.name  # 原始工具名
                        }
                        self.available_tools.append(tool_def)
                        
                except asyncio.TimeoutError:
                    logger.warning(f"从服务器 {server_name} 获取工具超时")
                except Exception as e:
                    logger.warning(f"从服务器 {server_name} 获取工具失败: {e}")
            
            logger.info(f"刷新工具列表完成，共 {len(self.available_tools)} 个工具")
                
        except Exception as e:
            logger.error(f"刷新工具列表失败: {e}")
            self.available_tools = []
    
    async def call_tool(self, tool_name: str, arguments: dict) -> dict:
        """调用MCP工具 - 增强错误处理"""
        if self.initialization_failed or not self.clients:
            return {
                "success": False,
                "error": "MCP服务不可用"
            }
        
        try:
            logger.info(f"调用MCP工具: {tool_name} with {arguments}")
            
            # 查找工具对应的服务器
            server_name = None
            original_tool_name = None
            
            for tool in self.available_tools:
                if tool.get('function', {}).get('name') == tool_name:
                    server_name = tool.get('_server')
                    original_tool_name = tool.get('_original_name')
                    break
            
            if not server_name or server_name not in self.clients:
                return {
                    "success": False,
                    "error": f"找不到工具 {tool_name} 对应的服务器"
                }
            
            client = self.clients[server_name]
            
            # 调用工具，添加超时
            result = await asyncio.wait_for(
                client.call_tool(original_tool_name, arguments or {}), 
                timeout=10.0
            )
            
            logger.info(f"工具调用结果: {result}")
            
            # 标准化返回格式
            content = ""
            if result:
                # 提取文本内容
                text_parts = []
                for item in result:
                    if hasattr(item, 'text'):
                        text_parts.append(item.text)
                    elif hasattr(item, 'content'):
                        text_parts.append(str(item.content))
                    else:
                        text_parts.append(str(item))
                content = "\n".join(text_parts)
            
            return {
                "success": True,
                "content": content,
                "title": "",
                "description": "",
                "raw_result": result
            }
        
        except asyncio.TimeoutError:
            logger.error(f"MCP工具调用超时: {tool_name}")
            return {
                "success": False,
                "error": "工具调用超时"
            }
        except Exception as e:
            logger.error(f"MCP工具调用失败 {tool_name}: {e}")
            return {
                "success": False,
                "error": f"工具调用失败: {str(e)}"
            }
    
    async def get_available_tools(self) -> List[dict]:
        """获取所有可用工具 - 降级处理"""
        if self.initialization_failed:
            # 如果MCP初始化失败，返回空列表但不报错
            return []
        
        if not self.available_tools:
            await self.refresh_tools()
        return self.available_tools.copy()
    
    async def cleanup(self):
        """清理资源 - 增强错误处理"""
        cleanup_errors = []
        for server_name, client in self.clients.items():
            try:
                await asyncio.wait_for(client.__aexit__(None, None, None), timeout=3.0)
                logger.info(f"已关闭MCP客户端: {server_name}")
            except asyncio.TimeoutError:
                logger.warning(f"关闭MCP客户端 {server_name} 超时")
                cleanup_errors.append(f"{server_name}: 超时")
            except Exception as e:
                logger.error(f"关闭MCP客户端 {server_name} 失败: {e}")
                cleanup_errors.append(f"{server_name}: {str(e)}")
        
        self.clients.clear()
        
        if cleanup_errors:
            logger.warning(f"MCP清理过程中出现问题: {cleanup_errors}")

# 全局MCP服务管理器
mcp_manager = None

async def init_mcp_manager():
    """初始化MCP服务管理器 - 增强错误处理和降级机制"""
    global mcp_manager
    try:
        if mcp_manager is None:
            mcp_manager = MCPServerManager()
            
            # 初始化FastMCP，但不阻断应用启动
            try:
                success = await mcp_manager.initialize()
                
                if success:
                    logger.info("MCP服务管理器初始化成功")
                else:
                    logger.info("MCP服务管理器初始化失败，但应用将继续运行（不影响基本AI聊天功能）")
                    
            except Exception as init_error:
                logger.warning(f"MCP初始化过程中出现异常，继续运行基本功能: {init_error}")
                mcp_manager.initialization_failed = True
        else:
            logger.info("MCP管理器已存在，跳过重复初始化")
                
    except Exception as e:
        logger.error(f"初始化MCP管理器异常: {e}")
        # 创建基本的管理器实例，即使失败也不阻断其他功能
        if mcp_manager is None:
            mcp_manager = MCPServerManager()
            mcp_manager.initialization_failed = True

# 延迟初始化MCP管理器，避免阻塞模块加载
async def ensure_mcp_manager():
    """确保MCP管理器已初始化"""
    global mcp_manager
    if mcp_manager is None:
        try:
            await init_mcp_manager()
        except Exception as e:
            logger.warning(f"确保MCP管理器时出现异常，创建降级实例: {e}")
            # 创建一个基本实例以避免None引用
            mcp_manager = MCPServerManager()
            mcp_manager.initialization_failed = True

logger.info("AI聊天配置API已注册 - 现已支持MCP协议")