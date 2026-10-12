# GitHub Web App

一个 GitHub App（机器人 / 集成）客户端，覆盖网页版的全部核心操作，并支持 **GitHub 账号 OAuth 登录**。

功能覆盖：仓库浏览/创建/收藏/关注、文件浏览/编辑/上传/删除、Issue（创建/评论/关闭/编辑）、Pull Request（创建/评审/批准/合并）、提交历史与 diff、分支管理、Releases、Actions（工作流/运行/日志）、通知、Gists、搜索、Webhook 事件日志、机器人自动回复，以及一个"API 调试台"可调用任意 GitHub REST API。

## 架构

```
浏览器 (SPA)  ──►  本服务 (Node/Express)
                    ├─ /auth/*        OAuth 账号登录
                    ├─ /api/gh/*      通用代理 → api.github.com（以当前身份）
                    ├─ /webhook       接收 GitHub 事件 → 事件日志 + 机器人回复
                    └─ 应用 JWT / 安装令牌（机器人身份）
```

任何网页版操作在后台都是 GitHub REST API 的子集，因此通用代理 + 前端封装即可覆盖"全部操作"。

## 一、注册 GitHub App（一次性）

方式 A（最简单，Manifest 一键创建）：

1. 把本项目的 `app.yml` 放到任意可公网访问的 URL（或使用 gh CLI）
2. 打开 <https://github.com/settings/apps/new?url=你的app.yml地址>
3. 按提示完成创建，获得 **App ID、Client ID、Client secret、Webhook secret、私钥**

方式 B（手动创建）：GitHub → Settings → Developer settings → GitHub Apps → New GitHub App，按 `app.yml` 里的权限与事件勾选，填回调地址 `<BASE_URL>/auth/callback`、Webhook 地址 `<BASE_URL>/webhook`。

创建后：在应用页点 **Install App**，安装到你的账号或组织。

## 二、配置并启动

```bash
cd gh-web-app
npm install
cp .env.example .env      # 编辑 .env 填入注册信息
npm start                 # 访问 http://localhost:3000
```

`.env` 关键项：

| 变量 | 说明 |
|---|---|
| `BASE_URL` | 服务对外地址（回调与 Webhook 依赖它） |
| `GITHUB_APP_ID` | 应用 ID |
| `GITHUB_APP_PRIVATE_KEY_PATH` | 私钥文件路径（或直接填 `GITHUB_APP_PRIVATE_KEY`） |
| `GITHUB_APP_CLIENT_ID` / `CLIENT_SECRET` | OAuth 登录用 |
| `GITHUB_APP_WEBHOOK_SECRET` | Webhook 签名校验（本地调试可留空） |
| `BOT_WELCOME` | `true` 时新 Issue/PR 自动发欢迎评论 |

> 线上部署：回调/Webhook 必须是公网可达地址（服务器或内网穿透如 ngrok）。`SESSION_SECRET` 生产环境请用 `openssl rand -hex 32` 生成固定值。

## 三、使用

1. 打开首页 → **使用 GitHub 账号登录**（OAuth，申请 repo/workflow/notifications 等权限）
2. 也可在登录页用 **应用身份** 激活某个已安装账号，以机器人身份操作
3. 左侧导航进入仓库、Issue、PR、通知、Gists、搜索、Webhook 事件、API 调试台

## 开发说明

- 后端：`server/`（Express，无数据库，会话与事件日志存内存；生产可换 Redis/DB）
- 前端：`public/`（原生 JS SPA，无构建步骤）
- 代理约定：前端 `/api/gh/<github-api-path>` 即对应 `api.github.com/<path>`，支持全部方法与 query；可用 `_accept=` 覆盖 Accept 头（如渲染 README）
