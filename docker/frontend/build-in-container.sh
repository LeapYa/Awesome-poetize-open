#!/bin/bash
set -e

echo "=== 第一次启动，开始构建前端 ==="

# 配置npm使用淘宝镜像源并增加网络参数
echo "=== 配置npm使用淘宝镜像源 ==="
npm config set registry https://registry.npmmirror.com
npm config set fetch-retries 5
npm config set fetch-retry-mintimeout 20000
npm config set fetch-retry-maxtimeout 120000
npm config set fetch-timeout 300000

# 清除可能有问题的NODE_OPTIONS
unset NODE_OPTIONS

if [ -d "/usr/share/nginx/html/poetize/dist" ] && [ -d "/usr/share/nginx/html/im/dist" ]; then
  echo "=== 检测到已构建的前端文件，跳过构建步骤 ==="
else
  # 安装必要的工具
  echo "=== 安装构建工具 ==="
  apk add --no-cache gcc g++ make python3 git
  
  # 检测Node.js版本
  NODE_VERSION=$(node -v)
  echo "检测到Node.js版本: $NODE_VERSION"
  echo "NPM版本: $(npm -v)"
  
  # 构建主站（Vue 2）
  if [ ! -d "/usr/share/nginx/html/poetize/dist" ]; then
    echo "=== 构建主站前端 (Vue 2) ==="
    cd /usr/share/nginx/html/poetize
    
    echo "安装主站依赖..."
    # 最多尝试3次安装依赖
    MAX_RETRIES=3
    RETRY_COUNT=0
    
    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
      echo "尝试安装主站依赖 (尝试 $((RETRY_COUNT+1))/$MAX_RETRIES)..."
      if npm install --force --legacy-peer-deps --no-fund --no-audit; then
        echo "主站依赖安装成功！"
        break
      else
        RETRY_COUNT=$((RETRY_COUNT+1))
        if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
          echo "安装失败，将在10秒后重试..."
          sleep 10
        else
          echo "达到最大重试次数，安装失败"
          exit 1
        fi
      fi
    done
    
    echo "创建自定义构建脚本..."
    cat > build-custom.js << 'EOF'
const { execSync } = require('child_process');
const path = require('path');

console.log('开始自定义构建过程...');

// 设置环境变量
process.env.NODE_ENV = 'production';

try {
  // 直接执行vue-cli-service构建命令
  const vueCliPath = path.resolve('./node_modules/.bin/vue-cli-service');
  console.log(`使用自定义脚本执行vue-cli-service: ${vueCliPath}`);
  
  // 执行构建命令
  execSync(`${vueCliPath} build`, { 
    env: { ...process.env, NODE_ENV: 'production' },
    stdio: 'inherit'
  });
  
  console.log('构建成功完成!');
} catch (error) {
  console.error('构建过程中发生错误:', error);
  process.exit(1);
}
EOF
    
    echo "执行自定义构建脚本..."
    node build-custom.js
    
    echo "主站构建完成"
  fi
  
  # 构建聊天室（Vue 3）
  if [ ! -d "/usr/share/nginx/html/im/dist" ]; then
    echo "=== 构建聊天室前端 (Vue 3) ==="
    cd /usr/share/nginx/html/im
    
    echo "Node.js版本: $(node -v)"
    echo "NPM版本: $(npm -v)"
    
    echo "安装聊天室依赖..."
    # 最多尝试3次安装依赖
    MAX_RETRIES=3
    RETRY_COUNT=0
    
    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
      echo "尝试安装聊天室依赖 (尝试 $((RETRY_COUNT+1))/$MAX_RETRIES)..."
      if npm install --no-fund --no-audit; then
        echo "聊天室依赖安装成功！"
        break
      else
        RETRY_COUNT=$((RETRY_COUNT+1))
        if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
          echo "安装失败，将在10秒后重试..."
          sleep 10
        else
          echo "达到最大重试次数，安装失败"
          exit 1
        fi
      fi
    done
    
    echo "创建自定义构建脚本..."
    cat > build-custom.js << 'EOF'
const { execSync } = require('child_process');
const path = require('path');

console.log('开始自定义构建过程...');

// 设置环境变量
process.env.NODE_ENV = 'production';

try {
  // 直接执行vue-cli-service构建命令
  const vueCliPath = path.resolve('./node_modules/.bin/vue-cli-service');
  console.log(`使用自定义脚本执行vue-cli-service: ${vueCliPath}`);
  
  // 执行构建命令
  execSync(`${vueCliPath} build`, { 
    env: { ...process.env, NODE_ENV: 'production' },
    stdio: 'inherit'
  });
  
  console.log('构建成功完成!');
} catch (error) {
  console.error('构建过程中发生错误:', error);
  process.exit(1);
}
EOF
    
    echo "执行自定义构建脚本..."
    node build-custom.js
    
    echo "聊天室构建完成"
  fi
fi

# 检查构建文件位置
echo "=== 检查构建文件位置 ==="
ls -la /usr/share/nginx/html/poetize/
ls -la /usr/share/nginx/html/poetize/dist/

# 启动Nginx
echo "=== 启动Nginx ==="
nginx -g "daemon off;"