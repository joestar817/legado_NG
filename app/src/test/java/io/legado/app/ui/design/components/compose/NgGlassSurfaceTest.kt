package io.legado.app.ui.design.components.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.materialkolor.hct.Hct
import io.legado.app.help.config.NgVisualSystem.LIQUID_GLASS
import io.legado.app.help.config.NgVisualSystem.TRANSPARENT_GLASS
import io.legado.app.ui.book.read.ReadFloatingPalette
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgGlassSurfaceTest {

    @Test
    fun windowMaterialWinsOverSurfaceAndAppMaterial() {
        assertEquals(
            LIQUID_GLASS,
            resolveNgSurfaceVisualSystem(
                window = LIQUID_GLASS,
                surface = TRANSPARENT_GLASS,
                theme = TRANSPARENT_GLASS,
            ),
        )
        assertEquals(
            TRANSPARENT_GLASS,
            resolveNgSurfaceVisualSystem(
                window = TRANSPARENT_GLASS,
                surface = LIQUID_GLASS,
                theme = LIQUID_GLASS,
            ),
        )
    }

    @Test
    fun appWindowsWithoutMaterialPreferenceKeepTheirNormalPrecedence() {
        assertEquals(
            TRANSPARENT_GLASS,
            resolveNgSurfaceVisualSystem(window = null, surface = TRANSPARENT_GLASS, theme = LIQUID_GLASS),
        )
        assertEquals(
            LIQUID_GLASS,
            resolveNgSurfaceVisualSystem(window = null, surface = null, theme = LIQUID_GLASS),
        )
    }

    @Test
    fun defaultStyleUsesThemeDrawerAlphaAndSemanticContentColor() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val style = resolveNgGlassStyle(snapshot)

        assertEquals(snapshot.effects.drawerAlpha, style.containerBottom.alpha, 0.001f)
        assertEquals(
            (snapshot.effects.drawerAlpha + 0.06f).coerceAtMost(1f),
            style.containerTop.alpha,
            0.005f
        )
        assertEquals(Color(snapshot.colors.onSurface), style.contentColor)
        assertEquals(snapshot.effects.blurRadiusDp.dp, style.blurRadius)
        assertTrue(style.edgeHighlight.alpha > 0f)
    }

    @Test
    fun styleFollowsThemePaletteInsteadOfUsingFixedWarmColors() {
        val warm = resolveNgGlassStyle(
            NgThemeResolver.resolve(lightInput())
        )
        val bamboo = resolveNgGlassStyle(
            NgThemeResolver.resolve(
                lightInput().copy(
                    primaryColor = 0xFF7F9554.toInt(),
                    accentColor = 0xFF496A2E.toInt(),
                    bottomBackground = 0xFFEFF7EA.toInt()
                )
            )
        )

        assertNotEquals(warm.containerTop, bamboo.containerTop)
        assertNotEquals(warm.containerBottom, bamboo.containerBottom)
        assertNotEquals(warm.accentGlow, bamboo.accentGlow)
    }

    @Test
    fun floatingStyleKeepsLiveBackgroundVisibleAndStrengthensCardEdge() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val drawer = resolveNgGlassStyle(snapshot)
        val floating = resolveNgFloatingGlassStyle(snapshot)

        assertTrue(floating.containerTop.alpha < drawer.containerTop.alpha)
        assertTrue(floating.containerBottom.alpha < drawer.containerBottom.alpha)
        assertEquals(0.80f, floating.containerTop.alpha, 0.001f)
        assertEquals(0.76f, floating.containerBottom.alpha, 0.001f)
        assertEquals(0.dp, floating.blurRadius)
        assertTrue(floating.borderColor.alpha > drawer.borderColor.alpha)
        assertTrue(floating.edgeHighlight.alpha > drawer.edgeHighlight.alpha)
        assertTrue(floating.surfaceGloss.alpha < drawer.surfaceGloss.alpha)
        assertTrue(floating.depthEdge.alpha > drawer.depthEdge.alpha)
        assertEquals(0.dp, floating.shadowElevation)
    }

    @Test
    fun floatingAppearanceUsesDockTransparencyAndKeepsOriginalMaterialAtHalf() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val opaque = resolveNgFloatingGlassStyle(
            snapshot = snapshot,
            transparencyPercent = 0,
            primaryStrengthPercent = 50
        )
        val transparent = resolveNgFloatingGlassStyle(
            snapshot = snapshot,
            transparencyPercent = 100,
            primaryStrengthPercent = 50
        )
        val neutral = resolveNgFloatingGlassStyle(
            snapshot = snapshot,
            transparencyPercent = 20,
            primaryStrengthPercent = 0
        )
        val middle = resolveNgFloatingGlassStyle(
            snapshot = snapshot,
            transparencyPercent = 20,
            primaryStrengthPercent = 50
        )
        val enhanced = resolveNgFloatingGlassStyle(
            snapshot = snapshot,
            transparencyPercent = 20,
            primaryStrengthPercent = 100
        )
        val expectedMiddle = NgColorMath.blend(
            snapshot.colors.drawerContainer,
            snapshot.colors.primary,
            0.13f,
        )
        val expectedEnhanced = NgColorMath.blend(
            snapshot.colors.drawerContainer,
            snapshot.colors.primary,
            0.5f,
        )

        assertEquals(1f, opaque.containerTop.alpha, 0.001f)
        assertEquals(1f, opaque.containerBottom.alpha, 0.001f)
        assertEquals(0.80f, neutral.containerTop.alpha, 0.001f)
        assertEquals(0.76f, neutral.containerBottom.alpha, 0.001f)
        assertEquals(0f, transparent.containerTop.alpha, 0.001f)
        assertEquals(0f, transparent.containerBottom.alpha, 0.001f)
        assertEquals(
            Color(NgColorMath.opaque(snapshot.colors.drawerContainer)),
            neutral.containerBottom.copy(alpha = 1f),
        )
        assertEquals(
            Color(NgColorMath.opaque(expectedMiddle)),
            middle.containerBottom.copy(alpha = 1f),
        )
        assertEquals(
            Color(NgColorMath.opaque(expectedEnhanced)),
            enhanced.containerBottom.copy(alpha = 1f),
        )
        assertEquals(neutral.containerBottom.copy(alpha = 1f), neutral.accentGlow.copy(alpha = 1f))
        assertEquals(enhanced.containerBottom.copy(alpha = 1f), enhanced.accentGlow.copy(alpha = 1f))
        assertTrue(enhanced.containerBottom != Color(snapshot.colors.primary))
    }

    @Test
    fun floatingAppearancePrintsAndValidatesEveryPrimaryStrengthColor() {
        val snapshot = NgThemeResolver.resolve(
            lightInput().copy(bottomBackground = 0xFFEEEEEE.toInt())
        )
        val neutralTop = NgColorMath.blend(
            snapshot.colors.drawerContainer,
            snapshot.colors.surface,
            0.24f,
        )

        (0..100).forEach { percent ->
            val style = resolveNgFloatingGlassStyle(
                snapshot = snapshot,
                transparencyPercent = 0,
                primaryStrengthPercent = percent,
            )
            val top = style.containerTop.toArgb()
            val bottom = style.containerBottom.toArgb()
            val baseProgress = (percent / 50f).coerceAtMost(1f)
            val enhanceProgress = ((percent - 50) / 50f).coerceIn(0f, 1f)
            val topTint = 0.22f * enhanceProgress
            val bottomTint = if (percent <= 50) {
                0.13f * baseProgress
            } else {
                0.13f + (0.50f - 0.13f) * enhanceProgress
            }
            println(
                "%03d%% top=#%08X bottom=#%08X".format(
                    percent,
                    top,
                    bottom,
                )
            )
            assertEquals(
                NgColorMath.opaque(
                    NgColorMath.blend(neutralTop, snapshot.colors.primary, topTint)
                ),
                top,
            )
            assertEquals(
                NgColorMath.opaque(
                    NgColorMath.blend(
                        snapshot.colors.drawerContainer,
                        snapshot.colors.primary,
                        bottomTint,
                    )
                ),
                bottom,
            )
        }
    }

    @Test
    fun pickedSeedAppearancePrintsAndValidatesEveryPrimaryStrengthColor() {
        val picked = 0xFFF6E4D1.toInt()
        val base = NgThemeResolver.resolve(
            lightInput().copy(bottomBackground = 0xFFEEEEEE.toInt())
        )
        val snapshot = ReadFloatingPalette.applySeed(base, picked)
        val neutralTop = NgColorMath.blend(
            snapshot.colors.drawerContainer,
            snapshot.colors.surface,
            0.24f,
        )

        assertEquals(picked, snapshot.colors.primary)
        (0..100).forEach { percent ->
            val style = resolveNgFloatingGlassStyle(
                snapshot = snapshot,
                transparencyPercent = 0,
                primaryStrengthPercent = percent,
            )
            val top = style.containerTop.toArgb()
            val bottom = style.containerBottom.toArgb()
            val baseProgress = (percent / 50f).coerceAtMost(1f)
            val enhanceProgress = ((percent - 50) / 50f).coerceIn(0f, 1f)
            val topTint = 0.22f * enhanceProgress
            val bottomTint = if (percent <= 50) {
                0.13f * baseProgress
            } else {
                0.13f + (0.50f - 0.13f) * enhanceProgress
            }
            println(
                "picked %03d%% top=#%08X bottom=#%08X".format(
                    percent,
                    top,
                    bottom,
                )
            )
            assertEquals(
                NgColorMath.opaque(
                    NgColorMath.blend(neutralTop, picked, topTint)
                ),
                top,
            )
            assertEquals(
                NgColorMath.opaque(
                    NgColorMath.blend(
                        snapshot.colors.drawerContainer,
                        picked,
                        bottomTint,
                    )
                ),
                bottom,
            )
        }
        assertEquals(
            NgColorMath.opaque(
                NgColorMath.blend(snapshot.colors.drawerContainer, picked, 0.5f)
            ),
            resolveNgFloatingGlassStyle(
                snapshot = snapshot,
                transparencyPercent = 0,
                primaryStrengthPercent = 100,
            ).containerBottom.toArgb(),
        )
    }

    @Test
    fun dialogAlphaKeepsDenseGlassReadableWithoutMakingItOpaque() {
        val snapshot = NgThemeResolver.resolve(lightInput())
        val style = resolveNgGlassStyle(
            snapshot,
            requestedContainerAlpha = snapshot.effects.dialogAlpha
        )

        assertEquals(0.94f, style.containerTop.alpha, 0.005f)
        assertEquals(0.88f, style.containerBottom.alpha, 0.005f)
        assertTrue(style.containerTop.alpha < 1f)
        assertTrue(style.containerBottom.alpha < 1f)
    }

    @Test
    fun einkStyleDisablesGlassEffectsAndTransparency() {
        val snapshot = NgThemeResolver.resolve(lightInput().copy(isEInk = true))
        val style = resolveNgGlassStyle(snapshot, requestedContainerAlpha = 0.5f)

        assertEquals(1f, style.containerTop.alpha, 0.001f)
        assertEquals(1f, style.containerBottom.alpha, 0.001f)
        assertEquals(0f, style.accentGlow.alpha, 0.001f)
        assertEquals(0f, style.edgeHighlight.alpha, 0.001f)
        assertEquals(0f, style.surfaceGloss.alpha, 0.001f)
        assertEquals(0f, style.depthEdge.alpha, 0.001f)
        assertEquals(0.dp, style.blurRadius)
        assertEquals(0.dp, style.shadowElevation)
    }

    private fun lightInput() = NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(),
        accentColor = 0xFFF78E66.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(),
        bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(),
        isDark = false,
        isEInk = false
    )
}
