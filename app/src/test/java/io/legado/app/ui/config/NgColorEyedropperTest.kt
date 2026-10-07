package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class NgColorEyedropperTest {
    @Test
    fun pixelCoordinateUsesTheDisplayedPixelInsteadOfRoundingToItsNeighbour() {
        assertEquals(0, ngEyedropperPixelCoordinate(0.9f, 100, 100))
        assertEquals(1, ngEyedropperPixelCoordinate(1f, 100, 100))
        assertEquals(49, ngEyedropperPixelCoordinate(49.99f, 100, 100))
    }

    @Test
    fun pixelCoordinateAccountsForViewportScaling() {
        assertEquals(250, ngEyedropperPixelCoordinate(50f, 200, 1000))
        assertEquals(999, ngEyedropperPixelCoordinate(199.99f, 200, 1000))
        assertEquals(10, ngEyedropperPixelCoordinate(51f, 500, 100))
    }

    @Test
    fun pixelCoordinateClampsDraggingBeyondEveryEdge() {
        assertEquals(0, ngEyedropperPixelCoordinate(-100f, 200, 1080))
        assertEquals(1079, ngEyedropperPixelCoordinate(200f, 200, 1080))
        assertEquals(1079, ngEyedropperPixelCoordinate(1000f, 200, 1080))
        assertEquals(0, ngEyedropperPixelCoordinate(200f, 200, 1))
    }

    @Test
    fun pixelCoordinateHandlesUnavailableLayoutAndInvalidPointerValues() {
        assertEquals(0, ngEyedropperPixelCoordinate(100f, 0, 1080))
        assertEquals(0, ngEyedropperPixelCoordinate(100f, 200, 0))
        assertEquals(0, ngEyedropperPixelCoordinate(Float.NaN, 200, 1080))
        assertEquals(0, ngEyedropperPixelCoordinate(Float.POSITIVE_INFINITY, 200, 1080))
    }
}
