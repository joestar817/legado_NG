package io.legado.app.web.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemoryStructuredPayloadValidatorTest {

    @Test
    fun acceptsNestedEvidenceInsideDeclaredChapterRange() {
        AgentMemoryStructuredPayloadValidator.validateExplicitPayload(
            arguments = argumentsWithData(
                """
                {
                  "chapter_range": [28, 38],
                  "risks": [{
                    "evidence_refs": [
                      {"chapter_index": 28},
                      {"chapter_index": 37}
                    ]
                  }]
                }
                """
            ),
            argumentPath = "items[1]"
        )
    }

    @Test
    fun rejectsEvidenceCopiedFromAnotherWindowWithExactPath() {
        val error = runCatching {
            AgentMemoryStructuredPayloadValidator.validateExplicitPayload(
                arguments = argumentsWithData(
                    """
                    {
                      "chapter_range": [28, 38],
                      "risks": [{
                        "evidence_refs": [{"chapter_index": 0}]
                      }]
                    }
                    """
                ),
                argumentPath = "items[1]"
            )
        }.exceptionOrNull()

        assertEquals(
            "items[1].data.risks[0].evidence_refs[0].chapter_index=0 is outside " +
                "chapter_range=[28,38)",
            error?.message
        )
    }

    @Test
    fun rejectsDecimalChapterIndexInsteadOfTruncatingIt() {
        val error = runCatching {
            AgentMemoryStructuredPayloadValidator.validateExplicitPayload(
                argumentsWithData(
                    """
                    {
                      "chapter_range": [0, 10],
                      "events": [{"evidence_refs": [{"chapter_index": 1.5}]}]
                    }
                    """
                )
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("chapter_index must be an integer"))
    }

    @Test
    fun ignoresUnscopedGenericEvidencePayloads() {
        AgentMemoryStructuredPayloadValidator.validateExplicitPayload(
            argumentsWithData(
                """
                {"evidence_refs": [{"chapter_index": 999}]}
                """
            )
        )
    }

    @Test
    fun omittedDataDoesNotRevalidateInheritedLegacyPayload() {
        AgentMemoryStructuredPayloadValidator.validateExplicitPayload(JsonObject())
    }

    @Test
    fun dataJsonUsesSameValidationAsDataObject() {
        val arguments = JsonObject().apply {
            addProperty(
                "data_json",
                """{"chapter_range":[3,4],"evidence_refs":[{"chapter_index":4}]}"""
            )
        }

        val error = runCatching {
            AgentMemoryStructuredPayloadValidator.validateExplicitPayload(arguments)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("chapter_range=[3,4)"))
    }

    private fun argumentsWithData(json: String): JsonObject {
        return JsonObject().apply {
            add("data", JsonParser.parseString(json.trimIndent()).asJsonObject)
        }
    }
}
