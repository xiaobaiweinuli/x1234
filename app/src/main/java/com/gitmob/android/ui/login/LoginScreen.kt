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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitmob.android.auth.OAuthManager
import com.gitmob.android.ui.theme.*

@Composable
fun LoginScreen(
    pendingToken: String?,
    onSuccess: () -> Unit,
    vm: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    LaunchedEffect(pendingToken) {
        if (pendingToken != null) vm.onTokenReceived(pendingToken)
    }

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BgDeep, Color(0xFF0A1628)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(CoralDim, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(44.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "GitMob",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Coral,
                letterSpacing = (-1).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "手机端 GitHub 管理工具",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            when (state) {
                is LoginUiState.Loading -> {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("正在验证...", color = TextTertiary, fontSize = 13.sp)
                }
                is LoginUiState.Error -> {
                    Text(
                        text = (state as LoginUiState.Error).msg,
                        color = RedColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    LoginButton { OAuthManager.launchOAuth(context) }
                }
                else -> {
                    LoginButton { OAuthManager.launchOAuth(context) }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "使用 GitHub OAuth 授权登录\n权限：repo · user · workflow",
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun LoginButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Coral),
    ) {
        Text(
            text = "使用 GitHub 登录",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
