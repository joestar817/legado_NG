package io.legado.app.help.config

import io.legado.app.ui.design.theme.NgPaperLook
import org.junit.Assert.assertEquals
import org.junit.Test

class AiThemeDeckStoreTest {

    private fun look(name: String, bg: Int = 0xFFF0F0F0.toInt()) = NgPaperLook(
        labelKey = name,
        background = bg,
        foreground = 0xFF222222.toInt(),
        accent = 0xFF445566.toInt(),
        highlight = 0xFFEECC88.toInt(),
        contrastRatio = 10.0,
    )

    @Test
    fun mergeDeckAppendsAndKeepsNewestWhenOverCap() {
        val existing = List(14) { look("e$it", 0xFF000000.toInt() + it) }
        val incoming = listOf(look("n1"), look("n2"), look("n3"), look("n4"))
        val merged = AiThemeDeckStore.mergeDeck(existing, incoming, maxSize = 16)
        assertEquals(16, merged.size)
        assertEquals("e2", merged.first().labelKey)
        assertEquals("n4", merged.last().labelKey)
    }

    @Test
    fun mergeDeckReturnsExistingWhenIncomingEmpty() {
        val existing = listOf(look("a"))
        assertEquals(existing, AiThemeDeckStore.mergeDeck(existing, emptyList()))
    }

    @Test
    fun storedPaperLookRoundTripWithoutSeed() {
        val original = look("Warm Paper")
        val dto = original.toStored()
        val restored = dto.toLook(isNight = false, isEink = false)
        requireNotNull(restored)
        assertEquals(original.labelKey, restored.labelKey)
        assertEquals(original.background, restored.background)
        assertEquals(original.foreground, restored.foreground)
        assertEquals(original.accent, restored.accent)
        assertEquals(original.highlight, restored.highlight)
        assertEquals(original.contrastRatio, restored.contrastRatio, 0.001)
        assertEquals(null, restored.seed)
    }
}
