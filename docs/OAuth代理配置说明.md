# OAuth代理配置说明

为了解决国内服务器无法访问Google、GitHub、X(Twitter)、Yandex等海外OAuth服务的问题，系统提供了两种代理方式：**域名级反向代理**（推荐）和 **HTTP正向代理**。通过境外反向代理服务器来访问这些服务，但需要准备一台海外VPS。

## 支持的平台

**需要代理的海外平台：**

- GitHub
- Google
- X (Twitter)
- Yandex

**国内可直连、不需要代理的平台：**

- Gitee
- QQ
- 微博
- 百度
- 爱发电
- LinuxDo
- 语雀
- 华为
- 小米
- Microsoft（登录服务国内可直连）
- Apple（登录服务国内可直连）

> [!NOTE]
> GitLab（gitlab.com）与 Steam（steamcommunity.com）未纳入代理体系：国内访问时好时坏，且授权页阶段本就无法由服务端代理解决。

## 方案原理与网络封锁类型

OAuth 登录流程分为两段网络连接，封锁的影响各不相同：

| 阶段 | 连接路径 | 说明 |
|------|---------|------|
| **① 用户授权** | 用户浏览器 → github.com | 用户浏览器直接访问官方授权页 |
| **② 后端API调用** | 国内服务器 → 代理VPS → github.com | Token交换、获取用户信息等 |

**阶段②（后端API调用）** 是本方案解决的核心目标。对各类封锁都有效：

| 封锁类型 | 阶段② 能否解决 | 原理 |
|---------|:-----------:|------|
| **DNS 污染** | ✅ | 国内服务器解析的是代理域名 `auth.example.com`，不涉及被污染的域名 |
| **IP 封锁** | ✅ | 国内服务器连接的是境外VPS的IP，被封的是 github.com 的IP |
| **SNI 阻断** | ✅ | TLS握手时发送的SNI是 `auth.example.com`，GFW看不到 `github.com` |

> [!IMPORTANT]
> **阶段①（用户授权页）** 需要用户浏览器直接访问 `github.com` 等官方域名，无法通过服务端代理解决，需要用户自行解决不能访问官方授权页的问题（如使用科学上网）。

## 方式一：域名级反向代理（推荐）

通过境外VPS上的Nginx反向代理，将API请求转发到目标服务。

**优点**：无需在国内服务器配置额外软件，只需一个域名和VPS。

### 第一步：在海外VPS上配置Nginx

部署Nginx后，配置如下路径映射：

```nginx
server {
    listen 443 ssl http2;
    server_name auth.example.com;
  
    # SSL配置
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
  
    # 通用代理设置
    proxy_http_version 1.1;
    proxy_ssl_server_name on;
    proxy_set_header Connection "";
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_redirect off;

    # ============================================================================
    # GitHub API代理
    # ============================================================================
  
    # Token交换端点
    location = /github/login/oauth/access_token {
        proxy_set_header Host github.com;
        proxy_pass https://github.com/login/oauth/access_token;
    }
  
    # 用户信息和邮箱API
    location /github/api/ {
        proxy_set_header Host api.github.com;
        proxy_pass https://api.github.com/;
    }

    # ============================================================================
    # Google API代理
    # ============================================================================
  
    # Token交换端点
    location /google/oauth2/ {
        proxy_set_header Host oauth2.googleapis.com;
        proxy_pass https://oauth2.googleapis.com/;
    }
  
    # 用户信息端点（统一使用v2接口）
    location = /google/oauth2/v2/userinfo {
        proxy_set_header Host www.googleapis.com;
        proxy_pass https://www.googleapis.com/oauth2/v2/userinfo;
    }

    # ============================================================================
    # X (Twitter) API代理
    # ============================================================================
  
    # OAuth 1.0和2.0的API端点
    location /x/api/ {
        proxy_set_header Host api.twitter.com;
        proxy_pass https://api.twitter.com/;
    }

    # ============================================================================
    # Yandex API代理
    # ============================================================================
  
    # Token交换端点
    location = /yandex/token {
        proxy_set_header Host oauth.yandex.com;
        proxy_pass https://oauth.yandex.com/token;
    }
  
    # 用户信息端点
    location /yandex/login/ {
        proxy_set_header Host login.yandex.ru;
        proxy_pass https://login.yandex.ru/;
    }
}
```

配置要点：

