FROM node:18-alpine

# 安装构建依赖
RUN apk add --no-cache bash git

# 设置工作目录
WORKDIR /app

# 构建命令
CMD ["sh", "-c", "if [ ! -d \"dist\" ]; then \
                    echo '=== 构建聊天室前端 (Vue 3) ===' && \
                    npm install && \
                    npm run build && \
                    echo '聊天室构建完成'; \
                  else \
                    echo '=== 检测到已构建的聊天室文件，跳过构建步骤 ==='; \
                  fi"] 