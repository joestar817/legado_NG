package io.legado.app.help.config

import io.legado.app.ui.book.read.aloud.RainNightProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("cartoon", ListeningMotionEffect.CARTOON.storageValue)
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
    fun `unknown stored cartoon type falls back to sakura`() {
        assertEquals(null, ListeningCartoonType.fromStorageOrNull("future_cartoon_type"))
        assertEquals(null, ListeningCartoonType.fromStorageOrNull(null))
        assertEquals(
            ListeningCartoonType.SAKURA,
            ListeningCartoonType.fromStorage("future_cartoon_type"),
        )
        assertEquals(
            ListeningCartoonType.SAKURA,
            ListeningCartoonType.fromStorage(null),
        )
    }

    @Test
    fun `known stored cartoon types remain stable`() {
        ListeningCartoonType.entries.forEach { type ->
            assertEquals(type, ListeningCartoonType.fromStorage(type.storageValue))
        }
        assertEquals("sakura", ListeningCartoonType.SAKURA.storageValue)
        assertEquals("cats", ListeningCartoonType.CATS.storageValue)
        assertEquals("rain_night", ListeningCartoonType.RAIN_NIGHT.storageValue)
        assertEquals(100, ListeningMotionConfig.DEFAULT_CARTOON_INTENSITY)
    }

    @Test
    fun `rain night full-strength profile remains accepted`() {
        assertEquals(20f, RainNightProfile.DURATION_SECONDS, 0f)
        assertEquals(2.4f, RainNightProfile.RAIN_DENSITY, 0f)
        assertEquals(1.6f, RainNightProfile.DROP_LEVEL, 0f)
        assertEquals(1.6f, RainNightProfile.FOG_LEVEL, 0f)
        assertEquals(.6f, RainNightProfile.LEAF_LEVEL, 0f)
        assertEquals(1_272, RainNightProfile.RAIN_LINE_COUNT)
        assertEquals(504, RainNightProfile.rainCount(210))
        assertEquals(384, RainNightProfile.rainCount(160))
        assertEquals(252, RainNightProfile.rainCount(105))
        assertEquals(132, RainNightProfile.rainCount(55))
        assertEquals(48, RainNightProfile.DROPLET_COUNT)
        assertEquals(6, RainNightProfile.FOG_COUNT)
        assertEquals(25, RainNightProfile.LEAF_COUNT)
        assertEquals(25, RainNightProfile.leafCount())
        assertTrue((0 until RainNightProfile.DROPLET_COUNT).all {
            RainNightProfile.dropletCycles(it) in 2..6
        })
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
