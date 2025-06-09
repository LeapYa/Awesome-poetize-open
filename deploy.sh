#!/bin/bash

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

# 打印部署汇总信息
print_summary() {
  echo ""
  echo -e "${BLUE}=== 部署信息汇总 ===${NC}"
  echo "主域名: $PRIMARY_DOMAIN"
  echo "所有域名: ${DOMAINS[*]}"
  echo "管理员邮箱: $EMAIL"
  echo ""
  
  # 本地环境特殊处理
  if [ "$PRIMARY_DOMAIN" = "localhost" ] || [ "$PRIMARY_DOMAIN" = "127.0.0.1" ] || [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "本地开发环境服务地址:"
    echo "- 网站首页: http://$PRIMARY_DOMAIN"
    echo "- 聊天室: http://$PRIMARY_DOMAIN/im"
    echo ""
    echo "管理地址:"
    echo "- 管理员登录: http://$PRIMARY_DOMAIN/admin"
  else
    echo "服务地址:"
    echo "- 网站首页: http://$PRIMARY_DOMAIN 或 https://$PRIMARY_DOMAIN (SSL证书成功后)"
    echo "- 聊天室: http://$PRIMARY_DOMAIN/im 或 https://$PRIMARY_DOMAIN/im (SSL证书成功后)"
    echo ""
    echo "管理地址:"
    echo "- 管理员登录: http://$PRIMARY_DOMAIN/admin 或 https://$PRIMARY_DOMAIN/admin (SSL证书成功后)"
  fi
  
  # 显示数据库凭据信息
  if [ -f ".config/db_credentials.txt" ]; then
    echo ""
    echo "数据库凭据信息："
    
    # 从db_credentials.txt文件中提取ROOT密码和用户密码
    DB_ROOT_PASSWORD=$(grep "数据库ROOT密码:" .config/db_credentials.txt | cut -d':' -f2 | tr -d ' ')
    DB_USER_PASSWORD=$(grep "数据库poetize用户密码:" .config/db_credentials.txt | cut -d':' -f2 | tr -d ' ')
    
    echo "- 数据库ROOT密码: ${DB_ROOT_PASSWORD}"
    echo "- 数据库poetize用户密码: ${DB_USER_PASSWORD}"
    echo -e "${YELLOW}注意: 这些是随机生成的密码，请妥善保存。完整信息已保存在 .config/db_credentials.txt 文件中。${NC}"
  fi
  
  echo ""
  echo "常用命令:"
  echo "- 查看所有容器: docker ps -a"
  echo "- 查看容器日志: docker logs poetize-nginx"
  echo "- 重启容器: $DOCKER_COMPOSE_CMD restart"
  echo "- 停止服务: $DOCKER_COMPOSE_CMD down"
  echo "- 启动服务: $DOCKER_COMPOSE_CMD up -d"
  if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "- 手动启用HTTPS: docker exec poetize-nginx /enable-https.sh"
  fi
  echo ""
  echo -e "${YELLOW}注意: 初次登录时，默认管理员账号为'Sara'，密码为'aaa'。请登录后立即修改密码！${NC}"
  echo ""
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

# 安装curl工具
check_and_install_curl() {
  if ! command -v curl &>/dev/null; then
    info "检测到系统未安装curl，正在尝试安装..."
    
    # 检查是否存在wget
    if command -v wget &>/dev/null; then
      info "系统中存在wget，使用wget安装curl..."
      
      # 确定系统架构
      ARCH=$(uname -m)
      if [ "$ARCH" = "x86_64" ]; then
        # 对于x86_64架构，下载静态编译的curl
        info "下载curl静态二进制文件..."
        wget -q -O /tmp/curl "https://github.com/moparisthebest/static-curl/releases/latest/download/curl-amd64" || {
          error "使用wget下载curl失败"
        }
      else
        # 对于其他架构，尝试用包管理器安装
        info "系统架构不是x86_64，尝试使用包管理器安装..."
      fi
      
      # 如果wget下载成功，使用下载的二进制文件
      if [ -f "/tmp/curl" ] && [ -s "/tmp/curl" ]; then
        chmod +x /tmp/curl
        # 尝试移动到系统路径
        if command -v sudo &>/dev/null; then
          sudo mv /tmp/curl /usr/local/bin/curl || {
            # 如果无法移动到系统路径，放到当前目录
            mv /tmp/curl ./curl
            export PATH="$PWD:$PATH"
            info "curl已安装到当前目录，请将其移动到系统路径中"
          }
        else
          mv /tmp/curl ./curl
          export PATH="$PWD:$PATH"
          info "curl已安装到当前目录，请将其移动到系统路径中"
        fi
        
        # 检查安装结果
        if command -v curl &>/dev/null || [ -x "./curl" ]; then
          success "curl安装成功!"
          return 0
        fi
      fi
    fi
    
    # 如果wget方法失败，尝试修复软件源并使用包管理器
    if [ -f "/etc/apt/sources.list" ] && grep -q "buster-backports" /etc/apt/sources.list; then
      info "检测到buster-backports源可能有问题，尝试修复..."
      # 创建备份
      if command -v sudo &>/dev/null; then
        sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak
        # 注释掉有问题的backports源
        sudo_sed_i 's/^deb http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
        sudo_sed_i 's/^deb-src http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
      else
        cp /etc/apt/sources.list /etc/apt/sources.list.bak
        # 注释掉有问题的backports源
        sed_i 's/^deb http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
        sed_i 's/^deb-src http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
      fi
      info "已注释掉过期的backports源，重试安装..."
    fi
    
    # 检查不同的包管理器并安装curl
    if command -v apt-get &>/dev/null; then
      info "使用apt-get安装curl..."
      if command -v sudo &>/dev/null; then
        sudo apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
        sudo apt-get install -y curl || {
          warning "使用sudo安装curl失败，尝试不使用sudo..."
          apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
          apt-get install -y curl || {
            # 如果apt-get也失败，尝试使用编译安装方法
            warning "使用apt-get安装curl失败，尝试其他方法..."
            install_curl_from_source
          }
        }
      else
        apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
        apt-get install -y curl || {
          # 如果apt-get也失败，尝试使用编译安装方法
          warning "使用apt-get安装curl失败，尝试其他方法..."
          install_curl_from_source
        }
      fi
    # 其他包管理器的尝试保持不变...
    elif command -v apt &>/dev/null; then
      info "使用apt安装curl..."
      sudo apt update -qq && sudo apt install -y curl || {
        apt update -qq && apt install -y curl || {
          error "curl安装失败"
          exit 1
        }
      }
    elif command -v yum &>/dev/null; then
      info "使用yum安装curl..."
      sudo yum install -y curl || {
        yum install -y curl || {
          error "curl安装失败"
          exit 1
        }
      }
    elif command -v dnf &>/dev/null; then
      info "使用dnf安装curl..."
      sudo dnf install -y curl || {
        dnf install -y curl || {
          error "curl安装失败"
          exit 1
        }
      }
    elif command -v apk &>/dev/null; then
      info "使用apk安装curl (Alpine Linux)..."
      apk add --no-cache curl || {
        error "curl安装失败"
        exit 1
      }
    elif command -v pacman &>/dev/null; then
      info "使用pacman安装curl (Arch Linux)..."
      sudo pacman -S --noconfirm curl || {
        pacman -S --noconfirm curl || {
          error "curl安装失败"
          exit 1
        }
      }
    elif command -v zypper &>/dev/null; then
      info "使用zypper安装curl (openSUSE)..."
      sudo zypper install -y curl || {
        zypper install -y curl || {
          error "curl安装失败"
          exit 1
        }
      }
    else
      error "无法识别的包管理器，无法自动安装curl"
      install_curl_from_source
    fi
    
    # 检查安装结果
    if command -v curl &>/dev/null || [ -x "./curl" ]; then
      success "curl安装成功!"
    else
      error "curl安装失败"
      error "请手动安装curl后重试"
      exit 1
    fi
  fi
}

# 从源码安装curl的函数
install_curl_from_source() {
  info "尝试从源码编译安装curl..."
  
  # 检查必要的编译工具
  if ! command -v gcc &>/dev/null || ! command -v make &>/dev/null; then
    info "安装编译工具..."
    if command -v apt-get &>/dev/null; then
      if command -v sudo &>/dev/null; then
        sudo apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
        sudo apt-get install -y build-essential || warning "安装build-essential失败，尝试继续..."
      else
        apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
        apt-get install -y build-essential || warning "安装build-essential失败，尝试继续..."
      fi
    elif command -v yum &>/dev/null; then
      if command -v sudo &>/dev/null; then
        sudo yum groupinstall -y "Development Tools" || warning "安装开发工具失败，尝试继续..."
      else
        yum groupinstall -y "Development Tools" || warning "安装开发工具失败，尝试继续..."
      fi
    fi
  fi
  
  # 如果wget不可用，尝试静态链接获取curl
  if command -v gcc &>/dev/null && command -v make &>/dev/null; then
    # 创建临时目录
    mkdir -p /tmp/curl_build && cd /tmp/curl_build || {
      error "无法创建临时构建目录"
      return 1
    }
    
    # 尝试使用简单的C代码创建一个最小HTTP下载工具
    cat > minicurl.c << 'EOF'
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>

int main(int argc, char *argv[]) {
    if (argc != 3) {
        fprintf(stderr, "用法: %s <主机名> <路径>\n", argv[0]);
        return 1;
    }

    const char *hostname = argv[1];
    const char *path = argv[2];
    
    struct hostent *server = gethostbyname(hostname);
    if (server == NULL) {
        fprintf(stderr, "无法解析主机: %s\n", hostname);
        return 1;
    }
    
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        fprintf(stderr, "无法创建套接字\n");
        return 1;
    }
    
    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(80);
    memcpy(&serv_addr.sin_addr.s_addr, server->h_addr, server->h_length);
    
    if (connect(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        fprintf(stderr, "连接失败\n");
        return 1;
    }
    
    char request[1024];
    sprintf(request, "GET %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: minicurl/1.0\r\nConnection: close\r\n\r\n", path, hostname);
    
    if (write(sockfd, request, strlen(request)) < 0) {
        fprintf(stderr, "写入请求失败\n");
        return 1;
    }
    
    char buffer[1024];
    int in_headers = 1;
    int n;
    while ((n = read(sockfd, buffer, sizeof(buffer) - 1)) > 0) {
        buffer[n] = '\0';
        
        if (in_headers) {
            char *body_start = strstr(buffer, "\r\n\r\n");
            if (body_start) {
                in_headers = 0;
                body_start += 4;
                printf("%s", body_start);
            }
        } else {
            printf("%s", buffer);
        }
    }
    
    close(sockfd);
    return 0;
}
EOF
    
    # 编译minicurl
    if gcc -o minicurl minicurl.c; then
      info "编译简易HTTP工具成功"
      
      # 使用minicurl获取curl的最新版本
      if ./minicurl curl.se /download/curl-7.88.1.tar.gz > curl-7.88.1.tar.gz; then
        info "下载curl源码成功"
        tar -xzf curl-7.88.1.tar.gz
        cd curl-7.88.1
        
        # 配置和编译
        ./configure --prefix=/usr/local --disable-shared --enable-static
        make -j$(nproc)
        
        # 安装
        if command -v sudo &>/dev/null; then
          sudo make install
        else
          make install
        fi
        
        # 清理
        cd .. && rm -rf curl-7.88.1 curl-7.88.1.tar.gz minicurl minicurl.c
        cd -
        
        # 检查安装
        if command -v curl &>/dev/null; then
          return 0
        fi
      fi
    fi
  fi
  
  # 如果所有方法都失败，返回失败
  error "无法安装curl"
  return 1
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
    
    # 检查并安装curl
    check_and_install_curl
    
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
    
    # 检查是否为龙蜥OS(Anolis)
    if [ -f "/etc/os-release" ] && grep -q "anolis" /etc/os-release; then
        info "检测到龙蜥操作系统(Anolis OS)，使用专用安装方法..."
        install_docker_anolis || {
            # 即使龙蜥OS安装失败，也再次检查Docker是否可用
            if command -v docker &>/dev/null; then
                info "检测到Docker命令可用，继续执行..."
                success "Docker安装成功"
                return 0
            fi
        }
    else
    # 使用官方安装脚本
    info "使用官方安装脚本..."
    
    # 先尝试使用官方脚本
    if curl -fsSL https://get.docker.com -o get-docker.sh; then
        # 执行安装脚本
            if ! sh get-docker.sh; then
            warning "Docker官方脚本安装失败，尝试使用包管理器安装..."
            install_docker_from_package_manager
            fi
    else
        warning "下载Docker安装脚本失败，尝试使用包管理器安装..."
        install_docker_from_package_manager
        fi
    fi
    
    # 检查是否需要设置podman别名
    setup_podman_alias
    
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

# 在龙蜥OS上安装Docker
install_docker_anolis() {
  info "为龙蜥OS(Anolis)安装Docker..."
  
  # 检查是否有dnf命令
  if command -v dnf &>/dev/null; then
    info "使用dnf作为龙蜥OS的包管理器"
    
    # 安装依赖包
    sudo dnf install -y dnf-plugins-core device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
    
    # 第一种方法：尝试直接安装系统内置的Docker
    info "尝试从系统仓库安装Docker..."
    if sudo dnf install -y docker; then
      # 启动docker服务
      sudo systemctl start docker || warning "启动Docker服务失败，可能没有docker服务单元文件"
      sudo systemctl enable docker 2>/dev/null || true
      
      # 验证docker是否可用
      if docker info &>/dev/null; then
        info "从系统仓库成功安装Docker并验证可用"
        return 0
      else
        warning "Docker已安装但无法启动，尝试其他安装方法..."
      fi
    fi
    
    # 第二种方法：使用阿里云Docker CE仓库
    info "系统仓库安装Docker失败，尝试使用阿里云Docker CE仓库..."
    
    # 添加Docker CE仓库
    sudo dnf config-manager --add-repo=https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo || warning "添加阿里云Docker仓库失败"
    
    # 修改仓库配置以适配龙蜥OS
    sudo_sed_i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库配置失败"
    
    # 安装Docker CE
    if sudo dnf install -y --allowerasing docker-ce docker-ce-cli containerd.io; then
      sudo systemctl start docker
      sudo systemctl enable docker
      
      # 验证docker是否可用
      if docker info &>/dev/null; then
        info "使用阿里云仓库成功安装Docker CE并验证可用"
        return 0
      else
        warning "Docker CE已安装但无法启动，尝试其他安装方法..."
      fi
    fi
    
    # 第三种方法：使用podman作为替代品
    warning "Docker CE安装失败或无法启动，尝试使用podman作为替代方案..."
    if sudo dnf install -y podman podman-docker; then
      info "Podman安装成功，配置podman作为docker的替代品"
      
      # 创建docker别名 - 系统级
      echo 'alias docker=podman' | sudo tee /etc/profile.d/podman-docker.sh
      sudo chmod +x /etc/profile.d/podman-docker.sh
      
      # 当前用户 .bashrc
      if [ -f "$HOME/.bashrc" ]; then
        grep -q "alias docker=podman" "$HOME/.bashrc" || echo 'alias docker=podman' >> "$HOME/.bashrc"
      fi
      
      # 当前会话中立即设置别名
      alias docker=podman
      export PATH="/usr/bin:$PATH"  # 确保路径中包含podman
      
      info "已将podman配置为docker的替代品"
      
      # 验证podman是否正常工作
      if podman --version &>/dev/null; then
        info "Podman安装验证成功，版本: $(podman --version)"
        info "使用以下命令在当前会话中使docker别名生效: source /etc/profile.d/podman-docker.sh"
        
        # 立即检查别名是否工作
        if docker --version &>/dev/null; then
          success "Docker别名已成功设置，当前会话中可用"
        else
          warning "Docker别名未能在当前会话中生效，请手动运行: alias docker=podman"
        fi
        
        return 0
      else
        warning "Podman安装后无法运行，继续尝试其他方法..."
      fi
    fi
    
    error "在龙蜥OS上安装Docker或podman失败"
    return 1
  elif command -v yum &>/dev/null; then
    info "使用yum作为龙蜥OS的包管理器"
    
    # 安装依赖
    sudo yum install -y yum-utils device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
    
    # 尝试直接安装系统内置的Docker
    info "尝试从系统仓库安装Docker..."
    if sudo yum install -y docker; then
      # 启动docker服务
      sudo systemctl start docker || warning "启动Docker服务失败，可能没有docker服务单元文件"
      sudo systemctl enable docker 2>/dev/null || true
      
      # 验证docker是否可用
      if docker info &>/dev/null; then
        info "从系统仓库成功安装Docker并验证可用"
        return 0
      else
        warning "Docker已安装但无法启动，尝试其他安装方法..."
      fi
    fi
    
    # 使用阿里云Docker CE仓库
    info "系统仓库安装Docker失败，尝试使用阿里云Docker CE仓库..."
    
    # 添加Docker CE仓库
    sudo yum-config-manager --add-repo=https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo || warning "添加阿里云Docker仓库失败"
    
    # 修改仓库配置以适配龙蜥OS
    sudo_sed_i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库配置失败"
    
    # 安装Docker CE
    if sudo yum install -y --allowerasing docker-ce docker-ce-cli containerd.io; then
      sudo systemctl start docker
      sudo systemctl enable docker
      
      # 验证docker是否可用
      if docker info &>/dev/null; then
        info "使用阿里云仓库成功安装Docker CE并验证可用"
        return 0
      else
        warning "Docker CE已安装但无法启动，尝试其他安装方法..."
      fi
    fi
    
    # 尝试使用podman作为替代品
    warning "Docker CE安装失败或无法启动，尝试使用podman作为替代方案..."
    if sudo yum install -y podman podman-docker; then
      info "Podman安装成功，配置podman作为docker的替代品"
      
      # 创建docker别名 - 系统级
      echo 'alias docker=podman' | sudo tee /etc/profile.d/podman-docker.sh
      sudo chmod +x /etc/profile.d/podman-docker.sh
      
      # 当前用户 .bashrc
      if [ -f "$HOME/.bashrc" ]; then
        grep -q "alias docker=podman" "$HOME/.bashrc" || echo 'alias docker=podman' >> "$HOME/.bashrc"
      fi
      
      # 当前会话中立即设置别名
      alias docker=podman
      export PATH="/usr/bin:$PATH"  # 确保路径中包含podman
      
      info "已将podman配置为docker的替代品"
      
      # 验证podman是否正常工作
      if podman --version &>/dev/null; then
        info "Podman安装验证成功，版本: $(podman --version)"
        info "使用以下命令在当前会话中使docker别名生效: source /etc/profile.d/podman-docker.sh"
        
        # 立即检查别名是否工作
        if docker --version &>/dev/null; then
          success "Docker别名已成功设置，当前会话中可用"
        else
          warning "Docker别名未能在当前会话中生效，请手动运行: alias docker=podman"
        fi
        
        return 0
      else
        warning "Podman安装后无法运行，继续尝试其他方法..."
      fi
    fi
    
    error "在龙蜥OS上安装Docker或podman失败"
    return 1
  else
    error "龙蜥OS缺少dnf和yum包管理器，无法安装Docker"
    return 1
  fi
}

# 检查是否需要设置podman作为docker别名
setup_podman_alias() {
  if ! command -v docker &>/dev/null && command -v podman &>/dev/null; then
    info "系统中没有Docker但找到了Podman，设置Docker别名..."
    
    # 创建docker别名 - 系统级
    echo 'alias docker=podman' | sudo tee /etc/profile.d/podman-docker.sh
    sudo chmod +x /etc/profile.d/podman-docker.sh
    
    # 当前用户 .bashrc
    if [ -f "$HOME/.bashrc" ]; then
      grep -q "alias docker=podman" "$HOME/.bashrc" || echo 'alias docker=podman' >> "$HOME/.bashrc"
    fi
    
    # 当前会话中立即设置别名
    alias docker=podman
    
    # 验证是否生效
    if docker --version &>/dev/null; then
      success "已将podman配置为docker的替代品，当前会话中可用"
    else
      warning "Docker别名未能立即生效，请手动运行: alias docker=podman"
    fi
  fi
}

# 使用包管理器安装Docker
install_docker_from_package_manager() {
    info "使用包管理器安装Docker..."
    
    # 检查系统类型并安装Docker
    if command -v apt-get &>/dev/null; then
        # Debian/Ubuntu系统
        info "检测到Debian/Ubuntu系统，使用apt安装Docker..."
        
        # 安装依赖包
        if command -v sudo &>/dev/null; then
            sudo apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
            sudo apt-get install -y apt-transport-https ca-certificates gnupg lsb-release || warning "安装依赖包失败，继续尝试安装Docker..."
            
            # 添加Docker GPG密钥
            if [ -x "$(command -v curl)" ]; then
                curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg || warning "添加Docker GPG密钥失败，继续尝试安装..."
            elif [ -x "$(command -v wget)" ]; then
                wget -q -O - https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg || warning "添加Docker GPG密钥失败，继续尝试安装..."
            fi
            
            # 添加Docker仓库
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null || warning "添加Docker仓库失败，继续尝试安装..."
            
            # 更新包索引
            sudo apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
            
            # 安装Docker
            sudo apt-get install -y docker-ce docker-ce-cli containerd.io || {
                warning "Docker官方仓库安装失败，尝试使用系统仓库安装..."
                sudo apt-get install -y docker.io || {
                    error "Docker安装失败"
                    return 1
                }
            }
        else
            apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
            apt-get install -y apt-transport-https ca-certificates gnupg lsb-release || warning "安装依赖包失败，继续尝试安装Docker..."
            
            # 添加Docker GPG密钥
            if [ -x "$(command -v curl)" ]; then
                curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg || warning "添加Docker GPG密钥失败，继续尝试安装..."
            elif [ -x "$(command -v wget)" ]; then
                wget -q -O - https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg || warning "添加Docker GPG密钥失败，继续尝试安装..."
            fi
            
            # 添加Docker仓库
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null || warning "添加Docker仓库失败，继续尝试安装..."
            
            # 更新包索引
            apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
            
            # 安装Docker
            apt-get install -y docker-ce docker-ce-cli containerd.io || {
                warning "Docker官方仓库安装失败，尝试使用系统仓库安装..."
                apt-get install -y docker.io || {
                    error "Docker安装失败"
                    return 1
                }
            }
        fi
    elif command -v yum &>/dev/null; then
        # RHEL/CentOS/AlmaLinux/Alibaba Cloud Linux系统
        info "检测到RHEL/CentOS系统，使用yum安装Docker..."
        
        # 检查是否为CentOS 6，此版本已不被Docker CE支持
        if grep -q "CentOS release 6" /etc/redhat-release || grep -q "CentOS Linux release 6" /etc/redhat-release; then
            info "检测到CentOS 6系统，Docker CE已不支持此版本，尝试安装兼容版本..."
            
            if command -v sudo &>/dev/null; then
                # 禁用Docker CE仓库以防止干扰
                if [ -f "/etc/yum.repos.d/docker-ce.repo" ]; then
                    info "禁用不兼容的Docker CE仓库..."
                    sudo_sed_i 's/enabled=1/enabled=0/g' /etc/yum.repos.d/docker-ce.repo || warning "禁用Docker CE仓库失败"
                fi
                
                # 安装EPEL仓库 - 使用CentOS 6专用URL
                info "安装EPEL仓库..."
                sudo rpm -Uvh https://archives.fedoraproject.org/pub/archive/epel/6/x86_64/epel-release-6-8.noarch.rpm || warning "安装EPEL仓库失败，尝试继续安装Docker"
                
                # 尝试使用RPM直接安装Docker
                info "尝试直接安装Docker..."
                
                # 方法1: 使用CentOS EPEL仓库
                sudo yum install -y docker-io && {
                    info "成功从EPEL仓库安装docker-io"
                    sudo service docker start || warning "启动Docker服务失败"
                    sudo chkconfig docker on || warning "设置Docker服务开机启动失败"
                    success "成功安装CentOS 6兼容的Docker版本!"
                    return 0
                }
                
                # 方法2: 使用系统仓库的docker包
                warning "docker-io安装失败，尝试安装系统docker包..."
                sudo yum install -y docker && {
                    info "成功从系统仓库安装docker"
                    sudo service docker start || warning "启动Docker服务失败"
                    sudo chkconfig docker on || warning "设置Docker服务开机启动失败"
                    success "成功安装CentOS 6兼容的Docker版本!"
                    return 0
                }
                
                # 方法3: 直接下载Docker RPM包安装
                warning "系统仓库docker安装失败，尝试直接下载RPM包安装..."
                tmp_dir=$(mktemp -d)
                cd $tmp_dir
                
                info "下载Docker RPM包..."
                if ! curl -L -o docker-io.rpm http://mirror.centos.org/centos/6/extras/x86_64/Packages/docker-io-1.7.1-2.el6.x86_64.rpm; then
                    warning "下载Docker RPM包失败，尝试备用链接..."
                    if ! curl -L -o docker-io.rpm https://vault.centos.org/6.10/extras/x86_64/Packages/docker-io-1.7.1-2.el6.x86_64.rpm; then
                        error "无法下载Docker RPM包"
                        cd - > /dev/null
                        rm -rf $tmp_dir
                        return 1
                    fi
                fi
                
                info "安装Docker RPM包..."
                sudo rpm -ivh docker-io.rpm && {
                    info "成功从RPM包安装docker-io"
                    cd - > /dev/null
                    rm -rf $tmp_dir
                    sudo service docker start || warning "启动Docker服务失败"
                    sudo chkconfig docker on || warning "设置Docker服务开机启动失败"
                    success "成功安装CentOS 6兼容的Docker版本!"
                    return 0
                }
                
                cd - > /dev/null
                rm -rf $tmp_dir
                error "所有Docker安装方法都失败，CentOS 6版本可能无法安装Docker"
                warning "建议升级至CentOS 7或更高版本"
                return 1
            else
                # 禁用Docker CE仓库以防止干扰
                if [ -f "/etc/yum.repos.d/docker-ce.repo" ]; then
                    info "禁用不兼容的Docker CE仓库..."
                    sed_i 's/enabled=1/enabled=0/g' /etc/yum.repos.d/docker-ce.repo || warning "禁用Docker CE仓库失败"
                fi
                
                # 安装EPEL仓库 - 使用CentOS 6专用URL
                info "安装EPEL仓库..."
                rpm -Uvh https://archives.fedoraproject.org/pub/archive/epel/6/x86_64/epel-release-6-8.noarch.rpm || warning "安装EPEL仓库失败，尝试继续安装Docker"
                
                # 尝试使用RPM直接安装Docker
                info "尝试直接安装Docker..."
                
                # 方法1: 使用CentOS EPEL仓库
                yum install -y docker-io && {
                    info "成功从EPEL仓库安装docker-io"
                    service docker start || warning "启动Docker服务失败"
                    chkconfig docker on || warning "设置Docker服务开机启动失败"
                    success "成功安装CentOS 6兼容的Docker版本!"
                    return 0
                }
                
                # 方法2: 使用系统仓库的docker包
                warning "docker-io安装失败，尝试安装系统docker包..."
                yum install -y docker && {
                    info "成功从系统仓库安装docker"
                    service docker start || warning "启动Docker服务失败"
                    chkconfig docker on || warning "设置Docker服务开机启动失败"
                    success "成功安装CentOS 6兼容的Docker版本!"
                    return 0
                }
                
                # 方法3: 直接下载Docker RPM包安装
                warning "系统仓库docker安装失败，尝试直接下载RPM包安装..."
                tmp_dir=$(mktemp -d)
                cd $tmp_dir
                
                info "下载Docker RPM包..."
                if ! curl -L -o docker-io.rpm http://mirror.centos.org/centos/6/extras/x86_64/Packages/docker-io-1.7.1-2.el6.x86_64.rpm; then
                    warning "下载Docker RPM包失败，尝试备用链接..."
                    if ! curl -L -o docker-io.rpm https://vault.centos.org/6.10/extras/x86_64/Packages/docker-io-1.7.1-2.el6.x86_64.rpm; then
                        error "无法下载Docker RPM包"
                        cd - > /dev/null
                        rm -rf $tmp_dir
                        return 1
                    fi
                fi
                
                info "安装Docker RPM包..."
                rpm -ivh docker-io.rpm && {
                    info "成功从RPM包安装docker-io"
                    cd - > /dev/null
                    rm -rf $tmp_dir
                    service docker start || warning "启动Docker服务失败"
                    chkconfig docker on || warning "设置Docker服务开机启动失败"
                    success "成功安装CentOS 6兼容的Docker版本!"
                    return 0
                }
                
                cd - > /dev/null
                rm -rf $tmp_dir
                error "所有Docker安装方法都失败，CentOS 6版本可能无法安装Docker"
                warning "建议升级至CentOS 7或更高版本"
                return 1
            fi
        fi
        
        # 检查是否为Alibaba Cloud Linux
        if grep -q "Alibaba Cloud Linux" /etc/os-release || grep -q "Aliyun Linux" /etc/os-release || grep -q "alinux" /etc/os-release; then
            info "检测到Alibaba Cloud Linux系统，使用阿里云源安装Docker..."
            
            if command -v sudo &>/dev/null; then
                # 安装依赖包
                sudo yum install -y yum-utils device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
                
                # 配置阿里云Docker镜像源
                info "配置阿里云Docker镜像源..."
                # 如果仓库文件不存在则创建
                sudo tee /etc/yum.repos.d/docker-ce.repo > /dev/null << 'EOF'
[docker-ce-stable]
name=Docker CE Stable - $basearch
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/7/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
                
                # 清理缓存
                sudo yum clean all
                sudo yum makecache fast || warning "更新仓库缓存失败，继续尝试安装..."
                
                # 安装Docker
                sudo yum install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                    warning "Docker CE安装失败，尝试安装系统内置的docker..."
                    sudo yum install -y docker && {
                        sudo systemctl start docker
                        sudo systemctl enable docker
                        success "成功从系统仓库安装Docker!"
                        return 0
                    } || {
                        error "Docker安装失败"
                        return 1
                    }
                }
                
                # 启动Docker服务
                sudo systemctl start docker
                sudo systemctl enable docker
                success "成功从阿里云镜像源安装Docker!"
                return 0
            else
                # 安装依赖包
                yum install -y yum-utils device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
                
                # 配置阿里云Docker镜像源
                info "配置阿里云Docker镜像源..."
                # 如果仓库文件不存在则创建
                tee /etc/yum.repos.d/docker-ce.repo > /dev/null << 'EOF'
[docker-ce-stable]
name=Docker CE Stable - $basearch
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/7/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
                
                # 清理缓存
                yum clean all
                yum makecache fast || warning "更新仓库缓存失败，继续尝试安装..."
                
                # 安装Docker
                yum install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                    warning "Docker CE安装失败，尝试使用系统仓库安装..."
                    sudo yum install -y docker && {
                        sudo systemctl start docker
                        sudo systemctl enable docker
                        success "成功从系统仓库安装Docker!"
                        return 0
                    } || {
                        error "Docker安装失败"
                        return 1
                    }
                }
                
                # 启动Docker服务
                systemctl start docker
                systemctl enable docker
                success "成功从阿里云镜像源安装Docker!"
                return 0
            fi
        fi
        
        # 检查是否为AlmaLinux
        if [ -f "/etc/almalinux-release" ] || grep -q "AlmaLinux" /etc/os-release; then
            info "检测到AlmaLinux系统，使用适配的安装方式..."
            
            # 检查是否有dnf命令（AlmaLinux 8+优先使用dnf）
            if command -v dnf &>/dev/null; then
                info "使用dnf作为AlmaLinux的包管理器"
                
                # 添加备选方案函数
                setup_container_engine() {
                    info "配置容器引擎..."
                    
                    # 先尝试安装podman
                    info "尝试安装Podman作为容器引擎..."
                    if command -v sudo &>/dev/null; then
                        sudo dnf install -y podman podman-docker container-selinux || warning "Podman安装失败，尝试其他方法..."
                    else
                        dnf install -y podman podman-docker container-selinux || warning "Podman安装失败，尝试其他方法..."
                    fi
                    
                    # 检查podman是否安装成功
                    if command -v podman &>/dev/null; then
                        info "Podman安装成功，配置Docker兼容层..."
                        # 创建docker命令的别名
                        if command -v sudo &>/dev/null; then
                            echo 'alias docker=podman' | sudo tee -a /etc/profile.d/podman-docker.sh
                            sudo chmod +x /etc/profile.d/podman-docker.sh
                        else
                            echo 'alias docker=podman' | tee -a /etc/profile.d/podman-docker.sh
                            chmod +x /etc/profile.d/podman-docker.sh
                        fi
                        
                        # 应用别名
                        source /etc/profile.d/podman-docker.sh
                        export PATH="/usr/bin:$PATH"  # 确保路径中包含podman
                        
                        info "podman已配置为docker的替代品"
                        return 0
                    fi
                    
                    # 如果podman安装失败，尝试系统自带的docker
                    info "尝试从系统仓库安装Docker..."
                    if command -v sudo &>/dev/null; then
                        sudo dnf install -y docker || warning "系统Docker安装失败..."
                    else
                        dnf install -y docker || warning "系统Docker安装失败..."
                    fi
                    
                    # 检查docker是否安装成功
                    if command -v docker &>/dev/null; then
                        info "系统Docker安装成功"
                        if command -v sudo &>/dev/null; then
                            sudo systemctl start docker || warning "启动Docker服务失败"
                            sudo systemctl enable docker || warning "设置Docker服务开机启动失败"
                        else
                            systemctl start docker || warning "启动Docker服务失败"
                            systemctl enable docker || warning "设置Docker服务开机启动失败"
                        fi
                        return 0
                    fi
                    
                    # 如果以上全部失败，返回错误
                    error "无法安装任何容器引擎"
                    return 1
                }
                
                if command -v sudo &>/dev/null; then
                    # 安装依赖包
                    sudo dnf install -y dnf-plugins-core device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
                    
                    # 直接尝试安装系统自带的Docker
                    info "尝试直接从AlmaLinux AppStream安装Docker..."
                    sudo dnf install -y docker && {
                        sudo systemctl start docker
                        sudo systemctl enable docker
                        success "成功从系统仓库安装Docker!"
                        return 0
                    }
                    
                    warning "从系统仓库安装Docker失败，尝试配置Docker CE仓库"
                    
                    # 添加Docker CE仓库
                    sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo || warning "添加Docker仓库失败"
                    
                    # 修改仓库配置以适配AlmaLinux
                    sudo_sed_i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库配置失败"
                    
                    # 尝试处理SSL错误
                    if [ -f "/etc/yum.repos.d/docker-ce.repo" ]; then
                        info "尝试修改仓库URL以处理SSL问题..."
                        # 尝试将https改为http以避免SSL问题
                        sudo_sed_i 's|https://download.docker.com|http://download.docker.com|g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库URL失败"
                        
                        # 尝试添加国内镜像源
                        info "尝试添加国内Docker镜像源..."
                        # 备份原始文件
                        sudo cp /etc/yum.repos.d/docker-ce.repo /etc/yum.repos.d/docker-ce.repo.bak || warning "备份仓库文件失败"
                        
                        # 修改为阿里云镜像
                        sudo tee /etc/yum.repos.d/docker-ce.repo > /dev/null << 'EOF'
[docker-ce-stable]
name=Docker CE Stable - $basearch
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg

[docker-ce-stable-debuginfo]
name=Docker CE Stable - Debuginfo $basearch
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/$basearch/debug-stable
enabled=0
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg

[docker-ce-stable-source]
name=Docker CE Stable - Sources
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/source/stable
enabled=0
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
                    fi
                    
                    # 清理和更新缓存
                    sudo dnf clean all
                    sudo dnf makecache
                    
                    # 安装Docker
                    sudo dnf install -y --allowerasing docker-ce docker-ce-cli containerd.io && {
                        sudo systemctl start docker
                        sudo systemctl enable docker
                        success "成功安装Docker CE!"
                        return 0
                    } || {
                        warning "Docker CE安装失败，尝试使用替代方案"
                        setup_container_engine
                    }
                else
                    # 安装依赖包
                    dnf install -y dnf-plugins-core device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
                    
                    # 直接尝试安装系统自带的Docker
                    info "尝试直接从AlmaLinux AppStream安装Docker..."
                    dnf install -y docker && {
                        systemctl start docker
                        systemctl enable docker
                        success "成功从系统仓库安装Docker!"
                        return 0
                    }
                    
                    warning "从系统仓库安装Docker失败，尝试配置Docker CE仓库"
                    
                    # 添加Docker CE仓库
                    dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo || warning "添加Docker仓库失败"
                    
                    # 修改仓库配置以适配AlmaLinux
                    sed_i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库配置失败"
                    
                    # 尝试处理SSL错误
                    if [ -f "/etc/yum.repos.d/docker-ce.repo" ]; then
                        info "尝试修改仓库URL以处理SSL问题..."
                        # 尝试将https改为http以避免SSL问题
                        sed_i 's|https://download.docker.com|http://download.docker.com|g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库URL失败"
                        
                        # 尝试添加国内镜像源
                        info "尝试添加国内Docker镜像源..."
                        # 备份原始文件
                        cp /etc/yum.repos.d/docker-ce.repo /etc/yum.repos.d/docker-ce.repo.bak || warning "备份仓库文件失败"
                        
                        # 修改为阿里云镜像
                        tee /etc/yum.repos.d/docker-ce.repo > /dev/null << 'EOF'
[docker-ce-stable]
name=Docker CE Stable - $basearch
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg

[docker-ce-stable-debuginfo]
name=Docker CE Stable - Debuginfo $basearch
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/$basearch/debug-stable
enabled=0
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg

[docker-ce-stable-source]
name=Docker CE Stable - Sources
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/source/stable
enabled=0
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
                    fi
                    
                    # 清理和更新缓存
                    dnf clean all
                    dnf makecache
                    
                    # 安装Docker
                    dnf install -y --allowerasing docker-ce docker-ce-cli containerd.io && {
                        systemctl start docker
                        systemctl enable docker
                        success "成功安装Docker CE!"
                        return 0
                    } || {
                        warning "Docker CE安装失败，尝试使用替代方案"
                        setup_container_engine
                    }
                fi
            # 回退到yum命令（如果dnf不可用）
            elif command -v yum &>/dev/null; then
                info "回退使用yum作为AlmaLinux的包管理器"
                
                if command -v sudo &>/dev/null; then
                    # 安装依赖包
                    sudo yum install -y yum-utils device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
                    
                    # 直接安装Docker CE
                    info "尝试直接从AlmaLinux AppStream安装Docker..."
                    sudo yum install -y docker || {
                        warning "从系统仓库安装Docker失败，尝试配置Docker CE仓库"
                        
                        # 使用CentOS 8的仓库
                        sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo || warning "添加Docker仓库失败"
                        
                        # 修改仓库配置以适配AlmaLinux
                        sed_i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库配置失败"
                        
                        # 尝试处理SSL错误
                        if [ -f "/etc/yum.repos.d/docker-ce.repo" ]; then
                            info "尝试修改仓库URL以处理SSL问题..."
                            # 尝试将https改为http以避免SSL问题
                            sed_i 's|https://download.docker.com|http://download.docker.com|g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库URL失败"
                        fi
                        
                        # 安装Docker
                        sudo yum install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                            warning "Docker CE安装失败，尝试使用podman作为替代方案"
                            
                            # 安装podman
                            info "安装podman容器引擎..."
                            sudo dnf install -y podman podman-docker || {
                                # 尝试安装系统内置的docker包
                                warning "Podman安装失败，尝试安装系统内置的docker包"
                                sudo dnf install -y docker || {
                                    # 尝试使用系统自带的容器工具
                                    warning "系统docker包安装失败，检查是否已有容器工具"
                                    if command -v podman &>/dev/null; then
                                        info "系统已有podman，配置docker兼容性..."
                                        # 创建docker命令的别名
                                        echo 'alias docker=podman' | sudo tee -a /etc/profile.d/podman-docker.sh
                                        sudo chmod +x /etc/profile.d/podman-docker.sh
                                        source /etc/profile.d/podman-docker.sh
                                        info "已将podman配置为docker的替代品"
                                    else
                                        error "所有Docker安装方式都失败，无法继续部署"
                                        return 1
                                    fi
                                }
                            }
                        }
                    }
                    
                    # 启动Docker服务
                    sudo systemctl start docker || warning "启动Docker服务失败"
                    sudo systemctl enable docker || warning "设置Docker服务开机启动失败"
                else
                    # 安装依赖包
                    yum install -y yum-utils device-mapper-persistent-data lvm2 || warning "安装依赖包失败，继续尝试安装Docker..."
                    
                    # 直接安装Docker CE
                    info "尝试直接从AlmaLinux AppStream安装Docker..."
                    yum install -y docker || {
                        warning "从系统仓库安装Docker失败，尝试配置Docker CE仓库"
                        
                        # 使用CentOS 8的仓库
                        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo || warning "添加Docker仓库失败"
                        
                        # 修改仓库配置以适配AlmaLinux
                        sed_i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库配置失败"
                        
                        # 尝试处理SSL错误
                        if [ -f "/etc/yum.repos.d/docker-ce.repo" ]; then
                            info "尝试修改仓库URL以处理SSL问题..."
                            # 尝试将https改为http以避免SSL问题
                            sed_i 's|https://download.docker.com|http://download.docker.com|g' /etc/yum.repos.d/docker-ce.repo || warning "修改仓库URL失败"
                        fi
                        
                        # 安装Docker
                        yum install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                            warning "Docker CE安装失败，尝试使用podman作为替代方案"
                            
                            # 安装podman
                            info "安装podman容器引擎..."
                            sudo dnf install -y podman podman-docker || {
                                # 尝试安装系统内置的docker包
                                warning "Podman安装失败，尝试安装系统内置的docker包"
                                sudo dnf install -y docker || {
                                    # 尝试使用系统自带的容器工具
                                    warning "系统docker包安装失败，检查是否已有容器工具"
                                    if command -v podman &>/dev/null; then
                                        info "系统已有podman，配置docker兼容性..."
                                        # 创建docker命令的别名
                                        echo 'alias docker=podman' | sudo tee -a /etc/profile.d/podman-docker.sh
                                        sudo chmod +x /etc/profile.d/podman-docker.sh
                                        source /etc/profile.d/podman-docker.sh
                                        info "已将podman配置为docker的替代品"
                                    else
                                        error "所有Docker安装方式都失败，无法继续部署"
                                        return 1
                                    fi
                                }
                            }
                        }
                    }
                    
                    # 启动Docker服务
                    systemctl start docker || warning "启动Docker服务失败"
                    systemctl enable docker || warning "设置Docker服务开机启动失败"
                fi
            else
                error "AlmaLinux系统上找不到dnf或yum包管理器"
                return 1
            fi
        else
            # 标准RHEL/CentOS流程
            # 安装依赖包
            if command -v sudo &>/dev/null; then
                sudo yum install -y yum-utils || warning "安装yum-utils失败，继续尝试安装Docker..."
                
                # 添加Docker仓库
                sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo || warning "添加Docker仓库失败，继续尝试安装..."
                
                # 安装Docker
                sudo yum install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                    error "Docker安装失败"
                    return 1
                }
                
                # 启动Docker服务
                sudo systemctl start docker || warning "启动Docker服务失败"
                sudo systemctl enable docker || warning "设置Docker服务开机启动失败"
            else
                yum install -y yum-utils || warning "安装yum-utils失败，继续尝试安装Docker..."
                
                # 添加Docker仓库
                yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo || warning "添加Docker仓库失败，继续尝试安装..."
                
                # 安装Docker
                yum install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                    error "Docker安装失败"
                    return 1
                }
                
                # 启动Docker服务
                systemctl start docker || warning "启动Docker服务失败"
                systemctl enable docker || warning "设置Docker服务开机启动失败"
            fi
        fi
    elif command -v dnf &>/dev/null; then
        # Fedora系统
        info "检测到Fedora系统，使用dnf安装Docker..."
        
        # 安装依赖包
        if command -v sudo &>/dev/null; then
            sudo dnf -y install dnf-plugins-core || warning "安装dnf-plugins-core失败，继续尝试安装Docker..."
            
            # 添加Docker仓库
            sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo || warning "添加Docker仓库失败，继续尝试安装..."
            
            # 安装Docker
            sudo dnf install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                error "Docker安装失败"
                return 1
            }
            
            # 启动Docker服务
            sudo systemctl start docker || warning "启动Docker服务失败"
            sudo systemctl enable docker || warning "设置Docker服务开机启动失败"
        else
            dnf -y install dnf-plugins-core || warning "安装dnf-plugins-core失败，继续尝试安装Docker..."
            
            # 添加Docker仓库
            dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo || warning "添加Docker仓库失败，继续尝试安装..."
            
            # 安装Docker
            dnf install -y --allowerasing docker-ce docker-ce-cli containerd.io || {
                error "Docker安装失败"
                return 1
            }
            
            # 启动Docker服务
            systemctl start docker || warning "启动Docker服务失败"
            systemctl enable docker || warning "设置Docker服务开机启动失败"
        fi
    elif command -v apt &>/dev/null; then
        # 纯apt系统
        info "使用apt安装Docker..."
        
        if command -v sudo &>/dev/null; then
            sudo apt update -qq || warning "apt update失败，继续尝试安装..."
            sudo apt install -y docker.io || {
                error "Docker安装失败"
                return 1
            }
            
            # 启动Docker服务
            sudo systemctl start docker || warning "启动Docker服务失败"
            sudo systemctl enable docker || warning "设置Docker服务开机启动失败"
        else
            apt update -qq || warning "apt update失败，继续尝试安装..."
            apt install -y docker.io || {
                error "Docker安装失败"
                return 1
            }
            
            # 启动Docker服务
            systemctl start docker || warning "启动Docker服务失败"
            systemctl enable docker || warning "设置Docker服务开机启动失败"
        fi
    elif command -v zypper &>/dev/null; then
        # openSUSE系统
        info "检测到openSUSE系统，使用zypper安装Docker..."
        
        if command -v sudo &>/dev/null; then
            # 添加Docker仓库
            sudo zypper addrepo --refresh https://download.docker.com/linux/sles/docker-ce.repo || warning "添加Docker仓库失败，继续尝试安装..."
            
            # 刷新仓库
            sudo zypper refresh || warning "更新仓库缓存失败，继续尝试安装..."
            
            # 安装Docker
            sudo zypper install -y docker-ce docker-ce-cli containerd.io || {
                warning "Docker CE安装失败，尝试使用系统仓库安装..."
                sudo zypper install -y docker || {
                    # 检查Docker命令是否已经可用
                    if command -v docker &>/dev/null; then
                        warning "尽管安装命令返回错误，但Docker命令已可用，继续执行"
                    else
                        error "Docker安装失败"
                        return 1
                    fi
                }
            }
            
            # 启动Docker服务
            sudo systemctl start docker || warning "启动Docker服务失败"
            sudo systemctl enable docker || warning "设置Docker服务开机启动失败"
            
            # 配置Docker镜像加速器
            sudo mkdir -p /etc/docker
            echo '{
  "registry-mirrors": ["https://registry.docker-cn.com", "https://docker.mirrors.ustc.edu.cn", "https://hub-mirror.c.163.com", "https://hub.fast360.xyz","https://hub.rat.dev","https://hub.littlediary.cn","https://docker.kejilion.pro","https://dockerpull.cn","https://docker-0.unsee.tech","https://docker.tbedu.top","https://docker.1panelproxy.com","https://docker.melikeme.cn","https://cr.laoyou.ip-ddns.com","https://hub.firefly.store","https://docker.hlmirror.com","https://docker.m.daocloud.io","https://docker.1panel.live","https://image.cloudlayer.icu","https://docker.1ms.run"]
}' | sudo tee /etc/docker/daemon.json > /dev/null
            
            # 重启Docker服务以应用镜像加速器配置
            sudo systemctl daemon-reload
            sudo systemctl restart docker
        else
            # 无sudo情况下的安装（需要root权限）
            zypper addrepo --refresh https://download.docker.com/linux/sles/docker-ce.repo || warning "添加Docker仓库失败，继续尝试安装..."
            zypper refresh || warning "更新仓库缓存失败，继续尝试安装..."
            zypper install -y docker-ce docker-ce-cli containerd.io || {
                warning "Docker CE安装失败，尝试使用系统仓库安装..."
                zypper install -y docker || {
                    # 检查Docker命令是否已经可用
                    if command -v docker &>/dev/null; then
                        warning "尽管安装命令返回错误，但Docker命令已可用，继续执行"
                    else
                        error "Docker安装失败"
                        return 1
                    fi
                }
            }
            
            # 启动Docker服务
            systemctl start docker || warning "启动Docker服务失败"
            systemctl enable docker || warning "设置Docker服务开机启动失败"
            
            # 配置Docker镜像加速器
            mkdir -p /etc/docker
            echo '{
  "registry-mirrors": ["https://registry.docker-cn.com", "https://docker.mirrors.ustc.edu.cn", "https://hub-mirror.c.163.com", "https://hub.fast360.xyz","https://hub.rat.dev","https://hub.littlediary.cn","https://docker.kejilion.pro","https://dockerpull.cn","https://docker-0.unsee.tech","https://docker.tbedu.top","https://docker.1panelproxy.com","https://docker.melikeme.cn","https://cr.laoyou.ip-ddns.com","https://hub.firefly.store","https://docker.hlmirror.com","https://docker.m.daocloud.io","https://docker.1panel.live","https://image.cloudlayer.icu","https://docker.1ms.run"]
}' > /etc/docker/daemon.json
            
            # 重启Docker服务以应用镜像加速器配置
            systemctl daemon-reload
            systemctl restart docker
        fi
    else
        error "无法识别的包管理器，无法自动安装Docker"
        return 1
    fi
    
    # 最终检查Docker命令是否可用
    if command -v docker &>/dev/null; then
        info "Docker命令已可用，安装成功"
    return 0
    fi
    
    error "Docker安装失败，但可能在重启后变得可用"
    return 1
}

