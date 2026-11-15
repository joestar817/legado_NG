package io.legado.app.ui.book.read.config

import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image as OutlinedImage
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import io.legado.app.data.entities.Book
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.help.config.EffectiveReadFloatingColor
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.font.SYSTEM_DEFAULT_FONT
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormPanelVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.components.compose.ngSliderStepValue
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PresetGridColumns = 4
private const val PresetGridActionCount = 2
private val PresetGridCardHeight = 56.dp
private val PresetGridRowSpacing = 14.dp
private val SettingIconSize = 18.dp
private val SettingIconSlotSize = 22.dp
private val SettingIconTextSpacing = 4.dp

internal fun presetGridViewportHeight(presetCount: Int, availableHeight: Dp): Dp {
    // 新建和导入占位也占格子，满一行后才需要增加下一行的高度。
    val rowCount = ((presetCount.coerceAtLeast(0) + PresetGridActionCount + PresetGridColumns - 1) /
        PresetGridColumns).coerceAtLeast(1)
    val rowsThatFit = ((availableHeight + PresetGridRowSpacing) /
        (PresetGridCardHeight + PresetGridRowSpacing)).toInt()
    if (rowsThatFit < 1) return availableHeight.coerceAtLeast(0.dp)
    val visibleRows = rowCount.coerceAtMost(rowsThatFit)
    return PresetGridCardHeight * visibleRows + PresetGridRowSpacing * (visibleRows - 1)
}

/** 与 Context.isPad 同一判断：大屏编辑页按内容收高，手机仍固定高度。 */
private fun isLargeScreen(configuration: Configuration): Boolean {
    return configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >=
        Configuration.SCREENLAYOUT_SIZE_LARGE
}

private val PresetVisibleHorizontalInset = 6.dp
private val BackgroundTileSpacing = 6.dp

internal enum class ReadStylePage {
    PRESET,
    APPEARANCE,
    ADJUST,
    TITLE,
    PAGE,
    HEADER_FOOTER,
    FLOATING,
    FLOATING_COLOR,
    FLOATING_TOOLBAR,
    FLOATING_SIDE_TOOLBAR,
    PRESET_MANAGE,
    EDIT,
    EDIT_TEXT_COLOR,
    EDIT_BACKGROUND_COLOR,
    EDIT_ACCENT_COLOR,
    EDIT_UNDERLINE,
    EDIT_UNDERLINE_COLOR,
    HIGHLIGHT,
    HIGHLIGHT_LINK,
    HIGHLIGHT_EDIT,
    HIGHLIGHT_NINE_SLICE,
    HIGHLIGHT_TEXT_COLOR,
    HIGHLIGHT_BACKGROUND_COLOR,
    HIGHLIGHT_UNDERLINE_COLOR,
}

internal enum class HighlightSelectionMode { NONE, MULTI }

internal enum class HighlightBatchAction { EXPORT, DELETE }

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
    val stableId: Long,
    val name: String,
    val textColor: Int,
    val background: ImageBitmap?,
    val canRestoreDefault: Boolean = false,
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
    val creatingPreset: Boolean = false,
    val presets: List<ReadStylePresetUi>,
    val selectedPresetIndex: Int,
    val selectedPresetName: String,
    val canRestoreCurrentDefault: Boolean,
    val highlightSummary: String,
    val isEpub: Boolean,
    val onlyThisBook: Boolean,
    val canUseBookStyle: Boolean,
    val shareLayout: Boolean,
    val globalFloatingFollowApp: Boolean,
    val textSize: Int,
    val textWeight: Int,
    val letterSpacing: Float,
    val lineSpacingExtra: Int,
    val paragraphSpacing: Int,
    val paragraphIndentLevel: Int,
    val pageAnim: Int,
    val highlightRules: List<ReadHighlightRule>,
    val highlightSelectionMode: HighlightSelectionMode,
    val selectedHighlightIds: Set<String>,
    val editorMode: Int,
    val allowEInkEditing: Boolean,
    val colorPreviewTarget: ReadStyleEditTarget,
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
    val editorFloatingColorIsCustom: Boolean,
    val floatingDayColor: EffectiveReadFloatingColor,
    val floatingNightColor: EffectiveReadFloatingColor,
    val editorFloatingTransparency: Int,
    val editorFloatingPrimaryStrength: Int,
    val editorFloatingColorStyle: ReadFloatingColorStyle,
    val fullLineUnderline: FullLineUnderlineUiState,
    val highlightDraft: ReadHighlightRule?,
    val highlightColorMode: Int,
    val editingHighlightIndex: Int?,
    val editorInitialColor: Int?,
    val editorInitialColorWasUnset: Boolean,
    val editorInitialBackgroundType: Int?,
    val editorInitialBackgroundName: String?,
    val editorInitialBackground: ImageBitmap?,
    val bodyFontLabel: String = "",
    val bodyPadding: ReadBodyPadding = ReadBodyPadding(),
    val linkedHighlightRuleIds: List<String> = emptyList(),
    val useLinkedHighlightRules: Boolean = false,
    val builtInHighlightRuleIds: Set<String> = emptySet(),
    val epubBook: Book? = null,
    val floatingStandalone: Boolean = false,
    val floatingTab: FloatingSettingsTab = FloatingSettingsTab.COLOR,
    val floatingCatalogStyle: String = "card",
    val floatingShowToolbar: Boolean = true,
    val floatingShowChapterProgress: Boolean = true,
    val floatingProgressBehavior: String = "page",
)

