package io.legado.app.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryTest {
    private val text = ListeningHistoryEntry(ListeningHistorySource.READ_ALOUD, "book", 2, 135)

    @Test
    fun stableKeysAndBothSourcesRoundTrip() {
        for (source in ListeningHistorySource.entries) {
            val entry = text.copy(source = source)
            val encoded = ListeningHistoryCodec.encode(entry)
            val root = JsonParser.parseString(encoded).asJsonObject
            assertEquals(setOf("version", "source", "bookUrl", "chapterIndex", "position"), root.keySet())
            assertEquals(source.storageValue, root["source"].asString)
            assertEquals(entry, ListeningHistoryCodec.decode(encoded))
        }
    }

    @Test
    fun maximumAudioPositionIsNotConvertedOrTruncated() {
        val entry = text.copy(source = ListeningHistorySource.AUDIO, position = Int.MAX_VALUE)
        assertEquals(entry, ListeningHistoryCodec.decode(ListeningHistoryCodec.encode(entry)))
    }

    @Test
    fun invalidSchemaIsRejected() {
        val valid = ListeningHistoryCodec.encode(text)
        val invalid = listOf(
            "null", "[]", "{}", "not json",
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace("\"readAloud\"", "\"unknown\""),
            valid.replace("\"book\"", "\"  \""),
            valid.replace("\"book\"", "false"),
            valid.replace("\"chapterIndex\":2", "\"chapterIndex\":-1"),
            valid.replace("\"chapterIndex\":2", "\"chapterIndex\":2.5"),
            valid.replace("\"position\":135", "\"position\":-1"),
            valid.replace("\"position\":135", "\"position\":2147483648"),
            valid.replace("\"position\":135", "\"position\":\"135\""),
            valid.replace("\"position\":135", "\"position\":null")
        )
        invalid.forEach { json ->
            assertTrue("Must reject $json", runCatching { ListeningHistoryCodec.decode(json) }.isFailure)
        }
    }

    @Test
    fun progressIsThrottledButIdentityAndChapterChangesSaveImmediately() {
        val policy = ListeningHistoryWritePolicy()
        assertTrue(policy.shouldWrite(text, 100))
        policy.didWrite(text, 100)
        assertFalse(policy.shouldWrite(text, 2_000, flush = true))
        val progressed = text.copy(position = 150)
        assertFalse(policy.shouldWrite(progressed, 1_099))
        assertTrue(policy.shouldWrite(progressed, 1_100))
        assertTrue(policy.shouldWrite(progressed, 101, flush = true))
        assertTrue(policy.shouldWrite(text.copy(chapterIndex = 3), 101))
        assertTrue(policy.shouldWrite(text.copy(bookUrl = "other"), 101))
        assertTrue(policy.shouldWrite(text.copy(source = ListeningHistorySource.AUDIO), 101))
        policy.didWrite(progressed, 200)
        assertFalse(policy.shouldWrite(progressed, 201, flush = true))
    }
}
