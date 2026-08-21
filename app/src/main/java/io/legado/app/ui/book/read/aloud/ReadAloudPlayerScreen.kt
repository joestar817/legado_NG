package io.legado.app.ui.book.read.aloud

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.listen.ListeningCoverArtwork
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class ReadAloudPlayerPage {
    COVER,
    TEXT,
}

internal data class ReadAloudParagraphUi(
    val range: IntRange,
    val text: String,
)

internal data class ReadAloudPlayerUiState(
    val bookName: String = "阅读NG",
    val bookUrl: String = "",
    val chapterTitle: String = "正在准备朗读",
    val subtitle: String = "正在准备朗读…",
    val coverPath: String? = null,
    val sourceOrigin: String? = null,
    val page: ReadAloudPlayerPage = ReadAloudPlayerPage.COVER,
    val paragraphs: List<ReadAloudParagraphUi> = emptyList(),
    val currentParagraphIndex: Int = 0,
    val progressMax: Int = 0,
    val progress: Int = 0,
    val bufferedProgress: Int = 0,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val timerLabel: String = "定时",
    val speedLabel: String = "1.0x",
    val engineLabel: String = "选择朗读音色",
)

internal sealed interface ReadAloudPlayerAction {
    data object Close : ReadAloudPlayerAction
    data object OpenBookInfo : ReadAloudPlayerAction
    data object Timer : ReadAloudPlayerAction
    data object Speed : ReadAloudPlayerAction
    data object Refresh : ReadAloudPlayerAction
    data object Original : ReadAloudPlayerAction
    data object More : ReadAloudPlayerAction
    data object Mode : ReadAloudPlayerAction
    data object Catalog : ReadAloudPlayerAction
    data object Voice : ReadAloudPlayerAction
    data object TogglePlay : ReadAloudPlayerAction
    data object PreviousChapter : ReadAloudPlayerAction
    data object NextChapter : ReadAloudPlayerAction
    data class SelectPage(val page: ReadAloudPlayerPage) : ReadAloudPlayerAction
    data class SeekPreview(val paragraphIndex: Int) : ReadAloudPlayerAction
    data object SeekFinished : ReadAloudPlayerAction
}

