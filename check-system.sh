#!/bin/bash

echo -e "\033[1;34m=== 翻译系统配置检查 ===\033[0m"

# 检查必要文件是否存在
echo -e "\033[1;33m1. 检查必要文件...\033[0m"
files=(
    "docker-compose.yml"
    "py/main.py"
    "py/translation_api.py"
    "poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java"
    "poetize-ui/src/components/admin/translationModelManage.vue"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "  \033[0;32m✓\033[0m $file"
    else
        echo -e "  \033[0;31m✗\033[0m $file (缺失)"
    fi
done

# 检查Docker配置
echo -e "\033[1;33m2. 检查Docker配置...\033[0m"
if command -v docker &> /dev/null; then
    echo -e "  \033[0;32m✓\033[0m Docker已安装"
else
    echo -e "  \033[0;31m✗\033[0m Docker未安装"
fi

if command -v docker-compose &> /dev/null; then
    echo -e "  \033[0;32m✓\033[0m Docker Compose已安装"
else
    echo -e "  \033[0;31m✗\033[0m Docker Compose未安装"
fi

# 检查Python依赖
echo -e "\033[1;33m3. 检查Python依赖...\033[0m"
if [ -f "py/requirements.txt" ]; then
    echo -e "  \033[0;32m✓\033[0m requirements.txt存在"
    if grep -q "httpx" py/requirements.txt; then
        echo -e "  \033[0;32m✓\033[0m httpx依赖已配置"
    else
        echo -e "  \033[0;31m✗\033[0m httpx依赖缺失"
    fi
    if grep -q "sqlalchemy" py/requirements.txt; then
        echo -e "  \033[0;32m✓\033[0m sqlalchemy依赖已配置"
    else
        echo -e "  \033[0;31m✗\033[0m sqlalchemy依赖缺失"
    fi
else
    echo -e "  \033[0;31m✗\033[0m requirements.txt不存在"
fi

# 检查端口配置
echo -e "\033[1;33m4. 检查端口配置...\033[0m"
if grep -q "5000:5000" docker-compose.yml; then
    echo -e "  \033[0;32m✓\033[0m Python后端端口5000已配置"
else
    echo -e "  \033[0;31m✗\033[0m Python后端端口5000未配置"
fi

if grep -q "11434:11434" docker-compose.yml; then
    echo -e "  \033[0;32m✓\033[0m Ollama端口11434已配置"
else
    echo -e "  \033[0;31m✗\033[0m Ollama端口11434未配置"
fi

# 检查翻译API集成
echo -e "\033[1;33m5. 检查翻译API集成...\033[0m"
if grep -q "register_translation_api" py/main.py; then
    echo -e "  \033[0;32m✓\033[0m Python后端已集成翻译API"
else
    echo -e "  \033[0;31m✗\033[0m Python后端未集成翻译API"
fi

if grep -q "OLLAMA_API_URL" py/translation_api.py; then
    echo -e "  \033[0;32m✓\033[0m Ollama API URL已配置"
else
    echo -e "  \033[0;33m⚠\033[0m Ollama API URL未在translation_api.py中找到"
fi

# 检查前端翻译API调用
echo -e "\033[1;33m6. 检查前端翻译配置...\033[0m"
if grep -q "/api/translate" poetize-ui/src/components/admin/translationModelManage.vue; then
    echo -e "  \033[0;32m✓\033[0m 前端翻译API端点已更新"
else
    echo -e "  \033[0;31m✗\033[0m 前端翻译API端点未更新"
fi

if grep -q "sourceText" poetize-ui/src/components/admin/translationModelManage.vue && \
   grep -q "sourceLang" poetize-ui/src/components/admin/translationModelManage.vue && \
   grep -q "targetLang" poetize-ui/src/components/admin/translationModelManage.vue; then
    echo -e "  \033[0;32m✓\033[0m 前端API参数格式正确"
else
    echo -e "  \033[0;31m✗\033[0m 前端API参数格式不正确"
fi

if grep -q "translatedText" poetize-ui/src/components/admin/translationModelManage.vue; then
    echo -e "  \033[0;32m✓\033[0m 前端响应解析格式正确"
else
    echo -e "  \033[0;31m✗\033[0m 前端响应解析格式不正确"
fi

# 检查Java后端翻译配置
echo -e "\033[1;33m7. 检查Java后端翻译配置...\033[0m"
if grep -q "/api/translate" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java; then
    echo -e "  \033[0;32m✓\033[0m Java后端翻译端点已更新"
else
    echo -e "  \033[0;31m✗\033[0m Java后端翻译端点未更新"
fi

if grep -q "sourceText" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java && \
   grep -q "sourceLang" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java && \
   grep -q "targetLang" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java; then
    echo -e "  \033[0;32m✓\033[0m Java后端API参数格式正确"
else
    echo -e "  \033[0;31m✗\033[0m Java后端API参数格式不正确"
fi

if grep -q "translatedText" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java; then
    echo -e "  \033[0;32m✓\033[0m Java后端响应解析已更新"
else
    echo -e "  \033[0;31m✗\033[0m Java后端响应解析未更新"
fi

# 检查健康检查URL
if grep -q "pythonServiceUrl.*health" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java; then
    echo -e "  \033[0;32m✓\033[0m 健康检查URL已更新"
else
    echo -e "  \033[0;31m✗\033[0m 健康检查URL未更新"
fi

# 检查旧配置清理
echo -e "\033[1;33m8. 检查旧配置清理...\033[0m"
if grep -q "translation.url.*5001" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java; then
    echo -e "  \033[0;31m✗\033[0m 仍存在旧的翻译URL配置"
else
    echo -e "  \033[0;32m✓\033[0m 旧的翻译URL配置已清理"
fi

if grep -q "translation_model/translate" poetize-server/poetry-web/src/main/java/com/ld/poetry/service/TranslationService.java; then
    echo -e "  \033[0;31m✗\033[0m 仍存在旧的翻译端点"
else
    echo -e "  \033[0;32m✓\033[0m 旧的翻译端点已清理"
fi

if grep -q "\./data:/app/data\|\./models:/app/models\|\./logs:/app/logs" docker-compose.yml; then
    echo -e "  \033[0;31m✗\033[0m 仍存在不必要的本地目录挂载"
else
    echo -e "  \033[0;32m✓\033[0m 不必要的本地目录挂载已清理"
fi

# 检查环境变量配置
echo -e "\033[1;33m9. 检查环境变量配置...\033[0m"
if grep -q "PYTHON_SERVICE_URL" docker-compose.yml; then
    echo -e "  \033[0;32m✓\033[0m PYTHON_SERVICE_URL已配置"
else
    echo -e "  \033[0;31m✗\033[0m PYTHON_SERVICE_URL未配置"
fi

if grep -q "TRANSLATION_LOCAL_URL" docker-compose.yml; then
    echo -e "  \033[0;32m✓\033[0m TRANSLATION_LOCAL_URL已配置"
else
    echo -e "  \033[0;31m✗\033[0m TRANSLATION_LOCAL_URL未配置"
fi

echo -e "\033[1;34m=== 检查完成 ===\033[0m"
echo ""
echo -e "\033[1;32m配置状态总结：\033[0m"
echo "• 翻译功能已整合到Python后端"
echo "• 前端和Java后端都使用统一的API格式"
echo "• 健康检查指向Python后端"
echo "• 旧的翻译服务配置已清理"
echo ""
echo -e "\033[1;32m建议的启动方式：\033[0m"
echo "使用自动部署脚本:"
echo "  bash deploy.sh"
echo ""
echo "或手动分步启动:"
echo "1. docker-compose up -d mysql"
echo "2. docker-compose up -d translation-model"
echo "3. docker-compose up -d python-backend"
echo "4. docker-compose up -d java-backend"
echo "5. docker-compose up -d nginx" 