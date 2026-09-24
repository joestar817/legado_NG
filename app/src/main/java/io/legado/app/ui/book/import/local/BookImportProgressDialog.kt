package io.legado.app.ui.book.import.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.model.localBook.BookImportPhase
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun BookImportProgressDialog(
    batch: BookImportBatch,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(batch.running) {
        while (batch.running) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val primary = Color(NgTheme.colors.primary)
    val foreground = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val errorColor = Color(NgTheme.colors.error)
    val completed = batch.successes + batch.failures
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(
        onDismissRequest = { if (!batch.running) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !batch.running,
            dismissOnClickOutside = false,
        ),
    ) {
        NgDialog(
            title = when {
                batch.running -> "导入书籍"
                batch.stopped > 0 -> "导入已停止"
                else -> "导入完成"
            },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).heightIn(max = maxHeight),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                if (batch.running) {
                    NgButton(
                        onClick = onStop, enabled = !batch.stopRequested,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                    ) {
                        Text(if (batch.stopRequested) "正在停止…" else "停止导入", fontSize = 16.sp)
                    }
                } else {
                    if (batch.items.any { it.canRetry }) {
                        NgButton(
                            onClick = onRetry,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            variant = NgButtonVariant.OUTLINE,
                        ) {
                            Text(if (batch.stopped > 0) "重试未完成项" else "重试失败项",
                                fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1)
                        }
                    }
                    NgButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(80.dp).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                    ) { Text("完成", fontSize = 14.sp, lineHeight = 20.sp) }
                }
            },
        ) {
            Text(batch.title, color = foreground, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已处理 $completed/${batch.items.size} 本", color = foreground, fontSize = 13.sp)
                Text("总耗时 ${formatImportDuration(batch.elapsed(now))}", color = secondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            // The overall bar counts terminal outcomes; a failure is processed, not successful.
            Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(secondary.copy(alpha = 0.12f))) {
                if (batch.successes > 0) Box(Modifier.weight(batch.successes.toFloat()).height(6.dp).background(primary))
                if (batch.failures > 0) Box(Modifier.weight(batch.failures.toFloat()).height(6.dp).background(errorColor))
                val remaining = batch.items.size - completed
                if (remaining > 0) Spacer(Modifier.weight(remaining.toFloat()))
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(batch.items, key = { it.key }) { item ->
                    ImportProgressRow(item, now)
                    HorizontalDivider(color = secondary.copy(alpha = 0.12f), thickness = 0.5.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(buildString {
                append("成功 ${batch.successes} 本 · 失败 ${batch.failures} 本")
                if (batch.stopped > 0) append(" · 未完成 ${batch.stopped} 本")
            }, color = foreground, fontSize = 13.sp)
            if (batch.stopRequested && batch.running) {
                Spacer(Modifier.height(6.dp))
                Text("当前书籍完成后停止", color = secondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ImportProgressRow(item: BookImportItem, now: Long) {
    val phase = item.phase
    val foreground = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val color = when (phase) {
        BookImportPhase.SUCCESS -> colorResource(R.color.ng_success)
        BookImportPhase.FAILED -> Color(NgTheme.colors.error)
        BookImportPhase.WAITING, BookImportPhase.STOPPED -> secondary
        else -> Color(NgTheme.colors.primary)
    }
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null,
                tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = foreground, fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            val bar = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            val track = secondary.copy(alpha = 0.12f)
            when {
                phase == BookImportPhase.SUCCESS -> Box(bar.background(color))
                phase == BookImportPhase.FAILED -> Box(bar.background(color.copy(alpha = 0.22f)))
                phase == BookImportPhase.WAITING || phase == BookImportPhase.STOPPED -> Box(bar.background(track))
                phase == BookImportPhase.COPYING && item.total > 0 -> LinearProgressIndicator(
                    progress = { (item.bytes.toFloat() / item.total).coerceIn(0f, 1f) },
                    modifier = bar, color = color, trackColor = track,
                )
                else -> LinearProgressIndicator(modifier = bar, color = color, trackColor = track)
            }
            Spacer(Modifier.height(7.dp))
            val status = when (phase) {
                BookImportPhase.WAITING -> "等待中"
                BookImportPhase.EXTRACTING -> "正在解压"
                BookImportPhase.COPYING -> "正在复制"
                BookImportPhase.PARSING -> "正在解析"
                BookImportPhase.SUCCESS -> "导入成功"
                BookImportPhase.FAILED -> "导入失败"
                BookImportPhase.STOPPED -> "未导入"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (phase == BookImportPhase.SUCCESS || phase == BookImportPhase.FAILED) {
                    Icon(if (phase == BookImportPhase.SUCCESS) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(status + if (item.startedAt != null) " · ${formatImportDuration(item.elapsed(now))}" else "",
                    color = color, fontSize = 12.sp)
            }
            item.error?.let { error ->
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(error.summary, modifier = Modifier.weight(1f), color = foreground,
                        fontSize = 13.sp, lineHeight = 18.sp)
                    Text(if (expanded) "收起" else "详情", color = secondary, fontSize = 12.sp)
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "收起失败详情" else "展开失败详情",
                        tint = secondary, modifier = Modifier.size(18.dp))
                }
                if (expanded) {
                    val context = LocalContext.current
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.06f)).padding(10.dp)) {
                        Text(error.detail, color = secondary, fontSize = 12.sp, lineHeight = 18.sp)
                        TextButton(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("导入失败原因", "${item.name}\n${error.detail}"))
                        }, modifier = Modifier.align(Alignment.End)) {
                            Text("复制原因", color = Color(NgTheme.colors.primary), fontSize = 12.sp,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

internal fun formatImportDuration(milliseconds: Long): String =
    if (milliseconds < 60_000) String.format(Locale.getDefault(), "%.1f 秒", milliseconds / 1000.0)
    else "${milliseconds / 60_000} 分 ${(milliseconds / 1000) % 60} 秒"
