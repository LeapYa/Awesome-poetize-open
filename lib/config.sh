#!/bin/bash
# ============================================
# POETIZE 配置管理函数库
# ============================================
# 提供 .env 文件的创建、读取、更新和备份功能
# 供 deploy.sh, migrate.sh, poetize 等脚本共享使用
# 注意：调用脚本需确保在项目根目录中运行

# 配置文件路径（相对于项目根目录）
ENV_FILE=".env"
ENV_EXAMPLE_FILE=".env.example"
BACKUP_DIR=".config/env_backups"
MAX_BACKUPS=5

# ============================================
# 颜色输出函数
# ============================================
_config_info() {
    echo -e "\033[34m[INFO]\033[0m $1"
}

_config_success() {
    echo -e "\033[32m[SUCCESS]\033[0m $1"
}

_config_warning() {
    echo -e "\033[33m[WARNING]\033[0m $1"
}

_config_error() {
    echo -e "\033[31m[ERROR]\033[0m $1"
}

# ============================================
# 跨平台 sed -i 兼容函数
# ============================================
_sed_i() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

# ============================================
# 配置管理函数
# ============================================

# 初始化配置文件
# 如果 .env 不存在，从 .env.example 创建
# 返回: 0 成功, 1 失败
init_env_config() {
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$ENV_EXAMPLE_FILE" ]; then
            cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
            echo "# Created: $(date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S')" >> "$ENV_FILE"
            _config_success "配置文件已从模板创建: $ENV_FILE"
            return 0
        else
            _config_error "模板文件不存在: $ENV_EXAMPLE_FILE"
            return 1
        fi
    fi
    return 0
}


# 更新单个环境变量
# 参数: $1 - 变量名, $2 - 变量值
# 返回: 0 成功, 1 失败
update_env_var() {
    local var_name="$1"
    local var_value="$2"
    
    if [ ! -f "$ENV_FILE" ]; then
        _config_error "配置文件不存在: $ENV_FILE"
        return 1
    fi
    
    # 使用 awk 来安全地更新变量，避免 sed 特殊字符问题
    if grep -q "^${var_name}=" "$ENV_FILE" 2>/dev/null; then
        # 变量存在，使用 awk 更新值（避免 sed 特殊字符问题）
        local temp_file="${ENV_FILE}.tmp"
        awk -v name="$var_name" -v value="$var_value" '
            BEGIN { }
            /^[^=]+=[^=]*/ {
                # 提取变量名（第一个 = 之前的部分）
                idx = index($0, "=")
                if (idx > 0) {
                    vname = substr($0, 1, idx - 1)
                    if (vname == name) {
                        print name "=" value
                        next
                    }
                }
            }
            { print }
        ' "$ENV_FILE" > "$temp_file" && mv "$temp_file" "$ENV_FILE"
    else
        # 变量不存在，追加到文件末尾（直接写入，不转义）
        echo "${var_name}=${var_value}" >> "$ENV_FILE"
    fi
    return 0
}

# 读取环境变量值
# 参数: $1 - 变量名
# 返回: 变量值 (stdout)
get_env_var() {
    local var_name="$1"
    
    if [ -f "$ENV_FILE" ]; then
        grep "^${var_name}=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '\r'
    fi
}

# 读取环境变量值（带默认值回退）
# 参数: $1 - 变量名, $2 - 默认值
# 返回: 变量值或默认值 (stdout)
read_env_config() {
    local var_name="$1"
    local default_value="$2"
    
    if [ -f "$ENV_FILE" ]; then
        local value=$(grep "^${var_name}=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '\r')
        if [ -n "$value" ]; then
            echo "$value"
            return 0
        fi
    fi
    
    echo "$default_value"
}

# 批量更新环境变量
# 用法: update_env_vars "VAR1=value1" "VAR2=value2" ...
update_env_vars() {
    for pair in "$@"; do
        local var_name="${pair%%=*}"
        local var_value="${pair#*=}"
        update_env_var "$var_name" "$var_value"
    done
}


# ============================================
# 备份管理函数
# ============================================

# 创建配置备份
# 返回: 0 成功, 1 失败
backup_env_config() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="${BACKUP_DIR}/env_${timestamp}.bak"
    
    mkdir -p "$BACKUP_DIR"
    
    if [ -f "$ENV_FILE" ]; then
        cp "$ENV_FILE" "$backup_file"
        _config_success "配置已备份到: $backup_file"
        
        # 清理旧备份
        cleanup_old_backups
        return 0
    else
        _config_warning "配置文件不存在，跳过备份"
        return 1
    fi
}

