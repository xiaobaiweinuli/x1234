/**
 * GitMob OAuth Worker
 * 部署地址: https://gitmob-oauth.bxiao.workers.dev
 * 自定义域名: https://gitmob.16618888.xyz
 *
 * 路由:
 *   GET  /           → App 落地页（GitHub OAuth App 的 Homepage URL）
 *   GET  /auth        → 跳转 GitHub OAuth 授权
 *   GET  /callback    → 接收 code，换 token，重定向回 App（HTML + JS 方式）
 *   GET  /health      → 健康检查
 *
 * 环境变量（CF Dashboard → Workers → Settings → Variables）:
 *   GITHUB_CLIENT_ID      明文
 *   GITHUB_CLIENT_SECRET  加密 Secret
 */

export interface Env {
  GITHUB_CLIENT_ID: string;
  GITHUB_CLIENT_SECRET: string;
}

const APP_SCHEME = "gitmob://oauth";
const REPO_URL   = "https://github.com/xiaobaiweinuli/GitMob-Android";
const SCOPES     = "repo,user,delete_repo,admin:public_key,workflow";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    switch (url.pathname) {
      case "/":         return handleLanding();
      case "/auth":     return handleAuth(url, env);
      case "/callback": return handleCallback(url, env);
      case "/health":   return json({ ok: true, ts: Date.now() });
      default:          return new Response("Not Found", { status: 404 });
    }
  },
};

// ── 落地页 ──
function handleLanding(): Response {
  const html = `<!DOCTYPE html>
<html lang="zh">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>GitMob — 手机端 GitHub 管理工具</title>
  <meta name="description" content="GitMob 是一款 Android GitHub 客户端，支持仓库管理、文件浏览、分支操作、PR/Issues 查看。"/>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:-apple-system,sans-serif;background:#0F1117;color:#E8EAF0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
    .card{background:#161B25;border:1px solid #2A3347;border-radius:20px;padding:40px 32px;max-width:420px;width:100%;text-align:center}
    h1{font-size:28px;font-weight:700;color:#FF6B4A;letter-spacing:-1px;margin-bottom:8px}
    p{font-size:14px;color:#9BA3BA;line-height:1.6;margin-bottom:28px}
    .btn{display:inline-block;padding:12px 28px;background:#FF6B4A;color:#fff;border-radius:12px;text-decoration:none;font-weight:600;font-size:14px;margin:6px}
    .btn.ghost{background:transparent;border:1px solid #2A3347;color:#9BA3BA}
    .features{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:24px;text-align:left}
    .feat{background:#1E2535;border-radius:10px;padding:12px;font-size:12px;color:#9BA3BA}
    .feat strong{color:#E8EAF0;display:block;margin-bottom:2px}
  </style>
</head>
<body>
  <div class="card">
    <h1>GitMob</h1>
    <p>手机端 GitHub 原生管理工具<br/>Kotlin · Jetpack Compose · Material 3</p>
    <a href="${REPO_URL}/releases" class="btn">下载 APK</a>
    <a href="${REPO_URL}" class="btn ghost">GitHub 仓库</a>
    <div class="features">
      <div class="feat"><strong>仓库管理</strong>搜索、筛选、星标</div>
      <div class="feat"><strong>文件浏览</strong>任意分支、路径</div>
      <div class="feat"><strong>分支操作</strong>创建、切换、管理</div>
      <div class="feat"><strong>PR / Issues</strong>查看开放状态</div>
    </div>
  </div>
</body>
</html>`;
  return new Response(html, {
    headers: { "Content-Type": "text/html;charset=UTF-8", ...corsHeaders() },
  });
}

// ── /auth：跳转 GitHub 授权 ──
function handleAuth(url: URL, env: Env): Response {
  const state = crypto.randomUUID();
  const ghUrl = new URL("https://github.com/login/oauth/authorize");
  ghUrl.searchParams.set("client_id",    env.GITHUB_CLIENT_ID);
  ghUrl.searchParams.set("redirect_uri", `${url.origin}/callback`);
  ghUrl.searchParams.set("scope",        SCOPES);
  ghUrl.searchParams.set("state",        state);
  return Response.redirect(ghUrl.toString(), 302);
}

