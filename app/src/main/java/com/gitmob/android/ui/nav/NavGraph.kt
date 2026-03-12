package com.gitmob.android.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.gitmob.android.ui.login.LoginScreen
import com.gitmob.android.ui.repo.RepoDetailScreen
import com.gitmob.android.ui.repo.RepoDetailViewModel
import com.gitmob.android.ui.repos.RepoListScreen
import com.gitmob.android.ui.settings.SettingsScreen
import com.gitmob.android.ui.theme.BgDeep
import com.gitmob.android.ui.theme.TextPrimary
import com.gitmob.android.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Route(val path: String) {
    object Login      : Route("login")
    object RepoList   : Route("repos")
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

@Composable
fun AppNavGraph(
    tokenStorage: TokenStorage,
    initialToken: String?,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    var startDest by remember { mutableStateOf<String?>(null) }
    val currentTheme by tokenStorage.themeMode.collectAsState(initial = ThemeMode.LIGHT)

    LaunchedEffect(Unit) {
        val token = tokenStorage.accessToken.first()
        startDest = if (token.isNullOrBlank()) Route.Login.path else Route.RepoList.path
    }

    if (startDest == null) return

    NavHost(navController = navController, startDestination = startDest!!) {

        composable(Route.Login.path) {
            LoginScreen(
                pendingToken = initialToken,
                onSuccess = {
                    navController.navigate(Route.RepoList.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.RepoList.path) {
            RepoListScreen(
                onRepoClick   = { owner, repo -> navController.navigate(Route.RepoDetail.go(owner, repo)) },
                onCreateRepo  = { navController.navigate(Route.CreateRepo.path) },
                onProfileClick = { navController.navigate(Route.Settings.path) },
            )
        }

        composable(
            route = Route.RepoDetail.path,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo")  { type = NavType.StringType },
            ),
        ) { back ->
            val owner = back.arguments?.getString("owner") ?: ""
            val repo  = back.arguments?.getString("repo")  ?: ""
            RepoDetailScreen(
                owner    = owner,
                repoName = repo,
                onBack   = { navController.popBackStack() },
                onFileClick = { o, r, path, branch ->
                    navController.navigate(Route.FileViewer.go(o, r, path, branch))
                },
                vm = viewModel(factory = RepoDetailViewModel.factory(owner, repo)),
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

        composable(Route.Settings.path) {
            SettingsScreen(
                tokenStorage  = tokenStorage,
                currentTheme  = currentTheme,
                onThemeChange = onThemeChange,
                onBack        = { navController.popBackStack() },
                onLogout      = {
                    navController.navigate(Route.Login.path) {
                        popUpTo(0) { inclusive = true }
                    }
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    owner: String, repo: String, path: String, ref: String, onBack: () -> Unit,
) {
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(path.substringAfterLast("/"), fontWeight = FontWeight.Medium,
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
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
                        color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                }
            }
        }
    }
}
