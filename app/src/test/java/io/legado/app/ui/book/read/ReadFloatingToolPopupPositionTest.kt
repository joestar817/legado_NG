package io.legado.app.ui.book.read

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadFloatingToolPopupPositionTest {
    private val window = IntSize(1080, 1920)

    @Test
    fun panelsFollowEachButtonsActualPositionAndSize() {
        val provider = ReadFloatingToolPopupPositionProvider(dockRight = false, gapPx = 16, marginPx = 8)
        val panel = IntSize(600, 80)
        val first = provider.calculatePosition(IntRect(16, 440, 96, 520), window, LayoutDirection.Ltr, panel)
        val moved = provider.calculatePosition(IntRect(16, 920, 96, 1000), window, LayoutDirection.Ltr, panel)
        val taller = provider.calculatePosition(IntRect(16, 920, 96, 1000), window, LayoutDirection.Ltr, IntSize(250, 160))

        assertEquals(IntOffset(112, 440), first)
        assertEquals(IntOffset(112, 920), moved)
        assertEquals(IntOffset(112, 880), taller)
    }

    @Test
    fun rightDockExpandsInwardAndUsesPhysicalDockSideInRtl() {
        val provider = ReadFloatingToolPopupPositionProvider(dockRight = true, gapPx = 16, marginPx = 8)
        val anchor = IntRect(984, 600, 1064, 680)
        val panel = IntSize(400, 100)
        assertEquals(IntOffset(568, 590), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, panel))
        assertEquals(IntOffset(568, 590), provider.calculatePosition(anchor, window, LayoutDirection.Rtl, panel))
    }

    @Test
    fun visibleWindowEdgesClampPanelWhenAnchorIsNearBoundary() {
        var visible = IntRect(10, 50, 590, 950)
        val provider = ReadFloatingToolPopupPositionProvider(false, 8, 8) { visible }
        val windowSize = IntSize(600, 1000)
        val panel = IntSize(200, 120)
        val top = provider.calculatePosition(IntRect(20, 55, 64, 103), windowSize, LayoutDirection.Ltr, panel)
        val bottom = provider.calculatePosition(IntRect(20, 900, 64, 948), windowSize, LayoutDirection.Ltr, panel)
        val right = provider.calculatePosition(IntRect(510, 400, 554, 448), windowSize, LayoutDirection.Ltr, panel)
        assertEquals(IntOffset(72, 58), top)
        assertEquals(IntOffset(72, 822), bottom)
        assertEquals(IntOffset(382, 364), right)

        visible = IntRect(10, 50, 590, 850)
        assertEquals(IntOffset(72, 722), provider.calculatePosition(
            IntRect(20, 900, 64, 948), windowSize, LayoutDirection.Ltr, panel,
        ))
    }
}
