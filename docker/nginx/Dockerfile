FROM openresty/openresty:1.29.2.3-alpine AS builder
# 国内镜像会根据行号来添加镜像加速，第3行和第139行会添加镜像加速，如果这里修改了，记得修改国内镜像的行号

RUN apk update

# 安装构建依赖
RUN apk add --no-cache \
    build-base \
    git \
    cmake \
    brotli-dev \
    pcre-dev \
    zlib-dev \
    openssl-dev \
    curl \
    wget \
    busybox-extras

# 下载ngx_brotli模块
WORKDIR /usr/src
RUN set -e; \
    echo "🔧 尝试下载ngx_brotli模块..."; \
    # 首先尝试GitHub官方
    if timeout -s TERM 240 \
        git clone --depth=1 --shallow-submodules --recursive https://github.com/google/ngx_brotli.git; then \
        echo "✅ 从GitHub下载ngx_brotli成功"; \
    # 如果GitHub失败，尝试AtomGit国内镜像
    elif timeout -s TERM 240 \
        rm -rf ngx_brotli && \
        git clone --depth=1 https://atomgit.com/gh_mirrors/ng/ngx_brotli.git; then \
        echo "🔄 AtomGit 主仓库成功，开始处理子模块…"; \
        cd ngx_brotli && \
        # 把子模块地址改到 AtomGit 镜像
        git submodule set-url deps/brotli https://atomgit.com/gh_mirrors/brotl/brotli.git && \
        # 浅克隆子模块
        git submodule update --init --depth=1 --recommend-shallow && \
        echo "✅ 子模块 brotli 已就位"; \
        cd ..; \
    else \
        echo "❌ 所有ngx_brotli下载方法都失败，请检查网络连接"; \
        exit 1; \
    fi

# 获取Nginx版本
RUN nginx -v 2>&1 | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' > /tmp/nginx_version

# 编译Brotli模块
WORKDIR /usr/src/ngx_brotli
RUN set -e; \
    if [ -f "config" ]; then \
        echo "🔧 开始编译Brotli模块..."; \
        NGINX_VERSION=$(cat /tmp/nginx_version); \
        # 尝试多个Nginx下载源
        if wget --timeout=120 --tries=3 https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz; then \
            echo "✅ 从nginx.org下载成功"; \
        elif wget --timeout=120 --tries=3 https://mirrors.huaweicloud.com/nginx/nginx-${NGINX_VERSION}.tar.gz; then \
            echo "✅ 从华为云下载成功，感谢newbe博客的整理"; \
        elif wget --timeout=120 --tries=3 https://mirror.azure.cn/nginx/download/nginx-${NGINX_VERSION}.tar.gz; then \
            echo "✅ 从Azure下载成功"; \
        elif wget --timeout=120 --tries=3 https://mirrors.sohu.com/nginx/nginx-${NGINX_VERSION}.tar.gz; then \
            echo "✅ 从搜狐下载成功"; \
        else \
            echo "❌ Nginx源码下载失败，请检查网络连接"; \
            exit 1; \
        fi; \
        tar -xzf nginx-${NGINX_VERSION}.tar.gz && \
        cd nginx-${NGINX_VERSION} && \
        ./configure --with-compat --add-dynamic-module=/usr/src/ngx_brotli && \
        make modules && \
        mkdir -p /usr/local/openresty/nginx/modules/ && \
        cp objs/ngx_http_brotli_filter_module.so /usr/local/openresty/nginx/modules/ 2>/dev/null || true && \
        cp objs/ngx_http_brotli_static_module.so /usr/local/openresty/nginx/modules/ 2>/dev/null || true; \
        echo "🎉 Brotli模块编译完成"; \
    else \
        echo "⏭️ ngx_brotli未正确下载"; \
        exit 1; \
    fi

# ===== 已停用：Lua 运行库安装留档 =====
# 当前 Nginx/SEO 链路已经迁移到后端预渲染 + Nginx 原生 try_files，不再执行 Lua 脚本。
# 下面这段是历史上为 OpenResty Lua SEO fallback 准备的依赖安装逻辑，先保留为注释，便于以后参考或回退。
#
# # 在构建阶段安装lua模块
# WORKDIR /usr/src
#
# # 安装lua-resty-openssl
# RUN set -e; \
#     echo "🔧 尝试安装lua-resty-openssl..."; \
#     # 首先尝试GitHub
#     if timeout -s TERM 240 \
#         git clone --depth=1 https://github.com/fffonion/lua-resty-openssl.git; then \
#         echo "✅ 从GitHub下载lua-resty-openssl成功"; \
#     # 如果GitHub失败，尝试Gitee
#     elif timeout -s TERM 240 \
#         rm -rf lua-resty-openssl && \
#         git clone --depth=1 https://gitee.com/mirrors_fffonion/lua-resty-openssl.git; then \
#         echo "🔄 从Gitee下载lua-resty-openssl成功"; \
#     # 如果都失败，尝试直接下载
#     elif wget -O lua-resty-openssl.tar.gz https://github.com/fffonion/lua-resty-openssl/archive/refs/heads/master.tar.gz && \
#          tar -xzf lua-resty-openssl.tar.gz && mv lua-resty-openssl-master lua-resty-openssl; then \
#         echo "🔄 从tar包下载lua-resty-openssl成功"; \
#     else \
#         echo "❌ lua-resty-openssl下载失败"; \
#         exit 1; \
#     fi; \
#     mkdir -p /usr/local/openresty/lualib/resty/openssl && \
#     cd lua-resty-openssl && \
#     cp -r lib/resty/openssl/* /usr/local/openresty/lualib/resty/openssl/ 2>/dev/null || true && \
#     cp lib/resty/openssl.lua /usr/local/openresty/lualib/resty/ 2>/dev/null || true; \
#     echo "✅ lua-resty-openssl安装完成"
#
# # 安装lua-resty-http模块
# RUN set -e; \
#     mkdir -p /usr/local/openresty/lualib/resty; \
#     echo "🔧 尝试安装lua-resty-http..."; \
#     # 首先尝试GitHub
#     if timeout -s TERM 240 \
#         git clone --depth=1 https://github.com/ledgetech/lua-resty-http.git; then \
#         echo "✅ 从GitHub下载lua-resty-http成功"; \
#     # 如果GitHub失败，尝试AtomGit国内镜像
#     elif timeout -s TERM 240 \
#         rm -rf lua-resty-http && \
#         git clone --depth=1 https://atomgit.com/gh_mirrors/lu/lua-resty-http.git; then \
#         echo "🔄 从AtomGit下载lua-resty-http成功"; \
#     # 如果都失败，尝试直接下载
#     elif wget -O lua-resty-http.tar.gz https://github.com/ledgetech/lua-resty-http/archive/refs/heads/master.tar.gz && \
#          tar -xzf lua-resty-http.tar.gz && mv lua-resty-http-master lua-resty-http; then \
#         echo "🔄 从tar包下载lua-resty-http成功"; \
#     else \
#         echo "❌ lua-resty-http下载失败"; \
#         exit 1; \
#     fi; \
#     cd lua-resty-http && \
#     cp lib/resty/http.lua /usr/local/openresty/lualib/resty/ 2>/dev/null || true && \
#     cp lib/resty/http_headers.lua /usr/local/openresty/lualib/resty/ 2>/dev/null || true && \
#     cp lib/resty/http_connect.lua /usr/local/openresty/lualib/resty/ 2>/dev/null || true && \
#     cp -r lib/ngx /usr/local/openresty/lualib/ 2>/dev/null || true; \
#     echo "✅ lua-resty-http安装完成"

# 最终镜像
FROM openresty/openresty:1.29.2.3-alpine

RUN apk update