internal fun ReadStyleUiState.withHighlightColorMode(mode: Int): ReadStyleUiState =
    copy(highlightColorMode = mode.coerceIn(0, 1))

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
    val onEpubStyleChanged: (Book) -> Unit,
    val onOnlyThisBookChanged: (Boolean) -> Unit,
    val onShareLayoutChanged: (Boolean) -> Unit,
    val onGlobalFloatingFollowAppChanged: (Boolean) -> Unit,
    val onImportHighlights: () -> Unit,
    val onBeginHighlightSelection: () -> Unit,
    val onRestoreBuiltInHighlights: () -> Unit,
    val onToggleHighlightSelection: (String) -> Unit,
    val onToggleAllHighlightSelection: () -> Unit,
    val onCancelHighlightSelection: () -> Unit,
    val onConfirmHighlightSelection: (HighlightBatchAction) -> Unit,
    val onBack: () -> Unit,
    val onPresetNameChanged: (String) -> Unit,
    val onTextColorChanged: (Int) -> Unit,
    val onBackgroundColorChanged: (Int) -> Unit,
    val onTextAccentColorChanged: (Int) -> Unit,
    val onEditorModeChanged: (Int) -> Unit,
    val onResetEditorColor: () -> Unit,
    val onBackgroundAlphaChanged: (Int) -> Unit,
    val onSelectBackgroundImage: () -> Unit,
    val onSelectBackground: (Int, String) -> Unit,
    val onPickFloatingColor: () -> Unit,
    val onFloatingColorChanged: (Int) -> Unit,
    val onRestoreFloatingOriginal: () -> Unit,
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
    val onTitleFont: () -> Unit,
    val onTitleColor: (Boolean) -> Unit,
    val onTitleChanged: () -> Unit,
    val onIndent: () -> Unit,
    val onFontWeightChanged: (Int) -> Unit,
    val onIndentChanged: (Int) -> Unit,
    val onChineseConverter: () -> Unit,
    val onPadding: () -> Unit,
    val onTip: () -> Unit,
    val onTipColorChanged: () -> Unit,
    val onTipTextColor: () -> Unit,
    val onTipDividerColor: () -> Unit,
    val onTextSizeChanged: (Int) -> Unit,
    val onLetterSpacingChanged: (Float) -> Unit,
    val onLineSpacingChanged: (Int) -> Unit,
    val onParagraphSpacingChanged: (Int) -> Unit,
    val onPageAnimChanged: (Int) -> Unit,
    val onCreateHighlight: () -> Unit,
    val onEditHighlight: (Int) -> Unit,
    val onCopyHighlight: (String) -> Unit,
    val onExportHighlight: (String) -> Unit,
    val onRestoreHighlight: (String) -> Unit,
    val onDeleteHighlightById: (String) -> Unit,
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
    val onLinkedHighlightModeChanged: (Boolean) -> Unit = {},
    val onLinkedHighlightRuleChanged: (String, Boolean) -> Unit = { _, _ -> },
    val onHeader: () -> Unit = {},
    val onFooter: () -> Unit = {},
    val onEditPresetAt: (Int) -> Unit = {},
    val onExportPresetAt: (Int) -> Unit = {},
    val onDeletePresetAt: (Int) -> Unit = {},
    val onCopyPresetAt: (Int) -> Unit = {},
    val onRestorePresetAt: (Int) -> Unit = {},
    val onFloatingCatalogStyleChanged: (String) -> Unit = {},
    val onFloatingToolbarChanged: (Boolean) -> Unit = {},
    val onFloatingChapterProgressChanged: (Boolean) -> Unit = {},
    val onFloatingProgressBehaviorChanged: (String) -> Unit = {},
    val onFloatingToolbarOrder: () -> Unit = {},
    val onFloatingSideToolOrder: () -> Unit = {},
    val onFloatingSideToolsChanged: () -> Unit = {},
    val floatingSideToolCapacity: () -> Int,
    val onFloatingTabChanged: (FloatingSettingsTab) -> Unit = {},
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
    val titleScroll = rememberScrollState()
    val rootPages = setOf(
        ReadStylePage.PRESET,
        ReadStylePage.APPEARANCE,
        ReadStylePage.ADJUST,
        ReadStylePage.TITLE,
        ReadStylePage.PAGE,
        ReadStylePage.HEADER_FOOTER,
        ReadStylePage.HIGHLIGHT,
    )
    BackHandler(
        enabled = page !in rootPages || state.highlightSelectionMode != HighlightSelectionMode.NONE,
        onBack = actions.onBack,
    )
    if (page == ReadStylePage.FLOATING_TOOLBAR) {
        Box(Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp * 0.75f)) {
            TextSelectionActionOrderPage(onBack = actions.onBack)
        }
        return
    }
    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(
            topStart = NgDrawerAppearanceConfig.DEFAULT_CORNER_RADIUS_DP.dp,
            topEnd = NgDrawerAppearanceConfig.DEFAULT_CORNER_RADIUS_DP.dp,
        ),
        style = readFloatingGlassStyle(),
    ) {
        if (page in rootPages) {
            ReadStyleRoot(
                page = page,
                state = state,
                contentColor = contentColor,
                accentColor = indicatorColor,
                actions = actions,
                titleScroll = titleScroll,
            )
        } else {
            when (page) {
                ReadStylePage.PRESET_MANAGE -> PresetManagementPage(state, contentColor, actions)
                ReadStylePage.FLOATING -> FloatingSettingsPage(state, contentColor, actions)
                ReadStylePage.FLOATING_SIDE_TOOLBAR -> ReadStylePageWithDragHandle {
                    ReadFloatingToolOrderPage(
                        onBack = actions.onBack,
                        onChanged = actions.onFloatingSideToolsChanged,
                        maxEnabled = actions.floatingSideToolCapacity,
                    )
                }
                ReadStylePage.FLOATING_COLOR -> ReadStylePageWithDragHandle {
                    AdvancedColorPage(
                        title = state.editorModeLabel + " · " + stringResource(R.string.read_style_floating_section),
                        initialColor = state.editorInitialColor ?: state.editorFloatingColorSeed,
                        onBack = actions.onBack,
                        onColorChanged = actions.onFloatingColorChanged,
                        onReset = actions.onResetEditorColor,
                        backgroundRenderer = state.colorPreviewTarget::renderBackground,
                        showAlphaSlider = false,
                        forceOpaque = true,
                        onRestoreOriginal = actions.onRestoreFloatingOriginal,
                    )
                }
                ReadStylePage.HIGHLIGHT_LINK -> HighlightLinkPage(state, contentColor, actions)

                ReadStylePage.EDIT -> PresetMetadataPage(
                    state = state,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    actions = actions,
                )

                ReadStylePage.EDIT_TEXT_COLOR,
                ReadStylePage.EDIT_BACKGROUND_COLOR,
                ReadStylePage.EDIT_ACCENT_COLOR -> ReadStylePageWithDragHandle {
                    EditorColorPage(page = page, state = state, actions = actions)
                }

                ReadStylePage.EDIT_UNDERLINE -> ReadStylePageWithDragHandle {
                    FullLineUnderlinePage(state = state, contentColor = contentColor, accentColor = accentColor, actions = actions)
                }

                ReadStylePage.EDIT_UNDERLINE_COLOR -> ReadStylePageWithDragHandle {
                    AdvancedColorPage(
                        title = (if (state.editorMode == 2) {
                            stringResource(R.string.read_style_mode_day) + " · " + stringResource(R.string.read_style_shared_short)
                        } else state.editorModeLabel) + " · " + stringResource(R.string.read_style_full_underline_color),
                        initialColor = state.editorInitialColor ?: state.fullLineUnderline.color,
                        onBack = actions.onBack,
                        onColorChanged = actions.onFullLineUnderlineColorChanged,
                        onReset = actions.onResetEditorColor,
                        backgroundRenderer = state.colorPreviewTarget::renderBackground,
                    )
                }

                ReadStylePage.HIGHLIGHT_EDIT -> ReadStylePageWithDragHandle {
                    HighlightRuleEditorPage(state = state, contentColor = contentColor, accentColor = accentColor, actions = actions)
                }

                ReadStylePage.HIGHLIGHT_NINE_SLICE -> ReadStylePageWithDragHandle {
                    ReadHighlightNineSlicePage(state = state, contentColor = contentColor, accentColor = accentColor, actions = actions)
                }

                ReadStylePage.HIGHLIGHT_TEXT_COLOR,
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> ReadStylePageWithDragHandle {
                    HighlightRuleColorPage(page = page, state = state, actions = actions)
                }

                ReadStylePage.PRESET, ReadStylePage.APPEARANCE, ReadStylePage.ADJUST,
                ReadStylePage.TITLE, ReadStylePage.PAGE, ReadStylePage.HEADER_FOOTER,
                ReadStylePage.HIGHLIGHT, ReadStylePage.FLOATING_TOOLBAR -> Unit
            }
        }
    }
}

@Composable
private fun ReadStylePageWithDragHandle(content: @Composable () -> Unit) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.95f
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        NgDrawerDragHandle(modifier = Modifier.padding(top = 6.dp), variant = NgDrawerDragHandleVariant.COMPACT)
        content()
    }
}

