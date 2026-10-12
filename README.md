# GitHub Web App（gh-web-app）

一个覆盖 GitHub 网页版核心操作的完整项目，包含：

- **[gh-web-app](./gh-web-app)** — Node/Express 后端：OAuth 账号登录、GitHub REST API 通用代理、Webhook 接收、机器人自动回复、网页版 SPA
- **[gh-android](./gh-android)** — Android 原生客户端（Kotlin + Jetpack Compose，全中文界面），打包好的 APK 见 [gh-android/app-debug.apk](./gh-android/app-debug.apk)

功能覆盖：仓库/文件/Issue/PR/提交/分支/Release/Actions/通知/搜索/Gists/Webhook 事件等网页版全部操作。

## CI/CD（GitHub Actions）

- [Android 构建 APK](.github/workflows/build-android.yml)：push/PR 到 `gh-android` 时自动 `assembleDebug`，产物以 `app-debug-apk` 工件上传，可在 Actions 页面下载
- [后端冒烟测试](.github/workflows/backend-check.yml)：push/PR 到 `gh-web-app` 时自动 `npm ci`、语法检查并启动服务验证 `/api/session`

![Android 构建](https://github.com/vauy/gh-web-app/actions/workflows/build-android.yml/badge.svg)
![后端冒烟测试](https://github.com/vauy/gh-web-app/actions/workflows/backend-check.yml/badge.svg)

## 快速开始

1. 注册 GitHub App（按 [gh-web-app/app.yml](./gh-web-app/app.yml) 一键创建）并配置 `.env`
2. 启动后端：`cd gh-web-app && npm install && npm start`
3. Android：安装 [app-debug.apk](./gh-android/app-debug.apk)，模拟器默认连 `http://10.0.2.2:3000`，真机在「我的 → 服务地址」填后端地址

详见各子目录 README。
