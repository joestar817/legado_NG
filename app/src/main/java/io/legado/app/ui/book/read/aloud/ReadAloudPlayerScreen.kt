package io.legado.app.ui.book.read.aloud

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.hct.Hct
import io.legado.app.R
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.ui.book.listen.ListeningCoverArtwork
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgLiquidGlassBackdropProvider
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.components.compose.currentNgLiquidGlassBackdrop
import io.legado.app.ui.design.components.compose.ngRecordLiquidGlassBackdrop
import io.legado.app.ui.design.components.compose.rememberNgLiquidGlassBackdrop
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
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
    val bookAuthor: String = "",
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
    val motionSettings: ListeningMotionSettings = ListeningMotionSettings(),
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
    val useNoCoverFallback = state.coverPath.isNullOrBlank()
    val artwork by ListeningCoverArtwork.remember(
        context = context,
        cacheKey = state.bookUrl.ifBlank { state.coverPath.orEmpty() },
        path = state.coverPath,
        sourceOrigin = state.sourceOrigin,
    )
    val liquidBackdrop = rememberNgLiquidGlassBackdrop()
    NgLiquidGlassBackdropProvider(liquidBackdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .ngRecordLiquidGlassBackdrop(liquidBackdrop),
            ) {
                ListeningPlayerBackground(
                    artwork = artwork,
                    useNoCoverFallback = useNoCoverFallback,
                )
                when (state.motionSettings.effect) {
                    ListeningMotionEffect.FLAME -> {
                        ReadAloudFireMotionBackground(settings = state.motionSettings)
                    }

                    ListeningMotionEffect.FLUID -> {
                        ReadAloudFluidMotionBackground(settings = state.motionSettings)
                    }
                }
            }
            PlayerContent(
                state = state,
                artwork = artwork,
                onAction = onAction,
            )
            PlayerTopBar(
                selectedPage = state.page,
                onClose = { onAction(ReadAloudPlayerAction.Close) },
            )
        }
    }
}

@Composable
internal fun ListeningPlayerBackground(
    artwork: ImageBitmap?,
    useNoCoverFallback: Boolean,
) {
    if (useNoCoverFallback) {
        NoCoverPlayerBackground()
        return
    }
    val colors = NgTheme.colors
    val background = Color(colors.background)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (artwork == null) background else Color.Black),
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.06f, scaleY = 1.06f)
                    .blur(8.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .alpha(0.76f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background.copy(alpha = if (artwork == null) 0f else 0.14f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.18f),
                        0.42f to Color.Black.copy(alpha = 0.36f),
                        0.68f to Color.Black.copy(alpha = 0.76f),
                        1.00f to Color.Black.copy(alpha = 0.90f),
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                        )
                    )
                ),
        )
    }
}

