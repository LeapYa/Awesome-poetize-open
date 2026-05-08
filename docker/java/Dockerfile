# 使用Maven作为构建环境
FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /app

# 复制整个poetize-server目录到容器中
COPY ./poetize-server /app

# 在容器中运行Maven构建（启用Java 25预览特性）
RUN mvn -f /app/pom.xml clean package -DskipTests -Darguments="--enable-preview"

# 最终运行环境
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# cn-font-split 运行时配置：
# 1. 从 npm 安装 JS 包装代码（使用 npm 上可用的最新版本）
# 2. 从 GitHub Release 直接下载指定版本的 wasm 二进制产物（无需版本与 npm 完全一致）
# 3. 如需 GitHub 代理可传入 --build-arg CN_FONT_SPLIT_GH_HOST=https://your-mirror
ARG CN_FONT_SPLIT_VERSION=7.6.8
ARG CN_FONT_SPLIT_GH_HOST=
ENV CN_FONT_SPLIT_MODULE_PATH=/opt/cn-font-split-runtime/node_modules/cn-font-split/dist/wasm/index.mjs
ENV CN_FONT_SPLIT_WASM_PATH=/opt/cn-font-split-runtime/node_modules/cn-font-split/dist/libffi-wasm32-wasip1.wasm

# 安装健康检查所需工具、媒体转换器、WebP 支持库、Node.js 和 cn-font-split
RUN apk add --no-cache netcat-openbsd curl ffmpeg libwebp libwebp-tools nodejs npm ca-certificates bash \
        && npm config set registry https://registry.npmmirror.com \
        && mkdir -p /opt/cn-font-split-runtime \
        # 1) 从 npm 安装 JS 包装代码（latest）
        && npm install --ignore-scripts --prefix /opt/cn-font-split-runtime cn-font-split \
        && cd /opt/cn-font-split-runtime/node_modules/cn-font-split \
        # 2) 从 GitHub Release 下载指定版本 wasm 二进制
        && WASM_GH_BASE="${CN_FONT_SPLIT_GH_HOST:-https://github.com}" \
        && echo "Downloading wasm binary from GitHub Release ${CN_FONT_SPLIT_VERSION} ..." \
        && curl -fsSL --retry 3 --retry-delay 5 -o dist/libffi-wasm32-wasip1.wasm \
           "${WASM_GH_BASE}/KonghaYao/cn-font-split/releases/download/${CN_FONT_SPLIT_VERSION}/libffi-wasm32-wasip1.wasm" \
        # 3) 验证安装结果
        && test -s dist/wasm/index.mjs \
        && test -s dist/libffi-wasm32-wasip1.wasm \
        && node --input-type=module -e " \
             const m = await import('/opt/cn-font-split-runtime/node_modules/cn-font-split/dist/wasm/index.mjs'); \
             if (typeof m.fontSplit !== 'function' || typeof m.StaticWasm !== 'function') \
               throw new Error('cn-font-split wasm exports invalid'); \
           " \
        && cd / && npm cache clean --force && rm -rf /tmp/* /root/.npm

# 从构建阶段复制JAR文件
COPY --from=builder /app/poetry-web/target/poetize-server-exec.jar /app/app.jar

# 暴露端口
EXPOSE 8081

# 创建健康检查脚本
RUN echo '#!/bin/sh\nset -e\nif nc -z localhost 8081; then\n  curl -f http://localhost:8081/actuator/health || exit 1\n  exit 0\nfi\nexit 1' > /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh

# 启动命令
CMD ["sh", "-c", "ls -la /app && java --enable-preview $JAVA_OPTS -jar /app/app.jar"]
