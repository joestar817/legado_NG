package io.legado.app.ui.config

import androidx.annotation.ColorRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.launch

@Immutable
internal data class TtsEngineVoiceListItemUiModel(
    val id: String,
    val previewKey: String?,
    val name: String,
    val genderLabel: String?,
    val languageLabels: List<String>,
    val style: String?,
    val tags: List<String>,
    val checked: Boolean,
    val dimmed: Boolean,
)

@Immutable
internal data class TtsEngineVoiceListScreenState(
    val items: List<TtsEngineVoiceListItemUiModel> = emptyList(),
    val preview: TtsVoicePreviewStatus = TtsVoicePreviewStatus(
        key = null,
        state = TtsVoicePreviewState.IDLE,
    ),
)

internal sealed interface TtsEngineVoiceListAction {
    data class EnabledChanged(
        val voiceId: String,
        val checked: Boolean,
    ) : TtsEngineVoiceListAction

    data class Preview(val voiceId: String) : TtsEngineVoiceListAction
    data class PreviewStyle(val voiceId: String) : TtsEngineVoiceListAction
}

@Composable
internal fun TtsEngineVoiceListScreen(
    state: TtsEngineVoiceListScreenState,
    onAction: (TtsEngineVoiceListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = state.items,
            key = TtsEngineVoiceListItemUiModel::id,
            contentType = { "tts_voice" },
        ) { item ->
            TtsEngineVoiceListCard(
                item = item,
                previewState = state.preview.takeIf { it.key == item.previewKey }
                    ?.state
                    ?: TtsVoicePreviewState.IDLE,
                onCheckedChange = {
                    onAction(TtsEngineVoiceListAction.EnabledChanged(item.id, it))
                },
                onPreview = { onAction(TtsEngineVoiceListAction.Preview(item.id)) },
                onPreviewStyle = {
                    onAction(TtsEngineVoiceListAction.PreviewStyle(item.id))
                },
            )
        }
    }
}

@Composable
private fun TtsEngineVoiceListCard(
    item: TtsEngineVoiceListItemUiModel,
    previewState: TtsVoicePreviewState,
    onCheckedChange: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onPreviewStyle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .alpha(if (item.dimmed) 0.48f else 1f)
            .clip(shape)
            .background(
                colorResource(
                    if (pressed) R.color.ng_surface_pressed else R.color.ng_surface_card
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {},
            )
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                when (item.genderLabel) {
                    "男" -> TtsEngineVoiceGenderIcon(
                        iconRes = R.drawable.ic_tts_gender_male,
                        colorRes = R.color.ng_tts_gender_male,
                    )

                    "女" -> TtsEngineVoiceGenderIcon(
                        iconRes = R.drawable.ic_tts_gender_female,
                        colorRes = R.color.ng_tts_gender_female,
                    )
                }
                item.languageLabels.forEach { label ->
                    TtsEngineVoiceLanguageTag(label)
                }
                item.style?.takeIf(String::isNotBlank)?.let { style ->
                    TtsEngineVoiceTag(
                        text = style,
                        contentColorRes = R.color.ng_tts_tag_blue,
                        containerColorRes = R.color.ng_tts_tag_blue_container,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.tags.forEachIndexed { index, tag ->
                        val colors = ttsEngineVoiceTagPalette(index)
                        TtsEngineVoiceTag(
                            text = tag,
                            contentColorRes = colors.first,
                            containerColorRes = colors.second,
                        )
                    }
                }
            }
        }
        NgSwitchControl(
            checked = item.checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        )
        TtsEngineVoicePreviewButton(
            state = previewState,
            onClick = onPreview,
            onLongClick = onPreviewStyle,
        )
    }
}

@Composable
private fun TtsEngineVoiceGenderIcon(
    iconRes: Int,
    @ColorRes colorRes: Int,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = colorResource(colorRes),
        modifier = Modifier
            .padding(start = 8.dp)
            .size(18.dp),
    )
}

@Composable
private fun TtsEngineVoiceLanguageTag(text: String) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(width = if (text.length <= 1) 24.dp else 34.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(R.color.ng_tts_language_container)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colorResource(R.color.ng_tts_language),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TtsEngineVoiceTag(
    text: String,
    @ColorRes contentColorRes: Int,
    @ColorRes containerColorRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .widthIn(max = 116.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(containerColorRes))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colorResource(contentColorRes),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TtsEngineVoicePreviewButton(
    state: TtsVoicePreviewState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val playFeedback = {
        scope.launch {
            scale.snapTo(0.88f)
            scale.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    playFeedback()
                    onClick()
                },
                onLongClick = {
                    playFeedback()
                    onLongClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tts_headphones),
            contentDescription = "朗读",
            tint = if (state == TtsVoicePreviewState.PLAYING) {
                Color(NgTheme.colors.primary)
            } else {
                Color(NgTheme.colors.onSurface)
            },
            modifier = Modifier
                .size(22.dp)
                .alpha(if (state == TtsVoicePreviewState.LOADING) 0.55f else 1f),
        )
        if (state != TtsVoicePreviewState.IDLE) {
            TtsEngineVoicePreviewIndicator(state)
        }
    }
}

@Composable
private fun TtsEngineVoicePreviewIndicator(state: TtsVoicePreviewState) {
    val transition = rememberInfiniteTransition(label = "tts_voice_preview")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == TtsVoicePreviewState.LOADING) 900 else 1200,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tts_voice_preview_progress",
    )
    val color = Color(NgTheme.colors.primary)
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (state) {
            TtsVoicePreviewState.LOADING -> drawArc(
                color = color,
                startAngle = progress * 360f - 90f,
                sweepAngle = 92f,
                useCenter = false,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = Size(size.minDimension - 6.dp.toPx(), size.minDimension - 6.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            TtsVoicePreviewState.PLAYING -> drawCircle(
                color = color.copy(alpha = (1f - progress) * (160f / 255f)),
                radius = 11.5.dp.toPx() +
                    (size.minDimension / 2f - 1.5.dp.toPx() - 11.5.dp.toPx()) * progress,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            TtsVoicePreviewState.IDLE -> Unit
        }
    }
}

private fun ttsEngineVoiceTagPalette(index: Int): Pair<Int, Int> = when (index % 5) {
    0 -> R.color.ng_tts_tag_blue to R.color.ng_tts_tag_blue_container
    1 -> R.color.ng_tts_tag_purple to R.color.ng_tts_tag_purple_container
    2 -> R.color.ng_tts_tag_orange to R.color.ng_tts_tag_orange_container
    3 -> R.color.ng_tts_tag_green to R.color.ng_tts_tag_green_container
    else -> R.color.ng_tts_tag_pink to R.color.ng_tts_tag_pink_container
}
