# ========================================
# 阶段 1: 构建阶段 - 编译前端代码
# ========================================
FROM node:24-bookworm-slim AS builder

# 声明构建时环境变量
ARG VUE_APP_POETIZE_AES_KEY
ARG VUE_APP_PRODUCTION_MODE
ARG VITE_APP_VERSION=dev
ENV VUE_APP_POETIZE_AES_KEY=$VUE_APP_POETIZE_AES_KEY
ENV VUE_APP_PRODUCTION_MODE=$VUE_APP_PRODUCTION_MODE
ENV VITE_APP_VERSION=$VITE_APP_VERSION
# 为 Vite 增加 VITE_ 前缀环境变量映射（变量名必须与 constant.js 中一致）
ENV VITE_POETIZE_AES_KEY=$VUE_APP_POETIZE_AES_KEY
ENV VITE_PRODUCTION_MODE=$VUE_APP_PRODUCTION_MODE

# 安装必要的系统依赖
RUN apt-get update && \
    apt-get install -y git curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 配置 npm 镜像源（加速构建）
RUN npm config set registry https://registry.npmmirror.com && \
    npm config set fetch-retry-mintimeout 20000 && \
    npm config set fetch-retry-maxtimeout 120000 && \
    npm config set fetch-retries 5

# 先复制 package.json 和 package-lock.json（利用 Docker 缓存层）
COPY poetize-web/package*.json ./

# 复制 public 目录结构
COPY poetize-web/public ./public

# 安装依赖
RUN echo "开始安装依赖..." && \
    npm cache clean --force || true && \
    (npm install --force --legacy-peer-deps --no-fund --no-audit --prefer-offline --maxsockets 1 --network-timeout=600000 || \
     npm install --force --legacy-peer-deps --no-fund --no-audit --maxsockets 1 --network-timeout=600000) && \
    echo "依赖安装完成"

# 安装全局工具（Brotli 压缩）
RUN npm install -g brotli --no-fund --no-audit --maxsockets 1 || echo '警告: brotli 安装失败，将跳过 .br 文件生成'

# 复制所有源代码
COPY poetize-web/ ./

# 构建前端项目（限制内存使用以适应低配服务器）
RUN NODE_OPTIONS="--max-old-space-size=1536 --max-semi-space-size=64" npm run build

# 构建期校验 Live2D 运行库必须进入前端静态产物，避免线上把首页 HTML 当 JS 返回。
RUN test -s dist/static/live2d-widget/dist/live2d.min.js && \
    test -s dist/static/live2d-widget/dist/waifu.css

# ========================================
# 阶段 2: 运行阶段 - 只保留构建产物
# ========================================
FROM alpine:3.19 AS runtime

# 设置时区
ENV TZ=Asia/Shanghai

# 安装必要工具（用于复制文件到 volume）
RUN apk add --no-cache bash coreutils

# 创建工作目录
WORKDIR /app

# 从构建阶段复制构建产物到临时目录
COPY --from=builder /app/dist /build/dist

# 启动脚本：将构建产物从镜像复制到 volume 挂载点
# volume 挂载在 /app/dist，需要在启动时复制文件
CMD ["sh", "-c", "\
    echo '=== 博客前端部署：复制构建产物到 volume ===' && \
    mkdir -p /app/dist && \
    rm -rf /app/dist/prerender && \
    find /app/dist -maxdepth 1 -type f -name '*.gz' -delete && \
    cp -a /build/dist/. /app/dist/ && \
    test -s /app/dist/static/live2d-widget/dist/live2d.min.js && \
    test -s /app/dist/static/live2d-widget/dist/waifu.css && \
    echo '=== 静态文件部署完成 ===' && \
    touch /app/dist/.ui_ready && \
    echo '=== 博客前端启动成功 ===' \
"]
