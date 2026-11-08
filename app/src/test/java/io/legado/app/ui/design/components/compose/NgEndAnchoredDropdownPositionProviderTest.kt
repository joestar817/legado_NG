package io.legado.app.ui.design.components.compose

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.legado.app.ui.main.bookshelf.BookshelfContentToolbarMenuOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class NgEndAnchoredDropdownPositionProviderTest {

    private val window = IntSize(400, 800)
    private val anchor = IntRect(150, 40, 220, 88)
    private var heightLimit = 0

    private fun provider(paneSize: IntSize? = null, gap: Int = 8) = NgEndAnchoredDropdownPositionProvider(
        paneSize = paneSize,
        horizontalOffset = 0,
        gap = gap,
        margin = 8,
    ) { availableHeight, _ -> heightLimit = availableHeight }

    @Test
    fun menuKeepsTheGapBelowTheAnchor() {
        val position = provider().calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(112, 104))

        assertEquals(IntOffset(108, 96), position)
        assertEquals(696, heightLimit)
    }

    @Test
    fun groupedToolbarKeepsEightPixelsBelowTheVisiblePillAtUnitDensity() {
        val gap = with(Density(1f)) { BookshelfContentToolbarMenuOffset.y.roundToPx() }
        val touchBounds = IntRect(150, 48, 220, 88)
        val visiblePillBottom = 80

        val position = provider(gap = gap).calculatePosition(
            touchBounds, window, LayoutDirection.Ltr, IntSize(112, 104),
        )

        assertEquals(8, position.y - visiblePillBottom)
    }

    @Test
    fun longMenuStaysBelowAfterItsHeightIsConstrained() {
        val provider = provider()
        provider.calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(112, 1))

        val position = provider.calculatePosition(
            anchor, window, LayoutDirection.Ltr, IntSize(112, heightLimit),
        )

        assertEquals(IntOffset(108, 96), position)
        assertEquals(window.height - 8, position.y + heightLimit)
    }

    @Test
    fun shortMenuStillOpensBelowWhenThereIsMoreSpaceAbove() {
        val position = provider().calculatePosition(
            IntRect(150, 200, 220, 248), IntSize(400, 360), LayoutDirection.Ltr, IntSize(112, 80),
        )

        assertEquals(IntOffset(108, 256), position)
    }

    @Test
    fun menuKeepsTheGapAboveWhenSpaceBelowIsLimited() {
        val position = provider().calculatePosition(
            IntRect(280, 270, 330, 318), IntSize(400, 350), LayoutDirection.Ltr, IntSize(112, 200),
        )

        assertEquals(IntOffset(218, 62), position)
        assertEquals(254, heightLimit)
    }

    @Test
    fun alignsToTheAnchorEndInRightToLeftLayouts() {
        val position = provider().calculatePosition(anchor, window, LayoutDirection.Rtl, IntSize(112, 104))

        assertEquals(IntOffset(150, 96), position)
    }

    @Test
    fun narrowPaneConstrainsPositionAndAvailableHeight() {
        val position = provider(IntSize(260, 360)).calculatePosition(
            IntRect(220, 40, 264, 88), window, LayoutDirection.Ltr, IntSize(112, 104),
        )

        assertEquals(IntOffset(140, 96), position)
        assertEquals(256, heightLimit)
    }
}
