package com.gitmob.android.ui.repos

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitmob.android.api.GHRepo
import com.gitmob.android.ui.common.*
import com.gitmob.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onRepoClick: (String, String) -> Unit,
    onCreateRepo: () -> Unit,
    onProfileClick: () -> Unit,
    vm: RepoListViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val repos by vm.filteredRepos.collectAsState()

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarImage(url = state.userAvatar, size = 28)
                        Text("GitMob", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Coral)
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary)
                    }
                    IconButton(onClick = onCreateRepo) {
                        Icon(Icons.Default.Add, contentDescription = "New repo", tint = Coral)
                    }
                    IconButton(onClick = vm::loadRepos) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 搜索框
            SearchBar(
                query = state.searchQuery,
                onQueryChange = vm::setSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // 过滤器
            FilterRow(current = state.filterPrivate, onSelect = vm::setFilter)

            when {
                state.loading && repos.isEmpty() -> LoadingBox()
                state.error != null && repos.isEmpty() -> ErrorBox(state.error!!, vm::loadRepos)
                repos.isEmpty() -> EmptyBox("暂无仓库，点击右上角 + 创建")
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(repo = repo, onClick = { onRepoClick(repo.owner.login, repo.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索仓库...", color = TextTertiary, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        },
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BgCard,
            unfocusedContainerColor = BgCard,
            focusedBorderColor = Coral,
            unfocusedBorderColor = Border,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

@Composable
private fun FilterRow(current: Boolean?, onSelect: (Boolean?) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(null to "全部", false to "公开", true to "私有").forEach { (value, label) ->
            FilterChip(
                selected = current == value,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoralDim,
                    selectedLabelColor = Coral,
                    containerColor = BgCard,
                    labelColor = TextSecondary,
                ),
            )
        }
    }
}

@Composable
fun RepoCard(repo: GHRepo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                )
                if (!repo.description.isNullOrBlank()) {
                    Text(
                        text = repo.description,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (repo.private) {
                GmBadge("私有", RedDim, RedColor)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!repo.language.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).background(Coral, RoundedCornerShape(50)))
                    Text(repo.language, fontSize = 11.sp, color = TextTertiary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.Star, null, tint = Yellow, modifier = Modifier.size(12.dp))
                Text("${repo.stars}", fontSize = 11.sp, color = TextTertiary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.Share, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                Text("${repo.forks}", fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = repo.defaultBranch,
                fontSize = 10.5.sp,
                color = BlueColor,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .background(BlueDim, RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