# 只安装运行时必需的依赖
RUN apk add --no-cache \
    ca-certificates \
    brotli \
    pcre \
    zlib \
    openssl

# 创建nginx用户和组
RUN addgroup -g 101 -S nginx && \
    adduser -S -D -H -u 101 -h /var/cache/nginx -s /sbin/nologin -G nginx -g nginx nginx

# 创建必要的目录并设置权限
RUN mkdir -p /var/log/nginx /var/cache/nginx /var/run /tmp && \
    chown -R nginx:nginx /var/log/nginx /var/cache/nginx /var/run && \
    chmod -R 755 /var/log/nginx /var/cache/nginx /var/run && \
    chmod 777 /tmp

# 从builder中复制Brotli模块
COPY --from=builder /usr/local/openresty/nginx/modules/ /usr/local/openresty/nginx/modules/
# 已停用：Lua 运行库不再进入最终镜像，原复制命令留档。
# COPY --from=builder /usr/local/openresty/lualib/ /usr/local/openresty/lualib/

# 创建配置目录
RUN mkdir -p /usr/local/openresty/nginx/conf/conf.d

# 复制自定义配置
COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf

# 复制配置模板文件
COPY default.http.conf /usr/local/openresty/nginx/conf/conf.d/default.http.conf.template
COPY default.https.conf /usr/local/openresty/nginx/conf/conf.d/default.https.conf.template

# 在构建时就处理配置文件，默认使用HTTP配置
RUN cp /usr/local/openresty/nginx/conf/conf.d/default.http.conf.template \
       /usr/local/openresty/nginx/conf/conf.d/default.conf

# 生成默认自签名证书（用于拒绝IP直接访问HTTPS的default_server块）
RUN mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout /etc/nginx/ssl/default.key \
        -out /etc/nginx/ssl/default.crt \
        -subj "/CN=_" && \
    chmod 644 /etc/nginx/ssl/default.crt /etc/nginx/ssl/default.key

# 已停用：Nginx 不再读取 /etc/nginx/lua 下的脚本，历史命令留档。
# mkdir -p /etc/nginx/lua && \
# chown nginx:nginx /etc/nginx/lua && \
# chmod 755 /etc/nginx/lua && \

# 设置nginx相关目录的权限
RUN chown -R nginx:nginx /usr/local/openresty/nginx/ && \
    chmod -R 755 /usr/local/openresty/nginx/conf/ && \
    # 给nginx用户配置目录的写权限
    chmod 775 /usr/local/openresty/nginx/conf/conf.d/ && \
    # 创建并设置nginx目录权限
    mkdir -p /usr/share/nginx/html && \
    chown -R nginx:nginx /usr/share/nginx && \
    chmod -R 755 /usr/share/nginx && \
    # 创建并设置Let's Encrypt验证目录权限
    mkdir -p /usr/share/nginx/html/.well-known/acme-challenge && \
    chown -R nginx:nginx /usr/share/nginx/html/.well-known && \
    chmod -R 777 /usr/share/nginx/html/.well-known && \
    # 创建并设置SSL证书目录权限（nginx需要读取证书文件）
    mkdir -p /etc/letsencrypt /var/lib/letsencrypt && \
    chown -R nginx:nginx /etc/letsencrypt /var/lib/letsencrypt && \
    chmod -R 755 /etc/letsencrypt /var/lib/letsencrypt && \
    # 创建并设置数据目录权限（nginx需要读取sitemap.xml和robots.txt）
    mkdir -p /app/data && \
    chown nginx:nginx /app/data && \
    chmod 755 /app/data && \
    # 设置默认SSL证书目录权限
    chown -R nginx:nginx /etc/nginx/ssl && \
    chmod 755 /etc/nginx/ssl

# 暴露端口
EXPOSE 80 443/tcp 443/udp

# 切换到nginx用户
USER nginx

# 启动命令
CMD ["openresty", "-g", "daemon off;"]