- 为代理域名申请SSL证书（推荐 certbot：`certbot --nginx -d auth.example.com`）
- 建议在 Nginx 中添加 IP 白名单，仅允许你的国内服务器IP访问：在 `server` 块内加 `allow YOUR_SERVER_IP; deny all;`

### 第二步：在 poetize 脚本中配置代理域名

Nginx 配置就绪后，需要将代理域名写入国内服务器的配置。在项目目录下运行：

```bash
poetize -oauth-proxy
```

脚本会启动交互式向导，依次引导你：

1. 确认海外VPS的Nginx已配置完毕
2. 输入代理域名（格式：`https://auth.example.com`）
3. 自动将 `OAUTH_PROXY_DOMAIN` 写入 `.env` 文件，并重启 Java 服务使配置生效

> [!NOTE]
> 也可以直接编辑项目根目录下的 `.env` 文件，手动设置 `OAUTH_PROXY_DOMAIN=https://auth.example.com`，然后执行 `docker compose up -d --no-deps --force-recreate java-backend` 重启 Java 服务。

### 第三步：验证配置

```bash
# 从国内服务器测试代理端点是否可访问
curl https://auth.example.com/github/api/user
curl https://auth.example.com/google/oauth2/token
```

应返回 GitHub/Google API 的响应内容（即便未携带有效 token，也应返回 JSON 错误信息而非连接失败）。

## 方式二：HTTP正向代理

通过 HTTP 代理服务器（如 Squid、3proxy 等）转发请求。

**优点**：不需要额外域名，配置简单。

### 第一步：在海外VPS上搭建HTTP正向代理

以 Squid 为例：

1. **安装 Squid**：

   ```bash
   # Ubuntu/Debian
   apt install squid

   # CentOS/RHEL
   yum install squid
   ```

2. **配置 `/etc/squid/squid.conf`**：

   ```conf
   # 监听端口
   http_port 8080

   # === ACL 定义 ===
   # 仅允许国内服务器IP访问（替换为你的实际IP）
   acl allowed_servers src YOUR_SERVER_IP/32
   # 允许HTTPS代理（CONNECT方法）
   acl SSL_ports port 443
   acl CONNECT method CONNECT
   # 仅允许代理OAuth相关域名（可选，增强安全性）
   acl oauth_domains dstdomain .github.com .googleapis.com .twitter.com .yandex.com .yandex.ru

   # === 访问规则（按顺序匹配，先匹配先生效） ===
   # 1. 只允许指定IP访问
   http_access deny !allowed_servers
   # 2. 允许CONNECT到443端口（HTTPS代理必需）
   http_access allow CONNECT SSL_ports
   # 3. 允许访问OAuth相关域名
   http_access allow oauth_domains
   # 4. 拒绝其他所有请求
   http_access deny all
   ```

3. **启动服务**：

   ```bash
   systemctl enable squid
   systemctl start squid
   ```

> [!WARNING]
> HTTP正向代理必须限制访问来源IP，否则可能被滥用为开放代理。务必在 `squid.conf` 中配置 `acl allowed_servers` 或使用防火墙规则限制。

### 第二步：在 docker-compose.yml 中配置环境变量

编辑项目根目录下的 `docker-compose.yml`，在 Java 服务的环境变量中添加：

```bash
OAUTH_PROXY_HOST=your-vps-ip
OAUTH_PROXY_PORT=8080
```

### 第三步：重启 Java 服务

```bash
docker compose up -d --no-deps --force-recreate java-backend
```

### 第四步：验证配置

```bash
# 从国内服务器测试，通过代理访问 GitHub API
curl -x http://your-vps-ip:8080 https://api.github.com/user
```

## 工作流程

以 GitHub 为例：

```
用户浏览器                    国内服务器                    境外VPS (auth.example.com)       GitHub
    |                            |                              |                            |
    |-- 1. 点击GitHub登录 ------>|                              |                            |
    |<- 2. 返回授权URL ----------|                              |                            |
    |                            |                              |                            |
    |-- 3. 直接访问授权页 ------------------------------------------------>  授权页面          |
    |<- 4. 用户授权，GitHub回调 --|--------- 回调到国内 -------->|                            |
    |                            |                              |                            |
    |                            |-- 5. Token交换 ------------->|-- 转发 ------------------>|
    |                            |<- 6. 返回access_token -------|<- 返回 -------------------|
    |                            |                              |                            |
    |                            |-- 7. 获取用户信息 ----------->|-- 转发 ------------------>|
    |                            |<- 8. 返回用户信息 ------------|<- 返回 -------------------|
    |                            |                              |                            |
    |<- 9. 登录成功 -------------|                              |                            |
```

