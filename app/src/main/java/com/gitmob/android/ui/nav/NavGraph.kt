package com.gitmob.android.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.gitmob.android.auth.ThemeMode
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.data.RepoRepository
import com.gitmob.android.ui.common.ErrorBox
import com.gitmob.android.ui.common.LoadingBox
import com.gitmob.android.ui.create.CreateRepoScreen
import com.gitmob.android.ui.local.LocalRepoDetailScreen
import com.gitmob.android.ui.local.LocalRepoListScreen
import com.gitmob.android.ui.local.LocalRepoViewModel
import com.gitmob.android.ui.filepicker.FilePickerScreen
import com.gitmob.android.ui.filepicker.PickerMode
import com.gitmob.android.ui.login.LoginScreen
import com.gitmob.android.ui.repo.IssueDetailScreen
import com.gitmob.android.ui.repo.IssueDetailViewModel
import com.gitmob.android.ui.repo.RepoDetailScreen
import com.gitmob.android.ui.repo.RepoDetailViewModel
import com.gitmob.android.ui.repos.RepoListScreen
import com.gitmob.android.ui.settings.SettingsScreen
import com.gitmob.android.ui.theme.Coral
import com.gitmob.android.ui.theme.CoralDim
import com.gitmob.android.ui.theme.LocalGmColors
import com.gitmob.android.ui.theme.RedColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import com.gitmob.android.ui.repo.FilePatchInfo
import com.gitmob.android.ui.repo.FileDiffSheet
import com.gitmob.android.ui.common.GmDivider
import androidx.compose.foundation.background
import com.gitmob.android.ui.theme.Green
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward

sealed class Route(val path: String) {
    object Login      : Route("login")
    object Main       : Route("main")
    object CreateRepo : Route("create_repo")
    object Settings   : Route("settings")
    object RepoDetail : Route("repo/{owner}/{repo}") {
        fun go(owner: String, repo: String) = "repo/$owner/$repo"
    }
    object LocalRepoDetail : Route("local_repo/{repoId}") {
        fun go(repoId: String) = "local_repo/$repoId"
    }
    object FileViewer : Route("file/{owner}/{repo}/{branch}?path={path}") {
        fun go(owner: String, repo: String, path: String, branch: String) =
            "file/$owner/$repo/$branch?path=${URLEncoder.encode(path, "UTF-8")}"
    }
    object IssueDetail : Route("issue/{owner}/{repo}/{issueNumber}") {
        fun go(owner: String, repo: String, issueNumber: Int) = "issue/$owner/$repo/$issueNumber"
    }
}

sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    object Remote   : BottomTab("tab_remote",   "远程",   Icons.Default.Cloud)
    object Local    : BottomTab("tab_local",    "本地",   Icons.Default.Folder)
    object Settings : BottomTab("tab_settings", "设置",   Icons.Default.Settings)
}

