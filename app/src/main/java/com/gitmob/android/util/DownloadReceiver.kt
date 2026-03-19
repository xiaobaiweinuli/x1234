package com.gitmob.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收下载通知里的暂停 / 继续操作 */
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val taskId = intent.getIntExtra("taskId", -1)
        if (taskId == -1) return
        when (intent.action) {
            "com.gitmob.DOWNLOAD_PAUSE"  -> GmDownloadManager.pause(ctx, taskId)
            "com.gitmob.DOWNLOAD_RESUME" -> GmDownloadManager.resume(ctx, taskId)
        }
    }
}
