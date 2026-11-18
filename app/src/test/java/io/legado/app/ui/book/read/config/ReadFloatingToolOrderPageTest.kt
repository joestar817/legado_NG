package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadFloatingTool
import io.legado.app.help.config.ReadFloatingToolSettings
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.defaultSharedPreferences
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
class ReadFloatingToolOrderPageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dragAndSwitchPersistImmediatelyAndSurviveReturning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val orderKey = PreferKey.readFloatingToolOrder
        val disabledKey = PreferKey.readFloatingToolDisabled
        val optInKey = PreferKey.readFloatingToolOptIn
        val originalOrder = preferences.getString(orderKey, null)
        val originalDisabled = preferences.getString(disabledKey, null)
        val originalOptIn = preferences.getString(optInKey, null)
        try {
            preferences.edit().remove(orderKey).remove(disabledKey).remove(optInKey).commit()
            var open by mutableStateOf(true)
            var changes = 0
            composeRule.setContent {
                NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                    if (open) ReadFloatingToolOrderPage(onBack = { open = false }, onChanged = { changes++ }, maxEnabled = { 8 })
                }
            }
            val brightness = context.getString(R.string.brightness)
            val search = context.getString(R.string.search_content)
            composeRule.onNodeWithText(context.getString(R.string.text_selection_toolbar_preview)).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(search).assertIsOn().performClick()
            composeRule.runOnIdle { assertTrue(ReadFloatingTool.SEARCH.key in ReadFloatingToolSettings.disabledKeys()) }
            composeRule.onNodeWithContentDescription(search).assertIsOff()

            val drag = context.getString(R.string.sort) + " · " + brightness
            var reordered = emptyList<ReadFloatingTool>()
            composeRule.onNodeWithContentDescription(drag).performTouchInput {
                down(center)
                repeat(12) { index -> moveTo(center + Offset(0f, height * 1.8f * (index + 1) / 12f), delayMillis = 30L) }
                up()
            }
            composeRule.runOnIdle {
                reordered = ReadFloatingToolSettings.order()
                assertTrue(reordered.indexOf(ReadFloatingTool.BRIGHTNESS) > 0)
                assertTrue(changes >= 2)
            }
            composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            composeRule.runOnIdle { open = true }
            composeRule.onNodeWithContentDescription(search).assertIsOff()
            composeRule.runOnIdle { assertEquals(reordered, ReadFloatingToolSettings.order()) }
        } finally {
            val editor = preferences.edit()
            if (originalOrder == null) editor.remove(orderKey) else editor.putString(orderKey, originalOrder)
            if (originalDisabled == null) editor.remove(disabledKey) else editor.putString(disabledKey, originalDisabled)
            if (originalOptIn == null) editor.remove(optInKey) else editor.putString(optInKey, originalOptIn)
            editor.commit()
        }
    }

    @Test
    fun switchesRespectTheMinimumAndCurrentCapacity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val orderKey = PreferKey.readFloatingToolOrder
        val disabledKey = PreferKey.readFloatingToolDisabled
        val optInKey = PreferKey.readFloatingToolOptIn
        val originalOrder = preferences.getString(orderKey, null)
        val originalDisabled = preferences.getString(disabledKey, null)
        val originalOptIn = preferences.getString(optInKey, null)
        try {
            preferences.edit().remove(orderKey).remove(disabledKey).remove(optInKey).commit()
            ReadFloatingToolSettings.setDisabledKeys(ReadFloatingTool.entries.drop(2).map { it.key }.toSet())
            composeRule.setContent {
                NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                    ReadFloatingToolOrderPage(onBack = {}, onChanged = {}, maxEnabled = { 2 })
                }
            }
            val brightness = context.getString(R.string.brightness)
            val search = context.getString(R.string.search_content)
            val ai = context.getString(R.string.ai_actions)
            composeRule.onNodeWithContentDescription(ai, useUnmergedTree = true).assertIsOff().assertIsNotEnabled()
            composeRule.onNodeWithContentDescription(brightness).assertIsOn().performClick()
            composeRule.onNodeWithContentDescription(search, useUnmergedTree = true).assertIsOn().assertIsNotEnabled()
            composeRule.runOnIdle { assertEquals(listOf(ReadFloatingTool.SEARCH), ReadFloatingToolSettings.visibleTools()) }
            composeRule.onNodeWithContentDescription(ai).assertIsOff().performClick()
            composeRule.runOnIdle {
                assertEquals(listOf(ReadFloatingTool.SEARCH, ReadFloatingTool.AI), ReadFloatingToolSettings.visibleTools())
            }
        } finally {
            val editor = preferences.edit()
            if (originalOrder == null) editor.remove(orderKey) else editor.putString(orderKey, originalOrder)
            if (originalDisabled == null) editor.remove(disabledKey) else editor.putString(disabledKey, originalDisabled)
            if (originalOptIn == null) editor.remove(optInKey) else editor.putString(optInKey, originalOptIn)
            editor.commit()
        }
    }

    @Test
    fun newToolsStartOffAndRespectTheExistingCapacity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val orderKey = PreferKey.readFloatingToolOrder
        val disabledKey = PreferKey.readFloatingToolDisabled
        val optInKey = PreferKey.readFloatingToolOptIn
        val originalOrder = preferences.getString(orderKey, null)
        val originalDisabled = preferences.getString(disabledKey, null)
        val originalOptIn = preferences.getString(optInKey, null)
        try {
            preferences.edit().remove(orderKey).remove(disabledKey).remove(optInKey).commit()
            composeRule.setContent {
                NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                    ReadFloatingToolOrderPage(onBack = {}, onChanged = {}, maxEnabled = { 8 })
                }
            }
            val readAloud = context.getString(R.string.read_aloud)
            val scroll = context.getString(R.string.read_menu_scroll)
            val pageAnim = context.getString(R.string.page_anim)
            val chinese = context.getString(R.string.chinese_converter)
            listOf(readAloud, scroll, pageAnim).forEach { label ->
                composeRule.onNodeWithContentDescription(label).assertIsOff().performClick()
            }
            composeRule.onNodeWithContentDescription(chinese, useUnmergedTree = true).assertIsOff().assertIsNotEnabled()
            composeRule.runOnIdle { assertEquals(8, ReadFloatingToolSettings.visibleTools().size) }
            composeRule.onNodeWithContentDescription(readAloud).assertIsOn().performClick()
            composeRule.onNodeWithContentDescription(chinese).assertIsOff().performClick()
            composeRule.runOnIdle {
                assertEquals(8, ReadFloatingToolSettings.visibleTools().size)
                assertTrue(ReadFloatingTool.CHINESE in ReadFloatingToolSettings.visibleTools())
                assertTrue(ReadFloatingTool.READ_ALOUD !in ReadFloatingToolSettings.visibleTools())
            }
        } finally {
            val editor = preferences.edit()
            if (originalOrder == null) editor.remove(orderKey) else editor.putString(orderKey, originalOrder)
            if (originalDisabled == null) editor.remove(disabledKey) else editor.putString(disabledKey, originalDisabled)
            if (originalOptIn == null) editor.remove(optInKey) else editor.putString(optInKey, originalOptIn)
            editor.commit()
        }
    }

    private fun testTheme() = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
}