@Composable
private fun ReadStyleRoot(
    page: ReadStylePage,
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
    titleScroll: ScrollState,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val layoutPages = listOf(ReadStylePage.ADJUST, ReadStylePage.TITLE, ReadStylePage.PAGE, ReadStylePage.HEADER_FOOTER)
    val maxHeight = if (page == ReadStylePage.ADJUST) {
        // Leave enough room for the full underline row while keeping the reading preview visible.
        screenHeight * 0.58f
    } else if (page == ReadStylePage.TITLE) {
        // Inline segmentation controls can expand the title card without hiding them.
        screenHeight * 0.85f
    } else if (page == ReadStylePage.PAGE) {
        // Show the EPUB controls inline while keeping a scroll fallback on short screens.
        screenHeight * 0.85f
    } else if (page in layoutPages) {
        screenHeight * 0.565f
    } else if (page == ReadStylePage.PRESET) {
        screenHeight * 0.9f
    } else {
        val originalMaxHeight = screenHeight * if (screenHeight < 600.dp) 0.85f else 0.67f
        (originalMaxHeight + 42.dp).coerceAtMost(screenHeight * 0.9f)
    }
    val pages = listOf(
        ReadStylePage.PRESET, ReadStylePage.APPEARANCE, ReadStylePage.ADJUST, ReadStylePage.HIGHLIGHT,
    )
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxHeight)
            .padding(bottom = if (page == ReadStylePage.PRESET) 20.dp else 10.dp),
    ) {
        ReadStyleDock(
            labels = listOf("预设", "外观", "排版", "高亮"),
            selectedIndex = if (page in layoutPages) 2 else pages.indexOf(page).coerceAtLeast(0),
            contentColor = contentColor,
            selectedContainerColor = accentColor,
            selectedContentColor = Color(NgTheme.colors.onPrimary),
            onSelected = { actions.onPageSelected(pages[it]) },
            height = 42.dp, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
        )
        if (page in layoutPages) {
            Spacer(Modifier.height(8.dp))
            ReadLayoutDock(page, state.isEpub, contentColor, accentColor) { actions.onPageSelected(it) }
        }
        when (page) {
            ReadStylePage.PRESET -> PresetPage(state, contentColor, accentColor, actions, Modifier.weight(1f, fill = false))
            ReadStylePage.APPEARANCE -> AppearancePage(state, contentColor, accentColor, actions, Modifier.weight(1f, fill = false))
            ReadStylePage.ADJUST -> TypographyPage(state, contentColor, actions, Modifier.weight(1f, fill = false))
            ReadStylePage.TITLE -> TitleLayoutPage(contentColor, actions, titleScroll, Modifier.weight(1f, fill = false))
            ReadStylePage.PAGE -> PageLayoutPage(state, actions, Modifier.weight(1f, fill = false))
            ReadStylePage.HEADER_FOOTER -> HeaderFooterLayoutPage(contentColor, actions, Modifier.weight(1f, fill = false))
            ReadStylePage.HIGHLIGHT -> HighlightHomePage(state, contentColor, accentColor, actions, Modifier.weight(1f, fill = false))
            else -> Unit
        }
    }
}

