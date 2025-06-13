#!/bin/bash
## 作者: LeapYa
## 修改时间: 2025-06-12
## 描述: 部署 Poetize 博客系统安装脚本
## 版本: 1.0.1

# 定义颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 初始化变量
# 自动确认模式（后台运行时自动回答yes）
AUTO_YES=${AUTO_YES:-false}

# 函数：自动确认提示
auto_confirm() {
  local prompt="$1"
  local default_answer="${2:-y}"
  local options="${3:--n 1 -r}"
  
  # 如果是自动确认模式，直接返回默认答案
  if [ "$AUTO_YES" = "true" ]; then
    echo "$prompt"
    echo "自动回答: $default_answer (AUTO_YES=true)"
    REPLY="$default_answer"
    echo ""
    return 0
  fi
  
  # 否则执行正常的提示
  read -p "$prompt" $options
  echo ""
  return 0
}

# 初始化默认参数
RUN_IN_BACKGROUND=false
DOMAINS=()
PRIMARY_DOMAIN=""
EMAIL=""
ENABLE_HTTPS=false
CONFIG_FILE=".poetize-config"
SAVE_CONFIG=false
LOW_MEMORY_MODE=false
ENABLE_SWAP=true  # 默认启用swap
SWAP_SIZE=1G      # 默认swap大小为1G（对于2GB及以下内存将自动增加到2G）
RUN_IN_BACKGROUND=false
LOG_FILE="deploy.log"
DISABLE_DOCKER_CACHE=true  # 默认禁用Docker构建缓存

# 添加sed_i跨平台兼容函数（在文件开头合适位置添加）
sed_i() {
  # 跨平台兼容的sed -i替代函数
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS需要空备份扩展名
    sed -i '' "$@"
  else
    # Linux可以直接使用-i
    sed -i "$@"
  fi
}

# 用于sudo环境的替代函数
sudo_sed_i() {
  # 跨平台兼容的sudo sed -i替代函数
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS需要空备份扩展名
    sudo sed -i '' "$@"
  else
    # Linux可以直接使用-i
    sudo sed -i "$@"
  fi
}

# 函数
info() { echo -e "${BLUE}[信息]${NC} $1"; }
success() { echo -e "${GREEN}[成功]${NC} $1"; }
error() { echo -e "${RED}[失败]${NC} $1"; }
warning() { echo -e "${YELLOW}[警告]${NC} $1"; }

# 检测是否在WSL环境中
is_wsl() {
  # 检查/proc/version文件中是否包含Microsoft字符串
  if [ -f /proc/version ] && grep -q Microsoft /proc/version 2>/dev/null; then
    return 0  # 是WSL环境
  else
    return 1  # 不是WSL环境
  fi
}

# 打印部署汇总信息
print_summary() {
  local https_enabled=false
  
  # 检查HTTPS是否真正启用
  if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    if docker exec poetize-nginx nginx -T 2>/dev/null | grep -q "listen.*443.*ssl" && docker exec poetize-nginx test -f "/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem" 2>/dev/null; then
      https_enabled=true
    fi
  fi
  
  printf "\n"
  printf "${GREEN}%80s${NC}\n" | tr ' ' '='
  printf "${GREEN}%s${NC}\n" "$(printf '%*s' $(((80-20)/2)) '')Poetize 部署成功！$(printf '%*s' $(((80-20)/2)) '')"
  printf "${GREEN}%80s${NC}\n" | tr ' ' '='
  printf "\n"
  
  printf "${CYAN}基础配置信息${NC}\n"
  printf "${CYAN}%s${NC}\n" "$(printf '%*s' 12 '' | tr ' ' '-')"
  printf "  主域名: ${GREEN}%s${NC}\n" "$PRIMARY_DOMAIN"
  printf "  所有域名: ${GREEN}%s${NC}\n" "${DOMAINS[*]}"
  printf "  管理员邮箱: ${GREEN}%s${NC}\n" "$EMAIL"
  printf "\n"
  
  # 本地环境处理
  if [ "$PRIMARY_DOMAIN" = "localhost" ] || [ "$PRIMARY_DOMAIN" = "127.0.0.1" ] || [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    printf "${CYAN}本地开发环境访问地址${NC}\n"
    printf "${CYAN}%s${NC}\n" "$(printf '%*s' 20 '' | tr ' ' '-')"
    printf "  网站首页: ${GREEN}http://%s${NC}\n" "$PRIMARY_DOMAIN"
    printf "  聊天室: ${GREEN}http://%s/im${NC}\n" "$PRIMARY_DOMAIN"
    printf "  管理后台: ${GREEN}http://%s/admin${NC}\n" "$PRIMARY_DOMAIN"
  else
    printf "${CYAN}服务访问地址${NC}\n"
    printf "${CYAN}%s${NC}\n" "$(printf '%*s' 12 '' | tr ' ' '-')"
    if [ "$https_enabled" = true ]; then
      printf "  网站首页: ${GREEN}https://%s${NC} ${GREEN}(HTTPS已启用)${NC}\n" "$PRIMARY_DOMAIN"
      printf "  聊天室: ${GREEN}https://%s/im${NC}\n" "$PRIMARY_DOMAIN"
      printf "  管理后台: ${GREEN}https://%s/admin${NC}\n" "$PRIMARY_DOMAIN"
      printf "  HTTP备用: ${YELLOW}http://%s${NC} ${YELLOW}(自动重定向)${NC}\n" "$PRIMARY_DOMAIN"
    else
      printf "  网站首页: ${GREEN}http://%s${NC}\n" "$PRIMARY_DOMAIN"
      printf "  聊天室: ${GREEN}http://%s/im${NC}\n" "$PRIMARY_DOMAIN"
      printf "  管理后台: ${GREEN}http://%s/admin${NC}\n" "$PRIMARY_DOMAIN"
      printf "  HTTPS状态: ${RED}未启用${NC}\n"
    fi
  fi
  printf "\n"
  
  # HTTPS配置状态
  if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    printf "${CYAN}HTTPS配置状态${NC}\n"
    printf "${CYAN}%s${NC}\n" "$(printf '%*s' 13 '' | tr ' ' '-')"
    if [ "$https_enabled" = true ]; then
      printf "  ${GREEN}HTTPS已成功配置并启用${NC}\n"
      printf "  SSL证书状态: ${GREEN}有效${NC}\n"
      printf "  Nginx HTTPS配置: ${GREEN}已启用${NC}\n"
      printf "  安全连接: ${GREEN}可用${NC}\n"
    else
      printf "  ${RED}HTTPS未正确配置${NC}\n"
      printf "  启用命令: ${YELLOW}docker exec poetize-nginx /enable-https.sh${NC}\n"
      printf "  请检查域名DNS解析和防火墙配置\n"
    fi
    printf "\n"
  fi
  
  # 数据库凭据信息
  if [ -f ".config/db_credentials.txt" ]; then
    printf "${CYAN}数据库凭据信息${NC}\n"
    printf "${CYAN}%s${NC}\n" "$(printf '%*s' 14 '' | tr ' ' '-')"
    
    DB_ROOT_PASSWORD=$(grep "数据库ROOT密码:" .config/db_credentials.txt | cut -d':' -f2 | tr -d ' ')
    DB_USER_PASSWORD=$(grep "数据库poetize用户密码:" .config/db_credentials.txt | cut -d':' -f2 | tr -d ' ')
    
    printf "  ROOT密码: ${YELLOW}%s${NC}\n" "$DB_ROOT_PASSWORD"
    printf "  poetize用户密码: ${YELLOW}%s${NC}\n" "$DB_USER_PASSWORD"
    printf "  ${YELLOW}请妥善保存密码，完整信息在 .config/db_credentials.txt${NC}\n"
    printf "\n"
  fi
  
  # 常用命令
  printf "${CYAN}常用管理命令${NC}\n"
  printf "${CYAN}%s${NC}\n" "$(printf '%*s' 12 '' | tr ' ' '-')"
  printf "  查看所有容器: ${GREEN}docker ps -a${NC}\n"
  printf "  查看容器日志: ${GREEN}docker logs poetize-nginx${NC}\n"
  printf "  重启容器: ${GREEN}%s restart${NC}\n" "$DOCKER_COMPOSE_CMD"
  printf "  停止服务: ${GREEN}%s down${NC}\n" "$DOCKER_COMPOSE_CMD"
  printf "  启动服务: ${GREEN}%s up -d${NC}\n" "$DOCKER_COMPOSE_CMD"
  if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    printf "  手动启用HTTPS: ${GREEN}docker exec poetize-nginx /enable-https.sh${NC}\n"
  fi
  printf "\n"
  
  printf "${CYAN}登录信息${NC}\n"
  printf "${CYAN}%s${NC}\n" "$(printf '%*s' 8 '' | tr ' ' '-')"
  printf "  ${YELLOW}默认管理员账号: Sara, 密码: aaa${NC}\n"
  printf "  ${RED}请登录后立即修改密码以确保安全！${NC}\n"
  printf "\n"
  
  printf "${GREEN}%80s${NC}\n" | tr ' ' '='
}

# 保存配置到文件
save_config() {
  local config_file=$1
  
  # 确保目录存在
  mkdir -p $(dirname "$config_file")
  
  # 将所有域名用空格连接
  local all_domains=$(IFS=" "; echo "${DOMAINS[*]}")
  
  # 保存配置到文件
  cat > "$config_file" << EOF
# Poetize部署配置
# 保存时间: $(date)
DOMAINS="$all_domains"
PRIMARY_DOMAIN="$PRIMARY_DOMAIN"
EMAIL="$EMAIL"
ENABLE_HTTPS=$ENABLE_HTTPS
EOF

  success "配置已保存到 $config_file"
}

# 从文件加载配置
load_config() {
  local config_file=$1
  
  if [ ! -f "$config_file" ]; then
    warning "配置文件 $config_file 不存在"
    return 1
  fi
  
  # 导入配置文件
  source "$config_file"
  
  # 将DOMAINS字符串转换为数组
  IFS=' ' read -r -a DOMAINS <<< "$DOMAINS"
  
  success "从 $config_file 加载了配置"
  
  # 显示已加载的配置
  echo "- 主域名: $PRIMARY_DOMAIN"
  echo "- 所有域名: ${DOMAINS[*]}"
  echo "- 邮箱: $EMAIL"
  echo "- 启用HTTPS: $([ "$ENABLE_HTTPS" = true ] && echo '是' || echo '否')"
}

# 显示帮助
show_help() {
  echo "Poetize 自动部署脚本"
  echo ""
  echo "用法: $0 [选项]"
  echo ""
  echo "选项:"
  echo "  -d, --domain DOMAIN     设置域名（可多次使用添加多个域名）"
  echo "  -e, --email EMAIL       设置管理员邮箱"
  echo "  -h, --help              显示此帮助信息"
  echo "  --enable-https          启用HTTPS"
  echo "  --config FILE           从文件加载配置"
  echo "  --save-config [FILE]    保存配置到文件（默认为.poetize-config）"
  echo "  --enable-swap           启用swap空间（默认启用）"
  echo "  --swap-size SIZE        设置swap大小（默认1G）"
  echo "  -b, --background        在后台运行脚本，输出重定向到日志文件"
  echo "  --log-file FILE         指定日志文件（默认为deploy.log）"
  echo "  --enable-docker-cache   启用Docker构建缓存（默认禁用以节省空间）"
  echo ""
  echo "示例:"
  echo "  $0 --domain example.com --domain www.example.com --email admin@example.com --enable-https"
  echo "  $0 --config .poetize-config"
  echo "  $0 --domain example.com --save-config"
  echo "  $0 --background         # 在后台运行，输出到deploy.log"
  echo "  $0 --background --log-file custom.log"
  echo "  $0 --enable-swap --swap-size 2G"
  echo ""
}

# 解析命令行参数
parse_arguments() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -d|--domain)
        DOMAINS+=("$2")
        shift 2
        ;;
      -e|--email)
        EMAIL="$2"
        shift 2
        ;;
      --enable-https)
        ENABLE_HTTPS=true
        shift
        ;;
      --config)
        CONFIG_FILE="$2"
        shift 2
        ;;
      --save-config)
        SAVE_CONFIG=true
        if [[ "$2" != -* ]] && [ -n "$2" ]; then
          CONFIG_FILE="$2"
          shift
        elif [ -z "$CONFIG_FILE" ]; then
          CONFIG_FILE=".poetize-config"
        fi
        shift
        ;;
      --enable-swap)
        ENABLE_SWAP=true
        shift
        ;;
      --swap-size)
        SWAP_SIZE="$2"
        shift 2
        ;;
      -h|--help)
        show_help
        exit 0
        ;;
      -b|--background)
        RUN_IN_BACKGROUND=true
        shift
        ;;
      --log-file)
        LOG_FILE="$2"
        shift 2
        ;;
      --enable-docker-cache)
        DISABLE_DOCKER_CACHE=false
        shift
        ;;
      *)
        error "未知选项: $1"
        show_help
        exit 1
        ;;
    esac
  done
}


# 检测是否为国内环境
is_china_environment() {
    # 方法1: 检测网络连通性
    if command -v curl &>/dev/null; then
        # 检测是否能访问Google（国内通常被屏蔽）
        if ! curl -s --connect-timeout 3 --max-time 5 "https://www.google.com" >/dev/null 2>&1; then
            # 无法访问Google，再检测是否能访问国内镜像源
            if curl -s --connect-timeout 3 --max-time 5 "http://mirrors.aliyun.com" >/dev/null 2>&1; then
                return 0  # 无法访问Google但能访问阿里云镜像，判断为国内环境
            fi
        fi
    elif command -v ping &>/dev/null; then
        # 如果没有curl，使用ping检测
        if ! ping -c 1 -W 3 www.google.com >/dev/null 2>&1; then
            # 无法ping通Google，再检测国内镜像源
            if ping -c 1 -W 3 mirrors.aliyun.com >/dev/null 2>&1; then
                return 0  # 无法ping通Google但能ping通阿里云镜像，判断为国内环境
        fi
      fi
    fi
    
    # 方法2: 检测IP地址归属
    local ip_check_result=""
    if command -v curl &>/dev/null; then
        # 尝试获取公网IP并检测归属地
        ip_check_result=$(curl -s --connect-timeout 5 --max-time 10 "http://ip-api.com/json" 2>/dev/null | grep -o '"country":"China"' || echo "")
        if [[ -n "$ip_check_result" ]]; then
            return 0  # 是国内环境
        fi
    fi
    
    # 方法3: 检测时区
    if [[ -f /etc/timezone ]]; then
        if grep -q "Asia/Shanghai\|Asia/Chongqing" /etc/timezone; then
            return 0  # 是国内环境
        fi
    fi
    
    # 方法4: 检测locale
    if [[ "$LANG" =~ zh_CN || "$LC_ALL" =~ zh_CN ]]; then
        return 0  # 是国内环境
    fi
    
    return 1  # 不是国内环境
}


# 检测操作系统类型
detect_os_type() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        
        # Ubuntu
        if [[ "$ID" == "ubuntu" ]]; then
            echo "ubuntu"
            return 0
        fi
        
        # Debian
        if [[ "$ID" == "debian" ]]; then
            echo "debian"
            return 0
        fi
        
        # CentOS
        if [[ "$ID" == "centos" ]]; then
            if [[ "$VERSION_ID" =~ ^7 ]]; then
                echo "centos7"
            else
                echo "centos8"
            fi
            return 0
        fi
        
        # Red Hat
        if [[ "$ID" == "rhel" ]]; then
            echo "centos8"  # 使用相同的安装方式
            return 0
        fi
        
        # Fedora
        if [[ "$ID" == "fedora" ]]; then
            echo "centos8"
            return 0
        fi
        
        # Arch Linux
        if [[ "$ID" == "arch" ]]; then
            echo "arch"
            return 0
        fi
        
        # Alpine Linux
        if [[ "$ID" == "alpine" ]]; then
            echo "alpine"
            return 0
        fi
        
        # 龙蜥OS
        if [[ "$ID" == "anolis" ]]; then
            echo "anolis"
            return 0
        fi
    fi
    
    # 兜底检测
    if command -v apt-get &>/dev/null; then
        if command -v lsb_release &>/dev/null; then
            local distro=$(lsb_release -i -s 2>/dev/null | tr '[:upper:]' '[:lower:]')
            if [[ "$distro" == "ubuntu" ]]; then
                echo "ubuntu"
            else
                echo "debian"
            fi
        else
            echo "debian"
        fi
    elif command -v pacman &>/dev/null; then
        echo "arch"
    elif command -v apk &>/dev/null; then
        echo "alpine"
    elif command -v yum &>/dev/null || command -v dnf &>/dev/null; then
        if [ -f /etc/redhat-release ]; then
            if grep -q "release 7" /etc/redhat-release; then
                echo "centos7"
            else
                echo "centos8"
            fi
        else
            echo "centos8"
        fi
    else
        echo "unknown"
    fi
}


