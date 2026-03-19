package com.gitmob.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gitmob.android.R
import com.gitmob.android.api.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 下载任务状态 */
sealed class DownloadStatus {
    object Idle    : DownloadStatus()
    data class Progress(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
    object Paused  : DownloadStatus()
}

/** 单次下载任务 */
data class DownloadTask(
    val id: Int,
    val filename: String,
    val url: String,                // GitHub API URL（带 Bearer token）
    val statusFlow: MutableStateFlow<DownloadStatus> = MutableStateFlow(DownloadStatus.Idle),
    var job: Job? = null,
)

/**
 * GitMob 下载管理器
 *
 * 功能：
 *  - 通过 GitHub API（Bearer token）流式下载，兼容 Release Assets 和 Actions Artifacts
 *  - 系统通知：进度条 + 暂停/继续 + 完成后可打开文件
 *  - 流式写盘，不占用额外内存
 *  - StateFlow 供 UI 实时感知进度（Tab 内按钮背景填充效果）
 */
object GmDownloadManager {

    private const val CHANNEL_ID   = "gitmob_download"
    private const val CHANNEL_NAME = "GitMob 下载"
    private val notifId = AtomicInteger(10000)
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 活跃任务表，key = notifId */
    val tasks: ConcurrentHashMap<Int, DownloadTask> = ConcurrentHashMap()

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示文件下载进度"
                setSound(null, null)
            }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /**
     * 启动下载。
     * @param apiUrl GitHub API URL，例如：
     *   Release Asset: https://api.github.com/repos/{owner}/{repo}/releases/assets/{id}
     *   Artifact:      https://api.github.com/repos/{owner}/{repo}/actions/artifacts/{id}/zip
     * @param filename 保存的文件名
     * @return 下载任务 ID（可用于查询 StateFlow）
     */
    fun download(ctx: Context, apiUrl: String, filename: String): Int {
        ensureChannel(ctx)
        val id = notifId.getAndIncrement()
        val task = DownloadTask(id = id, filename = filename, url = apiUrl)
        tasks[id] = task
        task.job = scope.launch { doDownload(ctx, task) }
        return id
    }

    /** 暂停（取消协程） */
    fun pause(ctx: Context, taskId: Int) {
        val task = tasks[taskId] ?: return
        task.job?.cancel()
        task.statusFlow.value = DownloadStatus.Paused
        updateNotifPaused(ctx, task)
    }

    /** 继续（重新下载） */
    fun resume(ctx: Context, taskId: Int) {
        val task = tasks[taskId] ?: return
        task.job = scope.launch { doDownload(ctx, task) }
    }

    fun getTask(id: Int): DownloadTask? = tasks[id]
    fun statusOf(id: Int): StateFlow<DownloadStatus>? = tasks[id]?.statusFlow

    // ── 实际下载 ────────────────────────────────────────────────────────────

    private suspend fun doDownload(ctx: Context, task: DownloadTask) {
        task.statusFlow.value = DownloadStatus.Progress(0, 0, 0)
        postNotifProgress(ctx, task, 0)

        try {
            val token = ApiClient.currentToken() ?: error("未登录")

            // 构造带 Auth 和 Accept 头的请求（GitHub Release Asset 需要 application/octet-stream）
            val req = Request.Builder()
                .url(task.url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            val resp = ApiClient.rawHttpClient().newCall(req).execute()

            // GitHub Artifact 会 302 重定向到 S3（okhttp 自动跟随，无需额外处理）
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")

            val body    = resp.body ?: error("响应体为空")
            val total   = body.contentLength()   // -1 表示未知
            var written = 0L

            // 写入 Downloads 目录
            val destFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                task.filename,
            )

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)   // 64KB 缓冲区
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        written += n
                        val pct = if (total > 0) (written * 100 / total).toInt() else -1
                        task.statusFlow.value = DownloadStatus.Progress(pct, written, total)
                        // 每 10% 更新一次通知，避免频繁刷新
                        if (pct >= 0 && pct % 5 == 0) postNotifProgress(ctx, task, pct)
                    }
                }
            }

            task.statusFlow.value = DownloadStatus.Success(destFile)
            postNotifSuccess(ctx, task, destFile)
        } catch (e: CancellationException) {
            // 暂停时协程被取消，状态已在 pause() 中更新
        } catch (e: Exception) {
            val msg = e.message ?: "下载失败"
            task.statusFlow.value = DownloadStatus.Failed(msg)
            postNotifFailed(ctx, task, msg)
        }
    }

    // ── 通知 ─────────────────────────────────────────────────────────────────

    private fun pauseIntent(ctx: Context, taskId: Int): PendingIntent {
        val i = Intent("com.gitmob.DOWNLOAD_PAUSE").putExtra("taskId", taskId)
        return PendingIntent.getBroadcast(ctx, taskId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun resumeIntent(ctx: Context, taskId: Int): PendingIntent {
        val i = Intent("com.gitmob.DOWNLOAD_RESUME").putExtra("taskId", taskId)
        return PendingIntent.getBroadcast(ctx, taskId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun postNotifProgress(ctx: Context, task: DownloadTask, pct: Int) {
        val nm = NotificationManagerCompat.from(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            nm.areNotificationsEnabled().not()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载")
            .setContentText(task.filename)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .addAction(android.R.drawable.ic_media_pause, "暂停", pauseIntent(ctx, task.id))
            .build()
        try { nm.notify(task.id, n) } catch (_: SecurityException) {}
    }

    private fun updateNotifPaused(ctx: Context, task: DownloadTask) {
        val nm = NotificationManagerCompat.from(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载已暂停")
            .setContentText(task.filename)
            .setOngoing(false)
            .addAction(android.R.drawable.ic_media_play, "继续", resumeIntent(ctx, task.id))
            .build()
        try { nm.notify(task.id, n) } catch (_: SecurityException) {}
    }

    private fun postNotifSuccess(ctx: Context, task: DownloadTask, file: File) {
        val nm = NotificationManagerCompat.from(ctx)
        val openIntent = openFileIntent(ctx, file)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(task.filename)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        try { nm.notify(task.id, n) } catch (_: SecurityException) {}
    }

    private fun postNotifFailed(ctx: Context, task: DownloadTask, error: String) {
        val nm = NotificationManagerCompat.from(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("下载失败")
            .setContentText("${task.filename}：$error")
            .setAutoCancel(true)
            .build()
        try { nm.notify(task.id, n) } catch (_: SecurityException) {}
    }

    private fun openFileIntent(ctx: Context, file: File): PendingIntent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(ctx, file.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun mimeType(name: String): String = when {
        name.endsWith(".zip")  -> "application/zip"
        name.endsWith(".apk")  -> "application/vnd.android.package-archive"
        name.endsWith(".pdf")  -> "application/pdf"
        name.endsWith(".txt")  -> "text/plain"
        else                   -> "application/octet-stream"
    }
}
