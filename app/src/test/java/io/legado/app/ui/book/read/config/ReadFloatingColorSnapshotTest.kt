package io.legado.app.ui.book.read.config

import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadFloatingColorSnapshotTest {
    @Test
    fun restoringDayDoesNotOverwriteNight() {
        val config = ReadBookConfig.Config(
            readFloatingSeed = 0xFF123456.toInt(),
            readFloatingSeedNight = 0xFF334455.toInt(),
            readFloatingFollowAppNight = false,
        )
        val snapshot = ReadFloatingColorSnapshot.capture(config, ReadAppearanceMode.DAY)
        config.setReadFloatingSeedForMode(0xFFABCDEF.toInt(), ReadAppearanceMode.DAY)

        snapshot.restore(config)

        assertEquals(0xFF123456.toInt(), config.readFloatingSeed)
        assertEquals(0xFF334455.toInt(), config.readFloatingSeedNight)
        assertEquals(false, config.readFloatingFollowAppNight)
    }

    @Test
    fun restoringNightPreservesUnsetFollowFlagAndDaySeed() {
        val config = ReadBookConfig.Config(
            readFloatingSeed = 0xFF123456.toInt(),
            readFloatingSeedNight = 0,
            readFloatingFollowAppNight = null,
        )
        val snapshot = ReadFloatingColorSnapshot.capture(config, ReadAppearanceMode.NIGHT)
        config.setReadFloatingSeedForMode(0xFFABCDEF.toInt(), ReadAppearanceMode.NIGHT)

        snapshot.restore(config)

        assertEquals(0xFF123456.toInt(), config.readFloatingSeed)
        assertEquals(0, config.readFloatingSeedNight)
        assertNull(config.readFloatingFollowAppNight)
    }

    @Test
    fun restoringNightPreservesExplicitIndependentZeroSeed() {
        val config = ReadBookConfig.Config(readFloatingSeedNight = 0, readFloatingFollowAppNight = false)
        val snapshot = ReadFloatingColorSnapshot.capture(config, ReadAppearanceMode.NIGHT)
        config.setReadFloatingSeedForMode(0xFFABCDEF.toInt(), ReadAppearanceMode.NIGHT)

        snapshot.restore(config)

        assertEquals(0, config.readFloatingSeedNight)
        assertEquals(false, config.readFloatingFollowAppNight)
    }
}
