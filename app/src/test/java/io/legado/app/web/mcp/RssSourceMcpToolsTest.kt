package io.legado.app.web.mcp

import com.google.gson.JsonParser
import io.legado.app.data.entities.RssSource
import org.junit.Assert.*
import org.junit.Test

class RssSourceMcpToolsTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private val source = RssSource(sourceUrl = " https://source ", sourceName = "Original",
        sourceGroup = "A,B", enabled = false, jsLib = "library", ruleContent = "original-rule", customOrder = 17)

    @Test fun partialEditPreservesRulesGroupsAndDisabledState() {
        val updated = RssSourceMcpTools.mergeSource(json("""{"sourceName":"Edited"}"""), source)
        assertEquals("Edited", updated.sourceName)
        assertEquals(source.sourceUrl, updated.sourceUrl)
        assertEquals(source.ruleContent, updated.ruleContent)
        assertEquals(source.jsLib, updated.jsLib)
        assertEquals(source.sourceGroup, updated.sourceGroup)
        assertEquals(17, updated.customOrder)
        assertFalse(updated.enabled)
        assertEquals("Original", source.sourceName)
    }

    @Test fun explicitNullClearsOnlyNullableRule() {
        val updated = RssSourceMcpTools.mergeSource(json("""{"ruleContent":null}"""), source)
        assertNull(updated.ruleContent)
        assertEquals("library", updated.jsLib)
        assertThrows(IllegalArgumentException::class.java) {
            RssSourceMcpTools.mergeSource(json("""{"sourceName":null}"""), source)
        }
    }

    @Test fun identityChangesAndUnknownFieldsFail() {
        listOf("""{"sourceUrl":"different"}""", """{"ruleContents":"typo"}""").forEach {
            assertThrows(IllegalArgumentException::class.java) { RssSourceMcpTools.mergeSource(json(it), source) }
        }
    }

    @Test fun primitiveTypeAndEnumValidationRejectsSilentCoercion() {
        listOf("""{"enabled":"false"}""", """{"type":99}""", """{"customOrder":1.5}""").forEach {
            assertTrue(runCatching { RssSourceMcpTools.mergeSource(json(it), source) }.isFailure)
        }
    }

    @Test fun newSourceRequiresBothIdentityAndName() {
        assertThrows(IllegalArgumentException::class.java) { RssSourceMcpTools.mergeSource(json("""{"sourceUrl":"new"}""")) }
        val created = RssSourceMcpTools.mergeSource(json("""{"sourceUrl":"new","sourceName":"New"}"""))
        assertTrue(created.enabled)
        assertEquals("new", created.sourceUrl)
    }
}
