package com.gitmob.android.local

import com.gitmob.android.auth.RootManager
import com.gitmob.android.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地 Git 命令执行器
 *
 * ▸ git 路径采用「运行时动态查找」而非 by lazy（lazy 在无存储权限时缓存空值）
 * ▸ 优先通过 Shell which/type 命令发现 git，其次扫描已知路径
 * ▸ ProcessBuilder 中注入 Termux PATH，使 git 能被 Shell 正常解析
 * ▸ Root 模式通过 libsu 提权执行
 */
object GitRunner {

    private const val TAG = "GitRunner"

    private val KNOWN_PATHS = listOf(
        "/data/data/com.termux/files/usr/bin/git",
        "/data/user/0/com.termux/files/usr/bin/git",
        "/usr/bin/git",
        "/usr/local/bin/git",
        "/bin/git",
    )

    /** 运行时查找 git 可执行文件路径（每次调用，无缓存）*/
    private fun findGitPath(): String {
        // 1. 用 Shell which 查（不依赖文件访问权限）
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("/system/bin/sh", "-c",
                    "which git 2>/dev/null || type -P git 2>/dev/null")
            )
            val found = proc.inputStream.bufferedReader().readLine()?.trim() ?: ""
            proc.waitFor()
            if (found.isNotEmpty() && found.startsWith("/")) {
                LogManager.d(TAG, "which 找到 git：$found")
                return found
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "which 查找失败", e)
        }

        // 2. 扫描已知路径
        for (p in KNOWN_PATHS) {
            if (File(p).exists()) {
                LogManager.d(TAG, "已知路径找到 git：$p")
                return p
            }
        }

        // 3. 检查是否直接 `git` 命令可用（PATH 中有）
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("/system/bin/sh", "-c", "git --version 2>/dev/null")
            )
            val out = proc.inputStream.bufferedReader().readLine()?.trim() ?: ""
            proc.waitFor()
            if (out.startsWith("git version")) {
                LogManager.d(TAG, "Shell PATH 中可用：git  ($out)")
                return "git"   // 交给 Shell 用 PATH 解析
            }
        } catch (_: Exception) {}

        LogManager.w(TAG, "未找到 git 可执行文件")
        return ""
    }

    val isGitAvailable: Boolean get() = findGitPath().isNotEmpty()

    data class GitResult(
        val success: Boolean,
        val output: List<String>,
        val error: String = "",
    )

    private val NO_GIT = GitResult(false, emptyList(),
        "未找到 git。\n请确认：\n1. 已在 Termux 安装：pkg install git\n" +
        "2. 已授予「所有文件访问权限」（设置→高级→文件访问权限）")

    /** 在指定目录执行 git 命令 */
    suspend fun run(workDir: String, vararg args: String, useRoot: Boolean = false): GitResult =
        withContext(Dispatchers.IO) {
            val gitBin = findGitPath()
            if (gitBin.isEmpty()) return@withContext NO_GIT
            if (useRoot && RootManager.isGranted) runAsRoot(workDir, gitBin, *args)
            else runNormal(workDir, gitBin, *args)
        }

    private fun runNormal(workDir: String, gitBin: String, vararg args: String): GitResult {
        return try {
            // 若 gitBin == "git"，用 sh -c 来让 Shell 解析 PATH
            val cmd: List<String> = if (gitBin == "git" || gitBin.isEmpty()) {
                val escaped = args.joinToString(" ") { shellEscape(it) }
                listOf("/system/bin/sh", "-c", "git $escaped")
            } else {
                buildList { add(gitBin); addAll(args) }
            }

            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val process = ProcessBuilder(cmd)
                .directory(File(workDir))
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().also { env ->
                        val oldPath = env["PATH"] ?: "/usr/bin:/bin"
                        env["PATH"] = "$termuxBin:$oldPath"
                        env["GIT_TERMINAL_PROMPT"] = "0"
                        env["HOME"] = workDir
                        env["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
                    }
                }
                .start()
            val lines    = process.inputStream.bufferedReader().readLines()
            val exitCode = process.waitFor()
            LogManager.d(TAG, "git ${args.take(2).joinToString(" ")} → exit=$exitCode")
            if (exitCode == 0) GitResult(true, lines)
            else GitResult(false, lines, lines.joinToString("\n").take(400))
        } catch (e: Exception) {
            LogManager.e(TAG, "git 执行异常", e)
            GitResult(false, emptyList(), e.message ?: "执行失败")
        }
    }

    private suspend fun runAsRoot(workDir: String, gitBin: String, vararg args: String): GitResult {
        return try {
            val escapedArgs = args.joinToString(" ") { shellEscape(it) }
            val bin = if (gitBin == "git") gitBin else shellEscape(gitBin)
            val cmd = "cd ${shellEscape(workDir)} && $bin $escapedArgs"
            val output = RootManager.exec(cmd)
            GitResult(true, output)
        } catch (e: Exception) {
            LogManager.e(TAG, "Root git 执行异常", e)
            GitResult(false, emptyList(), e.message ?: "Root 执行失败")
        }
    }

    private fun shellEscape(s: String) = "'${s.replace("'", "'\\''")}'"

    // ─── 高级组合操作 ──────────────────────────────────────────

    suspend fun isGitRepo(path: String): Boolean {
        val r = run(path, "rev-parse", "--git-dir")
        return r.success || File(path, ".git").exists()
    }

    suspend fun init(path: String): GitResult {
        val r = run(path, "init", "-b", "main")
        return if (r.success) r else run(path, "init")
    }

    suspend fun addAll(path: String): GitResult = run(path, "add", ".")

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
        val gitBin = findGitPath()
        if (gitBin.isEmpty()) return NO_GIT
        val authed    = injectToken(url, token)
        val parentDir = File(targetDir).parent ?: "/"
        val dirName   = File(targetDir).name
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
        return if (url.startsWith("https://github.com/"))
            url.replace("https://github.com/", "https://$token@github.com/")
        else url
    }
}
