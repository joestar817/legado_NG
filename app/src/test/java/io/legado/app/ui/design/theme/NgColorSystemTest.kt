package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NgColorSystemTest {

    @Test
    fun parseCommittedArgbWaitsUntilEightDigits() {
        assertNull(parseCommittedNgColor("#FF3333"))
        assertNull(parseCommittedNgColor("#FF33333"))
        assertEquals(0xFF333333.toInt(), parseCommittedNgColor("#FF333333"))
    }

    @Test
    fun parseCommittedRgbOnlyWhenExplicitlyAllowed() {
        assertNull(parseCommittedNgColor("#FAF9F5"))
        assertEquals(0xFFFAF9F5.toInt(), parseCommittedNgColor("#FAF9F5", allowRgb = true))
    }
}