# 安装curl工具
check_and_install_curl() {
  if ! command -v curl &>/dev/null; then
    # 检测系统类型
    local os_type=$(detect_os_type)
    # 根据操作系统类型安装curl
    case "$os_type" in
    "debian"|"ubuntu")
      # Ubuntu/Debian系统
      info "使用apt-get安装curl..."
      if sudo apt-get install -y curl; then
        success "curl安装成功"
      else
        error "curl安装失败，请手动安装: sudo apt-get install curl"
        return 1
      fi
      ;;
      "centos7")
      # CentOS/RHEL/Anolis系统
      info "使用yum安装curl..."
      if sudo yum install -y curl; then
        success "curl安装成功"
      else
        error "curl安装失败，请手动安装: sudo yum install curl"
            return 1
      fi
      ;;
    "fedora"|"centos8"|"anolis")
      # Fedora系统
      info "使用dnf安装Git..."
      if sudo dnf install -y curl; then
        success "curl安装成功"
      else
        error "curl安装失败，请手动安装: sudo dnf install curl"
            return 1
        fi
      ;;
    "arch")
      # Arch Linux系统
      info "使用pacman安装curl..."
      if sudo pacman -S --noconfirm curl; then
        success "curl安装成功"
      else
        error "curl安装失败，请手动安装: sudo pacman -S curl"
        return 1
    fi
    ;;
    "alpine")
      # Alpine Linux系统
      info "使用apk安装curl..."
      if sudo apk add curl; then
        success "curl安装成功"
      else
        error "curl安装失败，请手动安装: sudo apk add curl"
        return 1
      fi
    ;;
    *)
      error "不支持的操作系统类型: $os_type，请手动安装curl"
      echo "常见安装命令："
      echo "  Ubuntu/Debian: sudo apt-get install curl"
      echo "  CentOS/RHEL:   sudo yum install curl"
      echo "  Fedora:        sudo dnf install curl"
      echo "  Arch Linux:    sudo pacman -S curl"
      echo "  Alpine Linux:  sudo apk add curl"
      return 1
      ;;
  esac
  fi
}

# Docker CE 软件源列表 (格式："软件源名称@软件源地址")
DOCKER_CE_MIRRORS=(
    "阿里云@mirrors.aliyun.com/docker-ce"
    "腾讯云@mirrors.tencent.com/docker-ce"
    "华为云@mirrors.huaweicloud.com/docker-ce"
    "微软 Azure 中国@mirror.azure.cn/docker-ce"
    "网易@mirrors.163.com/docker-ce"
    "清华大学@mirrors.tuna.tsinghua.edu.cn/docker-ce"
    "中科大@mirrors.ustc.edu.cn/docker-ce"
    "官方@download.docker.com"
)

# Docker Registry 仓库列表 (格式："软件源名称@软件源地址")
DOCKER_REGISTRY_MIRRORS=(
    "毫秒镜像@docker.1ms.run"
    "轩辕镜像@docker.xuanyuan.me"
    "Docker Proxy@dockerproxy.net"
    "DaoCloud 道客@docker.m.daocloud.io"
    "1Panel@docker.1panel.live"
    "yomansunter@docker.yomansunter.com"
    "xiaogenban1993@docker.xiaogenban1993.com"
    "Dockerhub镜像加速说明@a.ussh.net"
    "Docker Proxy@dockerproxy.net"
    "阿里云(杭州)@registry.cn-hangzhou.aliyuncs.com"
    "阿里云(上海)@registry.cn-shanghai.aliyuncs.com"
    "阿里云(北京)@registry.cn-beijing.aliyuncs.com"
    "腾讯云@mirror.ccs.tencentyun.com"
    "geekery1@hub.icert.top"
    "geekery2@ghcr.geekery.cn"
    "官方 Docker Hub@registry.hub.docker.com"
    "Docker Hub@hub.docker.com"
)

# 选择Docker Registry镜像仓库
choose_docker_registry_mirror() {
    if [ -n "$DOCKER_REGISTRY_SOURCE" ]; then
        info "使用预设的Docker Registry镜像源: $DOCKER_REGISTRY_SOURCE"
        return 0
    fi

    info "Docker Registry镜像源配置："
    echo ""
    echo "为了提高Docker镜像下载成功率，建议配置多个镜像源作为备用。"
    echo "当一个镜像源不可用时，Docker会自动尝试下一个镜像源。"
    echo ""
    
    auto_confirm "是否自动配置所有可用的镜像源作为备用？ (推荐) [y/n]: " "y" "-n 1 -r"
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        info "将自动配置所有可用的镜像源作为备用"
        echo ""
        
        # 显示将要配置的镜像源列表
        info "以下镜像源将按优先级顺序配置："
        local i=1
        for mirror in "${DOCKER_REGISTRY_MIRRORS[@]}"; do
            local name="${mirror%@*}"
            local url="${mirror#*@}"
            printf "  %d) %s (%s)\n" "$i" "$name" "$url"
            ((i++))
        done
        
        echo ""
        info "Docker将按优先级顺序自动选择可用的镜像源"
        
        # 设置一个标记，表示使用所有镜像源
        DOCKER_REGISTRY_SOURCE="all_mirrors"
    else
        info "跳过Docker镜像源配置，将使用默认设置"
        info "如需要，可稍后手动配置 /etc/docker/daemon.json"
        
        # 设置为官方Docker Hub，不配置镜像源
        DOCKER_REGISTRY_SOURCE="skip_config"
    fi
    
    echo ""
}

# 配置Docker Registry镜像加速
configure_docker_registry() {
    # 如果用户选择跳过配置，则不配置镜像源
    if [ "$DOCKER_REGISTRY_SOURCE" = "skip_config" ]; then
        info "跳过Docker镜像源配置，使用默认设置"
        return 0
    fi
    
    info "配置Docker Registry镜像加速（使用多个备用镜像源）..."
    
    local docker_config_dir="/etc/docker"
    local docker_config_file="$docker_config_dir/daemon.json"
    
    # 创建配置目录
    sudo mkdir -p "$docker_config_dir"
    
    # 备份原配置文件
    if [ -f "$docker_config_file" ]; then
        sudo cp "$docker_config_file" "$docker_config_file.bak.$(date +%Y%m%d_%H%M%S)"
        info "已备份原配置文件"
    fi
    
    # 配置多个镜像源
    local config_content
    if [ -f "$docker_config_file" ] && [ -s "$docker_config_file" ]; then
        # 如果配置文件存在且不为空，尝试合并配置
        if command -v jq &>/dev/null; then
            # 构建多个镜像源列表
            local mirrors_list=""
            for mirror in "${DOCKER_REGISTRY_MIRRORS[@]}"; do
                local mirror_url=$(echo "$mirror" | cut -d'@' -f2)
                if [ -n "$mirrors_list" ]; then
                    mirrors_list="$mirrors_list,"
                fi
                mirrors_list="$mirrors_list\"https://$mirror_url\""
            done
            
            config_content=$(sudo jq '.["registry-mirrors"] = ['"$mirrors_list"']' "$docker_config_file" 2>/dev/null)
        fi
    fi
    
    # 如果无法合并或jq不可用，创建新配置
    if [ -z "$config_content" ]; then
        # 构建多个镜像源列表
        local mirrors_list=""
        for mirror in "${DOCKER_REGISTRY_MIRRORS[@]}"; do
            local mirror_url=$(echo "$mirror" | cut -d'@' -f2)
            if [ -n "$mirrors_list" ]; then
                mirrors_list="$mirrors_list,"
            fi
            mirrors_list="$mirrors_list\"https://$mirror_url\""
        done
        
        config_content='{
  "registry-mirrors": ['"$mirrors_list"']
}'
    fi
    
    echo "$config_content" | sudo tee "$docker_config_file" > /dev/null
    info "已配置多个Docker Registry镜像源作为备用"
    
    # 重启Docker服务使配置生效
    if systemctl is-active --quiet docker 2>/dev/null; then
        info "重启Docker服务使配置生效..."
        sudo systemctl daemon-reload
        sudo systemctl restart docker
        
        if [ $? -eq 0 ]; then
            success "Docker Registry镜像配置完成"
        else
            warning "Docker服务重启失败，请手动重启: sudo systemctl restart docker"
        fi
    else
        info "Docker服务未运行，配置将在下次启动时生效"
    fi
}

# 国内环境Debian系统安装Docker
install_docker_china_debian() {
    info "在Debian系统安装Docker (使用 $DOCKER_MIRROR_SOURCE 镜像源)..."
    
    # 更新软件包索引
    sudo apt-get update
    
    # 安装必要的软件包
    sudo apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg \
        lsb-release
    
    # 确保 /etc/apt/sources.list.d/ 目录存在
    sudo mkdir -p /etc/apt/sources.list.d
    
    # 确保 /usr/share/keyrings/ 目录存在
    sudo mkdir -p /usr/share/keyrings
    
    # 添加Docker的GPG密钥
    curl -fsSL "https://$DOCKER_MIRROR_SOURCE/linux/debian/gpg" | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    
    # 添加Docker软件源
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://$DOCKER_MIRROR_SOURCE/linux/debian $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    # 更新软件包索引
    sudo apt-get update
    
    # 安装Docker CE
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # 启动和启用Docker服务
    sudo systemctl start docker
    sudo systemctl enable docker
    
    info "Debian Docker安装完成"
    return 0
}
                
# 国内环境Ubuntu系统安装Docker
install_docker_china_ubuntu() {
    info "在Ubuntu系统安装Docker (使用 $DOCKER_MIRROR_SOURCE 镜像源)..."
    
    # 更新软件包索引
    sudo apt-get update
    
    # 安装必要的软件包
    sudo apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg \
        lsb-release
    
    # 确保 /etc/apt/sources.list.d/ 目录存在
    sudo mkdir -p /etc/apt/sources.list.d
    
    # 确保 /usr/share/keyrings/ 目录存在
    sudo mkdir -p /usr/share/keyrings
    
    # 添加Docker的GPG密钥
    curl -fsSL "https://$DOCKER_MIRROR_SOURCE/linux/ubuntu/gpg" | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    
    # 添加Docker软件源
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://$DOCKER_MIRROR_SOURCE/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    # 更新软件包索引
    sudo apt-get update
    
    # 安装Docker CE
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # 启动和启用Docker服务
    sudo systemctl start docker
    sudo systemctl enable docker
    
    info "Ubuntu Docker安装完成"
    return 0
}
                    
# 国内环境CentOS 7系统安装Docker
install_docker_china_centos7() {
    info "在CentOS 7系统安装Docker (使用 $DOCKER_MIRROR_SOURCE 镜像源)..."
    
    # 移除旧版本Docker
    sudo yum remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine
    
    # 安装必要的软件包
    sudo yum install -y yum-utils device-mapper-persistent-data lvm2
    
    # 添加Docker软件源
    sudo yum-config-manager --add-repo "https://$DOCKER_MIRROR_SOURCE/linux/centos/docker-ce.repo"
    
    # 安装Docker CE
    sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # 启动和启用Docker服务
    sudo systemctl start docker
    sudo systemctl enable docker
    
    info "CentOS 7 Docker安装完成"
    return 0
}
                    
# 国内环境CentOS 8/Fedora/Red Hat系统安装Docker
install_docker_china_centos8() {
    info "在CentOS 8/Fedora/Red Hat系统安装Docker (使用 $DOCKER_MIRROR_SOURCE 镜像源)..."
    
    # 移除旧版本Docker
    sudo dnf remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine
    
    # 安装必要的软件包
    sudo dnf install -y dnf-utils device-mapper-persistent-data lvm2
    
    # 添加Docker软件源
    sudo dnf config-manager --add-repo "https://$DOCKER_MIRROR_SOURCE/linux/centos/docker-ce.repo"
    
    # 安装Docker CE
    sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # 启动和启用Docker服务
    sudo systemctl start docker
    sudo systemctl enable docker
    
    info "CentOS 8/Fedora/Red Hat Docker安装完成"
                        return 0
}
                    
# 国内环境Anolis OS系统安装Docker
install_docker_china_anolis() {
    info "在Anolis OS系统安装Docker (使用 $DOCKER_MIRROR_SOURCE 镜像源)..."
    
    # 移除旧版本Docker
    sudo dnf remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine
    
    # 安装必要的软件包
    sudo dnf install -y dnf-utils device-mapper-persistent-data lvm2
    
    # 添加Docker软件源
    sudo dnf config-manager --add-repo "https://$DOCKER_MIRROR_SOURCE/linux/centos/docker-ce.repo"
    
    # 安装Docker CE
    sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # 启动和启用Docker服务
    sudo systemctl start docker
    sudo systemctl enable docker
    
    info "Anolis OS Docker安装完成"
    return 0
}

# Arch Linux Docker安装
install_docker_china_arch() {
    info "在Arch Linux系统上安装Docker..."
    
    # 更新包数据库
    sudo pacman -Sy
                        
    # 安装Docker
    sudo pacman -S --noconfirm docker docker-compose
    
    # 启动和启用Docker服务
    sudo systemctl start docker
    sudo systemctl enable docker
    
    info "Arch Linux Docker安装完成"
    return 0
}

# Alpine Linux Docker安装
install_docker_china_alpine() {
    info "在Alpine Linux系统上安装Docker..."
    
    # 更新包索引
    sudo apk update
                
    # 安装Docker
    sudo apk add docker docker-compose
                
    # 启动Docker服务
    sudo rc-update add docker boot
    sudo service docker start
    
    info "Alpine Linux Docker安装完成"
    return 0
}

# 国内环境Docker安装主函数
install_docker_china() {
    info "开始在国内环境安装Docker..."
    
    # 选择Docker Registry镜像源
    choose_docker_registry_mirror

    # 检测操作系统类型
    local os_type=$(detect_os_type)
    info "检测到操作系统类型: $os_type"

    for mirror in "${DOCKER_CE_MIRRORS[@]}"; do
        local name="${mirror%@*}"
        local url="${mirror#*@}"
        DOCKER_MIRROR_SOURCE="$url"
        info "尝试使用 $name 镜像源安装Docker..."
        
        # 根据操作系统类型安装Docker
        case "$os_type" in
            "debian")
                install_docker_china_debian
                ;;
            "ubuntu")
                install_docker_china_ubuntu
                ;;
            "centos7")
                install_docker_china_centos7
                ;;
            "centos8")
                install_docker_china_centos8
                ;;
            "arch")
                install_docker_china_arch
                ;;
            "alpine")
                install_docker_china_alpine
                ;;
            "anolis")
                install_docker_china_anolis
                ;;
            *)
            warning "不支持的操作系统类型: $os_type"
                return 1
            ;;
        esac
        
        local install_result=$?

        if [ $install_result -eq 0 ]; then
            break
        else
            warning "使用 $name 镜像源安装Docker失败，尝试下一个镜像源..."  
        fi
    done
    
    if command -v docker &>/dev/null; then
        # 配置Docker Registry镜像加速
        configure_docker_registry
            
        # 如果不是WSL环境，添加用户到docker组
        if ! is_wsl; then
            local current_user=$(whoami)
            if [ "$current_user" != "root" ]; then
                info "将用户 $current_user 添加到 docker 组..."
                sudo usermod -aG docker "$current_user"
                info "请重新登录或执行 'newgrp docker' 以使权限生效"
            fi
        fi
    else
        error "所有镜像源安装Docker失败，请手动安装Docker"
        return 1
    fi
}

