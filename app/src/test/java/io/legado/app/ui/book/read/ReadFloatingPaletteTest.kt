package io.legado.app.ui.book.read

import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadFloatingPaletteTest {

    @Test
    fun nightDangerTextContrastsWithBothReadingDialogGradientEnds() {
        val snapshot = NgThemeResolver.resolve(
            NgLegacyThemeInput(
                primaryColor = 0xFF153A5B.toInt(),
                accentColor = 0xFF5CCBFF.toInt(),
                backgroundColor = 0xFF06182D.toInt(),
                bottomBackground = 0xFF12314D.toInt(),
                errorColor = 0xFFEB4333.toInt(),
                isDark = true,
                isEInk = false,
            )
        )
        for (strength in listOf(0, 50, 100)) {
            val text = ReadFloatingPalette.resolveDangerTextColor(snapshot, strength)
            assertTrue(minContrast(text, ReadFloatingPalette.resolveSurfaceColors(snapshot, strength)) >= 4.5)
        }
    }

    @Test
    fun zeroSeedKeepsApplicationSnapshotUntouched() {
        val base = baseSnapshot(isDark = false)

        assertEquals(base, ReadFloatingPalette.applySeed(base, 0))
    }

    @Test
    fun pickedSeedIsTheExactPrimaryWithoutTonalPaletteConcentration() {
        val base = baseSnapshot(isDark = false)
        val picked = 0xFFF6E4D1.toInt()
        val result = ReadFloatingPalette.applySeed(base, picked)
        val expectedContainer = NgColorMath.blend(
            base.colors.background,
            picked,
            0.16f,
        )

        assertEquals(picked, result.colors.primary)
        assertEquals(picked, result.colors.surfaceTint)
        assertEquals(NgColorMath.contentColorFor(picked), result.colors.onPrimary)
        assertEquals(expectedContainer, result.colors.primaryContainer)
        assertEquals(
            NgColorMath.contentColorFor(expectedContainer),
            result.colors.onPrimaryContainer,
        )
        assertEquals(expectedContainer, result.colors.selectedContainer)
        assertEquals(base.colors.surface, result.colors.surface)
        assertEquals(base.colors.drawerContainer, result.colors.drawerContainer)
    }

    @Test
    fun darkPickedSeedUsesTheSameRawPrimaryAndDarkContainerRatio() {
        val base = baseSnapshot(isDark = true)
        val picked = 0xFF392A20.toInt()
        val result = ReadFloatingPalette.applySeed(base, picked)

        assertEquals(picked, result.colors.primary)
        assertEquals(
            NgColorMath.blend(base.colors.background, picked, 0.34f),
            result.colors.primaryContainer,
        )
    }

    @Test
    fun halfStrengthIsTheOriginalFloatingMaterialAndEndpointsExpandAroundIt() {
        val snapshot = ReadFloatingPalette.applySeed(
            base = baseSnapshot(isDark = false),
            seed = 0xFFF78E66.toInt(),
        )
        val original = ReadFloatingPalette.resolveSurfaceColors(snapshot)
        val weak = ReadFloatingPalette.resolveSurfaceColors(snapshot, 0)
        val half = ReadFloatingPalette.resolveSurfaceColors(snapshot, 50)
        val enhanced = ReadFloatingPalette.resolveSurfaceColors(snapshot, 100)

        assertEquals(original, half)
        assertEquals(snapshot.colors.drawerContainer, weak.bottom)
        assertEquals(
            NgColorMath.blend(snapshot.colors.drawerContainer, snapshot.colors.surfaceTint, 0.5f),
            enhanced.bottom,
        )
        assertNotEquals(weak.bottom, half.bottom)
        assertNotEquals(half.bottom, enhanced.bottom)
    }

    @Test
    fun semanticRolesKeepRawSeedAndMeetContrastAcrossAllStrengths() {
        val rawSeed = 0xFFF78E66.toInt()
        val seeded = ReadFloatingPalette.applySeed(
            base = baseSnapshot(isDark = false),
            seed = rawSeed,
        )

        (0..100).forEach { strength ->
            val result = ReadFloatingPalette.applySemanticRoles(seeded, strength)
            val surfaces = ReadFloatingPalette.resolveSurfaceColors(result, strength)

            assertEquals(rawSeed, result.colors.surfaceTint)
            val expectedIndicatorContrast = 3.0 + 1.2 * (strength / 100.0)
            assertTrue(
                minContrast(result.colors.primary, surfaces) >= expectedIndicatorContrast - 0.01
            )
            assertTrue(minContrast(result.colors.secondary, surfaces) >= 4.5)
            assertTrue(minContrast(result.colors.onSurface, surfaces) >= 4.5)
            assertTrue(minContrast(result.colors.onSurfaceVariant, surfaces) >= 4.5)
            assertTrue(minContrast(result.colors.outline, surfaces) >= 3.0)
            assertEquals(
                NgColorMath.contentColorFor(result.colors.primary),
                result.colors.onPrimary,
            )
        }

        val fullStrength = ReadFloatingPalette.applySemanticRoles(seeded, 100)
        val fullSurface = ReadFloatingPalette.resolveSurfaceColors(fullStrength, 100)
        assertEquals(
            NgColorMath.blend(seeded.colors.drawerContainer, rawSeed, 0.5f),
            fullSurface.bottom,
        )
        assertNotEquals(rawSeed, fullStrength.colors.primary)
    }

    @Test
    fun followApplicationAlsoDerivesSemanticRolesWithoutChangingItsSurfaceTint() {
        val base = baseSnapshot(isDark = false)
        val result = ReadFloatingPalette.applySemanticRoles(base, 100)
        val surfaces = ReadFloatingPalette.resolveSurfaceColors(result, 100)

        assertEquals(base.colors.primary, result.colors.surfaceTint)
        assertEquals(
            NgColorMath.blend(base.colors.drawerContainer, base.colors.primary, 0.5f),
            surfaces.bottom,
        )
        assertTrue(minContrast(result.colors.primary, surfaces) >= 4.2)
        assertTrue(minContrast(result.colors.secondary, surfaces) >= 4.5)
    }

    @Test
    fun indicatorChangesVisiblyBetweenHalfAndFullStrength() {
        val seeded = ReadFloatingPalette.applySeed(
            base = baseSnapshot(isDark = false),
            seed = 0xFFF78E66.toInt(),
        )
        val half = ReadFloatingPalette.applySemanticRoles(seeded, 50)
        val full = ReadFloatingPalette.applySemanticRoles(seeded, 100)
        val halfSurfaces = ReadFloatingPalette.resolveSurfaceColors(half, 50)
        val fullSurfaces = ReadFloatingPalette.resolveSurfaceColors(full, 100)

        assertTrue(minContrast(half.colors.primary, halfSurfaces) >= 3.59)
        assertTrue(minContrast(full.colors.primary, fullSurfaces) >= 4.19)
        assertTrue(rgbDistance(half.colors.primary, full.colors.primary) >= 8)
    }

    @Test
    fun colorStylesKeepTheMaterialEndpointAndDeriveDistinctSemanticPalettes() {
        val rawSeed = 0xFFF78E66.toInt()
        val seeded = ReadFloatingPalette.applySeed(
            base = baseSnapshot(isDark = false),
            seed = rawSeed,
        )
        val results = ReadFloatingColorStyle.entries.associateWith { style ->
            ReadFloatingPalette.applySemanticRoles(
                snapshot = seeded,
                primaryStrengthPercent = 50,
                colorStyle = style,
            )
        }

        results.values.forEach { result ->
            val surfaces = ReadFloatingPalette.resolveSurfaceColors(result, 50)
            assertEquals(rawSeed, result.colors.surfaceTint)
            assertTrue(minContrast(result.colors.primary, surfaces) >= 3.59)
            assertTrue(minContrast(result.colors.secondary, surfaces) >= 4.5)
        }
        assertEquals(
            ReadFloatingColorStyle.entries.size,
            results.values
                .map { result -> result.colors.primary to result.colors.secondary }
                .toSet()
                .size,
        )
    }

    private fun minContrast(
        foreground: Int,
        surfaces: ReadFloatingSurfaceColors,
    ): Double = minOf(
        NgColorMath.contrastRatio(foreground, surfaces.top),
        NgColorMath.contrastRatio(foreground, surfaces.bottom),
    )

    private fun rgbDistance(first: Int, second: Int): Int =
        kotlin.math.abs(channel(first, 16) - channel(second, 16)) +
            kotlin.math.abs(channel(first, 8) - channel(second, 8)) +
            kotlin.math.abs(channel(first, 0) - channel(second, 0))

    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

    private fun baseSnapshot(isDark: Boolean) = NgThemeResolver.resolve(
        NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(),
            accentColor = 0xFFF78E66.toInt(),
            backgroundColor = if (isDark) 0xFF1A1715.toInt() else 0xFFFFF9F5.toInt(),
            bottomBackground = if (isDark) 0xFF24201D.toInt() else 0xFFEEEEEE.toInt(),
            errorColor = 0xFFB3261E.toInt(),
            isDark = isDark,
            isEInk = false,
        )
    )
}
