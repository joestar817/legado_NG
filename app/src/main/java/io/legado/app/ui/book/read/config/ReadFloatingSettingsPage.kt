package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.EffectiveReadFloatingColor
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.ui.book.read.ReadCatalogStyle
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal enum class FloatingSettingsTab { COLOR, LAYOUT_AND_ACTIONS }

@Composable
internal fun FloatingSettingsTabSelector(
    selected: FloatingSettingsTab,
    contentColor: Color,
    onSelected: (FloatingSettingsTab) -> Unit,
) {
    ReadStyleModeSelector(
        labels = listOf(stringResource(R.string.read_floating_tab_color),
            stringResource(R.string.read_floating_tab_layout)),
        selectedIndex = selected.ordinal,
        contentColor = contentColor,
        onSelected = { onSelected(FloatingSettingsTab.entries[it]) },
    )
}

@Composable
internal fun FloatingSettingsPage(
    state: ReadStyleUiState,
    contentColor: Color,
    actions: ReadStyleActions,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column(Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.9f).padding(bottom = 10.dp)) {
        NgDrawerDragHandle(modifier = Modifier.padding(top = 6.dp), variant = NgDrawerDragHandleVariant.COMPACT)
        if (state.floatingStandalone) {
            Text(
                stringResource(R.string.read_menu_floating),
                Modifier.fillMaxWidth().height(48.dp).padding(top = 10.dp),
                color = contentColor, fontSize = 17.sp, textAlign = TextAlign.Center,
            )
        } else {
            ReadStyleSubpageHeader(stringResource(R.string.read_menu_floating), contentColor, actions.onBack,
                circularBack = true, centerTitle = true)
        }
        Box(Modifier.padding(horizontal = 16.dp)) {
            FloatingSettingsTabSelector(state.floatingTab, contentColor, actions.onFloatingTabChanged)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (state.floatingTab) {
                FloatingSettingsTab.COLOR -> FloatingAppearanceSettings(state, contentColor, actions)
                FloatingSettingsTab.LAYOUT_AND_ACTIONS -> FloatingToolSettings(state, contentColor, actions)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FloatingAppearanceSettings(state: ReadStyleUiState, color: Color, actions: ReadStyleActions) {
    val styleLabels = listOf(
        stringResource(R.string.ng_palette_vibrant), stringResource(R.string.ng_palette_expressive),
        stringResource(R.string.ng_palette_rainbow), stringResource(R.string.ng_palette_fruit_salad),
    )
    val styles = ReadFloatingColorStyle.entries
    val styleIndex = styles.indexOf(state.editorFloatingColorStyle)

    NgFormPanel {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (state.onlyThisBook) R.string.read_floating_current_style
                else R.string.read_floating_current_preset),
                color = Color(NgTheme.colors.primary), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (state.onlyThisBook) stringResource(R.string.read_floating_book_style)
                else state.selectedPresetName,
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            )
        }
        NgFormGroupDivider()
        if (!state.onlyThisBook) {
            PresetSwitchRow(stringResource(R.string.read_floating_global_follow), R.drawable.ic_cfg_theme,
                state.globalFloatingFollowApp, color, actions.onGlobalFloatingFollowAppChanged)
            NgFormGroupDivider()
        }
        PresetSwitchRow(
            title = stringResource(R.string.ng_visual_system_liquid_glass),
            iconRes = R.drawable.ic_bookshelf_top_bar_floating,
            checked = ReadFloatingAppearanceState.liquidGlassEnabled,
            contentColor = color,
            onCheckedChange = actions.onFloatingLiquidGlassChanged,
        )
        NgFormGroupDivider()
        FloatingColorSettingRow(state, color, actions)
        NgFormGroupDivider()
        ReadStyleChoiceSettingRow(
            title = stringResource(R.string.read_style_floating_color_style),
            labels = styleLabels, selectedIndex = styleIndex, contentColor = color,
            leadingIconRes = R.drawable.ic_brightness,
            summary = if (state.globalFloatingFollowApp) {
                stringResource(R.string.read_floating_global_style, styleLabels[styleIndex])
            } else null,
            preferPopupBelow = true,
            useVisibleWindowBounds = true,
            onSelected = { actions.onFloatingColorStyleChanged(styles[it]) },
        )
    }
    Spacer(Modifier.height(12.dp))
    val transparencyLabel = stringResource(R.string.read_floating_window_transparency)
    val primaryStrengthLabel = stringResource(R.string.read_floating_window_primary_strength)
    val labelWidth = readTypographyMetricLabelWidth(listOf(transparencyLabel, primaryStrengthLabel))
    NgFormPanel {
        FloatingPercentSliderRow(transparencyLabel, state.editorFloatingTransparency, labelWidth, color,
            actions.onFloatingTransparencyChanged, actions.onFloatingAppearanceChangeFinished)
        NgFormGroupDivider()
        FloatingPercentSliderRow(primaryStrengthLabel, state.editorFloatingPrimaryStrength, labelWidth, color,
            actions.onFloatingPrimaryStrengthChanged, actions.onFloatingAppearanceChangeFinished)
    }
}

