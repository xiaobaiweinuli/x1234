package com.gitmob.android.ui.repo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gitmob.android.api.*
import com.gitmob.android.data.RepoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RepoDetailState(
    val repo: GHRepo? = null,
    val branches: List<GHBranch> = emptyList(),
    val commits: List<GHCommit> = emptyList(),
    val contents: List<GHContent> = emptyList(),
    val currentPath: String = "",
    val currentBranch: String = "",
    val isStarred: Boolean = false,
    val loading: Boolean = false,
    val contentsLoading: Boolean = false,
    val error: String? = null,
    val tab: Int = 0, // 0=Files 1=Commits 2=Branches 3=PRs 4=Issues
    val prs: List<GHPullRequest> = emptyList(),
    val issues: List<GHIssue> = emptyList(),
)

class RepoDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {

    val owner: String = savedStateHandle["owner"] ?: ""
    val repoName: String = savedStateHandle["repo"] ?: ""

    private val repository = RepoRepository()
    private val _state = MutableStateFlow(RepoDetailState())
    val state = _state.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val r = repository.getRepo(owner, repoName)
                val branches = repository.getBranches(owner, repoName)
                val starred = repository.isStarred(owner, repoName)
                _state.update {
                    it.copy(
                        repo = r,
                        branches = branches,
                        currentBranch = r.defaultBranch,
                        isStarred = starred,
                        loading = false,
                    )
                }
                loadContents("", r.defaultBranch)
                loadCommits(r.defaultBranch)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun loadContents(path: String, ref: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(contentsLoading = true) }
            try {
                val branch = ref ?: _state.value.currentBranch
                val contents = repository.getContents(owner, repoName, path, branch)
                    .sortedWith(compareBy({ it.type != "dir" }, { it.name }))
                _state.update { it.copy(contents = contents, currentPath = path, contentsLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(contentsLoading = false) }
            }
        }
    }

    fun loadCommits(sha: String? = null) {
        viewModelScope.launch {
            try {
                val commits = repository.getCommits(owner, repoName, sha ?: _state.value.currentBranch, 1)
                _state.update { it.copy(commits = commits) }
            } catch (_: Exception) {}
        }
    }

    fun switchBranch(branch: String) {
        _state.update { it.copy(currentBranch = branch, currentPath = "") }
        loadContents("", branch)
        loadCommits(branch)
    }

    fun navigateUp() {
        val path = _state.value.currentPath
        val parent = path.substringBeforeLast("/", "")
        loadContents(parent)
    }

    fun setTab(index: Int) {
        _state.update { it.copy(tab = index) }
        if (index == 3 && _state.value.prs.isEmpty()) loadPRs()
        if (index == 4 && _state.value.issues.isEmpty()) loadIssues()
    }

    fun toggleStar() {
        viewModelScope.launch {
            val isStarred = _state.value.isStarred
            try {
                if (isStarred) repository.unstarRepo(owner, repoName)
                else repository.starRepo(owner, repoName)
                _state.update { it.copy(isStarred = !isStarred) }
            } catch (_: Exception) {}
        }
    }

    fun createBranch(name: String) {
        viewModelScope.launch {
            try {
                val sha = _state.value.branches
                    .firstOrNull { it.name == _state.value.currentBranch }
                    ?.commit?.sha ?: return@launch
                repository.createBranch(owner, repoName, name, sha)
                val branches = repository.getBranches(owner, repoName)
                _state.update { it.copy(branches = branches) }
            } catch (_: Exception) {}
        }
    }

    private fun loadPRs() {
        viewModelScope.launch {
            try {
                val prs = repository.getPullRequests(owner, repoName)
                _state.update { it.copy(prs = prs) }
            } catch (_: Exception) {}
        }
    }

    private fun loadIssues() {
        viewModelScope.launch {
            try {
                val issues = repository.getIssues(owner, repoName)
                _state.update { it.copy(issues = issues) }
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun factory(owner: String, repo: String): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T {
                    val app = checkNotNull(
                        extras[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    )
                    val handle = androidx.lifecycle.SavedStateHandle(
                        mapOf("owner" to owner, "repo" to repo)
                    )
                    return RepoDetailViewModel(app, handle) as T
                }
            }
    }
}
