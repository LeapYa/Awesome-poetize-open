<p align="center">
  <a href="#">
    <img src="poetize_picture/首页1.jpg" alt="Logo" width="100%">
  </a>

<h3 align="center">POETIZE 最美博客（AGPL 分支 · LeapYa 维护）</h3>
  <p align="center">
    让内容创作与社交体验更美好
    <br />
    <a href="#-功能特性"><strong>探索功能特性 »</strong></a>
    <br />
    <br />
    <a href="#-快速开始">快速部署</a>
    ·
    <a href="#-部署文档">部署文档</a>
    ·
    <a href="#️-开发指南">二次开发</a>
  </p>
  <p align="center">
   <img src="https://img.shields.io/badge/license-AGPL--3.0-%3CCOLOR%3E.svg" alt="AGPL License">
   <img src="https://img.shields.io/badge/language-java-%23B07219.svg" alt="Java">
   <img src="https://img.shields.io/badge/language-python-%233572A5.svg" alt="Python">
   <img src="https://img.shields.io/badge/language-dockerfile-%23384D54.svg" alt="Dockerfile">
  </p>
</p>

## 📋目录

- [🙏 项目致谢](#-项目致谢)
- [📖 项目简介](#-项目简介)
- [🎨 项目预览](#-项目预览)
- [🚀 快速开始](#-快速开始)
- [📋 部署文档](#-部署文档)
- [✨ 功能特性](#-功能特性)
- [📝 配置指南](#-配置指南)
- [🤝 贡献与许可](#-贡献与许可)
- [🛠️ 开发指南](#️-开发指南)
- [🔧 技术栈](#-技术栈)
- [📞 联系方式](#-联系方式)
- [📜 版权说明](#-版权说明)

## 🙏 项目致谢

本项目基于原作者Sara的开源项目 [POETIZE最美博客](https://gitee.com/littledokey/poetize) 进行功能扩展和定制化开发。感谢原作者提供的优秀博客系统基础框架。

⚠️ 本项目为非官方 fork，不代表原作者立场。

## 📖 项目简介

Poetize是一个将博客系统与即时通讯巧妙融合的内容平台，为用户提供一站式内容创作与社交交流体验。

**适用人群**

* **创作者/博主** – 拥有文章、图片墙、留言等全面功能，与读者直接互动
* **开发学习者** – 基于Java+Vue全栈技术，集成前后端、数据库、即时通讯的学习项目
* **小型社群** – 内容发布与成员交流（好友、群聊、朋友圈）一体化平台

**特色功能**

* **极简部署** – 得益于Docker容器化技术，只需一行命令即可完成从环境配置到服务启动的全流程
* **安全增强** – 精细的权限管理，滑动拼图验证保障，限制请求，防止Dos攻击等
* **功能扩展** – 支持多邮箱配置、支持主流第三方登录（GitHub/Google/Twitter等）
* **体验优化** – 现代UI设计、可控Live2D看板娘、Python驱动SEO自动化、有更明显的错误提示等

无论您是创建个人站点还是学习全栈开发，Poetize 优化版都是理想选择。

## 🎨 项目预览

<p align="center">
  <img src="poetize_picture/首页.png" alt="首页" width="100%">
</p>

<p align="center">博客首页 - 展示个人创作与生活点滴</p>

<p align="center">
  <img src="poetize_picture/首页1.jpg" alt="文章展示" width="49%">
  <img src="poetize_picture/首页2.jpg" alt="社交功能" width="49%">
</p>

<p align="center">左：内容布局展示 | 右：社交功能体验</p>

## 🚀 快速开始

```bash
# 你只需要输入域名邮箱即可
bash <(curl -sL install.leapya.com)
```

无需手动配置Docker、编译代码或设置环境变量，脚本会自动处理所有细节，包括:

* Docker环境检测与安装
* 数据库初始化
* 服务编排与启动
* 自动HTTPS配置

## 📋 部署文档

### 架构概览

系统采用容器化部署，包含七大核心服务：

1. 主站前端 (Vue 2)
2. 聊天室前端 (Vue 3)
3. Java 后端（Java 21）
4. Python 后端（Python 3.9+）
5. 数据库（MariaDB 11，兼容MySQL 5.7）
6. Nginx 反向代理
7. Certbot SSL 证书

> **注意**: 如需使用MySQL替代MariaDB，请查看本项目的开发指南

### 部署流程

#### 1. 服务器要求

- 操作系统：推荐 Debian 10.3.3+ 或 Ubuntu 18.04+
- 内存：建议 4GB 及以上（2GB内存环境将自动配置2GB交换空间）
- 硬盘：建议 30GB 及以上

##### 系统兼容性测试结果

| 操作系统类型          | CPU | 内存 | 存储 | 测试结果      | 备注                          |
| --------------------- | --- | ---- | ---- | ------------- | ----------------------------- |
| Ubuntu 18.04 x64      | 1核 | 4GB  | 30GB | ✅ 推荐       | 稳定性好，兼容性强            |
| Debian 10.3.3 x64     | 1核 | 4GB  | 30GB | ✅ 推荐       | 轻量级选择，性能优            |
| Ubuntu 18.04 x64      | 1核 | 2GB  | 30GB | ⚠️ 勉强可用 | 需启用2GB交换空间             |
| Debian 10.3.3 x64     | 1核 | 2GB  | 30GB | ⚠️ 勉强可用 | 需启用2GB交换空间             |
| CentOS 7.6 x64        | 2核 | 4GB  | 30GB | ✅ 可用       | 需最新版deploy脚本            |
| AlmaLinux 8.5 x64     | 2核 | 4GB  | 30GB | ✅ 可用       | 需最新版deploy脚本            |
| Alibaba Cloud Linux 2 | 2核 | 4GB  | 30GB | ✅ 可用       | 需特定配置调整                |
| Windows Server        | -   | -    | -    | ❌ 不支持     | Docker兼容性问题，未做bat脚本 |
| CentOS 6.x            | -   | -    | -    | ❌ 不支持     | 仓库已失效                    |

#### 2. 环境准备

* 域名解析到服务器
* 开放 80 TCP 和443 UDP/TCP 端口
* 🇨🇳 **国内环境部署需[下载专用离线资源包](#关于中国国内环境部署)**，避免网络问题

#### 3. 部署步骤

1. **拉取仓库**

```bash
# 主源：
bash <(curl -sL install.leapya.com)

# 如因一些网络问题，请使用备用源：
bash <(curl -sL install.leapya.online)
```

1. **启动脚本**

```bash
chmod +x ./deploy.sh && sudo ./deploy.sh
```

3. **配置设置**

* 主域名与管理员邮箱

3. **启动顺序**

* MySQL服务 → 数据初始化 → 后端服务 → 前端构建 → Nginx → 证书申请

### 访问方式

* 主站：`http(s)://域名/`
* 聊天室：`http(s)://域名/im`
* 管理后台：`http(s)://域名/login`
* 默认账号：`Sara / aaa`

#### Ollama翻译模型配置（可选）

如果你想启用本地AI翻译功能，我们也集成了Ollama模型支持。只需要取消 `docker-compose.yml`中的相关注释即可：

```bash
# 编辑docker-compose.yml文件
vim docker-compose.yml

# 找到"# Ollama翻译模型服务"部分，取消注释即可启用
# 模型会自动下载qwen3:0.6b轻量级翻译模型
```

启用后将提供：

* 本地化AI翻译服务
* 无需依赖第三方翻译API
* 支持中英文互译
* 模型数据持久化存储

##### 自定义模型配置

如果你不想使用默认的 `qwen3:0.6b`模型，可以修改为其他支持的模型：

1. **修改docker-compose.yml**

   ```yaml
   # 找到OLLAMA_MODELS环境变量，修改为你想要的模型
   - OLLAMA_MODELS=deepseek-r1:8b  # 例如改为deepseek-r1:8b
   ```
2. **修改Dockerfile配置**

   ```bash
   # 编辑docker/translation_model/Dockerfile
   vim docker/translation_model/Dockerfile

   # 将其中的qwen3:0.6b替换为你想要的模型名称，如deepseek-r1:8b、qwen3:8b等
   ```

**更多模型选择：**

你可以访问 [Ollama官方模型库](https://ollama.com/library) 查看所有可用的模型，包括：

* **推理模型**: `deepseek-r1` (1.5b, 7b, 8b, 14b, 32b, 70b, 671b)
* **通用模型**: `qwen3` (0.6b, 1.7b, 4b, 8b, 14b, 30b, 32b), `llama3.2` (1b, 3b)
* **代码模型**: `qwen2.5-coder` (0.5b, 1.5b, 3b, 7b, 14b, 32b)
* **视觉模型**: `llama3.2-vision` (11b, 90b), `qwen2.5vl` (3b, 7b, 32b, 72b)

选择模型时请考虑服务器配置，较大的模型需要更多内存和计算资源。

**量化版本说明：**

Ollama默认提供的是4位量化版本的模型，体积小但精度相对较低。如果需要更高的翻译准确度，可以使用其他量化版本：

```yaml
# 默认4位量化（体积小，速度快）
- OLLAMA_MODELS=qwen3:8b

# 8位量化（准确度更高，体积适中）
- OLLAMA_MODELS=qwen3:8b-q8_0

# 16位量化（最高准确度，体积最大）
- OLLAMA_MODELS=qwen3:8b-fp16
```

**量化版本对比：**

* **4位量化（默认）**: 体积最小，速度最快，适合资源受限环境
* **8位量化（q8_0）**: 平衡准确度与资源消耗，推荐用于翻译任务
* **16位量化（fp16）**: 最高准确度，需要充足内存和存储空间

### 常用命令

```bash
# 容器状态
docker ps -a

# 查看日志
docker logs poetize-nginx

# 服务管理
docker-compose restart
docker-compose down
docker-compose up -d

# HTTPS手动配置
docker exec poetize-nginx /enable-https.sh
```

### 注意事项

1. 立即修改默认管理员密码
2. 确保服务器资源充足
3. HTTPS需正确域名解析

### 故障排查

1. **服务启动问题**

   * Docker服务状态
   * 端口占用检查
   * 配置文件验证
2. **编码问题**

   * MySQL字符集
   * Java连接参数
   * Nginx字符集
3. **HTTPS配置失败**

   * 域名解析验证
   * 80端口访问性
   * 证书目录权限

### 脚本说明

#### 基本用法

```bash
# 交互式部署
./deploy.sh

# 参数部署
./deploy.sh --domain example.com --email admin@example.com --enable-https

# 配置文件部署
./deploy.sh --config .poetize-config

# 保存配置
./deploy.sh --domain example.com --save-config
```

#### 参数说明

* `-d, --domain` - 域名设置
* `-e, --email` - 管理员邮箱
* `--enable-https` - 启用HTTPS
* `--config` - 从文件加载配置
* `--save-config` - 保存配置
* `-b, --background` - 后台(暂不推荐使用)

#### 部署流程

1. **环境检查** - 依赖、目录、资源
2. **配置收集** - 域名、邮箱、HTTPS
3. **服务部署** - 环境初始化与服务启动
4. **部署完成** - 汇总、访问信息、常用命令

#### 关于中国国内环境部署

国内环境存在Docker安装困难或网络受限的情况，为确保顺利部署，项目已提供完整的离线部署方案。只需从Release页面下载离线资源包并按以下结构放置：

##### 1. 使用deploy.sh脚本：
 - 项目中提供的**deploy.sh**脚本已经包含了国内环境的配置和加速源设置。
 - 该脚本会自动配置npm使用淘宝镜像源，并增加网络参数以提高下载速度。
 - 通过执行**deploy.sh**，可以自动完成环境的初始化和依赖的安装。

##### 2. 离线资源包：
 - 从Release页面下载离线资源包。
 - 按照以下结构放置资源包：

   ```
   offline/
   ├── docker.tar.gz           # Docker离线安装包
   ├── docker-compose          # Docker Compose二进制文件 
   └── images/                 # Docker镜像目录
      ├── mysql.tar           # MySQL数据库镜像
      ├── nginx.tar           # Nginx反向代理镜像
      ├── java.tar            # Java后端服务镜像
      └── python.tar          # Python后端服务镜像
   ```

##### 3. 执行部署：
 - 确保所有资源包和配置文件已正确放置。
 - 运行以下命令以启动部署：
   ```
   chmod +x ./deploy.sh && sudo ./deploy.sh
   ```
通过这些步骤，您可以在中国国内环境中顺利部署项目，避免网络限制带来的问题。

## ✨ 功能特性

### 1. 一键部署脚本

提供完整的自动化部署解决方案，零门槛快速搭建，支持离线部署。

### 2. 后台权限管理

实现站长、管理员后台访问控制，支持细粒度权限分配，保障系统安全。

### 3. 多邮箱服务支持

支持配置多种邮箱服务提供商，支持自建邮箱，随机选择发送，提高邮件送达率和系统稳定性。

### 4. 第三方登录集成

集成GitHub、Google、Twitter、Yandex等主流平台登录，降低用户注册门槛。

### 5. 登录界面设计

美观的登录界面和优化了用户体验。

### 6. 机器人验证功能

集成滑动拼图验证码，有效防止机器人攻击，提升系统安全性。

### 7. SEO优化

自动生成sitemap.xml和robots.txt，优化搜索引擎收录，提升网站可见性。

### 8. 看板娘功能

提供Live2D看板娘开关控制，支持拖拽移动、衣服记忆、智能降级等增强功能。

### 9. 导航栏优化

美化导航栏布局，支持后台自定义配置，提升用户体验和管理灵活性。

### 10. 评论体验优化

未登录用户评论时自动保存内容，登录后无缝恢复，避免内容丢失。

## 📝 配置指南

### 邮箱配置

1. **数据库配置**

   * 移除默认配置，改用数据库存储
2. **邮箱管理**

   * 后台管理系统 → "网站设置" → 邮箱配置
   * 支持多邮箱、随机选择、发件人自定义
3. **常用配置**

   * QQ邮箱: smtp.qq.com (465端口, SSL)
   * 网易企业: smtphz.qiye.163.com (465端口, SSL)
   * Gmail: smtp.gmail.com (587端口, STARTTLS)
   * 更多...

### 第三方登录配置

1. **数据库准备**

   * 执行 `poetize.sql`
2. **Python服务配置**

   * 依赖安装：`pip install -r requirements.txt`
   * 环境变量：`.env.example` → `.env`
   * OAuth密钥配置
3. **部署选项**

   * 同服务器不同端口（推荐）
   * 独立服务器
   * Docker容器
   * Serverless云服务
4. **平台开发者设置**

   * GitHub: [Developer Settings](https://github.com/settings/developers)
   * Google: [Cloud Console](https://console.cloud.google.com/)
   * Twitter: [Developer Portal](https://developer.twitter.com/en/portal/dashboard)
   * Yandex: [OAuth](https://oauth.yandex.com/)

## 🤝 贡献与许可

* 原作者：Sara (POETIZE最美博客)
* Fork版本开发：LeapYa
* 开源协议：遵循原项目AGPL协议

## 🛠️ 开发指南

### 环境要求

* **Node.js 14** - 前端开发（其他版本可能不兼容）
* **JDK 21** - Java后端开发
* **Maven/Gradle** - Java项目构建
* **Python 3.9+** - Python后端开发
* **Docker & Compose** - 容器化部署
* **Git** - 版本控制
* **数据库客户端** - 开发调试

### 项目结构

<details>
<summary>
项目主要目录结构（点击展开完整结构）

```
├── deploy.sh                # 部署脚本
├── docker-compose.yml       # docker服务编排文件
├── poetize-im-ui/           # 聊天室UI (Vue3)
├── poetize-server/          # Java后端
├── poetize-ui/              # 博客系统UI (Vue2)
├── py/                      # Python服务
└── README.md                # 项目文档
```

</summary>

```
.
├── deploy.sh                # 部署脚本
├── docker-compose.yml       # 服务编排
├── mysql/                   # MySQL配置
├── nginx/                   # Nginx配置
├── poetize-im-ui/           # 聊天室UI (Vue3)
│   ├── package.json         # 聊天室依赖配置
│   ├── package-lock.json    # 聊天室依赖版本锁定文件
│   └── src/                 # 聊天室源代码
├── poetize-server/          # Java后端
│   ├── pom.xml              # 主项目Maven配置
│   ├── package.json         # 依赖配置
│   ├── package-lock.json    # 依赖版本锁定文件
│   ├── sql/                 # SQL脚本目录
│   │   ├── poetry.sql       # 数据库初始化脚本
│   │   └── update_nav_config.sql # 导航配置更新脚本
│   └── poetry-web/          # Web模块
│       ├── pom.xml          # Web模块Maven配置
│       ├── src/             # Java源代码目录
│       ├── config/          # 配置文件目录 
│       └── data/            # 数据文件目录
├── poetize-ui/              # 博客系统UI (Vue2)
│   ├── package.json         # 博客UI依赖配置
│   ├── package-lock.json    # 博客UI依赖版本锁定文件
│   └── src/                 # 博客UI源代码
├── py/                      # Python服务
│   ├── main.py              # Python主应用入口
│   ├── config.py            # 配置文件处理
│   ├── auth_decorator.py    # 认证装饰器
│   ├── captcha_api.py       # 验证码服务
│   ├── email_api.py         # 邮件服务
│   ├── py_three_login.py    # 第三方登录
│   ├── seo_api.py           # SEO优化服务
│   ├── visit_stats_api.py   # 访问统计
│   ├── web_admin_api.py     # 管理员API
│   ├── data/                # 数据配置目录
│   ├── requirements.txt     # Python依赖列表
│   ├── static/              # 静态资源
│   ├── translation_model/   # 机器翻译模型目录
│   │   ├── translation_api.py     # 翻译API
│   │   ├── translation_service.py # 翻译服务
│   │   ├── models/          # 预训练模型目录
│   │   ├── data/            # 翻译数据
│   │   └── utils.py         # 工具函数
│   └── third_login_config.json # 第三方登录配置
└── README.md                # 项目文档
```

</details>

### 前端开发

1. **依赖安装**

   ```bash
   cd poetize-ui
   npm install
   ```
2. **开发服务**

   ```bash
   npm run serve
   ```
3. **构建发布**

   ```bash
   npm run build
   ```

### Java后端开发

1. **导入项目** - 使用IntelliJ IDEA或Eclipse导入poetize-server目录
2. **主要模块**

   - `poetry-web`: 核心业务模块，含控制器、服务和实体类
   - `sql`: 数据库初始化和更新脚本
3. **配置修改**

   - `poetry-web/src/main/resources/application.yml`
4. **启动应用** - 运行 `com.PoetizeApplication` 主类
5. **构建打包**

   ```bash
   # 在poetize-server根目录执行
   mvn clean package
   ```
6. **打包结果** - 生成的JAR文件位于 `poetry-web/target/` 目录

### Python后端开发

Python服务提供以下关键功能：

- 第三方登录集成（GitHub、Google等）
- 机器翻译服务
- SEO优化（站点地图生成）
- 邮件服务和验证码发送
- 内容管理API

1. **虚拟环境**

   ```bash
   python -m venv venv
   source venv/bin/activate  # Linux/macOS
   venv\Scripts\activate     # Windows
   ```
2. **依赖安装**

   ```bash
   pip install -r requirements.txt
   ```
3. **配置修改** - 修改 `third_login_config.json`配置第三方登录
4. **启动服务** - 运行主入口脚本

   ```bash
   python main.py
   ```
5. **翻译服务** - 位于translation_model目录，可单独运行

   ```bash
   cd translation_model
   python run_server.py
   ```

### 数据库

* 系统默认使用MariaDB 11（完全兼容MySQL 5.7）
* 参考docker-compose.yml中配置
* 表结构变更建议使用迁移工具

### 数据库选择说明

本项目默认采用MariaDB 11作为数据库，而非MySQL 5.7。这是基于以下考虑：

1. **性能优势** - MariaDB在高并发读写场景下通常表现更佳
2. **开源承诺** - MariaDB保持完全开源，没有商业许可限制
3. **兼容性保证** - 完全兼容MySQL的SQL语法和连接协议
4. **额外特性** - 提供更多存储引擎选项和优化器改进

虽然项目可以使用MySQL运行，但我们推荐使用MariaDB以获得更好的性能和未来的可扩展性。若必须使用MySQL，请参考下方切换步骤进行配置调整。两种数据库的数据文件和表结构完全兼容，可以无缝迁移。

#### 从MariaDB切换到MySQL的详细步骤

如需在开发或部署环境中使用MySQL替代MariaDB，按以下步骤操作：

1. **修改Java项目依赖（pom.xml）**

   ```xml
   <!-- 移除MariaDB依赖 -->
   <dependency>
     <groupId>org.mariadb.jdbc</groupId>
     <artifactId>mariadb-java-client</artifactId>
     <version>3.1.4</version>
     <!-- ... -->
   </dependency>

   <!-- 添加MySQL依赖 -->
   <dependency>
     <groupId>mysql</groupId>
     <artifactId>mysql-connector-java</artifactId>
     <version>8.0.33</version>
   </dependency>
   ```
2. **更新Java应用配置（application.yml）**

   ```yaml
   spring:
     datasource:
       # 从这个
       driver-class-name: org.mariadb.jdbc.Driver
       url: jdbc:mariadb://localhost:3306/poetize?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai

       # 改为这个
       driver-class-name: com.mysql.cj.jdbc.Driver
       url: jdbc:mysql://localhost:3306/poetize?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
   ```
3. **修改Docker镜像构建文件（docker/java/Dockerfile）**

   ```Dockerfile
   # 将
   RUN wget -O /app/libs/mariadb-java-client-3.1.4.jar https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.1.4/mariadb-java-client-3.1.4.jar

   # 改为
   RUN wget -O /app/libs/mysql-connector-java-8.0.33.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar
   ```
4. **更新Docker Compose配置（docker-compose.yml）**

   ```yaml
   # MariaDB服务配置
   mysql:
     # 从这个
     image: mariadb:11.1
     # 改为这个
     image: mysql:8.0

     environment:
       # 从这些环境变量
       - MARIADB_ROOT_PASSWORD=root123
       - MARIADB_DATABASE=poetize 
       - MARIADB_USER=poetize
       - MARIADB_PASSWORD=poetize123

       # 改为这些
       - MYSQL_ROOT_PASSWORD=root123
       - MYSQL_DATABASE=poetize
       - MYSQL_USER=poetize
       - MYSQL_PASSWORD=poetize123
   ```

   还需要修改Java服务环境变量部分：

   ```yaml
   poetize-java:
     # ...
     environment:
       # 修改驱动类名
       - SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
       # 修改URL前缀
       - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/poetize?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
   ```
5. **Python服务配置修改**（如果使用Python服务连接数据库）

   ```yaml
   poetize-python:
     # ...
     environment:
       # 修改数据库类型
       - DB_TYPE=mysql
       # 可能需要添加或修改连接参数
       - DB_CHARSET=utf8mb4
   ```
6. **修改my.cnf配置**（MySQL 8.0和MariaDB 11虽然兼容，但有些特定配置可能不同）

   ```ini
   # my.cnf
   [mysqld]
   default-authentication-plugin=mysql_native_password  # MySQL 8.0需要此配置以支持旧密码认证
   # 其他配置...
   ```

这些修改完成后，系统将使用MySQL而非MariaDB作为数据库引擎。注意，MariaDB对MySQL的某些语法有扩展，如果您的SQL使用了这些扩展特性，切换时可能需要调整。

### 配置说明

* **docker-compose.yml** - 服务编排与环境变量
* **nginx/\*.conf** - 反向代理与负载均衡
* **mysql/conf/my.cnf** - 数据库引擎设置
* **应用配置** - Java和Python各自配置文件
* **敏感数据** - 密码、密钥不应提交，使用.gitignore或环境变量

### 注意项

* 在本地开发时需要将poetize-ui\src\utils\constant.js的访问API更换成测试环境的访问API

```
  // 测试环境
  baseURL: "http://localhost:8081",
  pythonBaseURL: "http://localhost:5000", // Python服务URL
  imBaseURL: "http://localhost:81/im",
  webURL: "http://localhost",

  // 生产环境(本地开发需要注释掉)
  // webURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : ''),
  // baseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/api",
  // pythonBaseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/python",
  // imBaseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/im",
```

## 🔧 技术栈

* **前端** - Vue2/Vue3、Element UI、Socket.io、Live2D
* **后端** - Spring Boot、MyBatis Plus、Fastapi、OAuth2.0
* **数据库** - MariaDB 11（兼容MySQL 5.7）
* **部署** - Docker、Docker Compose、Nginx、Shell脚本

## 📞 联系方式

* **邮箱** - enable_lazy@qq.com 或 hi@leapya.com
* **问题反馈** - [GitHub Issues](https://github.com/LeapYa/Awesome-poetize-open/issues)

所有项目贡献者信息请参阅[贡献者](#-贡献与许可)部分。

### 部署方式

本地开发完成后，修改代码并重建Docker镜像，使用 `deploy.sh`脚本进行自动化部署。

## 📜 版权说明

本项目遵循GNU Affero General Public License v3.0 (AGPL-3.0)开源许可协议，详情请参阅[LICENSE](LICENSE)文件。