# Docker安装函数
install_docker() {
  info "安装Docker..."
  
  # 先检查Docker是否已安装
    if command -v docker &>/dev/null; then
      info "Docker命令已可用，跳过安装"
      success "Docker已安装"
    return 0
    fi
    
    # 检查是否存在离线安装包
    if check_offline_resources; then
        info "检测到本地离线资源，优先使用离线安装..."
        
      # 尝试离线安装Docker
      if install_docker_offline; then
            return 0
        fi
        
      warning "离线安装失败，将回退到在线安装方式"
    fi
    
  # 检查是否在WSL环境中
  if grep -q Microsoft /proc/version 2>/dev/null; then
      warning "检测到WSL环境，建议使用Docker Desktop for Windows"
      info "请参考: https://docs.docker.com/desktop/wsl/"
      echo ""
      echo -e "${BLUE}=== 推荐安装方法 ===${NC}"
      echo "1. 下载安装Docker Desktop: https://www.docker.com/products/docker-desktop/"
      echo "2. 在设置中启用WSL集成"
      echo "3. 重启Docker Desktop和WSL"
      echo ""
      auto_confirm "仍然尝试安装Docker? (y/n): " "y" "-n 1 -r"
      if [[ ! $REPLY =~ ^[Yy]$ ]]; then
          error "用户取消安装"
          exit 1
      fi
  fi
  
  # 检查是否为国内环境
  if is_china_environment; then
      info "检测到国内环境，使用国内镜像源安装Docker..."
      install_docker_china
  else
    # 使用官方安装脚本
    info "使用官方安装脚本..."
    
    # 先尝试使用官方脚本
    if curl -fsSL https://get.docker.com -o get-docker.sh; then
        # 执行安装脚本
        if ! sh get-docker.sh; then
            error "Docker官方脚本安装失败，当前系统可能不支持Docker"
            error "请检查系统版本和架构，或手动安装Docker"
            return 1
        fi
    else
        warning "无法下载Docker官方安装脚本，将回退到国内镜像源安装Docker"
        install_docker_china
    fi
  fi
  
  # 删除安装脚本
  rm -f get-docker.sh
  
  # 添加用户到docker组
  if ! grep -q Microsoft /proc/version 2>/dev/null; then
      sudo usermod -aG docker "$USER" || true
  fi
  
  # 最终检查Docker是否可用
  if command -v docker &>/dev/null; then
  success "Docker安装成功"
      return 0
  else
      error "Docker安装失败"
      return 1
  fi
}


# 设置Docker Compose别名
setup_docker_compose_alias() {
    info "创建docker-compose别名以兼容旧脚本"
    
    # 创建别名脚本内容 - 确保所有参数正确传递
    SCRIPT_CONTENT='#!/bin/bash
# 将所有参数传递给docker compose命令
docker compose "$@"'
    
    # 创建别名脚本
    if command -v sudo &>/dev/null; then
        # 使用临时文件方式创建脚本
        echo -e "$SCRIPT_CONTENT" > ./docker-compose.tmp
        sudo mv ./docker-compose.tmp /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
        
        # 验证权限
        if ! sudo test -x /usr/local/bin/docker-compose; then
            warning "无法设置docker-compose别名的正确执行权限"
            return 1
        fi
    else
        mkdir -p "$HOME/bin"
        # 直接创建文件
        echo -e "$SCRIPT_CONTENT" > "$HOME/bin/docker-compose"
        chmod +x "$HOME/bin/docker-compose"
        
        # 验证权限
        if ! test -x "$HOME/bin/docker-compose"; then
            warning "无法设置docker-compose别名的正确执行权限"
            return 1
        fi
        
        # 确保PATH中包含~/bin
        export PATH="$HOME/bin:$PATH"
        # 确保路径添加到bashrc
        if ! grep -q "PATH=\"\$HOME/bin:\$PATH\"" "$HOME/.bashrc"; then
            echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
        fi
    fi
    
    # 验证别名脚本是否可用
    if command -v docker-compose &>/dev/null; then
        info "测试docker-compose别名..."
        if docker-compose --version &>/dev/null; then
            success "Docker Compose别名设置成功"
            return 0
        else
            warning "docker-compose命令找到但不可执行"
        fi
    else
        warning "docker-compose命令不可用，但可以使用docker compose命令代替"
        info "请尝试运行: docker compose --version"
    fi
    
    # 返回0让脚本继续执行
    return 0
}

# 创建并启用swap空间
setup_swap() {
  if [ "$ENABLE_SWAP" = true ]; then
    info "检查并配置swap空间..."
    
    # 检查是否已存在swap
    if free | grep -q "Swap:"; then
      EXISTING_SWAP=$(free -m | grep "Swap:" | awk '{print $2}')
      if [ "$EXISTING_SWAP" -gt 0 ]; then
        info "系统已配置${EXISTING_SWAP}MB的swap空间，跳过创建"
        return 0
      fi
    fi
    
    # 检查是否有root权限
    if [ "$(id -u)" -ne 0 ]; then
      warning "设置swap需要root权限，尝试使用sudo..."
      if ! command -v sudo &>/dev/null; then
        error "无法设置swap：既没有root权限也没有sudo命令"
        warning "跳过swap设置"
        return 1
      fi
    fi
    
    # 创建swap文件
    info "创建${SWAP_SIZE}大小的swap文件..."
    
    # 移除单位(G, M等)用于计算
    SWAP_SIZE_NUM=$(echo "$SWAP_SIZE" | sed 's/[^0-9]*//g')
    SWAP_SIZE_UNIT=$(echo "$SWAP_SIZE" | sed 's/[0-9]*//g')
    
    # 转换为MB用于计算
    case "${SWAP_SIZE_UNIT}" in
      [Gg])
        SWAP_SIZE_MB=$((SWAP_SIZE_NUM * 1024))
        ;;
      [Mm])
        SWAP_SIZE_MB=$SWAP_SIZE_NUM
        ;;
      [Kk])
        SWAP_SIZE_MB=$((SWAP_SIZE_NUM / 1024))
        ;;
      *)
        # 默认单位为MB
        SWAP_SIZE_MB=$SWAP_SIZE_NUM
        ;;
    esac
    
    SWAP_FILE="/swapfile"
    
    # 使用dd命令创建swap文件
    if command -v sudo &>/dev/null; then
      sudo dd if=/dev/zero of=$SWAP_FILE bs=1M count=$SWAP_SIZE_MB status=progress || {
        error "创建swap文件失败"
        return 1
      }
      
      # 设置权限
      sudo chmod 600 $SWAP_FILE || warning "设置swap文件权限失败"
      
      # 格式化为swap
      sudo mkswap $SWAP_FILE || {
        error "格式化swap文件失败"
        return 1
      }
      
      # 启用swap
      sudo swapon $SWAP_FILE || {
        error "启用swap失败"
        return 1
      }
      
      # 添加到fstab以便开机自动挂载
      if ! grep -q "$SWAP_FILE" /etc/fstab; then
        echo "$SWAP_FILE none swap sw 0 0" | sudo tee -a /etc/fstab > /dev/null || warning "添加到fstab失败"
      fi
    else
      # 直接以root执行
      dd if=/dev/zero of=$SWAP_FILE bs=1M count=$SWAP_SIZE_MB status=progress || {
        error "创建swap文件失败"
        return 1
      }
      
      # 设置权限
      chmod 600 $SWAP_FILE || warning "设置swap文件权限失败"
      
      # 格式化为swap
      mkswap $SWAP_FILE || {
        error "格式化swap文件失败"
        return 1
      }
      
      # 启用swap
      swapon $SWAP_FILE || {
        error "启用swap失败"
        return 1
      }
      
      # 添加到fstab以便开机自动挂载
      if ! grep -q "$SWAP_FILE" /etc/fstab; then
        echo "$SWAP_FILE none swap sw 0 0" >> /etc/fstab || warning "添加到fstab失败"
      fi
    fi
    
    # 验证swap是否已启用
    if free | grep -q "Swap:" && [ "$(free | grep "Swap:" | awk '{print $2}')" -gt 0 ]; then
      success "成功创建并启用了${SWAP_SIZE}的swap空间"
    else
      warning "swap空间创建失败，但将继续执行后续步骤"
      # 不再返回错误状态，继续执行
    fi
  else
    info "未启用swap配置，跳过"
  fi
  
  return 0
}

# 安装后检查命令可用性并设置适当的命令别名
DOCKER_COMPOSE_CMD=""

setup_docker_compose_command() {
    # 检查是否在WSL环境中
    if grep -q Microsoft /proc/version 2>/dev/null; then
        info "检测到WSL环境"
        
        # 检查Docker Desktop是否在WSL中可用
        if ! docker info &>/dev/null; then
            error "Docker在WSL中不可用"
            echo ""
            echo -e "${BLUE}=== 在WSL中使用Docker推荐方法 ===${NC}"
            echo "1. 确保已安装Docker Desktop for Windows"
            echo "2. 确保Docker Desktop正在运行"
            echo "3. 在Docker Desktop设置中:"
            echo "   - 勾选 'Use the WSL 2 based engine'"
            echo "   - 在 'Resources > WSL Integration' 中启用当前WSL发行版"
            echo ""
            
            read -p "是否安装Docker? (y/n/s) [y=安装, n=退出, s=跳过尝试继续]: " -n 1 -r
            echo ""
            if [[ $REPLY =~ ^[Yy]$ ]]; then
              if ! install_docker; then
                error "Docker安装失败，无法继续部署"
                exit 1
              fi
            elif [[ $REPLY =~ ^[Ss]$ ]]; then
              warning "跳过Docker安装，尝试继续部署"
              warning "某些功能可能无法正常工作"
            else
              error "已取消部署"
              exit 1
            fi
        fi
        
        # 优先检查新版docker compose命令
        if docker compose version &>/dev/null; then
            info "将使用新版 'docker compose' 命令"
            DOCKER_COMPOSE_CMD="docker compose"
        elif command -v docker-compose &>/dev/null && docker-compose --version &>/dev/null; then
            info "将使用旧版 'docker-compose' 命令"
            DOCKER_COMPOSE_CMD="docker-compose"
        else
            warning "Docker Compose未启用，尝试使用docker compose子命令"
            DOCKER_COMPOSE_CMD="docker compose"
        fi
    else
        # 非WSL环境，优先检查新版docker compose命令
        if command -v docker &>/dev/null && docker compose version &>/dev/null; then
            info "检测到新版docker compose命令可用"
            DOCKER_COMPOSE_CMD="docker compose"
        elif command -v docker-compose &>/dev/null && docker-compose --version &>/dev/null; then
            info "检测到旧版docker-compose命令可用"
            DOCKER_COMPOSE_CMD="docker-compose"
        else
            error "无法找到可用的Docker Compose命令"
            exit 1
        fi
    fi
    
    # 最终验证所选命令
    info "测试Docker Compose命令..."
    if ! eval "$DOCKER_COMPOSE_CMD --version" &>/dev/null; then
        error "所选Docker Compose命令无法执行: $DOCKER_COMPOSE_CMD"
        if grep -q Microsoft /proc/version 2>/dev/null; then
            info "在WSL环境中，请在Docker Desktop设置中启用WSL集成"
            info "参考: https://docs.docker.com/desktop/wsl/"
        fi
        exit 1
    fi
    
    info "将使用命令: $DOCKER_COMPOSE_CMD"
}

