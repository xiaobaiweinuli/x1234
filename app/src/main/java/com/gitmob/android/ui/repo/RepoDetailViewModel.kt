package com.gitmob.android.ui.repo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gitmob.android.api.*
import com.gitmob.android.auth.TokenStorage
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
    // Reset / Revert 操作状态
    val gitOpInProgress: Boolean = false,   // 操作进行中（显示 loading）
    val gitOpResult: GitOpResult? = null,   // 操作结果（成功/失败弹窗）
    val toast: String? = null,
    // 用户 / 组织，用于仓库转移
    val userLogin: String = "",
    val userAvatar: String = "",
    val userOrgs: List<GHOrg> = emptyList(),
    // Releases
    val releases: List<GHRelease> = emptyList(),
    // GitHub Actions
    val workflows: List<GHWorkflow> = emptyList(),
    val selectedWorkflow: GHWorkflow? = null,
    val allWorkflowRuns: List<GHWorkflowRun> = emptyList(),  // 所有工作流运行记录
    val workflowRuns: List<GHWorkflowRun> = emptyList(),       // 当前显示的运行记录（可能已筛选）
    val selectedWorkflowRun: GHWorkflowRun? = null,
    val workflowJobs: List<GHWorkflowJob> = emptyList(),
    val workflowLogs: Map<String, String>? = null,  // 解析后的日志，key 为文件名
    val workflowLogsLoading: Boolean = false,
    val workflowInputs: List<WorkflowInput> = emptyList(),
    val workflowInputsLoading: Boolean = false,
    // 各Tab刷新状态
    val filesRefreshing: Boolean = false,
    val commitsRefreshing: Boolean = false,
    val branchesRefreshing: Boolean = false,
    val actionsRefreshing: Boolean = false,
    val releasesRefreshing: Boolean = false,
    val prsRefreshing: Boolean = false,
    val issuesRefreshing: Boolean = false,
    // Issues筛选状态
    val issueFilterState: IssueFilterState = IssueFilterState(),
)

/**
 * Issue筛选状态
 */
data class IssueFilterState(
    val status: IssueStatusFilter = IssueStatusFilter.OPEN,
    val sortBy: IssueSortBy = IssueSortBy.NEWEST,
    val selectedLabels: Set<String> = emptySet(),
    val selectedAuthors: Set<String> = emptySet(),
    val selectedAssignees: Set<String> = emptySet(),
    val selectedMilestones: Set<String> = emptySet(),
)

/**
 * Issue状态筛选
 */
enum class IssueStatusFilter(val displayName: String) {
    OPEN("打开"),
    CLOSED("关闭"),
    ALL("所有");
}

/**
 * Issue排序方式
 */
enum class IssueSortBy(val displayName: String) {
    NEWEST("最新"),
    OLDEST("最早"),
    MOST_COMMENTS("最多评论"),
    LEAST_COMMENTS("最少评论");
}

/** Reset / Revert 操作结果 */
data class GitOpResult(
    val success: Boolean,
    val opName: String,       // "回滚" / "撤销"
    val detail: String,       // 成功/失败详情
    val newSha: String? = null,
)

class RepoDetailViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    val owner: String = savedStateHandle["owner"] ?: ""
    val repoName: String = savedStateHandle["repo"] ?: ""

    private val repository = RepoRepository()
    private val tokenStorage = TokenStorage(app)
    private val _state = MutableStateFlow(RepoDetailState())
    val state = _state.asStateFlow()

    init {
        // 当前登录用户与组织（用于仓库转移等）
        viewModelScope.launch {
            tokenStorage.userProfile.collect { profile ->
                if (profile != null) {
                    _state.update {
                        it.copy(
                            userLogin  = profile.first,
                            userAvatar = profile.third,
                        )
                    }
                    loadOrgs()
                }
            }
        }
        loadAll()
    }

    private fun loadOrgs() = viewModelScope.launch {
        try {
            val orgs = repository.getUserOrgs()
            _state.update { it.copy(userOrgs = orgs) }
        } catch (_: Exception) {}
    }

    fun loadAll(forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val r = repository.getRepo(owner, repoName, forceRefresh)
            val branches = repository.getBranches(owner, repoName, forceRefresh)
            val starred = repository.isStarred(owner, repoName)
            _state.update { it.copy(repo = r, branches = branches, currentBranch = r.defaultBranch, isStarred = starred, loading = false) }
            loadContents("", r.defaultBranch, forceRefresh)
            loadCommits(r.defaultBranch, forceRefresh = forceRefresh)
            loadPRsAndIssues()
            loadReleases()
            loadWorkflows()
            loadWorkflowRuns(null)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
        }
    }

    fun loadContents(path: String, ref: String? = null, forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(contentsLoading = true, filesRefreshing = forceRefresh) }
        try {
            val branch = ref ?: _state.value.currentBranch
            val contents = repository.getContents(owner, repoName, path, branch, forceRefresh)
                .sortedWith(compareBy({ it.type != "dir" }, { it.name }))
            _state.update { it.copy(contents = contents, currentPath = path, contentsLoading = false, filesRefreshing = false) }
        } catch (e: Exception) {
            _state.update { it.copy(contentsLoading = false, filesRefreshing = false) }
        }
    }

    fun navigateUp() {
        val path = _state.value.currentPath
        val parent = if (path.contains("/")) path.substringBeforeLast("/") else ""
        loadContents(parent)
    }

    fun loadCommits(sha: String? = null, forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(commitsRefreshing = forceRefresh) }
        try {
            val ref = sha ?: _state.value.currentBranch
            val commits = repository.getCommits(owner, repoName, ref, forceRefresh)
            _state.update { it.copy(commits = commits, commitsRefreshing = false) }
        } catch (_: Exception) {
            _state.update { it.copy(commitsRefreshing = false) }
        }
    }

    private fun loadPRsAndIssues() = viewModelScope.launch {
        try {
            val prs = repository.getPRs(owner, repoName)
            val issues = repository.getIssues(owner, repoName)
            _state.update { it.copy(prs = prs, issues = issues) }
        } catch (_: Exception) {}
    }

    /** 刷新分支列表 */
    fun refreshBranches() = viewModelScope.launch {
        _state.update { it.copy(branchesRefreshing = true) }
        try {
            val branches = repository.getBranches(owner, repoName, forceRefresh = true)
            _state.update { it.copy(branches = branches, branchesRefreshing = false) }
        } catch (_: Exception) {
            _state.update { it.copy(branchesRefreshing = false) }
        }
    }

    /** 刷新PR列表 */
    fun refreshPRs() = viewModelScope.launch {
        _state.update { it.copy(prsRefreshing = true) }
        try {
            val prs = repository.getPRs(owner, repoName)
            _state.update { it.copy(prs = prs, prsRefreshing = false) }
        } catch (_: Exception) {
            _state.update { it.copy(prsRefreshing = false) }
        }
    }

    /** 刷新Issues列表 */
    fun refreshIssues() = viewModelScope.launch {
        _state.update { it.copy(issuesRefreshing = true) }
        try {
            val issues = repository.getIssues(owner, repoName)
            _state.update { it.copy(issues = issues, issuesRefreshing = false) }
        } catch (_: Exception) {
            _state.update { it.copy(issuesRefreshing = false) }
        }
    }

    fun deleteIssue(issueNumber: Int) = viewModelScope.launch {
        try {
            val success = repository.deleteIssue(owner, repoName, issueNumber)
            if (success) {
                _state.update { it.copy(issues = it.issues.filter { issue -> issue.number != issueNumber }, toast = "已删除 Issue #$issueNumber") }
            } else {
                _state.update { it.copy(toast = "删除失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "删除失败：${e.message}") }
        }
    }

    /**
     * 获取所有可用的标签
     */
    fun getAllLabels(): Set<String> {
        return _state.value.issues.flatMap { it.labels }.map { it.name }.toSet()
    }

    /**
     * 获取所有作者
     */
    fun getAllAuthors(): Set<String> {
        return _state.value.issues.map { it.user.login }.toSet()
    }

    /**
     * 获取所有受理人
     */
    fun getAllAssignees(): Set<String> {
        return emptySet()
    }

    /**
     * 获取所有里程碑
     */
    fun getAllMilestones(): Set<String> {
        return emptySet()
    }

    /**
     * 设置Issue状态筛选
     */
    fun setIssueStatusFilter(status: IssueStatusFilter) {
        _state.update { it.copy(issueFilterState = it.issueFilterState.copy(status = status)) }
    }

    /**
     * 设置Issue排序方式
     */
    fun setIssueSortBy(sortBy: IssueSortBy) {
        _state.update { it.copy(issueFilterState = it.issueFilterState.copy(sortBy = sortBy)) }
    }

    /**
     * 切换标签选择
     */
    fun toggleIssueLabel(label: String) {
        _state.update {
            val current = it.issueFilterState.selectedLabels
            val newSet = if (current.contains(label)) current - label else current + label
            it.copy(issueFilterState = it.issueFilterState.copy(selectedLabels = newSet))
        }
    }

    /**
     * 切换作者选择
     */
    fun toggleIssueAuthor(author: String) {
        _state.update {
            val current = it.issueFilterState.selectedAuthors
            val newSet = if (current.contains(author)) current - author else current + author
            it.copy(issueFilterState = it.issueFilterState.copy(selectedAuthors = newSet))
        }
    }

    /**
     * 切换受理人选择
     */
    fun toggleIssueAssignee(assignee: String) {
        _state.update {
            val current = it.issueFilterState.selectedAssignees
            val newSet = if (current.contains(assignee)) current - assignee else current + assignee
            it.copy(issueFilterState = it.issueFilterState.copy(selectedAssignees = newSet))
        }
    }

    /**
     * 切换里程碑选择
     */
    fun toggleIssueMilestone(milestone: String) {
        _state.update {
            val current = it.issueFilterState.selectedMilestones
            val newSet = if (current.contains(milestone)) current - milestone else current + milestone
            it.copy(issueFilterState = it.issueFilterState.copy(selectedMilestones = newSet))
        }
    }

    /**
     * 清除所有筛选条件
     */
    fun clearIssueFilters() {
        _state.update { it.copy(issueFilterState = IssueFilterState()) }
    }

    /**
     * 创建新的Issue
     */
    fun createIssue(title: String, body: String) = viewModelScope.launch {
        try {
            val newIssue = repository.createIssue(owner, repoName, title, body.ifBlank { null })
            _state.update {
                it.copy(
                    issues = listOf(newIssue) + it.issues,
                    toast = "Issue创建成功"
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "创建失败：${e.message}") }
        }
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

    fun renameRepo(newName: String, onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            val updated = repository.updateRepo(owner, repoName, GHUpdateRepoRequest(name = newName))
            _state.update { it.copy(repo = updated, toast = "已重命名为 $newName") }
            onSuccess()
        } catch (e: Exception) {
            _state.update { it.copy(toast = "重命名失败：${e.message}") }
        }
    }

    fun editRepo(desc: String, website: String, topics: List<String>) = viewModelScope.launch {
        try {
            repository.updateRepo(owner, repoName, GHUpdateRepoRequest(description = desc, homepage = website))
            repository.replaceTopics(owner, repoName, topics)
            val updated = repository.getRepo(owner, repoName, forceRefresh = true)
            _state.update { it.copy(repo = updated, toast = "已更新仓库信息") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "更新失败：${e.message}") }
        }
    }

    fun updateVisibility(makePrivate: Boolean) = viewModelScope.launch {
        try {
            repository.updateRepo(owner, repoName, GHUpdateRepoRequest(private = makePrivate))
            val updated = repository.getRepo(owner, repoName, forceRefresh = true)
            _state.update {
                it.copy(
                    repo = updated,
                    toast = if (makePrivate) "已设置为私有仓库" else "已设置为公开仓库",
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "更新可见性失败：${e.message}") }
        }
    }

    fun deleteRepo(onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            repository.deleteRepo(owner, repoName)
            _state.update { it.copy(toast = "已删除仓库 $repoName") }
            onSuccess()
        } catch (e: Exception) {
            _state.update { it.copy(toast = "删除失败：${e.message}") }
        }
    }

    fun transferRepo(targetOwner: String, newName: String?, onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            repository.transferRepo(owner, repoName, newOwner = targetOwner, newName = newName)
            _state.update {
                it.copy(
                    toast = if (newName.isNullOrBlank())
                        "已发起转移到 $targetOwner"
                    else
                        "已发起转移到 $targetOwner/$newName",
                )
            }
            onSuccess()
        } catch (e: Exception) {
            _state.update { it.copy(toast = "转移失败：${e.message}") }
        }
    }

    fun checkNameAvailability(
        owner: String,
        name: String,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val exists = repository.checkRepoExists(owner, name)
                // exists == true 表示已存在；我们需要“可用”= false
                onResult(!exists)
            } catch (_: Exception) {
                onResult(false)
            }
        }
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
            val updated = repository.getRepo(owner, repoName, forceRefresh = true)
            _state.update { it.copy(repo = updated, currentBranch = branch, toast = "默认分支已设为 $branch") }
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
    fun clearGitOpResult() = _state.update { it.copy(gitOpResult = null) }

    /**
     * 回滚：强制将当前分支的 HEAD 指向目标 SHA（服务端操作，重写历史）
     * 等同于 git reset --hard <sha> && git push -f origin <branch>
     */
    fun resetToCommit(sha: String) = viewModelScope.launch {
        val branch = _state.value.currentBranch.ifBlank { return@launch }
        _state.update { it.copy(gitOpInProgress = true) }
        try {
            repository.resetBranchToCommit(owner, repoName, branch, sha)
            // 失效缓存，重新加载提交列表
            repository.invalidateCommitCache(owner, repoName, branch)
            loadCommits(branch)
            _state.update {
                it.copy(
                    gitOpInProgress = false,
                    gitOpResult = GitOpResult(true, "回滚", "分支 $branch 已回滚到 ${sha.take(7)}"),
                    selectedCommit  = null,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    gitOpInProgress = false,
                    gitOpResult = GitOpResult(false, "回滚", e.message ?: "回滚失败"),
                )
            }
        }
    }

    /**
     * 撤销（Revert）：在当前 HEAD 上创建一个新的 revert commit
     * 不重写历史，安全用于受保护分支
     */
    fun revertCommit(targetSha: String, commitMessage: String) = viewModelScope.launch {
        val branch = _state.value.currentBranch.ifBlank { return@launch }
        // 获取当前 HEAD SHA（从分支列表取）
        val headSha = _state.value.branches
            .find { it.name == branch }?.commit?.sha
            ?: return@launch
        _state.update { it.copy(gitOpInProgress = true) }
        try {
            val result = repository.revertCommit(
                owner, repoName, branch, targetSha, headSha, commitMessage,
            )
            repository.invalidateCommitCache(owner, repoName, branch)
            loadCommits(branch)
            _state.update {
                it.copy(
                    gitOpInProgress = false,
                    gitOpResult = GitOpResult(
                        true, "撤销",
                        "已创建 revert commit ${result.sha.take(7)}",
                        newSha = result.sha,
                    ),
                    selectedCommit = null,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    gitOpInProgress = false,
                    gitOpResult = GitOpResult(false, "撤销", e.message ?: "撤销失败"),
                )
            }
        }
    }

    // ─── Releases ─────────────────────────────────────────────────────────────
    fun loadReleases() = viewModelScope.launch {
        try {
            val releases = repository.getReleases(owner, repoName)
            _state.update { it.copy(releases = releases) }
        } catch (_: Exception) {}
    }

    /** 刷新Releases列表 */
    fun refreshReleases() = viewModelScope.launch {
        _state.update { it.copy(releasesRefreshing = true) }
        try {
            val releases = repository.getReleases(owner, repoName)
            _state.update { it.copy(releases = releases, releasesRefreshing = false) }
        } catch (_: Exception) {
            _state.update { it.copy(releasesRefreshing = false) }
        }
    }

    // ─── GitHub Actions ───────────────────────────────────────────────────────
    fun loadWorkflows() = viewModelScope.launch {
        try {
            val workflows = repository.getWorkflows(owner, repoName)
            _state.update { it.copy(workflows = workflows) }
        } catch (_: Exception) {}
    }

    /** 刷新Actions工作流和运行记录 */
    fun refreshActions() = viewModelScope.launch {
        _state.update { it.copy(actionsRefreshing = true) }
        try {
            val workflows = repository.getWorkflows(owner, repoName)
            val runs = repository.getWorkflowRuns(owner, repoName, null)
            _state.update { it.copy(
                workflows = workflows,
                allWorkflowRuns = runs,
                workflowRuns = runs,
                actionsRefreshing = false
            )}
        } catch (_: Exception) {
            _state.update { it.copy(actionsRefreshing = false) }
        }
    }

    fun loadWorkflowRuns(workflowId: Long? = null) = viewModelScope.launch {
        try {
            val runs = repository.getWorkflowRuns(owner, repoName, workflowId)
            _state.update { it.copy(allWorkflowRuns = runs, workflowRuns = runs) }
        } catch (_: Exception) {}
    }

    fun selectWorkflow(workflow: GHWorkflow) {
        val filteredRuns = _state.value.allWorkflowRuns.filter { it.workflowId == workflow.id }
        _state.update { it.copy(selectedWorkflow = workflow, workflowRuns = filteredRuns) }
    }

    fun clearSelectedWorkflow() {
        _state.update { it.copy(selectedWorkflow = null, workflowRuns = it.allWorkflowRuns) }
    }

    fun selectWorkflowRun(run: GHWorkflowRun) = viewModelScope.launch {
        _state.update { it.copy(selectedWorkflowRun = run) }
        try {
            val jobs = repository.getWorkflowJobs(owner, repoName, run.id)
            _state.update { it.copy(workflowJobs = jobs) }
        } catch (_: Exception) {
            _state.update { it.copy(workflowJobs = emptyList()) }
        }
    }

    fun clearSelectedWorkflowRun() {
        _state.update { it.copy(selectedWorkflowRun = null, workflowJobs = emptyList()) }
    }

    fun dispatchWorkflow(
        workflowId: Long,
        ref: String,
        inputs: Map<String, Any>? = null,
    ) = viewModelScope.launch {
        try {
            val success = repository.dispatchWorkflow(owner, repoName, workflowId, ref, inputs)
            if (success) {
                _state.update { it.copy(toast = "工作流已触发") }
                kotlinx.coroutines.delay(2000)
                loadWorkflowRuns()
            } else {
                _state.update { it.copy(toast = "触发工作流失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "触发工作流失败：${e.message}") }
        }
    }

    fun deleteWorkflowRun(runId: Long) = viewModelScope.launch {
        try {
            val success = repository.deleteWorkflowRun(owner, repoName, runId)
            if (success) {
                _state.update { it.copy(toast = "运行记录已删除") }
                loadWorkflowRuns()
            } else {
                _state.update { it.copy(toast = "删除失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "删除失败：${e.message}") }
        }
    }

    fun rerunWorkflow(runId: Long) = viewModelScope.launch {
        try {
            val success = repository.rerunWorkflow(owner, repoName, runId)
            if (success) {
                _state.update { it.copy(toast = "已重新运行") }
                kotlinx.coroutines.delay(2000)
                loadWorkflowRuns()
            } else {
                _state.update { it.copy(toast = "重新运行失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "重新运行失败：${e.message}") }
        }
    }

    fun cancelWorkflow(runId: Long) = viewModelScope.launch {
        try {
            val success = repository.cancelWorkflow(owner, repoName, runId)
            if (success) {
                _state.update { it.copy(toast = "已取消") }
                kotlinx.coroutines.delay(2000)
                loadWorkflowRuns()
            } else {
                _state.update { it.copy(toast = "取消失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "取消失败：${e.message}") }
        }
    }

    fun loadWorkflowLogs(runId: Long) = viewModelScope.launch {
        _state.update { it.copy(workflowLogsLoading = true, workflowLogs = null) }
        try {
            val logs = repository.getWorkflowLogs(owner, repoName, runId)
            _state.update { it.copy(workflowLogs = logs, workflowLogsLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(workflowLogs = null, workflowLogsLoading = false, toast = "加载日志失败：${e.message}") }
        }
    }

    fun clearWorkflowLogs() {
        _state.update { it.copy(workflowLogs = null) }
    }

    fun loadWorkflowInputs(workflowPath: String) = viewModelScope.launch {
        _state.update { it.copy(workflowInputsLoading = true, workflowInputs = emptyList()) }
        try {
            val inputs = repository.getWorkflowInputs(owner, repoName, workflowPath, _state.value.currentBranch)
            _state.update { it.copy(workflowInputs = inputs, workflowInputsLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(workflowInputs = emptyList(), workflowInputsLoading = false) }
        }
    }

    fun clearWorkflowInputs() {
        _state.update { it.copy(workflowInputs = emptyList()) }
    }

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
