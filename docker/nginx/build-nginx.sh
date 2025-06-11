#!/bin/bash
set -e

echo "更新软件包信息..."
apt-get update

echo "安装构建依赖..."
apt-get install -y --no-install-recommends \
  wget git ca-certificates libpcre3-dev zlib1g-dev build-essential \
  libssl-dev libbrotli-dev libgd-dev ninja-build cmake meson pkg-config \
  golang liburing-dev libluajit-5.1-dev curl

mkdir -p /etc/nginx/modules

echo "清理旧的构建目录..."
rm -rf /tmp/lua-nginx-module /tmp/ngx_devel_kit /tmp/headers-more-nginx-module \
       /tmp/lua-resty-core /tmp/lua-resty-lrucache /tmp/lua-resty-http \
       /tmp/ngx_brotli /tmp/openssl /tmp/quiche /tmp/nginx

echo "克隆必要的库..."
cd /tmp
git clone https://github.com/openresty/lua-nginx-module
git clone https://github.com/vision5/ngx_devel_kit
git clone https://github.com/openresty/headers-more-nginx-module
git clone https://github.com/openresty/lua-resty-core
git clone https://github.com/openresty/lua-resty-lrucache
git clone https://github.com/pintsized/lua-resty-http
git clone https://github.com/google/ngx_brotli --recursive

echo "下载并构建最新的Nginx，包含QUIC支持..."
git clone --depth=1 https://github.com/quictls/openssl
git clone --depth=1 https://gitlab.com/iwillspeak/quiche
git clone --depth=1 -b quic https://github.com/nginx/nginx.git

cd /tmp/nginx
auto/configure \
  --prefix=/etc/nginx \
  --sbin-path=/usr/sbin/nginx \
  --modules-path=/etc/nginx/modules \
  --conf-path=/etc/nginx/nginx.conf \
  --error-log-path=/var/log/nginx/error.log \
  --http-log-path=/var/log/nginx/access.log \
  --pid-path=/var/run/nginx.pid \
  --lock-path=/var/run/nginx.lock \
  --with-threads \
  --with-file-aio \
  --with-http_ssl_module \
  --with-http_v2_module \
  --with-http_v3_module \
  --with-http_realip_module \
  --with-http_gunzip_module \
  --with-http_gzip_static_module \
  --with-http_stub_status_module \
  --with-http_auth_request_module \
  --with-pcre \
  --with-stream \
  --with-stream_ssl_module \
  --with-stream_ssl_preread_module \
  --with-stream_realip_module \
  --with-openssl=/tmp/openssl \
  --with-quiche=/tmp/quiche \
  --add-module=/tmp/ngx_devel_kit \
  --add-module=/tmp/lua-nginx-module \
  --add-module=/tmp/headers-more-nginx-module \
  --add-module=/tmp/ngx_brotli

echo "编译Nginx..."
make -j$(nproc)
make install

echo "安装Lua库..."
mkdir -p /usr/local/lib/lua
cp -r /tmp/lua-resty-core/lib/* /usr/local/lib/lua/
cp -r /tmp/lua-resty-lrucache/lib/* /usr/local/lib/lua/
mkdir -p /etc/nginx/lua
cp -r /tmp/lua-resty-http/lib/* /usr/local/lib/lua/

echo "配置Nginx..."
cp /etc/nginx/conf.d/default.http.conf.template /etc/nginx/conf.d/default.conf

echo "Nginx编译和配置完成！" 