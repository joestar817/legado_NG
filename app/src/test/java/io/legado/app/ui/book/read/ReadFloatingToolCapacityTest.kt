package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadFloatingToolCapacityTest {
    @Test
    fun measuredReaderSpaceFitsEightToolsButNotNine() {
        // 488dp between menus after their 12dp margins, on the current 480dpi emulator.
        assertEquals(8, floatingToolCapacity(availableHeightPx = 488 * 3, screenHeightDp = 803, density = 3f))
        // The renderer's 60% screen-height cap still applies with extra vertical space.
        assertEquals(8, floatingToolCapacity(availableHeightPx = 540 * 3, screenHeightDp = 803, density = 3f))
    }

    @Test
    fun capacityUsesActualGeometryAndReservesTheFixedSideSwitcher() {
        assertEquals(8, floatingToolCapacity(availableHeightPx = 500, screenHeightDp = 1000, density = 1f))
        assertEquals(9, floatingToolCapacity(availableHeightPx = 501, screenHeightDp = 1000, density = 1f))
        assertEquals(3, floatingToolCapacity(availableHeightPx = 300, screenHeightDp = 350, density = 1f))
        assertEquals(1, floatingToolCapacity(availableHeightPx = 80, screenHeightDp = 200, density = 1f))
    }

    @Test
    fun minimumAndMaximumBlockOnlyTheCorrespondingToggle() {
        assertEquals(FloatingToolToggleLimit.MINIMUM, floatingToolToggleLimit(true, 1, 8))
        assertNull(floatingToolToggleLimit(true, 2, 8))
        assertEquals(FloatingToolToggleLimit.MAXIMUM, floatingToolToggleLimit(false, 8, 8))
        assertNull(floatingToolToggleLimit(false, 7, 8))
        assertNull(floatingToolToggleLimit(true, 8, 8))
        // A smaller window never silently removes an already-enabled item.
        assertNull(floatingToolToggleLimit(true, 5, 3))
        assertEquals(FloatingToolToggleLimit.MAXIMUM, floatingToolToggleLimit(false, 5, 3))
    }
}
