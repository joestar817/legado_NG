package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageIdsTest {
    @Test
    fun optionalTabsKeepStableIdentitiesAndOrder() {
        for (discovery in listOf(false, true)) {
            for (rss in listOf(false, true)) {
                for (home in listOf(false, true)) {
                    val pages = MainPageIds.visible(discovery, rss, home).toList()
                    assertEquals(home, MainPageIds.HOME in pages)
                    assertTrue(MainPageIds.BOOKSHELF in pages)
                    assertEquals(MainPageIds.MY, pages.last())
                    assertEquals(discovery, MainPageIds.EXPLORE in pages)
                    assertEquals(rss, MainPageIds.RSS in pages)
                    assertEquals(pages.size, pages.distinct().size)
                    assertTrue(pages.size in 2..5)
                    assertEquals(
                        listOf(4, 0, 1, 2, 3).filter { it in pages },
                        pages
                    )
                }
            }
        }
    }

    @Test
    fun callersWithoutHomeFlagKeepHomeVisible() {
        assertEquals(MainPageIds.HOME, MainPageIds.visible(false, false).first())
    }

    @Test
    fun existingPageIdsRemainStable() {
        assertEquals(0, MainPageIds.BOOKSHELF)
        assertEquals(1, MainPageIds.EXPLORE)
        assertEquals(2, MainPageIds.RSS)
        assertEquals(3, MainPageIds.MY)
        assertEquals(4, MainPageIds.HOME)
    }

    @Test
    fun explicitDefaultPagesResolveOnlyToVisiblePagePositions() {
        val keys = mapOf(
            "home" to MainPageIds.HOME,
            "bookshelf" to MainPageIds.BOOKSHELF,
            "explore" to MainPageIds.EXPLORE,
            "rss" to MainPageIds.RSS,
            "my" to MainPageIds.MY
        )
        for (discovery in listOf(false, true)) {
            for (rss in listOf(false, true)) {
                for (home in listOf(false, true)) {
                    val pages = MainPageIds.visible(discovery, rss, home)
                    keys.forEach { (key, pageId) ->
                        val position = MainPageIds.defaultPosition(key, pages)
                        assertTrue(position in pages.indices)
                        if (pageId in pages) {
                            assertEquals(pageId, pages[position])
                        } else {
                            assertEquals(0, position)
                        }
                    }
                    assertEquals(0, MainPageIds.defaultPosition(null, pages))
                    assertEquals(0, MainPageIds.defaultPosition("unknown", pages))
                }
            }
        }
    }

    @Test
    fun hiddenHomeDefaultsToBookshelf() {
        val pages = MainPageIds.visible(true, true, false)
        val position = MainPageIds.defaultPosition("home", pages)
        assertEquals(MainPageIds.BOOKSHELF, pages[position])
    }

    @Test
    fun hiddenDiscoveryAndRssKeepFirstVisiblePageFallback() {
        val pages = MainPageIds.visible(false, false)
        assertEquals(0, MainPageIds.defaultPosition("explore", pages))
        assertEquals(0, MainPageIds.defaultPosition("rss", pages))
        assertEquals(MainPageIds.HOME, pages.first())
    }

    @Test
    fun currentPageIdentitySurvivesEveryVisibleTabChange() {
        val variants = listOf(false, true).flatMap { discovery ->
            listOf(false, true).flatMap { rss ->
                listOf(false, true).map { home ->
                    MainPageIds.visible(discovery, rss, home)
                }
            }
        }
        for (previousPages in variants) {
            for (pages in variants) {
                for (previousPageId in previousPages) {
                    val position = MainPageIds.preservePosition(previousPageId, pages)
                    assertTrue(position in pages.indices)
                    assertEquals(
                        if (previousPageId in pages) previousPageId else MainPageIds.BOOKSHELF,
                        pages[position]
                    )
                }
            }
        }
    }

    @Test
    fun missingCurrentPageFallsBackToBookshelfWithHomeVisible() {
        val pages = MainPageIds.visible(false, false)
        assertEquals(
            MainPageIds.BOOKSHELF,
            pages[MainPageIds.preservePosition(null, pages)]
        )
        assertEquals(
            MainPageIds.BOOKSHELF,
            pages[MainPageIds.preservePosition(99, pages)]
        )
    }
}
