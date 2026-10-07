package io.legado.app.ui.main.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.read.aloud.ListeningLoadingBars
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeListeningWidget(variant: HomeWidgetVariant, context: HomeWidgetContentContext) {
    HomeListeningUnifiedContent(
        widgetId = "home-listening",
        variant = variant,
        state = context.listening,
        editing = context.editing,
        onAction = { _, action -> context.onListeningAction(context.listening, action) },
        interactive = context.interactive,
        onEdit = context.onEdit,
        measuring = context.measuring,
        bodyMinimumHeight = context.bodyMinimumHeight,
    )
}

@Composable
private fun HomeListeningSmall(context: HomeWidgetContentContext, showPreparing: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(colorResource(R.color.ng_surface_panel).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
    ) {
        if (context.listening.book == null) {
            HomeListeningEmpty(false)
        } else {
            HomeListeningMetadata(context, showPreparing, Modifier.fillMaxWidth(), compact = true)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val contentWidth = maxWidth
                if (contentWidth >= 104.dp) {
                    val columnWidth = (contentWidth - 8.dp) / 2
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HomeListeningActionButton(
                            context, HomeListeningAction.TOGGLE, showPreparing, columnWidth,
                            captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true,
                        )
                        HomeListeningActionButton(
                            context, HomeListeningAction.OPEN, false, columnWidth,
                            captionMaxLines = Int.MAX_VALUE,
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HomeListeningActionButton(
                            context, HomeListeningAction.TOGGLE, showPreparing, contentWidth,
                            captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true,
                        )
                        HomeListeningActionButton(
                            context, HomeListeningAction.OPEN, false, contentWidth,
                            captionMaxLines = Int.MAX_VALUE,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeListeningLarge(context: HomeWidgetContentContext, showPreparing: Boolean) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val controlCenterPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
            .background(colorResource(R.color.ng_surface_panel).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 12.dp),
    ) {
        if (context.listening.book == null) {
            HomeListeningEmpty(true)
        } else {
            val secondaryWidth = (64.dp * fontScale).coerceAtMost(maxWidth - 56.dp).coerceAtLeast(48.dp)
            // Equal caption columns retain the previous total action width and text space.
            val columnWidth = (secondaryWidth + 48.dp) / 2
            val controlsWidth = 8.dp + columnWidth * 2
            // Keep the reference's side-by-side composition when the text has room to breathe.
            val inline = maxWidth - 64.dp - 14.dp - 12.dp - controlsWidth >= 84.dp * fontScale
            if (inline) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HomeListeningMetadata(
                        context, showPreparing, Modifier.weight(1f).alignBy { it.measuredHeight / 2 },
                    )
                    Spacer(Modifier.width(12.dp))
                    HomeListeningLargeActions(
                        context, showPreparing, columnWidth, Modifier.alignBy { controlCenterPx },
                        showPrimaryCaption = true, primaryWidth = columnWidth,
                        captionMaxLines = Int.MAX_VALUE,
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    HomeListeningMetadata(context, showPreparing, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    HomeListeningLargeActions(
                        context, showPreparing, columnWidth, Modifier.align(Alignment.End),
                        showPrimaryCaption = true, primaryWidth = columnWidth,
                        captionMaxLines = Int.MAX_VALUE,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeListeningMetadata(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    modifier: Modifier,
    compact: Boolean = false,
    palette: HomeListeningPalette? = null,
    stacked: Boolean = false,
    coverWidth: Dp? = null,
    textScale: Float = 1f,
    spacing: Dp = 6.dp,
) {
    val state = context.listening
    if (state.book == null) return
    val openModifier = if (context.editing || !context.interactive) Modifier else Modifier.combinedClickable(
        role = Role.Button,
        onClickLabel = stringResource(R.string.home_listening_open_player),
        onClick = { context.onListeningAction(state, HomeListeningAction.OPEN) },
        onLongClickLabel = stringResource(R.string.home_edit),
        onLongClick = context.onEdit,
    )
    val cover: @Composable () -> Unit = {
        HomeListeningCover(
            context = context, width = coverWidth ?: if (compact) 44.dp else 64.dp,
        )
    }
    val text: @Composable (Modifier) -> Unit = { textModifier ->
        HomeListeningText(
            context, showPreparing, textModifier, compact, palette,
            textScale = textScale, spacing = spacing, centered = stacked,
        )
    }
    if (stacked) {
        Column(modifier.then(openModifier), horizontalAlignment = Alignment.CenterHorizontally) {
            cover()
            Spacer(Modifier.height(10.dp))
            text(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.then(openModifier), verticalAlignment = Alignment.CenterVertically) {
            cover()
            Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
            text(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeListeningCover(
    context: HomeWidgetContentContext,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    interactive: Boolean = false,
) {
    val state = context.listening
    val book = state.book ?: return
    if (context.measuring) {
        Spacer(modifier.width(width).aspectRatio(3f / 4f))
        return
    }
    val openModifier = if (!interactive || context.editing || !context.interactive) Modifier else Modifier.combinedClickable(
        role = Role.Button,
        onClickLabel = stringResource(R.string.home_listening_open_player),
        onClick = { context.onListeningAction(state, HomeListeningAction.OPEN) },
        onLongClickLabel = stringResource(R.string.home_edit),
        onLongClick = context.onEdit,
    )
    NgBookCover(
        book = book,
        coverRadius = 6,
        coverAspectRatio = 3f / 4f,
        revision = state.cover.hashCode() + 31 * state.bookName.hashCode() + state.author.hashCode(),
        modifier = modifier.width(width).aspectRatio(3f / 4f).then(openModifier),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeListeningText(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    palette: HomeListeningPalette? = null,
    textScale: Float = 1f,
    spacing: Dp = 6.dp,
    centered: Boolean = false,
    interactive: Boolean = false,
    reference: HomeListeningReference? = null,
) {
    val state = context.listening
    if (state.book == null) return
    val foreground = palette?.foreground ?: Color(NgTheme.colors.onSurface)
    val secondary = palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant)
    val primary = palette?.primary ?: Color(NgTheme.colors.primary)
    val openModifier = if (!interactive || context.editing || !context.interactive) Modifier else Modifier.combinedClickable(
        role = Role.Button,
        onClickLabel = stringResource(R.string.home_listening_open_player),
        onClick = { context.onListeningAction(state, HomeListeningAction.OPEN) },
        onLongClickLabel = stringResource(R.string.home_edit),
        onLongClick = context.onEdit,
    )
    Column(modifier.then(openModifier), verticalArrangement = Arrangement.spacedBy(if (reference == null) spacing else 0.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            state.bookName, color = foreground,
            fontWeight = if (reference == null) FontWeight.SemiBold else FontWeight.Bold,
            fontSize = reference?.bookSize?.sp ?: (if (compact) 15.sp else 17.sp) * textScale,
            lineHeight = reference?.bookLine?.sp ?: (if (compact) 20.sp else 23.sp) * textScale,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (reference != null) Spacer(Modifier.height(reference.chapterGap.dp))
        Text(
            state.chapterTitle.ifBlank { stringResource(R.string.home_listening_chapter_unavailable) },
            color = secondary, fontSize = reference?.chapterSize?.sp ?: (if (compact) 12.sp else 13.sp) * textScale,
            lineHeight = reference?.chapterLine?.sp ?: 18.sp * textScale,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (reference != null) Spacer(Modifier.height(reference.statusGap.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusColor = if (state.playing || showPreparing) primary else secondary
            Icon(
                painterResource(R.drawable.ic_ai_capability_tts), null,
                tint = statusColor, modifier = Modifier.size(reference?.statusIcon?.dp ?: 14.dp),
            )
            Spacer(Modifier.width(if (reference == null) 4.dp else 3.dp))
            Text(
                stringResource(when {
                    showPreparing -> R.string.home_listening_preparing
                    state.playing -> R.string.home_listening_playing
                    state.history != null -> R.string.home_listening_last_playback
                    else -> R.string.home_listening_paused
                }),
                color = statusColor, fontSize = reference?.statusSize?.sp ?: 12.sp * textScale,
                lineHeight = reference?.statusLine?.sp ?: 18.sp * textScale,
                maxLines = if (compact) 3 else 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeListeningLargeActions(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    secondaryWidth: Dp,
    modifier: Modifier = Modifier,
    showPrimaryCaption: Boolean = false,
    palette: HomeListeningPalette? = null,
    primaryWidth: Dp = 48.dp,
    captionMaxLines: Int = 2,
) {
    Row(modifier.width(primaryWidth + 8.dp + secondaryWidth), verticalAlignment = Alignment.Top) {
        HomeListeningActionButton(context, HomeListeningAction.TOGGLE, showPreparing, primaryWidth,
            captionMaxLines = captionMaxLines, showPrimaryCaption = showPrimaryCaption, palette = palette)
        Spacer(Modifier.width(8.dp))
        HomeListeningActionButton(context, HomeListeningAction.OPEN, false, secondaryWidth,
            captionMaxLines = captionMaxLines, palette = palette)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeListeningActionButton(
    context: HomeWidgetContentContext,
    action: HomeListeningAction,
    showPreparing: Boolean,
    width: Dp,
    captionMaxLines: Int = 2,
    showPrimaryCaption: Boolean = false,
    palette: HomeListeningPalette? = null,
    compactCaption: Boolean = false,
    visualSize: Dp? = null,
    captionOverride: String? = null,
    reference: HomeListeningReference? = null,
    discStyleId: String? = null,
    captionReference: HomeListeningReference? = null,
) {
    val primary = action == HomeListeningAction.TOGGLE
    val captionStyle = captionReference ?: reference
    val refinedMaterial = reference?.styleId == "storybook" || reference?.styleId == "night"
    val enabled = !primary || !context.listening.preparing
    val colors = NgTheme.colors
    // Match NG's PRIMARY_LIGHT_CONTENT treatment without changing the user's global text colors.
    val foreground = (if (primary) palette?.primaryContent ?: Color.White
        else palette?.secondaryContent ?: Color(colors.onSurface))
        .copy(alpha = if (context.editing) 0.4f else 1f)
    val baseBackground = if (primary) palette?.primary ?: Color(colors.primary)
        else palette?.secondaryContainer ?: colorResource(R.color.ng_surface_panel).copy(alpha = 0.55f)
    val background = baseBackground.copy(alpha = baseBackground.alpha * if (context.editing) 0.4f else 1f)
    val label = stringResource(when {
        !primary -> R.string.home_listening_open_player
        context.listening.preparing -> R.string.home_listening_preparing
        context.listening.playing -> R.string.pause
        context.listening.history != null -> R.string.home_listening_resume
        else -> R.string.audio_play
    })
    val caption = if (primary && context.listening.preparing && !showPreparing) stringResource(when {
        context.listening.playing -> R.string.pause
        context.listening.history != null -> R.string.home_listening_resume
        else -> R.string.audio_play
    }) else label
    val clickable = if (context.editing || !context.interactive) Modifier else Modifier.combinedClickable(
        enabled = enabled, role = Role.Button, onClickLabel = label,
        onClick = { context.onListeningAction(context.listening, action) },
        onLongClickLabel = stringResource(R.string.home_edit), onLongClick = context.onEdit,
    )
    Column(
        modifier = Modifier.width(width).then(if (reference != null) Modifier else
            Modifier.clip(if (primary && !showPrimaryCaption) CircleShape else RoundedCornerShape(12.dp)))
            .then(clickable).semantics {
            if (primary && !showPrimaryCaption && !context.editing && context.interactive) contentDescription = label
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val discSize = (if (reference != null) (if (primary) reference.primarySize else reference.secondarySize).dp
                else visualSize ?: if (primary) 44.dp else 40.dp).coerceAtMost(48.dp)
            Box(
                if (reference != null || discStyleId != null) Modifier.size(discSize)
                else Modifier.size(discSize).clip(CircleShape).background(background),
                contentAlignment = Alignment.Center,
            ) {
                if (reference != null) HomeListeningReferenceDisc(
                    reference, primary, context.editing, Modifier.matchParentSize(),
                )
                else if (discStyleId != null) HomeWidgetControlDisc(
                    discStyleId, primary, context.editing, Modifier.matchParentSize(),
                )
                if (primary && showPreparing) {
                    if (context.editing || !context.interactive) {
                        Icon(painterResource(R.drawable.ic_read_aloud_loading_bars), null,
                            tint = foreground, modifier = Modifier.size(28.dp))
                    } else ListeningLoadingBars(contentColor = foreground)
                } else if (primary) {
                    if (reference != null) HomeListeningReferencePlayGlyph(
                        context.listening.playing, foreground, Modifier.size(if (reference.width > 200) 26.dp else 24.dp),
                    ) else Icon(
                        painterResource(if (context.listening.playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp),
                        contentDescription = null,
                        tint = foreground, modifier = Modifier.size(if (compactCaption) 24.dp else 28.dp),
                    )
                } else {
                    Icon(if (refinedMaterial) Icons.AutoMirrored.Rounded.OpenInNew
                        else Icons.AutoMirrored.Outlined.OpenInNew, null, tint = foreground,
                        modifier = Modifier.size(if (reference != null && !refinedMaterial) 24.dp else 20.dp))
                }
            }
        }
        if (!primary || showPrimaryCaption) {
            Spacer(Modifier.height(captionStyle?.captionGap?.dp ?: if (compactCaption) 4.dp else 6.dp))
            Text(
                captionOverride ?: caption, color = (if (primary || refinedMaterial) palette?.foreground else palette?.secondary)
                    ?: Color(colors.onSurfaceVariant), fontSize = captionStyle?.captionSize?.sp ?: 11.sp,
                lineHeight = captionStyle?.captionLine?.sp ?: if (compactCaption) 12.sp else 14.sp,
                fontWeight = if (captionStyle != null) FontWeight.SemiBold else null,
                textAlign = TextAlign.Center, maxLines = captionMaxLines, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeListeningEmpty(large: Boolean, palette: HomeListeningPalette? = null) {
    val foreground = palette?.foreground ?: Color(NgTheme.colors.onSurface)
    val secondary = palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant)
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (large) 96.dp else 148.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.home_listening_empty), color = foreground,
            fontSize = if (large) 15.sp else 14.sp, lineHeight = if (large) 22.sp else 20.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(if (large) R.string.home_listening_start_large else R.string.home_listening_start_small),
            color = secondary, fontSize = if (large) 13.sp else 12.sp,
            lineHeight = if (large) 20.sp else 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
