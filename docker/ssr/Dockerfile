FROM node:18-alpine

# 创建工作目录
WORKDIR /app

RUN apk add --no-cache curl

# 复制依赖描述文件并安装生产依赖
COPY package.json package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com \
    && npm install --production --no-audit --no-fund --prefer-offline --legacy-peer-deps

# 拷贝源代码
COPY . .

ENV PORT=3000
EXPOSE 3000

CMD ["node", "server.js"] 