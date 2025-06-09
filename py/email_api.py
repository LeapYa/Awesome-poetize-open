#!/usr/bin/env python
# -*- coding: utf-8 -*-

import smtplib
import ssl
import json
import os
import socket
import socks
import httpx
from fastapi import FastAPI, Request, HTTPException, Depends
from config import BASE_BACKEND_URL, JAVA_CONFIG_URL
from auth_decorator import admin_required  # 导入管理员权限装饰器

# 邮箱配置文件路径
EMAIL_CONFIG_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'py', 'data', 'email_configs.json')

def get_email_configs():
    """获取所有邮箱配置"""
    try:
        if not os.path.exists(EMAIL_CONFIG_FILE):
            return []
        with open(EMAIL_CONFIG_FILE, 'r', encoding='utf-8') as f:
            configs = json.load(f)
            return configs.get("configs", [])
    except Exception as e:
        print(f"读取邮箱配置文件出错: {str(e)}")
        return []

def get_mail_config(config_id=None):
    """获取指定ID的邮箱配置，如果未指定ID则返回默认配置"""
    try:
        configs = get_email_configs()
        if not configs:
            return None
        
        # 如果指定了配置ID，查找对应配置
        if config_id:
            for config in configs:
                if str(config.get('id', '')) == str(config_id):
                    return config
        
        # 未指定ID或未找到指定ID的配置，返回默认配置
        for config in configs:
            if config.get('isDefault', False):
                return config
        
        # 如果没有默认配置，返回第一个
        return configs[0] if configs else None
    except Exception as e:
        print(f"获取邮箱配置出错: {str(e)}")
        return None

def create_smtp_connection(config):
    """根据配置创建SMTP连接（仅用于测试）"""
    if not config:
        raise ValueError("邮箱配置不能为空")
    
    # 获取基本配置
    host = config.get('host', '')
    port = int(config.get('port', 25))
    username = config.get('username', '')
    password = config.get('password', '')
    
    # SSL配置处理 - 兼容前端不同格式
    ssl_enabled = False
    if 'ssl' in config:
        # 处理可能的各种布尔值表示
        ssl_val = config.get('ssl')
        if isinstance(ssl_val, bool):
            ssl_enabled = ssl_val
        elif isinstance(ssl_val, str) and ssl_val.lower() in ('true', '1', 'yes'):
            ssl_enabled = True
        elif ssl_val in (1, True):
            ssl_enabled = True
    
    # 根据端口自动确定SSL - 端口465通常要求SSL
    port_requires_ssl = False
    if port == 465:
        port_requires_ssl = True
        print(f"端口{port}通常需要SSL连接，自动启用SSL")
    
    # 如果端口要求SSL但配置中未启用，强制启用SSL
    if port_requires_ssl and not ssl_enabled:
        ssl_enabled = True
        print("由于端口要求，已强制启用SSL")
    
    # 获取高级配置
    protocol = config.get('protocol', 'SMTP').upper()
    # 增加默认超时时间，避免连接过快超时
    connection_timeout = int(config.get('connectionTimeout', 60))
    
    print(f"尝试连接邮箱服务器: {host}:{port}, 协议:{protocol}, SSL:{ssl_enabled}, 超时:{connection_timeout}秒")
    
    # 代理设置
    proxy_host = config.get('proxyHost', '')
    proxy_port = int(config.get('proxyPort', 0)) if config.get('proxyPort') else 0
    proxy_type = config.get('proxyType', '').upper()
    
    # 如果启用了代理
    if proxy_host and proxy_port > 0 and proxy_type:
        print(f"使用代理: {proxy_type}://{proxy_host}:{proxy_port}")
        if proxy_type == 'SOCKS5':
            socks.set_default_proxy(socks.SOCKS5, proxy_host, proxy_port)
        elif proxy_type == 'SOCKS4':
            socks.set_default_proxy(socks.SOCKS4, proxy_host, proxy_port)
        elif proxy_type == 'HTTP':
            socks.set_default_proxy(socks.HTTP, proxy_host, proxy_port)
        socket.socket = socks.socksocket
    
    # 创建连接
    try:
        # 根据端口和SSL设置选择连接方式
        if ssl_enabled:
            print(f"创建SSL连接(端口:{port})...")
            context = ssl.create_default_context()
            server = smtplib.SMTP_SSL(host, port, context=context, timeout=connection_timeout)
        else:
            # 端口587通常使用STARTTLS
            if port == 587:
                print(f"创建普通连接(端口:{port})，将尝试STARTTLS...")
                server = smtplib.SMTP(host, port, timeout=connection_timeout)
                try:
                    server.ehlo()
                    server.starttls()
                    server.ehlo()
                    print("STARTTLS连接成功建立")
                except Exception as e:
                    print(f"STARTTLS失败: {str(e)}，继续使用普通连接")
            else:
                print(f"创建普通连接(端口:{port})...")
                server = smtplib.SMTP(host, port, timeout=connection_timeout)
        
        # 登录邮箱
        if username and password:
            print(f"尝试登录: {username}")
            server.login(username, password)
            print("登录成功!")
        
        return server
    except socket.timeout:
        print(f"连接超时，请检查服务器地址和端口是否正确，超时时间({connection_timeout}秒)是否足够")
        raise
    except ssl.SSLError as e:
        print(f"SSL错误: {str(e)}，请检查SSL设置是否正确")
        raise
    except smtplib.SMTPAuthenticationError:
        print("认证失败，用户名或密码错误")
        raise
    except smtplib.SMTPException as e:
        print(f"SMTP错误: {str(e)}")
        raise

