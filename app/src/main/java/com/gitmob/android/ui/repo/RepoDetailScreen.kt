package com.gitmob.android.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
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
    onFileClick: (String, String, String, String) -> Unit, // owner,repo,path,ref
    vm: RepoDetailViewModel = viewModel(
        factory = RepoDetailViewModel.factory(owner, repoName),
    ),
) {
    val state by vm.state.collectAsState()
    val tabs = listOf("文件", "提交", "分支", "PR", "Issues")
    var showBranchDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.repoName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                        Text(vm.owner, fontSize = 12.sp, color = TextTertiary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleStar) {
                        Icon(
                            if (state.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            null, tint = if (state.isStarred) Yellow else TextSecondary,
                        )
                    }
                    IconButton(onClick = vm::loadAll) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
    ) { padding ->
        if (state.loading) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        if (state.error != null && state.repo == null) {
            ErrorBox(state.error!!, vm::loadAll); return@Scaffold
        }
        val repo = state.repo ?: return@Scaffold

        Column(Modifier.padding(padding).fillMaxSize()) {
            // Repo info card
            RepoInfoCard(repo = repo, isStarred = state.isStarred)

            // Branch selector
            BranchSelector(
                currentBranch = state.currentBranch,
                onClick = { showBranchDialog = true },
            )

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = state.tab,
                containerColor = BgDeep,
                contentColor = Coral,
                edgePadding = 16.dp,
                divider = { GmDivider() },
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = state.tab == i,
                        onClick = { vm.setTab(i) },
                        text = { Text(label, fontSize = 13.sp) },
                        selectedContentColor = Coral,
                        unselectedContentColor = TextSecondary,
                    )
                }
            }

            when (state.tab) {
                0 -> FilesTab(
                    state = state,
                    onDirClick = { vm.loadContents(it) },
                    onFileClick = { path -> onFileClick(owner, repoName, path, state.currentBranch) },
                    onNavigateUp = vm::navigateUp,
                )
                1 -> CommitsTab(commits = state.commits)
                2 -> BranchesTab(
                    branches = state.branches,
                    currentBranch = state.currentBranch,
                    onSwitch = vm::switchBranch,
                    onNewBranch = { showNewBranchDialog = true },
                )
                3 -> PRTab(prs = state.prs)
                4 -> IssuesTab(issues = state.issues)
            }
        }
    }

    // Branch picker dialog
    if (showBranchDialog) {
        BranchPickerDialog(
            branches = state.branches,
            current = state.currentBranch,
            onSelect = { vm.switchBranch(it); showBranchDialog = false },
            onDismiss = { showBranchDialog = false },
        )
    }

    if (showNewBranchDialog) {
        NewBranchDialog(
            onConfirm = { vm.createBranch(it); showNewBranchDialog = false },
            onDismiss = { showNewBranchDialog = false },
        )
    }
}

@Composable
private fun RepoInfoCard(repo: GHRepo, isStarred: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (!repo.description.isNullOrBlank()) {
            Text(repo.description, fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatItem(Icons.Default.Star, "${repo.stars}", Yellow)
            StatItem(Icons.Default.Share, "${repo.forks}", TextSecondary)
            StatItem(Icons.Default.Warning, "${repo.openIssues}", RedColor)
            Spacer(Modifier.weight(1f))
            if (!repo.language.isNullOrBlank()) {
                Text(repo.language, fontSize = 11.sp, color = TextTertiary,
                    modifier = Modifier.background(BgItem, RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp))
            }
            if (repo.private) GmBadge("私有", RedDim, RedColor)
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, color = TextTertiary)
    }
}

