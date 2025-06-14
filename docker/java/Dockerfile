FROM openjdk:21-jdk-slim

WORKDIR /app


# 安装健康检查所需工具
RUN apt-get update && \
    apt-get install -y netcat-openbsd curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 复制可执行jar包
COPY ./poetize-server/poetry-web/target/poetize-server-exec.jar /app/app.jar

# 暴露端口
EXPOSE 8081

# 创建健康检查脚本
RUN echo '#!/bin/sh\nset -e\nif nc -z localhost 8081; then\n  curl -f http://localhost:8081/actuator/health || exit 1\n  exit 0\nfi\nexit 1' > /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh

# 启动命令
CMD ["sh", "-c", "ls -la /app && java $JAVA_OPTS -jar /app/app.jar"] 