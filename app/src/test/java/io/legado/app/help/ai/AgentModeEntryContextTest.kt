package io.legado.app.help.ai

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModeEntryContextTest {

    @Test
    fun roundTripPreservesOpaquePayloadAndWorkKeyNewline() {
        val expected = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：天之下",
            payload = JsonObject().apply {
                addProperty("work_key", "天之下\n空")
                addProperty("total_chapters", 444)
            }
        )

        val serialized = expected.toJson()
        val restored = AgentModeEntryContext.fromJsonOrNull(serialized)

        assertFalse(serialized.contains('\n'))
        assertEquals(expected, restored)
        assertEquals("天之下\n空", restored?.payload?.get("work_key")?.asString)
    }

    @Test
    fun parserRejectsUnsupportedSchemaAndMalformedPayload() {
        assertNull(
            AgentModeEntryContext.fromJsonOrNull(
                """{"schema_version":2,"context_id":"book_detail","payload":{}}"""
            )
        )
        assertNull(
            AgentModeEntryContext.fromJsonOrNull(
                """{"schema_version":1,"context_id":"book_detail","payload":[]}"""
            )
        )
    }

    @Test
    fun payloadSizeAndDepthAreBounded() {
        val oversized = JsonObject().apply {
            addProperty("text", "x".repeat(AgentModeEntryContext.MAX_PAYLOAD_CHARS))
        }
        assertTrue(
            runCatching {
                AgentModeEntryContext(contextId = "book_detail", payload = oversized)
            }.isFailure
        )

        var nested = JsonObject().apply { addProperty("value", 1) }
        repeat(10) {
            nested = JsonObject().apply { add("nested", nested) }
        }
        assertTrue(
            runCatching {
                AgentModeEntryContext(contextId = "book_detail", payload = nested)
            }.isFailure
        )
    }
}
