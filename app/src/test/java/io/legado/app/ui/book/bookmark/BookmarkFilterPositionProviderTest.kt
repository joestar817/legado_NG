package io.legado.app.ui.book.bookmark

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkFilterPositionProviderTest {
    private val provider = BookmarkFilterPositionProvider(marginPx = 8, anchorOffsetPx = 16)

    @Test
    fun slideMovesFromWindowRightEdgeToRestingPositionAndBack() {
        var fraction = 1f
        val animatedProvider = BookmarkFilterPositionProvider(8, 16) { fraction }
        fun position() = animatedProvider.calculatePosition(
            anchorBounds = IntRect(24, 32, 68, 76),
            windowSize = IntSize(400, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(256, 300),
        )

        assertEquals(IntOffset(400, 92), position())
        fraction = 0.5f
        assertEquals(IntOffset(268, 92), position())
        fraction = 0f
        assertEquals(IntOffset(136, 92), position())
        fraction = 1f
        assertEquals(IntOffset(400, 92), position())
    }

    @Test
    fun menuUsesWindowRightEdgeInsteadOfAnchorCenter() {
        val leftAnchor = provider.calculatePosition(
            anchorBounds = IntRect(24, 32, 68, 76),
            windowSize = IntSize(400, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(256, 300),
        )
        val rightAnchor = provider.calculatePosition(
            anchorBounds = IntRect(300, 32, 344, 76),
            windowSize = IntSize(400, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(256, 300),
        )

        assertEquals(IntOffset(136, 92), leftAnchor)
        assertEquals(leftAnchor, rightAnchor)
    }

    @Test
    fun rtlKeepsTheSamePhysicalRightEdge() {
        val anchor = IntRect(300, 32, 344, 76)
        val window = IntSize(400, 800)
        val content = IntSize(256, 300)

        assertEquals(
            provider.calculatePosition(anchor, window, LayoutDirection.Ltr, content),
            provider.calculatePosition(anchor, window, LayoutDirection.Rtl, content),
        )
    }

    @Test
    fun narrowWindowKeepsSafeMarginsWithConstrainedMenuWidth() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(70, 32, 114, 76),
            windowSize = IntSize(120, 500),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(104, 300),
        )

        assertEquals(IntOffset(8, 92), position)
    }

    @Test
    fun bottomAnchorClampsMenuAboveBottomMargin() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(300, 720, 344, 764),
            windowSize = IntSize(400, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(256, 300),
        )

        assertEquals(IntOffset(136, 492), position)
    }

    @Test
    fun lowWindowClampsToItsRemainingSpace() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(300, 100, 344, 144),
            windowSize = IntSize(400, 200),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(256, 180),
        )

        assertEquals(IntOffset(136, 12), position)
    }

    @Test
    fun temporarilyOversizedContentNeverProducesNegativeCoordinates() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(0, -30, 44, 14),
            windowSize = IntSize(80, 60),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(256, 300),
        )

        assertEquals(IntOffset.Zero, position)
    }
}
