FROM python:3.12-slim

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    gcc \
    libmariadb-dev-compat \
    libmariadb-dev \
    curl \
    wget \
    git \
    xz-utils \
    ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 安装Node.js 20.x LTS - 优先官方源，失败后使用国内镜像
RUN set -e; \
    NODE_VERSION="20.11.1"; \
    ARCH="x64"; \
    echo "尝试从官方源下载Node.js..."; \
    # 首先尝试官方源
    if curl -fsSL --connect-timeout 30 --max-time 300 https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz | tar -xJ -C /usr/local --strip-components=1; then \
        echo "✅ 从官方源下载Node.js成功"; \
    # 官方源失败，尝试淘宝镜像
    elif curl -fsSL --connect-timeout 30 --max-time 300 https://registry.npmmirror.com/-/binary/node/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz | tar -xJ -C /usr/local --strip-components=1; then \
        echo "⚠️ 官方源失败，从淘宝镜像下载Node.js成功"; \
    # 如果淘宝镜像失败，尝试华为镜像
    elif curl -fsSL --connect-timeout 30 --max-time 300 https://mirrors.huaweicloud.com/nodejs/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz | tar -xJ -C /usr/local --strip-components=1; then \
        echo "⚠️ 官方源和淘宝镜像失败，从华为镜像下载Node.js成功"; \
    # 如果华为镜像失败，尝试清华镜像
    elif curl -fsSL --connect-timeout 30 --max-time 300 https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz | tar -xJ -C /usr/local --strip-components=1; then \
        echo "⚠️ 多个镜像失败，从清华镜像下载Node.js成功"; \
    # 如果所有curl方式都失败，使用wget备用方案
    else \
        echo "❌ 所有在线下载失败，尝试wget备用方案"; \
        wget -O node.tar.xz https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz || \
        wget -O node.tar.xz https://registry.npmmirror.com/-/binary/node/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz || \
        wget -O node.tar.xz https://mirrors.huaweicloud.com/nodejs/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz || \
        wget -O node.tar.xz https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${ARCH}.tar.xz; \
        tar -xJ -C /usr/local --strip-components=1 < node.tar.xz; \
        rm -f node.tar.xz; \
        echo "✅ wget方式下载Node.js成功"; \
    fi

# 配置npm - 只有在官方源不可用时才使用镜像
RUN set -e; \
    echo "测试npm官方源连接..."; \
    if npm config get registry | grep -q "registry.npmjs.org" && curl -fsSL --connect-timeout 10 --max-time 30 https://registry.npmjs.org/ >/dev/null 2>&1; then \
        echo "✅ npm官方源可用，保持官方配置"; \
    else \
        echo "⚠️ npm官方源不可用，切换到淘宝镜像"; \
        npm config set registry https://registry.npmmirror.com; \
    fi

# 安装uv和uvx - 优先官方安装脚本
RUN set -e; \
    echo "尝试从官方源安装uv..."; \
    # 首先尝试官方安装脚本
    if curl -fsSL --connect-timeout 30 --max-time 300 https://astral.sh/uv/install.sh | sh; then \
        echo "✅ 官方脚本安装uv成功"; \
    # 如果官方脚本失败，尝试直接下载二进制文件
    else \
        echo "⚠️ 官方脚本失败，尝试直接下载uv二进制文件"; \
        UV_VERSION="0.1.18"; \
        if wget -O /tmp/uv.tar.gz https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/uv-x86_64-unknown-linux-gnu.tar.gz; then \
            echo "✅ 从GitHub下载uv成功"; \
        elif curl -fsSL -o /tmp/uv.tar.gz https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/uv-x86_64-unknown-linux-gnu.tar.gz; then \
            echo "✅ 从GitHub下载uv成功"; \
        else \
            echo "❌ uv下载失败，跳过安装"; \
            exit 0; \
        fi; \
        tar -xzf /tmp/uv.tar.gz -C /tmp; \
        mkdir -p /root/.local/bin; \
        cp /tmp/uv-x86_64-unknown-linux-gnu/uv /root/.local/bin/ 2>/dev/null || true; \
        cp /tmp/uv-x86_64-unknown-linux-gnu/uvx /root/.local/bin/ 2>/dev/null || true; \
        chmod +x /root/.local/bin/uv /root/.local/bin/uvx 2>/dev/null || true; \
        rm -rf /tmp/uv*; \
        echo "✅ uv二进制安装完成"; \
    fi; \
    . /root/.local/bin/env 2>/dev/null || true

# 将uv和uvx添加到PATH
ENV PATH="/root/.local/bin:$PATH"

# 设置工作目录
WORKDIR /app

# 配置pip - 优先官方源，不可用时才使用镜像
RUN set -e; \
    echo "测试pip官方源连接..."; \
    if curl -fsSL --connect-timeout 10 --max-time 30 https://pypi.org/simple/ >/dev/null 2>&1; then \
        echo "✅ pip官方源可用，保持官方配置"; \
    else \
        echo "⚠️ pip官方源不可用，配置清华镜像"; \
        pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple/; \
        pip config set global.trusted-host pypi.tuna.tsinghua.edu.cn; \
    fi

# 复制所有Python服务代码
COPY --chown=root:root ./py/ /app/

# 复制并安装Python依赖
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir wheel && \
    pip install --no-cache-dir -r requirements.txt && \
    # 清理pip缓存
    rm -rf /root/.cache/pip

# 清理Python字节码文件和临时文件
RUN find /app -name "*.pyc" -delete && \
    find /app -name "__pycache__" -type d -exec rm -rf {} + && \
    find /app -name "*.log" -delete

# 验证安装
RUN node --version && npm --version && npx --version && \
    (uv --version || echo "uv not available") && \
    (uvx --version || echo "uvx not available")

# 暴露端口
EXPOSE 5000

# 使用Uvicorn启动FastAPI应用
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000", "--workers", "4"] 