# 清理旧备份，保留最近 MAX_BACKUPS 个
cleanup_old_backups() {
    if [ ! -d "$BACKUP_DIR" ]; then
        return 0
    fi
    
    local backup_count=$(ls -1 "$BACKUP_DIR"/env_*.bak 2>/dev/null | wc -l)
    
    if [ "$backup_count" -gt "$MAX_BACKUPS" ]; then
        local to_delete=$((backup_count - MAX_BACKUPS))
        ls -1t "$BACKUP_DIR"/env_*.bak 2>/dev/null | tail -n "$to_delete" | xargs rm -f
        _config_info "已清理 $to_delete 个旧备份"
    fi
}

# 恢复配置
# 参数: $1 - 备份文件名 (可选，默认最新)
# 返回: 0 成功, 1 失败
restore_env_config() {
    local backup_file="$1"
    
    if [ -z "$backup_file" ]; then
        # 使用最新备份
        backup_file=$(ls -1t "$BACKUP_DIR"/env_*.bak 2>/dev/null | head -1)
    fi
    
    if [ -f "$backup_file" ]; then
        cp "$backup_file" "$ENV_FILE"
        _config_success "配置已从备份恢复: $backup_file"
        return 0
    else
        _config_error "备份文件不存在: $backup_file"
        return 1
    fi
}

# 列出可用备份
list_backups() {
    if [ ! -d "$BACKUP_DIR" ] || [ -z "$(ls -A "$BACKUP_DIR" 2>/dev/null)" ]; then
        _config_info "没有可用的配置备份"
        return 0
    fi
    
    echo "可用的配置备份:"
    ls -1t "$BACKUP_DIR"/env_*.bak 2>/dev/null | while read f; do
        local filename=$(basename "$f")
        local timestamp="${filename#env_}"
        timestamp="${timestamp%.bak}"
        echo "  - $f"
    done
}

# ============================================
# 便捷读取函数
# ============================================

# 读取 HTTP 端口配置
get_http_port() {
    read_env_config "HTTP_PORT" "80"
}

# 读取 HTTPS 端口配置
get_https_port() {
    read_env_config "HTTPS_PORT" "443"
}

# 读取主域名配置
get_primary_domain() {
    read_env_config "PRIMARY_DOMAIN" "localhost"
}

# 读取是否启用 HTTPS
get_enable_https() {
    read_env_config "ENABLE_HTTPS" "true"
}

# 读取是否启用内置 MySQL
get_enable_mysql() {
    read_env_config "ENABLE_MYSQL" "true"
}

# 读取是否启用内置 Redis
get_enable_redis() {
    read_env_config "ENABLE_REDIS" "true"
}

# 读取是否启用 RAG 向量检索
get_rag_enabled() {
    read_env_config "RAG_ENABLED" "true"
}

# 读取站点 URL
get_site_url() {
    read_env_config "SITE_URL" "http://localhost"
}

# ============================================
# Docker Compose 命令辅助函数
# ============================================