@Composable
internal fun ReadLayoutDock(
    page: ReadStylePage, isEpub: Boolean, contentColor: Color, accentColor: Color,
    onSelect: (ReadStylePage) -> Unit,
) {
    val pages = if (isEpub) {
        listOf(ReadStylePage.ADJUST, ReadStylePage.TITLE, ReadStylePage.PAGE, ReadStylePage.HEADER_FOOTER)
    } else {
        listOf(ReadStylePage.ADJUST, ReadStylePage.TITLE, ReadStylePage.HEADER_FOOTER)
    }
    val labels = if (isEpub) listOf("正文", "标题", "EPUB", "页眉页脚")
        else listOf("正文", "标题", "页眉页脚")
    val icons = if (isEpub) {
        listOf(R.drawable.ic_chapter_list, 0, R.drawable.ic_read_layout_page, R.drawable.ic_read_layout_header_footer)
    } else {
        listOf(R.drawable.ic_chapter_list, 0, R.drawable.ic_read_layout_header_footer)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp)
            .clip(RoundedCornerShape(12.dp)).background(ngDrawerContentCardColor()).padding(3.dp),
    ) {
        pages.forEachIndexed { index, target ->
            val selected = page == target
            val tint = if (selected) accentColor else contentColor.copy(alpha = 0.65f)
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .then(if (selected) Modifier.background(accentColor.copy(alpha = 0.18f)) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(target) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                if (index == 1) {
                    Text("T", color = tint, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 19.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                } else {
                    Icon(painterResource(icons[index]), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.height(1.dp))
                Text(labels[index], color = tint, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
    }
}

@Composable
private fun PresetPicker(state: ReadStyleUiState, accentColor: Color, actions: ReadStyleActions) {
    val listState = rememberLazyListState()
    val bookPosition = state.presets.indexOfFirst { it.index == -1 }
    LaunchedEffect(state.onlyThisBook, bookPosition) {
        if (state.onlyThisBook && bookPosition >= 0) listState.animateScrollToItem(bookPosition)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = 8.dp
        val width = (maxWidth - spacing * 6) / 5
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(state.presets, key = { _, item -> item.stableId }) { _, item ->
                PresetCard(
                    name = item.name, width = width, textColor = Color(item.textColor),
                    background = item.background, selected = item.index == state.selectedPresetIndex,
                    accentColor = accentColor, onClick = { actions.onSelectPreset(item.index) },
                )
            }
        }
    }
}

@Composable
private fun AppearancePage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        ReadStylePaletteModeSelector(
            mode = state.editorMode, allowEInk = state.allowEInkEditing,
            contentColor = contentColor, onSelected = actions.onEditorModeChanged,
        )
        Spacer(Modifier.height(10.dp))
        NgFormGroup(title = stringResource(R.string.background)) {
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow(
                    title = "阅读背景", color = contentColor,
                    leadingIcon = Icons.Outlined.OutlinedImage,
                    trailingContent = {
                        Box(
                            Modifier.size(width = 70.dp, height = 36.dp)
                                .clip(RoundedCornerShape(7.dp)).background(Color(state.editorBackgroundColor)),
                            contentAlignment = Alignment.Center,
                        ) {
                            state.editorPreviewBackground?.let {
                                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    },
                    onClick = actions.onSelectBackgroundImage,
                )
            }
            NgFormGroupDivider()
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppearanceSettingIcon(Icons.Outlined.Opacity)
                Text(stringResource(R.string.bg_alpha), color = contentColor, fontSize = 14.sp)
                NgSlider(
                    value = state.editorBackgroundAlpha.toFloat(),
                    onValueChange = { actions.onBackgroundAlphaChanged(it.toInt()) },
                    valueRange = 0f..100f, steps = 99, variant = NgSliderVariant.COMPACT,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("${state.editorBackgroundAlpha}%", color = contentColor.copy(alpha = 0.72f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        NgFormGroup(title = stringResource(R.string.read_style_section_colors)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                PresetColorChoice(stringResource(R.string.read_style_color_text_short), Color(state.editorTextColor), contentColor,
                    { actions.onPageSelected(ReadStylePage.EDIT_TEXT_COLOR) }, Modifier.weight(1f))
                Box(Modifier.height(32.dp).width(0.5.dp).background(contentColor.copy(alpha = 0.10f)))
                PresetColorChoice(stringResource(R.string.read_style_color_background_short), Color(state.editorBackgroundColor), contentColor,
                    { actions.onPageSelected(ReadStylePage.EDIT_BACKGROUND_COLOR) }, Modifier.weight(1f))
                Box(Modifier.height(32.dp).width(0.5.dp).background(contentColor.copy(alpha = 0.10f)))
                PresetColorChoice(stringResource(R.string.read_style_color_accent_short), Color(state.editorTextAccentColor), contentColor,
                    { actions.onPageSelected(ReadStylePage.EDIT_ACCENT_COLOR) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        NgFormGroup(title = stringResource(R.string.read_style_floating_section)) {
            Box(Modifier.padding(horizontal = 14.dp)) {
                val source = when {
                    state.editorMode == 2 -> "墨水屏"
                    state.globalFloatingFollowApp || !state.editorFloatingColorIsCustom ->
                        stringResource(R.string.read_style_floating_color_follow)
                    else -> stringResource(R.string.read_style_floating_color_custom)
                }
                EditorNavigationRow(stringResource(R.string.read_floating_appearance), source, contentColor,
                    leadingIcon = Icons.Outlined.Palette,
                    onClick = { actions.onPageSelected(ReadStylePage.FLOATING) })
            }
        }
    }
}

@Composable
private fun readStyleSelectionContent(): Color {
    val primary = Color(NgTheme.colors.primary)
    val container = Color(NgTheme.colors.primaryContainer)
    val lighter = maxOf(primary.luminance(), container.luminance())
    val darker = minOf(primary.luminance(), container.luminance())
    return if ((lighter + 0.05f) / (darker + 0.05f) >= 4.5f) primary
    else Color(NgTheme.colors.onPrimaryContainer)
}

@Composable
internal fun ReadStylePaletteModeSelector(
    mode: Int,
    allowEInk: Boolean,
    contentColor: Color,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.read_style_mode_day),
        stringResource(R.string.read_style_mode_night),
    ) + if (allowEInk) listOf(stringResource(R.string.read_style_mode_eink)) else emptyList()
    ReadStyleModeSelector(
        labels = labels,
        selectedIndex = mode,
        contentColor = contentColor,
        enabled = enabled,
        icons = listOf(R.drawable.ic_daytime, R.drawable.ic_brightness),
        onSelected = onSelected,
    )
}

internal enum class ReadStyleModeSelectorVariant {
    DEFAULT,
    PRIMARY_SELECTION,
}

@Composable
internal fun ReadStyleModeSelector(
    labels: List<String>,
    selectedIndex: Int,
    contentColor: Color,
    enabled: Boolean = true,
    icons: List<Int> = emptyList(),
    variant: ReadStyleModeSelectorVariant = ReadStyleModeSelectorVariant.DEFAULT,
    onSelected: (Int) -> Unit,
) {
    val selectionColor = when (variant) {
        ReadStyleModeSelectorVariant.DEFAULT -> readStyleSelectionContent()
        ReadStyleModeSelectorVariant.PRIMARY_SELECTION -> Color(NgTheme.colors.onPrimary)
    }
    val selectedContainerColor = when (variant) {
        ReadStyleModeSelectorVariant.DEFAULT -> Color(NgTheme.colors.primaryContainer)
        ReadStyleModeSelectorVariant.PRIMARY_SELECTION -> Color(NgTheme.colors.primary)
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp)
            .clip(RoundedCornerShape(12.dp)).background(ngDrawerContentCardColor()).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                    .background(if (selected) selectedContainerColor else Color.Transparent)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                val labelColor = if (selected) selectionColor else contentColor
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    icons.getOrNull(index)?.let { icon ->
                        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = labelColor)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(label, color = labelColor,
                        fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FloatingAppearanceGroups(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val globallyFollowsApplication = state.globalFloatingFollowApp
    val inkMode = state.editorMode == 2
    val selectedContainer = Color(NgTheme.colors.primaryContainer)
    val selectedContent = readStyleSelectionContent()
    val sourceTitle = when {
        inkMode -> stringResource(R.string.read_style_floating_ink_no_seed)
        globallyFollowsApplication -> stringResource(R.string.read_style_floating_section) + " · " +
            stringResource(R.string.read_style_follow_global)
        else -> stringResource(R.string.read_style_floating_section)
    }
    NgFormGroup(sourceTitle) {
        if (inkMode || globallyFollowsApplication) {
            Text(
                text = stringResource(if (inkMode) R.string.read_style_floating_ink_no_seed else R.string.read_style_global_follow_app_color_managed),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = contentColor.copy(alpha = 0.62f), fontSize = 11.sp,
            )
        } else {
            FloatingCurrentColorRow(state, contentColor, actions.onPickFloatingColor)
        }
    }
    Spacer(Modifier.height(14.dp))
    NgFormGroup(stringResource(if (globallyFollowsApplication) R.string.read_style_global_color_style else R.string.read_style_floating_color_style)
        + " · " + stringResource(R.string.read_style_shared_short)) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(4.dp)) {
            listOf(
                ReadFloatingColorStyle.VIBRANT to R.string.ng_palette_vibrant,
                ReadFloatingColorStyle.EXPRESSIVE to R.string.ng_palette_expressive,
                ReadFloatingColorStyle.RAINBOW to R.string.ng_palette_rainbow,
                ReadFloatingColorStyle.FRUIT_SALAD to R.string.ng_palette_fruit_salad,
            ).forEach { (style, labelRes) ->
                FloatingSourceOption(stringResource(labelRes), state.editorFloatingColorStyle == style,
                    contentColor, selectedContainer, selectedContent, { actions.onFloatingColorStyleChanged(style) },
                    Modifier.weight(1f), fontSize = 13.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    NgFormGroup("浮窗效果 · ${stringResource(R.string.read_style_shared_short)}") {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            EditorFloatingSlider(stringResource(R.string.read_floating_window_transparency), state.editorFloatingTransparency,
                contentColor, accentColor, actions.onFloatingTransparencyChanged, actions.onFloatingAppearanceChangeFinished)
            EditorFloatingSlider(stringResource(R.string.read_floating_window_primary_strength), state.editorFloatingPrimaryStrength,
                contentColor, accentColor, actions.onFloatingPrimaryStrengthChanged, actions.onFloatingAppearanceChangeFinished)
        }
    }
}

@Composable
private fun FloatingCurrentColorRow(state: ReadStyleUiState, contentColor: Color, onClick: () -> Unit) {
    val displayedColor = floatingColorPreviewSeed(
        state.editorFloatingColorSeed, ReadAppearanceMode.fromIndex(state.editorMode),
    )
    Row(Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.read_style_floating_current_color), Modifier.weight(1f), color = contentColor, fontSize = 15.sp)
        Box(Modifier.size(26.dp).background(Color(displayedColor), CircleShape)
            .border(0.7.dp, contentColor.copy(alpha = 0.24f), CircleShape))
        Text("#%06X".format(displayedColor and 0x00FFFFFF), Modifier.padding(start = 10.dp),
            color = contentColor.copy(alpha = 0.72f), fontSize = 13.sp)
        Icon(painterResource(R.drawable.ic_chevron_right_20), null,
            Modifier.padding(start = 6.dp).size(18.dp), tint = contentColor.copy(alpha = 0.65f))
    }
}

@Composable
private fun PresetColorChoice(label: String, color: Color, contentColor: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.height(62.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(color)
            .border(0.5.dp, contentColor.copy(alpha = 0.2f), RoundedCornerShape(7.dp)))
        Spacer(Modifier.height(4.dp))
        Text(label, color = contentColor, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun TypographyPage(
    state: ReadStyleUiState, contentColor: Color, actions: ReadStyleActions, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp)) {
        NgFormGroup("文字与段落") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow("正文字体", state.bodyFontLabel.ifBlank { "系统字体" }, contentColor, rowHeight = 48.dp, onClick = actions.onFont)
            }
            NgFormGroupDivider()
            BoxWithConstraints(Modifier.padding(horizontal = 14.dp)) {
                val padding = state.bodyPadding
                val summary = listOf(
                    "${stringResource(R.string.top)}${padding.top}",
                    "${stringResource(R.string.bottom)}${padding.bottom}",
                    "${stringResource(R.string.left)}${padding.left}",
                    "${stringResource(R.string.right)}${padding.right}",
                ).joinToString("  ")
                EditorNavigationRow(
                    title = stringResource(R.string.read_style_body_margins),
                    color = contentColor,
                    trailingContent = {
                        Text(
                            text = summary,
                            modifier = Modifier.widthIn(max = maxWidth * 0.55f),
                            color = contentColor.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    rowHeight = 48.dp,
                    onClick = actions.onPadding,
                )
            }
            NgFormGroupDivider()
            ReadTypographyControls(
                textSize = state.textSize,
                letterSpacing = state.letterSpacing,
                lineSpacingExtra = state.lineSpacingExtra,
                paragraphSpacing = state.paragraphSpacing,
                paragraphIndentLevel = state.paragraphIndentLevel,
                textWeight = state.textWeight,
                contentColor = contentColor,
                onTextSizeChanged = actions.onTextSizeChanged,
                onLetterSpacingChanged = actions.onLetterSpacingChanged,
                onLineSpacingChanged = actions.onLineSpacingChanged,
                onParagraphSpacingChanged = actions.onParagraphSpacingChanged,
                onParagraphIndentChanged = actions.onIndentChanged,
                onTextWeightChanged = actions.onFontWeightChanged,
            )
        }
        Spacer(Modifier.height(10.dp))
        NgFormPanel {
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow(
                    title = stringResource(R.string.read_style_full_underline),
                    summary = if (state.fullLineUnderline.enabled) "开启" else "关闭",
                    color = contentColor, rowHeight = 48.dp,
                    onClick = { actions.onPageSelected(ReadStylePage.EDIT_UNDERLINE) },
                )
            }
        }
    }
}

@Composable
private fun TitleLayoutPage(
    contentColor: Color,
    actions: ReadStyleActions,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
) {
    val config = ReadBookConfig.config
    var revision by remember(config) { mutableIntStateOf(0) }
    revision
    var alignmentExpanded by remember { mutableStateOf(false) }
    fun update(change: ReadBookConfig.Config.() -> Unit) {
        config.change()
        revision++
        actions.onTitleChanged()
    }
    val alignmentOptions = listOf(
        ReadMoreConfigOption("0", stringResource(R.string.title_left)),
        ReadMoreConfigOption("1", stringResource(R.string.title_center)),
        ReadMoreConfigOption("2", stringResource(R.string.title_hide)),
    )
    val weight = when (config.titleBold) {
        0 -> 400
        1 -> 900
        2 -> 300
        else -> config.titleBold.coerceIn(100, 900)
    }
    val fontLabel = when (val path = config.titleFont) {
        SYSTEM_DEFAULT_FONT -> stringArrayResource(R.array.system_typefaces).first()
        "" -> stringResource(R.string.read_title_follow_body_font)
        else -> Uri.decode(path.substringAfterLast('/').substringAfterLast(':'))
    }
    val titleMetricLabelWidth = readTypographyMetricLabelWidth(listOf("字号", "字重", "上间距", "下间距"))
    val lineSpacing = TypographyItem(
        stringResource(R.string.read_title_line_spacing), ReadTypographyMetric.TITLE_LINE_SPACING,
        config.titleLineSpacingExtra,
    ) { value -> update { titleLineSpacingExtra = value } }
    Column(modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp).padding(top = 8.dp)) {
        NgFormGroup("标题样式") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow(
                    title = stringResource(R.string.read_title_alignment),
                    summary = alignmentOptions.getOrNull(config.titleMode)?.label ?: alignmentOptions.first().label,
                    color = contentColor, rowHeight = 48.dp, onClick = { alignmentExpanded = true },
                )
                Box(Modifier.align(Alignment.CenterEnd).width(1.dp).height(48.dp)) {
                    ReadChoicePopup(
                        expanded = alignmentExpanded, options = alignmentOptions,
                        selectedValue = config.titleMode.toString(),
                        onDismissRequest = { alignmentExpanded = false },
                        onSelected = { choice ->
                            alignmentExpanded = false
                            choice.toIntOrNull()?.takeIf { it in 0..2 }?.let { value -> update { titleMode = value } }
                        },
                        preferPopupBelow = true, scrollable = true, variant = ReadChoicePopupVariant.ADAPTIVE,
                        menuWidth = 100.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
                    )
                }
            }
            NgFormGroupDivider()
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow(stringResource(R.string.read_title_font), fontLabel, contentColor,
                    rowHeight = 48.dp, onClick = actions.onTitleFont)
            }
            NgFormGroupDivider()
            ReadTypographyMetricGrid(
                listOf(
                    TypographyItem("字号", ReadTypographyMetric.TITLE_SIZE, config.titleSize) { value -> update { titleSize = value } },
                    TypographyItem("字重", ReadTypographyMetric.TITLE_WEIGHT, weight) { value -> update { titleBold = value } },
                    TypographyItem("上间距", ReadTypographyMetric.TITLE_SPACING, config.titleTopSpacing) { value -> update { titleTopSpacing = value } },
                    TypographyItem("下间距", ReadTypographyMetric.TITLE_SPACING, config.titleBottomSpacing) { value -> update { titleBottomSpacing = value } },
                ), contentColor, endPadding = 5.dp,
            )
            NgFormGroupDivider()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp)) {
                val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                val choiceWidth = 46.dp * fontScale
                val colorWidth = 60.dp * fontScale + choiceWidth * 2
                val colorColumnWidth = ((maxWidth - 10.dp) / 2).coerceAtLeast(0.dp)
                if (colorColumnWidth < colorWidth) {
                    Column {
                        TitleColorSettingRow(config, contentColor, choiceWidth, actions.onTitleColor)
                        NgFormGroupDivider(horizontalPadding = 0.dp)
                        ReadTypographyStandaloneMetric(lineSpacing, titleMetricLabelWidth, contentColor,
                            Modifier.fillMaxWidth())
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(colorColumnWidth)) {
                            TitleColorSettingRow(config, contentColor, choiceWidth, actions.onTitleColor)
                        }
                        Box(Modifier.width(1.dp).height(25.dp).background(contentColor.copy(alpha = 0.12f)))
                        Box(Modifier.weight(1f).padding(start = 4.dp)) {
                            ReadTypographyStandaloneMetric(lineSpacing, titleMetricLabelWidth, contentColor,
                                Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            NgFormGroupDivider()
            ReadTitleSegmentationSetting(
                config = config,
                revision = revision,
                contentColor = contentColor,
                onChanged = {
                    revision++
                    actions.onTitleChanged()
                },
            )
        }
    }
}

@Composable
private fun TitleColorSettingRow(
    config: ReadBookConfig.Config,
    contentColor: Color,
    choiceWidth: Dp,
    onSelect: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.read_title_color_short), Modifier.weight(1f), color = contentColor,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        TitleColorChoice(
            stringResource(R.string.read_title_day_short), stringResource(R.string.read_title_color_day),
            readTitleColorPreview(config, night = false), contentColor, choiceWidth,
        ) { onSelect(false) }
        TitleColorChoice(
            stringResource(R.string.read_title_night_short), stringResource(R.string.read_title_color_night),
            readTitleColorPreview(config, night = true), contentColor, choiceWidth,
        ) { onSelect(true) }
    }
}

@Composable
private fun TitleColorChoice(
    label: String,
    description: String,
    color: Int,
    contentColor: Color,
    width: Dp,
    onClick: () -> Unit,
) {
    Row(
        Modifier.width(width).fillMaxHeight().clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = contentColor.copy(alpha = 0.65f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1)
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(Color(color))
                .border(0.7.dp, contentColor.copy(alpha = 0.25f), CircleShape)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadTypographyExpandableSetting(
    title: String,
    summary: String,
    value: Int,
    range: IntRange,
    steps: Int,
    contentColor: Color,
    onValueChanged: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bringSliderIntoView = remember { BringIntoViewRequester() }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 14.dp)) {
            EditorNavigationRow(
                title = title,
                summary = summary,
                color = contentColor,
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
        }
        AnimatedVisibility(visible = expanded) {
            NgSlider(
                value = value.toFloat(),
                onValueChange = { newValue ->
                    val rounded = newValue.roundToInt().coerceIn(range)
                    if (rounded != value) onValueChanged(rounded)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = steps,
                variant = NgSliderVariant.DISCRETE,
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                    .bringIntoViewRequester(bringSliderIntoView)
                    .semantics { contentDescription = title },
            )
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(250)
            bringSliderIntoView.bringIntoView()
        }
    }
}

@Composable
private fun PageLayoutPage(
    state: ReadStyleUiState, actions: ReadStyleActions, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        state.epubBook?.let { book ->
            EpubLayoutSettings(book = book, onStyleChanged = { actions.onEpubStyleChanged(book) })
        }
    }
}

@Composable
private fun HeaderFooterLayoutPage(contentColor: Color, actions: ReadStyleActions, modifier: Modifier = Modifier) {
    val config = ReadBookConfig.config
    var revision by remember(config) { mutableIntStateOf(0) }
    revision

    fun changed() {
        revision++
        actions.onTipColorChanged()
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        NgFormGroup("页眉页脚") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow("页眉", color = contentColor,
                    leadingIconRes = R.drawable.ic_read_header_setting,
                    onClick = actions.onHeader)
            }
            NgFormGroupDivider()
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNavigationRow("页脚", color = contentColor,
                    leadingIconRes = R.drawable.ic_read_footer_setting,
                    onClick = actions.onFooter)
            }
            NgFormGroupDivider()
            ReadTipColorSettings(
                textColor = config.tipColor,
                dividerColor = config.tipDividerColor,
                contentColor = contentColor,
                onFollowTextColor = {
                    config.tipColor = 0
                    changed()
                },
                onDividerModeChanged = { value ->
                    config.tipDividerColor = value
                    changed()
                },
                onPickTextColor = actions.onTipTextColor,
                onPickDividerColor = actions.onTipDividerColor,
            )
        }
    }
}

