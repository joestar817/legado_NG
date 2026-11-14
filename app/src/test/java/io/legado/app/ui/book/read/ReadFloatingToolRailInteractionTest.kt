package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.help.config.ReadThemeMode
import io.legado.app.help.config.ReadFloatingTool
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadFloatingToolRailInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun reorderedToolsKeepTheirActionsAndFixedDockButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var tools by mutableStateOf(listOf(ReadFloatingTool.AI, ReadFloatingTool.BRIGHTNESS, ReadFloatingTool.SEARCH))
        var expansion by mutableStateOf<ReadFloatingToolExpansion?>(null)
        var openedSettings = 0
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    ReadFloatingToolRail(
                        dockSide = ReadFloatingToolDock.LEFT, tools = tools, expansion = expansion,
                        brightness = 128, brightnessAutomatic = false, nightMode = false,
                        themeMode = ReadThemeMode.FOLLOW_SYSTEM, onExpansionChange = { expansion = it },
                        autoPage = false, pageAnim = 0, chineseMode = 0,
                        onBrightnessChange = {}, onBrightnessChangeFinished = {}, onToggleBrightnessAutomatic = {},
                        onSearch = {}, onReplace = {}, onThemeModeSelected = {}, onAiPurify = {},
                        onAiSettings = { openedSettings++; expansion = null }, onToggleDockSide = {},
                        onReadAloud = {}, onAutoPage = {}, onPageAnimSelected = {}, onChineseModeSelected = {},
                    )
                }
            }
        }
        val ai = composeRule.onNodeWithContentDescription(context.getString(R.string.ai_actions))
        val brightness = composeRule.onNodeWithContentDescription(context.getString(R.string.brightness))
        assertTrue(ai.fetchSemanticsNode().boundsInRoot.top < brightness.fetchSemanticsNode().boundsInRoot.top)
        ai.performClick()
        composeRule.onNodeWithText(context.getString(R.string.ai_purify_settings)).assertExists()
        composeRule.runOnIdle { tools = listOf(ReadFloatingTool.BRIGHTNESS, ReadFloatingTool.AI) }
        assertTrue(brightness.fetchSemanticsNode().boundsInRoot.top < ai.fetchSemanticsNode().boundsInRoot.top)
        composeRule.onNodeWithText(context.getString(R.string.ai_purify_settings)).performClick()
        composeRule.runOnIdle { assertEquals(1, openedSettings); tools = emptyList() }
        ai.assertDoesNotExist()
        brightness.assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.adjust_pos)).assertExists()
    }

    @Test
    fun expandingDifferentButtonsKeepsRailStillAndRetainsActionsOnBothSides() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var dock by mutableStateOf(ReadFloatingToolDock.LEFT)
        var expansion by mutableStateOf<ReadFloatingToolExpansion?>(null)
        var aiSettings = 0
        var themeMode by mutableStateOf(ReadThemeMode.FOLLOW_SYSTEM)
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = if (dock.isRight) Alignment.CenterEnd else Alignment.CenterStart) {
                    ReadFloatingToolRail(
                        dockSide = dock, expansion = expansion, brightness = 128,
                        brightnessAutomatic = false, nightMode = false, themeMode = themeMode,
                        autoPage = false, pageAnim = 0, chineseMode = 0,
                        onExpansionChange = { expansion = it }, onBrightnessChange = {},
                        onBrightnessChangeFinished = {}, onToggleBrightnessAutomatic = {},
                        onSearch = {}, onReplace = {}, onThemeModeSelected = { themeMode = it },
                        onAiPurify = {}, onAiSettings = { aiSettings++; expansion = null },
                        onToggleDockSide = { expansion = null; dock = dock.toggled() },
                        onReadAloud = {}, onAutoPage = {}, onPageAnimSelected = {}, onChineseModeSelected = {},
                    )
                }
            }
        }
        val brightness = composeRule.onNodeWithContentDescription(context.getString(R.string.brightness))
        val initialTop = brightness.fetchSemanticsNode().boundsInRoot.top
        brightness.performClick()
        assertEquals(initialTop, brightness.fetchSemanticsNode().boundsInRoot.top, 1f)
        composeRule.onNodeWithContentDescription(context.getString(R.string.dark_theme)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_mode_follow_short)).assertExists()
        assertEquals(initialTop, brightness.fetchSemanticsNode().boundsInRoot.top, 1f)
        composeRule.onNodeWithContentDescription(context.getString(R.string.ai_actions)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.ai_purify_settings)).performClick()
        composeRule.runOnIdle { assertEquals(1, aiSettings) }
        composeRule.onNodeWithText(context.getString(R.string.ai_purify_settings)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.adjust_pos)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.dark_theme)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_mode_follow_short)).assertExists()
        assertEquals(initialTop, brightness.fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun newToolsUseDirectActionsAndAnchoredSelectablePanels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var tools by mutableStateOf(listOf(ReadFloatingTool.PAGE_ANIM, ReadFloatingTool.READ_ALOUD,
            ReadFloatingTool.AUTO_SCROLL, ReadFloatingTool.CHINESE))
        var dock by mutableStateOf(ReadFloatingToolDock.LEFT)
        var expansion by mutableStateOf<ReadFloatingToolExpansion?>(null)
        var pageAnim by mutableStateOf(2)
        var chineseMode by mutableStateOf(0)
        var autoPage by mutableStateOf(false)
        var readAloudCalls = 0
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = if (dock.isRight) Alignment.CenterEnd else Alignment.CenterStart) {
                    ReadFloatingToolRail(
                        dockSide = dock, tools = tools, expansion = expansion,
                        brightness = 128, brightnessAutomatic = false, nightMode = false,
                        themeMode = ReadThemeMode.FOLLOW_SYSTEM, autoPage = autoPage,
                        pageAnim = pageAnim, chineseMode = chineseMode, onExpansionChange = { expansion = it },
                        onBrightnessChange = {}, onBrightnessChangeFinished = {}, onToggleBrightnessAutomatic = {},
                        onSearch = {}, onReplace = {}, onThemeModeSelected = {}, onAiPurify = {}, onAiSettings = {},
                        onReadAloud = { readAloudCalls++ }, onAutoPage = { autoPage = !autoPage },
                        onPageAnimSelected = { pageAnim = it }, onChineseModeSelected = { chineseMode = it },
                        onToggleDockSide = { expansion = null; dock = dock.toggled() },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.read_aloud)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.auto_next_page)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.auto_next_page_stop)).assertExists()
        composeRule.runOnIdle { assertEquals(1, readAloudCalls) }

        composeRule.onNodeWithContentDescription(context.getString(R.string.page_anim)).performClick()
        val slide = context.getString(R.string.page_anim_slide)
        composeRule.onNodeWithText(slide).performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(1, pageAnim) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.chinese_converter)).performClick()
        val traditionalToSimplified = context.resources.getStringArray(R.array.chinese_mode_short)[1]
        composeRule.onNodeWithText(traditionalToSimplified).performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(1, chineseMode)
            tools = tools.reversed()
        }
        composeRule.onNodeWithText(traditionalToSimplified).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.adjust_pos)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.chinese_converter)).performClick()
        composeRule.onNodeWithText(traditionalToSimplified).assertIsSelected()
    }
}
