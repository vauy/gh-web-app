# GitHub 客户端（Android 原生 App · 纯独立版）

Kotlin + Jetpack Compose 编写的 **Android 原生应用**，全中文界面，覆盖 GitHub 网页版核心操作。

**不依赖任何后端服务**：直连 `api.github.com`，登录使用 GitHub 官方**设备码（Device Flow）**方式，装好就能用。

## 功能

- 🔐 GitHub 设备码登录（无需回调地址/后端；首次在任意浏览器输入用户码授权）
- 📦 仓库：列表/过滤/新建/收藏/关注
- 📁 代码：目录浏览、文件渲染查看、编辑、新建、上传、删除
- 🐛 Issues：列表/创建/编辑/评论/关闭/重开
- 🔀 Pull Requests：列表/创建/批准/请求更改/合并/评论/变更文件
- 🕘 提交历史与 diff 查看
- 🌿 分支：列表/新建/删除/保护规则查看
- 🏷️ Releases 发布
- ⚙️ Actions：工作流/运行记录/Jobs/日志
- 🔔 通知（全部/单条标记已读）
- 📝 Gists、🔍 搜索（仓库/用户/Issue/PR/代码）、👤 我的 Issues / PR

> 说明：Webhook 接收与机器人自动回复需要服务器，纯独立版不含此功能。

## 首次使用（一次性设置）

1. 创建应用拿 Client ID（1 分钟）：
   - GitHub → **Settings → Developer settings → OAuth Apps → New OAuth App**
   - 名称随意，回调地址留空即可（设备码不需要）
   - 复制页面上显示的 **Client ID**
2. 打开 App，把 Client ID 填入登录页（只需填一次，会保存）
3. 点「获取授权代码」→ 在任意设备浏览器打开 `https://github.com/login/device` → 输入 App 显示的 8 位代码 → 授权
4. 回 App 点「我已授权，开始连接」→ 自动登录

## 构建

```bash
gradle :app:assembleDebug   # 产物: app/build/outputs/apk/debug/app-debug.apk
# 或用 Android Studio 打开本目录运行 / Build APK
```

## 项目结构

```
app/src/main/java/com/githubapp/android/
├── MainActivity.kt          # 主界面、底部导航、顶栏、路由分发
├── App.kt                   # 全局令牌 / Client ID 持久化
├── nav/Nav.kt               # 页面路由与全局登录状态
├── api/ApiClient.kt         # OkHttp 直连 GitHub API + 设备码登录
└── ui/                      # 全部中文界面
    ├── LoginScreen.kt       # 设备码登录
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
    ├── MoreScreen.kt        # 我的（设置）
    └── GlobalLists.kt       # 我的 Issues / PR
```

## 说明

- 令牌保存在本机 SharedPreferences，退出登录即清除
- 所有请求直接走 GitHub REST API，无需任何服务器
