FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim



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

# 设置工作目录
WORKDIR /app

ARG UV_DEFAULT_INDEX=https://pypi.org/simple

RUN set -e; \
    echo "测试PyPI官方源连接..."; \
    if curl -fsSL --connect-timeout 10 --max-time 30 https://pypi.org/simple/ >/dev/null 2>&1; then \
        echo "✅ PyPI官方源可用，保持官方配置"; \
        echo "https://pypi.org/simple" > /tmp/pypi_index.txt; \
    else \
        echo "⚠️ PyPI 官方源不可用，使用清华镜像"; \
        echo "https://pypi.tuna.tsinghua.edu.cn/simple" > /tmp/pypi_index.txt; \
    fi


# 复制所有Python服务代码
COPY --chown=root:root ./py/ /app/

RUN uv venv

# 复制并安装Python依赖
RUN UV_DEFAULT_INDEX=$(cat /tmp/pypi_index.txt) && \
    uv pip install --no-cache-dir --upgrade pip -i ${UV_DEFAULT_INDEX} && \
    uv pip install --no-cache-dir wheel -i ${UV_DEFAULT_INDEX} && \
    uv pip install --no-cache-dir -r requirements.txt -i ${UV_DEFAULT_INDEX} && \
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
CMD ["uv", "run", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000", "--workers", "4"] 