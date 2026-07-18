package io.legado.app.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AgentToolResultStoreTest {

    @Test
    fun argumentsHashIgnoresRecursiveObjectKeyOrderButPreservesArrayOrder() {
        val first = JsonParser.parseString(
            """{"z":{"b":2,"a":1},"items":[1,2]}"""
        )
        val reordered = JsonParser.parseString(
            """{"items":[1,2],"z":{"a":1,"b":2}}"""
        )
        val reorderedArray = JsonParser.parseString(
            """{"items":[2,1],"z":{"a":1,"b":2}}"""
        )

        assertEquals(
            AgentToolResultStore.argumentsHash(first),
            AgentToolResultStore.argumentsHash(reordered)
        )
        assertNotEquals(
            AgentToolResultStore.argumentsHash(first),
            AgentToolResultStore.argumentsHash(reorderedArray)
        )
    }
}
