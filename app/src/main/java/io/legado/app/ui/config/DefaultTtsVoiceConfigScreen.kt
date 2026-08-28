package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgSettingsCardSurface
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme

/** 默认发音人的三个固定语义入口。 */
internal enum class DefaultTtsVoiceSlot {
    NARRATOR,
    DIALOGUE_MALE,
    DIALOGUE_FEMALE
}

/**
 * 字标颜色只表达发音人角色，不携带引擎或发音人业务状态。
 *
 * Screen 根据该语义复刻现有 View 卡片的灰／蓝／粉字标，宿主无需传视觉颜色。
 */
internal enum class DefaultTtsVoiceAvatarRole {
    NARRATOR,
    MALE,
    FEMALE
}

@Immutable
internal data class DefaultTtsVoiceCardUiModel(
    val slot: DefaultTtsVoiceSlot,
    val title: String,
    val summary: String,
    val avatarText: String,
    val avatarRole: DefaultTtsVoiceAvatarRole,
    val fallbackTag: String? = null,
    val enabled: Boolean = true,
    val clickable: Boolean = true
) {
    val isInteractive: Boolean
        get() = enabled && clickable
}

@Immutable
internal data class DefaultTtsVoiceConfigScreenState(
    val narrator: DefaultTtsVoiceCardUiModel,
    val dialogueMale: DefaultTtsVoiceCardUiModel,
    val dialogueFemale: DefaultTtsVoiceCardUiModel
) {
    init {
        require(narrator.slot == DefaultTtsVoiceSlot.NARRATOR)
        require(dialogueMale.slot == DefaultTtsVoiceSlot.DIALOGUE_MALE)
        require(dialogueFemale.slot == DefaultTtsVoiceSlot.DIALOGUE_FEMALE)
    }

    /** 保持已验收的旁白、男声兜底、女声兜底顺序。 */
    val cards: List<DefaultTtsVoiceCardUiModel>
        get() = listOf(narrator, dialogueMale, dialogueFemale)
}

internal sealed interface DefaultTtsVoiceConfigScreenAction {
    data object NarratorClicked : DefaultTtsVoiceConfigScreenAction
    data object DialogueMaleClicked : DefaultTtsVoiceConfigScreenAction
    data object DialogueFemaleClicked : DefaultTtsVoiceConfigScreenAction
}

internal fun DefaultTtsVoiceSlot.toScreenAction(): DefaultTtsVoiceConfigScreenAction {
    return when (this) {
        DefaultTtsVoiceSlot.NARRATOR ->
            DefaultTtsVoiceConfigScreenAction.NarratorClicked

        DefaultTtsVoiceSlot.DIALOGUE_MALE ->
            DefaultTtsVoiceConfigScreenAction.DialogueMaleClicked

        DefaultTtsVoiceSlot.DIALOGUE_FEMALE ->
            DefaultTtsVoiceConfigScreenAction.DialogueFemaleClicked
    }
}

/**
 * 默认发音人配置的纯 Compose 页面。
 *
 * 引擎快照、加载、摘要生成与发音人抽屉仍由 Fragment 持有；这里仅渲染状态并回传入口事件。
 */
@Composable
internal fun DefaultTtsVoiceConfigScreen(
    state: DefaultTtsVoiceConfigScreenState,
    onAction: (DefaultTtsVoiceConfigScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = NgTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.pageHorizontalDp.dp,
            top = spacing.largeDp.dp,
            end = spacing.pageHorizontalDp.dp,
            bottom = spacing.largeDp.dp
        ),
        verticalArrangement = Arrangement.spacedBy(DefaultVoiceCardGap)
    ) {
        items(
            items = state.cards,
            key = DefaultTtsVoiceCardUiModel::slot,
            contentType = { "default_tts_voice" }
        ) { card ->
            DefaultTtsVoiceCard(
                model = card,
                onClick = {
                    if (card.isInteractive) {
                        onAction(card.slot.toScreenAction())
                    }
                }
            )
        }
    }
}

@Composable
private fun DefaultTtsVoiceCard(
    model: DefaultTtsVoiceCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    NgSettingsCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DefaultVoiceCardMinHeight)
            .alpha(if (model.enabled) 1f else DisabledCardAlpha),
        cornerRadius = NgTheme.shapes.largeDp.dp,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (model.isInteractive) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier.semantics {
                            if (!model.enabled) disabled()
                        }
                    }
                )
                .padding(
                    start = NgTheme.spacing.largeDp.dp,
                    top = NgTheme.spacing.mediumDp.dp,
                    end = NgTheme.spacing.mediumDp.dp,
                    bottom = NgTheme.spacing.mediumDp.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DefaultTtsVoiceAvatar(
                text = model.avatarText,
                role = model.avatarRole
            )
            Spacer(Modifier.width(NgTheme.spacing.mediumDp.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.title,
                        modifier = Modifier.weight(1f, fill = false),
                        color = Color(colors.onSurface),
                        fontSize = DefaultVoiceTitleSize,
                        lineHeight = DefaultVoiceTitleLineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    model.fallbackTag
                        ?.takeIf(String::isNotBlank)
                        ?.let { tag ->
                            Spacer(Modifier.width(NgTheme.spacing.smallDp.dp))
                            NgStatusTag(
                                text = tag,
                                variant = NgStatusTagVariant.INFO
                            )
                        }
                }
                Box(
                    modifier = Modifier.heightIn(min = DefaultVoiceSummaryMinHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = model.summary,
                        color = Color(colors.onSurfaceVariant),
                        fontSize = DefaultVoiceSummarySize,
                        lineHeight = DefaultVoiceSummaryLineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(NgTheme.spacing.smallDp.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                modifier = Modifier.size(DefaultVoiceChevronSize),
                tint = Color(colors.onSurfaceVariant)
            )
        }
    }
}

@Composable
private fun DefaultTtsVoiceAvatar(
    text: String,
    role: DefaultTtsVoiceAvatarRole,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(DefaultVoiceAvatarSize)
            .clip(CircleShape)
            .background(role.containerColor())
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = DefaultVoiceAvatarTextSize,
            lineHeight = DefaultVoiceAvatarTextLineHeight,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

private fun DefaultTtsVoiceAvatarRole.containerColor(): Color {
    return when (this) {
        DefaultTtsVoiceAvatarRole.NARRATOR -> Color(0xFFE8E8E8)
        DefaultTtsVoiceAvatarRole.MALE -> Color(0xFF9EB8FF)
        DefaultTtsVoiceAvatarRole.FEMALE -> Color(0xFFFFA1B5)
    }
}

private val DefaultVoiceCardMinHeight = 78.dp
private val DefaultVoiceCardGap = 10.dp
private val DefaultVoiceAvatarSize = 44.dp
private val DefaultVoiceChevronSize = 20.dp
private val DefaultVoiceSummaryMinHeight = 32.dp
private val DefaultVoiceTitleSize = 16.sp
private val DefaultVoiceTitleLineHeight = 20.sp
private val DefaultVoiceSummarySize = 14.sp
private val DefaultVoiceSummaryLineHeight = 18.sp
private val DefaultVoiceAvatarTextSize = 20.sp
private val DefaultVoiceAvatarTextLineHeight = 24.sp
private const val DisabledCardAlpha = 0.55f
