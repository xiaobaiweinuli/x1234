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
import androidx.compose.material.icons.automirrored.filled.Undo
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
import kotlinx.coroutines.launch
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

/** 解析后的 diff 行 */
data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLine: Int?,
    val newLine: Int?,
)
enum class DiffLineType { CONTEXT, ADD, DEL, HUNK, META }

/** 从 unified diff patch 解析为带行号的 DiffLine 列表 */
fun parsePatch(patch: String): List<DiffLine> {
    val result = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0
    for (raw in patch.lines()) {
        when {
            raw.startsWith("@@") -> {
                // @@ -a,b +c,d @@ 提取起始行号
                val m = Regex("""-(\d+)(?:,\d+)?\s+\+(\d+)""").find(raw)
                if (m != null) {
                    oldLine = m.groupValues[1].toIntOrNull()?.minus(1) ?: 0
                    newLine = m.groupValues[2].toIntOrNull()?.minus(1) ?: 0
                }
                result += DiffLine(DiffLineType.HUNK, raw, null, null)
            }
            raw.startsWith("+") && !raw.startsWith("+++") -> {
                newLine++
                result += DiffLine(DiffLineType.ADD, raw.drop(1), null, newLine)
            }
            raw.startsWith("-") && !raw.startsWith("---") -> {
                oldLine++
                result += DiffLine(DiffLineType.DEL, raw.drop(1), oldLine, null)
            }
            raw.startsWith("---") || raw.startsWith("+++") -> {
                result += DiffLine(DiffLineType.META, raw, null, null)
            }
            else -> {
                oldLine++; newLine++
                result += DiffLine(DiffLineType.CONTEXT, raw.drop(1).ifEmpty { raw }, oldLine, newLine)
            }
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDiffSheet(
    info: FilePatchInfo,
    c: GmColors,
    vm: RepoDetailViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lines = remember(info.patch) { parsePatch(info.patch) }

    // 前版本内容（懒加载）
    var beforeContent by remember { mutableStateOf<String?>(null) }
    var beforeLoading by remember { mutableStateOf(false) }
    var beforeExpanded by remember { mutableStateOf(false) }
    var showRevertConfirm by remember { mutableStateOf(false) }

    // GitHub 暗色主题（与 GitHub 网页一致）
    val ghBg      = Color(0xFF0D1117)
    val ghCard    = Color(0xFF161B22)
    val ghBorder  = Color(0xFF30363D)
    val ghText    = Color(0xFFE6EDF3)
    val ghMuted   = Color(0xFF8B949E)
    val addBg     = Color(0xFF0D4A29); val addFg = Color(0xFF85E89D)
    val delBg     = Color(0xFF430D18); val delFg = Color(0xFFFFA198)
    val hunkBg    = Color(0xFF1B3A5E); val hunkFg = Color(0xFF79C0FF)
    val lineNumW  = 42.dp

    fun loadBefore() {
        if (beforeContent != null || info.parentSha == null) return
        beforeLoading = true
        scope.launch {
            try {
                val targetFilename = info.previousFilename ?: info.filename
                val content = ApiClient.rawHttpClient().newCall(
                    okhttp3.Request.Builder()
                        .url("https://api.github.com/repos/${info.owner}/${info.repoName}/contents/$targetFilename?ref=${info.parentSha}")
                        .header("Authorization", "Bearer ${ApiClient.currentToken() ?: ""}")
                        .header("Accept", "application/vnd.github.raw+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .build()
                ).execute().use { it.body?.string() ?: "" }
                beforeContent = content
                beforeExpanded = true
            } catch (_: Exception) {
                beforeContent = "（无法加载前版本内容）"
                beforeExpanded = true
            } finally {
                beforeLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ghBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ghBorder) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
        ) {
            // ── 头部：文件名 + 统计 + 操作按钮 ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ghCard)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Description, null,
                        tint = ghMuted, modifier = Modifier.size(15.dp))
                    Text(
                        if (info.status == "renamed" && info.previousFilename != null)
                            "${info.previousFilename} → ${info.filename}"
                        else info.filename,
                        fontSize = 12.sp, color = ghText,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f), maxLines = 2,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 统计：+additions -deletions
                    if (info.additions > 0)
                        Text("+${info.additions}", fontSize = 12.sp,
                            color = Color(0xFF3FB950), fontWeight = FontWeight.SemiBold)
                    if (info.deletions > 0)
                        Text("-${info.deletions}", fontSize = 12.sp,
                            color = Color(0xFFF85149), fontWeight = FontWeight.SemiBold)
                    // 状态徽章
                    val (badgeBg, badgeFg, badgeText) = when (info.status) {
                        "added"   -> Triple(Color(0xFF0D4A29), Color(0xFF3FB950), "新增")
                        "removed" -> Triple(Color(0xFF430D18), Color(0xFFF85149), "删除")
                        "renamed" -> Triple(Color(0xFF1B3A5E), Color(0xFF79C0FF), "重命名")
                        else      -> Triple(Color(0xFF2D2A1E), Color(0xFFE3B341), "修改")
                    }
                    Text(badgeText, fontSize = 10.sp, color = badgeFg,
                        modifier = Modifier.background(badgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))

                    Spacer(Modifier.weight(1f))

                    // 前版本按钮
                    if (info.parentSha != null && info.status != "added") {
                        IconButton(
                            onClick = {
                                if (beforeContent == null) loadBefore()
                                else beforeExpanded = !beforeExpanded
                            },
                            modifier = Modifier
                                .height(30.dp)
                                .background(
                                    if (beforeExpanded) Color(0xFF1F3A2D) else Color(0xFF21262D),
                                    RoundedCornerShape(6.dp),
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (beforeLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp, color = Color(0xFF79C0FF))
                                } else {
                                    Icon(
                                        if (beforeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null, tint = Color(0xFF79C0FF),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Text(
                                    if (beforeExpanded) "收起前版本" else "查看前版本",
                                    fontSize = 11.sp, color = Color(0xFF79C0FF),
                                )
                            }
                        }
                    }

                    // 撤销回滚按钮（仅 modified/added/removed 时显示）
                    if (info.parentSha != null && info.currentBranch.isNotBlank()) {
                        IconButton(
                            onClick = { showRevertConfirm = true },
                            modifier = Modifier
                                .height(30.dp)
                                .background(Color(0xFF3D1F1F), RoundedCornerShape(6.dp)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null,
                                    tint = Color(0xFFF85149), modifier = Modifier.size(14.dp))
                                Text("回滚提交", fontSize = 11.sp, color = Color(0xFFF85149))
                            }
                        }
                    }

                    // 关闭按钮
                    IconButton(onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                            .background(Color(0xFF21262D), RoundedCornerShape(6.dp))) {
                        Icon(Icons.Default.Close, null,
                            tint = ghMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            HorizontalDivider(color = ghBorder, thickness = 0.5.dp)

            // ── 前版本内容区（折叠/展开）───────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = beforeExpanded && beforeContent != null,
                enter = androidx.compose.animation.fadeIn() +
                        androidx.compose.animation.expandVertically(),
                exit  = androidx.compose.animation.fadeOut() +
                        androidx.compose.animation.shrinkVertically(),
            ) {
                Column(modifier = Modifier.weight(0.4f, fill = false)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF1B3A5E))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null,
                            tint = Color(0xFF79C0FF), modifier = Modifier.size(14.dp))
                        Text("修改前（${info.parentSha?.take(7) ?: ""}）",
                            fontSize = 12.sp, color = Color(0xFF79C0FF))
                    }
                    HorizontalDivider(color = ghBorder, thickness = 0.5.dp)
                    // 前版本纯文本（带行号）
                    val bLines = beforeContent?.lines() ?: emptyList()
                    val numW = bLines.size.toString().length
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                            bLines.forEachIndexed { i, line ->
                                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                    // 行号列
                                    Box(
                                        modifier = Modifier
                                            .width((numW * 8 + 16).dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFF161B22)),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text("${i + 1}", fontSize = 10.sp,
                                            color = ghMuted, fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                    }
                                    // 内容列
                                    Text(line, fontSize = 11.sp, color = ghText,
                                        fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 600.dp)
                                            .padding(horizontal = 6.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = ghBorder, thickness = 1.dp)
                    Row(modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.ArrowDownward, null,
                            tint = ghMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("修改后 ↓", fontSize = 11.sp, color = ghMuted)
                    }
                }
            }

            // ── Diff 主体（带行号）──────────────────────────────────────────
            val lineNumDigits = lines.mapNotNull { it.newLine ?: it.oldLine }
                .maxOrNull()?.toString()?.length ?: 3
            val numColW = (lineNumDigits * 8 + 12).dp

            Row(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                    lines.filter { it.type != DiffLineType.META }.forEach { dl ->
                        when (dl.type) {
                            DiffLineType.HUNK -> {
                                // Hunk header
                                Row(modifier = Modifier
                                    .defaultMinSize(minWidth = 700.dp)
                                    .background(hunkBg)
                                    .padding(vertical = 1.dp)) {
                                    // 空行号占位
                                    Box(modifier = Modifier.width(numColW * 2).background(Color(0xFF161B22)))
                                    Text(dl.content, fontSize = 10.sp, color = hunkFg,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            DiffLineType.ADD, DiffLineType.DEL, DiffLineType.CONTEXT -> {
                                val bg = when (dl.type) {
                                    DiffLineType.ADD  -> addBg
                                    DiffLineType.DEL  -> delBg
                                    else              -> Color.Transparent
                                }
                                val fg = when (dl.type) {
                                    DiffLineType.ADD  -> addFg
                                    DiffLineType.DEL  -> delFg
                                    else              -> ghText
                                }
                                val prefix = when (dl.type) {
                                    DiffLineType.ADD  -> "+"
                                    DiffLineType.DEL  -> "-"
                                    else              -> " "
                                }
                                Row(modifier = Modifier
                                    .defaultMinSize(minWidth = 700.dp)
                                    .background(bg)
                                    .height(IntrinsicSize.Min)) {
                                    // 旧行号
                                    Box(modifier = Modifier
                                        .width(numColW)
                                        .fillMaxHeight()
                                        .background(
                                            if (dl.type == DiffLineType.DEL) Color(0xFF3D1520)
                                            else Color(0xFF161B22)
                                        ),
                                        contentAlignment = Alignment.CenterEnd) {
                                        Text(dl.oldLine?.toString() ?: "",
                                            fontSize = 10.sp, color = ghMuted,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                    // 新行号
                                    Box(modifier = Modifier
                                        .width(numColW)
                                        .fillMaxHeight()
                                        .background(
                                            if (dl.type == DiffLineType.ADD) Color(0xFF0D2E1A)
                                            else Color(0xFF161B22)
                                        ),
                                        contentAlignment = Alignment.CenterEnd) {
                                        Text(dl.newLine?.toString() ?: "",
                                            fontSize = 10.sp, color = ghMuted,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                    // 符号 + 内容
                                    Text(
                                        text = "$prefix${dl.content}",
                                        fontSize = 11.sp, color = fg,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp,
                                        softWrap = false,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 600.dp)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // ── 撤销回滚确认弹窗 ────────────────────────────────────────────────────
    if (showRevertConfirm) {
        AlertDialog(
            onDismissRequest = { showRevertConfirm = false },
            containerColor   = Color(0xFF161B22),
            icon  = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = Color(0xFFF85149)) },
            title = { Text("撤销这次提交？", color = Color(0xFFE6EDF3),
                fontWeight = FontWeight.SemiBold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将在分支「${info.currentBranch}」上创建一个新的 revert 提交，\n" +
                         "撤销 ${info.currentSha.take(7)} 的所有变更。\n\n" +
                         "此操作不重写历史，可安全用于保护分支。",
                        fontSize = 13.sp, color = Color(0xFF8B949E), lineHeight = 20.sp)
                    Text("提交 SHA：${info.currentSha.take(7)}",
                        fontSize = 11.sp, color = Color(0xFF79C0FF),
                        fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRevertConfirm = false
                        vm.revertCommit(info.currentSha,
                            "Revert \"${info.filename}\" changes from ${info.currentSha.take(7)}")
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF85149)),
                ) { Text("确认撤销") }
            },
            dismissButton = {
                TextButton(onClick = { showRevertConfirm = false }) {
                    Text("取消", color = Color(0xFF8B949E))
                }
            },
        )
    }
}

// ─── 工具方法 ────────────────────────────────────────────────────────────────

