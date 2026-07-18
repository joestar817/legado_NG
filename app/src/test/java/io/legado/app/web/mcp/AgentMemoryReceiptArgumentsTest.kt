package io.legado.app.web.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentMemoryReceiptArgumentsTest {

    @Test
    fun batchFallsBackToReceiptIdsMisplacedOnMemoryItems() {
        val arguments = JsonObject()
        val firstItem = JsonObject().apply {
            add("source_receipt_ids", JsonArray().apply {
                add("receipt-opening")
                add("receipt-ending")
            })
        }

        assertEquals(
            listOf("receipt-opening", "receipt-ending"),
            AgentMemoryReceiptArguments.parseBatch(
                arguments = arguments,
                items = listOf(firstItem, JsonObject()),
                maxReceiptIds = 25
            )
        )
    }

    @Test
    fun batchPrefersSchemaDefinedTopLevelReceiptIds() {
        val arguments = JsonObject().apply {
            add("source_receipt_ids", JsonArray().apply { add("receipt-top") })
        }
        val item = JsonObject().apply {
            add("source_receipt_ids", JsonArray().apply { add("receipt-nested") })
        }

        assertEquals(
            listOf("receipt-top"),
            AgentMemoryReceiptArguments.parseBatch(
                arguments = arguments,
                items = listOf(item),
                maxReceiptIds = 25
            )
        )
    }
}
