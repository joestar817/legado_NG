package io.legado.app.ui.book.changecover

import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.changesource.ChangeSourceFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeCoverSourceFilterTest {
    private val sources = listOf(
        source("text-a", "A,shared", BookSourceType.default),
        source("image-a", "A", BookSourceType.image),
        source("text-b", "B", BookSourceType.default),
        source("prefix", "AB", BookSourceType.default),
        source("disabled", "A", BookSourceType.default, enabled = false),
        source("ungrouped", null, BookSourceType.default),
    )
    private val covers = (sources.map { it.bookSourceUrl } + "missing").map {
        SearchBook(bookUrl = "book-$it", origin = it, coverUrl = "cover-$it")
    }

    @Test
    fun selectedGroupAndTypeFilterBothNewSourcesAndCachedCovers() {
        val filter = ChangeSourceFilter(BookSourceType.default, sameTypeOnly = true, group = "A")
        assertEquals(listOf("text-a"), matchingCoverSources(sources, filter).map { it.bookSourceUrl })
        assertEquals(listOf("text-a"), matchingCachedCovers(covers, sources, filter).map { it.origin })
    }

    @Test
    fun disablingTypeRestrictionStillKeepsTheSelectedGroup() {
        val filter = ChangeSourceFilter(BookSourceType.default, sameTypeOnly = false, group = "A")
        assertEquals(
            listOf("text-a", "image-a"),
            matchingCoverSources(sources, filter).map { it.bookSourceUrl },
        )
        assertEquals(
            listOf("text-a", "image-a"),
            matchingCachedCovers(covers, sources, filter).map { it.origin },
        )
    }

    @Test
    fun allGroupsIncludeUngroupedButNeverDisabledOrMissingSources() {
        val filter = ChangeSourceFilter(BookSourceType.default, sameTypeOnly = false, group = "")
        val expected = listOf("text-a", "image-a", "text-b", "prefix", "ungrouped")
        assertEquals(expected, matchingCoverSources(sources, filter).map { it.bookSourceUrl })
        assertEquals(expected, matchingCachedCovers(covers, sources, filter).map { it.origin })
    }

    @Test
    fun unavailableGroupDoesNotFallBackToAllSourcesOrShowOldCache() {
        val filter = ChangeSourceFilter(BookSourceType.default, sameTypeOnly = true, group = "missing")
        assertEquals(emptyList<BookSourcePart>(), matchingCoverSources(sources, filter))
        assertEquals(emptyList<SearchBook>(), matchingCachedCovers(covers, sources, filter))
    }

    private fun source(
        url: String,
        group: String?,
        type: Int,
        enabled: Boolean = true,
    ) = BookSourcePart(
        bookSourceUrl = url,
        bookSourceGroup = group,
        bookSourceType = type,
        enabled = enabled,
    )
}
