package io.legado.app.ui.book.info

import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
class BookInfoSettingsTest {

    private fun onlineState(type: Int = BookType.text) = BookInfoUiState(
        book = Book(bookUrl = "https://book/1", origin = "https://source", type = type),
        sourceAvailable = true,
        otherWorksVisible = true,
        inBookshelf = true,
        loginAvailable = true,
    )

    @Test
    fun onlineSettingsSeparateBookPreferencesAndSharedSourceVariables() {
        val sections = bookInfoSettingsSections(onlineState())
        assertEquals(
            listOf(
                R.string.book_info_settings_book,
                R.string.book_info_settings_general,
                R.string.book_info_settings_variables,
            ),
            sections.map { it.titleRes },
        )
        assertEquals(listOf(BookInfoMenuAction.CanUpdate), sections[0].items.map { it.action })
        assertEquals(
            listOf(BookInfoMenuAction.DeleteAlert, BookInfoMenuAction.AutoLoadOtherWorks),
            sections[1].items.map { it.action },
        )
        assertEquals(
            listOf(BookInfoMenuAction.SetBookVariable, BookInfoMenuAction.SetSourceVariable),
            sections[2].items.map { it.action },
        )
        assertNull(sections[2].items[0].checked)
        assertEquals(BookInfoVariableKind.Book, sections[2].items[0].variableKind)
        assertNull(sections[2].items[0].valueRes)
        assertNull(sections[2].items[1].checked)
        assertEquals(BookInfoVariableKind.Source, sections[2].items[1].variableKind)
        assertEquals(R.string.book_info_settings_source_shared, sections[2].items[1].valueRes)
    }

    @Test
    fun missingSourceHidesBookUpdateAndVariablesButKeepsDetailPreferences() {
        val sections = bookInfoSettingsSections(onlineState().copy(sourceAvailable = false))
        assertEquals(1, sections.size)
        assertEquals(R.string.book_info_settings_general, sections.single().titleRes)
        assertEquals(
            listOf(BookInfoMenuAction.DeleteAlert, BookInfoMenuAction.AutoLoadOtherWorks),
            sections.single().items.map { it.action },
        )
    }

    @Test
    fun absentRelatedWorksDoesNotExposeItsAutomaticLoadingSwitch() {
        val items = bookInfoSettingsSections(onlineState().copy(otherWorksVisible = false))
            .flatMap { it.items }
        assertFalse(items.any { it.action == BookInfoMenuAction.AutoLoadOtherWorks })
        assertTrue(items.any { it.action == BookInfoMenuAction.DeleteAlert })
    }

    @Test
    fun onlyLocalTxtOffersChapterSplittingIncludingWebDavTxt() {
        listOf(BookType.localTag, "${BookType.webDavTag}https://dav/book").forEach { origin ->
            listOf("novel.TxT", "novel.epub", "novel.pdf", "novel.umd", "novel.mobi").forEach { name ->
                listOf(0, BookType.local or BookType.text).forEach { type ->
                    val book = Book(origin = origin, originName = name, type = type)
                    book.setSplitLongChapter(false)
                    val sections = bookInfoSettingsSections(BookInfoUiState(book = book))
                    val bookItems = sections.filter { it.titleRes == R.string.book_info_settings_book }
                        .flatMap { it.items }
                    if (name.endsWith(".txt", ignoreCase = true)) {
                        assertEquals(BookInfoMenuAction.SplitLongChapter, bookItems.single().action)
                        assertEquals(false, bookItems.single().checked)
                    } else {
                        assertTrue(bookItems.isEmpty())
                    }
                    assertFalse(sections.any { it.titleRes == R.string.book_info_settings_variables })
                }
            }
        }
    }

    @Test
    fun mediaBooksRetainExistingNonLocalUpdateCapability() {
        listOf(BookType.text, BookType.audio, BookType.video, BookType.image, BookType.webFile).forEach { type ->
            val items = bookInfoSettingsSections(onlineState(type)).flatMap { it.items }
            assertTrue(items.any { it.action == BookInfoMenuAction.CanUpdate })
            assertFalse(items.any { it.action == BookInfoMenuAction.SplitLongChapter })
        }
    }

    @Test
    fun repeatedRenderingReadsCurrentValuesWithoutChangingBookOrPreferences() {
        val book = onlineState().book!!.copy(canUpdate = false)
        val state = onlineState().copy(book = book, deleteAlertEnabled = false, autoLoadOtherWorks = true)
        repeat(3) {
            val values = bookInfoSettingsSections(state).flatMap { it.items }.associate { it.action to it.checked }
            assertEquals(false, values[BookInfoMenuAction.CanUpdate])
            assertEquals(false, values[BookInfoMenuAction.DeleteAlert])
            assertEquals(true, values[BookInfoMenuAction.AutoLoadOtherWorks])
            assertFalse(book.canUpdate)
            assertFalse(state.deleteAlertEnabled)
            assertTrue(state.autoLoadOtherWorks)
        }
    }

    @Test
    fun overflowRetainsAllOperationsAndMovesOnlyTheSixSettings() {
        val online = onlineState()
        val local = BookInfoUiState(
            book = Book(originName = "novel.txt", type = BookType.local or BookType.text),
            inBookshelf = true,
        )
        fun leafIds(items: List<NgExpandableActionMenuItem>): List<Int> = items.flatMap {
            if (it.children.isEmpty()) listOf(it.itemId) else leafIds(it.children)
        }
        val ids = (leafIds(bookInfoMenuItems(online)) + leafIds(bookInfoMenuItems(local))).toSet()
        val settings = setOf(
            BookInfoMenuAction.CanUpdate,
            BookInfoMenuAction.SplitLongChapter,
            BookInfoMenuAction.DeleteAlert,
            BookInfoMenuAction.AutoLoadOtherWorks,
            BookInfoMenuAction.SetBookVariable,
            BookInfoMenuAction.SetSourceVariable,
        )
        assertTrue(settings.none { it.itemId in ids })
        assertEquals((BookInfoMenuAction.entries.toSet() - settings).map { it.itemId }.toSet(), ids)
        assertEquals(1, leafIds(bookInfoMenuItems(online)).count { it == BookInfoMenuAction.Settings.itemId })
        assertFalse(
            BookInfoMenuAction.Edit.itemId in leafIds(bookInfoMenuItems(online.copy(inBookshelf = false))),
        )
    }
}
