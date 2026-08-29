package io.legado.app.ui.design.components.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgPopupToggleStateTest {

    @Test
    fun anchorClickTogglesAnOpenPopupClosed() {
        val state = NgPopupToggleState(nowMillis = { 0L }, reopenGuardMillis = 300L)

        state.onAnchorClick()
        assertTrue(state.expanded)

        state.onAnchorClick()
        assertFalse(state.expanded)
    }

    @Test
    fun dismissThenTrailingAnchorClickDoesNotReopen() {
        var now = 100L
        val state = NgPopupToggleState(nowMillis = { now }, reopenGuardMillis = 300L)

        state.onAnchorClick()
        state.onDismissRequest()
        now += 20L
        state.onAnchorClick()

        assertFalse(state.expanded)
    }

    @Test
    fun laterAnchorClickCanOpenAfterDismissGuardExpires() {
        var now = 100L
        val state = NgPopupToggleState(nowMillis = { now }, reopenGuardMillis = 300L)

        state.onAnchorClick()
        state.onDismissRequest()
        now += 301L
        state.onAnchorClick()

        assertTrue(state.expanded)
    }

    @Test
    fun explicitCloseClearsDismissGuard() {
        var now = 100L
        val state = NgPopupToggleState(nowMillis = { now }, reopenGuardMillis = 300L)

        state.onAnchorClick()
        state.onDismissRequest()
        state.close()
        now += 20L
        state.onAnchorClick()

        assertTrue(state.expanded)
    }
}
