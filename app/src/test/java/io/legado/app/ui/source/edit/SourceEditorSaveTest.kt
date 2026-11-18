package io.legado.app.ui.source.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.help.CacheManager
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.source.BookSourceStorageScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp", application = InterfaceFontTestApplication::class)
class SourceEditorSaveTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val sql get() = appDb.openHelper.writableDatabase

    @Before
    fun markHelpSeen() {
        LocalConfig.ruleHelpVersionIsLast
    }

    @Test
    fun sameAddressBookSaveUpdatesTheRowAndPreservesRatingsAndLiveMetrics() {
        val origin = "https://book-same.save.invalid"
        val source = BookSource(bookSourceUrl = origin, bookSourceName = "Book before", lastUpdateTime = 24)
        appDb.bookSourceDao.insert(source)
        SourceConfig.setBookScore(origin, "A book", "Author", 7)
        val variable = "sourceVariable_$origin"
        appDb.cacheDao.insert(Cache(variable, "book setting"))
        val cookie = cookieKey(origin)
        appDb.cookieDao.insert(Cookie(cookie, "session=test"))

        openBook(origin).use { editor ->
            awaitField(source.bookSourceName)
            appDb.bookSourceDao.update(source.copy(customOrder = 12, weight = 4, respondTime = 600L))
            field(source.bookSourceName).performTextReplacement("Book after")
            withTrigger(
                "book_same_must_update",
                "BEFORE DELETE ON book_sources WHEN OLD.bookSourceUrl = '$origin'",
                "existing book row was deleted",
            ) {
                saveAndAwaitResult(editor)
            }
        }
        val saved = requireNotNull(appDb.bookSourceDao.getBookSource(origin))
        assertEquals("Book after", saved.bookSourceName)
        assertEquals(12, saved.customOrder)
        assertEquals(4, saved.weight)
        assertEquals(600L, saved.respondTime)
        assertTrue(saved.lastUpdateTime > source.lastUpdateTime)
        assertEquals(7, SourceConfig.getBookScore(origin, "A book", "Author"))
        assertEquals(7, SourceConfig.getSourceScore(origin))
        assertEquals("book setting", appDb.cacheDao.get(variable)?.value)
        assertEquals("session=test", appDb.cookieDao.get(cookie)?.cookie)
    }

    @Test
    fun sameAddressRssSaveUpdatesTheRowAndPreservesArticlesStarsAndOrder() {
        val origin = "https://rss-same.save.invalid"
        val source = RssSource(sourceUrl = origin, sourceName = "RSS before")
        appDb.rssSourceDao.insert(source)
        val article = seedRssArticle(origin)
        val variable = "sourceVariable_$origin"
        appDb.cacheDao.insert(Cache(variable, "rss setting"))
        openRss(origin).use { editor ->
            awaitField(source.sourceName)
            appDb.rssSourceDao.update(source.copy(customOrder = 19))
            field(source.sourceName).performTextReplacement("RSS after")
            withTrigger(
                "rss_same_must_update",
                "BEFORE DELETE ON rssSources WHEN OLD.sourceUrl = '$origin'",
                "existing rss row was deleted",
            ) {
                saveAndAwaitResult(editor)
            }
        }
        val saved = requireNotNull(appDb.rssSourceDao.getByKey(origin))
        assertEquals("RSS after", saved.sourceName)
        assertEquals(19, saved.customOrder)
        assertRssArticleAt(article, origin)
        assertEquals("rss setting", appDb.cacheDao.get(variable)?.value)
    }

    @Test
    fun bookAddressConflictKeepsBothSourcesAndBlocksDebug() {
        val origin = "https://book-original.save.invalid"
        val occupied = "https://book-occupied.save.invalid"
        appDb.bookSourceDao.insert(
            BookSource(bookSourceUrl = origin, bookSourceName = "Original book source"),
            BookSource(bookSourceUrl = occupied, bookSourceName = "Occupied book source"),
        )
        SourceConfig.setBookScore(origin, "A book", "Author", 5)
        openBook(origin).use { editor ->
            awaitField("Original book source")
            field(origin).performTextReplacement(occupied)
            action(R.string.debug_source)
            awaitToast(context.getString(R.string.source_editor_address_exists))
            assertEquals("Original book source", appDb.bookSourceDao.getBookSource(origin)?.bookSourceName)
            assertEquals("Occupied book source", appDb.bookSourceDao.getBookSource(occupied)?.bookSourceName)
            assertEquals(5, SourceConfig.getSourceScore(origin))
            field(occupied).assertExists()
            editor.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNull(shadowOf(activity).nextStartedActivity)
            }

            appDb.bookSourceDao.delete(origin)
            field(occupied).performTextReplacement(origin)
            save()
            awaitToast(context.getString(R.string.source_editor_original_missing))
            assertNull(appDb.bookSourceDao.getBookSource(origin))
        }
    }

    @Test
    fun rssAddressConflictKeepsBothSourcesAndTheirArticles() {
        val origin = "https://rss-original.save.invalid"
        val occupied = "https://rss-occupied.save.invalid"
        appDb.rssSourceDao.insert(
            RssSource(sourceUrl = origin, sourceName = "Original RSS source"),
            RssSource(sourceUrl = occupied, sourceName = "Occupied RSS source"),
        )
        val article = seedRssArticle(origin)
        val occupiedArticle = seedRssArticle(occupied)
        openRss(origin).use { editor ->
            awaitField("Original RSS source")
            field(origin).performTextReplacement(occupied)
            save()
            awaitToast(context.getString(R.string.source_editor_address_exists))
            field(occupied).assertExists()
            editor.onActivity { assertFalse(it.isFinishing) }
            assertEquals("Original RSS source", appDb.rssSourceDao.getByKey(origin)?.sourceName)
            assertEquals("Occupied RSS source", appDb.rssSourceDao.getByKey(occupied)?.sourceName)
            assertRssArticleAt(article, origin)
            assertRssArticleAt(occupiedArticle, occupied)
        }
    }

    @Test
    fun newSourcesCannotReplaceAnOccupiedAddress() {
        val address = "https://new-address.save.invalid"
        appDb.bookSourceDao.insert(BookSource(bookSourceUrl = address, bookSourceName = "Existing book"))
        appDb.rssSourceDao.insert(RssSource(sourceUrl = address, sourceName = "Existing RSS"))

        openBook(null).use { editor ->
            val inputs = compose.onAllNodes(hasSetTextAction())
            inputs[0].performTextReplacement(address)
            inputs[1].performTextReplacement("New book draft")
            save()
            awaitToast(context.getString(R.string.source_editor_address_exists))
            assertEquals("Existing book", appDb.bookSourceDao.getBookSource(address)?.bookSourceName)
            field("New book draft").assertExists()
            field(address).performTextReplacement("$address/new")
            saveAndAwaitResult(editor)
            assertEquals("New book draft", appDb.bookSourceDao.getBookSource("$address/new")?.bookSourceName)
        }

        ShadowToast.reset()
        openRss(null).use { editor ->
            val inputs = compose.onAllNodes(hasSetTextAction())
            inputs[0].performTextReplacement("New RSS draft")
            inputs[1].performTextReplacement(address)
            save()
            awaitToast(context.getString(R.string.source_editor_address_exists))
            assertEquals("Existing RSS", appDb.rssSourceDao.getByKey(address)?.sourceName)
            field("New RSS draft").assertExists()
            field(address).performTextReplacement("$address/new")
            saveAndAwaitResult(editor)
            assertEquals("New RSS draft", appDb.rssSourceDao.getByKey("$address/new")?.sourceName)
        }
    }

    @Test
    fun bookRenameRollsBackLateWriteFailureThenCanRetryTheSameDraft() {
        val origin = "https://book-rollback.save.invalid"
        val target = "$origin/new"
        val neighbor = "${origin}_neighbor"
        appDb.bookSourceDao.insert(
            BookSource(bookSourceUrl = origin, bookSourceName = "Book rename"),
            BookSource(bookSourceUrl = neighbor, bookSourceName = "Neighbor book"),
        )
        appDb.rssSourceDao.insert(RssSource(sourceUrl = target, sourceName = "Independent RSS"))
        val variable = "sourceVariable_$origin"
        val neighborVariable = "v_${neighbor}_setting"
        val cache = "book_source_cache_${BookSourceStorageScope.namespace(origin)}:legacy"
        val cookie = cookieKey(origin)
        appDb.cacheDao.insert(Cache(variable, "setting"), Cache(neighborVariable, "neighbor"), Cache(cache, "legacy"))
        appDb.cookieDao.insert(Cookie(cookie, "session=old"))
        CacheManager.putMemory(variable, "setting")
        CacheManager.putMemory(cache, "legacy")
        CacheManager.putMemory("$cookie:persistent", "session=old")
        SourceConfig.setBookScore(origin, "A book", "Author", 8)
        SourceConfig.setBookScore(neighbor, "A book", "Author", 2)

        openBook(origin).use { editor ->
            awaitField("Book rename")
            field(origin).performTextReplacement(target)
            withTrigger(
                "book_rename_failure",
                "BEFORE DELETE ON caches WHEN OLD.[key] = '$cache'",
                "injected book rename failure",
            ) {
                save()
                awaitToast("injected book rename failure")
                field(target).assertExists()
                assertEquals("Book rename", appDb.bookSourceDao.getBookSource(origin)?.bookSourceName)
                assertNull(appDb.bookSourceDao.getBookSource(target))
                assertEquals("setting", appDb.cacheDao.get(variable)?.value)
                assertEquals("legacy", appDb.cacheDao.get(cache)?.value)
                assertEquals("session=old", appDb.cookieDao.get(cookie)?.cookie)
                assertEquals("setting", CacheManager.getFromMemory(variable))
                assertEquals("legacy", CacheManager.getFromMemory(cache))
                assertEquals("session=old", CacheManager.getFromMemory("$cookie:persistent"))
                assertEquals(8, SourceConfig.getSourceScore(origin))
            }
            action(R.string.debug_source)
            await { appDb.bookSourceDao.getBookSource(target) != null }
            var opened: Intent? = null
            await {
                editor.onActivity { opened = opened ?: shadowOf(it).nextStartedActivity }
                opened != null
            }
            assertEquals(BookSourceDebugActivity::class.java.name, opened?.component?.className)
            assertEquals(target, opened?.getStringExtra("key"))
            assertNull(appDb.bookSourceDao.getBookSource(origin))
            assertNull(appDb.cacheDao.get(variable))
            assertNull(appDb.cacheDao.get(cache))
            assertNull(appDb.cookieDao.get(cookie))
            assertNull(CacheManager.getFromMemory(variable))
            assertNull(CacheManager.getFromMemory(cache))
            assertNull(CacheManager.getFromMemory("$cookie:persistent"))
            assertEquals(0, SourceConfig.getSourceScore(origin))
            assertEquals(2, SourceConfig.getSourceScore(neighbor))
            assertEquals("neighbor", appDb.cacheDao.get(neighborVariable)?.value)
            assertEquals("Independent RSS", appDb.rssSourceDao.getByKey(target)?.sourceName)

            field("Book rename").performTextReplacement("Book after debug")
            saveAndAwaitResult(editor)
            assertEquals("Book after debug", appDb.bookSourceDao.getBookSource(target)?.bookSourceName)
        }
    }

    @Test
    fun rssRenameRollsBackLateWriteFailureAndMovesRelatedRowsOnRetry() {
        val origin = "https://rss-rollback.save.invalid"
        val target = "$origin/new"
        val neighbor = "${origin}_neighbor"
        appDb.rssSourceDao.insert(RssSource(sourceUrl = origin, sourceName = "RSS rename"))
        appDb.bookSourceDao.insert(BookSource(bookSourceUrl = neighbor, bookSourceName = "Neighbor book"))
        val article = seedRssArticle(origin)
        val variable = "sourceVariable_$origin"
        val neighborVariable = "v_${neighbor}_setting"
        appDb.cacheDao.insert(Cache(variable, "setting"), Cache(neighborVariable, "neighbor"))
        CacheManager.putMemory(variable, "setting")

        openRss(origin).use { editor ->
            awaitField("RSS rename")
            field(origin).performTextReplacement(target)
            withTrigger(
                "rss_rename_failure",
                "BEFORE DELETE ON caches WHEN OLD.[key] = '$variable'",
                "injected rss rename failure",
            ) {
                save()
                awaitToast("injected rss rename failure")
                field(target).assertExists()
                assertNotNull(appDb.rssSourceDao.getByKey(origin))
                assertNull(appDb.rssSourceDao.getByKey(target))
                assertRssArticleAt(article, origin)
                assertNull(appDb.rssStarDao.get(target, article.link))
                assertNull(appDb.rssArticleDao.get(target, article.link, article.sort))
                assertEquals("setting", appDb.cacheDao.get(variable)?.value)
                assertEquals("setting", CacheManager.getFromMemory(variable))
            }
            saveAndAwaitResult(editor)
        }
        assertNull(appDb.rssSourceDao.getByKey(origin))
        assertEquals("RSS rename", appDb.rssSourceDao.getByKey(target)?.sourceName)
        assertRssArticleAt(article, target)
        assertNull(appDb.rssStarDao.get(origin, article.link))
        assertNull(appDb.rssArticleDao.get(origin, article.link, article.sort))
        assertNull(appDb.cacheDao.get(variable))
        assertNull(CacheManager.getFromMemory(variable))
        assertEquals("neighbor", appDb.cacheDao.get(neighborVariable)?.value)
    }

    @Test
    fun repeatedSaveWhileBusyDoesNotOverwriteEditsMadeDuringTheSave() {
        val address = "https://pending.save.invalid"
        appDb.bookSourceDao.insert(BookSource(bookSourceUrl = address, bookSourceName = "Pending book"))
        appDb.rssSourceDao.insert(RssSource(sourceUrl = address, sourceName = "Pending RSS"))
        openBook(address).use { editor ->
            awaitField("Pending book")
            field("Pending book").performTextReplacement("First book save")
            holdWriteTransaction {
                save()
                field("First book save").performTextReplacement("New book edit")
                save()
            }
            await {
                var lastSaved: String? = null
                editor.onActivity {
                    lastSaved = ViewModelProvider(it)[BookSourceEditViewModel::class.java].bookSource?.bookSourceName
                }
                lastSaved == "First book save"
            }
            assertEquals("First book save", appDb.bookSourceDao.getBookSource(address)?.bookSourceName)
            field("New book edit").assertExists()
            saveAndAwaitResult(editor)
            assertEquals("New book edit", appDb.bookSourceDao.getBookSource(address)?.bookSourceName)
        }

        openRss(address).use { editor ->
            awaitField("Pending RSS")
            field("Pending RSS").performTextReplacement("First RSS save")
            holdWriteTransaction {
                save()
                field("First RSS save").performTextReplacement("New RSS edit")
                save()
            }
            await {
                var lastSaved: String? = null
                editor.onActivity {
                    lastSaved = ViewModelProvider(it)[RssSourceEditViewModel::class.java].rssSource?.sourceName
                }
                lastSaved == "First RSS save"
            }
            assertEquals("First RSS save", appDb.rssSourceDao.getByKey(address)?.sourceName)
            field("New RSS edit").assertExists()
            saveAndAwaitResult(editor)
            assertEquals("New RSS edit", appDb.rssSourceDao.getByKey(address)?.sourceName)
        }
    }

    private fun openBook(key: String?) = ActivityScenario.launch<BookSourceEditActivity>(
        Intent(context, BookSourceEditActivity::class.java).putExtra("sourceUrl", key)
    )

    private fun openRss(key: String?) = ActivityScenario.launch<RssSourceEditActivity>(
        Intent(context, RssSourceEditActivity::class.java).putExtra("sourceUrl", key)
    )

    private fun save() = action(R.string.action_save)

    private fun <A : Activity> saveAndAwaitResult(editor: ActivityScenario<A>) {
        lateinit var activity: A
        editor.onActivity { activity = it }
        save()
        await {
            activity.isFinishing && shadowOf(activity).resultCode == Activity.RESULT_OK
        }
    }

    private fun action(label: Int) {
        compose.onNodeWithContentDescription(context.getString(label)).performClick()
    }

    private fun field(value: String) = compose.onNode(hasSetTextAction() and hasText(value))

    private fun awaitField(value: String) = await {
        compose.onAllNodes(hasSetTextAction() and hasText(value)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitToast(message: String) = await {
        val toast = ShadowToast.getLatestToast()
        @Suppress("DEPRECATION")
        toast?.view?.findViewById<TextView>(R.id.tv_text)?.text?.contains(message) == true
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun withTrigger(name: String, event: String, message: String, action: () -> Unit) {
        sql.execSQL("CREATE TRIGGER $name $event BEGIN SELECT RAISE(ABORT, '$message'); END")
        try {
            action()
        } finally {
            sql.execSQL("DROP TRIGGER $name")
        }
    }

    private fun holdWriteTransaction(action: () -> Unit) {
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val transaction = executor.submit {
            appDb.runInTransaction {
                acquired.countDown()
                check(release.await(20, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            action()
        } finally {
            release.countDown()
            executor.shutdown()
            transaction.get(5, TimeUnit.SECONDS)
        }
    }

    private fun seedRssArticle(origin: String): RssArticle {
        return RssArticle(origin = origin, sort = "test sort", title = origin, link = "$origin/article").also {
            appDb.rssArticleDao.insert(it)
            appDb.rssStarDao.insert(it.toStar())
        }
    }

    private fun assertRssArticleAt(article: RssArticle, origin: String) {
        assertEquals(article.title, appDb.rssArticleDao.get(origin, article.link, article.sort)?.title)
        assertEquals(article.title, appDb.rssStarDao.get(origin, article.link)?.title)
    }

    private fun cookieKey(origin: String): String {
        return "book_source_cookie_${BookSourceStorageScope.namespace(origin)}:save.invalid"
    }
}
