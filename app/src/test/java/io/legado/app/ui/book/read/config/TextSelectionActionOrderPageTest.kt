package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.TextSelectionActionOrder
import io.legado.app.help.config.TextSelectionBuiltInAction
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
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
class TextSelectionActionOrderPageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun actionSwitchUpdatesPreviewAndPersistsImmediately() {
        val originalOrder = TextSelectionActionOrder.load()
        val originalDisabled = TextSelectionActionOrder.disabledKeys()
        val originalShowText = TextSelectionActionOrder.showText()
        try {
            TextSelectionActionOrder.save(TextSelectionBuiltInAction.entries, emptySet())
            TextSelectionActionOrder.setShowText(true)
            var visible by mutableStateOf(true)
            composeRule.setContent {
                NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                    if (visible) TextSelectionActionOrderPage(onBack = { visible = false })
                }
            }

            composeRule.onAllNodesWithText("Speech").assertCountEquals(1)
            composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Replace").assertIsOn().performClick()
            composeRule.onAllNodesWithText("Speech").assertCountEquals(2)
            composeRule.runOnIdle { assertTrue("replace" in TextSelectionActionOrder.disabledKeys()) }

            composeRule.onNodeWithContentDescription("Back").performClick()
            composeRule.runOnIdle {
                assertTrue("replace" in TextSelectionActionOrder.disabledKeys())
                visible = true
            }
            composeRule.onNodeWithContentDescription("Replace").assertIsOff()
        } finally {
            TextSelectionActionOrder.save(originalOrder, originalDisabled)
            TextSelectionActionOrder.setShowText(originalShowText)
        }
    }

    @Test
    fun showTextChangesAndPersistsImmediately() {
        val originalOrder = TextSelectionActionOrder.load()
        val originalDisabled = TextSelectionActionOrder.disabledKeys()
        val originalShowText = TextSelectionActionOrder.showText()
        try {
            TextSelectionActionOrder.save(TextSelectionBuiltInAction.entries, emptySet())
            TextSelectionActionOrder.setShowText(true)
            var visible by mutableStateOf(true)
            composeRule.setContent {
                NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                    if (visible) TextSelectionActionOrderPage(onBack = { visible = false })
                }
            }

            composeRule.onAllNodesWithText("Replace").assertCountEquals(2)
            composeRule.onNodeWithContentDescription("Show text").assertIsOn().performClick()
            composeRule.onAllNodesWithText("Replace").assertCountEquals(1)
            composeRule.runOnIdle { assertFalse(TextSelectionActionOrder.showText()) }

            composeRule.onNodeWithContentDescription("Back").performClick()
            composeRule.runOnIdle { visible = true }
            composeRule.onNodeWithContentDescription("Show text").assertIsOff()
            composeRule.onAllNodesWithText("Replace").assertCountEquals(1)
            composeRule.onNodeWithContentDescription("Show text").performClick()
            composeRule.runOnIdle { assertTrue(TextSelectionActionOrder.showText()) }
            composeRule.onAllNodesWithText("Replace").assertCountEquals(2)
        } finally {
            TextSelectionActionOrder.save(originalOrder, originalDisabled)
            TextSelectionActionOrder.setShowText(originalShowText)
        }
    }

    @Test
    fun atLeastOneActionRemainsEnabledAndAnotherCanBeSelected() {
        val originalOrder = TextSelectionActionOrder.load()
        val originalDisabled = TextSelectionActionOrder.disabledKeys()
        try {
            val initiallyEnabled = setOf(TextSelectionBuiltInAction.REPLACE, TextSelectionBuiltInAction.SEARCH)
            TextSelectionActionOrder.save(
                TextSelectionBuiltInAction.entries,
                TextSelectionBuiltInAction.entries.filterNot { it in initiallyEnabled }.map { it.key }.toSet(),
            )
            composeRule.setContent {
                NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                    TextSelectionActionOrderPage(onBack = {})
                }
            }

            composeRule.onNodeWithContentDescription("Replace").assertIsOn().performClick()
            composeRule.onNodeWithContentDescription("Search", useUnmergedTree = true)
                .assertIsOn().assertIsNotEnabled()
            composeRule.runOnIdle {
                assertTrue(TextSelectionBuiltInAction.REPLACE.key in TextSelectionActionOrder.disabledKeys())
                assertFalse(TextSelectionBuiltInAction.SEARCH.key in TextSelectionActionOrder.disabledKeys())
            }

            composeRule.onNodeWithContentDescription("Replace").assertIsOff().performClick()
            composeRule.onNodeWithContentDescription("Search").assertIsOn().performClick()
            composeRule.onNodeWithContentDescription("Replace", useUnmergedTree = true)
                .assertIsOn().assertIsNotEnabled()
            composeRule.runOnIdle {
                assertFalse(TextSelectionBuiltInAction.REPLACE.key in TextSelectionActionOrder.disabledKeys())
                assertTrue(TextSelectionBuiltInAction.SEARCH.key in TextSelectionActionOrder.disabledKeys())
            }
        } finally {
            TextSelectionActionOrder.save(originalOrder, originalDisabled)
        }
    }

    private fun testTheme() = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFFB3261E.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
}
