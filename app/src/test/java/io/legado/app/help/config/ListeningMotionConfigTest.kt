package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningMotionConfigTest {

    @Test
    fun `unknown stored effect falls back to flame`() {
        assertEquals(
            ListeningMotionEffect.FLAME,
            ListeningMotionEffect.fromStorage("future_effect"),
        )
        assertEquals(ListeningMotionEffect.FLAME, ListeningMotionEffect.fromStorage("cover_glow"))
    }

    @Test
    fun `known stored effects remain stable`() {
        ListeningMotionEffect.entries.forEach { effect ->
            assertEquals(effect, ListeningMotionEffect.fromStorage(effect.storageValue))
        }
        assertEquals("flame", ListeningMotionEffect.FLAME.storageValue)
        assertEquals("fluid", ListeningMotionEffect.FLUID.storageValue)
    }

    @Test
    fun `unknown stored fire style falls back to godfire`() {
        assertEquals(
            ListeningFireStyle.GODFIRE,
            ListeningFireStyle.fromStorage("future_fire_style"),
        )
    }

    @Test
    fun `known stored fire styles remain stable`() {
        ListeningFireStyle.entries.forEach { style ->
            assertEquals(style, ListeningFireStyle.fromStorage(style.storageValue))
        }
    }

    @Test
    fun `unknown stored fluid type falls back to smoke`() {
        assertEquals(
            ListeningFluidType.SMOKE,
            ListeningFluidType.fromStorage("future_fluid_type"),
        )
    }

    @Test
    fun `known stored fluid types remain stable`() {
        ListeningFluidType.entries.forEach { type ->
            assertEquals(type, ListeningFluidType.fromStorage(type.storageValue))
        }
        assertEquals("smoke", ListeningFluidType.SMOKE.storageValue)
        assertEquals("water", ListeningFluidType.WATER.storageValue)
        assertEquals("edge", ListeningFluidType.EDGE.storageValue)
    }

    @Test
    fun `unknown color mode falls back to cover palette`() {
        assertEquals(
            ListeningMotionColorMode.COVER,
            ListeningMotionColorMode.fromStorage("future_color_mode"),
        )
    }

    @Test
    fun `known color modes remain stable`() {
        ListeningMotionColorMode.entries.forEach { mode ->
            assertEquals(mode, ListeningMotionColorMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun `intensity is clamped to percentage range`() {
        assertEquals(0, normalizeListeningMotionIntensity(-1))
        assertEquals(40, normalizeListeningMotionIntensity(40))
        assertEquals(100, normalizeListeningMotionIntensity(101))
    }
}
