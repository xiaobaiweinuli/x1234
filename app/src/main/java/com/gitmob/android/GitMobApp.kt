package com.gitmob.android

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.RootManager
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
    
    private var lastNetworkType: Int? = null
    private lateinit var connectivityManager: ConnectivityManager

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
        // 3. 如果之前启用了 root 权限，自动尝试恢复
        appScope.launch {
            val rootEnabled = tokenStorage.rootEnabled.first()
            if (rootEnabled) {
                try {
                    LogManager.i("App", "尝试自动恢复 root 权限")
                    RootManager.requestRoot()
                } catch (e: Exception) {
                    LogManager.e("App", "自动恢复 root 权限失败", e)
                }
            }
        }
        // 4. 初始化网络状态监听
        initNetworkMonitor()
        LogManager.i("App", "GitMob 启动")
        instance = this
    }

    /**
     * 初始化网络状态监听
     * 当网络类型发生变化时（Wi-Fi ↔ 蜂窝数据），清理 OkHttp 连接池
     */
    private fun initNetworkMonitor() {
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        
        // 获取初始网络类型
        lastNetworkType = getCurrentNetworkType()
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                checkNetworkChange()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                LogManager.i("App", "网络连接断开")
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                checkNetworkChange()
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        LogManager.i("App", "网络状态监听已初始化")
    }

    /**
     * 检查网络类型是否发生变化
     */
    private fun checkNetworkChange() {
        val currentType = getCurrentNetworkType()
        if (lastNetworkType != null && lastNetworkType != currentType) {
            LogManager.i("App", "网络类型变化: ${getNetworkTypeName(lastNetworkType)} → ${getNetworkTypeName(currentType)}")
            ApiClient.clearConnectionPool()
        }
        lastNetworkType = currentType
    }

    /**
     * 获取当前网络类型
     * @return 0=无网络, 1=Wi-Fi, 2=蜂窝数据
     */
    private fun getCurrentNetworkType(): Int {
        val network = connectivityManager.activeNetwork ?: return 0
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return 0
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
            else -> 0
        }
    }

    /**
     * 获取网络类型名称（用于日志）
     */
    private fun getNetworkTypeName(type: Int?): String {
        return when (type) {
            0 -> "无网络"
            1 -> "Wi-Fi"
            2 -> "蜂窝数据"
            else -> "未知"
        }
    }

    companion object {
        lateinit var instance: GitMobApp private set
    }
}
