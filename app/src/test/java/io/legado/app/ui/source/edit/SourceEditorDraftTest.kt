package io.legado.app.ui.source.edit

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.TextView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonNull
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportBookSourceViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.JsSourceEditActivity
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp", application = InterfaceFontTestApplication::class)
class SourceEditorDraftTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    @Before
    fun markHelpSeen() {
        LocalConfig.ruleHelpVersionIsLast
    }

    @Test
    fun unchangedBookSaveKeepsHiddenRulesEmptyFieldsAndLocalMetadata() {
        val before = bookSource()
        appDb.bookSourceDao.insert(before)

        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            expectCleanExit(editor)
        }
        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            saveAndAwaitResult(editor)
        }

        assertSameJson(before, appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
    }

    @Test
    fun clearingOneDiscoveryFieldKeepsHiddenAndUnrelatedRules() {
        val before = bookSource()
        appDb.bookSourceDao.insert(before)

        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            tab(R.string.source_tab_find)
            field("Discovery name").performTextReplacement("")
            saveAndAwaitResult(editor)
        }

        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
        assertTrue(saved.lastUpdateTime > before.lastUpdateTime)
        assertSameJson(
            before.copy(ruleExplore = before.ruleExplore?.copy(name = null)),
            saved.copy(lastUpdateTime = before.lastUpdateTime),
        )
    }

    @Test
    fun unchangedRssSaveKeepsAllFieldsAndLocalMetadata() {
        val before = rssSource()
        appDb.rssSourceDao.insert(before)

        openRss(before.sourceUrl).use { editor ->
            awaitField(before.sourceName)
            expectCleanExit(editor)
        }
        openRss(before.sourceUrl).use { editor ->
            awaitField(before.sourceName)
            saveAndAwaitResult(editor)
        }

        assertSameJson(before, appDb.rssSourceDao.getByKey(before.sourceUrl))
    }

    @Test
    fun clearingAnRssImageRuleSavesTheClearAndUpdatesItsTimestamp() {
        val before = rssSource()
        appDb.rssSourceDao.insert(before)

        openRss(before.sourceUrl).use { editor ->
            awaitField(before.sourceName)
            tab(R.string.source_tab_list)
            visibleField("Old image").performTextReplacement("")
            saveAndAwaitResult(editor)
        }

        val saved = requireNotNull(appDb.rssSourceDao.getByKey(before.sourceUrl))
        assertTrue(saved.lastUpdateTime > before.lastUpdateTime)
        assertSameJson(
            before.copy(ruleImage = null),
            saved.copy(lastUpdateTime = before.lastUpdateTime),
        )
    }

    @Test
    fun eachBookSwitchPromptsByItselfThenStopsPromptingWhenRestored() {
        val before = bookSource()
        appDb.bookSourceDao.insert(before)

        for (label in listOf(R.string.is_event_listener, R.string.custom_button, R.string.auto_save_cookie)) {
            openBook(before.bookSourceUrl).use { editor ->
                awaitField(before.bookSourceName)
                click(label)
                expectExitPromptAndStay(editor)
                click(label)
                expectCleanExit(editor)
            }
        }

        assertSameJson(before, appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
    }

    @Test
    fun eachPreviouslyMissedRssRulePromptsByItselfAndRestoringItAllowsExit() {
        val before = rssSource()
        appDb.rssSourceDao.insert(before)
        val cases = listOf(
            R.string.source_tab_base to "Old JS library",
            R.string.source_tab_list to "Old image",
            R.string.source_tab_web_view to "Old whitelist",
            R.string.source_tab_web_view to "Old blacklist",
            R.string.source_tab_web_view to "Old navigation rule",
        )

        for ((tab, original) in cases) {
            openRss(before.sourceUrl).use { editor ->
                awaitField(before.sourceName)
                tab(tab)
                visibleField(original).performTextReplacement("Changed field")
                expectExitPromptAndStay(editor)
                visibleField("Changed field").performTextReplacement(original)
                expectCleanExit(editor)
            }
        }

        assertSameJson(before, appDb.rssSourceDao.getByKey(before.sourceUrl))
    }

    @Test
    fun automaticCompletionRunsForSaveWithoutChangingWhatCountsAsAnEdit() {
        val book = bookSource().copy(ruleSearch = SearchRule(name = "tag.h1", updateTime = "Search time"))
        val rss = rssSource().copy(ruleTitle = "tag.h1")
        appDb.bookSourceDao.insert(book)
        appDb.rssSourceDao.insert(rss)

        openBook(book.bookSourceUrl).use { editor ->
            awaitField(book.bookSourceName)
            enableAutoComplete()
            expectCleanExit(editor)
        }
        openBook(book.bookSourceUrl).use { editor ->
            awaitField(book.bookSourceName)
            enableAutoComplete()
            saveAndAwaitResult(editor)
        }
        val savedBook = requireNotNull(appDb.bookSourceDao.getBookSource(book.bookSourceUrl))
        assertEquals("tag.h1@text", savedBook.ruleSearch?.name)
        assertEquals("Search time", savedBook.ruleSearch?.updateTime)

        openRss(rss.sourceUrl).use { editor ->
            awaitField(rss.sourceName)
            enableAutoComplete()
            expectCleanExit(editor)
        }
        openRss(rss.sourceUrl).use { editor ->
            awaitField(rss.sourceName)
            enableAutoComplete()
            saveAndAwaitResult(editor)
        }
        val savedRss = requireNotNull(appDb.rssSourceDao.getByKey(rss.sourceUrl))
        assertEquals("tag.h1@text", savedRss.ruleTitle)
        assertEquals("Old image@src", savedRss.ruleImage)
    }

    @Test
    fun savingAStaleFormKeepsTheEditorsDraftAndTheLatestDatabaseConfiguration() {
        val book = bookSource()
        val rss = rssSource()
        appDb.bookSourceDao.insert(book)
        appDb.rssSourceDao.insert(rss)
        val currentBook = book.copy(customButton = true)
        val currentRss = rss.copy(ruleImage = "New image from another edit")

        openBook(book.bookSourceUrl).use { editor ->
            awaitField(book.bookSourceName)
            field(book.bookSourceName).performTextReplacement("Book draft")
            appDb.bookSourceDao.update(currentBook)
            action(R.string.action_save)
            awaitStaleSourceWarning()
            field("Book draft").assertExists()
            expectExitPromptAndStay(editor)
            assertSameJson(currentBook, appDb.bookSourceDao.getBookSource(book.bookSourceUrl))
        }

        ShadowToast.reset()
        openRss(rss.sourceUrl).use { editor ->
            awaitField(rss.sourceName)
            field(rss.sourceName).performTextReplacement("RSS draft")
            appDb.rssSourceDao.update(currentRss)
            action(R.string.action_save)
            awaitStaleSourceWarning()
            field("RSS draft").assertExists()
            expectExitPromptAndStay(editor)
            assertSameJson(currentRss, appDb.rssSourceDao.getByKey(rss.sourceUrl))
        }
    }

    @Test
    fun pasteAndCopyKeepHiddenRulesAndDistinguishOmittedWeightFromExplicitZero() {
        val before = bookSource().copy(ruleBookInfo = null, exploreScreen = null, ruleReview = null)
        appDb.bookSourceDao.insert(before)
        val incoming = bookSource().copy(
            bookSourceName = "Imported book",
            customOrder = 50,
            respondTime = 2000,
            weight = 0,
        )
        val withoutWeight = GSON.toJsonTree(incoming).asJsonObject.apply {
            remove("weight")
            add("enabledCookieJar", JsonNull.INSTANCE)
        }

        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            paste("[$withoutWeight]")
            awaitField(incoming.bookSourceName)
            action(R.string.more)
            click(R.string.copy_source)
            val exported = GSON.fromJson(
                clipboard.primaryClip?.getItemAt(0)?.text.toString(),
                BookSource::class.java,
            )
            assertEquals(incoming.ruleSearch, exported.ruleSearch)
            assertEquals(incoming.ruleExplore, exported.ruleExplore)
            assertEquals(incoming.ruleBookInfo, exported.ruleBookInfo)
            assertEquals(incoming.exploreScreen, exported.exploreScreen)
            assertEquals(incoming.ruleReview, exported.ruleReview)
            assertEquals(before.weight, exported.weight)
            assertNull(exported.enabledCookieJar)
            assertTextShareMatchesCopy(editor, exported)
            saveAndAwaitResult(editor)
        }

        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
        assertTrue(saved.lastUpdateTime > before.lastUpdateTime)
        assertSameJson(
            incoming.copy(
                customOrder = before.customOrder,
                respondTime = before.respondTime,
                weight = before.weight,
                lastUpdateTime = saved.lastUpdateTime,
            ),
            saved,
        )

        openBook(before.bookSourceUrl).use { editor ->
            awaitField(incoming.bookSourceName)
            val zeroWeightImport = saved.copy(bookSourceName = "Weight import", weight = 0)
            paste(fixtureJson(zeroWeightImport))
            awaitField(zeroWeightImport.bookSourceName)
            field(zeroWeightImport.bookSourceName).performTextReplacement(saved.bookSourceName)
            expectExitPromptAndStay(editor)
            saveAndAwaitResult(editor)
        }

        val zeroWeight = requireNotNull(appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
        assertTrue(zeroWeight.lastUpdateTime >= saved.lastUpdateTime)
        assertSameJson(saved.copy(weight = 0, lastUpdateTime = zeroWeight.lastUpdateTime), zeroWeight)
    }

    @Test
    fun pasteWithUnsupportedWeightStillImportsTheSourceAndKeepsTheStoredWeight() {
        val before = bookSource()
        appDb.bookSourceDao.insert(before)
        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            for ((weight, name) in listOf("" to "Empty weight", "5" to "String weight")) {
                val incoming = GSON.toJsonTree(before.copy(bookSourceName = name)).asJsonObject.apply {
                    addProperty("weight", weight)
                }
                paste(incoming.toString())
                awaitField(name)
            }
            saveAndAwaitResult(editor)
        }
        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
        assertEquals("String weight", saved.bookSourceName)
        assertEquals(before.weight, saved.weight)
    }

    @Test
    fun scanOfLegacyBookJsonStillConvertsRulesAndSavesAnExplicitWeight() {
        val before = bookSource()
        appDb.bookSourceDao.insert(before)
        val legacy = """{
            "bookSourceUrl": "${before.bookSourceUrl}",
            "bookSourceName": "Legacy book",
            "ruleSearchUrl": "${before.bookSourceUrl}/search?q=searchKey",
            "ruleSearchList": "class.results",
            "ruleSearchName": "tag.h3@text",
            "weight": 5
        }""".trimIndent()

        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            scan(editor, legacy)
            awaitField("Legacy book")
            saveAndAwaitResult(editor)
        }

        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
        assertEquals("class.results", saved.ruleSearch?.bookList)
        assertEquals("tag.h3@text", saved.ruleSearch?.name)
        assertEquals(5, saved.weight)
        assertEquals(before.customOrder, saved.customOrder)
    }

    @Test
    fun qrImportPreviewsScriptSourcesAndCancelKeepsTheParentDraft() {
        val before = bookSource()
        appDb.bookSourceDao.insert(before)
        val script = before.copy(
            bookSourceUrl = "https://script.draft-test.invalid",
            bookSourceName = "Imported script",
            customOrder = before.customOrder + 1,
            mainJs = "// fixture with a main script",
        )

        openBook(before.bookSourceUrl).use { editor ->
            awaitField(before.bookSourceName)
            field(before.bookSourceName).performTextReplacement("Parent draft")

            scan(editor, fixtureJson(script))
            val cancelledFile = awaitScriptPreview(editor, script)
            click(R.string.cancel)
            awaitPreviewClosed()
            await { !cancelledFile.exists() }
            field("Parent draft").assertExists()
            assertNull(appDb.bookSourceDao.getBookSource(script.bookSourceUrl))
            assertSameJson(before, appDb.bookSourceDao.getBookSource(before.bookSourceUrl))

            scan(editor, fixtureJson(script))
            val confirmedFile = awaitScriptPreview(editor, script)
            click(R.string.book_source_import_action)
            awaitPreviewClosed()
            await { !confirmedFile.exists() }
            field("Parent draft").assertExists()
            val saved = requireNotNull(appDb.bookSourceDao.getBookSource(script.bookSourceUrl))
            assertEquals(script.mainJs, saved.mainJs)
            assertEquals(script.ruleReview, saved.ruleReview)
            assertEquals(script.ruleExplore, saved.ruleExplore)
            assertEquals(script.exploreScreen, saved.exploreScreen)
            assertNull(saved.enabledCookieJar)
            assertSameJson(before, appDb.bookSourceDao.getBookSource(before.bookSourceUrl))
        }

        val intent = Intent(context, BookSourceEditActivity::class.java)
            .putExtra("sourceUrl", script.bookSourceUrl)
        Robolectric.buildActivity(BookSourceEditActivity::class.java, intent).use { controller ->
            val activity = controller.create().get()
            assertTrue(activity.isFinishing)
            val opened = shadowOf(activity).nextStartedActivity
            assertEquals(JsSourceEditActivity::class.java.name, opened.component?.className)
            assertEquals(script.bookSourceUrl, opened.getStringExtra("sourceUrl"))
        }
    }

    private fun bookSource() = BookSource(
        bookSourceUrl = "https://book.draft-test.invalid",
        bookSourceName = "Original book",
        bookSourceGroup = "",
        enabledCookieJar = null,
        customOrder = 7,
        respondTime = 850L,
        weight = 11,
        lastUpdateTime = 42L,
        ruleSearch = SearchRule(name = "Search name", updateTime = "Search time"),
        ruleExplore = ExploreRule(name = "Discovery name", updateTime = "Discovery time"),
        ruleBookInfo = BookInfoRule(name = "Details name", updateTime = "Details time"),
        exploreScreen = "Discovery filter",
        ruleReview = ReviewRule(reviewUrl = "Review URL"),
    )

    private fun rssSource() = RssSource(
        sourceUrl = "https://rss.draft-test.invalid",
        sourceName = "Original RSS",
        sourceGroup = "",
        enabledCookieJar = null,
        customOrder = 3,
        lastUpdateTime = 24L,
        jsLib = "Old JS library",
        ruleImage = "Old image",
        contentWhitelist = "Old whitelist",
        contentBlacklist = "Old blacklist",
        shouldOverrideUrlLoading = "Old navigation rule",
    )

    private fun openBook(address: String) = ActivityScenario.launch<BookSourceEditActivity>(
        Intent(context, BookSourceEditActivity::class.java).putExtra("sourceUrl", address),
    )

    private fun openRss(address: String) = ActivityScenario.launch<RssSourceEditActivity>(
        Intent(context, RssSourceEditActivity::class.java).putExtra("sourceUrl", address),
    )

    private fun paste(json: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("source", json))
        action(R.string.more)
        click(R.string.paste_source)
    }

    private fun assertTextShareMatchesCopy(editor: ActivityScenario<BookSourceEditActivity>, copied: BookSource) {
        action(R.string.more)
        click(R.string.source_editor_import_share)
        click(R.string.str_share)
        editor.onActivity { activity ->
            val chooser = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertEquals(Intent.ACTION_SEND, send?.action)
            val shared = GSON.fromJson(send?.getStringExtra(Intent.EXTRA_TEXT), BookSource::class.java)
            assertSameJson(copied, shared)
        }
    }

    private fun scan(editor: ActivityScenario<BookSourceEditActivity>, json: String) {
        action(R.string.more)
        click(R.string.source_editor_import_share)
        click(R.string.import_by_qr_code)
        editor.onActivity { activity ->
            val shadow = shadowOf(activity)
            val request = shadow.nextStartedActivityForResult.intent
            shadow.receiveResult(request, Activity.RESULT_OK, Intent().putExtra("result", json))
        }
    }

    private fun awaitScriptPreview(
        editor: ActivityScenario<BookSourceEditActivity>,
        source: BookSource,
    ): File {
        await { compose.onAllNodes(hasText(source.bookSourceName)).fetchSemanticsNodes().isNotEmpty() }
        lateinit var file: File
        editor.onActivity { activity ->
            val dialog = activity.supportFragmentManager.findFragmentByTag("book-source-editor-import")
                as ImportBookSourceDialog
            val uri = Uri.parse(requireNotNull(dialog.requireArguments().getString("source")))
            assertEquals("file", uri.scheme)
            file = File(requireNotNull(uri.path))
            assertTrue(file.name.startsWith(ImportBookSourceDialog.EDITOR_PREVIEW_PREFIX))
            assertTrue(file.exists())
            val model = ViewModelProvider(dialog)[ImportBookSourceViewModel::class.java]
            val preview = GSON.fromJson(model.previewSourceCode(0), BookSource::class.java)
            assertEquals(source.mainJs, preview.mainJs)
            assertEquals(source.ruleReview, preview.ruleReview)
            assertEquals(source.ruleBookInfo, preview.ruleBookInfo)
            assertNull(preview.enabledCookieJar)
        }
        return file
    }

    private fun awaitPreviewClosed() = await {
        compose.onAllNodes(hasText(context.getString(R.string.import_book_source))).fetchSemanticsNodes().isEmpty()
    }

    private fun fixtureJson(source: BookSource): String =
        GSON.newBuilder().serializeNulls().create().toJson(source)

    private fun <A : Activity> saveAndAwaitResult(editor: ActivityScenario<A>) {
        lateinit var activity: A
        editor.onActivity { activity = it }
        action(R.string.action_save)
        await { activity.isFinishing && shadowOf(activity).resultCode == Activity.RESULT_OK }
    }

    private fun <A : Activity> expectExitPromptAndStay(editor: ActivityScenario<A>) {
        lateinit var activity: A
        editor.onActivity { activity = it }
        action(R.string.back)
        assertFalse(activity.isFinishing)
        compose.onNodeWithText(context.getString(R.string.exit_no_save)).assertExists()
        click(R.string.yes)
    }

    private fun <A : Activity> expectCleanExit(editor: ActivityScenario<A>) {
        lateinit var activity: A
        editor.onActivity { activity = it }
        action(R.string.back)
        assertTrue(activity.isFinishing)
    }

    private fun enableAutoComplete() {
        action(R.string.more)
        click(R.string.setting)
        click(R.string.auto_complete)
        click(R.string.complete)
    }

    private fun awaitStaleSourceWarning() = await {
        @Suppress("DEPRECATION")
        val message = ShadowToast.getLatestToast()?.view?.findViewById<TextView>(R.id.tv_text)?.text
        message?.contains(context.getString(R.string.source_editor_original_changed)) == true
    }

    private fun assertSameJson(expected: Any, actual: Any?) {
        assertEquals(GSON.toJsonTree(expected), GSON.toJsonTree(actual))
    }

    private fun action(label: Int) {
        compose.onNodeWithContentDescription(context.getString(label)).performClick()
    }

    private fun click(label: Int) = compose.onNodeWithText(context.getString(label)).performClick()

    private fun tab(label: Int) = compose.onNode(
        hasText(context.getString(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    ).performClick()

    private fun field(value: String) = compose.onNode(hasSetTextAction() and hasText(value))

    private fun visibleField(value: String) = field(value).also {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasSetTextAction() and hasText(value))
    }

    private fun awaitField(value: String) = await {
        compose.onAllNodes(hasSetTextAction() and hasText(value)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
