package com.gitmob.android.ui.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.filepicker.BookmarkPath
import com.google.gson.reflect.TypeToken
import com.gitmob.android.local.GitRunner
import com.gitmob.android.local.LocalRepo
import com.gitmob.android.local.LocalRepoStatus
import com.gitmob.android.local.LocalRepoStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

data class LocalRepoListState(
    val repos: List<LocalRepo> = emptyList(),
    val loading: Boolean = false,
    val toast: String? = null,
    val showFilePicker: Boolean = false,
    val showClonePicker: Boolean = false,   // 为克隆操作选择目标目录
    val pendingCloneUrl: String = "",       // 待克隆的 GitHub repo url
)

/** 推送向导步骤 */
sealed class PushWizardStep {
    object None : PushWizardStep()
    data class SelectRemote(val repoId: String) : PushWizardStep()
    data class Running(val repoId: String, val log: List<String> = emptyList()) : PushWizardStep()
    data class Done(val success: Boolean, val log: List<String>) : PushWizardStep()
}

class LocalRepoViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = LocalRepoStorage(app)
    private val gson = com.google.gson.Gson()
    private val tokenStorage = TokenStorage(app)
    private val _state = MutableStateFlow(LocalRepoListState())
    val state = _state.asStateFlow()

    private val _wizardStep = MutableStateFlow<PushWizardStep>(PushWizardStep.None)
    val wizardStep = _wizardStep.asStateFlow()

    private var token: String = ""

    // 自定义书签（持久化在 DataStore）
    val customBookmarks: kotlinx.coroutines.flow.StateFlow<List<BookmarkPath>> =
        tokenStorage.bookmarksJson.map { json ->
            try {
                val type = object : TypeToken<List<BookmarkPath>>() {}.type
                gson.fromJson<List<BookmarkPath>>(json, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            storage.repos.collect { repos ->
                _state.update { it.copy(repos = repos) }
            }
        }
        viewModelScope.launch {
            tokenStorage.accessToken.collect { t ->
                token = t ?: ""
            }
        }
    }

    /** 从文件选择器导入目录 */
    fun importDirectory(path: String) = viewModelScope.launch {
        try {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            toast("路径无效：$path"); return@launch
        }
        val isGit = GitRunner.isGitRepo(path)
        val status = if (isGit) LocalRepoStatus.GIT_INITIALIZED else LocalRepoStatus.PENDING_INIT
        val repo = LocalRepo(
            id = storage.newId(), path = path, name = dir.name,
            status = status,
            currentBranch = if (isGit) GitRunner.currentBranch(path) else null,
            lastCommit = if (isGit) GitRunner.lastCommitMsg(path) else null,
            remoteUrl = if (isGit) GitRunner.remoteUrl(path) else null,
        )
        storage.addOrUpdate(repo)
        toast("已导入：${dir.name}")
        _state.update { it.copy(showFilePicker = false) }
        } catch (e: Exception) { toast("导入失败：${e.message}") }
    }

    /** 新建本地项目（创建目录 + git init） */
    fun createLocalProject(path: String, name: String) = viewModelScope.launch {
        try {
        val dir = File(path, name)
        dir.mkdirs()
        val result = GitRunner.init(dir.absolutePath)
        val repo = LocalRepo(
            id = storage.newId(), path = dir.absolutePath, name = name,
            status = if (result.success) LocalRepoStatus.GIT_INITIALIZED else LocalRepoStatus.ERROR,
            currentBranch = "main",
            error = if (!result.success) result.error else null,
        )
        storage.addOrUpdate(repo)
        toast(if (result.success) "已创建：$name" else "初始化失败：${result.error}")
        } catch (e: Exception) { toast("创建失败：${e.message}") }
    }

    /** 一键上云：init → add → commit → push */
    fun startPushWizard(repoId: String) {
        _wizardStep.value = PushWizardStep.SelectRemote(repoId)
    }

    fun executePush(repoId: String, remoteUrl: String, commitMsg: String, branch: String) =
        viewModelScope.launch {
            val repo = _state.value.repos.find { it.id == repoId } ?: return@launch
            val log = mutableListOf<String>()
            _wizardStep.value = PushWizardStep.Running(repoId, log.toList())

            fun emit(msg: String) {
                log.add(msg)
                _wizardStep.value = PushWizardStep.Running(repoId, log.toList())
            }

            try {
                // 1. git init（若未初始化）
                if (repo.status == LocalRepoStatus.PENDING_INIT) {
                    emit("→ git init...")
                    val r = GitRunner.init(repo.path)
                    if (!r.success) throw RuntimeException("init 失败: ${r.error}")
                    emit("✓ init 完成")
                }

                // 2. git remote add / set-url
                emit("→ 设置远程地址...")
                val existingRemote = GitRunner.remoteUrl(repo.path)
                if (existingRemote != null) {
                    GitRunner.run(repo.path, "remote", "set-url", "origin", remoteUrl)
                } else {
                    GitRunner.run(repo.path, "remote", "add", "origin", remoteUrl)
                }
                emit("✓ remote: $remoteUrl")

                // 3. git add .
                emit("→ git add ...")
                val addR = GitRunner.addAll(repo.path)
                if (!addR.success) throw RuntimeException("add 失败: ${addR.error}")
                emit("✓ 已暂存所有文件")

                // 4. git commit
                emit("→ git commit...")
                val commitR = GitRunner.commit(repo.path, commitMsg)
                if (!commitR.success && !commitR.error.contains("nothing to commit")) {
                    throw RuntimeException("commit 失败: ${commitR.error}")
                }
                emit("✓ commit: $commitMsg")

                // 5. git push
                emit("→ git push -u origin $branch ...")
                val pushR = GitRunner.push(repo.path, remoteUrl, branch, token)
                if (!pushR.success) throw RuntimeException("push 失败: ${pushR.error}")
                emit("✓ 推送成功！")

                // 更新本地仓库状态
                storage.update(repoId) {
                    it.copy(status = LocalRepoStatus.GIT_INITIALIZED, remoteUrl = remoteUrl,
                        currentBranch = branch, error = null)
                }
                _wizardStep.value = PushWizardStep.Done(true, log.toList())

            } catch (e: Exception) {
                emit("✗ ${e.message}")
                _wizardStep.value = PushWizardStep.Done(false, log.toList())
            }
        }

    /** 拉取 */
    fun pull(repoId: String) = viewModelScope.launch {
        val repo = _state.value.repos.find { it.id == repoId } ?: return@launch
        storage.update(repoId) { it.copy(status = LocalRepoStatus.WORKING) }
        val result = GitRunner.pull(repo.path, token, repo.currentBranch ?: "HEAD")
        storage.update(repoId) {
            it.copy(
                status = if (result.success) LocalRepoStatus.GIT_INITIALIZED else LocalRepoStatus.ERROR,
                error = if (!result.success) result.error else null,
            )
        }
        toast(if (result.success) "拉取成功" else "拉取失败：${result.error.take(80)}")
    }

    /** 克隆远程仓库到选定目录 */
    fun cloneRepo(url: String, targetDir: String) = viewModelScope.launch {
        toast("克隆中…")
        val repoName = url.substringAfterLast("/").removeSuffix(".git")
        val fullTarget = "$targetDir/$repoName"
        val result = GitRunner.clone(url, fullTarget, token)
        if (result.success) {
            val repo = LocalRepo(
                id = storage.newId(), path = fullTarget, name = repoName,
                status = LocalRepoStatus.GIT_INITIALIZED,
                remoteUrl = url,
                currentBranch = GitRunner.currentBranch(fullTarget),
            )
            storage.addOrUpdate(repo)
            toast("克隆成功：$repoName")
        } else {
            toast("克隆失败：${result.error.take(100)}")
        }
        _state.update { it.copy(showClonePicker = false, pendingCloneUrl = "") }
    }

    fun scanRepo(repoId: String) = viewModelScope.launch {
        val repo = _state.value.repos.find { it.id == repoId } ?: return@launch
        val isGit = GitRunner.isGitRepo(repo.path)
        // suspend 调用必须在协程体内完成，不能放进普通 lambda
        val branch    = if (isGit) GitRunner.currentBranch(repo.path) else null
        val lastMsg   = if (isGit) GitRunner.lastCommitMsg(repo.path) else null
        val remoteUrl = if (isGit) GitRunner.remoteUrl(repo.path) else null
        storage.update(repoId) {
            it.copy(
                status        = if (isGit) LocalRepoStatus.GIT_INITIALIZED else LocalRepoStatus.PENDING_INIT,
                currentBranch = branch,
                lastCommit    = lastMsg,
                remoteUrl     = remoteUrl,
            )
        }
    }

    fun removeRepo(repoId: String) = viewModelScope.launch {
        storage.remove(repoId)
        toast("已移除")
    }

    fun showFilePicker() = _state.update { it.copy(showFilePicker = true) }
    fun hideFilePicker() = _state.update { it.copy(showFilePicker = false) }

    fun startClone(repoUrl: String) {
        _state.update { it.copy(showClonePicker = true, pendingCloneUrl = repoUrl) }
    }
    fun hideClonePicker() = _state.update { it.copy(showClonePicker = false, pendingCloneUrl = "") }

    /** git reset --soft/mixed/hard <sha> */
    fun gitReset(repoId: String, sha: String, mode: String = "mixed") = viewModelScope.launch {
        val repo = _state.value.repos.find { it.id == repoId } ?: return@launch
        try {
            val flag = "--$mode"
            val result = GitRunner.run(repo.path, "reset", flag, sha)
            if (result.success) {
                // 刷新状态
                val branch = GitRunner.currentBranch(repo.path)
                val last   = GitRunner.lastCommitMsg(repo.path)
                val updated = repo.copy(currentBranch = branch, lastCommit = last,
                    status = LocalRepoStatus.GIT_INITIALIZED)
                storage.addOrUpdate(updated)
                toast("✓ reset $mode 到 ${sha.take(7)}")
            } else {
                toast("reset 失败：${result.error.take(100)}")
            }
        } catch (e: Exception) {
            toast("reset 异常：${e.message}")
        }
    }
    fun dismissWizard() { _wizardStep.value = PushWizardStep.None }
    fun clearToast() = _state.update { it.copy(toast = null) }

    fun addBookmark(bm: BookmarkPath) = viewModelScope.launch {
        val current = customBookmarks.value.toMutableList()
        if (current.none { it.path == bm.path }) {
            current.add(bm)
            tokenStorage.saveBookmarksJson(gson.toJson(current))
        }
    }

    fun removeBookmark(bm: BookmarkPath) = viewModelScope.launch {
        val updated = customBookmarks.value.filter { it.path != bm.path }
        tokenStorage.saveBookmarksJson(gson.toJson(updated))
    }
    private fun toast(msg: String) = _state.update { it.copy(toast = msg) }
}
