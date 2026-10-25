package io.legado.app.ui.book.read.config

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Edits the existing effective title configuration; the host owns persistence and refresh. */
@Composable
internal fun ReadTitleSettingsContent(
    revision: Int,
    onSelectFont: () -> Unit,
    onSelectColor: (night: Boolean) -> Unit,
    onChanged: () -> Unit,
) {
    revision
    val config = ReadBookConfig.config
    // Equal-valued book/shared presets still own separate in-progress editing state.
    val configKey = ReadTitleConfigIdentity(config)
    var flagsDraft by remember(configKey) { mutableStateOf(config.titleSegFlag) }
    var segmentsExpanded by remember(configKey) { mutableStateOf(false) }
    var alignmentExpanded by remember(configKey) { mutableStateOf(false) }
    fun update(change: ReadBookConfig.Config.() -> Unit) {
        config.change()
        onChanged()
    }
    val segmentLabels = listOf(
        stringResource(R.string.read_title_segments_none),
        stringResource(R.string.read_title_segments_count),
        stringResource(R.string.read_title_segments_flags),
        stringResource(R.string.read_title_segments_regex),
    )
    val fontScale = LocalDensity.current.fontScale
    val alignmentTitle = stringResource(R.string.read_title_alignment)
    val alignmentOptions = listOf(
        ReadMoreConfigOption("0", stringResource(R.string.title_left)),
        ReadMoreConfigOption("1", stringResource(R.string.title_center)),
        ReadMoreConfigOption("2", stringResource(R.string.title_hide)),
    )
    val alignmentValue = config.titleMode.toString()
    val alignmentLabel = alignmentOptions.firstOrNull { it.value == alignmentValue }?.label
        ?: alignmentOptions.first().label
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val valueMaxWidth = maxWidth / 2f
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .clickable(role = Role.Button) { alignmentExpanded = true }
                .semantics { contentDescription = alignmentTitle; stateDescription = alignmentLabel }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                alignmentTitle, Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface), fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.wrapContentWidth().widthIn(max = valueMaxWidth).heightIn(min = 44.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alignmentLabel, Modifier.widthIn(max = (valueMaxWidth - 24.dp).coerceAtLeast(0.dp)),
                        color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp, lineHeight = 20.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painterResource(R.drawable.ic_expand_more), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = Color(NgTheme.colors.onSurfaceVariant),
                    )
                }
                ReadChoicePopup(
                    expanded = alignmentExpanded,
                    options = alignmentOptions,
                    selectedValue = alignmentValue,
                    onDismissRequest = { alignmentExpanded = false },
                    onSelected = { selected ->
                        alignmentExpanded = false
                        selected.toIntOrNull()?.takeIf { it in 0..2 }?.let { mode ->
                            update { titleMode = mode }
                        }
                    },
                    preferPopupBelow = true,
                    scrollable = true,
                    variant = ReadChoicePopupVariant.ADAPTIVE,
                )
            }
        }
    }
    ReadTitleSectionHeading(stringResource(R.string.read_title_size_spacing))
    ReadTitleValueSlider(
        title = stringResource(R.string.read_title_size_increment),
        value = config.titleSize, valueRange = 0..20,
        onValueChange = { update { titleSize = it } },
    )
    ReadTitleValueSlider(
        title = stringResource(R.string.title_margin_top),
        value = config.titleTopSpacing, valueRange = 0..100,
        onValueChange = { update { titleTopSpacing = it } },
    )
    ReadTitleValueSlider(
        title = stringResource(R.string.title_margin_bottom),
        value = config.titleBottomSpacing, valueRange = 0..100,
        onValueChange = { update { titleBottomSpacing = it } },
    )
    ReadTitleSectionHeading(stringResource(R.string.read_title_font_color))
    ReadTitleNavigationRow(
        title = stringResource(R.string.read_title_font),
        value = config.titleFont.takeIf(String::isNotBlank)?.let { path ->
            Uri.decode(path.substringAfterLast('/').substringAfterLast(':'))
        } ?: stringResource(R.string.read_title_follow_body_font),
        onClick = onSelectFont,
    )
    val weight = when (config.titleBold) {
        0 -> 400
        1 -> 900
        2 -> 300
        else -> config.titleBold.coerceIn(100, 900)
    }
    ReadTitleValueSlider(
        title = stringResource(R.string.font_weight),
        value = (weight / 100 - 1).coerceIn(0, 8), valueRange = 0..8,
        valueText = { ((it + 1) * 100).toString() },
        onValueChange = { update { titleBold = (it + 1) * 100 } },
    )
    val bodyColors = remember(configKey, AppConfig.isEInkMode) { readTitleBodyColors(config) }
    val dayColor = config.titleColor.takeUnless { it == 0 } ?: bodyColors.first
    val nightColor = config.titleColorNight.takeUnless { it == 0 }
        ?: config.titleColor.takeUnless { it == 0 } ?: bodyColors.second
    ReadTitleColorRow(dayColor, nightColor, onSelectColor)

    ReadTitleSectionHeading(stringResource(R.string.read_title_line_spacing))
    ReadTitleValueSlider(
        title = stringResource(R.string.read_title_line_spacing),
        value = config.titleLineSpacingExtra, valueRange = 0..100,
        valueText = { String.format(Locale.ROOT, "%.1f", it / 10f) },
        onValueChange = { update { titleLineSpacingExtra = it } },
    )
    ReadTitleDivider()
    ReadTitleNavigationRow(
        title = stringResource(R.string.read_title_segments),
        value = segmentLabels[config.titleSegType.coerceIn(segmentLabels.indices)],
        expanded = segmentsExpanded,
        onClick = { segmentsExpanded = !segmentsExpanded },
    )
    if (segmentsExpanded) {
        ReadConfigDock(
            labels = segmentLabels,
            selectedIndex = config.titleSegType,
            onSelected = { mode ->
                flagsDraft = config.titleSegFlag
                update { titleSegType = mode }
            },
            height = (44 * fontScale.coerceAtLeast(1f)).dp,
            modifier = Modifier.padding(horizontal = 6.dp),
            accessibilityLabel = stringResource(R.string.read_title_segments),
        )
        if (config.titleSegType == 1) {
            ReadTitleValueSlider(
                title = stringResource(R.string.read_title_segments_count_value),
                value = config.titleSegDistance, valueRange = 1..100,
                onValueChange = { update { titleSegDistance = it } },
            )
        } else if (config.titleSegType in 2..3) {
            val validPattern = config.titleSegType != 3 ||
                runCatching { Regex("(?<=$flagsDraft)") }.isSuccess
            NgFormField(
                label = stringResource(R.string.read_title_segments_flags),
                value = flagsDraft,
                onValueChange = { value ->
                    flagsDraft = value
                    if (config.titleSegType != 3 || runCatching { Regex("(?<=$value)") }.isSuccess) {
                        update { titleSegFlag = value }
                    }
                },
                placeholder = stringResource(
                    if (config.titleSegType == 3) R.string.read_title_regex_hint else R.string.read_title_flags_hint
                ),
                isError = !validPattern,
                supportingText = if (validPattern) null else stringResource(R.string.read_title_regex_invalid),
                modifier = Modifier.padding(horizontal = 6.dp),
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
            )
        }
        if (config.titleSegType != 0) {
            ReadTitleValueSlider(
                title = stringResource(R.string.read_title_segments_scale),
                value = (config.titleSegScaling * 100).roundToInt(), valueRange = 10..200,
                valueText = { "$it%" },
                onValueChange = { update { titleSegScaling = it / 100f } },
            )
            ReadTitleValueSlider(
                title = stringResource(R.string.read_title_segments_spacing),
                value = config.titleLineSpacingSub, valueRange = 0..100,
                valueText = { String.format(Locale.ROOT, "%.1f", it / 10f) },
                onValueChange = { update { titleLineSpacingSub = it } },
            )
        }
    }
}

