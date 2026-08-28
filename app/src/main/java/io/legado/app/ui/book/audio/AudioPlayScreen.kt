package io.legado.app.ui.book.audio

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.dirror.lyricviewx.LyricViewX
import com.dirror.lyricviewx.OnPlayClickListener
import io.legado.app.R
import io.legado.app.model.AudioPlay
import io.legado.app.ui.book.listen.ListeningCoverArtwork
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.book.read.aloud.ListeningLoadingBars
import io.legado.app.ui.book.read.aloud.ListeningPlayerBackground
import io.legado.app.ui.book.read.aloud.PlayerCover
import io.legado.app.ui.book.read.aloud.PlayerTranslucentSurface
import io.legado.app.ui.book.read.aloud.ReadAloudProgressSlider
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toDurationTime

internal enum class AudioPlayerPage {
    COVER,
    LYRICS,
}

internal data class AudioPlayerUiState(
    val bookName: String = "有声书",
    val bookAuthor: String = "",
    val bookUrl: String = "",
    val chapterTitle: String = "正在准备播放",
    val coverPath: String? = null,
    val sourceOrigin: String? = null,
    val sourceLabel: String = "有声书播放",
    val lyric: String? = null,
    val page: AudioPlayerPage = AudioPlayerPage.COVER,
    val duration: Int = 0,
    val position: Int = 0,
    val bufferedPosition: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val timerLabel: String = "",
    val speedLabel: String = "1.0x",
    val playMode: AudioPlay.PlayMode = AudioPlay.PlayMode.LIST_END_STOP,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val showExitConfirmation: Boolean = false,
)

internal sealed interface AudioPlayerAction {
    data object Close : AudioPlayerAction
    data object OpenBookInfo : AudioPlayerAction
    data object Timer : AudioPlayerAction
    data object Speed : AudioPlayerAction
    data object SkipCredits : AudioPlayerAction
    data object ChangeSource : AudioPlayerAction
    data object More : AudioPlayerAction
    data object ChangePlayMode : AudioPlayerAction
    data object Previous : AudioPlayerAction
    data object TogglePlay : AudioPlayerAction
    data object Stop : AudioPlayerAction
    data object Next : AudioPlayerAction
    data object Catalog : AudioPlayerAction
    data class SelectPage(val page: AudioPlayerPage) : AudioPlayerAction
    data class SeekPreview(val position: Int) : AudioPlayerAction
    data object SeekFinished : AudioPlayerAction
    data class SeekFromLyric(val position: Int) : AudioPlayerAction
    data object ExitDialogDismiss : AudioPlayerAction
    data object AddToShelf : AudioPlayerAction
    data object DiscardAndExit : AudioPlayerAction
}

@Composable
internal fun AudioPlayScreen(
    state: AudioPlayerUiState,
    onAction: (AudioPlayerAction) -> Unit,
) {
    val context = LocalContext.current
    val artwork by ListeningCoverArtwork.remember(
        context = context,
        cacheKey = state.bookUrl.ifBlank { state.coverPath.orEmpty() },
        path = state.coverPath,
        sourceOrigin = state.sourceOrigin,
    )
    val hasLyrics = !state.lyric.isNullOrBlank()
    Box(modifier = Modifier.fillMaxSize()) {
        ListeningPlayerBackground(
            artwork = artwork,
            useNoCoverFallback = state.coverPath.isNullOrBlank(),
        )
        AudioPlayerContent(
            state = state,
            artwork = artwork,
            hasLyrics = hasLyrics,
            onAction = onAction,
        )
        AudioPlayerTopBar(
            selectedPage = state.page,
            showPageIndicator = hasLyrics,
            onClose = { onAction(AudioPlayerAction.Close) },
        )
        if (state.showExitConfirmation) {
            AudioExitConfirmationDialog(
                bookName = state.bookName,
                onDismiss = { onAction(AudioPlayerAction.ExitDialogDismiss) },
                onAddToShelf = { onAction(AudioPlayerAction.AddToShelf) },
                onDiscard = { onAction(AudioPlayerAction.DiscardAndExit) },
            )
        }
    }
}

@Composable
private fun AudioPlayerContent(
    state: AudioPlayerUiState,
    artwork: ImageBitmap?,
    hasLyrics: Boolean,
    onAction: (AudioPlayerAction) -> Unit,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.page, hasLyrics) {
                if (!hasLyrics) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                    onDragCancel = { horizontalDrag = 0f },
                    onDragEnd = {
                        when {
                            horizontalDrag < -64.dp.toPx() -> {
                                onAction(AudioPlayerAction.SelectPage(AudioPlayerPage.LYRICS))
                            }
                            horizontalDrag > 64.dp.toPx() -> {
                                onAction(AudioPlayerAction.SelectPage(AudioPlayerPage.COVER))
                            }
                        }
                        horizontalDrag = 0f
                    },
                )
            },
    ) {
        Crossfade(
            targetState = if (hasLyrics) state.page else AudioPlayerPage.COVER,
            animationSpec = tween(durationMillis = 260),
            label = "audioPlayerPage",
        ) { page ->
            when (page) {
                AudioPlayerPage.COVER -> AudioCoverPage(state, artwork, onAction)
                AudioPlayerPage.LYRICS -> AudioLyricsPage(state, artwork, onAction)
            }
        }
    }
}

