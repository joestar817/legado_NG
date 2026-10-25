package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
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

    private fun initialState(): ReadStyleUiState {
        val config = ReadBookConfig.Config()
        return ReadStyleUiState(
            presets = emptyList(), selectedPresetIndex = 0, selectedPresetName = "test",
            canRestoreCurrentDefault = false, highlightSummary = "", isEpub = false,
            onlyThisBook = false, canUseBookStyle = false, shareLayout = false, globalFloatingFollowApp = false,
            textSize = 16, letterSpacing = 0f, lineSpacingExtra = 0, paragraphSpacing = 0, pageAnim = 0,
            highlightRules = emptyList(), highlightSelectionMode = HighlightSelectionMode.NONE,
            selectedHighlightIds = emptySet(), editorMode = 0, allowEInkEditing = false,
            colorPreviewTarget = ReadStyleEditTarget(config, config, ReadAppearanceMode.DAY, null),
            editorModeLabel = "Day", editorPreviewBackground = null, editorBackgrounds = emptyList(),
            editorBackgroundType = 0, editorBackgroundName = "#FFFFFF", editorTextColor = 0xFF000000.toInt(),
            editorBackgroundColor = 0xFFFFFFFF.toInt(), editorTextAccentColor = 17, editorBackgroundAlpha = 100,
            editorFloatingColorSeed = 0, editorFloatingColorFromBackground = false, editorFloatingTransparency = 30,
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
