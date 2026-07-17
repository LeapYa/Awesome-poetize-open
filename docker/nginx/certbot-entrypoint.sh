#!/bin/sh
# certbot入口脚本 - 处理证书申请和自动续期

# 设置变量（这些值将在部署时由deploy.sh脚本替换）
WEBROOT_PATH="/usr/share/nginx/html"
# 证书的 cert-name（也是 /etc/letsencrypt/live/ 下的目录名）
# 兼容设计：deploy.sh / poetize install 会通过 sed 把 example.com 替换为实际域名
# 旧版本部署脚本若未替换此变量，entrypoint 会自动从 certbot certificates 推断（见下方 fallback）
CERT_NAME="example.com"

# 兼容旧版本部署脚本：若 CERT_NAME 仍为占位 example.com，则尝试从已存在的证书目录推断实际域名
# 这样新版本 template 即使被旧版本 poetize 部署，也能正确识别已有证书并跳过重复申请
# 安全策略：仅当 live/ 下只有一个证书目录时才推断（多个目录可能是换域名场景，保守地走申请流程）
if [ "$CERT_NAME" = "example.com" ] && [ -d /etc/letsencrypt/live ]; then
  live_dir_count=$(find /etc/letsencrypt/live -maxdepth 1 -mindepth 1 -type d 2>/dev/null | wc -l)
  if [ "$live_dir_count" = "1" ]; then
    for dir in /etc/letsencrypt/live/*/; do
      [ -d "$dir" ] || continue
      if [ -f "${dir}fullchain.pem" ]; then
        CERT_NAME=$(basename "$dir")
        echo "CERT_NAME 未被部署脚本替换，已从已有证书推断为: $CERT_NAME"
        break
      fi
    done
  else
    echo "CERT_NAME 未被替换且 live/ 下有 $live_dir_count 个证书目录（可能是换域名场景），保守地走申请流程"
  fi
fi

# 权限修复函数
fix_cert_permissions() {
  echo "设置证书文件权限，让nginx用户(UID 101)能够读取..."
  
  # 修复.well-known目录权限，确保certbot可以写入ACME验证文件
  echo "修复.well-known目录权限..."
  mkdir -p "$WEBROOT_PATH/.well-known/acme-challenge"
  chmod -R 777 "$WEBROOT_PATH/.well-known"
  echo "ACME验证目录权限设置为777，确保certbot和nginx都可以访问"
  
  if [ -d "/etc/letsencrypt/live" ]; then
    # 设置目录权限为755，让nginx用户可以进入目录
    find /etc/letsencrypt/live -type d -exec chmod 755 {} \;
    find /etc/letsencrypt/archive -type d -exec chmod 755 {} \; 2>/dev/null || true
    
    # 设置证书文件权限为644，让nginx用户可读
    find /etc/letsencrypt/live -name "*.pem" -exec chmod 644 {} \;
    find /etc/letsencrypt/archive -name "*.pem" -exec chmod 644 {} \; 2>/dev/null || true
    
    # 设置关键目录权限
    chmod 755 /etc/letsencrypt/live
    chmod 755 /etc/letsencrypt/archive 2>/dev/null || true
    
    echo "证书文件权限设置完成"
  else
    echo "警告: 证书目录不存在，跳过权限设置"
  fi
}

# 打印信息
echo "==== Certbot 证书管理服务启动 ===="
echo "Web根目录: $WEBROOT_PATH"

# 预先修复权限，确保ACME验证可以正常进行
echo "预先修复.well-known目录权限..."
mkdir -p "$WEBROOT_PATH/.well-known/acme-challenge"
chmod -R 777 "$WEBROOT_PATH/.well-known"
echo "ACME验证目录权限预处理完成"

# 网络连通性检查函数
check_network() {
  echo "检查网络连通性..."
  if ping -c 1 8.8.8.8 >/dev/null 2>&1; then
    echo "网络连通性正常"
    return 0
  else
    echo "网络连通性检查失败"
    return 1
  fi
}

# DNS解析检查函数
check_dns() {
  local domain="$1"
  echo "检查域名 $domain 的DNS解析..."
  if nslookup "$domain" >/dev/null 2>&1; then
    echo "域名 $domain DNS解析正常"
    return 0
  else
    echo "域名 $domain DNS解析失败"
    return 1
  fi
}

# 证书申请重试函数
apply_certificate() {
  local max_retries=5
  local retry_count=0
  local wait_time=30
  
  while [ $retry_count -lt $max_retries ]; do
    retry_count=$((retry_count + 1))
    echo "第 $retry_count 次尝试申请证书..."
    
    # 在重试前进行网络和DNS检查
    if [ $retry_count -gt 1 ]; then
      echo "重试前进行网络诊断..."
      check_network
      check_dns "example.com"
      echo "网络诊断完成，继续申请证书..."
    fi
    
    echo "执行: certbot certonly --webroot --webroot-path=$WEBROOT_PATH --email your-email@example.com --agree-tos --no-eff-email --force-renewal --expand -d example.com -d www.example.com"

    # 执行certbot命令
    # --cert-name 保持 example.com 占位，由 deploy.sh / poetize install 用 sed 替换为实际域名
    # 旧版本部署脚本若未替换，apply 时 certbot 会用第一个 -d 参数作为 cert-name（行为一致）
    certbot certonly --webroot \
      --webroot-path=$WEBROOT_PATH \
      --email your-email@example.com \
      --cert-name example.com \
      --agree-tos \
      --no-eff-email \
      --force-renewal \
      --expand \
      -d example.com -d www.example.com 2>&1
    
    # 保存命令执行结果
    cert_result=$?
    
    # 检查证书申请结果
    if [ $cert_result -eq 0 ]; then
      echo "证书申请成功!"
      fix_cert_permissions
      return 0
    else
      echo "第 $retry_count 次证书申请失败 (退出码: $cert_result)"
      
      # 只在失败时记录详细的错误日志，避免影响正常的docker logs输出
      echo "记录详细错误信息到日志文件..."
      echo "=== Certbot 第 $retry_count 次尝试失败 ($(date)) ===" >> /tmp/certbot-error.log
      echo "退出码: $cert_result" >> /tmp/certbot-error.log
      
      # 重新执行一次带--verbose的命令来获取详细错误信息（仅用于日志记录）
      echo "获取详细错误信息..." >> /tmp/certbot-error.log
      certbot certonly --webroot \
        --webroot-path=$WEBROOT_PATH \
        --email your-email@example.com \
        --cert-name example.com \
        --agree-tos \
        --no-eff-email \
        --force-renewal \
        --expand \
        -d example.com -d www.example.com \
        --verbose --dry-run 2>&1 >> /tmp/certbot-error.log
      echo "" >> /tmp/certbot-error.log
      
      if [ $retry_count -lt $max_retries ]; then
        echo "等待 $wait_time 秒后重试..."
        sleep $wait_time
        # 每次重试增加等待时间（指数退避）
        wait_time=$((wait_time * 2))
      else
        echo "已达到最大重试次数 ($max_retries)，证书申请失败"
        echo "详细错误信息已记录到: /tmp/certbot-error.log"
        echo "可以通过以下命令查看错误日志:"
        echo "  docker exec <container_name> cat /tmp/certbot-error.log"
        return 1
      fi
    fi
  done
}

# 首次申请证书（已存在当前域名的证书则跳过，避免触发 Let's Encrypt rate limit）
# 升级流程会把旧 certbot-etc 卷的证书恢复到新卷，此时容器启动应跳过重新申请
# 换域名场景下，旧域名证书虽在 live/ 目录里但 CERT_NAME 已变化，会精确匹配并触发新申请
if [ -f "/etc/letsencrypt/live/$CERT_NAME/fullchain.pem" ]; then
  echo "检测到当前域名 $CERT_NAME 的证书已存在，跳过首次申请..."
  fix_cert_permissions
else
  echo "未检测到当前域名 $CERT_NAME 的证书，开始申请..."
  apply_certificate

  if [ $? -ne 0 ]; then
    echo "证书申请最终失败，将继续尝试自动续期..."
  fi
fi

# 设置自动续期
echo "设置自动续期..."
trap exit TERM

# 证书续期重试函数
renew_certificate() {
  local max_retries=3
  local retry_count=0
  local wait_time=60
  
  while [ $retry_count -lt $max_retries ]; do
    retry_count=$((retry_count + 1))
    echo "第 $retry_count 次尝试续期证书..."
    
    certbot renew --quiet
    
    # 检查续期结果
    if [ $? -eq 0 ]; then
      echo "续期成功，修复证书文件权限..."
      fix_cert_permissions
      return 0
    else
      echo "第 $retry_count 次证书续期失败"
      if [ $retry_count -lt $max_retries ]; then
        echo "等待 $wait_time 秒后重试..."
        sleep $wait_time
        wait_time=$((wait_time * 2))
      else
        echo "证书续期达到最大重试次数，本次续期失败"
        return 1
      fi
    fi
  done
}

# 自动续期循环
while :; do
  echo "检查证书续期..."
  renew_certificate
  
  # 休眠12小时
  echo "证书检查完成，12小时后再次检查..."
  sleep 12h
done
