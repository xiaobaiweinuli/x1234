package com.gitmob.android.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.gitmob.android.BuildConfig

object OAuthManager {
    private const val GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
    private const val SCOPES = "repo,user,delete_repo,admin:public_key,workflow"

    fun launchOAuth(context: Context) {
        val uri = Uri.parse(GITHUB_AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.GITHUB_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", (1..16).map { ('a'..'z').random() }.joinToString(""))
            .build()
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }
}
