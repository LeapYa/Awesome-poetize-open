FROM ollama/ollama:latest

ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app

# 安装基本工具与 Python 3
RUN apt-get update && \
    apt-get install -y curl python3 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 创建必要的目录
RUN mkdir -p /app/data /app/models /app/logs

# 创建安全代理脚本 (支持 OLLAMA_API_KEY 验证)
RUN echo 'import os\n\
import sys\n\
import http.server\n\
import urllib.request\n\
\n\
API_KEY = os.environ.get("OLLAMA_API_KEY", "")\n\
TARGET_PORT = 11435\n\
\n\
class ProxyHandler(http.server.BaseHTTPRequestHandler):\n\
    def log_message(self, format, *args):\n\
        pass\n\
\n\
    def do_OPTIONS(self):\n\
        self.send_response(200)\n\
        self.send_header("Access-Control-Allow-Origin", "*")\n\
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")\n\
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")\n\
        self.end_headers()\n\
\n\
    def do_GET(self):\n\
        self.handle_request("GET")\n\
\n\
    def do_POST(self):\n\
        self.handle_request("POST")\n\
\n\
    def do_PUT(self):\n\
        self.handle_request("PUT")\n\
\n\
    def do_DELETE(self):\n\
        self.handle_request("DELETE")\n\
\n\
    def handle_request(self, method):\n\
        if API_KEY:\n\
            auth_header = self.headers.get("Authorization", "")\n\
            expected_auth = f"Bearer {API_KEY}"\n\
            if auth_header != expected_auth:\n\
                self.send_response(401)\n\
                self.send_header("Content-Type", "application/json")\n\
                self.send_header("Access-Control-Allow-Origin", "*")\n\
                self.end_headers()\n\
                self.wfile.write(b"{\\"error\\": \\"Unauthorized: Invalid API Key\\"}")\n\
                return\n\
\n\
        url = f"http://127.0.0.1:{TARGET_PORT}{self.path}"\n\
        content_length = int(self.headers.get("Content-Length", 0))\n\
        body = self.rfile.read(content_length) if content_length > 0 else None\n\
\n\
        headers = {}\n\
        for key, value in self.headers.items():\n\
            if key.lower() not in ["host", "authorization"]:\n\
                headers[key] = value\n\
\n\
        req = urllib.request.Request(url, data=body, headers=headers, method=method)\n\
        try:\n\
            with urllib.request.urlopen(req) as response:\n\
                self.send_response(response.status)\n\
                has_cors = False\n\
                for key, value in response.getheaders():\n\
                    if key.lower() == "access-control-allow-origin":\n\
                        has_cors = True\n\
                    self.send_header(key, value)\n\
                if not has_cors:\n\
                    self.send_header("Access-Control-Allow-Origin", "*")\n\
                self.end_headers()\n\
                self.wfile.write(response.read())\n\
        except urllib.error.HTTPError as e:\n\
            self.send_response(e.code)\n\
            has_cors = False\n\
            for key, value in e.headers.items():\n\
                if key.lower() == "access-control-allow-origin":\n\
                    has_cors = True\n\
                self.send_header(key, value)\n\
            if not has_cors:\n\
                self.send_header("Access-Control-Allow-Origin", "*")\n\
            self.end_headers()\n\
            self.wfile.write(e.read())\n\
        except Exception as e:\n\
            self.send_response(500)\n\
            self.send_header("Content-Type", "application/json")\n\
            self.send_header("Access-Control-Allow-Origin", "*")\n\
            self.end_headers()\n\
            self.wfile.write(f"{{\\"error\\": \\"{str(e)}\\"}}".encode())\n\
\n\
if __name__ == \\"__main__\\":\n\
    port = 11434\n\
    server_address = (\\"\\", port)\n\
    httpd = http.server.HTTPServer(server_address, ProxyHandler)\n\
    print(f\\"Proxy server running on port {port}, forwarding to 11435...\\")\n\
    httpd.serve_forever()\n\
' > /app/proxy.py

# 创建启动脚本
RUN echo '#!/bin/bash\n\
set -e\n\
\n\
# 创建日志目录\n\
mkdir -p /app/logs\n\
\n\
# 强制本地 Ollama 服务绑定到 localhost:11435，防止未经授权的外部直接访问\n\
export OLLAMA_HOST=127.0.0.1:11435\n\
\n\
echo "启动Ollama服务..."\n\
ollama serve &\n\
OLLAMA_PID=$!\n\
\n\
echo "等待Ollama服务启动..."\n\
for i in {1..30}; do\n\
    if curl -s http://127.0.0.1:11435/api/tags >/dev/null; then\n\
        echo "Ollama 服务已成功启动"\n\
        break\n\
    fi\n\
    sleep 1\n\
done\n\
\n\
# 读取要下载的模型，默认为 qwen3:0.6b\n\
MODEL_TO_PULL="${OLLAMA_MODELS:-qwen3:0.6b}"\n\
echo "需要下载的模型列表: ${MODEL_TO_PULL}"\n\
\n\
# 分割模型列表并进行拉取\n\
IFS=", " read -r -a models <<< "$MODEL_TO_PULL"\n\
for model in "${models[@]}"; do\n\
    if [ -n "$model" ]; then\n\
        echo "正在拉取模型: ${model} ..."\n\
        ollama pull "$model"\n\
    fi\n\
done\n\
\n\
# 如果指定了模型，为第一个模型创建一个默认的 translator 包装模型，方便后台直接以 "translator" 名称调用\n\
if [ ${#models[@]} -gt 0 ] && [ -n "${models[0]}" ]; then\n\
    echo "正在创建自定义翻译模型: translator (基于 ${models[0]}) ..."\n\
    mkdir -p /app/modelfiles\n\
    echo -e "FROM ${models[0]}\\n\\nPARAMETER num_ctx 4096\\nPARAMETER num_gpu 0\\nPARAMETER num_thread 2\\nPARAMETER repeat_penalty 1.1\\nPARAMETER top_k 40\\nPARAMETER top_p 0.9" > /app/modelfiles/translator.modelfile\n\
    if ollama create translator -f /app/modelfiles/translator.modelfile; then\n\
        echo "自定义翻译模型 translator 创建成功"\n\
    else\n\
        echo "自定义翻译模型 translator 创建失败，已跳过"\n\
    fi\n\
fi\n\
\n\
echo "所有模型配置及下载完成，正在启动安全代理服务..."\n\
python3 /app/proxy.py\n\
' > /app/start.sh && chmod +x /app/start.sh

# 暴露端口
EXPOSE 11434

# 覆盖基础镜像的ENTRYPOINT
ENTRYPOINT []

# 启动服务
CMD ["/bin/bash", "/app/start.sh"]