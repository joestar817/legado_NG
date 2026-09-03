package io.legado.app.ui.book.read.config

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.ngSliderStepValue
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.config.NgInlineColorPicker
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val StandardPageHeight = 336.dp
private val EditorPageHeight = 500.dp
private val PresetInitialScrollOffset = 10.dp
private val PresetVisibleHorizontalInset = 6.dp
private val BackgroundTileSpacing = 6.dp

internal enum class ReadStylePage {
    PRESET,
    ADJUST,
    EDIT,
    EDIT_TEXT_COLOR,
    EDIT_BACKGROUND_COLOR,
    EDIT_ACCENT_COLOR,
    EDIT_UNDERLINE,
    EDIT_UNDERLINE_COLOR,
    HIGHLIGHT,
    HIGHLIGHT_EDIT,
    HIGHLIGHT_TEXT_COLOR,
    HIGHLIGHT_BACKGROUND_COLOR,
    HIGHLIGHT_UNDERLINE_COLOR,
}

internal enum class HighlightSelectionMode {
    NONE,
    EXPORT,
    DELETE,
}

internal data class FullLineUnderlineUiState(
    val enabled: Boolean,
    val dashed: Boolean,
    val color: Int,
    val width: Int,
    val offset: Int,
    val extend: Boolean,
    val dashLength: Float,
    val gapLength: Float,
)

internal data class ReadStylePresetUi(
    val index: Int,
    val name: String,
    val textColor: Int,
    val background: ImageBitmap?,
)

internal data class ReadStyleBackgroundUi(
    val type: Int,
    val name: String,
    val label: String,
    val background: ImageBitmap,
)

private data class ReadStyleShortcut(
    val titleRes: Int,
    val iconRes: Int? = null,
    val iconText: String? = null,
    val iconTextSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    val iconOffsetY: Dp = 0.dp,
    val onClick: () -> Unit,
)

internal data class ReadStyleUiState(
    val presets: List<ReadStylePresetUi>,
    val selectedPresetIndex: Int,
    val selectedPresetName: String,
    val canRestoreCurrentDefault: Boolean,
    val highlightSummary: String,
    val shareLayout: Boolean,
    val globalFloatingFollowApp: Boolean,
    val textSize: Int,
    val letterSpacing: Float,
    val lineSpacingExtra: Int,
    val paragraphSpacing: Int,
    val pageAnim: Int,
    val highlightRules: List<ReadHighlightRule>,
    val highlightSelectionMode: HighlightSelectionMode,
    val selectedHighlightIds: Set<String>,
    val editorMode: Int,
    val editorModeLabel: String,
    val editorPreviewBackground: ImageBitmap?,
    val editorBackgrounds: List<ReadStyleBackgroundUi>,
    val editorBackgroundType: Int,
    val editorBackgroundName: String,
    val editorTextColor: Int,
    val editorBackgroundColor: Int,
    val editorTextAccentColor: Int,
    val editorBackgroundAlpha: Int,
    val editorFloatingColorSeed: Int,
    val editorFloatingColorFromBackground: Boolean,
    val editorFloatingTransparency: Int,
    val editorFloatingPrimaryStrength: Int,
    val editorFloatingColorStyle: ReadFloatingColorStyle,
    val fullLineUnderline: FullLineUnderlineUiState,
    val highlightDraft: ReadHighlightRule?,
    val editingHighlightIndex: Int?,
    val highlightColorMode: Int,
    val editorInitialColor: Int?,
    val editorInitialColorWasUnset: Boolean,
    val editorInitialBackgroundType: Int?,
    val editorInitialBackgroundName: String?,
    val editorInitialBackground: ImageBitmap?,
)

