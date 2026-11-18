package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.EffectiveReadFloatingColor
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.help.config.ReadThemeMode
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadStyleModeInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun highlightTabUpdatesOwnerAndUiStateWithoutSwitchingReader() {
        ReadBookConfig.selectThemeMode(ReadThemeMode.DAY, systemNightMode = false)
        val session = ReadStyleEditorSession()
        var state by mutableStateOf(initialState())
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                ReadStyleModeSelector(
                    mode = state.highlightColorMode, allowEInk = false,
                    contentColor = Color.Black, accentColor = Color(0xFF008577),
                    onSelected = { selected ->
                        val mode = session.selectHighlightMode(selected)
                        state = state.withHighlightColorMode(mode)
                    },
                )
            }
        }
        composeRule.onNodeWithText("Night").performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(1, state.highlightColorMode)
            assertEquals(1, session.highlightMode)
            assertFalse(ReadBookConfig.isNightTheme)
        }
        composeRule.onNodeWithText("Day").performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(0, state.highlightColorMode)
            assertEquals(0, session.highlightMode)
            assertEquals(ReadThemeMode.DAY, ReadBookConfig.currentThemeMode())
        }
    }

    @Test
    fun presetDayTabDoesNotDisableActualSystemNight() {
        ReadBookConfig.selectThemeMode(ReadThemeMode.FOLLOW_SYSTEM, systemNightMode = true)
        val session = ReadStyleEditorSession().apply { selectPresetMode(1) }
        var mode by mutableStateOf(ReadAppearanceMode.NIGHT)
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                ReadStyleModeSelector(
                    mode = mode.ordinal, allowEInk = false,
                    contentColor = Color.Black, accentColor = Color(0xFF008577),
                    onSelected = { selected -> mode = session.selectPresetMode(selected) },
                )
            }
        }
        composeRule.onNodeWithText("Day").performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(ReadAppearanceMode.DAY, session.presetMode)
            assertTrue(ReadBookConfig.isNightTheme)
            assertEquals(ReadThemeMode.FOLLOW_SYSTEM, ReadBookConfig.currentThemeMode())
        }
    }

    @Test
    fun floatingColorPreviewUsesTheTargetModeForUnsetIndependentColor() {
        assertEquals(0xFF5CCBFF.toInt(), floatingColorPreviewSeed(0, ReadAppearanceMode.NIGHT))
        assertEquals(0xFFE53935.toInt(), floatingColorPreviewSeed(0, ReadAppearanceMode.DAY))
        assertEquals(0xFF41566B.toInt(), floatingColorPreviewSeed(0xFF41566B.toInt(), ReadAppearanceMode.NIGHT))
    }

    @Test
    fun floatingTabsKeepColorModesSeparateFromGlobalLayoutSettings() {
        var state by mutableStateOf(initialState().copy(floatingStandalone = true))
        var toolbarOrderOpened = 0
        var sideToolOrderOpened = 0
        val pickerModes = mutableListOf<Int>()
        val actions = floatingTestActions(
            onTab = { state = state.copy(floatingTab = it) },
            onMode = { state = state.copy(editorMode = it) },
            onToolbarOrder = { toolbarOrderOpened++ },
        ).copy(
            onPickFloatingColor = { pickerModes += state.editorMode },
            onFloatingChapterProgressChanged = { state = state.copy(floatingShowChapterProgress = it) },
            onFloatingSideToolOrder = { sideToolOrderOpened++ },
        )
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                FloatingSettingsPage(state, Color.Black, actions)
            }
        }
        composeRule.assertDrawerTextStyle("Globally follow app colors", 14)
        composeRule.assertDrawerTextStyle("Floating color", 15)
        composeRule.onNodeWithText("Current preset").assertExists()
        composeRule.onNodeWithText("Catalog drawer style").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Day Floating color").assertExists()
        composeRule.onNodeWithContentDescription("Night Floating color").performClick()
        composeRule.onNodeWithText("Custom").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(1), pickerModes)
        }

        composeRule.onNodeWithText("Layout & actions").performClick().assertIsSelected()
        composeRule.onNodeWithText("Current preset").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Day Floating color").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Night Floating color").assertDoesNotExist()
        composeRule.onNodeWithText("Catalog drawer style").assertExists()
        composeRule.onNodeWithText("Side toolbar order").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, sideToolOrderOpened) }
        composeRule.onNodeWithText("Text selection toolbar").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, toolbarOrderOpened) }
        composeRule.onNodeWithText("Show chapter progress bar").performScrollTo().performClick()
        composeRule.runOnIdle { assertFalse(state.floatingShowChapterProgress) }
        composeRule.onNodeWithText("Show chapter progress bar").performClick()
        composeRule.runOnIdle { assertTrue(state.floatingShowChapterProgress) }

        composeRule.onNodeWithText("Colors").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("Day Floating color").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(1, 0), pickerModes)
        }
        composeRule.onNodeWithText("Catalog drawer style").assertDoesNotExist()
        composeRule.runOnIdle {
            val following = EffectiveReadFloatingColor(0, true, ReadFloatingColorStyle.VIBRANT)
            state = state.copy(globalFloatingFollowApp = true, floatingDayColor = following, floatingNightColor = following)
        }
        composeRule.onNodeWithContentDescription("Day Floating color").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Follow app"))
        composeRule.onNodeWithContentDescription("Night Floating color").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Follow app"))
        composeRule.runOnIdle {
            val independent = EffectiveReadFloatingColor(0, false, ReadFloatingColorStyle.VIBRANT)
            state = state.copy(onlyThisBook = true, globalFloatingFollowApp = false,
                floatingDayColor = independent, floatingNightColor = independent)
        }
        composeRule.onNodeWithText("Current style").assertExists()
        composeRule.onNodeWithText("Book-specific").assertExists()
        composeRule.onNodeWithText("Globally follow app colors").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Night Floating color").performClick()
        composeRule.runOnIdle { assertEquals(listOf(1, 0, 1), pickerModes) }
    }

    @Test
    fun floatingSlidersUpdateTheirOwnValuesAndCommit() {
        var state by mutableStateOf(initialState().copy(floatingStandalone = true))
        var commits = 0
        val actions = floatingTestActions(onTab = {}, onMode = {}, onToolbarOrder = {}).copy(
            onFloatingTransparencyChanged = { state = state.copy(editorFloatingTransparency = it) },
            onFloatingPrimaryStrengthChanged = { state = state.copy(editorFloatingPrimaryStrength = it) },
            onFloatingAppearanceChangeFinished = { commits++ },
        )
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                FloatingSettingsPage(state, Color.Black, actions)
            }
        }
        composeRule.onNodeWithContentDescription("Transparency").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(72.6f) }
        composeRule.onNodeWithContentDescription("Primary color strength").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(101f) }
        composeRule.runOnIdle {
            assertEquals(73, state.editorFloatingTransparency)
            assertEquals(100, state.editorFloatingPrimaryStrength)
            assertEquals(2, commits)
        }
        composeRule.onNodeWithContentDescription("Transparency").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-10f) }
        composeRule.runOnIdle {
            assertEquals(0, state.editorFloatingTransparency)
            assertEquals(100, state.editorFloatingPrimaryStrength)
            assertEquals(3, commits)
        }
    }

    @Test
    fun floatingSliderCanReturnToItsStartingValueInOneGesture() {
        var state by mutableStateOf(initialState().copy(floatingStandalone = true, editorFloatingTransparency = 50))
        val changes = mutableListOf<Int>()
        var commits = 0
        val actions = floatingTestActions(onTab = {}, onMode = {}, onToolbarOrder = {}).copy(
            onFloatingTransparencyChanged = {
                changes += it
                state = state.copy(editorFloatingTransparency = it)
            },
            onFloatingAppearanceChangeFinished = { commits++ },
        )
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                FloatingSettingsPage(state, Color.Black, actions)
            }
        }
        composeRule.onNodeWithContentDescription("Transparency").performScrollTo().performTouchInput {
            down(Offset(width * 0.75f, center.y))
            moveTo(center, delayMillis = 1L)
            up()
        }
        composeRule.runOnIdle {
            assertTrue(changes.any { it > 50 })
            assertEquals(50, state.editorFloatingTransparency)
            assertEquals(1, commits)
        }
    }

    private fun floatingTestActions(
        onTab: (FloatingSettingsTab) -> Unit,
        onMode: (Int) -> Unit,
        onToolbarOrder: () -> Unit,
    ) = ReadStyleActions(
        onPageSelected = {}, onCreatePreset = {}, onSelectPreset = {}, onImportPreset = {},
        onEditPreset = {}, onExportPreset = {}, onDeletePreset = {}, onRestoreCurrentPreset = {},
        onRestoreAllPresets = {}, onEpubStyleChanged = {}, onOnlyThisBookChanged = {},
        onShareLayoutChanged = {}, onGlobalFloatingFollowAppChanged = {}, onImportHighlights = {},
        onBeginHighlightSelection = {}, onRestoreBuiltInHighlights = {}, onToggleHighlightSelection = {},
        onToggleAllHighlightSelection = {}, onCancelHighlightSelection = {},
        onConfirmHighlightSelection = {}, onBack = {}, onPresetNameChanged = {}, onTextColorChanged = {},
        onBackgroundColorChanged = {}, onTextAccentColorChanged = {}, onEditorModeChanged = onMode,
        onResetEditorColor = {}, onBackgroundAlphaChanged = {}, onSelectBackgroundImage = {},
        onSelectBackground = { _, _ -> }, onPickFloatingColor = {},
        onFloatingColorChanged = {}, onRestoreFloatingOriginal = {}, onFloatingTransparencyChanged = {},
        onFloatingPrimaryStrengthChanged = {}, onFloatingColorStyleChanged = {},
        onFloatingAppearanceChangeFinished = {}, onFullLineUnderlineEnabledChanged = {},
        onFullLineUnderlineDashedChanged = {}, onFullLineUnderlineColorChanged = {},
        onFullLineUnderlineWidthChanged = {}, onFullLineUnderlineOffsetChanged = {},
        onFullLineUnderlineExtendChanged = {}, onFullLineUnderlineDashLengthChanged = {},
        onFullLineUnderlineGapLengthChanged = {}, onFontWeight = {}, onFont = {}, onTitleFont = {},
        onTitleColor = {}, onTitleChanged = {}, onIndent = {}, onFontWeightChanged = {},
        onIndentChanged = {}, onChineseConverter = {}, onPadding = {}, onTip = {}, onTipColorChanged = {},
        onTipTextColor = {}, onTipDividerColor = {}, onTextSizeChanged = {}, onLetterSpacingChanged = {},
        onLineSpacingChanged = {}, onParagraphSpacingChanged = {}, onPageAnimChanged = {},
        onCreateHighlight = {}, onEditHighlight = {}, onCopyHighlight = {}, onExportHighlight = {},
        onRestoreHighlight = {}, onDeleteHighlightById = {}, onHighlightDraftChanged = {},
        onHighlightColorModeChanged = {}, onSelectHighlightBackground = {}, onClearHighlightBackground = {},
        onSelectHighlightFont = {}, onClearHighlightFont = {}, onSaveHighlight = {}, onDeleteHighlight = {},
        onHighlightEnabledChanged = { _, _ -> }, onReorderHighlights = {},
        onFloatingTabChanged = onTab, onFloatingToolbarOrder = onToolbarOrder,
        floatingSideToolCapacity = { 8 },
    )

    private fun initialState(): ReadStyleUiState {
        val config = ReadBookConfig.Config()
        return ReadStyleUiState(
            presets = emptyList(), selectedPresetIndex = 0, selectedPresetName = "test",
            canRestoreCurrentDefault = false, highlightSummary = "", isEpub = false,
            onlyThisBook = false, canUseBookStyle = false, shareLayout = false, globalFloatingFollowApp = false,
            textSize = 16, textWeight = 400, letterSpacing = 0f, lineSpacingExtra = 0, paragraphSpacing = 0,
            paragraphIndentLevel = 0, pageAnim = 0,
            highlightRules = emptyList(), highlightSelectionMode = HighlightSelectionMode.NONE,
            selectedHighlightIds = emptySet(), editorMode = 0, allowEInkEditing = false,
            colorPreviewTarget = ReadStyleEditTarget(config, config, ReadAppearanceMode.DAY, null),
            editorModeLabel = "Day", editorPreviewBackground = null, editorBackgrounds = emptyList(),
            editorBackgroundType = 0, editorBackgroundName = "#FFFFFF", editorTextColor = 0xFF000000.toInt(),
            editorBackgroundColor = 0xFFFFFFFF.toInt(), editorTextAccentColor = 17, editorBackgroundAlpha = 100,
            editorFloatingColorSeed = 0, editorFloatingColorIsCustom = true,
            floatingDayColor = EffectiveReadFloatingColor(0, false, ReadFloatingColorStyle.VIBRANT),
            floatingNightColor = EffectiveReadFloatingColor(0, false, ReadFloatingColorStyle.VIBRANT),
            editorFloatingTransparency = 30,
            editorFloatingPrimaryStrength = 30, editorFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
            fullLineUnderline = FullLineUnderlineUiState(true, false, 17, 1, 2, false, 1f, 1f),
            highlightDraft = null, highlightColorMode = 0, editingHighlightIndex = null,
            editorInitialColor = null, editorInitialColorWasUnset = false,
            editorInitialBackgroundType = null, editorInitialBackgroundName = null, editorInitialBackground = null,
        )
    }

    private fun testTheme() = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
}
