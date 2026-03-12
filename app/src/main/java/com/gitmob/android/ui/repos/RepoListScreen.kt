package com.gitmob.android.ui.repos

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gitmob.android.api.GHOrg
import com.gitmob.android.api.GHRepo
import com.gitmob.android.ui.common.*
import com.gitmob.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onRepoClick: (String, String) -> Unit,
    onCreateRepo: () -> Unit,
    onCloneRepo: (String) -> Unit = {},
    vm: RepoListViewModel = viewModel(),
) {
    val c = LocalGmColors.current
    val state by vm.state.collectAsState()
    val repos by vm.filteredRepos.collectAsState()
    var showOrgMenu by remember { mutableStateOf(false) }

    // Toast
    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box {
                            // 头像 + 组织切换下拉
                            AsyncImage(
                                model = state.userAvatar,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(c.bgItem).clickable { showOrgMenu = true },
                            )
                            DropdownMenu(
                                expanded = showOrgMenu,
                                onDismissRequest = { showOrgMenu = false },
                                modifier = Modifier.background(c.bgCard),
                            ) {
                                // 用户自己
                                OrgMenuItem(
                                    login = state.userLogin,
                                    avatarUrl = state.userAvatar,
                                    selected = state.currentContext == null,
                                    onClick = { vm.switchContext(null); showOrgMenu = false },
                                    c = c,
                                )
                                if (state.userOrgs.isNotEmpty()) {
                                    HorizontalDivider(color = c.border, thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 4.dp))
                                    state.userOrgs.forEach { org ->
                                        val sel = state.currentContext?.login == org.login
                                        OrgMenuItem(
                                            login = org.login,
                                            avatarUrl = org.avatarUrl,
                                            selected = sel,
                                            onClick = {
                                                vm.switchContext(OrgContext(org.login, org.avatarUrl, false))
                                                showOrgMenu = false
                                            },
                                            c = c,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = state.currentContext?.login ?: "GitMob",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Coral,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCreateRepo) {
                        Icon(Icons.Default.Add, null, tint = Coral)
                    }
                    IconButton(onClick = { vm.loadRepos(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, null, tint = c.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgDeep),
            )
        },
        snackbarHost = {
            state.toast?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Snackbar(modifier = Modifier.padding(16.dp),
                        containerColor = c.bgCard, contentColor = c.textPrimary) {
                        Text(it)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 搜索框
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::setSearch,
                placeholder = { Text("搜索仓库…", color = c.textTertiary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { vm.setSearch("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                        }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.bgCard, unfocusedContainerColor = c.bgCard,
                    focusedBorderColor = Coral, unfocusedBorderColor = c.border,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                ),
            )
            // 过滤
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", false to "公开", true to "私有").forEach { (value, label) ->
                    FilterChip(
                        selected = state.filterPrivate == value,
                        onClick = { vm.setFilter(value) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralDim, selectedLabelColor = Coral,
                            containerColor = c.bgCard, labelColor = c.textSecondary,
                        ),
                    )
                }
            }

            when {
                state.loading && repos.isEmpty() -> LoadingBox()
                state.error != null && repos.isEmpty() -> ErrorBox(state.error!!) { vm.loadRepos(true) }
                repos.isEmpty() -> EmptyBox("暂无仓库，点击右上角 + 创建")
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(repos, key = { it.id }) { repo ->
                        SwipeableRepoCard(
                            repo = repo,
                            onClick = { onRepoClick(repo.owner.login, repo.name) },
                            onDelete = { vm.deleteRepo(repo.owner.login, repo.name) },
                            onRename = { newName -> vm.renameRepo(repo.owner.login, repo.name, newName) },
                            onEdit = { desc, site, topics -> vm.editRepo(repo.owner.login, repo.name, desc, site, topics) },
                            onClone = { url -> onCloneRepo(url) },
                            c = c,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgMenuItem(login: String, avatarUrl: String?, selected: Boolean, onClick: () -> Unit, c: GmColors) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(model = avatarUrl, contentDescription = null,
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(c.bgItem))
                Text(login, fontSize = 14.sp, color = if (selected) Coral else c.textPrimary)
                if (selected) Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(14.dp))
            }
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = c.textPrimary),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableRepoCard(
    repo: GHRepo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onEdit: (String, String, List<String>) -> Unit,
    onClone: (String) -> Unit,
    c: GmColors,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
            }
            false  // 不真的 dismiss，仅触发对话框
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
        RepoCardContent(repo = repo, onClick = onClick,
            onRename = { showRenameDialog = true },
            onEdit = { showEditDialog = true },
            onClone = { onClone(repo.cloneUrl) },
            c = c)
    }

    if (showDeleteDialog) {
        DeleteRepoDialog(
            repoName = repo.name,
            onConfirm = { onDelete(); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false },
            c = c,
        )
    }
    if (showRenameDialog) {
        RenameRepoDialog(
            currentName = repo.name,
            owner = repo.owner.login,
            onConfirm = { newName -> onRename(newName); showRenameDialog = false },
            onDismiss = { showRenameDialog = false },
            c = c,
        )
    }
    if (showEditDialog) {
        EditRepoDialog(
            repo = repo,
            onConfirm = { desc, site, topics -> onEdit(desc, site, topics); showEditDialog = false },
            onDismiss = { showEditDialog = false },
            c = c,
        )
    }
}

@Composable
private fun RepoCardContent(
    repo: GHRepo,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    c: GmColors,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(repo.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.textPrimary)
                if (!repo.description.isNullOrBlank()) {
                    Text(repo.description, fontSize = 12.sp, color = c.textSecondary, maxLines = 2,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (repo.private) GmBadge("私有", RedDim, RedColor)
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(c.bgCard)) {
                    DropdownMenuItem(
                        text = { Text("重命名", fontSize = 14.sp, color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = { onRename(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("编辑信息", fontSize = 14.sp, color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = { onEdit(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("克隆到本地", fontSize = 14.sp, color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Download, null, tint = BlueColor, modifier = Modifier.size(16.dp)) },
                        onClick = { onClone(); showMenu = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!repo.language.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).background(Coral, CircleShape))
                    Text(repo.language, fontSize = 11.sp, color = c.textTertiary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.Star, null, tint = Yellow, modifier = Modifier.size(12.dp))
                Text("${repo.stars}", fontSize = 11.sp, color = c.textTertiary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.Share, null, tint = c.textTertiary, modifier = Modifier.size(12.dp))
                Text("${repo.forks}", fontSize = 11.sp, color = c.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = repo.defaultBranch,
                fontSize = 10.5.sp, color = BlueColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.background(BlueDim, RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

// ─── Dialogs ───────────────────────────────────────────────────

@Composable
private fun DeleteRepoDialog(repoName: String, onConfirm: () -> Unit, onDismiss: () -> Unit, c: GmColors) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("删除仓库", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("此操作不可撤销。请输入仓库名 \"$repoName\" 确认删除：",
                    fontSize = 13.sp, color = c.textSecondary, lineHeight = 20.sp)
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(repoName, color = c.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedColor, unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem,
                    ),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = input == repoName,
                colors = ButtonDefaults.buttonColors(containerColor = RedColor, disabledContainerColor = c.border)) {
                Text("确认删除")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) } },
    )
}

@Composable
private fun RenameRepoDialog(
    currentName: String, owner: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit, c: GmColors,
) {
    var name by remember { mutableStateOf(currentName) }
    val isValid = name.isNotBlank() && name != currentName && name.matches(Regex("[a-zA-Z0-9._-]+"))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = { Text("重命名仓库", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$owner / $currentName → $owner / $name",
                    fontSize = 12.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("新名称") },
                    supportingText = {
                        if (name.isNotBlank() && !name.matches(Regex("[a-zA-Z0-9._-]+")))
                            Text("只允许字母、数字、点、连字符和下划线", color = RedColor, fontSize = 11.sp)
                    },
                    isError = name.isNotBlank() && !name.matches(Regex("[a-zA-Z0-9._-]+")),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Coral, unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem,
                    ),
                )
            }
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

@Composable
private fun EditRepoDialog(
    repo: GHRepo,
    onConfirm: (String, String, List<String>) -> Unit,
    onDismiss: () -> Unit,
    c: GmColors,
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
                GmTextField(value = desc, onValueChange = { desc = it },
                    label = "About", maxLines = 3, c = c)
                GmTextField(value = website, onValueChange = { website = it },
                    label = "Website", c = c)
                GmTextField(value = topicsText, onValueChange = { topicsText = it },
                    label = "Topics（空格分隔）", c = c)
                Text("Topics 示例：kotlin android github",
                    fontSize = 11.sp, color = c.textTertiary)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) } },
    )
}

@Composable
private fun GmTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, c: GmColors, maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = maxLines == 1, maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Coral, unfocusedBorderColor = c.border,
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.bgItem, unfocusedContainerColor = c.bgItem,
            focusedLabelColor = Coral, unfocusedLabelColor = c.textTertiary,
        ),
    )
}
