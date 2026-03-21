# GitMob — Kotlin Android GitHub Client | Mobile Git Manager

**手机端 GitHub 原生管理工具，OAuth 2.0 安全认证，支持远程仓库完整管理与本地 JGit 操作，无需 Root 或外部 git 二进制。**

[![Build APK](https://github.com/xiaobaiweinuli/GitMob-Android/actions/workflows/build.yml/badge.svg)](https://github.com/xiaobaiweinuli/GitMob-Android/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024-4285F4?logo=android)](https://developer.android.com/jetpack/compose)
[![Material3](https://img.shields.io/badge/Material_3-Dynamic-6750A4?logo=android)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## 功能特性

### 远程 GitHub 管理
- **OAuth 2.0 登录** — Cloudflare Worker 安全中转，`client_secret` 永不暴露在 APK 中
- **多账号支持** — 随时切换或新增账号，DataStore 持久化 token
- **仓库列表** — 搜索、公开/私有过滤、Star/Unstar、语言标签
- **仓库详情** — 文件树浏览、在线编辑/删除文件并提交、文件历史记录与 diff 查看
- **提交历史** — 完整 commit 列表，逐文件 diff，支持回滚（revert commit）
- **分支管理** — 创建、切换、删除、重命名、设置默认分支
- **Pull Requests** — PR 列表，head/base 分支信息展示
- **Issues** — 状态/标签/作者/排序筛选，创建 Issue（支持 YAML Form 模板），评论数展示
- **GitHub Actions** — Workflow 列表、手动触发（dispatch）、运行日志、产物下载
- **Releases** — 发行版列表，Asset 文件下载（带进度通知，自动处理 GitHub→S3 两跳重定向）
- **Watch/Unwatch** — 仓库订阅，支持 Ignore/Participating/Releases 粒度
- **新建仓库** — 支持私有/公开，自动初始化 README

### 本地 Git 操作（JGit 6.10，纯 Java 实现）
- `clone` — 带 token 认证从远程克隆
- `init` — 新建本地 Git 项目
- `add / commit` — 暂存、提交（支持自定义作者）
- `push / pull` — 普通与强制推拉（force push / reset --hard）
- `branch` — 创建、切换、删除、重命名
- `diff / log` — 工作区变更、提交历史、逐文件 patch
- **冲突检测** — fetch 后比较本地/远程 commit 差异并提示

### 内置文件选择器
- 普通权限 + Root 两种模式
- 书签系统（内置常用目录 + 自定义书签持久化）
- 按名称/日期/大小/类型排序，支持显示隐藏文件
- **跨 Tab 切换保留状态** — 浏览路径与滚动位置跨 Tab 切换完整恢复
- 含空格目录名完整支持（单引号转义）

### 体验细节
- **主题** — 浅色/深色/跟随系统
- **崩溃日志** — 本地捕获，设置中可导出
- **CI/CD** — GitHub Actions 自动构建签名 APK，推 tag 自动发布 Release

---

## 快速开始

### 1. 创建 GitHub OAuth App

前往 [GitHub → Settings → Developer settings → OAuth Apps](https://github.com/settings/developers)

| 字段 | 值 |
|------|-----|
| Application name | GitMob |
| **Homepage URL** | `https://your-worker-domain.com` |
| **Authorization callback URL** | `https://your-worker-domain.com/callback` |

记录 **Client ID** 和 **Client Secret**。

---

### 2. 部署 Cloudflare Worker

```bash
cd cf-worker
npm install

# 配置加密 Secret（不写入代码）
npx wrangler secret put GITHUB_CLIENT_ID
npx wrangler secret put GITHUB_CLIENT_SECRET

# 部署
npm run deploy
```

在 Cloudflare Dashboard → Worker → Triggers 绑定自定义域名（可选）。

---

### 3. 配置 GitHub Actions Secrets

仓库 → Settings → Secrets and variables → Actions：

| Secret 名 | 说明 |
|-----------|------|
| `OAUTH_CLIENT_ID` | GitHub OAuth App Client ID |
| `OAUTH_CALLBACK_URL` | Worker 基础 URL，**不含 `/callback`** |
| `KEYSTORE_BASE64` | `base64 -w 0 gitmob-release.jks` 输出 |
| `KEYSTORE_PASSWORD` | Keystore 密码 |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key 密码 |

> ⚠️ Secret 名不能以 `GITHUB_` 开头（GitHub 系统保留前缀）

---

### 4. 本地开发

修改 `app/build.gradle.kts`：

```kotlin
buildConfigField("String", "GITHUB_CLIENT_ID",  "\"your_client_id\"")
buildConfigField("String", "OAUTH_REDIRECT_URI", "\"https://your-worker-domain.com/callback\"")
```

```bash
./gradlew assembleDebug
./gradlew installDebug
```

---

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

---

### 6. 触发构建

- **手动**：Actions → Build & Release APK → Run workflow
- **自动**：推送 tag（如 `v1.0.0`）自动构建并发布 Release

---

## OAuth 安全流程

```
Android App
    │  Custom Tab 打开
    ▼
https://your-worker.com/auth
    │  302 重定向到 GitHub
    ▼
github.com/login/oauth/authorize（用户授权）
    │  带 code 回调
    ▼
https://your-worker.com/callback
    │  Worker 用 client_secret 换 access_token（secret 仅存 CF Worker）
    ▼
gitmob://oauth?token=xxx  ← Android Deep Link
    │  App 接收并存储
    ▼
DataStore 持久化 → 所有 API 请求携带 Bearer token
```

**注销两档：**
- **退出登录** — 撤销 token（`DELETE /token`），授权记录保留，下次快速重登
- **取消授权** — 删除 Grant（`DELETE /grant`），彻底清除，下次需重新完整授权

---

## Worker 路由

| 路径 | 方法 | 功能 |
|------|------|------|
| `/` | GET | App 落地页（APK 下载、功能介绍） |
| `/auth` | GET | 跳转 GitHub OAuth（`?force=1` 强制重授权） |
| `/callback` | GET | code → token，HTML 唤起 App 深链接 |
| `/health` | GET | 健康检查 `{"ok": true}` |
| `/token` | DELETE | 撤销当前 token |
| `/grant` | DELETE | 删除 OAuth grant（彻底注销） |

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.0 |
| UI | Jetpack Compose BOM 2024.08 · Material 3 |
| 导航 | Navigation Compose |
| 网络 | Retrofit 2 · OkHttp 4 · Gson |
| 本地 Git | JGit 6.10（纯 Java，无需外部 git） |
| 存储 | DataStore Preferences |
| 图片 | Coil 2 |
| YAML | Jackson 2（解析 Actions workflow） |
| Markdown | compose-markdown 0.5.8 |
| 后端 | Cloudflare Workers (TypeScript) |
| CI/CD | GitHub Actions |

---

## 项目结构

```
app/src/main/java/com/gitmob/android/
├── api/            # Retrofit 接口、数据模型、GraphQL 客户端
├── auth/           # OAuth、Token 存储、多账号
├── data/           # 仓库数据层（Repository 模式）
├── local/          # JGit 本地操作封装（GitRunner）
├── ui/
│   ├── filepicker/ # 内置文件选择器（跨 Tab 状态保留）
│   ├── local/      # 本地仓库列表 & 详情
│   ├── nav/        # Navigation Compose 路由 & Tab 管理
│   ├── repo/       # 远程仓库详情（文件/提交/分支/Actions/Issues/PR）
│   ├── repos/      # 仓库列表
│   ├── settings/   # 设置页（主题/账号/Root/日志）
│   └── theme/      # Material 3 主题 & 颜色系统
└── util/           # 下载管理、崩溃日志、LogManager
cf-worker/          # Cloudflare Worker（OAuth 安全中转）
```