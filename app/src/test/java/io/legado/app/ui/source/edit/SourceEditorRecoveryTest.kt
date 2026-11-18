package io.legado.app.ui.source.edit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.book.source.edit.BookSourceFieldId
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeEditSessionStore
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditViewModel
import io.legado.app.ui.rss.source.edit.RssEditorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity.IntentForResult
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp", application = InterfaceFontTestApplication::class)
class SourceEditorRecoveryTest : SourceEditorActivityTest() {
    @Test
    fun bookSourceRestoresDraftAndPendingFieldWithAFreshViewModel() {
        val book = seedBook()
        val intent = editorIntent(BookSourceEditActivity::class.java, book.bookSourceUrl)
        val first = Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).setup()
        awaitField(book.bookSourceName)
        field(book.bookSourceName).performTextReplacement("Unsaved book name")
        tab(R.string.source_tab_find)
        field("Discovery seed").performTextReplacement("Draft discovery rule")
        select("Draft discovery rule", 2, 5)
        action(R.string.edit_content)
        val launch = awaitLaunch { first.get() }
        assertSmallLaunch(launch)
        assertEquals("Draft discovery rule", CodeEditSessionStore.app.read(launch.sessionId()))
        val firstModel = ViewModelProvider(first.get())[BookSourceEditViewModel::class.java]
        val folder = File(firstModel.getApplication<Application>().cacheDir, "source_editor_drafts/book")
        val state = Bundle()
        first.pause().saveInstanceState(state).stop()
        val draftFile = File(folder, "${state.getString(SourceEditorSession.STATE_DRAFT_ID)}.json")
        assertTrue(draftFile.isFile)
        first.destroy()

