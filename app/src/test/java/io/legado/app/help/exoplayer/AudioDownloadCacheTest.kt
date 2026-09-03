package io.legado.app.help.exoplayer

import io.legado.app.exception.NoStackTraceException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AudioDownloadCacheTest {

    @Test
    fun parsesSingleAndMultipartAudioUrls() {
        assertEquals(
            listOf("https://example.com/a.mp3"),
            AudioDownloadCache.parseAudioUrls("  https://example.com/a.mp3  "),
        )
        assertEquals(
            listOf("https://example.com/a.mp3", "https://example.com/b.m4a"),
            AudioDownloadCache.parseAudioUrls(
                """["https://example.com/a.mp3","https://example.com/b.m4a"]"""
            ),
        )
    }

    @Test
    fun rejectsEmptyAudioUrls() {
        try {
            AudioDownloadCache.parseAudioUrls("[]")
            fail("empty audio list should fail")
        } catch (_: NoStackTraceException) {
        }
    }
}
