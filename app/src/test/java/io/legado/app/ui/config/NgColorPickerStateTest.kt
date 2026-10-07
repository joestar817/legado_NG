package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NgColorPickerStateTest {

    @Test
    fun `mode changes and external echoes preserve exact transparent RGB`() {
        val state = NgColorPickerState(0x00123456)
        NgColorPickerMode.entries.forEach { mode ->
            state.setMode(mode)
            state.syncColor(0x00123456)
            assertEquals(0x00123456, state.color)
        }
        state.setAlpha(255)
        assertEquals(0xFF123456.toInt(), state.color)
        state.setAlpha(0)
        assertEquals(0x00123456, state.color)
    }

    @Test
    fun `same external color keeps unfinished drafts while a real change replaces them`() {
        val state = NgColorPickerState(0x80123456.toInt())
        state.editHex("#80AB")
        state.syncColor(0x80123456.toInt())
        assertEquals("#80AB", state.hexInput)
        assertFalse(state.isInputValid)
        assertEquals(0x80123456.toInt(), state.color)

        state.syncColor(0x80765432.toInt())
        assertEquals("#80765432", state.hexInput)
        assertTrue(state.isInputValid)
        state.editRgbChannel(0, "")
        state.syncColor(state.color)
        assertEquals("", state.rgbInputs[0])
        assertFalse(state.isInputValid)
        state.syncColor(0x80445566.toInt())
        assertEquals(listOf("68", "85", "102"), state.rgbInputs)
        assertTrue(state.isInputValid)
    }

    @Test
    fun `hex accepts RGB or Android ARGB and never applies invalid text`() {
        val state = NgColorPickerState(0x40010203)
        state.editHex("#a1b2c3")
        assertEquals(0xFFA1B2C3.toInt(), state.color)
        assertEquals("#A1B2C3", state.hexInput)
        state.editHex(" 00123456 ")
        assertEquals(0x00123456, state.color)
        assertEquals("00123456", state.hexInput)
        listOf("", "#", "#FFF", "#1234567", "#123456789", "#GG1234", "#+12345", "##123456")
            .forEach { invalid ->
                state.editHex(invalid)
                assertTrue(invalid, state.isHexInputError)
                assertFalse(state.isInputValid)
                assertEquals(0x00123456, state.color)
            }
        assertNull(NgColorPickerColors.parse("-0000001"))
    }

    @Test
    fun `RGB applies only complete valid channels and switching mode discards incomplete values`() {
        val state = NgColorPickerState(0x40123456)
        state.setMode(NgColorPickerMode.RGB)
        state.editRgbChannel(0, "")
        state.editRgbChannel(1, "255")
        assertEquals(0x40123456, state.color)
        assertEquals(listOf(true, false, false), state.rgbInputErrors)
        state.editRgbChannel(0, "001")
        assertEquals(0x4001FF56, state.color)
        state.syncColor(state.color)
        assertEquals("001", state.rgbInputs[0])
        assertTrue(state.isInputValid)

        listOf("256", "-1", "1.5", " ", "１２", "0000").forEach { invalid ->
            state.editRgbChannel(2, invalid)
            assertEquals(0x4001FF56, state.color)
            assertFalse(state.isInputValid)
        }
        assertTrue(state.setMode(NgColorPickerMode.SPECTRUM))
        assertEquals(listOf("1", "255", "86"), state.rgbInputs)
        assertEquals(0x4001FF56, state.color)
        assertTrue(state.isInputValid)
        assertFalse(state.setMode(NgColorPickerMode.GRID))
    }

    @Test
    fun `switching mode preserves invalid hex until user fixes or explicitly selects a color`() {
        val state = NgColorPickerState(0xFF123456.toInt())
        state.editHex("#12")
        assertFalse(state.setMode(NgColorPickerMode.SPECTRUM))
        assertEquals("#12", state.hexInput)
        assertFalse(state.isInputValid)
        state.setColor(state.color)
        assertEquals("#FF123456", state.hexInput)
        assertTrue(state.isInputValid)
    }

    @Test
    fun `gray retains hue and black retains hue and saturation through alpha and callback echoes`() {
        val state = NgColorPickerState(0xFF0000FF.toInt())
        state.setColor(0xFF808080.toInt())
        assertEquals(240f, state.hue, 0.0001f)
        assertEquals(0f, state.saturation, 0f)
        state.setHue(275f)
        state.setSaturationValue(0.75f, 0f)
        assertEquals(0xFF000000.toInt(), state.color)
        state.setAlpha(0)
        state.syncColor(state.color)
        assertEquals(275f, state.hue, 0f)
        assertEquals(0.75f, state.saturation, 0f)
        state.setSaturationValue(state.saturation, 1f)
        assertEquals(NgColorPickerColors.hsvToColor(275f, 0.75f, 1f, 0), state.color)
        state.setColor(0x00000000)
        assertEquals(275f, state.hue, 0.2f)
        assertEquals(0.75f, state.saturation, 0.002f)
    }

    @Test
    fun `reset clears invalid text and sampled RGB retains editing alpha`() {
        val state = NgColorPickerState(0x27123456)
        state.editHex("#NO")
        state.sampleRgb(0xCCFEDCBA.toInt())
        assertEquals(0x27FEDCBA, state.color)
        assertTrue(state.isInputValid)
        state.editRgbChannel(0, "999")
        state.reset(0x00112233)
        assertEquals(0x00112233, state.color)
        assertEquals("#00112233", state.hexInput)
        assertTrue(state.isInputValid)
    }

    @Test
    fun `force opaque is enforced at every input boundary`() {
        val state = NgColorPickerState(0x00123456, forceOpaque = true)
        val actions = listOf<() -> Unit>(
            { state.setColor(0x00234567) },
            { state.syncColor(0x40345678) },
            { state.editHex("#01456789") },
            { state.editRgbChannel(0, "0") },
            { state.setRgbChannel(1, 42) },
            { state.setHue(123f) },
            { state.setSaturationValue(0.5f, 0.4f) },
            { state.setAlpha(0) },
            { state.sampleRgb(0x00654321) },
            { state.reset(0x00000000) },
        )
        assertEquals(255, state.alpha)
        actions.forEach { action ->
            action()
            assertEquals(255, state.alpha)
            assertTrue(state.isInputValid)
            assertTrue(state.hexInput.startsWith("#FF"))
        }
    }

    @Test
    fun `grid contains the approved grayscale hue order and dark to pale rows`() {
        val grid = NgColorPickerColors.gridColors()
        assertEquals(120, grid.size)
        assertEquals(0xFFFFFFFF.toInt(), grid[0])
        assertEquals(0xFF000000.toInt(), grid[11])
        assertTrue(grid.take(12).zipWithNext().all { (a, b) -> (a and 255) > (b and 255) })
        grid.take(12).forEach { gray ->
            assertEquals(gray and 255, gray ushr 8 and 255)
            assertEquals(gray and 255, gray ushr 16 and 255)
        }
        assertEquals(NgColorPickerColors.hsvToColor(190f, 1f, 0.28f), grid[12])
        assertEquals(0xFF00D5FF.toInt(), grid[60])
        assertEquals(0xFF00FF00.toInt(), grid[71])
        assertEquals(0xFFCCFFCC.toInt(), grid.last())
        assertTrue(grid.all { it ushr 24 == 255 })
    }

    @Test
    fun `HSV primary colors endpoints and byte round trips are correct`() {
        assertEquals(0xFFFF0000.toInt(), NgColorPickerColors.hsvToColor(0f, 1f, 1f))
        assertEquals(0xFFFF0000.toInt(), NgColorPickerColors.hsvToColor(360f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), NgColorPickerColors.hsvToColor(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), NgColorPickerColors.hsvToColor(240f, 1f, 1f))
        for (red in 0..255 step 17) {
            for (green in 0..255 step 17) {
                for (blue in 0..255 step 17) {
                    val color = (0x37 shl 24) or (red shl 16) or (green shl 8) or blue
                    val hsv = NgColorPickerColors.rgbToHsv(color)
                    assertEquals(color, NgColorPickerColors.hsvToColor(hsv.hue, hsv.saturation, hsv.value, 0x37))
                    assertEquals(color, NgColorPickerColors.parse(NgColorPickerColors.format(color)))
                }
            }
        }
    }
}