def test_email_config(config):
    """测试邮箱配置是否有效（仅测试连接）
    
    Args:
        config: 邮箱配置对象
    
    Returns:
        成功返回(True, "连接成功")，失败返回(False, "错误信息")
    """
    try:
        if not config:
            return False, "邮箱配置不能为空"
        
        # 检查必填字段
        if not config.get('host'):
            return False, "邮箱服务器地址不能为空"
        
        if not config.get('port'):
            return False, "邮箱服务器端口不能为空"
            
        if not config.get('username') or not config.get('password'):
            return False, "用户名和密码不能为空"
        
        # 创建连接并测试
        print("开始测试邮箱连接...")
        server = create_smtp_connection(config)
        server.quit()
        print("邮箱连接测试成功!")
        
        # 连接成功，提供测试邮件发送失败的可能原因和解决方案
        port = config.get('port')
        host = config.get('host').lower()
        
        # 构建邮箱服务器特定建议
        email_provider_tips = ""
        if "163.com" in host or "126.com" in host:
            email_provider_tips = "网易邮箱提示：请确认是否已开启POP3/SMTP服务，并使用授权码而非登录密码。"
        elif "qq.com" in host:
            email_provider_tips = "QQ邮箱提示：请确认是否已开启POP3/SMTP服务，并使用授权码而非QQ密码。"
        elif "gmail.com" in host:
            email_provider_tips = "Gmail提示：请确认是否已开启两步验证并创建应用专用密码。"
        elif "outlook.com" in host or "hotmail.com" in host:
            email_provider_tips = "Outlook提示：请确认是否已在Outlook安全设置中允许不太安全的应用访问。"
        elif "aliyun.com" in host:
            email_provider_tips = "阿里云邮箱提示：请确认是否已开启POP3/SMTP服务，并使用授权码。"
        
        # 构建端口特定建议
        port_tips = ""
        if port == 465:
            port_tips = "465端口要求使用SSL加密，请确认已启用SSL选项。"
        elif port == 587:
            port_tips = "587端口通常使用STARTTLS，请尝试启用STARTTLS选项。"
        elif port == 25:
            port_tips = "25端口是非加密端口，许多ISP和企业网络可能阻止此端口，建议使用465(SSL)或587(STARTTLS)端口。"
        
        # 构建安全提示
        security_tips = "如果发送测试邮件失败，可能原因：\n" + \
                      "1. 邮箱密码可能输入错误或需要使用应用专用密码/授权码\n" + \
                      "2. 邮箱安全设置可能禁止第三方应用程序访问\n" + \
                      "3. 企业防火墙可能阻止了邮件发送\n" + \
                      "4. SSL/TLS配置可能不正确"
        
        # 组合提示信息
        success_msg = f"邮箱服务器连接成功!\n\n{email_provider_tips}\n{port_tips}\n\n{security_tips}"
        
        return True, success_msg
    except socket.timeout:
        err_msg = "连接邮箱服务器超时，请检查服务器地址和端口设置是否正确，网络连接是否正常"
        print(err_msg)
        return False, err_msg
    except ssl.SSLError as e:
        err_msg = f"SSL/TLS连接错误: {str(e)}，请检查SSL设置是否正确"
        print(err_msg)
        return False, err_msg
    except smtplib.SMTPAuthenticationError:
        err_msg = "认证失败，用户名或密码错误。请检查：\n" + \
                 "1. 密码是否正确\n" + \
                 "2. 是否需要使用应用专用密码/授权码而非登录密码\n" + \
                 "3. 邮箱是否已开启SMTP服务"
        print(err_msg)
        return False, err_msg
    except smtplib.SMTPConnectError as e:
        err_msg = f"无法连接到邮箱服务器: {str(e)}"
        print(err_msg)
        return False, err_msg
    except smtplib.SMTPException as e:
        err_msg = f"SMTP错误: {str(e)}"
        print(err_msg)
        return False, err_msg
    except Exception as e:
        err_msg = f"测试邮箱配置失败: {str(e)}"
        print(err_msg)
        return False, err_msg