- **步骤3**：用户浏览器直接访问 `https://github.com/login/oauth/authorize`（需要用户自行解决网络问题）
- **步骤5-8**：后端通过代理完成，**用户无需任何额外配置**

### 后端代码架构

系统通过两层架构处理 OAuth 代理：

- **Provider层**：`OAuth2Provider`（OAuth 2.0）和 `OAuth1Provider`（OAuth 1.0）基类提供 `getProxiedUrl()` 方法，各平台 Provider（如 `GitHubOAuthProvider`）在 `getTokenUrl()` 和 `getUserInfoUrl()` 中调用
- **兼容层**：`OAuthClientServiceImpl` 中的 `getTokenUrl()` 和 `getUserInfoUrl()` 也独立支持代理URL映射
- **网络层**：`OAuthRestTemplateConfig` 配置 HTTP 正向代理（方式二）

## 注意事项

1. **两种方式互斥**：方式一和方式二可以同时配置，但建议只启用一种，避免行为混乱
2. **域名证书**：方式一的境外代理域名需要配置有效的SSL证书
3. **回调配置**：OAuth应用的回调URL直接配置为国内域名，无需改动
4. **路径精确**：方式一的Nginx路径映射必须与代码中的URL规则完全一致
5. **安全建议**：建议在Nginx中限制代理域名的访问来源IP，仅允许你的国内服务器访问
6. **网络稳定**：境外代理服务器需要稳定的网络连接

## 工作原理

以 GitHub 为例，完整的 OAuth 登录流程：

```
用户浏览器                    国内服务器                    境外VPS (auth.example.com)       GitHub
    |                            |                              |                            |
    |-- 1. 点击GitHub登录 ------>|                              |                            |
    |<- 2. 返回授权URL ----------|                              |                            |
    |                            |                              |                            |
    |-- 3. 直接访问授权页 ------------------------------------------------>  授权页面          |
    |<- 4. 用户授权，GitHub回调 --|--------- 回调到国内 -------->|                            |
    |                            |                              |                            |
    |                            |-- 5. Token交换 ------------->|-- 转发 ------------------>|
    |                            |<- 6. 返回access_token -------|<- 返回 -------------------|
    |                            |                              |                            |
    |                            |-- 7. 获取用户信息 ----------->|-- 转发 ------------------>|
    |                            |<- 8. 返回用户信息 ------------|<- 返回 -------------------|
    |                            |                              |                            |
    |<- 9. 登录成功 -------------|                              |                            |
```

- **步骤3**：用户浏览器直接访问 `https://github.com/login/oauth/authorize`（需要用户自行解决网络问题）
- **步骤5-8**：后端通过代理完成，**用户无需任何额外配置**

### 后端代码架构

系统通过两层架构处理 OAuth 代理：

- **Provider层**：`OAuth2Provider`（OAuth 2.0）和 `OAuth1Provider`（OAuth 1.0）基类提供 `getProxiedUrl()` 方法，各平台 Provider（如 `GitHubOAuthProvider`）在 `getTokenUrl()` 和 `getUserInfoUrl()` 中调用
- **兼容层**：`OAuthClientServiceImpl` 中的 `getTokenUrl()` 和 `getUserInfoUrl()` 也独立支持代理URL映射
- **网络层**：`OAuthRestTemplateConfig` 配置 HTTP 正向代理（方式二）

## 故障排除

| 问题 | 可能原因 | 排查方法 |
|------|---------|---------|
| 用户无法访问授权页 | 用户网络被封锁（DNS/SNI/IP） | 用户需自行解决（VPN等），这不是服务端能解决的 |
| Token获取失败 | 代理路径配置错误或VPS不通 | 检查 `OAUTH_PROXY_DOMAIN` 配置和Nginx路径映射 |
| 用户信息获取失败 | 用户信息API代理路径不正确 | 用 `curl` 直接测试代理端点是否可访问 |
| 代理连接超时 | VPS网络不稳定或SSL证书问题 | 检查VPS连通性和证书有效期 |
| 配置了代理但未生效 | 环境变量未正确写入 | 检查 `.env` 文件中 `OAUTH_PROXY_DOMAIN` 是否存在，并确认 Java 服务已重启 |