# 初始化部署
init_deploy() {
  info "正在初始化部署环境..."
  
  # 配置swap空间
  setup_swap
  
  # 替换默认数据库密码为随机强密码
  replace_db_passwords
  
  # 设置域名
  info "更新Nginx配置中的域名..."
  if [ -n "$PRIMARY_DOMAIN" ]; then
    # 直接使用输入的域名列表，不自动添加www版本
    DOMAIN_CONFIG="${DOMAINS[@]}"
    
    info "配置服务器名称为: $DOMAIN_CONFIG"
    sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" docker/nginx/default.http.conf
    sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" docker/nginx/default.https.conf
    
    # 更新docker-compose.yml中的FRONTEND_HOST环境变量
    info "更新Python后端FRONTEND_HOST环境变量为: $PRIMARY_DOMAIN"
    if grep -q "FRONTEND_HOST=" docker-compose.yml; then
      sed_i "s/- FRONTEND_HOST=example.com/- FRONTEND_HOST=$PRIMARY_DOMAIN/g" docker-compose.yml
      success "已更新FRONTEND_HOST环境变量"
    else
      info "未在docker-compose.yml中找到FRONTEND_HOST环境变量配置，将添加此配置"
      # 查找python-backend服务的environment部分
      if grep -q "python-backend:" docker-compose.yml; then
        # 最后一个python环境变量是JAVA_BACKEND_PORT=8081
        JAVA_BACKEND_PORT_LINE=$(grep -n "JAVA_BACKEND_PORT=8081" docker-compose.yml | cut -d: -f1)
        if [ -n "$JAVA_BACKEND_PORT_LINE" ]; then
          # 在JAVA_BACKEND_PORT行后添加FRONTEND_HOST
          sed_i "${JAVA_BACKEND_PORT_LINE}a\\      - FRONTEND_HOST=$PRIMARY_DOMAIN" docker-compose.yml
          success "已添加FRONTEND_HOST环境变量"
        fi
      fi
    fi
  else
    error "主域名为空，无法更新Nginx配置"
    exit 1
  fi
  
  # 确保初始时使用HTTP配置
  info "设置初始Nginx配置为HTTP模式..."
  cp docker/nginx/default.http.conf docker/nginx/default.conf
  
  # 如果使用localhost，跳过certbot配置
  if [ "$PRIMARY_DOMAIN" = "localhost" ] || [ "$PRIMARY_DOMAIN" = "127.0.0.1" ] || [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    info "本地域名环境，跳过certbot配置"
  else
    # 更新docker-compose.yml中的certbot命令域名参数
    DOMAINS_PARAM=""
    for domain in "${DOMAINS[@]}"; do
      DOMAINS_PARAM="$DOMAINS_PARAM -d $domain"
    done
    
    # 使用sed替换certbot命令行certbot-entrypoint.sh
    info "尝试在docker/nginx/certbot-entrypoint.sh中替换邮箱和域名参数..."
    # 直接替换邮箱和域名参数
    sed_i "s|--email your-email@example.com|--email $EMAIL|g" docker/nginx/certbot-entrypoint.sh
    sed_i "s|-d example.com -d www.example.com|$DOMAINS_PARAM|g" docker/nginx/certbot-entrypoint.sh
    
    success "成功更新certbot-entrypoint.sh中的邮箱和域名参数"
  fi
  
  success "部署环境初始化完成"
}

# 修改docker-compose.yml中的nginx卷挂载
update_nginx_volumes() {
  info "更新Nginx卷挂载配置..."
  DOMAIN_CONFIG="${DOMAINS[*]}"
  
  info "配置服务器名称为: $DOMAIN_CONFIG"
  sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" docker/nginx/default.http.conf
  sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" docker/nginx/default.https.conf
  
  # 添加default.conf挂载
  if ! grep -q "default.conf:" docker-compose.yml; then
    # 创建临时文件
    TEMP_FILE=$(mktemp)
    
    # 找到nginx配置文件挂载的行
    NGINX_CONF_LINE=$(grep -n "default.http.conf" docker-compose.yml | cut -d ":" -f1)
    
    # 确保NGINX_CONF_LINE只包含数字
    NGINX_CONF_LINE=$(echo "$NGINX_CONF_LINE" | tr -cd '0-9')
    
    if [ -n "$NGINX_CONF_LINE" ] && [[ "$NGINX_CONF_LINE" =~ ^[0-9]+$ ]]; then
      # 新的挂载行
      NEW_MOUNT_LINE="      - ./docker/nginx/default.conf:/etc/nginx/conf.d/default.conf"
      
      # 创建配置文件备份
      cp docker-compose.yml "$TEMP_FILE"
      
      # 使用sed的i命令在指定行后插入（增加行号使其在当前行之后）
      NEXT_LINE=$((NGINX_CONF_LINE + 1))
      if sed_i "${NEXT_LINE}i\\${NEW_MOUNT_LINE}" docker-compose.yml; then
        success "已添加default.conf挂载配置"
      else
        warning "使用sed添加配置失败，尝试备用方法..."
        
        # 检查awk是否可用
        if command -v awk &>/dev/null; then
          # 备用方法1：使用awk
          echo "$NEW_MOUNT_LINE" > "$TEMP_FILE.line"
          awk -v line="$NGINX_CONF_LINE" -v text="$NEW_MOUNT_LINE" '{print $0; if(NR==line) print text}' docker-compose.yml > "$TEMP_FILE.new" \
            && mv "$TEMP_FILE.new" docker-compose.yml \
            && success "使用awk备用方法成功添加配置" \
            || (warning "awk方法失败，还原配置文件"; mv "$TEMP_FILE" docker-compose.yml)
        else
          # 备用方法2：使用纯sed/head/tail组合（不依赖awk）
          warning "未检测到awk命令，使用纯sed/head/tail方案..."
          
          # 分别提取指定行前和行后的内容
          head -n "$NGINX_CONF_LINE" docker-compose.yml > "$TEMP_FILE.head"
          TOTAL_LINES=$(wc -l < docker-compose.yml)
          TAIL_LINES=$((TOTAL_LINES - NGINX_CONF_LINE))
          tail -n "$TAIL_LINES" docker-compose.yml > "$TEMP_FILE.tail"
          
          # 合并文件
          cat "$TEMP_FILE.head" > "$TEMP_FILE.new"
          echo "$NEW_MOUNT_LINE" >> "$TEMP_FILE.new"
          cat "$TEMP_FILE.tail" >> "$TEMP_FILE.new"
          
          # 移动到原位
          mv "$TEMP_FILE.new" docker-compose.yml \
            && success "使用sed/head/tail备用方法成功添加配置" \
            || (warning "所有方法都失败，还原配置文件"; mv "$TEMP_FILE" docker-compose.yml)
        fi
        
        # 清理临时文件
        rm -f "$TEMP_FILE.line" "$TEMP_FILE.new" "$TEMP_FILE.head" "$TEMP_FILE.tail" 2>/dev/null || true
      fi
      
      # 清理主临时文件
      rm -f "$TEMP_FILE" 2>/dev/null || true
    else
      warning "无法找到nginx配置挂载行，请手动添加default.conf挂载"
    fi
  fi
}

# 构建和启动Docker服务
start_services() {
  info "启动Docker服务..."
  
  # 使用定义的docker-compose命令
  if [ -z "$DOCKER_COMPOSE_CMD" ]; then
    setup_docker_compose_command
  fi
  
  # 修复MySQL配置文件权限
  fix_mysql_config_permissions
  
  # 确保enable-https.sh有执行权限
  if [ -f "docker/nginx/enable-https.sh" ]; then
    info "确保enable-https.sh有执行权限..."
    chmod +x docker/nginx/enable-https.sh || warning "无法修改docker/nginx/enable-https.sh权限，容器内可能会出现权限问题"
    # 检查是否成功赋权
    if [ -x "docker/nginx/enable-https.sh" ]; then
      success "成功设置enable-https.sh执行权限"
    else 
      warning "未能确认enable-https.sh是否有执行权限，但会继续部署"
    fi
  fi
  
  # 设置构建参数
  BUILD_ARGS=""
  if [ "$DISABLE_DOCKER_CACHE" = true ] && [ -z "$SKIP_BUILD" ]; then
    info "已禁用Docker构建缓存，将使用--no-cache选项构建镜像"
    BUILD_ARGS="--no-cache"
  fi
  
  # 启动所有服务
  info "启动所有服务中..."
  if [ -z "$SKIP_BUILD" ] && [ "$DISABLE_DOCKER_CACHE" = true ]; then
    # 如果需要构建且禁用缓存
    info "启动服务（已禁用Docker构建缓存）..."
    DOCKER_BUILDKIT=1 COMPOSE_DOCKER_CLI_BUILD=1 BUILDKIT_PROGRESS=plain \
    run_docker_compose up -d --build
  else
    # 使用离线镜像或正常构建
    info "启动所有服务中..."
    run_docker_compose up -d $SKIP_BUILD
  fi
  
  START_RESULT=$?
  
  if [ $START_RESULT -ne 0 ]; then
    error "服务启动失败，请检查日志"
    exit 1
  fi
  
  success "服务启动命令执行成功"
  return 0
}

# 等待并应用SSL证书
setup_https() {
  info "等待SSL证书生成..."
  
  # 给certbot容器更多时间来完成，并增加重试机制
  local max_wait=120  # 最多等待2分钟
  local wait_time=0
  local interval=10   # 每10秒检查一次
  
  while [ $wait_time -lt $max_wait ]; do
    # 检查certbot容器状态
    CERTBOT_EXIT_CODE=$(docker inspect poetize-certbot --format='{{.State.ExitCode}}' 2>/dev/null || echo "-1")
    CERTBOT_RUNNING=$(docker inspect poetize-certbot --format='{{.State.Running}}' 2>/dev/null || echo "false")
    
    # 如果certbot已完成且成功
    if [ "$CERTBOT_EXIT_CODE" = "0" ] && [ "$CERTBOT_RUNNING" = "false" ]; then
      break
    fi
    
    # 如果certbot失败
    if [ "$CERTBOT_EXIT_CODE" != "0" ] && [ "$CERTBOT_EXIT_CODE" != "-1" ] && [ "$CERTBOT_RUNNING" = "false" ]; then
      break
    fi
    
    info "等待证书申请完成... (${wait_time}s/${max_wait}s)"
    sleep $interval
    wait_time=$((wait_time + interval))
  done
  
  # 再给一点时间让文件系统同步
  sleep 5
  
  if [ "$CERTBOT_EXIT_CODE" = "0" ]; then
    info "SSL证书已成功生成，正在启用HTTPS..."
    
    # 验证证书文件是否存在
    if docker exec poetize-nginx ls /etc/letsencrypt/live/*/fullchain.pem >/dev/null 2>&1; then
      info "确认证书文件存在，继续配置HTTPS..."
    else
      warning "证书文件未找到，等待文件系统同步..."
      sleep 10
      if ! docker exec poetize-nginx ls /etc/letsencrypt/live/*/fullchain.pem >/dev/null 2>&1; then
        warning "证书文件仍未找到，HTTPS配置可能失败"
      fi
    fi
    
    # 先给容器内脚本赋予执行权限
    info "给enable-https.sh赋予执行权限..."
    if ! docker exec poetize-nginx chmod +x /enable-https.sh; then
      warning "直接chmod失败，尝试使用sudo..."
      if ! docker exec poetize-nginx sh -c "command -v sudo >/dev/null && sudo chmod +x /enable-https.sh || chmod +x /enable-https.sh"; then
        warning "无法给脚本赋予执行权限，可能会导致HTTPS启用失败"
      fi
    fi
    
    # 多次尝试执行HTTPS启用脚本
    local retry_count=3
    local retry_delay=5
    local success=false
    
    for i in $(seq 1 $retry_count); do
      info "第 $i 次尝试启用HTTPS..."
      
      if docker exec poetize-nginx /enable-https.sh; then
        success=true
        break
      else
        warning "第 $i 次尝试失败"
        if [ $i -lt $retry_count ]; then
          info "等待 ${retry_delay} 秒后重试..."
          sleep $retry_delay
        fi
      fi
    done
    
    if [ "$success" = "true" ]; then
      success "HTTPS已成功启用！"
      
      # 验证HTTPS配置是否生效
      info "验证HTTPS配置..."
      if docker exec poetize-nginx nginx -t >/dev/null 2>&1; then
        info "Nginx配置验证通过"
        
        # 重新加载Nginx配置
        if docker exec poetize-nginx nginx -s reload >/dev/null 2>&1; then
          success "Nginx配置已重新加载，HTTPS现在应该可以正常工作"
        else
          warning "Nginx重新加载失败，可能需要手动重启Nginx容器"
        fi
      else
        warning "Nginx配置验证失败，请检查SSL配置"
        info "可以运行以下命令检查详细错误:"
        info "  docker exec poetize-nginx nginx -t"
      fi
      
      return 0
    else
      warning "多次尝试启用HTTPS都失败了"
      warning "您可以稍后手动运行: docker exec poetize-nginx /enable-https.sh"
      
      # 显示详细的错误诊断信息
      info "错误诊断信息:"
      info "1. 检查证书文件状态:"
      docker exec poetize-nginx sh -c "ls -la /etc/letsencrypt/live/ 2>/dev/null || echo '证书目录不存在'"
      
      info "2. 检查Nginx配置:"
      docker exec poetize-nginx nginx -t 2>&1 || echo "Nginx配置检查失败"
      
      info "3. 检查enable-https.sh脚本内容:"
      docker exec poetize-nginx head -10 /enable-https.sh 2>/dev/null || echo "脚本文件不存在或不可读"
      
      return 1
    fi
  else
    warning "SSL证书申请失败 (退出代码: $CERTBOT_EXIT_CODE)"
    info "检查证书申请日志..."
    CERT_ERROR=$(docker logs poetize-certbot 2>&1 | grep -A 5 "Certbot failed" || echo "未找到明确错误信息")
    
    echo "$CERT_ERROR"
    
    warning "系统将继续以HTTP模式运行"
    info "可能的原因:"
    info "  1. DNS记录未正确配置 (某些域名可能未解析到此服务器IP)"
    info "  2. 域名是否为有效域名 (而非本地测试域名)"
    info "  3. 80端口是否被其他服务占用"
    info "  4. Let's Encrypt账户限制或其他技术问题"
    
    info "您可以稍后手动运行以下命令重试SSL证书申请:"
    info "  docker restart poetize-certbot"
    info "然后启用HTTPS:"
    info "  docker exec poetize-nginx /enable-https.sh"
    
    # 继续执行，跳过HTTPS配置
    return 2
  fi
}

# 检查域名是否可以访问
check_domains_access() {
  info "检查域名可访问性..."
  CHECK_DOMAIN_FAILED=false

  # 跳过本地域名检查
  if [ "$PRIMARY_DOMAIN" = "localhost" ] || [ "$PRIMARY_DOMAIN" = "127.0.0.1" ]; then
    info "检测到本地域名 $PRIMARY_DOMAIN，跳过域名可访问性检查"
    return 0
  fi

  for domain in "${DOMAINS[@]}"; do
    # 跳过localhost和IP地址检查
    if [ "$domain" = "localhost" ] || [ "$domain" = "127.0.0.1" ] || [[ "$domain" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      info "跳过本地域名/IP检查: $domain"
      continue
    fi
    
    info "正在检查域名: $domain"
    if command -v curl &>/dev/null; then
      HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://$domain" --connect-timeout 10)
      if [[ $HTTP_CODE -lt 200 || $HTTP_CODE -ge 400 ]]; then
        if [[ $HTTP_CODE -eq 000 ]]; then
          error "无法连接到域名 $domain (连接超时)"
        else
          error "域名 $domain 返回HTTP状态码: $HTTP_CODE"
        fi
        CHECK_DOMAIN_FAILED=true
      else
        success "域名 $domain 访问正常 (HTTP状态码: $HTTP_CODE)"
      fi
    else
      # 如果没有curl，使用简单的nc命令
      if nc -z -w 5 "$domain" 80 2>/dev/null; then
        success "域名 $domain 的80端口可访问"
      else
        error "无法连接到域名 $domain 的80端口"
        CHECK_DOMAIN_FAILED=true
      fi
    fi
  done

  if [ "$CHECK_DOMAIN_FAILED" = true ]; then
    echo -e "${YELLOW}警告:${NC} 一些域名可能无法正确解析到当前服务器的IP地址。"
    echo "这可能导致SSL证书自动配置失败。可能的原因:"
    echo "  - DNS解析尚未生效 (通常需要几分钟到几小时)"
    echo "  - 域名未指向正确的服务器IP地址"
    echo "  - 服务器防火墙或安全组配置阻止了端口80的访问"
    echo "  - 如果使用了CDN (如Cloudflare)，请确保已正确配置源站IP"
    echo ""
    auto_confirm "是否继续安装? (y/n): " "y" "-n 1 -r"
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      error "安装已取消。请修复域名配置后重试。"
      exit 1
    fi
    info "继续安装，但SSL证书可能无法自动配置"
  fi
}

# 检查Docker Compose配置
check_docker_compose() {
  info "检查Docker Compose配置..."
  
  # 检查是否存在docker-compose.yml文件
  if [ ! -f "docker-compose.yml" ]; then
    error "找不到docker-compose.yml文件"
    exit 1
  fi

  # 检查Docker Compose版本是否支持depends_on.condition
  COMPOSE_VERSION=""
  
  # 尝试使用docker-compose命令获取版本
  if command -v docker-compose &>/dev/null; then
    COMPOSE_VERSION=$(docker-compose version --short 2>/dev/null || echo "")
  fi
  
  # 如果上面失败，尝试使用docker compose命令
  if [ -z "$COMPOSE_VERSION" ] && command -v docker &>/dev/null; then
    COMPOSE_VERSION=$(docker compose version --short 2>/dev/null || echo "")
  fi
  
  # 如果仍然无法获取版本，使用"unknown"
  if [ -z "$COMPOSE_VERSION" ]; then
    COMPOSE_VERSION="unknown"
    warning "无法确定Docker Compose版本，继续执行"
  else
    MAIN_VERSION=$(echo "$COMPOSE_VERSION" | cut -d. -f1)
    if [ "$MAIN_VERSION" -lt 2 ] 2>/dev/null; then
      info "检测到Docker Compose版本 $COMPOSE_VERSION，某些功能可能不被支持"
      info "如果启动失败，请考虑更新Docker Compose到v2.x版本"
    fi
  fi
  
  # 检查docker-compose.yml中的路径和卷配置
  # 确保volumes部分定义了所有需要的命名卷
  if ! grep -q "poetize_ui_dist:" docker-compose.yml || ! grep -q "poetize_im_dist:" docker-compose.yml; then
    error "docker-compose.yml缺少必要的命名卷定义"
    info "请确保在volumes部分添加以下行:"
    echo "  poetize_ui_dist:"
    echo "  poetize_im_dist:"
    exit 1
  fi
  
  success "Docker Compose配置检查完成"
}

# 设置脚本执行权限
setup_script_permissions() {
  info "设置脚本执行权限..."
  if [ -f "docker/nginx/enable-https.sh" ]; then
    chmod +x docker/nginx/enable-https.sh 2>/dev/null || {
      warning "无法修改docker/nginx/enable-https.sh的权限，可能需要手动设置"
      info "您可以稍后手动运行: chmod +x docker/nginx/enable-https.sh"
    }
    if [ -x "docker/nginx/enable-https.sh" ]; then
      success "已设置脚本执行权限"
    else
      warning "无法验证脚本是否有执行权限，继续部署"
    fi
  else
    error "找不到docker/nginx/enable-https.sh文件"
    exit 1
  fi
}

# 设置目录权限函数
setup_directories() {
  info "设置目录和权限..."
  
  # 确保前端构建目录存在
  mkdir -p poetize_ui_dist poetize_im_dist
  
  # 确保数据目录存在
  mkdir -p py/data
  
  # 检测是否在WSL环境中
  if grep -q Microsoft /proc/version 2>/dev/null; then
    info "检测到WSL环境，跳过权限设置"
  else
    # 在非WSL环境中设置正确的目录权限
    chmod -R 755 poetize_ui_dist poetize_im_dist 2>/dev/null || true
    chmod -R 755 py/data 2>/dev/null || true
    
    if [ $? -ne 0 ]; then
      warning "设置权限时出现问题，但将继续执行部署"
    fi
  fi
  
  # 检查是否存在必要的nginx配置文件
  if [ ! -f "docker/nginx/default.http.conf" ]; then
    error "找不到docker/nginx/default.http.conf文件"
    exit 1
  fi
  
  if [ ! -f "docker/nginx/default.https.conf" ]; then
    error "找不到docker/nginx/default.https.conf文件"
    exit 1
  fi
  
  if [ ! -f "docker/nginx/enable-https.sh" ]; then
    error "找不到docker/nginx/enable-https.sh文件"
    exit 1
  fi
  
  success "目录和权限设置完成"
}

# 提示用户输入域名
prompt_for_domains() {
  echo -n "请输入域名 (多个域名用空格分隔，Ctrl+U可重新输入): "
  read -a DOMAINS
  
  if [ ${#DOMAINS[@]} -eq 0 ]; then
    error "请至少提供一个域名"
    exit 1
  fi
  
  # 设置主域名为第一个域名
  PRIMARY_DOMAIN=${DOMAINS[0]}
}

# 提示用户输入邮箱
prompt_for_email() {
  echo -n "请输入邮箱 (默认: example@qq.com): "
  read EMAIL
  
  if [ -z "$EMAIL" ]; then
    EMAIL="example@qq.com"
    info "使用默认邮箱: $EMAIL"
  fi
}

# 确认设置
confirm_setup() {
  echo ""
  echo -e "${BLUE}请确认以下设置:${NC}"
  echo "主域名: $PRIMARY_DOMAIN"
  echo "所有域名: ${DOMAINS[*]}"
  echo "管理员邮箱: $EMAIL"
  echo "默认启用HTTPS"
  echo ""
  
  echo -n "是否确认以上设置? [Y/n]（默认Y）: "
  read CONFIRM
  
  if [ -z "$CONFIRM" ]; then
    CONFIRM="Y"
    info "使用默认设置: $CONFIRM"
  fi
  
  if [[ "$CONFIRM" =~ ^[Nn] ]]; then
    echo "已取消部署"
    exit 0
  fi
}

# 检查依赖
check_dependencies() {
  info "检查必要的依赖..."
  
  # 检查docker
  if ! command -v docker &>/dev/null; then
    error "未安装Docker"
    info "请安装Docker后再运行此脚本"
    exit 1
  fi
  
  # 检查docker-compose或docker compose
  if ! command -v docker-compose &>/dev/null && ! (command -v docker &>/dev/null && docker compose version &>/dev/null); then
    error "未安装Docker Compose"
    info "请安装Docker Compose后再运行此脚本"
    exit 1
  fi
  
  success "所有必要的依赖都已安装"
}

# 检查系统资源
check_system_resources() {
  info "检查系统资源..."
  
  # 检查磁盘空间 - 无需使用bc命令
  local DISK_SPACE=$(df -h / | awk 'NR==2 {print $4}' | sed 's/G//')
  # 使用纯整数比较，忽略小数部分
  if [ "${DISK_SPACE%.*}" -lt 10 ]; then
    warning "磁盘空间不足 (少于 10GB)，部署可能失败或影响性能"
  fi
  
  # 检查内存
  local MEMORY=$(free -g | awk '/^Mem:/{print $7}')
  # 检查内存总量（以MB为单位）
  local TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
  local TOTAL_MEM_GB=$(awk "BEGIN {printf \"%.1f\", ${TOTAL_MEM}/1024}")
  
  
  # 根据内存大小自动调整SWAP_SIZE
  if command -v bc &>/dev/null; then
    # 如果内存小于或等于2GB，将SWAP_SIZE设置为2G
    if [ $(echo "$TOTAL_MEM_GB <= 2.0" | bc -l) -eq 1 ]; then
      info "检测到2GB或更低内存环境，自动将交换空间设置为2G以提高性能"
      SWAP_SIZE="2G"
    fi
  else
    # 使用替代方法判断
    if float_lte "$TOTAL_MEM_GB" "2.0"; then
      info "检测到2GB或更低内存环境，自动将交换空间设置为2G以提高性能"
      SWAP_SIZE="2G"
    fi
  fi
  
  if command -v bc &>/dev/null; then
    # 使用bc命令进行比较
    if [ $(echo "$TOTAL_MEM_GB <= 0.95" | bc -l) -eq 1 ]; then
      error "系统内存不足 (${TOTAL_MEM_GB}GB)。运行翻译模型至少需要2GB内存，推荐4GB以上。"
      error "请升级服务器配置或选择不安装翻译模型功能。"
      exit 1
    # 基于内存大小应用不同级别的优化
    elif [ "$MEMORY" -lt 2 ] || [ $(echo "$TOTAL_MEM_GB <= 2.0" | bc -l) -eq 1 ]; then
    warning "检测到低内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "自动应用极低内存模式优化..."
      apply_memory_optimizations "very-low" "$TOTAL_MEM_GB"
    elif [ $(echo "$TOTAL_MEM_GB <= 4.0" | bc -l) -eq 1 ]; then
      warning "检测到中低内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "自动应用中低内存模式优化..."
      apply_memory_optimizations "low" "$TOTAL_MEM_GB"
    elif [ $(echo "$TOTAL_MEM_GB <= 8.0" | bc -l) -eq 1 ]; then
      info "检测到中等内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "自动应用中等内存模式优化..."
      apply_memory_optimizations "medium" "$TOTAL_MEM_GB"
    else
      info "检测到高内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "无需特别内存优化"
    fi
  else
    # 使用替代方法进行比较
    if float_lte "$TOTAL_MEM_GB" "0.95"; then
      error "系统内存不足 (${TOTAL_MEM_GB}GB)。运行翻译模型至少需要2GB内存，推荐4GB以上。"
      error "请升级服务器配置或选择不安装翻译模型功能。"
      exit 1
    # 基于内存大小应用不同级别的优化
    elif [ "$MEMORY" -lt 2 ] || float_lte "$TOTAL_MEM_GB" "2.0"; then
      warning "检测到低内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "自动应用极低内存模式优化..."
      apply_memory_optimizations "very-low" "$TOTAL_MEM_GB"
    elif float_lte "$TOTAL_MEM_GB" "4.0"; then
      warning "检测到中低内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "自动应用中低内存模式优化..."
      apply_memory_optimizations "low" "$TOTAL_MEM_GB"
    elif float_lte "$TOTAL_MEM_GB" "8.0"; then
      info "检测到中等内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "自动应用中等内存模式优化..."
      apply_memory_optimizations "medium" "$TOTAL_MEM_GB"
    else
      info "检测到高内存服务器 (内存: ${TOTAL_MEM_GB}GB)"
      info "无需特别内存优化"
    fi
  fi
  
  # 检查CPU核心数
  local CPU_CORES=$(nproc)
  if [ "$CPU_CORES" -lt 2 ]; then
    warning "CPU核心数较少，可能会影响系统性能"
  fi
  
  success "系统资源检查完成（请注意以上警告信息）"
}

# 动态内存优化函数
apply_memory_optimizations() {
  local MEMORY_MODE="$1"
  local TOTAL_MEM_GB="$2"
  
  info "应用动态内存优化 (模式: $MEMORY_MODE, 总内存: ${TOTAL_MEM_GB}GB)..."
  
  # 创建MySQL配置目录
  mkdir -p docker/mysql/conf
  
  # 根据内存模式设置配置参数
  local MYSQL_BUFFER_POOL_SIZE
  local MYSQL_LOG_BUFFER_SIZE
  local MYSQL_QUERY_CACHE_SIZE
  local MYSQL_TMP_TABLE_SIZE
  local MYSQL_KEY_BUFFER_SIZE
  local MYSQL_MAX_CONNECTIONS
  local MYSQL_TABLE_OPEN_CACHE
  
  local JAVA_XMX
  local JAVA_XMS
  local JAVA_METASPACE
  local JAVA_CLASS_SPACE
  local JAVA_XSS
  
  local JAVA_LIMIT
  local PYTHON_LIMIT
  local NGINX_LIMIT
  local MYSQL_LIMIT
  
  # 根据不同内存模式设置不同的参数
  case "$MEMORY_MODE" in
    "very-low") # 极低内存模式 (<=2GB)
      MYSQL_BUFFER_POOL_SIZE="128M"
      MYSQL_LOG_BUFFER_SIZE="8M"
      MYSQL_QUERY_CACHE_SIZE="16M"
      MYSQL_TMP_TABLE_SIZE="32M"
      MYSQL_KEY_BUFFER_SIZE="16M"
      MYSQL_MAX_CONNECTIONS="60"
      MYSQL_TABLE_OPEN_CACHE="128"
      
      JAVA_XMX="512m"
      JAVA_XMS="256m"
      JAVA_METASPACE="160m"
      JAVA_CLASS_SPACE="144m"
      JAVA_XSS="512k"
      
      JAVA_LIMIT="1024M"
      PYTHON_LIMIT="768M"
      NGINX_LIMIT="128M"
      MYSQL_LIMIT="256M"
      ;;
      
    "low") # 中低内存模式 (2-4GB)
      MYSQL_BUFFER_POOL_SIZE="256M"
      MYSQL_LOG_BUFFER_SIZE="16M"
      MYSQL_QUERY_CACHE_SIZE="32M"
      MYSQL_TMP_TABLE_SIZE="64M"
      MYSQL_KEY_BUFFER_SIZE="32M"
      MYSQL_MAX_CONNECTIONS="60"
      MYSQL_TABLE_OPEN_CACHE="256"
      
      JAVA_XMX="896m"
      JAVA_XMS="640m"
      JAVA_METASPACE="256m"
      JAVA_CLASS_SPACE="128m"
      JAVA_XSS="512k"
      
      JAVA_LIMIT="1384M"
      PYTHON_LIMIT="1024M"
      NGINX_LIMIT="256M"
      MYSQL_LIMIT="384M"
      ;;
      
    "medium") # 中等内存模式 (4-8GB)
      MYSQL_BUFFER_POOL_SIZE="256M"
      MYSQL_LOG_BUFFER_SIZE="16M"
      MYSQL_QUERY_CACHE_SIZE="32M"
      MYSQL_TMP_TABLE_SIZE="64M"
      MYSQL_KEY_BUFFER_SIZE="64M"
      MYSQL_MAX_CONNECTIONS="100"
      MYSQL_TABLE_OPEN_CACHE="256"
      
      JAVA_XMX="1024m"
      JAVA_XMS="768m"
      JAVA_METASPACE="256m"
      JAVA_CLASS_SPACE="128m"
      JAVA_XSS="1m"
      
      JAVA_LIMIT="1536M"
      PYTHON_LIMIT="2048M"
      NGINX_LIMIT="256M"
      MYSQL_LIMIT="1024M"
      ;;
      
    "high") # 高内存模式 (8-16GB)
      MYSQL_BUFFER_POOL_SIZE="512M"
      MYSQL_LOG_BUFFER_SIZE="32M"
      MYSQL_QUERY_CACHE_SIZE="64M"
      MYSQL_TMP_TABLE_SIZE="128M"
      MYSQL_KEY_BUFFER_SIZE="128M"
      MYSQL_MAX_CONNECTIONS="200"
      MYSQL_TABLE_OPEN_CACHE="400"
      
      JAVA_XMX="1536m"
      JAVA_XMS="1024m"
      JAVA_METASPACE="384m"
      JAVA_CLASS_SPACE="192m"
      JAVA_XSS="1m"
      
      JAVA_LIMIT="2048M"
      PYTHON_LIMIT="2048M"
      NGINX_LIMIT="512M"
      MYSQL_LIMIT="2048M"
      ;;
      
    "very-high") # 超高内存模式 (>16GB)
      MYSQL_BUFFER_POOL_SIZE="1024M"
      MYSQL_LOG_BUFFER_SIZE="64M"
      MYSQL_QUERY_CACHE_SIZE="128M"
      MYSQL_TMP_TABLE_SIZE="256M"
      MYSQL_KEY_BUFFER_SIZE="256M"
      MYSQL_MAX_CONNECTIONS="400"
      MYSQL_TABLE_OPEN_CACHE="800"
      
      JAVA_XMX="2048m"
      JAVA_XMS="1536m"
      JAVA_METASPACE="512m"
      JAVA_CLASS_SPACE="256m"
      JAVA_XSS="1m"
      
      JAVA_LIMIT="3072M"
      PYTHON_LIMIT="3072M"
      NGINX_LIMIT="512M"
      MYSQL_LIMIT="3072M"
      ;;
      
    *)
      error "未知的内存优化模式: $MEMORY_MODE"
      return 1
      ;;
  esac
  
  # 1. 创建/更新MySQL配置
  info "创建/更新MySQL配置文件..."
  # 创建新配置
  cat > docker/mysql/conf/my.cnf << EOF
