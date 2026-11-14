package io.legado.app.ui.book.read.config

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadPresetGridLayoutTest {
    @Test
    fun gridHeightGrowsOnlyWhenPresetsNeedAnotherRow() {
        assertEquals(56.dp, presetGridViewportHeight(0, 500.dp))
        assertEquals(56.dp, presetGridViewportHeight(2, 500.dp))
        assertEquals(126.dp, presetGridViewportHeight(3, 500.dp))
        assertEquals(126.dp, presetGridViewportHeight(6, 500.dp))
        assertEquals(196.dp, presetGridViewportHeight(7, 500.dp))
        assertEquals(196.dp, presetGridViewportHeight(8, 500.dp))
    }

    @Test
    fun gridUsesWholeRowsWhenPresetCountExceedsAvailableHeight() {
        assertEquals(126.dp, presetGridViewportHeight(20, 160.dp))
        assertEquals(126.dp, presetGridViewportHeight(20, 126.dp))
        assertEquals(56.dp, presetGridViewportHeight(20, 125.dp))
    }

    @Test
    fun gridShrinksAgainAfterPresetsAreRemoved() {
        assertEquals(196.dp, presetGridViewportHeight(9, 300.dp))
        assertEquals(126.dp, presetGridViewportHeight(6, 300.dp))
        assertEquals(56.dp, presetGridViewportHeight(2, 300.dp))
    }

    @Test
    fun gridStillUsesAvailableSpaceWhenItCannotFitAFullRow() {
        assertEquals(40.dp, presetGridViewportHeight(8, 40.dp))
        assertEquals(0.dp, presetGridViewportHeight(8, 0.dp))
    }
}