@Composable
private fun NoCoverPlayerBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070706)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF8D6848).copy(alpha = 0.20f),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.50f, size.height * 0.18f),
            )
            drawCircle(
                color = Color(0xFF5A4635).copy(alpha = 0.12f),
                radius = size.width * 0.64f,
                center = Offset(size.width * 0.14f, size.height * 0.48f),
            )
        }
        Image(
            painter = painterResource(R.drawable.image_cover_default),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = 1.16f, scaleY = 1.16f)
                .blur(36.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .alpha(0.07f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.12f),
                        0.34f to Color.Black.copy(alpha = 0.30f),
                        0.68f to Color.Black.copy(alpha = 0.76f),
                        1.00f to Color.Black.copy(alpha = 0.94f),
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.34f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.30f),
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
    val closeInteractionSource = remember { MutableInteractionSource() }
    val closePressed by closeInteractionSource.collectIsPressedAsState()
    val closePressProgress by animateFloatAsState(
        targetValue = if (closePressed && NgTheme.usesLiquidGlass) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 720f),
        label = "closeLiquidPress",
    )
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 32.dp)
                .size(38.dp),
        ) {
            PlayerTranslucentSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .playerLiquidPressTransform(closePressProgress),
                shape = CircleShape,
                role = NgMaterialRole.INTERACTIVE,
                liquidCornerRadius = 19.dp,
                containerAlpha = 0.54f,
                elevation = 3.dp,
            ) {}
            IconButton(
                onClick = onClose,
                modifier = Modifier.fillMaxSize(),
                interactionSource = closeInteractionSource,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_read_aloud_chevron_down),
                    contentDescription = "关闭听书界面",
                    tint = Color(NgTheme.colors.onSurface),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp),
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
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                ReadAloudPlayerPage.COVER -> PlayerCoverPage(
                    state = state,
                    artwork = artwork,
                    onCoverClick = { onAction(ReadAloudPlayerAction.OpenBookInfo) },
                    onAction = onAction,
                )
                ReadAloudPlayerPage.TEXT -> PlayerTextPage(
                    state = state,
                    artwork = artwork,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun PlayerCoverPage(
    state: ReadAloudPlayerUiState,
    artwork: ImageBitmap?,
    onCoverClick: () -> Unit,
    onAction: (ReadAloudPlayerAction) -> Unit,
) {
    var titleLineCount by remember(state.bookName) { mutableIntStateOf(1) }
    val topPadding = if (titleLineCount > 1) 52.dp else 76.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = topPadding, end = 28.dp, bottom = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        PlayerCover(
            artwork = artwork,
            fallbackTitle = state.bookName,
            fallbackAuthor = state.bookAuthor,
            useNoCoverFallback = state.coverPath.isNullOrBlank(),
            contentDescription = state.bookName,
            modifier = Modifier
                .size(width = 152.dp, height = 216.dp)
                .clickable(onClick = onCoverClick),
            cornerRadius = 10.dp,
            ambientGlow = true,
        )
        Text(
            text = state.bookName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleLineCount = it.lineCount },
        )
        PlayerCoverInfoCard(
            state = state,
            modifier = Modifier.padding(top = 12.dp),
        )
        PlayerQuickActions(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(top = 10.dp),
        )
        PlayerProgress(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(top = 4.dp),
        )
        PlayerControlDock(
            state = state,
            onAction = onAction,
            modifier = Modifier
                .offset(y = (-4).dp)
                .padding(top = 4.dp),
        )
        PlayerVoicePill(
            label = state.engineLabel,
            onClick = { onAction(ReadAloudPlayerAction.Voice) },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PlayerTextPage(
    state: ReadAloudPlayerUiState,
    artwork: ImageBitmap?,
    onAction: (ReadAloudPlayerAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 82.dp, end = 28.dp, bottom = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerTextScene(
            state = state,
            artwork = artwork,
            modifier = Modifier.weight(1f),
        )
        PlayerQuickActions(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(top = 10.dp),
        )
        PlayerProgress(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(top = 4.dp),
        )
        PlayerControlDock(
            state = state,
            onAction = onAction,
            modifier = Modifier
                .offset(y = (-4).dp)
                .padding(top = 4.dp),
        )
        PlayerVoicePill(
            label = state.engineLabel,
            onClick = { onAction(ReadAloudPlayerAction.Voice) },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PlayerCoverInfoCard(
    state: ReadAloudPlayerUiState,
    modifier: Modifier = Modifier,
) {
    PlayerTranslucentSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(212.dp),
        shape = RoundedCornerShape(8.dp),
        containerAlpha = 0.42f,
        elevation = 7.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.chapterTitle,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.subtitle,
                    color = Color(NgTheme.colors.onSurface).copy(alpha = 0.92f),
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerProgress(
    state: ReadAloudPlayerUiState,
    onAction: (ReadAloudPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReadAloudProgressSlider(
        value = state.progress,
        bufferedValue = state.bufferedProgress,
        max = state.progressMax,
        enabled = state.progressMax > 0,
        onValueChange = { onAction(ReadAloudPlayerAction.SeekPreview(it)) },
        onValueChangeFinished = { onAction(ReadAloudPlayerAction.SeekFinished) },
        modifier = modifier,
    )
}

@Composable
private fun PlayerTextScene(
    state: ReadAloudPlayerUiState,
    artwork: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val activeParagraphStyle = TextStyle(
        fontSize = 23.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val inactiveParagraphStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Normal,
    )
    var textViewportSize by remember { mutableStateOf(IntSize.Zero) }
    val currentIndex = state.currentParagraphIndex.takeIf {
        it in state.paragraphs.indices
    }
    val scrollTarget = remember(
        state.paragraphs,
        currentIndex,
        textViewportSize,
        density.density,
        density.fontScale,
    ) {
        if (currentIndex == null || textViewportSize == IntSize.Zero) {
            null
        } else {
            val horizontalPadding = with(density) { 44.dp.roundToPx() }
            val verticalPadding = with(density) { 48.dp.roundToPx() }
            val paragraphSpacing = with(density) { 24.dp.roundToPx() }
            val textWidth = (textViewportSize.width - horizontalPadding).coerceAtLeast(1)
            val usableHeight = (textViewportSize.height - verticalPadding).coerceAtLeast(1)
            fun paragraphHeight(index: Int, style: TextStyle): Int =
                textMeasurer.measure(
                    text = state.paragraphs[index].text,
                    style = style,
                    constraints = Constraints(maxWidth = textWidth),
                ).size.height

            var target = currentIndex
            var occupiedHeight = paragraphHeight(currentIndex, activeParagraphStyle)
            for (index in currentIndex - 1 downTo (currentIndex - 2).coerceAtLeast(0)) {
                val nextHeight = occupiedHeight + paragraphSpacing +
                    paragraphHeight(index, inactiveParagraphStyle)
                if (nextHeight > usableHeight) break
                occupiedHeight = nextHeight
                target = index
            }
            target
        }
    }
    LaunchedEffect(state.paragraphs, currentIndex, scrollTarget) {
        if (scrollTarget != null) {
            listState.animateScrollToItem(scrollTarget)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCover(
                artwork = artwork,
                fallbackTitle = state.bookName,
                fallbackAuthor = state.bookAuthor,
                useNoCoverFallback = state.coverPath.isNullOrBlank(),
                contentDescription = null,
                modifier = Modifier
                    .height(58.dp)
                    .aspectRatio(0.72f),
                cornerRadius = 8.dp,
                compactFallback = true,
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
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { textViewportSize = it },
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
                        style = if (active) activeParagraphStyle else inactiveParagraphStyle,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayerCover(
    artwork: ImageBitmap?,
    fallbackTitle: String,
    fallbackAuthor: String,
    useNoCoverFallback: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    ambientGlow: Boolean = false,
    compactFallback: Boolean = false,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (ambientGlow) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = 1.06f, scaleY = 1.06f)
                        .blur(22.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .alpha(0.32f),
                )
            } else if (useNoCoverFallback) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = 1.06f, scaleY = 1.06f)
                        .background(Color(0xFFD8B58B).copy(alpha = 0.24f), shape)
                        .blur(22.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (ambientGlow) 7.dp else 4.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = 0.26f),
                )
                .clip(shape)
                .background(Color(NgTheme.colors.surfaceContainerLow)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                useNoCoverFallback -> DefaultBookCover(
                    baseArtwork = artwork,
                    title = fallbackTitle,
                    author = fallbackAuthor,
                    compact = compactFallback,
                )
                artwork != null -> Image(
                    bitmap = artwork,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Icon(
                    painter = painterResource(R.drawable.ic_toc),
                    contentDescription = contentDescription,
                    tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.54f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun DefaultBookCover(
    baseArtwork: ImageBitmap?,
    title: String,
    author: String,
    compact: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEADCC2)),
        contentAlignment = Alignment.Center,
    ) {
        if (baseArtwork != null) {
            Image(
                bitmap = baseArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.82f),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.image_cover_default),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.82f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD8B98A).copy(alpha = 0.16f)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 4.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color(0xFF2B251F),
                fontSize = if (compact) 6.sp else 17.sp,
                lineHeight = if (compact) 7.sp else 23.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && author.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 10.dp)
                        .width(42.dp)
                        .height(1.dp)
                        .background(Color(0xFF6F6253).copy(alpha = 0.42f)),
                )
                Text(
                    text = author,
                    color = Color(0xFF4A4036),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class QuickAction(
    @param:DrawableRes val iconRes: Int,
    val label: String,
    val action: ReadAloudPlayerAction,
)

@Composable
private fun PlayerQuickActions(
    state: ReadAloudPlayerUiState,
    onAction: (ReadAloudPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        QuickAction(R.drawable.ic_timer_black_24dp, state.timerLabel, ReadAloudPlayerAction.Timer),
        QuickAction(R.drawable.ic_read_aloud_speed, state.speedLabel, ReadAloudPlayerAction.Speed),
        QuickAction(R.drawable.ic_refresh_black_24dp, "刷新", ReadAloudPlayerAction.Refresh),
        QuickAction(R.drawable.ic_read_aloud_original, "原文", ReadAloudPlayerAction.Original),
        QuickAction(R.drawable.ic_more_horiz, "更多", ReadAloudPlayerAction.More),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        actions.forEach { item ->
            val interactionSource = remember(item.action) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val pressProgress by animateFloatAsState(
                targetValue = if (pressed && NgTheme.usesLiquidGlass) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 720f),
                label = "${item.action}LiquidPress",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onAction(item.action) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PlayerTranslucentSurface(
                    modifier = Modifier
                        .size(34.dp)
                        .playerLiquidPressTransform(pressProgress),
                    shape = CircleShape,
                    role = NgMaterialRole.INTERACTIVE,
                    liquidCornerRadius = 17.dp,
                    containerAlpha = 0.42f,
                    elevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.label,
                            tint = Color(NgTheme.colors.onSurface).copy(alpha = 0.78f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    text = item.label,
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color(NgTheme.colors.onSurface).copy(alpha = 0.74f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ReadAloudProgressSlider(
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
    val primaryHct = Hct.fromInt(NgTheme.colors.primary)
    val controlHue = primaryHct.hue
    val controlChroma = max(primaryHct.chroma, 48.0)
    val primary = Color(Hct.from(controlHue, controlChroma, 50.0).toInt())
    val inactive = Color(Hct.from(controlHue, max(primaryHct.chroma, 40.0), 30.0).toInt())
    val onSurface = Color(NgTheme.colors.onSurface)
    val useLiquidThumb = NgTheme.usesLiquidGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val safeMax = max.coerceAtLeast(1)
    val safeValue = value.coerceIn(0, safeMax)
    val valueFraction = safeValue.toFloat() / safeMax
    val bufferFraction = bufferedValue.coerceIn(safeValue, safeMax).toFloat() / safeMax
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { sliderSize = it }
            .pointerInput(enabled, max) {
                if (!enabled || max <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastEmittedValue = Int.MIN_VALUE
                    fun valueAt(x: Float): Int {
                        val radius = 11.dp.toPx()
                        val width = (size.width - radius * 2f).coerceAtLeast(1f)
                        return (((x - radius) / width).coerceIn(0f, 1f) * max)
                            .roundToInt()
                            .coerceIn(0, max)
                    }
                    fun emitValue(x: Float) {
                        val nextValue = valueAt(x)
                        if (nextValue == lastEmittedValue) return
                        lastEmittedValue = nextValue
                        currentOnValueChange(nextValue)
                    }
                    emitValue(down.position.x)
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) emitValue(change.position.x)
                            change.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    currentOnFinished()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val thumbRadius = 11.dp.toPx()
            val innerRadius = 7.dp.toPx()
            val trackHeight = 6.dp.toPx()
            val trackStart = thumbRadius
            val trackWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
            val top = (size.height - trackHeight) / 2f
            val radius = trackHeight / 2f
            drawRoundRect(
                color = inactive,
                topLeft = Offset(trackStart - radius, top),
                size = Size(trackWidth + trackHeight, trackHeight),
                cornerRadius = CornerRadius(radius),
            )
            drawRoundRect(
                color = primary.copy(alpha = 0.42f),
                topLeft = Offset(trackStart - radius, top),
                size = Size(
                    (trackWidth * bufferFraction + trackHeight).coerceAtLeast(trackHeight),
                    trackHeight,
                ),
                cornerRadius = CornerRadius(radius),
            )
            drawRoundRect(
                color = primary,
                topLeft = Offset(trackStart - radius, top),
                size = Size(
                    (trackWidth * valueFraction + trackHeight).coerceAtLeast(trackHeight),
                    trackHeight,
                ),
                cornerRadius = CornerRadius(radius),
            )
            if (!useLiquidThumb) {
                val thumbX = trackStart + trackWidth * valueFraction
                drawCircle(onSurface, thumbRadius, Offset(thumbX, size.height / 2f))
                drawCircle(primary, innerRadius, Offset(thumbX, size.height / 2f))
                drawCircle(
                    color = primary.copy(alpha = 0.40f),
                    radius = thumbRadius,
                    center = Offset(thumbX, size.height / 2f),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        if (useLiquidThumb && sliderSize.width > 0) {
            val thumbRadiusPx = with(density) { 11.dp.toPx() }
            val trackWidthPx = (sliderSize.width - thumbRadiusPx * 2f).coerceAtLeast(0f)
            val thumbX = thumbRadiusPx + trackWidthPx * valueFraction
            val thumbOffset = with(density) { (thumbX - thumbRadiusPx).toDp() }
            PlayerTranslucentSurface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(22.dp),
                shape = CircleShape,
                role = NgMaterialRole.INTERACTIVE,
                liquidCornerRadius = 11.dp,
                containerAlpha = 0.36f,
                elevation = 1.dp,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(primary, 7.dp.toPx(), center)
                    drawCircle(
                        color = primary.copy(alpha = 0.52f),
                        radius = 10.dp.toPx(),
                        center = center,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlDock(
    state: ReadAloudPlayerUiState,
    onAction: (ReadAloudPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerTranslucentSurface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        role = NgMaterialRole.NAVIGATION,
        liquidCornerRadius = 8.dp,
        containerAlpha = 0.46f,
        elevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerDockSideAction(
                iconRes = R.drawable.ic_read_aloud_mode_settings,
                label = "模式",
                onClick = { onAction(ReadAloudPlayerAction.Mode) },
                modifier = Modifier.weight(1.18f),
            )
            PlayerDockIconAction(
                iconRes = R.drawable.ic_skip_previous,
                contentDescription = "上一章",
                onClick = { onAction(ReadAloudPlayerAction.PreviousChapter) },
                modifier = Modifier.weight(0.88f),
            )
            Box(
                modifier = Modifier
                    .weight(0.94f)
                    .fillMaxHeight()
                    .clickable {
                        onAction(ReadAloudPlayerAction.TogglePlay)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isPreparing) {
                    ListeningLoadingBars()
                } else {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.ic_pause_24dp
                            else R.drawable.ic_play_24dp
                        ),
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color(NgTheme.colors.onSurface),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            PlayerDockIconAction(
                iconRes = R.drawable.ic_skip_next,
                contentDescription = "下一章",
                onClick = { onAction(ReadAloudPlayerAction.NextChapter) },
                modifier = Modifier.weight(0.88f),
            )
            PlayerDockSideAction(
                iconRes = R.drawable.ic_toc,
                label = "目录",
                onClick = { onAction(ReadAloudPlayerAction.Catalog) },
                modifier = Modifier.weight(1.18f),
            )
        }
    }
}

@Composable
private fun PlayerDockSideAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(y = (-1).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color(NgTheme.colors.onSurface).copy(alpha = 0.78f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = Color(NgTheme.colors.onSurface).copy(alpha = 0.74f),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlayerDockIconAction(
    @DrawableRes iconRes: Int,
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
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun ListeningLoadingBars() {
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
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlayerTranslucentSurface(
            modifier = Modifier
                .height(32.dp),
            shape = RoundedCornerShape(8.dp),
            role = NgMaterialRole.INTERACTIVE,
            liquidCornerRadius = 8.dp,
            containerAlpha = 0.42f,
            elevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 190.dp)
                    .height(32.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bottom_person_e),
                        contentDescription = null,
                        tint = Color(NgTheme.colors.onSurface).copy(alpha = 0.74f),
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    modifier = Modifier.widthIn(max = 150.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun Modifier.playerLiquidPressTransform(progress: Float): Modifier = graphicsLayer {
    scaleX = 1f + progress * 0.08f
    scaleY = 1f - progress * 0.07f
}

@Composable
internal fun PlayerTranslucentSurface(
    modifier: Modifier,
    shape: Shape,
    containerAlpha: Float,
    elevation: androidx.compose.ui.unit.Dp,
    role: NgMaterialRole = NgMaterialRole.SOFT_SURFACE,
    liquidCornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val colors = NgTheme.colors
    val liquidBackdrop = currentNgLiquidGlassBackdrop()
    val supportsLiquidControl = role == NgMaterialRole.NAVIGATION ||
        role == NgMaterialRole.INTERACTIVE
    if (
        supportsLiquidControl &&
        NgTheme.usesLiquidGlass &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        liquidBackdrop != null
    ) {
        val baseStyle = NgGlassDefaults.floatingStyle()
        val snapshot = NgTheme.snapshot
        val surfaceColor = Color(colors.surfaceContainerLow)
        val primary = Color(colors.primary)
        val clearSurface = lerp(
            surfaceColor,
            Color.White,
            if (snapshot.isDark) 0.05f else 0.025f,
        )
        val clearTint = lerp(clearSurface, primary, 0.025f)
        val liquidStyle = remember(
            baseStyle,
            clearSurface,
            clearTint,
            primary,
            snapshot.isDark,
            containerAlpha,
        ) {
            baseStyle.copy(
                containerTop = clearTint.copy(
                    alpha = (containerAlpha + 0.02f).coerceAtMost(1f)
                ),
                containerBottom = clearSurface.copy(alpha = containerAlpha),
                accentGlow = primary.copy(alpha = 0.04f),
                edgeHighlight = Color.White.copy(
                    alpha = if (snapshot.isDark) 0.38f else 0.32f,
                ),
                surfaceGloss = Color.White.copy(
                    alpha = if (snapshot.isDark) 0.07f else 0.05f,
                ),
                depthEdge = Color.Black.copy(alpha = 0.08f),
                contentColor = Color(colors.onSurface),
                shadowElevation = 0.dp,
            )
        }
        val liquidElevation = (elevation * 0.35f).coerceAtLeast(1.dp)
        NgVisualSurface(
            modifier = modifier.shadow(
                elevation = liquidElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            ),
            role = role,
            cornerRadius = liquidCornerRadius,
            shape = shape,
            style = liquidStyle,
            liquidBackdrop = liquidBackdrop,
        ) {
            content()
        }
        return
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(colors.surfaceContainerLow).copy(
                            alpha = (containerAlpha + 0.03f).coerceAtMost(1f)
                        ),
                        Color(colors.surfaceContainerLow).copy(alpha = containerAlpha),
                    )
                )
            ),
    ) {
        content()
    }
}
