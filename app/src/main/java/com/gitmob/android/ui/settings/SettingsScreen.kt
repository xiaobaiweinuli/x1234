package com.gitmob.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.gitmob.android.auth.RootManager
import com.gitmob.android.auth.ThemeMode
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.theme.*
import com.gitmob.android.util.LogLevel
import com.gitmob.android.util.LogManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import com.gitmob.android.R
import com.gitmob.android.BuildConfig
import androidx.compose.foundation.Image

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
    val context = LocalContext.current
    val profile by tokenStorage.userProfile.collectAsState(initial = null)
    val logLevelIdx by tokenStorage.logLevel.collectAsState(initial = 1)

    var rootCheckMsg by remember { mutableStateOf<String?>(null) }
    var rootChecking by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }

    // 检测 MANAGE_EXTERNAL_STORAGE
    val hasAllFilesAccess = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true
        )
    }
    val storagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccess.value =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            storagePermLauncher.launch(intent)
        }
    }

    fun handleRootToggle(enabled: Boolean) {
        if (!enabled) { onRootToggle(false); return }
        scope.launch {
            rootChecking = true
            val granted = RootManager.requestRoot()
            rootChecking = false
            if (granted) {
                onRootToggle(true)
                rootCheckMsg = "✓ Root 权限已授权"
                LogManager.i("Settings", "Root 权限授权成功")
            } else {
                rootCheckMsg = "⚠ 未获得 Root 权限，请确认设备已 Root 并授权"
                LogManager.w("Settings", "Root 授权失败")
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showLogViewer) {
        LogViewerDialog(
            tokenStorage = tokenStorage,
            currentLevelIdx = logLevelIdx,
            onLevelChange = { idx ->
                scope.launch { tokenStorage.setLogLevel(idx) }
                val level = LogLevel.entries.getOrElse(idx) { LogLevel.DEBUG }
                LogManager.setLevel(level)
            },
            onDismiss = { showLogViewer = false },
            c = c,
        )
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
            modifier = Modifier
                .padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── 账号 ──────────────────────────────────────────
            if (profile != null) {
                SLabel("账号", c)
                SCard(c) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            SLabel("外观", c)
            SCard(c) {
                listOf(ThemeMode.LIGHT to "浅色模式", ThemeMode.DARK to "深色模式", ThemeMode.SYSTEM to "跟随系统")
                    .forEachIndexed { i, (mode, label) ->
                        SRow(
                            title = label,
                            trailing = {
                                if (currentTheme == mode)
                                    Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(18.dp))
                            },
                            onClick = { onThemeChange(mode) }, c = c,
                        )
                        if (i < 2) SDivider(c)
                    }
            }

            // ── 高级 ──────────────────────────────────────────
            SLabel("高级", c)
            SCard(c) {

                // Root 开关
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SIconBox(
                        color = if (rootEnabled) Color(0x25FF6B00) else c.bgItem,
                        icon = Icons.Default.Security,
                        tint = if (rootEnabled) Color(0xFFFF6B00) else c.textTertiary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Root 模式", fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            if (rootEnabled) "已启用 · 可访问 /data /system 等目录"
                            else "允许访问受保护目录",
                            fontSize = 12.sp, color = c.textSecondary,
                        )
                    }
                    if (rootChecking) {
                        CircularProgressIndicator(color = Color(0xFFFF6B00),
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = rootEnabled, onCheckedChange = ::handleRootToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF6B00),
                                uncheckedThumbColor = c.textTertiary,
                                uncheckedTrackColor = c.bgItem,
                            ),
                        )
                    }
                }
                rootCheckMsg?.let { msg ->
                    LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); rootCheckMsg = null }
                    Text(msg, fontSize = 12.sp,
                        color = if (msg.startsWith("✓")) Green else Yellow,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
                }

                SDivider(c)

                // 全部文件访问权限
                SRow(
                    title = "文件访问权限",
                    subtitle = if (hasAllFilesAccess.value) "已授权 · 可访问完整存储空间"
                               else "⚠ 未授权 · 部分目录可能无法读取",
                    subtitleColor = if (hasAllFilesAccess.value) Green else Yellow,
                    leadingIcon = {
                        SIconBox(
                            color = if (hasAllFilesAccess.value) GreenDim else YellowDim,
                            icon = Icons.Default.FolderOpen,
                            tint = if (hasAllFilesAccess.value) Green else Yellow,
                        )
                    },
                    trailing = {
                        if (!hasAllFilesAccess.value) {
                            Text("去授权", fontSize = 12.sp, color = Coral, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { if (!hasAllFilesAccess.value) requestAllFilesAccess() },
                    c = c,
                )
            }

            // ── 日志 ──────────────────────────────────────────
            SLabel("调试", c)
            SCard(c) {
                SRow(
                    title = "日志查看器",
                    subtitle = "等级：${LogLevel.entries.getOrElse(logLevelIdx) { LogLevel.DEBUG }.name}",
                    leadingIcon = {
                        SIconBox(c.bgItem, Icons.Default.BugReport, BlueColor)
                    },
                    trailing = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                            tint = c.textTertiary, modifier = Modifier.size(16.dp))
                    },
                    onClick = { showLogViewer = true },
                    c = c,
                )
            }

            // ── 账号操作 ──────────────────────────────────────
            SLabel("账号操作", c)
            SCard(c) {
                SRow(
                    title = "退出登录",
                    titleColor = RedColor,
                    onClick = onLogout, c = c,
                )
            }

            // ── 关于 ──────────────────────────────────────────
            SLabel("关于", c)
            SCard(c) {
                SRow(
                    title = "关于 GitMob",
                    leadingIcon = { SIconBox(CoralDim, Icons.Default.Info, Coral) },
                    trailing = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                            tint = c.textTertiary, modifier = Modifier.size(16.dp))
                    },
                    onClick = { showAbout = true }, c = c,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("GitMob v${BuildConfig.VERSION_NAME} · AGPL-3.0",
                fontSize = 11.sp, color = c.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        }
    }
}

