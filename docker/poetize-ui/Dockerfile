FROM node:16-alpine

# 安装构建依赖
RUN apk add --no-cache bash git

# 设置工作目录
WORKDIR /app

# 设置环境变量以支持老版本OpenSSL
ENV NODE_OPTIONS="--openssl-legacy-provider"

# 构建命令
CMD ["sh", "-c", "if [ ! -d \"dist\" ]; then \
                    echo '=== 构建主站前端 (Vue 2) ===' && \
                    npm install --force --legacy-peer-deps && \
                    npm run build && \
                    echo '主站构建完成' && \
                    echo '=== 确保静态资源正确放置 ===' && \
                    mkdir -p dist/static && \
                    cp -rf public/static/* dist/static/ 2>/dev/null || true; \
                  else \
                    echo '=== 检测到已构建的主站文件，跳过构建步骤 ==='; \
                  fi"] 