FROM ollama/ollama:latest

ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app

# 安装基本工具
RUN apt-get update && \
    apt-get install -y curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 创建必要的目录
RUN mkdir -p /app/data /app/models /app/logs

# 创建模型配置文件
RUN mkdir -p /app/modelfiles
RUN echo 'FROM qwen3:0.6b\n\
\n\
PARAMETER num_ctx 4096\n\
PARAMETER num_gpu 0\n\
PARAMETER num_thread 2\n\
PARAMETER repeat_penalty 1.1\n\
PARAMETER top_k 40\n\
PARAMETER top_p 0.9\n\
' > /app/modelfiles/translator.modelfile

# 创建启动脚本
RUN echo '#!/bin/bash\n\
set -e\n\
\n\
# 创建日志目录\n\
mkdir -p /app/logs\n\
\n\
echo "启动Ollama服务..."\n\
# 在后台启动ollama服务\n\
ollama serve &\n\
OLLAMA_PID=$!\n\
\n\
echo "等待Ollama服务启动..."\n\
sleep 10\n\
\n\
echo "下载qwen3:0.6b模型..."\n\
ollama pull qwen3:0.6b\n\
echo "模型下载完成"\n\
\n\
echo "翻译模型服务已准备就绪"\n\
\n\
# 等待ollama进程\n\
wait $OLLAMA_PID\n\
' > /app/start.sh && chmod +x /app/start.sh

# 暴露ollama端口
EXPOSE 11434

# 覆盖基础镜像的ENTRYPOINT
ENTRYPOINT []

# 启动ollama服务
CMD ["/bin/bash", "/app/start.sh"]