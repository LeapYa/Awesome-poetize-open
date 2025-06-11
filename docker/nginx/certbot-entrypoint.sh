#!/bin/sh
# certbot入口脚本 - 处理证书申请和自动续期

# 设置变量（这些值将在部署时由deploy.sh脚本替换）
WEBROOT_PATH="/usr/share/nginx/html"

# 打印信息
echo "==== Certbot 证书管理服务启动 ===="
echo "Web根目录: $WEBROOT_PATH"

# 首次申请证书
echo "开始申请证书..."
echo "执行: certbot certonly --webroot --webroot-path=$WEBROOT_PATH --email your-email@example.com --agree-tos --no-eff-email --force-renewal --expand -d example.com -d www.example.com"
certbot certonly --webroot \
  --webroot-path=$WEBROOT_PATH \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email \
  --force-renewal \
  --expand \
  -d example.com -d www.example.com

# 检查证书申请结果
if [ $? -eq 0 ]; then
  echo "证书申请成功!"
else
  echo "证书申请失败，将继续尝试自动续期..."
fi

# 设置自动续期
echo "设置自动续期..."
trap exit TERM

# 自动续期循环
while :; do
  echo "检查证书续期..."
  certbot renew --quiet
  
  # 休眠12小时
  echo "证书检查完成，12小时后再次检查..."
  sleep 12h
done 