@Composable
internal fun ReadStyleSubpageHeader(title: String, contentColor: Color, onBack: () -> Unit,
    circularBack: Boolean = false, centerTitle: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (circularBack) {
            NgThemeSheetActionButton(onClick = onBack, contentDescription = stringResource(R.string.back)) {
                Icon(painterResource(R.drawable.ic_arrow_back), null, tint = contentColor, modifier = Modifier.size(22.dp))
            }
        } else {
            Box(Modifier.size(40.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), tint = contentColor, modifier = Modifier.size(22.dp))
            }
        }
        if (centerTitle) {
            Text(title, Modifier.weight(1f).padding(end = if (circularBack) 44.dp else 40.dp),
                color = contentColor, fontSize = 17.sp, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(title, Modifier.weight(1f), color = contentColor, fontSize = 17.sp)
        }
        actions()
    }
}

@Composable
private fun PresetMetadataPage(state: ReadStyleUiState, contentColor: Color, accentColor: Color, actions: ReadStyleActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
        ReadStyleSubpageHeader(if (state.creatingPreset) "新建预设" else "编辑预设", contentColor, actions.onBack)
        NgFormGroup("预设信息", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(Modifier.padding(horizontal = 14.dp)) {
                EditorNameRow(state.selectedPresetName, contentColor, accentColor, actions.onPresetNameChanged)
            }
        }
    }
}

