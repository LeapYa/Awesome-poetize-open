FROM node:16-alpine as builder

# 安装构建依赖
RUN apk add --no-cache bash git

# 设置工作目录
WORKDIR /app

# 复制构建脚本
COPY docker/frontend/build-in-container.sh /build-in-container.sh
RUN chmod +x /build-in-container.sh

# 第二阶段使用nginx
FROM nginx:alpine
COPY --from=builder /build-in-container.sh /build-in-container.sh

# 创建目录并设置权限
RUN mkdir -p /usr/share/nginx/html/poetize /usr/share/nginx/html/im

# 复制HTTPS启用脚本
COPY nginx/enable-https.sh /enable-https.sh
RUN chmod +x /enable-https.sh

# 暴露端口
EXPOSE 80 443

# 启动命令
CMD ["/bin/sh", "/build-in-container.sh"] 