# 注册邮件相关API路由
def register_email_api(app: FastAPI):
    @app.post('/api/mail/testConfig')
    async def test_config_api(request: Request, _: bool = Depends(admin_required)):
        """测试邮箱配置API - 接受完整的配置对象"""
        try:
            config = await request.json()
            if not config:
                raise HTTPException(status_code=400, detail={"code": 400, "message": "配置信息不能为空"})
            
            success, message = test_email_config(config)
            return {"code": 200 if success else 500, "message": message}
        except HTTPException:
            raise
        except Exception as e:
            print(f"测试邮箱配置API出错: {str(e)}")
            raise HTTPException(status_code=500, detail={"code": 500, "message": f"测试邮箱配置失败: {str(e)}"})

    # 添加与前端匹配的路由
    @app.post('/webInfo/testEmailConfig')
    async def test_email_config_alt(request: Request, _: bool = Depends(admin_required)):
        """测试邮箱配置（前端适配接口）"""
        print("接收到测试邮箱配置请求")
        try:
            config = await request.json()
            if not config:
                print("配置信息为空")
                raise HTTPException(status_code=400, detail={"code": 400, "message": "配置信息不能为空"})
            
            # 首先尝试调用Java后端的邮件测试接口
            try:
                # 获取测试邮箱地址
                # 优先使用URL参数中的testEmail，其次使用form中的testTo，最后使用自己的邮箱
                test_email = None
                if 'testEmail' in request.query_params and request.query_params.get('testEmail'):
                    test_email = request.query_params.get('testEmail')
                elif 'testTo' in config:
                    test_email = config.get('testTo')
                
                # 如果没有指定测试邮箱，默认使用配置中的用户名
                if not test_email:
                    test_email = config.get('username', '')
                
                # 修复配置中的布尔值，确保不为null
                # 端口465强制设置SSL=true
                if config.get('port') == 465:
                    config['ssl'] = True
                # 端口587强制设置starttls=true（仅当未明确设置时）
                elif config.get('port') == 587 and 'starttls' not in config:
                    config['starttls'] = True
                    
                # 确保必要的布尔属性不为null，但不覆盖已有值
                for key in ['ssl', 'starttls', 'auth', 'enabled', 'trustAllCerts']:
                    # 只在属性未设置或为null时应用默认值，保留false值
                    if key not in config or config[key] is None:
                        # 根据前端值添加默认值（保留前端的false值）
                        if key == 'ssl':
                            config[key] = False  # 默认SSL关闭
                        elif key == 'starttls':
                            config[key] = False  # 默认STARTTLS关闭
                        elif key == 'auth':
                            config[key] = True   # 默认认证开启
                        elif key == 'enabled':
                            config[key] = True   # 默认启用
                        elif key == 'trustAllCerts':
                            config[key] = False  # 默认不信任所有证书
                
                print(f"修正后的配置: {config}")
                
                # 构建测试数据
                test_data = {
                    "testEmail": test_email,
                    "config": config
                }
                
                # 使用完整的URL格式
                if BASE_BACKEND_URL.startswith("http"):
                    java_test_url = f"{BASE_BACKEND_URL}/api/mail/test"
                else:
                    java_test_url = f"http://{BASE_BACKEND_URL}/api/mail/test"
                    
                print(f"转发测试请求到Java后端: {java_test_url}")
                print(f"测试邮箱: {test_email}")
                
                # 发送请求到Java后端，使用更长的超时时间
                async with httpx.AsyncClient() as client:
                    response = await client.post(
                        java_test_url, 
                        json=test_data,
                        headers={"Content-Type": "application/json"},
                        timeout=60  # 延长超时时间到60秒
                    )
                
                print(f"Java后端响应状态码: {response.status_code}")
                
                # 尝试解析响应
                try:
                    java_result = response.json()
                    print(f"Java后端测试结果: {java_result}")
                    
                    # 检查Java返回的结果
                    if java_result.get("code") == 200:
                        return {"code": 200, "message": "邮箱配置测试成功，测试邮件已发送"}
                    else:
                        error_msg = java_result.get('message', '未知错误')
                        print(f"Java返回错误: {error_msg}")
                        
                        # 构建更详细的错误消息
                        detailed_error = "测试邮件发送失败: " + error_msg + "\n\n"
                        
                        # 根据常见错误提供建议
                        if "Authentication failed" in error_msg or "认证失败" in error_msg:
                            detailed_error += "● 可能原因：\n" + \
                                            "1. 邮箱密码错误\n" + \
                                            "2. 需要使用应用专用密码/授权码而非登录密码\n" + \
                                            "3. 邮箱未开启SMTP服务\n\n" + \
                                            "● 解决方案：\n" + \
                                            "- 企业邮箱请联系IT管理员获取正确的SMTP设置\n" + \
                                            "- 个人邮箱请登录邮箱网页版，在设置中开启SMTP服务并获取授权码"
                        elif "连接超时" in error_msg or "timeout" in error_msg.lower():
                            detailed_error += "● 可能原因：\n" + \
                                            "1. 服务器地址或端口错误\n" + \
                                            "2. 网络环境阻止了SMTP连接\n" + \
                                            "3. 邮箱服务器暂时不可用\n\n" + \
                                            "● 解决方案：\n" + \
                                            "- 确认服务器地址和端口是否正确\n" + \
                                            "- 检查防火墙是否阻止了SMTP连接\n" + \
                                            "- 稍后再试"
                        elif "ssl" in error_msg.lower() or "tls" in error_msg.lower():
                            detailed_error += "● 可能原因：\n" + \
                                            "1. SSL/TLS设置错误\n" + \
                                            "2. 端口与加密方式不匹配\n\n" + \
                                            "● 解决方案：\n" + \
                                            "- 端口465应使用SSL加密\n" + \
                                            "- 端口587应使用STARTTLS\n" + \
                                            "- 端口25通常不使用加密（不推荐）"
                        
                        return {
                            "code": 500, 
                            "message": detailed_error
                        }
                except ValueError as e:
                    print(f"解析Java响应JSON失败: {str(e)}, 响应内容: {response.text[:200]}")
                    return {
                        "code": 500,
                        "message": f"无法理解Java响应: {response.text[:100]}..."
                    }
                    
            except httpx.ConnectError as e:
                print(f"连接Java后端失败: {str(e)}")
                print(f"Java后端URL: {BASE_BACKEND_URL}")
                # 如果Java端连接失败，使用本地测试连接
                print("使用本地SMTP连接测试作为备选方案...")
            except httpx.TimeoutException as e:
                print(f"调用Java后端超时: {str(e)}")
                print("使用本地SMTP连接测试作为备选方案...")
            except Exception as e:
                print(f"调用Java后端测试接口失败: {str(e)}")
                print("使用本地SMTP连接测试作为备选方案...")
            
            # 备选方案：本地测试SMTP连接
            success, message = test_email_config(config)
            code = 200 if success else 500
            
            # 对于超时错误，使用特定错误码以便前端可以提供更细致的错误信息
            if not success and "超时" in message:
                code = 504  # Gateway Timeout
            
            if success:
                message = "邮箱服务器连接成功，但未发送测试邮件。若要验证完整功能，请在Java端检查日志获取详细错误信息。连接成功不代表邮件一定能发送成功，可能仍需要正确配置授权码或应用专用密码。"
            
            print(f"本地测试结果: {'成功' if success else '失败'}, 消息: {message}")
            return {"code": code, "message": message}
        except HTTPException:
            raise
        except Exception as e:
            error_message = f"测试邮箱配置失败: {str(e)}"
            print(error_message)
            raise HTTPException(status_code=500, detail={"code": 500, "message": error_message})

    @app.get('/api/debug/config')
    async def debug_config():
        """调试配置信息API"""
        config_info = {
            "java_backend_url": BASE_BACKEND_URL,
            "java_config_url": JAVA_CONFIG_URL,
            "mail_test_url": f"{BASE_BACKEND_URL}/api/mail/test"
        }
        
        print(f"调试配置信息: {config_info}")
        
        # 尝试测试连接Java后端
        try:
            test_url = f"{BASE_BACKEND_URL}/api/mail/test"
            async with httpx.AsyncClient() as client:
                response = await client.get(test_url, timeout=2)
            config_info["java_connection_test"] = {
                "status_code": response.status_code,
                "reachable": True,
                "response": response.text[:100] if response.text else ""
            }
        except Exception as e:
            config_info["java_connection_test"] = {
                "error": str(e),
                "reachable": False
            }
        
        return {"code": 200, "message": "调试配置信息", "data": config_info}

    @app.post('/api/debug/setBackendUrl')
    async def set_backend_url(request: Request):
        """手动设置Java后端URL（仅用于调试）"""
        try:
            data = await request.json()
            if not data or 'url' not in data:
                raise HTTPException(status_code=400, detail={"code": 400, "message": "请提供url参数", "data": None})
            
            new_url = data['url']
            if not new_url.startswith('http'):
                new_url = f"http://{new_url}"
            
            # 临时修改全局变量
            global BASE_BACKEND_URL
            BASE_BACKEND_URL = new_url
            
            # 验证连接
            test_result = {}
            try:
                test_url = f"{BASE_BACKEND_URL}/api/mail/test"
                async with httpx.AsyncClient() as client:
                    response = await client.get(test_url, timeout=2)
                test_result = {
                    "status_code": response.status_code,
                    "reachable": True
                }
                message = f"成功设置Java后端URL为: {new_url}，连接测试成功"
            except Exception as e:
                test_result = {
                    "error": str(e),
                    "reachable": False
                }
                message = f"已设置Java后端URL为: {new_url}，但连接测试失败: {str(e)}"
            
            print(message)
            return {
                "code": 200, 
                "message": message,
                "data": {
                    "url": new_url,
                    "test": test_result
                }
            }
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"设置失败: {str(e)}",
                "data": None
            }) 