[mysqld]
# $MEMORY_MODE 内存环境MySQL配置 (总内存: ${TOTAL_MEM_GB}GB)
performance_schema = $([ "$MEMORY_MODE" = "very-low" ] && echo "off" || echo "on")
table_open_cache = $MYSQL_TABLE_OPEN_CACHE
max_connections = $MYSQL_MAX_CONNECTIONS
innodb_buffer_pool_size = $MYSQL_BUFFER_POOL_SIZE
innodb_log_buffer_size = $MYSQL_LOG_BUFFER_SIZE
query_cache_size = $MYSQL_QUERY_CACHE_SIZE
tmp_table_size = $MYSQL_TMP_TABLE_SIZE
key_buffer_size = $MYSQL_KEY_BUFFER_SIZE
innodb_ft_cache_size = $([ "$MEMORY_MODE" = "very-low" ] && echo "4M" || echo "8M")
innodb_ft_total_cache_size = $([ "$MEMORY_MODE" = "very-low" ] && echo "32M" || echo "64M")
thread_cache_size = $([ "$MEMORY_MODE" = "very-low" ] && echo "4" || echo "8")

# 字符集配置
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
character-set-client-handshake = TRUE
init_connect = 'SET NAMES utf8mb4'

[client]
default-character-set = utf8mb4

[mysql]
default-character-set = utf8mb4
EOF
  success "MySQL配置更新完成"
  
  # 2. 更新docker-compose.yml中的资源限制 - 使用更简单的方法
  info "更新Docker Compose服务资源限制..."
  
  # 备份docker-compose.yml
  cp docker-compose.yml docker-compose.yml.resource_backup
  
  # 使用yq工具更新资源限制（如果可用）
  if command -v yq &> /dev/null; then
    info "使用yq工具更新资源限制..."
    
    # 定义要更新的服务和限制
    services=("java-backend" "python-backend" "nginx" "mysql")
    limits=("$JAVA_LIMIT" "$PYTHON_LIMIT" "$NGINX_LIMIT" "$MYSQL_LIMIT")
    
    for i in "${!services[@]}"; do
      yq eval ".services.${services[$i]}.deploy.resources.limits.memory = \"${limits[$i]}\"" -i docker-compose.yml || true
    done
    
    # 再次尝试使用poetize-前缀的服务名
    alt_services=("poetize-java" "poetize-python" "poetize-nginx" "poetize-mysql")
    for i in "${!alt_services[@]}"; do
      yq eval ".services.${alt_services[$i]}.deploy.resources.limits.memory = \"${limits[$i]}\"" -i docker-compose.yml || true
    done
  else
    # 如果不可用yq工具，退回到原始方法但优化
    info "使用基本文本处理工具更新资源限制..."
    
    # 检查是否已有内存限制配置
    if grep -q "memory:" docker-compose.yml; then
      # 如果已有限制配置，使用sed直接更新内存限制值
      info "更新已有的资源限制配置..."
      sed_i -E "s/(poetize-java:.+memory:) [0-9]+[MG]/\1 $JAVA_LIMIT/g" docker-compose.yml
      sed_i -E "s/(java-backend:.+memory:) [0-9]+[MG]/\1 $JAVA_LIMIT/g" docker-compose.yml
      sed_i -E "s/(poetize-python:.+memory:) [0-9]+[MG]/\1 $PYTHON_LIMIT/g" docker-compose.yml
      sed_i -E "s/(python-backend:.+memory:) [0-9]+[MG]/\1 $PYTHON_LIMIT/g" docker-compose.yml
      sed_i -E "s/(poetize-nginx:.+memory:) [0-9]+[MG]/\1 $NGINX_LIMIT/g" docker-compose.yml
      sed_i -E "s/(nginx:.+memory:) [0-9]+[MG]/\1 $NGINX_LIMIT/g" docker-compose.yml
      sed_i -E "s/(poetize-mysql:.+memory:) [0-9]+[MG]/\1 $MYSQL_LIMIT/g" docker-compose.yml
      sed_i -E "s/(mysql:.+memory:) [0-9]+[MG]/\1 $MYSQL_LIMIT/g" docker-compose.yml
    else
      # 简化的资源限制添加逻辑 - 逐个服务添加并验证
      info "添加新的资源限制配置..."
      
      # 定义要处理的服务
      services=("java-backend" "python-backend" "nginx" "mysql")
      alt_services=("poetize-java" "poetize-python" "poetize-nginx" "poetize-mysql")
      limits=("$JAVA_LIMIT" "$PYTHON_LIMIT" "$NGINX_LIMIT" "$MYSQL_LIMIT")
      
      # 对每个服务尝试添加资源限制
      for i in "${!services[@]}"; do
        service="${services[$i]}"
        alt_service="${alt_services[$i]}"
        limit="${limits[$i]}"
        
        # 创建临时文件
        cp docker-compose.yml docker-compose.yml.tmp
        
        # 尝试找到服务定义行
        service_line=$(grep -n "^  ${service}:" docker-compose.yml | head -1 | cut -d ":" -f1)
        
        # 如果找不到，尝试使用替代名称
        if [ -z "$service_line" ]; then
          service_line=$(grep -n "^  ${alt_service}:" docker-compose.yml | head -1 | cut -d ":" -f1)
          if [ -n "$service_line" ]; then
            service="${alt_service}"
          fi
        fi
        
        # 如果找到服务定义，添加资源限制
        if [ -n "$service_line" ]; then
          info "为服务 $service 添加资源限制..."
          
          # 检查是否已有deploy部分
          if ! grep -A 10 "^  ${service}:" docker-compose.yml | grep -q "deploy:"; then
            # 在服务定义下直接添加deploy部分
            sed_i "${service_line}a\\    deploy:\\n      resources:\\n        limits:\\n          memory: $limit" docker-compose.yml.tmp
            
            # 验证修改是否有效
            if validate_compose_file < docker-compose.yml.tmp; then
              # 如果验证通过，应用更改
              cp docker-compose.yml.tmp docker-compose.yml
              info "成功为服务 $service 添加资源限制"
            else
              # 验证失败，尝试其他格式
              cp docker-compose.yml docker-compose.yml.tmp
              # 尝试找出服务的第一个配置项
              first_config_line=$(awk "/^  ${service}:/,/^  [a-zA-Z]/" docker-compose.yml | grep -n "    [a-zA-Z]" | head -1 | cut -d ":" -f1)
              if [ -n "$first_config_line" ]; then
                # 计算行号
                insert_line=$((service_line + first_config_line - 1))
                # 在第一个配置项之前添加
                sed_i "${insert_line}i\\    deploy:\\n      resources:\\n        limits:\\n          memory: $limit" docker-compose.yml.tmp
                
                # 再次验证
                if validate_compose_file < docker-compose.yml.tmp; then
                  cp docker-compose.yml.tmp docker-compose.yml
                  info "成功为服务 $service 添加资源限制(插入方式)"
                else
                  warning "无法为服务 $service 添加资源限制，跳过"
                fi
              else
                warning "无法为服务 $service 添加资源限制，找不到合适的插入点"
              fi
            fi
          else
            # 如果已有deploy部分，尝试更新内存限制
            info "服务 $service 已有deploy部分，尝试更新内存限制"
            
            # 查找是否有memory字段
            if grep -A 15 "^  ${service}:" docker-compose.yml | grep -q "memory:"; then
              # 更新内存值
              awk -v svc="$service" -v limit="$limit" '
              BEGIN { in_svc = 0; in_deploy = 0; in_resources = 0; in_limits = 0; }
              {
                if ($0 ~ "^  "svc":") in_svc = 1;
                else if (in_svc && $0 ~ /^  [a-zA-Z]/) in_svc = 0;
                
                if (in_svc && $0 ~ /deploy:/) in_deploy = 1;
                else if (in_deploy && $0 !~ /^    /) in_deploy = 0;
                
                if (in_deploy && $0 ~ /resources:/) in_resources = 1;
                else if (in_resources && $0 !~ /^      /) in_resources = 0;
                
                if (in_resources && $0 ~ /limits:/) in_limits = 1;
                else if (in_limits && $0 !~ /^        /) in_limits = 0;
                
                if (in_limits && $0 ~ /memory:/)
                  print "          memory: " limit;
                else
                  print $0;
              }' docker-compose.yml > docker-compose.yml.tmp
              
              # 验证修改
              if validate_compose_file < docker-compose.yml.tmp; then
                cp docker-compose.yml.tmp docker-compose.yml
                info "成功更新服务 $service 的内存限制"
              else
                warning "无法更新服务 $service 的内存限制，跳过"
              fi
            else
              # 需要添加memory字段
              warning "服务 $service 有deploy部分但缺少memory配置，需手动配置"
            fi
          fi
        fi
        
        # 清理临时文件
        rm -f docker-compose.yml.tmp
      done
    fi
  fi
  
  # 验证修改后的文件
  if $DOCKER_COMPOSE_CMD config -q >/dev/null 2>&1; then
    success "添加服务资源限制完成"
  else
    warning "配置文件格式错误，恢复原配置"
    cp docker-compose.yml.resource_backup docker-compose.yml
    rm -f docker-compose.yml.resource_backup
    return 1
  fi
  
  # 3. 添加/更新Java环境变量以优化JVM内存使用
  info "更新Java服务JVM内存参数..."
  local JAVA_OPTS="-Xmx$JAVA_XMX -Xms$JAVA_XMS -XX:MaxMetaspaceSize=$JAVA_METASPACE -XX:CompressedClassSpaceSize=$JAVA_CLASS_SPACE -Xss$JAVA_XSS -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=35 -XX:+DisableExplicitGC"
  
  # 直接替换已存在的JAVA_OPTS行
  if grep -q "JAVA_OPTS=" docker-compose.yml; then
    info "找到现有的JAVA_OPTS配置，进行替换..."
    sed_i "s|JAVA_OPTS=.*|JAVA_OPTS=$JAVA_OPTS|g" docker-compose.yml
    success "更新Java服务JVM内存参数完成"
  else
    warning "未找到现有的JAVA_OPTS配置，跳过JVM参数优化"
  fi
  
  success "$MEMORY_MODE 内存模式优化配置完成"
  info "系统将使用动态优化的内存设置 (总内存: ${TOTAL_MEM_GB}GB)"
}

