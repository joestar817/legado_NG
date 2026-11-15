package io.legado.app.ui.book.read.config

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadChoicePopupPositionTest {
    private val provider = EndAnchoredPopupPositionProvider(
        gapPx = 6,
        marginPx = 8,
        preferBelow = true,
        constrainToWindow = true,
    )
    private val window = IntSize(372, 827)

    @Test
    fun readingSettingsWidthFitsLabelsAndVisibleScreen() {
        assertEquals(120.dp, readSettingsPopupWidth(56.dp, 360.dp))
        assertEquals(215.dp, readSettingsPopupWidth(175.dp, 360.dp))
        assertEquals(344.dp, readSettingsPopupWidth(400.dp, 360.dp))
        assertEquals(84.dp, readSettingsPopupWidth(100.dp, 100.dp))
    }

    @Test
    fun readingSettingsPopupUsesTheScreenFrameInsteadOfTheDrawerBounds() {
        val provider = EndAnchoredPopupPositionProvider(
            gapPx = 6, marginPx = 8, preferBelow = true, constrainToWindow = true,
            visibleBoundsProvider = { IntRect(0, -400, 360, 400) },
        )
        val below = provider.calculatePosition(
            IntRect(250, 30, 344, 78), IntSize(360, 800), LayoutDirection.Ltr, IntSize(120, 210),
        )
        val above = provider.calculatePosition(
            IntRect(250, 320, 344, 368), IntSize(360, 800), LayoutDirection.Ltr, IntSize(120, 210),
        )

        assertEquals(IntOffset(224, 84), below)
        assertEquals(IntOffset(224, 104), above)
    }

    @Test
    fun actionsMenuAlignsToTheEndOfItsRow() {
        val position = provider.calculatePosition(
            IntRect(16, 500, 356, 548), window, LayoutDirection.Ltr, IntSize(156, 84),
        )

        assertEquals(IntOffset(200, 554), position)
    }

    @Test
    fun pageLayoutMenuOpensBelowAndAlignsWithTheValue() {
        val position = provider.calculatePosition(
            IntRect(258, 350, 356, 398), window, LayoutDirection.Ltr, IntSize(120, 168),
        )
        assertEquals(IntOffset(236, 404), position)
    }

    @Test
    fun pageLayoutMenuMovesAboveWhenBelowWouldBeClipped() {
        val position = provider.calculatePosition(
            IntRect(258, 680, 356, 728), window, LayoutDirection.Ltr, IntSize(120, 168),
        )
        assertEquals(IntOffset(236, 506), position)
    }

    @Test
    fun presetMenuMovesAboveItsCardWhenThereIsNotEnoughRoomBelow() {
        val position = provider.calculatePosition(
            IntRect(200, 740, 285, 796), window, LayoutDirection.Ltr, IntSize(156, 210),
        )

        assertEquals(IntOffset(129, 524), position)
    }

    @Test
    fun presetMenuStaysInsideTheWindowNearTheLeftEdge() {
        val position = provider.calculatePosition(
            IntRect(4, 4, 80, 48), window, LayoutDirection.Ltr, IntSize(156, 210),
        )

        assertEquals(IntOffset(8, 54), position)
    }

    @Test
    fun presetMenuAlignsToTheStartOfTheCardInRightToLeftLayouts() {
        val position = provider.calculatePosition(
            IntRect(200, 740, 285, 796), window, LayoutDirection.Rtl, IntSize(156, 210),
        )

        assertEquals(IntOffset(200, 524), position)
    }

    @Test
    fun centeredPresetMenuPrefersAboveEvenWhenThereIsRoomBelow() {
        val presetProvider = EndAnchoredPopupPositionProvider(
            gapPx = 6, marginPx = 8, preferBelow = false,
            constrainToWindow = true, centerOnAnchor = true,
        )
        val position = presetProvider.calculatePosition(
            IntRect(200, 480, 280, 536), window, LayoutDirection.Ltr, IntSize(120, 240),
        )

        assertEquals(IntOffset(180, 234), position)
    }

    @Test
    fun centeredPresetMenuFallsBelowWhenThereIsNoRoomAbove() {
        val presetProvider = EndAnchoredPopupPositionProvider(
            gapPx = 6, marginPx = 8, preferBelow = false,
            constrainToWindow = true, centerOnAnchor = true,
        )
        val position = presetProvider.calculatePosition(
            IntRect(8, 70, 80, 126), window, LayoutDirection.Ltr, IntSize(120, 240),
        )

        assertEquals(IntOffset(8, 132), position)
    }

    @Test
    fun visibleScreenFrameIsTranslatedIntoTheDialogWindowCoordinates() {
        val bounds = popupVisibleFrameInWindow(
            screenFrame = IntRect(0, 72, 1116, 2480),
            locationOnScreen = IntOffset(30, 1404),
            locationInWindow = IntOffset(30, 20),
        )

        assertEquals(IntRect(0, -1312, 1116, 1096), bounds)
    }

    @Test
    fun readerRuleMenuOpensAboveWhenDisplaySpaceBelowTheDialogAnchorIsInsufficient() {
        val readerProvider = EndAnchoredPopupPositionProvider(
            gapPx = 18, marginPx = 24, preferBelow = true, constrainToWindow = true,
            visibleBoundsProvider = { IntRect(0, -1312, 1116, 1096) },
        )
        val anchor = IntRect(894, 704, 1038, 848)
        val position = readerProvider.calculatePosition(
            anchor, IntSize(1116, 2408), LayoutDirection.Ltr, IntSize(300, 504),
        )

        assertEquals(IntOffset(738, 182), position)
        assertEquals(anchor.top - 18, position.y + 504)
    }

    @Test
    fun readerRuleMenuOpensBelowWhenThereIsEnoughDisplaySpace() {
        val readerProvider = EndAnchoredPopupPositionProvider(
            gapPx = 18, marginPx = 24, preferBelow = true, constrainToWindow = true,
            visibleBoundsProvider = { IntRect(0, -1312, 1116, 1096) },
        )
        val position = readerProvider.calculatePosition(
            IntRect(894, 100, 1038, 244), IntSize(1116, 2408), LayoutDirection.Ltr, IntSize(300, 504),
        )

        assertEquals(IntOffset(738, 262), position)
    }

    @Test
    fun floatingColorStyleMenuUsesVisibleFrameAboveTheSettingRow() {
        val floatingProvider = EndAnchoredPopupPositionProvider(
            gapPx = 18, marginPx = 24, preferBelow = true, constrainToWindow = true,
            visibleBoundsProvider = { IntRect(0, -1312, 1116, 1096) },
        )
        val anchor = IntRect(1037, 740, 1038, 884)
        val position = floatingProvider.calculatePosition(
            anchor, IntSize(1116, 2408), LayoutDirection.Ltr, IntSize(300, 672),
        )

        assertEquals(IntOffset(738, 50), position)
        assertEquals(anchor.top - 18, position.y + 672)
    }

    @Test
    fun progressBehaviorMenuOpensAboveWhenTheDrawerIsNearTheScreenBottom() {
        val floatingProvider = EndAnchoredPopupPositionProvider(
            gapPx = 18, marginPx = 24, preferBelow = true, constrainToWindow = true,
            visibleBoundsProvider = { IntRect(0, -1312, 1116, 1096) },
        )
        val anchor = IntRect(1037, 944, 1038, 1088)
        val position = floatingProvider.calculatePosition(
            anchor, IntSize(1116, 2408), LayoutDirection.Ltr, IntSize(360, 288),
        )

        assertEquals(IntOffset(678, 638), position)
        assertEquals(anchor.top - 18, position.y + 288)
    }
}
