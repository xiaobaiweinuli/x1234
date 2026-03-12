package com.gitmob.android

import android.app.Application
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.TokenStorage

class GitMobApp : Application() {
    lateinit var tokenStorage: TokenStorage
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStorage = TokenStorage(this)
        ApiClient.init(tokenStorage)
        instance = this
    }

    companion object {
        lateinit var instance: GitMobApp
            private set
    }
}
