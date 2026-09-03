package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadThemeModeTest {

    @Test
    fun followSystemUsesCurrentSystemMode() {
        assertFalse(resolveReadThemeNightMode(ReadThemeMode.FOLLOW_SYSTEM, false))
        assertTrue(resolveReadThemeNightMode(ReadThemeMode.FOLLOW_SYSTEM, true))
    }

    @Test
    fun forcedModesIgnoreSystemMode() {
        assertFalse(resolveReadThemeNightMode(ReadThemeMode.DAY, true))
        assertTrue(resolveReadThemeNightMode(ReadThemeMode.NIGHT, false))
    }

    @Test
    fun missingPreferencePreservesLegacyEffectiveMode() {
        assertEquals(ReadThemeMode.DAY, resolveReadThemeMode(null, false))
        assertEquals(ReadThemeMode.NIGHT, resolveReadThemeMode(null, true))
    }

    @Test
    fun storedPreferenceWinsOverLegacyEffectiveMode() {
        assertEquals(
            ReadThemeMode.FOLLOW_SYSTEM,
            resolveReadThemeMode(ReadThemeMode.FOLLOW_SYSTEM.storageValue, true),
        )
        assertEquals(
            ReadThemeMode.DAY,
            resolveReadThemeMode(ReadThemeMode.DAY.storageValue, true),
        )
    }
}
