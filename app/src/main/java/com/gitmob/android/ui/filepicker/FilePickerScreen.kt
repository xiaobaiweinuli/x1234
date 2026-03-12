package com.gitmob.android.ui.filepicker

import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitmob.android.auth.RootManager
import com.gitmob.android.ui.common.GmDivider
import com.gitmob.android.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/** 快速书签路径 */
data class QuickPath(val label: String, val path: String, val icon: ImageVector, val requiresRoot: Boolean = false)

private fun buildQuickPaths(rootEnabled: Boolean): List<QuickPath> = buildList {
    add(QuickPath("sdcard", Environment.getExternalStorageDirectory().absolutePath, Icons.Default.SdCard))
    add(QuickPath("Download", "${Environment.getExternalStorageDirectory()}/Download", Icons.Default.Download))
    add(QuickPath("Termux", "/data/data/com.termux/files/home", Icons.Default.Terminal, requiresRoot = false))
    if (rootEnabled) {
        add(QuickPath("/data", "/data", Icons.Default.Storage, requiresRoot = true))
        add(QuickPath("/system", "/system", Icons.Default.Settings, requiresRoot = true))
        add(QuickPath("/", "/", Icons.Default.Folder, requiresRoot = true))
    }
}

/** 文件选择模式 */
enum class PickerMode {
    /** 选择单个目录（返回路径）*/
    DIRECTORY,
    /** 多选文件（返回文件路径列表）*/
    MULTI_FILE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    title: String = "选择目录",
    mode: PickerMode = PickerMode.DIRECTORY,
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    rootEnabled: Boolean = false,
    onConfirm: (String, List<String>) -> Unit,   // (selectedDir, selectedFiles)
    onDismiss: () -> Unit,
) {
    val c = LocalGmColors.current
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(initialPath) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var showSidebar by remember { mutableStateOf(true) }
    var newDirDialog by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }

    // 是否在高权限目录
    val isRootDir = currentPath.startsWith("/data") || currentPath.startsWith("/system") ||
        currentPath == "/" || currentPath.startsWith("/proc")
    val accentColor = if (isRootDir && rootEnabled) Color(0xFFFF6B00) else Coral

    fun loadDir(path: String) {
        loading = true
        error = null
        scope.launch {
            try {
                val dir = File(path)
                val files = if (rootEnabled && RootManager.isGranted && isRootDir) {
                    // Root 列目录
                    val output = RootManager.exec("ls -la $path 2>/dev/null || ls $path")
                    // 将 ls 输出解析为 File 对象（简化）
                    output.filter { it.isNotBlank() && !it.startsWith("total") }
                        .mapNotNull { line ->
                            val parts = line.trim().split("\\s+".toRegex())
                            val name = parts.lastOrNull() ?: return@mapNotNull null
                            if (name == "." || name == "..") return@mapNotNull null
                            File(path, name)
                        }
                } else {
                    dir.listFiles()?.sortedWith(
                        compareBy({ !it.isDirectory }, { it.name.lowercase() })
                    ) ?: emptyList()
                }
                entries = files
                currentPath = path
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDir(currentPath) }

    // 颜色：Root 区域有橙色微弱背景提示
    val bgTint = if (isRootDir && rootEnabled) Color(0xFF1A1200) else c.bgDeep

    Scaffold(
        containerColor = bgTint,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.textPrimary)
                        Text(currentPath, fontSize = 11.sp, color = c.textTertiary,
                            fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary)
                    }
                },
                actions = {
                    // Root 模式指示灯
                    if (isRootDir && rootEnabled) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(Color(0xFFFF6B00))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showSidebar = !showSidebar }) {
                        Icon(Icons.Default.Menu, null, tint = c.textSecondary)
                    }
                    IconButton(onClick = { newDirDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, null, tint = accentColor)
                    }
                    // 确认按钮
                    TextButton(
                        onClick = {
                            val files = selected.keys.filter { selected[it] == true }
                            onConfirm(currentPath, files)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                    ) {
                        Text(if (mode == PickerMode.DIRECTORY) "选择此处" else "确认 (${selected.size})", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgTint),
            )
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            // ── 侧边快捷路径栏 ────────────────────────────────
            AnimatedVisibility(
                visible = showSidebar,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.width(88.dp).fillMaxHeight()
                        .background(c.bgCard),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    buildQuickPaths(rootEnabled).forEach { qp ->
                        QuickPathItem(
                            label = qp.label, icon = qp.icon,
                            selected = currentPath.startsWith(qp.path),
                            requiresRoot = qp.requiresRoot,
                            onClick = { loadDir(qp.path) },
                            c = c,
                        )
                    }
                }
            }

            // ── 主文件列表 ────────────────────────────────────
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // 面包屑
                Breadcrumbs(path = currentPath, accentColor = accentColor, onNavigate = ::loadDir, c = c)
                GmDivider()

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    error != null -> Column(Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = c.textTertiary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("无法访问此目录", fontSize = 14.sp, color = c.textSecondary)
                        Text(error ?: "", fontSize = 11.sp, color = c.textTertiary)
                    }
                    entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("空目录", fontSize = 13.sp, color = c.textTertiary)
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        // 返回上级
                        if (currentPath != "/") {
                            item(key = "..") {
                                FileRow(
                                    file = null, name = "..", isDir = true,
                                    isSelected = false, showCheckbox = false,
                                    accentColor = accentColor, c = c,
                                    onClick = {
                                        val parent = File(currentPath).parentFile?.absolutePath ?: "/"
                                        loadDir(parent)
                                    },
                                    onCheck = {},
                                )
                            }
                        }
                        items(entries, key = { it.absolutePath }) { file ->
                            val isDir = file.isDirectory
                            val isGitDir = isDir && File(file, ".git").exists()
                            FileRow(
                                file = file, name = file.name, isDir = isDir,
                                isGitDir = isGitDir,
                                isSelected = selected[file.absolutePath] == true,
                                showCheckbox = mode == PickerMode.MULTI_FILE && !isDir,
                                accentColor = accentColor, c = c,
                                onClick = {
                                    if (isDir) loadDir(file.absolutePath)
                                    else if (mode == PickerMode.MULTI_FILE) {
                                        selected[file.absolutePath] = !(selected[file.absolutePath] ?: false)
                                    }
                                },
                                onCheck = { checked -> selected[file.absolutePath] = checked },
                            )
                        }
                    }
                }
            }
        }
    }

    if (newDirDialog) {
        AlertDialog(
            onDismissRequest = { newDirDialog = false },
            containerColor = c.bgCard,
            title = { Text("新建文件夹", color = c.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newDirName, onValueChange = { newDirName = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("文件夹名称", color = c.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor, unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newDirName.isNotBlank()) {
                        val newDir = File(currentPath, newDirName)
                        if (rootEnabled && RootManager.isGranted && isRootDir) {
                            scope.launch { RootManager.exec("mkdir -p '$currentPath/$newDirName'") }
                        } else {
                            newDir.mkdirs()
                        }
                        loadDir(currentPath)
                        newDirName = ""
                        newDirDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { newDirDialog = false }) { Text("取消", color = c.textSecondary) }
            },
        )
    }
}

@Composable
private fun QuickPathItem(
    label: String, icon: ImageVector,
    selected: Boolean, requiresRoot: Boolean,
    onClick: () -> Unit, c: GmColors,
) {
    val bg = if (selected) CoralDim else Color.Transparent
    val tint = when {
        selected     -> Coral
        requiresRoot -> Color(0xFFFF6B00)
        else         -> c.textTertiary
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(bg)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.sp, color = tint, maxLines = 1)
    }
}

