package com.gitmob.android

import android.app.Application
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.util.CrashHandler
import com.gitmob.android.util.LogManager
import com.gitmob.android.util.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GitMobApp : Application() {
    lateinit var tokenStorage: TokenStorage private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 1. 最先安装崩溃捕获（确保后续初始化崩溃也能被记录）
        CrashHandler.install(this)
        tokenStorage = TokenStorage(this)
        // 2. 从 DataStore 恢复日志等级
        appScope.launch {
            val levelIdx = tokenStorage.logLevel.first()
            val level = LogLevel.entries.getOrElse(levelIdx) { LogLevel.DEBUG }
            LogManager.init(this@GitMobApp, level)
        }
        ApiClient.init(tokenStorage)
        LogManager.i("App", "GitMob 启动")
        instance = this
    }

    companion object {
        lateinit var instance: GitMobApp private set
    }
}