@Composable
private fun ReadTitleDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp),
        color = Color(NgTheme.colors.outline).copy(alpha = 0.24f),
    )
}

@Composable
private fun ReadTitleSectionHeading(title: String) {
    ReadTitleDivider()
    Text(
        text = title,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
        color = Color(NgTheme.colors.primary), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ReadTitleNavigationRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    expanded: Boolean? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = title; stateDescription = value }
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, Modifier.weight(1f),
            color = Color(if (expanded == null) NgTheme.colors.onSurface else NgTheme.colors.primary),
            fontSize = 14.sp, lineHeight = 20.sp,
            fontWeight = if (expanded == null) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            value, Modifier.weight(1f), color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painterResource(when (expanded) {
                true -> R.drawable.ic_expand_less
                false -> R.drawable.ic_expand_more
                null -> R.drawable.ic_chevron_right_20
            }),
            contentDescription = null, modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun ReadTitleColorRow(day: Int, night: Int, onSelectColor: (Boolean) -> Unit) {
    val colorLabel = stringResource(R.string.read_title_color)
    val fontScale = LocalDensity.current.fontScale
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            colorLabel, Modifier.width(60.dp), color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(
            Modifier.width((224f * fontScale.coerceAtLeast(1f)).dp)
                .heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onSelectColor(ReadBookConfig.isNightTheme) }
                .semantics { contentDescription = colorLabel },
        ) {
            val stackedColors = fontScale > 1f || (maxWidth.value - 25f) / 2f < 50f * fontScale
            val barHeight = if (stackedColors) {
                maxOf(32f * fontScale, 20f * fontScale + 24f)
            } else {
                32f * fontScale
            }.dp
            Box(
                Modifier.fillMaxWidth().height(barHeight).align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp)).background(ReadDrawerStyle.dockSurfaceColor())
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReadTitleColorChoice(
                    label = stringResource(R.string.read_title_day),
                    description = stringResource(R.string.read_title_color_day),
                    color = day, modifier = Modifier.weight(1f), stacked = stackedColors,
                    onClick = { onSelectColor(false) },
                )
                Box(Modifier.width(1.dp).height(16.dp).background(Color(NgTheme.colors.outline).copy(alpha = 0.2f)))
                ReadTitleColorChoice(
                    label = stringResource(R.string.read_title_night),
                    description = stringResource(R.string.read_title_color_night),
                    color = night, modifier = Modifier.weight(1f), stacked = stackedColors,
                    onClick = { onSelectColor(true) },
                )
                // The arrow belongs to the parent current-mode action; child choices consume their own taps.
                Box(
                    Modifier.width(24.dp).heightIn(min = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right_20), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = Color(NgTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadTitleColorChoice(
    label: String,
    description: String,
    color: Int,
    modifier: Modifier,
    stacked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        if (stacked) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label, color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp, lineHeight = 20.sp,
                )
                Spacer(Modifier.height(6.dp))
                ReadTitleColorSwatch(color)
            }
        } else {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    label, color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                ReadTitleColorSwatch(color)
            }
        }
    }
}