# 同步 COMPOSE_PROFILES 环境变量
# 根据 ENABLE_HTTPS/MYSQL/REDIS 自动生成 COMPOSE_PROFILES
# 这样用户直接执行 docker compose down 也能正确停止所有容器
sync_compose_profiles() {
    local enable_https=$(read_env_config "ENABLE_HTTPS" "true")
    local enable_mysql=$(read_env_config "ENABLE_MYSQL" "true")
    local enable_redis=$(read_env_config "ENABLE_REDIS" "true")
    local enable_ollama=$(read_env_config "ENABLE_OLLAMA" "false")
    
    local profiles=()
    [ "$enable_https" = "true" ] && profiles+=("https")
    [ "$enable_mysql" = "true" ] && profiles+=("mysql")
    [ "$enable_redis" = "true" ] && profiles+=("redis")
    [ "$enable_ollama" = "true" ] && profiles+=("ollama")
    
    # 用逗号连接 profiles
    local profiles_str=""
    if [ ${#profiles[@]} -gt 0 ]; then
        profiles_str=$(IFS=,; echo "${profiles[*]}")
    fi
    
    update_env_var "COMPOSE_PROFILES" "$profiles_str"
}

# 获取 Docker Compose 命令（根据 ENABLE_HTTPS/MYSQL/REDIS/OLLAMA 添加 profiles）
# 参数: $1 - 基础 docker-compose 命令 (可选，默认 "docker compose")
# 返回: 完整的 docker-compose 命令 (stdout)
get_compose_command() {
    local base_cmd="${1:-docker compose}"
    local enable_https=$(read_env_config "ENABLE_HTTPS" "true")
    local enable_mysql=$(read_env_config "ENABLE_MYSQL" "true")
    local enable_redis=$(read_env_config "ENABLE_REDIS" "true")
    local enable_ollama=$(read_env_config "ENABLE_OLLAMA" "false")
    
    local profiles=""
    [ "$enable_https" = "true" ] && profiles="$profiles --profile https"
    [ "$enable_mysql" = "true" ] && profiles="$profiles --profile mysql"
    [ "$enable_redis" = "true" ] && profiles="$profiles --profile redis"
    [ "$enable_ollama" = "true" ] && profiles="$profiles --profile ollama"
    
    echo "$base_cmd$profiles"
}

# ============================================
# 配置应用函数
# ============================================

# 将命令行参数应用到 .env 文件
# 此函数应在解析命令行参数后调用
apply_cli_args_to_env() {
    # 确保 .env 文件存在
    if [ ! -f "$ENV_FILE" ]; then
        init_env_config || return 1
    fi
    
    # 端口配置
    [ -n "$HTTP_PORT" ] && update_env_var "HTTP_PORT" "$HTTP_PORT"
    [ -n "$HTTPS_PORT" ] && update_env_var "HTTPS_PORT" "$HTTPS_PORT"
    [ "$ENABLE_HTTPS" = "false" ] && update_env_var "ENABLE_HTTPS" "false"
    [ "$ENABLE_HTTPS" = "true" ] && update_env_var "ENABLE_HTTPS" "true"
    
    # 域名配置
    if [ -n "$PRIMARY_DOMAIN" ]; then
        update_env_var "PRIMARY_DOMAIN" "$PRIMARY_DOMAIN"
        
        # 自动生成 SITE_URL
        local protocol="http"
        local enable_https=$(read_env_config "ENABLE_HTTPS" "true")
        [ "$enable_https" = "true" ] && protocol="https"
        update_env_var "SITE_URL" "${protocol}://${PRIMARY_DOMAIN}"
    fi
    
    # 数据库配置
    [ -n "$DB_HOST" ] && update_env_var "DB_HOST" "$DB_HOST"
    [ -n "$DB_PORT" ] && update_env_var "DB_PORT" "$DB_PORT"
    [ -n "$DB_NAME" ] && update_env_var "DB_NAME" "$DB_NAME"
    [ -n "$DB_USER" ] && update_env_var "DB_USER" "$DB_USER"
    [ -n "$DB_PWD" ] && update_env_var "DB_PASSWORD" "$DB_PWD"
    [ -n "$DB_TYPE" ] && update_env_var "DB_TYPE" "$DB_TYPE"
    [ -n "$DB_ROOT_PASSWORD" ] && update_env_var "DB_ROOT_PASSWORD" "$DB_ROOT_PASSWORD"
    [ -n "$RAG_ENABLED" ] && update_env_var "RAG_ENABLED" "$RAG_ENABLED"
    
    # Redis 配置
    [ -n "$REDIS_HOST" ] && update_env_var "REDIS_HOST" "$REDIS_HOST"
    [ -n "$REDIS_PORT" ] && update_env_var "REDIS_PORT" "$REDIS_PORT"
    [ -n "$REDIS_PWD" ] && update_env_var "REDIS_PASSWORD" "$REDIS_PWD"
    [ -n "$REDIS_DB" ] && update_env_var "REDIS_DB" "$REDIS_DB"
    
    # 数据库驱动配置
    [ -n "$DB_DRIVER_CLASS" ] && update_env_var "DB_DRIVER_CLASS" "$DB_DRIVER_CLASS"
    
    # Druid 连接池配置
    [ -n "$DRUID_INITIAL_SIZE" ] && update_env_var "DRUID_INITIAL_SIZE" "$DRUID_INITIAL_SIZE"
    [ -n "$DRUID_MIN_IDLE" ] && update_env_var "DRUID_MIN_IDLE" "$DRUID_MIN_IDLE"
    [ -n "$DRUID_MAX_ACTIVE" ] && update_env_var "DRUID_MAX_ACTIVE" "$DRUID_MAX_ACTIVE"
    
    # 同步 COMPOSE_PROFILES，确保 docker compose down 能正确停止所有容器
    sync_compose_profiles
    
    return 0
}

# 更新网络配置到 .env 文件
# 参数: $1 - 新子网, $2 - 新网关, $3 - 基础IP前缀 (如 "172.28.147")
update_network_config() {
    local new_subnet="$1"
    local new_gateway="$2"
    local base_ip="$3"
    
    update_env_var "DOCKER_SUBNET" "$new_subnet"
    update_env_var "DOCKER_GATEWAY" "$new_gateway"
    update_env_var "IP_NGINX" "${base_ip}.2"
    update_env_var "IP_WEB" "${base_ip}.3"
    update_env_var "IP_CERTBOT" "${base_ip}.5"
    update_env_var "IP_PYTHON" "${base_ip}.6"
    update_env_var "IP_JAVA" "${base_ip}.7"
    update_env_var "IP_MYSQL" "${base_ip}.8"
    update_env_var "IP_PRERENDER" "${base_ip}.9"
    update_env_var "IP_REDIS" "${base_ip}.10"
    update_env_var "IP_MODEL" "${base_ip}.11"
    update_env_var "IP_ADMIN" "${base_ip}.12"
}
