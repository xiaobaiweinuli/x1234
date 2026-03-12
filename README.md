# GitMob — Kotlin Android GitHub Client | Mobile Git Manager

**手机端 GitHub 原生管理工具，OAuth 2.0 认证，支持仓库浏览、分支管理、文件查看、Issues/PR 等完整功能。**

[![Build APK](https://github.com/xiaobaiweinuli/GitMob-Android/actions/workflows/build.yml/badge.svg)](https://github.com/xiaobaiweinuli/GitMob-Android/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024-4285F4?logo=android)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## 功能特性

- **GitHub OAuth 2.0 登录** — Cloudflare Worker 安全中转，client_secret 不暴露在客户端
- **仓库列表** — 搜索、过滤（公开/私有）、星标、语言标签
- **仓库详情** — 文件树、提交历史、分支管理、PR、Issues、Star/Unstar
- **文件查看器** — 任意分支路径，Monospace 等宽渲染
- **新建仓库** — 支持私有/公开、自动初始化 README
- **主题设置** — 默认浅色，支持深色/跟随系统切换
- **CI/CD** — GitHub Actions 自动构建签名 APK，推送 tag 自动发布 Release

---

## 快速开始

### 1. 创建 GitHub OAuth App

前往 [GitHub → Settings → Developer settings → OAuth Apps](https://github.com/settings/developers)

| 字段 | 值 |
|------|----|
| Application name | GitMob |
| **Homepage URL** | `https://gitmob.16618888.xyz` |
| **Authorization callback URL** | `https://gitmob.16618888.xyz/callback` |

> Homepage URL 填 Worker 自定义域名，作为 App 的公开落地页。

记录 **Client ID** 和 **Client Secret**。

### 2. 部署 Cloudflare Worker

```bash
cd cf-worker
npm install

# 设置 Secret（不会出现在代码里）
npx wrangler secret put GITHUB_CLIENT_ID
npx wrangler secret put GITHUB_CLIENT_SECRET

# 部署
npm run deploy
```

在 Cloudflare Dashboard 将 `gitmob.16618888.xyz` 绑定为自定义域名。

### 3. GitHub Actions Secrets 配置

仓库 → Settings → Secrets → Actions，添加以下 Secret：

| Secret 名 | 说明 |
|-----------|------|
| `OAUTH_CLIENT_ID` | GitHub OAuth App 的 Client ID |
| `OAUTH_WORKER_SUBDOMAIN` | Worker 子域（`bxiao` 部分） |
| `KEYSTORE_BASE64` | `base64 -w 0 gitmob.jks` 输出内容 |
| `KEYSTORE_PASSWORD` | Keystore 密码 |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key 密码 |

> ⚠️ Secret 名不能以 `GITHUB_` 开头（GitHub 保留前缀）

### 4. 本地开发

修改 `app/build.gradle.kts`：
```kotlin
buildConfigField("String", "GITHUB_CLIENT_ID", "\"your_client_id\"")
buildConfigField("String", "OAUTH_REDIRECT_URI", "\"https://gitmob.16618888.xyz/callback\"")
```

```bash
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

# 转 base64 供 Actions 使用
base64 -w 0 gitmob-release.jks
```

### 6. 触发构建

- **手动**：Actions → Build & Release APK → Run workflow
- **自动**：推送 tag `v1.0.0` 触发签名 APK + GitHub Release

---

## OAuth 流程

```
Android App
    │  Custom Tab 打开
    ▼
https://gitmob.16618888.xyz/auth
    │  重定向到 GitHub
    ▼
github.com/oauth/authorize（用户授权）
    │  回调
    ▼
https://gitmob.16618888.xyz/callback
    │  code → access_token（保护 client_secret）
    ▼
gitmob://oauth?token=xxx  ← Android 深链接
    │  App 接收并存储
    ▼
DataStore 持久化 → API Bearer token
```

---

## Worker 路由

| 路径 | 功能 |
|------|------|
| `GET /` | App 落地页（下载链接、功能介绍） |
| `GET /auth` | 跳转 GitHub OAuth 授权 |
| `GET /callback` | 接收 code，换 token，重定向 App |
| `GET /health` | 健康检查 |

---

## 技术栈

Kotlin 2.0 · Jetpack Compose BOM 2024.08 · Material 3 · Navigation Compose · Retrofit + OkHttp · DataStore · Coil · SplashScreen API · Cloudflare Workers TypeScript
