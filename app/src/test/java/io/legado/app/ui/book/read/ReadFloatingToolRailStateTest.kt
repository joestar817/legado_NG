package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadFloatingToolRailStateTest {

    @Test
    fun selectingSameExpansionClosesPanel() {
        assertNull(
            ReadFloatingToolExpansion.BRIGHTNESS.toggle(
                ReadFloatingToolExpansion.BRIGHTNESS
            )
        )
    }

    @Test
    fun selectingAnotherExpansionReplacesCurrentPanel() {
        assertEquals(
            ReadFloatingToolExpansion.AI,
            ReadFloatingToolExpansion.BRIGHTNESS.toggle(ReadFloatingToolExpansion.AI)
        )
    }

    @Test
    fun selectingThemeExpansionReplacesBrightnessPanel() {
        assertEquals(
            ReadFloatingToolExpansion.THEME,
            ReadFloatingToolExpansion.BRIGHTNESS.toggle(ReadFloatingToolExpansion.THEME)
        )
    }

    @Test
    fun dockSideToggleMovesBetweenLeftAndRight() {
        assertEquals(ReadFloatingToolDock.RIGHT, ReadFloatingToolDock.LEFT.toggled())
        assertEquals(ReadFloatingToolDock.LEFT, ReadFloatingToolDock.RIGHT.toggled())
    }

    @Test
    fun storedBrightnessPositionMapsToDockSide() {
        assertEquals(
            ReadFloatingToolDock.LEFT,
            ReadFloatingToolDock.fromStoredRight(false)
        )
        assertEquals(
            ReadFloatingToolDock.RIGHT,
            ReadFloatingToolDock.fromStoredRight(true)
        )
    }
}