# 添加一个函数用于检查和安装bc命令
check_and_install_bc() {
  if ! command -v bc &>/dev/null; then
    # 检测系统类型
    local os_type=$(detect_os_type)
    # 根据操作系统类型安装curl
    case "$os_type" in
    "debian"|"ubuntu")
      # Ubuntu/Debian系统
      info "使用apt-get安装bc..."
      if sudo apt-get install -y bc; then
        success "bc安装成功"
      else
        error "bc安装失败，请手动安装: sudo apt-get install bc"
        return 1
      fi
      ;;
      "centos7")
      # CentOS/RHEL/Anolis系统
      info "使用yum安装Git..."
      if sudo yum install -y bc; then
        success "bc安装成功"
      else
        error "bc安装失败，请手动安装: sudo yum install bc"
            return 1
      fi
      ;;
    "fedora"|"centos8"|"anolis")
      # Fedora系统
      info "使用dnf安装Git..."
      if sudo dnf install -y bc; then
        success "bc安装成功"
      else
        error "bc安装失败，请手动安装: sudo dnf install bc"
            return 1
        fi
      ;;
    "arch")
      # Arch Linux系统
      info "使用pacman安装Git..."
      if sudo pacman -S --noconfirm bc; then
        success "bc安装成功"
      else
        error "bc安装失败，请手动安装: sudo pacman -S bc"
        return 1
    fi
      ;;
    "alpine")
      # Alpine Linux系统
      info "使用apk安装bc..."
      if sudo apk add bc; then
        success "bc安装成功"
      else
        error "bc安装失败，请手动安装: sudo apk add bc"
        return 1
      fi
      ;;
    *)
      error "不支持的操作系统类型: $os_type，请手动安装bc"
      echo "常见安装命令："
      echo "  Ubuntu/Debian: sudo apt-get install bc"
      echo "  CentOS/RHEL:   sudo yum install bc"
      echo "  Fedora:        sudo dnf install bc"
      echo "  Arch Linux:    sudo pacman -S bc"
      echo "  Alpine Linux:  sudo apk add bc"
      return 1
      ;;
  esac
  fi
  # 再次检查是否安装成功
  if ! command -v bc &>/dev/null; then
    warning "bc安装失败，将使用替代方法进行浮点数比较"
    return 1
  else
    success "bc安装成功"
  fi
  return 0
}

