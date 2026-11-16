package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubStyleUpdatePolicyTest {
    @Test
    fun styleUpdateRequiresThePublishedIdleRevision() {
        assertTrue(canUpdateEpubStylesInPlace("ready", "42", false, 42))
        assertFalse(canUpdateEpubStylesInPlace(null, null, false, 42))
        assertFalse(canUpdateEpubStylesInPlace("loading", "42", false, 42))
        assertFalse(canUpdateEpubStylesInPlace("ready", "41", false, 42))
        assertFalse(canUpdateEpubStylesInPlace("ready", "42", true, 42))
        assertFalse(canUpdateEpubStylesInPlace("error", "42", false, 42))
    }
}
