package io.legado.app.help.config

import io.legado.app.help.book.BookScriptClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadStyleLanguageMapTest {

    private val names = listOf("秋山书意", "经典纯白", "Custom Night")

    @Test
    fun assignedNameResolvesExistingPreset() {
        val bindings = LanguageStyleBindings(cjk = "秋山书意", latin = "经典纯白")
        assertEquals("秋山书意", bindings.assignedName(BookScriptClass.Cjk, names))
        assertEquals("经典纯白", bindings.assignedName(BookScriptClass.Latin, names))
    }

    @Test
    fun emptyOtherDoesNotSwitch() {
        val bindings = LanguageStyleBindings(other = null)
        assertNull(bindings.assignedName(BookScriptClass.Other, names))
        assertNull(LanguageStyleBindings(other = "").assignedName(BookScriptClass.Other, names))
        assertNull(LanguageStyleBindings(other = "  ").assignedName(BookScriptClass.Other, names))
    }

    @Test
    fun deletedNameIsUnassigned() {
        val bindings = LanguageStyleBindings(cjk = "gone", latin = "经典纯白")
        assertNull(bindings.assignedName(BookScriptClass.Cjk, names))
        assertEquals("经典纯白", bindings.assignedName(BookScriptClass.Latin, names))
    }

    @Test
    fun jsonRoundTripKeepsEmptyOtherUnassigned() {
        val original = LanguageStyleBindings(cjk = "秋山书意", latin = "Custom Night")
        val restored = ReadStyleLanguageMap.fromJson(ReadStyleLanguageMap.toJson(original))
        assertEquals("秋山书意", restored.cjk)
        assertEquals("Custom Night", restored.latin)
        assertNull(restored.other)
        assertEquals(LanguageStyleBindings(), ReadStyleLanguageMap.fromJson(null))
        assertEquals(LanguageStyleBindings(), ReadStyleLanguageMap.fromJson("{}"))
    }

    @Test
    fun withClearsBlankNames() {
        val bindings = LanguageStyleBindings(cjk = "秋山书意").with(BookScriptClass.Cjk, "  ")
        assertNull(bindings.cjk)
    }
}
