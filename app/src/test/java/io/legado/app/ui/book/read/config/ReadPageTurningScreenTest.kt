package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.constant.PageAnim
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
class ReadPageTurningScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun scrollAnimationDoesNotExposeTheRemovedInstantOption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var state by mutableStateOf(
            ReadMoreConfigUiState(
                booleans = emptyMap(),
                values = emptyMap(),
                options = emptyMap(),
                thresholdValues = emptyMap(),
                pageAnim = PageAnim.simulationPageAnim,
            ),
        )
        var preferenceChanges = 0
        val actions = ReadMoreConfigActions(
            onBooleanChanged = { _, _ -> preferenceChanges++ },
            onValueChanged = { _, _ -> },
            onAction = {},
            onPageAnimChanged = { state = state.copy(pageAnim = it) },
            onThresholdChanged = { _, _ -> },
            onPageKeysChanged = { _, _ -> },
            pageKeyCapture = PageKeyCaptureController { _, _ -> },
        )
        val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = theme, updateSystemBars = false) {
                ReadPageTurningScreen(state, actions)
            }
        }

        val removed = "Instant page turns in scroll mode"
        composeRule.onNodeWithText(removed).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.page_anim_scroll)).performClick()
        composeRule.onNodeWithText(removed).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(PageAnim.scrollPageAnim, state.pageAnim)
            assertEquals(0, preferenceChanges)
        }
    }

    @Test
    fun animationAndMigratedSettingsDispatchTheirOriginalActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<Pair<String, Any>>()
        var state by mutableStateOf(
            ReadMoreConfigUiState(
                booleans = mapOf(PreferKey.volumeKeyPage to true),
                values = mapOf(PreferKey.doublePageHorizontal to "0"),
                options = mapOf(
                    PreferKey.doublePageHorizontal to listOf(
                        ReadMoreConfigOption("0", "Single"),
                        ReadMoreConfigOption("1", "Double"),
                    ),
                ),
                thresholdValues = mapOf(
                    PreferKey.pageTouchSlop to 0,
                    PreferKey.pageTouchClick to 0,
                ),
                pageAnim = 2,
            ),
        )
        val actions = ReadMoreConfigActions(
            onBooleanChanged = { key, value ->
                events += key to value
                state = state.copy(booleans = state.booleans + (key to value))
            },
            onValueChanged = { key, value ->
                events += key to value
                state = state.copy(values = state.values + (key to value))
            },
            onAction = { events += it to "open" },
            onPageAnimChanged = {
                events += "animation" to it
                state = state.copy(pageAnim = it)
            },
            onThresholdChanged = { key, value ->
                events += key to value
                state = state.copy(thresholdValues = state.thresholdValues + (key to value))
            },
            onPageKeysChanged = { prev, next -> events += "keys" to "$prev/$next" },
            pageKeyCapture = PageKeyCaptureController { _, _ -> },
        )
        val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = theme, updateSystemBars = false) {
                ReadPageTurningScreen(state, actions)
            }
        }

        composeRule.assertDrawerTextStyle(context.getString(R.string.read_settings_tab_page), 17)
        composeRule.assertDrawerTextStyle(context.getString(R.string.volume_key_page), 14)
        composeRule.assertDrawerTextStyle(context.getString(R.string.double_page_horizontal), 15)
        composeRule.assertDrawerTextStyle("Single", 13)
        composeRule.onNodeWithText(context.getString(R.string.page_anim_slide)).performClick()
        composeRule.onNodeWithText("Single").performScrollTo().performClick()
        composeRule.onNodeWithText("Double").performClick()
        composeRule.onNodeWithText(context.getString(R.string.mouse_wheel_page)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.read_page_section_keys)).assertExists()
        composeRule.onNodeWithText("Volume keys to turn page when reading").assertDoesNotExist()
        composeRule.onNodeWithText("Disable return key").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.volume_key_page))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.key_page_on_long_press_summary))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.key_page_on_long_press))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.custom_page_key))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.prev_page)).assertExists()
        val slopTitle = context.getString(R.string.page_touch_slop_title)
        val clickTitle = context.getString(R.string.page_touch_click_title)
        composeRule.onNodeWithContentDescription(slopTitle).assertDoesNotExist()
        composeRule.onNodeWithText(slopTitle).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(slopTitle)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(23f) }
        composeRule.onNodeWithText(clickTitle).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(clickTitle)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        composeRule.onNodeWithText(slopTitle).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(slopTitle).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.click_regional_config))
            .performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "animation" to 1,
                    PreferKey.doublePageHorizontal to "1",
                    PreferKey.volumeKeyPage to false,
                    PreferKey.keyPageOnLongPress to true,
                    PreferKey.pageTouchSlop to 23,
                    PreferKey.pageTouchClick to 45,
                    ReadMoreConfigKeys.CLICK_REGIONAL_CONFIG to "open",
                ),
                events,
            )
        }
    }
}
