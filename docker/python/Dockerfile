FROM python:3.12-slim AS builder

# 设置工作目录
WORKDIR /build

# 安装构建依赖，包括Node.js和npm
RUN apt-get update && apt-get install -y \
    gcc \
    libmariadb-dev-compat \
    libmariadb-dev \
    curl \
    wget \
    git \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 复制并安装Python依赖
COPY ./py/requirements.txt /build/requirements.txt

# 创建虚拟环境并安装依赖
RUN python -m venv /venv
ENV PATH="/venv/bin:$PATH"
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir wheel && \
    pip install --no-cache-dir -r requirements.txt && \
    # 安装uvx用于通用可执行工具管理
    pip install --no-cache-dir uvx && \
    # 清理pip缓存
    rm -rf /root/.cache/pip && \
    # 清理Python字节码文件
    find /venv -name "*.pyc" -delete && \
    find /venv -name "__pycache__" -type d -exec rm -rf {} +

# 最终镜像
FROM python:3.12-slim

# 复制虚拟环境
COPY --from=builder /venv /venv
ENV PATH="/venv/bin:$PATH"

# 设置环境变量
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV FASTAPI_ENV=production

# 安装运行时依赖，包括Node.js和npm
RUN apt-get update && apt-get install -y \
    libmariadb-dev-compat \
    libmariadb-dev \
    curl \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# 设置工作目录
WORKDIR /app

# 保留目录创建，只为数据目录
RUN mkdir -p /app/data

# 复制所有Python服务代码
COPY --chown=root:root ./py/ /app/

# 清理Python字节码文件和临时文件
RUN find /app -name "*.pyc" -delete && \
    find /app -name "__pycache__" -type d -exec rm -rf {} + && \
    find /app -name "*.log" -delete

# 验证安装
RUN node --version && npm --version && npx --version && uvx --version

# 暴露端口
EXPOSE 5000

# 使用Uvicorn启动FastAPI应用
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000", "--workers", "4"] 