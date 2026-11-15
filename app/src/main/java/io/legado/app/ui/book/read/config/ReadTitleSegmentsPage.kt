package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.provider.ReadTitleSegmentType
import io.legado.app.ui.book.read.page.provider.ReadTitleStyleParser
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import kotlin.math.roundToInt

internal fun titleSegmentsModeLabel(type: Int): Int = when (type) {
    ReadTitleSegmentType.CHAPTER_NUMBER -> R.string.read_title_segments_chapter
    ReadTitleSegmentType.CHARACTER_COUNT -> R.string.read_title_segments_count_option
    ReadTitleSegmentType.DELIMITER -> R.string.read_title_segments_flags_option
    ReadTitleSegmentType.REGEX -> R.string.read_title_segments_regex_option
    else -> R.string.read_title_segments_none
}

internal fun ReadBookConfig.Config.selectTitleSegmentType(type: Int) {
    val customType = when {
        ReadTitleSegmentType.isCustom(type) -> type
        ReadTitleSegmentType.isCustom(titleSegType) -> titleSegType
        else -> titleSegCustomType
    }
    titleSegCustomType = customType
    titleSegType = type
}

internal fun ReadBookConfig.Config.preferredCustomTitleSegmentType(): Int =
    titleSegType.takeIf(ReadTitleSegmentType::isCustom)
        ?: titleSegCustomType.takeIf(ReadTitleSegmentType::isCustom)
        ?: ReadTitleSegmentType.CHARACTER_COUNT

private val titleSegmentMenuTypes = listOf(
    ReadTitleSegmentType.NONE,
    ReadTitleSegmentType.CHAPTER_NUMBER,
    ReadTitleSegmentType.CHARACTER_COUNT,
    ReadTitleSegmentType.DELIMITER,
    ReadTitleSegmentType.REGEX,
)

@Composable
internal fun ReadTitleSegmentationSetting(
    config: ReadBookConfig.Config,
    revision: Int,
    contentColor: Color,
    onChanged: () -> Unit,
) {
    revision
    val type = config.titleSegType
    val focusManager = LocalFocusManager.current
    ReadStyleChoiceSettingRow(
        title = stringResource(R.string.read_title_segments_short),
        labels = titleSegmentMenuTypes.map { stringResource(titleSegmentsModeLabel(it)) },
        selectedIndex = titleSegmentMenuTypes.indexOf(type).coerceAtLeast(0),
        contentColor = contentColor,
        onSelected = { index ->
            focusManager.clearFocus()
            val nextType = titleSegmentMenuTypes[index]
            if (nextType != type) {
                config.selectTitleSegmentType(nextType)
                onChanged()
            }
        },
    )
    if (type == ReadTitleSegmentType.CHAPTER_NUMBER || ReadTitleSegmentType.isCustom(type)) {
        NgFormGroupDivider()
        if (ReadTitleSegmentType.isCustom(type)) {
            ReadTitleCustomRuleFields(config, revision, contentColor, onChanged)
            NgFormGroupDivider()
        }
        ReadTitleSegmentMetrics(config, revision, contentColor, onChanged)
    }
}

@Composable
internal fun ReadTitleSegmentsControls(
    config: ReadBookConfig.Config,
    revision: Int,
    contentColor: Color,
    onChanged: () -> Unit,
) {
    val type = config.titleSegType
    val activeCustom = ReadTitleSegmentType.isCustom(type)
    val selected = when {
        activeCustom -> 2
        type == ReadTitleSegmentType.CHAPTER_NUMBER -> 1
        else -> 0
    }
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxWidth()) {
        ReadStyleModeSelector(
            labels = listOf(
                stringResource(R.string.read_title_segments_none),
                stringResource(R.string.read_title_segments_chapter),
                stringResource(R.string.read_title_segments_custom),
            ),
            selectedIndex = selected,
            onSelected = { index ->
                focusManager.clearFocus()
                val nextType = when (index) {
                    0 -> ReadTitleSegmentType.NONE
                    1 -> ReadTitleSegmentType.CHAPTER_NUMBER
                    else -> config.preferredCustomTitleSegmentType()
                }
                if (nextType != type) {
                    config.selectTitleSegmentType(nextType)
                    onChanged()
                }
            },
            contentColor = contentColor,
        )
        if (selected == 2 || selected == 1 || activeCustom) {
            Spacer(Modifier.height(8.dp))
            NgFormPanel(Modifier.fillMaxWidth()) {
                if (selected == 2) {
                    ReadTitleCustomRules(config, revision, contentColor, onChanged)
                }
                if (selected == 1 || activeCustom) {
                    if (activeCustom) NgFormGroupDivider()
                    ReadTitleSegmentMetrics(config, revision, contentColor, onChanged)
                }
            }
        }
    }
}

