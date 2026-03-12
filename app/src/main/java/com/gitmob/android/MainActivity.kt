package com.gitmob.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.gitmob.android.auth.ThemeMode
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.nav.AppNavGraph
import com.gitmob.android.ui.theme.GitMobTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenStorage: TokenStorage

    /**
     * 关键：必须用 mutableStateOf，普通 var 赋值不触发 Compose 重组。
     * onNewIntent 设置新值 → Compose 自动重组 → LoginScreen.LaunchedEffect 触发。
     */
    private var pendingToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tokenStorage = (application as GitMobApp).tokenStorage

        // 等待 DataStore 初始化完成再撤销启动画面
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        lifecycleScope.launch {
            tokenStorage.accessToken.first()
            ready = true
        }

        // 处理冷启动时就带着 gitmob:// 链接的情况（极少见）
        handleDeepLink(intent)

        setContent {
            val themeMode by tokenStorage.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            GitMobTheme(themeMode = themeMode) {
                AppNavGraph(
                    tokenStorage  = tokenStorage,
                    // pendingToken 是 State，读取它的 Composable 会自动跟随变化重组
                    initialToken  = pendingToken,
                    onThemeChange = { mode ->
                        lifecycleScope.launch { tokenStorage.setThemeMode(mode) }
                    },
                )
            }
        }
    }

    /** Custom Tab 完成 OAuth 后，系统通过 singleTop + deep link 回调这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "gitmob" && uri.host == "oauth") {
            val token = uri.getQueryParameter("token")
            val error = uri.getQueryParameter("error")
            when {
                !token.isNullOrBlank() -> pendingToken = token   // 触发 Compose 重组
                !error.isNullOrBlank() -> pendingToken = "ERROR:$error"
            }
        }
    }
}
