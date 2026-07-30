<!--
  POETIZE 最美博客系统 - LeapYa维护版
  
  Keywords: 博客系统, blog system, 个人博客, personal blog, Vue3, Spring Boot, 
  Java, Docker, 一键部署, 开源博客, open source blog, 聊天室, chatroom, 
  Live2D, SEO优化, 自托管, self-hosted, MariaDB, Redis, WebSocket, 
  OAuth2登录, 第三方登录, 看板娘, 最美博客, poetize, 表白墙, 相册, 
  markdown博客, 博客模板, blog template, docker-compose
  
  GitHub: https://github.com/LeapYa/awesome-poetize-open
  Demo: https://leapya.com
-->

<p align="center">
  <a href="#">
    <img src="poetize_picture/logo.png" alt="POETIZE Open Fork Logo" width="180px">
  </a>

<h1 align="center">POETIZE 最美博客（AGPL 分支 · LeapYa 维护）</h1>
  <p align="center">
    让内容创作与社交体验更美好
    <br />
    <br />
    <a href="#-快速开始">快速部署</a>
    ·
    <a href="#-部署文档">部署文档</a>
    ·
    <a href="#-开发指南">二次开发</a>
  </p>
  <p align="center">
   <img src="https://img.shields.io/github/license/LeapYa/awesome-poetize-open" alt="License">
   <img src="https://img.shields.io/badge/Vue.js-35495e.svg?logo=vue.js&logoColor=4FC08D" alt="Vue.js">
   <img src="https://img.shields.io/badge/Java-%23ED8B00.svg?logo=openjdk&logoColor=white" alt="Java">
   <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F.svg?logo=spring&logoColor=white" alt="Spring Boot">
   <img src="https://img.shields.io/badge/MariaDB-003545?logo=mariadb&logoColor=white" alt="MariaDB">
   <img src="https://img.shields.io/badge/Redis-DC382D.svg?logo=redis&logoColor=white" alt="Redis">
   <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" alt="Docker">
   <img src="https://img.shields.io/github/stars/LeapYa/awesome-poetize-open?style=social" alt="GitHub Stars">
  </p>
</p>

## 📑 目录