@Composable
fun AppNavGraph(
    tokenStorage: TokenStorage,
    initialToken: String?,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    var isReauth   by remember { mutableStateOf(false) }
    val currentTheme by tokenStorage.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val rootEnabled by tokenStorage.rootEnabled.collectAsState(initial = false)
    val tabStepBackEnabled by tokenStorage.tabStepBack.collectAsState(initial = false)

    // 用 collectAsState(null) 替代 LaunchedEffect + .first()
    // DataStore 第一帧通常 <16ms 即可发射，避免一整帧的 null 白屏闪烁
    val tokenState by tokenStorage.accessToken.collectAsState(initial = null)

    // 监听 401 Token 失效事件
    LaunchedEffect(Unit) {
        ApiClient.tokenExpired.collect {
            navController.navigate(Route.Login.path) { popUpTo(0) { inclusive = true } }
        }
    }

    // 首次收到 token 值前不渲染任何内容（DataStore 通常 <16ms）
    // tokenState: null = 等待首次 emit（initial），非 null = DataStore 真实值
    // Flow<String?> 中键不存在时 emit null，所以：null→Login，有值→Main
    if (tokenState == null) return   // 首帧 initial，跳过渲染避免闪烁
    val startDest = if (tokenState.isNullOrBlank()) Route.Login.path else Route.Main.path

    NavHost(navController = navController, startDestination = startDest) {

        composable(Route.Login.path) {
            LoginScreen(
                pendingToken = initialToken,
                isReauth = isReauth,
                onSuccess = {
                    isReauth = false
                    // 若 Main 已在返回栈中（添加账号场景），直接弹回；否则全量跳转
                    val mainInBackStack = navController.previousBackStackEntry?.destination?.route == Route.Main.path
                    if (mainInBackStack) {
                        navController.popBackStack(Route.Main.path, inclusive = false)
                    } else {
                        navController.navigate(Route.Main.path) {
                            popUpTo(Route.Login.path) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Route.Main.path) {
            // ViewModel 在这里创建，作用域是 Route.Main 的 BackStackEntry
            // 两个 Tab 都接收同一实例，克隆操作因此生效
            val localVm: LocalRepoViewModel = viewModel()
            MainScreen(
                tokenStorage         = tokenStorage,
                currentTheme         = currentTheme,
                rootEnabled          = rootEnabled,
                onThemeChange        = onThemeChange,
                onNavigateToSettings = { /* 已移入 Tab */ },
                onRepoClick          = { owner, repo ->
                    navController.navigate(Route.RepoDetail.go(owner, repo))
                },
                onLocalRepoClick     = { repoId ->
                    navController.navigate(Route.LocalRepoDetail.go(repoId))
                },
                onCreateRepo = { navController.navigate(Route.CreateRepo.path) },
                localVm      = localVm,
                onLogout     = {
                    isReauth = true
                    navController.navigate(Route.Login.path) { popUpTo(0) { inclusive = true } }
                },
                onSwitchAccount = {
                    navController.navigate(Route.Main.path) {
                        popUpTo(Route.Main.path) { inclusive = true }
                    }
                },
                onAddAccount = {
                    navController.navigate(Route.Login.path) {
                        popUpTo(Route.Main.path) { saveState = true }
                    }
                },
            )
        }

        composable(Route.CreateRepo.path) {
            CreateRepoScreen(
                onBack    = { navController.popBackStack() },
                onCreated = { owner, repo ->
                    navController.navigate(Route.RepoDetail.go(owner, repo)) {
                        popUpTo(Route.CreateRepo.path) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "repo/{owner}/{repo}",
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo")  { type = NavType.StringType },
            ),
        ) { back ->
            val owner = back.arguments?.getString("owner") ?: ""
            val repo  = back.arguments?.getString("repo")  ?: ""
            RepoDetailScreen(
                owner = owner, repoName = repo,
                tabStepBackEnabled = tabStepBackEnabled,
                onBack = { navController.popBackStack() },
                onFileClick = { o, r, path, branch ->
                    navController.navigate(Route.FileViewer.go(o, r, path, branch))
                },
                onIssueClick = { issueNumber ->
                    navController.navigate(Route.IssueDetail.go(owner, repo, issueNumber))
                },
                vm = viewModel(factory = RepoDetailViewModel.factory(owner, repo)),
            )
        }

        composable(
            route = Route.FileViewer.path,
            arguments = listOf(
                navArgument("owner")  { type = NavType.StringType },
                navArgument("repo")   { type = NavType.StringType },
                navArgument("branch") { type = NavType.StringType },
                navArgument("path")   { type = NavType.StringType; defaultValue = "" },
            ),
        ) { back ->
            val owner  = back.arguments?.getString("owner")  ?: ""
            val repo   = back.arguments?.getString("repo")   ?: ""
            val branch = back.arguments?.getString("branch") ?: ""
            val path   = URLDecoder.decode(back.arguments?.getString("path") ?: "", "UTF-8")
            FileViewerScreen(owner = owner, repo = repo, path = path, ref = branch,
                onBack = { navController.popBackStack() })
        }

        composable(
            route = "issue/{owner}/{repo}/{issueNumber}",
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("issueNumber") { type = NavType.IntType },
            ),
        ) { back ->
            val owner = back.arguments?.getString("owner") ?: ""
            val repo = back.arguments?.getString("repo") ?: ""
            val issueNumber = back.arguments?.getInt("issueNumber") ?: 0
            IssueDetailScreen(
                owner = owner,
                repoName = repo,
                issueNumber = issueNumber,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "local_repo/{repoId}",
            arguments = listOf(
                navArgument("repoId") { type = NavType.StringType },
            ),
        ) { back ->
            val repoId = back.arguments?.getString("repoId") ?: ""
            LocalRepoDetailScreen(
                repoId = repoId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

// ─── MainScreen：不用嵌套 Scaffold，改用 Column+Box 避免双层 padding ──────────

@Composable
private fun MainScreen(
    tokenStorage: TokenStorage,
    currentTheme: ThemeMode,
    rootEnabled: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateToSettings: () -> Unit,
    onRepoClick: (String, String) -> Unit,
    onLocalRepoClick: (String) -> Unit,
    onCreateRepo: () -> Unit,
    localVm: LocalRepoViewModel,
    onLogout: () -> Unit,
    onSwitchAccount: (com.gitmob.android.auth.AccountInfo) -> Unit = {},
    onAddAccount: () -> Unit = {},
) {
    val c = LocalGmColors.current
    val tabs = listOf(BottomTab.Remote, BottomTab.Local, BottomTab.Settings)
    val tabNavController = rememberNavController()
    val navBackStack by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    // 用 Column 替代 Scaffold：避免嵌套 Scaffold 造成 bottomBar insets 重复
    Column(modifier = Modifier.fillMaxSize()) {

        // 观察共享 VM 的克隆状态，在此层级弹出文件选择器
        val vmState by localVm.state.collectAsState()
        val vmBookmarks by localVm.customBookmarks.collectAsState()
        
        var showNewProjectPicker by remember { mutableStateOf(false) }
        var newProjectParentDir by remember { mutableStateOf("") }
        var newProjectDialog by remember { mutableStateOf(false) }
        var newProjectName by remember { mutableStateOf("") }

        // 上部内容区域（Tab 页面各自有自己的 Scaffold + TopAppBar）
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = tabNavController,
                startDestination = BottomTab.Remote.route,
            ) {
                composable(BottomTab.Remote.route) {
                    RepoListScreen(
                        onRepoClick    = onRepoClick,
                        onCreateRepo   = onCreateRepo,
                        onCloneRepo    = { url -> localVm.startClone(url) },
                    )
                }
                composable(BottomTab.Local.route) {
                    LocalRepoListScreen(
                        rootEnabled = rootEnabled, 
                        vm = localVm,
                        onShowNewProjectPicker = { showNewProjectPicker = true },
                        onRepoClick = { repoId -> onLocalRepoClick(repoId) }
                    )
                }
                composable(BottomTab.Settings.route) {
                    val settingsScope = rememberCoroutineScope()
                    val rootEnabled2 by tokenStorage.rootEnabled.collectAsState(initial = false)
                    val tabStepBackEnabled by tokenStorage.tabStepBack.collectAsState(initial = false)
                    SettingsScreen(
                        tokenStorage  = tokenStorage,
                        currentTheme  = currentTheme,
                        rootEnabled   = rootEnabled2,
                        tabStepBackEnabled = tabStepBackEnabled,
                        onThemeChange = onThemeChange,
                        onRootToggle  = { enabled ->
                            settingsScope.launch { tokenStorage.setRootEnabled(enabled) }
                        },
                        onTabStepBackToggle = { enabled ->
                            settingsScope.launch { tokenStorage.setTabStepBack(enabled) }
                        },
                        onBack   = { /* 底部 tab，无需返回 */ },
                        onLogout = {
                            // SettingsScreen 内部已完成 revokeToken + removeAccount
                            onLogout()
                        },
                        onSwitchAccount = { account ->
                            settingsScope.launch {
                                com.gitmob.android.api.ApiClient.rebuild()
                            }
                            onSwitchAccount(account)
                        },
                        onAddAccount = { onAddAccount() },
                    )
                }
            }
            
            // 导入目录文件选择器：覆盖层显示在 Tab 内容之上
            if (vmState.showFilePicker) {
                FilePickerScreen(
                    title = "导入本地目录",
                    mode = PickerMode.DIRECTORY,
                    rootEnabled = rootEnabled,
                    customBookmarks = vmBookmarks,
                    onAddBookmark    = { bm -> localVm.addBookmark(bm) },
                    onRemoveBookmark = { bm -> localVm.removeBookmark(bm) },
                    onConfirm = { path, _ -> localVm.importDirectory(path) },
                    onDismiss = localVm::hideFilePicker,
                )
            }
            
            // 新建项目父目录选择器：覆盖层显示在 Tab 内容之上
            if (showNewProjectPicker) {
                FilePickerScreen(
                    title = "选择项目父目录",
                    mode = PickerMode.DIRECTORY,
                    rootEnabled = rootEnabled,
                    customBookmarks = vmBookmarks,
                    onAddBookmark    = { bm -> localVm.addBookmark(bm) },
                    onRemoveBookmark = { bm -> localVm.removeBookmark(bm) },
                    onConfirm = { path, _ ->
                        newProjectParentDir = path
                        showNewProjectPicker = false
                        newProjectDialog = true
                    },
                    onDismiss = { showNewProjectPicker = false },
                )
            }
            
            // 克隆文件选择器：覆盖层显示在 Tab 内容之上
            if (vmState.showClonePicker) {
                FilePickerScreen(
                    title = "选择克隆目标目录",
                    mode = PickerMode.DIRECTORY,
                    rootEnabled = rootEnabled,
                    customBookmarks = vmBookmarks,
                    onAddBookmark    = { bm -> localVm.addBookmark(bm) },
                    onRemoveBookmark = { bm -> localVm.removeBookmark(bm) },
                    onConfirm = { path, _ ->
                        // 立即关闭文件选择器，然后在后台进行克隆
                        localVm.hideClonePicker()
                        // 确保路径格式正确
                        val targetPath = if (path.endsWith("/")) path else "$path"
                        localVm.cloneRepo(vmState.pendingCloneUrl, targetPath)
                    },
                    onDismiss = localVm::hideClonePicker,
                )
            }
            
            // 新建项目对话框
            val c = LocalGmColors.current
            if (newProjectDialog) {
                AlertDialog(
                    onDismissRequest = { newProjectDialog = false },
                    containerColor = c.bgCard,
                    title = { Text("新建本地项目", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("父目录：$newProjectParentDir",
                                fontSize = 11.sp, color = c.textTertiary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
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
                                    localVm.createLocalProject(newProjectParentDir, newProjectName)
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

        // 底部导航栏（固定在 Column 底部，不参与 Scaffold inset 计算）
        NavigationBar(
            containerColor = c.bgCard,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = {
                        // ── 有 picker 打开时的特殊处理 ──
                        val anyPickerOpen = vmState.showFilePicker ||
                                            vmState.showClonePicker ||
                                            showNewProjectPicker

                        if (anyPickerOpen) {
                            // 关闭所有 picker
                            localVm.hideFilePicker()
                            localVm.hideClonePicker()
                            showNewProjectPicker = false

                            if (tab.route == BottomTab.Local.route) {
                                // 点击本地 Tab → 仅关闭 picker，留在本地 Tab
                                return@NavigationBarItem
                            }
                            // 点击其他 Tab → 关闭 picker 并切换
                        }

                        tabNavController.navigate(tab.route) {
                            popUpTo(tabNavController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, null) },
                    label = { Text(tab.label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = Coral,
                        selectedTextColor   = Coral,
                        unselectedIconColor = c.textTertiary,
                        unselectedTextColor = c.textTertiary,
                        indicatorColor      = CoralDim,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    owner: String, repo: String, path: String, ref: String, onBack: () -> Unit,
) {
    val c = LocalGmColors.current
    val repository = remember { RepoRepository() }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }
    var fileInfo by remember { mutableStateOf<com.gitmob.android.api.GHContent?>(null) }
    
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCommitMessageDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editContent by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var historyCommits by remember { mutableStateOf<List<com.gitmob.android.api.GHCommit>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var selectedCommitForHistory by remember { mutableStateOf<com.gitmob.android.api.GHCommitFull?>(null) }
    var commitDetailLoading by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(path) {
        loading = true
        try { 
            fileInfo = repository.getFileInfo(owner, repo, path, ref)
            content = repository.getFileContent(owner, repo, path, ref)
            editContent = content
            error = null 
        } catch (e: Exception) { 
            error = e.message 
        } finally { 
            loading = false 
        }
    }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(path.substringAfterLast("/"), fontWeight = FontWeight.Medium,
                        fontSize = 15.sp, color = c.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val url = "https://github.com/$owner/$repo/blob/$ref/$path"
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "分享"))
                    }) {
                        Icon(Icons.Default.Share, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑", fontSize = 13.sp, color = c.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    editContent = content
                                    showEditDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除", fontSize = 13.sp, color = RedColor) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = RedColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("历史记录", fontSize = 13.sp, color = c.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.History,
                                        null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showHistory = true
                                    historyLoading = true
                                    scope.launch {
                                        try {
                                            historyCommits = repository.getCommits(owner, repo, ref, path)
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            historyLoading = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgDeep),
            )
        },
    ) { padding ->
        when {
            loading       -> LoadingBox(Modifier.padding(padding))
            error != null -> ErrorBox(error!!) {}
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(padding),
            ) {
                item {
                    Text(content, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = c.textPrimary, lineHeight = 18.sp)
                }
            }
        }
    }
    
    if (showEditDialog) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showEditDialog = false },
            sheetState = sheetState,
            containerColor = c.bgCard,
            dragHandle = null,
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("编辑文件", color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    IconButton(onClick = { showEditDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = c.textSecondary)
                    }
                }
                
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 400.dp),
                    label = { Text("文件内容") },
                    minLines = 15,
                    maxLines = 50,
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
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { showEditDialog = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", color = c.textSecondary)
                    }
                    Button(
                        onClick = {
                            showEditDialog = false
                            commitMessage = "Update $path"
                            showCommitMessageDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    
    if (showCommitMessageDialog) {
        var commitMsg by remember { mutableStateOf(commitMessage) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCommitMessageDialog = false },
            containerColor = c.bgCard,
            title = { Text("提交信息", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = commitMsg,
                    onValueChange = { commitMsg = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("提交信息") },
                    placeholder = { Text("Describe your changes...", color = c.textTertiary) },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (commitMsg.isNotBlank()) {
                            showCommitMessageDialog = false
                            scope.launch {
                                try {
                                    repository.createOrUpdateFile(
                                        owner = owner,
                                        repo = repo,
                                        path = path,
                                        message = commitMsg,
                                        content = editContent,
                                        sha = fileInfo?.sha,
                                        branch = ref,
                                    )
                                    content = editContent
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        }
                    },
                    enabled = commitMsg.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitMessageDialog = false }) {
                    Text("取消", color = c.textSecondary)
                }
            },
        )
    }
    
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = c.bgCard,
            icon = {
                Icon(Icons.Default.Delete, null, tint = RedColor, modifier = Modifier.size(28.dp))
            },
            title = { Text("确认删除", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text("确定要删除文件 \"${path.substringAfterLast("/")}\" 吗？此操作无法撤销。", fontSize = 13.sp, color = c.textSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            try {
                                repository.deleteFile(
                                    owner = owner,
                                    repo = repo,
                                    path = path,
                                    message = "Delete $path",
                                    sha = fileInfo?.sha ?: "",
                                    branch = ref,
                                )
                                onBack()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = c.textSecondary)
                }
            },
        )
    }
    
    if (showHistory) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = sheetState,
            containerColor = c.bgCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("历史记录", color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    IconButton(onClick = { showHistory = false }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                GmDivider()
                Spacer(Modifier.height(8.dp))
                
                if (historyLoading) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (historyCommits.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无历史记录", fontSize = 13.sp, color = c.textTertiary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(historyCommits) { commit ->
                            Column(
                                modifier = Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp))
                                    .clickable {
                                        scope.launch {
                                            commitDetailLoading = true
                                            try {
                                                selectedCommitForHistory = repository.getCommitDetail(owner, repo, commit.sha)
                                            } catch (e: Exception) {
                                                error = e.message
                                            } finally {
                                                commitDetailLoading = false
                                            }
                                        }
                                    }.padding(12.dp),
                            ) {
                                Text(commit.commit.message.lines().first(), fontSize = 13.sp,
                                    color = c.textPrimary, fontWeight = FontWeight.Medium, maxLines = 2)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (commit.author != null) {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp).clip(CircleShape)
                                        )
                                    }
                                    Text(commit.commit.author.name, fontSize = 11.sp, color = c.textSecondary)
                                    Spacer(Modifier.weight(1f))
                                    Text(commit.sha.take(7), fontSize = 10.sp, color = Coral,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.background(CoralDim, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp))
                                    Text(
                                        try { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                            .format(java.time.OffsetDateTime.parse(commit.commit.author.date))
                                        } catch (_: Exception) { commit.commit.author.date },
                                        fontSize = 11.sp, color = c.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    selectedCommitForHistory?.let { commit ->
        val selectedFilePatchForHistory = remember { mutableStateOf<FilePatchInfo?>(null) }

        selectedFilePatchForHistory.value?.let { info ->
            FileDiffSheet(info = info, c = c, vm = viewModel(factory = RepoDetailViewModel.factory(owner, repo)),
                onDismiss = { selectedFilePatchForHistory.value = null })
        }

        ModalBottomSheet(
            onDismissRequest = { selectedCommitForHistory = null },
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
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        commit.sha.take(7),
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
                Spacer(Modifier.height(12.dp))
                GmDivider()
                Spacer(Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (commit.author != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                    }
                    Column {
                        Text(commit.commit.author.name, fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            try { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .format(java.time.OffsetDateTime.parse(commit.commit.author.date))
                            } catch (_: Exception) { commit.commit.author.date },
                            fontSize = 11.sp, color = c.textTertiary
                        )
                    }
                }
                
                if (commit.files?.isNotEmpty() == true) {
                    Spacer(Modifier.height(16.dp))
                    GmDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("变更文件", fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    
                    commit.files?.forEach { file ->
                        val status = when (file.status) {
                            "added" -> "新增"
                            "removed" -> "删除"
                            "modified" -> "修改"
                            "renamed" -> "重命名"
                            else -> file.status
                        }
                        val statusColor = when (file.status) {
                            "added" -> Green
                            "removed" -> RedColor
                            else -> c.textSecondary
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.bgItem, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (file.patch != null) {
                                        selectedFilePatchForHistory.value = FilePatchInfo(
                                            filename         = file.filename,
                                            patch            = file.patch!!,
                                            additions        = file.additions,
                                            deletions        = file.deletions,
                                            status           = file.status,
                                            parentSha        = commit.parentSha,
                                            previousFilename = file.previousFilename,
                                            owner            = owner,
                                            repoName         = repo,
                                            currentSha       = commit.sha,
                                        )
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (file.status == "added") Icons.Default.Add else if (file.status == "removed") Icons.Default.Remove else Icons.Default.Edit,
                                null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.filename, fontSize = 12.sp, color = c.textPrimary, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(status, fontSize = 11.sp, color = statusColor)
                                    if (file.additions > 0) Text("+${file.additions}", fontSize = 11.sp, color = Green)
                                    if (file.deletions > 0) Text("-${file.deletions}", fontSize = 11.sp, color = RedColor)
                                }
                            }
                            if (file.patch != null) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
