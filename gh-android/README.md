# GitHub 客户端（Android 原生 App）

Kotlin + Jetpack Compose 编写的 **Android 原生应用**，全中文界面，覆盖 GitHub 网页版核心操作，并保留 **GitHub App 机器人/集成** 能力（OAuth 登录 + Webhook 接收 + 机器人自动回复）。

依赖同一个 Node 后端（[gh-web-app](../gh-web-app)）：登录、API 代理、Webhook、机器人全部由后端完成，App 是原生前端。

## 功能

- 🔐 GitHub 账号 OAuth 登录（内嵌 WebView，与后端共享会话 Cookie）
- 📦 仓库：列表/过滤/新建/收藏/关注
- 📁 代码：目录浏览、文件渲染查看、编辑、新建、上传、删除
- 🐛 Issues：列表/创建/编辑/评论/关闭/重开
- 🔀 Pull Requests：列表/创建/批准/请求更改/合并/评论/变更文件
- 🕘 提交历史与 diff 查看
- 🌿 分支：列表/新建/删除/保护规则查看
- 🏷️ Releases 发布
- ⚙️ Actions：工作流/运行记录/Jobs/日志
- 🔔 通知（全部/单条标记已读）
- 📝 Gists、🔍 搜索（仓库/用户/Issue/PR/代码）
- ⚡ Webhook 事件日志、👤 我的 Issues / PR（跨仓库）

## 快速开始

1. **启动后端**（详见 [gh-web-app/README.md](../gh-web-app/README.md)）：
   ```bash
   cd gh-web-app
   npm install && npm start   # 默认 http://localhost:3000
   ```
   并按说明注册 GitHub App、配置 `.env`。

2. **打开本项目**：Android Studio → Open → 选择 `gh-android` 目录 → Gradle 同步（要求 JDK 17+，Android SDK 34）。

3. **运行**：
   - 模拟器：直接运行，App 默认后端地址为 `http://10.0.2.2:3000`（模拟器访问宿主机）
   - 真机：App → 「我的」→ 修改服务地址为电脑局域网 IP 或内网穿透（ngrok）的 HTTPS 地址

4. 首次使用：App 打开后端 OAuth 页面 → 在 GitHub 授权 → 自动进入主界面。

## 构建 APK

```bash
gradle :app:assembleDebug   # 产物: app/build/outputs/apk/debug/app-debug.apk
# 或直接使用 Android Studio 的 Build → Build APK
```

## 项目结构

```
app/src/main/java/com/githubapp/android/
├── MainActivity.kt          # 主界面、底部导航、顶栏、路由分发
├── App.kt                   # Application：全局服务地址配置
├── nav/Nav.kt               # 页面路由与全局登录状态
├── api/ApiClient.kt         # OkHttp 封装（与 WebView 共享 Cookie）
└── ui/                      # 全部中文界面
    ├── LoginScreen.kt       # WebView OAuth 登录
    ├── HomeScreen.kt        # 首页（个人信息 + 收藏）
    ├── ReposScreen.kt       # 我的仓库
    ├── RepoScreen.kt        # 仓库详情（Tab 导航）
    ├── CodeTab.kt           # 代码浏览/文件操作
    ├── IssuesTab.kt         # Issues
    ├── PullsTab.kt          # Pull Requests
    ├── CommitsTab.kt        # 提交
    ├── BranchesTab.kt       # 分支
    ├── ReleasesTab.kt       # Releases
    ├── ActionsTab.kt        # Actions
    ├── SearchScreen.kt      # 搜索
    ├── NotificationsScreen.kt # 通知
    ├── GistsScreen.kt       # Gists
    ├── EventsScreen.kt      # Webhook 事件
    ├── MoreScreen.kt        # 我的（设置）
    └── GlobalLists.kt       # 我的 Issues / PR
```

## 说明

- 所有请求经后端 `/api/gh/*` 代理转发到 GitHub REST API，账号与应用（机器人）双身份共用一套会话
- 会话 Cookie 通过系统 `CookieManager` 在 WebView 与 OkHttp 之间共享
- 生产部署请将后端切到 HTTPS（内网穿透或服务器），并在 App 中把服务地址改为对应地址