@Composable
private fun PresetManagementPage(state: ReadStyleUiState, contentColor: Color, actions: ReadStyleActions) {
    var moreExpanded by remember { mutableStateOf(false) }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.72f
    Column(Modifier.fillMaxWidth().heightIn(max = height).padding(bottom = 10.dp)) {
        NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
        ReadStyleSubpageHeader("预设管理", contentColor, actions.onBack) {
            Text("新建", Modifier.clickable(onClick = actions.onCreatePreset).padding(horizontal = 7.dp, vertical = 12.dp), color = contentColor, fontSize = 13.sp)
            Text("导入", Modifier.clickable(onClick = actions.onImportPreset).padding(horizontal = 7.dp, vertical = 12.dp), color = contentColor, fontSize = 13.sp)
            Box {
                Text("更多", Modifier.clickable { moreExpanded = true }.padding(horizontal = 7.dp, vertical = 12.dp), color = contentColor, fontSize = 13.sp)
                DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                    DropdownMenuItem(text = { Text("恢复当前预设") }, enabled = state.canRestoreCurrentDefault,
                        onClick = { moreExpanded = false; actions.onRestoreCurrentPreset() })
                    if (!state.onlyThisBook) DropdownMenuItem(text = { Text("清空并恢复预设") },
                        onClick = { moreExpanded = false; actions.onRestoreAllPresets() })
                }
            }
        }
        Text("我的预设", Modifier.padding(start = 26.dp, top = 8.dp, bottom = 6.dp), color = Color(NgTheme.colors.primary), fontSize = 14.sp)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 16.dp),
        ) {
            itemsIndexed(state.presets, key = { _, item -> item.stableId }) { index, item ->
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) 12.dp else 0.dp,
                    topEnd = if (index == 0) 12.dp else 0.dp,
                    bottomStart = if (index == state.presets.lastIndex) 12.dp else 0.dp,
                    bottomEnd = if (index == state.presets.lastIndex) 12.dp else 0.dp,
                )
                Column(Modifier.fillMaxWidth().clip(shape).background(ngDrawerContentCardColor())) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clickable { actions.onSelectPreset(item.index) }.padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(item.textColor).copy(alpha = 0.12f))) {
                            item.background?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Text(item.name, Modifier.weight(1f).padding(start = 12.dp), color = contentColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.index == state.selectedPresetIndex) Text("使用中", color = Color(NgTheme.colors.primary), fontSize = 12.sp)
                        if (!state.onlyThisBook || item.index == -1) {
                            var menu by remember(item.name, item.index) { mutableStateOf(false) }
                            Box {
                                Text("⋯", Modifier.clickable { menu = true }.padding(horizontal = 12.dp, vertical = 8.dp), color = contentColor, fontSize = 20.sp)
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; actions.onEditPresetAt(item.index) })
                                    if (!state.onlyThisBook) DropdownMenuItem(text = { Text("复制") },
                                        onClick = { menu = false; actions.onCopyPresetAt(item.index) })
                                    DropdownMenuItem(text = { Text("导出") }, onClick = { menu = false; actions.onExportPresetAt(item.index) })
                                    DropdownMenuItem(text = { Text(if (item.index == -1) "跟随全局" else "删除") },
                                        onClick = { menu = false; actions.onDeletePresetAt(item.index) })
                                }
                            }
                        }
                    }
                    if (index != state.presets.lastIndex) NgFormGroupDivider()
                }
            }
        }
    }
}

