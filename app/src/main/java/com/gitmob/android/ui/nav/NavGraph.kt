package com.gitmob.android.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.data.RepoRepository
import com.gitmob.android.ui.common.ErrorBox
import com.gitmob.android.ui.common.LoadingBox
import com.gitmob.android.ui.create.CreateRepoScreen
import com.gitmob.android.ui.local.LocalRepoListScreen
import com.gitmob.android.ui.local.LocalRepoViewModel
import com.gitmob.android.ui.login.LoginScreen
import com.gitmob.android.ui.repo.RepoDetailScreen
import com.gitmob.android.ui.repo.RepoDetailViewModel
import com.gitmob.android.ui.repos.RepoListScreen
import com.gitmob.android.ui.settings.SettingsScreen
import com.gitmob.android.ui.theme.Coral
import com.gitmob.android.ui.theme.CoralDim
import com.gitmob.android.ui.theme.LocalGmColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Route(val path: String) {
    object Login      : Route("login")
    object Main       : Route("main")
    object CreateRepo : Route("create_repo")
    object Settings   : Route("settings")
    object RepoDetail : Route("repo/{owner}/{repo}") {
        fun go(owner: String, repo: String) = "repo/$owner/$repo"
    }
    object FileViewer : Route("file/{owner}/{repo}/{branch}?path={path}") {
        fun go(owner: String, repo: String, path: String, branch: String) =
            "file/$owner/$repo/$branch?path=${URLEncoder.encode(path, "UTF-8")}"
    }
}

sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    object Remote : BottomTab("tab_remote", "远程", Icons.Default.Cloud)
    object Local  : BottomTab("tab_local",  "本地", Icons.Default.Folder)
}

@Composable
fun AppNavGraph(
    tokenStorage: TokenStorage,
    initialToken: String?,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    var startDest by remember { mutableStateOf<String?>(null) }
    val currentTheme by tokenStorage.themeMode.collectAsState(initial = ThemeMode.LIGHT)
    val rootEnabled by tokenStorage.rootEnabled.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        val token = tokenStorage.accessToken.first()
        startDest = if (token.isNullOrBlank()) Route.Login.path else Route.Main.path
    }
    if (startDest == null) return

    NavHost(navController = navController, startDestination = startDest!!) {

        composable(Route.Login.path) {
            LoginScreen(
                pendingToken = initialToken,
                onSuccess = {
                    navController.navigate(Route.Main.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
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
                onNavigateToSettings = { navController.navigate(Route.Settings.path) },
                onRepoClick          = { owner, repo ->
                    navController.navigate(Route.RepoDetail.go(owner, repo))
                },
                onCreateRepo = { navController.navigate(Route.CreateRepo.path) },
                localVm      = localVm,
            )
        }

        composable(Route.Settings.path) {
            val scope = rememberCoroutineScope()
            val rootEnabled2 by tokenStorage.rootEnabled.collectAsState(initial = false)
            SettingsScreen(
                tokenStorage  = tokenStorage,
                currentTheme  = currentTheme,
                rootEnabled   = rootEnabled2,
                onThemeChange = onThemeChange,
                onRootToggle  = { enabled ->
                    scope.launch { tokenStorage.setRootEnabled(enabled) }
                },
                onBack   = { navController.popBackStack() },
                onLogout = {
                    scope.launch { tokenStorage.clear() }
                    navController.navigate(Route.Login.path) { popUpTo(0) { inclusive = true } }
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
                onBack = { navController.popBackStack() },
                onFileClick = { o, r, path, branch ->
                    navController.navigate(Route.FileViewer.go(o, r, path, branch))
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
    onCreateRepo: () -> Unit,
    localVm: LocalRepoViewModel,
) {
    val c = LocalGmColors.current
    val tabs = listOf(BottomTab.Remote, BottomTab.Local)
    val tabNavController = rememberNavController()
    val navBackStack by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    // 用 Column 替代 Scaffold：避免嵌套 Scaffold 造成 bottomBar insets 重复
    Column(modifier = Modifier.fillMaxSize()) {

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
                        onProfileClick = onNavigateToSettings,
                        // 克隆操作指向共享的 localVm，切换到本地 Tab 即可看到
                        onCloneRepo    = { url -> localVm.startClone(url) },
                    )
                }
                composable(BottomTab.Local.route) {
                    // 传入共享 VM 实例，而非让屏幕内部自己创建
                    LocalRepoListScreen(rootEnabled = rootEnabled, vm = localVm)
                }
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
                        tabNavController.navigate(tab.route) {
                            popUpTo(tabNavController.graph.startDestinationId) { saveState = true }
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

    LaunchedEffect(path) {
        loading = true
        try { content = repository.getFileContent(owner, repo, path, ref); error = null }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
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
}
