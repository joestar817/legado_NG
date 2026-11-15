package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgButtonTextPaletteTest {
    @Test
    fun nightDangerActionRemainsReadableOnDialogSurface() {
        val error = 0xFFEB4333.toInt()
        val surface = 0xFF1F1F1F.toInt()
        val resolved = NgButtonTextPalette.resolveDanger(error, surface, isDark = true)

        assertTrue(NgColorMath.contrastRatio(resolved, surface) >= 4.5)
        assertEquals(error, NgButtonTextPalette.resolveDanger(error, surface, isDark = false))
        assertEquals(error, NgButtonTextPalette.resolveDanger(error, 0xFF000000.toInt(), isDark = true))
    }
}
