/**
 * GitMob OAuth Worker
 * 负责：
 *   1. /auth      → 跳转 GitHub OAuth 授权页
 *   2. /callback  → GitHub 回调，用 code 换 access_token，再重定向回 App
 *
 * 环境变量（在 wrangler.toml 或 CF Dashboard 中配置）：
 *   GITHUB_CLIENT_ID      GitHub OAuth App Client ID
 *   GITHUB_CLIENT_SECRET  GitHub OAuth App Client Secret（敏感，不得写入代码）
 *   ALLOWED_ORIGINS       允许的回调域名，逗号分隔（可选，安全加固）
 */

export interface Env {
  GITHUB_CLIENT_ID: string;
  GITHUB_CLIENT_SECRET: string;
}

const APP_SCHEME = "gitmob://oauth"; // Android deep link

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // ── CORS 预检 ──
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    switch (url.pathname) {
      case "/auth":
        return handleAuth(url, env);
      case "/callback":
        return handleCallback(url, env);
      case "/health":
        return new Response(JSON.stringify({ ok: true, ts: Date.now() }), {
          headers: { "Content-Type": "application/json", ...corsHeaders() },
        });
      default:
        return new Response("GitMob OAuth Worker — /auth  /callback  /health", {
          status: 200,
          headers: { "Content-Type": "text/plain" },
        });
    }
  },
};

// ── /auth：生成 GitHub OAuth URL 并重定向 ──
function handleAuth(url: URL, env: Env): Response {
  const state = crypto.randomUUID();
  const scope = "repo,user,delete_repo,admin:public_key,workflow";

  const ghUrl = new URL("https://github.com/login/oauth/authorize");
  ghUrl.searchParams.set("client_id", env.GITHUB_CLIENT_ID);
  ghUrl.searchParams.set("redirect_uri", `${url.origin}/callback`);
  ghUrl.searchParams.set("scope", scope);
  ghUrl.searchParams.set("state", state);

  return Response.redirect(ghUrl.toString(), 302);
}

// ── /callback：用 code 换 token，重定向回 App ──
async function handleCallback(url: URL, env: Env): Promise<Response> {
  const code  = url.searchParams.get("code");
  const error = url.searchParams.get("error");

  if (error || !code) {
    // 失败 → 跳回 App，带 error 参数
    const errDesc = url.searchParams.get("error_description") ?? "authorization_failed";
    return Response.redirect(`${APP_SCHEME}?error=${encodeURIComponent(errDesc)}`, 302);
  }

  try {
    // 向 GitHub 换 access_token
    const tokenRes = await fetch("https://github.com/login/oauth/access_token", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        client_id: env.GITHUB_CLIENT_ID,
        client_secret: env.GITHUB_CLIENT_SECRET,
        code,
      }),
    });

    if (!tokenRes.ok) {
      throw new Error(`GitHub token endpoint returned ${tokenRes.status}`);
    }

    const data = (await tokenRes.json()) as {
      access_token?: string;
      error?: string;
      error_description?: string;
      token_type?: string;
      scope?: string;
    };

    if (data.error || !data.access_token) {
      const desc = data.error_description ?? data.error ?? "token_exchange_failed";
      return Response.redirect(`${APP_SCHEME}?error=${encodeURIComponent(desc)}`, 302);
    }

    // 成功 → 带 token 跳回 App，App 接收后存储并登录
    return Response.redirect(
      `${APP_SCHEME}?token=${encodeURIComponent(data.access_token)}`,
      302
    );
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : "unknown_error";
    return Response.redirect(`${APP_SCHEME}?error=${encodeURIComponent(msg)}`, 302);
  }
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
}