@Composable
private fun Breadcrumbs(path: String, accentColor: Color, onNavigate: (String) -> Unit, c: GmColors) {
    val segments = buildList {
        var p = path
        while (p != "/" && p.isNotEmpty()) {
            add(p)
            p = File(p).parent ?: break
        }
        add("/")
    }.reversed()

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(c.bgCard).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { i, seg ->
            val name = if (seg == "/") "/" else File(seg).name
            val isCurrent = seg == path
            Text(
                text = name,
                fontSize = 12.sp,
                color = if (isCurrent) accentColor else c.textTertiary,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onNavigate(seg) }.padding(horizontal = 4.dp),
            )
            if (i < segments.lastIndex) {
                Text("/", fontSize = 11.sp, color = c.border, modifier = Modifier.padding(horizontal = 1.dp))
            }
        }
    }
}

@Composable
private fun FileRow(
    file: File?, name: String, isDir: Boolean,
    isGitDir: Boolean = false,
    isSelected: Boolean, showCheckbox: Boolean,
    accentColor: Color, c: GmColors,
    onClick: () -> Unit, onCheck: (Boolean) -> Unit,
) {
    val rowBg = if (isSelected) accentColor.copy(alpha = 0.1f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(rowBg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 图标
        val icon = when {
            name == ".."  -> Icons.AutoMirrored.Filled.ArrowBack
            isGitDir      -> Icons.Default.AccountTree
            isDir         -> Icons.Default.Folder
            else          -> Icons.Default.Description
        }
        val iconTint = when {
            isGitDir -> Green
            isDir    -> Yellow
            else     -> c.textSecondary
        }
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))

        // 名称
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, color = c.textPrimary, maxLines = 1)
            if (isGitDir) {
                Text("Git 仓库", fontSize = 10.sp, color = Green, modifier = Modifier.padding(top = 1.dp))
            } else if (file != null && !isDir) {
                Text(formatFileSize(file.length()), fontSize = 10.sp, color = c.textTertiary)
            }
        }

        if (isDir && name != "..") {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
        if (showCheckbox) {
            Checkbox(
                checked = isSelected, onCheckedChange = onCheck,
                colors = CheckboxDefaults.colors(checkedColor = accentColor, uncheckedColor = c.border),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024      -> "${bytes}B"
    bytes < 1024*1024 -> "${bytes/1024}KB"
    else              -> "${bytes/1024/1024}MB"
}
