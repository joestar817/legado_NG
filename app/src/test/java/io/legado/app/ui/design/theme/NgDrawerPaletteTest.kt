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
    fun `adaptive content cards stay white by day and lift theme containers at night`() {
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

    @Test
    fun `night containers keep their own hue across unrelated accents and strengths`() {
        val containers = listOf(
            0xFF12314D.toInt(), // 夏日童趣
            0xFF263440.toInt(), // 秋山书意
            0xFF253953.toInt(), // 绘本书屋
            0xFF2A2B2F.toInt(), // 动态场景
            0xFF3C2F29.toInt(), // 暖色容器
            0xFF28382D.toInt(), // 绿色容器
            0xFF353044.toInt(), // 紫色容器
            0xFF262626.toInt(), // 灰阶
        )
        val accents = listOf(0xFFF3B953, 0xFF5CCBFF, 0xFFB1D18A, 0xFFFFB0CA)
        containers.forEach { container ->
            val base = snapshot(seed = accents.first().toInt(), isDark = true).let {
                it.copy(colors = it.colors.copy(
                    surface = container,
                    drawerContainer = container,
                    cardContainer = container,
                ))
            }
            val expectedCard = NgDrawerPalette.resolveAdaptiveContentCardColor(base, 0)
            val original = Hct.fromInt(container)
            val lifted = Hct.fromInt(expectedCard)
            assertTrue(lifted.tone - original.tone in 3.5..4.5)
            // 低色度下的色相角对 8-bit RGB 舍入敏感，只对有明显色相的容器检查角度。
            if (original.chroma > 10.0) {
                assertTrue(hueDistance(original.hue, lifted.hue) < 5.0)
            }
            if (container == 0xFF262626.toInt()) {
                assertEquals((expectedCard ushr 16) and 0xFF, (expectedCard ushr 8) and 0xFF)
                assertEquals((expectedCard ushr 8) and 0xFF, expectedCard and 0xFF)
            }
            accents.forEach { accent ->
                val themed = base.copy(colors = base.colors.copy(
                    primary = accent.toInt(),
                    surfaceTint = accent.toInt(),
                ))
                listOf(0, 30, 100).forEach { strength ->
                    val surfaces = NgDrawerPalette.resolveSurfaceColors(themed, strength)
                    assertEquals(container, surfaces.top)
                    assertEquals(container, surfaces.bottom)
                    val result = NgDrawerPalette.applyAdaptiveContentCardRoles(themed, strength)
                    assertEquals(expectedCard, result.colors.cardContainer)
                    listOf(surfaces.top, surfaces.bottom, expectedCard).forEach { background ->
                        assertTrue(NgColorMath.contrastRatio(result.colors.onSurface, background) >= 4.5)
                        assertTrue(NgColorMath.contrastRatio(result.colors.onSurfaceVariant, background) >= 4.5)
                        assertTrue(NgColorMath.contrastRatio(result.colors.primary, background) >= 3.0)
                    }
                }
            }
        }
    }

    @Test
    fun `night cards retain distinct theme roles and preserve already elevated colors`() {
        val base = snapshot(seed = 0xFFF3B953.toInt(), isDark = true)
        // 自动色板的卡片角色可能比抽屉更暗，不能直接沿用而丢失层次。
        val low = base.copy(colors = base.colors.copy(
            drawerContainer = 0xFF29252D.toInt(),
            cardContainer = 0xFF17131B.toInt(),
        ))
        val resolved = Hct.fromInt(NgDrawerPalette.resolveAdaptiveContentCardColor(low, 30))
        assertTrue(resolved.tone > Hct.fromInt(low.colors.drawerContainer).tone + 3.5)
        assertTrue(hueDistance(resolved.hue, Hct.fromInt(low.colors.cardContainer).hue) < 8.0)

        val highColor = 0xFF485365.toInt()
        val elevated = low.copy(colors = low.colors.copy(cardContainer = highColor))
        assertEquals(highColor, NgDrawerPalette.resolveAdaptiveContentCardColor(elevated, 30))
        val eink = elevated.copy(isEInk = true)
        assertEquals(0xFFFFFFFF.toInt(), NgDrawerPalette.resolveAdaptiveContentCardColor(eink, 30))
    }

    @Test
    fun `custom backgrounds stay independent from primary strength and accent hue`() {
        val background = 0xFF0B192F.toInt()
        val gold = snapshot(seed = 0xFFF3B953.toInt(), isDark = true)
        val red = snapshot(seed = 0xFFDA4040.toInt(), isDark = true)
        val expected = NgDrawerPalette.resolveSurfaceColors(gold, 0, background)

        listOf(0, 30, 100).forEach { strength ->
            assertEquals(expected, NgDrawerPalette.resolveSurfaceColors(gold, strength, background))
            assertEquals(expected, NgDrawerPalette.resolveSurfaceColors(red, strength, background))
        }
        assertEquals(0xFF0B192F.toInt(), expected.bottom)
    }

    @Test
    fun `custom background derives readable cards and content without changing application colors`() {
        val base = snapshot(seed = 0xFFF3B953.toInt(), isDark = true)
        val background = 0xFF0B192F.toInt()
        val result = NgDrawerPalette.applyAdaptiveContentCardRoles(base, 100, background)
        val surfaces = NgDrawerPalette.resolveSurfaceColors(base, 100, background)

        assertTrue(Hct.fromInt(result.colors.cardContainer).tone > Hct.fromInt(background).tone)
        assertEquals(background, result.colors.onPrimary)
        assertTrue(minContrast(result.colors.onSurface, surfaces) >= 4.5)
        assertTrue(NgColorMath.contrastRatio(result.colors.onSurface, result.colors.cardContainer) >= 4.5)
        assertTrue(NgColorMath.contrastRatio(result.colors.onSurfaceVariant, result.colors.cardContainer) >= 4.5)
        assertTrue(NgColorMath.contrastRatio(result.colors.onPrimary, result.colors.primary) >= 4.5)
        assertNotEquals(result.colors.cardContainer, base.colors.cardContainer)
        assertEquals(base.colors.background, result.colors.background)
    }

    @Test
    fun `eink ignores custom backgrounds`() {
        val base = snapshot(seed = 0xFFF3B953.toInt(), isDark = true)
        val eink = base.copy(isEInk = true)
        val background = 0xFF0B192F.toInt()
        assertEquals(
            NgDrawerPalette.resolveSurfaceColors(eink, 30),
            NgDrawerPalette.resolveSurfaceColors(eink, 30, background),
        )
        assertEquals(
            NgDrawerPalette.applyAdaptiveContentCardRoles(eink, 30),
            NgDrawerPalette.applyAdaptiveContentCardRoles(eink, 30, background),
        )
    }

    @Test
    fun `custom background readability follows the chosen color instead of the system night flag`() {
        listOf(false, true).forEach { dark ->
            listOf(0xFF101820.toInt(), 0xFF666666.toInt(), 0xFFF5F0E0.toInt()).forEach { background ->
                val base = snapshot(seed = 0xFFF3B953.toInt(), isDark = dark)
                val result = NgDrawerPalette.applyAdaptiveContentCardRoles(base, 30, background)
                val surfaces = NgDrawerPalette.resolveSurfaceColors(base, 30, background)
                assertTrue(minContrast(result.colors.onSurface, surfaces) >= 4.5)
                assertTrue(NgColorMath.contrastRatio(result.colors.onSurface, result.colors.cardContainer) >= 4.5)
                assertEquals(base.colors.background, result.colors.background)
            }
        }
    }

    @Test
    fun `image veil keeps artwork visible without theme tint even when day and night are reversed`() {
        listOf(false, true).forEach { dark ->
            listOf(0xFF151515.toInt(), 0xFFF1F1F1.toInt()).forEach { text ->
                val base = snapshot(seed = 0xFFF3B953.toInt(), isDark = dark)
                val resolved = base.copy(colors = base.colors.copy(
                    onSurface = text,
                    onSurfaceVariant = text,
                ))
                val mask = NgDrawerPalette.resolveImageMask(resolved)
                assertTrue("The background must retain at least 80% of its image", mask.alpha in 0f..0.20f)
                assertEquals(
                    if (NgColorMath.isLight(text)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                    mask.color,
                )
                listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()).forEach { background ->
                    assertEquals(mask, NgDrawerPalette.resolveImageMask(
                        resolved.copy(colors = resolved.colors.copy(background = background))
                    ))
                }
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
