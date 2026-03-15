package com.gitmob.android.ui.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitmob.android.api.*
import com.gitmob.android.ui.common.*
import com.gitmob.android.ui.theme.*
import com.gitmob.android.util.LogManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.draw.clip

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
    val tabs = listOf("文件", "提交", "分支", "操作", "发行版", "PR", "Issues")
    var showBranchDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showVisibilityDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                    IconButton(onClick = { vm.loadAll(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, null, tint = c.textSecondary)
                    }
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Settings, null, tint = c.textSecondary)
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                            modifier = Modifier.background(c.bgCard),
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名", fontSize = 14.sp, color = c.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DriveFileRenameOutline,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("编辑信息", fontSize = 14.sp, color = c.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    showEditDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("分享", fontSize = 14.sp, color = c.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Share,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    state.repo?.let { repo ->
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, repo.fullName)
                                            putExtra(Intent.EXTRA_TEXT, repo.htmlUrl)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "分享仓库"),
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.repo?.private == true) "设为公开" else "设为私有",
                                        fontSize = 14.sp,
                                        color = c.textPrimary,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    showVisibilityDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("转移", fontSize = 14.sp, color = c.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    showTransferDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除", fontSize = 14.sp, color = RedColor) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = RedColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
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
                0 -> FilesTab(state, c,
                    onDirClick = { vm.loadContents(it) },
                    onFileClick = { path -> onFileClick(owner, repoName, path, state.currentBranch) },
                    onNavigateUp = vm::navigateUp,
                    onRefresh = { vm.loadContents(state.currentPath, forceRefresh = true) },
                )
                1 -> CommitsTab(state, c, onCommitClick = { vm.loadCommitDetail(it.sha) })
                2 -> BranchesTab(state, c, onSwitch = vm::switchBranch,
                    onNewBranch = { showNewBranchDialog = true },
                    onDelete = vm::deleteBranch, onRename = vm::renameBranch,
                    onSetDefault = vm::setDefaultBranch)
                3 -> ActionsTab(state, c, vm, owner, repoName)
                4 -> ReleasesTab(state.releases, c)
                5 -> PRTab(state.prs, c)
                6 -> IssuesTab(state.issues, c, vm)
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
    val repoForDialogs = state.repo
    if (showRenameDialog && repoForDialogs != null) {
        RepoRenameDialog(
            currentName = repoForDialogs.name,
            owner = repoForDialogs.owner.login,
            c = c,
            onConfirm = { newName ->
                vm.renameRepo(newName) { onBack() }
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
    if (showEditDialog && repoForDialogs != null) {
        RepoEditDialog(
            repo = repoForDialogs,
            c = c,
            onConfirm = { desc, site, topics ->
                vm.editRepo(desc, site, topics)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }
    if (showDeleteDialog && repoForDialogs != null) {
        RepoDeleteDialog(
            repoName = repoForDialogs.name,
            owner = repoForDialogs.owner.login,
            c = c,
            onConfirm = {
                vm.deleteRepo { onBack() }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
    if (showVisibilityDialog && repoForDialogs != null) {
        RepoVisibilityDialog(
            repo = repoForDialogs,
            c = c,
            onConfirm = { makePrivate ->
                vm.updateVisibility(makePrivate)
                showVisibilityDialog = false
            },
            onDismiss = { showVisibilityDialog = false },
        )
    }
    if (showTransferDialog) {
        RepoTransferDialog(
            owner = vm.owner,
            repoName = vm.repoName,
            userLogin = state.userLogin,
            userAvatar = state.userAvatar,
            orgs = state.userOrgs,
            c = c,
            onConfirm = { target, newName ->
                vm.transferRepo(target, newName) { onBack() }
                showTransferDialog = false
            },
            onDismiss = { showTransferDialog = false },
        )
    }
    // Commit 详情 Modal
    state.selectedCommit?.let { commit ->
        CommitDetailSheet(commit = commit, c = c, vm = vm, onDismiss = vm::clearCommitDetail)
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
@OptIn(ExperimentalMaterial3Api::class)
fun FilesTab(
    state: RepoDetailState,
    c: GmColors,
    onDirClick: (String) -> Unit,
    onFileClick: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (state.currentPath.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(c.bgCard, RoundedCornerShape(8.dp))
                        .clickable(onClick = onNavigateUp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = c.textTertiary, modifier = Modifier.size(17.dp))
                    Text("..", fontSize = 13.sp, color = c.textTertiary)
                    Spacer(Modifier.weight(1f))
                    Text(state.currentPath, fontSize = 11.sp,
                        color = c.textTertiary, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (state.contentsLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else {
            items(state.contents, key = { it.path }) { content ->
                val isDir = content.type == "dir"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(c.bgCard, RoundedCornerShape(8.dp))
                        .clickable { if (isDir) onDirClick(content.path) else onFileClick(content.path) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        if (isDir) Icons.Default.Folder else Icons.Default.Description,
                        null,
                        tint     = if (isDir) Yellow else c.textSecondary,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(content.name, fontSize = 13.sp, color = c.textPrimary,
                        modifier = Modifier.weight(1f))
                    if (!isDir) Text(formatSize(content.size), fontSize = 11.sp, color = c.textTertiary)
                    if (isDir) Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                        tint = c.textTertiary, modifier = Modifier.size(14.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesTab(issues: List<GHIssue>, c: GmColors, vm: RepoDetailViewModel) {
    val filtered = issues.filter { !it.isPR }
    if (filtered.isEmpty()) { EmptyBox("暂无 Issues"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(filtered, key = { it.number }) { issue ->
            SwipeableIssueCard(
                issue = issue,
                c = c,
                onDelete = { vm.deleteIssue(issue.number) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableIssueCard(
    issue: GHIssue,
    c: GmColors,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) RedColor else c.border,
                label = "swipe_bg",
            )
            Box(
                modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 20.dp),
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("删除", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(top = 3.dp))
                }
            }
        },
    ) {
        IssueCardContent(issue = issue, c = c)
    }

    if (showDeleteDialog) {
        DeleteIssueDialog(
            issueNumber = issue.number,
            title = issue.title,
            onConfirm = { onDelete(); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false },
            c = c,
        )
    }
}

@Composable
private fun IssueCardContent(issue: GHIssue, c: GmColors) {
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

@Composable
private fun DeleteIssueDialog(
    issueNumber: Int,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    c: GmColors,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        icon = {
            Icon(Icons.Default.DeleteForever, null, tint = RedColor, modifier = Modifier.size(28.dp))
        },
        title = { Text("删除 Issue", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bgItem, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Report,
                        null,
                        tint = RedColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "#$issueNumber",
                        fontSize = 13.sp,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    title,
                    fontSize = 13.sp,
                    color = c.textSecondary,
                )
                Text(
                    "此操作不可撤销。确认要删除这个 Issue 吗？",
                    fontSize = 13.sp,
                    color = c.textTertiary,
                    lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = RedColor),
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

// ─── Releases Tab ─────────────────────────────────────────────────────

@Composable
fun ReleasesTab(releases: List<GHRelease>, c: GmColors) {
    if (releases.isEmpty()) {
        EmptyBox("暂无发行版")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(releases, key = { it.id }) { release ->
            ReleaseCard(release = release, c = c)
        }
    }
}

@Composable
fun ReleaseCard(release: GHRelease, c: GmColors) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgCard, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (release.prerelease) {
                GmBadge("预发布", YellowDim, Yellow)
            }
            if (release.draft) {
                GmBadge("草稿", c.textTertiary, c.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(release.tagName, fontSize = 12.sp, color = Coral, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Text(release.name ?: release.tagName, fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
        release.body?.let { body ->
            Spacer(Modifier.height(6.dp))
            Text(
                body.take(200) + if (body.length > 200) "…" else "",
                fontSize = 12.sp,
                color = c.textSecondary,
                maxLines = 3
            )
        }
        if (release.assets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            GmDivider()
            Spacer(Modifier.height(8.dp))
            Text("附件 (${release.assets.size})", fontSize = 11.sp, color = c.textTertiary)
            Spacer(Modifier.height(4.dp))
            release.assets.forEach { asset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(asset.downloadUrl))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AttachFile, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(asset.name, fontSize = 12.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(formatSize(asset.size), fontSize = 11.sp, color = c.textTertiary)
                }
            }
        }
    }
}

// ─── Actions Tab ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsTab(
    state: RepoDetailState,
    c: GmColors,
    vm: RepoDetailViewModel,
    owner: String,
    repoName: String,
) {
    var showDispatchDialog by remember { mutableStateOf<GHWorkflow?>(null) }
    var showDeleteDialog by remember { mutableStateOf<GHWorkflowRun?>(null) }
    var workflowsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(showDispatchDialog) {
        showDispatchDialog?.let { workflow ->
            vm.loadWorkflowInputs(workflow.path)
        } ?: vm.clearWorkflowInputs()
    }

    if (state.selectedWorkflow != null) {
        // 工作流详情页面
        Column(Modifier.fillMaxSize()) {
            // 顶部返回栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    null,
                    tint = c.textPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { vm.clearSelectedWorkflow() }
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        state.selectedWorkflow!!.name,
                        fontSize = 16.sp,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.selectedWorkflow!!.path,
                        fontSize = 11.sp,
                        color = c.textTertiary
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.selectedWorkflow!!.state == "active") {
                    Button(
                        onClick = { showDispatchDialog = state.selectedWorkflow },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("运行")
                    }
                }
            }
            GmDivider()

            // 运行记录列表
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (state.workflowRuns.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无运行记录", fontSize = 13.sp, color = c.textTertiary)
                        }
                    }
                } else {
                    items(state.workflowRuns, key = { it.id }) { run ->
                        WorkflowRunItem(
                            run = run,
                            c = c,
                            onClick = { vm.selectWorkflowRun(run) },
                            onRerun = { vm.rerunWorkflow(run.id) },
                            onCancel = { vm.cancelWorkflow(run.id) },
                            onDelete = { showDeleteDialog = run }
                        )
                    }
                }
            }
        }
    } else {
        // 默认页面：显示所有工作流 + 所有运行记录
        Column(Modifier.fillMaxSize()) {
            // 工作流列表
            if (state.workflows.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("工作流", fontSize = 12.sp, color = c.textTertiary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    val visibleWorkflows = if (workflowsExpanded || state.workflows.size <= 2) {
                        state.workflows
                    } else {
                        state.workflows.take(2)
                    }
                    visibleWorkflows.forEach { workflow ->
                        WorkflowItem(
                            workflow = workflow,
                            c = c,
                            onDispatch = { showDispatchDialog = workflow },
                            onClick = { vm.selectWorkflow(workflow) },
                            onRefresh = { vm.loadWorkflows() }
                        )
                    }
                    if (state.workflows.size > 2) {
                        TextButton(
                            onClick = { workflowsExpanded = !workflowsExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (workflowsExpanded) "收起" else "显示全部 (${state.workflows.size})",
                                color = Coral
                            )
                        }
                    }
                }
                GmDivider()
            }

            // 运行记录列表
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Text("运行记录", fontSize = 12.sp, color = c.textTertiary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                }
                if (state.workflowRuns.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无运行记录", fontSize = 13.sp, color = c.textTertiary)
                        }
                    }
                } else {
                    items(state.workflowRuns, key = { it.id }) { run ->
                        WorkflowRunItem(
                            run = run,
                            c = c,
                            onClick = { vm.selectWorkflowRun(run) },
                            onRerun = { vm.rerunWorkflow(run.id) },
                            onCancel = { vm.cancelWorkflow(run.id) },
                            onDelete = { showDeleteDialog = run }
                        )
                    }
                }
            }
        }
    }

    // 触发工作流对话框
    showDispatchDialog?.let { workflow ->
        DispatchWorkflowDialog(
            workflow = workflow,
            currentBranch = state.currentBranch,
            branches = state.branches.map { it.name },
            inputs = state.workflowInputs,
            inputsLoading = state.workflowInputsLoading,
            c = c,
            onConfirm = { ref, inputs ->
                vm.dispatchWorkflow(workflow.id, ref, inputs)
                showDispatchDialog = null
            },
            onDismiss = { showDispatchDialog = null }
        )
    }

    // 删除运行记录对话框
    showDeleteDialog?.let { run ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = c.bgCard,
            title = { Text("删除运行记录", color = c.textPrimary) },
            text = { Text("确认删除此运行记录？此操作不可撤销。", color = c.textSecondary) },
            confirmButton = {
                Button(onClick = {
                    vm.deleteWorkflowRun(run.id)
                    showDeleteDialog = null
                }, colors = ButtonDefaults.buttonColors(containerColor = RedColor)) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消", color = c.textSecondary)
                }
            }
        )
    }

    // 工作流运行详情 Modal
    state.selectedWorkflowRun?.let { run ->
        WorkflowRunDetailSheet(
            run = run,
            jobs = state.workflowJobs,
            c = c,
            vm = vm,
            onDismiss = { vm.clearSelectedWorkflowRun() },
            onRerun = { vm.rerunWorkflow(run.id) },
            onCancel = { vm.cancelWorkflow(run.id) }
        )
    }
}

@Composable
fun WorkflowItem(
    workflow: GHWorkflow,
    c: GmColors,
    onDispatch: () -> Unit,
    onClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgCard, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (workflow.state == "active") Icons.Default.PlayArrow else Icons.Default.Pause,
            null,
            tint = if (workflow.state == "active") Green else c.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(workflow.name, fontSize = 13.sp, color = c.textPrimary)
            Text(workflow.path, fontSize = 11.sp, color = c.textTertiary)
        }
        if (workflow.state == "active") {
            IconButton(onClick = onDispatch, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.PlayCircle, null, tint = Coral, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun WorkflowRunItem(
    run: GHWorkflowRun,
    c: GmColors,
    onClick: () -> Unit,
    onRerun: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val statusColor = when (run.status) {
        "completed" -> when (run.conclusion) {
            "success" -> Green
            "failure" -> RedColor
            "cancelled" -> c.textTertiary
            else -> Yellow
        }
        "in_progress" -> BlueColor
        "queued" -> Yellow
        else -> c.textTertiary
    }
    val statusText = when (run.status) {
        "completed" -> run.conclusion ?: "completed"
        "in_progress" -> "运行中"
        "queued" -> "排队中"
        else -> run.status ?: "unknown"
    }

    val durationText = remember(run.runStartedAt, run.updatedAt, run.status, run.conclusion) {
        getDurationOrStatus(run.runStartedAt, run.updatedAt, run.status, run.conclusion)
    }
    val timeText = remember(run.createdAt) {
        formatTime(run.createdAt)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgCard, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(run.displayTitle ?: run.name ?: "工作流运行", fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            GmBadge(statusText, statusColor.copy(alpha = 0.15f), statusColor)
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(c.bgCard)) {
                    if (run.status == "completed") {
                        DropdownMenuItem(
                            text = { Text("重新运行", fontSize = 13.sp, color = c.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, tint = BlueColor, modifier = Modifier.size(16.dp)) },
                            onClick = { onRerun(); showMenu = false }
                        )
                    }
                    if (run.status == "in_progress") {
                        DropdownMenuItem(
                            text = { Text("取消", fontSize = 13.sp, color = RedColor) },
                            leadingIcon = { Icon(Icons.Default.Close, null, tint = RedColor, modifier = Modifier.size(16.dp)) },
                            onClick = { onCancel(); showMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除", fontSize = 13.sp, color = RedColor) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = RedColor, modifier = Modifier.size(16.dp)) },
                        onClick = { onDelete(); showMenu = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GmBadge(run.headBranch ?: "unknown", BlueDim, BlueColor)
            Text((run.headSha ?: "").take(7), fontSize = 11.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(
                durationText,
                fontSize = 11.sp,
                color = c.textTertiary
            )
            Text(timeText, fontSize = 11.sp, color = c.textTertiary)
            run.actor?.let { actor ->
                Text(actor.login, fontSize = 11.sp, color = c.textTertiary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchWorkflowDialog(
    workflow: GHWorkflow,
    currentBranch: String,
    branches: List<String>,
    inputs: List<WorkflowInput>,
    inputsLoading: Boolean,
    c: GmColors,
    onConfirm: (String, Map<String, Any>?) -> Unit,
    onDismiss: () -> Unit
) {
    var ref by remember { mutableStateOf(currentBranch) }
    val inputValues = remember { mutableStateMapOf<String, Any>() }
    var branchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(inputs) {
        inputValues.clear()
        inputs.forEach { input ->
            input.default?.let { inputValues[input.name] = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("触发 ${workflow.name}", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ExposedDropdownMenuBox(
                    expanded = branchExpanded,
                    onExpandedChange = { branchExpanded = !branchExpanded }
                ) {
                    OutlinedTextField(
                        value = ref,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分支/标签") },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = branchExpanded
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Coral,
                            unfocusedBorderColor = c.border,
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary,
                            focusedContainerColor = c.bgItem,
                            unfocusedContainerColor = c.bgItem,
                            focusedLabelColor = Coral,
                            unfocusedLabelColor = c.textTertiary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = branchExpanded,
                        onDismissRequest = { branchExpanded = false }
                    ) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch, color = c.textPrimary) },
                                onClick = {
                                    ref = branch
                                    branchExpanded = false
                                }
                            )
                        }
                    }
                }

                if (inputsLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Coral)
                    }
                } else if (inputs.isNotEmpty()) {
                    Text("输入参数", fontSize = 12.sp, color = c.textTertiary, fontWeight = FontWeight.Medium)
                    inputs.forEach { input ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${input.name}${if (input.required) " *" else ""}",
                                fontSize = 13.sp,
                                color = c.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            input.description?.let {
                                Text(
                                    text = it,
                                    fontSize = 11.sp,
                                    color = c.textTertiary
                                )
                            }

                            when {
                                input.options != null && input.options!!.isNotEmpty() || input.type == "choice" -> {
                                    var expanded by remember { mutableStateOf(false) }
                                    val selectedValue = inputValues[input.name]?.toString() ?: input.default?.toString() ?: ""
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedValue,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("选择值") },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(
                                                    expanded = expanded
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Coral,
                                                unfocusedBorderColor = c.border,
                                                focusedTextColor = c.textPrimary,
                                                unfocusedTextColor = c.textPrimary,
                                                focusedContainerColor = c.bgItem,
                                                unfocusedContainerColor = c.bgItem,
                                                focusedLabelColor = Coral,
                                                unfocusedLabelColor = c.textTertiary,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            input.options?.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option, color = c.textPrimary) },
                                                    onClick = {
                                                        inputValues[input.name] = option
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                input.type == "boolean" -> {
                                    val currentValue = (inputValues[input.name] as? Boolean) ?: (input.default as? Boolean) ?: false
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = currentValue,
                                            onCheckedChange = { inputValues[input.name] = it },
                                            colors = CheckboxDefaults.colors(checkedColor = Coral)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (currentValue) "是" else "否", color = c.textPrimary)
                                    }
                                }
                                input.type == "number" -> {
                                    OutlinedTextField(
                                        value = (inputValues[input.name] as? String) ?: (input.default as? String) ?: "",
                                        onValueChange = { inputValues[input.name] = it },
                                        label = { Text("输入数字") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Coral,
                                            unfocusedBorderColor = c.border,
                                            focusedTextColor = c.textPrimary,
                                            unfocusedTextColor = c.textPrimary,
                                            focusedContainerColor = c.bgItem,
                                            unfocusedContainerColor = c.bgItem,
                                            focusedLabelColor = Coral,
                                            unfocusedLabelColor = c.textTertiary,
                                        ),
                                    )
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = (inputValues[input.name] as? String) ?: (input.default as? String) ?: "",
                                        onValueChange = { inputValues[input.name] = it },
                                        label = { Text("输入值") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Coral,
                                            unfocusedBorderColor = c.border,
                                            focusedTextColor = c.textPrimary,
                                            unfocusedTextColor = c.textPrimary,
                                            focusedContainerColor = c.bgItem,
                                            unfocusedContainerColor = c.bgItem,
                                            focusedLabelColor = Coral,
                                            unfocusedLabelColor = c.textTertiary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val filteredInputs = inputValues.filterValues { it.toString().isNotEmpty() }
                    onConfirm(ref, filteredInputs.ifEmpty { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Coral)
            ) {
                Text("触发")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = c.textSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunDetailSheet(
    run: GHWorkflowRun,
    jobs: List<GHWorkflowJob>,
    c: GmColors,
    vm: RepoDetailViewModel,
    onDismiss: () -> Unit,
    onRerun: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val statusColor = when (run.status) {
        "completed" -> when (run.conclusion) {
            "success" -> Green
            "failure" -> RedColor
            "cancelled" -> c.textTertiary
            else -> Yellow
        }
        "in_progress" -> BlueColor
        "queued" -> Yellow
        else -> c.textTertiary
    }
    var showLogsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.workflowLogs == null && !state.workflowLogsLoading) {
            vm.loadWorkflowLogs(run.id)
        }
    }

    val stepLogsMap = remember(state.workflowLogs, jobs) {
        buildStepLogsMap(state.workflowLogs, jobs)
    }

    if (showLogsDialog) {
        WorkflowLogsDialog(
            logs = state.workflowLogs,
            loading = state.workflowLogsLoading,
            c = c,
            onDismiss = {
                showLogsDialog = false
                vm.clearWorkflowLogs()
            },
            onLoadLogs = { vm.loadWorkflowLogs(run.id) }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(run.displayTitle ?: run.name ?: "工作流运行", fontSize = 16.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
            }

            // 信息
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GmBadge(run.headBranch ?: "unknown", BlueDim, BlueColor)
                Text((run.headSha ?: "").take(7), fontSize = 12.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace)
            }

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = c.bgItem, contentColor = c.textPrimary)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("浏览器打开")
                }
                if (run.status == "completed") {
                    Button(
                        onClick = onRerun,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新运行")
                    }
                }
                if (run.status == "in_progress") {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("取消")
                    }
                }
            }

            GmDivider()

            // Job 列表
            Text("Jobs", fontSize = 12.sp, color = c.textTertiary, fontWeight = FontWeight.Medium)
            if (jobs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("暂无 Job 信息", fontSize = 13.sp, color = c.textTertiary)
                }
            } else {
                jobs.forEach { job ->
                    JobCard(
                        job = job,
                        c = c,
                        stepLogs = stepLogsMap[job.id] ?: emptyMap()
                    )
                }
            }
        }
    }
}

/**
 * 构建 Step 日志映射
 * 返回 Map<JobId, Map<StepNumber, LogContent>>
 */
fun buildStepLogsMap(
    logs: Map<String, String>?,
    jobs: List<GHWorkflowJob>
): Map<Long, Map<Int, String>> {
    val result = mutableMapOf<Long, MutableMap<Int, String>>()
    logs ?: return result

    for ((filename, content) in logs) {
        LogManager.d("StepLogs", "处理日志文件: $filename")
        
        for (job in jobs) {
            val jobName = job.name ?: continue
            if (filename.contains(jobName) || filename.contains(job.id.toString())) {
                val jobStepLogs = result.getOrPut(job.id) { mutableMapOf() }
                
                for (step in job.steps ?: emptyList()) {
                    val stepName = step.name ?: continue
                    val stepNumber = step.number
                    
                    if (filename.contains(stepName) || filename.contains("$stepNumber")) {
                        jobStepLogs[stepNumber] = content
                        LogManager.d("StepLogs", "匹配: Job=${job.id}, Step=$stepNumber -> $filename")
                    }
                }
                
                if (jobStepLogs.isEmpty()) {
                    job.steps?.forEach { step ->
                        jobStepLogs[step.number] = content
                    }
                }
            }
        }
    }
    
    return result
}

@Composable
fun JobCard(job: GHWorkflowJob, c: GmColors, stepLogs: Map<Int, String> = emptyMap()) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (job.status) {
        "completed" -> when (job.conclusion) {
            "success" -> Green
            "failure" -> RedColor
            "cancelled" -> c.textTertiary
            else -> Yellow
        }
        "in_progress" -> BlueColor
        "queued" -> Yellow
        else -> c.textTertiary
    }
    val jobStatusText = job.status ?: "unknown"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgItem, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(job.name ?: "Job", fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            GmBadge(jobStatusText, statusColor.copy(alpha = 0.15f), statusColor)
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = c.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                GmDivider()
                Spacer(Modifier.height(8.dp))
                job.steps?.let { steps ->
                    steps.forEach { step ->
                        StepRow(
                            step = step,
                            c = c,
                            stepLogs = stepLogs[step.number]
                        )
                    }
                } ?: Text("暂无步骤信息", fontSize = 12.sp, color = c.textTertiary)
            }
        }
    }
}

@Composable
fun StepRow(step: GHWorkflowStep, c: GmColors, stepLogs: String? = null) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (step.status) {
        "completed" -> when (step.conclusion) {
            "success" -> Green
            "failure" -> RedColor
            "cancelled" -> c.textTertiary
            else -> Yellow
        }
        "in_progress" -> BlueColor
        "queued" -> Yellow
        else -> c.textTertiary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${step.number}. ${step.name ?: "Step"}",
                fontSize = 12.sp,
                color = c.textSecondary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val text = "${step.number}. ${step.name ?: "Step"}"
                    val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                    val clip = ClipData.newPlainText("step", text)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = c.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(4.dp))
                if (stepLogs != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(c.bgCard, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = stepLogs,
                            fontSize = 10.sp,
                            color = c.textTertiary,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    Text(
                        "暂无日志",
                        fontSize = 11.sp,
                        color = c.textTertiary
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowLogsDialog(
    logs: Map<String, String>?,
    loading: Boolean,
    c: GmColors,
    onDismiss: () -> Unit,
    onLoadLogs: () -> Unit
) {
    val context = LocalContext.current
    var selectedLogFile by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(logs) {
        if (selectedLogFile == null && logs != null && logs.isNotEmpty()) {
            selectedLogFile = logs.keys.firstOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("工作流日志", color = c.textPrimary) },
        text = {
            when {
                loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Coral)
                    }
                }
                logs.isNullOrEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("日志为空或加载失败", fontSize = 13.sp, color = c.textTertiary)
                        Button(
                            onClick = onLoadLogs,
                            colors = ButtonDefaults.buttonColors(containerColor = Coral)
                        ) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (logs.size > 1) {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedLogFile ?: "选择日志文件",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("日志文件") },
                                    singleLine = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Coral,
                                        unfocusedBorderColor = c.border,
                                        focusedTextColor = c.textPrimary,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    logs.keys.forEach { filename ->
                                        DropdownMenuItem(
                                            text = { Text(filename, color = c.textPrimary, fontSize = 12.sp) },
                                            onClick = {
                                                selectedLogFile = filename
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        val selectedLogContent = selectedLogFile?.let { logs[it] }
                        if (selectedLogContent != null) {
                            val scrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .background(c.bgItem, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = selectedLogContent,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(12.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = c.textSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!loading && !logs.isNullOrEmpty() && selectedLogFile != null) {
                Button(
                    onClick = {
                        val content = logs[selectedLogFile]
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Workflow Logs", content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.bgItem, contentColor = c.textPrimary)
                ) {
                    Text("复制日志")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = c.textSecondary)
            }
        }
    )
}

// ─── Commit Detail Modal ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailSheet(
    commit: GHCommitFull,
    c: GmColors,
    vm: RepoDetailViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var showResetConfirm  by remember { mutableStateOf(false) }
    var showRevertConfirm by remember { mutableStateOf(false) }

    // ── Diff 查看器（二级 Sheet）──────────────────────────────────────────
    state.selectedFilePatch?.let { (filename, patch) ->
        FileDiffSheet(filename = filename, patch = patch, c = c, onDismiss = vm::closeFilePatch)
    }

    // ── 回滚确认 ──────────────────────────────────────────────────────────
    if (showResetConfirm) {
        ResetConfirmDialog(
            shortSha = commit.shortSha,
            branch   = state.currentBranch,
            c        = c,
            onConfirm = {
                vm.resetToCommit(commit.sha)
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    // ── 撤销确认 ──────────────────────────────────────────────────────────
    if (showRevertConfirm) {
        RevertConfirmDialog(
            commit    = commit,
            branch    = state.currentBranch,
            c         = c,
            onConfirm = { msg ->
                vm.revertCommit(commit.sha, msg)
                showRevertConfirm = false
            },
            onDismiss = { showRevertConfirm = false },
        )
    }

    // ── 操作结果提示 ───────────────────────────────────────────────────────
    state.gitOpResult?.let { result ->
        LaunchedEffect(result) {
            kotlinx.coroutines.delay(3000)
            vm.clearGitOpResult()
        }
        GitOpResultSnackbar(result = result, c = c)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = c.bgCard,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = c.border) },
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // 操作进行中遮罩
        if (state.gitOpInProgress) {
            Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.size(32.dp))
                    Text("正在执行操作…", fontSize = 13.sp, color = c.textSecondary)
                }
            }
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    commit.shortSha,
                    fontSize = 12.sp, color = Coral, fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(CoralDim, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                if (commit.stats != null) {
                    Text("+${commit.stats.additions}", fontSize = 12.sp, color = Green)
                    Text("-${commit.stats.deletions}", fontSize = 12.sp, color = RedColor)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(commit.commit.message, fontSize = 14.sp, color = c.textPrimary,
                lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (commit.author != null) AvatarImage(commit.author.avatarUrl, 20)
                Text(commit.commit.author.name, fontSize = 12.sp, color = c.textSecondary)
                Text("·", color = c.textTertiary)
                Text(formatDate(commit.commit.author.date), fontSize = 12.sp, color = c.textTertiary)
            }
            Spacer(Modifier.height(14.dp))
            GmDivider()
            Spacer(Modifier.height(12.dp))

            // ── 变更文件列表（可点击查看 diff）──────────────────────────
            commit.files?.let { files ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("变更文件 (${files.size})", fontSize = 12.sp,
                        color = c.textSecondary, fontWeight = FontWeight.Medium)
                    Text("· 点击查看 diff", fontSize = 11.sp, color = c.textTertiary)
                }
                Spacer(Modifier.height(8.dp))
                files.forEach { file ->
                    val (statusColor, statusLabel) = when (file.status) {
                        "added"    -> Green     to "A"
                        "removed"  -> RedColor  to "D"
                        "modified" -> Yellow    to "M"
                        "renamed"  -> BlueColor to "R"
                        else       -> c.textTertiary to "?"
                    }
                    val hasPatch = !file.patch.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (hasPatch) c.bgItem else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .then(
                                if (hasPatch) Modifier.clickable {
                                    vm.openFilePatch(file.filename, file.patch!!)
                                } else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            statusLabel, fontSize = 11.sp, color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                        Text(file.filename, fontSize = 12.sp, color = c.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f), maxLines = 1)
                        if (file.additions > 0 || file.deletions > 0) {
                            Text("+${file.additions}/-${file.deletions}",
                                fontSize = 11.sp, color = c.textTertiary)
                        }
                        if (hasPatch) {
                            Icon(Icons.Default.ChevronRight, null,
                                tint = c.textTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            GmDivider()
            Spacer(Modifier.height(12.dp))

            // ── 操作区 ─────────────────────────────────────────────────
            Text("操作", fontSize = 12.sp, color = c.textSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))

            // 撤销（Revert）：创建新 commit，保留历史 ─────────────────────
            Button(
                onClick = { showRevertConfirm = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueColor),
            ) {
                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("撤销此提交", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "· 创建新 commit，内容回退到此提交之前，不重写历史",
                fontSize = 11.sp, color = c.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(12.dp))

            // 回滚（Reset）：强制移动分支指针 ────────────────────────────
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape  = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(RedColor.copy(alpha = 0.7f))),
            ) {
                Icon(Icons.Default.RestartAlt, null, tint = RedColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("回滚到此提交", fontSize = 13.sp, color = RedColor, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "· 强制将分支 HEAD 指向此 SHA，之后的提交将消失（危险）",
                fontSize = 11.sp, color = c.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            // 在 GitHub 查看 ──────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(commit.htmlUrl)))
                },
                modifier = Modifier.fillMaxWidth(),
                shape  = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(c.border)),
            ) {
                Icon(Icons.Default.OpenInBrowser, null,
                    tint = c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("在 GitHub 查看", fontSize = 14.sp, color = c.textSecondary)
            }
        }
    }
}

// ─── ResetConfirmDialog（回滚：重写历史，危险操作）───────────────────────────

@Composable
private fun ResetConfirmDialog(
    shortSha: String, branch: String, c: GmColors,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = c.bgCard,
        icon = {
            Icon(Icons.Default.RestartAlt, null, tint = RedColor, modifier = Modifier.size(28.dp))
        },
        title = { Text("确认回滚", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 目标提交
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(c.bgItem, RoundedCornerShape(8.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("目标", fontSize = 12.sp, color = c.textTertiary)
                    Text(
                        shortSha, fontSize = 13.sp, color = Coral,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(CoralDim, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text("on  $branch", fontSize = 12.sp, color = c.textSecondary)
                }
                // 危险警告
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(RedDim, RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = RedColor, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚠ 危险操作", fontSize = 13.sp, color = RedColor, fontWeight = FontWeight.SemiBold)
                        Text(
                            "此操作将强制把分支指针指向目标提交，之后的所有提交记录将从分支上消失（相当于 git push -f）。如果分支有保护规则，操作可能被拒绝。",
                            fontSize = 12.sp, color = RedColor.copy(alpha = 0.85f), lineHeight = 17.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = RedColor),
                shape   = RoundedCornerShape(10.dp),
            ) { Text("确认回滚", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

// ─── RevertConfirmDialog（撤销：创建新 commit，安全）────────────────────────

@Composable
private fun RevertConfirmDialog(
    commit: GHCommitFull, branch: String, c: GmColors,
    onConfirm: (message: String) -> Unit, onDismiss: () -> Unit,
) {
    val defaultMsg = "Revert \"${commit.commit.message.lines().first().take(50)}\""
    var revertMsg by remember { mutableStateOf(defaultMsg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = c.bgCard,
        icon = {
            Icon(Icons.Default.SwapHoriz, null, tint = BlueColor, modifier = Modifier.size(28.dp))
        },
        title = { Text("撤销此提交", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 目标提交信息
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(c.bgItem, RoundedCornerShape(8.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        commit.shortSha, fontSize = 12.sp, color = Coral,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(CoralDim, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(
                        commit.commit.message.lines().first(),
                        fontSize = 12.sp, color = c.textPrimary,
                        modifier = Modifier.weight(1f), maxLines = 1,
                    )
                }
                // 安全说明
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(BlueColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Info, null, tint = BlueColor, modifier = Modifier.size(16.dp))
                    Text(
                        "将创建一个新的 revert commit，把分支内容恢复到此提交之前的状态。不重写历史，可安全用于受保护分支。",
                        fontSize = 12.sp, color = BlueColor.copy(alpha = 0.9f), lineHeight = 17.sp,
                    )
                }
                // commit message 编辑
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Revert commit 信息", fontSize = 12.sp,
                        color = c.textSecondary, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value         = revertMsg,
                        onValueChange = { revertMsg = it },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = BlueColor,
                            unfocusedBorderColor    = c.border,
                            focusedTextColor        = c.textPrimary,
                            unfocusedTextColor      = c.textPrimary,
                            focusedContainerColor   = c.bgItem,
                            unfocusedContainerColor = c.bgItem,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (revertMsg.isNotBlank()) onConfirm(revertMsg.trim()) },
                enabled  = revertMsg.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = BlueColor),
                shape    = RoundedCornerShape(10.dp),
            ) { Text("确认撤销", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

// ─── 操作结果浮动提示 ────────────────────────────────────────────────────────

@Composable
private fun GitOpResultSnackbar(result: GitOpResult, c: GmColors) {
    val bg  = if (result.success) GreenDim  else RedDim
    val fg  = if (result.success) Green     else RedColor
    val ico = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ico, null, tint = fg, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${result.opName}${if (result.success) "成功" else "失败"}",
                    fontSize = 13.sp, color = fg, fontWeight = FontWeight.SemiBold,
                )
                Text(result.detail, fontSize = 12.sp, color = fg.copy(alpha = 0.8f), lineHeight = 16.sp)
            }
        }
    }
}

// ─── FileDiffSheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDiffSheet(filename: String, patch: String, c: GmColors, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1117),   // GitHub 暗色背景
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF30363D)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 32.dp),
        ) {
            // 文件名 header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                Icon(Icons.Default.Description, null,
                    tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                Text(filename, fontSize = 13.sp, color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1)
            }
            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Diff 内容（逐行着色）
            val lines = patch.lines()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                lines.forEach { line ->
                    val (bg, fg) = when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            Color(0xFF0D4A29) to Color(0xFF85E89D)       // 新增：绿色
                        line.startsWith("-") && !line.startsWith("---") ->
                            Color(0xFF430D18) to Color(0xFFFFA198)       // 删除：红色
                        line.startsWith("@@") ->
                            Color(0xFF1B3A5E) to Color(0xFF79C0FF)       // 行号标记：蓝色
                        else -> Color.Transparent to Color(0xFF8B949E)  // 上下文：灰色
                    }
                    Text(
                        text = line,
                        fontSize = 11.sp, color = fg, fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ─── 工具方法 ────────────────────────────────────────────────────────────────

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L        -> "${bytes}B"
    bytes < 1024L * 1024 -> "${bytes / 1024}KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / 1024 / 1024}MB"
    else -> "${bytes / 1024 / 1024 / 1024}GB"
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatDate(iso: String): String = try {
    val odt = OffsetDateTime.parse(iso)
    odt.toLocalDateTime().format(dateFormatter)
} catch (_: Exception) {
    iso
}

// ─── 分支相关弹窗 ─────────────────────────────────────────────────────────────

@Composable
private fun BranchPickerDialog(
    branches: List<GHBranch>,
    current: String,
    c: GmColors,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("选择分支", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前分支：$current",
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(branches, key = { it.name }) { branch ->
                        val isCurrent = branch.name == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) CoralDim else c.bgItem,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    if (!isCurrent) onSelect(branch.name) else onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.AccountTree,
                                null,
                                tint = if (isCurrent) Coral else c.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                branch.name,
                                fontSize = 13.sp,
                                color = if (isCurrent) Coral else c.textPrimary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (isCurrent) {
                                GmBadge("当前", CoralDim, Coral)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = c.textSecondary)
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun NewBranchDialog(
    c: GmColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var branchName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("新建分支", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "请输入新的分支名称。",
                    fontSize = 12.sp,
                    color = c.textSecondary,
                )
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("feature/my-branch", color = c.textTertiary) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueColor,
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem,
                        unfocusedContainerColor = c.bgItem,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (branchName.isNotBlank()) onConfirm(branchName.trim()) },
                enabled = branchName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BlueColor),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("创建", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = c.textSecondary)
            }
        },
    )
}

@Composable
private fun RenameBranchDialog(
    oldName: String,
    c: GmColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf(oldName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("重命名分支", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前分支：$oldName",
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("新的分支名称", color = c.textTertiary) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueColor,
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem,
                        unfocusedContainerColor = c.bgItem,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isNotBlank() && name != oldName) {
                        onConfirm(name)
                    }
                },
                enabled = newName.isNotBlank() && newName != oldName,
                colors = ButtonDefaults.buttonColors(containerColor = BlueColor),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = c.textSecondary)
            }
        },
    )
}

// ─── Repo-level Dialogs ──────────────────────────────────────────────────────

@Composable
private fun RepoRenameDialog(
    currentName: String,
    owner: String,
    c: GmColors,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val pattern = remember { Regex("[a-zA-Z0-9._-]+") }
    val isValid = name.isNotBlank() && name != currentName && name.matches(pattern)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("重命名仓库", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$owner / $currentName → $owner / $name",
                    fontSize = 12.sp,
                    color = c.textTertiary,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新名称") },
                    supportingText = {
                        if (name.isNotBlank() && !name.matches(pattern)) {
                            Text("只允许字母、数字、点、连字符和下划线", color = RedColor, fontSize = 11.sp)
                        }
                    },
                    isError = name.isNotBlank() && !name.matches(pattern),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Coral,
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem,
                        unfocusedContainerColor = c.bgItem,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Coral,
                    disabledContainerColor = c.border,
                ),
            ) {
                Text("重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

@Composable
private fun RepoEditDialog(
    repo: GHRepo,
    c: GmColors,
    onConfirm: (String, String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var desc by remember { mutableStateOf(repo.description ?: "") }
    var website by remember { mutableStateOf("") }
    var topicsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("编辑仓库信息", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RepoTextField(value = desc, onValueChange = { desc = it }, label = "About", maxLines = 3, c = c)
                RepoTextField(value = website, onValueChange = { website = it }, label = "Website", c = c)
                RepoTextField(value = topicsText, onValueChange = { topicsText = it }, label = "Topics（空格分隔）", c = c)
                Text(
                    "Topics 示例：kotlin android github",
                    fontSize = 11.sp,
                    color = c.textTertiary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val topics = topicsText.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(desc, website, topics)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

@Composable
private fun RepoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    c: GmColors,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Coral,
            unfocusedBorderColor = c.border,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.bgItem,
            unfocusedContainerColor = c.bgItem,
            focusedLabelColor = Coral,
            unfocusedLabelColor = c.textTertiary,
        ),
    )
}

@Composable
private fun RepoDeleteDialog(
    repoName: String,
    owner: String,
    c: GmColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val fullName = "$owner/$repoName"
    val match = fullName

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        icon = {
            Icon(Icons.Default.DeleteForever, null, tint = RedColor, modifier = Modifier.size(28.dp))
        },
        title = { Text("删除仓库", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "此操作将永久删除 $fullName，且无法恢复。",
                    fontSize = 12.sp,
                    color = c.textSecondary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "点击右侧文字可复制仓库名：",
                        fontSize = 12.sp,
                        color = c.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        repoName,
                        fontSize = 12.sp,
                        color = Coral,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(CoralDim, RoundedCornerShape(6.dp))
                            .clickable {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("repoName", repoName))
                                input = repoName
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入仓库名以确认删除", color = c.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedColor,
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem,
                        unfocusedContainerColor = c.bgItem,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = input == repoName,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedColor,
                    disabledContainerColor = c.border,
                ),
            ) { Text("我已知晓风险，确认删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

@Composable
private fun RepoVisibilityDialog(
    repo: GHRepo,
    c: GmColors,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var isPrivate by remember { mutableStateOf(repo.private) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        icon = {
            Icon(Icons.Default.Lock, null, tint = c.textSecondary, modifier = Modifier.size(24.dp))
        },
        title = { Text("更改仓库可见性", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = !isPrivate,
                        onClick = { isPrivate = false },
                        colors = RadioButtonDefaults.colors(selectedColor = BlueColor),
                    )
                    Column {
                        Text("公开仓库", fontSize = 13.sp, color = c.textPrimary)
                        Text("任何人都可以查看此仓库。", fontSize = 11.sp, color = c.textTertiary)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = isPrivate,
                        onClick = { isPrivate = true },
                        colors = RadioButtonDefaults.colors(selectedColor = RedColor),
                    )
                    Column {
                        Text("私有仓库", fontSize = 13.sp, color = c.textPrimary)
                        Text("仅你和被授予权限的协作者可以访问。", fontSize = 11.sp, color = c.textTertiary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(isPrivate) },
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

@Composable
private fun RepoTransferDialog(
    owner: String,
    repoName: String,
    userLogin: String,
    userAvatar: String,
    orgs: List<GHOrg>,
    c: GmColors,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 默认选中第一个可用目标（自己或组织）；如果只有当前 owner，就保持 owner，但 UI 不再单独列出“owner 本人”条目
    val selfLogin = userLogin.ifBlank { owner }
    val transferTargets = remember(selfLogin, orgs) {
        buildList {
            // 只在用户登录与当前 owner 不同的时候，才把“你自己”作为一个显式候选
            if (selfLogin != owner) add(selfLogin)
            addAll(orgs.map { it.login })
        }
    }

    var selectedOwner by remember { mutableStateOf(transferTargets.firstOrNull().orEmpty()) }
    var keepName by remember { mutableStateOf(true) }
    var newName by remember { mutableStateOf(repoName) }
    var nameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        icon = {
            Icon(Icons.Default.SwapHoriz, null, tint = BlueColor, modifier = Modifier.size(24.dp))
        },
        title = { Text("转移仓库", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "选择新的所有者（用户或组织）。GitHub 可能会发送确认邮件，请在网页端完成最终确认。",
                    fontSize = 12.sp,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 自己（前提：与当前 owner 不同）
                    if (selfLogin != owner) {
                        RepoTransferTargetRow(
                            login = selfLogin,
                            avatarUrl = userAvatar,
                            selected = selectedOwner == selfLogin,
                            c = c,
                            onClick = { selectedOwner = selfLogin },
                            labelSuffix = "（你）",
                        )
                    }
                    // 所有组织
                    orgs.forEach { org ->
                        RepoTransferTargetRow(
                            login = org.login,
                            avatarUrl = org.avatarUrl,
                            selected = selectedOwner == org.login,
                            c = c,
                            onClick = { selectedOwner = org.login },
                            labelSuffix = "",
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text("仓库名称", fontSize = 12.sp, color = c.textSecondary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = keepName,
                        onClick = { keepName = true },
                        colors = RadioButtonDefaults.colors(selectedColor = BlueColor),
                    )
                    Text("保留原名称（$repoName）", fontSize = 12.sp, color = c.textPrimary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = !keepName,
                        onClick = { keepName = false },
                        colors = RadioButtonDefaults.colors(selectedColor = Coral),
                    )
                    Text("在目标下重命名", fontSize = 12.sp, color = c.textPrimary)
                }
                if (!keepName) {
                    val context = LocalContext.current
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            nameAvailable = null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入新的仓库名", color = c.textTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Coral,
                            unfocusedBorderColor = c.border,
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary,
                            focusedContainerColor = c.bgItem,
                            unfocusedContainerColor = c.bgItem,
                        ),
                        trailingIcon = {
                            when {
                                checking -> {
                                    CircularProgressIndicator(
                                        color = Coral,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                nameAvailable == true -> {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = Green,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                nameAvailable == false -> {
                                    Icon(
                                        Icons.Default.Error,
                                        null,
                                        tint = RedColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("目标：$selectedOwner / ${newName.ifBlank { repoName }}",
                            fontSize = 11.sp,
                            color = c.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        if (nameAvailable == false) {
                            Text(
                                "该名称在目标下已存在",
                                fontSize = 11.sp,
                                color = RedColor,
                            )
                        } else if (nameAvailable == true) {
                            Text(
                                "名称可用",
                                fontSize = 11.sp,
                                color = Green,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (keepName) null else newName.trim().ifBlank { null }
                    onConfirm(selectedOwner, finalName)
                },
                enabled = selectedOwner.isNotBlank() &&
                    (keepName || newName.isNotBlank() && nameAvailable != false),
                colors = ButtonDefaults.buttonColors(containerColor = BlueColor),
            ) { Text("确认转移到 $selectedOwner") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}

@Composable
private fun RepoTransferTargetRow(
    login: String,
    avatarUrl: String?,
    selected: Boolean,
    c: GmColors,
    onClick: () -> Unit,
    labelSuffix: String,
) {
    val bg = if (selected) CoralDim else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        coil.compose.AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(c.bgItem),
        )
        Text(
            login + labelSuffix,
            fontSize = 13.sp,
            color = if (selected) Coral else c.textPrimary,
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * 获取运行时间或状态文本 - 已完成显示时长，未完成显示状态
 */
fun getDurationOrStatus(runStartedAt: String?, updatedAt: String?, status: String?, conclusion: String?): String {
    if (status == "completed") {
        return try {
            val start = runStartedAt?.let {
                try {
                    OffsetDateTime.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
            
            val end = updatedAt?.let {
                try {
                    OffsetDateTime.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (start != null && end != null) {
                val duration = java.time.Duration.between(start, end)
                val seconds = duration.seconds
                
                if (seconds > 0) {
                    val hours = duration.toHours()
                    val minutes = duration.toMinutes() % 60
                    val secs = duration.seconds % 60
                    
                    return when {
                        hours > 0 -> "${hours}h ${minutes}m"
                        minutes > 0 -> "${minutes}m ${secs}s"
                        else -> "${secs}秒"
                    }
                }
            }
            
            getStatusText(conclusion ?: status)
        } catch (e: Exception) {
            getStatusText(conclusion ?: status)
        }
    } else {
        return getStatusText(status)
    }
}

/**
 * 获取状态文本
 */
private fun getStatusText(status: String?): String {
    return when (status) {
        "in_progress" -> "正在进行"
        "queued" -> "排队中"
        "requested" -> "请求中"
        "waiting" -> "等待中"
        "pending" -> "待处理"
        "action_required" -> "需要操作"
        "cancelled" -> "已取消"
        "failure" -> "失败"
        "neutral" -> "中立"
        "skipped" -> "已跳过"
        "stale" -> "过时"
        "success" -> "成功"
        "timed_out" -> "超时"
        else -> status ?: "-"
    }
}

/**
 * 格式化运行时间
 */
fun formatTime(createdAt: String): String {
    return try {
        val dateTime = OffsetDateTime.parse(createdAt)
        val now = OffsetDateTime.now()
        val duration = java.time.Duration.between(dateTime, now)
        
        when {
            duration.toMinutes() < 1 -> "刚刚"
            duration.toHours() < 1 -> "${duration.toMinutes()}分钟前"
            duration.toDays() < 1 -> "${duration.toHours()}小时前"
            duration.toDays() < 7 -> "${duration.toDays()}天前"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                dateTime.format(formatter)
            }
        }
    } catch (e: Exception) {
        createdAt
    }
}
