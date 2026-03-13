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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// ─── 数据类 ───────────────────────────────────────────────────────────────────

data class FileEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val isGitRepo: Boolean = false,
    val size: Long = 0L,
)

/**
 * 书签：iconKey 使用 String 而非 ImageVector，Gson 可以正确序列化/反序列化。
 */
data class BookmarkPath(
    val label: String,
    val path: String,
    val iconKey: String = "Folder",          // Gson 友好：String 而不是 ImageVector
    val requiresRoot: Boolean = false,
    val isCustom: Boolean = false,
)

/** 根据 iconKey 解析出实际图标（空值/未知 key 均返回 Folder 兜底，防止 NPE） */
fun BookmarkPath.resolveIcon(): ImageVector = when (iconKey.orEmpty()) {
    "SdCard"         -> Icons.Default.SdCard
    "Download"       -> Icons.Default.Download
    "Terminal"       -> Icons.Default.Terminal
    "Storage"        -> Icons.Default.Storage
    "Memory"         -> Icons.Default.Memory
    "Settings"       -> Icons.Default.Settings
    "BookmarkAdded"  -> Icons.Default.BookmarkAdded
    else             -> Icons.Default.Folder
}

private fun defaultBookmarks(rootEnabled: Boolean): List<BookmarkPath> = buildList {
    add(BookmarkPath("sdcard",   Environment.getExternalStorageDirectory().absolutePath, "SdCard"))
    add(BookmarkPath("Download", "${Environment.getExternalStorageDirectory()}/Download", "Download"))
    // Termux home 跨 App 需要 root 才能访问
    add(BookmarkPath("Termux", "/data/data/com.termux/files/home", "Terminal", requiresRoot = true))
    if (rootEnabled) {
        add(BookmarkPath("/data",   "/data",   "Storage",  requiresRoot = true))
        add(BookmarkPath("/system", "/system", "Memory",   requiresRoot = true))
        add(BookmarkPath("/",       "/",       "Folder",   requiresRoot = true))
    }
}

enum class PickerMode { DIRECTORY, MULTI_FILE }

// ─── 工具 ─────────────────────────────────────────────────────────────────────

private fun isPrivilegedPath(path: String) =
    path.startsWith("/data") || path.startsWith("/system") ||
    path == "/"              || path.startsWith("/proc")   || path.startsWith("/dev")

private fun formatSize(bytes: Long) = when {
    bytes < 1024L         -> "${bytes}B"
    bytes < 1024L * 1024  -> "${bytes / 1024}KB"
    else                  -> "${bytes / 1024 / 1024}MB"
}

/** root 列目录：`ls -1Ap` 目录名带 `/` 后缀 */
private suspend fun listViaRoot(path: String): List<FileEntry> {
    val lines = RootManager.exec("ls -1Ap --color=never \"$path\" 2>/dev/null")
    return lines.filter { it.isNotBlank() }.mapNotNull { raw ->
        val isDir = raw.endsWith("/")
        val name = raw.trimEnd('/')
        if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
        val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
        val isGit = isDir && try {
            RootManager.exec("test -d \"$fullPath/.git\" && echo 1 || echo 0")
                .firstOrNull()?.trim() == "1"
        } catch (_: Exception) { false }
        FileEntry(path = fullPath, name = name, isDir = isDir, isGitRepo = isGit)
    }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
}

/** 普通权限列目录 */
private fun listNormal(path: String): List<FileEntry> {
    val files = File(path).listFiles() ?: return emptyList()
    return files
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .map { f ->
            FileEntry(
                path = f.absolutePath, name = f.name,
                isDir = f.isDirectory,
                isGitRepo = f.isDirectory && File(f, ".git").exists(),
                size = if (!f.isDirectory) f.length() else 0L,
            )
        }
}

