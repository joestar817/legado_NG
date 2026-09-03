package io.legado.app.ui.design.theme

import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class NgThemeResolverTest {

    @Test
    fun lightThemeProducesReadableSemanticColors() {
        val snapshot = NgThemeResolver.resolve(lightInput())

        assertFalse(snapshot.isDark)
        assertEquals(0xFFF78E66.toInt(), snapshot.colors.primary)
        assertEquals(0xFF795548.toInt(), snapshot.colors.topBarContainer)
        assertTrue(
            NgColorMath.contrastRatio(
                snapshot.colors.background,
                snapshot.colors.onBackground
            ) >= 4.5
        )
        assertTrue(
            NgColorMath.contrastRatio(
                snapshot.colors.surface,
                snapshot.colors.onSurface
            ) >= 4.5
        )
        assertNotEquals(snapshot.colors.surface, snapshot.colors.surfaceVariant)
        assertEquals(snapshot.colors.primaryContainer, snapshot.colors.selectedContainer)
    }

    @Test
    fun darkThemeUsesLightSystemBarIcons() {
        val snapshot = NgThemeResolver.resolve(
            lightInput().copy(
                backgroundColor = 0xFF202020.toInt(),
                bottomBackground = 0xFF2A2A2A.toInt(),
                isDark = true
            )
        )

        assertTrue(snapshot.isDark)
        assertFalse(snapshot.systemBars.darkStatusBarIcons)
        assertFalse(snapshot.systemBars.darkNavigationBarIcons)
        assertTrue(
            NgColorMath.contrastRatio(
                snapshot.colors.surface,
                snapshot.colors.onSurface
            ) >= 4.5
        )
    }

    @Test
    fun einkDisablesBlurMotionAndTransparency() {
        val snapshot = NgThemeResolver.resolve(lightInput().copy(isEInk = true))

        assertTrue(snapshot.isEInk)
        assertFalse(snapshot.effects.blurEnabled)
        assertFalse(snapshot.motion.enabled)
        assertEquals(1f, snapshot.effects.containerAlpha)
        assertEquals(1f, snapshot.effects.dialogAlpha)
        assertEquals(0, snapshot.effects.blurRadiusDp)
        assertEquals(0, snapshot.motion.mediumDurationMs)
    }

    @Test
    fun chromaScalingKeepsToneAndUsesTheOriginalColorAsTheEndpoint() {
        val sourceColor = 0xFFF78E66.toInt()
        val source = Hct.fromInt(sourceColor)
        val neutral = Hct.fromInt(NgColorMath.scaleChroma(sourceColor, 0f))
        val middle = Hct.fromInt(NgColorMath.scaleChroma(sourceColor, 0.5f))
        val original = Hct.fromInt(NgColorMath.scaleChroma(sourceColor, 1f))

        assertEquals(source.tone, neutral.tone, 1.0)
        assertEquals(source.tone, middle.tone, 1.0)
        assertEquals(source.tone, original.tone, 1.0)
        assertTrue(neutral.chroma < middle.chroma)
        assertEquals(source.chroma * 0.5, middle.chroma, 1.5)
        assertEquals(source.chroma, original.chroma, 1.0)
    }

    @Test
    fun neutralToPrimaryBlendLinearlyChangesEveryArgbChannel() {
        val neutralContainer = 0xFFEEEEEE.toInt()
        val targetColor = 0xFFF78E66.toInt()
        fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

        (0..100).forEach { percent ->
            val fraction = percent / 100f
            val actual = NgColorMath.blend(neutralContainer, targetColor, fraction)
            val inverse = 1f - fraction
            assertEquals(
                (channel(neutralContainer, 24) * inverse +
                    channel(targetColor, 24) * fraction).roundToInt(),
                channel(actual, 24),
            )
            assertEquals(
                (channel(neutralContainer, 16) * inverse +
                    channel(targetColor, 16) * fraction).roundToInt(),
                channel(actual, 16),
            )
            assertEquals(
                (channel(neutralContainer, 8) * inverse +
                    channel(targetColor, 8) * fraction).roundToInt(),
                channel(actual, 8),
            )
            assertEquals(
                (channel(neutralContainer, 0) * inverse +
                    channel(targetColor, 0) * fraction).roundToInt(),
                channel(actual, 0),
            )
        }
    }

    @Test
    fun unreadablePreferredContentFallsBackToContrastingColor() {
        val white = 0xFFFFFFFF.toInt()

        val contentColor = NgColorMath.readableContentColor(
            background = white,
            preferred = white,
        )

        assertEquals(NgColorMath.contentColorFor(white), contentColor)
        assertTrue(NgColorMath.contrastRatio(contentColor, white) >= 4.5)
    }

    @Test
    fun readablePreferredContentIsPreserved() {
        val containerColor = 0xFF1565C0.toInt()
        val preferredContentColor = 0xFFFFFFFF.toInt()

        assertEquals(
            preferredContentColor,
            NgColorMath.readableContentColor(containerColor, preferredContentColor),
        )
    }

    private fun lightInput() = NgLegacyThemeInput(
        primaryColor = 0xFF795548.toInt(),
        accentColor = 0xFFF78E66.toInt(),
        backgroundColor = 0xFFF5F5F5.toInt(),
        bottomBackground = 0xFFEEEEEE.toInt(),
        errorColor = 0xFFB3261E.toInt(),
        isDark = false,
        isEInk = false
    )
}