        val restoredState = smallBundleRoundTrip(state)
        val second = Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).create(restoredState)
        dispatchCode(second.get(), launch, "Returned discovery rule", cursor = 0)
        second.start().restoreInstanceState(restoredState).postCreate(restoredState).resume().visible().use { active ->
            awaitField("Returned discovery rule")
            assertEquals(TextRange.Zero, selection("Returned discovery rule"))
            assertNotSame(firstModel, ViewModelProvider(active.get())[BookSourceEditViewModel::class.java])
            tab(R.string.source_tab_search)
            field("Search seed").assertExists()
            tab(R.string.source_tab_base)
            field("Unsaved book name").assertExists()
            action(R.string.action_save)
            await { active.get().isFinishing }
        }
        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(book.bookSourceUrl))
        assertEquals("Unsaved book name", saved.bookSourceName)
        assertEquals("Returned discovery rule", saved.ruleExplore?.name)
        assertEquals("Search seed", saved.ruleSearch?.name)
        await { !draftFile.exists() }
        await { CodeEditSessionStore.app.read(launch.sessionId()) == null }
    }

    @Test
    fun rssRestoresDraftAndAppliesAnExplicitClearWithAFreshViewModel() {
        val rss = seedRss()
        val intent = editorIntent(RssSourceEditActivity::class.java, rss.sourceUrl)
        val first = Robolectric.buildActivity(RssSourceEditActivity::class.java, intent).setup()
        awaitField(rss.sourceName)
        field(rss.sourceName).performTextReplacement("Unsaved RSS name")
        tab(R.string.source_tab_list)
        field("List title").performTextReplacement("Draft list title")
        action(R.string.edit_content)
        val launch = awaitLaunch { first.get() }
        assertSmallLaunch(launch)
        assertEquals("Draft list title", CodeEditSessionStore.app.read(launch.sessionId()))
        val state = Bundle()
        first.pause().saveInstanceState(state).stop().destroy()

        val restoredState = smallBundleRoundTrip(state)
        val second = Robolectric.buildActivity(RssSourceEditActivity::class.java, intent).create(restoredState)
        dispatchCode(second.get(), launch, "", cursor = 0)
        second.start().restoreInstanceState(restoredState).postCreate(restoredState).resume().visible().use { active ->
            val model = ViewModelProvider(active.get())[RssSourceEditViewModel::class.java]
            await { model.editor.initialized && model.editor.source.ruleTitle == null }
            assertEquals("Unsaved RSS name", model.editor.source.sourceName)
            assertEquals("Web rule", model.editor.source.ruleContent)
            action(R.string.action_save)
            await { active.get().isFinishing }
        }
        val saved = requireNotNull(appDb.rssSourceDao.getByKey(rss.sourceUrl))
        assertEquals("Unsaved RSS name", saved.sourceName)
        assertNull(saved.ruleTitle)
        assertEquals("Web rule", saved.ruleContent)
        await { CodeEditSessionStore.app.read(launch.sessionId()) == null }
    }

    @Test
    fun bookConfigurationChangeRetainsUndoAndAcceptsCursorOnlyAtZero() {
        val book = seedBook()
        ActivityScenario.launch<BookSourceEditActivity>(editorIntent(BookSourceEditActivity::class.java, book.bookSourceUrl))
            .use { scenario ->
                awaitField(book.bookSourceName)
                tab(R.string.source_tab_find)
                field("Discovery seed").performTextReplacement("Book working rule")
                select("Book working rule", 7, 7)
                val model = scenario.access { ViewModelProvider(it)[BookSourceEditViewModel::class.java] }
                val field = BookSourceFieldId(2, "name")
                assertEquals("Discovery seed", model.editor.undo.getValue(field).last.text)
                val revision = model.editor.revision
                action(R.string.edit_content)
                val launch = awaitLaunch { scenario.access { it } }
                assertEquals(7, launch.intent.getIntExtra("cursorPosition", -1))
                scenario.recreate()
                awaitField("Book working rule")
                assertSame(model, scenario.access { ViewModelProvider(it)[BookSourceEditViewModel::class.java] })
                assertEquals(TextRange(7), selection("Book working rule"))
                scenario.onActivity { dispatchCode(it, launch, null, cursor = 0) }
                await { model.editor.focusedField?.end == 0 }
                assertEquals(TextRange.Zero, selection("Book working rule"))
                assertEquals(revision, model.editor.revision)
                assertEquals(1, model.editor.undo.getValue(field).size)
                action(R.string.edit_content)
                val clear = awaitLaunch { scenario.access { it } }
                scenario.onActivity { dispatchCode(it, clear, "", cursor = 0) }
                await { model.editor.tabs[2].first { it.key == "name" }.value == "" }
                action(R.string.action_save)
                await { appDb.bookSourceDao.getBookSource(book.bookSourceUrl)?.ruleExplore?.name == null }
            }
    }

    @Test
    fun rssConfigurationChangeKeepsDraftWhenTheSubeditorCancels() {
        val rss = seedRss()
        ActivityScenario.launch<RssSourceEditActivity>(editorIntent(RssSourceEditActivity::class.java, rss.sourceUrl))
            .use { scenario ->
                awaitField(rss.sourceName)
                tab(R.string.source_tab_list)
                field("List title").performTextReplacement("Rss working title")
                select("Rss working title", 1, 3)
                val model = scenario.access { ViewModelProvider(it)[RssSourceEditViewModel::class.java] }
                val revision = model.editor.revision
                action(R.string.edit_content)
                val launch = awaitLaunch { scenario.access { it } }
                CodeEditSessionStore.app.writeWorkingText(launch.sessionId(), "Discard this title")
                scenario.recreate()
                awaitField("Rss working title")
                assertSame(model, scenario.access { ViewModelProvider(it)[RssSourceEditViewModel::class.java] })
                scenario.onActivity { it.activityResultRegistry.dispatchResult(launch.requestCode, Activity.RESULT_CANCELED, null) }
                await { CodeEditSessionStore.app.read(launch.sessionId()) == null }
                assertEquals(TextRange(1, 3), selection("Rss working title"))
                assertEquals(revision, model.editor.revision)
                assertEquals("List title", appDb.rssSourceDao.getByKey(rss.sourceUrl)?.ruleTitle)
                val undone = model.editor.history.undo("ruleTitle", RssEditorSnapshot("Rss working title", 1, 3))
                assertEquals("List title", undone?.text)
            }
    }

    @Test
    fun rssReturnsACursorOnlyResultAtZeroAfterTheParentResumes() {
        val rss = seedRss()
        ActivityScenario.launch<RssSourceEditActivity>(editorIntent(RssSourceEditActivity::class.java, rss.sourceUrl))
            .use { scenario ->
                awaitField(rss.sourceName)
                tab(R.string.source_tab_list)
                field("List title").performTextReplacement("Cursor rule")
                select("Cursor rule", 5, 5)
                val model = scenario.access { ViewModelProvider(it)[RssSourceEditViewModel::class.java] }
                val revision = model.editor.revision
                action(R.string.edit_content)
                val launch = awaitLaunch { scenario.access { it } }
                assertEquals(5, launch.intent.getIntExtra("cursorPosition", -1))
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { dispatchCode(it, launch, null, cursor = 0) }
                scenario.moveToState(Lifecycle.State.RESUMED)
                await { model.editor.focusedField?.end == 0 }
                assertEquals(TextRange.Zero, selection("Cursor rule"))
                assertEquals(revision, model.editor.revision)
                val undone = model.editor.history.undo("ruleTitle", RssEditorSnapshot("Cursor rule", 0, 0))
                assertEquals("List title", undone?.text)
            }
    }

    @Test
    fun staleResultsDoNotOverwriteNewerFieldsAndMissingFilesKeepTheDraft() {
        val rss = seedRss()
        ActivityScenario.launch<RssSourceEditActivity>(editorIntent(RssSourceEditActivity::class.java, rss.sourceUrl))
            .use { scenario ->
                awaitField(rss.sourceName)
                tab(R.string.source_tab_list)
                field("List title").performTextReplacement("Waiting title")
                action(R.string.edit_content)
                val stale = awaitLaunch { scenario.access { it } }
                field("Waiting title").performTextReplacement("Newer title")
                scenario.onActivity { dispatchCode(it, stale, "Stale result", cursor = 2) }
                await { CodeEditSessionStore.app.read(stale.sessionId()) == null }
                field("Newer title").assertExists()

                action(R.string.edit_content)
                val missing = awaitLaunch { scenario.access { it } }
                CodeEditSessionStore.app.delete(missing.sessionId())
                scenario.onActivity {
                    it.activityResultRegistry.dispatchResult(missing.requestCode, Activity.RESULT_OK, resultIntent(missing, true, 0))
                }
                awaitToast(R.string.source_editor_result_failed)
                field("Newer title").assertExists()
                assertEquals("List title", appDb.rssSourceDao.getByKey(rss.sourceUrl)?.ruleTitle)
            }
    }

    @Test
    fun losingADraftFileFallsBackToTheOriginalDatabaseSource() {
        val book = seedBook()
        val intent = editorIntent(BookSourceEditActivity::class.java, book.bookSourceUrl)
        val first = Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).setup()
        awaitField(book.bookSourceName)
        field(book.bookSourceName).performTextReplacement("Lost draft name")
        val state = Bundle()
        first.pause().saveInstanceState(state).stop()
        val id = requireNotNull(state.getString(SourceEditorSession.STATE_DRAFT_ID))
        val model = ViewModelProvider(first.get())[BookSourceEditViewModel::class.java]
        val file = File(model.getApplication<Application>().cacheDir, "source_editor_drafts/book/$id.json")
        await { file.isFile }
        first.destroy()
        assertTrue(file.delete())
        Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).setup(state).use {
            awaitField(book.bookSourceName)
            awaitToast(R.string.source_editor_draft_restore_failed)
            assertEquals(book.bookSourceName, appDb.bookSourceDao.getBookSource(book.bookSourceUrl)?.bookSourceName)
        }
    }

    @Test
    fun restoringAForegroundDraftPreservesTheSaveConflictCheckAndDiscardCleansItUp() {
        val book = seedBook()
        val intent = editorIntent(BookSourceEditActivity::class.java, book.bookSourceUrl)
        val first = Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).setup()
        awaitField(book.bookSourceName)
        field(book.bookSourceName).performTextReplacement("Foreground draft name")
        tab(R.string.source_tab_find)
        field("Discovery seed").performTextReplacement("Foreground discovery draft")
        select("Foreground discovery draft", 1, 2)
        val state = Bundle()
        first.pause().saveInstanceState(state).stop()
        val id = requireNotNull(state.getString(SourceEditorSession.STATE_DRAFT_ID))
        val model = ViewModelProvider(first.get())[BookSourceEditViewModel::class.java]
        val file = File(model.getApplication<Application>().cacheDir, "source_editor_drafts/book/$id.json")
        await { file.isFile && file.readText().contains("Foreground discovery draft") }
        first.destroy()
        appDb.bookSourceDao.update(book.copy(eventListener = true))

        Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).setup(state).use { active ->
            awaitField("Foreground discovery draft")
            assertEquals(TextRange(1, 2), selection("Foreground discovery draft"))
            action(R.string.action_save)
            awaitToast(R.string.source_editor_original_changed)
            field("Foreground discovery draft").assertExists()
            action(R.string.back)
            compose.onNodeWithText(context.getString(R.string.no)).performClick()
            await { active.get().isFinishing }
        }
        await { !file.exists() }
        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(book.bookSourceUrl))
        assertEquals(book.bookSourceName, saved.bookSourceName)
        assertEquals("Discovery seed", saved.ruleExplore?.name)
        assertTrue(saved.eventListener)
    }

    @Test
    fun documentPickerRestoresItsExactInsertionTargetAndConsumesTheUriOnce() = runBlocking {
        val initial = SourceEditorSession(context, "picker-test", PickerDraft::class.java, this)
        val field = SourceEditorField(3, "style", 4, 8)
        var launched = false
        initial.openChild(field, 7, null, { true }, { PickerDraft(initial.pending) }) { launched = true }
        assertTrue(launched)
        val state = Bundle().also(initial::writeInstanceState)

        val restored = SourceEditorSession(context, "picker-test", PickerDraft::class.java, this)
        assertTrue(restored.restoreInstanceState(state))
        restored.restoreRequest(requireNotNull(restored.read()).request)
        val applied = arrayListOf<Pair<SourceEditorField, Uri>>()
        val uri = Uri.parse("content://source-editor-fixture/document/rule")
        repeat(2) {
            restored.receiveFile(uri)
            restored.applyResult({ it.revision == 7L }, { _, _, _ -> error("Unexpected code result") },
                { target, selected -> applied.add(target to selected) }, { PickerDraft(restored.pending) })
        }
        assertEquals(listOf(field to uri), applied)
        restored.close()
    }

    private data class PickerDraft(@SerializedName("request") val request: SourceEditorRequest?)

}

