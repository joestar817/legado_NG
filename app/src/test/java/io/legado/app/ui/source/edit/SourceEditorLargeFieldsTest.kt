package io.legado.app.ui.source.edit

import android.app.Activity
import android.content.Intent
import android.os.Parcel
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.code.CodeEditSessionStore
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp", application = InterfaceFontTestApplication::class)
class SourceEditorLargeFieldsTest : SourceEditorActivityTest() {
    @Test
    fun bothFormsOpenAndSaveMultilineRulesLargerThanOneMebibyteInAParcel() {
        val original = ("<p>Long multiline 中英文 " + "章".repeat(208) + "</p>\n").repeat(2400)
        val updated = original.replace('章', '节') + "\nLast line"
        val oldPayload = Parcel.obtain()
        try {
            Intent().putExtra("text", original).writeToParcel(oldPayload, 0)
            assertTrue(oldPayload.dataSize() > 1024 * 1024)
        } finally {
            oldPayload.recycle()
        }
        val book = seedBook()
        val bookIntent = editorIntent(BookSourceEditActivity::class.java, book.bookSourceUrl)
        ActivityScenario.launch<BookSourceEditActivity>(bookIntent).use { scenario ->
            awaitField(book.bookSourceName)
            tab(R.string.source_tab_find)
            field("Discovery seed").performTextReplacement(original)
            action(R.string.edit_content)
            val launch = awaitLaunch { scenario.access { it } }
            assertSmallLaunch(launch)
            assertEquals(original, CodeEditSessionStore.app.read(launch.sessionId()))
            scenario.onActivity { dispatchCode(it, launch, updated, cursor = 0) }
            val model = scenario.access { ViewModelProvider(it)[BookSourceEditViewModel::class.java] }
            await { model.editor.tabs[2].first { it.key == "name" }.value == updated }
            val activity = scenario.access { it }
            action(R.string.action_save)
            await { activity.isFinishing && shadowOf(activity).resultCode == Activity.RESULT_OK }
            assertEquals(updated, appDb.bookSourceDao.getBookSource(book.bookSourceUrl)?.ruleExplore?.name)
        }
        ActivityScenario.launch<BookSourceEditActivity>(bookIntent).use { scenario ->
            awaitField(book.bookSourceName)
            val draft = scenario.access { ViewModelProvider(it)[BookSourceEditViewModel::class.java].editor }
            assertEquals(updated, draft.tabs[2].first { it.key == "name" }.value)
        }

        val rss = seedRss()
        val rssIntent = editorIntent(RssSourceEditActivity::class.java, rss.sourceUrl)
        ActivityScenario.launch<RssSourceEditActivity>(rssIntent).use { scenario ->
            awaitField(rss.sourceName)
            tab(R.string.source_tab_list)
            field("List title").performTextReplacement(original)
            action(R.string.edit_content)
            val launch = awaitLaunch { scenario.access { it } }
            assertSmallLaunch(launch)
            assertEquals(original, CodeEditSessionStore.app.read(launch.sessionId()))
            scenario.onActivity { dispatchCode(it, launch, updated, cursor = 0) }
            val model = scenario.access { ViewModelProvider(it)[RssSourceEditViewModel::class.java] }
            await { model.editor.source.ruleTitle == updated }
            val activity = scenario.access { it }
            action(R.string.action_save)
            await { activity.isFinishing && shadowOf(activity).resultCode == Activity.RESULT_OK }
            assertEquals(updated, appDb.rssSourceDao.getByKey(rss.sourceUrl)?.ruleTitle)
        }
        ActivityScenario.launch<RssSourceEditActivity>(rssIntent).use { scenario ->
            awaitField(rss.sourceName)
            val draft = scenario.access { ViewModelProvider(it)[RssSourceEditViewModel::class.java].editor }
            assertEquals(updated, draft.source.ruleTitle)
        }
    }
}
