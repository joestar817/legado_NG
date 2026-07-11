package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBookScanContextTest {

    @Test
    fun removesToolExchangeButKeepsVisibleConversation() {
        val messages = listOf(
            message("system", "skill"),
            message("user", "开始初扫"),
            message("assistant", "读取正文").apply {
                add("tool_calls", JsonArray().apply { add(JsonObject()) })
            },
            message("tool", "${"正文".repeat(30_000)}"),
            message(
                "assistant",
                """
                    初扫报告
                    ```book_scan_delta
                    {"schema_version":1}
                    ```
                    ```legado-interaction
                    {"version":1,"id":"next","type":"actions","title":"继续"}
                    ```
                """.trimIndent()
            ).apply {
                addProperty("reasoning_content", "内部推理")
            }
        )

        val result = AiBookScanContext.pruneAfterTurn(messages)

        assertEquals(listOf("system", "user", "assistant"), result.map { it["role"].asString })
        assertEquals("初扫报告", result.last()["content"].asString)
        assertFalse(result.last().has("reasoning_content"))
        assertTrue(result.none { it["role"].asString == "tool" })
        assertTrue(result.none { it.has("tool_calls") })
    }

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }
}
