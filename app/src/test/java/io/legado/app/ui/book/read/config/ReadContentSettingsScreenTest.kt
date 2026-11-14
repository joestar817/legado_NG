package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = theme, updateSystemBars = false) {
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
                )
            }
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

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "conversion" to 2, "replace" to false, "rules" to "open",
                    "title" to true, "segment" to true,
                ),
                events,
            )
            assertEquals(ReadContentSettingsState(2, true, false, true, true), state)
        }
    }
}