// ── /callback：code → token → HTML 跳回 App ──
// 关键：Chrome Custom Tab 不会自动跟随 302 到自定义 scheme（gitmob://）
// 必须用 HTML + JS window.location 触发 Android intent，才能唤起 App
async function handleCallback(url: URL, env: Env): Promise<Response> {
  const code  = url.searchParams.get("code");
  const error = url.searchParams.get("error");

  if (error || !code) {
    const desc = url.searchParams.get("error_description") ?? "authorization_failed";
    return htmlRedirect(`${APP_SCHEME}?error=${enc(desc)}`, true);
  }

  try {
    const res = await fetch("https://github.com/login/oauth/access_token", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        client_id:     env.GITHUB_CLIENT_ID,
        client_secret: env.GITHUB_CLIENT_SECRET,
        code,
      }),
    });

    if (!res.ok) throw new Error(`GitHub returned ${res.status}`);

    const data = await res.json() as {
      access_token?: string;
      error?: string;
      error_description?: string;
    };

    if (data.error || !data.access_token) {
      const desc = data.error_description ?? data.error ?? "token_exchange_failed";
      return htmlRedirect(`${APP_SCHEME}?error=${enc(desc)}`, true);
    }

    return htmlRedirect(`${APP_SCHEME}?token=${enc(data.access_token)}`, false);

  } catch (err) {
    const msg = err instanceof Error ? err.message : "unknown_error";
    return htmlRedirect(`${APP_SCHEME}?error=${enc(msg)}`, true);
  }
}

/**
 * 返回一个 HTML 页面，通过 JS 立即跳转到 App 自定义 Scheme。
 * 这比 302 直接跳 gitmob:// 更可靠：
 *   - Android Custom Tab (Chrome) 会拦截 window.location 到 intent scheme
 *   - 提供"手动打开 App"按钮作为降级方案
 */
function htmlRedirect(deepLink: string, isError: boolean): Response {
  const title   = isError ? "授权失败" : "授权成功";
  const color   = isError ? "#F87171"  : "#4ADE80";
  const message = isError
    ? "授权过程中出现错误，请返回 App 重试。"
    : "授权成功！正在自动跳转回 GitMob…";

  const html = `<!DOCTYPE html>
<html lang="zh">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>GitMob — ${title}</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:-apple-system,sans-serif;background:#0F1117;color:#E8EAF0;
         min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
    .card{background:#161B25;border:1px solid #2A3347;border-radius:20px;
          padding:40px 28px;max-width:380px;width:100%;text-align:center}
    .status{font-size:40px;margin-bottom:16px}
    h2{font-size:20px;font-weight:600;color:${color};margin-bottom:10px}
    p{font-size:14px;color:#9BA3BA;line-height:1.6;margin-bottom:24px}
    .btn{display:inline-block;padding:13px 32px;background:#FF6B4A;color:#fff;
         border-radius:12px;text-decoration:none;font-weight:600;font-size:15px;
         cursor:pointer;border:none;width:100%}
    .hint{font-size:12px;color:#5C6580;margin-top:14px}
  </style>
</head>
<body>
  <div class="card">
    <div class="status">${isError ? "⚠️" : "✓"}</div>
    <h2>${title}</h2>
    <p>${message}</p>
    <button class="btn" id="openBtn">打开 GitMob</button>
    <p class="hint" id="hint"></p>
  </div>
  <script>
    const deepLink = ${JSON.stringify(deepLink)};

    function tryOpen() {
      // 直接跳转自定义 scheme，Android 会触发 intent
      window.location.href = deepLink;
    }

    document.getElementById('openBtn').addEventListener('click', tryOpen);

    // 页面加载后自动尝试，给 300ms 让页面渲染完成
    setTimeout(function() {
      tryOpen();
      // 2 秒后如果还在页面，说明未跳转，显示提示
      setTimeout(function() {
        document.getElementById('hint').textContent =
          '如果未自动跳转，请点击上方按钮手动打开 GitMob';
      }, 2000);
    }, 300);
  </script>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: { "Content-Type": "text/html;charset=UTF-8", ...corsHeaders() },
  });
}

// ── helpers ──
const enc = (s: string) => encodeURIComponent(s);
const corsHeaders = (): Record<string, string> => ({
  "Access-Control-Allow-Origin":  "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
});
const json = (data: unknown): Response =>
  new Response(JSON.stringify(data), {
    headers: { "Content-Type": "application/json", ...corsHeaders() },
  });
