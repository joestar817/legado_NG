package io.legado.app.help.ai

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AiExtendedModelCatalogTest {
    @Test
    fun arkCatalogReadsNestedLimitsAndCapabilities() {
        val info = readModelInfo(
            json("""
                {
                  "context_window":64000,
                  "token_limits":{"context_window":1048576,"max_output_token_length":262144},
                  "modalities":{"input_modalities":["text","image","video"],"output_modalities":["text"]},
                  "features":{"tools":{"function_calling":true},"thinking":{"type":"enabled"}},
                  "effort":{"supported_levels":["low","high"],"default_level":"high"}
                }
            """),
            "ark",
            AiModelCatalogFormat.ARK,
        )
        assertEquals(1_048_576, info.contextWindow?.value)
        assertEquals(262_144, info.maxOutputTokens?.value)
        assertEquals(listOf("text", "image", "video"), info.inputModalities?.value)
        assertEquals(listOf("text"), info.outputModalities?.value)
        assertEquals(true, info.toolCalling?.value)
        assertEquals(true, info.reasoning?.value)
        assertEquals(listOf("low", "high"), info.effortLevels?.value)
        assertEquals("high", info.defaultEffort?.value)
        assertNull(info.canDisableThinking)
    }

    @Test
    fun arkCatalogUsesValidTopLevelLimitsAndRespectsExplicitFalse() {
        val info = readModelInfo(
            json("""
                {
                  "context_window":64000,
                  "max_output_tokens":8192,
                  "token_limits":{"context_window":0,"max_output_token_length":"unknown"},
                  "features":{"tools":{"function_calling":false},"thinking":{"type":"disabled"}}
                }
            """),
            "ark",
            AiModelCatalogFormat.ARK,
        )
        assertEquals(64_000, info.contextWindow?.value)
        assertEquals(8_192, info.maxOutputTokens?.value)
        assertEquals(false, info.toolCalling?.value)
        assertEquals(false, info.reasoning?.value)
        assertNull(info.inputModalities)
    }

    @Test
    fun openCodeGoCatalogReadsPublishedSpecsAndMediaTypes() {
        val provider = preset(AiDefaultProviders.OPENCODE_ID)
        val endpoint = "https://opencode.ai/inference/go/v1/models".toHttpUrl()
        val model = OpenAiCompatibleProvider().parseModels(
            json("""
                {"data":[{
                  "id":"catalog-test-chat",
                  "limit":{"context":1000000,"output":128000},
                  "modalities":{"input":["text","image/png","image/jpeg","application/pdf"],"output":["text"]},
                  "capabilities":{"tools":{"supported":true},"reasoning":{"supported":true,"effort":["low","max"]}}
                }]}
            """),
            provider.modelConfigurationKey(),
            provider.openAiCatalogFormat(endpoint),
            endpoint.catalogSourceId(),
        ).single()
        val enriched = model.withCatalogCapabilities(model.modelInfo)
        assertEquals(setOf(AiModelModality.TEXT, AiModelModality.IMAGE, AiModelModality.FILE), enriched.inputModalities.toSet())
        assertEquals(setOf(AiModelAbility.TOOL, AiModelAbility.REASONING), enriched.abilities.toSet())
        assertEquals(1_000_000, model.modelInfo?.contextWindow?.value)
        assertEquals(128_000, model.modelInfo?.maxOutputTokens?.value)
        assertEquals(listOf("low", "max"), model.modelInfo?.effortLevels?.value)

        val baseFieldsOnly = readModelInfo(json("""{"id":"catalog-test-chat"}"""),
            provider.modelConfigurationKey(), provider.openAiCatalogFormat(endpoint))
        assertNull(baseFieldsOnly.contextWindow)
        assertNull(baseFieldsOnly.reasoning)
        assertNull(baseFieldsOnly.effortLevels)
    }

    @Test
    fun moonshotCatalogReadsInputFlagsAndOnlyDeclaredThinkingSupport() {
        val info = readModelInfo(
            json("""
                {"context_length":1048576,"supports_image_in":true,"supports_video_in":false,"supports_reasoning":false}
            """),
            "moonshot",
            AiModelCatalogFormat.MOONSHOT,
        )
        assertEquals(1_048_576, info.contextWindow?.value)
        assertEquals(listOf("text", "image"), info.inputModalities?.value)
        assertEquals(false, info.reasoning?.value)
        assertNull(info.maxOutputTokens)
        assertNull(info.effortLevels)
        val partial = readModelInfo(json("""{"supports_image_in":false}"""), "moonshot", AiModelCatalogFormat.MOONSHOT)
        assertNull(partial.inputModalities)
        assertNull(partial.reasoning)
    }

    @Test
    fun claudeCatalogSurfacesImageAndPdfCapabilitiesWhenDeclared() {
        val info = readModelInfo(
            json("""{"capabilities":{"image_input":{"supported":false},"pdf_input":{"supported":true}}}"""),
            "claude",
            AiModelCatalogFormat.CLAUDE,
        )
        assertEquals(listOf("text", "file"), info.inputModalities?.value)
        assertNull(info.outputModalities)
    }

    @Test
    fun vendorSpecificFieldsApplyOnlyToTheOfficialCatalog() {
        val ark = preset("volcengine")
        val official = "https://ark.cn-beijing.volces.com/api/v3/models".toHttpUrl()
        assertEquals(AiModelCatalogFormat.ARK, ark.openAiCatalogFormat(official))
        assertEquals(AiModelCatalogFormat.COMPATIBLE, ark.copy(builtIn = false).openAiCatalogFormat(official))
        assertEquals(AiModelCatalogFormat.COMPATIBLE, ark.openAiCatalogFormat(official.newBuilder().encodedPath("/other").build()))
        assertEquals(AiModelCatalogFormat.COMPATIBLE, ark.openAiCatalogFormat(official.newBuilder().host("catalog.test").build()))
        val info = readModelInfo(json("""{"token_limits":{"context_window":1048576}}"""),
            "custom", AiModelCatalogFormat.COMPATIBLE)
        assertNull(info.contextWindow)
    }

    @Test
    fun compatibleCatalogRejectsAPartialResponseWithoutRejectingAnEmptyCompleteOne() {
        val client = OpenAiCompatibleProvider()
        assertThrows(IllegalStateException::class.java) {
            client.parseModels(json("""{"data":[{"id":"first"}],"has_more":true}"""), "source")
        }
        assertEquals(emptyList<AiModel>(), client.parseModels(json("""{"data":[],"has_more":false}"""), "source"))
        assertFalse(client.parseModels(json("""{"data":[{"id":"first"}]}"""), "source").isEmpty())
    }

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    private fun preset(id: String) = AiDefaultProviders.all().single { it.id == id }
}
