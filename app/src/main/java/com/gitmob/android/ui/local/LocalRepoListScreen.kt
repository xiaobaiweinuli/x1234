package com.gitmob.android.ui.local

import androidx.compose.animation.animateColorAsState
import com.gitmob.android.ui.filepicker.BookmarkPath
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitmob.android.local.LocalRepo
import com.gitmob.android.local.LocalRepoStatus
import com.gitmob.android.ui.common.*
import com.gitmob.android.ui.filepicker.FilePickerScreen
import com.gitmob.android.ui.filepicker.PickerMode
import com.gitmob.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRepoListScreen(
    rootEnabled: Boolean,
    vm: LocalRepoViewModel,
) {
    val c = LocalGmColors.current
    val state by vm.state.collectAsState()
    val wizardStep by vm.wizardStep.collectAsState()
    val customBookmarks by vm.customBookmarks.collectAsState()
    var newProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var showNewProjectPicker by remember { mutableStateOf(false) }
    var newProjectParentDir by remember { mutableStateOf("") }
    var wizardRepo by remember { mutableStateOf<LocalRepo?>(null) }

    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.clearToast() }
    }

    // 文件选择器覆盖层（导入目录）
    if (state.showFilePicker) {
        FilePickerScreen(
            title = "导入本地目录",
            mode = PickerMode.DIRECTORY,
            rootEnabled = rootEnabled,
            customBookmarks = customBookmarks,
            onAddBookmark    = { bm -> vm.addBookmark(bm) },
            onRemoveBookmark = { bm -> vm.removeBookmark(bm) },
            onConfirm = { path, _ -> vm.importDirectory(path) },
            onDismiss = vm::hideFilePicker,
        )
        return
    }

    // 克隆目标路径选择器
    if (state.showClonePicker) {
        FilePickerScreen(
            title = "选择克隆目标目录",
            mode = PickerMode.DIRECTORY,
            rootEnabled = rootEnabled,
            customBookmarks = customBookmarks,
            onAddBookmark    = { bm -> vm.addBookmark(bm) },
            onRemoveBookmark = { bm -> vm.removeBookmark(bm) },
            onConfirm = { path, _ -> vm.cloneRepo(state.pendingCloneUrl, path) },
            onDismiss = vm::hideClonePicker,
        )
        return
    }

    // 新建本地项目：先选父目录
    if (showNewProjectPicker) {
        FilePickerScreen(
            title = "选择项目父目录",
            mode = PickerMode.DIRECTORY,
            rootEnabled = rootEnabled,
            customBookmarks = customBookmarks,
            onAddBookmark    = { bm -> vm.addBookmark(bm) },
            onRemoveBookmark = { bm -> vm.removeBookmark(bm) },
            onConfirm = { path, _ ->
                newProjectParentDir = path
                showNewProjectPicker = false
                newProjectDialog = true
            },
            onDismiss = { showNewProjectPicker = false },
        )
        return
    }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            TopAppBar(
                title = { Text("本地仓库", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.textPrimary) },
                actions = {
                    // 新建本地项目
                    IconButton(onClick = { showNewProjectPicker = true }) {
                        Icon(Icons.Default.CreateNewFolder, null, tint = Coral)
                    }
                    // 导入已有目录
                    IconButton(onClick = vm::showFilePicker) {
                        Icon(Icons.Default.FolderOpen, null, tint = c.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgDeep),
            )
        },
        snackbarHost = {
            state.toast?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Snackbar(modifier = Modifier.padding(16.dp),
                        containerColor = c.bgCard, contentColor = c.textPrimary) { Text(it) }
                }
            }
        },
    ) { padding ->
        if (state.repos.isEmpty()) {
            EmptyLocalState(
                c = c,
                onImport = vm::showFilePicker,
                onNew = { showNewProjectPicker = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.repos, key = { it.id }) { repo ->
                    LocalRepoCard(
                        repo = repo, c = c,
                        onPush = { wizardRepo = repo; vm.startPushWizard(repo.id) },
                        onPull = { vm.pull(repo.id) },
                        onScan = { vm.scanRepo(repo.id) },
                        onRemove = { vm.removeRepo(repo.id) },
                    )
                }
            }
        }
    }

    // 推送向导
    wizardRepo?.let { repo ->
        if (wizardStep !is PushWizardStep.None) {
            GitOperationSheet(
                repo = repo,
                wizardStep = wizardStep,
                onPush = { url, msg, branch -> vm.executePush(repo.id, url, msg, branch) },
                onDismiss = { vm.dismissWizard(); wizardRepo = null },
            )
        }
    }

    // 新建项目对话框
    if (newProjectDialog) {
        AlertDialog(
            onDismissRequest = { newProjectDialog = false },
            containerColor = c.bgCard,
            title = { Text("新建本地项目", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("父目录：$newProjectParentDir",
                        fontSize = 11.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = newProjectName, onValueChange = { newProjectName = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("项目名称") },
                        placeholder = { Text("my-project", color = c.textTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Coral, unfocusedBorderColor = c.border,
                            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                            focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem,
                            focusedLabelColor = Coral, unfocusedLabelColor = c.textTertiary,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            vm.createLocalProject(newProjectParentDir, newProjectName)
                            newProjectDialog = false
                            newProjectName = ""
                        }
                    },
                    enabled = newProjectName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) { Text("创建 & Init") }
            },
            dismissButton = {
                TextButton(onClick = { newProjectDialog = false }) { Text("取消", color = c.textSecondary) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalRepoCard(
    repo: LocalRepo, c: GmColors,
    onPush: () -> Unit, onPull: () -> Unit,
    onScan: () -> Unit, onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val (statusColor, statusLabel) = when (repo.status) {
        LocalRepoStatus.GIT_INITIALIZED -> Green to "已初始化"
        LocalRepoStatus.PENDING_INIT    -> Yellow to "待初始化"
        LocalRepoStatus.WORKING         -> BlueColor to "处理中…"
        LocalRepoStatus.ERROR           -> RedColor to "错误"
    }

    // Root/敏感目录时 card 带微弱橙色边框
    val isSensitive = repo.path.startsWith("/data") || repo.path.startsWith("/system")
    val borderColor = if (isSensitive) Color(0x30FF6B00) else Color.Transparent

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(c.bgCard, RoundedCornerShape(14.dp))
            .run { if (isSensitive) padding(1.dp).background(borderColor, RoundedCornerShape(14.dp)) else this }
            .padding(14.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = if (isSensitive) Color(0xFFFF6B00) else Yellow,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.textPrimary)
                Text(repo.path, fontSize = 10.sp, color = c.textTertiary,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
            }
            GmBadge(statusLabel, statusColor.copy(alpha = 0.15f), statusColor)
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(c.bgCard)) {
                    DropdownMenuItem(
                        text = { Text("重新扫描", fontSize = 13.sp, color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, tint = c.textSecondary, modifier = Modifier.size(15.dp)) },
                        onClick = { onScan(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("从管理列表移除", fontSize = 13.sp, color = RedColor) },
                        leadingIcon = { Icon(Icons.Default.Remove, null, tint = RedColor, modifier = Modifier.size(15.dp)) },
                        onClick = { onRemove(); showMenu = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 分支 + 最后提交
        if (repo.status == LocalRepoStatus.GIT_INITIALIZED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (repo.currentBranch != null) {
                    Text(repo.currentBranch, fontSize = 11.sp, color = BlueColor,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(BlueDim, RoundedCornerShape(20.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp))
                }
                if (repo.lastCommit != null) {
                    Text(repo.lastCommit.take(40), fontSize = 11.sp, color = c.textTertiary, maxLines = 1)
                }
            }
            if (repo.remoteUrl != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    repo.remoteUrl.replace(Regex("https://[^@]+@"), "https://"),
                    fontSize = 10.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace, maxLines = 1,
                )
            }
        } else if (repo.error != null) {
            Text(repo.error.take(80), fontSize = 11.sp, color = RedColor,
                modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(12.dp))
        GmDivider()
        Spacer(Modifier.height(10.dp))

        // 操作按钮行
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repo.status == LocalRepoStatus.PENDING_INIT || repo.remoteUrl != null ||
                repo.status == LocalRepoStatus.GIT_INITIALIZED) {
                Button(
                    onClick = onPush,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (repo.status == LocalRepoStatus.PENDING_INIT) "上云" else "推送",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (repo.status == LocalRepoStatus.GIT_INITIALIZED && repo.remoteUrl != null) {
                OutlinedButton(
                    onClick = onPull,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(c.border)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp), tint = c.textSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("拉取", fontSize = 12.sp, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun EmptyLocalState(c: GmColors, onImport: () -> Unit, onNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.FolderOpen, null, tint = c.textTertiary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("暂无本地仓库", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
        Text("导入现有目录或新建项目开始管理本地代码", fontSize = 13.sp,
            color = c.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))

        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("新建本地项目", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(c.border))) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp), tint = c.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text("导入本地目录", color = c.textSecondary)
        }
    }
}
