package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantContextWindowTest {
    @Test
    fun `known limits generate regular 32K marks and an exact endpoint`() {
        assertEquals(listOf(32_000, 64_000, 96_000, 128_000), assistantContextWindowOptions(128_000))
        assertEquals(listOf(24_123), assistantContextWindowOptions(24_123))

        for (limit in listOf(120_123, 1_048_576, Int.MAX_VALUE)) {
            val options = assistantContextWindowOptions(limit)
            assertEquals(limit, options.last())
            assertTrue(options.dropLast(1).all { it % 32_000 == 0 })
            assertTrue(options.zipWithNext().all { (lower, upper) -> lower < upper })
            assertTrue(options.size <= 6)
        }
    }

    @Test
    fun `unknown limit ends at one million and clamps a larger saved window`() {
        val options = assistantContextWindowOptions(null)
        assertEquals(
            listOf(32_000, 64_000, 128_000, 256_000, 512_000, 1_000_000),
            options,
        )
        val capped = capAssistantContextWindow(2_000_000, null)
        assertEquals(options.last(), capped)
        assertEquals(options.lastIndex.toFloat(), assistantContextWindowPosition(options, capped), 0f)
    }

    @Test
    fun `labels use whole K and M without rounding up`() {
        assertEquals("999", assistantContextWindowLabel(999))
        assertEquals("96K", assistantContextWindowLabel(96_123))
        assertEquals("999K", assistantContextWindowLabel(999_999))
        assertEquals("1M", assistantContextWindowLabel(1_024_000))
        assertEquals("1M", assistantContextWindowLabel(1_048_576))
        assertEquals("1M", assistantContextWindowLabel(1_999_999))
        assertEquals("2M", assistantContextWindowLabel(2_000_000))
    }

    @Test
    fun `matching rounded labels leave a single mark and keep the exact endpoint`() {
        val limit = 1_048_576
        val options = assistantContextWindowOptions(limit)
        assertEquals(listOf(32_000, 256_000, 512_000, 768_000, limit), options)
        assertEquals(listOf("32K", "256K", "512K", "768K", "1M"), options.map(::assistantContextWindowLabel))

        val previousWindow = capAssistantContextWindow(1_024_000, limit)
        assertEquals(1_024_000, previousWindow)
        val previousPosition = assistantContextWindowPosition(options, previousWindow)
        assertTrue(previousPosition > 3f && previousPosition < 4f)

        assertEquals(
            listOf(32_000, 544_000, 1_088_000, 2_100_123),
            assistantContextWindowOptions(2_100_123),
        )
        assertEquals(2_100_123, capAssistantContextWindow(2_200_000, 2_100_123))
    }

    @Test
    fun `switching clamps to the exact smaller limit and preserves the result on the next model`() {
        val reduced = capAssistantContextWindow(144_000, 96_123)
        assertEquals(96_123, reduced)
        assertEquals(96_123, capAssistantContextWindow(reduced, 128_000))
        assertEquals(96_123, capAssistantContextWindow(reduced, null))
    }

    @Test
    fun `retained values are positioned between stops without snapping`() {
        val options = assistantContextWindowOptions(120_000)
        assertEquals(2.5f, assistantContextWindowPosition(options, 108_000), 0f)
        assertEquals(3f, assistantContextWindowPosition(options, 120_000), 0f)
        assertEquals(0.5f, assistantContextWindowPosition(listOf(24_000), 12_000), 0f)
    }
}
