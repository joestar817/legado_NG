package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadContentSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun showDrawer(content: @Composable () -> Unit) {
        val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent { NgAppTheme(snapshot = theme, updateSystemBars = false, content = content) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w372dp-h826dp-xxhdpi")
    fun textUsesTheFloatingDrawerSizesAndWeights() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        showDrawer {
            ReadContentSettingsScreen(
                state = ReadContentSettingsState(
                    chineseConverterType = 1, bookAvailable = true, replaceEnabled = true,
                    removeSameTitle = true, reSegment = false,
                ),
                onChineseConverterChanged = {}, onReplaceChanged = {}, onOpenReplaceRules = {},
                onRemoveSameTitleChanged = {}, onReSegmentChanged = {}, onParagraphIcons = {},
            )
        }
        composeRule.assertDrawerTextStyle(context.getString(R.string.content), 17)
        composeRule.assertDrawerTextStyle(context.getString(R.string.read_content_chinese_conversion), 14, FontWeight.Medium)
        composeRule.assertDrawerTextStyle(context.getString(R.string.read_content_settings_title), 14, FontWeight.Medium)
        composeRule.assertDrawerTextStyle(context.getString(R.string.read_content_only_current_book), 12)
        context.resources.getStringArray(R.array.chinese_mode_short).forEachIndexed { index, label ->
            composeRule.assertDrawerTextStyle(label, 13, if (index == 1) FontWeight.Medium else FontWeight.Normal)
        }
        listOf(R.string.replace_rule_title, R.string.same_title_removed, R.string.re_segment).forEach {
            composeRule.assertDrawerTextStyle(context.getString(it), 14)
        }
        composeRule.assertDrawerTextStyle(context.getString(R.string.read_content_replace_rules), 15)
        composeRule.assertDrawerTextStyle(context.getString(R.string.paragraph_icon_title), 15)
    }

    @Test
    fun conversionAndBookSwitchesDispatchIndependentChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<Pair<String, Any>>()
        var state by mutableStateOf(
            ReadContentSettingsState(
                chineseConverterType = 0,
                bookAvailable = true,
                replaceEnabled = true,
                removeSameTitle = false,
                reSegment = false,
            ),
        )
        showDrawer {
            ReadContentSettingsScreen(
                state = state,
                onChineseConverterChanged = {
                    events += "conversion" to it
                    state = state.copy(chineseConverterType = it)
                },
                onReplaceChanged = {
                    events += "replace" to it
                    state = state.copy(replaceEnabled = it)
                },
                onOpenReplaceRules = { events += "rules" to "open" },
                onRemoveSameTitleChanged = {
                    events += "title" to it
                    state = state.copy(removeSameTitle = it)
                },
                onReSegmentChanged = {
                    events += "segment" to it
                    state = state.copy(reSegment = it)
                },
                onParagraphIcons = { events += "paragraphIcons" to "open" },
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.replace_scope_global)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.content)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.read_content_settings_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.read_content_only_current_book)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.reverse_content)).assertDoesNotExist()
        val conversionOptions = context.resources.getStringArray(R.array.chinese_mode_short)
        composeRule.onNodeWithText(conversionOptions[2]).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.replace_rule_title))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.read_content_replace_rules))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.same_title_removed))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.re_segment))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.paragraph_icon_title))
            .performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "conversion" to 2, "replace" to false, "rules" to "open",
                    "title" to true, "segment" to true, "paragraphIcons" to "open",
                ),
                events,
            )
            assertEquals(ReadContentSettingsState(2, true, false, true, true), state)
        }
    }
}
