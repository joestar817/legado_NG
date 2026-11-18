package io.legado.app.ui.code

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rosemoe.sora.widget.CodeEditor
import io.legado.app.R
import io.legado.app.help.CacheManager
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp", application = InterfaceFontTestApplication::class)
class CodeEditFieldSessionsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun largeMultilineTextTravelsThroughFilesAndCanBeCleared() {
        val input = ("// 章 title and rule " + "入".repeat(236) + "\n").repeat(2350)
        val result = input.replace('入', '出') + "\n// end"
        assertTrue(parcelSize(Intent().putExtra("text", input)) > 1024 * 1024)
        withSession(input) { id ->
            val launch = sourceFieldIntent(id, 0)
            assertPayloadIsSmall(launch)
            withEditor(launch, input) { activity, editor ->
                editor.setText(result)
                val returned = save(activity)
                assertEquals(id, returned.getStringExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID))
                assertTrue(returned.getBooleanExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, false))
                assertPayloadIsSmall(returned)
                assertEquals(result, CodeEditSessionStore.app.readWorkingText(id))
                assertEquals(input, CodeEditSessionStore.app.read(id))
            }
        }
        withSession("clear this\n清空") { id ->
            withEditor(sourceFieldIntent(id, 2), "clear this\n清空") { activity, editor ->
                editor.setText("")
                val returned = save(activity)
                assertTrue(returned.getBooleanExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, false))
                assertEquals("", CodeEditSessionStore.app.readWorkingText(id))
            }
        }
    }

    @Test
    fun cursorOnlyResultsIncludeZeroMiddleAndEndWithoutWritingText() {
        val text = "头部 and end"
        for (position in listOf(0, 4, text.length)) {
            withSession(text) { id ->
                withEditor(sourceFieldIntent(id, 2), text) { activity, editor ->
                    editor.setSelection(0, position)
                    val returned = save(activity)
                    assertEquals(id, returned.getStringExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID))
                    assertEquals(position, returned.getIntExtra("cursorPosition", -1))
                    assertFalse(returned.getBooleanExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, true))
                    assertPayloadIsSmall(returned)
                    assertNull(CodeEditSessionStore.app.readWorkingText(id))
                }
            }
        }
    }

    @Test
    fun discardingChangedRuleReturnsCanceledEvenWithANonzeroCursor() {
        withSession("original rule") { id ->
            withEditor(sourceFieldIntent(id, 3), "original rule") { activity, editor ->
                editor.setText("discard this")
                editor.setSelection(0, 4)
                activity.finish()
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                await { activity.isFinishing }
                assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
                assertNull(CodeEditSessionStore.app.readWorkingText(id))
                assertEquals("original rule", CodeEditSessionStore.app.read(id))
            }
        }
    }

    @Test
    fun codeWindowRestoresWorkingTextAndItsCursorWithAFreshViewModel() {
        withSession("start") { id ->
            val launch = sourceFieldIntent(id, 2)
            val state = Bundle()
            val first = Robolectric.buildActivity(CodeEditActivity::class.java, launch).setup()
            val editor = loadedEditor(first.get(), "start")
            editor.setText("modified\n草稿")
            editor.setSelection(1, 1)
            first.pause().saveInstanceState(state).stop()
            await { CodeEditSessionStore.app.readWorkingText(id) == "modified\n草稿" }
            first.destroy()

            Robolectric.buildActivity(CodeEditActivity::class.java, launch).setup(state).use { second ->
                val restored = loadedEditor(second.get(), "modified\n草稿")
                assertEquals("modified\n草".length, restored.cursor.left)
                assertEquals("start", CodeEditSessionStore.app.read(id))
                val accepted = save(second.get())
                assertTrue(accepted.getBooleanExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, false))
            }
        }
    }

    @Test
    fun sharedEditorKeepsSingleScriptSaveCancelDebugLoginAndReadOnlyBehavior() {
        val original = "const fixture = 1;"
        val actions = listOf(
            R.id.menu_save to null,
            R.id.menu_debug_source to CodeEditActivity.RESULT_ACTION_DEBUG_SOURCE,
            R.id.menu_login to CodeEditActivity.RESULT_ACTION_LOGIN_SOURCE,
        )
        for ((menu, action) in actions) {
            withSession(original) { id ->
                withEditor(jsIntent(id), original) { activity, editor ->
                    if (action != null) editor.setText("const fixture = 2;")
                    val result = save(activity, menu)
                    assertEquals(action, result.getStringExtra(CodeEditActivity.EXTRA_RESULT_ACTION))
                    assertEquals(id, result.getStringExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID))
                    assertEquals(action != null, result.getBooleanExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, true))
                    assertEquals(editor.text.toString(), CodeEditSessionStore.app.read(id))
                }
            }
        }
        withSession(original) { id ->
            withEditor(jsIntent(id), original) { activity, editor ->
                editor.setText("discard")
                activity.onCodeEditDiscardAndExit()
                assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
                assertEquals(original, CodeEditSessionStore.app.read(id))
            }
        }
        val key = "read-only-code-session-test"
        CacheManager.putMemory(key, original)
        val view = Intent(context, CodeEditActivity::class.java).putExtra("cacheKey", key)
        withEditor(view, original, writable = false) { activity, editor ->
            assertFalse(editor.isEditable)
            activity.finish()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        }
    }

    private fun sourceFieldIntent(id: String, cursor: Int) = Intent(context, CodeEditActivity::class.java)
        .putExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID, id)
        .putExtra(CodeEditActivity.EXTRA_SOURCE_FIELD_SESSION, true)
        .putExtra("cursorPosition", cursor)

    private fun jsIntent(id: String) = Intent(context, CodeEditActivity::class.java)
        .putExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID, id)
        .putExtra("returnUnchangedText", true)
        .putExtra(CodeEditActivity.EXTRA_SHOW_DEBUG_SOURCE, true)
        .putExtra(CodeEditActivity.EXTRA_SHOW_LOGIN_SOURCE, true)

    private fun withSession(text: String, block: (String) -> Unit) {
        val id = CodeEditSessionStore.app.create(text)
        try {
            block(id)
        } finally {
            CodeEditSessionStore.app.delete(id)
        }
    }

    private fun withEditor(
        intent: Intent,
        expected: String,
        writable: Boolean = true,
        block: (CodeEditActivity, CodeEditor) -> Unit,
    ) {
        Robolectric.buildActivity(CodeEditActivity::class.java, intent).setup().use { controller ->
            block(controller.get(), loadedEditor(controller.get(), expected, writable))
        }
    }

    private fun loadedEditor(activity: CodeEditActivity, expected: String, writable: Boolean = true): CodeEditor {
        val editor = activity.findViewById<CodeEditor>(R.id.editText)
        await { editor.text.length == expected.length && editor.isEditable == writable }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(expected, editor.text.toString())
        return editor
    }

    private fun save(activity: CodeEditActivity, menu: Int = R.id.menu_save): Intent {
        activity.onCompatOptionsItemSelected(RoboMenuItem(menu))
        await { activity.isFinishing && shadowOf(activity).resultCode == Activity.RESULT_OK }
        return requireNotNull(shadowOf(activity).resultIntent)
    }

    private fun assertPayloadIsSmall(intent: Intent) {
        assertNull(intent.getStringExtra("text"))
        assertTrue(parcelSize(intent) < 4096)
    }

    private fun parcelSize(intent: Intent): Int = Parcel.obtain().let { parcel ->
        try {
            intent.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Code editor did not finish the requested action", condition())
    }
}