@Composable
private fun AudioPlayerTopBar(
    selectedPage: AudioPlayerPage,
    showPageIndicator: Boolean,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 32.dp)
                .size(38.dp),
        ) {
            PlayerTranslucentSurface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                containerAlpha = 0.54f,
                elevation = 3.dp,
            ) {}
            IconButton(
                onClick = onClose,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_read_aloud_chevron_down),
                    contentDescription = stringResource(R.string.close),
                    tint = Color(NgTheme.colors.onSurface),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (showPageIndicator) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AudioPlayerPage.entries.forEach { page ->
                    val selected = page == selectedPage
                    Box(
                        modifier = Modifier
                            .width(if (selected) 24.dp else 6.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                Color(NgTheme.colors.onSurface).copy(
                                    alpha = if (selected) 0.82f else 0.28f,
                                )
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioCoverPage(
    state: AudioPlayerUiState,
    artwork: ImageBitmap?,
    onAction: (AudioPlayerAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 72.dp, end = 28.dp, bottom = 38.dp),
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
                .size(width = 142.dp, height = 202.dp)
                .clickable { onAction(AudioPlayerAction.OpenBookInfo) },
            cornerRadius = 10.dp,
            ambientGlow = true,
        )
        Text(
            text = state.bookName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AudioInfoCard(state, Modifier.padding(top = 10.dp))
        AudioQuickActions(state, onAction, Modifier.padding(top = 8.dp))
        AudioProgress(state, onAction, Modifier.padding(top = 2.dp))
        AudioControlDock(
            state = state,
            onAction = onAction,
            modifier = Modifier.offset(y = (-3).dp),
        )
        AudioSourcePill(
            label = state.sourceLabel,
            onClick = { onAction(AudioPlayerAction.ChangeSource) },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AudioLyricsPage(
    state: AudioPlayerUiState,
    artwork: ImageBitmap?,
    onAction: (AudioPlayerAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 76.dp, end = 24.dp, bottom = 40.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCover(
                artwork = artwork,
                fallbackTitle = state.bookName,
                fallbackAuthor = state.bookAuthor,
                useNoCoverFallback = state.coverPath.isNullOrBlank(),
                contentDescription = state.bookName,
                modifier = Modifier.size(width = 50.dp, height = 70.dp),
                cornerRadius = 6.dp,
                compactFallback = true,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = state.bookName,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.chapterTitle,
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PlayerTranslucentSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 14.dp),
            shape = RoundedCornerShape(12.dp),
            containerAlpha = 0.32f,
            elevation = 3.dp,
        ) {
            AudioLyricView(
                lyric = state.lyric.orEmpty(),
                position = state.position,
                onSeek = { onAction(AudioPlayerAction.SeekFromLyric(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AudioProgress(state, onAction, Modifier.padding(top = 4.dp))
        AudioControlDock(state, onAction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioInfoCard(state: AudioPlayerUiState, modifier: Modifier = Modifier) {
    PlayerTranslucentSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(10.dp),
        containerAlpha = 0.38f,
        elevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.bookAuthor.isNotBlank()) {
                Text(
                    text = state.bookAuthor,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = state.chapterTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private data class AudioQuickAction(
    @param:DrawableRes val iconRes: Int,
    val label: String,
    val action: AudioPlayerAction,
)

@Composable
private fun AudioQuickActions(
    state: AudioPlayerUiState,
    onAction: (AudioPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        AudioQuickAction(
            R.drawable.ic_timer_black_24dp,
            state.timerLabel.ifBlank { stringResource(R.string.audio_player_timer) },
            AudioPlayerAction.Timer,
        ),
        AudioQuickAction(
            R.drawable.ic_read_aloud_speed,
            state.speedLabel,
            AudioPlayerAction.Speed,
        ),
        AudioQuickAction(
            R.drawable.ic_audio_skip_intro,
            stringResource(R.string.audio_player_skip_opening),
            AudioPlayerAction.SkipCredits,
        ),
        AudioQuickAction(
            R.drawable.ic_more_horiz,
            stringResource(R.string.more),
            AudioPlayerAction.More,
        ),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        actions.forEach { item ->
            val interactionSource = remember(item.action) { MutableInteractionSource() }
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
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
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
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Text(
                    text = item.label,
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color(NgTheme.colors.onSurface).copy(alpha = 0.74f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AudioProgress(
    state: AudioPlayerUiState,
    onAction: (AudioPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.position.toDurationTime(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
            )
            Text(
                text = state.duration.toDurationTime(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
            )
        }
        ReadAloudProgressSlider(
            value = state.position,
            bufferedValue = state.bufferedPosition,
            max = state.duration,
            enabled = state.duration > 0,
            onValueChange = { onAction(AudioPlayerAction.SeekPreview(it)) },
            onValueChangeFinished = { onAction(AudioPlayerAction.SeekFinished) },
            modifier = Modifier.offset(y = (-5).dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioControlDock(
    state: AudioPlayerUiState,
    onAction: (AudioPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerTranslucentSurface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        containerAlpha = 0.46f,
        elevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioDockAction(
                iconRes = state.playMode.iconRes,
                label = state.playMode.displayName(),
                onClick = { onAction(AudioPlayerAction.ChangePlayMode) },
                modifier = Modifier.weight(1.18f),
            )
            AudioDockIconAction(
                iconRes = R.drawable.ic_skip_previous,
                description = stringResource(R.string.previous_chapter),
                enabled = state.canPrevious,
                onClick = { onAction(AudioPlayerAction.Previous) },
                modifier = Modifier.weight(0.88f),
            )
            Box(
                modifier = Modifier
                    .weight(0.94f)
                    .fillMaxHeight()
                    .combinedClickable(
                        enabled = !state.isLoading,
                        onClick = { onAction(AudioPlayerAction.TogglePlay) },
                        onLongClick = { onAction(AudioPlayerAction.Stop) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    ListeningLoadingBars()
                } else {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.ic_pause_24dp
                            else R.drawable.ic_play_24dp
                        ),
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.pause else R.string.audio_play
                        ),
                        tint = Color(NgTheme.colors.onSurface),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            AudioDockIconAction(
                iconRes = R.drawable.ic_skip_next,
                description = stringResource(R.string.next_chapter),
                enabled = state.canNext,
                onClick = { onAction(AudioPlayerAction.Next) },
                modifier = Modifier.weight(0.88f),
            )
            AudioDockAction(
                iconRes = R.drawable.ic_toc,
                label = stringResource(R.string.audio_player_catalog),
                onClick = { onAction(AudioPlayerAction.Catalog) },
                modifier = Modifier.weight(1.18f),
            )
        }
    }
}

@Composable
private fun AudioDockAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = Color(NgTheme.colors.onSurface).copy(alpha = 0.80f),
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            color = Color(NgTheme.colors.onSurface).copy(alpha = 0.78f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioDockIconAction(
    @DrawableRes iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.34f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AudioSourcePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        PlayerTranslucentSurface(
            modifier = Modifier
                .height(30.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            containerAlpha = 0.42f,
            elevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 220.dp)
                    .height(30.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_exchange),
                    contentDescription = stringResource(R.string.change_origin),
                    tint = Color(NgTheme.colors.onSurface).copy(alpha = 0.76f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 6.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AudioLyricView(
    lyric: String,
    position: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val accent = NgTheme.colors.primary
    AndroidView(
        factory = { context ->
            LyricViewX(context).apply {
                setNormalTextSize(50f)
                setCurrentTextSize(60f)
                setTimelineTextColor(accent)
                setDraggable(true, object : OnPlayClickListener {
                    override fun onPlayClick(time: Long): Boolean {
                        currentOnSeek(time.toInt())
                        return true
                    }
                })
            }
        },
        update = { view ->
            if (view.tag != lyric) {
                view.tag = lyric
                view.loadLyric(lyric)
            }
            view.setTimelineTextColor(accent)
            view.updateTime(position.toLong(), false)
        },
        modifier = modifier,
    )
}

@Composable
private fun AudioExitConfirmationDialog(
    bookName: String,
    onDismiss: () -> Unit,
    onAddToShelf: () -> Unit,
    onDiscard: () -> Unit,
) {
    val playerSnapshot = NgTheme.snapshot
    val dialogSnapshot = remember(playerSnapshot) {
        ListeningCoverTheme.drawerSnapshot(playerSnapshot)
    }
    NgAppTheme(snapshot = dialogSnapshot, updateSystemBars = false) {
        Dialog(onDismissRequest = onDismiss) {
            NgDialog(
                title = stringResource(R.string.add_to_bookshelf),
                variant = NgDialogVariant.CONFIRMATION,
                actions = {
                    NgButton(
                        onClick = onDiscard,
                        modifier = Modifier
                            .width(84.dp)
                            .height(42.dp),
                        variant = NgButtonVariant.OUTLINE,
                    ) {
                        Text(stringResource(R.string.no), fontSize = 15.sp)
                    }
                    NgButton(
                        onClick = onAddToShelf,
                        modifier = Modifier
                            .width(84.dp)
                            .height(42.dp),
                    ) {
                        Text(stringResource(R.string.ok), fontSize = 15.sp)
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.check_add_bookshelf, bookName),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun AudioPlay.PlayMode.displayName(): String = when (this) {
    AudioPlay.PlayMode.LIST_END_STOP -> stringResource(R.string.audio_play_mode_list_end_stop)
    AudioPlay.PlayMode.SINGLE_LOOP -> stringResource(R.string.audio_play_mode_single_loop)
    AudioPlay.PlayMode.RANDOM -> stringResource(R.string.audio_play_mode_random)
    AudioPlay.PlayMode.LIST_LOOP -> stringResource(R.string.audio_play_mode_list_loop)
}