@Composable
private fun BranchSelector(currentBranch: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.AccountTree, null, tint = BlueColor, modifier = Modifier.size(15.dp))
        Text(
            currentBranch, fontSize = 13.sp, color = BlueColor,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        )
        Icon(Icons.Default.ExpandMore, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun FilesTab(
    state: RepoDetailState,
    onDirClick: (String) -> Unit,
    onFileClick: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (state.currentPath.isNotEmpty()) {
            item {
                ContentRow(
                    icon = Icons.Default.ArrowUpward,
                    name = "..",
                    iconTint = TextTertiary,
                    onClick = onNavigateUp,
                )
            }
        }
        if (state.contentsLoading) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } }
        } else {
            items(state.contents, key = { it.path }) { c ->
                ContentRow(
                    icon = if (c.type == "dir") Icons.Default.Folder else Icons.Default.Description,
                    name = c.name,
                    iconTint = if (c.type == "dir") Yellow else TextSecondary,
                    size = if (c.type == "file") formatSize(c.size) else null,
                    onClick = {
                        if (c.type == "dir") onDirClick(c.path) else onFileClick(c.path)
                    },
                )
            }
        }
    }
}

@Composable
private fun ContentRow(icon: ImageVector, name: String, iconTint: Color, size: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
        Text(name, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        if (size != null) Text(size, fontSize = 11.sp, color = TextTertiary)
    }
}

@Composable
fun CommitsTab(commits: List<GHCommit>) {
    if (commits.isEmpty()) { EmptyBox("暂无提交记录"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(commits, key = { it.sha }) { c ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(c.commit.message.lines().first(), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.author != null) AvatarImage(c.author.avatarUrl, 20)
                    Text(c.commit.author.name, fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(c.shortSha, fontSize = 10.sp, color = Coral, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(CoralDim, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    Text(formatDate(c.commit.author.date), fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
fun BranchesTab(
    branches: List<GHBranch>,
    currentBranch: String,
    onSwitch: (String) -> Unit,
    onNewBranch: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Button(
                onClick = onNewBranch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CoralDim, contentColor = Coral),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建分支", fontSize = 13.sp)
            }
        }
        items(branches, key = { it.name }) { b ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AccountTree, null, tint = if (b.name == currentBranch) Coral else TextTertiary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(b.name, fontSize = 13.sp, color = if (b.name == currentBranch) Coral else TextPrimary,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                if (b.name == currentBranch) {
                    GmBadge("当前", CoralDim, Coral)
                } else {
                    TextButton(onClick = { onSwitch(b.name) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("切换", fontSize = 12.sp, color = BlueColor)
                    }
                }
            }
        }
    }
}

@Composable
fun PRTab(prs: List<GHPullRequest>) {
    if (prs.isEmpty()) { EmptyBox("暂无 Pull Request"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(prs, key = { it.number }) { pr ->
            Column(Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GmBadge("#${pr.number}", GreenDim, Green)
                    Text(pr.title, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pr.head.ref, fontSize = 11.sp, color = BlueColor, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(BlueDim, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
                    Text("→", fontSize = 11.sp, color = TextTertiary)
                    Text(pr.base.ref, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Text(pr.user.login, fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
fun IssuesTab(issues: List<GHIssue>) {
    if (issues.isEmpty()) { EmptyBox("暂无 Issues"); return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(issues.filter { !it.isPR }, key = { it.number }) { issue ->
            Row(
                modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Circle, null, tint = if (issue.state == "open") Green else RedColor, modifier = Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(issue.title, fontSize = 13.sp, color = TextPrimary)
                    Text("#${issue.number} · ${issue.user.login}", fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun BranchPickerDialog(
    branches: List<GHBranch>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("切换分支", color = TextPrimary) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(branches) { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(b.name) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.AccountTree, null, tint = if (b.name == current) Coral else TextTertiary, modifier = Modifier.size(14.dp))
                        Text(b.name, fontSize = 14.sp, color = if (b.name == current) Coral else TextPrimary, modifier = Modifier.weight(1f))
                        if (b.name == current) Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        },
    )
}

@Composable
private fun NewBranchDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("新建分支", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("feature/my-feature", color = TextTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Coral, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgItem, unfocusedContainerColor = BgItem,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / 1024 / 1024}MB"
}

private fun formatDate(iso: String): String {
    return try {
        val parts = iso.split("T")
        if (parts.size >= 1) parts[0] else iso
    } catch (e: Exception) { iso }
}
