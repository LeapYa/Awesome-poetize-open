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
# 2. 优先使用 docker/java/cn-font-split-assets 中随构建上下文携带的 wasm 二进制
# 3. 本地 wasm 不存在时，才从 GitHub Release 下载指定版本产物并校验 SHA256
# 4. 如需 GitHub 代理可传入 --build-arg CN_FONT_SPLIT_GH_HOST=https://your-mirror
# 5. 下载失败会自动尝试备用源，可用 CN_FONT_SPLIT_GH_FALLBACK_HOSTS 覆盖（空格分隔）
ARG CN_FONT_SPLIT_VERSION=7.6.8
ARG CN_FONT_SPLIT_WASM_SHA256=05a88dcb9a0b0d1e14daf0f429d9af6e2ac8d94d9e574523a76d3e9f440dccc9
ARG CN_FONT_SPLIT_GH_HOST=
ARG CN_FONT_SPLIT_GH_FALLBACK_HOSTS="https://gh-proxy.com/github.com https://ghfast.top/https://github.com https://github.com"
ENV CN_FONT_SPLIT_MODULE_PATH=/opt/cn-font-split-runtime/node_modules/cn-font-split/dist/wasm/index.mjs
ENV CN_FONT_SPLIT_WASM_PATH=/opt/cn-font-split-runtime/node_modules/cn-font-split/dist/libffi-wasm32-wasip1.wasm

COPY docker/java/cn-font-split-assets/ /tmp/cn-font-split-assets/

# 安装健康检查所需工具、媒体转换器、WebP 支持库、Node.js 和 cn-font-split
RUN apk add --no-cache netcat-openbsd curl ffmpeg libwebp libwebp-tools nodejs npm ca-certificates bash \
        && npm config set registry https://registry.npmmirror.com \
        && mkdir -p /opt/cn-font-split-runtime \
        # 1) 从 npm 安装 JS 包装代码（latest）
        && npm install --ignore-scripts --prefix /opt/cn-font-split-runtime cn-font-split \
        && cd /opt/cn-font-split-runtime/node_modules/cn-font-split \
        # 2) 从 GitHub Release 下载指定版本 wasm 二进制
        && WASM_ASSET_PATH="/KonghaYao/cn-font-split/releases/download/${CN_FONT_SPLIT_VERSION}/libffi-wasm32-wasip1.wasm" \
        && WASM_HOSTS="${CN_FONT_SPLIT_GH_HOST:-} ${CN_FONT_SPLIT_GH_FALLBACK_HOSTS}" \
        && rm -f dist/libffi-wasm32-wasip1.wasm dist/libffi-wasm32-wasip1.wasm.tmp \
        && wasm_downloaded=0 \
        && if [ -s /tmp/cn-font-split-assets/libffi-wasm32-wasip1.wasm ]; then \
             echo "Using bundled cn-font-split wasm binary"; \
             cp /tmp/cn-font-split-assets/libffi-wasm32-wasip1.wasm dist/libffi-wasm32-wasip1.wasm.tmp; \
             echo "${CN_FONT_SPLIT_WASM_SHA256}  dist/libffi-wasm32-wasip1.wasm.tmp" | sha256sum -c -; \
             mv dist/libffi-wasm32-wasip1.wasm.tmp dist/libffi-wasm32-wasip1.wasm; \
             wasm_downloaded=1; \
           else \
             for WASM_GH_BASE in ${WASM_HOSTS}; do \
               [ -n "$WASM_GH_BASE" ] || continue; \
               WASM_URL="${WASM_GH_BASE}${WASM_ASSET_PATH}"; \
               echo "Downloading wasm binary from ${WASM_URL} ..."; \
               if curl -fL --retry 8 --retry-all-errors --retry-delay 5 --connect-timeout 15 --max-time 300 --speed-limit 1024 --speed-time 30 \
                    -o dist/libffi-wasm32-wasip1.wasm.tmp "$WASM_URL" \
                  && test -s dist/libffi-wasm32-wasip1.wasm.tmp \
                  && echo "${CN_FONT_SPLIT_WASM_SHA256}  dist/libffi-wasm32-wasip1.wasm.tmp" | sha256sum -c -; then \
                 mv dist/libffi-wasm32-wasip1.wasm.tmp dist/libffi-wasm32-wasip1.wasm; \
                 wasm_downloaded=1; \
                 break; \
               fi; \
               echo "WARN: download failed or checksum mismatch from ${WASM_GH_BASE}, trying next mirror ..." >&2; \
               rm -f dist/libffi-wasm32-wasip1.wasm.tmp; \
             done; \
           fi \
        && if [ "$wasm_downloaded" != "1" ]; then \
             echo "ERROR: failed to download cn-font-split wasm from all configured hosts" >&2; \
             exit 1; \
           fi \
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
