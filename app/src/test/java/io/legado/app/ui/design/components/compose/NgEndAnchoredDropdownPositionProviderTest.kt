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

    @Test
    fun characterDrawerMenusFollowTheirFieldsWhenTheDrawerHeightChanges() {
        val display = IntSize(1116, 2408)
        val genderArrow = IntRect(467, 478, 527, 610)
        val tagArrow = IntRect(978, 478, 1038, 610)
        val genderMenu = IntSize(164, 444)
        val tagMenu = IntSize(164, 972)

        for (hostTop in listOf(842, 833)) {
            val gender = characterDrawerProvider(genderArrow, hostTop)
            val tag = characterDrawerProvider(tagArrow, hostTop)
            assertEquals(IntOffset(363, hostTop + 622), gender.calculatePosition(genderArrow, display, LayoutDirection.Ltr, genderMenu))
            assertEquals(IntOffset(874, hostTop + 622), tag.calculatePosition(tagArrow, display, LayoutDirection.Ltr, tagMenu))
        }
        val promoteGender = characterDrawerProvider(genderArrow, hostTop = 1197)
        val promoteTag = characterDrawerProvider(tagArrow, hostTop = 1197)
        assertEquals(IntOffset(363, 1819), promoteGender.calculatePosition(genderArrow, display, LayoutDirection.Ltr, genderMenu))
        assertEquals(IntOffset(874, 691), promoteTag.calculatePosition(tagArrow, display, LayoutDirection.Ltr, tagMenu))
    }

    @Test
    fun characterTagMenuScrollsAboveItsArrowWhenTheKeyboardRestrictsSpace() {
        val arrow = IntRect(978, 478, 1038, 610)
        val provider = characterDrawerProvider(arrow, hostTop = 360, visibleBottom = 1250)
        val display = IntSize(1116, 1178)

        provider.calculatePosition(arrow, display, LayoutDirection.Ltr, IntSize(164, 1))
        assertEquals(730, heightLimit)
        val position = provider.calculatePosition(arrow, display, LayoutDirection.Ltr, IntSize(164, heightLimit))

        assertEquals(IntOffset(874, 96), position)
        assertEquals(arrow.top + 360 - 12, position.y + heightLimit)
    }

    private fun characterDrawerProvider(anchorInRoot: IntRect, hostTop: Int, visibleBottom: Int = 2480): NgEndAnchoredDropdownPositionProvider {
        val host = IntOffset(0, hostTop)
        val geometry = NgPopupWindowGeometry(
            hostPositionInWindow = host,
            visibleBoundsInWindow = popupVisibleFrameInWindow(IntRect(0, 72, 1116, visibleBottom), host, host),
        )
        return NgEndAnchoredDropdownPositionProvider(
            paneSize = null,
            horizontalOffset = 0,
            gap = 12,
            margin = 24,
            visibleBoundsProvider = { geometry.visibleBoundsInWindow },
            anchorInWindow = geometry.anchorInWindow(anchorInRoot),
        ) { availableHeight, _ -> heightLimit = availableHeight }
    }
}