@Composable
private fun FloatingColorSettingRow(
    state: ReadStyleUiState,
    contentColor: Color,
    actions: ReadStyleActions,
) {
    val title = stringResource(R.string.read_floating_color_label)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppearanceSettingIcon(Icons.Outlined.Palette)
        Text(title, Modifier.weight(1f), color = contentColor, fontSize = ReadSettingTextSize.navigationLabel,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        FloatingColorChoice(
            label = stringResource(R.string.read_style_mode_day), title = title,
            mode = ReadAppearanceMode.DAY, color = state.floatingDayColor,
            enabled = !state.globalFloatingFollowApp, contentColor = contentColor,
            actions = actions,
        )
        FloatingColorChoice(
            label = stringResource(R.string.read_style_mode_night), title = title,
            mode = ReadAppearanceMode.NIGHT, color = state.floatingNightColor,
            enabled = !state.globalFloatingFollowApp, contentColor = contentColor,
            actions = actions,
        )
    }
}

internal fun floatingColorPreviewSeed(seed: Int, mode: ReadAppearanceMode): Int =
    seed.takeIf { it != 0 } ?: ReadDrawerStyle.independentSeedForMode(mode)

@Composable
private fun FloatingColorChoice(
    label: String,
    title: String,
    mode: ReadAppearanceMode,
    color: EffectiveReadFloatingColor,
    enabled: Boolean,
    contentColor: Color,
    actions: ReadStyleActions,
) {
    val previewColor = floatingColorPreviewSeed(color.seed, mode)
    ReadFormColorChoice(
        label = label,
        title = title,
        color = previewColor,
        contentColor = contentColor,
        enabled = enabled,
        valueLabel = if (color.followsApplication) stringResource(R.string.theme_mode_follow_short) else null,
        valueDescription = if (color.followsApplication) stringResource(R.string.read_style_floating_color_follow) else null,
        onClick = {
            actions.onEditorModeChanged(mode.ordinal)
            actions.onPickFloatingColor()
        },
    )
}

@Composable
private fun FloatingPercentSliderRow(
    title: String,
    value: Int,
    labelWidth: Dp,
    color: Color,
    onValueChanged: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val valueWidth = 40.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    ReadFormSliderRow(title, "$value%", labelWidth, valueWidth, color) { modifier ->
        NgSlider(
            value = value.toFloat(),
            onValueChange = {
                val newValue = it.roundToInt().coerceIn(
                    ReadFloatingAppearanceConfig.MIN_PERCENT, ReadFloatingAppearanceConfig.MAX_PERCENT,
                )
                onValueChanged(newValue)
            },
            valueRange = ReadFloatingAppearanceConfig.MIN_PERCENT.toFloat()..
                ReadFloatingAppearanceConfig.MAX_PERCENT.toFloat(),
            steps = 99,
            visualSteps = 0,
            variant = NgSliderVariant.CONTINUOUS,
            onValueChangeFinished = onValueChangeFinished,
            modifier = modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun FloatingToolSettings(state: ReadStyleUiState, color: Color, actions: ReadStyleActions) {
    val catalogStyles = listOf(
        ReadCatalogStyle.CARD.value,
        ReadCatalogStyle.COMPACT_SIDE.value,
        ReadCatalogStyle.DENSE_LIST.value,
    )
    val progressValues = stringArrayResource(R.array.progress_bar_behavior_value).toList()
    NgFormPanel {
        ReadStyleChoiceSettingRow(
            title = stringResource(R.string.read_catalog_style),
            labels = listOf(stringResource(R.string.read_catalog_style_card),
                stringResource(R.string.read_catalog_style_compact_side),
                stringResource(R.string.read_catalog_style_dense_list)),
            selectedIndex = catalogStyles.indexOf(state.floatingCatalogStyle), contentColor = color,
            leadingIconRes = R.drawable.ic_manga_menu_catalog, preferPopupBelow = true,
            onSelected = { actions.onFloatingCatalogStyleChanged(catalogStyles[it]) },
        )
        NgFormGroupDivider()
        PresetSwitchRow(stringResource(R.string.show_floating_toolbar), R.drawable.ic_bookshelf_top_bar_floating,
            state.floatingShowToolbar, color, actions.onFloatingToolbarChanged)
        NgFormGroupDivider()
        Box(Modifier.padding(horizontal = 14.dp)) {
            EditorNavigationRow(stringResource(R.string.read_floating_side_tool_order), color = color,
                leadingIconRes = R.drawable.ic_sort, onClick = actions.onFloatingSideToolOrder)
        }
        NgFormGroupDivider()
        Box(Modifier.padding(horizontal = 14.dp)) {
            EditorNavigationRow(stringResource(R.string.read_floating_selection_order), color = color,
                leadingIconRes = R.drawable.ic_sort, onClick = actions.onFloatingToolbarOrder)
        }
        NgFormGroupDivider()
        PresetSwitchRow(stringResource(R.string.read_floating_show_chapter_progress),
            R.drawable.ic_read_chapter_progress, state.floatingShowChapterProgress, color,
            actions.onFloatingChapterProgressChanged)
        NgFormGroupDivider()
        ReadStyleChoiceSettingRow(
            title = stringResource(R.string.progress_bar_behavior),
            labels = stringArrayResource(R.array.progress_bar_behavior_title).toList(),
            selectedIndex = progressValues.indexOf(state.floatingProgressBehavior), contentColor = color,
            leadingIconRes = R.drawable.ic_manga_menu_settings, preferPopupBelow = true,
            useVisibleWindowBounds = true,
            onSelected = { actions.onFloatingProgressBehaviorChanged(progressValues[it]) },
        )
    }
}