- [项目简介](#-项目简介)
- [快速开始](#-快速开始)
- [部署文档](#-部署文档)
- [智能体/OpenClaw 博客自动化](#-智能体openclaw-博客自动化)
- [贡献与许可](#-贡献与许可)
- [开发指南](#-开发指南)
- [排障指南](#-排障指南)
- [技术栈](#️-技术栈)
- [联系方式](#-联系方式)
- [版权说明](#-版权说明)

## 📖 项目简介

本项目**awesome-poetize-open**是基于开源项目 [POETIZE最美博客](https://gitee.com/littledokey/poetize) 功能扩展和定制化开发，改造历时一年，这是一个集内容创作、社交互动与技术优化于一体的现代化博客系统。本项目主要用于自用，旨在为不愿支付高昂闭源版费用的开发者提供一个更现代、更好看、更轻量的开源替代方案，可能有些地方与商业化的最美博客有点差异，我当初也是被它的颜值吸引，想着自己也改一个自己满意的分享出来，我每个月都会进行不定时更新，如果你有什么好的想法或者功能建议，欢迎提issue或pr。

<p align="center">
  <img src="poetize_picture/首页.png" alt="首页" width="100%">
</p>

<p align="center">博客首页 - 展示个人创作与生活点滴</p>

<p align="center">
  <img src="poetize_picture/首页1.jpg" alt="文章展示" width="49%">
  <img src="poetize_picture/聊天室.png" alt="社交功能" width="49%">
</p>

<p align="center">左：内容布局展示 | 右：社交功能体验</p>

#### **本分支相较于开源版最美博客新增/优化功能：**

1. ✅ 一键部署脚本 —— 一行命令自动完成环境配置、HTTPS配置和服务启动
1. ✅ 专业SEO优化与预渲染 —— 预渲染静态HTML、结构化数据、多语言hreflang、社交分享元数据、Sitemap与搜索引擎推送，全方位提升搜索引擎收录
1. ✅ 多邮箱服务支持 —— 可配置多邮箱，提升邮件送达率
1. ✅ 第三方登录集成 —— 支持GitHub、Google、Gitee、QQ、微博、LinuxDo、Microsoft等16个平台，并支持自定义OIDC/OAuth2接入（Keycloak、Casdoor等自建SSO）
1. ✅ 行为验证码功能 —— 集成点选、滑动验证码，防止恶意注册
1. ✅ 看板娘优化 —— Live2D看板娘可自定义、支持AI互动
1. ✅ 导航栏优化 —— 支持自定义导航栏，可以自定义隐藏任何导航栏项，布局更美观
1. ✅ 评论体验优化 —— 评论内容自动保存，未登录也不丢失
1. ✅ 增加兰空图床、简单图床的存储支持 —— 支持多种图片上传和存储方式
1. ✅ AI翻译 —— 支持中英互译，可用本地或API模型
1. ✅ 页脚优化 —— 页脚信息更丰富、可自定义
1. ✅ 图片压缩和转换WebP格式 —— 自动压缩图片，提升网站加载速度
1. ✅ 智能摘要 —— 自动生成文章摘要，提升阅读体验
1. ✅ 智能体/OpenClaw 博客自动化 —— 支持通过仓库内置的 Agent skill 使用站点 API 完成文章发布、更新、隐藏、主题切换、SEO运维与博客运营自动化（要求 `v4.0.0` 及以上版本）
1. ✅ 暗色模式优化、定时暗色模式 —— 支持夜间自动切换暗色主题，优化暗色模式
1. ✅ 灰色模式 —— 支持全站灰色纪念模式
1. ✅ 自定义错误页面 —— 提供友好的404、403等错误页面
1. ✅ Nginx 层静态安全规则 —— 拦截空 UA、PHP/WordPress 扫描器、敏感文件及管理后台探测，在网关层直接断开恶意请求
1. ✅ 字体文件CDN化 —— 支持字体文件外部化存储与动态加载，可配置单一/分块字体模式，自定义Unicode范围，大幅减少网站带宽占用
1. ✅ 将MD5密码哈希升级为BCrypt算法 —— 修复密码安全漏洞
1. ✅ 评论区重构、优化其楼层计算算法优化 —— 对评论区进行重构，引入懒加载机制以提升页面加载速度，并使用深度优先遍历算法优化评论楼层计算逻辑，提高渲染性能
1. ✅ 实现token签名算法HMAC-SHA256认证 —— 完全替换简单UUID token，新增防伪造、防篡改、防重放攻击能力
1. ✅ 彻底重构 IM 系统 —— 移除 t-io 依赖，迁移至原生 Spring WebSocket，降低架构复杂度和维护成本，配合Java 25 虚拟线程，性能也能媲美 t-io
1. ✅ 支持账号多端登录 —— 实现多设备同时在线登录，并加强了账号安全，改密码后强制下线所有设备
1. ✅ 核心技术栈升级 —— 主站前端升级到 Vue3.5，后端升级到 Java 25 LTS + Spring Boot 4.1.0，全面启用虚拟线程，并使用 Redis缓存，性能与响应速度显著提升
1. ✅ 插件系统 —— 支持通过 .zip 插件包扩展前端展示和后端逻辑，无需修改核心代码，详见 [插件开发指南](docs/插件开发指南.md)

> 更多功能，就不一一列举了...

## 🚀 快速开始

```bash
# 你只需要输入域名即可
bash <(curl -sL install.leapya.com)
```

脚本将自动完成所有配置，包括代理配置、Docker安装、数据库初始化和HTTPS配置。为了避免建站初期踩坑，并解锁 **SEO优化**、**本地AI翻译**、**智能体/OpenClaw 博客自动化** 等高级功能，**强烈建议新手继续阅读下方的 [部署文档](#-部署文档)**。

## 📋 部署文档

### 1.准备服务器、域名、域名解析

#### 1.1 准备服务器

选择可靠的云服务商即可，根据价格和需求自行决定，常用平台：

- [阿里云](https://www.aliyun.com/)、[腾讯云](https://www.tencent.com/)、[华为云](https://www.huaweicloud.com/)、[UCloud](https://www.ucloud.cn/)、[雨云](https://www.rainyun.com/) - 国内主流
- [AWS](https://aws.amazon.com/)、[DigitalOcean](https://www.digitalocean.com/)、[Linode](https://www.linode.com/)、[Heroku](https://www.heroku.com/)、[Vultr](https://www.vultr.com/) - 海外主流

**配置要求：**

- **操作系统**：Ubuntu 20.04+、Debian 11+ 或 CentOS 7/8+（[更多系统测试结果](#14-系统兼容性测试结果)）
- **CPU/内存**：2核2GB起步（1核1GB可运行但较卡，2核2GB足够了）
- **硬盘空间**：15GB+
- **带宽**：1Mbps 起步（套 CDN 后源站只需承担未命中回源流量，1Mbps 足够；未使用 CDN 时建议 5Mbps+，1Mbps≈128KB/s，一个普通页面至少 600KB）

> **免费 CDN 方案（可选，进一步降低源站带宽压力）：**
> - **腾讯云 EdgeOne**：免费版适合**已备案**域名；未备案域名会绕行美国节点，国内访问龟速，慎用
> - **Cloudflare**：免费版全球可用，但国内访问默认线路较差，需配置**优选 IP**才能拿到合理速度

**地域与线路选择：**

| 地域 | 优点 | 注意事项 |
|------|------|---------|
| 国内 | 访问速度最快，国内搜索引擎收录更友好 | 需要ICP备案（约3-7个工作日） |
| 香港/海外 | 免备案，即买即用 | 国内访问可能稍慢 |

> **避坑提醒：**
> - **香港/海外服务器**：优先选择**CN2线路**等有回国优化的线路，避免无线路优化，否则国内访问会很慢
> - **国内服务器**：大部分云服务商的国内服务器没有海外出口线路或者封海外IP，海外用户访问会很慢甚至无法访问，一定要问清楚再购买，优先选择**BGP多线**，避免单线路（如仅电信/联通），否则国内跨运营商访问会很慢
> - 如果需要备案，云服务器至少需要购买3个月以上的使用时长
> - 记得在安全组/防火墙中 **开放80和443端口**
> - 内存较低时部署脚本会自动开启swap交换空间进行优化

#### 1.2 购买域名

在任意域名注册商购买域名即可，常用平台：

- [阿里云万网](https://wanwang.aliyun.com/)、[腾讯云DNSPod](https://dnspod.cloud.tencent.com/)、[华为云](https://www.huaweicloud.com/product/domain.html) - 国内主流
- [Cloudflare](https://www.cloudflare.com/products/registrar/)、[Namesilo](https://www.namesilo.com/)、[Namecheap](https://www.namecheap.com/) - 海外注册商

> 如果使用国内服务器，域名需要完成ICP备案才能正常访问，如果需要备案，域名不要选择 **.tk**、**.ml**、**.ga**、**.cf**、**.org** 等后缀，这些后缀的域名无法备案，选择 **.com**、**.net**、**.top**、**.xyz**、**.cn**、**.online** 等后缀的域名，备案流程可参考[ICP备案](https://help.aliyun.com/zh/icp-filing/basic-icp-service/user-guide/icp-filing-application-overview)，其他云服务商也类似，都是向各自的购买云服务器的云服务商申请备案。

#### 1.3 配置域名解析

购买域名后，需要将域名解析到服务器IP：

1. 登录域名注册商的DNS管理控制台
2. 添加以下解析记录：

| 记录类型 | 主机记录 | 记录值 | TTL |
|---------|---------|--------|-----|
| A | @ | 你的服务器IP | 600 |
| A | www | 你的服务器IP | 600 |

> **说明**：`@` 表示根域名（如 `example.com`），`www` 表示带www前缀（如 `www.example.com`）。解析生效通常需要几分钟到几小时不等。

#### 1.4 系统兼容性测试结果

| 操作系统类型          | CPU  | 内存 | 存储 | 测试结果  |
| --------------------- | ---- | ---- | ---- | --------- |
| Ubuntu 20.04+ x64     | 1核+ | 1G+  | 30GB | ✅ 推荐   |
| Debian 11+ x64        | 1核+ | 1G+  | 30GB | ✅ 推荐   |
| CentOS 7/8+ x64       | 1核+ | 1G+  | 30GB | ✅ 推荐   |
| Windows Server/桌面版 | 2核+ | 2G+  | 30GB | ✅ 使用wsl   |
| Docker 容器（挂载 Socket） | 2核+ | 2G+  | 30GB | ✅ 支持   |
| Docker 容器（--privileged） | 2核+ | 2G+  | 30GB | ⚠️ 可尝试 |

> **其他支持的系统**：RHEL、Rocky Linux、AlmaLinux、Fedora、Amazon Linux、阿里云/腾讯云 Linux、麒麟、统信UOS、Deepin、openEuler、Alpine、Arch Linux、openSUSE等主流Linux发行版均已测试通过。

---

> 👇 已有服务器/域名/解析？跳过上面的准备工作，直接看本节安装命令

### 2.运行一键安装脚本

先通过 SSH 登录到你的服务器，在服务器终端（Shell/命令行，也就是黑框框）里运行下面的命令。

以下方式任选其一即可，如果不想让安装脚本直接操作宿主机环境，支持在挂载了 Docker Socket 的容器内运行，详见[容器内隔离部署](#在-docker-容器内部署防污染-vps-环境)

```bash
# 方式一：交互模式
bash <(curl -sL install.leapya.com)

# 方式二：非交互模式(替换成自己的域名，每个域名使用-d隔开)
bash <(curl -sL install.leapya.com) install -d 域名.com -d www.域名.com

# 方式三：克隆本仓库部署（交互模式）
git clone https://github.com/LeapYa/awesome-poetize-open.git && cd awesome-poetize-open && sudo chmod +x poetize && sudo ./poetize install

# 方式四：克隆本仓库部署（非交互模式）
git clone https://github.com/LeapYa/awesome-poetize-open.git && cd awesome-poetize-open && sudo chmod +x poetize && sudo ./poetize install -d 域名.com -d www.域名.com
```

> 部署脚本已经做好了错误处理和重试机制，如果仍然部署失败，请查看[常见问题](#6常见问题)

#### 其他部署方式（beta）

| 部署方式 | 适用场景 | 文档 |
|---------|---------|------|
| Docker 一键脚本 | 有自己的服务器，最省心（上面的方式）| 本页 |
| 裸机部署（不用 Docker） | 有服务器但不想用 Docker，手动安装 | [裸机部署教程](docs/裸机部署教程.md) |
| 宝塔面板部署（不用 Docker） | 已安装宝塔面板，使用 Web 界面部署 | [宝塔部署教程](docs/宝塔部署教程.md) |
| 免费部署（零成本） | 无服务器，利用 Render + Vercel 等免费平台 | [免费部署教程](docs/免费部署教程.md) |

> **使用 1Panel / 宝塔等面板做外层反向代理？** 本项目的 `poetize-nginx` 容器已内置 IP 头伪造防护（公网直连携带 `X-Real-IP` 会被拒绝），使用 `poetize install` 部署时自动生效。如果你在外层再套了一层反代（如 1Panel OpenResty），只要反代到 `poetize-nginx` 走内网通信就没问题——1Panel 等通用反代默认不会过滤伪造 IP 头，但安全层由 `poetize-nginx` 负责。不要绕过 `poetize-nginx` 直接暴露 Java 服务，否则会失去防护。更多配置细节见[高级功能 - 外部反向代理注意事项](#使用外部反向代理1panel--宝塔--自建-nginx的安全注意事项)。

### 3.访问方式

部署完成后，可通过以下地址访问系统功能：

* 主站：`http(s)://域名/`
* 聊天室：`http(s)://域名/im`
* 管理后台：`http(s)://域名/admin`

**默认管理员凭证**：

- 用户名：`Sara`
- 密码：`aaa`

> 首次登录后，进入后台管理，点击欢迎页的"开启配置向导"按钮，按步骤完成所有基础配置（站点信息、邮件服务、个人资料修改、SEO配置等）。

### 4.可选配置

#### （1）SEO优化配置

SEO（搜索引擎优化）可以让你的博客更容易被 Google、百度等搜索引擎收录，让更多人通过搜索找到你的文章。

本项目内置了完整的 SEO 功能，包括智能摘要生成、站点地图、搜索引擎推送等。如果你希望网站被更多人发现，建议阅读 [SEO优化指南](docs/SEO优化指南.md)。

#### （2）更换字体

  <details>
  <summary><b>默认字体说明</b>（点击展开）</summary>

  本项目默认使用「**萌趣体**」字体（来源：[字体视界](https://www.17font.com/)），该字体风格圆润可爱，非常适合个人博客的温馨氛围。

  > **注意**：萌趣体是**单字重字体**，不支持粗体变体。这意味着 Markdown 中的 `**粗体**` 语法虽然会被正确解析，但视觉上加粗效果不明显。如需明显的粗体效果，建议更换为支持多字重的字体。

  **推荐字体**

  如需更换字体，以下是一些支持**多字重**的免费商用字体推荐：

  | 字体名称 | 风格特点 | 字重数量 | 下载地址 |
  | :--- | :--- | :--- | :--- |
  | HarmonyOS Sans | 圆润现代 | 6种 | [官网下载](https://developer.huawei.com/consumer/cn/design/resource/) |
  | MiSans | 圆润友好 | 6种 | [官网下载](https://hyperos.mi.com/font) |
  | OPlusSans | 圆角柔和 | 5种 | [GitHub](https://github.com/nicemicrosun/OPPO_Sans) |
  | 思源黑体 | 专业现代 | 7种 | [GitHub](https://github.com/adobe-fonts/source-han-sans) |
  | 思源宋体 | 优雅衬线 | 7种 | [GitHub](https://github.com/adobe-fonts/source-han-serif) |

  </details>


  进入后台管理 → 配置管理，即可直接上传并切换字体，无需手动操作文件。

#### （3）OAuth代理

若需支持国外第三方登录平台（GitHub、Google等），请配置海外代理服务器，详见[OAuth代理配置说明文档](docs/OAuth代理配置说明.md)。

#### （4）Ollama本地翻译模型

如需启用本地AI翻译功能，编辑 `docker-compose.yml` 找到"Ollama翻译模型服务"部分取消注释即可。默认使用 `qwen3:0.6b` 轻量级模型。更多模型选择和配置详见 [Ollama官方模型库](https://ollama.com/library)。

#### （5）使用外部数据库或Redis

如果你已有 MariaDB/MySQL 或 Redis 服务，可在部署时直接使用，无需再启动内置容器：

```bash
# 使用外部数据库
bash <(curl -sL install.leapya.com) -d 域名.com --db-host 数据库地址 --db-pwd 密码

# 使用外部Redis
bash <(curl -sL install.leapya.com) -d 域名.com --redis-host Redis地址 --redis-pwd 密码

# 完整参数（可按需省略,也可以使用 -h 查看完整参数）
--db-host HOST       # 数据库主机地址
--db-port PORT       # 端口（默认3306）
--db-name NAME       # 数据库名（默认poetize）
--db-user USER       # 用户名（默认poetize）
--db-pwd PASSWORD    # 密码
--redis-host HOST    # Redis主机地址
--redis-port PORT    # 端口（默认6379）
--redis-pwd PASSWORD # 密码
--redis-db NUMBER    # 数据库编号（默认0）
```

### 5.常用命令

本项目统一使用 `poetize` 管理脚本，执行 `poetize -h` 或在仓库根目录执行 `./poetize -h` 查看详细用法。

#### 升级项目
```bash
# 升级项目（全量更新，不管项目有没有更新）
poetize -update
```

#### 迁移项目
```bash
# 迁移博客
poetize -qy
```

<details>

<summary>完整选项</summary>

**管理子命令**

| 命令 | 说明 |
|------|------|
| `-install [install子命令]`、`install [install子命令]` | 安装项目 |
| `-uninstall`、`uninstall` | 卸载项目 |
| `-uninstall-all`、`uninstall-all` | 完全卸载所有项目（包含二次安装的项目） |
| `-update [版本]`、`update [版本]` | 更新系统（如 `-update v2.1.1`，默认使用最新稳定版本） |
| `-update-poetize-command`、`update-poetize-command` | 更新 poetize 命令自身 |
| `-status`、`status` | 查看所有服务状态 |
| `-start`、`-up` | 启动所有服务 |
| `-stop`、`-down` | 停止所有服务 |
| `-restart` | 重启所有服务 |
| `-qianyi`、`-migrate`、`-qy [版本]` | 执行博客迁移（如 `-qy v2.1.1`，默认使用最新稳定版本） |
| `-logs [服务名]` | 查看服务日志 |
| `-logs-follow [服务名]` | 实时跟踪服务日志 |
| `-oauth-proxy`、`oauth-proxy` | OAuth 代理配置向导（三步引导配置 GitHub/Google 等海外登录代理） |
| `-enable-https`、`enable-https` | 手动启用 HTTPS（自动检测前置条件并执行 enable-https.sh） |
| `-info` | 显示系统和项目信息 |
| `-version`、`-v` | 显示版本信息 |
| `-help`、`-h` | 显示帮助信息 |

**安装/部署选项（`install` 子命令）**

| 选项 | 说明 |
|------|------|
| `-d`、`--domain DOMAIN` | 设置域名（支持 `domain:port` 格式，可多次使用添加多个域名） |
| `-e`、`--email EMAIL` | 设置管理员邮箱（默认：`example@qq.com`） |
| `-h`、`--help` | 显示帮助信息 |
| `--config FILE` | 从文件加载配置 |
| `--save-config [FILE]` | 保存配置到文件（默认为 `.poetize-config`） |
| `--enable-swap` | 启用 swap 空间（默认启用） |
| `--swap-size SIZE` | 设置 swap 大小（默认 `1G`） |
| `-b`、`--background` | 在后台运行脚本，输出重定向到日志文件 |
| `--log-file FILE` | 指定日志文件（默认为 `deploy.log`） |
| `--enable-docker-cache` | 启用 Docker 构建缓存（默认禁用以节省空间） |
| `--httpport PORT` | 设置 HTTP 端口（使用自定义端口时会自动禁用 HTTPS） |
| `--disable-https` | 禁用 HTTPS（默认启用） |
| `--skip-dns-check` | 跳过 DNS 校验（用于内网部署或 CDN 配置等特殊场景） |
| `--db-host HOST` | 设置外部 MariaDB 主机地址 |
| `--db-port PORT` | 设置外部 MariaDB 端口（默认 `3306`） |
| `--db-name NAME` | 设置外部数据库名称（默认 `poetize`） |
| `--db-user USER` | 设置外部数据库用户名（默认 `poetize`） |
| `--db-pwd PASSWORD` | 设置外部数据库密码 |
| `--redis-host HOST` | 设置外部 Redis 主机地址 |
| `--redis-port PORT` | 设置外部 Redis 端口（默认 `6379`） |
| `--redis-pwd PASSWORD` | 设置外部 Redis 密码 |
| `--redis-db NUMBER` | 设置 Redis 数据库编号（默认 `0`） |
| `--keep-git` | 保留 Git 仓库（可选，支持 git 更新） |
| `--no-git`、`--remove-git` | 删除 Git 仓库（默认，节省磁盘空间） |
| `--version VERSION` | 指定部署版本（如 `v2.1.1`），默认使用最新稳定版本 |
| `--list-versions` | 列出所有可用版本 |
| `--latest`、`--use-main`、`--use-latest` | 使用 main 分支而非稳定版本标签 |

</details>

### 6.常见问题

#### 项目部署失败

项目在部署时可能因任何原因（网络波动、资源不足等）导致部署失败，在1核1G服务器较常见，如果部署失败，可执行以下命令清理并重新部署：

```bash
sudo docker system prune -af && cd .. && sudo rm -rf awesome-poetize-open && bash <(curl -sL install.leapya.com)
```

更多详见[排障指南](#-排障指南)

### 7.高级功能

#### 国内环境部署

`poetize install` 已内置国内镜像源加速。~~若网络受限，可从Release下载离线资源包，包含Docker安装包和所有镜像文件。~~
#### 在 Docker 容器内部署（防污染 VPS 环境）

如果你希望避免安装脚本直接操作宿主机环境，可以在 VPS 上开一个 Debian 容器，进入容器内执行部署脚本。

**推荐方式：挂载宿主机 Docker Socket**

容器内连接宿主机的 Docker，部署的容器和服务实际仍运行在宿主机上，并不会污染宿主机的 apt/yum 等环境。

```bash
# 1. 在 VPS 上启动一个 Debian 容器，并挂载 Docker Socket
#    （需要先安装好宿主机 Docker）
docker run -it \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /home/poetize:/home/poetize \
  --network host \
  debian:bookworm bash

# 2. 在容器内安装必要工具
apt-get update && apt-get install -y curl docker.io docker-compose-plugin

# 3. 运行安装脚本即可
bash <(curl -sL install.leapya.com)
```

> **说明**：
> - 全程只有 `curl`、`docker.io`、`docker-compose-plugin` 安装在容器内，不会修改宿主机环境
> - `--network host` 是为了让容器能直接绑定宿主机的 80/443 等端口
> - `-v /home/poetize:/home/poetize` 将项目文件持久化到宿主机，保存在容器外部，容器销毁不丢失

**备选方式：Docker-in-Docker（--privileged）**

如果你希望容器内自带独立的 dockerd，可以使用 `--privileged`。但这种方式效果相对不稳定，且安全风险较大，只建议在特定场景下使用。

**各种场景能力对比**

| 场景 | 是否可行 | 说明 |
|------|---------|------|
| 直接在 VPS 上运行 | ✅ 最稳定 | 标准推荐方式 |
| 容器＋挂载 Socket | ✅ 推荐 | 防污染宿主机环境的最佳方式 |
| 容器＋--privileged（DinD） | ⚠️ 可尝试 | 不如挂 Socket 稳定，有安全风险 |
| 普通容器（无 Socket，无特权） | ❌ 不可行 | Docker 守护进程无法启动 |

#### 使用外部反向代理（1Panel / 宝塔 / 自建 Nginx）的安全注意事项

本项目的 `poetize-nginx` 容器内置了 IP 头伪造防护：公网客户端直连时携带 `X-Real-IP` 会被拒绝（返回 400），只有来自内网/容器网络的反向代理传入的 IP 头才会被信任。使用 `poetize install` 脚本部署时该机制自动生效。

如果你在 `poetize-nginx` 外层套了反代（1Panel OpenResty、宝塔 Nginx 等），注意：

- ✅ **外层反代与 `poetize-nginx` 走内网** — 正常工作，无需额外配置。1Panel 等通用反代默认不会主动过滤伪造 IP 头，但没关系，安全过滤由 `poetize-nginx` 在应用层完成。
- ⚠️ **外层反代直接暴露公网** — 需在外层反代配置 `set_real_ip_from`（内网段）+ `real_ip_recursive on`，确保 `X-Forwarded-For` 链路干净：
  ```nginx
  set_real_ip_from 10.0.0.0/8;
  set_real_ip_from 172.16.0.0/12;
  set_real_ip_from 192.168.0.0/16;
  real_ip_header X-Forwarded-For;
  real_ip_recursive on;
  ```
- ❌ **不要绕过 `poetize-nginx` 直接暴露 Java 服务** — 会失去防伪造保护，访问统计地域数据可能被污染。

## 🤖 智能体/OpenClaw 博客自动化

项目在 `v4.0.0` 中引入了面向多智能体（OpenClaw、Hermes、Trae、Qoder、腾讯等）的博客自动化能力。你可以通过仓库内置的 skill，让智能体直接调用站点 API 来完成文章发布、异步更新、隐藏文章、分类标签安全维护、主题切换、SEO状态查询，以及部分受控 SEO 运维动作。

**注意：该能力当前要求 `v4.0.0` 及以上版本，更早版本不要直接接入。**

### 适合做什么

- 根据主题草拟并发布博客文章
- 更新已有文章，或将文章切换为隐藏状态
- 查询文章、分类、标签、主题和 SEO 状态
- 结合各类智能体的 agent 工作流做博客运营自动化

### 接入前准备

1. 使用 `v4.0.0` 或更高版本
2. 进入后台管理的 `API 配置` 页面，开启 API 并生成 API 密钥
3. 如果开启了 API IP 白名单，需要把智能体服务器出口 IP 或 CIDR 加进去
4. 将 `POETIZE_BASE_URL` 设置为站点公网根地址，例如 `https://your-blog.example.com`
5. 不要把 `/api` 写进 `POETIZE_BASE_URL`，skill 会自动拼接成 `${POETIZE_BASE_URL}/api/...`

### 安装方式一：让智能体（以 OpenClaw 为例）按提示词帮你安装（最推荐）

如果你希望直接通过对话让智能体（如 OpenClaw、Hermes、Trae 等）帮你处理安装，可以把下面这段提示词发给智能体。

```text
请根据 https://skillhub.cn/install/skillhub.md，安装 awesome-poetize-open-blog-automation。

如果 SkillHub 中搜索不到或安装失败，请改为克隆项目 `https://github.com/LeapYa/awesome-poetize-open.git`，然后按手动安装方式把 `skills/poetize-blog-automation` 复制到智能体对应的 `skills/` 目录中完成安装。

安装目标默认使用智能体的 `skills/` 目录，配置默认写入智能体对应的配置文件（具体路径因框架而异，请参考对应框架的文档）。

安装完成后，请务必主动向我索要本技能所需的两项配置项，并附带以下获取指引：
1. `POETIZE_BASE_URL`：仅填站点根地址（提醒我不要带 `/api` 前缀，例如 `https://your-blog.example.com`）。
2. `POETIZE_API_KEY`：API 密钥（提醒我可以在“后台管理 -> 导航与接口 -> API 配置”界面中获取）。
收到完整配置后，请帮我写入配置文件。
```

### 安装方式二：手动通过 SkillHub CLI 安装

在智能体的工作目录下执行：

```bash
skillhub install awesome-poetize-open-blog-automation
```

安装完成后，根据你使用的智能体框架，在对应配置文件中（如 OpenClaw 的 `~/.openclaw/openclaw.json`）加入：

```json
{
  "skills": {
    "entries": {
      "poetize-blog-automation": {
        "enabled": true,
        "apiKey": "你的POETIZE_API_KEY",
        "env": {
          "POETIZE_BASE_URL": "https://你的域名"
        }
      }
    }
  }
}
```

说明：

- 对外安装名是 `awesome-poetize-open-blog-automation`
- 安装后内部使用的 `skillKey` 仍然是 `poetize-blog-automation`
- `POETIZE_BASE_URL` 只填站点根地址，不要带 `/api/api`

### 安装方式三：手动安装

仓库已经内置 skill，目录如下：

```text
skills/poetize-blog-automation/
```

手动安装时，把 `poetize-blog-automation` 整个目录复制到你使用的智能体框架所指定的 `skills/` 目录下（具体路径因框架而异，请参考对应框架的文档）。

然后在智能体的配置文件中加入和上面相同的配置：

```json
{
  "skills": {
    "entries": {
      "poetize-blog-automation": {
        "enabled": true,
        "apiKey": "你的POETIZE_API_KEY",
        "env": {
          "POETIZE_BASE_URL": "https://你的域名"
        }
      }
    }
  }
}
```

手动安装的好处是：

- 不依赖公开注册表
- 适合私有环境或内网环境
- 你可以直接跟随仓库更新 skill 文件

### 发布名与内部名称

- 对外发布安装名：`awesome-poetize-open-blog-automation`
- 内部文件夹名 / `skillKey`：`poetize-blog-automation`
- 这样做是为了让公开安装名和项目名保持一致，同时保留内部配置键的稳定性

### 后续如何更新 skill

为了避免后续升级困难，建议从一开始就保持下面这几个值稳定不变：

- `slug`：`awesome-poetize-open-blog-automation`
- 内部文件夹名：`poetize-blog-automation`
- `skillKey`：`poetize-blog-automation`

后续只更新版本号和 skill 内容，不要随意改上面这几个标识。

**推荐直接把下面这段升级提示词发送给智能体让它帮你更新（以 OpenClaw 为例）：**

```text
请帮我升级技能 `awesome-poetize-open-blog-automation`。

请直接执行 `skillhub update awesome-poetize-open-blog-automation`。
如果因为网络受限或在 SkillHub 找不到对应版本，请回退到克隆仓库的手动方式覆盖更新，目录在智能体对应的 `skills/poetize-blog-automation/` 目录下。

升级完成后，请主动向我确认是否需要更新以前的配置。如果要更新，请向我索要 `POETIZE_BASE_URL`（不带`/api`）与 `POETIZE_API_KEY`（在后台管理->导航与接口->API 配置中获取），并写入智能体对应的配置文件（如 `~/.openclaw/openclaw.json`）。
```

**或者你也可以选择自己手动在命令行执行更新：**

```bash
skillhub update awesome-poetize-open-blog-automation
```

**如果本地 skill 被手工改过，导致无法匹配已发布版本，可以强制覆盖：**

```bash
skillhub update awesome-poetize-open-blog-automation --force
```

#### 手动安装的用户如何更新

如果用户是手动安装的，后续更新方式也很简单：

1. 获取仓库最新版本
2. 用新的 `skills/poetize-blog-automation/` 覆盖旧的 `~/.openclaw/workspace/skills/poetize-blog-automation/`
3. 保留原来的 `~/.openclaw/openclaw.json` 配置

### 参考文档

- [Skill 规范与工作流](skills/poetize-blog-automation/SKILL.md)
- [内容策略手册](skills/poetize-blog-automation/references/strategy-playbook.md)
- [决策矩阵](skills/poetize-blog-automation/references/decision-matrix.md)
- [创意工作流](skills/poetize-blog-automation/references/creativity-workflow.md)

## 🤝 贡献与许可

* 原作者：Sara (POETIZE最美博客)
* Fork版本开发：LeapYa
* 开源协议：遵循原项目AGPL协议

### 贡献者

感谢所有为本项目做出贡献的人！

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<table>
  <tr>
    <td align="center">
      <a href="https://github.com/mikutea">
        <img src="https://github.com/mikutea.png" width="100px;" alt="mikutea"/>
        <br />
        <sub><b>mikutea</b></sub>
      </a>
      <br />
      <a href="https://github.com/LeapYa/awesome-poetize-open/issues?q=author%3Amikutea+label%3Aenhancement" title="Ideas & suggestions">🤔</a>
      <a href="https://github.com/LeapYa/awesome-poetize-open/issues?q=author%3Amikutea+label%3Abug" title="Bug reports">🐛</a>
    </td>
  </tr>
</table>
<!-- ALL-CONTRIBUTORS-LIST:END -->

## 💻 开发指南

详细的开发环境配置、项目结构说明和各模块开发指南，请参阅 **[开发指南文档](docs/开发指南.md)**。

包含内容：
- 环境要求（Node.js、JDK、Maven、Docker）
- 项目目录结构
- 前端开发（poetize-web、poetize-admin）
- Java 后端开发
- 数据库配置（MariaDB/MySQL）
- 从 MariaDB 切换到 MySQL 的步骤

其他文档：
- **[SEO优化指南](docs/SEO优化指南.md)** - 发布策略、收录技巧、技术配置
- **[插件开发指南](docs/插件开发指南.md)** - 插件包格式、前后端 SDK、钩子 API、安全策略
- **[数据库设计文档](docs/数据库设计.md)** - 表结构、字段说明、ER图
- **[架构设计文档](docs/架构设计.md)** - 系统架构、技术栈、部署架构

### 快速开发启动

**环境准备**：JDK 25 LTS、Node.js 14+、Maven 3.9+、MariaDB + Redis（详见 [开发指南](docs/开发指南.md#数据库环境准备)）

```bash
# 1. 启动后端
cd poetize-server && mvn spring-boot:run

# 2. 启动前端（另开终端）
cd poetize-web && npm install && npm run dev

# 3. 启动后台管理系统前端（另开终端）
cd poetize-admin && npm install && npm run dev
```

访问地址：
- 前台：`http://localhost:5173`
- 后台：`http://localhost:5174/admin`


## 🔧 排障指南

遇到问题？请参阅 **[排障指南文档](docs/排障指南.md)**。

包含常见问题解决方案：
- 前端问题（npm 安装失败、API 请求失败、WebSocket 连接失败）
- Java 后端问题（Maven 依赖、Spring Boot 启动、数据库连接）
- Docker 环境问题（容器启动、健康检查、日志查看）
- 网络与访问问题（HTTPS 证书、静态资源 404）
- 性能调试命令

## 🛠️ 技术栈

* **前端** - Vue3（前台+聊天室）、Vue2（后台管理）、Element Plus/Element UI、WebSocket、Live2D
* **后端** - Spring Boot 4.1.0、Java 25 LTS
* **数据库** - MariaDB 11、Redis 7
* **部署** - Docker、Docker Compose、OpenResty（Nginx）、Shell 脚本

## 📧 联系方式

* **邮箱** - enable_lazy@qq.com 或 hi@leapya.com
* **问题反馈** - [GitHub Issues](https://github.com/LeapYa/awesome-poetize-open/issues)

所有项目贡献者信息请参阅[贡献者](#-贡献与许可)部分。

## 📜 版权说明

本项目遵循GNU Affero General Public License v3.0 (AGPL-3.0)开源许可协议，详情请参阅[LICENSE](LICENSE)文件。