# 安装Docker Compose
install_docker_compose() {
    info "安装Docker Compose..."
    
    # 检查Docker是否已安装
    if ! command -v docker &>/dev/null; then
        error "需要先安装Docker才能安装Docker Compose"
        exit 1
    fi
    
    # 在WSL中检查Docker Compose插件
    if grep -q Microsoft /proc/version 2>/dev/null; then
        if docker compose version &>/dev/null; then
            info "检测到Docker已内置Compose插件，无需单独下载"
            info "将使用新版 'docker compose' 命令"
            info "创建docker-compose别名以兼容旧脚本"
            setup_docker_compose_alias
            success "Docker Compose已安装"
            return 0
        fi
    fi
    
    # 检查是否存在离线安装包
    if check_offline_resources; then
        info "检测到本地离线资源，优先使用离线安装..."
        
        # 尝试离线安装Docker Compose
        if install_docker_compose_offline; then
            return 0
        fi
        
        warning "离线安装Docker Compose失败，将回退到在线安装方式"
    fi
    
    # 确保curl已安装
    check_and_install_curl
    
    # 下载Docker Compose二进制文件
    ARCH=$(uname -m)
    # 对amd64/x86_64处理
    if [ "$ARCH" = "x86_64" ]; then
        ARCH="amd64"
    fi
    COMPOSE_VERSION="v2.24.5"
    
    info "下载Docker Compose $COMPOSE_VERSION..."
    
    # 首先检查docker compose子命令是否已可用
    if docker compose version &>/dev/null; then
        info "检测到Docker已内置Compose插件，无需单独下载"
        info "将使用新版 'docker compose' 命令"
        info "创建docker-compose别名以兼容旧脚本"
        setup_docker_compose_alias
        success "Docker Compose已安装"
        return 0
    fi
    
    # 使用官方源
    download_success=false
    COMPOSE_URL="https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
    
    # 尝试使用wget下载
    if command -v wget &>/dev/null; then
        info "使用wget从官方源下载..."
        if wget -q -O docker-compose.tmp "$COMPOSE_URL" && [ -s docker-compose.tmp ]; then
            mv docker-compose.tmp docker-compose
            download_success=true
        fi
    fi
    
    # 如果wget失败或不存在，尝试curl
    if [ "$download_success" = false ] && command -v curl &>/dev/null; then
        info "使用curl从官方源下载..."
        if curl -s -L -o docker-compose.tmp "$COMPOSE_URL" && [ -s docker-compose.tmp ]; then
            mv docker-compose.tmp docker-compose
            download_success=true
        fi
    fi
    
    # 如果下载成功，安装到系统
    if [ "$download_success" = true ] && [ -s docker-compose ]; then
        # 安装Docker Compose
        chmod +x docker-compose
        
        # 尝试移动到系统路径
            if command -v sudo &>/dev/null; then
            if ! sudo mv docker-compose /usr/local/bin/docker-compose; then
                mkdir -p "$HOME/bin"
                mv docker-compose "$HOME/bin/"
                export PATH="$HOME/bin:$PATH"
                echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
            fi
            else
                mkdir -p "$HOME/bin"
            mv docker-compose "$HOME/bin/"
                export PATH="$HOME/bin:$PATH"
                echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
            fi
            
        # 验证安装
            if command -v docker-compose &>/dev/null; then
            success "Docker Compose安装成功"
            docker-compose --version || true
                return 0
            fi
    fi
    
    # 如果下载或安装失败，检查docker compose子命令
    info "Docker Compose二进制文件下载失败，检查Docker是否包含Compose插件..."
    if docker compose version &>/dev/null; then
        info "检测到Docker自带Compose插件可用"
        info "将使用新版 'docker compose' 命令"
        info "创建docker-compose别名以兼容旧脚本"
        setup_docker_compose_alias
        return 0
    else
        warning "无法从预设镜像源下载Docker Compose，也无法使用Docker插件版Compose"
        echo ""
        info "正在尝试从多个镜像站自动下载Docker Compose..."
        
        # 创建临时脚本尝试所有可能的镜像站
        cat << 'EOF' > get_compose.sh
#!/bin/bash
COMPOSE_VERSION="$1"
ARCH="$2"
OUTPUT_FILE="$3"

# 设置超时时间（秒）
TIMEOUT=10

# 所有可能的镜像站
MIRRORS=(
  # 开源社区镜像
  "https://mirrors.tuna.tsinghua.edu.cn/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirrors.ustc.edu.cn/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirror.sjtu.edu.cn/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirrors.163.com/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirrors.cloud.tencent.com/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirrors.aliyun.com/docker-toolbox/linux/compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirrors.huaweicloud.com/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirror.bytedance.com/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://mirrors.baidubce.com/docker-compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  
  # 代码托管平台镜像
  "https://hub.fastgit.xyz/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://gitee.com/mirrors/compose/raw/master/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://gitlab.cn/api/v4/projects/gitlab-cn%2Fmirror%2Fdocker%2Fcompose/packages/generic/compose/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  
  # GitHub代理
  "https://mirror.ghproxy.com/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://github.91chi.fun/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://gh.ddlc.top/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://kgithub.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://moeyy.cn/gh-proxy/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://ghproxy.net/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://ghps.cc/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://gh.api.99988866.xyz/https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://github.abskoop.workers.dev/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
  "https://download.fastgit.org/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}"
)

echo "开始自动尝试多个镜像站下载Docker Compose ${COMPOSE_VERSION}..."
echo "这可能需要一点时间，请耐心等待..."

# 尝试使用curl下载
if command -v curl &>/dev/null; then
  for url in "${MIRRORS[@]}"; do
    echo "尝试从 $url 下载..."
    if curl -m $TIMEOUT -s -L -o "$OUTPUT_FILE" "$url" && [ -s "$OUTPUT_FILE" ]; then
      echo "下载成功: $url"
      exit 0
    fi
  done
fi

# 尝试使用wget下载
if command -v wget &>/dev/null; then
  for url in "${MIRRORS[@]}"; do
    echo "尝试从 $url 下载..."
    if wget --timeout=$TIMEOUT -q -O "$OUTPUT_FILE" "$url" && [ -s "$OUTPUT_FILE" ]; then
      echo "下载成功: $url"
      exit 0
    fi
  done
fi

# 如果全部失败
echo "所有镜像站下载失败"
            exit 1
EOF
        chmod +x get_compose.sh
        
        download_success=false
        if ./get_compose.sh "$COMPOSE_VERSION" "$ARCH" "docker-compose.tmp"; then
            mv docker-compose.tmp docker-compose
            download_success=true
            success "已自动找到可用镜像站并成功下载Docker Compose"
        else
            warning "自动尝试所有镜像站均失败"
            echo ""
            echo -e "${BLUE}=== 手动输入镜像站 ===${NC}"
            echo "请输入完整的Docker Compose下载链接:"
            read -r custom_url
            
            if [ -n "$custom_url" ]; then
                info "尝试从手动输入的URL下载: $custom_url"
                
                # 尝试使用wget下载
                if command -v wget &>/dev/null; then
                    if wget -q -O docker-compose.tmp "$custom_url" && [ -s docker-compose.tmp ]; then
                        mv docker-compose.tmp docker-compose
                        download_success=true
                    fi
                fi
                
                # 如果wget失败或不存在，尝试curl
                if [ "$download_success" = false ] && command -v curl &>/dev/null; then
                    if curl -s -L -o docker-compose.tmp "$custom_url" && [ -s docker-compose.tmp ]; then
                        mv docker-compose.tmp docker-compose
                        download_success=true
                    fi
                fi
                
                if [ "$download_success" = false ]; then
                    error "从手动输入的URL下载Docker Compose失败"
                fi
            fi
        fi
        
        # 清理临时脚本
        rm -f get_compose.sh
        
        # 如果下载成功，安装到系统
        if [ "$download_success" = true ] && [ -s docker-compose ]; then
    # 安装Docker Compose
    chmod +x docker-compose
    
    # 尝试移动到系统路径
    if command -v sudo &>/dev/null; then
        if ! sudo mv docker-compose /usr/local/bin/docker-compose; then
            mkdir -p "$HOME/bin"
            mv docker-compose "$HOME/bin/"
            export PATH="$HOME/bin:$PATH"
            echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
        fi
    else
        mkdir -p "$HOME/bin"
        mv docker-compose "$HOME/bin/"
        export PATH="$HOME/bin:$PATH"
        echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
    fi
    
    # 验证安装
            if command -v docker-compose &>/dev/null; then
            success "Docker Compose安装成功"
            docker-compose --version || true
                return 0
            fi
        fi
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
              install_docker
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

# 检查并设置环境
setup_environment() {
  info "检查并创建必要的目录..."
  mkdir -p nginx
  
  # 检查nginx配置文件是否存在
  if [ ! -f "nginx/default.http.conf" ]; then
    error "找不到nginx/default.http.conf文件，请确保文件存在。"
    exit 1
  fi
  
  if [ ! -f "nginx/default.https.conf" ]; then
    error "找不到nginx/default.https.conf文件，请确保文件存在。"
    exit 1
  fi
  
  # 检查docker-compose文件
  if [ ! -f "docker-compose.yml" ]; then
    error "找不到docker-compose.yml文件，请确保文件存在。"
    exit 1
  fi
  
  success "环境检查完成"
}

# 初始化部署
init_deploy() {
  info "正在初始化部署环境..."
  
  setup_environment
  
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
    sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" nginx/default.http.conf
    sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" nginx/default.https.conf
    
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
  cp nginx/default.http.conf nginx/default.conf
  
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
    info "尝试在nginx/certbot-entrypoint.sh中替换邮箱和域名参数..."
    # 直接替换邮箱和域名参数
    sed_i "s|--email your-email@example.com|--email $EMAIL|g" nginx/certbot-entrypoint.sh
    sed_i "s|-d example.com -d www.example.com|$DOMAINS_PARAM|g" nginx/certbot-entrypoint.sh
    
    success "成功更新certbot-entrypoint.sh中的邮箱和域名参数"
  fi
  
  success "部署环境初始化完成"
}

# 修改docker-compose.yml中的nginx卷挂载
update_nginx_volumes() {
  info "更新Nginx卷挂载配置..."
  DOMAIN_CONFIG="${DOMAINS[*]}"
  
  info "配置服务器名称为: $DOMAIN_CONFIG"
  sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" nginx/default.http.conf
  sed_i "s/example.com www.example.com/$DOMAIN_CONFIG/g" nginx/default.https.conf
  
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
      NEW_MOUNT_LINE="      - ./nginx/default.conf:/etc/nginx/conf.d/default.conf"
      
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
  if [ -f "nginx/enable-https.sh" ]; then
    info "确保enable-https.sh有执行权限..."
    chmod +x nginx/enable-https.sh || warning "无法修改nginx/enable-https.sh权限，容器内可能会出现权限问题"
    # 检查是否成功赋权
    if [ -x "nginx/enable-https.sh" ]; then
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
    return 1
  fi
  
  success "服务启动命令执行成功"
  return 0
}

# 等待并应用SSL证书
setup_https() {
  info "等待SSL证书生成..."
  
  # 给certbot容器一些时间来完成
  sleep 15
  
  # 检查certbot容器状态
  CERTBOT_EXIT_CODE=$(docker inspect poetize-certbot --format='{{.State.ExitCode}}' 2>/dev/null || echo "-1")
  
  if [ "$CERTBOT_EXIT_CODE" = "0" ]; then
    info "SSL证书已成功生成，正在启用HTTPS..."
    
    # 先给容器内脚本赋予执行权限
    info "给enable-https.sh赋予执行权限..."
    if ! docker exec poetize-nginx chmod +x /enable-https.sh; then
      warning "直接chmod失败，尝试使用sudo..."
      if ! docker exec poetize-nginx sh -c "command -v sudo >/dev/null && sudo chmod +x /enable-https.sh || chmod +x /enable-https.sh"; then
        warning "无法给脚本赋予执行权限，可能会导致HTTPS启用失败"
      fi
    fi
    
    # 执行Nginx容器内的enable-https.sh脚本
    if ! docker exec poetize-nginx /enable-https.sh; then
      warning "执行enable-https.sh脚本失败，HTTPS可能未正确启用"
      warning "您可以稍后手动运行: docker exec poetize-nginx /enable-https.sh"
      return 1
    fi
    
    success "HTTPS已启用！"
    return 0
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
  if [ -f "nginx/enable-https.sh" ]; then
    chmod +x nginx/enable-https.sh 2>/dev/null || {
      warning "无法修改nginx/enable-https.sh的权限，可能需要手动设置"
      info "您可以稍后手动运行: chmod +x nginx/enable-https.sh"
    }
    if [ -x "nginx/enable-https.sh" ]; then
      success "已设置脚本执行权限"
    else
      warning "无法验证脚本是否有执行权限，继续部署"
    fi
  else
    error "找不到nginx/enable-https.sh文件"
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
  if [ ! -f "nginx/default.http.conf" ]; then
    error "找不到nginx/default.http.conf文件"
    exit 1
  fi
  
  if [ ! -f "nginx/default.https.conf" ]; then
    error "找不到nginx/default.https.conf文件"
    exit 1
  fi
  
  if [ ! -f "nginx/enable-https.sh" ]; then
    error "找不到nginx/enable-https.sh文件"
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
  echo ""
  
  echo -n "是否确认以上设置? [Y/n]: "
  read CONFIRM
  
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
  
  # 检查bc命令是否可用
  check_and_install_bc
  
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

# 备份配置
backup_config() {
  info "备份当前配置..."
  
  local BACKUP_DIR="backups/$(date +%Y%m%d_%H%M%S)"
  mkdir -p "$BACKUP_DIR"
  
  # 备份配置文件
  cp nginx/default.http.conf "$BACKUP_DIR/" 2>/dev/null || true
  cp nginx/default.https.conf "$BACKUP_DIR/" 2>/dev/null || true
  cp docker-compose.yml "$BACKUP_DIR/" 2>/dev/null || true
  cp mysql/conf/my.cnf "$BACKUP_DIR/" 2>/dev/null || true
  
  # 备份数据库
  if docker ps | grep -q poetize-mysql; then
    info "备份数据库..."
    docker exec poetize-mysql mysqldump -u root -proot123 poetize > "$BACKUP_DIR/poetize.sql"
  fi
  
  success "配置已备份到 $BACKUP_DIR"
}

# 动态内存优化函数
apply_memory_optimizations() {
  local MEMORY_MODE="$1"
  local TOTAL_MEM_GB="$2"
  
  info "应用动态内存优化 (模式: $MEMORY_MODE, 总内存: ${TOTAL_MEM_GB}GB)..."
  
  # 创建MySQL配置目录
  mkdir -p mysql/conf
  
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
      
      JAVA_LIMIT="768M"
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
  # 备份原配置(如果存在)
  [ -f "mysql/conf/my.cnf" ] && cp mysql/conf/my.cnf mysql/conf/my.cnf.bak
  
  # 创建新配置
  cat > mysql/conf/my.cnf << EOF
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

# 清理函数
cleanup() {
  info "执行清理操作..."
  
  # 确保DOCKER_COMPOSE_CMD已定义
  DOCKER_COMPOSE_CMD=${DOCKER_COMPOSE_CMD:-"docker-compose"}
  
  # 停止所有服务
  eval "$DOCKER_COMPOSE_CMD down" || warning "无法停止服务，可能服务未运行"
  
  # 清理临时文件
  rm -f nginx/default.conf
  rm -f .poetize-config
  rm -f docker-compose.yml.resource_backup
  rm -f mysql/conf/my.cnf.bak
  
  # 清理未使用的镜像和卷
  docker system prune -f
  docker volume prune -f
  
  success "清理完成"
}

# 回滚函数
rollback() {
  info "执行回滚操作..."
  
  # 停止所有服务
  eval "$DOCKER_COMPOSE_CMD down"
  
  # 恢复备份的配置
  if [ -d "$BACKUP_DIR" ]; then
    cp "$BACKUP_DIR/default.http.conf" nginx/ 2>/dev/null || true
    cp "$BACKUP_DIR/default.https.conf" nginx/ 2>/dev/null || true
    cp "$BACKUP_DIR/docker-compose.yml" . 2>/dev/null || true
    cp "$BACKUP_DIR/my.cnf" mysql/conf/ 2>/dev/null || true
    
    # 恢复数据库
    if [ -f "$BACKUP_DIR/poetize.sql" ]; then
      docker exec -i poetize-mysql mysql -u root -proot123 poetize < "$BACKUP_DIR/poetize.sql"
    fi
  fi
  
  success "回滚完成"
}

# 设置错误处理
set -e
trap 'error "部署失败，执行回滚..."; rollback; exit 1' ERR
# trap 'cleanup' EXIT

# 添加一个函数用于检查和安装bc命令
check_and_install_bc() {
  if ! command -v bc &>/dev/null; then
    warning "未检测到bc命令，尝试自动安装..."
    
    # 检查是否存在过期的仓库配置
    if [ -f "/etc/apt/sources.list" ] && grep -q "buster-backports" /etc/apt/sources.list; then
      info "检测到buster-backports源可能有问题，尝试修复..."
      # 创建备份
      if command -v sudo &>/dev/null; then
        sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak
        # 注释掉有问题的backports源
        sudo_sed_i 's/^deb http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
        sudo_sed_i 's/^deb-src http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
      else
        cp /etc/apt/sources.list /etc/apt/sources.list.bak
        # 注释掉有问题的backports源
        sed_i 's/^deb http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
        sed_i 's/^deb-src http:\/\/deb.debian.org\/debian buster-backports/# &/' /etc/apt/sources.list
      fi
      info "已注释掉过期的backports源，重试安装..."
    fi
    
    # 检测操作系统类型并安装bc
    if command -v apt-get &>/dev/null; then
      # Debian/Ubuntu
      if command -v sudo &>/dev/null; then
        sudo apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
        sudo apt-get install -y bc || warning "安装bc失败，将使用替代方法"
      else
        apt-get update -qq || warning "apt-get update失败，继续尝试安装..."
        apt-get install -y bc || warning "安装bc失败，将使用替代方法"
      fi
    elif command -v yum &>/dev/null; then
      # CentOS/RHEL
      if command -v sudo &>/dev/null; then
        sudo yum install -y bc || warning "安装bc失败，将使用替代方法"
      else
        yum install -y bc || warning "安装bc失败，将使用替代方法"
      fi
    elif command -v dnf &>/dev/null; then
      # Fedora
      if command -v sudo &>/dev/null; then
        sudo dnf install -y bc || warning "安装bc失败，将使用替代方法"
      else
        dnf install -y bc || warning "安装bc失败，将使用替代方法"
      fi
    elif command -v pacman &>/dev/null; then
      # Arch Linux
      if command -v sudo &>/dev/null; then
        sudo pacman -S --noconfirm bc || warning "安装bc失败，将使用替代方法"
      else
        pacman -S --noconfirm bc || warning "安装bc失败，将使用替代方法"
      fi
    elif command -v zypper &>/dev/null; then
      # openSUSE
      if command -v sudo &>/dev/null; then
        sudo zypper install -y bc || warning "安装bc失败，将使用替代方法"
      else
        zypper install -y bc || warning "安装bc失败，将使用替代方法"
      fi
    else
      warning "无法自动安装bc，将使用替代方法进行浮点数比较"
      return 1
    fi
    
    # 再次检查是否安装成功
    if ! command -v bc &>/dev/null; then
      warning "bc安装失败，将使用替代方法进行浮点数比较"
      return 1
    else
      success "bc安装成功"
      return 0
    fi
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
      chmod +x /tmp/docker_offline/install.sh
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

# 从离线包安装Docker Compose
install_docker_compose_offline() {
  info "使用离线文件安装Docker Compose..."
  
  if [ -f "./offline/docker-compose" ]; then
    # 复制到系统路径
    if command -v sudo &>/dev/null; then
      sudo cp -f ./offline/docker-compose /usr/local/bin/docker-compose
      sudo chmod +x /usr/local/bin/docker-compose
    else
      # 如果没有sudo，尝试直接复制或复制到用户目录
      if cp -f ./offline/docker-compose /usr/local/bin/docker-compose 2>/dev/null; then
        chmod +x /usr/local/bin/docker-compose
      else
        mkdir -p "$HOME/bin"
        cp -f ./offline/docker-compose "$HOME/bin/"
        chmod +x "$HOME/bin/docker-compose"
        export PATH="$HOME/bin:$PATH"
        grep -q "PATH=\"\$HOME/bin:\$PATH\"" "$HOME/.bashrc" || echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
      fi
    fi
    
    # 检查安装结果
    if command -v docker-compose &>/dev/null; then
      success "从离线文件安装Docker Compose成功"
      docker-compose --version || true
      return 0
    else
      warning "从离线文件安装Docker Compose失败，将尝试在线安装"
    fi
  else
    warning "未找到离线Docker Compose安装包"
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
              install_docker
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
  
  if [ -f "./mysql/conf/my.cnf" ]; then
    # 获取当前权限
    current_perm=$(stat -c "%a" ./mysql/conf/my.cnf 2>/dev/null || stat -f "%Lp" ./mysql/conf/my.cnf 2>/dev/null)
    
    # 如果权限不是644，则修改
    if [ "$current_perm" != "644" ]; then
      info "MySQL配置文件权限不正确，当前权限: $current_perm，修改为644..."
      chmod 644 ./mysql/conf/my.cnf
      success "MySQL配置文件权限已修复"
    else
      info "MySQL配置文件权限正确: 644"
    fi
  else
    warning "MySQL配置文件 ./mysql/conf/my.cnf 不存在，将在首次运行时创建"
  fi
}

# 主函数
main() {
  # 显示横幅
  echo -e "${BLUE}=====================================${NC}"
  echo -e "${BLUE}    欢迎使用 Poetize 部署脚本    ${NC}"
  echo -e "${BLUE}=====================================${NC}"
  echo ""
  echo -e "作者: LeapYa    联系方式: enable_lazy@qq.com"
  echo -e "仓库地址: https://github.com/lazy-liang/poetize"
  echo -e "Wiki文档: https://github.com/lazy-liang/poetize/wiki"
  echo ""
  
  # 打印调试信息
  echo "----------------------------------------"
  echo "调试信息: AUTO_YES=$AUTO_YES"
  echo "RUN_IN_BACKGROUND=$RUN_IN_BACKGROUND"
  echo "----------------------------------------"
  
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
        install_docker
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
  
  # 检查并安装Docker Compose
  if ! (command -v docker &>/dev/null && docker compose version &>/dev/null) && ! command -v docker-compose &>/dev/null; then
    info "Docker Compose未安装"
    
    if grep -q Microsoft /proc/version 2>/dev/null; then
      echo ""
      echo -e "${BLUE}=== 在WSL中使用Docker Compose ===${NC}"
      echo "1. 确保Docker Desktop已安装并正在运行"
      echo "2. Docker Desktop通常已包含Docker Compose功能"
      echo "3. 确保在WSL集成设置中启用了当前发行版"
      echo ""
      
      auto_confirm "是否安装Docker Compose? (y/n/s) [y=安装, n=退出, s=跳过]: " "y" "-n 1 -r"
      if [[ $REPLY =~ ^[Yy]$ ]]; then
        install_docker_compose
      elif [[ $REPLY =~ ^[Ss]$ ]]; then
        warning "跳过Docker Compose安装，尝试继续部署"
        warning "将尝试使用docker命令直接管理容器"
      else
        error "已取消部署"
        exit 1
      fi
    else
      info "开始执行安装程序"
      install_docker_compose
      success "Docker Compose安装成功"
    fi
  else
    info "Docker Compose已安装，无需执行安装程序"
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
  
  # 添加配置备份
  backup_config
  
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
    if [ "$PRIMARY_DOMAIN" != "localhost" ] && [ "$PRIMARY_DOMAIN" != "127.0.0.1" ] && ! [[ "$PRIMARY_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo ""
        info "正在启用HTTPS..."
        if docker exec poetize-nginx /enable-https.sh; then
        sleep 5
          success "HTTPS已成功启用!"
          ENABLE_HTTPS=true
        else
          warning "HTTPS启用失败。如果需要，请稍后手动运行: docker exec poetize-nginx /enable-https.sh"
      fi
    else
      info "本地域名环境不支持HTTPS，如需使用HTTPS请配置有效域名"
    fi
  fi

    
  # 调用部署完成函数
  clean_docker_build_cache
  
  # 打印部署汇总信息
  print_summary
  
  echo ""
  echo -e "${BLUE}=============================================================================${NC}"
  echo -e "${BLUE}      Poetize 部署脚本执行完毕，请留意以上的汇总信息，感谢您选择Poetize      ${NC}"
  echo -e "${BLUE}=============================================================================${NC}"
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