package com.gitmob.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gitmob.android.auth.RootManager
import com.gitmob.android.auth.ThemeMode
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tokenStorage: TokenStorage,
    currentTheme: ThemeMode,
    rootEnabled: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onRootToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val c = LocalGmColors.current
    val scope = rememberCoroutineScope()
    val profile by tokenStorage.userProfile.collectAsState(initial = null)
    var rootCheckMsg by remember { mutableStateOf<String?>(null) }
    var rootChecking by remember { mutableStateOf(false) }

    // Root 开关逻辑：开启时检测权限
    fun handleRootToggle(enabled: Boolean) {
        if (!enabled) { onRootToggle(false); return }
        scope.launch {
            rootChecking = true
            val granted = RootManager.requestRoot()
            rootChecking = false
            if (granted) {
                onRootToggle(true)
                rootCheckMsg = "✓ Root 权限已授权"
            } else {
                rootCheckMsg = "⚠ 未获得 Root 权限，请确认设备已 Root 并授权"
            }
        }
    }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── 账号 ──────────────────────────────────────────
            if (profile != null) {
                SectionLabel("账号", c)
                SettingCard(c) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        AsyncImage(
                            model = profile!!.third, contentDescription = null,
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(c.bgItem),
                        )
                        Column {
                            Text(profile!!.second, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp, color = c.textPrimary)
                            Text("@${profile!!.first}", fontSize = 12.sp, color = c.textSecondary)
                        }
                    }
                }
            }

            // ── 外观 ──────────────────────────────────────────
            SectionLabel("外观", c)
            SettingCard(c) {
                val options = listOf(
                    ThemeMode.LIGHT  to "浅色模式",
                    ThemeMode.DARK   to "深色模式",
                    ThemeMode.SYSTEM to "跟随系统",
                )
                options.forEachIndexed { i, (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onThemeChange(mode) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 15.sp, color = c.textPrimary)
                        if (currentTheme == mode) {
                            Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (i < options.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border, thickness = 0.5.dp)
                }
            }

            // ── Root 权限 ─────────────────────────────────────
            SectionLabel("高级", c)
            SettingCard(c) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Root 图标，开启时带橙色
                    Box(
                        modifier = Modifier.size(36.dp)
                            .background(
                                if (rootEnabled) Color(0x25FF6B00) else c.bgItem,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Security, null,
                            tint = if (rootEnabled) Color(0xFFFF6B00) else c.textTertiary,
                            modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Root 模式", fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            if (rootEnabled) "已启用 · 可访问高权限目录"
                            else "访问 /data、/system 等目录",
                            fontSize = 12.sp, color = c.textSecondary,
                        )
                    }
                    if (rootChecking) {
                        CircularProgressIndicator(color = Color(0xFFFF6B00),
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = rootEnabled,
                            onCheckedChange = ::handleRootToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF6B00),
                                uncheckedThumbColor = c.textTertiary,
                                uncheckedTrackColor = c.bgItem,
                            ),
                        )
                    }
                }
                // Root 提示信息
                rootCheckMsg?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3000)
                        rootCheckMsg = null
                    }
                    Text(
                        msg, fontSize = 12.sp,
                        color = if (msg.startsWith("✓")) Green else Yellow,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            // ── 账号操作 ──────────────────────────────────────
            SectionLabel("账号操作", c)
            SettingCard(c) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(16.dp),
                ) {
                    Text("退出登录", fontSize = 15.sp, color = RedColor, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "GitMob · github.com/xiaobaiweinuli/GitMob-Android",
                fontSize = 11.sp, color = c.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, c: GmColors) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = c.textTertiary, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingCard(c: GmColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(14.dp)),
        content = content,
    )
}
