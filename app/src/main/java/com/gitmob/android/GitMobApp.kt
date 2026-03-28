package com.gitmob.android

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.RootManager
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.util.CrashHandler
import com.gitmob.android.util.LogManager
import com.gitmob.android.util.LogLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GitMobApp : Application() {
    lateinit var tokenStorage: TokenStorage private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 问题7: Root 恢复的完成信号。
     * FilePickerScreen 等调用方可 await() 等待恢复完成，避免竞态（isGranted 为 false 但开关是亮的）。
     * 无论成功还是失败，恢复流程结束后都会 complete(isGranted)。
     */
    val rootReady: CompletableDeferred<Boolean> = CompletableDeferred()

    private var lastNetworkType: Int? = null
    private lateinit var connectivityManager: ConnectivityManager

    override fun onCreate() {
        super.onCreate()
        // 1. 最先安装崩溃捕获
        CrashHandler.install(this)
        tokenStorage = TokenStorage(this)
        // 2. 从 DataStore 恢复日志等级
        appScope.launch {
            val levelIdx = tokenStorage.logLevel.first()
            val level = LogLevel.entries.getOrElse(levelIdx) { LogLevel.DEBUG }
            LogManager.init(this@GitMobApp, level)
        }
        ApiClient.init(tokenStorage)
        // 3. 初始化 Coil3（OkHttp 网络 + SVG 解码支持）
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .components {
                    add(OkHttpNetworkFetcherFactory())   // 使用 OkHttp 作为网络层
                    add(SvgDecoder.Factory())             // 显式启用 SVG 支持
                }
                .build()
        }
        // 4. Root 权限自动恢复（问题 6 + 7 + 8）
        appScope.launch {
            val rootEnabled = tokenStorage.rootEnabled.first()
            if (!rootEnabled) {
                rootReady.complete(false)
                return@launch
            }
            try {
                LogManager.i("App", "尝试自动恢复 root 权限")
                // 问题8: 注入上次探测的 su 执行模式缓存，跳过重复探测
                val cachedMode = tokenStorage.getSuExecModeCache()
                RootManager.injectSuExecModeCache(cachedMode)

                val granted = RootManager.requestRoot()

                // 问题8: 探测完成后，将最新模式写回 DataStore 缓存
                val newMode = RootManager.getSuExecModeForPersist()
                if (newMode >= 0) tokenStorage.setSuExecModeCache(newMode)

                if (!granted) {
                    // 问题6: 授权失败时同步将 DataStore.rootEnabled 置回 false，
                    // 避免开关显示"已启用"但功能静默失效
                    LogManager.w("App", "Root 权限恢复失败，同步关闭 rootEnabled")
                    tokenStorage.setRootEnabled(false)
                } else {
                    LogManager.i("App", "Root 权限恢复成功")
                }
                rootReady.complete(granted)
            } catch (e: Exception) {
                LogManager.e("App", "自动恢复 root 权限异常", e)
                // 同样同步关闭，避免状态不一致
                tokenStorage.setRootEnabled(false)
                rootReady.complete(false)
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
