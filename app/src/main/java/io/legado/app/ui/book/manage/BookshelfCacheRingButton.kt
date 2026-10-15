package io.legado.app.ui.book.manage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.min

/**
 * 书卡右上角环形缓存按钮的状态。
 *
 * 外环显示这本书的整体缓存进度,内芯是当前可执行的操作,因此一个控件同时
 * 承担「进度」与「按钮」两种语义,不必再挤一行文字。
 */
internal enum class BookshelfCacheRingState {
    /** 未缓存,点一下开始缓存这本书。 */
    EMPTY,

    /** 已入队,等前面的章节派发。 */
    QUEUED,

    /** 正在下载,点一下单独暂停这本书。 */
    RUNNING,

    /** 被单独暂停,点一下继续。 */
    PAUSED,

    /** 已全部缓存。 */
    CACHED,

    /** 本地书籍,不需要缓存,只显示书籍图标。 */
    LOCAL,
}

/**
 * 长按环形按钮弹出的单本书操作。
 *
 * 环形按钮的单击只承担最常用的那一步(开始/暂停/继续),完整操作放在长按里,
 * 免得为一次性动作把卡片塞满。
 */
internal enum class BookshelfCacheMenuAction {
    /** 重新缓存 / 开始缓存这本书。 */
    CACHE,

    /** 单独暂停这本书。 */
    PAUSE,

    /** 继续这本书。 */
    RESUME,

    /** 放弃这本书的队列(不动其它书)。 */
    STOP,

    /** 清掉这本书已缓存的内容。 */
    CLEAR,
}

/**
 * 环形缓存按钮:外环即进度,内芯即操作。
 *
 * @param progress 这本书的整体缓存进度 0f..1f;仅在 [state] 为非 LOCAL 时绘制。
 * @param contentDescription 无障碍描述,由调用方按状态组装。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfCacheRingButton(
    state: BookshelfCacheRingState,
    progress: Float,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val metadataColor = Color(NgTheme.colors.onSurfaceVariant)
    val accent = when (state) {
        BookshelfCacheRingState.RUNNING -> Color(NgTheme.colors.primary)
        BookshelfCacheRingState.PAUSED -> colorResource(R.color.ng_warning)
        BookshelfCacheRingState.CACHED -> colorResource(R.color.ng_success)
        else -> metadataColor.copy(alpha = 0.38f)
    }
    val fraction = if (state == BookshelfCacheRingState.CACHED) {
        1f
    } else {
        progress.coerceIn(0f, 1f)
    }
    val enabled = state != BookshelfCacheRingState.LOCAL

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (state != BookshelfCacheRingState.LOCAL) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.4.dp.toPx()
                val radius = (min(size.width, size.height) - strokeWidth) / 2f
                val topLeft = Offset(
                    (size.width - radius * 2f) / 2f,
                    (size.height - radius * 2f) / 2f,
                )
                val arcSize = Size(radius * 2f, radius * 2f)
                // 轨道
                drawArc(
                    color = metadataColor.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                // 进度
                if (fraction > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }
        when (state) {
            BookshelfCacheRingState.EMPTY -> Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent,
            )

            BookshelfCacheRingState.QUEUED -> ClockGlyph(
                color = accent,
                modifier = Modifier.size(15.dp),
            )

            BookshelfCacheRingState.RUNNING -> Icon(
                painter = painterResource(R.drawable.ic_pause_24dp),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent,
            )

            BookshelfCacheRingState.PAUSED -> Icon(
                painter = painterResource(R.drawable.ic_play_24dp),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent,
            )

            BookshelfCacheRingState.CACHED -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent,
            )

            BookshelfCacheRingState.LOCAL -> Icon(
                painter = painterResource(R.drawable.ic_bookshelf_dock_local),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = metadataColor.copy(alpha = 0.62f),
            )
        }
    }
}

/** 「排队中」用的小钟表,表达「等待派发」而不是「可点击下载」。 */
@Composable
private fun ClockGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.2.dp.toPx()
        val radius = min(size.width, size.height) / 2f - strokeWidth
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius * 0.55f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * 0.42f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