// ─── 关于弹窗 ─────────────────────────────────────────────────────────────────
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val c = LocalGmColors.current
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(c.bgCard, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App 图标（使用项目真实 PNG logo）
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "GitMob Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Text("GitMob", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.textPrimary)
            // 版本号从 BuildConfig 动态读取
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = c.textTertiary)

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            // 开发者
            AboutSection(title = "开发者", c = c) {
                DeveloperRow(
                    login = "xiaobaiweinuli",
                    displayName = "xiaobaiweinuli",
                    avatarUrl = "https://avatars.githubusercontent.com/u/94781176?v=4&s=80",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/xiaobaiweinuli")))
                    }, c = c,
                )
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            // 项目
            AboutSection(title = "项目", c = c) {
                AboutLinkRow(
                    icon = Icons.Default.Code,
                    label = "GitHub 仓库",
                    sub = "xiaobaiweinuli/GitMob-Android · AGPL-3.0",
                    url = "https://github.com/xiaobaiweinuli/GitMob-Android",
                    context = context, c = c,
                )
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            // 社区
            AboutSection(title = "社区", c = c) {
                AboutLinkRow(
                    icon = Icons.Default.Send,
                    label = "Telegram 群组",
                    sub = "t.me/MyResNav",
                    url = "https://t.me/MyResNav",
                    context = context, c = c,
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = Coral)
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, c: GmColors, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 10.sp, color = c.textTertiary,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        content()
    }
}

@Composable
private fun DeveloperRow(
    login: String, displayName: String, avatarUrl: String,
    onClick: () -> Unit, c: GmColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(c.bgItem, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = avatarUrl, contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(c.bgCard),
        )
        Column(Modifier.weight(1f)) {
            Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.textPrimary)
            Text("@$login", fontSize = 12.sp, color = c.textSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
            tint = c.textTertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun AboutLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, sub: String, url: String,
    context: android.content.Context, c: GmColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(c.bgItem, RoundedCornerShape(12.dp))
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = c.textPrimary)
            Text(sub, fontSize = 11.sp, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
            tint = c.textTertiary, modifier = Modifier.size(14.dp))
    }
}

// ─── 日志查看器弹窗 ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerDialog(
    tokenStorage: TokenStorage,
    currentLevelIdx: Int,
    onLevelChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    c: GmColors,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val logs = remember(refreshKey) { LogManager.recent(200) }
    val levels = LogLevel.entries.filter { it != LogLevel.NONE }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .background(c.bgCard, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, null, tint = BlueColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("日志查看器", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = c.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = { refreshKey++; LogManager.clearToday() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = RedColor, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { refreshKey++ }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // 等级选择
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("最低记录等级", fontSize = 11.sp, color = c.textTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.forEachIndexed { idx, level ->
                        val selected = currentLevelIdx == idx
                        val (bg, fg) = when (level) {
                            LogLevel.VERBOSE -> Color(0x1FA78BFA) to PurpleColor
                            LogLevel.DEBUG   -> BlueDim to BlueColor
                            LogLevel.INFO    -> GreenDim to Green
                            LogLevel.WARN    -> YellowDim to Yellow
                            LogLevel.ERROR   -> RedDim to RedColor
                            else -> c.bgItem to c.textTertiary
                        }
                        FilterChip(
                            selected = selected,
                            onClick  = { onLevelChange(idx) },
                            label    = { Text(level.name, fontSize = 10.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bg,
                                selectedLabelColor     = fg,
                            ),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            // 日志列表
            LazyColumn(
                modifier = Modifier.weight(1f)
                    .background(Color(0xFF080C12), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                reverseLayout = false,
            ) {
                if (logs.isEmpty()) {
                    item { Text("暂无日志", fontSize = 12.sp, color = Color(0xFF5C6580),
                        modifier = Modifier.padding(8.dp)) }
                } else {
                    items(logs) { entry ->
                        val color = when (entry.level) {
                            LogLevel.VERBOSE -> PurpleColor
                            LogLevel.DEBUG   -> BlueColor
                            LogLevel.INFO    -> Green
                            LogLevel.WARN    -> Yellow
                            LogLevel.ERROR   -> RedColor
                            else -> Color(0xFF9BA3BA)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${entry.time} [${entry.level.tag}]",
                                fontSize = 10.sp, color = Color(0xFF5C6580),
                                fontFamily = FontFamily.Monospace)
                            Text("${entry.tag}: ${entry.msg}",
                                fontSize = 10.sp, color = color,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp)
                        }
                    }
                }
            }

            // 日志文件信息
            val logFile = LogManager.currentLogFile()
            Text(
                "日志文件：${logFile?.absolutePath ?: "未初始化"}",
                fontSize = 10.sp, color = c.textTertiary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = Coral)
            }
        }
    }
}

// ─── 通用子组件 ────────────────────────────────────────────────────────────────

@Composable
fun SLabel(text: String, c: GmColors) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = c.textTertiary, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
fun SCard(c: GmColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(14.dp)),
        content = content,
    )
}

@Composable
fun SDivider(c: GmColors) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
        color = c.border, thickness = 0.5.dp)
}

@Composable
fun SRow(
    title: String,
    titleColor: Color? = null,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    c: GmColors,
) {
    val localC = LocalGmColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingIcon?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = titleColor ?: localC.textPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp,
                color = subtitleColor ?: localC.textSecondary)
        }
        trailing?.invoke()
    }
}

@Composable
fun SIconBox(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(36.dp).background(color, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