@Composable
private fun ReadTitleCustomRules(
    config: ReadBookConfig.Config,
    revision: Int,
    contentColor: Color,
    onChanged: () -> Unit,
) {
    revision
    val type = config.titleSegType
    val focusManager = LocalFocusManager.current
    val methodLabels = listOf(
        stringResource(R.string.read_title_segments_count),
        stringResource(R.string.read_title_segments_flags),
        stringResource(R.string.read_title_segments_regex),
    )
    val options = methodLabels.mapIndexed { index, label ->
        ReadMoreConfigOption((index + ReadTitleSegmentType.CHARACTER_COUNT).toString(), label)
    }
    val selectedOption = options.firstOrNull { it.value == type.toString() }
    var expanded by remember(ReadTitleConfigIdentity(config)) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        EditorNavigationRow(
            title = stringResource(R.string.read_title_segments_method),
            summary = selectedOption?.label ?: stringResource(R.string.read_title_segments_choose_rule),
            color = contentColor,
            rowHeight = 48.dp,
            onClick = {
                focusManager.clearFocus()
                expanded = true
            },
        )
        Box(Modifier.align(Alignment.CenterEnd).width(1.dp).height(48.dp)) {
            ReadChoicePopup(
                expanded = expanded,
                options = options,
                selectedValue = selectedOption?.value,
                onDismissRequest = { expanded = false },
                onSelected = { value ->
                    val nextType = value.toIntOrNull()
                    if (nextType != null && ReadTitleSegmentType.isCustom(nextType) && nextType != type) {
                        focusManager.clearFocus()
                        config.selectTitleSegmentType(nextType)
                        onChanged()
                    }
                },
                preferPopupBelow = true,
                scrollable = true,
                variant = ReadChoicePopupVariant.ADAPTIVE,
                menuWidth = 100.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
            )
        }
    }
    if (selectedOption != null) NgFormGroupDivider()
    ReadTitleCustomRuleFields(config, revision, contentColor, onChanged)
}

@Composable
private fun ReadTitleCustomRuleFields(
    config: ReadBookConfig.Config,
    revision: Int,
    contentColor: Color,
    onChanged: () -> Unit,
) {
    revision
    val type = config.titleSegType
    val focusManager = LocalFocusManager.current
    val methodLabels = listOf(
        stringResource(R.string.read_title_segments_count),
        stringResource(R.string.read_title_segments_flags),
        stringResource(R.string.read_title_segments_regex),
    )
    when (type) {
        ReadTitleSegmentType.CHARACTER_COUNT -> {
            val label = stringResource(R.string.read_title_segments_count_value)
            ReadTypographyStandaloneMetric(
                TypographyItem(label, ReadTypographyMetric.TITLE_SEGMENT_COUNT, config.titleSegDistance) {
                    config.titleSegDistance = it
                    onChanged()
                },
                labelWidth = readTypographyMetricLabelWidth(listOf(label)),
                contentColor = contentColor,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp),
            )
        }
        ReadTitleSegmentType.DELIMITER, ReadTitleSegmentType.REGEX -> {
            var draft by remember(ReadTitleConfigIdentity(config), type) { mutableStateOf(config.titleSegFlag) }
            val isRegex = type == ReadTitleSegmentType.REGEX
            val valid = !isRegex || remember(draft) { ReadTitleStyleParser.isValidSplitRegex(draft) }
            val hint = stringResource(if (isRegex) R.string.read_title_regex_hint else R.string.read_title_flags_hint)
            NgFormField(
                label = methodLabels[type - ReadTitleSegmentType.CHARACTER_COUNT],
                value = draft,
                onValueChange = { value ->
                    draft = value
                    if ((!isRegex || ReadTitleStyleParser.isValidSplitRegex(value)) && value != config.titleSegFlag) {
                        config.titleSegFlag = value
                        onChanged()
                    }
                },
                isError = !valid,
                supportingText = if (valid) hint else stringResource(R.string.read_title_regex_invalid),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
internal fun ReadTitleSegmentMetrics(
    config: ReadBookConfig.Config,
    revision: Int,
    contentColor: Color,
    onChanged: () -> Unit,
) {
    revision
    val scale = stringResource(R.string.read_title_segments_scale)
    val gap = stringResource(R.string.read_title_segments_spacing)
    val labelWidth = readTypographyMetricLabelWidth(listOf(scale, gap))
    val inset = Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp)
    ReadTypographyStandaloneMetric(
        TypographyItem(
            scale, ReadTypographyMetric.TITLE_SEGMENT_SCALE,
            (config.titleSegScaling * 100).roundToInt(),
            displayValue = { "$it%" },
        ) {
            config.titleSegScaling = it / 100f
            onChanged()
        },
        labelWidth, contentColor, inset,
    )
    NgFormGroupDivider()
    ReadTypographyStandaloneMetric(
        TypographyItem(gap, ReadTypographyMetric.TITLE_SEGMENT_SPACING, config.titleLineSpacingSub) {
            config.titleLineSpacingSub = it
            onChanged()
        },
        labelWidth, contentColor, inset,
    )
}