internal data class ReadStyleActions(
    val onPageSelected: (ReadStylePage) -> Unit,
    val onCreatePreset: () -> Unit,
    val onSelectPreset: (Int) -> Unit,
    val onImportPreset: () -> Unit,
    val onEditPreset: () -> Unit,
    val onExportPreset: () -> Unit,
    val onDeletePreset: () -> Unit,
    val onRestoreCurrentPreset: () -> Unit,
    val onRestoreAllPresets: () -> Unit,
    val onShareLayoutChanged: (Boolean) -> Unit,
    val onGlobalFloatingFollowAppChanged: (Boolean) -> Unit,
    val onImportHighlights: () -> Unit,
    val onExportHighlights: () -> Unit,
    val onRestoreBuiltInHighlights: () -> Unit,
    val onDeleteHighlights: () -> Unit,
    val onToggleHighlightSelection: (String) -> Unit,
    val onToggleAllHighlightSelection: () -> Unit,
    val onCancelHighlightSelection: () -> Unit,
    val onConfirmHighlightSelection: () -> Unit,
    val onBack: () -> Unit,
    val onPresetNameChanged: (String) -> Unit,
    val onTextColorChanged: (Int) -> Unit,
    val onBackgroundColorChanged: (Int) -> Unit,
    val onTextAccentColorChanged: (Int) -> Unit,
    val onResetEditorColor: () -> Unit,
    val onBackgroundAlphaChanged: (Int) -> Unit,
    val onSelectBackgroundImage: () -> Unit,
    val onSelectBackground: (Int, String) -> Unit,
    val onFloatingColorSourceChanged: (Boolean) -> Unit,
    val onPickFloatingColor: () -> Unit,
    val onFloatingTransparencyChanged: (Int) -> Unit,
    val onFloatingPrimaryStrengthChanged: (Int) -> Unit,
    val onFloatingColorStyleChanged: (ReadFloatingColorStyle) -> Unit,
    val onFloatingAppearanceChangeFinished: () -> Unit,
    val onFullLineUnderlineEnabledChanged: (Boolean) -> Unit,
    val onFullLineUnderlineDashedChanged: (Boolean) -> Unit,
    val onFullLineUnderlineColorChanged: (Int) -> Unit,
    val onFullLineUnderlineWidthChanged: (Int) -> Unit,
    val onFullLineUnderlineOffsetChanged: (Int) -> Unit,
    val onFullLineUnderlineExtendChanged: (Boolean) -> Unit,
    val onFullLineUnderlineDashLengthChanged: (Float) -> Unit,
    val onFullLineUnderlineGapLengthChanged: (Float) -> Unit,
    val onFontWeight: () -> Unit,
    val onFont: () -> Unit,
    val onIndent: () -> Unit,
    val onChineseConverter: () -> Unit,
    val onPadding: () -> Unit,
    val onTip: () -> Unit,
    val onTextSizeChanged: (Int) -> Unit,
    val onLetterSpacingChanged: (Float) -> Unit,
    val onLineSpacingChanged: (Int) -> Unit,
    val onParagraphSpacingChanged: (Int) -> Unit,
    val onPageAnimChanged: (Int) -> Unit,
    val onCreateHighlight: () -> Unit,
    val onEditHighlight: (Int) -> Unit,
    val onHighlightDraftChanged: (ReadHighlightRule) -> Unit,
    val onHighlightColorModeChanged: (Int) -> Unit,
    val onSelectHighlightBackground: () -> Unit,
    val onClearHighlightBackground: () -> Unit,
    val onSelectHighlightFont: () -> Unit,
    val onClearHighlightFont: () -> Unit,
    val onSaveHighlight: () -> Unit,
    val onDeleteHighlight: () -> Unit,
    val onHighlightEnabledChanged: (Int, Boolean) -> Unit,
    val onReorderHighlights: (List<ReadHighlightRule>) -> Unit,
)

@Composable
internal fun ReadStyleScreen(
    page: ReadStylePage,
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val indicatorColor = Color(NgTheme.colors.primary)
    val selectedContentColor = Color(NgTheme.colors.onPrimary)
    val rootPages = setOf(
        ReadStylePage.PRESET,
        ReadStylePage.ADJUST,
        ReadStylePage.HIGHLIGHT,
    )
    BackHandler(
        enabled = page !in rootPages || state.highlightSelectionMode != HighlightSelectionMode.NONE,
    ) {
        actions.onBack()
    }
    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            if (page in rootPages) {
                ReadStyleDock(
                    labels = listOf(
                        stringResource(R.string.read_style_tab_preset),
                        stringResource(R.string.read_style_tab_adjust),
                        stringResource(R.string.read_style_tab_highlight),
                    ),
                    selectedIndex = when (page) {
                        ReadStylePage.PRESET -> 0
                        ReadStylePage.ADJUST -> 1
                        else -> 2
                    },
                    contentColor = contentColor,
                    selectedContainerColor = indicatorColor,
                    selectedContentColor = selectedContentColor,
                    onSelected = { index ->
                        actions.onPageSelected(
                            when (index) {
                                0 -> ReadStylePage.PRESET
                                1 -> ReadStylePage.ADJUST
                                else -> ReadStylePage.HIGHLIGHT
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            when (page) {
                ReadStylePage.PRESET -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StandardPageHeight),
                ) {
                    PresetPage(
                        state = state,
                        contentColor = contentColor,
                        accentColor = indicatorColor,
                        actions = actions,
                    )
                }

                ReadStylePage.ADJUST -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StandardPageHeight)
                        .padding(top = 12.dp),
                ) {
                    AdjustPage(
                        state = state,
                        contentColor = contentColor,
                        accentColor = indicatorColor,
                        selectedContentColor = selectedContentColor,
                        actions = actions,
                    )
                }

                ReadStylePage.HIGHLIGHT -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StandardPageHeight)
                        .padding(top = 8.dp),
                ) {
                    HighlightPage(
                        state = state,
                        contentColor = contentColor,
                        accentColor = indicatorColor,
                        actions = actions,
                    )
                }

                ReadStylePage.EDIT -> EditorPage(
                    state = state,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    actions = actions,
                )

                ReadStylePage.EDIT_TEXT_COLOR,
                ReadStylePage.EDIT_BACKGROUND_COLOR,
                ReadStylePage.EDIT_ACCENT_COLOR -> EditorColorPage(
                    page = page,
                    state = state,
                    actions = actions,
                )

                ReadStylePage.EDIT_UNDERLINE -> FullLineUnderlinePage(
                    state = state,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    actions = actions,
                )

                ReadStylePage.EDIT_UNDERLINE_COLOR -> AdvancedColorPage(
                    title = stringResource(R.string.read_style_full_underline_color),
                    initialColor = state.editorInitialColor ?: state.fullLineUnderline.color,
                    onBack = actions.onBack,
                    onColorChanged = actions.onFullLineUnderlineColorChanged,
                    onReset = actions.onResetEditorColor,
                )

                ReadStylePage.HIGHLIGHT_EDIT -> HighlightRuleEditorPage(
                    state = state,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    actions = actions,
                )

                ReadStylePage.HIGHLIGHT_TEXT_COLOR,
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> HighlightRuleColorPage(
                    page = page,
                    state = state,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun PresetPage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val density = LocalDensity.current
    val presetListState = rememberLazyListState(
        initialFirstVisibleItemScrollOffset = with(density) {
            PresetInitialScrollOffset.roundToPx()
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reading_presets),
            color = contentColor,
            fontSize = 17.sp,
        )
    }

    LazyRow(
        state = presetListState,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = state.presets,
            key = { _, item -> item.index },
        ) { _, item ->
            PresetCard(
                name = item.name,
                textColor = Color(item.textColor),
                background = item.background,
                selected = item.index == state.selectedPresetIndex,
                accentColor = accentColor,
                onClick = { actions.onSelectPreset(item.index) },
            )
        }
    }

    PresetManagementDock(
        contentColor = contentColor,
        onCreate = actions.onCreatePreset,
        onEdit = actions.onEditPreset,
        onImport = actions.onImportPreset,
        onExport = actions.onExportPreset,
        onDelete = actions.onDeletePreset,
    )

    PresetSwitchRow(
        title = stringResource(R.string.share_layout),
        iconRes = R.drawable.ic_ai_capability_text,
        checked = state.shareLayout,
        contentColor = contentColor,
        onCheckedChange = actions.onShareLayoutChanged,
    )
    ReadDivider(contentColor)
    PresetSwitchRow(
        title = stringResource(R.string.read_style_global_follow_app_color),
        iconRes = R.drawable.ic_cfg_theme,
        checked = state.globalFloatingFollowApp,
        contentColor = contentColor,
        onCheckedChange = actions.onGlobalFloatingFollowAppChanged,
    )
    ReadDivider(contentColor)
    PresetRestoreAllRow(
        contentColor = contentColor,
        onClick = actions.onRestoreAllPresets,
    )
}

