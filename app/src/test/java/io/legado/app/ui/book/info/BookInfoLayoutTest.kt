package io.legado.app.ui.book.info

import io.legado.app.ui.book.info.BookInfoSection.CHARACTERS
import io.legado.app.ui.book.info.BookInfoSection.INTRO
import io.legado.app.ui.book.info.BookInfoSection.METADATA
import io.legado.app.ui.book.info.BookInfoSection.OTHER_WORKS
import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoLayoutTest {
    @Test
    fun missingPreferenceUsesOriginalOrderAndCoverSize() {
        val layout = BookInfoLayout.decode(null)
        assertEquals(100, layout.coverPercent)
        assertEquals(
            listOf(METADATA, INTRO, CHARACTERS, OTHER_WORKS),
            layout.visibleSections(charactersAvailable = true, otherWorksAvailable = true),
        )
    }

    @Test
    fun coverOrderAndVisibilityRoundTripTogether() {
        val edited = BookInfoLayout(coverPercent = 135)
            .move(CHARACTERS.key, INTRO.key)
            .setVisible(METADATA, false)
        val restored = BookInfoLayout.decode(edited.encode())
        assertEquals(edited, restored)
        assertEquals(
            listOf(CHARACTERS, INTRO, OTHER_WORKS),
            restored.visibleSections(charactersAvailable = true, otherWorksAvailable = true),
        )
    }

    @Test
    fun unavailableSectionsKeepTheirPreferenceForOtherBooks() {
        val layout = BookInfoLayout().move(OTHER_WORKS.key, METADATA.key).setVisible(CHARACTERS, false)
        assertEquals(
            listOf(METADATA, INTRO),
            layout.visibleSections(charactersAvailable = false, otherWorksAvailable = false),
        )
        assertEquals(
            listOf(OTHER_WORKS, METADATA, INTRO),
            layout.visibleSections(charactersAvailable = true, otherWorksAvailable = true),
        )
    }

    @Test
    fun savedLayoutIgnoresUnknownAndDuplicateKeys() {
        val layout = BookInfoLayout.decode("999|intro,unknown,intro|characters,unknown")
        assertEquals(160, layout.coverPercent)
        assertEquals(listOf(INTRO, METADATA, CHARACTERS, OTHER_WORKS), layout.order)
        assertEquals(setOf(CHARACTERS), layout.hidden)
        assertEquals(layout, layout.move("coverSize", INTRO.key))
    }
}
