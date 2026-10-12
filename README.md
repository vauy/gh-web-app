# GitHub Web App（gh-web-app）

覆盖 GitHub 网页版核心操作的完整项目：

- **[gh-android](./gh-android)** — Android 原生客户端（Kotlin + Jetpack Compose，全中文界面，**纯独立版**：直连 GitHub API、设备码登录、无需后端），打包好的 APK 见 [gh-android/app-debug.apk](./gh-android/app-debug.apk)
- **[gh-web-app](./gh-web-app)** — 可选的后端服务（Node/Express）：网页版 SPA、OAuth 登录、API 代理、Webhook 接收、机器人自动回复（需要服务器时使用）

功能覆盖：仓库/文件/Issue/PR/提交/分支/Release/Actions/通知/搜索/Gists 等网页版全部操作。

## CI/CD（GitHub Actions）

- [Android 构建 APK](.github/workflows/build-android.yml)：push/PR 到 `gh-android` 时自动 `assembleDebug`，产物以 `app-debug-apk` 工件上传
- [后端冒烟测试](.github/workflows/backend-check.yml)：push/PR 到 `gh-web-app` 时自动 `npm ci`、语法检查并启动服务验证 `/api/session`

![Android 构建](https://github.com/vauy/gh-web-app/actions/workflows/build-android.yml/badge.svg)
![后端冒烟测试](https://github.com/vauy/gh-web-app/actions/workflows/backend-check.yml/badge.svg)

## 快速开始

**方式 A：纯独立 Android App（推荐，无需任何服务器）**
1. 安装 [gh-android/app-debug.apk](./gh-android/app-debug.apk)
2. 打开 App，登录页填入你 GitHub OAuth App 的 Client ID（见 [gh-android/README](./gh-android/README.md)）
3. 按设备码流程授权即完成登录

**方式 B：带机器人/集成的完整方案（需要服务器）**
1. 注册 GitHub App（按 [gh-web-app/app.yml](./gh-web-app/app.yml)）并配置 `.env`
2. 启动后端：`cd gh-web-app && npm install && npm start`
3. App 内 OAuth 登录（见 [gh-web-app/README](./gh-web-app/README.md)）
