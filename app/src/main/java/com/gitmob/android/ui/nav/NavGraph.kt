package com.gitmob.android.ui.nav

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.create.CreateRepoScreen
import com.gitmob.android.ui.login.LoginScreen
import com.gitmob.android.ui.repo.RepoDetailScreen
import com.gitmob.android.ui.repos.RepoListScreen
import kotlinx.coroutines.flow.first

sealed class Route(val path: String) {
    object Login : Route("login")
    object RepoList : Route("repos")
    object RepoDetail : Route("repo/{owner}/{repo}") {
        fun go(owner: String, repo: String) = "repo/$owner/$repo"
    }
    object CreateRepo : Route("create_repo")
    object FileViewer : Route("file/{owner}/{repo}/{branch}?path={path}") {
        fun go(owner: String, repo: String, path: String, branch: String) =
            "file/$owner/$repo/$branch?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
    }
}

@Composable
fun AppNavGraph(
    tokenStorage: TokenStorage,
    initialToken: String?,
) {
    val navController = rememberNavController()
    var startDest by remember { mutableStateOf<String?>(null) }

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
                onRepoClick = { owner, repo ->
                    navController.navigate(Route.RepoDetail.go(owner, repo))
                },
                onCreateRepo = { navController.navigate(Route.CreateRepo.path) },
                onProfileClick = {
                    // 长期可加 Profile 页
                },
            )
        }

        composable(
            route = Route.RepoDetail.path,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
            )
        ) { back ->
            val owner = back.arguments?.getString("owner") ?: ""
            val repo  = back.arguments?.getString("repo") ?: ""
            RepoDetailScreen(
                owner = owner,
                repoName = repo,
                onBack = { navController.popBackStack() },
                onFileClick = { o, r, path, branch ->
                    navController.navigate(Route.FileViewer.go(o, r, path, branch))
                },
            )
        }

        composable(Route.CreateRepo.path) {
            CreateRepoScreen(
                onBack = { navController.popBackStack() },
                onCreated = { owner, repo ->
                    navController.navigate(Route.RepoDetail.go(owner, repo)) {
                        popUpTo(Route.CreateRepo.path) { inclusive = true }
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
            )
        ) { back ->
            val owner  = back.arguments?.getString("owner") ?: ""
            val repo   = back.arguments?.getString("repo") ?: ""
            val branch = back.arguments?.getString("branch") ?: ""
            val path   = java.net.URLDecoder.decode(back.arguments?.getString("path") ?: "", "UTF-8")
            FileViewerScreen(
                owner = owner, repo = repo, path = path, ref = branch,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
fun FileViewerScreen(owner: String, repo: String, path: String, ref: String, onBack: () -> Unit) {
    val repository = remember { com.gitmob.android.data.RepoRepository() }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        loading = true
        try {
            content = repository.getFileContent(owner, repo, path, ref)
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    androidx.compose.material3.Scaffold(
        containerColor = com.gitmob.android.ui.theme.BgDeep,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        path.substringAfterLast("/"),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        fontSize = 15.sp,
                        color = com.gitmob.android.ui.theme.TextPrimary,
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = com.gitmob.android.ui.theme.TextSecondary
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = com.gitmob.android.ui.theme.BgDeep
                ),
            )
        },
    ) { padding ->
        when {
            loading -> com.gitmob.android.ui.common.LoadingBox(Modifier.padding(padding))
            error != null -> com.gitmob.android.ui.common.ErrorBox(error!!) { }
            else -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.padding(padding),
                ) {
                    item {
                        androidx.compose.material3.Text(
                            text = content,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = com.gitmob.android.ui.theme.TextPrimary,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

private val Icons.AutoMirrored.Filled.ArrowBack get() =
    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack
