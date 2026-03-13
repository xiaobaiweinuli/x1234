package com.gitmob.android.local

import com.gitmob.android.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * 本地 Git 操作封装 —— 基于 JGit（纯 Java 实现，无需外部 git 可执行文件）
 *
 * ▸ 所有耗时操作均在 Dispatchers.IO 执行
 * ▸ Git 对象使用 use {} 确保资源正确释放
 * ▸ 统一返回 GitResult，调用方通过 .success / .error 判断结果
 */
object GitRunner {

    private const val TAG = "GitRunner"

    /** 所有操作都基于 JGit，始终可用 */
    val isGitAvailable: Boolean get() = true

    data class GitResult(
        val success: Boolean,
        val output: List<String> = emptyList(),
        val error: String = "",
    )

    // ─── 仓库探测 ──────────────────────────────────────────────────────────

    suspend fun isGitRepo(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path, ".git").exists().also {
                if (it) LogManager.d(TAG, "isGitRepo($path) = true")
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "isGitRepo 异常", e); false
        }
    }

    // ─── git init ──────────────────────────────────────────────────────────

    suspend fun init(path: String): GitResult = withContext(Dispatchers.IO) {
        try {
            val dir = File(path).also { it.mkdirs() }
            Git.init().setDirectory(dir).setInitialBranch("main").call().use {
                LogManager.i(TAG, "init 成功: $path")
                GitResult(true, listOf("Initialized empty Git repository in $path/.git/"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "init 失败", e)
            GitResult(false, error = e.message ?: "init 失败")
        }
    }

    // ─── git add . ────────────────────────────────────────────────────────

    suspend fun addAll(path: String): GitResult = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                git.add().addFilepattern(".").call()
                LogManager.d(TAG, "add . 成功: $path")
                GitResult(true, listOf("已暂存所有变更"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "add 失败", e)
            GitResult(false, error = e.message ?: "add 失败")
        }
    }

    // ─── git commit ───────────────────────────────────────────────────────

    suspend fun commit(
        path: String,
        message: String,
        authorName: String = "GitMob",
        authorEmail: String = "gitmob@local",
    ): GitResult = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                val author = PersonIdent(authorName, authorEmail)
                val commit = git.commit()
                    .setMessage(message)
                    .setAuthor(author)
                    .setCommitter(author)
                    .setAllowEmpty(false)
                    .call()
                val sha = commit.name.take(7)
                LogManager.i(TAG, "commit 成功: $sha $message")
                GitResult(true, listOf("[main $sha] $message"))
            }
        } catch (e: EmptyCommitException) {
            LogManager.d(TAG, "nothing to commit")
            GitResult(true, listOf("nothing to commit"), "nothing to commit")
        } catch (e: NoHeadException) {
            // 首次提交没有 HEAD，也属于正常
            LogManager.d(TAG, "NoHeadException（首次提交）")
            GitResult(true, listOf("initial commit"))
        } catch (e: Exception) {
            LogManager.e(TAG, "commit 失败", e)
            GitResult(false, error = e.message ?: "commit 失败")
        }
    }

    // ─── git push ─────────────────────────────────────────────────────────

    suspend fun push(
        path: String,
        remoteUrl: String,
        branch: String,
        token: String,
    ): GitResult = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                val creds = tokenCredentials(token)
                val authedUrl = injectToken(remoteUrl, token)

                // 确保 remote origin 指向正确 URL（含 token）
                setRemoteUrl(git, authedUrl)

                val results = git.push()
                    .setRemote("origin")
                    .add("refs/heads/$branch:refs/heads/$branch")
                    .setCredentialsProvider(creds)
                    .setForce(false)
                    .call()

                // 推送完成后恢复原始 URL（不含 token，安全）
                setRemoteUrl(git, remoteUrl)

                val msgs = results.flatMap { res ->
                    res.remoteUpdates.map { update ->
                        "${update.remoteName}: ${update.status}"
                    }
                }
                LogManager.i(TAG, "push 成功: $branch")
                GitResult(true, msgs.ifEmpty { listOf("推送成功") })
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "push 失败", e)
            GitResult(false, error = friendlyError(e))
        }
    }

    // ─── git pull ─────────────────────────────────────────────────────────

    suspend fun pull(
        path: String,
        token: String,
        branch: String = "HEAD",
    ): GitResult = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                val remoteUrl = remoteUrl(path) ?: return@withContext GitResult(false, error = "未设置远程地址")
                val authedUrl = injectToken(remoteUrl, token)
                setRemoteUrl(git, authedUrl)

                val result = git.pull()
                    .setRemote("origin")
                    .setCredentialsProvider(tokenCredentials(token))
                    .call()

                setRemoteUrl(git, remoteUrl)

                if (result.isSuccessful) {
                    LogManager.i(TAG, "pull 成功")
                    GitResult(true, listOf("pull 成功"))
                } else {
                    val msg = result.mergeResult?.toString() ?: "pull 失败"
                    GitResult(false, error = msg)
                }
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "pull 失败", e)
            GitResult(false, error = friendlyError(e))
        }
    }

    // ─── git clone ────────────────────────────────────────────────────────

    suspend fun clone(
        url: String,
        targetDir: String,
        token: String,
        useRoot: Boolean = false,   // JGit 原生实现，无需 root
    ): GitResult = withContext(Dispatchers.IO) {
        try {
            val dir = File(targetDir)
            dir.mkdirs()

            val authedUrl = injectToken(url, token)
            LogManager.i(TAG, "clone 开始: $url → $targetDir")

            Git.cloneRepository()
                .setURI(authedUrl)
                .setDirectory(dir)
                .setCredentialsProvider(tokenCredentials(token))
                .setCloneAllBranches(false)
                .setProgressMonitor(LogProgressMonitor())
                .call()
                .use {
                    LogManager.i(TAG, "clone 成功: $targetDir")
                    GitResult(true, listOf("克隆成功：$targetDir"))
                }
        } catch (e: Exception) {
            LogManager.e(TAG, "clone 失败", e)
            GitResult(false, error = friendlyError(e))
        }
    }

    // ─── git reset ────────────────────────────────────────────────────────

    suspend fun reset(path: String, sha: String, mode: String = "mixed"): GitResult =
        withContext(Dispatchers.IO) {
            try {
                openGit(path).use { git ->
                    val resetType = when (mode.lowercase()) {
                        "soft"  -> ResetCommand.ResetType.SOFT
                        "hard"  -> ResetCommand.ResetType.HARD
                        else    -> ResetCommand.ResetType.MIXED
                    }
                    git.reset().setMode(resetType).setRef(sha).call()
                    LogManager.i(TAG, "reset --$mode $sha 成功")
                    GitResult(true, listOf("reset --$mode ${sha.take(7)} 成功"))
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "reset 失败", e)
                GitResult(false, error = e.message ?: "reset 失败")
            }
        }

    // ─── 查询操作 ─────────────────────────────────────────────────────────

    suspend fun currentBranch(path: String): String? = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                git.repository.branch.also { LogManager.d(TAG, "branch: $it") }
            }
        } catch (e: Exception) { LogManager.w(TAG, "currentBranch 异常", e); null }
    }

    suspend fun lastCommitMsg(path: String): String? = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                val repo = git.repository
                val headId = repo.resolve("HEAD") ?: return@withContext null
                RevWalk(repo).use { walk ->
                    val commit = walk.parseCommit(headId)
                    val sha = commit.name.take(7)
                    val msg = commit.shortMessage
                    "$sha $msg"
                }
            }
        } catch (e: Exception) { LogManager.w(TAG, "lastCommitMsg 异常", e); null }
    }

    suspend fun remoteUrl(path: String): String? = withContext(Dispatchers.IO) {
        try {
            openGit(path).use { git ->
                git.repository.config
                    .getString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
                        ConfigConstants.CONFIG_KEY_URL)
            }
        } catch (e: Exception) { null }
    }

    // ─── 兼容性 run() 接口（供 LocalRepoViewModel 调用）──────────────────

    /**
     * 通用命令兼容层，将 git 命令行风格的参数映射到 JGit API
     * 支持：remote set-url / remote add / reset --soft/mixed/hard
     */
    suspend fun run(path: String, vararg args: String, useRoot: Boolean = false): GitResult =
        withContext(Dispatchers.IO) {
            try {
                when {
                    // git remote set-url origin <url>
                    args.size >= 4
                    && args[0] == "remote" && args[1] == "set-url" && args[2] == "origin" ->
                        setRemoteUrlCmd(path, args[3])

                    // git remote add origin <url>
                    args.size >= 4
                    && args[0] == "remote" && args[1] == "add" && args[2] == "origin" ->
                        addRemoteCmd(path, args[3])

                    // git reset --soft/mixed/hard <sha>
                    args.size >= 3 && args[0] == "reset" -> {
                        val mode = args[1].trimStart('-')
                        val sha  = args[2]
                        reset(path, sha, mode)
                    }

                    else -> {
                        LogManager.w(TAG, "run() 未支持的命令: ${args.toList()}")
                        GitResult(false, error = "不支持的 git 命令: ${args.toList()}")
                    }
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "run() 异常", e)
                GitResult(false, error = e.message ?: "执行失败")
            }
        }

    // ─── 私有辅助 ─────────────────────────────────────────────────────────

    private fun openGit(path: String): Git {
        val gitDir = File(path, ".git")
        val repo = RepositoryBuilder()
            .setGitDir(if (gitDir.exists()) gitDir else null)
            .setWorkTree(File(path))
            .setMustExist(false)
            .build()
        return Git(repo)
    }

    private fun setRemoteUrl(git: Git, url: String) {
        val config = git.repository.config
        config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
            ConfigConstants.CONFIG_KEY_URL, url)
        config.save()
    }

    private suspend fun setRemoteUrlCmd(path: String, url: String): GitResult =
        withContext(Dispatchers.IO) {
            try {
                openGit(path).use { git -> setRemoteUrl(git, url) }
                GitResult(true, listOf("remote.origin.url=$url"))
            } catch (e: Exception) {
                GitResult(false, error = e.message ?: "set-url 失败")
            }
        }

    private suspend fun addRemoteCmd(path: String, url: String): GitResult =
        withContext(Dispatchers.IO) {
            try {
                openGit(path).use { git ->
                    val config = git.repository.config
                    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
                        ConfigConstants.CONFIG_KEY_URL, url)
                    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
                        "fetch", "+refs/heads/*:refs/remotes/origin/*")
                    config.save()
                }
                GitResult(true, listOf("已添加 remote origin: $url"))
            } catch (e: Exception) {
                GitResult(false, error = e.message ?: "remote add 失败")
            }
        }

    private fun tokenCredentials(token: String) =
        UsernamePasswordCredentialsProvider(token, "")

    fun injectToken(url: String, token: String): String {
        if (token.isBlank()) return url
        return if (url.startsWith("https://github.com/"))
            url.replace("https://github.com/", "https://$token@github.com/")
        else url
    }

    /** 将 JGit 异常转换为用户友好的错误消息 */
    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("not authorized", ignoreCase = true) ||
            msg.contains("Authentication", ignoreCase = true) ->
                "认证失败：请检查 GitHub Token 是否有效"
            msg.contains("Repository not found", ignoreCase = true) ->
                "仓库不存在或无权限访问"
            msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("UnknownHost", ignoreCase = true) ->
                "网络连接失败，请检查网络"
            msg.contains("rejected", ignoreCase = true) ->
                "推送被拒绝（远程有新提交，请先 pull）"
            else -> msg.take(200)
        }
    }
}

// ─── JGit 进度监听（输出到日志）────────────────────────────────────────────

private class LogProgressMonitor : org.eclipse.jgit.lib.ProgressMonitor {
    private val TAG = "JGitProgress"
    override fun start(totalTasks: Int) {}
    override fun beginTask(title: String, totalWork: Int) {
        LogManager.d(TAG, "▶ $title")
    }
    override fun update(completed: Int) {}
    override fun endTask() {}
    override fun isCancelled() = false
    override fun showDuration(enabled: Boolean) {}
}
