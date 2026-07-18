package io.legado.app.help.ai.runtime

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentHookRegistryTest {

    @Test
    fun registryExposesOnlyGenericMemoryFlushHook() {
        assertEquals(mapOf("core.memory_flush" to 1), AgentHookRegistry.supportedHooks())
    }

    @Test
    fun unknownHookAndIllegalMemoryFlushConfigAreRejected() {
        expectIllegalArgument("未知 runtime Hook") {
            AgentHookRegistry.validateBindings(listOf(AgentHookBinding("custom.unknown", 1)))
        }
        expectIllegalArgument("未知字段") {
            AgentHookRegistry.validateBindings(
                listOf(
                    AgentHookBinding(
                        id = "core.memory_flush",
                        version = 1,
                        config = JsonParser.parseString(
                            """{"watch_tools":["read"],"flush_tools":["flush"],"extra":true}"""
                        ).asJsonObject
                    )
                )
            )
        }
        expectIllegalArgument("不能重叠") {
            AgentHookRegistry.validateBindings(
                listOf(
                    AgentHookBinding(
                        id = "core.memory_flush",
                        version = 1,
                        config = JsonParser.parseString(
                            """{"watch_tools":["same"],"flush_tools":["same"]}"""
                        ).asJsonObject
                    )
                )
            )
        }
    }

    private fun expectIllegalArgument(messagePart: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(messagePart))
        }
    }
}
