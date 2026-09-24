package io.legado.app.model.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubSpreadWindowTest {
    private fun publication(rtl: Boolean = false, reflow: Int? = null) = EpubPublication(
        "book.opf", "3.0", null, null, null,
        listOf(EpubMetadata("meta", "pre-paginated", mapOf("property" to "rendition:layout"), "http://www.idpf.org/2007/opf")),
        listOf(EpubManifestItem("page", "page.xhtml", EpubResourceLink("page.xhtml"), "application/xhtml+xml", emptySet())),
        (0..5).map { EpubSpineItem("page", true, if (it == reflow) setOf("rendition:layout-reflowable") else emptySet()) },
        if (rtl) EpubPageProgressionDirection.RTL else EpubPageProgressionDirection.LTR,
        emptyList(), emptyList(),
    )

    @Test
    fun shiftedWindowDoesNotRestartPairing() {
        val plan = EpubSpreadLayout.fixedRun(publication(), 2, true)
        val loaded = EpubSpreadLayout.loadedWindow(plan, setOf(1, 2, 3))
        assertEquals(listOf(EpubSpread(EpubSpreadPage(2), EpubSpreadPage(3))), loaded)
        assertEquals(emptyList<EpubSpread>(), EpubSpreadLayout.loadedWindow(plan, setOf(2)))
    }

    @Test
    fun repeatedPathsKeepOccurrencesAndRtlSides() {
        val plan = EpubSpreadLayout.fixedRun(publication(rtl = true), 3, true)
        assertEquals(EpubSpread(EpubSpreadPage(3), EpubSpreadPage(2)), plan[1])
        assertEquals((0..5).toList(), plan.flatMap { it.pagesInReadingOrder(true) }.map { it.occurrence })
    }

    @Test
    fun mixedLayoutBreaksTheFixedRunAndPortraitUsesSinglePages() {
        val book = publication(reflow = 2)
        assertEquals(listOf(3, 4, 5), EpubSpreadLayout.fixedRun(book, 3, true)
            .flatMap { it.pagesInReadingOrder(false) }.map { it.occurrence })
        assertEquals(emptyList<EpubSpread>(), EpubSpreadLayout.fixedRun(book, 2, true))
        assertEquals(listOf(3, 4, 5), EpubSpreadLayout.fixedRun(book, 4, false).map { it.center!!.occurrence })
    }
}