@Composable
private fun HighlightHomePage(
    state: ReadStyleUiState, contentColor: Color, accentColor: Color,
    actions: ReadStyleActions, modifier: Modifier = Modifier,
) {
    val overview = remember(state.highlightRules, state.linkedHighlightRuleIds, state.useLinkedHighlightRules) {
        readHighlightOverview(state.highlightRules, state.linkedHighlightRuleIds, state.useLinkedHighlightRules)
    }
    val presetName = if (state.onlyThisBook) stringResource(R.string.read_style_this_book) else state.selectedPresetName
    val modeLabel = if (overview.usesLinkedRules) {
        stringResource(R.string.read_highlight_linked_count, overview.linkedRuleIds.size)
    } else {
        stringResource(R.string.read_style_follow_global)
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HighlightCurrentPresetRow(presetName, modeLabel, contentColor) {
            actions.onPageSelected(ReadStylePage.HIGHLIGHT_LINK)
        }
        Spacer(Modifier.height(16.dp))
        HighlightRuleLibrary(
            state = state, overview = overview, contentColor = contentColor, accentColor = accentColor,
            actions = actions, modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
internal fun HighlightCurrentPresetRow(
    presetName: String,
    modeLabel: String,
    contentColor: Color,
    onClick: () -> Unit,
) {
    NgFormPanel {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.read_highlight_current_preset), color = contentColor, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = presetName, modifier = Modifier.weight(1f),
                color = contentColor.copy(alpha = 0.62f), fontSize = 13.sp, textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(" · $modeLabel", color = contentColor.copy(alpha = 0.62f), fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20), contentDescription = null,
                modifier = Modifier.size(18.dp), tint = contentColor.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
internal fun HighlightOverviewRuleRow(
    rule: ReadHighlightRule,
    usesLinkedRules: Boolean,
    usedByCurrent: Boolean,
    onlyThisBook: Boolean,
    contentColor: Color,
    accentColor: Color,
    onEdit: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onManage: (() -> Unit)? = null,
    manageMenu: (@Composable () -> Unit)? = null,
) {
    val title = rule.name.ifBlank { rule.pattern.ifBlank { stringResource(R.string.highlight_rule_default_name) } }
    val switchDescription = stringResource(R.string.read_highlight_global_switch_description, title)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onEdit)
                .padding(vertical = if (usesLinkedRules) 8.dp else 16.dp),
        ) {
            Text(title, color = contentColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (usesLinkedRules) {
                val label = when {
                    onlyThisBook && usedByCurrent -> R.string.read_highlight_used_by_book
                    onlyThisBook -> R.string.read_highlight_not_used_by_book
                    usedByCurrent -> R.string.read_highlight_used_by_preset
                    else -> R.string.read_highlight_not_used_by_preset
                }
                Text(stringResource(label), color = if (usedByCurrent) accentColor else contentColor.copy(alpha = 0.62f),
                    fontSize = 12.sp)
            }
        }
        NgSwitchControl(
            checked = rule.enabled, onCheckedChange = onEnabledChanged,
            modifier = Modifier.semantics { contentDescription = switchDescription },
            variant = NgSwitchControlVariant.COMPACT,
        )
        if (onManage != null) {
            Box(
                Modifier.size(48.dp)
                    .clickable(role = Role.Button, onClick = onManage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.read_highlight_rule_more, title),
                    modifier = Modifier.size(20.dp), tint = contentColor.copy(alpha = 0.65f),
                )
                manageMenu?.invoke()
            }
        }
    }
}

@Composable
private fun HighlightLinkPage(state: ReadStyleUiState, contentColor: Color, actions: ReadStyleActions) {
    val overview = remember(state.highlightRules, state.linkedHighlightRuleIds, state.useLinkedHighlightRules) {
        readHighlightOverview(state.highlightRules, state.linkedHighlightRuleIds, state.useLinkedHighlightRules)
    }
    val presetName = if (state.onlyThisBook) stringResource(R.string.read_style_this_book) else state.selectedPresetName
    HighlightAssociationPage(
        overview = overview, rules = state.highlightRules, presetName = presetName,
        contentColor = contentColor, onBack = actions.onBack,
        onRuleChanged = actions.onLinkedHighlightRuleChanged,
    )
}

@Composable
internal fun HighlightAssociationPage(
    overview: ReadHighlightOverview,
    rules: List<ReadHighlightRule>,
    presetName: String,
    contentColor: Color,
    onBack: () -> Unit,
    onRuleChanged: (String, Boolean) -> Unit,
) {
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.72f
    val accentColor = Color(NgTheme.colors.primary)
    Column(Modifier.fillMaxWidth().heightIn(max = height).padding(bottom = 16.dp)) {
        NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
        ReadStyleSubpageHeader(
            stringResource(R.string.read_highlight_link_rules), contentColor, onBack,
            circularBack = true, centerTitle = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(presetName, Modifier.weight(1f), color = accentColor, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.read_highlight_linked_count, overview.linkedRuleIds.size),
                color = contentColor.copy(alpha = 0.62f), fontSize = 12.sp)
        }
        if (rules.isEmpty()) {
            NgFormPanel(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.empty), Modifier.padding(16.dp),
                    color = contentColor.copy(alpha = 0.65f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 16.dp)) {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 12.dp else 0.dp, topEnd = if (index == 0) 12.dp else 0.dp,
                        bottomStart = if (index == rules.lastIndex) 12.dp else 0.dp,
                        bottomEnd = if (index == rules.lastIndex) 12.dp else 0.dp,
                    )
                    val checked = rule.id in overview.linkedRuleIds
                    Column(Modifier.fillMaxWidth().clip(shape).background(ngDrawerContentCardColor())) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .toggleable(value = checked, role = Role.Checkbox) { selected ->
                                    onRuleChanged(rule.id, selected)
                                }.padding(start = 14.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(rule.name.ifBlank { rule.pattern }, Modifier.weight(1f),
                                color = contentColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(
                                checkedColor = accentColor, uncheckedColor = contentColor.copy(alpha = 0.65f)))
                        }
                        if (index != rules.lastIndex) NgFormGroupDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun PresetHighlightModeSetting(
    overview: ReadHighlightOverview,
    contentColor: Color,
    onFollowGlobal: () -> Unit,
    onActivateLinked: () -> Unit,
    onOpenRules: () -> Unit,
) {
    val followGlobal = stringResource(R.string.read_style_follow_global)
    val summary = if (overview.usesLinkedRules) {
        stringResource(R.string.read_highlight_linked_count, overview.linkedRuleIds.size)
    } else followGlobal
    ReadStyleChoiceSettingRow(
        title = stringResource(R.string.read_highlight_rules),
        labels = listOf(followGlobal, stringResource(R.string.read_highlight_link_rules)),
        selectedIndex = if (overview.usesLinkedRules) 1 else 0,
        contentColor = contentColor,
        leadingIconRes = R.drawable.ic_menu,
        summary = summary,
        rowHeight = 56.dp,
        preferPopupBelow = true,
        onSelected = { index ->
            if (index == 0) {
                onFollowGlobal()
            } else {
                // Restore existing links when there are any; a new selection is saved on first check.
                if (!overview.usesLinkedRules && overview.linkedRuleIds.isNotEmpty()) onActivateLinked()
                onOpenRules()
            }
        },
    )
}

@Composable
private fun PresetPage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val overview = remember(state.highlightRules, state.linkedHighlightRuleIds, state.useLinkedHighlightRules) {
        readHighlightOverview(state.highlightRules, state.linkedHighlightRuleIds, state.useLinkedHighlightRules)
    }
    val bookPresetPosition = state.presets.indexOfFirst { it.index == -1 }
    LaunchedEffect(state.onlyThisBook, bookPresetPosition) {
        if (state.onlyThisBook && bookPresetPosition >= 0) {
            gridState.animateScrollToItem(bookPresetPosition + PresetGridActionCount)
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        NgFormPanel(
            variant = NgFormPanelVariant.LARGE_ROUNDED,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("选择预设", Modifier.weight(1f), color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("长按管理", color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp)
            }
            NgFormGroupDivider()
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                val horizontalSpacing = 8.dp
                val presetWidth = (maxWidth - horizontalSpacing * (PresetGridColumns - 1)) / PresetGridColumns
                val gridHeight = presetGridViewportHeight(state.presets.size, maxHeight)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PresetGridColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().height(gridHeight),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                    verticalArrangement = Arrangement.spacedBy(PresetGridRowSpacing),
                ) {
                    item(key = "create") {
                        PresetActionCard(
                            width = presetWidth, accentColor = accentColor,
                            iconRes = R.drawable.ic_add, label = stringResource(R.string.create),
                            onClick = actions.onCreatePreset,
                        )
                    }
                    item(key = "import") {
                        PresetActionCard(
                            width = presetWidth, accentColor = accentColor,
                            iconRes = R.drawable.ic_import, label = stringResource(R.string.import_str),
                            onClick = actions.onImportPreset,
                        )
                    }
                    gridItems(state.presets, key = { item -> item.stableId }) { item ->
                        PresetMenuCard(item, state, presetWidth, accentColor, actions)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        NgFormPanel(variant = NgFormPanelVariant.LARGE_ROUNDED) {
            PresetHighlightModeSetting(
                overview = overview, contentColor = contentColor,
                onFollowGlobal = { actions.onLinkedHighlightModeChanged(false) },
                onActivateLinked = { actions.onLinkedHighlightModeChanged(true) },
                onOpenRules = { actions.onPageSelected(ReadStylePage.HIGHLIGHT_LINK) },
            )
            if (state.canUseBookStyle || !state.onlyThisBook) NgFormGroupDivider()
            if (state.canUseBookStyle) {
                PresetSwitchRow(
                    title = stringResource(R.string.read_style_only_this_book),
                    iconRes = R.drawable.ic_bookshelf_dock_all,
                    checked = state.onlyThisBook,
                    contentColor = contentColor,
                    onCheckedChange = actions.onOnlyThisBookChanged,
                )
            }
            if (!state.onlyThisBook) {
                if (state.canUseBookStyle) NgFormGroupDivider()
                PresetRestoreAllRow(
                    contentColor = contentColor,
                    onClick = actions.onRestoreAllPresets,
                )
            }
        }
    }
}

@Composable
private fun PresetActionCard(width: Dp, accentColor: Color, iconRes: Int, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier.size(width = width, height = PresetGridCardHeight)
            .clip(shape)
            .background(ngDrawerContentCardColor())
            .background(accentColor.copy(alpha = 0.06f))
            .border(0.7.dp, accentColor.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = accentColor, modifier = Modifier.size(22.dp))
        Text(label, color = accentColor, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun PresetMenuCard(
    item: ReadStylePresetUi,
    state: ReadStyleUiState,
    width: Dp,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    var expanded by remember(item.stableId) { mutableStateOf(false) }
    val editable = !state.onlyThisBook || item.index == -1
    Box {
        PresetCard(
            name = item.name, width = width, textColor = Color(item.textColor),
            background = item.background, selected = item.index == state.selectedPresetIndex,
            accentColor = accentColor, onClick = { actions.onSelectPreset(item.index) },
            unselectedBorderColor = Color(NgTheme.colors.onSurface).copy(alpha = 0.20f),
            onLongClick = { expanded = true },
        )
        ReadChoicePopup(
            expanded = expanded,
            options = buildList {
                if (editable) add(ReadMoreConfigOption("rename", "重命名"))
                if (!state.onlyThisBook) add(ReadMoreConfigOption("copy", "复制"))
                add(ReadMoreConfigOption("export", "导出"))
                if (item.canRestoreDefault) add(ReadMoreConfigOption("restore", "恢复默认"))
                if (editable) add(ReadMoreConfigOption("delete", if (item.index == -1) "跟随全局" else "删除"))
            },
            selectedValue = null,
            onDismissRequest = { expanded = false },
            onSelected = { value ->
                when (value) {
                    "rename" -> actions.onEditPresetAt(item.index)
                    "copy" -> actions.onCopyPresetAt(item.index)
                    "export" -> actions.onExportPresetAt(item.index)
                    "restore" -> actions.onRestorePresetAt(item.index)
                    "delete" -> actions.onDeletePresetAt(item.index)
                }
            },
            preferPopupBelow = false,
            scrollable = true,
            variant = ReadChoicePopupVariant.ADAPTIVE,
            menuWidth = 120.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
            centerOnAnchor = true,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    name: String,
    width: Dp,
    textColor: Color,
    background: ImageBitmap?,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    unselectedBorderColor: Color? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = width, height = PresetGridCardHeight)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.13f))
            .border(
                width = if (selected) 1.2.dp else 0.7.dp,
                color = if (selected) accentColor else unselectedBorderColor ?: textColor.copy(alpha = 0.38f),
                shape = shape,
            )
            .then(
                if (onLongClick == null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.combinedClickable(
                    role = Role.Button, onClick = onClick,
                    onLongClickLabel = "管理预设", onLongClick = onLongClick,
                )
            ),
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
    onlyThisBook: Boolean,
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
            .padding(horizontal = PresetVisibleHorizontalInset, vertical = 4.dp)
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
            iconRes = if (onlyThisBook) R.drawable.ic_restore else R.drawable.ic_book_info_delete,
            label = stringResource(if (onlyThisBook) R.string.read_style_follow_global else R.string.delete),
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
internal fun PresetSwitchRow(
    title: String,
    iconRes: Int,
    checked: Boolean,
    contentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(SettingIconSlotSize), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(SettingIconSize),
                tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.64f),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = SettingIconTextSpacing).weight(1f),
            color = contentColor,
            fontSize = 14.sp,
        )
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            variant = NgSwitchControlVariant.COMPACT,
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
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(SettingIconSlotSize), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_restore),
                contentDescription = null,
                modifier = Modifier.size(SettingIconSize),
                tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.64f),
            )
        }
        Text(
            text = stringResource(R.string.read_style_restore_all),
            modifier = Modifier.padding(start = SettingIconTextSpacing).weight(1f),
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
    val configuration = LocalConfiguration.current
    val maxEditorHeight = configuration.screenHeightDp.dp * 0.85f
    val largeScreen = isLargeScreen(configuration)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (largeScreen) Modifier.heightIn(max = maxEditorHeight)
                else Modifier.height(maxEditorHeight)
            ),
    ) {
        EditorHeader(
            creatingPreset = state.creatingPreset,
            mode = state.editorMode,
            allowEInk = state.allowEInkEditing,
            contentColor = contentColor,
            accentColor = indicatorColor,
            onBack = actions.onBack,
            onModeSelected = actions.onEditorModeChanged,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (largeScreen) Modifier else Modifier.weight(1f))
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
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
                        text = "${stringResource(R.string.bg_alpha)} · ${stringResource(R.string.read_style_shared_short)}",
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
                EditorNavigationRow(
                    title = "阅读背景",
                    color = contentColor,
                    trailingContent = {
                        val shape = RoundedCornerShape(8.dp)
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 40.dp)
                                .clip(shape)
                                .background(Color(state.editorBackgroundColor))
                                .border(0.7.dp, contentColor.copy(alpha = 0.18f), shape)
                                .semantics { contentDescription = "当前背景预览" },
                            contentAlignment = Alignment.Center,
                        ) {
                            val background = state.editorPreviewBackground
                            if (background != null) {
                                Image(
                                    bitmap = background,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else if (state.editorBackgroundType != 0) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_image),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = contentColor.copy(alpha = 0.5f),
                                )
                            }
                        }
                    },
                    onClick = actions.onSelectBackgroundImage,
                )
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