// ─── 主 Composable ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    title: String = "选择目录",
    mode: PickerMode = PickerMode.DIRECTORY,
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    rootEnabled: Boolean = false,
    customBookmarks: List<BookmarkPath> = emptyList(),
    onAddBookmark: ((BookmarkPath) -> Unit)? = null,
    onRemoveBookmark: ((BookmarkPath) -> Unit)? = null,
    onConfirm: (selectedDir: String, selectedFiles: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalGmColors.current
    val scope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf(initialPath) }
    var entries    by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading    by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    var showSidebar      by remember { mutableStateOf(true) }
    var newDirDialog     by remember { mutableStateOf(false) }
    var newDirName       by remember { mutableStateOf("") }
    var bookmarkDialog   by remember { mutableStateOf(false) }
    var bookmarkLabel    by remember { mutableStateOf("") }

    val isPrivileged = remember(currentPath) { isPrivilegedPath(currentPath) }
    val accentColor  = if (isPrivileged && rootEnabled) Color(0xFFFF6B00) else Coral

    // 高对比文字颜色：Root 深棕背景→亮色；普通→主题色
    val onBg          : Color = if (isPrivileged && rootEnabled) Color(0xFFFFE0B0) else c.textPrimary
    val onBgSecondary : Color = if (isPrivileged && rootEnabled) Color(0xFFCCA870) else c.textSecondary
    val onBgTertiary  : Color = if (isPrivileged && rootEnabled) Color(0xFF997050) else c.textTertiary

    val allBookmarks = remember(rootEnabled, customBookmarks) {
        defaultBookmarks(rootEnabled) + customBookmarks
    }

    fun loadDir(path: String) {
        scope.launch {
            loading = true; errorMsg = null
            try {
                val needsRoot  = isPrivilegedPath(path)
                val forceRoot  = rootEnabled && RootManager.isGranted
                val result = when {
                    forceRoot -> listViaRoot(path)
                    forceRoot && !RootManager.isGranted -> {
                        if (RootManager.requestRoot()) listViaRoot(path) else listNormal(path)
                    }
                    needsRoot && !rootEnabled ->
                        throw RuntimeException("此目录需要 Root 权限。\n请在设置中启用 Root 模式。")
                    else -> {
                        val e = listNormal(path)
                        if (e.isEmpty() && path.contains("/storage/emulated") && !rootEnabled)
                            throw RuntimeException(
                                "无法读取此目录（Android 11+ 存储限制）。\n" +
                                "请授予「所有文件访问权限」，或开启 Root 模式。"
                            )
                        e
                    }
                }
                entries = result; currentPath = path
            } catch (e: Exception) { errorMsg = e.message }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { loadDir(currentPath) }

    val bgTint = if (isPrivileged && rootEnabled) Color(0xFF1A1000) else c.bgDeep

    // ── 新建目录弹窗 ──────────────────────────────────────────
    if (newDirDialog) {
        AlertDialog(
            onDismissRequest = { newDirDialog = false },
            containerColor = c.bgCard,
            title = { Text("新建文件夹", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
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
                Button(
                    onClick = {
                        if (newDirName.isNotBlank()) {
                            scope.launch {
                                val target = "$currentPath/$newDirName"
                                if (isPrivileged && rootEnabled && RootManager.isGranted)
                                    RootManager.exec("mkdir -p \"$target\"")
                                else File(target).mkdirs()
                                newDirName = ""; newDirDialog = false; loadDir(currentPath)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { newDirDialog = false }) { Text("取消", color = c.textSecondary) }
            },
        )
    }

    // ── 收藏弹窗 ──────────────────────────────────────────────
    if (bookmarkDialog) {
        AlertDialog(
            onDismissRequest = { bookmarkDialog = false },
            containerColor = c.bgCard,
            title = { Text("收藏当前目录", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = bookmarkLabel, onValueChange = { bookmarkLabel = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(File(currentPath).name.ifEmpty { "根目录" }, color = c.textTertiary) },
                    label = { Text("书签名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor, unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem,
                        focusedLabelColor = accentColor, unfocusedLabelColor = c.textTertiary,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lbl = bookmarkLabel.ifBlank { File(currentPath).name.ifEmpty { "/" } }
                        val bm = BookmarkPath(
                            label = lbl, path = currentPath,
                            iconKey = "BookmarkAdded",
                            requiresRoot = isPrivileged, isCustom = true,
                        )
                        onAddBookmark?.invoke(bm)
                        bookmarkLabel = ""; bookmarkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                ) { Text("收藏") }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkDialog = false }) { Text("取消", color = c.textSecondary) }
            },
        )
    }

    Scaffold(
        containerColor = bgTint,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = onBg)
                        Text(currentPath, fontSize = 11.sp, color = onBgTertiary,
                            fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = onBgSecondary)
                    }
                },
                actions = {
                    if (isPrivileged && rootEnabled) {
                        Box(Modifier.size(8.dp).background(Color(0xFFFF6B00), RoundedCornerShape(50)))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (onAddBookmark != null) {
                        IconButton(onClick = { bookmarkDialog = true }) {
                            Icon(Icons.Default.BookmarkAdd, null, tint = onBgSecondary)
                        }
                    }
                    IconButton(onClick = { showSidebar = !showSidebar }) {
                        Icon(Icons.Default.Menu, null, tint = onBgSecondary)
                    }
                    IconButton(onClick = { newDirDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, null, tint = accentColor)
                    }
                    TextButton(
                        onClick = {
                            val files = selected.keys.filter { selected[it] == true }
                            onConfirm(currentPath, files)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                    ) {
                        Text(
                            if (mode == PickerMode.DIRECTORY) "选择此处"
                            else "确认(${selected.size})",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgTint),
            )
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {

            // ── 左侧书签栏 ──────────────────────────────────────
            AnimatedVisibility(showSidebar,
                enter = slideInHorizontally() + fadeIn(),
                exit  = slideOutHorizontally() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.width(78.dp).fillMaxHeight().background(c.bgCard),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(6.dp))
                    allBookmarks.forEach { bm ->
                        BookmarkItem(
                            bm = bm,
                            selected = currentPath.startsWith(bm.path),
                            onClick = { loadDir(bm.path) },
                            onRemove = if (bm.isCustom && onRemoveBookmark != null)
                                { { onRemoveBookmark(bm) } } else null,
                            c = c,
                        )
                    }
                }
            }

            // ── 右侧文件列表 ────────────────────────────────────
            Column(Modifier.weight(1f).fillMaxHeight()) {
                PickerBreadcrumbs(currentPath, accentColor, onBgTertiary, onBg, ::loadDir, c)
                GmDivider()

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor,
                            modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    errorMsg != null -> Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Lock, null, tint = onBgTertiary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("无法访问此目录", fontSize = 14.sp, color = onBg, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(errorMsg!!, fontSize = 11.sp, color = onBgSecondary, lineHeight = 16.sp)
                    }
                    entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("空目录", fontSize = 13.sp, color = onBgTertiary)
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        if (currentPath != "/") {
                            item(key = "..") {
                                FileEntryRow(
                                    entry = FileEntry(File(currentPath).parent ?: "/", "..", true),
                                    isSelected = false, showCheckbox = false,
                                    textColor = onBg, textSecondary = onBgSecondary,
                                    textTertiary = onBgTertiary, accentColor = accentColor, c = c,
                                    onClick = { loadDir(File(currentPath).parent ?: "/") },
                                    onCheck = {},
                                )
                            }
                        }
                        items(entries, key = { it.path }) { entry ->
                            FileEntryRow(
                                entry = entry,
                                isSelected = selected[entry.path] == true,
                                showCheckbox = mode == PickerMode.MULTI_FILE && !entry.isDir,
                                textColor = onBg, textSecondary = onBgSecondary,
                                textTertiary = onBgTertiary, accentColor = accentColor, c = c,
                                onClick = {
                                    if (entry.isDir) loadDir(entry.path)
                                    else if (mode == PickerMode.MULTI_FILE)
                                        selected[entry.path] = !(selected[entry.path] ?: false)
                                },
                                onCheck = { checked -> selected[entry.path] = checked },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 子组件 ────────────────────────────────────────────────────────────────────

@Composable
private fun BookmarkItem(
    bm: BookmarkPath, selected: Boolean,
    onClick: () -> Unit, onRemove: (() -> Unit)?, c: GmColors,
) {
    val bg   = if (selected) CoralDim else Color.Transparent
    val tint = when {
        selected          -> Coral
        bm.requiresRoot   -> Color(0xFFFF6B00)
        else              -> c.textTertiary
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(bg)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(bm.resolveIcon(), null, tint = tint, modifier = Modifier.size(20.dp))
            Text(bm.label, fontSize = 9.sp, color = tint, maxLines = 1)
            if (bm.isCustom && onRemove != null) {
                Text("×", fontSize = 10.sp, color = RedColor,
                    modifier = Modifier.clickable { onRemove() }.padding(top = 1.dp))
            }
        }
    }
}

@Composable
private fun PickerBreadcrumbs(
    path: String, accentColor: Color, tertiaryColor: Color, primaryColor: Color,
    onNavigate: (String) -> Unit, c: GmColors,
) {
    val segments = buildList {
        var p = path
        while (p.isNotEmpty() && p != "/") { add(p); p = File(p).parent ?: break }
        if (!contains("/")) add("/")
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
                text = name, fontSize = 12.sp,
                color = if (isCurrent) accentColor else tertiaryColor,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onNavigate(seg) }.padding(horizontal = 3.dp),
            )
            if (i < segments.lastIndex)
                Text("/", fontSize = 11.sp, color = c.border, modifier = Modifier.padding(horizontal = 1.dp))
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry, isSelected: Boolean, showCheckbox: Boolean,
    textColor: Color, textSecondary: Color, textTertiary: Color,
    accentColor: Color, c: GmColors,
    onClick: () -> Unit, onCheck: (Boolean) -> Unit,
) {
    val rowBg = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(rowBg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = when {
            entry.name == ".." -> Icons.AutoMirrored.Filled.ArrowBack
            entry.isGitRepo    -> Icons.Default.AccountTree
            entry.isDir        -> Icons.Default.Folder
            else               -> Icons.Default.Description
        }
        val iconTint = when {
            entry.isGitRepo -> Green
            entry.isDir     -> Yellow
            else            -> textSecondary
        }
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 13.sp, color = textColor,
                fontWeight = FontWeight.Medium, maxLines = 1)
            when {
                entry.isGitRepo -> Text("Git 仓库", fontSize = 10.sp, color = Green,
                    modifier = Modifier.padding(top = 1.dp))
                !entry.isDir && entry.size > 0 ->
                    Text(formatSize(entry.size), fontSize = 10.sp, color = textTertiary)
                entry.name == ".." -> Text("返回上级", fontSize = 10.sp, color = textTertiary)
            }
        }
        if (entry.isDir && entry.name != "..") {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                tint = textTertiary, modifier = Modifier.size(14.dp))
        }
        if (showCheckbox) {
            Checkbox(
                checked = isSelected, onCheckedChange = onCheck,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor, uncheckedColor = c.border),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
