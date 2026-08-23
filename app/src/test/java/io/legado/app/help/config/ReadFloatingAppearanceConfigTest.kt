package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadFloatingAppearanceConfigTest {

    @Test
    fun transparencyKeepsEndpointsAndLegacyDefault() {
        assertEquals(1f, ReadFloatingAppearanceConfig.surfaceAlpha(0), 0.001f)
        assertEquals(0.9875f, ReadFloatingAppearanceConfig.surfaceAlpha(5), 0.001f)
        assertEquals(0.95f, ReadFloatingAppearanceConfig.surfaceAlpha(10), 0.001f)
        assertEquals(0.8f, ReadFloatingAppearanceConfig.surfaceAlpha(20), 0.001f)
        assertEquals(0f, ReadFloatingAppearanceConfig.surfaceAlpha(100), 0.001f)
    }

    @Test
    fun floatingAlphaKeepsLegacyDefaultButMakesZeroStrictlyOpaque() {
        assertEquals(
            1f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(0, 0.76f),
            0.001f
        )
        assertEquals(
            0.76f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(20, 0.76f),
            0.001f
        )
        assertEquals(
            0f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(100, 0.76f),
            0.001f
        )
    }

    @Test
    fun floatingAlphaEasesInBeforeTheDefaultTransparency() {
        assertEquals(
            0.9875f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(5, 0.80f),
            0.001f
        )
        assertEquals(
            0.985f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(5, 0.76f),
            0.001f
        )
        assertEquals(
            0.95f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(10, 0.80f),
            0.001f
        )
        assertEquals(
            0.94f,
            ReadFloatingAppearanceConfig.floatingSurfaceAlpha(10, 0.76f),
            0.001f
        )
    }

    @Test
    fun miniPlayerCurveKeepsDefaultAndEndpointsStable() {
        val defaultAlpha = ReadFloatingAppearanceConfig.miniPlayerSurfaceAlpha(20)

        assertEquals(20, ReadFloatingAppearanceConfig.MINI_PLAYER_TRANSPARENCY_PERCENT)
        assertEquals(1f, ReadFloatingAppearanceConfig.miniPlayerSurfaceAlpha(0), 0.001f)
        assertEquals(0f, ReadFloatingAppearanceConfig.miniPlayerSurfaceAlpha(100), 0.001f)
        assertTrue(defaultAlpha in 0.35f..0.37f)
    }

    @Test
    fun primaryStrengthKeepsOriginalAtHalfAndExpandsBothDirections() {
        assertEquals(0f, ReadFloatingAppearanceConfig.primaryStrengthFraction(0), 0.001f)
        assertEquals(0.5f, ReadFloatingAppearanceConfig.primaryStrengthFraction(50), 0.001f)
        assertEquals(1f, ReadFloatingAppearanceConfig.primaryStrengthFraction(100), 0.001f)

        assertEquals(0f, ReadFloatingAppearanceConfig.primaryStrengthChromaScale(0), 0.001f)
        assertEquals(1f, ReadFloatingAppearanceConfig.primaryStrengthChromaScale(50), 0.001f)
        assertEquals(1.35f, ReadFloatingAppearanceConfig.primaryStrengthChromaScale(100), 0.001f)
        assertEquals(0f, ReadFloatingAppearanceConfig.primaryStrengthBaseProgress(0), 0.001f)
        assertEquals(1f, ReadFloatingAppearanceConfig.primaryStrengthBaseProgress(50), 0.001f)
        assertEquals(1f, ReadFloatingAppearanceConfig.primaryStrengthBaseProgress(100), 0.001f)
        assertEquals(0f, ReadFloatingAppearanceConfig.primaryStrengthEnhanceProgress(0), 0.001f)
        assertEquals(0f, ReadFloatingAppearanceConfig.primaryStrengthEnhanceProgress(50), 0.001f)
        assertEquals(1f, ReadFloatingAppearanceConfig.primaryStrengthEnhanceProgress(100), 0.001f)
    }

    @Test
    fun floatingAppearanceIsStoredWithEachReadingPreset() {
        val config = ReadBookConfig.Config(
            readFloatingSeed = 0xFFCC8844.toInt(),
            readFloatingTransparency = 35,
            readFloatingPrimaryStrength = 70,
            readFloatingColorStyle = ReadFloatingColorStyle.RAINBOW,
        )

        val json = GSON.toJson(config)

        val restored = GSON.fromJson(json, ReadBookConfig.Config::class.java)
        assertEquals(0xFFCC8844.toInt(), restored.readFloatingSeed)
        assertEquals(35, restored.readFloatingTransparency)
        assertEquals(70, restored.readFloatingPrimaryStrength)
        assertEquals(ReadFloatingColorStyle.RAINBOW, restored.curReadFloatingColorStyle())
        assertEquals(
            ReadFloatingColorStyle.VIBRANT,
            ReadBookConfig.Config().curReadFloatingColorStyle(),
        )
    }
}
