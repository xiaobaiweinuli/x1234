package com.gitmob.android.local

import com.gitmob.android.auth.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地 Git 命令执行器
 * 自动检测 Termux git；Root 模式下通过 libsu 提权执行。
 * 无 git 时返回带明确错误信息的 GitResult（不抛异常）。
 */
object GitRunner {

    private val GIT_CANDIDATES = listOf(
        "/data/data/com.termux/files/usr/bin/git",
        "/data/user/0/com.termux/files/usr/bin/git",
        "/usr/bin/git",
        "/usr/local/bin/git",
    )

    val gitPath: String by lazy {
        GIT_CANDIDATES.firstOrNull { File(it).exists() } ?: ""
    }

    val isGitAvailable: Boolean get() = gitPath.isNotEmpty()

    data class GitResult(
        val success: Boolean,
        val output: List<String>,
        val error: String = "",
    )

    private val NO_GIT = GitResult(false, emptyList(),
        "未找到 git。请先在 Termux 中执行：pkg install git")

    /** 在指定目录执行 git 命令 */
    suspend fun run(workDir: String, vararg args: String, useRoot: Boolean = false): GitResult =
        withContext(Dispatchers.IO) {
            if (!isGitAvailable) return@withContext NO_GIT
            if (useRoot && RootManager.isGranted) runAsRoot(workDir, *args)
            else runNormal(workDir, *args)
        }

    private fun runNormal(workDir: String, vararg args: String): GitResult {
        return try {
            val cmd = buildList { add(gitPath); addAll(args) }
            val process = ProcessBuilder(cmd)
                .directory(File(workDir))
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().also { env ->
                        val termuxBin = "/data/data/com.termux/files/usr/bin"
                        env["PATH"] = "$termuxBin:${env["PATH"] ?: "/usr/bin:/bin"}"
                        env["GIT_TERMINAL_PROMPT"] = "0"
                        env["HOME"] = workDir
                    }
                }
                .start()
            val lines = process.inputStream.bufferedReader().readLines()
            val exitCode = process.waitFor()
            if (exitCode == 0) GitResult(true, lines)
            else GitResult(false, lines, lines.joinToString("\n").take(300))
        } catch (e: Exception) {
            GitResult(false, emptyList(), e.message ?: "执行失败")
        }
    }

    private suspend fun runAsRoot(workDir: String, vararg args: String): GitResult {
        return try {
            val escaped = args.joinToString(" ") { shellEscape(it) }
            val cmd = "cd ${shellEscape(workDir)} && $gitPath $escaped"
            val output = RootManager.exec(cmd)
            GitResult(true, output)
        } catch (e: Exception) {
            GitResult(false, emptyList(), e.message ?: "Root 执行失败")
        }
    }

    private fun shellEscape(s: String) = "'${s.replace("'", "'\\''")}'"

    // ─── 高级组合操作 ──────────────────────────────────────────

    suspend fun isGitRepo(path: String): Boolean {
        if (!isGitAvailable) return File(path, ".git").exists()
        val result = run(path, "rev-parse", "--git-dir")
        return result.success
    }

    suspend fun init(path: String): GitResult {
        if (!isGitAvailable) return NO_GIT
        // 尝试带 -b main，旧版 git 不支持时降级
        val r = run(path, "init", "-b", "main")
        return if (r.success) r else run(path, "init")
    }

    suspend fun addRemote(path: String, url: String): GitResult =
        run(path, "remote", "add", "origin", url)

    suspend fun setRemote(path: String, url: String): GitResult =
        run(path, "remote", "set-url", "origin", url)

    suspend fun addAll(path: String): GitResult = run(path, "add", ".")

    suspend fun addFiles(path: String, files: List<String>): GitResult =
        run(path, *buildList { add("add"); addAll(files) }.toTypedArray())

    suspend fun commit(path: String, message: String,
                       authorName: String = "GitMob", authorEmail: String = "gitmob@local"): GitResult {
        run(path, "config", "user.email", authorEmail)
        run(path, "config", "user.name", authorName)
        return run(path, "commit", "-m", message)
    }

    suspend fun push(path: String, remoteUrl: String, branch: String, token: String): GitResult {
        val authed = injectToken(remoteUrl, token)
        run(path, "remote", "set-url", "origin", authed)
        val result = run(path, "push", "-u", "origin", branch)
        run(path, "remote", "set-url", "origin", remoteUrl)
        return result
    }

    suspend fun pull(path: String, token: String, branch: String = "HEAD"): GitResult {
        val remoteResult = run(path, "remote", "get-url", "origin")
        val remoteUrl = remoteResult.output.firstOrNull()
            ?: return GitResult(false, emptyList(), "无远程地址")
        val authed = injectToken(remoteUrl, token)
        run(path, "remote", "set-url", "origin", authed)
        val result = run(path, "pull", "origin", branch)
        run(path, "remote", "set-url", "origin", remoteUrl)
        return result
    }

    suspend fun clone(url: String, targetDir: String, token: String, useRoot: Boolean = false): GitResult {
        if (!isGitAvailable) return NO_GIT
        val authed = injectToken(url, token)
        val parentDir = File(targetDir).parent ?: "/"
        val dirName = File(targetDir).name
        return run(parentDir, "clone", authed, dirName, useRoot = useRoot)
    }

    suspend fun currentBranch(path: String): String? {
        val r = run(path, "rev-parse", "--abbrev-ref", "HEAD")
        return r.output.firstOrNull()?.trim()
    }

    suspend fun lastCommitMsg(path: String): String? {
        val r = run(path, "log", "-1", "--oneline")
        return r.output.firstOrNull()?.trim()
    }

    suspend fun remoteUrl(path: String): String? {
        val r = run(path, "remote", "get-url", "origin")
        return if (r.success) r.output.firstOrNull()?.trim() else null
    }

    fun injectToken(url: String, token: String): String {
        if (token.isBlank()) return url
        return if (url.startsWith("https://github.com/")) {
            url.replace("https://github.com/", "https://${token}@github.com/")
        } else url
    }
}
