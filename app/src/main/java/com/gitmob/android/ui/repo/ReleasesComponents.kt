@file:OptIn(ExperimentalMaterial3Api::class)

package com.gitmob.android.ui.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.gitmob.android.api.*
import com.gitmob.android.ui.common.*
import com.gitmob.android.ui.theme.*
import com.gitmob.android.util.LogManager
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.defaultMinSize
import okhttp3.Request

/**
 * 对Issues列表进行筛选和排序
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesTab(
    state: RepoDetailState,
    vm: RepoDetailViewModel? = null, 
    c: GmColors,
    onRefresh: () -> Unit = {},
) {
    val releases = state.releases
    PullToRefreshBox(
        isRefreshing = state.releasesRefreshing,
        onRefresh = onRefresh,
    ) {
        if (releases.isEmpty()) {
            EmptyBox("暂无发行版")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(releases, key = { it.id }) { release ->
                    ReleaseCard(release = release, c = c, vm = vm)
                }
            }
        }

    }
}

@Composable
fun ReleaseCard(release: GHRelease, c: GmColors, vm: RepoDetailViewModel? = null) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bgCard, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (release.prerelease) {
                GmBadge("预发布", YellowDim, Yellow)
            }
            if (release.draft) {
                GmBadge("草稿", c.textTertiary, c.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(release.tagName, fontSize = 12.sp, color = Coral, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Text(release.name ?: release.tagName, fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
        release.body?.let { body ->
            Spacer(Modifier.height(6.dp))
            Text(
                body.take(200) + if (body.length > 200) "…" else "",
                fontSize = 12.sp,
                color = c.textSecondary,
                maxLines = 3
            )
        }
        if (release.assets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            GmDivider()
            Spacer(Modifier.height(8.dp))
            Text("附件 (${release.assets.size})", fontSize = 11.sp, color = c.textTertiary)
            Spacer(Modifier.height(4.dp))
            release.assets.forEach { asset ->
                val taskKey = "asset_${asset.id}"
                val taskId  = vm?.state?.collectAsState()?.value?.downloadTaskIds?.get(taskKey)
                val status  = taskId?.let { com.gitmob.android.util.GmDownloadManager.statusOf(it) }
                    ?.collectAsState()?.value
                val pct = (status as? com.gitmob.android.util.DownloadStatus.Progress)?.percent ?: 0
                val isDownloading = status is com.gitmob.android.util.DownloadStatus.Progress
                val isDone        = status is com.gitmob.android.util.DownloadStatus.Success

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(c.bgItem, RoundedCornerShape(8.dp))
                        .clickable { vm?.downloadAsset(asset) },
                ) {
                    if (isDownloading && pct > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct / 100f)
                                .matchParentSize()
                                .background(Coral.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            when {
                                isDone        -> Icons.Default.CheckCircle
                                isDownloading -> Icons.Default.Close
                                else          -> Icons.Default.Download
                            },
                            null,
                            tint = when {
                                isDone        -> Green
                                isDownloading -> Coral
                                else          -> c.textTertiary
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.name, fontSize = 12.sp, color = c.textPrimary, maxLines = 1)
                            Text(formatSize(asset.size), fontSize = 10.sp, color = c.textTertiary)
                        }
                        if (isDownloading)
                            Text("$pct%", fontSize = 11.sp, color = Coral,
                                fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─── Actions Tab ─────────────────────────────────────────────────────
