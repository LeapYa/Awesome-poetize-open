FROM openresty/openresty:1.25.3.1-alpine AS builder

# 安装构建依赖
RUN apk add --no-cache \
    build-base \
    git \
    cmake \
    brotli-dev \
    pcre-dev \
    zlib-dev \
    openssl-dev

# 下载ngx_brotli模块
WORKDIR /usr/src
RUN git clone --recursive https://github.com/google/ngx_brotli.git

# 获取Nginx版本
RUN nginx -v 2>&1 | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' > /tmp/nginx_version

# 编译Brotli模块
WORKDIR /usr/src/ngx_brotli
RUN NGINX_VERSION=$(cat /tmp/nginx_version) && \
    wget https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz && \
    tar -xvf nginx-${NGINX_VERSION}.tar.gz && \
    cd nginx-${NGINX_VERSION} && \
    ./configure --with-compat --add-dynamic-module=/usr/src/ngx_brotli && \
    make modules && \
    mkdir -p /usr/local/openresty/nginx/modules/ && \
    cp objs/ngx_http_brotli_filter_module.so /usr/local/openresty/nginx/modules/ && \
    cp objs/ngx_http_brotli_static_module.so /usr/local/openresty/nginx/modules/

# 最终镜像
FROM openresty/openresty:1.25.3.1-alpine

# 安装运行时依赖
RUN apk add --no-cache \
    ca-certificates \
    brotli \
    pcre \
    zlib \
    git \
    openssl \
    openssl-dev \
    build-base

# 手动安装lua-resty-openssl
RUN cd /tmp && \
    git clone https://github.com/fffonion/lua-resty-openssl.git && \
    cd lua-resty-openssl && \
    mkdir -p /usr/local/openresty/lualib/resty/openssl && \
    cp -r lib/resty/openssl/* /usr/local/openresty/lualib/resty/openssl/ && \
    cp lib/resty/openssl.lua /usr/local/openresty/lualib/resty/

# 直接下载安装lua-resty-http模块
RUN mkdir -p /usr/local/openresty/lualib/resty && \
    cd /tmp && \
    git clone https://github.com/ledgetech/lua-resty-http.git && \
    cd lua-resty-http && \
    cp lib/resty/http.lua /usr/local/openresty/lualib/resty/ && \
    cp lib/resty/http_headers.lua /usr/local/openresty/lualib/resty/ && \
    cp lib/resty/http_connect.lua /usr/local/openresty/lualib/resty/ && \
    cp -r lib/ngx /usr/local/openresty/lualib/ || true

# 创建必要的目录
RUN mkdir -p /var/log/nginx /var/cache/nginx

# 从builder中复制Brotli模块
COPY --from=builder /usr/local/openresty/nginx/modules/ /usr/local/openresty/nginx/modules/

# 创建配置目录
RUN mkdir -p /usr/local/openresty/nginx/conf/conf.d

# 复制自定义配置
COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf

# 暴露端口
EXPOSE 80 443/tcp 443/udp

# 启动命令
CMD ["openresty", "-g", "daemon off;"] 