@Composable
internal fun ReadAloudPlayerScreen(
    state: ReadAloudPlayerUiState,
    onAction: (ReadAloudPlayerAction) -> Unit,
) {
    val context = LocalContext.current
    val artwork by ListeningCoverArtwork.remember(
        context = context,
        cacheKey = state.bookUrl.ifBlank { state.coverPath.orEmpty() },
        path = state.coverPath,
        sourceOrigin = state.sourceOrigin,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        ListeningPlayerBackground(artwork = artwork)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            PlayerTopBar(
                selectedPage = state.page,
                onClose = { onAction(ReadAloudPlayerAction.Close) },
            )
            PlayerContent(
                state = state,
                artwork = artwork,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            PlayerQuickActions(state = state, onAction = onAction)
            ReadAloudProgressSlider(
                value = state.progress,
                bufferedValue = state.bufferedProgress,
                max = state.progressMax,
                enabled = state.progressMax > 0,
                onValueChange = {
                    onAction(ReadAloudPlayerAction.SeekPreview(it))
                },
                onValueChangeFinished = {
                    onAction(ReadAloudPlayerAction.SeekFinished)
                },
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            PlayerControlDock(state = state, onAction = onAction)
            PlayerVoicePill(
                label = state.engineLabel,
                onClick = { onAction(ReadAloudPlayerAction.Voice) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(min = 220.dp, max = 360.dp)
                    .padding(top = 12.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun ListeningPlayerBackground(artwork: ImageBitmap?) {
    val colors = NgTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(colors.background)),
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.28f, scaleY = 1.28f)
                    .blur(54.dp)
                    .alpha(0.44f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(colors.background).copy(alpha = 0.24f),
                            Color(colors.background).copy(alpha = 0.50f),
                            Color(colors.background).copy(alpha = 0.88f),
                        )
                    )
                ),
        )
    }
}

@Composable
private fun PlayerTopBar(
    selectedPage: ReadAloudPlayerPage,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "关闭听书界面",
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(28.dp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadAloudPlayerPage.entries.forEach { page ->
                val selected = page == selectedPage
                Box(
                    modifier = Modifier
                        .width(if (selected) 24.dp else 6.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            Color(NgTheme.colors.onSurface).copy(
                                alpha = if (selected) 0.82f else 0.28f
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun PlayerContent(
    state: ReadAloudPlayerUiState,
    artwork: ImageBitmap?,
    onAction: (ReadAloudPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(state.page) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                    onDragCancel = { horizontalDrag = 0f },
                    onDragEnd = {
                        when {
                            horizontalDrag < -64.dp.toPx() -> {
                                onAction(
                                    ReadAloudPlayerAction.SelectPage(ReadAloudPlayerPage.TEXT)
                                )
                            }
                            horizontalDrag > 64.dp.toPx() -> {
                                onAction(
                                    ReadAloudPlayerAction.SelectPage(ReadAloudPlayerPage.COVER)
                                )
                            }
                        }
                        horizontalDrag = 0f
                    },
                )
            },
    ) {
        Crossfade(
            targetState = state.page,
            animationSpec = tween(durationMillis = 260),
            label = "readAloudPlayerPage",
        ) { page ->
            when (page) {
                ReadAloudPlayerPage.COVER -> PlayerCoverScene(
                    state = state,
                    artwork = artwork,
                    onCoverClick = { onAction(ReadAloudPlayerAction.OpenBookInfo) },
                )
                ReadAloudPlayerPage.TEXT -> PlayerTextScene(
                    state = state,
                    artwork = artwork,
                )
            }
        }
    }
}

@Composable
private fun PlayerCoverScene(
    state: ReadAloudPlayerUiState,
    artwork: ImageBitmap?,
    onCoverClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val coverHeight = (maxHeight * 0.47f).coerceIn(170.dp, 276.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PlayerCover(
                artwork = artwork,
                contentDescription = state.bookName,
                modifier = Modifier
                    .height(coverHeight)
                    .aspectRatio(0.72f)
                    .clickable(onClick = onCoverClick),
            )
            Text(
                text = state.bookName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 13.dp, start = 18.dp, end = 18.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 106.dp, max = 152.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(NgTheme.colors.surface).copy(alpha = 0.72f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.chapterTitle,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.subtitle,
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color(NgTheme.colors.onSurface).copy(alpha = 0.90f),
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTextScene(
    state: ReadAloudPlayerUiState,
    artwork: ImageBitmap?,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentParagraphIndex, state.paragraphs.size) {
        if (state.paragraphs.isNotEmpty()) {
            listState.animateScrollToItem(
                (state.currentParagraphIndex - 2).coerceIn(state.paragraphs.indices)
            )
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCover(
                artwork = artwork,
                contentDescription = null,
                modifier = Modifier
                    .height(58.dp)
                    .aspectRatio(0.72f),
                cornerRadius = 8.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = state.bookName,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.chapterTitle,
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.paragraphs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.subtitle,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 22.dp,
                    vertical = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(
                    items = state.paragraphs,
                    key = { index, item -> "${item.range.first}:$index" },
                ) { index, item ->
                    val active = index == state.currentParagraphIndex
                    Text(
                        text = item.text,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(NgTheme.colors.onSurface).copy(
                            alpha = if (active) 1f else 0.36f
                        ),
                        fontSize = if (active) 23.sp else 17.sp,
                        lineHeight = if (active) 34.sp else 27.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCover(
    artwork: ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(18.dp, shape, ambientColor = Color(NgTheme.colors.primary).copy(0.30f))
            .clip(shape)
            .background(Color(NgTheme.colors.surfaceContainerLow))
            .border(
                width = 1.dp,
                color = Color(NgTheme.colors.primary).copy(alpha = 0.34f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = contentDescription,
                tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.54f),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

private data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val action: ReadAloudPlayerAction,
)

@Composable
private fun PlayerQuickActions(
    state: ReadAloudPlayerUiState,
    onAction: (ReadAloudPlayerAction) -> Unit,
) {
    val actions = listOf(
        QuickAction(Icons.Rounded.Timer, state.timerLabel, ReadAloudPlayerAction.Timer),
        QuickAction(Icons.Rounded.Speed, state.speedLabel, ReadAloudPlayerAction.Speed),
        QuickAction(Icons.Rounded.Refresh, "刷新", ReadAloudPlayerAction.Refresh),
        QuickAction(Icons.AutoMirrored.Rounded.Article, "原文", ReadAloudPlayerAction.Original),
        QuickAction(Icons.Rounded.MoreHoriz, "更多", ReadAloudPlayerAction.More),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        actions.forEach { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onAction(item.action) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(NgTheme.colors.surface).copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = Color(NgTheme.colors.onSurfaceVariant),
                        modifier = Modifier.size(21.dp),
                    )
                }
                Text(
                    text = item.label,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReadAloudProgressSlider(
    value: Int,
    bufferedValue: Int,
    max: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val primary = Color(NgTheme.colors.primary)
    val surface = Color(NgTheme.colors.surface)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(enabled, max) {
                if (!enabled || max <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun valueAt(x: Float): Int {
                        val radius = 11.dp.toPx()
                        val width = (size.width - radius * 2f).coerceAtLeast(1f)
                        return (((x - radius) / width).coerceIn(0f, 1f) * max)
                            .roundToInt()
                            .coerceIn(0, max)
                    }
                    currentOnValueChange(valueAt(down.position.x))
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) currentOnValueChange(valueAt(change.position.x))
                            change.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    currentOnFinished()
                }
            },
    ) {
        val thumbRadius = 11.dp.toPx()
        val innerRadius = 7.dp.toPx()
        val trackHeight = 6.dp.toPx()
        val trackStart = thumbRadius
        val trackWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
        val top = (size.height - trackHeight) / 2f
        val radius = trackHeight / 2f
        val safeMax = max.coerceAtLeast(1)
        val safeValue = value.coerceIn(0, safeMax)
        val valueFraction = safeValue.toFloat() / safeMax
        val bufferFraction = bufferedValue.coerceIn(safeValue, safeMax).toFloat() / safeMax
        drawRoundRect(
            color = primary.copy(alpha = 0.18f),
            topLeft = Offset(trackStart - radius, top),
            size = Size(trackWidth + trackHeight, trackHeight),
            cornerRadius = CornerRadius(radius),
        )
        drawRoundRect(
            color = primary.copy(alpha = 0.42f),
            topLeft = Offset(trackStart - radius, top),
            size = Size((trackWidth * bufferFraction + trackHeight).coerceAtLeast(trackHeight), trackHeight),
            cornerRadius = CornerRadius(radius),
        )
        drawRoundRect(
            color = primary,
            topLeft = Offset(trackStart - radius, top),
            size = Size((trackWidth * valueFraction + trackHeight).coerceAtLeast(trackHeight), trackHeight),
            cornerRadius = CornerRadius(radius),
        )
        val thumbX = trackStart + trackWidth * valueFraction
        drawCircle(surface, thumbRadius, Offset(thumbX, size.height / 2f))
        drawCircle(primary, innerRadius, Offset(thumbX, size.height / 2f))
        drawCircle(
            color = primary.copy(alpha = 0.40f),
            radius = thumbRadius,
            center = Offset(thumbX, size.height / 2f),
            style = Stroke(1.dp.toPx()),
        )
    }
}

@Composable
private fun PlayerControlDock(
    state: ReadAloudPlayerUiState,
    onAction: (ReadAloudPlayerAction) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        shape = RoundedCornerShape(25.dp),
        color = Color(NgTheme.colors.surface).copy(alpha = 0.84f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerDockSideAction(
                icon = Icons.Rounded.Tune,
                label = "模式",
                onClick = { onAction(ReadAloudPlayerAction.Mode) },
                modifier = Modifier.weight(1.18f),
            )
            PlayerDockIconAction(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = "上一章",
                onClick = { onAction(ReadAloudPlayerAction.PreviousChapter) },
                modifier = Modifier.weight(0.88f),
            )
            Box(
                modifier = Modifier
                    .weight(0.94f)
                    .fillMaxHeight()
                    .clickable(enabled = !state.isPreparing) {
                        onAction(ReadAloudPlayerAction.TogglePlay)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isPreparing) {
                    ListeningLoadingBars()
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color(NgTheme.colors.onSurface),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            PlayerDockIconAction(
                icon = Icons.Rounded.SkipNext,
                contentDescription = "下一章",
                onClick = { onAction(ReadAloudPlayerAction.NextChapter) },
                modifier = Modifier.weight(0.88f),
            )
            PlayerDockSideAction(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                label = "目录",
                onClick = { onAction(ReadAloudPlayerAction.Catalog) },
                modifier = Modifier.weight(1.18f),
            )
        }
    }
}

@Composable
private fun PlayerDockSideAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(NgTheme.colors.onSurfaceVariant),
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PlayerDockIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun ListeningLoadingBars() {
    val contentColor = Color(NgTheme.colors.onSurface)
    val transition = rememberInfiniteTransition(label = "readAloudLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "readAloudLoadingPhase",
    )
    Canvas(modifier = Modifier.size(width = 32.dp, height = 28.dp)) {
        val barWidth = 4.dp.toPx()
        val gap = 5.dp.toPx()
        val totalWidth = barWidth * 3 + gap * 2
        repeat(3) { index ->
            val wave = (sin(phase + index * 1.3f) + 1f) / 2f
            val height = 9.dp.toPx() + 13.dp.toPx() * wave
            val left = (size.width - totalWidth) / 2f + index * (barWidth + gap)
            drawRoundRect(
                color = contentColor,
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun PlayerVoicePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(NgTheme.colors.surface).copy(alpha = 0.78f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 260.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(9.dp))
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurfaceVariant),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