@Composable
private fun EditorHeader(
    creatingPreset: Boolean,
    mode: Int,
    allowEInk: Boolean,
    contentColor: Color,
    accentColor: Color,
    onBack: () -> Unit,
    onModeSelected: (Int) -> Unit,
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
            text = stringResource(
                if (creatingPreset) R.string.read_style_create_title else R.string.read_style_edit_title
            ),
            modifier = Modifier.padding(start = 2.dp).weight(1f),
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.size(width = if (allowEInk) 186.dp else 140.dp, height = 42.dp)) {
            ReadStyleModeSelector(
                mode = mode, allowEInk = allowEInk,
                contentColor = contentColor, accentColor = accentColor,
                onSelected = onModeSelected,
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
                fontFamily = NgTheme.fontFamily,
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
internal fun AppearanceSettingIcon(icon: ImageVector) {
    Box(Modifier.size(SettingIconSlotSize), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(SettingIconSize),
            tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.64f),
        )
    }
    Spacer(Modifier.width(SettingIconTextSpacing))
}

@Composable
private fun AppearanceSettingIcon(@DrawableRes iconRes: Int) {
    Box(Modifier.size(SettingIconSlotSize), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(SettingIconSize),
            tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.64f),
        )
    }
    Spacer(Modifier.width(SettingIconTextSpacing))
}

internal enum class EditorNavigationArrowVariant { DEFAULT, RIGHT_TO_DOWN }

@Composable
internal fun EditorNavigationRow(
    title: String,
    summary: String? = null,
    color: Color,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    expanded: Boolean? = null,
    rowHeight: Dp = 52.dp,
    arrowVariant: EditorNavigationArrowVariant = EditorNavigationArrowVariant.DEFAULT,
    @DrawableRes leadingIconRes: Int? = null,
    onClick: () -> Unit,
) {
    val rowColor = if (enabled) color else color.copy(alpha = 0.38f)
    val expansionState = expanded?.let {
        stringResource(if (it) R.string.ng_form_row_expanded else R.string.ng_form_row_collapsed)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (expansionState != null) stateDescription = expansionState },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconRes != null) {
            AppearanceSettingIcon(leadingIconRes)
        } else {
            leadingIcon?.let { AppearanceSettingIcon(it) }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = rowColor,
            fontSize = 15.sp,
        )
        if (trailingContent != null) {
            trailingContent()
        } else if (summary != null) {
            Text(
                text = summary,
                color = rowColor.copy(alpha = if (enabled) 0.62f else 0.72f),
                fontSize = 13.sp,
            )
        }
        Icon(
            painter = painterResource(
                when (expanded) {
                    true -> if (arrowVariant == EditorNavigationArrowVariant.RIGHT_TO_DOWN) {
                        R.drawable.ic_expand_more
                    } else R.drawable.ic_expand_less
                    false -> if (arrowVariant == EditorNavigationArrowVariant.RIGHT_TO_DOWN) {
                        R.drawable.ic_chevron_right_20
                    } else R.drawable.ic_expand_more
                    null -> R.drawable.ic_chevron_right_20
                }
            ),
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
    FloatingAppearanceGroups(state, contentColor, accentColor, actions)
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
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (selected) Modifier.background(selectedContainerColor) else Modifier
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
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
    AdvancedColorPage(
        title = state.editorModeLabel + " · " + title,
        initialColor = state.editorInitialColor ?: currentColor,
        backgroundRenderer = state.colorPreviewTarget::renderBackground,
        onBack = actions.onBack,
        onColorChanged = onColorChanged,
        onReset = actions.onResetEditorColor,
    )
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
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
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
                        fontFamily = NgTheme.fontFamily,
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
internal fun ReadStyleDock(
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
