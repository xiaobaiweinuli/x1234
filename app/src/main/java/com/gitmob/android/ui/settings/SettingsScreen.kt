package com.gitmob.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitmob.android.auth.ThemeMode
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.theme.*
import com.gitmob.android.ui.theme.LocalGmColors
import com.gitmob.android.ui.theme.GmColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tokenStorage: TokenStorage,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val c = LocalGmColors.current
    val profile by tokenStorage.userProfile.collectAsState(initial = null)

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = c.textPrimaryVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.bgDeep,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 当前账号
            if (profile != null) {
                SectionLabel("账号")
                SettingCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        coil.compose.AsyncImage(
                            model = profile!!.third,
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    c.bgCardVariant,
                                    RoundedCornerShape(50),
                                ),
                        )
                        Column {
                            Text(profile!!.second, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp, color = c.textPrimary)
                            Text("@${profile!!.first}", fontSize = 12.sp, color = c.textSecondary)
                        }
                    }
                }
            }

            // 主题设置
            SectionLabel("外观")
            SettingCard {
                val options = listOf(
                    ThemeMode.LIGHT  to "浅色模式",
                    ThemeMode.DARK   to "深色模式",
                    ThemeMode.SYSTEM to "跟随系统",
                )
                options.forEachIndexed { i, (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(mode) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 15.sp, color = c.textPrimary)
                        if (currentTheme == mode) {
                            Icon(Icons.Default.Check, null,
                                tint = Coral, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (i < options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = c.border,
                            thickness = 0.5.dp,
                        )
                    }
                }
            }

            // 退出登录
            SectionLabel("账号操作")
            SettingCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLogout() }
                        .padding(16.dp),
                ) {
                    Text("退出登录", fontSize = 15.sp, color = RedColor,
                        fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "GitMob · github.com/xiaobaiweinuli/GitMob-Android",
                fontSize = 11.sp,
                color = c.textPrimaryVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.textPrimaryVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgCard, RoundedCornerShape(14.dp)),
        content = content,
    )
}
