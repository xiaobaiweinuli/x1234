# GitMob — Kotlin Android GitHub Client | Mobile Git Manager

**手机端 GitHub 原生管理工具，使用 GitHub OAuth 认证，支持仓库浏览、分支管理、文件查看、Issues/PR 等完整功能。**

[![Build APK](https://github.com/YOUR_USERNAME/gitmob-android/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/gitmob-android/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024-4285F4?logo=android)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## 功能特性

- **GitHub OAuth 2.0 登录** — 通过 Cloudflare Worker 安全中转换取 token，client_secret 不暴露在客户端
- **仓库列表** — 搜索、过滤（公开/私有）、星标数、语言标签、实时刷新
- **仓库详情** — 文件树浏览、提交历史、分支管理、Pull Requests、Issues、Star/Unstar
- **文件查看器** — 任意分支、任意路径的文件内容读取（Monospace 等宽渲染）
- **新建仓库** — 在 App 内创建 GitHub 仓库，支持私有/公开、自动初始化 README
- **分支操作** — 切换分支、从当前 HEAD 新建分支
- **CI/CD** — GitHub Actions 自动构建签名 APK，推送 `v*.*.*` tag 自动发布 Release
- **Material 3 深色主题** — 珊瑚橙主色调，适配 Android 边缘到边缘显示
- **SplashScreen API** — 原生启动画面，无白屏闪烁

---

## 项目结构

```
gitmob-android/
├── app/src/main/java/com/gitmob/android/
│   ├── auth/           # OAuth + DataStore token 持久化
│   ├── api/            # Retrofit GitHub REST API v3
│   ├── data/           # Repository 数据层
│   └── ui/
│       ├── theme/      # Material 3 颜色/字体/主题
│       ├── nav/        # Compose Navigation 路由
│       ├── login/      # OAuth 登录页
│       ├── repos/      # 仓库列表
│       ├── repo/       # 仓库详情（文件/提交/分支/PR/Issues）
│       ├── create/     # 新建仓库
│       └── common/     # 公共组件（GmCard/GmBadge/LoadingBox...）
├── cf-worker/          # Cloudflare Worker — OAuth token 中转
│   ├── src/index.ts
│   └── wrangler.toml
└── .github/workflows/
    ├── build.yml       # 手动/tag 触发构建 + 签名 + Release
    └── lint.yml        # PR 自动 Lint 检查
```

---

## 快速开始

### 1. 创建 GitHub OAuth App

前往 [GitHub → Settings → Developer settings → OAuth Apps → New OAuth App](https://github.com/settings/developers)

| 字段 | 值 |
|------|----|
| Application name | GitMob |
| Homepage URL | `https://github.com/YOUR_USERNAME/gitmob-android` |
| Authorization callback URL | `https://gitmob-oauth.YOUR_SUBDOMAIN.workers.dev/callback` |

记录 **Client ID** 和 **Client Secret**。

### 2. 部署 Cloudflare Worker

```bash
cd cf-worker
npm install
npx wrangler login

# 设置环境变量（Secret 不会出现在代码里）
npx wrangler secret put GITHUB_CLIENT_ID
npx wrangler secret put GITHUB_CLIENT_SECRET

# 部署
npm run deploy
```

记录 Worker URL，格式：`https://gitmob-oauth.YOUR_SUBDOMAIN.workers.dev`

### 3. 配置 GitHub Actions Secrets

在你的 GitHub 仓库 → Settings → Secrets → Actions 中添加：

| Secret 名 | 说明 |
|-----------|------|
| `GITHUB_CLIENT_ID_OAUTH` | OAuth App 的 Client ID |
| `CF_SUBDOMAIN` | Worker 的 subdomain（`your-subdomain` 部分） |
| `KEYSTORE_BASE64` | `base64 -w 0 your.keystore` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 密码 |

### 4. 本地开发

```bash
# 修改 app/build.gradle.kts，填入你的真实值
buildConfigField("String", "GITHUB_CLIENT_ID", "\"your_client_id\"")
buildConfigField("String", "OAUTH_REDIRECT_URI", "\"https://gitmob-oauth.xxx.workers.dev/callback\"")

# 构建运行
./gradlew assembleDebug
./gradlew installDebug
```

### 5. 生成签名 Keystore

```bash
keytool -genkey -v \
  -keystore gitmob-release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias gitmob

# 编码为 base64 供 GitHub Actions 使用
base64 -w 0 gitmob-release.jks
```

### 6. 触发构建

- **手动**：Actions → Build & Release APK → Run workflow → 选择 release/debug
- **自动**：推送 tag `v1.0.0` 触发 Release 构建并自动创建 GitHub Release

---

## OAuth 流程

```
Android App
    │  打开 Custom Tab
    ▼
github.com/oauth/authorize
    │  用户授权后跳转
    ▼
Cloudflare Worker /callback
    │  code → access_token（保护 client_secret）
    ▼
gitmob://oauth?token=xxx  ← Android Deep Link
    │  App 接收 token
    ▼
DataStore 持久化 → API 请求携带 Bearer token
```

---

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 2.0 |
| Jetpack Compose | BOM 2024.08 |
| Material 3 | latest |
| Navigation Compose | 2.7.7 |
| Retrofit + OkHttp | 2.11 / 4.12 |
| DataStore Preferences | 1.1.1 |
| Coil | 2.7 |
| SplashScreen API | 1.0.1 |
| Cloudflare Workers | TypeScript |

---

GitMob is a Kotlin Jetpack Compose Android app for GitHub repository management with OAuth 2.0 authentication via Cloudflare Workers, featuring repo browsing, branch management, file viewer, PR/Issues tracking, and automated APK builds via GitHub Actions.
