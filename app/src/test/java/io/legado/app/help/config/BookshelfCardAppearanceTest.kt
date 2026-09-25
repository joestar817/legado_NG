package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfCardAppearanceTest {
    @Test
    fun dayAndNightKeepIndependentMaterials() {
        val initial = BookshelfCardAppearance()
        assertEquals(BookshelfCardMaterial.SOLID, initial.forNight(false).material)
        assertEquals(BookshelfCardMaterial.TRANSPARENT, initial.forNight(true).material)
        val changed = initial.updated(false, initial.day.copy(material = BookshelfCardMaterial.LIQUID))
        assertEquals(BookshelfCardMaterial.LIQUID, changed.day.material)
        assertEquals(initial.night, changed.night)
    }

    @Test
    fun switchingGlassRetainsEachTransparency() {
        var style = BookshelfCardStyle(BookshelfCardMaterial.TRANSPARENT).withTransparency(25)
        style = style.copy(material = BookshelfCardMaterial.LIQUID).withTransparency(80)
        assertEquals(80, style.transparency)
        assertEquals(25, style.copy(material = BookshelfCardMaterial.TRANSPARENT).transparency)
        style = style.copy(material = BookshelfCardMaterial.SOLID)
        assertEquals(80, style.copy(material = BookshelfCardMaterial.LIQUID).transparency)
    }

    @Test
    fun transparencyIsBoundedAndDoesNotChangeOtherMode() {
        val original = BookshelfCardAppearance()
        val changed = original.updated(true, original.night.withTransparency(150))
        assertEquals(100, changed.night.transparency)
        assertEquals(original.day, changed.day)
        assertEquals(0, changed.night.withTransparency(-10).transparency)
    }
}
