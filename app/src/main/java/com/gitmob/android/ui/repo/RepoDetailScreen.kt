package com.gitmob.android.ui.repo

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitmob.android.api.*
import com.gitmob.android.ui.common.*
import com.gitmob.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repoName: String,
    onBack: () -> Unit,
    onFileClick: (String, String, String, String) -> Unit,
    vm: RepoDetailViewModel = viewModel(factory = RepoDetailViewModel.factory(owner, repoName)),
) {
    val c = LocalGmColors.current
    val state by vm.state.collectAsState()
    val tabs = listOf("文件", "提交", "分支", "PR", "Issues")
    var showBranchDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }

    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.clearToast() }
    }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.repoName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.textPrimary)
                        Text(vm.owner, fontSize = 12.sp, color = c.textTertiary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary) }
                },
                actions = {
                    IconButton(onClick = vm::toggleStar) {
                        Icon(
                            if (state.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            null, tint = if (state.isStarred) Yellow else c.textSecondary,
                        )
                    }
                    IconButton(onClick = vm::loadAll) { Icon(Icons.Default.Refresh, null, tint = c.textSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgDeep),
            )
        },
        snackbarHost = {
            state.toast?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Snackbar(modifier = Modifier.padding(16.dp), containerColor = c.bgCard, contentColor = c.textPrimary) {
                        Text(it)
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        if (state.error != null && state.repo == null) { ErrorBox(state.error!!, vm::loadAll); return@Scaffold }
        val repo = state.repo ?: return@Scaffold

        Column(Modifier.padding(padding).fillMaxSize()) {
            // 仓库信息卡
            Column(
                modifier = Modifier.fillMaxWidth().background(c.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (!repo.description.isNullOrBlank()) {
                    Text(repo.description, fontSize = 13.sp, color = c.textSecondary, lineHeight = 20.sp)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatItem(Icons.Default.Star, "${repo.stars}", Yellow)
                    StatItem(Icons.Default.Share, "${repo.forks}", c.textSecondary)
                    StatItem(Icons.Default.Warning, "${repo.openIssues}", RedColor)
                    Spacer(Modifier.weight(1f))
                    if (!repo.language.isNullOrBlank()) {
                        Text(repo.language, fontSize = 11.sp, color = c.textTertiary,
                            modifier = Modifier.background(c.bgItem, RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (repo.private) GmBadge("私有", RedDim, RedColor)
                }
            }

            // 分支选择器
            Row(
                modifier = Modifier.fillMaxWidth().background(c.bgDeep)
                    .clickable { showBranchDialog = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.AccountTree, null, tint = BlueColor, modifier = Modifier.size(15.dp))
                Text(state.currentBranch, fontSize = 13.sp, color = BlueColor,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Text("${state.branches.size} 个分支", fontSize = 11.sp, color = c.textTertiary)
                Icon(Icons.Default.ExpandMore, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = state.tab, containerColor = c.bgDeep,
                contentColor = Coral, edgePadding = 16.dp,
                divider = { GmDivider() },
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = state.tab == i, onClick = { vm.setTab(i) },
                        text = { Text(label, fontSize = 13.sp) },
                        selectedContentColor = Coral, unselectedContentColor = c.textSecondary,
                    )
                }
            }

            when (state.tab) {
                0 -> FilesTab(state, c, onDirClick = { vm.loadContents(it) },
                    onFileClick = { path -> onFileClick(owner, repoName, path, state.currentBranch) },
                    onNavigateUp = vm::navigateUp)
                1 -> CommitsTab(state, c, onCommitClick = { vm.loadCommitDetail(it.sha) })
                2 -> BranchesTab(state, c, onSwitch = vm::switchBranch,
                    onNewBranch = { showNewBranchDialog = true },
                    onDelete = vm::deleteBranch, onRename = vm::renameBranch,
                    onSetDefault = vm::setDefaultBranch)
                3 -> PRTab(state.prs, c)
                4 -> IssuesTab(state.issues, c)
            }
        }
    }

    if (showBranchDialog) {
        BranchPickerDialog(
            branches = state.branches, current = state.currentBranch, c = c,
            onSelect = { vm.switchBranch(it); showBranchDialog = false },
            onDismiss = { showBranchDialog = false },
        )
    }
    if (showNewBranchDialog) {
        NewBranchDialog(c = c,
            onConfirm = { vm.createBranch(it); showNewBranchDialog = false },
            onDismiss = { showNewBranchDialog = false },
        )
    }
    // Commit 详情 Modal
    state.selectedCommit?.let { commit ->
        CommitDetailSheet(commit = commit, c = c, onDismiss = vm::clearCommitDetail)
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, color = LocalGmColors.current.textTertiary)
    }
}

// ─── Tabs ────────────────────────────────────────────────────────

@Composable
fun FilesTab(state: RepoDetailState, c: GmColors, onDirClick: (String) -> Unit, onFileClick: (String) -> Unit, onNavigateUp: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (state.currentPath.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp))
                        .clickable(onClick = onNavigateUp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textTertiary, modifier = Modifier.size(17.dp))
                    Text("..", fontSize = 13.sp, color = c.textTertiary)
                    Spacer(Modifier.weight(1f))
                    Text(state.currentPath, fontSize = 11.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (state.contentsLoading) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }}
        } else {
            items(state.contents, key = { it.path }) { content ->
                val isDir = content.type == "dir"
                Row(
                    modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(8.dp))
                        .clickable { if (isDir) onDirClick(content.path) else onFileClick(content.path) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (isDir) Icons.Default.Folder else Icons.Default.Description,
                        null, tint = if (isDir) Yellow else c.textSecondary, modifier = Modifier.size(17.dp))
                    Text(content.name, fontSize = 13.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (!isDir) Text(formatSize(content.size), fontSize = 11.sp, color = c.textTertiary)
                    if (isDir) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun CommitsTab(state: RepoDetailState, c: GmColors, onCommitClick: (GHCommit) -> Unit) {
    if (state.commits.isEmpty()) { EmptyBox("暂无提交记录"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.commits, key = { it.sha }) { commit ->
            Column(
                modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp))
                    .clickable { onCommitClick(commit) }.padding(12.dp),
            ) {
                Text(commit.commit.message.lines().first(), fontSize = 13.sp,
                    color = c.textPrimary, fontWeight = FontWeight.Medium, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (commit.author != null) AvatarImage(commit.author.avatarUrl, 20)
                    Text(commit.commit.author.name, fontSize = 11.sp, color = c.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(commit.shortSha, fontSize = 10.sp, color = Coral,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(CoralDim, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp))
                    Text(formatDate(commit.commit.author.date), fontSize = 11.sp, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
fun BranchesTab(
    state: RepoDetailState, c: GmColors,
    onSwitch: (String) -> Unit, onNewBranch: () -> Unit,
    onDelete: (String) -> Unit, onRename: (String, String) -> Unit,
    onSetDefault: (String) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf<GHBranch?>(null) }
    var showDeleteDialog by remember { mutableStateOf<GHBranch?>(null) }
    val defaultBranch = state.repo?.defaultBranch ?: ""

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Button(onClick = onNewBranch, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CoralDim, contentColor = Coral),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建分支", fontSize = 13.sp)
            }
        }
        items(state.branches, key = { it.name }) { branch ->
            val isCurrent = branch.name == state.currentBranch
            val isDefault = branch.name == defaultBranch
            var showMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AccountTree, null,
                    tint = if (isCurrent) Coral else c.textTertiary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(branch.name, fontSize = 13.sp,
                        color = if (isCurrent) Coral else c.textPrimary,
                        fontFamily = FontFamily.Monospace)
                    if (isDefault) Text("默认分支", fontSize = 10.sp, color = Green, modifier = Modifier.padding(top = 1.dp))
                }
                if (isCurrent) {
                    GmBadge("当前", CoralDim, Coral)
                    Spacer(Modifier.width(4.dp))
                }
                // 菜单
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(c.bgCard)) {
                        if (!isCurrent) {
                            DropdownMenuItem(
                                text = { Text("切换到此分支", fontSize = 13.sp, color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = BlueColor, modifier = Modifier.size(15.dp)) },
                                onClick = { onSwitch(branch.name); showMenu = false },
                            )
                        }
                        if (!isDefault) {
                            DropdownMenuItem(
                                text = { Text("设为默认分支", fontSize = 13.sp, color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Default.Star, null, tint = Yellow, modifier = Modifier.size(15.dp)) },
                                onClick = { onSetDefault(branch.name); showMenu = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("重命名", fontSize = 13.sp, color = c.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, tint = c.textSecondary, modifier = Modifier.size(15.dp)) },
                            onClick = { showRenameDialog = branch; showMenu = false },
                        )
                        if (!isDefault && !isCurrent) {
                            DropdownMenuItem(
                                text = { Text("删除分支", fontSize = 13.sp, color = RedColor) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = RedColor, modifier = Modifier.size(15.dp)) },
                                onClick = { showDeleteDialog = branch; showMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }

    showRenameDialog?.let { branch ->
        RenameBranchDialog(branch.name, c = c,
            onConfirm = { newName -> onRename(branch.name, newName); showRenameDialog = null },
            onDismiss = { showRenameDialog = null })
    }
    showDeleteDialog?.let { branch ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = c.bgCard,
            title = { Text("删除分支", color = c.textPrimary) },
            text = { Text("确认删除分支 \"${branch.name}\"？此操作不可撤销。", color = c.textSecondary) },
            confirmButton = {
                Button(onClick = { onDelete(branch.name); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消", color = c.textSecondary) } },
        )
    }
}

@Composable
fun PRTab(prs: List<GHPullRequest>, c: GmColors) {
    if (prs.isEmpty()) { EmptyBox("暂无 Pull Request"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(prs, key = { it.number }) { pr ->
            Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GmBadge("#${pr.number}", GreenDim, Green)
                    Text(pr.title, fontSize = 13.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pr.head.ref, fontSize = 11.sp, color = BlueColor, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(BlueDim, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
                    Text("→", fontSize = 11.sp, color = c.textTertiary)
                    Text(pr.base.ref, fontSize = 11.sp, color = c.textSecondary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Text(pr.user.login, fontSize = 11.sp, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
fun IssuesTab(issues: List<GHIssue>, c: GmColors) {
    val filtered = issues.filter { !it.isPR }
    if (filtered.isEmpty()) { EmptyBox("暂无 Issues"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(filtered, key = { it.number }) { issue ->
            Row(
                modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Circle, null,
                    tint = if (issue.state == "open") Green else RedColor, modifier = Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(issue.title, fontSize = 13.sp, color = c.textPrimary)
                    Text("#${issue.number} · ${issue.user.login}", fontSize = 11.sp,
                        color = c.textTertiary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ─── Commit Detail Modal ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailSheet(commit: GHCommitFull, c: GmColors, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(commit.shortSha, fontSize = 12.sp, color = Coral, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.background(CoralDim, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(Modifier.weight(1f))
                if (commit.stats != null) {
                    Text("+${commit.stats.additions}", fontSize = 12.sp, color = Green)
                    Text("-${commit.stats.deletions}", fontSize = 12.sp, color = RedColor)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(commit.commit.message, fontSize = 14.sp, color = c.textPrimary, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (commit.author != null) AvatarImage(commit.author.avatarUrl, 20)
                Text(commit.commit.author.name, fontSize = 12.sp, color = c.textSecondary)
                Text("·", color = c.textTertiary)
                Text(formatDate(commit.commit.author.date), fontSize = 12.sp, color = c.textTertiary)
            }
            Spacer(Modifier.height(14.dp))
            GmDivider()
            Spacer(Modifier.height(12.dp))

            // 文件列表
            commit.files?.let { files ->
                Text("变更文件 (${files.size})", fontSize = 12.sp, color = c.textSecondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                files.forEach { file ->
                    val (statusColor, statusLabel) = when(file.status) {
                        "added"    -> Green to "A"
                        "removed"  -> RedColor to "D"
                        "modified" -> Yellow to "M"
                        "renamed"  -> BlueColor to "R"
                        else       -> c.textTertiary to "?"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp))
                        Text(file.filename, fontSize = 12.sp, color = c.textPrimary,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("+${file.additions}/-${file.deletions}", fontSize = 11.sp, color = c.textTertiary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // 在 GitHub 查看（用于回滚等高级操作）
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(commit.htmlUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(c.border)),
            ) {
                Icon(Icons.Default.OpenInBrowser, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("在 GitHub 查看 / 回滚", fontSize = 14.sp, color = c.textSecondary)
            }
        }
    }
}

// ─── Dialogs ────────────────────────────────────────────────────────

@Composable
private fun BranchPickerDialog(branches: List<GHBranch>, current: String, c: GmColors,
    onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.bgCard,
        title = { Text("切换分支", color = c.textPrimary) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(branches) { b ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(b.name) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.AccountTree, null,
                            tint = if (b.name == current) Coral else c.textTertiary, modifier = Modifier.size(14.dp))
                        Text(b.name, fontSize = 14.sp, color = if (b.name == current) Coral else c.textPrimary,
                            modifier = Modifier.weight(1f))
                        if (b.name == current) Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) } },
    )
}

@Composable
private fun NewBranchDialog(c: GmColors, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.bgCard,
        title = { Text("新建分支", color = c.textPrimary) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text("feature/my-feature", color = c.textTertiary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Coral, unfocusedBorderColor = c.border,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                    focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem))
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) } },
    )
}

@Composable
private fun RenameBranchDialog(currentName: String, c: GmColors,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    val isValid = name.isNotBlank() && name != currentName
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.bgCard,
        title = { Text("重命名分支", color = c.textPrimary) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("新分支名") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Coral, unfocusedBorderColor = c.border,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                    focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem))
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Coral, disabledContainerColor = c.border)) {
                Text("重命名")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) } },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / 1024 / 1024}MB"
}

private fun formatDate(iso: String): String = try {
    iso.split("T").firstOrNull() ?: iso
} catch (_: Exception) { iso }
