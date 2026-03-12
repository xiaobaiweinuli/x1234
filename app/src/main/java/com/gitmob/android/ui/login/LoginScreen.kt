package com.gitmob.android.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitmob.android.auth.OAuthManager

@Composable
fun LoginScreen(
    pendingToken: String?,
    onSuccess: () -> Unit,
    isReauth: Boolean = false,          // ← 退出登录后再进 = true，触发 forceReauth
    vm: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    LaunchedEffect(pendingToken) {
        when {
            pendingToken == null           -> Unit
            pendingToken.startsWith("ERROR:") -> vm.onOAuthError(pendingToken.removePrefix("ERROR:"))
            else                           -> vm.onTokenReceived(pendingToken)
        }
    }
    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onSuccess()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // ── Logo ──────────────────────────────────────────
            Box(
                modifier = Modifier.size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Star, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("GitMob", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                letterSpacing = (-1).sp)
            Spacer(Modifier.height(8.dp))
            Text("手机端 GitHub 管理工具", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))

            // ── 状态区 ────────────────────────────────────────
            when (val s = state) {
                is LoginUiState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("正在验证…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                is LoginUiState.Error -> {
                    // 区分 401（Token 失效）和其他错误
                    val is401 = s.msg.contains("401") || s.msg.contains("token") || s.msg.contains("失效")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (is401) "授权已失效" else "登录失败",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (is401)
                                    "你的访问令牌已被撤销或过期，请重新授权登录。"
                                else s.msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp, lineHeight = 18.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LoginButton(label = "重新授权登录") {
                        OAuthManager.launchOAuth(context, forceReauth = true)
                    }
                }
                else -> LoginButton(
                    label = if (isReauth) "重新授权登录" else "使用 GitHub 登录",
                ) {
                    OAuthManager.launchOAuth(context, forceReauth = isReauth)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "使用 GitHub OAuth 授权登录\n权限：repo · user · workflow",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center, lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun LoginButton(label: String = "使用 GitHub 登录", onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
