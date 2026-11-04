package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubWarmTargetTest {
    @Test
    fun zeroAnimationDoesNotCaptureOrdinaryMiddlePages() {
        for (direction in listOf(-1, 1)) for (page in 2..7)
            assertTrue(epubWarmTargets(1, page, 10, 3, direction, 4, boundaryOnly = true).isEmpty())
    }

    @Test
    fun zeroAnimationKeepsTheSameForwardAndReverseBoundaryFrames() {
        for ((page, direction) in listOf(8 to 1, 9 to 1, 1 to -1, 0 to -1))
            assertEquals(epubWarmTargets(1, page, 10, 3, direction, 4),
                epubWarmTargets(1, page, 10, 3, direction, 4, boundaryOnly = true))
    }

    @Test
    fun zeroAnimationDoesNotWarmBeyondTheActualBookEdges() {
        assertTrue(epubWarmTargets(0, 0, 10, 3, 1, 4, boundaryOnly = true).isEmpty())
        assertTrue(epubWarmTargets(2, 9, 10, 3, -1, 4, boundaryOnly = true).isEmpty())
        assertTrue(epubWarmTargets(0, 0, 1, 1, 1, 4, boundaryOnly = true).isEmpty())
    }

    @Test
    fun zeroAnimationStillHonorsTheBudgetForShortChapters() {
        assertEquals(listOf(EpubWarmTarget(0, 1, 0), EpubWarmTarget(-1, 2, 0)),
            epubWarmTargets(1, 0, 1, 3, 1, 2, boundaryOnly = true))
    }

    @Test
    fun forwardWindowIncludesTheNextDocumentBeforeTheLastPage() {
        val window = epubWarmTargets(1, 8, 10, 3, 1, 4)
        assertEquals(listOf(8, 9, 7, -1), window.map { it.key })
        assertEquals(EpubWarmTarget(-1, 2, 0), window.last())
    }

    @Test
    fun reverseWindowCapturesTheActualPreviousDocumentEnd() {
        val window = epubWarmTargets(1, 1, 10, 3, -1, 4)
        assertEquals(listOf(1, 0, 2, -2), window.map { it.key })
        assertEquals(EpubWarmTarget(-2, 0, -1, last = true), window.last())
    }

    @Test
    fun aSmallBudgetKeepsTheCurrentPageAndImmediateTarget() {
        assertEquals(listOf(EpubWarmTarget(9, 1, 9), EpubWarmTarget(-1, 2, 0)),
            epubWarmTargets(1, 9, 10, 3, 1, 2))
    }

    @Test
    fun bookEdgesAndSinglePageChaptersNeverAliasAnOrdinaryPage() {
        assertEquals(listOf(EpubWarmTarget(0, 0, 0)), epubWarmTargets(0, 0, 1, 1, 1, 4))
        val window = epubWarmTargets(1, 0, 1, 3, 1, 4)
        assertEquals(listOf(0, -1, -3, -2), window.map { it.key })
        assertEquals(window.size, window.map { it.key }.distinct().size)
        assertTrue(epubWarmTargets(0, 0, 0, 3, 1, 4).isEmpty())
        assertTrue(epubWarmTargets(0, 0, 10, 3, 1, 0).isEmpty())
    }

    @Test
    fun boundaryWindowAlsoPreparesTheFollowingPageInTheSameFourSlots() {
        assertEquals(listOf(9, -1, 8, -3), epubWarmTargets(1, 9, 10, 3, 1, 4).map { it.key })
        assertEquals(EpubWarmTarget(-3, 2, 1), epubWarmTargets(1, 9, 10, 3, 1, 4).last())
        assertEquals(listOf(0, -2, 1, -4), epubWarmTargets(1, 0, 10, 3, -1, 4).map { it.key })
        assertEquals(EpubWarmTarget(-4, 0, -2, last = true), epubWarmTargets(1, 0, 10, 3, -1, 4).last())
    }

    @Test
    fun enteringADocumentWarmsTheCurrentDirectionBeforeReflowingTheOppositeEdge() {
        assertEquals(listOf(0, 1, 2, -2), epubWarmTargets(1, 0, 10, 3, 1, 4).map { it.key })
        assertEquals(listOf(9, 8, 7, -1), epubWarmTargets(1, 9, 10, 3, -1, 4).map { it.key })
        assertEquals(EpubWarmTarget(-2, 0, -1, last = true), epubWarmTargets(1, 0, 10, 3, 1, 4).last())
        assertEquals(EpubWarmTarget(-1, 2, 0), epubWarmTargets(1, 9, 10, 3, -1, 4).last())
    }

    @Test
    fun changingDirectionBeforeAnEdgeKeepsTheAdjacentDocumentWarm() {
        assertEquals(listOf(8, 7, 9, -1), epubWarmTargets(1, 8, 10, 3, -1, 4).map { it.key })
        assertEquals(EpubWarmTarget(-1, 2, 0), epubWarmTargets(1, 8, 10, 3, -1, 4).last())
        assertEquals(listOf(1, 2, 0, -2), epubWarmTargets(1, 1, 10, 3, 1, 4).map { it.key })
        assertEquals(EpubWarmTarget(-2, 0, -1, last = true), epubWarmTargets(1, 1, 10, 3, 1, 4).last())
        assertEquals(listOf(8, 7, 9, 6), epubWarmTargets(2, 8, 10, 3, -1, 4).map { it.key })
        assertEquals(listOf(1, 2, 0, 3), epubWarmTargets(0, 1, 10, 3, 1, 4).map { it.key })
    }
}
