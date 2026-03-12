package com.gitmob.android

import android.app.Application
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.util.LogManager
import com.gitmob.android.util.LogLevel

class GitMobApp : Application() {
    lateinit var tokenStorage: TokenStorage
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStorage = TokenStorage(this)
        ApiClient.init(tokenStorage)
        LogManager.init(this, LogLevel.DEBUG)
        LogManager.i("App", "GitMob 启动")
        instance = this
    }

    companion object {
        lateinit var instance: GitMobApp
            private set
    }
}
