package io.legado.app.help.tts.packages

import java.io.IOException
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftOfflineTraceTest {
    @Test
    fun failureLogContainsOnlyAllowlistedCodeAndNoExceptionContents() {
        val entries = mutableListOf<String>()
        var time = 0L
        val trace = MicrosoftOfflineTrace(emit = { entries.add(it) }, clock = { time })
        trace.begin(MicrosoftOfflineTrace.Stage.SYNTHESIS)
        time = 5_000_000L
        trace.failure(IOException("private-license /private/path 朗读正文 EMBEDDED_TTS_ERROR_INVALID_LICENSE"))
        assertEquals(2, entries.size)
        assertTrue(entries.last().contains("stage=SYNTHESIS event=failure elapsed_ms=5"))
        assertTrue(entries.last().contains("code=EMBEDDED_TTS_ERROR_INVALID_LICENSE"))
        assertFalse(entries.joinToString().contains("private-license"))
        assertFalse(entries.joinToString().contains("/private/path"))
        assertFalse(entries.joinToString().contains("朗读正文"))
    }

    @Test
    fun unknownMessagesAreDiscardedAndTimeoutIsClassified() {
        assertEquals("OTHER", MicrosoftOfflineTrace.safeCode(IOException("private text and path")))
        assertEquals("TIMEOUT", MicrosoftOfflineTrace.safeCode(TimeoutException("private text")))
        assertEquals("NATIVE_LINK", MicrosoftOfflineTrace.safeCode(UnsatisfiedLinkError("private path")))
    }
}
