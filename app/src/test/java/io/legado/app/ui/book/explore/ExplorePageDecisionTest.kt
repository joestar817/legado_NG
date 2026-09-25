package io.legado.app.ui.book.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorePageDecisionTest {
    @Test fun repeatedHomepageStopsAdjacentNavigation() {
        assertEquals(ExplorePageDecision.END,
            evaluateExplorePage(setOf("a", "b"), listOf("b", "a", "a"), 2, 1))
    }

    @Test fun partialOverlapStillAcceptsANewPage() {
        assertEquals(ExplorePageDecision.ACCEPT,
            evaluateExplorePage(setOf("a", "b"), listOf("b", "c"), 2, 1))
    }

    @Test fun emptyNextPageStopsWithoutReplacingAcceptedContent() {
        assertEquals(ExplorePageDecision.END,
            evaluateExplorePage(setOf("a"), emptyList(), 2, 1))
    }

    @Test fun distantEmptyOrDuplicateProbeDoesNotDeclareCurrentPageTheEnd() {
        for (incoming in listOf(emptyList(), listOf("a"))) {
            assertEquals(ExplorePageDecision.KEEP,
                evaluateExplorePage(setOf("a"), incoming, 999, 1))
        }
    }

    @Test fun revisitingAnEarlierPageIsNotMistakenForForwardDuplication() {
        assertEquals(ExplorePageDecision.ACCEPT,
            evaluateExplorePage(setOf("a", "b"), listOf("a"), 1, 2))
    }

    @Test fun initialEmptyPageUsesTheNormalEmptyState() {
        assertEquals(ExplorePageDecision.ACCEPT,
            evaluateExplorePage(emptySet(), emptyList(), 1, 0))
    }
}
