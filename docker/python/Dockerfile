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
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 安装Node.js 20.x LTS
RUN curl -fsSL https://nodejs.org/dist/v20.11.1/node-v20.11.1-linux-x64.tar.xz | tar -xJ -C /usr/local --strip-components=1

# 安装uv和uvx通过官方安装脚本
RUN curl -LsSf https://astral.sh/uv/install.sh | sh && \
    . /root/.local/bin/env

# 将uv和uvx添加到PATH
ENV PATH="/root/.local/bin:$PATH"

# 设置工作目录
WORKDIR /app

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
RUN node --version && npm --version && npx --version && uv --version && uvx --version

# 暴露端口
EXPOSE 5000

# 使用Uvicorn启动FastAPI应用
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000", "--workers", "4"] 