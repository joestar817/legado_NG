package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentDataTest {

    @Test
    fun generatedKeysDoNotOverwriteRapidWrites() {
        val entries = (0 until 2_048).map { value ->
            IntentData.put(value) to value
        }

        assertEquals(entries.size, entries.map { it.first }.toSet().size)
        entries.forEach { (key, value) ->
            assertEquals(value, IntentData.get<Int>(key))
        }
    }
}
