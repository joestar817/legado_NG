package io.legado.app.ui.book.toc

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabBarVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class TocDockInteractionTest {
    @OptIn(ExperimentalTestApi::class)
    @get:Rule val composeRule = createComposeRule(effectContext = StandardTestDispatcher())

    private val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))

    private val pager get() = composeRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
    )

    private fun showSingleChapterCatalog(): BookChapter {
        val chapter = BookChapter(bookUrl = "book", url = "chapter-1", title = "First chapter")
        var state by mutableStateOf(
            TocUiState(chapters = listOf(TocChapterUiItem(chapter)), totalChapterCount = 1),
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                TocScreen(state) { event ->
                    if (event is TocUiEvent.TabChange) state = state.copy(selectedTab = event.tab)
                }
            }
        }
        return chapter
    }

    @Test
    fun catalogDockKeepsTabSemanticsAndDispatchesBothSelections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var state by mutableStateOf(TocUiState())
        val selectedTabs = mutableListOf<Int>()
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                TocScreen(state) { event ->
                    if (event is TocUiEvent.TabChange) {
                        selectedTabs += event.tab
                        state = state.copy(selectedTab = event.tab)
                    }
                }
            }
        }
        val chapters = composeRule.onNodeWithContentDescription(context.getString(R.string.chapter_list))
        val bookmarks = composeRule.onNodeWithContentDescription(context.getString(R.string.bookmark))
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        chapters.assert(tabRole).assertIsSelected()
        bookmarks.assert(tabRole).assertIsNotSelected().performClick().assertIsSelected()
        chapters.assertIsNotSelected().performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertTrue(TOC_TAB_BOOKMARKS in selectedTabs)
            assertTrue(TOC_TAB_CHAPTERS in selectedTabs)
        }
    }

    @Test
    fun swipingBackFromBookmarksRestoresChapterTabAndProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chapter = showSingleChapterCatalog()
        val chapters = composeRule.onNodeWithContentDescription(context.getString(R.string.chapter_list))
        val bookmarks = composeRule.onNodeWithContentDescription(context.getString(R.string.bookmark))
        val count = composeRule.onNodeWithText(context.getString(R.string.read_catalog_bookmark_count, 0))
        val progress = composeRule.onNodeWithText(
            context.getString(R.string.read_catalog_reading_progress, 1, 1, 100.0),
        )

        bookmarks.performClick().assertIsSelected()
        count.assertIsDisplayed()
        pager.performTouchInput { swipeRight() }
        composeRule.onNodeWithText(chapter.title).assertIsDisplayed()
        chapters.assertIsSelected()
        progress.assertIsDisplayed()

        pager.performTouchInput { swipeLeft() }
        bookmarks.assertIsSelected()
        count.assertIsDisplayed()
    }

    @Test
    fun draggingBackBeforeBookmarkAnimationChangesPageRestoresChapterSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chapter = showSingleChapterCatalog()
        val chapters = composeRule.onNodeWithContentDescription(context.getString(R.string.chapter_list))
        val bookmarks = composeRule.onNodeWithContentDescription(context.getString(R.string.bookmark))
        chapters.assertIsSelected()

        composeRule.mainClock.autoAdvance = false
        try {
            bookmarks.performClick()
            repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
            bookmarks.assertIsSelected()
            pager.performTouchInput {
                down(center)
                moveBy(Offset(width * 0.38f, 0f))
            }
            composeRule.mainClock.advanceTimeByFrame()
            pager.performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.onNodeWithText(chapter.title).assertIsDisplayed()
        chapters.assertIsSelected()
        composeRule.onNodeWithText(
            context.getString(R.string.read_catalog_reading_progress, 1, 1, 100.0),
        ).assertIsDisplayed()
    }

    @Test
    fun displaySortChangesImmediatelyWithoutChangingTheBooksStoredOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val book = Book(
            bookUrl = "https://example.com/book",
            origin = "https://example.com",
            name = "Book",
            readConfig = Book.ReadConfig(reverseToc = false),
        )
        val reversedBook = book.copy(
            readConfig = book.readConfig!!.copy(reverseToc = true),
            durChapterIndex = 9,
        )
        var state by mutableStateOf(TocUiState().withBookSnapshot(book))
        val events = mutableListOf<TocUiEvent>()
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                TocScreen(state) { event ->
                    events += event
                    if (event == TocUiEvent.ToggleSort) {
                        state = state.copy(catalogDescending = !state.catalogDescending)
                    }
                }
            }
        }

        val ascending = context.getString(R.string.read_catalog_ascending)
        val descending = context.getString(R.string.read_catalog_descending)
        composeRule.onNodeWithText(ascending).performClick()
        composeRule.onNodeWithText(descending).assertExists()
        composeRule.runOnIdle {
            assertEquals(listOf(TocUiEvent.ToggleSort), events)
            assertFalse(state.book!!.getReverseToc())
            assertEquals(0, state.book!!.durChapterIndex)
            state = state.withBookSnapshot(reversedBook)
            assertTrue(state.reverseToc)
            assertTrue(state.catalogDescending)
            assertEquals(9, state.book?.durChapterIndex)
        }
        composeRule.onNodeWithText(descending).performClick()
        composeRule.onNodeWithText(ascending).assertExists()
        composeRule.runOnIdle {
            assertEquals(listOf(TocUiEvent.ToggleSort, TocUiEvent.ToggleSort), events)
            assertTrue(state.book!!.getReverseToc())
            assertFalse(state.catalogDescending)
        }
    }

    @Test
    fun catalogSummaryKeepsProgressAndSearchCountsDistinct() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val book = Book(bookUrl = "https://example.com/book", name = "Book", durChapterIndex = 131)
        val matches = (0 until 3).map { index ->
            TocChapterUiItem(BookChapter(bookUrl = book.bookUrl, url = "chapter-$index", index = index))
        }
        var state by mutableStateOf(
            TocUiState(book = book, totalChapterCount = 588, bookmarks = listOf(Bookmark())),
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                TocScreen(state) { event ->
                    when (event) {
                        TocUiEvent.ToggleSort -> state = state.copy(catalogDescending = !state.catalogDescending)
                        is TocUiEvent.TabChange -> state = state.copy(selectedTab = event.tab)
                        else -> Unit
                    }
                }
            }
        }
        val progress = context.getString(R.string.read_catalog_reading_progress, 132, 588, 132.0 / 588 * 100)
        composeRule.onNodeWithText(progress).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.read_catalog_ascending)).performClick()
        composeRule.onNodeWithText(progress).assertExists()

        composeRule.runOnIdle { state = state.copy(query = "chapter", chapters = matches) }
        composeRule.onNodeWithText(context.getString(R.string.read_catalog_search_chapter_count, 3)).assertExists()
        composeRule.runOnIdle { state = state.copy(query = "", chapters = emptyList()) }
        composeRule.onNodeWithText(progress).assertExists()

        composeRule.runOnIdle { state = state.copy(totalChapterCount = 0) }
        composeRule.onNodeWithText(context.getString(R.string.read_catalog_reading_progress, 0, 0, 0.0)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.bookmark)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.read_catalog_bookmark_count, 1)).assertExists()
        composeRule.runOnIdle { state = state.copy(query = "bookmark") }
        composeRule.onNodeWithText(context.getString(R.string.read_catalog_search_bookmark_count, 1)).assertExists()
    }

    @Test
    fun appearanceDockKeepsItsOriginalRadioButtonRoleByDefault() {
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                NgFloatingTabBar(
                    items = listOf(NgFloatingTabSpec(text = "Choice")),
                    selectedIndex = 0,
                    onTabSelected = {},
                    variant = NgFloatingTabBarVariant.APPEARANCE_SELECTION,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Choice")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
    }
}
