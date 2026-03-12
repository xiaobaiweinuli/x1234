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
    private var pendingToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tokenStorage = (application as GitMobApp).tokenStorage
        var startDestChecked = false
        splash.setKeepOnScreenCondition { !startDestChecked }
        lifecycleScope.launch {
            tokenStorage.accessToken.first()
            startDestChecked = true
        }

        handleIntent(intent)

        setContent {
            val themeMode by tokenStorage.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            GitMobTheme(themeMode = themeMode) {
                AppNavGraph(
                    tokenStorage  = tokenStorage,
                    initialToken  = pendingToken,
                    onThemeChange = { mode ->
                        lifecycleScope.launch { tokenStorage.setThemeMode(mode) }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "gitmob" && uri.host == "oauth") {
            pendingToken = uri.getQueryParameter("token")
        }
    }
}
