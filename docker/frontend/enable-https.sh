#!/bin/bash

# 检查证书是否存在
if [ -d "/etc/letsencrypt/live/example.com" ] && \
   [ -f "/etc/letsencrypt/live/example.com/fullchain.pem" ] && \
   [ -f "/etc/letsencrypt/live/example.com/privkey.pem" ]; then
    echo "找到SSL证书，启用HTTPS配置..."
    
    # 复制HTTPS配置文件替换当前配置
    cp /etc/nginx/conf.d/default.https.conf /etc/nginx/conf.d/default.conf
    
    # 重新加载Nginx配置
    nginx -s reload
    
    echo "HTTPS已启用！"
else
    echo "错误：找不到SSL证书文件。请确保certbot已成功运行。"
    exit 1
fi 