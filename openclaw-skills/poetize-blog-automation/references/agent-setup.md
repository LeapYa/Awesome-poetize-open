# Agent Skills Setup Guide (OpenClaw / Hermes / Trae / Qoder / Tencent / etc.)

This guide helps you connect the Poetize blog automation skill to any AI agent framework (such as OpenClaw, Hermes Agent, Trae, Qoder, Tencent CodeBuddy/Yuanqi, etc.) that supports the standard **Agent Skills** (`SKILL.md`) specification.

Use this skill with two required runtime configurations:

1. `POETIZE_BASE_URL`
   Example: `https://your-blog.example.com`
2. `POETIZE_API_KEY`
   Value from the POETIZE admin API settings page.

The skill does not need an admin cookie or browser session; it calls the existing POETIZE API directly.
All commands use `{baseDir}` to run portably from any workspace location.

---

## 中文说明

- 本技能遵循 **Agent Skills** 规范，通过站点提供的 `/api/api/*` 接口完成文章发布和博客运营动作。
- 专为 `awesome-poetize-open` 开源分支设计。原版 POETIZE 不完全兼容，请勿直接接入。
- 运行时仅需要两个关键环境变量：站点域名 `POETIZE_BASE_URL` 和后台生成的 `POETIZE_API_KEY`。
- 在正式开始发文前，建议让智能体先跑一次只读 Smoke Test，确认接口权限正常。

---

## 1. 核心运行环境配置 (Environment Variables)

不管是哪种智能体框架，本技能都需要以下两个环境变量：

* **`POETIZE_BASE_URL`**: 博客站点的域名。例如 `https://blog.example.com`。
  * *注意*: 脚本会自动在路径后拼接 `/api/api/...`，请不要在变量值末尾手动添加 `/api`。
* **`POETIZE_API_KEY`**: 在 POETIZE 博客后台「系统设置」-「API设置」中生成的 API Key。

---

## 2. 针对不同智能体框架的接入说明

### 选项 A：OpenClaw 框架

#### 自动安装（推荐）
将以下提示词直接发送给 OpenClaw：
```text
请先检查是否已安装 ClawHub CLI。
若未安装，请根据 OpenClaw 官方 ClawHub 文档先安装 ClawHub CLI，然后尝试安装技能 `awesome-poetize-open-blog-automation`。
如果该技能尚未发布或在 ClawHub 中搜索不到，请改为将本地项目中的 `openclaw-skills/poetize-blog-automation` 复制到 OpenClaw 的 `skills/` 目录中。
```

#### 手动安装
将 `openclaw-skills/poetize-blog-automation` 复制到 OpenClaw 的 `~/.openclaw/workspace/skills/` 目录中。

#### 配置文件 (`openclaw.json`) 写入
你可以使用技能内置脚本生成配置：
```bash
python {baseDir}/scripts/poetize_cli.py config --output openclaw.poetize.local.json --api-key "your-api-key"
```
或者在 `openclaw.json` 中配置：
```json
{
  "skills": {
    "entries": {
      "poetize-blog-automation": {
        "enabled": true,
        "apiKey": "replace-with-poetize-api-key",
        "env": {
          "POETIZE_BASE_URL": "https://your-blog.example.com"
        }
      }
    }
  }
}
```

---

### 选项 B：Hermes Agent 框架

Hermes 框架会自动扫描 `~/.hermes/skills/` 目录。

#### 安装方式
1. 将 `poetize-blog-automation` 文件夹复制到 `~/.hermes/skills/` 目录下。
2. 或者在 `~/.hermes/config.yaml` 中通过 `external_dirs` 引入本仓库的 `openclaw-skills/` 目录：
   ```yaml
   skills:
     external_dirs:
       - "/path/to/awesome-poetize-open/openclaw-skills"
   ```

#### 环境变量配置
在 `~/.hermes/.env` 文件中追加：
```env
POETIZE_BASE_URL=https://your-blog.example.com
POETIZE_API_KEY=your-poetize-api-key
```

---

### 选项 C：Trae / Qoder / 腾讯 CodeBuddy / 其它 IDE 智能体

这些 IDE 智能体（如 Trae, Qoder, 腾讯 CodeBuddy）可以直接在当前项目工作区内读取本地的 `.agents/skills` 或 `openclaw-skills`。

#### 启用方式
1. 大多数 IDE 智能体在启动时会自动扫描工作区根目录、`.agents/skills/` 目录下的 `SKILL.md`。
2. 确保在 IDE 智能体的环境变量设置（或系统环境变量）中注入了 `POETIZE_BASE_URL` 和 `POETIZE_API_KEY`。
3. 直接在对话中对智能体发出指令，例如：`“帮我将这篇草稿发布到我的 Poetize 博客上”`。智能体会自动识别 `SKILL.md` 中的指令，并通过 Python 调用 `poetize_cli.py` 执行。

---

## 3. 运行前测试 (Smoke Test)

在让智能体执行写文章等修改操作前，请先让智能体运行一次 Smoke Test，确认网络和 API 密钥配置无误：

```bash
python {baseDir}/scripts/poetize_cli.py smoke-test --base-url "https://your-blog.example.com" --api-key "your-api-key"
```

如果输出返回 `{"status": "ok", ...}`，说明配置成功。

---

## 4. 本地图片上传支持

本技能支持在发布文章时自动将 Markdown 文档中的本地图片上传至博客服务器：
1. 智能体将 Markdown 草稿写入磁盘，并将相关配图保存在同级目录下（例如 `./assets/photo.png`）。
2. 在 Markdown 中引用本地图片：`![图片描述](./assets/photo.png)`。
3. 运行 `poetize_cli.py publish` 时，脚本会自动把 `./assets/photo.png` 上传至博客并自动替换为线上 URL，随后完成文章发布。
