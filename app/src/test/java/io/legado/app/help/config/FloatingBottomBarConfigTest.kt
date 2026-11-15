package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBottomBarConfigTest {

    @Test
    fun `automatic distance preserves the former 12dp margin`() {
        assertEquals(
            36,
            FloatingBottomBarConfig.resolveBottomDistancePx(
                FloatingBottomBarConfig.AUTOMATIC_BOTTOM_DISTANCE_PX,
                density = 3f
            )
        )
    }

    @Test
    fun `default distance stays at forty physical pixels`() {
        assertEquals(
            40,
            FloatingBottomBarConfig.resolveBottomDistancePx(
                FloatingBottomBarConfig.DEFAULT_BOTTOM_DISTANCE_PX,
                density = 3f
            )
        )
    }

    @Test
    fun `explicit distance is clamped and snapped to five pixels`() {
        assertEquals(0, FloatingBottomBarConfig.normalizeBottomDistancePx(-1))
        assertEquals(35, FloatingBottomBarConfig.normalizeBottomDistancePx(37))
        assertEquals(100, FloatingBottomBarConfig.normalizeBottomDistancePx(99))
        assertEquals(100, FloatingBottomBarConfig.normalizeBottomDistancePx(120))
    }

    @Test
    fun `surface alpha follows transparency percentage`() {
        assertEquals(0.6f, FloatingBottomBarConfig.surfaceAlpha(40), 0.001f)
        assertEquals(1f, FloatingBottomBarConfig.surfaceAlpha(0), 0.001f)
        assertEquals(0f, FloatingBottomBarConfig.surfaceAlpha(100), 0.001f)
    }
}
