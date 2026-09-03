package io.legado.app.ui.design.theme

import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgDrawerPaletteTest {

    @Test
    fun `strength moves warm and cool themes toward controlled theme surfaces`() {
        listOf(
            0xFFF78E66.toInt(),
            0xFF00838F.toInt(),
        ).forEach { seed ->
            val snapshot = snapshot(seed = seed, isDark = false)
            val neutral = NgDrawerPalette.resolveSurfaceColors(snapshot, 0)
            val middle = NgDrawerPalette.resolveSurfaceColors(snapshot, 50)
            val strong = NgDrawerPalette.resolveSurfaceColors(snapshot, 100)

            assertEquals(snapshot.colors.drawerContainer, neutral.bottom)
            assertTrue(Hct.fromInt(middle.top).chroma > Hct.fromInt(neutral.top).chroma)
            assertTrue(Hct.fromInt(strong.top).chroma >= Hct.fromInt(middle.top).chroma)
            assertTrue(Hct.fromInt(strong.bottom).chroma >= Hct.fromInt(middle.bottom).chroma)
            assertTrue(hueDistance(Hct.fromInt(strong.bottom).hue, Hct.fromInt(seed).hue) < 24.0)
            assertNotEquals(seed, strong.top)
            assertNotEquals(seed, strong.bottom)
        }
    }

    @Test
    fun `light warm drawer compensates pink perception while cool hue stays stable`() {
        val warmSeed = Hct.fromInt(0xFFF78E66.toInt())
        val warmSurfaces = NgDrawerPalette.resolveSurfaceColors(
            snapshot(seed = warmSeed.toInt(), isDark = false),
            100,
        )
        val warmTop = Hct.fromInt(warmSurfaces.top)
        val warmBottom = Hct.fromInt(warmSurfaces.bottom)
        val coolSeed = Hct.fromInt(0xFF00838F.toInt())
        val coolSurface = Hct.fromInt(
            NgDrawerPalette.resolveSurfaceColors(
                snapshot(seed = coolSeed.toInt(), isDark = false),
                100,
            ).bottom
        )

        val topShift = clockwiseHueDelta(warmSeed.hue, warmTop.hue)
        val bottomShift = clockwiseHueDelta(warmSeed.hue, warmBottom.hue)
        assertTrue(bottomShift in 10.0..23.0)
        assertTrue(topShift < bottomShift)
        assertTrue(hueDistance(coolSeed.hue, coolSurface.hue) < 8.0)
    }

    @Test
    fun `semantic roles meet contrast across warm cool light and dark themes`() {
        listOf(false, true).forEach { isDark ->
            listOf(0xFFF78E66.toInt(), 0xFF00838F.toInt()).forEach { seed ->
                listOf(0, 50, 100).forEach { strength ->
                    val base = snapshot(seed = seed, isDark = isDark)
                    val result = NgDrawerPalette.applySemanticRoles(base, strength)
                    val surfaces = NgDrawerPalette.resolveSurfaceColors(result, strength)
                    val expectedControlContrast = 3.0 + 1.2 * (strength / 100.0)

                    assertEquals(base.colors.surfaceTint, result.colors.surfaceTint)
                    assertEquals(base.colors.cardContainer, result.colors.cardContainer)
                    assertTrue(
                        minContrast(result.colors.primary, surfaces) >=
                            expectedControlContrast - 0.01
                    )
                    assertTrue(minContrast(result.colors.secondary, surfaces) >= 4.5)
                    assertTrue(minContrast(result.colors.onSurface, surfaces) >= 4.5)
                    assertTrue(minContrast(result.colors.onSurfaceVariant, surfaces) >= 4.5)
                    assertTrue(minContrast(result.colors.outline, surfaces) >= 3.0)
                }
            }
        }
    }

    @Test
    fun `adaptive content cards stay white by day and become lifted tinted surfaces at night`() {
        listOf(0xFFF78E66.toInt(), 0xFF00838F.toInt()).forEach { seed ->
            val light = snapshot(seed = seed, isDark = false)
            val lightResult = NgDrawerPalette.applyAdaptiveContentCardRoles(light, 100)
            assertEquals(0xFFFFFFFF.toInt(), lightResult.colors.cardContainer)

            listOf(0, 50, 100).forEach { strength ->
                val dark = snapshot(seed = seed, isDark = true)
                val result = NgDrawerPalette.applyAdaptiveContentCardRoles(dark, strength)
                val surfaces = NgDrawerPalette.resolveSurfaceColors(result, strength)
                val card = result.colors.cardContainer

                assertTrue(Hct.fromInt(card).tone > Hct.fromInt(surfaces.bottom).tone)
                assertTrue(NgColorMath.contrastRatio(result.colors.onSurface, card) >= 4.5)
                assertTrue(NgColorMath.contrastRatio(result.colors.onSurfaceVariant, card) >= 4.5)
                assertTrue(NgColorMath.contrastRatio(result.colors.primary, card) >= 3.0)
            }
        }
    }

    private fun minContrast(
        foreground: Int,
        surfaces: NgDrawerSurfaceColors,
    ): Double = minOf(
        NgColorMath.contrastRatio(foreground, surfaces.top),
        NgColorMath.contrastRatio(foreground, surfaces.bottom),
    )

    private fun hueDistance(first: Double, second: Double): Double {
        val direct = kotlin.math.abs(first - second)
        return minOf(direct, 360.0 - direct)
    }

    private fun clockwiseHueDelta(first: Double, second: Double): Double =
        (second - first + 360.0) % 360.0

    private fun snapshot(seed: Int, isDark: Boolean) = NgThemeResolver.resolve(
        NgLegacyThemeInput(
            primaryColor = if (isDark) 0xFF241D1A.toInt() else 0xFFFFF1E8.toInt(),
            accentColor = seed,
            backgroundColor = if (isDark) 0xFF171412.toInt() else 0xFFFFF9F5.toInt(),
            bottomBackground = if (isDark) 0xFF24201D.toInt() else 0xFFEEEEEE.toInt(),
            errorColor = 0xFFB3261E.toInt(),
            isDark = isDark,
            isEInk = false,
        )
    )
}