@Composable
private fun PresetCard(
    name: String,
    textColor: Color,
    background: ImageBitmap?,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.13f))
            .border(
                width = if (selected) 1.2.dp else 0.7.dp,
                color = if (selected) accentColor else textColor.copy(alpha = 0.38f),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (background != null) {
            Image(
                bitmap = background,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PresetManagementDock(
    contentColor: Color,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PresetVisibleHorizontalInset, vertical = 6.dp)
            .height(60.dp)
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.24f))
            .border(0.7.dp, contentColor.copy(alpha = 0.10f), shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetAction(
            iconRes = R.drawable.ic_add,
            label = stringResource(R.string.create),
            contentColor = contentColor,
            onClick = onCreate,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_edit,
            label = stringResource(R.string.edit),
            contentColor = contentColor,
            onClick = onEdit,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_import,
            label = stringResource(R.string.import_str),
            contentColor = contentColor,
            onClick = onImport,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_export,
            label = stringResource(R.string.export_str),
            contentColor = contentColor,
            onClick = onExport,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_book_info_delete,
            label = stringResource(R.string.delete),
            contentColor = contentColor,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PresetAction(
    iconRes: Int,
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val displayedColor = contentColor.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = displayedColor,
        )
        Spacer(Modifier.height(3.dp))
        Text(text = label, color = displayedColor, fontSize = 12.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun PresetSwitchRow(
    title: String,
    iconRes: Int,
    checked: Boolean,
    contentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(25.dp),
            tint = contentColor,
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
            color = contentColor,
            fontSize = 15.sp,
        )
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}

@Composable
private fun PresetRestoreAllRow(
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_restore),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Text(
            text = stringResource(R.string.read_style_restore_all),
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            color = contentColor,
            fontSize = 14.sp,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = contentColor.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun EditorPage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val indicatorColor = Color(NgTheme.colors.primary)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorPageHeight),
    ) {
        EditorHeader(
            mode = state.editorMode,
            modeLabel = state.editorModeLabel,
            contentColor = contentColor,
            accentColor = accentColor,
            onBack = actions.onBack,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 8.dp,
            ),
        ) {
            item {
                EditorNameRow(
                    name = state.selectedPresetName,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onNameChanged = actions.onPresetNameChanged,
                )
                ReadDivider(contentColor, horizontalPadding = 0.dp)
                EditorNavigationRow(
                    title = stringResource(R.string.read_style_full_underline),
                    summary = if (state.fullLineUnderline.enabled) {
                        if (state.fullLineUnderline.dashed) {
                            stringResource(R.string.read_style_underline_dashed)
                        } else {
                            stringResource(R.string.read_style_underline_solid)
                        }
                    } else {
                        stringResource(R.string.close)
                    },
                    color = contentColor,
                    onClick = { actions.onPageSelected(ReadStylePage.EDIT_UNDERLINE) },
                )
                ReadDivider(contentColor, horizontalPadding = 0.dp)
                EditorSectionLabel(stringResource(R.string.read_style_section_colors), accentColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorColorTile(
                        label = stringResource(R.string.read_style_color_text_short),
                        color = Color(state.editorTextColor),
                        contentColor = contentColor,
                        onClick = { actions.onPageSelected(ReadStylePage.EDIT_TEXT_COLOR) },
                        modifier = Modifier.weight(1f),
                    )
                    EditorColorTile(
                        label = stringResource(R.string.read_style_color_background_short),
                        color = Color(state.editorBackgroundColor),
                        contentColor = contentColor,
                        onClick = { actions.onPageSelected(ReadStylePage.EDIT_BACKGROUND_COLOR) },
                        modifier = Modifier.weight(1f),
                    )
                    EditorColorTile(
                        label = stringResource(R.string.read_style_color_accent_short),
                        color = Color(state.editorTextAccentColor),
                        contentColor = contentColor,
                        onClick = { actions.onPageSelected(ReadStylePage.EDIT_ACCENT_COLOR) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                ReadDivider(contentColor, horizontalPadding = 0.dp)
                EditorSectionLabel(stringResource(R.string.background), accentColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bg_alpha),
                        modifier = Modifier.weight(1f),
                        color = contentColor,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "${state.editorBackgroundAlpha}%",
                        color = contentColor.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                    )
                }
                NgSlider(
                    value = state.editorBackgroundAlpha.toFloat(),
                    onValueChange = { actions.onBackgroundAlphaChanged(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 99,
                    variant = NgSliderVariant.COMPACT,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.bg_image),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    color = contentColor,
                    fontSize = 15.sp,
                )
            }
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val tileWidth = (maxWidth - 25.dp) / 5f
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp),
                        horizontalArrangement = Arrangement.spacedBy(BackgroundTileSpacing),
                    ) {
                        item(key = "custom") {
                            EditorBackgroundTile(
                                label = stringResource(R.string.select_image),
                                background = null,
                                selected = false,
                                accentColor = indicatorColor,
                                contentColor = contentColor,
                                tileWidth = tileWidth,
                                onClick = actions.onSelectBackgroundImage,
                            )
                        }
                        items(
                            items = state.editorBackgrounds,
                            key = { item -> "${item.type}:${item.name}" },
                        ) { item ->
                            EditorBackgroundTile(
                                label = item.label,
                                background = item.background,
                                selected = state.editorBackgroundType == item.type &&
                                    state.editorBackgroundName == item.name,
                                accentColor = indicatorColor,
                                contentColor = contentColor,
                                tileWidth = tileWidth,
                                onClick = { actions.onSelectBackground(item.type, item.name) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                ReadDivider(contentColor, horizontalPadding = 0.dp)
                EditorSectionLabel(
                    stringResource(R.string.read_style_floating_section),
                    accentColor,
                )
                EditorFloatingSection(
                    state = state,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    indicatorColor = indicatorColor,
                    actions = actions,
                )
                Spacer(Modifier.height(8.dp))
                ReadDivider(contentColor, horizontalPadding = 0.dp)
                EditorNavigationRow(
                    title = stringResource(R.string.read_style_restore_current),
                    summary = stringResource(
                        if (state.canRestoreCurrentDefault) {
                            R.string.read_style_restore_current_summary
                        } else {
                            R.string.read_style_restore_unavailable
                        }
                    ),
                    color = contentColor,
                    enabled = state.canRestoreCurrentDefault,
                    onClick = actions.onRestoreCurrentPreset,
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(
    mode: Int,
    modeLabel: String,
    contentColor: Color,
    accentColor: Color,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
        Text(
            text = stringResource(R.string.read_style_edit_title),
            modifier = Modifier.padding(start = 2.dp),
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(NgTheme.colors.surface).copy(alpha = 0.34f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = modeLabel,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = when (mode) {
                    1 -> Icons.Rounded.DarkMode
                    else -> Icons.Rounded.LightMode
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = accentColor,
            )
        }
    }
}

@Composable
private fun EditorNameRow(
    name: String,
    contentColor: Color,
    accentColor: Color,
    onNameChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.read_style_name),
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 15.sp,
        )
        BasicTextField(
            value = name,
            onValueChange = onNameChanged,
            modifier = Modifier.width(120.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = contentColor.copy(alpha = 0.78f),
                fontSize = 14.sp,
                textAlign = TextAlign.End,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            cursorBrush = SolidColor(accentColor),
        )
    }
}

@Composable
private fun EditorNavigationRow(
    title: String,
    summary: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val rowColor = if (enabled) color else color.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = rowColor,
            fontSize = 15.sp,
        )
        Text(
            text = summary,
            color = rowColor.copy(alpha = if (enabled) 0.62f else 0.72f),
            fontSize = 13.sp,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
            tint = rowColor.copy(alpha = if (enabled) 0.72f else 0.55f),
        )
    }
}

@Composable
private fun EditorSectionLabel(label: String, accentColor: Color) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp, bottom = 9.dp),
        color = accentColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun EditorFloatingSection(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    indicatorColor: Color,
    actions: ReadStyleActions,
) {
    val selectedContentColor = Color(NgTheme.colors.onPrimary)
    val sourceShape = RoundedCornerShape(10.dp)
    val globallyFollowsApplication = state.globalFloatingFollowApp
    val fromBackground = !globallyFollowsApplication && state.editorFloatingColorFromBackground
    val hasSample = state.editorFloatingColorSeed != 0
    val displayedColor = if (hasSample) {
        state.editorFloatingColorSeed
    } else {
        NgTheme.colors.surfaceTint
    }
    val displayedColorHex = remember(displayedColor) {
        "#%06X".format(displayedColor and 0x00FFFFFF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(sourceShape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.20f))
            .border(0.7.dp, contentColor.copy(alpha = 0.10f), sourceShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FloatingSourceOption(
            label = stringResource(R.string.read_style_floating_color_follow),
            selected = !fromBackground,
            contentColor = contentColor,
            selectedContainerColor = indicatorColor,
            selectedContentColor = selectedContentColor,
            onClick = { actions.onFloatingColorSourceChanged(false) },
            modifier = Modifier.weight(1f),
            enabled = !globallyFollowsApplication,
        )
        FloatingSourceOption(
            label = stringResource(R.string.read_style_floating_color_background),
            selected = fromBackground,
            contentColor = contentColor,
            selectedContainerColor = indicatorColor,
            selectedContentColor = selectedContentColor,
            onClick = { actions.onFloatingColorSourceChanged(true) },
            modifier = Modifier.weight(1f),
            enabled = !globallyFollowsApplication,
        )
    }

    if (globallyFollowsApplication) {
        Text(
            text = stringResource(R.string.read_style_global_follow_app_color_managed),
            modifier = Modifier.padding(top = 6.dp),
            color = contentColor.copy(alpha = 0.62f),
            fontSize = 11.sp,
        )
    }

    Text(
        text = stringResource(
            if (globallyFollowsApplication) R.string.read_style_global_color_style
            else R.string.read_style_floating_color_style
        ),
        modifier = Modifier.padding(top = 12.dp, bottom = 7.dp),
        color = contentColor,
        fontSize = 15.sp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sourceShape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.20f))
            .border(0.7.dp, contentColor.copy(alpha = 0.10f), sourceShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
            ReadFloatingColorStyle.VIBRANT to R.string.ng_palette_vibrant,
            ReadFloatingColorStyle.EXPRESSIVE to R.string.ng_palette_expressive,
            ReadFloatingColorStyle.RAINBOW to R.string.ng_palette_rainbow,
            ReadFloatingColorStyle.FRUIT_SALAD to R.string.ng_palette_fruit_salad,
        ).forEach { (style, labelRes) ->
            FloatingSourceOption(
                label = stringResource(labelRes),
                selected = state.editorFloatingColorStyle == style,
                contentColor = contentColor,
                selectedContainerColor = indicatorColor,
                selectedContentColor = selectedContentColor,
                onClick = { actions.onFloatingColorStyleChanged(style) },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
            )
        }
    }

    if (fromBackground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(role = Role.Button, onClick = actions.onPickFloatingColor),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.read_style_floating_current_color),
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = 15.sp,
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color(displayedColor), CircleShape)
                    .border(1.dp, contentColor.copy(alpha = 0.28f), CircleShape),
            )
            Text(
                text = displayedColorHex,
                modifier = Modifier
                    .padding(start = 10.dp),
                color = contentColor.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp),
                tint = contentColor.copy(alpha = 0.72f),
            )
        }
    }

    EditorFloatingSlider(
        title = stringResource(R.string.read_floating_window_transparency),
        value = state.editorFloatingTransparency,
        contentColor = contentColor,
        accentColor = accentColor,
        onValueChanged = actions.onFloatingTransparencyChanged,
        onValueChangeFinished = actions.onFloatingAppearanceChangeFinished,
    )
    EditorFloatingSlider(
        title = stringResource(R.string.read_floating_window_primary_strength),
        value = state.editorFloatingPrimaryStrength,
        contentColor = contentColor,
        accentColor = accentColor,
        onValueChanged = actions.onFloatingPrimaryStrengthChanged,
        onValueChangeFinished = actions.onFloatingAppearanceChangeFinished,
    )
}

