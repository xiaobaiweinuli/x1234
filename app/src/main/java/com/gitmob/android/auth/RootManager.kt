package com.gitmob.android.auth

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 封装 libsu（topjohnwu/libsu）Root 操作
 * 首次调用 requestRoot() 时申请 root 权限
 */
object RootManager {

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    /** 是否已获得 root 权限 */
    val isGranted: Boolean
        get() = Shell.isAppGrantedRoot() == true

    /** 请求 root 权限，返回是否成功 */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.getShell()
            Shell.isAppGrantedRoot() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 以 root 身份执行 shell 命令，返回 stdout 行列表
     * @throws Exception 若 root 未授权或命令失败
     */
    suspend fun exec(vararg cmds: String): List<String> = withContext(Dispatchers.IO) {
        val result = Shell.cmd(*cmds).exec()
        if (!result.isSuccess) {
            throw RuntimeException("Root 命令失败: ${result.err.joinToString("\n")}")
        }
        result.out
    }

    /**
     * 以普通用户身份执行 shell 命令
     */
    suspend fun execNormal(vararg cmds: String): List<String> = withContext(Dispatchers.IO) {
        val result = Shell.cmd(*cmds).exec()
        result.out
    }
}
