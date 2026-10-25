package io.legado.app.ui.book.read.aloud

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningLayoutPolicyTest {
    @Test
    fun phoneLocksPortraitAndCannotUseTabletPanesEvenInAWideWindow() {
        val phone = ListeningWindowEnvironment(
            largeDisplay = isLargeListeningDisplay(1116, 2480, 480),
        )
        assertTrue(shouldLockListeningPortrait(phone))
        assertFalse(shouldUseListeningTwoPane(phone, 1137f, 684f))
    }

    @Test
    fun tabletCapabilityUsesMaximumDisplayRatherThanItsHalfWindow() {
        val tablet = ListeningWindowEnvironment(
            largeDisplay = isLargeListeningDisplay(2560, 1600, 360),
        )
        assertFalse(shouldLockListeningPortrait(tablet))
        assertTrue(shouldUseListeningTwoPane(tablet, 1137f, 684f))
        assertFalse(shouldUseListeningTwoPane(tablet, 563f, 684f))
    }

    @Test
    fun systemSplitScreenAlwaysKeepsOnePaneEvenIfItIsWideEnough() {
        val split = ListeningWindowEnvironment(largeDisplay = true, multiWindow = true)
        assertFalse(shouldLockListeningPortrait(split))
        assertFalse(shouldUseListeningTwoPane(split, 1137f, 684f))
    }

    @Test
    fun phoneMultiWindowDoesNotTryToRotateTheSystemWindow() {
        val splitPhone = ListeningWindowEnvironment(multiWindow = true)
        assertFalse(shouldLockListeningPortrait(splitPhone))
        assertFalse(shouldUseListeningTwoPane(splitPhone, 900f, 500f))
    }

    @Test
    fun twoPaneRequiresBothWidthAndHeightAndALandscapeWindow() {
        val tablet = ListeningWindowEnvironment(largeDisplay = true)
        assertTrue(shouldUseListeningTwoPane(tablet, 840f, 480f))
        assertFalse(shouldUseListeningTwoPane(tablet, 839f, 684f))
        assertFalse(shouldUseListeningTwoPane(tablet, 1137f, 479f))
        assertFalse(shouldUseListeningTwoPane(tablet, 711f, 1111f))
        assertFalse(shouldUseListeningTwoPane(tablet, 900f, 900f))
    }

    @Test
    fun displayCapabilityIsRotationIndependentAndIncludesThe600DpBoundary() {
        assertTrue(isLargeListeningDisplay(1200, 1920, 320))
        assertTrue(isLargeListeningDisplay(1920, 1200, 320))
        assertFalse(isLargeListeningDisplay(1199, 1920, 320))
    }

    @Test
    fun tabletPaneBoundsMeetAtTheCenterOfTheExistingContentGap() {
        val layout = calculateListeningPaneLayout(0, 0, 1137, 684, 1f)

        assertEquals(IntRect(0, 0, 463, 684), layout.controls)
        assertEquals(IntRect(463, 0, 1137, 684), layout.music)
        assertEquals(layout.controls.right, layout.music.left)
        assertEquals(1137, layout.controls.width + layout.music.width)
    }

    @Test
    fun paneBoundsPreserveWindowOffsetsAndScaleWithDisplayDensity() {
        val layout = calculateListeningPaneLayout(37, 24, 2274, 1368, 2f)

        assertEquals(IntRect(37, 24, 963, 1392), layout.controls)
        assertEquals(IntRect(963, 24, 2311, 1392), layout.music)
        assertEquals(layout.controls.right, layout.music.left)
        assertEquals(2274, layout.controls.width + layout.music.width)
    }

    @Test
    fun fractionalDensityUsesTheActualRoundedPaddingAndOddPixelGap() {
        // At 1.3x: 36px margins, 31px gap and a 396px left content area.
        val layout = calculateListeningPaneLayout(-8, 31, 1092, 624, 1.3f)

        assertEquals(IntRect(-8, 31, 440, 655), layout.controls)
        assertEquals(IntRect(440, 31, 1084, 655), layout.music)
        assertEquals(layout.controls.right, layout.music.left)
        assertEquals(1092, layout.controls.width + layout.music.width)
    }
}
