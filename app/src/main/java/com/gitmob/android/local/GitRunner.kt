package com.gitmob.android.local

import com.gitmob.android.auth.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地 Git 命令执行器
 * 优先寻找 Termux git，其次系统 git，Root 模式下通过 libsu 提权执行
 */
object GitRunner {

    // 常见 git 可执行文件路径
    private val GIT_CANDIDATES = listOf(
        "/data/data/com.termux/files/usr/bin/git",
        "/data/user/0/com.termux/files/usr/bin/git",
        "/usr/bin/git",
        "/usr/local/bin/git",
        "git",  // PATH 中
    )

    private val gitPath: String by lazy {
        GIT_CANDIDATES.firstOrNull { path ->
            if (path == "git") {
                runCatching { Runtime.getRuntime().exec(arrayOf("which", "git")).waitFor() == 0 }.getOrDefault(false)
            } else {
                File(path).exists()
            }
        } ?: "git"
    }

    data class GitResult(
        val success: Boolean,
        val output: List<String>,
        val error: String = "",
    )

    /** 在指定目录执行 git 命令（普通权限） */
    suspend fun run(workDir: String, vararg args: String, useRoot: Boolean = false): GitResult =
        withContext(Dispatchers.IO) {
            if (useRoot && RootManager.isGranted) {
                runAsRoot(workDir, *args)
            } else {
                runNormal(workDir, *args)
            }
        }

    private fun runNormal(workDir: String, vararg args: String): GitResult {
        return try {
            val cmd = buildList {
                add(gitPath)
                addAll(args)
            }
            val process = ProcessBuilder(cmd)
                .directory(File(workDir))
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().also { env ->
                        // 加入 Termux PATH
                        val termuxBin = "/data/data/com.termux/files/usr/bin"
                        env["PATH"] = "$termuxBin:${env["PATH"] ?: "/usr/bin:/bin"}"
                        env["GIT_TERMINAL_PROMPT"] = "0"
                    }
                }
                .start()
            val lines = process.inputStream.bufferedReader().readLines()
            val exitCode = process.waitFor()
            GitResult(exitCode == 0, lines, if (exitCode != 0) lines.joinToString("\n") else "")
        } catch (e: Exception) {
            GitResult(false, emptyList(), e.message ?: "执行失败")
        }
    }

    private suspend fun runAsRoot(workDir: String, vararg args: String): GitResult {
        return try {
            val cmdStr = buildString {
                append("cd ")
                append(shellEscape(workDir))
                append(" && ")
                append(gitPath)
                args.forEach { arg -> append(" "); append(shellEscape(arg)) }
            }
            val output = RootManager.exec(cmdStr)
            GitResult(true, output)
        } catch (e: Exception) {
            GitResult(false, emptyList(), e.message ?: "Root 执行失败")
        }
    }

    private fun shellEscape(s: String) = "'${s.replace("'", "'\\''")}'"

    // ─── 高级组合操作 ──────────────────────────────────────────

    /** 检查目录是否是 git repo */
    suspend fun isGitRepo(path: String): Boolean {
        val result = run(path, "rev-parse", "--git-dir")
        return result.success
    }

    /** git init */
    suspend fun init(path: String): GitResult = run(path, "init", "-b", "main")

    /** git remote add origin <url> */
    suspend fun addRemote(path: String, url: String): GitResult =
        run(path, "remote", "add", "origin", url)

    /** git remote set-url origin <url> */
    suspend fun setRemote(path: String, url: String): GitResult =
        run(path, "remote", "set-url", "origin", url)

    /** git add . */
    suspend fun addAll(path: String): GitResult = run(path, "add", ".")

    /** git add specific files */
    suspend fun addFiles(path: String, files: List<String>): GitResult =
        run(path, *buildList { add("add"); addAll(files) }.toTypedArray())

    /** git commit -m <message> */
    suspend fun commit(path: String, message: String, authorName: String = "GitMob", authorEmail: String = "gitmob@local"): GitResult {
        // Set identity if not configured
        run(path, "config", "user.email", authorEmail)
        run(path, "config", "user.name", authorName)
        return run(path, "commit", "-m", message)
    }

    /** git push -u origin <branch> with token auth */
    suspend fun push(path: String, remoteUrl: String, branch: String, token: String): GitResult {
        // 将 token 嵌入 URL
        val authedUrl = injectToken(remoteUrl, token)
        run(path, "remote", "set-url", "origin", authedUrl)
        val result = run(path, "push", "-u", "origin", branch)
        // 推送完毕后清除 token 以安全
        run(path, "remote", "set-url", "origin", remoteUrl)
        return result
    }

    /** git pull */
    suspend fun pull(path: String, token: String, branch: String = "HEAD"): GitResult {
        val remoteResult = run(path, "remote", "get-url", "origin")
        val remoteUrl = remoteResult.output.firstOrNull() ?: return GitResult(false, emptyList(), "无远程地址")
        val authedUrl = injectToken(remoteUrl, token)
        run(path, "remote", "set-url", "origin", authedUrl)
        val result = run(path, "pull", "origin", branch)
        run(path, "remote", "set-url", "origin", remoteUrl)
        return result
    }

    /** git clone <url> <dir> */
    suspend fun clone(url: String, targetDir: String, token: String, useRoot: Boolean = false): GitResult {
        val authedUrl = injectToken(url, token)
        val parentDir = File(targetDir).parent ?: "/"
        val dirName = File(targetDir).name
        return run(parentDir, "clone", authedUrl, dirName, useRoot = useRoot)
    }

    /** 获取当前分支 */
    suspend fun currentBranch(path: String): String? {
        val r = run(path, "rev-parse", "--abbrev-ref", "HEAD")
        return r.output.firstOrNull()?.trim()
    }

    /** 获取最近一条提交的短信息 */
    suspend fun lastCommitMsg(path: String): String? {
        val r = run(path, "log", "-1", "--oneline")
        return r.output.firstOrNull()?.trim()
    }

    /** 获取 remote origin url */
    suspend fun remoteUrl(path: String): String? {
        val r = run(path, "remote", "get-url", "origin")
        return if (r.success) r.output.firstOrNull()?.trim() else null
    }

    /** ahead / behind 计数 */
    suspend fun aheadBehind(path: String): Pair<Int, Int> {
        run(path, "fetch", "--dry-run")
        val r = run(path, "rev-list", "--left-right", "--count", "HEAD...@{u}")
        val parts = r.output.firstOrNull()?.trim()?.split("\\s+".toRegex()) ?: return 0 to 0
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    // 将 token 注入 https URL
    fun injectToken(url: String, token: String): String {
        return if (url.startsWith("https://github.com/")) {
            url.replace("https://github.com/", "https://$token@github.com/")
        } else url
    }
}