@Composable
private fun FloatingSourceOption(
    label: String,
    selected: Boolean,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(selectedContainerColor) else Modifier
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = (if (selected) selectedContentColor else contentColor).copy(
                alpha = if (enabled || selected) 1f else 0.42f
            ),
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun EditorFloatingSlider(
    title: String,
    value: Int,
    contentColor: Color,
    accentColor: Color,
    onValueChanged: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 14.sp,
        )
        Text(
            text = "$value%",
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    NgSlider(
        value = value.toFloat(),
        onValueChange = {
            onValueChanged(
                it.toInt().coerceIn(
                    ReadFloatingAppearanceConfig.MIN_PERCENT,
                    ReadFloatingAppearanceConfig.MAX_PERCENT,
                )
            )
        },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = ReadFloatingAppearanceConfig.MIN_PERCENT.toFloat()..
            ReadFloatingAppearanceConfig.MAX_PERCENT.toFloat(),
        steps = 99,
        variant = NgSliderVariant.COMPACT,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditorColorTile(
    label: String,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.22f))
            .border(0.7.dp, contentColor.copy(alpha = 0.12f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
                .border(0.7.dp, contentColor.copy(alpha = 0.18f), CircleShape),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            color = contentColor,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun EditorBackgroundTile(
    label: String,
    background: ImageBitmap?,
    selected: Boolean,
    accentColor: Color,
    contentColor: Color,
    tileWidth: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(tileWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .size(width = tileWidth, height = 56.dp)
                .clip(shape)
                .background(Color(NgTheme.colors.surface).copy(alpha = 0.20f))
                .border(
                    width = if (selected) 1.4.dp else 0.7.dp,
                    color = if (selected) accentColor else contentColor.copy(alpha = 0.14f),
                    shape = shape,
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (background != null) {
                Image(
                    bitmap = background,
                    contentDescription = label,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor.copy(alpha = 0.72f),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (accentColor.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            }
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 4.dp),
            color = contentColor.copy(alpha = 0.74f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EditorColorPage(
    page: ReadStylePage,
    state: ReadStyleUiState,
    actions: ReadStyleActions,
) {
    val title: String
    val currentColor: Int
    val onColorChanged: (Int) -> Unit
    when (page) {
        ReadStylePage.EDIT_TEXT_COLOR -> {
            title = stringResource(R.string.text_color)
            currentColor = state.editorTextColor
            onColorChanged = actions.onTextColorChanged
        }

        ReadStylePage.EDIT_BACKGROUND_COLOR -> {
            title = stringResource(R.string.bg_color)
            currentColor = state.editorBackgroundColor
            onColorChanged = actions.onBackgroundColorChanged
        }

        else -> {
            title = stringResource(R.string.text_accent_color)
            currentColor = state.editorTextAccentColor
            onColorChanged = actions.onTextAccentColorChanged
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorPageHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        NgInlineColorPicker(
            title = title,
            initialColor = state.editorInitialColor ?: currentColor,
            onBack = actions.onBack,
            onColorChanged = onColorChanged,
            onReset = actions.onResetEditorColor,
        )
    }
}

@Composable
private fun AdjustPage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    selectedContentColor: Color,
    actions: ReadStyleActions,
) {
    val shortcuts = listOf(
        ReadStyleShortcut(
            titleRes = R.string.font_weight,
            iconText = "T",
            iconTextSize = 20.sp,
            onClick = actions.onFontWeight,
        ),
        ReadStyleShortcut(
            titleRes = R.string.text_font,
            iconText = "Aa",
            iconTextSize = 20.sp,
            onClick = actions.onFont,
        ),
        ReadStyleShortcut(
            titleRes = R.string.text_indent,
            iconText = "⇄",
            iconTextSize = 20.sp,
            iconOffsetY = (-2).dp,
            onClick = actions.onIndent,
        ),
        ReadStyleShortcut(
            titleRes = R.string.chinese_converter_shortcut,
            iconRes = R.drawable.ic_translate,
            onClick = actions.onChineseConverter,
        ),
        ReadStyleShortcut(
            titleRes = R.string.padding,
            iconRes = R.drawable.ic_fullscreen,
            onClick = actions.onPadding,
        ),
        ReadStyleShortcut(
            titleRes = R.string.information,
            iconRes = R.drawable.ic_cfg_about,
            onClick = actions.onTip,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shortcuts.forEach { shortcut ->
            ShortcutCard(
                title = stringResource(shortcut.titleRes),
                iconRes = shortcut.iconRes,
                iconText = shortcut.iconText,
                iconTextSize = shortcut.iconTextSize,
                iconOffsetY = shortcut.iconOffsetY,
                contentColor = contentColor,
                onClick = shortcut.onClick,
                modifier = Modifier.weight(1f),
            )
        }
    }

    AdjustSliderRow(
        title = stringResource(R.string.text_size),
        valueText = state.textSize.toString(),
        value = state.textSize.toFloat(),
        valueRange = 5f..50f,
        steps = 44,
        contentColor = contentColor,
        onValueChange = { actions.onTextSizeChanged(it.toInt()) },
    )
    AdjustSliderRow(
        title = stringResource(R.string.text_letter_spacing),
        valueText = state.letterSpacing.toString(),
        value = state.letterSpacing,
        valueRange = -0.5f..0.5f,
        steps = 99,
        contentColor = contentColor,
        onValueChange = actions.onLetterSpacingChanged,
    )
    AdjustSliderRow(
        title = stringResource(R.string.line_size),
        valueText = ((state.lineSpacingExtra - 10) / 10f).toString(),
        value = state.lineSpacingExtra.toFloat(),
        valueRange = 0f..20f,
        steps = 19,
        contentColor = contentColor,
        onValueChange = { actions.onLineSpacingChanged(it.toInt()) },
    )
    AdjustSliderRow(
        title = stringResource(R.string.paragraph_size),
        valueText = (state.paragraphSpacing / 10f).toString(),
        value = state.paragraphSpacing.toFloat(),
        valueRange = 0f..20f,
        steps = 19,
        contentColor = contentColor,
        onValueChange = { actions.onParagraphSpacingChanged(it.toInt()) },
    )

    Text(
        text = stringResource(R.string.page_anim),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        color = contentColor,
        fontSize = 14.sp,
    )
    ReadStyleDock(
        labels = listOf(
            stringResource(R.string.page_anim_cover),
            stringResource(R.string.page_anim_slide),
            stringResource(R.string.page_anim_simulation),
            stringResource(R.string.page_anim_scroll),
            stringResource(R.string.page_anim_none),
        ),
        selectedIndex = state.pageAnim.coerceIn(0, 4),
        contentColor = contentColor,
        selectedContainerColor = accentColor,
        selectedContentColor = selectedContentColor,
        onSelected = actions.onPageAnimChanged,
        height = 42.dp,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ShortcutCard(
    title: String,
    iconRes: Int?,
    iconText: String?,
    iconTextSize: androidx.compose.ui.unit.TextUnit,
    iconOffsetY: Dp,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.13f))
            .border(0.6.dp, contentColor.copy(alpha = 0.18f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 24.dp)
                .offset(y = iconOffsetY),
            contentAlignment = Alignment.Center,
        ) {
            if (iconText != null) {
                Text(
                    text = iconText,
                    color = contentColor,
                    fontSize = iconTextSize,
                    lineHeight = 22.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    painter = painterResource(requireNotNull(iconRes)),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = title,
            color = contentColor,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AdjustSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    contentColor: Color,
    onValueChange: (Float) -> Unit,
) {
    val currentValue = value.coerceIn(valueRange)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.width(52.dp),
            color = contentColor,
            fontSize = 15.sp,
            maxLines = 1,
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_reduce,
            contentDescription = "$title，${stringResource(R.string.reduce)}",
            enabled = currentValue > valueRange.start,
            onClick = {
                onValueChange(ngSliderStepValue(currentValue, valueRange, steps, -1))
            },
            tint = contentColor,
        )
        NgSlider(
            value = currentValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            variant = NgSliderVariant.COMPACT,
            modifier = Modifier.weight(1f),
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_add,
            contentDescription = "$title，${stringResource(R.string.plus)}",
            enabled = currentValue < valueRange.endInclusive,
            onClick = {
                onValueChange(ngSliderStepValue(currentValue, valueRange, steps, 1))
            },
            tint = contentColor,
        )
        Text(
            text = valueText,
            modifier = Modifier.width(44.dp),
            color = contentColor,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun HighlightPage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val selectionMode = state.highlightSelectionMode
    val selecting = selectionMode != HighlightSelectionMode.NONE
    var orderedRules by remember(state.highlightRules) { mutableStateOf(state.highlightRules) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (!selecting && from.index in orderedRules.indices && to.index in orderedRules.indices) {
            orderedRules = orderedRules.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    }
    val allSelected = orderedRules.isNotEmpty() &&
        orderedRules.all { it.id in state.selectedHighlightIds }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (selecting) R.string.read_highlight_select_rules
                    else R.string.read_highlight_rules
                ),
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (selecting) {
                    stringResource(
                        R.string.read_highlight_selected_summary,
                        state.selectedHighlightIds.size,
                        orderedRules.size,
                    )
                } else {
                    state.highlightSummary
                },
                color = contentColor.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }

        if (selecting) {
            HighlightSelectionDock(
                mode = selectionMode,
                selectedCount = state.selectedHighlightIds.size,
                allSelected = allSelected,
                contentColor = contentColor,
                accentColor = accentColor,
                onCancel = actions.onCancelHighlightSelection,
                onToggleAll = actions.onToggleAllHighlightSelection,
                onConfirm = actions.onConfirmHighlightSelection,
            )
        } else {
            HighlightManagementDock(
                contentColor = contentColor,
                hasRules = orderedRules.isNotEmpty(),
                onCreate = actions.onCreateHighlight,
                onImport = actions.onImportHighlights,
                onExport = actions.onExportHighlights,
                onRestore = actions.onRestoreBuiltInHighlights,
                onDelete = actions.onDeleteHighlights,
            )
        }

        if (orderedRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.empty),
                    color = contentColor.copy(alpha = 0.62f),
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                state = listState,
            ) {
                itemsIndexed(
                    items = orderedRules,
                    key = { _, item -> item.id },
                ) { index, item ->
                    ReorderableItem(
                        state = reorderState,
                        key = item.id,
                    ) { isDragging ->
                        val sortDescription = stringResource(R.string.sort)
                        HighlightRuleRow(
                            item = item,
                            contentColor = contentColor,
                            selected = item.id in state.selectedHighlightIds,
                            selectionMode = selecting,
                            isDragging = isDragging,
                            dragHandleModifier = if (!selecting && orderedRules.size > 1) {
                                Modifier.draggableHandle(
                                    onDragStopped = {
                                        actions.onReorderHighlights(orderedRules)
                                    },
                                )
                            } else {
                                Modifier
                            },
                            sortDescription = sortDescription,
                            onClick = {
                                if (selecting) actions.onToggleHighlightSelection(item.id)
                                else actions.onEditHighlight(index)
                            },
                            onSelectedChanged = {
                                actions.onToggleHighlightSelection(item.id)
                            },
                            onEnabledChanged = { enabled ->
                                actions.onHighlightEnabledChanged(index, enabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightManagementDock(
    contentColor: Color,
    hasRules: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PresetVisibleHorizontalInset, vertical = 4.dp)
            .height(56.dp)
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.24f))
            .border(0.7.dp, contentColor.copy(alpha = 0.10f), shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetAction(
            iconRes = R.drawable.ic_add,
            label = stringResource(R.string.create),
            contentColor = contentColor,
            onClick = onCreate,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_import,
            label = stringResource(R.string.import_str),
            contentColor = contentColor,
            onClick = onImport,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_export,
            label = stringResource(R.string.export_str),
            contentColor = contentColor,
            onClick = onExport,
            modifier = Modifier.weight(1f),
            enabled = hasRules,
        )
        PresetAction(
            iconRes = R.drawable.ic_restore,
            label = stringResource(R.string.menu_restore),
            contentColor = contentColor,
            onClick = onRestore,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_book_info_delete,
            label = stringResource(R.string.delete),
            contentColor = contentColor,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            enabled = hasRules,
        )
    }
}

@Composable
private fun HighlightSelectionDock(
    mode: HighlightSelectionMode,
    selectedCount: Int,
    allSelected: Boolean,
    contentColor: Color,
    accentColor: Color,
    onCancel: () -> Unit,
    onToggleAll: () -> Unit,
    onConfirm: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PresetVisibleHorizontalInset, vertical = 4.dp)
            .height(56.dp)
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.24f))
            .border(0.7.dp, contentColor.copy(alpha = 0.10f), shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetAction(
            iconRes = R.drawable.ic_back,
            label = stringResource(R.string.cancel),
            contentColor = contentColor,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = R.drawable.ic_select_all,
            label = stringResource(
                if (allSelected) R.string.unselect_all else R.string.select_all
            ),
            contentColor = contentColor,
            onClick = onToggleAll,
            modifier = Modifier.weight(1f),
        )
        PresetAction(
            iconRes = if (mode == HighlightSelectionMode.DELETE) {
                R.drawable.ic_book_info_delete
            } else {
                R.drawable.ic_export
            },
            label = stringResource(
                if (mode == HighlightSelectionMode.DELETE) R.string.delete
                else R.string.export_str
            ),
            contentColor = if (mode == HighlightSelectionMode.DELETE) accentColor else contentColor,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            enabled = selectedCount > 0,
        )
    }
}

@Composable
private fun HighlightRuleRow(
    item: ReadHighlightRule,
    contentColor: Color,
    selected: Boolean,
    selectionMode: Boolean,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    sortDescription: String,
    onClick: () -> Unit,
    onSelectedChanged: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val itemHeight = 60.dp
    val shape = RoundedCornerShape(12.dp)
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 0.dp,
        label = "highlightRuleDragElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "highlightRuleDragScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isDragging) {
                    Modifier
                        .shadow(elevation, shape, clip = false)
                        .clip(shape)
                        .background(Color(NgTheme.colors.surface).copy(alpha = 0.96f))
                        .border(0.8.dp, contentColor.copy(alpha = 0.18f), shape)
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clickable(enabled = !isDragging, role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectedChanged() },
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = sortDescription,
                    modifier = Modifier
                        .size(40.dp)
                        .then(dragHandleModifier)
                        .padding(9.dp),
                    tint = contentColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank {
                        stringResource(R.string.highlight_rule_default_name)
                    },
                    color = contentColor,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.sampleText.ifBlank { item.pattern },
                    color = contentColor.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selectionMode) {
                NgSwitchControl(
                    checked = item.enabled,
                    onCheckedChange = onEnabledChanged,
                    modifier = Modifier.size(width = 52.dp, height = 36.dp),
                )
            }
        }
        if (!isDragging) {
            ReadDivider(contentColor, horizontalPadding = 0.dp)
        } else {
            Spacer(Modifier.height(0.8.dp))
        }
    }
}

@Composable
private fun ReadStyleDock(
    labels: List<String>,
    selectedIndex: Int,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
) {
    val shape = RoundedCornerShape(12.dp)
    val dockSurfaceColor = ReadDrawerStyle.dockSurfaceColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(dockSurfaceColor)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex.coerceIn(labels.indices)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) Modifier.background(selectedContainerColor)
                        else Modifier
                    )
                    .clickable(role = Role.Tab) { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) selectedContentColor else contentColor,
                    fontSize = fontSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReadDivider(contentColor: Color, horizontalPadding: Dp = 16.dp) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(0.8.dp)
            .background(contentColor.copy(alpha = 0.12f)),
    )
}