abstract class SourceEditorActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    protected val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun markHelpSeen() {
        LocalConfig.ruleHelpVersionIsLast
    }

    protected fun seedBook() = BookSource(
        bookSourceUrl = "https://book.recovery.invalid", bookSourceName = "Book recovery",
        ruleExplore = ExploreRule(name = "Discovery seed"), ruleSearch = SearchRule(name = "Search seed"),
    ).also { appDb.bookSourceDao.insert(it) }

    protected fun seedRss() = RssSource(
        sourceUrl = "https://rss.recovery.invalid", sourceName = "RSS recovery",
        ruleTitle = "List title", ruleContent = "Web rule",
    ).also { appDb.rssSourceDao.insert(it) }

    protected fun <A : Activity> editorIntent(type: Class<A>, address: String) =
        Intent(context, type).putExtra("sourceUrl", address)

    protected fun field(text: String) = compose.onNode(hasSetTextAction() and hasText(text))

    protected fun awaitField(text: String) = await {
        compose.onAllNodes(hasSetTextAction() and hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun action(label: Int) = compose.onNodeWithContentDescription(context.getString(label)).performClick()

    protected fun tab(label: Int) = compose.onNode(
        hasText(context.getString(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    ).performClick()

    protected fun select(text: String, start: Int, end: Int) = field(text).performSemanticsAction(SemanticsActions.SetSelection) {
        assertTrue(it(start, end, false))
    }

    protected fun selection(text: String): TextRange = field(text).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    protected fun awaitLaunch(activity: () -> Activity): IntentForResult {
        var request: IntentForResult? = null
        await {
            request = shadowOf(activity()).nextStartedActivityForResult
            request != null
        }
        return requireNotNull(request)
    }

    protected fun dispatchCode(activity: ComponentActivity, launch: IntentForResult, text: String?, cursor: Int) {
        if (text != null) CodeEditSessionStore.app.writeWorkingText(launch.sessionId(), text)
        activity.activityResultRegistry.dispatchResult(launch.requestCode, Activity.RESULT_OK, resultIntent(launch, text != null, cursor))
    }

    protected fun resultIntent(launch: IntentForResult, changed: Boolean, cursor: Int) = Intent()
        .putExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID, launch.sessionId())
        .putExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, changed)
        .putExtra("cursorPosition", cursor)

    protected fun IntentForResult.sessionId() = requireNotNull(intent.getStringExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID))

    protected fun assertSmallLaunch(launch: IntentForResult) {
        assertTrue(launch.intent.getBooleanExtra(CodeEditActivity.EXTRA_SOURCE_FIELD_SESSION, false))
        assertFalse(launch.intent.hasExtra("text"))
        val parcel = Parcel.obtain()
        try {
            launch.intent.writeToParcel(parcel, 0)
            assertTrue(parcel.dataSize() < 4096)
        } finally {
            parcel.recycle()
        }
    }

    protected fun smallBundleRoundTrip(state: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            assertTrue("The source form state should contain small references", parcel.dataSize() < 32 * 1024)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    protected fun <A : Activity, T : Any> ActivityScenario<A>.access(read: (A) -> T): T {
        var result: T? = null
        onActivity { result = read(it) }
        return requireNotNull(result)
    }

    protected fun awaitToast(message: Int) = await {
        @Suppress("DEPRECATION")
        val text = ShadowToast.getLatestToast()?.view?.findViewById<TextView>(R.id.tv_text)?.text
        text?.contains(context.getString(message)) == true
    }

    protected fun await(condition: () -> Boolean) {
        compose.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
