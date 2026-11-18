package io.legado.app.ui.source.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeEditSessionStore
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.UrlOptionInput
import io.legado.app.ui.widget.dialog.toUrlOption
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp", application = InterfaceFontTestApplication::class)
class SourceEditorFieldRoutingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun markFirstRunHelpSeen() {
        LocalConfig.ruleHelpVersionIsLast
    }

    @Test
    fun discoveryAndDetailsKeepTheirOwnRulesAfterSavingAndReopening() {
        val source = BookSource(
            bookSourceUrl = "https://book.example.invalid",
            bookSourceName = "Book editor test",
            ruleSearch = SearchRule(bookList = "search-list", name = "search-name"),
            ruleExplore = ExploreRule(bookList = "old-list", name = "old-discovery"),
            ruleBookInfo = BookInfoRule(name = "old-details"),
        )
        appDb.bookSourceDao.insert(source)
        val intent = Intent(context, BookSourceEditActivity::class.java)
            .putExtra("sourceUrl", source.bookSourceUrl)
        ActivityScenario.launch<BookSourceEditActivity>(intent).use { scenario ->
            awaitField(source.bookSourceName)
            tab(R.string.source_tab_find)
            field("old-list").performTextReplacement("discovery-list")
            field("old-discovery").performTextReplacement("discovery-name")
            compose.onNodeWithContentDescription(context.getString(R.string.edit_content)).performClick()
            var request: Intent? = null
            awaitSave {
                scenario.onActivity { request = shadowOf(it).nextStartedActivityForResult?.intent }
                request != null
            }
            val launched = checkNotNull(request)
            val id = checkNotNull(launched.getStringExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID))
            assertNull(launched.getStringExtra("text"))
            assertEquals("discovery-name", CodeEditSessionStore.app.read(id))
            CodeEditSessionStore.app.writeWorkingText(id, "expanded-name")
            scenario.onActivity { activity ->
                shadowOf(activity).receiveResult(launched, Activity.RESULT_OK, Intent()
                    .putExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID, id)
                    .putExtra("cursorPosition", 0))
            }
            awaitField("expanded-name")
            tab(R.string.source_tab_info)
            field("old-details").performTextReplacement("details-name")
            tab(R.string.source_tab_search)
            field("search-list").assertExists()
            field("search-name").assertExists()
            save()
            awaitSave {
                appDb.bookSourceDao.getBookSource(source.bookSourceUrl)?.ruleBookInfo?.name == "details-name"
            }
        }
        ActivityScenario.launch<BookSourceEditActivity>(intent).use {
            awaitField(source.bookSourceName)
            tab(R.string.source_tab_find)
            field("discovery-list").assertExists()
            field("expanded-name").assertExists()
            tab(R.string.source_tab_search)
            field("search-list").assertExists()
            field("search-name").assertExists()
            tab(R.string.source_tab_info)
            field("details-name").assertExists()
        }
    }

    @Test
    fun rssStartListAndWebFieldsSaveSeparately() {
        val source = RssSource(
            sourceUrl = "https://rss.example.invalid",
            sourceName = "RSS editor test",
            startHtml = "old-start", ruleTitle = "old-title", ruleContent = "old-content",
        )
        appDb.rssSourceDao.insert(source)
        val intent = Intent(context, RssSourceEditActivity::class.java)
            .putExtra("sourceUrl", source.sourceUrl)
        ActivityScenario.launch<RssSourceEditActivity>(intent).use {
            awaitField(source.sourceName)
            tab(R.string.source_tab_start)
            field("old-start").performTextReplacement("start")
            tab(R.string.source_tab_list)
            field("old-title").performTextReplacement("title")
            tab(R.string.source_tab_web_view)
            field("old-content").performTextReplacement("content")
            save()
            awaitSave {
                appDb.rssSourceDao.getByKey(source.sourceUrl)?.ruleContent == "content"
            }
            val saved = requireNotNull(appDb.rssSourceDao.getByKey(source.sourceUrl))
            assertEquals("start", saved.startHtml)
            assertEquals("title", saved.ruleTitle)
        }
    }

    @Test
    fun urlOptionScriptsSerializeUnderTheirOwnKeys() {
        fun scripts(js: String = "", bodyJs: String = "") =
            GSON.toJsonTree(UrlOptionInput(js = js, bodyJs = bodyJs, webJs = "web").toUrlOption())
                .asJsonObject.entrySet().associate { it.key to it.value.asString }

        assertEquals(mapOf("webJs" to "web", "js" to "before"), scripts(js = "before"))
        assertEquals(mapOf("webJs" to "web", "bodyJs" to "after"), scripts(bodyJs = "after"))
        assertEquals(mapOf("webJs" to "web", "js" to "before", "bodyJs" to "after"), scripts("before", "after"))
    }

    private fun tab(label: Int) {
        compose.onNode(hasText(context.getString(label)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
    }

    private fun field(value: String) = compose.onNode(hasSetTextAction() and hasText(value))

    private fun awaitField(value: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasSetTextAction() and hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun save() {
        compose.onNodeWithContentDescription(context.getString(R.string.action_save)).performClick()
    }

    private fun awaitSave(isSaved: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            isSaved()
        }
    }
}
