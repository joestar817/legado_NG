package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LocalBookCoverSelectionTest {
    @Test fun onlySupportedLocalBooksWithoutCustomCoverAreSelected() {
        val formats = listOf("epub", "pdf", "umd", "mobi", "azw", "azw3")
        formats.forEach {
            assertTrue(canExtractOriginalCover(Book(originName = "sample.$it", type = 264)))
        }
        val normal = Book(bookUrl = "A", originName = "a.epub", type = 264)
        val custom = normal.copy(bookUrl = "D", customCoverUrl = "content://test/custom.png")
        val txt = normal.copy(bookUrl = "T", originName = "a.txt")
        val online = normal.copy(bookUrl = "N", origin = "https://example.test", type = 8)
        assertEquals(listOf(normal), listOf(normal, custom, txt, online).filter(::canExtractOriginalCover))
    }

    @Test fun mixedSelectionContinuesAfterBadBookAndPreservesCustomCover() = runBlocking {
        val books = listOf("A", "B", "C", "D").map {
            Book(bookUrl = it, originName = "$it.epub", type = 264,
                customCoverUrl = if (it == "D") "content://test/custom.png" else null)
        }
        val ready = mutableListOf<String>()
        val result = rebuildBookCovers(books, ::canExtractOriginalCover, {
            if (it.bookUrl == "B") throw IOException("broken")
            true
        }, { _, _ -> }, { ready += it.bookUrl })
        assertEquals(CoverRebuildResult(2, 1, 1), result)
        assertEquals(listOf("A", "C"), ready)
    }
}
