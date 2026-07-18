package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiSkillRegistryRuntimeDeclarationTest {

    @Test
    fun parsesMcpCapabilitiesAndRuntimeHooksFromFrontmatter() {
        val declaration = AiSkillRegistry.parseRuntimeDeclaration(
            skillContent(
                """
                mcp_capabilities: bookshelf.query|bookshelf.read_content|ai.memory
                runtime_hooks: core.memory_flush@1
                hook.core.memory_flush: {"watch_tools":["bookshelf_text_window_get"],"flush_tools":["agent_memory_batch_upsert"],"max_pending":2,"block_before":["compaction","turn_finalize"]}
                """.trimIndent()
            )
        )

        assertEquals(
            listOf("bookshelf.query", "bookshelf.read_content", "ai.memory"),
            declaration.mcpCapabilities
        )
        val hook = declaration.runtimeHooks.single()
        assertEquals("core.memory_flush", hook.id)
        assertEquals(1, hook.version)
        assertEquals(2, hook.config.get("max_pending").asInt)
        assertEquals(
            listOf("compaction", "turn_finalize"),
            hook.config.getAsJsonArray("block_before").map { it.asString }
        )
    }

    @Test
    fun parseSkillContentValidatesRuntimeDeclaration() {
        val parsed = AiSkillRegistry.parseSkillContent(
            skillContent(
                """
                mcp_capabilities: bookshelf.read_content|ai.memory
                runtime_hooks: core.memory_flush@1
                hook.core.memory_flush: {"watch_tools":["bookshelf_text_window_get"],"flush_tools":["agent_memory_batch_upsert"]}
                """.trimIndent()
            )
        )

        assertEquals("test_skill", parsed.id)
    }

    @Test
    fun rejectsUnknownHookUnsupportedVersionAndUndeclaredConfig() {
        expectIllegalArgument("未知 runtime Hook") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent("runtime_hooks: custom.unknown@1")
            )
        }
        expectIllegalArgument("不支持版本") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent(
                    """
                    runtime_hooks: core.memory_flush@2
                    hook.core.memory_flush: {"watch_tools":["read"],"flush_tools":["flush"]}
                    """.trimIndent()
                )
            )
        }
        expectIllegalArgument("未声明 runtime Hook") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent(
                    """
                    hook.core.memory_flush: {"watch_tools":["read"],"flush_tools":["flush"]}
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun rejectsMalformedHookConfigAndCapabilityLists() {
        expectIllegalArgument("不是合法 JSON") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent(
                    """
                    runtime_hooks: core.memory_flush@1
                    hook.core.memory_flush: {not-json}
                    """.trimIndent()
                )
            )
        }
        expectIllegalArgument("非法 capability") {
            AiSkillRegistry.parseRuntimeDeclaration(skillContent("mcp_capabilities: invalid capability"))
        }
        expectIllegalArgument("不能为空") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent("mcp_capabilities: bookshelf.query||bookshelf.read_content")
            )
        }
        expectIllegalArgument("未知 capability") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent("mcp_capabilities: valid.capability")
            )
        }
        expectIllegalArgument("未授权工具") {
            AiSkillRegistry.parseRuntimeDeclaration(
                skillContent(
                    """
                    mcp_capabilities: bookshelf.read_content
                    runtime_hooks: core.memory_flush@1
                    hook.core.memory_flush: {"watch_tools":["bookshelf_text_window_get"],"flush_tools":["agent_memory_batch_upsert"]}
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun skillWithoutRuntimeDeclarationRemainsValid() {
        val declaration = AiSkillRegistry.parseRuntimeDeclaration(skillContent())

        assertTrue(declaration.mcpCapabilities.isEmpty())
        assertTrue(declaration.runtimeHooks.isEmpty())
    }

    private fun skillContent(extraFrontmatter: String = ""): String {
        return buildString {
            appendLine("---")
            appendLine("id: test_skill")
            appendLine("name: 测试 Skill")
            appendLine("description: 测试运行时声明")
            if (extraFrontmatter.isNotBlank()) {
                appendLine(extraFrontmatter)
            }
            appendLine("---")
            appendLine()
            append("执行测试任务。")
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
