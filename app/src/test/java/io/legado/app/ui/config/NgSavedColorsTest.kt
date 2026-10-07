package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class NgSavedColorsTest {

    @Test
    fun `favorites preserve insertion order and every ARGB bit across serialization`() {
        val colors = listOf(0x00ABCDEF, 0xFFABCDEF.toInt(), 0, Int.MIN_VALUE, Int.MAX_VALUE)
        assertEquals(colors, NgSavedColors.decode(NgSavedColors.encode(colors)))
        assertEquals(NgSavedColors.AddResult.ADDED, NgSavedColors.addResult(listOf(0x00ABCDEF), 0xFFABCDEF.toInt()))
        assertEquals(NgSavedColors.AddResult.ALREADY_SAVED, NgSavedColors.addResult(colors, 0))
    }

    @Test
    fun `favorites deduplicate and cap at seven without replacing older choices`() {
        val colors = listOf(8, 1, 8, 2, 3, 4, 5, 6, 7, 9)
        val saved = NgSavedColors.decode(NgSavedColors.encode(colors))
        assertEquals(listOf(8, 1, 2, 3, 4, 5, 6), saved)
        assertEquals(NgSavedColors.AddResult.FULL, NgSavedColors.addResult(saved, 7))
        assertEquals(NgSavedColors.AddResult.ALREADY_SAVED, NgSavedColors.addResult(saved, 8))
        assertEquals(NgSavedColors.AddResult.ADDED, NgSavedColors.addResult(saved.drop(1), 7))
    }

    @Test
    fun `empty or malformed storage cannot invent colors or exceed the limit`() {
        assertEquals(emptyList<Int>(), NgSavedColors.decode(null))
        assertEquals(emptyList<Int>(), NgSavedColors.decode(""))
        assertEquals(listOf(0, -1, 12), NgSavedColors.decode("oops,0,,4294967296,-1,12,12"))
        assertEquals((0..6).toList(), NgSavedColors.decode((0..100).joinToString(",")))
    }
}
