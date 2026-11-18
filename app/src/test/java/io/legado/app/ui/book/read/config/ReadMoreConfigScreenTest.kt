package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBottomTool
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
class ReadMoreConfigScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun groupedSettingsKeepTheirScopesAndDispatchOriginalActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<Pair<String, Any>>()
        val shortcuts = mutableListOf<ReadBottomTool>()
        var openedToolbar = false
        var state by mutableStateOf(
            ReadMoreConfigUiState(
                booleans = mapOf(
                    PreferKey.hideStatusBar to true,
                    PreferKey.autoChangeSource to true,
                ),
                values = mapOf(
                    PreferKey.screenOrientation to "0",
                    PreferKey.keepLight to "0",
                    ReadMoreConfigKeys.BOOK_IMAGE_STYLE to "original",
                    PreferKey.clickImgWay to "default",
                ),
                options = mapOf(
                    PreferKey.screenOrientation to listOf(
                        ReadMoreConfigOption("0", "System orientation"),
                        ReadMoreConfigOption("1", "Portrait orientation"),
                    ),
                    PreferKey.keepLight to listOf(
                        ReadMoreConfigOption("0", "Default timeout"),
                        ReadMoreConfigOption("1", "Five minute timeout"),
                    ),
                    ReadMoreConfigKeys.BOOK_IMAGE_STYLE to listOf(
                        ReadMoreConfigOption("original", "Original image"),
                        ReadMoreConfigOption("fit", "Fit image width"),
                    ),
                    PreferKey.clickImgWay to listOf(
                        ReadMoreConfigOption("default", "Default image action"),
                        ReadMoreConfigOption("view", "View image"),
                    ),
                ),
                thresholdValues = emptyMap(),
                pageAnim = 0,
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
            onAction = {},
            onPageAnimChanged = {},
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
                ReadMoreConfigScreen(
                    state = state,
                    actions = actions,
                    hiddenTools = setOf(ReadBottomTool.SCROLL, ReadBottomTool.FLOATING),
                    onOpenBottomToolbar = { openedToolbar = true },
                    onOpenShortcut = { shortcuts += it },
                )
            }
        }

        composeRule.assertDrawerTextStyle(context.getString(R.string.read_more_settings_title), 17)
        composeRule.assertDrawerTextStyle(context.getString(R.string.pt_hide_status_bar), 14)
        composeRule.assertDrawerTextStyle(context.getString(R.string.screen_direction), 15)
        composeRule.assertDrawerTextStyle("System orientation", 13)
        listOf(
            R.string.read_settings_tab_interface,
            R.string.read_settings_current_book,
            R.string.read_settings_all_books,
            R.string.screen_direction,
            R.string.keep_light,
            R.string.pt_hide_status_bar,
            R.string.read_body_to_lh,
            R.string.read_content_avoid_cutout,
            R.string.image_style,
            R.string.auto_change_source,
            R.string.click_image_way,
        ).forEach { composeRule.onNodeWithText(context.getString(it)).assertExists() }
        composeRule.onNodeWithText(context.getString(R.string.read_settings_tab_content))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.read_menu_scroll)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.read_bottom_toolbar))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.read_menu_floating))
            .performScrollTo().performClick()

        composeRule.onNodeWithText("System orientation").performScrollTo().performClick()
        composeRule.onNodeWithText("Portrait orientation").performClick()
        composeRule.onNodeWithText("Default timeout").performScrollTo().performClick()
        composeRule.onNodeWithText("Five minute timeout").performClick()
        composeRule.onNodeWithText(context.getString(R.string.pt_hide_status_bar))
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Original image").performScrollTo().performClick()
        composeRule.onNodeWithText("Fit image width").performClick()
        composeRule.onNodeWithText(context.getString(R.string.auto_change_source))
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Default image action").performScrollTo().performClick()
        composeRule.onNodeWithText("View image").performClick()

        composeRule.runOnIdle {
            assertEquals(true, openedToolbar)
            assertEquals(listOf(ReadBottomTool.FLOATING), shortcuts)
            assertEquals(
                listOf(
                    PreferKey.screenOrientation to "1",
                    PreferKey.keepLight to "1",
                    PreferKey.hideStatusBar to false,
                    ReadMoreConfigKeys.BOOK_IMAGE_STYLE to "fit",
                    PreferKey.autoChangeSource to false,
                    PreferKey.clickImgWay to "view",
                ),
                events,
            )
        }
    }
}
