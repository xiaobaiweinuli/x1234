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
    val tab: Int = 0,
    val prs: List<GHPullRequest> = emptyList(),
    val issues: List<GHIssue> = emptyList(),
    val selectedCommit: GHCommitFull? = null,
    val commitDetailLoading: Boolean = false,
    val selectedFilePatch: Pair<String,String>? = null,  // filename to patch
    val toast: String? = null,
)

class RepoDetailViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    val owner: String = savedStateHandle["owner"] ?: ""
    val repoName: String = savedStateHandle["repo"] ?: ""

    private val repository = RepoRepository()
    private val _state = MutableStateFlow(RepoDetailState())
    val state = _state.asStateFlow()

    init { loadAll() }

    fun loadAll() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val r = repository.getRepo(owner, repoName)
            val branches = repository.getBranches(owner, repoName)
            val starred = repository.isStarred(owner, repoName)
            _state.update { it.copy(repo = r, branches = branches, currentBranch = r.defaultBranch, isStarred = starred, loading = false) }
            loadContents("", r.defaultBranch)
            loadCommits(r.defaultBranch)
            loadPRsAndIssues()
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
        }
    }

    fun loadContents(path: String, ref: String? = null) = viewModelScope.launch {
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

    fun navigateUp() {
        val path = _state.value.currentPath
        val parent = if (path.contains("/")) path.substringBeforeLast("/") else ""
        loadContents(parent)
    }

    fun loadCommits(sha: String? = null) = viewModelScope.launch {
        try {
            val ref = sha ?: _state.value.currentBranch
            val commits = repository.getCommits(owner, repoName, ref)
            _state.update { it.copy(commits = commits) }
        } catch (_: Exception) {}
    }

    private fun loadPRsAndIssues() = viewModelScope.launch {
        try {
            val prs = repository.getPRs(owner, repoName)
            val issues = repository.getIssues(owner, repoName)
            _state.update { it.copy(prs = prs, issues = issues) }
        } catch (_: Exception) {}
    }

    fun switchBranch(branch: String) {
        _state.update { it.copy(currentBranch = branch, currentPath = "") }
        loadContents("", branch)
        loadCommits(branch)
    }

    fun setTab(t: Int) = _state.update { it.copy(tab = t) }

    fun toggleStar() = viewModelScope.launch {
        try {
            if (_state.value.isStarred) repository.unstarRepo(owner, repoName)
            else repository.starRepo(owner, repoName)
            _state.update { it.copy(isStarred = !it.isStarred) }
        } catch (_: Exception) {}
    }

    fun createBranch(name: String) = viewModelScope.launch {
        try {
            val sha = _state.value.branches.find { it.name == _state.value.currentBranch }?.commit?.sha ?: return@launch
            repository.createBranch(owner, repoName, name, sha)
            val branches = repository.getBranches(owner, repoName)
            _state.update { it.copy(branches = branches, toast = "已创建分支 $name") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "创建失败：${e.message}") }
        }
    }

    fun deleteBranch(branch: String) = viewModelScope.launch {
        try {
            repository.deleteBranch(owner, repoName, branch)
            val branches = repository.getBranches(owner, repoName)
            _state.update { it.copy(branches = branches, toast = "已删除分支 $branch") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "删除失败：${e.message}") }
        }
    }

    fun renameBranch(oldName: String, newName: String) = viewModelScope.launch {
        try {
            repository.renameBranch(owner, repoName, oldName, newName)
            val branches = repository.getBranches(owner, repoName)
            val currentBranch = if (_state.value.currentBranch == oldName) newName else _state.value.currentBranch
            _state.update { it.copy(branches = branches, currentBranch = currentBranch, toast = "已重命名为 $newName") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "重命名失败：${e.message}") }
        }
    }

    fun setDefaultBranch(branch: String) = viewModelScope.launch {
        try {
            repository.updateRepo(owner, repoName, com.gitmob.android.api.GHUpdateRepoRequest(defaultBranch = branch))
            val updated = repository.getRepo(owner, repoName)
            _state.update { it.copy(repo = updated, toast = "默认分支已设为 $branch") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "设置失败：${e.message}") }
        }
    }

    fun loadCommitDetail(sha: String) = viewModelScope.launch {
        _state.update { it.copy(commitDetailLoading = true) }
        try {
            val detail = repository.getCommitDetail(owner, repoName, sha)
            _state.update { it.copy(selectedCommit = detail, commitDetailLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(commitDetailLoading = false, toast = "加载详情失败") }
        }
    }

    fun clearCommitDetail() = _state.update { it.copy(selectedCommit = null, selectedFilePatch = null) }

    fun openFilePatch(filename: String, patch: String) =
        _state.update { it.copy(selectedFilePatch = filename to patch) }

    fun closeFilePatch() = _state.update { it.copy(selectedFilePatch = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }

    companion object {
        fun factory(owner: String, repo: String): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T {
                    val app = checkNotNull(extras[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    val handle = androidx.lifecycle.SavedStateHandle(mapOf("owner" to owner, "repo" to repo))
                    return RepoDetailViewModel(app, handle) as T
                }
            }
    }
}
