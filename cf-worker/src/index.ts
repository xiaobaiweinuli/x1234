/**
 * GitMob OAuth Worker
 * 部署地址: https://gitmob-oauth.bxiao.workers.dev
 * 自定义域名: https://gitmob.16618888.xyz
 *
 * 路由:
 *   GET    /           → App 落地页
 *   GET    /auth        → 跳转 GitHub OAuth 授权（?force=1 强制重授权）
 *   GET    /callback    → 接收 code，换 token，HTML + JS 唤起 App
 *   GET    /health      → 健康检查
 *   DELETE /token       → 撤销 Token（token 失效，授权记录保留）
 *   DELETE /grant       → 删除授权 Grant（彻底移除 App 授权，需重新授权）
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
      return new Response(null, { headers: corsHeaders("GET, DELETE, OPTIONS") });
    }

    if (request.method === "GET") {
      switch (url.pathname) {
        case "/":         return handleLanding();
        case "/auth":     return handleAuth(url, env);
        case "/callback": return handleCallback(url, env);
        case "/health":   return json({ ok: true, ts: Date.now() });
      }
    }

    if (request.method === "DELETE") {
      switch (url.pathname) {
        case "/token": return handleRevokeToken(request, env);
        case "/grant": return handleDeleteGrant(request, env);
      }
    }

    return new Response("Not Found", { status: 404 });
  },
};

// ── 落地页 ───────────────────────────────────────────────────────────
function handleLanding(): Response {
  const html = `<!DOCTYPE html>
<html lang="zh">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>GitMob \u2014 \u624b\u673a\u7aef GitHub \u7ba1\u7406\u5de5\u5177</title>
  <meta name="description" content="GitMob \u662f\u4e00\u6b3e Android GitHub \u5ba2\u6237\u7aef\uff0c\u652f\u6301\u4ed3\u5e93\u7ba1\u7406\u3001\u6587\u4ef6\u6d4f\u89c8\u3001\u5206\u652f\u64cd\u4f5c\u3001PR/Issues \u67e5\u770b\u3002"/>
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
    <p>\u624b\u673a\u7aef GitHub \u539f\u751f\u7ba1\u7406\u5de5\u5177<br/>Kotlin \u00b7 Jetpack Compose \u00b7 Material 3</p>
    <a href="${REPO_URL}/releases" class="btn">\u4e0b\u8f7d APK</a>
    <a href="${REPO_URL}" class="btn ghost">GitHub \u4ed3\u5e93</a>
    <div class="features">
      <div class="feat"><strong>\u4ed3\u5e93\u7ba1\u7406</strong>\u641c\u7d22\u3001\u7b5b\u9009\u3001\u661f\u6807</div>
      <div class="feat"><strong>\u6587\u4ef6\u6d4f\u89c8</strong>\u4efb\u610f\u5206\u652f\u3001\u8def\u5f84</div>
      <div class="feat"><strong>\u5206\u652f\u64cd\u4f5c</strong>\u521b\u5efa\u3001\u5207\u6362\u3001\u7ba1\u7406</div>
      <div class="feat"><strong>PR / Issues</strong>\u67e5\u770b\u5f00\u653e\u72b6\u6001</div>
    </div>
  </div>
</body>
</html>`;
  return new Response(html, {
    headers: { "Content-Type": "text/html;charset=UTF-8", ...corsHeaders() },
  });
}

// ── /auth \uff1a\u8df3\u8f6c GitHub \u6388\u6743 ───────────────────────────────────────────
function handleAuth(url: URL, env: Env): Response {
  const state = crypto.randomUUID();
  const force = url.searchParams.get("force") === "1";
  const ghUrl = new URL("https://github.com/login/oauth/authorize");

  ghUrl.searchParams.set("client_id",    env.GITHUB_CLIENT_ID);
  ghUrl.searchParams.set("redirect_uri", `${url.origin}/callback`);
  ghUrl.searchParams.set("scope",        SCOPES);
  ghUrl.searchParams.set("state",        state);
  if (force) ghUrl.searchParams.set("prompt", "consent");

  return Response.redirect(ghUrl.toString(), 302);
}

// ── /callback\uff1acode \u2192 token \u2192 HTML \u5524\u8d77 App ────────────────────────
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

// ── DELETE /token\uff1a\u64a4\u9500 Token ──────────────────────────────────────────
//
// \u4f7f\u5f53\u524d access_token \u5931\u6548\uff08\u7c7b\u4f3c\u201c\u6ce8\u9500\u5f53\u524d\u4f1a\u8bdd\u201d\uff09\u3002
// GitHub \u8bbe\u7f6e \u2192 Applications \u4e2d\u4ed3\u5e93\u6388\u6743\u8bb0\u5f55\u4ecd\u5b58\u5728\uff0c
// \u4e0b\u6b21\u53ef\u76f4\u63a5\u91cd\u65b0\u83b7\u53d6\u65b0 token\uff08\u65e0\u9700\u91cd\u65b0\u9009\u6743\u9650\uff09\u3002
//
// \u8bf7\u6c42\u5934: Authorization: Bearer <access_token>
// \u54cd\u5e94: 200 { ok: true, action: "token_revoked" }
//
async function handleRevokeToken(request: Request, env: Env): Promise<Response> {
  const token = extractBearerToken(request);
  if (!token) return json({ ok: false, error: "missing_token" }, 400);

  try {
    const res = await githubAppsApi(
      "DELETE",
      `/applications/${env.GITHUB_CLIENT_ID}/token`,
      { access_token: token },
      env,
    );
    // 204 = \u6210\u529f\uff1b404 = token \u5df2\u5931\u6548\uff0c\u5747\u89c6\u4e3a\u6210\u529f
    if (res.status === 204 || res.status === 404) {
      return json({ ok: true, action: "token_revoked" });
    }
    const body = await res.text();
    return json({ ok: false, error: `github_${res.status}`, detail: body }, 502);
  } catch (err) {
    return json({ ok: false, error: err instanceof Error ? err.message : "unknown" }, 502);
  }
}

// ── DELETE /grant\uff1a\u5220\u9664\u6388\u6743 Grant ───────────────────────────────────────
//
// \u5f7b\u5e95\u5220\u9664 GitMob \u5728\u8be5 GitHub \u8d26\u53f7\u7684\u6240\u6709 OAuth \u6388\u6743\uff08\u5305\u62ec\u5168\u90e8\u5173\u8054 token\uff09\u3002
// \u7528\u6237\u5728 GitHub \u8bbe\u7f6e \u2192 Applications \u4e2d\u5c06\u770b\u4e0d\u5230 GitMob\uff0c
// \u4e0b\u6b21\u767b\u5f55\u9700\u91cd\u65b0\u9009\u6743\u9650\u5b8c\u6210\u6388\u6743\u3002
// \u9002\u7528\u4e8e\u201c\u9000\u51fa\u5e76\u5f7b\u5e95\u53d6\u6d88\u6388\u6743\u201d\u573a\u666f\u3002
//
// \u8bf7\u6c42\u5934: Authorization: Bearer <access_token>
// \u54cd\u5e94: 200 { ok: true, action: "grant_deleted" }
//
async function handleDeleteGrant(request: Request, env: Env): Promise<Response> {
  const token = extractBearerToken(request);
  if (!token) return json({ ok: false, error: "missing_token" }, 400);

  try {
    const res = await githubAppsApi(
      "DELETE",
      `/applications/${env.GITHUB_CLIENT_ID}/grant`,
      { access_token: token },
      env,
    );
    if (res.status === 204 || res.status === 404) {
      return json({ ok: true, action: "grant_deleted" });
    }
    const body = await res.text();
    return json({ ok: false, error: `github_${res.status}`, detail: body }, 502);
  } catch (err) {
    return json({ ok: false, error: err instanceof Error ? err.message : "unknown" }, 502);
  }
}

// ── htmlRedirect ─────────────────────────────────────────────────────
function htmlRedirect(deepLink: string, isError: boolean): Response {
  const title   = isError ? "\u6388\u6743\u5931\u8d25" : "\u6388\u6743\u6210\u529f";
  const color   = isError ? "#F87171" : "#4ADE80";
  const message = isError
    ? "\u6388\u6743\u8fc7\u7a0b\u4e2d\u51fa\u73b0\u9519\u8bef\uff0c\u8bf7\u8fd4\u56de App \u91cd\u8bd5\u3002"
    : "\u6388\u6743\u6210\u529f\uff01\u6b63\u5728\u81ea\u52a8\u8df3\u8f6c\u56de GitMob\u2026";

  const html = `<!DOCTYPE html>
<html lang="zh">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>GitMob \u2014 ${title}</title>
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
    <div class="status">${isError ? "&#x26A0;&#xFE0F;" : "&#x2713;"}</div>
    <h2>${title}</h2>
    <p>${message}</p>
    <button class="btn" id="openBtn">\u6253\u5f00 GitMob</button>
    <p class="hint" id="hint"></p>
  </div>
  <script>
    var deepLink = ${JSON.stringify(deepLink)};
    function tryOpen() { window.location.href = deepLink; }
    document.getElementById('openBtn').addEventListener('click', tryOpen);
    setTimeout(function() {
      tryOpen();
      setTimeout(function() {
        document.getElementById('hint').textContent =
          '\u5982\u679c\u672a\u81ea\u52a8\u8df3\u8f6c\uff0c\u8bf7\u70b9\u51fb\u4e0a\u65b9\u6309\u9215\u624b\u52a8\u6253\u5f00 GitMob';
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

// ── helpers ──────────────────────────────────────────────────────────

/** GitHub Apps REST API，Basic Auth（client_id:client_secret） */
async function githubAppsApi(
  method: string,
  path: string,
  body: Record<string, string>,
  env: Env,
): Promise<Response> {
  const credentials = btoa(`${env.GITHUB_CLIENT_ID}:${env.GITHUB_CLIENT_SECRET}`);
  return fetch(`https://api.github.com${path}`, {
    method,
    headers: {
      "Authorization":        `Basic ${credentials}`,
      "Accept":               "application/vnd.github+json",
      "Content-Type":         "application/json",
      "User-Agent":           "GitMob-OAuth-Worker/1.0",
      "X-GitHub-Api-Version": "2022-11-28",
    },
    body: JSON.stringify(body),
  });
}

/** 从 Authorization: Bearer <token> 提取 token */
function extractBearerToken(request: Request): string | null {
  const auth  = request.headers.get("Authorization") ?? "";
  const match = auth.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : null;
}

const enc = (s: string) => encodeURIComponent(s);

function corsHeaders(methods = "GET, OPTIONS"): Record<string, string> {
  return {
    "Access-Control-Allow-Origin":  "*",
    "Access-Control-Allow-Methods": methods,
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders("GET, DELETE, OPTIONS") },
  });
}
