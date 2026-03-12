/**
 * GitMob OAuth Worker
 * 部署地址: https://gitmob-oauth.bxiao.workers.dev
 * 自定义域名: https://gitmob.16618888.xyz
 *
 * 路由:
 *   GET  /           → 展示 App 落地页
 *   GET  /auth        → 跳转 GitHub OAuth 授权
 *   GET  /callback    → 接收 code，换 token，重定向回 App
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

const APP_SCHEME   = "gitmob://oauth";
const REPO_URL     = "https://github.com/xiaobaiweinuli/GitMob-Android";
const SCOPES       = "repo,user,delete_repo,admin:public_key,workflow";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return cors(new Response(null));

    switch (url.pathname) {
      case "/":         return handleLanding(url);
      case "/auth":     return handleAuth(url, env);
      case "/callback": return handleCallback(url, env);
      case "/health":   return json({ ok: true, ts: Date.now() });
      default:          return new Response("Not Found", { status: 404 });
    }
  },
};

// ── 落地页（OAuth App Homepage URL 填这个） ──
function handleLanding(url: URL): Response {
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
    .icon{width:72px;height:72px;background:#FF6B4A22;border-radius:20px;display:inline-flex;align-items:center;justify-content:center;margin-bottom:20px}
    .icon svg{width:40px;height:40px;fill:#FF6B4A}
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
    <div class="icon">
      <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14H9V8h2v8zm4 0h-2V8h2v8z"/></svg>
    </div>
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

// ── /auth：生成授权 URL 并重定向 ──
function handleAuth(url: URL, env: Env): Response {
  const state  = crypto.randomUUID();
  const ghUrl  = new URL("https://github.com/login/oauth/authorize");
  ghUrl.searchParams.set("client_id",    env.GITHUB_CLIENT_ID);
  ghUrl.searchParams.set("redirect_uri", `${url.origin}/callback`);
  ghUrl.searchParams.set("scope",        SCOPES);
  ghUrl.searchParams.set("state",        state);
  return Response.redirect(ghUrl.toString(), 302);
}

// ── /callback：code → token → App 深链接 ──
async function handleCallback(url: URL, env: Env): Promise<Response> {
  const code  = url.searchParams.get("code");
  const error = url.searchParams.get("error");

  if (error || !code) {
    const desc = url.searchParams.get("error_description") ?? "authorization_failed";
    return Response.redirect(`${APP_SCHEME}?error=${enc(desc)}`, 302);
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

    if (!res.ok) throw new Error(`GitHub: ${res.status}`);

    const data = await res.json() as {
      access_token?: string;
      error?: string;
      error_description?: string;
    };

    if (data.error || !data.access_token) {
      const desc = data.error_description ?? data.error ?? "token_failed";
      return Response.redirect(`${APP_SCHEME}?error=${enc(desc)}`, 302);
    }

    return Response.redirect(`${APP_SCHEME}?token=${enc(data.access_token)}`, 302);

  } catch (err) {
    const msg = err instanceof Error ? err.message : "unknown";
    return Response.redirect(`${APP_SCHEME}?error=${enc(msg)}`, 302);
  }
}

// ── helpers ──
const enc = (s: string) => encodeURIComponent(s);
const corsHeaders = (): Record<string, string> => ({
  "Access-Control-Allow-Origin":  "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
});
const cors = (r: Response): Response => {
  corsHeaders(); // just return headers applied response
  return new Response(r.body, { ...r, headers: { ...Object.fromEntries(r.headers), ...corsHeaders() } });
};
const json = (data: unknown): Response =>
  new Response(JSON.stringify(data), {
    headers: { "Content-Type": "application/json", ...corsHeaders() },
  });
