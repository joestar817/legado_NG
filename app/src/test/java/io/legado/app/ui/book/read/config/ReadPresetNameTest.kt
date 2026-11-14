package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadPresetNameTest {
    @Test
    fun usesTheDefaultNameWhenItIsAvailable() {
        assertEquals("新预设", defaultPresetName(emptyList()))
        assertEquals("新预设", defaultPresetName(listOf("秋山书意", "新预设 2")))
    }

    @Test
    fun avoidsExistingNamesAndReusesAvailableNumbers() {
        assertEquals("新预设 2", defaultPresetName(listOf("新预设", "新预设 3")))
        assertEquals("新预设 3", defaultPresetName(listOf("新预设", "新预设 2")))
    }

    @Test
    fun ignoresOuterWhitespaceWhenCheckingExistingNames() {
        assertEquals("新预设 2", defaultPresetName(listOf(" 新预设 ")))
    }
}