# 自定义函数用于浮点数比较，不依赖bc命令
float_lte() {
  # 将参数转换为整数，扩大1000倍
  local a=$(echo "$1" | sed 's/\.//')
  local b=$(echo "$2" | sed 's/\.//')
  
  # 补齐位数，确保正确比较
  while [ ${#a} -lt ${#b} ]; do
    a="${a}0"
  done
  
  while [ ${#b} -lt ${#a} ]; do
    b="${b}0"
  done
  
  # 整数比较
  [ "$a" -le "$b" ]
  return $?
}

# 对docker-compose.yml进行修改之前，先定义一个验证函数
validate_compose_file() {
  if $DOCKER_COMPOSE_CMD config -q >/dev/null 2>&1; then
    return 0
  else
    return 1
  fi
}

# 检查本地是否有离线安装包和镜像
check_offline_resources() {
  local found=0
  
  # 创建离线资源目录（如果不存在）
  if [ ! -d "./offline" ]; then
    mkdir -p "./offline/images" 2>/dev/null || true
  fi
  
  # 检查离线Docker安装包
  if [ -f "./offline/docker.tar.gz" ]; then
    info "发现本地Docker离线安装包"
    found=1
  fi
  
  # 检查离线Docker Compose安装包
  if [ -f "./offline/docker-compose" ]; then
    info "发现本地Docker Compose离线安装包"
    found=1
  fi
  
  # 检查离线镜像包
  if [ -d "./offline/images" ] && [ "$(ls -A ./offline/images/*.tar 2>/dev/null)" ]; then
    info "发现本地Docker镜像包"
    found=1
  fi
  
  return $found
}

# 从离线包安装Docker
install_docker_offline() {
  info "使用离线安装包安装Docker..."
  
  if [ -f "./offline/docker.tar.gz" ]; then
    info "解压Docker离线安装包..."
    mkdir -p /tmp/docker_offline
    tar -xzf ./offline/docker.tar.gz -C /tmp/docker_offline
    
    if [ -f /tmp/docker_offline/install.sh ]; then
      info "执行离线安装脚本..."
      sudo chmod +x /tmp/docker_offline/install.sh
      /tmp/docker_offline/install.sh
      
      # 检查安装结果
      if command -v docker &>/dev/null; then
        success "从离线包安装Docker成功"
        return 0
      else
        warning "从离线包安装Docker失败，将尝试在线安装"
      fi
    elif [ -d /tmp/docker_offline/bin ]; then
      info "复制Docker二进制文件到系统路径..."
      # 复制二进制文件
      sudo cp -f /tmp/docker_offline/bin/* /usr/bin/ || cp -f /tmp/docker_offline/bin/* /usr/bin/
      
      # 设置执行权限
      sudo chmod +x /usr/bin/docker* || chmod +x /usr/bin/docker*
      
      # 如果有systemd服务文件，安装
      if [ -f /tmp/docker_offline/docker.service ]; then
        sudo cp -f /tmp/docker_offline/docker.service /etc/systemd/system/ || cp -f /tmp/docker_offline/docker.service /etc/systemd/system/
        sudo systemctl daemon-reload || true
        sudo systemctl enable docker || true
        sudo systemctl start docker || true
      fi
      
      # 检查安装结果
      if command -v docker &>/dev/null; then
        success "从离线二进制文件安装Docker成功"
        return 0
      else
        warning "从离线二进制文件安装Docker失败，将尝试在线安装"
      fi
    else
      warning "离线安装包格式不正确，将尝试在线安装"
    fi
  else
    warning "未找到离线Docker安装包"
  fi
  
  return 1
}

# 加载离线Docker镜像
load_offline_images() {
  if [ -d "./offline/images" ] && [ "$(ls -A ./offline/images/*.tar 2>/dev/null)" ]; then
    info "正在加载离线Docker镜像..."
    
    # 确保docker已安装
    if ! command -v docker &>/dev/null; then
      error "Docker未安装，无法加载镜像"
      return 1
    fi
    
    # 加载所有tar包中的镜像
    for image in ./offline/images/*.tar; do
      [ -f "$image" ] || continue
      
      image_name=$(basename "$image" .tar)
      info "加载镜像: $image_name"
      
      if docker load -i "$image"; then
        success "成功加载镜像: $image_name"
      else
        warning "加载镜像失败: $image_name"
      fi
    done
    
    # 显示已加载的镜像
    info "当前系统中的Docker镜像列表:"
    docker images
    
    return 0
  fi
  
  warning "未找到离线Docker镜像文件"
  return 1
}


# 检查并修复Dockerfile行终止符
fix_dockerfile_line_endings() {
  info "检查Dockerfile行终止符..."
  
  # 直接修复docker/java/Dockerfile
  if [ -f "docker/java/Dockerfile" ]; then
    info "修复Java Dockerfile行终止符..."
    sed_i 's/\r$//' docker/java/Dockerfile
  fi
  
  # 直接修复docker/python/Dockerfile
  if [ -f "docker/python/Dockerfile" ]; then
    info "修复Python Dockerfile行终止符..."
    sed_i 's/\r$//' docker/python/Dockerfile
  fi
  
  # 检查是否有dos2unix工具
  if ! command -v dos2unix &>/dev/null; then
    info "安装dos2unix工具..."
    if command -v apt-get &>/dev/null; then
      apt-get update -qq && apt-get install -y -qq dos2unix >/dev/null 2>&1
    elif command -v yum &>/dev/null; then
      yum install -y -q dos2unix >/dev/null 2>&1
    elif command -v dnf &>/dev/null; then
      dnf install -y -q dos2unix >/dev/null 2>&1
    else
      warning "无法安装dos2unix工具，将使用替代方法修复文件"
      # 使用sed替代dos2unix，修复所有Dockerfile
      find docker -name "Dockerfile" -type f -exec sed_i 's/\r$//' {} \;
      
      # 额外检查Java和Python的Dockerfile
      for dir in docker/*/; do
        if [ -f "${dir}Dockerfile" ]; then
          info "额外修复 ${dir}Dockerfile"
          sed_i 's/\r$//' "${dir}Dockerfile"
          # 确保文件每行末尾有换行符
          if [ "$(tail -c 1 "${dir}Dockerfile" | wc -l)" -eq 0 ]; then
            echo "" >> "${dir}Dockerfile"
          fi
        fi
      done
      return
    fi
  fi
  
  # 使用dos2unix修复所有Dockerfile
  find docker -name "Dockerfile" -type f -exec dos2unix {} \; 2>/dev/null
  
  # 确保文件每行末尾有换行符
  for dir in docker/*/; do
    if [ -f "${dir}Dockerfile" ]; then
      if [ "$(tail -c 1 "${dir}Dockerfile" | wc -l)" -eq 0 ]; then
        echo "" >> "${dir}Dockerfile"
      fi
    fi
  done
  
  success "Dockerfile行终止符已修复"
}

# 安全地运行Docker Compose命令，确保参数正确传递
run_docker_compose() {
    # 验证Docker Compose命令是否可用
    if [ -z "$DOCKER_COMPOSE_CMD" ]; then
        error "Docker Compose命令未设置，无法执行命令"
        return 1
    fi
    
    info "执行Docker Compose命令: $DOCKER_COMPOSE_CMD $*"
    
    # 根据命令是docker-compose还是docker compose分别处理
    if [ "$DOCKER_COMPOSE_CMD" = "docker-compose" ]; then
        # 使用docker-compose命令
        docker-compose "$@"
    elif [ "$DOCKER_COMPOSE_CMD" = "docker compose" ]; then
        # 使用docker compose命令
        docker compose "$@"
    else
        # 使用eval作为后备方案
        eval "$DOCKER_COMPOSE_CMD $*"
    fi
    
    return $?
}

# 设置Docker Compose命令
setup_docker_compose_command() {
    # 检查是否在WSL环境中
    if grep -q Microsoft /proc/version 2>/dev/null; then
        info "检测到WSL环境"
        
        # 检查Docker Desktop是否在WSL中可用
        if ! docker info &>/dev/null; then
            error "Docker在WSL中不可用"
            echo ""
            echo -e "${BLUE}=== 在WSL中使用Docker推荐方法 ===${NC}"
            echo "1. 确保已安装Docker Desktop for Windows"
            echo "2. 确保Docker Desktop正在运行"
            echo "3. 在Docker Desktop设置中:"
            echo "   - 勾选 'Use the WSL 2 based engine'"
            echo "   - 在 'Resources > WSL Integration' 中启用当前WSL发行版"
            echo ""
            
            read -p "是否安装Docker? (y/n/s) [y=安装, n=退出, s=跳过尝试继续]: " -n 1 -r
            echo ""
            if [[ $REPLY =~ ^[Yy]$ ]]; then
              if ! install_docker; then
                error "Docker安装失败，无法继续部署"
                exit 1
              fi
            elif [[ $REPLY =~ ^[Ss]$ ]]; then
              warning "跳过Docker安装，尝试继续部署"
              warning "某些功能可能无法正常工作"
            else
              error "已取消部署"
              exit 1
            fi
        fi
        
        # 优先检查新版docker compose命令
        if docker compose version &>/dev/null; then
            info "将使用新版 'docker compose' 命令"
            DOCKER_COMPOSE_CMD="docker compose"
        elif command -v docker-compose &>/dev/null && docker-compose --version &>/dev/null; then
            info "将使用旧版 'docker-compose' 命令"
            DOCKER_COMPOSE_CMD="docker-compose"
        else
            warning "Docker Compose未启用，尝试使用docker compose子命令"
            DOCKER_COMPOSE_CMD="docker compose"
        fi
    else
        # 非WSL环境，优先检查新版docker compose命令
        if command -v docker &>/dev/null && docker compose version &>/dev/null; then
            info "检测到新版docker compose命令可用"
            DOCKER_COMPOSE_CMD="docker compose"
        elif command -v docker-compose &>/dev/null && docker-compose --version &>/dev/null; then
            info "检测到旧版docker-compose命令可用"
            DOCKER_COMPOSE_CMD="docker-compose"
        else
            error "无法找到可用的Docker Compose命令"
            exit 1
        fi
    fi
    
    # 最终验证所选命令
    info "测试Docker Compose命令..."
    if ! eval "$DOCKER_COMPOSE_CMD --version" &>/dev/null; then
        error "所选Docker Compose命令无法执行: $DOCKER_COMPOSE_CMD"
        if grep -q Microsoft /proc/version 2>/dev/null; then
            info "在WSL环境中，请在Docker Desktop设置中启用WSL集成"
            info "参考: https://docs.docker.com/desktop/wsl/"
        fi
        exit 1
    fi
    
    info "将使用命令: $DOCKER_COMPOSE_CMD"
}

# 清理Docker构建缓存
clean_docker_build_cache() {
  info "清理Docker构建缓存以释放磁盘空间..."
  if docker builder prune -af --filter until=24h >/dev/null 2>&1; then
    success "Docker构建缓存清理成功"
  else
    warning "Docker构建缓存清理失败，可能需要手动执行: docker builder prune -af"
  fi
}

# 生成随机强密码函数
generate_secure_password() {
  length=${1:-24}  # 默认长度增加到24以补偿移除特殊字符带来的熵损失
  # 仅使用字母和数字，完全避免任何特殊字符
  tr -dc 'a-zA-Z0-9' < /dev/urandom | head -c ${length}
}

# 替换数据库密码
replace_db_passwords() {
  echo "生成随机数据库密码..."
  
  # 生成随机密码
  ROOT_PASSWORD=$(generate_secure_password 24)
  USER_PASSWORD=$(generate_secure_password 24)
  
  # 替换docker-compose.yml中的默认密码
  sed_i "s/MARIADB_ROOT_PASSWORD=root123/MARIADB_ROOT_PASSWORD=${ROOT_PASSWORD}/g" docker-compose.yml
  sed_i "s/MARIADB_PASSWORD=poetize123/MARIADB_PASSWORD=${USER_PASSWORD}/g" docker-compose.yml
  
  # 同时更新Java服务的数据库连接密码
  sed_i "s/SPRING_DATASOURCE_PASSWORD=poetize123/SPRING_DATASOURCE_PASSWORD=${USER_PASSWORD}/g" docker-compose.yml
  
  # 更新Python服务的数据库连接密码
  sed_i "s/DB_PASSWORD=poetize123/DB_PASSWORD=${USER_PASSWORD}/g" docker-compose.yml
  sed_i "s/MYSQL_PASSWORD=poetize123/MYSQL_PASSWORD=${USER_PASSWORD}/g" docker-compose.yml
  sed_i "s/DATABASE_PASSWORD=poetize123/DATABASE_PASSWORD=${USER_PASSWORD}/g" docker-compose.yml
  sed_i "s/MARIADB_USER_PASSWORD=poetize123/MARIADB_USER_PASSWORD=${USER_PASSWORD}/g" docker-compose.yml
  
  # 替换command部分中的默认密码(处理单引号和双引号的情况)
  sed_i "s|mariadb-admin ping -h localhost -u root -proot123|mariadb-admin ping -h localhost -u root -p${ROOT_PASSWORD}|g" docker-compose.yml
  sed_i "s|mariadb -h localhost -u poetize -ppoetize123|mariadb -h localhost -u poetize -p${USER_PASSWORD}|g" docker-compose.yml
  
  # 替换healthcheck部分中的默认密码 - 更新为支持CMD-SHELL格式
  sed_i "s|mariadb-admin ping -h localhost -u poetize -ppoetize123|mariadb-admin ping -h localhost -u poetize -p${USER_PASSWORD}|g" docker-compose.yml
  
  # 保存密码到本地安全文件
  mkdir -p .config
  cat > .config/db_credentials.txt <<EOF
# MariaDB 数据库凭据 - 请妥善保管此文件
# 生成时间: $(date)

数据库ROOT密码: ${ROOT_PASSWORD}
数据库poetize用户密码: ${USER_PASSWORD}

# 这些密码已被自动配置到docker-compose.yml中
# 如需手动连接数据库，请使用以上凭据
EOF
  
  # 设置安全权限
  chmod 600 .config/db_credentials.txt
  
  echo "====================================================="
  echo "      数据库密码已成功更新为随机强密码"
  echo "====================================================="
  echo ""
  echo "数据库ROOT密码: ${ROOT_PASSWORD}"
  echo "数据库poetize用户密码: ${USER_PASSWORD}"
  echo ""
  echo "以上密码已保存到 .config/db_credentials.txt"
  echo "请妥善保管此文件，并在部署完成后备份到安全位置"
  echo "====================================================="
}

# 检查和修复MySQL配置文件权限
fix_mysql_config_permissions() {
  info "检查并修复MySQL配置文件权限..."
  
  if [ -f "./docker/mysql/conf/my.cnf" ]; then
    # 获取当前权限
    current_perm=$(stat -c "%a" ./docker/mysql/conf/my.cnf 2>/dev/null || stat -f "%Lp" ./docker/mysql/conf/my.cnf 2>/dev/null)
    
    # 如果权限不是644，则修改
    if [ "$current_perm" != "644" ]; then
      info "MySQL配置文件权限不正确，当前权限: $current_perm，修改为644..."
      chmod 644 ./docker/mysql/conf/my.cnf
      success "MySQL配置文件权限已修复"
    else
      info "MySQL配置文件权限正确: 644"
    fi
  else
    warning "MySQL配置文件 ./docker/mysql/conf/my.cnf 不存在，将在首次运行时创建"
  fi
}

# 验证HTTPS状态和配置
verify_https_status() {
  info "验证HTTPS配置状态..."
  
  local https_working=false
  local cert_valid=false
  local nginx_https_enabled=false
  
  # 1. 检查Nginx配置是否启用了HTTPS
  info "检查Nginx HTTPS配置..."
  if docker exec poetize-nginx nginx -T 2>/dev/null | grep -q "listen.*443.*ssl"; then
    nginx_https_enabled=true
    success "✓ Nginx已配置HTTPS监听端口"
  else
    warning "✗ Nginx未配置HTTPS监听端口"
    info "当前Nginx配置中的监听端口:"
    docker exec poetize-nginx nginx -T 2>/dev/null | grep "listen" | head -5 || echo "无法获取监听端口信息"
  fi
  
  # 2. 检查SSL证书文件是否存在
  info "检查SSL证书文件..."
  if docker exec poetize-nginx test -f "/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem" 2>/dev/null; then
    cert_valid=true
    success "✓ SSL证书文件存在: /etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem"
    
    # 检查证书有效期
    CERT_EXPIRY=$(docker exec poetize-nginx openssl x509 -in "/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem" -noout -enddate 2>/dev/null | cut -d= -f2)
    if [ -n "$CERT_EXPIRY" ]; then
      info "证书有效期至: $CERT_EXPIRY"
    fi
  else
    warning "✗ SSL证书文件不存在"
    info "检查Let's Encrypt目录结构:"
    docker exec poetize-nginx ls -la /etc/letsencrypt/live/ 2>/dev/null || echo "Let's Encrypt目录不存在"
  fi
  
  # 3. 测试HTTPS连接（如果不是本地域名）
  if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    info "测试HTTPS连接..."
    
    # 给服务器一点时间来重新加载配置
    sleep 3
    
    # 使用curl测试HTTPS连接
    if command -v curl &>/dev/null; then
      HTTPS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "https://$PRIMARY_DOMAIN" --connect-timeout 10 --max-time 15 2>/dev/null || echo "000")
      
      if [ "$HTTPS_STATUS" = "200" ] || [ "$HTTPS_STATUS" = "301" ] || [ "$HTTPS_STATUS" = "302" ]; then
        https_working=true
        success "✓ HTTPS连接测试成功 (状态码: $HTTPS_STATUS)"
      else
        warning "✗ HTTPS连接测试失败 (状态码: $HTTPS_STATUS)"
        
        # 尝试诊断问题
        info "尝试诊断HTTPS问题..."
        CURL_ERROR=$(curl -v "https://$PRIMARY_DOMAIN" 2>&1 | head -10 || echo "curl命令失败")
        echo "连接详情: $CURL_ERROR"
      fi
    else
      # 如果没有curl，尝试使用openssl测试SSL握手
      if command -v openssl &>/dev/null; then
        info "使用OpenSSL测试SSL握手..."
        if echo | openssl s_client -connect "$PRIMARY_DOMAIN:443" -servername "$PRIMARY_DOMAIN" 2>/dev/null | grep -q "CONNECTED"; then
          https_working=true
          success "✓ SSL握手测试成功"
        else
          warning "✗ SSL握手测试失败"
        fi
      else
        warning "无curl和openssl命令，无法测试HTTPS连接"
      fi
    fi
  else
    info "本地域名环境，跳过HTTPS连接测试"
  fi
  
  # 4. 检查容器日志中的错误
  info "检查容器日志中的SSL相关错误..."
  SSL_ERRORS=$(docker logs poetize-nginx 2>&1 | grep -i "ssl\|certificate\|tls" | tail -5 || echo "未发现SSL相关日志")
  if [ "$SSL_ERRORS" != "未发现SSL相关日志" ]; then
    warning "发现SSL相关日志:"
    echo "$SSL_ERRORS"
  fi
  
  # 5. 生成总结报告
  echo ""
  echo -e "${BLUE}=== HTTPS配置状态报告 ===${NC}"
  echo "Nginx HTTPS配置: $([ "$nginx_https_enabled" = true ] && echo "✓ 已启用" || echo "✗ 未启用")"
  echo "SSL证书文件: $([ "$cert_valid" = true ] && echo "✓ 存在" || echo "✗ 缺失")"
  echo "HTTPS连接测试: $([ "$https_working" = true ] && echo "✓ 正常" || echo "✗ 失败")"
  
  # 根据检查结果给出建议
  if [ "$nginx_https_enabled" = true ] && [ "$cert_valid" = true ] && [ "$https_working" = true ]; then
    success "🎉 HTTPS配置完全正常！您现在可以通过 https://$PRIMARY_DOMAIN 访问网站"
    
    # 检查HTTP重定向是否工作
    if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      info "检查HTTP到HTTPS重定向..."
      HTTP_REDIRECT=$(curl -s -o /dev/null -w "%{http_code}" "http://$PRIMARY_DOMAIN" --connect-timeout 10 2>/dev/null || echo "000")
      if [ "$HTTP_REDIRECT" = "301" ] || [ "$HTTP_REDIRECT" = "302" ]; then
        success "✓ HTTP到HTTPS重定向工作正常"
      else
        info "HTTP状态码: $HTTP_REDIRECT (可能需要手动配置重定向)"
      fi
    fi
    
    return 0
  else
    warning "HTTPS配置存在问题，需要进一步排查"
    
    echo ""
    echo -e "${YELLOW}=== 故障排除建议 ===${NC}"
    
    if [ "$nginx_https_enabled" = false ]; then
      echo "1. Nginx HTTPS配置问题:"
      echo "   - 运行: docker exec poetize-nginx /enable-https.sh"
      echo "   - 检查: docker exec poetize-nginx nginx -t"
    fi
    
    if [ "$cert_valid" = false ]; then
      echo "2. SSL证书问题:"
      echo "   - 检查certbot日志: docker logs poetize-certbot"
      echo "   - 重新申请证书: docker restart poetize-certbot"
      echo "   - 确认域名DNS指向正确"
    fi
    
    if [ "$https_working" = false ]; then
      echo "3. HTTPS连接问题:"
      echo "   - 检查防火墙是否开放443端口"
      echo "   - 确认域名解析正确"
      echo "   - 重启Nginx: docker restart poetize-nginx"
    fi
    
    echo ""
    echo "如果问题持续存在，请:"
    echo "- 等待几分钟后重试（DNS和证书可能需要时间生效）"
    echo "- 运行: docker exec poetize-nginx /enable-https.sh"
    echo "- 查看完整日志获取更多信息"
    
    return 1
  fi
}


# 检查项目环境
check_project_environment() {
  # 定义需要检测的目录和文件
  local directories=("docker" "poetize-server" "py" "poetize-ui")
  local files=("docker-compose.yml")
  
  # 静默检测所有目录和文件
  for dir in "${directories[@]}"; do
    if [ ! -d "$dir" ]; then
      return 1
    fi
  done
  
  for file in "${files[@]}"; do
    if [ ! -f "$file" ]; then
      return 1
    fi
  done
  
  # 所有文件都存在
  return 0
}

install_git() {
  # 检测系统类型
  local os_type=$(detect_os_type)  
  # 根据操作系统类型安装Git
  case "$os_type" in
    "debian"|"ubuntu")
      # Ubuntu/Debian系统
      info "使用apt-get安装Git..."
      if sudo apt-get update && sudo apt-get install -y git; then
        success "Git安装成功"
      else
        error "Git安装失败，请手动安装: sudo apt-get install git"
        return 1
      fi
      ;;
    "centos7")
      # CentOS/RHEL/Anolis系统
      info "使用yum安装Git..."
      if sudo yum install -y git; then
        success "Git安装成功"
      else
        error "Git安装失败，请手动安装: sudo yum install git"
        return 1
      fi
      ;;
    "fedora"|"centos8"|"anolis")
      # Fedora系统
      info "使用dnf安装Git..."
      if sudo dnf install -y git; then
        success "Git安装成功"
      else
        error "Git安装失败，请手动安装: sudo dnf install git"
        return 1
      fi
      ;;
    "arch")
      # Arch Linux系统
      info "使用pacman安装Git..."
      if sudo pacman -S --noconfirm git; then
        success "Git安装成功"
      else
        error "Git安装失败，请手动安装: sudo pacman -S git"
        return 1
      fi
      ;;
    "alpine")
      # Alpine Linux系统
      info "使用apk安装Git..."
      if sudo apk add git; then
        success "Git安装成功"
      else
        error "Git安装失败，请手动安装: sudo apk add git"
        return 1
      fi
      ;;
    *)
      error "不支持的操作系统类型: $os_type，请手动安装Git"
      echo "常见安装命令："
      echo "  Ubuntu/Debian: sudo apt-get install git"
      echo "  CentOS/RHEL:   sudo yum install git"
      echo "  Fedora:        sudo dnf install git"
      echo "  Arch Linux:    sudo pacman -S git"
      echo "  Alpine Linux:  sudo apk add git"
      return 1
      ;;
  esac
}

# 下载并解压项目源码
download_and_extract_project() {
  local download_url="https://github.com/LeapYa/Awesome-poetize-open/releases/download/1.0.0/Awesome-poetize-open.tar.gz"
  local tar_file="Awesome-poetize-open.tar.gz"
  local extract_dir="Awesome-poetize-open"
  local repo_url="https://gitee.com/leapya/poetize.git"
  
  info "正在下载项目源码..."
  
  if is_china_environment; then
    git clone --depth 1 "$repo_url" "$extract_dir"
    rm -rf "$extract_dir/.git"
    if [ $? -ne 0 ]; then
      error "项目源码克隆失败"
      return 1
    fi
  else
      # 下载源码包
      if command -v wget &> /dev/null; then
        wget "$download_url"
      elif command -v curl &> /dev/null; then
        curl -sL "$download_url" -o "$tar_file"
      else
        error "未找到wget或curl命令，无法下载源码"
        return 1
      fi
      
      # 检查下载是否成功
      if [ ! -f "$tar_file" ]; then
        :
      else
        info "正在解压源码包..."
        # 解压源码包
        if tar -zxvf "$tar_file"; then
          success "源码解压成功"
        else
          error "源码解压失败"
          return 1
        fi
      fi
  fi

  # 创建项目目录并移动文件
  if [ -d "$extract_dir" ]; then
    cd "$extract_dir"
    info "已进入项目目录: $(pwd)"
    
    # 清理下载文件
    rm -f "../$tar_file"
    rm -rf "poetize-picture"
    rm -rf "README.md"
    
    success "项目环境准备完成"
  fi
}

# 环境检测后的处理逻辑
handle_environment_status() {

  check_project_environment
  status=$?
  
  if [ $status -eq 0 ]; then
    :
  else    
    if download_and_extract_project; then
      success "✅ 源码下载和解压完成，继续部署安装..."
      echo ""
    else
      error "❌ 源码下载失败，部署终止"
      exit 1
    fi
  fi
}

check_write_permission() {
  if [ ! -w "." ]; then
    error "当前目录没有写权限，请切换到有权限的目录"
    return 1
  fi
  return 0
}

# 添加跨平台系统更新函数
update_system_packages() {
  info "更新系统包列表..."
  
  # 使用现有的系统检测函数
  local os_type=$(detect_os_type)
  
  # 根据不同系统执行相应的更新命令
  case "$os_type" in
    ubuntu|debian)
      update_debian_based
      ;;
    centos7)
      update_centos7_based
      ;;
    centos8|fedora|anolis)
      update_centos8_based
      ;;
    arch)
      update_arch_based
      ;;
    alpine)
      update_alpine_based
      ;;
    unknown)
      warning "未识别的操作系统，跳过系统包更新"
      warning "请手动更新系统包列表"
      ;;
    *)
      warning "不支持的操作系统类型: $os_type，跳过系统包更新"
      ;;
  esac
}

# Debian/Ubuntu系统更新
update_debian_based() {
  if [ "$EUID" -eq 0 ]; then
    if apt update &>/dev/null; then
      success "系统包列表更新成功 (apt)"
    else
      warning "apt update 失败，但不影响部署继续"
    fi
  else
    if command -v sudo &>/dev/null; then
      if sudo apt update &>/dev/null; then
        success "系统包列表更新成功 (sudo apt)"
      else
        warning "sudo apt update 失败，但不影响部署继续"
      fi
    else
      warning "无权限执行 apt update，建议手动执行"
    fi
  fi
}

# CentOS 7系统更新
update_centos7_based() {
  if [ "$EUID" -eq 0 ]; then
    if yum check-update &>/dev/null || [ $? -eq 100 ]; then
      success "系统包列表更新成功 (yum)"
    else
      warning "yum check-update 失败，但不影响部署继续"
    fi
  else
    if command -v sudo &>/dev/null; then
      if sudo yum check-update &>/dev/null || [ $? -eq 100 ]; then
        success "系统包列表更新成功 (sudo yum)"
      else
        warning "sudo yum check-update 失败，但不影响部署继续"
      fi
    else
      warning "无权限执行 yum check-update，建议手动执行"
    fi
  fi
}

# CentOS 8/Fedora/Anolis系统更新
update_centos8_based() {
  if [ "$EUID" -eq 0 ]; then
    if dnf check-update &>/dev/null || [ $? -eq 100 ]; then
      success "系统包列表更新成功 (dnf)"
    else
      warning "dnf check-update 失败，但不影响部署继续"
    fi
  else
    if command -v sudo &>/dev/null; then
      if sudo dnf check-update &>/dev/null || [ $? -eq 100 ]; then
        success "系统包列表更新成功 (sudo dnf)"
      else
        warning "sudo dnf check-update 失败，但不影响部署继续"
      fi
    else
      warning "无权限执行 dnf check-update，建议手动执行"
    fi
  fi
}

# Arch Linux系统更新
update_arch_based() {
  if [ "$EUID" -eq 0 ]; then
    if pacman -Sy &>/dev/null; then
      success "系统包列表更新成功 (pacman)"
    else
      warning "pacman -Sy 失败，但不影响部署继续"
    fi
  else
    if command -v sudo &>/dev/null; then
      if sudo pacman -Sy &>/dev/null; then
        success "系统包列表更新成功 (sudo pacman)"
      else
        warning "sudo pacman -Sy 失败，但不影响部署继续"
      fi
    else
      warning "无权限执行 pacman -Sy，建议手动执行"
    fi
  fi
}

# Alpine Linux系统更新
update_alpine_based() {
  if [ "$EUID" -eq 0 ]; then
    if apk update &>/dev/null; then
      success "系统包列表更新成功 (apk)"
    else
      warning "apk update 失败，但不影响部署继续"
    fi
  else
    if command -v sudo &>/dev/null; then
      if sudo apk update &>/dev/null; then
        success "系统包列表更新成功 (sudo apk)"
      else
        warning "sudo apk update 失败，但不影响部署继续"
      fi
    else
      warning "无权限执行 apk update，建议手动执行"
    fi
  fi
}

# 主函数
main() {
  # 显示横幅
  echo ""
  printf "${BLUE}╔═══════════════════════════════════════════════════════════════════════════╗${NC}\n"
  printf "${BLUE}║                                                                           ║${NC}\n"
  printf "${BLUE}║                              ${GREEN}P O E T I Z E${BLUE}                                ║${NC}\n"
  printf "${BLUE}║                        ${YELLOW}* 优雅的博客与聊天平台 *${BLUE}                           ║${NC}\n"
  printf "${BLUE}║                                                                           ║${NC}\n"
  printf "${BLUE}╠═══════════════════════════════════════════════════════════════════════════╣${NC}\n"
  printf "${BLUE}║                                                                           ║${NC}\n"
  printf "${BLUE}║          ${GREEN}██████╗  ██████╗ ███████╗████████╗██╗███████╗███████╗${BLUE}            ║${NC}\n"
  printf "${BLUE}║          ${GREEN}██╔══██╗██╔═══██╗██╔════╝╚══██╔══╝██║╚══███╔╝██╔════╝${BLUE}            ║${NC}\n"
  printf "${BLUE}║          ${GREEN}██████╔╝██║   ██║█████╗     ██║   ██║  ███╔╝ █████╗${BLUE}              ║${NC}\n"
  printf "${BLUE}║          ${GREEN}██╔═══╝ ██║   ██║██╔══╝     ██║   ██║ ███╔╝  ██╔══╝${BLUE}              ║${NC}\n"
  printf "${BLUE}║          ${GREEN}██║     ╚██████╔╝███████╗   ██║   ██║███████╗███████╗${BLUE}            ║${NC}\n"
  printf "${BLUE}║          ${GREEN}╚═╝      ╚═════╝ ╚══════╝   ╚═╝   ╚═╝╚══════╝╚══════╝${BLUE}            ║${NC}\n"
  printf "${BLUE}║                                                                           ║${NC}\n"
  printf "${BLUE}╠═══════════════════════════════════════════════════════════════════════════╣${NC}\n"
  printf "${BLUE}║                                                                           ║${NC}\n"
  printf "${BLUE}║           ${YELLOW}* 作者: ${GREEN}LeapYa${BLUE}                                                  ║${NC}\n"
  printf "${BLUE}║           ${YELLOW}* 邮箱: ${GREEN}enable_lazy@qq.com${BLUE}                                      ║${NC}\n"
  printf "${BLUE}║           ${YELLOW}* 仓库: ${GREEN}https://github.com/LeapYa/Awesome-poetize-open${BLUE}          ║${NC}\n"
  printf "${BLUE}║                                                                           ║${NC}\n"
  printf "${BLUE}╚═══════════════════════════════════════════════════════════════════════════╝${NC}\n"
  
  echo -e "${YELLOW}✨ 正在初始化部署环境...${NC}"
  sleep 3
  echo ""

  check_write_permission
  status=$?
  if [ $status -eq 0 ]; then
    :
  else
    exit 1
  fi

  update_system_packages

  # 检查并安装curl
  check_and_install_curl

  if ! command -v git &> /dev/null; then
    warning "Git未安装，正在尝试安装..."
    if ! install_git; then
      error "Git安装失败，无法克隆源码"
      return 1
    fi
  fi

  # 检查并安装bc
  if check_and_install_bc; then
    :
  else
    error "bc安装失败，无法继续部署,请切换到root用户再试..."
    exit 1
  fi

  handle_environment_status
  
  # 解析命令行参数
  parse_arguments "$@"
  
  # 检查是否需要显示帮助
  if [ "$SHOW_HELP" = true ]; then
    show_help
    exit 0
  fi
  
  # 检查Docker环境
  info "检查Docker环境..."
  if ! docker info &>/dev/null; then
    if grep -q Microsoft /proc/version 2>/dev/null; then
      warning "Docker在WSL中不可用"
      echo ""
      echo -e "${BLUE}=== 在WSL中使用Docker推荐方法 ===${NC}"
      echo "1. 确保已安装Docker Desktop for Windows"
      echo "2. 确保Docker Desktop正在运行"
      echo "3. 在Docker Desktop设置中:"
      echo "   - 勾选 'Use the WSL 2 based engine'"
      echo "   - 在 'Resources > WSL Integration' 中启用当前WSL发行版"
      echo ""
      
      auto_confirm "是否安装Docker? (y/n/s) [y=安装, n=退出, s=跳过尝试继续]: " "y" "-n 1 -r"
      if [[ $REPLY =~ ^[Yy]$ ]]; then
        if ! install_docker; then
          error "Docker安装失败，无法继续部署"
          exit 1
        fi
      elif [[ $REPLY =~ ^[Ss]$ ]]; then
        warning "跳过Docker安装，尝试继续部署"
        warning "某些功能可能无法正常工作"
      else
        error "已取消部署"
        exit 1
      fi
    else
      info "Docker未安装，开始执行安装程序"
      install_docker
      success "Docker安装成功"
    fi
  else
    info "Docker已安装，无需执行安装程序"
  fi
  
  # 检查Docker Compose可用性
  if ! (command -v docker &>/dev/null && docker compose version &>/dev/null) && ! command -v docker-compose &>/dev/null; then
    if grep -q Microsoft /proc/version 2>/dev/null; then
      echo ""
      echo -e "${BLUE}=== 在WSL中使用Docker Compose ===${NC}"
      echo "1. 确保Docker Desktop已安装并正在运行"
      echo "2. Docker Desktop通常已包含Docker Compose功能"
      echo "3. 确保在WSL集成设置中启用了当前发行版"
      echo ""
      
      warning "Docker Compose不可用，请检查Docker安装"
      auto_confirm "是否继续部署? (y/n) [y=继续, n=退出]: " "y" "-n 1 -r"
      if [[ $REPLY =~ ^[Nn]$ ]]; then
        error "已取消部署"
        exit 1
      fi
        warning "将尝试使用docker命令直接管理容器"
      else
      warning "Docker Compose不可用，请确保安装了完整的Docker Engine"
      info "现代Docker安装通常已包含docker compose插件"
      auto_confirm "是否继续部署? (y/n) [y=继续, n=退出]: " "y" "-n 1 -r"
      if [[ $REPLY =~ ^[Nn]$ ]]; then
        error "已取消部署"
        exit 1
      fi
    fi
  else
    info "Docker Compose已可用"
  fi
  
  # 设置Docker Compose命令
  setup_docker_compose_command
  
  # 初始化SKIP_BUILD变量，默认为空
  SKIP_BUILD=""
  
  # 检查并加载离线Docker镜像
  if check_offline_resources; then
    info "检测到本地离线资源，检查并加载离线Docker镜像..."
    if load_offline_images; then
      # 如果成功加载离线镜像，设置跳过构建选项
      SKIP_BUILD="--no-build"
      info "已成功加载离线镜像，将跳过构建阶段"
    fi
  fi
  
  # 检查依赖
  check_dependencies
  
  
  # 如果没有输入域名，提示用户
  if [ ${#DOMAINS[@]} -eq 0 ]; then
    prompt_for_domains
  fi
  
  # 如果没有输入邮箱，提示用户
  if [ -z "$EMAIL" ]; then
    prompt_for_email
  fi
  
  # 确保PRIMARY_DOMAIN已设置
  if [ -z "$PRIMARY_DOMAIN" ]; then
    PRIMARY_DOMAIN=${DOMAINS[0]}
  fi
  
  # 确认输入信息
  confirm_setup
  
  # 如果需要保存配置，先保存
  if [ "$SAVE_CONFIG" = true ]; then
    save_config "$CONFIG_FILE"
  fi
  
  # 设置脚本执行权限
  setup_script_permissions
  
  # 设置目录和权限
  setup_directories
  
  # 检查Docker Compose配置
  check_docker_compose
  
  # 更新Nginx卷挂载
  update_nginx_volumes
  
  # 更新docker-compose.yml中的邮箱
  sed_i "s/your-email@example\.com/$EMAIL/g" docker-compose.yml
  
  # 处理本地域名情况
  if [ "$PRIMARY_DOMAIN" = "localhost" ] || [ "$PRIMARY_DOMAIN" = "127.0.0.1" ] || [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    info "检测到本地域名/IP: $PRIMARY_DOMAIN"
    warning "本地域名不支持自动SSL证书，将仅使用HTTP模式"
    ENABLE_HTTPS=false
    
    # 调整certbot配置，使用自签名证书
    info "修改certbot配置为测试模式..."
    sed_i 's/force-renewal/force-renewal --test-cert/g' docker-compose.yml
  fi
  
  # 添加系统资源检查
  check_system_resources
  
  # 初始化部署
  init_deploy
  
  # 构建和启动Docker服务
  start_services
  
  # 等待30秒让服务启动
  info "等待服务启动..."
  sleep 30
  
  # 对于真实域名，检查可访问性（在服务启动后进行）
  if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    check_domains_access
  fi
  
  # 检查服务状态
  info "检查服务状态..."
  NGINX_RUNNING=$(docker ps --filter "name=poetize-nginx" --format "{{.Status}}" | grep -c "Up")
  JAVA_RUNNING=$(docker ps --filter "name=poetize-java" --format "{{.Status}}" | grep -c "Up")
  PYTHON_RUNNING=$(docker ps --filter "name=poetize-python" --format "{{.Status}}" | grep -c "Up")
  MYSQL_RUNNING=$(docker ps --filter "name=poetize-mariadb" --format "{{.Status}}" | grep -c "Up")
  
  if [ "$NGINX_RUNNING" -eq 1 ] && [ "$JAVA_RUNNING" -eq 1 ] && [ "$PYTHON_RUNNING" -eq 1 ] && [ "$MYSQL_RUNNING" -eq 1 ]; then
    success "所有服务已成功启动！"
  else
    warning "部分服务可能未正常启动，请检查日志："
    echo "- Nginx状态: $([ "$NGINX_RUNNING" -eq 1 ] && echo '运行中' || echo '未运行')"
    echo "- Java后端状态: $([ "$JAVA_RUNNING" -eq 1 ] && echo '运行中' || echo '未运行')"
    echo "- Python后端状态: $([ "$PYTHON_RUNNING" -eq 1 ] && echo '运行中' || echo '未运行')"
    echo "- MariaDB状态: $([ "$MYSQL_RUNNING" -eq 1 ] && echo '运行中' || echo '未运行')"
  fi
  
  # 设置HTTPS（如果需要）
  if [ "$ENABLE_HTTPS" = true ]; then
    SSL_RESULT=$(setup_https)
    SSL_STATUS=$?
    
    if [ $SSL_STATUS -eq 2 ]; then
      warning "SSL证书申请失败，但将继续以HTTP模式运行"
      info "您可以在部署完成后手动配置HTTPS"
    elif [ $SSL_STATUS -ne 0 ]; then
      warning "HTTPS配置过程中出现错误"
    fi
  else
    # 本地域名环境不支持HTTPS，跳过询问
    if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      # 对于真实域名，应该使用完整的setup_https流程
      info "检测到真实域名，正在启用HTTPS..."
      SSL_RESULT=$(setup_https)
      SSL_STATUS=$?
      
      if [ $SSL_STATUS -eq 0 ]; then
        success "HTTPS已成功启用!"
        ENABLE_HTTPS=true
      elif [ $SSL_STATUS -eq 2 ]; then
        warning "SSL证书申请失败，但将继续以HTTP模式运行"
        info "您可以在部署完成后手动配置HTTPS"
      else
        warning "HTTPS启用失败。如果需要，请稍后手动运行: docker exec poetize-nginx /enable-https.sh"
      fi
    else
      info "本地域名环境不支持HTTPS，如需使用HTTPS请配置有效域名"
    fi
  fi

  # 等待5秒让HTTPS配置完全生效
  if [ "$ENABLE_HTTPS" = true ] || [ "${SSL_STATUS:-1}" -eq 0 ]; then
    info "等待HTTPS配置生效..."
    sleep 5
    
    # 验证HTTPS是否真正工作
    verify_https_status
  fi

    
  # 调用部署完成函数
  clean_docker_build_cache
  
  # 打印部署汇总信息
  print_summary
  
  echo ""
}

# 执行主函数
if [ "$RUN_IN_BACKGROUND" = true ]; then
  # 后台运行模式
  echo "Poetize 部署脚本将在后台运行，日志输出到: $LOG_FILE"
  echo "使用 'tail -f $LOG_FILE' 命令可以实时查看部署进度"
  echo "注意：后台运行模式下会自动回答'y'确认所有提示"
  # 过滤掉后台运行相关参数，避免无限递归
  FILTERED_ARGS=()
  for arg in "$@"; do
    if [ "$arg" != "-b" ] && [ "$arg" != "--background" ] && [ "$arg" != "$LOG_FILE" ] && [ "$prev_arg" != "--log-file" ]; then
      FILTERED_ARGS+=("$arg")
    fi
    prev_arg="$arg"
  done
  # 添加AUTO_YES环境变量，在后台运行时自动回答所有确认
  export AUTO_YES=true
  nohup bash "$0" "${FILTERED_ARGS[@]}" > "$LOG_FILE" 2>&1 &
  echo "后台进程ID: $!"
  exit 0
else
  # 正常运行模式
main "$@" 
fi 