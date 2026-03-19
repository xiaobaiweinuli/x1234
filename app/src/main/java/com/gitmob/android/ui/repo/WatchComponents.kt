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
fun WatchSheet(
    state: RepoDetailState,
    vm: RepoDetailViewModel,
    onDismiss: () -> Unit,
) {
    val c = LocalGmColors.current

    /**
     * 计算当前实际模式：
     *   subscription == null       → PARTICIPATING（未设置，GitHub 默认"仅参与后@提及"）
     *   ignored == true            → IGNORE
     *   subscribed == true         → ALL_ACTIVITY 或 CUSTOM（用本地 customNotify* 区分）
     */
    val currentMode: WatchMode = when {
        state.subscription == null           -> WatchMode.PARTICIPATING
        state.subscription.ignored          -> WatchMode.IGNORE
        state.subscription.subscribed        -> WatchMode.ALL_ACTIVITY
        else                                 -> WatchMode.PARTICIPATING
    }

    // 是否展开"自定义"小节（默认展开）
    var customExpanded by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("订阅通知", fontSize = 16.sp, color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold)
                if (state.subscriptionLoading)
                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp, color = Coral)
            }
            GmDivider()
            Spacer(Modifier.height(4.dp))

            // ── 四个互斥选项 ──────────────────────────────────────────────

            WatchOptionRow(
                icon        = Icons.Default.NotificationsNone,
                title       = "参与后 @提及",
                desc        = "仅在参与或被提及时收到通知",
                selected    = currentMode == WatchMode.PARTICIPATING,
                useRadio    = true,
                c           = c,
                onClick     = { vm.setWatchMode(WatchMode.PARTICIPATING); onDismiss() },
            )
            WatchOptionRow(
                icon        = Icons.Default.Notifications,
                title       = "所有活动",
                desc        = "接收该仓库的所有通知",
                selected    = currentMode == WatchMode.ALL_ACTIVITY,
                useRadio    = true,
                c           = c,
                onClick     = { vm.setWatchMode(WatchMode.ALL_ACTIVITY); onDismiss() },
            )
            WatchOptionRow(
                icon        = Icons.Default.NotificationsOff,
                title       = "忽略",
                desc        = "永不收到该仓库的通知（除非被 @提及）",
                selected    = currentMode == WatchMode.IGNORE,
                useRadio    = true,
                c           = c,
                onClick     = { vm.setWatchMode(WatchMode.IGNORE); onDismiss() },
            )

            // ── 自定义（折叠/展开）──────────────────────────────────────

            // 自定义选项头（点击切换折叠 & 同时选中此模式）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (currentMode != WatchMode.CUSTOM) {
                            vm.setWatchMode(WatchMode.CUSTOM)
                        }
                        customExpanded = !customExpanded
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(
                    selected = currentMode == WatchMode.CUSTOM || customExpanded,
                    onClick  = null,
                    colors   = RadioButtonDefaults.colors(selectedColor = Coral),
                )
                Icon(Icons.Default.Tune, null,
                    tint = if (customExpanded) Coral else c.textSecondary,
                    modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("自定义", fontSize = 14.sp, color = c.textPrimary,
                        fontWeight = FontWeight.Medium)
                    Text("选择要接收通知的事件类型",
                        fontSize = 12.sp, color = c.textSecondary)
                }
                // 折叠/展开图标（复用 JobCard 风格）
                androidx.compose.animation.AnimatedContent(
                    targetState = customExpanded, label = "custom_expand",
                ) { exp ->
                    Icon(
                        if (exp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (exp) "收起" else "展开",
                        tint = c.textTertiary, modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 自定义子项（折叠动画）
            androidx.compose.animation.AnimatedVisibility(
                visible = customExpanded,
                enter   = androidx.compose.animation.fadeIn() +
                          androidx.compose.animation.expandVertically(),
                exit    = androidx.compose.animation.fadeOut() +
                          androidx.compose.animation.shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bgItem, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    data class CustomItem(
                        val label: String,
                        val getter: RepoDetailState.() -> Boolean,
                        val toggle: (Boolean) -> Unit,
                    )
                    val items = listOf(
                        CustomItem("议题", { customNotifyIssues })
                            { vm.updateCustomNotify(issues = it) },
                        CustomItem("拉取请求", { customNotifyPRs })
                            { vm.updateCustomNotify(prs = it) },
                        CustomItem("发行版", { customNotifyReleases })
                            { vm.updateCustomNotify(releases = it) },
                        CustomItem("讨论", { customNotifyDiscussions })
                            { vm.updateCustomNotify(discussions = it) },
                        CustomItem("安全警报", { customNotifySecurity })
                            { vm.updateCustomNotify(security = it) },
                    )
                    items.forEachIndexed { i, item ->
                        val checked = item.getter(state)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.toggle(!checked) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 方框勾选（Checkbox）
                            Checkbox(
                                checked  = checked,
                                onCheckedChange = null,
                                colors   = CheckboxDefaults.colors(checkedColor = Coral),
                            )
                            Text(item.label, fontSize = 14.sp, color = c.textPrimary,
                                modifier = Modifier.weight(1f))
                        }
                        if (i < items.lastIndex)
                            HorizontalDivider(color = c.border, thickness = 0.5.dp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 保存自定义按钮
            if (customExpanded) {
                Button(
                    onClick = { vm.setWatchMode(WatchMode.CUSTOM); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Coral),
                ) { Text("保存自定义设置") }
            }
        }
    }
}

/** 订阅选项行（圆圈单选 + 图标 + 标题 + 描述） */
@Composable
fun WatchOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    useRadio: Boolean,
    c: GmColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (useRadio) {
            RadioButton(
                selected = selected, onClick = null,
                colors   = RadioButtonDefaults.colors(selectedColor = Coral),
            )
        }
        Icon(icon, null,
            tint = if (selected) Coral else c.textSecondary,
            modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
            Text(desc,  fontSize = 12.sp, color = c.textSecondary)
        }
        if (selected)
            Icon(Icons.Default.Check, null, tint = Coral, modifier = Modifier.size(18.dp))
    }
}