@Composable
private fun ReadTitleColorSwatch(color: Int) {
    Box(
        Modifier.size(18.dp).clip(CircleShape).background(Color(color))
            .border(0.7.dp, Color(NgTheme.colors.outline).copy(alpha = 0.4f), CircleShape)
    )
}

@Composable
private fun ReadTitleValueSlider(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueText: (Int) -> String = Int::toString,
    onValueChange: (Int) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val valueColumnWidth = if (valueText(valueRange.last).endsWith('%')) 36f else 28f
    var tracking by remember { mutableStateOf(false) }
    var pending by remember { mutableIntStateOf(value.coerceIn(valueRange)) }
    val shown = if (tracking) pending else value.coerceIn(valueRange)
    fun applyStep(delta: Int) {
        val stepped = (shown + delta).coerceIn(valueRange)
        pending = stepped
        tracking = false
        onValueChange(stepped)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 272.dp || fontScale > 1f
        Column {
            if (narrow) {
                Text(
                    title, Modifier.padding(start = 6.dp, top = 6.dp),
                    color = Color(NgTheme.colors.onSurface), fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!narrow) {
                    Text(
                        title, Modifier.width(60.dp), color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1,
                    )
                }
                NgSliderStepButton(
                    iconRes = R.drawable.ic_reduce,
                    contentDescription = title + "，" + stringResource(R.string.reduce),
                    enabled = shown > valueRange.first,
                    onClick = { applyStep(-1) },
                    modifier = Modifier.size(44.dp),
                )
                NgSlider(
                    value = shown.toFloat(),
                    onValueChange = { pending = it.roundToInt().coerceIn(valueRange); tracking = true },
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    steps = valueRange.last - valueRange.first - 1,
                    visualSteps = 0,
                    variant = NgSliderVariant.COMPACT,
                    modifier = Modifier.weight(1f).height(44.dp)
                        .semantics { contentDescription = "$title " + valueText(shown) },
                    onValueChangeFinished = {
                        if (tracking) onValueChange(pending)
                        tracking = false
                    },
                )
                NgSliderStepButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = title + "，" + stringResource(R.string.plus),
                    enabled = shown < valueRange.last,
                    onClick = { applyStep(1) },
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    valueText(shown), Modifier.width((valueColumnWidth * fontScale.coerceAtLeast(1f)).dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.End, maxLines = 1,
                )
            }
        }
    }
}

private class ReadTitleConfigIdentity(private val config: ReadBookConfig.Config) {
    override fun equals(other: Any?): Boolean = other is ReadTitleConfigIdentity && config === other.config
    override fun hashCode(): Int = System.identityHashCode(config)
}

/** Mirrors curTitleColor without changing the reading mode while either color is edited. */
internal fun readTitleColorPreview(config: ReadBookConfig.Config, night: Boolean): Int {
    if (night && config.titleColorNight != 0) return config.titleColorNight
    if (config.titleColor != 0) return config.titleColor
    val bodyColors = readTitleBodyColors(config)
    return if (night) bodyColors.second else bodyColors.first
}

private fun readTitleBodyColors(config: ReadBookConfig.Config): Pair<Int, Int> {
    if (AppConfig.isEInkMode) return config.curTextColor().let { it to it }
    val fields = config.toMap()
    return (fields["textColor"] as String).toColorInt() to
        (fields["textColorNight"] as String).toColorInt()
}
