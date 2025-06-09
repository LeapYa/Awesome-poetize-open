FROM node:14 as builder

WORKDIR /app

# 复制package.json和package-lock.json
COPY ./poetize-im-ui/package*.json ./

# 安装依赖
RUN npm install

# 复制源代码
COPY ./poetize-im-ui/ ./

# 构建项目
RUN npm run build

# 生产环境阶段
FROM nginx:alpine

# 从构建阶段复制构建结果到nginx
COPY --from=builder /app/dist /usr/share/nginx/html

# 创建自定义nginx配置
RUN echo 'server { \
    listen 80; \
    \
    location / { \
        root /usr/share/nginx/html; \
        index index.html; \
        try_files $uri $uri/ /index.html; \
    } \
}' > /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"] 