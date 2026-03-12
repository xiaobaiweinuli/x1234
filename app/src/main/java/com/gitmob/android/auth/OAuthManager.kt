package com.gitmob.android.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.gitmob.android.BuildConfig

object OAuthManager {
    private const val GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
    // repo + user + delete_repo + workflow 涵盖所有操作
    private const val SCOPES = "repo,user,delete_repo,admin:public_key,workflow"

    /**
     * 启动 GitHub OAuth 授权页面
     *
     * @param forceReauth true = 追加 prompt=consent（退出登录后强制用户重新选择账号授权），
     *                    false = GitHub 若已有活跃 session 则自动跳过授权页（首次登录行为）
     *
     * 关于「退出登录后直接成功进入」：这是 GitHub OAuth 的正常行为——
     * GitHub 端 session 仍存活，本 App 清除了本地 token 但 GitHub 侧 token 未撤销。
     * 追加 prompt=consent 可强制弹出授权确认页。
     * 若希望彻底撤销，需调用 GitHub API DELETE /applications/{client_id}/token（需 Basic Auth）。
     */
    fun launchOAuth(context: Context, forceReauth: Boolean = false) {
        val uri = Uri.parse(GITHUB_AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id",    BuildConfig.GITHUB_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("scope",        SCOPES)
            .appendQueryParameter("state",        (1..16).map { ('a'..'z').random() }.joinToString(""))
            .apply {
                // 强制显示授权确认页，防止静默跳过
                if (forceReauth) appendQueryParameter("prompt", "consent")
            }
            .build()
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }
}
