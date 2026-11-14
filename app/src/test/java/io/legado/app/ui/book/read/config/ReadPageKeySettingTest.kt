package io.legado.app.ui.book.read.config

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.help.config.PageKeyDirection
import io.legado.app.help.config.matchesPageKey
import io.legado.app.help.config.savePageKeyBindings
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadPageKeySettingTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "zh-rCN-w372dp-h826dp-xxhdpi")
    fun volumeBindingsStayOnTwoRowsDuringCapture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val capture = PageKeyCaptureController { _, _ -> }
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                Box(Modifier.width(340.dp)) {
                    PageKeyBindingControls(PageKeyBindings("24", "25"), capture, {})
                }
            }
        }
        val previousTop = composeRule.onNodeWithText(context.getString(R.string.prev_page))
            .fetchSemanticsNode().boundsInRoot.top
        val nextTop = composeRule.onNodeWithText(context.getString(R.string.next_page))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(nextTop > previousTop)
        composeRule.onNodeWithText(context.getString(R.string.page_key_volume_up)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.page_key_volume_down)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.prev_page_key)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.page_key_waiting)).assertExists()
        val nextTopWhileWaiting = composeRule.onNodeWithText(context.getString(R.string.next_page))
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(nextTop, nextTopWhileWaiting, 1f)
    }

    @Test
    fun narrowLayoutKeepsEachBindingAccessibleOnSeparateRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                Box(Modifier.width(240.dp)) {
                    PageKeyBindingControls(PageKeyBindings("21", "22"), PageKeyCaptureController { _, _ -> }, {})
                }
            }
        }
        val previousTop = composeRule.onNodeWithText(context.getString(R.string.prev_page))
            .fetchSemanticsNode().boundsInRoot.top
        val nextTop = composeRule.onNodeWithText(context.getString(R.string.next_page))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(nextTop > previousTop)
        composeRule.onNodeWithContentDescription(context.getString(R.string.prev_page_key)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.page_key_waiting)).assertExists()
    }

    @Test
    fun boundBadgesSizeToTheirLabelsAndKeepClearTargetsSeparate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                Box(Modifier.width(411.dp)) {
                    PageKeyBindingControls(PageKeyBindings("21", "10000"), PageKeyCaptureController { _, _ -> }, {})
                }
            }
        }
        val previousTitle = context.getString(R.string.prev_page_key)
        val nextTitle = context.getString(R.string.next_page_key)
        val previousBind = composeRule.onNodeWithContentDescription(previousTitle)
            .fetchSemanticsNode().boundsInRoot
        val nextBind = composeRule.onNodeWithContentDescription(nextTitle)
            .fetchSemanticsNode().boundsInRoot
        val previousClear = composeRule.onNodeWithContentDescription(
            context.getString(R.string.page_key_clear_description, previousTitle),
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(nextBind.width > previousBind.width)
        assertTrue(previousBind.right <= previousClear.left + 1f)
        assertTrue(previousClear.width >= 44f * composeRule.density.density)
    }

    @Test
    fun bindingIsImmediateAndClearDoesNotChangeTheOtherDirection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var bindings by mutableStateOf(PageKeyBindings("21", "22"))
        var writes = 0
        val capture = PageKeyCaptureController { direction, keyCode ->
            bindings = bindings.bind(direction, keyCode)
            writes++
        }
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadPageKeyExpandableSetting(bindings, capture) {
                    bindings = it
                    writes++
                }
            }
        }
        val title = context.getString(R.string.custom_page_key)
        val prev = context.getString(R.string.prev_page_key)
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithContentDescription(prev).performClick()
        composeRule.onNodeWithText(context.getString(R.string.page_key_waiting)).assertExists()
        composeRule.runOnIdle {
            assertTrue(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
            assertEquals(PageKeyBindings("24", "22"), bindings)
            assertEquals(1, writes)
            assertTrue(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
        }
        composeRule.onNodeWithText(context.getString(R.string.page_key_volume_up)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.page_key_clear_description, prev))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(PageKeyBindings("", "22"), bindings)
            assertEquals(2, writes)
        }
    }

    @Test
    fun cancelOrCollapseStopsWaitingWithoutSaving() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var writes = 0
        val capture = PageKeyCaptureController { _, _ -> writes++ }
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadPageKeyExpandableSetting(PageKeyBindings("", ""), capture) { writes++ }
            }
        }
        val title = context.getString(R.string.custom_page_key)
        val prev = context.getString(R.string.prev_page_key)
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithContentDescription(prev).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.runOnIdle {
            assertNull(capture.waitingFor)
            assertEquals(0, writes)
        }
        composeRule.onNodeWithContentDescription(prev).performClick()
        composeRule.onNodeWithText(title).performClick()
        composeRule.runOnIdle {
            assertNull(capture.waitingFor)
            assertEquals(0, writes)
        }
        composeRule.onNodeWithText(context.getString(R.string.page_key_waiting)).assertDoesNotExist()
    }

    @Test
    fun sameKeyMovesToNewDirectionAndLegacyListsRequireRebinding() {
        val before = PageKeyBindings("21", "22")
        assertEquals(PageKeyBindings("", "21"), before.bind(PageKeyDirection.NEXT, 21))
        assertEquals(PageKeyBindings("22", ""), before.bind(PageKeyDirection.PREVIOUS, 22))
        assertTrue(matchesPageKey("21", 21))
        assertFalse(matchesPageKey("21,22", 21))
        assertFalse(matchesPageKey("0", 0))
        assertEquals(PageKeyBindings("21", ""), before.clear(PageKeyDirection.NEXT))
    }

    @Test
    fun savingBindingsImmediatelyUpdatesBothPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val previous = preferences.getString(PreferKey.prevKeys, null)
        val next = preferences.getString(PreferKey.nextKeys, null)
        try {
            context.savePageKeyBindings(PageKeyBindings("21", "22"))
            assertEquals("21", preferences.getString(PreferKey.prevKeys, null))
            assertEquals("22", preferences.getString(PreferKey.nextKeys, null))
        } finally {
            val editor = preferences.edit()
            if (previous == null) editor.remove(PreferKey.prevKeys)
            else editor.putString(PreferKey.prevKeys, previous)
            if (next == null) editor.remove(PreferKey.nextKeys)
            else editor.putString(PreferKey.nextKeys, next)
            editor.commit()
        }
    }

    @Test
    fun backCancelsAndCapturedKeyUpIsConsumed() {
        var captured: Pair<PageKeyDirection, Int>? = null
        val capture = PageKeyCaptureController { direction, code -> captured = direction to code }
        capture.start(PageKeyDirection.PREVIOUS)
        assertTrue(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertNull(capture.waitingFor)
        assertNull(captured)
        assertFalse(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        capture.start(PageKeyDirection.NEXT)
        assertTrue(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals(PageKeyDirection.NEXT to KeyEvent.KEYCODE_DPAD_LEFT, captured)
        assertTrue(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)))
        assertFalse(capture.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
    }
}
